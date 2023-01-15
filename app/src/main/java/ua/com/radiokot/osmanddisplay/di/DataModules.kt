package ua.com.radiokot.osmanddisplay.di

import android.content.Context
import android.content.SharedPreferences
import org.koin.core.module.Module
import org.koin.dsl.module
import ua.com.radiokot.osmanddisplay.base.data.storage.MemoryOnlyRepositoryCache
import ua.com.radiokot.osmanddisplay.base.data.storage.ObjectPersistence
import ua.com.radiokot.osmanddisplay.base.data.storage.SharedPreferencesObjectPersistence
import ua.com.radiokot.osmanddisplay.features.main.data.model.SelectedBleDevice
import ua.com.radiokot.osmanddisplay.features.track.data.storage.ImportedTracksRepository
import java.io.File

val dataModules: List<Module> = listOf(
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