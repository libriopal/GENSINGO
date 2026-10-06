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

    /** Whether the square was built in the battery mode (J24): built otherwise, it is rebuilt. */
    @Volatile var lighter: Boolean = false
        private set

    /** The dark map style rendered onto the square, while the budget leaves it. */
    @Volatile var drape: IntArray? = null

    /** True once the camera was fitted to the square in this visit: a rotation must not refit it. */
    @Volatile var fitted: Boolean = false

    /**
     * The view built [s]: it replaces the previous square, which goes back to the budget. The full
     * scene replacing its own relief-first scene (the same mosaic, J30) keeps the drape.
     */
    @Synchronized
    fun adopt(s: Terrain3D.Scene, weights: Int, lighter: Boolean = false) {
        if (scene === s) return
        if (scene?.mosaic !== s.mosaic) dropDrape()
        scene?.release()
        scene = s
        weightsKey = weights
        this.lighter = lighter
        budget?.let { s.useBudget(it) }
    }

    /** A drape for the current square, under the budget; evicting it only loses the drape. */
    @Synchronized
    fun keepDrape(px: IntArray, onEvicted: () -> Unit) {
        val s = scene ?: return
        dropDrape()
        drape = px
        val square = s.mosaic
        budget?.put(BUDGET_DRAPE, { key -> if (key === square) { synchronized(this) { if (scene?.mosaic === square) drape = null }; onEvicted() } }, square, px.size * 4L)
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
        if (s != null) budget?.remove(BUDGET_DRAPE, s.mosaic)
        drape = null
    }

    companion object {
        const val BUDGET_DRAPE = "drape"
    }
}
