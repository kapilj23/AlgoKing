package com.ttele.algoking.engine.algorithms.linkedlist

import com.ttele.algoking.engine.event.MarkId
import com.ttele.algoking.engine.event.PointerId
import com.ttele.algoking.engine.event.VizEvent
import com.ttele.algoking.engine.scene.Badge
import com.ttele.algoking.engine.scene.Cell
import com.ttele.algoking.engine.scene.CellState
import com.ttele.algoking.engine.scene.DetachedNode
import com.ttele.algoking.engine.scene.EndCaps
import com.ttele.algoking.engine.scene.Link
import com.ttele.algoking.engine.scene.LinkState
import com.ttele.algoking.engine.scene.PointerMark
import com.ttele.algoking.engine.scene.SceneLayout
import com.ttele.algoking.engine.scene.SceneProjector
import com.ttele.algoking.engine.scene.SequenceScene

/**
 * Draws a linked list.
 *
 * Every other lesson in AlgoKing draws a row of values whose *positions* carry the
 * meaning. Here the positions carry nothing: the meaning is in the arrows, so the
 * arrows are what the scene is mostly made of. That is why a chain is a layout of
 * its own rather than a row with decoration on top.
 */
class LinkedListProjector : SceneProjector<LinkedListState> {

    override fun project(
        state: LinkedListState,
        activeEvents: List<VizEvent>,
    ): SequenceScene {
        val doomed = doomedIndex(state)

        val cells = state.nodes.mapIndexed { index, node ->
            Cell(
                key = node.id,
                value = node.value,
                slot = index,
                state = when {
                    index == doomed -> CellState.ELIMINATED
                    state.found && index == state.cursor -> CellState.FINALIZED
                    index == state.cursor -> CellState.COMPARING
                    else -> CellState.IDLE
                },
            )
        }

        return SequenceScene(
            cells = cells,
            layout = SceneLayout.CHAIN,
            links = links(state, doomed),
            detached = state.detached?.let { node ->
                DetachedNode(node.value, requireNotNull(state.openLink))
            },
            pointers = state.cursor
                ?.takeIf { it in state.nodes.indices && state.phase == ListPhase.WALKING }
                ?.let { listOf(PointerMark(PointerId.I, it, "here")) }
                .orEmpty(),
            badge = (state.task as? ListTask.Find)
                ?.let { Badge(MarkId.TARGET, "Looking for", it.target) },
            // HEAD is where you enter, NULL is how you know you are out. Both are
            // labels rather than nodes, because neither holds a value.
            endCaps = EndCaps("HEAD", "NULL"),
            legendLabels = mapOf(
                // "In play" is array wording. These are nodes.
                CellState.IDLE to "Node",
                CellState.COMPARING to "Here",
                CellState.ELIMINATED to "Leaving",
                CellState.FINALIZED to "Found",
            ),
        )
    }

    /** The node on its way out, so it can be drawn as detaching rather than gone. */
    private fun doomedIndex(state: LinkedListState): Int? =
        state.openLink.takeIf { state.phase == ListPhase.RECONNECTING }

    /**
     * One arrow per gap, `0..nodes.size`.
     *
     * The states are the operation, told as a picture: the arrow you are following
     * is ACTIVE, the arrow you have cut is OPEN, and the arrow you just made is NEW.
     */
    private fun links(state: LinkedListState, doomed: Int?): List<Link> =
        (0 until state.linkCount).map { slot ->
            Link(
                slot = slot,
                state = when {
                    slot == state.openLink -> LinkState.OPEN
                    // The node is leaving, so the arrow out of it is going too.
                    doomed != null && slot == doomed + 1 -> LinkState.OPEN
                    slot == state.changedLink && state.phase == ListPhase.SETTLED -> LinkState.NEW
                    state.phase == ListPhase.WALKING && slot == state.cursor -> LinkState.ACTIVE
                    else -> LinkState.SETTLED
                },
            )
        }
}
