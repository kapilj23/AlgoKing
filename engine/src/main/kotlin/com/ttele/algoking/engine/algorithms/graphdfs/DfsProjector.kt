package com.ttele.algoking.engine.algorithms.graphdfs

import com.ttele.algoking.engine.event.VizEvent
import com.ttele.algoking.engine.scene.CellState
import com.ttele.algoking.engine.scene.EdgeState
import com.ttele.algoking.engine.scene.GraphEdgeView
import com.ttele.algoking.engine.scene.GraphNodeView
import com.ttele.algoking.engine.scene.GraphScene
import com.ttele.algoking.engine.scene.SceneProjector

/**
 * DFS presentation knowledge — ARCHITECTURE.md §7.2.
 *
 * Three things have to be legible at once, and they are the three things the
 * learner is being asked about:
 *
 *  - **where DFS is standing** — the current node, `COMPARING`;
 *  - **where it has been** — visited nodes, `FINALIZED`, and the traversal strip;
 *  - **how it got there** — the stack, drawn as `PATH` edges, which is the route a
 *    backtrack will unwind.
 *
 * The state names are the ones every other lesson uses, so the legend and the
 * colours come out of the design system unchanged.
 */
class DfsProjector : SceneProjector<DfsState> {

    override fun project(state: DfsState, activeEvents: List<VizEvent>): GraphScene {
        val nodes = state.graph.nodes.mapIndexed { index, node ->
            GraphNodeView(
                slot = index,
                label = node.label,
                state = when {
                    node.id == state.current -> CellState.COMPARING
                    state.isVisited(node.id) -> CellState.FINALIZED
                    else -> CellState.IDLE
                },
                x = node.x,
                y = node.y,
            )
        }

        // The stack as a set of edges: consecutive pairs on the path.
        val pathEdges = state.stack.zipWithNext().toSet()

        // The step just taken, so the picture says which way DFS moved rather than
        // leaving the learner to diff two frames.
        val lastEdge: Pair<String, String>? = when {
            state.backtracked -> null
            state.stack.size >= 2 -> state.stack[state.stack.lastIndex - 1] to
                state.stack.last()

            else -> null
        }

        val edges = state.graph.edges.map { (a, b) ->
            val onPath = (a to b) in pathEdges || (b to a) in pathEdges
            GraphEdgeView(
                from = state.graph.indexOf(a),
                to = state.graph.indexOf(b),
                state = when {
                    lastEdge != null &&
                        (a to b == lastEdge || b to a == lastEdge) -> EdgeState.ACTIVE
                    // A backtrack unwinds the edge that is no longer on the path
                    // but joins the node just left to the one just returned to.
                    state.backtracked && isJustUnwound(state, a, b) -> EdgeState.BACKTRACK
                    onPath -> EdgeState.PATH
                    else -> EdgeState.IDLE
                },
            )
        }

        return GraphScene(
            nodes = nodes,
            edges = edges,
            traversal = state.visited.map { id -> state.graph.node(id)?.label ?: id },
            stack = state.stack.map { id -> state.graph.node(id)?.label ?: id },
            legendLabels = mapOf(
                CellState.COMPARING to "Current",
                CellState.FINALIZED to "Visited",
                CellState.IDLE to "Unvisited",
            ),
        )
    }

    /**
     * The edge a backtrack just came up.
     *
     * After the stack pops, the node DFS left is no longer on it, so the edge is
     * identified as *the one joining the new current node to a visited node that
     * is not on the path any more*.
     */
    private fun isJustUnwound(state: DfsState, a: String, b: String): Boolean {
        val current = state.current ?: return false
        val other = when (current) {
            a -> b
            b -> a
            else -> return false
        }
        return state.isVisited(other) && other !in state.stack
    }
}
