package com.vizmazen.assistant

import android.content.Context
import android.service.voice.VoiceInteractionSession
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast

/**
 * دي النافذة الفعلية اللي بتظهر لما تسحب من زاوية الشاشة.
 *
 * دلوقتي هي بس بتاخد النص وبتعمله Toast — الخطوة الجاية هنبعت
 * النص (أو الصوت اللي هيتحول نص عن طريق Groq Whisper) لموديل الـ AI
 * عشان يقرر يعمل إيه بالظبط.
 */
class AssistantSession(context: Context) : VoiceInteractionSession(context) {

    override fun onCreateContentView(): View {
        val view = LayoutInflater.from(context).inflate(R.layout.session_assistant, null)

        val input = view.findViewById<EditText>(R.id.commandInput)
        val sendButton = view.findViewById<Button>(R.id.sendButton)

        sendButton.setOnClickListener {
            val command = input.text.toString().trim()
            if (command.isNotEmpty()) {
                // TODO (الخطوة الجاية): ابعت "command" لموديل الـ AI بدل الـ Toast دي
                Toast.makeText(context, "Received command: $command", Toast.LENGTH_SHORT).show()
                hide()
            }
        }

        return view
    }
}
