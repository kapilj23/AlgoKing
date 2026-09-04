package com.ttele.algoking.engine.challenge

/**
 * A nudge, at one of two strengths.
 *
 * [rule] is true for the rewarded rung, which states the general rule of Binary
 * Search. It never states *this* run's answer, and that distinction is the whole
 * design: an ad may buy the method, never the move.
 */
data class MissionHint(val body: String, val rule: Boolean = false)

/**
 * The hint ladders, one per gate.
 *
 * ### The golden rule
 *
 * > A hint says **what to think about**, never **what to tap**.
 *
 * Every string here is deliberately free of the current `left`, `right`, `mid`
 * and target values. That is not an oversight to be fixed later by
 * "personalising" them — the moment a hint reads *"7842 > 5000, so left = 5001"*
 * the learner has been handed the answer and the Challenge has stopped testing
 * anything. Keeping the copy static is what makes that impossible.
 *
 * Two rungs per gate: one free, one rewarded. There is no third, because there
 * is nothing left to say that would not be the answer.
 */
object MissionHints {

    fun forStep(step: MissionStep): List<MissionHint>? = when (step) {
        MissionStep.FIND_MID -> MID
        MissionStep.COMPARE -> COMPARE
        MissionStep.MOVE_BOUNDARY -> BOUNDARY
        MissionStep.DONE -> null
    }

    private val MID = listOf(
        MissionHint(
            "The middle is the box at the centre of the range you are still " +
                "searching — not the centre of the whole shelf.",
        ),
        MissionHint(
            // The canonical AlgoKing formula, written the overflow-safe way so
            // the learner can reuse it verbatim. Code Reveal prints the same
            // line in Java and Python.
            "Count from the left boundary:\n\nmid = left + (right − left) ÷ 2\n\n" +
                "When the range holds an even number of boxes there is no exact " +
                "centre, and this lands on the left-hand one.",
            rule = true,
        ),
    )

    private val COMPARE = listOf(
        MissionHint(
            "You are not choosing a direction yet. You are only reading two " +
                "numbers: the target, and the id on the box you just opened.",
        ),
        MissionHint(
            "A comparison has exactly three outcomes:\n\n" +
                "target < mid\ntarget = mid\ntarget > mid\n\n" +
                "Which one is true here is arithmetic, not judgement.",
            rule = true,
        ),
    )

    private val BOUNDARY = listOf(
        MissionHint(
            "The shelf is sorted. Given what the comparison told you, one side of " +
                "the box you opened cannot possibly hold the target — and the box " +
                "itself has already been checked.",
        ),
        MissionHint(
            "The rule, for a range that includes both ends:\n\n" +
                "if target > mid → left = mid + 1\n" +
                "if target < mid → right = mid − 1\n" +
                "if target = mid → found\n\n" +
                "The +1 and −1 matter: the middle has been checked, so it leaves " +
                "the range too.",
            rule = true,
        ),
    )
}
