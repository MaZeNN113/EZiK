package viz.EZiK

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var statusText: TextView
    private lateinit var shizukuStatusText: TextView
    private val commandExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        shizukuStatusText = findViewById(R.id.shizukuStatusText)
        CommandUiBinder.bind(
            findViewById(R.id.commandPanel),
            this,
            commandExecutor,
            onFinished = { }
        )

        findViewById<Button>(R.id.makeDefaultButton).setOnClickListener { requestAssistantRole() }
        findViewById<Button>(R.id.openAssistantSettingsButton).setOnClickListener {
            openSettings(Settings.ACTION_VOICE_INPUT_SETTINGS)
        }
        findViewById<Button>(R.id.requestShizukuButton).setOnClickListener {
            ShizukuManager.requestPermission(this)
            updateStatus()
        }
        findViewById<Button>(R.id.openAccessibilitySettingsButton).setOnClickListener {
            openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        }
        findViewById<Button>(R.id.openAppSettingsButton).setOnClickListener {
            openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CODE_RECORD_AUDIO)
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    override fun onDestroy() {
        commandExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun updateStatus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            statusText.text = if (roleManager?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true) {
                getString(R.string.status_is_assistant)
            } else {
                getString(R.string.status_not_assistant)
            }
        } else {
            statusText.text = getString(R.string.status_unsupported_version)
        }
        shizukuStatusText.text = getString(R.string.shizuku_status, ShizukuManager.status(this))
    }

    private fun requestAssistantRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
                startActivityForResult(
                    roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT),
                    REQUEST_CODE_ASSISTANT_ROLE
                )
            }
        }
    }

    private fun openSettings(action: String, data: String? = null) {
        runCatching {
            startActivity(Intent(action).apply {
                if (data != null) setData(android.net.Uri.parse(data))
            })
        }.onFailure {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_ASSISTANT_ROLE) updateStatus()
    }

    companion object {
        private const val REQUEST_CODE_ASSISTANT_ROLE = 100
        private const val REQUEST_CODE_RECORD_AUDIO = 101
    }
}
