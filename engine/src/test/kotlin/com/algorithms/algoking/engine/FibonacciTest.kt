package com.algorithms.algoking.engine

import com.algorithms.algoking.engine.algorithms.fibonacci.FibonacciAction
import com.algorithms.algoking.engine.algorithms.fibonacci.FibonacciAlgorithm
import com.algorithms.algoking.engine.algorithms.fibonacci.FibonacciProjector
import com.algorithms.algoking.engine.algorithms.fibonacci.FibonacciState
import com.algorithms.algoking.engine.algorithms.fibonacci.fibonacciTable
import com.algorithms.algoking.engine.algorithms.fibonacci.naiveCallCount
import com.algorithms.algoking.engine.algorithms.fibonacci.naiveCallsTo
import com.algorithms.algoking.engine.catalog.AlgorithmCatalog
import com.algorithms.algoking.engine.challenge.ChallengeCatalog
import com.algorithms.algoking.engine.core.AlgorithmId
import com.algorithms.algoking.engine.core.AlgorithmRunner
import com.algorithms.algoking.engine.core.Dataset
import com.algorithms.algoking.engine.core.Probe
import com.algorithms.algoking.engine.dataset.FibonacciDatasets
import com.algorithms.algoking.engine.decision.DecisionKind
import com.algorithms.algoking.engine.decision.DecisionValidation
import com.algorithms.algoking.engine.decision.Validation
import com.algorithms.algoking.engine.event.Outcome
import com.algorithms.algoking.engine.event.VizEvent
import com.algorithms.algoking.engine.progress.AlgorithmProgress
import com.algorithms.algoking.engine.progress.Stage
import com.algorithms.algoking.engine.scene.CellState
import com.algorithms.algoking.engine.scene.PrefixOp
import com.algorithms.algoking.engine.scene.SceneLayout
import com.algorithms.algoking.engine.scene.SequenceScene
import com.algorithms.algoking.engine.walkthrough.WatchStepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fibonacci by tabulation — the second dynamic-programming lesson.
 *
 * The table, the transitions, the two source indices, the refusals and the
 * walkthrough. Every Fibonacci number asserted here is checked against a
 * **separately written recursive definition** rather than against the engine's own
 * table, so a bug in the table cannot make its own test pass.
 */
class FibonacciTest {

    private val algorithm = FibonacciAlgorithm()
    private val projector = FibonacciProjector()

    private fun runner(n: Int) = AlgorithmRunner(algorithm, Dataset(emptyList(), target = n))

    private fun state(n: Int) = algorithm.initial(Dataset(emptyList(), target = n))

    /** Drive to completion the way WATCH does — always the correct option. */
    private fun driveCorrectly(n: Int): FibonacciState {
        val r = runner(n)
        var guard = 0
        while (guard++ < 500) {
            when (val probe = r.probe()) {
                is Probe.Mechanical -> r.apply(probe.action)
                is Probe.Decide -> r.apply(probe.decision.correct)
                is Probe.Terminal -> return r.current.state
            }
        }
        error("Fibonacci did not terminate.")
    }

    /**
     * The definition, written out independently. Deliberately the naive recursion:
     * it is slow, it is the thing the lesson is about, and at these sizes its cost
     * is the point rather than a problem.
     */
    private fun referenceFib(n: Int): Int = if (n <= 1) maxOf(n, 0) else
        referenceFib(n - 1) + referenceFib(n - 2)

    // -- 1. The sequence itself ------------------------------------------------

    @Test
    fun `the documented values are the ones the engine produces`() {
        // The exact table the brief specifies, asserted value by value.
        val expected = listOf(0, 1, 1, 2, 3, 5, 8, 13, 21)
        assertEquals(expected, fibonacciTable(8))

        assertEquals(0, fibonacciTable(8)[0])
        assertEquals(1, fibonacciTable(8)[1])
        assertEquals(1, fibonacciTable(8)[2])
        assertEquals(2, fibonacciTable(8)[3])
        assertEquals(3, fibonacciTable(8)[4])
        assertEquals(5, fibonacciTable(8)[5])
        assertEquals(8, fibonacciTable(8)[6])
        assertEquals(13, fibonacciTable(8)[7])
        assertEquals(21, fibonacciTable(8)[8])
    }

    @Test
    fun `the table agrees with an independent recursive definition`() {
        // The table is iterative; the reference is recursive. If they agree for
        // every index up to 20, the table is the sequence and not something that
        // merely looks like it.
        val table = fibonacciTable(20)
        for (i in 0..20) {
            assertEquals("F($i)", referenceFib(i), table[i])
        }
    }

    @Test
    fun `F of 8 is 21, driven through the real engine`() {
        val end = driveCorrectly(8)
        assertEquals(21, end.result)
        assertEquals(21, end.answer)
        assertEquals(listOf(0, 1, 1, 2, 3, 5, 8, 13, 21), end.dp)
    }

    // -- 2. Table construction, step by step -----------------------------------

    @Test
    fun `the base cases are given, never asked for`() {
        val start = state(8)
        // F(0) and F(1) are the definition of the sequence — there is no reasoning
        // that produces them, so the lesson does not pretend there is.
        assertEquals(listOf(0, 1), start.dp)
        assertEquals(2, start.nextIndex)

        // ...and the first thing the learner is asked about is dp[2].
        val probe = algorithm.probe(start)
        assertTrue(probe is Probe.Decide)
        assertEquals(
            "What is dp[2]?",
            2,
            (probe as Probe.Decide).decision.prompt.args.first(),
        )
    }

    @Test
    fun `every transition writes exactly one entry, in order`() {
        val r = runner(8)
        val written = mutableListOf<Int>()
        var guard = 0
        while (guard++ < 100) {
            when (val probe = r.probe()) {
                is Probe.Decide -> {
                    val before = r.current.state.dp.size
                    val frame = r.apply(probe.decision.correct)
                    // One cell per step — the cursor can never skip or double up.
                    assertEquals(before + 1, frame.state.dp.size)
                    val inserted = frame.events.filterIsInstance<VizEvent.Insert>()
                    assertEquals(1, inserted.size)
                    assertEquals(before, inserted.single().at)
                    written += inserted.single().value
                }
                is Probe.Mechanical -> error("Fibonacci has no mechanical beats.")
                is Probe.Terminal -> break
            }
        }
        // dp[2]..dp[8] — seven entries, and the base cases were not among them.
        assertEquals(listOf(1, 2, 3, 5, 8, 13, 21), written)
    }

    @Test
    fun `the source indices are always the two cells immediately before`() {
        val r = runner(8)
        var guard = 0
        while (guard++ < 100) {
            val probe = r.probe()
            if (probe is Probe.Terminal) break
            val decision = (probe as Probe.Decide).decision
            val i = r.current.state.nextIndex
            // The focus is what the picture lights and what the copy names, so it
            // has to be exactly dp[i-1] and dp[i-2] — never a window, never the
            // row above, never a guess.
            assertEquals("focus at dp[$i]", listOf(i - 2, i - 1), decision.focus)
            r.apply(decision.correct)
        }
    }

    @Test
    fun `the expected answer is always the sum of the two previous entries`() {
        val r = runner(8)
        var guard = 0
        while (guard++ < 100) {
            val probe = r.probe()
            if (probe is Probe.Terminal) break
            val s = r.current.state
            val a = requireNotNull(s.previous)
            val b = requireNotNull(s.beforeThat)
            assertEquals(a + b, s.expectedNext)
            assertEquals(
                FibonacciAction.Fill(a + b),
                (probe as Probe.Decide).decision.correct,
            )
            r.apply(probe.decision.correct)
        }
    }

    @Test
    fun `every decision offers three distinct values including the right one`() {
        val r = runner(8)
        var guard = 0
        while (guard++ < 100) {
            val probe = r.probe()
            if (probe is Probe.Terminal) break
            val decision = (probe as Probe.Decide).decision
            assertEquals(DecisionKind.OPTIONS, decision.kind)
            assertEquals(3, decision.options.size)
            // Distinct, or two buttons read the same and one of them is a trap.
            assertEquals(3, decision.options.map { it.action }.toSet().size)
            // ADR-006's rule, applied per decision: the right answer must be on
            // the table, or the learner cannot progress at all.
            assertTrue(decision.options.any { it.action == decision.correct })
            // Every wrong option explains itself.
            for (option in decision.options) {
                if (option.action == decision.correct) continue
                assertNotNull(
                    "whyWrong for $option",
                    decision.whyWrong[option.action],
                )
            }
            r.apply(decision.correct)
        }
    }

    @Test
    fun `the correct answer does not always sit in the same seat`() {
        // A learner who notices the answer is always the middle button has found a
        // way to pass without reading the numbers.
        val seats = mutableSetOf<Int>()
        val r = runner(8)
        var guard = 0
        while (guard++ < 100) {
            val probe = r.probe()
            if (probe is Probe.Terminal) break
            val decision = (probe as Probe.Decide).decision
            seats += decision.options.indexOfFirst { it.action == decision.correct }
            r.apply(decision.correct)
        }
        assertTrue("the answer never moved seat: $seats", seats.size > 1)
    }

    // -- 3. Wrong answers ------------------------------------------------------

    @Test
    fun `a wrong answer is refused and the state does not move`() {
        val r = runner(8)
        val decision = (r.probe() as Probe.Decide).decision
        val before = r.current.state
        val wrong = decision.options.map { it.action }.first { it != decision.correct }

        // Five wrong answers in a row, and the table is byte-for-byte where it was.
        repeat(5) { attempt ->
            val result = DecisionValidation.validate(decision, wrong, attempt)
            assertTrue("attempt $attempt", result is Validation.Retry)
        }
        assertEquals(before, r.current.state)
        assertEquals(listOf(0, 1), r.current.state.dp)
    }

    @Test
    fun `guidance escalates and then holds, so a learner is never dead-ended`() {
        val decision = (runner(8).probe() as Probe.Decide).decision
        val wrong = decision.options.map { it.action }.first { it != decision.correct }

        val first = DecisionValidation.validate(decision, wrong, 0) as Validation.Retry
        val second = DecisionValidation.validate(decision, wrong, 1) as Validation.Retry
        val third = DecisionValidation.validate(decision, wrong, 2) as Validation.Retry
        val fourth = DecisionValidation.validate(decision, wrong, 3) as Validation.Retry

        // Three distinct rungs...
        assertEquals(3, setOf(first.guidance, second.guidance, third.guidance).size)
        // ...and the most explicit one repeats rather than running out (ADR-021).
        assertEquals(third.guidance, fourth.guidance)
    }

    @Test
    fun `the correct answer is accepted and carries an action`() {
        val decision = (runner(8).probe() as Probe.Decide).decision
        val result = DecisionValidation.validate(decision, decision.correct, 0)
        assertTrue(result is Validation.Accept)
        assertEquals(decision.correct, (result as Validation.Accept).action)
    }

    @Test
    fun `a run of wrong guesses still ends on the right table`() {
        // The ADR-021 guarantee, end to end: a learner who guesses wrongly at every
        // beat before answering correctly reaches exactly the same answer, and the
        // engine records no wrong decision for the guesses it never applied.
        val r = runner(8)
        var guard = 0
        while (guard++ < 100) {
            val probe = r.probe()
            if (probe is Probe.Terminal) break
            val decision = (probe as Probe.Decide).decision
            for (option in decision.options) {
                if (option.action == decision.correct) continue
                val v = DecisionValidation.validate(decision, option.action, 0)
                // A Retry carries no action, so there is nothing to apply.
                assertTrue(v is Validation.Retry)
            }
            r.apply(decision.correct)
        }
        assertEquals(listOf(0, 1, 1, 2, 3, 5, 8, 13, 21), r.current.state.dp)
        assertEquals(0, r.current.metrics.wrongDecisions)
    }

    @Test
    fun `applying a wrong value directly still leaves a legal state that terminates`() {
        // `apply` is total (ADR-001) — it takes any action, which is what makes
        // `validate` a pure comparison. In TRY nothing ever calls it this way, but
        // the machine must not be corruptible if something does.
        val start = state(8)
        val diverged = algorithm.apply(start, FibonacciAction.Fill(99)).next
        assertEquals(listOf(0, 1, 99), diverged.dp)
        assertFalse(algorithm.apply(start, FibonacciAction.Fill(99)).correct)

        // It still runs to a terminal state rather than hanging or throwing.
        var s = diverged
        var guard = 0
        while (guard++ < 100) {
            when (val probe = algorithm.probe(s)) {
                is Probe.Decide -> s = algorithm.apply(s, probe.decision.correct).next
                is Probe.Mechanical -> s = algorithm.apply(s, probe.action).next
                is Probe.Terminal -> break
            }
        }
        assertTrue(s.buildComplete)
    }

    @Test
    fun `adversarial driving terminates from every beat`() {
        // Every offered option applied at every beat, and the machine still ends.
        // This is the test that makes wrong-choice handling safe (ARCHITECTURE §11).
        for (seed in 0 until 40) {
            val r = runner(8)
            var guard = 0
            while (guard++ < 200) {
                when (val probe = r.probe()) {
                    is Probe.Decide -> {
                        val options = probe.decision.options
                        r.apply(options[(seed + guard) % options.size].action)
                    }
                    is Probe.Mechanical -> r.apply(probe.action)
                    is Probe.Terminal -> break
                }
            }
            assertTrue("seed $seed did not terminate", r.current.state.buildComplete)
        }
    }

    @Test
    fun `filling past the end is a no-op rather than a crash`() {
        val end = driveCorrectly(8)
        val after = algorithm.apply(end, FibonacciAction.Fill(34))
        assertSame(end, after.next)
        assertFalse(after.correct)
        assertTrue(after.events.isEmpty())
    }

    // -- 4. Completion ---------------------------------------------------------

    @Test
    fun `the run terminates once the table is full`() {
        val r = runner(8)
        var guard = 0
        while (guard++ < 100 && r.probe() !is Probe.Terminal) {
            r.apply((r.probe() as Probe.Decide).decision.correct)
        }
        val probe = r.probe()
        assertTrue(probe is Probe.Terminal)
        assertEquals(Outcome.Completed(true), (probe as Probe.Terminal).outcome)
        assertTrue(r.current.state.finished)
        // The terminal event is emitted with the last fill, so the Complete screen
        // has something to fold.
        assertTrue(r.current.events.any { it is VizEvent.Terminal })
    }

    @Test
    fun `rewind returns the exact previous state`() {
        val r = runner(8)
        val before = r.current.state
        r.apply((r.probe() as Probe.Decide).decision.correct)
        assertEquals(before, r.rewind().state)
    }

    // -- 5. Edge cases ---------------------------------------------------------

    @Test
    fun `small and degenerate targets are finished lessons, not crashes`() {
        // F(0): nothing to build. A finished lesson.
        val zero = driveCorrectly(0)
        assertEquals(listOf(0), zero.dp)
        assertEquals(0, zero.result)

        // F(1): both base cases are already the whole table.
        val one = driveCorrectly(1)
        assertEquals(listOf(0, 1), one.dp)
        assertEquals(1, one.result)

        // F(2): exactly one decision.
        val two = driveCorrectly(2)
        assertEquals(listOf(0, 1, 1), two.dp)

        // A negative target is a bad dataset, normalised once, never a crash.
        val negative = algorithm.initial(Dataset(emptyList(), target = -5))
        assertEquals(0, negative.n)
        assertTrue(algorithm.probe(negative) is Probe.Terminal)
    }

    @Test
    fun `a dataset with no target still produces a lesson`() {
        val noTarget = algorithm.initial(Dataset(emptyList()))
        assertEquals(8, noTarget.n)
    }

    @Test
    fun `larger targets stay correct`() {
        for (n in 2..25) {
            assertEquals("F($n)", referenceFib(n), driveCorrectly(n).result)
        }
    }

    // -- 6. The repeated work the lesson is built on ---------------------------

    @Test
    fun `naive call counts match a real counting recursion`() {
        // The claim in the copy — "F(8) takes 67 calls" — is computed, and here it
        // is checked against a recursion that actually counts itself.
        var calls = 0
        fun count(n: Int): Int {
            calls++
            return if (n <= 1) maxOf(n, 0) else count(n - 1) + count(n - 2)
        }
        for (n in 0..18) {
            calls = 0
            count(n)
            assertEquals("calls for F($n)", calls, naiveCallCount(n))
        }
    }

    @Test
    fun `the documented repeated work for the teaching dataset is real`() {
        // 67 calls to produce nine numbers is the argument the lesson opens on.
        assertEquals(67, naiveCallCount(8))
        assertEquals(9, fibonacciTable(8).size)
        // And F(3) alone is recomputed eight times on the way.
        assertEquals(8, naiveCallsTo(8, 3))
    }

    @Test
    fun `recompute counts match a real counting recursion`() {
        for (n in 0..16) {
            for (k in 0..n) {
                var hits = 0
                fun count(m: Int) {
                    if (m == k) hits++
                    if (m > 1) {
                        count(m - 1)
                        count(m - 2)
                    }
                }
                count(n)
                assertEquals("F($k) inside F($n)", hits, naiveCallsTo(n, k))
            }
        }
    }

    @Test
    fun `naive cost grows far faster than the table does`() {
        // The saving is the lesson, so it is asserted rather than described.
        for (n in 10..20) {
            assertTrue(
                "naive should dwarf the table at n=$n",
                naiveCallCount(n) > 10 * (n + 1),
            )
        }
    }

    // -- 7. The picture --------------------------------------------------------

    @Test
    fun `the scene is an ordinary row, with indices shown`() {
        val scene = projector.project(state(8), emptyList()) as SequenceScene
        // A one-axis DP table is a sequence. No new scene shape was added for this
        // lesson, and this is what says so (ADR-045).
        assertEquals(SceneLayout.ROW, scene.layout)
        assertEquals(9, scene.cells.size)
        // The copy names dp[i-1] and dp[i-2], so the positions have to be visible.
        assertTrue(scene.showIndices)
    }

    @Test
    fun `uncomputed cells are holes, never zeros`() {
        val scene = projector.project(state(8), emptyList()) as SequenceScene
        // The base cases are there...
        assertEquals(CellState.COMPARING, scene.cells[0].state)
        assertEquals(CellState.COMPARING, scene.cells[1].state)
        // ...and everything else is a GHOST — the hole Insertion Sort established.
        // A `0` in an uncomputed cell would claim a value exists before it does.
        for (i in 2..8) {
            assertEquals("dp[$i]", CellState.GHOST, scene.cells[i].state)
        }
    }

    @Test
    fun `the two source cells are lit, and they are the ones just added`() {
        val r = runner(8)
        // Build up to dp[5] so the sources are interior cells rather than the base.
        repeat(4) { r.apply((r.probe() as Probe.Decide).decision.correct) }
        val frame = r.apply((r.probe() as Probe.Decide).decision.correct)
        val scene = projector.project(frame.state, frame.events) as SequenceScene

        // dp[6] was just written from dp[5] and dp[4].
        assertEquals(CellState.CANDIDATE, scene.cells[6].state)
        assertEquals(CellState.COMPARING, scene.cells[5].state)
        assertEquals(CellState.COMPARING, scene.cells[4].state)
        // Not the cells the cursor has already moved on to.
        assertEquals(CellState.IDLE, scene.cells[3].state)
        assertEquals(CellState.GHOST, scene.cells[7].state)
    }

    @Test
    fun `the equation names its operands and hides the result until it is known`() {
        val pending = projector.project(state(8), emptyList()) as SequenceScene
        val asked = requireNotNull(pending.equation)
        assertEquals(1, asked.left)
        assertEquals(0, asked.right)
        assertEquals(PrefixOp.PLUS, asked.operator)
        // An answer already on screen is not a question (ADR-030).
        assertNull(asked.result)
        assertEquals("dp[1]", asked.leftLabel)
        assertEquals("dp[0]", asked.rightLabel)
        assertEquals("dp[2]", asked.resultLabel)

        // Once answered, the same strip carries the result.
        val r = runner(8)
        val frame = r.apply((r.probe() as Probe.Decide).decision.correct)
        val answered = requireNotNull(
            (projector.project(frame.state, frame.events) as SequenceScene).equation,
        )
        assertEquals(1, answered.result)
    }

    @Test
    fun `the badge withholds the answer until the table is built`() {
        val start = requireNotNull(
            (projector.project(state(8), emptyList()) as SequenceScene).badge,
        )
        assertEquals("F(8)", start.label)
        assertEquals("?", start.valueLabel)

        val end = requireNotNull(
            (projector.project(driveCorrectly(8), emptyList()) as SequenceScene).badge,
        )
        assertEquals(21, end.value)
        assertNull(end.valueLabel)
    }

    @Test
    fun `the answer cell is finalized once the table is full`() {
        val scene = projector.project(driveCorrectly(8), emptyList()) as SequenceScene
        assertEquals(CellState.FINALIZED, scene.cells[8].state)
        assertEquals(21, scene.cells[8].value)
        // No holes left.
        assertTrue(scene.cells.none { it.state == CellState.GHOST })
    }

    // -- 8. The walkthrough ----------------------------------------------------

    private fun script() = AlgorithmCatalog.fibonacci().watchScript()

    @Test
    fun `the walkthrough opens on the problem and closes on the idea`() {
        val steps = script().steps
        assertEquals(WatchStepKind.SETUP, steps.first().kind)
        assertEquals(WatchStepKind.INSIGHT, steps[steps.size - 2].kind)
        assertEquals(WatchStepKind.SUMMARY, steps.last().kind)
        // Five recap bullets: the rule, the trap, memoization, tabulation, and the
        // one sentence both fixes share.
        assertEquals(5, steps.last().bullets.size)
    }

    @Test
    fun `the walkthrough shows the repeated work before it shows the table`() {
        val steps = script().steps
        val naive = steps.indexOfFirst {
            it.headline.id == com.algorithms.algoking.engine.narration
                .NarrationId.FIB_WATCH_NAIVE
        }
        val fixes = steps.indexOfFirst {
            it.headline.id == com.algorithms.algoking.engine.narration
                .NarrationId.FIB_WATCH_TWO_FIXES
        }
        val firstBuild = steps.indexOfFirst {
            it.headline.id == com.algorithms.algoking.engine.narration
                .NarrationId.FIB_WATCH_BUILD
        }
        // The trap has to land before the fix, or the fix is a solution to a
        // problem the learner has not met.
        assertTrue("naive beat missing", naive >= 0)
        assertTrue("naive before the fix", naive < fixes)
        assertTrue("the fix before the first build", fixes < firstBuild)
    }

    @Test
    fun `the walkthrough states both memoization and tabulation`() {
        // The brief asks for both to be taught; tabulation is what is interactive,
        // so memoization has to be said out loud or it is simply absent.
        val ids = script().steps.flatMap { listOf(it.headline) + it.bullets }.map { it.id }
        assertTrue(
            ids.contains(
                com.algorithms.algoking.engine.narration.NarrationId.FIB_WATCH_TWO_FIXES,
            ),
        )
        assertTrue(
            ids.contains(com.algorithms.algoking.engine.narration.NarrationId.FIB_IDEA_3),
        )
        assertTrue(
            ids.contains(com.algorithms.algoking.engine.narration.NarrationId.FIB_IDEA_4),
        )
    }

    @Test
    fun `the walkthrough narrates the opening entries and collapses the middle`() {
        val steps = script().steps
        val builds = steps.count {
            it.headline.id == com.algorithms.algoking.engine.narration
                .NarrationId.FIB_WATCH_BUILD
        }
        val collapsed = steps.count {
            it.headline.id == com.algorithms.algoking.engine.narration
                .NarrationId.FIB_WATCH_COLLAPSED
        }
        // dp[2] dp[3] dp[4] in full, dp[5..7] as one beat, dp[8] as the finale.
        assertEquals(3, builds)
        assertEquals(1, collapsed)
        assertEquals(1, steps.count { it.kind == WatchStepKind.FOUND })
    }

    @Test
    fun `the walkthrough is a readable length`() {
        // Pinned, so "just narrate one more thing" cannot quietly turn the lesson
        // into a slideshow (ADR-025).
        val size = script().size
        assertTrue("walkthrough was $size steps", size in 10..16)
    }

    @Test
    fun `no two adjacent walkthrough steps are identical`() {
        // ADR-020: a step where nothing changed is a bug, not a beat.
        val steps = script().steps
        for (i in 1 until steps.size) {
            val a = steps[i - 1]
            val b = steps[i]
            assertTrue(
                "steps $i and ${i - 1} say and show the same thing",
                a.headline != b.headline || a.scene != b.scene || a.support != b.support,
            )
        }
    }

    @Test
    fun `the run-up on an unchanged picture is kept short`() {
        // ADR-020's rule has a soft edge for a conceptual lesson: the opening
        // beats genuinely do share one picture, because the table is still empty.
        // They are allowed, and they are capped — a long run of identical screens
        // is a slideshow however good the copy is.
        val steps = script().steps
        var longest = 1
        var run = 1
        for (i in 1 until steps.size) {
            run = if (steps[i].scene == steps[i - 1].scene) run + 1 else 1
            longest = maxOf(longest, run)
        }
        assertTrue("$longest steps in a row showed the same picture", longest <= 4)
    }

    @Test
    fun `the walkthrough ends on the answer it was looking for`() {
        val last = script().steps.last()
        assertEquals(21, (last.scene as SequenceScene).cells[8].value)
        assertEquals(listOf(8, 21), last.headline.args)
    }

    // -- 9. Registration -------------------------------------------------------

    @Test
    fun `the lesson is in the catalog and wired to its own datasets`() {
        val pack = AlgorithmCatalog.byId(AlgorithmId.FIBONACCI)
        assertEquals(AlgorithmId.FIBONACCI, pack.id)
        assertEquals("Fibonacci", pack.displayName)
        assertEquals(FibonacciDatasets.watch, pack.watchDataset)
        assertEquals(FibonacciDatasets.tryIt, pack.tryDataset)
        assertEquals(8, pack.watchDataset.target)
        assertEquals(8, pack.tryDataset.target)
    }

    @Test
    fun `there is no challenge, the same as every other Advanced lesson`() {
        // CHALLENGE is V2 (ADR-031). Null is the honest signature — "not written
        // yet" is a real state.
        assertNull(ChallengeCatalog.byId(AlgorithmId.FIBONACCI))
    }

    @Test
    fun `progress runs nought, fifty, one hundred and cannot go backwards`() {
        var progress = AlgorithmProgress()
        assertEquals(0, progress.percent)

        progress = progress.complete(Stage.WATCH)
        assertEquals(50, progress.percent)

        progress = progress.complete(Stage.TRY)
        assertEquals(100, progress.percent)

        // Latched and additive (ADR-028): repeating a stage cannot subtract.
        progress = progress.complete(Stage.WATCH)
        assertEquals(100, progress.percent)
    }

    @Test
    fun `TRY asks about entries the walkthrough never narrated`() {
        // WATCH and TRY share one n, because there is only one Fibonacci sequence.
        // What stops TRY being a replay is that WATCH collapsed dp[5] dp[6] dp[7]
        // into a single beat — so most of what TRY asks was never walked through.
        val narratedInFull = setOf(2, 3, 4, 8)
        val asked = mutableListOf<Int>()
        val r = AlgorithmRunner(algorithm, FibonacciDatasets.tryIt)
        var guard = 0
        while (guard++ < 100) {
            val probe = r.probe()
            if (probe is Probe.Terminal) break
            asked += r.current.state.nextIndex
            r.apply((probe as Probe.Decide).decision.correct)
        }
        assertEquals(listOf(2, 3, 4, 5, 6, 7, 8), asked)
        assertTrue(asked.any { it !in narratedInFull })
    }
}
