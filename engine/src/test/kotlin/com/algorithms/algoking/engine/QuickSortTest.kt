package com.algorithms.algoking.engine

import com.algorithms.algoking.engine.algorithms.quicksort.PartitionSide
import com.algorithms.algoking.engine.algorithms.quicksort.QuickSortAction
import com.algorithms.algoking.engine.algorithms.quicksort.QuickSortAlgorithm
import com.algorithms.algoking.engine.algorithms.quicksort.QuickSortProjector
import com.algorithms.algoking.engine.algorithms.quicksort.QuickSortState
import com.algorithms.algoking.engine.algorithms.quicksort.QuickSortWatchNarrator
import com.algorithms.algoking.engine.core.AlgorithmRunner
import com.algorithms.algoking.engine.core.Dataset
import com.algorithms.algoking.engine.core.Probe
import com.algorithms.algoking.engine.dataset.QuickSortDatasets
import com.algorithms.algoking.engine.decision.DecisionKind
import com.algorithms.algoking.engine.decision.DecisionValidation
import com.algorithms.algoking.engine.decision.Validation
import com.algorithms.algoking.engine.event.Outcome
import com.algorithms.algoking.engine.narration.NarrationId
import com.algorithms.algoking.engine.scene.CellState
import com.algorithms.algoking.engine.walkthrough.WatchScriptBuilder
import com.algorithms.algoking.engine.walkthrough.WatchStepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickSortTest {

    private fun runner(dataset: Dataset) = AlgorithmRunner(QuickSortAlgorithm(), dataset)

    private fun playPerfectly(dataset: Dataset): AlgorithmRunner<QuickSortState, QuickSortAction> {
        val r = runner(dataset)
        var guard = 0
        while (guard++ < 2048) {
            when (val probe = r.probe()) {
                is Probe.Mechanical -> r.apply(probe.action)
                is Probe.Decide -> r.apply(probe.decision.correct)
                is Probe.Terminal -> return r
            }
        }
        error("did not terminate")
    }

    private fun toDecision(
        r: AlgorithmRunner<QuickSortState, QuickSortAction>,
    ): Probe.Decide<QuickSortAction> {
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
            listOf(6, 3, 8, 2, 7, 4, 5),
            listOf(8, 3, 6, 2, 7, 4, 5),
            listOf(9, 4, 7, 2, 6, 3, 8),
            listOf(5, 4, 3, 2, 1),
            listOf(1, 2, 3, 4, 5),
            listOf(3, 1, 3, 2, 3),
            listOf(2, 1),
            listOf(7),
        ).forEach { values ->
            val r = playPerfectly(Dataset(values))
            assertEquals("failed on $values", values.sorted(), r.current.state.values)
            assertEquals(Outcome.Sorted, (r.probe() as Probe.Terminal).outcome)
        }
    }

    @Test
    fun `duplicates equal to the pivot go left and still sort`() {
        listOf(
            listOf(4, 4, 4, 4),
            listOf(5, 2, 5, 1, 5),
            listOf(3, 7, 3, 7, 3),
        ).forEach { values ->
            assertEquals(values.sorted(), playPerfectly(Dataset(values)).current.state.values)
        }

        // The rule itself: a value equal to the pivot is classified left.
        val r = runner(Dataset(listOf(5, 2, 5)))
        val d = toDecision(r).decision
        assertEquals(5, r.current.state.pivot)
        assertEquals(5, r.current.state.scanned)
        assertEquals(QuickSortAction.ClassifyLeft, d.correct)
    }

    // ── The pivot ─────────────────────────────────────────────────────────────

    @Test
    fun `the pivot is always the last value of the partition`() {
        val r = runner(QuickSortDatasets.watch)
        toDecision(r)
        assertEquals(r.current.state.hi, r.current.state.pivotAt)
        assertEquals(5, r.current.state.pivot)

        // And it stays deterministic on the next partition too.
        var guard = 0
        while (guard++ < 256 && r.current.state.finalized.isEmpty()) {
            when (val probe = r.probe()) {
                is Probe.Mechanical -> r.apply(probe.action)
                is Probe.Decide -> r.apply(probe.decision.correct)
                is Probe.Terminal -> break
            }
        }
        toDecision(r)
        assertEquals(r.current.state.hi, r.current.state.pivotAt)
    }

    @Test
    fun `choosing the pivot is the app's job, not a decision`() {
        val r = runner(QuickSortDatasets.watch)
        assertTrue(
            "the learner should not be asked to pick the pivot",
            r.probe() is Probe.Mechanical,
        )
        assertEquals(QuickSortAction.SelectPivot, (r.probe() as Probe.Mechanical).action)
    }

    @Test
    fun `a placed pivot is final and never moves again`() {
        val r = runner(QuickSortDatasets.watch)
        var placedValue: Int? = null
        var placedAt: Int? = null
        var guard = 0

        while (guard++ < 2048) {
            when (val probe = r.probe()) {
                is Probe.Mechanical -> r.apply(probe.action)
                is Probe.Terminal -> break
                is Probe.Decide -> {
                    val before = r.current.state
                    r.apply(probe.decision.correct)
                    val after = r.current.state
                    if (placedAt == null && after.finalized.size > before.finalized.size) {
                        placedAt = after.finalized.first()
                        placedValue = after.values[placedAt]
                    }
                }
            }
            // Once placed, that slot holds that value for the rest of the run.
            if (placedAt != null) {
                assertEquals(
                    "the pivot moved after being finalised",
                    placedValue,
                    r.current.state.values[placedAt],
                )
            }
        }
        assertNotNull(placedAt)
    }

    @Test
    fun `after a partition everything left of the pivot is smaller and right is larger`() {
        val r = runner(QuickSortDatasets.watch)
        var guard = 0
        while (guard++ < 256) {
            when (val probe = r.probe()) {
                is Probe.Mechanical -> r.apply(probe.action)
                is Probe.Terminal -> break
                is Probe.Decide -> {
                    val before = r.current.state
                    r.apply(probe.decision.correct)
                    val after = r.current.state
                    if (after.finalized.size > before.finalized.size) {
                        val at = before.pivotHome
                        val pivot = after.values[at]
                        assertTrue(
                            "a larger value sat left of the pivot",
                            (before.lo until at).all { after.values[it] <= pivot },
                        )
                        assertTrue(
                            "a smaller value sat right of the pivot",
                            ((at + 1)..before.hi).all { after.values[it] > pivot },
                        )
                        return
                    }
                }
            }
        }
        error("no partition completed")
    }

    // ── The two decisions ────────────────────────────────────────────────────

    @Test
    fun `classifying is left-or-right against the pivot`() {
        // 6 3 8 2 7 4 | 5 — the first value, 6, is larger than the pivot.
        val r = runner(QuickSortDatasets.watch)
        val d = toDecision(r).decision

        assertEquals(DecisionKind.OPTIONS, d.kind)
        assertEquals(
            setOf(QuickSortAction.ClassifyLeft, QuickSortAction.ClassifyRight),
            d.options.map { it.action }.toSet(),
        )
        assertEquals(6, r.current.state.scanned)
        assertEquals(QuickSortAction.ClassifyRight, d.correct)
    }

    @Test
    fun `placing the pivot is a cell decision at the boundary of the two groups`() {
        val r = runner(QuickSortDatasets.watch)
        toDecision(r)   // selects the pivot and puts the cursor on the first value
        var guard = 0
        while (guard++ < 64 && r.current.state.scanning) {
            r.apply(toDecision(r).decision.correct)
        }
        val d = toDecision(r).decision
        assertEquals(DecisionKind.CELL, d.kind)
        assertEquals(QuickSortAction.PlacePivot(r.current.state.pivotHome), d.correct)
        // Only slots inside the partition are offered.
        assertEquals(r.current.state.partition.count(), d.options.size)
        // And Try must ask it — it is the insight, not bookkeeping.
        assertTrue(!d.autoInTry)
    }

    @Test
    fun `a wrong classification is refused and the array does not move`() {
        val r = runner(QuickSortDatasets.watch)
        val d = toDecision(r).decision
        val before = r.current.state

        repeat(4) { attempt ->
            val verdict = DecisionValidation.validate(d, QuickSortAction.ClassifyLeft, attempt)
            assertTrue("attempt $attempt was accepted", verdict is Validation.Retry)
        }
        assertEquals(before, r.current.state)
        assertEquals(QuickSortDatasets.watch.values, r.current.state.values)
    }

    @Test
    fun `a wrong pivot placement is refused and the array does not move`() {
        val r = runner(QuickSortDatasets.watch)
        toDecision(r)   // selects the pivot and puts the cursor on the first value
        var guard = 0
        while (guard++ < 64 && r.current.state.scanning) {
            r.apply(toDecision(r).decision.correct)
        }
        val before = r.current.state
        val d = toDecision(r).decision

        d.options.mapNotNull { it.slot }.filter { it != before.pivotHome }.forEach { slot ->
            val verdict = DecisionValidation.validate(
                d,
                QuickSortAction.PlacePivot(slot),
                priorAttempts = 0,
            )
            assertTrue("slot $slot was accepted", verdict is Validation.Retry)
        }
        assertEquals(before, r.current.state)
    }

    @Test
    fun `guidance escalates and Challenge gets a terser clue`() {
        val r = runner(QuickSortDatasets.watch)
        val d = toDecision(r).decision
        assertEquals(3, d.guidance.size)
        assertEquals(3, d.guidance.distinct().size)
        assertNotEquals(d.minimalFeedback, d.guidance.last())
        assertEquals(3, d.hintLadder.size)
    }

    // ── The visual story ─────────────────────────────────────────────────────

    @Test
    fun `the scene shows the pivot, the cursor and the partition's groups`() {
        val r = runner(QuickSortDatasets.watch)
        toDecision(r)
        val scene = QuickSortProjector().project(r.current.state, r.current.events)

        val states = scene.cells.map { it.state }
        assertTrue("no pivot", states.contains(CellState.CANDIDATE))
        assertTrue("nothing being checked", states.contains(CellState.COMPARING))
        assertNotNull("the pivot is not surfaced", scene.badge)
        assertEquals(5, scene.badge!!.value)
        // The left/right/unjudged structure is expressed as groups.
        assertTrue("no partition structure", scene.groups.isNotEmpty())
    }

    @Test
    fun `finished pivots render green while the rest keeps working`() {
        val r = runner(QuickSortDatasets.watch)
        var guard = 0
        while (guard++ < 256 && r.current.state.finalized.isEmpty()) {
            when (val probe = r.probe()) {
                is Probe.Mechanical -> r.apply(probe.action)
                is Probe.Decide -> r.apply(probe.decision.correct)
                is Probe.Terminal -> break
            }
        }
        val scene = QuickSortProjector().project(r.current.state, r.current.events)
        assertTrue(scene.cells.any { it.state == CellState.FINALIZED })
        assertTrue("everything went green at once", scene.cells.any { it.state != CellState.FINALIZED })
    }

    // ── Watch walkthrough ────────────────────────────────────────────────────

    @Test
    fun `the walkthrough narrates the first partition in full and stays short`() {
        val steps = WatchScriptBuilder(
            QuickSortAlgorithm(),
            QuickSortProjector(),
            QuickSortWatchNarrator(),
        ).build(QuickSortDatasets.watch).steps

        assertEquals(WatchStepKind.SETUP, steps.first().kind)
        assertEquals(WatchStepKind.SUMMARY, steps.last().kind)
        assertTrue("no pivot beat", steps.any { it.kind == WatchStepKind.EXAMINE })
        // Both classification outcomes must be seen.
        assertTrue("nothing went left", steps.any { it.kind == WatchStepKind.KEEP })
        assertTrue("nothing went right", steps.any { it.kind == WatchStepKind.SWAP })
        assertTrue("no pivot landing", steps.any { it.kind == WatchStepKind.PASS_COMPLETE })
        assertTrue(steps.any { it.kind == WatchStepKind.INSIGHT })
        assertTrue("walkthrough is ${steps.size} steps", steps.size in 12..28)
    }

    @Test
    fun `the walkthrough carries exactly one unscored checkpoint`() {
        val steps = WatchScriptBuilder(
            QuickSortAlgorithm(),
            QuickSortProjector(),
            QuickSortWatchNarrator(),
        ).build(QuickSortDatasets.watch).steps

        val predictions = steps.filter { it.prediction != null }
        assertEquals(1, predictions.size)
        assertEquals(2, predictions.single().prediction!!.options.size)
    }

    @Test
    fun `the recap states the three-line mental model`() {
        val steps = WatchScriptBuilder(
            QuickSortAlgorithm(),
            QuickSortProjector(),
            QuickSortWatchNarrator(),
        ).build(QuickSortDatasets.watch).steps
        assertEquals(3, steps.last().bullets.size)
    }

    @Test
    fun `both teaching arrays leave two non-empty sides after the first partition`() {
        listOf(QuickSortDatasets.watch, QuickSortDatasets.tryIt).forEach { dataset ->
            val pivot = dataset.values.last()
            val smaller = dataset.values.dropLast(1).count { it <= pivot }
            val larger = dataset.values.dropLast(1).count { it > pivot }
            assertTrue("$dataset has an empty left side", smaller > 0)
            assertTrue("$dataset has an empty right side", larger > 0)
        }
    }

    // ── Saying which part is being solved (ADR-054) ──────────────────────────

    /** Every state the run passes through, in order. */
    private fun statesOf(dataset: Dataset): List<QuickSortState> {
        val r = runner(dataset)
        val out = mutableListOf(r.current.state)
        var guard = 0
        while (guard++ < 2048) {
            when (val probe = r.probe()) {
                is Probe.Mechanical -> r.apply(probe.action)
                is Probe.Decide -> r.apply(probe.decision.correct)
                is Probe.Terminal -> return out
            }
            out += r.current.state
        }
        error("did not terminate")
    }

    @Test
    fun `a pivot landing leaves both halves on screen for one beat`() {
        val split = statesOf(QuickSortDatasets.watch).first { it.showingSplit }
        val s = requireNotNull(split.split)

        // [3, 2, 4]  5  [7, 8, 6] — the first pivot of the teaching array.
        assertEquals(5, s.pivot)
        assertEquals(listOf(3, 2, 4), split.values.slice(s.left))
        assertEquals(5, split.values[s.pivotAt])
        assertEquals(listOf(7, 8, 6), split.values.slice(s.right))

        // The picture divides the array into exactly those three pieces.
        val scene = QuickSortProjector().project(split, emptyList())
        assertEquals(listOf(s.left, s.pivotAt..s.pivotAt, s.right), scene.groups)
        // The pivot is the only finished value, and both halves are still in play.
        assertEquals(
            listOf(CellState.IDLE, CellState.IDLE, CellState.IDLE, CellState.FINALIZED,
                CellState.IDLE, CellState.IDLE, CellState.IDLE),
            scene.cells.sortedBy { it.slot }.map { it.state },
        )
        // And both halves are named for what they are.
        assertEquals(
            listOf("below 5", "above 5"),
            scene.regions.map { it.label },
        )
    }

    @Test
    fun `a partition knows which side of which pivot it is`() {
        val descents = statesOf(QuickSortDatasets.watch)
            .filter { it.active && !it.showingSplit && it.pivotAt == null }
            .map { Triple(it.side, it.parentPivot, it.partitionValues) }
            .distinct()

        // The whole array first, then the values that lost to the 5, then the
        // ones that beat it.
        assertEquals(PartitionSide.WHOLE, descents.first().first)
        assertTrue(
            "the left part of 5 is never taken up",
            descents.any { it.first == PartitionSide.LEFT && it.second == 5 && it.third == listOf(3, 2, 4) },
        )
        assertTrue(
            "the right part of 5 is never taken up",
            descents.any { it.first == PartitionSide.RIGHT && it.second == 5 && it.third == listOf(7, 8, 6) },
        )
    }

    /**
     * The point of ADR-054: while one side is being sorted the other is visibly
     * **waiting**, not finished and not gone. Without this the outline moved and
     * nothing else did, and a learner could not tell which part was live.
     */
    @Test
    fun `while one side is being solved the rest of the array is parked`() {
        val solvingLeft = statesOf(QuickSortDatasets.watch)
            .first { it.side == PartitionSide.LEFT && it.pivotAt != null }
        val scene = QuickSortProjector().project(solvingLeft, emptyList())

        val parked = scene.cells.filter { it.state == CellState.ELIMINATED }.map { it.slot }
        assertEquals("the right half should be waiting", listOf(4, 5, 6), parked.sorted())
        assertTrue(
            "nothing inside the live partition is parked",
            solvingLeft.partition.none { it in parked },
        )
        // Parked is not eliminated, and the legend has to say so.
        assertEquals("Waiting", scene.legendLabels[CellState.ELIMINATED])
    }

    @Test
    fun `the live partition is captioned with the side it is`() {
        val states = statesOf(QuickSortDatasets.watch)
        val labels = states
            .filter { !it.showingSplit && it.active && !it.done }
            .map { QuickSortProjector().project(it, emptyList()).regions.singleOrNull()?.label }
            .distinct()

        assertTrue("the whole array is never named", labels.contains("the whole array"))
        assertTrue("the left part is never named", labels.contains("left of 5"))
        assertTrue("the right part is never named", labels.contains("right of 5"))
    }

    @Test
    fun `the walkthrough names each side exactly once`() {
        val steps = WatchScriptBuilder(
            QuickSortAlgorithm(),
            QuickSortProjector(),
            QuickSortWatchNarrator(),
        ).build(QuickSortDatasets.watch).steps

        assertEquals(
            "the left part should be announced once",
            1,
            steps.count { it.headline.id == NarrationId.QUICK_WATCH_NEXT_LEFT },
        )
        assertEquals(
            "the right part should be announced once",
            1,
            steps.count { it.headline.id == NarrationId.QUICK_WATCH_NEXT_RIGHT },
        )
        // The split beat is the first partition's, and only the first partition's.
        assertEquals(1, steps.count { it.headline.id == NarrationId.QUICK_WATCH_SPLIT })

        // The left part is taken up before the right, the way the array reads.
        val left = steps.indexOfFirst { it.headline.id == NarrationId.QUICK_WATCH_NEXT_LEFT }
        val right = steps.indexOfFirst { it.headline.id == NarrationId.QUICK_WATCH_NEXT_RIGHT }
        val split = steps.indexOfFirst { it.headline.id == NarrationId.QUICK_WATCH_SPLIT }
        assertTrue("the split is shown before either side is entered", split < left)
        assertTrue("the left part comes first", left < right)
    }

    /**
     * "Two smaller arrays" is only said when there are two.
     *
     * Later pivots often leave one side empty — the 4 in `[3, 2, 4]` leaves
     * nothing above it — and claiming two halves there would be false.
     */
    @Test
    fun `the split beat never claims two halves when one side is empty`() {
        val steps = WatchScriptBuilder(
            QuickSortAlgorithm(),
            QuickSortProjector(),
            QuickSortWatchNarrator(),
        ).build(QuickSortDatasets.watch).steps

        val splits = statesOf(QuickSortDatasets.watch).filter { it.showingSplit }
        assertTrue("the teaching array should split lopsidedly somewhere", splits.any {
            it.split!!.left.isEmpty() || it.split!!.right.isEmpty()
        })
        // Exactly one split has both sides, and exactly one beat says so.
        assertEquals(
            splits.count { !it.split!!.left.isEmpty() && !it.split!!.right.isEmpty() && it.finalized.size == 1 },
            steps.count { it.headline.id == NarrationId.QUICK_WATCH_SPLIT },
        )
    }

    @Test
    fun `the split beat does not disturb the sort`() {
        listOf(QuickSortDatasets.watch, QuickSortDatasets.tryIt).forEach { dataset ->
            val end = playPerfectly(dataset).current.state
            assertEquals(dataset.values.sorted(), end.values)
            assertTrue(end.done)
            assertEquals(null, end.split)
        }
    }
}
