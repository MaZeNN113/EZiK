package viz.EZiK

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import java.util.concurrent.Executors

/** Reliable bottom assistant surface used by the system VoiceInteractionService. */
class AssistantSession(context: Context) : VoiceInteractionSession(context) {

    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate() {
        super.onCreate()
        getWindow()?.let { dialog ->
            dialog.setCanceledOnTouchOutside(true)
            dialog.setCancelable(true)
            dialog.window?.let(::configureWindow)
        }
    }

    override fun onCreateContentView(): View {
        val view = LayoutInflater.from(context).inflate(R.layout.session_assistant, null)
        CommandUiBinder.bind(view, context, executor, onFinished = { hide() }, autoStartVoice = true)
        installImeHandling(view)
        return view
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        getWindow()?.window?.let { window ->
            configureWindow(window)
        }
    }

    override fun onHide() {
        getWindow()?.window?.decorView?.translationY = 0f
        super.onHide()
    }

    private fun configureWindow(window: android.view.Window) {
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.setGravity(Gravity.BOTTOM)
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        )
        window.attributes = window.attributes.apply { dimAmount = 0.18f }
    }

    private fun installImeHandling(root: View) {
        val targetWindow = getWindow()?.window ?: return
        // adjustResize handles the IME. Changing window.y from an insets callback causes
        // a relayout loop on MIUI and makes the keyboard visibly jump.
        targetWindow.decorView.setOnApplyWindowInsetsListener { _, insets -> insets }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
