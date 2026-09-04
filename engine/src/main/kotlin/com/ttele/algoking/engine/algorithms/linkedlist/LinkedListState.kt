package com.ttele.algoking.engine.algorithms.linkedlist

import com.ttele.algoking.engine.core.Dataset
import com.ttele.algoking.engine.decision.Action

/**
 * One node. [id] is stable for the node's whole life, so the renderer can follow a
 * node as the chain rearranges around it rather than re-drawing a box that happens
 * to hold the same number.
 */
data class LinkedNode(val id: Int, val value: Int)

/**
 * What a lesson asks the learner to do to the list.
 *
 * Every list in AlgoKing is kept in order, which is what gives [Insert] a single
 * defensible answer: the learner is asked *where a value belongs*, not asked to
 * guess which gap the author had in mind.
 */
sealed interface ListTask {

    /** Walk from HEAD until [target] turns up — or until NULL proves it is absent. */
    data class Find(val target: Int) : ListTask

    /** Put [value] into the gap where it belongs. */
    data class Insert(val value: Int) : ListTask

    /** Take the node holding [value] out, and reconnect around the hole. */
    data class Delete(val value: Int) : ListTask
}

/**
 * How far through the current task the learner is.
 *
 * A linked-list operation is not one move — it is *find the place*, then *change
 * the links*. Splitting the phases is what lets the lesson ask about each half
 * separately instead of collapsing an insertion into a single tap.
 */
enum class ListPhase {
    /** Walking the chain, one node at a time. */
    WALKING,

    /** Choosing which gap the operation acts on. */
    CHOOSING_GAP,

    /** The gap is open and a new node is floating in it. Where does it point? */
    LINKING,

    /** The link into the doomed node is chosen. Where should it point instead? */
    RECONNECTING,

    /** Nothing is in flight. */
    SETTLED,
}

/** What the learner can do to a linked list. */
sealed interface ListAction : Action {

    /** Answer "is this the node you are looking for?". */
    data class Answer(val yes: Boolean) : ListAction

    /**
     * Point at a **gap** — link `i` is the arrow into node `i`, and link `n` is the
     * arrow to NULL. Pointing at links rather than at nodes *is* the lesson:
     * changing a linked list means changing the arrows.
     */
    data class PickLink(val slot: Int) : ListAction

    /** Say where an arrow should point. [nodeId] is a node, or null for NULL. */
    data class PointAt(val nodeId: Int?) : ListAction

    /**
     * There is nothing this task can do to this list — deleting from an empty list,
     * searching for a value that was never there. Applied by the app, never offered
     * to the learner, and always narrated rather than silently skipped.
     */
    data object Skip : ListAction
}

/**
 * The list, plus where the learner is in the current operation.
 *
 * [nodes] is chain order — index 0 is the node HEAD points at. That is an ordering
 * of *links*, not of memory: the lesson says out loud that nodes need not sit next
 * to each other, and the renderer draws arrows rather than adjacency.
 */
data class LinkedListState(
    val nodes: List<LinkedNode>,
    val script: List<ListTask>,
    val at: Int,
    /** The node the learner is standing on, as an index into [nodes]. */
    val cursor: Int? = null,
    val phase: ListPhase = ListPhase.SETTLED,
    /** A node that has been made but is not linked in yet. */
    val detached: LinkedNode? = null,
    /** The gap the current operation has opened. */
    val openLink: Int? = null,
    /** The gap whose arrow was most recently rewired, so it can be shown as new. */
    val changedLink: Int? = null,
    /** True once a [ListTask.Find] has succeeded. */
    val found: Boolean = false,
    /** True when a walk ran off the end without finding the target. */
    val hitNull: Boolean = false,
    val nextId: Int = 1_000,
    val done: Boolean = false,
) {
    val task: ListTask? get() = script.getOrNull(at)
    val empty: Boolean get() = nodes.isEmpty()

    /** Gaps are numbered `0..size` — always one more than there are nodes. */
    val linkCount: Int get() = nodes.size + 1

    val values: List<Int> get() = nodes.map { it.value }

    fun indexOfValue(value: Int): Int? =
        nodes.indexOfFirst { it.value == value }.takeIf { it >= 0 }

    /** The node an arrow out of gap [slot] arrives at; null means NULL. */
    fun nodeAfterLink(slot: Int): LinkedNode? = nodes.getOrNull(slot)

    /** The node an arrow into gap [slot] leaves from; null means HEAD. */
    fun nodeBeforeLink(slot: Int): LinkedNode? = nodes.getOrNull(slot - 1)

    /** Where [value] belongs in an ordered list — the gap it should occupy. */
    fun sortedGap(value: Int): Int = nodes.count { it.value < value }
}

/**
 * Turns a dataset into a lesson script.
 *
 * The script is a pure function of the dataset, and the dataset's `label` names the
 * dataset's *role*: Watch gets the shortest script that still shows every
 * operation, Try adds the awkward case of inserting before the head, and a
 * challenge gets exactly one operation with no scaffolding around it. Keeping the
 * choice here — rather than in three algorithm subclasses — means the engine has
 * one code path and the lesson designer has one file to read.
 */
object ListScripts {

    const val WATCH = "watch"
    const val TRY = "try"
    const val FIND = "find"
    const val INSERT = "insert"
    const val DELETE = "delete"

    fun forDataset(dataset: Dataset): List<ListTask> {
        val v = dataset.values
        if (v.isEmpty()) return emptyList()
        val target = dataset.target ?: v[v.size / 2]

        return when (dataset.label) {
            // Search it, grow it, shrink it. Three operations, nothing repeated.
            WATCH -> listOf(
                ListTask.Find(target),
                ListTask.Insert(between(v[0], v[1])),
                ListTask.Delete(between(v[0], v[1])),
            )

            // Try adds the case every implementation gets wrong first: a value that
            // belongs before the head, where there is no previous node to rewire.
            TRY -> listOf(
                ListTask.Find(target),
                ListTask.Insert(between(v[0], v[1])),
                ListTask.Insert(beforeAll(v)),
                ListTask.Delete(v[2 % v.size]),
            )

            // Change the list, then walk it and see what the change did. After a
            // delete the walk runs off the end, which is the most convincing proof
            // a linked list can offer that a node is really gone.
            INSERT -> listOf(ListTask.Insert(target), ListTask.Find(target))
            DELETE -> listOf(ListTask.Delete(target), ListTask.Find(target))
            else -> listOf(ListTask.Find(target))
        }
    }

    /** A value that lands strictly between two neighbours, so the gap is unique. */
    private fun between(a: Int, b: Int): Int = if (b - a >= 2) (a + b) / 2 else a + 1

    /** A value smaller than everything in the list. */
    private fun beforeAll(v: List<Int>): Int = maxOf(1, v.min() - 2)
}
