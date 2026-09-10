package com.ttele.algoking.engine

import com.ttele.algoking.engine.algorithms.countingsort.CountingSortAction
import com.ttele.algoking.engine.algorithms.countingsort.CountingSortAlgorithm
import com.ttele.algoking.engine.algorithms.countingsort.CountingSortProjector
import com.ttele.algoking.engine.algorithms.countingsort.CountingSortState
import com.ttele.algoking.engine.catalog.AlgorithmCatalog
import com.ttele.algoking.engine.core.AlgorithmId
import com.ttele.algoking.engine.core.AlgorithmRunner
import com.ttele.algoking.engine.core.Dataset
import com.ttele.algoking.engine.core.Probe
import com.ttele.algoking.engine.dataset.CountingSortDatasets
import com.ttele.algoking.engine.decision.DecisionKind
import com.ttele.algoking.engine.decision.DecisionValidation
import com.ttele.algoking.engine.decision.Validation
import com.ttele.algoking.engine.event.Outcome
import com.ttele.algoking.engine.event.VizEvent
import com.ttele.algoking.engine.narration.NarrationId
import com.ttele.algoking.engine.scene.CellState
import com.ttele.algoking.engine.scene.CountingScene
import com.ttele.algoking.engine.walkthrough.WatchStepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Counting Sort.
 *
 * The lesson's claim is that it sorts **without comparing anything**, so the
 * assertions are written against that: the output has to be right, the table has
 * to be right, and the run has to make zero comparisons while producing both.
 */
class CountingSortTest {

    private val algorithm = CountingSortAlgorithm()
    private val teaching = CountingSortDatasets.watch
    private val tryIt = CountingSortDatasets.tryIt

    private fun runner(values: List<Int>) =
        AlgorithmRunner(algorithm, Dataset(values = values, label = "test"))

    /** Drives a full run, always choosing correctly. */
    private fun solve(values: List<Int>): CountingSortState {
        val runner = runner(values)
        var guard = 0
        while (guard++ < 2_000) {
            when (val probe = runner.probe()) {
                is Probe.Mechanical -> runner.apply(probe.action)
                is Probe.Decide -> runner.apply(probe.decision.correct)
                is Probe.Terminal -> return runner.current.state
            }
        }
        error("Counting Sort did not terminate")
    }

    /** Every state a correct run passes through. */
    private fun trace(values: List<Int>): List<CountingSortState> {
        val runner = runner(values)
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
        error("Counting Sort did not terminate")
    }

    // ── The lesson's own datasets ────────────────────────────────────────────

    @Test
    fun `the teaching dataset counts and rebuilds exactly as documented`() {
        val end = solve(teaching.values)

        assertEquals(listOf(4, 2, 2, 8, 3, 3, 1), end.values)
        assertEquals(1, end.min)
        assertEquals(8, end.max)
        assertEquals(8, end.span)
        // 1:1  2:2  3:2  4:1  5:0  6:0  7:0  8:1
        assertEquals(listOf(1, 2, 2, 1, 0, 0, 0, 1), end.counts)
        assertEquals(listOf(1, 2, 2, 3, 3, 4, 8), end.placed)
    }

    @Test
    fun `the try dataset is a different shape, and still only the rule produces it`() {
        val end = solve(tryIt.values)

        assertEquals(listOf(3, 1, 4, 1, 5, 3), end.values)
        assertEquals(1, end.min)
        assertEquals(5, end.max)
        // 1:2  2:0  3:2  4:1  5:1 — the hole is at 2, not where WATCH's were.
        assertEquals(listOf(2, 0, 2, 1, 1), end.counts)
        assertEquals(listOf(1, 1, 3, 3, 4, 5), end.placed)
    }

    @Test
    fun `the input array is never reordered - counting sort does not move it`() {
        // Every other sort in the library rearranges the array in place. This one
        // reads it once and never touches it again, and the picture has to be able
        // to say so.
        for (state in trace(teaching.values)) {
            assertEquals(teaching.values, state.values)
        }
    }

    // ── Against a reference ──────────────────────────────────────────────────

    @Test
    fun `the output equals a plain sort, on every shape a sort has to survive`() {
        val cases = listOf(
            listOf(4, 2, 2, 8, 3, 3, 1), // the lesson
            listOf(3, 1, 4, 1, 5, 3), // TRY
            listOf(1, 2, 3, 4, 5), // already sorted
            listOf(5, 4, 3, 2, 1), // reversed
            listOf(7), // one element
            listOf(6, 6, 6, 6), // all equal
            listOf(0, 0, 1), // zero is a legal value
            listOf(2, 1), // the smallest interesting case
            listOf(9, 1, 9, 1, 5), // both extremes repeated
            listOf(0, 9), // the widest range in the lesson's constraints
        )
        for (values in cases) {
            val end = solve(values)
            assertEquals("sorted $values", values.sorted(), end.placed)
            assertTrue("finished $values", end.finished)
        }
    }

    @Test
    fun `the table always says what the input actually holds`() {
        val cases = listOf(
            listOf(4, 2, 2, 8, 3, 3, 1),
            listOf(3, 1, 4, 1, 5, 3),
            listOf(6, 6, 6, 6),
            listOf(0, 0, 1),
            listOf(9, 1, 9, 1, 5),
        )
        for (values in cases) {
            val end = solve(values)
            assertEquals("counts $values", end.trueCounts, end.counts)
            // The counts add up to the input, which is the invariant that makes
            // the rebuild produce the right number of elements.
            assertEquals("total $values", values.size, end.counts.sum())
        }
    }

    @Test
    fun `every value in the range gets a bucket, including the ones that never appear`() {
        val end = solve(teaching.values)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8), end.bucketValues)
        // 5, 6 and 7 are the half of the table a learner ignores, and the reason
        // `k` is a cost rather than a detail.
        assertEquals(0, end.countOf(5))
        assertEquals(0, end.countOf(6))
        assertEquals(0, end.countOf(7))
    }

    // ── It does not compare ──────────────────────────────────────────────────

    @Test
    fun `a complete run makes zero comparisons`() {
        // The whole lesson, as an assertion. Every other sort in the library
        // counts comparisons in the hundreds; this one must report none, and the
        // Complete screen prints that number.
        val runner = runner(teaching.values)
        val trace = runner.runToCompletion()
        assertEquals(0, trace.frames.last().metrics.comparisons)
        assertEquals(0, trace.frames.last().metrics.swaps)
        assertTrue(
            trace.frames.none { frame -> frame.events.any { it is VizEvent.Compare } },
        )
        assertTrue(
            trace.frames.none { frame -> frame.events.any { it is VizEvent.Swap } },
        )
    }

    @Test
    fun `the run ends Sorted, and only once everything is placed`() {
        val runner = runner(teaching.values)
        val trace = runner.runToCompletion()
        assertEquals(Outcome.Sorted, (runner.probe() as Probe.Terminal).outcome)
        assertTrue(
            trace.frames.last().events.any {
                it is VizEvent.Terminal && it.outcome == Outcome.Sorted
            },
        )
        assertEquals(teaching.values.size, trace.frames.last().state.placed.size)
    }

    // ── The decisions ────────────────────────────────────────────────────────

    @Test
    fun `both phases are the learner's, and both are a tap on the table`() {
        val runner = runner(teaching.values)
        var counting = 0
        var placing = 0
        var guard = 0

        while (guard++ < 2_000) {
            when (val probe = runner.probe()) {
                is Probe.Terminal -> break
                is Probe.Mechanical -> error("nothing here is bookkeeping")
                is Probe.Decide -> {
                    val decision = probe.decision
                    // One gesture for both questions: tap a bucket.
                    assertEquals(DecisionKind.CELL, decision.kind)
                    // Never auto-answered: both halves are the lesson.
                    assertFalse(decision.autoInTry)
                    // Every bucket is on the table, so nothing is pre-filtered.
                    assertEquals(8, decision.options.size)
                    assertEquals((0..7).toList(), decision.options.mapNotNull { it.slot })
                    assertTrue(decision.correct in decision.options.map { it.action })
                    when (decision.correct) {
                        is CountingSortAction.Count -> counting++
                        is CountingSortAction.Place -> placing++
                    }
                    runner.apply(decision.correct)
                }
            }
        }

        // One decision per value counted, one per value placed. Nothing else.
        assertEquals(7, counting)
        assertEquals(7, placing)
    }

    @Test
    fun `the counting question is answered by the bucket with the same number on it`() {
        val runner = runner(teaching.values)
        for (value in teaching.values) {
            val decision = (runner.probe() as Probe.Decide).decision
            assertEquals(CountingSortAction.Count(value), decision.correct)
            runner.apply(decision.correct)
        }
    }

    @Test
    fun `the rebuild always asks for the smallest value with anything left`() {
        val runner = runner(teaching.values)
        repeat(teaching.values.size) {
            runner.apply((runner.probe() as Probe.Decide).decision.correct)
        }
        // 1, 2, 2, 3, 3, 4, 8 — the buckets read in ascending order, twice over
        // where a bucket holds two.
        val asked = mutableListOf<Int>()
        repeat(teaching.values.size) {
            val decision = (runner.probe() as Probe.Decide).decision
            asked += (decision.correct as CountingSortAction.Place).value
            runner.apply(decision.correct)
        }
        assertEquals(listOf(1, 2, 2, 3, 3, 4, 8), asked)
    }

    @Test
    fun `every decision carries a full ladder and a reason for every wrong bucket`() {
        val runner = runner(teaching.values)
        var guard = 0
        while (guard++ < 2_000) {
            val probe = runner.probe()
            if (probe is Probe.Terminal) break
            val decision = (probe as Probe.Decide).decision

            assertEquals("three rungs", 3, decision.guidance.size)
            assertNotNull(decision.hint)
            assertNotNull(decision.correctFeedback)
            // Every wrong option can say why it is wrong — "Wrong" is forbidden
            // (PRODUCT_SPEC.md §5).
            for (option in decision.options) {
                if (option.action == decision.correct) continue
                assertNotNull(
                    "no reason for ${option.action}",
                    decision.whyWrong[option.action],
                )
            }
            runner.apply(decision.correct)
        }
    }

    @Test
    fun `the three wrong answers in the rebuild are the three real mistakes`() {
        val runner = runner(teaching.values)
        repeat(teaching.values.size) {
            runner.apply((runner.probe() as Probe.Decide).decision.correct)
        }
        // Place the 1, so bucket 1 is spent and bucket 2 is next.
        runner.apply((runner.probe() as Probe.Decide).decision.correct)

        val decision = (runner.probe() as Probe.Decide).decision
        assertEquals(CountingSortAction.Place(2), decision.correct)

        fun reason(value: Int) =
            decision.whyWrong[CountingSortAction.Place(value)]?.id

        // Counted nothing at all.
        assertEquals(NarrationId.CS_WHY_EMPTY_BUCKET, reason(5))
        // Had one, and it is already out.
        assertEquals(NarrationId.CS_WHY_BUCKET_SPENT, reason(1))
        // Has values left, but it is not next — the output is built smallest first.
        assertEquals(NarrationId.CS_WHY_OUT_OF_ORDER, reason(3))
        assertEquals(NarrationId.CS_WHY_OUT_OF_ORDER, reason(8))
    }

    // ── Wrong answers ────────────────────────────────────────────────────────

    @Test
    fun `a wrong bucket in the counting phase changes nothing at all`() {
        val runner = runner(teaching.values)
        val before = runner.current.state
        val decision = (runner.probe() as Probe.Decide).decision

        // Five wrong taps in a row.
        var attempts = 0
        for (wrong in listOf(1, 2, 3, 5, 8)) {
            val verdict = DecisionValidation.validate(
                decision,
                CountingSortAction.Count(wrong),
                attempts++,
            )
            assertTrue("bucket $wrong should be refused", verdict is Validation.Retry)
            // A Retry carries no action, so there is nothing the caller could
            // apply — the invariant, not a convention (ADR-021).
            assertEquals(before, runner.current.state)
        }

        // ...and the run still completes correctly afterwards.
        val end = solve(teaching.values)
        assertEquals(listOf(1, 2, 2, 3, 3, 4, 8), end.placed)
    }

    @Test
    fun `a wrong value in the rebuild changes nothing at all`() {
        val runner = runner(teaching.values)
        repeat(teaching.values.size) {
            runner.apply((runner.probe() as Probe.Decide).decision.correct)
        }
        val before = runner.current.state
        val decision = (runner.probe() as Probe.Decide).decision

        // An empty bucket, a bucket out of order, and the largest value.
        for ((attempt, wrong) in listOf(5, 3, 8).withIndex()) {
            val verdict =
                DecisionValidation.validate(decision, CountingSortAction.Place(wrong), attempt)
            assertTrue("value $wrong should be refused", verdict is Validation.Retry)
            assertEquals(before, runner.current.state)
        }
    }

    @Test
    fun `guidance escalates and then holds, so a learner is never dead-ended`() {
        val decision = (runner(teaching.values).probe() as Probe.Decide).decision
        val wrong = CountingSortAction.Count(7)

        val levels = (0..5).map { attempt ->
            (DecisionValidation.validate(decision, wrong, attempt) as Validation.Retry).level
        }
        assertEquals(listOf(1, 2, 3, 4, 5, 6), levels)

        // The most explicit rung repeats rather than running out.
        val last = (0..5).map { attempt ->
            (DecisionValidation.validate(decision, wrong, attempt) as Validation.Retry).guidance.id
        }
        assertEquals(NarrationId.CS_RETRY_COUNT_EXPLAIN, last[2])
        assertEquals(NarrationId.CS_RETRY_COUNT_EXPLAIN, last[5])
    }

    @Test
    fun `an action that is not legal from this state is refused, not obeyed`() {
        val runner = runner(teaching.values)
        val before = runner.current.state

        // Placing before anything has been counted.
        val transition = algorithm.apply(before, CountingSortAction.Place(1))
        assertEquals(before, transition.next)
        assertFalse(transition.correct)

        // Counting into a bucket outside the range.
        val outside = algorithm.apply(before, CountingSortAction.Count(99))
        assertEquals(before, outside.next)
        assertFalse(outside.correct)
    }

    @Test
    fun `the run terminates however badly it is driven`() {
        // Adversarial: every bucket applied at every beat, correct or not. The
        // machine must still stop (ARCHITECTURE.md §11).
        for (seed in 0..40) {
            val runner = runner(teaching.values)
            var guard = 0
            while (guard++ < 500) {
                when (val probe = runner.probe()) {
                    is Probe.Terminal -> break
                    is Probe.Mechanical -> runner.apply(probe.action)
                    is Probe.Decide -> {
                        val options = probe.decision.options
                        runner.apply(options[(seed + guard) % options.size].action)
                    }
                }
            }
            assertTrue("seed $seed did not terminate", guard < 500)
        }
    }

    // ── Edge cases ───────────────────────────────────────────────────────────

    @Test
    fun `an empty array is a finished lesson, not a crash`() {
        val runner = runner(emptyList())
        assertEquals(Outcome.Sorted, (runner.probe() as Probe.Terminal).outcome)
        assertTrue(runner.current.state.finished)
        assertEquals(0, runner.current.state.span)
    }

    @Test
    fun `a single element is counted once and placed once`() {
        val end = solve(listOf(7))
        assertEquals(listOf(1), end.counts)
        assertEquals(listOf(7), end.placed)
        assertEquals(1, end.span)
    }

    @Test
    fun `all values equal put everything in one bucket and take it all back out`() {
        val end = solve(listOf(6, 6, 6, 6))
        assertEquals(1, end.span)
        assertEquals(listOf(4), end.counts)
        assertEquals(listOf(6, 6, 6, 6), end.placed)
    }

    @Test
    fun `zero is an ordinary value`() {
        val end = solve(listOf(0, 2, 0))
        assertEquals(0, end.min)
        assertEquals(listOf(2, 0, 1), end.counts)
        assertEquals(listOf(0, 0, 2), end.placed)
    }

    // ── The picture ──────────────────────────────────────────────────────────

    @Test
    fun `the scene shows three rows, and the answer is holes until it is filled`() {
        val projector = CountingSortProjector()
        val runner = runner(teaching.values)
        val opening = projector.project(runner.current.state, emptyList()) as CountingScene

        assertEquals(7, opening.input.size)
        assertEquals(8, opening.buckets.size)
        assertEquals(7, opening.output.size)
        // Nothing placed yet: every output slot is a hole, never a zero claiming
        // a value exists.
        assertTrue(opening.output.all { it.state == CellState.GHOST })
        // Each bucket knows the value it counts, which is what the learner taps.
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8), opening.buckets.map { it.value })
        assertTrue(opening.buckets.all { it.count == 0 })
        // Before the first action there is no tally: a strip reading
        // `count[4]: 0 → ?` would answer the question in the act of posing it.
        assertNull(opening.tally)
    }

    @Test
    fun `the tally reports the change that just happened, in both directions`() {
        val projector = CountingSortProjector()
        val runner = runner(teaching.values)

        // Count the first 4.
        runner.apply((runner.probe() as Probe.Decide).decision.correct)
        val counted = projector.project(runner.current.state, emptyList()) as CountingScene
        assertEquals(4, counted.tally?.value)
        assertEquals(0, counted.tally?.from)
        assertEquals(1, counted.tally?.to)

        // Finish counting, then place the first value.
        repeat(teaching.values.size - 1) {
            runner.apply((runner.probe() as Probe.Decide).decision.correct)
        }
        runner.apply((runner.probe() as Probe.Decide).decision.correct)
        val placed = projector.project(runner.current.state, emptyList()) as CountingScene
        // Bucket 1 held one, and has just given it up.
        assertEquals(1, placed.tally?.value)
        assertEquals(1, placed.tally?.from)
        assertEquals(0, placed.tally?.to)
    }

    @Test
    fun `a bucket that counted nothing is drawn as ruled out, not as untouched`() {
        val projector = CountingSortProjector()
        val end = solve(teaching.values)
        val scene = projector.project(end, emptyList()) as CountingScene

        val empty = scene.buckets.filter { it.value in listOf(5, 6, 7) }
        assertTrue(empty.all { it.state == CellState.ELIMINATED })
        assertTrue(empty.all { it.count == 0 })
        // And everything that did hold values has given all of them up.
        assertTrue(
            scene.buckets.filter { it.count > 0 }.all { it.state == CellState.FINALIZED },
        )
    }

    // ── The walkthrough ──────────────────────────────────────────────────────

    private fun watchSteps() = AlgorithmCatalog.countingSort().watchScript().steps

    @Test
    fun `the walkthrough opens on the range and the table, before anything is counted`() {
        val kinds = watchSteps().map { it.kind }
        assertEquals(WatchStepKind.SETUP, kinds.first())
        val ids = watchSteps().map { it.headline.id }
        assertEquals(NarrationId.CS_WATCH_SETUP, ids[0])
        // Find the range, then build the table for it — in that order, because the
        // table is the size of the range it just found.
        assertEquals(NarrationId.CS_WATCH_RANGE, ids[1])
        assertEquals(NarrationId.CS_WATCH_TABLE, ids[2])
    }

    @Test
    fun `the walkthrough shows a bucket going from one to two`() {
        // The beat that makes a count a count rather than a flag. If the collapse
        // ever swallows it, the lesson stops teaching the thing it is for.
        val counts = watchSteps().filter { it.headline.id == NarrationId.CS_WATCH_COUNT }
        assertTrue("only ${counts.size} counting beats", counts.size >= 4)
        val repeated = counts.any { it.headline.args.getOrNull(1) == 1 }
        assertTrue("no bucket is shown going 1 -> 2", repeated)
        // ...and the first one it shows is a bucket taking its first value.
        assertEquals(0, counts.first().headline.args[1])
    }

    @Test
    fun `the walkthrough ends on the insight and then the recap`() {
        val steps = watchSteps()
        assertEquals(WatchStepKind.SUMMARY, steps.last().kind)
        assertEquals(WatchStepKind.INSIGHT, steps[steps.lastIndex - 1].kind)
        // The insight prints the comparison count the run actually produced.
        assertEquals(0, steps[steps.lastIndex - 1].headline.args[0])
        assertEquals(4, steps.last().bullets.size)
    }

    @Test
    fun `every watch step changes something visible - ADR-020`() {
        watchSteps().zipWithNext { a, b ->
            val changed = a.scene != b.scene ||
                a.headline != b.headline ||
                a.support != b.support ||
                a.bullets != b.bullets
            assertTrue("steps ${a.index} and ${b.index} are identical", changed)
        }
    }

    @Test
    fun `the walkthrough is exactly the beats the lesson was designed as`() {
        // Pinned, so a change to the collapse rule has to be a decision rather
        // than a surprise — and so the shape of the teaching run is reviewable
        // here rather than only on a device.
        assertEquals(
            listOf(
                WatchStepKind.SETUP to NarrationId.CS_WATCH_SETUP,
                WatchStepKind.EXAMINE to NarrationId.CS_WATCH_RANGE, // 1 .. 8
                WatchStepKind.EXAMINE to NarrationId.CS_WATCH_TABLE, // 8 buckets
                WatchStepKind.ADD to NarrationId.CS_WATCH_COUNT, // 4: 0 -> 1
                WatchStepKind.ADD to NarrationId.CS_WATCH_COUNT, // 2: 0 -> 1
                WatchStepKind.ADD to NarrationId.CS_WATCH_COUNT, // 2: 1 -> 2, the beat
                WatchStepKind.ADD to NarrationId.CS_WATCH_COUNT, // 8: 0 -> 1
                WatchStepKind.ADD to NarrationId.CS_WATCH_COUNT_REST, // 3, 3, 1
                WatchStepKind.PASS_COMPLETE to NarrationId.CS_WATCH_COUNTED,
                WatchStepKind.REMOVE to NarrationId.CS_WATCH_PLACE, // the 1
                WatchStepKind.REMOVE to NarrationId.CS_WATCH_PLACE, // a 2
                WatchStepKind.REMOVE to NarrationId.CS_WATCH_PLACE, // the other 2
                WatchStepKind.REMOVE to NarrationId.CS_WATCH_PLACE_REST,
                WatchStepKind.SORTED to NarrationId.CS_WATCH_DONE,
                WatchStepKind.INSIGHT to NarrationId.CS_WATCH_INSIGHT,
                WatchStepKind.SUMMARY to NarrationId.CS_WATCH_SUMMARY,
            ),
            watchSteps().map { it.kind to it.headline.id },
        )
    }

    @Test
    fun `the walkthrough is long enough to teach and short enough to finish`() {
        val steps = watchSteps()
        // Fourteen actions collapse to a walkthrough that can be tapped through in
        // a minute: the counting shows its shape and stops, and so does the
        // rebuild (ADR-025's rule).
        assertTrue("${steps.size} steps", steps.size in 12..20)
        assertTrue(steps.all { it.prediction == null })
    }

    // ── Wiring ───────────────────────────────────────────────────────────────

    @Test
    fun `the lesson is wired into the catalogue with both stages`() {
        val pack = AlgorithmCatalog.byId(AlgorithmId.COUNTING_SORT)
        assertEquals(AlgorithmId.COUNTING_SORT, pack.id)
        assertEquals("Counting Sort", pack.displayName)
        assertEquals(CountingSortDatasets.watch.values, pack.watchDataset.values)
        assertEquals(CountingSortDatasets.tryIt.values, pack.tryDataset.values)
        // WATCH and TRY are different data: TRY is application, not recall.
        assertTrue(pack.watchDataset.values != pack.tryDataset.values)
    }
}
