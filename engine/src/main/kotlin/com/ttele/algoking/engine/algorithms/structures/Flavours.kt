package com.ttele.algoking.engine.algorithms.structures

import com.ttele.algoking.engine.core.AlgorithmId
import com.ttele.algoking.engine.event.MarkId
import com.ttele.algoking.engine.event.MeterId
import com.ttele.algoking.engine.event.PointerId
import com.ttele.algoking.engine.event.VizEvent
import com.ttele.algoking.engine.narration.NarrationId
import com.ttele.algoking.engine.scene.Badge
import com.ttele.algoking.engine.scene.Cell
import com.ttele.algoking.engine.scene.CellState
import com.ttele.algoking.engine.scene.EndCaps
import com.ttele.algoking.engine.scene.MeterReadout
import com.ttele.algoking.engine.scene.SceneLayout
import com.ttele.algoking.engine.scene.PointerMark
import com.ttele.algoking.engine.scene.SceneProjector
import com.ttele.algoking.engine.scene.SequenceScene

/**
 * **Last In, First Out.** The newest item is the only one you can reach, so the
 * Stack has exactly one live end — the TOP — and it is drawn as a vertical pile
 * where the top really is at the top.
 */
object StackFlavour : StructureFlavour {
    override val id = AlgorithmId.STACK
    override val removesFromFront = false
    override val nextOutLabel = "TOP"
    override val lastInLabel: String? = null
    // Both ends of the caption describe the *same* end, because a stack only has
    // one. That asymmetry against the queue is the point.
    override val leadingCap = "IN ↓   OUT ↑"
    override val trailingCap = "BOTTOM"

    override fun opLabel(op: StructureOp) = when (op) {
        StructureOp.ADD -> NarrationId.STACK_OP_PUSH
        StructureOp.REMOVE -> NarrationId.STACK_OP_POP
        StructureOp.PEEK -> NarrationId.STACK_OP_PEEK
    }

    override fun instruction(op: StructureOp) = when (op) {
        StructureOp.ADD -> NarrationId.STACK_ASK_ADD
        StructureOp.REMOVE -> NarrationId.STACK_ASK_REMOVE
        StructureOp.PEEK -> NarrationId.STACK_ASK_PEEK
    }

    override fun predictPrompt() = NarrationId.STACK_ASK_PREDICT

    override fun correctFeedback(op: StructureOp) = when (op) {
        StructureOp.ADD -> NarrationId.STACK_PUSHED
        StructureOp.REMOVE -> NarrationId.STACK_POPPED
        StructureOp.PEEK -> NarrationId.STACK_PEEKED
    }

    override fun guidance(op: StructureOp) = listOf(
        NarrationId.STACK_RETRY_LOOK,
        NarrationId.STACK_RETRY_ASK,
        explain(op),
    )

    override fun minimalFeedback(op: StructureOp) = NarrationId.STACK_RETRY_LOOK

    override fun whyWrong(chosen: StructureOp, wanted: StructureOp) = when (chosen) {
        StructureOp.ADD -> NarrationId.STACK_WHY_PUSH
        StructureOp.REMOVE -> NarrationId.STACK_WHY_POP
        StructureOp.PEEK -> NarrationId.STACK_WHY_PEEK
    }

    override fun hintLadder(op: StructureOp) = listOf(
        NarrationId.STACK_HINT_TOP,
        NarrationId.STACK_HINT_RULE,
        explain(op),
    )

    override fun predictGuidance() = listOf(
        NarrationId.STACK_PREDICT_LOOK,
        NarrationId.STACK_PREDICT_ASK,
        NarrationId.STACK_PREDICT_EXPLAIN,
    )

    override fun emptyNarration() = NarrationId.STACK_EMPTY

    private fun explain(op: StructureOp) = when (op) {
        StructureOp.ADD -> NarrationId.STACK_RETRY_EXPLAIN_PUSH
        StructureOp.REMOVE -> NarrationId.STACK_RETRY_EXPLAIN_POP
        StructureOp.PEEK -> NarrationId.STACK_RETRY_EXPLAIN_PEEK
    }

    override val watch = StructureWatchCopy(
        setup = NarrationId.STACK_WATCH_SETUP,
        setupSupport = NarrationId.STACK_WATCH_SETUP_SUPPORT,
        added = NarrationId.STACK_WATCH_PUSH,
        addedSupport = NarrationId.STACK_WATCH_PUSH_SUPPORT,
        removed = NarrationId.STACK_WATCH_POP,
        removedSupport = NarrationId.STACK_WATCH_POP_SUPPORT,
        peeked = NarrationId.STACK_WATCH_PEEK,
        peekedSupport = NarrationId.STACK_WATCH_PEEK_SUPPORT,
        predictPrompt = NarrationId.STACK_PREDICT_PROMPT,
        predictRight = NarrationId.STACK_PREDICT_RIGHT,
        predictWrong = NarrationId.STACK_PREDICT_WRONG,
        insight = NarrationId.STACK_WATCH_INSIGHT,
        insightSupport = NarrationId.STACK_WATCH_INSIGHT_SUPPORT,
        summary = NarrationId.STACK_WATCH_SUMMARY,
        summarySupport = NarrationId.STACK_WATCH_SUMMARY_SUPPORT,
        ideas = listOf(
            NarrationId.STACK_IDEA_1,
            NarrationId.STACK_IDEA_2,
            NarrationId.STACK_IDEA_3,
        ),
    )
}

/**
 * **First In, First Out.** Items join at the REAR and leave from the FRONT, so
 * the Queue has *two* live ends and is drawn as a horizontal line — the shape a
 * queue of people actually has.
 */
object QueueFlavour : StructureFlavour {
    override val id = AlgorithmId.QUEUE
    override val removesFromFront = true
    override val nextOutLabel = "FRONT"
    override val lastInLabel = "REAR"
    override val leadingCap = "OUT ←"
    override val trailingCap = "← IN"

    override fun opLabel(op: StructureOp) = when (op) {
        StructureOp.ADD -> NarrationId.QUEUE_OP_ENQUEUE
        StructureOp.REMOVE -> NarrationId.QUEUE_OP_DEQUEUE
        StructureOp.PEEK -> NarrationId.QUEUE_OP_PEEK
    }

    override fun instruction(op: StructureOp) = when (op) {
        StructureOp.ADD -> NarrationId.QUEUE_ASK_ADD
        StructureOp.REMOVE -> NarrationId.QUEUE_ASK_REMOVE
        StructureOp.PEEK -> NarrationId.QUEUE_ASK_PEEK
    }

    override fun predictPrompt() = NarrationId.QUEUE_ASK_PREDICT

    override fun correctFeedback(op: StructureOp) = when (op) {
        StructureOp.ADD -> NarrationId.QUEUE_ENQUEUED
        StructureOp.REMOVE -> NarrationId.QUEUE_DEQUEUED
        StructureOp.PEEK -> NarrationId.QUEUE_PEEKED
    }

    override fun guidance(op: StructureOp) = listOf(
        NarrationId.QUEUE_RETRY_LOOK,
        NarrationId.QUEUE_RETRY_ASK,
        explain(op),
    )

    override fun minimalFeedback(op: StructureOp) = NarrationId.QUEUE_RETRY_LOOK

    override fun whyWrong(chosen: StructureOp, wanted: StructureOp) = when (chosen) {
        StructureOp.ADD -> NarrationId.QUEUE_WHY_ENQUEUE
        StructureOp.REMOVE -> NarrationId.QUEUE_WHY_DEQUEUE
        StructureOp.PEEK -> NarrationId.QUEUE_WHY_PEEK
    }

    override fun hintLadder(op: StructureOp) = listOf(
        NarrationId.QUEUE_HINT_ENDS,
        NarrationId.QUEUE_HINT_RULE,
        explain(op),
    )

    override fun predictGuidance() = listOf(
        NarrationId.QUEUE_PREDICT_LOOK,
        NarrationId.QUEUE_PREDICT_ASK,
        NarrationId.QUEUE_PREDICT_EXPLAIN,
    )

    override fun emptyNarration() = NarrationId.QUEUE_EMPTY

    private fun explain(op: StructureOp) = when (op) {
        StructureOp.ADD -> NarrationId.QUEUE_RETRY_EXPLAIN_ENQUEUE
        StructureOp.REMOVE -> NarrationId.QUEUE_RETRY_EXPLAIN_DEQUEUE
        StructureOp.PEEK -> NarrationId.QUEUE_RETRY_EXPLAIN_PEEK
    }

    override val watch = StructureWatchCopy(
        setup = NarrationId.QUEUE_WATCH_SETUP,
        setupSupport = NarrationId.QUEUE_WATCH_SETUP_SUPPORT,
        added = NarrationId.QUEUE_WATCH_ENQUEUE,
        addedSupport = NarrationId.QUEUE_WATCH_ENQUEUE_SUPPORT,
        removed = NarrationId.QUEUE_WATCH_DEQUEUE,
        removedSupport = NarrationId.QUEUE_WATCH_DEQUEUE_SUPPORT,
        peeked = NarrationId.QUEUE_WATCH_PEEK,
        peekedSupport = NarrationId.QUEUE_WATCH_PEEK_SUPPORT,
        predictPrompt = NarrationId.QUEUE_PREDICT_PROMPT,
        predictRight = NarrationId.QUEUE_PREDICT_RIGHT,
        predictWrong = NarrationId.QUEUE_PREDICT_WRONG,
        insight = NarrationId.QUEUE_WATCH_INSIGHT,
        insightSupport = NarrationId.QUEUE_WATCH_INSIGHT_SUPPORT,
        summary = NarrationId.QUEUE_WATCH_SUMMARY,
        summarySupport = NarrationId.QUEUE_WATCH_SUMMARY_SUPPORT,
        ideas = listOf(
            NarrationId.QUEUE_IDEA_1,
            NarrationId.QUEUE_IDEA_2,
            NarrationId.QUEUE_IDEA_3,
        ),
    )
}

/**
 * Draws a linear structure.
 *
 * The two flavours must not look alike, and this is where that is decided: a Stack
 * is a **vertical pile with one live end**, a Queue is a **horizontal line with
 * two**. Everything else — cells, badges, end labels — is the same design system,
 * so the difference the learner sees is the difference that matters.
 */
class StructureProjector(private val flavour: StructureFlavour) :
    SceneProjector<StructureState> {

    override fun project(
        state: StructureState,
        activeEvents: List<VizEvent>,
    ): SequenceScene {
        // While the learner is being asked which item leaves next, the labels that
        // would answer the question are taken away. Otherwise the prediction tests
        // whether they can follow an arrow.
        val predicting = state.task is StructureTask.Predict

        val size = state.items.size
        // Reaching into an empty structure draws the slot that was reached for. It
        // is the only honest way to show "there was nothing there" — a caption on an
        // unchanged screen would read as a step that did nothing.
        if (state.empty && state.reachedEmpty) {
            return SequenceScene(
                cells = listOf(Cell(key = -1, value = 0, slot = 0, state = CellState.GHOST)),
                layout = if (flavour.removesFromFront) SceneLayout.ROW else SceneLayout.PILE,
                endCaps = EndCaps(flavour.leadingCap, flavour.trailingCap),
                legendLabels = mapOf(CellState.GHOST to "Nothing there"),
            )
        }
        val cells = state.items.mapIndexed { index, value ->
            val slot = flavour.displaySlot(size, index)
            Cell(
                key = index,
                value = value,
                slot = slot,
                state = when {
                    predicting -> CellState.IDLE
                    // The one item you can actually reach.
                    slot == 0 -> CellState.CANDIDATE
                    else -> CellState.IDLE
                },
            )
        }.sortedBy { it.slot }

        val pointers = when {
            predicting || state.empty -> emptyList()
            else -> buildList {
                add(PointerMark(PointerId.I, 0, flavour.nextOutLabel))
                flavour.lastInLabel?.let {
                    add(PointerMark(PointerId.J, cells.lastIndex, it))
                }
            }
        }

        return SequenceScene(
            cells = cells,
            pointers = pointers,
            badge = state.peeked
                ?.takeUnless { predicting }
                ?.let { Badge(MarkId.BEST, flavour.nextOutLabel, it) },
            meters = listOf(
                MeterReadout(MeterId.REMAINING, "Items", size.toLong()),
            ),
            layout = if (flavour.removesFromFront) SceneLayout.ROW else SceneLayout.PILE,
            endCaps = if (predicting) {
                null
            } else {
                EndCaps(flavour.leadingCap, flavour.trailingCap)
            },
            // Both structures say the same words here on purpose: "next out" is
            // identical, and only the item it lands on differs.
            legendLabels = mapOf(CellState.CANDIDATE to "Next out"),
        )
    }
}
