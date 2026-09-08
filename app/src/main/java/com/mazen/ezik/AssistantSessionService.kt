package com.vizmazen.assistant

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/**
 * كل مرة تسحب من الزاوية (assist gesture)، النظام بيستدعي onNewSession
 * وبيطلب منها "session" جديدة — وهي دي اللي بترسم النافذة اللي هتشوفها.
 */
class AssistantSessionService : VoiceInteractionSessionService() {

    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return AssistantSession(this)
    }
}
