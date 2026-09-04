package com.ttele.algoking.engine.challenge

/**
 * Whether the next hint is free, or has to be unlocked.
 *
 * Pure and table-tested, for the same reason `AdPolicy` is (ARCHITECTURE.md
 * §10.3): the rules around what costs an ad are easy to get subtly wrong, and
 * getting one wrong damages the product more than a missed impression earns.
 */
sealed interface HintAccess {
    /** Give it to them. No dialog, no ad, no friction. */
    data object Free : HintAccess

    /** Offered behind a rewarded ad — always with a way to decline. */
    data object Rewarded : HintAccess

    /** The ladder is spent; the last rung repeats rather than dead-ending. */
    data object Exhausted : HintAccess
}

/**
 * The rule: **the first hint of every decision is free.**
 *
 * A learner who is stuck should never have to pay to become unstuck — that is
 * the difference between a teaching product and a slot machine
 * (PRODUCT_SPEC.md §9: "Never behind an ad: … comprehension"). What an ad may
 * buy is the *next* rung, which is convenience rather than comprehension,
 * because the first rung plus the guidance ladder already contains the answer
 * route.
 *
 * [levelsUsed] counts hints taken on the decision currently on screen, so the
 * allowance resets every time the learner moves on rather than being a budget
 * for the whole run.
 */
object HintPolicy {

    /** How many rungs a Binary Search hint ladder has. */
    const val LADDER_DEPTH = 3

    fun access(levelsUsed: Int, ladderDepth: Int = LADDER_DEPTH): HintAccess = when {
        levelsUsed <= 0 -> HintAccess.Free
        levelsUsed >= ladderDepth -> HintAccess.Exhausted
        else -> HintAccess.Rewarded
    }
}
