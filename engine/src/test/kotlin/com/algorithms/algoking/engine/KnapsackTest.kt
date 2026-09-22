package com.algorithms.algoking.engine

import com.algorithms.algoking.engine.algorithms.knapsack.IntroBeat
import com.algorithms.algoking.engine.algorithms.knapsack.KnapsackAction
import com.algorithms.algoking.engine.algorithms.knapsack.KnapsackAlgorithm
import com.algorithms.algoking.engine.algorithms.knapsack.KnapsackPhase
import com.algorithms.algoking.engine.algorithms.knapsack.KnapsackProjector
import com.algorithms.algoking.engine.algorithms.knapsack.KnapsackState
import com.algorithms.algoking.engine.algorithms.knapsack.TablePos
import com.algorithms.algoking.engine.algorithms.knapsack.TakeRule
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
 *
 * Since ADR-053 the first act makes claims of its own — everything does not fit,
 * one thing still does, and the bag packed by hand is beaten — and each of those is
 * a property of the authored data rather than of the copy. They are checked here
 * against the same brute force, because a lesson that says "this bag is not the
 * best" had better be right about it.
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

    /** The state at a named beat of the first act. */
    private fun atBeat(problem: KnapsackProblem, beat: IntroBeat): KnapsackState {
        val runner = runner(problem)
        var guard = 0
        while (guard++ < 10_000) {
            val state = runner.current.state
            if (state.phase == KnapsackPhase.PROBLEM && state.intro == beat) return state
            when (val probe = runner.probe()) {
                is Probe.Mechanical -> runner.apply(probe.action)
                is Probe.Decide -> runner.apply(probe.decision.correct)
                is Probe.Terminal -> error("$beat never happened")
            }
        }
        error("$beat never happened")
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
                listOf(0, 0, 0, 8, 8, 8),
                listOf(0, 0, 6, 8, 8, 14),
                listOf(0, 0, 6, 8, 10, 14),
                listOf(0, 3, 6, 9, 11, 14),
            ),
            tableOf(end),
        )
        assertEquals(14, end.best)
        assertEquals(listOf("Laptop", "Headphones"), end.bag.map { it.name })
        assertEquals(listOf(true, true, false, false), end.taken)
    }

    @Test
    fun `the try table is exactly the one the lesson teaches`() {
        val end = solve(tryIt)
        assertEquals(
            listOf(
                listOf(0, 0, 0, 0, 0, 0),
                listOf(0, 0, 0, 0, 10, 10),
                listOf(0, 2, 2, 2, 10, 12),
                listOf(0, 2, 5, 7, 10, 12),
                listOf(0, 2, 5, 9, 11, 14),
            ),
            tableOf(end),
        )
        assertEquals(14, end.best)
        assertEquals(listOf("Rope", "Stove"), end.bag.map { it.name })
        assertEquals(listOf(false, false, true, true), end.taken)
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
                    assertTrue("tie at dp[$i][$c] of ${p.items}", skip != take)
                }
            }
        }
        assertEquals(setOf("Laptop", "Headphones"), everyBag(watch).maxBy { it.value }.names)
        assertEquals(setOf("Rope", "Stove"), everyBag(tryIt).maxBy { it.value }.names)
    }

    /**
     * Every claim the first act makes, checked against brute force.
     *
     * ADR-053 asks the learner four questions before any table exists, and each one
     * only has a single true answer because the data was chosen to give it one. If
     * a bag is ever re-authored and one of these breaks, the lesson is asking a
     * question it cannot mark.
     */
    @Test
    fun `the first act's four questions each have exactly one true answer`() {
        for (p in listOf(watch, tryIt)) {
            val label = p.items.joinToString { it.name }

            // 1. "How much of this can you take?" — not all of it, and not even
            //    any three of it, so "only some" is the only honest answer.
            assertTrue("$label: everything fits", p.totalWeight > p.capacity)
            val lightestThree = p.items.sortedBy { it.weight }.take(3).sumOf { it.weight }
            assertTrue("$label: three of them fit", lightestThree > p.capacity)

            // 3. "What else still fits?" — exactly one thing does.
            val first = p.greedyBag.first()
            val room = p.capacity - first.weight
            val fitting = p.items.filter { it != first && it.weight <= room }
            assertEquals("$label: things that still fit", 1, fitting.size)
            assertEquals(p.greedyBag.drop(1), fitting)

            // 4. "Which bag is worth more?" — two different bags, and the one the
            //    learner packed by hand is the losing one.
            assertTrue("$label: greedy already optimal", p.greedyValue < p.bestValue)
            assertTrue("$label: same bag twice", p.greedyBag != p.bestBag)
            assertTrue("$label: the best bag does not fit", p.bestBag.sumOf { it.weight } <= p.capacity)
        }
    }

    @Test
    fun `the problem solves itself the same way the lesson's table does`() {
        for (p in listOf(watch, tryIt)) {
            val brute = everyBag(p).maxBy { it.value }
            assertEquals(brute.value, p.bestValue)
            assertEquals(brute.names, p.bestBag.map { it.name }.toSet())
            // And it agrees with the table the learner actually builds.
            assertEquals(solve(p).best, p.bestValue)
            assertEquals(solve(p).bag, p.bestBag)
        }
    }

    @Test
    fun `packing by hand gets 13 and 12, and the table beats both`() {
        assertEquals(listOf("Camera", "Watch"), watch.greedyBag.map { it.name })
        assertEquals(13, watch.greedyValue)
        assertEquals(14, watch.bestValue)

        assertEquals(listOf("Tent", "Water"), tryIt.greedyBag.map { it.name })
        assertEquals(12, tryIt.greedyValue)
        assertEquals(14, tryIt.bestValue)
    }

    @Test
    fun `the answer cell is a SKIP in watch and a TAKE in try`() {
        val watchLast = asked(watch).last {
            it.second.correct == KnapsackAction.Skip || it.second.correct == KnapsackAction.Take
        }
        assertEquals(TablePos(4, 5), watchLast.first.position)
        assertEquals(KnapsackAction.Skip, watchLast.second.correct)

        val tryLast = asked(tryIt).last {
            it.second.correct == KnapsackAction.Skip || it.second.correct == KnapsackAction.Take
        }
        assertEquals(TablePos(4, 5), tryLast.first.position)
        assertEquals(KnapsackAction.Take, tryLast.second.correct)
    }

    /** TRY must not be answerable from a memorised WATCH. */
    @Test
    fun `the two bags disagree everywhere a memorised run would be reused`() {
        assertTrue(watch.items.map { it.name }.intersect(tryIt.items.map { it.name }.toSet()).isEmpty())
        assertEquals(listOf(true, true, false, false), solve(watch).taken)
        assertEquals(listOf(false, false, true, true), solve(tryIt).taken)
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
                    val p = problem(
                        cap,
                        Triple("A", w1, v1),
                        Triple("B", w2, v2),
                        Triple("C", w3, v3),
                    )
                    val end = solve(p)
                    assertEquals("table for ${p.items} cap $cap", referenceTable(p), tableOf(end))
                    assertEquals("bag for ${p.items} cap $cap", referenceBag(p), end.bag.map { it.name }.toSet())
                    assertEquals("best for ${p.items} cap $cap", p.bestValue, end.best)
                    assertEquals("bag helper for ${p.items} cap $cap", end.bag, p.bestBag)
                }
    }

    @Test
    fun `an empty item list is a finished lesson, not a crash`() {
        val p = KnapsackProblem(emptyList(), 5)
        val end = solve(p)
        assertEquals(KnapsackPhase.DONE, end.phase)
        assertEquals(0, end.best)
        assertTrue(end.bag.isEmpty())
        assertTrue(asked(p).isEmpty())
    }

    @Test
    fun `zero capacity asks nothing and takes nothing`() {
        val p = problem(0, Triple("A", 2, 3))
        val end = solve(p)
        assertEquals(KnapsackPhase.DONE, end.phase)
        assertEquals(0, end.best)
        assertTrue(end.bag.isEmpty())
        assertTrue(asked(p).isEmpty())
    }

    @Test
    fun `a single item that fits is taken, and only the first act and the walk back are asked`() {
        val p = problem(5, Triple("A", 2, 3))
        val end = solve(p)
        assertEquals(3, end.best)
        assertEquals(listOf("A"), end.bag.map { it.name })
        // Row 1 is never asked, so the table contributes nothing here.
        val build = asked(p).filter { it.first.phase == KnapsackPhase.BUILD }
        assertTrue(build.isEmpty())
        assertEquals(1, asked(p).count { it.first.phase == KnapsackPhase.TRACE })
    }

    @Test
    fun `a single item heavier than the bag is left out`() {
        val p = problem(3, Triple("A", 4, 9))
        val end = solve(p)
        assertEquals(0, end.best)
        assertTrue(end.bag.isEmpty())
        assertEquals(listOf(false), end.taken)
    }

    @Test
    fun `when nothing fits, every row copies and everything is left out`() {
        val p = problem(2, Triple("A", 3, 5), Triple("B", 4, 9), Triple("C", 5, 11))
        val end = solve(p)
        assertEquals(0, end.best)
        assertTrue(end.bag.isEmpty())
        assertEquals(listOf(false, false, false), end.taken)
        assertEquals(referenceTable(p), tableOf(end))
    }

    @Test
    fun `duplicate weights and duplicate values are handled like anything else`() {
        val p = problem(5, Triple("A", 2, 4), Triple("B", 2, 4), Triple("C", 3, 4))
        val end = solve(p)
        assertEquals(referenceTable(p), tableOf(end))
        assertEquals(referenceBag(p), end.bag.map { it.name }.toSet())
    }

    @Test
    fun `with two optimal bags the engine reports the one SKIP-on-a-tie produces`() {
        // A + B = 7 and C = 7. dp[3][5] has SKIP 7 against TAKE 7 + 0 = 7.
        val p = problem(5, Triple("A", 2, 3), Triple("B", 3, 4), Triple("C", 5, 7))
        assertEquals(2, everyBag(p).count { it.value == 7 })
        val end = solve(p)
        assertEquals(7, end.best)
        assertEquals(setOf("A", "B"), end.bag.map { it.name }.toSet())
        assertEquals(end.bag, p.bestBag)
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

    /**
     * The whole of TRY, pinned.
     *
     * Four questions about the problem, five about the table, four walking back up
     * it — and the story half comes first, which is the point of ADR-053 and is
     * asserted separately below.
     */
    @Test
    fun `try asks exactly thirteen questions, in this order`() {
        val questions = asked(tryIt)

        val intro = questions.filter { it.first.phase == KnapsackPhase.PROBLEM }
        assertEquals(
            listOf(
                IntroBeat.ITEMS to KnapsackAction.AnswerCapacity(all = false),
                IntroBeat.TOO_MUCH to KnapsackAction.AnswerTimes(TakeRule.ONCE),
                IntroBeat.ONCE to KnapsackAction.AnswerFits("Water"),
                IntroBeat.PACKED to KnapsackAction.AnswerBetter(optimal = true),
            ),
            intro.map { (state, d) -> state.intro to d.correct },
        )

        val build = questions.filter { it.first.phase == KnapsackPhase.BUILD }
        assertEquals(
            listOf(
                Triple(TablePos(2, 5), DecisionKind.CELL, KnapsackAction.PickSource(1, 4)),
                Triple(TablePos(2, 5), DecisionKind.OPTIONS, KnapsackAction.Take),
                Triple(TablePos(3, 1), DecisionKind.OPTIONS, KnapsackAction.Skip),
                Triple(TablePos(3, 5), DecisionKind.OPTIONS, KnapsackAction.Skip),
                Triple(TablePos(4, 5), DecisionKind.OPTIONS, KnapsackAction.Take),
            ),
            build.map { (state, d) -> Triple(state.position, d.kind, d.correct) },
        )

        val trace = questions.filter { it.first.phase == KnapsackPhase.TRACE }
        assertEquals(
            listOf(
                KnapsackAction.MarkTaken,
                KnapsackAction.MarkTaken,
                KnapsackAction.MarkLeftOut,
                KnapsackAction.MarkLeftOut,
            ),
            trace.map { it.second.correct },
        )
        assertEquals(13, questions.size)
    }

    @Test
    fun `watch asks the same thirteen, and the same shape`() {
        val questions = asked(watch)
        assertEquals(13, questions.size)
        assertEquals(4, questions.count { it.first.phase == KnapsackPhase.PROBLEM })
        assertEquals(5, questions.count { it.first.phase == KnapsackPhase.BUILD })
        assertEquals(4, questions.count { it.first.phase == KnapsackPhase.TRACE })
    }

    /**
     * ADR-053's rule, as a test: **the problem is understood before the table
     * appears.** Every question that needs no table comes before every question
     * that is about one.
     */
    @Test
    fun `the story is asked before the table`() {
        for (p in listOf(watch, tryIt)) {
            val phases = asked(p).map { it.first.phase }
            val lastProblem = phases.indexOfLast { it == KnapsackPhase.PROBLEM }
            val firstTable = phases.indexOfFirst { it != KnapsackPhase.PROBLEM }
            assertTrue("${p.items}: the table is asked about before the problem is posed", lastProblem < firstTable)
        }
    }

    /**
     * The table is a tool, not a drill.
     *
     * ADR-053 cut the build from every fitting cell to the ones that decide
     * something: one boundary, one reuse, and the cell that settles each row. If
     * this number climbs back up, the lesson has quietly become the thing it was
     * rewritten to stop being.
     */
    @Test
    fun `the learner fills five cells of thirty, not all of them`() {
        for (p in listOf(watch, tryIt)) {
            val build = asked(p).filter { it.first.phase == KnapsackPhase.BUILD }
            assertEquals("${p.items}: cells asked", 5, build.size)
            assertEquals("${p.items}: source questions", 1, build.count { it.second.kind == DecisionKind.CELL })
            assertEquals("${p.items}: no-fit questions", 1, build.count { !it.first.fits })
            assertEquals(30, (p.items.size + 1) * (p.capacity + 1))
        }
    }

    @Test
    fun `row 1 and column 0 are never asked, and the last column of every later row is`() {
        for (p in listOf(watch, tryIt)) {
            val build = asked(p).filter { it.first.phase == KnapsackPhase.BUILD }
            assertTrue(build.all { it.first.row >= 2 && it.first.col >= 1 })
            for (row in 2..p.items.size) {
                assertEquals(
                    "row $row of ${p.items} decides its last column",
                    1,
                    build.count { it.first.row == row && it.first.col == p.capacity && it.second.kind == DecisionKind.OPTIONS },
                )
            }
        }
    }

    @Test
    fun `every decision is well formed`() {
        for (p in listOf(watch, tryIt)) {
            for ((_, decision) in asked(p)) {
                assertTrue(
                    "${decision.prompt.id}: correct is not an option",
                    decision.options.any { it.action == decision.correct },
                )
                assertTrue("${decision.prompt.id}: too few options", decision.options.size >= 2)
                assertEquals(
                    "${decision.prompt.id}: guidance is not three rungs",
                    3,
                    decision.guidance.size,
                )
                assertFalse("${decision.prompt.id}: answered by the app", decision.autoInTry)
                for (option in decision.options) {
                    if (option.action == decision.correct) continue
                    assertNotNull(
                        "${decision.prompt.id}: ${option.action} has no reason",
                        decision.whyWrong[option.action],
                    )
                }
            }
        }
    }

    @Test
    fun `the 0 1 question rules out the other two knapsacks by name`() {
        val (_, decision) = asked(watch).first { it.first.intro == IntroBeat.TOO_MUCH }
        assertEquals(KnapsackAction.AnswerTimes(TakeRule.ONCE), decision.correct)
        assertEquals(
            NarrationId.KN_WHY_MANY,
            decision.whyWrong[KnapsackAction.AnswerTimes(TakeRule.ANY_NUMBER)]?.id,
        )
        assertEquals(
            NarrationId.KN_WHY_FRACTION,
            decision.whyWrong[KnapsackAction.AnswerTimes(TakeRule.FRACTION)]?.id,
        )
    }

    @Test
    fun `a same-row tap is refused with the 0 1 reason`() {
        val (state, decision) = asked(watch).first { it.second.kind == DecisionKind.CELL }
        val sameRow = decision.options
            .map { it.action as KnapsackAction.PickSource }
            .first { it.row == state.row }
        assertEquals(NarrationId.KN_WHY_SAME_ROW, decision.whyWrong[sameRow]?.id)
    }

    @Test
    fun `every wrong answer leaves the state byte-for-byte identical`() {
        for (p in listOf(watch, tryIt)) {
            val runner = runner(p)
            var guard = 0
            while (guard++ < 10_000) {
                when (val probe = runner.probe()) {
                    is Probe.Mechanical -> runner.apply(probe.action)
                    is Probe.Decide -> {
                        val before = runner.current.state
                        for (option in probe.decision.options) {
                            if (option.action == probe.decision.correct) continue
                            assertTrue(
                                "a wrong answer was accepted",
                                DecisionValidation.validate(probe.decision, option.action, 0) is Validation.Retry,
                            )
                            assertEquals(before, algorithm.apply(before, option.action).next)
                            assertFalse(algorithm.apply(before, option.action).correct)
                        }
                        runner.apply(probe.decision.correct)
                    }
                    is Probe.Terminal -> break
                }
            }
        }
    }

    @Test
    fun `an item that does not fit cannot be taken`() {
        val (state, _) = asked(watch).first { it.first.phase == KnapsackPhase.BUILD && !it.first.fits }
        val after = algorithm.apply(state, KnapsackAction.Take)
        assertEquals(state, after.next)
        assertFalse(after.correct)
    }

    @Test
    fun `the first act refuses every wrong answer too`() {
        val items = atBeat(watch, IntroBeat.ITEMS)
        assertEquals(items, algorithm.apply(items, KnapsackAction.AnswerCapacity(all = true)).next)

        val once = atBeat(watch, IntroBeat.TOO_MUCH)
        assertEquals(once, algorithm.apply(once, KnapsackAction.AnswerTimes(TakeRule.ANY_NUMBER)).next)

        val fits = atBeat(watch, IntroBeat.ONCE)
        assertEquals(fits, algorithm.apply(fits, KnapsackAction.AnswerFits("Laptop")).next)
        assertEquals(fits, algorithm.apply(fits, KnapsackAction.AnswerFits(null)).next)

        val better = atBeat(watch, IntroBeat.PACKED)
        assertEquals(better, algorithm.apply(better, KnapsackAction.AnswerBetter(optimal = false)).next)
    }

    @Test
    fun `driven adversarially, the run still ends on the right table`() {
        for (p in listOf(watch, tryIt)) {
            val runner = runner(p)
            var guard = 0
            var wrongTurns = 0
            while (guard++ < 10_000) {
                when (val probe = runner.probe()) {
                    is Probe.Mechanical -> runner.apply(probe.action)
                    is Probe.Decide -> {
                        // Always try a wrong one first; it must change nothing.
                        val wrong = probe.decision.options.firstOrNull { it.action != probe.decision.correct }
                        if (wrong != null) {
                            val before = runner.current.state
                            runner.apply(wrong.action)
                            assertEquals(before, runner.current.state)
                            wrongTurns++
                        }
                        runner.apply(probe.decision.correct)
                    }
                    is Probe.Terminal -> break
                }
            }
            assertEquals(13, wrongTurns)
            assertEquals(referenceTable(p), tableOf(runner.current.state))
            assertEquals(referenceBag(p), runner.current.state.bag.map { it.name }.toSet())
        }
    }

    @Test
    fun `rewinding a decision restores the exact prior state`() {
        val runner = runner(watch)
        var guard = 0
        while (guard++ < 10_000) {
            when (val probe = runner.probe()) {
                is Probe.Mechanical -> runner.apply(probe.action)
                is Probe.Decide -> {
                    val before = runner.current.state
                    runner.apply(probe.decision.correct)
                    runner.rewind(1)
                    assertEquals(before, runner.current.state)
                    runner.apply(probe.decision.correct)
                }
                is Probe.Terminal -> return
            }
        }
    }

    @Test
    fun `completion comes only once the walk back reaches row 0`() {
        val runner = runner(watch)
        var guard = 0
        while (guard++ < 10_000) {
            when (val probe = runner.probe()) {
                is Probe.Mechanical -> runner.apply(probe.action)
                is Probe.Decide -> {
                    assertNull("finished early", (runner.probe() as? Probe.Terminal)?.outcome)
                    runner.apply(probe.decision.correct)
                }
                is Probe.Terminal -> {
                    assertEquals(Outcome.Completed(correct = true), probe.outcome)
                    assertEquals(0, runner.current.state.traceRow)
                    assertTrue(runner.current.state.taken.all { it != null })
                    return
                }
            }
        }
        error("never finished")
    }

    @Test
    fun `comparisons counts one per cell where the item fits`() {
        val trace = runner(watch).runToCompletion()
        // Row 1: capacities 3-5. Row 2: 2-5. Row 3: 4-5. Row 4: 1-5.
        assertEquals(3 + 4 + 2 + 5, trace.frames.last().metrics.comparisons)
    }

    // ── WATCH ────────────────────────────────────────────────────────────────

    /**
     * The walkthrough, pinned. Nine beats of problem, then the table.
     *
     * The first nine are ADR-053: the bag, the things, the overflow, the 0/1 rule,
     * a bag packed by hand, a better one, the size of the brute force, and the fork
     * — all of it before `KN_WATCH_DEFINE` says the word `dp` for the first time.
     */
    @Test
    fun `the walkthrough is exactly the twenty-nine beats the lesson was designed as`() {
        val steps = AlgorithmCatalog.zeroOneKnapsack().watchScript().steps
        assertEquals(
            listOf(
                WatchStepKind.SETUP, WatchStepKind.EXAMINE, WatchStepKind.COMPARE, WatchStepKind.EXAMINE,
                WatchStepKind.ADD, WatchStepKind.ADD, WatchStepKind.COMPARE, WatchStepKind.EXAMINE,
                WatchStepKind.EXAMINE, WatchStepKind.EXAMINE, WatchStepKind.EXAMINE, WatchStepKind.EXAMINE,
                WatchStepKind.EXAMINE, WatchStepKind.ADD, WatchStepKind.EXAMINE, WatchStepKind.KEEP,
                WatchStepKind.COMPARE, WatchStepKind.ADD, WatchStepKind.COMPARE, WatchStepKind.ADD,
                WatchStepKind.EXAMINE, WatchStepKind.COMPARE, WatchStepKind.KEEP, WatchStepKind.ELIMINATE,
                WatchStepKind.ELIMINATE, WatchStepKind.ADD, WatchStepKind.FOUND, WatchStepKind.INSIGHT,
                WatchStepKind.SUMMARY,
            ),
            steps.map { it.kind },
        )
        assertEquals(
            listOf(
                NarrationId.KN_WATCH_BAG, NarrationId.KN_WATCH_ITEMS, NarrationId.KN_WATCH_TOO_MUCH,
                NarrationId.KN_WATCH_ONCE, NarrationId.KN_WATCH_PACKING, NarrationId.KN_WATCH_PACKED,
                NarrationId.KN_WATCH_COMPARED, NarrationId.KN_WATCH_EVERY_BAG, NarrationId.KN_WATCH_FORK,
                NarrationId.KN_WATCH_GRID, NarrationId.KN_WATCH_AXES, NarrationId.KN_WATCH_NAME,
                NarrationId.KN_WATCH_DEFINE, NarrationId.KN_WATCH_FIRST_ROW, NarrationId.KN_WATCH_NO_FIT,
                NarrationId.KN_WATCH_SKIP_SIDE, NarrationId.KN_WATCH_TAKE_SIDE, NarrationId.KN_WATCH_MAX,
                NarrationId.KN_WATCH_REUSE, NarrationId.KN_WATCH_REUSE_RESULT,
                NarrationId.KN_WATCH_ROW_SUMMARY, NarrationId.KN_WATCH_LAST_CELL,
                NarrationId.KN_WATCH_ANSWER_SKIP, NarrationId.KN_WATCH_TRACE_START,
                NarrationId.KN_WATCH_TRACE_LEFT_OUT, NarrationId.KN_WATCH_TRACE_TAKEN,
                NarrationId.KN_WATCH_TRACE_DONE, NarrationId.KN_WATCH_INSIGHT,
                NarrationId.KN_WATCH_SUMMARY,
            ),
            steps.map { it.headline.id },
        )
    }

    @Test
    fun `the walkthrough's numbers are read from the run`() {
        val steps = AlgorithmCatalog.zeroOneKnapsack().watchScript().steps
        // Everything weighs 10 into a bag that holds 5 — the founding fact.
        assertEquals(listOf(10, 5), steps[2].headline.args)
        assertEquals(ComparisonReadout(10, Relation.GREATER, 5), steps[2].comparison)
        // The Camera is taken first, and leaves 1 kg.
        assertEquals(listOf("Camera", 10), steps[4].headline.args)
        assertEquals(listOf(4, 5, 4, 1), steps[4].support?.args)
        // The bag packed by hand, and the better one that beats it.
        assertEquals(listOf("Camera + Watch", 5, 13), steps[5].headline.args)
        assertEquals(listOf("Laptop + Headphones", 14), steps[6].headline.args)
        assertEquals(ComparisonReadout(14, Relation.GREATER, 13), steps[6].comparison)
        assertEquals(listOf(16L), steps[7].headline.args)
        // dp[2][5] reads dp[1][3] = 8 — the reuse, and what that cell means.
        assertEquals(listOf(1, 3, 8), steps[18].support?.args)
        assertEquals(listOf("Laptop and Headphones"), steps[19].support?.args)
        // The last cell: SKIP 14 against TAKE 13, and the hand-packed bag named as the loser.
        assertEquals(ComparisonReadout(14, Relation.GREATER, 13), steps[21].comparison)
        assertEquals(NarrationId.KN_WATCH_ANSWER_GREEDY_SUPPORT, steps[22].support?.id)
        assertEquals(listOf("Laptop + Headphones", 5, 14), steps[26].headline.args)
        assertEquals(ComparisonReadout(14, Relation.GREATER, 13), steps[27].comparison)
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
    fun `the table does not exist until the whole problem has been posed`() {
        for (beat in IntroBeat.entries.filter { it < IntroBeat.GRID }) {
            val scene = projector.project(atBeat(watch, beat), emptyList())
            assertFalse("a table exists at $beat", scene.tableVisible)
        }
        assertTrue(projector.project(atBeat(watch, IntroBeat.GRID), emptyList()).tableVisible)
        assertTrue(projector.project(solve(watch), emptyList()).tableVisible)
    }

    /**
     * The table is explained before it is filled, and **`dp` is the last thing
     * said about it, not the first**.
     *
     * The grid arrives empty, then a row and a column light up and the box where
     * they cross is named in words, and only then does the shorthand appear. A
     * learner who meets `dp[2][5]` before any of that has been handed notation to
     * memorise, which is the complaint ADR-053 exists to answer.
     */
    @Test
    fun `the grid is shown, then what a box means, and only then what it is called`() {
        val grid = projector.project(atBeat(watch, IntroBeat.GRID), emptyList())
        assertTrue("the grid should be on screen", grid.tableVisible)
        assertTrue("every box is empty", grid.cells.flatten().all { it == null })
        assertTrue("the cards should give the table the screen", grid.items.isEmpty())
        assertNull("no bag beside the grid", grid.bag)
        assertNull("nothing is pointed at yet", grid.focusCaption)
        assertTrue("nothing is highlighted yet", grid.rowHeaders.none { it.active })
        assertTrue("nothing is highlighted yet", grid.columnHeaders.none { it.active })

        val axes = projector.project(atBeat(watch, IntroBeat.AXES), emptyList())
        // One row and one column, crossing on the box being explained.
        assertEquals(listOf("Headphones"), axes.rowHeaders.filter { it.active }.map { it.label })
        assertEquals(listOf("5"), axes.columnHeaders.filter { it.active }.map { it.label })
        assertEquals(CellState.GHOST, axes.cells[2][5]?.state)
        assertTrue(
            "only the box being explained is lit",
            axes.cells.flatten().filterNotNull().all { it.state == CellState.GHOST },
        )
        assertEquals(
            "This box: the best you can do with Laptop and Headphones, and 5 kg of room",
            axes.focusCaption,
        )

        val name = projector.project(atBeat(watch, IntroBeat.NAME), emptyList())
        assertEquals("dp[2][5] — row 2, column 5", name.focusCaption)

        // And `dp` is said nowhere before that beat.
        for (beat in IntroBeat.entries.filter { it < IntroBeat.NAME }) {
            val scene = projector.project(atBeat(watch, beat), emptyList())
            assertFalse("$beat mentions dp", scene.focusCaption.orEmpty().contains("dp["))
        }
    }

    @Test
    fun `the first act draws a bag being packed, and no dp anywhere`() {
        val bagOnly = projector.project(atBeat(watch, IntroBeat.BAG), emptyList())
        assertTrue("cards arrive with the items beat", bagOnly.items.isEmpty())
        assertEquals(0, bagOnly.bag?.used)
        assertEquals(5, bagOnly.bag?.capacity)

        val packing = projector.project(atBeat(watch, IntroBeat.PACKING), emptyList())
        assertEquals(listOf("Camera"), packing.bag?.contents)
        assertEquals(4, packing.bag?.used)
        assertEquals(10, packing.bag?.value)
        assertEquals("5 − 4 = 1 kg left", packing.focusCaption)

        val packed = projector.project(atBeat(watch, IntroBeat.PACKED), emptyList())
        assertEquals(listOf("Camera", "Watch"), packed.bag?.contents)
        assertEquals(13, packed.bag?.value)

        // Nothing in the packing story names a cell; the grid beats are checked
        // on their own, above.
        for (beat in IntroBeat.entries.filter { it < IntroBeat.GRID }) {
            val scene = projector.project(atBeat(watch, beat), emptyList())
            assertFalse("$beat mentions dp", scene.focusCaption.orEmpty().contains("dp["))
        }
    }

    @Test
    fun `while the two bags are being compared, neither total is on screen`() {
        val asking = projector.project(atBeat(watch, IntroBeat.PACKED), emptyList())
        val strip = requireNotNull(asking.choice)
        assertEquals("Camera + Watch", strip.first.caption)
        assertEquals("10 + 3", strip.first.formula)
        assertNull("the hand-packed total is readable", strip.first.value)
        assertEquals("Laptop + Headphones", strip.second.caption)
        assertEquals("8 + 6", strip.second.formula)
        assertNull("the answer is readable", strip.second.value)

        val answered = requireNotNull(projector.project(atBeat(watch, IntroBeat.COMPARED), emptyList()).choice)
        assertEquals(13, answered.first.value)
        assertEquals(14, answered.second.value)
    }

    @Test
    fun `the fork states the rule with no numbers in it`() {
        val fork = requireNotNull(projector.project(atBeat(watch, IntroBeat.FORK), emptyList()).choice)
        assertEquals("Every item, one at a time", fork.stem)
        assertEquals("Take", fork.first.caption)
        assertEquals("Skip", fork.second.caption)
        assertNull(fork.first.value)
        assertNull(fork.second.value)
    }

    @Test
    fun `the cards carry 0 and 1 for the bag on screen`() {
        val packed = projector.project(atBeat(watch, IntroBeat.PACKED), emptyList())
        assertEquals(
            // Laptop, Headphones out; Camera, Watch in — the bag packed by hand.
            listOf("0", "0", "1", "1"),
            packed.items.map { it.bitLabel },
        )
        val compared = projector.project(atBeat(watch, IntroBeat.COMPARED), emptyList())
        assertEquals(listOf("1", "1", "0", "0"), compared.items.map { it.bitLabel })
    }

    @Test
    fun `while TAKE's cell is being chosen, TAKE's side is still a question`() {
        val (state, _) = asked(tryIt).first { it.second.kind == DecisionKind.CELL }
        val scene: DpTableScene = projector.project(state, emptyList())
        val choice = requireNotNull(scene.choice)
        assertNull(choice.first.value)
        assertTrue(choice.first.formula.contains("?"))
        assertEquals(10, choice.second.value)

        val cells = scene.cells.flatten().filterNotNull()
        assertEquals(CellState.GHOST, scene.cells[2][5]?.state)
        assertEquals(CellState.CANDIDATE, scene.cells[1][5]?.state)
        assertTrue("nothing is lit as TAKE's cell yet", cells.none { it.state == CellState.COMPARING })
    }

    @Test
    fun `a no-fit question never prints the comparison`() {
        val (state, _) = asked(tryIt).first { it.first.phase == KnapsackPhase.BUILD && !it.first.fits }
        val choice = requireNotNull(projector.project(state, emptyList()).choice)
        assertNull(choice.first.value)
        assertEquals("Rope weighs 2", choice.first.formula)
        assertEquals(2, choice.second.value)
    }

    @Test
    fun `the finished bag shows what was taken and what was not`() {
        val scene = projector.project(solve(watch), emptyList())
        assertEquals(
            listOf(CellState.FINALIZED, CellState.FINALIZED, CellState.ELIMINATED, CellState.ELIMINATED),
            scene.items.map { it.state },
        )
        val bag = requireNotNull(scene.bag)
        assertEquals(5, bag.used)
        assertEquals(14, bag.value)
        // One path cell per row, each green.
        assertEquals(CellState.FINALIZED, scene.cells[4][5]?.state)
        assertEquals(CellState.FINALIZED, scene.cells[3][5]?.state)
        assertEquals(CellState.FINALIZED, scene.cells[2][5]?.state)
        assertEquals(CellState.FINALIZED, scene.cells[1][3]?.state)
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
