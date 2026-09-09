package com.ttele.algoking.engine.algorithms.traversal

import com.ttele.algoking.engine.event.VizEvent
import com.ttele.algoking.engine.scene.CellState
import com.ttele.algoking.engine.scene.EdgeState
import com.ttele.algoking.engine.scene.GraphEdgeView
import com.ttele.algoking.engine.scene.GraphNodeView
import com.ttele.algoking.engine.scene.GraphScene
import com.ttele.algoking.engine.scene.SceneProjector

/**
 * Traversal presentation knowledge — ARCHITECTURE.md §7.2.
 *
 * One projector for all three lessons, because the picture genuinely is the same
 * picture: the tree, where the traversal is standing, what it has emitted, and
 * the call stack. The only thing that differs is what the output strip is
 * **called**, which is [label] — and that is the point. Three lessons that looked
 * different would let a learner attribute the different orders to the screen
 * rather than to the rule.
 *
 * ### The four states, and why the middle one carries the lesson
 *
 * | Means | State | Reads |
 * |---|---|---|
 * | where the traversal is standing | `COMPARING` | Current |
 * | **reached, waiting on the stack, not emitted** | `CANDIDATE` | Waiting |
 * | emitted to the output | `FINALIZED` | Visited |
 * | not reached | `IDLE` | Not reached |
 *
 * `CANDIDATE` — the amber Selection Sort uses for a value it is *holding on to* —
 * is what makes the three traversals visibly different on identical data:
 *
 *  - **postorder** leaves a trail of amber parents that only turn green on the way
 *    back up, which is "children first, parent last" as a picture;
 *  - **preorder** turns a node green the moment it is reached, so the green grows
 *    downward ahead of the walk;
 *  - **inorder** does both, and the amber node sitting between a finished left
 *    subtree and an untouched right one is exactly the beat being taught.
 *
 * Current is checked before visited, so the node the learner is reasoning from
 * stays the violet one even in preorder, where it has just been emitted.
 */
class TraversalProjector(private val label: String) : SceneProjector<TreeWalkState> {

    override fun project(state: TreeWalkState, activeEvents: List<VizEvent>): GraphScene {
        val positions = state.tree.layout()
        val slots = positions.withIndex().associate { (index, node) -> node.value to index }

        val nodes = positions.mapIndexed { index, node ->
            GraphNodeView(
                slot = index,
                label = node.value.toString(),
                state = when {
                    node.value == state.current -> CellState.COMPARING
                    state.isVisited(node.value) -> CellState.FINALIZED
                    state.isPending(node.value) -> CellState.CANDIDATE
                    else -> CellState.IDLE
                },
                x = node.x,
                y = node.y,
            )
        }

        // The stack drawn as edges: the route from the root down to where the
        // traversal is standing. It is the call stack, and it is what a return
        // visibly unwinds rather than teleports out of.
        val onStack = state.stack.map { it.node }.zipWithNext().toSet()
        val justMoved: Pair<Int, Int>? = when (val move = state.lastMove) {
            is TraversalMove.Descend -> move.from to move.to
            is TraversalMove.Return -> move.to?.let { it to move.from }
            else -> null
        }

        val edges = state.tree.edges().mapNotNull { (parent, child) ->
            val from = slots[parent] ?: return@mapNotNull null
            val to = slots[child] ?: return@mapNotNull null
            GraphEdgeView(
                from = from,
                to = to,
                state = when {
                    (parent to child) == justMoved -> EdgeState.ACTIVE
                    (parent to child) in onStack -> EdgeState.PATH
                    else -> EdgeState.IDLE
                },
            )
        }

        return GraphScene(
            nodes = nodes,
            edges = edges,
            // The output, and the lesson's product. Read straight from state, so
            // the strip and the algorithm cannot disagree.
            traversal = state.visited.map { it.toString() },
            traversalLabel = label,
            // The call stack, supporting rather than leading: the tree, the
            // current node and the order are the picture; this explains a return.
            stack = state.stack.map { it.node.toString() },
            pathLabel = "Stack",
            showPathStrip = true,
            legendLabels = mapOf(
                CellState.COMPARING to "Current",
                CellState.CANDIDATE to "Waiting",
                CellState.FINALIZED to "Visited",
                CellState.IDLE to "Not reached",
            ),
        )
    }
}
