package com.ginsengo.steward.learn

/**
 * What makes a find "verified".
 *
 * A verified find is allowed to change the model, so the word has to mean something the app
 * measured, not a box the user ticked. Two independent things must hold:
 *
 *  1. POSITION. The (averaged) fix is within [MAX_ACCURACY_M] and no older than
 *     [MAX_FIX_AGE_MS]. The threshold is 20 m, not the 5-10 m an open-sky GPS gives,
 *     because finds are made in coves under closed canopy, where phones typically report
 *     10-30 m. A 15 m rule would have rejected most real finds; see IDEA_CANDIDATES.md
 *     Step 4 for the first version of this rule and what broke it.
 *  2. IDENTIFICATION. Three field checks that separate ginseng from its common look-alikes
 *     (Virginia creeper, wild sarsaparilla, box-elder and hickory seedlings), all confirmed.
 *
 * Anything else is saved, shown on the map as unverified, and never used for learning.
 * Patches imported from the older app carry no recorded accuracy and are LEGACY: shown, not
 * learned from.
 */
object FindVerifier {

    const val MAX_ACCURACY_M = 20f
    const val MAX_FIX_AGE_MS = 60_000L

    enum class Check(val bit: Int, val label: String) {
        WHORL(1, "Leaves (prongs) rise from one point at the top of a single stem"),
        FIVE_TOOTHED(2, "Each prong has 3-5 toothed leaflets, the lowest two clearly smaller"),
        NOT_VINE_OR_WOODY(4, "Not a vine and not woody (rules out Virginia creeper and tree seedlings)"),
        ;

        companion object {
            const val ALL = 1 or 2 or 4
            fun fromMask(mask: Int): Set<Check> = entries.filter { mask and it.bit != 0 }.toSet()
            fun mask(checks: Set<Check>): Int = checks.fold(0) { m, c -> m or c.bit }
        }
    }

    enum class Level(val label: String) {
        VERIFIED("Verified"),
        UNVERIFIED("Unverified"),
        LEGACY("Imported patch"),
    }

    data class Result(val level: Level, val reasons: List<String>)

    fun verify(accuracyM: Float?, fixAgeMs: Long?, checksMask: Int): Result {
        val reasons = buildList {
            when {
                accuracyM == null -> add("No GPS accuracy recorded")
                !(accuracyM > 0f) -> add("GPS accuracy unknown")
                accuracyM > MAX_ACCURACY_M ->
                    add("GPS ±%.0f m is worse than ±%.0f m".format(accuracyM, MAX_ACCURACY_M))
            }
            when {
                fixAgeMs == null -> add("No fix time recorded")
                fixAgeMs < 0 || fixAgeMs > MAX_FIX_AGE_MS -> add("GPS fix is stale")
            }
            val missing = Check.entries.filter { checksMask and it.bit == 0 }
            if (missing.isNotEmpty()) add("${missing.size} identification check(s) not confirmed")
        }
        return Result(if (reasons.isEmpty()) Level.VERIFIED else Level.UNVERIFIED, reasons)
    }
}
