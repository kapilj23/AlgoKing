package com.ttele.algoking.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ttele.algoking.ui.icons.AlgoIcons
import com.ttele.algoking.ui.theme.AlgoAccent
import com.ttele.algoking.ui.theme.AlgoColors
import com.ttele.algoking.ui.theme.AlgoType
import com.ttele.algoking.ui.theme.Dimens
import com.ttele.algoking.ui.theme.Radius
import com.ttele.algoking.ui.theme.Spacing

/**
 * The moment the learner lands it.
 *
 * Anchored at the bottom of the screen, directly above the forward action, so the
 * congratulation and the next step read as one beat. Built entirely from existing
 * tokens — success green, gold sparkles, the mascot — so a celebration never looks
 * like it came from a different product. No confetti, no sound, no interruption:
 * the learner can move on the instant they want to.
 */
@Composable
fun CelebrationBanner(
    badge: String,
    headline: String,
    support: String,
    modifier: Modifier = Modifier,
    accent: AlgoAccent = AlgoAccent.Green,
    stats: List<String> = emptyList(),
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(headline) { shown = true }

    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.92f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "celebrationScale",
    )
    val fade by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(220),
        label = "celebrationFade",
    )

    Box(modifier.fillMaxWidth()) {
        AlgoCard(
            modifier = Modifier
                .fillMaxWidth()
                .scale(scale)
                .alpha(fade),
            color = accent.soft,
            shadow = null,
            border = accent.core.copy(alpha = 0.28f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    CategoryBadge(badge, accent, solid = true)
                    Gap(Spacing.xs)
                    Text(
                        text = headline,
                        style = AlgoType.headlineLarge,
                        color = AlgoColors.textPrimary,
                    )
                    Gap(Spacing.xxs)
                    Text(
                        text = support,
                        style = AlgoType.bodyMedium,
                        color = AlgoColors.textSecondary,
                    )
                    if (stats.isNotEmpty()) {
                        Gap(Spacing.xs)
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                            stats.forEach { StatPill(it) }
                        }
                    }
                }
                Gap(Spacing.xs)
                MascotKing(Modifier.size(Dimens.mascot))
            }
        }

        // Gold sparkles, drifting over the card's top edge.
        Sparkle(24.dp, (-6).dp, 18.dp, delayMillis = 0, modifier = Modifier.align(Alignment.TopStart))
        Sparkle(12.dp, 10.dp, 12.dp, delayMillis = 400, modifier = Modifier.align(Alignment.TopStart))
        Sparkle((-18).dp, (-8).dp, 16.dp, delayMillis = 700, modifier = Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun StatPill(label: String) {
    Box(
        Modifier
            .background(AlgoColors.surface, Radius.pill)
            .border(Dimens.hairline, AlgoColors.border, Radius.pill)
            .padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
    ) {
        Text(label, style = AlgoType.labelSmall, color = AlgoColors.textSecondary)
    }
}

@Composable
private fun Sparkle(
    x: Dp,
    y: Dp,
    size: Dp,
    delayMillis: Int,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "sparkle")
    val pulse by transition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, delayMillis = delayMillis),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sparklePulse",
    )
    val spin by transition.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, delayMillis = delayMillis),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sparkleSpin",
    )

    Box(
        modifier
            .offset(x = x, y = y)
            .scale(pulse)
            .rotate(spin),
    ) {
        AlgoIcon(AlgoIcons.Sparkle, AlgoColors.gold, size)
    }
}
