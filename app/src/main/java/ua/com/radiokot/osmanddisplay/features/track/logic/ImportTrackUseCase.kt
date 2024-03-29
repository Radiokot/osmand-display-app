package ua.com.radiokot.osmanddisplay.features.track.logic

import android.graphics.Bitmap
import com.mapbox.common.NetworkRestriction
import com.mapbox.common.TileRegionLoadOptions
import com.mapbox.common.TileStore
import com.mapbox.common.TilesetDescriptor
import com.mapbox.geojson.LineString
import com.mapbox.geojson.MultiPoint
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import ua.com.radiokot.osmanddisplay.base.extension.kLogger
import ua.com.radiokot.osmanddisplay.features.track.data.model.ImportedTrackRecord
import ua.com.radiokot.osmanddisplay.features.track.data.storage.ImportedTracksRepository

/**
 * Imports a track defined by [name], [geometry] and [thumbnail] to the
 * [importedTracksRepository] and downloads map tiles for it.
 *
 * @param onlinePreviewUrl optional URL of the online track preview
 */
class ImportTrackUseCase(
    private val name: String,
    private val geometry: LineString,
    private val poi: MultiPoint,
    private val thumbnail: Bitmap,
    private val onlinePreviewUrl: String?,
    private val importedTracksRepository: ImportedTracksRepository,
    private val tileStore: TileStore,
    private val tilesetDescriptor: TilesetDescriptor,
) {
    private val logger = kLogger("ImportTrackUC")

    private lateinit var importedTrack: ImportedTrackRecord

    operator fun invoke(): Single<ImportedTrackRecord> {
        return importTrack()
            .doOnSuccess { importedTrack = it }
            .flatMap {
                downloadTiles()
            }
            .map { importedTrack }
    }


    private fun importTrack(): Single<ImportedTrackRecord> {
        return importedTracksRepository.importTrack(
            name = name,
            geometry = geometry,
            poi = poi,
            thumbnail = thumbnail,
            onlinePreviewUrl = onlinePreviewUrl,
        )
    }

    private fun downloadTiles(): Single<Boolean> = Single.create { emitter ->
        val cancellable = tileStore.loadTileRegion(
            importedTrack.id,
            TileRegionLoadOptions.Builder()
                .descriptors(listOf(tilesetDescriptor))
                .geometry(geometry)
                .acceptExpired(false)
                .networkRestriction(NetworkRestriction.NONE)
                .build(),
            { progress ->
                logger.debug {
                    "downloadTiles(): downloading:" +
                            "\nprogress=$progress"
                }
            }
        ) { result ->
            val error = result.error
            if (error != null) {
                emitter.tryOnError(RuntimeException(error.message))
            } else {
                emitter.onSuccess(true)
            }
        }

        emitter.setDisposable(object : Disposable {
            private var isDisposed = false

            override fun dispose() {
                cancellable.cancel()
                isDisposed = true
            }

            override fun isDisposed(): Boolean = isDisposed
        })
    }

    fun interface Factory {
        fun get(
            name: String,
            geometry: LineString,
            poi: MultiPoint,
            thumbnail: Bitmap,
            onlinePreviewUrl: String?
        ): ImportTrackUseCase
    }
}
