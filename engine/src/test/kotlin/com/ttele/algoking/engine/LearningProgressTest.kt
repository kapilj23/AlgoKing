package com.ttele.algoking.engine

import com.ttele.algoking.engine.catalog.AlgorithmCatalog
import com.ttele.algoking.engine.core.AlgorithmId
import com.ttele.algoking.engine.progress.AlgorithmProgress
import com.ttele.algoking.engine.progress.LearningProgress
import com.ttele.algoking.engine.progress.ProgressCodec
import com.ttele.algoking.engine.progress.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Learning progress.
 *
 * The percentage is derived, never stored, so these tests are the specification:
 * if the table below and the code ever disagree, the code is wrong.
 */
class LearningProgressTest {

    // ── The derivation table ──────────────────────────────────────────────────

    @Test
    fun `nothing completed is zero percent`() {
        assertEquals(0, AlgorithmProgress(false, false, false).percent)
    }

    @Test
    fun `watch alone is a third`() {
        assertEquals(33, AlgorithmProgress(true, false, false).percent)
    }

    @Test
    fun `watch and try are two thirds`() {
        assertEquals(66, AlgorithmProgress(true, true, false).percent)
    }

    @Test
    fun `all three are complete`() {
        assertEquals(100, AlgorithmProgress(true, true, true).percent)
    }

    @Test
    fun `the percentage only ever takes one of four values`() {
        val seen = mutableSetOf<Int>()
        for (w in listOf(false, true)) {
            for (t in listOf(false, true)) {
                for (c in listOf(false, true)) {
                    seen += AlgorithmProgress(w, t, c).percent
                }
            }
        }
        // Never 50%, never 25%, never a number derived from taps or time.
        assertEquals(setOf(0, 33, 66, 100), seen)
    }

    @Test
    fun `mastered means all three, not merely opened`() {
        assertFalse(AlgorithmProgress(true, true, false).mastered)
        assertTrue(AlgorithmProgress(true, true, true).mastered)
    }

    // ── Progress can never go backwards ───────────────────────────────────────

    @Test
    fun `completing a stage twice changes nothing`() {
        val once = AlgorithmProgress.NONE.complete(Stage.WATCH)
        assertEquals(once, once.complete(Stage.WATCH))
    }

    @Test
    fun `practising a mastered algorithm again keeps it at a hundred`() {
        var p = AlgorithmProgress.NONE
            .complete(Stage.WATCH)
            .complete(Stage.TRY)
            .complete(Stage.CHALLENGE)
        // Three more practice runs. Progress is stage completion, not a score.
        repeat(3) { p = p.complete(Stage.CHALLENGE) }
        assertEquals(100, p.percent)
    }

    @Test
    fun `a failed retry cannot take a stage away`() {
        // There is no API to un-complete a stage, which is the point: the model
        // makes "reset progress on failure" unrepresentable rather than merely
        // discouraged.
        val afterWatch = AlgorithmProgress.NONE.complete(Stage.WATCH)
        assertEquals(33, afterWatch.percent)
        assertEquals(33, afterWatch.complete(Stage.WATCH).percent)
    }

    // ── Resume ────────────────────────────────────────────────────────────────

    @Test
    fun `the next stage is the first one not finished`() {
        assertEquals(Stage.WATCH, AlgorithmProgress.NONE.nextStage)
        assertEquals(Stage.TRY, AlgorithmProgress(true, false, false).nextStage)
        assertEquals(Stage.CHALLENGE, AlgorithmProgress(true, true, false).nextStage)
        assertNull(AlgorithmProgress(true, true, true).nextStage)
    }

    // ── Every algorithm is independent ────────────────────────────────────────

    @Test
    fun `one algorithm's progress never touches another's`() {
        val progress = LearningProgress.EMPTY
            .complete(AlgorithmId.BINARY_SEARCH, Stage.WATCH)
            .complete(AlgorithmId.BINARY_SEARCH, Stage.TRY)
            .complete(AlgorithmId.BINARY_SEARCH, Stage.CHALLENGE)
            .complete(AlgorithmId.BUBBLE_SORT, Stage.WATCH)
            .complete(AlgorithmId.BUBBLE_SORT, Stage.TRY)
            .complete(AlgorithmId.STACK, Stage.WATCH)

        assertEquals(100, progress[AlgorithmId.BINARY_SEARCH].percent)
        assertEquals(66, progress[AlgorithmId.BUBBLE_SORT].percent)
        assertEquals(33, progress[AlgorithmId.STACK].percent)
        assertEquals(0, progress[AlgorithmId.QUEUE].percent)
        assertEquals(0, progress[AlgorithmId.MERGE_SORT].percent)
    }

    @Test
    fun `an untouched algorithm reads as zero rather than missing`() {
        for (id in AlgorithmId.entries) {
            assertEquals(0, LearningProgress.EMPTY[id].percent)
            assertFalse(LearningProgress.EMPTY[id].started)
        }
    }

    @Test
    fun `the model covers every algorithm in the catalog`() {
        // A new lesson gets progress for free — there is no per-algorithm code to
        // remember to write.
        for (id in AlgorithmId.entries) {
            assertEquals(id, AlgorithmCatalog.byId(id).id)
            assertEquals(Stage.WATCH, LearningProgress.EMPTY[id].nextStage)
        }
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Test
    fun `progress survives a round trip through storage`() {
        val progress = LearningProgress.EMPTY
            .complete(AlgorithmId.QUICK_SORT, Stage.WATCH)
            .complete(AlgorithmId.QUICK_SORT, Stage.TRY)
            .complete(AlgorithmId.QUEUE, Stage.WATCH)

        val restored = ProgressCodec.decode(ProgressCodec.encode(progress))

        assertEquals(66, restored[AlgorithmId.QUICK_SORT].percent)
        assertEquals(33, restored[AlgorithmId.QUEUE].percent)
        assertEquals(0, restored[AlgorithmId.STACK].percent)
    }

    @Test
    fun `only completed stages are written`() {
        val progress = LearningProgress.EMPTY.complete(AlgorithmId.STACK, Stage.WATCH)
        assertEquals(setOf("STACK:WATCH"), ProgressCodec.encode(progress))
    }

    @Test
    fun `nothing completed writes nothing`() {
        assertTrue(ProgressCodec.encode(LearningProgress.EMPTY).isEmpty())
    }

    @Test
    fun `a key from an older install is ignored, not fatal`() {
        val restored = ProgressCodec.decode(
            setOf(
                "BINARY_SEARCH:WATCH",
                "HEAP_SORT:WATCH",      // an algorithm that does not exist
                "BUBBLE_SORT:MASTER",   // a stage that was removed
                "nonsense",
            ),
        )
        assertEquals(33, restored[AlgorithmId.BINARY_SEARCH].percent)
        assertEquals(0, restored[AlgorithmId.BUBBLE_SORT].percent)
    }

    @Test
    fun `restoring twice is the same as restoring once`() {
        val keys = setOf("MERGE_SORT:WATCH", "MERGE_SORT:TRY", "MERGE_SORT:WATCH")
        assertEquals(66, ProgressCodec.decode(keys)[AlgorithmId.MERGE_SORT].percent)
    }
}
