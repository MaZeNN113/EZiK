package com.vizmazen.EZiK

import android.service.voice.VoiceInteractionService

/**
 * دي الخدمة الأساسية اللي بتخلي النظام يعتبر التطبيق ده "مساعد رقمي".
 * مش لازم نكتب فيها حاجة كتير دلوقتي — شغلها الحقيقي بيحصل في AssistantSession.
 */
class AssistantInteractionService : VoiceInteractionService() {

    override fun onReady() {
        super.onReady()
    }
}
