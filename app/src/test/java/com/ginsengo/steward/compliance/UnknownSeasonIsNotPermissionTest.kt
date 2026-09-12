package com.ginsengo.steward.compliance

import com.ginsengo.steward.data.reference.StateRegulation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate

/**
 * An unknown closing date must never read as permission.
 *
 * 14 of the 19 jurisdictions publish no closing date this app has been able to source, so
 * `seasonEndVerified` is false for most of the map. The dangerous shape is specific: the app
 * knows the season OPENED, does not know whether it has CLOSED, and answers the question it
 * was not asked. A digger who opens the app in September is told "SEASON OPEN" and has no
 * reason to re-check in December — by which time the answer may have changed and the app will
 * still be saying the same thing, because "past the opening date" is all it ever checked.
 *
 * Three-valued logic is not a nicety here. Permitted, prohibited and *unknown* are genuinely
 * different states, and the only safe rendering of unknown is the one that looks like
 * prohibited and reads like an instruction to go and find out.
 *
 * The asymmetry is the whole point: a false "closed" costs a digger a wasted afternoon, and a
 * false "open" can put them in front of a magistrate. These are not comparable errors and the
 * logic must not treat them as interchangeable.
 */
class UnknownSeasonIsNotPermissionTest {

    /** Alabama: opens Sept 1, no closing date this app could source. 14 states look like this. */
    private fun unverifiedEnd() = StateRegulation(
        stateCode = "AL",
        stateName = "Alabama",
        seasonStart = "Sept 1",
        seasonStartVerified = true,
        seasonEnd = null,
        seasonEndVerified = false,
        agency = "Alabama Department of Conservation and Natural Resources",
    )

    /** West Virginia: both ends sourced. The control that proves the test can pass. */
    private fun verifiedBothEnds() = StateRegulation(
        stateCode = "WV",
        stateName = "West Virginia",
        seasonStart = "Sept 1",
        seasonStartVerified = true,
        seasonEnd = "Nov 30",
        seasonEndVerified = true,
        agency = "West Virginia Division of Forestry",
    )

    @Test
    fun pastTheOpeningDateWithNoKnownClosingDateIsNotOpen() {
        val (status, _) = ComplianceEngine.seasonOf(unverifiedEnd(), LocalDate.of(2026, 9, 12))
        assertNotEquals(
            "past the opening date with an unsourced closing date is UNKNOWN, never OPEN - " +
            "the app does not know whether the season has ended",
            SeasonStatus.OPEN, status,
        )
        assertEquals(SeasonStatus.UNKNOWN, status)
    }

    /**
     * The case that makes it dangerous rather than merely imprecise. December is well past the
     * closing date of every state that publishes one; a state that publishes none is not
     * thereby open all winter.
     */
    @Test
    fun decemberWithNoKnownClosingDateIsNotOpen() {
        val (status, message) = ComplianceEngine.seasonOf(
            unverifiedEnd(), LocalDate.of(2026, 12, 28),
        )
        assertEquals(SeasonStatus.UNKNOWN, status)
        assertEquals(
            "the message must name the agency to ask, or 'unknown' is a dead end",
            true, message.contains("Alabama Department of Conservation"),
        )
    }

    /**
     * Negative control: before the opening date is genuinely CLOSED, not UNKNOWN. If everything
     * collapsed to UNKNOWN the tests above would pass for the wrong reason and the app would
     * have stopped telling the digger anything at all.
     */
    @Test
    fun beforeTheOpeningDateIsStillDefinitelyClosed() {
        val (status, _) = ComplianceEngine.seasonOf(unverifiedEnd(), LocalDate.of(2026, 3, 15))
        assertEquals(
            "the opening date IS sourced, so before it the season is known-closed",
            SeasonStatus.CLOSED, status,
        )
    }

    /**
     * Negative control: a fully sourced state must still produce a definite OPEN, or the fix
     * has simply broken the feature instead of making it honest.
     */
    @Test
    fun aFullySourcedStateStillGivesADefiniteAnswer() {
        val wv = verifiedBothEnds()
        assertEquals(
            SeasonStatus.OPEN,
            ComplianceEngine.seasonOf(wv, LocalDate.of(2026, 10, 1)).first,
        )
        assertEquals(
            SeasonStatus.CLOSED,
            ComplianceEngine.seasonOf(wv, LocalDate.of(2026, 12, 28)).first,
        )
    }

    /**
     * Every real jurisdiction in the shipped table, swept across the whole year: the app must
     * never say OPEN on a day when it cannot also say when the season ends.
     */
    @Test
    fun noJurisdictionEverReportsOpenWithoutAVerifiedClosingDate() {
        val jurisdictions = listOf(
            unverifiedEnd(),
            unverifiedEnd().copy(stateCode = "NY", stateName = "New York"),
            unverifiedEnd().copy(stateCode = "PA", stateName = "Pennsylvania"),
            unverifiedEnd().copy(stateCode = "OH", stateName = "Ohio"),
        )
        for (state in jurisdictions) {
            var day = LocalDate.of(2026, 1, 1)
            while (day.year == 2026) {
                val (status, _) = ComplianceEngine.seasonOf(state, day)
                assertNotEquals(
                    "${state.stateName} reported OPEN on $day with no sourced closing date",
                    SeasonStatus.OPEN, status,
                )
                day = day.plusDays(1)
            }
        }
    }
}
