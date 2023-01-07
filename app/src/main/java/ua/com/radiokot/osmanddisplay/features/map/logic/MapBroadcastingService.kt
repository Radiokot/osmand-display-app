package ua.com.radiokot.osmanddisplay.features.map.logic

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.mapbox.maps.*
import io.reactivex.BackpressureStrategy
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.rxkotlin.toSingle
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import mu.KotlinLogging
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import org.koin.core.parameter.parametersOf
import ua.com.radiokot.osmanddisplay.R
import ua.com.radiokot.osmanddisplay.features.broadcasting.logic.DisplayCommandSender
import ua.com.radiokot.osmanddisplay.features.broadcasting.logic.NotificationChannelHelper
import ua.com.radiokot.osmanddisplay.features.main.view.MainActivity
import ua.com.radiokot.osmanddisplay.features.map.model.LocationData

class MapBroadcastingService : Service() {
    private val logger = KotlinLogging.logger("MapBcService@${hashCode()}")

    private val notificationManager: NotificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private lateinit var commandSender: DisplayCommandSender
    private lateinit var compositeDisposable: CompositeDisposable

    private val snapshotter: Snapshotter by inject { parametersOf(200f, 200f, this) }
    private val locationMarker: Bitmap by lazy {
        BitmapFactory.decodeResource(resources, R.drawable.me)
    }

    private val locationClient: FusedLocationProviderClient by inject()

    private val locationRequest = LocationRequest.Builder(10000)
        .setMinUpdateIntervalMillis(10000)
        .setMaxUpdateDelayMillis(30000)
        .setMinUpdateDistanceMeters(2f)
        .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
        .build()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.also { lastLocation ->
                logger.debug {
                    "onLocationResult(): received_location:" +
                            "\nlng=${lastLocation.longitude}," +
                            "\nlat=${lastLocation.latitude}"
                }

                // TODO: Speed & bearing.
                locationsSubject.onNext(LocationData(lastLocation))
            }
        }
    }

    private val locationsSubject: Subject<LocationData> = PublishSubject.create()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        compositeDisposable = CompositeDisposable()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        startForeground(NOTIFICATION_ID, getNotification())

        val deviceAddress = requireNotNull(intent?.getStringExtra(DEVICE_ADDRESS_KEY)) {
            "$DEVICE_ADDRESS_KEY extra must be set"
        }

        logger.debug {
            "onStartCommand(): starting:" +
                    "\ndevice_address=$deviceAddress"
        }

        commandSender = get {
            parametersOf(deviceAddress)
        }

        snapshotter.setStyleListener(object : SnapshotStyleListener {
            override fun onDidFinishLoadingStyle(style: Style) {
                // TODO: Set track here.
//                logger.debug { "snapshotter_style_extended" }
            }
        })

        requestLocationUpdates()

        subscribeToLocations()

        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates() {
        try {
            locationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: Exception) {
            logger.debug(e) {
                "subscribeToLocationUpdates(): failed"
            }
            // TODO: Remove once the logger is fixed.
            e.printStackTrace()
        }
    }

    private var locationsDisposable: Disposable? = null
    private fun subscribeToLocations() {
        var sendStartTime = 0L
        lateinit var frameToSend: Bitmap

        locationsDisposable?.dispose()
        locationsDisposable = locationsSubject

            // Backpressure dropping strategy is set to eliminate queueing of outdated locations.
            .toFlowable(BackpressureStrategy.LATEST)

            // Buffer size is set to 1 to eliminate queueing of outdated locations.
            .observeOn(Schedulers.io(), false, 1)

            // Concurrency factor of 1 for this flatMap is essential.
            // Otherwise it creates multiple subscriptions waiting for the ending
            // in parallel, which breaks backpressure.
            .flatMapSingle({ location ->
                getMapSnapshot(location)
                    .observeOn(Schedulers.io())
                    .flatMap { snapshot ->
                        composeFrame(snapshot)
                            .doOnSuccess {
                                logger.debug {
                                    "subscribeToLocations(): frame_composed:" +
                                            "\nlocation=$location"
                                }
                            }
                    }
                    .flatMap { frame ->
                        frameToSend = frame

                        SendFrameUseCase(
                            frame = frame,
                            commandSender = commandSender
                        )
                            .perform()
                            .toSingleDefault(frame to location)
                            .doOnSubscribe {
                                logger.debug {
                                    "subscribeToLocations(): subscribed_to_frame_sending:" +
                                            "\nlocation=$location"
                                }
                                sendStartTime = System.currentTimeMillis()
                            }
                    }
            }, false, 1)
            .doOnSubscribe {
                logger.debug { "subscribeToLocations(): subscribed" }
            }
            .subscribeOn(Schedulers.io())
            .subscribeBy(
                onNext = { (sentFrame, location) ->
                    logger.debug {
                        "subscribeToLocations(): frame_sent," +
                                "\ntime=${System.currentTimeMillis() - sendStartTime}," +
                                "\nlocation=$location"
                    }

                    addSentFrameToNotification(sentFrame)
                },
                onError = {
                    logger.error(it) { "subscribeToLocations(): error_occurred" }
                    // TODO: Remove once the logger is fixed.
                    it.printStackTrace()

                    frameToSend.recycle()
                    subscribeToLocations()
                }
            )
            .addTo(compositeDisposable)
    }

    private fun getMapSnapshot(locationData: LocationData): Single<Bitmap> =
        Single.create { emitter ->
            snapshotter.apply {
                setCamera(
                    CameraOptions.Builder()
                        .center(locationData.toPoint())
                        .zoom(14.5)
                        .build()
                )

                start { snapshot ->
                    val bitmap = snapshot?.bitmap()
                    if (bitmap == null) {
                        logger.debug { "captureAndSendMap(): snapshotter_not_ready" }

                        emitter.tryOnError(RuntimeException("Snapshotter is not ready"))
                    } else {
                        logger.debug { "captureAndSendMap(): got_snapshot" }

                        emitter.onSuccess(bitmap)
                    }
                }
            }
        }
            // Important for Snapshotter.
            .subscribeOn(AndroidSchedulers.mainThread())

    private fun composeFrame(map: Bitmap): Single<Bitmap> = {
        val resizedMap = Bitmap.createScaledBitmap(map, 200, 200, false)
        map.recycle()

        val canvas = Canvas(resizedMap)
        canvas.drawBitmap(
            locationMarker,
            (map.width - locationMarker.width) / 2f,
            (map.height - locationMarker.height) / 2f,
            null
        )

        resizedMap
    }.toSingle()

    private fun getNotification(frame: Bitmap? = null): Notification {
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

        return NotificationCompat.Builder(
            this,
            NotificationChannelHelper.BROADCASTING_NOTIFICATION_CHANNEL_ID
        )
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getText(R.string.map_broadcasting_is_running))
            .setSmallIcon(R.drawable.ic_map)
            .setContentIntent(pendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setOngoing(true)
            .apply {
                if (frame != null) {
                    setLargeIcon(frame)
                }
            }
            .build()
    }

    private var lastShownNotificationFrame: Bitmap? = null
    private fun addSentFrameToNotification(frame: Bitmap) {
        lastShownNotificationFrame?.recycle()
        lastShownNotificationFrame = frame
        notificationManager.notify(
            NOTIFICATION_ID,
            getNotification(frame)
        )
    }

    override fun onDestroy() {
        logger.debug { "destroying" }

        compositeDisposable.dispose()
        snapshotter.cancel()
        snapshotter.destroy()
        locationClient.removeLocationUpdates(locationCallback)

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