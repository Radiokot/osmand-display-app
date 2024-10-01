package ua.com.radiokot.osmanddisplay.di

import android.app.Activity
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import ua.com.radiokot.osmanddisplay.features.main.logic.ScanAndSelectBleDeviceUseCase
import ua.com.radiokot.osmanddisplay.features.track.brouter.logic.GetTrackFromBRouterWebUseCase
import ua.com.radiokot.osmanddisplay.features.track.logic.ClearImportedTracksUseCase
import ua.com.radiokot.osmanddisplay.features.track.logic.ImportTrackUseCase
import ua.com.radiokot.osmanddisplay.features.track.logic.ReadGeoJsonFileUseCase
import ua.com.radiokot.osmanddisplay.features.track.logic.ReadGpxFileUseCase
import java.util.UUID

val useCaseModules: List<Module> = listOf(
    // BLE device
    module {
        factory { (activity: Activity) ->
            ScanAndSelectBleDeviceUseCase(
                // Must be an activity, not an application context.
                companionDeviceManager = get { parametersOf(activity) },
                filterServiceUuid = UUID.fromString(getProperty("displayServiceUuid")),
            )
        }

    },

    // Tracks
    module {
        factory { (url: String) ->
            GetTrackFromBRouterWebUseCase(
                url = url,
                httpClient = get(),
            )
        }

        single {
            ImportTrackUseCase.Factory { name, geometry, poi, thumbnail, onlinePreviewUrl ->
                ImportTrackUseCase(
                    name = name,
                    geometry = geometry,
                    poi = poi,
                    thumbnail = thumbnail,
                    onlinePreviewUrl = onlinePreviewUrl,
                    importedTracksRepository = get(),
                    tileStore = get(),
                    tilesetDescriptor = get(),
                )
            }
        }

        factory {
            ClearImportedTracksUseCase(
                importedTracksRepository = get(),
                tileStore = get(),
            )
        }

        singleOf(::ReadGeoJsonFileUseCase)
        singleOf(::ReadGpxFileUseCase)
    },
)
