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
 * Two distinct things are on offer here and they are deliberately not merged:
 *
 *  - [pitchedRelief] tilts the camera and deepens hillshade so relief reads as depth. It is
 *    flat geometry that looks three-dimensional, and it costs nothing.
 *  - [terrainMesh] is an actual 3D triangle mesh, drawn in a separate GL surface layered
 *    over the map, because MapLibre Android exposes no terrain API at any published version
 *    (verified against the 13.6.1 artifact: no Terrain class, no setTerrain; raster-dem is
 *    wired only into hillshade).
 *
 * Keeping them separate matters because the mesh can fail in ways the relief cannot — no
 * elevation tiles, no GL context, or a camera reconstruction that disagrees with the map —
 * and when it does, the app should fall back to something honest rather than to nothing.
 */
/**
 * Which layers are drawn.
 *
 * Everything the map can show is ON by default. The reasoning changed once the camera bug was
 * fixed: with the map opening on a fallback four states wide, every DEM layer was computed for
 * ground nobody was standing on and silently did nothing, so defaulting them off hid a broken
 * app behind a clean-looking one. Now that the camera lands on the fix, a layer that draws
 * nothing is a real signal rather than an expected one.
 *
 * The cost is honest: hillshade, the height overlay and the habitat heatmap all pull elevation
 * tiles and the heatmap runs flow accumulation over them, so first paint does real work and the
 * radio is busy. That is the right trade for a tool you open once at the trailhead. Turn them
 * off in Layers if the phone is struggling.
 */
data class MapLayerState(
    val basemap: Basemap = Basemap.DARK,
    val hillshade: Boolean = true,
    val heightOverlay: Boolean = true,
    val habitatHeatmap: Boolean = true,
    val pitchedRelief: Boolean = true,
    /**
     * Permanently false. The GL mesh overlay blacked out the map once the map moved to a
     * TextureView; see LayerPanel for why the two cannot coexist. Kept as a field rather than
     * deleted so any saved state that still says `true` is ignored instead of crashing.
     */
    val terrainMesh: Boolean = false,
    val heightOpacity: Float = 0.55f,
    val heatmapOpacity: Float = 0.75f,
    val meshOpacity: Float = 0.85f,
    /** Vertical multiplier for the mesh. 1.0 is true scale. */
    val meshExaggeration: Float = 1.5f,
    /** Colour the mesh by the ginseng forecast instead of by elevation. */
    val meshForecastTint: Boolean = false,
) {
    /** True when anything needs the elevation tile source loaded. */
    val needsDem: Boolean get() = hillshade || heightOverlay || pitchedRelief || terrainMesh

    /** The mesh needs a tilted camera to read as 3D at all. */
    val wantsTilt: Boolean get() = pitchedRelief || terrainMesh
}
