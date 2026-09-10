package com.discomplemented.ginseng.compliance.seasonal

import java.time.LocalDate
import java.time.Month

/**
 * Enforces North Carolina specific regulatory rules for ginseng harvesting.
 */
object RegulatoryEngine {

    /**
     * Checks if the current date falls within the legal NC wild ginseng harvesting season.
     * Season: September 1st through December 31st.
     */
    fun isHarvestingSeason(date: LocalDate = LocalDate.now()): Boolean {
        val month = date.month
        val day = date.dayOfMonth

        // September is Month.SEPTEMBER
        return month == Month.SEPTEMBER ||
                month == Month.OCTOBER ||
                month == Month.NOVEMBER ||
                month == Month.DECEMBER
    }

    /**
     * Returns a message indicating the current legal status.
     */
    fun getHarvestingStatusMessage(date: LocalDate = LocalDate.now()): String {
        return if (isHarvestingSeason(date)) {
            "Legal harvesting season is currently ACTIVE."
        } else {
            "Harvesting is currently ILLEGAL. Season begins September 1st."
        }
    }
}
