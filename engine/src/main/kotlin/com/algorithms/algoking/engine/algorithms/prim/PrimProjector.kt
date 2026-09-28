package com.algorithms.algoking.engine.algorithms.prim

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
 * Prim presentation knowledge — ARCHITECTURE.md §7.2.
 *
 * The same `GraphScene` Dijkstra draws, with every piece of it meaning one thing:
 *
 * | Means | Node state | Reads |
 * |---|---|---|
 * | joined the tree just now | `COMPARING` | Just added |
 * | in the tree | `FINALIZED` | In tree |
 * | an edge from the tree reaches it | `CANDIDATE` | Can join |
 * | nothing from the tree reaches it yet | `IDLE` | Not reached |
 *
 * | Means | Edge state |
 * |---|---|
 * | chosen — part of the tree | `TREE` (green) |
 * | leaving the tree — one of the edges being compared | `OPTION` (orange) |
 * | both ends in the tree — it would only make a loop | `ELIMINATED` |
 *
 * So the edges being compared are always the lit ones, and an edge the algorithm
 * will never use is visibly crossed out rather than silently ignored.
 */
class PrimProjector : SceneProjector<PrimState> {

    override fun project(state: PrimState, activeEvents: List<VizEvent>): GraphScene {
        val reachable = state.crossing.map { it.to }.toSet()
        val done = state.finished

        val nodes = state.graph.nodes.mapIndexed { index, node ->
            GraphNodeView(
                slot = index,
                label = node.label,
                state = when {
                    node.id == state.lastAdded && !done -> CellState.COMPARING
                    node.id in state.inTree -> CellState.FINALIZED
                    node.id in reachable -> CellState.CANDIDATE
                    else -> CellState.IDLE
                },
                x = node.x,
                y = node.y,
            )
        }

        val chosen = state.treeEdges.map { setOf(it.from, it.to) }.toSet()
        val crossing = state.crossing.map { setOf(it.from, it.to) }.toSet()

        val edges = state.graph.edges.map { (a, b) ->
            val key = setOf(a, b)
            GraphEdgeView(
                from = state.graph.indexOf(a),
                to = state.graph.indexOf(b),
                state = when {
                    key in chosen -> EdgeState.TREE
                    key in crossing && !done -> EdgeState.OPTION
                    a in state.inTree && b in state.inTree -> EdgeState.ELIMINATED
                    else -> EdgeState.IDLE
                },
                label = state.graph.weightOf(a, b)?.toString(),
            )
        }

        return GraphScene(
            nodes = nodes,
            edges = edges,
            // The order nodes joined, read straight from state.
            traversal = state.inTree,
            traversalLabel = "In tree",
            stack = emptyList(),
            showPathStrip = false,
            // What the tree costs so far — the number the whole lesson minimises.
            badge = Badge(mark = MarkId.BEST, label = "TOTAL COST", value = state.total),
            legendLabels = mapOf(
                CellState.COMPARING to "Just added",
                CellState.FINALIZED to "In tree",
                CellState.CANDIDATE to "Can join",
                CellState.IDLE to "Not reached",
            ),
        )
    }
}
