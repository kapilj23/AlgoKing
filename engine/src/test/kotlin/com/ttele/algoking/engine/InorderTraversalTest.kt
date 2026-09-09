package com.ttele.algoking.engine

import com.ttele.algoking.engine.algorithms.traversal.InorderRule
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

/**
 * Binary Tree — Inorder. **LEFT → NODE → RIGHT.**
 *
 * The order the lesson produces is checked against `BinaryTree.inorder()`, which
 * is written recursively and independently: the lesson walks an explicit stack one
 * learner decision at a time, the reference recurses, and they have to agree on
 * every tree they are given.
 */
class InorderTraversalTest {

    private val algorithm = TreeTraversalAlgorithm(InorderRule)

    private fun runner(tree: BinaryTree) =
        AlgorithmRunner(algorithm, Dataset(values = emptyList(), tree = tree))

    /** Drives the real algorithm to its terminal state, always choosing correctly. */
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
        error("inorder did not terminate")
    }

    /** The nodes the learner taps, in order — what TRY actually costs. */
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
        error("inorder did not terminate")
    }

    // ── The rule ─────────────────────────────────────────────────────────────

    @Test
    fun `the rule is left, node, right`() {
        assertEquals(listOf(Step.LEFT, Step.VISIT, Step.RIGHT), InorderRule.order)
        assertEquals(AlgorithmId.TREE_INORDER, InorderRule.id)
    }

    // ── The order it produces ────────────────────────────────────────────────

    @Test
    fun `the teaching tree comes out in order`() {
        assertEquals(
            listOf(20, 30, 40, 50, 60, 70, 80),
            walk(TreeDatasets.teachingTree).visited,
        )
    }

    @Test
    fun `the try tree is not sorted, and only the traversal produces it`() {
        // The whole reason TRY uses a tree that is not a search tree.
        val visited = walk(TreeDatasets.tryTree).visited
        assertEquals(listOf(2, 4, 7, 9, 5), visited)
        assertFalse("inorder is not sorting", visited == visited.sorted())
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
            assertEquals("inorder of $tree", tree.inorder(), walk(tree).visited)
        }
    }

    @Test
    fun `an empty tree is a finished lesson, not a crash`() {
        val state = algorithm.initial(Dataset(values = emptyList(), tree = BinaryTree.EMPTY))
        assertTrue(state.finished)
        assertTrue(algorithm.probe(state) is Probe.Terminal)
        assertTrue(walk(BinaryTree.EMPTY).visited.isEmpty())
        assertTrue(TraversalProjector("Inorder").project(state, emptyList()).nodes.isEmpty())
    }

    @Test
    fun `a single node is one tap`() {
        assertEquals(listOf(42), walk(BinaryTree.of(42)).visited)
        // The root is placed on the stack, so visiting it is the only decision.
        assertEquals(listOf(42), taps(BinaryTree.of(42)))
    }

    // ── The interaction ──────────────────────────────────────────────────────

    @Test
    fun `the learner never taps the same node twice in a row`() {
        // Moving into a node that is immediately due its visit does both in one
        // beat. Without that, every leaf costs two consecutive taps on itself.
        for (tree in listOf(TreeDatasets.teachingTree, TreeDatasets.tryTree)) {
            val tapped = taps(tree)
            tapped.zipWithNext().forEach { (a, b) ->
                assertFalse("tapped $a twice in a row", a == b)
            }
        }
    }

    @Test
    fun `try costs nine decisions on the teaching tree and six on the try tree`() {
        assertEquals(listOf(30, 20, 30, 40, 50, 70, 60, 70, 80), taps(TreeDatasets.teachingTree))
        assertEquals(listOf(4, 2, 4, 7, 9, 5), taps(TreeDatasets.tryTree))
    }

    @Test
    fun `every decision is a tap on the tree, and the learner always makes it`() {
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
                    // Every node is tappable, and every wrong one has a reason.
                    assertEquals(TreeDatasets.teachingTree.size, decision.options.size)
                    assertEquals(decision.options.size - 1, decision.whyWrong.size)
                    assertFalse(decision.correct in decision.whyWrong.keys)
                    runner.apply(decision.correct)
                }
            }
        }
        assertEquals(9, decisions)
    }

    // ── The invariant: a wrong tap is a learning event ───────────────────────

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

        val accepted = DecisionValidation.validate(decision, decision.correct, 4)
        assertTrue(accepted is Validation.Accept)
        runner.apply((accepted as Validation.Accept).action)
        assertEquals(30, runner.current.state.current)
    }

    @Test
    fun `visiting a node before its left subtree is refused`() {
        // The mistake this lesson exists to answer: 50 is not ready until its
        // whole left subtree is out.
        val start = algorithm.initial(Dataset(values = emptyList(), tree = TreeDatasets.teachingTree))
        val tooEarly = algorithm.apply(start, TraversalAction.Touch(50))
        assertFalse(tooEarly.correct)
        assertEquals(start, tooEarly.next)
        // And a node that is nowhere near is refused just as flatly.
        assertEquals(start, algorithm.apply(start, TraversalAction.Touch(80)).next)
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
        assertEquals(listOf(20, 30, 40, 50, 60, 70, 80), runner.current.state.visited)
    }

    // ── The picture ──────────────────────────────────────────────────────────

    @Test
    fun `a node waiting on the stack is amber, and its finished left side is green`() {
        val runner = runner(TreeDatasets.teachingTree)
        // 30 down, 20 down-and-visited, return to 30 — now 30 is current with its
        // left side out and its right side untouched. The beat the lesson is about.
        runner.apply((runner.probe() as Probe.Decide).decision.correct)
        runner.apply((runner.probe() as Probe.Decide).decision.correct)
        while (runner.probe() is Probe.Mechanical) {
            runner.apply((runner.probe() as Probe.Mechanical).action)
        }
        val scene = TraversalProjector("Inorder").project(runner.current.state, emptyList())
        fun state(label: String) = scene.nodes.first { it.label == label }.state

        assertEquals(CellState.COMPARING, state("30"))
        assertEquals(CellState.FINALIZED, state("20"))
        assertEquals(CellState.CANDIDATE, state("50"))
        assertEquals(CellState.IDLE, state("40"))
        assertEquals(CellState.IDLE, state("70"))
        assertEquals(listOf("20"), scene.traversal)
        assertEquals("Inorder", scene.traversalLabel)
        assertEquals(listOf("50", "30"), scene.stack)
        assertTrue(scene.showPathStrip)
    }

    // ── WATCH ────────────────────────────────────────────────────────────────

    @Test
    fun `the walkthrough opens on the walk down and closes on the recap`() {
        val steps = AlgorithmCatalog.inorderTraversal().watchScript().steps
        val kinds = steps.map { it.kind }
        assertEquals(WatchStepKind.SETUP, kinds.first())
        assertEquals(WatchStepKind.INSIGHT, kinds[kinds.lastIndex - 1])
        assertEquals(WatchStepKind.SUMMARY, kinds.last())
        // One beat per visit, and every node is visited.
        assertEquals(7, kinds.count { it == WatchStepKind.FOUND })
        assertEquals(4, steps.last().bullets.size)
    }

    @Test
    fun `no two adjacent walkthrough steps are identical`() {
        val steps = AlgorithmCatalog.inorderTraversal().watchScript().steps
        steps.zipWithNext().forEach { (a, b) ->
            assertTrue(
                "steps ${a.index} and ${b.index} are identical",
                a.scene != b.scene || a.headline != b.headline || a.support != b.support,
            )
        }
    }

    @Test
    fun `the walkthrough is long enough to teach and short enough to finish`() {
        val steps = AlgorithmCatalog.inorderTraversal().watchScript().steps
        assertTrue("${steps.size} steps", steps.size in 10..16)
    }

    // ── Wiring ───────────────────────────────────────────────────────────────

    @Test
    fun `the lesson is wired into the catalogue with both stages`() {
        val pack = AlgorithmCatalog.byId(AlgorithmId.TREE_INORDER)
        assertEquals(AlgorithmId.TREE_INORDER, pack.id)
        assertEquals("Inorder Traversal", pack.displayName)
        assertEquals(TreeDatasets.teachingTree, pack.watchDataset.tree)
        assertEquals(TreeDatasets.tryTree, pack.tryDataset.tree)
    }
}
