package ua.com.radiokot.osmanddisplay.features.broadcasting.logic

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import ua.com.radiokot.osmanddisplay.R

object NotificationChannelHelper {
    fun ensureBroadcastingNotificationChannel(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.createNotificationChannel(
            NotificationChannel(
                BROADCASTING_NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.broadcasting_notifications),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    }

    const val BROADCASTING_NOTIFICATION_CHANNEL_ID = "service"
}