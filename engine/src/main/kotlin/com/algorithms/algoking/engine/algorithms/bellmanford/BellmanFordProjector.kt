package com.algorithms.algoking.engine.algorithms.bellmanford

import com.algorithms.algoking.engine.core.DirectedEdge
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
 * Bellman–Ford presentation knowledge: Dijkstra's picture — a distance inside every
 * node, ∞ until it is reached — with one-way arrows, because a negative road only
 * makes sense in one direction.
 *
 * | Means | Node state | Reads |
 * |---|---|---|
 * | the start of the road being looked at | `COMPARING` | From |
 * | the end of it | `CANDIDATE` | To |
 * | has a distance | `FINALIZED` | Has a distance |
 * | still ∞ | `IDLE` | Still ∞ |
 *
 * The road being looked at is orange; the roads on the best routes so far are
 * green, so the learner watches the route tree re-route when a distance drops.
 */
class BellmanFordProjector : SceneProjector<BfState> {

    override fun project(state: BfState, activeEvents: List<VizEvent>): GraphScene =
        scene(state, focus = state.current)

    /** The picture around [focus]; Watch passes the road it is narrating. */
    fun scene(state: BfState, focus: DirectedEdge?): GraphScene {
        val routeEdges = state.pred.map { (to, from) -> from to to }.toSet()
        return GraphScene(
            nodes = state.graph.nodes.mapIndexed { index, node ->
                GraphNodeView(
                    slot = index,
                    label = node.label,
                    state = when {
                        focus != null && node.id == focus.from -> CellState.COMPARING
                        focus != null && node.id == focus.to -> CellState.CANDIDATE
                        state.d(node.id) != null -> CellState.FINALIZED
                        else -> CellState.IDLE
                    },
                    x = node.x,
                    y = node.y,
                    secondaryLabel = bfLabel(state.d(node.id)),
                )
            },
            edges = state.edges.map { edge ->
                GraphEdgeView(
                    from = state.graph.indexOf(edge.from),
                    to = state.graph.indexOf(edge.to),
                    state = when {
                        edge == focus -> EdgeState.OPTION
                        (edge.from to edge.to) in routeEdges -> EdgeState.TREE
                        else -> EdgeState.IDLE
                    },
                    label = weightLabel(edge.weight),
                )
            },
            traversal = emptyList(),
            stack = emptyList(),
            showPathStrip = false,
            showStrips = false,
            directed = true,
            badge = Badge(
                mark = MarkId.BEST,
                label = "PASS",
                value = state.pass,
                valueLabel = when {
                    state.done && state.negativeCycle -> "NEGATIVE CYCLE"
                    state.done -> "DONE"
                    state.checking -> "CHECK"
                    else -> "${state.pass} of ${state.maxPasses}"
                },
            ),
            legendLabels = mapOf(
                CellState.COMPARING to "From",
                CellState.CANDIDATE to "To",
                CellState.FINALIZED to "Has a distance",
                CellState.IDLE to "Still ∞",
            ),
        )
    }
}
