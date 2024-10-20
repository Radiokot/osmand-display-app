package ua.com.radiokot.osmanddisplay.features.track.data.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(localName = "gpx")
class GpxData
@JsonCreator
constructor(
    @JsonProperty("metadata")
    val metadata: Metadata?,
    @JsonProperty("trk")
    val track: Track,
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    class Metadata
    @JsonCreator
    constructor(
        @JsonProperty("name")
        val name: String?,
        @JsonProperty("link")
        val link: Link?,
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        class Link
        @JsonCreator
        constructor(
            @JacksonXmlProperty(localName = "href", isAttribute = true)
            val href: String,
        )
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    class Track
    @JsonCreator
    constructor(
        @JsonProperty("trkseg")
        @JacksonXmlElementWrapper(useWrapping = false)
        val segments: List<Segment>,
        @JsonProperty("name")
        val name: String?,
    ) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        class Segment
        @JsonCreator
        constructor(
            @JsonProperty("trkpt")
            @JacksonXmlElementWrapper(useWrapping = false)
            val trackPoints: List<Point>,
        )

        @JsonIgnoreProperties(ignoreUnknown = true)
        class Point
        @JsonCreator
        constructor(
            @JacksonXmlProperty(localName = "lat", isAttribute = true)
            val lat: Double,
            @JacksonXmlProperty(localName = "lon", isAttribute = true)
            val lon: Double,
        )
    }
}
