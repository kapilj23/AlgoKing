package com.algorithms.algoking.engine

import com.algorithms.algoking.engine.algorithms.knapsack.KnapsackAction
import com.algorithms.algoking.engine.algorithms.knapsack.KnapsackAlgorithm
import com.algorithms.algoking.engine.algorithms.knapsack.KnapsackPhase
import com.algorithms.algoking.engine.algorithms.knapsack.KnapsackProjector
import com.algorithms.algoking.engine.algorithms.knapsack.KnapsackState
import com.algorithms.algoking.engine.algorithms.knapsack.TablePos
import com.algorithms.algoking.engine.catalog.AlgorithmCatalog
import com.algorithms.algoking.engine.challenge.ChallengeCatalog
import com.algorithms.algoking.engine.core.AlgorithmId
import com.algorithms.algoking.engine.core.AlgorithmRunner
import com.algorithms.algoking.engine.core.Dataset
import com.algorithms.algoking.engine.core.KnapsackItem
import com.algorithms.algoking.engine.core.KnapsackProblem
import com.algorithms.algoking.engine.core.Probe
import com.algorithms.algoking.engine.dataset.KnapsackDatasets
import com.algorithms.algoking.engine.decision.Decision
import com.algorithms.algoking.engine.decision.DecisionKind
import com.algorithms.algoking.engine.decision.DecisionValidation
import com.algorithms.algoking.engine.decision.Validation
import com.algorithms.algoking.engine.event.Outcome
import com.algorithms.algoking.engine.event.Relation
import com.algorithms.algoking.engine.narration.NarrationId
import com.algorithms.algoking.engine.progress.LearningProgress
import com.algorithms.algoking.engine.progress.ProgressCodec
import com.algorithms.algoking.engine.progress.Stage
import com.algorithms.algoking.engine.scene.CellState
import com.algorithms.algoking.engine.scene.DpTableScene
import com.algorithms.algoking.engine.walkthrough.ComparisonReadout
import com.algorithms.algoking.engine.walkthrough.WatchStepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 0/1 Knapsack.
 *
 * The lesson's table and bag are **generated**, so they are checked against two
 * references written here with no lesson machinery in them at all: a recursive
 * textbook knapsack for every cell, and a brute-force enumeration of every bag for
 * the answer.
 */
class KnapsackTest {

    private val algorithm = KnapsackAlgorithm()
    private val projector = KnapsackProjector()
    private val watch = KnapsackDatasets.watchProblem
    private val tryIt = KnapsackDatasets.tryProblem

    private fun problem(capacity: Int, vararg items: Triple<String, Int, Int>) =
        KnapsackProblem(items.map { (name, weight, value) -> KnapsackItem(name, weight, value) }, capacity)

    private fun runner(problem: KnapsackProblem) =
        AlgorithmRunner(algorithm, Dataset(values = emptyList(), knapsack = problem))

    /** Drives the real lesson to its terminal state, always choosing correctly. */
    private fun solve(problem: KnapsackProblem): KnapsackState {
        val runner = runner(problem)
        var guard = 0
        while (guard++ < 10_000) {
            when (val probe = runner.probe()) {
                is Probe.Mechanical -> runner.apply(probe.action)
                is Probe.Decide -> runner.apply(probe.decision.correct)
                is Probe.Terminal -> return runner.current.state
            }
        }
        error("Knapsack did not terminate")
    }

    /** Every question the learner is asked, in order, with the state it was asked in. */
    private fun asked(problem: KnapsackProblem): List<Pair<KnapsackState, Decision<KnapsackAction>>> {
        val runner = runner(problem)
        val out = mutableListOf<Pair<KnapsackState, Decision<KnapsackAction>>>()
        var guard = 0
        while (guard++ < 10_000) {
            when (val probe = runner.probe()) {
                is Probe.Mechanical -> runner.apply(probe.action)
                is Probe.Decide -> {
                    out += runner.current.state to probe.decision
                    runner.apply(probe.decision.correct)
                }
                is Probe.Terminal -> return out
            }
        }
        error("Knapsack did not terminate")
    }

    private fun tableOf(state: KnapsackState): List<List<Int>> =
        state.table.map { row -> row.map { requireNotNull(it) { "an uncomputed cell survived" } } }

    // ── Independent references ───────────────────────────────────────────────

    /** Textbook recursive 0/1 knapsack: no table, no decisions, no cursor. */
    private fun referenceTable(problem: KnapsackProblem): List<List<Int>> {
        fun best(i: Int, c: Int): Int {
            if (i == 0 || c == 0) return 0
            val item = problem.items[i - 1]
            val skip = best(i - 1, c)
            return if (item.weight > c) skip else maxOf(skip, item.value + best(i - 1, c - item.weight))
        }
        return (0..problem.items.size).map { i -> (0..problem.capacity).map { c -> best(i, c) } }
    }

    /** The standard backtrack over a reference table: a changed cell means taken. */
    private fun referenceBag(problem: KnapsackProblem): Set<String> {
        val table = referenceTable(problem)
        var c = problem.capacity
        val bag = mutableSetOf<String>()
        for (i in problem.items.size downTo 1) {
            if (table[i][c] != table[i - 1][c]) {
                bag += problem.items[i - 1].name
                c -= problem.items[i - 1].weight
            }
        }
        return bag
    }

    private data class Bag(val names: Set<String>, val weight: Int, val value: Int)

    /** Brute force: every subset that fits. */
    private fun everyBag(problem: KnapsackProblem): List<Bag> {
        val items = problem.items
        return (0 until (1 shl items.size)).map { mask ->
            val chosen = items.filterIndexed { i, _ -> mask and (1 shl i) != 0 }
            Bag(chosen.map { it.name }.toSet(), chosen.sumOf { it.weight }, chosen.sumOf { it.value })
        }.filter { it.weight <= problem.capacity }
    }

    // ── The lesson's own datasets ────────────────────────────────────────────

    @Test
    fun `the watch table is exactly the one the lesson teaches`() {
        val end = solve(watch)
        assertEquals(
            listOf(
                listOf(0, 0, 0, 0, 0, 0),
                listOf(0, 0, 3, 3, 3, 3),
                listOf(0, 0, 3, 5, 5, 8),
                listOf(0, 0, 3, 5, 7, 8),
            ),
            tableOf(end),
        )
        assertEquals(8, end.best)
        assertEquals(listOf("Book", "Camera"), end.bag.map { it.name })
        assertEquals(listOf(true, true, false), end.taken)
    }

    @Test
    fun `the try table is exactly the one the lesson teaches`() {
        val end = solve(tryIt)
        assertEquals(
            listOf(
                listOf(0, 0, 0, 0, 0, 0),
                listOf(0, 0, 2, 2, 2, 2),
                listOf(0, 0, 2, 2, 5, 5),
                listOf(0, 0, 2, 4, 5, 6),
            ),
            tableOf(end),
        )
        assertEquals(6, end.best)
        assertEquals(listOf("Water", "Stove"), end.bag.map { it.name })
        assertEquals(listOf(true, false, true), end.taken)
    }

    @Test
    fun `both lesson bags have exactly one optimum, and no ties anywhere in their tables`() {
        for (p in listOf(watch, tryIt)) {
            val bags = everyBag(p)
            val best = bags.maxOf { it.value }
            assertEquals("${p.items} has one optimal bag", 1, bags.count { it.value == best })

            val table = referenceTable(p)
            for (i in 1..p.items.size) {
                val item = p.items[i - 1]
                for (c in item.weight..p.capacity) {
                    val skip = table[i - 1][c]
                    val take = item.value + table[i - 1][c - item.weight]
                    assertTrue("tie at dp[$i][$c]", skip != take)
                }
            }
        }
        assertEquals(setOf("Book", "Camera"), everyBag(watch).maxBy { it.value }.names)
        assertEquals(setOf("Water", "Stove"), everyBag(tryIt).maxBy { it.value }.names)
    }

    @Test
    fun `greedy takes the Laptop and loses to the table`() {
        val start = algorithm.initial(KnapsackDatasets.watch)
        assertEquals(listOf("Laptop"), start.greedyPick.map { it.name })
        assertEquals(7, start.greedyValue)
        assertEquals(8, solve(watch).best)
        // Best value per weight picks the Laptop too, so both greedy strategies lose.
        assertEquals("Laptop", watch.items.maxBy { it.value.toDouble() / it.weight }.name)
    }

    @Test
    fun `the answer cell is a SKIP in watch and a TAKE in try`() {
        val watchLast = asked(watch).last { it.second.correct == KnapsackAction.Skip || it.second.correct == KnapsackAction.Take }
        assertEquals(TablePos(3, 5), watchLast.first.position)
        assertEquals(KnapsackAction.Skip, watchLast.second.correct)

        val tryLast = asked(tryIt).last { it.second.correct == KnapsackAction.Skip || it.second.correct == KnapsackAction.Take }
        assertEquals(TablePos(3, 5), tryLast.first.position)
        assertEquals(KnapsackAction.Take, tryLast.second.correct)
    }

    // ── Against the references ───────────────────────────────────────────────

    @Test
    fun `the engine agrees with both references on every small problem`() {
        val weights = 1..4
        val values = listOf(1, 4, 6)
        val capacities = listOf(0, 3, 5)
        for (w1 in weights) for (w2 in weights) for (w3 in weights)
            for (v1 in values) for (v2 in values) for (v3 in values)
                for (cap in capacities) {
                    val p = problem(cap, Triple("A", w1, v1), Triple("B", w2, v2), Triple("C", w3, v3))
                    val end = solve(p)
                    assertEquals("table for $p", referenceTable(p), tableOf(end))

                    val bestValue = everyBag(p).maxOf { it.value }
                    assertEquals("best for $p", bestValue, end.best)

                    val bag = end.bag
                    assertTrue("bag fits for $p", bag.sumOf { it.weight } <= cap)
                    assertEquals("bag is optimal for $p", bestValue, bag.sumOf { it.value })
                    assertEquals("SKIP on a tie for $p", referenceBag(p), bag.map { it.name }.toSet())
                }
    }

    // ── Edge cases ───────────────────────────────────────────────────────────

    @Test
    fun `an empty item list is a finished lesson, not a crash`() {
        val p = problem(5)
        val end = solve(p)
        assertEquals(KnapsackPhase.DONE, end.phase)
        assertEquals(0, end.best)
        assertEquals(listOf(listOf(0, 0, 0, 0, 0, 0)), tableOf(end))
        assertTrue(asked(p).isEmpty())
    }

    @Test
    fun `zero capacity asks nothing and takes nothing`() {
        val p = problem(0, Triple("A", 1, 5), Triple("B", 2, 3))
        val end = solve(p)
        assertEquals(KnapsackPhase.DONE, end.phase)
        assertEquals(0, end.best)
        assertEquals(listOf(false, false), end.taken)
        assertTrue(asked(p).isEmpty())
    }

    @Test
    fun `a single item that fits is taken, and only the walk back is asked`() {
        val p = problem(5, Triple("A", 3, 4))
        val end = solve(p)
        assertEquals(listOf(0, 0, 0, 4, 4, 4), tableOf(end)[1])
        assertEquals(listOf(true), end.taken)
        val questions = asked(p)
        assertEquals(1, questions.size)
        assertEquals(KnapsackAction.MarkTaken, questions.single().second.correct)
    }

    @Test
    fun `a single item heavier than the bag is left out`() {
        val p = problem(2, Triple("A", 3, 4))
        val end = solve(p)
        assertEquals(0, end.best)
        assertEquals(listOf(false), end.taken)
        assertEquals(KnapsackAction.MarkLeftOut, asked(p).single().second.correct)
    }

    @Test
    fun `when nothing fits, every row copies and everything is left out`() {
        val p = problem(2, Triple("A", 3, 1), Triple("B", 4, 2))
        val end = solve(p)
        assertEquals(0, end.best)
        assertEquals(listOf(false, false), end.taken)
        // B's boundary would be capacity 3, which this bag does not have.
        assertTrue(asked(p).all { it.first.phase == KnapsackPhase.TRACE })
    }

    @Test
    fun `duplicate weights and duplicate values are handled like anything else`() {
        val sameWeight = problem(4, Triple("A", 2, 3), Triple("B", 2, 4))
        assertEquals(7, solve(sameWeight).best)
        assertEquals(referenceTable(sameWeight), tableOf(solve(sameWeight)))

        val sameValue = problem(4, Triple("A", 1, 5), Triple("B", 3, 5))
        assertEquals(10, solve(sameValue).best)
        assertEquals(referenceTable(sameValue), tableOf(solve(sameValue)))
    }

    @Test
    fun `with two optimal bags the engine reports the one SKIP-on-a-tie produces`() {
        // A + B = 7 and C = 7. dp[3][5] has SKIP 7 against TAKE 7 + 0 = 7.
        val p = problem(5, Triple("A", 2, 3), Triple("B", 3, 4), Triple("C", 5, 7))
        assertEquals(2, everyBag(p).count { it.value == 7 })
        val end = solve(p)
        assertEquals(7, end.best)
        assertEquals(setOf("A", "B"), end.bag.map { it.name }.toSet())
        val tie = asked(p).single { it.first.building && it.first.position == TablePos(3, 5) && it.first.source != null }
        assertEquals(KnapsackAction.Skip, tie.second.correct)
    }

    @Test
    fun `a problem the lesson cannot teach cannot be built`() {
        assertThrows(IllegalArgumentException::class.java) { problem(5, Triple("A", 0, 1)) }
        assertThrows(IllegalArgumentException::class.java) { problem(5, Triple("A", 1, -1)) }
        assertThrows(IllegalArgumentException::class.java) { problem(-1, Triple("A", 1, 1)) }
        assertThrows(IllegalArgumentException::class.java) { problem(5, Triple("A", 1, 1), Triple("A", 2, 2)) }
        assertThrows(IllegalArgumentException::class.java) { problem(5, Triple(" ", 1, 1)) }
    }

    // ── What the learner is asked ────────────────────────────────────────────

    @Test
    fun `try asks exactly thirteen questions, in this order`() {
        val questions = asked(tryIt)
        val expected = listOf(
            Triple(TablePos(2, 3), DecisionKind.OPTIONS, KnapsackAction.Skip),
            Triple(TablePos(2, 4), DecisionKind.OPTIONS, KnapsackAction.Take),
            Triple(TablePos(2, 5), DecisionKind.CELL, KnapsackAction.PickSource(1, 1)),
            Triple(TablePos(2, 5), DecisionKind.OPTIONS, KnapsackAction.Take),
            Triple(TablePos(3, 2), DecisionKind.OPTIONS, KnapsackAction.Skip),
            Triple(TablePos(3, 3), DecisionKind.OPTIONS, KnapsackAction.Take),
            Triple(TablePos(3, 4), DecisionKind.CELL, KnapsackAction.PickSource(2, 1)),
            Triple(TablePos(3, 4), DecisionKind.OPTIONS, KnapsackAction.Skip),
            Triple(TablePos(3, 5), DecisionKind.CELL, KnapsackAction.PickSource(2, 2)),
            Triple(TablePos(3, 5), DecisionKind.OPTIONS, KnapsackAction.Take),
        )
        val build = questions.filter { it.first.phase == KnapsackPhase.BUILD }
        assertEquals(expected, build.map { (state, d) -> Triple(state.position, d.kind, d.correct) })

        val trace = questions.filter { it.first.phase == KnapsackPhase.TRACE }
        assertEquals(
            listOf(KnapsackAction.MarkTaken, KnapsackAction.MarkLeftOut, KnapsackAction.MarkTaken),
            trace.map { it.second.correct },
        )
        assertEquals(13, questions.size)
    }

    @Test
    fun `row 1 and column 0 are never asked, and each later row asks one no-fit`() {
        for (p in listOf(watch, tryIt)) {
            val build = asked(p).filter { it.first.phase == KnapsackPhase.BUILD }
            assertTrue(build.all { it.first.row >= 2 && it.first.col >= 1 })
            for (row in 2..p.items.size) {
                val noFits = build.count { it.first.row == row && !it.first.fits }
                assertEquals("row $row of ${p.items}", 1, noFits)
            }
        }
    }

    @Test
    fun `every decision is well formed`() {
        for (p in listOf(watch, tryIt)) {
            for ((_, d) in asked(p)) {
                assertTrue(d.options.any { it.action == d.correct })
                assertEquals(3, d.guidance.size)
                assertFalse("the learner's judgement is never the app's", d.autoInTry)
                for (option in d.options) {
                    if (option.action == d.correct) continue
                    assertNotNull("why ${option.action} is wrong", d.whyWrong[option.action])
                }
            }
        }
    }

    @Test
    fun `a same-row tap is refused with the 0 1 reason`() {
        val (state, decision) = asked(tryIt).first { it.second.kind == DecisionKind.CELL && it.first.row == 3 }
        val sameRow = KnapsackAction.PickSource(3, 1)
        assertEquals(NarrationId.KN_WHY_SAME_ROW, decision.whyWrong.getValue(sameRow).id)
        assertEquals(state, algorithm.apply(state, sameRow).next)
    }

    // ── Wrong actions never move anything ────────────────────────────────────

    @Test
    fun `every wrong answer leaves the state byte-for-byte identical`() {
        for (p in listOf(watch, tryIt)) {
            for ((state, decision) in asked(p)) {
                for (option in decision.options) {
                    if (option.action == decision.correct) continue
                    val transition = algorithm.apply(state, option.action)
                    assertEquals("${option.action} at ${state.position}", state, transition.next)
                    assertFalse(transition.correct)
                    assertTrue(DecisionValidation.validate(decision, option.action, 0) is Validation.Retry)
                }
            }
        }
    }

    @Test
    fun `an item that does not fit cannot be taken`() {
        val (state, decision) = asked(tryIt).first()
        assertFalse(state.fits)
        assertEquals(NarrationId.KN_WHY_NO_FIT, decision.whyWrong.getValue(KnapsackAction.Take).id)
        val attempt = algorithm.apply(state, KnapsackAction.Take)
        assertEquals(state, attempt.next)
        assertFalse(attempt.correct)
    }

    @Test
    fun `driven adversarially, the run still ends on the right table`() {
        for (p in listOf(watch, tryIt)) {
            val runner = runner(p)
            val every = listOf(
                KnapsackAction.Introduce, KnapsackAction.FillBase, KnapsackAction.Focus,
                KnapsackAction.Take, KnapsackAction.Skip, KnapsackAction.BeginTrace,
                KnapsackAction.MarkTaken, KnapsackAction.MarkLeftOut,
            ) + (0..p.items.size).flatMap { r -> (0..p.capacity).map { c -> KnapsackAction.PickSource(r, c) } }
            var guard = 0
            while (runner.probe() !is Probe.Terminal && guard++ < 500) {
                for (action in every) {
                    if (runner.probe() is Probe.Terminal) break
                    runner.apply(action)
                }
            }
            val end = runner.current.state
            assertEquals(KnapsackPhase.DONE, end.phase)
            assertEquals(referenceTable(p), tableOf(end))
            assertEquals(referenceBag(p), end.bag.map { it.name }.toSet())
        }
    }

    @Test
    fun `rewinding a decision restores the exact prior state`() {
        val runner = runner(tryIt)
        while (runner.probe() !is Probe.Decide) {
            runner.apply((runner.probe() as Probe.Mechanical).action)
        }
        val before = runner.current.state
        runner.apply((runner.probe() as Probe.Decide).decision.correct)
        runner.rewind()
        assertEquals(before, runner.current.state)
    }

    @Test
    fun `completion comes only once the walk back reaches row 0`() {
        val runner = runner(watch)
        var guard = 0
        while (guard++ < 10_000) {
            when (val probe = runner.probe()) {
                is Probe.Mechanical -> runner.apply(probe.action)
                is Probe.Decide -> runner.apply(probe.decision.correct)
                is Probe.Terminal -> {
                    assertEquals(Outcome.Completed(correct = true), probe.outcome)
                    assertEquals(0, runner.current.state.traceRow)
                    return
                }
            }
            if (runner.current.state.phase != KnapsackPhase.DONE) {
                assertFalse(runner.probe() is Probe.Terminal)
            }
        }
        error("did not terminate")
    }

    @Test
    fun `comparisons counts one per cell where the item fits`() {
        val trace = runner(watch).runToCompletion()
        // Row 1: capacities 2–5. Row 2: 3–5. Row 3: 4–5.
        assertEquals(9, trace.frames.last().metrics.comparisons)
    }

    // ── WATCH ────────────────────────────────────────────────────────────────

    @Test
    fun `the walkthrough is exactly the twenty beats the lesson was designed as`() {
        val steps = AlgorithmCatalog.zeroOneKnapsack().watchScript().steps
        assertEquals(
            listOf(
                WatchStepKind.SETUP, WatchStepKind.EXAMINE, WatchStepKind.COMPARE, WatchStepKind.EXAMINE,
                WatchStepKind.EXAMINE, WatchStepKind.ADD, WatchStepKind.EXAMINE, WatchStepKind.KEEP,
                WatchStepKind.COMPARE, WatchStepKind.ADD, WatchStepKind.COMPARE, WatchStepKind.ADD,
                WatchStepKind.EXAMINE, WatchStepKind.COMPARE, WatchStepKind.KEEP, WatchStepKind.ELIMINATE,
                WatchStepKind.ADD, WatchStepKind.FOUND, WatchStepKind.INSIGHT, WatchStepKind.SUMMARY,
            ),
            steps.map { it.kind },
        )
        assertEquals(
            listOf(
                NarrationId.KN_WATCH_SETUP, NarrationId.KN_WATCH_RULE, NarrationId.KN_WATCH_GREEDY,
                NarrationId.KN_WATCH_SUBPROBLEM, NarrationId.KN_WATCH_DEFINE, NarrationId.KN_WATCH_FIRST_ROW,
                NarrationId.KN_WATCH_NO_FIT, NarrationId.KN_WATCH_SKIP_SIDE, NarrationId.KN_WATCH_TAKE_SIDE,
                NarrationId.KN_WATCH_MAX, NarrationId.KN_WATCH_REUSE, NarrationId.KN_WATCH_REUSE_RESULT,
                NarrationId.KN_WATCH_ROW_SUMMARY, NarrationId.KN_WATCH_LAST_CELL, NarrationId.KN_WATCH_ANSWER_SKIP,
                NarrationId.KN_WATCH_TRACE_START, NarrationId.KN_WATCH_TRACE_TAKEN, NarrationId.KN_WATCH_TRACE_DONE,
                NarrationId.KN_WATCH_INSIGHT, NarrationId.KN_WATCH_SUMMARY,
            ),
            steps.map { it.headline.id },
        )
    }

    @Test
    fun `the walkthrough's numbers are read from the run`() {
        val steps = AlgorithmCatalog.zeroOneKnapsack().watchScript().steps
        // SKIP keeps dp[1][3] = 3; TAKE is 5 + dp[1][0] = 5.
        assertEquals(listOf(3, "Camera", 1, 3), steps[7].headline.args)
        assertEquals(ComparisonReadout(5, Relation.GREATER, 3), steps[8].comparison)
        // dp[2][5] reads dp[1][2] = 3 — the reuse.
        assertEquals(listOf(1, 2, 3), steps[10].support?.args)
        assertEquals(listOf("Book and Camera"), steps[11].support?.args)
        // The last cell: SKIP 8 against TAKE 7, and greedy named as the loser.
        assertEquals(ComparisonReadout(8, Relation.GREATER, 7), steps[13].comparison)
        assertEquals(NarrationId.KN_WATCH_ANSWER_GREEDY_SUPPORT, steps[14].support?.id)
        assertEquals(listOf("Book + Camera", 5, 8), steps[17].headline.args)
        assertEquals(ComparisonReadout(8, Relation.GREATER, 7), steps[18].comparison)
    }

    @Test
    fun `no two adjacent walkthrough steps look the same`() {
        val steps = AlgorithmCatalog.zeroOneKnapsack().watchScript().steps
        steps.zipWithNext().forEach { (a, b) ->
            val changed = a.scene != b.scene || a.comparison != b.comparison || a.bullets != b.bullets
            assertTrue("steps ${a.index} and ${b.index} are identical", changed)
        }
    }

    // ── The picture ──────────────────────────────────────────────────────────

    @Test
    fun `the table does not exist until the problem has been posed`() {
        val start = algorithm.initial(KnapsackDatasets.watch)
        assertFalse(projector.project(start, emptyList()).tableVisible)

        var state = start
        repeat(3) { state = algorithm.apply(state, KnapsackAction.Introduce).next }
        assertTrue(projector.project(state, emptyList()).tableVisible)
    }

    @Test
    fun `while TAKE's cell is being chosen, TAKE's side is still a question`() {
        val (state, _) = asked(tryIt).first { it.second.kind == DecisionKind.CELL }
        val scene: DpTableScene = projector.project(state, emptyList())
        val choice = assertNotNull(scene.choice).let { scene.choice!! }
        assertNull(choice.first.value)
        assertTrue(choice.first.formula.contains("?"))
        assertEquals(2, choice.second.value)

        val cells = scene.cells.flatten().filterNotNull()
        assertEquals(CellState.GHOST, scene.cells[2][5]?.state)
        assertEquals(CellState.CANDIDATE, scene.cells[1][5]?.state)
        assertTrue("nothing is lit as TAKE's cell yet", cells.none { it.state == CellState.COMPARING })
    }

    @Test
    fun `a no-fit question never prints the comparison`() {
        val (state, _) = asked(tryIt).first()
        val choice = projector.project(state, emptyList()).choice!!
        assertNull(choice.first.value)
        assertEquals("Tent weighs 4", choice.first.formula)
        assertEquals(2, choice.second.value)
    }

    @Test
    fun `the finished bag shows what was taken and what was not`() {
        val scene = projector.project(solve(watch), emptyList())
        assertEquals(
            listOf(CellState.FINALIZED, CellState.FINALIZED, CellState.ELIMINATED),
            scene.items.map { it.state },
        )
        val bag = scene.bag!!
        assertEquals(5, bag.used)
        assertEquals(8, bag.value)
        // One path cell per row, each green.
        assertEquals(CellState.FINALIZED, scene.cells[3][5]?.state)
        assertEquals(CellState.FINALIZED, scene.cells[2][5]?.state)
        assertEquals(CellState.FINALIZED, scene.cells[1][2]?.state)
    }

    // ── Wiring ───────────────────────────────────────────────────────────────

    @Test
    fun `progress for knapsack is 0 50 100 and belongs to knapsack alone`() {
        val watched = LearningProgress.EMPTY.complete(AlgorithmId.ZERO_ONE_KNAPSACK, Stage.WATCH)
        assertEquals(50, watched[AlgorithmId.ZERO_ONE_KNAPSACK].percent)
        assertEquals(0, watched[AlgorithmId.DIJKSTRA].percent)

        val finished = watched.complete(AlgorithmId.ZERO_ONE_KNAPSACK, Stage.TRY)
        val restored = ProgressCodec.decode(ProgressCodec.encode(finished))
        assertEquals(100, restored[AlgorithmId.ZERO_ONE_KNAPSACK].percent)
    }

    @Test
    fun `the catalogue carries the lesson and no challenge`() {
        val pack = AlgorithmCatalog.byId(AlgorithmId.ZERO_ONE_KNAPSACK)
        assertEquals(AlgorithmId.ZERO_ONE_KNAPSACK, pack.id)
        assertEquals("0/1 Knapsack", pack.displayName)
        assertNull(ChallengeCatalog.byId(AlgorithmId.ZERO_ONE_KNAPSACK))
    }
}
