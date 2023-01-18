package ua.com.radiokot.osmanddisplay.features.map.logic

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.mapbox.maps.CameraOptions
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.toSingle
import mu.KotlinLogging
import ua.com.radiokot.osmanddisplay.features.map.model.LocationData
import kotlin.math.roundToInt

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
        cameraZoom: Double,
        postScale: Double,
    ): Single<Bitmap> {
        return waitForSnapshotterSetup()
            .flatMap {
                getMapSnapshot(location, cameraZoom)
            }
            .flatMap { snapshot ->
                composeFrame(
                    snapshot = snapshot,
                    bearing = location.bearing,
                    postScale = postScale,
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
    ): Single<Bitmap> {
        snapshotter.setCamera(
            CameraOptions.Builder()
                .center(location.toPoint())
                .zoom(zoom)
                .bearing(location.bearing ?: 0.0)
                .build()
        )

        return snapshotter.getSnapshotBitmap()
            .doOnError {
                logger.warn { "getMapSnapshot(): snapshotter_not_ready" }
            }
            .doOnSuccess {
                logger.debug {
                    "getMapSnapshot(): got_snapshot:" +
                            "\nwidth=${it.width}," +
                            "\nheight=${it.height}"
                }
            }
    }

    private fun composeFrame(
        snapshot: Bitmap,
        bearing: Double?,
        postScale: Double,
    ): Single<Bitmap> = {
        // The lower the post scale, the bigger area of the snapshot
        // we have to fit into the frame
        val scaledFrameWidth = (frameWidthPx / postScale).toInt()
        val scaledFrameHeight = (frameHeightPx / postScale).toInt()

        // If there is a bearing, offset the center to see
        // more upcoming path.
        val centerYOffset =
            if (bearing != null && bearing != 0.0)
                (scaledFrameHeight * 0.65 / 2).roundToInt()
            else
                0

        // Create a frame which is bigger than the required
        // frame size to fit more area.
        val scaledFrame = Bitmap.createBitmap(
            snapshot,
            (snapshot.width - scaledFrameWidth) / 2,
            (snapshot.height - scaledFrameHeight) / 2 - centerYOffset,
            scaledFrameWidth,
            scaledFrameHeight
        )
        snapshot.recycle()

        val canvas = Canvas(scaledFrame)
        val locationX = canvas.width / 2f
        val locationY = canvas.height / 2f + centerYOffset

        // Draw the location marker.
        canvas.drawBitmap(
            locationMarker,
            locationX - locationMarker.width / 2f,
            locationY - locationMarker.height / 2f,
            null
        )

        // Draw the bearing indicator on the marker
        // as a line pointing from the center.
        canvas.drawLine(
            locationX,
            locationY,
            locationX,
            locationY - BEARING_CIRCLE_RADIUS,
            Paint().apply {
                color = Color.BLACK
                strokeWidth = BEARING_LINE_WIDTH
            }
        )

        // Return the result in the required size.
        Bitmap.createScaledBitmap(
            scaledFrame,
            frameWidthPx,
            frameHeightPx,
            false
        )
            .also { scaledFrame.recycle() }
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