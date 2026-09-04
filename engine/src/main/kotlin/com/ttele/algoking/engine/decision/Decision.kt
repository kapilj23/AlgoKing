package com.ttele.algoking.engine.decision

import com.ttele.algoking.engine.narration.NarrationKey

/** Marker for anything a learner (or the app) can apply to an algorithm state. */
interface Action

/**
 * How the learner expresses a decision.
 *
 * This is presentation-shape, not algorithm identity: the renderer needs to know
 * whether to draw buttons or make the array tappable, and nothing more.
 */
enum class DecisionKind {
    /** A row of equal-weight buttons. */
    OPTIONS,

    /** The learner taps a cell in the sequence. */
    CELL,
}

/**
 * A decision point — ARCHITECTURE.md §6.
 *
 * The correct action is **data on the decision**, not a branch in the UI, and
 * [ActionOption] carries no `isCorrect` field — so the UI could not style the
 * right answer differently even by accident.
 */
/**
 * How the index in the middle of a range was arrived at, so the learner can
 * reproduce it instead of trusting it.
 *
 * Nothing may say "check the middle" without also being able to show this. The
 * middle is arithmetic on *positions*, so a learner who cannot see the positions
 * has been shown a magic trick rather than an algorithm.
 *
 * [exact] is false when the range holds an even number of positions, so there is
 * no single middle and the algorithm has to pick one. `BinarySearchState
 * .middleOfRange` resolves that with `left + (right - left) / 2`, which takes
 * the **left-hand** of the two. That is a rule the learner has to be told, never
 * left to infer from a number that appears without explanation.
 */
data class MidpointReadout(val lo: Int, val hi: Int, val mid: Int) {
    /** The count of positions in play — what the learner can count on screen. */
    val span: Int get() = hi - lo + 1

    /** True when [span] is odd and one position is genuinely in the middle. */
    val exact: Boolean get() = span % 2 == 1
}

data class Decision<A : Action>(
    val kind: DecisionKind,
    val prompt: NarrationKey,
    val options: List<ActionOption<A>>,
    val correct: A,
    /** Cells to emphasise while the learner reasons. */
    val focus: List<Int>,
    /** The free hint, available before any wrong answer. */
    val hint: NarrationKey,
    /**
     * Escalating guidance for repeated wrong attempts, least to most explicit.
     * Used by Try, which teaches. It never runs out: past the end, the last entry
     * repeats, because a learner is never dead-ended.
     */
    val guidance: List<NarrationKey>,
    /**
     * One short, neutral clue. Used by Challenge, which records the mistake and
     * lets the learner reason — it must never grow into the Try explanation.
     */
    val minimalFeedback: NarrationKey,
    /** Why *this particular* wrong option cannot be right. Try only. */
    val whyWrong: Map<A, NarrationKey> = emptyMap(),
    /** Said once the learner gets it, explaining what their choice achieved. */
    val correctFeedback: NarrationKey,
    /** Progressive hints for Challenge — never enough to solve it outright. */
    val hintLadder: List<NarrationKey> = emptyList(),
    /**
     * The arithmetic behind the correct answer, when there is any.
     *
     * A decision that is a *judgement* leaves this null: there is no sum that
     * proves which half can hold the target, only reasoning. A decision that is a
     * *computation* carries it, so a wrong answer can be met with the working
     * rather than another hint — you cannot reason your way to
     * `0 + (11 - 0) / 2`, you can only be shown it once and then do it yourself.
     */
    val midpoint: MidpointReadout? = null,
    /**
     * True when the *app* may answer this in Try, because it is bookkeeping the
     * learner is not being taught — Binary Search computing `mid`, for instance
     * (PRODUCT_SPEC.md §3). A genuine judgement must leave this false, or Try
     * silently answers the question it was supposed to ask.
     */
    val autoInTry: Boolean = false,
)

data class ActionOption<A : Action>(
    val action: A,
    val label: NarrationKey,
    /** For [DecisionKind.CELL]: the slot this option selects. */
    val slot: Int? = null,
)
