package com.ginsengo.steward.terrain3d

import com.ginsengo.steward.terrain.DemTileStore
import com.ginsengo.steward.terrain.GinsengSuitability
import com.ginsengo.steward.terrain.Hydrology
import com.ginsengo.steward.terrain.SuitabilityRasterizer
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sinh

/**
 * The 3D view's scene: a small square of ground at the best elevation resolution the tiles
 * offer, built once and then only re-coloured.
 *
 * The view covers [AREA_TILES] x [AREA_TILES] elevation tiles around the user: at zoom 15
 * that is about 3 km square at 3.9 m cells, and the whole budget of the view (mesh density,
 * texture texels, contour interval) is spent on that one square. Zoom 14 and 13 are the
 * fallbacks when the zoom-15 tiles are not cached.
 */
object Terrain3D {

    val ZOOMS = intArrayOf(15, 14, 13)
    const val AREA_TILES = 3
    /** Units for mesh positions; any zoom works, the camera scales from it. */
    const val BUILD_ZOOM = 14.0
    const val EXAGGERATION = 1.5f
    /** The 2D map's position-on-slope radius at the zoom where ~3 km fills the screen. */
    const val TPI_RADIUS_M = 300.0
    const val MAX_GRID = 385

    /**
     * (north, west, south, east) of the [AREA_TILES]-square block of tiles centred on the
     * user's tile at zoom [z], inset by a hair so tile arithmetic lands inside it.
     */
    fun areaBounds(lat: Double, lng: Double, z: Int): DoubleArray {
        val half = AREA_TILES / 2
        val tx0 = DemTileStore.lonToTileX(lng, z) - half
        val ty0 = DemTileStore.latToTileY(lat, z) - half
        val north = DemTileStore.tileYToLat(ty0, z)
        val south = DemTileStore.tileYToLat(ty0 + AREA_TILES, z)
        val west = DemTileStore.tileXToLon(tx0, z)
        val east = DemTileStore.tileXToLon(tx0 + AREA_TILES, z)
        val eLat = (north - south) * 1e-6; val eLng = (east - west) * 1e-6
        return doubleArrayOf(north - eLat, west + eLng, south + eLat, east - eLng)
    }

    /** Vertices per edge: one every two elevation cells, capped. */
    fun gridFor(m: DemTileStore.Mosaic): Int {
        val interior = max(m.grid.w, m.grid.h) - 2 * m.haloPx
        return min(MAX_GRID, interior / 2 + 1).coerceAtLeast(33)
    }

    /** Camera zoom at which the area's width fills [fraction] of the viewport. */
    fun fitZoom(areaWidthM: Double, lat: Double, viewportWidthPx: Int, fraction: Double = 0.92): Double {
        val metresPerPx = areaWidthM / (fraction * viewportWidthPx.coerceAtLeast(1))
        return ln(MapCamera.EARTH_CIRCUMFERENCE * cos(Math.toRadians(lat)) / (MapCamera.TILE_SIZE * metresPerPx)) / ln(2.0)
    }

    class Scene(
        val mosaic: DemTileStore.Mosaic,
        val ground: TerrainTextures.Ground,
        val mesh: TerrainMesh.Mesh,
        val creekKm: Double,
    ) {
        val textureSize = TerrainTextures.sizeFor(mosaic)
        private val iw = mosaic.grid.w - 2 * mosaic.haloPx
        private val ih = mosaic.grid.h - 2 * mosaic.haloPx
        val widthM = iw * mosaic.grid.cellSizeM
        val heightM = ih * mosaic.grid.cellSizeM
        val contourM = TerrainTextures.contourInterval((mesh.maxElevationM - mesh.minElevationM).toDouble())
        /**
         * The displayed square (halo cropped), for the basemap snapshot and the rebuild check.
         * Exact Web Mercator edges: rows are linear in Mercator y, not in latitude, and the
         * snapshot is matched to the texture texel for texel (a linear guess is ~1 m off).
         */
        val north = latOfEdge(mosaic, mosaic.haloPx.toDouble())
        val south = latOfEdge(mosaic, (mosaic.haloPx + ih).toDouble())
        val west = lngOfEdge(mosaic, mosaic.haloPx.toDouble())
        val east = lngOfEdge(mosaic, (mosaic.haloPx + iw).toDouble())

        fun contains(lat: Double, lng: Double) = lat in south..north && lng in west..east

        /**
         * Elevation in metres at a position, bilinear between cell centres, or null outside the
         * displayed square: the halo is sampled for the analysis but not drawn, so a marker
         * there would float in the air.
         */
        fun elevationAt(lat: Double, lng: Double): Double? {
            if (!contains(lat, lng)) return null
            val g = mosaic.grid
            val n = DemTileStore.TILE.toDouble() * (1 shl mosaic.zoom)
            val gx = MapCamera.mercatorX(lng) * n - mosaic.tileX0 * DemTileStore.TILE - 0.5
            val gy = MapCamera.mercatorY(lat) * n - mosaic.tileY0 * DemTileStore.TILE - 0.5
            val x0 = floor(gx).toInt().coerceIn(0, g.w - 1); val y0 = floor(gy).toInt().coerceIn(0, g.h - 1)
            val x1 = (x0 + 1).coerceAtMost(g.w - 1); val y1 = (y0 + 1).coerceAtMost(g.h - 1)
            val fx = (gx - x0).coerceIn(0.0, 1.0); val fy = (gy - y0).coerceIn(0.0, 1.0)
            val top = g[x0, y0] * (1 - fx) + g[x1, y0] * fx
            val bottom = g[x0, y1] * (1 - fx) + g[x1, y1] * fx
            return top * (1 - fy) + bottom * fy
        }

        // At most two textures (a 2048-square one is 16 MB): the one on screen and the last.
        private val baked = LinkedHashMap<String, IntArray>()

        /**
         * The texture for a colouring, the user's layer toggles, and the 2D map's basemap when
         * one was rendered (MAP mode drapes it; without it the neutral relief is the base).
         */
        @Synchronized
        fun texture(
            mode: TerrainTextures.Mode,
            layers: TerrainTextures.Layers = TerrainTextures.Layers(),
            basemap: IntArray? = null,
        ): IntArray {
            val key = "$mode:$layers:${basemap?.let { System.identityHashCode(it) } ?: 0}"
            baked[key]?.let { return it }
            val g = if (basemap == null) ground else TerrainTextures.Ground(
                ground.mosaic, ground.scores, ground.scoreSize, ground.lines, ground.exaggeration, basemap)
            val px = TerrainTextures.bake(g, mode, textureSize, layers)
            while (baked.size >= 2) baked.remove(baked.keys.first())
            baked[key] = px
            return px
        }

        fun describe(): String =
            "HD 3D · %.1f m elevation · %.1f × %.1f km · %.0f km of creeks & drains"
                .format(mosaic.grid.cellSizeM, widthM / 1000, heightM / 1000, creekKm)
    }

    /**
     * Everything but tile loading, which needs Android. Habitat scores come from
     * [SuitabilityRasterizer.scoreGrid] at one pixel per elevation cell: the function the 2D
     * heatmap draws, so the two views cannot disagree.
     */
    fun build(
        mosaic: DemTileStore.Mosaic,
        weights: DoubleArray = GinsengSuitability.PRIOR_WEIGHTS,
        exaggeration: Float = EXAGGERATION,
    ): Scene {
        val interior = max(mosaic.grid.w, mosaic.grid.h) - 2 * mosaic.haloPx
        val scores = SuitabilityRasterizer.scoreGrid(mosaic, interior, TPI_RADIUS_M, weights)
        val hydro = Hydrology.of(mosaic.grid)
        val lines = hydro.lines(Hydrology.Kind.DRAINAGE)
        val ground = TerrainTextures.Ground(mosaic, scores, interior, lines, exaggeration.toDouble())
        val centreLat = (mosaic.northLat + mosaic.southLat) / 2
        val centreLng = (mosaic.westLon + mosaic.eastLon) / 2
        val cam = MapCamera(centreLat, centreLng, BUILD_ZOOM, 0.0, 0.0, 1000, 1000)
        val mesh = TerrainMesh.build(mosaic, cam, gridFor(mosaic), exaggeration)
        return Scene(mosaic, ground, mesh, creekKm = interiorLengthM(mosaic, lines) / 1000)
    }

    /**
     * Terrain height for the camera maths, in [MapCamera.project]'s convention: metres above
     * the camera's ground plane at [anchorM], exaggerated as drawn. Flat at the plane outside
     * the square.
     */
    fun heightFn(s: Scene, anchorM: Double): (Double, Double) -> Double =
        { lat, lng -> ((s.elevationAt(lat, lng) ?: anchorM) - anchorM) * EXAGGERATION }

    /** Bounds of [heightFn] over the square, padded: where the pan's ray march starts and stops. */
    fun rangeFor(s: Scene, anchorM: Double): ClosedFloatingPointRange<Double> =
        ((s.mesh.minElevationM - anchorM) * EXAGGERATION - 50.0)..((s.mesh.maxElevationM - anchorM) * EXAGGERATION + 50.0)

    /**
     * Puts the camera back on the ground of [s] without moving the picture, after a gesture
     * or when a new square replaces the old one: [CameraMath.reanchor], then the ground plane
     * moved by the same height (un-exaggerated). Returns the new camera and ground plane, or
     * the inputs unchanged when the camera is already on the ground.
     */
    fun settle(cam: ViewCamera, viewportW: Int, viewportH: Int, s: Scene, anchorM: Double): Pair<ViewCamera, Double> {
        val (next, dh) = CameraMath.reanchor(cam, viewportW, viewportH, heightFn(s, anchorM), rangeFor(s, anchorM))
            ?: return cam to anchorM
        return next to anchorM + dh / EXAGGERATION
    }

    /** Latitude of a cell-row EDGE (row r's top edge is r): rows are linear in Mercator y. */
    fun latOfEdge(m: DemTileStore.Mosaic, row: Double): Double {
        val wy = (m.tileY0 * DemTileStore.TILE + row) / (DemTileStore.TILE.toDouble() * (1 shl m.zoom))
        return Math.toDegrees(atan(sinh(PI * (1.0 - 2.0 * wy))))
    }

    /** Longitude of a cell-column edge. */
    fun lngOfEdge(m: DemTileStore.Mosaic, col: Double): Double =
        (m.tileX0 * DemTileStore.TILE + col) / (DemTileStore.TILE.toDouble() * (1 shl m.zoom)) * 360.0 - 180.0

    /** Channel length inside the displayed square only (the halo is sampled, never shown). */
    fun interiorLengthM(m: DemTileStore.Mosaic, lines: List<Hydrology.Line>): Double {
        val w = m.grid.w; val h = m.grid.h; val halo = m.haloPx
        fun inside(c: Int) = c % w in halo until w - halo && c / w in halo until h - halo
        var total = 0.0
        for (l in lines) for (k in 1 until l.cells.size) {
            val a = l.cells[k - 1]; val b = l.cells[k]
            if (!inside(a) || !inside(b)) continue
            total += kotlin.math.hypot((a % w - b % w).toDouble(), (a / w - b / w).toDouble()) * m.grid.cellSizeM
        }
        return total
    }
}
