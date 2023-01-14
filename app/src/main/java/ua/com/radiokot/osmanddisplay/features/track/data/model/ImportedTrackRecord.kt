package ua.com.radiokot.osmanddisplay.features.track.data.model

import com.mapbox.geojson.Feature
import java.io.File
import java.util.*

class ImportedTrackRecord(
    val name: String,
    val importedAt: Date,
    val thumbnailImageFile: File,
    val geoJsonFile: File
) {
    companion object {
        fun fromGeoJsonFile(file: File): ImportedTrackRecord {
            val geoJson = Feature.fromJson(file.readText())

            val thumbnailImageFileName = geoJson.getStringProperty("thumbnail")

            return ImportedTrackRecord(
                name = geoJson.getStringProperty("name"),
                importedAt = Date(geoJson.getNumberProperty("imported_at").toLong()),
                thumbnailImageFile = File(file.parentFile, thumbnailImageFileName),
                geoJsonFile = file
            )
        }
    }
}