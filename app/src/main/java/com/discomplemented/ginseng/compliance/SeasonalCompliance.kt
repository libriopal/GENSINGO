package com.discomplemented.ginseng.compliance

import java.time.LocalDate
import java.time.MonthDay

/**
 * Seasonal harvesting compliance for North Carolina ginseng.
 */
class SeasonalCompliance {

    // NC harvesting season: Sept 1 - Nov 30
    private val harvestingSeasonStart = MonthDay.of(9, 1)
    private val harvestingSeasonEnd = MonthDay.of(11, 30)

    /**
     * Check if current date is within legal harvesting season.
     */
    fun isInHarvestingSeason(): Boolean {
        val today = LocalDate.now()
        val monthDay = MonthDay.from(today)
        return monthDay >= harvestingSeasonStart && monthDay <= harvestingSeasonEnd
    }

    /**
     * Get days remaining in harvesting season.
     */
    fun daysUntilSeasonEnd(): Int {
        val today = LocalDate.now()
        val endDate = LocalDate.of(today.year, 11, 30)
        return (endDate.toEpochDay() - today.toEpochDay()).toInt()
    }

    /**
     * Get compliance status message.
     */
    fun getComplianceMessage(): String {
        return if (isInHarvestingSeason()) {
            val daysRemaining = daysUntilSeasonEnd()
            "Harvesting season active. $daysRemaining days remaining."
        } else {
            "Harvesting season closed. Scouting only."
        }
    }
}
