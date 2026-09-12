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
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var statusText: TextView
    private lateinit var shizukuStatusText: TextView
    private lateinit var voiceKeyStatusText: TextView
    private lateinit var groqKeyInput: EditText
    private val commandExecutor = Executors.newSingleThreadExecutor()
    private val secureStore by lazy { SecureStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        shizukuStatusText = findViewById(R.id.shizukuStatusText)
        voiceKeyStatusText = findViewById(R.id.voiceKeyStatusText)
        groqKeyInput = findViewById(R.id.groqKeyInput)

        CommandUiBinder.bind(
            findViewById(R.id.commandPanel),
            this,
            commandExecutor,
            onFinished = {}
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
        findViewById<Button>(R.id.saveGroqButton).setOnClickListener {
            secureStore.putApiKey(groqKeyInput.text.toString())
            groqKeyInput.text?.clear()
            Toast.makeText(this, if (secureStore.hasApiKey()) "Groq key saved" else "Groq key removed", Toast.LENGTH_SHORT).show()
            updateStatus()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CODE_RECORD_AUDIO)
        }
        updateStatus()
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
        val assistantReady = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true
        } else false

        val accessibilityOn = runCatching {
            val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            enabled?.split(':')?.any { it.contains(packageName, ignoreCase = true) } == true
        }.getOrDefault(false)

        statusText.text = listOf(
            if (assistantReady) getString(R.string.status_is_assistant) else getString(R.string.status_not_assistant),
            if (accessibilityOn) getString(R.string.status_accessibility_on) else getString(R.string.status_accessibility_off)
        ).joinToString("  •  ")

        shizukuStatusText.text = getString(R.string.shizuku_status, ShizukuManager.status(this))
        voiceKeyStatusText.text = if (secureStore.hasApiKey()) {
            getString(R.string.status_groq_ready)
        } else {
            getString(R.string.status_groq_missing)
        }
    }

    private fun requestAssistantRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true) {
                Toast.makeText(this, R.string.status_is_assistant, Toast.LENGTH_SHORT).show()
                return
            }
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_ASSISTANT) == true) {
                startActivityForResult(
                    roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT),
                    REQUEST_CODE_ASSISTANT_ROLE
                )
                return
            }
        }
        openSettings(Settings.ACTION_VOICE_INPUT_SETTINGS)
    }

    private fun openSettings(action: String) {
        runCatching { startActivity(Intent(action)) }
            .onFailure { startActivity(Intent(Settings.ACTION_SETTINGS)) }
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
