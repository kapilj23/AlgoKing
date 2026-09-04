package com.ttele.algoking.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ttele.algoking.ui.icons.AlgoIcons
import com.ttele.algoking.ui.theme.AlgoAccent
import com.ttele.algoking.ui.theme.AlgoColors
import com.ttele.algoking.ui.theme.AlgoGradients
import com.ttele.algoking.ui.theme.AlgoType
import com.ttele.algoking.ui.theme.AlgoViz
import com.ttele.algoking.ui.theme.Dimens
import com.ttele.algoking.ui.theme.Elevation
import com.ttele.algoking.ui.theme.Radius
import com.ttele.algoking.ui.theme.Spacing
import com.ttele.algoking.ui.theme.algoShadow

/**
 * Category chip — DESIGN_SYSTEM.md §6.4.
 * Selected: the primary gradient. Unselected: white with a hairline.
 */
@Composable
fun CategoryChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .height(Dimens.chipHeight)
            .then(if (selected) Modifier.algoShadow(Elevation.card, Radius.pill) else Modifier)
            .then(
                if (selected) {
                    Modifier.background(AlgoGradients.primary(), Radius.pill)
                } else {
                    Modifier
                        .background(AlgoColors.surface, Radius.pill)
                        .border(Dimens.hairline, AlgoColors.border, Radius.pill)
                },
            )
            .clickable(interaction, null, onClick = onClick)
            .padding(horizontal = Dimens.chipPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = AlgoType.labelMedium,
            color = if (selected) AlgoColors.onPrimary else AlgoColors.textPrimary,
        )
    }
}

/**
 * Category badge — DESIGN_SYSTEM.md §6.5.
 * Tinted with the algorithm's own accent; solid gradient when its card is selected.
 */
@Composable
fun CategoryBadge(
    label: String,
    accent: AlgoAccent,
    modifier: Modifier = Modifier,
    solid: Boolean = false,
) {
    Box(
        modifier = modifier
            .height(Dimens.badgeHeight)
            .then(
                if (solid) {
                    Modifier.background(AlgoGradients.accentTile(accent), Radius.pill)
                } else {
                    Modifier.background(accent.soft, Radius.pill)
                },
            )
            .padding(horizontal = Dimens.badgePadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = AlgoType.labelSmall,
            color = if (solid) AlgoColors.onPrimary else accent.onSoft,
        )
    }
}

/**
 * Progress ring — DESIGN_SYSTEM.md §6.6.
 * Starts at twelve o'clock, sweeps clockwise, and fills with a light-to-core
 * gradient so a complete ring reads as a gradient loop.
 */
@Composable
fun ProgressRing(
    /** Whole percent, 0..100. The ring and the numeral read the same value. */
    percent: Int,
    accent: AlgoAccent,
    modifier: Modifier = Modifier,
) {
    val progress = percent.coerceIn(0, 100) / 100f
    Box(
        modifier = modifier.size(Dimens.progressRing),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
            val stroke = Dimens.progressRingStroke.toPx()
            val inset = stroke / 2f
            val arcSize = androidx.compose.ui.geometry.Size(
                size.width - stroke,
                size.height - stroke,
            )
            drawArc(
                color = accent.soft,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                brush = Brush.sweepGradient(
                    0.00f to accent.light,
                    0.55f to accent.core,
                    1.00f to accent.light,
                ),
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Text(
            text = "${percent.coerceIn(0, 100)}%",
            style = AlgoType.numeralSmall,
            color = AlgoColors.textPrimary,
        )
    }
}

/** The states an algorithm row can be in, mapped to its right-hand slot. */
sealed interface AlgorithmStatus {
    val label: String

    data class Mastered(override val label: String = "Mastered") : AlgorithmStatus

    /**
     * [percent] is a whole number — 0, 33, 66 — because the learning model has
     * exactly three milestones. Carrying a float here would invite a ring that
     * says 32 % and a label that says 33 %.
     */
    data class InProgress(
        val percent: Int,
        override val label: String = "In Progress",
    ) : AlgorithmStatus

    data class Locked(override val label: String = "Locked") : AlgorithmStatus
}

/**
 * Algorithm card — DESIGN_SYSTEM.md §6.3.
 *
 * The selected variant changes exactly two things: the fill becomes
 * `primarySurface` and the badge goes solid. The radius, the padding and the
 * internal rhythm are shared with every other card in the app.
 */
@Composable
fun AlgorithmCard(
    title: String,
    description: String,
    category: String,
    accent: AlgoAccent,
    glyph: ImageVector,
    status: AlgorithmStatus,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }

    AlgoCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(interaction, null, onClick = onClick),
        color = if (selected) AlgoColors.primarySurface else AlgoColors.surface,
        shadow = if (selected) null else Elevation.card,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Icon tile — the algorithm's accent gradient, white glyph.
            Box(
                modifier = Modifier
                    .size(Dimens.algorithmIconTile)
                    .algoShadow(Elevation.card, Radius.icon)
                    .background(AlgoGradients.accentTile(accent), Radius.icon),
                contentAlignment = Alignment.Center,
            ) {
                AlgoIcon(glyph, AlgoColors.onPrimary, Dimens.algorithmIconGlyph)
            }

            Gap(Spacing.sm)

            Column(Modifier.weight(1f)) {
                Text(title, style = AlgoType.titleMedium, color = AlgoColors.textPrimary)
                Gap(Spacing.xxs)
                Text(
                    text = description,
                    style = AlgoType.bodyMedium,
                    color = AlgoColors.textSecondary,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Gap(Spacing.xs)
                CategoryBadge(category, accent, solid = selected)
            }

            Gap(Spacing.xs)

            Column(
                modifier = Modifier.width(Dimens.statusColumn),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(status.label, style = AlgoType.labelSmall, color = AlgoColors.textMuted)
                Gap(Spacing.xs)
                when (status) {
                    is AlgorithmStatus.Mastered -> ProgressRing(100, accent)
                    is AlgorithmStatus.InProgress -> ProgressRing(status.percent, accent)
                    is AlgorithmStatus.Locked -> Box(
                        modifier = Modifier
                            .size(Dimens.lockTile)
                            .background(AlgoColors.disabledSurface, Radius.cell),
                        contentAlignment = Alignment.Center,
                    ) {
                        AlgoIcon(AlgoIcons.Lock, AlgoColors.disabled, Dimens.secondaryGlyph)
                    }
                }
            }

            Gap(Spacing.xs)
            AlgoIcon(AlgoIcons.ChevronRight, AlgoColors.disabled, Dimens.chevron)
        }
    }
}

/** One cell of a [MetricRow]. */
data class Metric(val label: String, val value: String)

/**
 * Metric card — DESIGN_SYSTEM.md §6.10.
 * Three equal cells split by inset hairline rules.
 */
@Composable
fun MetricRow(metrics: List<Metric>, modifier: Modifier = Modifier) {
    AlgoCard(
        modifier = modifier.fillMaxWidth().height(Dimens.metricRowHeight),
        padding = Spacing.xs,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            metrics.forEachIndexed { index, metric ->
                if (index > 0) {
                    Box(
                        Modifier
                            .width(Dimens.hairline)
                            .fillMaxHeight()
                            .padding(vertical = Spacing.xxs)
                            .background(AlgoColors.border),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(metric.label, style = AlgoType.labelSmall, color = AlgoColors.textMuted)
                    Gap(Spacing.xxs)
                    Text(metric.value, style = AlgoType.numeralMedium, color = AlgoColors.textPrimary)
                }
            }
        }
    }
}

/**
 * Explanation card — DESIGN_SYSTEM.md §6.11.
 *
 * The RESULT third of COMPARE → DECISION → RESULT. It sits on `surfaceVariant`
 * rather than `surface` so it reads as consequence, not instruction.
 */
@Composable
fun ExplanationCard(
    title: String,
    lines: List<String>,
    modifier: Modifier = Modifier,
    mascot: Boolean = true,
) {
    AlgoCard(
        modifier = modifier.fillMaxWidth(),
        color = AlgoColors.surfaceVariant,
        shadow = null,
        border = AlgoColors.border,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(title, style = AlgoType.titleMedium, color = AlgoColors.primary)
                Gap(Spacing.xs)
                lines.forEach { line ->
                    Text(line, style = AlgoType.bodyLarge, color = AlgoColors.textSecondary)
                }
            }
            if (mascot) {
                Gap(Spacing.xs)
                MascotKing(Modifier.size(Dimens.mascot))
            }
        }
    }
}

/** A pill on `primarySoft` — the step counter and any other inline meta label. */
@Composable
fun StepChip(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(Dimens.badgeHeight + Spacing.xs)
            .background(AlgoColors.primarySoft, Radius.pill)
            .padding(horizontal = Spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = AlgoType.labelMedium, color = AlgoColors.primary)
    }
}

/** The outlined info affordance in the corner of a learning card. */
@Composable
fun InfoButton(modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(28.dp)
            .background(AlgoColors.primarySoft, Radius.pill)
            .border(Dimens.outline, AlgoColors.primary, Radius.pill)
            .clickable(interaction, null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AlgoIcon(AlgoIcons.Info, AlgoColors.primary, 16.dp)
    }
}

/** Centred body copy used inside instruction cards. */
@Composable
fun InstructionText(lines: List<String>, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        lines.forEach {
            Text(
                text = it,
                style = AlgoType.bodyLarge,
                color = AlgoColors.textSecondary,
                textAlign = TextAlign.Start,
            )
        }
    }
}

/** Exposed so previews can show every accent tile side by side. */
internal val accentSwatches: List<Pair<AlgoAccent, Color>> = AlgoAccent.entries.map { it to it.core }

/**
 * A compact count for the header's trailing slot — "3 / 9".
 * Same 44dp pill geometry as [StreakChip], so header treatments line up across
 * every screen in the app.
 */
@Composable
fun HeaderCountChip(current: Int, total: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(Dimens.headerButton)
            .background(AlgoColors.primarySoft, Radius.pill)
            .padding(horizontal = Spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$current / $total",
            style = AlgoType.labelMedium,
            color = AlgoColors.primary,
        )
    }
}

/**
 * Walkthrough progress — deliberately quiet. It answers "am I getting anywhere?"
 * and nothing else, so it never competes with the array for attention.
 */
@Composable
fun StepDots(total: Int, current: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { index ->
            Box(
                Modifier
                    .size(if (index == current) Dimens.stepDotActive else Dimens.stepDot)
                    .background(
                        when {
                            index == current -> AlgoColors.primary
                            index < current -> AlgoColors.primary.copy(alpha = 0.35f)
                            else -> AlgoColors.borderStrong
                        },
                        Radius.pill,
                    ),
            )
        }
    }
}

/**
 * How `mid` was arrived at, as a chip the learner can check against the array:
 * `mid = 0 + (11 − 0) ÷ 2 = 5`.
 *
 * It shows the canonical AlgoKing formula, `left + (right - left) / 2`, which is
 * the same line Code Reveal prints in Java and Python. Teaching one formula in
 * the animation and showing another in the code would break the one promise the
 * product is built on.
 *
 * Written this way rather than as `(left + right) / 2` because the subtraction
 * form cannot overflow, and the version a learner is taught should be the version
 * they can safely reuse.
 *
 * `left` and `right` carry the same violet as their pointer labels under the
 * array, so the eye can travel between the equation and the positions it names.
 */
@Composable
fun MidpointChip(lo: Int, hi: Int, mid: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(AlgoColors.primarySoft, Radius.card)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The rule, in the words printed under the array. A learner who reads
        // this line can find every term of it on screen.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("mid = ", style = AlgoType.labelMedium, color = AlgoColors.textSecondary)
            Text("left", style = AlgoType.labelMedium, color = AlgoViz.pointer)
            Text(" + (", style = AlgoType.labelMedium, color = AlgoColors.textSecondary)
            Text("right", style = AlgoType.labelMedium, color = AlgoViz.pointer)
            Text(" − ", style = AlgoType.labelMedium, color = AlgoColors.textSecondary)
            Text("left", style = AlgoType.labelMedium, color = AlgoViz.pointer)
            Text(") ÷ 2", style = AlgoType.labelMedium, color = AlgoColors.textSecondary)
        }
        Gap(Spacing.xxs)
        // The same rule with this round's positions substituted in.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$lo", style = AlgoType.numeralMedium, color = AlgoViz.pointer)
            Text(" + (", style = AlgoType.numeralMedium, color = AlgoColors.textSecondary)
            Text("$hi", style = AlgoType.numeralMedium, color = AlgoViz.pointer)
            Text(" − ", style = AlgoType.numeralMedium, color = AlgoColors.textSecondary)
            Text("$lo", style = AlgoType.numeralMedium, color = AlgoViz.pointer)
            Text(") ÷ 2 = ", style = AlgoType.numeralMedium, color = AlgoColors.textSecondary)
            Text("$mid", style = AlgoType.numeralMedium, color = AlgoColors.primary)
        }
    }
}

/** The comparison being stated, as a chip: `45 < 73`. */
@Composable
fun ComparisonChip(left: Int, symbol: String, right: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(AlgoColors.primarySoft, Radius.pill)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$left", style = AlgoType.numeralMedium, color = AlgoViz.comparing)
        Gap(Spacing.xs)
        Text(symbol, style = AlgoType.numeralMedium, color = AlgoColors.textSecondary)
        Gap(Spacing.xs)
        Text("$right", style = AlgoType.numeralMedium, color = AlgoColors.primary)
    }
}
