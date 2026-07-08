package com.aiexile.animetrack.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.aiexile.animetrack.MainActivity
import com.aiexile.animetrack.model.Anime

object NotificationHelper {

    private const val CHANNEL_ID = "anime_update"
    private const val CHANNEL_NAME = "番剧更新通知"
    private const val NOTIFICATION_ID = 1001

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "每日番剧更新提醒"
            setShowBadge(true)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    fun showUpdateNotification(context: Context, animeList: List<Anime>) {
        if (animeList.isEmpty()) return

        createChannel(context)

        val title = "今日番剧更新"
        val contentText = when (animeList.size) {
            1 -> "今天《${animeList[0].title}》更新"
            in 2..5 -> {
                val names = animeList.take(5).joinToString("、") { "《${it.title}》" }
                "今天有 ${animeList.size} 部番剧更新：$names"
            }
            else -> {
                val names = animeList.take(3).joinToString("、") { "《${it.title}》" }
                "今天有 ${animeList.size} 部番剧更新：$names 等 ${animeList.size} 部"
            }
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.aiexile.animetrack.R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(contentText)
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }
}
