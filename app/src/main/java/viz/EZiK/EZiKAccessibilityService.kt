package viz.EZiK

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Explicitly opt-in bridge for future screen-aware actions.
 * No arbitrary clicks or shell commands are executed from accessibility events.
 */
class EZiKAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile private var instance: EZiKAccessibilityService? = null
        fun isEnabled(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }
}
