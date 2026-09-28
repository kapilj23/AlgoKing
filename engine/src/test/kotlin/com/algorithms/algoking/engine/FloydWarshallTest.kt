package com.algorithms.algoking.engine

import com.algorithms.algoking.engine.algorithms.floydwarshall.FloydWarshallAlgorithm
import com.algorithms.algoking.engine.algorithms.floydwarshall.FwAction
import com.algorithms.algoking.engine.algorithms.floydwarshall.FwState
import com.algorithms.algoking.engine.catalog.AlgorithmCatalog
import com.algorithms.algoking.engine.core.AlgorithmRunner
import com.algorithms.algoking.engine.core.Dataset
import com.algorithms.algoking.engine.core.Graph
import com.algorithms.algoking.engine.core.Probe
import com.algorithms.algoking.engine.dataset.FloydWarshallDatasets
import com.algorithms.algoking.engine.decision.Decision
import com.algorithms.algoking.engine.decision.DecisionKind
import com.algorithms.algoking.engine.decision.DecisionValidation
import com.algorithms.algoking.engine.decision.Validation
import com.algorithms.algoking.engine.event.Outcome
import com.algorithms.algoking.engine.narration.NarrationId
import com.algorithms.algoking.engine.scene.CellState
import com.algorithms.algoking.engine.scene.DpTableScene
import com.algorithms.algoking.engine.walkthrough.WatchStepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Floyd–Warshall.
 *
 * The finished table is checked against [reference] — every pair's distance found
 * by relaxing every road until nothing changes, with no rounds and no via node, so
 * it cannot share the lesson's mistakes.
 */
class FloydWarshallTest {

    private val algorithm = FloydWarshallAlgorithm()
    private val teaching = FloydWarshallDatasets.teachingGraph
    private val tryGraph = FloydWarshallDatasets.tryGraph

    private fun runner(graph: Graph) =
        AlgorithmRunner(algorithm, Dataset(values = emptyList(), graph = graph))

    private fun trace(graph: Graph): List<FwState> {
        val runner = runner(graph)
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
        error("Floyd–Warshall did not terminate")
    }

    private fun solve(graph: Graph) = trace(graph).last()

    private fun decisions(graph: Graph): List<Pair<FwState, Decision<FwAction>>> =
        trace(graph).mapNotNull { state ->
            (algorithm.probe(state) as? Probe.Decide)?.let { state to it.decision }
        }

    /** Relax every road, both ways, until nothing changes. */
    private fun reference(graph: Graph): List<List<Int?>> {
        val ids = graph.ids
        val d = ids.map { a -> ids.map { b -> if (a == b) 0 else null }.toMutableList() }
        var changed = true
        while (changed) {
            changed = false
            for ((a, b) in graph.edges) {
                val w = graph.weightOf(a, b)!!
                for (s in ids.indices) {
                    for ((x, y) in listOf(a to b, b to a)) {
                        val via = d[s][ids.indexOf(x)] ?: continue
                        val to = ids.indexOf(y)
                        if (d[s][to] == null || via + w < d[s][to]!!) {
                            d[s][to] = via + w
                            changed = true
                        }
                    }
                }
            }
        }
        return d
    }

    // ── The lesson's own datasets ────────────────────────────────────────────

    @Test
    fun `the teaching run ends at the table the lesson was designed around`() {
        val end = solve(teaching)
        assertEquals(
            listOf(
                listOf(0, 2, 5, 6),
                listOf(2, 0, 3, 4),
                listOf(5, 3, 0, 1),
                listOf(6, 4, 1, 0),
            ),
            end.dist,
        )
        assertEquals(reference(teaching), end.dist)
    }

    @Test
    fun `the try run has its own table`() {
        val end = solve(tryGraph)
        assertEquals(
            listOf(
                listOf(0, 3, 1, 8),
                listOf(3, 0, 2, 5),
                listOf(1, 2, 0, 7),
                listOf(8, 5, 7, 0),
            ),
            end.dist,
        )
        assertEquals(reference(tryGraph), end.dist)
    }

    @Test
    fun `the table starts with the direct roads and infinity elsewhere`() {
        val start = runner(teaching).current.state
        assertEquals(
            listOf(
                listOf(0, 2, null, 10),
                listOf(2, 0, 3, 7),
                listOf(null, 3, 0, 1),
                listOf(10, 7, 1, 0),
            ),
            start.dist,
        )
    }

    @Test
    fun `the table stays symmetric at every step`() {
        for (graph in listOf(teaching, tryGraph)) {
            for (state in trace(graph)) {
                for (i in 0 until state.n) {
                    for (j in 0 until state.n) assertEquals(state.d(i, j), state.d(j, i))
                }
            }
        }
    }

    @Test
    fun `ten comparisons each, four of them improvements, and no ties`() {
        for (graph in listOf(teaching, tryGraph)) {
            val ds = decisions(graph)
            assertEquals(10, ds.size)
            assertEquals(4, ds.count { (s, _) -> s.current!!.let { (i, j) -> s.improves(i, j) } })
            for ((s, _) in ds) {
                val (i, j) = s.current!!
                assertTrue("tie at ${s.name(i)}→${s.name(j)}", s.detour(i, j) != s.d(i, j))
            }
        }
    }

    // ── Decisions ────────────────────────────────────────────────────────────

    @Test
    fun `every decision is fully specified`() {
        for (graph in listOf(teaching, tryGraph)) {
            for ((state, d) in decisions(graph)) {
                val (i, j) = state.current!!
                assertEquals(DecisionKind.OPTIONS, d.kind)
                assertTrue(d.options.size in 2..3)
                assertEquals(d.options.size, d.options.map { it.action }.toSet().size)
                assertEquals(FwAction.SetDistance(state.relaxed(i, j)), d.correct)
                assertTrue(d.correct in d.options.map { it.action })
                assertEquals(3, d.guidance.size)
                assertEquals(d.options.size - 1, d.whyWrong.size)
                assertFalse(d.correct in d.whyWrong.keys)
                assertFalse("Try must never answer this", d.autoInTry)
            }
        }
    }

    @Test
    fun `a wrong number changes nothing`() {
        for (graph in listOf(teaching, tryGraph)) {
            for ((state, d) in decisions(graph)) {
                for (option in d.options.map { it.action }) {
                    if (option == d.correct) continue
                    assertTrue(DecisionValidation.validate(d, option, 0) is Validation.Retry)
                    assertEquals(state, algorithm.apply(state, option).next)
                }
            }
        }
    }

    @Test
    fun `adding only one leg is named as its own mistake`() {
        // Round A, B → D: 7 now, 2 + 10 = 12 through A. The 2 is the half-route slip.
        val (state, d) = decisions(teaching).first()
        assertEquals("A", state.viaNode)
        assertEquals(
            NarrationId.FW_WHY_ONE_LEG,
            d.whyWrong.getValue(FwAction.SetDistance(2)).id,
        )
        assertEquals(NarrationId.FW_WHY_WORSE, d.whyWrong.getValue(FwAction.SetDistance(12)).id)
    }

    @Test
    fun `a pair with an infinite leg is passed over without a question`() {
        // Round A: B → C needs C → A, which is ∞ at the start.
        val state = trace(teaching).first { it.viaStarted && it.via == 0 && it.cursor == 0 }
        val (i, j) = state.current!!
        assertEquals("B" to "C", state.name(i) to state.name(j))
        assertEquals(Probe.Mechanical(FwAction.NoRoute), algorithm.probe(state))
    }

    @Test
    fun `the run ends complete`() {
        assertEquals(Probe.Terminal(Outcome.Completed(true)), algorithm.probe(solve(teaching)))
    }

    // ── WATCH ────────────────────────────────────────────────────────────────

    private fun watchSteps() = AlgorithmCatalog.floydWarshall().watchScript().steps

    @Test
    fun `the walkthrough is exactly the beats the lesson was designed as`() {
        assertEquals(
            listOf(
                WatchStepKind.SETUP to NarrationId.FW_WATCH_SETUP,
                WatchStepKind.EXAMINE to NarrationId.FW_WATCH_ROUND, // through A
                WatchStepKind.KEEP to NarrationId.FW_WATCH_KEEP, // B→D
                WatchStepKind.EXAMINE to NarrationId.FW_WATCH_ROUND, // through B
                WatchStepKind.ELIMINATE to NarrationId.FW_WATCH_UPDATE, // A→C ∞ → 5
                WatchStepKind.ELIMINATE to NarrationId.FW_WATCH_UPDATE, // A→D 10 → 9
                WatchStepKind.KEEP to NarrationId.FW_WATCH_KEEP, // C→D
                WatchStepKind.EXAMINE to NarrationId.FW_WATCH_ROUND, // through C
                WatchStepKind.KEEP to NarrationId.FW_WATCH_KEEP, // A→B
                WatchStepKind.ELIMINATE to NarrationId.FW_WATCH_UPDATE, // A→D 9 → 6
                WatchStepKind.ELIMINATE to NarrationId.FW_WATCH_UPDATE, // B→D 7 → 4
                WatchStepKind.EXAMINE to NarrationId.FW_WATCH_ROUND, // through D
                WatchStepKind.KEEP to NarrationId.FW_WATCH_KEEP,
                WatchStepKind.KEEP to NarrationId.FW_WATCH_KEEP,
                WatchStepKind.KEEP to NarrationId.FW_WATCH_KEEP,
                WatchStepKind.FOUND to NarrationId.FW_WATCH_DONE,
                WatchStepKind.INSIGHT to NarrationId.FW_WATCH_INSIGHT,
                WatchStepKind.SUMMARY to NarrationId.FW_WATCH_SUMMARY,
            ),
            watchSteps().map { it.kind to it.headline.id },
        )
    }

    @Test
    fun `the route through several towns is called out, once`() {
        val built = watchSteps().filter { it.support?.id == NarrationId.FW_WATCH_UPDATE_BUILT }
        assertEquals(1, built.size)
        // A → D, 9 → 6, through C — using A → C = 5 from round B.
        val args = built.single().support!!.args
        assertEquals(listOf("A", "D", "C", "9", 5, 1, 6, "A", "C", 5), args)
    }

    @Test
    fun `a Watch beat draws the pair it talks about, with the winner marked`() {
        val update = watchSteps().first { it.kind == WatchStepKind.ELIMINATE }
        val scene = update.scene as DpTableScene
        val choice = assertNotNull(scene.choice).let { scene.choice!! }
        assertEquals("∞", choice.first.valueLabel)
        assertEquals("5", choice.second.valueLabel)
        // A → C (row 0, column 2) was just improved.
        assertEquals(CellState.FINALIZED, scene.cells[0][2]!!.state)
        assertEquals("5", scene.cells[0][2]!!.label)
    }

    @Test
    fun `every watch step changes something visible - ADR-020`() {
        watchSteps().zipWithNext { a, b ->
            val changed = a.scene != b.scene ||
                a.headline != b.headline ||
                a.support != b.support ||
                a.bullets != b.bullets
            assertTrue("steps ${a.index} and ${b.index} are identical", changed)
        }
    }
}
