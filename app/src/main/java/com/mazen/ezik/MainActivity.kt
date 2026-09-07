package com.mazen.ezik

import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        val title = TextView(this).apply {
            text = "EZiK\nYour Own Personal Agent"
            textSize = 24f
        }

        val assistantButton = Button(this).apply {
            text = "Set EZiK as Assistant"
            setOnClickListener {
                val roleManager = getSystemService(RoleManager::class.java)
                when {
                    roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT) &&
                        !roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT) -> {
                        startActivityForResult(
                            roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT),
                            1001
                        )
                    }
                    roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT) -> {
                        Toast.makeText(this@MainActivity, "EZiK is already the assistant", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        try {
                            startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
                        } catch (_: Exception) {
                            startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                        }
                    }
                }
            }
        }

        val accessibilityButton = Button(this).apply {
            text = "Open Accessibility Settings"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        layout.addView(title)
        layout.addView(assistantButton)
        layout.addView(accessibilityButton)
        setContentView(layout)
    }
}
