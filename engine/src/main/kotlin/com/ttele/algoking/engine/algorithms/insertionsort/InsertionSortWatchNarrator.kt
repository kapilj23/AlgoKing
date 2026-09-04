package com.ttele.algoking.engine.algorithms.insertionsort

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
 * The Insertion Sort walkthrough.
 *
 * The first two keys are narrated in full — one that shifts and one that is
 * already in place — because a learner who only ever sees shifting concludes that
 * every key moves. From the third key on, each insertion collapses to one beat.
 *
 * One unscored checkpoint sits after the first insertion: *what happens to this
 * larger value?*
 */
class InsertionSortWatchNarrator : WatchNarrator<InsertionSortState> {

    private var predictionPlaced = false

    override fun opening(
        state: InsertionSortState,
        scene: Scene,
    ): List<PartialStep> {
        predictionPlaced = false
        return listOf(
            PartialStep(
                kind = WatchStepKind.SETUP,
                scene = scene,
                headline = NarrationKey(NarrationId.INSERT_WATCH_SETUP),
                support = NarrationKey(
                    NarrationId.INSERT_WATCH_SETUP_SUPPORT,
                    listOf(state.values.first()),
                ),
            ),
        )
    }

    override fun onFrame(
        previous: InsertionSortState,
        frame: Frame<InsertionSortState>,
        scene: Scene,
    ): List<PartialStep> {
        val state = frame.state
        val events = frame.events
        // Keys 1 and 2 in full; after that, one beat per insertion.
        val detailed = previous.pass <= 2

        // ── A key was lifted out of the array ─────────────────────────────────
        if (events.any { it is VizEvent.Remove } && !previous.holding) {
            if (!detailed) return emptyList()
            val key = requireNotNull(state.key)
            return listOf(
                PartialStep(
                    kind = WatchStepKind.EXAMINE,
                    scene = scene,
                    headline = NarrationKey(NarrationId.INSERT_WATCH_TAKE_KEY, listOf(key)),
                    support = NarrationKey(NarrationId.INSERT_WATCH_KEY_SUPPORT),
                ),
            )
        }

        // ── A larger value moved right into the gap ───────────────────────────
        val shifted = previous.holding && state.holding && state.holeAt != previous.holeAt
        if (shifted) {
            if (!detailed) return emptyList()
            val moved = previous.comparedValue ?: return emptyList()
            val key = requireNotNull(previous.key)
            return listOf(
                PartialStep(
                    kind = WatchStepKind.SWAP,
                    scene = scene,
                    headline = NarrationKey(
                        NarrationId.INSERT_WATCH_LARGER,
                        listOf(moved, key),
                    ),
                    // Named explicitly: this is a shift, not a swap.
                    support = NarrationKey(NarrationId.INSERT_WATCH_SHIFT, listOf(moved)),
                    comparison = ComparisonReadout(moved, Relation.GREATER, key),
                ),
            )
        }

        // ── The key was dropped into the gap ──────────────────────────────────
        if (previous.holding && !state.holding) {
            val key = requireNotNull(previous.key)
            val compared = previous.comparedValue
            val untouched = previous.holeAt == previous.sortedTo
            val steps = mutableListOf<PartialStep>()

            steps += PartialStep(
                kind = if (state.done) WatchStepKind.SORTED else WatchStepKind.PASS_COMPLETE,
                scene = scene,
                headline = NarrationKey(
                    when {
                        state.done -> NarrationId.INSERT_WATCH_SORTED
                        untouched -> NarrationId.INSERT_WATCH_ALREADY_PLACED
                        detailed -> NarrationId.INSERT_WATCH_NOTHING_LARGER
                        else -> NarrationId.INSERT_WATCH_INSERTED_N
                    },
                    listOf(key, compared ?: key),
                ),
                support = NarrationKey(
                    if (state.done) {
                        NarrationId.INSERT_WATCH_SORTED_SUPPORT
                    } else {
                        NarrationId.INSERT_WATCH_GROWS
                    },
                ),
            )

            if (!predictionPlaced && previous.pass == 1 && !state.done) {
                predictionPlaced = true
                checkpoint(scene, state)?.let { steps += it }
            }
            return steps
        }

        return emptyList()
    }

    /**
     * "What should happen to this value?" — asked about a value the learner can see
     * is larger than the key that is coming next.
     */
    private fun checkpoint(
        scene: Scene,
        state: InsertionSortState,
    ): PartialStep? {
        val nextKey = state.values.getOrNull(state.sortedTo) ?: return null
        val neighbour = state.values.getOrNull(state.sortedTo - 1) ?: return null
        val shouldShift = neighbour > nextKey
        return PartialStep(
            kind = WatchStepKind.PREDICT,
            scene = scene,
            headline = NarrationKey(
                NarrationId.INSERT_PREDICT_PROMPT,
                listOf(neighbour, nextKey),
            ),
            support = NarrationKey(NarrationId.INSERT_PREDICT_SUPPORT),
            prediction = WatchPrediction(
                prompt = NarrationKey(
                    NarrationId.INSERT_PREDICT_PROMPT,
                    listOf(neighbour, nextKey),
                ),
                options = listOf(
                    NarrationKey(NarrationId.INSERT_OPTION_SHIFT),
                    NarrationKey(NarrationId.INSERT_OPTION_INSERT),
                ),
                correctIndex = if (shouldShift) 0 else 1,
                whenRight = NarrationKey(
                    if (shouldShift) {
                        NarrationId.INSERT_PREDICT_RIGHT_SHIFT
                    } else {
                        NarrationId.INSERT_PREDICT_RIGHT_KEEP
                    },
                    listOf(neighbour, nextKey),
                ),
                whenWrong = NarrationKey(
                    if (shouldShift) {
                        NarrationId.INSERT_RETRY_EXPLAIN_SHIFT
                    } else {
                        NarrationId.INSERT_RETRY_EXPLAIN_INSERT
                    },
                    listOf(neighbour, nextKey),
                ),
            ),
        )
    }

    override fun closing(
        state: InsertionSortState,
        metrics: Metrics,
        scene: Scene,
    ): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.INSIGHT,
            scene = scene,
            headline = NarrationKey(NarrationId.INSERT_WATCH_INSIGHT),
            support = NarrationKey(NarrationId.INSERT_WATCH_INSIGHT_SUPPORT),
        ),
        PartialStep(
            kind = WatchStepKind.SUMMARY,
            scene = scene,
            headline = NarrationKey(
                NarrationId.INSERT_WATCH_SUMMARY,
                listOf(state.values.size - 1),
            ),
            support = NarrationKey(NarrationId.INSERT_WATCH_SUMMARY_SUPPORT),
            bullets = listOf(
                NarrationKey(NarrationId.INSERT_IDEA_1),
                NarrationKey(NarrationId.INSERT_IDEA_2),
                NarrationKey(NarrationId.INSERT_IDEA_3),
            ),
        ),
    )
}
