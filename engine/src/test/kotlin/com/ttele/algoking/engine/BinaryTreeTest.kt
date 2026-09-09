package com.ttele.algoking.engine

import com.ttele.algoking.engine.core.BinaryTree
import com.ttele.algoking.engine.core.TreeNode
import com.ttele.algoking.engine.dataset.TreeDatasets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shared tree infrastructure, tested once for every lesson that stands on it.
 *
 * The distinction this file exists to protect: the **structural** operations are
 * correct for any binary tree, and the **search-tree** ones are correct only while
 * the ordering invariant holds. Three traversal lessons walk trees that are not
 * ordered, so mixing the two halves up would produce a lesson that silently fails
 * to find nodes that are on screen.
 */
class BinaryTreeTest {

    private val balanced = TreeDatasets.teachingTree
    private val unordered = TreeDatasets.tryTree

    private val leftOnly = BinaryTree(TreeNode(4, left = TreeNode(3, left = TreeNode(2))))
    private val rightOnly = BinaryTree(TreeNode(1, right = TreeNode(2, right = TreeNode(3))))

    // ── Traversals ───────────────────────────────────────────────────────────

    @Test
    fun `the three traversals of the teaching tree`() {
        assertEquals(listOf(20, 30, 40, 50, 60, 70, 80), balanced.inorder())
        assertEquals(listOf(50, 30, 20, 40, 70, 60, 80), balanced.preorder())
        assertEquals(listOf(20, 40, 30, 60, 80, 70, 50), balanced.postorder())
    }

    @Test
    fun `the three traversals of a tree that is not a search tree`() {
        // Inorder here is NOT sorted, which is the entire point of this dataset.
        assertEquals(listOf(2, 4, 7, 9, 5), unordered.inorder())
        assertFalse(unordered.inorder() == unordered.inorder().sorted())
        assertEquals(listOf(7, 4, 2, 9, 5), unordered.preorder())
        assertEquals(listOf(2, 4, 5, 9, 7), unordered.postorder())
    }

    @Test
    fun `every traversal visits every node exactly once`() {
        for (tree in listOf(balanced, unordered, leftOnly, rightOnly)) {
            for (order in listOf(tree.inorder(), tree.preorder(), tree.postorder())) {
                assertEquals(tree.size, order.size)
                assertEquals(order.toSet().size, order.size)
            }
        }
    }

    @Test
    fun `traversals of an empty tree are empty, and of one node are that node`() {
        assertEquals(emptyList<Int>(), BinaryTree.EMPTY.inorder())
        assertEquals(emptyList<Int>(), BinaryTree.EMPTY.preorder())
        assertEquals(emptyList<Int>(), BinaryTree.EMPTY.postorder())

        val one = BinaryTree.of(42)
        assertEquals(listOf(42), one.inorder())
        assertEquals(listOf(42), one.preorder())
        assertEquals(listOf(42), one.postorder())
    }

    @Test
    fun `a chain traverses in the order its shape dictates`() {
        // 4(3(2)) — every node has only a left child.
        assertEquals(listOf(2, 3, 4), leftOnly.inorder())
        assertEquals(listOf(4, 3, 2), leftOnly.preorder())
        assertEquals(listOf(2, 3, 4), leftOnly.postorder())
        // 1(-, 2(-, 3)) — every node has only a right child.
        assertEquals(listOf(1, 2, 3), rightOnly.inorder())
        assertEquals(listOf(1, 2, 3), rightOnly.preorder())
        assertEquals(listOf(3, 2, 1), rightOnly.postorder())
    }

    @Test
    fun `traversal depends on shape alone, never on values`() {
        // Relabel every node through a map that scrambles the ordering, and each
        // traversal must visit the same *positions* — the output is the
        // relabelled sequence, never a re-sorted one. This is the assertion that
        // proves inorder is not sorting.
        fun relabel(node: TreeNode?): TreeNode? = node?.let {
            TreeNode(-it.value * 7, relabel(it.left), relabel(it.right))
        }

        for (tree in listOf(balanced, unordered, leftOnly, rightOnly)) {
            val scrambled = BinaryTree(relabel(tree.root))
            assertEquals(tree.inorder().map { -it * 7 }, scrambled.inorder())
            assertEquals(tree.preorder().map { -it * 7 }, scrambled.preorder())
            assertEquals(tree.postorder().map { -it * 7 }, scrambled.postorder())
        }
    }

    // ── Structural lookups ───────────────────────────────────────────────────

    @Test
    fun `structural lookups work on a tree that is not ordered`() {
        // The trap this file exists to catch: `node()` descends by comparison, so
        // on an unordered tree it misses values that are plainly there. The
        // structural half does not.
        assertNull("node() should miss it — the tree is not ordered", unordered.node(5))
        assertEquals(5, unordered.findNode(5)?.value)
        assertTrue(unordered.holds(5))
        assertFalse(unordered.contains(5))

        assertEquals(listOf(7, 9, 5), unordered.pathToNode(5))
        assertEquals(9, unordered.parentByStructure(5))
        assertEquals(listOf(4, 9), unordered.childrenOf(7))
        assertEquals(listOf(2), unordered.childrenOf(4))
        assertEquals(listOf(5), unordered.childrenOf(9))
        assertEquals(emptyList<Int>(), unordered.childrenOf(2))
    }

    @Test
    fun `structural lookups agree with the search-tree ones on an ordered tree`() {
        for (value in balanced.inorder()) {
            assertEquals(balanced.node(value), balanced.findNode(value))
            assertEquals(balanced.searchPath(value), balanced.pathToNode(value))
            assertEquals(balanced.parentOf(value), balanced.parentByStructure(value))
        }
    }

    @Test
    fun `a missing value has no node, no path and no parent`() {
        assertNull(unordered.findNode(99))
        assertFalse(unordered.holds(99))
        assertEquals(emptyList<Int>(), unordered.pathToNode(99))
        assertNull(unordered.parentByStructure(99))
        assertEquals(emptyList<Int>(), unordered.childrenOf(99))
        assertNull(BinaryTree.EMPTY.findNode(1))
        assertEquals(emptyList<Int>(), BinaryTree.EMPTY.pathToNode(1))
    }

    @Test
    fun `the root has no parent and every other node has one`() {
        assertNull(unordered.parentByStructure(7))
        for (value in unordered.inorder() - 7) {
            assertTrue(unordered.parentByStructure(value) != null)
        }
    }

    // ── Layout ───────────────────────────────────────────────────────────────

    @Test
    fun `an unordered tree still lays out as a tree`() {
        val layout = unordered.layout()
        assertEquals(unordered.size, layout.size)
        assertEquals(layout.map { it.x }.toSet().size, layout.size)
        for ((parent, child) in unordered.edges()) {
            val p = layout.first { it.value == parent }
            val c = layout.first { it.value == child }
            assertTrue("$parent should sit above $child", p.y < c.y)
            // A left child is drawn to the left of its parent whatever its value
            // — the layout reads the shape, not the numbers.
            val isLeftChild = unordered.findNode(parent)?.left?.value == child
            if (isLeftChild) assertTrue(c.x < p.x) else assertTrue(c.x > p.x)
        }
    }

    @Test
    fun `nodes on one row are never adjacent columns`() {
        for (tree in listOf(balanced, unordered, leftOnly, rightOnly)) {
            val layout = tree.layout()
            val columns = layout.withIndex().associate { (i, node) -> node.value to i }
            for (a in layout) {
                for (b in layout) {
                    if (a.value == b.value || a.depth != b.depth) continue
                    val gap = kotlin.math.abs(columns.getValue(a.value) - columns.getValue(b.value))
                    assertTrue("${a.value} and ${b.value} share a row $gap apart", gap >= 2)
                }
            }
        }
    }
}
