package ua.com.radiokot.osmanddisplay.features.map.model

import android.location.Location
import com.mapbox.geojson.Point
import ua.com.radiokot.osmanddisplay.base.extension.getDestination

data class LocationData(
    val lng: Double,
    val lat: Double,
    val bearing: Double?,
) {
    constructor(location: Location) : this(
        lng = location.longitude,
        lat = location.latitude,
        bearing = location.bearing.takeIf { location.hasBearing() }?.toDouble(),
    )

    fun toPoint() = Point.fromLngLat(lng, lat)

    companion object {
        /**
         * @return location of the future destination point
         * if continue traveling from the [currentLocation]
         *
         * @see Location.getDestination
         */
        fun ofDestinationPoint(
            currentLocation: Location,
            travelTimeS: Int,
        ): LocationData {
            val (destinationLat, destinationLng) = currentLocation.getDestination(travelTimeS)

            return LocationData(
                lng = destinationLng,
                lat = destinationLat,
                bearing = currentLocation.bearing
                    .takeIf { currentLocation.hasBearing() }
                    ?.toDouble(),
            )
        }
    }
}