package ua.com.radiokot.osmanddisplay.features.map.logic

import android.content.Context
import android.graphics.Bitmap
import com.mapbox.maps.*
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
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

    val coreSnapshotter =
        Snapshotter::class.declaredMemberProperties
            .first { it.name == "coreSnapshotter" }
            .run {
                isAccessible = true
                get(this@FriendlySnapshotter).also { isAccessible = false }
            } as MapSnapshotterInterface

    val style = createStyle(coreSnapshotter, context)

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

    /**
     * @return Single emitting the snapshot bitmap.
     * Subscribed on the main thread.
     */
    fun getSnapshotBitmap(): Single<Bitmap> = Single.create { emitter ->
        start { snapshot ->
            val bitmap = snapshot?.bitmap()
            if (bitmap == null) {
//                logger.warn { "getMapSnapshot(): snapshotter_not_ready" }
                emitter.tryOnError(IllegalStateException("Snapshotter is not ready"))
            } else {
//                logger.debug { "getMapSnapshot(): got_snapshot" }
                emitter.onSuccess(bitmap)
            }
        }
    }.subscribeOn(AndroidSchedulers.mainThread())

    private companion object {
        private fun createStyle(
            styleManager: StyleManagerInterface,
            context: Context,
        ): Style {
            val pixelRatio = context.resources.displayMetrics.density

            return Style::class.constructors.first().call(styleManager, pixelRatio)
        }
    }
}