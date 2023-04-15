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
        Assert.assertEquals(0, geoJsonTrackData.poi.coordinates().size)
    }

    @Test
    fun readFeature() {
        val content = TestAssets.readText("BRouterTrack.geojson")
        val geoJsonTrackData = GeoJsonTrackData.fromFileContent(content)

        Assert.assertEquals("Днепр (16,2km)", geoJsonTrackData.name)
        Assert.assertEquals(0, geoJsonTrackData.poi.coordinates().size)
    }

    @Test
    fun readWithPoi() {
        val content = TestAssets.readText("BRouterTrackWithPOI.geojson")
        val geoJsonTrackData = GeoJsonTrackData.fromFileContent(content)

        val poi = geoJsonTrackData.poi
        Assert.assertEquals(3, poi.coordinates().size)
        Assert.assertEquals(35.01828452247929, poi.coordinates().last().longitude(), 0.000001)
        Assert.assertEquals(48.434744933730215, poi.coordinates().last().latitude(), 0.000001)
    }

    @Test
    fun readWithTrailingCommas() {
        val content = TestAssets.readText("BRouterTrackWithTrailingComma.geojson")
        val geoJsonTrackData = GeoJsonTrackData.fromFileContent(content)
        
        Assert.assertEquals(2, geoJsonTrackData.poi.coordinates().size)
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
        val content = TestAssets.readText("NamelessTrack.geojson")
        val geoJsonTrackData = GeoJsonTrackData.fromFileContent(content)

        Assert.assertNull(geoJsonTrackData.name)
        Assert.assertEquals(0, geoJsonTrackData.poi.coordinates().size)
    }
}