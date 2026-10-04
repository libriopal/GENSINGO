package com.ginsengo.steward.geo

/**
 * Takes slippy-map tile coordinates out of log text (exe.md invariant: privacy, the coarse cell
 * only; candidate I19). A tile id z/x/y locates the phone: at zoom 15 a tile is about 1 km across,
 * and logcat is readable by anything attached to the phone. The zoom says nothing about where, so
 * it is kept; x and y are replaced.
 *
 * The shapes, as seen on the device:
 * - `DEM tile 15/8831/12914 unavailable` (the app's own elevation store),
 * - `Failed to load tile 2/1/1=>2 for source g-dem` (MapLibre's native log),
 * - `…/terrarium/14/4475/6422.png` (a URL inside an exception message),
 * - `15_8831_12914.png` (the elevation cache's file names).
 */
object LogRedaction {

    private val SLASHED = Regex("""(?<!\d)(\d{1,2})/(\d+)/(\d+)(?!\d)""")
    private val UNDERSCORED = Regex("""(?<!\d)(\d{1,2})_(\d+)_(\d+)(?!\d)""")

    fun redact(text: String?): String =
        (text ?: "")
            .replace(SLASHED) { "${it.groupValues[1]}/x/y" }
            .replace(UNDERSCORED) { "${it.groupValues[1]}_x_y" }

    /** A throwable as one redacted line: its class and message, never its stack (no URL slips out). */
    fun describe(t: Throwable?): String =
        if (t == null) "" else t.javaClass.simpleName + (t.message?.let { ": " + redact(it) } ?: "")
}
