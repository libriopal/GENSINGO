package com.ginsengo.steward.terrain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * exe.md I4: the app's own Terrarium decoder. The vectors below are encoded by hand from the
 * published format (Tilezen joerd, "terrarium": metres = R·256 + G + B/256 − 32768) for real,
 * published elevations, so they are pinned in time and independent of the code under test.
 * `RealTerrainTest` cannot catch a decoder defect: its fixtures were decoded by a Python script.
 * Negative control: mutant Q2 (the −32768 offset dropped) must fail here.
 */
class TerrariumDecodeTest {

    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test
    fun seaLevelIsZero() = assertEquals(0f, DemTileStore.terrariumMetres(argb(128, 0, 0)), 0f)

    @Test
    fun mountMitchellTheHighestPeakEastOfTheMississippi() {
        // 2,037 m: 32768 + 2037 = 34805 = 135·256 + 245.
        assertEquals(2037f, DemTileStore.terrariumMetres(argb(135, 245, 0)), 0f)
    }

    @Test
    fun belowSeaLevelBadwaterBasin() {
        // −86 m: 32768 − 86 = 32682 = 127·256 + 170.
        assertEquals(-86f, DemTileStore.terrariumMetres(argb(127, 170, 0)), 0f)
    }

    @Test
    fun theBlueChannelCarriesFractionsOfAMetre() {
        // 1,000.5 m: 33768.5 = 131·256 + 232 + 128/256.
        assertEquals(1000.5f, DemTileStore.terrariumMetres(argb(131, 232, 128)), 0f)
    }

    @Test
    fun alphaIsIgnored() {
        assertEquals(2037f, DemTileStore.terrariumMetres((0x00 shl 24) or (135 shl 16) or (245 shl 8)), 0f)
    }
}
