package com.vizmazen.EZiK

import android.app.Activity
import android.os.Bundle

/**
 * Standard ACTION_ASSIST entry point used by RoleManager on devices that
 * enumerate assistant activities instead of VoiceInteractionService entries.
 */
class AssistActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
