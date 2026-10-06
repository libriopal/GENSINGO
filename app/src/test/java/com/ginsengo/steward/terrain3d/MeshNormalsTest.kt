package com.ginsengo.steward.terrain3d

import com.ginsengo.steward.terrain.DemTileStore
import com.ginsengo.steward.terrain.TerrainMath
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * B6: the 3D mesh is lit by the slope the habitat score uses.
 *
 * Two witnesses. A plane of known gradient, where the right normal is a formula (and a north/south
 * sign error cannot hide). And real Boone terrain, where a plane cannot decide anything (every
 * stencil is exact on a plane): there the mesh normal must equal the gradient rebuilt from the
 * analysis's own public answer, [TerrainMath.slopeAspect], at the four cells the vertex is
 * interpolated from, while the lattice stencil the mesh used before is shown to miss it.
 */
class MeshNormalsTest {

    private val halo = 32
    private val exaggeration = Terrain3D.EXAGGERATION
    private val buildCamera = MapCamera(36.2, -81.67, Terrain3D.BUILD_ZOOM, 0.0, 0.0, 1000, 1000)

    private fun mosaic(g: TerrainMath.Grid) = DemTileStore.Mosaic(g, 15, 8950, 12844, 1, 1, halo, 1, 1)

    private fun normal(m: TerrainMesh.Mesh, v: Int): DoubleArray {
        val b = v * TerrainMesh.FLOATS_PER_VERTEX + TerrainMesh.OFF_NORMAL
        return doubleArrayOf(m.vertices[b].toDouble(), m.vertices[b + 1].toDouble(), m.vertices[b + 2].toDouble())
    }

    private fun unit(x: Double, y: Double, z: Double): DoubleArray {
        val l = sqrt(x * x + y * y + z * z); return doubleArrayOf(x / l, y / l, z / l)
    }

    /** Angle between two directions, atan2(|a×b|, a·b): acos near 1 turns float rounding into 0.02°. */
    private fun angleDeg(a: DoubleArray, b: DoubleArray): Double {
        val cx = a[1] * b[2] - a[2] * b[1]; val cy = a[2] * b[0] - a[0] * b[2]; val cz = a[0] * b[1] - a[1] * b[0]
        return Math.toDegrees(atan2(sqrt(cx * cx + cy * cy + cz * cz), a[0] * b[0] + a[1] * b[1] + a[2] * b[2]))
    }

    /** z = 800 + a·east + b·north: every vertex's normal is (−a·e, +b·e, 1) normalised (mesh y runs south). */
    @Test
    fun aPlaneGetsItsExactNormalAtEveryVertex() {
        val cs = 3.86; val a = 0.30; val b = -0.20
        val w = 256; val h = 256
        val g = TerrainMath.Grid(w, h, FloatArray(w * h) { i -> (800 + a * (i % w) * cs + b * (h - 1 - i / w) * cs).toFloat() }, cs)
        val n = 48
        val m = TerrainMesh.build(mosaic(g), buildCamera, n, exaggeration)
        val want = unit(-a * exaggeration, b * exaggeration, 1.0)
        for (v in 0 until n * n) {
            val got = normal(m, v)
            assertTrue("vertex $v: ${got.toList()} vs ${want.toList()}", angleDeg(got, want) < 0.01)
        }
    }

    private fun boone(): TerrainMath.Grid {
        val stream = javaClass.getResourceAsStream("/terrain_boone_z15.bin")
        assertNotNull("missing terrain fixture", stream)
        DataInputStream(stream!!.buffered()).use { d ->
            val magic = ByteArray(8); d.readFully(magic)
            val w = d.readInt(); val h = d.readInt()
            return TerrainMath.Grid(w, h, FloatArray(w * h) { d.readShort().toFloat() }, 3.86)
        }
    }

    /** The gradient (east, south) the analysis reports at a cell, rebuilt from its slope and aspect. */
    private fun analysisGradient(g: TerrainMath.Grid, x: Int, y: Int): DoubleArray {
        val (slope, aspect) = TerrainMath.slopeAspect(g, x, y)
        if (aspect < 0) return doubleArrayOf(0.0, 0.0)
        val t = tan(Math.toRadians(slope)); val asp = Math.toRadians(aspect)
        // Aspect is the downhill direction, clockwise from north: uphill east = −sin, uphill north = −cos.
        return doubleArrayOf(-t * sin(asp), t * cos(asp))
    }

    @Test
    fun theMeshIsLitByTheSlopeTheScoreUses() {
        val g = boone()
        val n = 97
        val m = TerrainMesh.build(mosaic(g), buildCamera, n, exaggeration)
        val iw = g.w - 2 * halo; val ih = g.h - 2 * halo
        var worstNew = 0.0; var worstOld = 0.0
        fun pos(i: Int, j: Int, c: Int) =
            m.vertices[(j.coerceIn(0, n - 1) * n + i.coerceIn(0, n - 1)) * TerrainMesh.FLOATS_PER_VERTEX + TerrainMesh.OFF_POSITION + c].toDouble()
        for (j in 1 until n - 1) for (i in 1 until n - 1) {
            val gx = halo + i.toDouble() / (n - 1) * iw - 0.5; val gy = halo + j.toDouble() / (n - 1) * ih - 0.5
            val x0 = gx.toInt(); val y0 = gy.toInt(); val tx = gx - x0; val ty = gy - y0
            var dx = 0.0; var dy = 0.0
            for ((cx, cy, wgt) in listOf(Triple(x0, y0, (1 - tx) * (1 - ty)), Triple(x0 + 1, y0, tx * (1 - ty)),
                    Triple(x0, y0 + 1, (1 - tx) * ty), Triple(x0 + 1, y0 + 1, tx * ty))) {
                val gr = analysisGradient(g, cx, cy); dx += wgt * gr[0]; dy += wgt * gr[1]
            }
            val want = unit(-dx * exaggeration, -dy * exaggeration, 1.0)
            worstNew = maxOf(worstNew, angleDeg(normal(m, j * n + i), want))
            // The stencil the mesh used before B6: central differences over its own vertex lattice.
            val ddx = pos(i + 1, j, 0) - pos(i - 1, j, 0); val ddz = pos(i + 1, j, 2) - pos(i - 1, j, 2)
            val eyy = pos(i, j + 1, 1) - pos(i, j - 1, 1); val eyz = pos(i, j + 1, 2) - pos(i, j - 1, 2)
            worstOld = maxOf(worstOld, angleDeg(unit(-ddz / ddx, -eyz / eyy, 1.0), want))
        }
        assertTrue("mesh normals stray %.4f° from the analysis's slope".format(worstNew), worstNew < 0.01)
        assertTrue("the old lattice stencil should miss it by over 1° somewhere (%.3f°)".format(worstOld), worstOld > 1.0)
    }
}
