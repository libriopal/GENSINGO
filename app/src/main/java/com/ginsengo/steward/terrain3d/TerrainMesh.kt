package com.ginsengo.steward.terrain3d

import com.ginsengo.steward.terrain.DemTileStore
import com.ginsengo.steward.terrain.TerrainMath
import kotlin.math.sqrt

/**
 * Builds a terrain mesh from a DEM mosaic: positions, normals, elevation and texture
 * coordinates, ready to upload as one interleaved vertex buffer. Colour comes from the
 * texture [TerrainTextures] bakes over the same interior (Phase 8): the first version tinted
 * each vertex from its own suitability, every ~60 m, and the heatmap did not survive it.
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

    /** Floats per vertex: position(3) + normal(3) + elevation(1) + texture uv(2) + wall(1). */
    const val FLOATS_PER_VERTEX = 10
    const val STRIDE_BYTES = FLOATS_PER_VERTEX * 4

    const val OFF_POSITION = 0
    const val OFF_NORMAL = 3
    const val OFF_ELEVATION = 6
    const val OFF_UV = 7
    /** 0 on the surface, 1 on the base of the skirt: walls get an earth colour, not stretched texels. */
    const val OFF_WALL = 9

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
     * @param exaggeration vertical multiplier; 1.0 is true scale
     *
     * Vertex (i, j) sits at fraction (i, j) / (gridN - 1) of the interior, edge to edge, with
     * texture coordinate equal to that fraction and elevation sampled where [TerrainTextures]
     * samples the same fraction, so colour and geometry agree to the cell.
     */
    fun build(
        mosaic: DemTileStore.Mosaic,
        camera: MapCamera,
        gridN: Int,
        exaggeration: Float = 1.0f,
    ): Mesh {
        val g = mosaic.grid
        val halo = mosaic.haloPx
        val iw = g.w - 2 * halo
        val ih = g.h - 2 * halo

        // Geographic bounds of the displayed (halo-cropped) region. Rows are linear in
        // Mercator y, so positions are interpolated in world pixels, not in latitude.
        val north = mosaic.northLat + (mosaic.southLat - mosaic.northLat) * (halo.toDouble() / g.h)
        val south = mosaic.northLat + (mosaic.southLat - mosaic.northLat) * ((halo + ih).toDouble() / g.h)
        val west = mosaic.westLon + (mosaic.eastLon - mosaic.westLon) * (halo.toDouble() / g.w)
        val east = mosaic.westLon + (mosaic.eastLon - mosaic.westLon) * ((halo + iw).toDouble() / g.w)
        val nY = camera.worldY(mosaic.northLat); val sY = camera.worldY(mosaic.southLat)
        val topY = nY + (sY - nY) * (halo.toDouble() / g.h)
        val bottomY = nY + (sY - nY) * ((halo + ih).toDouble() / g.h)

        val originX = camera.worldX((west + east) / 2.0)
        val originY = (topY + bottomY) / 2.0

        val n = gridN
        val interior = n * n
        // Skirt: one extra ring of vertices dropped below the surface, so the edge of the
        // model reads as a solid block instead of a paper-thin sheet.
        val skirtCount = 4 * n
        val total = interior + skirtCount

        val verts = FloatArray(total * FLOATS_PER_VERTEX)
        var minE = Float.MAX_VALUE
        var maxE = -Float.MAX_VALUE

        for (j in 0 until n) {
            val fy = j.toDouble() / (n - 1)
            val gy = halo + fy * ih - 0.5
            val wy = topY + (bottomY - topY) * fy - originY
            for (i in 0 until n) {
                val fx = i.toDouble() / (n - 1)
                val gx = halo + fx * iw - 0.5
                val e = bilinear(g, gx, gy).toFloat()
                if (e < minE) minE = e
                if (e > maxE) maxE = e
                val wx = camera.worldX(west + (east - west) * fx) - originX
                val base = (j * n + i) * FLOATS_PER_VERTEX
                verts[base + OFF_POSITION] = wx.toFloat()
                verts[base + OFF_POSITION + 1] = wy.toFloat()
                verts[base + OFF_POSITION + 2] = (e * camera.pixelsPerMeter * exaggeration).toFloat()
                verts[base + OFF_ELEVATION] = e
                verts[base + OFF_UV] = fx.toFloat()
                verts[base + OFF_UV + 1] = fy.toFloat()
            }
        }

        computeNormals(verts, n)

        // Skirt vertices: copy the boundary ring, pushed down.
        // A flat base below the lowest point, so the model reads as a solid block of ground.
        val relief = (maxE - minE).coerceAtLeast(1f)
        val baseM = minE - maxOf(relief * 0.12f, 20f)
        val baseZ = (baseM * camera.pixelsPerMeter * exaggeration).toFloat()
        val skirtDepth = ((minE - baseM) * camera.pixelsPerMeter * exaggeration).toFloat()
        var s = interior
        val skirtIndexOf = HashMap<Int, Int>(skirtCount * 2)
        fun addSkirt(srcIdx: Int) {
            val src = srcIdx * FLOATS_PER_VERTEX
            val dst = s * FLOATS_PER_VERTEX
            System.arraycopy(verts, src, verts, dst, FLOATS_PER_VERTEX)
            verts[dst + OFF_POSITION + 2] = baseZ
            verts[dst + OFF_WALL] = 1f
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
