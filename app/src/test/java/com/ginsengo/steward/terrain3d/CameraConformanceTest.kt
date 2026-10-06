package com.ginsengo.steward.terrain3d

import com.ginsengo.steward.geo.Projection
import com.ginsengo.steward.terrain.DemTileStore
import com.ginsengo.steward.terrain.TerrainMath
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * exe.md A4: the camera conformance test. For one [CameraState], the two ways the app puts a
 * point of ground on the screen must agree:
 *  - the **mesh path**: a stored mesh vertex (origin-relative, build-zoom world pixels, height in
 *    exaggerated build-zoom pixels) through [MapCamera.mvpForMeshBuiltAt], exactly what the vertex
 *    shader computes;
 *  - the **projection path**: [MapCamera.project] of the same geographic point at its drawn
 *    height. The 3D markers, the track line and the pan maths use this. (Until M.1 the device's
 *    `AlignmentCheck` also held it to MapLibre's projection, for the flat map.)
 *
 * Ten sampled states (centre, zoom, bearing, pitch, ground plane), 25 vertices each, over a hilly
 * synthetic scene. **The oracle shows it can fail:** feeding the mesh path the previous state's
 * camera (a stale camera, the defect a second camera store would cause) must exceed the tolerance
 * by a wide margin. Mutant Q1 (= exe.md I3: bearing sign flipped in the mesh matrix) must fail it.
 */
class CameraConformanceTest {

    private val w = 1080
    private val h = 2400
    private val tolerancePx = 0.5

    private fun scene(): Terrain3D.Scene {
        val n = 3 * DemTileStore.TILE
        val g = TerrainMath.Grid(n, n, FloatArray(n * n) { i ->
            val x = (i % n).toDouble(); val y = (i / n).toDouble()
            (600 + 120 * sin(x / 40) * cos(y / 55) + 0.3 * x).toFloat()
        }, 7.8)
        return Terrain3D.build(DemTileStore.Mosaic(g, 14, 4475, 6422, 3, 3, DemTileStore.TILE, 9, 9))
    }

    private class Point(val lat: Double, val lng: Double, val e: Double, val x: Float, val y: Float, val z: Float)

    /** 25 mesh vertices with their geographic position recovered from the build-zoom world pixels. */
    private fun controlPoints(s: Terrain3D.Scene): List<Point> {
        val mesh = s.mesh
        val n = mesh.gridN
        val world = Projection.worldPx(Terrain3D.BUILD_ZOOM, MapCamera.TILE_SIZE)
        val picks = listOf(0, n / 4, n / 2, 3 * n / 4, n - 1)
        return picks.flatMap { j -> picks.map { i ->
            val b = (j * n + i) * TerrainMesh.FLOATS_PER_VERTEX
            val x = mesh.vertices[b + TerrainMesh.OFF_POSITION]
            val y = mesh.vertices[b + TerrainMesh.OFF_POSITION + 1]
            val z = mesh.vertices[b + TerrainMesh.OFF_POSITION + 2]
            Point(
                lat = Projection.lat((mesh.originWorldY + y) / world),
                lng = Projection.lng((mesh.originWorldX + x) / world),
                e = mesh.vertices[b + TerrainMesh.OFF_ELEVATION].toDouble(),
                x = x, y = y, z = z,
            )
        } }
    }

    /** What the vertex shader does with `u_mvp`: clip space, perspective divide, viewport. */
    private fun meshScreen(m: FloatArray, p: Point): DoubleArray? {
        val cx = m[0] * p.x + m[4] * p.y + m[8] * p.z + m[12]
        val cy = m[1] * p.x + m[5] * p.y + m[9] * p.z + m[13]
        val cw = m[3] * p.x + m[7] * p.y + m[11] * p.z + m[15]
        if (cw <= 1e-6f) return null
        return doubleArrayOf((cx / cw * 0.5 + 0.5) * w, (0.5 - cy / cw * 0.5) * h)
    }

    private fun states(s: Terrain3D.Scene): List<Pair<CameraState, Double>> {
        val rnd = Random(4)
        val m = s.mosaic
        return List(10) {
            val fx = 0.25 + 0.5 * rnd.nextDouble(); val fy = 0.25 + 0.5 * rnd.nextDouble()
            val lat = Terrain3D.latOfEdge(m, m.haloPx + fy * DemTileStore.TILE)
            val lng = Terrain3D.lngOfEdge(m, m.haloPx + fx * DemTileStore.TILE)
            val cam = CameraState(lat, lng, 13.5 + 3.5 * rnd.nextDouble(), 360 * rnd.nextDouble(), 72 * rnd.nextDouble())
            cam to s.elevationAt(lat, lng)!!
        }
    }

    /** Largest disagreement over the control points, with the mesh path drawn from [meshCam]. */
    private fun worstPx(s: Terrain3D.Scene, cam: CameraState, anchor: Double, meshCam: CameraState, meshAnchor: Double): Pair<Double, Int> {
        val view = CameraMath.mapCamera(cam, w, h)
        val meshView = CameraMath.mapCamera(meshCam, w, h)
        val mvp = meshView.mvpForMeshBuiltAt(
            Terrain3D.BUILD_ZOOM, s.mesh.originWorldX, s.mesh.originWorldY,
            groundZ = meshAnchor * s.mesh.pixelsPerMeter * Terrain3D.EXAGGERATION,
        )
        var worst = 0.0; var compared = 0
        for (p in controlPoints(s)) {
            val a = view.project(p.lat, p.lng, (p.e - anchor) * Terrain3D.EXAGGERATION) ?: continue
            val b = meshScreen(mvp, p) ?: continue
            if (a[0] !in -w.toFloat()..2f * w || a[1] !in -h.toFloat()..2f * h) continue   // far off screen
            worst = maxOf(worst, hypot(a[0] - b[0], a[1] - b[1]))
            compared++
        }
        return worst to compared
    }

    @Test
    fun theMeshAndTheProjectionPlaceTheSameGroundOnTheSamePixel() {
        val s = scene()
        var compared = 0
        for ((cam, anchor) in states(s)) {
            val (worst, n) = worstPx(s, cam, anchor, cam, anchor)
            compared += n
            println("conformance: zoom %.2f bearing %5.1f pitch %4.1f -> worst %.4f px over %d points"
                .format(cam.zoom, cam.bearing, cam.pitch, worst, n))
            assertTrue("state $cam: mesh and projection disagree by %.3f px".format(worst), worst <= tolerancePx)
        }
        assertTrue("too few points compared ($compared) for the test to mean anything", compared >= 150)
    }

    /** The negative control, inside the test: the oracle can fail. */
    @Test
    fun aStaleCameraIsCaught() {
        val s = scene()
        val st = states(s)
        for (k in 1 until st.size) {
            val (cam, anchor) = st[k]
            val (stale, staleAnchor) = st[k - 1]
            val (worst, _) = worstPx(s, cam, anchor, stale, staleAnchor)
            println("conformance (stale camera, must fail): state $k -> worst %.1f px".format(worst))
            assertTrue("a stale camera at state $k gave only %.2f px".format(worst), worst > 50 * tolerancePx)
        }
    }
}
