package ua.com.radiokot.osmanddisplay.features.track.data.model

import com.mapbox.geojson.Feature
import com.mapbox.geojson.GeoJson
import com.mapbox.geojson.Geometry
import java.io.File
import java.util.*

class ImportedTrackRecord(
    val name: String,
    val importedAt: Date,
    val thumbnailImageFile: File,
    val geoJsonFile: File
) {
    companion object {
        private const val VERSION = 1

        fun fromGeoJsonFile(file: File): ImportedTrackRecord {
            val geoJson = Feature.fromJson(file.readText())

            val version = geoJson.getNumberProperty("version")
            require(version == VERSION) {
                "Unknown version $version"
            }

            val thumbnailImageFileName = geoJson.getStringProperty("thumbnail")

            return ImportedTrackRecord(
                name = geoJson.getStringProperty("name"),
                importedAt = Date(geoJson.getNumberProperty("imported_at").toLong()),
                thumbnailImageFile = File(file.parentFile, thumbnailImageFileName),
                geoJsonFile = file
            )
        }

        fun createGeoJson(
            name: String,
            importedAt: Date,
            geometry: Geometry,
            thumbnailImageFileName: String,
        ): GeoJson = Feature.fromGeometry(geometry).apply {
            addNumberProperty("version", 1)
            addStringProperty("name", name)
            addNumberProperty("imported_at", importedAt.time)
            addStringProperty("thumbnail", thumbnailImageFileName)
        }
    }
}