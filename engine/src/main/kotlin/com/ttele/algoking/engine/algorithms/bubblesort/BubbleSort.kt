package com.ttele.algoking.engine.algorithms.bubblesort

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
import com.ttele.algoking.engine.event.Outcome
import com.ttele.algoking.engine.event.PointerId
import com.ttele.algoking.engine.event.Relation
import com.ttele.algoking.engine.event.VizEvent
import com.ttele.algoking.engine.narration.NarrationId
import com.ttele.algoking.engine.narration.NarrationKey

/**
 * What the learner can do in Bubble Sort — PRODUCT_SPEC.md §3.
 *
 * The decision is **swap or keep**, and only that. Which pair is under comparison
 * is the algorithm's business: the next pair is always the next pair, so asking the
 * learner to pick it would teach a gesture rather than the comparison logic.
 */
sealed interface BubbleSortAction : Action {

    /** Bring the next adjacent pair under comparison. Mechanical, never a choice. */
    data object FocusPair : BubbleSortAction

    /** The left value is greater — exchange them. */
    data object Swap : BubbleSortAction

    /** Already in order — examine, then deliberately do nothing. */
    data object Keep : BubbleSortAction

    /** End of the unsorted region: finalise one value and start the next pass. */
    data object CompletePass : BubbleSortAction
}

/**
 * Immutable state.
 *
 * [sortedFrom] is the index where the finished suffix begins — everything at or
 * after it has bubbled into its final position. [swappedThisPass] is what makes
 * early termination possible: a pass that moves nothing proves the array is sorted.
 */
data class BubbleSortState(
    val values: List<Int>,
    /** Left index of the current pair. */
    val i: Int,
    /** 1-based, for narration. */
    val pass: Int,
    val sortedFrom: Int,
    val swappedThisPass: Boolean,
    /** True once the pair has been brought under comparison. */
    val focused: Boolean,
    val comparisonsThisPass: Int,
    val done: Boolean,
) {
    val pairEnd: Int get() = i + 1
    val hasPair: Boolean get() = i + 1 < sortedFrom
    val unsorted: IntRange get() = 0 until sortedFrom
}

class BubbleSortAlgorithm : Algorithm<BubbleSortState, BubbleSortAction> {

    override val id = AlgorithmId.BUBBLE_SORT

    override fun initial(dataset: Dataset) = BubbleSortState(
        values = dataset.values,
        i = 0,
        pass = 1,
        sortedFrom = dataset.values.size,
        swappedThisPass = false,
        focused = false,
        comparisonsThisPass = 0,
        done = dataset.values.size <= 1,
    )

    override fun probe(state: BubbleSortState): Probe<BubbleSortAction> {
        if (state.done) return Probe.Terminal(Outcome.Sorted)

        // The pass ran off the end of the unsorted region.
        if (!state.hasPair) return Probe.Mechanical(BubbleSortAction.CompletePass)

        // The app advances the pair pointer; the learner never picks it.
        if (!state.focused) return Probe.Mechanical(BubbleSortAction.FocusPair)

        return Probe.Decide(decision(state))
    }

    private fun decision(state: BubbleSortState): Decision<BubbleSortAction> {
        val left = state.values[state.i]
        val right = state.values[state.pairEnd]
        val shouldSwap = left > right
        val correct = if (shouldSwap) BubbleSortAction.Swap else BubbleSortAction.Keep

        return Decision(
            kind = DecisionKind.OPTIONS,
            // Guidance thins out as the learner settles into the mechanic.
            prompt = NarrationKey(
                when {
                    state.pass == 1 && state.comparisonsThisPass <= 1 ->
                        NarrationId.BUBBLE_ASK_WHICH_ACTION

                    state.pass == 1 -> NarrationId.BUBBLE_ASK_WHAT_HAPPENS
                    else -> NarrationId.BUBBLE_ASK_YOUR_MOVE
                },
            ),
            options = listOf(
                ActionOption(BubbleSortAction.Swap, NarrationKey(NarrationId.BUBBLE_OPTION_SWAP)),
                ActionOption(BubbleSortAction.Keep, NarrationKey(NarrationId.BUBBLE_OPTION_KEEP)),
            ),
            correct = correct,
            focus = listOf(state.i, state.pairEnd),
            hint = NarrationKey(NarrationId.BUBBLE_HINT, listOf(left, right)),
            // Least to most explicit — Try climbs this; the state never moves.
            guidance = listOf(
                NarrationKey(NarrationId.BUBBLE_RETRY_LOOK_AGAIN),
                NarrationKey(NarrationId.BUBBLE_RETRY_ASK_ORDER, listOf(left, right)),
                NarrationKey(
                    if (shouldSwap) {
                        NarrationId.BUBBLE_RETRY_EXPLAIN_SWAP
                    } else {
                        NarrationId.BUBBLE_RETRY_EXPLAIN_KEEP
                    },
                    listOf(left, right),
                ),
            ),
            // Challenge restates the comparison and stops there.
            minimalFeedback = NarrationKey(
                if (shouldSwap) {
                    NarrationId.BUBBLE_COMPARE_GREATER
                } else {
                    NarrationId.BUBBLE_COMPARE_LESS
                },
                listOf(left, right),
            ),
            whyWrong = mapOf(
                (if (shouldSwap) BubbleSortAction.Keep else BubbleSortAction.Swap) to
                    NarrationKey(
                        if (shouldSwap) {
                            NarrationId.BUBBLE_WHY_KEEP_WRONG
                        } else {
                            NarrationId.BUBBLE_WHY_SWAP_WRONG
                        },
                        listOf(left, right),
                    ),
            ),
            correctFeedback = NarrationKey(
                if (shouldSwap) NarrationId.BUBBLE_SWAPPED else NarrationId.BUBBLE_KEPT,
                listOf(left, right),
            ),
            hintLadder = listOf(
                NarrationKey(NarrationId.BUBBLE_HINT_LOOK, listOf(left, right)),
                NarrationKey(NarrationId.BUBBLE_HINT_RULE),
                NarrationKey(
                    if (shouldSwap) {
                        NarrationId.BUBBLE_RETRY_EXPLAIN_SWAP
                    } else {
                        NarrationId.BUBBLE_RETRY_EXPLAIN_KEEP
                    },
                    listOf(left, right),
                ),
            ),
        )
    }

    // ── Transitions ───────────────────────────────────────────────────────────

    override fun apply(
        state: BubbleSortState,
        action: BubbleSortAction,
    ): Transition<BubbleSortState> = when (action) {
        BubbleSortAction.FocusPair -> focusPair(state)
        BubbleSortAction.Swap -> resolve(state, swap = true)
        BubbleSortAction.Keep -> resolve(state, swap = false)
        BubbleSortAction.CompletePass -> completePass(state)
    }

    private fun focusPair(state: BubbleSortState): Transition<BubbleSortState> {
        val left = state.values[state.i]
        val right = state.values[state.pairEnd]
        val relation = when {
            left > right -> Relation.GREATER
            left < right -> Relation.LESS
            else -> Relation.EQUAL
        }
        return Transition(
            next = state.copy(focused = true),
            events = listOf(
                VizEvent.MovePointer(PointerId.I, state.i),
                VizEvent.MovePointer(PointerId.J, state.pairEnd),
                VizEvent.Examine(listOf(state.i, state.pairEnd), ExamineRole.COMPARING),
                VizEvent.Compare(state.i, state.pairEnd, relation),
            ),
            narration = NarrationKey(
                if (relation == Relation.GREATER) {
                    NarrationId.BUBBLE_COMPARE_GREATER
                } else {
                    NarrationId.BUBBLE_COMPARE_LESS
                },
                listOf(left, right),
            ),
            correct = true,
        )
    }

    /**
     * Applying the *wrong* action is the same code path with a different argument.
     * Validation is what stops it ever being called that way.
     */
    private fun resolve(state: BubbleSortState, swap: Boolean): Transition<BubbleSortState> {
        val left = state.values[state.i]
        val right = state.values[state.pairEnd]
        val shouldSwap = left > right

        val values = if (swap) {
            state.values.toMutableList().also {
                it[state.i] = right
                it[state.pairEnd] = left
            }
        } else {
            state.values
        }

        return Transition(
            next = state.copy(
                values = values,
                i = state.i + 1,
                focused = false,
                swappedThisPass = state.swappedThisPass || swap,
                comparisonsThisPass = state.comparisonsThisPass + 1,
            ),
            events = if (swap) {
                listOf(VizEvent.Swap(state.i, state.pairEnd))
            } else {
                listOf(VizEvent.Hold(listOf(state.i, state.pairEnd)))
            },
            narration = NarrationKey(
                if (swap) NarrationId.BUBBLE_SWAPPED else NarrationId.BUBBLE_KEPT,
                listOf(left, right),
            ),
            correct = swap == shouldSwap,
        )
    }

    /**
     * End of a pass. Two ways to finish: the unsorted region shrinks to nothing, or
     * — the more interesting one — a whole pass moved nothing, which *proves* the
     * array is already sorted.
     */
    private fun completePass(state: BubbleSortState): Transition<BubbleSortState> {
        val settled = state.sortedFrom - 1
        val earlyExit = !state.swappedThisPass
        val finished = earlyExit || settled <= 1

        val next = state.copy(
            i = 0,
            pass = state.pass + 1,
            sortedFrom = if (finished) 0 else settled,
            swappedThisPass = false,
            focused = false,
            comparisonsThisPass = 0,
            done = finished,
        )

        return Transition(
            next = next,
            events = buildList {
                add(VizEvent.MovePointer(PointerId.I, null))
                add(VizEvent.MovePointer(PointerId.J, null))
                if (finished) {
                    add(VizEvent.Finalize(state.values.indices))
                    add(VizEvent.Terminal(Outcome.Sorted))
                } else {
                    add(VizEvent.Finalize(settled..settled))
                }
            },
            narration = NarrationKey(
                when {
                    earlyExit -> NarrationId.BUBBLE_NO_SWAPS
                    finished -> NarrationId.BUBBLE_SORTED
                    else -> NarrationId.BUBBLE_PASS_COMPLETE
                },
                listOf(state.values.getOrElse(settled) { 0 }, state.pass),
            ),
            correct = true,
        )
    }
}
