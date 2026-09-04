package com.ttele.algoking.engine.scoring

import com.ttele.algoking.engine.event.Metrics
import kotlin.math.ceil
import kotlin.math.log2

/**
 * Scoring — PRODUCT_SPEC.md §7.
 *
 * Three families exist so an efficiency formula is never applied to an algorithm
 * whose cost the learner does not control. Binary Search is Efficiency.
 */
enum class StarFamily { EFFICIENCY, ACCURACY, SCENARIO }

data class ScoreInput(
    val family: StarFamily,
    val metrics: Metrics,
    /** The comparison count of a perfect run on this dataset. */
    val optimalComparisons: Int,
    val completed: Boolean,
)

data class ScoreResult(
    val stars: Int,
    val optimal: Boolean,
    /** Generated from run data, not picked from a template pool. */
    val verdict: Verdict,
)

/**
 * The verdict line is the difference between a scoreboard and a tutor, so it is
 * built from what actually happened rather than a canned string.
 */
sealed interface Verdict {
    data object Optimal : Verdict
    data class ExtraComparisons(val extra: Int) : Verdict
    data class WrongDecisions(val count: Int) : Verdict
    data class UsedHints(val count: Int) : Verdict
    data object Completed : Verdict
}

object Scorer {

    /** ⌈log2(n + 1)⌉ — the worst case a perfect binary search ever needs. */
    fun optimalComparisons(size: Int): Int =
        if (size <= 0) 0 else ceil(log2((size + 1).toDouble())).toInt()

    fun score(input: ScoreInput): ScoreResult {
        if (!input.completed) {
            return ScoreResult(0, optimal = false, verdict = Verdict.Completed)
        }
        val m = input.metrics
        val extra = (m.comparisons - input.optimalComparisons).coerceAtLeast(0)

        val stars = when (input.family) {
            // Correctness first, then independence, then efficiency — never speed.
            // Two stars is "understood it, with some mistakes"; one star means the
            // learner needed real support to finish.
            StarFamily.EFFICIENCY -> when {
                extra == 0 && m.wrongDecisions == 0 && m.hintsUsed == 0 -> 3
                extra <= 2 && m.wrongDecisions <= 2 && m.hintsUsed <= 1 -> 2
                else -> 1
            }

            StarFamily.ACCURACY -> when {
                m.wrongDecisions == 0 && m.hintsUsed == 0 -> 3
                m.wrongDecisions <= 2 || m.hintsUsed == 1 -> 2
                else -> 1
            }

            StarFamily.SCENARIO -> if (m.wrongDecisions == 0) 3 else 2
        }

        val verdict = when {
            stars == 3 -> Verdict.Optimal
            m.wrongDecisions > 0 -> Verdict.WrongDecisions(m.wrongDecisions)
            extra > 0 -> Verdict.ExtraComparisons(extra)
            m.hintsUsed > 0 -> Verdict.UsedHints(m.hintsUsed)
            else -> Verdict.Completed
        }

        return ScoreResult(stars, optimal = stars == 3, verdict = verdict)
    }
}
