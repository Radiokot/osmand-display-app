package ua.com.radiokot.osmanddisplay

import org.junit.Assert
import org.junit.Test
import ua.com.radiokot.osmanddisplay.features.track.data.model.GeoJsonTrackData

class GeoJsonTrackDataTest {
    @Test
    fun readFeatureCollection() {
        val content = TestAssets.readText("StravaTrack.geojson")
        val geoJsonTrackData = GeoJsonTrackData.fromFileContent(content)

        Assert.assertEquals("На тот берег", geoJsonTrackData.name)
    }

    @Test
    fun readFeature() {
        val content = TestAssets.readText("BRouterTrack.geojson")
        val geoJsonTrackData = GeoJsonTrackData.fromFileContent(content)

        Assert.assertEquals("Днепр (16,2km)", geoJsonTrackData.name)
    }

    @Test(expected = IllegalStateException::class)
    fun readEmptyFeatureCollection() {
        val content = """
            {
                "type": "FeatureCollection",
                "features": []
            }
        """.trimIndent()
        GeoJsonTrackData.fromFileContent(content)
    }

    @Test
    fun readFeatureWithNoName() {
        val content = """
            {
                "type": "Feature",
                "geometry": {
                    "type": "LineString",
                    "coordinates": [
                        [
                            35.072030000000005,
                            48.45664000000001,
                            102.83
                        ],
                        [
                            35.07115,
                            48.4566,
                            106.59
                        ]
                    ]
                }
            }
        """.trimIndent()

        val geoJsonTrackData = GeoJsonTrackData.fromFileContent(content)

        Assert.assertNull(geoJsonTrackData.name)
    }
}