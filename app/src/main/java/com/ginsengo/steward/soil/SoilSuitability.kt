package com.ginsengo.steward.soil

/**
 * Ginseng site quality read from the USDA soil survey, for the North Carolina mountains.
 *
 * This is the factor every other part of this app admits it cannot see. The terrain model infers
 * landform from curvature over a ~10 m elevation grid; the soil survey records landform because a
 * surveyor walked the ground and wrote it down. Where the two disagree, the surveyor wins.
 *
 * Everything in this file is grounded in values MEASURED from SSURGO across 19 western North
 * Carolina survey areas, not in what the vocabulary was assumed to contain. The landform field
 * (`cogeomordesc.geomfname` where `geomftname = 'Landform'`) uses a closed vocabulary, and the
 * counts over those counties are:
 *
 *   mountain slopes 1296 · ridges 1280 · **coves 412** · drainageways 376 · fans 312
 *   flood plains 175 · hillslopes 145 · stream terraces 131 · depressions 55 · bogs 1
 *
 * The important part is that "cove" alone is not the answer. Drainage class varies WITHIN coves,
 * and the difference is the difference between ginseng ground and rotted roots:
 *
 *   well drained coves ....... Saunook, Cullasaja, Santeetlah, Greenlee, Lonon, Balsam, Brevard,
 *                              Chiltoskie, Heintooga, Keener, Lostcove, Maymead, Northcove, Spivey
 *   poorly drained coves ..... Alarka, Sylva
 *   excessively drained ...... Rubble land
 *
 * That is the same non-monotonic shape the terrain model encodes as a band, arrived at from an
 * independent direction: a cove is the right landform, a waterlogged cove is bottomland, and
 * ginseng is susceptible to water-borne root pathogens where water accumulates.
 *
 * NOT VALIDATED against ginseng occurrence. No occurrence dataset at usable precision exists -
 * iNaturalist and GBIF obscure this species' coordinates by design, to deter poaching. So this
 * is a better-grounded heuristic than the terrain surface, not a prediction. It is better
 * grounded because its inputs were observed by a person on that specific ground rather than
 * inferred from a raster.
 */
object SoilSuitability {

    /** Landform classes, in the survey's own vocabulary. */
    enum class Landform(val surveyNames: Set<String>, val score: Double, val note: String) {
        COVE(
            setOf("coves"), 1.00,
            "Mapped as a COVE by the soil survey - the landform ginseng grows in, recorded by " +
                "someone who walked it rather than inferred from a contour.",
        ),
        COLLUVIAL(
            setOf("fans", "benches", "toes"), 0.85,
            "Colluvial bench, fan or toe slope: deep soil that accumulated from upslope. The " +
                "ground ginseng shares with ramps and goldenseal.",
        ),
        DRAINAGEWAY(
            setOf("drainageways", "valleys", "mountain valleys"), 0.55,
            "Drainageway. Often the right moisture and the right shade, but check drainage - the " +
                "channel itself is too wet.",
        ),
        SLOPE(
            setOf(
                "mountain slopes", "mountainsides", "hillslopes", "hillsides", "hills",
                "low hills", "escarpments",
            ), 0.45,
            "General mountain slope. Neither favoured nor ruled out; aspect and position decide.",
        ),
        RIDGE(
            setOf("ridges", "interfluves", "broad interstream divides", "rims"), 0.15,
            "Ridge or interfluff: thin, dry, exposed soil. Ginseng is rarely on the ridge itself.",
        ),
        BOTTOMLAND(
            setOf(
                "flood plains", "stream terraces", "terraces", "natural levees",
                "river valleys", "backswamps", "flats",
            ),
            0.20,
            "Floodplain or terrace. Too wet, too disturbed, and it floods.",
        ),
        WET(
            setOf(
                "depressions", "bogs", "pocosins", "marshes", "sloughs", "slackwater areas",
                "troughs", "dune slacks", "lakebeds", "carolina bays",
            ), 0.05,
            "Depression or bog. Standing water rots ginseng roots.",
        ),
        UNKNOWN(emptySet(), 0.40, "Landform not recorded for this map unit."),
        ;

        companion object {
            /**
             * Terms that mean this landform is COASTAL, whatever else the name says.
             *
             * This guard is not hypothetical. The statewide vocabulary contains "mainland coves"
             * and "barrier coves" - both coastal - alongside the mountain "coves". A matcher
             * that merely looked for the word "cove" would score a barrier island as prime
             * ginseng ground. Ginseng is a mountain plant; anything wearing one of these words
             * is not its habitat regardless of the noun.
             */
            private val COASTAL = listOf(
                "barrier", "mainland", "tidal", "marine", "estuarine", "dune", "pocosin",
                "carolina bay", "lakeshore", "beach", "shoal", "submerged", "berm", "bays",
            )

            /**
             * The survey vocabulary is NOT closed - 50 distinct values statewide, and
             * "mountainsides" turned up in the field from an area outside the first sample.
             * So: exact match on what was measured, then a guarded keyword fallback for terms
             * that have not been seen, then UNKNOWN. The fallback never promotes anything to
             * COVE, because a cove is the one class worth being conservative about.
             */
            fun of(surveyName: String?): Landform {
                val n = surveyName?.trim()?.lowercase() ?: return UNKNOWN
                entries.firstOrNull { it != UNKNOWN && n in it.surveyNames }?.let { return it }
                if (COASTAL.any { it in n }) return BOTTOMLAND
                return when {
                    "bog" in n || "marsh" in n || "swamp" in n || "depression" in n -> WET
                    "flood" in n || "terrace" in n || "levee" in n -> BOTTOMLAND
                    "ridge" in n || "divide" in n || "interfluve" in n -> RIDGE
                    "slope" in n || "side" in n || "hill" in n || "mountain" in n -> SLOPE
                    "fan" in n || "bench" in n || "toe" in n -> COLLUVIAL
                    "drainage" in n || "valley" in n -> DRAINAGEWAY
                    else -> UNKNOWN
                }
            }
        }
    }

    /**
     * Drainage class, in the survey's own vocabulary.
     *
     * "Well drained" is the target. Both ends are penalised and they are penalised for different
     * reasons, which is why this is not a ramp: wet ground rots the root, and excessively drained
     * ground dries out in August when the plant is still carrying seed.
     */
    fun drainageScore(drainageClass: String?): Double = when (drainageClass?.trim()?.lowercase()) {
        "well drained" -> 1.00
        "moderately well drained" -> 0.75
        "somewhat excessively drained" -> 0.45
        "excessively drained" -> 0.20
        "somewhat poorly drained" -> 0.20
        "poorly drained" -> 0.05
        "very poorly drained" -> 0.00
        null, "" -> 0.40
        else -> 0.40
    }

    /**
     * Surface-horizon pH.
     *
     * The band is deliberately NOT the 5.5-6.0 that cultivation guides quote. Those are planting
     * targets for woods-grown beds. Measured on real forested mountain ground in this survey -
     * Pisgah, Nantahala, Roan - surface pH runs 4.6 to 5.3, and those are sites in the middle of
     * the species' range. Handing a digger 5.5-6.0 as a requirement would tell him that genuinely
     * good NC ground is wrong. Scored broad and flat, penalising only the real extremes.
     */
    fun phScore(ph: Double?): Double {
        if (ph == null) return 0.5
        return when {
            ph < 4.0 -> 0.15
            ph < 4.5 -> 0.55
            ph <= 6.5 -> 1.00
            ph <= 7.3 -> 0.80
            else -> 0.45
        }
    }

    /**
     * Organic matter in the surface horizon, percent.
     *
     * Ginseng wants deep leaf-litter duff. Measured values on NC mountain forest run about 4% on
     * typical slopes and up to 15% on high cool sites. Below ~2% is usually disturbed, eroded or
     * pasture ground.
     */
    fun organicMatterScore(omPercent: Double?): Double {
        if (omPercent == null) return 0.5
        return when {
            omPercent < 1.0 -> 0.15
            omPercent < 2.0 -> 0.45
            omPercent < 4.0 -> 0.80
            else -> 1.00
        }
    }

    data class SoilReading(
        val seriesName: String?,
        val landformSurveyName: String?,
        val drainageClass: String?,
        val surfacePh: Double?,
        val organicMatterPercent: Double?,
        /** Component percentage of the map unit, used as confidence rather than as score. */
        val componentPercent: Int? = null,
    )

    data class SoilVerdict(
        val score: Double,
        val landform: Landform,
        val reasons: List<String>,
        /** True when the survey gave enough to say anything at all. */
        val hasEvidence: Boolean,
    )

    /**
     * Weights. Landform and drainage carry the most because they are what the survey OBSERVED;
     * pH and organic matter are laboratory-estimated representative values for the map unit, not
     * measurements of the spot underfoot, so they inform rather than decide.
     */
    const val W_LANDFORM = 0.45
    const val W_DRAINAGE = 0.30
    const val W_PH = 0.10
    const val W_ORGANIC = 0.15

    fun evaluate(r: SoilReading): SoilVerdict {
        val landform = Landform.of(r.landformSurveyName)
        val drainage = drainageScore(r.drainageClass)
        val ph = phScore(r.surfacePh)
        val om = organicMatterScore(r.organicMatterPercent)

        val score = W_LANDFORM * landform.score +
                W_DRAINAGE * drainage +
                W_PH * ph +
                W_ORGANIC * om

        val reasons = mutableListOf<String>()
        r.seriesName?.let { reasons += "Soil series: $it." }
        reasons += landform.note
        r.drainageClass?.let { reasons += "Drainage class: $it." }

        // The specific combination worth calling out, because it is the trap the terrain model
        // also has to avoid: right landform, wrong water.
        if (landform == Landform.COVE && drainage <= 0.2) {
            reasons += "This is a cove, but a POORLY DRAINED one. Standing water rots ginseng " +
                "roots - the landform is right and the ground is not."
        }
        r.surfacePh?.let { reasons += "Surface pH about $it." }
        r.organicMatterPercent?.let { reasons += "Surface organic matter about $it%." }

        val hasEvidence = r.landformSurveyName != null || r.drainageClass != null
        if (!hasEvidence) {
            reasons += "The soil survey has no usable record for this spot - often urban land, " +
                "water, or an unmapped area. Read the ground with your own eyes."
        }

        return SoilVerdict(
            score = score.coerceIn(0.0, 1.0),
            landform = landform,
            reasons = reasons,
            hasEvidence = hasEvidence,
        )
    }
}
