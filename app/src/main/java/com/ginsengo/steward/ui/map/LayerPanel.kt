package com.ginsengo.steward.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ginsengo.steward.core.Provenance
import com.ginsengo.steward.terrain.GinsengSuitability
import com.ginsengo.steward.terrain.SuitabilityRasterizer
import com.ginsengo.steward.terrain3d.Terrain3DStatus
import com.ginsengo.steward.ui.components.ChoiceChip
import com.ginsengo.steward.ui.components.GenCard
import com.ginsengo.steward.ui.components.ProvenanceTag
import com.ginsengo.steward.ui.components.SectionHeader
import com.ginsengo.steward.ui.theme.Gen
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LayerPanel(
    state: MapLayerState,
    onChange: (MapLayerState) -> Unit,
    heatStatus: SuitabilityRasterizer.Raster?,
    terrainStatus: Terrain3DStatus,
    modifier: Modifier = Modifier,
) {
    GenCard(modifier) {
        SectionHeader("Map layers")

        Text("Base", style = MaterialTheme.typography.labelSmall, color = Gen.TextSecondary)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Basemap.entries.forEach { b ->
                ChoiceChip(b.label, state.basemap == b, { onChange(state.copy(basemap = b)) })
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Terrain", style = MaterialTheme.typography.labelSmall, color = Gen.TextSecondary)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip(
                "Height map", state.heightOverlay,
                { onChange(state.copy(heightOverlay = !state.heightOverlay)) },
            )
            ChoiceChip(
                "Hillshade", state.hillshade,
                { onChange(state.copy(hillshade = !state.hillshade)) },
            )
            ChoiceChip(
                "Pitched relief", state.pitchedRelief,
                { onChange(state.copy(pitchedRelief = !state.pitchedRelief)) },
                accent = Gen.Warning,
            )
            ChoiceChip(
                "3D terrain", state.terrainMesh,
                { onChange(state.copy(terrainMesh = !state.terrainMesh)) },
            )
        }
        if (state.pitchedRelief && !state.terrainMesh) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Pitched relief tilts the camera and deepens the shading — flat ground that " +
                        "reads as depth. Turn on 3D terrain for real geometry.",
                style = MaterialTheme.typography.bodySmall,
                color = Gen.TextSecondary,
            )
        }
        if (state.heightOverlay) {
            OpacityRow("Height opacity", state.heightOpacity) {
                onChange(state.copy(heightOpacity = it))
            }
        }

        if (state.terrainMesh) {
            OpacityRow("3D opacity", state.meshOpacity) {
                onChange(state.copy(meshOpacity = it))
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Exaggeration ${"%.1f".format(state.meshExaggeration)}×",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gen.TextSecondary,
                    modifier = Modifier.width(120.dp),
                )
                Slider(
                    value = state.meshExaggeration,
                    onValueChange = { onChange(state.copy(meshExaggeration = it)) },
                    valueRange = 1f..4f,
                    colors = SliderDefaults.colors(
                        thumbColor = Gen.Primary,
                        activeTrackColor = Gen.Primary,
                        inactiveTrackColor = Gen.Hairline,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(4.dp))
            ChoiceChip(
                "Tint 3D by forecast", state.meshForecastTint,
                { onChange(state.copy(meshForecastTint = !state.meshForecastTint)) },
            )
            Spacer(Modifier.height(8.dp))
            TerrainStatusLine(terrainStatus)
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "Ginseng forecast",
            style = MaterialTheme.typography.labelSmall,
            color = Gen.TextSecondary,
        )
        Spacer(Modifier.height(6.dp))
        ChoiceChip(
            "Habitat heatmap", state.habitatHeatmap,
            { onChange(state.copy(habitatHeatmap = !state.habitatHeatmap)) },
        )

        if (state.habitatHeatmap) {
            OpacityRow("Heatmap opacity", state.heatmapOpacity) {
                onChange(state.copy(heatmapOpacity = it))
            }

            Spacer(Modifier.height(10.dp))
            HeatLegend()

            Spacer(Modifier.height(10.dp))
            if (heatStatus == null) {
                Text(
                    "Reading terrain…",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gen.TextSecondary,
                )
            } else {
                val res = "%.1f".format(heatStatus.metresPerDemCell)
                val cov = (heatStatus.coverage * 100).roundToInt()
                Text(
                    "Elevation zoom ${heatStatus.demZoom} · $res m per cell · " +
                            "${heatStatus.superSample}× supersampled · " +
                            "slope-position radius ${heatStatus.tpiRadiusCells} cells" +
                            if (cov < 100) " · $cov% tile coverage" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (cov < 100) Gen.Warning else Gen.TextSecondary,
                )
            }

            Spacer(Modifier.height(10.dp))
            ProvenanceTag(
                Provenance.RESEARCH_ESTIMATE,
                detail = "Terrain-only forecast from a published index set (McCune & Keon " +
                        "heat load, topographic position, multiple-flow wetness). Weighted " +
                        "by hand from site-selection literature — not a fitted model, and " +
                        "never validated against known patches.",
            )
            Spacer(Modifier.height(8.dp))
            Text(
                GinsengSuitability.CALCIUM_NOTE,
                style = MaterialTheme.typography.bodySmall,
                color = Gen.Warning,
            )
        }

        Spacer(Modifier.height(10.dp))
        Text(
            state.basemap.attribution,
            style = MaterialTheme.typography.bodySmall,
            color = Gen.TextSecondary,
        )
    }
}

@Composable
private fun OpacityRow(label: String, value: Float, onChange: (Float) -> Unit) {
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = Gen.TextSecondary,
            modifier = Modifier.width(120.dp),
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0.1f..1f,
            colors = SliderDefaults.colors(
                thumbColor = Gen.Primary,
                activeTrackColor = Gen.Primary,
                inactiveTrackColor = Gen.Hairline,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Legend matching [SuitabilityRasterizer.colourFor] exactly, sampled from it. */
@Composable
private fun HeatLegend() {
    Column {
        Row(Modifier.fillMaxWidth().height(10.dp).clip(Gen.PillShape)) {
            val steps = 24
            repeat(steps) { i ->
                val s = 0.35 + (i / (steps - 1.0)) * 0.65
                val argb = SuitabilityRasterizer.colourFor(s, 0.35)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .height(10.dp)
                        .background(Color(argb).copy(alpha = 1f))
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("Unlikely", "Marginal", "Possible", "Worth walking").forEach {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Gen.TextSecondary)
            }
        }
    }
}

/**
 * Reports what the 3D overlay is actually doing, including when it has refused to draw.
 *
 * The alignment figure is the important one. The overlay reconstructs MapLibre's camera
 * rather than borrowing it, and a reconstruction that is subtly wrong still renders
 * convincing terrain — in the wrong place. Showing the residual against the map's own
 * projection means a misalignment is visible as a number rather than as a hillside that
 * looks fine and is not.
 */
@Composable
private fun TerrainStatusLine(status: Terrain3DStatus) {
    val colour = when {
        status.glError != null -> Gen.Alert
        status.alignment != null && !status.alignment.aligned -> Gen.Alert
        status.drawing -> Gen.Primary
        else -> Gen.TextSecondary
    }
    Text(status.describe(), style = MaterialTheme.typography.bodySmall, color = colour)
    if (status.alignment != null && !status.alignment.aligned) {
        Spacer(Modifier.height(4.dp))
        Text(
            "The mesh is hidden rather than drawn out of register with the map.",
            style = MaterialTheme.typography.bodySmall,
            color = Gen.TextSecondary,
        )
    }
}
