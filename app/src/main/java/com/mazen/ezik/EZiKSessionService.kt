package com.mazen.ezik
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
class EZiKSessionService: VoiceInteractionSessionService(){override fun onNewSession(args:Bundle?):VoiceInteractionSession=EZiKSession(this)}
