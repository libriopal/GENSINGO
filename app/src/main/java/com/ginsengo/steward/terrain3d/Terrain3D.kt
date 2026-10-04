package com.ginsengo.steward.terrain3d

import com.ginsengo.steward.terrain.DemTileStore
import com.ginsengo.steward.terrain.GinsengSuitability
import com.ginsengo.steward.terrain.Hydrology
import com.ginsengo.steward.terrain.SuitabilityRasterizer
import com.ginsengo.steward.geo.Projection
import com.ginsengo.steward.perf.MemoryBudget
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

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

    /** Names in the memory budget's counters (A13). */
    const val BUDGET_SCENE = "scene"
    const val BUDGET_TEXTURES = "textures"

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
            val n = Projection.worldPx(mosaic.zoom, DemTileStore.TILE)
            val gx = Projection.x(lng) * n - mosaic.tileX0 * DemTileStore.TILE - 0.5
            val gy = Projection.y(lat) * n - mosaic.tileY0 * DemTileStore.TILE - 0.5
            val x0 = floor(gx).toInt().coerceIn(0, g.w - 1); val y0 = floor(gy).toInt().coerceIn(0, g.h - 1)
            val x1 = (x0 + 1).coerceAtMost(g.w - 1); val y1 = (y0 + 1).coerceAtMost(g.h - 1)
            val fx = (gx - x0).coerceIn(0.0, 1.0); val fy = (gy - y0).coerceIn(0.0, 1.0)
            val top = g[x0, y0] * (1 - fx) + g[x1, y0] * fx
            val bottom = g[x0, y1] * (1 - fx) + g[x1, y1] * fx
            return top * (1 - fy) + bottom * fy
        }

        // Baked textures (a 2048-square one is 16 MB). Without a budget: the one on screen and the
        // last. With one (the app, A13): as many as the shared ceiling allows, the one on screen
        // pinned, the rest evicted least-recently-used along with every other cache in the app.
        private val baked = LinkedHashMap<String, IntArray>()
        private var budget: MemoryBudget? = null
        private var onScreen: String? = null
        private val id = System.identityHashCode(this)
        private val evictTexture = MemoryBudget.Owner { k -> synchronized(this) { baked.remove((k as Pair<*, *>).second) } }

        /** Puts this scene under the app's memory budget: its own arrays pinned, its textures evictable. */
        @Synchronized
        fun useBudget(b: MemoryBudget) {
            budget = b
            val bytes = mosaic.grid.z.size * 4L + mesh.vertices.size * 4L + mesh.indices.size * 4L + (ground.scores?.size ?: 0) * 8L
            b.put(BUDGET_SCENE, { }, id, bytes, pinned = true)
            for ((k, px) in baked) b.put(BUDGET_TEXTURES, evictTexture, id to k, px.size * 4L, pinned = k == onScreen)
        }

        /** This scene is gone from the screen: the budget forgets it and its textures. */
        @Synchronized
        fun release() {
            val b = budget ?: return
            b.remove(BUDGET_SCENE, id)
            for (k in baked.keys) b.remove(BUDGET_TEXTURES, id to k)
            budget = null
        }

        /**
         * The texture for a colouring, the user's layer toggles, and the 2D map's basemap when
         * one was rendered (MAP mode drapes it; without it the neutral relief is the base). The
         * texture returned is the one going on screen: it is pinned, the previous one released.
         */
        @Synchronized
        fun texture(
            mode: TerrainTextures.Mode,
            layers: TerrainTextures.Layers = TerrainTextures.Layers(),
            basemap: IntArray? = null,
        ): IntArray {
            val key = "$mode:$layers:${basemap?.let { System.identityHashCode(it) } ?: 0}"
            val px = baked[key] ?: run {
                val g = if (basemap == null) ground else TerrainTextures.Ground(
                    ground.mosaic, ground.scores, ground.scoreSize, ground.lines, ground.exaggeration, basemap)
                val fresh = TerrainTextures.bake(g, mode, textureSize, layers)
                if (budget == null) while (baked.size >= 2) baked.remove(baked.keys.first())
                baked[key] = fresh
                budget?.put(BUDGET_TEXTURES, evictTexture, id to key, fresh.size * 4L, pinned = true)
                fresh
            }
            val b = budget
            if (b != null && onScreen != key) {
                onScreen?.let { b.pin(BUDGET_TEXTURES, id to it, false) }
                b.pin(BUDGET_TEXTURES, id to key, true)
                b.touch(BUDGET_TEXTURES, id to key)
            }
            onScreen = key
            return px
        }

        /** How many baked textures this scene holds now (the budget's witness in tests). */
        @Synchronized fun texturesHeld(): Int = baked.size

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
    fun settle(cam: CameraState, viewportW: Int, viewportH: Int, s: Scene, anchorM: Double): Pair<CameraState, Double> {
        val (next, dh) = CameraMath.reanchor(cam, viewportW, viewportH, heightFn(s, anchorM), rangeFor(s, anchorM))
            ?: return cam to anchorM
        return next to anchorM + dh / EXAGGERATION
    }

    /** Latitude of a cell-row EDGE (row r's top edge is r): rows are linear in Mercator y. */
    fun latOfEdge(m: DemTileStore.Mosaic, row: Double): Double =
        Projection.lat((m.tileY0 * DemTileStore.TILE + row) / Projection.worldPx(m.zoom, DemTileStore.TILE))

    /** Longitude of a cell-column edge. */
    fun lngOfEdge(m: DemTileStore.Mosaic, col: Double): Double =
        Projection.lng((m.tileX0 * DemTileStore.TILE + col) / Projection.worldPx(m.zoom, DemTileStore.TILE))

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
