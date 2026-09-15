package com.algorithms.algoking.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import com.algorithms.algoking.engine.scene.BitRow
import com.algorithms.algoking.engine.scene.BitStep
import com.algorithms.algoking.engine.scene.BitwiseScene
import com.algorithms.algoking.engine.scene.TruthRow
import com.algorithms.algoking.ui.theme.AlgoColors
import com.algorithms.algoking.ui.theme.AlgoType
import com.algorithms.algoking.ui.theme.AlgoViz
import com.algorithms.algoking.ui.theme.Dimens
import com.algorithms.algoking.ui.theme.Radius
import com.algorithms.algoking.ui.theme.Spacing

/**
 * Column-aligned rows of bits, and the truth table that combines them —
 * DESIGN_SYSTEM.md §6.16k.
 *
 * ### The column is the operation
 *
 * Three rows, one set of columns, and a rule above the last of them — the way the
 * sum is written out on paper. A learner who reads nothing should still be able to
 * see that the bottom bit is made from the two above it, and that is the entire
 * job of this layout.
 *
 * Cells are the same [SceneCell] every other lesson draws, so a result bit not
 * decided yet is a `GHOST`: a hole, never a `0`. That matters more here than
 * anywhere else in the app, because `0` is a real answer in this table — a
 * placeholder zero would be indistinguishable from a decided one.
 */
@Composable
fun BitwiseTable(
    scene: BitwiseScene,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {

        scene.rows.forEachIndexed { index, row ->
            // A rule above the last row, because it is the one the others make.
            if (index == scene.rows.lastIndex) {
                Gap(Spacing.xs)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(Dimens.dotRail)
                        .background(AlgoColors.borderStrong, Radius.pill),
                )
                Gap(Spacing.xs)
            }
            BitRowView(row)
        }

        Gap(Spacing.md)
        TruthTable(scene.truthTable)

        scene.step?.let {
            Gap(Spacing.md)
            BitStrip(it)
        }
    }
}

/** One labelled row: the caption above, the bits below, sharing the columns. */
@Composable
private fun BitRowView(row: BitRow) {
    Column(Modifier.padding(bottom = Spacing.xs)) {
        Text(
            text = row.label.uppercase(),
            style = AlgoType.labelSmall,
            color = AlgoColors.textMuted,
            modifier = Modifier.padding(bottom = Spacing.xxs),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.sceneCellGap),
        ) {
            row.bits.sortedBy { it.slot }.forEach { cell ->
                SceneCell(
                    cell = cell,
                    inRange = false,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * All four rows, always, with the one in play lit.
 *
 * It is the lookup the learner should be making, so the picture makes it — and it
 * stays on screen for the whole lesson rather than appearing when it is needed,
 * because a table that comes and goes reads as a hint rather than as the rule.
 */
@Composable
private fun TruthTable(rows: List<TruthRow>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AlgoColors.surfaceVariant, Radius.card)
            .padding(Spacing.sm),
    ) {
        Text(
            text = "XOR",
            style = AlgoType.labelSmall,
            color = AlgoColors.textMuted,
            modifier = Modifier.padding(bottom = Spacing.xxs),
        )
        // Two per row: four lines stacked would be tall, and the pairing reads
        // fine side by side at 360dp.
        rows.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                pair.forEach { row ->
                    TruthLine(row, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TruthLine(row: TruthRow, modifier: Modifier = Modifier) {
    val outline by animateColorAsState(
        targetValue = if (row.active) AlgoViz.comparing else Color.Transparent,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "truthOutline",
    )
    val fill by animateColorAsState(
        targetValue = if (row.active) AlgoColors.primarySoft else Color.Transparent,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "truthFill",
    )
    val ink = if (row.active) AlgoViz.comparing else AlgoColors.textSecondary

    Text(
        text = "${row.a} ⊕ ${row.b} = ${row.result}",
        style = AlgoType.labelMedium,
        color = ink,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = modifier
            .background(fill, Radius.sceneCell)
            .border(Dimens.outline, outline, Radius.sceneCell)
            .padding(vertical = Spacing.xxs),
    )
}

/**
 * The working, on one line: `1 ⊕ 1 = 0`, with the column it belongs to named.
 *
 * The result reads `?` until it is known — an answer already on screen is not a
 * question (ADR-030).
 */
@Composable
private fun BitStrip(step: BitStep) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AlgoColors.surfaceVariant, Radius.card)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "bit ${step.index}",
            style = AlgoType.labelSmall,
            color = AlgoColors.textMuted,
        )
        Box(Modifier.width(Spacing.md))
        Operand(step.a.toString())
        Symbol("⊕")
        Operand(step.b.toString())
        Symbol("=")
        Operand(step.result?.toString() ?: "?", emphasised = true)
    }
}

@Composable
private fun Operand(value: String, emphasised: Boolean = false) {
    Text(
        text = value,
        style = AlgoType.numeralMedium,
        color = if (emphasised) AlgoColors.primary else AlgoColors.textPrimary,
        maxLines = 1,
    )
}

@Composable
private fun Symbol(text: String) {
    Text(
        text = text,
        style = AlgoType.titleSmall,
        color = AlgoColors.textSecondary,
        modifier = Modifier.padding(horizontal = Spacing.xs),
    )
}
