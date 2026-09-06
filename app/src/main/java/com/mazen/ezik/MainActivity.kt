package com.mazen.ezik
import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.*
class MainActivity: Activity(){ override fun onCreate(b:Bundle?){super.onCreate(b); val l=LinearLayout(this);l.orientation=LinearLayout.VERTICAL;l.setPadding(48,48,48,48); val t=TextView(this);t.text="EZiK\nYour Own Personal Agent";t.textSize=24f;val a=Button(this);a.text="Set EZiK as Assistant";a.setOnClickListener{val r=getSystemService(RoleManager::class.java);if(r.isRoleAvailable(RoleManager.ROLE_ASSISTANT)&&!r.isRoleHeld(RoleManager.ROLE_ASSISTANT))startActivityForResult(r.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT),1001)};val x=Button(this);x.text="Open Accessibility Settings";x.setOnClickListener{startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))};l.addView(t);l.addView(a);l.addView(x);setContentView(l)}}
