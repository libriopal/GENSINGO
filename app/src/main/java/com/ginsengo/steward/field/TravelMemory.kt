package com.ginsengo.steward.field

import com.ginsengo.steward.prospect.Prospects
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The travel memory's grid (owner directive J31): 1/10,000 of a degree, about 11 m north–south and
 * 9 m east–west in the ginseng states, one row per cell the owner has been in.
 *
 * A degree grid, not a Mercator one, because the upgrade folds every stored track point in with
 * one SQL statement ([SQL_KEY]), and SQLite computes exactly this key: `CAST(x AS INTEGER)`
 * truncates toward zero, which is `floor` for the non-negative `lat + 90` and `lng + 180`.
 */
object TravelCells {
    const val PER_DEGREE = 10_000.0
    /** Room for every longitude index (0 .. 3,600,000) under one latitude index. */
    const val LNG_SPAN = 4_000_000L

    fun latIndex(lat: Double): Long = floor((lat + 90.0) * PER_DEGREE).toLong()
    fun lngIndex(lng: Double): Long = floor((lng + 180.0) * PER_DEGREE).toLong()
    fun key(lat: Double, lng: Double): Long = latIndex(lat) * LNG_SPAN + lngIndex(lng)

    fun south(key: Long): Double = (key / LNG_SPAN) / PER_DEGREE - 90.0
    fun north(key: Long): Double = (key / LNG_SPAN + 1) / PER_DEGREE - 90.0
    fun west(key: Long): Double = (key % LNG_SPAN) / PER_DEGREE - 180.0
    fun east(key: Long): Double = (key % LNG_SPAN + 1) / PER_DEGREE - 180.0

    /** [key] in SQLite, over a table with `lat` and `lng` columns (the 5→6 migration). */
    const val SQL_KEY = "CAST((`lat` + 90) * 10000 AS INTEGER) * 4000000 + CAST((`lng` + 180) * 10000 AS INTEGER)"

    /**
     * The key range holding every cell between [south] and [north]: all longitudes of those rows,
     * so a query over it is filtered by longitude after ([inBox]). Only the owner's own cells are
     * in the table, so a row band is small.
     */
    fun band(south: Double, north: Double): LongRange =
        latIndex(south) * LNG_SPAN..(latIndex(north) * LNG_SPAN + LNG_SPAN - 1)

    fun inBox(key: Long, north: Double, west: Double, south: Double, east: Double): Boolean =
        north(key) > south && south(key) < north && east(key) > west && west(key) < east
}

/**
 * Which cells a fix marks. Pure: the rules the recorder applies, unit-tested.
 *
 * A fix vaguer than [MAX_ACCURACY_M] marks nothing (under canopy a 60 m fix would paint ground the
 * owner never stood on). Otherwise it marks its own cell and, when the previous accepted fix is
 * close in space and time, every cell on the line between them: fixes 30 s apart (the slowest
 * walking plan) are ~40 m apart, and the memory must read as a path, not as dots.
 */
object TravelMemory {
    const val MAX_ACCURACY_M = 30f
    const val MAX_GAP_M = 150.0
    const val MAX_GAP_MS = 5 * 60_000L
    /** Under a third of the smaller cell side, so no cell the line crosses is stepped over. */
    const val STEP_M = 3.0

    data class Fix(val lat: Double, val lng: Double, val accuracyM: Float, val time: Long)

    fun cellsFor(prev: Fix?, fix: Fix): LongArray {
        if (fix.accuracyM > MAX_ACCURACY_M) return LongArray(0)
        val own = TravelCells.key(fix.lat, fix.lng)
        if (prev == null) return longArrayOf(own)
        val d = Prospects.distanceMetres(prev.lat, prev.lng, fix.lat, fix.lng)
        val dt = fix.time - prev.time
        if (d > MAX_GAP_M || dt > MAX_GAP_MS || dt < 0) return longArrayOf(own)
        val n = ceil(d / STEP_M).toInt().coerceAtLeast(1)
        val out = LinkedHashSet<Long>()
        for (k in 0..n) {
            val t = k.toDouble() / n
            out += TravelCells.key(prev.lat + (fix.lat - prev.lat) * t, prev.lng + (fix.lng - prev.lng) * t)
        }
        return out.toLongArray()
    }
}

/** What the map reads of the travel memory (J31): the table over a square, and this process's additions. */
interface TravelSource {
    /** Every remembered cell overlapping the box. */
    suspend fun cellsIn(north: Double, west: Double, south: Double, east: Double): LongArray
    /** Changes whenever this process adds cells ([TravelRecorder.version]). */
    val version: StateFlow<Int>
    /** The cells this process added since [index]. */
    fun recentSince(index: Int): LongArray
}

/**
 * The app's one travel recorder: every fix the app receives goes through it, from the map on
 * screen and from the track service in the pocket (the same process, one shared "previous fix").
 *
 * Battery: cells are written in batches ([FLUSH_CELLS] or [FLUSH_MS], whichever first, and when
 * the screen goes), not on every fix. What this process has added is also kept in memory
 * ([recentSince]), so the map draws a new cell at once instead of re-reading the table.
 */
class TravelRecorder(
    private val store: suspend (cells: LongArray, time: Long) -> Unit,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var prev: TravelMemory.Fix? = null
    private val pending = LinkedHashSet<Long>()
    private val seen = HashSet<Long>()
    private val recent = ArrayList<Long>()
    private var lastFlush = clock()

    private val _version = MutableStateFlow(0)
    /** How many cells this process has added; it changes when the map has something new to draw. */
    val version: StateFlow<Int> = _version.asStateFlow()

    fun record(fix: TravelMemory.Fix) {
        val flushNow: Boolean
        synchronized(this) {
            val cells = TravelMemory.cellsFor(prev, fix)
            if (fix.accuracyM <= TravelMemory.MAX_ACCURACY_M) prev = fix
            for (c in cells) {
                pending += c
                if (seen.add(c)) recent += c
            }
            if (recent.size != _version.value) _version.value = recent.size
            flushNow = pending.size >= FLUSH_CELLS || clock() - lastFlush >= FLUSH_MS
        }
        if (flushNow) flush()
    }

    /** The cells added since [index] (a previous [version] value). */
    @Synchronized
    fun recentSince(index: Int): LongArray =
        if (index >= recent.size) LongArray(0) else recent.subList(index.coerceAtLeast(0), recent.size).toLongArray()

    /** Writes what is pending. Called on the schedule above and when the app leaves the screen. */
    fun flush() {
        val batch: LongArray
        val t: Long
        synchronized(this) {
            lastFlush = clock()
            if (pending.isEmpty()) return
            batch = pending.toLongArray()
            pending.clear()
            t = lastFlush
        }
        scope.launch { store(batch, t) }
    }

    companion object {
        const val FLUSH_CELLS = 64
        const val FLUSH_MS = 30_000L
    }
}
