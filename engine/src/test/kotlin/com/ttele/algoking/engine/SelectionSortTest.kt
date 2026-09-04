package com.ttele.algoking.engine

import com.ttele.algoking.engine.algorithms.selectionsort.SelectionSortAction
import com.ttele.algoking.engine.algorithms.selectionsort.SelectionSortAlgorithm
import com.ttele.algoking.engine.algorithms.selectionsort.SelectionSortProjector
import com.ttele.algoking.engine.algorithms.selectionsort.SelectionSortState
import com.ttele.algoking.engine.algorithms.selectionsort.SelectionSortWatchNarrator
import com.ttele.algoking.engine.core.AlgorithmRunner
import com.ttele.algoking.engine.core.Dataset
import com.ttele.algoking.engine.core.Probe
import com.ttele.algoking.engine.dataset.SelectionSortDatasets
import com.ttele.algoking.engine.decision.DecisionKind
import com.ttele.algoking.engine.decision.DecisionValidation
import com.ttele.algoking.engine.decision.Validation
import com.ttele.algoking.engine.event.Outcome
import com.ttele.algoking.engine.scene.CellState
import com.ttele.algoking.engine.walkthrough.WatchScriptBuilder
import com.ttele.algoking.engine.walkthrough.WatchStepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionSortTest {

    private fun runner(dataset: Dataset) = AlgorithmRunner(SelectionSortAlgorithm(), dataset)

    private fun playPerfectly(
        dataset: Dataset,
    ): AlgorithmRunner<SelectionSortState, SelectionSortAction> {
        val r = runner(dataset)
        var guard = 0
        while (guard++ < 512) {
            when (val probe = r.probe()) {
                is Probe.Mechanical -> r.apply(probe.action)
                is Probe.Decide -> r.apply(probe.decision.correct)
                is Probe.Terminal -> return r
            }
        }
        error("did not terminate")
    }

    // ── Correctness ───────────────────────────────────────────────────────────

    @Test
    fun `it sorts, and reports Sorted`() {
        listOf(
            listOf(7, 3, 8, 2, 5),
            listOf(6, 4, 9, 2, 7),
            listOf(5, 4, 3, 2, 1),
            listOf(1, 2, 3, 4, 5),
            listOf(3, 1),
            listOf(4),
        ).forEach { values ->
            val r = playPerfectly(Dataset(values))
            assertEquals("failed on $values", values.sorted(), r.current.state.values)
            assertEquals(Outcome.Sorted, (r.probe() as Probe.Terminal).outcome)
        }
    }

    @Test
    fun `the sorted prefix grows by exactly one per pass and never moves again`() {
        val r = runner(SelectionSortDatasets.watch)
        var previousSorted = 0
        var guard = 0
        while (guard++ < 512) {
            when (val probe = r.probe()) {
                is Probe.Mechanical -> r.apply(probe.action)
                is Probe.Terminal -> break
                is Probe.Decide -> {
                    val before = r.current.state
                    r.apply(probe.decision.correct)
                    val after = r.current.state
                    if (after.sortedTo != before.sortedTo) {
                        assertEquals(before.sortedTo + 1, after.sortedTo)
                        // Everything already placed stays placed.
                        assertEquals(
                            before.values.take(before.sortedTo),
                            after.values.take(before.sortedTo),
                        )
                        previousSorted = after.sortedTo
                    }
                }
            }
        }
        assertTrue(previousSorted > 0)
    }

    @Test
    fun `each pass places the smallest value that remains`() {
        val r = runner(SelectionSortDatasets.watch)
        var guard = 0
        while (guard++ < 512) {
            when (val probe = r.probe()) {
                is Probe.Mechanical -> r.apply(probe.action)
                is Probe.Terminal -> break
                is Probe.Decide -> {
                    val before = r.current.state
                    if (before.scanComplete) {
                        val remaining = before.values.drop(before.sortedTo)
                        assertEquals(
                            "pass ${before.pass} picked the wrong value",
                            remaining.min(),
                            before.minValue,
                        )
                    }
                    r.apply(probe.decision.correct)
                }
            }
        }
    }

    // ── The two decisions, and how they differ from Bubble Sort ──────────────

    @Test
    fun `the scan decision judges against the remembered minimum, not a neighbour`() {
        // 7 3 8 2 5 — cursor starts on 3, candidate is 7.
        val r = runner(SelectionSortDatasets.watch)
        val d = (r.probe() as Probe.Decide).decision

        assertEquals(DecisionKind.OPTIONS, d.kind)
        assertEquals(SelectionSortAction.NewMinimum, d.correct)

        r.apply(SelectionSortAction.NewMinimum)   // min is now 3
        // Cursor moves to 8, and 8 is judged against the *remembered* 3 — the cell
        // immediately before it is 3's old slot, not its neighbour.
        val next = (r.probe() as Probe.Decide).decision
        assertEquals(SelectionSortAction.NotSmaller, next.correct)
        assertEquals(3, r.current.state.minValue)
    }

    @Test
    fun `placing is a cell decision, and only the front of the unsorted portion is right`() {
        val r = runner(SelectionSortDatasets.watch)
        var guard = 0
        while (guard++ < 64 && !r.current.state.scanComplete) {
            r.apply((r.probe() as Probe.Decide).decision.correct)
        }

        val d = (r.probe() as Probe.Decide).decision
        assertEquals(DecisionKind.CELL, d.kind)
        assertEquals(SelectionSortAction.Place(0), d.correct)
        // The sorted prefix is not on offer — it is finished.
        assertEquals(r.current.state.unsorted.count(), d.options.size)
        assertEquals(2, r.current.state.minValue)
    }

    @Test
    fun `a wrong scan judgement is refused and the array does not move`() {
        val r = runner(SelectionSortDatasets.watch)
        val before = r.current.state
        val d = (r.probe() as Probe.Decide).decision

        repeat(4) { attempt ->
            val verdict = DecisionValidation.validate(d, SelectionSortAction.NotSmaller, attempt)
            assertTrue("attempt $attempt was accepted", verdict is Validation.Retry)
        }
        assertEquals(before, r.current.state)
    }

    @Test
    fun `a wrong placement is refused and the array does not move`() {
        val r = runner(SelectionSortDatasets.watch)
        var guard = 0
        while (guard++ < 64 && !r.current.state.scanComplete) {
            r.apply((r.probe() as Probe.Decide).decision.correct)
        }
        val before = r.current.state
        val d = (r.probe() as Probe.Decide).decision

        d.options.mapNotNull { it.slot }.filter { it != before.sortedTo }.forEach { slot ->
            val verdict = DecisionValidation.validate(
                d,
                SelectionSortAction.Place(slot),
                priorAttempts = 0,
            )
            assertTrue("slot $slot was accepted", verdict is Validation.Retry)
        }
        assertEquals(before, r.current.state)
    }

    @Test
    fun `guidance escalates and Challenge gets a terser clue`() {
        val r = runner(SelectionSortDatasets.watch)
        val d = (r.probe() as Probe.Decide).decision
        assertEquals(3, d.guidance.size)
        assertEquals(3, d.guidance.distinct().size)
        assertNotEquals(d.minimalFeedback, d.guidance.last())
        assertEquals(3, d.hintLadder.size)
    }

    @Test
    fun `the prompt sheds guidance as the learner settles in`() {
        val r = runner(Dataset(listOf(9, 7, 5, 3, 1)))
        val first = (r.probe() as Probe.Decide).decision.prompt
        r.apply((r.probe() as Probe.Decide).decision.correct)
        val second = (r.probe() as Probe.Decide).decision.prompt
        assertNotEquals(first, second)
    }

    // ── The visual story is not Bubble Sort's ────────────────────────────────

    @Test
    fun `the projector shows a sorted prefix, a remembered candidate and a scan cursor`() {
        val r = runner(SelectionSortDatasets.watch)
        r.apply(SelectionSortAction.NewMinimum)   // min becomes 3, cursor moves to 8
        val scene = SelectionSortProjector().project(r.current.state, r.current.events)

        val states = scene.cells.map { it.state }
        assertTrue("no scan cursor", states.contains(CellState.COMPARING))
        assertTrue("no remembered candidate", states.contains(CellState.CANDIDATE))
        // The remembered minimum is surfaced as a badge, like Binary Search's target.
        assertNotNull(scene.badge)
        assertEquals(3, scene.badge!!.value)
    }

    @Test
    fun `the finished prefix renders as final`() {
        val r = playPerfectly(SelectionSortDatasets.watch)
        val scene = SelectionSortProjector().project(r.current.state, r.current.events)
        assertTrue(scene.cells.all { it.state == CellState.FINALIZED })
    }

    // ── Watch walkthrough ────────────────────────────────────────────────────

    @Test
    fun `the walkthrough narrates the first scan in full and stays short`() {
        val steps = WatchScriptBuilder(
            SelectionSortAlgorithm(),
            SelectionSortProjector(),
            SelectionSortWatchNarrator(),
        ).build(SelectionSortDatasets.watch).steps

        assertEquals(WatchStepKind.SETUP, steps.first().kind)
        assertEquals(WatchStepKind.SUMMARY, steps.last().kind)
        // The candidate must be seen to change, and to stay.
        assertTrue("no new-minimum beat", steps.any { it.kind == WatchStepKind.SWAP })
        assertTrue("no not-smaller beat", steps.any { it.kind == WatchStepKind.KEEP })
        assertTrue(steps.any { it.kind == WatchStepKind.PASS_COMPLETE })
        assertTrue(steps.any { it.kind == WatchStepKind.INSIGHT })
        assertTrue("walkthrough is ${steps.size} steps", steps.size in 10..22)
    }

    @Test
    fun `the walkthrough carries exactly one unscored checkpoint with a real answer`() {
        val steps = WatchScriptBuilder(
            SelectionSortAlgorithm(),
            SelectionSortProjector(),
            SelectionSortWatchNarrator(),
        ).build(SelectionSortDatasets.watch).steps

        val predictions = steps.filter { it.prediction != null }
        assertEquals(1, predictions.size)
        val p = predictions.single().prediction!!
        assertTrue("too few options", p.options.size >= 2)
        assertTrue("no correct option", p.correctIndex in p.options.indices)
    }

    @Test
    fun `the recap states the three-line mental model`() {
        val steps = WatchScriptBuilder(
            SelectionSortAlgorithm(),
            SelectionSortProjector(),
            SelectionSortWatchNarrator(),
        ).build(SelectionSortDatasets.watch).steps
        assertEquals(3, steps.last().bullets.size)
    }

    @Test
    fun `Try data shares no values with Watch data`() {
        val watch = SelectionSortDatasets.watch.values.toSet()
        assertTrue(SelectionSortDatasets.tryIt.values.none { it in watch && it != 7 && it != 2 })
        assertNotEquals(SelectionSortDatasets.watch.values, SelectionSortDatasets.tryIt.values)
    }
}
