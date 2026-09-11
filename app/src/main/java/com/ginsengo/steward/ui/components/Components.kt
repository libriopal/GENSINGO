package com.ginsengo.steward.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ginsengo.steward.core.Provenance
import com.ginsengo.steward.ui.theme.Gen

/**
 * PRD §5.4 - the provenance tag strip. Every data surface in the app carries one.
 * The colour encodes how much weight the reader should put on the number next to it.
 */
@Composable
fun ProvenanceTag(
    provenance: Provenance,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    val accent = when (provenance) {
        Provenance.VERIFIED -> Gen.Primary
        Provenance.RESEARCH_ESTIMATE -> Gen.Warning
        Provenance.PROTOTYPE -> Gen.Warning
        Provenance.APPROXIMATE -> Gen.Warning
    }
    Column(modifier) {
        Box(
            Modifier
                .clip(Gen.PillShape)
                .background(accent.copy(alpha = 0.12f))
                .border(1.dp, accent.copy(alpha = 0.45f), Gen.PillShape)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                provenance.label,
                color = accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
            )
        }
        val text = detail ?: provenance.detail
        if (text.isNotBlank()) {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = Gen.TextSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** PRD §5.4 - glowing status pill with a live pulsing dot. */
@Composable
fun StatusPill(
    text: String,
    accent: Color,
    modifier: Modifier = Modifier,
    pulsing: Boolean = false,
) {
    val alpha = if (pulsing) {
        val t = rememberInfiniteTransition(label = "pulse")
        t.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
            label = "pulseAlpha",
        ).value
    } else 1f

    Row(
        modifier
            .clip(Gen.PillShape)
            .background(accent.copy(alpha = 0.12f))
            .border(1.dp, accent.copy(alpha = 0.5f), Gen.PillShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(7.dp)
                .alpha(alpha)
                .clip(CircleShape)
                .background(accent)
        )
        Text(
            text,
            color = accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
    }
}

/** PRD §5.3 - floating panel: 16dp corners, 1px emerald hairline. */
@Composable
fun GenCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = Gen.PanelShape,
        color = Gen.Surface,
        border = BorderStroke(1.dp, Gen.Hairline),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier.padding(bottom = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Gen.TextPrimary)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Gen.TextSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
fun PrimaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = Gen.PillShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Gen.Primary,
            contentColor = Gen.Base,
            disabledContainerColor = Gen.Hairline,
            disabledContentColor = Gen.TextSecondary,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SecondaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Gen.Primary,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = Gen.PillShape,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** A yes/no or multiple-choice chip used throughout the field checklists. */
@Composable
fun ChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Gen.Primary,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = Gen.PillShape,
        color = if (selected) accent.copy(alpha = 0.16f) else Gen.Base,
        border = BorderStroke(1.dp, if (selected) accent else Gen.Hairline),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) accent else Gen.TextSecondary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
fun HeroMetric(value: String, caption: String, accent: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            value,
            style = MaterialTheme.typography.displayLarge,
            color = accent,
        )
        Text(caption, style = MaterialTheme.typography.labelSmall, color = Gen.TextSecondary)
    }
}
