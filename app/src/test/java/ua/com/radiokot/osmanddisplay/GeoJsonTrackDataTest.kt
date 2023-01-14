package ua.com.radiokot.osmanddisplay

import org.junit.Assert
import org.junit.Test
import ua.com.radiokot.osmanddisplay.features.track.model.GeoJsonTrackData

class GeoJsonTrackDataTest {
    @Test
    fun readFeatureCollection() {
        val content = """
            {
                "type": "FeatureCollection",
                "features": [
                    {
                        "type": "Feature",
                        "properties": {
                            "name": "На тот берег",
                            "type": "Велосипед",
                            "links": [
                                {
                                    "href": "https://www.strava.com/routes/3044739247970226322"
                                }
                            ]
                        },
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
                ]
            }
        """.trimIndent()

        val geoJsonTrackData = GeoJsonTrackData.fromFileContent(content)

        Assert.assertEquals("На тот берег", geoJsonTrackData.name)
    }

    @Test
    fun readFeature() {
        val content = """
            {
                "type": "Feature",
                "properties": {
                    "creator": "BRouter-1.6.3",
                    "name": "Днепр (16,2km)",
                    "track-length": "16210",
                    "filtered ascend": "89",
                    "plain-ascend": "8",
                    "total-time": "1677",
                    "total-energy": "180610"
                },
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