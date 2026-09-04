package com.ttele.algoking.engine.decision

import com.ttele.algoking.engine.narration.NarrationKey

/**
 * The generic decision-validation model.
 *
 * ```
 * DecisionPoint → UserAction → validate → Accept  → engine advances
 *                                       ↘ Retry   → feedback, SAME state
 * ```
 *
 * **A wrong answer is a learning event, not a state transition.** Nothing in here
 * can mutate an algorithm state — [validate] is pure and returns a verdict, and
 * only [Validation.Accept] gives the caller an action to apply. That is what makes
 * it impossible for a learner's mistake to corrupt the algorithm, in *any*
 * algorithm: the rule lives here once, not in eight `if (algorithm == ...)`.
 */
sealed interface Validation<out A : Action> {

    /** The choice was right. Apply [action] and move on. */
    data class Accept<A : Action>(
        val action: A,
        val feedback: NarrationKey,
    ) : Validation<A>

    /**
     * The choice was wrong. The state does **not** change; teach and re-ask.
     *
     * [level] is 1-based and escalates with each repeat on the same decision:
     * point at the evidence, then ask the reasoning question, then say it plainly.
     */
    data class Retry(
        val level: Int,
        val guidance: NarrationKey,
        val whyWrong: NarrationKey?,
        val focus: List<Int>,
    ) : Validation<Nothing>
}

object DecisionValidation {

    /**
     * @param priorAttempts how many times this learner has already got *this*
     *        decision wrong. 0 on the first try.
     */
    fun <A : Action> validate(
        decision: Decision<A>,
        chosen: A,
        priorAttempts: Int,
    ): Validation<A> {
        if (chosen == decision.correct) {
            return Validation.Accept(chosen, decision.correctFeedback)
        }

        val level = priorAttempts + 1
        val guidance = decision.guidance
            .getOrElse(level - 1) { decision.guidance.lastOrNull() ?: decision.hint }

        return Validation.Retry(
            level = level,
            guidance = guidance,
            whyWrong = decision.whyWrong[chosen],
            focus = decision.focus,
        )
    }
}
