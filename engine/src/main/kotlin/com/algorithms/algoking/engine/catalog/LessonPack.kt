package com.algorithms.algoking.engine.catalog

import com.algorithms.algoking.engine.algorithms.binarysearch.BinarySearchAlgorithm
import com.algorithms.algoking.engine.algorithms.binarysearch.BinarySearchProjector
import com.algorithms.algoking.engine.algorithms.binarysearch.BinarySearchWatchNarrator
import com.algorithms.algoking.engine.algorithms.xor.XorCipherAlgorithm
import com.algorithms.algoking.engine.algorithms.xor.XorProjector
import com.algorithms.algoking.engine.algorithms.xor.XorWatchNarrator
import com.algorithms.algoking.engine.dataset.XorDatasets
import com.algorithms.algoking.engine.algorithms.caesar.CaesarCipherAlgorithm
import com.algorithms.algoking.engine.algorithms.caesar.CaesarProjector
import com.algorithms.algoking.engine.algorithms.caesar.CaesarWatchNarrator
import com.algorithms.algoking.engine.dataset.CaesarDatasets
import com.algorithms.algoking.engine.algorithms.sha256.Sha256HashingAlgorithm
import com.algorithms.algoking.engine.algorithms.sha256.Sha256Projector
import com.algorithms.algoking.engine.algorithms.sha256.Sha256WatchNarrator
import com.algorithms.algoking.engine.dataset.Sha256Datasets
import com.algorithms.algoking.engine.algorithms.fibonacci.FibonacciAlgorithm
import com.algorithms.algoking.engine.algorithms.fibonacci.FibonacciProjector
import com.algorithms.algoking.engine.algorithms.fibonacci.FibonacciWatchNarrator
import com.algorithms.algoking.engine.dataset.FibonacciDatasets
import com.algorithms.algoking.engine.algorithms.knapsack.KnapsackAlgorithm
import com.algorithms.algoking.engine.algorithms.knapsack.KnapsackProjector
import com.algorithms.algoking.engine.algorithms.knapsack.KnapsackWatchNarrator
import com.algorithms.algoking.engine.dataset.KnapsackDatasets
import com.algorithms.algoking.engine.algorithms.dijkstra.DijkstraAlgorithm
import com.algorithms.algoking.engine.algorithms.dijkstra.DijkstraProjector
import com.algorithms.algoking.engine.algorithms.dijkstra.DijkstraWatchNarrator
import com.algorithms.algoking.engine.dataset.DijkstraDatasets
import com.algorithms.algoking.engine.algorithms.traversal.InorderRule
import com.algorithms.algoking.engine.algorithms.traversal.InorderWatchNarrator
import com.algorithms.algoking.engine.algorithms.traversal.PostorderRule
import com.algorithms.algoking.engine.algorithms.traversal.PostorderWatchNarrator
import com.algorithms.algoking.engine.algorithms.traversal.PreorderRule
import com.algorithms.algoking.engine.algorithms.traversal.PreorderWatchNarrator
import com.algorithms.algoking.engine.algorithms.traversal.TraversalProjector
import com.algorithms.algoking.engine.algorithms.traversal.TreeTraversalAlgorithm
import com.algorithms.algoking.engine.dataset.TreeDatasets
import com.algorithms.algoking.engine.algorithms.avl.AvlProjector
import com.algorithms.algoking.engine.algorithms.avl.AvlTreeAlgorithm
import com.algorithms.algoking.engine.algorithms.avl.AvlWatchNarrator
import com.algorithms.algoking.engine.dataset.AvlDatasets
import com.algorithms.algoking.engine.algorithms.bst.BinarySearchTreeAlgorithm
import com.algorithms.algoking.engine.algorithms.bst.BstProjector
import com.algorithms.algoking.engine.algorithms.bst.BstWatchNarrator
import com.algorithms.algoking.engine.dataset.BstDatasets
import com.algorithms.algoking.engine.algorithms.bubblesort.BubbleSortAlgorithm
import com.algorithms.algoking.engine.algorithms.countingsort.CountingSortAlgorithm
import com.algorithms.algoking.engine.algorithms.countingsort.CountingSortProjector
import com.algorithms.algoking.engine.algorithms.countingsort.CountingSortWatchNarrator
import com.algorithms.algoking.engine.dataset.CountingSortDatasets
import com.algorithms.algoking.engine.algorithms.bubblesort.BubbleSortProjector
import com.algorithms.algoking.engine.algorithms.bubblesort.BubbleSortWatchNarrator
import com.algorithms.algoking.engine.core.Algorithm
import com.algorithms.algoking.engine.core.AlgorithmId
import com.algorithms.algoking.engine.core.Dataset
import com.algorithms.algoking.engine.dataset.BinarySearchDatasets
import com.algorithms.algoking.engine.dataset.TwoPointersDatasets
import com.algorithms.algoking.engine.dataset.PrefixSumDatasets
import com.algorithms.algoking.engine.dataset.GraphDatasets
import com.algorithms.algoking.engine.dataset.BfsDatasets
import com.algorithms.algoking.engine.algorithms.graphbfs.BreadthFirstSearchAlgorithm
import com.algorithms.algoking.engine.algorithms.graphbfs.BfsProjector
import com.algorithms.algoking.engine.algorithms.graphbfs.BfsWatchNarrator
import com.algorithms.algoking.engine.algorithms.graphdfs.DepthFirstSearchAlgorithm
import com.algorithms.algoking.engine.algorithms.graphdfs.DfsProjector
import com.algorithms.algoking.engine.algorithms.graphdfs.DfsWatchNarrator
import com.algorithms.algoking.engine.algorithms.prefixsum.PrefixSumAlgorithm
import com.algorithms.algoking.engine.algorithms.prefixsum.PrefixSumProjector
import com.algorithms.algoking.engine.algorithms.prefixsum.PrefixSumWatchNarrator
import com.algorithms.algoking.engine.algorithms.twopointers.TwoPointersAlgorithm
import com.algorithms.algoking.engine.algorithms.twopointers.TwoPointersProjector
import com.algorithms.algoking.engine.algorithms.twopointers.TwoPointersWatchNarrator
import com.algorithms.algoking.engine.algorithms.hashing.HashMapAlgorithm
import com.algorithms.algoking.engine.algorithms.hashing.HashMapProjector
import com.algorithms.algoking.engine.algorithms.hashing.HashMapWatchNarrator
import com.algorithms.algoking.engine.algorithms.insertionsort.InsertionSortAlgorithm
import com.algorithms.algoking.engine.algorithms.linkedlist.LinkedListAlgorithm
import com.algorithms.algoking.engine.algorithms.linkedlist.LinkedListProjector
import com.algorithms.algoking.engine.algorithms.linkedlist.LinkedListWatchNarrator
import com.algorithms.algoking.engine.dataset.LinkedListDatasets
import com.algorithms.algoking.engine.algorithms.mergesort.MergeSortAlgorithm
import com.algorithms.algoking.engine.algorithms.quicksort.QuickSortAlgorithm
import com.algorithms.algoking.engine.algorithms.quicksort.QuickSortProjector
import com.algorithms.algoking.engine.algorithms.quicksort.QuickSortWatchNarrator
import com.algorithms.algoking.engine.algorithms.mergesort.MergeSortProjector
import com.algorithms.algoking.engine.algorithms.mergesort.MergeSortWatchNarrator
import com.algorithms.algoking.engine.algorithms.insertionsort.InsertionSortProjector
import com.algorithms.algoking.engine.algorithms.insertionsort.InsertionSortWatchNarrator
import com.algorithms.algoking.engine.algorithms.selectionsort.SelectionSortAlgorithm
import com.algorithms.algoking.engine.algorithms.selectionsort.SelectionSortProjector
import com.algorithms.algoking.engine.algorithms.selectionsort.SelectionSortWatchNarrator
import com.algorithms.algoking.engine.algorithms.structures.LinearStructureAlgorithm
import com.algorithms.algoking.engine.algorithms.structures.QueueFlavour
import com.algorithms.algoking.engine.algorithms.structures.StackFlavour
import com.algorithms.algoking.engine.algorithms.structures.StructureFlavour
import com.algorithms.algoking.engine.algorithms.structures.StructureProjector
import com.algorithms.algoking.engine.algorithms.structures.StructureWatchNarrator
import com.algorithms.algoking.engine.dataset.StructureDatasets
import com.algorithms.algoking.engine.dataset.BubbleSortDatasets
import com.algorithms.algoking.engine.dataset.HashMapDatasets
import com.algorithms.algoking.engine.dataset.InsertionSortDatasets
import com.algorithms.algoking.engine.dataset.MergeSortDatasets
import com.algorithms.algoking.engine.dataset.QuickSortDatasets
import com.algorithms.algoking.engine.dataset.SelectionSortDatasets
import com.algorithms.algoking.engine.decision.Action
import com.algorithms.algoking.engine.scene.SceneProjector
import com.algorithms.algoking.engine.walkthrough.WatchNarrator
import com.algorithms.algoking.engine.walkthrough.WatchScript
import com.algorithms.algoking.engine.walkthrough.WatchScriptBuilder

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

    /**
     * The third graph lesson, and the first that is a **search** rather than a
     * traversal: DFS goes deep, BFS goes level by level, Dijkstra goes by
     * distance — which on a graph where every edge costs 1 is the same thing BFS
     * does, and the weights are the entire reason a different algorithm exists.
     */
    fun dijkstra() = LessonPack(
        id = AlgorithmId.DIJKSTRA,
        displayName = "Dijkstra",
        algorithm = DijkstraAlgorithm(),
        projector = DijkstraProjector(),
        watchNarrator = DijkstraWatchNarrator(),
        watchDataset = DijkstraDatasets.watch,
        tryDataset = DijkstraDatasets.tryIt,
    )

    /**
     * The first dynamic-programming lesson. Every cell of the table is a smaller
     * bag, solved once, and asks one question — take this item, or leave it? —
     * by reading two cells from the row above.
     */
    fun zeroOneKnapsack() = LessonPack(
        id = AlgorithmId.ZERO_ONE_KNAPSACK,
        displayName = "0/1 Knapsack",
        algorithm = KnapsackAlgorithm(),
        projector = KnapsackProjector(),
        watchNarrator = KnapsackWatchNarrator(),
        watchDataset = KnapsackDatasets.watch,
        tryDataset = KnapsackDatasets.tryIt,
    )

    /**
     * The second dynamic-programming lesson, and the one that says why DP exists.
     * Knapsack shows a table being *used*; this one shows what it costs not to
     * have one — the same recurrence run naively makes 67 calls to produce nine
     * numbers, and the table makes nine.
     */
    fun fibonacci() = LessonPack(
        id = AlgorithmId.FIBONACCI,
        displayName = "Fibonacci",
        algorithm = FibonacciAlgorithm(),
        projector = FibonacciProjector(),
        watchNarrator = FibonacciWatchNarrator(),
        watchDataset = FibonacciDatasets.watch,
        tryDataset = FibonacciDatasets.tryIt,
    )

    /**
     * One operation, and the fact that applying it twice undoes it. The smallest
     * complete idea in the library, and the only lesson whose data is bits.
     */
    fun xorCipher() = LessonPack(
        id = AlgorithmId.XOR_CIPHER,
        displayName = "XOR Cipher",
        algorithm = XorCipherAlgorithm(),
        projector = XorProjector(),
        watchNarrator = XorWatchNarrator(),
        watchDataset = XorDatasets.watch,
        tryDataset = XorDatasets.tryIt,
    )

    /**
     * The first lesson whose data is text rather than numbers, and the app's
     * smallest complete idea: every letter moves a fixed number of places along an
     * alphabet that is a ring rather than a line.
     */
    fun caesarCipher() = LessonPack(
        id = AlgorithmId.CAESAR_CIPHER,
        displayName = "Caesar Cipher",
        algorithm = CaesarCipherAlgorithm(),
        projector = CaesarProjector(),
        watchNarrator = CaesarWatchNarrator(),
        watchDataset = CaesarDatasets.watch,
        tryDataset = CaesarDatasets.tryIt,
    )

    /**
     * The third Cryptography lesson, and the one that is **not a cipher**.
     *
     * Caesar and XOR both hide a message and both give it back; this one gives
     * nothing back, and making that difference land is half of why it exists. The
     * app owns the hashing — nobody runs 64 compression rounds by hand — and the
     * learner owns the five judgements about what a hash guarantees, each asked
     * with the digests that answer it already on screen.
     */
    fun sha256() = LessonPack(
        id = AlgorithmId.SHA_256,
        displayName = "SHA-256 Hashing",
        algorithm = Sha256HashingAlgorithm(),
        projector = Sha256Projector(),
        watchNarrator = Sha256WatchNarrator(),
        watchDataset = Sha256Datasets.watch,
        tryDataset = Sha256Datasets.tryIt,
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

    fun countingSort() = LessonPack(
        id = AlgorithmId.COUNTING_SORT,
        displayName = "Counting Sort",
        algorithm = CountingSortAlgorithm(),
        projector = CountingSortProjector(),
        watchNarrator = CountingSortWatchNarrator(),
        watchDataset = CountingSortDatasets.watch,
        tryDataset = CountingSortDatasets.tryIt,
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
        AlgorithmId.DIJKSTRA -> dijkstra()
        AlgorithmId.BINARY_SEARCH_TREE -> binarySearchTree()
        AlgorithmId.AVL_TREE -> avlTree()
        AlgorithmId.TREE_INORDER -> inorderTraversal()
        AlgorithmId.TREE_PREORDER -> preorderTraversal()
        AlgorithmId.TREE_POSTORDER -> postorderTraversal()
        AlgorithmId.ZERO_ONE_KNAPSACK -> zeroOneKnapsack()
        AlgorithmId.FIBONACCI -> fibonacci()
        AlgorithmId.XOR_CIPHER -> xorCipher()
        AlgorithmId.CAESAR_CIPHER -> caesarCipher()
        AlgorithmId.SHA_256 -> sha256()
        AlgorithmId.BUBBLE_SORT -> bubbleSort()
        AlgorithmId.SELECTION_SORT -> selectionSort()
        AlgorithmId.INSERTION_SORT -> insertionSort()
        AlgorithmId.MERGE_SORT -> mergeSort()
        AlgorithmId.QUICK_SORT -> quickSort()
        AlgorithmId.COUNTING_SORT -> countingSort()
        AlgorithmId.STACK -> stack()
        AlgorithmId.QUEUE -> queue()
        AlgorithmId.LINKED_LIST -> linkedList()
        AlgorithmId.HASH_MAP -> hashMap()
    }
}
