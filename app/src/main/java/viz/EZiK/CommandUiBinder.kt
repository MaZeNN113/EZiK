package viz.EZiK

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import java.util.concurrent.ExecutorService

/**
 * منطق ربط النافذة (كتابة + صوت + تنفيذ) موحّد في مكان واحد،
 * عشان نستخدمه من AssistantSession (السحب من الزاوية) ومن AssistActivity
 * (احتياطي بعض أجهزة MIUI + زرار الباور).
 *
 * جديد في النسخة دي: التسجيل بيوقف لوحده لما يحس بسكوت (زي Gemini بالظبط)،
 * مش لازم تدوس "إيقاف" يدوي.
 */
object CommandUiBinder {

    private const val SILENCE_THRESHOLD = 900        // تحت الرقم ده = سكوت
    private const val SPEECH_THRESHOLD = 1600         // فوق الرقم ده = فيه كلام فعلاً
    private const val SILENCE_DURATION_MS = 1300L     // سكوت متواصل بعد الكلام = خلاص
    private const val POLL_INTERVAL_MS = 150L
    private const val MAX_RECORDING_MS = 20_000L      // حد أقصى أمان لو الاكتشاف فشل

    fun bind(
        view: View,
        context: Context,
        executor: ExecutorService,
        onFinished: () -> Unit
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
            vadRunnable?.let { mainHandler.removeCallbacks(it) }
            voiceButton.text = context.getString(R.string.btn_voice_start)

            // recorder.stop() ممكن يرمي RuntimeException لو اتوقف بسرعة قوي
            // من غير ما يسجل صوت كفاية — عشان كده جوه runCatching.
            val fileResult = runCatching { recorder.stop() }
            val file = fileResult.getOrNull()
            if (fileResult.isFailure || file == null) {
                Toast.makeText(
                    context,
                    context.getString(R.string.voice_error, "recording too short"),
                    Toast.LENGTH_LONG
                ).show()
                return
            }
            Toast.makeText(context, R.string.voice_transcribing, Toast.LENGTH_SHORT).show()
            executor.execute {
                val transcript = GroqWhisperClient(context).transcribe(file)
                file.delete()
                mainHandler.post {
                    transcript.onSuccess { executeCommand(it) }
                        .onFailure { error ->
                            Toast.makeText(
                                context,
                                context.getString(R.string.voice_error, error.message ?: "transcription unavailable"),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                }
            }
        }

        fun startVadLoop() {
            vadRunnable = object : Runnable {
                override fun run() {
                    if (!isRecording) return
                    val elapsed = System.currentTimeMillis() - recordingStartedAt
                    if (elapsed >= MAX_RECORDING_MS) {
                        stopRecordingAndProcess()
                        return
                    }
                    val amplitude = recorder.currentAmplitude()
                    when {
                        amplitude >= SPEECH_THRESHOLD -> {
                            hasDetectedSpeech = true
                            silenceStartedAt = 0L
                        }
                        amplitude < SILENCE_THRESHOLD && hasDetectedSpeech -> {
                            if (silenceStartedAt == 0L) {
                                silenceStartedAt = System.currentTimeMillis()
                            } else if (System.currentTimeMillis() - silenceStartedAt >= SILENCE_DURATION_MS) {
                                stopRecordingAndProcess()
                                return
                            }
                        }
                        else -> silenceStartedAt = 0L
                    }
                    mainHandler.postDelayed(this, POLL_INTERVAL_MS)
                }
            }
            mainHandler.postDelayed(vadRunnable!!, POLL_INTERVAL_MS)
        }

        sendButton.setOnClickListener {
            val command = input.text.toString().trim()
            if (command.isNotEmpty()) executeCommand(command)
        }

        voiceButton.setOnClickListener {
            if (!isRecording) {
                try {
                    recorder.start()
                    isRecording = true
                    hasDetectedSpeech = false
                    silenceStartedAt = 0L
                    recordingStartedAt = System.currentTimeMillis()
                    voiceButton.text = context.getString(R.string.btn_voice_stop)
                    Toast.makeText(context, R.string.voice_processing, Toast.LENGTH_SHORT).show()
                    startVadLoop()
                } catch (error: Exception) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.voice_error, error.message ?: "microphone unavailable"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                // المستخدم دوس "إيقاف" يدوي بنفسه (اختياري دلوقتي، مش لازم)
                stopRecordingAndProcess()
            }
        }
    }
}
