package com.example

import android.app.Application
import com.example.notifications.NotificationSyncManager
import java.io.File

class FutePlayerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        
        // Prepare WebView cache and code directories to prevent Chromium Simple Cache init errors
        try {
            val cacheBase = File(cacheDir, "WebView/Default/HTTP Cache/Code Cache")
            File(cacheBase, "wasm").mkdirs()
            File(cacheBase, "js").mkdirs()
            cacheBase.setReadable(true, false)
            cacheBase.setWritable(true, false)
            cacheBase.setExecutable(true, false)
        } catch (_: Exception) {}

        // Initialize background notification sync listener globally for the process
        NotificationSyncManager.getInstance(this).startSync()
    }
}
