package com.ttele.algoking.engine

import com.ttele.algoking.engine.algorithms.binarysearch.BinarySearchAction
import com.ttele.algoking.engine.algorithms.binarysearch.BinarySearchAlgorithm
import com.ttele.algoking.engine.core.AlgorithmRunner
import com.ttele.algoking.engine.core.Dataset
import com.ttele.algoking.engine.core.Probe
import com.ttele.algoking.engine.dataset.BinarySearchDatasets
import com.ttele.algoking.engine.decision.DecisionKind
import com.ttele.algoking.engine.decision.DecisionValidation
import com.ttele.algoking.engine.decision.Validation
import com.ttele.algoking.engine.event.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule under test: **in Try, a wrong answer is a learning event, not a state
 * transition.** These tests are the thing that stops that regressing.
 */
class DecisionValidationTest {

    /**
     * Any option that is not the correct one.
     *
     * Deliberately derived rather than named: these tests are about what happens
     * to *a wrong choice*, and pinning a direction only couples them to whichever
     * way the authored dataset happens to branch first.
     */
    private fun Probe.Decide<BinarySearchAction>.aWrongAction(): BinarySearchAction =
        decision.options.map { it.action }.first { it != decision.correct }

    private fun runnerAtFirstDecision(dataset: Dataset = BinarySearchDatasets.tryIt) =
        AlgorithmRunner(BinarySearchAlgorithm(), dataset).also {
            // Try answers the "which cell?" decision for the learner, so skip to the
            // half decision — the one Try actually asks.
            while ((it.probe() as? Probe.Decide)?.decision?.kind == DecisionKind.CELL) {
                it.apply((it.probe() as Probe.Decide).decision.correct)
            }
        }

    @Test
    fun `a wrong choice is rejected and never yields an action to apply`() {
        val runner = runnerAtFirstDecision()
        val probe = runner.probe() as Probe.Decide<BinarySearchAction>
        val decision = probe.decision
        // tryIt: mid = 42, target = 28 -> the target is smaller, so KeepLeft.
        assertEquals(BinarySearchAction.KeepLeft, decision.correct)

        val verdict = DecisionValidation.validate(decision, probe.aWrongAction(), 0)
        assertTrue(verdict is Validation.Retry)
        // There is no action on a Retry — the caller has nothing it *could* apply.
        assertTrue(verdict !is Validation.Accept)
    }

    @Test
    fun `the algorithm state is byte-for-byte identical after any number of wrong answers`() {
        val runner = runnerAtFirstDecision()
        val before = runner.current
        val probe = runner.probe() as Probe.Decide<BinarySearchAction>
        val decision = probe.decision

        repeat(5) { attempt ->
            val verdict = DecisionValidation.validate(
                decision,
                probe.aWrongAction(),
                attempt,
            )
            assertTrue("attempt $attempt was accepted", verdict is Validation.Retry)
        }

        assertEquals(before.state, runner.current.state)
        assertEquals(before.index, runner.current.index)
        assertEquals(before.metrics, runner.current.metrics)
    }

    @Test
    fun `guidance escalates and then holds, so a learner is never dead-ended`() {
        val runner = runnerAtFirstDecision()
        val probe = runner.probe() as Probe.Decide<BinarySearchAction>
        val decision = probe.decision

        val levels = (0..5).map { attempt ->
            DecisionValidation.validate(decision, probe.aWrongAction(), attempt)
                as Validation.Retry
        }

        assertEquals(listOf(1, 2, 3, 4, 5, 6), levels.map { it.level })

        // Levels 1..3 are three distinct rungs.
        assertNotEquals(levels[0].guidance, levels[1].guidance)
        assertNotEquals(levels[1].guidance, levels[2].guidance)
        // Past the ladder, the most explicit rung repeats rather than running out.
        assertEquals(levels[2].guidance, levels[3].guidance)
        assertEquals(levels[2].guidance, levels[5].guidance)
        levels.forEach { assertNotNull(it.guidance) }
    }

    @Test
    fun `the correct choice is accepted and carries its own explanation`() {
        val runner = runnerAtFirstDecision()
        val decision = (runner.probe() as Probe.Decide).decision

        val verdict = DecisionValidation.validate(decision, decision.correct, priorAttempts = 3)
        assertTrue(verdict is Validation.Accept)
        assertEquals(decision.correct, (verdict as Validation.Accept).action)
        assertNotNull(verdict.feedback)
    }

    @Test
    fun `only accepted actions advance the run, and the run stays correct`() {
        val runner = runnerAtFirstDecision()
        var guard = 0

        while (guard++ < 32) {
            when (val probe = runner.probe()) {
                // Kept in the hierarchy for algorithms that advance pointers or pass
                // boundaries; Binary Search asks for every beat.
                is Probe.Mechanical -> runner.apply(probe.action)
                is Probe.Terminal -> break
                is Probe.Decide if probe.decision.kind == DecisionKind.CELL ->
                    runner.apply(probe.decision.correct)

                is Probe.Decide -> {
                    val decision = probe.decision
                    val wrong = decision.options
                        .map { it.action }
                        .first { it != decision.correct }

                    // Hammer the wrong answer; nothing may move.
                    val before = runner.current.state
                    repeat(3) { attempt ->
                        val v = DecisionValidation.validate(decision, wrong, attempt)
                        assertTrue(v is Validation.Retry)
                    }
                    assertEquals(before, runner.current.state)

                    // Only the accepted action advances it.
                    val accept = DecisionValidation.validate(decision, decision.correct, 3)
                    runner.apply((accept as Validation.Accept).action)
                }
            }
        }

        val outcome = (runner.probe() as Probe.Terminal).outcome
        // tryIt target 28 sits at index 4.
        assertEquals(Outcome.Found(4), outcome)
        // A learner who guessed wrong ten times still reaches an optimal trace.
        assertEquals(0, runner.current.metrics.wrongDecisions)
    }

    @Test
    fun `whyWrong is present for every wrong option and absent for the right one`() {
        val runner = runnerAtFirstDecision()
        val decision = (runner.probe() as Probe.Decide).decision

        decision.options.map { it.action }.forEach { action ->
            val verdict = DecisionValidation.validate(decision, action, 0)
            if (action == decision.correct) {
                assertTrue(verdict is Validation.Accept)
            } else {
                assertNotNull(
                    "no explanation for $action",
                    (verdict as Validation.Retry).whyWrong,
                )
            }
        }
    }

    @Test
    fun `focus names the cell the learner should be looking at`() {
        val runner = runnerAtFirstDecision()
        val probe = runner.probe() as Probe.Decide<BinarySearchAction>
        val verdict = DecisionValidation.validate(probe.decision, probe.aWrongAction(), 0)
        assertEquals(listOf(runner.current.state.mid), (verdict as Validation.Retry).focus)
    }

    @Test
    fun `a decision with no wrong options still validates cleanly`() {
        // mid == target: FOUND is correct, and the halves are still offered.
        val runner = runnerAtFirstDecision(Dataset(listOf(10, 20, 30), target = 20))
        val decision = (runner.probe() as Probe.Decide).decision
        assertEquals(BinarySearchAction.Found, decision.correct)

        val verdict = DecisionValidation.validate(decision, BinarySearchAction.Found, 0)
        assertTrue(verdict is Validation.Accept)
        assertNull((DecisionValidation.validate(decision, BinarySearchAction.Found, 0) as? Validation.Retry))
    }
}
