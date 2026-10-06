package com.ginsengo.steward.ui.map

/**
 * The map draped on the 3D ground by MapDrape, under the app's layers (P.1: the owner asked for
 * Google-Maps-like map types). The dark streets style is the one saved for offline use
 * (OfflineArea); satellite and topo are the USGS National Map's public-domain tiles (US
 * government work), which the app used before M.1, and need a connection unless MapLibre's own
 * cache already holds them. [style] is a style URL or a style JSON; null draws no map.
 */
enum class Basemap(val label: String, val attribution: String, val style: String?, val maxZoom: Double) {
    DARK("Streets", "© OpenFreeMap © OpenStreetMap contributors", DARK_STYLE, 14.0),
    SATELLITE("Satellite", "Imagery: USGS The National Map", usgsRaster("USGSImageryOnly"), 15.0),
    TOPO("Topo", "USGS The National Map: US Topo", usgsRaster("USGSTopo"), 15.0),
    NONE("Terrain only", "", null, 14.0),
}

/** The dark style both MapDrape and the offline download read. */
const val DARK_STYLE = "https://tiles.openfreemap.org/styles/dark"

/** A one-layer raster style over a USGS National Map tile service (z/y/x, 256 px, to zoom 16). */
private fun usgsRaster(service: String): String =
    """{"version":8,"sources":{"usgs":{"type":"raster","tiles":["https://basemap.nationalmap.gov/arcgis/rest/services/""" +
        service + """/MapServer/tile/{z}/{y}/{x}"],"tileSize":256,"maxzoom":16}},"layers":[{"id":"usgs","type":"raster","source":"usgs"}]}"""

/**
 * Roads and trails over the ground (N.1): OpenStreetMap's transportation lines from the same
 * OpenFreeMap vector tiles the streets map uses (so "Save 10 miles" already holds them offline),
 * drawn bright on a transparent background and baked ON TOP of the habitat colour. Logging and
 * forest roads are OSM `highway=track` (class "track"); trails are `path`, `footway`, `bridleway`
 * (class "path"). No labels: text needs glyphs, and one missing glyph fails a whole snapshot.
 */
const val ROADS_STYLE = """{"version":8,"sources":{"omt":{"type":"vector","url":"https://tiles.openfreemap.org/planet"}},"layers":[""" +
    """{"id":"major-case","type":"line","source":"omt","source-layer":"transportation","filter":["match",["get","class"],["motorway","trunk","primary","secondary","tertiary"],true,false],"layout":{"line-cap":"round","line-join":"round"},"paint":{"line-color":"#06100B","line-width":4.2}},""" +
    """{"id":"major","type":"line","source":"omt","source-layer":"transportation","filter":["match",["get","class"],["motorway","trunk","primary","secondary","tertiary"],true,false],"layout":{"line-cap":"round","line-join":"round"},"paint":{"line-color":"#FFFFFF","line-width":2.6}},""" +
    """{"id":"minor-case","type":"line","source":"omt","source-layer":"transportation","filter":["match",["get","class"],["minor","service"],true,false],"layout":{"line-cap":"round","line-join":"round"},"paint":{"line-color":"#06100B","line-width":3.2}},""" +
    """{"id":"minor","type":"line","source":"omt","source-layer":"transportation","filter":["match",["get","class"],["minor","service"],true,false],"layout":{"line-cap":"round","line-join":"round"},"paint":{"line-color":"#E6F4EC","line-width":1.7}},""" +
    """{"id":"track-case","type":"line","source":"omt","source-layer":"transportation","filter":["==",["get","class"],"track"],"layout":{"line-cap":"butt","line-join":"round"},"paint":{"line-color":"#06100B","line-width":3.6}},""" +
    """{"id":"track","type":"line","source":"omt","source-layer":"transportation","filter":["==",["get","class"],"track"],"layout":{"line-cap":"butt","line-join":"round"},"paint":{"line-color":"#FFB02E","line-width":2.2,"line-dasharray":[2.5,1.2]}},""" +
    """{"id":"path-case","type":"line","source":"omt","source-layer":"transportation","filter":["==",["get","class"],"path"],"layout":{"line-cap":"round","line-join":"round"},"paint":{"line-color":"#06100B","line-width":3.0}},""" +
    """{"id":"path","type":"line","source":"omt","source-layer":"transportation","filter":["==",["get","class"],"path"],"layout":{"line-cap":"round","line-join":"round"},"paint":{"line-color":"#FF6B5A","line-width":1.8,"line-dasharray":[1.2,1.0]}}""" +
    """]}"""

/** Vertical exaggeration choices for the 3D ground (rendering settings, P.1). */
val RELIEF_CHOICES = listOf(1.0f, 1.5f, 2.0f, 3.0f)

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
    /** The map draped on the ground (MapDrape); [Basemap.NONE]: relief and layers only. */
    val basemap: Basemap = Basemap.DARK,
    /** Vertical exaggeration of the 3D ground ([RELIEF_CHOICES]); a change rebuilds the mesh. */
    val relief: Float = 1.5f,
    /** The legend box over the map; hidden, the map is clear. */
    val legend: Boolean = true,
    val habitat: Boolean = true,
    val visited: Boolean = true,
    val unwalkedOnly: Boolean = false,
    val finds: Boolean = true,
    val trackLine: Boolean = false,
    val suggestions: Boolean = true,
    val water: Boolean = true,
    /** N.1: logging and forest roads, trails and roads from OpenStreetMap, over the habitat colour. */
    val roads: Boolean = true,
    val hillshade: Boolean = true,
    /** Contour lines from the elevation tiles. */
    val contours: Boolean = true,
    val heatmapOpacity: Float = 0.7f,
)
