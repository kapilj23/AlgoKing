package com.ttele.algoking.engine

import com.ttele.algoking.engine.algorithms.graphbfs.BreadthFirstSearchAlgorithm
import com.ttele.algoking.engine.algorithms.graphdfs.DepthFirstSearchAlgorithm
import com.ttele.algoking.engine.core.Algorithm
import com.ttele.algoking.engine.core.AlgorithmRunner
import com.ttele.algoking.engine.core.Dataset
import com.ttele.algoking.engine.core.Graph
import com.ttele.algoking.engine.core.Probe
import com.ttele.algoking.engine.dataset.GraphDatasets
import com.ttele.algoking.engine.decision.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Edge weights on the shared [Graph].
 *
 * The field is additive and defaulted, and the half of this file that matters most
 * is the last test: **a traversal must produce exactly the same walk whether or not
 * the graph it is given carries costs.** DFS and BFS shipped long before weights
 * existed and must not be able to tell the difference.
 */
class GraphWeightTest {

    private val plain = GraphDatasets.teachingGraph

    private val weighted = plain.copy(
        weights = Graph.weightsOf(
            Triple("A", "B", 4),
            Triple("A", "C", 7),
            Triple("B", "D", 1),
            Triple("B", "E", 9),
        ),
    )

    // ── The field ────────────────────────────────────────────────────────────

    @Test
    fun `a graph is unweighted unless it is given weights`() {
        assertFalse(plain.isWeighted)
        assertTrue(plain.weights.isEmpty())
        assertNull(plain.weightOf("A", "B"))
        assertTrue(weighted.isWeighted)
    }

    @Test
    fun `an edge has one weight, whichever end it is asked from`() {
        assertEquals(4, weighted.weightOf("A", "B"))
        assertEquals(4, weighted.weightOf("B", "A"))
        assertEquals(1, weighted.weightOf("D", "B"))
        assertEquals(Graph.edgeKey("A", "B"), Graph.edgeKey("B", "A"))
    }

    @Test
    fun `a pair with no edge reads as null rather than zero`() {
        // Zero would be a cost, and a wrong one. Absent is absent.
        assertNull("C and D are not joined", weighted.weightOf("C", "D"))
        assertNull("D and E are not joined", weighted.weightOf("D", "E"))
        assertNull("Z is not a node", weighted.weightOf("A", "Z"))
    }

    @Test
    fun `a non-positive weight cannot be authored`() {
        // Dijkstra settles a node the moment it is cheapest, on the assumption
        // nothing later can beat it. A negative edge breaks that, so the lesson
        // must not be able to hold one — caught at construction, not at runtime.
        assertThrows(IllegalArgumentException::class.java) {
            Graph.weightsOf(Triple("A", "B", -1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            Graph.weightsOf(Triple("A", "B", 0))
        }
    }

    // ── The regression that matters ──────────────────────────────────────────

    private fun <S : Any, A : Action> walk(algorithm: Algorithm<S, A>, graph: Graph): S {
        val runner = AlgorithmRunner(
            algorithm,
            Dataset(values = emptyList(), graph = graph, startNode = "A"),
        )
        var guard = 0
        while (guard++ < 500) {
            when (val probe = runner.probe()) {
                is Probe.Mechanical -> runner.apply(probe.action)
                is Probe.Decide -> runner.apply(probe.decision.correct)
                is Probe.Terminal -> return runner.current.state
            }
        }
        error("did not terminate")
    }

    @Test
    fun `DFS and BFS traverse a weighted graph exactly as they traverse a plain one`() {
        // The guard on the additive change: a traversal asks where it can get to,
        // never what it costs, so adding costs to the graph must be invisible to
        // both of them — same visit order, same state, byte for byte.
        assertEquals(
            walk(DepthFirstSearchAlgorithm(), plain),
            walk(DepthFirstSearchAlgorithm(), weighted).copy(graph = plain),
        )
        assertEquals(
            walk(BreadthFirstSearchAlgorithm(), plain),
            walk(BreadthFirstSearchAlgorithm(), weighted).copy(graph = plain),
        )
    }

    @Test
    fun `weights change nothing about the shape of a graph`() {
        assertEquals(plain.nodes, weighted.nodes)
        assertEquals(plain.edges, weighted.edges)
        assertEquals(plain.ids, weighted.ids)
        for (id in plain.ids) {
            assertEquals(plain.neighbours(id), weighted.neighbours(id))
        }
        assertEquals(plain.reachableFrom("A"), weighted.reachableFrom("A"))
    }
}
