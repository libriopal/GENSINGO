package com.ginsengo.steward.terrain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Acceptance tests for WP-C (docs/blueprints/one-map.md): the Kotlin port of maplibre-contour's
 * isolines. Grids are row-major, `z[y * w + x]`; sample (x, y) sits at the point (x, y), the
 * convention of the app's other grids (TerrainTextures.bilinear).
 */
class IsolinesTest {

    private fun grid(w: Int, h: Int, f: (x: Int, y: Int) -> Double) =
        FloatArray(w * h) { i -> f(i % w, i / w).toFloat() }

    /** Bilinear sample of a row-major grid at (x, y), same convention as the port's output. */
    private fun bilinear(z: FloatArray, w: Int, h: Int, x: Double, y: Double): Double {
        val x0 = floor(x).toInt().coerceIn(0, w - 2)
        val y0 = floor(y).toInt().coerceIn(0, h - 2)
        val tx = x - x0
        val ty = y - y0
        val v00 = z[y0 * w + x0].toDouble(); val v10 = z[y0 * w + x0 + 1].toDouble()
        val v01 = z[(y0 + 1) * w + x0].toDouble(); val v11 = z[(y0 + 1) * w + x0 + 1].toDouble()
        return (v00 * (1 - tx) + v10 * tx) * (1 - ty) + (v01 * (1 - tx) + v11 * tx) * ty
    }

    /** Two consecutive vertices lie on edges of one cell, so they are at most a diagonal apart. */
    private fun assertContinuous(what: String, line: FloatArray) {
        for (i in 2 until line.size step 2) {
            val d = hypot((line[i] - line[i - 2]).toDouble(), (line[i + 1] - line[i - 1]).toDouble())
            assertTrue("$what jumps $d cells between vertices ${i / 2 - 1} and ${i / 2}", d <= sqrt(2.0) + 1e-6)
        }
    }

    /** 1. A cone z = 1000 - r: the 900 m isoline is one closed ring at r = 100 +/- 1 cell. */
    @Test
    fun coneGivesOneClosedRingAtTheRightRadius() {
        val n = 301
        val c = 150.0
        val z = grid(n, n) { x, y -> 1000.0 - hypot(x - c, y - c) }
        val lines = isolines(z, n, n, 100.0)

        val level = lines[900]
        assertNotNull("no 900 m isoline; levels were ${lines.keys}", level)
        assertEquals("the 900 m isoline must be one line", 1, level!!.size)
        val ring = level[0]
        assertTrue("a line is flat [x0, y0, x1, y1, ...]", ring.size % 2 == 0 && ring.size >= 8)

        val last = ring.size - 2
        assertEquals("ring not closed in x", ring[0], ring[last], 1e-4f)
        assertEquals("ring not closed in y", ring[1], ring[last + 1], 1e-4f)
        for (i in ring.indices step 2) {
            val r = hypot(ring[i] - c, ring[i + 1] - c)
            assertEquals("vertex ${i / 2} of the 900 m ring at r = $r", 100.0, r, 1.0)
        }
        assertContinuous("the 900 m ring", ring)

        // It goes all the way round: the enclosed area is the circle's (shoelace formula).
        var twiceArea = 0.0
        for (i in 0 until last step 2) {
            twiceArea += ring[i].toDouble() * ring[i + 3] - ring[i + 2].toDouble() * ring[i + 1]
        }
        val area = abs(twiceArea) / 2
        assertEquals("area enclosed by the 900 m ring", Math.PI * 100 * 100, area, 0.01 * Math.PI * 100 * 100)
    }

    /** 2. A plane rising east (z = x): each level L is one line at x = L +/- 0.5, full height. */
    @Test
    fun planeRisingEastGivesOneStraightLinePerLevel() {
        val w = 60
        val h = 40
        val z = grid(w, h) { x, _ -> x.toDouble() }
        val lines = isolines(z, w, h, 10.0)

        for (l in 10..50 step 10) assertTrue("level $l missing; levels were ${lines.keys}", l in lines.keys)
        for ((l, ls) in lines) {
            assertEquals("level $l must be one line", 1, ls.size)
            val line = ls[0]
            var minY = Double.MAX_VALUE
            var maxY = -Double.MAX_VALUE
            for (i in line.indices step 2) {
                assertEquals("level $l vertex ${i / 2} x", l.toDouble(), line[i].toDouble(), 0.5)
                minY = minOf(minY, line[i + 1].toDouble())
                maxY = maxOf(maxY, line[i + 1].toDouble())
            }
            assertEquals("level $l must start at the top row", 0.0, minY, 1e-6)
            assertEquals("level $l must reach the bottom row", (h - 1).toDouble(), maxY, 1e-6)
            assertContinuous("level $l", line)
        }
    }

    /**
     * 3. Every vertex of every level-L line, bilinearly sampled, equals L within 0.05 m: the
     * vertices are linearly interpolated along the cell edges, not snapped to the cells.
     */
    @Test
    fun everyVertexSitsAtItsLevel() {
        val w = 120
        val h = 90
        val hills = grid(w, h) { x, y ->
            400.0 + 60.0 * sin(x / 11.0) * cos(y / 7.0) + 2.3 * x - 1.7 * y + 0.01 * (x - 60.0) * (x - 60.0)
        }
        val n = 101
        val cone = grid(n, n) { x, y -> 1000.0 - 1.37 * hypot(x - 50.3, y - 49.6) }

        var checked = 0
        for ((name, z, size) in listOf(Triple("hills", hills, w to h), Triple("cone", cone, n to n))) {
            val (gw, gh) = size
            val lines = isolines(z, gw, gh, 5.0)
            assertTrue("$name gave too few levels: ${lines.keys}", lines.size >= 10)
            for ((l, ls) in lines) for (line in ls) for (i in line.indices step 2) {
                val x = line[i].toDouble()
                val y = line[i + 1].toDouble()
                val v = bilinear(z, gw, gh, x, y)
                assertEquals("$name level $l vertex at ($x, $y) samples $v", l.toDouble(), v, 0.05)
                checked++
            }
        }
        assertTrue("only $checked vertices checked", checked > 2000)
    }

    /** 4. A flat grid yields no lines, whether or not its height is itself a level. */
    @Test
    fun flatGridYieldsNoLines() {
        val w = 30
        val h = 20
        for (height in listOf(100.0, 123.4, 0.0, -35.0)) {
            for (interval in listOf(5.0, 10.0)) {
                val lines = isolines(grid(w, h) { _, _ -> height }, w, h, interval)
                assertTrue("flat $height m at $interval m gave ${lines.keys}", lines.isEmpty())
            }
        }
    }
}
