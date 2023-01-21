package ua.com.radiokot.osmanddisplay.di

import android.app.Activity
import android.graphics.Bitmap
import com.mapbox.geojson.Geometry
import org.koin.core.module.Module
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import ua.com.radiokot.osmanddisplay.features.main.logic.ScanAndSelectBleDeviceUseCase
import ua.com.radiokot.osmanddisplay.features.track.brouter.logic.GetTrackFromBRouterWebUseCase
import ua.com.radiokot.osmanddisplay.features.track.logic.ClearImportedTracksUseCase
import ua.com.radiokot.osmanddisplay.features.track.logic.ImportTrackUseCase
import java.util.*

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

        factory { (name: String, geometry: Geometry, thumbnail: Bitmap) ->
            ImportTrackUseCase(
                name = name,
                geometry = geometry,
                thumbnail = thumbnail,
                importedTracksRepository = get(),
                tileStore = get(),
                tilesetDescriptor = get(),
            )
        }

        factory {
            ClearImportedTracksUseCase(
                importedTracksRepository = get(),
                tileStore = get(),
            )
        }
    },
)