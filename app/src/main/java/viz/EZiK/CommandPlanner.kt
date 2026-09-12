package viz.EZiK

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import java.util.Locale

sealed interface AssistantAction {
    data class LaunchApp(val query: String) : AssistantAction
    data class SearchInApp(val appQuery: String, val searchQuery: String) : AssistantAction
    data class WebSearch(val query: String) : AssistantAction
    data class OpenUrl(val url: String) : AssistantAction
    data class OpenSettings(val action: String) : AssistantAction
    data class Speak(val text: String) : AssistantAction
    data class Answer(val text: String) : AssistantAction
}

object CommandPlanner {
    private val knownWakeMishears = setOf("isaac", "ezekiel", "izik", "ezeek", "azik", "ezic")

    fun plan(raw: String): AssistantAction {
        val normalized = raw.trim().replace(Regex("\\s+"), " ")
        if (normalized.isBlank()) return AssistantAction.Speak("What would you like me to do?")

        val wakeOnly = Regex("(?i)^(?:(?:hey|يا)\\s+)?(?:ezik|e-zik|إيزيك|ايزيك)[.!؟،,!؟?:؛\\s]*$")
        if (wakeOnly.matches(normalized)) {
            return AssistantAction.Speak("أيوه، معاك EZiK. قول لي أعمل إيه؟")
        }

        var command = stripWakeWord(normalized)
        if (command.substringBefore(' ').trimEnd(',', '،').lowercase(Locale.ROOT) in knownWakeMishears) {
            command = command.substringAfter(' ', "").trim()
        }

        parse(command)?.let { return it }

        val withoutLeadingWord = command.replace(Regex("^\\S+[،,]\\s*"), "").trim()
        if (withoutLeadingWord != command) parse(withoutLeadingWord)?.let { return it }

        return AssistantAction.Speak("I understood: $command")
    }

    private fun stripWakeWord(text: String): String {
        val pattern = Regex("(?i)^(?:(?:hey|يا)\\s+)?(?:ezik|e-zik|إيزيك|ايزيك)(?:[,.!؟?:؛\\s]+|$)")
        return text.replace(pattern, "").trim()
    }

    private fun parse(command: String): AssistantAction? {
        val lower = command.lowercase(Locale.ROOT)

        val settings = listOf(
            "open wifi settings" to Intent(Settings.ACTION_WIFI_SETTINGS),
            "wifi settings" to Intent(Settings.ACTION_WIFI_SETTINGS),
            "open bluetooth settings" to Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
            "bluetooth settings" to Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
            "open app settings" to Intent(Settings.ACTION_APPLICATION_SETTINGS)
        )
        settings.firstOrNull { lower == it.first }?.let { pair ->
            pair.second.action?.let { action -> return AssistantAction.OpenSettings(action) }
        }

        val inAppMarkers = listOf(" and search for ", " and find ", " وابحث عن ", " وابحث في ")
        inAppMarkers.firstOrNull { lower.contains(it) }?.let { marker ->
            val index = lower.indexOf(marker)
            val app = command.substring(0, index)
                .removePrefixIgnoreCase("open ")
                .removePrefixIgnoreCase("افتح ")
                .trim()
            val query = command.substring(index + marker.length).trim()
            if (app.isNotBlank() && query.isNotBlank()) return AssistantAction.SearchInApp(app, query)
        }

        val launchPrefixes = listOf("open ", "launch ", "start ", "go to ", "افتح ", "شغل ", "شغّل ", "روح على ")
        launchPrefixes.firstOrNull { lower.startsWith(it) }?.let {
            val app = command.substring(it.length).trim()
            if (app.isNotBlank()) return AssistantAction.LaunchApp(app)
        }

        val searchPrefixes = listOf("search for ", "search ", "look up ", "ابحث عن ", "دور على ", "دور في ", "find ")
        searchPrefixes.firstOrNull { lower.startsWith(it) }?.let {
            val query = command.substring(it.length).trim()
            if (query.isNotBlank()) return AssistantAction.WebSearch(query)
        }

        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return AssistantAction.OpenUrl(command)
        }

        return null
    }

    private fun String.removePrefixIgnoreCase(prefix: String): String =
        if (startsWith(prefix, ignoreCase = true)) substring(prefix.length) else this
}

private fun findBestAppMatch(context: Context, query: String): ApplicationInfo? {
    val pm = context.packageManager
    val q = query.trim().lowercase(Locale.ROOT)
    if (q.isEmpty()) return null
    val compact = q.replace(Regex("[\\s._-]+"), "")

    val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        .filter { it.enabled }

    fun label(app: ApplicationInfo) = pm.getApplicationLabel(app).toString().trim().lowercase(Locale.ROOT)
    fun compactLabel(app: ApplicationInfo) = label(app).replace(Regex("[\\s._-]+"), "")

    apps.firstOrNull { label(it) == q }?.let { return it }
    apps.firstOrNull { compactLabel(it) == compact }?.let { return it }
    apps.firstOrNull { compactLabel(it).contains(compact) }?.let { return it }
    apps.firstOrNull { it.packageName.lowercase(Locale.ROOT).contains(compact) }?.let { return it }

    val maxDistance = (q.length / 3).coerceIn(2, 4)
    return apps.asSequence()
        .map { it to levenshtein(compactLabel(it), compact) }
        .minByOrNull { it.second }
        ?.takeIf { it.second <= maxDistance }
        ?.first
}

private fun levenshtein(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    var previous = IntArray(b.length + 1) { it }
    for (i in a.indices) {
        val current = IntArray(b.length + 1)
        current[0] = i + 1
        for (j in b.indices) {
            val cost = if (a[i] == b[j]) 0 else 1
            current[j + 1] = minOf(
                current[j] + 1,
                previous[j + 1] + 1,
                previous[j] + cost
            )
        }
        previous = current
    }
    return previous[b.length]
}

object AssistantBrain {
    fun resolve(context: Context, raw: String): Result<AssistantAction> {
        val direct = CommandPlanner.plan(raw)
        if (direct !is AssistantAction.Speak || direct.text.startsWith("أيوه، معاك")) {
            return Result.success(direct)
        }
        if (!SecureStore(context).hasApiKey()) return Result.success(direct)
        return GroqChatClient(context).decide(raw)
    }
}

object ActionExecutor {
    fun execute(context: Context, action: AssistantAction): String {
        val pm = context.packageManager
        return when (action) {
            is AssistantAction.LaunchApp -> {
                val match = findBestAppMatch(context, action.query)
                    ?: return "I couldn't find ${action.query}"
                val intent = pm.getLaunchIntentForPackage(match.packageName)
                    ?: return "${action.query} cannot be opened"
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "Opening ${pm.getApplicationLabel(match)}"
            }
            is AssistantAction.SearchInApp -> {
                val match = findBestAppMatch(context, action.appQuery)
                    ?: return "I couldn't find ${action.appQuery}"
                val searchIntent = Intent(Intent.ACTION_SEARCH).apply {
                    setPackage(match.packageName)
                    putExtra("query", action.searchQuery)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val intent = if (searchIntent.resolveActivity(pm) != null) {
                    searchIntent
                } else {
                    pm.getLaunchIntentForPackage(match.packageName)
                        ?: return "${action.appQuery} cannot be opened"
                }
                context.startActivity(intent)
                "Searching ${pm.getApplicationLabel(match)} for ${action.searchQuery}"
            }
            is AssistantAction.WebSearch -> {
                val uri = Uri.parse("https://www.google.com/search?q=${Uri.encode(action.query)}")
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "Searching for ${action.query}"
            }
            is AssistantAction.OpenUrl -> {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(action.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "Opening link"
            }
            is AssistantAction.OpenSettings -> {
                context.startActivity(Intent(action.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "Opening settings"
            }
            is AssistantAction.Speak -> action.text
            is AssistantAction.Answer -> action.text
        }
    }
}
