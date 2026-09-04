package com.ttele.algoking.engine.algorithms.selectionsort

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
 * The Selection Sort walkthrough.
 *
 * **Pass 1 is narrated in full** — the whole point is that a scan happens before
 * anything moves, and a learner who skips it never sees why the algorithm is
 * called *selection*. Later passes collapse to one beat each.
 *
 * One unscored checkpoint sits after pass 1: *"which number goes first?"*
 */
class SelectionSortWatchNarrator : WatchNarrator<SelectionSortState> {

    private var predictionPlaced = false

    override fun opening(
        state: SelectionSortState,
        scene: Scene,
    ): List<PartialStep> {
        predictionPlaced = false
        return listOf(
            PartialStep(
                kind = WatchStepKind.SETUP,
                scene = scene,
                headline = NarrationKey(NarrationId.SELECT_WATCH_SETUP),
                support = NarrationKey(NarrationId.SELECT_WATCH_SETUP_SUPPORT),
            ),
            PartialStep(
                kind = WatchStepKind.EXAMINE,
                scene = scene,
                headline = NarrationKey(NarrationId.SELECT_WATCH_FIRST_SLOT),
                support = NarrationKey(
                    NarrationId.SELECT_WATCH_FIRST_CANDIDATE,
                    listOf(state.minValue),
                ),
            ),
        )
    }

    override fun onFrame(
        previous: SelectionSortState,
        frame: Frame<SelectionSortState>,
        scene: Scene,
    ): List<PartialStep> {
        val state = frame.state
        val events = frame.events
        val detailed = previous.pass == 1

        // ── The minimum was placed and the sorted prefix grew ─────────────────
        if (events.any { it is VizEvent.Swap }) {
            val placed = previous.minValue
            val steps = mutableListOf<PartialStep>()

            steps += PartialStep(
                kind = if (state.done) WatchStepKind.SORTED else WatchStepKind.PASS_COMPLETE,
                scene = scene,
                headline = NarrationKey(
                    when {
                        state.done -> NarrationId.SELECT_WATCH_SORTED
                        previous.pass == 1 -> NarrationId.SELECT_WATCH_PLACE
                        else -> NarrationId.SELECT_WATCH_PLACE_N
                    },
                    listOf(placed, previous.pass),
                ),
                support = NarrationKey(
                    if (state.done) {
                        NarrationId.SELECT_WATCH_SORTED_SUPPORT
                    } else {
                        NarrationId.SELECT_WATCH_GROWS
                    },
                ),
            )

            if (!predictionPlaced && previous.pass == 1 && !state.done) {
                predictionPlaced = true
                steps += checkpoint(scene, state)
            }
            return steps
        }

        // ── A value was judged against the remembered candidate ───────────────
        val compare = events.filterIsInstance<VizEvent.Compare>().firstOrNull()
        if (compare != null) {
            if (!detailed) return emptyList()

            val scanned = previous.values[requireNotNull(previous.cursor)]
            val best = previous.minValue
            val tookIt = state.minIndex != previous.minIndex

            return listOf(
                PartialStep(
                    kind = if (tookIt) WatchStepKind.SWAP else WatchStepKind.KEEP,
                    scene = scene,
                    headline = NarrationKey(
                        if (tookIt) {
                            NarrationId.SELECT_WATCH_SMALLER
                        } else {
                            NarrationId.SELECT_WATCH_NOT_SMALLER
                        },
                        listOf(scanned, best),
                    ),
                    support = if (tookIt) {
                        NarrationKey(NarrationId.SELECT_WATCH_NEW_MIN, listOf(scanned))
                    } else {
                        null
                    },
                    comparison = ComparisonReadout(
                        scanned,
                        if (tookIt) Relation.LESS else Relation.GREATER,
                        best,
                    ),
                ),
            )
        }

        return emptyList()
    }

    /** Which value does Selection Sort put first? Unscored; it only prepares Try. */
    private fun checkpoint(
        scene: Scene,
        state: SelectionSortState,
    ): PartialStep {
        val remaining = state.values.drop(state.sortedTo)
        val smallest = remaining.min()
        // Four plausible answers, one of them right.
        val options = remaining.take(4).let { first ->
            if (smallest in first) first else first.dropLast(1) + smallest
        }
        return PartialStep(
            kind = WatchStepKind.PREDICT,
            scene = scene,
            headline = NarrationKey(NarrationId.SELECT_PREDICT_PROMPT),
            support = NarrationKey(NarrationId.SELECT_PREDICT_SUPPORT),
            prediction = WatchPrediction(
                prompt = NarrationKey(NarrationId.SELECT_PREDICT_PROMPT),
                options = options.map { NarrationKey(NarrationId.SELECT_VALUE, listOf(it)) },
                correctIndex = options.indexOf(smallest),
                whenRight = NarrationKey(NarrationId.SELECT_PREDICT_RIGHT, listOf(smallest)),
                whenWrong = NarrationKey(NarrationId.SELECT_PREDICT_WRONG, listOf(smallest)),
            ),
        )
    }

    override fun closing(
        state: SelectionSortState,
        metrics: Metrics,
        scene: Scene,
    ): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.INSIGHT,
            scene = scene,
            headline = NarrationKey(NarrationId.SELECT_WATCH_INSIGHT),
            support = NarrationKey(NarrationId.SELECT_WATCH_INSIGHT_SUPPORT),
        ),
        PartialStep(
            kind = WatchStepKind.SUMMARY,
            scene = scene,
            headline = NarrationKey(
                NarrationId.SELECT_WATCH_SUMMARY,
                listOf(metrics.comparisons),
            ),
            support = NarrationKey(NarrationId.SELECT_WATCH_SUMMARY_SUPPORT),
            bullets = listOf(
                NarrationKey(NarrationId.SELECT_IDEA_1),
                NarrationKey(NarrationId.SELECT_IDEA_2),
                NarrationKey(NarrationId.SELECT_IDEA_3),
            ),
        ),
    )
}
