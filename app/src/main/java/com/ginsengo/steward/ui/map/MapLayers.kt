package com.ginsengo.steward.ui.map

/**
 * The map under the app's layers: the OpenFreeMap dark vector style, drawn on the 3D ground by
 * MapDrape (and saved for offline use by OfflineArea). The USGS Topo choice went with the flat map
 * (wave M.1): it was never draped in 3D, and the 3D ground draws contours and hillshade itself.
 */
enum class Basemap(val label: String, val attribution: String) {
    DARK("Dark", "© OpenFreeMap © OpenStreetMap contributors"),
}

/** The dark style both MapDrape and the offline download read. */
const val DARK_STYLE = "https://tiles.openfreemap.org/styles/dark"

/**
 * The layers the user can switch, plus the map on the ground ([drape]).
 *
 *  - [habitat]: the terrain model under the published weights, or under learned weights once
 *    held-out finds have justified them.
 *  - [visited]: where you have been, the persistent travel memory (J31).
 *  - [unwalkedOnly]: J20, walked ground greyed so colour is left only where you have not been.
 *  - [finds]: your finds; every find at full weight (UserFinds).
 *
 * plus [water] (creeks and drains traced from elevation), the track line and the suggestion
 * markers. Everything defaults on except the track line, which the travel memory already
 * summarises, and the J20 grey-out, which is a question the owner asks, not a default view.
 */
data class MapLayerState(
    val basemap: Basemap = Basemap.DARK,
    /** The dark map's roads and names drawn on the ground (MapDrape); off: relief and layers only. */
    val drape: Boolean = true,
    val habitat: Boolean = true,
    val visited: Boolean = true,
    val unwalkedOnly: Boolean = false,
    val finds: Boolean = true,
    val trackLine: Boolean = false,
    val suggestions: Boolean = true,
    val water: Boolean = true,
    val hillshade: Boolean = true,
    /** Contour lines from the elevation tiles. */
    val contours: Boolean = true,
    val heatmapOpacity: Float = 0.7f,
)
