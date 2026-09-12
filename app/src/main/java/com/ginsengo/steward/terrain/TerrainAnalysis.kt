package com.ginsengo.steward.terrain

/**
 * The expensive, radius-independent analysis of one DEM mosaic, computed once and reused.
 *
 * Two things here cost real time and neither depends on the camera:
 *
 *  - multiple-flow accumulation for the wetness index, which sorts every cell and walks its
 *    eight neighbours (~224 ms for a 256x256 mosaic on a desktop JVM);
 *  - the summed-area table that makes the topographic position index constant-time.
 *
 * Rebuilding the mesh while the camera moves means building several meshes over the SAME
 * mosaic — a pan of a few hundred metres usually lands on the same elevation tiles. Redoing
 * flow accumulation each time was the difference between a rebuild that fits in a gesture
 * and one that does not, so it is cached against the mosaic that produced it.
 *
 * The cache is deliberately tiny. These arrays are megabytes, the app only ever needs the
 * current viewport and its immediate neighbours, and holding more would trade a stutter for
 * an out-of-memory kill on a cheap handset.
 */
class TerrainAnalysis private constructor(
    val grid: TerrainMath.Grid,
    val summedArea: TerrainMath.SummedArea,
    private val twiOrNull: DoubleArray?,
) {
    val hasWetness: Boolean get() = twiOrNull != null

    /** Wetness index at a cell, or a neutral mid-band value when it was not computed. */
    fun twiAt(x: Int, y: Int): Double =
        twiOrNull?.get(y.coerceIn(0, grid.h - 1) * grid.w + x.coerceIn(0, grid.w - 1)) ?: 8.0

    fun tpi(x: Int, y: Int, radiusCells: Int): Double =
        TerrainMath.tpiFast(grid, summedArea, x, y, radiusCells)

    companion object {
        private const val MAX_CACHED = 3
        private val cache = LinkedHashMap<String, TerrainAnalysis>()

        /**
         * @param withWetness when false, flow accumulation is skipped entirely. The 3D mesh
         *   only needs wetness if it is being tinted by the forecast, and skipping it is the
         *   single biggest saving available for a plain terrain rebuild.
         */
        @Synchronized
        fun of(mosaic: DemTileStore.Mosaic, withWetness: Boolean): TerrainAnalysis {
            val key = "${mosaic.zoom}/${mosaic.tileX0}/${mosaic.tileY0}/" +
                    "${mosaic.tilesX}x${mosaic.tilesY}/${mosaic.haloPx}/w=$withWetness"
            cache[key]?.let {
                // Refresh recency.
                cache.remove(key); cache[key] = it
                return it
            }
            // A cached entry computed WITH wetness satisfies a request without it.
            if (!withWetness) {
                val richer = key.removeSuffix("w=false") + "w=true"
                cache[richer]?.let { return it }
            }

            val g = mosaic.grid
            val analysis = TerrainAnalysis(
                grid = g,
                summedArea = TerrainMath.SummedArea(g),
                twiOrNull = if (withWetness) TerrainMath.topographicWetnessIndex(g) else null,
            )
            cache[key] = analysis
            while (cache.size > MAX_CACHED) {
                val oldest = cache.keys.firstOrNull() ?: break
                cache.remove(oldest)
            }
            return analysis
        }

        @Synchronized
        fun clear() = cache.clear()

        @Synchronized
        fun cachedCount(): Int = cache.size
    }
}
