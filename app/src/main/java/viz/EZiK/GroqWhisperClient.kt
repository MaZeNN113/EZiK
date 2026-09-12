package viz.EZiK

import android.content.Context
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class GroqWhisperClient(context: Context) {
    private val apiKey = SecureStore(context).getApiKey()

    fun transcribe(audioFile: File): Result<String> = runCatching {
        require(!apiKey.isNullOrBlank()) { "Groq API key is not configured" }
        require(audioFile.exists() && audioFile.length() > 0) { "Audio file is empty" }

        val boundary = "----EZiK-${UUID.randomUUID()}"
        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            useCaches = false
            connectTimeout = 10_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }

        try {
            DataOutputStream(connection.outputStream).use { output ->
                writeField(output, boundary, "model", MODEL)
                writeField(output, boundary, "response_format", "json")
                writeField(output, boundary, "temperature", "0")
                writeField(output, boundary, "prompt", PROMPT)
                output.writeBytes("--$boundary\r\n")
                output.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"command.m4a\"\r\n")
                output.writeBytes("Content-Type: audio/mp4\r\n\r\n")
                audioFile.inputStream().use { it.copyTo(output) }
                output.writeBytes("\r\n--$boundary--\r\n")
            }

            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (code !in 200..299) {
                val detail = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }.getOrNull()
                error("Groq HTTP $code${detail?.let { ": $it" } ?: ""}")
            }

            JSONObject(body).optString("text").trim().also {
                require(it.isNotBlank()) { "Groq returned an empty transcription" }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun writeField(output: DataOutputStream, boundary: String, name: String, value: String) {
        output.writeBytes("--$boundary\r\n")
        output.writeBytes("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
        output.writeBytes("$value\r\n")
    }

    companion object {
        private const val ENDPOINT = "https://api.groq.com/openai/v1/audio/transcriptions"
        private const val MODEL = "whisper-large-v3-turbo"
        private const val PROMPT =
            "EZiK voice assistant command. Spoken in Egyptian Arabic or English. " +
            "The assistant name is EZiK, pronounced ee-zik. It may be said first. " +
            "Preserve app names, URLs, book titles, numbers and English words accurately."
    }
}
