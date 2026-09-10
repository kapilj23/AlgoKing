package com.ttele.algoking.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.ttele.algoking.engine.scene.Cell
import com.ttele.algoking.engine.scene.CellState
import com.ttele.algoking.engine.scene.CountBucket
import com.ttele.algoking.engine.scene.CountTally
import com.ttele.algoking.engine.scene.CountingScene
import com.ttele.algoking.ui.theme.AlgoColors
import com.ttele.algoking.ui.theme.AlgoType
import com.ttele.algoking.ui.theme.AlgoViz
import com.ttele.algoking.ui.theme.Dimens
import com.ttele.algoking.ui.theme.Radius
import com.ttele.algoking.ui.theme.Spacing

/**
 * Three rows — DESIGN_SYSTEM.md §6.16h.
 *
 * ### Why the rows do not share columns
 *
 * `PrefixTable` lays its two arrays over shared slots because `array[i]` really
 * did produce `prefix[i + 1]`, and the offset is that lesson. Counting Sort has no
 * such relationship: input position 0 has nothing to do with bucket 0, and drawing
 * them in the same columns would invent an alignment the algorithm does not have.
 *
 * So each row fills the width on its own terms, and the rows are told apart by
 * their captions and by the count row's own treatment — a bucket is a cell with
 * **the value it counts printed underneath it**, which is the one relationship the
 * learner has to see.
 *
 * Cells are the same [SceneCell] every other lesson draws, so an output slot not
 * filled yet is a `GHOST` — a hole, never a zero pretending to be an answer.
 */
@Composable
fun CountingTable(
    scene: CountingScene,
    modifier: Modifier = Modifier,
    selectableSlots: Set<Int> = emptySet(),
    onSelectSlot: (Int) -> Unit = {},
) {
    Column(modifier.fillMaxWidth()) {

        RowCaption(scene.inputLabel)
        CellRow(scene.input.size) { slot -> scene.input.getOrNull(slot) }

        Gap(Spacing.md)

        // The table. `selectableSlots` indexes **buckets**, because both of this
        // lesson's questions are answered by tapping one.
        RowCaption(scene.countLabel)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.sceneCellGap),
        ) {
            scene.buckets.forEach { bucket ->
                BucketColumn(
                    bucket = bucket,
                    selectable = bucket.slot in selectableSlots,
                    onSelect = { onSelectSlot(bucket.slot) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        scene.tally?.let {
            Gap(Spacing.sm)
            TallyStrip(it)
        }

        Gap(Spacing.md)

        RowCaption(scene.outputLabel)
        CellRow(scene.output.size) { slot -> scene.output.getOrNull(slot) }
    }
}

/**
 * One bucket: the count inside the cell, the value it counts printed underneath.
 *
 * The value below is what makes the table readable as *"how many threes?"* rather
 * than as a second array of numbers — and it is deliberately in the same position
 * an index rail occupies everywhere else, because that is where the eye already
 * looks for "what this cell is about".
 */
@Composable
private fun BucketColumn(
    bucket: CountBucket,
    selectable: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        SceneCell(
            // The cell shows the count; the bucket's own value is the caption. A
            // display cell rather than scene data, so the engine never has to
            // decide which of a bucket's two numbers `Cell.value` means.
            cell = Cell(
                key = bucket.slot,
                value = bucket.count,
                slot = bucket.slot,
                state = bucket.state,
            ),
            inRange = false,
            selectable = selectable,
            onSelect = onSelect,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = bucket.value.toString(),
            style = AlgoType.labelSmall,
            color = if (selectable) AlgoViz.pointer else AlgoColors.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.xxs),
        )
    }
}

/**
 * `count[3]:  1 → 2`
 *
 * The change that just happened, as data rather than a sentence — the same job
 * the prefix equation strip does, and the same rule: it reports what was done,
 * never what should be done next.
 */
@Composable
private fun TallyStrip(tally: CountTally) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AlgoColors.surfaceVariant, Radius.card)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "count[${tally.value}]",
            style = AlgoType.labelSmall,
            color = AlgoColors.textMuted,
        )
        Gap(Spacing.xs)
        Text(
            text = tally.from.toString(),
            style = AlgoType.numeralMedium,
            color = AlgoColors.textSecondary,
        )
        Text(
            text = " → ",
            style = AlgoType.numeralMedium,
            color = AlgoColors.textMuted,
        )
        Text(
            // `?` while the new count is still the question.
            text = tally.to?.toString() ?: "?",
            style = AlgoType.numeralMedium,
            color = AlgoColors.primary,
        )
    }
}

/** One row of cells across [slots] equal columns, sized exactly as everywhere else. */
@Composable
private fun CellRow(slots: Int, cellAt: (Int) -> Cell?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.sceneCellGap),
    ) {
        repeat(slots) { slot ->
            when (val cell = cellAt(slot)) {
                null -> Box(Modifier.weight(1f))
                else -> SceneCell(
                    cell = cell,
                    inRange = false,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** The row's name, so three arrays are never read as one. */
@Composable
private fun RowCaption(text: String) {
    Text(
        text = text.uppercase(),
        style = AlgoType.labelSmall,
        color = AlgoColors.textMuted,
        modifier = Modifier.padding(bottom = Spacing.xxs),
    )
}

/** Kept for the legend: the states a counting scene can actually show. */
internal val CountingScene.legendStates: List<CellState>
    get() = (input.map { it.state } + buckets.map { it.state } + output.map { it.state })
