package com.algorithms.algoking.engine

import com.algorithms.algoking.engine.algorithms.gcd.EuclidGcdAlgorithm
import com.algorithms.algoking.engine.algorithms.gcd.GcdAction
import com.algorithms.algoking.engine.algorithms.gcd.GcdState
import com.algorithms.algoking.engine.catalog.AlgorithmCatalog
import com.algorithms.algoking.engine.core.AlgorithmRunner
import com.algorithms.algoking.engine.core.Dataset
import com.algorithms.algoking.engine.core.Probe
import com.algorithms.algoking.engine.dataset.EuclidGcdDatasets
import com.algorithms.algoking.engine.decision.Decision
import com.algorithms.algoking.engine.decision.DecisionValidation
import com.algorithms.algoking.engine.decision.Validation
import com.algorithms.algoking.engine.event.Outcome
import com.algorithms.algoking.engine.narration.NarrationId
import com.algorithms.algoking.engine.scene.CellState
import com.algorithms.algoking.engine.scene.DpTableScene
import com.algorithms.algoking.engine.walkthrough.WatchStepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The answer is checked against the largest number dividing both — found by trying them all. */
class EuclidGcdTest {

    private val algorithm = EuclidGcdAlgorithm()

    private fun trace(dataset: Dataset): List<GcdState> {
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

    private fun decisions(dataset: Dataset): List<Pair<GcdState, Decision<GcdAction>>> =
        trace(dataset).mapNotNull { s -> (algorithm.probe(s) as? Probe.Decide)?.let { s to it.decision } }

    private fun bruteGcd(a: Int, b: Int): Int = (1..minOf(a, b)).last { a % it == 0 && b % it == 0 }

    @Test
    fun `watch - 48 and 18 in three steps`() {
        val end = trace(EuclidGcdDatasets.watch).last()
        assertEquals(listOf(12, 6, 0), end.steps.map { it.r })
        assertEquals(6, end.a)
        assertEquals(bruteGcd(48, 18), end.a)
        assertEquals(Probe.Terminal(Outcome.Completed(true)), algorithm.probe(end))
    }

    @Test
    fun `try - 270 and 192 in four steps`() {
        val end = trace(EuclidGcdDatasets.tryIt).last()
        assertEquals(listOf(78, 36, 6, 0), end.steps.map { it.r })
        assertEquals(bruteGcd(270, 192), end.a)
    }

    @Test
    fun `right for every pair up to 60`() {
        for (a in 1..60) for (b in 1..a) {
            assertEquals("$a, $b", bruteGcd(a, b), trace(Dataset(values = listOf(a, b))).last().a)
        }
    }

    @Test
    fun `every decision is fully specified, and wrong answers change nothing`() {
        for (d in listOf(EuclidGcdDatasets.watch, EuclidGcdDatasets.tryIt)) {
            for ((s, dec) in decisions(d)) {
                assertTrue(dec.options.size in 2..3)
                assertEquals(3, dec.guidance.size)
                assertEquals(dec.options.size - 1, dec.whyWrong.size)
                assertFalse(dec.correct in dec.whyWrong.keys)
                assertFalse(dec.autoInTry)
                for (option in dec.options.map { it.action }) {
                    if (option == dec.correct) continue
                    assertTrue(DecisionValidation.validate(dec, option, 0) is Validation.Retry)
                    assertEquals(s, algorithm.apply(s, option).next)
                }
            }
        }
    }

    @Test
    fun `each wrong number names its own mistake`() {
        val ds = decisions(EuclidGcdDatasets.watch)
        // 48 mod 18: 12 is right, 2 is the quotient, 30 is 48 − 18.
        val (_, first) = ds.first()
        assertEquals(listOf(2, 12, 30), first.options.map { (it.action as GcdAction.Remainder).value })
        assertEquals(NarrationId.GCD_WHY_QUOTIENT, first.whyWrong.getValue(GcdAction.Remainder(2)).id)
        assertEquals(NarrationId.GCD_WHY_SUBTRACT_ONCE, first.whyWrong.getValue(GcdAction.Remainder(30)).id)

        // The answer: 6, not the 0, not the 48.
        val (_, answer) = ds.last()
        assertEquals(GcdAction.Answer(6), answer.correct)
        assertEquals(NarrationId.GCD_WHY_ZERO, answer.whyWrong.getValue(GcdAction.Answer(0)).id)
        assertEquals(NarrationId.GCD_WHY_START, answer.whyWrong.getValue(GcdAction.Answer(48)).id)
    }

    @Test
    fun `the table grows a row per step and marks the answer`() {
        val end = trace(EuclidGcdDatasets.watch).last()
        val scene = AlgorithmCatalog.euclidGcd().projector.project(end, emptyList()) as DpTableScene
        assertEquals(3, scene.cells.size)
        assertEquals(listOf(48, 18, 12), scene.cells[0].map { it!!.value })
        // The GCD is the last row's b: 6, next to the 0.
        assertEquals(6, scene.cells[2]!![1]!!.value)
        assertEquals(CellState.FINALIZED, scene.cells[2]!![1]!!.state)

        val asking = trace(EuclidGcdDatasets.watch).first()
        val open = AlgorithmCatalog.euclidGcd().projector.project(asking, emptyList()) as DpTableScene
        assertEquals(CellState.GHOST, open.cells[0]!![2]!!.state)
        assertEquals(36, open.choice!!.first.value)
        assertEquals(null, open.choice!!.second.value)
    }

    @Test
    fun `the walkthrough is one beat per step, then the answer`() {
        val steps = AlgorithmCatalog.euclidGcd().watchScript().steps
        assertEquals(
            listOf(
                WatchStepKind.SETUP,
                WatchStepKind.COMPARE, WatchStepKind.COMPARE, WatchStepKind.KEEP,
                WatchStepKind.FOUND, WatchStepKind.INSIGHT, WatchStepKind.SUMMARY,
            ),
            steps.map { it.kind },
        )
        // The step beat shows its own working, not the next question.
        val first = steps[1].scene as DpTableScene
        assertEquals(1, first.cells.size)
        assertEquals(12, first.choice!!.second.value)
        steps.zipWithNext { a, b ->
            assertTrue(a.scene != b.scene || a.headline != b.headline || a.support != b.support)
        }
    }
}
