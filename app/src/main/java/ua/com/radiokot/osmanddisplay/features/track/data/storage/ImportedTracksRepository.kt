package ua.com.radiokot.osmanddisplay.features.track.data.storage

import android.graphics.Bitmap
import com.mapbox.geojson.GeoJson
import com.mapbox.geojson.LineString
import com.mapbox.geojson.MultiPoint
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.rxkotlin.toCompletable
import io.reactivex.rxkotlin.toSingle
import io.reactivex.schedulers.Schedulers
import ua.com.radiokot.osmanddisplay.base.data.storage.MultipleItemsRepository
import ua.com.radiokot.osmanddisplay.base.data.storage.RepositoryCache
import ua.com.radiokot.osmanddisplay.base.extension.kLogger
import ua.com.radiokot.osmanddisplay.features.track.data.model.ImportedTrackRecord
import java.io.File
import java.util.Date

class ImportedTracksRepository(
    private val directory: File,
    itemsCache: RepositoryCache<ImportedTrackRecord>,
) : MultipleItemsRepository<ImportedTrackRecord>(itemsCache) {
    private val logger = kLogger("ImportedTracksRepo")

    override fun getItems(): Single<List<ImportedTrackRecord>> = {
        directory
            .listFiles { _, name -> name.endsWith(".$GEOJSON_FILE_EXTENSION") }!!
            .mapNotNull { file ->
                try {
                    ImportedTrackRecord.fromGeoJsonFile(file)
                } catch (e: Exception) {
                    logger.warn(e) {
                        "getItems(): record_creation_failed:" +
                                "\nfile=$file"
                    }
                    null
                }
            }
            .sortedByDescending(ImportedTrackRecord::importedAt)
    }
        .toSingle()
        .subscribeOn(Schedulers.io())
        .doOnError {
            logger.error(it) { "getItems(): error_occurred" }
        }

    fun importTrack(
        name: String,
        geometry: LineString,
        poi: MultiPoint,
        thumbnail: Bitmap,
        onlinePreviewUrl: String?,
    ): Single<ImportedTrackRecord> {
        val importedAt = Date()
        val id = importedAt.time.toString()

        val geoJsonFileName = "$id.$GEOJSON_FILE_EXTENSION"
        val thumbnailImageFileName = "${id}_thumbnail.jpg"

        return writeThumbnailImageFile(
            thumbnail = thumbnail,
            fileName = thumbnailImageFileName,
        )
            .flatMap { thumbnailImageFile ->
                writeGeoJsonFile(
                    geoJson = ImportedTrackRecord.createGeoJson(
                        name = name,
                        thumbnailImageFileName = thumbnailImageFileName,
                        importedAt = importedAt,
                        geometry = geometry,
                        poi = poi,
                        onlinePreviewUrl = onlinePreviewUrl,
                    ),
                    fileName = geoJsonFileName
                )
                    .map { thumbnailImageFile to it }
            }
            .map { (thumbnailImageFile, geoJsonFile) ->
                ImportedTrackRecord(
                    name = name,
                    importedAt = importedAt,
                    thumbnailImageFile = thumbnailImageFile,
                    geoJsonFile = geoJsonFile,
                    onlinePreviewUrl = onlinePreviewUrl,
                )
            }
            .subscribeOn(Schedulers.io())
            .doOnSubscribe {
                logger.debug {
                    "importTrack(): start_import:" +
                            "\nname=$name," +
                            "\nid=$id"
                }
            }
            .doOnError {
                logger.error(it) {
                    "importTrack(): error_occurred"
                }
            }
            .doOnSuccess {
                itemsCache.add(it)
                broadcast()

                logger.debug {
                    "importTrack(): imported:" +
                            "\nrecord=$it"
                }
            }
    }

    private fun writeThumbnailImageFile(
        thumbnail: Bitmap,
        fileName: String,
    ): Single<File> = {
        File(directory.path, fileName).apply {
            createNewFile()
            outputStream().use { outputStream ->
                thumbnail.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            }
        }
    }
        .toSingle()
        .subscribeOn(Schedulers.io())

    private fun writeGeoJsonFile(
        geoJson: GeoJson,
        fileName: String,
    ): Single<File> = {
        File(directory.path, fileName).apply {
            createNewFile()
            writeText(geoJson.toJson())
        }
    }
        .toSingle()
        .subscribeOn(Schedulers.io())

    fun clear(): Completable = {
        directory
            .listFiles()
            ?.forEach(File::delete)
        itemsCache.clear()
        broadcast()
    }
        .toCompletable()
        .subscribeOn(Schedulers.io())
        .doOnSubscribe {
            logger.debug { "clear()" }
        }
        .doOnError {
            logger.error(it) {
                "clear(): error_occurred"
            }
        }

    private companion object {
        private const val GEOJSON_FILE_EXTENSION = "geojson"
    }
}
