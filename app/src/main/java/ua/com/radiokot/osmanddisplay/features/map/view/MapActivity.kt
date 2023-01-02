package ua.com.radiokot.osmanddisplay.features.map.view

import android.graphics.Bitmap
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.elevation.SurfaceColors
import com.mapbox.geojson.Point
import com.mapbox.maps.*
import com.mapbox.maps.plugin.scalebar.scalebar
import kotlinx.android.synthetic.main.activity_map.*
import org.koin.android.ext.android.inject
import ua.com.radiokot.osmanddisplay.R
import ua.com.radiokot.osmanddisplay.base.view.ToastManager

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
            setStyleUri("mapbox://styles/radiokot/clcezktcw001614o7bunaemim")
            setCamera(
                CameraOptions.Builder()
                    .center(Point.fromLngLat(35.0715, 48.4573))
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

            getMapboxMap().loadStyleUri("mapbox://styles/radiokot/clcezktcw001614o7bunaemim")
        }
    }

    private fun initButtons() {
        capture_bitmap_button.setOnClickListener {
            captureAndDisplayBitmap()
        }
    }

    private fun captureAndDisplayBitmap() {
        snapshotter.start { snapshot ->
            runOnUiThread {
                onBitmapCaptured(snapshot?.bitmap())
            }
        }
    }

    private fun onBitmapCaptured(bitmap: Bitmap?) {
        if (bitmap != null) {
            bitmap_image_view.setImageBitmap(bitmap)
            toastManager.short("Bitmap captured: ${bitmap.width}x${bitmap.height}")
        } else {
            toastManager.short("The map is not yet ready")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        snapshotter.cancel()
        snapshotter.destroy()
    }
}