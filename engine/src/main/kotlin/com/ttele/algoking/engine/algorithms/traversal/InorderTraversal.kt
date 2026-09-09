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
 * # Inorder — LEFT → NODE → RIGHT
 *
 * ```
 * inorder(node):
 *     inorder(node.left)
 *     visit(node)
 *     inorder(node.right)
 * ```
 *
 * A node is visited **between** its two subtrees: everything to its left comes
 * out first, then it, then everything to its right. So the traversal walks all
 * the way down the left spine before it emits anything at all, and every node
 * spends time on the stack waiting for its left side to finish.
 *
 * On a **search tree** that produces sorted order — but that is a fact about the
 * tree, not about the traversal, and TRY proves it by running exactly this rule
 * over a tree whose values are in no order at all.
 */
object InorderRule : TraversalRule {

    override val id = AlgorithmId.TREE_INORDER

    /** **The algorithm.** Left subtree, then the node, then the right subtree. */
    override val order = listOf(Step.LEFT, Step.VISIT, Step.RIGHT)

    override val copy = TraversalCopy(
        ask = NarrationId.INORDER_ASK,
        hint = NarrationId.INORDER_HINT,
        retryLook = NarrationId.INORDER_RETRY_LOOK,
        retryAsk = NarrationId.INORDER_RETRY_ASK,
        retryExplain = NarrationId.INORDER_RETRY_EXPLAIN,
        whyAlreadyVisited = NarrationId.INORDER_WHY_VISITED,
        whyNotAdjacent = NarrationId.INORDER_WHY_NOT_ADJACENT,
        // The line the lesson exists for: a node is not ready until everything
        // to its left is out.
        whyNodeTooEarly = NarrationId.INORDER_WHY_LEFT_FIRST,
        whyChildTooEarly = NarrationId.INORDER_WHY_NODE_BEFORE_RIGHT,
        whyWrongChild = NarrationId.INORDER_WHY_LEFT_BEFORE_RIGHT,
        onVisit = NarrationId.INORDER_ON_VISIT,
        onDescend = NarrationId.INORDER_ON_DESCEND,
        onDescendVisit = NarrationId.INORDER_ON_DESCEND_VISIT,
        onReturn = NarrationId.INORDER_ON_RETURN,
        correctVisit = NarrationId.INORDER_CORRECT_VISIT,
        correctDescend = NarrationId.INORDER_CORRECT_DESCEND,
    )
}

/**
 * The Inorder walkthrough.
 *
 * The **left subtree is narrated in full** and the right one is collapsed — the
 * rule ADR-025 set for the sorts, for the same reason: by the time the traversal
 * reaches 70 the learner has watched the pattern three times, and a fourth full
 * narration is patience rather than understanding.
 *
 * The three questions the brief asked to be answered are beats 1, 3 and 5: *why
 * 20 comes before 30* (the walk down happens before anything is emitted), *why
 * the traversal returns to 30* (20 is finished and 30 is still owed its visit),
 * and *why 40 comes after 30* (the right subtree is last).
 */
class InorderWatchNarrator : WatchNarrator<TreeWalkState> {

    override fun opening(state: TreeWalkState, scene: Scene): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.SETUP,
            scene = scene,
            headline = NarrationKey(NarrationId.INORDER_WATCH_SETUP),
            support = NarrationKey(NarrationId.INORDER_WATCH_SETUP_SUPPORT),
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
                            NarrationId.INORDER_WATCH_DOWN_AND_VISIT
                        } else {
                            NarrationId.INORDER_WATCH_DOWN
                        },
                        listOf(move.to, move.from),
                    ),
                    support = NarrationKey(
                        if (emitted) {
                            // The base case, and the answer to "why is 20 first".
                            NarrationId.INORDER_WATCH_DOWN_AND_VISIT_WHY
                        } else {
                            NarrationId.INORDER_WATCH_DOWN_WHY
                        },
                        listOf(move.to),
                    ),
                ),
            )

            is TraversalMove.Visit -> listOf(
                PartialStep(
                    kind = WatchStepKind.FOUND,
                    scene = scene,
                    headline = NarrationKey(NarrationId.INORDER_WATCH_VISIT, listOf(move.node)),
                    support = NarrationKey(
                        NarrationId.INORDER_WATCH_VISIT_WHY,
                        listOf(move.node),
                    ),
                ),
            )

            is TraversalMove.Return -> {
                // A return is a beat when it lands somewhere that still owes
                // something — which for inorder means the parent has not been
                // emitted yet, and is the answer to "why does it come back to
                // 30". A return onto a node that is already out, or the unwinding
                // at the end of the run, has nothing to say and gets no step:
                // the lesson ends where the traversal ends (ADR-034).
                val to = move.to ?: return emptyList()
                if (state.isVisited(to)) return emptyList()
                listOf(
                    PartialStep(
                        kind = WatchStepKind.EXAMINE,
                        scene = scene,
                        headline = NarrationKey(
                            NarrationId.INORDER_WATCH_RETURN,
                            listOf(move.from, to),
                        ),
                        support = NarrationKey(
                            NarrationId.INORDER_WATCH_RETURN_OWED,
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
            headline = NarrationKey(NarrationId.INORDER_WATCH_INSIGHT),
            support = NarrationKey(
                NarrationId.INORDER_WATCH_INSIGHT_SUPPORT,
                listOf(state.visited.joinToString(", ")),
            ),
        ),
        PartialStep(
            kind = WatchStepKind.SUMMARY,
            scene = scene,
            headline = NarrationKey(
                NarrationId.INORDER_WATCH_SUMMARY,
                listOf(state.visited.joinToString("  →  ")),
            ),
            support = NarrationKey(NarrationId.INORDER_WATCH_SUMMARY_SUPPORT),
            bullets = listOf(
                NarrationKey(NarrationId.INORDER_IDEA_1),
                NarrationKey(NarrationId.INORDER_IDEA_2),
                NarrationKey(NarrationId.INORDER_IDEA_3),
                NarrationKey(NarrationId.INORDER_IDEA_4),
            ),
        ),
    )
}
