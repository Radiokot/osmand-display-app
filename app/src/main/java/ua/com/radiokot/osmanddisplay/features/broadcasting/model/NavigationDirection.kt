package ua.com.radiokot.osmanddisplay.features.broadcasting.model

import net.osmand.aidlapi.navigation.ADirectionInfo

data class NavigationDirection(
    val turnType: Int,
    val distanceM: Int,
) {
    constructor(aDirectionInfo: ADirectionInfo) : this(
        turnType = aDirectionInfo.turnType,
        distanceM = aDirectionInfo.distanceTo,
    )
}