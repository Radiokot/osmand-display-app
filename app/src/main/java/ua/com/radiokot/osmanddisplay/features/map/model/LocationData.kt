package ua.com.radiokot.osmanddisplay.features.map.model

import android.location.Location
import com.mapbox.geojson.Point

data class LocationData(
    val lng: Double,
    val lat: Double,
    val bearing: Float,
) {
    constructor(location: Location) : this(
        lng = location.longitude,
        lat = location.latitude,
        bearing = location.bearing,
    )

    fun toPoint() = Point.fromLngLat(lng, lat)
}