package com.algorithms.algoking.engine.algorithms.sieve

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
import com.algorithms.algoking.engine.event.VizEvent
import com.algorithms.algoking.engine.narration.NarrationId
import com.algorithms.algoking.engine.narration.NarrationKey

/**
 * What the learner does in the Sieve of Eratosthenes. Two questions:
 *
 * 1. **Which number is the next prime?** — a tap on the grid: the smallest number
 *    not crossed out yet.
 * 2. **Where does crossing out start?** — `2 × p` or `p × p`. The second is right,
 *    and knowing why is the sieve's one real insight.
 *
 * Crossing out the multiples is the app's, shown all at once in orange.
 */
sealed interface SieveAction : Action {
    data class Pick(val number: Int) : SieveAction
    data class StartAt(val number: Int) : SieveAction

    /** Cross out p², p² + p, … up to n. Mechanical. */
    data object CrossOut : SieveAction

    /** p × p is past n: everything still standing is prime. Mechanical. */
    data object Finish : SieveAction
}

enum class SievePhase { PICK, START, CROSS, FINISH, DONE }

/**
 * Immutable state. [crossedBy] is the whole model: a number in it is composite,
 * and the value is the prime that crossed it out first.
 */
data class SieveState(
    val n: Int,
    val crossedBy: Map<Int, Int>,
    /** Primes picked so far, in order. */
    val primes: List<Int>,
    /** The prime being worked on, or null between picks. */
    val current: Int?,
    val phase: SievePhase,
    /** What the last CrossOut crossed, for the picture. */
    val justCrossed: List<Int> = emptyList(),
) {
    val numbers: IntRange get() = 1..n

    fun isCrossed(k: Int): Boolean = k in crossedBy

    /** **The rule for question 1**: the smallest number ≥ 2 neither crossed nor already picked. */
    val nextPrime: Int?
        get() = (2..n).firstOrNull { it !in crossedBy && it !in primes }

    /** Every number still standing once the sieve is done — or so far. */
    val survivors: List<Int> get() = (2..n).filter { it !in crossedBy }

    /** The multiples of [p] that crossing out will reach, from p². */
    fun multiplesFromSquare(p: Int): List<Int> =
        generateSequence(p * p) { it + p }.takeWhile { it <= n }.toList()
}

/**
 * The Sieve of Eratosthenes — a free Math lesson.
 *
 * ```
 * write out 2 … n
 * repeat:
 *     p = the smallest number not crossed out and not yet used
 *     if p × p > n: stop — every number still standing is prime
 *     cross out p × p, p × p + p, p × p + 2p, … up to n
 * ```
 *
 * ### What the learner has to understand
 *
 * 1. **A number that survives is prime** — nothing smaller crossed it out, so
 *    nothing smaller divides it. The sieve never divides anything.
 * 2. **Start at p × p.** Every multiple of p below that has a smaller factor and
 *    is already crossed out: 6 by 2, and 15 by 3 before 5 ever gets to it.
 * 3. **Stop at √n.** Once p × p is past the end, there is nothing left for p to
 *    cross out.
 *
 * **Time O(n log log n)**; **space O(n)**.
 */
class SieveAlgorithm : Algorithm<SieveState, SieveAction> {

    override val id = AlgorithmId.SIEVE

    override fun initial(dataset: Dataset): SieveState = SieveState(
        n = requireNotNull(dataset.target) { "The sieve needs an upper limit." },
        crossedBy = emptyMap(),
        primes = emptyList(),
        current = null,
        phase = SievePhase.PICK,
    )

    override fun probe(state: SieveState): Probe<SieveAction> = when (state.phase) {
        SievePhase.DONE -> Probe.Terminal(Outcome.Completed(true))
        SievePhase.PICK -> state.nextPrime?.let { Probe.Decide(pickDecision(state, it)) }
            ?: Probe.Mechanical(SieveAction.Finish)
        SievePhase.START -> Probe.Decide(startDecision(state, requireNotNull(state.current)))
        SievePhase.CROSS -> Probe.Mechanical(SieveAction.CrossOut)
        SievePhase.FINISH -> Probe.Mechanical(SieveAction.Finish)
    }

    private fun pickDecision(state: SieveState, next: Int): Decision<SieveAction> {
        val args = listOf(next, state.primes.lastOrNull() ?: 0)
        return Decision(
            kind = DecisionKind.CELL,
            prompt = NarrationKey(NarrationId.SV_ASK_PICK),
            // Every number is tappable — offering only the survivors would outline the answer.
            options = state.numbers.map { k ->
                ActionOption<SieveAction>(
                    action = SieveAction.Pick(k),
                    label = NarrationKey(NarrationId.SV_OPTION_NUMBER, listOf(k)),
                    slot = k - 1,
                )
            },
            correct = SieveAction.Pick(next),
            focus = listOf(next - 1),
            hint = NarrationKey(NarrationId.SV_HINT_PICK),
            guidance = listOf(
                NarrationKey(NarrationId.SV_RETRY_PICK_LOOK),
                NarrationKey(NarrationId.SV_RETRY_PICK_ASK),
                NarrationKey(NarrationId.SV_RETRY_PICK_EXPLAIN, args),
            ),
            minimalFeedback = NarrationKey(NarrationId.SV_RETRY_PICK_LOOK),
            whyWrong = buildMap {
                for (k in state.numbers) {
                    if (k == next) continue
                    val key = when {
                        k == 1 -> NarrationKey(NarrationId.SV_WHY_ONE)
                        k in state.primes -> NarrationKey(NarrationId.SV_WHY_DONE, listOf(k))
                        k in state.crossedBy -> NarrationKey(
                            NarrationId.SV_WHY_CROSSED,
                            listOf(k, state.crossedBy.getValue(k), k / state.crossedBy.getValue(k)),
                        )
                        else -> NarrationKey(NarrationId.SV_WHY_NOT_SMALLEST, listOf(k, next))
                    }
                    put(SieveAction.Pick(k), key)
                }
            },
            correctFeedback = NarrationKey(NarrationId.SV_CORRECT_PICK, args),
            hintLadder = listOf(NarrationKey(NarrationId.SV_HINT_PICK)),
            autoInTry = false,
        )
    }

    private fun startDecision(state: SieveState, p: Int): Decision<SieveAction> {
        val square = p * p
        val double = 2 * p
        // Shared by every line about this start: p, 2 × p, p × p.
        val args = listOf(p, double, square)
        return Decision(
            kind = DecisionKind.OPTIONS,
            prompt = NarrationKey(NarrationId.SV_ASK_START, listOf(p)),
            options = listOf(double, square).map {
                ActionOption<SieveAction>(SieveAction.StartAt(it), NarrationKey(NarrationId.SV_OPTION_NUMBER, listOf(it)))
            },
            correct = SieveAction.StartAt(square),
            focus = listOf(square - 1),
            hint = NarrationKey(NarrationId.SV_HINT_START, args),
            guidance = listOf(
                NarrationKey(NarrationId.SV_RETRY_START_LOOK, args),
                NarrationKey(NarrationId.SV_RETRY_START_ASK, args),
                NarrationKey(NarrationId.SV_RETRY_START_EXPLAIN, args),
            ),
            minimalFeedback = NarrationKey(NarrationId.SV_RETRY_START_LOOK, args),
            whyWrong = mapOf(SieveAction.StartAt(double) to NarrationKey(NarrationId.SV_WHY_START_DOUBLE, args)),
            correctFeedback = NarrationKey(NarrationId.SV_CORRECT_START, args),
            hintLadder = listOf(NarrationKey(NarrationId.SV_HINT_START, args)),
            autoInTry = false,
        )
    }

    override fun apply(state: SieveState, action: SieveAction): Transition<SieveState> = when (action) {
        is SieveAction.Pick -> pick(state, action.number)
        is SieveAction.StartAt -> startAt(state, action.number)
        SieveAction.CrossOut -> crossOut(state)
        SieveAction.Finish -> finish(state)
    }

    private fun refuse(state: SieveState, slot: Int) = Transition(
        next = state,
        events = if (slot >= 0) listOf(VizEvent.Examine(listOf(slot), ExamineRole.INSPECTING)) else emptyList(),
        narration = null,
        correct = false,
    )

    private fun pick(state: SieveState, k: Int): Transition<SieveState> {
        if (state.phase != SievePhase.PICK || k != state.nextPrime) return refuse(state, k - 1)
        val next = state.copy(
            primes = state.primes + k,
            current = k,
            justCrossed = emptyList(),
            phase = when {
                // Nothing left for k to cross out: everything standing is prime.
                k * k > state.n -> SievePhase.FINISH
                // For 2, 2 × 2 and 2 + 2 are the same number — nothing to ask.
                2 * k == k * k -> SievePhase.CROSS
                else -> SievePhase.START
            },
        )
        return Transition(
            next = next,
            events = listOf(
                VizEvent.Examine(listOf(k - 1), ExamineRole.COMPARING),
                VizEvent.Meter(MeterId.RUNNING_SUM, next.primes.size.toLong()),
            ),
            narration = NarrationKey(
                if (next.phase == SievePhase.FINISH) NarrationId.SV_PICKED_LAST else NarrationId.SV_PICKED,
                listOf(k, k * k, state.n),
            ),
            correct = true,
        )
    }

    private fun startAt(state: SieveState, k: Int): Transition<SieveState> {
        val p = state.current ?: return refuse(state, k - 1)
        if (state.phase != SievePhase.START || k != p * p) return refuse(state, k - 1)
        return Transition(
            next = state.copy(phase = SievePhase.CROSS),
            events = listOf(VizEvent.Examine(listOf(k - 1), ExamineRole.CANDIDATE)),
            narration = NarrationKey(NarrationId.SV_STARTED, listOf(p, k)),
            correct = true,
        )
    }

    private fun crossOut(state: SieveState): Transition<SieveState> {
        val p = requireNotNull(state.current)
        // Only numbers still standing are newly crossed; the rest keep their first crosser.
        val fresh = state.multiplesFromSquare(p).filter { it !in state.crossedBy }
        val next = state.copy(
            crossedBy = state.crossedBy + fresh.associateWith { p },
            justCrossed = fresh,
            current = null,
            phase = SievePhase.PICK,
        )
        return Transition(
            next = next,
            events = fresh.map { VizEvent.Eliminate((it - 1)..(it - 1), EliminateReason.OUT_OF_WINDOW) },
            narration = NarrationKey(NarrationId.SV_CROSSED, listOf(p, fresh.size)),
            correct = true,
        )
    }

    private fun finish(state: SieveState): Transition<SieveState> {
        val next = state.copy(
            primes = state.survivors,
            current = null,
            justCrossed = emptyList(),
            phase = SievePhase.DONE,
        )
        return Transition(
            next = next,
            events = listOf(
                VizEvent.Finalize(0..(state.n - 1)),
                VizEvent.Meter(MeterId.RUNNING_SUM, next.primes.size.toLong()),
                VizEvent.Terminal(Outcome.Completed(true)),
            ),
            narration = NarrationKey(NarrationId.SV_FINISHED, listOf(next.primes.size, state.n)),
            correct = true,
        )
    }
}
