package com.ginsengo.steward.terrain3d

import com.ginsengo.steward.field.TravelCells
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.tan

/** The travel memory's GPU mask lines up with the ground under it (J31) and carries the J20 buffer. */
class TravelMaskTest {

    private val lat0 = 35.55003
    private val lng0 = -82.95007

    private fun mask(level: Int = 15): Pair<TravelMask, DoubleArray> {
        val b = Terrain3D.areaBounds(lat0, lng0, level)
        return TravelMask(b[0], b[1], b[2], b[3], SquareLevel.widthM(level, lat0)) to b
    }

    // Web Mercator written out here, independently of Projection.
    private fun mx(lng: Double) = (lng + 180.0) / 360.0
    private fun my(lat: Double) = (1.0 - ln(tan(PI / 4 + Math.toRadians(lat) / 2)) / PI) / 2.0

    private fun marked(m: TravelMask): List<Pair<Int, Int>> =
        m.visited.indices.filter { m.visited[it] != 0.toByte() }.map { (it / m.size) to (it % m.size) }

    @Test
    fun aCellLandsOnTheTexelsOverItsGround() {
        val (m, b) = mask()
        val cell = TravelCells.key(lat0, lng0)
        assertTrue(m.mark(cell))
        val texels = marked(m)
        assertTrue("${texels.size} texels for an 11 × 9 m cell on ~5.8 m texels", texels.size in 2..9)
        val cLat = (TravelCells.south(cell) + TravelCells.north(cell)) / 2
        val cLng = (TravelCells.west(cell) + TravelCells.east(cell)) / 2
        val row = ((my(cLat) - my(b[0])) / (my(b[2]) - my(b[0])) * m.size).toInt()
        val col = ((mx(cLng) - mx(b[1])) / (mx(b[3]) - mx(b[1])) * m.size).toInt()
        assertTrue("centre ($row, $col) not in $texels", (row to col) in texels)
    }

    @Test
    fun northIsRowZero() {
        val (m, b) = mask()
        m.mark(TravelCells.key(b[0] - 0.0002, lng0))
        m.mark(TravelCells.key(b[2] + 0.0002, lng0))
        val rows = marked(m).map { it.first }
        assertTrue(rows.toString(), rows.minOrNull()!! < 10 && rows.maxOrNull()!! > m.size - 10)
        val m2 = mask().first
        m2.mark(TravelCells.key(lat0, b[1] + 0.0002))
        assertTrue(marked(m2).all { it.second < 10 })
    }

    @Test
    fun groundOffTheSquareChangesNothing() {
        val (m, b) = mask()
        assertFalse(m.mark(TravelCells.key(b[0] + 0.01, lng0)))
        assertFalse(m.mark(TravelCells.key(lat0, b[3] + 0.01)))
        assertTrue(marked(m).isEmpty())
        // And a cell already drawn is no change either: no needless upload.
        val cell = TravelCells.key(lat0, lng0)
        assertTrue(m.mark(cell))
        assertFalse(m.mark(cell))
        assertFalse(m.markAll(longArrayOf(cell)))
    }

    @Test
    fun onTheCoarsestSquareACellIsStillOneTexel() {
        val (m, _) = mask(11)
        assertTrue(m.mark(TravelCells.key(lat0, lng0)))
        assertEquals(1, marked(m).size)
        assertEquals("one ~94 m texel is wider than the 15 m buffer", 0, m.bufferTexels)
        assertArrayEquals(m.visited, m.dilated())
    }

    @Test
    fun theBufferIsFifteenMetres() {
        val (m, _) = mask()
        val texelM = SquareLevel.widthM(15, lat0) / m.size
        assertEquals(15.0 / texelM, m.bufferTexels.toDouble(), 0.5)
        assertEquals(3, m.bufferTexels)
    }

    @Test
    fun dilationIsADisk() {
        val m = TravelMask(36.0, -83.0, 35.9, -82.9, 10_000.0, size = 32)
        m.visited[16 * 32 + 16] = 0xFF.toByte()
        val d = m.dilated(3)
        // Points with dx² + dy² ≤ 9: 29 of them.
        assertEquals(29, d.count { it != 0.toByte() })
        assertTrue(d[16 * 32 + 19] != 0.toByte())
        assertTrue(d[19 * 32 + 16] != 0.toByte())
        assertTrue(d[17 * 32 + 19] == 0.toByte())
        // At the edge the disk is clipped, not wrapped to the other side.
        val e = TravelMask(36.0, -83.0, 35.9, -82.9, 10_000.0, size = 32)
        e.visited[0] = 0xFF.toByte()
        val de = e.dilated(2)
        assertTrue(de[31] == 0.toByte() && de[31 * 32] == 0.toByte())
        assertEquals(6, de.count { it != 0.toByte() })
    }

    @Test
    fun theGpuTexelsCarryVisitedAndBuffer() {
        val m = TravelMask(36.0, -83.0, 35.9, -82.9, 10_000.0, size = 8)
        m.visited[9] = 0xFF.toByte()
        val buf = m.dilated(1)
        val rg = m.rg(buf)
        assertEquals(128, rg.size)
        for (i in 0 until 64) {
            assertEquals(m.visited[i], rg[2 * i])
            assertEquals(buf[i], rg[2 * i + 1])
        }
        assertArrayEquals(m.visited, ByteArray(64) { m.rg(null)[2 * it + 1] })
    }

    @Test
    fun unwalkedIsStrongGroundOutsideTheBuffer() {
        val strong = booleanArrayOf(true, true, false, false)
        val buffer = byteArrayOf(0, 0xFF.toByte(), 0, 0xFF.toByte())
        assertArrayEquals(booleanArrayOf(true, false, false, false), TravelMask.unwalked(strong, buffer))
    }

    /** J20's witness: a strong patch with a walk down its west side keeps colour only away from the walk. */
    @Test
    fun aWalkAcrossAStrongPatchGreysOnlyTheGroundWithinFifteenMetres() {
        val (m, b) = mask()
        val n = m.size
        val strong = BooleanArray(n * n) { (it / n) in 200 until 300 && (it % n) in 200 until 300 }
        // Columns are linear in longitude; rows are Mercator (written out here).
        fun lngOfCol(c: Double) = b[1] + (c + 0.5) / n * (b[3] - b[1])
        fun latOfRow(r: Double): Double {
            val y = my(b[0]) + (r + 0.5) / n * (my(b[2]) - my(b[0]))
            return Math.toDegrees(kotlin.math.atan(kotlin.math.sinh(PI * (1 - 2 * y))))
        }
        val walkLng = lngOfCol(220.0)
        var lat = latOfRow(180.0)
        while (lat > latOfRow(320.0)) { m.mark(TravelCells.key(lat, walkLng)); lat -= 0.5e-4 }
        val kept = TravelMask.unwalked(strong, m.dilated())
        val mPerDegLng = 111_320.0 * cos(Math.toRadians(lat0))
        var greyed = 0; var colour = 0
        for (r in 200 until 300) for (c in 200 until 300) {
            val d = kotlin.math.abs(lngOfCol(c.toDouble()) - walkLng) * mPerDegLng
            // The walked cell is up to 9 m wide around the line and a texel ~5.8 m: margins for both.
            if (d < 15.0) { assertFalse("($r, $c) $d m from the walk kept colour", kept[r * n + c]); greyed++ }
            if (d > 15.0 + 9.0 + 6.0) { assertTrue("($r, $c) $d m from the walk greyed", kept[r * n + c]); colour++ }
        }
        assertTrue("$greyed greyed, $colour kept", greyed > 400 && colour > 7_000)
        // Weak ground is never kept, walked or not.
        assertTrue(kept.indices.none { kept[it] && !strong[it] })
    }

    @Test
    fun theSquaresWidthMatchesItsBounds() {
        // The buffer's texel count rests on widthM; check it against the bounds the mask covers.
        val b = Terrain3D.areaBounds(lat0, lng0, 15)
        val widthAtCentre = (b[3] - b[1]) * 111_320.0 * cos(Math.toRadians(lat0))
        assertEquals(widthAtCentre, SquareLevel.widthM(15, lat0), widthAtCentre * 0.01)
    }
}
