package com.algorithms.algoking.engine.algorithms.topo

import com.algorithms.algoking.engine.event.VizEvent
import com.algorithms.algoking.engine.scene.CellState
import com.algorithms.algoking.engine.scene.EdgeState
import com.algorithms.algoking.engine.scene.GraphEdgeView
import com.algorithms.algoking.engine.scene.GraphNodeView
import com.algorithms.algoking.engine.scene.GraphScene
import com.algorithms.algoking.engine.scene.SceneProjector

/**
 * Topological sort presentation knowledge.
 *
 * Every unplaced node carries **the number of arrows still coming into it**, inside
 * the circle — the same place Dijkstra puts a distance. A placed node carries its
 * position in the order instead. Ready nodes are deliberately *not* coloured: the
 * 0 inside them is the signal, and finding it is the learner's job.
 *
 * | Means | Node | Arrows |
 * |---|---|---|
 * | just placed | `COMPARING` — its arrows orange, being removed | `OPTION` |
 * | placed earlier | `FINALIZED` — its arrows faded, already removed | `ELIMINATED` |
 * | not placed | `IDLE`, with its count | `IDLE` |
 */
class TopologicalSortProjector : SceneProjector<TopoState> {

    override fun project(state: TopoState, activeEvents: List<VizEvent>): GraphScene {
        val just = state.lastPlaced
        return GraphScene(
            nodes = state.graph.nodes.mapIndexed { index, node ->
                val at = state.placed.indexOf(node.id)
                GraphNodeView(
                    slot = index,
                    label = node.label,
                    state = when {
                        node.id == just && !state.done -> CellState.COMPARING
                        at >= 0 -> CellState.FINALIZED
                        else -> CellState.IDLE
                    },
                    x = node.x,
                    y = node.y,
                    secondaryLabel = if (at >= 0) "#${at + 1}" else state.arrowsIn(node.id).toString(),
                )
            },
            edges = state.edges.map { edge ->
                GraphEdgeView(
                    from = state.graph.indexOf(edge.from),
                    to = state.graph.indexOf(edge.to),
                    state = when {
                        edge.from == just && !state.done -> EdgeState.OPTION
                        edge.from in state.placed -> EdgeState.ELIMINATED
                        else -> EdgeState.IDLE
                    },
                )
            },
            traversal = state.placed,
            traversalLabel = "Order",
            stack = emptyList(),
            showPathStrip = false,
            directed = true,
            legendLabels = mapOf(
                CellState.COMPARING to "Just placed",
                CellState.FINALIZED to "Placed",
                CellState.IDLE to "Arrows still in",
            ),
        )
    }
}
