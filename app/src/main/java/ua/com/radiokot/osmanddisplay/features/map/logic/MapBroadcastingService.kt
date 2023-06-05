package ua.com.radiokot.osmanddisplay.features.map.logic

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.location.Location
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.mapbox.maps.*
import io.reactivex.BackpressureStrategy
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinComponent
import org.koin.core.parameter.parametersOf
import ua.com.radiokot.osmanddisplay.R
import ua.com.radiokot.osmanddisplay.base.extension.getNumericProperty
import ua.com.radiokot.osmanddisplay.base.extension.kLogger
import ua.com.radiokot.osmanddisplay.features.broadcasting.logic.DisplayCommandSender
import ua.com.radiokot.osmanddisplay.features.broadcasting.logic.NotificationChannelHelper
import ua.com.radiokot.osmanddisplay.features.main.view.MainActivity
import ua.com.radiokot.osmanddisplay.features.map.model.LocationData
import ua.com.radiokot.osmanddisplay.features.track.data.model.ImportedTrackRecord
import java.util.*
import java.util.concurrent.TimeUnit

class MapBroadcastingService : Service(), KoinComponent {
    inner class Binder : android.os.Binder() {
        val deviceAddress: String?
            get() = this@MapBroadcastingService.deviceAddress
    }

    private val logger = kLogger("MapBcService")

    private val mapCameraZoom: Double =
        requireNotNull(getKoin().getNumericProperty("mapCameraZoom"))
    private val mapFramePostScale: Double =
        getKoin().getNumericProperty("mapFramePostScale", 1.0)

    private val notificationManager: NotificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private var deviceAddress: String? = null
    private lateinit var commandSender: DisplayCommandSender
    private lateinit var compositeDisposable: CompositeDisposable
    private lateinit var binder: Binder
    private var composedFramesCounter = 0

    // Needs to be injected in the main thread.
    private lateinit var mapFrameFactory: MapFrameFactory
    private lateinit var invertedLocationMarkerMapFrameFactory: MapFrameFactory

    private val locationClient: FusedLocationProviderClient by inject()

    private val locationRequest = LocationRequest.Builder(8000)
        .setMinUpdateIntervalMillis(7000)
        .setMaxUpdateDelayMillis(15000)
        .setMinUpdateDistanceMeters(2f)
        .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
        .setGranularity(Granularity.GRANULARITY_FINE)
        .build()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation
                ?.also(this@MapBroadcastingService::onNewLocationFromClient)
        }
    }

    private val locationsSubject: Subject<LocationData> = PublishSubject.create()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        compositeDisposable = CompositeDisposable()
        binder = Binder()

        logger.debug { "onCreate(): created" }
    }

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        startForeground(NOTIFICATION_ID, getNotification())

        val deviceAddress = requireNotNull(intent?.getStringExtra(DEVICE_ADDRESS_EXTRA)) {
            "$DEVICE_ADDRESS_EXTRA extra must be set"
        }
        val track = intent?.getParcelableExtra<ImportedTrackRecord>(TRACK_EXTRA)

        logger.debug {
            "onStartCommand(): starting:" +
                    "\ndevice_address=$deviceAddress," +
                    "\ntrack=$track" +
                    "\nflags=$flags"
        }

        this.deviceAddress = deviceAddress
        commandSender = get { parametersOf(deviceAddress) }
        mapFrameFactory = get { parametersOf(track, false) }
        invertedLocationMarkerMapFrameFactory = get { parametersOf(track, true) }

        subscribeToLocations()

        requestLocationUpdates()

        return START_REDELIVER_INTENT
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
                "subscribeToLocationUpdates(): error_occurred"
            }
        }
    }

    private fun onNewLocationFromClient(location: Location) {
        logger.debug {
            "onLocationResult(): received_location:" +
                    "\nlocation=${location}"
        }

        locationsSubject.onNext(
            // If the speed is significant, feed the predicted future location.
            if (location.speed >= PREDICTION_SPEED_THRESHOLD_MS && location.hasBearing()) {
                logger.debug {
                    "onLocationResult(): predict_future_location"
                }

                LocationData.ofDestinationPoint(
                    currentLocation = location,
                    travelTimeS = AVERAGE_FRAME_PROCESSING_TIME_S,
                )
            } else {
                LocationData(location)
            }
        )
    }

    private var locationsDisposable: Disposable? = null
    private fun subscribeToLocations() {
        var sendStartTime = 0L
        var frameToSend: Bitmap? = null

        locationsDisposable?.dispose()
        locationsDisposable = locationsSubject
            // Backpressure dropping strategy is set to eliminate queueing of outdated locations.
            .toFlowable(BackpressureStrategy.LATEST)
            // Buffer size is set to 1 to eliminate queueing of outdated locations.
            .observeOn(Schedulers.io(), false, 1)
            .concatMapSingle { location ->
                // Use factory with inverted location marker from time to time
                // to avoid "burning out" the location marker on the e-ink surface.
                val frameFactory =
                    if ((++composedFramesCounter) % 3 == 0)
                        invertedLocationMarkerMapFrameFactory
                    else
                        mapFrameFactory

                frameFactory
                    .composeFrame(
                        location = location,
                        cameraZoom = mapCameraZoom,
                        postScale = mapFramePostScale,
                    )
                    .timeout(5, TimeUnit.SECONDS, Schedulers.io())
                    .map { it to location }
            }
            .concatMapSingle { (frame, location) ->
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
                    .timeout(15, TimeUnit.SECONDS, Schedulers.io())
            }
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

                    frameToSend?.recycle()
                    subscribeToLocations()
                }
            )
            .addTo(compositeDisposable)
    }

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
        logger.debug { "onDestroy(): destroying" }

        compositeDisposable.dispose()
        if (this::mapFrameFactory.isInitialized) {
            mapFrameFactory.destroy()
        }
        locationClient.removeLocationUpdates(locationCallback)

        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 2
        private const val PREDICTION_SPEED_THRESHOLD_MS = 2.5
        private const val AVERAGE_FRAME_PROCESSING_TIME_S = 6

        private const val DEVICE_ADDRESS_EXTRA = "device_address"
        private const val TRACK_EXTRA = "track"

        fun getBundle(
            deviceAddress: String,
            track: ImportedTrackRecord?
        ) = Bundle().apply {
            putString(DEVICE_ADDRESS_EXTRA, deviceAddress)
            putParcelable(TRACK_EXTRA, track)
        }
    }
}
