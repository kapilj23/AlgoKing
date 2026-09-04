package com.ttele.algoking.engine.algorithms.binarysearch

import com.ttele.algoking.engine.core.Algorithm
import com.ttele.algoking.engine.core.AlgorithmId
import com.ttele.algoking.engine.core.Dataset
import com.ttele.algoking.engine.core.Probe
import com.ttele.algoking.engine.core.Transition
import com.ttele.algoking.engine.decision.Action
import com.ttele.algoking.engine.decision.ActionOption
import com.ttele.algoking.engine.decision.Decision
import com.ttele.algoking.engine.decision.DecisionKind
import com.ttele.algoking.engine.decision.MidpointReadout
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
 * What the learner can do in Binary Search — PRODUCT_SPEC.md §3.
 *
 * Two beats per round: **look at a cell**, then **keep a half**. Both are exposed
 * as decisions; whether a phase asks the learner or answers for them is a host
 * policy, never a flag inside the algorithm (ARCHITECTURE.md §4.1).
 */
sealed interface BinarySearchAction : Action {

    /** Examine the cell at [index]. In a correct run that is always the middle. */
    data class Inspect(val index: Int) : BinarySearchAction

    /** Keep the left half: everything from `mid` rightwards is impossible. */
    data object KeepLeft : BinarySearchAction

    /** Keep the right half: everything from `mid` leftwards is impossible. */
    data object KeepRight : BinarySearchAction

    /** `values[mid] == target`. */
    data object Found : BinarySearchAction
}

/**
 * Immutable state. [lo]..[hi] is the live search range; when it inverts the target
 * is absent and the run terminates with [Outcome.NotFound] — which is why the
 * not-found case needs no special code path.
 */
data class BinarySearchState(
    val values: List<Int>,
    val target: Int,
    val lo: Int,
    val hi: Int,
    /** Set once the middle of the current range has been examined. */
    val mid: Int?,
    val foundAt: Int?,
    val exhausted: Boolean,
) {
    val range: IntRange get() = lo..hi
    val remaining: Int get() = if (hi < lo) 0 else hi - lo + 1
    val finished: Boolean get() = foundAt != null || exhausted

    /**
     * **The canonical AlgoKing midpoint.** Lower middle, overflow-safe:
     *
     * ```
     * mid = left + (right - left) / 2
     * ```
     *
     * With an even-sized range this takes the *left* of the two centre cells.
     * That is the textbook convention, and it is the one every interview, every
     * reference implementation and — critically — AlgoKing's own Code Reveal
     * will show. Teaching one formula in the animation and printing another in
     * the code would break the single promise the product is built on.
     *
     * Written as `lo + (hi - lo) / 2` rather than `(lo + hi) / 2` because the
     * latter overflows on large ranges, and the version the learner is taught
     * should be the version they can safely reuse.
     *
     * **This is the only place a midpoint is computed.** Watch, Try, Challenge
     * and every hint read it from here; none of them may derive their own.
     */
    val middleOfRange: Int get() = lo + (hi - lo) / 2
}

class BinarySearchAlgorithm : Algorithm<BinarySearchState, BinarySearchAction> {

    override val id = AlgorithmId.BINARY_SEARCH

    override fun initial(dataset: Dataset) = BinarySearchState(
        values = dataset.values,
        target = requireNotNull(dataset.target) { "Binary Search needs a target." },
        lo = 0,
        hi = dataset.values.lastIndex,
        mid = null,
        foundAt = null,
        exhausted = false,
    )

    override fun probe(state: BinarySearchState): Probe<BinarySearchAction> {
        state.foundAt?.let { return Probe.Terminal(Outcome.Found(it)) }
        if (state.exhausted || state.hi < state.lo) return Probe.Terminal(Outcome.NotFound)

        // Beat 1 — where do we look? Watch and Try answer this for the learner;
        // Challenge makes them find the middle themselves.
        if (state.mid == null) return Probe.Decide(inspectDecision(state))

        // Beat 2 — the decision that carries the algorithm.
        return Probe.Decide(halfDecision(state, state.mid))
    }

    // ── Decisions ─────────────────────────────────────────────────────────────

    private fun inspectDecision(state: BinarySearchState): Decision<BinarySearchAction> {
        val middle = state.middleOfRange
        return Decision(
            kind = DecisionKind.CELL,
            // "First" is only true once. After that the learner is continuing.
            prompt = NarrationKey(
                if (state.lo == 0 && state.hi == state.values.lastIndex) {
                    NarrationId.BS_ASK_WHERE_TO_LOOK
                } else {
                    NarrationId.BS_ASK_NEXT_MOVE
                },
            ),
            options = state.range.map { slot ->
                ActionOption(
                    action = BinarySearchAction.Inspect(slot),
                    label = NarrationKey(NarrationId.BS_OPTION_CHECK_MIDDLE),
                    slot = slot,
                )
            },
            correct = BinarySearchAction.Inspect(middle),
            focus = listOf(middle),
            hint = NarrationKey(NarrationId.BS_HINT_START_MIDDLE),
            // No ladder here, and that is the point. Which half survives is a
            // judgement worth three rungs of teaching; where the middle is, is
            // arithmetic. A learner who miscounted is not helped by being asked
            // to reason again — they are helped by being shown the sum once and
            // then doing it themselves next round.
            guidance = listOf(
                NarrationKey(
                    NarrationId.BS_RETRY_MIDDLE_IS,
                    listOf(middle, state.values[middle]),
                ),
            ),
            minimalFeedback = NarrationKey(NarrationId.BS_RETRY_NOT_THE_MIDDLE),
            correctFeedback = NarrationKey(
                NarrationId.BS_CORRECT_MIDDLE,
                listOf(state.values[middle]),
            ),
            hintLadder = listOf(
                NarrationKey(NarrationId.BS_HINT_START_MIDDLE),
                NarrationKey(NarrationId.BS_HINT_MIDDLE_OF_ACTIVE, listOf(state.remaining)),
                NarrationKey(NarrationId.BS_RETRY_MIDDLE_EXPLICIT, listOf(state.values[middle])),
            ),
            // Try asks for the arithmetic as well as the half. Letting the app
            // compute `mid` meant the learner reached Challenge never having
            // found a middle themselves, and the first thing Challenge asks for
            // is a middle.
            autoInTry = false,
            midpoint = MidpointReadout(lo = state.lo, hi = state.hi, mid = middle),
        )
    }

    private fun halfDecision(
        state: BinarySearchState,
        mid: Int,
    ): Decision<BinarySearchAction> {
        val midValue = state.values[mid]
        val targetIsLarger = midValue < state.target
        val correct: BinarySearchAction = when {
            midValue == state.target -> BinarySearchAction.Found
            targetIsLarger -> BinarySearchAction.KeepRight
            else -> BinarySearchAction.KeepLeft
        }

        val options = buildList<ActionOption<BinarySearchAction>> {
            add(ActionOption(BinarySearchAction.KeepLeft, key(NarrationId.BS_OPTION_LEFT)))
            add(ActionOption(BinarySearchAction.KeepRight, key(NarrationId.BS_OPTION_RIGHT)))
            if (midValue == state.target) {
                add(ActionOption(BinarySearchAction.Found, key(NarrationId.BS_OPTION_FOUND)))
            }
        }

        return Decision(
            kind = DecisionKind.OPTIONS,
            prompt = NarrationKey(NarrationId.BS_ASK_WHICH_HALF, listOf(state.target)),
            options = options,
            correct = correct,
            focus = listOf(mid),
            hint = NarrationKey(NarrationId.BS_HINT_COMPARE, listOf(midValue, state.target)),
            // Least to most explicit. A wrong answer never advances the algorithm,
            // so the ladder is the only thing that changes.
            guidance = listOf(
                NarrationKey(NarrationId.BS_RETRY_LOOK_AGAIN, listOf(midValue, state.target)),
                NarrationKey(
                    if (targetIsLarger) {
                        NarrationId.BS_RETRY_ASK_LARGER
                    } else {
                        NarrationId.BS_RETRY_ASK_SMALLER
                    },
                ),
                NarrationKey(
                    when {
                        midValue == state.target -> NarrationId.BS_RETRY_EXPLAIN_FOUND
                        targetIsLarger -> NarrationId.BS_RETRY_EXPLAIN_RIGHT
                        else -> NarrationId.BS_RETRY_EXPLAIN_LEFT
                    },
                    listOf(midValue, state.target),
                ),
            ),
            // Challenge restates the evidence and stops. It must not become Try.
            minimalFeedback = NarrationKey(
                when {
                    midValue == state.target -> NarrationId.BS_WATCH_COMPARE_EQUAL
                    targetIsLarger -> NarrationId.BS_WATCH_COMPARE_LESS
                    else -> NarrationId.BS_WATCH_COMPARE_GREATER
                },
                listOf(midValue, state.target),
            ),
            whyWrong = buildMap<BinarySearchAction, NarrationKey> {
                if (correct != BinarySearchAction.KeepLeft) {
                    put(
                        BinarySearchAction.KeepLeft,
                        NarrationKey(
                            NarrationId.BS_WHY_LEFT_IMPOSSIBLE,
                            listOf(state.target, midValue),
                        ),
                    )
                }
                if (correct != BinarySearchAction.KeepRight) {
                    put(
                        BinarySearchAction.KeepRight,
                        NarrationKey(
                            NarrationId.BS_WHY_RIGHT_IMPOSSIBLE,
                            listOf(state.target, midValue),
                        ),
                    )
                }
            },
            correctFeedback = NarrationKey(
                when {
                    midValue == state.target -> NarrationId.BS_CORRECT_FOUND
                    targetIsLarger -> NarrationId.BS_CORRECT_RIGHT
                    else -> NarrationId.BS_CORRECT_LEFT
                },
                listOf(midValue, state.target),
            ),
            hintLadder = listOf(
                NarrationKey(NarrationId.BS_HINT_COMPARE, listOf(midValue, state.target)),
                NarrationKey(
                    if (targetIsLarger) {
                        NarrationId.BS_RETRY_ASK_LARGER
                    } else {
                        NarrationId.BS_RETRY_ASK_SMALLER
                    },
                ),
                NarrationKey(
                    when {
                        midValue == state.target -> NarrationId.BS_RETRY_EXPLAIN_FOUND
                        targetIsLarger -> NarrationId.BS_RETRY_EXPLAIN_RIGHT
                        else -> NarrationId.BS_RETRY_EXPLAIN_LEFT
                    },
                    listOf(midValue, state.target),
                ),
            ),
        )
    }

    // ── Transitions ───────────────────────────────────────────────────────────

    override fun apply(
        state: BinarySearchState,
        action: BinarySearchAction,
    ): Transition<BinarySearchState> = when (action) {
        is BinarySearchAction.Inspect -> inspect(state, action.index)
        BinarySearchAction.KeepLeft -> keepHalf(state, left = true)
        BinarySearchAction.KeepRight -> keepHalf(state, left = false)
        BinarySearchAction.Found -> found(state)
    }

    private fun inspect(state: BinarySearchState, index: Int): Transition<BinarySearchState> {
        val value = state.values[index]
        val relation = when {
            value < state.target -> Relation.LESS
            value > state.target -> Relation.GREATER
            else -> Relation.EQUAL
        }
        return Transition(
            next = state.copy(mid = index),
            events = listOf(
                VizEvent.MovePointer(PointerId.MID, index),
                VizEvent.Examine(listOf(index), ExamineRole.COMPARING),
                VizEvent.Compare(index, TARGET_SENTINEL, relation),
                VizEvent.Meter(MeterId.REMAINING, state.remaining.toLong()),
            ),
            narration = NarrationKey(
                when (relation) {
                    Relation.LESS -> NarrationId.BS_COMPARE_LESS
                    Relation.GREATER -> NarrationId.BS_COMPARE_GREATER
                    Relation.EQUAL -> NarrationId.BS_COMPARE_EQUAL
                },
                listOf(value, state.target),
            ),
            correct = index == state.middleOfRange,
        )
    }

    /**
     * Applying the *wrong* half is the same code path with a different argument.
     * Validation is what stops it ever being called that way — the algorithm stays
     * total rather than defensive.
     */
    private fun keepHalf(
        state: BinarySearchState,
        left: Boolean,
    ): Transition<BinarySearchState> {
        val mid = requireNotNull(state.mid)
        val midValue = state.values[mid]
        val correct = if (left) midValue > state.target else midValue < state.target

        val eliminated = if (left) mid..state.hi else state.lo..mid
        val nextLo = if (left) state.lo else mid + 1
        val nextHi = if (left) mid - 1 else state.hi
        val exhausted = nextHi < nextLo

        val next = state.copy(lo = nextLo, hi = nextHi, mid = null, exhausted = exhausted)

        return Transition(
            next = next,
            events = buildList {
                add(
                    VizEvent.Eliminate(
                        eliminated,
                        if (left) EliminateReason.TOO_LARGE else EliminateReason.TOO_SMALL,
                    ),
                )
                add(VizEvent.MovePointer(PointerId.LO, nextLo.takeIf { !exhausted }))
                add(VizEvent.MovePointer(PointerId.HI, nextHi.takeIf { !exhausted }))
                add(VizEvent.MovePointer(PointerId.MID, null))
                add(
                    VizEvent.Region(
                        if (exhausted) null else nextLo..nextHi,
                        RegionId.SEARCH_SPACE,
                    ),
                )
                add(VizEvent.Meter(MeterId.REMAINING, next.remaining.toLong()))
                if (exhausted) add(VizEvent.Terminal(Outcome.NotFound))
            },
            narration = NarrationKey(
                if (exhausted) {
                    NarrationId.BS_RANGE_EMPTY
                } else if (left) {
                    NarrationId.BS_ELIMINATED_RIGHT
                } else {
                    NarrationId.BS_ELIMINATED_LEFT
                },
                listOf(eliminated.count()),
            ),
            correct = correct,
        )
    }

    private fun found(state: BinarySearchState): Transition<BinarySearchState> {
        val mid = requireNotNull(state.mid)
        val actuallyFound = state.values[mid] == state.target
        return Transition(
            next = state.copy(foundAt = mid.takeIf { actuallyFound }),
            events = buildList {
                if (actuallyFound) {
                    add(VizEvent.Finalize(mid..mid))
                    add(VizEvent.Mark(mid, MarkId.TARGET))
                    add(VizEvent.Terminal(Outcome.Found(mid)))
                } else {
                    add(VizEvent.Examine(listOf(mid), ExamineRole.INSPECTING))
                }
            },
            narration = NarrationKey(NarrationId.BS_FOUND, listOf(state.target, mid)),
            correct = actuallyFound,
        )
    }

    private fun key(id: NarrationId) = NarrationKey(id)

    private companion object {
        /** `Compare` needs two indices; the target is not in the array. */
        const val TARGET_SENTINEL = -1
    }
}
