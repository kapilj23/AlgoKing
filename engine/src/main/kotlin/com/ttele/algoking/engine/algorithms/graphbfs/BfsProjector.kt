package com.ttele.algoking.engine.algorithms.graphbfs

import com.ttele.algoking.engine.event.VizEvent
import com.ttele.algoking.engine.scene.CellState
import com.ttele.algoking.engine.scene.EdgeState
import com.ttele.algoking.engine.scene.GraphEdgeView
import com.ttele.algoking.engine.scene.GraphNodeView
import com.ttele.algoking.engine.scene.GraphScene
import com.ttele.algoking.engine.scene.SceneProjector

/**
 * BFS presentation knowledge — ARCHITECTURE.md §7.2.
 *
 * The same `GraphScene` DFS projects into, with the queue filled in. That is
 * deliberate: a learner who has done both should see the *algorithms* differ, not
 * the screens.
 *
 * Where DFS shows a stack — the one path it is standing on — BFS shows the queue,
 * because that is the structure driving it. Everything else is identical.
 *
 * ### Three node states, and the middle one is the lesson
 *
 * | means | is | reads |
 * |---|---|---|
 * | dequeued and processed | `FINALIZED` | Visited |
 * | seen and waiting in the queue | `CANDIDATE` | In queue |
 * | not seen at all | `IDLE` | Unvisited |
 *
 * `CANDIDATE` — amber, the state Selection Sort uses for a value it is *holding
 * on to* — is exactly right here: a queued node has been found but not yet
 * processed, and making that visible is what shows the frontier growing a level
 * at a time.
 */
class BfsProjector : SceneProjector<BfsState> {

    override fun project(state: BfsState, activeEvents: List<VizEvent>): GraphScene {
        val nodes = state.graph.nodes.mapIndexed { index, node ->
            GraphNodeView(
                slot = index,
                label = node.label,
                state = when {
                    node.id == state.current -> CellState.COMPARING
                    node.id in state.queue -> CellState.CANDIDATE
                    state.isVisited(node.id) -> CellState.FINALIZED
                    else -> CellState.IDLE
                },
                x = node.x,
                y = node.y,
            )
        }

        // The edges BFS actually used: each queued or processed node was reached
        // from exactly one place, and drawing those shows the tree the traversal
        // built, level by level.
        val discovered = discoveryEdges(state)

        val edges = state.graph.edges.map { (a, b) ->
            val used = (a to b) in discovered || (b to a) in discovered
            val touchingCurrent = state.current != null && (a == state.current || b == state.current)
            GraphEdgeView(
                from = state.graph.indexOf(a),
                to = state.graph.indexOf(b),
                state = when {
                    used && touchingCurrent -> EdgeState.ACTIVE
                    used -> EdgeState.PATH
                    else -> EdgeState.IDLE
                },
            )
        }

        return GraphScene(
            nodes = nodes,
            edges = edges,
            // The traversal is the DEQUEUE order — what BFS has processed, not
            // what it has merely seen. Those differ, and conflating them is the
            // most common way a BFS visual lies.
            traversal = state.dequeued.map { label(state, it) },
            stack = emptyList(),
            queue = state.queue.map { label(state, it) },
            pathLabel = "Queue",
            legendLabels = mapOf(
                CellState.COMPARING to "Current",
                CellState.CANDIDATE to "In queue",
                CellState.FINALIZED to "Visited",
                CellState.IDLE to "Unvisited",
            ),
        )
    }

    /**
     * The edge each node was discovered through.
     *
     * Rebuilt from visit order rather than stored: a node's discoverer is the
     * earliest-visited neighbour that was already seen when it arrived, which is
     * exactly how BFS found it.
     */
    private fun discoveryEdges(state: BfsState): Set<Pair<String, String>> = buildSet {
        val order = state.visited
        for ((index, node) in order.withIndex()) {
            if (index == 0) continue
            val discoverer = state.graph.neighbours(node)
                .filter { order.indexOf(it) in 0 until index }
                .minByOrNull { order.indexOf(it) }
            if (discoverer != null) add(discoverer to node)
        }
    }

    private fun label(state: BfsState, id: String): String =
        state.graph.node(id)?.label ?: id
}
