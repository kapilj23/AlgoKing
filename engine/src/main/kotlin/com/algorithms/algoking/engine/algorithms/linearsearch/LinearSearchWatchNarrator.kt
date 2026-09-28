package com.algorithms.algoking.engine.algorithms.linearsearch

import com.algorithms.algoking.engine.core.Frame
import com.algorithms.algoking.engine.event.Metrics
import com.algorithms.algoking.engine.narration.NarrationId
import com.algorithms.algoking.engine.narration.NarrationKey
import com.algorithms.algoking.engine.scene.Scene
import com.algorithms.algoking.engine.walkthrough.PartialStep
import com.algorithms.algoking.engine.walkthrough.WatchNarrator
import com.algorithms.algoking.engine.walkthrough.WatchStepKind

/**
 * The Linear Search walkthrough: one beat per element compared, then the result.
 * The first "not it" says the rule in full; the rest are short, because the rule
 * does not change — which is the point.
 */
class LinearSearchWatchNarrator : WatchNarrator<LinearSearchState> {

    override fun opening(state: LinearSearchState, scene: Scene): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.SETUP,
            scene = scene,
            headline = NarrationKey(NarrationId.LS_WATCH_SETUP, listOf(state.target)),
            support = NarrationKey(NarrationId.LS_WATCH_SETUP_SUPPORT, listOf(state.values.size)),
        ),
    )

    override fun onFrame(
        previous: LinearSearchState,
        frame: Frame<LinearSearchState>,
        scene: Scene,
    ): List<PartialStep> {
        val state = frame.state
        val i = previous.index
        val value = previous.values.getOrNull(i) ?: return emptyList()
        val args = listOf(value, state.target, i, state.checked, state.values.size)

        return when {
            state.foundAt != null && previous.foundAt == null -> listOf(
                PartialStep(
                    kind = WatchStepKind.FOUND,
                    scene = scene,
                    headline = NarrationKey(NarrationId.LS_WATCH_FOUND, args),
                    support = NarrationKey(NarrationId.LS_WATCH_FOUND_WHY, args),
                ),
            )

            state.index > previous.index -> buildList {
                add(
                    PartialStep(
                        kind = WatchStepKind.ELIMINATE,
                        scene = scene,
                        headline = NarrationKey(NarrationId.LS_WATCH_CHECK, args),
                        support = NarrationKey(
                            if (i == 0) NarrationId.LS_WATCH_CHECK_FIRST else NarrationId.LS_WATCH_CHECK_WHY,
                            args,
                        ),
                    ),
                )
                if (state.exhausted) {
                    add(
                        PartialStep(
                            kind = WatchStepKind.NOT_FOUND,
                            scene = scene,
                            headline = NarrationKey(NarrationId.LS_WATCH_MISSING, args),
                            support = NarrationKey(NarrationId.LS_WATCH_MISSING_WHY, args),
                        ),
                    )
                }
            }

            else -> emptyList()
        }
    }

    override fun closing(state: LinearSearchState, metrics: Metrics, scene: Scene): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.INSIGHT,
            scene = scene,
            headline = NarrationKey(NarrationId.LS_WATCH_INSIGHT),
            support = NarrationKey(NarrationId.LS_WATCH_INSIGHT_SUPPORT, listOf(state.values.size)),
        ),
        PartialStep(
            kind = WatchStepKind.SUMMARY,
            scene = scene,
            headline = NarrationKey(NarrationId.LS_WATCH_SUMMARY, listOf(state.checked)),
            support = NarrationKey(NarrationId.LS_WATCH_SUMMARY_SUPPORT),
            bullets = listOf(
                NarrationKey(NarrationId.LS_IDEA_1),
                NarrationKey(NarrationId.LS_IDEA_2),
                NarrationKey(NarrationId.LS_IDEA_3),
                NarrationKey(NarrationId.LS_IDEA_4),
            ),
        ),
    )
}
