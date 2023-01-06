package ua.com.radiokot.osmanddisplay.features.map.logic

import android.graphics.Bitmap
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.rxkotlin.toSingle
import io.reactivex.schedulers.Schedulers
import ua.com.radiokot.osmanddisplay.features.broadcasting.logic.DisplayCommandSender
import ua.com.radiokot.osmanddisplay.features.broadcasting.model.DisplayCommand
import java.util.concurrent.TimeUnit
import kotlin.experimental.or

/**
 * Encodes and sends the given grayscale frame to the display.
 *
 * @param frame grayscale bitmap of the required size. Recycle manually.
 */
class SendFrameUseCase(
    private val frame: Bitmap,
    private val commandSender: DisplayCommandSender,
) {
    private lateinit var encodedFrame: ByteArray

    fun perform(): Completable {
        return sendFramePrepare()
            .delay(400, TimeUnit.MILLISECONDS, Schedulers.io())
            .flatMap {
                getEncodedFrame()
            }
            .doOnSuccess {
                encodedFrame = it
            }
            .flatMap {
                sendFrame()
            }
            .flatMap {
                sendFrameShow()
            }
            .ignoreElement()
    }

    private fun sendFramePrepare(): Single<Boolean> =
        commandSender
            .send(DisplayCommand.FramePrepare)
            .toSingleDefault(true)

    private fun getEncodedFrame(): Single<ByteArray> = {
        val bytesWidth =
            if (frame.width % 8 == 0) frame.width / 8 else frame.width / 8 + 1
        val bytesHeight = frame.height
        val output = ByteArray(bytesWidth * bytesHeight)

        (0 until frame.width).forEach { x ->
            (0 until frame.height).forEach { y ->
                // In a grayscale bitmap all RGB bytes are equal.
                // 8th bit of blue flips at 128, so it is a 50% white threshold.
                // If the pixel is white, the value is 0b10000000
                val unshiftedBit = frame.getPixel(x, y) and 0x80

                // Set the corresponding output bit by shifting
                // the unshifted bit to the right according to the
                // pixel X coordinate.
                val outputByteIndex = x / 8 + y * bytesWidth
                output[outputByteIndex] =
                    output[outputByteIndex] or (unshiftedBit shr x % 8).toByte()
            }
        }

        output
    }.toSingle()

    private fun sendFrame(): Single<Boolean> =
        Completable.concat(
            encodedFrame
                .asIterable()
                .chunked(32)
                .map { dataChunk ->
                    commandSender.send(DisplayCommand.FrameData(dataChunk.toByteArray()))
                }
        )
            .toSingleDefault(true)

    private fun sendFrameShow(): Single<Boolean> =
        commandSender
            .send(DisplayCommand.FrameShow)
            .toSingleDefault(true)
}