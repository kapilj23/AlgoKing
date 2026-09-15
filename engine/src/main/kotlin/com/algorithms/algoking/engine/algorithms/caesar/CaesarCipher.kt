package com.algorithms.algoking.engine.algorithms.caesar

import com.algorithms.algoking.engine.core.Algorithm
import com.algorithms.algoking.engine.core.AlgorithmId
import com.algorithms.algoking.engine.core.CipherProblem
import com.algorithms.algoking.engine.core.Dataset
import com.algorithms.algoking.engine.core.Probe
import com.algorithms.algoking.engine.core.Transition
import com.algorithms.algoking.engine.core.alphabetIndexOf
import com.algorithms.algoking.engine.core.caesarEncrypt
import com.algorithms.algoking.engine.core.letterAt
import com.algorithms.algoking.engine.core.shiftLetter
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
 * What the learner does in Caesar Cipher.
 *
 * One judgement — *what does this letter become?* — plus one beat the app performs,
 * because a space is not a letter and copying it through is not a decision.
 */
sealed interface CaesarAction : Action {

    /** Commit [letter] as the encryption of the character at the cursor. */
    data class Encrypt(val letter: Char) : CaesarAction

    /**
     * Pass a non-alphabetic character through untouched.
     *
     * Mechanical: the cipher shifts letters, so a space has exactly one legal
     * outcome and tapping the only legal target teaches a gesture
     * (PRODUCT_SPEC.md §3).
     */
    data object CopyThrough : CaesarAction
}

/**
 * Immutable state — the single source of truth for the message, the shift, the
 * cursor, the answer so far and the expected answer.
 *
 * [produced] holds only the characters encrypted so far, so its length *is* the
 * cursor and the two can never disagree — the same call [PrefixSumState] and
 * [FibonacciState] make about their arrays.
 */
data class CaesarState(
    val problem: CipherProblem,
    /** The ciphertext built so far. Grows to `plaintext.length`. */
    val produced: String,
) {

    val plaintext: String get() = problem.plaintext

    /** Always `0..25` — [CipherProblem] normalises at construction. */
    val shift: Int get() = problem.shift

    /** The position being encrypted. Past the end once finished. */
    val index: Int get() = produced.length

    val finished: Boolean get() = produced.length >= plaintext.length

    /** The character at the cursor, or null once the message is finished. */
    val currentChar: Char? get() = plaintext.getOrNull(index)

    /** True when the cursor is on something the cipher does not shift. */
    val currentIsLetter: Boolean get() = currentChar?.isLetter() == true

    /**
     * What the character at the cursor should become.
     *
     * **Nothing outside the engine encrypts anything**: the projector, the
     * walkthrough, the options and the UI all read this or [expected].
     */
    val expectedChar: Char? get() = currentChar?.let { shiftLetter(it, shift) }

    /** The full answer, whether or not the learner has got there yet. */
    val expected: String get() = problem.ciphertext

    /** `0..25` for the character at the cursor, `-1` for a non-letter. */
    val currentIndex: Int get() = currentChar?.let { alphabetIndexOf(it) } ?: -1

    /** `currentIndex + shift`, **before** the wrap — what makes `mod 26` visible. */
    val currentSum: Int get() = currentIndex + shift

    /** True when this character runs off the end of the alphabet and comes back. */
    val currentWraps: Boolean
        get() = currentIsLetter && currentSum >= CipherProblem.ALPHABET_SIZE

    /** How many characters of the message are still to do. */
    val remaining: Int get() = (plaintext.length - produced.length).coerceAtLeast(0)
}

/**
 * Caesar Cipher — the first encryption lesson, and a deliberately small one.
 *
 * ### What it teaches
 *
 * Every letter moves a fixed number of places along the alphabet, and the alphabet
 * is a **ring** rather than a line:
 *
 * ```
 * encrypted = (index + shift) mod 26
 * ```
 *
 * The `mod 26` is the whole lesson. `A -> D` is arithmetic anyone can do; `Z -> C`
 * is the thing that has to be seen, because it is where the line becomes a circle.
 * The teaching alphabet is therefore always drawn in full, and the walkthrough
 * spends a beat on the wrap even though `HELLO` never needs one.
 *
 * Decryption is named once and not built: it is this run backwards, and a lesson
 * that does both teaches neither twice as well.
 *
 * ### Scope
 *
 * Letters are shifted, case is preserved, and everything else is passed through.
 * A space is not a letter and shifting it would invent a rule the cipher does not
 * have — so the app copies it, and says so.
 */
class CaesarCipherAlgorithm : Algorithm<CaesarState, CaesarAction> {

    override val id = AlgorithmId.CAESAR_CIPHER

    override fun initial(dataset: Dataset): CaesarState = CaesarState(
        problem = dataset.cipher ?: FALLBACK,
        produced = "",
    )

    override fun probe(state: CaesarState): Probe<CaesarAction> {
        if (state.finished) return Probe.Terminal(Outcome.Completed(true))
        // A space, a comma or a digit has one legal outcome and no judgement in it.
        if (!state.currentIsLetter) return Probe.Mechanical(CaesarAction.CopyThrough)
        return Probe.Decide(letterDecision(state))
    }

    // -- The one decision -----------------------------------------------------

    private fun letterDecision(state: CaesarState): Decision<CaesarAction> {
        val from = requireNotNull(state.currentChar)
        val correct = requireNotNull(state.expectedChar)
        val fromIndex = state.currentIndex
        val shift = state.shift
        val choices = letterChoices(state, from, correct)
        val wraps = state.currentWraps

        return Decision(
            kind = DecisionKind.OPTIONS,
            prompt = NarrationKey(NarrationId.CC_ASK_LETTER, listOf(from, shift)),
            options = choices.map { candidate ->
                ActionOption<CaesarAction>(
                    action = CaesarAction.Encrypt(candidate),
                    label = NarrationKey(NarrationId.CC_OPTION_LETTER, listOf(candidate)),
                )
            },
            correct = CaesarAction.Encrypt(correct),
            // The alphabet slot the learner should be counting from. The mapping
            // row is the evidence, so the beat points at it rather than at the
            // message.
            focus = listOf(fromIndex),
            hint = NarrationKey(NarrationId.CC_HINT_COUNT, listOf(from, shift)),
            guidance = listOf(
                NarrationKey(NarrationId.CC_RETRY_LOOK, listOf(from, shift)),
                NarrationKey(NarrationId.CC_RETRY_ASK, listOf(from, shift, fromIndex)),
                NarrationKey(
                    if (wraps) NarrationId.CC_RETRY_EXPLAIN_WRAP
                    else NarrationId.CC_RETRY_EXPLAIN,
                    listOf(from, fromIndex, shift, state.currentSum, correct),
                ),
            ),
            minimalFeedback = NarrationKey(NarrationId.CC_RETRY_LOOK, listOf(from, shift)),
            whyWrong = buildMap {
                // Each distractor is named for the mistake that produces it, so the
                // feedback describes what the learner actually did rather than
                // restating the rule at them (PRODUCT_SPEC.md §5).
                for (candidate in choices) {
                    if (candidate == correct) continue
                    val id = when (candidate) {
                        shiftLetter(from, -shift) -> NarrationId.CC_WHY_BACKWARDS
                        from -> NarrationId.CC_WHY_NO_SHIFT
                        shiftLetter(from, shift + 1),
                        shiftLetter(from, shift - 1),
                        -> NarrationId.CC_WHY_OFF_BY_ONE
                        else -> NarrationId.CC_WHY_WRONG_DISTANCE
                    }
                    put(
                        CaesarAction.Encrypt(candidate),
                        NarrationKey(id, listOf(from, shift, correct, candidate)),
                    )
                }
            },
            correctFeedback = NarrationKey(
                if (wraps) NarrationId.CC_CORRECT_WRAP else NarrationId.CC_CORRECT,
                listOf(from, shift, correct, state.currentSum),
            ),
            hintLadder = listOf(
                NarrationKey(NarrationId.CC_HINT_COUNT, listOf(from, shift)),
                NarrationKey(NarrationId.CC_RETRY_ASK, listOf(from, shift, fromIndex)),
            ),
        )
    }

    /**
     * Three letters, one right, and the wrong two are the mistakes learners
     * actually make:
     *
     *  - **shifted the wrong way** — `A - 2 = Y` instead of `A + 2 = C`. The single
     *    most common Caesar error, and the one decryption depends on understanding;
     *  - **off by one** — counting the starting letter as the first step, so `A + 2`
     *    lands on `B` rather than `C`;
     *  - **not shifted at all**, which only appears where the two above collide.
     *
     * Deterministic: the correct answer's seat rotates with the cursor rather than
     * being drawn at random, so a run is reproducible and a test can pin it. The
     * construction Prefix Sum established and Fibonacci reused.
     */
    private fun letterChoices(
        state: CaesarState,
        from: Char,
        correct: Char,
    ): List<Char> {
        val shift = state.shift
        val pool = LinkedHashSet<Char>()
        pool += correct
        // Shifted backwards instead of forwards.
        pool += shiftLetter(from, -shift)
        // Counted the starting letter as step one.
        pool += shiftLetter(from, shift - 1)
        pool += shiftLetter(from, shift + 1)
        // Never moved. Only survives dedup where the shift is 0 or tiny.
        pool += from
        // Last resorts, so there are always three distinct options even when the
        // shift is 0 and most of the pool collapses onto one letter.
        pool += shiftLetter(from, shift + 2)
        pool += shiftLetter(from, shift + 3)

        val three = pool.take(3)
        val seat = state.index % three.size
        return three.drop(seat) + three.take(seat)
    }

    // -- Transitions ----------------------------------------------------------

    /**
     * Applies whatever it was given, correct or not.
     *
     * `apply` takes any legal action and is not the thing that decides which are
     * legal (ARCHITECTURE.md §4.1) — which is what makes `validate` a pure
     * comparison rather than a special case. In Try nothing ever calls it with a
     * wrong letter, because a `Retry` carries no action (ADR-021).
     */
    override fun apply(
        state: CaesarState,
        action: CaesarAction,
    ): Transition<CaesarState> = when (action) {
        is CaesarAction.Encrypt -> encrypt(state, action.letter)
        CaesarAction.CopyThrough -> copyThrough(state)
    }

    private fun encrypt(state: CaesarState, letter: Char): Transition<CaesarState> {
        // Nothing left to encrypt, or the cursor is not on a letter: a no-op that
        // leaves the state byte-for-byte as it was, rather than an exception. An
        // over-long or ill-timed action sequence is a bad caller, not a crash.
        if (state.finished || !state.currentIsLetter) {
            return Transition(state, emptyList(), null, correct = false)
        }

        val from = requireNotNull(state.currentChar)
        val correct = letter == state.expectedChar
        val at = state.index
        val next = state.copy(produced = state.produced + letter)

        return Transition(
            next = next,
            events = buildList {
                add(VizEvent.Examine(listOf(at), ExamineRole.COMPARING))
                add(VizEvent.Insert(alphabetIndexOf(letter), at))
                add(VizEvent.Meter(MeterId.REMAINING, next.remaining.toLong()))
                if (next.finished) {
                    add(VizEvent.Finalize(0..state.plaintext.lastIndex))
                    add(VizEvent.Terminal(Outcome.Completed(correct)))
                }
            },
            narration = NarrationKey(
                NarrationId.CC_ENCRYPTED,
                listOf(from, state.shift, letter),
            ),
            correct = correct,
        )
    }

    private fun copyThrough(state: CaesarState): Transition<CaesarState> {
        if (state.finished || state.currentIsLetter) {
            return Transition(state, emptyList(), null, correct = false)
        }
        val char = requireNotNull(state.currentChar)
        val at = state.index
        val next = state.copy(produced = state.produced + char)
        return Transition(
            next = next,
            events = buildList {
                add(VizEvent.Hold(listOf(at)))
                add(VizEvent.Meter(MeterId.REMAINING, next.remaining.toLong()))
                if (next.finished) {
                    add(VizEvent.Finalize(0..state.plaintext.lastIndex))
                    add(VizEvent.Terminal(Outcome.Completed(true)))
                }
            },
            narration = NarrationKey(NarrationId.CC_COPIED, listOf(char)),
            correct = true,
        )
    }

    private companion object {
        /** Used only when a dataset forgets to say; every authored one says. */
        val FALLBACK = CipherProblem("HELLO", 3)
    }
}

/**
 * The alphabet, and what each of its letters becomes under [shift].
 *
 * Always 26 entries. Built from [shiftLetter], so the mapping the learner reads and
 * the mapping the algorithm applies cannot drift apart.
 */
fun caesarAlphabet(shift: Int): List<Pair<Char, Char>> =
    (0 until CipherProblem.ALPHABET_SIZE).map { index ->
        val from = letterAt(index)
        from to shiftLetter(from, shift)
    }

/** Kept beside the transform it exercises, so the doc example is executable. */
internal fun caesarExample(): String = caesarEncrypt("HELLO", 3)
