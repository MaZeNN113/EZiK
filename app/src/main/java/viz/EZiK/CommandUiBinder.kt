package viz.EZiK

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import java.util.concurrent.ExecutorService

/** Shared command UI for the launcher, assist activity, and voice session. */
object CommandUiBinder {
    private const val SILENCE_THRESHOLD = 120
    private const val SPEECH_THRESHOLD = 250
    private const val SILENCE_DURATION_MS = 550L
    private const val POLL_INTERVAL_MS = 80L
    private const val MAX_RECORDING_MS = 8_000L
    private const val MIN_RECORDING_MS = 900L

    fun bind(
        view: View,
        context: Context,
        executor: ExecutorService,
        onFinished: () -> Unit,
        autoStartVoice: Boolean = false
    ) {
        val mainHandler = Handler(Looper.getMainLooper())
        val recorder = VoiceRecorder(context)
        var isRecording = false
        var recordingStartedAt = 0L
        var hasDetectedSpeech = false
        var silenceStartedAt = 0L
        var vadRunnable: Runnable? = null

        val input = view.findViewById<EditText>(R.id.commandInput)
        val sendButton = view.findViewById<Button>(R.id.sendButton)
        val voiceButton = view.findViewById<Button>(R.id.voiceButton)

        fun executeCommand(command: String) {
            if (command.isBlank()) return
            executor.execute {
                val result = runCatching { ActionExecutor.execute(context, CommandPlanner.plan(command)) }
                mainHandler.post {
                    result.onSuccess { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                        .onFailure { error -> Toast.makeText(context, error.message ?: "Command failed", Toast.LENGTH_LONG).show() }
                    onFinished()
                }
            }
        }

        fun stopRecordingAndProcess() {
            if (!isRecording) return
            isRecording = false
            vadRunnable?.let(mainHandler::removeCallbacks)
            voiceButton.text = context.getString(R.string.btn_voice_start)
            val file = runCatching { recorder.stop() }.getOrNull()
            if (file == null) {
                Toast.makeText(context, context.getString(R.string.voice_error, "recording too short"), Toast.LENGTH_LONG).show()
                return
            }
            Toast.makeText(context, R.string.voice_transcribing, Toast.LENGTH_SHORT).show()
            executor.execute {
                val transcript = GroqWhisperClient(context).transcribe(file)
                file.delete()
                mainHandler.post {
                    transcript.onSuccess { text ->
                        input.setText(text)
                        executeCommand(text)
                    }.onFailure { error ->
                        Toast.makeText(context, context.getString(R.string.voice_error, error.message ?: "transcription unavailable"), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        fun startRecording() {
            if (isRecording) return
            try {
                recorder.start()
                isRecording = true
                hasDetectedSpeech = false
                silenceStartedAt = 0L
                recordingStartedAt = System.currentTimeMillis()
                voiceButton.text = context.getString(R.string.btn_voice_stop)
                Toast.makeText(context, R.string.voice_processing, Toast.LENGTH_SHORT).show()
                vadRunnable = object : Runnable {
                    override fun run() {
                        if (!isRecording) return
                        val now = System.currentTimeMillis()
                        val elapsed = now - recordingStartedAt
                        val amplitude = recorder.currentAmplitude()
                        if (amplitude >= SPEECH_THRESHOLD) {
                            hasDetectedSpeech = true
                            silenceStartedAt = 0L
                        } else if (amplitude < SILENCE_THRESHOLD && hasDetectedSpeech) {
                            if (silenceStartedAt == 0L) silenceStartedAt = now
                        } else if (amplitude >= SILENCE_THRESHOLD) {
                            silenceStartedAt = 0L
                        }
                        val silenceLongEnough = silenceStartedAt != 0L && now - silenceStartedAt >= SILENCE_DURATION_MS
                        if ((elapsed >= MIN_RECORDING_MS && hasDetectedSpeech && silenceLongEnough) || elapsed >= MAX_RECORDING_MS) {
                            stopRecordingAndProcess()
                        } else {
                            mainHandler.postDelayed(this, POLL_INTERVAL_MS)
                        }
                    }
                }
                mainHandler.postDelayed(vadRunnable!!, POLL_INTERVAL_MS)
            } catch (error: Exception) {
                Toast.makeText(context, context.getString(R.string.voice_error, error.message ?: "microphone unavailable"), Toast.LENGTH_LONG).show()
            }
        }

        sendButton.setOnClickListener { executeCommand(input.text.toString().trim()) }
        voiceButton.setOnClickListener {
            if (isRecording) stopRecordingAndProcess() else startRecording()
        }

        if (autoStartVoice) {
            // Post until the session window is attached and the microphone permission
            // dialog, if any, has settled; do not require a second Speak tap.
            mainHandler.postDelayed({ startRecording() }, 250L)
        }
    }
}
