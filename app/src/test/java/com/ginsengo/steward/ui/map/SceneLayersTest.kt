package com.ginsengo.steward.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scene description (exe.md A9) and its depth policy (A10): the validation the register asks
 * for. A layer added without a renderer does not compile (the backends' `when`s are exhaustive);
 * what compiles is checked here: every declared policy is honoured by both backends, and every
 * switch in the Layers sheet changes what both views draw.
 */
class SceneLayersTest {

    @Test
    fun everyLayersDepthPolicyIsHonouredByBothBackends() {
        for (layer in SceneLayer.entries) {
            val mesh = MeshLayers.draw(layer)
            when (layer.depth) {
                Depth.DRAPED -> assertEquals("$layer is draped: the 3D view must bake it onto the mesh", MeshDraw.BAKED, mesh)
                Depth.ON_TOP -> assertEquals("$layer is on top: the 3D view must draw it over the mesh", MeshDraw.CANVAS, mesh)
                Depth.OCCLUDED -> assertEquals("$layer is occluded: the 3D view must test it against the terrain", MeshDraw.CANVAS_OCCLUDED, mesh)
            }
        }
    }

    /** On the flat map the policy is the stacking: every draped layer under every layer standing on it. */
    @Test
    fun theFlatMapStacksEveryDrapedLayerUnderEveryLayerOnTop() {
        val order = FlatLayers.ORDER
        assertEquals("a style layer drawn for two scene layers", order.size, order.toSet().size)
        for (layer in SceneLayer.entries) assertTrue("$layer has no flat renderer", FlatLayers.ids(layer).isNotEmpty())
        fun indices(vararg d: Depth) = SceneLayer.entries.filter { it.depth in d }.flatMap(FlatLayers::ids).map(order::indexOf)
        val standing = indices(Depth.ON_TOP, Depth.OCCLUDED)
        assertTrue("draped ${indices(Depth.DRAPED)} vs standing $standing", indices(Depth.DRAPED).max() < standing.min())
        // A18: the markers a digger walks to declare occlusion; the lines do not.
        assertEquals(setOf(SceneLayer.FINDS, SceneLayer.SUGGESTIONS, SceneLayer.ME),
            SceneLayer.entries.filter { it.depth == Depth.OCCLUDED }.toSet())
        // The order FieldMap.addLayers has always used (checked again on device against the loaded style).
        assertEquals(listOf("g-hillshade", "g-habitat-layer", "g-contour-layer", "g-water-layer", "g-visited-layer",
            "g-track-layer", "g-finds-heat", "g-finds-dots", "g-ring-layer", "g-suggest-layer", "g-me-layer"), order)
    }

    @Test
    fun theSheetIsExactlyTheLayersWithASwitch() {
        assertEquals(SceneLayer.SHEET.size, SceneLayer.SHEET.toSet().size)
        assertEquals(SceneLayer.entries.filter { it.set != null }.toSet(), SceneLayer.SHEET.toSet())
    }

    /**
     * No dead controls: flipping any switch in the sheet changes the flat map's visibility of that
     * layer AND what the 3D view draws (the texture bake for draped layers, the canvas for the rest).
     * The Hillshade switch used to fail this: the 3D bake shaded regardless.
     */
    @Test
    fun everySwitchChangesWhatBothViewsDraw() {
        val base = MapLayerState()
        for (layer in SceneLayer.SHEET) {
            val on = layer.set!!(base, true); val off = layer.set.invoke(base, false)
            assertTrue("$layer: set/shown disagree", layer.shown(on) && !layer.shown(off))
            for (other in SceneLayer.entries - layer) assertEquals("$layer's switch moved $other", other.shown(on), other.shown(off))
            when (MeshLayers.draw(layer)) {
                MeshDraw.BAKED -> assertNotEquals("$layer's switch does not reach the 3D bake", MeshLayers.baked(on), MeshLayers.baked(off))
                MeshDraw.CANVAS, MeshDraw.CANVAS_OCCLUDED -> assertTrue("$layer's switch does not reach the 3D canvas",
                    MeshLayers.onCanvas(layer, on) && !MeshLayers.onCanvas(layer, off))
            }
        }
        assertNotEquals("Heat opacity does not reach the 3D bake",
            MeshLayers.baked(base), MeshLayers.baked(base.copy(heatmapOpacity = 0.3f)))
        assertEquals(0.3f, MeshLayers.baked(base.copy(heatmapOpacity = 0.3f)).habitatOpacity)
    }
}
