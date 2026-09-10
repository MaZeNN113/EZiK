package viz.EZiK

import android.content.Context
import android.service.voice.VoiceInteractionSession
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.Gravity
import android.view.WindowManager
import java.util.concurrent.Executors

/**
 * دي النافذة الفعلية اللي بتظهر لما تسحب من زاوية الشاشة.
 * كل منطق الكتابة/الصوت/التنفيذ بقى في CommandUiBinder (مشترك مع AssistActivity).
 */
class AssistantSession(context: Context) : VoiceInteractionSession(context) {

    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreateContentView(): View {
        val view = LayoutInflater.from(context).inflate(R.layout.session_assistant, null)
        CommandUiBinder.bind(view, context, executor, onFinished = { hide() })
        return view
    }

    override fun onCreate() {
        super.onCreate()
        getWindow()?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.setGravity(Gravity.BOTTOM)
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.attributes = window.attributes.apply { dimAmount = 0.18f }
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
