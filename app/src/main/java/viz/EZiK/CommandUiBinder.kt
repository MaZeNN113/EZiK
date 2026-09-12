package viz.EZiK

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.ExecutorService

/** Shared command surface used by the launcher and both assistant entry paths. */
object CommandUiBinder {
    private const val SILENCE_THRESHOLD = 120
    private const val SPEECH_THRESHOLD = 250
    private const val SILENCE_DURATION_MS = 700L
    private const val POLL_INTERVAL_MS = 80L
    private const val MAX_RECORDING_MS = 12_000L
    private const val MIN_RECORDING_MS = 700L

    fun bind(
        view: View,
        context: Context,
        executor: ExecutorService,
        onFinished: () -> Unit,
        autoStartVoice: Boolean = false
    ) {
        val handler = Handler(Looper.getMainLooper())
        val recorder = VoiceRecorder(context)
        var recording = false
        var processing = false
        var startedAt = 0L
        var heardSpeech = false
        var silenceStartedAt = 0L
        var vad: Runnable? = null

        val input = view.findViewById<EditText>(R.id.commandInput)
        val send = view.findViewById<Button>(R.id.sendButton)
        val voice = view.findViewById<Button>(R.id.voiceButton)
        val status = view.findViewById<TextView?>(R.id.sessionStatus)

        fun setStatus(text: String) {
            status?.text = text
        }

        fun finishSafely() {
            if (!processing) return
            processing = false
            onFinished()
        }

        fun executeCommand(command: String) {
            val text = command.trim()
            if (text.isBlank() || processing) return
            processing = true
            setStatus("Working…")
            send.isEnabled = false
            voice.isEnabled = false

            executor.execute {
                val result = runCatching { ActionExecutor.execute(context, AssistantBrain.resolve(context, text).getOrThrow()) }
                handler.post {
                    send.isEnabled = true
                    voice.isEnabled = true
                    result.onSuccess {
                        Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        Toast.makeText(
                            context,
                            context.getString(R.string.action_failed, it.message ?: "unknown error"),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    finishSafely()
                }
            }
        }

        fun processRecording() {
            if (!recording) return
            recording = false
            vad?.let(handler::removeCallbacks)
            voice.text = context.getString(R.string.btn_voice_start)
            setStatus("Transcribing…")

            val file = runCatching { recorder.stop() }.getOrNull()
            if (file == null || !file.exists() || file.length() == 0L) {
                setStatus("Ready")
                Toast.makeText(context, "Recording was too short", Toast.LENGTH_SHORT).show()
                return
            }

            executor.execute {
                val result = GroqWhisperClient(context).transcribe(file)
                file.delete()
                handler.post {
                    result.onSuccess { transcript ->
                        input.setText(transcript)
                        input.setSelection(input.text.length)
                        executeCommand(transcript)
                    }.onFailure { error ->
                        setStatus("Ready")
                        val message = if (error.message?.contains("API key", true) == true) {
                            context.getString(R.string.groq_key_missing)
                        } else {
                            error.message ?: "transcription unavailable"
                        }
                        Toast.makeText(
                            context,
                            context.getString(R.string.voice_error, message),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

        fun startRecording() {
            if (recording || processing) return
            if (!SecureStore(context).hasApiKey()) {
                Toast.makeText(context, R.string.groq_key_missing, Toast.LENGTH_LONG).show()
                return
            }
            try {
                recorder.start()
                recording = true
                startedAt = System.currentTimeMillis()
                heardSpeech = false
                silenceStartedAt = 0L
                voice.text = context.getString(R.string.btn_voice_stop)
                setStatus("Listening…")

                vad = object : Runnable {
                    override fun run() {
                        if (!recording) return
                        val now = System.currentTimeMillis()
                        val elapsed = now - startedAt
                        val amplitude = recorder.currentAmplitude()

                        when {
                            amplitude >= SPEECH_THRESHOLD -> {
                                heardSpeech = true
                                silenceStartedAt = 0L
                            }
                            heardSpeech && amplitude < SILENCE_THRESHOLD -> {
                                if (silenceStartedAt == 0L) silenceStartedAt = now
                            }
                            else -> silenceStartedAt = 0L
                        }

                        val silenceComplete =
                            silenceStartedAt != 0L && now - silenceStartedAt >= SILENCE_DURATION_MS

                        if ((elapsed >= MIN_RECORDING_MS && heardSpeech && silenceComplete) ||
                            elapsed >= MAX_RECORDING_MS) {
                            processRecording()
                        } else {
                            handler.postDelayed(this, POLL_INTERVAL_MS)
                        }
                    }
                }
                handler.postDelayed(vad!!, POLL_INTERVAL_MS)
            } catch (e: Exception) {
                recording = false
                recorder.cancel()
                setStatus("Ready")
                Toast.makeText(
                    context,
                    context.getString(R.string.voice_error, e.message ?: "microphone unavailable"),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        send.setOnClickListener { executeCommand(input.text.toString()) }
        voice.setOnClickListener {
            if (recording) processRecording() else startRecording()
        }

        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) {
                vad?.let(handler::removeCallbacks)
                if (recording) {
                    recording = false
                    recorder.cancel()
                }
            }
        })

        if (autoStartVoice) {
            handler.postDelayed({ startRecording() }, 300L)
        }
    }
}
