package com.algorithms.algoking.engine.algorithms.linearsearch

import com.algorithms.algoking.engine.core.Algorithm
import com.algorithms.algoking.engine.core.AlgorithmId
import com.algorithms.algoking.engine.core.Dataset
import com.algorithms.algoking.engine.core.Probe
import com.algorithms.algoking.engine.core.Transition
import com.algorithms.algoking.engine.decision.Action
import com.algorithms.algoking.engine.decision.ActionOption
import com.algorithms.algoking.engine.decision.Decision
import com.algorithms.algoking.engine.decision.DecisionKind
import com.algorithms.algoking.engine.event.EliminateReason
import com.algorithms.algoking.engine.event.ExamineRole
import com.algorithms.algoking.engine.event.MeterId
import com.algorithms.algoking.engine.event.Outcome
import com.algorithms.algoking.engine.event.PointerId
import com.algorithms.algoking.engine.event.Relation
import com.algorithms.algoking.engine.event.VizEvent
import com.algorithms.algoking.engine.narration.NarrationId
import com.algorithms.algoking.engine.narration.NarrationKey

/**
 * What the learner does in Linear Search: **one question per element — is this
 * the target?** Two buttons. NEXT moves on; FOUND stops. That comparison is the
 * whole algorithm, so it is never the app's.
 */
sealed interface LinearSearchAction : Action {
    data object Next : LinearSearchAction
    data object Found : LinearSearchAction
}

/**
 * Immutable state. [index] is the element being looked at; everything before it
 * has already been checked and was not the target.
 */
data class LinearSearchState(
    val values: List<Int>,
    val target: Int,
    val index: Int,
    val foundAt: Int?,
) {
    val exhausted: Boolean get() = foundAt == null && index >= values.size
    val finished: Boolean get() = foundAt != null || exhausted

    /** How many elements have been compared with the target so far. */
    val checked: Int get() = if (foundAt != null) foundAt + 1 else index.coerceAtMost(values.size)

    val current: Int? get() = values.getOrNull(index)?.takeIf { !finished }
}

/**
 * Linear Search — the first search, and the free one Binary Search is measured
 * against.
 *
 * ```
 * for i from 0 to the end:
 *     if values[i] == target: found, at i
 * not found — every element was checked
 * ```
 *
 * ### What the learner has to understand
 *
 * 1. **It needs nothing from the array.** The WATCH array is deliberately not
 *    sorted, and it does not matter.
 * 2. **The cost is the position.** A target at index 5 takes 6 checks; a missing
 *    one takes every element. That is O(n), and it is exactly what Binary Search
 *    buys back — for the price of a sorted array.
 *
 * **Time O(n)**; **space O(1)**.
 */
class LinearSearchAlgorithm : Algorithm<LinearSearchState, LinearSearchAction> {

    override val id = AlgorithmId.LINEAR_SEARCH

    override fun initial(dataset: Dataset) = LinearSearchState(
        values = dataset.values,
        target = requireNotNull(dataset.target) { "Linear Search needs a target." },
        index = 0,
        foundAt = null,
    )

    override fun probe(state: LinearSearchState): Probe<LinearSearchAction> {
        state.foundAt?.let { return Probe.Terminal(Outcome.Found(it)) }
        if (state.exhausted) return Probe.Terminal(Outcome.NotFound)
        return Probe.Decide(decision(state))
    }

    private fun decision(state: LinearSearchState): Decision<LinearSearchAction> {
        val i = state.index
        val value = state.values[i]
        val match = value == state.target
        val args = listOf(value, state.target, i)

        return Decision(
            kind = DecisionKind.OPTIONS,
            prompt = NarrationKey(NarrationId.LS_ASK, args),
            options = listOf(
                ActionOption(LinearSearchAction.Next, NarrationKey(NarrationId.LS_OPTION_NEXT)),
                ActionOption(LinearSearchAction.Found, NarrationKey(NarrationId.LS_OPTION_FOUND)),
            ),
            correct = if (match) LinearSearchAction.Found else LinearSearchAction.Next,
            focus = listOf(i),
            hint = NarrationKey(NarrationId.LS_HINT, args),
            guidance = listOf(
                NarrationKey(NarrationId.LS_RETRY_LOOK, args),
                NarrationKey(NarrationId.LS_RETRY_ASK, args),
                NarrationKey(if (match) NarrationId.LS_RETRY_EXPLAIN_FOUND else NarrationId.LS_RETRY_EXPLAIN_NEXT, args),
            ),
            minimalFeedback = NarrationKey(NarrationId.LS_RETRY_LOOK, args),
            whyWrong = if (match) {
                mapOf(LinearSearchAction.Next to NarrationKey(NarrationId.LS_WHY_PASSED_IT, args))
            } else {
                mapOf(LinearSearchAction.Found to NarrationKey(NarrationId.LS_WHY_NOT_IT, args))
            },
            correctFeedback = NarrationKey(
                if (match) NarrationId.LS_CORRECT_FOUND else NarrationId.LS_CORRECT_NEXT,
                args,
            ),
            hintLadder = listOf(NarrationKey(NarrationId.LS_HINT, args)),
            autoInTry = false,
        )
    }

    override fun apply(
        state: LinearSearchState,
        action: LinearSearchAction,
    ): Transition<LinearSearchState> {
        val i = state.index
        val value = state.values.getOrNull(i)
            ?: return Transition(state, emptyList(), null, correct = false)
        val match = value == state.target
        val right = if (match) LinearSearchAction.Found else LinearSearchAction.Next
        // A wrong answer is refused, not applied.
        if (action != right) {
            return Transition(
                next = state,
                events = listOf(VizEvent.Examine(listOf(i), ExamineRole.INSPECTING)),
                narration = null,
                correct = false,
            )
        }
        val next = if (match) state.copy(foundAt = i) else state.copy(index = i + 1)
        return Transition(
            next = next,
            events = buildList {
                add(VizEvent.MovePointer(PointerId.I, i))
                add(VizEvent.Examine(listOf(i), ExamineRole.COMPARING))
                add(
                    VizEvent.Compare(
                        i,
                        TARGET_SENTINEL,
                        when {
                            value < state.target -> Relation.LESS
                            value > state.target -> Relation.GREATER
                            else -> Relation.EQUAL
                        },
                    ),
                )
                add(VizEvent.Meter(MeterId.REMAINING, next.checked.toLong()))
                if (match) {
                    add(VizEvent.Finalize(i..i))
                    add(VizEvent.Terminal(Outcome.Found(i)))
                } else {
                    add(VizEvent.Eliminate(i..i, EliminateReason.OUT_OF_WINDOW))
                    if (next.exhausted) add(VizEvent.Terminal(Outcome.NotFound))
                }
            },
            narration = NarrationKey(
                if (match) NarrationId.LS_FOUND else NarrationId.LS_NOT_IT,
                listOf(value, state.target, i),
            ),
            correct = true,
        )
    }

    private companion object {
        /** The target is not a slot; comparisons against it use this. */
        const val TARGET_SENTINEL = -1
    }
}
