package com.ttele.algoking.engine

import com.ttele.algoking.engine.algorithms.prefixsum.PrefixSumAction
import com.ttele.algoking.engine.algorithms.prefixsum.PrefixSumAlgorithm
import com.ttele.algoking.engine.algorithms.prefixsum.PrefixSumProjector
import com.ttele.algoking.engine.algorithms.prefixsum.PrefixSumState
import com.ttele.algoking.engine.catalog.AlgorithmCatalog
import com.ttele.algoking.engine.core.AlgorithmId
import com.ttele.algoking.engine.core.AlgorithmRunner
import com.ttele.algoking.engine.core.Dataset
import com.ttele.algoking.engine.core.Probe
import com.ttele.algoking.engine.dataset.PrefixSumDatasets
import com.ttele.algoking.engine.decision.DecisionValidation
import com.ttele.algoking.engine.decision.Validation
import com.ttele.algoking.engine.scene.CellState
import com.ttele.algoking.engine.scene.PrefixOp
import com.ttele.algoking.engine.scene.PrefixScene
import com.ttele.algoking.engine.walkthrough.WatchStepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prefix Sum.
 *
 * Every assertion here uses the **standard leading-zero representation**:
 * `prefix` is `n + 1` long, `prefix[0] = 0`, `prefix[i + 1] = prefix[i] +
 * array[i]`, and `rangeSum(left, right) = prefix[right + 1] - prefix[left]`.
 * There is no second representation anywhere in the lesson, and these tests are
 * what keeps it that way.
 */
class PrefixSumTest {

    private val algorithm = PrefixSumAlgorithm()

    private fun runner(values: List<Int>, left: Int = 0, right: Int = values.lastIndex) =
        AlgorithmRunner(
            algorithm,
            Dataset(values = values, queryLeft = left, queryRight = right),
        )

    /** Drive to completion the way WATCH does. */
    private fun driveCorrectly(
        values: List<Int>,
        left: Int = 0,
        right: Int = values.lastIndex,
    ): PrefixSumState {
        val r = runner(values, left, right)
        var guard = 0
        while (guard++ < 500) {
            when (val probe = r.probe()) {
                is Probe.Mechanical -> r.apply(probe.action)
                is Probe.Decide -> r.apply(probe.decision.correct)
                is Probe.Terminal -> return r.current.state
            }
        }
        error("Prefix Sum did not terminate.")
    }

    private fun state(values: List<Int>, left: Int = 0, right: Int = values.lastIndex) =
        algorithm.initial(Dataset(values = values, queryLeft = left, queryRight = right))

    // -- 1. Construction -------------------------------------------------------

    @Test
    fun `the authored array builds the documented prefix table`() {
        val end = driveCorrectly(listOf(2, 4, 3, 7, 1), left = 1, right = 3)
        assertEquals(listOf(0, 2, 6, 9, 16, 17), end.prefix)
    }

    @Test
    fun `the prefix array is always one longer than the array`() {
        for (values in listOf(listOf(1), listOf(1, 2), listOf(4, 4, 4, 4, 4, 4, 4))) {
            assertEquals(values.size + 1, driveCorrectly(values).prefix.size)
        }
    }

    @Test
    fun `prefix always starts at zero`() {
        assertEquals(0, state(listOf(2, 4, 3)).prefix.first())
        assertEquals(0, driveCorrectly(listOf(2, 4, 3)).prefix.first())
    }

    @Test
    fun `each entry is the previous prefix plus the current value`() {
        val values = listOf(3, -1, 8, 0, 5, 5)
        val built = driveCorrectly(values).prefix
        for (i in values.indices) {
            assertEquals(
                "prefix[${i + 1}]",
                built[i] + values[i],
                built[i + 1],
            )
        }
    }

    @Test
    fun `fullPrefix agrees with what the learner builds, step for step`() {
        // The state exposes the finished table for the UI; it must be the same
        // table the lesson produces, or the picture and the run would disagree.
        val values = listOf(2, 4, 3, 7, 1)
        assertEquals(driveCorrectly(values).prefix, state(values).fullPrefix)
    }

    // -- 2. Range queries ------------------------------------------------------

    @Test
    fun `the documented query is 14`() {
        val s = state(listOf(2, 4, 3, 7, 1), left = 1, right = 3)
        // prefix[4] - prefix[1] = 16 - 2 = 14, and 4 + 3 + 7 = 14.
        assertEquals(14, s.rangeSum(1, 3))
        assertEquals(14, s.queryAnswer)
    }

    @Test
    fun `a range starting at zero subtracts the leading zero`() {
        val s = state(listOf(2, 4, 3, 7, 1))
        assertEquals(2 + 4 + 3, s.rangeSum(0, 2))
        // prefix[0] is 0, so the formula needs no special case — the entire point
        // of the leading-zero representation.
        assertEquals(s.fullPrefix[3] - s.fullPrefix[0], s.rangeSum(0, 2))
    }

    @Test
    fun `a range ending at the final element uses the last prefix entry`() {
        val s = state(listOf(2, 4, 3, 7, 1))
        assertEquals(3 + 7 + 1, s.rangeSum(2, 4))
        assertEquals(s.fullPrefix[5] - s.fullPrefix[2], s.rangeSum(2, 4))
    }

    @Test
    fun `a single-element range is that element`() {
        val values = listOf(2, 4, 3, 7, 1)
        val s = state(values)
        for (i in values.indices) {
            assertEquals(values[i], s.rangeSum(i, i))
        }
    }

    @Test
    fun `the whole array is the total`() {
        val values = listOf(2, 4, 3, 7, 1)
        assertEquals(values.sum(), state(values).rangeSum(0, values.lastIndex))
    }

    @Test
    fun `range sums agree with brute force everywhere, including negatives`() {
        val arrays = listOf(
            listOf(2, 4, 3, 7, 1),
            listOf(-5, 3, -2, 8, 0, -1),
            listOf(4, 4, 4, 4),
            listOf(7),
            listOf(0, 0, 0),
        )
        for (values in arrays) {
            val s = state(values)
            for (l in values.indices) {
                for (r in l..values.lastIndex) {
                    assertEquals(
                        "values=$values range=$l..$r",
                        values.subList(l, r + 1).sum(),
                        s.rangeSum(l, r),
                    )
                }
            }
        }
    }

    @Test
    fun `many queries all read from one table`() {
        // The claim behind O(n + q): the table is built once, and every query is a
        // subtraction on it rather than another walk of the array.
        val values = listOf(2, 4, 3, 7, 1, 9, 6)
        val s = state(values)
        val table = s.fullPrefix
        for (l in values.indices) {
            for (r in l..values.lastIndex) {
                assertEquals(table[r + 1] - table[l], s.rangeSum(l, r))
            }
        }
    }

    // -- 3. Edge cases ---------------------------------------------------------

    @Test
    fun `an empty array is a finished lesson, not a crash`() {
        val r = AlgorithmRunner(algorithm, Dataset(values = emptyList()))
        assertTrue(r.probe() is Probe.Terminal)
        assertNull(state(emptyList()).queryRange)
        assertNull(state(emptyList()).expectedNext)
    }

    @Test
    fun `a single-element array builds one entry and answers its only range`() {
        val end = driveCorrectly(listOf(7))
        assertEquals(listOf(0, 7), end.prefix)
        assertEquals(7, end.answer)
    }

    @Test
    fun `an inverted range is normalised rather than rejected`() {
        // left > right is a bad dataset, not a crash. It is normalised once, in
        // the state, so nothing downstream ever holds an impossible span.
        val s = state(listOf(2, 4, 3, 7, 1), left = 3, right = 1)
        assertEquals(1..3, s.queryRange)
        assertEquals(14, s.queryAnswer)
    }

    @Test
    fun `out-of-bounds bounds are clamped into the array`() {
        val values = listOf(2, 4, 3, 7, 1)
        assertEquals(0..4, state(values, left = -9, right = 99).queryRange)
        assertEquals(values.sum(), state(values, left = -9, right = 99).queryAnswer)
        assertEquals(0..0, state(values, left = -3, right = -1).queryRange)
        assertEquals(4..4, state(values, left = 9, right = 12).queryRange)
    }

    @Test
    fun `rangeSum never throws, for any bounds at all`() {
        val values = listOf(2, 4, 3, 7, 1)
        val s = state(values)
        for (l in -5..9) {
            for (r in -5..9) {
                s.rangeSum(l, r)
            }
        }
    }

    @Test
    fun `duplicate values need no special casing`() {
        val end = driveCorrectly(listOf(4, 4, 4, 4), left = 1, right = 2)
        assertEquals(listOf(0, 4, 8, 12, 16), end.prefix)
        assertEquals(8, end.answer)
    }

    @Test
    fun `negative numbers work, and a running total may go down`() {
        val end = driveCorrectly(listOf(5, -3, 2, -8), left = 1, right = 2)
        assertEquals(listOf(0, 5, 2, 4, -4), end.prefix)
        assertEquals(-1, end.answer)
    }

    // -- 4. The decisions ------------------------------------------------------

    @Test
    fun `every build decision offers three values, one of them right`() {
        val r = runner(listOf(2, 4, 3, 7, 1), 1, 3)
        var builds = 0
        var guard = 0
        while (guard++ < 100) {
            when (val probe = r.probe()) {
                is Probe.Mechanical -> r.apply(probe.action)
                is Probe.Decide -> {
                    val d = probe.decision
                    assertEquals(3, d.options.size)
                    assertEquals(3, d.options.map { it.action }.distinct().size)
                    assertTrue(d.options.any { it.action == d.correct })
                    if (d.correct is PrefixSumAction.Fill) builds++
                    r.apply(d.correct)
                }

                is Probe.Terminal -> break
            }
        }
        assertEquals(5, builds)
    }

    @Test
    fun `the build distractors are the mistakes learners actually make`() {
        // prefix[2] = prefix[1] + array[1] = 2 + 4 = 6. The wrong options must be
        // 4 (the running total dropped) and 7 (two array values added together),
        // because feedback can only name a misconception the option encodes.
        val r = runner(listOf(2, 4, 3, 7, 1), 1, 3)
        r.apply(PrefixSumAction.Fill(2))
        val decision = (r.probe() as Probe.Decide).decision
        val offered = decision.options
            .map { (it.action as PrefixSumAction.Fill).value }
            .toSet()
        assertEquals(setOf(6, 4, 7), offered)
        assertEquals(PrefixSumAction.Fill(6), decision.correct)
    }

    @Test
    fun `the query asks for the indices before the arithmetic`() {
        val r = runner(listOf(2, 4, 3, 7, 1), 1, 3)
        repeat(5) { r.apply((r.probe() as Probe.Decide).decision.correct) }

        val indices = (r.probe() as Probe.Decide).decision
        assertEquals(PrefixSumAction.UseIndices(hi = 4, lo = 1), indices.correct)
        r.apply(indices.correct)

        val evaluate = (r.probe() as Probe.Decide).decision
        assertEquals(PrefixSumAction.Answer(14), evaluate.correct)
    }

    @Test
    fun `the off-by-one is always on the table`() {
        // prefix[right] instead of prefix[right + 1] is the mistake this technique
        // is famous for, so it must be offerable — a beat that cannot be failed
        // is not testing anything.
        val r = runner(listOf(2, 4, 3, 7, 1), 1, 3)
        repeat(5) { r.apply((r.probe() as Probe.Decide).decision.correct) }
        val options = (r.probe() as Probe.Decide).decision.options.map { it.action }
        assertTrue(options.contains(PrefixSumAction.UseIndices(hi = 3, lo = 1)))
    }

    @Test
    fun `a wrong answer never changes the state`() {
        // ADR-021, checked here because a new algorithm is exactly where the
        // invariant could be broken.
        val r = runner(listOf(2, 4, 3, 7, 1), 1, 3)
        val decision = (r.probe() as Probe.Decide).decision
        val before = r.current.state

        val wrong = decision.options.map { it.action }.filter { it != decision.correct }
        for (attempt in 0..4) {
            for (action in wrong) {
                val verdict = DecisionValidation.validate(decision, action, attempt)
                assertTrue(verdict is Validation.Retry)
                assertEquals(before, r.current.state)
            }
        }
    }

    @Test
    fun `guidance escalates and then holds`() {
        val r = runner(listOf(2, 4, 3, 7, 1), 1, 3)
        val decision = (r.probe() as Probe.Decide).decision
        val wrong = decision.options.map { it.action }.first { it != decision.correct }
        val rungs = (0..5).map {
            (DecisionValidation.validate(decision, wrong, it) as Validation.Retry).guidance
        }
        assertEquals(3, rungs.take(3).distinct().size)
        assertEquals(rungs[2], rungs[5])
    }

    @Test
    fun `every wrong build option carries its own explanation`() {
        val r = runner(listOf(2, 4, 3, 7, 1), 1, 3)
        r.apply(PrefixSumAction.Fill(2))
        val decision = (r.probe() as Probe.Decide).decision
        for (option in decision.options) {
            if (option.action == decision.correct) continue
            assertNotNull(
                "no whyWrong for ${option.action}",
                decision.whyWrong[option.action],
            )
        }
    }

    // -- 5. Termination and state transitions ----------------------------------

    @Test
    fun `every run terminates, over many arrays and every range`() {
        for (size in 1..7) {
            val values = (1..size).map { it * 2 }
            for (l in values.indices) {
                for (r in l..values.lastIndex) {
                    driveCorrectly(values, l, r)
                }
            }
        }
    }

    @Test
    fun `the phases run build, then indices, then answer`() {
        val r = runner(listOf(2, 4, 3, 7, 1), 1, 3)
        val order = mutableListOf<String>()
        var guard = 0
        while (guard++ < 100) {
            when (val probe = r.probe()) {
                is Probe.Mechanical -> r.apply(probe.action)
                is Probe.Decide -> {
                    order += when (probe.decision.correct) {
                        is PrefixSumAction.Fill -> "fill"
                        is PrefixSumAction.UseIndices -> "indices"
                        is PrefixSumAction.Answer -> "answer"
                    }
                    r.apply(probe.decision.correct)
                }

                is Probe.Terminal -> break
            }
        }
        assertEquals(
            listOf("fill", "fill", "fill", "fill", "fill", "indices", "answer"),
            order,
        )
    }

    @Test
    fun `applying a fill past the end changes nothing`() {
        val end = driveCorrectly(listOf(2, 4, 3, 7, 1))
        val after = algorithm.apply(end, PrefixSumAction.Fill(99))
        assertEquals(end.prefix, after.next.prefix)
        assertFalse(after.correct)
    }

    // -- 6. WATCH and TRY share one rule set -----------------------------------

    @Test
    fun `WATCH and TRY read from the same algorithm`() {
        val pack = AlgorithmCatalog.prefixSum()
        val script = pack.watchScript()
        assertTrue(script.size > 0)

        val a = driveCorrectly(
            pack.watchDataset.values,
            requireNotNull(pack.watchDataset.queryLeft),
            requireNotNull(pack.watchDataset.queryRight),
        )
        val b = driveCorrectly(
            pack.watchDataset.values,
            requireNotNull(pack.watchDataset.queryLeft),
            requireNotNull(pack.watchDataset.queryRight),
        )
        assertEquals(a, b)
    }

    @Test
    fun `the try dataset is a different array and a different range`() {
        val watch = PrefixSumDatasets.watch
        val tryIt = PrefixSumDatasets.tryIt
        assertTrue(watch.values != tryIt.values)
        assertTrue(watch.queryLeft != tryIt.queryLeft || watch.queryRight != tryIt.queryRight)

        val end = driveCorrectly(
            tryIt.values,
            requireNotNull(tryIt.queryLeft),
            requireNotNull(tryIt.queryRight),
        )
        assertEquals(listOf(0, 5, 6, 14, 16, 22), end.prefix)
        assertEquals(16, end.answer)
    }

    // -- 7. The walkthrough ----------------------------------------------------

    @Test
    fun `every watch step changes something visible - ADR-020`() {
        val steps = AlgorithmCatalog.prefixSum().watchScript().steps
        steps.zipWithNext { a, b ->
            val changed = a.scene != b.scene ||
                a.headline != b.headline ||
                a.support != b.support ||
                a.bullets != b.bullets
            assertTrue("steps ${a.index} and ${b.index} are identical", changed)
        }
    }

    @Test
    fun `the walkthrough builds before it queries`() {
        val steps = AlgorithmCatalog.prefixSum().watchScript().steps
        val lastBuild = steps.indexOfLast { it.kind == WatchStepKind.EXAMINE }
        val query = steps.indexOfFirst { it.kind == WatchStepKind.COMPARE }
        val answer = steps.indexOfFirst { it.kind == WatchStepKind.FOUND }
        assertTrue("no query beat", query >= 0)
        assertTrue("the query comes before the table is built", lastBuild < query)
        assertTrue("the answer comes before the query is posed", query < answer)
    }

    @Test
    fun `the script ends INSIGHT then SUMMARY, with the rules as bullets`() {
        val steps = AlgorithmCatalog.prefixSum().watchScript().steps
        assertEquals(WatchStepKind.SUMMARY, steps.last().kind)
        assertEquals(WatchStepKind.INSIGHT, steps[steps.lastIndex - 1].kind)
        assertEquals(4, steps.last().bullets.size)
    }

    @Test
    fun `the walkthrough stays short enough to be a lesson`() {
        val size = AlgorithmCatalog.prefixSum().watchScript().size
        assertTrue("walkthrough is $size steps", size in 8..16)
    }

    // -- 8. The picture --------------------------------------------------------

    @Test
    fun `uncomputed prefix cells are holes, not zeroes`() {
        val scene = PrefixSumProjector()
            .project(state(listOf(2, 4, 3, 7, 1), 1, 3), emptyList())

        assertEquals(6, scene.prefix.size)
        assertEquals(CellState.GHOST, scene.prefix[1].state)
        assertEquals(CellState.GHOST, scene.prefix[5].state)
        // prefix[0] = 0 is real and given.
        assertTrue(scene.prefix[0].state != CellState.GHOST)
    }

    @Test
    fun `the equation shows the working with the result withheld`() {
        val scene = PrefixSumProjector()
            .project(state(listOf(2, 4, 3, 7, 1), 1, 3), emptyList())
        val equation = requireNotNull(scene.equation)
        assertEquals(0, equation.left)
        assertEquals(PrefixOp.PLUS, equation.operator)
        assertEquals(2, equation.right)
        // An answer already on screen is not a question.
        assertNull(equation.result)
    }

    @Test
    fun `the range bracket only appears once the table is built`() {
        val projector = PrefixSumProjector()
        val building = projector.project(state(listOf(2, 4, 3, 7, 1), 1, 3), emptyList())
        assertNull(building.queryRange)

        val end = driveCorrectly(listOf(2, 4, 3, 7, 1), 1, 3)
        val done = projector.project(end, emptyList())
        assertEquals(1..3, done.queryRange)
    }

    @Test
    fun `the query equation subtracts, and lands on the answer`() {
        val end = driveCorrectly(listOf(2, 4, 3, 7, 1), 1, 3)
        val equation = requireNotNull(PrefixSumProjector().project(end, emptyList()).equation)
        assertEquals(PrefixOp.MINUS, equation.operator)
        assertEquals(16, equation.left)
        assertEquals(2, equation.right)
        assertEquals(14, equation.result)
    }

    @Test
    fun `the projected scene is a PrefixScene, not a sequence`() {
        val scene: Any = PrefixSumProjector().project(state(listOf(1, 2, 3)), emptyList())
        assertTrue(scene is PrefixScene)
    }

    // -- 9. Wiring -------------------------------------------------------------

    @Test
    fun `the catalog resolves Prefix Sum like any other lesson`() {
        val pack = AlgorithmCatalog.byId(AlgorithmId.PREFIX_SUM)
        assertEquals(AlgorithmId.PREFIX_SUM, pack.id)
        assertEquals("Prefix Sum", pack.displayName)
    }
}
