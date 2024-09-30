package ua.com.radiokot.osmanddisplay.features.track.logic

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.LineString
import com.mapbox.geojson.MultiPoint
import com.mapbox.geojson.Point
import io.reactivex.Single
import io.reactivex.rxkotlin.toSingle
import io.reactivex.schedulers.Schedulers
import ua.com.radiokot.osmanddisplay.features.track.data.model.GeoJsonTrackData
import java.io.InputStream
import java.io.InputStreamReader

class ReadGeoJsonFileUseCase {
    operator fun invoke(
        inputStream: InputStream,
    ): Single<GeoJsonTrackData> = {
        val content = inputStream.use {
            InputStreamReader(it).readText()
        }

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
                    // Safe calls are required.
                    // Arbitrary file parsing result may contain null features.
                    ?.find { it?.geometry() is LineString }

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
                // Safe calls are required. See above.
                ?.mapNotNull { it?.geometry() }
                ?.filterIsInstance<Point>()
                .let { MultiPoint.fromLngLats(it ?: emptyList()) }

        val name = trackFeature
            .getStringProperty("name")
            ?.takeIf(String::isNotEmpty)

        GeoJsonTrackData(
            name = name,
            track = track,
            poi = poi,
        )
    }.toSingle().subscribeOn(Schedulers.io())

    private companion object {
        private const val MAX_FILE_SIZE_BYTES = 1 * 1024 * 1024
    }
}
