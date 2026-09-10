package com.ttele.algoking.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.ttele.algoking.ui.icons.AlgoIcons
import com.ttele.algoking.ui.theme.AlgoColors
import com.ttele.algoking.ui.theme.AlgoGradients
import com.ttele.algoking.ui.theme.AlgoType
import com.ttele.algoking.ui.theme.AlgoViz
import com.ttele.algoking.ui.theme.Dimens
import com.ttele.algoking.ui.theme.Elevation
import com.ttele.algoking.ui.theme.Radius
import com.ttele.algoking.ui.theme.Spacing
import com.ttele.algoking.ui.theme.algoShadow
import kotlin.math.max

/**
 * The four states in the legend — DESIGN_SYSTEM.md §1.3.
 * The same four mean the same four things in every algorithm.
 */
enum class VizState(val legendLabel: String) {
    Comparing("Comparing"),
    Next("Next"),
    Sorted("Sorted"),
    Checked("Checked"),
    ;

    val top: Color
        get() = when (this) {
            Comparing -> AlgoViz.comparingTop
            Next -> AlgoViz.nextTop
            Sorted -> AlgoViz.sortedTop
            Checked -> AlgoViz.checkedTop
        }

    val bottom: Color
        get() = when (this) {
            Comparing -> AlgoViz.comparingBottom
            Next -> AlgoViz.nextBottom
            Sorted -> AlgoViz.sortedBottom
            Checked -> AlgoViz.checkedBottom
        }

    val flat: Color
        get() = when (this) {
            Comparing -> AlgoViz.comparing
            Next -> AlgoViz.next
            Sorted -> AlgoViz.sorted
            Checked -> AlgoViz.checked
        }

    /** The numeral sits white on colour, ink on the lavender idle bar. */
    val onColor: Color
        get() = if (this == Checked) AlgoColors.textPrimary else AlgoColors.onPrimary
}

/** One element of the live array. */
data class BarSpec(val value: Int, val state: VizState)

/**
 * Array bar — DESIGN_SYSTEM.md §6.12.
 *
 * The reference's bar heights are decorative; here they encode the value, which is
 * what a sorting visualiser has to do. Everything else — width, radius, gradient,
 * numeral placement — is the reference's treatment unchanged.
 */
@Composable
fun ArrayBar(bar: BarSpec, maxValue: Int, modifier: Modifier = Modifier) {
    val span = Dimens.arrayBarMaxHeight - Dimens.arrayBarMinHeight
    val ratio = if (maxValue <= 0) 1f else bar.value.toFloat() / maxValue.toFloat()
    val height = Dimens.arrayBarMinHeight + span * ratio.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .width(Dimens.arrayBarWidth)
            .height(height)
            .then(
                if (bar.state == VizState.Comparing) {
                    Modifier.algoShadow(Elevation.raised, Radius.cell)
                } else {
                    Modifier
                },
            )
            .background(AlgoGradients.bar(bar.state.top, bar.state.bottom), Radius.cell),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Text(
            text = bar.value.toString(),
            style = AlgoType.numeralLarge,
            color = bar.state.onColor,
            modifier = Modifier.padding(bottom = Dimens.arrayBarValueInset),
        )
    }
}

/**
 * Array cell — DESIGN_SYSTEM.md §6.12.
 * The static problem statement: flat white, outlined, no gradient and no shadow.
 * This is how the reference separates "the problem" from "the live canvas".
 */
@Composable
fun ArrayCell(value: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(Dimens.arrayCell)
            .background(AlgoColors.surface, Radius.cell)
            .border(Dimens.outline, AlgoColors.border, Radius.cell),
        contentAlignment = Alignment.Center,
    ) {
        Text(value.toString(), style = AlgoType.numeralLarge, color = AlgoColors.textPrimary)
    }
}

/**
 * The live canvas: the dashed swap arc and its index labels above a row of bars.
 *
 * This is the COMPARE third of COMPARE → DECISION → RESULT — the two involved
 * elements are the only coloured things on the row, and the arc names them.
 */
@Composable
fun ArrayVisualizer(
    bars: List<BarSpec>,
    modifier: Modifier = Modifier,
    arcFrom: Int? = null,
    arcTo: Int? = null,
) {
    val maxValue = max(1, bars.maxOfOrNull { it.value } ?: 1)

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.arcRegionHeight),
        ) {
            if (arcFrom != null && arcTo != null) {
                SwapArc(bars.size, arcFrom, arcTo, Modifier.fillMaxSize())
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    Dimens.arrayBarGap,
                    Alignment.CenterHorizontally,
                ),
            ) {
                bars.indices.forEach { index ->
                    Box(
                        modifier = Modifier.width(Dimens.arrayBarWidth),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        if (index == arcFrom || index == arcTo) {
                            Text(
                                text = index.toString(),
                                style = AlgoType.labelMedium,
                                color = AlgoViz.indexLabel,
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(
                Dimens.arrayBarGap,
                Alignment.CenterHorizontally,
            ),
            verticalAlignment = Alignment.Bottom,
        ) {
            bars.forEach { ArrayBar(it, maxValue) }
        }
    }
}

/** The dashed arc that names the pair under comparison. */
@Composable
private fun SwapArc(count: Int, from: Int, to: Int, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val barWidth = Dimens.arrayBarWidth.toPx()
        val gap = Dimens.arrayBarGap.toPx()
        val slot = barWidth + gap
        val total = count * barWidth + (count - 1) * gap
        val originX = (size.width - total) / 2f

        fun centerOf(index: Int) = originX + index * slot + barWidth / 2f

        val startX = centerOf(from)
        val endX = centerOf(to)
        val baseY = size.height - 2.dp.toPx()
        val peakY = size.height * 0.22f

        val path = Path().apply {
            moveTo(startX, baseY)
            quadraticTo((startX + endX) / 2f, peakY - size.height * 0.30f, endX, baseY)
        }

        drawPath(
            path = path,
            color = AlgoViz.pointer,
            style = Stroke(
                width = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(7.dp.toPx(), 5.dp.toPx()),
                ),
            ),
        )

        // Arrowhead, pointing down into the destination bar.
        val head = 6.dp.toPx()
        val dir = if (endX >= startX) 1f else -1f
        drawLine(
            color = AlgoViz.pointer,
            start = Offset(endX, baseY),
            end = Offset(endX - dir * head, baseY - head),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = AlgoViz.pointer,
            start = Offset(endX, baseY),
            end = Offset(endX + dir * head * 0.35f, baseY - head * 1.25f),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/**
 * The legend — DESIGN_SYSTEM.md §6.13.
 * Printed under every array, on every screen, in this order. It is the contract.
 */
@Composable
fun VizLegend(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.legendGap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VizState.entries.forEach { state ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(Dimens.legendSwatch)
                        .background(state.flat, Radius.swatch),
                )
                Gap(Spacing.xs)
                Text(
                    text = state.legendLabel,
                    style = AlgoType.bodyMedium,
                    color = AlgoColors.textSecondary,
                    softWrap = false,
                    maxLines = 1,
                )
            }
        }
    }
}

/** One stage of the four-phase lesson spine. */
data class Stage(val label: String, val state: StageState)

enum class StageState { Complete, Current, Upcoming }

/**
 * Stage stepper — DESIGN_SYSTEM.md §6.14.
 * The connector leaving a completed node is a green-to-violet gradient: it is the
 * single place where the status hue hands over to the chrome hue.
 *
 * **Every node sits directly above its own label.** Both rows are laid out over
 * the same n equal slots, and a node is centred in its slot exactly as its label
 * is: hence the half-slot lead-in and lead-out (`weight(1f)` against the
 * connectors' `weight(2f)`, because the gap between two slot centres is twice the
 * distance from the edge to the first one). Without them the nodes stretch to the
 * outer edges of the row while the labels stay at their slot centres, and with
 * two stages that leaves each circle about 50dp adrift of the word beneath it.
 */
@Composable
fun StageStepper(stages: List<Stage>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Half a slot, so node 0 lands on the centre of label 0.
            Box(Modifier.weight(1f))
            stages.forEachIndexed { index, stage ->
                if (index > 0) {
                    val previous = stages[index - 1].state
                    val brush = when {
                        previous == StageState.Complete && stage.state == StageState.Current ->
                            Brush.horizontalGradient(listOf(AlgoColors.success, AlgoColors.primary))

                        previous == StageState.Complete ->
                            Brush.horizontalGradient(listOf(AlgoColors.success, AlgoColors.success))

                        else ->
                            Brush.horizontalGradient(listOf(AlgoColors.border, AlgoColors.border))
                    }
                    Box(
                        Modifier
                            // A whole slot: centre to centre, against the halves
                            // at either end.
                            .weight(2f)
                            .height(Dimens.stepperConnector)
                            .background(brush),
                    )
                }
                StageNode(index, stage.state)
            }
            Box(Modifier.weight(1f))
        }
        Gap(Spacing.xs)
        Row(modifier = Modifier.fillMaxWidth()) {
            stages.forEach { stage ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = stage.label,
                        style = AlgoType.labelMedium,
                        color = when (stage.state) {
                            StageState.Current -> AlgoColors.textPrimary
                            StageState.Complete -> AlgoColors.textSecondary
                            StageState.Upcoming -> AlgoColors.textMuted
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StageNode(index: Int, state: StageState) {
    Box(
        modifier = Modifier
            .size(Dimens.stepperNode)
            .then(
                when (state) {
                    StageState.Complete -> Modifier.background(AlgoColors.success, Radius.pill)
                    StageState.Current -> Modifier
                        .algoShadow(Elevation.card, Radius.pill)
                        .background(AlgoGradients.primary(), Radius.pill)

                    StageState.Upcoming -> Modifier
                        .background(AlgoColors.surface, Radius.pill)
                        .border(Dimens.outline, AlgoColors.borderStrong, Radius.pill)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            StageState.Complete -> AlgoIcon(AlgoIcons.Check, AlgoColors.onPrimary, 16.dp)
            StageState.Current -> Text(
                "${index + 1}",
                style = AlgoType.labelMedium,
                color = AlgoColors.onPrimary,
            )

            StageState.Upcoming -> Text(
                "${index + 1}",
                style = AlgoType.labelMedium,
                color = AlgoColors.textSecondary,
            )
        }
    }
}

