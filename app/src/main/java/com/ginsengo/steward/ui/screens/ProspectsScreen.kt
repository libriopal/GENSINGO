package com.ginsengo.steward.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ginsengo.steward.prospect.ProspectSite
import com.ginsengo.steward.prospect.Prospects
import com.ginsengo.steward.ui.FieldViewModel
import com.ginsengo.steward.ui.components.GenCard
import com.ginsengo.steward.ui.components.ScreenScaffold
import com.ginsengo.steward.ui.theme.Gen
import kotlin.math.roundToInt

/**
 * Ranked habitat near you, offline.
 *
 * Sorted by distance rather than by score, because standing on a hillside the useful question is
 * "what is close" and not "what is theoretically best in the county". The score is still shown,
 * and the reasons are read back from the recorded facts rather than restating the number.
 */
@Composable
fun ProspectsScreen(vm: FieldViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val set = remember { Prospects.load(context) }
    val location by vm.location.collectAsState()

    ScreenScaffold("Nearby habitat", onBack) {
        if (set == null) {
            GenCard {
                Text("No bundled sites", style = MaterialTheme.typography.titleMedium,
                    color = Gen.Warning)
                Text(
                    "This build carries no pre-computed habitat. Generate it with " +
                        "tools/nc_heatmap.py and rebuild.",
                    style = MaterialTheme.typography.bodyMedium, color = Gen.TextSecondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            return@ScreenScaffold
        }

        GenCard {
            Text(
                "${set.sites.size} sites, ${set.county} County",
                style = MaterialTheme.typography.titleMedium, color = Gen.TextPrimary,
            )
            Text(
                set.source,
                style = MaterialTheme.typography.bodySmall, color = Gen.TextSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                set.caveat,
                style = MaterialTheme.typography.bodySmall, color = Gen.Warning,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Spacer(Modifier.height(12.dp))

        val fix = location
        if (fix == null) {
            Text(
                "No position yet — showing the highest-scoring sites instead of the nearest.",
                style = MaterialTheme.typography.bodyMedium, color = Gen.Warning,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            set.sites.take(20).forEach { SiteCard(it, null, null) }
        } else {
            Prospects.nearest(set.sites, fix.lat, fix.lng, limit = 20).forEach { (site, metres) ->
                SiteCard(site, metres, Prospects.bearingTrue(fix.lat, fix.lng, site.lat, site.lon))
            }
        }
    }
}

@Composable
private fun SiteCard(site: ProspectSite, metres: Double?, bearingTrue: Double?) {
    GenCard(Modifier.padding(bottom = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "%.2f".format(site.score),
                fontFamily = FontFamily.Monospace, fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (site.score >= 0.85) Gen.Primary else Gen.TextPrimary,
            )
            Spacer(Modifier.height(0.dp))
            Text(
                "   ${site.series ?: "?"} · ${site.landform ?: "?"}",
                style = MaterialTheme.typography.titleMedium, color = Gen.TextPrimary,
            )
        }

        if (metres != null) {
            val km = metres / 1000.0
            val dist = if (metres < 1000) "${metres.roundToInt()} m" else "%.1f km".format(km)
            Text(
                "$dist away, bearing ${bearingTrue?.roundToInt() ?: 0}° true",
                style = MaterialTheme.typography.titleMedium, color = Gen.Primary,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                "True north, not magnetic — your compass reads several degrees off here, and a " +
                    "mattock or a truck will swing it further.",
                style = MaterialTheme.typography.bodySmall, color = Gen.TextSecondary,
            )
        }

        Text(
            "%.6f, %.6f".format(site.lat, site.lon),
            fontFamily = FontFamily.Monospace, fontSize = 15.sp,
            color = Gen.TextPrimary, modifier = Modifier.padding(top = 6.dp),
        )

        site.why().forEach {
            Text(
                "· $it",
                style = MaterialTheme.typography.bodyMedium, color = Gen.TextSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
