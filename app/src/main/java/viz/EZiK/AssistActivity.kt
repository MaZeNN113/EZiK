package viz.EZiK

import android.app.Activity
import android.os.Bundle
import java.util.concurrent.Executors

/**
 * بعض إصدارات MIUI بتفتح ACTION_ASSIST activity عادي بدل ما تستخدم
 * VoiceInteractionSession. عشان كده الشاشة دي بترسم نفس نافذة الأوامر
 * (session_assistant.xml) بدل ما تحول المستخدم لشاشة الإعدادات الكاملة.
 */
class AssistActivity : Activity() {

    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.session_assistant)
        val root = findViewById<android.view.View>(android.R.id.content)
        CommandUiBinder.bind(root, this, executor, onFinished = {
            // finish() لوحده مش دايماً كافي هنا: نافذة الـ assist أحياناً بتفضل
            // فوق التطبيق اللي فتحناه لحد ما تعمل back يدوي. moveTaskToBack
            // بيجبر النافذة دي تنزل تحت فوراً بدل ما تستنى.
            finish()
            moveTaskToBack(true)
        })
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
