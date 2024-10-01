package ua.com.radiokot.osmanddisplay.di

import android.app.Activity
import android.companion.CompanionDeviceManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.ctc.wstx.stax.WstxInputFactory
import com.ctc.wstx.stax.WstxOutputFactory
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.xml.XmlFactory
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.android.gms.location.LocationServices
import com.welie.blessed.BluetoothCentralManager
import com.welie.blessed.BluetoothCentralManagerCallback
import mu.KotlinLogging
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import ua.com.radiokot.osmanddisplay.base.util.http.HttpExceptionInterceptor
import ua.com.radiokot.osmanddisplay.base.view.ToastManager
import ua.com.radiokot.osmanddisplay.features.broadcasting.logic.BleDisplayCommandSender
import ua.com.radiokot.osmanddisplay.features.broadcasting.logic.DisplayCommandSender
import ua.com.radiokot.osmanddisplay.features.broadcasting.logic.OsmAndAidlHelper
import ua.com.radiokot.osmanddisplay.features.broadcasting.logic.OsmAndServiceConnectionListener
import java.time.Duration
import java.util.UUID

val commonModules: List<Module> = listOf(
    // JSON
    module {
        single<ObjectMapper> {
            jacksonObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        }
    },

    // XML
    module {
        single<XmlMapper> {
            XmlMapper.builder(
                XmlFactory.builder()
                    .xmlInputFactory(WstxInputFactory())
                    .xmlOutputFactory(WstxOutputFactory())
                    .build()
            ).build()
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
