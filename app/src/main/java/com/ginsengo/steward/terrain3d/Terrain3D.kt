package com.ginsengo.steward.terrain3d

import com.ginsengo.steward.terrain.DemTileStore
import com.ginsengo.steward.terrain.GinsengSuitability
import com.ginsengo.steward.terrain.Hydrology
import com.ginsengo.steward.terrain.SuitabilityRasterizer
import kotlin.math.cos
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
        private val baked = HashMap<TerrainTextures.Mode, IntArray>()

        @Synchronized
        fun texture(mode: TerrainTextures.Mode): IntArray =
            baked.getOrPut(mode) { TerrainTextures.bake(ground, mode, textureSize) }

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
