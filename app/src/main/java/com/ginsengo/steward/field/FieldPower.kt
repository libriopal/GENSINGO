package com.ginsengo.steward.field

import com.ginsengo.steward.data.db.TrackPoint
import com.ginsengo.steward.prospect.Prospects

/**
 * The way back (J21): from where you stand to where today's track began, which is where the car
 * is in practice. A chip shows it while Track records, so in a hollow with no signal the way out is
 * on screen. Pure; the great-circle functions are [Prospects]'.
 */
object WayBack {
    data class Leg(val distanceM: Double, val bearingDeg: Double)

    /** The leg to the first point of [sessionId] in [track] (its earliest), or null without one. */
    fun toStart(track: List<TrackPoint>, sessionId: String?, lat: Double, lng: Double): Leg? {
        if (sessionId == null) return null
        val start = track.filter { it.sessionId == sessionId }.minByOrNull { it.time } ?: return null
        return Leg(
            Prospects.distanceMetres(lat, lng, start.lat, start.lng),
            Prospects.bearingTrue(lat, lng, start.lat, start.lng),
        )
    }

    /** "1.2 km NE" / "340 m S": the chip's words. */
    fun describe(leg: Leg): String {
        val d = if (leg.distanceM >= 1000) "%.1f km".format(leg.distanceM / 1000) else "%d m".format(leg.distanceM.toInt())
        val points = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        return "$d ${points[(((leg.bearingDeg % 360 + 360) % 360 + 22.5) / 45).toInt() % 8]}"
    }
}

/**
 * The battery field mode (J24): below the owner's threshold, unplugged, or with the phone's own
 * battery saver on, the 3D map runs lighter. Pure: what "lighter" means is here, tested.
 *
 * No saving is claimed in numbers: what each lever saves needs a real phone to measure (exe.md
 * C18). The levers are the ones the 3D view controls: mesh density, texture size, the map
 * snapshot, and the frame rate.
 */
object BatteryMode {
    const val DEFAULT_THRESHOLD_PCT = 25
    val THRESHOLD_RANGE = 10..50

    /** Vertices per edge of the 3D mesh when on (half the normal cap of 385). */
    const val GRID_CAP = 193
    /** Colour texture edge when on (a quarter of the normal 2048 × 2048's memory). */
    const val TEXTURE_CAP = 1024
    /** Minimum time between frames: 30 frames a second normally, 20 when on. */
    const val FRAME_MS = 33L
    const val FRAME_MS_SAVER = 50L

    fun on(batteryPct: Int?, charging: Boolean, systemSaver: Boolean, thresholdPct: Int): Boolean =
        systemSaver || (!charging && batteryPct != null && batteryPct <= thresholdPct)
}

/**
 * Frames on demand, paced (J32): during a gesture every touch event asks for a frame, up to the
 * panel's touch rate; one frame per [minIntervalMs] is drawn and the last request is never lost
 * (a trailing frame is scheduled), so the view always comes to rest on the final camera.
 */
class FramePacer(var minIntervalMs: Long) {
    private var lastFrameAt = Long.MIN_VALUE / 2
    private var trailing = false

    /** 0: draw now. Above 0: schedule one trailing frame after that many ms. −1: one is already scheduled. */
    fun request(nowMs: Long): Long {
        val wait = lastFrameAt + minIntervalMs - nowMs
        if (wait <= 0 && !trailing) { lastFrameAt = nowMs; return 0 }
        if (trailing) return -1
        trailing = true
        return wait
    }

    /** The scheduled trailing frame is being drawn now. */
    fun trailingDrawn(nowMs: Long) { trailing = false; lastFrameAt = nowMs }
}
