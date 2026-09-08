package com.vizmazen.assistant

import android.app.Activity
import android.app.role.RoleManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        val makeDefaultButton = findViewById<Button>(R.id.makeDefaultButton)

        makeDefaultButton.setOnClickListener {
            requestAssistantRole()
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            val isHeld = roleManager?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true
            statusText.text = if (isHeld) {
                getString(R.string.status_is_assistant)
            } else {
                getString(R.string.status_not_assistant)
            }
        } else {
            statusText.text = getString(R.string.status_unsupported_version)
        }
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
    }
}
