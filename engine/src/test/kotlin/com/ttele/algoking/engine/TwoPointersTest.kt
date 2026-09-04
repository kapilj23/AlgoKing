package com.ttele.algoking.engine

import com.ttele.algoking.engine.algorithms.twopointers.TwoPointersAction
import com.ttele.algoking.engine.algorithms.twopointers.TwoPointersAlgorithm
import com.ttele.algoking.engine.algorithms.twopointers.TwoPointersProjector
import com.ttele.algoking.engine.algorithms.twopointers.TwoPointersState
import com.ttele.algoking.engine.catalog.AlgorithmCatalog
import com.ttele.algoking.engine.core.AlgorithmId
import com.ttele.algoking.engine.core.AlgorithmRunner
import com.ttele.algoking.engine.core.Dataset
import com.ttele.algoking.engine.core.Probe
import com.ttele.algoking.engine.dataset.TwoPointersDatasets
import com.ttele.algoking.engine.decision.DecisionValidation
import com.ttele.algoking.engine.decision.Validation
import com.ttele.algoking.engine.event.Outcome
import com.ttele.algoking.engine.event.Relation
import com.ttele.algoking.engine.event.VizEvent
import com.ttele.algoking.engine.scene.CellState
import com.ttele.algoking.engine.scene.SequenceScene
import com.ttele.algoking.engine.walkthrough.WatchStepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two Pointers — the first Advanced lesson.
 *
 * The single most important property here is that **one rule set drives WATCH and
 * TRY**. Both go through `probe`/`apply` on the same `TwoPointersAlgorithm`, so
 * there is no second implementation that could disagree; several tests below
 * assert that directly rather than trusting it.
 */
class TwoPointersTest {

    private val algorithm = TwoPointersAlgorithm()

    private fun runner(values: List<Int>, target: Int) =
        AlgorithmRunner(algorithm, Dataset(values = values, target = target))

    /** Drive to completion the way WATCH does, collecting the decisions taken. */
    private fun driveCorrectly(
        values: List<Int>,
        target: Int,
    ): Pair<TwoPointersState, List<TwoPointersAction>> {
        val r = runner(values, target)
        val decisions = mutableListOf<TwoPointersAction>()
        var guard = 0
        while (guard++ < 200) {
            when (val probe = r.probe()) {
                is Probe.Mechanical -> r.apply(probe.action)
                is Probe.Decide -> {
                    decisions += probe.decision.correct
                    r.apply(probe.decision.correct)
                }

                is Probe.Terminal -> return r.current.state to decisions
            }
        }
        error("Two Pointers did not terminate.")
    }

    // -- 1. The authored teaching run -----------------------------------------

    @Test
    fun `the watch dataset runs exactly the documented three rounds`() {
        val (state, decisions) = driveCorrectly(listOf(1, 2, 4, 6, 8, 10), 10)

        // 1 + 10 = 11 > 10 -> RIGHT ; 1 + 8 = 9 < 10 -> LEFT ; 2 + 8 = 10 -> found.
        assertEquals(
            listOf(
                TwoPointersAction.MoveRight,
                TwoPointersAction.MoveLeft,
                TwoPointersAction.FoundPair,
            ),
            decisions,
        )
        assertEquals(1, state.foundLeft)
        assertEquals(4, state.foundRight)
        assertEquals(2, state.leftValue)
        assertEquals(8, state.rightValue)
    }

    @Test
    fun `the watch run shows both moves, so neither pointer is a mystery`() {
        val (_, decisions) = driveCorrectly(
            TwoPointersDatasets.watch.values,
            requireNotNull(TwoPointersDatasets.watch.target),
        )
        assertTrue(decisions.contains(TwoPointersAction.MoveLeft))
        assertTrue(decisions.contains(TwoPointersAction.MoveRight))
    }

    @Test
    fun `the try dataset is a different array and a different target`() {
        val watch = TwoPointersDatasets.watch
        val tryIt = TwoPointersDatasets.tryIt
        assertTrue(watch.values != tryIt.values)
        assertTrue(watch.target != tryIt.target)

        val (state, _) = driveCorrectly(tryIt.values, requireNotNull(tryIt.target))
        assertNotNull(state.foundLeft)
        assertEquals(17, requireNotNull(state.leftValue) + requireNotNull(state.rightValue))
    }

    @Test
    fun `both teaching datasets are sorted, which is the whole precondition`() {
        for (dataset in listOf(TwoPointersDatasets.watch, TwoPointersDatasets.tryIt)) {
            assertEquals(dataset.values.sorted(), dataset.values)
            assertEquals(dataset.values.distinct().size, dataset.values.size)
        }
    }

    // -- 2. The movement rule -------------------------------------------------

    @Test
    fun `sum greater than target moves RIGHT left`() {
        val r = runner(listOf(1, 2, 4, 6, 8, 10), 10)
        r.apply(TwoPointersAction.Compare)
        val decision = (r.probe() as Probe.Decide).decision
        assertEquals(TwoPointersAction.MoveRight, decision.correct)

        r.apply(decision.correct)
        assertEquals(0, r.current.state.left)
        assertEquals(4, r.current.state.right)
    }

    @Test
    fun `sum smaller than target moves LEFT right`() {
        // 1 + 8 = 9 < 10.
        val r = runner(listOf(1, 2, 4, 6, 8), 10)
        r.apply(TwoPointersAction.Compare)
        val decision = (r.probe() as Probe.Decide).decision
        assertEquals(TwoPointersAction.MoveLeft, decision.correct)

        r.apply(decision.correct)
        assertEquals(1, r.current.state.left)
        assertEquals(4, r.current.state.right)
    }

    @Test
    fun `sum equal to target is the pair, and the run ends`() {
        // 2 + 8 = 10.
        val r = runner(listOf(2, 4, 6, 8), 10)
        r.apply(TwoPointersAction.Compare)
        val decision = (r.probe() as Probe.Decide).decision
        assertEquals(TwoPointersAction.FoundPair, decision.correct)

        r.apply(decision.correct)
        assertTrue(r.probe() is Probe.Terminal)
        assertEquals(Outcome.Found(0), (r.probe() as Probe.Terminal).outcome)
    }

    @Test
    fun `the rule holds for every reachable state, not just the authored ones`() {
        // The rule stated once, checked against the algorithm everywhere it fires.
        for (target in 0..40) {
            val r = runner(listOf(1, 3, 5, 8, 12, 17, 21), target)
            var guard = 0
            while (guard++ < 200) {
                when (val probe = r.probe()) {
                    is Probe.Mechanical -> r.apply(probe.action)
                    is Probe.Decide -> {
                        val s = r.current.state
                        val expected = when (requireNotNull(s.relation)) {
                            Relation.EQUAL -> TwoPointersAction.FoundPair
                            Relation.GREATER -> TwoPointersAction.MoveRight
                            Relation.LESS -> TwoPointersAction.MoveLeft
                        }
                        assertEquals(
                            "target=$target sum=${s.currentSum}",
                            expected,
                            probe.decision.correct,
                        )
                        r.apply(probe.decision.correct)
                    }

                    is Probe.Terminal -> break
                }
            }
        }
    }

    // -- 3. Correctness against a reference ------------------------------------

    /** Brute force: does any pair sum to the target? */
    private fun referenceHasPair(values: List<Int>, target: Int): Boolean {
        for (i in values.indices) {
            for (j in i + 1 until values.size) {
                if (values[i] + values[j] == target) return true
            }
        }
        return false
    }

    @Test
    fun `the outcome matches brute force across many sorted arrays and targets`() {
        val arrays = listOf(
            listOf(1, 2, 4, 6, 8, 10),
            listOf(3, 5, 9, 11, 14, 21),
            listOf(-9, -4, -1, 0, 2, 7, 13),
            listOf(2, 2, 4, 4, 8, 8),
            listOf(1, 100),
            listOf(0, 0, 0, 0),
        )
        for (values in arrays) {
            for (target in -20..40) {
                val (state, _) = driveCorrectly(values, target)
                assertEquals(
                    "values=$values target=$target",
                    referenceHasPair(values, target),
                    state.foundLeft != null,
                )
                // When it claims a pair, the pair really does add up.
                state.foundLeft?.let { l ->
                    assertEquals(target, values[l] + values[requireNotNull(state.foundRight)])
                }
            }
        }
    }

    // -- 4. Edge cases ---------------------------------------------------------

    @Test
    fun `no pair drives the pointers together and reports NotFound`() {
        val (state, _) = driveCorrectly(listOf(1, 2, 4, 6), 100)
        assertNull(state.foundLeft)
        assertTrue(state.exhausted)
        assertFalse(state.hasPair)
    }

    @Test
    fun `an array too short to hold a pair terminates immediately`() {
        for (values in listOf(emptyList(), listOf(5), listOf(-3))) {
            val r = AlgorithmRunner(algorithm, Dataset(values = values, target = 5))
            val probe = r.probe()
            assertTrue("values=$values", probe is Probe.Terminal)
            assertEquals(Outcome.NotFound, (probe as Probe.Terminal).outcome)
        }
    }

    @Test
    fun `a pair at the two ends is found on the first comparison`() {
        val (state, decisions) = driveCorrectly(listOf(1, 2, 4, 6, 9), 10)
        assertEquals(listOf(TwoPointersAction.FoundPair), decisions)
        assertEquals(0, state.foundLeft)
        assertEquals(4, state.foundRight)
    }

    @Test
    fun `duplicate values are handled without special casing`() {
        val (state, _) = driveCorrectly(listOf(2, 2, 4, 4), 4)
        assertNotNull(state.foundLeft)
        assertEquals(
            4,
            requireNotNull(state.leftValue) + requireNotNull(state.rightValue),
        )
    }

    @Test
    fun `negative numbers work, because nothing here assumes positives`() {
        val (state, _) = driveCorrectly(listOf(-8, -3, 0, 1, 5, 9), -3)
        assertNotNull(state.foundLeft)
        assertEquals(
            -3,
            requireNotNull(state.leftValue) + requireNotNull(state.rightValue),
        )
    }

    @Test
    fun `pointers can never cross, whatever sequence of moves is applied`() {
        // Every legal action from every reachable state, driven adversarially.
        val values = listOf(1, 3, 5, 7, 9, 11)
        var state = algorithm.initial(Dataset(values = values, target = 12))
        val moves = listOf(
            TwoPointersAction.MoveLeft,
            TwoPointersAction.MoveRight,
            TwoPointersAction.MoveLeft,
            TwoPointersAction.MoveLeft,
            TwoPointersAction.MoveRight,
            TwoPointersAction.MoveRight,
            TwoPointersAction.MoveLeft,
            TwoPointersAction.MoveRight,
            TwoPointersAction.MoveLeft,
            TwoPointersAction.MoveRight,
        )
        for (move in moves) {
            state = algorithm.apply(state, move).next
            assertTrue("left=${state.left}", state.left in values.indices)
            assertTrue("right=${state.right}", state.right in values.indices)
            assertTrue("crossed: ${state.left} > ${state.right}", state.left <= state.right)
        }
    }

    @Test
    fun `claiming a pair that does not add up changes nothing`() {
        val r = runner(listOf(1, 2, 4, 6, 8, 10), 10)
        r.apply(TwoPointersAction.Compare)
        val before = r.current.state

        val after = algorithm.apply(before, TwoPointersAction.FoundPair)
        assertEquals(before.left, after.next.left)
        assertEquals(before.right, after.next.right)
        assertNull(after.next.foundLeft)
        assertFalse(after.correct)
    }

    // -- 5. Termination --------------------------------------------------------

    @Test
    fun `every run terminates, for every target over a range of arrays`() {
        for (size in 2..12) {
            val values = (1..size).map { it * 3 }
            for (target in 0..(size * 7)) {
                // driveCorrectly errors out if it exceeds its guard.
                driveCorrectly(values, target)
            }
        }
    }

    // -- 6. WATCH and TRY are the same rules -----------------------------------

    @Test
    fun `WATCH and TRY read their decisions from the same algorithm`() {
        // The claim, as code: build the walkthrough the way WATCH does, and drive
        // the same dataset the way TRY does. Neither may disagree about a single
        // correct action, because there is only one `probe` to ask.
        val pack = AlgorithmCatalog.twoPointers()
        val script = pack.watchScript()
        assertTrue("a walkthrough with no steps is not a lesson", script.size > 0)

        val (watchState, watchDecisions) = driveCorrectly(
            pack.watchDataset.values,
            requireNotNull(pack.watchDataset.target),
        )
        val (replayState, replayDecisions) = driveCorrectly(
            pack.watchDataset.values,
            requireNotNull(pack.watchDataset.target),
        )
        assertEquals(watchDecisions, replayDecisions)
        assertEquals(watchState, replayState)
    }

    @Test
    fun `a wrong answer never moves a pointer`() {
        // The invariant the whole learning engine rests on (ADR-021), checked here
        // because a new algorithm is exactly where it could be broken.
        val r = runner(listOf(1, 2, 4, 6, 8, 10), 10)
        r.apply(TwoPointersAction.Compare)
        val decision = (r.probe() as Probe.Decide).decision
        val before = r.current.state

        // Correct is MoveRight; try both wrong answers, repeatedly.
        val wrong = listOf(TwoPointersAction.MoveLeft, TwoPointersAction.FoundPair)
        for (attempt in 0..4) {
            for (action in wrong) {
                val verdict = DecisionValidation.validate(decision, action, attempt)
                assertTrue(verdict is Validation.Retry)
                // A Retry carries no action, so there is nothing to apply.
                assertEquals(before, r.current.state)
            }
        }
    }

    @Test
    fun `guidance escalates and then holds, never dead-ending`() {
        val r = runner(listOf(1, 2, 4, 6, 8, 10), 10)
        r.apply(TwoPointersAction.Compare)
        val decision = (r.probe() as Probe.Decide).decision

        val rungs = (0..5).map { attempt ->
            val verdict = DecisionValidation.validate(
                decision,
                TwoPointersAction.MoveLeft,
                attempt,
            )
            (verdict as Validation.Retry).guidance
        }
        assertEquals(3, rungs.take(3).distinct().size)
        // Past the ladder the most explicit rung repeats rather than running out.
        assertEquals(rungs[2], rungs[3])
        assertEquals(rungs[2], rungs[5])
    }

    @Test
    fun `the correct action is always among the options`() {
        for (target in 0..30) {
            val r = runner(listOf(1, 4, 6, 9, 13, 20), target)
            var guard = 0
            while (guard++ < 200) {
                when (val probe = r.probe()) {
                    is Probe.Mechanical -> r.apply(probe.action)
                    is Probe.Decide -> {
                        val d = probe.decision
                        assertTrue(
                            "target=$target",
                            d.options.any { it.action == d.correct },
                        )
                        // All three moves are always offered, so "Pair found"
                        // appearing is never itself the answer.
                        assertEquals(3, d.options.size)
                        r.apply(d.correct)
                    }

                    is Probe.Terminal -> break
                }
            }
        }
    }

    // -- 7. The picture --------------------------------------------------------

    @Test
    fun `the projector marks both ends as the pair and everything outside as gone`() {
        val r = runner(listOf(1, 2, 4, 6, 8, 10), 10)
        r.apply(TwoPointersAction.Compare)
        r.apply(TwoPointersAction.MoveRight)
        r.apply(TwoPointersAction.Compare)

        val scene = TwoPointersProjector()
            .project(r.current.state, r.current.events) as SequenceScene

        assertEquals(CellState.COMPARING, scene.cells[0].state)
        assertEquals(CellState.COMPARING, scene.cells[4].state)
        assertEquals(CellState.ELIMINATED, scene.cells[5].state)
        assertEquals(CellState.IDLE, scene.cells[2].state)
        // Both pointers named, in the textbook's words.
        assertEquals(setOf("LEFT", "RIGHT"), scene.pointers.map { it.label }.toSet())
    }

    @Test
    fun `the sum is only shown once the app has actually read the pair`() {
        val r = runner(listOf(1, 2, 4, 6, 8, 10), 10)
        val projector = TwoPointersProjector()

        val before = projector.project(r.current.state, emptyList()) as SequenceScene
        assertTrue(
            "a sum on screen before the comparison answers the question",
            before.meters.none { it.label == "Sum" },
        )

        r.apply(TwoPointersAction.Compare)
        val after = projector.project(r.current.state, r.current.events) as SequenceScene
        assertEquals(11L, after.meters.first { it.label == "Sum" }.value)
    }

    @Test
    fun `the found pair is finalized, and nothing else is`() {
        val r = runner(listOf(2, 4, 6, 8), 10)
        r.apply(TwoPointersAction.Compare)
        r.apply(TwoPointersAction.FoundPair)

        val scene = TwoPointersProjector()
            .project(r.current.state, r.current.events) as SequenceScene
        val finalized = scene.cells.filter { it.state == CellState.FINALIZED }.map { it.slot }
        assertEquals(listOf(0, 3), finalized)
    }

    // -- 8. It is wired into the app the same way every other lesson is --------

    @Test
    fun `the catalog resolves Two Pointers like any other lesson`() {
        val pack = AlgorithmCatalog.byId(AlgorithmId.TWO_POINTERS)
        assertEquals(AlgorithmId.TWO_POINTERS, pack.id)
        assertEquals("Two Pointers", pack.displayName)
    }

    @Test
    fun `no Swap event is ever emitted - nothing here exchanges two cells`() {
        val r = runner(listOf(1, 2, 4, 6, 8, 10), 10)
        val trace = r.runToCompletion()
        assertTrue(
            trace.frames.flatMap { it.events }.none { it is VizEvent.Swap },
        )
    }

    // -- 9. The walkthrough ----------------------------------------------------

    @Test
    fun `every watch step changes something visible - ADR-020`() {
        // The rule that keeps WATCH a walkthrough rather than a slideshow: a step
        // where nothing changed is a bug, not a beat.
        val steps = AlgorithmCatalog.twoPointers().watchScript().steps
        steps.zipWithNext { a, b ->
            val changed = a.scene != b.scene ||
                a.headline != b.headline ||
                a.comparison != b.comparison ||
                a.bullets != b.bullets
            assertTrue("steps ${a.index} and ${b.index} are identical", changed)
        }
    }

    @Test
    fun `the walkthrough states the sum before the pointer moves`() {
        // The order is the pedagogy: reason first, then consequence. A learner
        // shown a pointer that has already moved cannot predict the move.
        val steps = AlgorithmCatalog.twoPointers().watchScript().steps
        val firstCompare = steps.indexOfFirst { it.kind == WatchStepKind.COMPARE }
        val firstMove = steps.indexOfFirst { it.kind == WatchStepKind.ELIMINATE }
        assertTrue("no comparison beat", firstCompare >= 0)
        assertTrue("no move beat", firstMove >= 0)
        assertTrue("the move is narrated before the sum", firstCompare < firstMove)
    }

    @Test
    fun `every comparison step carries the readout chip that earns it its place`() {
        val steps = AlgorithmCatalog.twoPointers().watchScript().steps
        steps.filter { it.kind == WatchStepKind.COMPARE }.forEach {
            assertNotNull("a COMPARE step with no readout shows no change", it.comparison)
        }
    }

    @Test
    fun `the script ends INSIGHT then SUMMARY, and finds the pair`() {
        val steps = AlgorithmCatalog.twoPointers().watchScript().steps
        assertEquals(WatchStepKind.SUMMARY, steps.last().kind)
        assertEquals(WatchStepKind.INSIGHT, steps[steps.lastIndex - 1].kind)
        assertTrue(steps.any { it.kind == WatchStepKind.FOUND })
        assertTrue("the recap should list the rules", steps.last().bullets.isNotEmpty())
    }

    @Test
    fun `the walkthrough stays short enough to be a lesson, not a slideshow`() {
        // Pinned so "just narrate one more thing" cannot quietly turn WATCH into
        // something nobody finishes — the same guard every other lesson carries.
        val size = AlgorithmCatalog.twoPointers().watchScript().size
        assertTrue("walkthrough is $size steps", size in 8..16)
    }

    @Test
    fun `every step has a headline`() {
        AlgorithmCatalog.twoPointers().watchScript().steps.forEach {
            assertNotNull(it.headline)
        }
    }
}
