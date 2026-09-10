package viz.EZiK

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/**
 * ليه الملف ده موجود؟
 * أندرويد (وخصوصاً MIUI) بيعتبر تطبيقك "مساعد صوتي كامل" مؤهل يظهر في
 * Settings > Digital assistant app بس لو الـ voice-interaction-service بتاعك
 * بيشاور على RecognitionService حقيقي — حتى لو مش هتستخدمه فعلياً.
 *
 * إحنا مش بنعتمد على الكلاس ده في التعرف على الصوت — التعرف الحقيقي بيحصل
 * عن طريق Groq Whisper API جوه AssistantSession. الكلاس ده بس "ورقة تعريف"
 * عشان نعدي شرط النظام ونظهر في قايمة المساعدين.
 */
class EZiKRecognitionService : RecognitionService() {

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        // مش بنستخدمه — بنرجّع خطأ فوري عشان أي حد يستدعيه بالغلط ميعلقش مستني.
        listener?.error(SpeechRecognizer.ERROR_CLIENT)
    }

    override fun onCancel(listener: Callback?) {
        // مفيش حاجة نلغيها
    }

    override fun onStopListening(listener: Callback?) {
        // مفيش حاجة نوقفها
    }
}
