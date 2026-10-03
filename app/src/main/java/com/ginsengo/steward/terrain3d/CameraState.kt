package com.ginsengo.steward.terrain3d

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Where the map is looking: the same numbers in both projections. MapLibre's zoom (512-px world
 * tiles), bearing (degrees clockwise from north) and pitch (degrees from vertical), which
 * [MapCamera] mirrors exactly.
 */
data class CameraState(
    val lat: Double,
    val lng: Double,
    val zoom: Double,
    val bearing: Double,
    val pitch: Double,
)

/**
 * The one camera (exe.md A1). Neither view stores a camera of its own: the 2D map and the 3D
 * view are mirrors of this state.
 *
 * THE EPOCH RULE, which is what stops two mirrors fighting:
 *  - [report]: the view under the user's finger says where it now looks. The state follows; the
 *    epoch does not change, so no view is told to move.
 *  - [move]: the app moves the camera (the first fix, recentre, "Show on map", the 2D/3D switch,
 *    the 3D fit or compass reset). The epoch changes, and every view applies it exactly once
 *    ([Follower]).
 *
 * The rejected alternatives: re-applying every report makes a view chase its own gesture; letting
 * MapLibre's camera be the truth loses the truth whenever the 2D map is off screen.
 */
class SharedCamera(initial: CameraState) {

    /** The camera; how many app moves have happened; whether the latest one asked to animate. */
    data class Snapshot(val camera: CameraState, val epoch: Int, val animate: Boolean)

    private val _state = MutableStateFlow(Snapshot(initial, 0, false))
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    /** The camera now, for callers outside Compose. */
    val camera: CameraState get() = _state.value.camera

    /** From the view the user is moving. No other view is told to follow. */
    fun report(c: CameraState) = _state.update { it.copy(camera = c) }

    /** From the app. Every view follows once. */
    fun move(c: CameraState, animate: Boolean = false) =
        _state.update { Snapshot(c, it.epoch + 1, animate) }

    /**
     * A view's side of the rule: [take] hands back a snapshot to apply when, and only when, an
     * app move has happened since this view last applied one. A view created later starts at the
     * epoch it was created in: it is placed at the current camera, not replayed through history.
     */
    class Follower(startEpoch: Int) {
        private var applied = startEpoch

        fun take(s: Snapshot): Snapshot? = if (s.epoch != applied) s.also { applied = s.epoch } else null
    }
}
