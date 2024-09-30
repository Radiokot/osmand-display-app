package ua.com.radiokot.osmanddisplay

import com.ctc.wstx.stax.WstxInputFactory
import com.ctc.wstx.stax.WstxOutputFactory
import com.fasterxml.jackson.dataformat.xml.XmlFactory
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import org.junit.Assert
import org.junit.Test
import ua.com.radiokot.osmanddisplay.features.track.logic.ReadGeoJsonFileUseCase
import ua.com.radiokot.osmanddisplay.features.track.logic.ReadGpxFileUseCase

class TrackDataTest {
    @Test
    fun readFeatureCollection() {
        val geoJsonTrackData = ReadGeoJsonFileUseCase().invoke(
            TestAssets.getInputStream("StravaTrack.geojson")
        ).blockingGet()

        Assert.assertEquals("На тот берег", geoJsonTrackData.name)
        Assert.assertEquals(0, geoJsonTrackData.poi.coordinates().size)
        Assert.assertEquals(
            35.072030000000005,
            geoJsonTrackData.track.coordinates().first().longitude(),
            0.000001
        )
        Assert.assertEquals(
            48.45664000000001,
            geoJsonTrackData.track.coordinates().first().latitude(),
            0.000001
        )
        Assert.assertEquals(
            35.07115,
            geoJsonTrackData.track.coordinates().last().longitude(),
            0.000001
        )
        Assert.assertEquals(
            48.4566,
            geoJsonTrackData.track.coordinates().last().latitude(),
            0.000001
        )
    }

    @Test
    fun readFeature() {
        val geoJsonTrackData = ReadGeoJsonFileUseCase().invoke(
            TestAssets.getInputStream("BRouterTrack.geojson")
        ).blockingGet()

        Assert.assertEquals("Днепр (16,2km)", geoJsonTrackData.name)
        Assert.assertEquals(0, geoJsonTrackData.poi.coordinates().size)
    }

    @Test
    fun readWithPoi() {
        val geoJsonTrackData = ReadGeoJsonFileUseCase().invoke(
            TestAssets.getInputStream("BRouterTrackWithPOI.geojson")
        ).blockingGet()

        val poi = geoJsonTrackData.poi
        Assert.assertEquals(3, poi.coordinates().size)
        Assert.assertEquals(35.01828452247929, poi.coordinates().last().longitude(), 0.000001)
        Assert.assertEquals(48.434744933730215, poi.coordinates().last().latitude(), 0.000001)
    }

    @Test
    fun readWithTrailingCommas() {
        val geoJsonTrackData = ReadGeoJsonFileUseCase().invoke(
            TestAssets.getInputStream("BRouterTrackWithTrailingComma.geojson")
        ).blockingGet()

        Assert.assertEquals(2, geoJsonTrackData.poi.coordinates().size)
    }

    @Test(expected = IllegalStateException::class)
    fun readEmptyFeatureCollection() {
        ReadGeoJsonFileUseCase().invoke(
            TestAssets.getInputStream("EmptyCollectionTrack.geojson")
        ).blockingGet()
    }

    @Test
    fun readFeatureWithNoName() {
        val geoJsonTrackData = ReadGeoJsonFileUseCase().invoke(
            TestAssets.getInputStream("NamelessTrack.geojson")
        ).blockingGet()

        Assert.assertNull(geoJsonTrackData.name)
        Assert.assertEquals(0, geoJsonTrackData.poi.coordinates().size)
    }

    @Test
    fun readStravaGpx() {
        val gpxTrackData = ReadGpxFileUseCase(
            xmlMapper = XmlMapper.builder(
                XmlFactory.builder()
                    .xmlInputFactory(WstxInputFactory())
                    .xmlOutputFactory(WstxOutputFactory())
                    .build()
            ).build()
        )
            .invoke(TestAssets.getInputStream("StravaTrack.gpx"))
            .blockingGet()

        Assert.assertEquals("На тот берег", gpxTrackData.name)
        Assert.assertEquals(
            35.072030000000005,
            gpxTrackData.track.first().lon,
            0.000001
        )
        Assert.assertEquals(
            48.45664000000001,
            gpxTrackData.track.first().lat,
            0.000001
        )
        Assert.assertEquals(
            35.07115,
            gpxTrackData.track.last().lon,
            0.000001
        )
        Assert.assertEquals(
            48.4566,
            gpxTrackData.track.last().lat,
            0.000001
        )
    }
}
