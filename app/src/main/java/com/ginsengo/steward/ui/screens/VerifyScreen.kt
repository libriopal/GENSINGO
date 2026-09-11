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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ginsengo.steward.compliance.SeasonStatus
import com.ginsengo.steward.core.Provenance
import com.ginsengo.steward.ui.FieldViewModel
import com.ginsengo.steward.ui.components.ChoiceChip
import com.ginsengo.steward.ui.components.GenCard
import com.ginsengo.steward.ui.components.ProvenanceTag
import com.ginsengo.steward.ui.components.ScreenScaffold
import com.ginsengo.steward.ui.components.SectionHeader
import com.ginsengo.steward.ui.theme.Gen
import com.ginsengo.steward.verify.BerryState
import com.ginsengo.steward.verify.PlantVerification
import com.ginsengo.steward.verify.ProngCount
import com.ginsengo.steward.verify.Verdict
import com.ginsengo.steward.verify.VerificationInput

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VerifyScreen(vm: FieldViewModel, onBack: () -> Unit) {
    val compliance by vm.compliance.collectAsStateWithLifecycle()
    val state = compliance?.state

    var prongs by remember { mutableStateOf<ProngCount?>(null) }
    var berries by remember { mutableStateOf<BerryState?>(null) }
    var scarsText by remember { mutableStateOf("") }

    val input = VerificationInput(
        prongs = prongs,
        berries = berries,
        stemScars = scarsText.toIntOrNull(),
        stateMinProngs = state?.minimumProngs ?: 3,
        stateMinAgeYears = state?.minimumAgeYears ?: 5,
        stateName = state?.stateName,
        seasonOpen = compliance?.season?.let { it == SeasonStatus.OPEN },
    )
    val result = PlantVerification.evaluate(input)

    ScreenScaffold("Verify Maturity", onBack) {

        // Step 1 - prongs
        GenCard {
            SectionHeader(
                "1 · Count the prongs",
                subtitle = "A prong is one compound leaf coming off the main stem.",
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProngCount.entries.forEach { p ->
                    ChoiceChip(
                        p.label,
                        prongs == p,
                        { prongs = p },
                        accent = if (p.prongs >= (state?.minimumProngs ?: 3)) Gen.Primary else Gen.Alert,
                    )
                }
            }
            prongs?.let {
                Text(
                    it.guidance,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Gen.TextSecondary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        // Step 2 - berries
        GenCard {
            SectionHeader(
                "2 · Berries",
                subtitle = "A plant that hasn't seeded this year must be left standing.",
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BerryState.entries.forEach { b ->
                    ChoiceChip(
                        b.label,
                        berries == b,
                        { berries = b },
                        accent = if (b == BerryState.RED_BERRIES) Gen.Primary else Gen.Warning,
                    )
                }
            }
        }

        // Step 3 - stem scars (optional)
        GenCard {
            SectionHeader(
                "3 · Stem scars (optional)",
                subtitle = "Scars on the root neck. 4 scars ≈ 5 years. 9 scars ≈ 10 years.",
            )
            OutlinedTextField(
                value = scarsText,
                onValueChange = { s -> scarsText = s.filter { it.isDigit() }.take(2) },
                label = { Text("Scar count") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
            scarsText.toIntOrNull()?.let {
                Text(
                    "About ${PlantVerification.ageFromScars(it)} years old.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Gen.TextSecondary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        // Step 4 - verdict
        if (result == null) {
            GenCard {
                Text(
                    "Answer prongs and berries to get a verdict.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Gen.TextSecondary,
                )
            }
        } else {
            val accent = when (result.verdict) {
                Verdict.LEGAL -> Gen.Primary
                Verdict.CHECK_STATE -> Gen.Warning
                Verdict.TOO_YOUNG, Verdict.DO_NOT_HARVEST -> Gen.Alert
            }
            GenCard {
                Text(
                    result.headline,
                    style = MaterialTheme.typography.titleLarge,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                result.reasons.forEach {
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text("•  ", style = MaterialTheme.typography.bodyMedium, color = Gen.TextSecondary)
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = Gen.TextSecondary)
                    }
                }
                if (state == null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No approved state detected here, so this used the common 3-prong / " +
                                "5-year minimum. Set your state in Settings if you know better.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Gen.Warning,
                    )
                }
                Spacer(Modifier.height(10.dp))
                ProvenanceTag(
                    Provenance.VERIFIED,
                    detail = "Maturity rules: U.S. Fish & Wildlife Service American Ginseng " +
                            "export program.",
                )
            }

            // Stewardship reminders always show, including when the verdict is "leave it".
            GenCard {
                SectionHeader("Before you leave this plant")
                result.stewardshipReminders.forEach {
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Text("•  ", style = MaterialTheme.typography.bodyMedium, color = Gen.Primary)
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = Gen.TextPrimary)
                    }
                }
            }
        }

        // Look-alikes (PRD Workflow E item 6) - kept on this screen because this is where a
        // misidentification actually costs something.
        GenCard {
            SectionHeader("Not ginseng", subtitle = "Common look-alikes worth ruling out.")
            LookAlike("Virginia creeper", "Five leaflets from one point, and it climbs. Ginseng never climbs.")
            LookAlike("Dwarf ginseng", "Much smaller, leaflets stalkless, and the berry cluster is yellow, not red.")
            LookAlike("Wild sarsaparilla", "Three divisions of leaflets off a single stalk, no central berry cluster.")
        }
    }
}

@Composable
private fun LookAlike(name: String, tell: String) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(name, style = MaterialTheme.typography.titleMedium, color = Gen.Warning)
        Text(tell, style = MaterialTheme.typography.bodyMedium, color = Gen.TextSecondary)
    }
}
