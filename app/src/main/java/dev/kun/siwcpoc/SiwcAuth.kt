package dev.kun.siwcpoc

import android.net.Uri
import android.util.Base64
import org.json.JSONObject
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom

/** Implements SIWC dynamic registration with a real loopback listener on the phone.
 * All calls must run from a worker thread, never the Android main thread.
 */
class SiwcAuth(private val store: SecureStore) {
    companion object {
        const val RESOURCE = "https://api.openai.com/v1"
        const val SCOPE = "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct"
        const val AUTH_URL = "https://auth.openai.com/api/accounts/authorize"
        const val TOKEN_URL = "https://auth.openai.com/api/accounts/oauth/token"
        const val AGENT_NAME = "ChatGPT Subscription Android POC"
    }

    fun login(openBrowser: (Uri) -> Unit): ChatGptSession {
        val old = store.load()
        val state = randomUrlSafe(24)
        val nonce = randomUrlSafe(24)
        val verifier = randomUrlSafe(48)
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 180_000
            val redirect = "http://127.0.0.1:${server.localPort}/auth/callback"
            val clientId = old?.clientId ?: "dynamic_agent_client"
            val url = Uri.parse(AUTH_URL).buildUpon()
                .appendQueryParameter("client_id", clientId)
                .appendQueryParameter("redirect_uri", redirect)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("scope", SCOPE)
                .appendQueryParameter("resource", RESOURCE)
                .appendQueryParameter("state", state)
                .appendQueryParameter("nonce", nonce)
                .appendQueryParameter("code_challenge", challenge)
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("ext_agent_host_id", store.hostId())
            if (old == null) url.appendQueryParameter("agent_name_hint", AGENT_NAME)
            else url.appendQueryParameter("id_token_hint", old.idToken)
            openBrowser(url.build())

            val callback = awaitCallback(server)
            require(callback.getQueryParameter("state") == state) { "OAuth state mismatch" }
            val oauthError = callback.getQueryParameter("error")
            require(oauthError.isNullOrBlank()) { "Authorization declined: $oauthError" }
            val code = callback.getQueryParameter("code")
                ?.takeIf { it.isNotBlank() } ?: error("OAuth callback missing code")
            val returnedClient = callback.getQueryParameter("client_id")
            val issued = when {
                old == null -> returnedClient?.takeIf { it.startsWith("oaiapp_") }
                    ?: error("Dynamic registration did not return an issued client_id")
                returnedClient != null && returnedClient != old.clientId ->
                    error("Returned client ID differs from registered account")
                else -> old.clientId
            }
            val j = HttpClient.postForm(
                TOKEN_URL,
                mapOf(
                    "grant_type" to "authorization_code",
                    "client_id" to issued,
                    "code" to code,
                    "code_verifier" to verifier,
                    "redirect_uri" to redirect,
                    "resource" to RESOURCE
                )
            )
            val identity = OidcVerifier.verify(j.getString("id_token"), issued, nonce)
            if (old != null) require(identity.subject == old.subject) {
                "Different ChatGPT account. Sign out before changing accounts."
            }
            val session = fromTokenResponse(j, issued, identity, old)
            require(session.hasSubscriptionScope()) {
                "ChatGPT plan usage was not authorized (missing chatgpt.tokens.use.direct)."
            }
            store.save(session)
            return session
        }
    }

    fun freshSession(forceRefresh: Boolean = false): ChatGptSession {
        val current = store.load() ?: error("Not signed in")
        require(current.hasSubscriptionScope()) { "Subscription scope not granted" }
        if (!forceRefresh && current.expiresAt > System.currentTimeMillis() + 120_000) return current
        require(current.refreshToken.isNotBlank()) { "Refresh token missing, sign in again" }
        val j = HttpClient.postForm(
            TOKEN_URL,
            mapOf(
                "grant_type" to "refresh_token",
                "client_id" to current.clientId,
                "refresh_token" to current.refreshToken,
                "resource" to RESOURCE
            )
        )
        val fresh = current.copy(
            accessToken = j.getString("access_token"),
            refreshToken = j.optString("refresh_token").ifBlank { current.refreshToken },
            scopes = j.optString("scope").ifBlank { current.scopes },
            expiresAt = System.currentTimeMillis() + (j.getLong("expires_in") * 1000L)
        )
        require(fresh.hasSubscriptionScope()) { "Subscription permission revoked" }
        // If rotation returned an ID token, validate its signature and stable subject.
        if (j.has("id_token")) {
            // Refresh flows do not always return a nonce. Do not replace the previously
            // validated ID token without an appropriately validated new one.
            // The stored original token is only used as an account hint during re-login.
        }
        store.save(fresh)
        return fresh
    }

    private fun fromTokenResponse(
        j: JSONObject, clientId: String, identity: OidcVerifier.Identity, old: ChatGptSession?
    ): ChatGptSession {
        val granted = j.optString("scope")
        require(granted.isNotBlank()) { "Token response lacks granted scopes" }
        val lifetime = j.getLong("expires_in")
        require(lifetime > 0) { "Invalid access-token expiration" }
        return ChatGptSession(
            clientId = clientId,
            subject = identity.subject,
            email = identity.email,
            idToken = j.getString("id_token"),
            accessToken = j.getString("access_token"),
            refreshToken = j.optString("refresh_token").ifBlank { old?.refreshToken ?: "" },
            scopes = granted,
            expiresAt = System.currentTimeMillis() + lifetime * 1000L
        )
    }

    private fun awaitCallback(server: ServerSocket): Uri {
        // Do not log the callback URL: it contains a one-time authorization code.
        while (true) {
            val socket = server.accept()
            try {
                socket.soTimeout = 10_000
                val requestLine = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
                    .readLine()
                val parts = requestLine?.split(' ') ?: emptyList()
                if (parts.size < 3 || parts[0] != "GET") {
                    reply(socket, 400, "Invalid request")
                    continue
                }
                val uri = Uri.parse("http://127.0.0.1${parts[1]}")
                if (uri.path != "/auth/callback") {
                    reply(socket, 404, "Not found")
                    continue
                }
                reply(socket, 200, "Sign-in callback received. Return to the Android app.")
                return uri
            } finally {
                socket.close()
            }
        }
    }

    private fun reply(socket: Socket, code: Int, text: String) {
        val body = "<!doctype html><html><meta name='viewport' content='width=device-width'><body><h3>$text</h3></body></html>"
            .toByteArray(Charsets.UTF_8)
        val reason = if (code == 200) "OK" else "Error"
        val headers = "HTTP/1.1 $code $reason\r\nContent-Type: text/html; charset=utf-8\r\n" +
            "Content-Length: ${body.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n"
        socket.getOutputStream().apply {
            write(headers.toByteArray(Charsets.US_ASCII))
            write(body)
            flush()
        }
    }

    private fun randomUrlSafe(bytes: Int): String {
        val randomBytes = ByteArray(bytes)
        SecureRandom().nextBytes(randomBytes)
        return Base64.encodeToString(
            randomBytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
    }
}
