package com.ttele.algoking.engine.algorithms.avl

import com.ttele.algoking.engine.event.VizEvent
import com.ttele.algoking.engine.scene.CellState
import com.ttele.algoking.engine.scene.EdgeState
import com.ttele.algoking.engine.scene.GraphEdgeView
import com.ttele.algoking.engine.scene.GraphNodeView
import com.ttele.algoking.engine.scene.GraphScene
import com.ttele.algoking.engine.scene.SceneProjector

/**
 * AVL presentation knowledge — ARCHITECTURE.md §7.2.
 *
 * The same `GraphScene` the BST and the two graph lessons project into, because a
 * tree is still nodes at positions joined by edges (ADR-036). What this lesson
 * needs that none of them did is a **number on every node**: AVL is the first
 * lesson where the learner has to read a value the tree computes about itself.
 *
 * Three things have to be legible at once:
 *
 *  - **every balance factor**, as a caption, with the ones that have broken the
 *    ±1 rule marked. The learner cannot be asked to find the unbalanced node
 *    without being able to read the tree's own arithmetic;
 *  - **what just arrived** — the new node, `CANDIDATE` amber, because it is the
 *    thing whose arrival caused all of this;
 *  - **what is about to move** — the node named as out of balance is `COMPARING`,
 *    and the edges the rotation will re-hang are `ACTIVE`. A rotation is a change
 *    to *links*, so the links are what light up.
 *
 * There is no `ELIMINATED` here and no target badge: nothing is being searched
 * for, and nothing is ruled out. An AVL insert visits a path and repairs it.
 */
class AvlProjector : SceneProjector<AvlState> {

    override fun project(state: AvlState, activeEvents: List<VizEvent>): GraphScene {
        val positions = state.tree.layout()
        val slots = positions.withIndex().associate { (index, node) -> node.value to index }
        val factors = state.balanceFactors
        val unbalanced = state.unbalanced

        val nodes = positions.mapIndexed { index, node ->
            val factor = factors[node.value] ?: 0
            val broken = factor !in -1..1
            GraphNodeView(
                slot = index,
                label = node.value.toString(),
                state = when {
                    // Named as the one to fix. Only ever one node at a time, and
                    // it is the node every question on screen is about.
                    state.pivot == node.value -> CellState.COMPARING
                    state.justInserted == node.value -> CellState.CANDIDATE
                    else -> CellState.IDLE
                },
                x = node.x,
                y = node.y,
                // The tree's own arithmetic, on every node, always. Hiding it on
                // the balanced ones would turn "find the unbalanced node" into
                // "find the node with a number next to it".
                caption = signed(factor),
                captionAlert = broken,
            )
        }

        // The links the agreed rotation will re-hang, or — before the learner has
        // agreed one — the two steps the imbalance runs through, which is the
        // evidence they are being asked to read.
        val moving: Set<Pair<Int, Int>> = when {
            state.riser != null && state.pivot != null -> pathBetween(state, state.pivot, 2)
            state.pivot != null -> pathBetween(state, state.pivot, 2)
            else -> emptySet()
        }

        val edges = state.tree.edges().mapNotNull { (parent, child) ->
            val from = slots[parent] ?: return@mapNotNull null
            val to = slots[child] ?: return@mapNotNull null
            GraphEdgeView(
                from = from,
                to = to,
                state = when {
                    (parent to child) in moving -> EdgeState.ACTIVE
                    // The route the new value took to get here: how the tree grew
                    // taller, and therefore why anything is out of balance.
                    state.justInserted != null &&
                        (parent to child) in insertionPath(state) -> EdgeState.PATH

                    else -> EdgeState.IDLE
                },
            )
        }

        return GraphScene(
            nodes = nodes,
            edges = edges,
            // What has been put in so far, in the order it went in — the shape of
            // the tree is a consequence of this sequence, and on a plain BST the
            // very same sequence would have produced a much taller tree.
            traversal = state.tree.inorder().map { it.toString() },
            traversalLabel = "In order",
            stack = emptyList(),
            showPathStrip = false,
            legendLabels = mapOf(
                CellState.CANDIDATE to "Just added",
                CellState.COMPARING to if (unbalanced != null) "Out of balance" else "Rotating",
                CellState.IDLE to "In place",
            ),
        )
    }

    /** The first [steps] parent → child links from [from] toward the new value. */
    private fun pathBetween(state: AvlState, from: Int, steps: Int): Set<Pair<Int, Int>> {
        val inserted = state.justInserted ?: return emptySet()
        val path = state.tree.searchPath(inserted)
        val start = path.indexOf(from)
        if (start < 0) return emptySet()
        return path.drop(start)
            .take(steps + 1)
            .zipWithNext()
            .toSet()
    }

    private fun insertionPath(state: AvlState): Set<Pair<Int, Int>> {
        val inserted = state.justInserted ?: return emptySet()
        return state.tree.searchPath(inserted).zipWithNext().toSet()
    }

    private fun signed(factor: Int): String = if (factor > 0) "+$factor" else "$factor"
}
