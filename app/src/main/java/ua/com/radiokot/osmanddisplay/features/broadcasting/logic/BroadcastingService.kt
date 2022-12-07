package ua.com.radiokot.osmanddisplay.features.broadcasting.logic

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import org.koin.core.parameter.parametersOf
import ua.com.radiokot.osmanddisplay.R
import ua.com.radiokot.osmanddisplay.features.main.view.MainActivity

class BroadcastingService : Service(), OsmAndServiceConnectionListener {
    sealed class Status {
        object Created : Status()
        object OsmAndBind : Status()
        object OsmAndConnected : Status()
        object RegisteredForNavigationUpdates : Status()
        object OsmAndDisconnected : Status()
        object OsmAndUnbind : Status()
        object OsmAndNotFound : Status()
    }

    private var status: Status = Status.Created
        set(value) {
            field = value
            Log.d(LOG_TAG, "status: status_changed: new=$status")
        }

    private val osmAndAidlHelper: OsmAndAidlHelper by inject {
        parametersOf(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        Log.d(LOG_TAG, "onCreate: creating: instance=$this")

        super.onCreate()

        status =
            if (osmAndAidlHelper.bindService()) {
                Status.OsmAndBind
            } else {
                Status.OsmAndNotFound
            }
    }

    override fun onOsmAndServiceConnected() {
        status = Status.OsmAndConnected

        osmAndAidlHelper.setNavigationInfoUpdateListener { directionInfo ->
            Log.d(
                LOG_TAG, "onNavigationInfoUpdate: received: turnType=${directionInfo.turnType}," +
                        "\ndistance=${directionInfo.distanceTo}"
            )
        }

        if (osmAndAidlHelper.registerForNavigationUpdates(true, 0L) != -1L) {
            status = Status.RegisteredForNavigationUpdates
        }
    }

    override fun onOsmAndServiceDisconnected() {
        status = Status.OsmAndDisconnected
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(LOG_TAG, "onStartCommand: starting: instance=$this")

        startForeground(NOTIFICATION_ID, createNotification())

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

        ensureNotificationChannel()

        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getText(R.string.broadcasting_is_running))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                }
            }
            .build()
    }

    private fun ensureNotificationChannel() {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.broadcasting_notifications),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    }

    override fun onDestroy() {
        Log.d(LOG_TAG, "onDestroy: destroying: instance=$this")

        osmAndAidlHelper.cleanupResources()

        super.onDestroy()
    }

    private companion object {
        private const val LOG_TAG = "BcService"
        private const val NOTIFICATION_CHANNEL_ID = "service"
        private const val NOTIFICATION_ID = 1
    }
}