package com.ttele.algoking.engine

import com.ttele.algoking.engine.algorithms.binarysearch.BinarySearchAction
import com.ttele.algoking.engine.algorithms.binarysearch.BinarySearchAlgorithm
import com.ttele.algoking.engine.algorithms.binarysearch.BinarySearchState
import com.ttele.algoking.engine.core.AlgorithmRunner
import com.ttele.algoking.engine.core.Dataset
import com.ttele.algoking.engine.core.Probe
import com.ttele.algoking.engine.dataset.BinarySearchDatasets
import com.ttele.algoking.engine.dataset.ComparisonsBetween
import com.ttele.algoking.engine.dataset.DatasetSpec
import com.ttele.algoking.engine.dataset.EndsWith
import com.ttele.algoking.engine.dataset.NoValueOverlapWith
import com.ttele.algoking.engine.dataset.Scenario
import com.ttele.algoking.engine.dataset.SeededGenerator
import com.ttele.algoking.engine.event.Outcome
import com.ttele.algoking.engine.event.VizEvent
import com.ttele.algoking.engine.scoring.ScoreInput
import com.ttele.algoking.engine.scoring.Scorer
import com.ttele.algoking.engine.scoring.StarFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BinarySearchTest {

    private fun runner(dataset: Dataset) =
        AlgorithmRunner(BinarySearchAlgorithm(), dataset)

    /** The app picks the middle in Watch and Try; this stands in for that host. */
    private fun middleOf(r: AlgorithmRunner<BinarySearchState, BinarySearchAction>) =
        (r.probe() as Probe.Decide).decision.correct

    private fun terminalOf(frames: List<VizEvent>): Outcome? = frames
        .filterIsInstance<VizEvent.Terminal>()
        .lastOrNull()
        ?.outcome

    @Test
    fun `watch dataset finds the target`() {
        val trace = runner(BinarySearchDatasets.watch).runToCompletion()
        val outcome = trace.frames.flatMap { it.events }
            .filterIsInstance<VizEvent.Terminal>()
            .last().outcome
        // The Watch target is 45, at index 6.
        assertEquals(Outcome.Found(6), outcome)
    }

    @Test
    fun `every position in every array is findable`() {
        val values = BinarySearchDatasets.watch.values
        values.forEachIndexed { index, value ->
            val trace = runner(Dataset(values, value)).runToCompletion()
            val outcome = trace.frames.flatMap { it.events }
                .filterIsInstance<VizEvent.Terminal>()
                .last().outcome
            assertEquals("target $value at $index", Outcome.Found(index), outcome)
        }
    }

    @Test
    fun `an absent target empties the range and terminates NOT FOUND`() {
        val trace = runner(Dataset(listOf(3, 8, 14, 21, 29), target = 20)).runToCompletion()
        val outcome = trace.frames.flatMap { it.events }
            .filterIsInstance<VizEvent.Terminal>()
            .last().outcome
        assertEquals(Outcome.NotFound, outcome)
    }

    @Test
    fun `comparison count never exceeds the theoretical optimum`() {
        val values = (1..64).map { it * 3 }
        val optimal = Scorer.optimalComparisons(values.size)
        values.forEach { target ->
            val trace = runner(Dataset(values, target)).runToCompletion()
            val comparisons = trace.frames.last().metrics.comparisons
            assertTrue("target $target used $comparisons > $optimal", comparisons <= optimal)
        }
    }

    @Test
    fun `applying the wrong half is a legal transition that loses the target`() {
        val r = runner(BinarySearchDatasets.watch)
        // mid = 37, target = 45 -> correct is KeepRight. Take the left half instead.
        r.apply(middleOf(r))
        val frame = r.apply(BinarySearchAction.KeepLeft)

        assertTrue("a wrong action is recorded, not rejected", !frame.correct)
        assertEquals(1, frame.metrics.wrongDecisions)

        // The machine keeps running from the diverged state and ends NOT FOUND.
        val trace = r.runToCompletion()
        val outcome = trace.frames.flatMap { it.events }
            .filterIsInstance<VizEvent.Terminal>()
            .last().outcome
        assertEquals(Outcome.NotFound, outcome)
    }

    @Test
    fun `rewind restores the exact previous state`() {
        val r = runner(BinarySearchDatasets.watch)
        val before = r.current.state
        r.apply(middleOf(r))
        r.apply(BinarySearchAction.KeepLeft)
        r.rewind(2)
        assertEquals(before, r.current.state)
    }

    @Test
    fun `probe offers FOUND only when the middle actually is the target`() {
        val r = runner(Dataset(listOf(10, 20, 30), target = 20))
        r.apply(middleOf(r))
        val decide = r.probe() as Probe.Decide
        assertTrue(decide.decision.options.any { it.action == BinarySearchAction.Found })
        assertEquals(BinarySearchAction.Found, decide.decision.correct)
    }

    @Test
    fun `probe withholds FOUND when the middle is not the target`() {
        val r = runner(BinarySearchDatasets.watch)
        r.apply(middleOf(r))
        val decide = r.probe() as Probe.Decide
        assertTrue(decide.decision.options.none { it.action == BinarySearchAction.Found })
    }

    @Test
    fun `generated challenges satisfy their scenario and never reuse lesson data`() {
        val generator = SeededGenerator(BinarySearchAlgorithm())
        listOf(Scenario.EARLY, Scenario.MIDDLE, Scenario.LATE, Scenario.ABSENT)
            .forEach { scenario ->
                repeat(12) { i ->
                    val spec = DatasetSpec(
                        size = 11..13,
                        valueRange = 2..99,
                        scenario = scenario,
                        constraints = listOf(
                            EndsWith(found = scenario != Scenario.ABSENT),
                            ComparisonsBetween(2, 5),
                            NoValueOverlapWith(
                                BinarySearchDatasets.watch,
                                BinarySearchDatasets.tryIt,
                            ),
                        ),
                    )
                    val dataset = generator.generate(spec, seed = i.toLong())
                    assertTrue("sorted", dataset.values.sorted() == dataset.values)
                    assertTrue("distinct", dataset.values.distinct().size == dataset.values.size)
                }
            }
    }

    @Test
    fun `a perfect run scores three stars and is flagged optimal`() {
        val trace = runner(BinarySearchDatasets.watch).runToCompletion()
        val result = Scorer.score(
            ScoreInput(
                family = StarFamily.EFFICIENCY,
                metrics = trace.frames.last().metrics,
                optimalComparisons = trace.frames.last().metrics.comparisons,
                completed = true,
            ),
        )
        assertEquals(3, result.stars)
        assertTrue(result.optimal)
    }

    @Test
    fun `state stays immutable across a run`() {
        val r = runner(BinarySearchDatasets.watch)
        val first: BinarySearchState = r.current.state
        r.runToCompletion()
        assertEquals(0, first.lo)
        assertEquals(11, first.hi)
        assertEquals(null, first.mid)
    }
}
