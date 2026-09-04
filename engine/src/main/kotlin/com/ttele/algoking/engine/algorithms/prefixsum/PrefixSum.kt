package com.ttele.algoking.engine.algorithms.prefixsum

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
import com.ttele.algoking.engine.event.MeterId
import com.ttele.algoking.engine.event.Outcome
import com.ttele.algoking.engine.event.RegionId
import com.ttele.algoking.engine.event.VizEvent
import com.ttele.algoking.engine.narration.NarrationId
import com.ttele.algoking.engine.narration.NarrationKey

/**
 * What the learner does in Prefix Sum.
 *
 * Three judgements, in two phases: build each running total, then choose which
 * two prefix values answer a range query, then evaluate the subtraction. Nothing
 * here is mechanical — every beat is a decision, because every beat is somewhere
 * a learner gets prefix sums wrong.
 */
sealed interface PrefixSumAction : Action {

    /** Commit [value] as the next prefix entry. */
    data class Fill(val value: Int) : PrefixSumAction

    /**
     * Answer a range query with `prefix[hi] - prefix[lo]`.
     *
     * Choosing the indices is a separate beat from evaluating them because the
     * off-by-one — reaching for `prefix[right]` instead of `prefix[right + 1]` —
     * is the mistake this technique is famous for, and it has to be askable on
     * its own.
     */
    data class UseIndices(val hi: Int, val lo: Int) : PrefixSumAction

    /** Evaluate the subtraction the learner just chose. */
    data class Answer(val value: Int) : PrefixSumAction
}

/**
 * Immutable state.
 *
 * ### The representation, fixed once
 *
 * The prefix array uses the **standard leading-zero form**: it is `values.size + 1`
 * long and `prefix[0] = 0`.
 *
 * ```
 * values  =    [2, 4, 3,  7,  1]
 * prefix  = [0, 2, 6, 9, 16, 17]
 *
 * prefix[i + 1]        = prefix[i] + values[i]
 * rangeSum(left, right) = prefix[right + 1] - prefix[left]
 * ```
 *
 * The leading zero is not decoration: without it the range query needs a branch
 * for `left == 0`, and a formula with an exception in it is a formula the learner
 * has to memorise rather than understand. **Nothing in the engine, the
 * walkthrough, the UI or the tests uses the other representation.**
 *
 * [prefix] holds only the entries computed so far, always starting `[0]`, so its
 * size *is* the build cursor and the two can never disagree.
 */
data class PrefixSumState(
    val values: List<Int>,
    /** Known prefix entries, starting `[0]`. Grows to `values.size + 1`. */
    val prefix: List<Int>,
    /** The range the learner is asked about, as authored. Clamped by [queryRange]. */
    val askedLeft: Int,
    val askedRight: Int,
    /** The prefix indices the learner chose, once they have. */
    val chosenHi: Int?,
    val chosenLo: Int?,
    val answer: Int?,
) {
    val size: Int get() = values.size

    /** The prefix index currently being computed. Past the end once built. */
    val nextIndex: Int get() = prefix.size

    val buildComplete: Boolean get() = prefix.size == size + 1

    /** The value [nextIndex] should take: `prefix[i] + values[i - 1]`. */
    val expectedNext: Int?
        get() = if (buildComplete || values.isEmpty()) {
            null
        } else {
            prefix.last() + values[prefix.size - 1]
        }

    /**
     * The query, clamped into the array — **the only range the lesson ever uses**.
     *
     * An authored `left > right`, a negative bound or a bound past the end is a
     * bad dataset, not a crash: it is normalised here once, so no caller has to
     * remember to check and no state downstream can hold an impossible span.
     * Null only when there is nothing to query at all.
     */
    val queryRange: IntRange?
        get() {
            if (values.isEmpty()) return null
            val lo = askedLeft.coerceIn(0, values.lastIndex)
            val hi = askedRight.coerceIn(0, values.lastIndex)
            return if (lo <= hi) lo..hi else hi..lo
        }

    /** The complete prefix array, whether or not the learner has built it yet. */
    val fullPrefix: List<Int>
        get() = buildList {
            add(0)
            var running = 0
            for (v in values) {
                running += v
                add(running)
            }
        }

    /**
     * **The single source of truth for a range sum.** The UI never adds cells up
     * itself; it reads this.
     */
    fun rangeSum(left: Int, right: Int): Int {
        val p = fullPrefix
        val lo = left.coerceIn(0, size)
        val hi = (right + 1).coerceIn(0, size)
        return p[hi] - p[lo]
    }

    /** The answer to the question actually being asked. */
    val queryAnswer: Int?
        get() = queryRange?.let { rangeSum(it.first, it.last) }

    val finished: Boolean
        get() = when {
            values.isEmpty() -> true
            !buildComplete -> false
            queryRange == null -> true
            else -> answer != null
        }
}

/**
 * Prefix Sum — an Advanced lesson about precomputation.
 *
 * ### What it teaches, and why the subtraction is the point
 *
 * Building the array is O(n) and easy. The idea worth having is the query:
 *
 * ```
 * prefix[right + 1]  holds everything up to and including `right`
 * prefix[left]       holds everything strictly before `left`
 * the difference is exactly values[left .. right]
 * ```
 *
 * So a range sum costs one subtraction — O(1) — however long the range is. A
 * learner who can build the array but reaches for `prefix[right]` has not learned
 * the technique, which is why choosing the indices is its own decision with its
 * own guidance ladder.
 */
class PrefixSumAlgorithm : Algorithm<PrefixSumState, PrefixSumAction> {

    override val id = AlgorithmId.PREFIX_SUM

    override fun initial(dataset: Dataset) = PrefixSumState(
        values = dataset.values,
        // The leading zero is given, not asked for: it is the definition of the
        // representation rather than a step the learner could get wrong.
        prefix = listOf(0),
        askedLeft = dataset.queryLeft ?: 0,
        askedRight = dataset.queryRight ?: dataset.values.lastIndex,
        chosenHi = null,
        chosenLo = null,
        answer = null,
    )

    override fun probe(state: PrefixSumState): Probe<PrefixSumAction> {
        // An empty array has no prefix to build and no range to query. That is a
        // finished lesson, not an error.
        if (state.values.isEmpty()) return Probe.Terminal(Outcome.Completed(true))

        if (!state.buildComplete) return Probe.Decide(fillDecision(state))

        val range = state.queryRange ?: return Probe.Terminal(Outcome.Completed(true))

        if (state.chosenHi == null || state.chosenLo == null) {
            return Probe.Decide(indicesDecision(state, range))
        }
        if (state.answer == null) return Probe.Decide(answerDecision(state, range))

        return Probe.Terminal(Outcome.Completed(true))
    }

    // -- Phase 1: build -------------------------------------------------------

    private fun fillDecision(state: PrefixSumState): Decision<PrefixSumAction> {
        val i = state.nextIndex
        val previous = state.prefix.last()
        val value = state.values[i - 1]
        val correct = previous + value

        val choices = fillChoices(state, i, previous, value, correct)

        return Decision(
            kind = DecisionKind.OPTIONS,
            prompt = NarrationKey(NarrationId.PS_ASK_NEXT_PREFIX, listOf(i)),
            options = choices.map { candidate ->
                ActionOption<PrefixSumAction>(
                    action = PrefixSumAction.Fill(candidate),
                    label = NarrationKey(NarrationId.PS_OPTION_VALUE, listOf(candidate)),
                )
            },
            correct = PrefixSumAction.Fill(correct),
            focus = listOf(i - 1),
            hint = NarrationKey(NarrationId.PS_HINT_BUILD, listOf(i, i - 1, i - 1)),
            guidance = listOf(
                NarrationKey(NarrationId.PS_RETRY_BUILD_LOOK, listOf(i)),
                NarrationKey(NarrationId.PS_RETRY_BUILD_ASK, listOf(previous, value)),
                NarrationKey(
                    NarrationId.PS_RETRY_BUILD_EXPLAIN,
                    listOf(i, previous, value, correct),
                ),
            ),
            minimalFeedback = NarrationKey(NarrationId.PS_RETRY_BUILD_LOOK, listOf(i)),
            whyWrong = buildMap {
                // Each wrong option is named for the misconception that produces
                // it, so the feedback describes the learner's actual error rather
                // than restating the rule at them.
                for (candidate in choices) {
                    if (candidate == correct) continue
                    val id = when (candidate) {
                        value -> NarrationId.PS_WHY_FORGOT_RUNNING_TOTAL
                        previous -> NarrationId.PS_WHY_FORGOT_TO_ADD
                        else -> NarrationId.PS_WHY_WRONG_OPERANDS
                    }
                    put(
                        PrefixSumAction.Fill(candidate),
                        NarrationKey(id, listOf(previous, value, correct, i)),
                    )
                }
            },
            correctFeedback = NarrationKey(
                NarrationId.PS_CORRECT_BUILD,
                listOf(previous, value, correct, i),
            ),
            hintLadder = listOf(
                NarrationKey(NarrationId.PS_HINT_BUILD, listOf(i, i - 1, i - 1)),
                NarrationKey(NarrationId.PS_RETRY_BUILD_ASK, listOf(previous, value)),
                NarrationKey(
                    NarrationId.PS_RETRY_BUILD_EXPLAIN,
                    listOf(i, previous, value, correct),
                ),
            ),
        )
    }

    /**
     * Three values, one of them right, and the wrong two are the two mistakes
     * learners actually make: adding only the current element (forgetting the
     * running total), and adding two array values to each other.
     *
     * Deterministic — the position of the correct answer rotates with the index
     * rather than being drawn at random, so a run is reproducible and a test can
     * pin it.
     */
    private fun fillChoices(
        state: PrefixSumState,
        index: Int,
        previous: Int,
        value: Int,
        correct: Int,
    ): List<Int> {
        val pool = LinkedHashSet<Int>()
        pool += correct
        // "prefix[i] = values[i - 1]" — the running total dropped.
        pool += value
        // Two array values added to each other instead of prefix + array.
        state.values.getOrNull(index)?.let { pool += value + it }
        // Nothing added at all.
        pool += previous
        // Last resort, so there are always three distinct options.
        pool += correct + value.coerceAtLeast(1)
        pool += correct + 1

        val three = pool.take(3)
        // Rotate so the answer is not always in the same seat.
        val shift = index % three.size
        return three.drop(shift) + three.take(shift)
    }

    // -- Phase 2: the query ---------------------------------------------------

    private fun indicesDecision(
        state: PrefixSumState,
        range: IntRange,
    ): Decision<PrefixSumAction> {
        val left = range.first
        val right = range.last
        val correct = PrefixSumAction.UseIndices(hi = right + 1, lo = left)

        // The off-by-one is the whole point of this beat, so it is always on the
        // table: `prefix[right]` stops one short and loses values[right].
        val alternatives = LinkedHashSet<Pair<Int, Int>>()
        alternatives += (right + 1) to left
        alternatives += right to left
        alternatives += (right + 1) to (if (left > 0) left - 1 else left + 1)
            .coerceIn(0, state.size)

        val options = alternatives.take(3).map { (hi, lo) ->
            ActionOption<PrefixSumAction>(
                action = PrefixSumAction.UseIndices(hi, lo),
                label = NarrationKey(NarrationId.PS_OPTION_INDICES, listOf(hi, lo)),
            )
        }

        return Decision(
            kind = DecisionKind.OPTIONS,
            prompt = NarrationKey(NarrationId.PS_ASK_WHICH_INDICES, listOf(left, right)),
            options = options,
            correct = correct,
            focus = (left..right).toList(),
            hint = NarrationKey(NarrationId.PS_HINT_FORMULA),
            guidance = listOf(
                NarrationKey(NarrationId.PS_RETRY_INDICES_LOOK),
                NarrationKey(NarrationId.PS_RETRY_INDICES_ASK, listOf(right)),
                NarrationKey(
                    NarrationId.PS_RETRY_INDICES_EXPLAIN,
                    listOf(left, right, right + 1, left),
                ),
            ),
            minimalFeedback = NarrationKey(NarrationId.PS_RETRY_INDICES_LOOK),
            whyWrong = buildMap {
                for ((hi, lo) in alternatives.take(3)) {
                    if (hi == right + 1 && lo == left) continue
                    val id = when {
                        hi == right -> NarrationId.PS_WHY_STOPS_SHORT
                        lo < left -> NarrationId.PS_WHY_KEEPS_TOO_MUCH
                        else -> NarrationId.PS_WHY_DROPS_TOO_MUCH
                    }
                    put(
                        PrefixSumAction.UseIndices(hi, lo),
                        NarrationKey(id, listOf(hi, lo, left, right)),
                    )
                }
            },
            correctFeedback = NarrationKey(
                NarrationId.PS_CORRECT_INDICES,
                listOf(right + 1, left),
            ),
            hintLadder = listOf(
                NarrationKey(NarrationId.PS_HINT_FORMULA),
                NarrationKey(NarrationId.PS_RETRY_INDICES_ASK, listOf(right)),
                NarrationKey(
                    NarrationId.PS_RETRY_INDICES_EXPLAIN,
                    listOf(left, right, right + 1, left),
                ),
            ),
        )
    }

    private fun answerDecision(
        state: PrefixSumState,
        range: IntRange,
    ): Decision<PrefixSumAction> {
        val full = state.fullPrefix
        val hi = requireNotNull(state.chosenHi)
        val lo = requireNotNull(state.chosenLo)
        val hiValue = full[hi.coerceIn(0, state.size)]
        val loValue = full[lo.coerceIn(0, state.size)]
        val correct = hiValue - loValue

        val pool = LinkedHashSet<Int>()
        pool += correct
        pool += hiValue + loValue
        pool += loValue - hiValue
        pool += correct + 1
        val choices = pool.take(3)
        val shift = range.first % choices.size
        val ordered = choices.drop(shift) + choices.take(shift)

        return Decision(
            kind = DecisionKind.OPTIONS,
            prompt = NarrationKey(
                NarrationId.PS_ASK_EVALUATE,
                listOf(hi, lo, hiValue, loValue),
            ),
            options = ordered.map { candidate ->
                ActionOption<PrefixSumAction>(
                    action = PrefixSumAction.Answer(candidate),
                    label = NarrationKey(NarrationId.PS_OPTION_VALUE, listOf(candidate)),
                )
            },
            correct = PrefixSumAction.Answer(correct),
            focus = (range).toList(),
            hint = NarrationKey(NarrationId.PS_HINT_SUBTRACT, listOf(hiValue, loValue)),
            guidance = listOf(
                NarrationKey(NarrationId.PS_RETRY_EVAL_LOOK, listOf(hiValue, loValue)),
                NarrationKey(NarrationId.PS_RETRY_EVAL_ASK),
                NarrationKey(
                    NarrationId.PS_RETRY_EVAL_EXPLAIN,
                    listOf(hiValue, loValue, correct),
                ),
            ),
            minimalFeedback = NarrationKey(
                NarrationId.PS_RETRY_EVAL_LOOK,
                listOf(hiValue, loValue),
            ),
            correctFeedback = NarrationKey(
                NarrationId.PS_CORRECT_ANSWER,
                listOf(hiValue, loValue, correct, range.first, range.last),
            ),
            hintLadder = listOf(
                NarrationKey(NarrationId.PS_HINT_SUBTRACT, listOf(hiValue, loValue)),
                NarrationKey(NarrationId.PS_RETRY_EVAL_ASK),
                NarrationKey(
                    NarrationId.PS_RETRY_EVAL_EXPLAIN,
                    listOf(hiValue, loValue, correct),
                ),
            ),
        )
    }

    // -- Transitions ----------------------------------------------------------

    override fun apply(
        state: PrefixSumState,
        action: PrefixSumAction,
    ): Transition<PrefixSumState> = when (action) {
        is PrefixSumAction.Fill -> fill(state, action.value)
        is PrefixSumAction.UseIndices -> useIndices(state, action.hi, action.lo)
        is PrefixSumAction.Answer -> answer(state, action.value)
    }

    /**
     * Appends whatever it was given, correct or not — `apply` takes any legal
     * action and is not the thing that decides which are legal (ARCHITECTURE.md
     * §4.1). In Try nothing ever calls it with a wrong value, because a `Retry`
     * carries no action.
     */
    private fun fill(state: PrefixSumState, value: Int): Transition<PrefixSumState> {
        if (state.buildComplete || state.values.isEmpty()) {
            return Transition(state, emptyList(), null, correct = false)
        }
        val i = state.nextIndex
        val correct = value == state.expectedNext
        return Transition(
            next = state.copy(prefix = state.prefix + value),
            events = listOf(
                VizEvent.Examine(listOf(i - 1), ExamineRole.COMPARING),
                VizEvent.Insert(value, i),
                VizEvent.Meter(MeterId.RUNNING_SUM, value.toLong()),
            ),
            narration = NarrationKey(
                NarrationId.PS_BUILT,
                listOf(i, state.prefix.last(), state.values[i - 1], value),
            ),
            correct = correct,
        )
    }

    private fun useIndices(
        state: PrefixSumState,
        hi: Int,
        lo: Int,
    ): Transition<PrefixSumState> {
        val range = state.queryRange
        val correct = range != null && hi == range.last + 1 && lo == range.first
        return Transition(
            next = state.copy(chosenHi = hi, chosenLo = lo),
            events = buildList {
                range?.let { add(VizEvent.Region(it, RegionId.WINDOW)) }
                add(VizEvent.Examine(listOf(lo, hi), ExamineRole.INSPECTING))
            },
            narration = NarrationKey(NarrationId.PS_CHOSE_INDICES, listOf(hi, lo)),
            correct = correct,
        )
    }

    private fun answer(state: PrefixSumState, value: Int): Transition<PrefixSumState> {
        val expected = state.queryAnswer
        val correct = value == expected
        val range = state.queryRange
        return Transition(
            next = state.copy(answer = value),
            events = buildList {
                range?.let { add(VizEvent.Finalize(it)) }
                add(VizEvent.Meter(MeterId.BEST, value.toLong()))
                add(VizEvent.Terminal(Outcome.Completed(correct)))
            },
            narration = NarrationKey(
                NarrationId.PS_ANSWERED,
                listOf(range?.first ?: 0, range?.last ?: 0, value),
            ),
            correct = correct,
        )
    }
}
