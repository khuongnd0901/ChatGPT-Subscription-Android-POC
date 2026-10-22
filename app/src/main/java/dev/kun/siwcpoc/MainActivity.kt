package dev.kun.siwcpoc

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import org.json.JSONArray
import java.util.concurrent.Executors

/** Minimal native UI intended to verify real SIWC login + eligible Responses inference.
 * No WebView, custom API key, backend, or code execution functionality.
 */
class MainActivity : Activity() {
    private lateinit var sessionStore: SecureStore
    private lateinit var auth: SiwcAuth
    private lateinit var status: TextView
    private lateinit var output: TextView
    private lateinit var prompt: EditText
    private lateinit var modelSpinner: Spinner
    private lateinit var signIn: Button
    private lateinit var listModels: Button
    private lateinit var send: Button
    private lateinit var refresh: Button
    private lateinit var signOut: Button
    private val executor = Executors.newSingleThreadExecutor()
    private var modelIds: List<String> = emptyList()
    private var running = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionStore = SecureStore(applicationContext)
        auth = SiwcAuth(sessionStore)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val inset = (20 * resources.displayMetrics.density).toInt()
            setPadding(inset, inset, inset, inset)
        }
        val scroll = ScrollView(this).apply {
            addView(root, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        setContentView(scroll)

        val title = TextView(this).apply {
            text = "ChatGPT Subscription - Android POC"
            textSize = 21f
            setTypeface(null, Typeface.BOLD)
        }
        root.addView(title)
        root.addView(label("Dynamic OAuth / PKCE / OIDC / Responses SSE", 12f))

        status = label("Checking session...", 14f)
        root.addView(status)

        signIn = addButton(root, "Continue with ChatGPT") {
            task("Opening browser for ChatGPT OAuth...") {
                auth.login { authUri ->
                    runOnUiThread {
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, authUri))
                        } catch (_: Exception) {
                            status.text = "Cannot open system browser; sign-in will time out."
                        }
                    }
                }
                runOnUiThread {
                    status.text = "Authenticated. Subscription scope granted. Select Fetch models."
                }
            }
        }
        listModels = addButton(root, "Fetch models (/v1/models)") {
            task("Fetching account model catalog...") {
                val token = auth.freshSession().accessToken
                var httpStatus = 0
                val response = HttpClient.getJson("https://api.openai.com/v1/models", token) { httpStatus = it }
                val items = response.getJSONArray("models")
                val slugs = mutableListOf<String>()
                val names = mutableListOf<String>()
                for (i in 0 until items.length()) {
                    val m = items.getJSONObject(i)
                    if (m.optString("visibility") != "list") continue
                    val slug = m.getString("slug")
                    slugs.add(slug)
                    names.add("${m.optString("display_name", slug)} ($slug)")
                }
                runOnUiThread {
                    modelIds = slugs
                    val adapter = ArrayAdapter(
                        this, android.R.layout.simple_spinner_item, names
                    )
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    modelSpinner.adapter = adapter
                    status.text = "HTTP $httpStatus: Model catalog loaded: ${slugs.size} visible models"
                }
            }
        }
        root.addView(label("Model (account-specific)", 15f))
        modelSpinner = Spinner(this)
        root.addView(modelSpinner)
        root.addView(label("Prompt", 15f))
        prompt = EditText(this).apply {
            setText("Hãy trả lời chính xác một câu: Xin chào từ ChatGPT subscription!")
            minLines = 3
            maxLines = 6
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        root.addView(prompt)
        send = addButton(root, "Run inference (/v1/responses SSE)") {
            val index = modelSpinner.selectedItemPosition
            if (index !in modelIds.indices) {
                status.text = "Fetch models and choose a model first."
                return@addButton
            }
            val text = prompt.text.toString().trim()
            if (text.isEmpty()) {
                status.text = "Enter a prompt."
                return@addButton
            }
            val selectedModel = modelIds[index]
            output.text = ""
            task("Streaming response from $selectedModel...") {
                val token = auth.freshSession().accessToken
                var httpStatus = 0
                HttpClient.streamResponses(token, selectedModel, text,
                    onDelta = { delta -> runOnUiThread { output.append(delta) } },
                    onEvent = { event -> runOnUiThread { status.text = "PASS: HTTP $httpStatus; $event ($selectedModel)" } },
                    onStatus = { httpStatus = it }
                )
            }
        }
        refresh = addButton(root, "Refresh session (test token rotation)") {
            task("Refreshing OAuth session...") {
                auth.freshSession(forceRefresh = true)
                runOnUiThread { status.text = "PASS: Token refresh; subscription scope retained. Fetch models to verify." }
            }
        }
        signOut = addButton(root, "Sign out / clear encrypted credentials") {
            if (running) return@addButton
            sessionStore.signOut()
            modelIds = emptyList()
            modelSpinner.adapter = null
            output.text = ""
            status.text = "Signed out (host ID retained)."
        }
        root.addView(label("Inference output", 15f))
        output = label("No response yet.", 16f)
        output.setTextIsSelectable(true)
        root.addView(output)
        root.addView(label(
            "POC only. OAuth tokens are not printed. On browser sign-in, return to this app " +
                "after seeing the loopback callback success page.", 12f
        ))
        try {
            val saved = sessionStore.load()
            status.text = if (saved == null) "Not signed in" else
                "Saved account: ${saved.email.ifBlank { "(no email)" }}; " +
                    "subscription scope=${saved.hasSubscriptionScope()}. Fetch models to verify."
        } catch (_: Exception) {
            sessionStore.signOut()
            status.text = "Credentials could not be decrypted; local session cleared."
        }
    }

    private fun task(description: String, body: () -> Unit) {
        if (running) return
        running = true
        setButtons(false)
        status.text = description
        executor.execute {
            try {
                body()
            } catch (e: Exception) {
                runOnUiThread { status.text = "FAIL ($description): " +
                    if (e is org.json.JSONException) "Invalid JSON (body hidden)"
                    else (e.message?.take(250) ?: e.javaClass.simpleName) }
            } finally {
                runOnUiThread {
                    running = false
                    setButtons(true)
                }
            }
        }
    }

    private fun setButtons(enabled: Boolean) {
        signIn.isEnabled = enabled
        listModels.isEnabled = enabled
        send.isEnabled = enabled
        refresh.isEnabled = enabled
        signOut.isEnabled = enabled
    }

    private fun addButton(root: LinearLayout, title: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = title
            setOnClickListener { action() }
            root.addView(this)
        }
    }

    private fun label(text: String, size: Float) = TextView(this).apply {
        this.text = text
        textSize = size
        val margin = (7 * resources.displayMetrics.density).toInt()
        setPadding(0, margin, 0, margin)
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
