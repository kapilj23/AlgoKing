package com.ttele.algoking.engine

import com.ttele.algoking.engine.algorithms.graphbfs.BfsAction
import com.ttele.algoking.engine.algorithms.graphbfs.BfsProjector
import com.ttele.algoking.engine.algorithms.graphbfs.BfsState
import com.ttele.algoking.engine.algorithms.graphbfs.BreadthFirstSearchAlgorithm
import com.ttele.algoking.engine.algorithms.graphdfs.DepthFirstSearchAlgorithm
import com.ttele.algoking.engine.catalog.AlgorithmCatalog
import com.ttele.algoking.engine.core.AlgorithmId
import com.ttele.algoking.engine.core.AlgorithmRunner
import com.ttele.algoking.engine.core.Dataset
import com.ttele.algoking.engine.core.Graph
import com.ttele.algoking.engine.core.GraphNode
import com.ttele.algoking.engine.core.Probe
import com.ttele.algoking.engine.dataset.BfsDatasets
import com.ttele.algoking.engine.dataset.GraphDatasets
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
 * Graph BFS.
 *
 * The traversal is generated, never authored. The most important assertions here
 * are the ones about the **queue**: that a node is marked visited when it is
 * enqueued rather than dequeued, and that first-in-first-out is what produces
 * level order.
 */
class GraphBfsTest {

    private val algorithm = BreadthFirstSearchAlgorithm()
    private val teaching = GraphDatasets.teachingGraph

    private fun graph(vararg adjacency: Pair<String, List<String>>): Graph {
        val ids = adjacency.map { it.first }
        return Graph(
            nodes = ids.mapIndexed { i, id -> GraphNode(id, id, i * 0.2f, 0.5f) },
            adjacency = adjacency.toMap(),
        )
    }

    private fun runner(g: Graph, start: String) =
        AlgorithmRunner(algorithm, Dataset(values = emptyList(), graph = g, startNode = start))

    private fun traverse(g: Graph, start: String): BfsState {
        val r = runner(g, start)
        var guard = 0
        while (guard++ < 500) {
            when (val probe = r.probe()) {
                is Probe.Mechanical -> r.apply(probe.action)
                is Probe.Decide -> r.apply(probe.decision.correct)
                is Probe.Terminal -> return r.current.state
            }
        }
        error("BFS did not terminate.")
    }

    /** The traversal is the DEQUEUE order. */
    private fun order(g: Graph, start: String): List<String> = traverse(g, start).dequeued

    // -- 1. The traversal ------------------------------------------------------

    @Test
    fun `the teaching graph traverses A B C D E`() {
        assertEquals(listOf("A", "B", "C", "D", "E"), order(teaching, "A"))
    }

    @Test
    fun `BFS and DFS differ on the same graph, and only the structure differs`() {
        // The claim the pair of lessons exists to make. Same graph, same adjacency
        // order, same start node — a queue instead of a stack.
        val bfs = order(teaching, "A")

        val dfsRunner = AlgorithmRunner(
            DepthFirstSearchAlgorithm(),
            Dataset(values = emptyList(), graph = teaching, startNode = "A"),
        )
        val dfs = dfsRunner.runToCompletion().frames.last().state.visited

        assertEquals(listOf("A", "B", "C", "D", "E"), bfs)
        assertEquals(listOf("A", "B", "D", "E", "C"), dfs)
        assertTrue("the two traversals must differ", bfs != dfs)
        assertEquals("both must reach every node", bfs.toSet(), dfs.toSet())
    }

    @Test
    fun `the traversal comes from the engine, not the dataset`() {
        assertEquals(
            listOf("A", "B", "C", "D", "E"),
            traverse(BfsDatasets.watch.graph!!, BfsDatasets.watch.startNode!!).dequeued,
        )
    }

    @Test
    fun `neighbour order decides the traversal`() {
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
    fun `level order - everything one step from A precedes anything two steps away`() {
        val visited = order(teaching, "A")
        val levelOne = listOf("B", "C")
        val levelTwo = listOf("D", "E")
        for (near in levelOne) {
            for (far in levelTwo) {
                assertTrue(
                    "$near must precede $far",
                    visited.indexOf(near) < visited.indexOf(far),
                )
            }
        }
    }

    // -- 2. The queue ----------------------------------------------------------

    @Test
    fun `the queue is first in, first out`() {
        val r = runner(teaching, "A")
        r.apply(BfsAction.Go("A"))          // seed
        r.apply(BfsAction.Go("A"))          // dequeue A
        r.apply(BfsAction.Go("B"))          // enqueue B
        r.apply(BfsAction.Go("C"))          // enqueue C
        assertEquals(listOf("B", "C"), r.current.state.queue)

        // B joined first, so B leaves first.
        r.apply(BfsAction.Go("B"))
        assertEquals(listOf("C"), r.current.state.queue)
        assertEquals(listOf("A", "B"), r.current.state.dequeued)
    }

    @Test
    fun `B and C are enqueued before D and E`() {
        // The step that makes it level-order rather than depth-first.
        val r = runner(teaching, "A")
        r.apply(BfsAction.Go("A"))
        r.apply(BfsAction.Go("A"))
        r.apply(BfsAction.Go("B"))
        r.apply(BfsAction.Go("C"))
        r.apply(BfsAction.Go("B"))          // dequeue B
        r.apply(BfsAction.Go("D"))
        r.apply(BfsAction.Go("E"))
        assertEquals(listOf("C", "D", "E"), r.current.state.queue)
    }

    @Test
    fun `a node is marked visited when it is enqueued, not when it is dequeued`() {
        // The rule that keeps BFS correct on a cyclic graph.
        val r = runner(teaching, "A")
        r.apply(BfsAction.Go("A"))
        r.apply(BfsAction.Go("A"))
        r.apply(BfsAction.Go("B"))

        val state = r.current.state
        assertTrue("B is visited the moment it is queued", state.isVisited("B"))
        assertTrue(state.queue.contains("B"))
        assertFalse("B has not been processed yet", state.dequeued.contains("B"))
    }

    @Test
    fun `an already visited neighbour is never enqueued again`() {
        val r = runner(teaching, "A")
        r.apply(BfsAction.Go("A"))
        r.apply(BfsAction.Go("A"))
        r.apply(BfsAction.Go("B"))
        r.apply(BfsAction.Go("C"))
        r.apply(BfsAction.Go("B"))          // dequeue B; A is a neighbour and seen
        val before = r.current.state

        val after = algorithm.apply(before, BfsAction.Go("A"))
        assertEquals(before.queue, after.next.queue)
        assertEquals(before.visited, after.next.visited)
        assertFalse(after.correct)
    }

    @Test
    fun `the queue empties exactly once every reachable node is processed`() {
        val end = traverse(teaching, "A")
        assertTrue(end.queue.isEmpty())
        assertEquals(end.reachable, end.dequeued.toSet())
        assertEquals(end.visited.toSet(), end.dequeued.toSet())
    }

    // -- 3. Cycles -------------------------------------------------------------

    @Test
    fun `a cycle terminates and processes each node once`() {
        val cycle = graph(
            "A" to listOf("B", "C"),
            "B" to listOf("A", "C"),
            "C" to listOf("A", "B"),
        )
        val visited = order(cycle, "A")
        assertEquals(listOf("A", "B", "C"), visited)
        assertEquals(visited.size, visited.distinct().size)
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
        assertEquals(listOf("A", "B", "E", "C", "D"), order(ring, "A"))
    }

    @Test
    fun `a self loop does not trap the traversal`() {
        val selfish = graph("A" to listOf("A", "B"), "B" to listOf("A"))
        assertEquals(listOf("A", "B"), order(selfish, "A"))
    }

    // -- 4. Edge cases ---------------------------------------------------------

    @Test
    fun `an empty graph terminates immediately`() {
        val r = AlgorithmRunner(
            algorithm,
            Dataset(values = emptyList(), graph = Graph(emptyList(), emptyMap())),
        )
        assertTrue(r.probe() is Probe.Terminal)
    }

    @Test
    fun `a single node is processed and the run ends`() {
        assertEquals(listOf("A"), order(graph("A" to emptyList()), "A"))
    }

    @Test
    fun `a node with no neighbours empties the queue straight away`() {
        val isolated = graph("A" to emptyList(), "B" to emptyList())
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
        assertEquals(listOf("C", "D"), order(split, "C"))
    }

    @Test
    fun `a start node not in the graph terminates rather than crashing`() {
        val r = AlgorithmRunner(
            algorithm,
            Dataset(values = emptyList(), graph = teaching, startNode = "Z"),
        )
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
        assertEquals(listOf("D", "B", "A", "E", "C"), order(teaching, "D"))
        assertEquals(listOf("C", "A", "B", "D", "E"), order(teaching, "C"))
    }

    @Test
    fun `every start node terminates and reaches its whole component`() {
        for (node in teaching.nodes) {
            val end = traverse(teaching, node.id)
            assertEquals(end.reachable, end.dequeued.toSet())
        }
    }

    // -- 5. The decision -------------------------------------------------------

    @Test
    fun `the decision is a tap on the graph, like DFS`() {
        val r = runner(teaching, "A")
        r.apply(BfsAction.Go("A"))
        val decision = (r.probe() as Probe.Decide).decision
        assertEquals(DecisionKind.CELL, decision.kind)
        assertTrue(decision.options.all { it.slot != null })
    }

    @Test
    fun `dequeuing out of order is refused, and explained`() {
        // Queue is [B, C]; tapping C must not process it.
        val r = runner(teaching, "A")
        r.apply(BfsAction.Go("A"))
        r.apply(BfsAction.Go("A"))
        r.apply(BfsAction.Go("B"))
        r.apply(BfsAction.Go("C"))
        val before = r.current.state
        assertEquals(listOf("B", "C"), before.queue)

        val decision = (r.probe() as Probe.Decide).decision
        assertEquals(BfsAction.Go("B"), decision.correct)
        assertTrue(
            DecisionValidation.validate(decision, BfsAction.Go("C"), 0) is Validation.Retry,
        )
        assertNotNull(decision.whyWrong[BfsAction.Go("C")])

        val after = algorithm.apply(before, BfsAction.Go("C"))
        assertEquals(before.queue, after.next.queue)
        assertEquals(before.dequeued, after.next.dequeued)
    }

    @Test
    fun `enqueueing happens in neighbour order`() {
        val r = runner(teaching, "A")
        r.apply(BfsAction.Go("A"))
        r.apply(BfsAction.Go("A"))          // dequeue A
        val decision = (r.probe() as Probe.Decide).decision
        // A's neighbours are [B, C], so B first.
        assertEquals(BfsAction.Go("B"), decision.correct)
        assertNotNull(decision.whyWrong[BfsAction.Go("C")])
    }

    @Test
    fun `a wrong tap never changes the queue or the traversal`() {
        val r = runner(teaching, "A")
        r.apply(BfsAction.Go("A"))
        val decision = (r.probe() as Probe.Decide).decision
        val before = r.current.state

        val wrong = decision.options.map { it.action }.filter { it != decision.correct }
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
        r.apply(BfsAction.Go("A"))
        r.apply(BfsAction.Go("A"))
        val decision = (r.probe() as Probe.Decide).decision
        val wrong = decision.options.map { it.action }.first { it != decision.correct }
        val rungs = (0..5).map {
            (DecisionValidation.validate(decision, wrong, it) as Validation.Retry).guidance
        }
        assertEquals(3, rungs.take(3).distinct().size)
        assertEquals(rungs[2], rungs[5])
    }

    @Test
    fun `the correct action is always among the options, from every start`() {
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

    // -- 6. The picture --------------------------------------------------------

    @Test
    fun `queued nodes are drawn differently from processed ones`() {
        val r = runner(teaching, "A")
        r.apply(BfsAction.Go("A"))
        r.apply(BfsAction.Go("A"))
        r.apply(BfsAction.Go("B"))
        val scene = BfsProjector().project(r.current.state, r.current.events)

        fun stateOf(label: String) = scene.nodes.first { it.label == label }.state
        assertEquals(CellState.COMPARING, stateOf("A"))   // being processed
        assertEquals(CellState.CANDIDATE, stateOf("B"))   // seen, waiting
        assertEquals(CellState.IDLE, stateOf("D"))        // not seen
    }

    @Test
    fun `the scene carries the queue, front first`() {
        val r = runner(teaching, "A")
        r.apply(BfsAction.Go("A"))
        r.apply(BfsAction.Go("A"))
        r.apply(BfsAction.Go("B"))
        r.apply(BfsAction.Go("C"))
        val scene = BfsProjector().project(r.current.state, r.current.events)
        assertEquals(listOf("B", "C"), scene.queue)
        assertEquals("Queue", scene.pathLabel)
        // BFS has no stack to show.
        assertTrue(scene.stack.isEmpty())
    }

    @Test
    fun `the traversal strip is the dequeue order, not the enqueue order`() {
        // The most common way a BFS visual lies.
        val r = runner(teaching, "A")
        r.apply(BfsAction.Go("A"))
        r.apply(BfsAction.Go("A"))
        r.apply(BfsAction.Go("B"))
        r.apply(BfsAction.Go("C"))
        val state = r.current.state
        val scene = BfsProjector().project(state, emptyList())

        assertEquals(listOf("A", "B", "C"), state.visited)
        assertEquals(listOf("A"), state.dequeued)
        assertEquals(listOf("A"), scene.traversal)
    }

    // -- 7. The walkthrough ----------------------------------------------------

    @Test
    fun `every watch step changes something visible - ADR-020`() {
        val steps = AlgorithmCatalog.graphBfs().watchScript().steps
        steps.zipWithNext { a, b ->
            val changed = a.scene != b.scene ||
                a.headline != b.headline ||
                a.support != b.support ||
                a.bullets != b.bullets
            assertTrue("steps ${a.index} and ${b.index} are identical", changed)
        }
    }

    @Test
    fun `the script ends INSIGHT then SUMMARY with the rules as bullets`() {
        val steps = AlgorithmCatalog.graphBfs().watchScript().steps
        assertEquals(WatchStepKind.SUMMARY, steps.last().kind)
        assertEquals(WatchStepKind.INSIGHT, steps[steps.lastIndex - 1].kind)
        assertEquals(4, steps.last().bullets.size)
    }

    @Test
    fun `the walkthrough narrates every dequeue`() {
        val steps = AlgorithmCatalog.graphBfs().watchScript().steps
        // Five nodes come off the queue; the last one is the FOUND beat.
        val dequeues = steps.count {
            it.kind == WatchStepKind.COMPARE || it.kind == WatchStepKind.FOUND
        }
        assertEquals(5, dequeues)
    }

    @Test
    fun `the walkthrough stays short enough to be a lesson`() {
        val size = AlgorithmCatalog.graphBfs().watchScript().size
        assertTrue("walkthrough is $size steps", size in 10..20)
    }

    // -- 8. Wiring -------------------------------------------------------------

    @Test
    fun `WATCH and TRY run the same graph through the same engine`() {
        val pack = AlgorithmCatalog.graphBfs()
        val watch = traverse(pack.watchDataset.graph!!, pack.watchDataset.startNode!!)
        val tryIt = traverse(pack.tryDataset.graph!!, pack.tryDataset.startNode!!)
        assertEquals(watch.dequeued, tryIt.dequeued)
        assertEquals(listOf("A", "B", "C", "D", "E"), tryIt.dequeued)
    }

    @Test
    fun `BFS and DFS share one graph, so the comparison is honest`() {
        assertEquals(GraphDatasets.teachingGraph, BfsDatasets.watch.graph)
        assertEquals(GraphDatasets.watch.startNode, BfsDatasets.watch.startNode)
    }

    @Test
    fun `the catalog resolves Graph BFS like any other lesson`() {
        val pack = AlgorithmCatalog.byId(AlgorithmId.GRAPH_BFS)
        assertEquals(AlgorithmId.GRAPH_BFS, pack.id)
        assertEquals("Graph BFS", pack.displayName)
    }
}
