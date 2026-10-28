package com.voicepoco.app

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log

class HarnessActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val backend = intent?.getStringExtra("backend") ?: "rules"
        Log.i("POCO_HARNESS", "ENTER backend=" + backend)
        val i = Intent(this, HarnessService::class.java).putExtra("backend", backend)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        finish()
    }
}
