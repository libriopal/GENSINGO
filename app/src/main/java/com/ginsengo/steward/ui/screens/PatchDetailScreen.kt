package com.ginsengo.steward.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ginsengo.steward.core.Provenance
import com.ginsengo.steward.data.db.GinsengPatch
import com.ginsengo.steward.ui.FieldViewModel
import com.ginsengo.steward.ui.components.GenCard
import com.ginsengo.steward.ui.components.PrimaryAction
import com.ginsengo.steward.ui.components.ProvenanceTag
import com.ginsengo.steward.ui.components.ScreenScaffold
import com.ginsengo.steward.ui.components.SecondaryAction
import com.ginsengo.steward.ui.components.SectionHeader
import com.ginsengo.steward.ui.theme.Gen
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PatchDetailScreen(vm: FieldViewModel, patchId: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var patch by remember { mutableStateOf<GinsengPatch?>(null) }
    var name by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var plantCount by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var savedNote by remember { mutableStateOf(false) }

    LaunchedEffect(patchId) {
        val p = vm.container.database.patchDao().byId(patchId)
        patch = p
        name = p?.name.orEmpty()
        notes = p?.notes.orEmpty()
        plantCount = p?.plantCount?.toString().orEmpty()
    }

    val p = patch
    ScreenScaffold(p?.name ?: "Patch", onBack) {
        if (p == null) {
            GenCard {
                Text("Patch not found.", style = MaterialTheme.typography.bodyMedium, color = Gen.TextSecondary)
            }
            return@ScreenScaffold
        }

        vm.container.photos.resolve(p.photoPath)?.let { f ->
            AsyncImage(
                model = f,
                contentDescription = "Patch photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(Gen.PanelShape),
            )
        }

        GenCard {
            SectionHeader("Where")
            Text(
                "%.5f, %.5f".format(p.lat, p.lng),
                style = MaterialTheme.typography.titleMedium,
                color = Gen.Primary,
            )
            Text(
                "Last visited " + SimpleDateFormat("d MMM yyyy", Locale.US)
                    .format(Date(p.lastVisitedDate)),
                style = MaterialTheme.typography.labelSmall,
                color = Gen.TextSecondary,
            )
            p.habitatScore?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Habitat score %.2f".format(it),
                    style = MaterialTheme.typography.titleMedium,
                    color = Gen.Warning,
                )
                Spacer(Modifier.height(6.dp))
                ProvenanceTag(Provenance.RESEARCH_ESTIMATE)
            }
        }

        GenCard {
            SectionHeader("Edit")
            GenField(name, { name = it }, "Patch name", Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            GenField(plantCount, { plantCount = it.filter(Char::isDigit).take(4) },
                "Plants counted", Modifier.fillMaxWidth(), KeyboardType.Number)
            Spacer(Modifier.height(8.dp))
            GenField(notes, { notes = it }, "Notes", Modifier.fillMaxWidth(), singleLine = false)
            Spacer(Modifier.height(12.dp))
            PrimaryAction(
                if (savedNote) "Saved" else "Save changes",
                {
                    scope.launch {
                        vm.container.database.patchDao().update(
                            p.copy(
                                name = name.ifBlank { "Unnamed patch" },
                                notes = notes,
                                plantCount = plantCount.toIntOrNull() ?: 0,
                                lastVisitedDate = System.currentTimeMillis(),
                            )
                        )
                        savedNote = true
                    }
                },
                Modifier.fillMaxWidth(),
            )
        }

        GenCard {
            SectionHeader("Delete", subtitle = "This cannot be undone.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryAction(
                    if (confirmDelete) "Tap again to confirm" else "Delete patch",
                    {
                        if (!confirmDelete) confirmDelete = true
                        else scope.launch {
                            vm.container.photos.delete(p.photoPath)
                            vm.container.database.patchDao().delete(p)
                            onBack()
                        }
                    },
                    accent = Gen.Alert,
                )
            }
        }
    }
}
