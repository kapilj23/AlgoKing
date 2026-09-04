package com.ttele.algoking.engine.algorithms.mergesort

import com.ttele.algoking.engine.core.Frame
import com.ttele.algoking.engine.event.Metrics
import com.ttele.algoking.engine.event.Relation
import com.ttele.algoking.engine.narration.NarrationId
import com.ttele.algoking.engine.narration.NarrationKey
import com.ttele.algoking.engine.scene.Scene
import com.ttele.algoking.engine.walkthrough.ComparisonReadout
import com.ttele.algoking.engine.walkthrough.PartialStep
import com.ttele.algoking.engine.walkthrough.WatchNarrator
import com.ttele.algoking.engine.walkthrough.WatchPrediction
import com.ttele.algoking.engine.walkthrough.WatchStepKind

/**
 * The Merge Sort walkthrough.
 *
 * The divide phase is narrated in full — it is short, and it is the half that makes
 * Merge Sort different. The merge phase narrates **every comparison of the first two
 * levels** (where the idea lands) and then one beat per merge, because by level
 * three the learner is watching the same rule on longer runs.
 */
class MergeSortWatchNarrator : WatchNarrator<MergeSortState> {

    private var predictionPlaced = false

    override fun opening(
        state: MergeSortState,
        scene: Scene,
    ): List<PartialStep> {
        predictionPlaced = false
        return listOf(
            PartialStep(
                kind = WatchStepKind.SETUP,
                scene = scene,
                headline = NarrationKey(NarrationId.MERGE_WATCH_SETUP),
                support = NarrationKey(NarrationId.MERGE_WATCH_SETUP_SUPPORT),
            ),
        )
    }

    override fun onFrame(
        previous: MergeSortState,
        frame: Frame<MergeSortState>,
        scene: Scene,
    ): List<PartialStep> {
        val state = frame.state

        // ── Dividing ──────────────────────────────────────────────────────────
        if (state.phase == MergePhase.DIVIDING && state.groupWidth != previous.groupWidth) {
            val first = previous.groupWidth == previous.values.size
            return listOf(
                PartialStep(
                    kind = WatchStepKind.EXAMINE,
                    scene = scene,
                    headline = NarrationKey(
                        when {
                            first -> NarrationId.MERGE_WATCH_FIRST_SPLIT
                            state.groupWidth == 1 -> NarrationId.MERGE_WATCH_DIVIDED
                            else -> NarrationId.MERGE_WATCH_SPLIT_AGAIN
                        },
                    ),
                    support = NarrationKey(
                        if (state.groupWidth == 1) {
                            NarrationId.MERGE_WATCH_BASE_CASE
                        } else {
                            NarrationId.MERGE_WATCH_KEEP_DIVIDING
                        },
                    ),
                ),
            )
        }

        // ── Starting the merge phase ──────────────────────────────────────────
        if (state.phase == MergePhase.MERGING && previous.phase == MergePhase.DIVIDING) {
            return listOf(
                PartialStep(
                    kind = WatchStepKind.EXAMINE,
                    scene = scene,
                    headline = NarrationKey(NarrationId.MERGE_WATCH_START_MERGE),
                    support = NarrationKey(NarrationId.MERGE_WATCH_MERGE_RULE),
                ),
            )
        }

        // ── A fresh pair opened: the moment both fronts exist and the learner has
        //    seen the rule exactly once. That is where the checkpoint belongs.
        if (!predictionPlaced &&
            state.merging &&
            state.taken == 0 &&
            state.lo > 0 &&
            state.runWidth == 1
        ) {
            predictionPlaced = true
            checkpoint(scene, state)?.let { return listOf(it) }
        }

        // ── A value was taken during a merge ──────────────────────────────────
        if (state.taken > previous.taken) {
            // Levels 1 and 2 in full; after that the rule is established.
            if (previous.runWidth > 2) return emptyList()

            val took = state.values[previous.writeAt]
            val leftFront = previous.leftFront
            val rightFront = previous.rightFront
            val other = if (took == leftFront) rightFront else leftFront

            val steps = mutableListOf<PartialStep>()
            steps += PartialStep(
                kind = WatchStepKind.SWAP,
                scene = scene,
                headline = NarrationKey(
                    if (other == null) {
                        NarrationId.MERGE_WATCH_ONLY_LEFT
                    } else {
                        NarrationId.MERGE_WATCH_TOOK
                    },
                    listOf(took, other ?: took),
                ),
                support = if (previous.taken == 0) {
                    NarrationKey(NarrationId.MERGE_WATCH_COMPARE_FRONTS)
                } else {
                    null
                },
                comparison = other?.let {
                    ComparisonReadout(took, Relation.LESS, it)
                },
            )

            return steps
        }

        // ── A merge or a level finished ───────────────────────────────────────
        if (state.done) {
            return listOf(
                PartialStep(
                    kind = WatchStepKind.SORTED,
                    scene = scene,
                    headline = NarrationKey(NarrationId.MERGE_WATCH_SORTED),
                    support = NarrationKey(NarrationId.MERGE_WATCH_SORTED_SUPPORT),
                ),
            )
        }
        if (state.runWidth > previous.runWidth) {
            return listOf(
                PartialStep(
                    kind = WatchStepKind.PASS_COMPLETE,
                    scene = scene,
                    headline = NarrationKey(
                        NarrationId.MERGE_WATCH_LEVEL_DONE,
                        listOf(previous.runWidth * 2),
                    ),
                    support = NarrationKey(NarrationId.MERGE_WATCH_LEVEL_SUPPORT),
                ),
            )
        }

        return emptyList()
    }

    /** "Which value goes first?" asked over two sorted halves the learner can see. */
    private fun checkpoint(scene: Scene, state: MergeSortState): PartialStep? {
        val leftFront = state.leftFront ?: return null
        val rightFront = state.rightFront ?: return null
        val takeLeft = leftFront <= rightFront
        return PartialStep(
            kind = WatchStepKind.PREDICT,
            scene = scene,
            headline = NarrationKey(NarrationId.MERGE_PREDICT_PROMPT),
            support = NarrationKey(NarrationId.MERGE_PREDICT_SUPPORT),
            prediction = WatchPrediction(
                prompt = NarrationKey(NarrationId.MERGE_PREDICT_PROMPT),
                options = listOf(
                    NarrationKey(NarrationId.MERGE_VALUE, listOf(leftFront)),
                    NarrationKey(NarrationId.MERGE_VALUE, listOf(rightFront)),
                ),
                correctIndex = if (takeLeft) 0 else 1,
                whenRight = NarrationKey(
                    NarrationId.MERGE_PREDICT_RIGHT,
                    listOf(minOf(leftFront, rightFront), maxOf(leftFront, rightFront)),
                ),
                whenWrong = NarrationKey(
                    NarrationId.MERGE_PREDICT_WRONG,
                    listOf(minOf(leftFront, rightFront), maxOf(leftFront, rightFront)),
                ),
            ),
        )
    }

    override fun closing(
        state: MergeSortState,
        metrics: Metrics,
        scene: Scene,
    ): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.INSIGHT,
            scene = scene,
            headline = NarrationKey(NarrationId.MERGE_WATCH_INSIGHT),
            support = NarrationKey(NarrationId.MERGE_WATCH_INSIGHT_SUPPORT),
        ),
        PartialStep(
            kind = WatchStepKind.SUMMARY,
            scene = scene,
            headline = NarrationKey(
                NarrationId.MERGE_WATCH_SUMMARY,
                listOf(metrics.comparisons),
            ),
            support = NarrationKey(NarrationId.MERGE_WATCH_SUMMARY_SUPPORT),
            bullets = listOf(
                NarrationKey(NarrationId.MERGE_IDEA_1),
                NarrationKey(NarrationId.MERGE_IDEA_2),
                NarrationKey(NarrationId.MERGE_IDEA_3),
            ),
        ),
    )
}
