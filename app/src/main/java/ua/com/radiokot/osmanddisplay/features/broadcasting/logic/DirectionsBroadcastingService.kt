package ua.com.radiokot.osmanddisplay.features.broadcasting.logic

import android.app.*
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
import kotlin.math.roundToInt

class DirectionsBroadcastingService : Service(), OsmAndServiceConnectionListener {
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

            // Backpressure dropping strategy is set to eliminate queueing of outdated directions.
            .toFlowable(BackpressureStrategy.LATEST)

            // Do not re-broadcast items that are shown in the same way.
            .distinctUntilChanged { old, new ->
                old.isShownLike(new)
            }

            // Buffer size is set to 1 to eliminate queueing of outdated directions.
            .observeOn(Schedulers.io(), false, 1)

            // Concurrency factor of 1 for this flatMap with .delay is essential.
            // Otherwise it creates multiple subscriptions waiting for delay ending
            // in parallel, which breaks backpressure.
            .flatMapSingle({ direction ->
                commandSender
                    ?.send(DisplayCommand.ShowDirection(direction))
                    ?.doOnSubscribe {
                        logger.debug {
                            "subscribe_to_direction_send: " +
                                    "direction=$direction"
                        }
                    }
                    ?.doOnComplete {
                        logger.debug {
                            "display_delay_started: " +
                                    "direction=$direction"
                        }
                    }
                    // As we don't wait for an acknowledgment that the direction
                    // is actually displayed, a delay is introduced to make the subscription
                    // to wait for the time it normally takes to display a direction.
                    //
                    // In combination with backpressure, this eliminates
                    // queueing of outdated directions.
                    ?.delay(4800, TimeUnit.MILLISECONDS)
                    ?.toSingleDefault(direction)
                    ?: Single.error(Exception("Command sender is not set"))
            }, false, 1)
            .doOnSubscribe {
                logger.debug { "subscribed_to_directions" }
            }
            .subscribeOn(Schedulers.io())
            .subscribeBy(
                onNext = {
                    logger.debug {
                        "direction_processed: " +
                                "direction=$it"
                    }
                },
                onError = {
                    logger.error(it) { "direction_processing_error" }

                    subscribeToDirections()
                }
            )
            .addTo(compositeDisposable)
    }

    override fun onOsmAndServiceConnected() {
        status = Status.OsmAndConnected

        var muteNavigationUpdates = false

        osmAndAidlHelper.setNavigationInfoUpdateListener { directionInfo ->
            logger.debug {
                "navigation_info_update: " +
                        "turnType=${directionInfo.turnType}, " +
                        "\ndistance=${directionInfo.distanceTo}, " +
                        "\nmute=$muteNavigationUpdates"
            }

            if (!muteNavigationUpdates) {
                directionsSubject.onNext(NavigationDirection(directionInfo))
            }
        }

        osmAndAidlHelper.setVoiceRouterNotifyListener { voiceCommand ->
            val commands = voiceCommand.commands

            logger.debug {
                "voice_command: " +
                        commands.joinToString(":")
            }

            fun String.toDistance(): Int =
                toDouble().roundToInt().coerceAtLeast(0)

            val navigationDirection: NavigationDirection? =
                when {
//                    commands[0] == "go_ahead" -> {
//                        NavigationDirection(
//                            turnType = 1,
//                            distanceM = commands[1].toDistance()
//                        )
//                    }
                    commands[0] == "turn" || commands[0] == "prepare_turn" -> {
                        val distance = commands[2].toDistance()

                        when (commands[1]) {
                            "left" -> {
                                NavigationDirection(
                                    turnType = 2,
                                    distanceM = distance
                                )
                            }
                            "left_sh" -> {
                                NavigationDirection(
                                    turnType = 4,
                                    distanceM = distance
                                )
                            }
                            "left_sl" -> {
                                NavigationDirection(
                                    turnType = 3,
                                    distanceM = distance
                                )
                            }
                            "right" -> {
                                NavigationDirection(
                                    turnType = 5,
                                    distanceM = distance
                                )
                            }
                            "right_sh" -> {
                                NavigationDirection(
                                    turnType = 7,
                                    distanceM = distance
                                )
                            }
                            "right_sl" -> {
                                NavigationDirection(
                                    turnType = 6,
                                    distanceM = distance
                                )
                            }
                            else -> null
                        }
                    }
                    else -> null
                }

            if (navigationDirection != null) {
                // Mute regular directions when close to the turn.
                if (navigationDirection.distanceM == 0) {
                    muteNavigationUpdates = false
                } else if (navigationDirection.distanceM < 200) {
                    muteNavigationUpdates = true
                }

                directionsSubject.onNext(navigationDirection)
            }
        }

        if (osmAndAidlHelper.registerForVoiceRouterMessages(true, 1L) != -1L) {
            status = Status.RegisteredForNavigationUpdates
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

        NotificationChannelHelper.ensureBroadcastingNotificationChannel(this)

        return Notification.Builder(
            this,
            NotificationChannelHelper.BROADCASTING_NOTIFICATION_CHANNEL_ID
        )
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getText(R.string.directions_broadcasting_is_running))
            .setSmallIcon(R.drawable.ic_directions)
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

        osmAndAidlHelper.cleanupResources()
        compositeDisposable.dispose()

        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 1

        private const val DEVICE_ADDRESS_KEY = "device_address"

        fun getBundle(deviceAddress: String) = Bundle().apply {
            putString(DEVICE_ADDRESS_KEY, deviceAddress)
        }
    }
}