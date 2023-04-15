package ua.com.radiokot.osmanddisplay.di

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import com.mapbox.bindgen.Value
import com.mapbox.common.TileDataDomain
import com.mapbox.common.TileStore
import com.mapbox.common.TileStoreOptions
import com.mapbox.geojson.LineString
import com.mapbox.maps.*
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import ua.com.radiokot.osmanddisplay.BuildConfig
import ua.com.radiokot.osmanddisplay.R
import ua.com.radiokot.osmanddisplay.base.extension.addMultipoint
import ua.com.radiokot.osmanddisplay.base.extension.addTrack
import ua.com.radiokot.osmanddisplay.base.extension.getNumericProperty
import ua.com.radiokot.osmanddisplay.features.map.logic.FriendlySnapshotter
import ua.com.radiokot.osmanddisplay.features.map.logic.MapFrameFactory
import ua.com.radiokot.osmanddisplay.features.map.logic.SnapshotterMapFrameFactory
import ua.com.radiokot.osmanddisplay.features.track.data.model.ImportedTrackRecord
import kotlin.math.ceil
import kotlin.math.floor

enum class InjectedSnapshotter {
    TRACK_THUMBNAIL,
    MAP_BROADCASTING,
}

val mapModules: List<Module> = listOf(
    // Offline
    module {
        single {
            TileStore.create().apply {
                setOption(
                    TileStoreOptions.MAPBOX_ACCESS_TOKEN,
                    TileDataDomain.MAPS,
                    Value(BuildConfig.MAPBOX_PUBLIC_TOKEN)
                )
            }
        }

        single {
            OfflineManager(
                ResourceOptions.Builder()
                    .tileStore(get())
                    .accessToken(BuildConfig.MAPBOX_PUBLIC_TOKEN)
                    .build()
            )
        }

        single {
            val mapCameraZoom = checkNotNull(getNumericProperty<Double>("mapCameraZoom"))

            get<OfflineManager>()
                .createTilesetDescriptor(
                    TilesetDescriptorOptions.Builder()
                        .styleURI(getProperty("mapStyleUri"))
                        .stylePackOptions(
                            StylePackLoadOptions.Builder()
                                .glyphsRasterizationMode(GlyphsRasterizationMode.ALL_GLYPHS_RASTERIZED_LOCALLY)
                                .build()
                        )
                        .minZoom(floor(mapCameraZoom).toInt().toByte())
                        .maxZoom(ceil(mapCameraZoom).toInt().toByte())
                        .build()
                )
        }

        single<ResourceOptions> {
            ResourceOptions.Builder()
                .tileStore(get())
                .tileStoreUsageMode(TileStoreUsageMode.READ_ONLY)
                .accessToken(BuildConfig.MAPBOX_PUBLIC_TOKEN)
                .build()
        }
    },

    // Snapshotter
    module {
        // Snapshotter for map broadcasting
        factory(named(InjectedSnapshotter.MAP_BROADCASTING)) { (
                                                                   widthPx: Int, heightPx: Int,
                                                                   trackGeoJson: String?,
                                                                   poiGeoJson: String?,
                                                               ) ->
            val options = MapSnapshotOptions.Builder()
                .size(Size(widthPx.toFloat(), heightPx.toFloat()))
                .resourceOptions(get())
                .build()

            val overlayOptions = SnapshotOverlayOptions(
                showLogo = false,
                showAttributes = false
            )

            FriendlySnapshotter(get(), options, overlayOptions).apply {
                setStyleUri(getProperty("mapStyleUri"))

                if (trackGeoJson != null || poiGeoJson != null) {
                    addStyleLoadedListenerOnce {
                        // 😎
                        with(style) {
                            if (trackGeoJson != null) {
                                addTrack(
                                    id = "my-track",
                                    geoJsonData = trackGeoJson,
                                    directionMarker = BitmapFactory.decodeResource(
                                        androidContext().resources,
                                        R.drawable.arrow
                                    )
                                )
                            }

                            if (poiGeoJson != null) {
                                addMultipoint(
                                    id = "my-poi",
                                    geoJsonData = poiGeoJson,
                                    marker = BitmapFactory.decodeResource(
                                        androidContext().resources,
                                        R.drawable.marker
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } bind Snapshotter::class

        // Snapshotter for track thumbnails
        factory(named(InjectedSnapshotter.TRACK_THUMBNAIL)) { (track: LineString, poiGeoJson: String) ->
            val width = 350f
            val options = MapSnapshotOptions.Builder()
                .size(Size(width, width * 0.75f))
                .resourceOptions(get())
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
                            track,
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

                        addMultipoint(
                            id = "my-poi",
                            geoJsonData = poiGeoJson,
                            marker = BitmapFactory.decodeResource(
                                androidContext().resources,
                                R.drawable.red_star
                            )
                        )
                    }
                }
            }
        } bind Snapshotter::class
    },

    // Map frame factory
    module {
        factory<MapFrameFactory> { (track: ImportedTrackRecord?) ->
            val snapshotSizePx = 450
            val frameSizePx = 200

            SnapshotterMapFrameFactory(
                snapshotter = get(named(InjectedSnapshotter.MAP_BROADCASTING)) {
                    parametersOf(
                        snapshotSizePx,
                        snapshotSizePx,
                        track?.readTrackGeoJson(),
                        track?.readPoiGeoJson(),
                    )
                },
                locationMarker = BitmapFactory.decodeResource(
                    get<Context>().resources,
                    R.drawable.location
                ),
                frameWidthPx = frameSizePx,
                frameHeightPx = frameSizePx,
            )
        }
    }
)