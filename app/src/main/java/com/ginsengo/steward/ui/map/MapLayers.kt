package com.ginsengo.steward.ui.map

/** Which basemap is underneath everything. */
enum class Basemap(val label: String, val attribution: String) {
    /** OpenFreeMap dark vector — the default field look (PRD §4.1). */
    DARK("Dark", "© OpenFreeMap © OpenStreetMap contributors"),

    /**
     * USGS imagery. Public domain, no key, no usage cap, and legal to ship.
     * Esri World Imagery was the obvious alternative and was rejected: Esri's terms
     * require an ArcGIS licence and restrict redistribution in a commercial mobile app,
     * so it cannot go into a Play release. USGS covers the whole contiguous US, which
     * includes all 19 approved ginseng states.
     */
    SATELLITE("Satellite", "USGS National Map — public domain"),

    /** USGS topographic — contours, which a digger reads terrain from directly. */
    TOPO("Topo", "USGS National Map — public domain"),
    ;

    val tileUrl: String?
        get() = when (this) {
            DARK -> null // vector style, not a raster template
            SATELLITE -> "https://basemap.nationalmap.gov/arcgis/rest/services/" +
                    "USGSImageryOnly/MapServer/tile/{z}/{y}/{x}"
            TOPO -> "https://basemap.nationalmap.gov/arcgis/rest/services/" +
                    "USGSTopo/MapServer/tile/{z}/{y}/{x}"
        }

    /** USGS serves no tiles past z16; asking for more yields 404s and blank ground. */
    val maxZoom: Int get() = if (this == DARK) 20 else 16
}

/**
 * Everything the user can toggle on the map.
 *
 * `terrain3d` is named honestly: it tilts and exaggerates relief shading to read as depth.
 * It is NOT a 3D terrain mesh — MapLibre Android exposes no terrain API at any published
 * version (verified against the 13.6.1 artifact: no Terrain class, no setTerrain, and
 * raster-dem is wired only to hillshade). See EINCOL_REPORT.md for what a real 3D mesh
 * would take.
 */
data class MapLayerState(
    val basemap: Basemap = Basemap.DARK,
    val hillshade: Boolean = false,
    val heightOverlay: Boolean = false,
    val habitatHeatmap: Boolean = false,
    val terrain3d: Boolean = false,
    val heightOpacity: Float = 0.55f,
    val heatmapOpacity: Float = 0.75f,
) {
    /** True when anything needs the elevation tile source loaded. */
    val needsDem: Boolean get() = hillshade || heightOverlay || terrain3d
}
