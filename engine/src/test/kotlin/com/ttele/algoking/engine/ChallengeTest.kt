package com.ttele.algoking.engine

import com.ttele.algoking.engine.algorithms.binarysearch.BinarySearchAction
import com.ttele.algoking.engine.algorithms.binarysearch.BinarySearchAlgorithm
import com.ttele.algoking.engine.challenge.Challenge
import com.ttele.algoking.engine.challenge.ChallengeGenerator
import com.ttele.algoking.engine.challenge.ChallengeRun
import com.ttele.algoking.engine.challenge.ChallengeType
import com.ttele.algoking.engine.challenge.Difficulty
import com.ttele.algoking.engine.core.AlgorithmRunner
import com.ttele.algoking.engine.core.Probe
import com.ttele.algoking.engine.dataset.BinarySearchDatasets
import com.ttele.algoking.engine.decision.DecisionKind
import com.ttele.algoking.engine.decision.DecisionValidation
import com.ttele.algoking.engine.decision.Validation
import com.ttele.algoking.engine.event.Outcome
import com.ttele.algoking.engine.scoring.ScoreInput
import com.ttele.algoking.engine.scoring.Scorer
import com.ttele.algoking.engine.scoring.StarFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeTest {

    /** Plays a challenge exactly the way a perfect learner would. */
    private fun playPerfectly(challenge: Challenge): AlgorithmRunner<*, *> {
        val runner = AlgorithmRunner(BinarySearchAlgorithm(), challenge.dataset)
        var guard = 0
        while (guard++ < 64) {
            when (val probe = runner.probe()) {
                is Probe.Decide -> runner.apply(probe.decision.correct)
                is Probe.Mechanical -> runner.apply(probe.action)
                is Probe.Terminal -> return runner
            }
        }
        error("did not terminate")
    }

    private fun outcomeOf(runner: AlgorithmRunner<*, *>): Outcome =
        (runner.probe() as Probe.Terminal).outcome

    // ── Generation ────────────────────────────────────────────────────────────

    @Test
    fun `a FIND challenge ends found and a NOT_FOUND challenge empties the range`() {
        repeat(10) { i ->
            val find = ChallengeGenerator.generate(
                ChallengeType.FIND,
                Difficulty.Intermediate,
                seed = i.toLong(),
            )
            assertTrue(outcomeOf(playPerfectly(find)) is Outcome.Found)

            val absent = ChallengeGenerator.generate(
                ChallengeType.NOT_FOUND,
                Difficulty.Intermediate,
                seed = i.toLong(),
            )
            assertEquals(Outcome.NotFound, outcomeOf(playPerfectly(absent)))
            assertFalse(absent.dataset.values.contains(absent.target))
        }
    }

    @Test
    fun `challenge data never reuses the Watch or Try arrays`() {
        val lesson = (BinarySearchDatasets.watch.values + BinarySearchDatasets.tryIt.values).toSet()
        repeat(12) { i ->
            val c = ChallengeGenerator.forRound(round = i + 1, seed = i.toLong())
            assertTrue(
                "round ${i + 1} reused lesson values",
                c.dataset.values.none { it in lesson },
            )
            assertNotEquals(BinarySearchDatasets.watch.values, c.dataset.values)
            assertNotEquals(BinarySearchDatasets.tryIt.values, c.dataset.values)
        }
    }

    @Test
    fun `the same seed always produces the same challenge`() {
        repeat(6) { i ->
            val a = ChallengeGenerator.forRound(i + 1, seed = 4242L + i)
            val b = ChallengeGenerator.forRound(i + 1, seed = 4242L + i)
            assertEquals(a.dataset.values, b.dataset.values)
            assertEquals(a.target, b.target)
            assertEquals(a.type, b.type)
            assertEquals(a.difficulty, b.difficulty)
        }
    }

    @Test
    fun `difficulty rises with the round and stays sorted and distinct`() {
        assertEquals(Difficulty.Beginner, ChallengeGenerator.plainForRound(1, 0).difficulty)
        assertEquals(Difficulty.Intermediate, ChallengeGenerator.plainForRound(3, 0).difficulty)
        assertEquals(Difficulty.Advanced, ChallengeGenerator.plainForRound(6, 0).difficulty)

        repeat(12) { i ->
            val c = ChallengeGenerator.plainForRound(i + 1, seed = i.toLong())
            assertEquals(c.dataset.values.sorted(), c.dataset.values)
            assertEquals(c.dataset.values.distinct().size, c.dataset.values.size)
        }
    }

    @Test
    fun `not-found challenges appear in the rotation`() {
        val types = (1..9).map { ChallengeGenerator.plainForRound(it, seed = it.toLong()).type }
        assertTrue("no NOT_FOUND in the first nine", types.contains(ChallengeType.NOT_FOUND))
        assertTrue("no FIND in the first nine", types.contains(ChallengeType.FIND))
    }

    // ── The learner picks the middle ──────────────────────────────────────────

    @Test
    fun `the first decision of every round asks which cell to check`() {
        val c = ChallengeGenerator.forRound(2, seed = 7)
        val runner = AlgorithmRunner(BinarySearchAlgorithm(), c.dataset)
        val decision = (runner.probe() as Probe.Decide).decision

        assertEquals(DecisionKind.CELL, decision.kind)
        // Every live cell is offered, and only the middle is right.
        assertEquals(c.size, decision.options.size)
        assertEquals(BinarySearchAction.Inspect(c.size / 2), decision.correct)
    }

    @Test
    fun `picking a non-middle cell is refused and changes nothing`() {
        val c = ChallengeGenerator.forRound(2, seed = 7)
        val runner = AlgorithmRunner(BinarySearchAlgorithm(), c.dataset)
        val before = runner.current.state
        val decision = (runner.probe() as Probe.Decide).decision

        val wrongSlots = decision.options
            .mapNotNull { it.slot }
            .filter { BinarySearchAction.Inspect(it) != decision.correct }

        wrongSlots.forEach { slot ->
            val verdict = DecisionValidation.validate(
                decision,
                BinarySearchAction.Inspect(slot),
                priorAttempts = 0,
            )
            assertTrue("slot $slot was accepted", verdict is Validation.Retry)
        }
        assertEquals(before, runner.current.state)
    }

    @Test
    fun `the cell decision carries a minimal clue and a hint ladder`() {
        val c = ChallengeGenerator.forRound(1, seed = 3)
        val runner = AlgorithmRunner(BinarySearchAlgorithm(), c.dataset)
        val decision = (runner.probe() as Probe.Decide).decision

        // Challenge shows one terse clue; Try climbs the longer ladder.
        assertNotEquals(decision.minimalFeedback, decision.guidance.last())
        assertEquals(3, decision.hintLadder.size)
        // A hint must never simply be the answer on the first rung.
        assertNotEquals(decision.hintLadder.first(), decision.hintLadder.last())
    }

    @Test
    fun `hints never shrink the number of decisions the learner must make`() {
        val c = ChallengeGenerator.forRound(2, seed = 11)
        val perfect = playPerfectly(c)
        val decisionsWithoutHints = perfect.current.index

        // Taking every hint changes nothing about the run itself.
        val withHints = playPerfectly(c)
        assertEquals(decisionsWithoutHints, withHints.current.index)
    }

    // ── Scoring ───────────────────────────────────────────────────────────────

    @Test
    fun `a flawless run is three stars and a mistaken one is not`() {
        val c = ChallengeGenerator.forRound(2, seed = 5)
        val perfect = ChallengeRun(
            challenge = c,
            decisions = 6,
            correctDecisions = 6,
            mistakes = 0,
            hintsUsed = 0,
            comparisons = c.optimalComparisons,
            completed = true,
            targetFound = true,
        )
        assertTrue(perfect.optimal)
        assertEquals(1f, perfect.accuracy, 0.001f)
        assertEquals(
            3,
            Scorer.score(
                ScoreInput(
                    StarFamily.EFFICIENCY,
                    perfect.toMetrics(),
                    c.optimalComparisons,
                    completed = true,
                ),
            ).stars,
        )

        val messy = perfect.copy(mistakes = 3, correctDecisions = 3, hintsUsed = 2)
        assertFalse(messy.optimal)
        assertTrue(
            Scorer.score(
                ScoreInput(
                    StarFamily.EFFICIENCY,
                    messy.toMetrics(),
                    c.optimalComparisons,
                    completed = true,
                ),
            ).stars < 3,
        )
    }

    @Test
    fun `finding nothing still counts as completing a NOT_FOUND challenge`() {
        val c = ChallengeGenerator.generate(ChallengeType.NOT_FOUND, Difficulty.Beginner, 9)
        val run = ChallengeRun(
            challenge = c,
            decisions = 6,
            correctDecisions = 6,
            comparisons = c.optimalComparisons,
            completed = true,
            targetFound = false,
        )
        // Proving absence is a success, and must score like one.
        assertTrue(run.optimal)
        assertEquals(
            3,
            Scorer.score(
                ScoreInput(
                    StarFamily.EFFICIENCY,
                    run.toMetrics(),
                    c.optimalComparisons,
                    completed = true,
                ),
            ).stars,
        )
    }
}
