package ua.com.radiokot.osmanddisplay.features.track.data.model

import android.os.Parcelable
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.mapbox.geojson.*
import kotlinx.android.parcel.Parcelize

@Parcelize
class GeoJsonTrackData(
    val name: String?,
    val track: LineString,
    val poi: MultiPoint,
) : Parcelable {

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

            checkNotNull(trackFeature) {
                "GeoJSON content must have at leas one LineString (track) feature"
            }

            val track = trackFeature.geometry() as LineString

            val poi: MultiPoint =
                featureCollection
                    ?.features()
                    ?.map(Feature::geometry)
                    ?.filterIsInstance(Point::class.java)
                    .let { MultiPoint.fromLngLats(it ?: emptyList()) }

            val name = trackFeature
                .getStringProperty("name")
                ?.takeIf(String::isNotEmpty)

            return GeoJsonTrackData(
                name = name,
                track = track,
                poi = poi,
            )
        }
    }
}