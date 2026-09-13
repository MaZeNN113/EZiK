package viz.EZiK

import android.content.Context

object EZiKPrefs {
    private const val NAME = "ezik_settings"
    private const val AUTO_VOICE = "auto_voice"
    private const val CONFIRM = "confirm_actions"
    private const val CLOUD = "cloud_reasoning"
    private const val APP_ACTIONS = "app_actions"

    private fun prefs(context: Context) = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    fun autoVoice(context: Context) = prefs(context).getBoolean(AUTO_VOICE, true)
    fun confirmActions(context: Context) = prefs(context).getBoolean(CONFIRM, true)
    fun cloudReasoning(context: Context) = prefs(context).getBoolean(CLOUD, true)
    fun appActions(context: Context) = prefs(context).getBoolean(APP_ACTIONS, true)
    fun setAutoVoice(context: Context, v: Boolean) = prefs(context).edit().putBoolean(AUTO_VOICE, v).apply()
    fun setConfirmActions(context: Context, v: Boolean) = prefs(context).edit().putBoolean(CONFIRM, v).apply()
    fun setCloudReasoning(context: Context, v: Boolean) = prefs(context).edit().putBoolean(CLOUD, v).apply()
    fun setAppActions(context: Context, v: Boolean) = prefs(context).edit().putBoolean(APP_ACTIONS, v).apply()
}
