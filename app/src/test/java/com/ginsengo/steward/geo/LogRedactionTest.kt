package com.ginsengo.steward.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * I19: no tile id survives into a log line. The inputs are the message shapes captured from the
 * emulator's logcat in wave A.2 (the app's elevation store, MapLibre's native log, a URL in an
 * exception, the cache's file names).
 */
class LogRedactionTest {

    private val seenOnDevice = listOf(
        "DEM tile 15/8831/12914 unavailable",
        "{TextureViewRend}[Style]: Failed to load tile 2/1/1=>2 for source g-dem: java.security.cert.CertPathValidatorException",
        "Failed to load tile 14/4475/6422=>14 for source g-dem",
        "Unable to resolve host: https://s3.amazonaws.com/elevation-tiles-prod/terrarium/14/4475/6422.png",
        "cache miss 15_8831_12914.png",
    )

    @Test
    fun noTileIdSurvivesAndTheZoomIsKept() {
        for (line in seenOnDevice) {
            val out = LogRedaction.redact(line)
            for (id in listOf("8831", "12914", "4475", "6422")) assertFalse("'$id' survived in: $out", out.contains(id))
            assertFalse("a z/x/y shape survived in: $out", Regex("""\d+/\d+/\d+""").containsMatchIn(out))
        }
        assertEquals("DEM tile 15/x/y unavailable", LogRedaction.redact(seenOnDevice[0]))
        assertEquals("cache miss 15_x_y.png", LogRedaction.redact(seenOnDevice[4]))
        assertTrue(LogRedaction.redact(seenOnDevice[1]).contains("tile 2/x/y=>2"))
    }

    /** What is not a tile id is left alone: two-number ratios, versions, times. */
    @Test
    fun ordinaryNumbersAreUntouched() {
        for (line in listOf("habitat: DEM zoom 14, 25/35 tiles", "MapLibre 13.6.1", "rasterised 768x768 in 538849 ms", "10-04 00:22:33.167"))
            assertEquals(line, LogRedaction.redact(line))
    }

    @Test
    fun aThrowableBecomesOneRedactedLine() {
        val t = java.io.FileNotFoundException("https://s3.amazonaws.com/elevation-tiles-prod/terrarium/15/8831/12914.png")
        assertEquals("FileNotFoundException: https://s3.amazonaws.com/elevation-tiles-prod/terrarium/15/x/y.png", LogRedaction.describe(t))
        assertEquals("IllegalStateException", LogRedaction.describe(IllegalStateException()))
        assertEquals("", LogRedaction.describe(null))
    }
}
