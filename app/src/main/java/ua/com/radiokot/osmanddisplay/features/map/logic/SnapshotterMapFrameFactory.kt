package ua.com.radiokot.osmanddisplay.features.map.logic

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.mapbox.maps.CameraOptions
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.toSingle
import ua.com.radiokot.osmanddisplay.R
import ua.com.radiokot.osmanddisplay.base.extension.kLogger
import ua.com.radiokot.osmanddisplay.features.map.model.LocationData
import java.io.IOException
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/**
 * Composes map frames based on [FriendlySnapshotter] map snapshots.
 * Draws location marker and bearing, if it is available.
 *
 * If there is something wrong with the snapshot, composes a frame with
 * the error message.
 */
class SnapshotterMapFrameFactory(
    private val snapshotter: FriendlySnapshotter,
    private val locationMarker: Bitmap,
    private val frameWidthPx: Int,
    private val frameHeightPx: Int,
    private val bearingLineColor: Int,
    private val timeFormat: DateFormat,
    private val timeColor: Int,
    private val timeBackgroundColor: Int,
) : MapFrameFactory {
    private val logger = kLogger("SnapshotterMFF")

    private val bearingLinePaint = Paint().apply {
        color = bearingLineColor
        strokeWidth = BEARING_LINE_WIDTH
    }
    private val timePaint = Paint().apply {
        typeface = Typeface.DEFAULT_BOLD
        textSize = TIME_TEXT_SIZE
        style = Paint.Style.FILL
        color = timeColor
    }
    private val timeHeight = timePaint.fontMetrics.bottom - timePaint.fontMetrics.top
    private val timeDescent = timePaint.fontMetrics.descent
    private val timeBackgroundPaint = Paint().apply {
        style = Paint.Style.FILL
        color = timeBackgroundColor
    }

    private data class SnapshotResult(
        val error: Throwable?,
        val snapshot: Bitmap?,
    )

    override fun composeFrame(
        location: LocationData,
        cameraZoom: Double,
        postScale: Double,
    ): Single<Bitmap> {
        return waitForSnapshotterSetup()
            .flatMap {
                getMapSnapshot(location, cameraZoom)
                    .map { snapshot ->
                        SnapshotResult(null, snapshot)
                    }
            }
            .onErrorReturn { error ->
                SnapshotResult(error, null)
            }
            .flatMap { (error, snapshot) ->
                if (error == null && snapshot != null) {
                    composeFrame(
                        snapshot = snapshot,
                        bearing = location.bearing,
                        postScale = postScale,
                    )
                } else {
                    if (error is IOException) {
                        composeErrorFrame(snapshotter.context.getString(R.string.error_failed_to_load_tile))
                    } else {
                        composeErrorFrame(
                            error?.message
                                ?: snapshotter.context.getString(
                                    R.string.template_error_occurred,
                                    error.toString()
                                )
                        )
                    }
                }
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
                .bearing(location.bearing)
                .build()
        )

        return snapshotter.getSnapshotBitmap()
            .doOnError {
                logger.warn(it) { "getMapSnapshot(): snapshotter_not_ready" }
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
            if (bearing != null)
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
        if (bearing != null) {
            canvas.drawLine(
                locationX,
                locationY,
                locationX,
                locationY - BEARING_CIRCLE_RADIUS,
                bearingLinePaint,
            )
        }

        // Draw the time in 12h format, to be shorter.
        val time = timeFormat.format(Date())
        val timeWidth = timePaint.measureText(time)
        val timeBackgroundRect =
            Rect(
                scaledFrame.width - timeWidth.toInt(),
                scaledFrame.height - timeHeight.toInt(),
                scaledFrame.width,
                scaledFrame.height
            )
        canvas.drawRect(timeBackgroundRect, timeBackgroundPaint)
        canvas.drawText(
            time,
            timeBackgroundRect.left.toFloat(),
            timeBackgroundRect.bottom - timeDescent,
            timePaint
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

    private fun composeErrorFrame(message: String): Single<Bitmap> = {
        val bitmap =
            Bitmap.createBitmap(frameWidthPx, frameHeightPx, Bitmap.Config.ARGB_8888, false)
        val canvas = Canvas(bitmap)

        val textPaint = TextPaint(Paint()).apply {
            textSize = 24f
            color = Color.WHITE
        }

        val offsetTop = 25f
        val offsetHorizontal = 25f

        val textLayout =
            StaticLayout.Builder.obtain(
                message,
                0,
                message.length,
                textPaint,
                frameWidthPx - 2 * offsetHorizontal.toInt()
            )
                .setMaxLines(3)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .build()

        canvas.translate(offsetHorizontal, offsetTop)
        textLayout.draw(canvas)

        bitmap
    }.toSingle()

    override fun destroy() {
        snapshotter.cancel()
        snapshotter.destroy()
    }

    private companion object {
        private const val BEARING_CIRCLE_RADIUS = 10f
        private const val BEARING_LINE_WIDTH = 5f
        private const val TIME_TEXT_SIZE = 21f
    }
}
