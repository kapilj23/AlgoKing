package com.ttele.algoking.engine.catalog

import com.ttele.algoking.engine.algorithms.binarysearch.BinarySearchAlgorithm
import com.ttele.algoking.engine.algorithms.binarysearch.BinarySearchProjector
import com.ttele.algoking.engine.algorithms.binarysearch.BinarySearchWatchNarrator
import com.ttele.algoking.engine.algorithms.traversal.InorderRule
import com.ttele.algoking.engine.algorithms.traversal.InorderWatchNarrator
import com.ttele.algoking.engine.algorithms.traversal.PostorderRule
import com.ttele.algoking.engine.algorithms.traversal.PostorderWatchNarrator
import com.ttele.algoking.engine.algorithms.traversal.PreorderRule
import com.ttele.algoking.engine.algorithms.traversal.PreorderWatchNarrator
import com.ttele.algoking.engine.algorithms.traversal.TraversalProjector
import com.ttele.algoking.engine.algorithms.traversal.TreeTraversalAlgorithm
import com.ttele.algoking.engine.dataset.TreeDatasets
import com.ttele.algoking.engine.algorithms.avl.AvlProjector
import com.ttele.algoking.engine.algorithms.avl.AvlTreeAlgorithm
import com.ttele.algoking.engine.algorithms.avl.AvlWatchNarrator
import com.ttele.algoking.engine.dataset.AvlDatasets
import com.ttele.algoking.engine.algorithms.bst.BinarySearchTreeAlgorithm
import com.ttele.algoking.engine.algorithms.bst.BstProjector
import com.ttele.algoking.engine.algorithms.bst.BstWatchNarrator
import com.ttele.algoking.engine.dataset.BstDatasets
import com.ttele.algoking.engine.algorithms.bubblesort.BubbleSortAlgorithm
import com.ttele.algoking.engine.algorithms.bubblesort.BubbleSortProjector
import com.ttele.algoking.engine.algorithms.bubblesort.BubbleSortWatchNarrator
import com.ttele.algoking.engine.core.Algorithm
import com.ttele.algoking.engine.core.AlgorithmId
import com.ttele.algoking.engine.core.Dataset
import com.ttele.algoking.engine.dataset.BinarySearchDatasets
import com.ttele.algoking.engine.dataset.TwoPointersDatasets
import com.ttele.algoking.engine.dataset.PrefixSumDatasets
import com.ttele.algoking.engine.dataset.GraphDatasets
import com.ttele.algoking.engine.dataset.BfsDatasets
import com.ttele.algoking.engine.algorithms.graphbfs.BreadthFirstSearchAlgorithm
import com.ttele.algoking.engine.algorithms.graphbfs.BfsProjector
import com.ttele.algoking.engine.algorithms.graphbfs.BfsWatchNarrator
import com.ttele.algoking.engine.algorithms.graphdfs.DepthFirstSearchAlgorithm
import com.ttele.algoking.engine.algorithms.graphdfs.DfsProjector
import com.ttele.algoking.engine.algorithms.graphdfs.DfsWatchNarrator
import com.ttele.algoking.engine.algorithms.prefixsum.PrefixSumAlgorithm
import com.ttele.algoking.engine.algorithms.prefixsum.PrefixSumProjector
import com.ttele.algoking.engine.algorithms.prefixsum.PrefixSumWatchNarrator
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

    /**
     * The second Advanced lesson, and the first about *precomputation* rather
     * than a way of walking data: pay O(n) once, then answer any range in O(1).
     */
    fun prefixSum() = LessonPack(
        id = AlgorithmId.PREFIX_SUM,
        displayName = "Prefix Sum",
        algorithm = PrefixSumAlgorithm(),
        projector = PrefixSumProjector(),
        watchNarrator = PrefixSumWatchNarrator(),
        watchDataset = PrefixSumDatasets.watch,
        tryDataset = PrefixSumDatasets.tryIt,
    )

    /**
     * The third Advanced lesson, and the first whose data is a graph. What it
     * teaches is not the traversal but the three judgements behind it: which
     * neighbour, when to backtrack, and why some neighbours are skipped.
     */
    fun graphDfs() = LessonPack(
        id = AlgorithmId.GRAPH_DFS,
        displayName = "Graph DFS",
        algorithm = DepthFirstSearchAlgorithm(),
        projector = DfsProjector(),
        watchNarrator = DfsWatchNarrator(),
        watchDataset = GraphDatasets.watch,
        tryDataset = GraphDatasets.tryIt,
    )

    /**
     * The fourth Advanced lesson, and DFS-s pair. Same graph, same adjacency,
     * same start — a queue instead of a stack, and a different traversal falls
     * out. That contrast is the reason both exist.
     */
    fun graphBfs() = LessonPack(
        id = AlgorithmId.GRAPH_BFS,
        displayName = "Graph BFS",
        algorithm = BreadthFirstSearchAlgorithm(),
        projector = BfsProjector(),
        watchNarrator = BfsWatchNarrator(),
        watchDataset = BfsDatasets.watch,
        tryDataset = BfsDatasets.tryIt,
    )

    /**
     * The fifth Advanced lesson, and Binary Search's other half: the same
     * decision rule — smaller one way, larger the other — over a structure that
     * *stores* the order instead of relying on an array being sorted. Nothing is
     * computed; the node itself says which way to go.
     */
    fun binarySearchTree() = LessonPack(
        id = AlgorithmId.BINARY_SEARCH_TREE,
        displayName = "Binary Search Tree",
        algorithm = BinarySearchTreeAlgorithm(),
        projector = BstProjector(),
        watchNarrator = BstWatchNarrator(),
        watchDataset = BstDatasets.watch,
        tryDataset = BstDatasets.tryIt,
    )

    /**
     * The sixth Advanced lesson, and the answer to the caveat the Binary Search
     * Tree lesson has to end on: a BST is only fast while it stays bushy, and an
     * AVL tree is one that refuses to become anything else.
     */
    fun avlTree() = LessonPack(
        id = AlgorithmId.AVL_TREE,
        displayName = "AVL Tree",
        algorithm = AvlTreeAlgorithm(),
        projector = AvlProjector(),
        watchNarrator = AvlWatchNarrator(),
        watchDataset = AvlDatasets.watch,
        tryDataset = AvlDatasets.tryIt,
    )

    /**
     * The first of three traversal lessons. They are separate modules that share
     * one machine: the rule each teaches is one line in its own file
     * (`InorderRule.order`), and everything the learner sees — the datasets, the
     * script, the walkthrough, the progress, the card — is its own.
     */
    fun inorderTraversal() = LessonPack(
        id = AlgorithmId.TREE_INORDER,
        displayName = "Inorder Traversal",
        algorithm = TreeTraversalAlgorithm(InorderRule),
        projector = TraversalProjector("Inorder"),
        watchNarrator = InorderWatchNarrator(),
        watchDataset = TreeDatasets.watch,
        tryDataset = TreeDatasets.tryIt,
    )

    /**
     * The second traversal. Same machine, same tree, same gesture as Inorder —
     * and a different order out, because `PreorderRule.order` puts the visit
     * first. That is the whole comparison the three lessons exist to make.
     */
    fun preorderTraversal() = LessonPack(
        id = AlgorithmId.TREE_PREORDER,
        displayName = "Preorder Traversal",
        algorithm = TreeTraversalAlgorithm(PreorderRule),
        projector = TraversalProjector("Preorder"),
        watchNarrator = PreorderWatchNarrator(),
        watchDataset = TreeDatasets.watch,
        tryDataset = TreeDatasets.tryIt,
    )

    /**
     * The third, and the one with the most to teach: a node waits for *both* of
     * its subtrees, so every parent sits on the stack while its children come out
     * underneath it.
     */
    fun postorderTraversal() = LessonPack(
        id = AlgorithmId.TREE_POSTORDER,
        displayName = "Postorder Traversal",
        algorithm = TreeTraversalAlgorithm(PostorderRule),
        projector = TraversalProjector("Postorder"),
        watchNarrator = PostorderWatchNarrator(),
        watchDataset = TreeDatasets.watch,
        tryDataset = TreeDatasets.tryIt,
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
        AlgorithmId.PREFIX_SUM -> prefixSum()
        AlgorithmId.GRAPH_DFS -> graphDfs()
        AlgorithmId.GRAPH_BFS -> graphBfs()
        AlgorithmId.BINARY_SEARCH_TREE -> binarySearchTree()
        AlgorithmId.AVL_TREE -> avlTree()
        AlgorithmId.TREE_INORDER -> inorderTraversal()
        AlgorithmId.TREE_PREORDER -> preorderTraversal()
        AlgorithmId.TREE_POSTORDER -> postorderTraversal()
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
