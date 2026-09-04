package com.ttele.algoking.engine.algorithms.twopointers

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
import com.ttele.algoking.engine.walkthrough.WatchStepKind

/**
 * The Two Pointers walkthrough, one beat at a time.
 *
 * Every round is **two** steps, and the split is the pedagogy:
 *
 * 1. `COMPARE` — *"1 + 10 = 11. That is greater than 10."* The sum is on screen
 *    and the pointers have not moved.
 * 2. `ELIMINATE` — *"Move RIGHT one position left."* Now they have.
 *
 * Collapsing those into one step would show the learner a pointer that has
 * already moved beside the reason it should move, which is the wrong order to
 * think in. The engine emits them as two transitions (`Compare`, then the move),
 * so the narrator does not have to invent the seam — it is already there.
 *
 * No step is manufactured for a frame where nothing changed; `WatchScriptTest`
 * fails the build if two adjacent steps are identical.
 */
class TwoPointersWatchNarrator : WatchNarrator<TwoPointersState> {

    override fun opening(
        state: TwoPointersState,
        scene: Scene,
    ): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.SETUP,
            scene = scene,
            headline = NarrationKey(NarrationId.TP_WATCH_SETUP, listOf(state.target)),
            support = NarrationKey(NarrationId.TP_WATCH_SETUP_SUPPORT),
        ),
        PartialStep(
            kind = WatchStepKind.EXAMINE,
            scene = scene,
            headline = NarrationKey(NarrationId.TP_WATCH_ENDS),
            support = NarrationKey(
                NarrationId.TP_WATCH_ENDS_SUPPORT,
                listOf(state.left, state.right),
            ),
        ),
    )

    override fun onFrame(
        previous: TwoPointersState,
        frame: Frame<TwoPointersState>,
        scene: Scene,
    ): List<PartialStep> {
        val state = frame.state
        val events = frame.events

        // -- The pair was read: state the sum, and how it stands ----------------
        val compared = events.filterIsInstance<VizEvent.Compare>().firstOrNull()
        if (compared != null) {
            val sum = requireNotNull(state.sum)
            val leftValue = requireNotNull(state.leftValue)
            val rightValue = requireNotNull(state.rightValue)
            return listOf(
                PartialStep(
                    kind = WatchStepKind.COMPARE,
                    scene = scene,
                    // The arithmetic is the headline, because it is the thing the
                    // learner has to be able to reproduce.
                    headline = NarrationKey(
                        NarrationId.TP_WATCH_SUM,
                        listOf(leftValue, rightValue, sum),
                    ),
                    support = NarrationKey(
                        when (compared.relation) {
                            Relation.GREATER -> NarrationId.TP_WATCH_SUM_GREATER
                            Relation.LESS -> NarrationId.TP_WATCH_SUM_LESS
                            Relation.EQUAL -> NarrationId.TP_WATCH_SUM_EQUAL
                        },
                        listOf(sum, state.target),
                    ),
                    comparison = ComparisonReadout(sum, compared.relation, state.target),
                ),
            )
        }

        // -- A pointer moved: say which, and say why ---------------------------
        val discarded = events.filterIsInstance<VizEvent.Eliminate>().firstOrNull()
        if (discarded != null) {
            val movedLeft = state.left != previous.left
            val closed = state.exhausted
            val droppedValue = previous.values[discarded.range.first]
            return listOf(
                PartialStep(
                    kind = if (closed) WatchStepKind.NOT_FOUND else WatchStepKind.ELIMINATE,
                    scene = scene,
                    headline = when {
                        closed -> NarrationKey(
                            NarrationId.TP_WATCH_NO_PAIR,
                            listOf(state.target),
                        )

                        movedLeft -> NarrationKey(NarrationId.TP_WATCH_MOVE_LEFT)
                        else -> NarrationKey(NarrationId.TP_WATCH_MOVE_RIGHT)
                    },
                    // The support line is where the *technique* is taught rather
                    // than the gesture: the value that just left took every pair
                    // it belonged to with it.
                    support = when {
                        closed -> NarrationKey(NarrationId.TP_WATCH_WINDOW_CLOSED)
                        movedLeft -> NarrationKey(
                            NarrationId.TP_WATCH_MOVE_LEFT_WHY,
                            listOf(droppedValue),
                        )

                        else -> NarrationKey(
                            NarrationId.TP_WATCH_MOVE_RIGHT_WHY,
                            listOf(droppedValue),
                        )
                    },
                ),
            )
        }

        // -- The pair -----------------------------------------------------------
        if (events.any { it is VizEvent.Finalize }) {
            return listOf(
                PartialStep(
                    kind = WatchStepKind.FOUND,
                    scene = scene,
                    headline = NarrationKey(NarrationId.TP_WATCH_FOUND),
                    support = NarrationKey(
                        NarrationId.TP_WATCH_FOUND_SUPPORT,
                        listOf(
                            requireNotNull(state.leftValue),
                            requireNotNull(state.rightValue),
                            state.target,
                        ),
                    ),
                ),
            )
        }

        // Nothing visible happened — do not manufacture a step for it.
        return emptyList()
    }

    override fun closing(
        state: TwoPointersState,
        metrics: Metrics,
        scene: Scene,
    ): List<PartialStep> = listOf(
        // The one engineered moment of the lesson, on its own step so it cannot
        // be scrolled past (PRODUCT_SPEC.md §4).
        PartialStep(
            kind = WatchStepKind.INSIGHT,
            scene = scene,
            headline = NarrationKey(NarrationId.TP_WATCH_INSIGHT),
            support = NarrationKey(
                NarrationId.TP_WATCH_INSIGHT_SUPPORT,
                listOf(state.values.size, metrics.comparisons),
            ),
        ),
        PartialStep(
            kind = WatchStepKind.SUMMARY,
            scene = scene,
            headline = NarrationKey(
                if (state.foundLeft != null) {
                    NarrationId.TP_WATCH_SUMMARY_FOUND
                } else {
                    NarrationId.TP_WATCH_SUMMARY_NO_PAIR
                },
                listOf(state.target, metrics.comparisons),
            ),
            support = NarrationKey(NarrationId.TP_WATCH_SUMMARY_SUPPORT),
            bullets = listOf(
                NarrationKey(NarrationId.TP_IDEA_1),
                NarrationKey(NarrationId.TP_IDEA_2),
                NarrationKey(NarrationId.TP_IDEA_3),
                NarrationKey(NarrationId.TP_IDEA_4),
            ),
        ),
    )
}
