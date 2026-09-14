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
}
