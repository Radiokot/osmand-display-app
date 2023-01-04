package ua.com.radiokot.osmanddisplay.features.map.view

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.elevation.SurfaceColors
import com.mapbox.geojson.Point
import com.mapbox.maps.*
import com.mapbox.maps.extension.style.image.image
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.symbolLayer
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.style
import com.mapbox.maps.plugin.scalebar.scalebar
import kotlinx.android.synthetic.main.activity_map.*
import org.koin.android.ext.android.inject
import ua.com.radiokot.osmanddisplay.R
import ua.com.radiokot.osmanddisplay.base.view.ToastManager
import java.io.ByteArrayOutputStream
import kotlin.experimental.or

class MapActivity : AppCompatActivity() {
    private val toastManager: ToastManager by inject()

    private val snapshotter: Snapshotter by lazy {
        val options = MapSnapshotOptions.Builder()
            .size(Size(600f, 600f))
            .resourceOptions(
                ResourceOptions.Builder()
                    .accessToken(ua.com.radiokot.osmanddisplay.BuildConfig.MAPBOX_PUBLIC_TOKEN)
                    .build()
            )
            .build()

        val overlayOptions = SnapshotOverlayOptions(
            showLogo = false,
            showAttributes = false
        )

        Snapshotter(this, options, overlayOptions).apply {
            setStyleListener(object : SnapshotStyleListener {
                val markerIconId = "marker"
                val markerLocationId = "marker_location"
                val markerLayerId = "marker_layer"

                override fun onDidFinishLoadingStyle(style: Style) {
                }

                override fun onDidFullyLoadStyle(style: Style) {
                    style.addImage(
                        markerIconId,
                        BitmapFactory.decodeResource(resources, R.drawable.bicycle)
                    )
                    style.addSource(geoJsonSource(markerIconId) {
                        geometry(Point.fromLngLat(CENTER_LNG, CENTER_LAT))
                    })
                    style.addLayer(symbolLayer(markerLayerId, markerLocationId) {
                        iconImage(markerIconId)
                        iconAnchor(IconAnchor.BOTTOM)
                    })
                }
            })

            setStyleUri("mapbox://styles/radiokot/clcezktcw001614o7bunaemim")

            setCamera(
                CameraOptions.Builder()
                    .center(Point.fromLngLat(CENTER_LNG, CENTER_LAT))
                    .zoom(15.5)
                    .build()
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        initColor()
        initMap()
        initButtons()
    }

    private fun initColor() {
        window.statusBarColor = SurfaceColors.SURFACE_2.getColor(this)
    }

    private fun initMap() {
        map_view.apply {
            scalebar.enabled = false

            getMapboxMap().loadStyle(style("mapbox://styles/radiokot/clcezktcw001614o7bunaemim") {
                val markerIconId = "marker"
                val markerLocationId = "marker_location"
                val markerLayerId = "marker_layer"

                +image(markerIconId) {
                    bitmap(BitmapFactory.decodeResource(resources, R.drawable.bicycle))
                }
                +geoJsonSource(markerLocationId) {
                    geometry(Point.fromLngLat(CENTER_LNG, CENTER_LAT))
                }
                +symbolLayer(markerLayerId, markerLocationId) {
                    iconImage(markerIconId)
                    iconAnchor(IconAnchor.BOTTOM)
                }
            })
        }
    }

    private fun initButtons() {
        capture_bitmap_button.setOnClickListener {
            captureAndDisplayBitmap()
        }
    }

    private fun captureAndDisplayBitmap() {
//        snapshotter.start { snapshot ->
//            runOnUiThread {
//                onBitmapCaptured(snapshot?.bitmap())
//            }
//        }
        map_view.snapshot { bitmap ->
            runOnUiThread { onBitmapCaptured(bitmap) }
        }
    }

    private fun onBitmapCaptured(bitmap: Bitmap?) {
        if (bitmap != null) {
            toastManager.short("Bitmap captured: ${bitmap.width}x${bitmap.height}")
            processAndShowBitmap(bitmap)
        } else {
            toastManager.short("The map is not yet ready")
        }
    }

    private fun processAndShowBitmap(bitmap: Bitmap) {
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 200, 200, false)
        bitmap.recycle()
        val bwBitmap =
            Bitmap.createBitmap(scaledBitmap.width, scaledBitmap.height, Bitmap.Config.ARGB_8888)
        val bytesWidth =
            if (scaledBitmap.width % 8 == 0) scaledBitmap.width / 8 else scaledBitmap.width / 8 + 1
        val bytesHeight = scaledBitmap.height
        val output = ByteArray(bytesWidth * bytesHeight)

        (0 until scaledBitmap.width).forEach { i ->
            (0 until scaledBitmap.height).forEach { j ->
                val pixel = scaledBitmap.getPixel(i, j)
                // 8th bit flips at 128, so it is a 50% threshold.
                val bit = (pixel and 0x80) shr 7

                val outputByteIndex = i / 8 + j * bytesWidth
                output[outputByteIndex] = output[outputByteIndex] or ((bit shl (8 - i % 8)).toByte())

                bwBitmap.setPixel(i, j, if (bit == 1) Color.WHITE else Color.BLACK)
            }
        }
        scaledBitmap.recycle()
        bitmap_image_view.setImageBitmap(bwBitmap)
        toastManager.short("BW bitmap bytes: ${output.size}")
    }

    override fun onDestroy() {
        super.onDestroy()
        snapshotter.cancel()
        snapshotter.destroy()
    }

    private companion object {
        private const val CENTER_LAT = 48.4573
        private const val CENTER_LNG = 35.0715
    }
}