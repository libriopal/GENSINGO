package com.ginsengo.steward.terrain

import org.junit.Assert.assertArrayEquals
import org.junit.Test
import kotlin.random.Random

/** The primitive sort must reproduce the boxed stable sort exactly, ties and edge values included. */
class DescendingOrderTest {

    private fun boxed(v: FloatArray) = (v.indices).sortedByDescending { v[it] }.toIntArray()

    @Test
    fun matchesTheBoxedStableSortOnFlatsAndEdgeValues() {
        val r = Random(1)
        repeat(200) {
            // Coarse values force many ties, as flats in a DEM do.
            val v = FloatArray(500) { (r.nextInt(20) - 10).toFloat() }
            assertArrayEquals(boxed(v), TerrainMath.descendingOrder(v))
        }
        val edge = floatArrayOf(0f, -0f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
            1f, -1f, Float.MIN_VALUE, -Float.MIN_VALUE, 0f, Float.NaN)
        assertArrayEquals(boxed(edge), TerrainMath.descendingOrder(edge))
    }
}
