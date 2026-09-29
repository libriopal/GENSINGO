package com.ginsengo.steward.ui.map

/** Which basemap is underneath everything. */
enum class Basemap(val label: String, val attribution: String) {
    /** OpenFreeMap dark vector: the default, and the one the offline download saves. */
    DARK("Dark", "© OpenFreeMap © OpenStreetMap contributors"),

    /**
     * USGS topographic: contours, which a digger reads terrain from directly. Public domain,
     * no key, legal to ship. (Esri imagery was rejected earlier on licence grounds.)
     */
    TOPO("Topo", "USGS National Map — public domain"),
    ;

    val tileUrl: String?
        get() = when (this) {
            DARK -> null
            TOPO -> "https://basemap.nationalmap.gov/arcgis/rest/services/" +
                    "USGSTopo/MapServer/tile/{z}/{y}/{x}"
        }

    val maxZoom: Int get() = if (this == DARK) 20 else 16
}

/**
 * The layers the user can switch. Five surfaces, three of which are the "heatmaps":
 *
 *  - [habitat]: the terrain model under the published weights, or under learned weights once
 *    held-out finds have justified them. Computed on the phone per viewport.
 *  - [visited]: where you have been, a GPU heatmap of stored track points.
 *  - [finds]: your finds, a GPU heatmap; verified finds weigh more than unverified ones.
 *
 * plus the track line and the suggestion markers. Everything defaults on except the track
 * line, which the visited heatmap already summarises.
 */
data class MapLayerState(
    val basemap: Basemap = Basemap.DARK,
    val habitat: Boolean = true,
    val visited: Boolean = true,
    val finds: Boolean = true,
    val trackLine: Boolean = false,
    val suggestions: Boolean = true,
    val hillshade: Boolean = true,
    val heatmapOpacity: Float = 0.7f,
)
