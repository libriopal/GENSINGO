package com.ginsengo.steward.field

/**
 * How hard to run the GPS, decided from things the phone actually measures.
 *
 * This replaces `FieldPowerManager`, which offered four named modes with printed battery
 * lifetimes ("18+ hrs") derived from an assumed 15 Wh battery and assumed wattages. Nothing
 * measured those numbers, so this policy makes no lifetime claim at all. It only decides the
 * request, and every input to the decision is a measurement: battery level, charging state,
 * whether the screen is on, recent ground speed, and how long the fix has been still.
 *
 * The three levers, cheapest first:
 *  - INTERVAL: fewer fixes. The obvious lever and the weakest on its own, because the GNSS
 *    chip is only one cost.
 *  - BATCHING (`maxDelayMs`): the fused provider holds fixes and delivers them together, so
 *    the application processor can sleep between batches. With the screen off this is the
 *    lever that matters most, and it costs nothing in track quality because a batch still
 *    carries every fix.
 *  - ACCURACY: BALANCED lets the provider skip GNSS. Only safe when the track is not being
 *    drawn from those fixes (still, or critically low battery), because under forest canopy
 *    there is no Wi-Fi and "balanced" degrades to cell towers.
 *
 * Pure: no Android types, so every branch is unit-tested and mutation-tested.
 */
object PowerPolicy {

    enum class Accuracy { HIGH, BALANCED }

    enum class Mode(val label: String) {
        VIEWING("Live"),
        TRACKING_MOVING("Tracking"),
        TRACKING_STILL("Tracking · still"),
        LOW_BATTERY("Low battery"),
        CRITICAL_BATTERY("Battery critical"),
    }

    data class Plan(
        val mode: Mode,
        val accuracy: Accuracy,
        val intervalMs: Long,
        val minIntervalMs: Long,
        /** 0 = deliver immediately. Above 0, fixes are batched up to this long. */
        val maxDelayMs: Long,
        val minDistanceM: Float,
        /** Whether a background model call may start on its own (never overrides consent). */
        val allowAutoResearch: Boolean,
    )

    const val LOW_BATTERY_PCT = 20
    const val CRITICAL_BATTERY_PCT = 10
    const val AUTO_RESEARCH_MIN_BATTERY_PCT = 30

    /** Walking pace floor. Below this for [STILL_AFTER_MS] the user is standing, not walking. */
    const val MOVING_SPEED_MPS = 0.4
    const val STILL_AFTER_MS = 90_000L

    /**
     * @param tracking       the user has switched recording on
     * @param screenOn       the app is visible
     * @param batteryPct     0..100, or null if unknown (treated as healthy, not as empty)
     * @param charging       plugged in; battery level is then irrelevant
     * @param speedMps       recent ground speed, or null when unknown
     * @param stillForMs     how long the position has stayed within its own accuracy
     */
    fun plan(
        tracking: Boolean,
        screenOn: Boolean,
        batteryPct: Int?,
        charging: Boolean,
        speedMps: Double?,
        stillForMs: Long,
    ): Plan {
        val battery = if (charging) 100 else (batteryPct ?: 100)
        val moving = (speedMps != null && speedMps >= MOVING_SPEED_MPS) || stillForMs < STILL_AFTER_MS
        val batch = !screenOn

        if (battery <= CRITICAL_BATTERY_PCT) {
            return Plan(
                mode = Mode.CRITICAL_BATTERY,
                accuracy = if (tracking && moving) Accuracy.HIGH else Accuracy.BALANCED,
                intervalMs = 120_000L,
                minIntervalMs = 60_000L,
                maxDelayMs = if (batch) 600_000L else 0L,
                minDistanceM = 10f,
                allowAutoResearch = false,
            )
        }
        if (battery <= LOW_BATTERY_PCT) {
            return Plan(
                mode = Mode.LOW_BATTERY,
                accuracy = if (tracking && moving) Accuracy.HIGH else Accuracy.BALANCED,
                intervalMs = if (moving) 30_000L else 120_000L,
                minIntervalMs = 15_000L,
                maxDelayMs = if (batch) 300_000L else 0L,
                minDistanceM = 5f,
                allowAutoResearch = false,
            )
        }
        if (!tracking) {
            // Only reached with the screen on: with it off and tracking off, nothing runs.
            return Plan(
                mode = Mode.VIEWING,
                accuracy = Accuracy.HIGH,
                intervalMs = 3_000L,
                minIntervalMs = 1_000L,
                maxDelayMs = 0L,
                minDistanceM = 2f,
                allowAutoResearch = battery >= AUTO_RESEARCH_MIN_BATTERY_PCT,
            )
        }
        return if (moving) {
            Plan(
                mode = Mode.TRACKING_MOVING,
                accuracy = Accuracy.HIGH,
                intervalMs = 5_000L,
                minIntervalMs = 2_000L,
                maxDelayMs = if (batch) 60_000L else 0L,
                minDistanceM = 3f,
                allowAutoResearch = battery >= AUTO_RESEARCH_MIN_BATTERY_PCT,
            )
        } else {
            Plan(
                mode = Mode.TRACKING_STILL,
                accuracy = Accuracy.BALANCED,
                intervalMs = 30_000L,
                minIntervalMs = 15_000L,
                maxDelayMs = if (batch) 300_000L else 0L,
                minDistanceM = 5f,
                allowAutoResearch = battery >= AUTO_RESEARCH_MIN_BATTERY_PCT,
            )
        }
    }
}
