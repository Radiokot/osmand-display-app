package ua.com.radiokot.osmanddisplay.features.map.view

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.elevation.SurfaceColors
import com.mapbox.geojson.Point
import com.mapbox.maps.*
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.generated.symbolLayer
import com.mapbox.maps.extension.style.layers.properties.generated.IconRotationAlignment
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.layers.properties.generated.SymbolPlacement
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.style
import com.mapbox.maps.plugin.scalebar.scalebar
import kotlinx.android.synthetic.main.activity_map.*
import mu.KotlinLogging
import org.koin.android.ext.android.inject
import ua.com.radiokot.osmanddisplay.R
import ua.com.radiokot.osmanddisplay.base.view.ToastManager
import kotlin.experimental.or

class MapActivity : AppCompatActivity() {
    private val toastManager: ToastManager by inject()

    private val logger = KotlinLogging.logger("MapActivity@${hashCode()}")

    private val snapshotter: Snapshotter by lazy {
        val options = MapSnapshotOptions.Builder()
            .size(Size(200f, 200f))
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
                override fun onDidFinishLoadingStyle(style: Style) {
                }

                override fun onDidFullyLoadStyle(style: Style) {
                    style.addImage(
                        "arrow-my",
                        BitmapFactory.decodeResource(resources, R.drawable.arrow)
                    )
                    style.addImage(
                        "me-my",
                        BitmapFactory.decodeResource(resources, R.drawable.me)
                    )
                    style.addSource(geoJsonSource("geojson-my") {
                        data(
                            """
                            {
                                "type": "FeatureCollection",
                                "features": [
                                    {
                                        "type": "Feature",
                                        "properties": {
                                            "name": "На тот берег",
                                            "type": "Велосипед",
                                            "links": [
                                                {
                                                    "href": "https://www.strava.com/routes/3044739247970226322"
                                                }
                                            ]
                                        },
                                        "geometry": {
                                            "type": "LineString",
                                            "coordinates": [
                                                [
                                                    35.072030000000005,
                                                    48.45664000000001,
                                                    102.83
                                                ],
                                                [
                                                    35.07115,
                                                    48.4566,
                                                    106.59
                                                ],
                                                [
                                                    35.07109000252696,
                                                    48.457312500038434,
                                                    106.50000000000001
                                                ],
                                                [
                                                    35.07103000336981,
                                                    48.45802500004566,
                                                    105.98
                                                ],
                                                [
                        35.07097000252846,
                        48.45873750002172,
                        105.23
                    ],
                    [
                        35.070910000000005,
                        48.459450000000004,
                        104.99000000000001
                    ],
                    [
                        35.070910000000005,
                        48.459410000000005,
                        104.99000000000001
                    ],
                    [
                        35.07085,
                        48.459610000000005,
                        104.94
                    ],
                    [
                        35.07079,
                        48.459680000000006,
                        104.85000000000001
                    ]
                                            ]
                                        }
                                    }
                                ]
                            }
                        """.trimIndent()
                        )
                    })
                    style.addLayer(lineLayer("geojson-line-my", "geojson-my") {
                        lineCap(LineCap.ROUND)
                        lineJoin(LineJoin.ROUND)
                        lineWidth(18.0)
                        lineColor("#FFF")
                    })
                    style.addLayer(symbolLayer("geojson-line-directions-my", "geojson-my") {
                        symbolPlacement(SymbolPlacement.LINE)
                        symbolSpacing(25.0)
                        iconImage("arrow-my")
                        iconAllowOverlap(true)
                        iconIgnorePlacement(true)
                        iconRotate(90.0)
                        iconRotationAlignment(IconRotationAlignment.MAP)
                    })
                    style.addLayer(symbolLayer("me-icon-my", "geojson-my") {
                        symbolPlacement(SymbolPlacement.POINT)
                        iconImage("me-my")
                        iconAllowOverlap(true)
                        iconIgnorePlacement(true)
                    })
                }
            })

            setStyleUri("mapbox://styles/radiokot/clcezktcw001614o7bunaemim")

            setCamera(
                CameraOptions.Builder()
                    .center(Point.fromLngLat(CENTER_LNG, CENTER_LAT))
                    .zoom(14.5)
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

            getMapboxMap().loadStyle(style("mapbox://styles/radiokot/clcezktcw001614o7bunaemim") {})
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
                output[outputByteIndex] =
                    output[outputByteIndex] or ((bit shl (8 - i % 8)).toByte())

                bwBitmap.setPixel(i, j, if (bit == 1) Color.WHITE else Color.BLACK)
            }
        }
        bitmap_image_view.setImageBitmap(bwBitmap)
        scaledBitmap.recycle()
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