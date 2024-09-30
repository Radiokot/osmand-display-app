package ua.com.radiokot.osmanddisplay.features.track.logic

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import io.reactivex.Single
import io.reactivex.rxkotlin.toSingle
import io.reactivex.schedulers.Schedulers
import ua.com.radiokot.osmanddisplay.features.track.data.model.GpxData
import ua.com.radiokot.osmanddisplay.features.track.data.model.GpxTrackData
import java.io.InputStream
import java.io.InputStreamReader

class ReadGpxFileUseCase(
    private val xmlMapper: XmlMapper,
) {
    operator fun invoke(
        inputStream: InputStream,
    ): Single<GpxTrackData> = {
        val content = inputStream.use {
            InputStreamReader(it).readText()
        }

        xmlMapper.readValue(
            content,
            GpxData::class.java,
        ).let(::GpxTrackData)
    }.toSingle().subscribeOn(Schedulers.io())
}
