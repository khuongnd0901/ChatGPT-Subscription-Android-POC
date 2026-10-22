package dev.kun.siwcpoc

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicBoolean

class ApiException(val statusCode: Int, message: String) : Exception("HTTP $statusCode: $message")

/** HTTPS-only network client: never print Authorization headers, request bodies or tokens. */
object HttpClient {
    fun getJson(url: String, bearer: String? = null): JSONObject {
        return requestJson(url, "GET", bearer, null, null)
    }

    fun postForm(url: String, fields: Map<String, String>): JSONObject {
        val body = fields.entries.joinToString("&") {
            "${encode(it.key)}=${encode(it.value)}"
        }.toByteArray(Charsets.UTF_8)
        return requestJson(url, "POST", null, "application/x-www-form-urlencoded", body)
    }

    fun requestJson(
        url: String, method: String, bearer: String?, contentType: String?, body: ByteArray?
    ): JSONObject {
        val conn = connection(url, method, bearer)
        return try {
            if (body != null) {
                conn.setRequestProperty("Content-Type", contentType ?: "application/json")
                conn.doOutput = true
                conn.outputStream.use { it.write(body) }
            }
            val status = conn.responseCode
            val response = readLimited(if (status in 200..299) conn.inputStream else conn.errorStream)
            if (status !in 200..299) throw ApiException(status, safeMessage(response))
            JSONObject(response)
        } finally {
            conn.disconnect()
        }
    }

    fun streamResponses(
        accessToken: String,
        model: String,
        prompt: String,
        onDelta: (String) -> Unit,
        onEvent: (String) -> Unit
    ) {
        val input = org.json.JSONArray().put(
            JSONObject().put("role", "user").put("content", prompt)
        )
        val payload = JSONObject().apply {
            put("model", model)
            put("input", input)
            put("store", false)
            put("stream", true)
            put("instructions", "Respond clearly in Vietnamese.")
        }
        val conn = connection("https://api.openai.com/v1/responses", "POST", accessToken)
        conn.readTimeout = 120_000
        val completed = AtomicBoolean(false)
        try {
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "text/event-stream")
            conn.doOutput = true
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val status = conn.responseCode
            if (status !in 200..299) {
                throw ApiException(status, safeMessage(readLimited(conn.errorStream)))
            }
            val reader = BufferedReader(conn.inputStream.reader(Charsets.UTF_8))
            var dataLines = mutableListOf<String>()
            fun dispatch() {
                if (dataLines.isEmpty()) return
                val raw = dataLines.joinToString("\n")
                dataLines = mutableListOf()
                if (raw == "[DONE]") return
                val j = JSONObject(raw)
                val event = j.optString("type")
                when (event) {
                    "response.output_text.delta" -> onDelta(j.optString("delta"))
                    "response.completed" -> {
                        completed.set(true)
                        onEvent("response.completed")
                    }
                    "response.failed" -> {
                        val err = j.optJSONObject("response")?.optJSONObject("error")
                        throw IllegalStateException("Response failed: ${err?.optString("code", "unknown")}")
                    }
                    "error" -> throw IllegalStateException("SSE error: ${j.optString("code", "unknown")}")
                }
            }
            reader.useLines { lines ->
                for (line in lines) {
                    if (line.isEmpty()) dispatch()
                    else if (line.startsWith("data:")) dataLines.add(line.substring(5).trimStart())
                }
            }
            dispatch()
            if (!completed.get()) error("SSE ended without response.completed")
        } finally {
            conn.disconnect()
        }
    }

    private fun connection(url: String, method: String, bearer: String?): HttpURLConnection {
        require(url.startsWith("https://")) { "Only HTTPS is allowed" }
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", "SIWC-Android-POC/0.1")
            if (bearer != null) setRequestProperty("Authorization", "Bearer $bearer")
        }
    }

    private fun readLimited(stream: InputStream?): String {
        if (stream == null) return "(empty response)"
        return stream.bufferedReader(Charsets.UTF_8).use { reader ->
            val chars = CharArray(8192)
            val n = reader.read(chars)
            if (n < 0) "" else String(chars, 0, n)
        }
    }

    private fun safeMessage(response: String): String {
        // Avoid showing raw token-like fields from a provider's error response.
        return try {
            val j = JSONObject(response)
            val e = j.optJSONObject("error")
            val code = e?.optString("code") ?: j.optString("code", "request_failed")
            val message = e?.optString("message") ?: j.optString("message", "")
            "$code ${message.take(220)}"
        } catch (_: Exception) {
            "Request failed (non-JSON response)"
        }
    }

    private fun encode(s: String): String = URLEncoder.encode(s, "UTF-8")
}
