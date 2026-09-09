package com.example

import android.app.Application
import android.system.Os
import com.example.notifications.NotificationSyncManager
import java.io.File

class FutePlayerApplication : Application() {

    companion object {
        init {
            try {
                Os.setenv("LIBGL_ALWAYS_SOFTWARE", "1", true)
                Os.setenv("MESA_LOADER_DRIVER_OVERRIDE", "swrast", true)
                Os.setenv("MESA_LOG_LEVEL", "none", true)
                Os.setenv("MESA_DEBUG", "0", true)
            } catch (_: Exception) {}
        }
    }

    override fun onCreate() {
        super.onCreate()
        
        try {
            Os.setenv("LIBGL_ALWAYS_SOFTWARE", "1", true)
            Os.setenv("MESA_LOADER_DRIVER_OVERRIDE", "swrast", true)
            Os.setenv("MESA_LOG_LEVEL", "none", true)
            Os.setenv("MESA_DEBUG", "0", true)
        } catch (_: Exception) {}
        
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
