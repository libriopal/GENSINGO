package com.ginsengo.steward.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ginsengo.steward.compliance.SeasonStatus
import com.ginsengo.steward.core.Provenance
import com.ginsengo.steward.ui.FieldViewModel
import com.ginsengo.steward.ui.Routes
import com.ginsengo.steward.ui.components.GenCard
import com.ginsengo.steward.ui.components.PrimaryAction
import com.ginsengo.steward.ui.components.ProvenanceTag
import com.ginsengo.steward.ui.components.SecondaryAction
import com.ginsengo.steward.ui.components.StatusPill
import com.ginsengo.steward.terrain.SuitabilityRasterizer
import com.ginsengo.steward.terrain3d.Terrain3DStatus
import com.ginsengo.steward.ui.map.FieldMap
import com.ginsengo.steward.ui.map.LayerPanel
import com.ginsengo.steward.ui.map.MapLayerState
import com.ginsengo.steward.ui.theme.Gen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: FieldViewModel,
    onRequestLocationPermission: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val location by vm.location.collectAsStateWithLifecycle()
    val compliance by vm.compliance.collectAsStateWithLifecycle()
    val patches by vm.patches.collectAsStateWithLifecycle()
    val granted by vm.permissionGranted.collectAsStateWithLifecycle()

    var followMe by remember { mutableStateOf(true) }
    var mapFailed by remember { mutableStateOf(false) }
    var layerState by remember { mutableStateOf(MapLayerState()) }
    var showLayers by remember { mutableStateOf(false) }
    var heatStatus by remember { mutableStateOf<SuitabilityRasterizer.Raster?>(null) }
    var terrainStatus by remember { mutableStateOf(Terrain3DStatus()) }

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded)
    )

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 172.dp,
        sheetContainerColor = Gen.Surface,
        sheetContentColor = Gen.TextPrimary,
        containerColor = Gen.Base,
        sheetContent = {
            HomeSheet(
                patchCount = patches.size,
                onNavigate = onNavigate,
            )
        },
    ) {
        Box(Modifier.fillMaxSize()) {

            if (!mapFailed) {
                FieldMap(
                    patches = patches,
                    me = location,
                    followMe = followMe,
                    layers = layerState,
                    demStore = vm.container.demTiles,
                    modifier = Modifier.fillMaxSize(),
                    onStyleFailed = { mapFailed = true },
                    onHeatmapStatus = { heatStatus = it },
                    onTerrainStatus = { terrainStatus = it },
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Map tiles unavailable offline.\nEvery other tool still works.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Gen.TextSecondary,
                    )
                }
            }

            // ---- Top floating compliance bar (PRD §5.5 mobile) ----
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                ComplianceBar(
                    stateName = compliance?.state?.stateName,
                    detected = compliance?.stateDetected == true,
                    season = compliance?.season ?: SeasonStatus.UNKNOWN,
                    accuracyM = location?.accuracyM,
                    hasFix = location != null,
                )
                val prohibited = compliance?.landStatuses.orEmpty()
                if (prohibited.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    GenCard {
                        prohibited.forEach { land ->
                            Text(
                                "${land.rule} - ${land.areaName}",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (land.rule == "PROHIBITED") Gen.Alert else Gen.Warning,
                            )
                            Text(
                                land.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Gen.TextSecondary,
                                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                            )
                            ProvenanceTag(land.provenance)
                        }
                    }
                }
                if (!granted) {
                    Spacer(Modifier.height(8.dp))
                    GenCard {
                        Text(
                            "Location is off",
                            style = MaterialTheme.typography.titleMedium,
                            color = Gen.TextPrimary,
                        )
                        Text(
                            "GENSINGO uses your position to centre the map, auto-detect which " +
                                    "state's rules apply, and stamp a patch when you log it. " +
                                    "Your position never leaves the phone.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Gen.TextSecondary,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                        PrimaryAction("Turn on location", onRequestLocationPermission)
                    }
                }
            }

            // ---- Bottom floating field-action bar, thumb level (PRD §5.5) ----
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 188.dp, start = 12.dp, end = 12.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SecondaryAction(
                    "Verify",
                    { onNavigate(Routes.VERIFY) },
                    Modifier.weight(1f),
                    accent = Gen.Warning,
                )
                PrimaryAction(
                    "Read Habitat",
                    { onNavigate(Routes.HABITAT) },
                    Modifier.weight(1.4f),
                )
                SecondaryAction(
                    "Log Patch",
                    { onNavigate(Routes.logWithScore(null)) },
                    Modifier.weight(1f),
                )
            }

            // ---- Recentre + layers ----
            Column(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 248.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SecondaryAction(
                    when {
                        layerState.terrainMesh -> "3D on"
                        layerState.habitatHeatmap -> "Forecast on"
                        else -> "Layers"
                    },
                    { showLayers = !showLayers },
                    accent = if (layerState.habitatHeatmap || layerState.terrainMesh)
                        Gen.Primary else Gen.TextSecondary,
                )
                SecondaryAction(
                    if (followMe) "Following" else "Recentre",
                    { followMe = !followMe },
                    accent = if (followMe) Gen.Primary else Gen.TextSecondary,
                )
            }

            if (showLayers) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 16.dp)
                ) {
                    LayerPanel(
                        state = layerState,
                        onChange = { layerState = it },
                        heatStatus = heatStatus,
                        terrainStatus = terrainStatus,
                    )
                }
            }
        }
    }
}

@Composable
private fun ComplianceBar(
    stateName: String?,
    detected: Boolean,
    season: SeasonStatus,
    accuracyM: Float?,
    hasFix: Boolean,
) {
    val seasonAccent = when (season) {
        SeasonStatus.OPEN -> Gen.Primary
        SeasonStatus.CLOSED -> Gen.Warning
        // Warning, not secondary grey. Grey reads as "nothing to report"; the app reaches
        // UNKNOWN precisely when it cannot rule out that harvest here is currently illegal,
        // which is something to report. Only the wording distinguishes it from CLOSED.
        SeasonStatus.UNKNOWN -> Gen.Warning
    }
    GenCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stateName ?: "No approved state here",
                    style = MaterialTheme.typography.titleMedium,
                    color = Gen.TextPrimary,
                )
                Text(
                    if (stateName == null) "Harvest is legal in 19 states only"
                    else if (detected) "Auto-detected offline" else "Selected by hand",
                    style = MaterialTheme.typography.labelSmall,
                    color = Gen.TextSecondary,
                )
            }
            Spacer(Modifier.width(8.dp))
            StatusPill(season.label, seasonAccent, pulsing = season == SeasonStatus.OPEN)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(
                if (!hasFix) "NO FIX" else "GPS ±${accuracyM?.toInt() ?: "?"} m",
                if (hasFix) Gen.Primary else Gen.Alert,
                pulsing = hasFix,
            )
        }
    }
}

@Composable
private fun HomeSheet(patchCount: Int, onNavigate: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Field tools",
            style = MaterialTheme.typography.titleLarge,
            color = Gen.TextPrimary,
            fontWeight = FontWeight.Bold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryAction("Where am I", { onNavigate(Routes.POSITION) }, Modifier.weight(1f))
            SecondaryAction("My Patches ($patchCount)", { onNavigate(Routes.PATCHES) }, Modifier.weight(1f))
            SecondaryAction("Stewardship", { onNavigate(Routes.GUIDE) }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryAction("Settings", { onNavigate(Routes.SETTINGS) }, Modifier.weight(1f),
                accent = Gen.TextSecondary)
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(4.dp))
        ProvenanceTag(
            Provenance.PROTOTYPE,
            detail = "Patch records are yours alone: stored on this phone, never uploaded.",
        )
    }
}
