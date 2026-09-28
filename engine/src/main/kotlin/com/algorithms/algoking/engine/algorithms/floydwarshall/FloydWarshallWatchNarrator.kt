package com.algorithms.algoking.engine.algorithms.floydwarshall

import com.algorithms.algoking.engine.core.Frame
import com.algorithms.algoking.engine.event.Metrics
import com.algorithms.algoking.engine.narration.NarrationId
import com.algorithms.algoking.engine.narration.NarrationKey
import com.algorithms.algoking.engine.scene.Scene
import com.algorithms.algoking.engine.walkthrough.PartialStep
import com.algorithms.algoking.engine.walkthrough.WatchNarrator
import com.algorithms.algoking.engine.walkthrough.WatchStepKind

/**
 * The Floyd–Warshall walkthrough.
 *
 * One beat to open each round, and one per pair actually compared — an update or a
 * keep, each drawn with the two cards showing which won. A pair whose detour has
 * an ∞ leg gets no beat: nothing was compared, and the round's opening line says
 * why such pairs are passed over.
 *
 * The beat that matters most is the one whose detour uses a distance an **earlier
 * round** found rather than a road. It says so, because that is how a route
 * through several towns appears without anyone looking for one.
 */
class FloydWarshallWatchNarrator : WatchNarrator<FwState> {

    /** To redraw a beat around the pair it is about, rather than the next one. */
    private val projector = FloydWarshallProjector()

    override fun opening(state: FwState, scene: Scene): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.SETUP,
            scene = scene,
            headline = NarrationKey(NarrationId.FW_WATCH_SETUP),
            support = NarrationKey(NarrationId.FW_WATCH_SETUP_SUPPORT, listOf(state.n)),
        ),
    )

    override fun onFrame(
        previous: FwState,
        frame: Frame<FwState>,
        scene: Scene,
    ): List<PartialStep> {
        val state = frame.state

        // -- A round begins ---------------------------------------------------
        if (state.viaStarted && !previous.viaStarted) {
            return listOf(
                PartialStep(
                    kind = WatchStepKind.EXAMINE,
                    scene = scene,
                    headline = NarrationKey(
                        NarrationId.FW_WATCH_ROUND,
                        listOf(state.via + 1, state.viaNode.orEmpty()),
                    ),
                    support = NarrationKey(
                        if (state.via == 0) NarrationId.FW_WATCH_ROUND_FIRST else NarrationId.FW_WATCH_ROUND_WHY,
                        listOf(state.viaNode.orEmpty()),
                    ),
                ),
            )
        }

        // -- A pair was compared ----------------------------------------------
        val pair = previous.current
        if (pair != null && state.cursor == previous.cursor + 1 && state.via == previous.via &&
            previous.detour(pair.first, pair.second) != null
        ) {
            val (i, j) = pair
            val k = previous.via
            val a = previous.name(i)
            val b = previous.name(j)
            val via = previous.name(k)
            val legA = previous.d(i, k) ?: 0
            val legB = previous.d(k, j) ?: 0
            val through = legA + legB
            val nowLabel = distanceLabel(previous.d(i, j))
            val better = previous.improves(i, j)

            // A leg that is not a road was found in an earlier round.
            val builtLeg = listOf(i to k, k to j).firstOrNull { (x, y) ->
                previous.d(x, y) != previous.graph.weightOf(previous.name(x), previous.name(y))
            }
            val args = listOf(a, b, via, nowLabel, legA, legB, through)

            return listOf(
                PartialStep(
                    kind = if (better) WatchStepKind.ELIMINATE else WatchStepKind.KEEP,
                    scene = projector.scene(state, focus = pair, decided = true, before = previous),
                    headline = NarrationKey(
                        if (better) NarrationId.FW_WATCH_UPDATE else NarrationId.FW_WATCH_KEEP,
                        args,
                    ),
                    support = when {
                        better && builtLeg != null -> NarrationKey(
                            NarrationId.FW_WATCH_UPDATE_BUILT,
                            args + listOf(
                                previous.name(builtLeg.first),
                                previous.name(builtLeg.second),
                                previous.d(builtLeg.first, builtLeg.second) ?: 0,
                            ),
                        )

                        better -> NarrationKey(NarrationId.FW_WATCH_UPDATE_WHY, args)
                        else -> NarrationKey(NarrationId.FW_WATCH_KEEP_WHY, args)
                    },
                ),
            )
        }

        // -- The last round ended ---------------------------------------------
        if (state.finished && !previous.finished) {
            return listOf(
                PartialStep(
                    kind = WatchStepKind.FOUND,
                    scene = scene,
                    headline = NarrationKey(NarrationId.FW_WATCH_DONE),
                    support = NarrationKey(NarrationId.FW_WATCH_DONE_WHY, listOf(state.n)),
                ),
            )
        }

        // Passing over an ∞ detour, or closing a round: nothing visible changed.
        return emptyList()
    }

    override fun closing(state: FwState, metrics: Metrics, scene: Scene): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.INSIGHT,
            scene = scene,
            headline = NarrationKey(NarrationId.FW_WATCH_INSIGHT),
            support = NarrationKey(NarrationId.FW_WATCH_INSIGHT_SUPPORT),
        ),
        PartialStep(
            kind = WatchStepKind.SUMMARY,
            scene = scene,
            headline = NarrationKey(
                NarrationId.FW_WATCH_SUMMARY,
                listOf(state.n * (state.n - 1) / 2),
            ),
            support = NarrationKey(NarrationId.FW_WATCH_SUMMARY_SUPPORT),
            bullets = listOf(
                NarrationKey(NarrationId.FW_IDEA_1),
                NarrationKey(NarrationId.FW_IDEA_2),
                NarrationKey(NarrationId.FW_IDEA_3),
                NarrationKey(NarrationId.FW_IDEA_4),
                NarrationKey(NarrationId.FW_IDEA_5),
            ),
        ),
    )
}
