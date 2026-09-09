package com.ttele.algoking.engine

import com.ttele.algoking.engine.algorithms.traversal.PreorderRule
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

/** Binary Tree — Preorder. **NODE → LEFT → RIGHT.** */
class PreorderTraversalTest {

    private val algorithm = TreeTraversalAlgorithm(PreorderRule)

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
        error("preorder did not terminate")
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
        error("preorder did not terminate")
    }

    @Test
    fun `the rule is node, left, right`() {
        assertEquals(listOf(Step.VISIT, Step.LEFT, Step.RIGHT), PreorderRule.order)
        assertEquals(AlgorithmId.TREE_PREORDER, PreorderRule.id)
    }

    @Test
    fun `the teaching tree comes out root first`() {
        val visited = walk(TreeDatasets.teachingTree).visited
        assertEquals(listOf(50, 30, 20, 40, 70, 60, 80), visited)
        assertEquals(50, visited.first())
    }

    @Test
    fun `the try tree comes out root first too`() {
        assertEquals(listOf(7, 4, 2, 9, 5), walk(TreeDatasets.tryTree).visited)
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
            assertEquals("preorder of $tree", tree.preorder(), walk(tree).visited)
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
    fun `every tap is a visit, so the taps are the traversal`() {
        // Preorder's shape, stated: arriving at a node and emitting it are the
        // same moment, so the sequence of taps *is* the output.
        assertEquals(walk(TreeDatasets.teachingTree).visited, taps(TreeDatasets.teachingTree))
        assertEquals(walk(TreeDatasets.tryTree).visited, taps(TreeDatasets.tryTree))
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
    fun `every decision is a tap, with a full ladder and a reason per wrong node`() {
        val runner = runner(TreeDatasets.teachingTree)
        var decisions = 0
        var guard = 0
        while (guard++ < 200) {
            when (val probe = runner.probe()) {
                is Probe.Mechanical -> runner.apply(probe.action)
                is Probe.Terminal -> break
                is Probe.Decide -> {
                    val decision = probe.decision
                    decisions++
                    assertEquals(DecisionKind.CELL, decision.kind)
                    assertFalse(decision.autoInTry)
                    assertEquals(3, decision.guidance.size)
                    assertNotNull(decision.hint)
                    assertEquals(decision.options.size - 1, decision.whyWrong.size)
                    runner.apply(decision.correct)
                }
            }
        }
        assertEquals(7, decisions)
    }

    @Test
    fun `going into a child before visiting the root is refused`() {
        // The mistake this lesson exists to answer, and the brief's example.
        val start = algorithm.initial(Dataset(values = emptyList(), tree = TreeDatasets.teachingTree))
        val tooEarly = algorithm.apply(start, TraversalAction.Touch(30))
        assertFalse(tooEarly.correct)
        assertEquals(start, tooEarly.next)
        // The root is what the traversal owes first.
        assertEquals(TraversalAction.Touch(50), (algorithm.probe(start) as Probe.Decide).decision.correct)
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
        assertEquals(listOf(50, 30, 20, 40, 70, 60, 80), runner.current.state.visited)
    }

    @Test
    fun `the root turns green immediately, and its children are still grey`() {
        // Preorder's signature picture, and the opposite of postorder's.
        val runner = runner(TreeDatasets.teachingTree)
        runner.apply((runner.probe() as Probe.Decide).decision.correct)
        val scene = TraversalProjector("Preorder").project(runner.current.state, emptyList())
        fun state(label: String) = scene.nodes.first { it.label == label }.state

        // 50 is current *and* visited: current wins, so "where am I" stays visible.
        assertEquals(CellState.COMPARING, state("50"))
        assertEquals(CellState.IDLE, state("30"))
        assertEquals(CellState.IDLE, state("70"))
        assertEquals(listOf("50"), scene.traversal)
        assertEquals("Preorder", scene.traversalLabel)
    }

    @Test
    fun `the walkthrough opens on the root and closes on the recap`() {
        val steps = AlgorithmCatalog.preorderTraversal().watchScript().steps
        val kinds = steps.map { it.kind }
        assertEquals(WatchStepKind.SETUP, kinds.first())
        assertEquals(WatchStepKind.INSIGHT, kinds[kinds.lastIndex - 1])
        assertEquals(WatchStepKind.SUMMARY, kinds.last())
        assertEquals(7, kinds.count { it == WatchStepKind.FOUND })
        // Nothing happens on the way up in preorder, so no return earns a beat.
        assertEquals(0, kinds.count { it == WatchStepKind.EXAMINE })
        assertEquals(4, steps.last().bullets.size)
    }

    @Test
    fun `no two adjacent walkthrough steps are identical`() {
        val steps = AlgorithmCatalog.preorderTraversal().watchScript().steps
        steps.zipWithNext().forEach { (a, b) ->
            assertTrue(
                "steps ${a.index} and ${b.index} are identical",
                a.scene != b.scene || a.headline != b.headline || a.support != b.support,
            )
        }
    }

    @Test
    fun `the lesson is wired into the catalogue with both stages`() {
        val pack = AlgorithmCatalog.byId(AlgorithmId.TREE_PREORDER)
        assertEquals(AlgorithmId.TREE_PREORDER, pack.id)
        assertEquals("Preorder Traversal", pack.displayName)
        assertEquals(TreeDatasets.teachingTree, pack.watchDataset.tree)
        assertEquals(TreeDatasets.tryTree, pack.tryDataset.tree)
    }
}
