package com.ttele.algoking.engine

import com.ttele.algoking.engine.algorithms.dijkstra.DijkstraAction
import com.ttele.algoking.engine.algorithms.dijkstra.DijkstraAlgorithm
import com.ttele.algoking.engine.algorithms.dijkstra.DijkstraState
import com.ttele.algoking.engine.core.AlgorithmRunner
import com.ttele.algoking.engine.core.Dataset
import com.ttele.algoking.engine.core.Graph
import com.ttele.algoking.engine.core.GraphNode
import com.ttele.algoking.engine.core.Probe
import com.ttele.algoking.engine.dataset.DijkstraDatasets
import com.ttele.algoking.engine.decision.DecisionKind
import com.ttele.algoking.engine.decision.DecisionValidation
import com.ttele.algoking.engine.decision.Validation
import com.ttele.algoking.engine.event.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dijkstra's algorithm.
 *
 * The lesson's answer is **generated**, so the reference it is checked against has
 * to be written independently: [reference] below is a plain textbook Dijkstra with
 * no lesson machinery in it at all, and the two have to agree on every distance and
 * every predecessor of every graph they are given.
 */
class DijkstraTest {

    private val algorithm = DijkstraAlgorithm()
    private val teaching = DijkstraDatasets.teachingGraph
    private val tryGraph = DijkstraDatasets.tryGraph

    private fun dataset(graph: Graph, start: String, target: String) =
        Dataset(values = emptyList(), graph = graph, startNode = start, targetNode = target)

    private fun runner(graph: Graph, start: String, target: String) =
        AlgorithmRunner(algorithm, dataset(graph, start, target))

    /** Drives the real lesson to its terminal state, always choosing correctly. */
    private fun solve(graph: Graph, start: String, target: String): DijkstraState {
        val runner = runner(graph, start, target)
        var guard = 0
        while (guard++ < 2_000) {
            when (val probe = runner.probe()) {
                is Probe.Mechanical -> runner.apply(probe.action)
                is Probe.Decide -> runner.apply(probe.decision.correct)
                is Probe.Terminal -> return runner.current.state
            }
        }
        error("Dijkstra did not terminate")
    }

    /** Every state the run passes through, for asserting on individual beats. */
    private fun trace(graph: Graph, start: String, target: String): List<DijkstraState> {
        val runner = runner(graph, start, target)
        val states = mutableListOf(runner.current.state)
        var guard = 0
        while (guard++ < 2_000) {
            when (val probe = runner.probe()) {
                is Probe.Mechanical -> runner.apply(probe.action)
                is Probe.Decide -> runner.apply(probe.decision.correct)
                is Probe.Terminal -> return states
            }
            states += runner.current.state
        }
        error("Dijkstra did not terminate")
    }

    /**
     * Textbook Dijkstra, written independently of the lesson: no frames, no
     * decisions, no cursor. Settles every reachable node rather than stopping at a
     * target, so the lesson is compared against it only on what it actually
     * settled.
     */
    private fun reference(graph: Graph, start: String): Pair<Map<String, Int>, Map<String, String>> {
        val dist = mutableMapOf(start to 0)
        val prev = mutableMapOf<String, String>()
        val settled = mutableSetOf<String>()
        while (true) {
            val u = dist.keys.filter { it !in settled }.minByOrNull { dist.getValue(it) } ?: break
            settled += u
            for (v in graph.neighbours(u)) {
                val w = graph.weightOf(u, v) ?: continue
                val candidate = dist.getValue(u) + w
                if (candidate < (dist[v] ?: Int.MAX_VALUE)) {
                    dist[v] = candidate
                    prev[v] = u
                }
            }
        }
        return dist to prev
    }

    // ── The lesson's own datasets ────────────────────────────────────────────

    @Test
    fun `the teaching run settles in cost order and finds the cheapest route`() {
        val end = solve(teaching, "A", "F")
        assertEquals(listOf("A", "C", "B", "D", "E", "F"), end.processed)
        assertEquals(
            mapOf("A" to 0, "C" to 2, "B" to 3, "D" to 6, "E" to 7, "F" to 9),
            end.distances,
        )
        assertEquals(listOf("A", "C", "B", "E", "F"), end.pathTo("F"))
        assertEquals(9, end.distanceOf("F"))
    }

    @Test
    fun `the direct edge from A to B loses to the route through C`() {
        // The lesson's opening move: B is reached at 5 and settles at 3, so the
        // first thing the learner watches is a distance being beaten.
        val states = trace(teaching, "A", "F")
        assertTrue("B must be reached at 5 first", states.any { it.distances["B"] == 5 })
        assertTrue("and then improved to 3", states.any { it.distances["B"] == 3 })
        assertEquals(5, teaching.weightOf("A", "B"))
        assertEquals(3, solve(teaching, "A", "F").distanceOf("B"))
    }

    @Test
    fun `D is settled but is not on the answer`() {
        // Settled does not mean chosen — and D is the node with the direct edge to
        // the target that looks shortest on the page.
        val end = solve(teaching, "A", "F")
        assertTrue("D" in end.processed)
        assertFalse("D" in end.pathTo("F"))
    }

    @Test
    fun `the try run keeps twice and wins on the direct route`() {
        val end = solve(tryGraph, "A", "E")
        assertEquals(listOf("A", "C", "B", "D", "E"), end.processed)
        assertEquals(
            mapOf("A" to 0, "C" to 1, "B" to 3, "D" to 5, "E" to 7),
            end.distances,
        )
        assertEquals(listOf("A", "D", "E"), end.pathTo("E"))
        assertEquals(7, end.distanceOf("E"))
    }

    @Test
    fun `every selection in both datasets has exactly one cheapest node`() {
        // A tie would make two answers correct, which a TRY decision cannot say.
        for ((graph, start, target) in listOf(
            Triple(teaching, "A", "F"),
            Triple(tryGraph, "A", "E"),
        )) {
            for (state in trace(graph, start, target)) {
                if (state.current != null || state.frontier.isEmpty()) continue
                val best = state.distances.getValue(state.frontier.first())
                assertEquals(
                    "tie in the frontier of $graph",
                    1,
                    state.frontier.count { state.distances.getValue(it) == best },
                )
            }
        }
    }

    // ── Against an independent implementation ────────────────────────────────

    @Test
    fun `the lesson agrees with textbook Dijkstra on every graph it is given`() {
        val cases = listOf(
            Triple(teaching, "A", "F"),
            Triple(teaching, "A", "E"),
            Triple(teaching, "F", "A"),
            Triple(teaching, "C", "F"),
            Triple(tryGraph, "A", "E"),
            Triple(tryGraph, "E", "A"),
            Triple(tryGraph, "C", "D"),
        )
        for ((graph, start, target) in cases) {
            val end = solve(graph, start, target)
            val (refDist, refPrev) = reference(graph, start)
            for (node in end.processed) {
                assertEquals("distance to $node from $start", refDist[node], end.distanceOf(node))
                assertEquals("predecessor of $node from $start", refPrev[node], end.predecessors[node])
            }
            assertEquals(refDist[target], end.distanceOf(target))
        }
    }

    @Test
    fun `a settled distance is never improved afterwards`() {
        // The guarantee the selection rule buys, asserted directly: once a node is
        // settled, nothing that happens later moves it.
        for ((graph, start, target) in listOf(
            Triple(teaching, "A", "F"),
            Triple(tryGraph, "A", "E"),
        )) {
            val states = trace(graph, start, target)
            val settledAt = mutableMapOf<String, Int>()
            states.forEachIndexed { i, s ->
                s.processed.forEach { node -> settledAt.putIfAbsent(node, i) }
            }
            for ((node, index) in settledAt) {
                val atSettle = states[index].distances.getValue(node)
                for (later in states.drop(index)) {
                    assertEquals("$node moved after settling", atSettle, later.distances[node])
                }
            }
        }
    }

    // ── Relaxation ───────────────────────────────────────────────────────────

    @Test
    fun `a cheaper candidate updates the distance and the predecessor together`() {
        val states = trace(teaching, "A", "F")
        val before = states.last { it.distances["B"] == 5 }
        val after = states.first { it.distances["B"] == 3 }
        assertEquals("A", before.predecessors["B"])
        // The path moves with the number: they are the same fact.
        assertEquals("C", after.predecessors["B"])
    }

    @Test
    fun `a candidate that is not an improvement changes nothing`() {
        // D offers E 6 + 5 = 11 against E's 7. The lesson's KEEP.
        val states = trace(teaching, "A", "F")
        val atKeep = states.first { s ->
            s.pending?.let { it.from == "D" && it.to == "E" } == true
        }
        assertEquals(11, atKeep.pending?.candidate)
        assertEquals(7, atKeep.distanceOf("E"))
        assertFalse(atKeep.improves)
        assertEquals(7, atKeep.relaxedValue)

        val applied = algorithm.apply(atKeep, DijkstraAction.SetDistance(7)).next
        assertEquals(7, applied.distanceOf("E"))
        assertEquals("B", applied.predecessors["E"])
    }

    @Test
    fun `reaching a node for the first time is never a question`() {
        // Infinity loses to everything, so there is nothing to compare and the app
        // does it — but it still names the arithmetic.
        val runner = runner(teaching, "A", "F")
        runner.apply((runner.probe() as Probe.Decide).decision.correct) // select A
        val probe = runner.probe()
        assertTrue("first reach must be mechanical", probe is Probe.Mechanical)
        assertEquals(DijkstraAction.FirstReach, (probe as Probe.Mechanical).action)
    }

    @Test
    fun `the three relaxation options are the answer and the two real mistakes`() {
        val states = trace(teaching, "A", "F")
        val atHero = states.first { s -> s.pending?.let { it.from == "C" && it.to == "B" } == true }
        // C is 2, C-B costs 1, B is currently 5.
        assertEquals(listOf(3, 1, 5), atHero.relaxationOptions())
        assertEquals(3, atHero.relaxedValue)
        assertTrue(atHero.improves)
    }

    // ── The decisions ────────────────────────────────────────────────────────

    @Test
    fun `selection is a tap and relaxation is a value, and both are the learner's`() {
        val runner = runner(teaching, "A", "F")
        val select = (runner.probe() as Probe.Decide).decision
        assertEquals(DecisionKind.CELL, select.kind)
        assertFalse(select.autoInTry)
        assertEquals(DijkstraAction.Select("A"), select.correct)
        assertEquals(teaching.nodes.size, select.options.size)
        runner.apply(select.correct)

        var guard = 0
        while (guard++ < 50 && runner.probe() is Probe.Mechanical) {
            runner.apply((runner.probe() as Probe.Mechanical).action)
        }
        val next = runner.probe()
        assertTrue(next is Probe.Decide)
        val decision = (next as Probe.Decide).decision
        // After A, the frontier is C=2 and B=5, so the next question is a selection.
        assertEquals(DecisionKind.CELL, decision.kind)
        assertEquals(DijkstraAction.Select("C"), decision.correct)
    }

    @Test
    fun `every decision carries a full ladder and a reason for every wrong answer`() {
        val runner = runner(teaching, "A", "F")
        var selections = 0
        var relaxations = 0
        var guard = 0
        while (guard++ < 500) {
            when (val probe = runner.probe()) {
                is Probe.Mechanical -> runner.apply(probe.action)
                is Probe.Terminal -> break
                is Probe.Decide -> {
                    val d = probe.decision
                    if (d.kind == DecisionKind.CELL) selections++ else relaxations++
                    assertEquals(3, d.guidance.size)
                    assertNotNull(d.hint)
                    assertEquals(d.options.size - 1, d.whyWrong.size)
                    assertFalse(d.correct in d.whyWrong.keys)
                    runner.apply(d.correct)
                }
            }
        }
        // Six selections (A included) and four genuine relaxations.
        assertEquals(6, selections)
        assertEquals(4, relaxations)
    }

    // ── Wrong answers ────────────────────────────────────────────────────────

    @Test
    fun `selecting anything but the cheapest node changes nothing`() {
        val runner = runner(teaching, "A", "F")
        runner.apply((runner.probe() as Probe.Decide).decision.correct) // A
        var guard = 0
        while (guard++ < 50 && runner.probe() is Probe.Mechanical) {
            runner.apply((runner.probe() as Probe.Mechanical).action)
        }
        val decision = (runner.probe() as Probe.Decide).decision
        val before = runner.current.state

        for (option in decision.options.map { it.action }) {
            if (option == decision.correct) continue
            assertTrue(DecisionValidation.validate(decision, option, 0) is Validation.Retry)
            assertEquals(before, runner.current.state)
            // And applied directly, it is refused rather than recorded.
            assertEquals(before, algorithm.apply(before, option).next)
        }
    }

    @Test
    fun `setting a distance to anything but the true outcome changes nothing`() {
        val states = trace(teaching, "A", "F")
        val atHero = states.first { s -> s.pending?.let { it.from == "C" && it.to == "B" } == true }
        for (wrong in listOf(1, 5, 0, 99)) {
            val out = algorithm.apply(atHero, DijkstraAction.SetDistance(wrong))
            assertFalse(out.correct)
            assertEquals(atHero, out.next)
        }
        val right = algorithm.apply(atHero, DijkstraAction.SetDistance(3))
        assertTrue(right.correct)
        assertEquals(3, right.next.distanceOf("B"))
    }

    @Test
    fun `the run terminates however badly it is driven`() {
        val runner = runner(teaching, "A", "F")
        val nodes = teaching.ids + listOf("Z")
        var guard = 0
        while (guard++ < 4_000) {
            when (val probe = runner.probe()) {
                is Probe.Terminal -> break
                is Probe.Mechanical -> runner.apply(probe.action)
                is Probe.Decide -> {
                    nodes.forEach { runner.apply(DijkstraAction.Select(it)) }
                    (0..14).forEach { runner.apply(DijkstraAction.SetDistance(it)) }
                    runner.apply(probe.decision.correct)
                }
            }
        }
        assertTrue(runner.probe() is Probe.Terminal)
        assertEquals(listOf("A", "C", "B", "D", "E", "F"), runner.current.state.processed)
        assertEquals(9, runner.current.state.distanceOf("F"))
    }

    // ── Edge cases ───────────────────────────────────────────────────────────

    @Test
    fun `an empty graph is a finished lesson, not a crash`() {
        val empty = Graph(emptyList(), emptyMap())
        val state = algorithm.initial(dataset(empty, "A", "B"))
        assertTrue(state.finished)
        assertTrue(algorithm.probe(state) is Probe.Terminal)
        assertTrue(state.distances.isEmpty())
    }

    @Test
    fun `a single node that is its own target is already the answer`() {
        val one = Graph(listOf(GraphNode("A", "A", 0.5f, 0.5f)), mapOf("A" to emptyList()))
        val end = solve(one, "A", "A")
        assertEquals(listOf("A"), end.processed)
        assertEquals(0, end.distanceOf("A"))
        assertEquals(listOf("A"), end.pathTo("A"))
    }

    @Test
    fun `an unreachable target ends the run and says so`() {
        val split = Graph(
            nodes = listOf(
                GraphNode("A", "A", 0.1f, 0.5f),
                GraphNode("B", "B", 0.4f, 0.5f),
                GraphNode("X", "X", 0.9f, 0.5f),
            ),
            adjacency = mapOf("A" to listOf("B"), "B" to listOf("A"), "X" to emptyList()),
            weights = Graph.weightsOf(Triple("A", "B", 3)),
        )
        val end = solve(split, "A", "X")
        assertTrue(end.finished)
        assertTrue(end.unreachable)
        assertNull(end.distanceOf("X"))
        assertEquals(emptyList<String>(), end.pathTo("X"))
        assertEquals(Outcome.NotFound, terminalOf(split, "A", "X"))
    }

    private fun terminalOf(graph: Graph, start: String, target: String): Outcome {
        val runner = runner(graph, start, target)
        var guard = 0
        while (guard++ < 2_000) {
            when (val probe = runner.probe()) {
                is Probe.Mechanical -> runner.apply(probe.action)
                is Probe.Decide -> runner.apply(probe.decision.correct)
                is Probe.Terminal -> return probe.outcome
            }
        }
        error("did not terminate")
    }

    @Test
    fun `two routes of equal cost resolve deterministically`() {
        // A tie is not wrong, it just means two answers are equally good. The
        // lesson's datasets have none; the engine still has to be predictable.
        val diamond = Graph(
            nodes = listOf(
                GraphNode("A", "A", 0.1f, 0.5f),
                GraphNode("B", "B", 0.4f, 0.1f),
                GraphNode("C", "C", 0.4f, 0.9f),
                GraphNode("D", "D", 0.9f, 0.5f),
            ),
            adjacency = mapOf(
                "A" to listOf("B", "C"),
                "B" to listOf("A", "D"),
                "C" to listOf("A", "D"),
                "D" to listOf("B", "C"),
            ),
            weights = Graph.weightsOf(
                Triple("A", "B", 2), Triple("A", "C", 2),
                Triple("B", "D", 3), Triple("C", "D", 3),
            ),
        )
        val first = solve(diamond, "A", "D")
        val second = solve(diamond, "A", "D")
        assertEquals(first.processed, second.processed)
        assertEquals(first.predecessors, second.predecessors)
        assertEquals(5, first.distanceOf("D"))
    }

    @Test
    fun `a node with no edges is reached only if it is the start`() {
        val lonely = Graph(
            nodes = listOf(GraphNode("A", "A", 0.2f, 0.5f), GraphNode("B", "B", 0.8f, 0.5f)),
            adjacency = mapOf("A" to emptyList(), "B" to emptyList()),
        )
        val end = solve(lonely, "A", "B")
        assertEquals(listOf("A"), end.processed)
        assertTrue(end.unreachable)
    }

    @Test
    fun `every weight in both teaching graphs is positive`() {
        for (graph in listOf(teaching, tryGraph)) {
            assertTrue(graph.isWeighted)
            for ((a, b) in graph.edges) {
                val w = graph.weightOf(a, b)
                assertNotNull("edge $a-$b has no weight", w)
                assertTrue("edge $a-$b is not positive", requireNotNull(w) > 0)
            }
        }
    }
}
