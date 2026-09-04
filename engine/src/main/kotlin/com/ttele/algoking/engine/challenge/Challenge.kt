package com.ttele.algoking.engine.challenge

import com.ttele.algoking.engine.algorithms.binarysearch.BinarySearchAlgorithm
import com.ttele.algoking.engine.core.AlgorithmRunner
import com.ttele.algoking.engine.core.Dataset
import com.ttele.algoking.engine.dataset.BinarySearchDatasets
import com.ttele.algoking.engine.dataset.ComparisonsBetween
import com.ttele.algoking.engine.dataset.DatasetSpec
import com.ttele.algoking.engine.dataset.EndsWith
import com.ttele.algoking.engine.dataset.NoValueOverlapWith
import com.ttele.algoking.engine.dataset.Scenario
import com.ttele.algoking.engine.dataset.SeededGenerator
import com.ttele.algoking.engine.dataset.StructureDatasets
import com.ttele.algoking.engine.event.Metrics
import com.ttele.algoking.engine.scenario.Mission
import com.ttele.algoking.engine.scenario.MissionCatalog
import com.ttele.algoking.engine.scoring.Scorer
import kotlin.random.Random

/**
 * What kind of question the challenge asks.
 *
 * The engine is shaped so more types can be added — predict the next middle,
 * spot the incorrect step, minimise comparisons — without touching the runner or
 * the renderer. Only [FIND] and [NOT_FOUND] are built.
 */
enum class ChallengeType {
    /** Binary Search: the target is in the array. */
    FIND,

    /** Binary Search: the target is absent; drive the range to empty. */
    NOT_FOUND,

    /** Bubble Sort: sort the array. */
    SORT,

    /**
     * Bubble Sort: the array is already sorted, and the learner should reach a
     * swapless pass. Teaches the termination rule most courses skip.
     */
    EARLY_EXIT,

    /**
     * Stack / Queue: run a sequence of operations correctly. There is nothing to
     * find and nothing to sort — the learner is judged on whether they reach for
     * the right end.
     */
    OPERATIONS,

    /** Linked List: walk the chain until the value turns up, or NULL proves it will not. */
    TRAVERSE,

    /** Linked List: splice a node into the right gap and give it the right NEXT. */
    LINK_INSERT,

    /** Linked List: cut a node out without leaving a broken link. */
    LINK_DELETE,

    /** Hash Map: run a sequence of PUT, GET and REMOVE, hashing every key. */
    HASH_OPERATIONS,

    /** Hash Map: a bucket already holds two keys, and one of them is wanted. */
    HASH_COLLISION,
}

/** The shapes a sorting challenge can take. Shape is the difficulty knob here. */
enum class BubbleShape { MIXED, REVERSED, NEARLY_SORTED, DUPLICATES, ALREADY_SORTED }

/**
 * Difficulty rises gradually. It is a property of the *data*, never of the rules —
 * a harder challenge is a bigger array or a less obvious target, never a stricter
 * clock or a stingier validator.
 */
enum class Difficulty(
    val size: IntRange,
    val values: IntRange,
) {
    Beginner(7..9, 2..60),
    Intermediate(11..13, 2..99),
    Advanced(14..16, 2..140),
}

/** A generated, reproducible challenge. */
data class Challenge(
    val type: ChallengeType,
    val difficulty: Difficulty,
    val seed: Long,
    val dataset: Dataset,
    /**
     * The world this challenge is set in, when it has one.
     *
     * Presentation only: the mission supplies the story, the labels and the
     * units, and the engine still searches [dataset]. Null for every lesson that
     * has not been given a story yet, which is why adding one changed no
     * algorithm.
     */
    val mission: Mission? = null,
) {
    /** Searching challenges have a target; sorting challenges do not. */
    val target: Int? get() = dataset.target
    val size: Int get() = dataset.values.size

    /** The comparison count of a perfect run on this data. */
    val optimalComparisons: Int get() = Scorer.optimalComparisons(size)
}

/**
 * Deterministic from a seed, and validated by running the real algorithm — so a
 * challenge that violates a pedagogical rule cannot be produced
 * (ARCHITECTURE.md §9).
 */
object ChallengeGenerator {

    /**
     * A Binary Search challenge, set somewhere.
     *
     * The learner is handed a sorted world and a target and left alone: nothing
     * here names Binary Search, because recognising that this *is* a Binary
     * Search problem is the thing Challenge exists to test (PRODUCT_SPEC.md §6).
     *
     * The mission rotates every round, so Practice Again is a new world rather
     * than the same array with different numbers.
     */
    fun forRound(round: Int, seed: Long): Challenge {
        val mission = MissionCatalog.forRound(round, seed)
        return Challenge(
            type = ChallengeType.FIND,
            difficulty = mission.difficulty,
            seed = seed,
            dataset = mission.dataset,
            mission = mission,
        )
    }

    /**
     * The bare, storyless generator. Kept because NOT_FOUND has no mission
     * wrapper yet — driving a range to empty needs a story about *absence*, and a
     * warehouse that simply lacks a pallet reads as a bug rather than a result.
     */
    fun plainForRound(round: Int, seed: Long): Challenge {
        val difficulty = when {
            round <= 1 -> Difficulty.Beginner
            round <= 3 -> Difficulty.Intermediate
            else -> Difficulty.Advanced
        }
        val type = if (round >= 3 && round % 3 == 0) {
            ChallengeType.NOT_FOUND
        } else {
            ChallengeType.FIND
        }
        return generate(type, difficulty, seed)
    }

    /**
     * Bubble Sort challenges. Shape matters more than size here: a learner who
     * only ever meets reversed arrays never has to decide KEEP, so the rotation
     * deliberately mixes in near-sorted data, duplicates, and one already-sorted
     * array that should end after a single swapless pass.
     */
    fun bubbleForRound(round: Int, seed: Long): Challenge {
        val difficulty = when {
            round <= 1 -> Difficulty.Beginner
            round <= 3 -> Difficulty.Intermediate
            else -> Difficulty.Advanced
        }
        val shape = BubbleShape.entries[(seed + round).mod(BubbleShape.entries.size)]
        return Challenge(
            type = if (shape == BubbleShape.ALREADY_SORTED) {
                ChallengeType.EARLY_EXIT
            } else {
                ChallengeType.SORT
            },
            difficulty = difficulty,
            seed = seed,
            dataset = drawBubble(shape, difficulty, seed),
        )
    }

    private fun drawBubble(
        shape: BubbleShape,
        difficulty: Difficulty,
        seed: Long,
    ): Dataset {
        val rng = Random(seed * 31 + shape.ordinal)
        // Sorting arrays stay short: every element costs the learner taps.
        val size = when (difficulty) {
            Difficulty.Beginner -> 4
            Difficulty.Intermediate -> 5
            Difficulty.Advanced -> 6
        }
        val pool = generateSequence { rng.nextInt(2, 40) }
            .distinct()
            .take(size)
            .toList()

        val values = when (shape) {
            BubbleShape.MIXED -> pool.shuffled(rng)
            BubbleShape.REVERSED -> pool.sortedDescending()
            BubbleShape.NEARLY_SORTED -> pool.sorted().toMutableList().also {
                val at = rng.nextInt(0, it.lastIndex)
                val tmp = it[at]; it[at] = it[at + 1]; it[at + 1] = tmp
            }

            BubbleShape.DUPLICATES -> pool.sorted().toMutableList().also {
                it[rng.nextInt(0, it.size)] = it[rng.nextInt(0, it.size)]
            }.shuffled(rng)

            BubbleShape.ALREADY_SORTED -> pool.sorted()
        }

        return Dataset(values = values, label = shape.name)
    }

    /**
     * Stack and Queue challenges.
     *
     * Nothing about the *rules* gets harder — LIFO is LIFO. What grows is the
     * number of operations the learner has to stay consistent across, so difficulty
     * is expressed as a longer script over unfamiliar numbers.
     */
    fun structureForRound(round: Int, seed: Long): Challenge {
        val difficulty = when {
            round <= 1 -> Difficulty.Beginner
            round <= 3 -> Difficulty.Intermediate
            else -> Difficulty.Advanced
        }
        val size = when (difficulty) {
            Difficulty.Beginner -> 4
            Difficulty.Intermediate -> 5
            Difficulty.Advanced -> 6
        }
        val rng = Random(seed * 37 + round)
        // Two digits, all distinct, and none of them seen in Watch or Try: the
        // learner has to track which value went in when, and a familiar number
        // would let them answer from memory instead — PRODUCT_SPEC.md §6.
        val taught = (StructureDatasets.watch.values + StructureDatasets.tryIt.values).toSet()
        val values = generateSequence { rng.nextInt(10, 99) }
            .filterNot { it in taught }
            .distinct()
            .take(size)
            .toList()
        return Challenge(
            type = ChallengeType.OPERATIONS,
            difficulty = difficulty,
            seed = seed,
            dataset = Dataset(values = values, label = "operations"),
        )
    }

    /**
     * Linked List challenges.
     *
     * One operation each, and the rotation guarantees the learner meets all three —
     * a challenge set that only ever searched would never ask them to change a link,
     * which is the part that is actually hard.
     */
    fun listForRound(round: Int, seed: Long): Challenge {
        val difficulty = when {
            round <= 1 -> Difficulty.Beginner
            round <= 3 -> Difficulty.Intermediate
            else -> Difficulty.Advanced
        }
        val size = when (difficulty) {
            Difficulty.Beginner -> 4
            Difficulty.Intermediate -> 5
            Difficulty.Advanced -> 5
        }
        val rng = Random(seed * 41 + round)

        // Ordered, spread out, and two digits: gaps wide enough that an inserted
        // value has exactly one place it can belong.
        val values = generateSequence(rng.nextInt(5, 12)) { it + rng.nextInt(5, 14) }
            .take(size)
            .toList()

        // The first challenge is always a walk: rewiring a list you cannot navigate
        // is not a harder version of the same skill, it is a different one.
        val type = if (round <= 1) {
            ChallengeType.TRAVERSE
        } else {
            LIST_TYPES[(seed + round).mod(LIST_TYPES.size)]
        }

        val target = when (type) {
            // A value that is genuinely missing, and belongs in an interior gap —
            // never at either end, where the answer is guessable.
            ChallengeType.LINK_INSERT -> {
                val gap = 1 + (seed + round).mod(values.size - 1)
                (values[gap - 1] + values[gap]) / 2
            }
            // Somewhere the learner has to walk to.
            else -> values[1 + (seed + round).mod(values.size - 1)]
        }

        return Challenge(
            type = type,
            difficulty = difficulty,
            seed = seed,
            dataset = Dataset(values = values, target = target, label = labelFor(type)),
        )
    }

    private val LIST_TYPES = listOf(
        ChallengeType.TRAVERSE,
        ChallengeType.LINK_INSERT,
        ChallengeType.LINK_DELETE,
    )

    /** The label selects the script, so the challenge type and the script agree. */
    private fun labelFor(type: ChallengeType) = when (type) {
        ChallengeType.LINK_INSERT -> "insert"
        ChallengeType.LINK_DELETE -> "delete"
        else -> "find"
    }

    /**
     * Hash Map challenges.
     *
     * Keys are drawn so that **at least two of them collide**. A generated challenge
     * where every key found an empty bucket would quietly drop the one idea the
     * learner most needs to practise, so the generator refuses to produce one.
     */
    fun hashForRound(round: Int, seed: Long): Challenge {
        val difficulty = when {
            round <= 1 -> Difficulty.Beginner
            round <= 3 -> Difficulty.Intermediate
            else -> Difficulty.Advanced
        }
        val buckets = 5
        val rng = Random(seed * 43 + round)

        // Every third challenge is the pure collision case: a map that already has a
        // shared bucket, and one lookup inside it.
        val collisionRound = round >= 2 && round % 3 == 2
        val size = if (collisionRound) 2 else if (difficulty == Difficulty.Beginner) 3 else 4

        val shared = rng.nextInt(0, buckets)
        val keys = buildList {
            // Two keys that are guaranteed to land together.
            add(shared + buckets * rng.nextInt(2, 5))
            add(shared + buckets * rng.nextInt(5, 9))
            while (size > this.size) {
                val candidate = rng.nextInt(10, 60)
                if (none { it == candidate }) add(candidate)
            }
        }.take(size)

        return Challenge(
            type = if (collisionRound) ChallengeType.HASH_COLLISION else ChallengeType.HASH_OPERATIONS,
            difficulty = difficulty,
            seed = seed,
            // The target is the *second* colliding key, so a lookup has to scan past
            // the first one rather than stopping at the head of the chain.
            dataset = Dataset(
                values = keys,
                target = keys[1],
                label = if (collisionRound) "collision" else "operations",
            ),
        )
    }

    fun generate(type: ChallengeType, difficulty: Difficulty, seed: Long): Challenge {
        require(type == ChallengeType.FIND || type == ChallengeType.NOT_FOUND) {
            "generate() draws searching challenges; use bubbleForRound() for sorting."
        }
        val scenario = when (type) {
            ChallengeType.NOT_FOUND -> Scenario.ABSENT
            else -> when (seed.mod(3)) {
                0 -> Scenario.EARLY
                1 -> Scenario.MIDDLE
                else -> Scenario.LATE
            }
        }

        val dataset = SeededGenerator(BinarySearchAlgorithm()).generate(
            DatasetSpec(
                size = difficulty.size,
                valueRange = difficulty.values,
                scenario = scenario,
                constraints = listOf(
                    EndsWith(found = type == ChallengeType.FIND),
                    ComparisonsBetween(2, 6),
                    // "Fully regenerated, no overlap" — PRODUCT_SPEC.md §6. The
                    // learner must retrieve the model, not recall the data.
                    NoValueOverlapWith(
                        BinarySearchDatasets.watch,
                        BinarySearchDatasets.tryIt,
                    ),
                ),
            ),
            seed = seed,
        )

        return Challenge(type, difficulty, seed, dataset)
    }
}

/**
 * Everything the result screen needs, recorded as the learner works.
 *
 * `decisions` counts every choice offered; `mistakes` counts the ones that were
 * refused. Neither is shown prominently during the run — the array is the hero.
 */
data class ChallengeRun(
    val challenge: Challenge,
    val decisions: Int = 0,
    val correctDecisions: Int = 0,
    val mistakes: Int = 0,
    val hintsUsed: Int = 0,
    val comparisons: Int = 0,
    val swaps: Int = 0,
    val passes: Int = 0,
    val elapsedMillis: Long = 0,
    val completed: Boolean = false,
    val targetFound: Boolean = false,
) {
    val accuracy: Float
        get() = if (decisions == 0) 1f else correctDecisions.toFloat() / decisions

    /**
     * Did the learner do exactly what the algorithm would have done?
     *
     * Note what is *not* here: swap count. The learner does not choose how many
     * swaps an array needs — the input does — so scoring it would grade the data
     * rather than the person (PRODUCT_SPEC.md §7).
     */
    val optimal: Boolean
        get() = mistakes == 0 && hintsUsed == 0

    fun toMetrics() = Metrics(
        comparisons = comparisons,
        swaps = swaps,
        passes = passes,
        steps = decisions,
        wrongDecisions = mistakes,
        hintsUsed = hintsUsed,
        elapsedMillis = elapsedMillis,
    )
}
