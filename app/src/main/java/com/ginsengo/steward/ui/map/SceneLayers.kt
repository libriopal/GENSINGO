package com.ginsengo.steward.ui.map

import com.ginsengo.steward.terrain3d.TerrainTextures

/**
 * How a layer meets the ground (exe.md A10). Since wave M.1 the 3D view is the only map (owner
 * directive), so this decides how the one map draws it.
 */
enum class Depth {
    /** Painted on the ground: baked into the mesh's texture, or sampled from the travel memory's mask. */
    DRAPED,

    /**
     * Stands on the ground and is hidden by a ridge in front of it. The canvas tests each marker
     * against the terrain ([com.ginsengo.steward.terrain3d.Occlusion], A18) and draws a hidden one
     * faint: still a place to walk to, plainly behind the ridge.
     */
    OCCLUDED,

    /** Drawn over the ground and never hidden: lines over the 3D surface. */
    ON_TOP,
}

/**
 * Every layer the map draws: one scene description (exe.md A9). The 2D backend went with the flat map
 * in M.1; [MeshLayers] is an exhaustive `when`, so a layer added here without a
 * renderer does not compile.
 *
 * Declaration order is drawing order, bottom to top. [shown] reads the Layers sheet; [set] is that
 * switch, or null for the two layers that have none (the scan ring and your own position, drawn
 * whenever they exist).
 *
 * The basemap is not a layer here: MapDrape's snapshot of the dark map, under the baked layers.
 */
enum class SceneLayer(
    val depth: Depth,
    val shown: (MapLayerState) -> Boolean,
    val set: ((MapLayerState, Boolean) -> MapLayerState)?,
) {
    HILLSHADE(Depth.DRAPED, { it.hillshade }, { s, on -> s.copy(hillshade = on) }),
    HABITAT(Depth.DRAPED, { it.habitat }, { s, on -> s.copy(habitat = on) }),
    /** J20: walked ground greyed, so colour is left only on ground you have not walked. */
    UNWALKED(Depth.DRAPED, { it.unwalkedOnly }, { s, on -> s.copy(unwalkedOnly = on) }),
    CONTOURS(Depth.DRAPED, { it.contours }, { s, on -> s.copy(contours = on) }),
    WATER(Depth.DRAPED, { it.water }, { s, on -> s.copy(water = on) }),
    /** J31: the persistent travel memory, every fix the app has received. */
    VISITED(Depth.DRAPED, { it.visited }, { s, on -> s.copy(visited = on) }),
    TRACK(Depth.ON_TOP, { it.trackLine }, { s, on -> s.copy(trackLine = on) }),
    FINDS(Depth.OCCLUDED, { it.finds }, { s, on -> s.copy(finds = on) }),
    RING(Depth.ON_TOP, { true }, null),
    SUGGESTIONS(Depth.OCCLUDED, { it.suggestions }, { s, on -> s.copy(suggestions = on) }),
    ME(Depth.OCCLUDED, { true }, null),
    ;

    companion object {
        /** The Layers sheet's switches, in the sheet's order: exactly the layers with a [set]. */
        val SHEET = listOf(HABITAT, UNWALKED, VISITED, WATER, FINDS, TRACK, SUGGESTIONS, CONTOURS, HILLSHADE)

        /** The sheet's label for a switch. */
        fun label(layer: SceneLayer): String = when (layer) {
            HILLSHADE -> "Hillshade"
            HABITAT -> "Habitat heatmap"
            UNWALKED -> "Only show ground I haven't walked"
            CONTOURS -> "Contour lines"
            WATER -> "Creeks & streams (traced from elevation)"
            VISITED -> "Where I've been"
            TRACK -> "Track line"
            FINDS -> "My finds"
            RING -> "Scan radius"
            SUGGESTIONS -> "Suggestions"
            ME -> "My position"
        }
    }
}

/** How the 3D view draws each layer. */
enum class MeshDraw {
    /** Into the ground texture (TerrainTextures): it follows the mesh. */
    BAKED,

    /** From the travel memory's mask (TravelMask), sampled by the shader over the same ground. */
    MEMORY,

    /** On the Compose canvas over the GL surface, each point lifted to the terrain under it. */
    CANVAS,

    /** On the canvas, and drawn faint where the terrain hides it from the eye (Occlusion). */
    CANVAS_OCCLUDED,
}

/** The 3D view's renderer for each layer. */
object MeshLayers {
    fun draw(layer: SceneLayer): MeshDraw = when (layer) {
        SceneLayer.HILLSHADE, SceneLayer.HABITAT, SceneLayer.CONTOURS, SceneLayer.WATER -> MeshDraw.BAKED
        // The travel memory and its J20 grey-out live in their own mask: a new cell must not
        // re-bake the colour texture.
        SceneLayer.VISITED, SceneLayer.UNWALKED -> MeshDraw.MEMORY
        // Lines stay on top: a line half behind a ridge would need per-segment tests for little gain.
        SceneLayer.TRACK, SceneLayer.RING -> MeshDraw.CANVAS
        SceneLayer.FINDS, SceneLayer.SUGGESTIONS, SceneLayer.ME -> MeshDraw.CANVAS_OCCLUDED
    }

    /** The texture bake's switches, from the sheet, through the registry. */
    fun baked(s: MapLayerState): TerrainTextures.Layers = TerrainTextures.Layers(
        habitat = SceneLayer.HABITAT.shown(s),
        water = SceneLayer.WATER.shown(s),
        contours = SceneLayer.CONTOURS.shown(s),
        hillshade = SceneLayer.HILLSHADE.shown(s),
        habitatOpacity = s.heatmapOpacity,
    )

    /** Whether the canvas draws [layer] now. */
    fun onCanvas(layer: SceneLayer, s: MapLayerState): Boolean =
        (draw(layer) == MeshDraw.CANVAS || draw(layer) == MeshDraw.CANVAS_OCCLUDED) && layer.shown(s)

    /** Whether the shader draws [layer] from the travel memory now. */
    fun onMemory(layer: SceneLayer, s: MapLayerState): Boolean = draw(layer) == MeshDraw.MEMORY && layer.shown(s)
}
