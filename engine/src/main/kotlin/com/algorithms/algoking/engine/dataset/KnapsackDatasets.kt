package com.algorithms.algoking.engine.dataset

import com.algorithms.algoking.engine.core.Dataset
import com.algorithms.algoking.engine.core.KnapsackItem
import com.algorithms.algoking.engine.core.KnapsackProblem

/**
 * The two 0/1 Knapsack bags — `docs/zero-one-knapsack-plan.md` §2.
 *
 * Both were chosen by exhaustive search, not by eye: the brief's candidate had two
 * different optimal bags, a greedy strategy that already found the optimum, and
 * ties inside the table, any one of which would have taught something false.
 */
object KnapsackDatasets {

    /**
     * Book 2/3 · Camera 3/5 · Laptop 4/7, capacity 5.
     *
     * - **one optimal bag**: Book + Camera, weight 5, value 8;
     * - **both greedy strategies lose**: most-valuable-first and best-value-per-
     *   weight both take the Laptop and get 7;
     * - **the answer cell is a SKIP** — `dp[3][5]` keeps 8 against the Laptop's
     *   `7 + dp[2][1] = 7`, so greedy is refuted inside the table;
     * - **`dp[2][5]` reads `dp[1][2]`**, a non-zero answer from an earlier row —
     *   where dynamic programming gets its name;
     * - **no ties anywhere**, so every decision has exactly one right answer.
     */
    val watchProblem = KnapsackProblem(
        items = listOf(
            KnapsackItem("Book", weight = 2, value = 3),
            KnapsackItem("Camera", weight = 3, value = 5),
            KnapsackItem("Laptop", weight = 4, value = 7),
        ),
        capacity = 5,
    )

    /**
     * Water 2/2 · Tent 4/5 · Stove 3/4, capacity 5 — application, not recall.
     *
     * The same table size and the same recurrence, and the opposite shape where a
     * memorised WATCH would mislead: the answer cell is a **TAKE**, the walk back
     * starts with **Taken**, the left-out item is the **middle** row, and SKIP wins
     * mid-row at `dp[3][4]` rather than at the end. Best: Water + Stove, 6.
     */
    val tryProblem = KnapsackProblem(
        items = listOf(
            KnapsackItem("Water", weight = 2, value = 2),
            KnapsackItem("Tent", weight = 4, value = 5),
            KnapsackItem("Stove", weight = 3, value = 4),
        ),
        capacity = 5,
    )

    val watch = Dataset(values = emptyList(), label = "watch", knapsack = watchProblem)

    val tryIt = Dataset(values = emptyList(), label = "try", knapsack = tryProblem)
}
