package com.ttele.algoking.engine.scenario

import com.ttele.algoking.engine.challenge.Difficulty
import com.ttele.algoking.engine.core.Dataset
import kotlin.random.Random

/**
 * Builds a mission from a round and a seed — deterministic, and different every
 * round.
 *
 * Two rules shape every generator here, and both exist because of how learners
 * game a search:
 *
 *  - **the target is never at an end.** A target at slot 0 or the last slot can be
 *    found by guessing rather than halving, and a challenge that rewards guessing
 *    has not tested anything.
 *  - **the target is never the first middle.** Landing on the answer with the
 *    opening tap makes a one-decision challenge out of a four-decision one.
 */
object MissionCatalog {

    /** The rotation. Practice Again lands in a different world each time. */
    private val ROTATION = listOf(
        MissionKind.WAREHOUSE,
        MissionKind.BOX_OFFICE,
        MissionKind.SERVER_LOG,
    )

    fun forRound(round: Int, seed: Long): Mission {
        val kind = ROTATION[(round - 1).coerceAtLeast(0) % ROTATION.size]
        // Difficulty is a property of the data, never of the rules. MEDIUM ships;
        // the others exist so raising it later is a number, not a redesign.
        val difficulty = when {
            round <= 1 -> Difficulty.Intermediate
            round <= 3 -> Difficulty.Intermediate
            else -> Difficulty.Advanced
        }
        return build(kind, difficulty, seed + round * 31L)
    }

    fun build(kind: MissionKind, difficulty: Difficulty, seed: Long): Mission {
        val rng = Random(seed)
        val size = difficulty.size.random(rng)
        return when (kind) {
            MissionKind.WAREHOUSE -> warehouse(size, difficulty, rng)
            MissionKind.BOX_OFFICE -> boxOffice(size, difficulty, rng)
            MissionKind.SERVER_LOG -> serverLog(size, difficulty, rng)
        }
    }

    // ── Worlds ────────────────────────────────────────────────────────────────

    private fun warehouse(size: Int, difficulty: Difficulty, rng: Random): Mission {
        val ids = ascending(size, first = 1000..2400, step = 320..900, rng)
        val target = pickTarget(ids, rng)
        return Mission(
            kind = MissionKind.WAREHOUSE,
            icon = "📦",
            title = "Warehouse Mission",
            story = "The night shift stacked $size pallets down aisle 4 and " +
                "left them in product-ID order.",
            goal = "Locate product #$target.",
            subject = "pallets",
            sortedBy = "Pallets are in ascending product-ID order.",
            dataset = Dataset(values = ids, target = target, label = "warehouse"),
            labels = ids.map { "#$it" },
            targetLabel = "#$target",
            difficulty = difficulty,
        )
    }

    private fun boxOffice(size: Int, difficulty: Difficulty, rng: Random): Mission {
        val prices = ascending(size, first = 120..260, step = 40..180, rng)
        val target = pickTarget(prices, rng)
        return Mission(
            kind = MissionKind.BOX_OFFICE,
            icon = "🎟️",
            title = "Box Office Mission",
            story = "Tonight's $size screenings are listed cheapest first.",
            goal = "Find the ₹$target seat.",
            subject = "screenings",
            sortedBy = "Screenings are listed by price, low to high.",
            dataset = Dataset(values = prices, target = target, label = "boxoffice"),
            labels = prices.map { "₹$it" },
            targetLabel = "₹$target",
            difficulty = difficulty,
        )
    }

    private fun serverLog(size: Int, difficulty: Difficulty, rng: Random): Mission {
        // Minutes past midnight, so the engine compares integers and the learner
        // reads clock time.
        val start = rng.nextInt(6, 10) * 60
        val minutes = ascending(size, first = start..(start + 25), step = 7..48, rng)
        val target = pickTarget(minutes, rng)
        return Mission(
            kind = MissionKind.SERVER_LOG,
            icon = "🖥️",
            title = "Server Log Mission",
            story = "$size entries from last night's log, written in the order " +
                "they happened.",
            goal = "Open the entry at ${clock(target)}.",
            subject = "log entries",
            sortedBy = "Entries run oldest to newest.",
            dataset = Dataset(values = minutes, target = target, label = "serverlog"),
            labels = minutes.map(::clock),
            targetLabel = clock(target),
            difficulty = difficulty,
        )
    }

    // ── Shared generation ─────────────────────────────────────────────────────

    /** A strictly ascending run, so every mission is a legal Binary Search input. */
    private fun ascending(size: Int, first: IntRange, step: IntRange, rng: Random): List<Int> {
        var value = first.random(rng)
        return List(size) {
            if (it == 0) value else {
                value += step.random(rng)
                value
            }
        }
    }

    private fun pickTarget(values: List<Int>, rng: Random): Int =
        values[pickTargetSlot(values.size, rng)]

    /**
     * Pick a slot that makes the learner work for it.
     *
     * Excluding the ends is not enough on its own: a target that happens to land
     * on the first or second middle is solved in one or two rounds, which is a
     * demonstration rather than a challenge. So the rounds are *counted* — by
     * halving exactly the way the engine does — and only slots costing three to
     * five rounds are eligible.
     *
     * The fallbacks widen rather than fail: a mission that cannot be generated
     * is worse than a slightly short one.
     */
    private fun pickTargetSlot(size: Int, rng: Random): Int {
        val interior = (1..(size - 2)).toList()
        if (interior.isEmpty()) return size / 2
        val ideal = interior.filter { roundsToFind(it, size) in 3..5 }
        if (ideal.isNotEmpty()) return ideal.random(rng)
        val notTrivial = interior.filter { roundsToFind(it, size) >= 2 }
        return (notTrivial.ifEmpty { interior }).random(rng)
    }

    /**
     * How many find-mid → compare → move rounds reaching [slot] costs.
     *
     * Mirrors `BinarySearchState.middleOfRange` — the canonical lower middle.
     * The two are asserted against each other in MissionRunTest, so they cannot
     * drift apart silently.
     */
    private fun roundsToFind(slot: Int, size: Int): Int {
        var lo = 0
        var hi = size - 1
        var rounds = 0
        while (lo <= hi) {
            rounds++
            val mid = lo + (hi - lo) / 2
            when {
                mid == slot -> return rounds
                slot > mid -> lo = mid + 1
                else -> hi = mid - 1
            }
        }
        return rounds
    }

    private fun clock(minutes: Int): String {
        val h = (minutes / 60) % 24
        val m = minutes % 60
        return "%02d:%02d".format(h, m)
    }

}
