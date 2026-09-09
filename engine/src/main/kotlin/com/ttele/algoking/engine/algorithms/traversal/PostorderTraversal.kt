package com.ttele.algoking.engine.algorithms.traversal

import com.ttele.algoking.engine.core.AlgorithmId
import com.ttele.algoking.engine.core.Frame
import com.ttele.algoking.engine.event.Metrics
import com.ttele.algoking.engine.narration.NarrationId
import com.ttele.algoking.engine.narration.NarrationKey
import com.ttele.algoking.engine.scene.Scene
import com.ttele.algoking.engine.walkthrough.PartialStep
import com.ttele.algoking.engine.walkthrough.WatchNarrator
import com.ttele.algoking.engine.walkthrough.WatchStepKind

/**
 * # Postorder — LEFT → RIGHT → NODE
 *
 * ```
 * postorder(node):
 *     postorder(node.left)
 *     postorder(node.right)
 *     visit(node)
 * ```
 *
 * A node is visited **last**, after *both* of its subtrees are completely done.
 * Children first, parent last — so the root is the final node out, and every
 * parent sits waiting on the stack while everything beneath it is emitted.
 *
 * That is why postorder is the order you delete a tree in, or evaluate an
 * expression tree in: by the time you reach a node, everything it depends on has
 * already been dealt with.
 *
 * It is the most conceptually interesting of the three, and the walkthrough is
 * written around the waiting rather than around the visits.
 */
object PostorderRule : TraversalRule {

    override val id = AlgorithmId.TREE_POSTORDER

    /** **The algorithm.** Both subtrees, left then right, and only then the node. */
    override val order = listOf(Step.LEFT, Step.RIGHT, Step.VISIT)

    override val copy = TraversalCopy(
        ask = NarrationId.POSTORDER_ASK,
        hint = NarrationId.POSTORDER_HINT,
        retryLook = NarrationId.POSTORDER_RETRY_LOOK,
        retryAsk = NarrationId.POSTORDER_RETRY_ASK,
        retryExplain = NarrationId.POSTORDER_RETRY_EXPLAIN,
        whyAlreadyVisited = NarrationId.POSTORDER_WHY_VISITED,
        whyNotAdjacent = NarrationId.POSTORDER_WHY_NOT_ADJACENT,
        // The line the lesson exists for: both subtrees before the parent.
        whyNodeTooEarly = NarrationId.POSTORDER_WHY_CHILDREN_FIRST,
        whyChildTooEarly = NarrationId.POSTORDER_WHY_NODE_TOO_EARLY,
        whyWrongChild = NarrationId.POSTORDER_WHY_LEFT_BEFORE_RIGHT,
        onVisit = NarrationId.POSTORDER_ON_VISIT,
        onDescend = NarrationId.POSTORDER_ON_DESCEND,
        onDescendVisit = NarrationId.POSTORDER_ON_DESCEND_VISIT,
        onReturn = NarrationId.POSTORDER_ON_RETURN,
        correctVisit = NarrationId.POSTORDER_CORRECT_VISIT,
        correctDescend = NarrationId.POSTORDER_CORRECT_DESCEND,
    )
}

/**
 * The Postorder walkthrough.
 *
 * Written around the **waiting**, because that is what postorder is. The beats
 * that carry the lesson are the returns: coming back to a node and *not* visiting
 * it, because it still has a right subtree; and then coming back a second time
 * and finally visiting it.
 *
 * The shared rule — a return earns a beat when it lands on a node that still owes
 * something — produces the maximum number of beats here and none at all in
 * preorder, from the same line of code. That contrast is the point of shipping
 * the three lessons over one machine.
 */
class PostorderWatchNarrator : WatchNarrator<TreeWalkState> {

    override fun opening(state: TreeWalkState, scene: Scene): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.SETUP,
            scene = scene,
            headline = NarrationKey(NarrationId.POSTORDER_WATCH_SETUP),
            support = NarrationKey(NarrationId.POSTORDER_WATCH_SETUP_SUPPORT),
        ),
    )

    override fun onFrame(
        previous: TreeWalkState,
        frame: Frame<TreeWalkState>,
        scene: Scene,
    ): List<PartialStep> {
        val state = frame.state
        val emitted = state.visited.size > previous.visited.size

        return when (val move = state.lastMove) {
            is TraversalMove.Descend -> listOf(
                PartialStep(
                    kind = if (emitted) WatchStepKind.FOUND else WatchStepKind.EXAMINE,
                    scene = scene,
                    headline = NarrationKey(
                        if (emitted) {
                            NarrationId.POSTORDER_WATCH_DOWN_AND_VISIT
                        } else {
                            NarrationId.POSTORDER_WATCH_DOWN
                        },
                        listOf(move.to, move.from),
                    ),
                    support = NarrationKey(
                        if (emitted) {
                            // A leaf has no subtrees to wait for, so it is out at
                            // once. That is the base case of "children first".
                            NarrationId.POSTORDER_WATCH_LEAF_WHY
                        } else {
                            NarrationId.POSTORDER_WATCH_DOWN_WHY
                        },
                        listOf(move.to),
                    ),
                ),
            )

            // The payoff beat: everything below is out, so the node finally is.
            is TraversalMove.Visit -> listOf(
                PartialStep(
                    kind = WatchStepKind.FOUND,
                    scene = scene,
                    headline = NarrationKey(NarrationId.POSTORDER_WATCH_VISIT, listOf(move.node)),
                    support = NarrationKey(
                        if (state.finished || state.visited.size == state.tree.size) {
                            // The root, last of all.
                            NarrationId.POSTORDER_WATCH_VISIT_ROOT_WHY
                        } else {
                            NarrationId.POSTORDER_WATCH_VISIT_WHY
                        },
                        listOf(move.node),
                    ),
                ),
            )

            // The beats the lesson is built on: back at a node, and still not
            // visiting it.
            is TraversalMove.Return -> {
                val to = move.to ?: return emptyList()
                if (state.isVisited(to)) return emptyList()
                val owesRight = state.tree.findNode(to)?.right?.value
                    ?.let { !state.isVisited(it) } == true
                listOf(
                    PartialStep(
                        kind = WatchStepKind.EXAMINE,
                        scene = scene,
                        headline = NarrationKey(
                            NarrationId.POSTORDER_WATCH_RETURN,
                            listOf(move.from, to),
                        ),
                        support = NarrationKey(
                            if (owesRight) {
                                NarrationId.POSTORDER_WATCH_RETURN_NOT_YET
                            } else {
                                NarrationId.POSTORDER_WATCH_RETURN_NOW
                            },
                            listOf(to),
                        ),
                    ),
                )
            }

            null -> emptyList()
        }
    }

    override fun closing(
        state: TreeWalkState,
        metrics: Metrics,
        scene: Scene,
    ): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.INSIGHT,
            scene = scene,
            headline = NarrationKey(NarrationId.POSTORDER_WATCH_INSIGHT),
            support = NarrationKey(
                NarrationId.POSTORDER_WATCH_INSIGHT_SUPPORT,
                listOf(state.visited.lastOrNull() ?: 0),
            ),
        ),
        PartialStep(
            kind = WatchStepKind.SUMMARY,
            scene = scene,
            headline = NarrationKey(
                NarrationId.POSTORDER_WATCH_SUMMARY,
                listOf(state.visited.joinToString("  →  ")),
            ),
            support = NarrationKey(NarrationId.POSTORDER_WATCH_SUMMARY_SUPPORT),
            bullets = listOf(
                NarrationKey(NarrationId.POSTORDER_IDEA_1),
                NarrationKey(NarrationId.POSTORDER_IDEA_2),
                NarrationKey(NarrationId.POSTORDER_IDEA_3),
                NarrationKey(NarrationId.POSTORDER_IDEA_4),
            ),
        ),
    )
}
