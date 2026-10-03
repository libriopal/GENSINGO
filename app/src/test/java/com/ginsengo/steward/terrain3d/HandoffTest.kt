package com.ginsengo.steward.terrain3d

import com.ginsengo.steward.terrain.DemTileStore
import com.ginsengo.steward.terrain.TerrainMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream

/**
 * The measured hand-off between the flat map and the mesh (exe.md A7) and the cross-fade built
 * on it (A8).
 *
 * Witnesses, independent of [Handoff]: the pinhole formula for a camera looking straight down
 * (a point x world pixels off-centre at height h, under an eye D above the plane, is drawn
 * x·D/(D−h) from the centre), and a real Terrarium tile over Boone, NC (the fixture
 * RealTerrainTest decodes with Python, not with the app's decoder).
 */
class HandoffTest {

    private val w = 1080
    private val h = 2400
    /** 2 dp on a 2.625-density phone (the emulator the device gates run on). */
    private val tolPx = Handoff.TOLERANCE_DP * 2.625

    /**
     * Straight down, one point off-centre: the hand-off relief is the analytic one. Solving
     * x·rh/(D − rh) = t for r gives r = t·D / (h·(x + t)).
     */
    @Test
    fun theHandOffMatchesThePinholeCameraLookingStraightDown() {
        val mc = MapCamera(35.56, -83.0, 14.5, 0.0, 0.0, w, h)
        val x = 400.0                                    // world (= screen) pixels east of centre
        val lng = MapCamera.lngFromMercatorX((mc.centerX + x) / mc.worldSize)
        val heightM = 300.0
        val sample = listOf(Handoff.Sample(mc.centerLat, lng, heightM))
        val hPx = heightM * mc.pixelsPerMeter
        val d = mc.cameraToCenterDistance
        val expected = tolPx * d / (hPx * (x + tolPx))
        val r = Handoff.relief(mc, sample, tolPx)
        assertEquals("analytic hand-off", expected, r, 1e-4)
        assertTrue("the expected value is a real hand-off, not a clamp", expected in 0.01..0.99)
    }

    /** The acceptance check: change the tolerance and the hand-off moves (and only up). */
    @Test
    fun theHandOffMovesWithTheTolerance() {
        val (mc, samples) = boone(pitch = 50.0)
        val tight = Handoff.relief(mc, samples, 2.0)
        val normal = Handoff.relief(mc, samples, tolPx)
        val loose = Handoff.relief(mc, samples, 32.0)
        println("Boone z15, pitch 50: hand-off relief %.4f @2px, %.4f @%.2fpx, %.4f @32px".format(tight, normal, tolPx, loose))
        assertTrue("tight $tight < normal $normal < loose $loose", tight < normal && normal < loose)
        assertTrue("a real tile in steep country needs a hand-off below full relief", loose < 1.0 && tight > 0.0)
    }

    /** The hand-off is tight: within the tolerance there, beyond it just above. */
    @Test
    fun theHandOffIsTheLargestReliefWithinTheTolerance() {
        val (mc, samples) = boone(pitch = 50.0)
        val r = Handoff.relief(mc, samples, tolPx)
        assertTrue(Handoff.disagreementPx(mc, samples, r) <= tolPx)
        assertTrue(Handoff.disagreementPx(mc, samples, (r + 0.01).coerceAtMost(1.0)) > tolPx)
        assertEquals("flat, the mesh is the map's plane", 0.0, Handoff.disagreementPx(mc, samples, 0.0), 1e-9)
    }

    /**
     * Why the hand-off is a relief and not a tilt (the falsification in A.2's record): on real
     * terrain the flat map and the full-relief mesh disagree by more than the tolerance at EVERY
     * tilt the map can draw, straight down included, so no tilt can be "the" cross-over.
     */
    @Test
    fun noTiltMakesTheFlatMapAgreeWithRealRelief() {
        val rows = (0..60 step 15).map { p ->
            val (mc, samples) = boone(pitch = p.toDouble())
            p to Handoff.disagreementPx(mc, samples, 1.0)
        }
        println("Boone z15 full-relief disagreement by pitch: " + rows.joinToString { (p, d) -> "%d°=%.1fpx".format(p, d) })
        rows.forEach { (p, d) -> assertTrue("pitch $p: $d px", d > tolPx) }
    }

    @Test
    fun theFadeKeepsTheReliefUnderTheHandOffWhileTheMapShows() {
        val handoff = 0.23
        var last = Handoff.blend(0f, handoff)
        assertEquals(Handoff.Blend(0f, 0.0), last)
        for (i in 1..400) {
            val u = i / 200f
            val b = Handoff.blend(u, handoff)
            if (b.alpha < 1f) assertTrue("reveal $u: relief ${b.relief} while the map shows", b.relief <= handoff + 1e-12)
            assertTrue("alpha never falls as reveal rises", b.alpha >= last.alpha)
            assertTrue("relief never falls as reveal rises", b.relief >= last.relief - 1e-12)
            assertTrue("no jump at $u", b.relief - last.relief < 0.01 && b.alpha - last.alpha < 0.01)
            last = b
        }
        assertEquals(Handoff.Blend(1f, 1.0), Handoff.blend(Handoff.RISEN, handoff))
        assertEquals("covered at the hand-off", Handoff.Blend(1f, handoff), Handoff.blend(Handoff.COVERED, handoff))
        assertEquals("half faded, half the hand-off", 0.115, Handoff.blend(0.5f, handoff).relief, 1e-9)
    }

    /** The Boone z15 tile as a one-tile scene, the camera fitted over it as the 3D view fits its square. */
    private fun boone(pitch: Double): Pair<MapCamera, List<Handoff.Sample>> {
        val z = 15
        val g = fixture("terrain_boone_z15.bin", 3.86)
        val tx = DemTileStore.lonToTileX(-81.67, z); val ty = DemTileStore.latToTileY(36.2, z)
        val s = Terrain3D.build(DemTileStore.Mosaic(g, z, tx, ty, 1, 1, 0, 1, 1))
        val lat = (s.north + s.south) / 2; val lng = (s.west + s.east) / 2
        val anchor = s.elevationAt(lat, lng)!!
        val mc = MapCamera(lat, lng, Terrain3D.fitZoom(s.widthM, lat, w), 30.0, pitch, w, h)
        return mc to Handoff.samples(s, anchor, n = 15)
    }

    /** RealTerrainTest's fixture format: "GSDEMTIL", width, height, big-endian int16 metres. */
    private fun fixture(name: String, cellSizeM: Double): TerrainMath.Grid {
        DataInputStream(javaClass.getResourceAsStream("/$name")!!.buffered()).use { d ->
            val magic = ByteArray(8); d.readFully(magic)
            assertEquals("GSDEMTIL", String(magic, Charsets.US_ASCII))
            val gw = d.readInt(); val gh = d.readInt()
            return TerrainMath.Grid(gw, gh, FloatArray(gw * gh) { d.readShort().toFloat() }, cellSizeM)
        }
    }
}
