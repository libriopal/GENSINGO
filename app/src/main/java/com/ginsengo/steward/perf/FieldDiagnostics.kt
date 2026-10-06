package com.ginsengo.steward.perf

import java.util.concurrent.atomic.AtomicInteger

/**
 * What the map is doing, in words a field tester can read out or copy (F.1: the owner reported
 * "terrain and layers don't work" from a phone, with no way to say why). Filled by the tile store,
 * the 3D renderer and the 3D view; shown in the Layers sheet with a Copy button.
 *
 * Privacy: counts, timings, the GPU's name and error texts only. No coordinate, tile id, URL or
 * search text is ever put here (a tile id locates the phone to ~1 km, I19).
 */
object FieldDiagnostics {
    val tilesFromCache = AtomicInteger()
    val tilesDownloaded = AtomicInteger()
    val tilesFailed = AtomicInteger()
    @Volatile var lastTileFailure: String = ""

    @Volatile var gpu: String = "not started"
    @Volatile var glError: String? = null
    @Volatile var framesDrawn: Int = 0

    @Volatile var square: String = "none yet"
    @Volatile var mapDrape: String = "—"
    @Volatile var roads: String = "—"
    @Volatile var gps: String = "—"
    @Volatile var mapSelfTest: String = "not run (Layers → Test map rendering)"

    fun report(): String = buildString {
        appendLine("GPS: $gps")
        appendLine("Elevation tiles: ${tilesFromCache.get()} from storage, ${tilesDownloaded.get()} downloaded, ${tilesFailed.get()} failed" +
            (if (lastTileFailure.isNotEmpty()) " (last: $lastTileFailure)" else ""))
        appendLine("3D square: $square")
        appendLine("GPU: $gpu · frames drawn: $framesDrawn")
        glError?.let { appendLine("3D drawing error: $it") }
        appendLine("Map on the ground: $mapDrape")
        appendLine("Roads & trails: $roads")
        append("Map renderer self-test: $mapSelfTest")
    }
}
