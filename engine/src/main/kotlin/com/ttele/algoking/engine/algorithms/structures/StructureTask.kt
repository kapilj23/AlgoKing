package com.ttele.algoking.engine.algorithms.structures

import com.ttele.algoking.engine.decision.Action

/**
 * The operations a linear data structure exposes.
 *
 * Stack and Queue share this vocabulary because the *learner's* decision is the
 * same shape in both — **which operation does this job?** — even though the ends
 * they act on are opposite. That is the comparison the lessons are building
 * towards: `ADD` and `REMOVE` are one word each here, and the structure decides
 * which end they touch.
 */
enum class StructureOp {
    /** PUSH on a Stack, ENQUEUE on a Queue. */
    ADD,

    /** POP on a Stack, DEQUEUE on a Queue. */
    REMOVE,

    /** Look without changing anything. Identical in both. */
    PEEK,
}

/** One instruction in a lesson's script. */
sealed interface StructureTask {

    /** "Add 40." — the learner must choose the adding operation. */
    data class Operate(val op: StructureOp, val value: Int? = null) : StructureTask

    /**
     * "Which item will leave next?" — the learner points at a cell rather than
     * performing the operation. This is what tests the *model* rather than the
     * button, and it is where LIFO and FIFO actually diverge.
     */
    data class Predict(val op: StructureOp) : StructureTask
}

/** What the learner can do. */
sealed interface StructureAction : Action {

    /** Perform an operation. */
    data class Perform(val op: StructureOp) : StructureAction

    /**
     * Point at a cell as the answer to a prediction.
     *
     * [slot] is the **drawn** position, not the entry index — the learner taps what
     * they can see, and a Stack draws its newest item first. Mapping back is the
     * algorithm's job, not the screen's.
     */
    data class Point(val slot: Int) : StructureAction
}

/**
 * State shared by both structures.
 *
 * [items] is stored **entry-order**: index 0 entered first. Which end counts as
 * "next out" is the structure's business, not the state's — that single choice is
 * the whole difference between LIFO and FIFO.
 */
data class StructureState(
    val items: List<Int>,
    val script: List<StructureTask>,
    val at: Int,
    /** The item most recently looked at, so PEEK can be shown as having happened. */
    val peeked: Int? = null,
    /** The item most recently removed, for the leaving narration. */
    val lastRemoved: Int? = null,
    /**
     * Real structures are not infinite. A bounded capacity is what makes "full" a
     * teachable state instead of a crash the learner never sees.
     */
    val capacity: Int = 6,
    /**
     * True when the last operation reached for an item that was not there. It is
     * state rather than a message because the *screen* has to show it: a caption
     * alone would leave two identical frames in a row.
     */
    val reachedEmpty: Boolean = false,
    val done: Boolean = false,
) {
    val task: StructureTask? get() = script.getOrNull(at)
    val empty: Boolean get() = items.isEmpty()
    val full: Boolean get() = items.size >= capacity
    val completed: Int get() = at
    val total: Int get() = script.size
}

/**
 * Builds a lesson script from a list of values.
 *
 * Deterministic, and shaped so the learner always meets the same beats in the same
 * order: fill it up, predict what leaves, take it off, add again, look without
 * changing, then empty it out. A script that only ever added would never show the
 * structure's actual rule.
 *
 * Two placements are deliberate. The prediction sits at the first moment where a
 * Stack and a Queue would give **different** answers. And the tail drains the
 * structure completely and then asks for one more — because "what happens when it
 * is empty?" is the question every implementation has to answer, and a lesson that
 * never reaches empty never answers it.
 */
fun buildScript(values: List<Int>): List<StructureTask> = buildList {
    values.forEachIndexed { index, value ->
        add(StructureTask.Operate(StructureOp.ADD, value))
        // Three items in is the earliest point where "newest" and "oldest" are
        // clearly different things.
        if (index == 2) {
            add(StructureTask.Predict(StructureOp.REMOVE))
            add(StructureTask.Operate(StructureOp.REMOVE))
        }
    }
    add(StructureTask.Operate(StructureOp.PEEK))
    // Draining shows the *order* items come out in, which is the whole lesson —
    // and one removal past the end shows what an empty structure does.
    repeat(values.size) { add(StructureTask.Operate(StructureOp.REMOVE)) }
}