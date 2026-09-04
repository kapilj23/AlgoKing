package com.ttele.algoking.engine.algorithms.quicksort

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
 * The Quick Sort walkthrough.
 *
 * The **first partition is narrated in full** — every comparison, then the pivot
 * landing — because that one pass contains the entire idea. Later partitions
 * collapse to one beat each: by then the learner is watching the same rule on a
 * smaller array, which is exactly the point being made.
 */
class QuickSortWatchNarrator : WatchNarrator<QuickSortState> {

    private var predictionPlaced = false

    override fun opening(
        state: QuickSortState,
        scene: Scene,
    ): List<PartialStep> {
        predictionPlaced = false
        return listOf(
            PartialStep(
                kind = WatchStepKind.SETUP,
                scene = scene,
                headline = NarrationKey(NarrationId.QUICK_WATCH_SETUP),
                support = NarrationKey(NarrationId.QUICK_WATCH_SETUP_SUPPORT),
            ),
        )
    }

    override fun onFrame(
        previous: QuickSortState,
        frame: Frame<QuickSortState>,
        scene: Scene,
    ): List<PartialStep> {
        val state = frame.state
        // The first partition in full; the rest at one beat apiece.
        val detailed = previous.finalized.isEmpty()

        // ── A pivot was chosen ────────────────────────────────────────────────
        if (state.pivotAt != null && previous.pivotAt == null) {
            if (!detailed) return emptyList()
            val pivot = requireNotNull(state.pivot)
            return listOf(
                PartialStep(
                    kind = WatchStepKind.EXAMINE,
                    scene = scene,
                    headline = NarrationKey(NarrationId.QUICK_WATCH_PIVOT, listOf(pivot)),
                    support = NarrationKey(NarrationId.QUICK_WATCH_PIVOT_SUPPORT),
                ),
            )
        }

        // ── A value was classified ────────────────────────────────────────────
        if (previous.scanning && state.cursor != previous.cursor && state.pivotAt != null) {
            if (!detailed) return emptyList()
            val value = requireNotNull(previous.scanned)
            val pivot = requireNotNull(previous.pivot)
            val wentLeft = state.boundary != previous.boundary

            val steps = mutableListOf<PartialStep>()
            steps += PartialStep(
                kind = if (wentLeft) WatchStepKind.KEEP else WatchStepKind.SWAP,
                scene = scene,
                headline = NarrationKey(
                    if (wentLeft) {
                        NarrationId.QUICK_WATCH_SMALLER
                    } else {
                        NarrationId.QUICK_WATCH_LARGER
                    },
                    listOf(value, pivot),
                ),
                support = NarrationKey(
                    if (wentLeft) {
                        NarrationId.QUICK_WATCH_BELONGS_LEFT
                    } else {
                        NarrationId.QUICK_WATCH_BELONGS_RIGHT
                    },
                ),
                comparison = ComparisonReadout(
                    value,
                    if (wentLeft) Relation.LESS else Relation.GREATER,
                    pivot,
                ),
            )

            // Two worked comparisons is enough context to be asked to predict.
            if (!predictionPlaced && detailed && previous.cursor == previous.lo + 1) {
                predictionPlaced = true
                checkpoint(scene, state)?.let { steps += it }
            }
            return steps
        }

        // ── The pivot landed ──────────────────────────────────────────────────
        if (state.finalized.size > previous.finalized.size &&
            frame.events.any { it is VizEvent.Finalize }
        ) {
            if (state.done) return emptyList()
            val pivot = previous.pivot
            return listOf(
                PartialStep(
                    kind = WatchStepKind.PASS_COMPLETE,
                    scene = scene,
                    headline = NarrationKey(
                        if (detailed) {
                            NarrationId.QUICK_WATCH_PARTITIONED
                        } else {
                            NarrationId.QUICK_WATCH_PIVOT_PLACED
                        },
                        listOf(pivot ?: 0),
                    ),
                    support = NarrationKey(
                        if (detailed) {
                            NarrationId.QUICK_WATCH_PIVOT_FINAL
                        } else {
                            NarrationId.QUICK_WATCH_REPEAT
                        },
                    ),
                ),
            )
        }

        if (state.done) {
            return listOf(
                PartialStep(
                    kind = WatchStepKind.SORTED,
                    scene = scene,
                    headline = NarrationKey(NarrationId.QUICK_WATCH_SORTED),
                    support = NarrationKey(NarrationId.QUICK_WATCH_SORTED_SUPPORT),
                ),
            )
        }

        return emptyList()
    }

    /** "Which side does this value belong on?" — asked mid-partition. */
    private fun checkpoint(scene: Scene, state: QuickSortState): PartialStep? {
        val value = state.scanned ?: return null
        val pivot = state.pivot ?: return null
        return PartialStep(
            kind = WatchStepKind.PREDICT,
            scene = scene,
            headline = NarrationKey(NarrationId.QUICK_PREDICT_PROMPT, listOf(value, pivot)),
            support = NarrationKey(NarrationId.QUICK_PREDICT_SUPPORT),
            prediction = WatchPrediction(
                prompt = NarrationKey(NarrationId.QUICK_PREDICT_PROMPT, listOf(value, pivot)),
                options = listOf(
                    NarrationKey(NarrationId.QUICK_OPTION_LEFT),
                    NarrationKey(NarrationId.QUICK_OPTION_RIGHT),
                ),
                correctIndex = if (value <= pivot) 0 else 1,
                whenRight = NarrationKey(
                    if (value <= pivot) {
                        NarrationId.QUICK_RETRY_EXPLAIN_LEFT
                    } else {
                        NarrationId.QUICK_RETRY_EXPLAIN_RIGHT
                    },
                    listOf(value, pivot),
                ),
                whenWrong = NarrationKey(
                    if (value <= pivot) {
                        NarrationId.QUICK_RETRY_EXPLAIN_LEFT
                    } else {
                        NarrationId.QUICK_RETRY_EXPLAIN_RIGHT
                    },
                    listOf(value, pivot),
                ),
            ),
        )
    }

    override fun closing(
        state: QuickSortState,
        metrics: Metrics,
        scene: Scene,
    ): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.INSIGHT,
            scene = scene,
            headline = NarrationKey(NarrationId.QUICK_WATCH_INSIGHT),
            support = NarrationKey(NarrationId.QUICK_WATCH_INSIGHT_SUPPORT),
        ),
        PartialStep(
            kind = WatchStepKind.SUMMARY,
            scene = scene,
            headline = NarrationKey(
                NarrationId.QUICK_WATCH_SUMMARY,
                listOf(state.values.size),
            ),
            support = NarrationKey(NarrationId.QUICK_WATCH_SUMMARY_SUPPORT),
            bullets = listOf(
                NarrationKey(NarrationId.QUICK_IDEA_1),
                NarrationKey(NarrationId.QUICK_IDEA_2),
                NarrationKey(NarrationId.QUICK_IDEA_3),
            ),
        ),
    )
}
