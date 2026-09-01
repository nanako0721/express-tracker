package com.example.expresstracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

class MainActivity : ComponentActivity() {
    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) UpdateHelper.installIfReady(context)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
		if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),1001)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(downloadReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), RECEIVER_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(downloadReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        setContent { ExpressTrackerApp() }
    }

    override fun onResume() {
        super.onResume()
        UpdateHelper.installIfReady(this)
    }

    override fun onDestroy() {
        unregisterReceiver(downloadReceiver)
        super.onDestroy()
    }
}
