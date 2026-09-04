package com.ttele.algoking.engine.event

/**
 * The event model — ARCHITECTURE.md §5.2.
 *
 * Events are named for **what the renderer must do**, never for what an algorithm
 * calls it. Algorithm vocabulary lives in narration keys. Adding an algorithm
 * should add zero events.
 */
sealed interface VizEvent {

    /** Draw attention without changing anything. compare / inspect / peek. */
    data class Examine(val indices: List<Int>, val role: ExamineRole) : VizEvent

    /** A comparison actually happened — counted in [Metrics.comparisons]. */
    data class Compare(val a: Int, val b: Int, val relation: Relation) : VizEvent

    /** Two cells exchange positions. Rendered as the arc. Counted in [Metrics.swaps]. */
    data class Swap(val a: Int, val b: Int) : VizEvent

    /** Examined and deliberately left alone. The visual counterpart of "keep". */
    data class Hold(val indices: List<Int>) : VizEvent

    /** A named cursor moves. lo / hi / mid / i / j / windowStart… */
    data class MovePointer(val pointer: PointerId, val to: Int?) : VizEvent

    /** A range leaves consideration forever. Collapse and desaturate. */
    data class Eliminate(val range: IntRange, val reason: EliminateReason) : VizEvent

    /** A range is now provably final. Lock and turn green. */
    data class Finalize(val range: IntRange) : VizEvent

    /** A value enters the sequence at an index. */
    data class Insert(val value: Int, val at: Int) : VizEvent

    /** A value leaves the sequence. */
    data class Remove(val at: Int) : VizEvent

    /** A persistent annotation on one cell. minSoFar / target / best. */
    data class Mark(val index: Int?, val mark: MarkId) : VizEvent

    /** A persistent span annotation. window / sortedPrefix / searchSpace. */
    data class Region(val range: IntRange?, val region: RegionId) : VizEvent

    /** A scalar readout changed. */
    data class Meter(val meter: MeterId, val value: Long) : VizEvent

    /** The run ended. */
    data class Terminal(val outcome: Outcome) : VizEvent
}

enum class ExamineRole { COMPARING, INSPECTING, CANDIDATE }

enum class Relation { LESS, EQUAL, GREATER }

enum class EliminateReason { TOO_SMALL, TOO_LARGE, ALREADY_SORTED, OUT_OF_WINDOW }

enum class PointerId { LO, HI, MID, I, J, WINDOW_START, WINDOW_END }

enum class MarkId { TARGET, MIN_SO_FAR, BEST }

enum class RegionId { SEARCH_SPACE, WINDOW, SORTED_PREFIX }

enum class MeterId { RUNNING_SUM, BEST, DISTANCE, REMAINING }

sealed interface Outcome {
    data class Found(val index: Int) : Outcome
    data object NotFound : Outcome
    data object Sorted : Outcome
    data class Completed(val correct: Boolean) : Outcome
}
