package com.ginsengo.steward.core

/**
 * PRD §8.7. Every data surface in the app carries one of these, and the tag is derived
 * from where the value actually came from - never typed in next to the value it describes.
 */
enum class Provenance(val label: String, val detail: String) {
    /** Real data from an authoritative source. */
    VERIFIED(
        "VERIFIED",
        "Real data from an authoritative source."
    ),

    /** Model output. Distinguished from a field guarantee. */
    RESEARCH_ESTIMATE(
        "RESEARCH-GRADE ESTIMATE",
        "Model output. An estimate, not a field guarantee."
    ),

    /** Synthetic, unverified, or user-reported. */
    PROTOTYPE(
        "PROTOTYPE",
        "Unverified or user-reported."
    ),

    /**
     * Geometry that over-covers the thing it represents. Distinct from PROTOTYPE because
     * the failure mode differs: an approximate boundary is wrong at the edges in a known
     * direction, and the UI should say which direction.
     */
    APPROXIMATE(
        "APPROXIMATE",
        "Boundary is approximate and over-covers. Confirm on the ground."
    ),
}
