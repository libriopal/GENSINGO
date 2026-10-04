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

    /**
     * A15: the way back from a 3D view tilted past the flat map's limit, sampled frame by frame:
     * the sink (relief falling to the hand-off, pitch eased into 60°), the landing under the cover
     * (CameraMath.to2d, as FieldViewModel.landFlat), the fade out. The projected screen-centre
     * ground and the square's corners never jump: no step moves them more than a few pixels, and
     * the landing moves them by nothing at all. Without the easing (mutant), the landing clamps
     * 75° to 60° in one frame.
     */
    @Test
    fun theWayBackToTheMapHasNoJump() {
        val (mc0, samples) = boone(pitch = 75.0)
        val start = CameraState(mc0.centerLat, mc0.centerLng, mc0.zoom, mc0.bearingDeg, 75.0)
        // Control points: the corners and centre of the samples' grid (heights as drawn at full relief).
        val probes = listOf(samples.first(), samples[samples.size / 2], samples.last(),
            samples[14], samples[samples.size - 15])
        val handoff = Handoff.relief(mc0, samples, tolPx)
        fun screen(cam: CameraState, reveal: Float): List<Pair<Double, Double>> {
            val mc = CameraMath.mapCamera(cam, w, h)
            val relief = Handoff.blend(reveal, handoff).relief
            return probes.map { p -> mc.project(p.lat, p.lng, p.heightM * relief)!!.let { it[0].toDouble() to it[1].toDouble() } }
        }
        val frames = 36                                         // 600 ms at 60 Hz, as MainScreen's SINK_MS
        var cam = start
        var last = screen(cam, Handoff.RISEN)
        var worstStep = 0.0
        for (i in 1..frames) {
            val reveal = Handoff.RISEN - (Handoff.RISEN - Handoff.COVERED) * i / frames
            cam = cam.copy(pitch = Handoff.sinkPitch(start.pitch, Handoff.RISEN, reveal))
            val now = screen(cam, reveal)
            worstStep = maxOf(worstStep, now.zip(last).maxOf { (a, b) -> kotlin.math.hypot(a.first - b.first, a.second - b.second) })
            last = now
        }
        assertEquals("the sink ends within the map's range", CameraMath.MAX_2D_PITCH, cam.pitch, 1e-9)
        val landed = CameraMath.forView(cam, view3d = false)
        val landingJump = screen(landed, Handoff.COVERED).zip(screen(cam, Handoff.COVERED))
            .maxOf { (a, b) -> kotlin.math.hypot(a.first - b.first, a.second - b.second) }
        println("way back from 75°: worst per-frame step %.2f px, landing %.4f px".format(worstStep, landingJump))
        assertEquals("the landing moves nothing", 0.0, landingJump, 1e-6)
        assertTrue("a frame moved the picture $worstStep px", worstStep < 25.0)
    }

    @Test
    fun theSinkLeavesAMapRangePitchAlone() {
        assertEquals(50.0, Handoff.sinkPitch(50.0, Handoff.RISEN, 1.5f), 0.0)
        assertEquals(67.5, Handoff.sinkPitch(75.0, Handoff.RISEN, 1.5f), 1e-9)
        assertEquals(60.0, Handoff.sinkPitch(75.0, Handoff.COVERED, 0.7f), 0.0)
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
