package com.ginsengo.steward.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ginsengo.steward.BuildConfig
import com.ginsengo.steward.core.Provenance
import com.ginsengo.steward.habitat.HabitatFeature
import com.ginsengo.steward.ui.FieldViewModel
import com.ginsengo.steward.ui.components.ChoiceChip
import com.ginsengo.steward.ui.components.GenCard
import com.ginsengo.steward.ui.components.ProvenanceTag
import com.ginsengo.steward.ui.components.ScreenScaffold
import com.ginsengo.steward.ui.components.SecondaryAction
import com.ginsengo.steward.ui.components.SectionHeader
import com.ginsengo.steward.ui.components.StatusPill
import com.ginsengo.steward.ui.theme.Gen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: FieldViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = vm.container.settings
    val patches by vm.patches.collectAsStateWithLifecycle()
    val readings by vm.readings.collectAsStateWithLifecycle()

    var manualState by remember { mutableStateOf(settings.manualStateCode) }
    var exportMsg by remember { mutableStateOf<String?>(null) }

    ScreenScaffold("Settings", onBack) {

        // ---- Privacy ----
        GenCard {
            SectionHeader("Privacy")
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill("LOCAL ONLY", Gen.Primary)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Patch locations, photos and readings live in this app's private storage on " +
                        "this phone. There is no account, no server and no analytics. The only " +
                        "thing GENSINGO sends over the network is a request for map tiles, and " +
                        "that carries no patch data.",
                style = MaterialTheme.typography.bodyMedium,
                color = Gen.TextSecondary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Android's cloud backup is switched off for this app, so your patches do not " +
                        "ride along to Google Drive.",
                style = MaterialTheme.typography.bodyMedium,
                color = Gen.TextSecondary,
            )
        }

        // ---- State selection ----
        GenCard {
            SectionHeader(
                "Your state",
                subtitle = "Detected from GPS offline. Override it if detection is wrong.",
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChoiceChip("Auto", manualState == null, {
                    manualState = null
                    settings.manualStateCode = null
                    vm.refreshCompliance()
                })
                vm.container.reference.states.forEach { s ->
                    ChoiceChip(s.stateCode, manualState == s.stateCode, {
                        manualState = s.stateCode
                        settings.manualStateCode = s.stateCode
                        vm.refreshCompliance()
                    })
                }
            }
        }

        // ---- Export ----
        GenCard {
            SectionHeader(
                "Export your data",
                subtitle = "${patches.size} patches · ${readings.size} habitat readings",
            )
            Text(
                "Writes a CSV into app-private storage and hands it to one app of your " +
                        "choosing through a one-time grant. Nothing is uploaded on your behalf.",
                style = MaterialTheme.typography.bodyMedium,
                color = Gen.TextSecondary,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            SecondaryAction("Export patches as CSV", {
                scope.launch {
                    val file = withContext(Dispatchers.IO) {
                        val dir = File(context.filesDir, "exports").apply { mkdirs() }
                        val f = File(dir, "gensingo-patches.csv")
                        f.printWriter().use { out ->
                            out.println("id,name,lat,lng,plant_count,habitat_score,harvested," +
                                    "roots_harvested,seeds_replanted,last_visited,notes")
                            patches.forEach { p ->
                                out.println(
                                    listOf(
                                        p.id, csv(p.name), p.lat, p.lng, p.plantCount,
                                        p.habitatScore ?: "", p.harvested,
                                        p.rootsHarvested ?: "", p.seedsReplanted ?: "",
                                        p.lastVisitedDate, csv(p.notes),
                                    ).joinToString(",")
                                )
                            }
                        }
                        f
                    }
                    exportMsg = runCatching {
                        val uri = FileProvider.getUriForFile(
                            context, "${context.packageName}.fileprovider", file
                        )
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(
                            android.content.Intent.createChooser(send, "Export patches")
                        )
                        "Exported ${patches.size} patches."
                    }.getOrElse { "Export written to app storage, but no app could receive it." }
                }
            })
            exportMsg?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Gen.Primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        // ---- Model transparency ----
        GenCard {
            SectionHeader("Habitat model")
            val engine = vm.container.habitat
            if (engine == null) {
                Text(
                    "Model not loaded on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Gen.Warning,
                )
            } else {
                Text(
                    engine.loadNote,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Gen.TextSecondary,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Feature weights in the shipped graph",
                    style = MaterialTheme.typography.titleMedium,
                    color = Gen.TextPrimary,
                )
                Spacer(Modifier.height(6.dp))
                HabitatFeature.entries.forEach { f ->
                    val w = engine.weightOf(f)
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(
                            f.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (w == 0.0) Gen.TextSecondary else Gen.TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            if (w == 0.0) "0 · ignored" else "%.4f".format(w),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (w == 0.0) Gen.Alert else Gen.Primary,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Slope angle and aspect carry a weight of exactly zero in this graph, so " +
                            "the analysis cannot respond to them. The field checklist does use " +
                            "them, and it is the reading to trust on a hillside.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Gen.Warning,
                )
            }
            Spacer(Modifier.height(10.dp))
            ProvenanceTag(
                Provenance.RESEARCH_ESTIMATE,
                detail = "235-byte linear baseline. Not a validated ecological model.",
            )
        }

        // ---- Data sources ----
        GenCard {
            SectionHeader("Where the data comes from")
            Source("Harvest rules, maturity minimums, approved states",
                "U.S. Fish & Wildlife Service, American Ginseng export program", Provenance.VERIFIED)
            Source("Companion plant photographs",
                "Wikimedia Commons, public domain or Creative Commons, attributed per photo",
                Provenance.VERIFIED)
            Source("State outlines",
                "Public-domain generalised US state boundaries", Provenance.VERIFIED)
            Source("Elevation grid",
                "NASA SRTM 90 m, sampled once at build time to a 0.1° grid. Never fetched at runtime.",
                Provenance.VERIFIED)
            Source("Protected area boundaries",
                "Approximate bounding polygons. They over-cover and may warn outside the real unit.",
                Provenance.APPROXIMATE)
            Source("Habitat suitability score",
                "235-byte linear baseline graph bundled with the app.", Provenance.RESEARCH_ESTIMATE)
            Source("Your patches and readings",
                "Entered by you. Unverified by anyone.", Provenance.PROTOTYPE)
        }

        // ---- About ----
        GenCard {
            SectionHeader("About")
            Text(
                "GENSINGO ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.titleMedium,
                color = Gen.TextPrimary,
            )
            Text(
                "A field companion for wild American ginseng stewards. It assists " +
                        "identification and record-keeping; it does not decide. Verify maturity " +
                        "and land status yourself, and check your state's rules before you dig.\n\n" +
                        "American ginseng is protected under CITES Appendix II. Harvest is legal " +
                        "in 19 states and on the Menominee Reservation, in season, on ground you " +
                        "have the right to dig.",
                style = MaterialTheme.typography.bodyMedium,
                color = Gen.TextSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun Source(what: String, where: String, provenance: Provenance) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(what, style = MaterialTheme.typography.titleMedium, color = Gen.TextPrimary)
        Text(
            where,
            style = MaterialTheme.typography.bodyMedium,
            color = Gen.TextSecondary,
            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
        )
        ProvenanceTag(provenance, detail = "")
    }
}

private fun csv(s: String) = "\"" + s.replace("\"", "\"\"").replace("\n", " ") + "\""
