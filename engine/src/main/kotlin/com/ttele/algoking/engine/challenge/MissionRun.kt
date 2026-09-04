package com.ttele.algoking.engine.challenge

import com.ttele.algoking.engine.algorithms.binarysearch.BinarySearchAction
import com.ttele.algoking.engine.algorithms.binarysearch.BinarySearchAlgorithm
import com.ttele.algoking.engine.algorithms.binarysearch.BinarySearchState
import com.ttele.algoking.engine.core.AlgorithmRunner
import com.ttele.algoking.engine.core.Probe
import com.ttele.algoking.engine.scenario.Mission

/**
 * The Challenge loop, as a pure state machine.
 *
 * ### Why this exists rather than a second Binary Search
 *
 * Challenge asks for three decisions per round — find the middle, compare it to
 * the target, then move the boundary — where the engine exposes two. It would be
 * easy to add the missing beat to `BinarySearchAlgorithm.probe()`, and it would
 * be wrong: `probe()` is shared with Watch and Try, and those two are finished.
 *
 * So the extra beat is a **decomposition, not an addition**. The engine's single
 * "which half survives" decision is presented as two gates that must both be
 * passed before the one engine action is applied. Nothing here decides what is
 * correct — [BinarySearchAlgorithm] still does, and this class only decides how
 * many questions to ask on the way there.
 *
 * ### The invariant
 *
 * A wrong answer never advances anything. Every `choose*` returns [Verdict.Wrong]
 * and leaves the runner untouched, so the learner meets the same state again.
 * That is checked by test rather than trusted.
 */
class MissionRun(val mission: Mission) {

    private val runner = AlgorithmRunner(BinarySearchAlgorithm(), mission.dataset)

    /** Remaining-candidate counts, one per shrink. The efficiency story. */
    private val _trail = mutableListOf<Int>()
    val trail: List<Int> get() = _trail.toList()

    var step: MissionStep = MissionStep.FIND_MID
        private set

    /** The comparison the learner committed to, once they have. */
    var comparison: Comparison? = null
        private set

    var decisions: Int = 0
        private set
    var mistakes: Int = 0
        private set
    var hintsUsed: Int = 0
        private set

    /** Hints taken on the step currently on screen. Resets when the step does. */
    private var hintsThisStep = 0

    private val startedAt = System.currentTimeMillis()

    init {
        _trail += state.remaining
    }

    // ── What the screen draws ─────────────────────────────────────────────────

    val state: BinarySearchState get() = runner.current.state

    val left: Int get() = state.lo
    val right: Int get() = state.hi
    val mid: Int? get() = state.mid
    val target: Int get() = state.target
    val finished: Boolean get() = step == MissionStep.DONE

    /** Slots the learner may tap right now. Empty when the answer is not a box. */
    val selectableSlots: Set<Int>
        get() = when (step) {
            MissionStep.FIND_MID -> (left..right).toSet()
            // Only the live range can hold the next boundary. Tapping a discarded
            // box is not a wrong answer, it is a mis-tap, and the two should not
            // be recorded the same way.
            MissionStep.MOVE_BOUNDARY -> (left..right).toSet()
            else -> emptySet()
        }

    /** Which boundary the learner has to move, once the comparison is settled. */
    val boundaryToMove: Boundary?
        get() = when (comparison) {
            Comparison.GREATER -> Boundary.LEFT
            Comparison.LESS -> Boundary.RIGHT
            else -> null
        }

    val elapsedMillis: Long get() = System.currentTimeMillis() - startedAt

    // ── The three gates ───────────────────────────────────────────────────────

    /**
     * Gate 1 — which box is the middle of the live range?
     *
     * The engine names the correct slot; this only asks the question.
     */
    fun chooseMid(slot: Int): Verdict {
        if (step != MissionStep.FIND_MID) return Verdict.Ignored
        val decision = decideProbe() ?: return Verdict.Ignored
        val correct = decision.correct as? BinarySearchAction.Inspect ?: return Verdict.Ignored

        if (slot != correct.index) return wrong()

        runner.apply(correct)
        advance(MissionStep.COMPARE)
        return Verdict.Right
    }

    /**
     * Gate 2 — is the target below, at, or above the middle?
     *
     * Answering does not move the search. It is the reasoning the pointer move
     * depends on, asked out loud so that the move cannot be a coin toss.
     */
    fun chooseComparison(choice: Comparison): Verdict {
        if (step != MissionStep.COMPARE) return Verdict.Ignored
        val midSlot = mid ?: return Verdict.Ignored
        val actual = Comparison.of(target, state.values[midSlot])

        if (choice != actual) return wrong()

        comparison = actual
        // An exact hit ends the round here: there is no boundary left to move.
        return if (actual == Comparison.EQUAL) {
            runner.apply(BinarySearchAction.Found)
            advance(MissionStep.DONE)
            Verdict.Right
        } else {
            advance(MissionStep.MOVE_BOUNDARY)
            Verdict.Right
        }
    }

    /**
     * Gate 3 — move the boundary that discards the half which cannot hold the
     * target.
     *
     * Both the *which* and the *where* must be right. Moving the correct pointer
     * to the wrong slot is as wrong as moving the wrong pointer, because the
     * off-by-one is the part of Binary Search people actually get wrong.
     */
    fun moveBoundary(boundary: Boundary, toSlot: Int): Verdict {
        if (step != MissionStep.MOVE_BOUNDARY) return Verdict.Ignored
        val midSlot = mid ?: return Verdict.Ignored
        val settled = comparison ?: return Verdict.Ignored

        val expectedBoundary = boundaryToMove ?: return Verdict.Ignored
        val expectedSlot = when (settled) {
            Comparison.GREATER -> midSlot + 1
            Comparison.LESS -> midSlot - 1
            Comparison.EQUAL -> return Verdict.Ignored
        }
        if (boundary != expectedBoundary || toSlot != expectedSlot) return wrong()

        // The engine performs the move. `KeepRight` *is* left = mid + 1, and
        // `KeepLeft` *is* right = mid - 1 — asserted in MissionRunTest so this
        // stays true if the algorithm is ever rewritten.
        runner.apply(
            if (settled == Comparison.GREATER) {
                BinarySearchAction.KeepRight
            } else {
                BinarySearchAction.KeepLeft
            },
        )
        comparison = null
        _trail += state.remaining

        advance(if (runner.probe() is Probe.Terminal) MissionStep.DONE else MissionStep.FIND_MID)
        return Verdict.Right
    }

    // ── Hints ─────────────────────────────────────────────────────────────────

    /** Whether the next hint on this step is free, behind an ad, or spent. */
    val hintAccess: HintAccess get() = HintPolicy.access(hintsThisStep, ladderDepth = 2)

    /**
     * Take the next hint for the current step.
     *
     * Hints are pure reads — there is no path from here to `runner.apply`, which
     * is what makes "a hint cannot corrupt the search" structural rather than a
     * promise.
     */
    fun takeHint(): MissionHint? {
        val ladder = MissionHints.forStep(step) ?: return null
        if (hintsThisStep >= ladder.size) return ladder.last()
        val hint = ladder[hintsThisStep]
        hintsThisStep++
        hintsUsed++
        return hint
    }

    /**
     * An immutable view of the run, for a UI that redraws from values.
     *
     * The run itself is a mutable state machine; handing the screen a snapshot
     * after every interaction keeps the two from disagreeing about what is on
     * screen, and keeps Compose out of the engine.
     */
    fun snapshot(): MissionSnapshot = MissionSnapshot(
        step = step,
        left = left,
        right = right,
        mid = mid,
        comparison = comparison,
        selectableSlots = selectableSlots,
        trail = trail,
        decisions = decisions,
        mistakes = mistakes,
        hintsUsed = hintsUsed,
        hintAccess = hintAccess,
        finished = finished,
        elapsedMillis = elapsedMillis,
    )

    // ── Internals ─────────────────────────────────────────────────────────────

    private fun decideProbe(): com.ttele.algoking.engine.decision.Decision<BinarySearchAction>? =
        (runner.probe() as? Probe.Decide<BinarySearchAction>)?.decision

    private fun wrong(): Verdict {
        mistakes++
        decisions++
        return Verdict.Wrong
    }

    private fun advance(to: MissionStep) {
        decisions++
        step = to
        hintsThisStep = 0
    }
}

/** Where the learner is in the round. */
enum class MissionStep { FIND_MID, COMPARE, MOVE_BOUNDARY, DONE }

/** Which end of the live range a boundary sits at. */
enum class Boundary { LEFT, RIGHT }

/** The target against the middle. */
enum class Comparison {
    LESS, EQUAL, GREATER;

    companion object {
        fun of(target: Int, mid: Int): Comparison = when {
            target < mid -> LESS
            target > mid -> GREATER
            else -> EQUAL
        }
    }
}

sealed interface Verdict {
    /** Accepted. The engine moved. */
    data object Right : Verdict

    /** Refused. **Nothing moved** — the same question is still on screen. */
    data object Wrong : Verdict

    /** Not a question that was being asked. Costs nothing and records nothing. */
    data object Ignored : Verdict
}

/** Everything the Challenge screen draws, as values. */
data class MissionSnapshot(
    val step: MissionStep,
    val left: Int,
    val right: Int,
    val mid: Int?,
    val comparison: Comparison?,
    val selectableSlots: Set<Int>,
    val trail: List<Int>,
    val decisions: Int,
    val mistakes: Int,
    val hintsUsed: Int,
    val hintAccess: HintAccess,
    val finished: Boolean,
    val elapsedMillis: Long,
) {
    val remaining: Int get() = if (right < left) 0 else right - left + 1

    /** How much of the shelf has been ruled out, 0..1. Drives the shrink meter. */
    fun eliminatedFraction(total: Int): Float =
        if (total <= 0) 0f else 1f - remaining.toFloat() / total
}

/**
 * Fold a finished mission into the same [ChallengeRun] every other lesson
 * reports, so stars, the result screen and progress need no mission-specific
 * branch anywhere.
 *
 * `comparisons` is the number of boxes opened, which is the cost a learner
 * genuinely controls in a search — the same quantity the Efficiency star family
 * grades (PRODUCT_SPEC.md §7).
 */
fun MissionRun.toChallengeRun(challenge: Challenge): ChallengeRun = ChallengeRun(
    challenge = challenge,
    decisions = decisions,
    correctDecisions = (decisions - mistakes).coerceAtLeast(0),
    mistakes = mistakes,
    hintsUsed = hintsUsed,
    comparisons = trail.size,
    elapsedMillis = elapsedMillis,
    completed = finished,
    targetFound = finished,
)
