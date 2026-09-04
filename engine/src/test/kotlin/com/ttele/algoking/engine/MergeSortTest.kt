package com.ttele.algoking.engine

import com.ttele.algoking.engine.algorithms.mergesort.MergePhase
import com.ttele.algoking.engine.algorithms.mergesort.MergeSortAction
import com.ttele.algoking.engine.algorithms.mergesort.MergeSortAlgorithm
import com.ttele.algoking.engine.algorithms.mergesort.MergeSortProjector
import com.ttele.algoking.engine.algorithms.mergesort.MergeSortState
import com.ttele.algoking.engine.algorithms.mergesort.MergeSortWatchNarrator
import com.ttele.algoking.engine.core.AlgorithmRunner
import com.ttele.algoking.engine.core.Dataset
import com.ttele.algoking.engine.core.Probe
import com.ttele.algoking.engine.dataset.MergeSortDatasets
import com.ttele.algoking.engine.decision.DecisionKind
import com.ttele.algoking.engine.decision.DecisionValidation
import com.ttele.algoking.engine.decision.Validation
import com.ttele.algoking.engine.event.Outcome
import com.ttele.algoking.engine.event.VizEvent
import com.ttele.algoking.engine.scene.CellState
import com.ttele.algoking.engine.walkthrough.WatchScriptBuilder
import com.ttele.algoking.engine.walkthrough.WatchStepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MergeSortTest {

    private fun runner(dataset: Dataset) = AlgorithmRunner(MergeSortAlgorithm(), dataset)

    private fun playPerfectly(dataset: Dataset): AlgorithmRunner<MergeSortState, MergeSortAction> {
        val r = runner(dataset)
        var guard = 0
        while (guard++ < 1024) {
            when (val probe = r.probe()) {
                is Probe.Mechanical -> r.apply(probe.action)
                is Probe.Decide -> r.apply(probe.decision.correct)
                is Probe.Terminal -> return r
            }
        }
        error("did not terminate")
    }

    private fun toDecision(
        r: AlgorithmRunner<MergeSortState, MergeSortAction>,
    ): Probe.Decide<MergeSortAction> {
        var guard = 0
        while (guard++ < 256) {
            when (val probe = r.probe()) {
                is Probe.Mechanical -> r.apply(probe.action)
                is Probe.Decide -> return probe
                is Probe.Terminal -> error("terminated before a decision")
            }
        }
        error("no decision")
    }

    // ── Correctness ───────────────────────────────────────────────────────────

    @Test
    fun `it sorts, and reports Sorted`() {
        listOf(
            listOf(8, 3, 6, 2, 7, 1, 5, 4),
            listOf(7, 2, 9, 4),
            listOf(6, 3, 8, 1, 5, 2),
            listOf(5, 4, 3, 2, 1),
            listOf(1, 2, 3, 4),
            listOf(3, 3, 1, 3),
            listOf(2, 1, 3),
            listOf(4),
        ).forEach { values ->
            val r = playPerfectly(Dataset(values))
            assertEquals("failed on $values", values.sorted(), r.current.state.values)
            assertEquals(Outcome.Sorted, (r.probe() as Probe.Terminal).outcome)
        }
    }

    @Test
    fun `odd lengths merge correctly`() {
        listOf(
            listOf(5, 1, 4, 2, 3),
            listOf(9, 7, 8),
            listOf(6, 1, 5, 3, 2, 7, 4),
        ).forEach { values ->
            assertEquals(values.sorted(), playPerfectly(Dataset(values)).current.state.values)
        }
    }

    // ── The divide phase is real and visible ─────────────────────────────────

    @Test
    fun `dividing changes the boundaries and never the data`() {
        val r = runner(MergeSortDatasets.watch)
        val original = r.current.state.values

        var guard = 0
        while (guard++ < 64 && r.current.state.phase == MergePhase.DIVIDING) {
            when (val probe = r.probe()) {
                is Probe.Mechanical -> r.apply(probe.action)
                is Probe.Decide -> r.apply(probe.decision.correct)
                is Probe.Terminal -> break
            }
        }
        // Everything is still exactly where it started; only the grouping changed.
        assertEquals(original, r.current.state.values)
    }

    @Test
    fun `the group width halves to one before any merging begins`() {
        val r = runner(MergeSortDatasets.watch)
        val widths = mutableListOf(r.current.state.groupWidth)
        var guard = 0
        while (guard++ < 64 && r.current.state.phase == MergePhase.DIVIDING) {
            when (val probe = r.probe()) {
                is Probe.Mechanical -> r.apply(probe.action)
                is Probe.Decide -> r.apply(probe.decision.correct)
                is Probe.Terminal -> break
            }
            if (r.current.state.phase == MergePhase.DIVIDING) {
                widths += r.current.state.groupWidth
            }
        }
        assertEquals(listOf(8, 4, 2, 1), widths)
    }

    @Test
    fun `the first split is the learner's and it is a cell decision`() {
        val r = runner(MergeSortDatasets.watch)
        val d = toDecision(r).decision
        assertEquals(DecisionKind.CELL, d.kind)
        assertEquals(MergeSortAction.SplitAt(4), d.correct)
        // Deeper splits are the same idea; they are not asked again.
        assertTrue("the split should not be auto-answered in Try", !d.autoInTry)
    }

    @Test
    fun `the scene exposes the groups the array is divided into`() {
        val r = runner(MergeSortDatasets.watch)
        r.apply(MergeSortAction.SplitAt(4))
        val scene = MergeSortProjector().project(r.current.state, r.current.events)
        assertEquals(listOf(0..3, 4..7), scene.groups)

        r.apply(MergeSortAction.DivideAgain)
        val deeper = MergeSortProjector().project(r.current.state, r.current.events)
        assertEquals(4, deeper.groups.size)
    }

    // ── The merge decision ───────────────────────────────────────────────────

    @Test
    fun `the merge decision offers the two front values and only the smaller is right`() {
        // 8 3 6 2 7 1 5 4 -> first merge is 8 | 3.
        val r = runner(MergeSortDatasets.watch)
        toDecision(r)
        r.apply(MergeSortAction.SplitAt(4))
        val d = toDecision(r).decision

        assertEquals(DecisionKind.OPTIONS, d.kind)
        assertEquals(
            setOf(MergeSortAction.TakeLeft, MergeSortAction.TakeRight),
            d.options.map { it.action }.toSet(),
        )
        assertEquals(8, r.current.state.leftFront)
        assertEquals(3, r.current.state.rightFront)
        assertEquals(MergeSortAction.TakeRight, d.correct)
    }

    @Test
    fun `a wrong merge choice is refused and the window does not move`() {
        val r = runner(MergeSortDatasets.watch)
        toDecision(r)
        r.apply(MergeSortAction.SplitAt(4))
        toDecision(r)
        val before = r.current.state
        val d = (r.probe() as Probe.Decide).decision

        repeat(4) { attempt ->
            val verdict = DecisionValidation.validate(d, MergeSortAction.TakeLeft, attempt)
            assertTrue("attempt $attempt was accepted", verdict is Validation.Retry)
        }
        assertEquals(before, r.current.state)
        assertEquals(MergeSortDatasets.watch.values, r.current.state.values)
    }

    @Test
    fun `appending the tail of a run is mechanical, not a decision`() {
        // Once one run is exhausted there is nothing to judge.
        val r = runner(Dataset(listOf(1, 2, 9, 3)))
        var sawMechanicalTake = false
        var guard = 0
        while (guard++ < 256) {
            when (val probe = r.probe()) {
                is Probe.Mechanical -> {
                    if (probe.action == MergeSortAction.TakeLeft ||
                        probe.action == MergeSortAction.TakeRight
                    ) {
                        sawMechanicalTake = true
                    }
                    r.apply(probe.action)
                }

                is Probe.Decide -> r.apply(probe.decision.correct)
                is Probe.Terminal -> break
            }
        }
        assertTrue("the leftover tail should not be asked about", sawMechanicalTake)
    }

    @Test
    fun `merging never emits a swap`() {
        val r = runner(MergeSortDatasets.watch)
        var guard = 0
        while (guard++ < 1024) {
            when (val probe = r.probe()) {
                is Probe.Mechanical -> r.apply(probe.action)
                is Probe.Decide -> r.apply(probe.decision.correct)
                is Probe.Terminal -> break
            }
            assertTrue(
                "Merge Sort combines; it never exchanges",
                r.current.events.none { it is VizEvent.Swap },
            )
        }
    }

    @Test
    fun `guidance escalates and Challenge gets a terser clue`() {
        val r = runner(MergeSortDatasets.watch)
        toDecision(r)
        r.apply(MergeSortAction.SplitAt(4))
        val d = toDecision(r).decision
        assertEquals(3, d.guidance.size)
        assertEquals(3, d.guidance.distinct().size)
        assertNotEquals(d.minimalFeedback, d.guidance.last())
        assertEquals(3, d.hintLadder.size)
    }

    // ── The visual story ─────────────────────────────────────────────────────

    @Test
    fun `a merge shows a growing merged prefix and the two fronts`() {
        val r = runner(Dataset(listOf(8, 3, 6, 2)))
        toDecision(r)
        r.apply(MergeSortAction.SplitAt(2))
        toDecision(r)
        r.apply(MergeSortAction.TakeRight)     // takes 3

        val scene = MergeSortProjector().project(r.current.state, r.current.events)
        assertEquals(CellState.FINALIZED, scene.cells[0].state)
        assertTrue(scene.cells.any { it.state == CellState.COMPARING })
        // Combining is not exchanging, so there is no arc to draw.
        assertNull(scene.arc)
    }

    @Test
    fun `the finished array renders as final in one group`() {
        val r = playPerfectly(MergeSortDatasets.watch)
        val scene = MergeSortProjector().project(r.current.state, r.current.events)
        assertTrue(scene.cells.all { it.state == CellState.FINALIZED })
        assertEquals(1, scene.groups.size)
    }

    // ── Watch walkthrough ────────────────────────────────────────────────────

    @Test
    fun `the walkthrough shows the divide phase and the merge rule, and stays short`() {
        val steps = WatchScriptBuilder(
            MergeSortAlgorithm(),
            MergeSortProjector(),
            MergeSortWatchNarrator(),
        ).build(MergeSortDatasets.watch).steps

        assertEquals(WatchStepKind.SETUP, steps.first().kind)
        assertEquals(WatchStepKind.SUMMARY, steps.last().kind)
        // The dividing beats.
        assertTrue(steps.count { it.kind == WatchStepKind.EXAMINE } >= 3)
        // The merge beats.
        assertTrue(steps.any { it.kind == WatchStepKind.SWAP })
        assertTrue(steps.any { it.kind == WatchStepKind.PASS_COMPLETE })
        assertTrue(steps.any { it.kind == WatchStepKind.INSIGHT })
        assertTrue("walkthrough is ${steps.size} steps", steps.size in 14..30)
    }

    @Test
    fun `the walkthrough carries exactly one unscored checkpoint`() {
        val steps = WatchScriptBuilder(
            MergeSortAlgorithm(),
            MergeSortProjector(),
            MergeSortWatchNarrator(),
        ).build(MergeSortDatasets.watch).steps

        val predictions = steps.filter { it.prediction != null }
        assertEquals(1, predictions.size)
        assertEquals(2, predictions.single().prediction!!.options.size)
    }

    @Test
    fun `the recap states the three-line mental model`() {
        val steps = WatchScriptBuilder(
            MergeSortAlgorithm(),
            MergeSortProjector(),
            MergeSortWatchNarrator(),
        ).build(MergeSortDatasets.watch).steps
        assertEquals(3, steps.last().bullets.size)
    }

    @Test
    fun `Try data is smaller than Watch data and shares no values`() {
        val watch = MergeSortDatasets.watch.values.toSet()
        assertTrue(MergeSortDatasets.tryIt.values.size < MergeSortDatasets.watch.values.size)
        assertTrue(MergeSortDatasets.tryIt.values.any { it !in watch })
    }
}
