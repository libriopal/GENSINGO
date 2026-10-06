package com.ginsengo.steward.terrain3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * exe.md A1/A3: one camera, two mirrors. The property that matters is the epoch rule: a view
 * must follow every app move exactly once and must never be told to follow its own gesture
 * (that is a feedback loop: the camera chases itself and stutters or never settles).
 *
 * Witness: two simulated mirrors driven through a realistic session. The negative control is
 * mutant Q5 (report bumps the epoch), under which a mirror re-applies its own gesture.
 */
class SharedCameraTest {

    private val start = CameraState(35.55, -82.95, 9.0, 0.0, 50.0)

    /** A view: it applies app moves through its Follower and counts what it applied. */
    private class Mirror(cam: SharedCamera) {
        val follower = SharedCamera.Follower(cam.state.value.epoch)
        val applied = mutableListOf<CameraState>()
        fun sync(cam: SharedCamera) { follower.take(cam.state.value)?.let { applied += it.camera } }
    }

    @Test
    fun aGestureReportMovesTheCameraButTellsNoViewToFollow() {
        val cam = SharedCamera(start)
        val map = Mirror(cam)
        val dragged = start.copy(lat = 35.56, lng = -82.94)
        cam.report(dragged)
        map.sync(cam)
        assertEquals("the state follows the gesture", dragged, cam.camera)
        assertTrue("the gesturing view must not be told to re-apply its own move: ${map.applied}", map.applied.isEmpty())
    }

    @Test
    fun everyAppMoveIsFollowedExactlyOnceByEveryView() {
        val cam = SharedCamera(start)
        val map = Mirror(cam); val terrain = Mirror(cam)
        val fix = start.copy(lat = 35.6, lng = -83.0, zoom = 14.0)
        cam.move(fix)
        repeat(3) { map.sync(cam); terrain.sync(cam) }     // recompositions, repeated collects
        assertEquals(listOf(fix), map.applied)
        assertEquals(listOf(fix), terrain.applied)
        // A gesture after the move is not an app move: nobody re-applies anything.
        cam.report(fix.copy(bearing = 30.0))
        map.sync(cam); terrain.sync(cam)
        assertEquals(1, map.applied.size)
        // The next app move is applied once more, with its animate flag.
        val there = fix.copy(lat = 35.61, zoom = 15.0)
        cam.move(there, animate = true)
        map.sync(cam)
        assertEquals(there, map.applied.last())
        assertTrue(cam.state.value.animate)
    }

    @Test
    fun aViewCreatedLaterStartsAtTheCurrentCameraAndReplaysNothing() {
        val cam = SharedCamera(start)
        cam.move(start.copy(zoom = 14.0))
        cam.move(start.copy(zoom = 15.0))
        val late = Mirror(cam)            // e.g. the 2D map rebuilt after leaving 3D
        late.sync(cam)
        assertTrue("history must not be replayed into a new view", late.applied.isEmpty())
        assertNull(late.follower.take(cam.state.value))
        cam.move(start.copy(zoom = 16.0))
        assertNotNull(late.follower.take(cam.state.value))
    }
}
