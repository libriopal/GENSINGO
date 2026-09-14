package com.ginsengo.steward.habitat

import com.ginsengo.steward.geo.GeoMath

enum class SlopePosition(val label: String, val hint: String) {
    UPPER("Upper third", "Drier, thinner soil - ginseng is less common here."),
    MIDDLE("Middle third", "Workable. Check soil depth and companions."),
    LOWER("Lower third", "Moist, deep soil collects here - the classic position."),
}

/** Trees that mark rich, moist cove hardwood - the association ginseng grows in. */
enum class TreeSpecies(val label: String, val favourable: Boolean) {
    SUGAR_MAPLE("Sugar maple", true),
    REDBUD("Redbud", true),
    PAWPAW("Pawpaw", true),
    TULIP_POPLAR("Tulip poplar", true),
    DOGWOOD("Dogwood", true),
    BASSWOOD("Basswood", true),
    BLACK_WALNUT("Black walnut", true),
    OAK("Oak", false),
    HICKORY("Hickory", false),
    CEDAR("Cedar", false),
    PINE("Pine", false),
}

enum class HabitatVerdict(val label: String, val blurb: String) {
    STRONG(
        "Strong habitat indicators",
        "This reads like ginseng ground. Walk it slowly and look low."
    ),
    MIXED(
        "Mixed signals",
        "Some indicators present, others missing. Worth a look, no promises."
    ),
    UNLIKELY(
        "Unlikely habitat",
        "The indicators point away from ginseng here. Try a cooler, moister slope."
    ),
}

/** Everything the digger told the app in Workflow A. */
data class ChecklistAnswers(
    val aspectDegrees: Double? = null,
    val slopePosition: SlopePosition? = null,
    val treesPresent: Set<TreeSpecies> = emptySet(),
    val companionsSeen: Set<String> = emptySet(),
    val totalCompanionsOffered: Int = 1,
    val soilDeepDarkLoose: Boolean? = null,
) {
    val isComplete: Boolean
        get() = aspectDegrees != null && slopePosition != null && soilDeepDarkLoose != null

    val hasOnlyDrySiteTrees: Boolean
        get() = treesPresent.isNotEmpty() && treesPresent.none { it.favourable }
}

data class ChecklistResult(
    val verdict: HabitatVerdict,
    val score01: Double,
    val reasons: List<String>,
    val warnings: List<String>,
)

/**
 * Scores the field checklist (PRD Workflow A step 4).
 *
 * This is deliberately a separate, simple, inspectable rule set - NOT the ONNX model.
 * Keeping them apart matters: the model weights slope aspect and slope angle at zero, so
 * if the checklist result were derived from the model, the two things the digger is
 * standing there looking at - which way the hill faces and where on it they are - would
 * quietly stop affecting the answer.
 */
object HabitatChecklist {

    fun evaluate(a: ChecklistAnswers): ChecklistResult {
        val reasons = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        var points = 0.0
        var possible = 0.0

        // Aspect: north/east ideal.
        a.aspectDegrees?.let { asp ->
            possible += 2.0
            val fav = GeoMath.aspectFavourability(asp)
            points += 2.0 * fav
            val name = GeoMath.compassName(asp.toFloat())
            if (fav >= 0.66) reasons.add("Slope faces $name - cool and shaded, which ginseng wants.")
            else if (fav >= 0.33) reasons.add("Slope faces $name - workable but not ideal.")
            else warnings.add("Slope faces $name. South- and west-facing ground bakes; ginseng rarely holds there.")
        }

        // Position on slope.
        a.slopePosition?.let { pos ->
            possible += 2.0
            points += when (pos) {
                SlopePosition.LOWER -> 2.0
                SlopePosition.MIDDLE -> 1.2
                SlopePosition.UPPER -> 0.3
            }
            reasons.add("${pos.label} of the slope - ${pos.hint}")
        }

        // Tree association.
        if (a.treesPresent.isNotEmpty()) {
            possible += 2.0
            val good = a.treesPresent.count { it.favourable }
            val bad = a.treesPresent.count { !it.favourable }
            points += (2.0 * good / (good + bad).coerceAtLeast(1)).coerceIn(0.0, 2.0)
            if (good > 0) {
                reasons.add(
                    a.treesPresent.filter { it.favourable }.joinToString(", ") { it.label } +
                            " overhead - the rich-woods association."
                )
            }
            if (a.hasOnlyDrySiteTrees) {
                warnings.add(
                    "Only oak, hickory, cedar or pine here. That is dry-site timber - " +
                            "ginseng is unlikely under it."
                )
            }
        }

        // Companion plants.
        if (a.totalCompanionsOffered > 0) {
            possible += 3.0
            val frac = a.companionsSeen.size.toDouble() / a.totalCompanionsOffered
            points += 3.0 * frac.coerceIn(0.0, 1.0)
            if (a.companionsSeen.isNotEmpty()) {
                reasons.add("${a.companionsSeen.size} companion species confirmed on the ground.")
            } else {
                warnings.add("No companion plants confirmed. They are the most reliable sign there is.")
            }
        }

        // Soil.
        a.soilDeepDarkLoose?.let { ok ->
            possible += 2.0
            if (ok) {
                points += 2.0
                reasons.add("Deep, dark, loose soil under leaf litter.")
            } else {
                warnings.add("Soil is thin, hard or bare. Ginseng needs deep loose duff.")
            }
        }

        val score = if (possible <= 0.0) 0.0 else (points / possible).coerceIn(0.0, 1.0)
        val verdict = when {
            score >= 0.66 -> HabitatVerdict.STRONG
            score >= 0.38 -> HabitatVerdict.MIXED
            else -> HabitatVerdict.UNLIKELY
        }
        return ChecklistResult(verdict, score, reasons, warnings)
    }

    /**
     * Derives the model's seven inputs, per PRD §8.1, and records where each one came from.
     *
     * The origin tagging is the point. PRD §8.1 defines canopy cover as a slope/aspect
     * heuristic plus companion-plant density, and moisture as slope position plus the soil
     * check. Companion sightings, slope position and the soil check are all things the
     * digger just typed in. Since those two features carry the model's two largest weights,
     * most of the model's output is a re-encoding of the checklist - and the UI says so
     * rather than presenting the score as a second, independent opinion.
     */
    fun deriveFeatures(
        lat: Double,
        lng: Double,
        elevationM: Double?,
        elevationMeasured: Boolean,
        slopeDegrees: Double?,
        answers: ChecklistAnswers,
    ): List<FeatureInput> {
        val aspect = answers.aspectDegrees ?: 0.0
        val companionDensity =
            if (answers.totalCompanionsOffered <= 0) 0.0
            else (answers.companionsSeen.size.toDouble() / answers.totalCompanionsOffered)
                .coerceIn(0.0, 1.0)

        // Canopy: shaded aspect plus companion density. Companions dominate and are user-reported.
        val canopy = (0.35 * GeoMath.aspectFavourability(aspect) + 0.65 * companionDensity)
            .coerceIn(0.0, 1.0)

        // Moisture: slope position plus the soil check. Both user-reported.
        val posTerm = when (answers.slopePosition) {
            SlopePosition.LOWER -> 1.0
            SlopePosition.MIDDLE -> 0.55
            SlopePosition.UPPER -> 0.2
            null -> 0.5
        }
        val soilTerm = when (answers.soilDeepDarkLoose) {
            true -> 1.0
            false -> 0.2
            null -> 0.5
        }
        val moisture = (0.5 * posTerm + 0.5 * soilTerm).coerceIn(0.0, 1.0)

        return listOf(
            FeatureInput(HabitatFeature.LATITUDE, lat, InputOrigin.MEASURED),
            FeatureInput(HabitatFeature.LONGITUDE, lng, InputOrigin.MEASURED),
            FeatureInput(
                HabitatFeature.ALTITUDE, elevationM ?: 0.0,
                if (elevationMeasured) InputOrigin.MEASURED else InputOrigin.FROM_YOUR_CHECKLIST
            ),
            FeatureInput(HabitatFeature.CANOPY_COVER, canopy, InputOrigin.FROM_YOUR_CHECKLIST),
            FeatureInput(
                HabitatFeature.SLOPE_ANGLE, slopeDegrees ?: 0.0,
                if (slopeDegrees != null) InputOrigin.MEASURED else InputOrigin.FROM_YOUR_CHECKLIST
            ),
            FeatureInput(HabitatFeature.ASPECT, aspect, InputOrigin.MEASURED),
            FeatureInput(HabitatFeature.MOISTURE, moisture, InputOrigin.FROM_YOUR_CHECKLIST),
        )
    }
}
