package com.ginsengo.steward.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scene description (exe.md A9) and its depth policy (A10). Since M.1 the 3D view is the only
 * map (owner directive): a layer added without a renderer does not compile (MeshLayers' `when` is
 * exhaustive); what compiles is checked here: every declared policy is honoured, and every switch in
 * the Layers sheet changes what the map draws.
 */
class SceneLayersTest {

    @Test
    fun everyLayersDepthPolicyIsHonoured() {
        for (layer in SceneLayer.entries) {
            val mesh = MeshLayers.draw(layer)
            when (layer.depth) {
                Depth.DRAPED -> assertTrue("$layer is draped: the 3D view must paint it on the ground (texture or memory mask)",
                    mesh == MeshDraw.BAKED || mesh == MeshDraw.MEMORY)
                Depth.ON_TOP -> assertEquals("$layer is on top: the 3D view must draw it over the mesh", MeshDraw.CANVAS, mesh)
                Depth.OCCLUDED -> assertEquals("$layer is occluded: the 3D view must test it against the terrain", MeshDraw.CANVAS_OCCLUDED, mesh)
            }
        }
    }

    /** A18: the markers a digger walks to declare occlusion; the lines do not. J31: the memory is on the ground. */
    @Test
    fun theMarkersAreOccludedAndTheMemoryLiesOnTheGround() {
        assertEquals(setOf(SceneLayer.FINDS, SceneLayer.SUGGESTIONS, SceneLayer.ME),
            SceneLayer.entries.filter { it.depth == Depth.OCCLUDED }.toSet())
        assertEquals(MeshDraw.MEMORY, MeshLayers.draw(SceneLayer.VISITED))
        assertEquals(MeshDraw.MEMORY, MeshLayers.draw(SceneLayer.UNWALKED))
    }

    @Test
    fun theSheetIsExactlyTheLayersWithASwitch() {
        assertEquals(SceneLayer.SHEET.size, SceneLayer.SHEET.toSet().size)
        assertEquals(SceneLayer.entries.filter { it.set != null }.toSet(), SceneLayer.SHEET.toSet())
    }

    /**
     * No dead controls: flipping any switch in the sheet changes what the map draws (the texture
     * bake, the memory mask, or the canvas). The Hillshade switch used to fail this: the 3D bake
     * shaded regardless.
     */
    @Test
    fun everySwitchChangesWhatTheMapDraws() {
        val base = MapLayerState()
        for (layer in SceneLayer.SHEET) {
            val on = layer.set!!(base, true); val off = layer.set.invoke(base, false)
            assertTrue("$layer: set/shown disagree", layer.shown(on) && !layer.shown(off))
            for (other in SceneLayer.entries - layer) assertEquals("$layer's switch moved $other", other.shown(on), other.shown(off))
            when (MeshLayers.draw(layer)) {
                MeshDraw.BAKED -> assertNotEquals("$layer's switch does not reach the 3D bake", MeshLayers.baked(on), MeshLayers.baked(off))
                MeshDraw.MEMORY -> assertTrue("$layer's switch does not reach the memory mask",
                    MeshLayers.onMemory(layer, on) && !MeshLayers.onMemory(layer, off))
                MeshDraw.CANVAS, MeshDraw.CANVAS_OCCLUDED -> assertTrue("$layer's switch does not reach the 3D canvas",
                    MeshLayers.onCanvas(layer, on) && !MeshLayers.onCanvas(layer, off))
            }
        }
        assertNotEquals("Heat opacity does not reach the 3D bake",
            MeshLayers.baked(base), MeshLayers.baked(base.copy(heatmapOpacity = 0.3f)))
        assertEquals(0.3f, MeshLayers.baked(base.copy(heatmapOpacity = 0.3f)).habitatOpacity)
    }
}
