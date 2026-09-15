package com.algorithms.algoking.engine.scene

/**
 * Column-aligned rows of bits, and the truth table that combines them —
 * DESIGN_SYSTEM.md §6.16k.
 *
 * ### Why this is an eighth shape
 *
 * The bar every shape has had to clear (ADR-030, ADR-033, ADR-034, ADR-040,
 * ADR-044, ADR-046) and that ADR-036 and ADR-045 each refused: it is a new *kind*
 * of data, not a familiar kind in a new lesson.
 *
 * A bitwise operation is **three rows sharing one set of columns**, where the third
 * is computed from the two above it, position by position. Nothing else here says
 * that:
 *
 *  - `CountingScene` also has three rows, and its own documentation says they
 *    deliberately **do not** share columns — input position 0 has nothing to do
 *    with bucket 0. Here the shared column *is* the operation;
 *  - `PrefixScene` shares columns between two rows, but offset by one, because
 *    `array[i]` produced `prefix[i + 1]`. These three line up exactly;
 *  - `CipherScene` has two aligned messages plus a 26-letter lookup. There is no
 *    third aligned row for a key, and a `CipherPair` holds two letters where a
 *    truth-table row holds three bits;
 *  - `DpTableScene` is the near miss and the instructive one. It is `rows ×
 *    columns`, which this could be squeezed into — but its two axes are two
 *    *quantities* (items allowed, room left) and a cell is a point in that space.
 *    These rows are three different *things* — input, key, output — that happen to
 *    line up, and "the key's bit 2" is not a coordinate. It also carries item
 *    cards, a bag meter and a two-sided choice strip that a bitwise lesson would
 *    null out, which is a union pretending to be a record.
 *
 * Everything inside is ordinary scene data: the same [Cell] and [CellState] every
 * other lesson uses, so a result bit not computed yet is a [CellState.GHOST] — the
 * hole Insertion Sort established.
 */
data class BitwiseScene(
    /**
     * The rows, top to bottom, all the same length. The **last** is the result the
     * others produce, and the renderer draws a rule above it.
     *
     * A list rather than three named fields because the labels change with the
     * phase — encrypting reads *Plaintext / Key / Ciphertext*, and applying the key
     * again reads *Ciphertext / Key / Recovered* — while the shape does not.
     */
    val rows: List<BitRow>,
    /** All four rows, always. The one matching the current bits is [TruthRow.active]. */
    val truthTable: List<TruthRow>,
    /**
     * The working, as data rather than a sentence: `1 XOR 1 = 0`.
     *
     * Null before anything is being decided. [BitStep.result] is null while the
     * result is still the question — the rule the hash flow set (ADR-030): an
     * answer already on screen is not a question.
     */
    val step: BitStep? = null,
    /** e.g. "XOR". */
    val badge: Badge? = null,
    val meters: List<MeterReadout> = emptyList(),
    /** Renames a cell state in the legend, as [SequenceScene.legendLabels] does. */
    val legendLabels: Map<CellState, String> = emptyMap(),
) : Scene {
    /** How many columns every row has. */
    val width: Int get() = rows.firstOrNull()?.bits?.size ?: 0
}

/** One labelled row of bits. */
data class BitRow(val label: String, val bits: List<Cell>)

/**
 * One line of the truth table.
 *
 * It carries **three** bits, which is why this is not a [Cell]: a cell has one
 * value, and a projector that has to remember which of three its one field
 * currently means is a projector that will eventually mean the wrong one. The same
 * reasoning `CountBucket` and `CipherPair` were each given.
 */
data class TruthRow(
    val a: Int,
    val b: Int,
    val result: Int,
    /** True for the row that answers the bit currently on the table. */
    val active: Boolean = false,
)

/** `1 XOR 1 = 0`. [result] is null while it is still the question. */
data class BitStep(
    /** Which column, so the strip and the rows agree about what is being asked. */
    val index: Int,
    val a: Int,
    val b: Int,
    val result: Int? = null,
)
