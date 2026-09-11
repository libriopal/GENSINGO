package com.ginsengo.steward.compliance

import com.ginsengo.steward.core.Provenance
import com.ginsengo.steward.data.reference.ReferenceRepository
import com.ginsengo.steward.data.reference.StateRegulation
import com.ginsengo.steward.geo.GeoFeature
import com.ginsengo.steward.geo.GeoJson
import com.ginsengo.steward.geo.PointInPolygon
import java.time.LocalDate
import java.time.MonthDay

enum class SeasonStatus(val label: String) {
    OPEN("SEASON OPEN"),
    CLOSED("RESTRICTED"),
    UNKNOWN("SEASON UNKNOWN"),
}

data class LandStatus(
    val areaName: String,
    val rule: String,
    val message: String,
    val provenance: Provenance,
)

data class ComplianceStatus(
    val state: StateRegulation?,
    val stateDetected: Boolean,
    val season: SeasonStatus,
    val seasonDetail: String,
    val landStatuses: List<LandStatus>,
) {
    val isProhibitedLand: Boolean get() = landStatuses.any { it.rule == "PROHIBITED" }
    val needsPermit: Boolean get() = landStatuses.any { it.rule == "PERMIT REQUIRED" }
}

/**
 * Offline season + land-status checks (PRD §8.6).
 *
 * Everything here runs against bundled GeoJSON by ray casting. There is no network call and
 * no geocoding service.
 *
 * The engine WARNS and never blocks, per PRD §8.6. That is not laxity: the protected-area
 * polygons are approximate bounding boxes that over-cover, so a hard block would refuse
 * legitimate harvests on private ground near a forest edge. An approximate boundary may
 * warn where it should not; it must never forbid.
 */
class ComplianceEngine(private val reference: ReferenceRepository) {

    private val stateFeatures: List<GeoFeature> by lazy {
        runCatching { GeoJson.parseFeatureCollection(reference.read("geo/state_boundaries.geojson")) }
            .getOrElse { emptyList() }
    }

    private val protectedFeatures: List<GeoFeature> by lazy {
        runCatching { GeoJson.parseFeatureCollection(reference.read("geo/protected_areas.geojson")) }
            .getOrElse { emptyList() }
    }

    /** Offline state auto-detect. Returns null outside the 19 approved states. */
    fun detectState(lat: Double, lng: Double): StateRegulation? {
        val hit = stateFeatures.firstOrNull { PointInPolygon.inFeature(it, lat, lng) } ?: return null
        return reference.stateByCode(hit.properties.optString("code"))
    }

    fun landStatusAt(lat: Double, lng: Double): List<LandStatus> =
        protectedFeatures.filter { PointInPolygon.inFeature(it, lat, lng) }.map { f ->
            LandStatus(
                areaName = f.properties.optString("name"),
                rule = f.properties.optString("rule"),
                message = f.properties.optString("message"),
                provenance = if (f.properties.optString("provenance") == "approximate")
                    Provenance.APPROXIMATE else Provenance.VERIFIED,
            )
        }

    fun statusAt(
        lat: Double,
        lng: Double,
        today: LocalDate = LocalDate.now(),
        manualState: StateRegulation? = null,
    ): ComplianceStatus {
        val detected = detectState(lat, lng)
        val state = detected ?: manualState
        val (season, detail) = seasonFor(state, today)
        return ComplianceStatus(
            state = state,
            stateDetected = detected != null,
            season = season,
            seasonDetail = detail,
            landStatuses = landStatusAt(lat, lng),
        )
    }

    fun seasonFor(state: StateRegulation?, today: LocalDate): Pair<SeasonStatus, String> =
        seasonOf(state, today)

    companion object {
        /**
         * Pure season logic, kept off the instance so it can be unit-tested on a plain JVM
         * without an Android Context.
         */
        fun seasonOf(state: StateRegulation?, today: LocalDate): Pair<SeasonStatus, String> {
        if (state == null) {
            return SeasonStatus.UNKNOWN to
                    "No approved state detected here. Wild ginseng harvest is legal in only " +
                    "19 states and on the Menominee Reservation."
        }
        val start = parseMonthDay(state.seasonStart)
            ?: return SeasonStatus.UNKNOWN to "Season dates unavailable for ${state.stateName}."

        val today0 = MonthDay.from(today)

        // End date is frequently unpublished. Report the honest half-answer rather than
        // inventing a closing date the digger might rely on.
        val end = parseMonthDay(state.seasonEnd).takeIf { state.seasonEndVerified }
        if (end == null) {
            val open = today0 >= start
            return (if (open) SeasonStatus.OPEN else SeasonStatus.CLOSED) to
                    "${state.stateName} opens ${state.seasonStart}. Closing date is not " +
                    "published federally - confirm with ${state.agency}."
        }

        val inSeason =
            if (start <= end) today0 >= start && today0 <= end
            else today0 >= start || today0 <= end   // season wraps the new year
        return (if (inSeason) SeasonStatus.OPEN else SeasonStatus.CLOSED) to
                "${state.stateName}: ${state.seasonStart} to ${state.seasonEnd}."
        }

        private val MONTHS = mapOf(
            "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
            "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
        )

        /** Parses "Sept 1", "Nov 30", "September" (day defaults to the 1st). */
        fun parseMonthDay(text: String?): MonthDay? {
            val t = text?.trim()?.lowercase() ?: return null
            if (t.isEmpty()) return null
            val key = t.take(3)
            val month = MONTHS[key] ?: return null
            val day = Regex("\\d{1,2}").find(t)?.value?.toIntOrNull() ?: 1
            val safeDay = day.coerceIn(1, 28.takeIf { month == 2 } ?: 31)
            return runCatching { MonthDay.of(month, safeDay) }.getOrNull()
        }
    }
}
