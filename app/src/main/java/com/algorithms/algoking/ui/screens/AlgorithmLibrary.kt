package com.algorithms.algoking.ui.screens

import androidx.compose.ui.graphics.vector.ImageVector
import com.algorithms.algoking.engine.core.AlgorithmId
import com.algorithms.algoking.engine.progress.AlgorithmProgress
import com.algorithms.algoking.ui.components.AlgorithmStatus
import com.algorithms.algoking.ui.icons.AlgoIcons
import com.algorithms.algoking.ui.theme.AlgoAccent

/**
 * The Home library content — PRODUCT_SPEC.md §10 and §15.
 *
 * Each entry owns one accent hue, and that hue is the only colour that varies
 * between rows: the tile gradient, the badge and the ring all read from it.
 *
 * An entry carries **no progress**. Progress belongs to the learner, not to the
 * catalogue, and it is read from `ProgressRepository` at render time — see
 * [statusFor].
 */
data class AlgorithmEntry(
    val id: AlgorithmId,
    val title: String,
    val description: String,
    val category: String,
    val accent: AlgoAccent,
    val glyph: ImageVector,
)

/**
 * Only categories that actually contain a lesson. A chip that filters to an empty
 * list is a dead end the learner has no way to recover from.
 */
val algorithmCategories = listOf(
    "All",
    "Searching",
    "Sorting",
    "Structures",
    // Protecting or fingerprinting a message rather than finding or ordering one.
    // A category like any other, so the chip row, the card badge and the filter all
    // work with no new mechanism — and free, because access follows the category
    // and only the Advanced shelf is Pro (ADR-041).
    //
    // **It is "Cryptography" rather than "Encryption" because SHA-256 is not
    // encryption** (ADR-048). The shelf holds two ciphers and one hash function,
    // and a chip reading "Encryption" would file the hash lesson under the exact
    // word that lesson exists to correct — the app contradicting itself on the
    // Home screen, in a badge, before the learner has opened anything.
    "Cryptography",
    // Techniques rather than named routines, and the first place the library
    // gets harder. It is a category like any other so the chip row, the card
    // badge and the filter all work with no new mechanism — PRODUCT_SPEC.md §12.
    "Advanced",
)

val algorithmLibrary = listOf(
    AlgorithmEntry(
        id = AlgorithmId.BINARY_SEARCH,
        title = "Binary Search",
        description = "Divide and conquer to find the target in a sorted array.",
        category = "Searching",
        accent = AlgoAccent.Green,
        glyph = AlgoIcons.TileSearch,
    ),
    AlgorithmEntry(
        id = AlgorithmId.TWO_POINTERS,
        title = "Two Pointers",
        description = "Walk in from both ends of a sorted array.",
        category = "Advanced",
        accent = AlgoAccent.Blue,
        glyph = AlgoIcons.TileNodes,
    ),
    AlgorithmEntry(
        id = AlgorithmId.PREFIX_SUM,
        title = "Prefix Sum",
        description = "Precompute running totals, then answer any range instantly.",
        category = "Advanced",
        accent = AlgoAccent.Green,
        glyph = AlgoIcons.TileBars,
    ),
    AlgorithmEntry(
        id = AlgorithmId.GRAPH_DFS,
        title = "Graph DFS",
        description = "Go as deep as you can, then back up and take the next branch.",
        category = "Advanced",
        accent = AlgoAccent.Pink,
        glyph = AlgoIcons.TileNodes,
    ),
    AlgorithmEntry(
        id = AlgorithmId.GRAPH_BFS,
        title = "Graph BFS",
        description = "Explore level by level, driven by a queue.",
        category = "Advanced",
        accent = AlgoAccent.Orange,
        glyph = AlgoIcons.TileNodes,
    ),
    AlgorithmEntry(
        id = AlgorithmId.DIJKSTRA,
        title = "Dijkstra",
        description = "Cheapest route first, one distance at a time.",
        category = "Advanced",
        accent = AlgoAccent.Violet,
        glyph = AlgoIcons.TileNodes,
    ),
    AlgorithmEntry(
        id = AlgorithmId.BINARY_SEARCH_TREE,
        title = "Binary Search Tree",
        description = "One comparison per node, and a whole subtree drops out.",
        category = "Advanced",
        accent = AlgoAccent.Violet,
        glyph = AlgoIcons.TileNodes,
    ),
    AlgorithmEntry(
        id = AlgorithmId.AVL_TREE,
        title = "AVL Tree",
        description = "A search tree that rotates itself back into shape.",
        category = "Advanced",
        accent = AlgoAccent.Green,
        glyph = AlgoIcons.TileNodes,
    ),
    AlgorithmEntry(
        id = AlgorithmId.TREE_INORDER,
        title = "Binary Tree — Inorder",
        description = "Left subtree, then the node, then the right.",
        category = "Advanced",
        accent = AlgoAccent.Blue,
        glyph = AlgoIcons.TileNodes,
    ),
    AlgorithmEntry(
        id = AlgorithmId.TREE_PREORDER,
        title = "Binary Tree — Preorder",
        description = "The node first, then its left and right subtrees.",
        category = "Advanced",
        accent = AlgoAccent.Orange,
        glyph = AlgoIcons.TileNodes,
    ),
    AlgorithmEntry(
        id = AlgorithmId.TREE_POSTORDER,
        title = "Binary Tree — Postorder",
        description = "Both subtrees first. The node comes last.",
        category = "Advanced",
        accent = AlgoAccent.Pink,
        glyph = AlgoIcons.TileNodes,
    ),
    // The two dynamic-programming lessons, gentlest first. Fibonacci is the one
    // that argues DP is worth having at all — one axis, one rule, and a naive
    // recursion whose cost the learner watches — so it comes before the knapsack
    // table, which assumes that argument is already won.
    //
    // Both are filed under Advanced, which is what makes them Pro: there is no
    // flag to set, and nothing in the billing layer changed to add one (ADR-041).
    AlgorithmEntry(
        id = AlgorithmId.FIBONACCI,
        title = "Fibonacci",
        description = "Solve each smaller problem once, and never again.",
        category = "Advanced",
        accent = AlgoAccent.Violet,
        glyph = AlgoIcons.TileBars,
    ),
    AlgorithmEntry(
        id = AlgorithmId.ZERO_ONE_KNAPSACK,
        title = "0/1 Knapsack",
        description = "Take it or leave it, and never solve the same bag twice.",
        category = "Advanced",
        accent = AlgoAccent.Orange,
        glyph = AlgoIcons.TileBars,
    ),
    AlgorithmEntry(
        id = AlgorithmId.BUBBLE_SORT,
        title = "Bubble Sort",
        description = "Compare neighbours and swap the ones out of order.",
        category = "Sorting",
        accent = AlgoAccent.Violet,
        glyph = AlgoIcons.TileDots,
    ),
    AlgorithmEntry(
        id = AlgorithmId.SELECTION_SORT,
        title = "Selection Sort",
        description = "Find the smallest value left and place it at the front.",
        category = "Sorting",
        accent = AlgoAccent.Orange,
        glyph = AlgoIcons.TileDots,
    ),
    AlgorithmEntry(
        id = AlgorithmId.INSERTION_SORT,
        title = "Insertion Sort",
        description = "Shift larger values right and drop each value into place.",
        category = "Sorting",
        accent = AlgoAccent.Pink,
        glyph = AlgoIcons.TileBars,
    ),
    AlgorithmEntry(
        id = AlgorithmId.MERGE_SORT,
        title = "Merge Sort",
        description = "Divide the array, then merge the sorted pieces back together.",
        category = "Sorting",
        accent = AlgoAccent.Blue,
        glyph = AlgoIcons.TileNodes,
    ),
    AlgorithmEntry(
        id = AlgorithmId.QUICK_SORT,
        title = "Quick Sort",
        description = "Pick a pivot, partition around it, repeat on each side.",
        category = "Sorting",
        accent = AlgoAccent.Green,
        glyph = AlgoIcons.TileNodes,
    ),
    AlgorithmEntry(
        id = AlgorithmId.COUNTING_SORT,
        title = "Counting Sort",
        description = "Count each value, then rebuild the array in order.",
        category = "Sorting",
        accent = AlgoAccent.Violet,
        glyph = AlgoIcons.TileBars,
    ),
    AlgorithmEntry(
        id = AlgorithmId.STACK,
        title = "Stack",
        description = "A pile you can only touch from the top. Last in, first out.",
        category = "Structures",
        accent = AlgoAccent.Violet,
        glyph = AlgoIcons.TileBars,
    ),
    AlgorithmEntry(
        id = AlgorithmId.QUEUE,
        title = "Queue",
        description = "A line you join at the back and leave from the front.",
        category = "Structures",
        accent = AlgoAccent.Orange,
        glyph = AlgoIcons.TileDots,
    ),
    AlgorithmEntry(
        id = AlgorithmId.LINKED_LIST,
        title = "Linked List",
        description = "A chain of nodes. Each one points to the next.",
        category = "Structures",
        accent = AlgoAccent.Blue,
        glyph = AlgoIcons.TileNodes,
    ),
    AlgorithmEntry(
        id = AlgorithmId.HASH_MAP,
        title = "Hash Map",
        description = "The key calculates where its value lives.",
        category = "Structures",
        accent = AlgoAccent.Pink,
        glyph = AlgoIcons.TileBars,
    ),
    // The three Cryptography lessons, gentlest first. All free, like everything
    // outside the Advanced shelf — the category *is* the access rule, so there is
    // no flag here saying so (ADR-032, ADR-041).
    AlgorithmEntry(
        id = AlgorithmId.CAESAR_CIPHER,
        title = "Caesar Cipher",
        description = "Shift every letter, and let the alphabet wrap around.",
        category = "Cryptography",
        accent = AlgoAccent.Orange,
        glyph = AlgoIcons.TileDots,
    ),
    AlgorithmEntry(
        id = AlgorithmId.XOR_CIPHER,
        title = "XOR Cipher",
        description = "One bitwise rule — and the same key undoes it.",
        category = "Cryptography",
        accent = AlgoAccent.Blue,
        glyph = AlgoIcons.TileBars,
    ),
    // Third on the shelf and the one that is **not a cipher**. It comes after both
    // of them on purpose: "there is no way back" only lands as a distinction once
    // the learner has watched two messages be turned into something else and then
    // turned back again (ADR-048).
    AlgorithmEntry(
        id = AlgorithmId.SHA_256,
        title = "SHA-256 Hashing",
        description = "Any input, a 256-bit fingerprint — and no way back.",
        category = "Cryptography",
        accent = AlgoAccent.Green,
        glyph = AlgoIcons.TileBars,
    ),
    // Last on the shelf, and the only lesson on it that is Pro (ADR-049). It comes
    // after all three on purpose: Caesar and XOR each hide a message with one
    // operation the learner performs by hand, SHA-256 refuses to give one back, and
    // AES is what a real system actually uses — which only reads as the payoff once
    // the three simpler ideas are in place.
    //
    // Its category is "Cryptography" because that is what it *is*; being Pro is
    // decided in `ProAccess`, not by a flag here and not by filing it on a shelf
    // that would put the wrong word on its card (ADR-032, ADR-041).
    AlgorithmEntry(
        id = AlgorithmId.AES,
        title = "AES",
        description = "Four steps a round, ten rounds — and the last one is different.",
        category = "Cryptography",
        accent = AlgoAccent.Violet,
        glyph = AlgoIcons.TileNodes,
    ),
    // Last on the shelf, and the second Pro lesson on it (ADR-050). It comes after
    // AES on purpose: every cipher before it shares one key between both sides, and
    // "how do two strangers agree on that key?" is a question a learner only feels
    // once they have met four algorithms that cannot answer it.
    //
    // Its category is "Cryptography" because that is what it is; being Pro is
    // decided in `ProAccess`, not by a flag here (ADR-032, ADR-041, ADR-049).
    AlgorithmEntry(
        id = AlgorithmId.RSA,
        title = "RSA",
        description = "Two keys built from two primes — and only one is a secret.",
        category = "Cryptography",
        accent = AlgoAccent.Pink,
        glyph = AlgoIcons.TileNodes,
    ),
)

/**
 * Turns learning progress into the card's right-hand slot.
 *
 * The label names the *stage boundary* the learner is standing on, so the ring and
 * the words say the same thing: 50 % reads as "Watch done", not as a number with no
 * meaning attached to it. The MVP has two stages, so the boundary is 50 %.
 */
fun statusFor(progress: AlgorithmProgress): AlgorithmStatus = when {
    progress.finished -> AlgorithmStatus.Completed()
    progress.tryCompleted -> AlgorithmStatus.InProgress(progress.percent, "Try done")
    progress.watchCompleted -> AlgorithmStatus.InProgress(progress.percent, "Watch done")
    progress.started -> AlgorithmStatus.InProgress(progress.percent)
    else -> AlgorithmStatus.InProgress(0, "Not started")
}
