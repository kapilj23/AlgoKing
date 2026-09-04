package com.ttele.algoking.engine

import com.ttele.algoking.engine.algorithms.insertionsort.InsertionSortAction
import com.ttele.algoking.engine.algorithms.insertionsort.InsertionSortAlgorithm
import com.ttele.algoking.engine.algorithms.insertionsort.InsertionSortProjector
import com.ttele.algoking.engine.algorithms.insertionsort.InsertionSortState
import com.ttele.algoking.engine.algorithms.insertionsort.InsertionSortWatchNarrator
import com.ttele.algoking.engine.core.AlgorithmRunner
import com.ttele.algoking.engine.core.Dataset
import com.ttele.algoking.engine.core.Probe
import com.ttele.algoking.engine.dataset.InsertionSortDatasets
import com.ttele.algoking.engine.decision.DecisionKind
import com.ttele.algoking.engine.decision.DecisionValidation
import com.ttele.algoking.engine.decision.Validation
import com.ttele.algoking.engine.event.Outcome
import com.ttele.algoking.engine.event.VizEvent
import com.ttele.algoking.engine.scene.CellState
import com.ttele.algoking.engine.walkthrough.WatchScriptBuilder
import com.ttele.algoking.engine.walkthrough.WatchStepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InsertionSortTest {

    private fun runner(dataset: Dataset) = AlgorithmRunner(InsertionSortAlgorithm(), dataset)

    private fun playPerfectly(
        dataset: Dataset,
    ): AlgorithmRunner<InsertionSortState, InsertionSortAction> {
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

    /** Drives the runner until the next learner decision. */
    private fun toDecision(
        r: AlgorithmRunner<InsertionSortState, InsertionSortAction>,
    ): Probe.Decide<InsertionSortAction> {
        var guard = 0
        while (guard++ < 64) {
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
            listOf(7, 3, 8, 2, 5),
            listOf(6, 9, 2, 7, 4),
            listOf(5, 4, 3, 2, 1),
            listOf(1, 2, 3, 4, 5),
            listOf(3, 3, 1, 2),
            listOf(2, 1),
            listOf(9),
        ).forEach { values ->
            val r = playPerfectly(Dataset(values))
            assertEquals("failed on $values", values.sorted(), r.current.state.values)
            assertEquals(Outcome.Sorted, (r.probe() as Probe.Terminal).outcome)
        }
    }

    @Test
    fun `the left side is sorted at every single step`() {
        val r = runner(InsertionSortDatasets.watch)
        var guard = 0
        while (guard++ < 512) {
            val s = r.current.state
            // The prefix is only guaranteed while no key is lifted out of it.
            if (!s.holding) {
                val prefix = s.values.take(s.sortedTo)
                assertEquals("prefix $prefix was not sorted", prefix.sorted(), prefix)
            }
            when (val probe = r.probe()) {
                is Probe.Mechanical -> r.apply(probe.action)
                is Probe.Decide -> r.apply(probe.decision.correct)
                is Probe.Terminal -> break
            }
        }
    }

    @Test
    fun `the sorted portion grows by exactly one per insertion`() {
        val r = runner(InsertionSortDatasets.watch)
        var guard = 0
        var insertions = 0
        while (guard++ < 512) {
            when (val probe = r.probe()) {
                is Probe.Mechanical -> r.apply(probe.action)
                is Probe.Terminal -> break
                is Probe.Decide -> {
                    val before = r.current.state.sortedTo
                    r.apply(probe.decision.correct)
                    val after = r.current.state.sortedTo
                    if (after != before) {
                        assertEquals(before + 1, after)
                        insertions++
                    }
                }
            }
        }
        assertEquals(InsertionSortDatasets.watch.values.size - 1, insertions)
    }

    // ── A shift is not a swap ────────────────────────────────────────────────

    @Test
    fun `a shift moves one value right and never exchanges two`() {
        // 7 3 8 2 5 — key 3, compared against 7.
        val r = runner(InsertionSortDatasets.watch)
        toDecision(r)
        val before = r.current.state
        assertEquals(3, before.key)
        assertEquals(7, before.comparedValue)

        val frame = r.apply(InsertionSortAction.Shift)

        // 7 moved right into the gap; the key is still held outside the array.
        assertEquals(listOf(7, 7, 8, 2, 5), frame.state.values)
        assertEquals(3, frame.state.key)
        assertEquals(0, frame.state.holeAt)
        // No Swap event exists to draw an exchange arc from.
        assertTrue(frame.events.none { it is VizEvent.Swap })
        assertTrue(frame.events.any { it is VizEvent.Insert })
        assertTrue(frame.events.any { it is VizEvent.Remove })
    }

    @Test
    fun `no swap event is ever emitted by the whole algorithm`() {
        val r = runner(InsertionSortDatasets.watch)
        var guard = 0
        while (guard++ < 512) {
            when (val probe = r.probe()) {
                is Probe.Mechanical -> r.apply(probe.action)
                is Probe.Decide -> r.apply(probe.decision.correct)
                is Probe.Terminal -> break
            }
            assertTrue(
                "Insertion Sort must never draw an exchange",
                r.current.events.none { it is VizEvent.Swap },
            )
        }
    }

    @Test
    fun `the gap travels left until the key fits`() {
        // Key 2 in 3 7 8 | 2 5 has to walk all the way to the front.
        val r = runner(InsertionSortDatasets.watch)
        var guard = 0
        val holes = mutableListOf<Int>()
        while (guard++ < 512) {
            when (val probe = r.probe()) {
                is Probe.Mechanical -> r.apply(probe.action)
                is Probe.Terminal -> break
                is Probe.Decide -> {
                    r.apply(probe.decision.correct)
                    r.current.state.holeAt?.let { holes += it }
                }
            }
        }
        // Somewhere in the run the gap reaches index 0 — that is the "insert at the
        // front" case, and it is the one learners find surprising.
        assertTrue("the gap never reached the front", holes.contains(0))
    }

    // ── The decision ─────────────────────────────────────────────────────────

    @Test
    fun `the decision is shift or insert, judged against the held key`() {
        val r = runner(InsertionSortDatasets.watch)
        val d = toDecision(r).decision

        assertEquals(DecisionKind.OPTIONS, d.kind)
        assertEquals(
            setOf(InsertionSortAction.Shift, InsertionSortAction.Insert),
            d.options.map { it.action }.toSet(),
        )
        // 7 > key 3, so it must move right.
        assertEquals(InsertionSortAction.Shift, d.correct)
    }

    @Test
    fun `a key already in place is inserted without a single shift`() {
        // 3 7 | 8 2 5 — the key 8 is larger than 7, so nothing moves.
        val r = runner(InsertionSortDatasets.watch)
        var guard = 0
        while (guard++ < 64 && r.current.state.sortedTo < 2) {
            when (val probe = r.probe()) {
                is Probe.Mechanical -> r.apply(probe.action)
                is Probe.Decide -> r.apply(probe.decision.correct)
                is Probe.Terminal -> break
            }
        }
        val d = toDecision(r).decision
        assertEquals(8, r.current.state.key)
        assertEquals(InsertionSortAction.Insert, d.correct)
    }

    @Test
    fun `a wrong decision is refused and the array does not move`() {
        val r = runner(InsertionSortDatasets.watch)
        val d = toDecision(r).decision
        val before = r.current.state

        repeat(4) { attempt ->
            val verdict = DecisionValidation.validate(d, InsertionSortAction.Insert, attempt)
            assertTrue("attempt $attempt was accepted", verdict is Validation.Retry)
        }
        assertEquals(before, r.current.state)
        assertEquals(listOf(7, 3, 8, 2, 5), r.current.state.values)
    }

    @Test
    fun `guidance escalates and Challenge gets a terser clue`() {
        val r = runner(InsertionSortDatasets.watch)
        val d = toDecision(r).decision
        assertEquals(3, d.guidance.size)
        assertEquals(3, d.guidance.distinct().size)
        assertNotEquals(d.minimalFeedback, d.guidance.last())
        assertEquals(3, d.hintLadder.size)
    }

    @Test
    fun `the prompt sheds guidance as the learner settles in`() {
        val r = runner(Dataset(listOf(9, 7, 5, 3, 1)))
        val first = toDecision(r).decision.prompt
        var guard = 0
        while (guard++ < 64 && r.current.state.pass < 4) {
            when (val probe = r.probe()) {
                is Probe.Mechanical -> r.apply(probe.action)
                is Probe.Decide -> r.apply(probe.decision.correct)
                is Probe.Terminal -> break
            }
        }
        assertNotEquals(first, toDecision(r).decision.prompt)
    }

    // ── The visual story is its own ──────────────────────────────────────────

    @Test
    fun `the projector shows a gap, a compared value and the key held outside`() {
        val r = runner(InsertionSortDatasets.watch)
        toDecision(r)
        val scene = InsertionSortProjector().project(r.current.state, r.current.events)

        val states = scene.cells.map { it.state }
        assertTrue("no gap", states.contains(CellState.GHOST))
        assertTrue("nothing being compared", states.contains(CellState.COMPARING))
        assertNotNull("the key is not surfaced", scene.badge)
        assertEquals(3, scene.badge!!.value)
        // An arc would read as an exchange, and nothing exchanges here.
        assertNull(scene.arc)
    }

    @Test
    fun `the finished array renders as final with no gap`() {
        val r = playPerfectly(InsertionSortDatasets.watch)
        val scene = InsertionSortProjector().project(r.current.state, r.current.events)
        assertTrue(scene.cells.all { it.state == CellState.FINALIZED })
        assertNull(scene.badge)
    }

    // ── Watch walkthrough ────────────────────────────────────────────────────

    @Test
    fun `the walkthrough shows a shift, a no-shift, and stays short`() {
        val steps = WatchScriptBuilder(
            InsertionSortAlgorithm(),
            InsertionSortProjector(),
            InsertionSortWatchNarrator(),
        ).build(InsertionSortDatasets.watch).steps

        assertEquals(WatchStepKind.SETUP, steps.first().kind)
        assertEquals(WatchStepKind.SUMMARY, steps.last().kind)
        assertTrue("no key-taken beat", steps.any { it.kind == WatchStepKind.EXAMINE })
        assertTrue("no shift beat", steps.any { it.kind == WatchStepKind.SWAP })
        assertTrue("no insertion beat", steps.any { it.kind == WatchStepKind.PASS_COMPLETE })
        assertTrue(steps.any { it.kind == WatchStepKind.INSIGHT })
        assertTrue("walkthrough is ${steps.size} steps", steps.size in 10..24)
    }

    @Test
    fun `the walkthrough carries exactly one unscored checkpoint`() {
        val steps = WatchScriptBuilder(
            InsertionSortAlgorithm(),
            InsertionSortProjector(),
            InsertionSortWatchNarrator(),
        ).build(InsertionSortDatasets.watch).steps

        val predictions = steps.filter { it.prediction != null }
        assertEquals(1, predictions.size)
        val p = predictions.single().prediction!!
        assertEquals(2, p.options.size)
        assertTrue(p.correctIndex in 0..1)
    }

    @Test
    fun `the recap states the three-line mental model`() {
        val steps = WatchScriptBuilder(
            InsertionSortAlgorithm(),
            InsertionSortProjector(),
            InsertionSortWatchNarrator(),
        ).build(InsertionSortDatasets.watch).steps
        assertEquals(3, steps.last().bullets.size)
    }

    @Test
    fun `Try data is not the Watch data`() {
        assertNotEquals(
            InsertionSortDatasets.watch.values,
            InsertionSortDatasets.tryIt.values,
        )
        assertFalse(InsertionSortDatasets.tryIt.values.isEmpty())
    }
}
