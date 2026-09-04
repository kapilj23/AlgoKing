package com.ttele.algoking.engine.algorithms.insertionsort

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
 * What the learner can do in Insertion Sort — PRODUCT_SPEC.md §3.
 *
 * The decision is **shift or insert**, judged against a key the algorithm is
 * holding *outside* the array. That is what makes Insertion Sort different from
 * Bubble Sort: nothing is ever exchanged. A larger value moves one place right
 * into the gap, the gap travels left, and the key drops into it at the end.
 */
sealed interface InsertionSortAction : Action {

    /** Lift the next unsorted value out of the array. Mechanical, never a choice. */
    data object LiftKey : InsertionSortAction

    /** The compared value is larger than the key — move it right into the gap. */
    data object Shift : InsertionSortAction

    /** Nothing larger remains — drop the key into the gap. */
    data object Insert : InsertionSortAction
}

/**
 * Immutable state.
 *
 * [key] is held out of the array while [holeAt] marks the gap it left. Shifting
 * moves a value into the gap and walks the gap one place left; inserting fills it.
 * [sortedTo] is the size of the always-sorted prefix.
 */
data class InsertionSortState(
    val values: List<Int>,
    /** The value being placed, or null between insertions. */
    val key: Int?,
    /** Where the gap is. Always inside the sorted prefix while a key is lifted. */
    val holeAt: Int?,
    /** The value being judged against the key, or null once the gap reaches the front. */
    val comparingAt: Int?,
    val sortedTo: Int,
    val pass: Int,
    val done: Boolean,
) {
    val holding: Boolean get() = key != null
    val sortedRange: IntRange get() = 0 until sortedTo
    val comparedValue: Int? get() = comparingAt?.let { values[it] }
}

class InsertionSortAlgorithm : Algorithm<InsertionSortState, InsertionSortAction> {

    override val id = AlgorithmId.INSERTION_SORT

    override fun initial(dataset: Dataset) = InsertionSortState(
        values = dataset.values,
        key = null,
        holeAt = null,
        comparingAt = null,
        // A single value is trivially a sorted prefix.
        sortedTo = 1,
        pass = 1,
        done = dataset.values.size <= 1,
    )

    override fun probe(state: InsertionSortState): Probe<InsertionSortAction> {
        if (state.done) return Probe.Terminal(Outcome.Sorted)

        // The app picks the next key: which value comes next is never a judgement.
        if (!state.holding) return Probe.Mechanical(InsertionSortAction.LiftKey)

        return Probe.Decide(decision(state))
    }

    private fun decision(state: InsertionSortState): Decision<InsertionSortAction> {
        val key = requireNotNull(state.key)
        val compared = state.comparedValue
        // Nothing to the left, or the left neighbour is already smaller: it goes here.
        val shouldShift = compared != null && compared > key
        val correct = if (shouldShift) {
            InsertionSortAction.Shift
        } else {
            InsertionSortAction.Insert
        }

        val args = listOf(compared ?: key, key)

        return Decision(
            kind = DecisionKind.OPTIONS,
            prompt = NarrationKey(
                when {
                    compared == null -> NarrationId.INSERT_ASK_NOTHING_LEFT
                    state.pass <= 2 -> NarrationId.INSERT_ASK_WHAT_HAPPENS
                    state.pass == 3 -> NarrationId.INSERT_ASK_NEXT_MOVE
                    else -> NarrationId.INSERT_ASK_YOUR_MOVE
                },
                args,
            ),
            options = listOf(
                ActionOption(
                    InsertionSortAction.Shift,
                    NarrationKey(NarrationId.INSERT_OPTION_SHIFT),
                ),
                ActionOption(
                    InsertionSortAction.Insert,
                    NarrationKey(NarrationId.INSERT_OPTION_INSERT),
                ),
            ),
            correct = correct,
            focus = listOfNotNull(state.comparingAt, state.holeAt),
            hint = NarrationKey(NarrationId.INSERT_HINT_COMPARE, args),
            guidance = listOf(
                NarrationKey(NarrationId.INSERT_RETRY_LOOK, args),
                NarrationKey(NarrationId.INSERT_RETRY_ASK, args),
                NarrationKey(
                    when {
                        compared == null -> NarrationId.INSERT_RETRY_EXPLAIN_FRONT
                        shouldShift -> NarrationId.INSERT_RETRY_EXPLAIN_SHIFT
                        else -> NarrationId.INSERT_RETRY_EXPLAIN_INSERT
                    },
                    args,
                ),
            ),
            minimalFeedback = NarrationKey(
                when {
                    compared == null -> NarrationId.INSERT_NOTHING_LEFT
                    shouldShift -> NarrationId.INSERT_COMPARE_LARGER
                    else -> NarrationId.INSERT_COMPARE_SMALLER
                },
                args,
            ),
            whyWrong = mapOf(
                (if (shouldShift) InsertionSortAction.Insert else InsertionSortAction.Shift) to
                    NarrationKey(
                        if (shouldShift) {
                            NarrationId.INSERT_WHY_TOO_EARLY
                        } else {
                            NarrationId.INSERT_WHY_NO_SHIFT
                        },
                        args,
                    ),
            ),
            correctFeedback = NarrationKey(
                when {
                    shouldShift -> NarrationId.INSERT_SHIFTED
                    compared == null -> NarrationId.INSERT_INSERTED_FRONT
                    else -> NarrationId.INSERT_INSERTED
                },
                args,
            ),
            hintLadder = listOf(
                NarrationKey(NarrationId.INSERT_HINT_COMPARE, args),
                NarrationKey(NarrationId.INSERT_HINT_RULE),
                NarrationKey(
                    when {
                        compared == null -> NarrationId.INSERT_RETRY_EXPLAIN_FRONT
                        shouldShift -> NarrationId.INSERT_RETRY_EXPLAIN_SHIFT
                        else -> NarrationId.INSERT_RETRY_EXPLAIN_INSERT
                    },
                    args,
                ),
            ),
        )
    }

    // ── Transitions ───────────────────────────────────────────────────────────

    override fun apply(
        state: InsertionSortState,
        action: InsertionSortAction,
    ): Transition<InsertionSortState> = when (action) {
        InsertionSortAction.LiftKey -> liftKey(state)
        InsertionSortAction.Shift -> shift(state)
        InsertionSortAction.Insert -> insert(state)
    }

    /** Take the next unsorted value out of the array, leaving a gap behind it. */
    private fun liftKey(state: InsertionSortState): Transition<InsertionSortState> {
        val at = state.sortedTo
        val key = state.values[at]
        return Transition(
            next = state.copy(
                key = key,
                holeAt = at,
                comparingAt = (at - 1).takeIf { it >= 0 },
            ),
            events = listOf(
                VizEvent.Remove(at),
                VizEvent.Mark(at, MarkId.BEST),
                VizEvent.MovePointer(PointerId.J, at - 1),
                VizEvent.Examine(listOf(at - 1), ExamineRole.COMPARING),
            ),
            narration = NarrationKey(NarrationId.INSERT_KEY_TAKEN, listOf(key)),
            correct = true,
        )
    }

    /**
     * **A shift is not a swap.** One value moves right into the gap; the key is not
     * involved, and the gap walks one place left. That distinction is the whole
     * reason Insertion Sort looks different from Bubble Sort.
     */
    private fun shift(state: InsertionSortState): Transition<InsertionSortState> {
        val hole = requireNotNull(state.holeAt)
        val from = requireNotNull(state.comparingAt)
        val moved = state.values[from]
        val key = requireNotNull(state.key)

        val values = state.values.toMutableList().also { it[hole] = moved }
        val nextCompare = (from - 1).takeIf { it >= 0 }

        return Transition(
            next = state.copy(
                values = values,
                holeAt = from,
                comparingAt = nextCompare,
            ),
            events = buildList {
                // Insert-at-the-gap, not Swap: the renderer must not draw an exchange.
                add(VizEvent.Insert(moved, hole))
                add(VizEvent.Remove(from))
                add(VizEvent.MovePointer(PointerId.J, nextCompare))
                nextCompare?.let { add(VizEvent.Examine(listOf(it), ExamineRole.COMPARING)) }
            },
            narration = NarrationKey(NarrationId.INSERT_SHIFTED, listOf(moved, key)),
            correct = moved > key,
        )
    }

    /** Drop the key into the gap. The sorted prefix grows by one. */
    private fun insert(state: InsertionSortState): Transition<InsertionSortState> {
        val hole = requireNotNull(state.holeAt)
        val key = requireNotNull(state.key)
        val compared = state.comparedValue
        val values = state.values.toMutableList().also { it[hole] = key }

        val nextSorted = state.sortedTo + 1
        val finished = nextSorted >= state.values.size

        return Transition(
            next = state.copy(
                values = values,
                key = null,
                holeAt = null,
                comparingAt = null,
                sortedTo = nextSorted,
                pass = state.pass + 1,
                done = finished,
            ),
            events = buildList {
                add(VizEvent.Insert(key, hole))
                add(VizEvent.Mark(null, MarkId.BEST))
                add(VizEvent.MovePointer(PointerId.J, null))
                add(VizEvent.Finalize(0 until nextSorted))
                if (finished) {
                    add(VizEvent.Terminal(Outcome.Sorted))
                } else {
                    add(VizEvent.Region(nextSorted..state.values.lastIndex, RegionId.SEARCH_SPACE))
                }
            },
            narration = NarrationKey(
                if (finished) NarrationId.INSERT_SORTED else NarrationId.INSERT_INSERTED,
                listOf(compared ?: key, key),
            ),
            // Correct only when nothing larger is left to the left of the gap.
            correct = compared == null || compared <= key,
        )
    }
}

/** Kept beside the algorithm so narration can read the comparison direction. */
internal fun relationOf(compared: Int, key: Int): Relation = when {
    compared > key -> Relation.GREATER
    compared < key -> Relation.LESS
    else -> Relation.EQUAL
}
