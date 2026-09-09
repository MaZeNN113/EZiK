package viz.EZiK

import android.content.Context
import android.os.Build
import android.widget.Toast
import rikka.shizuku.Shizuku

/**
 * Optional privileged execution bridge. Accessibility remains the fallback.
 * Actual commands must be allow-listed by the command planner before execution.
 */
object ShizukuManager {
    private const val REQUEST_CODE = 4201

    // Shizuku.pingBinder() ممكن يرمي استثناء لو Shizuku مش متثبت خالص على الجهاز
    // بدل ما يرجع false بس، فبنحوطها هنا.
    fun isAvailable(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun hasPermission(): Boolean = isAvailable() && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun requestPermission(context: Context) {
        if (!isAvailable()) {
            Toast.makeText(context, R.string.shizuku_not_running, Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT >= 23 && !hasPermission()) {
            Shizuku.requestPermission(REQUEST_CODE)
        }
    }

    fun status(context: Context): String = when {
        !isAvailable() -> context.getString(R.string.shizuku_unavailable)
        hasPermission() -> context.getString(R.string.shizuku_ready)
        else -> context.getString(R.string.shizuku_needs_permission)
    }
}
