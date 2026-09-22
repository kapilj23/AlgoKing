package com.algorithms.algoking.engine.algorithms.quicksort

import com.algorithms.algoking.engine.core.Frame
import com.algorithms.algoking.engine.event.Metrics
import com.algorithms.algoking.engine.event.Relation
import com.algorithms.algoking.engine.event.VizEvent
import com.algorithms.algoking.engine.narration.NarrationId
import com.algorithms.algoking.engine.narration.NarrationKey
import com.algorithms.algoking.engine.scene.Scene
import com.algorithms.algoking.engine.walkthrough.ComparisonReadout
import com.algorithms.algoking.engine.walkthrough.PartialStep
import com.algorithms.algoking.engine.walkthrough.WatchNarrator
import com.algorithms.algoking.engine.walkthrough.WatchPrediction
import com.algorithms.algoking.engine.walkthrough.WatchStepKind

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
    private var leftNarrated = false
    private var rightNarrated = false

    override fun opening(
        state: QuickSortState,
        scene: Scene,
    ): List<PartialStep> {
        predictionPlaced = false
        leftNarrated = false
        rightNarrated = false
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

        // ── The pivot landed, and the partition split in two ──────────────────
        // The first one gets the beat where **both halves exist at once**, which
        // is the frame that makes the recursion visible (ADR-054). Later pivots
        // get the terse beat they always got: by then the learner is watching the
        // same rule on a smaller array, which is the point being made.
        if (state.showingSplit && !previous.showingSplit) {
            if (state.done) return emptyList()
            val split = requireNotNull(state.split)
            val bothSides = !split.left.isEmpty() && !split.right.isEmpty()
            return listOf(
                PartialStep(
                    kind = WatchStepKind.PASS_COMPLETE,
                    scene = scene,
                    headline = NarrationKey(
                        when {
                            // Only say "two smaller arrays" when there are two.
                            detailed && bothSides -> NarrationId.QUICK_WATCH_SPLIT
                            detailed -> NarrationId.QUICK_WATCH_PARTITIONED
                            else -> NarrationId.QUICK_WATCH_PIVOT_PLACED
                        },
                        listOf(split.pivot),
                    ),
                    support = NarrationKey(
                        when {
                            detailed && bothSides -> NarrationId.QUICK_WATCH_SPLIT_SUPPORT
                            detailed -> NarrationId.QUICK_WATCH_PIVOT_FINAL
                            else -> NarrationId.QUICK_WATCH_REPEAT
                        },
                        listOf(split.pivot),
                    ),
                ),
            )
        }

        // ── A side was taken up ───────────────────────────────────────────────
        // Named, and only the first time each side is entered: after that the
        // learner has the idea and the beats would be the same sentence again.
        if (previous.showingSplit && state.active && !state.done) {
            val side = state.side
            val already = if (side == PartitionSide.LEFT) leftNarrated else rightNarrated
            if (already) return emptyList()
            if (side == PartitionSide.LEFT) leftNarrated = true else rightNarrated = true
            return listOf(
                PartialStep(
                    kind = WatchStepKind.EXAMINE,
                    scene = scene,
                    headline = NarrationKey(
                        if (side == PartitionSide.LEFT) {
                            NarrationId.QUICK_WATCH_NEXT_LEFT
                        } else {
                            NarrationId.QUICK_WATCH_NEXT_RIGHT
                        },
                        listOf(state.partitionValues.joinToString(", ")),
                    ),
                    support = NarrationKey(NarrationId.QUICK_WATCH_SIDE_SUPPORT),
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
