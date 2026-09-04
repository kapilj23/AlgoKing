package com.ttele.algoking.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.ttele.algoking.engine.scene.BucketRow
import com.ttele.algoking.engine.scene.BucketScene
import com.ttele.algoking.engine.scene.BucketState
import com.ttele.algoking.engine.scene.EntryCell
import com.ttele.algoking.engine.scene.EntryState
import com.ttele.algoking.engine.scene.FlowOperation
import com.ttele.algoking.engine.scene.FlowStage
import com.ttele.algoking.engine.scene.HashFlow
import com.ttele.algoking.ui.theme.AlgoColors
import com.ttele.algoking.ui.theme.AlgoType
import com.ttele.algoking.ui.theme.AlgoViz
import com.ttele.algoking.ui.theme.Dimens
import com.ttele.algoking.ui.theme.Radius
import com.ttele.algoking.ui.theme.Spacing

/**
 * The bucket table — DESIGN_SYSTEM.md §6.16e.
 *
 * Two things share the stage, and both are the lesson. Above: the **flow**, a key
 * turning into a bucket index in front of the learner. Below: the **table**, five
 * buckets that may hold nothing, one thing, or a chain.
 *
 * The flow is not decoration. Without it the table is a list of lists and the
 * learner never sees where the speed comes from; with it, the arithmetic and its
 * destination are on screen at the same moment.
 *
 * `selectableSlots` indexes **buckets**, because the answer to "where does this key
 * go?" is a place in the table, and it should be tapped there.
 */
@Composable
fun BucketTable(
    scene: BucketScene,
    modifier: Modifier = Modifier,
    selectableSlots: Set<Int> = emptySet(),
    onSelectSlot: (Int) -> Unit = {},
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {

        scene.flow?.let { flow ->
            HashFlowStrip(flow)
            Gap(Spacing.sm)
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Dimens.bucketGap),
        ) {
            scene.buckets.forEach { row ->
                BucketLine(
                    row = row,
                    selectable = row.index in selectableSlots,
                    onSelect = { onSelectSlot(row.index) },
                )
            }
        }
    }
}

/**
 * `12  →  12 % 5  →  2  →  BUCKET 2`
 *
 * Each stage appears only once it is actually known. The bucket stays a question
 * mark while the learner is being asked for it, which is what makes the question
 * askable at all — an answer already on screen is not a question.
 */
@Composable
private fun HashFlowStrip(flow: HashFlow) {
    val known = flow.stage != FlowStage.KEY && flow.stage != FlowStage.ASKING && flow.bucket != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AlgoColors.surfaceVariant, Radius.card)
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        FlowChip(
            label = when (flow.operation) {
                FlowOperation.PUT -> "PUT"
                FlowOperation.LOOKUP -> "GET"
                FlowOperation.REMOVE -> "REMOVE"
            },
            value = flow.key.toString(),
            tint = AlgoColors.primary,
        )
        FlowArrow()
        // The arithmetic, written out. Seeing "12 % 5" is the moment the structure
        // stops being magic.
        FlowChip(
            label = "HASH",
            value = "${flow.key} % ${flow.modulus}",
            tint = AlgoColors.textSecondary,
        )
        FlowArrow()
        FlowChip(
            label = "BUCKET",
            value = if (known) flow.bucket.toString() else "?",
            tint = if (known) AlgoViz.sorted else AlgoColors.textMuted,
        )
    }
}

@Composable
private fun FlowChip(label: String, value: String, tint: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = AlgoType.labelSmall,
            color = AlgoColors.textMuted,
            maxLines = 1,
            softWrap = false,
        )
        Text(
            text = value,
            style = AlgoType.numeralMedium,
            color = tint,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun FlowArrow() {
    Text(
        text = "→",
        style = AlgoType.titleMedium,
        color = AlgoColors.textMuted,
        modifier = Modifier.padding(horizontal = Spacing.sm),
    )
}

/**
 * One bucket: its number, a rule, and whatever it holds.
 *
 * An empty bucket says "empty" rather than being blank. Five rows of equal weight
 * is what makes the table read as *addressable space* — every bucket is always
 * there, whether or not anything has landed in it.
 */
@Composable
private fun BucketLine(row: BucketRow, selectable: Boolean, onSelect: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val outline by animateColorAsState(
        targetValue = when {
            selectable -> AlgoColors.primary.copy(alpha = 0.70f)
            row.state == BucketState.TARGET -> AlgoViz.comparing
            row.state == BucketState.COLLIDED -> AlgoViz.next
            else -> AlgoColors.border
        },
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "bucketOutline",
    )
    val fill by animateColorAsState(
        targetValue = when (row.state) {
            BucketState.TARGET -> AlgoColors.primarySoft
            else -> AlgoColors.surface
        },
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "bucketFill",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.bucketHeight)
            .background(fill, Radius.cell)
            .border(
                width = if (selectable) Dimens.outlineStrong else Dimens.outline,
                color = outline,
                shape = Radius.cell,
            )
            .then(
                if (selectable) {
                    Modifier.clickable(interaction, indication = null, onClick = onSelect)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.width(Dimens.bucketLabelWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "BUCKET",
                style = AlgoType.labelSmall,
                color = AlgoColors.textMuted,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                text = row.index.toString(),
                style = AlgoType.numeralMedium,
                color = if (row.state == BucketState.TARGET) {
                    AlgoColors.primary
                } else {
                    AlgoColors.textPrimary
                },
            )
        }

        Box(
            Modifier
                .width(Dimens.hairline)
                .height(Dimens.bucketRuleHeight)
                .background(AlgoColors.border),
        )
        Gap(Spacing.sm)

        if (row.entries.isEmpty()) {
            Text(
                text = "empty",
                style = AlgoType.bodyMedium,
                color = AlgoColors.textMuted,
                modifier = Modifier.weight(1f),
            )
        } else {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                row.entries.forEach { entry -> EntryChip(entry, chained = row.entries.size > 1) }
            }
        }
    }
}

/**
 * One `key → value` pair.
 *
 * When a bucket holds more than one, each entry gets a small leading tick — the
 * chain made visible. A collision has to look like *two things living together*,
 * never like an error.
 */
@Composable
private fun EntryChip(entry: EntryCell, chained: Boolean) {
    val tint = when (entry.state) {
        EntryState.MATCHED -> AlgoViz.sorted
        EntryState.SCANNING -> AlgoViz.comparing
        EntryState.LEAVING -> AlgoColors.textMuted
        EntryState.NEW -> AlgoColors.primary
        EntryState.IDLE -> AlgoColors.border
    }
    val ink = when (entry.state) {
        EntryState.MATCHED, EntryState.SCANNING -> AlgoColors.onPrimary
        EntryState.LEAVING -> AlgoColors.textMuted
        else -> AlgoColors.textPrimary
    }
    val background = when (entry.state) {
        EntryState.MATCHED -> AlgoViz.sorted
        EntryState.SCANNING -> AlgoViz.comparing
        else -> AlgoColors.surface
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (chained) {
            Text(
                text = "└",
                style = AlgoType.labelSmall,
                color = AlgoColors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(Dimens.chainTickWidth),
            )
        }
        Row(
            modifier = Modifier
                .background(background, Radius.pill)
                .border(Dimens.outline, tint, Radius.pill)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = entry.key.toString(),
                style = AlgoType.numeralMedium,
                color = ink,
                maxLines = 1,
            )
            Text(
                text = "  →  ",
                style = AlgoType.bodyMedium,
                color = ink.copy(alpha = 0.7f),
                maxLines = 1,
            )
            Text(
                text = entry.label,
                style = AlgoType.bodyLarge,
                color = ink,
                maxLines = 1,
            )
        }
    }
}
