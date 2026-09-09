package com.ttele.algoking.engine.algorithms.bst

import com.ttele.algoking.engine.event.MarkId
import com.ttele.algoking.engine.event.VizEvent
import com.ttele.algoking.engine.scene.Badge
import com.ttele.algoking.engine.scene.CellState
import com.ttele.algoking.engine.scene.EdgeState
import com.ttele.algoking.engine.scene.GraphEdgeView
import com.ttele.algoking.engine.scene.GraphNodeView
import com.ttele.algoking.engine.scene.GraphScene
import com.ttele.algoking.engine.scene.SceneProjector

/**
 * BST presentation knowledge — ARCHITECTURE.md §7.2.
 *
 * A tree is nodes at positions joined by edges, which is exactly what
 * `GraphScene` already is — so BST reuses DFS and BFS's shape and their renderer
 * rather than introducing a fifth one. What is genuinely new is not the picture's
 * *shape* but one of its states: a search **rules parts of the structure out**,
 * and a traversal never does.
 *
 * Four things have to be legible at once, and they are the four things the lesson
 * is about:
 *
 *  - **where the search is standing** — the current node, `COMPARING`, and the
 *    only node scaled up;
 *  - **how it got there** — the path, `FINALIZED`, joined by violet edges;
 *  - **what it will never look at** — ruled-out subtrees, `ELIMINATED`, still
 *    drawn and visibly out of play. This is the payoff: at the end of the
 *    teaching run four of seven nodes were never touched, and you can see it;
 *  - **what it is looking for** — the target, as a badge rather than a colour,
 *    the way every other search lesson shows it.
 *
 * The positions come from [com.ttele.algoking.engine.core.BinaryTree.layout],
 * which derives them from the shape of the tree. Nothing here authors a
 * coordinate, so a different tree draws itself correctly with no new code.
 */
class BstProjector : SceneProjector<BstState> {

    override fun project(state: BstState, activeEvents: List<VizEvent>): GraphScene {
        val positions = state.tree.layout()
        val slots = positions.withIndex().associate { (index, node) -> node.value to index }

        val nodes = positions.mapIndexed { index, node ->
            GraphNodeView(
                slot = index,
                label = node.value.toString(),
                state = stateOf(state, node.value),
                x = node.x,
                y = node.y,
            )
        }

        // The edge the search has just come down, so the picture says which way it
        // moved rather than leaving the learner to diff two frames.
        val lastStep: Pair<Int, Int>? = state.path
            .takeIf { it.size >= 2 }
            ?.let { it[it.lastIndex - 1] to it.last() }
        val pathEdges = state.path.zipWithNext().toSet()

        val edges = state.tree.edges().mapNotNull { (parent, child) ->
            val from = slots[parent] ?: return@mapNotNull null
            val to = slots[child] ?: return@mapNotNull null
            GraphEdgeView(
                from = from,
                to = to,
                state = when {
                    // A branch into a ruled-out subtree is not a branch any more.
                    child in state.eliminated || parent in state.eliminated ->
                        EdgeState.ELIMINATED

                    (parent to child) == lastStep -> EdgeState.ACTIVE
                    (parent to child) in pathEdges -> EdgeState.PATH
                    else -> EdgeState.IDLE
                },
            )
        }

        return GraphScene(
            nodes = nodes,
            edges = edges,
            // Read straight from engine state, so the strip and the algorithm
            // cannot disagree about where the search has been.
            traversal = state.path.map { it.toString() },
            // A search is not a traversal: it follows one path and stops, and
            // calling it what it is, is half of the point being made.
            traversalLabel = "Search path",
            // No stack, no queue. A BST search is driven by the tree itself, and a
            // second strip repeating the path above it would be a line of text
            // pretending to be a data structure.
            stack = emptyList(),
            showPathStrip = false,
            badge = Badge(MarkId.TARGET, "Target", state.target),
            // Four states are on screen at once here, which is one more than any
            // other lesson, so each label is a word or two: the swatches carry
            // the meaning and the strip above already says "search path".
            // "Current" and "In play" are the words DFS and Binary Search use.
            legendLabels = mapOf(
                CellState.COMPARING to "Current",
                CellState.FINALIZED to "Path",
                CellState.ELIMINATED to "Ruled out",
                CellState.IDLE to "In play",
            ),
        )
    }

    private fun stateOf(state: BstState, value: Int): CellState = when {
        // The answer, and the strongest thing on the screen once it exists.
        state.foundValue == value -> CellState.FINALIZED
        state.current == value -> CellState.COMPARING
        // Ruled out beats visited: a node the search passed through can never be
        // in a discarded subtree, so the two never actually collide — but stating
        // the order makes that a property of the code rather than a coincidence.
        value in state.eliminated -> CellState.ELIMINATED
        value in state.path -> CellState.FINALIZED
        else -> CellState.IDLE
    }
}
