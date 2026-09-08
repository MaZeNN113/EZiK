package com.vizmazen.EZiK

import android.content.Context
import android.service.voice.VoiceInteractionSession
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.util.concurrent.Executors

/**
 * دي النافذة الفعلية اللي بتظهر لما تسحب من زاوية الشاشة.
 *
 * دلوقتي هي بس بتاخد النص وبتعمله Toast — الخطوة الجاية هنبعت
 * النص (أو الصوت اللي هيتحول نص عن طريق Groq Whisper) لموديل الـ AI
 * عشان يقرر يعمل إيه بالظبط.
 */
class AssistantSession(context: Context) : VoiceInteractionSession(context) {

    private val recorder = VoiceRecorder(context)
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isRecording = false

    override fun onCreateContentView(): View {
        val view = LayoutInflater.from(context).inflate(R.layout.session_assistant, null)

        val input = view.findViewById<EditText>(R.id.commandInput)
        val sendButton = view.findViewById<Button>(R.id.sendButton)
        val voiceButton = view.findViewById<Button>(R.id.voiceButton)

        sendButton.setOnClickListener {
            val command = input.text.toString().trim()
            if (command.isNotEmpty()) {
                executeCommand(command)
            }
        }

        voiceButton.setOnClickListener {
            if (!isRecording) {
                try {
                    recorder.start()
                    isRecording = true
                    voiceButton.text = context.getString(R.string.btn_voice_stop)
                    Toast.makeText(context, R.string.voice_processing, Toast.LENGTH_SHORT).show()
                } catch (error: Exception) {
                    Toast.makeText(context, context.getString(R.string.voice_error, error.message ?: "microphone unavailable"), Toast.LENGTH_LONG).show()
                }
            } else {
                isRecording = false
                voiceButton.text = context.getString(R.string.btn_voice_start)
                val file = recorder.stop() ?: return@setOnClickListener
                Toast.makeText(context, R.string.voice_transcribing, Toast.LENGTH_SHORT).show()
                executor.execute {
                    val transcript = GroqWhisperClient(context).transcribe(file)
                    file.delete()
                    mainHandler.post {
                        transcript.onSuccess { executeCommand(it) }
                            .onFailure { error -> Toast.makeText(context, context.getString(R.string.voice_error, error.message ?: "transcription unavailable"), Toast.LENGTH_LONG).show() }
                    }
                }
            }
        }

        return view
    }

    private fun executeCommand(command: String) {
        executor.execute {
            val result = runCatching { ActionExecutor.execute(context, CommandPlanner.plan(command)) }
            mainHandler.post {
                result.onSuccess { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                    .onFailure { error -> Toast.makeText(context, error.message ?: "Command failed", Toast.LENGTH_LONG).show() }
                hide()
            }
        }
    }

    override fun onDestroy() {
        recorder.cancel()
        executor.shutdownNow()
        super.onDestroy()
    }
}
