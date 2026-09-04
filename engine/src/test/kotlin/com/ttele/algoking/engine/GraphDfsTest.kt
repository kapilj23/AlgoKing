package com.ttele.algoking.engine

import com.ttele.algoking.engine.algorithms.graphdfs.DepthFirstSearchAlgorithm
import com.ttele.algoking.engine.algorithms.graphdfs.DfsAction
import com.ttele.algoking.engine.algorithms.graphdfs.DfsProjector
import com.ttele.algoking.engine.algorithms.graphdfs.DfsState
import com.ttele.algoking.engine.catalog.AlgorithmCatalog
import com.ttele.algoking.engine.core.AlgorithmId
import com.ttele.algoking.engine.core.AlgorithmRunner
import com.ttele.algoking.engine.core.Dataset
import com.ttele.algoking.engine.core.Graph
import com.ttele.algoking.engine.core.GraphNode
import com.ttele.algoking.engine.core.Probe
import com.ttele.algoking.engine.dataset.GraphDatasets
import com.ttele.algoking.engine.decision.DecisionKind
import com.ttele.algoking.engine.decision.DecisionValidation
import com.ttele.algoking.engine.decision.Validation
import com.ttele.algoking.engine.scene.CellState
import com.ttele.algoking.engine.scene.EdgeState
import com.ttele.algoking.engine.walkthrough.WatchStepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Graph DFS.
 *
 * The traversal is **generated**, never authored: every test below drives the
 * real engine and reads `visited` out of it. Nothing anywhere in the lesson
 * writes down `A → B → D → E → C` as an answer.
 */
class GraphDfsTest {

    private val algorithm = DepthFirstSearchAlgorithm()

    /** The lesson graph: A-B, A-C, B-D, B-E. */
    private val teaching = GraphDatasets.teachingGraph

    private fun graph(
        vararg adjacency: Pair<String, List<String>>,
    ): Graph {
        val ids = adjacency.map { it.first }
        return Graph(
            nodes = ids.mapIndexed { i, id -> GraphNode(id, id, i * 0.2f, 0.5f) },
            adjacency = adjacency.toMap(),
        )
    }

    private fun runner(g: Graph, start: String) =
        AlgorithmRunner(algorithm, Dataset(values = emptyList(), graph = g, startNode = start))

    /** Drive the way WATCH does, and return the finished state. */
    private fun traverse(g: Graph, start: String): DfsState {
        val r = runner(g, start)
        var guard = 0
        while (guard++ < 500) {
            when (val probe = r.probe()) {
                is Probe.Mechanical -> r.apply(probe.action)
                is Probe.Decide -> r.apply(probe.decision.correct)
                is Probe.Terminal -> return r.current.state
            }
        }
        error("DFS did not terminate.")
    }

    private fun order(g: Graph, start: String): List<String> = traverse(g, start).visited

    // -- 1. The traversal ------------------------------------------------------

    @Test
    fun `the teaching graph traverses A B D E C`() {
        assertEquals(listOf("A", "B", "D", "E", "C"), order(teaching, "A"))
    }

    @Test
    fun `the traversal comes from the engine, not from the dataset`() {
        // Nothing in the dataset names an order; it names adjacency, and the
        // order falls out of visited state.
        assertEquals(
            listOf("A", "B", "D", "E", "C"),
            traverse(GraphDatasets.watch.graph!!, GraphDatasets.watch.startNode!!).visited,
        )
    }

    @Test
    fun `neighbour order decides the traversal, and nothing else does`() {
        // The same five nodes and the same edges, with A's neighbours reversed.
        val flipped = Graph(
            nodes = teaching.nodes,
            adjacency = mapOf(
                "A" to listOf("C", "B"),
                "B" to listOf("A", "D", "E"),
                "C" to listOf("A"),
                "D" to listOf("B"),
                "E" to listOf("B"),
            ),
        )
        assertEquals(listOf("A", "C", "B", "D", "E"), order(flipped, "A"))
    }

    @Test
    fun `depth beats breadth - C waits until the whole B branch is done`() {
        // The claim that makes this depth-first. C is adjacent to the start and is
        // still visited last.
        val visited = order(teaching, "A")
        assertTrue(visited.indexOf("D") < visited.indexOf("C"))
        assertTrue(visited.indexOf("E") < visited.indexOf("C"))
        assertEquals("C", visited.last())
    }

    // -- 2. Visited handling ---------------------------------------------------

    @Test
    fun `a node is never visited twice`() {
        val visited = order(teaching, "A")
        assertEquals(visited.size, visited.distinct().size)
    }

    @Test
    fun `every reachable node is visited exactly once`() {
        val end = traverse(teaching, "A")
        assertEquals(end.reachable, end.visited.toSet())
    }

    @Test
    fun `revisiting a visited node is refused and changes nothing`() {
        val r = runner(teaching, "A")
        r.apply(DfsAction.Go("A"))
        r.apply(DfsAction.Go("B"))
        val before = r.current.state

        // A is a neighbour of B and already visited. It is also B's parent, so
        // the honest reading is "backtrack" - use D's dead end instead for a
        // genuine revisit attempt.
        r.apply(DfsAction.Go("D"))
        val atD = r.current.state
        val after = algorithm.apply(atD, DfsAction.Go("D"))
        assertEquals(atD.visited, after.next.visited)
        assertEquals(atD.stack, after.next.stack)
        assertFalse(after.correct)
        assertNotNull(before)
    }

    // -- 3. Backtracking -------------------------------------------------------

    @Test
    fun `a dead end is recognised, and backtracking is the correct move`() {
        val r = runner(teaching, "A")
        r.apply(DfsAction.Go("A"))
        r.apply(DfsAction.Go("B"))
        r.apply(DfsAction.Go("D"))

        val state = r.current.state
        assertTrue(state.atDeadEnd)
        assertEquals("B", state.parent)
        assertEquals(DfsAction.Go("B"), (r.probe() as Probe.Decide).decision.correct)
    }

    @Test
    fun `backtracking pops the stack without un-visiting anything`() {
        val r = runner(teaching, "A")
        r.apply(DfsAction.Go("A"))
        r.apply(DfsAction.Go("B"))
        r.apply(DfsAction.Go("D"))
        val before = r.current.state

        r.apply(DfsAction.Go("B"))
        val after = r.current.state
        assertEquals(listOf("A", "B"), after.stack)
        assertEquals(before.visited, after.visited)
        assertTrue(after.backtracked)
    }

    @Test
    fun `the run needs exactly three backtracks, and they are not all alike`() {
        // D -> B and E -> B are leaf dead ends; B -> A is a node whose branches are
        // all used up. A graph with only one kind would teach backtracking as a
        // special case rather than as the rule.
        val r = runner(teaching, "A")
        var backtracks = 0
        var guard = 0
        while (guard++ < 100) {
            when (val probe = r.probe()) {
                is Probe.Mechanical -> r.apply(probe.action)
                is Probe.Decide -> {
                    val before = r.current.state.stack.size
                    r.apply(probe.decision.correct)
                    if (r.current.state.stack.size < before) backtracks++
                }

                is Probe.Terminal -> break
            }
        }
        assertEquals(3, backtracks)
    }

    @Test
    fun `backtracking too early is wrong`() {
        val r = runner(teaching, "A")
        r.apply(DfsAction.Go("A"))
        r.apply(DfsAction.Go("B"))
        // B still has D and E unvisited, so going back to A is premature.
        val decision = (r.probe() as Probe.Decide).decision
        assertEquals(DfsAction.Go("D"), decision.correct)
        val verdict = DecisionValidation.validate(decision, DfsAction.Go("A"), 0)
        assertTrue(verdict is Validation.Retry)
        assertNotNull(decision.whyWrong[DfsAction.Go("A")])
    }

    // -- 4. Cycles -------------------------------------------------------------

    @Test
    fun `a cycle terminates and visits each node once`() {
        val cycle = graph(
            "A" to listOf("B", "C"),
            "B" to listOf("A", "C"),
            "C" to listOf("A", "B"),
        )
        assertEquals(listOf("A", "B", "C"), order(cycle, "A"))
    }

    @Test
    fun `a larger cycle still terminates`() {
        val ring = graph(
            "A" to listOf("B", "E"),
            "B" to listOf("A", "C"),
            "C" to listOf("B", "D"),
            "D" to listOf("C", "E"),
            "E" to listOf("D", "A"),
        )
        assertEquals(listOf("A", "B", "C", "D", "E"), order(ring, "A"))
    }

    @Test
    fun `a self loop does not trap the traversal`() {
        val selfish = graph(
            "A" to listOf("A", "B"),
            "B" to listOf("A"),
        )
        assertEquals(listOf("A", "B"), order(selfish, "A"))
    }

    // -- 5. Edge cases ---------------------------------------------------------

    @Test
    fun `an empty graph terminates immediately`() {
        val r = AlgorithmRunner(
            algorithm,
            Dataset(values = emptyList(), graph = Graph(emptyList(), emptyMap())),
        )
        assertTrue(r.probe() is Probe.Terminal)
    }

    @Test
    fun `a single node is visited and the run ends`() {
        val single = graph("A" to emptyList())
        assertEquals(listOf("A"), order(single, "A"))
    }

    @Test
    fun `a node with no neighbours is a dead end straight away`() {
        val isolated = graph("A" to emptyList(), "B" to emptyList())
        // Only A is reachable, so the run ends after visiting it.
        assertEquals(listOf("A"), order(isolated, "A"))
    }

    @Test
    fun `a disconnected component is not traversed`() {
        val split = graph(
            "A" to listOf("B"),
            "B" to listOf("A"),
            "C" to listOf("D"),
            "D" to listOf("C"),
        )
        assertEquals(listOf("A", "B"), order(split, "A"))
        // Starting in the other component visits only that one.
        assertEquals(listOf("C", "D"), order(split, "C"))
    }

    @Test
    fun `a start node that is not in the graph terminates rather than crashing`() {
        val r = AlgorithmRunner(
            algorithm,
            Dataset(values = emptyList(), graph = teaching, startNode = "Z"),
        )
        // Falls back to the first node rather than exploding, and still terminates.
        assertNotNull(r.runToCompletion())
    }

    @Test
    fun `an edge naming an unknown node is ignored`() {
        val broken = Graph(
            nodes = listOf(GraphNode("A", "A", 0f, 0f), GraphNode("B", "B", 1f, 1f)),
            adjacency = mapOf("A" to listOf("B", "GHOST"), "B" to listOf("A")),
        )
        assertEquals(listOf("A", "B"), order(broken, "A"))
    }

    @Test
    fun `a different start node gives a different traversal`() {
        assertEquals(listOf("D", "B", "A", "C", "E"), order(teaching, "D"))
        assertEquals(listOf("C", "A", "B", "D", "E"), order(teaching, "C"))
    }

    @Test
    fun `every start node terminates and reaches its whole component`() {
        for (node in teaching.nodes) {
            val end = traverse(teaching, node.id)
            assertEquals(end.reachable, end.visited.toSet())
        }
    }

    // -- 6. The decision -------------------------------------------------------

    @Test
    fun `the decision is a tap on the graph, not a row of buttons`() {
        val r = runner(teaching, "A")
        r.apply(DfsAction.Go("A"))
        val decision = (r.probe() as Probe.Decide).decision
        assertEquals(DecisionKind.CELL, decision.kind)
        // Every option carries the slot of the node it selects.
        assertTrue(decision.options.all { it.slot != null && it.slot!! >= 0 })
    }

    @Test
    fun `all three judgements are askable from one tap`() {
        // At B: A is visited (skip it), D is the right branch, E is the wrong
        // branch, and A is also the parent (backtrack too early).
        val r = runner(teaching, "A")
        r.apply(DfsAction.Go("A"))
        r.apply(DfsAction.Go("B"))
        val decision = (r.probe() as Probe.Decide).decision

        val offered = decision.options.map { (it.action as DfsAction.Go).node }.toSet()
        assertEquals(setOf("A", "D", "E"), offered)
        assertEquals(DfsAction.Go("D"), decision.correct)
        // Each wrong tap has its own explanation.
        assertNotNull(decision.whyWrong[DfsAction.Go("A")])
        assertNotNull(decision.whyWrong[DfsAction.Go("E")])
    }

    @Test
    fun `a wrong tap never changes the graph state`() {
        val r = runner(teaching, "A")
        r.apply(DfsAction.Go("A"))
        val decision = (r.probe() as Probe.Decide).decision
        val before = r.current.state

        val wrong = decision.options
            .map { it.action }
            .filter { it != decision.correct }
        for (attempt in 0..4) {
            for (action in wrong) {
                assertTrue(
                    DecisionValidation.validate(decision, action, attempt) is Validation.Retry,
                )
                assertEquals(before, r.current.state)
            }
        }
    }

    @Test
    fun `guidance escalates and then holds`() {
        val r = runner(teaching, "A")
        r.apply(DfsAction.Go("A"))
        val decision = (r.probe() as Probe.Decide).decision
        val wrong = decision.options.map { it.action }.first { it != decision.correct }
        val rungs = (0..5).map {
            (DecisionValidation.validate(decision, wrong, it) as Validation.Retry).guidance
        }
        assertEquals(3, rungs.take(3).distinct().size)
        assertEquals(rungs[2], rungs[5])
    }

    @Test
    fun `the correct action is always among the options, from every state`() {
        for (node in teaching.nodes) {
            val r = runner(teaching, node.id)
            var guard = 0
            while (guard++ < 100) {
                when (val probe = r.probe()) {
                    is Probe.Mechanical -> r.apply(probe.action)
                    is Probe.Decide -> {
                        val d = probe.decision
                        assertTrue(d.options.any { it.action == d.correct })
                        r.apply(d.correct)
                    }

                    is Probe.Terminal -> break
                }
            }
        }
    }

    // -- 7. The picture --------------------------------------------------------

    @Test
    fun `the projector marks current, visited and unvisited`() {
        val r = runner(teaching, "A")
        r.apply(DfsAction.Go("A"))
        r.apply(DfsAction.Go("B"))
        val scene = DfsProjector().project(r.current.state, r.current.events)

        fun stateOf(label: String) = scene.nodes.first { it.label == label }.state
        assertEquals(CellState.COMPARING, stateOf("B"))
        assertEquals(CellState.FINALIZED, stateOf("A"))
        assertEquals(CellState.IDLE, stateOf("C"))
        assertEquals(CellState.IDLE, stateOf("D"))
    }

    @Test
    fun `the path DFS came down is drawn, and it is the stack`() {
        val r = runner(teaching, "A")
        r.apply(DfsAction.Go("A"))
        r.apply(DfsAction.Go("B"))
        r.apply(DfsAction.Go("D"))
        val scene = DfsProjector().project(r.current.state, r.current.events)

        assertEquals(listOf("A", "B", "D"), scene.stack)
        assertEquals(listOf("A", "B", "D"), scene.traversal)
        // A-B is on the path; A-C is not.
        val onPath = scene.edges.filter { it.state != EdgeState.IDLE }
        assertTrue(onPath.isNotEmpty())
    }

    @Test
    fun `the traversal strip is the visited list, never assembled separately`() {
        val end = traverse(teaching, "A")
        val scene = DfsProjector().project(end, emptyList())
        assertEquals(end.visited, scene.traversal)
    }

    // -- 8. The walkthrough ----------------------------------------------------

    @Test
    fun `every watch step changes something visible - ADR-020`() {
        val steps = AlgorithmCatalog.graphDfs().watchScript().steps
        steps.zipWithNext { a, b ->
            val changed = a.scene != b.scene ||
                a.headline != b.headline ||
                a.support != b.support ||
                a.bullets != b.bullets
            assertTrue("steps ${a.index} and ${b.index} are identical", changed)
        }
    }

    @Test
    fun `the walkthrough narrates every backtrack`() {
        // The half of DFS learners lose. If these beats are ever dropped the
        // lesson becomes a list being written down.
        val steps = AlgorithmCatalog.graphDfs().watchScript().steps
        assertEquals(3, steps.count { it.kind == WatchStepKind.ELIMINATE })
    }

    @Test
    fun `the script ends INSIGHT then SUMMARY with the rules as bullets`() {
        val steps = AlgorithmCatalog.graphDfs().watchScript().steps
        assertEquals(WatchStepKind.SUMMARY, steps.last().kind)
        assertEquals(WatchStepKind.INSIGHT, steps[steps.lastIndex - 1].kind)
        assertEquals(4, steps.last().bullets.size)
    }

    @Test
    fun `the walkthrough stays short enough to be a lesson`() {
        val size = AlgorithmCatalog.graphDfs().watchScript().size
        assertTrue("walkthrough is $size steps", size in 8..16)
    }

    // -- 9. WATCH and TRY are the same rules -----------------------------------

    @Test
    fun `WATCH and TRY run the same graph through the same engine`() {
        val pack = AlgorithmCatalog.graphDfs()
        val watch = traverse(pack.watchDataset.graph!!, pack.watchDataset.startNode!!)
        val tryIt = traverse(pack.tryDataset.graph!!, pack.tryDataset.startNode!!)
        assertEquals(watch.visited, tryIt.visited)
        assertEquals(listOf("A", "B", "D", "E", "C"), tryIt.visited)
    }

    @Test
    fun `the catalog resolves Graph DFS like any other lesson`() {
        val pack = AlgorithmCatalog.byId(AlgorithmId.GRAPH_DFS)
        assertEquals(AlgorithmId.GRAPH_DFS, pack.id)
        assertEquals("Graph DFS", pack.displayName)
    }
}
