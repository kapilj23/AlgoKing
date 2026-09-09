package com.ttele.algoking.engine

import com.ttele.algoking.engine.algorithms.bst.BinarySearchTreeAlgorithm
import com.ttele.algoking.engine.algorithms.bst.BstAction
import com.ttele.algoking.engine.algorithms.bst.BstProjector
import com.ttele.algoking.engine.algorithms.bst.BstState
import com.ttele.algoking.engine.catalog.AlgorithmCatalog
import com.ttele.algoking.engine.core.AlgorithmId
import com.ttele.algoking.engine.core.AlgorithmRunner
import com.ttele.algoking.engine.core.BinaryTree
import com.ttele.algoking.engine.core.Dataset
import com.ttele.algoking.engine.core.Probe
import com.ttele.algoking.engine.dataset.BstDatasets
import com.ttele.algoking.engine.decision.DecisionKind
import com.ttele.algoking.engine.decision.DecisionValidation
import com.ttele.algoking.engine.decision.Validation
import com.ttele.algoking.engine.event.Outcome
import com.ttele.algoking.engine.narration.NarrationId
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
 * Binary Search Tree.
 *
 * The search path is generated, never authored: every assertion below drives the
 * real engine and reads the path back out of the state it produced. The two that
 * matter most are the last ones — that a wrong decision cannot move the search,
 * and that no shape of tree or target can produce a state that crashes.
 */
class BstTest {

    private val algorithm = BinarySearchTreeAlgorithm()
    private val teaching = BstDatasets.teachingTree

    private fun dataset(tree: BinaryTree, target: Int) =
        Dataset(values = tree.inorder(), target = target, tree = tree)

    private fun runner(tree: BinaryTree, target: Int) =
        AlgorithmRunner(algorithm, dataset(tree, target))

    /** Drives the real algorithm to its terminal state, always choosing correctly. */
    private fun search(tree: BinaryTree, target: Int): BstState {
        val runner = runner(tree, target)
        var guard = 0
        while (guard++ < 500) {
            when (val probe = runner.probe()) {
                is Probe.Mechanical -> runner.apply(probe.action)
                is Probe.Decide -> runner.apply(probe.decision.correct)
                is Probe.Terminal -> return runner.current.state
            }
        }
        error("BST search did not terminate")
    }

    private fun outcome(tree: BinaryTree, target: Int): Outcome {
        val runner = runner(tree, target)
        var guard = 0
        while (guard++ < 500) {
            when (val probe = runner.probe()) {
                is Probe.Mechanical -> runner.apply(probe.action)
                is Probe.Decide -> runner.apply(probe.decision.correct)
                is Probe.Terminal -> return probe.outcome
            }
        }
        error("BST search did not terminate")
    }

    // ── The tree itself ──────────────────────────────────────────────────────

    @Test
    fun `the teaching tree is the authored shape`() {
        assertEquals(listOf(20, 30, 40, 50, 60, 70, 80), teaching.inorder())
        assertEquals(50, teaching.root?.value)
        assertEquals(30, teaching.root?.left?.value)
        assertEquals(70, teaching.root?.right?.value)
        assertEquals(20, teaching.root?.left?.left?.value)
        assertEquals(40, teaching.root?.left?.right?.value)
        assertEquals(60, teaching.root?.right?.left?.value)
        assertEquals(80, teaching.root?.right?.right?.value)
        assertEquals(7, teaching.size)
        assertEquals(3, teaching.height)
    }

    @Test
    fun `insertion keeps the ordering invariant`() {
        val tree = BinaryTree.of(50, 30, 70, 20, 40, 60, 80, 65, 10)
        assertEquals(tree.inorder(), tree.inorder().sorted())
        // Every value in a left subtree is smaller than its node, and every value
        // in a right subtree is larger. That is the whole structure, asserted.
        for (value in tree.inorder()) {
            val node = tree.node(value)!!
            node.left?.let { assertTrue(tree.subtree(it.value).all { v -> v < value }) }
            node.right?.let { assertTrue(tree.subtree(it.value).all { v -> v > value }) }
        }
    }

    @Test
    fun `inserting a value already present changes nothing`() {
        assertEquals(teaching, teaching.insert(50))
        assertEquals(teaching, teaching.insert(20))
        assertEquals(7, teaching.insert(70).size)
    }

    // ── The five paths the brief names ───────────────────────────────────────

    @Test
    fun `searching the root ends immediately`() {
        val state = search(teaching, 50)
        assertEquals(listOf(50), state.path)
        assertEquals(50, state.foundValue)
    }

    @Test
    fun `searching the left subtree goes left`() {
        assertEquals(listOf(50, 30), search(teaching, 30).path)
        assertEquals(listOf(50, 30, 20), search(teaching, 20).path)
    }

    @Test
    fun `searching the right subtree goes right`() {
        assertEquals(listOf(50, 70), search(teaching, 70).path)
    }

    @Test
    fun `the teaching search turns both ways`() {
        val state = search(teaching, 60)
        // 60 > 50 so RIGHT, then 60 < 70 so LEFT. The path is produced by the
        // engine; nothing in the lesson writes it down.
        assertEquals(listOf(50, 70, 60), state.path)
        assertEquals(60, state.foundValue)
        assertFalse(state.missing)
    }

    @Test
    fun `a missing value ends at a null child, not a crash`() {
        val state = search(teaching, 65)
        assertEquals(listOf(50, 70, 60), state.path)
        assertNull(state.foundValue)
        assertTrue(state.missing)
        assertEquals(Outcome.NotFound, outcome(teaching, 65))
    }

    @Test
    fun `the engine agrees with the structure about every path`() {
        // The reference is the data structure's own walk. They are written
        // independently — one steps through decisions, the other loops — and
        // every value in and out of the tree has to make them agree.
        for (target in 0..100) {
            assertEquals(
                "path for $target",
                teaching.searchPath(target),
                search(teaching, target).path,
            )
            assertEquals(
                "presence of $target",
                teaching.contains(target),
                search(teaching, target).foundValue != null,
            )
        }
    }

    // ── Edge cases ───────────────────────────────────────────────────────────

    @Test
    fun `a target below the minimum walks left and proves absence`() {
        val state = search(teaching, 5)
        assertEquals(listOf(50, 30, 20), state.path)
        assertTrue(state.missing)
    }

    @Test
    fun `a target above the maximum walks right and proves absence`() {
        val state = search(teaching, 95)
        assertEquals(listOf(50, 70, 80), state.path)
        assertTrue(state.missing)
    }

    @Test
    fun `a single-node tree finds its one value and misses everything else`() {
        val single = BinaryTree.of(42)
        assertEquals(listOf(42), search(single, 42).path)
        assertEquals(42, search(single, 42).foundValue)
        assertTrue(search(single, 41).missing)
        assertTrue(search(single, 43).missing)
    }

    @Test
    fun `an empty tree is a finished search, not a crash`() {
        val empty = BinaryTree.EMPTY
        val state = algorithm.initial(dataset(empty, 60))
        assertTrue(state.missing)
        assertNull(state.current)
        assertTrue(state.path.isEmpty())
        assertEquals(Outcome.NotFound, outcome(empty, 60))
        // And it still projects: an empty picture is a picture.
        assertTrue(BstProjector().project(state, emptyList()).nodes.isEmpty())
    }

    @Test
    fun `a null child is an answer rather than an exception`() {
        // 20 is a leaf: both children are null, and either direction terminates.
        val atLeaf = search(teaching, 15)
        assertEquals(listOf(50, 30, 20), atLeaf.path)
        assertTrue(atLeaf.missing)
        assertNull(atLeaf.current)
        // Probing a finished state is safe and keeps saying the same thing.
        val runner = runner(teaching, 15)
        var guard = 0
        while (guard++ < 500 && runner.probe() !is Probe.Terminal) {
            when (val probe = runner.probe()) {
                is Probe.Mechanical -> runner.apply(probe.action)
                is Probe.Decide -> runner.apply(probe.decision.correct)
                is Probe.Terminal -> Unit
            }
        }
        assertTrue(runner.probe() is Probe.Terminal)
        assertTrue(runner.probe() is Probe.Terminal)
    }

    @Test
    fun `a skewed tree still searches correctly, one node per level`() {
        // Inserted in ascending order, so it degenerates into a linked list —
        // the O(n) case the lesson is careful to say out loud.
        val skewed = BinaryTree.of(10, 20, 30, 40, 50)
        assertEquals(5, skewed.height)
        assertEquals(listOf(10, 20, 30, 40, 50), search(skewed, 50).path)
        assertEquals(50, search(skewed, 50).foundValue)
        // 35 is greater than 10, 20 and 30, then smaller than 40 — so it walks
        // the whole chain before the null left child proves it is absent. Five
        // values, four comparisons: the linked-list behaviour, demonstrated.
        assertEquals(listOf(10, 20, 30, 40), search(skewed, 35).path)
        assertTrue(search(skewed, 35).missing)
    }

    @Test
    fun `every search costs at most the height of the tree`() {
        val trees = listOf(teaching, BinaryTree.of(10, 20, 30, 40, 50), BinaryTree.of(42))
        for (tree in trees) {
            for (target in 0..100) {
                assertTrue(
                    "path longer than the tree is tall",
                    search(tree, target).path.size <= tree.height,
                )
            }
        }
    }

    // ── The decision ─────────────────────────────────────────────────────────

    @Test
    fun `the left-right decision is the learner's, at every node`() {
        val runner = runner(teaching, 60)
        // The comparison is the app's — reading two numbers is not a judgement.
        assertTrue(runner.probe() is Probe.Mechanical)
        runner.apply((runner.probe() as Probe.Mechanical).action)

        val probe = runner.probe()
        assertTrue(probe is Probe.Decide)
        val decision = (probe as Probe.Decide).decision
        assertEquals(DecisionKind.OPTIONS, decision.kind)
        // Which way to go is the lesson, so Try must never answer it.
        assertFalse(decision.autoInTry)
        assertEquals(BstAction.GoRight, decision.correct)
    }

    @Test
    fun `all three options are offered every round, including FOUND`() {
        // Offering FOUND only where it is correct would answer the question the
        // beat exists to ask: *is this the node?* (ADR-032.)
        for (target in listOf(60, 20, 50, 65)) {
            val runner = runner(teaching, target)
            var guard = 0
            while (guard++ < 50) {
                when (val probe = runner.probe()) {
                    is Probe.Mechanical -> runner.apply(probe.action)
                    is Probe.Terminal -> break
                    is Probe.Decide -> {
                        val actions = probe.decision.options.map { it.action }.toSet()
                        assertEquals(
                            setOf(BstAction.GoLeft, BstAction.GoRight, BstAction.Found),
                            actions,
                        )
                        assertTrue(probe.decision.correct in actions)
                        runner.apply(probe.decision.correct)
                    }
                }
            }
        }
    }

    @Test
    fun `every decision carries a full guidance ladder and a reason per option`() {
        val runner = runner(teaching, 60)
        var decisions = 0
        var guard = 0
        while (guard++ < 50) {
            when (val probe = runner.probe()) {
                is Probe.Mechanical -> runner.apply(probe.action)
                is Probe.Terminal -> break
                is Probe.Decide -> {
                    val decision = probe.decision
                    decisions++
                    assertEquals(3, decision.guidance.size)
                    assertNotNull(decision.hint)
                    // Two wrong options, each with its own line naming what is
                    // wrong with *that* choice. "Wrong" is never said.
                    assertEquals(2, decision.whyWrong.size)
                    assertFalse(decision.correct in decision.whyWrong.keys)
                    runner.apply(decision.correct)
                }
            }
        }
        assertEquals(3, decisions)
    }

    // ── The invariant: a wrong answer is a learning event ────────────────────

    @Test
    fun `a wrong decision never moves the search`() {
        val runner = runner(teaching, 60)
        runner.apply((runner.probe() as Probe.Mechanical).action)
        val decision = (runner.probe() as Probe.Decide).decision
        val before = runner.current.state

        // Five wrong answers, through the path Try actually uses.
        repeat(5) { attempt ->
            val verdict = DecisionValidation.validate(decision, BstAction.GoLeft, attempt)
            assertTrue(verdict is Validation.Retry)
            // A Retry carries no action, so there is nothing the caller could
            // apply — the rule is structural (ARCHITECTURE.md §6.1).
            assertEquals(before, runner.current.state)
        }
        // Claiming FOUND on a node that does not match is refused the same way.
        assertTrue(
            DecisionValidation.validate(decision, BstAction.Found, 0) is Validation.Retry,
        )
        assertEquals(before, runner.current.state)

        // And the correct answer still works afterwards.
        val accepted = DecisionValidation.validate(decision, BstAction.GoRight, 5)
        assertTrue(accepted is Validation.Accept)
        runner.apply((accepted as Validation.Accept).action)
        assertEquals(listOf(50, 70), runner.current.state.path)
    }

    @Test
    fun `guidance escalates and then holds`() {
        val runner = runner(teaching, 60)
        runner.apply((runner.probe() as Probe.Mechanical).action)
        val decision = (runner.probe() as Probe.Decide).decision

        val levels = (0..5).map { attempt ->
            (DecisionValidation.validate(decision, BstAction.GoLeft, attempt) as Validation.Retry)
                .level
        }
        assertEquals(listOf(1, 2, 3, 4, 5, 6), levels)
        // The most explicit rung repeats rather than running out — a learner is
        // never dead-ended.
        val last = (DecisionValidation.validate(decision, BstAction.GoLeft, 9) as Validation.Retry)
        assertEquals(decision.guidance.last(), last.guidance)
    }

    @Test
    fun `applying a wrong direction still leaves a legal state`() {
        // `apply` is total by design (ADR-001): the wrong action is the same code
        // path with a different argument. Try never calls it this way, but the
        // state on the other side still has to be one the algorithm could be in.
        val start = algorithm.initial(dataset(teaching, 60))
        val compared = algorithm.apply(start, BstAction.Compare).next
        val wrong = algorithm.apply(compared, BstAction.GoLeft)

        assertFalse("a wrong move is not scored as correct", wrong.correct)
        val state = wrong.next
        assertEquals(listOf(50, 30), state.path)
        assertNull("the comparison is re-read at the new node", state.comparison)
        // Still a real search state: it terminates, and it never claims a find.
        var guard = 0
        var runner = state
        while (guard++ < 50 && !runner.finished) {
            val probe = algorithm.probe(runner)
            runner = when (probe) {
                is Probe.Mechanical -> algorithm.apply(runner, probe.action).next
                is Probe.Decide -> algorithm.apply(runner, probe.decision.correct).next
                is Probe.Terminal -> break
            }
        }
        assertTrue(runner.finished)
        assertNull(runner.foundValue)
    }

    @Test
    fun `claiming FOUND on the wrong node changes nothing at all`() {
        val start = algorithm.initial(dataset(teaching, 60))
        val compared = algorithm.apply(start, BstAction.Compare).next
        val claimed = algorithm.apply(compared, BstAction.Found)
        assertFalse(claimed.correct)
        assertEquals(compared, claimed.next)
    }

    // ── Elimination, which is the point being made ───────────────────────────

    @Test
    fun `each comparison rules out the other subtree`() {
        val runner = runner(teaching, 60)
        fun advance() {
            when (val probe = runner.probe()) {
                is Probe.Mechanical -> runner.apply(probe.action)
                is Probe.Decide -> runner.apply(probe.decision.correct)
                is Probe.Terminal -> Unit
            }
        }
        // Nothing is ruled out before anything is compared.
        assertTrue(runner.current.state.eliminated.isEmpty())

        advance() // compare 60 with 50
        advance() // go right
        assertEquals(setOf(20, 30, 40), runner.current.state.eliminated)

        advance() // compare 60 with 70
        advance() // go left
        assertEquals(setOf(20, 30, 40, 80), runner.current.state.eliminated)

        // Four of seven nodes were never looked at, and the search touched three.
        val end = search(teaching, 60)
        assertEquals(4, end.eliminated.size)
        assertEquals(3, end.path.size)
        assertEquals(teaching.size, end.eliminated.size + end.path.size)
    }

    // ── The picture ──────────────────────────────────────────────────────────

    @Test
    fun `positions are derived from the tree, and no two nodes collide`() {
        for (tree in listOf(teaching, BinaryTree.of(10, 20, 30, 40, 50), BinaryTree.of(42))) {
            val layout = tree.layout()
            assertEquals(tree.size, layout.size)
            assertTrue(layout.all { it.x in 0f..1f && it.y in 0f..1f })
            // In-order x means every node has a column of its own.
            assertEquals(layout.size, layout.map { it.x }.toSet().size)
            // A parent is always drawn above its children, and horizontally
            // between them — which is what makes the drawing read as a tree.
            for ((parent, child) in tree.edges()) {
                val p = layout.first { it.value == parent }
                val c = layout.first { it.value == child }
                assertTrue("$parent should sit above $child", p.y < c.y)
                if (child < parent) assertTrue(c.x < p.x) else assertTrue(c.x > p.x)
            }
        }
        // The teaching tree's root is centred, because its in-order position is.
        assertEquals(0.5f, teaching.layout().first { it.value == 50 }.x, 0.001f)
    }

    @Test
    fun `nodes drawn on the same row are never adjacent columns`() {
        // Why labels cannot overlap, stated as a property rather than checked
        // once by eye on one screen size: two nodes at the same depth always have
        // their lowest common ancestor sitting between them in in-order, so they
        // are at least **two** columns apart. On the teaching tree that is a third
        // of the width between same-row neighbours — a 48dp node with room to
        // spare even on a 320dp phone, at any width, without shrinking anything.
        for (tree in listOf(teaching, BinaryTree.of(50, 30, 70, 40, 60), BinaryTree.of(1, 2, 3))) {
            val layout = tree.layout()
            val columns = layout.withIndex().associate { (index, node) -> node.value to index }
            for (a in layout) {
                for (b in layout) {
                    if (a.value == b.value || a.depth != b.depth) continue
                    val gap = kotlin.math.abs(columns.getValue(a.value) - columns.getValue(b.value))
                    assertTrue("${a.value} and ${b.value} share a row $gap column apart", gap >= 2)
                }
            }
        }
    }

    @Test
    fun `the scene says where the search is, has been, and will never go`() {
        val projector = BstProjector()
        val end = search(teaching, 60)
        val scene = projector.project(end, emptyList())

        fun stateOf(label: String) = scene.nodes.first { it.label == label }.state
        assertEquals(CellState.FINALIZED, stateOf("60"))
        assertEquals(CellState.FINALIZED, stateOf("50"))
        assertEquals(CellState.FINALIZED, stateOf("70"))
        assertEquals(CellState.ELIMINATED, stateOf("30"))
        assertEquals(CellState.ELIMINATED, stateOf("20"))
        assertEquals(CellState.ELIMINATED, stateOf("40"))
        assertEquals(CellState.ELIMINATED, stateOf("80"))

        // The path strip is read from engine state, so it cannot disagree.
        assertEquals(listOf("50", "70", "60"), scene.traversal)
        assertEquals("Search path", scene.traversalLabel)
        assertFalse(scene.showPathStrip)
        assertEquals(60, scene.badge?.value)
        // Branches into ruled-out subtrees are drawn, and drawn as out of play.
        assertTrue(scene.edges.any { it.state == EdgeState.ELIMINATED })
        assertTrue(scene.edges.any { it.state == EdgeState.PATH || it.state == EdgeState.ACTIVE })
    }

    @Test
    fun `the current node is the only one comparing, and only before it is found`() {
        val projector = BstProjector()
        val start = algorithm.initial(dataset(teaching, 60))
        val scene = projector.project(start, emptyList())
        assertEquals(
            listOf("50"),
            scene.nodes.filter { it.state == CellState.COMPARING }.map { it.label },
        )
    }

    // ── WATCH ────────────────────────────────────────────────────────────────

    @Test
    fun `the walkthrough compares before it moves, at every node`() {
        val steps = AlgorithmCatalog.binarySearchTree().watchScript().steps
        val kinds = steps.map { it.kind }

        assertEquals(WatchStepKind.SETUP, kinds.first())
        assertEquals(WatchStepKind.INSIGHT, kinds[kinds.lastIndex - 1])
        assertEquals(WatchStepKind.SUMMARY, kinds.last())

        // Three comparisons, two moves, one find — and a COMPARE always precedes
        // the ELIMINATE it justifies. Showing a search that has already moved
        // beside the reason it should move is the wrong order to think in.
        assertEquals(3, kinds.count { it == WatchStepKind.COMPARE })
        assertEquals(2, kinds.count { it == WatchStepKind.ELIMINATE })
        assertEquals(1, kinds.count { it == WatchStepKind.FOUND })
        val firstMove = kinds.indexOf(WatchStepKind.ELIMINATE)
        assertTrue(kinds.indexOf(WatchStepKind.COMPARE) < firstMove)
    }

    @Test
    fun `every comparison step carries its readout chip`() {
        val steps = AlgorithmCatalog.binarySearchTree().watchScript().steps
        val compares = steps.filter { it.kind == WatchStepKind.COMPARE }
        assertTrue(compares.all { it.comparison != null })
        // The chip reads "target ? node", in that order, so it matches the
        // sentence beside it.
        assertEquals(listOf(60, 60, 60), compares.map { it.comparison!!.left })
        assertEquals(listOf(50, 70, 60), compares.map { it.comparison!!.right })
    }

    @Test
    fun `no two adjacent walkthrough steps are identical`() {
        // ADR-020: a step where nothing changed is a bug, not a beat.
        val steps = AlgorithmCatalog.binarySearchTree().watchScript().steps
        steps.zipWithNext().forEach { (a, b) ->
            assertTrue(
                "steps ${a.index} and ${b.index} are identical",
                a.scene != b.scene || a.headline != b.headline || a.comparison != b.comparison,
            )
        }
    }

    @Test
    fun `the walkthrough is exactly the ten beats the lesson was designed as`() {
        // Pinned, so "just narrate one more thing" cannot quietly turn a lesson
        // into a slideshow — and so the shape of the teaching run is reviewable
        // here rather than only on a device.
        val steps = AlgorithmCatalog.binarySearchTree().watchScript().steps
        assertEquals(
            listOf(
                WatchStepKind.SETUP to NarrationId.BST_WATCH_SETUP,
                WatchStepKind.EXAMINE to NarrationId.BST_WATCH_ROOT,
                // 60 > 50
                WatchStepKind.COMPARE to NarrationId.BST_WATCH_COMPARE_GREATER,
                WatchStepKind.ELIMINATE to NarrationId.BST_WATCH_MOVE_RIGHT,
                // 60 < 70
                WatchStepKind.COMPARE to NarrationId.BST_WATCH_COMPARE_LESS,
                WatchStepKind.ELIMINATE to NarrationId.BST_WATCH_MOVE_LEFT,
                // 60 == 60
                WatchStepKind.COMPARE to NarrationId.BST_WATCH_COMPARE_EQUAL,
                WatchStepKind.FOUND to NarrationId.BST_WATCH_FOUND,
                WatchStepKind.INSIGHT to NarrationId.BST_WATCH_INSIGHT,
                WatchStepKind.SUMMARY to NarrationId.BST_WATCH_SUMMARY,
            ),
            steps.map { it.kind to it.headline.id },
        )
    }

    @Test
    fun `the walkthrough is long enough to teach and short enough to finish`() {
        val steps = AlgorithmCatalog.binarySearchTree().watchScript().steps
        assertTrue("${steps.size} steps", steps.size in 8..14)
        // The recap names the rule, in one bullet per branch plus the two ideas
        // the lesson exists to leave behind.
        assertEquals(5, steps.last().bullets.size)
    }

    // ── Wiring ───────────────────────────────────────────────────────────────

    @Test
    fun `the lesson is wired into the catalogue with both stages`() {
        val pack = AlgorithmCatalog.byId(AlgorithmId.BINARY_SEARCH_TREE)
        assertEquals(AlgorithmId.BINARY_SEARCH_TREE, pack.id)
        assertEquals("Binary Search Tree", pack.displayName)
        assertNotNull(pack.watchDataset.tree)
        assertNotNull(pack.tryDataset.tree)
        assertEquals(60, pack.watchDataset.target)
        assertEquals(60, pack.tryDataset.target)
        // `values` is the tree read in order, which is the sentence that connects
        // this lesson to Binary Search.
        assertEquals(pack.watchDataset.values, pack.watchDataset.values.sorted())
    }
}
