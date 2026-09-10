package viz.EZiK

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class GroqWhisperClient(
    private val context: Context,
    private val apiKey: String = BuildConfig.GROQ_API_KEY
) {
    fun transcribe(audioFile: File): Result<String> = runCatching {
        require(apiKey.isNotBlank()) { "Groq API key is not configured" }
        require(audioFile.exists()) { "Audio file does not exist" }
        val boundary = "----EZiK-${UUID.randomUUID()}"
        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 45_000
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }

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

        val body = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (connection.responseCode !in 200..299) error("Groq HTTP ${connection.responseCode}: $body")
        JSONObject(body).optString("text").trim().also { require(it.isNotBlank()) { "Groq returned empty transcription" } }
    }

    private fun writeField(output: DataOutputStream, boundary: String, name: String, value: String) {
        output.writeBytes("--$boundary\r\n")
        output.writeBytes("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
        output.writeBytes("$value\r\n")
    }

    companion object {
        private const val ENDPOINT = "https://api.groq.com/openai/v1/audio/transcriptions"
        private const val MODEL = "whisper-large-v3-turbo"
        private const val PROMPT = "EZiK voice assistant command, spoken in Egyptian Arabic or English. The assistant's name is EZiK (pronounced ee-zik), often said first like \"EZiK, open Goodreads\". Preserve app names, book titles, URLs, and English words exactly."
    }
}
