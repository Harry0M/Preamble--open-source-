package com.theblankstate.preamble.notification

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat

class VoiceEntryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            val aiIntent = Intent(this, VoiceTaskService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(aiIntent)
            } else {
                startService(aiIntent)
            }
            finish()
            overridePendingTransition(0, 0)
        } else {
            // Permission not granted, redirect to MainActivity which will ask for permission
            val mainIntent = Intent(this, com.theblankstate.preamble.MainActivity::class.java).apply {
                action = "REQUEST_MIC_PERMISSION"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(mainIntent)
            finish()
            overridePendingTransition(0, 0)
        }
    }
}
