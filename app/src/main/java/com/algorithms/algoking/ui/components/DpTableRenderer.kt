package com.algorithms.algoking.ui.components

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.algorithms.algoking.engine.scene.BagMeter
import com.algorithms.algoking.engine.scene.CellState
import com.algorithms.algoking.engine.scene.ChoiceEmphasis
import com.algorithms.algoking.engine.scene.ChoiceSide
import com.algorithms.algoking.engine.scene.ChoiceStrip
import com.algorithms.algoking.engine.scene.DpTableScene
import com.algorithms.algoking.engine.scene.ItemCard
import com.algorithms.algoking.engine.scene.TableHeader
import com.algorithms.algoking.ui.theme.AlgoColors
import com.algorithms.algoking.ui.theme.AlgoType
import com.algorithms.algoking.ui.theme.Dimens
import com.algorithms.algoking.ui.theme.Radius
import com.algorithms.algoking.ui.theme.Spacing

/**
 * A table with two meaningful axes — DESIGN_SYSTEM.md §6.16i.
 *
 * What is new here is **layout only**. Every cell is the [SceneCell] every lesson
 * draws, every strip is the `surfaceVariant` card with a `labelSmall` caption over
 * a `numeralMedium` value that `PrefixTable` uses, and every colour is an existing
 * token.
 *
 * ### Fitting a phone
 *
 * The row names take the 64dp gutter the Stack-vs-Queue table already names rows
 * with, and the columns share what is left: about 35dp each for six capacities on a
 * 360dp phone — wider than Counting Sort's eight buckets. Nothing scrolls, and
 * nothing shrinks; a cell is `sceneCellHeight` tall like everywhere else.
 *
 * A cell not computed yet is an empty hairline slot, not a zero — a zero would
 * claim a value exists. The cell being decided is a `GHOST`: the hole being filled.
 */
@Composable
fun DpTable(
    scene: DpTableScene,
    modifier: Modifier = Modifier,
    selectableSlots: Set<Int> = emptySet(),
    onSelectSlot: (Int) -> Unit = {},
) {
    Column(modifier.fillMaxWidth()) {
        if (scene.items.isNotEmpty()) {
            ItemCards(scene.items)
            Gap(Spacing.sm)
        }
        scene.bag?.let { bag ->
            BagMeterRow(bag)
            if (scene.tableVisible) Gap(Spacing.md)
        }

        if (!scene.tableVisible) return@Column

        TableGrid(scene, selectableSlots, onSelectSlot)

        scene.focusCaption?.let { caption ->
            Gap(Spacing.xs)
            Text(
                text = caption,
                style = AlgoType.labelSmall,
                color = AlgoColors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        scene.choice?.let { choice ->
            Gap(Spacing.sm)
            ChoiceStripRow(choice)
        }
    }
}

@Composable
private fun TableGrid(
    scene: DpTableScene,
    selectableSlots: Set<Int>,
    onSelectSlot: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.sceneCellGap),
    ) {
        // What the columns count, over the row-name gutter, then the columns.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.sceneCellGap),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = scene.columnCaption.uppercase(),
                style = AlgoType.labelSmall,
                color = AlgoColors.textMuted,
                maxLines = 1,
                modifier = Modifier.width(Dimens.compareLabelWidth),
            )
            scene.columnHeaders.forEach { header ->
                Text(
                    text = header.label,
                    style = AlgoType.labelMedium,
                    color = if (header.active) AlgoColors.primary else AlgoColors.textMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        scene.cells.forEachIndexed { row, cells ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.sceneCellGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RowName(scene.rowHeaders.getOrNull(row))
                cells.forEach { cell ->
                    if (cell == null) {
                        Box(
                            Modifier
                                .weight(1f)
                                .height(Dimens.sceneCellHeight)
                                .border(Dimens.hairline, AlgoColors.border, Radius.sceneCell),
                        )
                    } else {
                        SceneCell(
                            cell = cell,
                            inRange = false,
                            modifier = Modifier.weight(1f),
                            selectable = cell.slot in selectableSlots,
                            onSelect = { onSelectSlot(cell.slot) },
                        )
                    }
                }
            }
        }
    }
}

/** The item a row adds: its name, and its weight and value underneath. */
@Composable
private fun RowName(header: TableHeader?) {
    Column(Modifier.width(Dimens.compareLabelWidth)) {
        Text(
            text = header?.label.orEmpty(),
            style = AlgoType.labelMedium,
            color = if (header?.active == true) AlgoColors.primary else AlgoColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        header?.detail?.let {
            Text(
                text = it,
                style = AlgoType.labelSmall,
                color = AlgoColors.textMuted,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ItemCards(items: List<ItemCard>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        items.forEach { ItemCardView(it, Modifier.weight(1f)) }
    }
}

/**
 * One item. Taken is green, left out fades, the one being read is violet — the
 * same states the table's cells use, so the cards and the table agree.
 */
@Composable
private fun ItemCardView(item: ItemCard, modifier: Modifier = Modifier) {
    val fill = when (item.state) {
        CellState.FINALIZED -> AlgoColors.successSoft
        CellState.COMPARING -> AlgoColors.primarySoft
        else -> AlgoColors.surface
    }
    val outline = when (item.state) {
        CellState.FINALIZED -> AlgoColors.success
        CellState.COMPARING -> AlgoColors.primary
        else -> AlgoColors.border
    }
    Column(
        modifier = modifier
            .alpha(if (item.state == CellState.ELIMINATED) 0.55f else 1f)
            .background(fill, Radius.cell)
            .border(Dimens.outline, outline, Radius.cell)
            .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = item.name,
            style = AlgoType.titleSmall,
            color = AlgoColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "w${item.weight} · v${item.value}",
            style = AlgoType.labelSmall,
            color = AlgoColors.textMuted,
            maxLines = 1,
        )
        item.bitLabel?.let { bit ->
            Gap(Spacing.xxs)
            Box(
                Modifier
                    .background(AlgoColors.primarySoft, Radius.pill)
                    .padding(horizontal = Spacing.xs),
            ) {
                Text(text = bit, style = AlgoType.labelSmall, color = AlgoColors.primary, maxLines = 1)
            }
        }
    }
}

/** How full the bag is — a track that fills, and the weight and value in numbers. */
@Composable
private fun BagMeterRow(bag: BagMeter) {
    val fraction = if (bag.capacity == 0) 0f else (bag.used.toFloat() / bag.capacity).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "BAG", style = AlgoType.labelSmall, color = AlgoColors.textMuted)
            Box(Modifier.weight(1f))
            Text(
                text = "${bag.used} / ${bag.capacity}",
                style = AlgoType.titleSmall,
                color = AlgoColors.textPrimary,
            )
            Gap(Spacing.xs)
            Text(text = "value ${bag.value}", style = AlgoType.labelMedium, color = AlgoColors.primary)
        }
        Gap(Spacing.xxs)
        Box(
            Modifier
                .fillMaxWidth()
                .height(Dimens.stepDotActive)
                .background(AlgoColors.surfaceMuted, Radius.pill),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(Dimens.stepDotActive)
                    .background(AlgoColors.success, Radius.pill),
            )
        }
        if (bag.contents.isNotEmpty()) {
            Gap(Spacing.xxs)
            Text(
                text = bag.contents.joinToString(" + "),
                style = AlgoType.labelSmall,
                color = AlgoColors.textSecondary,
            )
        }
    }
}

/**
 * The two outcomes side by side. The first side takes the first decision tone and
 * the second the second — the pairing `DecisionTone.forIndex` gives the buttons
 * underneath, so each side is the colour of the button that chooses it.
 */
@Composable
private fun ChoiceStripRow(strip: ChoiceStrip) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        ChoiceCard(strip.first, DecisionTone.First.hue, Modifier.weight(1f))
        ChoiceCard(strip.second, DecisionTone.Second.hue, Modifier.weight(1f))
    }
}

@Composable
private fun ChoiceCard(side: ChoiceSide, tone: Color, modifier: Modifier = Modifier) {
    val chosen = side.emphasis == ChoiceEmphasis.CHOSEN
    Column(
        modifier = modifier
            .alpha(if (side.emphasis == ChoiceEmphasis.PASSED) 0.5f else 1f)
            .background(AlgoColors.surfaceVariant, Radius.card)
            .border(
                width = if (chosen) Dimens.outlineStrong else Dimens.hairline,
                color = if (chosen) tone else AlgoColors.border,
                shape = Radius.card,
            )
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    ) {
        Text(text = side.caption.uppercase(), style = AlgoType.labelSmall, color = tone, maxLines = 1)
        Text(
            text = side.formula,
            style = AlgoType.labelSmall,
            color = AlgoColors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            // `?` while this side is still the question.
            text = side.value?.toString() ?: "?",
            style = AlgoType.numeralMedium,
            color = if (chosen) tone else AlgoColors.textPrimary,
        )
    }
}
