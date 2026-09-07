package com.mazen.ezik

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class EZiKAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // UI observation and actions will be added in the agent layer.
    }

    override fun onInterrupt() {
        // Nothing to interrupt yet.
    }
}
