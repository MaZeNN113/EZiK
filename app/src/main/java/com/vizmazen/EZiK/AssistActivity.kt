package com.vizmazen.EZiK

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Standard ACTION_ASSIST entry point used by RoleManager on devices that
 * enumerate assistant activities instead of VoiceInteractionService entries.
 */
class AssistActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Some Xiaomi builds enumerate ACTION_ASSIST instead of opening the
        // VoiceInteractionSession. Do not finish immediately: hand off to the
        // real setup/command screen so the assistant invocation remains usable.
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
        finish()
    }
}
