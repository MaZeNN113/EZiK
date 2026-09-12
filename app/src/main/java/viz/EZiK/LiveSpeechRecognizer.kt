package viz.EZiK

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/** Low-latency live recognition. Prefers on-device, then the system recognizer. */
class LiveSpeechRecognizer(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null
    private var delivered = false

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(onState: (String) -> Unit, onResult: (Result<String>) -> Unit) {
        stop()
        delivered = false
        val onDevice = SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        recognizer = runCatching {
            if (onDevice) SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            else SpeechRecognizer.createSpeechRecognizer(context)
        }.getOrElse {
            onResult(Result.failure(it)); return
        }
        recognizer!!.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { onState("Listening") }
            override fun onBeginningOfSpeech() { onState("Listening") }
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { onState("Processing") }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { onState(it) }
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onError(error: Int) {
                if (!delivered) {
                    delivered = true
                    cleanup()
                    onResult(Result.failure(IllegalStateException("Speech recognition error $error")))
                }
            }
            override fun onResults(results: Bundle?) {
                if (delivered) return
                delivered = true
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                cleanup()
                if (text.isBlank()) onResult(Result.failure(IllegalStateException("Empty speech result")))
                else onResult(Result.success(text))
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 350L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 250L)
        }
        recognizer!!.startListening(intent)
    }

    fun stop() {
        recognizer?.runCatching { stopListening() }
        cleanup()
    }

    fun cancel() {
        recognizer?.runCatching { cancel() }
        cleanup()
    }

    private fun cleanup() {
        recognizer?.runCatching { destroy() }
        recognizer = null
    }
}
