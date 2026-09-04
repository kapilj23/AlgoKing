package com.ttele.algoking.engine.algorithms.structures

import com.ttele.algoking.engine.core.Algorithm
import com.ttele.algoking.engine.core.AlgorithmId
import com.ttele.algoking.engine.core.Dataset
import com.ttele.algoking.engine.core.Probe
import com.ttele.algoking.engine.core.Transition
import com.ttele.algoking.engine.decision.ActionOption
import com.ttele.algoking.engine.decision.Decision
import com.ttele.algoking.engine.decision.DecisionKind
import com.ttele.algoking.engine.event.ExamineRole
import com.ttele.algoking.engine.event.Outcome
import com.ttele.algoking.engine.event.VizEvent
import com.ttele.algoking.engine.narration.NarrationId
import com.ttele.algoking.engine.narration.NarrationKey

/**
 * The copy and the one rule that separate one linear structure from another.
 *
 * Everything else about Stack and Queue is identical — which is exactly the point
 * the pair of lessons is making. **The only real difference is which end
 * `REMOVE` takes from**, and that single line is [removesFromFront].
 */
interface StructureFlavour {
    val id: AlgorithmId

    /** Queue removes from the front; Stack removes from the end it added to. */
    val removesFromFront: Boolean

    /** Labels for the two live ends, e.g. `TOP`, or `FRONT` and `REAR`. */
    val nextOutLabel: String
    val lastInLabel: String?

    /** Flanking labels for the drawn structure, e.g. `FRONT →` / `← REAR`. */
    val leadingCap: String?
    val trailingCap: String?

    fun opLabel(op: StructureOp): NarrationId
    fun instruction(op: StructureOp): NarrationId
    fun predictPrompt(): NarrationId
    fun correctFeedback(op: StructureOp): NarrationId
    fun guidance(op: StructureOp): List<NarrationId>
    fun minimalFeedback(op: StructureOp): NarrationId
    fun whyWrong(chosen: StructureOp, wanted: StructureOp): NarrationId
    fun hintLadder(op: StructureOp): List<NarrationId>
    fun predictGuidance(): List<NarrationId>
    fun emptyNarration(): NarrationId

    /** The walkthrough copy, kept in one bundle so the interface stays readable. */
    val watch: StructureWatchCopy

    // ── Where things are drawn ────────────────────────────────────────────────

    /**
     * Where entry-order [index] appears on screen.
     *
     * A Queue draws entry order left to right. A Stack draws a pile, newest on top,
     * so its drawn order is the reverse. Everything the learner taps is a drawn
     * slot; this is the only place the two coordinate systems meet.
     */
    fun displaySlot(size: Int, index: Int): Int =
        if (removesFromFront) index else size - 1 - index

    /** The inverse of [displaySlot] — and, for a Stack, the same function. */
    fun entryIndex(size: Int, slot: Int): Int =
        if (removesFromFront) slot else size - 1 - slot
}

/** Walkthrough copy for one structure. */
data class StructureWatchCopy(
    val setup: NarrationId,
    val setupSupport: NarrationId,
    val added: NarrationId,
    val addedSupport: NarrationId,
    val removed: NarrationId,
    val removedSupport: NarrationId,
    val peeked: NarrationId,
    val peekedSupport: NarrationId,
    val predictPrompt: NarrationId,
    val predictRight: NarrationId,
    val predictWrong: NarrationId,
    val insight: NarrationId,
    val insightSupport: NarrationId,
    val summary: NarrationId,
    val summarySupport: NarrationId,
    val ideas: List<NarrationId>,
)

/**
 * One algorithm serves both structures.
 *
 * This is *not* the "force everything into one interaction" trap: the learner's
 * decision genuinely is the same shape in both — pick the operation, or point at
 * the item that leaves next. What differs is the rule behind the right answer, and
 * that lives in [flavour]. Writing two near-identical files would have hidden the
 * one line that actually distinguishes LIFO from FIFO.
 */
class LinearStructureAlgorithm(
    private val flavour: StructureFlavour,
) : Algorithm<StructureState, StructureAction> {

    override val id = flavour.id

    override fun initial(dataset: Dataset) = StructureState(
        items = emptyList(),
        script = buildScript(dataset.values),
        at = 0,
        // One slot spare, so the structure never fills during a scripted lesson but
        // "full" is still a state the model knows how to be in.
        capacity = maxOf(4, dataset.values.size),
        done = dataset.values.isEmpty(),
    )

    /** The entry index `REMOVE` or `PEEK` would touch. */
    private fun nextOutIndex(state: StructureState): Int? = when {
        state.empty -> null
        flavour.removesFromFront -> 0
        else -> state.items.lastIndex
    }

    /** The drawn position of the reachable item. Zero for both structures — by
     *  construction, since each one draws its live end first. */
    private fun nextOutSlot(state: StructureState): Int? =
        nextOutIndex(state)?.let { flavour.displaySlot(state.items.size, it) }

    override fun probe(state: StructureState): Probe<StructureAction> {
        if (state.done || state.at >= state.script.size) {
            return Probe.Terminal(Outcome.Completed(correct = true))
        }
        return when (val task = requireNotNull(state.task)) {
            is StructureTask.Operate -> Probe.Decide(operateDecision(state, task))
            is StructureTask.Predict ->
                // Nothing to point at. Never happens in an authored script, but the
                // probe has to be total.
                if (state.empty) {
                    Probe.Mechanical(StructureAction.Point(0))
                } else {
                    Probe.Decide(predictDecision(state))
                }
        }
    }

    // ── Decisions ─────────────────────────────────────────────────────────────

    private fun operateDecision(
        state: StructureState,
        task: StructureTask.Operate,
    ): Decision<StructureAction> {
        val wanted = task.op
        val args = listOfNotNull(task.value)

        return Decision(
            kind = DecisionKind.OPTIONS,
            // The instruction names the *goal*, never the operation — otherwise the
            // learner is matching a word rather than understanding the structure.
            prompt = NarrationKey(flavour.instruction(wanted), args),
            options = StructureOp.entries.map { op ->
                ActionOption(
                    StructureAction.Perform(op),
                    NarrationKey(flavour.opLabel(op)),
                )
            },
            correct = StructureAction.Perform(wanted),
            focus = if (wanted == StructureOp.ADD) {
                emptyList()
            } else {
                listOfNotNull(nextOutSlot(state))
            },
            hint = NarrationKey(flavour.hintLadder(wanted).first()),
            guidance = flavour.guidance(wanted).map { NarrationKey(it, args) },
            minimalFeedback = NarrationKey(flavour.minimalFeedback(wanted), args),
            whyWrong = StructureOp.entries
                .filter { it != wanted }
                .associate { chosen ->
                    StructureAction.Perform(chosen) to
                        NarrationKey(flavour.whyWrong(chosen, wanted))
                },
            correctFeedback = NarrationKey(flavour.correctFeedback(wanted), args),
            hintLadder = flavour.hintLadder(wanted).map { NarrationKey(it, args) },
        )
    }

    /**
     * "Which item leaves next?"
     *
     * The projector hides the end labels while this is the live task, so the answer
     * has to come from the rule rather than from an arrow already pointing at it.
     */
    private fun predictDecision(state: StructureState): Decision<StructureAction> {
        val index = requireNotNull(nextOutIndex(state))
        val answer = flavour.displaySlot(state.items.size, index)
        return Decision(
            kind = DecisionKind.CELL,
            prompt = NarrationKey(flavour.predictPrompt()),
            options = state.items.indices.map { i ->
                val slot = flavour.displaySlot(state.items.size, i)
                ActionOption(
                    action = StructureAction.Point(slot),
                    label = NarrationKey(NarrationId.STRUCT_OPTION_POINT),
                    slot = slot,
                )
            },
            correct = StructureAction.Point(answer),
            focus = listOf(answer),
            hint = NarrationKey(flavour.predictGuidance().first()),
            guidance = flavour.predictGuidance().map { NarrationKey(it) },
            minimalFeedback = NarrationKey(flavour.predictGuidance().first()),
            correctFeedback = NarrationKey(
                NarrationId.STRUCT_PREDICT_RIGHT,
                listOf(state.items[index]),
            ),
            hintLadder = flavour.predictGuidance().map { NarrationKey(it) },
        )
    }

    // ── Transitions ───────────────────────────────────────────────────────────

    override fun apply(
        state: StructureState,
        action: StructureAction,
    ): Transition<StructureState> = when (action) {
        is StructureAction.Perform -> perform(state, action.op)
        is StructureAction.Point -> point(state, action.slot)
    }

    private fun perform(state: StructureState, op: StructureOp): Transition<StructureState> {
        val task = state.task as? StructureTask.Operate
        val wanted = task?.op

        // Applying the wrong operation is still total — validation is what stops it
        // ever reaching here, not a guard inside the transition.
        if (op != wanted) return Transition(state, emptyList(), null, correct = false)

        return when (op) {
            StructureOp.ADD -> add(state, requireNotNull(task.value))
            StructureOp.REMOVE -> remove(state)
            StructureOp.PEEK -> peek(state)
        }
    }

    private fun add(state: StructureState, value: Int): Transition<StructureState> {
        if (state.full) {
            // Overflow is a fact about the structure, not an error in the lesson.
            return Transition(
                next = advance(state),
                events = emptyList(),
                narration = NarrationKey(NarrationId.STRUCT_FULL, listOf(value)),
                correct = true,
            )
        }
        // Entry order, always appended. The structure decides which end leaves.
        val items = state.items + value
        val slot = flavour.displaySlot(items.size, items.lastIndex)
        return Transition(
            next = advance(
                state.copy(items = items, peeked = null, lastRemoved = null, reachedEmpty = false),
            ),
            events = listOf(VizEvent.Insert(value, slot)),
            narration = NarrationKey(flavour.correctFeedback(StructureOp.ADD), listOf(value)),
            correct = true,
        )
    }

    private fun remove(state: StructureState): Transition<StructureState> {
        val index = nextOutIndex(state)
            // Underflow is explained, never crashed. Clearing the two memories
            // matters: a stale `lastRemoved` would let the screen repeat the
            // previous removal as though it had just happened again.
            ?: return Transition(
                next = advance(
                    state.copy(peeked = null, lastRemoved = null, reachedEmpty = true),
                ),
                events = emptyList(),
                narration = NarrationKey(flavour.emptyNarration()),
                correct = true,
            )

        val value = state.items[index]
        return Transition(
            next = advance(
                state.copy(
                    items = state.items.filterIndexed { i, _ -> i != index },
                    peeked = null,
                    lastRemoved = value,
                    reachedEmpty = false,
                ),
            ),
            events = listOf(VizEvent.Remove(flavour.displaySlot(state.items.size, index))),
            narration = NarrationKey(
                flavour.correctFeedback(StructureOp.REMOVE),
                listOf(value),
            ),
            correct = true,
        )
    }

    /** PEEK is the one operation that must leave the structure untouched. */
    private fun peek(state: StructureState): Transition<StructureState> {
        val index = nextOutIndex(state)
            ?: return Transition(
                next = advance(
                    state.copy(peeked = null, lastRemoved = null, reachedEmpty = true),
                ),
                events = emptyList(),
                narration = NarrationKey(flavour.emptyNarration()),
                correct = true,
            )
        val value = state.items[index]
        return Transition(
            next = advance(state.copy(peeked = value, lastRemoved = null, reachedEmpty = false)),
            events = listOf(
                VizEvent.Examine(
                    listOf(flavour.displaySlot(state.items.size, index)),
                    ExamineRole.INSPECTING,
                ),
            ),
            narration = NarrationKey(flavour.correctFeedback(StructureOp.PEEK), listOf(value)),
            correct = true,
        )
    }

    /**
     * A prediction changes nothing. It records that the learner committed, and the
     * operation that follows is what actually moves the structure.
     */
    private fun point(state: StructureState, slot: Int): Transition<StructureState> {
        val answer = nextOutSlot(state)
        return Transition(
            next = advance(state),
            events = listOf(VizEvent.Examine(listOf(slot), ExamineRole.CANDIDATE)),
            narration = NarrationKey(
                NarrationId.STRUCT_PREDICT_RIGHT,
                listOfNotNull(nextOutIndex(state)?.let { state.items[it] }),
            ),
            correct = slot == answer,
        )
    }

    private fun advance(state: StructureState): StructureState {
        val next = state.at + 1
        return state.copy(at = next, done = next >= state.script.size)
    }
}
