package ua.com.radiokot.osmanddisplay.features.track.data.model

class GpxTrackData(
    val name: String?,
    val track: List<GpxData.Track.Point>,
    val link: String?,
) {

    constructor(gpx: GpxData) : this(
        name = gpx.metadata.name
            ?.takeIf(String::isNotEmpty),
        track = gpx.track.segments
            .flatMap(GpxData.Track.Segment::trackPoints),
        link = gpx.metadata.link
            ?.takeIf(String::isNotEmpty),
    )
}
