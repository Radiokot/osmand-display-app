package ua.com.radiokot.osmanddisplay.features.map.logic

import android.graphics.Bitmap
import io.reactivex.Single
import ua.com.radiokot.osmanddisplay.features.map.model.LocationData

interface MapFrameFactory {
    /**
     * @param location location to be shown on the frame
     * @param cameraZoom map camera zoom that controls map details level
     * @param postScale scale of the composed frame, once the map snapshot is already taken.
     * Value < 1 means a bigger area is fit into the frame, but all the objects are smaller
     */
    fun composeFrame(
        location: LocationData,
        cameraZoom: Double,
        postScale: Double,
    ): Single<Bitmap>

    fun destroy()
}