package com.ginsengo.steward.field

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos

/** The travel memory's grid and the rules for which cells a fix marks (J31). */
class TravelMemoryTest {

    private val lat0 = 35.55003
    private val lng0 = -82.95007
    private val mPerDegLat = 111_320.0
    private val mPerDegLng = 111_320.0 * cos(Math.toRadians(lat0))

    private fun fix(northM: Double, eastM: Double, acc: Float = 5f, t: Long = 0L) =
        TravelMemory.Fix(lat0 + northM / mPerDegLat, lng0 + eastM / mPerDegLng, acc, t)

    @Test
    fun aKeysCellHoldsItsPointOnBothSidesOfTheMeridianAndEquator() {
        for ((lat, lng) in listOf(lat0 to lng0, 35.5 to -82.5, -12.34567 to 45.67891, 0.00001 to -0.00001, 44.99999 to -71.00001)) {
            val k = TravelCells.key(lat, lng)
            assertTrue("$lat in ${TravelCells.south(k)}..${TravelCells.north(k)}", TravelCells.south(k) <= lat + 1e-9 && lat < TravelCells.north(k))
            assertTrue("$lng in ${TravelCells.west(k)}..${TravelCells.east(k)}", TravelCells.west(k) <= lng + 1e-9 && lng < TravelCells.east(k))
            assertEquals(1e-4, TravelCells.north(k) - TravelCells.south(k), 1e-9)
            assertEquals(1e-4, TravelCells.east(k) - TravelCells.west(k), 1e-9)
        }
    }

    @Test
    fun neighbouringCellsHaveNeighbouringKeys() {
        val k = TravelCells.key(lat0, lng0)
        assertEquals(k + 1, TravelCells.key(lat0, lng0 + 1e-4))
        assertEquals(k + TravelCells.LNG_SPAN, TravelCells.key(lat0 + 1e-4, lng0))
        // A cell is about 11 m north–south and 9 m east–west here.
        assertEquals(11.1, 1e-4 * mPerDegLat, 0.1)
        assertEquals(9.06, 1e-4 * mPerDegLng, 0.1)
    }

    @Test
    fun aBandHoldsEveryLongitudeOfItsRowsAndInBoxNarrowsIt() {
        val k = TravelCells.key(lat0, lng0)
        val band = TravelCells.band(lat0 - 0.01, lat0 + 0.01)
        assertTrue(k in band)
        assertTrue(TravelCells.key(lat0, 179.9999) in band)
        assertTrue(TravelCells.key(lat0, -180.0) in band)
        assertTrue(TravelCells.key(lat0 + 0.02, lng0) !in band)
        assertTrue(TravelCells.key(lat0 - 0.02, lng0) !in band)
        assertTrue(TravelCells.inBox(k, lat0 + 0.01, lng0 - 0.01, lat0 - 0.01, lng0 + 0.01))
        assertTrue(!TravelCells.inBox(TravelCells.key(lat0, lng0 + 0.05), lat0 + 0.01, lng0 - 0.01, lat0 - 0.01, lng0 + 0.01))
    }

    @Test
    fun aVagueFixMarksNothingAndTheLimitItselfIsAccepted() {
        assertEquals(0, TravelMemory.cellsFor(null, fix(0.0, 0.0, acc = 30.01f)).size)
        assertArrayEquals(longArrayOf(TravelCells.key(lat0, lng0)), TravelMemory.cellsFor(null, fix(0.0, 0.0, acc = 30f)))
    }

    @Test
    fun aWalkReadsAsAnUnbrokenPath() {
        // 40 m north-east in 30 s: the slowest walking plan's spacing.
        val a = fix(0.0, 0.0, t = 0)
        val b = fix(28.0, 28.0, t = 30_000)
        val cells = TravelMemory.cellsFor(a, b)
        assertEquals(TravelCells.key(a.lat, a.lng), cells.first())
        assertEquals(TravelCells.key(b.lat, b.lng), cells.last())
        // At least one cell per row or column crossed (a diagonal step crosses both at once).
        val rows = abs(cells.last() / TravelCells.LNG_SPAN - cells.first() / TravelCells.LNG_SPAN)
        val cols = abs(cells.last() % TravelCells.LNG_SPAN - cells.first() % TravelCells.LNG_SPAN)
        assertEquals(2L, rows)
        assertEquals(3L, cols)
        assertTrue("${cells.size} cells for 40 m", cells.size >= 4)
        for (i in 1 until cells.size) {
            val dLat = abs(cells[i] / TravelCells.LNG_SPAN - cells[i - 1] / TravelCells.LNG_SPAN)
            val dLng = abs(cells[i] % TravelCells.LNG_SPAN - cells[i - 1] % TravelCells.LNG_SPAN)
            assertTrue("cell $i skips ($dLat, $dLng)", dLat <= 1 && dLng <= 1 && dLat + dLng > 0)
        }
    }

    @Test
    fun aJumpOrALongPauseIsNotJoinedUp() {
        val a = fix(0.0, 0.0, t = 0)
        // Over 150 m apart: a jump (or the car), not a walk.
        assertEquals(1, TravelMemory.cellsFor(a, fix(151.0, 0.0, t = 60_000)).size)
        assertTrue(TravelMemory.cellsFor(a, fix(149.0, 0.0, t = 60_000)).size > 1)
        // Over five minutes apart: the app was closed in between.
        assertEquals(1, TravelMemory.cellsFor(a, fix(40.0, 0.0, t = TravelMemory.MAX_GAP_MS + 1)).size)
        assertTrue(TravelMemory.cellsFor(a, fix(40.0, 0.0, t = TravelMemory.MAX_GAP_MS)).size > 1)
        // Out of order: never a line backwards in time.
        assertEquals(1, TravelMemory.cellsFor(a, fix(40.0, 0.0, t = -1)).size)
    }

    private class Sink {
        val batches = ArrayList<LongArray>()
        val store: suspend (LongArray, Long) -> Unit = { cells, _ -> batches += cells }
    }

    @Test
    fun theRecorderWritesInBatchesNotPerFix() {
        var now = 0L
        val sink = Sink()
        val r = TravelRecorder(sink.store, CoroutineScope(Dispatchers.Unconfined)) { now }
        // Each fix 200 m from the last marks exactly one new cell; all well inside the time window.
        for (i in 0 until TravelRecorder.FLUSH_CELLS - 1) {
            now += 100
            r.record(fix(200.0 * i, 0.0, t = now))
        }
        assertEquals("nothing written before a batch fills", 0, sink.batches.size)
        assertEquals(TravelRecorder.FLUSH_CELLS - 1, r.version.value)
        now += 100
        r.record(fix(200.0 * TravelRecorder.FLUSH_CELLS, 0.0, t = now))
        assertEquals(1, sink.batches.size)
        assertEquals(TravelRecorder.FLUSH_CELLS, sink.batches.single().size)
        assertEquals(4, r.recentSince(TravelRecorder.FLUSH_CELLS - 4).size)
        assertEquals(0, r.recentSince(TravelRecorder.FLUSH_CELLS).size)
    }

    @Test
    fun theRecorderAlsoWritesOnTimeAndWhenAsked() {
        var now = 0L
        val sink = Sink()
        val r = TravelRecorder(sink.store, CoroutineScope(Dispatchers.Unconfined)) { now }
        r.record(fix(0.0, 0.0, t = now))
        assertEquals(0, sink.batches.size)
        now += TravelRecorder.FLUSH_MS
        r.record(fix(500.0, 0.0, t = now))
        assertEquals(1, sink.batches.size)
        assertEquals(2, sink.batches.single().size)
        r.flush()
        assertEquals("an empty flush writes nothing", 1, sink.batches.size)
        r.record(fix(1000.0, 0.0, t = now + 1))
        r.flush()
        assertEquals(2, sink.batches.size)
    }

    @Test
    fun aVagueFixNeitherMarksNorBreaksThePath() {
        val sink = Sink()
        val r = TravelRecorder(sink.store, CoroutineScope(Dispatchers.Unconfined)) { 0L }
        r.record(fix(0.0, 0.0, t = 0))
        r.record(fix(400.0, 400.0, acc = 80f, t = 10_000))
        assertEquals(1, r.version.value)
        // The next good fix joins to the last good one, not to the vague one.
        r.record(fix(0.0, 40.0, t = 30_000))
        val added = r.recentSince(0)
        assertTrue("${added.size}", added.size >= 4)
        assertTrue(added.all { it / TravelCells.LNG_SPAN == TravelCells.key(lat0, lng0) / TravelCells.LNG_SPAN })
    }

    @Test
    fun revisitingACellIsNotNewWork() {
        val sink = Sink()
        val r = TravelRecorder(sink.store, CoroutineScope(Dispatchers.Unconfined)) { 0L }
        r.record(fix(0.0, 0.0, t = 0))
        r.record(fix(0.5, 0.5, t = 5_000))
        r.record(fix(0.0, 0.0, t = 10_000))
        assertEquals(1, r.version.value)
    }
}
