package com.ginsengo.steward.terrain3d

import com.ginsengo.steward.terrain.DemTileStore
import com.ginsengo.steward.terrain.TerrainMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream
import kotlin.math.abs

/**
 * B1 and B2: the 3D model is a closed block of ground from every side.
 *
 * The witness is [MeshTopology] over the real Boone z15 terrain, plus a projection check that
 * needs no topology at all: from cameras all round the square, a wall is drawn (front-facing) if
 * and only if it faces the camera. Before B1 the north and east walls failed both: they faced
 * inward, so a camera north or east of the square culled the near wall and saw the sky under the
 * edge of the model.
 */
class MeshTopologyTest {

    private fun fixture(): TerrainMath.Grid {
        val stream = javaClass.getResourceAsStream("/terrain_boone_z15.bin")
        assertNotNull("missing terrain fixture", stream)
        DataInputStream(stream!!.buffered()).use { d ->
            val magic = ByteArray(8); d.readFully(magic)
            val w = d.readInt(); val h = d.readInt()
            return TerrainMath.Grid(w, h, FloatArray(w * h) { d.readShort().toFloat() }, 3.86)
        }
    }

    private fun mosaic() = DemTileStore.Mosaic(fixture(), 15, 8950, 12844, 1, 1, 32, 1, 1)

    private val buildCamera = MapCamera(36.2, -81.67, Terrain3D.BUILD_ZOOM, 0.0, 0.0, 1000, 1000)

    private fun mesh(n: Int = 48) = TerrainMesh.build(mosaic(), buildCamera, n, exaggeration = Terrain3D.EXAGGERATION)

    private fun isWall(m: TerrainMesh.Mesh, v: Int) = m.vertices[v * TerrainMesh.FLOATS_PER_VERTEX + TerrainMesh.OFF_WALL] == 1f

    /** Closed but for the bottom of the walls, which no camera above ground can see into. */
    @Test
    fun aFullMeshIsClosedButForTheFootOfItsWalls() {
        val n = 48
        val m = mesh(n)
        val r = MeshTopology.check(m.indices)
        assertTrue("edges walked twice the same way (a wall or triangle facing inward): ${r.sameDirection.take(5)}", r.sameDirection.isEmpty())
        assertTrue("non-manifold edges: ${r.overShared.take(5)}", r.overShared.isEmpty())
        assertEquals("open edges must be exactly the skirt's bottom ring", 4 * (n - 1), r.openEdges.size)
        for ((a, b) in r.openEdges) assertTrue("open edge $a-$b is not at the foot of a wall", isWall(m, a) && isWall(m, b))
    }

    /** The detector itself: an induced crack and a missing skirt are both found (I10, I11). */
    @Test
    fun theDetectorFindsAnInducedCrackAndAMissingSkirt() {
        val n = 32
        val m = mesh(n)
        val surface = (n - 1) * (n - 1) * 2 * 3
        // I10: one interior surface triangle dropped. Its three edges lose their partner.
        val t = ((n / 2) * (n - 1) + n / 2) * 6
        val cracked = m.indices.copyOfRange(0, t) + m.indices.copyOfRange(t + 3, m.indices.size)
        assertEquals(4 * (n - 1) + 3, MeshTopology.check(cracked).openEdges.size)
        // I11: no skirt. Every boundary edge of the surface is open, and the bottom ring is gone.
        val bare = m.indices.copyOfRange(0, surface)
        val open = MeshTopology.check(bare).openEdges
        assertEquals(4 * (n - 1), open.size)
        assertTrue(open.none { (a, b) -> isWall(m, a) || isWall(m, b) })
    }

    /**
     * From eight bearings at 60° of tilt, each wall triangle is front-facing on screen exactly when
     * the camera is on its outward side. Projected with the view's own matrix, so it is the GPU's
     * culling decision, not a re-derivation of the winding.
     */
    @Test
    fun everyWallIsDrawnFromTheSideThatSeesIt() {
        val n = 48
        val m = mesh(n)
        val worldSize = MapCamera.TILE_SIZE * Math.pow(2.0, Terrain3D.BUILD_ZOOM)
        val lat = MapCamera.latFromMercatorY(m.originWorldY / worldSize)
        val lng = MapCamera.lngFromMercatorX(m.originWorldX / worldSize)
        val anchor = (m.minElevationM + m.maxElevationM) / 2.0
        val groundZ = anchor * m.pixelsPerMeter * Terrain3D.EXAGGERATION
        val p = m.vertices
        fun pos(v: Int, c: Int) = p[v * TerrainMesh.FLOATS_PER_VERTEX + TerrainMesh.OFF_POSITION + c].toDouble()
        val minX = (0 until n * n).minOf { pos(it, 0) }; val maxX = (0 until n * n).maxOf { pos(it, 0) }
        val minY = (0 until n * n).minOf { pos(it, 1) }; val maxY = (0 until n * n).maxOf { pos(it, 1) }
        var walls = 0
        for (bearing in listOf(0.0, 45.0, 90.0, 135.0, 180.0, 225.0, 270.0, 315.0)) {
            val mc = MapCamera(lat, lng, Terrain3D.BUILD_ZOOM, bearing, 60.0, 1080, 2400)
            val mvp = mc.mvpForMeshBuiltAt(Terrain3D.BUILD_ZOOM, m.originWorldX, m.originWorldY, groundZ)
            val eye = mc.eyeWorld()
            val ex = eye[0] - m.originWorldX; val ey = eye[1] - m.originWorldY
            val wrong = ArrayList<String>()
            for (t in m.indices.indices step 3) {
                val v = intArrayOf(m.indices[t], m.indices[t + 1], m.indices[t + 2])
                if (v.none { it >= n * n }) continue   // surface
                walls++
                val xs = v.map { pos(it, 0) }; val ys = v.map { pos(it, 1) }
                val (ox, oy, side) = when {
                    xs.all { abs(it - minX) < 1e-3 } -> Triple(-1.0, 0.0, "west")
                    xs.all { abs(it - maxX) < 1e-3 } -> Triple(1.0, 0.0, "east")
                    ys.all { abs(it - minY) < 1e-3 } -> Triple(0.0, -1.0, "north")
                    ys.all { abs(it - maxY) < 1e-3 } -> Triple(0.0, 1.0, "south")
                    else -> error("a wall triangle off the boundary: $xs $ys")
                }
                val facing = ox * (ex - xs.average()) + oy * (ey - ys.average()) > 0
                val ndc = v.map { vi ->
                    val x = pos(vi, 0); val y = pos(vi, 1); val z = pos(vi, 2)
                    val cx = mvp[0] * x + mvp[4] * y + mvp[8] * z + mvp[12]
                    val cy = mvp[1] * x + mvp[5] * y + mvp[9] * z + mvp[13]
                    val cw = mvp[3] * x + mvp[7] * y + mvp[11] * z + mvp[15]
                    (cx / cw) to (cy / cw)
                }
                val area = (ndc[1].first - ndc[0].first) * (ndc[2].second - ndc[0].second) -
                    (ndc[2].first - ndc[0].first) * (ndc[1].second - ndc[0].second)
                if (abs(area) < 1e-12) continue
                if ((area > 0) != facing) wrong += "$side wall at bearing $bearing: facing=$facing, drawn=${area > 0}"
            }
            assertTrue(wrong.distinct().take(4).joinToString("; "), wrong.isEmpty())
        }
        assertTrue("no wall triangles checked", walls > 0)
    }
}
