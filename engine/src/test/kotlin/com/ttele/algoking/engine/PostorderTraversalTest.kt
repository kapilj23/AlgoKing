package com.ttele.algoking.engine

import com.ttele.algoking.engine.algorithms.traversal.PostorderRule
import com.ttele.algoking.engine.algorithms.traversal.Step
import com.ttele.algoking.engine.algorithms.traversal.TraversalAction
import com.ttele.algoking.engine.algorithms.traversal.TraversalProjector
import com.ttele.algoking.engine.algorithms.traversal.TreeTraversalAlgorithm
import com.ttele.algoking.engine.algorithms.traversal.TreeWalkState
import com.ttele.algoking.engine.catalog.AlgorithmCatalog
import com.ttele.algoking.engine.core.AlgorithmId
import com.ttele.algoking.engine.core.AlgorithmRunner
import com.ttele.algoking.engine.core.BinaryTree
import com.ttele.algoking.engine.core.Dataset
import com.ttele.algoking.engine.core.Probe
import com.ttele.algoking.engine.core.TreeNode
import com.ttele.algoking.engine.dataset.TreeDatasets
import com.ttele.algoking.engine.decision.DecisionKind
import com.ttele.algoking.engine.decision.DecisionValidation
import com.ttele.algoking.engine.decision.Validation
import com.ttele.algoking.engine.scene.CellState
import com.ttele.algoking.engine.walkthrough.WatchStepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Binary Tree — Postorder. **LEFT → RIGHT → NODE.** Children first, parent last. */
class PostorderTraversalTest {

    private val algorithm = TreeTraversalAlgorithm(PostorderRule)

    private fun runner(tree: BinaryTree) =
        AlgorithmRunner(algorithm, Dataset(values = emptyList(), tree = tree))

    private fun walk(tree: BinaryTree): TreeWalkState {
        val runner = runner(tree)
        var guard = 0
        while (guard++ < 500) {
            when (val probe = runner.probe()) {
                is Probe.Mechanical -> runner.apply(probe.action)
                is Probe.Decide -> runner.apply(probe.decision.correct)
                is Probe.Terminal -> return runner.current.state
            }
        }
        error("postorder did not terminate")
    }

    private fun taps(tree: BinaryTree): List<Int> {
        val runner = runner(tree)
        val tapped = mutableListOf<Int>()
        var guard = 0
        while (guard++ < 500) {
            when (val probe = runner.probe()) {
                is Probe.Mechanical -> runner.apply(probe.action)
                is Probe.Terminal -> return tapped
                is Probe.Decide -> {
                    tapped += (probe.decision.correct as TraversalAction.Touch).node
                    runner.apply(probe.decision.correct)
                }
            }
        }
        error("postorder did not terminate")
    }

    @Test
    fun `the rule is left, right, node`() {
        assertEquals(listOf(Step.LEFT, Step.RIGHT, Step.VISIT), PostorderRule.order)
        assertEquals(AlgorithmId.TREE_POSTORDER, PostorderRule.id)
    }

    @Test
    fun `the root comes out last`() {
        val visited = walk(TreeDatasets.teachingTree).visited
        assertEquals(listOf(20, 40, 30, 60, 80, 70, 50), visited)
        assertEquals(50, visited.last())
    }

    @Test
    fun `a parent always comes out after both of its children`() {
        // The claim of the lesson, asserted over every tree rather than read off
        // one expected sequence.
        val trees = listOf(
            TreeDatasets.teachingTree,
            TreeDatasets.tryTree,
            BinaryTree.of(50, 25, 75, 12, 37, 62, 87, 6, 18),
            BinaryTree(TreeNode(1, TreeNode(2, TreeNode(3)), TreeNode(4, right = TreeNode(5)))),
        )
        for (tree in trees) {
            val order = walk(tree).visited
            for ((parent, child) in tree.edges()) {
                assertTrue(
                    "$child should come out before its parent $parent",
                    order.indexOf(child) < order.indexOf(parent),
                )
            }
        }
    }

    @Test
    fun `the try tree comes out children first`() {
        assertEquals(listOf(2, 4, 5, 9, 7), walk(TreeDatasets.tryTree).visited)
    }

    @Test
    fun `the lesson agrees with the recursive reference on every shape`() {
        val trees = listOf(
            TreeDatasets.teachingTree,
            TreeDatasets.tryTree,
            BinaryTree.EMPTY,
            BinaryTree.of(42),
            BinaryTree(TreeNode(4, left = TreeNode(3, left = TreeNode(2)))),
            BinaryTree(TreeNode(1, right = TreeNode(2, right = TreeNode(3)))),
            BinaryTree.of(10, 20, 30, 40, 50),
            BinaryTree.of(50, 25, 75, 12, 37, 62, 87, 6, 18),
            BinaryTree(TreeNode(1, TreeNode(2, TreeNode(3)), TreeNode(4, right = TreeNode(5)))),
        )
        for (tree in trees) {
            assertEquals("postorder of $tree", tree.postorder(), walk(tree).visited)
        }
    }

    @Test
    fun `an empty tree is a finished lesson, not a crash`() {
        val state = algorithm.initial(Dataset(values = emptyList(), tree = BinaryTree.EMPTY))
        assertTrue(state.finished)
        assertTrue(algorithm.probe(state) is Probe.Terminal)
        assertTrue(walk(BinaryTree.EMPTY).visited.isEmpty())
    }

    @Test
    fun `a single node is one tap`() {
        assertEquals(listOf(42), walk(BinaryTree.of(42)).visited)
        assertEquals(listOf(42), taps(BinaryTree.of(42)))
    }

    @Test
    fun `the learner never taps the same node twice in a row`() {
        for (tree in listOf(TreeDatasets.teachingTree, TreeDatasets.tryTree)) {
            taps(tree).zipWithNext().forEach { (a, b) ->
                assertFalse("tapped $a twice in a row", a == b)
            }
        }
    }

    @Test
    fun `visiting a parent before its right subtree is refused`() {
        // The brief's example: at 30, with 20 out and 40 untouched, 30 is not next.
        val runner = runner(TreeDatasets.teachingTree)
        // Down to 30, down-and-visit 20, then the app returns to 30.
        runner.apply((runner.probe() as Probe.Decide).decision.correct)
        runner.apply((runner.probe() as Probe.Decide).decision.correct)
        while (runner.probe() is Probe.Mechanical) {
            runner.apply((runner.probe() as Probe.Mechanical).action)
        }
        assertEquals(30, runner.current.state.current)
        assertEquals(listOf(20), runner.current.state.visited)

        val decision = (runner.probe() as Probe.Decide).decision
        // 40 is next — not 30, which still owes a subtree.
        assertEquals(TraversalAction.Touch(40), decision.correct)
        assertNotNull(decision.whyWrong[TraversalAction.Touch(30)])

        val before = runner.current.state
        assertTrue(
            DecisionValidation.validate(decision, TraversalAction.Touch(30), 0)
                is Validation.Retry,
        )
        assertEquals(before, runner.current.state)
    }

    @Test
    fun `a wrong tap never moves the traversal`() {
        val runner = runner(TreeDatasets.teachingTree)
        val decision = (runner.probe() as Probe.Decide).decision
        val before = runner.current.state
        for (option in decision.options.map { it.action }) {
            if (option == decision.correct) continue
            assertTrue(DecisionValidation.validate(decision, option, 0) is Validation.Retry)
            assertEquals(before, runner.current.state)
        }
    }

    @Test
    fun `the run terminates however badly it is driven`() {
        val runner = runner(TreeDatasets.teachingTree)
        val everyNode = TreeDatasets.teachingTree.inorder() + listOf(0, 999)
        var guard = 0
        while (guard++ < 4_000) {
            when (val probe = runner.probe()) {
                is Probe.Terminal -> break
                is Probe.Mechanical -> runner.apply(probe.action)
                is Probe.Decide -> {
                    everyNode.forEach { runner.apply(TraversalAction.Touch(it)) }
                    runner.apply(probe.decision.correct)
                }
            }
        }
        assertTrue(runner.probe() is Probe.Terminal)
        assertEquals(listOf(20, 40, 30, 60, 80, 70, 50), runner.current.state.visited)
    }

    @Test
    fun `parents sit amber on the stack while their children turn green`() {
        // Postorder's signature picture, and the opposite of preorder's.
        val runner = runner(TreeDatasets.teachingTree)
        runner.apply((runner.probe() as Probe.Decide).decision.correct)
        runner.apply((runner.probe() as Probe.Decide).decision.correct)
        while (runner.probe() is Probe.Mechanical) {
            runner.apply((runner.probe() as Probe.Mechanical).action)
        }
        val scene = TraversalProjector("Postorder").project(runner.current.state, emptyList())
        fun state(label: String) = scene.nodes.first { it.label == label }.state

        assertEquals(CellState.FINALIZED, state("20"))
        assertEquals(CellState.COMPARING, state("30"))
        // The root has been reached and is waiting for everything beneath it.
        assertEquals(CellState.CANDIDATE, state("50"))
        assertEquals(CellState.IDLE, state("40"))
        assertEquals(listOf("50", "30"), scene.stack)
        assertEquals("Postorder", scene.traversalLabel)
    }

    @Test
    fun `the walkthrough is built around the waiting`() {
        val steps = AlgorithmCatalog.postorderTraversal().watchScript().steps
        val kinds = steps.map { it.kind }
        assertEquals(WatchStepKind.SETUP, kinds.first())
        assertEquals(WatchStepKind.INSIGHT, kinds[kinds.lastIndex - 1])
        assertEquals(WatchStepKind.SUMMARY, kinds.last())
        assertEquals(7, kinds.count { it == WatchStepKind.FOUND })
        // Postorder earns the most return beats of the three: every one lands on
        // a parent that is still waiting. Preorder earns none.
        assertTrue(kinds.count { it == WatchStepKind.EXAMINE } >= 4)
        assertEquals(4, steps.last().bullets.size)
    }

    @Test
    fun `no two adjacent walkthrough steps are identical`() {
        val steps = AlgorithmCatalog.postorderTraversal().watchScript().steps
        steps.zipWithNext().forEach { (a, b) ->
            assertTrue(
                "steps ${a.index} and ${b.index} are identical",
                a.scene != b.scene || a.headline != b.headline || a.support != b.support,
            )
        }
    }

    @Test
    fun `the lesson is wired into the catalogue with both stages`() {
        val pack = AlgorithmCatalog.byId(AlgorithmId.TREE_POSTORDER)
        assertEquals(AlgorithmId.TREE_POSTORDER, pack.id)
        assertEquals("Postorder Traversal", pack.displayName)
        assertEquals(TreeDatasets.teachingTree, pack.watchDataset.tree)
        assertEquals(TreeDatasets.tryTree, pack.tryDataset.tree)
    }
}
