package viz.EZiK

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Generic UI bridge for the currently visible accessibility tree. */
class EZiKAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile private var instance: EZiKAccessibilityService? = null

        fun isReady(): Boolean = instance != null
        fun performBack(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_BACK) == true
        fun scrollForward(): Boolean = findScrollable()?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true
        fun scrollBackward(): Boolean = findScrollable()?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) == true

        fun search(query: String): Boolean = runOnService {
            val root = rootInActiveWindow ?: return@runOnService false
            val searchNode = findSearchNode(root) ?: return@runOnService false
            if (searchNode.isEditable) {
                searchNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                searchNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, android.os.Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, query)
                })
                return@runOnService true
            }
            if (searchNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                Handler(Looper.getMainLooper()).postDelayed({
                    val refreshed = rootInActiveWindow ?: return@postDelayed
                    findEditable(refreshed)?.apply {
                        performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                        performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, android.os.Bundle().apply {
                            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, query)
                        })
                        performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }
                }, 350L)
                return@runOnService true
            }
            false
        } == true

        fun tap(target: String): Boolean = runOnService {
            val root = rootInActiveWindow ?: return@runOnService false
            findBestNode(root, target)?.let { node ->
                if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return@runOnService true
                var parent = node.parent
                repeat(3) {
                    if (parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return@runOnService true
                    parent = parent?.parent
                }
            }
            false
        } == true

        fun type(text: String): Boolean = runOnService {
            val root = rootInActiveWindow ?: return@runOnService false
            val field = findEditable(root) ?: return@runOnService false
            field.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, android.os.Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            })
        } == true

        private fun findSearchNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            val nodes = mutableListOf<AccessibilityNodeInfo>()
            collect(root, nodes)
            val words = listOf("search", "find", "بحث", "بحث عن", "rechercher")
            return nodes.firstOrNull { node ->
                val hay = "${node.text ?: ""} ${node.contentDescription ?: ""}".lowercase(Locale.ROOT)
                words.any { hay.contains(it) }
            } ?: findEditable(root)
        }

        private fun findEditable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            val nodes = mutableListOf<AccessibilityNodeInfo>()
            collect(root, nodes)
            return nodes.firstOrNull { it.isEditable && it.isVisibleToUser }
        }

        private fun findBestNode(root: AccessibilityNodeInfo, target: String): AccessibilityNodeInfo? {
            val q = target.trim().lowercase(Locale.ROOT)
            if (q.isEmpty()) return null
            val nodes = mutableListOf<AccessibilityNodeInfo>()
            collect(root, nodes)
            return nodes.firstOrNull {
                val text = (it.text?.toString() ?: "").lowercase(Locale.ROOT)
                val desc = (it.contentDescription?.toString() ?: "").lowercase(Locale.ROOT)
                text == q || desc == q
            } ?: nodes.firstOrNull {
                val text = (it.text?.toString() ?: "").lowercase(Locale.ROOT)
                val desc = (it.contentDescription?.toString() ?: "").lowercase(Locale.ROOT)
                text.contains(q) || desc.contains(q)
            }
        }

        private fun findScrollable(): AccessibilityNodeInfo? {
            val root = instance?.rootInActiveWindow ?: return null
            val nodes = mutableListOf<AccessibilityNodeInfo>()
            collect(root, nodes)
            return nodes.firstOrNull { it.isScrollable }
        }

        private fun collect(node: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>) {
            out += node
            for (i in 0 until node.childCount) node.getChild(i)?.let { collect(it, out) }
        }

        private fun <T> runOnService(block: EZiKAccessibilityService.() -> T): T? {
            val service = instance ?: return null
            if (Looper.myLooper() == Looper.getMainLooper()) return block(service)
            var result: T? = null
            val latch = CountDownLatch(1)
            Handler(Looper.getMainLooper()).post {
                result = runCatching { block(service) }.getOrNull()
                latch.countDown()
            }
            latch.await(2, TimeUnit.SECONDS)
            return result
        }
    }
}
