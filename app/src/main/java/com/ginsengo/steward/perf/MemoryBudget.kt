package com.ginsengo.steward.perf

/**
 * One memory ceiling for every large cache the app holds (exe.md A13).
 *
 * Written when the 2D map stayed alive under the 3D view (A.2; the flat map went in M.1). What it
 * budgets: decoded elevation tiles (read by the 3D build and the radius scan), the 3D square, its baked textures (16 MB each at 2048²), the
 * map snapshot draped on it. Each used to keep its own rule (24 tiles; 2 textures; whatever was
 * on screen), and nothing listened to the system asking for memory back.
 *
 * Now every such allocation is an entry here, with its owner, its size and when it was last used.
 * One policy: when the total passes the ceiling, the least recently used unpinned entry goes,
 * whichever cache it belongs to. Pinned entries (what is on screen) count but are never evicted.
 * The system's trim requests lower the ceiling ([trim]); [restore] lifts it back.
 *
 * Owners are told to drop an entry through [Owner.evict], always outside this object's lock, so an
 * owner may call back in (or hold its own lock) without a deadlock.
 */
class MemoryBudget(val baseCeilingBytes: Long, private val clock: () -> Long = { System.nanoTime() }) {

    fun interface Owner {
        /** Drop [key]: the budget has already forgotten it. Called on whichever thread overflowed. */
        fun evict(key: Any)
    }

    private class Entry(val owner: String, val callback: Owner, val key: Any, val bytes: Long, var lastUse: Long, var pinned: Boolean)

    private val entries = HashMap<Pair<String, Any>, Entry>()
    private val evicted = HashMap<String, Int>()

    /** The ceiling in force now: [baseCeilingBytes], or less after a [trim]. */
    @Volatile var ceilingBytes: Long = baseCeilingBytes
        private set

    /** Records an allocation, then evicts least-recently-used unpinned entries until under the ceiling. */
    fun put(owner: String, callback: Owner, key: Any, bytes: Long, pinned: Boolean = false) {
        val victims = synchronized(this) {
            entries[owner to key] = Entry(owner, callback, key, bytes, clock(), pinned)
            overflow()
        }
        victims.forEach { it.callback.evict(it.key) }
    }

    /** Marks a use: the entry becomes the most recently used. */
    @Synchronized fun touch(owner: String, key: Any) { entries[owner to key]?.lastUse = clock() }

    /** The owner dropped [key] itself: forget it. */
    @Synchronized fun remove(owner: String, key: Any) { entries.remove(owner to key) }

    /** Forgets every entry of [owner] (a scene replaced, a cache cleared). */
    @Synchronized fun removeAll(owner: String) { entries.keys.removeAll { it.first == owner } }

    /** Pins or unpins [key]; unpinning may evict, as it can bring evictable bytes over the ceiling. */
    fun pin(owner: String, key: Any, pinned: Boolean) {
        val victims = synchronized(this) {
            entries[owner to key]?.let { it.pinned = pinned; if (pinned) it.lastUse = clock() }
            if (pinned) emptyList() else overflow()
        }
        victims.forEach { it.callback.evict(it.key) }
    }

    /**
     * The system asked for memory back: lower the ceiling to a share of the base for [level]
     * (`ComponentCallbacks2.TRIM_MEMORY_*`) and evict down to it. Levels above `UI_HIDDEN` (the
     * app in the background) drop everything that is not pinned.
     */
    fun trim(level: Int) {
        val share = when {
            level >= TRIM_BACKGROUND -> 0.0
            level >= TRIM_UI_HIDDEN -> 0.5
            level >= TRIM_RUNNING_CRITICAL -> 0.25
            level >= TRIM_RUNNING_LOW -> 0.5
            level >= TRIM_RUNNING_MODERATE -> 0.75
            else -> 1.0
        }
        val victims = synchronized(this) {
            ceilingBytes = minOf(ceilingBytes, (baseCeilingBytes * share).toLong())
            overflow()
        }
        victims.forEach { it.callback.evict(it.key) }
    }

    /** Back in the foreground: the full ceiling again. */
    @Synchronized fun restore() { ceilingBytes = baseCeilingBytes }

    @Synchronized fun totalBytes(): Long = entries.values.sumOf { it.bytes }
    @Synchronized fun pinnedBytes(): Long = entries.values.filter { it.pinned }.sumOf { it.bytes }
    @Synchronized fun bytesOf(owner: String): Long = entries.values.filter { it.owner == owner }.sumOf { it.bytes }
    @Synchronized fun evictions(owner: String): Int = evicted[owner] ?: 0

    /** One line of counters, for the log (no positions in it). */
    @Synchronized fun report(): String {
        fun mb(b: Long) = "%.1f".format(b / 1_048_576.0)
        val byOwner = entries.values.groupBy { it.owner }.toSortedMap()
            .entries.joinToString(" · ") { (o, es) -> "$o ${mb(es.sumOf { it.bytes })}" }
        val ev = evicted.toSortedMap().entries.joinToString(" ") { (o, n) -> "$o $n" }
        return "memory ${mb(totalBytes())}/${mb(ceilingBytes)} MB (pinned ${mb(pinnedBytes())})" +
            (if (byOwner.isNotEmpty()) " · $byOwner" else "") + (if (ev.isNotEmpty()) " · evicted $ev" else "")
    }

    /** Removes and returns the entries to evict, least recently used first, until under the ceiling. */
    private fun overflow(): List<Entry> {
        var total = entries.values.sumOf { it.bytes }
        if (total <= ceilingBytes) return emptyList()
        val out = ArrayList<Entry>()
        for (e in entries.values.filter { !it.pinned }.sortedBy { it.lastUse }) {
            if (total <= ceilingBytes) break
            entries.remove(e.owner to e.key)
            evicted[e.owner] = (evicted[e.owner] ?: 0) + 1
            total -= e.bytes
            out += e
        }
        return out
    }

    companion object {
        // android.content.ComponentCallbacks2 levels (plain ints here so the class stays JVM-testable).
        const val TRIM_RUNNING_MODERATE = 5
        const val TRIM_RUNNING_LOW = 10
        const val TRIM_RUNNING_CRITICAL = 15
        const val TRIM_UI_HIDDEN = 20
        const val TRIM_BACKGROUND = 40

        /**
         * A fifth of the heap the system gives this app class, and never under 24 MB. The rest is
         * for the work itself: a 3D build, the habitat raster and the 10-mile scan each need tens
         * of MB of short-lived arrays, often at once. A third (the first choice) ran the emulator's
         * 192 MB heap out of memory during a 3D build on A.3's device run, with the scan's tiles
         * filling the cache the old 24-tile cap had kept to ~6 MB.
         */
        fun ceilingFor(memoryClassMb: Int): Long = maxOf(24L, memoryClassMb / 5L) * 1_048_576L
    }
}
