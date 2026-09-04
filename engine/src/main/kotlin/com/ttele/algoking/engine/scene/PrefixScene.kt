package com.ttele.algoking.engine.scene

/**
 * Two aligned arrays — DESIGN_SYSTEM.md §6.16f.
 *
 * ### Why this is not a `SequenceScene`
 *
 * Prefix Sum is the second lesson whose picture is genuinely not one sequence,
 * and it fails to fit for a different reason than the hash map did (ADR-030). It
 * has **two arrays of different lengths**, and the whole lesson lives in the
 * *offset* between them: `prefix[i + 1]` is the running total of everything up to
 * and including `values[i]`, which is why the prefix row is one cell longer and
 * why it sits half a cell to the right.
 *
 * Flattening both into one row of `n + (n + 1)` cells would say they are one
 * sequence, which is the single thing the learner must not believe — and showing
 * only one at a time would hide the relationship that *is* the technique.
 *
 * Everything inside is ordinary scene data: the same [Cell] and [CellState] every
 * other lesson uses, so a prefix cell that has not been computed yet is
 * [CellState.GHOST] — "a hole, never a greyed-out number" — exactly as Insertion
 * Sort's vacated slot is.
 */
data class PrefixScene(
    /** The original array. */
    val source: List<Cell>,
    /**
     * The prefix array, `source.size + 1` long. Entries not computed yet are
     * [CellState.GHOST], so the row shows the shape of the answer before the
     * answer exists.
     */
    val prefix: List<Cell>,
    /** Row captions, e.g. "Array" and "Prefix". */
    val sourceLabel: String,
    val prefixLabel: String,
    /**
     * The working, as data rather than a sentence: `2 + 4 = 6` while building,
     * `16 − 2 = 14` while querying.
     */
    val equation: PrefixEquation? = null,
    /**
     * The span being asked about, drawn as a bracket under the source row. Null
     * while the prefix array is still being built.
     */
    val queryRange: IntRange? = null,
    val badge: Badge? = null,
    val meters: List<MeterReadout> = emptyList(),
    /** Renames a cell state in the legend, as [SequenceScene.legendLabels] does. */
    val legendLabels: Map<CellState, String> = emptyMap(),
) : Scene

/**
 * One line of arithmetic, with its operands named.
 *
 * [result] is null while the result is still the question. That is the same rule
 * the hash flow follows — an answer already on screen is not a question
 * (ADR-030) — and it is what lets the identical component carry both the
 * walkthrough's statement and Try's prompt.
 */
data class PrefixEquation(
    val left: Int,
    val operator: PrefixOp,
    val right: Int,
    val result: Int? = null,
    /** e.g. `prefix[1]`, so the learner reads the formula and not just the sum. */
    val leftLabel: String? = null,
    val rightLabel: String? = null,
    /** e.g. `prefix[2]` — what is being computed. */
    val resultLabel: String? = null,
)

enum class PrefixOp { PLUS, MINUS }
