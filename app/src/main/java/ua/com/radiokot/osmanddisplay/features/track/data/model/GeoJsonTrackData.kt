package ua.com.radiokot.osmanddisplay.features.track.data.model

import com.mapbox.geojson.LineString
import com.mapbox.geojson.MultiPoint

class GeoJsonTrackData(
    val name: String?,
    val track: LineString,
    val poi: MultiPoint,
)
