package com.ttele.algoking.engine.catalog

import com.ttele.algoking.engine.algorithms.binarysearch.BinarySearchAlgorithm
import com.ttele.algoking.engine.algorithms.binarysearch.BinarySearchProjector
import com.ttele.algoking.engine.algorithms.binarysearch.BinarySearchWatchNarrator
import com.ttele.algoking.engine.algorithms.bubblesort.BubbleSortAlgorithm
import com.ttele.algoking.engine.algorithms.bubblesort.BubbleSortProjector
import com.ttele.algoking.engine.algorithms.bubblesort.BubbleSortWatchNarrator
import com.ttele.algoking.engine.core.Algorithm
import com.ttele.algoking.engine.core.AlgorithmId
import com.ttele.algoking.engine.core.Dataset
import com.ttele.algoking.engine.dataset.BinarySearchDatasets
import com.ttele.algoking.engine.dataset.TwoPointersDatasets
import com.ttele.algoking.engine.algorithms.twopointers.TwoPointersAlgorithm
import com.ttele.algoking.engine.algorithms.twopointers.TwoPointersProjector
import com.ttele.algoking.engine.algorithms.twopointers.TwoPointersWatchNarrator
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
import com.ttele.algoking.engine.walkthrough.WatchNarrator
import com.ttele.algoking.engine.walkthrough.WatchScript
import com.ttele.algoking.engine.walkthrough.WatchScriptBuilder

/**
 * Everything one lesson needs, in one value — ARCHITECTURE.md §3 (`catalog/`).
 *
 * This is what lets Binary Search and Bubble Sort share a single controller and a
 * single set of screens. Adding an algorithm means adding a pack: an algorithm, a
 * projector, a narrator, and the two authored datasets. No new screen,
 * no new renderer, no `when (algorithm)` anywhere in `:app`.
 */
class LessonPack<S : Any, A : Action>(
    val id: AlgorithmId,
    val displayName: String,
    val algorithm: Algorithm<S, A>,
    val projector: SceneProjector<S>,
    private val watchNarrator: WatchNarrator<S>,
    /** Authored, not generated — the teaching example has to be perfect. */
    val watchDataset: Dataset,
    /** A different array, so Try is application rather than recall. */
    val tryDataset: Dataset,
) {
    fun watchScript(): WatchScript =
        WatchScriptBuilder(algorithm, projector, watchNarrator).build(watchDataset)
}

object AlgorithmCatalog {

    fun binarySearch() = LessonPack(
        id = AlgorithmId.BINARY_SEARCH,
        displayName = "Binary Search",
        algorithm = BinarySearchAlgorithm(),
        projector = BinarySearchProjector(),
        watchNarrator = BinarySearchWatchNarrator(),
        watchDataset = BinarySearchDatasets.watch,
        tryDataset = BinarySearchDatasets.tryIt,
    )

    /**
     * The first Advanced lesson. It is a *technique* rather than a named routine,
     * and the thing being taught is the reason a pointer moves — never which
     * button to press.
     */
    fun twoPointers() = LessonPack(
        id = AlgorithmId.TWO_POINTERS,
        displayName = "Two Pointers",
        algorithm = TwoPointersAlgorithm(),
        projector = TwoPointersProjector(),
        watchNarrator = TwoPointersWatchNarrator(),
        watchDataset = TwoPointersDatasets.watch,
        tryDataset = TwoPointersDatasets.tryIt,
    )

    fun bubbleSort() = LessonPack(
        id = AlgorithmId.BUBBLE_SORT,
        displayName = "Bubble Sort",
        algorithm = BubbleSortAlgorithm(),
        projector = BubbleSortProjector(),
        watchNarrator = BubbleSortWatchNarrator(),
        watchDataset = BubbleSortDatasets.watch,
        tryDataset = BubbleSortDatasets.tryIt,
    )

    fun selectionSort() = LessonPack(
        id = AlgorithmId.SELECTION_SORT,
        displayName = "Selection Sort",
        algorithm = SelectionSortAlgorithm(),
        projector = SelectionSortProjector(),
        watchNarrator = SelectionSortWatchNarrator(),
        watchDataset = SelectionSortDatasets.watch,
        tryDataset = SelectionSortDatasets.tryIt,
    )

    fun insertionSort() = LessonPack(
        id = AlgorithmId.INSERTION_SORT,
        displayName = "Insertion Sort",
        algorithm = InsertionSortAlgorithm(),
        projector = InsertionSortProjector(),
        watchNarrator = InsertionSortWatchNarrator(),
        watchDataset = InsertionSortDatasets.watch,
        tryDataset = InsertionSortDatasets.tryIt,
    )

    fun mergeSort() = LessonPack(
        id = AlgorithmId.MERGE_SORT,
        displayName = "Merge Sort",
        algorithm = MergeSortAlgorithm(),
        projector = MergeSortProjector(),
        watchNarrator = MergeSortWatchNarrator(),
        watchDataset = MergeSortDatasets.watch,
        tryDataset = MergeSortDatasets.tryIt,
    )

    fun quickSort() = LessonPack(
        id = AlgorithmId.QUICK_SORT,
        displayName = "Quick Sort",
        algorithm = QuickSortAlgorithm(),
        projector = QuickSortProjector(),
        watchNarrator = QuickSortWatchNarrator(),
        watchDataset = QuickSortDatasets.watch,
        tryDataset = QuickSortDatasets.tryIt,
    )

    fun stack() = structure(
        flavour = StackFlavour,
        displayName = "Stack",
    )

    fun queue() = structure(
        flavour = QueueFlavour,
        displayName = "Queue",
    )

    /**
     * Both structures are the same pack with a different flavour — which is the
     * strongest statement the catalog can make that LIFO and FIFO differ by one
     * rule, not by one implementation.
     */
    private fun structure(
        flavour: StructureFlavour,
        displayName: String,
    ) = LessonPack(
        id = flavour.id,
        displayName = displayName,
        algorithm = LinearStructureAlgorithm(flavour),
        projector = StructureProjector(flavour),
        watchNarrator = StructureWatchNarrator(flavour),
        watchDataset = StructureDatasets.watch,
        tryDataset = StructureDatasets.tryIt,
    )

    fun linkedList() = LessonPack(
        id = AlgorithmId.LINKED_LIST,
        displayName = "Linked List",
        algorithm = LinkedListAlgorithm(),
        projector = LinkedListProjector(),
        watchNarrator = LinkedListWatchNarrator(),
        watchDataset = LinkedListDatasets.watch,
        tryDataset = LinkedListDatasets.tryIt,
    )

    fun hashMap() = LessonPack(
        id = AlgorithmId.HASH_MAP,
        displayName = "Hash Map",
        algorithm = HashMapAlgorithm(),
        projector = HashMapProjector(),
        watchNarrator = HashMapWatchNarrator(),
        watchDataset = HashMapDatasets.watch,
        tryDataset = HashMapDatasets.tryIt,
    )

    fun byId(id: AlgorithmId): LessonPack<*, *> = when (id) {
        AlgorithmId.BINARY_SEARCH -> binarySearch()
        AlgorithmId.TWO_POINTERS -> twoPointers()
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
