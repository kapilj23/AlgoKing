package com.ttele.algoking.engine.algorithms.traversal

import com.ttele.algoking.engine.core.Algorithm
import com.ttele.algoking.engine.core.BinaryTree
import com.ttele.algoking.engine.core.Dataset
import com.ttele.algoking.engine.core.Probe
import com.ttele.algoking.engine.core.Transition
import com.ttele.algoking.engine.decision.ActionOption
import com.ttele.algoking.engine.decision.Decision
import com.ttele.algoking.engine.decision.DecisionKind
import com.ttele.algoking.engine.event.ExamineRole
import com.ttele.algoking.engine.event.MeterId
import com.ttele.algoking.engine.event.Outcome
import com.ttele.algoking.engine.event.VizEvent
import com.ttele.algoking.engine.narration.NarrationKey

/**
 * The traversal machine, shared by all three lessons.
 *
 * It walks a tree with an explicit stack of [WalkFrame]s — the recursion a
 * textbook implementation would leave to the language, written down as data so it
 * can be drawn, stepped and rewound. At every frame it takes the lesson's
 * [TraversalRule.order] and performs whichever of `LEFT`, `VISIT` and `RIGHT`
 * comes next.
 *
 * **This class never learns which traversal it is running.** It reads an order and
 * a script; the order lives in `InorderTraversal.kt`, `PreorderTraversal.kt` and
 * `PostorderTraversal.kt`, one line each, which is where a reader looks for it.
 * That is the same shape `LinearStructureAlgorithm(flavour)` gives Stack and Queue
 * (ADR-027), and the same reasoning: one machine, and the difference stated where
 * the lesson is.
 *
 * ### Complexity, and where the lesson gets it from
 *
 * Every node is pushed once, has its three steps performed once, and is popped
 * once — **O(n) time**. The stack holds one frame per level of the path currently
 * being walked, so it is **O(h) space**, and on a skewed tree `h` is `n`. The
 * lesson can say both because the stack is on screen doing it.
 */
class TreeTraversalAlgorithm(
    private val rule: TraversalRule,
) : Algorithm<TreeWalkState, TraversalAction> {

    override val id = rule.id

    override fun initial(dataset: Dataset): TreeWalkState {
        val tree = dataset.tree ?: BinaryTree.EMPTY
        return TreeWalkState(
            tree = tree,
            // The root goes on the stack before anything happens — a traversal
            // always starts there, and where it starts is given rather than
            // chosen. An empty tree gets an empty stack and is finished.
            stack = tree.root?.let { listOf(WalkFrame(it.value, done = 0)) }.orEmpty(),
            visited = emptyList(),
            lastMove = null,
        )
    }

    override fun probe(state: TreeWalkState): Probe<TraversalAction> {
        val frame = state.stack.lastOrNull()
            ?: return Probe.Terminal(Outcome.Completed(true))

        // Nothing left to do at this node: return to the parent. Mechanical — see
        // TraversalAction.Return.
        val step = nextStepIndex(state, frame) ?: return Probe.Mechanical(TraversalAction.Return)

        return Probe.Decide(touchDecision(state, frame, step))
    }

    // -- The rule, applied ----------------------------------------------------

    /**
     * The index into [TraversalRule.order] of the next step that actually does
     * something, or null when the frame is finished.
     *
     * A `LEFT` step over a node with no left child is not a beat — there is
     * nothing to see and nothing to decide — so it is skipped here rather than
     * being turned into a step the learner has to tap through. `VISIT` is always
     * actionable.
     */
    private fun nextStepIndex(state: TreeWalkState, frame: WalkFrame): Int? {
        val node = state.tree.findNode(frame.node) ?: return null
        for (index in frame.done until rule.order.size) {
            val actionable = when (rule.order[index]) {
                Step.LEFT -> node.left != null
                Step.RIGHT -> node.right != null
                Step.VISIT -> true
            }
            if (actionable) return index
        }
        return null
    }

    /** The node a step wants the learner to tap. */
    private fun targetOf(state: TreeWalkState, frame: WalkFrame, stepIndex: Int): Int? {
        val node = state.tree.findNode(frame.node) ?: return null
        return when (rule.order[stepIndex]) {
            Step.LEFT -> node.left?.value
            Step.RIGHT -> node.right?.value
            Step.VISIT -> node.value
        }
    }

    // -- The decision ---------------------------------------------------------

    private fun touchDecision(
        state: TreeWalkState,
        frame: WalkFrame,
        stepIndex: Int,
    ): Decision<TraversalAction> {
        val order = state.tree.inorder()
        val current = frame.node
        val correct = requireNotNull(targetOf(state, frame, stepIndex))
        val expected = rule.order[stepIndex]

        return Decision(
            kind = DecisionKind.CELL,
            prompt = NarrationKey(rule.copy.ask, listOf(current)),
            // Every node is tappable. Offering only the legal ones would outline
            // the answer, and a wrong tap is where most of the teaching is.
            options = order.mapIndexed { slot, value ->
                ActionOption<TraversalAction>(
                    action = TraversalAction.Touch(value),
                    label = NarrationKey(rule.copy.ask, listOf(value)),
                    slot = slot,
                )
            },
            correct = TraversalAction.Touch(correct),
            focus = listOf(order.indexOf(current)),
            hint = NarrationKey(rule.copy.hint, listOf(current)),
            guidance = listOf(
                NarrationKey(rule.copy.retryLook, listOf(current)),
                NarrationKey(rule.copy.retryAsk, listOf(current)),
                NarrationKey(rule.copy.retryExplain, listOf(current, correct)),
            ),
            minimalFeedback = NarrationKey(rule.copy.retryLook, listOf(current)),
            whyWrong = buildMap {
                for (value in order) {
                    if (value == correct) continue
                    put(
                        TraversalAction.Touch(value),
                        NarrationKey(
                            reasonFor(state, current, expected, value),
                            listOf(value, current, correct),
                        ),
                    )
                }
            },
            correctFeedback = NarrationKey(
                if (expected == Step.VISIT) rule.copy.correctVisit else rule.copy.correctDescend,
                listOf(correct, current),
            ),
            hintLadder = listOf(
                NarrationKey(rule.copy.hint, listOf(current)),
                NarrationKey(rule.copy.retryAsk, listOf(current)),
            ),
            // Which node comes next *is* the traversal. Try must always ask it.
            autoInTry = false,
        )
    }

    /**
     * Which of the five ways a tap can be wrong this one is.
     *
     * The classification is the engine's — it can see the stack — while the
     * sentence is the lesson's. That split is what lets postorder answer a tap on
     * the parent with *"both subtrees first"* and preorder answer a tap on the
     * child with *"the node comes first"*, from one code path.
     */
    private fun reasonFor(
        state: TreeWalkState,
        current: Int,
        expected: Step,
        tapped: Int,
    ) = when {
        state.isVisited(tapped) -> rule.copy.whyAlreadyVisited
        tapped != current && tapped !in state.currentChildren -> rule.copy.whyNotAdjacent
        // They reached for this node while one of its subtrees is still owed.
        tapped == current -> rule.copy.whyNodeTooEarly
        // They went into a child while the node itself was owed a visit.
        expected == Step.VISIT -> rule.copy.whyChildTooEarly
        // They took one subtree while the other was still owed.
        else -> rule.copy.whyWrongChild
    }

    // -- Transitions ----------------------------------------------------------

    override fun apply(
        state: TreeWalkState,
        action: TraversalAction,
    ): Transition<TreeWalkState> = when (action) {
        is TraversalAction.Touch -> touch(state, action.node)
        TraversalAction.Return -> returnToParent(state)
    }

    private fun touch(state: TreeWalkState, node: Int): Transition<TreeWalkState> {
        val frame = state.stack.lastOrNull() ?: return unchanged(state)
        val stepIndex = nextStepIndex(state, frame) ?: return unchanged(state)
        val slot = state.tree.inorder().indexOf(node)

        // Touching anything other than what the rule asks for is not a state the
        // traversal can be in, so it comes back unchanged — the same refusal the
        // BST gives a false "found". In Try it never gets this far, because a
        // `Retry` carries no action (ARCHITECTURE.md §6.1).
        if (node != targetOf(state, frame, stepIndex)) return refuse(state, slot)

        val advanced = frame.copy(done = stepIndex + 1)
        val rest = state.stack.dropLast(1)

        return if (rule.order[stepIndex] == Step.VISIT) {
            val next = state.copy(
                stack = rest + advanced,
                visited = state.visited + node,
                lastMove = TraversalMove.Visit(node),
            )
            Transition(
                next = next,
                events = buildList {
                    add(VizEvent.Finalize(slot..slot))
                    add(VizEvent.Meter(MeterId.REMAINING, (next.tree.size - next.visited.size).toLong()))
                },
                narration = NarrationKey(rule.copy.onVisit, listOf(node, next.visited.size)),
                correct = true,
            )
        } else {
            // Moving into a node performs whatever that node then *owes with no
            // choice attached*: if its first actionable step is its own visit —
            // a leaf in any order, every node in preorder — the visit happens in
            // the same beat.
            //
            // Without this the learner taps the same node twice in a row, once to
            // arrive and once to visit, and the second tap has exactly one legal
            // target. That is the gesture-teaching trap PRODUCT_SPEC.md §3 warns
            // about, and it would put it at every leaf in every lesson.
            var child = WalkFrame(node, done = 0)
            val childStep = nextStepIndex(state.copy(stack = rest + advanced + child), child)
            val visitsOnArrival = childStep != null && rule.order[childStep] == Step.VISIT
            if (visitsOnArrival) child = child.copy(done = requireNotNull(childStep) + 1)

            val next = state.copy(
                stack = rest + advanced + child,
                visited = if (visitsOnArrival) state.visited + node else state.visited,
                lastMove = TraversalMove.Descend(frame.node, node, rule.order[stepIndex]),
            )
            Transition(
                next = next,
                events = buildList {
                    add(VizEvent.Examine(listOf(slot), ExamineRole.COMPARING))
                    if (visitsOnArrival) add(VizEvent.Finalize(slot..slot))
                    add(
                        VizEvent.Meter(
                            MeterId.REMAINING,
                            (next.tree.size - next.visited.size).toLong(),
                        ),
                    )
                },
                narration = NarrationKey(
                    if (visitsOnArrival) rule.copy.onDescendVisit else rule.copy.onDescend,
                    listOf(node, frame.node, next.visited.size),
                ),
                correct = true,
            )
        }
    }

    private fun returnToParent(state: TreeWalkState): Transition<TreeWalkState> {
        val frame = state.stack.lastOrNull() ?: return unchanged(state)
        val rest = state.stack.dropLast(1)
        val next = state.copy(
            stack = rest,
            lastMove = TraversalMove.Return(frame.node, rest.lastOrNull()?.node),
        )
        return Transition(
            next = next,
            events = buildList {
                if (next.finished) add(VizEvent.Terminal(Outcome.Completed(true)))
            },
            narration = NarrationKey(
                rule.copy.onReturn,
                listOf(frame.node, rest.lastOrNull()?.node ?: frame.node),
            ),
            correct = true,
        )
    }

    private fun unchanged(state: TreeWalkState) =
        Transition(state, emptyList(), null, correct = false)

    private fun refuse(state: TreeWalkState, slot: Int) = Transition(
        next = state,
        events = if (slot >= 0) {
            listOf(VizEvent.Examine(listOf(slot), ExamineRole.INSPECTING))
        } else {
            emptyList()
        },
        narration = null,
        correct = false,
    )
}
