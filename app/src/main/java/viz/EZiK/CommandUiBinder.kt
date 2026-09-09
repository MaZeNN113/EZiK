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
 * عشان نستخدمه من AssistantSession (السحب من الزاوية العادي)
 * ومن AssistActivity (الاحتياطي بتاع بعض أجهزة MIUI) من غير ما نكرر الكود.
 */
object CommandUiBinder {

    fun bind(
        view: View,
        context: Context,
        executor: ExecutorService,
        onFinished: () -> Unit
    ) {
        val mainHandler = Handler(Looper.getMainLooper())
        val recorder = VoiceRecorder(context)
        var isRecording = false

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

        sendButton.setOnClickListener {
            val command = input.text.toString().trim()
            if (command.isNotEmpty()) executeCommand(command)
        }

        voiceButton.setOnClickListener {
            if (!isRecording) {
                try {
                    recorder.start()
                    isRecording = true
                    voiceButton.text = context.getString(R.string.btn_voice_stop)
                    Toast.makeText(context, R.string.voice_processing, Toast.LENGTH_SHORT).show()
                } catch (error: Exception) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.voice_error, error.message ?: "microphone unavailable"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                isRecording = false
                voiceButton.text = context.getString(R.string.btn_voice_start)
                // recorder.stop() ممكن يرمي RuntimeException لو اتقفل بسرعة قوي
                // من غير ما يسجل صوت كفاية — عشان كده هنا جوه runCatching، مش
                // زي قبل كده. من غيرها كانت بتقفل الجلسة كلها فجأة.
                val fileResult = runCatching { recorder.stop() }
                val file = fileResult.getOrNull()
                if (fileResult.isFailure || file == null) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.voice_error, "recording too short"),
                        Toast.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
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
        }
    }
}
