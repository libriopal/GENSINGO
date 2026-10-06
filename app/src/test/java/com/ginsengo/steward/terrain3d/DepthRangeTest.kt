package com.ginsengo.steward.terrain3d

import com.ginsengo.steward.terrain.DemTileStore
import com.ginsengo.steward.terrain.TerrainMath
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream
import kotlin.math.abs

/**
 * B7: the 3D view's depth buffer resolves its own ground, and the near plane clips none of it.
 *
 * Over a square the size the app builds (3 × 3 zoom-15 tiles, ~3 km; real Boone terrain mirrored
 * so the tiles join without a seam), from every pitch the view allows, eight bearings and the
 * whole zoom range the 3D view moves through:
 *  (a) no vertex inside the frustum is nearer the eye than [DepthRange.near];
 *  (b) a 16-bit depth buffer (the fallback some GPUs give) resolves a step finer than one
 *      elevation cell at the farthest ground in view, where MapLibre's fixed 48-px near plane
 *      resolves only ~16 m at the steepest pitch.
 */
class DepthRangeTest {

    private val tile = DemTileStore.TILE

    private fun boone(): FloatArray {
        val stream = javaClass.getResourceAsStream("/terrain_boone_z15.bin")
        assertNotNull("missing terrain fixture", stream)
        DataInputStream(stream!!.buffered()).use { d ->
            val magic = ByteArray(8); d.readFully(magic)
            val w = d.readInt(); val h = d.readInt()
            return FloatArray(w * h) { d.readShort().toFloat() }
        }
    }

    /** 5 × 5 tiles (the 3D view's 3 × 3 plus a one-tile halo), each the fixture mirrored to join its neighbours. */
    private val mosaic: DemTileStore.Mosaic = run {
        val src = boone()
        val w = 5 * tile
        val z = FloatArray(w * w) { i ->
            val x = i % w; val y = i / w
            val tx = x / tile; val ty = y / tile
            val sx = if (tx % 2 == 0) x % tile else tile - 1 - x % tile
            val sy = if (ty % 2 == 0) y % tile else tile - 1 - y % tile
            src[sy * tile + sx]
        }
        DemTileStore.Mosaic(TerrainMath.Grid(w, w, z, 3.86), 15, 8948, 12842, 5, 5, tile, 25, 25)
    }

    private val buildCamera = MapCamera(36.2, -81.67, Terrain3D.BUILD_ZOOM, 0.0, 0.0, 1000, 1000)
    private val mesh = TerrainMesh.build(mosaic, buildCamera, 193, Terrain3D.EXAGGERATION)
    private val worldSize = MapCamera.TILE_SIZE * Math.pow(2.0, Terrain3D.BUILD_ZOOM)
    private val lat = MapCamera.latFromMercatorY(mesh.originWorldY / worldSize)
    private val lng = MapCamera.lngFromMercatorX(mesh.originWorldX / worldSize)
    private val anchor = mosaic.grid[mosaic.grid.w / 2, mosaic.grid.h / 2].toDouble()
    private val fit = Terrain3D.fitZoom(3 * tile * 3.86, lat, 1080)

    private class View(val mc: MapCamera, val near: Double, val mvp: FloatArray)

    private fun view(zoom: Double, bearing: Double, pitch: Double, terrainAware: Boolean = true): View {
        val mc = MapCamera(lat, lng, zoom, bearing, pitch, 1080, 2400)
        val k = Math.pow(2.0, zoom - Terrain3D.BUILD_ZOOM)
        val highest = (mesh.maxElevationM - anchor) * mesh.pixelsPerMeter * Terrain3D.EXAGGERATION * k
        val near = if (terrainAware) DepthRange.near(mc, highest) else mc.nearZ
        val mvp = mc.mvpForMeshBuiltAt(Terrain3D.BUILD_ZOOM, mesh.originWorldX, mesh.originWorldY,
            groundZ = anchor * mesh.pixelsPerMeter * Terrain3D.EXAGGERATION, nearPx = near)
        return View(mc, near, mvp)
    }

    /** Eye depth (clip w) of every vertex the triangles use that lies inside the frustum's sides. */
    private fun depthsInView(mvp: FloatArray): List<Double> {
        val used = BooleanArray(mesh.vertexCount).also { u -> mesh.indices.forEach { u[it] = true } }
        val out = ArrayList<Double>()
        val p = mesh.vertices
        for (v in 0 until mesh.vertexCount) {
            if (!used[v]) continue
            val b = v * TerrainMesh.FLOATS_PER_VERTEX
            val x = p[b]; val y = p[b + 1]; val z = p[b + 2]
            val cx = mvp[0] * x + mvp[4] * y + mvp[8] * z + mvp[12]
            val cy = mvp[1] * x + mvp[5] * y + mvp[9] * z + mvp[13]
            val cw = mvp[3] * x + mvp[7] * y + mvp[11] * z + mvp[15]
            if (cw > 0 && abs(cx) <= cw && abs(cy) <= cw) out += cw.toDouble()
        }
        return out
    }

    private val pitches = listOf(15.0, 30.0, 45.0, 55.0, 60.0, 70.0, 80.0)

    @Test
    fun theNearPlaneNeverClipsGroundInView() {
        var raised = 0; var cameras = 0
        for (dz in listOf(-1.5, 0.0, 1.0, 2.0, 3.0, 3.5)) for (pitch in pitches) for (bearing in 0 until 360 step 45) {
            val v = view(fit + dz, bearing.toDouble(), pitch)
            cameras++
            if (v.near <= v.mc.nearZ) continue   // MapLibre's own plane: not this candidate's claim
            raised++
            val nearest = depthsInView(v.mvp).minOrNull() ?: continue
            assertTrue("zoom fit%+.1f pitch %.0f bearing %d: ground at %.1f px inside a near plane at %.1f px"
                .format(dz, pitch, bearing, nearest, v.near), nearest >= v.near * (1 - 1e-4))
        }
        assertTrue("the terrain-aware plane rose for only $raised of $cameras cameras", raised * 2 > cameras)
    }

    @Test
    fun sixteenBitDepthResolvesACellAtTheFarthestGround() {
        val cell = mosaic.grid.cellSizeM
        var worstOld = 0.0
        for (pitch in pitches) {
            var worstPitchNew = 0.0; var worstPitchOld = 0.0; var nearNew = 0.0; var nearOld = 0.0
            for (bearing in 0 until 360 step 45) for (aware in listOf(true, false)) {
                val v = view(fit, bearing.toDouble(), pitch, aware)
                val far = maxOf(v.mc.farZ, v.near * 2.0)
                val d = depthsInView(v.mvp).maxOrNull() ?: continue
                val stepM = DepthRange.step(d, v.near, far, 16) * v.mc.metersPerPixel
                if (aware) {
                    assertTrue("pitch %.0f bearing %d: 16-bit step %.2f m at the far ground".format(pitch, bearing, stepM), stepM < cell)
                    worstPitchNew = maxOf(worstPitchNew, stepM); nearNew = v.near
                } else { worstOld = maxOf(worstOld, stepM); worstPitchOld = maxOf(worstPitchOld, stepM); nearOld = v.near }
            }
            // The measurement itself, for the wave's evidence (the test report keeps stdout).
            println("B7 pitch %2.0f: 16-bit step at the far ground %.2f m (near %.0f px), fixed near %.2f m (near %.0f px); cell %.2f m"
                .format(pitch, worstPitchNew, nearNew, worstPitchOld, nearOld, cell))
        }
        assertTrue("the fixed 48-px plane should fail this somewhere (worst %.1f m)".format(worstOld), worstOld > cell)
    }
}
