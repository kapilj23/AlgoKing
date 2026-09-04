package com.ttele.algoking.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.ttele.algoking.engine.scene.Cell
import com.ttele.algoking.engine.scene.CellState
import com.ttele.algoking.engine.scene.PrefixEquation
import com.ttele.algoking.engine.scene.PrefixOp
import com.ttele.algoking.engine.scene.PrefixScene
import com.ttele.algoking.ui.theme.AlgoColors
import com.ttele.algoking.ui.theme.AlgoType
import com.ttele.algoking.ui.theme.AlgoViz
import com.ttele.algoking.ui.theme.Dimens
import com.ttele.algoking.ui.theme.Radius
import com.ttele.algoking.ui.theme.Spacing

/**
 * Two aligned arrays — DESIGN_SYSTEM.md §6.16f.
 *
 * ### The offset is the whole picture
 *
 * Both rows are laid out over the **same** `n + 1` slots, and the source row
 * leaves slot 0 empty. That puts `array[i]` directly above `prefix[i + 1]` — the
 * cell it is the increment for — and leaves `prefix[0] = 0` standing alone at the
 * left with nothing above it, which is exactly why the prefix array is one longer.
 *
 * A learner who reads nothing should still be able to see that each prefix cell
 * is the one before it plus the array cell above it.
 *
 * Cells are the same [SceneCell] every other lesson draws, so an uncomputed prefix
 * entry is a `GHOST` — a hole, never a greyed-out number claiming a value exists.
 */
@Composable
fun PrefixTable(
    scene: PrefixScene,
    modifier: Modifier = Modifier,
) {
    // One slot per prefix cell; the source row is inset by one so the columns line
    // up on the relationship rather than on the index.
    val slots = scene.prefix.size

    Column(modifier.fillMaxWidth()) {

        RowCaption(scene.sourceLabel)
        // Slot 0 belongs to prefix[0] alone: there is no array value that produced
        // it, and the gap says so.
        CellRow(slots) { slot -> scene.source.getOrNull(slot - 1) }

        // Index labels for the source row, in its own slots.
        SlotRow(cellCount = slots) { slot ->
            if (slot >= 1 && slot - 1 <= scene.source.lastIndex) {
                Text(
                    text = (slot - 1).toString(),
                    style = AlgoType.labelSmall,
                    color = AlgoColors.textMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // The span being asked about, drawn under the values it covers.
        scene.queryRange?.let { range ->
            SlotRow(cellCount = slots) { slot ->
                val index = slot - 1
                if (index in range) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(Dimens.dotRail)
                            .background(AlgoViz.pointer, Radius.pill),
                    )
                }
            }
            Text(
                text = "sum ${range.first}..${range.last}",
                style = AlgoType.labelSmall,
                color = AlgoViz.pointer,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }

        Gap(Spacing.sm)

        RowCaption(scene.prefixLabel)
        CellRow(slots) { slot -> scene.prefix.getOrNull(slot) }
        SlotRow(cellCount = slots) { slot ->
            if (slot <= scene.prefix.lastIndex) {
                Text(
                    text = slot.toString(),
                    style = AlgoType.labelSmall,
                    color = AlgoColors.textMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }

        scene.equation?.let {
            Gap(Spacing.md)
            EquationStrip(it)
        }
    }
}

/**
 * One row of cells across [slots] equal columns, with a gap where a slot holds
 * nothing.
 *
 * The weight goes **into** [SceneCell], not around it. That is what gives a cell
 * the same width and height it has in every other lesson; wrapping it in a Box
 * leaves it unconstrained, and it collapses to a narrow capsule with the numeral
 * spilling over the edges.
 */
@Composable
private fun CellRow(slots: Int, cellAt: (Int) -> Cell?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.sceneCellGap),
    ) {
        repeat(slots) { slot ->
            when (val cell = cellAt(slot)) {
                null -> Spacer(Modifier.weight(1f))
                else -> SceneCell(
                    cell = cell,
                    inRange = false,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** The row's name, so the two arrays are never confused for one. */
@Composable
private fun RowCaption(text: String) {
    Text(
        text = text.uppercase(),
        style = AlgoType.labelSmall,
        color = AlgoColors.textMuted,
        modifier = Modifier.padding(bottom = Spacing.xxs),
    )
}

/**
 * The working, on one line: `prefix[1] + array[1]` over `2 + 4 = 6`.
 *
 * The result reads `?` until it is known — an answer already on screen is not a
 * question, the same rule the hash flow follows (ADR-030).
 */
@Composable
private fun EquationStrip(equation: PrefixEquation) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AlgoColors.surfaceVariant, Radius.card)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Operand(equation.left.toString(), equation.leftLabel)
        Symbol(if (equation.operator == PrefixOp.PLUS) "+" else "−")
        Operand(equation.right.toString(), equation.rightLabel)
        Symbol("=")
        Operand(
            value = equation.result?.toString() ?: "?",
            caption = equation.resultLabel,
            emphasised = true,
        )
    }
}

@Composable
private fun Operand(value: String, caption: String?, emphasised: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        caption?.let {
            Text(
                text = it,
                style = AlgoType.labelSmall,
                color = AlgoColors.textMuted,
                maxLines = 1,
            )
        }
        Text(
            text = value,
            style = AlgoType.numeralMedium,
            color = if (emphasised) AlgoColors.primary else AlgoColors.textPrimary,
        )
    }
}

@Composable
private fun Symbol(text: String) {
    Text(
        text = text,
        style = AlgoType.numeralMedium,
        color = AlgoColors.textSecondary,
        modifier = Modifier.padding(horizontal = Spacing.xs),
    )
}

