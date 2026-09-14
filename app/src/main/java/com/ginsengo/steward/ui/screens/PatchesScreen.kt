package com.ginsengo.steward.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ginsengo.steward.core.Provenance
import com.ginsengo.steward.data.db.GinsengPatch
import com.ginsengo.steward.geo.GeoMath
import com.ginsengo.steward.geo.LatLng
import com.ginsengo.steward.ui.FieldViewModel
import com.ginsengo.steward.ui.components.ChoiceChip
import com.ginsengo.steward.ui.components.GenCard
import com.ginsengo.steward.ui.components.ProvenanceTag
import com.ginsengo.steward.ui.components.ScreenScaffold
import com.ginsengo.steward.ui.components.SectionHeader
import com.ginsengo.steward.ui.theme.Gen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class PatchFilter(val label: String) {
    ALL("All"), HARVESTED("Harvested"), UNHARVESTED("Not harvested"), SCORED("Has score")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PatchesScreen(vm: FieldViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val patches by vm.patches.collectAsStateWithLifecycle()
    val location by vm.location.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf(PatchFilter.ALL) }
    var revealed by remember { mutableStateOf(setOf<String>()) }

    val shown = patches.filter {
        when (filter) {
            PatchFilter.ALL -> true
            PatchFilter.HARVESTED -> it.harvested
            PatchFilter.UNHARVESTED -> !it.harvested
            PatchFilter.SCORED -> it.habitatScore != null
        }
    }

    // Proximity grouping (PRD Workflow D step 4): patches within 500 m of each other read
    // as one hillside, which is how a digger thinks about them.
    val groups = remember(shown) { groupByProximity(shown, 500.0) }

    ScreenScaffold("My Patches", onBack) {
        GenCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${patches.size} patch${if (patches.size == 1) "" else "es"}",
                        style = MaterialTheme.typography.titleLarge,
                        color = Gen.TextPrimary,
                    )
                    Text(
                        "On this phone only.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Gen.TextSecondary,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PatchFilter.entries.forEach {
                    ChoiceChip(it.label, filter == it, { filter = it })
                }
            }
        }

        if (shown.isEmpty()) {
            GenCard {
                Text(
                    if (patches.isEmpty()) "Nothing logged yet."
                    else "No patches match that filter.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Gen.TextSecondary,
                )
            }
        }

        groups.forEachIndexed { i, group ->
            if (groups.size > 1) {
                SectionHeader(
                    "Group ${i + 1}",
                    subtitle = "${group.size} patch${if (group.size == 1) "" else "es"} within 500 m",
                )
            }
            group.sortedByDescending { it.lastVisitedDate }.forEach { patch ->
                PatchCard(
                    patch = patch,
                    photoFile = vm.container.photos.resolve(patch.photoPath),
                    revealed = revealed.contains(patch.id),
                    distanceM = location?.let {
                        GeoMath.distanceMeters(LatLng(it.lat, it.lng), LatLng(patch.lat, patch.lng))
                    },
                    onToggleReveal = {
                        revealed = if (revealed.contains(patch.id)) revealed - patch.id
                        else revealed + patch.id
                    },
                    onOpen = { onOpen(patch.id) },
                )
            }
        }

        ProvenanceTag(
            Provenance.PROTOTYPE,
            detail = "Patch records are user-reported and unverified.",
        )
    }
}

@Composable
private fun PatchCard(
    patch: GinsengPatch,
    photoFile: java.io.File?,
    revealed: Boolean,
    distanceM: Double?,
    onToggleReveal: () -> Unit,
    onOpen: () -> Unit,
) {
    GenCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.Top) {
            if (photoFile != null) {
                AsyncImage(
                    model = photoFile,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp).clip(Gen.PanelShape),
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    patch.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Gen.TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    SimpleDateFormat("d MMM yyyy", Locale.US).format(Date(patch.lastVisitedDate)) +
                            " · ${patch.plantCount} plants" +
                            (distanceM?.let { " · ${formatDistance(it)} away" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = Gen.TextSecondary,
                )
            }
        }

        if (patch.notes.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(patch.notes, style = MaterialTheme.typography.bodyMedium, color = Gen.TextSecondary)
        }

        // PRD §8.3 / Workflow D step 2: coordinates blurred until explicitly tapped.
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(Gen.PillShape)
                .clickable(onClick = onToggleReveal)
                .padding(vertical = 6.dp)
        ) {
            // The coordinates are MASKED, not visually blurred. Modifier.blur is a no-op
            // below API 31 and this app ships to API 26, so a blur would have quietly
            // rendered honey-hole coordinates in plain text on older phones - which is
            // most of the phones actually carried into the woods. Masking the string
            // means the real value never reaches the composition until it is revealed.
            Text(
                if (revealed) "%.5f, %.5f".format(patch.lat, patch.lng) else MASKED_COORDS,
                style = MaterialTheme.typography.bodyMedium,
                color = if (revealed) Gen.Primary else Gen.TextSecondary,
            )
        }
        Text(
            if (revealed) "Tap to hide" else "Tap to reveal coordinates",
            style = MaterialTheme.typography.bodySmall,
            color = Gen.TextSecondary,
        )

        if (patch.harvested) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Harvested: ${patch.rootsHarvested ?: 0} roots · " +
                        "${patch.seedsReplanted ?: 0} seeds replanted",
                style = MaterialTheme.typography.bodyMedium,
                color = if ((patch.seedsReplanted ?: 0) > 0) Gen.Primary else Gen.Warning,
            )
        }
    }
}

private const val MASKED_COORDS = "\u2022\u2022.\u2022\u2022\u2022\u2022\u2022, \u2022\u2022.\u2022\u2022\u2022\u2022\u2022"

private fun formatDistance(m: Double): String =
    if (m < 1000) "${m.toInt()} m" else "%.1f km".format(m / 1000)

/**
 * Single-link clustering: a patch joins a group if it is within [radiusM] of ANY member,
 * so a ridge of patches strung along a hollow stays one group instead of splitting into
 * pairs the way a fixed-centre cluster would.
 */
private fun groupByProximity(patches: List<GinsengPatch>, radiusM: Double): List<List<GinsengPatch>> {
    val remaining = patches.toMutableList()
    val groups = mutableListOf<List<GinsengPatch>>()
    while (remaining.isNotEmpty()) {
        val seed = remaining.removeAt(0)
        val group = mutableListOf(seed)
        var grew = true
        while (grew) {
            grew = false
            val it = remaining.iterator()
            while (it.hasNext()) {
                val cand = it.next()
                val near = group.any { g ->
                    GeoMath.distanceMeters(LatLng(g.lat, g.lng), LatLng(cand.lat, cand.lng)) <= radiusM
                }
                if (near) {
                    group.add(cand); it.remove(); grew = true
                }
            }
        }
        groups.add(group)
    }
    return groups
}
