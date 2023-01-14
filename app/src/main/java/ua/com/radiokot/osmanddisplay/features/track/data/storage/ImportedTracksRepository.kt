package ua.com.radiokot.osmanddisplay.features.track.data.storage

import android.graphics.Bitmap
import com.mapbox.geojson.Feature
import com.mapbox.geojson.Geometry
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.rxkotlin.toSingle
import io.reactivex.schedulers.Schedulers
import mu.KotlinLogging
import ua.com.radiokot.osmanddisplay.base.data.storage.MultipleItemsRepository
import ua.com.radiokot.osmanddisplay.base.data.storage.RepositoryCache
import ua.com.radiokot.osmanddisplay.features.track.data.model.ImportedTrackRecord
import java.io.File
import java.util.*

class ImportedTracksRepository(
    private val directory: File,
    itemsCache: RepositoryCache<ImportedTrackRecord>,
) : MultipleItemsRepository<ImportedTrackRecord>(itemsCache) {
    private val logger = KotlinLogging.logger("ImportedTracksRepo@${hashCode()}")

    override fun getItems(): Single<List<ImportedTrackRecord>> = Single.defer {
        directory
            .listFiles { _, name -> name.endsWith(".geojson") }!!
            .map(ImportedTrackRecord.Companion::fromGeoJsonFile)
            .sortedByDescending(ImportedTrackRecord::importedAt)
            .toSingle()
    }
        .subscribeOn(Schedulers.io())
        .doOnError {
            logger.error(it) { "getItems(): error_occurred" }
        }

    fun importTrack(
        name: String,
        geometry: Geometry,
        thumbnail: Bitmap,
    ): Completable = Completable.defer {
        val id = System.currentTimeMillis()

        val thumbnailFileName = "${id}_thumbnail.jpg"
        val thumbnailImageFile = File(directory.path, thumbnailFileName).apply {
            createNewFile()
            outputStream().use { outputStream ->
                thumbnail.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            }
        }

        val geoJson = Feature.fromGeometry(geometry).apply {
            addNumberProperty("version", 1)
            addStringProperty("name", name)
            addNumberProperty("imported_at", id)
            addStringProperty("thumbnail", thumbnailFileName)
        }

        val geoJsonFile = File(directory.path, "${id}.geojson").apply {
            createNewFile()
            writeText(geoJson.toJson())
        }

        itemsCache.add(
            ImportedTrackRecord(
                name = name,
                importedAt = Date(id),
                thumbnailImageFile = thumbnailImageFile,
                geoJsonFile = geoJsonFile,
            )
        )
        broadcast()

        Completable.complete()
    }
        .subscribeOn(Schedulers.io())
        .doOnSubscribe {
            logger.debug { "importTrack()" }
        }
        .doOnError {
            logger.error(it) {
                "importTrack(): error_occurred"
            }
        }

    fun clear(): Completable = Completable.defer {
        directory.deleteRecursively()
        itemsCache.clear()
        broadcast()

        Completable.complete()
    }
        .subscribeOn(Schedulers.io())
        .doOnSubscribe {
            logger.debug { "clear()" }
        }
        .doOnError {
            logger.error(it) {
                "clear(): error_occurred"
            }
        }
}