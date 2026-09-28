package com.algorithms.algoking.engine

import com.algorithms.algoking.engine.algorithms.prim.PrimAction
import com.algorithms.algoking.engine.algorithms.prim.PrimAlgorithm
import com.algorithms.algoking.engine.algorithms.prim.PrimState
import com.algorithms.algoking.engine.catalog.AlgorithmCatalog
import com.algorithms.algoking.engine.core.AlgorithmRunner
import com.algorithms.algoking.engine.core.Dataset
import com.algorithms.algoking.engine.core.Graph
import com.algorithms.algoking.engine.core.GraphNode
import com.algorithms.algoking.engine.core.Probe
import com.algorithms.algoking.engine.dataset.PrimDatasets
import com.algorithms.algoking.engine.decision.CheapestReadout
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prim's algorithm.
 *
 * The lesson's answer is **generated**, so it is checked against [referenceMstCost],
 * a plain Kruskal with no lesson machinery in it — a different algorithm entirely,
 * so the two cannot share a mistake.
 */
class PrimTest {

    private val algorithm = PrimAlgorithm()
    private val teaching = PrimDatasets.teachingGraph
    private val tryGraph = PrimDatasets.tryGraph

    private fun runner(graph: Graph, start: String = "A") = AlgorithmRunner(
        algorithm,
        Dataset(values = emptyList(), graph = graph, startNode = start),
    )

    /** Every state the run passes through, always choosing correctly. */
    private fun trace(graph: Graph, start: String = "A"): List<PrimState> {
        val runner = runner(graph, start)
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
        error("Prim did not terminate")
    }

    private fun solve(graph: Graph, start: String = "A") = trace(graph, start).last()

    /** Kruskal, written independently: sort the edges, join with union-find. */
    private fun referenceMstCost(graph: Graph): Int {
        val parent = graph.ids.associateWith { it }.toMutableMap()
        fun find(x: String): String {
            var r = x
            while (parent.getValue(r) != r) r = parent.getValue(r)
            return r
        }
        var cost = 0
        for ((a, b) in graph.edges.sortedBy { (a, b) -> graph.weightOf(a, b) }) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) {
                parent[ra] = rb
                cost += graph.weightOf(a, b)!!
            }
        }
        return cost
    }

    // ── The lesson's own datasets ────────────────────────────────────────────

    @Test
    fun `the teaching run builds the tree the lesson was designed around`() {
        val end = solve(teaching)
        assertEquals(listOf("A", "C", "B", "D", "E", "F"), end.inTree)
        assertEquals(
            listOf("A – C", "C – B", "B – D", "B – E", "E – F"),
            end.treeEdges.map { it.label },
        )
        assertEquals(12, end.total)
        assertEquals(referenceMstCost(teaching), end.total)
        assertEquals(listOf("A – B", "C – D", "D – E", "D – F"), end.loops)
    }

    @Test
    fun `the try run builds a different tree, so Watch cannot be recited`() {
        val end = solve(tryGraph)
        assertEquals(listOf("A", "C", "B", "E", "F", "D"), end.inTree)
        assertEquals(15, end.total)
        assertEquals(referenceMstCost(tryGraph), end.total)
        assertTrue(end.inTree != solve(teaching).inTree)
    }

    @Test
    fun `n nodes are joined by exactly n - 1 edges and the run ends complete`() {
        for (graph in listOf(teaching, tryGraph)) {
            val runner = runner(graph)
            trace(graph).last().let { end ->
                assertEquals(graph.nodes.size - 1, end.treeEdges.size)
                assertTrue(end.spanning)
            }
            var guard = 0
            while (guard++ < 100) {
                val probe = runner.probe()
                if (probe is Probe.Terminal) {
                    assertEquals(Outcome.Completed(true), probe.outcome)
                    break
                }
                runner.apply((probe as Probe.Decide).decision.correct)
            }
        }
    }

    @Test
    fun `every choice has exactly one cheapest edge - no ties`() {
        for (graph in listOf(teaching, tryGraph)) {
            for (state in trace(graph).filter { !it.finished }) {
                val weights = state.crossing.map { it.weight }
                assertEquals(
                    "tie at tree ${state.inTree}",
                    1,
                    weights.count { it == weights.min() },
                )
            }
        }
    }

    @Test
    fun `the start is the only node in the tree before anything is chosen`() {
        val first = runner(teaching).current.state
        assertEquals(listOf("A"), first.inTree)
        assertEquals(0, first.total)
        assertEquals(listOf("A – C", "A – B"), first.crossing.map { it.label })
    }

    // ── The two things the lesson exists to teach ────────────────────────────

    @Test
    fun `the teaching graph once takes an edge from an older node, not the newest`() {
        // The fourth choice: D just joined and offers 5 and 6, but B–E at 4 wins.
        val states = trace(teaching)
        val fromOlder = states.zipWithNext().count { (before, after) ->
            val newest = before.lastAdded
            val taken = after.treeEdges.lastOrNull()
            newest != null && taken != null &&
                after.treeEdges.size > before.treeEdges.size && taken.from != newest
        }
        assertEquals(1, fromOlder)
    }

    @Test
    fun `an edge between two tree nodes is never offered`() {
        for (graph in listOf(teaching, tryGraph)) {
            for (state in trace(graph)) {
                for (edge in state.crossing) {
                    assertTrue(edge.from in state.inTree)
                    assertFalse(edge.to in state.inTree)
                }
            }
        }
    }

    // ── Decisions ────────────────────────────────────────────────────────────

    @Test
    fun `every decision is fully specified`() {
        for (graph in listOf(teaching, tryGraph)) {
            val runner = runner(graph)
            var decisions = 0
            var guard = 0
            while (guard++ < 100) {
                when (val probe = runner.probe()) {
                    is Probe.Mechanical -> runner.apply(probe.action)
                    is Probe.Terminal -> break
                    is Probe.Decide -> {
                        val d = probe.decision
                        decisions++
                        assertEquals(DecisionKind.CELL, d.kind)
                        assertEquals(3, d.guidance.size)
                        assertEquals(d.options.size - 1, d.whyWrong.size)
                        assertFalse(d.correct in d.whyWrong.keys)
                        assertFalse("Try must never answer this", d.autoInTry)
                        assertNotNull(d.cheapest)
                        runner.apply(d.correct)
                    }
                }
            }
            assertEquals(graph.nodes.size - 1, decisions)
        }
    }

    @Test
    fun `adding anything but the cheap edge's node changes nothing`() {
        val runner = runner(teaching)
        val decision = (runner.probe() as Probe.Decide).decision
        val before = runner.current.state
        for (option in decision.options.map { it.action }) {
            if (option == decision.correct) continue
            assertTrue(DecisionValidation.validate(decision, option, 0) is Validation.Retry)
            assertEquals(before, algorithm.apply(before, option).next)
        }
    }

    @Test
    fun `each wrong tap names its own mistake`() {
        // Fourth choice on the teaching graph: tree {A, C, B, D}, D newest.
        val state = trace(teaching).first { it.inTree.size == 4 && !it.finished }
        val runner = runner(teaching)
        repeat(3) { runner.apply((runner.probe() as Probe.Decide).decision.correct) }
        val decision = (runner.probe() as Probe.Decide).decision
        assertEquals(state, runner.current.state)
        assertEquals(PrimAction.Add("E"), decision.correct)

        val why = decision.whyWrong.mapKeys { (it.key as PrimAction.Add).node }
            .mapValues { it.value.id }
        assertEquals(NarrationId.PRIM_WHY_IN_TREE, why["A"])
        assertEquals(NarrationId.PRIM_WHY_IN_TREE, why["D"])
        // F is reached only from D, the newest node — and B–E is cheaper.
        assertEquals(NarrationId.PRIM_WHY_NEWEST_ONLY, why["F"])

        // At the start, B is reachable but not cheapest, and D is out of reach.
        val first = (runner(teaching).probe() as Probe.Decide).decision
        val atStart = first.whyWrong.mapKeys { (it.key as PrimAction.Add).node }
            .mapValues { it.value.id }
        assertEquals(NarrationId.PRIM_WHY_NOT_CHEAPEST, atStart["B"])
        assertEquals(NarrationId.PRIM_WHY_UNREACHED, atStart["D"])
    }

    // ── The readout ──────────────────────────────────────────────────────────

    @Test
    fun `every choice shows every edge leaving the tree, unsorted, and the cheapest`() {
        for (graph in listOf(teaching, tryGraph)) {
            for (state in trace(graph).filter { !it.finished }) {
                val readout = state.cheapestReadout()!!
                assertEquals(CheapestReadout.Mode.EDGES, readout.mode)
                assertEquals(
                    state.crossing.map { it.from to it.to },
                    readout.rows.map { it.via to it.node },
                )
                assertEquals(state.crossing.map { it.weight }, readout.rows.map { it.distance })
                assertEquals(state.cheapest!!.to, readout.chosen)
                assertEquals(state.cheapest!!.from, readout.chosenVia)
                assertEquals(readout.rows.minOf { it.distance }, readout.best)
                assertEquals(1, readout.rows.count(readout::isWinner))
                assertEquals(state.loops, readout.skippedLoops)
            }
        }
        // The first choice, spelled out.
        val first = runner(teaching).current.state.cheapestReadout()!!
        assertEquals(listOf("A – C" to 2, "A – B" to 5), first.rows.map { "${it.via} – ${it.node}" to it.distance })
        assertEquals("C", first.chosen)
    }

    // ── A disconnected graph ─────────────────────────────────────────────────

    @Test
    fun `a graph that cannot be spanned ends without pretending it was`() {
        val split = Graph(
            nodes = listOf(
                GraphNode("A", "A", 0f, 0f),
                GraphNode("B", "B", 1f, 0f),
                GraphNode("C", "C", 0f, 1f),
            ),
            adjacency = mapOf("A" to listOf("B"), "B" to listOf("A"), "C" to emptyList()),
            weights = Graph.weightsOf(Triple("A", "B", 1)),
        )
        val runner = runner(split)
        runner.apply((runner.probe() as Probe.Decide).decision.correct)
        assertEquals(Probe.Terminal(Outcome.NotFound), runner.probe())
    }

    // ── WATCH ────────────────────────────────────────────────────────────────

    private fun watchSteps() = AlgorithmCatalog.prim().watchScript().steps

    @Test
    fun `the walkthrough is exactly the beats the lesson was designed as`() {
        assertEquals(
            listOf(
                WatchStepKind.SETUP to NarrationId.PRIM_WATCH_SETUP,
                WatchStepKind.COMPARE to NarrationId.PRIM_WATCH_ADD, // C
                WatchStepKind.COMPARE to NarrationId.PRIM_WATCH_ADD, // B
                WatchStepKind.ELIMINATE to NarrationId.PRIM_WATCH_LOOP, // A–B
                WatchStepKind.COMPARE to NarrationId.PRIM_WATCH_ADD, // D
                WatchStepKind.ELIMINATE to NarrationId.PRIM_WATCH_LOOP, // C–D
                WatchStepKind.COMPARE to NarrationId.PRIM_WATCH_ADD, // E, from older B
                WatchStepKind.ELIMINATE to NarrationId.PRIM_WATCH_LOOP, // D–E
                WatchStepKind.COMPARE to NarrationId.PRIM_WATCH_ADD, // F
                WatchStepKind.FOUND to NarrationId.PRIM_WATCH_DONE,
                WatchStepKind.INSIGHT to NarrationId.PRIM_WATCH_INSIGHT,
                WatchStepKind.SUMMARY to NarrationId.PRIM_WATCH_SUMMARY,
            ),
            watchSteps().map { it.kind to it.headline.id },
        )
    }

    @Test
    fun `every join in Watch carries its comparison, and the older-node one says so`() {
        val joins = watchSteps().filter { it.kind == WatchStepKind.COMPARE }
        assertTrue(joins.all { it.cheapest != null })
        assertEquals(
            listOf("C", "B", "D", "E", "F"),
            joins.map { it.cheapest!!.chosen },
        )
        assertEquals(
            listOf(NarrationId.PRIM_WATCH_ADD_OLDER),
            joins.map { it.support!!.id }.filter { it == NarrationId.PRIM_WATCH_ADD_OLDER },
        )
        assertEquals(NarrationId.PRIM_WATCH_ADD_OLDER, joins[3].support!!.id)
    }

    @Test
    fun `every watch step changes something visible - ADR-020`() {
        watchSteps().zipWithNext { a, b ->
            val changed = a.scene != b.scene ||
                a.headline != b.headline ||
                a.support != b.support ||
                a.cheapest != b.cheapest ||
                a.bullets != b.bullets
            assertTrue("steps ${a.index} and ${b.index} are identical", changed)
        }
    }

    @Test
    fun `the picture marks tree edges, candidate edges and loops`() {
        val runner = runner(teaching)
        runner.apply((runner.probe() as Probe.Decide).decision.correct) // C
        runner.apply((runner.probe() as Probe.Decide).decision.correct) // B
        val scene = AlgorithmCatalog.prim().projector.project(runner.current.state, emptyList())
            as GraphScene
        val ids = teaching.ids
        fun edge(a: String, b: String) = scene.edges.first {
            setOf(it.from, it.to) == setOf(ids.indexOf(a), ids.indexOf(b))
        }.state

        assertEquals(EdgeState.PATH, edge("A", "C"))
        assertEquals(EdgeState.PATH, edge("C", "B"))
        assertEquals(EdgeState.ELIMINATED, edge("A", "B"))
        assertEquals(EdgeState.ACTIVE, edge("B", "D"))
        assertEquals(EdgeState.ACTIVE, edge("C", "D"))
        assertEquals(EdgeState.IDLE, edge("E", "F"))

        val state = scene.nodes.associate { it.label to it.state }
        assertEquals(CellState.COMPARING, state["B"])
        assertEquals(CellState.FINALIZED, state["A"])
        assertEquals(CellState.CANDIDATE, state["D"])
        assertEquals(CellState.IDLE, state["F"])
        assertEquals(3, scene.badge!!.value)
    }
}
