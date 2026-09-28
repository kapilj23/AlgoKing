package com.algorithms.algoking.engine

import com.algorithms.algoking.engine.algorithms.linearsearch.LinearSearchAction
import com.algorithms.algoking.engine.algorithms.linearsearch.LinearSearchAlgorithm
import com.algorithms.algoking.engine.algorithms.linearsearch.LinearSearchState
import com.algorithms.algoking.engine.catalog.AlgorithmCatalog
import com.algorithms.algoking.engine.core.AlgorithmRunner
import com.algorithms.algoking.engine.core.Dataset
import com.algorithms.algoking.engine.core.Probe
import com.algorithms.algoking.engine.dataset.LinearSearchDatasets
import com.algorithms.algoking.engine.decision.DecisionKind
import com.algorithms.algoking.engine.decision.DecisionValidation
import com.algorithms.algoking.engine.decision.Validation
import com.algorithms.algoking.engine.event.Outcome
import com.algorithms.algoking.engine.narration.NarrationId
import com.algorithms.algoking.engine.scene.CellState
import com.algorithms.algoking.engine.scene.SequenceScene
import com.algorithms.algoking.engine.walkthrough.WatchStepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinearSearchTest {

    private val algorithm = LinearSearchAlgorithm()

    private fun trace(dataset: Dataset): List<LinearSearchState> {
        val runner = AlgorithmRunner(algorithm, dataset)
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

    @Test
    fun `watch finds 42 at index 5 in six checks`() {
        val end = trace(LinearSearchDatasets.watch).last()
        assertEquals(5, end.foundAt)
        assertEquals(6, end.checked)
        assertEquals(LinearSearchDatasets.watch.values.indexOf(42), end.foundAt)
        assertEquals(Probe.Terminal(Outcome.Found(5)), algorithm.probe(end))
    }

    @Test
    fun `try finds 21 at index 6 in seven checks`() {
        val end = trace(LinearSearchDatasets.tryIt).last()
        assertEquals(6, end.foundAt)
        assertEquals(7, end.checked)
    }

    @Test
    fun `both arrays are unsorted - the point of the lesson`() {
        for (d in listOf(LinearSearchDatasets.watch, LinearSearchDatasets.tryIt)) {
            assertFalse(d.values == d.values.sorted())
            assertEquals("the target appears once", 1, d.values.count { it == d.target })
        }
    }

    @Test
    fun `a missing target checks every element and ends not found`() {
        val end = trace(Dataset(values = listOf(4, 9, 1), target = 7)).last()
        assertTrue(end.exhausted)
        assertEquals(3, end.checked)
        assertEquals(Probe.Terminal(Outcome.NotFound), algorithm.probe(end))
    }

    @Test
    fun `one decision per element checked, each fully specified`() {
        val states = trace(LinearSearchDatasets.watch)
        val decisions = states.mapNotNull { (algorithm.probe(it) as? Probe.Decide)?.decision }
        assertEquals(6, decisions.size)
        for (d in decisions) {
            assertEquals(DecisionKind.OPTIONS, d.kind)
            assertEquals(listOf(LinearSearchAction.Next, LinearSearchAction.Found), d.options.map { it.action })
            assertEquals(3, d.guidance.size)
            assertEquals(1, d.whyWrong.size)
            assertFalse(d.autoInTry)
        }
        assertEquals(List(5) { LinearSearchAction.Next } + LinearSearchAction.Found, decisions.map { it.correct })
    }

    @Test
    fun `the wrong button changes nothing and names the mistake`() {
        val states = trace(LinearSearchDatasets.watch)
        val first = states.first()
        val d = (algorithm.probe(first) as Probe.Decide).decision
        assertTrue(DecisionValidation.validate(d, LinearSearchAction.Found, 0) is Validation.Retry)
        assertEquals(first, algorithm.apply(first, LinearSearchAction.Found).next)
        assertEquals(NarrationId.LS_WHY_NOT_IT, d.whyWrong.getValue(LinearSearchAction.Found).id)

        val atTarget = states.first { it.index == 5 && it.foundAt == null }
        val hit = (algorithm.probe(atTarget) as Probe.Decide).decision
        assertEquals(NarrationId.LS_WHY_PASSED_IT, hit.whyWrong.getValue(LinearSearchAction.Next).id)
        assertEquals(atTarget, algorithm.apply(atTarget, LinearSearchAction.Next).next)
    }

    @Test
    fun `checked cells grey out behind the pointer`() {
        val mid = trace(LinearSearchDatasets.watch)[3]
        val scene = AlgorithmCatalog.linearSearch().projector.project(mid, emptyList()) as SequenceScene
        assertEquals(
            listOf(CellState.ELIMINATED, CellState.ELIMINATED, CellState.ELIMINATED, CellState.COMPARING),
            scene.cells.take(4).map { it.state },
        )
        assertEquals(3, scene.pointers.single().slot)
        assertEquals(42, scene.badge!!.value)
    }

    @Test
    fun `the walkthrough is one beat per check, then the find`() {
        val steps = AlgorithmCatalog.linearSearch().watchScript().steps
        assertEquals(
            listOf(WatchStepKind.SETUP) + List(5) { WatchStepKind.ELIMINATE } +
                listOf(WatchStepKind.FOUND, WatchStepKind.INSIGHT, WatchStepKind.SUMMARY),
            steps.map { it.kind },
        )
        assertEquals(NarrationId.LS_WATCH_CHECK_FIRST, steps[1].support!!.id)
        steps.zipWithNext { a, b ->
            assertTrue(a.scene != b.scene || a.headline != b.headline || a.support != b.support)
        }
    }
}
