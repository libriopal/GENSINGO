package com.ginsengo.steward.research

import com.ginsengo.steward.geo.Projection
import com.ginsengo.steward.learn.FindLearner
import com.ginsengo.steward.prospect.Prospects
import com.ginsengo.steward.terrain.DemTileStore
import com.ginsengo.steward.terrain.GinsengSuitability
import com.ginsengo.steward.terrain.Hydrology
import com.ginsengo.steward.terrain.TerrainMath
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sinh
import kotlin.math.tan
import kotlin.random.Random

/**
 * The terrain model run over the whole search radius, on the device, from cached elevation.
 *
 * This is the only thing in the app allowed to produce a suggested location. The research
 * model downstream sees these candidates by ID and may rank and explain them; it cannot add
 * one. The previous research engine placed four "hotspots" at fixed offsets from wherever
 * the caller stood (`lat + 0.0482`), and clamped them into a North Carolina box, so a user
 * in Pennsylvania was sent ~345 km. Every candidate here is a place whose terrain was
 * actually computed, and it carries the numbers that made it a candidate.
 *
 * Factors come from [GinsengSuitability.factorValues], the same function the heatmap and
 * the learner use, at one resolution for the whole scan (DEM zoom [zoom]) so finds and
 * background are described identically.
 */
class RadiusScan private constructor(
    val centerLat: Double,
    val centerLng: Double,
    val radiusM: Double,
    val zoom: Int,
    val cellSizeM: Double,
    private val grid: TerrainMath.Grid,
    private val sat: TerrainMath.SummedArea,
    private val twi: DoubleArray,
    private val tileX0: Int,
    private val tileY0: Int,
    val tpiRadiusCells: Int,
    val tilesLoaded: Int,
    val tilesRequested: Int,
) {
    data class Candidate(
        /** Stable across scans of the same ground: the DEM cell it was picked at. */
        val key: String,
        val lat: Double,
        val lng: Double,
        val distanceM: Double,
        val bearingDeg: Double,
        /** Neighbourhood-mean score under the weights the scan was ranked with. */
        val score: Double,
        val factors: DoubleArray,
        val elevationM: Double,
        val slopeDeg: Double,
        val aspectDeg: Double,
        /** Nearest creek (20 ha or more drains through it) within [WATER_SEARCH_M], or null. */
        val water: Water? = null,
    )

    /** Where the nearest creek is from a candidate, traced from the same elevation. */
    data class Water(
        val distanceM: Double,
        /** From the candidate towards the creek, degrees true. */
        val bearingDeg: Double,
        /** Candidate elevation minus the creek's: positive means the creek is below. */
        val dropM: Double,
        val kind: Hydrology.Kind,
    )

    /**
     * Channel class per cell over the whole scan, traced once (Hydrology) and kept as one byte
     * per cell; the full analysis is ~38 MB while it runs and is dropped straight after.
     */
    private val channels: ByteArray by lazy { Hydrology.of(grid).kindMap() }

    fun waterAt(x: Int, y: Int): Water? {
        val minClass = (Hydrology.Kind.CREEK.ordinal + 1).toByte()
        val maxR = (WATER_SEARCH_M / cellSizeM).toInt()
        val i = Hydrology.nearest(grid.w, grid.h, x, y, maxR) { channels[it] >= minClass }
        if (i < 0) return null
        val cx = i % grid.w; val cy = i / grid.w
        val lat = latOfRow(y.toDouble()); val lng = lngOfCol(x.toDouble())
        val wLat = latOfRow(cy.toDouble()); val wLng = lngOfCol(cx.toDouble())
        return Water(
            distanceM = Prospects.distanceMetres(lat, lng, wLat, wLng),
            bearingDeg = Prospects.bearingTrue(lat, lng, wLat, wLng),
            dropM = (grid[x, y] - grid[cx, cy]).toDouble(),
            kind = Hydrology.Kind.entries[channels[i] - 1],
        )
    }

    private val worldPx: Double = Projection.worldPx(zoom, DemTileStore.TILE)

    /** Geographic position of a cell centre. Exact Web Mercator, not linear interpolation. */
    fun latOfRow(y: Double): Double = Projection.lat((tileY0 * DemTileStore.TILE + y + 0.5) / worldPx)

    fun lngOfCol(x: Double): Double = Projection.lng((tileX0 * DemTileStore.TILE + x + 0.5) / worldPx)

    private fun colOf(lng: Double): Double = Projection.x(lng) * worldPx - tileX0 * DemTileStore.TILE - 0.5

    private fun rowOf(lat: Double): Double = Projection.y(lat) * worldPx - tileY0 * DemTileStore.TILE - 0.5

    /** Six factor values at a cell. Edge cells are clamped one in, as the rasteriser does. */
    fun factorsAtCell(x: Int, y: Int): DoubleArray {
        val xi = x.coerceIn(1, grid.w - 2)
        val yi = y.coerceIn(1, grid.h - 2)
        val (slope, aspect) = TerrainMath.slopeAspect(grid, xi, yi)
        return GinsengSuitability.factorValues(
            heatLoadRaw = TerrainMath.heatLoadIndex(latOfRow(yi.toDouble()), slope, aspect),
            tpiMeters = TerrainMath.tpiFast(grid, sat, xi, yi, tpiRadiusCells),
            twi = twi[yi * grid.w + xi],
            slopeDeg = slope,
            curvature = TerrainMath.profileCurvature(grid, xi, yi),
            elevationM = grid[xi, yi].toDouble(),
        )
    }

    /** Factors at a position, or null outside the scanned ground. */
    fun factorsAt(lat: Double, lng: Double): DoubleArray? {
        val x = colOf(lng).roundToInt()
        val y = rowOf(lat).roundToInt()
        if (x !in 1 until grid.w - 1 || y !in 1 until grid.h - 1) return null
        return factorsAtCell(x, y)
    }

    fun inRadius(lat: Double, lng: Double): Boolean =
        Prospects.distanceMetres(centerLat, centerLng, lat, lng) <= radiusM

    /**
     * Picks up to [count] candidates inside the radius.
     *
     * A candidate is ranked by the MEAN score over its neighbourhood ([smoothCells] each
     * side), not by one cell: a single bright pixel is usually a DEM artefact, and a digger
     * walks to an area, not to a 30 m square. Picks are at least [minSeparationM] apart so
     * the list is ten places, not one cove ten times.
     *
     * @param exclude  returns true for places that must not be suggested (e.g. inside a
     *                 protected area); they are skipped rather than ranked last, because a
     *                 suggestion is a recommendation to go there.
     */
    fun candidates(
        weights: DoubleArray,
        count: Int = 10,
        minSeparationM: Double = 800.0,
        smoothCells: Int = 2,
        exclude: (lat: Double, lng: Double) -> Boolean = { _, _ -> false },
    ): List<Candidate> {
        val w = grid.w; val h = grid.h
        val score = FloatArray(w * h) { Float.NaN }
        val m = tpiRadiusCells + 2
        for (y in m until h - m) {
            val lat = latOfRow(y.toDouble())
            for (x in m until w - m) {
                if (!inRadius(lat, lngOfCol(x.toDouble()))) continue
                score[y * w + x] = GinsengSuitability.weighted(factorsAtCell(x, y), weights).toFloat()
            }
        }
        // Neighbourhood mean of the per-cell scores, NaN-aware. Cells with no fair mean stay
        // NaN; ranking is one primitive sort (see TerrainMath.descendingOrder for why).
        val smooth = FloatArray(w * h) { Float.NaN }
        val full = (2 * smoothCells + 1) * (2 * smoothCells + 1)
        for (y in m until h - m) for (x in m until w - m) {
            if (score[y * w + x].isNaN()) continue
            var s = 0.0; var n = 0
            for (dy in -smoothCells..smoothCells) for (dx in -smoothCells..smoothCells) {
                val v = score[(y + dy) * w + (x + dx)]
                if (!v.isNaN()) { s += v; n++ }
            }
            if (n * 2 < full) continue   // mostly outside the radius; not a fair mean
            smooth[y * w + x] = (s / n).toFloat()
        }
        // NaN sorts highest in Float.compare order, so give unranked cells -Inf instead.
        val rankable = FloatArray(w * h) { if (smooth[it].isNaN()) Float.NEGATIVE_INFINITY else smooth[it] }
        val order = TerrainMath.descendingOrder(rankable)

        val picked = ArrayList<Candidate>()
        for (idx in order) {
            if (picked.size >= count || rankable[idx] == Float.NEGATIVE_INFINITY) break
            val x = idx % w; val y = idx / w
            val lat = latOfRow(y.toDouble()); val lng = lngOfCol(x.toDouble())
            if (picked.any { Prospects.distanceMetres(it.lat, it.lng, lat, lng) < minSeparationM }) continue
            if (exclude(lat, lng)) continue
            val (slope, aspect) = TerrainMath.slopeAspect(grid, x, y)
            picked += Candidate(
                key = "z$zoom:${tileX0 * DemTileStore.TILE + x}:${tileY0 * DemTileStore.TILE + y}",
                lat = lat, lng = lng,
                distanceM = Prospects.distanceMetres(centerLat, centerLng, lat, lng),
                bearingDeg = Prospects.bearingTrue(centerLat, centerLng, lat, lng),
                score = smooth[idx].toDouble(),
                factors = factorsAtCell(x, y),
                elevationM = grid[x, y].toDouble(),
                slopeDeg = slope, aspectDeg = aspect,
                water = waterAt(x, y),
            )
        }
        return picked
    }

    /**
     * Random cells inside the radius, described exactly as finds are. The learner's
     * background. Seeded from the scan centre so the same ground gives the same sample.
     */
    fun background(n: Int = 600, seed: Long = seedFor(centerLat, centerLng)): List<FindLearner.Sample> {
        val r = Random(seed)
        val out = ArrayList<FindLearner.Sample>(n)
        val m = tpiRadiusCells + 2
        var tries = 0
        while (out.size < n && tries < n * 20) {
            tries++
            val x = m + r.nextInt((grid.w - 2 * m).coerceAtLeast(1))
            val y = m + r.nextInt((grid.h - 2 * m).coerceAtLeast(1))
            val lat = latOfRow(y.toDouble()); val lng = lngOfCol(x.toDouble())
            if (!inRadius(lat, lng)) continue
            out += FindLearner.Sample(factorsAtCell(x, y), lat, lng)
        }
        return out
    }

    /**
     * How far apart two places must be before their terrain stops being the same terrain:
     * the shortest lag at which no factor's correlation between point pairs that far apart
     * is still above [threshold]. Measured on this scan's own ground, because it is a
     * property of the landscape (broad plateaus decorrelate slowly, dissected coves fast).
     *
     * The learner blocks its held-out test at this distance. Blocking at a fixed 200 m let
     * a "held-out" find share the autocorrelated terrain of a training find 250 m away, so
     * the gate could reward learning a PLACE rather than a habitat. That objection came from
     * the Phase 7 independent critic; the leakage experiment in LearnerLeakageTest measured it.
     *
     * Estimated from purpose-drawn pairs (a random cell and a partner exactly one lag away)
     * rather than from random background pairs, which at a 16 km radius leave the short lags
     * that matter almost empty.
     */
    fun correlationRangeM(pairsPerLag: Int = 400, threshold: Double = 0.2, seed: Long = 5L): Double {
        val r = Random(seed)
        val m = tpiRadiusCells + 2
        var lag = LAG_STEP_M
        while (lag <= MAX_BLOCK_M) {
            val a = ArrayList<DoubleArray>(pairsPerLag)
            val b = ArrayList<DoubleArray>(pairsPerLag)
            var tries = 0
            while (a.size < pairsPerLag && tries < pairsPerLag * 30) {
                tries++
                val x = m + r.nextInt((grid.w - 2 * m).coerceAtLeast(1))
                val y = m + r.nextInt((grid.h - 2 * m).coerceAtLeast(1))
                val lat = latOfRow(y.toDouble()); val lng = lngOfCol(x.toDouble())
                if (!inRadius(lat, lng)) continue
                val theta = r.nextDouble() * 2 * PI
                val lat2 = lat + lag * kotlin.math.cos(theta) / 111_320.0
                val lng2 = lng + lag * kotlin.math.sin(theta) / (111_320.0 * cos(Math.toRadians(lat)))
                val f2 = factorsAt(lat2, lng2) ?: continue
                a += factorsAtCell(x, y); b += f2
            }
            if (a.size >= 50) {
                val worst = (0 until 6).maxOf { k -> pearson(a, b, k) }
                if (worst < threshold) return lag.coerceAtLeast(MIN_BLOCK_M)
            }
            lag += LAG_STEP_M
        }
        return MAX_BLOCK_M
    }

    private fun pearson(a: List<DoubleArray>, b: List<DoubleArray>, k: Int): Double {
        val n = a.size.toDouble()
        val ma = a.sumOf { it[k] } / n; val mb = b.sumOf { it[k] } / n
        var sab = 0.0; var saa = 0.0; var sbb = 0.0
        for (i in a.indices) {
            val da = a[i][k] - ma; val db = b[i][k] - mb
            sab += da * db; saa += da * da; sbb += db * db
        }
        return if (saa <= 1e-12 || sbb <= 1e-12) 0.0 else sab / kotlin.math.sqrt(saa * sbb)
    }

    companion object {
        /** DEM zoom for the scan: ~31 m cells at 36 N, the whole radius in <= 6x6 tiles. */
        const val SCAN_ZOOM = 12
        const val RADIUS_M = 16_093.44   // 10 statute miles
        /** Position-on-slope scale for a landscape scan, matching the heatmap at this zoom. */
        const val TPI_RADIUS_M = 700.0
        /** How far to look for a creek from a candidate. */
        const val WATER_SEARCH_M = 2_000.0
        const val LAG_STEP_M = 100.0
        const val MIN_BLOCK_M = 200.0
        const val MAX_BLOCK_M = 3_000.0

        fun seedFor(lat: Double, lng: Double): Long =
            (floor(lat * 100).toLong() * 73_856_093L) xor (floor(lng * 100).toLong() * 19_349_663L)

        /** Bounds to request tiles for: the radius plus a TPI-sized margin. */
        fun bounds(lat: Double, lng: Double, radiusM: Double = RADIUS_M): DoubleArray {
            val marginM = radiusM + TPI_RADIUS_M * 1.5
            val dLat = marginM / 111_320.0
            val dLng = marginM / (111_320.0 * cos(Math.toRadians(lat)))
            return doubleArrayOf(lat + dLat, lng - dLng, lat - dLat, lng + dLng) // N, W, S, E
        }

        /**
         * Builds a scan from a mosaic. Runs the expensive whole-grid operators once:
         * multiple-flow wetness and the summed-area table.
         */
        fun of(
            mosaic: DemTileStore.Mosaic,
            centerLat: Double,
            centerLng: Double,
            radiusM: Double = RADIUS_M,
        ): RadiusScan {
            val g = mosaic.grid
            return RadiusScan(
                centerLat = centerLat, centerLng = centerLng, radiusM = radiusM,
                zoom = mosaic.zoom, cellSizeM = g.cellSizeM,
                grid = g,
                sat = TerrainMath.SummedArea(g),
                twi = TerrainMath.topographicWetnessIndex(g),
                tileX0 = mosaic.tileX0, tileY0 = mosaic.tileY0,
                tpiRadiusCells = (TPI_RADIUS_M / g.cellSizeM).roundToInt().coerceIn(1, 60),
                tilesLoaded = mosaic.tilesLoaded, tilesRequested = mosaic.tilesRequested,
            )
        }

        /** Distance band for the prompt: coarse on purpose (privacy). */
        fun distanceBand(m: Double): String = when {
            m < 1_000 -> "under 1 km"
            m < 3_000 -> "1-3 km"
            m < 6_000 -> "3-6 km"
            m < 10_000 -> "6-10 km"
            else -> "10-16 km"
        }
    }
}
