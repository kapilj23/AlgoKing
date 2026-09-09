package com.ttele.algoking.engine.algorithms.dijkstra

import com.ttele.algoking.engine.event.VizEvent
import com.ttele.algoking.engine.scene.CellState
import com.ttele.algoking.engine.scene.EdgeState
import com.ttele.algoking.engine.scene.GraphEdgeView
import com.ttele.algoking.engine.scene.GraphNodeView
import com.ttele.algoking.engine.scene.GraphScene
import com.ttele.algoking.engine.scene.SceneProjector

/**
 * Dijkstra presentation knowledge — ARCHITECTURE.md §7.2.
 *
 * The same `GraphScene` and `GraphStage` DFS, BFS and the tree lessons use. Two
 * things are on screen here that no previous lesson needed, and both are the
 * algorithm rather than decoration:
 *
 *  - **a distance on every node**, `∞` until it is reached, drawn *inside* the
 *    circle. A layout spike at 360dp put it beside the node, the way AVL does,
 *    and two of the six landed on an edge — on a graph, the space around a node
 *    is where edges leave;
 *  - **a weight on every edge.** Without them the lesson is unteachable, and with
 *    them the midpoint of every edge in the teaching graph clears every circle.
 *
 * ### The four states, and what the amber one means
 *
 * | Means | State | Reads |
 * |---|---|---|
 * | being processed right now | `COMPARING` | Current |
 * | **reached, cheapest-so-far known, not settled** | `CANDIDATE` | Frontier |
 * | settled — its distance can no longer change | `FINALIZED` | Settled |
 * | not reached, still ∞ | `IDLE` | Unreached |
 *
 * The amber frontier *is* the priority queue. It is deliberately **not** also
 * drawn as a sorted strip: a list reading `C 2 · B 5` would answer the question
 * the lesson asks — *which node is cheapest?* — before the learner does
 * (ADR-030). BFS may show its queue because taking the front is not a judgement
 * there; here it is the whole rule.
 */
class DijkstraProjector : SceneProjector<DijkstraState> {

    override fun project(state: DijkstraState, activeEvents: List<VizEvent>): GraphScene {
        val nodes = state.graph.nodes.mapIndexed { index, node ->
            val settled = node.id in state.processed
            GraphNodeView(
                slot = index,
                label = node.label,
                state = when {
                    node.id == state.current -> CellState.COMPARING
                    settled -> CellState.FINALIZED
                    node.id in state.distances -> CellState.CANDIDATE
                    else -> CellState.IDLE
                },
                x = node.x,
                y = node.y,
                // Inside the circle, so it can never collide with an edge.
                secondaryLabel = state.distanceOf(node.id)?.toString() ?: "∞",
            )
        }

        // Once the run is over, only the route to the target stays lit — the
        // answer, rather than the working. Until then, the whole predecessor tree
        // is drawn, so the learner watches it re-route when a distance is beaten.
        val lit: Set<Pair<String, String>> = if (state.finished && !state.unreachable) {
            state.pathTo(state.target).zipWithNext().toSet()
        } else {
            state.routeEdges
        }
        val pending = state.pending

        val edges = state.graph.edges.map { (a, b) ->
            val isPending = pending != null &&
                ((pending.from == a && pending.to == b) || (pending.from == b && pending.to == a))
            GraphEdgeView(
                from = state.graph.indexOf(a),
                to = state.graph.indexOf(b),
                state = when {
                    isPending -> EdgeState.ACTIVE
                    (a to b) in lit || (b to a) in lit -> EdgeState.PATH
                    else -> EdgeState.IDLE
                },
                label = state.graph.weightOf(a, b)?.toString(),
            )
        }

        return GraphScene(
            nodes = nodes,
            edges = edges,
            // What has been settled, in the order it was settled — read straight
            // from state, so the strip and the algorithm cannot disagree.
            traversal = state.processed,
            traversalLabel = "Settled",
            // The best route to the target so far, which is how the answer is seen
            // to *emerge* rather than appear: it reads A → C → B → D → F at 12 and
            // then A → C → B → E → F at 9. Empty until the target is reached at
            // all, and the strip is hidden until then.
            stack = state.pathTo(state.target),
            pathLabel = if (state.finished) "Shortest path" else "Best route so far",
            showPathStrip = state.target in state.distances,
            legendLabels = mapOf(
                CellState.COMPARING to "Current",
                CellState.CANDIDATE to "Frontier",
                CellState.FINALIZED to "Settled",
                CellState.IDLE to "Unreached",
            ),
        )
    }
}
