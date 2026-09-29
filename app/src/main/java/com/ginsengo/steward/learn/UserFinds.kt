package com.ginsengo.steward.learn

/**
 * The user's field data is ground truth.
 *
 * Every find the user saves, and every patch they logged in the older app, is treated as true
 * and accurate: drawn at full weight, learned from, and counted in what the research model is
 * told. Nothing in the app reads a quality label to demote one.
 *
 * This replaces a gate (Phase 7, first version) that kept a find off the learner when its GPS
 * fix was worse than ±20 m, the fix was over a minute old, one of three identification boxes
 * was unticked, or it came from the older app. The user directed that their field patches be
 * treated as true and accurate and nothing less; it is their data, and it now is.
 *
 * What the phone measured about a new find's position (accuracy, fix count, fix time) is still
 * stored with it and shown back to the user, as information, never as a filter.
 */
object UserFinds {
    /** What [com.ginsengo.steward.data.db.Find.verification] holds for every find: confirmed by the user. */
    const val CONFIRMED = "VERIFIED"
}
