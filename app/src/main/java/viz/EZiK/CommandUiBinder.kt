package viz.EZiK

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.ExecutorService

/** Shared, lifecycle-safe command UI for launcher, assistant session and fallback activity. */
object CommandUiBinder {
    private const val MAX_FALLBACK_RECORDING_MS = 6_000L
    private const val MIN_FALLBACK_RECORDING_MS = 500L

    fun bind(
        view: View,
        context: Context,
        executor: ExecutorService,
        onFinished: () -> Unit,
        autoStartVoice: Boolean = false
    ) {
        val main = Handler(Looper.getMainLooper())
        val liveSpeech = LiveSpeechRecognizer(context)
        val recorder = VoiceRecorder(context)
        var fallbackRecording = false
        var liveRecording = false
        var fallbackStarted = 0L
        var fallbackStop: Runnable? = null
        var destroyed = false

        val input = view.findViewById<EditText>(R.id.commandInput)
        val send = view.findViewById<Button>(R.id.sendButton)
        val voice = view.findViewById<Button>(R.id.voiceButton)
        val status = view.findViewById<TextView>(R.id.voiceStatus)

        fun setState(text: String) {
            status?.text = text
            status?.visibility = if (text.isBlank()) View.GONE else View.VISIBLE
        }

        fun executeCommand(command: String) {
            val clean = command.trim()
            if (clean.isBlank()) return
            setState("Working…")
            send.isEnabled = false
            voice.isEnabled = false
            executor.execute {
                val result = runCatching { ActionExecutor.execute(context, CommandPlanner.plan(clean)) }
                main.post {
                    send.isEnabled = true
                    voice.isEnabled = true
                    result.onSuccess { message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        setState("")
                        onFinished()
                    }.onFailure { error ->
                        setState("Failed")
                        Toast.makeText(context, error.message ?: "Command failed", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        fun finishFallbackRecording() {
            if (!fallbackRecording) return
            fallbackRecording = false
            fallbackStop?.let(main::removeCallbacks)
            fallbackStop = null
            val file = runCatching { recorder.stop() }.getOrNull()
            voice.isEnabled = true
            if (file == null) {
                setState("Voice recording failed")
                return
            }
            if (System.currentTimeMillis() - fallbackStarted < MIN_FALLBACK_RECORDING_MS) {
                file.delete()
                setState("Speak a little longer")
                return
            }
            setState("Transcribing with Whisper…")
            executor.execute {
                val result = GroqWhisperClient(context).transcribe(file)
                file.delete()
                main.post {
                    result.onSuccess { text ->
                        input.setText(text)
                        input.setSelection(input.text.length)
                        setState("Recognized")
                        executeCommand(text)
                    }.onFailure { error ->
                        setState("Voice failed")
                        Toast.makeText(context, error.message ?: "Whisper failed", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        fun startFallbackRecording() {
            if (fallbackRecording) return
            runCatching { recorder.start() }.onFailure {
                setState("Microphone unavailable")
                Toast.makeText(context, it.message ?: "Microphone unavailable", Toast.LENGTH_LONG).show()
                return
            }
            fallbackRecording = true
            fallbackStarted = System.currentTimeMillis()
            voice.text = context.getString(R.string.btn_voice_stop)
            setState("Listening…")
            fallbackStop = Runnable { finishFallbackRecording() }.also { main.postDelayed(it, MAX_FALLBACK_RECORDING_MS) }
        }

        fun startVoice() {
            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                setState("Microphone permission required")
                Toast.makeText(context, R.string.mic_permission_required, Toast.LENGTH_LONG).show()
                return
            }
            voice.text = context.getString(R.string.btn_voice_stop)
            send.isEnabled = false
            if (liveSpeech.isAvailable()) {
                liveRecording = true
                liveSpeech.start(
                    onState = { state -> main.post { if (!destroyed) setState(state) } },
                    onResult = { result ->
                        main.post {
                            if (destroyed) return@post
                            liveRecording = false
                            voice.text = context.getString(R.string.btn_voice_start)
                            send.isEnabled = true
                            result.onSuccess { text ->
                                input.setText(text)
                                input.setSelection(input.text.length)
                                setState("Recognized")
                                executeCommand(text)
                            }.onFailure { error ->
                                setState("Voice failed")
                                Toast.makeText(context, error.message ?: "Speech recognition failed", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
            } else {
                startFallbackRecording()
            }
        }

        fun stopVoice() {
            if (liveRecording) {
                liveRecording = false
                liveSpeech.stop()
                voice.text = context.getString(R.string.btn_voice_start)
                send.isEnabled = true
                setState("")
            } else if (fallbackRecording) {
                finishFallbackRecording()
                voice.text = context.getString(R.string.btn_voice_start)
            }
        }

        send.setOnClickListener { executeCommand(input.text.toString()) }
        voice.setOnClickListener {
            if (liveRecording || fallbackRecording) stopVoice() else startVoice()
        }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                executeCommand(input.text.toString()); true
            } else false
        }

        if (autoStartVoice) {
            main.postDelayed({
                if (!destroyed) {
                    if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startVoice()
                    else setState("Microphone permission required")
                }
            }, 120L)
        }

        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) {
                destroyed = true
                fallbackStop?.let(main::removeCallbacks)
                liveSpeech.cancel()
                recorder.cancel()
                executor.shutdownNow()
            }
        })
    }
}
