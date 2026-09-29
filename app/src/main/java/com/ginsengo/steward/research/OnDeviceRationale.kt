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
            append("Computed on this phone from elevation data; ")
            append("it cannot see soil calcium, canopy or recent logging.")
        }
        return Text(headline, rationale, LOOK_FOR)
    }

    /** From GinsengSuitability.CALCIUM_NOTE: the sourced indicator species, not invented ones. */
    const val LOOK_FOR =
        "Sugar maple, tulip poplar, black walnut or basswood overhead; jack-in-the-pulpit, " +
                "maidenhair fern or blue cohosh underfoot. Those mark the calcium-rich ground the " +
                "terrain model cannot see."
}
