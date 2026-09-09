package com.ttele.algoking.engine.algorithms.bst

import com.ttele.algoking.engine.core.Algorithm
import com.ttele.algoking.engine.core.AlgorithmId
import com.ttele.algoking.engine.core.BinaryTree
import com.ttele.algoking.engine.core.BstNode
import com.ttele.algoking.engine.core.Dataset
import com.ttele.algoking.engine.core.Probe
import com.ttele.algoking.engine.core.Transition
import com.ttele.algoking.engine.decision.Action
import com.ttele.algoking.engine.decision.ActionOption
import com.ttele.algoking.engine.decision.Decision
import com.ttele.algoking.engine.decision.DecisionKind
import com.ttele.algoking.engine.event.EliminateReason
import com.ttele.algoking.engine.event.ExamineRole
import com.ttele.algoking.engine.event.MarkId
import com.ttele.algoking.engine.event.MeterId
import com.ttele.algoking.engine.event.Outcome
import com.ttele.algoking.engine.event.PointerId
import com.ttele.algoking.engine.event.Relation
import com.ttele.algoking.engine.event.VizEvent
import com.ttele.algoking.engine.narration.NarrationId
import com.ttele.algoking.engine.narration.NarrationKey

/**
 * What the learner can do in a BST search.
 *
 * Two beats per node, the shape Binary Search and Two Pointers both use: **the
 * app states the comparison**, then **the learner decides where the search goes.**
 * Reading `60 > 50` is not a judgement; knowing that greater means RIGHT is the
 * entire technique, so it is always the learner's (PRODUCT_SPEC.md §3).
 */
sealed interface BstAction : Action {

    /** Compare the target with the current node. Mechanical — nothing to choose. */
    data object Compare : BstAction

    /** The target is smaller, so it can only be in the left subtree. */
    data object GoLeft : BstAction

    /** The target is larger, so it can only be in the right subtree. */
    data object GoRight : BstAction

    /** `target == current`. */
    data object Found : BstAction
}

/**
 * Immutable state — the single source of truth for the whole lesson.
 *
 * Everything the screen shows is read from here: which node is current, the
 * comparison, the search path, what has been ruled out and whether the search
 * ended in a node or in a null child. Nothing about a BST is decided anywhere
 * else, and in particular no Composable ever compares a target with a value.
 *
 * [comparison] doubles as the beat flag: null means the current node has not been
 * read yet, non-null means the comparison is on screen and the learner owes a
 * decision. It mirrors `BinarySearchState.mid` and `TwoPointersState.sum`.
 */
data class BstState(
    val tree: BinaryTree,
    val target: Int,
    /** The node the search is standing on. Null once the walk ran off the tree. */
    val current: Int?,
    /**
     * The search path, root first — **generated**, never authored.
     *
     * `[50, 70, 60]` is not written down anywhere in the lesson; it is what this
     * list holds after the learner has made three correct decisions.
     */
    val path: List<Int>,
    /** Values in subtrees a comparison has ruled out. They stay drawn, and dimmed. */
    val eliminated: Set<Int>,
    /** Null until [BstAction.Compare] has read the current node. */
    val comparison: Relation?,
    val foundValue: Int?,
    /** True once the search reached a null child: the target is not in the tree. */
    val missing: Boolean,
) {
    val currentNode: BstNode? get() = current?.let { tree.node(it) }

    /**
     * How [target] stands against the current node — **the one place the BST
     * decision rule is evaluated.** Null when there is no node to compare with.
     */
    val relation: Relation?
        get() = current?.let { value ->
            when {
                target < value -> Relation.LESS
                target > value -> Relation.GREATER
                else -> Relation.EQUAL
            }
        }

    val finished: Boolean get() = foundValue != null || missing

    /** How many comparisons the search has made so far. */
    val depth: Int get() = path.size

    /** Nodes neither visited nor ruled out — still possible. */
    val remaining: Int get() = tree.size - eliminated.size - path.size

    /** The child a direction leads to, or null when the branch is empty. */
    fun child(left: Boolean): Int? =
        currentNode?.let { if (left) it.left?.value else it.right?.value }
}

/**
 * Binary Search Tree search — an Advanced lesson.
 *
 * ```
 * target < node   ->  go LEFT    (every larger value is on the other side)
 * target > node   ->  go RIGHT   (every smaller value is on the other side)
 * target == node  ->  found
 * no child there  ->  not in the tree
 * ```
 *
 * ### Why it is sound, which is the actual lesson
 *
 * The BST invariant — *left subtree < node < right subtree* — is doing all the
 * work. When the target is greater than the node, every value in the left subtree
 * is smaller than the node and therefore smaller than the target, so the target
 * cannot possibly be there. One comparison does not rule out one value; it rules
 * out an entire subtree.
 *
 * That is the same trade Binary Search makes on a sorted array, and the reason
 * this lesson follows it: Binary Search re-derives the middle by arithmetic every
 * round, while a BST has the halving *built into the structure* — the node tells
 * you which way to go, so nothing has to be computed.
 *
 * ### Complexity, stated honestly
 *
 * The search visits one node per level, so it costs the **height** of the tree:
 * O(log n) on a balanced tree, and O(n) on a skewed one, where a BST degenerates
 * into a linked list. The lesson says both — claiming O(log n) for every BST is
 * the most common thing said wrongly about them.
 */
class BinarySearchTreeAlgorithm : Algorithm<BstState, BstAction> {

    override val id = AlgorithmId.BINARY_SEARCH_TREE

    override fun initial(dataset: Dataset): BstState {
        val tree = dataset.tree ?: BinaryTree.EMPTY
        val target = requireNotNull(dataset.target) { "BST search needs a target." }
        return BstState(
            tree = tree,
            target = target,
            current = tree.root?.value,
            // The root is on the path before anything is compared: the search is
            // already standing on it, which is what "start at the root" means.
            path = listOfNotNull(tree.root?.value),
            eliminated = emptySet(),
            comparison = null,
            foundValue = null,
            // An empty tree is not an error. There is nowhere to look, and "not
            // in the tree" is the correct answer rather than a crash.
            missing = tree.isEmpty,
        )
    }

    override fun probe(state: BstState): Probe<BstAction> {
        state.foundValue?.let { return Probe.Terminal(Outcome.Found(slotOf(state, it))) }
        if (state.missing || state.current == null) return Probe.Terminal(Outcome.NotFound)

        // Beat 1 — read the node. Comparing two numbers is not a judgement, so
        // the app does it and says the result out loud.
        if (state.comparison == null) return Probe.Mechanical(BstAction.Compare)

        // Beat 2 — the decision that carries the algorithm.
        return Probe.Decide(moveDecision(state))
    }

    // -- The decision ---------------------------------------------------------

    private fun moveDecision(state: BstState): Decision<BstAction> {
        val node = requireNotNull(state.current)
        val relation = requireNotNull(state.relation)
        val target = state.target

        val correct: BstAction = when (relation) {
            Relation.LESS -> BstAction.GoLeft
            Relation.GREATER -> BstAction.GoRight
            Relation.EQUAL -> BstAction.Found
        }

        // All three options, every round — including when FOUND is wrong.
        // Offering FOUND only where it happens to be correct would answer the
        // question the beat exists to ask: *is this the node?* (ADR-032.)
        val options = listOf<ActionOption<BstAction>>(
            ActionOption(BstAction.GoLeft, key(NarrationId.BST_OPTION_LEFT)),
            ActionOption(BstAction.GoRight, key(NarrationId.BST_OPTION_RIGHT)),
            ActionOption(BstAction.Found, key(NarrationId.BST_OPTION_FOUND)),
        )

        // What a correct move discards, named so the feedback can say it out loud.
        val discarded = ruledOutBy(state, goingLeft = relation == Relation.LESS)

        return Decision(
            kind = DecisionKind.OPTIONS,
            prompt = NarrationKey(NarrationId.BST_ASK_WHICH_WAY, listOf(target, node)),
            options = options,
            correct = correct,
            focus = listOf(slotOf(state, node)),
            hint = NarrationKey(NarrationId.BST_HINT_RULE, listOf(target, node)),
            // Least to most explicit, and the last rung still leaves the learner
            // to act. Rung 2 asks the reasoning question — "which side of a BST
            // holds the larger values?" is the thought that turns the rule into
            // understanding, and answering it is what rung 3 is for.
            guidance = listOf(
                NarrationKey(NarrationId.BST_RETRY_LOOK, listOf(target, node)),
                NarrationKey(
                    when (relation) {
                        Relation.LESS -> NarrationId.BST_RETRY_ASK_SMALLER
                        Relation.GREATER -> NarrationId.BST_RETRY_ASK_LARGER
                        Relation.EQUAL -> NarrationId.BST_RETRY_ASK_EQUAL
                    },
                    listOf(target, node),
                ),
                NarrationKey(
                    when (relation) {
                        Relation.LESS -> NarrationId.BST_RETRY_EXPLAIN_LEFT
                        Relation.GREATER -> NarrationId.BST_RETRY_EXPLAIN_RIGHT
                        Relation.EQUAL -> NarrationId.BST_RETRY_EXPLAIN_FOUND
                    },
                    listOf(target, node),
                ),
            ),
            minimalFeedback = NarrationKey(
                when (relation) {
                    Relation.LESS -> NarrationId.BST_COMPARED_LESS
                    Relation.GREATER -> NarrationId.BST_COMPARED_GREATER
                    Relation.EQUAL -> NarrationId.BST_COMPARED_EQUAL
                },
                listOf(target, node),
            ),
            // Why *this* option cannot be right. Each line names the invariant on
            // the side the learner reached for, so the feedback teaches the rule
            // rather than reporting a verdict — "❌ Wrong" is forbidden
            // (PRODUCT_SPEC.md §5).
            whyWrong = buildMap {
                if (correct != BstAction.GoLeft) {
                    put(
                        BstAction.GoLeft,
                        NarrationKey(
                            if (relation == Relation.EQUAL) {
                                NarrationId.BST_WHY_ALREADY_HERE
                            } else {
                                NarrationId.BST_WHY_LEFT_IMPOSSIBLE
                            },
                            listOf(target, node),
                        ),
                    )
                }
                if (correct != BstAction.GoRight) {
                    put(
                        BstAction.GoRight,
                        NarrationKey(
                            if (relation == Relation.EQUAL) {
                                NarrationId.BST_WHY_ALREADY_HERE
                            } else {
                                NarrationId.BST_WHY_RIGHT_IMPOSSIBLE
                            },
                            listOf(target, node),
                        ),
                    )
                }
                if (correct != BstAction.Found) {
                    put(
                        BstAction.Found,
                        NarrationKey(NarrationId.BST_WHY_NOT_THIS_NODE, listOf(target, node)),
                    )
                }
            },
            // What their right answer achieved: not "correct", but which subtree
            // just left the search.
            correctFeedback = NarrationKey(
                when {
                    relation == Relation.EQUAL -> NarrationId.BST_CORRECT_FOUND
                    discarded.isEmpty() && relation == Relation.LESS ->
                        NarrationId.BST_CORRECT_LEFT_EMPTY

                    discarded.isEmpty() -> NarrationId.BST_CORRECT_RIGHT_EMPTY
                    relation == Relation.LESS -> NarrationId.BST_CORRECT_LEFT
                    else -> NarrationId.BST_CORRECT_RIGHT
                },
                listOf(target, node, discarded.size, state.depth),
            ),
            hintLadder = listOf(
                NarrationKey(NarrationId.BST_HINT_RULE, listOf(target, node)),
                NarrationKey(
                    when (relation) {
                        Relation.LESS -> NarrationId.BST_RETRY_ASK_SMALLER
                        Relation.GREATER -> NarrationId.BST_RETRY_ASK_LARGER
                        Relation.EQUAL -> NarrationId.BST_RETRY_ASK_EQUAL
                    },
                    listOf(target, node),
                ),
                NarrationKey(
                    when (relation) {
                        Relation.LESS -> NarrationId.BST_RETRY_EXPLAIN_LEFT
                        Relation.GREATER -> NarrationId.BST_RETRY_EXPLAIN_RIGHT
                        Relation.EQUAL -> NarrationId.BST_RETRY_EXPLAIN_FOUND
                    },
                    listOf(target, node),
                ),
            ),
            // Which way to go **is** the lesson. The app never answers it in Try.
            autoInTry = false,
        )
    }

    // -- Transitions ----------------------------------------------------------

    override fun apply(state: BstState, action: BstAction): Transition<BstState> = when (action) {
        BstAction.Compare -> compare(state)
        BstAction.GoLeft -> move(state, goingLeft = true)
        BstAction.GoRight -> move(state, goingLeft = false)
        BstAction.Found -> found(state)
    }

    private fun compare(state: BstState): Transition<BstState> {
        // Total rather than defensive: with no node there is nothing to read, so
        // the state comes back untouched and `probe` terminates on the next look.
        val relation = state.relation ?: return unchanged(state)
        val node = requireNotNull(state.current)
        val slot = slotOf(state, node)

        return Transition(
            next = state.copy(comparison = relation),
            events = listOf(
                VizEvent.Examine(listOf(slot), ExamineRole.COMPARING),
                // Both operands are the node: the target is a badge rather than a
                // position, the way it is in Binary Search.
                VizEvent.Compare(slot, slot, relation),
                VizEvent.Meter(MeterId.DISTANCE, state.depth.toLong()),
            ),
            narration = NarrationKey(
                when (relation) {
                    Relation.LESS -> NarrationId.BST_COMPARED_LESS
                    Relation.GREATER -> NarrationId.BST_COMPARED_GREATER
                    Relation.EQUAL -> NarrationId.BST_COMPARED_EQUAL
                },
                listOf(state.target, node),
            ),
            correct = true,
        )
    }

    /**
     * Descend one level.
     *
     * Applying the *wrong* direction is the same code path with a different
     * argument — that is what makes validation a pure comparison rather than a
     * special case (ARCHITECTURE.md §6.1). In Try nothing ever calls it that way,
     * because a `Retry` carries no action, so a learner's mistake cannot move the
     * search. The state it produces is still a legal search state either way: one
     * level deeper, or off the tree.
     */
    private fun move(state: BstState, goingLeft: Boolean): Transition<BstState> {
        val node = state.current ?: return unchanged(state)
        val relation = state.relation
        val correct = when {
            goingLeft -> relation == Relation.LESS
            else -> relation == Relation.GREATER
        }

        val child = state.child(goingLeft)
        // The other side of the tree leaves the search entirely — that is the
        // whole idea, and it is why one comparison is worth so much.
        val discarded = ruledOutBy(state, goingLeft)
        val eliminated = state.eliminated + discarded

        val next = state.copy(
            current = child,
            path = if (child != null) state.path + child else state.path,
            eliminated = eliminated,
            comparison = null,
            // A null child is the answer to "is it here?", not a crash: the
            // search has proved the value is absent.
            missing = child == null,
        )

        return Transition(
            next = next,
            events = buildList {
                // A subtree is a *contiguous run of the in-order sequence*, which
                // is what makes `Eliminate` honest here: the range that leaves is
                // exactly the range of sorted positions that can no longer hold
                // the target — the same event Binary Search emits for a half.
                slotRangeOf(state, discarded)?.let { range ->
                    add(
                        VizEvent.Eliminate(
                            range,
                            if (goingLeft) EliminateReason.TOO_LARGE else EliminateReason.TOO_SMALL,
                        ),
                    )
                }
                child?.let { add(VizEvent.MovePointer(PointerId.MID, slotOf(next, it))) }
                add(VizEvent.Meter(MeterId.REMAINING, next.remaining.coerceAtLeast(0).toLong()))
                if (next.missing) add(VizEvent.Terminal(Outcome.NotFound))
            },
            narration = NarrationKey(
                when {
                    next.missing && goingLeft -> NarrationId.BST_NO_LEFT_CHILD
                    next.missing -> NarrationId.BST_NO_RIGHT_CHILD
                    goingLeft -> NarrationId.BST_MOVED_LEFT
                    else -> NarrationId.BST_MOVED_RIGHT
                },
                listOf(node, child ?: state.target, state.target),
            ),
            correct = correct,
        )
    }

    private fun found(state: BstState): Transition<BstState> {
        val node = state.current ?: return unchanged(state)
        // Claiming a node that does not match changes nothing. The validator is
        // what returns the learner to the same decision.
        if (state.relation != Relation.EQUAL) {
            return Transition(
                next = state,
                events = listOf(
                    VizEvent.Examine(listOf(slotOf(state, node)), ExamineRole.INSPECTING),
                ),
                narration = null,
                correct = false,
            )
        }

        val slot = slotOf(state, node)
        return Transition(
            next = state.copy(foundValue = node),
            events = listOf(
                VizEvent.Finalize(slot..slot),
                VizEvent.Mark(slot, MarkId.TARGET),
                VizEvent.Terminal(Outcome.Found(slot)),
            ),
            narration = NarrationKey(
                NarrationId.BST_FOUND,
                listOf(node, state.depth),
            ),
            correct = true,
        )
    }

    private fun unchanged(state: BstState) =
        Transition(state, emptyList(), null, correct = false)

    // -- Helpers --------------------------------------------------------------

    /**
     * The subtree a move away from [goingLeft] discards — the *other* child's
     * whole subtree. The node itself is not discarded: the search stood on it,
     * and it stays on the path.
     */
    private fun ruledOutBy(state: BstState, goingLeft: Boolean): Set<Int> {
        val abandoned = state.child(!goingLeft) ?: return emptySet()
        return state.tree.subtree(abandoned)
    }

    /**
     * A node's slot: its position in **in-order** sequence.
     *
     * The same index the projector lays out from, so the events, the scene and
     * the decision all mean the same thing by "slot 3".
     */
    private fun slotOf(state: BstState, value: Int): Int =
        state.tree.inorder().indexOf(value)

    /** The contiguous in-order range a set of subtree values occupies. */
    private fun slotRangeOf(state: BstState, values: Set<Int>): IntRange? {
        if (values.isEmpty()) return null
        val order = state.tree.inorder()
        val slots = values.mapNotNull { value -> order.indexOf(value).takeIf { it >= 0 } }
        if (slots.isEmpty()) return null
        return slots.min()..slots.max()
    }

    private fun key(id: NarrationId) = NarrationKey(id)
}
