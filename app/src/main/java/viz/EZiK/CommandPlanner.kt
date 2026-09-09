package viz.EZiK

import android.content.Context
import android.content.Intent
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
    fun plan(raw: String): AssistantAction {
        val normalized = raw.trim()
        val wakeWordPattern = Regex("(?i)^(?:(?:hey|يا)\\s+)?(?:ezik|e-zik|إيزيك|ايزيك)[،،!؟?:؛\\s]+")
        val wakeOnlyPattern = Regex("(?i)^(?:(?:hey|يا)\\s+)?(?:ezik|e-zik|إيزيك|ايزيك)[.!؟،،!؟?:؛\\s]*$")
        if (wakeOnlyPattern.matches(normalized)) {
            return AssistantAction.Speak("أيوه، معاك EZiK. قول لي أعمل إيه؟")
        }
        val command = normalized.replace(wakeWordPattern, "").trim()
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
        launchPrefixes.firstOrNull { lower.startsWith(it) }?.let { return AssistantAction.LaunchApp(command.drop(it.length).trim()) }
        searchPrefixes.firstOrNull { lower.startsWith(it) }?.let { return AssistantAction.WebSearch(command.drop(it.length).trim()) }
        if (lower.startsWith("http://") || lower.startsWith("https://")) return AssistantAction.OpenUrl(command)
        return AssistantAction.Speak("I understood: $command")
    }
}

object ActionExecutor {
    fun execute(context: Context, action: AssistantAction): String {
        when (action) {
            is AssistantAction.LaunchApp -> {
                val packageManager = context.packageManager
                val match = packageManager.getInstalledApplications(0).firstOrNull {
                    packageManager.getApplicationLabel(it).toString().contains(action.query, ignoreCase = true) ||
                        it.packageName.contains(action.query, ignoreCase = true)
                } ?: return "I couldn't find ${action.query}"
                packageManager.getLaunchIntentForPackage(match.packageName)?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(it)
                    return "Opening ${packageManager.getApplicationLabel(match)}"
                }
                return "${action.query} cannot be opened"
            }
            is AssistantAction.SearchInApp -> {
                val packageManager = context.packageManager
                val match = packageManager.getInstalledApplications(0).firstOrNull {
                    packageManager.getApplicationLabel(it).toString().contains(action.appQuery, ignoreCase = true) ||
                        it.packageName.contains(action.appQuery, ignoreCase = true)
                } ?: return "I couldn't find ${action.appQuery}"
                val searchIntent = Intent(Intent.ACTION_SEARCH).apply {
                    setPackage(match.packageName)
                    putExtra("query", action.searchQuery)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val launchIntent = packageManager.getLaunchIntentForPackage(match.packageName)
                context.startActivity(if (searchIntent.resolveActivity(packageManager) != null) searchIntent else launchIntent ?: return "${action.appQuery} cannot be opened")
                return "Searching ${action.appQuery} for ${action.searchQuery}"
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
