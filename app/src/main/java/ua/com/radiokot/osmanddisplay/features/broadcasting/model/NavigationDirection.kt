package ua.com.radiokot.osmanddisplay.features.broadcasting.model

import net.osmand.aidlapi.navigation.ADirectionInfo
import kotlin.math.roundToInt

data class NavigationDirection(
    val turnType: Int,
    val distanceM: Int,
) {
    constructor(aDirectionInfo: ADirectionInfo) : this(
        turnType = aDirectionInfo.turnType,
        distanceM = aDirectionInfo.distanceTo,
    )

    /**
     * @return whether the [other] direction, which may not be equal to this one,
     * is shown in the same way (due to the distance rounding).
     */
    fun isShownLike(other: NavigationDirection): Boolean {
        val turnTypesEqual = this.turnType == other.turnType
        val distancesEqual = this.distanceM == other.distanceM
        val roundedKmDistancesEqual =
            if (distancesEqual) {
                true
            } else if (this.distanceM > MAX_METERS_DISTANCE && this.distanceM > MAX_METERS_DISTANCE) {
                (this.distanceM / 100.0).roundToInt() == (other.distanceM / 100.0).roundToInt()
            } else {
                false
            }
        return turnTypesEqual && (distancesEqual || roundedKmDistancesEqual)
    }

    private companion object {
        // Max distance the display shows in meters.
        private const val MAX_METERS_DISTANCE = 945
    }
}