package com.algorithms.algoking.engine.scene

/**
 * A message, an alphabet mapping, and the message it becomes —
 * DESIGN_SYSTEM.md §6.16j.
 *
 * ### Why this is a seventh shape
 *
 * The same judgement ADR-030, ADR-033, ADR-034, ADR-040 and ADR-044 each made, and
 * that ADR-036 and ADR-045 each refused: a shape is for a new *kind* of data, and
 * this is one.
 *
 * A Caesar cipher is a **mapping** — 26 from-letter/to-letter pairs — sitting
 * between two aligned messages of the same length. None of the six existing shapes
 * says that:
 *
 *  - a `SequenceScene` is one row whose slots are positions, and there are three
 *    rows here with three different meanings;
 *  - a `PrefixScene` has two rows, but lays them over shared slots offset by one,
 *    because `array[i]` produced `prefix[i + 1]`. Plaintext and ciphertext are the
 *    same length and line up exactly, and the alphabet lines up with neither;
 *  - a `CountingScene`'s middle row is indexed by **value**; this one is indexed
 *    by letter and carries *two* letters per entry, which a `CountBucket`'s one
 *    value and one count cannot hold without one of them meaning something else;
 *  - a `DpTableScene` has two axes. This has one, twice over.
 *
 * Everything inside is ordinary scene data: the same [Cell] and [CellState] every
 * other lesson uses, so a ciphertext position not yet produced is a
 * [CellState.GHOST] — the hole Insertion Sort established — and the message rows
 * are drawn by the [Cell] renderer every other lesson draws.
 */
data class CipherScene(
    /** The message being encrypted. `Cell.label` carries the character. */
    val plaintext: List<Cell>,
    /**
     * The answer, the same length as [plaintext]. Positions not produced yet are
     * [CellState.GHOST], so the shape of the answer is visible before the answer.
     */
    val ciphertext: List<Cell>,
    /** All 26 letters and what each becomes. Always all 26 — see [CipherPair]. */
    val alphabet: List<CipherPair>,
    val plaintextLabel: String,
    val ciphertextLabel: String,
    val alphabetLabel: String,
    /**
     * The working, as data rather than a sentence: `H (7) + 3 = 10 → K`.
     *
     * Null before anything has been asked. [CipherStep.toIndex] is null while the
     * result is still the question — the rule the hash flow set (ADR-030): an
     * answer already on screen is not a question.
     */
    val step: CipherStep? = null,
    /** e.g. "Shift 3". */
    val badge: Badge? = null,
    val meters: List<MeterReadout> = emptyList(),
    /** Renames a cell state in the legend, as [SequenceScene.legendLabels] does. */
    val legendLabels: Map<CellState, String> = emptyMap(),
) : Scene

/**
 * One letter and what it becomes.
 *
 * **All 26 are always present**, including the ones this message never uses. The
 * wrap is the half of the cipher a learner gets wrong, and it is only visible as a
 * picture if `X Y Z` are on screen sitting above `A B C`. A mapping that showed
 * only the letters in play would quietly remove the lesson.
 *
 * It carries **two letters**, which is why this is not a [Cell]: a cell has one
 * value, and a projector that has to remember which of the two its one field
 * currently means is a projector that will eventually mean the wrong one. The same
 * reasoning `CountBucket` was given.
 */
data class CipherPair(
    val slot: Int,
    val from: Char,
    val to: Char,
    val state: CellState,
)

/**
 * The arithmetic behind one character, with the wrap shown rather than hidden.
 *
 * ```
 * H (7)  + 3 = 10        -> K
 * Z (25) + 3 = 28 - 26 = 2  -> C      wrapped
 * ```
 *
 * [sum] is the total *before* the wrap, so the renderer can show the subtraction
 * that `mod 26` actually performs. A learner told only "it wraps" has a word; one
 * who watches 28 become 2 has the rule.
 */
data class CipherStep(
    val from: Char,
    val fromIndex: Int,
    val shift: Int,
    /** `fromIndex + shift`, before any wrap. */
    val sum: Int,
    val wrapped: Boolean,
    /** Null while the result is still the question. */
    val toIndex: Int? = null,
    val to: Char? = null,
)
