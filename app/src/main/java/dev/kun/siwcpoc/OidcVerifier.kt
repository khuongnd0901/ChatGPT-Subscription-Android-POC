package dev.kun.siwcpoc

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.RSAPublicKeySpec

/** Validate an OpenAI OIDC ID token before storing / trusting its identity.
 * POC accepts RS256 only; rejecting an unknown algorithm is fail-closed.
 */
object OidcVerifier {
    data class Identity(val subject: String, val email: String)

    fun verify(idToken: String, clientId: String, expectedNonce: String): Identity {
        val pieces = idToken.split('.')
        require(pieces.size == 3) { "Malformed ID token" }
        val header = JSONObject(String(decode(pieces[0]), Charsets.UTF_8))
        val claims = JSONObject(String(decode(pieces[1]), Charsets.UTF_8))
        require(header.optString("alg") == "RS256") { "Unexpected ID token signing algorithm" }
        val kid = header.getString("kid")
        val jwks = HttpClient.getJson("https://auth.openai.com/.well-known/jwks.json")
            .getJSONArray("keys")
        var candidate: JSONObject? = null
        for (i in 0 until jwks.length()) {
            val j = jwks.getJSONObject(i)
            if (j.optString("kid") == kid && j.optString("kty") == "RSA") {
                candidate = j
                break
            }
        }
        val jwk = candidate ?: error("OIDC signing key not found")
        val modulus = BigInteger(1, decode(jwk.getString("n")))
        val exponent = BigInteger(1, decode(jwk.getString("e")))
        val key = KeyFactory.getInstance("RSA")
            .generatePublic(RSAPublicKeySpec(modulus, exponent))
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initVerify(key)
        signature.update("${pieces[0]}.${pieces[1]}".toByteArray(Charsets.US_ASCII))
        require(signature.verify(decode(pieces[2]))) { "ID token signature invalid" }

        require(claims.optString("iss") == "https://auth.openai.com") { "Invalid issuer" }
        val audience: Any? = claims.opt("aud")
        val matchingAudience = when (audience) {
            is String -> audience == clientId
            is JSONArray -> (0 until audience.length()).any { audience.optString(it) == clientId }
            else -> false
        }
        require(matchingAudience) { "ID token audience mismatch" }
        if (audience is JSONArray && audience.length() > 1) {
            require(claims.optString("azp") == clientId) { "ID token azp mismatch" }
        }
        require(claims.optString("nonce") == expectedNonce) { "OIDC nonce mismatch" }
        val now = System.currentTimeMillis() / 1000L
        require(claims.has("iat") && claims.getLong("iat") <= now + 60) { "Invalid iat" }
        require(claims.has("exp") && claims.getLong("exp") > now - 60) { "ID token expired" }
        if (claims.has("nbf")) require(claims.getLong("nbf") <= now + 60) { "Token not yet valid" }
        val sub = claims.getString("sub")
        require(sub.isNotBlank()) { "Missing OIDC sub" }
        return Identity(sub, claims.optString("email"))
    }

    private fun decode(value: String): ByteArray = Base64.decode(
        value, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
    )
}
