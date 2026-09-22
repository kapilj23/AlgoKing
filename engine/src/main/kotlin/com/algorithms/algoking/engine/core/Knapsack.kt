package com.algorithms.algoking.engine.core

/** One thing that could go in the bag. */
data class KnapsackItem(val name: String, val weight: Int, val value: Int)

/**
 * A 0/1 knapsack problem: items, and a bag that holds [capacity].
 *
 * Validated where it is authored, the rule `Graph.weightsOf` set (ADR-039): a
 * problem the lesson cannot teach must be impossible to build rather than handled
 * at runtime.
 *
 *  - **weights are positive** — a weightless item would always go in, and the
 *    lesson's one question, *take it or leave it?*, would stop being a question;
 *  - **values are not negative** — nobody packs something worth less than nothing;
 *  - **names are unique and not blank** — the learner is told which item a row is
 *    by its name, and two rows called the same thing would be unreadable.
 */
data class KnapsackProblem(val items: List<KnapsackItem>, val capacity: Int) {
    init {
        require(capacity >= 0) { "Capacity is $capacity; it cannot be negative." }
        for (item in items) {
            require(item.name.isNotBlank()) { "An item has a blank name." }
            require(item.weight > 0) {
                "${item.name} weighs ${item.weight}; weights must be positive."
            }
            require(item.value >= 0) {
                "${item.name} is worth ${item.value}; values cannot be negative."
            }
        }
        require(items.map { it.name }.toSet().size == items.size) {
            "Item names must be unique."
        }
    }

    /** What every item together weighs — the reason the bag is a problem at all. */
    val totalWeight: Int get() = items.sumOf { it.weight }

    /**
     * The best bag, worked out from the problem itself.
     *
     * **The lesson's own table is not consulted**, and that is the point: ADR-053's
     * first act happens before any table exists, and it still has to be able to say
     * *"that bag is not the best one"* truthfully. So the claim is arithmetic the
     * engine does and a test checks against brute force, rather than a sentence
     * somebody typed into the copy.
     *
     * The recurrence and the walk back are the lesson's, tie rule included — SKIP
     * when the two sides are equal — so this agrees with what the table produces
     * cell for cell. A test pins that agreement.
     */
    val bestBag: List<KnapsackItem>
        get() {
            val table = solve()
            val bag = ArrayDeque<KnapsackItem>()
            var c = capacity
            for (i in items.size downTo 1) {
                if (table[i][c] != table[i - 1][c]) {
                    bag.addFirst(items[i - 1])
                    c -= items[i - 1].weight
                }
            }
            return bag.toList()
        }

    /** What [bestBag] is worth. */
    val bestValue: Int get() = solve()[items.size][capacity]

    /**
     * What taking the most valuable item first would pack.
     *
     * The lesson claims this loses, so the engine computes it and a test proves the
     * claim on both authored bags rather than trusting the copy.
     */
    val greedyBag: List<KnapsackItem>
        get() {
            var left = capacity
            val picked = mutableListOf<KnapsackItem>()
            for (candidate in items.sortedByDescending { it.value }) {
                if (candidate.weight <= left) {
                    picked += candidate
                    left -= candidate.weight
                }
            }
            return picked
        }

    val greedyValue: Int get() = greedyBag.sumOf { it.value }

    /** How many bags trying every combination would mean: 2ⁿ. */
    val bagCount: Long get() = if (items.size >= 62) Long.MAX_VALUE else 1L shl items.size

    private fun solve(): List<List<Int>> {
        val table = MutableList(items.size + 1) { MutableList(capacity + 1) { 0 } }
        for (i in 1..items.size) {
            val item = items[i - 1]
            for (c in 0..capacity) {
                val exclude = table[i - 1][c]
                val include =
                    if (item.weight <= c) item.value + table[i - 1][c - item.weight] else null
                table[i][c] = if (include != null && include > exclude) include else exclude
            }
        }
        return table
    }
}
