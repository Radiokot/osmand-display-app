package ua.com.radiokot.osmanddisplay.features.map.view

import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.view.drawToBitmap
import com.google.android.material.elevation.SurfaceColors
import com.mapbox.maps.MapView
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.plugin.attribution.attribution
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.logo.logo
import com.mapbox.maps.plugin.scalebar.scalebar
import kotlinx.android.synthetic.main.activity_map.*
import org.koin.android.ext.android.inject
import ua.com.radiokot.osmanddisplay.R
import ua.com.radiokot.osmanddisplay.base.view.ToastManager

class MapActivity : AppCompatActivity() {
    private val toastManager: ToastManager by inject()

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
        map_view.snapshot { bitmap ->
            runOnUiThread {
                if (bitmap != null) {
                    bitmap_image_view.setImageBitmap(bitmap)
                    toastManager.short("Bitmap captured: ${bitmap.width}x${bitmap.height}")
                } else {
                    toastManager.short("The map is not yet ready")
                }
            }
        }
    }
}