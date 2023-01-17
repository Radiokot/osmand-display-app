package ua.com.radiokot.osmanddisplay.di

import android.graphics.BitmapFactory
import android.graphics.Color
import com.mapbox.geojson.Geometry
import com.mapbox.maps.*
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import ua.com.radiokot.osmanddisplay.BuildConfig
import ua.com.radiokot.osmanddisplay.R
import ua.com.radiokot.osmanddisplay.base.extension.addTrack
import ua.com.radiokot.osmanddisplay.features.map.logic.FriendlySnapshotter
import ua.com.radiokot.osmanddisplay.features.map.logic.MapFrameFactory
import ua.com.radiokot.osmanddisplay.features.map.logic.SnapshotterMapFrameFactory
import ua.com.radiokot.osmanddisplay.features.track.data.model.ImportedTrackRecord

val mapModules: List<Module> = listOf(
    // Snapshotter
    module {
        // Snapshotter for map broadcasting
        factory(named(InjectedSnapshotter.MAP_BROADCASTING)) { (widthPx: Int, heightPx: Int,
                                                                   trackGeoJson: String?) ->
            val options = MapSnapshotOptions.Builder()
                .size(Size(widthPx.toFloat(), heightPx.toFloat()))
                .resourceOptions(
                    ResourceOptions.Builder()
                        .accessToken(BuildConfig.MAPBOX_PUBLIC_TOKEN)
                        .build()
                )
                .build()

            val overlayOptions = SnapshotOverlayOptions(
                showLogo = false,
                showAttributes = false
            )

            FriendlySnapshotter(get(), options, overlayOptions).apply {
                setStyleUri(getProperty("mapStyleUri"))

                if (trackGeoJson != null) {
                    addStyleLoadedListenerOnce {
                        style.apply {
                            addTrack(
                                id = "my-track",
                                geoJsonData = trackGeoJson,
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
        factory(named(InjectedSnapshotter.TRACK_THUMBNAIL)) { (trackGeoJson: String, geometry: Geometry) ->
            val width = 350f
            val options = MapSnapshotOptions.Builder()
                .size(Size(width, width * 0.75f))
                .resourceOptions(
                    ResourceOptions.Builder()
                        .accessToken(BuildConfig.MAPBOX_PUBLIC_TOKEN)
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
                            geoJsonData = trackGeoJson,
                            color = Color.RED,
                            width = width / 120.0,
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
                        track?.geoJsonFile?.readText(Charsets.UTF_8)
                    )
                },
                locationMarker = BitmapFactory.decodeResource(
                    androidContext().resources,
                    R.drawable.location
                ),
                frameWidthPx = frameSizePx,
                frameHeightPx = frameSizePx,
            )
        }
    }
)