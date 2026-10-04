package com.ginsengo.steward.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ginsengo.steward.data.db.Suggestion
import com.ginsengo.steward.field.FixAverager
import com.ginsengo.steward.learn.FindLearner
import com.ginsengo.steward.prospect.Prospects
import com.ginsengo.steward.research.Provider
import com.ginsengo.steward.research.ResearchPrompt
import com.ginsengo.steward.research.SuggestionAssembler
import com.ginsengo.steward.terrain.GinsengSuitability
import com.ginsengo.steward.ui.map.Basemap
import com.ginsengo.steward.ui.map.DARK_STYLE
import com.ginsengo.steward.ui.map.FieldMap
import com.ginsengo.steward.ui.map.SceneLayer
import com.ginsengo.steward.terrain3d.Handoff
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.zIndex
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import kotlin.math.roundToInt

private enum class Sheet { NONE, SUGGEST, FIND, LAYERS }

@Composable
fun MainScreen(vm: FieldViewModel) {
    val me by vm.location.collectAsState()
    val tracking by vm.tracking.collectAsState()
    val track by vm.track.collectAsState()
    val finds by vm.finds.collectAsState()
    val suggestions by vm.suggestions.collectAsState()
    val run by vm.latestRun.collectAsState()
    val layers by vm.layers.collectAsState()
    val view3d by vm.view3d.collectAsState()
    val busy by vm.busy.collectAsState()
    val verdict by vm.verdict.collectAsState()
    val toast by vm.toast.collectAsState()
    val permission by vm.permission.collectAsState()

    var sheet by remember { mutableStateOf(Sheet.NONE) }
    var mapStatus by remember { mutableStateOf("") }
    var meshStatus by remember { mutableStateOf("") }
    LaunchedEffect(view3d) { if (!view3d) meshStatus = "" }
    val habitatStatus = if (view3d && meshStatus.isNotBlank()) meshStatus else mapStatus

    // The cross-fade between the map and the 3D view (exe.md A8, Handoff.blend): 0 is the map;
    // Handoff.COVERED, the 3D view covering it with its relief at the measured hand-off;
    // Handoff.RISEN, full relief and the gestures on the 3D view. The fade starts only once the
    // 3D view is built, fitted and drawn: until then the map stays, and stays usable.
    // Saved across a rotation (A16): a risen 3D view comes back risen, but only with its square still
    // held by the ViewModel; without one it warms under the map again rather than show nothing.
    var savedReveal by rememberSaveable { mutableFloatStateOf(0f) }
    val reveal = remember {
        Animatable(if (view3d && savedReveal >= Handoff.RISEN && vm.meshSession.scene != null) Handoff.RISEN else 0f)
    }
    SideEffect { savedReveal = reveal.value }
    var meshReady by remember { mutableStateOf(false) }
    LaunchedEffect(view3d, meshReady) {
        if (view3d) {
            if (meshReady || reveal.value > 0f) reveal.animateTo(Handoff.RISEN, tween(REVEAL_MS, easing = LinearEasing))
        } else if (reveal.value > 0f) {
            if (reveal.value > Handoff.COVERED) {
                // Sink to the hand-off relief, easing the pitch into the flat map's range on the way
                // (Handoff.sinkPitch, A15): the landing below then moves nothing.
                val pitch0 = vm.camera.camera.pitch
                val from = reveal.value
                reveal.animateTo(Handoff.COVERED, tween(SINK_MS, easing = LinearEasing)) {
                    val p = Handoff.sinkPitch(pitch0, from, value)
                    if (p != vm.camera.camera.pitch) vm.camera.report(vm.camera.camera.copy(pitch = p))
                }
            }
            vm.landFlat()
            reveal.animateTo(0f, tween(FADE_OUT_MS, easing = LinearEasing))
        }
        // Back on the map: the square and its memory go (A13, A16).
        if (!view3d && reveal.value == 0f) vm.meshSession.end()
    }
    val revealed = reveal.value
    // The 3D view drapes the 2D map's style only when that style actually loaded here.
    var darkStyleLoaded by remember { mutableStateOf(false) }
    val weights = verdict?.active ?: GinsengSuitability.PRIOR_WEIGHTS

    LaunchedEffect(toast) {
        if (toast != null) { kotlinx.coroutines.delay(3_500); vm.consumeToast() }
    }

    Box(Modifier.fillMaxSize().background(Gen.Bg)) {
        // One stack: the map always; the 3D view over it while it shows, or under it (hidden,
        // building) until it is ready to fade in. Both are TextureViews, so they stack and fade.
        Box(Modifier.fillMaxSize()) {
            FieldMap(
                me = me, track = track, finds = finds, suggestions = suggestions,
                radiusCenter = run?.let { it.centerLat to it.centerLng },
                layers = layers, weights = weights, demStore = vm.container.demTiles,
                camera = vm.camera,
                onHabitatStatus = { mapStatus = it },
                modifier = Modifier.fillMaxSize(),
                onBasemap = { darkStyleLoaded = it },
                active = revealed < Handoff.RISEN,
            )
            if (view3d || revealed > 0f) {
                Terrain3DView(
                    me = me, camera = vm.camera,
                    track = track, finds = finds, suggestions = suggestions,
                    layers = layers, weights = weights, demStore = vm.container.demTiles,
                    styleUri = if (darkStyleLoaded && layers.basemap == Basemap.DARK) DARK_STYLE else null,
                    onStatus = { meshStatus = it },
                    modifier = Modifier.fillMaxSize().zIndex(if (revealed > 0f) 1f else -1f),
                    radiusCenter = run?.let { it.centerLat to it.centerLng },
                    reveal = revealed,
                    onReady = { meshReady = it },
                    session = vm.meshSession,
                    budget = vm.container.memoryBudget,
                )
            }
        }

        // ---- status line
        Column(Modifier.statusBarsPadding().padding(10.dp).align(Alignment.TopStart)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(me?.let { "GPS ±${it.accuracyM.roundToInt()} m" } ?: if (permission) "GPS…" else "No location permission",
                    if (me != null) Gen.Text else Gen.Amber)
                if (tracking.recording) {
                    Chip("● REC %.2f km".format(tracking.distanceM / 1000), Gen.Danger)
                }
                Chip(if (vm.container.isOnline()) "Online" else "Offline", Gen.TextDim)
            }
            val line = busy ?: toast ?: habitatStatus.takeIf { it.isNotBlank() }
            if (line != null) {
                Spacer(Modifier.height(4.dp))
                Chip(line, Gen.TextDim)
            }
        }

        // ---- side actions
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // One switch between the two projections of the same map: the camera stays one
            // camera (FieldViewModel.setView3d applies to3d/to2d to it).
            SmallFloatingActionButton(
                onClick = { vm.setView3d(!view3d) },
                containerColor = Gen.SurfaceHigh, contentColor = Gen.Text,
                modifier = Modifier.semantics { contentDescription = if (view3d) "Show flat map" else "Show in 3D" },
            ) { Icon(if (view3d) Icons.Filled.Map else Icons.Filled.Terrain, null) }
            SmallFloatingActionButton(
                onClick = { vm.recenter() },
                containerColor = Gen.SurfaceHigh, contentColor = Gen.Text,
                modifier = Modifier.semantics { contentDescription = "Centre on me" },
            ) { Icon(Icons.Filled.MyLocation, null) }
        }

        // ---- bottom bar
        Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp)
                .background(Gen.Surface.copy(alpha = 0.92f), RoundedCornerShape(22.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            BarButton(if (tracking.recording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                if (tracking.recording) "Stop" else "Track",
                if (tracking.recording) Gen.Danger else Gen.Text) { vm.toggleTracking() }
            BarButton(Icons.Filled.Add, "Find", Gen.Amber) {
                vm.startBurst(); sheet = Sheet.FIND
            }
            BarButton(Icons.Filled.AutoAwesome, "Suggest", Gen.Accent) { sheet = Sheet.SUGGEST }
            BarButton(Icons.Filled.Layers, "Layers", Gen.Text) { sheet = Sheet.LAYERS }
        }
    }

    when (sheet) {
        Sheet.SUGGEST -> SuggestSheet(vm, suggestions, run, busy, me) { sheet = Sheet.NONE }
        Sheet.FIND -> FindSheet(vm) { sheet = Sheet.NONE }
        Sheet.LAYERS -> LayersSheet(vm, verdict) { sheet = Sheet.NONE }
        Sheet.NONE -> Unit
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Text(
        text, color = color, fontSize = 12.sp,
        modifier = Modifier.background(Gen.Bg.copy(alpha = 0.78f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun BarButton(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Column(
        Modifier.width(78.dp).clickable(onClick = onClick).padding(vertical = 6.dp)
            .semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        Text(label, color = Gen.Text, fontSize = 12.sp)
    }
}

// ============================================================================ Suggest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestSheet(
    vm: FieldViewModel,
    items: List<Suggestion>,
    run: com.ginsengo.steward.data.db.ResearchRun?,
    busy: String?,
    me: com.ginsengo.steward.field.FieldLocation?,
    onClose: () -> Unit,
) {
    val uri = LocalUriHandler.current
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Gen.Surface) {
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Within 10 miles", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Button(onClick = { vm.research() }, enabled = busy == null) { Text(if (busy != null) "Working…" else "Refresh") }
                }
                vm.complianceLine()?.let { Text(it, color = Gen.TextDim, fontSize = 12.sp) }
                if (run != null) {
                    val how = if (run.provider != null) {
                        "${run.model ?: run.provider} · ${run.citationsKept} sources kept" +
                                (if (run.citationsRejected > 0) " · ${run.citationsRejected} unretrieved removed" else "") +
                                (if (run.idsRejected > 0) " · ${run.idsRejected} invalid picks removed" else "")
                    } else "Computed on this phone"
                    Text("$how · ${if (run.weights == "LEARNED") "learned weights" else "published weights"}",
                        color = Gen.TextDim, fontSize = 12.sp)
                    run.message?.let { Text(it, color = Gen.Amber, fontSize = 12.sp) }
                    run.summary?.let { Spacer(Modifier.height(6.dp)); Text(it, fontSize = 13.sp) }
                }
                Text("Suggestions say where the terrain looks right, never where digging is allowed. " +
                        "Check land ownership, permission and your state's rules.",
                    color = Gen.TextDim, fontSize = 11.sp, modifier = Modifier.padding(vertical = 6.dp))
            }
            if (items.isEmpty()) item {
                Text(if (me == null) "Waiting for a GPS fix." else "No suggestions yet. Tap Refresh.",
                    color = Gen.TextDim, modifier = Modifier.padding(vertical = 16.dp))
            }
            items(items, key = { it.id }) { s ->
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    .background(Gen.SurfaceHigh, RoundedCornerShape(12.dp)).padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${s.rank}", color = if (s.provenance == Suggestion.PROVENANCE_MODEL) Gen.Accent else Gen.TextDim,
                            fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                        Text(s.headline, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    }
                    val where = me?.let {
                        val d = Prospects.distanceMetres(it.lat, it.lng, s.lat, s.lng)
                        val b = Prospects.bearingTrue(it.lat, it.lng, s.lat, s.lng)
                        "%.1f km %s".format(d / 1000, ResearchPrompt.octant(b))
                    } ?: ""
                    Text("$where · terrain %.2f · ${statusLabel(s.status)} · ${if (s.provenance == Suggestion.PROVENANCE_MODEL) "research model" else "computed"}"
                        .format(s.terrainScore), color = Gen.TextDim, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(s.rationale, fontSize = 13.sp)
                    Text("Look for: ${s.lookFor}", color = Gen.TextDim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    SuggestionAssembler.sourcesFromJson(s.sourcesJson).forEach { src ->
                        Text("↗ ${src.title.ifBlank { src.url }}", color = Gen.Blue, fontSize = 12.sp,
                            modifier = Modifier.padding(top = 2.dp).clickable { runCatching { uri.openUri(src.url) } })
                    }
                    Row {
                        TextButton(onClick = { vm.focusOn(s); onClose() }) { Text("Show on map") }
                        if (s.status == Suggestion.STATUS_NEW || s.status == Suggestion.STATUS_VISITED) {
                            TextButton(onClick = { vm.markNotFound(s) }) { Text("Walked it, none", color = Gen.TextDim) }
                        }
                    }
                }
            }
        }
    }
}

private fun statusLabel(s: String) = when (s) {
    Suggestion.STATUS_VISITED -> "visited"
    Suggestion.STATUS_FOUND -> "found ginseng"
    Suggestion.STATUS_NOT_FOUND -> "none found"
    else -> "not visited"
}

// ============================================================================ Find

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FindSheet(vm: FieldViewModel, onClose: () -> Unit) {
    val burst by vm.burstCount.collectAsState()
    val me by vm.location.collectAsState()
    var fix by remember { mutableStateOf<FixAverager.Result?>(null) }
    var plants by remember { mutableIntStateOf(1) }
    var prongs by remember { mutableStateOf<Int?>(null) }
    var note by remember { mutableStateOf("") }

    LaunchedEffect(burst) {
        // The averaging window ended on its own (20 s): take the position.
        if (fix == null && burst == FieldViewModel.BURST_DONE) fix = vm.stopBurst()
    }

    ModalBottomSheet(onDismissRequest = { vm.stopBurst(); onClose() }, containerColor = Gen.Surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text("Mark a find", style = MaterialTheme.typography.titleMedium)
            val f = fix
            if (f == null) {
                Text("Hold still: averaging GPS fixes (${burst.coerceAtLeast(0)} so far, " +
                        "${me?.accuracyM?.roundToInt() ?: "?"} m now).", color = Gen.TextDim)
                Button(onClick = { fix = vm.stopBurst() }, enabled = me != null, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Use this position")
                }
                return@Column
            }
            Text("Position ±${f.accuracyM.roundToInt()} m from ${f.fixCount} fix(es)", color = Gen.TextDim, fontSize = 12.sp)

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text("Plants", modifier = Modifier.width(70.dp))
                OutlinedButton(onClick = { plants = (plants - 1).coerceAtLeast(1) }) { Text("−") }
                Text("$plants", modifier = Modifier.padding(horizontal = 14.dp))
                OutlinedButton(onClick = { plants++ }) { Text("+") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Most prongs", modifier = Modifier.width(90.dp))
                (1..4).forEach { n -> FilterChip(selected = prongs == n, onClick = { prongs = if (prongs == n) null else n }, label = { Text("$n") }) }
            }
            OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(8.dp))
            // The user's word is the record (UserFinds): no find is saved as anything less.
            Text("Saved as your find. The map and the learner treat it as true.", color = Gen.Accent, fontSize = 13.sp)
            Button(onClick = { vm.saveFind(f, plants, prongs, note); onClose() },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("Save find") }
        }
    }
}

// ============================================================================ Layers + settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LayersSheet(vm: FieldViewModel, verdict: FindLearner.Verdict?, onClose: () -> Unit) {
    val layers by vm.layers.collectAsState()
    val settings by vm.settings.collectAsState()
    var keyText by remember(settings.provider) { mutableStateOf("") }
    var modelText by remember(settings.provider) { mutableStateOf(settings.model) }
    var notices by remember { mutableStateOf(false) }
    if (notices) NoticesDialog { notices = false }

    ModalBottomSheet(onDismissRequest = onClose, containerColor = Gen.Surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text("Layers", style = MaterialTheme.typography.titleMedium)
            // One row per switch in the scene description: each means the same in 2D and 3D.
            for (layer in SceneLayer.SHEET) {
                val label = SceneLayer.label(layer) + if (layer == SceneLayer.HABITAT)
                    " (${if (verdict?.adopted == true) "learned weights" else "published weights"})" else ""
                Toggle(label, layer.shown(layers)) { vm.setLayers(layer.set!!(layers, it)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Basemap.entries.forEach { b ->
                    FilterChip(selected = layers.basemap == b, onClick = { vm.setLayers(layers.copy(basemap = b)) }, label = { Text(b.label) })
                }
            }
            Text("Heat opacity", color = Gen.TextDim, fontSize = 12.sp)
            Slider(layers.heatmapOpacity, { vm.setLayers(layers.copy(heatmapOpacity = it)) }, valueRange = 0.2f..1f)
            Text("Green: terrain model (research-grade estimate; cannot see soil calcium or canopy). " +
                    "Blue lines: creeks and drains traced from elevation, not surveyed; small ones may be dry. " +
                    "Blue glow: where you've recorded a track. Amber: your finds.",
                color = Gen.TextDim, fontSize = 11.sp)

            Spacer(Modifier.height(14.dp))
            Text("Learning from your finds", style = MaterialTheme.typography.titleSmall)
            Text(verdict?.reason ?: "Waiting for a terrain scan.", fontSize = 13.sp)
            verdict?.learned?.let { learned ->
                GinsengSuitability.Factor.entries.forEach { f ->
                    Text("%-22s published %.2f   learned %.2f".format(f.display, verdict.prior[f.ordinal], learned[f.ordinal]),
                        color = Gen.TextDim, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(14.dp))
            Text("Offline", style = MaterialTheme.typography.titleSmall)
            OutlinedButton(onClick = { vm.saveOffline() }) { Text("Save 10 miles around me") }

            Spacer(Modifier.height(14.dp))
            Text("Research model", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Provider.entries.forEach { p ->
                    FilterChip(selected = settings.provider == p, onClick = { vm.setProvider(p) }, label = { Text(p.label) })
                }
            }
            OutlinedTextField(
                keyText, { keyText = it },
                label = { Text(if (settings.hasKey) "${settings.provider.label} API key (saved; type to replace)" else "${settings.provider.label} API key") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(modelText, { modelText = it }, label = { Text("Model") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row {
                TextButton(onClick = { if (keyText.isNotBlank()) vm.setKey(keyText); vm.setModel(modelText); keyText = "" }) { Text("Save") }
                if (settings.hasKey) TextButton(onClick = { vm.setKey("") }) { Text("Remove key", color = Gen.Danger) }
            }
            Toggle("Send to ${settings.provider.label}", settings.consent) { vm.setConsent(it) }
            Text("When on, a research request sends: the ~11 km grid cell you're in, your state's rules, " +
                    "each candidate's terrain numbers and distance band, and counts of past outcomes. " +
                    "Never your GPS fix, your track, or the location of any find.",
                color = Gen.TextDim, fontSize = 11.sp)
            Toggle("Refresh automatically after moving 5 km", settings.auto) { vm.setAuto(it) }

            Spacer(Modifier.height(14.dp))
            TextButton(onClick = { notices = true }) { Text("Open-source notices") }
        }
    }
}

/**
 * The licence notices of code ported into the app (THIRD_PARTY_NOTICES.md, packaged as an
 * asset by the build): BSD-3 requires them in the documentation of a binary distribution.
 */
@Composable
private fun NoticesDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val text = remember {
        runCatching { context.assets.open(NOTICES_ASSET).bufferedReader().use { it.readText() } }
            .getOrElse { "Notices unavailable in this build." }
    }
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
        title = { Text("Open-source notices") },
        text = {
            Text(text, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()))
        },
        containerColor = Gen.Surface,
    )
}

/** Copied into the APK's assets from the repository root by the build (app/build.gradle.kts). */
const val NOTICES_ASSET = "THIRD_PARTY_NOTICES.md"

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 14.sp)
        Switch(checked = value, onCheckedChange = onChange)
    }
}

/** The cross-fade's timing: fade in and rise (0 → RISEN), sink to the hand-off, fade out. */
private const val REVEAL_MS = 1_200
private const val SINK_MS = 600
private const val FADE_OUT_MS = 600
