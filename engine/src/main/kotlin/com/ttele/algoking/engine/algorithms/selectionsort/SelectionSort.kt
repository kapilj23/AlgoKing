package com.ttele.algoking.engine.algorithms.selectionsort

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
import com.ttele.algoking.engine.event.PointerId
import com.ttele.algoking.engine.event.RegionId
import com.ttele.algoking.engine.event.Relation
import com.ttele.algoking.engine.event.VizEvent
import com.ttele.algoking.engine.narration.NarrationId
import com.ttele.algoking.engine.narration.NarrationKey

/**
 * What the learner can do in Selection Sort — PRODUCT_SPEC.md §3.
 *
 * Two different decisions, and neither is Bubble Sort's:
 *
 *  1. **"Is this the new minimum?"** at each scanned cell — a judgement against a
 *     *remembered* candidate, not against a neighbour.
 *  2. **"Where does it go?"** once the scan is done — the front of the unsorted
 *     portion, which is what makes the sorted prefix grow.
 *
 * Advancing the scan cursor is the app's job: the next cell is always the next
 * cell, and tapping the only legal target teaches a gesture.
 */
sealed interface SelectionSortAction : Action {

    /** Move the scan on to the next unsorted cell. Mechanical, never a choice. */
    data object ScanNext : SelectionSortAction

    /** The scanned value is smaller than the remembered candidate. */
    data object NewMinimum : SelectionSortAction

    /** The scanned value is not smaller — the candidate stands. */
    data object NotSmaller : SelectionSortAction

    /** Put the confirmed minimum at [at]. Correct only at the front of the unsorted portion. */
    data class Place(val at: Int) : SelectionSortAction
}

/**
 * Immutable state.
 *
 * [sortedTo] is where the finished prefix ends — everything before it is final.
 * [cursor] is the cell being judged; when it runs past the end the scan is over
 * and [minIndex] holds the smallest remaining value.
 */
data class SelectionSortState(
    val values: List<Int>,
    /** Front of the unsorted portion; also the slot the minimum must land in. */
    val sortedTo: Int,
    /** The cell under judgement, or null once the scan has finished. */
    val cursor: Int?,
    /** The smallest value found so far in this pass. */
    val minIndex: Int,
    val pass: Int,
    val done: Boolean,
) {
    val unsorted: IntRange get() = sortedTo..values.lastIndex
    val scanComplete: Boolean get() = cursor == null
    val minValue: Int get() = values[minIndex]
}

class SelectionSortAlgorithm : Algorithm<SelectionSortState, SelectionSortAction> {

    override val id = AlgorithmId.SELECTION_SORT

    override fun initial(dataset: Dataset) = SelectionSortState(
        values = dataset.values,
        sortedTo = 0,
        // The first unsorted cell is the opening candidate; the scan starts after it.
        cursor = if (dataset.values.size > 1) 1 else null,
        minIndex = 0,
        pass = 1,
        done = dataset.values.size <= 1,
    )

    override fun probe(state: SelectionSortState): Probe<SelectionSortAction> {
        if (state.done) return Probe.Terminal(Outcome.Sorted)

        val cursor = state.cursor
        if (cursor != null) {
            // Mid-scan: judge this value against the remembered candidate.
            return Probe.Decide(judgeDecision(state, cursor))
        }

        // Scan complete: the minimum is known, and it has one correct home.
        return Probe.Decide(placeDecision(state))
    }

    // ── Decisions ─────────────────────────────────────────────────────────────

    private fun judgeDecision(
        state: SelectionSortState,
        cursor: Int,
    ): Decision<SelectionSortAction> {
        val scanned = state.values[cursor]
        val best = state.minValue
        val isSmaller = scanned < best
        val correct = if (isSmaller) {
            SelectionSortAction.NewMinimum
        } else {
            SelectionSortAction.NotSmaller
        }

        return Decision(
            kind = DecisionKind.OPTIONS,
            prompt = NarrationKey(
                when {
                    state.pass == 1 && cursor == state.sortedTo + 1 ->
                        NarrationId.SELECT_ASK_IS_SMALLER

                    state.pass == 1 -> NarrationId.SELECT_ASK_STILL_SMALLEST
                    else -> NarrationId.SELECT_ASK_YOUR_TURN
                },
                listOf(scanned, best),
            ),
            options = listOf(
                ActionOption(
                    SelectionSortAction.NewMinimum,
                    NarrationKey(NarrationId.SELECT_OPTION_NEW_MIN),
                ),
                ActionOption(
                    SelectionSortAction.NotSmaller,
                    NarrationKey(NarrationId.SELECT_OPTION_NOT_SMALLER),
                ),
            ),
            correct = correct,
            focus = listOf(cursor, state.minIndex),
            hint = NarrationKey(NarrationId.SELECT_HINT_COMPARE, listOf(scanned, best)),
            guidance = listOf(
                NarrationKey(NarrationId.SELECT_RETRY_LOOK, listOf(scanned, best)),
                NarrationKey(NarrationId.SELECT_RETRY_ASK, listOf(scanned, best)),
                NarrationKey(
                    if (isSmaller) {
                        NarrationId.SELECT_RETRY_EXPLAIN_SMALLER
                    } else {
                        NarrationId.SELECT_RETRY_EXPLAIN_LARGER
                    },
                    listOf(scanned, best),
                ),
            ),
            minimalFeedback = NarrationKey(
                if (isSmaller) NarrationId.SELECT_COMPARE_LESS else NarrationId.SELECT_COMPARE_MORE,
                listOf(scanned, best),
            ),
            whyWrong = mapOf(
                (
                    if (isSmaller) {
                        SelectionSortAction.NotSmaller
                    } else {
                        SelectionSortAction.NewMinimum
                    }
                    ) to NarrationKey(
                    if (isSmaller) {
                        NarrationId.SELECT_WHY_MISSED_SMALLER
                    } else {
                        NarrationId.SELECT_WHY_NOT_SMALLER
                    },
                    listOf(scanned, best),
                ),
            ),
            correctFeedback = NarrationKey(
                if (isSmaller) NarrationId.SELECT_NEW_MIN else NarrationId.SELECT_KEPT_MIN,
                listOf(scanned, best),
            ),
            hintLadder = listOf(
                NarrationKey(NarrationId.SELECT_HINT_COMPARE, listOf(scanned, best)),
                NarrationKey(NarrationId.SELECT_HINT_RULE),
                NarrationKey(
                    if (isSmaller) {
                        NarrationId.SELECT_RETRY_EXPLAIN_SMALLER
                    } else {
                        NarrationId.SELECT_RETRY_EXPLAIN_LARGER
                    },
                    listOf(scanned, best),
                ),
            ),
        )
    }

    private fun placeDecision(state: SelectionSortState): Decision<SelectionSortAction> =
        Decision(
            kind = DecisionKind.CELL,
            prompt = NarrationKey(NarrationId.SELECT_ASK_WHERE, listOf(state.minValue)),
            // Only the unsorted portion is offered: the sorted prefix is finished.
            options = state.unsorted.map { slot ->
                ActionOption(
                    action = SelectionSortAction.Place(slot),
                    label = NarrationKey(NarrationId.SELECT_OPTION_PLACE),
                    slot = slot,
                )
            },
            correct = SelectionSortAction.Place(state.sortedTo),
            focus = listOf(state.minIndex, state.sortedTo),
            hint = NarrationKey(NarrationId.SELECT_HINT_PLACE),
            guidance = listOf(
                NarrationKey(NarrationId.SELECT_RETRY_PLACE_LOOK),
                NarrationKey(NarrationId.SELECT_RETRY_PLACE_ASK),
                NarrationKey(
                    NarrationId.SELECT_RETRY_PLACE_EXPLAIN,
                    listOf(state.minValue),
                ),
            ),
            minimalFeedback = NarrationKey(NarrationId.SELECT_RETRY_PLACE_LOOK),
            correctFeedback = NarrationKey(
                NarrationId.SELECT_PLACED,
                listOf(state.minValue),
            ),
            hintLadder = listOf(
                NarrationKey(NarrationId.SELECT_HINT_PLACE),
                NarrationKey(NarrationId.SELECT_RETRY_PLACE_ASK),
                NarrationKey(NarrationId.SELECT_RETRY_PLACE_EXPLAIN, listOf(state.minValue)),
            ),
        )

    // ── Transitions ───────────────────────────────────────────────────────────

    override fun apply(
        state: SelectionSortState,
        action: SelectionSortAction,
    ): Transition<SelectionSortState> = when (action) {
        SelectionSortAction.ScanNext -> advance(state, takeCandidate = false)
        SelectionSortAction.NewMinimum -> advance(state, takeCandidate = true)
        SelectionSortAction.NotSmaller -> advance(state, takeCandidate = false)
        is SelectionSortAction.Place -> place(state, action.at)
    }

    /**
     * Judge the scanned cell and move on. Whether the candidate changes is the
     * learner's call; where the cursor goes next never is.
     */
    private fun advance(
        state: SelectionSortState,
        takeCandidate: Boolean,
    ): Transition<SelectionSortState> {
        val cursor = requireNotNull(state.cursor)
        val scanned = state.values[cursor]
        val best = state.minValue
        val shouldTake = scanned < best

        val minIndex = if (takeCandidate) cursor else state.minIndex
        val nextCursor = (cursor + 1).takeIf { it <= state.values.lastIndex }

        return Transition(
            next = state.copy(minIndex = minIndex, cursor = nextCursor),
            events = buildList {
                add(VizEvent.Examine(listOf(cursor), ExamineRole.CANDIDATE))
                add(
                    VizEvent.Compare(
                        cursor,
                        state.minIndex,
                        if (shouldTake) Relation.LESS else Relation.GREATER,
                    ),
                )
                if (takeCandidate) add(VizEvent.Mark(minIndex, MarkId.MIN_SO_FAR))
                add(VizEvent.MovePointer(PointerId.I, nextCursor))
            },
            narration = NarrationKey(
                if (takeCandidate) NarrationId.SELECT_NEW_MIN else NarrationId.SELECT_KEPT_MIN,
                listOf(scanned, best),
            ),
            correct = takeCandidate == shouldTake,
        )
    }

    /**
     * Place the minimum. Applying a *wrong* slot is the same code path with a
     * different argument; validation is what stops it ever being called that way.
     */
    private fun place(state: SelectionSortState, at: Int): Transition<SelectionSortState> {
        val minValue = state.minValue
        val values = state.values.toMutableList().also {
            val moved = it.removeAt(state.minIndex)
            it.add(at.coerceIn(0, it.size), moved)
        }

        val nextSorted = state.sortedTo + 1
        val finished = nextSorted >= state.values.lastIndex

        return Transition(
            next = state.copy(
                values = values,
                sortedTo = nextSorted,
                minIndex = nextSorted.coerceAtMost(state.values.lastIndex),
                cursor = (nextSorted + 1).takeIf { !finished && it <= state.values.lastIndex },
                pass = state.pass + 1,
                done = finished,
            ),
            events = buildList {
                add(VizEvent.Swap(state.minIndex, at))
                add(VizEvent.Finalize(0..at))
                add(VizEvent.Mark(null, MarkId.MIN_SO_FAR))
                if (finished) {
                    add(VizEvent.Finalize(state.values.indices))
                    add(VizEvent.Terminal(Outcome.Sorted))
                } else {
                    add(VizEvent.Region(nextSorted..state.values.lastIndex, RegionId.SEARCH_SPACE))
                }
            },
            narration = NarrationKey(
                if (finished) NarrationId.SELECT_SORTED else NarrationId.SELECT_PLACED,
                listOf(minValue),
            ),
            correct = at == state.sortedTo,
        )
    }
}
