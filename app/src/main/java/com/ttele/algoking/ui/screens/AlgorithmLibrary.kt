package com.ttele.algoking.ui.screens

import androidx.compose.ui.graphics.vector.ImageVector
import com.ttele.algoking.engine.core.AlgorithmId
import com.ttele.algoking.engine.progress.AlgorithmProgress
import com.ttele.algoking.ui.components.AlgorithmStatus
import com.ttele.algoking.ui.icons.AlgoIcons
import com.ttele.algoking.ui.theme.AlgoAccent

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
)

/**
 * Turns learning progress into the card's right-hand slot.
 *
 * The label names the *stage boundary* the learner is standing on, so the ring and
 * the words say the same thing: 33 % reads as "Watch done", not as a number with no
 * meaning attached to it.
 */
fun statusFor(progress: AlgorithmProgress): AlgorithmStatus = when {
    progress.mastered -> AlgorithmStatus.Mastered()
    progress.tryCompleted -> AlgorithmStatus.InProgress(progress.percent, "Try done")
    progress.watchCompleted -> AlgorithmStatus.InProgress(progress.percent, "Watch done")
    progress.started -> AlgorithmStatus.InProgress(progress.percent)
    else -> AlgorithmStatus.InProgress(0, "Not started")
}
