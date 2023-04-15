package ua.com.radiokot.osmanddisplay.base.extension

import android.graphics.Bitmap
import android.graphics.Color
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.generated.symbolLayer
import com.mapbox.maps.extension.style.layers.properties.generated.*
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource

fun Style.addTrack(
    id: String,
    geoJsonData: String,
    color: Int = Color.WHITE,
    width: Double = 18.0,
    directionMarker: Bitmap? = null,
) = apply {
    addSource(geoJsonSource(id) {
        data(geoJsonData)
    })

    addLayer(lineLayer("$id-line", id) {
        lineCap(LineCap.ROUND)
        lineJoin(LineJoin.ROUND)
        lineWidth(width)
        lineColor(color)
    })

    if (directionMarker != null) {
        val imageId = "$id-direction-marker"

        addImage(imageId, directionMarker)

        addLayer(symbolLayer("$id-direction", id) {
            symbolPlacement(SymbolPlacement.LINE)
            symbolSpacing(25.0)
            iconImage(imageId)
            iconAllowOverlap(true)
            iconIgnorePlacement(true)
            iconRotate(90.0)
            iconRotationAlignment(IconRotationAlignment.MAP)
        })
    }
}

fun Style.addMultipoint(
    id: String,
    geoJsonData: String,
    marker: Bitmap,
) = apply {
    addSource(geoJsonSource(id) {
        data(geoJsonData)
    })

    val imageId = "$id-poi-marker"

    addImage(imageId, marker)

    addLayer(symbolLayer("$id-multipoint", id) {
        symbolPlacement(SymbolPlacement.POINT)
        iconImage(imageId)
        iconAllowOverlap(true)
        iconIgnorePlacement(true)
        iconAnchor(IconAnchor.BOTTOM)
    })
}