package com.ginsengo.steward.terrain3d

import com.ginsengo.steward.perf.MemoryBudget

/**
 * The 3D view's built square for one visit to 3D (exe.md A16), held by the ViewModel.
 *
 * A rotation recreates the activity, and with it the 3D view: before A.3 that threw away the
 * square and rebuilt it (seconds on a phone, 12 minutes on the emulator) while the map showed. Now
 * the square, its draped map and whether the camera was already fitted live here for as long as
 * the user stays in 3D, and the new view picks them up. Leaving 3D ends the session ([end]), which
 * gives the memory back to the shared budget (A13): the square is not kept warm afterwards (that is
 * candidate J17, awaiting the owner's scope).
 */
class MeshSession(private val budget: MemoryBudget?) {

    @Volatile var scene: Terrain3D.Scene? = null
        private set

    /** The weights the square was scored with: a square scored with other weights is rebuilt. */
    @Volatile var weightsKey: Int = 0
        private set

    /** The 2D map's style rendered onto the square, while the budget leaves it. */
    @Volatile var drape: IntArray? = null

    /** True once the camera was fitted to the square in this visit: a rotation must not refit it. */
    @Volatile var fitted: Boolean = false

    /** The view built [s]: it replaces the previous square, which goes back to the budget. */
    @Synchronized
    fun adopt(s: Terrain3D.Scene, weights: Int) {
        if (scene === s) return
        dropDrape()
        scene?.release()
        scene = s
        weightsKey = weights
        budget?.let { s.useBudget(it) }
    }

    /** A drape for the current square, under the budget; evicting it only loses the drape. */
    @Synchronized
    fun keepDrape(px: IntArray, onEvicted: () -> Unit) {
        val s = scene ?: return
        dropDrape()
        drape = px
        budget?.put(BUDGET_DRAPE, { key -> if (key === s) { synchronized(this) { if (scene === s) drape = null }; onEvicted() } }, s, px.size * 4L)
    }

    /** Leaving 3D: everything back to the budget. */
    @Synchronized
    fun end() {
        dropDrape()
        scene?.release()
        scene = null
        fitted = false
    }

    private fun dropDrape() {
        val s = scene
        if (s != null) budget?.remove(BUDGET_DRAPE, s)
        drape = null
    }

    companion object {
        const val BUDGET_DRAPE = "drape"
    }
}
