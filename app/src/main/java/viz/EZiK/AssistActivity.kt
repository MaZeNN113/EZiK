package viz.EZiK

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import java.util.concurrent.Executors

/** Compact bottom assistant surface used by MIUI ACTION_ASSIST fallback. */
class AssistActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.session_assistant)
        configureWindow()

        val root = findViewById<View>(android.R.id.content)
        CommandUiBinder.bind(root, this, executor, onFinished = { finishAndRemoveTask() }, autoStartVoice = true)
        installImeHandling(root)
    }

    private fun configureWindow() {
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.setDimAmount(0.18f)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        )
        window.setGravity(Gravity.BOTTOM)
        window.decorView.setBackgroundColor(Color.TRANSPARENT)
        window.decorView.post {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            window.setGravity(Gravity.BOTTOM)
            window.decorView.requestApplyInsets()
        }
    }

    private fun installImeHandling(root: View) {
        // Let Android's adjustResize perform one stable layout pass. Mutating the
        // window's bottom offset from inside WindowInsets creates a relayout loop on MIUI.
        window.decorView.setOnApplyWindowInsetsListener { _, insets -> insets }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
