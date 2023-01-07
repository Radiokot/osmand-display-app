package ua.com.radiokot.osmanddisplay.features.map.logic

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import com.mapbox.geojson.Point
import com.mapbox.maps.*
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import mu.KotlinLogging
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import org.koin.core.parameter.parametersOf
import ua.com.radiokot.osmanddisplay.R
import ua.com.radiokot.osmanddisplay.features.broadcasting.logic.DisplayCommandSender
import ua.com.radiokot.osmanddisplay.features.broadcasting.logic.NotificationChannelHelper
import ua.com.radiokot.osmanddisplay.features.main.view.MainActivity

class MapBroadcastingService : Service() {
    private val logger = KotlinLogging.logger("MapBcService@${hashCode()}")

    private lateinit var commandSender: DisplayCommandSender
    private lateinit var compositeDisposable: CompositeDisposable

    private val snapshotter: Snapshotter by inject { parametersOf(200f, 200f, this) }

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

        captureAndSendMap()

        return super.onStartCommand(intent, flags, startId)
    }

    private fun captureAndSendMap() {
        snapshotter.apply {
            setCamera(
                CameraOptions.Builder()
                    // TODO: Replace with the actual location
                    .center(Point.fromLngLat(35.0715, 48.4573))
                    .zoom(14.5)
                    .build()
            )

            start { snapshot ->
                val bitmap = snapshot?.bitmap()
                if (bitmap == null) {
                    logger.debug { "captureAndSendMap(): snapshotter_not_ready" }
                } else {
                    logger.debug { "captureAndSendMap(): got_snapshot" }
                    sendMap(bitmap)
                }
            }
        }
    }

    private fun sendMap(map: Bitmap) {
        val scaledFrame = Bitmap.createScaledBitmap(map, 200, 200, false)
        map.recycle()

        var startTime = 0L

        SendFrameUseCase(
            frame = scaledFrame,
            commandSender = commandSender
        )
            .perform()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe {
                logger.debug { "sendMap(): started" }
                startTime = System.currentTimeMillis()
            }
            .doOnTerminate {
                scaledFrame.recycle()
            }
            .subscribeBy(
                onComplete = {
                    logger.debug { "sendMap(): completed," +
                            "\ntime=${System.currentTimeMillis() - startTime}" }
                },
                onError = {
                    logger.error(it) {
                        "sendMap(): failed"
                    }
                }
            )
            .addTo(compositeDisposable)
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
        snapshotter.cancel()
        snapshotter.destroy()

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