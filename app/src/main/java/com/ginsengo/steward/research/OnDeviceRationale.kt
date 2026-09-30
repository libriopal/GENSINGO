package com.ginsengo.steward.research

import com.ginsengo.steward.terrain.GinsengSuitability
import kotlin.math.roundToInt

/**
 * The words shown when no model is reachable (offline, no key, no consent, or a failed call).
 *
 * Written only from the candidate's own numbers, so every clause can be traced to a value
 * the device computed. It names the strongest and weakest factor, and it always names what
 * the terrain cannot see, because that is the part a digger most needs to check.
 */
object OnDeviceRationale {

    data class Text(val headline: String, val rationale: String, val lookFor: String)

    private val FACTORS = GinsengSuitability.Factor.entries

    fun describe(c: RadiusScan.Candidate): Text {
        val f = c.factors
        val strongest = FACTORS.maxBy { f[it.ordinal] * it.weight }
        val weakest = FACTORS.minBy { f[it.ordinal] }
        val aspect = ResearchPrompt.octant(c.aspectDeg)

        val headline = buildString {
            append(aspect.replaceFirstChar { it.uppercase() })
            append("-facing, ")
            append(c.slopeDeg.roundToInt()).append("° slope, ")
            append(c.elevationM.roundToInt()).append(" m")
        }
        val rationale = buildString {
            append("Terrain score %.2f. ".format(c.score))
            append("Strongest factor: ${strongest.display.lowercase()} (%.2f). ".format(f[strongest.ordinal]))
            if (f[weakest.ordinal] < 0.5) {
                append("Weakest: ${weakest.display.lowercase()} (%.2f). ".format(f[weakest.ordinal]))
            }
            append(waterSentence(c.water)).append(' ')
            append("Computed on this phone from elevation data; ")
            append("it cannot see soil calcium, canopy or recent logging.")
        }
        return Text(headline, rationale, LOOK_FOR)
    }

    /**
     * Where the nearest creek is, from the traced drainage (Hydrology). Ginseng coves sit
     * above water, not in it, so height above the creek is given alongside distance.
     */
    fun waterSentence(w: RadiusScan.Water?): String {
        if (w == null) return "No creek within %.0f km on the elevation model.".format(RadiusScan.WATER_SEARCH_M / 1000)
        val dist = (w.distanceM / 10).roundToInt() * 10
        val drop = w.dropM.roundToInt()
        val height = when {
            drop >= 3 -> "$drop m below"
            drop <= -3 -> "${-drop} m above"
            else -> "at about this height"
        }
        val name = w.kind.label.replaceFirstChar { it.uppercase() }
        return "$name $dist m ${ResearchPrompt.octant(w.bearingDeg)}, $height (traced from elevation)."
    }

    /** From GinsengSuitability.CALCIUM_NOTE: the sourced indicator species, not invented ones. */
    const val LOOK_FOR =
        "Sugar maple, tulip poplar, black walnut or basswood overhead; jack-in-the-pulpit, " +
                "maidenhair fern or blue cohosh underfoot. Those mark the calcium-rich ground the " +
                "terrain model cannot see."
}
