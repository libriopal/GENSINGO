package com.ginsengo.steward.verify

/**
 * Workflow B - the harvest decision gate.
 *
 * This is the one place in the app where a wrong answer is a legal offence and a dead
 * plant, so the rules are explicit, conservative, and never inferred from a model.
 *
 * Sourced from the U.S. Fish & Wildlife Service American Ginseng export program:
 *   - 18 of the 19 approved states require a plant at least 5 years old bearing 3 compound
 *     leaves ("prongs").
 *   - Illinois requires 10 years and 4 leaves.
 *   - Age can be read from stem scars on the root neck: a 5-year-old plant carries 4
 *     scars, a 10-year-old carries 9.
 */

enum class ProngCount(val prongs: Int, val label: String, val guidance: String) {
    ONE(1, "1 prong", "First-year or very young. Leave it - it has never made a seed."),
    TWO(2, "2 prongs", "Still immature in every approved state. Leave it."),
    THREE(3, "3 prongs", "Mature in 18 of the 19 approved states. Not in Illinois."),
    FOUR_PLUS(4, "4+ prongs", "Mature everywhere ginseng harvest is legal."),
}

enum class BerryState(val label: String) {
    RED_BERRIES("Red ripe berries"),
    GREEN_BERRIES("Green, unripe berries"),
    NO_BERRIES("No berries"),
}

enum class Verdict { LEGAL, TOO_YOUNG, CHECK_STATE, DO_NOT_HARVEST }

data class VerificationInput(
    val prongs: ProngCount? = null,
    val berries: BerryState? = null,
    val stemScars: Int? = null,
    val stateMinProngs: Int = 3,
    val stateMinAgeYears: Int = 5,
    val stateName: String? = null,
    val seasonOpen: Boolean? = null,
) {
    val isComplete: Boolean get() = prongs != null && berries != null
}

data class VerificationResult(
    val verdict: Verdict,
    val headline: String,
    val reasons: List<String>,
    val stewardshipReminders: List<String>,
) {
    val isHarvestable: Boolean get() = verdict == Verdict.LEGAL
}

object PlantVerification {

    /**
     * Age implied by stem scars, as a MINIMUM rather than an estimate.
     *
     * One scar is added each autumn as the stem dies back, so a 5-year-old plant carries 4 and
     * a 10-year-old carries 9 - which is the arithmetic every state agency publishes. What the
     * agencies do not spell out is that the count can only ever UNDERSTATE age: ginseng can go
     * dormant for a season or more and add no scar at all, and old scars can be lost or
     * obscured on a weathered neck.
     *
     * That error runs in the safe direction - a plant reads younger than it is, so the rule
     * bites earlier - which is exactly why it must be reported as a floor. "Roughly 5 years
     * old" invites a digger to round a 4-scar plant down to legal in a state that wants 10.
     */
    fun minimumAgeFromScars(scars: Int): Int = scars + 1

    @Deprecated("Scars give a lower bound, not an estimate.", ReplaceWith("minimumAgeFromScars(scars)"))
    fun ageFromScars(scars: Int): Int = minimumAgeFromScars(scars)

    /**
     * Can a prong count alone ever demonstrate this state's age minimum?
     *
     * Three prongs is generally not reached before year five, which is the whole basis of the
     * 3-prong rule in eighteen jurisdictions. No prong count demonstrates TEN years, so in
     * Illinois - which requires 10 years and 4 prongs - counting leaves is not sufficient
     * evidence of legal age and the root neck has to be read.
     */
    fun prongsCanDemonstrateAge(stateMinAgeYears: Int): Boolean = stateMinAgeYears <= 5

    val STEWARDSHIP = listOf(
        "Plant the red berries within a few feet of the parent, about an inch deep, before you leave.",
        "Fill the hole and put the leaf litter back the way you found it.",
        "Leave mature plants standing. A patch you strip is a patch that is gone.",
        "Do not dig what you cannot use. Small roots are worth little and cost the patch everything.",
        "Say nothing about where this patch is.",
    )

    fun evaluate(input: VerificationInput): VerificationResult? {
        val prongs = input.prongs ?: return null
        val berries = input.berries ?: return null
        val reasons = mutableListOf<String>()
        val state = input.stateName ?: "your state"

        // Berries first: a plant that has never seeded must not be dug, regardless of size.
        if (berries == BerryState.NO_BERRIES) {
            return VerificationResult(
                verdict = Verdict.DO_NOT_HARVEST,
                headline = "Do not harvest - no seeds",
                reasons = listOf(
                    "This plant has not produced seed this year.",
                    "Digging it removes it from the patch and leaves nothing behind."
                ),
                stewardshipReminders = STEWARDSHIP,
            )
        }

        if (prongs.prongs < input.stateMinProngs) {
            reasons += "$state requires at least ${input.stateMinProngs} prongs; this plant has ${prongs.prongs}."
            reasons += prongs.guidance
            return VerificationResult(
                verdict = Verdict.TOO_YOUNG,
                headline = "Too young - leave this plant",
                reasons = reasons,
                stewardshipReminders = STEWARDSHIP,
            )
        }

        reasons += "${prongs.prongs} prongs meets the ${input.stateMinProngs}-prong minimum for $state."

        if (berries == BerryState.GREEN_BERRIES) {
            reasons += "Berries are still green. Seeds are not viable yet - come back when they are red."
            return VerificationResult(
                verdict = Verdict.CHECK_STATE,
                headline = "Wait - seed not ripe",
                reasons = reasons,
                stewardshipReminders = STEWARDSHIP,
            )
        }

        // Stem scars: corroboration in most states, REQUIRED evidence where the age minimum
        // is higher than a prong count can demonstrate.
        if (input.stemScars == null && !prongsCanDemonstrateAge(input.stateMinAgeYears)) {
            return VerificationResult(
                verdict = Verdict.CHECK_STATE,
                headline = "Count the scars before you decide",
                reasons = reasons + listOf(
                    "$state requires ${input.stateMinAgeYears} years, and no number of prongs " +
                        "demonstrates that. Three prongs indicates about five years, not ten.",
                    "Read the stem scars on the root neck before treating this plant as legal.",
                ),
                stewardshipReminders = STEWARDSHIP,
            )
        }
        input.stemScars?.let { scars ->
            val age = minimumAgeFromScars(scars)
            reasons += "$scars stem scars means AT LEAST $age years old - dormant seasons add " +
                "no scar, so this is a floor, never an estimate."
            if (age < input.stateMinAgeYears) {
                return VerificationResult(
                    verdict = Verdict.TOO_YOUNG,
                    headline = "Too young - leave this plant",
                    reasons = reasons + "$state requires ${input.stateMinAgeYears} years.",
                    stewardshipReminders = STEWARDSHIP,
                )
            }
        }

        if (input.seasonOpen == false) {
            reasons += "Season is not open in $state right now."
            return VerificationResult(
                verdict = Verdict.CHECK_STATE,
                headline = "Mature, but the season is closed",
                reasons = reasons,
                stewardshipReminders = STEWARDSHIP,
            )
        }

        if (input.stemScars == null) {
            reasons += "Confirm with stem scars on the root neck if you want a second reading."
        }

        return VerificationResult(
            verdict = Verdict.LEGAL,
            headline = "Meets the legal minimum in $state",
            reasons = reasons,
            stewardshipReminders = STEWARDSHIP,
        )
    }
}
