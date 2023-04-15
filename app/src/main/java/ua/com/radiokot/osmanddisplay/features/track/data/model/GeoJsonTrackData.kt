package ua.com.radiokot.osmanddisplay.features.track.data.model

import android.os.Parcelable
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.mapbox.geojson.*
import kotlinx.android.parcel.Parcelize

@Parcelize
class GeoJsonTrackData(
    val name: String?,
    val trackFeature: Feature,
    val poi: MultiPoint,
) : Parcelable {
    init {
        requireNotNull(trackFeature.geometry()) {
            "The GeoJSON feature must have a geometry"
        }
    }

    val trackGeometry: Geometry
        get() = trackFeature.geometry()!!

    companion object {
        /**
         * @return [GeoJsonTrackData] if the [content] is a track GeoJSON,
         * throws exception otherwise.
         */
        fun fromFileContent(content: String): GeoJsonTrackData {
            val jsonContent = Gson().fromJson(content, JsonObject::class.java)

            val geoJsonType = jsonContent.get("type")?.asString

            checkNotNull(geoJsonType) {
                "GeoJSON content must have a type"
            }

            val featureCollection: FeatureCollection? =
                if (geoJsonType == "FeatureCollection")
                    FeatureCollection.fromJson(content)
                else
                    null

            val trackFeature: Feature? = when {
                featureCollection != null ->
                    featureCollection
                        .features()
                        ?.find { it.geometry() is LineString }
                geoJsonType == "Feature" ->
                    Feature.fromJson(content)
                else ->
                    null
            }

            val poi: MultiPoint =
                featureCollection
                    ?.features()
                    ?.map(Feature::geometry)
                    ?.filterIsInstance(Point::class.java)
                    .let { MultiPoint.fromLngLats(it ?: emptyList()) }

            checkNotNull(trackFeature) {
                "GeoJSON content must have at leas one LineString (track) feature"
            }

            checkNotNull(trackFeature.geometry()) {
                "The track feature must have a geometry"
            }

            val name = trackFeature
                .getStringProperty("name")
                ?.takeIf(String::isNotEmpty)

            return GeoJsonTrackData(
                name = name,
                trackFeature = trackFeature,
                poi = poi,
            )
        }
    }
}