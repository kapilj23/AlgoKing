package com.ttele.algoking.engine.algorithms.avl

import com.ttele.algoking.engine.core.Algorithm
import com.ttele.algoking.engine.core.AlgorithmId
import com.ttele.algoking.engine.core.BinaryTree
import com.ttele.algoking.engine.core.Dataset
import com.ttele.algoking.engine.core.Probe
import com.ttele.algoking.engine.core.Transition
import com.ttele.algoking.engine.decision.Action
import com.ttele.algoking.engine.decision.ActionOption
import com.ttele.algoking.engine.decision.Decision
import com.ttele.algoking.engine.decision.DecisionKind
import com.ttele.algoking.engine.event.ExamineRole
import com.ttele.algoking.engine.event.MeterId
import com.ttele.algoking.engine.event.Outcome
import com.ttele.algoking.engine.event.VizEvent
import com.ttele.algoking.engine.narration.NarrationId
import com.ttele.algoking.engine.narration.NarrationKey

/**
 * What the learner does in the AVL lesson.
 *
 * **One gesture the whole way through: tap the node.** It answers two different
 * questions on alternate beats — *which node is out of balance?* and *which node
 * takes its place?* — and between them they are the entire algorithm.
 *
 * The alternative was a row of four buttons reading RIGHT / LEFT / LEFT-RIGHT /
 * RIGHT-LEFT. That was rejected for the reason ADR-034 rejected a BACKTRACK
 * button: it teaches the learner to classify a case into a name and reach for the
 * matching control, when what they actually have to see is **which node comes
 * up**. Naming it is the app's job, and it names it the moment they are right.
 */
sealed interface AvlAction : Action {

    /** Place the next value by the BST rule. Mechanical — that lesson is done. */
    data object Insert : AvlAction

    /** The lowest node whose balance factor has reached ±2. */
    data class Pivot(val node: Int) : AvlAction

    /** The node that moves up into the pivot's place: its child, or its grandchild. */
    data class Raise(val node: Int) : AvlAction

    /** Perform the next rotation of the agreed plan. Mechanical — it is bookkeeping. */
    data object Rotate : AvlAction
}

/** A single rotation, as the picture will perform it. */
data class RotationStep(val kind: RotationKind, val at: Int)

enum class RotationKind { LEFT, RIGHT }

/**
 * The four shapes an imbalance can take, named for the two steps from the
 * unbalanced node down toward the value that caused it.
 *
 * `LEFT_LEFT` and `RIGHT_RIGHT` lean one way and are fixed by **one** rotation;
 * `LEFT_RIGHT` and `RIGHT_LEFT` zig-zag and need **two**. That split — straight
 * or bent — is the only classification the lesson asks the learner to make, and
 * they make it by choosing which node comes up rather than by naming the case.
 */
enum class ImbalanceShape {
    LEFT_LEFT,
    LEFT_RIGHT,
    RIGHT_LEFT,
    RIGHT_RIGHT;

    val isDouble: Boolean get() = this == LEFT_RIGHT || this == RIGHT_LEFT
}

/**
 * Immutable state — the single source of truth for the whole lesson.
 *
 * [pivot], [riser] and [plan] are the learner's answers and their consequence;
 * everything else about balance is **derived** from [tree], so no field can ever
 * disagree with the shape on screen.
 */
data class AvlState(
    val tree: BinaryTree,
    /** Values still to be inserted, in order. The head is next. */
    val pending: List<Int>,
    /** The value placed this round, or null between rounds. */
    val justInserted: Int?,
    /** The unbalanced node, once the learner has named it. */
    val pivot: Int?,
    /** The node moving up, once the learner has named it. */
    val riser: Int?,
    /** The rotations still to perform. One step for a single, two for a double. */
    val plan: List<RotationStep>,
    /** Rotations performed so far, for the run summary. */
    val rotations: Int,
    /** What the last rotation was, so the picture and the copy can say it. */
    val lastRotation: RotationStep?,
) {
    /** Every node's balance factor. The lesson shows all of them, always. */
    val balanceFactors: Map<Int, Int> get() = tree.balanceFactors()

    /**
     * **The lowest node that has reached ±2**, or null while the tree is legal.
     *
     * Derived rather than stored: an insertion can only unbalance a node on the
     * path it descended, and the *lowest* one is the one to rotate — fixing it
     * brings every ancestor back into range, which is why one insert never needs
     * more than one repair.
     */
    val unbalanced: Int?
        get() {
            val inserted = justInserted ?: return null
            return tree.searchPath(inserted)
                .lastOrNull { tree.balanceFactor(it) !in -1..1 }
        }

    /** Which way the imbalance under [node] leans, read as two steps toward the new value. */
    fun shapeAt(node: Int): ImbalanceShape? {
        val inserted = justInserted ?: return null
        val path = tree.searchPath(inserted)
        val index = path.indexOf(node)
        if (index < 0) return null
        val first = path.getOrNull(index + 1) ?: return null
        val second = path.getOrNull(index + 2) ?: return null
        val firstLeft = first < node
        val secondLeft = second < first
        return when {
            firstLeft && secondLeft -> ImbalanceShape.LEFT_LEFT
            firstLeft -> ImbalanceShape.LEFT_RIGHT
            secondLeft -> ImbalanceShape.RIGHT_LEFT
            else -> ImbalanceShape.RIGHT_RIGHT
        }
    }

    /**
     * The node that must come up at [node]: its child when the imbalance runs
     * straight, its grandchild when it bends.
     *
     * That single sentence is the lesson, and it is why the learner is asked for
     * a node rather than for the name of a case.
     */
    fun riserFor(node: Int): Int? {
        val inserted = justInserted ?: return null
        val path = tree.searchPath(inserted)
        val index = path.indexOf(node)
        if (index < 0) return null
        val shape = shapeAt(node) ?: return null
        return if (shape.isDouble) path.getOrNull(index + 2) else path.getOrNull(index + 1)
    }

    /** The child of [node] on the way down to the value that unbalanced it. */
    fun heavyChildOf(node: Int): Int? {
        val inserted = justInserted ?: return null
        val path = tree.searchPath(inserted)
        return path.getOrNull(path.indexOf(node) + 1)
    }

    /** The rotations that raise [riserFor] into [pivot]'s place. */
    fun planFor(pivot: Int): List<RotationStep> {
        val shape = shapeAt(pivot) ?: return emptyList()
        val child = heavyChildOf(pivot) ?: return emptyList()
        return when (shape) {
            ImbalanceShape.LEFT_LEFT -> listOf(RotationStep(RotationKind.RIGHT, pivot))
            ImbalanceShape.RIGHT_RIGHT -> listOf(RotationStep(RotationKind.LEFT, pivot))
            // A double is two singles: straighten the bend, then rotate as usual.
            // Performing them as separate steps is what makes that visible.
            ImbalanceShape.LEFT_RIGHT -> listOf(
                RotationStep(RotationKind.LEFT, child),
                RotationStep(RotationKind.RIGHT, pivot),
            )

            ImbalanceShape.RIGHT_LEFT -> listOf(
                RotationStep(RotationKind.RIGHT, child),
                RotationStep(RotationKind.LEFT, pivot),
            )
        }
    }

    val finished: Boolean
        get() = pending.isEmpty() && justInserted == null && plan.isEmpty()
}

/**
 * AVL tree insertion — an Advanced lesson, and the answer to the caveat the
 * Binary Search Tree lesson had to end on.
 *
 * A BST is only fast while it stays bushy; insert values in ascending order and
 * it degenerates into a linked list and search costs O(n). An AVL tree refuses to
 * let that happen: after every insertion it checks how lopsided each node on the
 * path has become, and the moment one is off by 2 it **rotates** that node back
 * into range.
 *
 * ```
 * balance factor = height(left) - height(right),  and it must stay in -1..1
 *
 * straight (LL / RR)  ->  one rotation,  the child comes up
 * bent     (LR / RL)  ->  two rotations, the grandchild comes up
 * ```
 *
 * ### What the learner has to understand
 *
 * Not the four case names, which are memorisable and worth little. Three things:
 *
 * 1. **not every insert needs a fix** — the first one in each stage deliberately
 *    does not, so nobody leaves thinking a rotation follows every insertion;
 * 2. **where the fix goes** — the *lowest* node that has reached ±2;
 * 3. **which node comes up** — the child, or the grandchild when the path bends.
 *
 * ### Why rotations are safe, which is the insight
 *
 * A rotation re-hangs three links and changes the *depth* of nodes without
 * changing their **order** — the in-order sequence is identical before and after,
 * so the result is still a search tree. The picture says it out loud: nodes keep
 * their column and only change row.
 */
class AvlTreeAlgorithm : Algorithm<AvlState, AvlAction> {

    override val id = AlgorithmId.AVL_TREE

    override fun initial(dataset: Dataset) = AvlState(
        tree = dataset.tree ?: BinaryTree.EMPTY,
        pending = dataset.values,
        justInserted = null,
        pivot = null,
        riser = null,
        plan = emptyList(),
        rotations = 0,
        lastRotation = null,
    )

    override fun probe(state: AvlState): Probe<AvlAction> {
        // A plan already agreed is bookkeeping: the learner made the judgement,
        // and performing it one rotation at a time is what shows a double being
        // two singles.
        if (state.plan.isNotEmpty()) return Probe.Mechanical(AvlAction.Rotate)

        val unbalanced = state.unbalanced
        if (unbalanced != null) {
            if (state.pivot == null) return Probe.Decide(pivotDecision(state, unbalanced))
            return Probe.Decide(riserDecision(state, state.pivot))
        }

        // Balanced, so the round is over. Placing the next value by the BST rule
        // is the previous lesson's work, not this one's (PRODUCT_SPEC.md §3).
        if (state.pending.isNotEmpty()) return Probe.Mechanical(AvlAction.Insert)

        return Probe.Terminal(Outcome.Completed(true))
    }

    // -- Decisions ------------------------------------------------------------

    private fun pivotDecision(state: AvlState, correct: Int): Decision<AvlAction> {
        val order = state.tree.inorder()
        val factor = state.tree.balanceFactor(correct)

        return Decision(
            kind = DecisionKind.CELL,
            prompt = NarrationKey(NarrationId.AVL_ASK_PIVOT),
            // Every node is tappable. Narrowing the options to the insertion path
            // would answer half the question by outlining where to look.
            options = order.mapIndexed { slot, value ->
                ActionOption<AvlAction>(
                    action = AvlAction.Pivot(value),
                    label = NarrationKey(NarrationId.AVL_OPTION_NODE, listOf(value)),
                    slot = slot,
                )
            },
            correct = AvlAction.Pivot(correct),
            focus = listOf(order.indexOf(correct)),
            hint = NarrationKey(NarrationId.AVL_HINT_PIVOT),
            guidance = listOf(
                NarrationKey(NarrationId.AVL_RETRY_PIVOT_LOOK),
                NarrationKey(NarrationId.AVL_RETRY_PIVOT_ASK),
                NarrationKey(
                    NarrationId.AVL_RETRY_PIVOT_EXPLAIN,
                    listOf(correct, factor, signed(factor)),
                ),
            ),
            minimalFeedback = NarrationKey(NarrationId.AVL_RETRY_PIVOT_LOOK),
            whyWrong = buildMap {
                for (value in order) {
                    if (value == correct) continue
                    val theirs = state.tree.balanceFactor(value)
                    val id = when {
                        // Off by 2 as well, but higher up: fixing the lowest one
                        // brings the ancestors back on its own.
                        theirs !in -1..1 -> NarrationId.AVL_WHY_NOT_LOWEST
                        else -> NarrationId.AVL_WHY_STILL_BALANCED
                    }
                    put(
                        AvlAction.Pivot(value),
                        NarrationKey(id, listOf(value, signed(theirs), correct)),
                    )
                }
            },
            correctFeedback = NarrationKey(
                NarrationId.AVL_CORRECT_PIVOT,
                listOf(correct, signed(factor)),
            ),
            hintLadder = listOf(
                NarrationKey(NarrationId.AVL_HINT_PIVOT),
                NarrationKey(NarrationId.AVL_RETRY_PIVOT_ASK),
            ),
            // Reading the balance factors is arithmetic the app already printed;
            // finding the one that broke, and finding the *lowest* one, is not.
            autoInTry = false,
        )
    }

    private fun riserDecision(state: AvlState, pivot: Int): Decision<AvlAction> {
        val order = state.tree.inorder()
        val correct = state.riserFor(pivot) ?: pivot
        val shape = state.shapeAt(pivot)
        val child = state.tree.searchPath(requireNotNull(state.justInserted)).let { path ->
            path.getOrNull(path.indexOf(pivot) + 1)
        }
        val subtree = state.tree.subtree(pivot)

        return Decision(
            kind = DecisionKind.CELL,
            prompt = NarrationKey(NarrationId.AVL_ASK_RISER, listOf(pivot)),
            options = order.mapIndexed { slot, value ->
                ActionOption<AvlAction>(
                    action = AvlAction.Raise(value),
                    label = NarrationKey(NarrationId.AVL_OPTION_NODE, listOf(value)),
                    slot = slot,
                )
            },
            correct = AvlAction.Raise(correct),
            focus = listOf(order.indexOf(correct)),
            hint = NarrationKey(
                if (shape?.isDouble == true) {
                    NarrationId.AVL_HINT_RISER_BENT
                } else {
                    NarrationId.AVL_HINT_RISER_STRAIGHT
                },
                listOf(pivot),
            ),
            guidance = listOf(
                NarrationKey(NarrationId.AVL_RETRY_RISER_LOOK, listOf(pivot)),
                NarrationKey(
                    if (shape?.isDouble == true) {
                        NarrationId.AVL_RETRY_RISER_ASK_BENT
                    } else {
                        NarrationId.AVL_RETRY_RISER_ASK_STRAIGHT
                    },
                    listOf(pivot),
                ),
                NarrationKey(
                    if (shape?.isDouble == true) {
                        NarrationId.AVL_RETRY_RISER_EXPLAIN_BENT
                    } else {
                        NarrationId.AVL_RETRY_RISER_EXPLAIN_STRAIGHT
                    },
                    listOf(pivot, correct, child ?: correct),
                ),
            ),
            minimalFeedback = NarrationKey(NarrationId.AVL_RETRY_RISER_LOOK, listOf(pivot)),
            // Each wrong tap is named for the misconception it is: the pivot
            // itself, the child when the path bends (the big one), the grandchild
            // when it does not, or a node outside the subtree entirely.
            whyWrong = buildMap {
                for (value in order) {
                    if (value == correct) continue
                    val id = when {
                        value == pivot -> NarrationId.AVL_WHY_PIVOT_ITSELF
                        value !in subtree -> NarrationId.AVL_WHY_OUTSIDE
                        value == child -> NarrationId.AVL_WHY_CHILD_NOT_ENOUGH
                        else -> NarrationId.AVL_WHY_NOT_ON_THE_PATH
                    }
                    put(
                        AvlAction.Raise(value),
                        NarrationKey(id, listOf(value, pivot, correct)),
                    )
                }
            },
            correctFeedback = NarrationKey(
                if (shape?.isDouble == true) {
                    NarrationId.AVL_CORRECT_RISER_BENT
                } else {
                    NarrationId.AVL_CORRECT_RISER_STRAIGHT
                },
                listOf(correct, pivot),
            ),
            hintLadder = listOf(
                NarrationKey(
                    if (shape?.isDouble == true) {
                        NarrationId.AVL_HINT_RISER_BENT
                    } else {
                        NarrationId.AVL_HINT_RISER_STRAIGHT
                    },
                    listOf(pivot),
                ),
            ),
            autoInTry = false,
        )
    }

    // -- Transitions ----------------------------------------------------------

    override fun apply(state: AvlState, action: AvlAction): Transition<AvlState> = when (action) {
        AvlAction.Insert -> insert(state)
        is AvlAction.Pivot -> choosePivot(state, action.node)
        is AvlAction.Raise -> chooseRiser(state, action.node)
        AvlAction.Rotate -> rotate(state)
    }

    private fun insert(state: AvlState): Transition<AvlState> {
        val value = state.pending.firstOrNull() ?: return unchanged(state)
        val grown = state.tree.insert(value)
        val next = state.copy(
            tree = grown,
            pending = state.pending.drop(1),
            justInserted = value,
            pivot = null,
            riser = null,
            lastRotation = null,
        )
        val slot = grown.inorder().indexOf(value)
        return Transition(
            next = next,
            events = buildList {
                add(VizEvent.Insert(value, slot))
                add(VizEvent.Examine(listOf(slot), ExamineRole.CANDIDATE))
                add(VizEvent.Meter(MeterId.DISTANCE, grown.height.toLong()))
                if (next.finished) add(VizEvent.Terminal(Outcome.Completed(true)))
            },
            narration = NarrationKey(
                if (next.unbalanced == null) {
                    NarrationId.AVL_INSERTED_BALANCED
                } else {
                    NarrationId.AVL_INSERTED_BROKE_IT
                },
                listOf(value, state.tree.parentOfInsertion(value), next.unbalanced ?: value),
            ),
            correct = true,
        )
    }

    /**
     * Naming a node that is not the one out of balance changes nothing.
     *
     * The same refusal Two Pointers gives a false "pair found" and the BST gives
     * a false "found": a claim that is not true is not a state the algorithm can
     * be in. In Try it never gets this far — a `Retry` carries no action — and
     * refusing here is what keeps the machine strictly progressing under *any*
     * sequence of actions, which is what makes the termination guarantee real.
     */
    private fun choosePivot(state: AvlState, node: Int): Transition<AvlState> {
        val slot = state.tree.inorder().indexOf(node)
        if (state.unbalanced != node) return refuse(state, slot)
        return Transition(
            next = state.copy(pivot = node),
            events = listOf(VizEvent.Examine(listOf(slot), ExamineRole.COMPARING)),
            narration = NarrationKey(
                NarrationId.AVL_PIVOT_CHOSEN,
                listOf(node, signed(state.tree.balanceFactor(node))),
            ),
            correct = true,
        )
    }

    private fun chooseRiser(state: AvlState, node: Int): Transition<AvlState> {
        val pivot = state.pivot ?: return unchanged(state)
        val slot = state.tree.inorder().indexOf(node)
        if (state.riserFor(pivot) != node) return refuse(state, slot)

        val plan = state.planFor(pivot)
        return Transition(
            next = state.copy(riser = node, plan = plan),
            events = listOf(VizEvent.Examine(listOf(slot), ExamineRole.CANDIDATE)),
            narration = NarrationKey(
                if (plan.size > 1) NarrationId.AVL_RISER_DOUBLE else NarrationId.AVL_RISER_SINGLE,
                listOf(node, pivot),
            ),
            correct = true,
        )
    }

    private fun rotate(state: AvlState): Transition<AvlState> {
        val step = state.plan.firstOrNull() ?: return unchanged(state)
        val rotated = when (step.kind) {
            RotationKind.LEFT -> state.tree.rotateLeft(step.at)
            RotationKind.RIGHT -> state.tree.rotateRight(step.at)
        }
        val remaining = state.plan.drop(1)
        val done = remaining.isEmpty()

        val next = state.copy(
            tree = rotated,
            plan = remaining,
            rotations = state.rotations + 1,
            lastRotation = step,
            // The repair is finished, so the round is: the highlight clears and
            // the next value can go in.
            justInserted = if (done) null else state.justInserted,
            pivot = if (done) null else state.pivot,
            riser = if (done) null else state.riser,
        )

        val slot = rotated.inorder().indexOf(step.at)
        return Transition(
            next = next,
            events = buildList {
                add(VizEvent.Examine(listOf(slot), ExamineRole.INSPECTING))
                // The subtree that was just repaired is provably legal again.
                if (done) add(VizEvent.Finalize(slot..slot))
                add(VizEvent.Meter(MeterId.DISTANCE, rotated.height.toLong()))
                if (next.finished) add(VizEvent.Terminal(Outcome.Completed(true)))
            },
            narration = NarrationKey(
                when {
                    !done -> NarrationId.AVL_ROTATED_FIRST_HALF
                    step.kind == RotationKind.RIGHT -> NarrationId.AVL_ROTATED_RIGHT
                    else -> NarrationId.AVL_ROTATED_LEFT
                },
                listOf(step.at),
            ),
            correct = true,
        )
    }

    private fun unchanged(state: AvlState) =
        Transition(state, emptyList(), null, correct = false)

    private fun refuse(state: AvlState, slot: Int) = Transition(
        next = state,
        events = if (slot >= 0) {
            listOf(VizEvent.Examine(listOf(slot), ExamineRole.INSPECTING))
        } else {
            emptyList()
        },
        narration = null,
        correct = false,
    )

    private fun signed(factor: Int): String = if (factor > 0) "+$factor" else "$factor"
}

/** Where a value landed, for the narration that names the BST rule doing its work. */
private fun BinaryTree.parentOfInsertion(value: Int): Int =
    searchPath(value).lastOrNull { it != value } ?: value
