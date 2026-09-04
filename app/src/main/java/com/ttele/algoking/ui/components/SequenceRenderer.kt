package com.ttele.algoking.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ttele.algoking.engine.event.PointerId
import com.ttele.algoking.engine.scene.Cell
import com.ttele.algoking.engine.scene.CellState
import com.ttele.algoking.engine.scene.BucketScene
import com.ttele.algoking.engine.scene.Scene
import com.ttele.algoking.engine.scene.SceneLayout
import com.ttele.algoking.engine.scene.SequenceScene
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
 * One renderer for every sequence algorithm — ARCHITECTURE.md §7.1.
 *
 * It receives a [SequenceScene] and cannot ask which algorithm produced it. The
 * visual hierarchy it enforces is the one the lesson needs to teach:
 *
 *   TARGET → CURRENT SEARCH RANGE → MIDDLE → ELIMINATED RANGE
 */
/**
 * The one entry point. A scene names its own shape, so this can dispatch without
 * ever learning which algorithm produced it.
 *
 * What a slot *means* is the shape's business: a cell in a row, a plate in a pile,
 * a link in a chain, a bucket in a table.
 */
@Composable
fun SceneRenderer(
    scene: Scene,
    modifier: Modifier = Modifier,
    selectableSlots: Set<Int> = emptySet(),
    onSelectSlot: (Int) -> Unit = {},
) {
    when (scene) {
        is SequenceScene -> SequenceRenderer(scene, modifier, selectableSlots, onSelectSlot)
        is BucketScene -> BucketTable(scene, modifier, selectableSlots, onSelectSlot)
    }
}

@Composable
fun SequenceRenderer(
    scene: SequenceScene,
    modifier: Modifier = Modifier,
    /** Slots the learner may tap. Empty means the array is read-only. */
    selectableSlots: Set<Int> = emptySet(),
    onSelectSlot: (Int) -> Unit = {},
) {
    // The scene declares its own shape, so the renderer still never asks which
    // algorithm produced it.
    when (scene.layout) {
        SceneLayout.PILE -> VerticalScene(scene, modifier, selectableSlots, onSelectSlot)
        SceneLayout.CHAIN -> ChainScene(scene, modifier, selectableSlots, onSelectSlot)
        SceneLayout.GRID -> GridScene(scene, modifier, selectableSlots, onSelectSlot)
        SceneLayout.ROW -> HorizontalScene(scene, modifier, selectableSlots, onSelectSlot)
    }
}

@Composable
private fun HorizontalScene(
    scene: SequenceScene,
    modifier: Modifier = Modifier,
    selectableSlots: Set<Int> = emptySet(),
    onSelectSlot: (Int) -> Unit = {},
) {
    val midSlot = scene.pointers.firstOrNull { it.pointer == PointerId.MID }?.slot
    val region = scene.regions.firstOrNull()?.range

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {

        // 1 — TARGET. A badge, never a colour: spending a viz hue on the target
        //     would break the legend contract.
        scene.badge?.let { badge ->
            Row(
                modifier = Modifier
                    .background(AlgoColors.primarySoft, Radius.pill)
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = badge.label.uppercase(),
                    style = AlgoType.labelSmall,
                    color = AlgoColors.primary,
                )
                Gap(Spacing.xs)
                Text(
                    text = badge.valueLabel ?: badge.value.toString(),
                    style = AlgoType.numeralMedium,
                    color = AlgoColors.primary,
                )
            }
            Gap(Spacing.sm)
        }

        // 2..4 — the stage. End caps flank it when the sequence has two live ends
        //        to name, and everything below them shares one column so the rails
        //        stay lined up with the cells.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            scene.endCaps?.leading?.let {
                EndCapLabel(it)
                Gap(Spacing.xs)
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                StageColumn(scene, midSlot, region, selectableSlots, onSelectSlot)
            }
            scene.endCaps?.trailing?.let {
                Gap(Spacing.xs)
                EndCapLabel(it)
            }
        }
    }
}

/** Sections 2 to 4 of the horizontal stage: swap arc, cells, and the pointer rail. */
@Composable
private fun ColumnScope.StageColumn(
    scene: SequenceScene,
    midSlot: Int?,
    region: IntRange?,
    selectableSlots: Set<Int>,
    onSelectSlot: (Int) -> Unit,
) {
    // 2 — The exchange, drawn over the pair that is swapping. Motion that is
    //     a consequence of a decision, never decoration.
    Box(Modifier.fillMaxWidth()) {
        scene.arc?.let { arc ->
            SwapArc(
                cellCount = scene.cells.size,
                from = arc.from,
                to = arc.to,
                modifier = Modifier.fillMaxWidth().height(Dimens.sceneRailHeight),
            )
        }
        SlotRow(cellCount = scene.cells.size) { slot ->
            if (slot == midSlot) {
                Column(
                    modifier = Modifier.wrapContentWidth(unbounded = true),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        // The scene names its own pointer; the renderer does not
                        // get to know that this one is called "mid".
                        text = scene.pointers
                            .firstOrNull { it.pointer == PointerId.MID }?.label.orEmpty(),
                        style = AlgoType.labelSmall,
                        color = AlgoColors.primary,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text("▾", style = AlgoType.labelSmall, color = AlgoColors.primary)
                }
            }
        }
    }

    // 3 — The cells, with the live search range banded behind them.
    if (scene.cells.isEmpty()) {
        // A queue that has been drained is still a queue. Drawing nothing would
        // read as a broken screen rather than as an empty structure.
        EmptyPlate()
        return
    }
    Box(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.sceneCellGap),
        ) {
            val boundaries = scene.groups
                .drop(1)
                .mapNotNull { it.firstOrNull() }
                .toSet()
            scene.cells.sortedBy { it.slot }.forEach { cell ->
                // A group boundary reads as a wider gap: the array is divided
                // here, and nothing crosses it until these pieces are merged.
                if (cell.slot in boundaries) {
                    Box(
                        Modifier
                            .width(Dimens.sceneGroupGap)
                            .height(Dimens.sceneCellHeight)
                            .padding(vertical = Spacing.xs)
                            .background(AlgoColors.borderStrong, Radius.pill),
                    )
                }
                SceneCell(
                    cell = cell,
                    inRange = region?.contains(cell.slot) ?: false,
                    selectable = cell.slot in selectableSlots,
                    onSelect = { onSelectSlot(cell.slot) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    // 4 — The positions. A lesson that computes an index cannot leave the
    //     indices off the screen, or its arithmetic names numbers the learner
    //     has no way to find. Positions outside the live range are dimmed
    //     rather than dropped: they still exist, they are just out of play.
    if (scene.showIndices) {
        Gap(Spacing.xxs)
        SlotRow(cellCount = scene.cells.size) { slot ->
            Text(
                text = "$slot",
                style = AlgoType.labelSmall,
                color = if (region?.contains(slot) != false) {
                    AlgoViz.indexLabel
                } else {
                    AlgoColors.disabled
                },
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }

    // 5 — The bounding pointers, drawn as arrows that point at the cell they
    //     name. A label floating under a row of twelve cells is ambiguous; an
    //     arrow is not.
    Gap(Spacing.xxs)
    SlotRow(cellCount = scene.cells.size) { slot ->
        val label = scene.pointers
            .filter { it.slot == slot && it.pointer != PointerId.MID }
            .joinToString(" ") { it.label }
        if (label.isNotEmpty()) {
            Column(
                // A slot is a twelfth of the width — about 30dp — and "right" does
                // not fit in it. The label is centred on its cell and the slots
                // beside it are almost always empty, so let it measure to its own
                // width and spill rather than lose its last letter.
                modifier = Modifier.wrapContentWidth(unbounded = true),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("▴", style = AlgoType.labelSmall, color = AlgoViz.pointer)
                Text(
                    text = label,
                    style = AlgoType.labelSmall,
                    color = AlgoViz.pointer,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** The label naming one end of a sequence — "OUT ←", "← IN". */
@Composable
private fun EndCapLabel(text: String) {
    Text(
        text = text,
        style = AlgoType.labelSmall,
        color = AlgoColors.textMuted,
        maxLines = 1,
        softWrap = false,
    )
}

/**
 * A vertical pile — DESIGN_SYSTEM.md §6.16c.
 *
 * A Stack is drawn the way a stack is: one reachable plate at the top, the rest
 * bearing its weight underneath, and a base line so the bottom is somewhere real
 * rather than the edge of the screen. Slot 0 is the top, which is what the
 * projector already guarantees.
 */
@Composable
private fun VerticalScene(
    scene: SequenceScene,
    modifier: Modifier = Modifier,
    selectableSlots: Set<Int> = emptySet(),
    onSelectSlot: (Int) -> Unit = {},
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {

        scene.badge?.let { badge ->
            Row(
                modifier = Modifier
                    .background(AlgoColors.primarySoft, Radius.pill)
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = badge.label.uppercase(),
                    style = AlgoType.labelSmall,
                    color = AlgoColors.primary,
                )
                Gap(Spacing.xs)
                Text(
                    text = badge.valueLabel ?: badge.value.toString(),
                    style = AlgoType.numeralMedium,
                    color = AlgoColors.primary,
                )
            }
            Gap(Spacing.sm)
        }

        scene.endCaps?.leading?.let {
            EndCapLabel(it)
            Gap(Spacing.xxs)
        }

        if (scene.cells.isEmpty()) {
            // Empty is a state worth drawing, not a blank. A learner who pops one
            // too many should see the space where the pile used to be.
            EmptyPlate()
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.stackCellGap),
            ) {
                scene.cells.sortedBy { it.slot }.forEach { cell ->
                    val label = scene.pointers
                        .filter { it.slot == cell.slot }
                        .joinToString(" ") { it.label }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SceneCell(
                            cell = cell,
                            inRange = false,
                            selectable = cell.slot in selectableSlots,
                            onSelect = { onSelectSlot(cell.slot) },
                            modifier = Modifier.width(Dimens.stackCellWidth),
                            height = Dimens.stackCellHeight,
                        )
                        if (label.isNotEmpty()) {
                            Gap(Spacing.xs)
                            Text(
                                text = label,
                                style = AlgoType.labelSmall,
                                color = AlgoColors.primary,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
        }

        // The base the pile rests on.
        Gap(Spacing.xxs)
        Box(
            Modifier
                .width(Dimens.stackCellWidth)
                .height(Dimens.outlineStrong)
                .background(AlgoColors.borderStrong, Radius.pill),
        )
        scene.endCaps?.trailing?.let {
            Gap(Spacing.xxs)
            EndCapLabel(it)
        }
    }
}

/** The outline of a plate that is not there. */
@Composable
private fun EmptyPlate() {
    Box(
        modifier = Modifier
            .width(Dimens.stackCellWidth)
            .height(Dimens.stackCellHeight)
            .border(
                width = Dimens.outline,
                color = AlgoColors.borderStrong,
                shape = Radius.sceneCell,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "empty",
            style = AlgoType.labelSmall,
            color = AlgoColors.textMuted,
        )
    }
}

/**
 * The exchange arc — DESIGN_SYSTEM.md §6.12.
 * A dashed hop from one slot to the other, so a swap reads as movement rather
 * than as two numbers blinking.
 */
@Composable
private fun SwapArc(cellCount: Int, from: Int, to: Int, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (cellCount <= 0) return@Canvas
        val gap = Dimens.sceneCellGap.toPx()
        val cell = (size.width - gap * (cellCount - 1)) / cellCount
        fun centerOf(slot: Int) = slot * (cell + gap) + cell / 2f

        val startX = centerOf(from)
        val endX = centerOf(to)
        val baseY = size.height - 1.dp.toPx()

        val path = Path().apply {
            moveTo(startX, baseY)
            quadraticTo((startX + endX) / 2f, -size.height * 0.7f, endX, baseY)
        }
        drawPath(
            path = path,
            color = AlgoViz.pointer,
            style = Stroke(
                width = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(6.dp.toPx(), 4.dp.toPx()),
                ),
            ),
        )

        val head = 5.dp.toPx()
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
            end = Offset(endX + dir * head * 0.35f, baseY - head * 1.3f),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/**
 * A row that lines one item up with each cell slot.
 *
 * The rail height is a *minimum*, not a cap: a pointer is an arrow stacked over
 * a word, and a fixed 20dp silently sliced the label off. Keeping the minimum
 * preserves the rhythm when a rail holds a single glyph.
 */
@Composable
private fun SlotRow(cellCount: Int, content: @Composable (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.sceneRailHeight),
        horizontalArrangement = Arrangement.spacedBy(Dimens.sceneCellGap),
    ) {
        repeat(cellCount) { slot ->
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                content(slot)
            }
        }
    }
}

/**
 * One cell. The state-to-treatment mapping is the whole visual vocabulary, and it
 * is identical to the legend printed under every array in the app.
 */
@Composable
private fun SceneCell(
    cell: Cell,
    inRange: Boolean,
    modifier: Modifier = Modifier,
    selectable: Boolean = false,
    onSelect: () -> Unit = {},
    height: Dp = Dimens.sceneCellHeight,
) {
    val interaction = remember { MutableInteractionSource() }
    val eliminated = cell.state == CellState.ELIMINATED
    val comparing = cell.state == CellState.COMPARING
    val finalized = cell.state == CellState.FINALIZED
    val candidate = cell.state == CellState.CANDIDATE
    // Insertion Sort lifts a value out and walks the hole left. A ghost cell is
    // empty space with an outline, never a number sitting in a box.
    val ghost = cell.state == CellState.GHOST

    // Eliminated cells collapse and desaturate — "half the search space, gone".
    val scale by animateFloatAsState(
        targetValue = if (eliminated) 0.82f else 1f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "cellScale",
    )
    val fade by animateFloatAsState(
        targetValue = if (eliminated) 0.45f else 1f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "cellFade",
    )
    val outline by animateColorAsState(
        targetValue = when {
            comparing -> AlgoViz.comparing
            candidate -> AlgoViz.next
            ghost -> AlgoColors.primary.copy(alpha = 0.45f)
            selectable -> AlgoColors.primary.copy(alpha = 0.70f)
            inRange -> AlgoColors.primary.copy(alpha = 0.35f)
            eliminated || finalized -> Color.Transparent
            // Still a cell, just no longer inside a live range.
            else -> AlgoColors.borderStrong
        },
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "cellOutline",
    )

    val fill: Brush = when {
        finalized -> AlgoGradients.bar(AlgoViz.sortedTop, AlgoViz.sortedBottom)
        comparing -> AlgoGradients.bar(AlgoViz.comparingTop, AlgoViz.comparingBottom)
        // The value the algorithm is remembering.
        candidate -> AlgoGradients.bar(AlgoViz.nextTop, AlgoViz.nextBottom)
        eliminated -> Brush.verticalGradient(
            listOf(AlgoViz.eliminated, AlgoViz.eliminated),
        )

        ghost -> Brush.verticalGradient(
            listOf(AlgoColors.primarySoft, AlgoColors.primarySoft),
        )

        inRange -> Brush.verticalGradient(listOf(AlgoColors.surface, AlgoColors.surface))
        else -> Brush.verticalGradient(listOf(AlgoColors.surface, AlgoColors.surface))
    }

    val ink = when {
        finalized || comparing || candidate -> AlgoColors.onPrimary
        eliminated -> AlgoColors.textMuted
        else -> AlgoColors.textPrimary
    }

    Box(
        modifier = modifier
            .height(height)
            .scale(scale)
            .alpha(fade)
            .then(
                if (comparing || finalized || candidate) {
                    Modifier.algoShadow(Elevation.raised, Radius.sceneCell)
                } else {
                    Modifier
                },
            )
            .background(fill, Radius.sceneCell)
            .border(
                width = if (selectable) Dimens.outlineStrong else Dimens.outline,
                color = outline,
                shape = Radius.sceneCell,
            )
            .then(
                if (selectable) {
                    Modifier.clickable(interaction, indication = null, onClick = onSelect)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        // A ghost renders as the gap it is: the old value is gone, not greyed.
        if (!ghost) {
            Text(
                text = cell.label ?: cell.value.toString(),
                style = AlgoType.sceneNumeral,
                color = ink,
                maxLines = 1,
            )
        }
    }
}

/** The scalar readouts a scene carries — "In range: 5". */
@Composable
fun SceneMeters(scene: Scene, modifier: Modifier = Modifier) {
    val meters = when (scene) {
        is SequenceScene -> scene.meters
        is BucketScene -> scene.meters
    }
    if (meters.isEmpty()) return
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        meters.forEach { meter ->
            Box(
                Modifier
                    .background(AlgoColors.surfaceVariant, Radius.pill)
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
            ) {
                Text(
                    text = "${meter.label} ${meter.value}",
                    style = AlgoType.labelSmall,
                    color = AlgoColors.textSecondary,
                )
            }
            Gap(Spacing.xs)
        }
    }
}

/**
 * The legend, derived from the scene.
 *
 * It names only the states actually on screen, and it never asks which algorithm
 * produced them — so Binary Search's eliminated halves and Bubble Sort's settled
 * suffix are described by the same four words in the same four colours.
 */
@Composable
fun SceneLegend(scene: Scene, modifier: Modifier = Modifier) {
    // A bucket table names its own states in the copy, so it needs no legend.
    if (scene !is SequenceScene) return
    val present = scene.cells.map { it.state }.toSet()
    fun name(state: CellState, fallback: String) = scene.legendLabels[state] ?: fallback
    val entries = buildList {
        if (CellState.COMPARING in present) add(AlgoViz.comparing to name(CellState.COMPARING, "Checking"))
        if (CellState.CANDIDATE in present) add(AlgoViz.next to name(CellState.CANDIDATE, "Smallest so far"))
        if (CellState.GHOST in present) add(AlgoColors.primarySoft to name(CellState.GHOST, "Gap"))
        if (CellState.IDLE in present) {
            add(AlgoColors.primary.copy(alpha = 0.35f) to name(CellState.IDLE, "In play"))
        }
        if (CellState.ELIMINATED in present) {
            add(AlgoViz.eliminated to name(CellState.ELIMINATED, "Eliminated"))
        }
        if (CellState.FINALIZED in present) add(AlgoViz.sorted to name(CellState.FINALIZED, "Final"))
    }
    if (entries.isEmpty()) return

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        entries.forEach { (color, label) -> LegendEntry(color, label) }
    }
}

@Composable
private fun LegendEntry(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(Dimens.legendSwatch)
                .background(color, Radius.swatch),
        )
        Gap(Spacing.xxs)
        Text(
            text = label,
            style = AlgoType.bodyMedium,
            color = AlgoColors.textSecondary,
            softWrap = false,
            maxLines = 1,
        )
    }
}

/**
 * A mission stage: real boxes, big enough to print a whole product id, wrapping
 * onto as many rows as they need.
 *
 * The row layout gives every cell a twelfth of the width, which is right when
 * the value is two digits and useless when it is `#4821`. Here the box keeps its
 * size and the layout gives way instead — a warehouse looks like a warehouse.
 *
 * There is no index rail and no `left`/`right` rail. Watch showed the arithmetic
 * and Try practises it; numbering the boxes would do the counting
 * for the learner. What a box does carry is its own pointer tag, because a
 * shared rail cannot point at something three rows down.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GridScene(
    scene: SequenceScene,
    modifier: Modifier = Modifier,
    selectableSlots: Set<Int> = emptySet(),
    onSelectSlot: (Int) -> Unit = {},
) {
    val region = scene.regions.firstOrNull()?.range

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {

        scene.badge?.let { badge ->
            Row(
                modifier = Modifier
                    .background(AlgoColors.primarySoft, Radius.pill)
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = badge.label.uppercase(),
                    style = AlgoType.labelSmall,
                    color = AlgoColors.primary,
                )
                Gap(Spacing.xs)
                Text(
                    text = badge.valueLabel ?: badge.value.toString(),
                    style = AlgoType.numeralMedium,
                    color = AlgoColors.primary,
                )
            }
            Gap(Spacing.md)
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(
                Dimens.missionBoxGap,
                Alignment.CenterHorizontally,
            ),
            verticalArrangement = Arrangement.spacedBy(Dimens.missionBoxGap),
        ) {
            scene.cells.sortedBy { it.slot }.forEach { cell ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SceneCell(
                        cell = cell,
                        inRange = region?.contains(cell.slot) ?: false,
                        selectable = cell.slot in selectableSlots,
                        onSelect = { onSelectSlot(cell.slot) },
                        modifier = Modifier.width(Dimens.missionBoxWidth),
                        height = Dimens.missionBoxHeight,
                    )
                    // The tag rides with its box rather than sitting on a rail,
                    // so it still points at the right one after a wrap.
                    val tag = scene.pointers
                        .filter { it.slot == cell.slot }
                        .joinToString(" · ") { it.label }
                    Gap(Spacing.xxs)
                    Text(
                        text = tag,
                        style = AlgoType.labelSmall,
                        color = AlgoViz.pointer,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}
