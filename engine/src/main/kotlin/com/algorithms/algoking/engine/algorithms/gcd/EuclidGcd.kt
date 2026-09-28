package com.algorithms.algoking.engine.algorithms.gcd

import com.algorithms.algoking.engine.core.Algorithm
import com.algorithms.algoking.engine.core.AlgorithmId
import com.algorithms.algoking.engine.core.Dataset
import com.algorithms.algoking.engine.core.Probe
import com.algorithms.algoking.engine.core.Transition
import com.algorithms.algoking.engine.decision.Action
import com.algorithms.algoking.engine.decision.ActionOption
import com.algorithms.algoking.engine.decision.Decision
import com.algorithms.algoking.engine.decision.DecisionKind
import com.algorithms.algoking.engine.event.ExamineRole
import com.algorithms.algoking.engine.event.Outcome
import com.algorithms.algoking.engine.event.VizEvent
import com.algorithms.algoking.engine.narration.NarrationId
import com.algorithms.algoking.engine.narration.NarrationKey

/**
 * What the learner does in Euclid's algorithm. Two questions:
 *
 * 1. **What is a mod b?** — the remainder. The wrong numbers are the quotient
 *    (how many times b fits, not what is left) and a − b (taking b away once).
 * 2. **What is the GCD?** — once the remainder is 0: the last number that was
 *    not 0, not the 0 and not where the run started.
 */
sealed interface GcdAction : Action {
    data class Remainder(val value: Int) : GcdAction
    data class Answer(val value: Int) : GcdAction
}

/** One row of the table: the pair, and its remainder once worked out. */
data class GcdStep(val a: Int, val b: Int, val r: Int) {
    val quotient: Int get() = a / b
}

/** Immutable state: the pair being worked on, and the rows already done. */
data class GcdState(
    val start: Pair<Int, Int>,
    val a: Int,
    val b: Int,
    val steps: List<GcdStep>,
    val answered: Boolean,
) {
    /** The remainder reached 0: time to name the answer. */
    val stopped: Boolean get() = b == 0

    val done: Boolean get() = stopped && answered

    val quotient: Int get() = a / b
    val remainder: Int get() = a % b

    /** The three numbers a mod b question offers: the remainder, the quotient, a − b. Distinct. */
    val remainderOptions: List<Int> get() = listOf(remainder, quotient, a - b).distinct().sorted()

    /** The GCD question's three numbers: the answer, the 0, and the starting number. */
    val answerOptions: List<Int> get() = listOf(a, 0, start.first).distinct().sorted()
}

/**
 * Euclid's algorithm — a free Math lesson, and about the oldest algorithm there is.
 *
 * ```
 * while b is not 0:
 *     (a, b) = (b, a mod b)
 * the GCD is a
 * ```
 *
 * ### What the learner has to understand
 *
 * 1. **Swapping for the remainder keeps the answer.** Anything that divides a
 *    and b also divides a − b, a − 2b, … and so the remainder. The pair shrinks;
 *    the GCD stays put.
 * 2. **The remainder is what is left over**, not how many times b fits.
 * 3. **Stop at 0 — the answer is the number before it.**
 *
 * **Time O(log min(a, b))**; **space O(1)**.
 */
class EuclidGcdAlgorithm : Algorithm<GcdState, GcdAction> {

    override val id = AlgorithmId.EUCLID_GCD

    override fun initial(dataset: Dataset): GcdState {
        val (a, b) = dataset.values.let { requireNotNull(it.getOrNull(0)) to requireNotNull(it.getOrNull(1)) }
        return GcdState(start = a to b, a = a, b = b, steps = emptyList(), answered = false)
    }

    override fun probe(state: GcdState): Probe<GcdAction> = when {
        state.done -> Probe.Terminal(Outcome.Completed(true))
        state.stopped -> Probe.Decide(answerDecision(state))
        else -> Probe.Decide(remainderDecision(state))
    }

    private fun remainderDecision(state: GcdState): Decision<GcdAction> {
        val a = state.a
        val b = state.b
        val q = state.quotient
        val r = state.remainder
        // Every line about this step: a, b, quotient, b × quotient, remainder, a − b.
        val args = listOf(a, b, q, b * q, r, a - b)
        return Decision(
            kind = DecisionKind.OPTIONS,
            prompt = NarrationKey(NarrationId.GCD_ASK_MOD, args),
            options = state.remainderOptions.map {
                ActionOption<GcdAction>(GcdAction.Remainder(it), NarrationKey(NarrationId.GCD_OPTION_VALUE, listOf(it)))
            },
            correct = GcdAction.Remainder(r),
            focus = listOf(state.steps.size),
            hint = NarrationKey(NarrationId.GCD_HINT_MOD, args),
            guidance = listOf(
                NarrationKey(NarrationId.GCD_RETRY_MOD_LOOK, args),
                NarrationKey(NarrationId.GCD_RETRY_MOD_ASK, args),
                NarrationKey(NarrationId.GCD_RETRY_MOD_EXPLAIN, args),
            ),
            minimalFeedback = NarrationKey(NarrationId.GCD_RETRY_MOD_LOOK, args),
            whyWrong = buildMap {
                for (value in state.remainderOptions) {
                    if (value == r) continue
                    val id = if (value == q) NarrationId.GCD_WHY_QUOTIENT else NarrationId.GCD_WHY_SUBTRACT_ONCE
                    put(GcdAction.Remainder(value), NarrationKey(id, args))
                }
            },
            correctFeedback = NarrationKey(
                if (r == 0) NarrationId.GCD_CORRECT_MOD_ZERO else NarrationId.GCD_CORRECT_MOD,
                args,
            ),
            hintLadder = listOf(NarrationKey(NarrationId.GCD_HINT_MOD, args)),
            autoInTry = false,
        )
    }

    private fun answerDecision(state: GcdState): Decision<GcdAction> {
        val args = listOf(state.a, state.start.first, state.start.second)
        return Decision(
            kind = DecisionKind.OPTIONS,
            prompt = NarrationKey(NarrationId.GCD_ASK_ANSWER, args),
            options = state.answerOptions.map {
                ActionOption<GcdAction>(GcdAction.Answer(it), NarrationKey(NarrationId.GCD_OPTION_VALUE, listOf(it)))
            },
            correct = GcdAction.Answer(state.a),
            focus = listOf(state.steps.size - 1),
            hint = NarrationKey(NarrationId.GCD_HINT_ANSWER, args),
            guidance = listOf(
                NarrationKey(NarrationId.GCD_RETRY_ANSWER_LOOK, args),
                NarrationKey(NarrationId.GCD_RETRY_ANSWER_ASK, args),
                NarrationKey(NarrationId.GCD_RETRY_ANSWER_EXPLAIN, args),
            ),
            minimalFeedback = NarrationKey(NarrationId.GCD_RETRY_ANSWER_LOOK, args),
            whyWrong = buildMap {
                for (value in state.answerOptions) {
                    if (value == state.a) continue
                    val id = if (value == 0) NarrationId.GCD_WHY_ZERO else NarrationId.GCD_WHY_START
                    put(GcdAction.Answer(value), NarrationKey(id, args))
                }
            },
            correctFeedback = NarrationKey(
                NarrationId.GCD_CORRECT_ANSWER,
                args + listOf(state.start.first / state.a, state.start.second / state.a),
            ),
            hintLadder = listOf(NarrationKey(NarrationId.GCD_HINT_ANSWER, args)),
            autoInTry = false,
        )
    }

    override fun apply(state: GcdState, action: GcdAction): Transition<GcdState> {
        val refuse = Transition(
            next = state,
            events = listOf(VizEvent.Examine(listOf(state.steps.size), ExamineRole.INSPECTING)),
            narration = null,
            correct = false,
        )
        return when (action) {
            is GcdAction.Remainder -> {
                if (state.stopped || action.value != state.remainder) return refuse
                val step = GcdStep(state.a, state.b, state.remainder)
                Transition(
                    // The pair moves along: (a, b) becomes (b, a mod b).
                    next = state.copy(a = state.b, b = step.r, steps = state.steps + step),
                    events = listOf(VizEvent.Examine(listOf(state.steps.size), ExamineRole.COMPARING)),
                    narration = NarrationKey(NarrationId.GCD_STEPPED, listOf(step.a, step.b, step.r)),
                    correct = true,
                )
            }

            is GcdAction.Answer -> {
                if (!state.stopped || action.value != state.a) return refuse
                Transition(
                    next = state.copy(answered = true),
                    events = listOf(
                        VizEvent.Finalize(0..0),
                        VizEvent.Terminal(Outcome.Completed(true)),
                    ),
                    narration = NarrationKey(NarrationId.GCD_ANSWERED, listOf(state.a)),
                    correct = true,
                )
            }
        }
    }
}
