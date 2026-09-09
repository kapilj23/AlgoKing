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
 * # Preorder — NODE → LEFT → RIGHT
 *
 * ```
 * preorder(node):
 *     visit(node)
 *     preorder(node.left)
 *     preorder(node.right)
 * ```
 *
 * A node is visited **the moment it is reached**, before either subtree is
 * looked at. So the root comes out first, the output grows *downward* ahead of
 * the walk, and nothing at all happens on the way back up.
 *
 * That is why preorder is the order you copy or serialise a tree in: read the
 * sequence back and you meet every parent before its children, which is exactly
 * the order you would need to rebuild it.
 */
object PreorderRule : TraversalRule {

    override val id = AlgorithmId.TREE_PREORDER

    /** **The algorithm.** The node first, then the left subtree, then the right. */
    override val order = listOf(Step.VISIT, Step.LEFT, Step.RIGHT)

    override val copy = TraversalCopy(
        ask = NarrationId.PREORDER_ASK,
        hint = NarrationId.PREORDER_HINT,
        retryLook = NarrationId.PREORDER_RETRY_LOOK,
        retryAsk = NarrationId.PREORDER_RETRY_ASK,
        retryExplain = NarrationId.PREORDER_RETRY_EXPLAIN,
        whyAlreadyVisited = NarrationId.PREORDER_WHY_VISITED,
        whyNotAdjacent = NarrationId.PREORDER_WHY_NOT_ADJACENT,
        whyNodeTooEarly = NarrationId.PREORDER_WHY_NODE_TOO_EARLY,
        // The line the lesson exists for: the root is visited before anything
        // below it is even looked at.
        whyChildTooEarly = NarrationId.PREORDER_WHY_NODE_FIRST,
        whyWrongChild = NarrationId.PREORDER_WHY_LEFT_BEFORE_RIGHT,
        onVisit = NarrationId.PREORDER_ON_VISIT,
        onDescend = NarrationId.PREORDER_ON_DESCEND,
        onDescendVisit = NarrationId.PREORDER_ON_DESCEND_VISIT,
        onReturn = NarrationId.PREORDER_ON_RETURN,
        correctVisit = NarrationId.PREORDER_CORRECT_VISIT,
        correctDescend = NarrationId.PREORDER_CORRECT_DESCEND,
    )
}

/**
 * The Preorder walkthrough.
 *
 * It opens on the payoff — **visit 50 first**, before either subtree has been
 * looked at — and then every beat is the same shape: move to a node and emit it.
 * Arriving and visiting are one moment in preorder, and the walkthrough says so
 * by never separating them.
 *
 * **No beat is emitted for a return**, and that is the lesson rather than an
 * omission: in preorder a parent is always already out by the time the traversal
 * comes back up, so the way back really does do nothing. The shared rule — a
 * return is worth a beat only when it lands on a node that still owes something —
 * produces exactly that here, and produces the opposite in postorder.
 */
class PreorderWatchNarrator : WatchNarrator<TreeWalkState> {

    override fun opening(state: TreeWalkState, scene: Scene): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.SETUP,
            scene = scene,
            headline = NarrationKey(NarrationId.PREORDER_WATCH_SETUP),
            support = NarrationKey(NarrationId.PREORDER_WATCH_SETUP_SUPPORT),
        ),
    )

    override fun onFrame(
        previous: TreeWalkState,
        frame: Frame<TreeWalkState>,
        scene: Scene,
    ): List<PartialStep> {
        val state = frame.state

        return when (val move = state.lastMove) {
            // The root: reached before anything, and out before anything.
            is TraversalMove.Visit -> listOf(
                PartialStep(
                    kind = WatchStepKind.FOUND,
                    scene = scene,
                    headline = NarrationKey(NarrationId.PREORDER_WATCH_ROOT, listOf(move.node)),
                    support = NarrationKey(NarrationId.PREORDER_WATCH_ROOT_WHY, listOf(move.node)),
                ),
            )

            is TraversalMove.Descend -> listOf(
                PartialStep(
                    kind = WatchStepKind.FOUND,
                    scene = scene,
                    headline = NarrationKey(
                        NarrationId.PREORDER_WATCH_DOWN,
                        listOf(move.to, move.from),
                    ),
                    support = NarrationKey(
                        if (move.side == Step.LEFT) {
                            NarrationId.PREORDER_WATCH_DOWN_LEFT_WHY
                        } else {
                            // Reaching a right subtree means the left one is
                            // finished, which is the only thing the way up says.
                            NarrationId.PREORDER_WATCH_DOWN_RIGHT_WHY
                        },
                        listOf(move.from, move.to),
                    ),
                ),
            )

            // Nothing happens on the way up in preorder — see the class comment.
            is TraversalMove.Return -> {
                val to = move.to ?: return emptyList()
                if (state.isVisited(to)) return emptyList()
                listOf(
                    PartialStep(
                        kind = WatchStepKind.EXAMINE,
                        scene = scene,
                        headline = NarrationKey(
                            NarrationId.PREORDER_WATCH_RETURN,
                            listOf(move.from, to),
                        ),
                        support = NarrationKey(NarrationId.PREORDER_WATCH_RETURN_WHY),
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
            headline = NarrationKey(NarrationId.PREORDER_WATCH_INSIGHT),
            support = NarrationKey(
                NarrationId.PREORDER_WATCH_INSIGHT_SUPPORT,
                listOf(state.visited.firstOrNull() ?: 0),
            ),
        ),
        PartialStep(
            kind = WatchStepKind.SUMMARY,
            scene = scene,
            headline = NarrationKey(
                NarrationId.PREORDER_WATCH_SUMMARY,
                listOf(state.visited.joinToString("  →  ")),
            ),
            support = NarrationKey(NarrationId.PREORDER_WATCH_SUMMARY_SUPPORT),
            bullets = listOf(
                NarrationKey(NarrationId.PREORDER_IDEA_1),
                NarrationKey(NarrationId.PREORDER_IDEA_2),
                NarrationKey(NarrationId.PREORDER_IDEA_3),
                NarrationKey(NarrationId.PREORDER_IDEA_4),
            ),
        ),
    )
}
