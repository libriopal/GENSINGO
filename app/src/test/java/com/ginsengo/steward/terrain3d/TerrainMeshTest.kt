package com.ginsengo.steward.terrain3d

import com.ginsengo.steward.terrain.DemTileStore
import com.ginsengo.steward.terrain.TerrainMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Mesh construction over the real Boone, North Carolina elevation fixture.
 *
 * Everything that can be wrong about the mesh other than the GL calls themselves is
 * checked here: index validity, normal orientation, skirt geometry, and above all the
 * floating-point precision that motivated the local-origin design.
 */
class TerrainMeshTest {

    /** Boone NC at zoom 14 — the tile the fixture was sampled from. */
    private val Z = 14
    private val TILE_X = 4475
    private val TILE_Y = 6422

    private fun fixtureGrid(cellSizeM: Double = 7.71): TerrainMath.Grid {
        val stream = javaClass.getResourceAsStream("/terrain_boone_z14.bin")
        assertNotNull("missing terrain fixture", stream)
        DataInputStream(stream!!.buffered()).use { d ->
            val magic = ByteArray(8); d.readFully(magic)
            assertEquals("GSDEMTIL", String(magic, Charsets.US_ASCII))
            val w = d.readInt(); val h = d.readInt()
            return TerrainMath.Grid(w, h, FloatArray(w * h) { d.readShort().toFloat() }, cellSizeM)
        }
    }

    private fun mosaic(halo: Int = 32) = DemTileStore.Mosaic(
        grid = fixtureGrid(),
        zoom = Z,
        tileX0 = TILE_X, tileY0 = TILE_Y,
        tilesX = 1, tilesY = 1,
        haloPx = halo,
        tilesLoaded = 1, tilesRequested = 1,
    )

    private fun camera(zoom: Double = 14.0) = MapCamera(
        centerLat = 36.2, centerLng = -81.67,
        zoom = zoom, bearingDeg = 0.0, pitchDeg = 50.0,
        viewportWidth = 1080, viewportHeight = 1920,
    )

    private fun build(n: Int = 64, exaggeration: Float = 1f) =
        TerrainMesh.build(mosaic(), camera(), n, tpiRadiusM = 120.0, exaggeration = exaggeration)

    @Test
    fun geometryCountsAreConsistent() {
        val n = 48
        val m = build(n)
        assertEquals(n, m.gridN)
        assertEquals(n * n + 4 * n, m.vertexCount)
        assertEquals(m.vertexCount * TerrainMesh.FLOATS_PER_VERTEX, m.vertices.size)
        val expectedTris = (n - 1) * (n - 1) * 2 + 4 * (n - 1) * 2
        assertEquals(expectedTris, m.triangleCount)
    }

    @Test
    fun everyIndexIsInRange() {
        val m = build(40)
        assertTrue("index buffer must be whole triangles", m.indices.size % 3 == 0)
        m.indices.forEach {
            assertTrue("index $it out of range (${m.vertexCount})", it in 0 until m.vertexCount)
        }
    }

    @Test
    fun noDegenerateTriangles() {
        val m = build(40)
        var degenerate = 0
        for (t in m.indices.indices step 3) {
            val a = m.indices[t]; val b = m.indices[t + 1]; val c = m.indices[t + 2]
            if (a == b || b == c || a == c) degenerate++
        }
        assertEquals("degenerate triangles in the index buffer", 0, degenerate)
    }

    /**
     * THE PRECISION TEST — the reason the local origin exists.
     *
     * At zoom 15 the Mercator pixel space is 512 * 2^15 = 16,777,216 units across. float32
     * holds ~7 significant digits, so an absolute coordinate up there resolves to 1-2 units
     * — metres of jitter and vertices visibly snapping as the camera pans. Storing
     * positions relative to a local origin keeps every coordinate small enough that float32
     * still resolves centimetres.
     */
    @Test
    fun vertexPositionsStaySmallEnoughForFloat32() {
        val m = TerrainMesh.build(mosaic(), camera(zoom = 15.0), 64, tpiRadiusM = 120.0)
        var maxAbs = 0f
        for (v in 0 until m.vertexCount) {
            val b = v * TerrainMesh.FLOATS_PER_VERTEX + TerrainMesh.OFF_POSITION
            maxAbs = maxOf(maxAbs, abs(m.vertices[b]), abs(m.vertices[b + 1]))
        }
        assertTrue(
            "local coordinates should be small; got $maxAbs (absolute world coords would " +
                    "be in the millions and lose metres to float32)",
            maxAbs < 50_000f,
        )
        // The origin itself must be the large number, proving it was actually subtracted.
        assertTrue(
            "origin should carry the magnitude, got ${m.originWorldX}",
            abs(m.originWorldX) > 1_000_000.0,
        )
        // float32 resolution at the largest stored coordinate must still be sub-metre.
        val ulp = Math.ulp(maxAbs)
        assertTrue("float32 step at $maxAbs is $ulp world px — too coarse", ulp < 0.05f)
    }

    @Test
    fun normalsAreUnitLengthAndPointUpwards() {
        val m = build(48)
        val n = m.gridN
        for (j in 0 until n) for (i in 0 until n) {
            val b = (j * n + i) * TerrainMesh.FLOATS_PER_VERTEX + TerrainMesh.OFF_NORMAL
            val x = m.vertices[b]; val y = m.vertices[b + 1]; val z = m.vertices[b + 2]
            val len = sqrt(x * x + y * y + z * z)
            assertEquals("normal at $i,$j must be unit length", 1f, len, 1e-3f)
            assertTrue("normal at $i,$j must face upward, z=$z", z > 0f)
        }
    }

    @Test
    fun skirtVerticesHangBelowTheirSourceEdge() {
        val n = 32
        val m = build(n)
        val interior = n * n
        assertTrue("skirt must exist", m.vertexCount > interior)
        assertTrue("skirt depth must be positive", m.skirtDepthPx > 0f)

        // North edge skirt is the first block appended after the interior vertices.
        for (i in 0 until n) {
            val src = i * TerrainMesh.FLOATS_PER_VERTEX + TerrainMesh.OFF_POSITION
            val dst = (interior + i) * TerrainMesh.FLOATS_PER_VERTEX + TerrainMesh.OFF_POSITION
            assertEquals("skirt keeps x", m.vertices[src], m.vertices[dst], 1e-4f)
            assertEquals("skirt keeps y", m.vertices[src + 1], m.vertices[dst + 1], 1e-4f)
            assertTrue(
                "skirt must sit below the surface",
                m.vertices[dst + 2] < m.vertices[src + 2],
            )
        }
    }

    @Test
    fun elevationsMatchTheUnderlyingDem() {
        val m = build(64)
        val g = fixtureGrid()
        assertTrue("min elevation looks wrong: ${m.minElevationM}", m.minElevationM > 900f)
        assertTrue("max elevation looks wrong: ${m.maxElevationM}", m.maxElevationM < 1200f)
        assertTrue("mesh must have relief", m.maxElevationM - m.minElevationM > 20f)
        assertTrue(m.minElevationM >= g.z.min() - 1f)
        assertTrue(m.maxElevationM <= g.z.max() + 1f)
    }

    @Test
    fun suitabilityIsBoundedAndVaries() {
        val m = build(56)
        var min = Float.MAX_VALUE; var max = -Float.MAX_VALUE
        for (v in 0 until m.gridN * m.gridN) {
            val s = m.vertices[v * TerrainMesh.FLOATS_PER_VERTEX + TerrainMesh.OFF_SUITABILITY]
            assertTrue("suitability out of range: $s", s in 0f..1f)
            min = minOf(min, s); max = maxOf(max, s)
        }
        assertTrue("suitability must vary across a real hillside ($min..$max)", max - min > 0.2f)
    }

    @Test
    fun exaggerationScalesHeightOnly() {
        val flat = build(40, exaggeration = 1f)
        val tall = build(40, exaggeration = 3f)
        val n = flat.gridN
        // Pick an interior vertex with real relief.
        val idx = (n / 2) * n + (n / 2)
        val b = idx * TerrainMesh.FLOATS_PER_VERTEX + TerrainMesh.OFF_POSITION
        assertEquals("x must not change", flat.vertices[b], tall.vertices[b], 1e-4f)
        assertEquals("y must not change", flat.vertices[b + 1], tall.vertices[b + 1], 1e-4f)
        assertEquals(
            "z must scale with exaggeration",
            flat.vertices[b + 2] * 3f, tall.vertices[b + 2], abs(flat.vertices[b + 2]) * 1e-3f + 1e-3f,
        )
    }

    @Test
    fun gridDensityAdaptsToZoom() {
        val far = TerrainMesh.gridSizeFor(9.0)
        val mid = TerrainMesh.gridSizeFor(12.0)
        val near = TerrainMesh.gridSizeFor(16.0)
        assertTrue("$far <= $mid <= $near", far <= mid && mid <= near)
        assertTrue("finest grid must stay within unsigned-short-free territory", near <= 256)
    }

    /**
     * The whole mesh must sit inside the mosaic's geographic footprint. A mesh that is
     * silently offset by the halo would render terrain shifted by a tile, which on a
     * hillside looks entirely believable.
     */
    @Test
    fun meshOriginSitsInsideTheMosaicBounds() {
        val mo = mosaic()
        val c = camera()
        val m = TerrainMesh.build(mo, c, 32, tpiRadiusM = 120.0)
        val westX = c.worldX(mo.westLon)
        val eastX = c.worldX(mo.eastLon)
        val northY = c.worldY(mo.northLat)
        val southY = c.worldY(mo.southLat)
        assertTrue("origin x inside bounds", m.originWorldX in westX..eastX)
        assertTrue("origin y inside bounds", m.originWorldY in northY..southY)
    }
}
