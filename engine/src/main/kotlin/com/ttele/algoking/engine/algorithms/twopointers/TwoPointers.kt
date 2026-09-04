package com.ttele.algoking.engine.algorithms.twopointers

import com.ttele.algoking.engine.core.Algorithm
import com.ttele.algoking.engine.core.AlgorithmId
import com.ttele.algoking.engine.core.Dataset
import com.ttele.algoking.engine.core.Probe
import com.ttele.algoking.engine.core.Transition
import com.ttele.algoking.engine.decision.Action
import com.ttele.algoking.engine.decision.ActionOption
import com.ttele.algoking.engine.decision.Decision
import com.ttele.algoking.engine.decision.DecisionKind
import com.ttele.algoking.engine.event.EliminateReason
import com.ttele.algoking.engine.event.ExamineRole
import com.ttele.algoking.engine.event.MarkId
import com.ttele.algoking.engine.event.MeterId
import com.ttele.algoking.engine.event.Outcome
import com.ttele.algoking.engine.event.PointerId
import com.ttele.algoking.engine.event.RegionId
import com.ttele.algoking.engine.event.Relation
import com.ttele.algoking.engine.event.VizEvent
import com.ttele.algoking.engine.narration.NarrationId
import com.ttele.algoking.engine.narration.NarrationKey

/**
 * What the learner can do in Two Pointers.
 *
 * Two beats per round, the same shape Binary Search uses: **the app states the
 * sum**, then **the learner decides which pointer moves**. Adding the two values
 * is arithmetic the app owns; deciding which pointer can still improve the sum is
 * the entire technique, and it is always the learner's (PRODUCT_SPEC.md §3).
 */
sealed interface TwoPointersAction : Action {

    /** Read `values[left] + values[right]`. Mechanical — there is nothing to choose. */
    data object Compare : TwoPointersAction

    /**
     * The sum is too small. Step LEFT rightwards to a larger value.
     *
     * This discards every pair `(left, k)` for `k <= right` in one move, which is
     * why the technique is linear rather than quadratic.
     */
    data object MoveLeft : TwoPointersAction

    /** The sum is too large. Step RIGHT leftwards to a smaller value. */
    data object MoveRight : TwoPointersAction

    /** `values[left] + values[right] == target`. */
    data object FoundPair : TwoPointersAction
}

/**
 * Immutable state.
 *
 * [left] and [right] bound the live window; when they meet or cross, every pair
 * has been ruled out and the run terminates with [Outcome.NotFound] — the same
 * way Binary Search's range inverting needs no special code path.
 *
 * [sum] is the *announced* sum and doubles as the beat flag: null means the pair
 * has not been read yet, non-null means the comparison is on screen and the
 * learner owes a decision. It mirrors `BinarySearchState.mid` exactly.
 */
data class TwoPointersState(
    val values: List<Int>,
    val target: Int,
    val left: Int,
    val right: Int,
    /** Null until [TwoPointersAction.Compare] has read the current pair. */
    val sum: Int?,
    val foundLeft: Int?,
    val foundRight: Int?,
    val exhausted: Boolean,
) {
    /** The live window. Empty once the pointers have met. */
    val window: IntRange get() = left..right

    /** How many values are still in play. */
    val remaining: Int get() = if (right < left) 0 else right - left + 1

    val finished: Boolean get() = foundLeft != null || exhausted

    /** True while [left] and [right] name two distinct, in-bounds cells. */
    val hasPair: Boolean
        get() = left in values.indices && right in values.indices && left < right

    val leftValue: Int? get() = values.getOrNull(left)
    val rightValue: Int? get() = values.getOrNull(right)

    /**
     * **The single source of truth for the sum.** The UI reads this; it never adds
     * two array cells itself. Null when there is no pair to add, so a crossed or
     * one-element window cannot produce a number that looks meaningful.
     */
    val currentSum: Int?
        get() = if (hasPair) values[left] + values[right] else null

    /** How [currentSum] stands against [target]. Null when there is no pair. */
    val relation: Relation?
        get() = currentSum?.let {
            when {
                it < target -> Relation.LESS
                it > target -> Relation.GREATER
                else -> Relation.EQUAL
            }
        }
}

/**
 * Two Pointers on a **sorted** array — the first Advanced lesson.
 *
 * ### The rule, and why it is the whole lesson
 *
 * ```
 * sum > target  ->  move RIGHT left   (the only way to get a smaller sum)
 * sum < target  ->  move LEFT right   (the only way to get a larger sum)
 * sum == target ->  the pair is found
 * ```
 *
 * Sortedness is what makes this sound. When the sum is too large, `values[right]`
 * is the largest value still in play, so *every* pair that includes it is at
 * least this large — the whole column can go, not just this one pair. A learner
 * who takes away only "click LEFT or RIGHT" has missed it; the narration and the
 * guidance ladder both name the reason rather than the button.
 */
class TwoPointersAlgorithm : Algorithm<TwoPointersState, TwoPointersAction> {

    override val id = AlgorithmId.TWO_POINTERS

    override fun initial(dataset: Dataset) = TwoPointersState(
        values = dataset.values,
        target = requireNotNull(dataset.target) { "Two Pointers needs a target sum." },
        left = 0,
        right = dataset.values.lastIndex,
        sum = null,
        foundLeft = null,
        foundRight = null,
        // Fewer than two values is not an error, it is an array with no pairs in it.
        exhausted = dataset.values.size < 2,
    )

    override fun probe(state: TwoPointersState): Probe<TwoPointersAction> {
        state.foundLeft?.let { return Probe.Terminal(Outcome.Found(it)) }
        if (state.exhausted || !state.hasPair) return Probe.Terminal(Outcome.NotFound)

        // Beat 1 — read the pair. Adding two numbers is not a judgement, so the
        // app does it and says the result out loud.
        if (state.sum == null) return Probe.Mechanical(TwoPointersAction.Compare)

        // Beat 2 — the decision that carries the technique.
        return Probe.Decide(moveDecision(state))
    }

    // -- The decision ---------------------------------------------------------

    private fun moveDecision(state: TwoPointersState): Decision<TwoPointersAction> {
        val sum = requireNotNull(state.currentSum)
        val leftValue = requireNotNull(state.leftValue)
        val rightValue = requireNotNull(state.rightValue)
        val relation = requireNotNull(state.relation)

        val correct: TwoPointersAction = when (relation) {
            Relation.EQUAL -> TwoPointersAction.FoundPair
            Relation.GREATER -> TwoPointersAction.MoveRight
            Relation.LESS -> TwoPointersAction.MoveLeft
        }

        // All three options, every round — including when "Pair found" is wrong.
        // Offering it only when it happens to be correct would answer the very
        // question the beat exists to ask: *is this the pair?*
        val options = listOf<ActionOption<TwoPointersAction>>(
            ActionOption(TwoPointersAction.MoveLeft, key(NarrationId.TP_OPTION_MOVE_LEFT)),
            ActionOption(TwoPointersAction.MoveRight, key(NarrationId.TP_OPTION_MOVE_RIGHT)),
            ActionOption(TwoPointersAction.FoundPair, key(NarrationId.TP_OPTION_FOUND)),
        )

        return Decision(
            kind = DecisionKind.OPTIONS,
            prompt = NarrationKey(NarrationId.TP_ASK_WHICH_POINTER, listOf(sum, state.target)),
            options = options,
            correct = correct,
            focus = listOf(state.left, state.right),
            hint = NarrationKey(
                NarrationId.TP_HINT_COMPARE,
                listOf(leftValue, rightValue, sum, state.target),
            ),
            // Least to most explicit, and the last rung still leaves the learner
            // to act. Rung 2 asks the reasoning question rather than answering it:
            // "do we need a larger sum or a smaller one?" is the thought that
            // turns the rule into understanding.
            guidance = listOf(
                NarrationKey(
                    NarrationId.TP_RETRY_LOOK_AGAIN,
                    listOf(sum, state.target),
                ),
                NarrationKey(
                    when (relation) {
                        Relation.GREATER -> NarrationId.TP_RETRY_ASK_SMALLER
                        Relation.LESS -> NarrationId.TP_RETRY_ASK_LARGER
                        Relation.EQUAL -> NarrationId.TP_RETRY_ASK_EQUAL
                    },
                ),
                NarrationKey(
                    when (relation) {
                        Relation.GREATER -> NarrationId.TP_RETRY_EXPLAIN_RIGHT
                        Relation.LESS -> NarrationId.TP_RETRY_EXPLAIN_LEFT
                        Relation.EQUAL -> NarrationId.TP_RETRY_EXPLAIN_FOUND
                    },
                    listOf(sum, state.target, rightValue, leftValue),
                ),
            ),
            minimalFeedback = NarrationKey(
                when (relation) {
                    Relation.GREATER -> NarrationId.TP_SUM_GREATER
                    Relation.LESS -> NarrationId.TP_SUM_LESS
                    Relation.EQUAL -> NarrationId.TP_SUM_EQUAL
                },
                listOf(sum, state.target),
            ),
            // Why *this* option cannot be right. Each one names the value that
            // would move and what it would do to the sum — the misconception is
            // always "a pointer moved", never "the wrong button was pressed".
            whyWrong = buildMap {
                if (correct != TwoPointersAction.MoveLeft) {
                    put(
                        TwoPointersAction.MoveLeft,
                        NarrationKey(
                            if (relation == Relation.EQUAL) {
                                NarrationId.TP_WHY_MOVE_PAST_PAIR
                            } else {
                                NarrationId.TP_WHY_LEFT_WRONG
                            },
                            listOf(sum, state.target),
                        ),
                    )
                }
                if (correct != TwoPointersAction.MoveRight) {
                    put(
                        TwoPointersAction.MoveRight,
                        NarrationKey(
                            if (relation == Relation.EQUAL) {
                                NarrationId.TP_WHY_MOVE_PAST_PAIR
                            } else {
                                NarrationId.TP_WHY_RIGHT_WRONG
                            },
                            listOf(sum, state.target),
                        ),
                    )
                }
                if (correct != TwoPointersAction.FoundPair) {
                    put(
                        TwoPointersAction.FoundPair,
                        NarrationKey(
                            NarrationId.TP_WHY_NOT_FOUND_YET,
                            listOf(sum, state.target),
                        ),
                    )
                }
            },
            correctFeedback = NarrationKey(
                when (relation) {
                    Relation.GREATER -> NarrationId.TP_CORRECT_RIGHT
                    Relation.LESS -> NarrationId.TP_CORRECT_LEFT
                    Relation.EQUAL -> NarrationId.TP_CORRECT_FOUND
                },
                listOf(leftValue, rightValue, sum, state.target),
            ),
            hintLadder = listOf(
                NarrationKey(
                    NarrationId.TP_HINT_COMPARE,
                    listOf(leftValue, rightValue, sum, state.target),
                ),
                NarrationKey(
                    when (relation) {
                        Relation.GREATER -> NarrationId.TP_RETRY_ASK_SMALLER
                        Relation.LESS -> NarrationId.TP_RETRY_ASK_LARGER
                        Relation.EQUAL -> NarrationId.TP_RETRY_ASK_EQUAL
                    },
                ),
                NarrationKey(
                    when (relation) {
                        Relation.GREATER -> NarrationId.TP_RETRY_EXPLAIN_RIGHT
                        Relation.LESS -> NarrationId.TP_RETRY_EXPLAIN_LEFT
                        Relation.EQUAL -> NarrationId.TP_RETRY_EXPLAIN_FOUND
                    },
                    listOf(sum, state.target, rightValue, leftValue),
                ),
            ),
            // The sum is arithmetic the app already performed and printed. The
            // *move* is the judgement, so Try must ask it.
            autoInTry = false,
        )
    }

    // -- Transitions ----------------------------------------------------------

    override fun apply(
        state: TwoPointersState,
        action: TwoPointersAction,
    ): Transition<TwoPointersState> = when (action) {
        TwoPointersAction.Compare -> compare(state)
        TwoPointersAction.MoveLeft -> move(state, movingLeft = true)
        TwoPointersAction.MoveRight -> move(state, movingLeft = false)
        TwoPointersAction.FoundPair -> found(state)
    }

    private fun compare(state: TwoPointersState): Transition<TwoPointersState> {
        // Total rather than defensive: with no pair there is nothing to read, so
        // the state is returned untouched and `probe` terminates on the next look.
        val sum = state.currentSum ?: return Transition(state, emptyList(), null, correct = false)
        val relation = requireNotNull(state.relation)

        return Transition(
            next = state.copy(sum = sum),
            events = listOf(
                VizEvent.Examine(listOf(state.left, state.right), ExamineRole.COMPARING),
                VizEvent.Compare(state.left, state.right, relation),
                VizEvent.Meter(MeterId.RUNNING_SUM, sum.toLong()),
            ),
            narration = NarrationKey(
                NarrationId.TP_SUM_IS,
                listOf(requireNotNull(state.leftValue), requireNotNull(state.rightValue), sum),
            ),
            correct = true,
        )
    }

    /**
     * Applying the *wrong* pointer is the same code path with a different
     * argument — that is what makes validation a pure comparison rather than a
     * special case. In Try nothing ever calls it that way, because a `Retry`
     * carries no action (ARCHITECTURE.md §6.1).
     */
    private fun move(
        state: TwoPointersState,
        movingLeft: Boolean,
    ): Transition<TwoPointersState> {
        val relation = state.relation
        val correct = if (movingLeft) relation == Relation.LESS else relation == Relation.GREATER

        // Clamped, so no action sequence can put a pointer outside the array or
        // drive the window inside out.
        val nextLeft = if (movingLeft) (state.left + 1).coerceAtMost(state.right) else state.left
        val nextRight =
            if (movingLeft) state.right else (state.right - 1).coerceAtLeast(state.left)
        val exhausted = nextLeft >= nextRight

        // The pair that just left, and every pair it was part of.
        val discarded = if (movingLeft) state.left..state.left else state.right..state.right

        val next = state.copy(
            left = nextLeft,
            right = nextRight,
            sum = null,
            exhausted = exhausted,
        )

        return Transition(
            next = next,
            events = buildList {
                add(
                    VizEvent.Eliminate(
                        discarded,
                        if (movingLeft) EliminateReason.TOO_SMALL else EliminateReason.TOO_LARGE,
                    ),
                )
                add(VizEvent.MovePointer(PointerId.LO, nextLeft.takeIf { !exhausted }))
                add(VizEvent.MovePointer(PointerId.HI, nextRight.takeIf { !exhausted }))
                add(
                    VizEvent.Region(
                        if (exhausted) null else nextLeft..nextRight,
                        RegionId.SEARCH_SPACE,
                    ),
                )
                add(VizEvent.Meter(MeterId.REMAINING, next.remaining.toLong()))
                if (exhausted) add(VizEvent.Terminal(Outcome.NotFound))
            },
            narration = NarrationKey(
                when {
                    exhausted -> NarrationId.TP_WINDOW_CLOSED
                    movingLeft -> NarrationId.TP_MOVED_LEFT
                    else -> NarrationId.TP_MOVED_RIGHT
                },
                listOf(state.values.getOrNull(nextLeft) ?: 0, state.values.getOrNull(nextRight) ?: 0),
            ),
            correct = correct,
        )
    }

    private fun found(state: TwoPointersState): Transition<TwoPointersState> {
        val actuallyFound = state.relation == Relation.EQUAL
        if (!actuallyFound) {
            // Claiming a pair that does not add up changes nothing. The learner is
            // returned to the same decision by the validator.
            return Transition(
                next = state,
                events = listOf(
                    VizEvent.Examine(listOf(state.left, state.right), ExamineRole.INSPECTING),
                ),
                narration = null,
                correct = false,
            )
        }

        return Transition(
            next = state.copy(foundLeft = state.left, foundRight = state.right),
            events = listOf(
                VizEvent.Finalize(state.left..state.left),
                VizEvent.Finalize(state.right..state.right),
                VizEvent.Mark(state.left, MarkId.TARGET),
                VizEvent.Mark(state.right, MarkId.TARGET),
                VizEvent.Terminal(Outcome.Found(state.left)),
            ),
            narration = NarrationKey(
                NarrationId.TP_FOUND,
                listOf(
                    requireNotNull(state.leftValue),
                    requireNotNull(state.rightValue),
                    state.target,
                ),
            ),
            correct = true,
        )
    }

    private fun key(id: NarrationId) = NarrationKey(id)
}
