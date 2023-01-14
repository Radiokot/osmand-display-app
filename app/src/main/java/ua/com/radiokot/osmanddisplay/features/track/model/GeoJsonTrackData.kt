package ua.com.radiokot.osmanddisplay.features.track.model

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.GeoJson

class GeoJsonTrackData(
    val name: String?,
    val geoJson: GeoJson,
) {
    companion object {
        /**
         * @return [GeoJsonTrackData] if the [content] is a track GeoJSON,
         * throws exception otherwise.
         */
        fun fromFileContent(content: String): GeoJsonTrackData {
            val jsonContent = Gson().fromJson(content, JsonObject::class.java)

            val geoJson = when (jsonContent.get("type").asString) {
                "Feature" ->
                    Feature.fromJson(content)
                "FeatureCollection" ->
                    FeatureCollection.fromJson(content)
                        .takeIf { !it.features().isNullOrEmpty() }
                else ->
                    null
            }

            check(geoJson != null) {
                "The file content can't be read as GeoJSON"
            }

            val name = when (geoJson) {
                is Feature ->
                    getNameOfFeature(geoJson)
                is FeatureCollection ->
                    geoJson.features()
                        ?.firstOrNull()
                        ?.let(::getNameOfFeature)
                else ->
                    null
            }

            return GeoJsonTrackData(
                name = name,
                geoJson = geoJson,
            )
        }

        private fun getNameOfFeature(feature: Feature): String? {
            return feature.getStringProperty("name")
        }
    }
}