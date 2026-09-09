package com.ttele.algoking.engine.algorithms.traversal

import com.ttele.algoking.engine.core.AlgorithmId
import com.ttele.algoking.engine.core.BinaryTree
import com.ttele.algoking.engine.decision.Action
import com.ttele.algoking.engine.narration.NarrationId

/**
 * One of the three things a traversal does at every node.
 *
 * **The order of these three is the entire difference between the three lessons.**
 * Inorder is `LEFT, VISIT, RIGHT`; preorder is `VISIT, LEFT, RIGHT`; postorder is
 * `LEFT, RIGHT, VISIT`. Each lesson states its own on one line, in its own file.
 */
enum class Step {
    /** Go into the left subtree. Skipped when there is no left child. */
    LEFT,

    /** Emit this node — the only step that produces output. */
    VISIT,

    /** Go into the right subtree. Skipped when there is no right child. */
    RIGHT,
}

/**
 * A node on the call stack, and how many of its steps are behind it.
 *
 * [done] indexes into the lesson's [TraversalRule.order]: 0 means nothing has
 * happened at this node yet, 3 means it is finished and the traversal returns.
 * This is a recursive call frame, written down as data — which is what lets the
 * lesson show a call stack without ever recursing.
 */
data class WalkFrame(val node: Int, val done: Int)

/** What the traversal just did, so the picture and the copy can say it. */
sealed interface TraversalMove {

    /** Went from [from] into its [side] child, [to]. */
    data class Descend(val from: Int, val to: Int, val side: Step) : TraversalMove

    /** Emitted [node] to the output. */
    data class Visit(val node: Int) : TraversalMove

    /** Finished [from] and returned to [to]. Null [to] means the run is over. */
    data class Return(val from: Int, val to: Int?) : TraversalMove
}

/**
 * What the learner can do.
 *
 * **One gesture: tap the node the traversal touches next.** Whether that tap means
 * *go into it* or *visit it* is decided by which node it is — the node itself is a
 * visit, a child is a descent — so the learner never has to say which kind of
 * touch they meant, and the same gesture serves all three lessons (ADR-038).
 */
sealed interface TraversalAction : Action {

    /** Whatever the traversal does next with [node]. */
    data class Touch(val node: Int) : TraversalAction

    /**
     * Go back to the parent. **Mechanical** — once a node's steps are all done
     * there is nothing else the traversal could do, so charging the learner a tap
     * for it would be charging them for bookkeeping (PRODUCT_SPEC.md §3).
     */
    data object Return : TraversalAction
}

/**
 * Immutable state, shared by all three traversals.
 *
 * [visited] is an ordered list because it **is** the traversal — the thing the
 * lesson produces — exactly as `DfsState.visited` is. [stack] is the call stack a
 * recursive implementation would build, and the two together are everything the
 * screen shows.
 */
data class TreeWalkState(
    val tree: BinaryTree,
    val stack: List<WalkFrame>,
    val visited: List<Int>,
    val lastMove: TraversalMove?,
) {
    /** The node the traversal is standing on. */
    val current: Int? get() = stack.lastOrNull()?.node

    /** Where a return would go. Null at the root. */
    val parent: Int? get() = stack.getOrNull(stack.lastIndex - 1)?.node

    /** Empty stack means every node has been finished. */
    val finished: Boolean get() = stack.isEmpty()

    fun isVisited(node: Int): Boolean = node in visited

    /** The children of the node the traversal is standing on. */
    val currentChildren: List<Int>
        get() = current?.let { tree.childrenOf(it) }.orEmpty()

    /** True when [node] is somewhere on the stack — reached, not yet finished. */
    fun isPending(node: Int): Boolean = stack.any { it.node == node }
}

/**
 * What one traversal lesson is.
 *
 * A rule is [order] plus the words that lesson says. The engine below reads both
 * and nothing else, so it never learns which traversal it is running — and each
 * lesson's rule is one readable line in its own file rather than a value passed in
 * from somewhere the reader has to go and find.
 */
interface TraversalRule {
    val id: AlgorithmId

    /** **The algorithm.** Three steps, in this lesson's order. */
    val order: List<Step>

    /** Everything this lesson says. One field per moment it has to speak. */
    val copy: TraversalCopy
}

/**
 * One traversal lesson's script.
 *
 * Every field is a moment the lesson has to say something in its own words — the
 * question, the ladder, and one line for each way a tap can be wrong. Naming them
 * is what keeps a lesson's voice readable in one place instead of scattered
 * through a shared `when`.
 */
data class TraversalCopy(
    /** The question, asked at every beat. */
    val ask: NarrationId,
    val hint: NarrationId,
    val retryLook: NarrationId,
    val retryAsk: NarrationId,
    val retryExplain: NarrationId,

    // -- The five ways a tap can be wrong ------------------------------------
    /** They tapped a node that is already in the output. */
    val whyAlreadyVisited: NarrationId,
    /** They tapped somewhere the traversal cannot reach in one step. */
    val whyNotAdjacent: NarrationId,
    /** They tapped this node while a subtree of it is still unfinished. */
    val whyNodeTooEarly: NarrationId,
    /** They went into a child while this node itself was owed a visit. */
    val whyChildTooEarly: NarrationId,
    /** They took the right subtree while the left one was still owed. */
    val whyWrongChild: NarrationId,

    // -- What just happened ---------------------------------------------------
    val onVisit: NarrationId,
    val onDescend: NarrationId,
    /** Moved into a node and visited it in the same beat — a leaf, or preorder. */
    val onDescendVisit: NarrationId,
    val onReturn: NarrationId,
    val correctVisit: NarrationId,
    val correctDescend: NarrationId,
)
