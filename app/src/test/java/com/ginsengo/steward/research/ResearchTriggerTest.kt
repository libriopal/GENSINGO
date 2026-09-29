package com.ginsengo.steward.research

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchTriggerTest {

    private val now = 10_000_000_000L

    /** The device-run failure: a stale last-known position started the session's research. */
    @Test
    fun aStaleFixNeverStartsARun() {
        assertFalse(ResearchTrigger.shouldRefresh(null, null, null, 39.24, -123.15, now - 3 * 3_600_000L, now))
    }

    @Test
    fun theFirstFreshFixRuns() {
        assertTrue(ResearchTrigger.shouldRefresh(null, null, null, 35.56, -83.0, now - 5_000, now))
    }

    /** The other device-run failure: walking offline never refreshed the list. */
    @Test
    fun movingThreeKilometresRefreshesWithoutAnyModel() {
        assertFalse(ResearchTrigger.shouldRefresh(35.56, -83.0, now - 60_000, 35.575, -83.0, now - 1_000, now))
        assertTrue(ResearchTrigger.shouldRefresh(35.56, -83.0, now - 60_000, 35.59, -83.0, now - 1_000, now))
    }

    @Test
    fun aHalfDayOldRunRefreshesInPlace() {
        assertTrue(ResearchTrigger.shouldRefresh(35.56, -83.0, now - 13 * 3_600_000L, 35.56, -83.0, now - 1_000, now))
    }
}
