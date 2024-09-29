package ua.com.radiokot.osmanddisplay.features.track.data.model

import android.os.Parcelable
import com.ctc.wstx.stax.WstxInputFactory
import com.ctc.wstx.stax.WstxOutputFactory
import com.fasterxml.jackson.dataformat.xml.XmlFactory
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.LineString
import com.mapbox.geojson.MultiPoint
import com.mapbox.geojson.Point
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

            return GeoJsonTrackData(
                name = name,
                track = track,
                poi = poi,
            )
        }

        fun fromGpxContent(content: String): GeoJsonTrackData {
            val xmlMapper = XmlMapper.builder(
                XmlFactory.builder()
                    .xmlInputFactory(WstxInputFactory())
                    .xmlOutputFactory(WstxOutputFactory())
                    .build()
            ).build()

            val gpx: Gpx = xmlMapper.readValue(
                content,
                Gpx::class.java,
            )

            return GeoJsonTrackData(
                name = gpx.metadata.name
                    ?.takeIf(String::isNotEmpty),
                track = LineString.fromLngLats(
                    gpx.track.segments
                        .flatMap(Gpx.Track.Segment::trackPoints)
                        .map { Point.fromLngLat(it.lon, it.lat) }
                ),
                poi = MultiPoint.fromLngLats(emptyList()),
            )
        }
    }
}
