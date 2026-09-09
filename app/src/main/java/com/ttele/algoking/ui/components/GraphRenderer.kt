package com.ttele.algoking.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.Canvas
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ttele.algoking.engine.scene.CellState
import com.ttele.algoking.engine.scene.EdgeState
import com.ttele.algoking.engine.scene.GraphNodeView
import com.ttele.algoking.engine.scene.GraphScene
import com.ttele.algoking.ui.theme.AlgoColors
import androidx.compose.ui.graphics.Brush
import com.ttele.algoking.ui.theme.AlgoType
import com.ttele.algoking.ui.theme.AlgoViz
import com.ttele.algoking.ui.theme.Dimens
import com.ttele.algoking.ui.theme.Radius
import com.ttele.algoking.ui.theme.Spacing

/**
 * A graph — DESIGN_SYSTEM.md §6.16g.
 *
 * The fourth scene shape and the first that is two-dimensional. Node positions
 * arrive normalised 0..1 and are scaled to whatever width the card gives, so the
 * scene never learns anything about dp.
 *
 * Three things have to be readable at once, and they are the three things the
 * learner is being asked about: **where DFS is standing**, **where it has been**,
 * and **how it got there**. Edges carry the third — the path DFS came down is
 * drawn solid violet, so a backtrack visibly unwinds a line that is already on
 * screen rather than teleporting.
 *
 * Node colours are the same viz tokens every other lesson uses, via the same
 * [CellState] values, so nothing here forks the visual language.
 */
@Composable
fun GraphStage(
    scene: GraphScene,
    modifier: Modifier = Modifier,
    selectableSlots: Set<Int> = emptySet(),
    onSelectSlot: (Int) -> Unit = {},
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {

        // TARGET, when the lesson is a search. A badge, never a colour — the same
        // treatment every other search lesson gives it, so spending a viz hue on
        // the target cannot break the legend contract (DESIGN_SYSTEM.md §6.16b).
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

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.graphStageHeight),
        ) {
            val width = maxWidth
            val height = maxHeight
            // Inset so a node centred at x = 0 or 1 still has its whole circle and
            // its tap target inside the box.
            val inset = Dimens.graphNode / 2 + Spacing.xs

            fun px(node: GraphNodeView): Pair<Dp, Dp> {
                val x = inset + (width - inset * 2) * node.x
                val y = inset + (height - inset * 2) * node.y
                return x to y
            }

            // 1 — Edges, under the nodes.
            Canvas(Modifier.fillMaxWidth().height(height)) {
                val insetPx = inset.toPx()
                fun point(node: GraphNodeView) = Offset(
                    x = insetPx + (size.width - insetPx * 2) * node.x,
                    y = insetPx + (size.height - insetPx * 2) * node.y,
                )
                scene.edges.forEach { edge ->
                    val a = scene.nodes.getOrNull(edge.from) ?: return@forEach
                    val b = scene.nodes.getOrNull(edge.to) ?: return@forEach
                    drawEdge(point(a), point(b), edge.state)
                }
            }

            // 2 — Edge weights, at the midpoint, on a surface pill.
            //
            //     Drawn as `Text` rather than into the Canvas above: ARCHITECTURE
            //     §10.5 keeps text out of `Canvas` entirely. A layout spike at
            //     360dp confirmed every midpoint on the weighted teaching graph
            //     clears every node circle, so a plain midpoint needs no offset.
            scene.edges.forEach { edge ->
                val label = edge.label ?: return@forEach
                val a = scene.nodes.getOrNull(edge.from) ?: return@forEach
                val b = scene.nodes.getOrNull(edge.to) ?: return@forEach
                val (ax, ay) = px(a)
                val (bx, by) = px(b)
                EdgeWeight(
                    label = label,
                    active = edge.state == EdgeState.ACTIVE,
                    modifier = Modifier.offset(
                        x = (ax + bx) / 2 - Dimens.edgeLabel / 2,
                        y = (ay + by) / 2 - Dimens.edgeLabelHeight / 2,
                    ),
                )
            }

            // 3 — Nodes, tappable.
            scene.nodes.forEach { node ->
                val (x, y) = px(node)
                GraphNodeCircle(
                    node = node,
                    selectable = node.slot in selectableSlots,
                    onSelect = { onSelectSlot(node.slot) },
                    modifier = Modifier.offset(
                        x = x - Dimens.graphNode / 2,
                        y = y - Dimens.graphNode / 2,
                    ),
                )
                // 4 — What the node knows about itself, above it: AVL's balance
                //     factor. Drawn after the circles so a caption is never
                //     covered by a neighbouring node, and outside the circle so
                //     it cannot be mistaken for the node's value.
                node.caption?.let { caption ->
                    Text(
                        text = caption,
                        style = AlgoType.labelSmall,
                        color = if (node.captionAlert) {
                            AlgoColors.secondary
                        } else {
                            AlgoColors.textMuted
                        },
                        modifier = Modifier
                            .offset(
                                x = x + Dimens.graphNode / 2 - Spacing.sm,
                                y = y - Dimens.graphNode / 2 - Spacing.sm,
                            )
                            .background(AlgoColors.surface, Radius.pill)
                            .padding(horizontal = Spacing.xxs),
                    )
                }
            }
        }

        Gap(Spacing.sm)
        TraversalStrip(scene)
    }
}

/**
 * The edge treatments.
 *
 * `PATH` is the call stack made visible — the route DFS came down and the route a
 * backtrack will unwind. `BACKTRACK` is dashed, because the step it describes is
 * a retreat rather than progress, and it is the half of DFS learners lose.
 */
private fun DrawScope.drawEdge(from: Offset, to: Offset, state: EdgeState) {
    val (color, width, dashed) = when (state) {
        EdgeState.IDLE -> Triple(AlgoColors.borderStrong, 2.dp, false)
        EdgeState.PATH -> Triple(AlgoViz.pointer, 3.dp, false)
        EdgeState.ACTIVE -> Triple(AlgoViz.comparing, 4.dp, false)
        EdgeState.BACKTRACK -> Triple(AlgoColors.secondary, 3.dp, true)
        // A branch into a ruled-out subtree. Still drawn — the structure did not
        // change — but visibly no longer a route the algorithm can take.
        EdgeState.ELIMINATED -> Triple(AlgoColors.border, 2.dp, false)
    }
    drawLine(
        color = color,
        start = from,
        end = to,
        strokeWidth = width.toPx(),
        cap = StrokeCap.Round,
        pathEffect = if (dashed) {
            PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
        } else {
            null
        },
    )
}

@Composable
private fun GraphNodeCircle(
    node: GraphNodeView,
    selectable: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val current = node.state == CellState.COMPARING
    val visited = node.state == CellState.FINALIZED

    // Seen but not processed yet — BFS's frontier, sitting in the queue. Amber,
    // the same token the queue cells use, so a node and its queue cell are
    // obviously the same thing. DFS never produces this state.
    val queued = node.state == CellState.CANDIDATE

    // Ruled out by a comparison — a search discards whole regions of a structure,
    // and a traversal never does. The node stays exactly where it is, because it
    // has not gone anywhere: it is simply somewhere the algorithm has proved it
    // does not need to look (DESIGN_SYSTEM.md §6.16b — the collapse is what makes
    // "a whole subtree, gone" legible).
    val eliminated = node.state == CellState.ELIMINATED
    val filled = current || visited || queued

    // The current node lifts slightly, and a ruled-out node settles back. It is
    // the one thing on screen the learner is reasoning from, and in a 2-D picture
    // position alone does not say so.
    val scale by animateFloatAsState(
        targetValue = when {
            current -> 1.08f
            // Less than a cell's 0.82: a two-digit numeral inside a circle stops
            // being readable before a numeral in a box does.
            eliminated -> 0.88f
            else -> 1f
        },
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "graphNodeScale",
    )
    val fill by animateColorAsState(
        targetValue = when {
            current -> AlgoViz.comparing
            visited -> AlgoViz.sorted
            queued -> AlgoViz.next
            eliminated -> AlgoViz.eliminated
            else -> AlgoColors.surface
        },
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "graphNodeFill",
    )

    Box(
        modifier = modifier
            .size(Dimens.graphNode)
            .scale(scale)
            .alpha(if (eliminated) AlgoViz.eliminatedAlpha else 1f)
            .then(
                if (filled) {
                    Modifier.background(
                        brush = Brush.verticalGradient(
                            when {
                                current -> listOf(
                                    AlgoViz.comparingTop,
                                    AlgoViz.comparingBottom,
                                )

                                queued -> listOf(AlgoViz.nextTop, AlgoViz.nextBottom)
                                else -> listOf(AlgoViz.sortedTop, AlgoViz.sortedBottom)
                            },
                        ),
                        shape = CircleShape,
                    )
                } else {
                    Modifier.background(fill, CircleShape)
                },
            )
            .border(
                BorderStroke(
                    width = if (selectable) 2.5.dp else Dimens.hairline * 1.5f,
                    color = when {
                        selectable -> AlgoViz.pointer
                        filled -> Color.Transparent
                        // Out of play, so it loses the violet "still possible"
                        // outline: the outline is the thing that says a node is
                        // still a candidate.
                        eliminated -> Color.Transparent
                        // The same "still in play" outline an idle cell carries in
                        // every other lesson, so the legend swatch matches the node.
                        else -> AlgoColors.primary.copy(alpha = 0.35f)
                    },
                ),
                CircleShape,
            )
            .then(
                if (selectable) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onSelect,
                    )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        val ink = when {
            filled -> Color.White
            eliminated -> AlgoColors.textMuted
            else -> AlgoColors.textPrimary
        }
        // Held locally: `secondaryLabel` crosses the module boundary, so it cannot
        // be smart-cast at the use site.
        val secondary = node.secondaryLabel
        if (secondary == null) {
            Text(
                text = node.label,
                style = AlgoType.numeralMedium,
                color = ink,
                textAlign = TextAlign.Center,
            )
        } else {
            // Two lines inside the circle: the name, and what the node knows
            // about itself. Dijkstra's tentative distance goes here rather than
            // beside the node, because on a graph the space around a node is
            // where its edges leave — see GraphNodeView.secondaryLabel.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = node.label,
                    style = AlgoType.titleSmall,
                    color = ink,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = secondary,
                    style = AlgoType.labelSmall,
                    color = if (filled) Color.White.copy(alpha = 0.92f) else AlgoColors.textMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * What one edge costs, drawn at its midpoint.
 *
 * A `surface` pill so it stays legible over the line it labels, and it takes the
 * `comparing` violet while its edge is the one being relaxed — so the weight the
 * arithmetic is about is the weight that is lit.
 */
@Composable
private fun EdgeWeight(label: String, active: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(Dimens.edgeLabel)
            .height(Dimens.edgeLabelHeight)
            .background(AlgoColors.surface, Radius.pill)
            .border(
                BorderStroke(
                    width = if (active) 1.5.dp else Dimens.hairline,
                    color = if (active) AlgoViz.comparing else AlgoColors.border,
                ),
                Radius.pill,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = AlgoType.labelSmall,
            color = if (active) AlgoViz.comparing else AlgoColors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The traversal so far, and the path DFS is standing on.
 *
 * Both come straight from engine state: the traversal *is* the visited list, and
 * the stack *is* the call stack. Neither is assembled here, which is what stops
 * the display and the algorithm ever disagreeing.
 */
@Composable
private fun TraversalStrip(scene: GraphScene) {
    Column(Modifier.fillMaxWidth()) {
        StripLine(
            // A traversal for DFS and BFS, a search path for BST — the scene says
            // which, because "traversal" would be a false claim about a walk that
            // deliberately never visits most of the structure.
            caption = scene.traversalLabel,
            value = scene.traversal.takeIf { it.isNotEmpty() }
                ?.joinToString("  →  ") ?: "—",
            emphasis = true,
        )
        // A lesson driven by a structure the learner has to watch shows it. A BST
        // search is driven by the tree itself, so it turns the second strip off
        // rather than repeating the line above it under another name.
        if (!scene.showPathStrip) return@Column
        Gap(Spacing.xxs)
        // A stack for DFS, a queue for BFS. Only one is ever populated, so the
        // strip shows whichever structure is actually driving the lesson.
        if (scene.stack.isNotEmpty()) {
            StripLine(
                caption = scene.pathLabel,
                value = scene.stack.joinToString("  ›  "),
                emphasis = false,
            )
        } else {
            QueueStrip(scene)
        }
    }
}

/**
 * The queue, drawn as cells with its two ends named.
 *
 * BFS *is* the queue, so it gets a picture rather than a line of text. FRONT is
 * where nodes leave and REAR is where they join, and watching B and C sit there
 * while D and E line up behind them is what makes level-order visible — the same
 * `OUT ←` / `← IN` language the Queue lesson already uses (DESIGN_SYSTEM §6.16).
 */
@Composable
private fun QueueStrip(scene: GraphScene) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AlgoColors.surfaceVariant, Radius.card)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = scene.pathLabel.uppercase(),
            style = AlgoType.labelSmall,
            color = AlgoColors.textMuted,
        )
        if (scene.queue.isEmpty()) {
            // An empty queue is the termination condition, not a blank.
            Text(
                text = "empty",
                style = AlgoType.bodyMedium,
                color = AlgoColors.textMuted,
            )
            return@Row
        }
        Text("OUT ←", style = AlgoType.labelSmall, color = AlgoColors.textMuted)
        scene.queue.forEach { label ->
            Box(
                modifier = Modifier
                    .background(AlgoViz.next, Radius.cell)
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
            ) {
                Text(
                    text = label,
                    style = AlgoType.titleSmall,
                    color = Color.White,
                )
            }
        }
        Text("← IN", style = AlgoType.labelSmall, color = AlgoColors.textMuted)
    }
}

@Composable
private fun StripLine(caption: String, value: String, emphasis: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (emphasis) AlgoColors.primarySoft else AlgoColors.surfaceVariant,
                Radius.card,
            )
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = caption.uppercase(),
            style = AlgoType.labelSmall,
            color = AlgoColors.textMuted,
        )
        Text(
            text = value,
            style = AlgoType.titleSmall,
            color = if (emphasis) AlgoColors.primary else AlgoColors.textSecondary,
        )
    }
}
