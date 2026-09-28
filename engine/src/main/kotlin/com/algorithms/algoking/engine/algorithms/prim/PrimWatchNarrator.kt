package com.algorithms.algoking.engine.algorithms.prim

import com.algorithms.algoking.engine.core.Frame
import com.algorithms.algoking.engine.event.Metrics
import com.algorithms.algoking.engine.narration.NarrationId
import com.algorithms.algoking.engine.narration.NarrationKey
import com.algorithms.algoking.engine.scene.Scene
import com.algorithms.algoking.engine.walkthrough.PartialStep
import com.algorithms.algoking.engine.walkthrough.WatchNarrator
import com.algorithms.algoking.engine.walkthrough.WatchStepKind

/**
 * The Prim walkthrough.
 *
 * One beat per node that joins, each carrying the full comparison — every edge
 * leaving the tree, and the cheapest — so no choice is ever a number that simply
 * appeared. Two things get a beat of their own because they are the two things
 * people get wrong:
 *
 *  - the join whose edge leaves from an **older** tree node rather than the newest
 *    one says so, in its own words;
 *  - an edge that has just become a **loop** — both ends now in the tree — is
 *    pointed at once, the moment it is ruled out, rather than silently dropped.
 */
class PrimWatchNarrator : WatchNarrator<PrimState> {

    override fun opening(state: PrimState, scene: Scene): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.SETUP,
            scene = scene,
            headline = NarrationKey(NarrationId.PRIM_WATCH_SETUP),
            support = NarrationKey(
                NarrationId.PRIM_WATCH_SETUP_SUPPORT,
                listOf(state.start, state.graph.nodes.size, state.graph.nodes.size - 1),
            ),
        ),
    )

    override fun onFrame(
        previous: PrimState,
        frame: Frame<PrimState>,
        scene: Scene,
    ): List<PartialStep> {
        val state = frame.state
        if (state.inTree.size == previous.inTree.size) return emptyList()

        val edge = state.treeEdges.last()
        val newest = previous.lastAdded
        val fromOlder = newest != null && edge.from != newest

        val steps = mutableListOf(
            PartialStep(
                kind = WatchStepKind.COMPARE,
                scene = scene,
                headline = NarrationKey(
                    NarrationId.PRIM_WATCH_ADD,
                    listOf(edge.to, edge.label, edge.weight),
                ),
                support = NarrationKey(
                    if (fromOlder) NarrationId.PRIM_WATCH_ADD_OLDER else NarrationId.PRIM_WATCH_ADD_WHY,
                    listOf(edge.from, newest.orEmpty(), state.total),
                ),
                // The comparison that picked it, from the state *before* the choice.
                cheapest = previous.cheapestReadout(),
            ),
        )

        if (state.finished) {
            steps += PartialStep(
                kind = WatchStepKind.FOUND,
                scene = scene,
                headline = NarrationKey(
                    NarrationId.PRIM_WATCH_DONE,
                    listOf(state.total, state.treeEdges.size, state.graph.nodes.size),
                ),
                support = NarrationKey(
                    NarrationId.PRIM_WATCH_DONE_WHY,
                    listOf(state.loops.joinToString(", ")),
                ),
            )
            return steps
        }

        // An edge that was a candidate a moment ago and now joins two tree nodes.
        val newLoops = state.loops - previous.loops.toSet()
        if (newLoops.isNotEmpty()) {
            steps += PartialStep(
                kind = WatchStepKind.ELIMINATE,
                scene = scene,
                headline = NarrationKey(
                    NarrationId.PRIM_WATCH_LOOP,
                    listOf(newLoops.joinToString(" and "), newLoops.size),
                ),
                support = NarrationKey(
                    NarrationId.PRIM_WATCH_LOOP_WHY,
                    listOf(newLoops.joinToString(" and "), newLoops.size),
                ),
            )
        }
        return steps
    }

    override fun closing(
        state: PrimState,
        metrics: Metrics,
        scene: Scene,
    ): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.INSIGHT,
            scene = scene,
            headline = NarrationKey(NarrationId.PRIM_WATCH_INSIGHT),
            support = NarrationKey(NarrationId.PRIM_WATCH_INSIGHT_SUPPORT),
        ),
        PartialStep(
            kind = WatchStepKind.SUMMARY,
            scene = scene,
            headline = NarrationKey(
                NarrationId.PRIM_WATCH_SUMMARY,
                listOf(state.treeEdges.joinToString(",  ") { "${it.label} (${it.weight})" }, state.total),
            ),
            support = NarrationKey(NarrationId.PRIM_WATCH_SUMMARY_SUPPORT),
            bullets = listOf(
                NarrationKey(NarrationId.PRIM_IDEA_1),
                NarrationKey(NarrationId.PRIM_IDEA_2),
                NarrationKey(NarrationId.PRIM_IDEA_3),
                NarrationKey(NarrationId.PRIM_IDEA_4),
                NarrationKey(NarrationId.PRIM_IDEA_5),
            ),
        ),
    )
}
