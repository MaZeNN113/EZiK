package viz.EZiK

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import java.util.Locale

sealed interface AssistantAction {
    data class LaunchApp(val query: String) : AssistantAction
    data class SearchInApp(val appQuery: String, val searchQuery: String) : AssistantAction
    data class WebSearch(val query: String) : AssistantAction
    data class OpenUrl(val url: String) : AssistantAction
    data class Speak(val text: String) : AssistantAction
}

object CommandPlanner {

    // أي كلمة ممكن Whisper يسمعها بالغلط بدل "EZiK" (زي Isaac) بتتحط هنا
    // كخط دفاع إضافي، لكن الحل الأساسي هو إعادة المحاولة بعد شيل أول كلمة
    // (tryParse فالغة → نجرب تاني من غير أول توكن) عشان يشتغل حتى مع أخطاء مسموعة جديدة.
    private val knownWakeMishears = setOf("isaac", "ezekiel", "izik", "ezeek", "azik")

    fun plan(raw: String): AssistantAction {
        val normalized = raw.trim().replace(Regex("\\s+"), " ")
        val wakeWordPattern = Regex("(?i)^(?:(?:hey|يا)\\s+)?(?:ezik|e-zik|إيزيك|ايزيك)[،,!؟?:؛\\s]+")
        val wakeOnlyPattern = Regex("(?i)^(?:(?:hey|يا)\\s+)?(?:ezik|e-zik|إيزيك|ايزيك)[.!؟،,!؟?:؛\\s]*$")
        if (wakeOnlyPattern.matches(normalized)) {
            return AssistantAction.Speak("أيوه، معاك EZiK. قول لي أعمل إيه؟")
        }

        var command = normalized.replace(wakeWordPattern, "").trim()
        // لو أول كلمة من أشهر الأخطاء المسموعة بدل EZiK، امسحها برضه
        val firstWord = command.substringBefore(' ').trimEnd(',', '،').lowercase(Locale.ROOT)
        if (firstWord in knownWakeMishears) {
            command = command.substringAfter(' ', "").trim()
        }

        tryParse(command)?.let { return it }

        // خط دفاع أخير: لو النص بيبدأ بكلمة غريبة متبوعة بفاصلة (نمط "اسم، أمر"،
        // زي "Isaac, open Goodreads")، ده على الأغلب EZiK اتسمعت غلط — نشيل
        // أول كلمة ونجرب تاني، من غير ما نحتاج نعرف كل الأخطاء المسموعة مقدماً.
        val withoutLeadingWord = command.replace(Regex("^\\S+[،,]\\s*"), "").trim()
        if (withoutLeadingWord.isNotEmpty() && withoutLeadingWord != command) {
            tryParse(withoutLeadingWord)?.let { return it }
        }

        return AssistantAction.Speak("I understood: $command")
    }

    private fun tryParse(command: String): AssistantAction? {
        val lower = command.lowercase(Locale.ROOT)
        val launchPrefixes = listOf("open ", "launch ", "start ", "افتح ", "شغل ", "شغّل ", "روح على ")
        val searchPrefixes = listOf("search for ", "search ", "ابحث عن ", "دور على ", "دور في ", "find ")
        val inAppMarkers = listOf(" and search for ", " وابحث عن ", " وابحث في ", " and find ")

        inAppMarkers.firstOrNull { lower.contains(it) }?.let { marker ->
            val parts = command.split(marker, limit = 2)
            if (parts.size == 2) {
                val app = parts[0].removePrefix("open ").removePrefix("افتح ").trim()
                return AssistantAction.SearchInApp(app, parts[1].trim())
            }
        }
        launchPrefixes.firstOrNull { lower.startsWith(it) }?.let {
            return AssistantAction.LaunchApp(command.drop(it.length).trim())
        }
        searchPrefixes.firstOrNull { lower.startsWith(it) }?.let {
            return AssistantAction.WebSearch(command.drop(it.length).trim())
        }
        if (lower.startsWith("http://") || lower.startsWith("https://")) return AssistantAction.OpenUrl(command)
        return null
    }
}

/**
 * مطابقة "ذكية" لاسم التطبيق: مطابقة كاملة، بعدها احتواء (متجاهلة الفراغات
 * الزيادة)، وأخيراً أقرب تشابه (Levenshtein) عشان الأخطاء الإملائية الصغيرة
 * (زي "Good Reads" بدل "Goodreads") تتقبل برضه بدل ما ترجع "couldn't find".
 */
private fun findBestAppMatch(context: Context, query: String): ApplicationInfo? {
    val pm = context.packageManager
    val q = query.trim().lowercase(Locale.ROOT)
    if (q.isEmpty()) return null
    val qCompact = q.replace(" ", "")
    val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

    fun label(app: ApplicationInfo) = pm.getApplicationLabel(app).toString().lowercase(Locale.ROOT)

    apps.firstOrNull { label(it) == q }?.let { return it }
    apps.firstOrNull { label(it).replace(" ", "").contains(qCompact) }?.let { return it }
    apps.firstOrNull { it.packageName.lowercase(Locale.ROOT).contains(qCompact) }?.let { return it }

    val maxAllowedDistance = (q.length / 3).coerceAtLeast(2)
    return apps
        .map { it to levenshtein(label(it), q) }
        .minByOrNull { it.second }
        ?.takeIf { it.second <= maxAllowedDistance }
        ?.first
}

private fun levenshtein(a: String, b: String): Int {
    val dp = Array(a.length + 1) { IntArray(b.length + 1) }
    for (i in 0..a.length) dp[i][0] = i
    for (j in 0..b.length) dp[0][j] = j
    for (i in 1..a.length) {
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
        }
    }
    return dp[a.length][b.length]
}

object ActionExecutor {
    fun execute(context: Context, action: AssistantAction): String {
        when (action) {
            is AssistantAction.LaunchApp -> {
                val pm = context.packageManager
                val match = findBestAppMatch(context, action.query) ?: return "I couldn't find ${action.query}"
                pm.getLaunchIntentForPackage(match.packageName)?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(it)
                    return "Opening ${pm.getApplicationLabel(match)}"
                }
                return "${action.query} cannot be opened"
            }
            is AssistantAction.SearchInApp -> {
                val pm = context.packageManager
                val match = findBestAppMatch(context, action.appQuery) ?: return "I couldn't find ${action.appQuery}"
                val searchIntent = Intent(Intent.ACTION_SEARCH).apply {
                    setPackage(match.packageName)
                    putExtra("query", action.searchQuery)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val launchIntent = pm.getLaunchIntentForPackage(match.packageName)
                context.startActivity(
                    if (searchIntent.resolveActivity(pm) != null) searchIntent
                    else launchIntent ?: return "${action.appQuery} cannot be opened"
                )
                return "Searching ${pm.getApplicationLabel(match)} for ${action.searchQuery}"
            }
            is AssistantAction.WebSearch -> {
                val uri = Uri.parse("https://www.google.com/search?q=${Uri.encode(action.query)}")
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return "Searching for ${action.query}"
            }
            is AssistantAction.OpenUrl -> {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(action.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return "Opening link"
            }
            is AssistantAction.Speak -> return action.text
        }
    }
}
