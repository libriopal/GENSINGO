package com.ginsengo.steward.terrain

import com.ginsengo.steward.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * Tests of the surface the map actually DRAWS, not only of the model it is supposed to draw.
 *
 * Commit 7a3656d replaced the rasteriser's scoring with a matrix that computed position on
 * slope (TPI) and wetness (TWI) and then never used them, and estimated canopy from aspect.
 * Every model test still passed, because they tested GinsengSuitability, which the map had
 * stopped calling. These tests go through SuitabilityRasterizer.scoreGrid, the exact per-pixel
 * computation rasterise() colours.
 */
class DrawnSurfaceTest {

    private val mosaic = Fixtures.mosaic("scan_boone_z12.bin")
    private val prior = GinsengSuitability.PRIOR_WEIGHTS

    private fun surface(w: DoubleArray) = SuitabilityRasterizer.scoreGrid(mosaic, 96, 700.0, w)

    private fun meanAbsDiff(a: DoubleArray, b: DoubleArray) = a.indices.sumOf { abs(a[it] - b[it]) } / a.size

    /** Zeroing any one factor's weight must visibly change the drawn surface. */
    @Test
    fun everyFactorReachesThePixels() {
        val base = surface(prior)
        for (f in GinsengSuitability.Factor.entries) {
            val w = prior.copyOf().also { it[f.ordinal] = 0.0 }
            val d = meanAbsDiff(base, surface(w))
            assertTrue("${f.name} does not reach the drawn surface (mean diff $d)", d > 1e-3)
        }
    }

    /** Each factor alone draws a non-flat surface: none of them is a constant stand-in. */
    @Test
    fun eachFactorAloneIsAMeasuredSurface() {
        for (f in GinsengSuitability.Factor.entries) {
            val s = surface(DoubleArray(6) { if (it == f.ordinal) 1.0 else 0.0 })
            val spread = s.max() - s.min()
            assertTrue("${f.name} is flat on real terrain (spread $spread)", spread > 0.05)
        }
    }

    /**
     * The non-monotonic guard, on the DRAWN surface: a flat, saturated valley floor must not
     * outscore the lower slopes above it. The obvious "wetter is better" map paints creek
     * bottoms brightest, which is the one place a digger should not be sent.
     */
    @Test
    fun theDrawnSurfaceTurnsOverAtTheWetEnd() {
        val n = 256
        val cell = 7.7
        val z = FloatArray(n * n) { i ->
            val x = i % n
            val d = abs(x - n / 2)
            if (d < 8) 300f else (300 + (d - 8) * cell * 0.34).toFloat()   // ~19 degree side slopes
        }
        val m = DemTileStore.Mosaic(TerrainMath.Grid(n, n, z, cell), 14, 4474, 6423, 1, 1, 0, 1, 1)
        val out = 128
        val s = SuitabilityRasterizer.scoreGrid(m, out, 120.0, prior)
        fun colMean(x0: Int, x1: Int): Double {
            var t = 0.0; var c = 0
            for (y in 16 until out - 16) for (x in x0..x1) { t += s[y * out + x]; c++ }
            return t / c
        }
        val floor = colMean(out / 2 - 2, out / 2 + 1)
        val lowerSlope = colMean(out / 2 + 8, out / 2 + 20)
        assertTrue("valley floor $floor outscored the lower slope $lowerSlope", floor < lowerSlope)
    }
}

/** The 3D mesh and the 2D heatmap must be the same ramp, stop for stop. */
class ShaderRampParityTest {

    @Test
    fun meshRampEqualsHeatmapRamp() {
        val src = com.ginsengo.steward.terrain3d.TerrainShaders.FRAGMENT
        val fn = src.substring(src.indexOf("vec3 suitabilityTint"), src.indexOf("void main()"))
        val stops = Regex("""vec3\(([\d.]+),\s*([\d.]+),\s*([\d.]+)\);\s*//\s*#([0-9A-Fa-f]{6})""").findAll(fn).toList()
        assertEquals("stop count", SuitabilityRasterizer.RAMP.size, stops.size)
        stops.forEachIndexed { i, m ->
            val rgb = SuitabilityRasterizer.RAMP[i]
            val want = intArrayOf((rgb shr 16) and 255, (rgb shr 8) and 255, rgb and 255)
            assertEquals("hex comment $i", "%06X".format(rgb), m.groupValues[4].uppercase())
            for (c in 0..2) {
                val got = m.groupValues[c + 1].toDouble() * 255
                assertEquals("stop $i channel $c", want[c].toDouble(), got, 1.0)
            }
        }
    }
}

class ConvertersTest {

    private val c = com.ginsengo.steward.data.db.Converters()

    @Test
    fun multiWordNamesSurviveARoundTrip() {
        val names = listOf("Black cohosh", "Sugar maple", "Jack-in-the-pulpit")
        assertEquals(names, c.toList(c.fromList(names)))
    }

    /** Rows written by the field-tested build (schema 1) used U+001F. They must still read. */
    @Test
    fun fieldTestedBuildRowsStillRead() {
        assertEquals(listOf("Sugar maple", "Tulip poplar"), c.toList("Sugar maple\u001FTulip poplar"))
        assertEquals(listOf("Black cohosh"), c.toList("Black cohosh"))
        assertEquals(emptyList<String>(), c.toList(""))
    }

    /** The separator in the source must be the escape, which no editor can turn into a space. */
    @Test
    fun theSeparatorIsWrittenAsAnEscapeInSource() {
        val src = File("src/main/java/com/ginsengo/steward/data/db/Entities.kt").readText()
        assertTrue(src.contains("SEPARATOR = \"\\u001F\""))
        assertEquals("\u001F", com.ginsengo.steward.data.db.Converters.SEPARATOR)
    }
}

/** The hand-written migration SQL is exactly what Room generates for schema 5. */
class MigrationSqlTest {

    @Test
    fun migrationSqlEqualsRoomsOwnCreateSql() {
        val json = kotlinx.serialization.json.Json.parseToJsonElement(
            File("schemas/com.ginsengo.steward.data.db.AppDatabase/5.json").readText()
        ).let { it as kotlinx.serialization.json.JsonObject }
        val entities = (json["database"] as kotlinx.serialization.json.JsonObject)["entities"] as kotlinx.serialization.json.JsonArray
        val room = ArrayList<String>()
        for (e in entities) {
            e as kotlinx.serialization.json.JsonObject
            val table = (e["tableName"] as kotlinx.serialization.json.JsonPrimitive).content
            if (table in setOf("ginseng_patches", "habitat_readings")) continue
            room += (e["createSql"] as kotlinx.serialization.json.JsonPrimitive).content.replace("\${TABLE_NAME}", table)
            (e["indices"] as? kotlinx.serialization.json.JsonArray)?.forEach { ix ->
                room += ((ix as kotlinx.serialization.json.JsonObject)["createSql"] as kotlinx.serialization.json.JsonPrimitive)
                    .content.replace("\${TABLE_NAME}", table)
            }
        }
        assertEquals(room.sorted(), com.ginsengo.steward.data.db.AppDatabase.SCHEMA_5_CREATE.sorted())
    }
}

class OfflineAreaTest {

    @Test
    fun theOfflineDownloadIsBounded() {
        val tiles = com.ginsengo.steward.ui.OfflineArea.demTiles(35.55, -82.95)
        assertEquals(tiles.size, tiles.toSet().size)
        assertTrue("${tiles.size} tiles", tiles.size in 60..400)
        assertTrue(tiles.any { it.first == 12 } && tiles.any { it.first == 15 })
    }
}

/** A tilted view's footprint must never make the heatmap vanish (device run, Phase 7). */
class ZoomFittingTest {

    @Test
    fun aTiltedViewDropsResolutionInsteadOfDrawingNothing() {
        // ~8 km wide x ~21 km deep: MapLibre's maximum 60-degree tilt at camera zoom 14 on a
        // portrait phone (the default 50 degrees rotated 45 degrees is similar: ~9 x 9 tiles).
        // First version of this test used ~3 x 7.5 km, from arithmetic that scaled the
        // footprint up with zoom instead of down; that view fits, and the test failed on it.
        val n = 35.65; val s = 35.46; val w = -83.04; val e = -82.955
        val z = DemTileStore.zoomFitting(n, w, s, e, preferred = 14)
        assertTrue("fell to $z", z in 10..13)
        val nx = DemTileStore.lonToTileX(e, z) - DemTileStore.lonToTileX(w, z) + 3
        val ny = DemTileStore.latToTileY(s, z) - DemTileStore.latToTileY(n, z) + 3
        assertTrue(nx * ny <= DemTileStore.MAX_TILES)
    }

    @Test
    fun aViewThatFitsKeepsFullResolution() {
        assertEquals(15, DemTileStore.zoomFitting(35.565, -83.0, 35.555, -82.99, preferred = 15))
    }
}
