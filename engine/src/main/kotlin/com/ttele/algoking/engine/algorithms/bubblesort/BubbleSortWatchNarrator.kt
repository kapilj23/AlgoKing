package com.ttele.algoking.engine.algorithms.bubblesort

import com.ttele.algoking.engine.core.Frame
import com.ttele.algoking.engine.event.Metrics
import com.ttele.algoking.engine.event.Relation
import com.ttele.algoking.engine.event.VizEvent
import com.ttele.algoking.engine.narration.NarrationId
import com.ttele.algoking.engine.narration.NarrationKey
import com.ttele.algoking.engine.scene.Scene
import com.ttele.algoking.engine.walkthrough.ComparisonReadout
import com.ttele.algoking.engine.walkthrough.PartialStep
import com.ttele.algoking.engine.walkthrough.WatchNarrator
import com.ttele.algoking.engine.walkthrough.WatchPrediction
import com.ttele.algoking.engine.walkthrough.WatchStepKind

/**
 * The Bubble Sort walkthrough.
 *
 * **Pass 1 is shown in full** — that is where the mental model is built. Later
 * passes collapse to one beat each, because watching the same comparison rule
 * twenty more times teaches nothing and costs the learner twenty taps.
 *
 * One unscored checkpoint sits just before the end, so the learner commits to a
 * SWAP/KEEP answer before Try asks for real.
 */
class BubbleSortWatchNarrator : WatchNarrator<BubbleSortState> {

    private var predictionPlaced = false

    override fun opening(
        state: BubbleSortState,
        scene: Scene,
    ): List<PartialStep> {
        predictionPlaced = false
        return listOf(
            PartialStep(
                kind = WatchStepKind.SETUP,
                scene = scene,
                headline = NarrationKey(NarrationId.BUBBLE_WATCH_SETUP),
                support = NarrationKey(NarrationId.BUBBLE_WATCH_SETUP_SUPPORT),
            ),
        )
    }

    override fun onFrame(
        previous: BubbleSortState,
        frame: Frame<BubbleSortState>,
        scene: Scene,
    ): List<PartialStep> {
        val state = frame.state
        val events = frame.events
        val detailed = previous.pass == 1

        // ── The pair came under comparison ────────────────────────────────────
        val compare = events.filterIsInstance<VizEvent.Compare>().firstOrNull()
        if (compare != null) {
            if (!detailed) return emptyList()

            val left = state.values[state.i]
            val right = state.values[state.pairEnd]
            val first = state.i == 0 && state.pass == 1

            return listOf(
                PartialStep(
                    kind = WatchStepKind.EXAMINE,
                    scene = scene,
                    headline = NarrationKey(
                        if (first) {
                            NarrationId.BUBBLE_WATCH_COMPARE_NEIGHBOURS
                        } else {
                            NarrationId.BUBBLE_WATCH_COMPARE_NEXT
                        },
                    ),
                    support = if (first) {
                        NarrationKey(NarrationId.BUBBLE_WATCH_NEIGHBOURS_SUPPORT)
                    } else {
                        null
                    },
                ),
                PartialStep(
                    kind = WatchStepKind.COMPARE,
                    scene = scene,
                    headline = NarrationKey(
                        if (compare.relation == Relation.GREATER) {
                            NarrationId.BUBBLE_WATCH_LARGER
                        } else {
                            NarrationId.BUBBLE_WATCH_SMALLER
                        },
                        listOf(left, right),
                    ),
                    comparison = ComparisonReadout(left, compare.relation, right),
                ),
            )
        }

        // ── The decision was carried out ──────────────────────────────────────
        if (events.any { it is VizEvent.Swap }) {
            if (!detailed) return emptyList()
            return listOf(
                PartialStep(
                    kind = WatchStepKind.SWAP,
                    scene = scene,
                    headline = NarrationKey(NarrationId.BUBBLE_WATCH_SWAP),
                ),
            )
        }

        if (events.any { it is VizEvent.Hold }) {
            if (!detailed) return emptyList()
            return listOf(
                PartialStep(
                    kind = WatchStepKind.KEEP,
                    scene = scene,
                    headline = NarrationKey(NarrationId.BUBBLE_WATCH_KEEP),
                    support = NarrationKey(NarrationId.BUBBLE_WATCH_KEEP_SUPPORT),
                ),
            )
        }

        // ── A pass finished ───────────────────────────────────────────────────
        if (events.any { it is VizEvent.Finalize }) {
            val settled = previous.values.getOrElse(previous.sortedFrom - 1) { 0 }
            val earlyExit = !previous.swappedThisPass
            val steps = mutableListOf<PartialStep>()

            steps += PartialStep(
                kind = if (state.done) WatchStepKind.SORTED else WatchStepKind.PASS_COMPLETE,
                scene = scene,
                headline = NarrationKey(
                    when {
                        earlyExit -> NarrationId.BUBBLE_WATCH_NO_SWAPS
                        state.done -> NarrationId.BUBBLE_WATCH_SORTED
                        previous.pass == 1 -> NarrationId.BUBBLE_WATCH_REACHED_END
                        else -> NarrationId.BUBBLE_WATCH_PASS_N
                    },
                    listOf(settled, previous.pass),
                ),
                support = NarrationKey(
                    when {
                        earlyExit -> NarrationId.BUBBLE_WATCH_NO_SWAPS_SUPPORT
                        state.done -> NarrationId.BUBBLE_WATCH_SORTED_SUPPORT
                        else -> NarrationId.BUBBLE_WATCH_PASS_SUPPORT
                    },
                    listOf(settled),
                ),
            )

            // The checkpoint goes after pass 1 — one worked pass is enough context.
            if (!predictionPlaced && previous.pass == 1 && !state.done) {
                predictionPlaced = true
                steps += checkpoint(scene, state)
            }
            return steps
        }

        return emptyList()
    }

    /**
     * The unscored checkpoint. It reuses the live array, so the learner is
     * predicting the very next thing the algorithm will do.
     */
    private fun checkpoint(scene: Scene, state: BubbleSortState): PartialStep {
        val left = state.values.getOrElse(0) { 0 }
        val right = state.values.getOrElse(1) { 0 }
        val shouldSwap = left > right
        return PartialStep(
            kind = WatchStepKind.PREDICT,
            scene = scene,
            headline = NarrationKey(NarrationId.BUBBLE_PREDICT_PROMPT, listOf(left, right)),
            support = NarrationKey(NarrationId.BUBBLE_PREDICT_SUPPORT),
            prediction = WatchPrediction(
                prompt = NarrationKey(NarrationId.BUBBLE_PREDICT_PROMPT, listOf(left, right)),
                options = listOf(
                    NarrationKey(NarrationId.BUBBLE_OPTION_SWAP),
                    NarrationKey(NarrationId.BUBBLE_OPTION_KEEP),
                ),
                correctIndex = if (shouldSwap) 0 else 1,
                whenRight = NarrationKey(
                    if (shouldSwap) NarrationId.BUBBLE_SWAPPED else NarrationId.BUBBLE_KEPT,
                    listOf(left, right),
                ),
                whenWrong = NarrationKey(
                    if (shouldSwap) {
                        NarrationId.BUBBLE_RETRY_EXPLAIN_SWAP
                    } else {
                        NarrationId.BUBBLE_RETRY_EXPLAIN_KEEP
                    },
                    listOf(left, right),
                ),
            ),
        )
    }

    override fun closing(
        state: BubbleSortState,
        metrics: Metrics,
        scene: Scene,
    ): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.INSIGHT,
            scene = scene,
            headline = NarrationKey(NarrationId.BUBBLE_WATCH_INSIGHT),
            support = NarrationKey(NarrationId.BUBBLE_WATCH_INSIGHT_SUPPORT),
        ),
        PartialStep(
            kind = WatchStepKind.SUMMARY,
            scene = scene,
            headline = NarrationKey(
                NarrationId.BUBBLE_WATCH_SUMMARY,
                listOf(metrics.comparisons, metrics.swaps),
            ),
            support = NarrationKey(NarrationId.BUBBLE_WATCH_SUMMARY_SUPPORT),
            bullets = listOf(
                NarrationKey(NarrationId.BUBBLE_IDEA_1),
                NarrationKey(NarrationId.BUBBLE_IDEA_2),
                NarrationKey(NarrationId.BUBBLE_IDEA_3),
            ),
        ),
    )
}
