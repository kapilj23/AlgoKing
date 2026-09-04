package com.ttele.algoking.engine

import com.ttele.algoking.engine.challenge.Boundary
import com.ttele.algoking.engine.challenge.ChallengeGenerator
import com.ttele.algoking.engine.challenge.Comparison
import com.ttele.algoking.engine.challenge.Difficulty
import com.ttele.algoking.engine.challenge.HintAccess
import com.ttele.algoking.engine.challenge.MissionRun
import com.ttele.algoking.engine.challenge.MissionStep
import com.ttele.algoking.engine.challenge.Verdict
import com.ttele.algoking.engine.core.Dataset
import com.ttele.algoking.engine.scenario.Mission
import com.ttele.algoking.engine.scenario.MissionCatalog
import com.ttele.algoking.engine.scenario.MissionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Challenge loop, gate by gate.
 *
 * The assertions that matter most are the negative ones: after every kind of
 * wrong answer the search range must be *exactly* where it was. A challenge that
 * quietly advances on a mistake is worse than no challenge, because it teaches
 * the mistake.
 */
class MissionRunTest {

    private fun mission(values: List<Int>, target: Int) = Mission(
        kind = MissionKind.WAREHOUSE,
        icon = "📦",
        title = "Test",
        story = "",
        goal = "",
        subject = "pallets",
        sortedBy = "",
        dataset = Dataset(values, target),
        labels = values.map { "#$it" },
        targetLabel = "#$target",
        difficulty = Difficulty.Intermediate,
    )

    private fun run(values: List<Int>, target: Int) = MissionRun(mission(values, target))

    private fun standard() = run(
        listOf(1024, 1847, 2631, 3418, 4290, 5176, 6042, 6931, 7842, 8617, 9425),
        target = 7842,
    )

    /** Play one round the way a flawless learner would. */
    private fun MissionRun.playRound() {
        assertEquals(Verdict.Right, chooseMid(correctMid()))
        val cmp = Comparison.of(target, state.values[mid!!])
        assertEquals(Verdict.Right, chooseComparison(cmp))
        if (cmp != Comparison.EQUAL) {
            val boundary = if (cmp == Comparison.GREATER) Boundary.LEFT else Boundary.RIGHT
            val slot = if (cmp == Comparison.GREATER) mid!! + 1 else mid!! - 1
            assertEquals(Verdict.Right, moveBoundary(boundary, slot))
        }
    }

    private fun MissionRun.correctMid(): Int = left + (right - left) / 2

    // ── Gate 1: find the middle ───────────────────────────────────────────────

    @Test
    fun `a correct middle is accepted and moves to the comparison`() {
        val r = standard()
        assertEquals(MissionStep.FIND_MID, r.step)
        assertEquals(Verdict.Right, r.chooseMid(r.correctMid()))
        assertEquals(MissionStep.COMPARE, r.step)
    }

    @Test
    fun `a wrong middle is refused and the range does not move`() {
        val r = standard()
        val before = r.state
        assertEquals(Verdict.Wrong, r.chooseMid(r.correctMid() + 1))
        assertEquals("the search moved on a wrong middle", before, r.state)
        assertEquals(MissionStep.FIND_MID, r.step)
        assertEquals(1, r.mistakes)
    }

    @Test
    fun `the middle follows the engine's own convention`() {
        val r = standard()
        // The canonical lower middle. If this is ever changed, the test fails
        // rather than a learner being marked wrong for following the formula
        // the app taught them.
        assertEquals(r.left + (r.right - r.left) / 2, r.correctMid())
        assertEquals(Verdict.Right, r.chooseMid(r.correctMid()))
    }

    // ── Gate 2: compare ───────────────────────────────────────────────────────

    @Test
    fun `a wrong comparison is refused and nothing moves`() {
        val r = standard()
        r.chooseMid(r.correctMid())
        val before = r.state
        val actual = Comparison.of(r.target, r.state.values[r.mid!!])
        val wrong = Comparison.entries.first { it != actual }

        assertEquals(Verdict.Wrong, r.chooseComparison(wrong))
        assertEquals(before, r.state)
        assertEquals(MissionStep.COMPARE, r.step)
        assertNull("a refused comparison must not be remembered", r.comparison)
    }

    @Test
    fun `a correct comparison settles the reasoning without moving the search`() {
        val r = standard()
        r.chooseMid(r.correctMid())
        val before = r.state
        assertEquals(Verdict.Right, r.chooseComparison(Comparison.GREATER))
        assertEquals("comparing is reasoning, not a state change", before, r.state)
        assertEquals(MissionStep.MOVE_BOUNDARY, r.step)
    }

    // ── Gate 3: move the boundary ─────────────────────────────────────────────

    @Test
    fun `moving the wrong pointer is refused`() {
        val r = standard()
        r.chooseMid(r.correctMid())
        r.chooseComparison(Comparison.GREATER)
        val before = r.state
        // Target is above the middle, so RIGHT is not the boundary that moves.
        assertEquals(Verdict.Wrong, r.moveBoundary(Boundary.RIGHT, r.mid!! - 1))
        assertEquals(before, r.state)
    }

    @Test
    fun `moving the right pointer to the wrong slot is refused`() {
        val r = standard()
        r.chooseMid(r.correctMid())
        r.chooseComparison(Comparison.GREATER)
        val before = r.state
        // The off-by-one is the part people get wrong, so it is a wrong answer.
        assertEquals(Verdict.Wrong, r.moveBoundary(Boundary.LEFT, r.mid!!))
        assertEquals(before, r.state)
        assertEquals(Verdict.Wrong, r.moveBoundary(Boundary.LEFT, r.mid!! + 2))
        assertEquals(before, r.state)
    }

    @Test
    fun `a correct move discards exactly the half that cannot hold the target`() {
        val r = standard()
        val midSlot = r.correctMid()
        r.chooseMid(midSlot)
        r.chooseComparison(Comparison.GREATER)
        assertEquals(Verdict.Right, r.moveBoundary(Boundary.LEFT, midSlot + 1))

        assertEquals("left must be mid + 1", midSlot + 1, r.left)
        assertEquals("right must not have moved", 10, r.right)
        assertEquals(MissionStep.FIND_MID, r.step)
        assertNull("the comparison is spent once the move is made", r.comparison)
    }

    @Test
    fun `a target below the middle moves the right boundary to mid minus one`() {
        val r = run(listOf(10, 20, 30, 40, 50, 60, 70), target = 20)
        val midSlot = r.correctMid()
        r.chooseMid(midSlot)
        r.chooseComparison(Comparison.LESS)
        assertEquals(Verdict.Right, r.moveBoundary(Boundary.RIGHT, midSlot - 1))
        assertEquals(0, r.left)
        assertEquals(midSlot - 1, r.right)
    }

    // ── The whole loop ────────────────────────────────────────────────────────

    @Test
    fun `a flawless run finds the target and completes`() {
        val r = standard()
        var guard = 0
        while (!r.finished && guard++ < 40) r.playRound()

        assertEquals(MissionStep.DONE, r.step)
        assertEquals(0, r.mistakes)
        assertEquals("the search must land on the target", 7842, r.state.values[r.mid!!])
    }

    @Test
    fun `the search space halves, and the trail records it`() {
        val r = standard()
        var guard = 0
        while (!r.finished && guard++ < 40) r.playRound()

        assertEquals("the trail starts at the full shelf", 11, r.trail.first())
        r.trail.zipWithNext { wide, narrow ->
            assertTrue("the range must shrink every round: ${r.trail}", narrow < wide)
        }
    }

    @Test
    fun `a generated medium mission takes three to five rounds`() {
        // The hand-written shelf above happens to put its target on the second
        // middle, so the guarantee is asserted where it is actually made — in
        // the generator, which counts rounds before choosing a target.
        (1..12).forEach { round ->
            val m = MissionCatalog.forRound(round, 21L)
            val r = MissionRun(m)
            var rounds = 0
            while (!r.finished && rounds < 40) { r.playRound(); rounds++ }
            assertTrue("${m.kind} round $round took $rounds rounds, wanted 3..5", rounds in 3..5)
        }
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test
    fun `a target sitting on the opening middle ends in one round`() {
        // Five boxes: mid = 0 + (4 - 0) / 2 = 2.
        val r = run(listOf(10, 20, 30, 40, 50), target = 30)
        assertEquals(2, r.correctMid())
        r.playRound()
        assertEquals(MissionStep.DONE, r.step)
    }

    @Test
    fun `a target at the first slot is still reachable`() {
        val r = run(listOf(10, 20, 30, 40, 50, 60, 70), target = 10)
        var guard = 0
        while (!r.finished && guard++ < 40) r.playRound()
        assertEquals(MissionStep.DONE, r.step)
        assertEquals(10, r.state.values[r.mid!!])
    }

    @Test
    fun `a target at the last slot is still reachable`() {
        val r = run(listOf(10, 20, 30, 40, 50, 60, 70), target = 70)
        var guard = 0
        while (!r.finished && guard++ < 40) r.playRound()
        assertEquals(MissionStep.DONE, r.step)
        assertEquals(70, r.state.values[r.mid!!])
    }

    @Test
    fun `a range of one box still has to be opened and compared`() {
        val r = run(listOf(42), target = 42)
        assertEquals(0, r.correctMid())
        assertEquals(Verdict.Right, r.chooseMid(0))
        assertEquals(Verdict.Right, r.chooseComparison(Comparison.EQUAL))
        assertEquals(MissionStep.DONE, r.step)
    }

    @Test
    fun `taps outside the current gate are ignored rather than counted wrong`() {
        val r = standard()
        // Comparing before a box is open is not a mistake, it is a mis-tap.
        assertEquals(Verdict.Ignored, r.chooseComparison(Comparison.LESS))
        assertEquals(Verdict.Ignored, r.moveBoundary(Boundary.LEFT, 1))
        assertEquals(0, r.mistakes)
    }

    // ── Hints ─────────────────────────────────────────────────────────────────

    @Test
    fun `each gate offers one free hint then one behind an ad`() {
        val r = standard()
        assertEquals(HintAccess.Free, r.hintAccess)
        r.takeHint()
        assertEquals(HintAccess.Rewarded, r.hintAccess)
        r.takeHint()
        assertEquals("two rungs is the whole ladder", HintAccess.Exhausted, r.hintAccess)
    }

    @Test
    fun `the free hint allowance comes back on the next gate`() {
        val r = standard()
        r.takeHint()
        r.takeHint()
        assertEquals(HintAccess.Exhausted, r.hintAccess)

        r.chooseMid(r.correctMid())
        assertEquals("a new question earns a new free hint", HintAccess.Free, r.hintAccess)
    }

    @Test
    fun `a hint cannot move the search`() {
        val r = standard()
        val before = r.state
        val step = r.step
        r.takeHint()
        r.takeHint()
        r.takeHint()
        assertEquals(before, r.state)
        assertEquals(step, r.step)
    }

    @Test
    fun `no hint ever contains the answer`() {
        val r = standard()
        // Every value on screen, and every boundary the learner has to derive.
        val giveaways = buildList {
            addAll(r.state.values.map { it.toString() })
            add(r.target.toString())
            add(r.correctMid().toString())
            add((r.correctMid() + 1).toString())
            add((r.correctMid() - 1).toString())
        }
        MissionStep.entries.forEach { step ->
            com.ttele.algoking.engine.challenge.MissionHints.forStep(step)?.forEach { hint ->
                giveaways.forEach { value ->
                    assertTrue(
                        "a hint for $step leaks the answer '$value': ${hint.body}",
                        !hint.body.contains(value),
                    )
                }
            }
        }
    }

    @Test
    fun `the rewarded rung gives the rule and the free rung does not`() {
        MissionStep.entries.mapNotNull { MissionHints.forStep(it) }.forEach { ladder ->
            assertEquals(2, ladder.size)
            assertTrue("the free rung must stay conceptual", !ladder[0].rule)
            assertTrue("an ad must buy the rule", ladder[1].rule)
        }
    }

    // ── Practice again ────────────────────────────────────────────────────────

    @Test
    fun `practice again is a different mission with different data`() {
        val first = ChallengeGenerator.forRound(1, 5L).mission!!
        val next = ChallengeGenerator.forRound(2, 5L).mission!!
        assertNotEquals(first.kind, next.kind)
        assertNotEquals(first.dataset.values, next.dataset.values)
        assertNotEquals(first.dataset.target, next.dataset.target)
    }

    @Test
    fun `every generated mission can be solved through the three gates`() {
        (1..9).forEach { round ->
            val m = MissionCatalog.forRound(round, 3L)
            val r = MissionRun(m)
            var guard = 0
            while (!r.finished && guard++ < 60) r.playRound()
            assertEquals("${m.kind} round $round never completed", MissionStep.DONE, r.step)
            assertEquals(m.dataset.target, r.state.values[r.mid!!])
        }
    }
}

private typealias MissionHints = com.ttele.algoking.engine.challenge.MissionHints
