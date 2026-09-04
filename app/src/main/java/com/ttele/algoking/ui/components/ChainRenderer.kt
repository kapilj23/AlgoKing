package com.ttele.algoking.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.ttele.algoking.engine.scene.Cell
import com.ttele.algoking.engine.scene.CellState
import com.ttele.algoking.engine.scene.LinkState
import com.ttele.algoking.engine.scene.SequenceScene
import com.ttele.algoking.ui.theme.AlgoColors
import com.ttele.algoking.ui.theme.AlgoType
import com.ttele.algoking.ui.theme.AlgoViz
import com.ttele.algoking.ui.theme.Dimens
import com.ttele.algoking.ui.theme.Radius
import com.ttele.algoking.ui.theme.Spacing

/**
 * The chain — DESIGN_SYSTEM.md §6.16d.
 *
 * Every other stage in AlgoKing draws a row whose *positions* carry the meaning.
 * Here the positions carry nothing, and the app has to say so: the arrows are the
 * loudest thing on screen, each node wears its NEXT pointer as a visible
 * compartment, and the row is bracketed by HEAD and NULL so the chain has a start
 * and a provable end.
 *
 * The learner taps **arrows**, not boxes. `selectableSlots` therefore indexes links
 * — link `i` sits before node `i`, and link `n` is the arrow to NULL — which is the
 * whole reason a linked list gets a layout of its own.
 */
@Composable
fun ChainScene(
    scene: SequenceScene,
    modifier: Modifier = Modifier,
    selectableSlots: Set<Int> = emptySet(),
    onSelectSlot: (Int) -> Unit = {},
) {
    val cells = scene.cells.sortedBy { it.slot }
    val linkState = { slot: Int ->
        scene.links.firstOrNull { it.slot == slot }?.state ?: LinkState.SETTLED
    }

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.Top,
        ) {
            Gap(Spacing.xxs)
            EntryLabel(scene.endCaps?.leading ?: "HEAD")

            // Link 0 is HEAD's own arrow, so the very first thing after the label is
            // an arrow — which is what makes "HEAD points at the first node" a
            // picture rather than a sentence.
            for (slot in cells.indices) {
                LinkArrow(
                    state = linkState(slot),
                    selectable = slot in selectableSlots,
                    detachedValue = scene.detached?.takeIf { it.atLink == slot }?.value,
                    onSelect = { onSelectSlot(slot) },
                )
                NodeBox(cells[slot])
            }

            LinkArrow(
                state = linkState(cells.size),
                selectable = cells.size in selectableSlots,
                detachedValue = scene.detached?.takeIf { it.atLink == cells.size }?.value,
                onSelect = { onSelectSlot(cells.size) },
            )
            TerminatorLabel(scene.endCaps?.trailing ?: "NULL")
            Gap(Spacing.xxs)
        }

        if (cells.isEmpty()) {
            Gap(Spacing.xs)
            Text(
                text = "HEAD points to NULL — the list is empty.",
                style = AlgoType.bodyMedium,
                color = AlgoColors.textMuted,
            )
        }
    }
}

/** HEAD: a label with a downward tick, so it reads as a pointer and not a node. */
@Composable
private fun EntryLabel(text: String) {
    Column(
        modifier = Modifier.height(Dimens.chainRowHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            style = AlgoType.labelSmall,
            color = AlgoColors.primary,
            maxLines = 1,
            softWrap = false,
        )
        Text("▾", style = AlgoType.labelSmall, color = AlgoColors.primary)
    }
}

/** NULL: deliberately not a box. There is no node here; that is the point. */
@Composable
private fun TerminatorLabel(text: String) {
    Box(
        modifier = Modifier.height(Dimens.chainRowHeight),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = AlgoType.labelSmall,
            color = AlgoColors.textMuted,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * One node: a value compartment and a NEXT compartment, divided.
 *
 * The divider is the lesson. A node is not a number in a slot — it is a value
 * *plus* a reference, and the arrow that leaves the node leaves from the second
 * compartment, where the reference actually lives.
 */
@Composable
private fun NodeBox(cell: Cell) {
    val comparing = cell.state == CellState.COMPARING
    val leaving = cell.state == CellState.ELIMINATED
    val found = cell.state == CellState.FINALIZED

    val fade by animateFloatAsState(
        targetValue = if (leaving) 0.45f else 1f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "nodeFade",
    )
    val outline by animateColorAsState(
        targetValue = when {
            found -> AlgoViz.sorted
            comparing -> AlgoViz.comparing
            leaving -> AlgoViz.eliminated
            else -> AlgoColors.borderStrong
        },
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "nodeOutline",
    )
    val fill = when {
        found -> AlgoViz.sorted
        comparing -> AlgoViz.comparing
        else -> AlgoColors.surface
    }
    val ink = if (found || comparing) AlgoColors.onPrimary else AlgoColors.textPrimary

    Box(
        modifier = Modifier
            .height(Dimens.chainRowHeight),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .height(Dimens.chainNodeHeight)
                .background(fill, Radius.sceneCell)
                .border(Dimens.outline, outline, Radius.sceneCell),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(Dimens.chainValueWidth)
                    .height(Dimens.chainNodeHeight),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = cell.value.toString(),
                    style = AlgoType.sceneNumeral,
                    color = ink,
                    maxLines = 1,
                )
            }
            Box(
                Modifier
                    .width(Dimens.hairline)
                    .height(Dimens.chainNodeHeight)
                    .padding(vertical = Spacing.xxs)
                    .background(outline.copy(alpha = 0.5f)),
            )
            // The NEXT compartment. Small, always present, and the place every
            // arrow starts from.
            Box(
                modifier = Modifier
                    .width(Dimens.chainNextWidth)
                    .height(Dimens.chainNodeHeight),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(Dimens.chainNextDot)
                        .background(
                            if (found || comparing) {
                                AlgoColors.onPrimary.copy(alpha = 0.85f)
                            } else {
                                AlgoColors.primary
                            },
                            Radius.pill,
                        ),
                )
            }
        }
        if (fade < 1f) {
            // A node on its way out keeps its shape but loses its weight.
            Box(
                Modifier
                    .height(Dimens.chainNodeHeight)
                    .width(Dimens.chainValueWidth + Dimens.chainNextWidth)
                    .background(AlgoColors.background.copy(alpha = 1f - fade), Radius.sceneCell),
            )
        }
    }
}

/**
 * One arrow, and the tap target that goes with it.
 *
 * A cut link is drawn dashed with a gap in the middle — the list *is* broken at that
 * moment, and pretending otherwise would hide the only thing insertion and deletion
 * have in common.
 */
@Composable
private fun LinkArrow(
    state: LinkState,
    selectable: Boolean,
    detachedValue: Int?,
    onSelect: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val colour by animateColorAsState(
        targetValue = when {
            selectable -> AlgoColors.primary
            state == LinkState.OPEN -> AlgoColors.warning
            state == LinkState.NEW -> AlgoViz.sorted
            state == LinkState.ACTIVE -> AlgoColors.primary
            else -> AlgoColors.borderStrong
        },
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "linkColour",
    )

    Column(
        modifier = Modifier
            .width(if (detachedValue != null) Dimens.chainGapWide else Dimens.chainGap)
            .height(Dimens.chainRowHeight)
            .then(
                if (selectable) {
                    Modifier.clickable(interaction, indication = null, onClick = onSelect)
                } else {
                    Modifier
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // The node that exists but is not connected yet, hovering over its gap.
        if (detachedValue != null) {
            Box(
                modifier = Modifier
                    .height(Dimens.chainDetachedHeight)
                    .width(Dimens.chainGapWide)
                    .background(AlgoColors.primarySoft, Radius.sceneCell)
                    .border(Dimens.outlineStrong, AlgoColors.primary, Radius.sceneCell),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = detachedValue.toString(),
                    style = AlgoType.sceneNumeral,
                    color = AlgoColors.primary,
                    maxLines = 1,
                )
            }
            Gap(Spacing.xxs)
        }

        Arrow(colour = colour, cut = state == LinkState.OPEN, emphatic = selectable)

        // A tappable link says so. Without this the learner has no way to know the
        // arrows are the interactive part.
        if (selectable) {
            Text(
                text = "tap",
                style = AlgoType.labelSmall,
                color = AlgoColors.primary,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun Arrow(colour: Color, cut: Boolean, emphatic: Boolean) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(Dimens.chainArrowHeight),
    ) {
        val y = size.height / 2f
        val stroke = if (emphatic) 3.dp.toPx() else 2.dp.toPx()
        val head = 5.dp.toPx()
        val endX = size.width - 1.dp.toPx()

        if (cut) {
            // Two stubs and a visible gap: the chain is genuinely open here.
            val gap = size.width * 0.30f
            drawLine(
                color = colour,
                start = Offset(0f, y),
                end = Offset(size.width / 2f - gap / 2f, y),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
            )
            drawLine(
                color = colour,
                start = Offset(size.width / 2f + gap / 2f, y),
                end = Offset(endX, y),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
            )
        } else {
            drawLine(
                color = colour,
                start = Offset(0f, y),
                end = Offset(endX, y),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }

        drawLine(
            color = colour,
            start = Offset(endX, y),
            end = Offset(endX - head, y - head * 0.8f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = colour,
            start = Offset(endX, y),
            end = Offset(endX - head, y + head * 0.8f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}
