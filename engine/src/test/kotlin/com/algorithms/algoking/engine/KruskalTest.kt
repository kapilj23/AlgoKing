package com.algorithms.algoking.engine

import com.algorithms.algoking.engine.algorithms.kruskal.KruskalAction
import com.algorithms.algoking.engine.algorithms.kruskal.KruskalAlgorithm
import com.algorithms.algoking.engine.algorithms.kruskal.KruskalState
import com.algorithms.algoking.engine.algorithms.prim.PrimAlgorithm
import com.algorithms.algoking.engine.catalog.AlgorithmCatalog
import com.algorithms.algoking.engine.core.AlgorithmRunner
import com.algorithms.algoking.engine.core.Dataset
import com.algorithms.algoking.engine.core.Graph
import com.algorithms.algoking.engine.core.GraphNode
import com.algorithms.algoking.engine.core.Probe
import com.algorithms.algoking.engine.dataset.KruskalDatasets
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kruskal's algorithm.
 *
 * The answer is checked against Prim's engine on the same graph — a different
 * algorithm, growing from a start rather than sorting edges — so the two cannot
 * share a mistake, and any minimum spanning tree has the same total.
 */
class KruskalTest {

    private val algorithm = KruskalAlgorithm()
    private val teaching = KruskalDatasets.teachingGraph
    private val tryGraph = KruskalDatasets.tryGraph

    private fun runner(graph: Graph) =
        AlgorithmRunner(algorithm, Dataset(values = emptyList(), graph = graph))

    private fun trace(graph: Graph): List<KruskalState> {
        val runner = runner(graph)
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
        error("Kruskal did not terminate")
    }

    private fun solve(graph: Graph) = trace(graph).last()

    /** Prim's total on the same graph, driven by its own engine. */
    private fun primTotal(graph: Graph): Int {
        val prim = AlgorithmRunner(
            PrimAlgorithm(),
            Dataset(values = emptyList(), graph = graph, startNode = graph.ids.first()),
        )
        var guard = 0
        while (guard++ < 500) {
            when (val probe = prim.probe()) {
                is Probe.Decide -> prim.apply(probe.decision.correct)
                is Probe.Mechanical -> prim.apply(probe.action)
                is Probe.Terminal -> return prim.current.state.total
            }
        }
        error("Prim did not terminate")
    }

    // ── The lesson's own datasets ────────────────────────────────────────────

    @Test
    fun `the teaching run takes, skips and stops exactly as designed`() {
        val end = solve(teaching)
        assertEquals(listOf("E – F", "A – B", "A – C", "D – E", "C – E"), end.taken.map { it.label })
        assertEquals(listOf("B – C", "D – F"), end.skipped.map { it.label })
        assertEquals(18, end.total)
        // B–D is never looked at: the tree was finished first.
        assertEquals(7, end.cursor)
        assertEquals("B – D", end.sorted.last().label)
        assertEquals(primTotal(teaching), end.total)
    }

    @Test
    fun `the try run has its own answer`() {
        val end = solve(tryGraph)
        assertEquals(listOf("B – C", "D – F", "A – B", "D – E", "C – E"), end.taken.map { it.label })
        assertEquals(listOf("A – C", "E – F"), end.skipped.map { it.label })
        assertEquals(17, end.total)
        assertEquals(primTotal(tryGraph), end.total)
    }

    @Test
    fun `every weight is distinct, so the sorted order is never a matter of taste`() {
        for (graph in listOf(teaching, tryGraph)) {
            val sorted = runner(graph).current.state.sorted
            assertEquals(graph.edges.size, sorted.size)
            assertEquals(sorted.size, sorted.map { it.weight }.toSet().size)
            assertEquals(sorted.sortedBy { it.weight }, sorted)
        }
    }

    @Test
    fun `n nodes end in one group, joined by n - 1 edges`() {
        for (graph in listOf(teaching, tryGraph)) {
            val end = solve(graph)
            assertEquals(graph.nodes.size - 1, end.taken.size)
            assertEquals(1, end.groups.size)
            assertEquals(Probe.Terminal(Outcome.Completed(true)), algorithm.probe(end))
        }
    }

    @Test
    fun `a taken edge always joins two groups, and a skipped one never does`() {
        for (graph in listOf(teaching, tryGraph)) {
            trace(graph).zipWithNext { before, after ->
                val edge = before.sorted[before.cursor]
                if (after.taken.size > before.taken.size) {
                    assertFalse(before.makesLoop(edge))
                    assertEquals(before.groups.size - 1, after.groups.size)
                } else {
                    assertTrue(before.makesLoop(edge))
                    assertEquals(before.groups, after.groups)
                }
            }
        }
    }

    @Test
    fun `groups start as one node each and merge as edges are taken`() {
        val states = trace(teaching)
        assertEquals(teaching.ids.map { listOf(it) }, states.first().groups)
        // After E–F, A–B and A–C: {A, B, C}  {D}  {E, F}.
        assertEquals(listOf(listOf("A", "B", "C"), listOf("D"), listOf("E", "F")), states[3].groups)
    }

    // ── Decisions ────────────────────────────────────────────────────────────

    @Test
    fun `every decision is fully specified and shows its working`() {
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
                        assertEquals(DecisionKind.OPTIONS, d.kind)
                        assertEquals(
                            listOf(KruskalAction.Take, KruskalAction.Skip),
                            d.options.map { it.action },
                        )
                        assertEquals(3, d.guidance.size)
                        assertEquals(1, d.whyWrong.size)
                        assertFalse(d.correct in d.whyWrong.keys)
                        assertFalse("Try must never answer this", d.autoInTry)
                        val list = d.edgeList
                        assertNotNull(list)
                        assertEquals(1, list!!.rows.count { it.status == EdgeListReadout.Status.NEXT })
                        assertEquals(graph.ids.toSet(), list.groups.flatten().toSet())
                        assertEquals(graph.ids.size, list.groups.flatten().size)
                        runner.apply(d.correct)
                    }
                }
            }
            // Five takes and two skips, on both graphs.
            assertEquals(7, decisions)
        }
    }

    @Test
    fun `the wrong button changes nothing`() {
        for (graph in listOf(teaching, tryGraph)) {
            for (state in trace(graph).filter { !it.finished }) {
                val decision = (algorithm.probe(state) as Probe.Decide).decision
                val wrong = if (decision.correct == KruskalAction.Take) KruskalAction.Skip else KruskalAction.Take
                assertTrue(DecisionValidation.validate(decision, wrong, 0) is Validation.Retry)
                assertEquals(state, algorithm.apply(state, wrong).next)
            }
        }
    }

    @Test
    fun `each wrong button names its own mistake`() {
        val states = trace(teaching)
        // B–C (4): both ends in {A, B, C}.
        val loop = (algorithm.probe(states[3]) as Probe.Decide).decision
        assertEquals(KruskalAction.Skip, loop.correct)
        assertEquals(NarrationId.KR_WHY_LOOP, loop.whyWrong.getValue(KruskalAction.Take).id)
        assertTrue("{A, B, C}" in loop.whyWrong.getValue(KruskalAction.Take).args)

        // E–F (1): different groups.
        val first = (algorithm.probe(states[0]) as Probe.Decide).decision
        assertEquals(KruskalAction.Take, first.correct)
        assertEquals(NarrationId.KR_WHY_NEEDED, first.whyWrong.getValue(KruskalAction.Skip).id)
    }

    @Test
    fun `a merged group is written in graph order`() {
        // Found on a phone: D – E joining {D, F} and {E} read "{D, F, E}".
        val states = trace(tryGraph)
        val deState = states.first { it.next?.label == "D – E" }
        val decision = (algorithm.probe(deState) as Probe.Decide).decision
        assertEquals("{D, E, F}", decision.correctFeedback.args[3])
    }

    @Test
    fun `the edge list marks what happened to every edge`() {
        val end = solve(teaching).edgeListReadout()
        assertEquals(
            listOf("E – F", "A – B", "A – C", "B – C", "D – E", "D – F", "C – E", "B – D"),
            end.rows.map { it.label },
        )
        assertEquals(
            listOf(
                EdgeListReadout.Status.TAKEN,
                EdgeListReadout.Status.TAKEN,
                EdgeListReadout.Status.TAKEN,
                EdgeListReadout.Status.SKIPPED,
                EdgeListReadout.Status.TAKEN,
                EdgeListReadout.Status.SKIPPED,
                EdgeListReadout.Status.TAKEN,
                // Never looked at — and not marked NEXT, because the walk is over.
                EdgeListReadout.Status.WAITING,
            ),
            end.rows.map { it.status },
        )
    }

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

    private fun watchSteps() = AlgorithmCatalog.kruskal().watchScript().steps

    @Test
    fun `the walkthrough is exactly the beats the lesson was designed as`() {
        assertEquals(
            listOf(
                WatchStepKind.SETUP to NarrationId.KR_WATCH_SETUP,
                WatchStepKind.ADD to NarrationId.KR_WATCH_TAKE, // E–F
                WatchStepKind.ADD to NarrationId.KR_WATCH_TAKE, // A–B
                WatchStepKind.ADD to NarrationId.KR_WATCH_TAKE, // A–C
                WatchStepKind.ELIMINATE to NarrationId.KR_WATCH_SKIP, // B–C
                WatchStepKind.ADD to NarrationId.KR_WATCH_TAKE, // D–E
                WatchStepKind.ELIMINATE to NarrationId.KR_WATCH_SKIP, // D–F
                WatchStepKind.ADD to NarrationId.KR_WATCH_TAKE, // C–E
                WatchStepKind.FOUND to NarrationId.KR_WATCH_DONE,
                WatchStepKind.INSIGHT to NarrationId.KR_WATCH_INSIGHT,
                WatchStepKind.SUMMARY to NarrationId.KR_WATCH_SUMMARY,
            ),
            watchSteps().map { it.kind to it.headline.id },
        )
        // The finish names the edge that was never looked at.
        val done = watchSteps().first { it.kind == WatchStepKind.FOUND }
        assertEquals(NarrationId.KR_WATCH_DONE_WHY, done.support!!.id)
        assertEquals("B – D (8)", done.support!!.args[0])
    }

    @Test
    fun `every decision beat in Watch carries the edge list`() {
        val beats = watchSteps().filter {
            it.kind == WatchStepKind.ADD || it.kind == WatchStepKind.ELIMINATE ||
                it.kind == WatchStepKind.SETUP
        }
        assertTrue(beats.all { it.edgeList != null })
    }

    @Test
    fun `a Watch beat lights the edge it talks about, not the next one`() {
        // Found on a phone: "Skip B – C" was drawn with D – E lit and marked next.
        val skip = watchSteps().first { it.kind == WatchStepKind.ELIMINATE }
        assertEquals("B – C", skip.headline.args[0])
        val scene = skip.scene as GraphScene
        val ids = teaching.ids
        fun edge(a: String, b: String) = scene.edges.first {
            setOf(it.from, it.to) == setOf(ids.indexOf(a), ids.indexOf(b))
        }.state
        assertEquals(EdgeState.OPTION, edge("B", "C"))
        assertEquals(EdgeState.IDLE, edge("D", "E"))
        val node = scene.nodes.associate { it.label to it.state }
        assertEquals(CellState.CANDIDATE, node["B"])
        assertEquals(CellState.CANDIDATE, node["C"])
        assertFalse(node["D"] == CellState.CANDIDATE)
        // And the list marks nothing as next.
        assertTrue(skip.edgeList!!.rows.none { it.status == EdgeListReadout.Status.NEXT })
    }

    @Test
    fun `every watch step changes something visible - ADR-020`() {
        watchSteps().zipWithNext { a, b ->
            val changed = a.scene != b.scene ||
                a.headline != b.headline ||
                a.support != b.support ||
                a.edgeList != b.edgeList ||
                a.bullets != b.bullets
            assertTrue("steps ${a.index} and ${b.index} are identical", changed)
        }
    }

    @Test
    fun `the picture marks taken, next and skipped edges`() {
        // After E–F, A–B, A–C and the B–C skip: D–E is next.
        val state = trace(teaching)[4]
        val scene = AlgorithmCatalog.kruskal().projector.project(state, emptyList()) as GraphScene
        val ids = teaching.ids
        fun edge(a: String, b: String) = scene.edges.first {
            setOf(it.from, it.to) == setOf(ids.indexOf(a), ids.indexOf(b))
        }.state

        assertEquals(EdgeState.TREE, edge("E", "F"))
        assertEquals(EdgeState.TREE, edge("A", "B"))
        assertEquals(EdgeState.ELIMINATED, edge("B", "C"))
        assertEquals(EdgeState.OPTION, edge("D", "E"))
        assertEquals(EdgeState.IDLE, edge("B", "D"))

        val node = scene.nodes.associate { it.label to it.state }
        assertEquals(CellState.CANDIDATE, node["D"])
        assertEquals(CellState.CANDIDATE, node["E"])
        assertEquals(CellState.FINALIZED, node["A"])
        assertEquals(6, scene.badge!!.value)
    }
}
