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
    data class TapInApp(val appQuery: String, val target: String) : AssistantAction
    data class TypeInApp(val appQuery: String, val text: String) : AssistantAction
    data class ScrollInApp(val appQuery: String, val forward: Boolean = true) : AssistantAction
    data class BackInApp(val appQuery: String) : AssistantAction
    data class WebSearch(val query: String) : AssistantAction
    data class OpenUrl(val url: String) : AssistantAction
    data class Speak(val text: String) : AssistantAction
}

object CommandPlanner {
    private val knownWakeMishears = setOf("isaac", "ezekiel", "izik", "ezeek", "azik")

    fun plan(raw: String): AssistantAction {
        val normalized = raw.trim().replace(Regex("\\s+"), " ")
        val wakeWordPattern = Regex("(?i)^(?:(?:hey|يا)\\s+)?(?:ezik|e-zik|إيزيك|ايزيك)[،,!؟?:؛\\s]+")
        val wakeOnlyPattern = Regex("(?i)^(?:(?:hey|يا)\\s+)?(?:ezik|e-zik|إيزيك|ايزيك)[.!؟،,!؟?:؛\\s]*$")
        if (wakeOnlyPattern.matches(normalized)) return AssistantAction.Speak("أيوه، معاك EZiK. قول لي أعمل إيه؟")

        var command = normalized.replace(wakeWordPattern, "").trim()
        val firstWord = command.substringBefore(' ').trimEnd(',', '،').lowercase(Locale.ROOT)
        if (firstWord in knownWakeMishears) command = command.substringAfter(' ', "").trim()

        tryParse(command)?.let { return it }
        val withoutLeadingWord = command.replace(Regex("^\\S+[،,]\\s*"), "").trim()
        if (withoutLeadingWord.isNotEmpty() && withoutLeadingWord != command) tryParse(withoutLeadingWord)?.let { return it }
        return AssistantAction.Speak("I understood: $command")
    }

    private fun tryParse(command: String): AssistantAction? {
        val lower = command.lowercase(Locale.ROOT)
        val launchPrefixes = listOf("open ", "launch ", "start ", "افتح ", "شغل ", "شغّل ", "روح على ")

        // App + search. Examples: "open Goodreads and search for Atomic Habits".
        val searchRegexes = listOf(
            Regex("(?i)^(?:open|افتح) (.+?) (?:and )?(?:search|find|ابحث) (?:for|عن|في) (.+)$"),
            Regex("(?i)^(?:search|find|ابحث) (.+?) (?:for|عن|في) (.+)$")
        )
        for (regex in searchRegexes) regex.matchEntire(command)?.let { m ->
            val app = m.groupValues[1].trim()
            val query = m.groupValues[2].trim()
            if (app.isNotBlank() && query.isNotBlank()) return AssistantAction.SearchInApp(app, query)
        }

        val tapRegexes = listOf(
            Regex("(?i)^(?:open|افتح) (.+?) and (?:tap|click|press) (.+)$"),
            Regex("(?i)^(?:in|في) (.+?) (?:tap|click|press|اضغط) (.+)$"),
            Regex("(?i)^(?:tap|click|press|اضغط) (.+?) (?:in|في) (.+)$")
        )
        for (r in tapRegexes) r.matchEntire(command)?.let { m ->
            val groups = m.groupValues
            val app = if (r == tapRegexes[2]) groups[2] else groups[1]
            val target = if (r == tapRegexes[2]) groups[1] else groups[2]
            return AssistantAction.TapInApp(app.trim(), target.trim())
        }

        val typeMatch = Regex("(?i)^(?:in|في) (.+?) (?:type|write|اكتب) (.+)$").find(command)
        if (typeMatch != null) return AssistantAction.TypeInApp(typeMatch.groupValues[1].trim(), typeMatch.groupValues[2].trim())

        val scrollMatch = Regex("(?i)^(?:in|في) (.+?) (scroll|اسكرول)(?: (up|down|upward|downward))?$").find(command)
        if (scrollMatch != null) {
            val direction = scrollMatch.groupValues.getOrNull(3)?.lowercase(Locale.ROOT)
            return AssistantAction.ScrollInApp(scrollMatch.groupValues[1].trim(), direction != "up" && direction != "upward")
        }

        val backMatch = Regex("(?i)^(?:in|في) (.+?) (?:go back|back|ارجع)$").find(command)
        if (backMatch != null) return AssistantAction.BackInApp(backMatch.groupValues[1].trim())

        launchPrefixes.firstOrNull { lower.startsWith(it) }?.let { return AssistantAction.LaunchApp(command.drop(it.length).trim()) }
        val searchPrefixes = listOf("search for ", "search ", "ابحث عن ", "دور على ", "find ")
        searchPrefixes.firstOrNull { lower.startsWith(it) }?.let { return AssistantAction.WebSearch(command.drop(it.length).trim()) }
        if (lower.startsWith("http://") || lower.startsWith("https://")) return AssistantAction.OpenUrl(command)
        return null
    }
}

private fun findBestAppMatch(context: Context, query: String): ApplicationInfo? {
    val pm = context.packageManager
    val q = query.trim().lowercase(Locale.ROOT)
    if (q.isEmpty()) return null
    val compact = q.replace(" ", "")
    val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
    fun label(app: ApplicationInfo) = pm.getApplicationLabel(app).toString().lowercase(Locale.ROOT)
    apps.firstOrNull { label(it) == q }?.let { return it }
    apps.firstOrNull { label(it).replace(" ", "").contains(compact) }?.let { return it }
    apps.firstOrNull { it.packageName.lowercase(Locale.ROOT).contains(compact) }?.let { return it }
    val maxAllowedDistance = (q.length / 3).coerceAtLeast(2)
    return apps.map { it to levenshtein(label(it), q) }.minByOrNull { it.second }?.takeIf { it.second <= maxAllowedDistance }?.first
}

private fun levenshtein(a: String, b: String): Int {
    val dp = Array(a.length + 1) { IntArray(b.length + 1) }
    for (i in 0..a.length) dp[i][0] = i
    for (j in 0..b.length) dp[0][j] = j
    for (i in 1..a.length) for (j in 1..b.length) {
        val cost = if (a[i - 1] == b[j - 1]) 0 else 1
        dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
    }
    return dp[a.length][b.length]
}

object ActionExecutor {
    fun execute(context: Context, action: AssistantAction): String {
        if (action !is AssistantAction.LaunchApp && action !is AssistantAction.WebSearch && action !is AssistantAction.OpenUrl && action !is AssistantAction.Speak && !EZiKPrefs.appActions(context)) {
            return "In-app actions are disabled in Settings."
        }
        return when (action) {
        is AssistantAction.LaunchApp -> launch(context, action.query)
        is AssistantAction.SearchInApp -> appCommand(context, action.appQuery, "search") {
            if (EZiKAccessibilityService.isReady()) {
                Thread.sleep(1200)
                EZiKAccessibilityService.search(action.searchQuery) == true
            } else false
        } .let { if (it) "Searching ${action.appQuery} for ${action.searchQuery}" else "Opened ${action.appQuery}, but its search UI could not be controlled. Enable EZiK Accessibility." }
        is AssistantAction.TapInApp -> appCommand(context, action.appQuery, "tap") {
            Thread.sleep(1200); EZiKAccessibilityService.tap(action.target) == true
        }.let { if (it) "Tapped ${action.target}" else "Opened ${action.appQuery}, but I couldn't find '${action.target}'." }
        is AssistantAction.TypeInApp -> appCommand(context, action.appQuery, "type") {
            Thread.sleep(1200); EZiKAccessibilityService.type(action.text) == true
        }.let { if (it) "Typed into ${action.appQuery}" else "Opened ${action.appQuery}, but I couldn't find an editable field." }
        is AssistantAction.ScrollInApp -> appCommand(context, action.appQuery, "scroll") {
            Thread.sleep(1200); if (action.forward) EZiKAccessibilityService.scrollForward() == true else EZiKAccessibilityService.scrollBackward() == true
        }.let { if (it) "Scrolled" else "Opened ${action.appQuery}, but no scrollable area was found." }
        is AssistantAction.BackInApp -> { launch(context, action.appQuery); Thread.sleep(500); if (EZiKAccessibilityService.performBack()) "Went back" else "Back action unavailable" }
        is AssistantAction.WebSearch -> {
            val uri = Uri.parse("https://www.google.com/search?q=${Uri.encode(action.query)}")
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); "Searching for ${action.query}"
        }
        is AssistantAction.OpenUrl -> { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(action.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); "Opening link" }
        is AssistantAction.Speak -> action.text
        }
    }

    private fun launch(context: Context, query: String): String {
        val pm = context.packageManager
        val match = findBestAppMatch(context, query) ?: return "I couldn't find $query"
        val intent = pm.getLaunchIntentForPackage(match.packageName) ?: return "$query cannot be opened"
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return "Opening ${pm.getApplicationLabel(match)}"
    }

    private fun appCommand(context: Context, appQuery: String, ignored: String, action: () -> Boolean): Boolean {
        if (!EZiKAccessibilityService.isReady()) {
            launch(context, appQuery)
            return false
        }
        launch(context, appQuery)
        return action()
    }
}
