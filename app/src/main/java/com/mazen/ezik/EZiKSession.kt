package com.mazen.ezik
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.service.voice.VoiceInteractionSession
import android.view.*
import android.widget.*
class EZiKSession(context:Context):VoiceInteractionSession(context){override fun onCreateContentView():View{val r=LinearLayout(context);r.orientation=LinearLayout.VERTICAL;r.setPadding(32,24,32,24);val t=TextView(context);t.text="EZiK";t.textSize=28f;val e=EditText(context);e.hint="What should I do?";val b=Button(context);b.text="Run";b.setOnClickListener{run(e.text.toString())};r.addView(t);r.addView(e);r.addView(b);return r}private fun run(s:String){if(s.contains("goodreads",true)){context.packageManager.getLaunchIntentForPackage("com.goodreads")?.let{it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);context.startActivity(it);hide();return}};context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/search?q="+Uri.encode(s))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));hide()}}
