package com.algorithms.algoking.engine.algorithms.topo

import com.algorithms.algoking.engine.core.Frame
import com.algorithms.algoking.engine.event.Metrics
import com.algorithms.algoking.engine.narration.NarrationId
import com.algorithms.algoking.engine.narration.NarrationKey
import com.algorithms.algoking.engine.scene.Scene
import com.algorithms.algoking.engine.walkthrough.PartialStep
import com.algorithms.algoking.engine.walkthrough.WatchNarrator
import com.algorithms.algoking.engine.walkthrough.WatchStepKind

/**
 * The topological sort walkthrough: one beat per node placed, each saying why it
 * could go — alone, or the earliest of several — and which nodes its arrows were
 * the last thing holding back.
 */
class TopologicalSortWatchNarrator : WatchNarrator<TopoState> {

    override fun opening(state: TopoState, scene: Scene): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.SETUP,
            scene = scene,
            headline = NarrationKey(NarrationId.TS_WATCH_SETUP),
            support = NarrationKey(NarrationId.TS_WATCH_SETUP_SUPPORT),
            edgeList = state.readout(),
        ),
    )

    override fun onFrame(previous: TopoState, frame: Frame<TopoState>, scene: Scene): List<PartialStep> {
        val state = frame.state
        if (state.placed.size == previous.placed.size) return emptyList()

        val node = state.placed.last()
        val readyBefore = previous.ready
        val freed = previous.wouldFree(node)
        val args = listOf(node, readyBefore.joinToString(" and "), freed.joinToString(" and "), freed.size)

        val steps = mutableListOf(
            PartialStep(
                kind = WatchStepKind.COMPARE,
                scene = scene,
                headline = NarrationKey(NarrationId.TS_WATCH_PLACE, args),
                support = NarrationKey(
                    if (readyBefore.size > 1) NarrationId.TS_WATCH_PLACE_TIE else NarrationId.TS_WATCH_PLACE_WHY,
                    args,
                ),
                edgeList = state.readout(),
            ),
        )
        if (state.done) {
            steps += PartialStep(
                kind = WatchStepKind.FOUND,
                scene = scene,
                headline = NarrationKey(NarrationId.TS_WATCH_DONE, listOf(state.placed.joinToString(" → "))),
                support = NarrationKey(NarrationId.TS_WATCH_DONE_WHY),
            )
        } else if (state.stuck) {
            steps += PartialStep(
                kind = WatchStepKind.NOT_FOUND,
                scene = scene,
                headline = NarrationKey(NarrationId.TS_WATCH_CYCLE),
                support = NarrationKey(NarrationId.TS_WATCH_CYCLE_WHY),
            )
        }
        return steps
    }

    override fun closing(state: TopoState, metrics: Metrics, scene: Scene): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.INSIGHT,
            scene = scene,
            headline = NarrationKey(NarrationId.TS_WATCH_INSIGHT),
            support = NarrationKey(NarrationId.TS_WATCH_INSIGHT_SUPPORT),
        ),
        PartialStep(
            kind = WatchStepKind.SUMMARY,
            scene = scene,
            headline = NarrationKey(NarrationId.TS_WATCH_SUMMARY, listOf(state.placed.joinToString(" → "))),
            support = NarrationKey(NarrationId.TS_WATCH_SUMMARY_SUPPORT),
            bullets = listOf(
                NarrationKey(NarrationId.TS_IDEA_1),
                NarrationKey(NarrationId.TS_IDEA_2),
                NarrationKey(NarrationId.TS_IDEA_3),
                NarrationKey(NarrationId.TS_IDEA_4),
                NarrationKey(NarrationId.TS_IDEA_5),
            ),
        ),
    )
}
