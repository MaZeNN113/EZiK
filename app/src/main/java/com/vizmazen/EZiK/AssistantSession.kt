package com.vizmazen.EZiK

import android.content.Context
import android.service.voice.VoiceInteractionSession
import android.view.LayoutInflater
import android.view.View
import java.util.concurrent.Executors

/**
 * دي النافذة الفعلية اللي بتظهر لما تسحب من زاوية الشاشة.
 * كل منطق الكتابة/الصوت/التنفيذ بقى في CommandUiBinder (مشترك مع AssistActivity).
 */
class AssistantSession(context: Context) : VoiceInteractionSession(context) {

    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreateContentView(): View {
        val view = LayoutInflater.from(context).inflate(R.layout.session_assistant, null)
        CommandUiBinder.bind(view, context, executor, onFinished = { hide() })
        return view
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
