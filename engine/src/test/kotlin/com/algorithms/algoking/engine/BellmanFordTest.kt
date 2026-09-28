package com.algorithms.algoking.engine

import com.algorithms.algoking.engine.algorithms.bellmanford.BellmanFordAlgorithm
import com.algorithms.algoking.engine.algorithms.bellmanford.BfAction
import com.algorithms.algoking.engine.algorithms.bellmanford.BfState
import com.algorithms.algoking.engine.catalog.AlgorithmCatalog
import com.algorithms.algoking.engine.core.AlgorithmRunner
import com.algorithms.algoking.engine.core.Dataset
import com.algorithms.algoking.engine.core.DirectedEdge
import com.algorithms.algoking.engine.core.Probe
import com.algorithms.algoking.engine.dataset.BellmanFordDatasets
import com.algorithms.algoking.engine.decision.Decision
import com.algorithms.algoking.engine.decision.DecisionKind
import com.algorithms.algoking.engine.decision.DecisionValidation
import com.algorithms.algoking.engine.decision.EdgeListReadout
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
 * Bellman–Ford.
 *
 * The distances are checked against [reference], which tries every simple route
 * from the start — no passes, no relaxation — so it cannot share the lesson's
 * mistakes. On a graph with no negative cycle, a shortest route is always simple.
 */
class BellmanFordTest {

    private val algorithm = BellmanFordAlgorithm()
    private val places = BellmanFordDatasets.places

    private fun dataset(edges: List<DirectedEdge>) =
        Dataset(values = emptyList(), graph = places, startNode = "A", directedEdges = edges)

    private fun trace(edges: List<DirectedEdge>): List<BfState> {
        val runner = AlgorithmRunner(algorithm, dataset(edges))
        val states = mutableListOf(runner.current.state)
        var guard = 0
        while (guard++ < 1_000) {
            when (val probe = runner.probe()) {
                is Probe.Mechanical -> runner.apply(probe.action)
                is Probe.Decide -> runner.apply(probe.decision.correct)
                is Probe.Terminal -> return states
            }
            states += runner.current.state
        }
        error("Bellman–Ford did not terminate")
    }

    private fun solve(edges: List<DirectedEdge>) = trace(edges).last()

    private fun decisions(edges: List<DirectedEdge>): List<Pair<BfState, Decision<BfAction>>> =
        trace(edges).mapNotNull { s -> (algorithm.probe(s) as? Probe.Decide)?.let { s to it.decision } }

    /** The cheapest simple route from A to every node, by trying them all. */
    private fun reference(edges: List<DirectedEdge>): Map<String, Int> {
        val best = mutableMapOf("A" to 0)
        fun walk(node: String, cost: Int, seen: Set<String>) {
            for (e in edges.filter { it.from == node && it.to !in seen }) {
                val c = cost + e.weight
                if (best[e.to] == null || c < best.getValue(e.to)) best[e.to] = c
                walk(e.to, c, seen + e.to)
            }
        }
        walk("A", 0, setOf("A"))
        return best
    }

    // ── The lesson's own datasets ────────────────────────────────────────────

    @Test
    fun `the watch run needs every pass, then checks`() {
        val end = solve(BellmanFordDatasets.watchEdges)
        assertEquals(mapOf("A" to 0, "B" to 4, "C" to 1, "D" to 3), end.dist)
        assertEquals(reference(BellmanFordDatasets.watchEdges), end.dist)
        assertEquals(listOf("A", "B", "C", "D"), end.pathTo("D"))
        // Three passes, then the check pass (pass 4), which found nothing.
        assertTrue(end.checking)
        assertEquals(4, end.pass)
        assertFalse(end.negativeCycle)
        assertEquals(Probe.Terminal(Outcome.Completed(true)), algorithm.probe(end))
    }

    @Test
    fun `the try run finds everything in pass 1, and pass 2 proves it`() {
        val end = solve(BellmanFordDatasets.tryEdges)
        assertEquals(mapOf("A" to 0, "B" to 3, "C" to 1, "D" to 5), end.dist)
        assertEquals(reference(BellmanFordDatasets.tryEdges), end.dist)
        assertFalse("stopped early, never needed the check pass", end.checking)
        assertEquals(2, end.pass)
    }

    @Test
    fun `nine comparisons in watch, seven in try, and no ties`() {
        for ((edges, count) in listOf(
            BellmanFordDatasets.watchEdges to 9,
            BellmanFordDatasets.tryEdges to 7,
        )) {
            val ds = decisions(edges)
            assertEquals(count, ds.size)
            for ((s, _) in ds) {
                val e = s.current!!
                assertTrue(s.candidate(e) != s.d(e.to))
            }
        }
    }

    @Test
    fun `a negative cycle is found by the check pass`() {
        // B → C → B costs 1 − 3 = −2 every time round.
        val cycle = listOf(
            DirectedEdge("A", "B", 1),
            DirectedEdge("B", "C", 1),
            DirectedEdge("C", "B", -3),
            DirectedEdge("C", "D", 2),
        )
        val end = solve(cycle)
        assertTrue(end.negativeCycle)
        assertEquals(Probe.Terminal(Outcome.NotFound), algorithm.probe(end))
    }

    // ── Decisions ────────────────────────────────────────────────────────────

    @Test
    fun `every decision is fully specified`() {
        for (edges in listOf(BellmanFordDatasets.watchEdges, BellmanFordDatasets.tryEdges)) {
            for ((s, d) in decisions(edges)) {
                val e = s.current!!
                assertEquals(DecisionKind.OPTIONS, d.kind)
                assertTrue(d.options.size in 2..3)
                assertEquals(BfAction.SetDistance(s.relaxed(e)), d.correct)
                assertTrue(d.correct in d.options.map { it.action })
                assertEquals(3, d.guidance.size)
                assertEquals(d.options.size - 1, d.whyWrong.size)
                assertFalse(d.correct in d.whyWrong.keys)
                assertFalse(d.autoInTry)
                assertEquals(1, d.edgeList!!.rows.count { it.status == EdgeListReadout.Status.NEXT })
            }
        }
    }

    @Test
    fun `a wrong number changes nothing`() {
        for ((s, d) in decisions(BellmanFordDatasets.watchEdges)) {
            for (option in d.options.map { it.action }) {
                if (option == d.correct) continue
                assertTrue(DecisionValidation.validate(d, option, 0) is Validation.Retry)
                assertEquals(s, algorithm.apply(s, option).next)
            }
        }
    }

    @Test
    fun `reading the road alone is named as its own mistake`() {
        // Pass 2, C → D: C is 5, road 2, so 7 — and 2 on its own is the slip.
        val (_, d) = decisions(BellmanFordDatasets.watchEdges)
            .first { (s, _) -> s.current!!.from == "C" && s.pass == 2 }
        assertEquals(BfAction.SetDistance(7), d.correct)
        assertEquals(NarrationId.BF_WHY_WEIGHT_ONLY, d.whyWrong.getValue(BfAction.SetDistance(2)).id)
        assertEquals(NarrationId.BF_WHY_MISSED, d.whyWrong.getValue(BfAction.SetDistance(null)).id)
    }

    @Test
    fun `a road from infinity, or giving the same distance, asks nothing`() {
        val first = trace(BellmanFordDatasets.watchEdges).first { it.passStarted }
        assertEquals("C", first.current!!.from)
        assertEquals(Probe.Mechanical(BfAction.Unreached), algorithm.probe(first))

        val same = trace(BellmanFordDatasets.watchEdges)
            .first { it.pass == 2 && it.current?.from == "A" && it.current?.to == "B" }
        assertEquals(Probe.Mechanical(BfAction.Same), algorithm.probe(same))
    }

    // ── WATCH ────────────────────────────────────────────────────────────────

    private fun watchSteps() = AlgorithmCatalog.bellmanFord().watchScript().steps

    @Test
    fun `the walkthrough is exactly the beats the lesson was designed as`() {
        assertEquals(
            listOf(
                WatchStepKind.SETUP to NarrationId.BF_WATCH_SETUP,
                WatchStepKind.EXAMINE to NarrationId.BF_WATCH_PASS, // pass 1
                WatchStepKind.ELIMINATE to NarrationId.BF_WATCH_UPDATE, // B = 4
                WatchStepKind.ELIMINATE to NarrationId.BF_WATCH_UPDATE, // C = 5
                WatchStepKind.EXAMINE to NarrationId.BF_WATCH_PASS, // pass 2
                WatchStepKind.ELIMINATE to NarrationId.BF_WATCH_UPDATE, // D = 7
                WatchStepKind.ELIMINATE to NarrationId.BF_WATCH_UPDATE, // C = 1, negative road
                WatchStepKind.KEEP to NarrationId.BF_WATCH_KEEP, // B → D
                WatchStepKind.KEEP to NarrationId.BF_WATCH_KEEP, // A → C
                WatchStepKind.EXAMINE to NarrationId.BF_WATCH_PASS, // pass 3
                WatchStepKind.ELIMINATE to NarrationId.BF_WATCH_UPDATE, // D = 3
                WatchStepKind.KEEP to NarrationId.BF_WATCH_KEEP, // B → D
                WatchStepKind.KEEP to NarrationId.BF_WATCH_KEEP, // A → C
                WatchStepKind.EXAMINE to NarrationId.BF_WATCH_CHECK,
                WatchStepKind.FOUND to NarrationId.BF_WATCH_DONE_CHECKED,
                WatchStepKind.INSIGHT to NarrationId.BF_WATCH_INSIGHT,
                WatchStepKind.SUMMARY to NarrationId.BF_WATCH_SUMMARY,
            ),
            watchSteps().map { it.kind to it.headline.id },
        )
    }

    @Test
    fun `the negative road's beat says so, and lights that road`() {
        val negative = watchSteps().filter { it.support?.id == NarrationId.BF_WATCH_UPDATE_NEGATIVE }
        assertEquals(1, negative.size)
        val scene = negative.single().scene as GraphScene
        assertTrue(scene.directed)
        val ids = places.ids
        val road = scene.edges.single { it.from == ids.indexOf("B") && it.to == ids.indexOf("C") }
        assertEquals(EdgeState.OPTION, road.state)
        assertEquals("−3", road.label)
        val node = scene.nodes.associate { it.label to it }
        assertEquals(CellState.COMPARING, node.getValue("B").state)
        assertEquals(CellState.CANDIDATE, node.getValue("C").state)
        assertEquals("1", node.getValue("C").secondaryLabel)
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
