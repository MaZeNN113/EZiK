package viz.EZiK

import android.content.Context
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class GroqWhisperClient(private val context: Context) {
    fun transcribe(audioFile: File): Result<String> = runCatching {
        val apiKey = SecurePrefs.getGroqKey(context)
        require(apiKey.isNotBlank()) { "Add your Groq API key in EZiK settings" }
        require(audioFile.exists()) { "Audio file does not exist" }
        val boundary = "----EZiK-${UUID.randomUUID()}"
        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 4_000
            readTimeout = 12_000
            useCaches = false
            setRequestProperty("Authorization", "Bearer $apiKey")
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
                audioFile.inputStream().use { it.copyTo(output, 16 * 1024) }
                output.writeBytes("\r\n--$boundary--\r\n")
            }
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("Groq HTTP $code")
            JSONObject(body).optString("text").trim().also {
                require(it.isNotBlank()) { "Groq returned empty transcription" }
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
        private const val PROMPT = "EZiK voice assistant command in Egyptian Arabic or English. Preserve app names, book titles, URLs and English words. EZiK may be misheard as Isaac or Ezekiel."
    }
}
