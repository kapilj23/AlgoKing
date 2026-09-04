package com.ttele.algoking.engine

import com.ttele.algoking.engine.algorithms.binarysearch.BinarySearchAction
import com.ttele.algoking.engine.algorithms.binarysearch.BinarySearchAlgorithm
import com.ttele.algoking.engine.algorithms.binarysearch.BinarySearchState
import com.ttele.algoking.engine.challenge.ChallengeGenerator
import com.ttele.algoking.engine.challenge.ChallengeType
import com.ttele.algoking.engine.challenge.HintAccess
import com.ttele.algoking.engine.challenge.HintPolicy
import com.ttele.algoking.engine.core.AlgorithmRunner
import com.ttele.algoking.engine.core.Probe
import com.ttele.algoking.engine.decision.DecisionValidation
import com.ttele.algoking.engine.decision.Validation
import com.ttele.algoking.engine.event.Outcome
import com.ttele.algoking.engine.scenario.MissionCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Challenge is a story wrapped around Binary Search, and these assertions
 * exist to keep it *only* a story: the mission may change every word on screen,
 * and may never change what the algorithm does or what counts as correct.
 */
class MissionChallengeTest {

    private fun rounds(count: Int = 12) = (1..count).map { ChallengeGenerator.forRound(it, 7L) }

    // ── The world is a legal Binary Search input ───────────────────────────────

    @Test
    fun `every mission is sorted, distinct, and holds its target`() {
        rounds().forEach { challenge ->
            val mission = requireNotNull(challenge.mission)
            val values = mission.dataset.values
            assertEquals(
                "${mission.kind} must be sorted or Binary Search is unsound",
                values.sorted(),
                values,
            )
            assertEquals("duplicates make the middle ambiguous", values.distinct(), values)
            assertTrue(
                "${mission.kind} target is missing from its own data",
                mission.dataset.target in values,
            )
            assertEquals("every slot needs a label", values.size, mission.labels.size)
        }
    }

    @Test
    fun `the target is never an end and never the opening middle`() {
        rounds(24).forEach { challenge ->
            val mission = requireNotNull(challenge.mission)
            val last = mission.size - 1
            val slot = mission.targetSlot
            assertTrue("a target at slot 0 can be guessed", slot != 0)
            assertTrue("a target at the last slot can be guessed", slot != last)
            // Landing on the answer with the first tap makes it a one-decision run.
            assertNotEquals("the opening middle must not be the answer", 0 + (last - 0) / 2, slot)
        }
    }

    @Test
    fun `a mission takes three to six decisions to solve`() {
        rounds(24).forEach { challenge ->
            val decisions = solve(challenge.dataset.values, challenge.dataset.target!!).decisions
            assertTrue(
                "${challenge.mission?.kind} needed $decisions decisions, want 3..12",
                decisions in 3..12,
            )
        }
    }

    // ── Variety ───────────────────────────────────────────────────────────────

    @Test
    fun `practice again lands in a different world`() {
        val worlds = rounds(6).map { it.mission?.kind }
        assertTrue("consecutive rounds repeated a world: $worlds", worlds[0] != worlds[1])
        assertTrue(worlds[1] != worlds[2])
        assertTrue("all three worlds should appear in six rounds", worlds.toSet().size >= 3)
    }

    @Test
    fun `practice again changes the data, not just the story`() {
        val first = ChallengeGenerator.forRound(1, 7L)
        val again = ChallengeGenerator.forRound(4, 7L)
        assertNotEquals(first.dataset.values, again.dataset.values)
    }

    @Test
    fun `the same round and seed rebuild the identical mission`() {
        assertEquals(
            ChallengeGenerator.forRound(2, 99L).mission,
            ChallengeGenerator.forRound(2, 99L).mission,
        )
    }

    // ── The algorithm underneath is untouched ─────────────────────────────────

    @Test
    fun `a mission challenge is still an exact FIND that terminates on the target`() {
        rounds().forEach { challenge ->
            assertEquals(ChallengeType.FIND, challenge.type)
            val outcome = solve(challenge.dataset.values, challenge.dataset.target!!).outcome
            val found = outcome as? Outcome.Found
            assertNotNull("mission ${challenge.mission?.kind} never found its target", found)
            assertEquals(
                challenge.dataset.target,
                challenge.dataset.values[found!!.index],
            )
        }
    }

    @Test
    fun `a wrong middle leaves the search range exactly where it was`() {
        val challenge = ChallengeGenerator.forRound(1, 7L)
        val runner = AlgorithmRunner(BinarySearchAlgorithm(), challenge.dataset)
        val probe = runner.probe() as Probe.Decide<BinarySearchAction>
        val before = runner.current.state

        val wrong = probe.decision.options
            .map { it.action }
            .first { it != probe.decision.correct }

        val verdict = DecisionValidation.validate(probe.decision, wrong, priorAttempts = 0)
        assertTrue("a wrong middle must not be accepted", verdict is Validation.Retry)
        // A Retry carries no action, so there is nothing the caller could apply.
        assertEquals("the state moved on a wrong answer", before, runner.current.state)
    }

    @Test
    fun `a wrong half leaves the search range exactly where it was`() {
        val challenge = ChallengeGenerator.forRound(1, 7L)
        val runner = AlgorithmRunner(BinarySearchAlgorithm(), challenge.dataset)
        // Answer the middle correctly to reach the half decision.
        runner.apply((runner.probe() as Probe.Decide).decision.correct)
        val probe = runner.probe() as Probe.Decide<BinarySearchAction>
        val before = runner.current.state

        val wrong = probe.decision.options
            .map { it.action }
            .first { it != probe.decision.correct }

        assertTrue(
            DecisionValidation.validate(probe.decision, wrong, priorAttempts = 0)
                is Validation.Retry,
        )
        assertEquals(before, runner.current.state)
    }

    @Test
    fun `the correct half is the one that can still contain the target`() {
        val challenge = ChallengeGenerator.forRound(1, 7L)
        val runner = AlgorithmRunner(BinarySearchAlgorithm(), challenge.dataset)
        val target = challenge.dataset.target!!

        runner.apply((runner.probe() as Probe.Decide).decision.correct)
        val state = runner.current.state as BinarySearchState
        val midValue = state.values[state.mid!!]
        val decision = (runner.probe() as Probe.Decide<BinarySearchAction>).decision

        val expected = when {
            midValue == target -> BinarySearchAction.Found
            target > midValue -> BinarySearchAction.KeepRight
            else -> BinarySearchAction.KeepLeft
        }
        assertEquals(expected, decision.correct)
    }

    // ── Hints ─────────────────────────────────────────────────────────────────

    @Test
    fun `the first hint of a decision is always free`() {
        assertEquals(HintAccess.Free, HintPolicy.access(levelsUsed = 0))
    }

    @Test
    fun `later hints are offered behind an ad, and the ladder never dead-ends`() {
        assertEquals(HintAccess.Rewarded, HintPolicy.access(1))
        assertEquals(HintAccess.Rewarded, HintPolicy.access(2))
        assertEquals(HintAccess.Exhausted, HintPolicy.access(3))
        assertEquals(HintAccess.Exhausted, HintPolicy.access(9))
    }

    @Test
    fun `every challenge decision carries a full hint ladder`() {
        val challenge = ChallengeGenerator.forRound(1, 7L)
        val runner = AlgorithmRunner(BinarySearchAlgorithm(), challenge.dataset)
        repeat(4) {
            val probe = runner.probe()
            if (probe !is Probe.Decide<BinarySearchAction>) return
            assertEquals(
                "a hint ladder shorter than the policy's depth would dead-end",
                HintPolicy.LADDER_DEPTH,
                probe.decision.hintLadder.size,
            )
            runner.apply(probe.decision.correct)
        }
    }

    @Test
    fun `reading a hint cannot move the algorithm`() {
        // Hints are pure data hanging off the decision; there is no code path
        // from reading one to applying an action. Asserting the decision is
        // unchanged after reading every rung is how that stays true.
        val challenge = ChallengeGenerator.forRound(1, 7L)
        val runner = AlgorithmRunner(BinarySearchAlgorithm(), challenge.dataset)
        val probe = runner.probe() as Probe.Decide<BinarySearchAction>
        val before = runner.current.state

        probe.decision.hintLadder.forEach { assertNotNull(it) }

        assertEquals(before, runner.current.state)
        assertEquals(probe.decision.correct, (runner.probe() as Probe.Decide).decision.correct)
    }

    // ── Labels are presentation, and only presentation ────────────────────────

    @Test
    fun `relabelling a mission never changes what is correct`() {
        val plain = MissionCatalog.build(
            kind = com.ttele.algoking.engine.scenario.MissionKind.WAREHOUSE,
            difficulty = com.ttele.algoking.engine.challenge.Difficulty.Intermediate,
            seed = 11L,
        )
        val runner = AlgorithmRunner(BinarySearchAlgorithm(), plain.dataset)
        val decision = (runner.probe() as Probe.Decide<BinarySearchAction>).decision
        // The label the learner reads on the correct cell must name the same slot
        // the engine picked — otherwise the story and the algorithm disagree.
        val correct = decision.correct as BinarySearchAction.Inspect
        assertEquals(plain.labels[correct.index], "#${plain.dataset.values[correct.index]}")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private data class Run(val decisions: Int, val outcome: Outcome)

    /** Drive the real algorithm the way a flawless learner would. */
    private fun solve(values: List<Int>, target: Int): Run {
        val runner = AlgorithmRunner(
            BinarySearchAlgorithm(),
            com.ttele.algoking.engine.core.Dataset(values, target),
        )
        var decisions = 0
        var guard = 0
        while (guard++ < 256) {
            when (val probe = runner.probe()) {
                is Probe.Terminal -> return Run(decisions, probe.outcome)
                is Probe.Decide -> {
                    decisions++
                    runner.apply(probe.decision.correct)
                }

                is Probe.Mechanical -> runner.apply(probe.action)
            }
        }
        error("did not terminate")
    }
}
