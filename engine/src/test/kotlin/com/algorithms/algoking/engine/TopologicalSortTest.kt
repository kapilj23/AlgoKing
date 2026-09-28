package com.algorithms.algoking.engine

import com.algorithms.algoking.engine.algorithms.topo.TopoAction
import com.algorithms.algoking.engine.algorithms.topo.TopoState
import com.algorithms.algoking.engine.algorithms.topo.TopologicalSortAlgorithm
import com.algorithms.algoking.engine.catalog.AlgorithmCatalog
import com.algorithms.algoking.engine.core.AlgorithmRunner
import com.algorithms.algoking.engine.core.Dataset
import com.algorithms.algoking.engine.core.DirectedEdge
import com.algorithms.algoking.engine.core.Graph
import com.algorithms.algoking.engine.core.GraphNode
import com.algorithms.algoking.engine.core.Probe
import com.algorithms.algoking.engine.dataset.TopologicalSortDatasets
import com.algorithms.algoking.engine.decision.Decision
import com.algorithms.algoking.engine.decision.DecisionKind
import com.algorithms.algoking.engine.decision.DecisionValidation
import com.algorithms.algoking.engine.decision.Validation
import com.algorithms.algoking.engine.event.Outcome
import com.algorithms.algoking.engine.narration.NarrationId
import com.algorithms.algoking.engine.scene.CellState
import com.algorithms.algoking.engine.scene.EdgeState
import com.algorithms.algoking.engine.scene.GraphScene
import com.algorithms.algoking.engine.walkthrough.WatchStepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Topological sort.
 *
 * The order is checked against [reference]: every permutation of the nodes, kept
 * only if every arrow points forward, and the alphabetically first of those — which
 * is exactly what "free node, earliest letter" must produce, found without it.
 */
class TopologicalSortTest {

    private val algorithm = TopologicalSortAlgorithm()

    private fun runner(graph: Graph, edges: List<DirectedEdge>) = AlgorithmRunner(
        algorithm,
        Dataset(values = emptyList(), graph = graph, directedEdges = edges),
    )

    private fun trace(graph: Graph, edges: List<DirectedEdge>): List<TopoState> {
        val runner = runner(graph, edges)
        val states = mutableListOf(runner.current.state)
        var guard = 0
        while (guard++ < 200) {
            when (val probe = runner.probe()) {
                is Probe.Mechanical -> runner.apply(probe.action)
                is Probe.Decide -> runner.apply(probe.decision.correct)
                is Probe.Terminal -> return states
            }
            states += runner.current.state
        }
        error("did not terminate")
    }

    private fun decisions(graph: Graph, edges: List<DirectedEdge>): List<Pair<TopoState, Decision<TopoAction>>> =
        trace(graph, edges).mapNotNull { s -> (algorithm.probe(s) as? Probe.Decide)?.let { s to it.decision } }

    private fun permutations(items: List<String>): List<List<String>> =
        if (items.size <= 1) {
            listOf(items)
        } else {
            items.flatMap { first -> permutations(items - first).map { listOf(first) + it } }
        }

    private fun reference(graph: Graph, edges: List<DirectedEdge>): List<String> =
        permutations(graph.ids)
            .filter { order -> edges.all { order.indexOf(it.from) < order.indexOf(it.to) } }
            .minBy { it.joinToString("") }

    private val watch = TopologicalSortDatasets.watchGraph to TopologicalSortDatasets.watchEdges
    private val tryIt = TopologicalSortDatasets.tryGraph to TopologicalSortDatasets.tryEdges

    // ── The lesson's own datasets ────────────────────────────────────────────

    @Test
    fun `the watch order is the one the lesson was designed around`() {
        val (g, e) = watch
        val end = trace(g, e).last()
        assertEquals(listOf("C", "E", "A", "B", "D", "F"), end.placed)
        assertEquals(reference(g, e), end.placed)
        assertEquals(Probe.Terminal(Outcome.Completed(true)), algorithm.probe(end))
    }

    @Test
    fun `the try order is different, and just as checked`() {
        val (g, e) = tryIt
        val end = trace(g, e).last()
        assertEquals(listOf("D", "B", "F", "A", "C", "E"), end.placed)
        assertEquals(reference(g, e), end.placed)
    }

    @Test
    fun `every arrow points forward in the finished order`() {
        for ((g, e) in listOf(watch, tryIt)) {
            val order = trace(g, e).last().placed
            for (edge in e) assertTrue(order.indexOf(edge.from) < order.indexOf(edge.to))
        }
    }

    @Test
    fun `the answer is never just the alphabet`() {
        for ((g, e) in listOf(watch, tryIt)) {
            val order = trace(g, e).last().placed
            assertFalse(order == order.sorted())
        }
    }

    @Test
    fun `watch has three ties and two placements that free nothing`() {
        val (g, e) = watch
        val ds = decisions(g, e)
        assertEquals(6, ds.size)
        assertEquals(3, ds.count { (s, _) -> s.ready.size > 1 })
        // C and A have arrows out and still free nothing; D and F have none at all.
        val freesNothing = ds.filter { (s, _) -> s.wouldFree(s.next!!).isEmpty() }.map { (s, _) -> s.next }
        assertEquals(listOf("C", "A", "D", "F"), freesNothing)
        assertTrue(e.any { it.from == "C" } && e.any { it.from == "A" })
    }

    // ── Decisions ────────────────────────────────────────────────────────────

    @Test
    fun `every decision is fully specified`() {
        for ((g, e) in listOf(watch, tryIt)) {
            for ((s, d) in decisions(g, e)) {
                assertEquals(DecisionKind.CELL, d.kind)
                assertEquals(TopoAction.Place(s.next!!), d.correct)
                assertEquals(3, d.guidance.size)
                assertEquals(d.options.size - 1, d.whyWrong.size)
                assertFalse(d.correct in d.whyWrong.keys)
                assertFalse(d.autoInTry)
                assertTrue(d.edgeList != null)
            }
        }
    }

    @Test
    fun `a wrong tap changes nothing`() {
        val (g, e) = watch
        for ((s, d) in decisions(g, e)) {
            for (option in d.options.map { it.action }) {
                if (option == d.correct) continue
                assertTrue(DecisionValidation.validate(d, option, 0) is Validation.Retry)
                assertEquals(s, algorithm.apply(s, option).next)
            }
        }
    }

    @Test
    fun `each wrong tap names its own mistake`() {
        val (g, e) = watch
        val (_, first) = decisions(g, e).first()
        fun why(node: String) = first.whyWrong.getValue(TopoAction.Place(node))
        // A waits on C and E.
        assertEquals(NarrationId.TS_WHY_WAITING, why("A").id)
        assertEquals(listOf("A", 2, "C and E"), why("A").args)
        // E is free too — valid, but not the earliest letter.
        assertEquals(NarrationId.TS_WHY_NOT_FIRST, why("E").id)

        val (_, later) = decisions(g, e)[1]
        assertEquals(NarrationId.TS_WHY_PLACED, later.whyWrong.getValue(TopoAction.Place("C")).id)
    }

    @Test
    fun `a cycle leaves nothing free, and the run says so`() {
        val nodes = listOf("A", "B", "C", "D").mapIndexed { i, id -> GraphNode(id, id, i / 4f, 0.5f) }
        val g = Graph(nodes, emptyMap())
        val e = listOf(
            DirectedEdge("A", "B", 1),
            DirectedEdge("B", "C", 1),
            DirectedEdge("C", "A", 1),
            DirectedEdge("D", "A", 1),
        )
        val end = trace(g, e).last()
        assertEquals(listOf("D"), end.placed)
        assertTrue(end.stuck)
        assertEquals(Probe.Terminal(Outcome.NotFound), algorithm.probe(end))
    }

    // ── WATCH ────────────────────────────────────────────────────────────────

    private fun watchSteps() = AlgorithmCatalog.topologicalSort().watchScript().steps

    @Test
    fun `the walkthrough is exactly the beats the lesson was designed as`() {
        val steps = watchSteps()
        assertEquals(
            listOf(WatchStepKind.SETUP) + List(6) { WatchStepKind.COMPARE } +
                listOf(WatchStepKind.FOUND, WatchStepKind.INSIGHT, WatchStepKind.SUMMARY),
            steps.map { it.kind },
        )
        assertEquals(
            listOf("C", "E", "A", "B", "D", "F"),
            steps.filter { it.kind == WatchStepKind.COMPARE }.map { it.headline.args[0] },
        )
        assertEquals(3, steps.count { it.support?.id == NarrationId.TS_WATCH_PLACE_TIE })
    }

    @Test
    fun `the picture counts arrows in, and marks the arrows just removed`() {
        val (g, e) = watch
        val afterC = trace(g, e)[1]
        val scene = AlgorithmCatalog.topologicalSort().projector.project(afterC, emptyList()) as GraphScene
        assertTrue(scene.directed)
        val node = scene.nodes.associate { it.label to it }
        assertEquals(CellState.COMPARING, node.getValue("C").state)
        assertEquals("#1", node.getValue("C").secondaryLabel)
        assertEquals("1", node.getValue("A").secondaryLabel) // only E still points in
        assertEquals("0", node.getValue("E").secondaryLabel)
        val ids = g.ids
        val ca = scene.edges.single { it.from == ids.indexOf("C") && it.to == ids.indexOf("A") }
        assertEquals(EdgeState.OPTION, ca.state)
    }

    @Test
    fun `every watch step changes something visible - ADR-020`() {
        watchSteps().zipWithNext { a, b ->
            val changed = a.scene != b.scene || a.headline != b.headline ||
                a.support != b.support || a.edgeList != b.edgeList || a.bullets != b.bullets
            assertTrue("steps ${a.index} and ${b.index} are identical", changed)
        }
    }
}
