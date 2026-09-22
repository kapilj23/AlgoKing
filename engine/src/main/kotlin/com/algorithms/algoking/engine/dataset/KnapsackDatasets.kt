package com.algorithms.algoking.engine.dataset

import com.algorithms.algoking.engine.core.Dataset
import com.algorithms.algoking.engine.core.KnapsackItem
import com.algorithms.algoking.engine.core.KnapsackProblem

/**
 * The two 0/1 Knapsack bags — ADR-053.
 *
 * Both are four everyday things in a 5 kg bag, because the first act packs one by
 * hand and a learner has to be able to hold the whole problem in their head while
 * they do it. Both were checked by exhaustive search rather than by eye, and every
 * one of these properties is a test:
 *
 *  - **one optimal bag**, so no question has two right answers;
 *  - **no ties anywhere in the table** — a cell where SKIP and TAKE are worth the
 *    same would mark a learner wrong for a convention rather than for knapsack,
 *    and with two optimal bags that tie lands on the answer cell itself;
 *  - **everything together does not fit**, and no three of them do either, so the
 *    first question has a true answer;
 *  - **exactly one item still fits** once the most valuable one is in, so the
 *    hand-packing question has exactly one;
 *  - **taking the most valuable item first loses**, so the bag the learner packs by
 *    hand is genuinely beaten by the one they are asked to find.
 */
object KnapsackDatasets {

    /**
     * Laptop 3/8 · Headphones 2/6 · Camera 4/10 · Watch 1/3, capacity 5.
     *
     * The first act packs the Camera (the most valuable) and then the Watch (the
     * only thing left that fits) for 13, and then asks which of two full 5 kg bags
     * is worth more — because **Laptop + Headphones is 14**, and that is where
     * grabbing the most valuable thing first is refuted by the learner rather than
     * by the copy.
     *
     * In the table: the answer cell `dp[4][5]` is a **SKIP**, and the walk back
     * leaves two out before it takes two.
     */
    val watchProblem = KnapsackProblem(
        items = listOf(
            KnapsackItem("Laptop", weight = 3, value = 8),
            KnapsackItem("Headphones", weight = 2, value = 6),
            KnapsackItem("Camera", weight = 4, value = 10),
            KnapsackItem("Watch", weight = 1, value = 3),
        ),
        capacity = 5,
    )

    /**
     * Tent 4/10 · Water 1/2 · Rope 2/5 · Stove 3/9, capacity 5 — application, not
     * recall.
     *
     * The same size and the same story, and the opposite shape everywhere a
     * memorised WATCH would mislead: the hand-packed bag is Tent + Water for 12
     * against **Rope + Stove for 14**, the answer cell `dp[4][5]` is a **TAKE**
     * rather than a SKIP, and the walk back takes two before it leaves two out.
     */
    val tryProblem = KnapsackProblem(
        items = listOf(
            KnapsackItem("Tent", weight = 4, value = 10),
            KnapsackItem("Water", weight = 1, value = 2),
            KnapsackItem("Rope", weight = 2, value = 5),
            KnapsackItem("Stove", weight = 3, value = 9),
        ),
        capacity = 5,
    )

    val watch = Dataset(values = emptyList(), label = "watch", knapsack = watchProblem)

    val tryIt = Dataset(values = emptyList(), label = "try", knapsack = tryProblem)
}
