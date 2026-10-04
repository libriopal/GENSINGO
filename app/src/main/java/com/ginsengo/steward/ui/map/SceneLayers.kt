package com.ginsengo.steward.ui.map

import com.ginsengo.steward.terrain3d.TerrainTextures

/**
 * How a layer meets the ground (exe.md A10). In 3D it decides how the layer is drawn; on the flat
 * map, where everything lies on one plane, it decides the stacking: every draped layer under every
 * layer drawn on top.
 */
enum class Depth {
    /** Painted on the ground: baked into the mesh's texture in 3D, so it follows ridges and coves. */
    DRAPED,

    /**
     * Stands on the ground and is hidden by a ridge in front of it. In 3D the canvas tests each
     * marker against the terrain ([com.ginsengo.steward.terrain3d.Occlusion], A18) and draws a
     * hidden one faint: still a place to walk to, plainly behind the ridge. On the flat map there is
     * no ridge, so it stacks with the layers drawn on top.
     */
    OCCLUDED,

    /** Drawn over the ground and never hidden: markers and lines over the 3D surface. */
    ON_TOP,
}

/**
 * Every layer the map draws: ONE scene description, drawn by two backends (exe.md A9). The flat
 * map draws each through [FlatLayers], the 3D view through [MeshLayers]; both tables are
 * exhaustive `when`s, so a layer added here without a renderer in either backend does not compile.
 *
 * Declaration order is drawing order, bottom to top, in both backends. [shown] reads the Layers
 * sheet, so a switch means the same thing in 2D and 3D; [set] is that switch, or null for the two
 * layers that have none (the scan ring and your own position, drawn whenever they exist).
 *
 * The basemap is not a layer here: it is the style both backends draw on (2D: the style itself;
 * 3D: MapDrape's snapshot of it, under the baked layers).
 */
enum class SceneLayer(
    val depth: Depth,
    val shown: (MapLayerState) -> Boolean,
    val set: ((MapLayerState, Boolean) -> MapLayerState)?,
) {
    HILLSHADE(Depth.DRAPED, { it.hillshade }, { s, on -> s.copy(hillshade = on) }),
    HABITAT(Depth.DRAPED, { it.habitat }, { s, on -> s.copy(habitat = on) }),
    CONTOURS(Depth.DRAPED, { it.contours }, { s, on -> s.copy(contours = on) }),
    WATER(Depth.DRAPED, { it.water }, { s, on -> s.copy(water = on) }),
    VISITED(Depth.ON_TOP, { it.visited }, { s, on -> s.copy(visited = on) }),
    TRACK(Depth.ON_TOP, { it.trackLine }, { s, on -> s.copy(trackLine = on) }),
    FINDS(Depth.OCCLUDED, { it.finds }, { s, on -> s.copy(finds = on) }),
    RING(Depth.ON_TOP, { true }, null),
    SUGGESTIONS(Depth.OCCLUDED, { it.suggestions }, { s, on -> s.copy(suggestions = on) }),
    ME(Depth.OCCLUDED, { true }, null),
    ;

    companion object {
        /** The Layers sheet's switches, in the sheet's order: exactly the layers with a [set]. */
        val SHEET = listOf(HABITAT, WATER, VISITED, FINDS, TRACK, SUGGESTIONS, CONTOURS, HILLSHADE)

        /** The sheet's label for a switch. */
        fun label(layer: SceneLayer): String = when (layer) {
            HILLSHADE -> "Hillshade"
            HABITAT -> "Habitat heatmap"
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

/** The flat map's renderer for each layer: the MapLibre style layers that draw it. */
object FlatLayers {
    fun ids(layer: SceneLayer): List<String> = when (layer) {
        SceneLayer.HILLSHADE -> listOf("g-hillshade")
        SceneLayer.HABITAT -> listOf("g-habitat-layer")
        SceneLayer.CONTOURS -> listOf("g-contour-layer")
        SceneLayer.WATER -> listOf("g-water-layer")
        SceneLayer.VISITED -> listOf("g-visited-layer")                 // a heatmap
        SceneLayer.TRACK -> listOf("g-track-layer")
        SceneLayer.FINDS -> listOf("g-finds-heat", "g-finds-dots")
        SceneLayer.RING -> listOf("g-ring-layer")
        SceneLayer.SUGGESTIONS -> listOf("g-suggest-layer")
        SceneLayer.ME -> listOf("g-me-layer")
    }

    /** The style's app layers bottom to top, as the map adds them; checked against the loaded style on device. */
    val ORDER: List<String> = SceneLayer.entries.flatMap(::ids)
}

/** How the 3D view draws each layer. */
enum class MeshDraw {
    /** Into the ground texture (TerrainTextures): it follows the mesh. */
    BAKED,

    /** On the Compose canvas over the GL surface, each point lifted to the terrain under it. */
    CANVAS,

    /** On the canvas, and drawn faint where the terrain hides it from the eye (Occlusion). */
    CANVAS_OCCLUDED,
}

/** The 3D view's renderer for each layer. */
object MeshLayers {
    fun draw(layer: SceneLayer): MeshDraw = when (layer) {
        SceneLayer.HILLSHADE, SceneLayer.HABITAT, SceneLayer.CONTOURS, SceneLayer.WATER -> MeshDraw.BAKED
        // VISITED is drawn as the line of where you walked: the 2D heatmap's blur has no 3D size.
        // Lines stay on top: a line half behind a ridge would need per-segment tests for little gain.
        SceneLayer.VISITED, SceneLayer.TRACK, SceneLayer.RING -> MeshDraw.CANVAS
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
    fun onCanvas(layer: SceneLayer, s: MapLayerState): Boolean = draw(layer) != MeshDraw.BAKED && layer.shown(s)
}
