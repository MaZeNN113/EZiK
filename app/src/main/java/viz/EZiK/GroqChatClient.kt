package viz.EZiK

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/** Optional cloud reasoning layer. Offline/direct rules always run first. */
object GroqChatClient {
    fun resolve(context: Context, command: String): AssistantAction? {
        val key = SecurePrefs.getGroqKey(context)
        if (key.isBlank()) return null
        return runCatching {
            val body = JSONObject().apply {
                put("model", "openai/gpt-oss-120b")
                put("temperature", 0.1)
                put("max_completion_tokens", 300)
                put("messages", org.json.JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", "You are EZiK, an Android assistant. Return ONLY JSON with type and fields. Allowed type: launch_app, search_in_app, tap_in_app, type_in_app, scroll_in_app, back_in_app, web_search, open_url, speak. Never invent package names. For app actions, app is the human-readable app name."))
                    put(JSONObject().put("role", "user").put("content", command))
                })
            }
            val conn = URL("https://api.groq.com/openai/v1/chat/completions").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 15000
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $key")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            val text = (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream).bufferedReader().use { it.readText() }
            if (conn.responseCode !in 200..299) return null
            val content = JSONObject(text).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
            val json = JSONObject(content.removePrefix("```").removePrefix("json").removeSuffix("```").trim())
            when (json.getString("type")) {
                "launch_app" -> AssistantAction.LaunchApp(json.getString("app"))
                "search_in_app" -> AssistantAction.SearchInApp(json.getString("app"), json.getString("query"))
                "tap_in_app" -> AssistantAction.TapInApp(json.getString("app"), json.getString("target"))
                "type_in_app" -> AssistantAction.TypeInApp(json.getString("app"), json.getString("text"))
                "scroll_in_app" -> AssistantAction.ScrollInApp(json.getString("app"), json.optBoolean("forward", true))
                "back_in_app" -> AssistantAction.BackInApp(json.getString("app"))
                "web_search" -> AssistantAction.WebSearch(json.getString("query"))
                "open_url" -> AssistantAction.OpenUrl(json.getString("url"))
                "speak" -> AssistantAction.Speak(json.getString("text"))
                else -> null
            }
        }.getOrNull()
    }
}
