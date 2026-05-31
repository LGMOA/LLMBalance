package com.llmbalance.app.ntfy

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.llmbalance.app.R

object NotificationHelper {

    const val CHANNEL_ID = "ntfy_notification_channel"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Task Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for completed Claude tasks"
                enableVibration(true)
                setShowBadge(true)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun showTestNotification(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(
            9999,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Test Notification")
                .setContentText("If you see this, notifications are working!")
                .setSmallIcon(R.drawable.ic_notification)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
        )
    }
}
