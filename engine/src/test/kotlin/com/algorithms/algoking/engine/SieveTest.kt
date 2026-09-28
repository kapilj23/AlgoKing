package com.algorithms.algoking.engine

import com.algorithms.algoking.engine.algorithms.sieve.SieveAction
import com.algorithms.algoking.engine.algorithms.sieve.SieveAlgorithm
import com.algorithms.algoking.engine.algorithms.sieve.SieveState
import com.algorithms.algoking.engine.catalog.AlgorithmCatalog
import com.algorithms.algoking.engine.core.AlgorithmRunner
import com.algorithms.algoking.engine.core.Dataset
import com.algorithms.algoking.engine.core.Probe
import com.algorithms.algoking.engine.dataset.SieveDatasets
import com.algorithms.algoking.engine.decision.Decision
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

/** The primes are checked against trial division — no sieve involved. */
class SieveTest {

    private val algorithm = SieveAlgorithm()

    private fun trace(dataset: Dataset): List<SieveState> {
        val runner = AlgorithmRunner(algorithm, dataset)
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
        error("did not terminate")
    }

    private fun decisions(dataset: Dataset): List<Pair<SieveState, Decision<SieveAction>>> =
        trace(dataset).mapNotNull { s -> (algorithm.probe(s) as? Probe.Decide)?.let { s to it.decision } }

    private fun primesByDivision(n: Int): List<Int> =
        (2..n).filter { k -> (2 until k).none { k % it == 0 } }

    @Test
    fun `watch finds the ten primes up to 30`() {
        val end = trace(SieveDatasets.watch).last()
        assertEquals(listOf(2, 3, 5, 7, 11, 13, 17, 19, 23, 29), end.primes)
        assertEquals(primesByDivision(30), end.primes)
        assertEquals(Probe.Terminal(Outcome.Completed(true)), algorithm.probe(end))
    }

    @Test
    fun `try finds the fifteen primes up to 50`() {
        val end = trace(SieveDatasets.tryIt).last()
        assertEquals(15, end.primes.size)
        assertEquals(primesByDivision(50), end.primes)
    }

    @Test
    fun `the sieve is right for every limit up to 120`() {
        for (n in 2..120) {
            assertEquals("n = $n", primesByDivision(n), trace(Dataset(values = emptyList(), target = n)).last().primes)
        }
    }

    @Test
    fun `watch asks six questions and try asks eight`() {
        val watch = decisions(SieveDatasets.watch).map { it.second.correct }
        assertEquals(
            listOf(
                SieveAction.Pick(2),
                SieveAction.Pick(3), SieveAction.StartAt(9),
                SieveAction.Pick(5), SieveAction.StartAt(25),
                SieveAction.Pick(7),
            ),
            watch,
        )
        val tryIt = decisions(SieveDatasets.tryIt).map { it.second.correct }
        assertEquals(8, tryIt.size)
        assertEquals(SieveAction.StartAt(49), tryIt[6])
        assertEquals(SieveAction.Pick(11), tryIt.last())
    }

    @Test
    fun `every decision is fully specified, and wrong answers change nothing`() {
        for ((s, d) in decisions(SieveDatasets.tryIt)) {
            assertEquals(3, d.guidance.size)
            assertEquals(d.options.size - 1, d.whyWrong.size)
            assertFalse(d.correct in d.whyWrong.keys)
            assertFalse(d.autoInTry)
            for (option in d.options.map { it.action }) {
                if (option == d.correct) continue
                assertTrue(DecisionValidation.validate(d, option, 0) is Validation.Retry)
                assertEquals(s, algorithm.apply(s, option).next)
            }
        }
    }

    @Test
    fun `each wrong answer names its own mistake`() {
        val ds = decisions(SieveDatasets.watch)
        // Picking after 2 has crossed its multiples: next is 3.
        val (_, pick3) = ds[1]
        assertEquals(DecisionKind.CELL, pick3.kind)
        fun why(k: Int) = pick3.whyWrong.getValue(SieveAction.Pick(k))
        assertEquals(NarrationId.SV_WHY_ONE, why(1).id)
        assertEquals(NarrationId.SV_WHY_DONE, why(2).id)
        assertEquals(NarrationId.SV_WHY_CROSSED, why(4).id)
        assertEquals(listOf(4, 2, 2), why(4).args)
        assertEquals(NarrationId.SV_WHY_NOT_SMALLEST, why(5).id)

        val (_, start3) = ds[2]
        assertEquals(listOf(SieveAction.StartAt(6), SieveAction.StartAt(9)), start3.options.map { it.action })
        assertEquals(NarrationId.SV_WHY_START_DOUBLE, start3.whyWrong.getValue(SieveAction.StartAt(6)).id)
    }

    @Test
    fun `a number keeps the prime that crossed it out first`() {
        val end = trace(SieveDatasets.watch).last()
        assertEquals(2, end.crossedBy[6])
        assertEquals(3, end.crossedBy[15])
        assertEquals(5, end.crossedBy[25])
    }

    @Test
    fun `the grid shows six columns and marks the multiples just crossed`() {
        val afterThree = trace(SieveDatasets.watch).first { it.justCrossed.firstOrNull() == 9 }
        val scene = AlgorithmCatalog.sieve().projector.project(afterThree, emptyList()) as SequenceScene
        assertEquals(6, scene.gridColumns)
        val state = scene.cells.associate { it.value to it.state }
        assertEquals(CellState.COMPARING, state[3])
        assertEquals(CellState.CANDIDATE, state[9])
        assertEquals(CellState.CANDIDATE, state[15])
        assertEquals(CellState.ELIMINATED, state[4])
        assertEquals(CellState.ELIMINATED, state[1])
        assertEquals(CellState.IDLE, state[7])
    }

    @Test
    fun `the walkthrough is exactly the beats the lesson was designed as`() {
        val steps = AlgorithmCatalog.sieve().watchScript().steps
        assertEquals(
            listOf(
                WatchStepKind.SETUP,
                WatchStepKind.EXAMINE, WatchStepKind.ELIMINATE, // 2
                WatchStepKind.EXAMINE, WatchStepKind.COMPARE, WatchStepKind.ELIMINATE, // 3
                WatchStepKind.EXAMINE, WatchStepKind.COMPARE, WatchStepKind.ELIMINATE, // 5
                WatchStepKind.EXAMINE, // 7 — and stop
                WatchStepKind.FOUND,
                WatchStepKind.INSIGHT,
                WatchStepKind.SUMMARY,
            ),
            steps.map { it.kind },
        )
        assertEquals(NarrationId.SV_WATCH_PICK_STOP, steps[9].support!!.id)
        steps.zipWithNext { a, b ->
            assertTrue(a.scene != b.scene || a.headline != b.headline || a.support != b.support)
        }
    }
}
