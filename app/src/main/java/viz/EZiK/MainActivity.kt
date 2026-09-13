package viz.EZiK

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var statusText: TextView
    private lateinit var micStatusText: TextView
    private lateinit var accessibilityStatusText: TextView
    private lateinit var shizukuStatusText: TextView
    private lateinit var overallBadge: TextView
    private val commandExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        statusText = findViewById(R.id.statusText)
        micStatusText = findViewById(R.id.micStatusText)
        accessibilityStatusText = findViewById(R.id.accessibilityStatusText)
        shizukuStatusText = findViewById(R.id.shizukuStatusText)
        overallBadge = findViewById(R.id.overallBadge)

        CommandUiBinder.bind(findViewById(R.id.commandPanel), this, commandExecutor, onFinished = { updateStatus() })

        findViewById<Button>(R.id.makeDefaultButton).setOnClickListener { requestAssistantRole() }
        findViewById<Button>(R.id.openAssistantSettingsButton).setOnClickListener { openSettings(Settings.ACTION_VOICE_INPUT_SETTINGS) }
        findViewById<Button>(R.id.requestShizukuButton).setOnClickListener { ShizukuManager.requestPermission(this); updateStatus() }
        findViewById<Button>(R.id.openAccessibilitySettingsButton).setOnClickListener { openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS) }
        findViewById<Button>(R.id.openAppSettingsButton).setOnClickListener { openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName") }

        bindSwitch(R.id.autoVoiceSwitch, EZiKPrefs.autoVoice(this)) { EZiKPrefs.setAutoVoice(this, it) }
        bindSwitch(R.id.appActionsSwitch, EZiKPrefs.appActions(this)) { EZiKPrefs.setAppActions(this, it) }
        bindSwitch(R.id.confirmActionsSwitch, EZiKPrefs.confirmActions(this)) { EZiKPrefs.setConfirmActions(this, it) }
        bindSwitch(R.id.cloudReasoningSwitch, EZiKPrefs.cloudReasoning(this)) { EZiKPrefs.setCloudReasoning(this, it) }

        val keyInput = findViewById<EditText>(R.id.groqKeyInput)
        findViewById<Button>(R.id.saveGroqKeyButton).setOnClickListener {
            SecurePrefs.saveGroqKey(this, keyInput.text.toString())
            keyInput.text.clear()
            updateStatus()
            android.widget.Toast.makeText(this, "Cloud key saved locally", android.widget.Toast.LENGTH_SHORT).show()
        }

        val scroll = findViewById<ScrollView>(R.id.mainScroll)
        findViewById<Button>(R.id.navHome).setOnClickListener { scroll.smoothScrollTo(0, findViewById<View>(R.id.homeSection).top) }
        findViewById<Button>(R.id.navCapabilities).setOnClickListener { scroll.smoothScrollTo(0, findViewById<View>(R.id.capabilitiesSection).top) }
        findViewById<Button>(R.id.navSettings).setOnClickListener { scroll.smoothScrollTo(0, findViewById<View>(R.id.settingsSection).top) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CODE_RECORD_AUDIO)
        }
    }

    private fun bindSwitch(id: Int, initial: Boolean, save: (Boolean) -> Unit) {
        findViewById<SwitchCompat>(id).apply {
            isChecked = initial
            setOnCheckedChangeListener { _, checked -> save(checked) }
        }
    }

    override fun onResume() { super.onResume(); updateStatus() }
    override fun onDestroy() { commandExecutor.shutdownNow(); super.onDestroy() }

    private fun updateStatus() {
        val assistant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true else false
        statusText.text = if (assistant) "● Default assistant is active" else "○ Make EZiK your default assistant"
        statusText.setTextColor(if (assistant) 0xFF55D187.toInt() else 0xFFFFB454.toInt())
        val micGranted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        micStatusText.text = if (micGranted) "● Microphone · ready" else "○ Microphone · permission required"
        micStatusText.setTextColor(if (micGranted) 0xFF55D187.toInt() else 0xFFFF6B6B.toInt())
        val accessibility = isAccessibilityEnabled()
        accessibilityStatusText.text = if (accessibility) "● Accessibility bridge · connected" else "○ Accessibility bridge · enable for app actions"
        accessibilityStatusText.setTextColor(if (accessibility) 0xFF55D187.toInt() else 0xFFFFB454.toInt())
        shizukuStatusText.text = "● Shizuku · ${ShizukuManager.status(this)}"
        overallBadge.text = if (assistant && micGranted) "READY" else "SETUP"
        overallBadge.setTextColor(if (assistant && micGranted) 0xFF55D187.toInt() else 0xFFFFB454.toInt())
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(':').any { it.equals("$packageName/.EZiKAccessibilityService", true) || it.endsWith("/.EZiKAccessibilityService", true) }
    }

    private fun requestAssistantRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_ASSISTANT) == true) {
                if (!roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT) ) startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT), REQUEST_CODE_ASSISTANT_ROLE)
                else updateStatus()
            } else openSettings(Settings.ACTION_VOICE_INPUT_SETTINGS)
        } else openSettings(Settings.ACTION_VOICE_INPUT_SETTINGS)
    }

    private fun openSettings(action: String, data: String? = null) {
        runCatching { startActivity(Intent(action).apply { if (data != null) setData(android.net.Uri.parse(data)) }) }
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
