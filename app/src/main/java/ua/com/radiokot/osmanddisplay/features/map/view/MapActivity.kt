package ua.com.radiokot.osmanddisplay.features.map.view

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import com.google.android.material.elevation.SurfaceColors
import com.mapbox.geojson.*
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
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_map.*
import mu.KotlinLogging
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import org.koin.core.parameter.parametersOf
import ua.com.radiokot.osmanddisplay.R
import ua.com.radiokot.osmanddisplay.base.view.ToastManager
import ua.com.radiokot.osmanddisplay.features.broadcasting.logic.DisplayCommandSender
import ua.com.radiokot.osmanddisplay.features.main.view.MainActivity
import ua.com.radiokot.osmanddisplay.features.map.logic.SendFrameUseCase
import kotlin.experimental.or

class MapActivity : AppCompatActivity() {
    private val toastManager: ToastManager by inject()

    private val compositeDisposable = CompositeDisposable()

    private val deviceAddress: String? by lazy {
        intent.getStringExtra(DEVICE_ADDRESS_EXTRA)
    }

    private val commandSender: DisplayCommandSender
        get() = get { parametersOf(deviceAddress) }

    private val logger = KotlinLogging.logger("MapActivity@${hashCode()}")

    private var centerLng = CENTER_LNG
    private var centerLat = CENTER_LAT

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
        capture_and_send_bitmap_button.apply {
            setText(
                if (deviceAddress != null)
                    R.string.capture_and_send_bitmap
                else
                    R.string.capture_bitmap
            )

            setOnClickListener {
                captureAndDisplayBitmap()
            }
        }
    }

    private fun captureAndDisplayBitmap() {
        snapshotter.setCamera(
            CameraOptions.Builder()
                .center(Point.fromLngLat(centerLng, centerLat))
                .zoom(14.5)
                .build()
        )

        snapshotter.start { snapshot ->
            runOnUiThread {
                onBitmapCaptured(snapshot?.bitmap())
            }
        }

        centerLng += 0.0001
        centerLat += 0.0003
    }

    private fun onBitmapCaptured(bitmap: Bitmap?) {
        if (bitmap != null) {
            showAndSendBitmap(bitmap)
        } else {
            toastManager.short("The map is not yet ready")
        }
    }

    private fun showAndSendBitmap(bitmap: Bitmap) {
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 200, 200, false)
        bitmap.recycle()

        bitmap_image_view.drawable.also {
            if (it is BitmapDrawable) {
                it.bitmap.recycle()
            }
        }
        bitmap_image_view.setImageBitmap(scaledBitmap)

        if (deviceAddress != null) {
            sendBitmap(scaledBitmap)
        }
    }

    private fun sendBitmap(bitmap: Bitmap) {
        SendFrameUseCase(
            frame = bitmap,
            commandSender = commandSender
        )
            .perform()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnTerminate {
                bitmap.recycle()
            }
            .subscribeBy(
                onComplete = { toastManager.short("Complete") },
                onError = { toastManager.short("Error") }
            )
            .addTo(compositeDisposable)
    }

    override fun onDestroy() {
        super.onDestroy()
        snapshotter.cancel()
        snapshotter.destroy()
        compositeDisposable.dispose()
    }

    companion object {
        private const val CENTER_LAT = 48.4573
        private const val CENTER_LNG = 35.0715

        private const val DEVICE_ADDRESS_EXTRA = "device_address"

        fun getBundle(deviceAddress: String?) = Bundle().apply {
            putString(DEVICE_ADDRESS_EXTRA, deviceAddress)
        }
    }
}