package com.ttele.algoking.engine

import com.ttele.algoking.engine.algorithms.binarysearch.BinarySearchAlgorithm
import com.ttele.algoking.engine.algorithms.binarysearch.BinarySearchProjector
import com.ttele.algoking.engine.algorithms.binarysearch.BinarySearchWatchNarrator
import com.ttele.algoking.engine.core.Dataset
import com.ttele.algoking.engine.dataset.BinarySearchDatasets
import com.ttele.algoking.engine.scene.CellState
import com.ttele.algoking.engine.walkthrough.WatchScript
import com.ttele.algoking.engine.walkthrough.WatchScriptBuilder
import com.ttele.algoking.engine.walkthrough.WatchStepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchScriptTest {

    private fun script(dataset: Dataset): WatchScript = WatchScriptBuilder(
        algorithm = BinarySearchAlgorithm(),
        projector = BinarySearchProjector(),
        narrator = BinarySearchWatchNarrator(),
    ).build(dataset)

    @Test
    fun `the teaching array walks the exact beats the lesson promises`() {
        val steps = script(BinarySearchDatasets.watch).steps

        // Three rounds under the canonical lower middle: right, then left, then
        // found on a two-cell range. Both branches, in eleven beats.
        assertEquals(
            listOf(
                WatchStepKind.SETUP,
                WatchStepKind.EXAMINE,
                WatchStepKind.COMPARE,
                WatchStepKind.ELIMINATE,
                WatchStepKind.EXAMINE,
                WatchStepKind.COMPARE,
                WatchStepKind.ELIMINATE,
                WatchStepKind.EXAMINE,
                WatchStepKind.COMPARE,
                WatchStepKind.FOUND,
                WatchStepKind.INSIGHT,
                WatchStepKind.SUMMARY,
            ),
            steps.map { it.kind },
        )
    }

    @Test
    fun `the first middle is 37 and the first comparison is against the target`() {
        val steps = script(BinarySearchDatasets.watch).steps
        val compare = steps.first { it.kind == WatchStepKind.COMPARE }
        val readout = assertNotNull(compare.comparison).let { compare.comparison!! }
        // lo=0, hi=11 -> mid = 0 + (11 - 0) / 2 = 5, which holds 37.
        assertEquals(37, readout.left)
        assertEquals(45, readout.right)
    }

    @Test
    fun `the middle of an even range is the left of the two centre cells`() {
        val steps = script(BinarySearchDatasets.watch).steps
        val midpoints = steps.mapNotNull { it.midpoint }
        assertTrue("watch should pick three middles", midpoints.size >= 3)
        midpoints.forEach { readout ->
            assertEquals(
                "mid must be left + (right - left) / 2",
                readout.lo + (readout.hi - readout.lo) / 2,
                readout.mid,
            )
        }
        // The last round is a two-cell range, which is the one place the lower
        // and upper conventions disagree. mid must land on the left cell.
        val last = midpoints.last()
        assertEquals(2, last.hi - last.lo + 1)
        assertEquals(last.lo, last.mid)
    }

    @Test
    fun `the first elimination removes exactly half the array`() {
        val steps = script(BinarySearchDatasets.watch).steps
        val eliminate = steps.first { it.kind == WatchStepKind.ELIMINATE }
        val gone = eliminate.scene.sequence.cells.filter { it.state == CellState.ELIMINATED }
        // Six of twelve — the number the insight frame quotes.
        assertEquals(6, gone.size)
        assertEquals(listOf(3, 8, 14, 21, 29, 37), gone.map { it.value })

        val alive = eliminate.scene.sequence.cells.filter { it.state != CellState.ELIMINATED }
        assertEquals(listOf(45, 52, 61, 73, 81, 94), alive.map { it.value })
    }

    @Test
    fun `the walkthrough shows both branches`() {
        val steps = script(BinarySearchDatasets.watch).steps
        val eliminations = steps.filter { it.kind == WatchStepKind.ELIMINATE }
        assertEquals(2, eliminations.size)

        // Round 1 discards the low end, round 2 the high end. A learner who only
        // ever watches one side shrink has seen half the algorithm.
        val firstGone = eliminations[0].scene.sequence.cells
            .filter { it.state == CellState.ELIMINATED }.map { it.value }
        val secondGone = eliminations[1].scene.sequence.cells
            .filter { it.state == CellState.ELIMINATED }.map { it.value }
        assertTrue("round 1 should discard the left", firstGone.contains(3))
        assertTrue("round 2 should discard the right", secondGone.contains(94))
    }

    @Test
    fun `every step carries a headline and a distinct scene from its neighbour`() {
        val steps = script(BinarySearchDatasets.watch).steps
        steps.forEach { assertNotNull("step ${it.index} has no headline", it.headline) }

        // No step may exist where nothing at all changed: either the scene moved on,
        // or the step added a comparison readout / closing recap.
        steps.zipWithNext().forEach { (a, b) ->
            val changed = a.scene != b.scene ||
                a.comparison != b.comparison ||
                b.bullets.isNotEmpty() ||
                b.kind == WatchStepKind.INSIGHT
            assertTrue("steps ${a.index}->${b.index} change nothing", changed)
        }
    }

    @Test
    fun `a not-found target ends on NOT_FOUND and still reaches the recap`() {
        val steps = script(Dataset(listOf(4, 9, 15, 21, 28, 35, 42), target = 30)).steps
        assertTrue(steps.any { it.kind == WatchStepKind.NOT_FOUND })
        assertEquals(WatchStepKind.SUMMARY, steps.last().kind)
        assertEquals(4, steps.last().bullets.size)
    }

    @Test
    fun `the script always ends INSIGHT then SUMMARY, whatever the data`() {
        listOf(
            BinarySearchDatasets.watch,
            BinarySearchDatasets.tryIt,
            Dataset(listOf(5, 11, 18), target = 5),
            Dataset(listOf(5, 11, 18), target = 18),
            Dataset(listOf(5, 11, 18), target = 12),
        ).forEach { dataset ->
            val kinds = script(dataset).steps.map { it.kind }
            assertEquals(WatchStepKind.SUMMARY, kinds.last())
            assertEquals(WatchStepKind.INSIGHT, kinds[kinds.lastIndex - 1])
            assertEquals(WatchStepKind.SETUP, kinds.first())
        }
    }
}
