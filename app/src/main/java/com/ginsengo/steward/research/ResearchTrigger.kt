package com.ginsengo.steward.research

import com.ginsengo.steward.prospect.Prospects

/**
 * When a new fix should refresh the suggestions on its own. Pure, so it is tested directly.
 *
 * Found on the Phase 7 device run: the first run of a session used the platform's stale last
 * known position (the emulator's, ~3,500 km away), and afterwards nothing refreshed the list
 * unless the research model's auto-refresh conditions (consent, key, network) all held. So a
 * user walking offline kept the first area's suggestions indefinitely, when the goal is
 * suggestions for the CURRENT 10-mile radius.
 *
 *  - Only a FRESH fix ([FRESH_MS]) may start an automatic run; a stale one may still move
 *    the dot on the map.
 *  - The on-device ranking refreshes after moving [RESCAN_M] from the last run's centre, or
 *    after [STALE_MS]. That costs a terrain scan, a few seconds, once per 3 km.
 *  - Whether that automatic run may also call the research model is a separate, stricter
 *    decision ([ResearchRepository.shouldAutoRun]): 5 km, 30 minutes, battery, consent, key.
 */
object ResearchTrigger {

    const val FRESH_MS = 2 * 60_000L
    const val RESCAN_M = ResearchRepository.RESCAN_M
    const val STALE_MS = 12 * 3_600_000L

    fun shouldRefresh(
        lastCenterLat: Double?, lastCenterLng: Double?, lastTime: Long?,
        fixLat: Double, fixLng: Double, fixTime: Long, now: Long,
    ): Boolean {
        if (now - fixTime > FRESH_MS) return false
        if (lastCenterLat == null || lastCenterLng == null || lastTime == null) return true
        if (now - lastTime > STALE_MS) return true
        return Prospects.distanceMetres(lastCenterLat, lastCenterLng, fixLat, fixLng) > RESCAN_M
    }
}
