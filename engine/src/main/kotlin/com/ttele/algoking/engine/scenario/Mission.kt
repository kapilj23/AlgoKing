package com.ttele.algoking.engine.scenario

import com.ttele.algoking.engine.challenge.Difficulty
import com.ttele.algoking.engine.core.Dataset

/**
 * The story a Challenge is wrapped in.
 *
 * Watch teaches Binary Search, Try guides it, and Challenge asks whether the
 * learner can *recognise* that this is a Binary Search problem at all. That
 * recognition is the thing being tested, so the Challenge never names the
 * algorithm: it hands over a sorted world and a target and stops talking.
 *
 * A mission is a **presentation layer over a dataset**. It owns the story, the
 * labels and the units; it owns no algorithm logic whatsoever. The engine still
 * searches `dataset.values`, which is why Warehouse, Library and Box Office are
 * one implementation rather than three.
 */
data class Mission(
    val kind: MissionKind,
    /** One emoji. The mission's face on the brief and the result. */
    val icon: String,
    /** "Warehouse Mission" */
    val title: String,
    /** The situation, in one or two sentences. Never mentions Binary Search. */
    val story: String,
    /** What success looks like. "Find product #7842." */
    val goal: String,
    /** What the sorted thing is: "products", "books", "screenings". */
    val subject: String,
    /** Says out loud that the data is ordered — the one clue the learner gets. */
    val sortedBy: String,
    val dataset: Dataset,
    /**
     * Display text per slot, parallel to `dataset.values`.
     *
     * This is what lets a Library mission search book titles on an engine that
     * only understands integers: the values are ranks in alphabetical order, and
     * comparing ranks *is* comparing titles.
     */
    val labels: List<String>,
    /** The target as the learner sees it: "#7842", "The Alchemist", "₹750". */
    val targetLabel: String,
    val difficulty: Difficulty,
) {
    val size: Int get() = dataset.values.size

    fun label(slot: Int): String =
        labels.getOrElse(slot) { dataset.values.getOrElse(slot) { 0 }.toString() }

    /** Where the answer is. Used by tests and by the result's narrowing trail. */
    val targetSlot: Int get() = dataset.values.indexOf(dataset.target)
}

/**
 * The worlds a Binary Search mission can be set in.
 *
 * Each is a different *data type* wearing the same algorithm — an integer id, an
 * alphabetical rank, a price, a timestamp. A learner who solves all four has had
 * to notice that "sorted" is the only property Binary Search actually needs.
 */
enum class MissionKind {
    WAREHOUSE,
    BOX_OFFICE,
    SERVER_LOG,
    // LIBRARY is written but not in rotation: a book title needs a vertical
    // shelf to be readable, and a 30dp row cell clips it to nonsense.
}
