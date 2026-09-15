package com.algorithms.algoking.engine.algorithms.xor

import com.algorithms.algoking.engine.core.Algorithm
import com.algorithms.algoking.engine.core.AlgorithmId
import com.algorithms.algoking.engine.core.Dataset
import com.algorithms.algoking.engine.core.Probe
import com.algorithms.algoking.engine.core.Transition
import com.algorithms.algoking.engine.core.XorProblem
import com.algorithms.algoking.engine.core.bitAt
import com.algorithms.algoking.engine.core.xorBit
import com.algorithms.algoking.engine.core.xorBits
import com.algorithms.algoking.engine.decision.Action
import com.algorithms.algoking.engine.decision.ActionOption
import com.algorithms.algoking.engine.decision.Decision
import com.algorithms.algoking.engine.decision.DecisionKind
import com.algorithms.algoking.engine.event.ExamineRole
import com.algorithms.algoking.engine.event.MeterId
import com.algorithms.algoking.engine.event.Outcome
import com.algorithms.algoking.engine.event.VizEvent
import com.algorithms.algoking.engine.narration.NarrationId
import com.algorithms.algoking.engine.narration.NarrationKey

/**
 * What the learner does in the XOR cipher.
 *
 * One judgement, one bit at a time: *what is `a XOR b`?* There is no mechanical
 * beat anywhere in this lesson — every column is a decision, because the XOR
 * **is** the lesson and an app that computed it would leave the learner tapping
 * through a table that fills itself.
 */
sealed interface XorAction : Action {

    /** Commit [bit] as the result of the column at the cursor. */
    data class SetBit(val bit: Int) : XorAction
}

/** Which way round the key is being applied. */
enum class XorPhase {
    /** `plaintext XOR key = ciphertext`. */
    ENCRYPT,

    /** `ciphertext XOR key = plaintext` — the same operation, and the whole point. */
    DECRYPT,
}

/**
 * Immutable state — the single source of truth for both rows, the cursor, the
 * result so far, the expected answer and the phase.
 *
 * [produced] holds only the bits decided so far, so its length *is* the cursor
 * within the phase and the two can never disagree — the same call `PrefixSumState`,
 * `FibonacciState` and `CaesarState` each make.
 */
data class XorState(
    val problem: XorProblem,
    /** Bits produced by the first pass. Grows to the message width. */
    val encrypted: String,
    /** Bits produced by the second pass. Stays empty unless the lesson goes back. */
    val decrypted: String,
) {

    /**
     * Which pass is running — **derived, never stored**.
     *
     * Storing it and rolling it over in `apply` is the obvious build and it is
     * wrong: the frame that finishes encryption would then already *be* in the
     * decrypt phase, so the picture explaining the last encrypted bit would be
     * drawn with the rows relabelled and the result row emptied. The learner would
     * read "1010 ⊕ 1100 = 0110" beside a blank. Deriving it means the state cannot
     * disagree with itself, and [XorProjector] decides which pass a *frame*
     * belongs to from the events it carries.
     */
    val phase: XorPhase
        get() = when {
            // A lesson that never goes back is always in the first pass — including
            // after it has finished, so anything reading `produced` at completion
            // gets the answer rather than an empty second pass that never runs.
            !problem.roundTrip -> XorPhase.ENCRYPT
            encrypted.length < problem.length -> XorPhase.ENCRYPT
            else -> XorPhase.DECRYPT
        }

    /** The result of the current pass, so far. */
    val produced: String
        get() = if (phase == XorPhase.ENCRYPT) encrypted else decrypted

    /** What the first pass produced. Empty until it has finished. */
    val ciphertext: String get() = if (encrypted.length == problem.length) encrypted else ""

    /** The top row of the current pass: the plaintext, or the ciphertext. */
    val input: String
        get() = when (phase) {
            XorPhase.ENCRYPT -> problem.plaintext
            XorPhase.DECRYPT -> encrypted
        }

    val key: String get() = problem.key

    val width: Int get() = problem.length

    /** The column being decided. Past the end once the phase is done. */
    val index: Int get() = produced.length

    val phaseComplete: Boolean get() = produced.length >= width

    /**
     * True once there is nothing left to do at all — one pass, or both when the
     * lesson goes back the other way.
     */
    val finished: Boolean
        get() = when {
            encrypted.length < width -> false
            !problem.roundTrip -> true
            else -> decrypted.length >= width
        }

    /** The input bit at the cursor, or null once the phase is done. */
    val currentInputBit: Int? get() = if (phaseComplete) null else bitAt(input, index)

    /** The key bit at the cursor, or null once the phase is done. */
    val currentKeyBit: Int? get() = if (phaseComplete) null else bitAt(key, index)

    /**
     * What the column at the cursor should become.
     *
     * **Nothing outside the engine computes an XOR**: the projector, the
     * walkthrough, the options and the UI all read this or [expected].
     */
    val expectedBit: Int?
        get() {
            val a = currentInputBit ?: return null
            val b = currentKeyBit ?: return null
            return xorBit(a, b)
        }

    /** The full answer for the current phase, whether or not the learner is there. */
    val expected: String get() = xorBits(input, key)

    /**
     * The original message, recovered — null until the second pass has finished.
     *
     * This is the claim the lesson exists to make, so it is derived from the run
     * rather than copied from the problem: if applying the key twice did *not*
     * give the plaintext back, this would say so.
     */
    val recovered: String?
        get() = if (problem.roundTrip && decrypted.length == width) decrypted else null

    /** How many columns of this phase are left. */
    val remaining: Int get() = (width - produced.length).coerceAtLeast(0)
}

/**
 * XOR Cipher — an educational demonstration, and deliberately a small one.
 *
 * ### What it teaches
 *
 * One operation, and one consequence of it:
 *
 * ```
 * result is 1 when the bits are different, 0 when they are the same
 *
 * plaintext  XOR key = ciphertext
 * ciphertext XOR key = plaintext        <- the same key, applied again
 * ```
 *
 * The reversal is the lesson. XOR is its own inverse, so encrypting and decrypting
 * are not two procedures — they are the same procedure run twice, and a learner
 * who watches `1010` become `0110` and then become `1010` again has seen why that
 * matters without a word of algebra.
 *
 * ### This is not secure encryption, and the lesson says so
 *
 * XOR is a building block of real cryptography, but a XOR cipher with a short or
 * reused key is trivially broken. That sentence is in the recap and on the
 * Complete screen, because a lesson that leaves a learner thinking they have seen
 * encryption has taught them something worse than nothing.
 *
 * ### Why every beat is a decision
 *
 * There is no `Probe.Mechanical` here. Both rows are on screen and every column is
 * the same judgement, so handing any of them to the app would be handing over the
 * only thing being taught.
 */
class XorCipherAlgorithm : Algorithm<XorState, XorAction> {

    override val id = AlgorithmId.XOR_CIPHER

    override fun initial(dataset: Dataset): XorState = XorState(
        problem = dataset.xor ?: FALLBACK,
        encrypted = "",
        decrypted = "",
    )

    override fun probe(state: XorState): Probe<XorAction> {
        if (state.finished) return Probe.Terminal(Outcome.Completed(true))
        return Probe.Decide(bitDecision(state))
    }

    // -- The one decision -----------------------------------------------------

    private fun bitDecision(state: XorState): Decision<XorAction> {
        val a = requireNotNull(state.currentInputBit)
        val b = requireNotNull(state.currentKeyBit)
        val correct = requireNotNull(state.expectedBit)
        val same = a == b
        val column = state.index

        return Decision(
            kind = DecisionKind.OPTIONS,
            prompt = NarrationKey(NarrationId.XOR_ASK_BIT, listOf(a, b)),
            // Two options, and both are always offered in the same order. A row of
            // buttons whose contents changed with the answer would let a learner
            // read the answer off the row rather than off the bits.
            options = listOf(0, 1).map { candidate ->
                ActionOption<XorAction>(
                    action = XorAction.SetBit(candidate),
                    label = NarrationKey(NarrationId.XOR_OPTION_BIT, listOf(candidate)),
                )
            },
            correct = XorAction.SetBit(correct),
            // Both bits of this column, on both rows — the evidence is the pair,
            // never one of them.
            focus = listOf(column),
            hint = NarrationKey(NarrationId.XOR_HINT_RULE),
            guidance = listOf(
                NarrationKey(NarrationId.XOR_RETRY_LOOK, listOf(a, b)),
                NarrationKey(
                    if (same) NarrationId.XOR_RETRY_ASK_SAME
                    else NarrationId.XOR_RETRY_ASK_DIFFERENT,
                    listOf(a, b),
                ),
                NarrationKey(
                    if (same) NarrationId.XOR_RETRY_EXPLAIN_SAME
                    else NarrationId.XOR_RETRY_EXPLAIN_DIFFERENT,
                    listOf(a, b, correct),
                ),
            ),
            minimalFeedback = NarrationKey(NarrationId.XOR_RETRY_LOOK, listOf(a, b)),
            whyWrong = mapOf(
                // There is exactly one wrong answer, and which mistake it is depends
                // only on whether the two bits match — so the feedback can name the
                // half of the rule the learner has just got backwards, which is the
                // most useful thing it could possibly say.
                XorAction.SetBit(1 - correct) to NarrationKey(
                    if (same) NarrationId.XOR_WHY_SAME_IS_ZERO
                    else NarrationId.XOR_WHY_DIFFERENT_IS_ONE,
                    listOf(a, b, correct),
                ),
            ),
            correctFeedback = NarrationKey(
                if (same) NarrationId.XOR_CORRECT_SAME else NarrationId.XOR_CORRECT_DIFFERENT,
                listOf(a, b, correct),
            ),
            hintLadder = listOf(
                NarrationKey(NarrationId.XOR_HINT_RULE),
                NarrationKey(
                    if (same) NarrationId.XOR_RETRY_ASK_SAME
                    else NarrationId.XOR_RETRY_ASK_DIFFERENT,
                    listOf(a, b),
                ),
            ),
        )
    }

    // -- Transitions ----------------------------------------------------------

    /**
     * Applies whatever it was given, correct or not.
     *
     * `apply` takes any legal action and is not the thing that decides which are
     * legal (ARCHITECTURE.md §4.1) — which is what makes `validate` a pure
     * comparison rather than a special case. In Try nothing ever calls it with a
     * wrong bit, because a `Retry` carries no action (ADR-021).
     */
    override fun apply(state: XorState, action: XorAction): Transition<XorState> =
        when (action) {
            is XorAction.SetBit -> setBit(state, action.bit)
        }

    private fun setBit(state: XorState, bit: Int): Transition<XorState> {
        // Nothing left to decide, or a value that is not a bit: a no-op leaving the
        // state byte-for-byte as it was, rather than an exception. An over-long or
        // malformed action sequence is a bad caller, not a crash.
        if (state.finished || state.phaseComplete || bit !in 0..1) {
            return Transition(state, emptyList(), null, correct = false)
        }

        val a = requireNotNull(state.currentInputBit)
        val b = requireNotNull(state.currentKeyBit)
        val correct = bit == state.expectedBit
        val at = state.index
        val grown = state.produced + bit.toString()
        val phaseDone = grown.length >= state.width

        // Only ever appends to the pass that is running. There is no phase to roll
        // over, because the phase is derived — and the second pass reads whatever
        // the first one actually produced, which is what makes the reversal a real
        // run over real output rather than a replay of the plaintext.
        val next = when (state.phase) {
            XorPhase.ENCRYPT -> state.copy(encrypted = grown)
            XorPhase.DECRYPT -> state.copy(decrypted = grown)
        }

        return Transition(
            next = next,
            events = buildList {
                // The pair being read, then the bit it produced.
                add(VizEvent.Examine(listOf(at), ExamineRole.COMPARING))
                add(VizEvent.Insert(bit, at))
                add(VizEvent.Meter(MeterId.REMAINING, (state.width - grown.length).toLong()))
                if (phaseDone) add(VizEvent.Finalize(0 until state.width))
                if (next.finished) add(VizEvent.Terminal(Outcome.Completed(correct)))
            },
            narration = NarrationKey(NarrationId.XOR_SET, listOf(a, b, bit)),
            correct = correct,
        )
    }

    private companion object {
        /** Used only when a dataset forgets to say; every authored one says. */
        val FALLBACK = XorProblem("1010", "1100", roundTrip = true)
    }
}
