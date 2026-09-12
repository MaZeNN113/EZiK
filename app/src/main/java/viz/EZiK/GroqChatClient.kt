package viz.EZiK

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL

class GroqChatClient(context: Context) {
    private val apiKey = SecureStore(context).getApiKey()

    fun decide(userText: String): Result<AssistantAction> = runCatching {
        require(!apiKey.isNullOrBlank()) { "Groq API key is not configured" }

        val body = JSONObject().apply {
            put("model", MODEL)
            put("temperature", 0.1)
            put("max_completion_tokens", 700)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                put(JSONObject().put("role", "user").put("content", userText))
            })
        }

        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            useCaches = false
            connectTimeout = 10_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }

        try {
            DataOutputStream(connection.outputStream).use { it.write(body.toString().toByteArray()) }
            val code = connection.responseCode
            val response = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("Groq HTTP $code")

            val content = JSONObject(response)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .optString("content")
                .trim()

            parseAction(content)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseAction(content: String): AssistantAction {
        val cleaned = content
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val json = JSONObject(cleaned)
        return when (json.optString("action")) {
            "launch_app" -> AssistantAction.LaunchApp(json.optString("app"))
            "search_web" -> AssistantAction.WebSearch(json.optString("query"))
            "open_url" -> AssistantAction.OpenUrl(json.optString("url"))
            "open_settings" -> {
                val action = when (json.optString("setting")) {
                    "wifi" -> android.provider.Settings.ACTION_WIFI_SETTINGS
                    "bluetooth" -> android.provider.Settings.ACTION_BLUETOOTH_SETTINGS
                    "apps" -> android.provider.Settings.ACTION_APPLICATION_SETTINGS
                    else -> throw IllegalArgumentException("Unsupported setting")
                }
                AssistantAction.OpenSettings(action)
            }
            "answer" -> AssistantAction.Answer(json.optString("text"))
            else -> throw IllegalArgumentException("Unsupported AI action")
        }
    }

    companion object {
        private const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
        private const val MODEL = "openai/gpt-oss-120b"

        private const val SYSTEM_PROMPT = """
You are EZiK, an Android personal assistant. Your job is to turn a user's request into ONE safe, typed action.
Return ONLY valid JSON, no markdown and no extra text.

Allowed actions:
{"action":"launch_app","app":"Goodreads"}
{"action":"search_web","query":"..."}
{"action":"open_url","url":"https://..."}
{"action":"open_settings","setting":"wifi|bluetooth|apps"}
{"action":"answer","text":"..."}

Rules:
- Never invent a package name.
- Never output shell commands, accessibility coordinates, intents, permissions, or code.
- Never perform destructive, financial, account, messaging, calling, or privacy-sensitive actions.
- If the user asks for something outside the allowed actions, return an "answer" explaining that it is not implemented yet.
- Understand Egyptian Arabic, Modern Standard Arabic, and English.
- Preserve app names, URLs, titles, and quoted text.
- Keep answer text concise and useful.
"""
    }
}
