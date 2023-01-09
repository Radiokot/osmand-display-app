package ua.com.radiokot.osmanddisplay.base.extension

import android.graphics.Bitmap
import android.graphics.Color
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.generated.symbolLayer
import com.mapbox.maps.extension.style.layers.properties.generated.IconRotationAlignment
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.layers.properties.generated.SymbolPlacement
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource

fun Style.addTrack(
    id: String,
    geoJsonData: String,
    directionMarker: Bitmap? = null,
) = apply {
    addSource(geoJsonSource(id) {
        data(geoJsonData)
    })

    addLayer(lineLayer("$id-line", id) {
        lineCap(LineCap.ROUND)
        lineJoin(LineJoin.ROUND)
        lineWidth(18.0)
        lineColor(Color.WHITE)
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