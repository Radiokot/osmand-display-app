package ua.com.radiokot.osmanddisplay.features.broadcasting.model

import java.nio.ByteBuffer
import java.nio.ByteOrder

sealed class DisplayCommand(
    val code: Byte,

    /**
     * Whether an acknowledgment from the device
     * is required for the sent command to be considered successful
     */
    val requiresAcq: Boolean = false
) {
    abstract fun toByteArray(): ByteArray

    data class ShowDirection(
        val turnType: Int,
        val distanceM: Int,
    ) : DisplayCommand(0x10) {

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
    }

    object ClearScreen : DisplayCommand(0x20) {
        override fun toByteArray(): ByteArray = byteArrayOf(code)
    }

    object FramePrepare : DisplayCommand(0x30, requiresAcq = true) {
        override fun toByteArray(): ByteArray = byteArrayOf(code)
    }

    class FrameData(val data: ByteArray) : DisplayCommand(0x31) {
        init {
            require(data.size <= MAX_DATA_SIZE) {
                "Data can't be bigger than $MAX_DATA_SIZE bytes"
            }
        }

        override fun toByteArray(): ByteArray =
            ByteBuffer
                .allocate(data.size + 2)
                .put(code)
                .put(data.size.toByte())
                .put(data)
                .array()

        companion object {
            const val MAX_DATA_SIZE = 62
        }
    }

    object FrameShow : DisplayCommand(0x32) {
        override fun toByteArray(): ByteArray = byteArrayOf(code)
    }
}