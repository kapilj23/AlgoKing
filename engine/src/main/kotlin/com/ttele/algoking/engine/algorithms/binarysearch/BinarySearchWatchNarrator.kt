package com.ttele.algoking.engine.algorithms.binarysearch

import com.ttele.algoking.engine.core.Frame
import com.ttele.algoking.engine.event.Metrics
import com.ttele.algoking.engine.event.Outcome
import com.ttele.algoking.engine.event.PointerId
import com.ttele.algoking.engine.event.Relation
import com.ttele.algoking.engine.event.VizEvent
import com.ttele.algoking.engine.narration.NarrationId
import com.ttele.algoking.engine.narration.NarrationKey
import com.ttele.algoking.engine.scene.Scene
import com.ttele.algoking.engine.walkthrough.ComparisonReadout
import com.ttele.algoking.engine.decision.MidpointReadout
import com.ttele.algoking.engine.walkthrough.PartialStep
import com.ttele.algoking.engine.walkthrough.WatchNarrator
import com.ttele.algoking.engine.walkthrough.WatchStepKind

/**
 * The Binary Search walkthrough, one beat at a time.
 *
 * Every step answers "what did the algorithm just do?", and no step exists where
 * nothing changed. A single `CheckMiddle` transition becomes two beats — the
 * highlight, then the comparison — because those are two separate things to
 * understand, and the comparison chip is the visible change between them.
 */
class BinarySearchWatchNarrator : WatchNarrator<BinarySearchState> {

    override fun opening(
        state: BinarySearchState,
        scene: Scene,
    ): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.SETUP,
            scene = scene,
            headline = NarrationKey(NarrationId.BS_WATCH_SETUP, listOf(state.target)),
            support = NarrationKey(NarrationId.BS_WATCH_SETUP_SUPPORT),
        ),
    )

    override fun onFrame(
        previous: BinarySearchState,
        frame: Frame<BinarySearchState>,
        scene: Scene,
    ): List<PartialStep> {
        val state = frame.state
        val events = frame.events

        // ── The middle was chosen: highlight, then state the comparison ────────
        val movedMid = events.filterIsInstance<VizEvent.MovePointer>()
            .firstOrNull { it.pointer == PointerId.MID && it.to != null }
        if (movedMid != null) {
            val mid = requireNotNull(state.mid)
            val midValue = state.values[mid]
            val relation = events.filterIsInstance<VizEvent.Compare>()
                .firstOrNull()?.relation ?: Relation.EQUAL
            val firstLook = previous.lo == 0 && previous.hi == state.values.lastIndex
            // `Inspect` only sets `mid`, so the range here is still the one the
            // middle was computed from — the equation on screen is self-consistent.
            val midpoint = MidpointReadout(lo = state.lo, hi = state.hi, mid = mid)

            return listOf(
                PartialStep(
                    kind = WatchStepKind.EXAMINE,
                    scene = scene,
                    headline = NarrationKey(
                        if (firstLook) {
                            NarrationId.BS_WATCH_CHECK_MIDDLE
                        } else {
                            NarrationId.BS_WATCH_CHECK_MIDDLE_AGAIN
                        },
                    ),
                    // The support line stops being trivia and starts being the
                    // arithmetic: a learner who cannot reproduce `mid` has not
                    // learned Binary Search, they have watched one.
                    support = NarrationKey(
                        if (midpoint.exact) {
                            NarrationId.BS_WATCH_MID_FORMULA
                        } else {
                            NarrationId.BS_WATCH_MID_FORMULA_ROUNDED
                        },
                        listOf(midpoint.span, midpoint.lo, midpoint.hi, midpoint.mid),
                    ),
                    midpoint = midpoint,
                ),
                PartialStep(
                    kind = WatchStepKind.COMPARE,
                    scene = scene,
                    headline = NarrationKey(
                        when (relation) {
                            Relation.LESS -> NarrationId.BS_WATCH_COMPARE_LESS
                            Relation.GREATER -> NarrationId.BS_WATCH_COMPARE_GREATER
                            Relation.EQUAL -> NarrationId.BS_WATCH_COMPARE_EQUAL
                        },
                        listOf(midValue, state.target),
                    ),
                    support = when (relation) {
                        Relation.LESS -> NarrationKey(NarrationId.BS_WATCH_MUST_BE_RIGHT)
                        Relation.GREATER -> NarrationKey(NarrationId.BS_WATCH_MUST_BE_LEFT)
                        Relation.EQUAL -> null
                    },
                    comparison = ComparisonReadout(midValue, relation, state.target),
                ),
            )
        }

        // ── Half the search space left ────────────────────────────────────────
        val eliminated = events.filterIsInstance<VizEvent.Eliminate>().firstOrNull()
        if (eliminated != null) {
            val keptLeft = eliminated.range.last == previous.hi
            val empty = state.remaining == 0
            return listOf(
                PartialStep(
                    kind = if (empty) WatchStepKind.NOT_FOUND else WatchStepKind.ELIMINATE,
                    scene = scene,
                    headline = if (empty) {
                        NarrationKey(NarrationId.BS_WATCH_NOT_FOUND, listOf(state.target))
                    } else {
                        NarrationKey(
                            if (keptLeft) {
                                NarrationId.BS_WATCH_MUST_BE_LEFT
                            } else {
                                NarrationId.BS_WATCH_MUST_BE_RIGHT_TARGET
                            },
                            listOf(state.target),
                        )
                    },
                    support = NarrationKey(
                        if (empty) {
                            NarrationId.BS_WATCH_RANGE_EMPTY
                        } else if (keptLeft) {
                            NarrationId.BS_WATCH_IGNORE_RIGHT
                        } else {
                            NarrationId.BS_WATCH_IGNORE_LEFT
                        },
                        listOf(eliminated.range.count()),
                    ),
                ),
            )
        }

        // ── Found ─────────────────────────────────────────────────────────────
        if (events.any { it is VizEvent.Finalize }) {
            return listOf(
                PartialStep(
                    kind = WatchStepKind.FOUND,
                    scene = scene,
                    headline = NarrationKey(NarrationId.BS_WATCH_FOUND),
                    support = NarrationKey(
                        NarrationId.BS_WATCH_FOUND_SUPPORT,
                        listOf(frame.metrics.comparisons, state.values.size),
                    ),
                ),
            )
        }

        // Nothing visible happened — do not manufacture a step for it.
        return emptyList()
    }

    override fun closing(
        state: BinarySearchState,
        metrics: Metrics,
        scene: Scene,
    ): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.INSIGHT,
            scene = scene,
            headline = NarrationKey(NarrationId.BS_WATCH_INSIGHT),
            support = NarrationKey(NarrationId.BS_WATCH_INSIGHT_SUPPORT),
        ),
        PartialStep(
            kind = WatchStepKind.SUMMARY,
            scene = scene,
            headline = NarrationKey(
                if (state.foundAt != null) {
                    NarrationId.BS_WATCH_SUMMARY_FOUND
                } else {
                    NarrationId.BS_WATCH_SUMMARY_NOT_FOUND
                },
                listOf(state.target, metrics.comparisons, state.values.size),
            ),
            support = NarrationKey(NarrationId.BS_WATCH_SUMMARY_SUPPORT),
            bullets = listOf(
                NarrationKey(NarrationId.BS_IDEA_1),
                NarrationKey(NarrationId.BS_IDEA_2),
                NarrationKey(NarrationId.BS_IDEA_3),
                NarrationKey(NarrationId.BS_IDEA_4),
            ),
        ),
    )
}

/** Convenience: the outcome a script ends on, for the UI's completion state. */
fun BinarySearchState.outcome(): Outcome =
    foundAt?.let { Outcome.Found(it) } ?: Outcome.NotFound
