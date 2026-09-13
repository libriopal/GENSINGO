package com.ginsengo.steward.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.border
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ginsengo.steward.core.Provenance
import com.ginsengo.steward.data.db.HabitatReadingRecord
import com.ginsengo.steward.geo.GeoMath
import com.ginsengo.steward.habitat.ChecklistAnswers
import com.ginsengo.steward.habitat.HabitatChecklist
import com.ginsengo.steward.habitat.HabitatEngine
import com.ginsengo.steward.habitat.HabitatVerdict
import com.ginsengo.steward.habitat.InputOrigin
import com.ginsengo.steward.habitat.SlopePosition
import com.ginsengo.steward.habitat.TreeSpecies
import com.ginsengo.steward.ui.FieldViewModel
import com.ginsengo.steward.ui.components.ChoiceChip
import com.ginsengo.steward.ui.components.GenCard
import com.ginsengo.steward.ui.components.HeroMetric
import com.ginsengo.steward.ui.components.PrimaryAction
import com.ginsengo.steward.ui.components.ProvenanceTag
import com.ginsengo.steward.ui.components.ScreenScaffold
import com.ginsengo.steward.ui.components.SecondaryAction
import com.ginsengo.steward.ui.components.SectionHeader
import com.ginsengo.steward.ui.theme.Gen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HabitatReadingScreen(
    vm: FieldViewModel,
    onBack: () -> Unit,
    onLogPatch: (Double?) -> Unit,
) {
    val location by vm.location.collectAsStateWithLifecycle()
    val bearing by vm.bearing.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val companions = remember { vm.container.reference.companions }

    var slopePosition by remember { mutableStateOf<SlopePosition?>(null) }
    var trees by remember { mutableStateOf(setOf<TreeSpecies>()) }
    var seen by remember { mutableStateOf(setOf<String>()) }
    var soilOk by remember { mutableStateOf<Boolean?>(null) }
    var lockedAspect by remember { mutableStateOf<Double?>(null) }
    var analysis by remember { mutableStateOf<HabitatEngine.Run?>(null) }
    var saved by remember { mutableStateOf(false) }

    val aspect = lockedAspect ?: bearing?.toDouble()

    val answers = ChecklistAnswers(
        aspectDegrees = aspect,
        slopePosition = slopePosition,
        treesPresent = trees,
        companionsSeen = seen,
        totalCompanionsOffered = companions.size.coerceAtLeast(1),
        soilDeepDarkLoose = soilOk,
    )
    val result = HabitatChecklist.evaluate(answers)

    ScreenScaffold("Read Habitat", onBack) {

        // ---------------- Slope orientation ----------------
        GenCard {
            SectionHeader(
                "Which way does the slope face?",
                subtitle = "Point the top of the phone straight downhill.",
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val deg = aspect
                HeroMetric(
                    value = if (deg == null) "--" else GeoMath.compassName(deg.toFloat()),
                    caption = if (deg == null) "no compass" else "${deg.roundToInt()}°",
                    accent = when {
                        deg == null -> Gen.TextSecondary
                        GeoMath.aspectFavourability(deg) >= 0.66 -> Gen.Primary
                        GeoMath.aspectFavourability(deg) >= 0.33 -> Gen.Warning
                        else -> Gen.Alert
                    },
                    modifier = Modifier.weight(1f),
                )
                SecondaryAction(
                    if (lockedAspect == null) "Lock reading" else "Unlock",
                    { lockedAspect = if (lockedAspect == null) bearing?.toDouble() else null },
                    accent = if (lockedAspect == null) Gen.Primary else Gen.Warning,
                )
            }
            Text(
                "North and east faces stay cool and damp. South and west bake.",
                style = MaterialTheme.typography.bodySmall,
                color = Gen.TextSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        // ---------------- Position on slope ----------------
        GenCard {
            SectionHeader("Where are you on the slope?")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SlopePosition.entries.forEach { pos ->
                    ChoiceChip(pos.label, slopePosition == pos, { slopePosition = pos })
                }
            }
            slopePosition?.let {
                Text(
                    it.hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = Gen.TextSecondary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        // ---------------- Trees ----------------
        GenCard {
            SectionHeader("What's growing overhead?", subtitle = "Tap everything you can see.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TreeSpecies.entries.forEach { t ->
                    ChoiceChip(
                        t.label,
                        trees.contains(t),
                        { trees = if (trees.contains(t)) trees - t else trees + t },
                        accent = if (t.favourable) Gen.Primary else Gen.Warning,
                    )
                }
            }
            if (answers.hasOnlyDrySiteTrees) {
                Text(
                    "Only dry-site timber here. Ginseng is unlikely under pure oak, hickory, " +
                            "cedar or pine.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Gen.Warning,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        // ---------------- Companion plants ----------------
        GenCard {
            SectionHeader(
                "Companion plants",
                subtitle = "The most reliable sign there is. Tap what you've actually found.",
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(companions.size) { i ->
                    val p = companions[i]
                    val isSeen = seen.contains(p.slug)
                    Column(
                        Modifier
                            .width(116.dp)
                            .clickable {
                                seen = if (isSeen) seen - p.slug else seen + p.slug
                            }
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(Gen.PanelShape)
                                .border(
                                    if (isSeen) 2.dp else 1.dp,
                                    if (isSeen) Gen.Primary else Gen.Hairline,
                                    Gen.PanelShape,
                                )
                                .background(Gen.Base)
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                    .data("file:///android_asset/${p.assetPath}")
                                    .crossfade(true)
                                    .build(),
                                contentDescription = p.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(Gen.PanelShape),
                            )
                            if (isSeen) {
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(14.dp)
                                        .clip(Gen.PillShape)
                                        .background(Gen.Primary)
                                )
                            }
                        }
                        Text(
                            p.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSeen) Gen.Primary else Gen.TextPrimary,
                            fontWeight = if (isSeen) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Text(
                            p.indicatorStrength,
                            style = MaterialTheme.typography.bodySmall,
                            color = Gen.TextSecondary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            ProvenanceTag(
                Provenance.VERIFIED,
                detail = "Botanical reference photographs from Wikimedia Commons.",
            )
        }

        // ---------------- Soil ----------------
        GenCard {
            SectionHeader(
                "Soil check",
                subtitle = "Deep, dark, loose, under a good layer of leaf litter?",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("Yes", soilOk == true, { soilOk = true })
                ChoiceChip("No", soilOk == false, { soilOk = false }, accent = Gen.Warning)
            }
        }

        // ---------------- Field verdict ----------------
        val verdictAccent = when (result.verdict) {
            HabitatVerdict.STRONG -> Gen.Primary
            HabitatVerdict.MIXED -> Gen.Warning
            HabitatVerdict.UNLIKELY -> Gen.Alert
        }
        GenCard {
            SectionHeader("Field reading")
            HeroMetric(
                "${(result.score01 * 100).roundToInt()}",
                "of 100 from your checklist",
                verdictAccent,
            )
            Text(
                result.verdict.label,
                style = MaterialTheme.typography.titleLarge,
                color = verdictAccent,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                result.verdict.blurb,
                style = MaterialTheme.typography.bodyMedium,
                color = Gen.TextSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
            )
            result.reasons.forEach { Bullet(it, Gen.TextSecondary) }
            result.warnings.forEach { Bullet(it, Gen.Warning) }
        }

        // ---------------- Optional model analysis ----------------
        val engine = vm.container.habitat
        GenCard {
            SectionHeader(
                "Habitat analysis (optional)",
                subtitle = "Runs the bundled model on this spot.",
            )
            if (engine == null) {
                Text(
                    "The habitat model could not be loaded on this device, so analysis is " +
                            "unavailable. Your field reading above is unaffected.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Gen.Warning,
                )
            } else {
                PrimaryAction(
                    if (analysis == null) "Run Habitat Analysis" else "Re-run analysis",
                    {
                        val loc = location
                        scope.launch {
                            val (elev, measured) = loc?.let { vm.elevationFor(it) } ?: (null to false)
                            val sa = loc?.let { vm.slopeAspectFor(it) }
                            val features = HabitatChecklist.deriveFeatures(
                                lat = loc?.lat ?: 0.0,
                                lng = loc?.lng ?: 0.0,
                                elevationM = elev,
                                elevationMeasured = measured,
                                slopeDegrees = sa?.slopeDegrees,
                                answers = answers,
                            )
                            analysis = withContext(Dispatchers.Default) { engine.analyse(features) }
                        }
                    },
                    enabled = location != null,
                )
                if (location == null) {
                    Text(
                        "Waiting for a GPS fix.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Gen.TextSecondary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                analysis?.let { run -> ModelBreakdown(run, engine) }
            }
        }

        // ---------------- Save / continue ----------------
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SecondaryAction(
                if (saved) "Saved" else "Save reading",
                {
                    val loc = location
                    scope.launch {
                        vm.container.database.habitatReadingDao().insert(
                            HabitatReadingRecord(
                                patchId = null,
                                lat = loc?.lat ?: 0.0,
                                lng = loc?.lng ?: 0.0,
                                slopeOrientation = aspect?.let { GeoMath.compassName(it.toFloat()) } ?: "unknown",
                                slopePosition = slopePosition?.name?.lowercase() ?: "unknown",
                                treesPresent = trees.map { it.label },
                                companionPlantsSeen = seen.toList(),
                                soilCheck = soilOk == true,
                                result = result.verdict.name.lowercase(),
                                modelScore = analysis?.analysis?.score,
                                modelShareFromAnswers = analysis?.analysis?.shareFromYourAnswers,
                            )
                        )
                        saved = true
                    }
                },
                Modifier.weight(1f),
                enabled = !saved,
            )
            PrimaryAction(
                "Log a patch here",
                { onLogPatch(analysis?.analysis?.score ?: result.score01) },
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ModelBreakdown(run: HabitatEngine.Run, engine: HabitatEngine) {
    val a = run.analysis
    Spacer(Modifier.height(14.dp))
    HeroMetric("%.2f".format(a.score), "model suitability, 0 to 1", Gen.Warning)

    Spacer(Modifier.height(10.dp))
    ProvenanceTag(
        Provenance.RESEARCH_ESTIMATE,
        detail = "Derived from a 235-byte linear baseline graph. Not a scientifically " +
                "validated ecological model, and not a field guarantee.",
    )

    // The honesty panel. This is the finding that the EINCOL pass turned up, surfaced
    // where it actually matters - next to the number a digger might otherwise read as a
    // second opinion corroborating their own checklist.
    Spacer(Modifier.height(14.dp))
    Text("What moved this number", style = MaterialTheme.typography.titleMedium, color = Gen.TextPrimary)
    Spacer(Modifier.height(6.dp))

    val active = a.contributions.filterNot { it.isInert }
    val maxPush = active.maxOfOrNull { kotlin.math.abs(it.logitPush) }?.coerceAtLeast(1e-6) ?: 1.0
    active.sortedByDescending { kotlin.math.abs(it.logitPush) }.forEach { c ->
        Column(Modifier.padding(vertical = 4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    c.feature.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Gen.TextPrimary,
                )
                Text(
                    c.origin.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (c.origin == InputOrigin.MEASURED) Gen.Primary else Gen.Warning,
                )
            }
            LinearProgressIndicator(
                progress = { (kotlin.math.abs(c.logitPush) / maxPush).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(4.dp).padding(top = 4.dp),
                color = if (c.origin == InputOrigin.MEASURED) Gen.Primary else Gen.Warning,
                trackColor = Gen.Hairline,
            )
        }
    }

    val share = (a.shareFromYourAnswers * 100).roundToInt()
    Spacer(Modifier.height(10.dp))
    Text(
        "$share% of this score comes from answers you just gave. The model is mostly " +
                "restating your own checklist, not checking it.",
        style = MaterialTheme.typography.bodyMedium,
        color = Gen.Warning,
    )

    val inert = engine.inertFeatures()
    if (inert.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Text(
            "Ignored entirely by this model: " +
                    inert.joinToString(", ") { it.displayName.lowercase() } +
                    ". The bundled graph weights them at zero, so they cannot change the " +
                    "score no matter what you enter. Your field reading above does use them.",
            style = MaterialTheme.typography.bodySmall,
            color = Gen.TextSecondary,
        )
    }

    // A "cross-checked: both engines agree" line used to sit here. It was removed rather
    // than repaired. The second engine ran the same graph over the same inputs, and that
    // graph restates the digger's own checklist answers - so the agreement was arithmetic,
    // not corroboration, and showing it invited exactly the confidence it did not earn.
}

@Composable
private fun Bullet(text: String, color: androidx.compose.ui.graphics.Color) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text("•  ", style = MaterialTheme.typography.bodyMedium, color = color)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}
