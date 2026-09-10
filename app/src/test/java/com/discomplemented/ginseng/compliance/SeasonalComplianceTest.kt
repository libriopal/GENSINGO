package com.discomplemented.ginseng.compliance

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.MonthDay

/**
 * Unit tests for seasonal compliance.
 */
class SeasonalComplianceTest {
    private val compliance = SeasonalCompliance()

    @Test
    fun testHarvestingSeasonMessage() {
        val message = compliance.getComplianceMessage()
        assertTrue(message.isNotEmpty())
    }

    @Test
    fun testDaysUntilSeasonEnd() {
        val days = compliance.daysUntilSeasonEnd()
        assertTrue(days >= -365 && days <= 365)  // Should be within a year
    }
}
