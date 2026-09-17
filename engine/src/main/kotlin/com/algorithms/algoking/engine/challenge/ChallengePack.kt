package com.algorithms.algoking.engine.challenge

import com.algorithms.algoking.engine.core.AlgorithmId
import com.algorithms.algoking.engine.scoring.StarFamily

/**
 * Everything the **deferred** CHALLENGE stage needs for one algorithm.
 *
 * ### Why this is a separate pack
 *
 * Challenge is V2 (`docs/v2-challenge.md`). These three fields used to live on
 * `LessonPack`, which meant every MVP screen that touched a lesson also carried a
 * challenge factory and a star family it never used. Splitting them out is what
 * keeps the MVP spine — WATCH → TRY — honest: `LessonPack` now holds only what
 * WATCH and TRY actually read, and nothing in `:app` can reach a challenge by
 * accident.
 *
 * Nothing in the MVP flow constructs or consumes this. It is retained, compiled
 * and test-covered so that V2 is a matter of wiring a screen to
 * [AlgorithmId] → [ChallengePack], not of rebuilding the generator, the star
 * families and the mission catalogue from scratch.
 *
 * **Do not reference this from an MVP screen.** If the MVP ever needs something in
 * here, that is a signal the thing belongs on `LessonPack` instead.
 */
class ChallengePack(
    val id: AlgorithmId,
    /** The one-line promise shown on the challenge briefing. */
    val challengeBrief: String,
    /**
     * Never an efficiency formula for an algorithm whose cost the learner does not
     * control — PRODUCT_SPEC.md §7.
     */
    val starFamily: StarFamily,
    private val challengeFactory: (round: Int, seed: Long) -> Challenge,
) {
    fun challenge(round: Int, seed: Long): Challenge = challengeFactory(round, seed)
}

/**
 * The V2 challenge wiring, kept beside the generator it drives.
 *
 * This is deliberately a mirror of `AlgorithmCatalog` rather than part of it: the
 * MVP catalogue should have no reason to import anything from this package.
 */
object ChallengeCatalog {

    fun binarySearch() = ChallengePack(
        id = AlgorithmId.BINARY_SEARCH,
        challengeBrief = "Make the right decisions. Avoid unnecessary checks.",
        // The learner genuinely controls the comparison count here, so efficiency
        // is a fair thing to score.
        starFamily = StarFamily.EFFICIENCY,
        challengeFactory = { round, seed -> ChallengeGenerator.forRound(round, seed) },
    )

    fun bubbleSort() = ChallengePack(
        id = AlgorithmId.BUBBLE_SORT,
        challengeBrief = "Compare neighbours. Swap when they are out of order.",
        // The learner does not control how many swaps an array needs, so judging
        // efficiency here would score the input, not the person — PRODUCT_SPEC §7.
        starFamily = StarFamily.ACCURACY,
        challengeFactory = { round, seed -> ChallengeGenerator.bubbleForRound(round, seed) },
    )

    fun selectionSort() = ChallengePack(
        id = AlgorithmId.SELECTION_SORT,
        challengeBrief = "Find the smallest value that is left, then place it.",
        // The number of comparisons is fixed by the array size, not by the
        // learner — so accuracy is the only honest thing to score.
        starFamily = StarFamily.ACCURACY,
        challengeFactory = { round, seed -> ChallengeGenerator.bubbleForRound(round, seed) },
    )

    fun insertionSort() = ChallengePack(
        id = AlgorithmId.INSERTION_SORT,
        challengeBrief = "Shift larger values right, then drop the key into the gap.",
        // How many shifts an array needs is decided by the input, not the learner.
        starFamily = StarFamily.ACCURACY,
        challengeFactory = { round, seed -> ChallengeGenerator.bubbleForRound(round, seed) },
    )

    fun mergeSort() = ChallengePack(
        id = AlgorithmId.MERGE_SORT,
        challengeBrief = "Split it down the middle, then take the smaller front value.",
        // The comparison count is fixed by the input; the decisions are not.
        starFamily = StarFamily.ACCURACY,
        challengeFactory = { round, seed -> ChallengeGenerator.bubbleForRound(round, seed) },
    )

    fun quickSort() = ChallengePack(
        id = AlgorithmId.QUICK_SORT,
        challengeBrief = "Measure every value against the pivot, then place the pivot.",
        // The comparison count is fixed by the pivots the data produces.
        starFamily = StarFamily.ACCURACY,
        challengeFactory = { round, seed -> ChallengeGenerator.bubbleForRound(round, seed) },
    )

    fun stack() = ChallengePack(
        id = AlgorithmId.STACK,
        challengeBrief = "Only the top is reachable. Last in, first out.",
        // There is no cost to optimise here: the script fixes the number of
        // operations. What is worth measuring is whether the learner reached for
        // the right end every time.
        starFamily = StarFamily.ACCURACY,
        challengeFactory = { round, seed -> ChallengeGenerator.structureForRound(round, seed) },
    )

    fun queue() = ChallengePack(
        id = AlgorithmId.QUEUE,
        challengeBrief = "Items join at the rear and leave from the front.",
        starFamily = StarFamily.ACCURACY,
        challengeFactory = { round, seed -> ChallengeGenerator.structureForRound(round, seed) },
    )

    fun linkedList() = ChallengePack(
        id = AlgorithmId.LINKED_LIST,
        challengeBrief = "Walk from HEAD. Change the links, not the boxes.",
        // Walking the chain costs what the chain costs; the learner does not choose
        // the list. What they do choose is every link, and those are worth scoring.
        starFamily = StarFamily.ACCURACY,
        challengeFactory = { round, seed -> ChallengeGenerator.listForRound(round, seed) },
    )

    fun hashMap() = ChallengePack(
        id = AlgorithmId.HASH_MAP,
        challengeBrief = "Hash the key. Look in one bucket. Nothing else.",
        // There is no cost to optimise: the hash decides everything. What is worth
        // scoring is whether the learner did the arithmetic and read the chain.
        starFamily = StarFamily.ACCURACY,
        challengeFactory = { round, seed -> ChallengeGenerator.hashForRound(round, seed) },
    )

    /**
     * Null for a lesson whose challenge has not been authored.
     *
     * Two Pointers shipped WATCH and TRY without one: its challenge needs its own
     * generated datasets and constraints, and inventing them here to satisfy an
     * exhaustive `when` would put an untested challenge in the catalogue that V2
     * would then trust. Nullable is the honest signature — "not written yet" is a
     * real state, and V2 has to answer it rather than inherit a guess.
     */
    fun byId(id: AlgorithmId): ChallengePack? = when (id) {
        AlgorithmId.BINARY_SEARCH -> binarySearch()
        AlgorithmId.TWO_POINTERS -> null
        AlgorithmId.PREFIX_SUM -> null
        AlgorithmId.GRAPH_DFS -> null
        AlgorithmId.GRAPH_BFS -> null
        AlgorithmId.DIJKSTRA -> null
        AlgorithmId.BINARY_SEARCH_TREE -> null
        AlgorithmId.AVL_TREE -> null
        AlgorithmId.TREE_INORDER -> null
        AlgorithmId.TREE_PREORDER -> null
        AlgorithmId.TREE_POSTORDER -> null
        AlgorithmId.ZERO_ONE_KNAPSACK -> null
        AlgorithmId.FIBONACCI -> null
        // Not authored: a generated bitwise challenge needs a constraint keeping the
        // key off all-zeros and all-ones, neither of which teaches anything.
        AlgorithmId.XOR_CIPHER -> null
        // Not authored: a generated cipher challenge needs a constraint keeping the
        // message short and the shift non-trivial, and that has not been written.
        AlgorithmId.CAESAR_CIPHER -> null
        // Not authored, and this one may never be: the lesson's judgements are
        // about what hashing *guarantees* rather than about a step to execute, so
        // there is no run to generate a fresh dataset for. A V2 challenge here
        // would be a different kind of assessment, not a seeded variant of this
        // one — which is a design decision rather than a missing constraint.
        AlgorithmId.SHA_256 -> null
        // Not authored, for the reason SHA-256's is not: the judgements are about
        // what AES *is* — a block size, the shape of a round, what the last one
        // leaves out — rather than about a step to execute, and nobody runs a
        // MixColumns by hand. A seeded variant would be a different block through
        // the same fixed cipher, which changes the bytes on screen and not one of
        // the questions. If a V2 challenge exists here it is a different kind of
        // assessment, not a generated version of this lesson.
        AlgorithmId.AES -> null
        // Not authored. A generated variant would draw new primes and ask the same
        // ten questions, which changes the arithmetic on screen and none of the
        // judgements — and the numbers have to stay small enough to check by hand,
        // which leaves very little room to generate within. If a V2 challenge exists
        // here it is a different kind of assessment.
        AlgorithmId.RSA -> null
        AlgorithmId.BUBBLE_SORT -> bubbleSort()
        AlgorithmId.SELECTION_SORT -> selectionSort()
        AlgorithmId.INSERTION_SORT -> insertionSort()
        AlgorithmId.MERGE_SORT -> mergeSort()
        AlgorithmId.QUICK_SORT -> quickSort()
        // Not authored: a counting-sort challenge needs generated data whose value
        // range is small enough to stay readable and wide enough to have holes in
        // it, and that constraint has not been written. Null rather than a guess.
        AlgorithmId.COUNTING_SORT -> null
        AlgorithmId.STACK -> stack()
        AlgorithmId.QUEUE -> queue()
        AlgorithmId.LINKED_LIST -> linkedList()
        AlgorithmId.HASH_MAP -> hashMap()
    }
}
