package com.ttele.algoking.engine.scene

/**
 * Three rows: an array, a table of counts, and the answer being rebuilt —
 * DESIGN_SYSTEM.md §6.16h.
 *
 * ### Why this is not a `SequenceScene`, and not a `PrefixScene` either
 *
 * Counting Sort is the fifth shape, and it earns the same way the four before it
 * did (ADR-030, ADR-033, ADR-034, ADR-036): the data is a different *kind*, not a
 * familiar kind in a new lesson.
 *
 * A sequence is one row of cells whose slots are positions. Here there are three
 * rows, and the middle one is indexed by **value** rather than by position —
 * bucket 3 is not the fourth thing in a line, it is the answer to *"how many
 * threes are there?"*. That is the single idea the lesson exists to leave behind,
 * and flattening it into a row of positions would say the opposite.
 *
 * `PrefixScene` is closer, and still wrong: its two rows are laid out over shared
 * slots because `array[i]` genuinely produced `prefix[i + 1]`, and the offset is
 * the lesson. Counting Sort's rows have **no** column relationship at all — input
 * position 0 has nothing to do with bucket 0 — so drawing them over shared slots
 * would invent an alignment the algorithm does not have. Each row therefore owns
 * its own width, which is also what keeps the three visibly separate.
 *
 * Everything inside is ordinary scene data: the same [Cell] and [CellState] as
 * every other lesson, so an output slot that has not been filled yet is
 * [CellState.GHOST] — the hole Insertion Sort established, not a greyed-out zero
 * claiming a value exists.
 */
data class CountingScene(
    /** The array being sorted. Never reordered — counting sort does not move it. */
    val input: List<Cell>,
    /** One bucket per value in the range, in ascending value order. */
    val buckets: List<CountBucket>,
    /**
     * The answer, `input.size` long. Slots not filled yet are [CellState.GHOST],
     * so the shape of the answer is visible before the answer is.
     */
    val output: List<Cell>,
    val inputLabel: String,
    val countLabel: String,
    val outputLabel: String,
    /**
     * The working, as data rather than a sentence: `count[3]: 1 → 2`.
     *
     * Null when nothing is being counted. [CountTally.to] is null while the new
     * count is still the question — the rule the hash flow set (ADR-030): an
     * answer already on screen is not a question.
     */
    val tally: CountTally? = null,
    val badge: Badge? = null,
    val meters: List<MeterReadout> = emptyList(),
    /** Renames a cell state in the legend, as [SequenceScene.legendLabels] does. */
    val legendLabels: Map<CellState, String> = emptyMap(),
) : Scene

/**
 * One count bucket.
 *
 * It carries **two numbers**, and keeping them apart is the point: [value] is what
 * the bucket counts and [count] is how many of them have been seen. A [Cell] would
 * have to conflate them into its one `value` field, and a projector that has to
 * remember which of the two it meant is a projector that will eventually mean the
 * wrong one.
 *
 * [slot] is what a `DecisionKind.CELL` option selects, so tapping a bucket is how
 * the learner answers both of this lesson's questions.
 */
data class CountBucket(
    val slot: Int,
    val value: Int,
    val count: Int,
    val state: CellState,
    /**
     * How many of this value are still waiting to be placed, during the rebuild.
     * Equal to [count] until the output starts filling; null while counting, when
     * nothing has been placed and the distinction would be noise.
     */
    val remaining: Int? = null,
)

/** `count[3]: 1 → 2`. [to] is null while the new count is still the question. */
data class CountTally(val value: Int, val from: Int, val to: Int? = null)
