package ua.com.radiokot.osmanddisplay.di

import android.app.Activity
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.SharedPreferences
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import ua.com.radiokot.osmanddisplay.base.data.storage.ObjectPersistence
import ua.com.radiokot.osmanddisplay.base.data.storage.SharedPreferencesObjectPersistence
import ua.com.radiokot.osmanddisplay.features.main.logic.ScanAndSelectBleDeviceUseCase
import ua.com.radiokot.osmanddisplay.features.main.data.model.SelectedBleDevice
import ua.com.radiokot.osmanddisplay.base.view.ToastManager
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
                companionDeviceManager = activity
                    .getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager,
                filterServiceUuid = UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB")
            )
        }
    },
)