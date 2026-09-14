package com.ginsengo.steward.field

import kotlin.math.abs
import kotlin.math.floor

/**
 * Your position, in the forms someone else can act on.
 *
 * Nothing else in this app addresses the case that actually kills people who dig ginseng: alone,
 * on a steep slope, with a mattock, out of signal, hurt. An independent audit named it the
 * largest omission in the whole project and it was right.
 *
 * Three specific things this exists to get right, none of which are obvious:
 *
 *  1. **Decimal degrees is not the only format that matters.** US dispatch and most county SAR
 *     teams work in degrees and decimal minutes. Reading "35.35" to someone expecting
 *     "N 35 21.000" wastes minutes at the worst possible time, so both are shown, always,
 *     labelled, and in a font you can read aloud.
 *
 *  2. **A stale fix displayed as current is worse than no fix.** Under Appalachian canopy the
 *     GPS drops out for minutes at a time. A position frozen from where you were twenty minutes
 *     ago, shown without comment, sends help to the wrong ridge. Fix age is always visible and
 *     an old fix says so loudly.
 *
 *  3. **SMS gets out where data does not.** A text needs one brief moment of marginal signal
 *     and will keep retrying; an HTTP request will not. The share action composes an SMS.
 *
 * All of this is pure and unit-tested, because the one time it is needed is the one time nobody
 * is going to be checking whether it works.
 */
object EmergencyPosition {

    /** A fix older than this is not to be relied on without saying so. */
    const val STALE_AFTER_MS = 2 * 60 * 1000L

    /** Beyond this, the fix is too loose to send anyone to. */
    const val POOR_ACCURACY_M = 50f

    /**
     * Decimal degrees to six places. Six is not arbitrary: the sixth decimal is about 0.11 m at
     * this latitude, so it is past the point where any consumer GPS is meaningful, and five would
     * quantise to roughly a metre.
     */
    fun decimalDegrees(lat: Double, lng: Double): String =
        "%.6f, %.6f".format(lat, lng)

    /**
     * Degrees and decimal minutes — the format US dispatch and county SAR generally expect.
     *
     * Longitude is padded to three digits because "W 82" and "W 082" are read differently over a
     * bad radio link, and the hemisphere letters are explicit so a dropped minus sign cannot put
     * you in the eastern hemisphere.
     */
    fun degreesDecimalMinutes(lat: Double, lng: Double): String {
        fun part(value: Double, positive: String, negative: String, degWidth: Int): String {
            val hemisphere = if (value >= 0) positive else negative
            val a = abs(value)
            var deg = floor(a).toInt()
            var minutes = (a - deg) * 60.0
            // Guard the rounding boundary: 59.9999' must become the next degree, not 60.000'.
            if (minutes >= 59.9995) {
                minutes = 0.0
                deg += 1
            }
            return "%s %0${degWidth}d° %06.3f'".format(hemisphere, deg, minutes)
        }
        return part(lat, "N", "S", 2) + "  " + part(lng, "E", "W", 3)
    }

    fun isStale(fixTimeMs: Long, nowMs: Long): Boolean = nowMs - fixTimeMs > STALE_AFTER_MS

    fun isPoor(accuracyM: Float): Boolean = accuracyM > POOR_ACCURACY_M

    /** Human fix age. Deliberately blunt: "4 min old" reads faster than a timestamp. */
    fun fixAge(fixTimeMs: Long, nowMs: Long): String {
        val seconds = ((nowMs - fixTimeMs) / 1000L).coerceAtLeast(0)
        return when {
            seconds < 10 -> "just now"
            seconds < 60 -> "$seconds sec old"
            seconds < 3600 -> "${seconds / 60} min old"
            else -> "${seconds / 3600} hr old"
        }
    }

    /**
     * The message to send. Written to be read aloud by someone who is not calm.
     *
     * Position first, because it is the only part that cannot be guessed, and both formats in
     * full. Accuracy and age are included rather than hidden: a responder who knows the fix is
     * 40 m and four minutes old can plan for it, and one who is told nothing cannot.
     */
    fun emergencyMessage(
        lat: Double,
        lng: Double,
        accuracyM: Float,
        altitudeM: Double?,
        fixTimeMs: Long,
        nowMs: Long,
        note: String? = null,
    ): String = buildString {
        append("HELP. I need assistance and may not be able to call.\n\n")
        append("MY POSITION\n")
        append(decimalDegrees(lat, lng)).append('\n')
        append(degreesDecimalMinutes(lat, lng)).append('\n')
        append("Accuracy: +/- ${accuracyM.toInt()} m")
        if (isPoor(accuracyM)) append("  (POOR)")
        append('\n')
        altitudeM?.let { append("Elevation: ${it.toInt()} m\n") }
        append("Fix taken: ${fixAge(fixTimeMs, nowMs)}")
        if (isStale(fixTimeMs, nowMs)) {
            append("  -- WARNING, this position is OLD and I may have moved since")
        }
        append('\n')
        if (!note.isNullOrBlank()) append("\n").append(note.trim()).append('\n')
        append("\nMap link: https://www.google.com/maps?q=$lat,$lng\n")
        append("Sent by GENSINGO.")
    }

    /**
     * Magnetic declination across the ginseng states runs roughly 4-12 degrees WEST, so a
     * compass needle points that much west of true north and a bearing taken off it must be
     * corrected before it is walked.
     *
     * This is a stated caution rather than a computed value on purpose: a real declination model
     * (IGRF/WMM) is a large coefficient table that expires, and shipping a stale one that looks
     * authoritative is worse than telling someone the correction exists. The device compass is
     * also routinely thrown 30 degrees or more by a mattock head, a truck, or a magnetic case.
     */
    const val DECLINATION_CAUTION =
        "Compass bearings here need a magnetic declination correction of roughly 4-12 degrees " +
        "west, depending on where you are. A steel tool, a vehicle or a magnetic phone case will " +
        "swing the needle much further than that. Trust terrain and your map over the needle."
}
