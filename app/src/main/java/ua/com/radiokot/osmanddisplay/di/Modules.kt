package ua.com.radiokot.osmanddisplay.di

import android.app.Activity
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.android.gms.location.LocationServices
import com.mapbox.geojson.GeoJson
import com.mapbox.geojson.Geometry
import com.mapbox.maps.*
import com.welie.blessed.BluetoothCentralManager
import com.welie.blessed.BluetoothCentralManagerCallback
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import ua.com.radiokot.osmanddisplay.R
import ua.com.radiokot.osmanddisplay.base.data.storage.MemoryOnlyRepositoryCache
import ua.com.radiokot.osmanddisplay.base.data.storage.ObjectPersistence
import ua.com.radiokot.osmanddisplay.base.data.storage.SharedPreferencesObjectPersistence
import ua.com.radiokot.osmanddisplay.base.extension.addTrack
import ua.com.radiokot.osmanddisplay.base.view.ToastManager
import ua.com.radiokot.osmanddisplay.features.broadcasting.logic.BleDisplayCommandSender
import ua.com.radiokot.osmanddisplay.features.broadcasting.logic.DisplayCommandSender
import ua.com.radiokot.osmanddisplay.features.broadcasting.logic.OsmAndAidlHelper
import ua.com.radiokot.osmanddisplay.features.broadcasting.logic.OsmAndServiceConnectionListener
import ua.com.radiokot.osmanddisplay.features.main.data.model.SelectedBleDevice
import ua.com.radiokot.osmanddisplay.features.main.logic.ScanAndSelectBleDeviceUseCase
import ua.com.radiokot.osmanddisplay.features.map.logic.FriendlySnapshotter
import ua.com.radiokot.osmanddisplay.features.map.logic.MapFrameFactory
import ua.com.radiokot.osmanddisplay.features.map.logic.SnapshotterMapFrameFactory
import ua.com.radiokot.osmanddisplay.features.track.data.storage.ImportedTracksRepository
import java.io.File
import java.io.InputStreamReader
import java.util.*

val injectionModules: List<Module> = listOf(
    // JSON
    module {
        single<ObjectMapper> {
            jacksonObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        }
    },

    // Storage
    module {
        single<SharedPreferences> {
            get<Context>().getSharedPreferences("default", Context.MODE_PRIVATE)
        }

        single<ObjectPersistence<SelectedBleDevice>>() {
            SharedPreferencesObjectPersistence.forType(
                key = "selected_ble_device",
                objectMapper = get(),
                preferences = get()
            )
        }
    },

    // Toast
    module {
        factory {
            ToastManager(
                context = get()
            )
        }
    },

    // Use-cases
    module {
        factory { (activity: Activity) ->
            ScanAndSelectBleDeviceUseCase(
                // Must be an activity, not an application context.
                companionDeviceManager = get { parametersOf(activity) },
                filterServiceUuid = UUID.fromString(getProperty("displayServiceUuid"))
            )
        }
    },

    // OsmAnd
    module {
        factory { (connectionListener: OsmAndServiceConnectionListener) ->
            OsmAndAidlHelper(
                getProperty("osmAndPackage"),
                get(),
                connectionListener,
            )
        }
    },

    // Bluetooth
    module {
        single {
            BluetoothCentralManager(
                get(),
                object : BluetoothCentralManagerCallback() {},
                Handler(Looper.getMainLooper())
            )
        }

        factory { (activity: Activity) ->
            activity.getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager
        }
    },

    // Commands
    module {
        factory<DisplayCommandSender> { (deviceAddress: String) ->
            BleDisplayCommandSender(
                deviceAddress = deviceAddress,
                serviceUuid = UUID.fromString(getProperty("displayServiceUuid")),
                characteristicUuid = UUID.fromString(getProperty("displayCharacteristicUuid")),
                keepAlive = true,
                context = get()
            )
        }
    },

    // Map
    module {
        // Snapshotter for map broadcasting
        factory(named(InjectedSnapshotter.MAP_BROADCASTING)) { (widthDp: Float, heightDp: Float, track: String?) ->
            val options = MapSnapshotOptions.Builder()
                .size(Size(widthDp, heightDp))
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

            FriendlySnapshotter(get(), options, overlayOptions).apply {
                setStyleUri(getProperty("mapStyleUri"))

                if (track != null) {
                    addStyleLoadedListenerOnce {
                        style.apply {
                            val trackInputStream =
                                InputStreamReader(androidContext().assets.open(track))

                            addTrack(
                                id = "my-track",
                                geoJsonData = trackInputStream.use(InputStreamReader::readText),
                                directionMarker = BitmapFactory.decodeResource(
                                    androidContext().resources,
                                    R.drawable.arrow
                                )
                            )
                        }
                    }
                }
            }
        } bind Snapshotter::class

        // Snapshotter for track thumbnails
        factory(named(InjectedSnapshotter.TRACK_THUMBNAIL)) { (track: GeoJson, geometry: Geometry) ->
            val width = 350f
            val options = MapSnapshotOptions.Builder()
                .size(Size(width, width * 0.75f))
                .resourceOptions(
                    ResourceOptions.Builder()
                        .accessToken(ua.com.radiokot.osmanddisplay.BuildConfig.MAPBOX_PUBLIC_TOKEN)
                        .build()
                )
                .build()

            val overlayOptions = SnapshotOverlayOptions(
                showLogo = true,
                showAttributes = false
            )

            FriendlySnapshotter(get(), options, overlayOptions).apply {
                setStyleUri(getProperty("trackThumbnailMapStyleUri"))

                addStyleLoadedListenerOnce {
                    setCamera(
                        coreSnapshotter.cameraForGeometry(
                            geometry,
                            EdgeInsets(20.0, 20.0, 20.0, 20.0),
                            0.0,
                            0.0
                        )
                    )

                    style.apply {
                        addTrack(
                            id = "my-track",
                            geoJsonData = track.toJson(),
                            color = Color.RED,
                            width = width / 120.0,
                        )
                    }
                }
            }
        } bind Snapshotter::class

        // Map frame factory
        factory<MapFrameFactory> { (track: String?) ->
            SnapshotterMapFrameFactory(
                snapshotter = get(named(InjectedSnapshotter.MAP_BROADCASTING)) {
                    parametersOf(
                        230f,
                        230f,
                        track
                    )
                },
                locationMarker = BitmapFactory.decodeResource(
                    androidContext().resources,
                    R.drawable.location
                ),
                frameWidthPx = 200,
                frameHeightPx = 200,
            )
        }
    },

    // Location
    module {
        factory {
            LocationServices.getFusedLocationProviderClient(androidContext())
        }
    },

    // Repository
    module {
        single {
            ImportedTracksRepository(
                directory = File(get<Context>().noBackupFilesDir, "tracks/")
                    .apply {
                        mkdir()
                    },
                itemsCache = MemoryOnlyRepositoryCache()
            )
        }
    },
)