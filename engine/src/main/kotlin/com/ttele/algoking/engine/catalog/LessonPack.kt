package com.ttele.algoking.engine.catalog

import com.ttele.algoking.engine.algorithms.binarysearch.BinarySearchAlgorithm
import com.ttele.algoking.engine.algorithms.binarysearch.BinarySearchProjector
import com.ttele.algoking.engine.algorithms.binarysearch.BinarySearchWatchNarrator
import com.ttele.algoking.engine.algorithms.bubblesort.BubbleSortAlgorithm
import com.ttele.algoking.engine.algorithms.bubblesort.BubbleSortProjector
import com.ttele.algoking.engine.algorithms.bubblesort.BubbleSortWatchNarrator
import com.ttele.algoking.engine.challenge.Challenge
import com.ttele.algoking.engine.challenge.ChallengeGenerator
import com.ttele.algoking.engine.core.Algorithm
import com.ttele.algoking.engine.core.AlgorithmId
import com.ttele.algoking.engine.core.Dataset
import com.ttele.algoking.engine.dataset.BinarySearchDatasets
import com.ttele.algoking.engine.algorithms.hashing.HashMapAlgorithm
import com.ttele.algoking.engine.algorithms.hashing.HashMapProjector
import com.ttele.algoking.engine.algorithms.hashing.HashMapWatchNarrator
import com.ttele.algoking.engine.algorithms.insertionsort.InsertionSortAlgorithm
import com.ttele.algoking.engine.algorithms.linkedlist.LinkedListAlgorithm
import com.ttele.algoking.engine.algorithms.linkedlist.LinkedListProjector
import com.ttele.algoking.engine.algorithms.linkedlist.LinkedListWatchNarrator
import com.ttele.algoking.engine.dataset.LinkedListDatasets
import com.ttele.algoking.engine.algorithms.mergesort.MergeSortAlgorithm
import com.ttele.algoking.engine.algorithms.quicksort.QuickSortAlgorithm
import com.ttele.algoking.engine.algorithms.quicksort.QuickSortProjector
import com.ttele.algoking.engine.algorithms.quicksort.QuickSortWatchNarrator
import com.ttele.algoking.engine.algorithms.mergesort.MergeSortProjector
import com.ttele.algoking.engine.algorithms.mergesort.MergeSortWatchNarrator
import com.ttele.algoking.engine.algorithms.insertionsort.InsertionSortProjector
import com.ttele.algoking.engine.algorithms.insertionsort.InsertionSortWatchNarrator
import com.ttele.algoking.engine.algorithms.selectionsort.SelectionSortAlgorithm
import com.ttele.algoking.engine.algorithms.selectionsort.SelectionSortProjector
import com.ttele.algoking.engine.algorithms.selectionsort.SelectionSortWatchNarrator
import com.ttele.algoking.engine.algorithms.structures.LinearStructureAlgorithm
import com.ttele.algoking.engine.algorithms.structures.QueueFlavour
import com.ttele.algoking.engine.algorithms.structures.StackFlavour
import com.ttele.algoking.engine.algorithms.structures.StructureFlavour
import com.ttele.algoking.engine.algorithms.structures.StructureProjector
import com.ttele.algoking.engine.algorithms.structures.StructureWatchNarrator
import com.ttele.algoking.engine.dataset.StructureDatasets
import com.ttele.algoking.engine.dataset.BubbleSortDatasets
import com.ttele.algoking.engine.dataset.HashMapDatasets
import com.ttele.algoking.engine.dataset.InsertionSortDatasets
import com.ttele.algoking.engine.dataset.MergeSortDatasets
import com.ttele.algoking.engine.dataset.QuickSortDatasets
import com.ttele.algoking.engine.dataset.SelectionSortDatasets
import com.ttele.algoking.engine.decision.Action
import com.ttele.algoking.engine.scene.SceneProjector
import com.ttele.algoking.engine.scoring.StarFamily
import com.ttele.algoking.engine.walkthrough.WatchNarrator
import com.ttele.algoking.engine.walkthrough.WatchScript
import com.ttele.algoking.engine.walkthrough.WatchScriptBuilder

/**
 * Everything one lesson needs, in one value — ARCHITECTURE.md §3 (`catalog/`).
 *
 * This is what lets Binary Search and Bubble Sort share a single controller and a
 * single set of screens. Adding an algorithm means adding a pack: an algorithm, a
 * projector, a narrator, two authored datasets, and a star family. No new screen,
 * no new renderer, no `when (algorithm)` anywhere in `:app`.
 */
class LessonPack<S : Any, A : Action>(
    val id: AlgorithmId,
    val displayName: String,
    /** The one-line promise shown on the challenge briefing. */
    val challengeBrief: String,
    val algorithm: Algorithm<S, A>,
    val projector: SceneProjector<S>,
    private val watchNarrator: WatchNarrator<S>,
    /** Authored, not generated — the teaching example has to be perfect. */
    val watchDataset: Dataset,
    /** A different array, so Try is application rather than recall. */
    val tryDataset: Dataset,
    val starFamily: StarFamily,
    private val challengeFactory: (round: Int, seed: Long) -> Challenge,
) {
    fun watchScript(): WatchScript =
        WatchScriptBuilder(algorithm, projector, watchNarrator).build(watchDataset)

    fun challenge(round: Int, seed: Long): Challenge = challengeFactory(round, seed)
}

object AlgorithmCatalog {

    fun binarySearch() = LessonPack(
        id = AlgorithmId.BINARY_SEARCH,
        displayName = "Binary Search",
        challengeBrief = "Make the right decisions. Avoid unnecessary checks.",
        algorithm = BinarySearchAlgorithm(),
        projector = BinarySearchProjector(),
        watchNarrator = BinarySearchWatchNarrator(),
        watchDataset = BinarySearchDatasets.watch,
        tryDataset = BinarySearchDatasets.tryIt,
        starFamily = StarFamily.EFFICIENCY,
        challengeFactory = { round, seed -> ChallengeGenerator.forRound(round, seed) },
    )

    fun bubbleSort() = LessonPack(
        id = AlgorithmId.BUBBLE_SORT,
        displayName = "Bubble Sort",
        challengeBrief = "Compare neighbours. Swap when they are out of order.",
        algorithm = BubbleSortAlgorithm(),
        projector = BubbleSortProjector(),
        watchNarrator = BubbleSortWatchNarrator(),
        watchDataset = BubbleSortDatasets.watch,
        tryDataset = BubbleSortDatasets.tryIt,
        // The learner does not control how many swaps an array needs, so judging
        // efficiency here would score the input, not the person — PRODUCT_SPEC §7.
        starFamily = StarFamily.ACCURACY,
        challengeFactory = { round, seed -> ChallengeGenerator.bubbleForRound(round, seed) },
    )

    fun selectionSort() = LessonPack(
        id = AlgorithmId.SELECTION_SORT,
        displayName = "Selection Sort",
        challengeBrief = "Find the smallest value that is left, then place it.",
        algorithm = SelectionSortAlgorithm(),
        projector = SelectionSortProjector(),
        watchNarrator = SelectionSortWatchNarrator(),
        watchDataset = SelectionSortDatasets.watch,
        tryDataset = SelectionSortDatasets.tryIt,
        // The number of comparisons is fixed by the array size, not by the
        // learner — so accuracy is the only honest thing to score.
        starFamily = StarFamily.ACCURACY,
        challengeFactory = { round, seed -> ChallengeGenerator.bubbleForRound(round, seed) },
    )

    fun insertionSort() = LessonPack(
        id = AlgorithmId.INSERTION_SORT,
        displayName = "Insertion Sort",
        challengeBrief = "Shift larger values right, then drop the key into the gap.",
        algorithm = InsertionSortAlgorithm(),
        projector = InsertionSortProjector(),
        watchNarrator = InsertionSortWatchNarrator(),
        watchDataset = InsertionSortDatasets.watch,
        tryDataset = InsertionSortDatasets.tryIt,
        // How many shifts an array needs is decided by the input, not the learner.
        starFamily = StarFamily.ACCURACY,
        challengeFactory = { round, seed -> ChallengeGenerator.bubbleForRound(round, seed) },
    )

    fun mergeSort() = LessonPack(
        id = AlgorithmId.MERGE_SORT,
        displayName = "Merge Sort",
        challengeBrief = "Split it down the middle, then take the smaller front value.",
        algorithm = MergeSortAlgorithm(),
        projector = MergeSortProjector(),
        watchNarrator = MergeSortWatchNarrator(),
        watchDataset = MergeSortDatasets.watch,
        tryDataset = MergeSortDatasets.tryIt,
        // The comparison count is fixed by the input; the decisions are not.
        starFamily = StarFamily.ACCURACY,
        challengeFactory = { round, seed -> ChallengeGenerator.bubbleForRound(round, seed) },
    )

    fun quickSort() = LessonPack(
        id = AlgorithmId.QUICK_SORT,
        displayName = "Quick Sort",
        challengeBrief = "Measure every value against the pivot, then place the pivot.",
        algorithm = QuickSortAlgorithm(),
        projector = QuickSortProjector(),
        watchNarrator = QuickSortWatchNarrator(),
        watchDataset = QuickSortDatasets.watch,
        tryDataset = QuickSortDatasets.tryIt,
        // The comparison count is fixed by the pivots the data produces.
        starFamily = StarFamily.ACCURACY,
        challengeFactory = { round, seed -> ChallengeGenerator.bubbleForRound(round, seed) },
    )

    fun stack() = structure(
        flavour = StackFlavour,
        displayName = "Stack",
        challengeBrief = "Only the top is reachable. Last in, first out.",
    )

    fun queue() = structure(
        flavour = QueueFlavour,
        displayName = "Queue",
        challengeBrief = "Items join at the rear and leave from the front.",
    )

    /**
     * Both structures are the same pack with a different flavour — which is the
     * strongest statement the catalog can make that LIFO and FIFO differ by one
     * rule, not by one implementation.
     */
    private fun structure(
        flavour: StructureFlavour,
        displayName: String,
        challengeBrief: String,
    ) = LessonPack(
        id = flavour.id,
        displayName = displayName,
        challengeBrief = challengeBrief,
        algorithm = LinearStructureAlgorithm(flavour),
        projector = StructureProjector(flavour),
        watchNarrator = StructureWatchNarrator(flavour),
        watchDataset = StructureDatasets.watch,
        tryDataset = StructureDatasets.tryIt,
        // There is no cost to optimise here: the script fixes the number of
        // operations. What is worth measuring is whether the learner reached for
        // the right end every time.
        starFamily = StarFamily.ACCURACY,
        challengeFactory = { round, seed -> ChallengeGenerator.structureForRound(round, seed) },
    )

    fun linkedList() = LessonPack(
        id = AlgorithmId.LINKED_LIST,
        displayName = "Linked List",
        challengeBrief = "Walk from HEAD. Change the links, not the boxes.",
        algorithm = LinkedListAlgorithm(),
        projector = LinkedListProjector(),
        watchNarrator = LinkedListWatchNarrator(),
        watchDataset = LinkedListDatasets.watch,
        tryDataset = LinkedListDatasets.tryIt,
        // Walking the chain costs what the chain costs; the learner does not choose
        // the list. What they do choose is every link, and those are worth scoring.
        starFamily = StarFamily.ACCURACY,
        challengeFactory = { round, seed -> ChallengeGenerator.listForRound(round, seed) },
    )

    fun hashMap() = LessonPack(
        id = AlgorithmId.HASH_MAP,
        displayName = "Hash Map",
        challengeBrief = "Hash the key. Look in one bucket. Nothing else.",
        algorithm = HashMapAlgorithm(),
        projector = HashMapProjector(),
        watchNarrator = HashMapWatchNarrator(),
        watchDataset = HashMapDatasets.watch,
        tryDataset = HashMapDatasets.tryIt,
        // There is no cost to optimise: the hash decides everything. What is worth
        // scoring is whether the learner did the arithmetic and read the chain.
        starFamily = StarFamily.ACCURACY,
        challengeFactory = { round, seed -> ChallengeGenerator.hashForRound(round, seed) },
    )

    fun byId(id: AlgorithmId): LessonPack<*, *> = when (id) {
        AlgorithmId.BINARY_SEARCH -> binarySearch()
        AlgorithmId.BUBBLE_SORT -> bubbleSort()
        AlgorithmId.SELECTION_SORT -> selectionSort()
        AlgorithmId.INSERTION_SORT -> insertionSort()
        AlgorithmId.MERGE_SORT -> mergeSort()
        AlgorithmId.QUICK_SORT -> quickSort()
        AlgorithmId.STACK -> stack()
        AlgorithmId.QUEUE -> queue()
        AlgorithmId.LINKED_LIST -> linkedList()
        AlgorithmId.HASH_MAP -> hashMap()
    }
}
