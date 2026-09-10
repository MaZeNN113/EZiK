package viz.EZiK

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/** Optional fallback hook. Command actions will be added incrementally and allow-listed. */
class EZiKAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
}
