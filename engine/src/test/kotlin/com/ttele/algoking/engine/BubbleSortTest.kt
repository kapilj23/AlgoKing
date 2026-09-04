package com.ttele.algoking.engine

import com.ttele.algoking.engine.algorithms.bubblesort.BubbleSortAction
import com.ttele.algoking.engine.algorithms.bubblesort.BubbleSortAlgorithm
import com.ttele.algoking.engine.algorithms.bubblesort.BubbleSortProjector
import com.ttele.algoking.engine.algorithms.bubblesort.BubbleSortState
import com.ttele.algoking.engine.algorithms.bubblesort.BubbleSortWatchNarrator
import com.ttele.algoking.engine.challenge.ChallengeGenerator
import com.ttele.algoking.engine.challenge.ChallengeType
import com.ttele.algoking.engine.core.AlgorithmRunner
import com.ttele.algoking.engine.core.Dataset
import com.ttele.algoking.engine.core.Probe
import com.ttele.algoking.engine.dataset.BubbleSortDatasets
import com.ttele.algoking.engine.decision.DecisionValidation
import com.ttele.algoking.engine.decision.Validation
import com.ttele.algoking.engine.event.Outcome
import com.ttele.algoking.engine.event.VizEvent
import com.ttele.algoking.engine.scene.CellState
import com.ttele.algoking.engine.walkthrough.WatchScriptBuilder
import com.ttele.algoking.engine.walkthrough.WatchStepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleSortTest {

    private fun runner(dataset: Dataset) = AlgorithmRunner(BubbleSortAlgorithm(), dataset)

    private fun playPerfectly(dataset: Dataset): AlgorithmRunner<BubbleSortState, BubbleSortAction> {
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
            listOf(6, 2, 9, 1, 4),
            listOf(5, 4, 3, 2, 1),
            listOf(1, 2, 3, 4, 5),
            listOf(2, 2, 1, 3),
            listOf(9),
        ).forEach { values ->
            val r = playPerfectly(Dataset(values))
            assertEquals(values.sorted(), r.current.state.values)
            assertEquals(Outcome.Sorted, (r.probe() as Probe.Terminal).outcome)
        }
    }

    @Test
    fun `the learner is never asked which pair to compare`() {
        val r = runner(BubbleSortDatasets.watch)
        var decisions = 0
        var guard = 0
        while (guard++ < 512) {
            when (val probe = r.probe()) {
                // Pair advancement and pass boundaries are the app's job.
                is Probe.Mechanical -> r.apply(probe.action)
                is Probe.Decide -> {
                    decisions++
                    // Every decision offered is exactly SWAP or KEEP.
                    assertEquals(
                        setOf(BubbleSortAction.Swap, BubbleSortAction.Keep),
                        probe.decision.options.map { it.action }.toSet(),
                    )
                    r.apply(probe.decision.correct)
                }

                is Probe.Terminal -> break
            }
        }
        assertTrue("no decisions were offered", decisions > 0)
    }

    @Test
    fun `a pass with no swaps ends the sort early`() {
        val r = playPerfectly(Dataset(listOf(1, 2, 3, 4, 5)))
        // Five sorted values: one pass of four comparisons proves it, and stops.
        assertEquals(4, r.current.metrics.comparisons)
        assertEquals(0, r.current.metrics.swaps)
        assertEquals(1, r.current.state.pass - 1)
    }

    @Test
    fun `each pass finalises exactly one more value from the right`() {
        val r = runner(BubbleSortDatasets.watch)
        val finalized = mutableListOf<Int>()
        var guard = 0
        while (guard++ < 512) {
            when (val probe = r.probe()) {
                is Probe.Mechanical -> {
                    val frame = r.apply(probe.action)
                    frame.events.filterIsInstance<VizEvent.Finalize>().forEach {
                        finalized += it.range.last
                    }
                }

                is Probe.Decide -> r.apply(probe.decision.correct)
                is Probe.Terminal -> break
            }
        }
        // The suffix locks in from the right-hand end inwards.
        assertEquals(listOf(4, 3, 2), finalized.take(3))
    }

    @Test
    fun `the projector marks the pair comparing and the settled suffix final`() {
        val r = runner(BubbleSortDatasets.watch)
        r.apply(BubbleSortAction.FocusPair)
        val scene = BubbleSortProjector().project(r.current.state, r.current.events)

        assertEquals(CellState.COMPARING, scene.cells[0].state)
        assertEquals(CellState.COMPARING, scene.cells[1].state)
        assertEquals(CellState.IDLE, scene.cells[2].state)
    }

    @Test
    fun `a swap emits an arc the renderer can animate`() {
        val r = runner(BubbleSortDatasets.watch)
        r.apply(BubbleSortAction.FocusPair)
        val frame = r.apply(BubbleSortAction.Swap)
        val scene = BubbleSortProjector().project(frame.state, frame.events)
        assertNotNull(scene.arc)
        assertEquals(listOf(3, 7, 8, 2, 5), frame.state.values)
    }

    // ── The decision, and refusing a wrong one ────────────────────────────────

    @Test
    fun `SWAP is correct when the left value is larger, KEEP when it is not`() {
        val r = runner(Dataset(listOf(7, 3, 8)))
        r.apply(BubbleSortAction.FocusPair)
        assertEquals(
            BubbleSortAction.Swap,
            (r.probe() as Probe.Decide).decision.correct,
        )

        r.apply(BubbleSortAction.Swap)   // 3 7 8
        r.apply(BubbleSortAction.FocusPair)
        assertEquals(
            BubbleSortAction.Keep,
            (r.probe() as Probe.Decide).decision.correct,
        )
    }

    @Test
    fun `a wrong decision is refused and the array does not move`() {
        val r = runner(BubbleSortDatasets.watch)
        r.apply(BubbleSortAction.FocusPair)
        val before = r.current.state
        val decision = (r.probe() as Probe.Decide).decision

        repeat(4) { attempt ->
            val verdict = DecisionValidation.validate(decision, BubbleSortAction.Keep, attempt)
            assertTrue("attempt $attempt was accepted", verdict is Validation.Retry)
        }
        assertEquals(before, r.current.state)
        assertEquals(listOf(7, 3, 8, 2, 5), r.current.state.values)
    }

    @Test
    fun `guidance escalates then holds, and Challenge gets a terser clue`() {
        val r = runner(BubbleSortDatasets.watch)
        r.apply(BubbleSortAction.FocusPair)
        val d = (r.probe() as Probe.Decide).decision

        assertEquals(3, d.guidance.size)
        assertTrue(d.guidance.distinct().size == 3)
        // Challenge's one-line clue is not the Try explanation.
        assertTrue(d.minimalFeedback != d.guidance.last())
        assertEquals(3, d.hintLadder.size)
    }

    @Test
    fun `the prompt sheds guidance as the learner settles in`() {
        val r = runner(Dataset(listOf(9, 7, 5, 3, 1)))
        r.apply(BubbleSortAction.FocusPair)
        val first = (r.probe() as Probe.Decide).decision.prompt
        r.apply(BubbleSortAction.Swap)
        r.apply(BubbleSortAction.FocusPair)
        r.apply(BubbleSortAction.Swap)
        r.apply(BubbleSortAction.FocusPair)
        val later = (r.probe() as Probe.Decide).decision.prompt
        assertTrue("the prompt never changed", first != later)
    }

    // ── Watch walkthrough ─────────────────────────────────────────────────────

    @Test
    fun `the walkthrough shows pass one in full and condenses the rest`() {
        val steps = WatchScriptBuilder(
            BubbleSortAlgorithm(),
            BubbleSortProjector(),
            BubbleSortWatchNarrator(),
        ).build(BubbleSortDatasets.watch).steps

        assertEquals(WatchStepKind.SETUP, steps.first().kind)
        assertEquals(WatchStepKind.SUMMARY, steps.last().kind)
        assertTrue(steps.any { it.kind == WatchStepKind.SWAP })
        assertTrue("no KEEP beat — the learner never sees 'do not swap'", steps.any { it.kind == WatchStepKind.KEEP })
        assertTrue(steps.any { it.kind == WatchStepKind.PASS_COMPLETE })
        assertTrue(steps.any { it.kind == WatchStepKind.INSIGHT })

        // Pass 1 of a 5-value array is 4 comparisons; a fully detailed sort would
        // be far longer. The walkthrough must stay short enough to finish.
        assertTrue("walkthrough is ${steps.size} steps — too long", steps.size <= 22)
        assertTrue("walkthrough is ${steps.size} steps — too thin", steps.size >= 12)
    }

    @Test
    fun `the walkthrough carries exactly one unscored checkpoint`() {
        val steps = WatchScriptBuilder(
            BubbleSortAlgorithm(),
            BubbleSortProjector(),
            BubbleSortWatchNarrator(),
        ).build(BubbleSortDatasets.watch).steps

        val predictions = steps.filter { it.prediction != null }
        assertEquals(1, predictions.size)
        val p = predictions.single().prediction!!
        assertEquals(2, p.options.size)
        assertTrue(p.correctIndex in 0..1)
        // It sits before the recap, so it prepares the learner for Try.
        assertTrue(predictions.single().index < steps.lastIndex - 1)
    }

    @Test
    fun `the recap states the three-line mental model`() {
        val steps = WatchScriptBuilder(
            BubbleSortAlgorithm(),
            BubbleSortProjector(),
            BubbleSortWatchNarrator(),
        ).build(BubbleSortDatasets.watch).steps
        assertEquals(3, steps.last().bullets.size)
    }

    // ── Challenge data ────────────────────────────────────────────────────────

    @Test
    fun `challenge arrays are fresh, varied, and solvable`() {
        val lesson = (BubbleSortDatasets.watch.values + BubbleSortDatasets.tryIt.values)
        val shapes = mutableSetOf<String>()
        repeat(15) { i ->
            val c = ChallengeGenerator.bubbleForRound(round = i + 1, seed = i.toLong())
            shapes += c.dataset.label
            assertTrue("empty challenge", c.dataset.values.isNotEmpty())
            assertFalse("reused the lesson array", c.dataset.values == lesson)
            // Every generated array actually sorts.
            val r = playPerfectly(c.dataset)
            assertEquals(c.dataset.values.sorted(), r.current.state.values)
        }
        assertTrue("only ${shapes.size} shapes in 15 rounds", shapes.size >= 3)
    }

    @Test
    fun `an already-sorted challenge is typed EARLY_EXIT and ends in one pass`() {
        val early = (1..20)
            .map { ChallengeGenerator.bubbleForRound(it, it.toLong()) }
            .firstOrNull { it.type == ChallengeType.EARLY_EXIT }
        assertNotNull("no EARLY_EXIT challenge in 20 rounds", early)

        val r = playPerfectly(early!!.dataset)
        assertEquals(0, r.current.metrics.swaps)
    }

    @Test
    fun `the same seed and round reproduce the same challenge`() {
        repeat(6) { i ->
            val a = ChallengeGenerator.bubbleForRound(i + 1, 99L + i)
            val b = ChallengeGenerator.bubbleForRound(i + 1, 99L + i)
            assertEquals(a.dataset.values, b.dataset.values)
            assertEquals(a.type, b.type)
        }
    }
}
