package com.algorithms.algoking.engine.algorithms.bellmanford

import com.algorithms.algoking.engine.core.Frame
import com.algorithms.algoking.engine.event.Metrics
import com.algorithms.algoking.engine.narration.NarrationId
import com.algorithms.algoking.engine.narration.NarrationKey
import com.algorithms.algoking.engine.scene.Scene
import com.algorithms.algoking.engine.walkthrough.PartialStep
import com.algorithms.algoking.engine.walkthrough.WatchNarrator
import com.algorithms.algoking.engine.walkthrough.WatchStepKind

/**
 * The Bellman–Ford walkthrough.
 *
 * One beat to open each pass, one per road actually compared, and one to end the
 * run — either "a pass changed nothing" or "the check pass found nothing to lower".
 * A road that starts at ∞, or gives exactly the distance already known, gets no
 * beat of its own: the list beside every beat marks it, and nothing moved.
 */
class BellmanFordWatchNarrator : WatchNarrator<BfState> {

    /** To redraw a beat around the road it is about, not the next one. */
    private val projector = BellmanFordProjector()

    override fun opening(state: BfState, scene: Scene): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.SETUP,
            scene = scene,
            headline = NarrationKey(NarrationId.BF_WATCH_SETUP, listOf(state.start)),
            support = NarrationKey(
                NarrationId.BF_WATCH_SETUP_SUPPORT,
                listOf(state.start, state.n, state.maxPasses),
            ),
            edgeList = state.readout(),
        ),
    )

    override fun onFrame(previous: BfState, frame: Frame<BfState>, scene: Scene): List<PartialStep> {
        val state = frame.state

        // -- A pass begins ----------------------------------------------------
        if (state.passStarted && !previous.passStarted) {
            return listOf(
                PartialStep(
                    kind = WatchStepKind.EXAMINE,
                    scene = scene,
                    headline = NarrationKey(
                        if (state.checking) NarrationId.BF_WATCH_CHECK else NarrationId.BF_WATCH_PASS,
                        listOf(state.pass, state.maxPasses),
                    ),
                    support = NarrationKey(
                        when {
                            state.checking -> NarrationId.BF_WATCH_CHECK_WHY
                            state.pass == 1 -> NarrationId.BF_WATCH_PASS_FIRST
                            else -> NarrationId.BF_WATCH_PASS_AGAIN
                        },
                        listOf(state.maxPasses),
                    ),
                    edgeList = state.readout(),
                ),
            )
        }

        // -- A road was compared ----------------------------------------------
        val edge = previous.current
        val decided = edge != null && !previous.checking && state.cursor == previous.cursor + 1 &&
            previous.candidate(edge) != null && previous.candidate(edge) != previous.d(edge.to)
        if (decided && edge != null) {
            val fromDist = previous.d(edge.from) ?: 0
            val candidate = fromDist + edge.weight
            val better = previous.improves(edge)
            val args = listOf(
                edge.from, edge.to, fromDist, weightLabel(edge.weight), candidate, bfLabel(previous.d(edge.to)),
            )
            return listOf(
                PartialStep(
                    kind = if (better) WatchStepKind.ELIMINATE else WatchStepKind.KEEP,
                    scene = projector.scene(state, focus = edge),
                    headline = NarrationKey(
                        if (better) NarrationId.BF_WATCH_UPDATE else NarrationId.BF_WATCH_KEEP,
                        args,
                    ),
                    support = NarrationKey(
                        when {
                            better && edge.weight < 0 -> NarrationId.BF_WATCH_UPDATE_NEGATIVE
                            better -> NarrationId.BF_WATCH_UPDATE_WHY
                            else -> NarrationId.BF_WATCH_KEEP_WHY
                        },
                        args,
                    ),
                    edgeList = state.readout(showNext = false),
                ),
            )
        }

        // -- The run ended ------------------------------------------------------
        if (state.done && !previous.done) {
            val id = when {
                state.negativeCycle -> NarrationId.BF_WATCH_CYCLE
                previous.checking -> NarrationId.BF_WATCH_DONE_CHECKED
                else -> NarrationId.BF_WATCH_DONE_EARLY
            }
            return listOf(
                PartialStep(
                    kind = if (state.negativeCycle) WatchStepKind.NOT_FOUND else WatchStepKind.FOUND,
                    scene = scene,
                    headline = NarrationKey(id, listOf(previous.pass)),
                    support = NarrationKey(
                        when {
                            state.negativeCycle -> NarrationId.BF_WATCH_CYCLE_WHY
                            previous.checking -> NarrationId.BF_WATCH_DONE_CHECKED_WHY
                            else -> NarrationId.BF_WATCH_DONE_EARLY_WHY
                        },
                        listOf(previous.pass),
                    ),
                    edgeList = previous.readout(showNext = false),
                ),
            )
        }

        return emptyList()
    }

    override fun closing(state: BfState, metrics: Metrics, scene: Scene): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.INSIGHT,
            scene = scene,
            headline = NarrationKey(NarrationId.BF_WATCH_INSIGHT),
            support = NarrationKey(NarrationId.BF_WATCH_INSIGHT_SUPPORT),
        ),
        PartialStep(
            kind = WatchStepKind.SUMMARY,
            scene = scene,
            headline = NarrationKey(
                NarrationId.BF_WATCH_SUMMARY,
                listOf(state.graph.ids.joinToString(",  ") { "$it ${bfLabel(state.d(it))}" }),
            ),
            support = NarrationKey(NarrationId.BF_WATCH_SUMMARY_SUPPORT),
            bullets = listOf(
                NarrationKey(NarrationId.BF_IDEA_1),
                NarrationKey(NarrationId.BF_IDEA_2),
                NarrationKey(NarrationId.BF_IDEA_3),
                NarrationKey(NarrationId.BF_IDEA_4),
                NarrationKey(NarrationId.BF_IDEA_5),
            ),
        ),
    )
}
