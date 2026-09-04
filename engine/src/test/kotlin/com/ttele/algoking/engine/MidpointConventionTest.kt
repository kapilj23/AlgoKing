package com.ttele.algoking.engine

import com.ttele.algoking.engine.algorithms.binarysearch.BinarySearchAction
import com.ttele.algoking.engine.algorithms.binarysearch.BinarySearchAlgorithm
import com.ttele.algoking.engine.algorithms.binarysearch.BinarySearchState
import com.ttele.algoking.engine.core.AlgorithmRunner
import com.ttele.algoking.engine.core.Dataset
import com.ttele.algoking.engine.core.Probe
import com.ttele.algoking.engine.event.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The canonical midpoint convention — ARCHITECTURE.md §4.4.
 *
 * `mid = left + (right - left) / 2`, the **lower** middle, with `left = mid + 1`
 * / `right = mid - 1` on an inclusive range.
 *
 * This file exists because the convention is only observable on an even-sized
 * range, which makes it exactly the kind of rule that rots silently. Everything
 * downstream — the Watch trace, the Challenge's mid gate, the rewarded hint,
 * Code Reveal — is authored against these numbers.
 */
class MidpointConventionTest {

    private fun state(lo: Int, hi: Int) = BinarySearchState(
        values = List(hi + 1) { it * 10 },
        target = 0,
        lo = lo,
        hi = hi,
        mid = null,
        foundAt = null,
        exhausted = false,
    )

    // ── The formula ───────────────────────────────────────────────────────────

    @Test
    fun `the midpoint of an even range takes the left of the two centres`() {
        // 12 positions. Upper middle would say 6; the canonical answer is 5.
        assertEquals(5, state(lo = 0, hi = 11).middleOfRange)
    }

    @Test
    fun `the midpoint of an odd range is the exact centre`() {
        assertEquals(5, state(lo = 0, hi = 10).middleOfRange)
    }

    @Test
    fun `the midpoint is measured from the left boundary, not from zero`() {
        // lo = 4, hi = 11 -> 4 + (11 - 4) / 2 = 4 + 3 = 7.
        assertEquals(7, state(lo = 4, hi = 11).middleOfRange)
    }

    @Test
    fun `a two-element range takes the first`() {
        // The case that distinguishes the two conventions. Upper says 1.
        assertEquals(0, state(lo = 0, hi = 1).middleOfRange)
        assertEquals(6, state(lo = 6, hi = 7).middleOfRange)
    }

    @Test
    fun `a single-element range is its own middle`() {
        assertEquals(0, state(lo = 0, hi = 0).middleOfRange)
        assertEquals(9, state(lo = 9, hi = 9).middleOfRange)
    }

    @Test
    fun `the midpoint always lies inside the range it was computed from`() {
        (0..40).forEach { lo ->
            (lo..40).forEach { hi ->
                val mid = state(lo, hi).middleOfRange
                assertTrue("mid $mid escaped $lo..$hi", mid in lo..hi)
            }
        }
    }

    @Test
    fun `the formula is the overflow-safe form of the same value`() {
        // left + (right - left) / 2 == (left + right) / 2 for every non-negative
        // range, and only the former survives large indices.
        (0..60).forEach { lo ->
            (lo..60).forEach { hi ->
                assertEquals((lo + hi) / 2, state(lo, hi).middleOfRange)
            }
        }
    }

    // ── The boundary transitions ──────────────────────────────────────────────

    /** Answer the "which cell?" gate, so the half decision is the live one. */
    private fun runnerAtHalfDecision(
        values: List<Int>,
        target: Int,
    ): AlgorithmRunner<BinarySearchState, BinarySearchAction> {
        val runner = AlgorithmRunner(BinarySearchAlgorithm(), Dataset(values, target))
        runner.apply((runner.probe() as Probe.Decide<BinarySearchAction>).decision.correct)
        return runner
    }

    @Test
    fun `a target above the middle moves left to mid plus one`() {
        val values = (0..11).map { it * 10 }
        val runner = runnerAtHalfDecision(values, target = 90)
        val before = runner.current.state as BinarySearchState
        val mid = before.mid!!
        assertEquals(5, mid)

        runner.apply(BinarySearchAction.KeepRight)
        val after = runner.current.state as BinarySearchState
        assertEquals("left must become mid + 1", mid + 1, after.lo)
        assertEquals("right must not move", before.hi, after.hi)
    }

    @Test
    fun `a target below the middle moves right to mid minus one`() {
        val values = (0..11).map { it * 10 }
        val runner = runnerAtHalfDecision(values, target = 10)
        val before = runner.current.state as BinarySearchState
        val mid = before.mid!!

        runner.apply(BinarySearchAction.KeepLeft)
        val after = runner.current.state as BinarySearchState
        assertEquals("right must become mid - 1", mid - 1, after.hi)
        assertEquals("left must not move", before.lo, after.lo)
    }

    @Test
    fun `a target equal to the middle terminates as found`() {
        val values = (0..11).map { it * 10 }
        val runner = runnerAtHalfDecision(values, target = 50)
        val mid = (runner.current.state as BinarySearchState).mid!!

        runner.apply(BinarySearchAction.Found)
        assertEquals(Outcome.Found(mid), (runner.probe() as Probe.Terminal).outcome)
    }

    // ── The whole convention, end to end ──────────────────────────────────────

    @Test
    fun `the authored Watch trace is exactly the one the lesson claims`() {
        val runner = AlgorithmRunner(
            BinarySearchAlgorithm(),
            com.ttele.algoking.engine.dataset.BinarySearchDatasets.watch,
        )
        val midsSeen = mutableListOf<Int>()
        var guard = 0
        while (guard++ < 40) {
            when (val probe = runner.probe()) {
                is Probe.Terminal -> break
                is Probe.Decide -> {
                    runner.apply(probe.decision.correct)
                    (runner.current.state as BinarySearchState).mid?.let {
                        if (midsSeen.lastOrNull() != it) midsSeen += it
                    }
                }

                is Probe.Mechanical -> runner.apply(probe.action)
            }
        }
        // 37 at slot 5, then 61 at slot 8, then 45 at slot 6 — right, then left,
        // then found on a two-cell range. Documented in BinarySearchDatasets.
        assertEquals(listOf(5, 8, 6), midsSeen)
    }

    @Test
    fun `every index of every size is reachable under this convention`() {
        (1..40).forEach { size ->
            val values = List(size) { it * 3 }
            values.forEachIndexed { index, target ->
                val runner = AlgorithmRunner(BinarySearchAlgorithm(), Dataset(values, target))
                val trace = runner.runToCompletion()
                val outcome = trace.frames.flatMap { it.events }
                    .filterIsInstance<com.ttele.algoking.engine.event.VizEvent.Terminal>()
                    .last().outcome
                assertEquals("size $size, index $index", Outcome.Found(index), outcome)
            }
        }
    }
}
