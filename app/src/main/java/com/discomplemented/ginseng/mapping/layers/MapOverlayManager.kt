package com.discomplemented.ginseng.mapping.layers

import android.graphics.Color
import com.discomplemented.ginseng.domain.model.GinsengPatch
import com.discomplemented.ginseng.domain.model.TrackNode
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.sources.GeoJsonSource

package com.discomplemented.ginseng.mapping.layers

import android.graphics.Color
import com.discomplemented.ginseng.domain.model.GinsengPatch
import com.discomplemented.ginseng.domain.model.SuitabilityPoint
import com.discomplemented.ginseng.domain.model.TrackNode
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.sources.GeoJsonSource

class MapOverlayManager(private val style: Style) {

    private val TRACK_SOURCE_ID = "track-source"
    private val TRACK_LAYER_ID = "track-layer"
    private val PATCH_SOURCE_ID = "patch-source"
    private val PATCH_LAYER_ID = "patch-layer"
    private val SUITABILITY_SOURCE_ID = "suitability-source"
    private val SUITABILITY_LAYER_ID = "suitability-layer"

    /**
     * Adds or updates the track history layer as a LineString.
     */
    fun updateTrackLayer(nodes: List<TrackNode>) {
        if (nodes.isEmpty()) return

        val geoJson = buildTrackGeoJson(nodes)

        if (style.getSource(TRACK_SOURCE_ID) !is GeoJsonSource) {
            style.addSource(GeoJsonSource(TRACK_SOURCE_ID, geoJson))
            style.addLayer(LineLayer(TRACK_LAYER_ID, TRACK_SOURCE_ID).apply {
                lineColor(Color.BLUE.toHexString())
                lineWidth(3.0)
            })
        } else {
            (style.getSource(TRACK_SOURCE_ID) as? GeoJsonSource)?.setGeoJson(geoJson)
        }
    }

    /**
     * Adds or updates the ginseng patch layer as a heatmap or circle layer.
     */
    fun updatePatchLayer(patches: List<GinsengPatch>) {
        if (patches.isEmpty()) return

        val geoJson = buildPatchGeoJson(patches)

        if (style.getSource(PATCH_SOURCE_ID) !is GeoJsonSource) {
            style.addSource(GeoJsonSource(PATCH_SOURCE_ID, geoJson))
            style.addLayer(CircleLayer(PATCH_LAYER_ID, PATCH_SOURCE_ID).apply {
                circleColor(Color.RED.toHexString())
                circleRadius(6.0)
                circleStrokeColor(Color.WHITE.toHexString())
                circleStrokeWidth(2.0)
            })
        } else {
            (style.getSource(PATCH_SOURCE_ID) as? GeoJsonSource)?.setGeoJson(geoJson)
        }
    }

    /**
     * Adds or updates the suitability heatmap layer as a circle layer.
     */
    fun updateSuitabilityLayer(points: List<SuitabilityPoint>) {
        if (points.isEmpty()) return

        val geoJson = buildSuitabilityGeoJson(points)

        if (style.getSource(SUITABILITY_SOURCE_ID) !is GeoJsonSource) {
            style.addSource(GeoJsonSource(SUITABILITY_SOURCE_ID, geoJson))
            style.addLayer(CircleLayer(SUITABILITY_LAYER_ID, SUITABILITY_SOURCE_ID).apply {
                circleColor(Color.GREEN.toHexString())
                circleRadius(8.0)
                circleOpacity(0.6)
            })
        } else {
            (style.getSource(SUITABILITY_SOURCE_ID) as? GeoJsonSource)?.setGeoJson(geoJson)
        }
    }

    private fun buildTrackGeoJson(nodes: List<TrackNode>): String {
        val coordinates = nodes.map { "[${it.longitude}, ${it.latitude}]" }.joinToString(",")
        return """{"type": "FeatureCollection", "features": [{"type": "Feature", "geometry": {"type": "LineString", "coordinates": [$coordinates]}, "properties": {}}]}"""
    }

    private fun buildPatchGeoJson(patches: List<GinsengPatch>): String {
        val features = patches.joinToString(",") { patch ->
            """{"type": "Feature", "geometry": {"type": "Point", "coordinates": [${patch.longitude}, ${patch.latitude}]}, "properties": {}}"""
        }
        return """{"type": "FeatureCollection", "features": [$features]}"""
    }

    private fun buildSuitabilityGeoJson(points: List<SuitabilityPoint>): String {
        val features = points.joinToString(",") { point ->
            """{"type": "Feature", "geometry": {"type": "Point", "coordinates": [${point.longitude}, ${point.latitude}]}, "properties": {"score": ${point.score}}}"""
        }
        return """{"type": "FeatureCollection", "features": [$features]}"""
    }

    private fun Int.toHexString(): String {
        return String.format("#%06X", (0xFFFFFF and this))
    }
}
