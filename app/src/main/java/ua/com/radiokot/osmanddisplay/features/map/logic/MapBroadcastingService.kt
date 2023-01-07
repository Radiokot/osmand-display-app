package ua.com.radiokot.osmanddisplay.features.map.logic

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import io.reactivex.disposables.CompositeDisposable
import mu.KotlinLogging
import org.koin.android.ext.android.get
import org.koin.core.parameter.parametersOf
import ua.com.radiokot.osmanddisplay.R
import ua.com.radiokot.osmanddisplay.features.broadcasting.logic.DisplayCommandSender
import ua.com.radiokot.osmanddisplay.features.broadcasting.logic.NotificationChannelHelper
import ua.com.radiokot.osmanddisplay.features.main.view.MainActivity

class MapBroadcastingService : Service() {
    private val logger = KotlinLogging.logger("MapBcService@${hashCode()}")

    private lateinit var commandSender: DisplayCommandSender
    private lateinit var compositeDisposable: CompositeDisposable

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        compositeDisposable = CompositeDisposable()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())

        val deviceAddress = requireNotNull(intent?.getStringExtra(DEVICE_ADDRESS_KEY)) {
            "$DEVICE_ADDRESS_KEY extra must be set"
        }

        logger.debug {
            "starting: " +
                    "device_address=$deviceAddress"
        }

        commandSender = get {
            parametersOf(deviceAddress)
        }

        return super.onStartCommand(intent, flags, startId)
    }

    private fun createNotification(): Notification {
        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .let { notificationIntent ->
                    PendingIntent.getActivity(
                        this, 0, notificationIntent,
                        PendingIntent.FLAG_IMMUTABLE
                    )
                }

        NotificationChannelHelper.ensureBroadcastingNotificationChannel(this)

        return Notification.Builder(
            this,
            NotificationChannelHelper.BROADCASTING_NOTIFICATION_CHANNEL_ID
        )
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getText(R.string.map_broadcasting_is_running))
            .setSmallIcon(R.drawable.ic_map)
            .setContentIntent(pendingIntent)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                }
            }
            .build()
    }

    override fun onDestroy() {
        logger.debug { "destroying" }

        compositeDisposable.dispose()

        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 2

        private const val DEVICE_ADDRESS_KEY = "device_address"

        fun getBundle(deviceAddress: String) = Bundle().apply {
            putString(DEVICE_ADDRESS_KEY, deviceAddress)
        }
    }
}