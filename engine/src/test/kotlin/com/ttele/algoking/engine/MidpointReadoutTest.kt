package com.ttele.algoking.engine

import com.ttele.algoking.engine.algorithms.binarysearch.BinarySearchAlgorithm
import com.ttele.algoking.engine.algorithms.binarysearch.BinarySearchProjector
import com.ttele.algoking.engine.algorithms.binarysearch.BinarySearchWatchNarrator
import com.ttele.algoking.engine.dataset.BinarySearchDatasets
import com.ttele.algoking.engine.scene.SequenceScene
import com.ttele.algoking.engine.walkthrough.WatchScriptBuilder
import com.ttele.algoking.engine.walkthrough.WatchStepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Watch must never present `mid` as a number that simply appeared.
 *
 * These assertions are about honesty rather than layout: the equation the screen
 * shows has to be the equation the algorithm evaluated, and the positions it
 * names have to be positions the learner can see on the array.
 */
class MidpointReadoutTest {

    private val steps = WatchScriptBuilder(
        algorithm = BinarySearchAlgorithm(),
        projector = BinarySearchProjector(),
        narrator = BinarySearchWatchNarrator(),
    ).build(BinarySearchDatasets.watch).steps

    @Test
    fun `every step that picks a middle explains how it got there`() {
        val examines = steps.filter { it.kind == WatchStepKind.EXAMINE }
        assertTrue("Watch should examine a middle at least twice", examines.size >= 2)
        examines.forEach { step ->
            assertNotNull(
                "EXAMINE step ${step.index} chose a middle without showing the arithmetic",
                step.midpoint,
            )
        }
    }

    @Test
    fun `the equation on screen is the one the engine evaluated`() {
        steps.mapNotNull { it.midpoint }.forEach { readout ->
            assertEquals(
                "mid must equal left + (right - left) / 2 — the canonical formula",
                readout.lo + (readout.hi - readout.lo) / 2,
                readout.mid,
            )
            assertTrue(readout.mid in readout.lo..readout.hi)
        }
    }

    @Test
    fun `the positions the equation names are visible on the array`() {
        steps.filter { it.midpoint != null }.forEach { step ->
            val readout = step.midpoint!!
            val scene = step.scene as SequenceScene

            // Indices are drawn, so "0 + (11 - 0) / 2" names numbers the learner
            // can point at rather than numbers only the engine knows.
            assertTrue(
                "step ${step.index} computes an index without showing the indices",
                scene.showIndices,
            )

            // Each term of the equation sits over the cell it refers to.
            fun slotOf(label: String) =
                scene.pointers.firstOrNull { it.label == label }?.slot
            assertEquals("left should point at the low bound", readout.lo, slotOf("left"))
            assertEquals("right should point at the high bound", readout.hi, slotOf("right"))
            assertEquals("mid should point at the computed middle", readout.mid, slotOf("mid"))
        }
    }

    @Test
    fun `the bounds are named after the halves they bound`() {
        val labels = steps
            .mapNotNull { it.scene as? SequenceScene }
            .flatMap { it.pointers }
            .map { it.label }
            .toSet()
        assertTrue("expected left/right/mid, got $labels", labels.containsAll(setOf("left", "right", "mid")))
        assertTrue("lo/hi are engine vocabulary and must not reach the screen: $labels",
            labels.none { it == "lo" || it == "hi" })
    }

    @Test
    fun `span and exactness describe the range honestly`() {
        steps.mapNotNull { it.midpoint }.forEach { readout ->
            assertEquals(readout.hi - readout.lo + 1, readout.span)
            assertEquals(readout.span % 2 == 1, readout.exact)
        }
    }
}
