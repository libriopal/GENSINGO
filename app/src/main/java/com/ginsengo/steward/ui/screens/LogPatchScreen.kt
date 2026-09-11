package com.ginsengo.steward.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ginsengo.steward.compliance.SeasonStatus
import com.ginsengo.steward.core.Provenance
import com.ginsengo.steward.data.db.GinsengPatch
import com.ginsengo.steward.ui.FieldViewModel
import com.ginsengo.steward.ui.components.ChoiceChip
import com.ginsengo.steward.ui.components.GenCard
import com.ginsengo.steward.ui.components.PrimaryAction
import com.ginsengo.steward.ui.components.ProvenanceTag
import com.ginsengo.steward.ui.components.ScreenScaffold
import com.ginsengo.steward.ui.components.SecondaryAction
import com.ginsengo.steward.ui.components.SectionHeader
import com.ginsengo.steward.ui.components.StatusPill
import com.ginsengo.steward.ui.theme.Gen
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun LogPatchScreen(
    vm: FieldViewModel,
    initialScore: Double?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val location by vm.location.collectAsStateWithLifecycle()
    val compliance by vm.compliance.collectAsStateWithLifecycle()

    var name by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var plantCountText by remember { mutableStateOf("") }
    var harvested by remember { mutableStateOf(false) }
    var rootsText by remember { mutableStateOf("") }
    var seedsText by remember { mutableStateOf("") }
    var manualLat by remember { mutableStateOf("") }
    var manualLng by remember { mutableStateOf("") }
    var photoFile by remember { mutableStateOf<File?>(null) }
    var showCamera by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> showCamera = granted }

    val lat = manualLat.toDoubleOrNull() ?: location?.lat
    val lng = manualLng.toDoubleOrNull() ?: location?.lng

    if (showCamera) {
        val target = remember { vm.container.photos.newPhotoFile() }
        Box(Modifier.fillMaxSize()) {
            CameraCapture(
                targetFile = target,
                onCaptured = { photoFile = it; showCamera = false },
                onCancel = { showCamera = false },
            )
        }
        return
    }

    ScreenScaffold("Log Patch", onBack) {

        // ---- Position ----
        GenCard {
            SectionHeader("Position", subtitle = "Captured from GPS. Override if you need to.")
            if (lat != null && lng != null) {
                Text(
                    "%.5f, %.5f".format(lat, lng),
                    style = MaterialTheme.typography.titleMedium,
                    color = Gen.TextPrimary,
                )
                location?.let {
                    Text(
                        "±${it.accuracyM.toInt()} m" +
                                (it.altitudeM?.let { a -> " · ${a.toInt()} m elevation" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = Gen.TextSecondary,
                    )
                }
            } else {
                Text(
                    "No GPS fix yet. Enter coordinates by hand or wait for a fix.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Gen.Warning,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GenField(manualLat, { manualLat = it }, "Lat override", Modifier.weight(1f), KeyboardType.Decimal)
                GenField(manualLng, { manualLng = it }, "Lng override", Modifier.weight(1f), KeyboardType.Decimal)
            }
        }

        // ---- Compliance warning, never a block ----
        compliance?.let { c ->
            if (c.season == SeasonStatus.CLOSED || c.isProhibitedLand || c.needsPermit) {
                GenCard {
                    if (c.season == SeasonStatus.CLOSED) {
                        Text(
                            "Season is closed here",
                            style = MaterialTheme.typography.titleMedium,
                            color = Gen.Warning,
                        )
                        Text(
                            c.seasonDetail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Gen.TextSecondary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    c.landStatuses.forEach { land ->
                        Text(
                            "${land.rule} · ${land.areaName}",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (land.rule == "PROHIBITED") Gen.Alert else Gen.Warning,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Text(
                            land.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Gen.TextSecondary,
                        )
                        Spacer(Modifier.height(6.dp))
                        ProvenanceTag(land.provenance)
                    }
                    Text(
                        "You can still record this patch — recording is not harvesting.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Gen.TextSecondary,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }

        // ---- Photo ----
        GenCard {
            SectionHeader("Photo", subtitle = "Kept in app-private storage. Never uploaded.")
            photoFile?.let { f ->
                AsyncImage(
                    model = f,
                    contentDescription = "Patch photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .clip(Gen.PanelShape),
                )
                Spacer(Modifier.height(8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryAction(
                    if (photoFile == null) "Take photo" else "Retake",
                    {
                        val ok = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (ok) showCamera = true else cameraPermission.launch(Manifest.permission.CAMERA)
                    },
                )
                if (photoFile != null) {
                    SecondaryAction("Remove", { photoFile?.delete(); photoFile = null }, accent = Gen.Alert)
                }
            }
        }

        // ---- Details ----
        GenCard {
            SectionHeader("Details")
            GenField(name, { name = it }, "Patch name", Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            GenField(plantCountText, { plantCountText = it.filter(Char::isDigit).take(4) },
                "Plants counted", Modifier.fillMaxWidth(), KeyboardType.Number)
            Spacer(Modifier.height(8.dp))
            GenField(notes, { notes = it }, "Notes", Modifier.fillMaxWidth(), singleLine = false)
        }

        // ---- Harvest ----
        GenCard {
            SectionHeader("Did you harvest here?")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("No", !harvested, { harvested = false })
                ChoiceChip("Yes", harvested, { harvested = true }, accent = Gen.Warning)
            }
            if (harvested) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GenField(rootsText, { rootsText = it.filter(Char::isDigit).take(3) },
                        "Roots taken", Modifier.weight(1f), KeyboardType.Number)
                    GenField(seedsText, { seedsText = it.filter(Char::isDigit).take(3) },
                        "Seeds replanted", Modifier.weight(1f), KeyboardType.Number)
                }
                val roots = rootsText.toIntOrNull() ?: 0
                val seeds = seedsText.toIntOrNull() ?: 0
                if (roots > 0 && seeds == 0) {
                    Text(
                        "Replant the berries before you leave. It is the whole difference " +
                                "between digging and stripping.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Gen.Warning,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        // ---- Habitat score carried over ----
        if (initialScore != null) {
            GenCard {
                SectionHeader("Habitat score")
                Text(
                    "%.2f".format(initialScore),
                    style = MaterialTheme.typography.displayLarge,
                    color = Gen.Warning,
                )
                Spacer(Modifier.height(8.dp))
                ProvenanceTag(
                    Provenance.RESEARCH_ESTIMATE,
                    detail = "Carried over from your habitat reading.",
                )
            }
        }

        // ---- Privacy indicator (PRD Workflow C step 9) ----
        GenCard {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                StatusPill("LOCAL ONLY", Gen.Primary)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Saved to this phone. No account, no server, no sync.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Gen.TextSecondary,
                )
            }
        }

        PrimaryAction(
            if (saving) "Saving…" else "Save patch",
            {
                if (lat == null || lng == null) return@PrimaryAction
                saving = true
                scope.launch {
                    val rel = photoFile?.let { vm.container.photos.relativePathOf(it) }
                    vm.container.database.patchDao().upsert(
                        GinsengPatch(
                            name = name.ifBlank { "Unnamed patch" },
                            lat = lat,
                            lng = lng,
                            photoPath = rel,
                            plantCount = plantCountText.toIntOrNull() ?: 0,
                            notes = notes,
                            habitatScore = initialScore,
                            harvested = harvested,
                            rootsHarvested = rootsText.toIntOrNull(),
                            seedsReplanted = seedsText.toIntOrNull(),
                            provenance = "prototype",
                        )
                    )
                    onSaved()
                }
            },
            Modifier.fillMaxWidth(),
            enabled = lat != null && lng != null && !saving,
        )
    }
}

@Composable
fun GenField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Gen.Primary,
            unfocusedBorderColor = Gen.Hairline,
            focusedTextColor = Gen.TextPrimary,
            unfocusedTextColor = Gen.TextPrimary,
            cursorColor = Gen.Primary,
            focusedLabelColor = Gen.Primary,
            unfocusedLabelColor = Gen.TextSecondary,
        ),
    )
}
