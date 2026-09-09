package com.ttele.algoking.engine

import com.ttele.algoking.engine.algorithms.avl.AvlAction
import com.ttele.algoking.engine.algorithms.avl.AvlProjector
import com.ttele.algoking.engine.algorithms.avl.AvlState
import com.ttele.algoking.engine.algorithms.avl.AvlTreeAlgorithm
import com.ttele.algoking.engine.algorithms.avl.ImbalanceShape
import com.ttele.algoking.engine.algorithms.avl.RotationKind
import com.ttele.algoking.engine.catalog.AlgorithmCatalog
import com.ttele.algoking.engine.core.AlgorithmId
import com.ttele.algoking.engine.core.AlgorithmRunner
import com.ttele.algoking.engine.core.BinaryTree
import com.ttele.algoking.engine.core.Dataset
import com.ttele.algoking.engine.core.Probe
import com.ttele.algoking.engine.dataset.AvlDatasets
import com.ttele.algoking.engine.decision.DecisionKind
import com.ttele.algoking.engine.decision.DecisionValidation
import com.ttele.algoking.engine.decision.Validation
import com.ttele.algoking.engine.scene.CellState
import com.ttele.algoking.engine.scene.EdgeState
import com.ttele.algoking.engine.walkthrough.WatchStepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AVL Tree.
 *
 * Two claims carry this lesson, and both are asserted against an independent
 * implementation rather than against a remembered answer: that the engine ends up
 * where textbook AVL insertion would (`BinaryTree.avlInsert`), and that a
 * rotation changes depth without changing order.
 */
class AvlTest {

    private val algorithm = AvlTreeAlgorithm()

    private fun dataset(tree: BinaryTree, values: List<Int>) =
        Dataset(values = values, tree = tree)

    private fun runner(tree: BinaryTree, values: List<Int>) =
        AlgorithmRunner(algorithm, dataset(tree, values))

    /** Drives the real algorithm to its terminal state, always choosing correctly. */
    private fun run(tree: BinaryTree, values: List<Int>): AvlState {
        val runner = runner(tree, values)
        var guard = 0
        while (guard++ < 500) {
            when (val probe = runner.probe()) {
                is Probe.Mechanical -> runner.apply(probe.action)
                is Probe.Decide -> runner.apply(probe.decision.correct)
                is Probe.Terminal -> return runner.current.state
            }
        }
        error("AVL insertion did not terminate")
    }

    /** Every state the run passes through, for asserting on the beats. */
    private fun trace(tree: BinaryTree, values: List<Int>): List<AvlState> {
        val runner = runner(tree, values)
        val states = mutableListOf(runner.current.state)
        var guard = 0
        while (guard++ < 500) {
            when (val probe = runner.probe()) {
                is Probe.Mechanical -> runner.apply(probe.action)
                is Probe.Decide -> runner.apply(probe.decision.correct)
                is Probe.Terminal -> return states
            }
            states += runner.current.state
        }
        error("AVL insertion did not terminate")
    }

    // ── The structure ────────────────────────────────────────────────────────

    @Test
    fun `balance factor is left height minus right height`() {
        val tree = BinaryTree.of(30, 20, 40, 10)
        assertEquals(1, tree.balanceFactor(30))
        assertEquals(1, tree.balanceFactor(20))
        assertEquals(0, tree.balanceFactor(40))
        assertEquals(0, tree.balanceFactor(10))
        assertTrue(tree.isBalanced)
    }

    @Test
    fun `a plain BST insert can break the invariant`() {
        // This is the whole reason the lesson exists: `insert` does not rebalance.
        val broken = BinaryTree.of(30, 20, 40, 10).insert(5)
        assertEquals(2, broken.balanceFactor(20))
        assertFalse(broken.isBalanced)
    }

    @Test
    fun `a rotation changes depth but never order`() {
        // The insight of the lesson, asserted: in-order is identical before and
        // after, so the result is still a search tree — while the depths differ,
        // which is what the rotation was for.
        val before = BinaryTree.of(30, 20, 40, 10).insert(5)
        val after = before.rotateRight(20)
        assertEquals(before.inorder(), after.inorder())
        assertTrue(after.height < before.height)
        assertTrue(after.isBalanced)
        // And it is reversible, which is what "only the links moved" means.
        assertEquals(before, after.rotateLeft(10))
    }

    @Test
    fun `rotating a node with nothing to raise leaves the tree alone`() {
        val tree = BinaryTree.of(30, 20, 40)
        assertEquals(tree, tree.rotateLeft(20))
        assertEquals(tree, tree.rotateRight(40))
        assertEquals(tree, tree.rotateLeft(999))
        assertEquals(BinaryTree.EMPTY, BinaryTree.EMPTY.rotateRight(1))
    }

    @Test
    fun `the reference AVL insert keeps every tree balanced`() {
        // 200 ascending inserts is the worst case for a plain BST — a 200-level
        // linked list. AVL keeps it under 10.
        var tree = BinaryTree.EMPTY
        for (value in 1..200) {
            tree = tree.avlInsert(value)
            assertTrue("unbalanced after $value", tree.isBalanced)
        }
        assertEquals(200, tree.size)
        assertEquals((1..200).toList(), tree.inorder())
        assertTrue("height ${tree.height}", tree.height <= 10)
    }

    // ── The lesson's own runs ────────────────────────────────────────────────

    @Test
    fun `the watch run needs no rotation, then a single, then a double`() {
        val states = trace(AvlDatasets.watchTree, AvlDatasets.watch.values)

        // 35 goes in and nothing is broken.
        val afterFirst = states.first { it.justInserted == 35 }
        assertNull(afterFirst.unbalanced)

        // 5 breaks 20, and the path below it runs straight: one right rotation.
        val afterSecond = states.first { it.justInserted == 5 }
        assertEquals(20, afterSecond.unbalanced)
        assertEquals(ImbalanceShape.LEFT_LEFT, afterSecond.shapeAt(20))
        assertEquals(10, afterSecond.riserFor(20))
        assertEquals(listOf(RotationKind.RIGHT), afterSecond.planFor(20).map { it.kind })

        // 37 breaks 40, and the path bends: two rotations, grandchild up.
        val afterThird = states.first { it.justInserted == 37 }
        assertEquals(40, afterThird.unbalanced)
        assertEquals(ImbalanceShape.LEFT_RIGHT, afterThird.shapeAt(40))
        assertEquals(37, afterThird.riserFor(40))
        assertEquals(
            listOf(RotationKind.LEFT to 35, RotationKind.RIGHT to 40),
            afterThird.planFor(40).map { it.kind to it.at },
        )
    }

    @Test
    fun `the try run is the mirror image of the watch run`() {
        val states = trace(AvlDatasets.tryTree, AvlDatasets.tryIt.values)

        assertNull(states.first { it.justInserted == 15 }.unbalanced)

        val single = states.first { it.justInserted == 50 }
        assertEquals(30, single.unbalanced)
        // The mirror of WATCH's LEFT_LEFT, fixed by the opposite rotation.
        assertEquals(ImbalanceShape.RIGHT_RIGHT, single.shapeAt(30))
        assertEquals(40, single.riserFor(30))
        assertEquals(listOf(RotationKind.LEFT), single.planFor(30).map { it.kind })

        val double = states.first { it.justInserted == 13 }
        assertEquals(10, double.unbalanced)
        assertEquals(ImbalanceShape.RIGHT_LEFT, double.shapeAt(10))
        assertEquals(13, double.riserFor(10))
        assertEquals(
            listOf(RotationKind.RIGHT to 15, RotationKind.LEFT to 10),
            double.planFor(10).map { it.kind to it.at },
        )
    }

    @Test
    fun `between them the two stages cover all four cases`() {
        val shapes = (
            trace(AvlDatasets.watchTree, AvlDatasets.watch.values) +
                trace(AvlDatasets.tryTree, AvlDatasets.tryIt.values)
            )
            .mapNotNull { state -> state.unbalanced?.let { state.shapeAt(it) } }
            .toSet()
        assertEquals(ImbalanceShape.entries.toSet(), shapes)
    }

    @Test
    fun `both runs end perfectly balanced, and agree with textbook AVL`() {
        for ((tree, values) in listOf(
            AvlDatasets.watchTree to AvlDatasets.watch.values,
            AvlDatasets.tryTree to AvlDatasets.tryIt.values,
        )) {
            val end = run(tree, values)
            assertTrue(end.tree.isBalanced)
            assertEquals(7, end.tree.size)
            assertEquals(3, end.tree.height)
            // Two repairs, three rotations: the second one is a double.
            assertEquals(3, end.rotations)
            // What the learner produced is what textbook AVL insertion produces.
            val reference = values.fold(tree) { acc, value -> acc.avlInsert(value) }
            assertEquals(reference, end.tree)
        }
    }

    @Test
    fun `the engine matches textbook AVL for every insertion order it is given`() {
        // Independent implementations, driven over the same data: the lesson
        // rebalances by asking the learner, the reference rebalances on the way
        // back up a recursion, and they have to end in the same shape.
        val starts = listOf(
            BinaryTree.of(30, 20, 40, 10),
            BinaryTree.of(20, 10, 30, 40),
            BinaryTree.of(50),
            BinaryTree.of(50, 25, 75, 12, 37, 62, 87),
        )
        val sequences = listOf(
            listOf(5),
            listOf(35, 5, 37),
            listOf(15, 50, 13),
            listOf(1, 2, 3, 4, 5),
            listOf(99, 98, 97),
            listOf(60, 55, 65, 5, 45),
        )
        for (start in starts) {
            for (values in sequences) {
                val fresh = values.filterNot { start.contains(it) }
                if (fresh.isEmpty()) continue
                val end = run(start, fresh)
                assertTrue("unbalanced: $start + $fresh", end.tree.isBalanced)
                assertEquals(
                    "shape differs: $start + $fresh",
                    fresh.fold(start) { acc, value -> acc.avlInsert(value) },
                    end.tree,
                )
            }
        }
    }

    @Test
    fun `one insertion never needs more than one repair`() {
        // The AVL guarantee, and the reason the lesson can ask for exactly one
        // pivot per insert: fixing the lowest unbalanced node restores the whole
        // tree, so no state ever asks for a second.
        val states = trace(AvlDatasets.watchTree, AvlDatasets.watch.values)
        val repairsPerInsert = states
            .filter { it.plan.isNotEmpty() }
            .groupBy { it.justInserted }
            .mapValues { (_, group) -> group.first().plan.size }
        assertTrue(repairsPerInsert.values.all { it in 1..2 })
        assertEquals(setOf(5, 37), repairsPerInsert.keys)
    }

    // ── Edge cases ───────────────────────────────────────────────────────────

    @Test
    fun `an empty starting tree just builds one`() {
        val end = run(BinaryTree.EMPTY, listOf(10, 20, 30))
        assertTrue(end.tree.isBalanced)
        assertEquals(listOf(10, 20, 30), end.tree.inorder())
        assertEquals(20, end.tree.root?.value)
        assertEquals(1, end.rotations)
    }

    @Test
    fun `nothing to insert is a finished lesson`() {
        val state = algorithm.initial(dataset(AvlDatasets.watchTree, emptyList()))
        assertTrue(state.finished)
        assertTrue(algorithm.probe(state) is Probe.Terminal)
        // And it still projects.
        assertEquals(4, AvlProjector().project(state, emptyList()).nodes.size)
    }

    @Test
    fun `inserting a value already present changes nothing`() {
        val end = run(AvlDatasets.watchTree, listOf(30))
        assertEquals(AvlDatasets.watchTree, end.tree)
        assertEquals(0, end.rotations)
    }

    @Test
    fun `ascending inserts stay short instead of becoming a list`() {
        val end = run(BinaryTree.EMPTY, (1..15).toList())
        assertEquals(15, end.tree.size)
        assertTrue(end.tree.isBalanced)
        // A plain BST over the same values is 15 levels tall.
        assertEquals(15, (1..15).fold(BinaryTree.EMPTY) { t, v -> t.insert(v) }.height)
        assertEquals(4, end.tree.height)
    }

    // ── The decisions ────────────────────────────────────────────────────────

    @Test
    fun `both decisions are the learner's, and both are taps on the tree`() {
        val runner = runner(AvlDatasets.watchTree, listOf(5))
        runner.apply((runner.probe() as Probe.Mechanical).action)

        val pivot = (runner.probe() as Probe.Decide).decision
        assertEquals(DecisionKind.CELL, pivot.kind)
        assertFalse(pivot.autoInTry)
        assertEquals(AvlAction.Pivot(20), pivot.correct)
        // Every node is tappable: narrowing the options would answer half of it.
        assertEquals(runner.current.state.tree.size, pivot.options.size)
        runner.apply(pivot.correct)

        val riser = (runner.probe() as Probe.Decide).decision
        assertEquals(DecisionKind.CELL, riser.kind)
        assertFalse(riser.autoInTry)
        assertEquals(AvlAction.Raise(10), riser.correct)
    }

    @Test
    fun `every decision carries a full ladder and a reason for every wrong node`() {
        val runner = runner(AvlDatasets.watchTree, AvlDatasets.watch.values)
        var decisions = 0
        var guard = 0
        while (guard++ < 100) {
            when (val probe = runner.probe()) {
                is Probe.Mechanical -> runner.apply(probe.action)
                is Probe.Terminal -> break
                is Probe.Decide -> {
                    val decision = probe.decision
                    decisions++
                    assertEquals(3, decision.guidance.size)
                    assertNotNull(decision.hint)
                    // One reason per wrong option, and none for the right one.
                    assertEquals(decision.options.size - 1, decision.whyWrong.size)
                    assertFalse(decision.correct in decision.whyWrong.keys)
                    runner.apply(decision.correct)
                }
            }
        }
        // Two repairs, two decisions each.
        assertEquals(4, decisions)
    }

    @Test
    fun `the bent case offers the child as a wrong answer with its own reason`() {
        // The single most common AVL mistake, and the one the lesson has to be
        // able to answer specifically.
        val runner = runner(AvlDatasets.watchTree, AvlDatasets.watch.values)
        var guard = 0
        while (guard++ < 100 && runner.current.state.justInserted != 37) {
            when (val probe = runner.probe()) {
                is Probe.Mechanical -> runner.apply(probe.action)
                is Probe.Decide -> runner.apply(probe.decision.correct)
                is Probe.Terminal -> break
            }
        }
        runner.apply((runner.probe() as Probe.Decide).decision.correct) // pivot 40
        val riser = (runner.probe() as Probe.Decide).decision
        assertEquals(AvlAction.Raise(37), riser.correct)
        // 35 is 40's child. Bringing it up is the mistake, and it has a line.
        assertNotNull(riser.whyWrong[AvlAction.Raise(35)])
        assertNotNull(riser.whyWrong[AvlAction.Raise(40)])
    }

    // ── The invariant: a wrong answer is a learning event ────────────────────

    @Test
    fun `a wrong tap never changes the tree`() {
        val runner = runner(AvlDatasets.watchTree, listOf(5))
        runner.apply((runner.probe() as Probe.Mechanical).action)
        val decision = (runner.probe() as Probe.Decide).decision
        val before = runner.current.state

        // Every wrong node, through the path Try actually uses.
        for (option in decision.options.map { it.action }) {
            if (option == decision.correct) continue
            assertTrue(DecisionValidation.validate(decision, option, 0) is Validation.Retry)
            assertEquals(before, runner.current.state)
        }

        val accepted = DecisionValidation.validate(decision, decision.correct, 3)
        assertTrue(accepted is Validation.Accept)
        runner.apply((accepted as Validation.Accept).action)
        assertEquals(20, runner.current.state.pivot)
    }

    @Test
    fun `applying a wrong tap directly is refused rather than recorded`() {
        // `apply` stays total, but naming a node that is not out of balance is
        // not a state the algorithm can be in — so it comes back unchanged, the
        // same refusal Two Pointers gives a false "pair found".
        val start = algorithm.initial(dataset(AvlDatasets.watchTree, listOf(5)))
        val inserted = algorithm.apply(start, AvlAction.Insert).next

        val wrongPivot = algorithm.apply(inserted, AvlAction.Pivot(30))
        assertFalse(wrongPivot.correct)
        assertEquals(inserted, wrongPivot.next)

        val pivoted = algorithm.apply(inserted, AvlAction.Pivot(20)).next
        val wrongRiser = algorithm.apply(pivoted, AvlAction.Raise(30))
        assertFalse(wrongRiser.correct)
        assertEquals(pivoted, wrongRiser.next)
        assertTrue(pivoted.plan.isEmpty())
    }

    @Test
    fun `the run terminates however badly it is driven`() {
        // Every action, applied at every state, from a run of ten inserts: the
        // machine must still finish. This is what makes wrong answers safe.
        val runner = runner(BinaryTree.EMPTY, listOf(50, 25, 75, 10, 30, 60, 80, 5, 1, 2))
        val values = (0..100).toList()
        var guard = 0
        while (guard++ < 4_000) {
            when (val probe = runner.probe()) {
                is Probe.Terminal -> break
                is Probe.Mechanical -> runner.apply(probe.action)
                is Probe.Decide -> {
                    // Try every wrong node before the right one, at every beat.
                    for (value in values) {
                        runner.apply(AvlAction.Pivot(value))
                        runner.apply(AvlAction.Raise(value))
                    }
                    runner.apply(probe.decision.correct)
                }
            }
        }
        assertTrue(runner.probe() is Probe.Terminal)
        assertTrue(runner.current.state.tree.isBalanced)
    }

    // ── The picture ──────────────────────────────────────────────────────────

    @Test
    fun `every node carries its balance factor, and only the broken one is marked`() {
        val states = trace(AvlDatasets.watchTree, AvlDatasets.watch.values)
        val broken = states.first { it.justInserted == 5 && it.pivot == null }
        val scene = AvlProjector().project(broken, emptyList())

        assertTrue(scene.nodes.all { it.caption != null })
        assertEquals("+2", scene.nodes.first { it.label == "20" }.caption)
        assertEquals(
            listOf("20"),
            scene.nodes.filter { it.captionAlert }.map { it.label },
        )
        // The new value is amber; nothing is violet until the learner names it.
        assertEquals(
            listOf("5"),
            scene.nodes.filter { it.state == CellState.CANDIDATE }.map { it.label },
        )
        assertTrue(scene.nodes.none { it.state == CellState.COMPARING })
        // The two steps of the imbalance are drawn as the links they are.
        assertTrue(scene.edges.any { it.state == EdgeState.PATH })
    }

    @Test
    fun `naming the pivot lights it and the links the rotation will move`() {
        val states = trace(AvlDatasets.watchTree, AvlDatasets.watch.values)
        val chosen = states.first { it.pivot == 20 }
        val scene = AvlProjector().project(chosen, emptyList())
        assertEquals(
            listOf("20"),
            scene.nodes.filter { it.state == CellState.COMPARING }.map { it.label },
        )
        assertTrue(scene.edges.any { it.state == EdgeState.ACTIVE })
    }

    @Test
    fun `the picture keeps every node in its column across a rotation`() {
        // What the in-order layout buys, and what makes the insight visible: a
        // rotation moves nodes between rows, never between columns.
        val states = trace(AvlDatasets.watchTree, AvlDatasets.watch.values)
        val index = states.indexOfFirst { it.plan.size == 1 && it.pivot == 20 }
        val before = AvlProjector().project(states[index], emptyList())
        val after = AvlProjector().project(states[index + 1], emptyList())

        assertEquals(before.nodes.map { it.label }, after.nodes.map { it.label })
        assertEquals(before.nodes.map { it.x }, after.nodes.map { it.x })
        assertNotEquals(before.nodes.map { it.y }, after.nodes.map { it.y })
    }

    private fun assertNotEquals(a: Any?, b: Any?) = assertFalse("$a == $b", a == b)

    // ── WATCH ────────────────────────────────────────────────────────────────

    @Test
    fun `the walkthrough shows a quiet insert, a single and a double`() {
        val steps = AlgorithmCatalog.avlTree().watchScript().steps
        val kinds = steps.map { it.kind }

        assertEquals(WatchStepKind.SETUP, kinds.first())
        assertEquals(WatchStepKind.INSIGHT, kinds[kinds.lastIndex - 1])
        assertEquals(WatchStepKind.SUMMARY, kinds.last())

        // Three inserts, and three rotations across two repairs.
        assertEquals(3, kinds.count { it == WatchStepKind.ADD })
        assertEquals(3, kinds.count { it == WatchStepKind.SWAP })
        // Two beats per repair before the rotation: which node broke, and which
        // node comes up. The second is what Try asks, so it gets its own step.
        assertEquals(4, kinds.count { it == WatchStepKind.EXAMINE })
    }

    @Test
    fun `no two adjacent walkthrough steps are identical`() {
        // ADR-020: a step where nothing changed is a bug, not a beat.
        val steps = AlgorithmCatalog.avlTree().watchScript().steps
        steps.zipWithNext().forEach { (a, b) ->
            assertTrue(
                "steps ${a.index} and ${b.index} are identical",
                a.scene != b.scene || a.headline != b.headline || a.support != b.support,
            )
        }
    }

    @Test
    fun `the walkthrough is long enough to teach and short enough to finish`() {
        val steps = AlgorithmCatalog.avlTree().watchScript().steps
        assertTrue("${steps.size} steps", steps.size in 10..16)
        assertEquals(5, steps.last().bullets.size)
    }

    // ── Wiring ───────────────────────────────────────────────────────────────

    @Test
    fun `the lesson is wired into the catalogue with both stages`() {
        val pack = AlgorithmCatalog.byId(AlgorithmId.AVL_TREE)
        assertEquals(AlgorithmId.AVL_TREE, pack.id)
        assertEquals("AVL Tree", pack.displayName)
        assertNotNull(pack.watchDataset.tree)
        assertNotNull(pack.tryDataset.tree)
        // Try is a different tree and a different sequence: the mirror of Watch.
        assertFalse(pack.watchDataset.tree == pack.tryDataset.tree)
        assertFalse(pack.watchDataset.values == pack.tryDataset.values)
        assertTrue(requireNotNull(pack.watchDataset.tree).isBalanced)
        assertTrue(requireNotNull(pack.tryDataset.tree).isBalanced)
    }
}
