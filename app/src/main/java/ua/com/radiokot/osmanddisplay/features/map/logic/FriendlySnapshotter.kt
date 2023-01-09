package ua.com.radiokot.osmanddisplay.features.map.logic

import android.content.Context
import com.mapbox.maps.*
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible

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

    private val styleLoadedListeners = mutableListOf<(Style) -> Any>()

    /**
     * Shows whether the style is loaded,
     * which is false until its JSON is set or downloaded from the URL.
     */
    val isStyleLoaded: Boolean
        get() = style.isStyleLoaded

    val style = createStyle(this, context)

    init {
        subscribe({ event ->
            when (event.type) {
                MapEvents.STYLE_LOADED -> {
                    styleLoadedListeners.toList().forEach { it(style) }
                }
                MapEvents.MAP_LOADED -> {
                    isMapEverLoaded = true
                }
            }
        }, listOf(MapEvents.STYLE_LOADED, MapEvents.MAP_LOADED))
    }

    /**
     * Sets up a callback for [MapEvents.MAP_LOADED] event.
     */
    fun onMapLoaded(listener: (() -> Any)?) {
        mapLoadedListener = listener
    }

    /**
     * Adds a callback for [MapEvents.STYLE_LOADED] event.
     *
     * @see removeStyleLoadedListener
     */
    fun addStyleLoadedListener(listener: (Style) -> Any) {
        styleLoadedListeners.add(listener)
    }

    /**
     * Adds a callback for [MapEvents.STYLE_LOADED] event
     * and removes it once it is called for the first time.
     */
    fun addStyleLoadedListenerOnce(listener: (Style) -> Any) {
        addStyleLoadedListener { style ->
            removeStyleLoadedListener(listener)
            listener.invoke(style)
        }
    }

    fun removeStyleLoadedListener(listener: (Style) -> Any) {
        styleLoadedListeners.remove(listener)
    }

    private companion object {
        private fun createStyle(
            snapshotter: Snapshotter,
            context: Context,
        ): Style {
            val coreSnapshotter = Snapshotter::class.declaredMemberProperties
                .first { it.name == "coreSnapshotter" }
                .run {
                    isAccessible = true
                    get(snapshotter).also { isAccessible = false }
                }

            val pixelRatio = context.resources.displayMetrics.density

            return Style::class.constructors.first().call(coreSnapshotter, pixelRatio)
        }
    }
}