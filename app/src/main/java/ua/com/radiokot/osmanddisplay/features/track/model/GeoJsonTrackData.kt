package ua.com.radiokot.osmanddisplay.features.track.model

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Geometry

class GeoJsonTrackData(
    val name: String?,
    val geoJsonFeature: Feature,
    val geometry: Geometry,
) {
    companion object {
        /**
         * @return [GeoJsonTrackData] if the [content] is a track GeoJSON,
         * throws exception otherwise.
         */
        fun fromFileContent(content: String): GeoJsonTrackData {
            val jsonContent = Gson().fromJson(content, JsonObject::class.java)

            val geoJsonFeature = when (jsonContent.get("type").asString) {
                "Feature" ->
                    Feature.fromJson(content)
                "FeatureCollection" ->
                    FeatureCollection.fromJson(content)
                        .features()
                        ?.firstOrNull()
                else ->
                    null
            }

            checkNotNull(geoJsonFeature) {
                "The file content can't be read as GeoJSON"
            }

            val geometry = geoJsonFeature.geometry()
            checkNotNull(geometry) {
                "The Feature must have a geometry"
            }

            val name = geoJsonFeature
                .getStringProperty("name")
                .takeIf(String::isNotEmpty)

            return GeoJsonTrackData(
                name = name,
                geoJsonFeature = geoJsonFeature,
                geometry = geometry,
            )
        }
    }
}