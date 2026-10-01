package com.ginsengo.steward.terrain3d

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.tan

/**
 * Reconstructs MapLibre's camera as a model-view-projection matrix, so a separate GL
 * surface can draw geometry that lines up with the map underneath it.
 *
 * WHY THIS FILE EXISTS AT ALL
 * `DESIGN_LOD_HYBRID.md` assumed the renderer could "accept the ModelViewProjection matrix
 * directly from the MapLibre camera". It cannot: MapLibre Android's [Projection] exposes
 * `toScreenLocation`, `fromScreenLocation` and `getMetersPerPixelAtLatitude`, and no matrix
 * of any kind. `CustomLayer`, which would render inside MapLibre's own GL context and get
 * the matrix for free, takes a `long` native pointer to a C++ host object and is
 * unreachable from Kotlin without NDK code. So the overlay has to rebuild the same camera
 * from the public CameraPosition, and then prove it agrees.
 *
 * THE ALGORITHM
 * This mirrors MapLibre's transform exactly:
 *
 *   worldSize = TILE_SIZE * 2^zoom            (Web Mercator pixel space, y increasing south)
 *   cameraToCenterDistance = 0.5 / tan(fov/2) * viewportHeight
 *   M = perspective(fov, w/h, near, far)
 *       * scale(1, -1, 1)                     (mercator y is down; GL y is up)
 *       * translate(0, 0, -cameraToCenterDistance)
 *       * rotateX(pitch)
 *       * rotateZ(bearingRadians)
 *       * translate(-centerX, -centerY, 0)
 *
 * Vertex positions are in world pixel units, with z positive UPWARD (towards the camera at
 * zero pitch), so elevation enters as `metres * pixelsPerMeter`.
 *
 * HOW IT IS CHECKED
 * Two independent witnesses, because a projection that is subtly wrong still draws
 * something plausible — it just sits a few metres off the ground, which is the one failure
 * mode a screenshot will not reveal:
 *
 *  1. Offline: geometric invariants that hold for ANY correct implementation of this
 *     projection, not for a re-derivation of my own arithmetic (see MapCameraTest).
 *  2. On device: [AlignmentCheck] projects known coordinates through this matrix and
 *     compares against MapLibre's own `Projection.toScreenLocation`. That is a genuinely
 *     independent implementation of the same transform, and if the residual is too large
 *     the overlay hides itself rather than drawing a misaligned mesh.
 */
class MapCamera(
    val centerLat: Double,
    val centerLng: Double,
    val zoom: Double,
    /** Degrees clockwise from north, matching MapLibre's CameraPosition.bearing. */
    val bearingDeg: Double,
    /** Degrees from vertical, matching CameraPosition.tilt. */
    val pitchDeg: Double,
    val viewportWidth: Int,
    val viewportHeight: Int,
) {
    val worldSize: Double = TILE_SIZE * Math.pow(2.0, zoom)

    /** Camera target in world pixel coordinates. */
    val centerX: Double = mercatorX(centerLng) * worldSize
    val centerY: Double = mercatorY(centerLat) * worldSize

    /**
     * Pixels per metre of ALTITUDE at the camera's latitude. Mercator is conformal, so
     * the vertical exaggeration must use the same scale as horizontal distance or the
     * terrain comes out stretched.
     */
    val pixelsPerMeter: Double =
        worldSize / (EARTH_CIRCUMFERENCE * cos(Math.toRadians(centerLat)))

    val cameraToCenterDistance: Double = 0.5 / tan(FOV / 2.0) * viewportHeight

    /** Ground resolution — must agree with Projection.getMetersPerPixelAtLatitude. */
    val metersPerPixel: Double =
        EARTH_CIRCUMFERENCE * cos(Math.toRadians(centerLat)) / worldSize

    private val pitch = Math.toRadians(pitchDeg.coerceIn(0.0, 85.0))

    /** MapLibre's internal angle is the negation of the user-facing bearing. */
    private val angle = -Math.toRadians(bearingDeg)

    val nearZ: Double = viewportHeight / 50.0

    val farZ: Double = run {
        // Distance to the furthest visible ground point, which grows quickly with pitch.
        // Clamped so a near-horizontal camera cannot push the far plane to infinity and
        // collapse depth precision.
        val halfFov = FOV / 2.0
        val groundAngle = PI / 2.0 + pitch
        val denom = sin((PI - groundAngle - halfFov).coerceIn(0.01, PI - 0.01))
        val topHalfSurfaceDistance = sin(halfFov) * cameraToCenterDistance / denom
        val furthest = cos(PI / 2.0 - pitch) * topHalfSurfaceDistance + cameraToCenterDistance
        minOf(furthest * 1.01, cameraToCenterDistance * HORIZON_LIMIT)
    }

    /**
     * The absolute matrix in DOUBLE precision.
     *
     * It has to stay double for [project]. The translation column carries the camera centre
     * in world pixels, which is ~33.5 million at zoom 17, and float32's step size there is
     * 4 world pixels. Because world pixels shrink as zoom grows, that works out at a
     * constant ~1.9 m of ground error at EVERY zoom level — enough to consume a third of
     * [AlignmentCheck]'s tolerance and occasionally trip it for no reason. The instrument
     * would have been reporting its own rounding as a map misalignment.
     */
    private fun vpMatrixDouble(): DoubleArray {
        var m = perspective(FOV, viewportWidth.toDouble() / viewportHeight, nearZ, farZ)
        m = m * scale(1.0, -1.0, 1.0)
        m = m * translate(0.0, 0.0, -cameraToCenterDistance)
        m = m * rotateX(pitch)
        m = m * rotateZ(angle)
        m = m * translate(-centerX, -centerY, 0.0)
        return m
    }

    /**
     * Column-major 4x4 for glUniformMatrix4fv.
     *
     * Only safe for geometry stored relative to a local origin — see [mvpForOrigin]. Uploading
     * this one with absolute world coordinates reintroduces exactly the precision loss the
     * local origin exists to avoid.
     */
    fun viewProjectionMatrix(): FloatArray {
        val m = vpMatrixDouble()
        return FloatArray(16) { m[it].toFloat() }
    }

    /**
     * MVP for geometry whose vertices are stored relative to [originX], [originY].
     *
     * The origin translation is applied HERE, in double precision, and only the small
     * relative coordinates ever reach float32. Folding it into the matrix instead of adding
     * it per-vertex is what keeps a zoom-15 mesh from jittering by metres — see the
     * precision note in TerrainMesh.
     */
    fun mvpForOrigin(originX: Double, originY: Double): FloatArray {
        var m = perspective(FOV, viewportWidth.toDouble() / viewportHeight, nearZ, farZ)
        m = m * scale(1.0, -1.0, 1.0)
        m = m * translate(0.0, 0.0, -cameraToCenterDistance)
        m = m * rotateX(pitch)
        m = m * rotateZ(angle)
        m = m * translate(originX - centerX, originY - centerY, 0.0)
        return FloatArray(16) { m[it].toFloat() }
    }

    /**
     * MVP for a mesh built at [buildZoom] and viewed at this camera's zoom.
     *
     * World pixels double per zoom level, so a mesh built once can be viewed at any zoom by
     * scaling its (origin-relative) vertices by 2^(zoom - buildZoom), including height,
     * which was also stored in build-zoom pixels. That is what lets the standalone 3D view
     * pinch-zoom without rebuilding the mesh on every frame. [originX]/[originY] are in
     * BUILD-zoom world pixels, as [TerrainMesh.Mesh] stores them.
     */
    fun mvpForMeshBuiltAt(buildZoom: Double, originX: Double, originY: Double, groundZ: Double = 0.0): FloatArray {
        val k = Math.pow(2.0, zoom - buildZoom)
        var m = perspective(FOV, viewportWidth.toDouble() / viewportHeight, nearZ, farZ)
        m = m * scale(1.0, -1.0, 1.0)
        m = m * translate(0.0, 0.0, -cameraToCenterDistance)
        m = m * rotateX(pitch)
        m = m * rotateZ(angle)
        m = m * translate(originX * k - centerX, originY * k - centerY, 0.0)
        m = m * scale(k, k, k)
        // [groundZ] (build-zoom pixels) is lowered to the camera's target plane, so the camera
        // looks at the ground under the user rather than at sea level beneath it: with ~1 km
        // of exaggerated elevation in between, the model floated in the top half of the screen.
        m = m * translate(0.0, 0.0, -groundZ)
        return FloatArray(16) { m[it].toFloat() }
    }

    /** World pixel coordinates for a geographic position. */
    fun worldX(lng: Double): Double = mercatorX(lng) * worldSize
    fun worldY(lat: Double): Double = mercatorY(lat) * worldSize

    /**
     * Projects a geographic position to screen pixels, or null when it falls behind the
     * camera. Used by [AlignmentCheck] and by nothing else — the GPU does this for real
     * geometry.
     */
    fun project(lat: Double, lng: Double, elevationM: Double = 0.0): FloatArray? =
        projectWith(vpMatrixDouble(), lat, lng, elevationM)

    /**
     * [project] for many points (the 3D view's track line): the matrix is built once, not
     * once per point.
     */
    fun projector(): (lat: Double, lng: Double, elevationM: Double) -> FloatArray? {
        val m = vpMatrixDouble()
        return { lat, lng, e -> projectWith(m, lat, lng, e) }
    }

    private fun projectWith(m: DoubleArray, lat: Double, lng: Double, elevationM: Double): FloatArray? {
        val x = worldX(lng)
        val y = worldY(lat)
        val z = elevationM * pixelsPerMeter
        val clipX = m[0] * x + m[4] * y + m[8] * z + m[12]
        val clipY = m[1] * x + m[5] * y + m[9] * z + m[13]
        val clipW = m[3] * x + m[7] * y + m[11] * z + m[15]
        if (clipW <= 1e-6) return null
        val ndcX = clipX / clipW
        val ndcY = clipY / clipW
        return floatArrayOf(
            ((ndcX * 0.5 + 0.5) * viewportWidth).toFloat(),
            ((0.5 - ndcY * 0.5) * viewportHeight).toFloat(),
        )
    }

    /**
     * The inverse of [project] onto the horizontal plane at [heightM] (the same convention as
     * project's `elevationM`): the (lat, lng) that this camera shows at screen pixel
     * ([screenX], [screenY]) at that height, or null when the ray misses the plane (above
     * the horizon, or a plane above the camera).
     *
     * The ray is built by undoing each factor of [vpMatrixDouble] in reverse order,
     * analytically and relative to the centre, and the centre is added back last. Inverting
     * the absolute 4x4 matrix numerically would mix the ~10^7 world-pixel translation into
     * every term (the precision trap described there). A hit is accepted under the same rule
     * project uses: clip w > 1e-6, i.e. in front of the camera.
     */
    fun unproject(screenX: Double, screenY: Double, heightM: Double = 0.0): DoubleArray? {
        // Screen -> NDC -> eye-space ray direction (eye at the origin, looking down -z).
        val ndcX = 2.0 * screenX / viewportWidth - 1.0
        val ndcY = 1.0 - 2.0 * screenY / viewportHeight
        val f = 1.0 / tan(FOV / 2.0)
        val aspect = viewportWidth.toDouble() / viewportHeight
        val ex = ndcX * aspect / f
        val ey = -(ndcY / f)          // undo scale(1, -1, 1)
        val ez = -1.0
        // Undo translate(0, 0, -cameraToCenterDistance): the eye sits at (0, 0, d).
        val d = cameraToCenterDistance
        // Undo rotateX(pitch).
        val cp = cos(pitch); val sp = sin(pitch)
        val eyeY1 = sp * d; val eyeZ1 = cp * d
        val rx1 = ex
        val ry1 = cp * ey + sp * ez
        val rz1 = -sp * ey + cp * ez
        // Undo rotateZ(angle).
        val ca = cos(angle); val sa = sin(angle)
        val eyeX0 = sa * eyeY1; val eyeY0 = ca * eyeY1
        val rx0 = ca * rx1 + sa * ry1
        val ry0 = -sa * rx1 + ca * ry1
        // Intersect with the plane z = heightM * pixelsPerMeter (centre-relative world pixels).
        if (abs(rz1) < 1e-12) return null
        val t = (heightM * pixelsPerMeter - eyeZ1) / rz1
        // Clip w equals t here, because the eye-space direction has z = -1.
        if (t <= 1e-6) return null
        val x = centerX + eyeX0 + t * rx0
        val y = centerY + eyeY0 + t * ry0
        return doubleArrayOf(latFromMercatorY(y / worldSize), lngFromMercatorX(x / worldSize))
    }

    companion object {
        /** MapLibre's vector tile size; the whole pixel space is defined against it. */
        const val TILE_SIZE = 512.0

        /** MapLibre's default field of view: 2*atan(1/3). */
        const val FOV = 0.6435011087932844

        const val EARTH_CIRCUMFERENCE = 40_075_016.686

        /** Keeps the far plane finite as pitch approaches the horizon. */
        private const val HORIZON_LIMIT = 40.0

        fun mercatorX(lng: Double): Double = (180.0 + lng) / 360.0

        fun mercatorY(lat: Double): Double {
            val l = lat.coerceIn(-85.051129, 85.051129)
            return (180.0 -
                    (180.0 / PI) * ln(tan(PI / 4.0 + l * PI / 360.0))) / 360.0
        }

        fun lngFromMercatorX(x: Double): Double = x * 360.0 - 180.0

        fun latFromMercatorY(y: Double): Double {
            val y2 = 180.0 - y * 360.0
            return 360.0 / PI * kotlin.math.atan(kotlin.math.exp(y2 * PI / 180.0)) - 90.0
        }
    }
}

// ---------------------------------------------------------------- tiny matrix helpers
// Column-major, matching OpenGL. Deliberately local: pulling in a matrix library for six
// operations would add a dependency whose conventions then have to be verified too.

internal operator fun DoubleArray.times(o: DoubleArray): DoubleArray {
    val r = DoubleArray(16)
    for (c in 0 until 4) for (row in 0 until 4) {
        var s = 0.0
        for (k in 0 until 4) s += this[k * 4 + row] * o[c * 4 + k]
        r[c * 4 + row] = s
    }
    return r
}

internal fun identity(): DoubleArray = doubleArrayOf(
    1.0, 0.0, 0.0, 0.0,
    0.0, 1.0, 0.0, 0.0,
    0.0, 0.0, 1.0, 0.0,
    0.0, 0.0, 0.0, 1.0,
)

internal fun translate(x: Double, y: Double, z: Double): DoubleArray =
    identity().also { it[12] = x; it[13] = y; it[14] = z }

internal fun scale(x: Double, y: Double, z: Double): DoubleArray =
    identity().also { it[0] = x; it[5] = y; it[10] = z }

internal fun rotateX(a: Double): DoubleArray {
    val c = cos(a); val s = sin(a)
    return identity().also { it[5] = c; it[6] = s; it[9] = -s; it[10] = c }
}

internal fun rotateZ(a: Double): DoubleArray {
    val c = cos(a); val s = sin(a)
    return identity().also { it[0] = c; it[1] = s; it[4] = -s; it[5] = c }
}

internal fun perspective(fovY: Double, aspect: Double, near: Double, far: Double): DoubleArray {
    val f = 1.0 / tan(fovY / 2.0)
    val m = DoubleArray(16)
    m[0] = f / aspect
    m[5] = f
    m[10] = (far + near) / (near - far)
    m[11] = -1.0
    m[14] = (2.0 * far * near) / (near - far)
    return m
}
