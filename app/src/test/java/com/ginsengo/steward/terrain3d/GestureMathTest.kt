package com.ginsengo.steward.terrain3d

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.atan2

/**
 * A17: the 3D view's gestures move the camera as the 2D map's do. The witness is MapLibre 13.6.1's
 * own arithmetic, transcribed from its bytecode (docs/eincol/evidence/A.3-01-gesture-javap.txt),
 * not GestureMath's: the literals below are the ones in the class files.
 */
class GestureMathTest {

    /** onShove: `tilt − 0.1f × deltaPixelsY` (float multiply, then widened). */
    @Test
    fun aTwoFingerDragTiltsAsTheMapDoes() {
        for (dy in floatArrayOf(-120f, -66.5f, -1f, 0f, 3f, 80f)) {
            val mapLibre = -(0.1f * dy).toDouble()
            assertEquals("dy=$dy", mapLibre, GestureMath.pitchDelta(dy), 1e-12)
        }
        assertEquals("dragging up 100 px tilts 10° towards the horizon", 10.0, GestureMath.pitchDelta(-100f), 1e-6)
    }

    /** onScale (not quick-zoom): `ln(scaleFactor) / ln(1.5707963267948966) × 0.6499999761581421 × zoomRate(1)`. */
    @Test
    fun aPinchZoomsAsTheMapDoes() {
        for (s in floatArrayOf(0.5f, 0.97f, 1f, 1.03f, 1.25f, 2f)) {
            val mapLibre = Math.log(s.toDouble()) / Math.log(1.5707963267948966) * 0.6499999761581421 * 1.0
            assertEquals("scale=$s", mapLibre, GestureMath.zoomDelta(s), 1e-12)
        }
        assertEquals("a degenerate pinch does nothing", 0.0, GestureMath.zoomDelta(0f), 0.0)
    }

    /**
     * onRotate adds `rotationDegreesSinceLast` to the bearing, and the gestures library computes it
     * as `toDegrees(atan2(prevDy, prevDx) − atan2(currDy, currDx))`. Compose's calculateRotation is
     * the fingers' clockwise angle on screen (y down). Two fingers turned 30° clockwise must change
     * the bearing as MapLibre would.
     */
    @Test
    fun aTwistTurnsTheMapAsTheMapDoes() {
        val prev = doubleArrayOf(200.0, 0.0)                 // finger 2 minus finger 1, screen px
        val a = Math.toRadians(30.0)
        val curr = doubleArrayOf(200.0 * Math.cos(a), 200.0 * Math.sin(a))   // turned clockwise on a y-down screen
        val mapLibre = Math.toDegrees(atan2(prev[1], prev[0]) - atan2(curr[1], curr[0]))
        val composeClockwise = Math.toDegrees(atan2(curr[1], curr[0]) - atan2(prev[1], prev[0])).toFloat()
        assertEquals(mapLibre, GestureMath.bearingDelta(composeClockwise), 1e-4)
        assertEquals("the bearing falls when the fingers turn clockwise", -30.0, GestureMath.bearingDelta(30f), 1e-9)
    }
}
