package com.ginsengo.steward.terrain3d

import com.ginsengo.steward.terrain.DemTileStore
import com.ginsengo.steward.terrain.GinsengSuitability
import com.ginsengo.steward.terrain.TerrainAnalysis
import com.ginsengo.steward.terrain.TerrainMath
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Builds a terrain mesh from a DEM mosaic: positions, normals, elevation and per-vertex
 * ginseng suitability, ready to upload as one interleaved vertex buffer.
 *
 * Pure Kotlin and fully unit-tested. That is deliberate — everything that can be wrong
 * about a terrain mesh *except* the GL calls themselves lives here, so the part that cannot
 * be tested without a device is kept as small and as dumb as possible.
 *
 * FLOATING-POINT PRECISION
 * Vertex positions are stored RELATIVE TO A LOCAL ORIGIN, not in absolute world pixels.
 * At zoom 15 the Web Mercator pixel space is 512 * 2^15 = 16,777,216 units across. float32
 * carries about 7 significant decimal digits, so an absolute coordinate near 16.7 million
 * resolves to roughly 1-2 units — metres of jitter, and vertices visibly snapping as the
 * camera moves. Subtracting a local origin keeps every stored coordinate small, and the
 * origin is folded back into the MVP on the CPU in double precision. This is the precision
 * problem `DESIGN_LOD_HYBRID.md` flagged and is the reason [originWorldX]/[originWorldY]
 * exist.
 */
object TerrainMesh {

    /** Floats per vertex: position(3) + normal(3) + elevation(1) + suitability(1). */
    const val FLOATS_PER_VERTEX = 8
    const val STRIDE_BYTES = FLOATS_PER_VERTEX * 4

    const val OFF_POSITION = 0
    const val OFF_NORMAL = 3
    const val OFF_ELEVATION = 6
    const val OFF_SUITABILITY = 7

    class Mesh(
        val vertices: FloatArray,
        val indices: IntArray,
        val vertexCount: Int,
        val gridN: Int,
        /** Local origin in world pixel space; add this back in the MVP. */
        val originWorldX: Double,
        val originWorldY: Double,
        val pixelsPerMeter: Double,
        val minElevationM: Float,
        val maxElevationM: Float,
        val skirtDepthPx: Float,
    ) {
        val triangleCount: Int get() = indices.size / 3
    }

    /**
     * ADAPTIVE SCOPE. Vertex density follows camera zoom, for the same reason the heatmap's
     * radius does: close in, the digger is reading one hillside and wants the benches and
     * hollows resolved; zoomed out, a dense mesh is millions of triangles describing terrain
     * that is three pixels tall on screen.
     */
    fun gridSizeFor(cameraZoom: Double): Int = when {
        cameraZoom >= 15.0 -> 192
        cameraZoom >= 13.0 -> 160
        cameraZoom >= 11.0 -> 128
        else -> 96
    }

    /**
     * @param mosaic     elevation, halo included (the halo is sampled, never displayed)
     * @param camera     supplies the world-pixel scale and altitude scale
     * @param gridN      vertices per edge
     * @param tpiRadiusM neighbourhood radius for the suitability surface, in metres
     * @param exaggeration vertical multiplier; 1.0 is true scale
     */
    fun build(
        mosaic: DemTileStore.Mosaic,
        camera: MapCamera,
        gridN: Int,
        tpiRadiusM: Double,
        exaggeration: Float = 1.0f,
        withSuitability: Boolean = true,
    ): Mesh {
        val g = mosaic.grid
        val halo = mosaic.haloPx
        val iw = g.w - 2 * halo
        val ih = g.h - 2 * halo

        val tpiRadiusCells = (tpiRadiusM / g.cellSizeM).roundToInt().coerceIn(1, 60)
        // Cached per mosaic: flow accumulation and the summed-area table are the expensive
        // parts and neither depends on the camera, so a pan that stays on the same elevation
        // tiles reuses them instead of paying ~224 ms again.
        val analysis = TerrainAnalysis.of(mosaic, withWetness = withSuitability)

        // Geographic bounds of the displayed (halo-cropped) region.
        val north = mosaic.northLat + (mosaic.southLat - mosaic.northLat) * (halo.toDouble() / g.h)
        val south = mosaic.northLat + (mosaic.southLat - mosaic.northLat) * ((halo + ih).toDouble() / g.h)
        val west = mosaic.westLon + (mosaic.eastLon - mosaic.westLon) * (halo.toDouble() / g.w)
        val east = mosaic.westLon + (mosaic.eastLon - mosaic.westLon) * ((halo + iw).toDouble() / g.w)

        val originX = camera.worldX((west + east) / 2.0)
        val originY = camera.worldY((north + south) / 2.0)

        val n = gridN
        val interior = n * n
        // Skirt: one extra ring of vertices dropped below the surface, so the seam between
        // adjacent mesh loads shows no see-through crack when they disagree by a metre.
        val skirtCount = 4 * n
        val total = interior + skirtCount

        val verts = FloatArray(total * FLOATS_PER_VERTEX)
        var minE = Float.MAX_VALUE
        var maxE = -Float.MAX_VALUE

        // Sample the DEM on the vertex lattice.
        val elev = FloatArray(interior)
        val suit = FloatArray(interior)
        for (j in 0 until n) {
            val fy = j.toDouble() / (n - 1)
            val gy = halo + fy * (ih - 1)
            for (i in 0 until n) {
                val fx = i.toDouble() / (n - 1)
                val gx = halo + fx * (iw - 1)
                val xi = gx.toInt().coerceIn(1, g.w - 2)
                val yi = gy.toInt().coerceIn(1, g.h - 2)
                val e = bilinear(g, gx, gy)
                elev[j * n + i] = e.toFloat()
                if (e < minE) minE = e.toFloat()
                if (e > maxE) maxE = e.toFloat()

                suit[j * n + i] = if (!withSuitability) 0f else {
                    val (slopeDeg, aspectDeg) = TerrainMath.slopeAspect(g, xi, yi)
                    GinsengSuitability.score(
                        heatLoadRaw = TerrainMath.heatLoadIndex(
                            mosaic.latAtRow(yi), slopeDeg, aspectDeg
                        ),
                        tpiMeters = analysis.tpi(xi, yi, tpiRadiusCells),
                        twi = analysis.twiAt(xi, yi),
                        slopeDeg = slopeDeg,
                        curvature = TerrainMath.profileCurvature(g, xi, yi),
                        elevationM = e,
                    ).score.toFloat()
                }
            }
        }

        // Positions, relative to the local origin.
        for (j in 0 until n) {
            val fy = j.toDouble() / (n - 1)
            val lat = north + (south - north) * fy
            val wy = camera.worldY(lat) - originY
            for (i in 0 until n) {
                val fx = i.toDouble() / (n - 1)
                val lng = west + (east - west) * fx
                val wx = camera.worldX(lng) - originX
                val e = elev[j * n + i]
                val base = (j * n + i) * FLOATS_PER_VERTEX
                verts[base + OFF_POSITION] = wx.toFloat()
                verts[base + OFF_POSITION + 1] = wy.toFloat()
                verts[base + OFF_POSITION + 2] =
                    (e * camera.pixelsPerMeter * exaggeration).toFloat()
                verts[base + OFF_ELEVATION] = e
                verts[base + OFF_SUITABILITY] = suit[j * n + i]
            }
        }

        computeNormals(verts, n)

        // Skirt vertices: copy the boundary ring, pushed down.
        val relief = (maxE - minE).coerceAtLeast(1f)
        val skirtDepth = (relief * camera.pixelsPerMeter * 0.5).toFloat().coerceAtLeast(1f)
        var s = interior
        val skirtIndexOf = HashMap<Int, Int>(skirtCount * 2)
        fun addSkirt(srcIdx: Int) {
            val src = srcIdx * FLOATS_PER_VERTEX
            val dst = s * FLOATS_PER_VERTEX
            System.arraycopy(verts, src, verts, dst, FLOATS_PER_VERTEX)
            verts[dst + OFF_POSITION + 2] = verts[src + OFF_POSITION + 2] - skirtDepth
            skirtIndexOf[srcIdx] = s
            s++
        }
        for (i in 0 until n) addSkirt(i)                       // north edge
        for (i in 0 until n) addSkirt((n - 1) * n + i)         // south edge
        for (j in 0 until n) addSkirt(j * n)                   // west edge
        for (j in 0 until n) addSkirt(j * n + (n - 1))         // east edge

        // Indices: surface triangles plus skirt quads.
        val surfaceTris = (n - 1) * (n - 1) * 2
        val skirtTris = 4 * (n - 1) * 2
        val idx = IntArray((surfaceTris + skirtTris) * 3)
        var k = 0
        for (j in 0 until n - 1) {
            for (i in 0 until n - 1) {
                val a = j * n + i
                val b = j * n + i + 1
                val c = (j + 1) * n + i
                val d = (j + 1) * n + i + 1
                idx[k++] = a; idx[k++] = c; idx[k++] = b
                idx[k++] = b; idx[k++] = c; idx[k++] = d
            }
        }
        fun skirtStrip(edge: List<Int>) {
            for (t in 0 until edge.size - 1) {
                val a = edge[t]
                val b = edge[t + 1]
                val a2 = skirtIndexOf[a] ?: continue
                val b2 = skirtIndexOf[b] ?: continue
                idx[k++] = a; idx[k++] = a2; idx[k++] = b
                idx[k++] = b; idx[k++] = a2; idx[k++] = b2
            }
        }
        skirtStrip((0 until n).map { it })
        skirtStrip((0 until n).map { (n - 1) * n + it })
        skirtStrip((0 until n).map { it * n })
        skirtStrip((0 until n).map { it * n + (n - 1) })

        return Mesh(
            vertices = verts,
            indices = if (k == idx.size) idx else idx.copyOf(k),
            vertexCount = total,
            gridN = n,
            originWorldX = originX,
            originWorldY = originY,
            pixelsPerMeter = camera.pixelsPerMeter,
            minElevationM = if (minE == Float.MAX_VALUE) 0f else minE,
            maxElevationM = if (maxE == -Float.MAX_VALUE) 0f else maxE,
            skirtDepthPx = skirtDepth,
        )
    }

    /**
     * Central-difference normals over the vertex lattice.
     *
     * Computed here rather than in the vertex shader because a shader would need the
     * neighbouring heights, which means either a height texture or a geometry stage. Doing
     * it once on the CPU costs one pass over a mesh that is rebuilt only when the camera
     * settles, and keeps the shader trivial enough to reason about without running it.
     */
    private fun computeNormals(verts: FloatArray, n: Int) {
        fun pos(i: Int, j: Int, c: Int): Float {
            val ii = i.coerceIn(0, n - 1)
            val jj = j.coerceIn(0, n - 1)
            return verts[(jj * n + ii) * FLOATS_PER_VERTEX + OFF_POSITION + c]
        }
        for (j in 0 until n) {
            for (i in 0 until n) {
                val dxx = pos(i + 1, j, 0) - pos(i - 1, j, 0)
                val dxz = pos(i + 1, j, 2) - pos(i - 1, j, 2)
                val dyy = pos(i, j + 1, 1) - pos(i, j - 1, 1)
                val dyz = pos(i, j + 1, 2) - pos(i, j - 1, 2)
                // tangents: (dxx, 0, dxz) along +x, (0, dyy, dyz) along +y
                var nx = -dxz * dyy
                var ny = -dxx * dyz
                var nz = dxx * dyy
                val len = sqrt(nx * nx + ny * ny + nz * nz)
                if (len > 1e-9f) { nx /= len; ny /= len; nz /= len } else { nx = 0f; ny = 0f; nz = 1f }
                // World z is up, so a flat surface must yield a normal pointing at +z.
                if (nz < 0f) { nx = -nx; ny = -ny; nz = -nz }
                val b = (j * n + i) * FLOATS_PER_VERTEX + OFF_NORMAL
                verts[b] = nx; verts[b + 1] = ny; verts[b + 2] = nz
            }
        }
    }

    private fun bilinear(g: TerrainMath.Grid, x: Double, y: Double): Double {
        val x0 = x.toInt().coerceIn(0, g.w - 2)
        val y0 = y.toInt().coerceIn(0, g.h - 2)
        val tx = (x - x0).coerceIn(0.0, 1.0)
        val ty = (y - y0).coerceIn(0.0, 1.0)
        val v00 = g[x0, y0].toDouble(); val v10 = g[x0 + 1, y0].toDouble()
        val v01 = g[x0, y0 + 1].toDouble(); val v11 = g[x0 + 1, y0 + 1].toDouble()
        return (v00 * (1 - tx) + v10 * tx) * (1 - ty) + (v01 * (1 - tx) + v11 * tx) * ty
    }
}
