package com.algorithms.algoking.engine.algorithms.kruskal

import com.algorithms.algoking.engine.event.MarkId
import com.algorithms.algoking.engine.event.VizEvent
import com.algorithms.algoking.engine.scene.Badge
import com.algorithms.algoking.engine.scene.CellState
import com.algorithms.algoking.engine.scene.EdgeState
import com.algorithms.algoking.engine.scene.GraphEdgeView
import com.algorithms.algoking.engine.scene.GraphNodeView
import com.algorithms.algoking.engine.scene.GraphScene
import com.algorithms.algoking.engine.scene.SceneProjector

/**
 * Kruskal presentation knowledge — the same `GraphScene` and colours as Prim, so a
 * learner who has done one reads the other without a legend:
 *
 * | Means | Edge state |
 * |---|---|
 * | taken — part of the tree | `TREE` (green) |
 * | the edge being decided right now | `OPTION` (orange) |
 * | skipped — it would only have made a loop | `ELIMINATED` |
 * | not looked at yet | `IDLE` |
 *
 * | Means | Node state | Reads |
 * |---|---|---|
 * | an end of the edge being decided | `CANDIDATE` | Checking |
 * | joined to something by a taken edge | `FINALIZED` | Joined |
 * | still on its own | `IDLE` | On its own |
 *
 * The node colours cannot say *which* group a node is in — two separate green
 * pieces look alike — so the groups are spelled out in the edge-list readout,
 * which is where the take-or-skip question is actually answered.
 */
class KruskalProjector : SceneProjector<KruskalState> {

    override fun project(state: KruskalState, activeEvents: List<VizEvent>): GraphScene =
        scene(state, focus = state.next)

    /**
     * The picture with [focus] as the edge being talked about — orange, with its two
     * ends marked "Checking".
     *
     * Try focuses the edge it is asking about, which is [KruskalState.next]. Watch
     * narrates an edge *after* it was decided, when `next` has already moved on, so
     * it focuses the decided edge instead: otherwise the words would be about B – C
     * while the picture lit D – E.
     */
    fun scene(state: KruskalState, focus: KruskalEdge?): GraphScene {
        val current = focus
        val checking = current?.let { setOf(it.a, it.b) }.orEmpty()
        val joined = state.taken.flatMap { listOf(it.a, it.b) }.toSet()

        val nodes = state.graph.nodes.mapIndexed { index, node ->
            GraphNodeView(
                slot = index,
                label = node.label,
                state = when (node.id) {
                    in checking -> CellState.CANDIDATE
                    in joined -> CellState.FINALIZED
                    else -> CellState.IDLE
                },
                x = node.x,
                y = node.y,
            )
        }

        val taken = state.taken.map { setOf(it.a, it.b) }.toSet()
        val skipped = state.skipped.map { setOf(it.a, it.b) }.toSet()
        val next = current?.let { setOf(it.a, it.b) }

        val edges = state.graph.edges.map { (a, b) ->
            val key = setOf(a, b)
            GraphEdgeView(
                from = state.graph.indexOf(a),
                to = state.graph.indexOf(b),
                state = when (key) {
                    // A taken edge stays green even when it is the focus; its ends
                    // turning orange is what marks it.
                    in taken -> EdgeState.TREE
                    next -> EdgeState.OPTION
                    in skipped -> EdgeState.ELIMINATED
                    else -> EdgeState.IDLE
                },
                label = state.graph.weightOf(a, b)?.toString(),
            )
        }

        return GraphScene(
            nodes = nodes,
            edges = edges,
            // The edges taken, in the order they were taken.
            traversal = state.taken.map { it.label },
            traversalLabel = "Taken",
            stack = emptyList(),
            showPathStrip = false,
            badge = Badge(mark = MarkId.BEST, label = "TOTAL COST", value = state.total),
            legendLabels = mapOf(
                CellState.CANDIDATE to "Checking",
                CellState.FINALIZED to "Joined",
                CellState.IDLE to "On its own",
            ),
        )
    }
}
