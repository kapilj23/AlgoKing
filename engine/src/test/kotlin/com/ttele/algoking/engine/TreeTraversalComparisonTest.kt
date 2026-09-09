package com.ttele.algoking.engine

import com.ttele.algoking.engine.algorithms.traversal.InorderRule
import com.ttele.algoking.engine.algorithms.traversal.PostorderRule
import com.ttele.algoking.engine.algorithms.traversal.PreorderRule
import com.ttele.algoking.engine.algorithms.traversal.Step
import com.ttele.algoking.engine.algorithms.traversal.TraversalAction
import com.ttele.algoking.engine.algorithms.traversal.TraversalRule
import com.ttele.algoking.engine.algorithms.traversal.TreeTraversalAlgorithm
import com.ttele.algoking.engine.catalog.AlgorithmCatalog
import com.ttele.algoking.engine.core.AlgorithmId
import com.ttele.algoking.engine.core.AlgorithmRunner
import com.ttele.algoking.engine.core.BinaryTree
import com.ttele.algoking.engine.core.Dataset
import com.ttele.algoking.engine.core.Probe
import com.ttele.algoking.engine.core.TreeNode
import com.ttele.algoking.engine.dataset.TreeDatasets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **One tree, three orders.**
 *
 * The reason the three traversals ship as three lessons rather than one: the data
 * is identical, the picture is identical, the gesture is identical, and the orders
 * that come out are not. This file is that claim stated as code — and it is what
 * would catch a copy-paste between the three rules.
 */
class TreeTraversalComparisonTest {

    private val rules = listOf(InorderRule, PreorderRule, PostorderRule)

    private fun walk(rule: TraversalRule, tree: BinaryTree): List<Int> {
        val runner = AlgorithmRunner(
            TreeTraversalAlgorithm(rule),
            Dataset(values = emptyList(), tree = tree),
        )
        var guard = 0
        while (guard++ < 500) {
            when (val probe = runner.probe()) {
                is Probe.Mechanical -> runner.apply(probe.action)
                is Probe.Decide -> runner.apply(probe.decision.correct)
                is Probe.Terminal -> return runner.current.state.visited
            }
        }
        error("${rule.id} did not terminate")
    }

    @Test
    fun `the same tree produces three different orders`() {
        val tree = TreeDatasets.teachingTree
        assertEquals(listOf(20, 30, 40, 50, 60, 70, 80), walk(InorderRule, tree))
        assertEquals(listOf(50, 30, 20, 40, 70, 60, 80), walk(PreorderRule, tree))
        assertEquals(listOf(20, 40, 30, 60, 80, 70, 50), walk(PostorderRule, tree))
    }

    @Test
    fun `the three orders are pairwise different on both teaching trees`() {
        for (tree in listOf(TreeDatasets.teachingTree, TreeDatasets.tryTree)) {
            val orders = rules.map { walk(it, tree) }
            assertEquals(3, orders.toSet().size)
            for (i in orders.indices) {
                for (j in i + 1 until orders.size) {
                    assertNotEquals(orders[i], orders[j])
                }
            }
        }
    }

    @Test
    fun `all three visit every node exactly once`() {
        val trees = listOf(
            TreeDatasets.teachingTree,
            TreeDatasets.tryTree,
            BinaryTree.of(42),
            BinaryTree(TreeNode(4, left = TreeNode(3, left = TreeNode(2)))),
            BinaryTree(TreeNode(1, right = TreeNode(2, right = TreeNode(3)))),
        )
        for (tree in trees) {
            for (rule in rules) {
                val order = walk(rule, tree)
                assertEquals(tree.size, order.size)
                assertEquals(order.toSet(), tree.inorder().toSet())
            }
        }
    }

    @Test
    fun `the only difference between the three is where VISIT sits`() {
        // Each rule is a permutation of the same three steps, and the position of
        // VISIT is the entire algorithm.
        for (rule in rules) {
            assertEquals(setOf(Step.LEFT, Step.VISIT, Step.RIGHT), rule.order.toSet())
            assertEquals(3, rule.order.size)
        }
        assertEquals(0, PreorderRule.order.indexOf(Step.VISIT))
        assertEquals(1, InorderRule.order.indexOf(Step.VISIT))
        assertEquals(2, PostorderRule.order.indexOf(Step.VISIT))
        // And all three take the left subtree before the right one.
        for (rule in rules) {
            assertTrue(rule.order.indexOf(Step.LEFT) < rule.order.indexOf(Step.RIGHT))
        }
    }

    @Test
    fun `traversal depends on shape alone, never on values`() {
        // Relabel every node through a map that destroys the ordering, and each
        // lesson must visit the same *positions* in the same sequence. This is
        // what proves inorder is a traversal rule and not a sorting algorithm.
        fun relabel(node: TreeNode?): TreeNode? = node?.let {
            TreeNode(-it.value * 7, relabel(it.left), relabel(it.right))
        }

        val trees = listOf(
            TreeDatasets.teachingTree,
            TreeDatasets.tryTree,
            BinaryTree(TreeNode(4, left = TreeNode(3, left = TreeNode(2)))),
            BinaryTree(TreeNode(1, right = TreeNode(2, right = TreeNode(3)))),
        )
        for (tree in trees) {
            val scrambled = BinaryTree(relabel(tree.root))
            for (rule in rules) {
                assertEquals(
                    "${rule.id} on a relabelled tree",
                    walk(rule, tree).map { -it * 7 },
                    walk(rule, scrambled),
                )
            }
        }
    }

    @Test
    fun `inorder is not sorting`() {
        // On the WATCH tree inorder happens to come out sorted, because that tree
        // is a search tree. On the TRY tree it does not — which is why TRY uses a
        // tree that is not one.
        val watch = walk(InorderRule, TreeDatasets.teachingTree)
        assertEquals(watch.sorted(), watch)

        val tryIt = walk(InorderRule, TreeDatasets.tryTree)
        assertFalse("inorder must not come out sorted here", tryIt == tryIt.sorted())
        assertEquals(listOf(2, 4, 7, 9, 5), tryIt)
    }

    @Test
    fun `all three ship as separate lessons with their own progress`() {
        val ids = listOf(
            AlgorithmId.TREE_INORDER,
            AlgorithmId.TREE_PREORDER,
            AlgorithmId.TREE_POSTORDER,
        )
        val packs = ids.map { AlgorithmCatalog.byId(it) }
        // Three ids, three packs, three names — nothing is shared at the level
        // the learner sees.
        assertEquals(3, packs.map { it.id }.toSet().size)
        assertEquals(3, packs.map { it.displayName }.toSet().size)
        // And all three run on the same tree, which is the point.
        assertEquals(1, packs.map { it.watchDataset.tree }.toSet().size)
    }

    @Test
    fun `the same wrong tap is answered differently by each lesson`() {
        // At the root of the teaching tree, tapping 30 is correct for inorder and
        // postorder and wrong for preorder; tapping 50 is the opposite. One
        // gesture, three rules, three answers — from one machine.
        fun decisionAt(rule: TraversalRule) = AlgorithmRunner(
            TreeTraversalAlgorithm(rule),
            Dataset(values = emptyList(), tree = TreeDatasets.teachingTree),
        ).probe().let { (it as Probe.Decide).decision }

        assertEquals(TraversalAction.Touch(30), decisionAt(InorderRule).correct)
        assertEquals(TraversalAction.Touch(50), decisionAt(PreorderRule).correct)
        assertEquals(TraversalAction.Touch(30), decisionAt(PostorderRule).correct)

        // Every lesson has its own sentence for the tap it refuses.
        val reasons = rules.map { rule ->
            val decision = decisionAt(rule)
            val wrong = if (decision.correct == TraversalAction.Touch(50)) 30 else 50
            decision.whyWrong.getValue(TraversalAction.Touch(wrong)).id
        }
        assertEquals(3, reasons.toSet().size)
    }
}
