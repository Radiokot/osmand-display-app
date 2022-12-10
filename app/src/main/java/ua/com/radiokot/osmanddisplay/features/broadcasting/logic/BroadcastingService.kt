package ua.com.radiokot.osmanddisplay.features.broadcasting.logic

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import io.reactivex.BackpressureStrategy
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import mu.KotlinLogging
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import org.koin.core.parameter.parametersOf
import ua.com.radiokot.osmanddisplay.R
import ua.com.radiokot.osmanddisplay.features.broadcasting.model.DisplayCommand
import ua.com.radiokot.osmanddisplay.features.broadcasting.model.NavigationDirection
import ua.com.radiokot.osmanddisplay.features.main.view.MainActivity
import java.util.concurrent.TimeUnit

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

    private val logger = KotlinLogging.logger("BcService@${hashCode()}")

    private var status: Status = Status.Created
        set(value) {
            field = value

            logger.debug {
                "status_changed: " +
                        "new=$status"
            }
        }

    private val osmAndAidlHelper: OsmAndAidlHelper by inject {
        parametersOf(this)
    }

    private val directionsSubject: Subject<NavigationDirection> = PublishSubject.create()

    private var commandSender: DisplayCommandSender? = null

    private lateinit var compositeDisposable: CompositeDisposable

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        logger.debug { "creating" }

        super.onCreate()

        compositeDisposable = CompositeDisposable()

        initOsmAnd()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())

        val deviceAddress = intent?.getStringExtra(DEVICE_ADDRESS_KEY)

        logger.debug {
            "starting: " +
                    "device_address=$deviceAddress"
        }

        if (deviceAddress != null) {
            commandSender = get { parametersOf(deviceAddress) }
            subscribeToDirections()
        }

        return super.onStartCommand(intent, flags, startId)
    }

    private fun initOsmAnd() {
        status =
            if (osmAndAidlHelper.bindService()) {
                Status.OsmAndBind
            } else {
                Status.OsmAndNotFound
            }
    }

    private var directionsDisposable: Disposable? = null
    private fun subscribeToDirections() {
        directionsDisposable?.dispose()
        directionsDisposable = directionsSubject
            .toFlowable(BackpressureStrategy.DROP)
            .observeOn(Schedulers.io(), false, 1)
            .distinctUntilChanged { old, new ->
                // Do not re-broadcast items that are shown in the same way.
                old.isShownLike(new)
            }
            .flatMapSingle { direction ->
                commandSender
                    ?.send(DisplayCommand.ShowDirection(direction))
                    ?.doOnSubscribe {
                        logger.debug {
                            "subscribe_to_direction_send: " +
                                    "direction=$direction"
                        }
                    }
                    /** As we don't wait for an acknowledgment that the direction
                     * is actually displayed, it is better to introduce a delay
                     * for the time it normally takes to display a direction.
                     * Combined with backpressure, this delay eliminates
                     * queueing of outdated directions */
                    ?.delay(4800, TimeUnit.MILLISECONDS)
                    ?.toSingleDefault(direction)
                    ?: Single.error(Exception("Command sender is not set"))
            }
            .doOnSubscribe {
                logger.debug { "subscribed_to_directions" }
            }
            .subscribeOn(Schedulers.io())
            .subscribeBy(
                onNext = {
                    logger.debug {
                        "direction_sent: " +
                                "direction=$it"
                    }
                },
                onError = {
                    logger.error(it) { "direction_send_error" }
                    subscribeToDirections()
                },
                onComplete = {}
            )
            .addTo(compositeDisposable)
    }

    override fun onOsmAndServiceConnected() {
        status = Status.OsmAndConnected

        osmAndAidlHelper.setNavigationInfoUpdateListener { directionInfo ->
            logger.debug {
                "navigation_info_update: " +
                        "turnType=${directionInfo.turnType}, " +
                        "\ndistance=${directionInfo.distanceTo}"
            }

            directionsSubject.onNext(NavigationDirection(directionInfo))
        }

        if (osmAndAidlHelper.registerForNavigationUpdates(true, 0L) != -1L) {
            status = Status.RegisteredForNavigationUpdates
        }
    }

    override fun onOsmAndServiceDisconnected() {
        status = Status.OsmAndDisconnected
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
        logger.debug { "destroying" }

        osmAndAidlHelper.cleanupResources()
        compositeDisposable.dispose()

        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "service"
        private const val NOTIFICATION_ID = 1

        private const val DEVICE_ADDRESS_KEY = "device_address"

        fun getBundle(deviceAddress: String) = Bundle().apply {
            putString(DEVICE_ADDRESS_KEY, deviceAddress)
        }
    }
}