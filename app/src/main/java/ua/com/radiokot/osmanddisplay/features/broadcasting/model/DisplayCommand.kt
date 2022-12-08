package ua.com.radiokot.osmanddisplay.features.broadcasting.model

import java.nio.ByteBuffer
import java.nio.ByteOrder

sealed class DisplayCommand(
    val code: Byte,
) {
    abstract fun toByteArray(): ByteArray

    data class ShowDirection(
        val turnType: Int,
        val distanceM: Int,
    ) : DisplayCommand(CODE) {

        constructor(direction: NavigationDirection) : this(
            turnType = direction.turnType,
            distanceM = direction.distanceM,
        )

        override fun toByteArray(): ByteArray =
            ByteBuffer
                .allocate(6)
                .put(code)
                .put(turnType.toByte())
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(distanceM)
                .array()

        companion object {
            const val CODE: Byte = 0x10
        }
    }
}