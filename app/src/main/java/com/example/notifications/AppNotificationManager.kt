package com.example.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

class AppNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_MATCHES = "channel_live_matches"
        const val CHANNEL_CHANNELS = "channel_quick_channels"

        const val EXTRA_TARGET_TYPE = "extra_target_type"
        const val EXTRA_TARGET_ID = "extra_target_id"
        const val TARGET_MATCH = "target_match"
        const val TARGET_CHANNEL = "target_channel"
        const val TARGET_UPDATE = "target_update"
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()
            val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val matchesChannel = NotificationChannel(
                CHANNEL_MATCHES,
                "Jogos e Partidas Ao Vivo",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificações de novos jogos de futebol, partidas ao vivo e transmissões iniciadas"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500)
                enableLights(true)
                lightColor = 0xFF00E676.toInt()
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(defaultSoundUri, audioAttributes)
                setShowBadge(true)
            }

            val channelsChannel = NotificationChannel(
                CHANNEL_CHANNELS,
                "Novos Canais e Transmissões",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Avisos sobre novos canais rápidos e emissoras adicionadas"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
                enableLights(true)
                lightColor = 0xFF00E676.toInt()
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(defaultSoundUri, audioAttributes)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(matchesChannel)
            notificationManager.createNotificationChannel(channelsChannel)
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    fun showNewMatchNotification(
        title: String,
        time: String,
        league: String,
        matchId: String
    ) {
        if (!hasNotificationPermission()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TARGET_TYPE, TARGET_MATCH)
            putExtra(EXTRA_TARGET_ID, matchId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            matchId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_MATCHES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("⚽ Novo Jogo Disponível: $title")
            .setContentText("$league • Horário: $time")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("A partida $title ($league) está programada para $time. Toque para acompanhar as transmissões!")
            )
            .setColor(0xFF00E676.toInt())
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(matchId.hashCode(), notification)
        } catch (_: SecurityException) {}
    }

    fun showNewChannelNotification(
        channelTitle: String,
        channelSubtitle: String,
        channelId: String
    ) {
        if (!hasNotificationPermission()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TARGET_TYPE, TARGET_CHANNEL)
            putExtra(EXTRA_TARGET_ID, channelId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            channelId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_CHANNELS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("📺 Novo Canal Adicionado: $channelTitle")
            .setContentText(channelSubtitle.ifBlank { "Já disponível para assistir agora!" })
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("O canal '$channelTitle' foi adicionado aos Canais Rápidos. Toque para abrir e assistir.")
            )
            .setColor(0xFF00E676.toInt())
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(channelId.hashCode(), notification)
        } catch (_: SecurityException) {}
    }

    fun showAppUpdateNotification(versionName: String) {
        if (!hasNotificationPermission()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TARGET_TYPE, TARGET_UPDATE)
            putExtra(EXTRA_TARGET_ID, "update")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            9999,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_MATCHES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("🚀 Nova Atualização Disponível (v$versionName)")
            .setContentText("Uma nova versão do app está pronta. Toque para atualizar na aba Suporte.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Uma nova versão ($versionName) do aplicativo foi lançada. Acesse a aba de Suporte para baixar e atualizar agora mesmo!")
            )
            .setColor(0xFF00E676.toInt())
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(9999, notification)
        } catch (_: SecurityException) {}
    }

    fun showCustomNotification(title: String, message: String) {
        if (!hasNotificationPermission()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            "custom_notification".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_CHANNELS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(message)
            )
            .setColor(0xFF00E676.toInt())
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(System.currentTimeMillis().hashCode(), notification)
        } catch (_: SecurityException) {}
    }

    fun showAdminChannelAlertNotification(
        title: String,
        message: String,
        offlineCount: Int
    ) {
        if (!hasNotificationPermission()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TARGET_TYPE, TARGET_CHANNEL)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            "admin_channels_alert".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_CHANNELS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(message)
            )
            .setColor(if (offlineCount > 0) 0xFFFF1744.toInt() else 0xFF00E676.toInt())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify("admin_channel_diagnostic".hashCode(), notification)
        } catch (_: SecurityException) {}
    }
}
