package ua.com.radiokot.osmanddisplay.features.map.logic

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.bitmap
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.toSingle
import mu.KotlinLogging
import ua.com.radiokot.osmanddisplay.features.map.model.LocationData
import kotlin.math.cos
import kotlin.math.sin

/**
 * Composes map frames based on [FriendlySnapshotter] map snapshots.
 * Draws location marker and bearing, if it is available.
 */
class SnapshotterMapFrameFactory(
    private val snapshotter: FriendlySnapshotter,
    private val locationMarker: Bitmap,
    private val frameWidthPx: Int,
    private val frameHeightPx: Int,
) : MapFrameFactory {
    private val logger = KotlinLogging.logger("SnapshotterMFF@${hashCode()}")

    override fun composeFrame(
        location: LocationData,
        zoom: Double
    ): Single<Bitmap> {
        return waitForSnapshotterSetup()
            .flatMap {
                getMapSnapshot(location, zoom)
            }
            .flatMap { snapshot ->
                composeFrame(
                    snapshot = snapshot,
                    bearing = location.bearing,
                )
            }
    }

    private fun waitForSnapshotterSetup(): Single<Boolean> = Single.create<Boolean?> { emitter ->
        // Snapshotter is not ready if its style is not loaded
        // or the map wasn't ever loaded, which causes
        // visual glitch in the first snapshot.
        snapshotter.apply {
            fun confirmAndUnsubscribe() {
                logger.debug {
                    "waitForSnapshotterSetup(): snapshotter_is_set_up"
                }

                onMapLoaded(null)
                emitter.onSuccess(true)
            }

            fun triggerMapLoading() {
                onMapLoaded(::confirmAndUnsubscribe)

                logger.debug {
                    "waitForSnapshotterSetup(): triggering_map_loading_snapshot"
                }

                start {}
            }

            when {
                // Everything is loaded.
                isStyleLoaded && isMapEverLoaded -> {
                    confirmAndUnsubscribe()
                }
                // Map wasn't ever loaded, trigger the loading.
                isStyleLoaded -> {
                    triggerMapLoading()
                }
                // Nothing is loaded, wait for the style and then trigger the map loading.
                else -> {
                    logger.debug {
                        "waitForSnapshotterSetup(): waiting_for_style_loading"
                    }

                    addStyleLoadedListenerOnce {
                        triggerMapLoading()
                    }
                }
            }
        }
    }
        // Important for Snapshotter.
        .subscribeOn(AndroidSchedulers.mainThread())

    private fun getMapSnapshot(
        location: LocationData,
        zoom: Double,
    ): Single<Bitmap> = Single.create<Bitmap> { emitter ->
        snapshotter.apply {
            setCamera(
                CameraOptions.Builder()
                    .center(location.toPoint())
                    .zoom(zoom)
                    .build()
            )

            start { snapshot ->
                val bitmap = snapshot?.bitmap()
                if (bitmap == null) {
                    logger.warn { "getMapSnapshot(): snapshotter_not_ready" }

                    emitter.tryOnError(RuntimeException("Snapshotter is not ready"))
                } else {
                    logger.debug { "getMapSnapshot(): got_snapshot" }

                    emitter.onSuccess(bitmap)
                }
            }
        }
    }
        // Important for Snapshotter.
        .subscribeOn(AndroidSchedulers.mainThread())

    private fun composeFrame(
        snapshot: Bitmap,
        bearing: Double?,
    ): Single<Bitmap> = {
        // Resize the snapshot to match the display size.
        val frame = Bitmap.createScaledBitmap(
            snapshot,
            frameWidthPx,
            frameHeightPx,
            false
        )
        snapshot.recycle()

        val canvas = Canvas(frame)
        val centerX = canvas.width / 2f
        val centerY = canvas.height / 2f

        // Draw the location marker, which is always in the center.
        canvas.drawBitmap(
            locationMarker,
            centerX - locationMarker.width / 2f,
            centerY - locationMarker.height / 2f,
            null
        )

        // Draw the bearing indicator as a line pointing from the center.
        if (bearing != null) {
            canvas.drawLine(
                centerX,
                centerY,
                centerX + BEARING_CIRCLE_RADIUS * sin(Math.toRadians(bearing)).toFloat(),
                // Subtract the Y coordinate as canvas Y axis is top-to-bottom.
                centerY - BEARING_CIRCLE_RADIUS * cos(Math.toRadians(bearing)).toFloat(),
                Paint().apply {
                    color = Color.BLACK
                    strokeWidth = BEARING_LINE_WIDTH
                }
            )
        }

        frame
    }.toSingle()

    override fun destroy() {
        snapshotter.cancel()
        snapshotter.destroy()
    }

    private companion object {
        private const val BEARING_CIRCLE_RADIUS = 10f
        private const val BEARING_LINE_WIDTH = 5f
    }
}