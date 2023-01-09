package ua.com.radiokot.osmanddisplay.features.map.logic

import android.graphics.Bitmap
import io.reactivex.Single
import ua.com.radiokot.osmanddisplay.features.map.model.LocationData

interface MapFrameFactory {
    fun composeFrame(
        location: LocationData,
        zoom: Double,
    ): Single<Bitmap>

    fun destroy()
}