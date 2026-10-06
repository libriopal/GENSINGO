package com.ginsengo.steward.terrain3d

import com.ginsengo.steward.Fixtures
import com.ginsengo.steward.geo.Projection
import com.ginsengo.steward.research.RadiusScan
import com.ginsengo.steward.terrain.DemTileStore
import com.ginsengo.steward.terrain.GinsengSuitability
import com.ginsengo.steward.terrain.SuitabilityRasterizer
import com.ginsengo.steward.terrain.TerrainMath
import com.ginsengo.steward.ui.Gen
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream
import kotlin.math.sqrt

/**
 * B4 and B5: missing elevation is a hole, never ground, and it looks unknown, never weak.
 *
 * The fixture is real Boone terrain with a block of cells declared missing and filled the way
 * the store fills a missing tile (edge extension: a flat plateau). Every consumer is asked about
 * that block: the mesh, the marker height, the score, the texture, the ranking, the learner's
 * background. Before B4 each of them treated the plateau as ground.
 */
class NoDataTest {

    private val halo = 32
    // The missing block, in mosaic cells: [x0, x1) × [y0, y1), well inside the interior.
    private val bx0 = 110; private val bx1 = 150; private val by0 = 90; private val by1 = 130

    private fun fixture(): TerrainMath.Grid {
        val stream = javaClass.getResourceAsStream("/terrain_boone_z15.bin")
        assertNotNull("missing terrain fixture", stream)
        DataInputStream(stream!!.buffered()).use { d ->
            val magic = ByteArray(8); d.readFully(magic)
            val w = d.readInt(); val h = d.readInt()
            return TerrainMath.Grid(w, h, FloatArray(w * h) { d.readShort().toFloat() }, 3.86)
        }
    }

    private fun inBlock(x: Int, y: Int) = x in bx0 until bx1 && y in by0 until by1

    /** The fixture with the block missing: masked, and filled flat from its west edge as the store would. */
    private fun holed(): DemTileStore.Mosaic {
        val g = fixture()
        val mask = BooleanArray(g.w * g.h)
        for (y in by0 until by1) for (x in bx0 until bx1) {
            mask[y * g.w + x] = true
            g.z[y * g.w + x] = g.z[y * g.w + bx0 - 1]
        }
        return DemTileStore.Mosaic(g, 15, 8950, 12844, 1, 1, halo, 1, 1, noData = mask)
    }

    private val buildCamera = MapCamera(36.2, -81.67, Terrain3D.BUILD_ZOOM, 0.0, 0.0, 1000, 1000)

    /** Vertex (i, j)'s four source cells, as TerrainMesh samples them. */
    private fun sourceCells(m: DemTileStore.Mosaic, n: Int, i: Int, j: Int): List<Pair<Int, Int>> {
        val iw = m.grid.w - 2 * halo; val ih = m.grid.h - 2 * halo
        val gx = halo + i.toDouble() / (n - 1) * iw - 0.5; val gy = halo + j.toDouble() / (n - 1) * ih - 0.5
        val x0 = gx.toInt().coerceIn(0, m.grid.w - 2); val y0 = gy.toInt().coerceIn(0, m.grid.h - 2)
        return listOf(x0 to y0, x0 + 1 to y0, x0 to y0 + 1, x0 + 1 to y0 + 1)
    }

    @Test
    fun theMeshHasAHoleWhereTheElevationIsMissing() {
        val n = 97
        val m = holed()
        val mesh = TerrainMesh.build(m, buildCamera, n, Terrain3D.EXAGGERATION)
        val full = TerrainMesh.build(m.copy(noData = null), buildCamera, n, Terrain3D.EXAGGERATION)
        assertTrue("the hole removed no triangles", mesh.triangleCount < full.triangleCount)
        val onGap = BooleanArray(n * n) { v -> sourceCells(m, n, v % n, v / n).any { (x, y) -> inBlock(x, y) } }
        for (t in mesh.indices.indices step 3) for (k in 0..2) {
            val v = mesh.indices[t + k]
            if (v < n * n) assertFalse("triangle $t stands on missing elevation at vertex $v", onGap[v])
        }
        // Still a sound mesh: walls outward, and nothing open but the wall foot and the hole's rim.
        val r = MeshTopology.check(mesh.indices)
        assertTrue(r.sameDirection.isEmpty() && r.overShared.isEmpty())
        val nearGap = BooleanArray(n * n) { v ->
            val i = v % n; val j = v / n
            (-1..1).any { dj -> (-1..1).any { di -> (i + di) in 0 until n && (j + dj) in 0 until n && onGap[(j + dj) * n + i + di] } }
        }
        fun wall(v: Int) = mesh.vertices[v * TerrainMesh.FLOATS_PER_VERTEX + TerrainMesh.OFF_WALL] == 1f
        for ((a, b) in r.openEdges) {
            val ok = (wall(a) && wall(b)) || (a < n * n && b < n * n && nearGap[a] && nearGap[b])
            assertTrue("open edge $a-$b is neither the wall foot nor the hole's rim: a crack", ok)
        }
        assertTrue("the relief must ignore the flat stand-in", mesh.maxElevationM <= full.maxElevationM)
    }

    @Test
    fun noMarkerStandsOnMissingGround() {
        val m = holed()
        val s = Terrain3D.build(m)
        fun lat(y: Double) = Terrain3D.latOfEdge(m, y)
        fun lng(x: Double) = Terrain3D.lngOfEdge(m, x)
        assertTrue(s.hasHoles)
        assertNull(s.elevationAt(lat((by0 + by1) / 2.0), lng((bx0 + bx1) / 2.0)))
        assertNotNull(s.elevationAt(lat(60.5), lng(60.5)))
        assertFalse(Terrain3D.build(m.copy(noData = null)).hasHoles)
    }

    @Test
    fun missingGroundIsNeitherScoredNorPaintedAsGround() {
        val m = holed()
        val iw = m.grid.w - 2 * halo
        val scores = SuitabilityRasterizer.scoreGrid(m, iw, 300.0)
        for (oy in 0 until iw) for (ox in 0 until iw) {
            val x = ox + halo; val y = oy + halo
            val s = scores[oy * iw + ox]
            if (inBlock(x, y)) assertTrue("score at missing cell ($x, $y) is $s", s.isNaN())
            val clear = x < bx0 - 2 || x >= bx1 + 2 || y < by0 - 2 || y >= by1 + 2
            if (clear) assertFalse("real ground at ($x, $y) lost its score", s.isNaN())
        }
        // The texture: unknown texels are the hatch over the base and nothing else (no habitat,
        // hillshade, contour or channel on them).
        val ground = TerrainTextures.Ground(m, scores, iw, emptyList(), Terrain3D.EXAGGERATION.toDouble())
        val size = 2 * iw
        val px = TerrainTextures.bake(ground, TerrainTextures.Mode.HABITAT, size)
        var checked = 0
        for (ty in 0 until size) for (tx in 0 until size) {
            val gx = halo + (tx + 0.5) / size * iw - 0.5; val gy = halo + (ty + 0.5) / size * iw - 0.5
            val x0 = gx.toInt(); val y0 = gy.toInt()
            if (!(inBlock(x0, y0) && inBlock(x0 + 1, y0 + 1))) continue
            val want = TerrainTextures.over(TerrainTextures.NEUTRAL_RAMP[0], SuitabilityRasterizer.unknownAt(tx, ty)) or (0xFF shl 24)
            assertEquals("texel ($tx, $ty)", want, px[ty * size + tx])
            checked++
        }
        assertTrue(checked > 1000)
    }

    /** The ranking and the learner never use the cells the map shows as unknown. */
    @Test
    fun theRankingNeverSendsYouToMissingGround() {
        val base = Fixtures.mosaic("scan_boone_z12.bin")
        val weights = GinsengSuitability.PRIOR_WEIGHTS
        val lat = (base.northLat + base.southLat) / 2; val lng = (base.westLon + base.eastLon) / 2
        val first = RadiusScan.of(base, lat, lng, 6_000.0).candidates(weights).first()
        val (cx, cy) = cellOf(base, first.key)
        // Declare the ground around the best place missing.
        val w = base.grid.w
        val mask = BooleanArray(w * base.grid.h)
        fun gap(x: Int, y: Int) = x in cx - 20..cx + 20 && y in cy - 20..cy + 20
        for (y in 0 until base.grid.h) for (x in 0 until w) if (gap(x, y)) mask[y * w + x] = true
        val scan = RadiusScan.of(base.copy(noData = mask), lat, lng, 6_000.0)
        val picked = scan.candidates(weights)
        assertTrue(picked.isNotEmpty())
        for (c in picked) {
            val (x, y) = cellOf(base, c.key)
            assertFalse("candidate ${c.key} is on missing ground", gap(x, y))
        }
        val worldPx = Projection.worldPx(base.zoom, DemTileStore.TILE)
        for (s in scan.background(400)) {
            val x = (Projection.x(s.lng) * worldPx - base.tileX0 * DemTileStore.TILE - 0.5).toInt()
            val y = (Projection.y(s.lat) * worldPx - base.tileY0 * DemTileStore.TILE - 0.5).toInt()
            assertFalse("a learner background sample on missing ground", gap(x, y))
        }
        assertNull(scan.factorsAt(first.lat, first.lng))
    }

    private fun cellOf(m: DemTileStore.Mosaic, key: String): Pair<Int, Int> {
        val parts = key.split(":")
        return (parts[1].toInt() - m.tileX0 * DemTileStore.TILE) to (parts[2].toInt() - m.tileY0 * DemTileStore.TILE)
    }

    @Test
    fun aTransparentOrBlackPixelHoldsNoElevation() {
        assertTrue("transparent", DemTileStore.isNoData(0x00808000))
        assertTrue("opaque black decodes to -32768 m", DemTileStore.isNoData(0xFF000000.toInt()))
        assertFalse("sea level", DemTileStore.isNoData(0xFF800000.toInt()))
        // R·256 + G − 32768 = −10,999 m, just above the limit: deep ocean is still elevation.
        val r = (32768 - 10999) / 256; val g = (32768 - 10999) % 256
        assertFalse(DemTileStore.isNoData((0xFF shl 24) or (r shl 16) or (g shl 8)))
    }

    /** The store's assembly: a missing tile and a gap inside a loaded tile are both marked; the gap is filled from its own tile. */
    @Test
    fun theStoreMarksMissingTilesAndGapsInsideATile() {
        val t = DemTileStore.TILE
        val nx = 3; val ny = 1; val w = nx * t
        val z = FloatArray(w * t) { i -> 500f + (i % w) }   // rises eastward, 1 m per cell
        for (r in 100 until 120) for (c in 40 until 60) z[r * w + c] = Float.NaN          // a gap in tile 0
        for (r in 0 until t) for (c in 2 * t until 3 * t) z[r * w + c] = Float.NaN          // tile 2: no real pixel
        val loaded = booleanArrayOf(true, false, true)                                     // tile 1 never loaded
        val mask = DemTileStore.markNoData(z, nx, ny, loaded)
        assertNotNull(mask)
        mask!!
        assertTrue(mask[110 * w + 50] && mask[5 * w + t + 5] && mask[5 * w + 2 * t + 5])
        assertFalse(mask[110 * w + 39] || mask[110 * w + 60] || mask[99 * w + 50])
        assertFalse("a tile with no real pixel is not loaded", loaded[2])
        // Filled from the nearest real cell in the row: west half from x=39, east half from x=60.
        assertEquals(539f, z[110 * w + 45]); assertEquals(560f, z[110 * w + 55])
        assertFalse((0 until w * t).any { z[it].isNaN() && it % w < t })
    }

    @Test
    fun unknownGroundDoesNotLookLikeWeakGround() {
        val size = 16
        val scores = DoubleArray(size * size) { i -> if (i % size < 8) Double.NaN else 0.1 }   // west unknown, east weak
        val px = SuitabilityRasterizer.pixels(scores, size, 0.35)
        val unknown = (0 until size * size).filter { it % size < 8 }
        val weak = (0 until size * size).filter { it % size >= 8 }
        assertTrue("weak ground is transparent", weak.all { px[it] == 0 })
        val marked = unknown.count { px[it] != 0 }
        assertEquals("a quarter of the unknown pixels carry the hatch", unknown.size / 4, marked)
        // The hatch is the frozen palette's dim text colour, and is not a colour of the habitat ramp.
        val hatch = SuitabilityRasterizer.HATCH
        assertEquals(Gen.TextDim.toArgb() and 0xFFFFFF, hatch and 0xFFFFFF)
        val nearest = (35..100).minOf { s ->
            val c = SuitabilityRasterizer.colourFor(s / 100.0, 0.35)
            fun ch(v: Int, sh: Int) = ((v shr sh) and 255).toDouble()
            sqrt((0..2).sumOf { k -> (ch(c, 8 * k) - ch(hatch, 8 * k)).let { it * it } })
        }
        assertTrue("the hatch is too close to a habitat colour ($nearest)", nearest > 30.0)
    }
}
