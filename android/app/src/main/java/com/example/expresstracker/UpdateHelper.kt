package com.example.expresstracker

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection

object UpdateHelper {
    const val URL = ""
    const val VERSION = "1.0.3"
    private const val DAY = 86_400_000L
    private const val DOWNLOAD_ID = "update_download_id"

    fun shouldAutoCheck(context: Context): Boolean = false

    suspend fun exists(): Boolean = false

    fun download(context: Context) = Unit

    fun installIfReady(context: Context) = Unit
}
