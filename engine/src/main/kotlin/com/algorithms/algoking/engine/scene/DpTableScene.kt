package com.algorithms.algoking.engine.scene

/**
 * A table with two meaningful axes — DESIGN_SYSTEM.md §6.16i.
 *
 * ### Why this is the sixth shape
 *
 * Every shape before it earned its place by being a different *kind* of data
 * (ADR-030, ADR-033, ADR-034, ADR-036, ADR-040), and this one does too. A dynamic
 * programming table is indexed by **two** quantities — for 0/1 Knapsack, how many
 * items are allowed and how much room there is — and the lesson lives in how a
 * cell reads the row above it: straight up for SKIP, and `weight` columns to the
 * left for TAKE.
 *
 * - `SequenceScene` has one index. Its `GRID` layout wraps a flow of boxes whose
 *   rows mean nothing.
 * - `PrefixScene` and `CountingScene` stack one-dimensional rows; no column means
 *   anything across them.
 * - `GraphScene` is nodes and edges, with no headers and no grid.
 *
 * The fields name what the renderer draws — rows, columns, headers, cells and a
 * two-sided choice — and nothing about knapsacks, so the renderer still cannot
 * tell which lesson sent it.
 *
 * ### Cells
 *
 * [cells] is `rows × columns` of the ordinary [Cell] and [CellState]. A cell not
 * computed yet is **null** — an empty slot, because a zero there would claim a
 * value exists. The one cell currently being decided is a [CellState.GHOST]: the
 * hole the learner is filling. A `DecisionKind.CELL` option selects
 * `row × columns + column`, which is also every cell's [Cell.slot].
 */
data class DpTableScene(
    val rowHeaders: List<TableHeader>,
    val columnHeaders: List<TableHeader>,
    /** What the columns count, printed over the header gutter — "Capacity". */
    val columnCaption: String,
    val cells: List<List<Cell?>>,
    /** False while the problem is still being posed, before any table exists. */
    val tableVisible: Boolean,
    /** The two outcomes being weighed. Null when nothing is being decided. */
    val choice: ChoiceStrip? = null,
    /** What the cell in focus means, in words: `dp[2][5] — …`. */
    val focusCaption: String? = null,
    /** Cards for the things being chosen between, when the table is not enough. */
    val items: List<ItemCard> = emptyList(),
    /** How full the bag is, when a bag is being packed on screen. */
    val bag: BagMeter? = null,
    val meters: List<MeterReadout> = emptyList(),
    /** Renames a cell state in the legend, as [SequenceScene.legendLabels] does. */
    val legendLabels: Map<CellState, String> = emptyMap(),
) : Scene {
    val columnCount: Int get() = columnHeaders.size
}

/** A row or column name, with an optional second line. [active] marks the one in focus. */
data class TableHeader(
    val label: String,
    val detail: String? = null,
    val active: Boolean = false,
)

/**
 * Two outcomes side by side.
 *
 * [first] is drawn in the first decision tone and [second] in the second — the
 * same positional pairing `DecisionTone.forIndex` gives the option buttons, so
 * each side is the colour of the button that chooses it.
 */
data class ChoiceStrip(val first: ChoiceSide, val second: ChoiceSide)

data class ChoiceSide(
    val caption: String,
    /** The working: `7 + dp[2][1]`. */
    val formula: String,
    /** Null while this side is still the question — it reads `?`. */
    val value: Int?,
    val emphasis: ChoiceEmphasis = ChoiceEmphasis.OPEN,
)

enum class ChoiceEmphasis {
    /** Being weighed. */
    OPEN,

    /** The side that won. */
    CHOSEN,

    /** The side that lost. */
    PASSED,
}

/** One item: its name, its two numbers, and — when it has been decided — 0 or 1. */
data class ItemCard(
    val name: String,
    val weight: Int,
    val value: Int,
    val state: CellState,
    /** `0`, `1`, `0 or 1`, or null when the question has not come up. */
    val bitLabel: String? = null,
)

data class BagMeter(
    val used: Int,
    val capacity: Int,
    val value: Int,
    val contents: List<String>,
)
