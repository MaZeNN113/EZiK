package com.vizmazen.EZiK

import android.app.Activity
import android.app.role.RoleManager
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var shizukuStatusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        shizukuStatusText = findViewById(R.id.shizukuStatusText)
        findViewById<Button>(R.id.makeDefaultButton).setOnClickListener { requestAssistantRole() }
        findViewById<Button>(R.id.requestShizukuButton).setOnClickListener {
            ShizukuManager.requestPermission(this)
            updateStatus()
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
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
                startActivityForResult(intent, REQUEST_CODE_ASSISTANT_ROLE)
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_ASSISTANT_ROLE) {
            updateStatus()
        }
    }

    companion object {
        private const val REQUEST_CODE_ASSISTANT_ROLE = 100
        private const val REQUEST_CODE_RECORD_AUDIO = 101
    }
}
