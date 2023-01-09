package ua.com.radiokot.osmanddisplay.features.map.logic

import android.content.Context
import com.mapbox.maps.MapEvents
import com.mapbox.maps.MapSnapshotOptions
import com.mapbox.maps.SnapshotOverlayOptions
import com.mapbox.maps.Snapshotter

/**
 * [Snapshotter] that is more friendly to use.
 */
class FriendlySnapshotter(
    context: Context,
    options: MapSnapshotOptions,
    overlayOptions: SnapshotOverlayOptions
) : Snapshotter(context, options, overlayOptions) {
    private var mapLoadedListener: (() -> Any)? = null

    /**
     * Shows whether the map was ever loaded,
     * which is false until the first snapshot is made.
     */
    var isMapEverLoaded: Boolean = false
        private set(value) {
            field = value
            mapLoadedListener?.invoke()
        }

    private var styleLoadedListener: (() -> Any)? = null

    /**
     * Shows whether the style is loaded,
     * which is false until its JSON is set or downloaded from the URL.
     */
    var isStyleLoaded: Boolean = false
        private set(value) {
            field = value
            styleLoadedListener?.invoke()
        }

    init {
        subscribe({ event ->
            when (event.type) {
                MapEvents.STYLE_LOADED -> {
                    isStyleLoaded = true
                }
                MapEvents.MAP_LOADED -> {
                    isMapEverLoaded = true
                }
            }
        }, listOf(MapEvents.STYLE_LOADED, MapEvents.MAP_LOADED))
    }

    /**
     * Set up a callback for [MapEvents.MAP_LOADED] event.
     */
    fun onMapLoaded(listener: (() -> Any)?) {
        mapLoadedListener = listener
    }

    /**
     * Set up a callback for [MapEvents.STYLE_LOADED] event.
     */
    fun onStyleLoaded(listener: (() -> Any)?) {
        styleLoadedListener = listener
    }
}