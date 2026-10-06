package com.ginsengo.steward.ui

import androidx.compose.foundation.background
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Explore
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.text.KeyboardActions
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
import com.ginsengo.steward.field.BatteryMode
import com.ginsengo.steward.field.FixAverager
import com.ginsengo.steward.field.WayBack
import com.ginsengo.steward.learn.FindLearner
import com.ginsengo.steward.prospect.Prospects
import com.ginsengo.steward.research.Provider
import com.ginsengo.steward.research.ResearchPrompt
import com.ginsengo.steward.research.SuggestionAssembler
import com.ginsengo.steward.terrain.GinsengSuitability
import com.ginsengo.steward.ui.map.SceneLayer
import kotlin.math.roundToInt

private enum class Sheet { NONE, SUGGEST, FIND, LAYERS, PLACES, CHAT }

@Composable
fun MainScreen(vm: FieldViewModel) {
    val me by vm.location.collectAsState()
    val tracking by vm.tracking.collectAsState()
    val track by vm.track.collectAsState()
    val finds by vm.finds.collectAsState()
    val suggestions by vm.suggestions.collectAsState()
    val run by vm.latestRun.collectAsState()
    val layers by vm.layers.collectAsState()
    val busy by vm.busy.collectAsState()
    val lighter by vm.batteryMode.collectAsState()
    val battery by vm.battery.collectAsState()
    val verdict by vm.verdict.collectAsState()
    val toast by vm.toast.collectAsState()
    val permission by vm.permission.collectAsState()
    val pin by vm.searchPin.collectAsState()
    val results by vm.searchResults.collectAsState()
    val searching by vm.searching.collectAsState()
    val placeChosen by vm.placeChosen.collectAsState()
    val target by vm.target.collectAsState()
    val follow by vm.follow.collectAsState()
    val places by vm.places.collectAsState()
    val compassOn by vm.compass.collectAsState()
    val heading = rememberHeading(compassOn && me != null, me?.lat, me?.lng)
    // F.1: while following, the screen stays on (the walker glances at it); off again when follow stops.
    val hostView = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(follow) {
        hostView.keepScreenOn = follow
        onDispose { hostView.keepScreenOn = false }
    }

    var sheet by remember { mutableStateOf(Sheet.NONE) }
    var habitatStatus by remember { mutableStateOf("") }
    val weights = verdict?.active ?: GinsengSuitability.PRIOR_WEIGHTS

    LaunchedEffect(toast) {
        if (toast != null) { kotlinx.coroutines.delay(3_500); vm.consumeToast() }
    }

    Box(Modifier.fillMaxSize().background(Gen.Bg)) {
        // The one map (owner directive, wave M.1): the terrain in 3D, nothing under it.
        Terrain3DView(
            me = me, camera = vm.camera,
            track = track, finds = finds, suggestions = suggestions,
            layers = layers, weights = weights, demStore = vm.container.demTiles,
            // The chosen map on the ground, unless "Terrain only" or in the battery mode (J24).
            basemap = if (lighter) com.ginsengo.steward.ui.map.Basemap.NONE else layers.basemap,
            onStatus = { habitatStatus = it },
            modifier = Modifier.fillMaxSize(),
            radiusCenter = run?.let { it.centerLat to it.centerLng },
            session = vm.meshSession,
            budget = vm.container.memoryBudget,
            travel = vm.container.travelSource,
            lighter = lighter,
            // No square at the fallback start position, far from the owner: wait for the first fix.
            waitForFix = permission && me == null && !placeChosen,
            pin = pin,
            onLegendChange = { vm.setLayers(layers.copy(legend = it)) },
            heading = heading,
            target = target,
            places = places,
            onGoHere = { name, la, lo -> vm.goHere(name, la, lo) },
            onSave = { la, lo -> vm.savePlace(null, la, lo) },
            onUserMove = { if (follow) vm.setFollow(false); vm.placeChosen() },
        )

        // ---- search (P.1) and the status line
        Column(Modifier.statusBarsPadding().padding(10.dp).align(Alignment.TopStart)) {
            SearchBar(searching, pin != null || results != null, onSearch = vm::search, onClear = vm::clearSearch)
            results?.let { res ->
                Column(
                    Modifier.fillMaxWidth().padding(top = 4.dp).background(Gen.Surface.copy(alpha = 0.97f), RoundedCornerShape(12.dp))
                        .padding(vertical = 4.dp),
                ) {
                    res.places.forEach { place ->
                        Text(place.name, color = Gen.Text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().clickable { vm.goTo(place) }.padding(horizontal = 12.dp, vertical = 8.dp))
                    }
                    val note = res.error ?: if (res.places.isEmpty()) "Nothing found." else res.source
                    Text(note, color = Gen.TextDim, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(me?.let { "GPS ±${it.accuracyM.roundToInt()} m" } ?: if (permission) "GPS…" else "No location permission",
                    if (me != null) Gen.Text else Gen.Amber)
                if (tracking.recording) {
                    Chip("● REC %.2f km".format(tracking.distanceM / 1000), Gen.Danger)
                }
                Chip(if (vm.container.isOnline()) "Online" else "Offline", Gen.TextDim)
            }
            // J21: the way back to where this track began, while it records.
            val back = me?.let { WayBack.toStart(track, tracking.sessionId, it.lat, it.lng) }
            if (tracking.recording && back != null) {
                Spacer(Modifier.height(4.dp))
                Chip("Back to start: " + WayBack.describe(back), Gen.Amber)
            }
            // N.1: walking guidance to the chosen point; tap to stop.
            val tg = target
            if (tg != null && me != null) {
                val here = me!!
                val leg = com.ginsengo.steward.field.Guidance.leg(here.lat, here.lng, tg.lat, tg.lng)
                Spacer(Modifier.height(4.dp))
                Text(
                    "➜ ${tg.name.take(24)}: " + com.ginsengo.steward.field.Guidance.describe(leg, heading, here.accuracyM) + "   ✕",
                    color = Gen.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.background(Gen.Bg.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
                        .clickable { vm.clearTarget() }.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            // J24: the battery mode says so, and why.
            if (lighter) {
                Spacer(Modifier.height(4.dp))
                Chip("Battery saver: lighter 3D" + (battery.pct?.let { " ($it %)" } ?: ""), Gen.Amber)
            }
            val line = busy ?: toast ?: habitatStatus.takeIf { it.isNotBlank() }
            if (line != null) {
                Spacer(Modifier.height(4.dp))
                Chip(line, Gen.TextDim, maxLines = 2)
            }
        }

        // ---- side actions
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SmallFloatingActionButton(
                onClick = { vm.recenter() },
                containerColor = Gen.SurfaceHigh, contentColor = Gen.Text,
                modifier = Modifier.semantics { contentDescription = "Centre on me" },
            ) { Icon(Icons.Filled.MyLocation, null) }
            // N.1: follow me: the map keeps you in the middle until you move it.
            SmallFloatingActionButton(
                onClick = { vm.setFollow(!follow) },
                containerColor = if (follow) Gen.Accent else Gen.SurfaceHigh,
                contentColor = if (follow) Gen.Bg else Gen.Text,
                modifier = Modifier.semantics { contentDescription = if (follow) "Stop following me" else "Follow me" },
            ) { Icon(Icons.Filled.Explore, null) }
            // F.1: the field chat.
            SmallFloatingActionButton(
                onClick = { sheet = Sheet.CHAT },
                containerColor = Gen.SurfaceHigh, contentColor = Gen.Accent,
                modifier = Modifier.semantics { contentDescription = "Ask the field chat" },
            ) { Icon(Icons.Filled.Forum, null) }
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
            BarButton(Icons.Filled.Place, "Places", Gen.Text) { sheet = Sheet.PLACES }
            BarButton(Icons.Filled.Layers, "Layers", Gen.Text) { sheet = Sheet.LAYERS }
        }
    }

    when (sheet) {
        Sheet.SUGGEST -> SuggestSheet(vm, suggestions, run, busy, me) { sheet = Sheet.NONE }
        Sheet.FIND -> FindSheet(vm) { sheet = Sheet.NONE }
        Sheet.LAYERS -> LayersSheet(vm, verdict) { sheet = Sheet.NONE }
        Sheet.PLACES -> PlacesSheet(vm, places, me) { sheet = Sheet.NONE }
        Sheet.CHAT -> ChatSheet(vm) { sheet = Sheet.NONE }
        Sheet.NONE -> Unit
    }
}

@Composable
private fun Chip(text: String, color: Color, maxLines: Int = Int.MAX_VALUE) {
    Text(
        text, color = color, fontSize = 12.sp, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.background(Gen.Bg.copy(alpha = 0.78f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** F.1: the field chat, persistent, with the model's notes about the owner. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatSheet(vm: FieldViewModel, onClose: () -> Unit) {
    val chat by vm.chat.collectAsState()
    val busy by vm.chatBusy.collectAsState()
    val error by vm.chatError.collectAsState()
    val settings by vm.settings.collectAsState()
    val ranked by vm.suggestions.collectAsState()
    var input by remember { mutableStateOf("") }
    var showNotes by remember { mutableStateOf(false) }
    val blocker = remember(chat, busy) { vm.chatBlocker() }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(chat.turns.size, busy) { if (chat.turns.isNotEmpty()) listState.animateScrollToItem(chat.turns.size) }
    ModalBottomSheet(
        onDismissRequest = onClose, containerColor = Gen.Surface,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(horizontal = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Field chat", style = MaterialTheme.typography.titleMedium)
                    Text("${settings.provider.label} · ${settings.model} · sees your ~11 km area, never coordinates",
                        color = Gen.TextDim, fontSize = 11.sp)
                }
                TextButton(onClick = { showNotes = !showNotes }) { Text("Memory (${chat.notes.size})") }
                TextButton(onClick = { vm.clearChat() }) { Text("Clear", color = Gen.TextDim) }
            }
            if (showNotes) {
                Column(Modifier.fillMaxWidth().background(Gen.Bg, RoundedCornerShape(10.dp)).padding(8.dp)) {
                    if (chat.notes.isEmpty()) Text("Nothing kept yet. Tell it about your county, habits or goals and it will remember.",
                        color = Gen.TextDim, fontSize = 12.sp)
                    chat.notes.forEach { n ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("• $n", color = Gen.Text, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Text("✕", color = Gen.TextDim, modifier = Modifier.clickable { vm.forgetNote(n) }.padding(6.dp))
                        }
                    }
                }
            }
            if (blocker != null) Text(blocker, color = Gen.Amber, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (chat.turns.isEmpty()) item {
                    Text("Ask about where to look, what to check on arrival, the season and rules, or how to plan a walk. " +
                        "It reads the app's ranked places, your saved places and the season with each question.",
                        color = Gen.TextDim, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                }
                items(chat.turns) { t ->
                    val mine = t.role == com.ginsengo.steward.research.ChatTurn.USER
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                        androidx.compose.foundation.text.selection.SelectionContainer {
                            Text(t.text, color = Gen.Text, fontSize = 14.sp,
                                modifier = Modifier.widthIn(max = 320.dp)
                                    .background(if (mine) Gen.SurfaceHigh else Gen.Bg, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp))
                        }
                    }
                    // The places a reply names (#2, #5) become one tap from the map.
                    if (!mine) {
                        val named = Regex("#(\\d{1,2})").findAll(t.text).mapNotNull { m ->
                            ranked.firstOrNull { it.rank == m.groupValues[1].toInt() }
                        }.distinctBy { it.rank }.take(4).toList()
                        if (named.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            named.forEach { sg ->
                                TextButton(onClick = { vm.focusOn(sg); onClose() }) { Text("Show #${sg.rank}", fontSize = 12.sp) }
                                TextButton(onClick = { vm.goHere("#${sg.rank}", sg.lat, sg.lng); onClose() }) { Text("Go #${sg.rank}", fontSize = 12.sp) }
                            }
                        }
                    }
                }
                if (busy) item { Text("Thinking…", color = Gen.TextDim, fontSize = 12.sp) }
            }
            error?.let { Text(it, color = Gen.Danger, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp)) }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                com.ginsengo.steward.research.ChatPrompt.QUICK.forEach { q ->
                    FilterChip(selected = false, enabled = !busy, onClick = { vm.sendChat(q) }, label = { Text(q, fontSize = 12.sp) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                OutlinedTextField(input, { input = it }, placeholder = { Text("Ask anything about today's hunt") },
                    modifier = Modifier.weight(1f), maxLines = 4)
                Spacer(Modifier.width(6.dp))
                Button(onClick = { vm.sendChat(input); input = "" }, enabled = !busy && input.isNotBlank()) { Text(if (busy) "…" else "Send") }
            }
        }
    }
}

/** N.1: the owner's saved places, nearest first, each one a tap from guidance. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlacesSheet(vm: FieldViewModel, places: List<com.ginsengo.steward.field.SavedPlace>, me: com.ginsengo.steward.field.FieldLocation?, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Gen.Surface) {
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            item {
                Text("Places", style = MaterialTheme.typography.titleMedium)
                Text("Tap the ground and choose Save to keep a spot (the truck, a trailhead, a patch to check). " +
                    "Kept on this phone only.", color = Gen.TextDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                if (places.isEmpty()) Text("No saved places yet.", color = Gen.TextDim)
            }
            val sorted = if (me == null) places else places.sortedBy {
                com.ginsengo.steward.prospect.Prospects.distanceMetres(me.lat, me.lng, it.lat, it.lng)
            }
            items(sorted) { p ->
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(p.name, color = Gen.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    val where = me?.let {
                        val leg = com.ginsengo.steward.field.Guidance.leg(it.lat, it.lng, p.lat, p.lng)
                        com.ginsengo.steward.field.WayBack.describe(com.ginsengo.steward.field.WayBack.Leg(leg.distanceM, leg.bearingDeg)) + " · "
                    } ?: ""
                    Text(where + "%.5f, %.5f".format(java.util.Locale.US, p.lat, p.lng), color = Gen.TextDim, fontSize = 12.sp)
                    Row {
                        TextButton(onClick = { vm.goHere(p.name, p.lat, p.lng); onClose() }) { Text("Go here") }
                        TextButton(onClick = { vm.goTo(com.ginsengo.steward.ui.map.PlaceSearch.Place(p.name, p.lat, p.lng)); onClose() }) { Text("Show") }
                        TextButton(onClick = { vm.deletePlace(p) }) { Text("Delete", color = Gen.Danger) }
                    }
                }
            }
        }
    }
}

/** P.1: address, place or coordinate search, Google-Maps-style, over the map. */
@Composable
private fun SearchBar(searching: Boolean, active: Boolean, onSearch: (String) -> Unit, onClear: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    Row(
        Modifier.fillMaxWidth().background(Gen.Surface.copy(alpha = 0.95f), RoundedCornerShape(24.dp)).padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, null, tint = Gen.TextDim, modifier = Modifier.size(20.dp))
        androidx.compose.foundation.text.BasicTextField(
            value = q, onValueChange = { q = it }, singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = Gen.Text, fontSize = 15.sp),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Gen.Accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch(q); focus.clearFocus() }),
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 12.dp)
                .semantics { contentDescription = "Search address, place or coordinates" },
            decorationBox = { inner ->
                if (q.isEmpty()) Text("Search address, place or lat, lng", color = Gen.TextDim, fontSize = 15.sp)
                inner()
            },
        )
        if (searching) Text("…", color = Gen.TextDim, fontSize = 16.sp, modifier = Modifier.padding(8.dp))
        else if (q.isNotEmpty() || active) Text("✕", color = Gen.TextDim, fontSize = 16.sp,
            modifier = Modifier.clickable { q = ""; onClear(); focus.clearFocus() }.padding(8.dp))
    }
}

@Composable
private fun BarButton(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Column(
        Modifier.width(64.dp).clickable(onClick = onClick).padding(vertical = 6.dp)
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
                        TextButton(onClick = { vm.goHere("#${s.rank}", s.lat, s.lng); onClose() }) { Text("Go here") }
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
            // One row per switch in the scene description (SceneLayer.SHEET).
            for (layer in SceneLayer.SHEET) {
                val label = SceneLayer.label(layer) + if (layer == SceneLayer.HABITAT)
                    " (${if (verdict?.adopted == true) "learned weights" else "published weights"})" else ""
                Toggle(label, layer.shown(layers)) { vm.setLayers(layer.set!!(layers, it)) }
            }
            Spacer(Modifier.height(10.dp))
            Text("Map on the ground", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                com.ginsengo.steward.ui.map.Basemap.entries.forEach { b ->
                    FilterChip(selected = layers.basemap == b, onClick = { vm.setLayers(layers.copy(basemap = b)) }, label = { Text(b.label) })
                }
            }
            Text("Streets is saved for offline use with \"Save 10 miles\"; Satellite and Topo (USGS) need a connection.",
                color = Gen.TextDim, fontSize = 11.sp)
            Text("Relief", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                com.ginsengo.steward.ui.map.RELIEF_CHOICES.forEach { f ->
                    FilterChip(selected = layers.relief == f, onClick = { vm.setLayers(layers.copy(relief = f)) },
                        label = { Text(if (f == f.toInt().toFloat()) "${f.toInt()}×" else "$f×") })
                }
            }
            Text("Height exaggeration of the 3D ground; 1× is true scale. Changing it rebuilds the view.",
                color = Gen.TextDim, fontSize = 11.sp)
            Toggle("Legend on the map", layers.legend) { vm.setLayers(layers.copy(legend = it)) }
            val compassNow by vm.compass.collectAsState()
            Toggle("Compass heading on my position", compassNow) { vm.setCompass(it) }
            Text("Heat opacity", color = Gen.TextDim, fontSize = 12.sp)
            Slider(layers.heatmapOpacity, { vm.setLayers(layers.copy(heatmapOpacity = it)) }, valueRange = 0.2f..1f)
            Text("Green: terrain model (research-grade estimate; cannot see soil calcium or canopy). " +
                    "Blue lines: creeks and drains traced from elevation, not surveyed; small ones may be dry. " +
                    "Pale wash: where you've been, from every fix this phone received while the map was open " +
                    "or Track was recording (kept on this phone only). Amber: your finds.",
                color = Gen.TextDim, fontSize = 11.sp)

            Spacer(Modifier.height(14.dp))
            Text("Battery", style = MaterialTheme.typography.titleSmall)
            val saverPct by vm.saverPct.collectAsState()
            Text("Lighter 3D below $saverPct % (unplugged), or when the phone's battery saver is on: " +
                    "half the mesh, a smaller texture, no map on the ground, 20 frames a second.",
                color = Gen.TextDim, fontSize = 12.sp)
            Slider(saverPct.toFloat(), { vm.setSaverPct(it.roundToInt()) },
                valueRange = BatteryMode.THRESHOLD_RANGE.first.toFloat()..BatteryMode.THRESHOLD_RANGE.last.toFloat(), steps = 7)

            Spacer(Modifier.height(14.dp))
            // F.1: what the map is doing, to read out or paste into a report when something is blank.
            Text("Field diagnostics", style = MaterialTheme.typography.titleSmall)
            var diagTick by remember { mutableIntStateOf(0) }
            val diag = remember(diagTick) { com.ginsengo.steward.perf.FieldDiagnostics.report() }
            Text(diag, color = Gen.TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            val ctx = LocalContext.current
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            var testing by remember { mutableStateOf(false) }
            Row {
                TextButton(onClick = {
                    ctx.getSystemService(android.content.ClipboardManager::class.java)
                        ?.setPrimaryClip(android.content.ClipData.newPlainText("Gensingo diagnostics", diag))
                }) { Text("Copy diagnostics") }
                TextButton(enabled = !testing, onClick = {
                    testing = true
                    scope.launch {
                        com.ginsengo.steward.perf.FieldDiagnostics.mapSelfTest = com.ginsengo.steward.ui.map.MapDrape.selfTest(ctx)
                        testing = false; diagTick++
                    }
                }) { Text(if (testing) "Testing…" else "Test map rendering") }
            }

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

