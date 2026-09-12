package com.ginsengo.steward.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ginsengo.steward.field.EmergencyPosition
import com.ginsengo.steward.ui.FieldViewModel
import com.ginsengo.steward.ui.components.GenCard
import com.ginsengo.steward.ui.components.ScreenScaffold
import com.ginsengo.steward.ui.components.PrimaryAction
import com.ginsengo.steward.ui.components.SecondaryAction
import com.ginsengo.steward.ui.theme.Gen
import kotlinx.coroutines.delay

/**
 * Where you are, in a form somebody else can act on.
 *
 * This screen assumes the worst reading conditions the app will ever face: one hand, poor light
 * or harsh light, and someone who is hurt or frightened reading numbers aloud over a bad
 * connection. So the coordinates are large and monospaced, both formats are always present, and
 * the fix's age and accuracy are never hidden behind a tap.
 */
@Composable
fun PositionScreen(vm: FieldViewModel, onBack: () -> Unit) {
    val location by vm.location.collectAsState()
    val context = LocalContext.current

    // The age of a fix changes while you look at it, so the clock has to tick.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    ScreenScaffold("Position", onBack) {
        val fix = location
        if (fix == null) {
            GenCard {
                Text(
                    "No position yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = Gen.Warning,
                )
                Text(
                    "The app has no fix. Under canopy this can take several minutes, and on a " +
                    "steep north slope it can fail entirely. Move to open sky if you can. If " +
                    "you are hurt and cannot move, call 911 and describe the terrain, the " +
                    "drainage you are in, and the last road or trail you crossed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Gen.TextSecondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            return@ScreenScaffold
        }

        val stale = EmergencyPosition.isStale(fix.timestamp, now)
        val poor = EmergencyPosition.isPoor(fix.accuracyM)

        GenCard {
            Text("READ THIS OUT", style = MaterialTheme.typography.labelLarge, color = Gen.Primary)
            Spacer(Modifier.height(10.dp))

            Text(
                EmergencyPosition.degreesDecimalMinutes(fix.lat, fix.lng),
                fontFamily = FontFamily.Monospace,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Gen.TextPrimary,
            )
            Text(
                "degrees and decimal minutes - what most dispatchers expect",
                style = MaterialTheme.typography.bodySmall,
                color = Gen.TextSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )

            Spacer(Modifier.height(12.dp))
            Text(
                EmergencyPosition.decimalDegrees(fix.lat, fix.lng),
                fontFamily = FontFamily.Monospace,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Gen.TextPrimary,
            )
            Text(
                "decimal degrees",
                style = MaterialTheme.typography.bodySmall,
                color = Gen.TextSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        Spacer(Modifier.height(12.dp))

        GenCard {
            Text(
                "Accuracy +/- ${fix.accuracyM.toInt()} m" + if (poor) "   POOR" else "",
                style = MaterialTheme.typography.titleMedium,
                color = if (poor) Gen.Warning else Gen.TextPrimary,
            )
            Text(
                "Fix ${EmergencyPosition.fixAge(fix.timestamp, now)}" +
                    if (stale) "   THIS POSITION IS OLD" else "",
                style = MaterialTheme.typography.titleMedium,
                color = if (stale) Gen.Alert else Gen.TextPrimary,
                modifier = Modifier.padding(top = 4.dp),
            )
            fix.altitudeM?.let {
                Text(
                    "Elevation ${it.toInt()} m",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Gen.TextSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (stale) {
                Text(
                    "An old fix shown as current is how people get sent to the wrong ridge. " +
                    "Move to open sky and wait for this to refresh before relying on it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gen.TextSecondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        PrimaryAction(
            "Send position by text",
            {
                val body = EmergencyPosition.emergencyMessage(
                    lat = fix.lat, lng = fix.lng,
                    accuracyM = fix.accuracyM, altitudeM = fix.altitudeM,
                    fixTimeMs = fix.timestamp, nowMs = System.currentTimeMillis(),
                )
                // SMS rather than anything network-backed: a text needs one brief moment of
                // marginal signal and keeps retrying. It opens the composer rather than
                // sending, so the user picks who gets it and can add a line.
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).apply {
                            putExtra("sms_body", body)
                        }
                    )
                }.onFailure {
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, body)
                            },
                            "Send position",
                        )
                    )
                }
            },
            Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))

        SecondaryAction(
            "Copy for any other app",
            {
                val body = EmergencyPosition.emergencyMessage(
                    lat = fix.lat, lng = fix.lng,
                    accuracyM = fix.accuracyM, altitudeM = fix.altitudeM,
                    fixTimeMs = fix.timestamp, nowMs = System.currentTimeMillis(),
                )
                context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, body)
                        },
                        "Send position",
                    )
                )
            },
            Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        GenCard {
            Text("Compass", style = MaterialTheme.typography.titleMedium, color = Gen.TextPrimary)
            Text(
                EmergencyPosition.DECLINATION_CAUTION,
                style = MaterialTheme.typography.bodyMedium,
                color = Gen.TextSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
