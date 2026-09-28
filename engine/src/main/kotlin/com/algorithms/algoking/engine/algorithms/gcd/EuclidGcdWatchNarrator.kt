package com.algorithms.algoking.engine.algorithms.gcd

import com.algorithms.algoking.engine.core.Frame
import com.algorithms.algoking.engine.event.Metrics
import com.algorithms.algoking.engine.narration.NarrationId
import com.algorithms.algoking.engine.narration.NarrationKey
import com.algorithms.algoking.engine.scene.Scene
import com.algorithms.algoking.engine.walkthrough.PartialStep
import com.algorithms.algoking.engine.walkthrough.WatchNarrator
import com.algorithms.algoking.engine.walkthrough.WatchStepKind

/**
 * The Euclid walkthrough: one beat per remainder, drawn with that step's working
 * complete, then the answer. The first beat says *why* swapping for the remainder
 * is allowed; the rest just do it, because the rule does not change.
 */
class EuclidGcdWatchNarrator : WatchNarrator<GcdState> {

    private val projector = EuclidGcdProjector()

    override fun opening(state: GcdState, scene: Scene): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.SETUP,
            scene = scene,
            headline = NarrationKey(NarrationId.GCD_WATCH_SETUP, listOf(state.start.first, state.start.second)),
            support = NarrationKey(NarrationId.GCD_WATCH_SETUP_SUPPORT),
        ),
    )

    override fun onFrame(previous: GcdState, frame: Frame<GcdState>, scene: Scene): List<PartialStep> {
        val state = frame.state

        if (state.steps.size > previous.steps.size) {
            val step = state.steps.last()
            val args = listOf(step.a, step.b, step.quotient, step.b * step.quotient, step.r)
            return listOf(
                PartialStep(
                    kind = if (step.r == 0) WatchStepKind.KEEP else WatchStepKind.COMPARE,
                    scene = projector.scene(state, finished = step),
                    headline = NarrationKey(NarrationId.GCD_WATCH_STEP, args),
                    support = NarrationKey(
                        when {
                            step.r == 0 -> NarrationId.GCD_WATCH_STEP_ZERO
                            previous.steps.isEmpty() -> NarrationId.GCD_WATCH_STEP_FIRST
                            else -> NarrationId.GCD_WATCH_STEP_WHY
                        },
                        args,
                    ),
                ),
            )
        }

        if (state.done && !previous.done) {
            val g = state.a
            return listOf(
                PartialStep(
                    kind = WatchStepKind.FOUND,
                    scene = scene,
                    headline = NarrationKey(NarrationId.GCD_WATCH_DONE, listOf(g)),
                    support = NarrationKey(
                        NarrationId.GCD_WATCH_DONE_WHY,
                        listOf(g, state.start.first, state.start.second, state.start.first / g, state.start.second / g),
                    ),
                ),
            )
        }
        return emptyList()
    }

    override fun closing(state: GcdState, metrics: Metrics, scene: Scene): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.INSIGHT,
            scene = scene,
            headline = NarrationKey(NarrationId.GCD_WATCH_INSIGHT),
            support = NarrationKey(NarrationId.GCD_WATCH_INSIGHT_SUPPORT),
        ),
        PartialStep(
            kind = WatchStepKind.SUMMARY,
            scene = scene,
            headline = NarrationKey(
                NarrationId.GCD_WATCH_SUMMARY,
                listOf(state.start.first, state.start.second, state.a, state.steps.size),
            ),
            support = NarrationKey(NarrationId.GCD_WATCH_SUMMARY_SUPPORT),
            bullets = listOf(
                NarrationKey(NarrationId.GCD_IDEA_1),
                NarrationKey(NarrationId.GCD_IDEA_2),
                NarrationKey(NarrationId.GCD_IDEA_3),
                NarrationKey(NarrationId.GCD_IDEA_4),
            ),
        ),
    )
}
