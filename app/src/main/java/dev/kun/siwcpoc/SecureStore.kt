package dev.kun.siwcpoc

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class ChatGptSession(
    val clientId: String,
    val subject: String,
    val email: String,
    val idToken: String,
    val accessToken: String,
    val refreshToken: String,
    val scopes: String,
    val expiresAt: Long
) {
    fun hasSubscriptionScope(): Boolean =
        scopes.split(' ').contains("chatgpt.tokens.use.direct")

    fun toJson(): JSONObject = JSONObject().apply {
        put("client_id", clientId)
        put("sub", subject)
        put("email", email)
        put("id_token", idToken)
        put("access_token", accessToken)
        put("refresh_token", refreshToken)
        put("scope", scopes)
        put("expires_at", expiresAt)
    }

    companion object {
        fun fromJson(j: JSONObject) = ChatGptSession(
            j.getString("client_id"), j.getString("sub"), j.optString("email"),
            j.getString("id_token"), j.getString("access_token"),
            j.optString("refresh_token"), j.getString("scope"), j.getLong("expires_at")
        )
    }
}

/** Stored credentials are AES-GCM encrypted using a non-exportable Android Keystore key.
 * Android backup is disabled to prevent restoring ciphertext without its original Keystore key.
 */
class SecureStore(context: Context) {
    private val prefs = context.getSharedPreferences("siwc_poc", Context.MODE_PRIVATE)
    private val alias = "siwc_poc_aes_v1"

    fun hostId(): String {
        prefs.getString("host_id", null)?.let { return it }
        val generated = "urn:uuid:${UUID.randomUUID()}"
        check(prefs.edit().putString("host_id", generated).commit())
        return generated
    }

    fun save(session: ChatGptSession) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ciphertext = cipher.doFinal(session.toJson().toString().toByteArray(Charsets.UTF_8))
        val stored = JSONObject().apply {
            put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            put("data", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
        }
        check(prefs.edit().putString("session", stored.toString()).commit())
    }

    fun load(): ChatGptSession? {
        val stored = prefs.getString("session", null) ?: return null
        val payload = JSONObject(stored)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE, key(),
            GCMParameterSpec(128, Base64.decode(payload.getString("iv"), Base64.NO_WRAP))
        )
        val plain = cipher.doFinal(Base64.decode(payload.getString("data"), Base64.NO_WRAP))
        return ChatGptSession.fromJson(JSONObject(String(plain, Charsets.UTF_8)))
    }

    fun signOut() {
        check(prefs.edit().remove("session").commit())
        // Host ID intentionally survives sign-out; tokens do not.
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generateKey()
        }
    }
}
