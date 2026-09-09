package com.example.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.example.data.FutemaisRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotificationSyncManager(private val context: Context) {

    private val repository by lazy { FutemaisRepository(context) }
    private val notificationManager by lazy { AppNotificationManager(context) }
    private val sharedPrefs by lazy { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        @Volatile
        private var INSTANCE: NotificationSyncManager? = null

        fun getInstance(context: Context): NotificationSyncManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NotificationSyncManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    fun startSync() {
        startFirestoreSync()
        schedulePeriodicBackgroundCheck()
    }

    private fun startFirestoreSync() {
        repository.syncUpdateFromFirestore { url, version, timestamp ->
            val lastNotifiedTimestamp = sharedPrefs.getLong("last_notified_timestamp", 0L)
            if (url.isNotBlank() && timestamp > lastNotifiedTimestamp) {
                notificationManager.showAppUpdateNotification(version)
                sharedPrefs.edit()
                    .putLong("last_notified_timestamp", timestamp)
                    .putString("last_notified_url", url)
                    .putString("last_notified_version", version)
                    .apply()
            }
        }

        repository.syncFromFirestore {
            checkNewChannelsAndNotify()
        }
    }

    fun checkAllUpdates() {
        scope.launch {
            try {
                checkNewMatchesAndNotify()
                checkNewChannelsAndNotify()
            } catch (_: Exception) {}
        }
    }

    private fun checkNewChannelsAndNotify() {
        val quickChannels = repository.getQuickChannels()
        val notifiedIds = sharedPrefs.getStringSet("notified_channel_ids", null)
        val currentIds = quickChannels.map { it.id }.toSet()

        if (notifiedIds == null) {
            sharedPrefs.edit().putStringSet("notified_channel_ids", currentIds).apply()
        } else {
            val newChannels = quickChannels.filter { !notifiedIds.contains(it.id) }
            for (ch in newChannels) {
                notificationManager.showNewChannelNotification(
                    channelTitle = ch.title,
                    channelSubtitle = ch.subtitle,
                    channelId = ch.id
                )
            }
            if (newChannels.isNotEmpty()) {
                val updatedNotified = (notifiedIds + currentIds).toSet()
                sharedPrefs.edit().putStringSet("notified_channel_ids", updatedNotified).apply()
            }
        }
    }

    private suspend fun checkNewMatchesAndNotify() {
        val result = repository.fetchMatches()
        result.onSuccess { matchesList ->
            val previouslyNotifiedIds = sharedPrefs.getStringSet("notified_match_ids", null)
            val currentMatchIds = matchesList.map { it.id }.toSet()

            if (previouslyNotifiedIds == null) {
                sharedPrefs.edit().putStringSet("notified_match_ids", currentMatchIds).apply()
            } else {
                val newMatches = matchesList.filter { !previouslyNotifiedIds.contains(it.id) }
                if (newMatches.isNotEmpty()) {
                    if (newMatches.size == 1) {
                        val m = newMatches.first()
                        notificationManager.showNewMatchNotification(
                            title = "${m.homeTeam} vs ${m.awayTeam}",
                            time = m.time,
                            league = m.championship,
                            matchId = m.id
                        )
                    } else {
                        val first = newMatches.first()
                        notificationManager.showNewMatchNotification(
                            title = "${newMatches.size} novos jogos ao vivo disponíveis!",
                            time = "Hoje e Próximos",
                            league = "${first.homeTeam} vs ${first.awayTeam} e outros",
                            matchId = first.id
                        )
                    }
                    val updatedNotified = (previouslyNotifiedIds + currentMatchIds).toSet()
                    sharedPrefs.edit().putStringSet("notified_match_ids", updatedNotified).apply()
                }
            }
        }
    }

    fun schedulePeriodicBackgroundCheck() {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, BackgroundSyncReceiver::class.java).apply {
                action = BackgroundSyncReceiver.ACTION_SYNC_NOTIFICATIONS
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val intervalMs = 15 * 60 * 1000L // 15 minutes
            val triggerAtMs = SystemClock.elapsedRealtime() + intervalMs

            alarmManager.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtMs,
                intervalMs,
                pendingIntent
            )
        } catch (_: Exception) {}
    }
}
