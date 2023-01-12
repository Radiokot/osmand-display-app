package ua.com.radiokot.osmanddisplay.base.extension

import android.location.Location
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private const val EQUATORIAL_RADIUS = 6378137.0

/**
 * @return Lat-lng of the destination point if traveling
 * from this location for [travelTimeS] with the constant speed.
 *
 * @see Location.getSpeed
 */
fun Location.getDestination(travelTimeS: Int): Pair<Double, Double> {
    val theta = Math.toRadians(bearing.toDouble())
    val travelDistanceM = speed * travelTimeS

    // Angular distance in radians.
    val delta = travelDistanceM / EQUATORIAL_RADIUS

    val phi1 = Math.toRadians(this.latitude)
    val lambda1 = Math.toRadians(this.longitude)

    val phi2 = asin(
        sin(phi1) * cos(delta)
                + cos(phi1) * sin(delta) * cos(theta)
    )
    val lambda2 = lambda1 + atan2(
        sin(theta) * sin(delta) * cos(phi1),
        cos(delta) - sin(phi1) * sin(phi2)
    )

    return Math.toDegrees(phi2) to Math.toDegrees(lambda2)
}