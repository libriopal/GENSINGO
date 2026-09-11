package com.ginsengo.steward.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ginsengo.steward.core.Provenance
import com.ginsengo.steward.data.reference.StateRegulation
import com.ginsengo.steward.ui.FieldViewModel
import com.ginsengo.steward.ui.components.ChoiceChip
import com.ginsengo.steward.ui.components.GenCard
import com.ginsengo.steward.ui.components.ProvenanceTag
import com.ginsengo.steward.ui.components.ScreenScaffold
import com.ginsengo.steward.ui.components.SectionHeader
import com.ginsengo.steward.ui.components.StatusPill
import com.ginsengo.steward.ui.theme.Gen

private enum class GuideTab(val label: String) {
    RULES("Rules"), LAND("Land"), STEWARDSHIP("Stewardship"), PLANTS("Companions"), AGING("Aging")
}

@Composable
fun GuideScreen(vm: FieldViewModel, onBack: () -> Unit) {
    var tab by remember { mutableStateOf(GuideTab.RULES) }
    val reference = vm.container.reference

    ScreenScaffold("Stewardship Guide", onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            GuideTab.entries.forEach {
                ChoiceChip(it.label, tab == it, { tab = it })
            }
        }

        when (tab) {
            GuideTab.RULES -> {
                GenCard {
                    SectionHeader(
                        "Where harvest is legal",
                        subtitle = "19 states plus the Menominee Reservation. Nowhere else in the US.",
                    )
                    ProvenanceTag(
                        Provenance.VERIFIED,
                        detail = "U.S. Fish & Wildlife Service, American Ginseng export program.",
                    )
                }
                reference.states.forEach { StateCard(it) }
            }

            GuideTab.LAND -> {
                LandCard(
                    "National Parks",
                    "PROHIBITED",
                    Gen.Alert,
                    "Digging ginseng in a National Park is a federal offence, and it is " +
                            "prosecuted. Great Smoky Mountains runs patrols specifically for this.",
                )
                LandCard(
                    "National Wildlife Refuges",
                    "PROHIBITED",
                    Gen.Alert,
                    "Harvest on USFWS refuge land is prohibited.",
                )
                LandCard(
                    "Most state lands",
                    "USUALLY PROHIBITED",
                    Gen.Alert,
                    "State parks and most state forests close ginseng entirely. A few state " +
                            "forests permit it. Assume closed until the managing agency says otherwise.",
                )
                LandCard(
                    "National Forests",
                    "PERMIT REQUIRED",
                    Gen.Warning,
                    "Some national forests sell a limited number of ginseng permits each " +
                            "season, with their own rules on quantity and season. No permit means no digging.",
                )
                LandCard(
                    "Private land",
                    "PERMISSION REQUIRED",
                    Gen.Warning,
                    "Written permission from the landowner. Digging without it is theft in " +
                            "every one of the 19 states.",
                )
                GenCard {
                    Text(
                        "GENSINGO warns about land status but never blocks you, because the " +
                                "boundaries it carries are approximate and over-cover. A warning " +
                                "near an edge may be wrong; silence is not permission.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Gen.TextSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    ProvenanceTag(Provenance.APPROXIMATE)
                }
            }

            GuideTab.STEWARDSHIP -> {
                GenCard {
                    SectionHeader(
                        "Good stewardship",
                        subtitle = "A patch you look after outlives you. One you strip does not.",
                    )
                }
                STEWARDSHIP_PRACTICES.forEachIndexed { i, (title, body) ->
                    GenCard {
                        Row {
                            Text(
                                "${i + 1}",
                                style = MaterialTheme.typography.displayLarge,
                                color = Gen.Primary,
                            )
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Gen.TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    body,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Gen.TextSecondary,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
            }

            GuideTab.PLANTS -> {
                reference.companions.forEach { p ->
                    GenCard {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data("file:///android_asset/${p.assetPath}")
                                .crossfade(true)
                                .build(),
                            contentDescription = p.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 10f)
                                .clip(Gen.PanelShape),
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    p.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Gen.TextPrimary,
                                )
                                Text(
                                    p.scientificName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Gen.TextSecondary,
                                )
                            }
                            StatusPill(
                                p.indicatorStrength.uppercase(),
                                if (p.indicatorStrength == "strong") Gen.Primary else Gen.Warning,
                            )
                        }
                        Text(
                            p.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Gen.TextSecondary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        ProvenanceTag(
                            Provenance.VERIFIED,
                            detail = "${p.mediaType.replaceFirstChar { it.uppercase() }} · " +
                                    "${p.attribution} · Wikimedia Commons",
                        )
                    }
                }
            }

            GuideTab.AGING -> {
                GenCard {
                    SectionHeader("Counting prongs")
                    AgeRow("1 prong", "First year or two. Never legal.", Gen.Alert)
                    AgeRow("2 prongs", "Immature in every approved state.", Gen.Alert)
                    AgeRow("3 prongs", "Mature in 18 of the 19 states. Not Illinois.", Gen.Primary)
                    AgeRow("4+ prongs", "Mature everywhere harvest is legal.", Gen.Primary)
                }
                GenCard {
                    SectionHeader(
                        "Counting stem scars",
                        subtitle = "The root neck keeps one scar per year the plant came up.",
                    )
                    AgeRow("4 scars", "About 5 years old - the common legal minimum.", Gen.Primary)
                    AgeRow("9 scars", "About 10 years old - the Illinois minimum.", Gen.Primary)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Scars are the more reliable reading of the two: a plant can lose a " +
                                "prong to browse or weather and still be old enough.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Gen.TextSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                    ProvenanceTag(
                        Provenance.VERIFIED,
                        detail = "U.S. Fish & Wildlife Service, American Ginseng export program.",
                    )
                }
            }
        }
    }
}

@Composable
private fun StateCard(s: StateRegulation) {
    GenCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    s.stateName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Gen.TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Opens ${s.seasonStart ?: "September"} · closes ${s.seasonEndDisplay}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (s.seasonEndVerified) Gen.TextSecondary else Gen.Warning,
                )
            }
            StatusPill(
                "${s.minimumProngs} prong · ${s.minimumAgeYears} yr",
                if (s.minimumProngs > 3) Gen.Warning else Gen.Primary,
            )
        }
        if (s.harvestNotes.isNotBlank()) {
            Text(
                s.harvestNotes,
                style = MaterialTheme.typography.bodyMedium,
                color = Gen.TextSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        ProvenanceTag(
            s.provenance,
            detail = if (s.seasonEndVerified)
                "Season dates and maturity rule confirmed."
            else
                "Opening date and maturity rule are federal. The closing date is set by the " +
                        "state and is not published federally - confirm it with ${s.agency}.",
        )
    }
}

@Composable
private fun LandCard(title: String, rule: String, accent: androidx.compose.ui.graphics.Color, body: String) {
    GenCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = Gen.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            StatusPill(rule, accent)
        }
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = Gen.TextSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun AgeRow(label: String, body: String, accent: androidx.compose.ui.graphics.Color) {
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = accent,
            modifier = Modifier.width(96.dp),
        )
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Gen.TextSecondary)
    }
}

/**
 * Stewardship practices. The maturity, seed-planting and season elements follow FWS
 * guidance for the American Ginseng export program; the rest are long-standing digger
 * practice rather than federal rules, and are not labelled as regulation.
 */
private val STEWARDSHIP_PRACTICES = listOf(
    "Dig only mature plants" to
            "Three prongs and red berries at minimum - four in Illinois. A young plant dug is " +
            "a plant that never seeded.",
    "Plant the berries where you dug" to
            "Press each red berry about an inch into the soil within a few feet of the parent, " +
            "before you move on. This is the single practice that keeps a patch alive.",
    "Harvest only in season" to
            "Season opens in September everywhere it is legal. Before that the seed is not ripe, " +
            "and digging early destroys the year's reproduction.",
    "Leave the big ones" to
            "The largest plants produce the most seed. Taking them is the fastest way to empty " +
            "a hillside.",
    "Take a fraction, never the patch" to
            "Leave most of what you find. A patch worked lightly every few years outlasts one " +
            "cleaned out once.",
    "Fill your holes" to
            "Replace the soil and the leaf litter. An open hole dries out the roots around it.",
    "Do not sell or dig on someone else's ground" to
            "Written permission, every time. Digging without it is theft, not trespass.",
    "Keep the location to yourself" to
            "A patch is only as safe as the number of people who know where it is.",
    "Record what you took" to
            "Roots out, seeds back in. Honest records are how you find out whether your patch " +
            "is holding or shrinking.",
)
