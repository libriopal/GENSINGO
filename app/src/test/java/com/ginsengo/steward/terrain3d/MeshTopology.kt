package com.ginsengo.steward.terrain3d

/**
 * The crack detector (exe.md B2): an edge-use oracle over a triangle index buffer.
 *
 * A closed, consistently wound surface uses every edge exactly twice, once in each direction.
 * So, with one pass over the triangles:
 *  - an edge used ONCE is open: a crack, a T-junction, a dropped triangle, a missing skirt, or
 *    (by design) the bottom of the skirt and the rim of a no-data hole;
 *  - an edge used twice in the SAME direction borders a triangle that faces the wrong way
 *    (B1's inward walls were found this way);
 *  - an edge used more than twice is non-manifold.
 *
 * Test-only on purpose: at runtime it would cost ~450k map entries per build for nothing a user
 * sees. The mesh it checks is the one [TerrainMesh.build] returns.
 */
object MeshTopology {

    class Report(
        /** Undirected edges used by exactly one triangle, as (low, high) vertex indices. */
        val openEdges: List<Pair<Int, Int>>,
        /** Edges two triangles walk in the same direction. */
        val sameDirection: List<Pair<Int, Int>>,
        /** Edges used by more than two triangles. */
        val overShared: List<Pair<Int, Int>>,
    )

    fun check(indices: IntArray): Report {
        val up = HashMap<Long, Int>()     // uses walking low -> high
        val down = HashMap<Long, Int>()   // uses walking high -> low
        fun use(a: Int, b: Int) {
            val lo = minOf(a, b); val hi = maxOf(a, b)
            val key = (lo.toLong() shl 32) or hi.toLong()
            (if (a < b) up else down).merge(key, 1, Int::plus)
        }
        for (t in indices.indices step 3) {
            val a = indices[t]; val b = indices[t + 1]; val c = indices[t + 2]
            use(a, b); use(b, c); use(c, a)
        }
        val open = ArrayList<Pair<Int, Int>>()
        val same = ArrayList<Pair<Int, Int>>()
        val over = ArrayList<Pair<Int, Int>>()
        for (key in up.keys + down.keys) {
            val u = up[key] ?: 0; val d = down[key] ?: 0
            val edge = (key ushr 32).toInt() to (key and 0xffffffffL).toInt()
            when {
                u + d == 1 -> open += edge
                u + d > 2 -> over += edge
                u == 2 || d == 2 -> same += edge
            }
        }
        return Report(open, same, over)
    }
}
