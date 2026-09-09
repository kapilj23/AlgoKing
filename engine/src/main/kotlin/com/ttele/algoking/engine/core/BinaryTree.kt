package com.ttele.algoking.engine.core

/**
 * One node of a binary tree.
 *
 * Not of a *search* tree: the traversal lessons walk trees whose values are in no
 * particular order, and a node is the same thing either way. Whether the ordering
 * invariant holds is a property of the tree, and [BinaryTree] says which of its
 * operations depend on it.
 *
 * A node is a **value plus two references**, and nothing else. There is no parent
 * pointer, no depth field and no cached size: every one of those would be a second
 * copy of a relationship the shape already carries, and a second copy is something
 * that can disagree with the first.
 *
 * Immutable, like every other piece of engine state — [BinaryTree.insert] returns a
 * new tree rather than mutating this one, so a tree can be a value inside an
 * algorithm state and rewind stays a matter of popping a stack (ADR-001).
 */
data class TreeNode(
    val value: Int,
    val left: TreeNode? = null,
    val right: TreeNode? = null,
)

/**
 * A binary tree — the shared structure behind every tree lesson.
 *
 * ### Two kinds of operation, and the difference matters
 *
 * **Structural.** [size], [height], [inorder], [preorder], [postorder], [edges],
 * [depths], [layout], [findNode], [pathToNode], [parentByStructure] — these read
 * the shape and nothing else. They are correct for **any** binary tree, which is
 * what the traversal lessons need: a traversal is a rule about structure, and the
 * values could be anything at all.
 *
 * **Search-tree.** [node], [contains], [searchPath], [insert], [subtree],
 * [parentOf], [heightAt], [balanceFactor], [rotateLeft], [rotateRight] and
 * [avlInsert] descend **by comparing values**, so they are correct only while the
 * ordering invariant holds:
 *
 * ```
 * every value in the LEFT subtree  <  node value  <  every value in the RIGHT subtree
 * ```
 *
 * That invariant is what makes [searchPath] a *search*: one comparison rules out
 * an entire subtree, so it follows one path from the root instead of looking at
 * every value. On a tree that is not ordered, those methods will quietly fail to
 * find nodes that are there — so a lesson working on an arbitrary tree must use
 * the structural half. Each one is marked below.
 *
 * ### What lives here and what does not
 *
 * This is the **data structure**, so it holds the shape and the operations the
 * shape defines, and knows nothing about lessons, scenes, steps or narration. The
 * algorithms drive their walks one decision at a time for the learner; the helpers
 * here are how a *caller* checks such a walk against the structure, and they are
 * what the tests assert against.
 *
 * Values are assumed unique across a tree — a lesson identifies a node by its
 * value, because that is what the learner taps.
 */
data class BinaryTree(val root: TreeNode? = null) {

    val isEmpty: Boolean get() = root == null

    /** How many nodes the tree holds. */
    val size: Int get() = count(root)

    /** Levels from the root, inclusive. An empty tree is 0, a single node is 1. */
    val height: Int get() = heightOf(root)

    /**
     * The node holding [value], found the way the structure says to find it —
     * by descending, never by scanning. Null when the value is not in the tree.
     */
    fun node(value: Int): TreeNode? {
        var current = root
        while (current != null) {
            current = when {
                value == current.value -> return current
                value < current.value -> current.left
                else -> current.right
            }
        }
        return null
    }

    fun contains(value: Int): Boolean = node(value) != null

    /**
     * The path a BST search follows looking for [target], root first.
     *
     * It ends **on** the target when the tree holds it, and on the last node
     * visited before the walk ran off the end of the tree when it does not — so
     * `searchPath(65)` on the teaching tree is `[50, 70, 60]` while
     * `contains(65)` is false. A search that proves absence is a completed
     * search, not a failed one.
     *
     * This is the reference the lesson is checked against: the algorithm generates
     * its path from real state one decision at a time, and a test asserts the two
     * agree. Nothing in the lesson reads its answer from here.
     */
    fun searchPath(target: Int): List<Int> = buildList {
        var current = root
        while (current != null) {
            add(current.value)
            if (target == current.value) return@buildList
            current = if (target < current.value) current.left else current.right
        }
    }

    /**
     * The tree with [value] added in its ordered place, or this tree unchanged if
     * it is already present.
     *
     * The BST lesson teaches search, so nothing in it calls this at runtime — but
     * insertion is the operation that *defines* where a value belongs, and the
     * model would be dishonest without it. It is also the seam a future insert
     * lesson plugs into: an algorithm and a narrator, over this.
     */
    fun insert(value: Int): BinaryTree = BinaryTree(insertInto(root, value))

    // -- Traversals — structural, correct for any binary tree ------------------
    //
    // The reference implementations, written the way the textbook writes them, so
    // the three traversal lessons have something independent to be checked
    // against. A lesson generates its order one learner decision at a time; these
    // generate it by recursion, and a test asserts they agree.

    /**
     * **LEFT → NODE → RIGHT.**
     *
     * On a search tree this comes out sorted — but that is a fact about the tree,
     * not about the traversal. On an unordered binary tree it is simply the order
     * the nodes sit in from left to right.
     */
    fun inorder(): List<Int> = buildList { collectInorder(root, this) }

    /** **NODE → LEFT → RIGHT.** The node is emitted on the way *down*. */
    fun preorder(): List<Int> = buildList { collectPreorder(root, this) }

    /** **LEFT → RIGHT → NODE.** A node is emitted only once nothing is left below it. */
    fun postorder(): List<Int> = buildList { collectPostorder(root, this) }

    // -- Structural lookups ----------------------------------------------------
    //
    // The same questions [node], [searchPath] and [parentOf] answer, asked of a
    // tree that may not be ordered. They walk the whole shape rather than
    // descending by comparison, so they cost O(n) instead of O(h) — which is the
    // right trade for a lesson that has already drawn every node on screen.

    /** The node holding [value] anywhere in the tree, ordered or not. */
    fun findNode(value: Int): TreeNode? = searchStructure(root, value)

    /** True when [value] is anywhere in the tree. Ordering not required. */
    fun holds(value: Int): Boolean = findNode(value) != null

    /**
     * The path from the root down to [value], inclusive — empty when it is absent.
     *
     * This is what tells a traversal lesson whether a tapped node is a child of
     * where it is standing, and what a return unwinds.
     */
    fun pathToNode(value: Int): List<Int> = buildList {
        if (!collectPath(root, value, this)) clear()
    }

    /** The parent of [value] by structure, or null for the root and absent values. */
    fun parentByStructure(value: Int): Int? =
        pathToNode(value).let { path -> path.getOrNull(path.lastIndex - 1) }

    /** The children of [value], left first, skipping the ones that are not there. */
    fun childrenOf(value: Int): List<Int> =
        findNode(value)?.let { listOfNotNull(it.left?.value, it.right?.value) }.orEmpty()

    // -- Balance ---------------------------------------------------------------
    //
    // A plain BST does not care how lopsided it gets; an AVL tree does, and these
    // are what it measures itself with. They live here rather than in the AVL
    // lesson because they are properties of a tree, not of an algorithm.

    /** Height of the subtree rooted at [value]. 0 when the value is not present. */
    fun heightAt(value: Int): Int = heightOf(node(value))

    /**
     * `height(left) - height(right)` for the subtree rooted at [value].
     *
     * **The one place balance is measured.** Positive means left-heavy, negative
     * right-heavy, and AVL's whole rule is that this stays within ±1: the moment
     * a node reaches ±2 it has to be rotated back.
     */
    fun balanceFactor(value: Int): Int = node(value)
        ?.let { heightOf(it.left) - heightOf(it.right) }
        ?: 0

    /** Every node's balance factor, so a picture can show all of them at once. */
    fun balanceFactors(): Map<Int, Int> = inorder().associateWith { balanceFactor(it) }

    /** True when every node is within ±1 — the AVL invariant. */
    val isBalanced: Boolean get() = inorder().all { balanceFactor(it) in -1..1 }

    /** The parent of [value], or null for the root and for absent values. */
    fun parentOf(value: Int): Int? {
        var current = root ?: return null
        var parent: Int? = null
        while (current.value != value) {
            parent = current.value
            current = (if (value < current.value) current.left else current.right) ?: return null
        }
        return parent
    }

    /**
     * Rotate the subtree rooted at [value] to the left: its **right child comes
     * up** and takes its place. Unchanged when there is no right child to raise.
     *
     * ```
     *    a                b
     *     \              / \
     *      b     ->     a   c
     *       \            \
     *        c            (b's old left)
     * ```
     *
     * A rotation re-hangs three links and touches nothing else, which is why
     * rebalancing costs O(1) — and, crucially, it **preserves in-order**: the
     * values still read left to right in exactly the same sequence, so the result
     * is still a search tree. That is the property the lesson is built on.
     */
    fun rotateLeft(at: Int): BinaryTree {
        val node = node(at) ?: return this
        val riser = node.right ?: return this
        val rotated = riser.copy(left = node.copy(right = riser.left))
        return BinaryTree(replaceSubtree(root, at, rotated))
    }

    /** The mirror of [rotateLeft]: the **left child comes up**. */
    fun rotateRight(at: Int): BinaryTree {
        val node = node(at) ?: return this
        val riser = node.left ?: return this
        val rotated = riser.copy(right = node.copy(left = riser.right))
        return BinaryTree(replaceSubtree(root, at, rotated))
    }

    /**
     * Insert [value] and rebalance — the reference AVL insertion.
     *
     * Nothing in the lesson calls this: the lesson inserts with [insert] and then
     * has the *learner* find the imbalance and name the rotation, which is the
     * entire point of it. This exists so the tests can check what the learner
     * produced against an independent implementation of what AVL should produce.
     */
    fun avlInsert(value: Int): BinaryTree = BinaryTree(avlInsertInto(root, value))

    /**
     * Every value in the subtree rooted at [value], including it. Empty when the
     * value is not in the tree.
     *
     * This is what a comparison rules out: choosing a direction discards the other
     * child's whole subtree in one move.
     */
    fun subtree(value: Int): Set<Int> = node(value)?.let { collectSubtree(it) }.orEmpty()

    /** Parent → child, one entry per edge. */
    fun edges(): List<Pair<Int, Int>> = buildList { collectEdges(root, this) }

    /** Depth of every value, root at 0. */
    fun depths(): Map<Int, Int> = buildMap { collectDepths(root, 0, this) }

    /**
     * Where each node sits, normalised 0..1 — **derived from the shape of the
     * tree, never authored.**
     *
     * `x` is the node's position in **in-order** sequence and `y` is its depth.
     * In-order is what makes the picture a tree rather than a tangle: every node
     * sits horizontally between its two subtrees, so a parent is always drawn
     * above and between its children, and no two nodes can collide because every
     * node has an in-order position of its own.
     *
     * It is also the truest available picture of the invariant — left to right on
     * screen is ascending order, which is exactly what the BST property says.
     *
     * A graph authors its positions (`GraphNode.x`) because five nodes have a
     * shape a person should choose. A tree's shape is *implied by its data*, so
     * choosing it by hand would let the drawing and the structure disagree.
     */
    fun layout(): List<TreePosition> {
        val order = inorder()
        if (order.isEmpty()) return emptyList()
        val depths = depths()
        val lastX = (order.size - 1).coerceAtLeast(1)
        val lastY = (height - 1).coerceAtLeast(1)
        return order.mapIndexed { index, value ->
            val depth = depths[value] ?: 0
            TreePosition(
                value = value,
                depth = depth,
                // A lone node has no spread to divide, so it is centred rather
                // than pinned to an edge by a division that means nothing.
                x = if (order.size == 1) 0.5f else index.toFloat() / lastX,
                y = if (height <= 1) 0.5f else depth.toFloat() / lastY,
            )
        }
    }

    // -- Recursion, kept private so the shape above stays the interface --------

    private fun count(node: TreeNode?): Int =
        if (node == null) 0 else 1 + count(node.left) + count(node.right)

    private fun heightOf(node: TreeNode?): Int =
        if (node == null) 0 else 1 + maxOf(heightOf(node.left), heightOf(node.right))

    private fun insertInto(node: TreeNode?, value: Int): TreeNode = when {
        node == null -> TreeNode(value)
        value < node.value -> node.copy(left = insertInto(node.left, value))
        value > node.value -> node.copy(right = insertInto(node.right, value))
        // Already there. The lesson's values are unique, and a duplicate insert
        // is a no-op rather than a second node or an exception.
        else -> node
    }

    /**
     * Swap the subtree rooted at [at] for [with].
     *
     * Descends by BST order, which is safe precisely because a rotation does not
     * change it: the path to the old subtree root is still the path to the new one.
     */
    private fun replaceSubtree(node: TreeNode?, at: Int, with: TreeNode): TreeNode? = when {
        node == null -> null
        node.value == at -> with
        at < node.value -> node.copy(left = replaceSubtree(node.left, at, with))
        else -> node.copy(right = replaceSubtree(node.right, at, with))
    }

    /** Textbook AVL insertion: place by BST order, then rebalance on the way up. */
    private fun avlInsertInto(node: TreeNode?, value: Int): TreeNode {
        if (node == null) return TreeNode(value)
        val grown = when {
            value < node.value -> node.copy(left = avlInsertInto(node.left, value))
            value > node.value -> node.copy(right = avlInsertInto(node.right, value))
            else -> return node
        }
        return rebalance(grown, value)
    }

    private fun rebalance(node: TreeNode, inserted: Int): TreeNode {
        val balance = heightOf(node.left) - heightOf(node.right)
        return when {
            balance > 1 && inserted < requireNotNull(node.left).value -> rotateRightAt(node)
            balance < -1 && inserted > requireNotNull(node.right).value -> rotateLeftAt(node)
            balance > 1 -> rotateRightAt(node.copy(left = rotateLeftAt(requireNotNull(node.left))))
            balance < -1 -> rotateLeftAt(node.copy(right = rotateRightAt(requireNotNull(node.right))))
            else -> node
        }
    }

    private fun rotateLeftAt(node: TreeNode): TreeNode {
        val riser = node.right ?: return node
        return riser.copy(left = node.copy(right = riser.left))
    }

    private fun rotateRightAt(node: TreeNode): TreeNode {
        val riser = node.left ?: return node
        return riser.copy(right = node.copy(left = riser.right))
    }

    private fun collectInorder(node: TreeNode?, into: MutableList<Int>) {
        node ?: return
        collectInorder(node.left, into)
        into += node.value
        collectInorder(node.right, into)
    }

    private fun collectPreorder(node: TreeNode?, into: MutableList<Int>) {
        node ?: return
        into += node.value
        collectPreorder(node.left, into)
        collectPreorder(node.right, into)
    }

    private fun collectPostorder(node: TreeNode?, into: MutableList<Int>) {
        node ?: return
        collectPostorder(node.left, into)
        collectPostorder(node.right, into)
        into += node.value
    }

    private fun searchStructure(node: TreeNode?, value: Int): TreeNode? = when {
        node == null -> null
        node.value == value -> node
        else -> searchStructure(node.left, value) ?: searchStructure(node.right, value)
    }

    /** Depth-first, recording the route; returns false and leaves nothing behind. */
    private fun collectPath(node: TreeNode?, value: Int, into: MutableList<Int>): Boolean {
        node ?: return false
        into += node.value
        if (node.value == value) return true
        if (collectPath(node.left, value, into)) return true
        if (collectPath(node.right, value, into)) return true
        into.removeAt(into.lastIndex)
        return false
    }

    private fun collectSubtree(node: TreeNode): Set<Int> = buildSet {
        add(node.value)
        node.left?.let { addAll(collectSubtree(it)) }
        node.right?.let { addAll(collectSubtree(it)) }
    }

    private fun collectEdges(node: TreeNode?, into: MutableList<Pair<Int, Int>>) {
        node ?: return
        node.left?.let {
            into += node.value to it.value
            collectEdges(it, into)
        }
        node.right?.let {
            into += node.value to it.value
            collectEdges(it, into)
        }
    }

    private fun collectDepths(node: TreeNode?, depth: Int, into: MutableMap<Int, Int>) {
        node ?: return
        into[node.value] = depth
        collectDepths(node.left, depth + 1, into)
        collectDepths(node.right, depth + 1, into)
    }

    companion object {
        val EMPTY = BinaryTree(null)

        /**
         * A tree built by inserting [values] in the order given.
         *
         * Insertion order decides the shape, which is the honest way to author a
         * tree: `of(50, 30, 70, 20, 40, 60, 80)` is balanced, and
         * `of(20, 30, 40, 50)` is the skewed chain that makes search O(n).
         */
        fun of(vararg values: Int): BinaryTree =
            values.fold(EMPTY) { tree, value -> tree.insert(value) }
    }
}

/** Where one node is drawn. [x] and [y] are normalised 0..1 — see [BinaryTree.layout]. */
data class TreePosition(
    val value: Int,
    val depth: Int,
    val x: Float,
    val y: Float,
)
