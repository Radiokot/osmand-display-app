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
import com.mapbox.geojson.Geometry
import com.mapbox.maps.*
import com.welie.blessed.BluetoothCentralManager
import com.welie.blessed.BluetoothCentralManagerCallback
import mu.KotlinLogging
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
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
import ua.com.radiokot.osmanddisplay.base.util.http.HttpExceptionInterceptor
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
import ua.com.radiokot.osmanddisplay.features.track.brouter.logic.GetTrackFromBRouterWebUseCase
import ua.com.radiokot.osmanddisplay.features.track.data.model.ImportedTrackRecord
import ua.com.radiokot.osmanddisplay.features.track.data.storage.ImportedTracksRepository
import java.io.File
import java.time.Duration
import java.util.*

val commonModules: List<Module> = listOf(
    // JSON
    module {
        single<ObjectMapper> {
            jacksonObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
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

        factory { (url: String) ->
            GetTrackFromBRouterWebUseCase(
                url = url,
                httpClient = get()
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

    // Location
    module {
        factory {
            LocationServices.getFusedLocationProviderClient(androidContext())
        }
    },

    // HTTP
    module {
        fun getLoggingInterceptor(): HttpLoggingInterceptor {
            val logger = KotlinLogging.logger("HTTP")
            return HttpLoggingInterceptor(logger::info).apply {
                level =
                    if (logger.isDebugEnabled)
                        HttpLoggingInterceptor.Level.BODY
                    else
                        HttpLoggingInterceptor.Level.BASIC
            }
        }

        fun getDefaultBuilder(): OkHttpClient.Builder {
            return OkHttpClient.Builder()
                .callTimeout(Duration.ofSeconds(30))
                .addInterceptor(HttpExceptionInterceptor())
        }

        single {
            getDefaultBuilder()
                .addInterceptor(getLoggingInterceptor())
                .build()
        }
    }
)