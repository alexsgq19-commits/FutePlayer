package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager

class BackgroundSyncReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SYNC_NOTIFICATIONS = "com.example.ACTION_SYNC_NOTIFICATIONS"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "FutePlayer:BackgroundSyncWakeLock"
        )?.apply {
            acquire(3000L) // Acquire wakelock for max 3 seconds to guarantee background sync execution
        }

        try {
            val syncManager = NotificationSyncManager.getInstance(context)
            syncManager.startSync()
            syncManager.checkAllUpdates()
        } finally {
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
            } catch (_: Exception) {}
        }
    }
}
