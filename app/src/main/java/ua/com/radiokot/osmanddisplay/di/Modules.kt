package ua.com.radiokot.osmanddisplay.di

import android.app.Activity
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.welie.blessed.BluetoothCentralManager
import com.welie.blessed.BluetoothCentralManagerCallback
import org.koin.core.module.Module
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import ua.com.radiokot.osmanddisplay.base.data.storage.ObjectPersistence
import ua.com.radiokot.osmanddisplay.base.data.storage.SharedPreferencesObjectPersistence
import ua.com.radiokot.osmanddisplay.base.view.ToastManager
import ua.com.radiokot.osmanddisplay.features.broadcasting.logic.BleDisplayCommandSender
import ua.com.radiokot.osmanddisplay.features.broadcasting.logic.DisplayCommandSender
import ua.com.radiokot.osmanddisplay.features.broadcasting.logic.OsmAndAidlHelper
import ua.com.radiokot.osmanddisplay.features.broadcasting.logic.OsmAndServiceConnectionListener
import ua.com.radiokot.osmanddisplay.features.main.data.model.SelectedBleDevice
import ua.com.radiokot.osmanddisplay.features.main.logic.ScanAndSelectBleDeviceUseCase
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
                // TODO: Make a property
                filterServiceUuid = UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB")
            )
        }
    },

    // OsmAnd
    module {
        factory { (connectionListener: OsmAndServiceConnectionListener) ->
            OsmAndAidlHelper(
                "net.osmand",
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
                // TODO: Make a property
                serviceUuid = UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB"),
                // TODO: Make a property
                characteristicUuid = UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB"),
                bluetoothCentralManager = get()
            )
        }
    },
)