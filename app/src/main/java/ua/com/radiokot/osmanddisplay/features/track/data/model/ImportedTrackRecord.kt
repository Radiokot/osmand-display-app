package ua.com.radiokot.osmanddisplay.features.track.data.model

import android.os.Parcelable
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.GeoJson
import com.mapbox.geojson.LineString
import com.mapbox.geojson.MultiPoint
import kotlinx.android.parcel.Parcelize
import java.io.File
import java.util.Date

@Parcelize
class ImportedTrackRecord(
    val name: String,
    val importedAt: Date,
    val thumbnailImageFile: File,
    val geoJsonFile: File,
    val onlinePreviewUrl: String?,
) : Parcelable {
    val id: String
        get() = geoJsonFile.nameWithoutExtension

    fun readTrackGeoJson(): String =
        geoJsonFile.readText(Charsets.UTF_8)
            .let(FeatureCollection::fromJson)
            .features()!!
            .first()
            .toJson()

    fun readPoiGeoJson(): String =
        geoJsonFile.readText(Charsets.UTF_8)
            .let(FeatureCollection::fromJson)
            .features()!![1]
            .toJson()

    override fun toString(): String {
        return "ImportedTrackRecord(name='$name', id='$id')"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ImportedTrackRecord

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    companion object {
        private const val VERSION = 2

        fun fromGeoJsonFile(file: File): ImportedTrackRecord {
            val features = FeatureCollection.fromJson(file.readText()).features()
            requireNotNull(features) {
                "Track must be a non-empty FeatureCollection"
            }

            val trackFeature = features.first()

            val version = trackFeature.getNumberProperty("version").toInt()
            require(version == VERSION) {
                "Unknown version $version"
            }

            val thumbnailImageFileName = trackFeature.getStringProperty("thumbnail")
            val onlinePreviewUrl = trackFeature.getStringProperty("online_preview_url")

            return ImportedTrackRecord(
                name = trackFeature.getStringProperty("name"),
                importedAt = Date(trackFeature.getNumberProperty("imported_at").toLong()),
                thumbnailImageFile = File(file.parentFile, thumbnailImageFileName),
                geoJsonFile = file,
                onlinePreviewUrl = onlinePreviewUrl,
            )
        }

        fun createGeoJson(
            name: String,
            importedAt: Date,
            geometry: LineString,
            poi: MultiPoint,
            thumbnailImageFileName: String,
            onlinePreviewUrl: String?,
        ): GeoJson = FeatureCollection.fromFeatures(
            arrayOf(
                Feature.fromGeometry(geometry).apply {
                    addNumberProperty("version", 2)
                    addStringProperty("name", name)
                    addStringProperty("type", "track")
                    addNumberProperty("imported_at", importedAt.time)
                    addStringProperty("thumbnail", thumbnailImageFileName)
                    onlinePreviewUrl?.also {
                        addStringProperty("online_preview_url", it)
                    }
                },
                Feature.fromGeometry(poi).apply {
                    addStringProperty("type", "extra-poi")
                },
            )
        )
    }
}
