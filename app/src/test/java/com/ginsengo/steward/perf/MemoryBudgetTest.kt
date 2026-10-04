package com.ginsengo.steward.perf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A13: one ceiling for both views' caches. The witness is a hand-run ledger: each case states
 * which entries must remain, computed from sizes and use order, not from the budget's own code.
 */
class MemoryBudgetTest {

    private var now = 0L
    private fun budget(ceiling: Long) = MemoryBudget(ceiling) { now++ }

    private class Owner(val held: MutableSet<Any> = mutableSetOf()) : MemoryBudget.Owner {
        override fun evict(key: Any) { held.remove(key) }
    }

    private fun MemoryBudget.add(name: String, o: Owner, key: Any, bytes: Long, pinned: Boolean = false) {
        o.held += key; put(name, o, key, bytes, pinned)
    }

    /** One ceiling across owners: filling one cache and then adding to another evicts from the first. */
    @Test
    fun oneCeilingIsSharedByEveryCache() {
        val b = budget(100)
        val dem = Owner(); val tex = Owner()
        for (i in 1..4) b.add("dem", dem, "t$i", 25)          // 100: at the ceiling
        b.add("textures", tex, "habitat", 50)                 // 150 → the two oldest tiles go
        assertEquals(setOf("t3", "t4"), dem.held)
        assertEquals(setOf("habitat"), tex.held)
        assertEquals(100L, b.totalBytes())
        assertEquals(2, b.evictions("dem"))
    }

    /** Least recently USED, not least recently added: a touched tile outlives an untouched texture. */
    @Test
    fun theLeastRecentlyUsedEntryGoesWhicheverCacheItIsIn() {
        val b = budget(100)
        val dem = Owner(); val tex = Owner()
        b.add("dem", dem, "old", 40)
        b.add("textures", tex, "idle", 40)
        b.touch("dem", "old")                                 // the tile is used again
        b.add("dem", dem, "new", 40)                          // 120 → the idle texture goes
        assertEquals(setOf("old", "new"), dem.held)
        assertTrue(tex.held.isEmpty())
    }

    /** What is on screen is pinned: counted, never evicted, even past the ceiling. */
    @Test
    fun pinnedEntriesAreNeverEvicted() {
        val b = budget(100)
        val tex = Owner(); val dem = Owner()
        b.add("textures", tex, "onScreen", 80, pinned = true)
        b.add("dem", dem, "a", 30)                            // 110 → "a" must go, the pinned texture stays
        assertEquals(setOf("onScreen"), tex.held)
        assertTrue(dem.held.isEmpty())
        b.add("scene", Owner(), "square", 60, pinned = true)  // pinned alone exceed the ceiling: kept, honestly over
        assertEquals(140L, b.totalBytes())
        assertEquals(140L, b.pinnedBytes())
        b.pin("textures", "onScreen", false)                  // off screen: now evictable, and over the ceiling
        assertFalse(tex.held.contains("onScreen"))
    }

    /** The system asks for memory back: the ceiling comes down and the budget evicts to it; restore lifts it. */
    @Test
    fun aTrimLowersTheCeilingAndEvictsToIt() {
        val b = budget(200)
        val dem = Owner()
        for (i in 1..8) b.add("dem", dem, "t$i", 25)          // 200
        b.trim(MemoryBudget.TRIM_RUNNING_CRITICAL)            // → 25 % = 50
        assertEquals(50L, b.ceilingBytes)
        assertEquals(setOf("t7", "t8"), dem.held)
        b.trim(MemoryBudget.TRIM_BACKGROUND)                  // in the background: nothing unpinned stays
        assertTrue(dem.held.isEmpty())
        b.restore()
        assertEquals(200L, b.ceilingBytes)
        val report = b.report()
        assertTrue(report, report.startsWith("memory 0.0/0.0 MB") && report.contains("evicted dem 8"))
    }

    /** An owner may call back into the budget from its eviction callback (it is called outside the lock). */
    @Test
    fun anOwnerMayCallBackWhileEvicting() {
        val b = budget(50)
        val seen = mutableListOf<Any>()
        val reentrant = MemoryBudget.Owner { key -> seen += key; b.remove("dem", "unrelated"); b.totalBytes() }
        b.put("dem", reentrant, "a", 30)
        b.put("dem", reentrant, "b", 30)
        assertEquals(listOf<Any>("a"), seen)
    }

    @Test
    fun theCeilingIsAThirdOfTheHeapClassAndNeverUnder48Mb() {
        assertEquals(48L * 1_048_576, MemoryBudget.ceilingFor(96))
        assertEquals(170L * 1_048_576, MemoryBudget.ceilingFor(512))
    }
}
