package com.ginsengo.steward.terrain3d

import com.ginsengo.steward.Fixtures
import com.ginsengo.steward.terrain.DemTileStore
import com.ginsengo.steward.terrain.Hydrology
import com.ginsengo.steward.terrain.SuitabilityRasterizer
import com.ginsengo.steward.terrain.TerrainMath
import com.ginsengo.steward.ui.map.MapDrape
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * WP-A, the map drape (docs/blueprints/one-map.md). The 3D view shows the real basemap, a
 * MapSnapshotter render of the 2D map's own style, under the app's layers. These pin how the
 * snapshot is composited into the terrain texture, that the layer toggles do what the Layers
 * sheet says, that nothing changed for the existing modes, and that the snapshot never asks
 * for a zoom the saved offline region does not hold.
 */
class MapDrapeCompositeTest {

    private val allOff = TerrainTextures.Layers(habitat = false, water = false, contours = false)

    private fun mosaic(n: Int, cell: Double = 10.0, halo: Int = 0, z: (Int, Int) -> Double) =
        DemTileStore.Mosaic(
            TerrainMath.Grid(n, n, FloatArray(n * n) { i -> z(i % n, i / n).toFloat() }, cell),
            14, 4475, 6422, 1, 1, halo, 1, 1,
        )

    private fun randomOpaqueBasemap(size: Int, seed: Long = 7L): IntArray {
        val r = Random(seed)
        return IntArray(size * size) { r.nextInt() or (0xFF shl 24) }
    }

    /** Hand-made channels, so the picture depends on the baker alone and not on Hydrology. */
    private fun lines(n: Int, halo: Int, c: Int) = listOf(
        Hydrology.Line(IntArray(n - 2 * halo) { (halo + it) * n + c }, Hydrology.Kind.STREAM),
        Hydrology.Line(IntArray(60) { (halo + 10 + it) * n + halo + 5 + it / 2 }, Hydrology.Kind.CREEK),
        Hydrology.Line(IntArray(25) { (halo + 70) * n + halo + 60 + it }, Hydrology.Kind.DRAINAGE),
    )

    /**
     * Relief, a halo, habitat scores on both sides of [TerrainTextures.MIN_SCORE] and all three
     * kinds of channel: every stage of the bake has something to draw. Test 5's digests were
     * taken from exactly this ground with the bake as it was before WP-A (commit 556eb69).
     */
    private fun goldenGround(basemap: IntArray? = null): TerrainTextures.Ground {
        val halo = 4; val n = 96 + 2 * halo; val c = n / 2
        val z = FloatArray(n * n) { i ->
            val x = i % n; val y = i / n
            (500.0 + abs(x - c) * 2.5 + (n - y) * 0.6 + 12.0 * sin(x / 7.0) * cos(y / 9.0)).toFloat()
        }
        val m = DemTileStore.Mosaic(TerrainMath.Grid(n, n, z, 10.0), 14, 4475, 6422, 1, 1, halo, 1, 1)
        val scores = DoubleArray(96 * 96) { i -> 0.5 + 0.5 * sin((i % 96) * 0.21) * cos((i / 96) * 0.17) }
        return TerrainTextures.Ground(m, scores, 96, lines(n, halo, c), 1.5, basemap = basemap)
    }

    private fun digest(px: IntArray): String {
        val b = ByteBuffer.allocate(px.size * 4); px.forEach { b.putInt(it) }
        return MessageDigest.getInstance("SHA-256").digest(b.array()).joinToString("") { "%02x".format(it) }
    }

    private fun hex(c: Int) = "#%08X".format(c)

    /** Fails on the first differing texel, in a form a person can read. */
    private fun assertTexels(label: String, want: IntArray, got: IntArray, size: Int) {
        assertEquals("$label: texel count", want.size, got.size)
        val bad = want.indices.count { want[it] != got[it] }
        if (bad == 0) return
        val i = want.indices.first { want[it] != got[it] }
        fail("$label: texel ${i % size},${i / size} expected ${hex(want[i])} but was ${hex(got[i])}; " +
            "$bad of ${want.size} texels differ")
    }

    private fun blue(p: Int) = (p and 255) - ((p shr 16) and 255) > 60

    // ------------------------------------------------------------------ acceptance tests

    /** 1. With every layer off, flat ground shows the basemap and nothing else, bit for bit. */
    @Test
    fun mapModeWithEveryLayerOffIsTheBasemapExactly() {
        val n = 64
        val basemap = randomOpaqueBasemap(n)
        // Scores and channels are present, so "off" has something to hide.
        val scores = DoubleArray(n * n) { (it % n) / (n - 1.0) }
        val ground = TerrainTextures.Ground(mosaic(n) { _, _ -> 700.0 }, scores, n,
            lines(n, 0, n / 2), 1.5, basemap = basemap)
        val px = TerrainTextures.bake(ground, TerrainTextures.Mode.MAP, n, allOff)
        assertTrue(px.all { (it ushr 24) == 0xFF })
        assertTexels("MAP, layers off", basemap, px, n)
    }

    /** 2. Habitat on: each texel is the 2D heatmap colour composited over the basemap. */
    @Test
    fun mapModeHabitatIsTheHeatmapColourOverTheBasemap() {
        val n = 64
        val basemap = randomOpaqueBasemap(n, seed = 11L)
        val scores = DoubleArray(n * n) { (it % n) / (n - 1.0) }          // 0 west .. 1 east
        val ground = TerrainTextures.Ground(mosaic(n) { _, _ -> 700.0 }, scores, n, emptyList(), 1.5,
            basemap = basemap)
        val px = TerrainTextures.bake(ground, TerrainTextures.Mode.MAP, n,
            TerrainTextures.Layers(habitat = true, water = false, contours = false))
        val want = IntArray(n * n) {
            TerrainTextures.over(basemap[it], SuitabilityRasterizer.colourFor(scores[it], TerrainTextures.MIN_SCORE)) or
                (0xFF shl 24)
        }
        assertTexels("MAP, habitat on", want, px, n)
        // Weak ground shows the map untouched; strong ground is visibly tinted.
        assertEquals(hex(basemap[n / 2 * n]), hex(px[n / 2 * n]))
        assertTrue(px[n / 2 * n + n - 1] != basemap[n / 2 * n + n - 1])
    }

    /** 3. The water toggle draws the creek along the valley floor, or draws nothing at all. */
    @Test
    fun waterOnDrawsTheCreekOnTheValleyFloorAndWaterOffDrawsNone() {
        val n = 200; val c = n / 2
        val m = mosaic(n) { x, y -> 500.0 + abs(x - c) * 3.0 + (n - y) * 0.5 }
        val traced = Hydrology.of(m.grid).lines()
        val grey = IntArray(n * n) { 0xFF7A7468.toInt() }                // a map colour that is not blue
        val withWater = TerrainTextures.Ground(m, null, n, traced, 1.0, basemap = grey)
        val on = TerrainTextures.bake(withWater, TerrainTextures.Mode.MAP, n, TerrainTextures.Layers(water = true))
        val off = TerrainTextures.bake(withWater, TerrainTextures.Mode.MAP, n, TerrainTextures.Layers(water = false))

        val lowRows = (n * 3 / 4 until n - 2)
        assertTrue("the valley floor is not blue", lowRows.count { blue(on[it * n + c]) } > lowRows.count() * 0.8)
        for (y in 0 until n) for (x in listOf(10, 40, n - 40, n - 10)) assertFalse("blue at $x,$y", blue(on[y * n + x]))

        assertEquals("water off still draws blue", 0, off.count { blue(it) })
        // ...and "off" is exactly the picture of the same ground with no channels at all.
        val dry = TerrainTextures.bake(TerrainTextures.Ground(m, null, n, emptyList(), 1.0, basemap = grey),
            TerrainTextures.Mode.MAP, n, TerrainTextures.Layers(water = true))
        assertTexels("water off vs no channels", dry, off, n)
    }

    /**
     * 4. With no usable snapshot, MAP falls back to the neutral relief: exactly HABITAT, for every
     * combination of layers. "No usable snapshot" is no basemap, one with no opaque pixel, or one
     * made for another texture size (never read out of bounds).
     */
    @Test
    fun mapModeWithoutABasemapIsTheHabitatMode() {
        val combos = (0 until 8).map { TerrainTextures.Layers(habitat = it and 1 != 0, water = it and 2 != 0, contours = it and 4 != 0) }
        for (size in intArrayOf(96, 128)) {
            val translucent = Random(3L).let { r -> IntArray(size * size) { r.nextInt() and 0xFEFFFFFF.toInt() } }
            val noMap = goldenGround()
            val seeThrough = goldenGround(translucent)
            val wrongSize = goldenGround(randomOpaqueBasemap(size + 1))
            val habitat = combos.map { TerrainTextures.bake(noMap, TerrainTextures.Mode.HABITAT, size, it) }
            combos.forEachIndexed { k, layers ->
                for ((label, g) in listOf("null" to noMap, "translucent" to seeThrough, "wrong size" to wrongSize)) {
                    assertTexels("size $size, $layers, basemap $label",
                        habitat[k], TerrainTextures.bake(g, TerrainTextures.Mode.MAP, size, layers), size)
                }
            }
            // Each toggle changes the picture, so the equalities above are not between blanks.
            assertFalse("habitat toggle does nothing", habitat[7].contentEquals(habitat[6]))
            assertFalse("water toggle does nothing", habitat[7].contentEquals(habitat[5]))
            assertFalse("contour toggle does nothing", habitat[7].contentEquals(habitat[3]))
        }
    }

    /** 5. The existing modes, called as before or with default Layers, are unchanged bit for bit. */
    @Test
    fun defaultLayersReproduceThePreviousOutputsExactly() {
        val golden = mapOf(
            "HABITAT 96" to "4483969a77ff8a661b8c5d41fb10e22c3fe6c974a7ee6c9b132293984eda2db9",
            "ELEVATION 96" to "68c732ff094638cde1dbe0fd24d854ee8aba333619eaae7b40b1a4b0e9623646",
            "HABITAT 128" to "48eb43a488b79c2644f6858a1e8d88b2774dbac305a78bb1cf820bfc68167a60",
            "ELEVATION 128" to "3e87fdb903f5266744d94e65a65586887e41263cb62895a46056be931141574a",
        )
        val g = goldenGround()
        for (size in intArrayOf(96, 128)) for (mode in listOf(TerrainTextures.Mode.HABITAT, TerrainTextures.Mode.ELEVATION)) {
            val key = "$mode $size"
            assertEquals("$key, called as before", golden[key], digest(TerrainTextures.bake(g, mode, size)))
            assertEquals("$key, default Layers", golden[key], digest(TerrainTextures.bake(g, mode, size, TerrainTextures.Layers())))
        }
    }

    /**
     * 6. The snapshot must not ask for a zoom above the saved region's top (14): at the screen's
     * pixel ratio the 3 km square fits, at ratio 1 it would not, and the logical size is cut
     * just enough. MapCamera, the 3D view's copy of MapLibre's transform, is the witness that the
     * zoom really maps the square onto that many logical pixels.
     */
    @Test
    fun theSnapshotZoomNeverExceedsTheSavedRegionsTopZoom() {
        val lat = 35.5; val widthM = 3000.0

        val hd = MapDrape.logicalSize(1536, 2.625f, widthM, lat)
        assertEquals("1536 px at density 2.625 needs no cut", 585, hd)
        assertTrue("zoom ${MapDrape.snapshotZoom(hd, widthM, lat)}", MapDrape.snapshotZoom(hd, widthM, lat) <= 14.0)

        assertTrue("logical 1536 should exceed z14", MapDrape.snapshotZoom(1536, widthM, lat) > 14.0)
        val cut = MapDrape.logicalSize(1536, 1f, widthM, lat)
        assertTrue("density 1 was not cut: $cut", cut < 1536)
        assertTrue("cut to $cut, zoom ${MapDrape.snapshotZoom(cut, widthM, lat)}", MapDrape.snapshotZoom(cut, widthM, lat) <= 14.0)
        assertTrue("cut more than needed: $cut", MapDrape.snapshotZoom(cut + 1, widthM, lat) > 14.0)

        // A lower ceiling cuts the high-density size too.
        val z13 = MapDrape.logicalSize(1536, 2.625f, widthM, lat, maxZoom = 13.0)
        assertTrue(z13 < hd && MapDrape.snapshotZoom(z13, widthM, lat) <= 13.0)

        for (px in intArrayOf(hd, cut, z13)) {
            val cam = MapCamera(lat, -83.0, MapDrape.snapshotZoom(px, widthM, lat), 0.0, 0.0, px, px)
            assertEquals("logical $px", widthM, cam.metersPerPixel * px, 1e-6)
        }
    }

    // ------------------------------------------------------------------ budget and instrument

    /**
     * The contract's budget: a 1536-square MAP bake with every layer on, on the real-terrain
     * HD scene (the same 1280-cell area MeshBuildBudgetTest builds), under 1 s on the JVM.
     * Best of three after a warm-up; JVM wall-clock is a floor for a phone, not a prediction.
     */
    @Test
    fun aFullSizeMapBakeStaysUnderOneSecond() {
        val src = Fixtures.mosaic("scan_boone_z12.bin").grid
        val n = 5 * DemTileStore.TILE
        val z = FloatArray(n * n) { i -> src.z[((i / n) % src.h) * src.w + (i % n) % src.w] }
        val mo = DemTileStore.Mosaic(TerrainMath.Grid(n, n, z, 3.9), 15, 8829, 12917, 5, 5, DemTileStore.TILE, 25, 25)
        val scene = Terrain3D.build(mo)
        assertEquals(1536, scene.textureSize)
        val g = scene.ground
        val ground = TerrainTextures.Ground(g.mosaic, g.scores, g.scoreSize, g.lines, g.exaggeration,
            basemap = randomOpaqueBasemap(scene.textureSize))
        fun time(mode: TerrainTextures.Mode): Long {
            TerrainTextures.bake(ground, mode, scene.textureSize)
            var best = Long.MAX_VALUE
            repeat(3) {
                val t0 = System.nanoTime()
                TerrainTextures.bake(ground, mode, scene.textureSize)
                best = minOf(best, (System.nanoTime() - t0) / 1_000_000)
            }
            return best
        }
        val map = time(TerrainTextures.Mode.MAP)
        val habitat = time(TerrainTextures.Mode.HABITAT)
        println("bake 1536^2 (JVM, best of 3): MAP $map ms, HABITAT $habitat ms")
        assertTrue("MAP bake $map ms", map < 1_000)
    }

    /**
     * The debug request counter (the auditor's instrument) sorts requests by the URL's path
     * alone and never keeps the URL: tile paths encode a location.
     */
    @Test
    fun requestsAreSortedByKindFromThePath() {
        val k = MapDrape.RequestKind.entries.associateBy { it.name }
        assertEquals(k["STYLE"], MapDrape.kindOf("/styles/dark"))
        assertEquals(k["STYLE"], MapDrape.kindOf("/styles/liberty/style.json"))
        assertEquals(k["SPRITE"], MapDrape.kindOf("/sprites/ofm_f384/ofm@2x.json"))
        assertEquals(k["SPRITE"], MapDrape.kindOf("/sprite@2x.png"))
        assertEquals(k["GLYPHS"], MapDrape.kindOf("/fonts/Noto%20Sans%20Regular/0-255.pbf"))
        assertEquals(k["TILE"], MapDrape.kindOf("/planet/20250101_001001_pt/14/4475/6422.pbf"))
        assertEquals(k["TILE"], MapDrape.kindOf("/tiles/15/8829/12917@2x.png"))
        assertEquals(k["OTHER"], MapDrape.kindOf("/planet"))
    }

    /**
     * The counter's client is assembled by reflection (OkHttp is runtime-only for the app), so
     * nothing but running it shows it works: requests still fetch, each is counted under its
     * kind, and a network failure reaches OkHttp's caller as its own IOException.
     */
    @Test
    fun theCountingClientFetchesCountsAndPassesFailuresThrough() {
        val client = MapDrape.countingClient() as okhttp3.Call.Factory
        val server = MockWebServer()
        repeat(3) { server.enqueue(MockResponse().setBody("ok")) }
        server.start()
        val paths = listOf("/styles/dark", "/planet/20250101_001001_pt/14/4475/6422.pbf", "/fonts/Noto%20Sans%20Regular/0-255.pbf")
        val before = MapDrape.requestCounts()
        try {
            for (p in paths) client.newCall(okhttp3.Request.Builder().url(server.url(p)).build()).execute().use {
                assertEquals("ok", it.body!!.string())
            }
        } finally {
            server.shutdown()
        }
        val after = MapDrape.requestCounts()
        val delta = MapDrape.RequestKind.entries.associate { it.name to after[it.ordinal] - before[it.ordinal] }
        assertEquals(mapOf("STYLE" to 1, "SPRITE" to 0, "GLYPHS" to 1, "TILE" to 1, "OTHER" to 0), delta)

        val refused = okhttp3.Request.Builder().url(server.url("/styles/dark")).build()
        try {
            client.newCall(refused).execute().close()
            fail("a request to a stopped server succeeded")
        } catch (e: IOException) {
            // OkHttp's own exception type, not a reflection wrapper.
        }
    }
}
