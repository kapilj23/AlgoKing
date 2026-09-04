package com.ttele.algoking.engine.algorithms.quicksort

import com.ttele.algoking.engine.core.Algorithm
import com.ttele.algoking.engine.core.AlgorithmId
import com.ttele.algoking.engine.core.Dataset
import com.ttele.algoking.engine.core.Probe
import com.ttele.algoking.engine.core.Transition
import com.ttele.algoking.engine.decision.Action
import com.ttele.algoking.engine.decision.ActionOption
import com.ttele.algoking.engine.decision.Decision
import com.ttele.algoking.engine.decision.DecisionKind
import com.ttele.algoking.engine.event.ExamineRole
import com.ttele.algoking.engine.event.MarkId
import com.ttele.algoking.engine.event.Outcome
import com.ttele.algoking.engine.event.RegionId
import com.ttele.algoking.engine.event.Relation
import com.ttele.algoking.engine.event.VizEvent
import com.ttele.algoking.engine.narration.NarrationId
import com.ttele.algoking.engine.narration.NarrationKey

/**
 * What the learner can do in Quick Sort.
 *
 *  1. **Which side of the pivot does this value belong on?** — asked for every
 *     value in the partition. This is partitioning, and it is the whole idea.
 *  2. **Where does the pivot end up?** — asked once per partition, because the
 *     answer *is* the insight: the pivot lands between the two groups, finished.
 *
 * Choosing the pivot and picking the next partition are the app's job — neither
 * is a judgement the learner is being taught here.
 */
sealed interface QuickSortAction : Action {

    /** Take the last value of the partition as the pivot. Mechanical. */
    data object SelectPivot : QuickSortAction

    /** The scanned value is at most the pivot: it belongs on the left. */
    data object ClassifyLeft : QuickSortAction

    /** The scanned value is greater than the pivot: it belongs on the right. */
    data object ClassifyRight : QuickSortAction

    /** Drop the pivot between the two groups. [at] is the slot the learner chose. */
    data class PlacePivot(val at: Int) : QuickSortAction

    /** Move on to the next partition still waiting. Mechanical. */
    data object NextPartition : QuickSortAction
}

/**
 * Immutable state — Lomuto partitioning, which is the shape that renders honestly:
 * the left group really does grow from `lo`, and the pivot really does land at
 * `boundary`.
 *
 * ```
 * [lo, boundary)  classified ≤ pivot          the left group
 * [boundary, cursor)  classified > pivot      the right group
 * cursor          the value being judged
 * hi              the pivot
 * ```
 *
 * **Pivot strategy: the last value of the current partition.** Deterministic on
 * purpose — a beginner cannot build a mental model against a moving target.
 *
 * **Values equal to the pivot go left.** The comparison is `<=`, so duplicates
 * never bounce between partitions and the pivot's final slot stays correct.
 */
data class QuickSortState(
    val values: List<Int>,
    /** The partition being worked on. `lo > hi` means there is none right now. */
    val lo: Int,
    val hi: Int,
    val pivotAt: Int?,
    /** The value being classified, or null once every value has been. */
    val cursor: Int?,
    /** First index of the "greater than pivot" group. */
    val boundary: Int,
    /** Pivots that have reached their final position. */
    val finalized: Set<Int>,
    /** Partitions still waiting, innermost last. */
    val pending: List<IntRange>,
    val done: Boolean,
) {
    val active: Boolean get() = lo <= hi
    val partition: IntRange get() = lo..hi
    val pivot: Int? get() = pivotAt?.let { values[it] }
    val scanning: Boolean get() = cursor != null && cursor < hi
    val scanned: Int? get() = cursor?.takeIf { it < hi }?.let { values[it] }

    /** Where the pivot will land: between the two groups. */
    val pivotHome: Int get() = boundary

    /** The left group, the right group, and what has not been judged yet. */
    fun groups(): List<IntRange> {
        if (!active || pivotAt == null) return emptyList()
        val at = cursor ?: hi
        return buildList {
            if (boundary > lo) add(lo until boundary)
            if (at > boundary) add(boundary until at)
            if (hi > at) add(at until hi)
            add(hi..hi)
        }
    }
}

class QuickSortAlgorithm : Algorithm<QuickSortState, QuickSortAction> {

    override val id = AlgorithmId.QUICK_SORT

    override fun initial(dataset: Dataset) = QuickSortState(
        values = dataset.values,
        lo = 0,
        hi = dataset.values.lastIndex,
        pivotAt = null,
        cursor = null,
        boundary = 0,
        finalized = emptySet(),
        pending = emptyList(),
        done = dataset.values.size <= 1,
    )

    override fun probe(state: QuickSortState): Probe<QuickSortAction> {
        if (state.done) return Probe.Terminal(Outcome.Sorted)

        // No live partition, or one too small to partition: move on.
        if (!state.active || state.hi - state.lo < 1) {
            return Probe.Mechanical(QuickSortAction.NextPartition)
        }

        // The pivot is always the last value here — not something to judge.
        if (state.pivotAt == null) return Probe.Mechanical(QuickSortAction.SelectPivot)

        return if (state.scanning) {
            Probe.Decide(classifyDecision(state))
        } else {
            Probe.Decide(placeDecision(state))
        }
    }

    // ── Decisions ─────────────────────────────────────────────────────────────

    private fun classifyDecision(state: QuickSortState): Decision<QuickSortAction> {
        val value = requireNotNull(state.scanned)
        val pivot = requireNotNull(state.pivot)
        // Equal values go left, so the pivot's home stays correct with duplicates.
        val goesLeft = value <= pivot
        val correct = if (goesLeft) {
            QuickSortAction.ClassifyLeft
        } else {
            QuickSortAction.ClassifyRight
        }
        val args = listOf(value, pivot)
        val first = state.cursor == state.lo

        return Decision(
            kind = DecisionKind.OPTIONS,
            prompt = NarrationKey(
                when {
                    first -> NarrationId.QUICK_ASK_COMPARE
                    state.finalized.isEmpty() -> NarrationId.QUICK_ASK_WHICH_SIDE
                    else -> NarrationId.QUICK_ASK_YOUR_MOVE
                },
                args,
            ),
            options = listOf(
                ActionOption(
                    QuickSortAction.ClassifyLeft,
                    NarrationKey(NarrationId.QUICK_OPTION_LEFT),
                ),
                ActionOption(
                    QuickSortAction.ClassifyRight,
                    NarrationKey(NarrationId.QUICK_OPTION_RIGHT),
                ),
            ),
            correct = correct,
            focus = listOfNotNull(state.cursor, state.pivotAt),
            hint = NarrationKey(NarrationId.QUICK_HINT_COMPARE, args),
            guidance = listOf(
                NarrationKey(NarrationId.QUICK_RETRY_LOOK, args),
                NarrationKey(NarrationId.QUICK_RETRY_ASK, args),
                NarrationKey(
                    if (goesLeft) {
                        NarrationId.QUICK_RETRY_EXPLAIN_LEFT
                    } else {
                        NarrationId.QUICK_RETRY_EXPLAIN_RIGHT
                    },
                    args,
                ),
            ),
            minimalFeedback = NarrationKey(
                if (goesLeft) NarrationId.QUICK_COMPARE_LESS else NarrationId.QUICK_COMPARE_MORE,
                args,
            ),
            whyWrong = mapOf(
                (if (goesLeft) QuickSortAction.ClassifyRight else QuickSortAction.ClassifyLeft) to
                    NarrationKey(
                        if (goesLeft) {
                            NarrationId.QUICK_WHY_NOT_RIGHT
                        } else {
                            NarrationId.QUICK_WHY_NOT_LEFT
                        },
                        args,
                    ),
            ),
            correctFeedback = NarrationKey(
                if (goesLeft) NarrationId.QUICK_WENT_LEFT else NarrationId.QUICK_WENT_RIGHT,
                args,
            ),
            hintLadder = listOf(
                NarrationKey(NarrationId.QUICK_HINT_COMPARE, args),
                NarrationKey(NarrationId.QUICK_HINT_RULE),
                NarrationKey(
                    if (goesLeft) {
                        NarrationId.QUICK_RETRY_EXPLAIN_LEFT
                    } else {
                        NarrationId.QUICK_RETRY_EXPLAIN_RIGHT
                    },
                    args,
                ),
            ),
        )
    }

    private fun placeDecision(state: QuickSortState): Decision<QuickSortAction> {
        val pivot = requireNotNull(state.pivot)
        return Decision(
            kind = DecisionKind.CELL,
            prompt = NarrationKey(NarrationId.QUICK_ASK_PIVOT_HOME, listOf(pivot)),
            options = state.partition.map { slot ->
                ActionOption(
                    action = QuickSortAction.PlacePivot(slot),
                    label = NarrationKey(NarrationId.QUICK_OPTION_PLACE),
                    slot = slot,
                )
            },
            correct = QuickSortAction.PlacePivot(state.pivotHome),
            focus = listOf(state.pivotHome),
            hint = NarrationKey(NarrationId.QUICK_HINT_PIVOT_HOME),
            guidance = listOf(
                NarrationKey(NarrationId.QUICK_RETRY_PLACE_LOOK),
                NarrationKey(NarrationId.QUICK_RETRY_PLACE_ASK, listOf(pivot)),
                NarrationKey(NarrationId.QUICK_RETRY_PLACE_EXPLAIN, listOf(pivot)),
            ),
            minimalFeedback = NarrationKey(NarrationId.QUICK_RETRY_PLACE_LOOK),
            correctFeedback = NarrationKey(NarrationId.QUICK_PIVOT_FINAL, listOf(pivot)),
            hintLadder = listOf(
                NarrationKey(NarrationId.QUICK_HINT_PIVOT_HOME),
                NarrationKey(NarrationId.QUICK_RETRY_PLACE_ASK, listOf(pivot)),
                NarrationKey(NarrationId.QUICK_RETRY_PLACE_EXPLAIN, listOf(pivot)),
            ),
        )
    }

    // ── Transitions ───────────────────────────────────────────────────────────

    override fun apply(
        state: QuickSortState,
        action: QuickSortAction,
    ): Transition<QuickSortState> = when (action) {
        QuickSortAction.SelectPivot -> selectPivot(state)
        QuickSortAction.ClassifyLeft -> classify(state, left = true)
        QuickSortAction.ClassifyRight -> classify(state, left = false)
        is QuickSortAction.PlacePivot -> placePivot(state, action.at)
        QuickSortAction.NextPartition -> nextPartition(state)
    }

    private fun selectPivot(state: QuickSortState): Transition<QuickSortState> = Transition(
        next = state.copy(pivotAt = state.hi, cursor = state.lo, boundary = state.lo),
        events = listOf(
            VizEvent.Mark(state.hi, MarkId.BEST),
            VizEvent.Region(state.partition, RegionId.SEARCH_SPACE),
            VizEvent.Examine(listOf(state.lo), ExamineRole.COMPARING),
        ),
        narration = NarrationKey(NarrationId.QUICK_PIVOT_IS, listOf(state.values[state.hi])),
        correct = true,
    )

    /**
     * Lomuto: a value that belongs left is exchanged into the growing left group;
     * one that belongs right simply stays where it is and the cursor moves on.
     */
    private fun classify(state: QuickSortState, left: Boolean): Transition<QuickSortState> {
        val cursor = requireNotNull(state.cursor)
        val value = state.values[cursor]
        val pivot = requireNotNull(state.pivot)
        val shouldGoLeft = value <= pivot

        val values = if (left && cursor != state.boundary) {
            state.values.toMutableList().also {
                val tmp = it[state.boundary]
                it[state.boundary] = it[cursor]
                it[cursor] = tmp
            }
        } else {
            state.values
        }

        return Transition(
            next = state.copy(
                values = values,
                boundary = if (left) state.boundary + 1 else state.boundary,
                cursor = cursor + 1,
            ),
            events = buildList {
                add(
                    VizEvent.Compare(
                        cursor,
                        requireNotNull(state.pivotAt),
                        if (shouldGoLeft) Relation.LESS else Relation.GREATER,
                    ),
                )
                if (left && cursor != state.boundary) add(VizEvent.Swap(state.boundary, cursor))
                if (cursor + 1 < state.hi) {
                    add(VizEvent.Examine(listOf(cursor + 1), ExamineRole.COMPARING))
                }
            },
            narration = NarrationKey(
                if (left) NarrationId.QUICK_WENT_LEFT else NarrationId.QUICK_WENT_RIGHT,
                listOf(value, pivot),
            ),
            correct = left == shouldGoLeft,
        )
    }

    /**
     * The pivot drops between the two groups — and that slot is final. This is the
     * moment the lesson exists for.
     */
    private fun placePivot(state: QuickSortState, at: Int): Transition<QuickSortState> {
        val pivotAt = requireNotNull(state.pivotAt)
        val pivot = state.values[pivotAt]
        val values = state.values.toMutableList().also {
            val tmp = it[at]
            it[at] = it[pivotAt]
            it[pivotAt] = tmp
        }

        // Both sides still need work; the smaller side is queued last so it is
        // taken first, which keeps the learner inside one region at a time.
        val queued = buildList {
            addAll(state.pending)
            if (at + 1 < state.hi) add((at + 1)..state.hi)
            if (state.lo < at - 1) add(state.lo..(at - 1))
        }

        return Transition(
            next = state.copy(
                values = values,
                lo = 1,
                hi = 0,
                pivotAt = null,
                cursor = null,
                finalized = state.finalized + at,
                pending = queued,
            ),
            events = buildList {
                if (at != pivotAt) add(VizEvent.Swap(at, pivotAt))
                add(VizEvent.Finalize(at..at))
                add(VizEvent.Mark(null, MarkId.BEST))
            },
            narration = NarrationKey(NarrationId.QUICK_PIVOT_FINAL, listOf(pivot)),
            correct = at == state.pivotHome,
        )
    }

    /** Take the next waiting partition; single values are already final. */
    private fun nextPartition(state: QuickSortState): Transition<QuickSortState> {
        var finalized = state.finalized
        var queue = state.pending

        // Anything already in the current window that is a single value is done.
        if (state.active && state.hi == state.lo) finalized = finalized + state.lo

        while (queue.isNotEmpty()) {
            val next = queue.last()
            queue = queue.dropLast(1)
            if (next.first == next.last) {
                finalized = finalized + next.first
                continue
            }
            return Transition(
                next = state.copy(
                    lo = next.first,
                    hi = next.last,
                    pivotAt = null,
                    cursor = null,
                    boundary = next.first,
                    finalized = finalized,
                    pending = queue,
                ),
                events = listOf(VizEvent.Region(next, RegionId.SEARCH_SPACE)),
                narration = NarrationKey(
                    NarrationId.QUICK_NEXT_PARTITION,
                    listOf(next.count()),
                ),
                correct = true,
            )
        }

        return Transition(
            next = state.copy(
                lo = 1,
                hi = 0,
                pivotAt = null,
                cursor = null,
                finalized = state.values.indices.toSet(),
                pending = emptyList(),
                done = true,
            ),
            events = listOf(
                VizEvent.Finalize(state.values.indices),
                VizEvent.Terminal(Outcome.Sorted),
            ),
            narration = NarrationKey(NarrationId.QUICK_SORTED),
            correct = true,
        )
    }
}
