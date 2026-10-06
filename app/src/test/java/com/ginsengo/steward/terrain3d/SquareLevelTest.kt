package com.ginsengo.steward.terrain3d

import com.ginsengo.steward.research.RadiusScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The one map's square for each camera zoom (J30). */
class SquareLevelTest {

    private val lat = 35.55
    private val px = 1080

    @Test
    fun eachLevelIsTwiceTheNextAndTheFinestIsAboutThreeKilometres() {
        assertEquals(2_985.0, SquareLevel.widthM(15, lat), 30.0)
        for (l in SquareLevel.COARSEST until SquareLevel.FINEST) {
            assertEquals(2.0, SquareLevel.widthM(l, lat) / SquareLevel.widthM(l + 1, lat), 1e-9)
        }
    }

    @Test
    fun theCoarsestSquareHoldsTheWholeTenMileRadius() {
        val half = SquareLevel.widthM(SquareLevel.COARSEST, lat) / 2
        assertTrue("half width $half m", half > RadiusScan.RADIUS_M)
        // In the north of the range too, where a tile is narrower.
        assertTrue(SquareLevel.widthM(SquareLevel.COARSEST, 47.0) / 2 > RadiusScan.RADIUS_M)
    }

    @Test
    fun everyLevelIsChosenAtTheZoomWhereItFits() {
        for (l in SquareLevel.COARSEST..SquareLevel.FINEST) {
            val fit = SquareLevel.fitZoom(l, lat, px)
            assertEquals(l, SquareLevel.forZoom(fit, lat, px, null))
            assertEquals(l, SquareLevel.forZoom(fit + 0.45, lat, px, null))
            assertEquals(l, SquareLevel.forZoom(fit - 0.45, lat, px, null))
        }
        // Beyond either end the end levels hold.
        assertEquals(SquareLevel.FINEST, SquareLevel.forZoom(SquareLevel.maxZoom(lat, px), lat, px, null))
        assertEquals(SquareLevel.COARSEST, SquareLevel.forZoom(SquareLevel.minZoom(lat, px), lat, px, null))
    }

    @Test
    fun aCameraRestingNearABoundaryDoesNotFlip() {
        val fit14 = SquareLevel.fitZoom(14, lat, px)
        var level = 14
        var switches = 0
        // Wobbling across the half-level boundary, as a pinch that stops there does.
        repeat(50) { i ->
            val z = fit14 + if (i % 2 == 0) 0.45 else 0.55
            val next = SquareLevel.forZoom(z, lat, px, level)
            if (next != level) switches++
            level = next
        }
        assertEquals(0, switches)
        assertEquals(14, SquareLevel.forZoom(fit14 + 0.74, lat, px, 14))
        assertEquals(15, SquareLevel.forZoom(fit14 + 0.76, lat, px, 14))
        assertEquals(14, SquareLevel.forZoom(fit14 - 0.74, lat, px, 14))
        assertEquals(13, SquareLevel.forZoom(fit14 - 0.76, lat, px, 14))
    }

    @Test
    fun pastTheEndsTheEndLevelHolds() {
        val fit15 = SquareLevel.fitZoom(15, lat, px)
        assertEquals(15, SquareLevel.forZoom(fit15 + 3.0, lat, px, 15))
        val fit11 = SquareLevel.fitZoom(11, lat, px)
        assertEquals(11, SquareLevel.forZoom(fit11 - 0.99, lat, px, 11))
    }

    @Test
    fun aBigJumpGoesStraightToTheRightLevel() {
        val fit12 = SquareLevel.fitZoom(12, lat, px)
        assertEquals(12, SquareLevel.forZoom(fit12, lat, px, 15))
    }

    @Test
    fun theSlopePositionRadiusFollowsTheSquare() {
        // The 3 km square keeps the radius it always had; the 48 km one uses the 2D rule's widest.
        assertEquals(Terrain3D.TPI_RADIUS_M, SquareLevel.tpiRadiusM(15, lat, px), 0.0)
        assertEquals(1_500.0, SquareLevel.tpiRadiusM(11, lat, px), 0.0)
        var prev = 0.0
        for (l in SquareLevel.FINEST downTo SquareLevel.COARSEST) {
            val r = SquareLevel.tpiRadiusM(l, lat, px)
            assertTrue("level $l: $r after $prev", r >= prev)
            prev = r
        }
    }
}
