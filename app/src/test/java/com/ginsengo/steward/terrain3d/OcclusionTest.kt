package com.ginsengo.steward.terrain3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A18: a marker the terrain hides is hidden, and only then. The witness is the line of sight
 * worked by hand: a wall of height H at horizontal distance s from the marker, with the eye at
 * height E and horizontal distance L, blocks the view exactly when H > E·s/L. E and L come from the
 * camera's definition (eye cameraToCenterDistance away from the target, at the pitch), not from
 * MapCamera.eyeWorld.
 */
class OcclusionTest {

    private val lat0 = 35.56; private val lng0 = -83.0
    private val w = 1080; private val h = 2400

    /**
     * A marker 1 km north of the target, and a ridge [wallM] high whose face is 500 m south of it
     * (between it and the eye), 300 m deep towards the eye: real ridges are wide, and the face
     * nearest the marker is where the sight line is lowest, so it decides.
     */
    private fun hiddenBehindWall(pitch: Double, wallM: Double, wallSouthOfMarkerM: Double = 500.0): Boolean {
        val mc = MapCamera(lat0, lng0, 14.0, 0.0, pitch, w, h)
        val markerLat = lat0 + 1000.0 / 111_320.0
        val wallLat = markerLat - wallSouthOfMarkerM / 111_320.0
        val depth = 300.0 / 111_320.0
        val heightAt = { la: Double, _: Double -> if (la <= wallLat && la >= wallLat - depth) wallM else 0.0 }
        return Occlusion.hidden(mc, markerLat, lng0, 0.0, heightAt, maxGroundM = wallM)
    }

    /** E·s/L for the same geometry, from the camera's definition. */
    private fun threshold(pitch: Double, s: Double = 500.0): Double {
        val mc = MapCamera(lat0, lng0, 14.0, 0.0, pitch, w, h)
        val dM = mc.cameraToCenterDistance / mc.pixelsPerMeter            // eye to target, metres
        val p = Math.toRadians(pitch)
        val eyeHeight = dM * Math.cos(p)
        val eyeSouthOfTarget = dM * Math.sin(p)
        val markerToEye = 1000.0 + eyeSouthOfTarget                       // bearing 0: the eye is south
        return eyeHeight * s / markerToEye
    }

    @Test
    fun aWallBetweenTheEyeAndTheMarkerHidesItJustAboveTheLineOfSight() {
        for (pitch in listOf(45.0, 60.0, 75.0)) {
            val t = threshold(pitch)
            // Below the sight line nothing can hide it. Above it, the walk's samples land within a
            // few tens of metres of the face, so the hiding height is within ~15 % above E·s/L.
            assertFalse("pitch $pitch: a ridge at 85 % of the sight line (${"%.0f".format(t * 0.85)} m) must not hide",
                hiddenBehindWall(pitch, t * 0.85))
            assertTrue("pitch $pitch: a ridge at 130 % of the sight line (${"%.0f".format(t * 1.3)} m) must hide",
                hiddenBehindWall(pitch, t * 1.3))
        }
    }

    @Test
    fun lookingDownOverTheWallRevealsTheMarker() {
        val wall = threshold(70.0) * 1.5
        assertTrue("tilted towards the horizon the wall hides it", hiddenBehindWall(70.0, wall))
        assertFalse("looking straight down the eye sees over it", hiddenBehindWall(0.0, wall))
    }

    @Test
    fun aWallBehindTheMarkerHidesNothing() {
        // The wall north of the marker: the eye (south) sees the marker in front of it.
        assertFalse(hiddenBehindWall(70.0, 2_000.0, wallSouthOfMarkerM = -500.0))
    }

    /** eyeWorld is where unproject's rays come from: two points on the centre ray line up with it. */
    @Test
    fun theEyeLiesOnTheCameraRays() {
        val mc = MapCamera(lat0, lng0, 14.6, 30.0, 55.0, w, h)
        val eye = mc.eyeWorld()
        for ((sx, sy) in listOf(540.0 to 1200.0, 200.0 to 1800.0, 900.0 to 900.0)) {
            val a = mc.unproject(sx, sy, 0.0)!!; val b = mc.unproject(sx, sy, 300.0)!!
            val ax = mc.worldX(a[1]); val ay = mc.worldY(a[0]); val bx = mc.worldX(b[1]); val by = mc.worldY(b[0])
            val bz = 300.0 * mc.pixelsPerMeter
            val t = eye[2] / bz                                   // the eye's height along a→b
            assertEquals("eye x via ($sx,$sy)", eye[0], ax + (bx - ax) * t, 1e-3 * mc.cameraToCenterDistance)
            assertEquals("eye y via ($sx,$sy)", eye[1], ay + (by - ay) * t, 1e-3 * mc.cameraToCenterDistance)
        }
    }
}
