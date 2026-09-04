package com.ttele.algoking.engine

import com.ttele.algoking.engine.algorithms.structures.LinearStructureAlgorithm
import com.ttele.algoking.engine.algorithms.structures.QueueFlavour
import com.ttele.algoking.engine.algorithms.structures.StackFlavour
import com.ttele.algoking.engine.algorithms.structures.StructureAction
import com.ttele.algoking.engine.algorithms.structures.StructureFlavour
import com.ttele.algoking.engine.algorithms.structures.StructureOp
import com.ttele.algoking.engine.algorithms.structures.StructureProjector
import com.ttele.algoking.engine.algorithms.structures.StructureState
import com.ttele.algoking.engine.algorithms.structures.StructureTask
import com.ttele.algoking.engine.algorithms.structures.StructureWatchNarrator
import com.ttele.algoking.engine.algorithms.structures.buildScript
import com.ttele.algoking.engine.catalog.AlgorithmCatalog
import com.ttele.algoking.engine.challenge.ChallengeGenerator
import com.ttele.algoking.engine.challenge.ChallengeType
import com.ttele.algoking.engine.core.AlgorithmId
import com.ttele.algoking.engine.core.AlgorithmRunner
import com.ttele.algoking.engine.core.Dataset
import com.ttele.algoking.engine.core.Probe
import com.ttele.algoking.engine.dataset.StructureDatasets
import com.ttele.algoking.engine.decision.DecisionKind
import com.ttele.algoking.engine.decision.DecisionValidation
import com.ttele.algoking.engine.decision.Validation
import com.ttele.algoking.engine.event.Outcome
import com.ttele.algoking.engine.scene.CellState
import com.ttele.algoking.engine.scene.SceneLayout
import com.ttele.algoking.engine.walkthrough.WatchScriptBuilder
import com.ttele.algoking.engine.walkthrough.WatchStepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stack and Queue.
 *
 * Almost every assertion here is written for **both** flavours from the same code,
 * because that is the claim the pair of lessons makes: one structure, one rule,
 * opposite answers. A test that could only be written once for each would mean the
 * abstraction had not earned its place.
 */
class StructureTest {

    private fun runner(flavour: StructureFlavour, dataset: Dataset) =
        AlgorithmRunner(LinearStructureAlgorithm(flavour), dataset)

    private fun playPerfectly(
        flavour: StructureFlavour,
        dataset: Dataset,
    ): AlgorithmRunner<StructureState, StructureAction> {
        val r = runner(flavour, dataset)
        var guard = 0
        while (guard++ < 512) {
            when (val probe = r.probe()) {
                is Probe.Mechanical -> r.apply(probe.action)
                is Probe.Decide -> r.apply(probe.decision.correct)
                is Probe.Terminal -> return r
            }
        }
        error("did not terminate")
    }

    /** Every value the structure handed back, in the order it handed them back. */
    private fun exitOrder(flavour: StructureFlavour, dataset: Dataset): List<Int> {
        val r = runner(flavour, dataset)
        val out = mutableListOf<Int>()
        var guard = 0
        while (guard++ < 512) {
            val before = r.current.state
            when (val probe = r.probe()) {
                is Probe.Mechanical -> r.apply(probe.action)
                is Probe.Decide -> r.apply(probe.decision.correct)
                is Probe.Terminal -> return out
            }
            val after = r.current.state
            if (after.items.size < before.items.size) {
                out += requireNotNull(after.lastRemoved)
            }
        }
        error("did not terminate")
    }

    private fun toDecision(
        r: AlgorithmRunner<StructureState, StructureAction>,
    ): Probe.Decide<StructureAction> {
        var guard = 0
        while (guard++ < 256) {
            when (val probe = r.probe()) {
                is Probe.Mechanical -> r.apply(probe.action)
                is Probe.Decide -> return probe
                is Probe.Terminal -> error("terminated before a decision")
            }
        }
        error("no decision")
    }

    // ── The one rule ──────────────────────────────────────────────────────────

    @Test
    fun `a stack hands items back in reverse order`() {
        val data = Dataset(listOf(12, 27, 41, 58))
        // Pushed 12 27 41, popped 41, pushed 58, then drained: 58 27 12.
        assertEquals(listOf(41, 58, 27, 12), exitOrder(StackFlavour, data))
    }

    @Test
    fun `a queue hands items back in arrival order`() {
        val data = Dataset(listOf(12, 27, 41, 58))
        assertEquals(listOf(12, 27, 41, 58), exitOrder(QueueFlavour, data))
    }

    @Test
    fun `the two structures disagree on the very first removal`() {
        val data = Dataset(listOf(12, 27, 41, 58))
        assertNotEquals(
            exitOrder(StackFlavour, data).first(),
            exitOrder(QueueFlavour, data).first(),
        )
    }

    @Test
    fun `both structures end empty and completed`() {
        for (flavour in listOf(StackFlavour, QueueFlavour)) {
            val r = playPerfectly(flavour, StructureDatasets.watch)
            assertTrue("${flavour.id} did not empty", r.current.state.empty)
            assertEquals(
                Outcome.Completed(correct = true),
                (r.probe() as Probe.Terminal).outcome,
            )
        }
    }

    // ── Edge cases: the lesson has to reach them, not dodge them ──────────────

    @Test
    fun `removing from an empty structure is narrated, not fatal`() {
        for (flavour in listOf(StackFlavour, QueueFlavour)) {
            val empty = StructureState(
                items = emptyList(),
                script = listOf(StructureTask.Operate(StructureOp.REMOVE)),
                at = 0,
            )
            val algorithm = LinearStructureAlgorithm(flavour)
            val t = algorithm.apply(empty, StructureAction.Perform(StructureOp.REMOVE))
            assertTrue("${flavour.id} should stay empty", t.next.items.isEmpty())
            assertTrue("${flavour.id} should still count as correct", t.correct)
            assertEquals(flavour.emptyNarration(), t.narration?.id)
        }
    }

    @Test
    fun `peeking an empty structure is narrated, not fatal`() {
        for (flavour in listOf(StackFlavour, QueueFlavour)) {
            val empty = StructureState(
                items = emptyList(),
                script = listOf(StructureTask.Operate(StructureOp.PEEK)),
                at = 0,
            )
            val t = LinearStructureAlgorithm(flavour)
                .apply(empty, StructureAction.Perform(StructureOp.PEEK))
            assertNull("${flavour.id} peeked at nothing", t.next.peeked)
            assertEquals(flavour.emptyNarration(), t.narration?.id)
        }
    }

    @Test
    fun `adding to a full structure is refused without losing what is already there`() {
        for (flavour in listOf(StackFlavour, QueueFlavour)) {
            val full = StructureState(
                items = listOf(1, 2, 3),
                script = listOf(StructureTask.Operate(StructureOp.ADD, 4)),
                at = 0,
                capacity = 3,
            )
            val t = LinearStructureAlgorithm(flavour)
                .apply(full, StructureAction.Perform(StructureOp.ADD))
            assertEquals("${flavour.id} lost items", listOf(1, 2, 3), t.next.items)
        }
    }

    @Test
    fun `the whole lesson runs without the structure ever overflowing`() {
        // Overflow is handled, but a scripted lesson should never *need* it: an
        // instruction the learner obeys should always do what it says.
        for (flavour in listOf(StackFlavour, QueueFlavour)) {
            for (dataset in listOf(StructureDatasets.watch, StructureDatasets.tryIt)) {
                val r = runner(flavour, dataset)
                var guard = 0
                while (guard++ < 512) {
                    val before = r.current.state
                    assertFalse(
                        "${flavour.id} was full when asked to add",
                        before.full && before.task.let {
                            it is StructureTask.Operate && it.op == StructureOp.ADD
                        },
                    )
                    when (val probe = r.probe()) {
                        is Probe.Mechanical -> r.apply(probe.action)
                        is Probe.Decide -> r.apply(probe.decision.correct)
                        is Probe.Terminal -> break
                    }
                }
            }
        }
    }

    // ── PEEK must not move anything ───────────────────────────────────────────

    @Test
    fun `peek leaves the items untouched`() {
        for (flavour in listOf(StackFlavour, QueueFlavour)) {
            val state = StructureState(
                items = listOf(5, 6, 7),
                script = listOf(StructureTask.Operate(StructureOp.PEEK)),
                at = 0,
            )
            val t = LinearStructureAlgorithm(flavour)
                .apply(state, StructureAction.Perform(StructureOp.PEEK))
            assertEquals(state.items, t.next.items)
            assertEquals(
                if (flavour.removesFromFront) 5 else 7,
                t.next.peeked,
            )
        }
    }

    // ── A wrong answer is a learning event, not a state transition ────────────

    @Test
    fun `wrong operations never touch the structure`() {
        for (flavour in listOf(StackFlavour, QueueFlavour)) {
            val r = runner(flavour, StructureDatasets.tryIt)
            val decision = toDecision(r).decision
            val before = r.current.state

            val wrong = decision.options
                .map { it.action }
                .filter { it != decision.correct }

            wrong.forEachIndexed { attempt, action ->
                val verdict = DecisionValidation.validate(decision, action, attempt)
                assertTrue("${flavour.id} accepted a wrong op", verdict is Validation.Retry)
            }
            assertEquals("${flavour.id} state moved on a miss", before, r.current.state)
        }
    }

    @Test
    fun `pointing at the wrong item never advances the script`() {
        for (flavour in listOf(StackFlavour, QueueFlavour)) {
            val r = runner(flavour, StructureDatasets.watch)
            var probe = toDecision(r)
            var guard = 0
            while (probe.decision.kind != DecisionKind.CELL && guard++ < 64) {
                r.apply(probe.decision.correct)
                probe = toDecision(r)
            }
            assertEquals(DecisionKind.CELL, probe.decision.kind)

            val before = r.current.state
            probe.decision.options
                .map { it.action }
                .filter { it != probe.decision.correct }
                .forEach { action ->
                    assertTrue(
                        "${flavour.id} accepted the wrong cell",
                        DecisionValidation.validate(probe.decision, action, 0)
                            is Validation.Retry,
                    )
                }
            assertEquals(before, r.current.state)
        }
    }

    @Test
    fun `the prediction asks about the end the structure actually uses`() {
        val data = Dataset(listOf(12, 27, 41, 58))
        // Both draw their live end at slot 0 — but they reach a different *value*
        // there, which is the whole point of asking.
        for (flavour in listOf(StackFlavour, QueueFlavour)) {
            val r = runner(flavour, data)
            var probe = toDecision(r)
            var guard = 0
            while (probe.decision.kind != DecisionKind.CELL && guard++ < 64) {
                r.apply(probe.decision.correct)
                probe = toDecision(r)
            }
            val state = r.current.state
            val correct = probe.decision.correct as StructureAction.Point
            val index = flavour.entryIndex(state.items.size, correct.slot)
            assertEquals(
                "${flavour.id} predicted the wrong item",
                if (flavour.removesFromFront) 12 else 41,
                state.items[index],
            )
        }
    }

    @Test
    fun `nothing in a structure lesson is answered for the learner`() {
        // `autoInTry` exists for bookkeeping the learner is not being taught. Every
        // decision here IS the lesson, so none of them may be auto-resolved.
        for (flavour in listOf(StackFlavour, QueueFlavour)) {
            val r = runner(flavour, StructureDatasets.tryIt)
            var guard = 0
            while (guard++ < 512) {
                when (val probe = r.probe()) {
                    is Probe.Mechanical -> r.apply(probe.action)
                    is Probe.Decide -> {
                        assertFalse(
                            "${flavour.id} auto-answers a real decision",
                            probe.decision.autoInTry,
                        )
                        r.apply(probe.decision.correct)
                    }

                    is Probe.Terminal -> break
                }
            }
        }
    }

    // ── Presentation ──────────────────────────────────────────────────────────

    @Test
    fun `a stack is drawn as a pile and a queue as a line`() {
        val data = Dataset(listOf(1, 2, 3))
        val state = StructureState(items = listOf(1, 2, 3), script = buildScript(data.values), at = 0)
        assertEquals(
            SceneLayout.PILE,
            StructureProjector(StackFlavour).project(state, emptyList()).layout,
        )
        assertEquals(
            SceneLayout.ROW,
            StructureProjector(QueueFlavour).project(state, emptyList()).layout,
        )
    }

    @Test
    fun `the reachable item is drawn first in both structures`() {
        val state = StructureState(
            items = listOf(1, 2, 3),
            script = listOf(StructureTask.Operate(StructureOp.PEEK)),
            at = 0,
        )
        // Stack: 3 went in last and sits on top. Queue: 1 has waited longest.
        assertEquals(
            3,
            StructureProjector(StackFlavour).project(state, emptyList())
                .cells.first { it.slot == 0 }.value,
        )
        assertEquals(
            1,
            StructureProjector(QueueFlavour).project(state, emptyList())
                .cells.first { it.slot == 0 }.value,
        )
    }

    @Test
    fun `the end labels disappear while the learner is being asked to predict`() {
        val state = StructureState(
            items = listOf(1, 2, 3),
            script = listOf(StructureTask.Predict(StructureOp.REMOVE)),
            at = 0,
        )
        for (flavour in listOf(StackFlavour, QueueFlavour)) {
            val scene = StructureProjector(flavour).project(state, emptyList())
            assertTrue("${flavour.id} left the answer on screen", scene.pointers.isEmpty())
            assertNull("${flavour.id} left an end cap on screen", scene.endCaps)
            assertTrue(
                "${flavour.id} highlighted the answer",
                scene.cells.none { it.state == CellState.CANDIDATE },
            )
        }
    }

    @Test
    fun `reaching into an empty structure draws the slot that was not there`() {
        for (flavour in listOf(StackFlavour, QueueFlavour)) {
            val emptied = StructureState(
                items = emptyList(),
                script = listOf(StructureTask.Operate(StructureOp.REMOVE)),
                at = 0,
                reachedEmpty = true,
            )
            val scene = StructureProjector(flavour).project(emptied, emptyList())
            // Without this the screen would be identical to the step before it, and
            // a walkthrough step that changes nothing teaches nothing.
            assertEquals(1, scene.cells.size)
            assertEquals(CellState.GHOST, scene.cells.single().state)
        }
    }

    @Test
    fun `only the queue names two ends`() {
        val state = StructureState(
            items = listOf(1, 2, 3),
            script = listOf(StructureTask.Operate(StructureOp.PEEK)),
            at = 0,
        )
        assertEquals(
            1,
            StructureProjector(StackFlavour).project(state, emptyList()).pointers.size,
        )
        assertEquals(
            2,
            StructureProjector(QueueFlavour).project(state, emptyList()).pointers.size,
        )
    }

    // ── The walkthrough ───────────────────────────────────────────────────────

    @Test
    fun `each structure has one walkthrough, one checkpoint, one insight`() {
        for (flavour in listOf(StackFlavour, QueueFlavour)) {
            val script = WatchScriptBuilder(
                LinearStructureAlgorithm(flavour),
                StructureProjector(flavour),
                StructureWatchNarrator(flavour),
            ).build(StructureDatasets.watch)

            val kinds = script.steps.map { it.kind }
            assertEquals("${flavour.id} checkpoints", 1, kinds.count { it == WatchStepKind.PREDICT })
            assertEquals("${flavour.id} insights", 1, kinds.count { it == WatchStepKind.INSIGHT })
            assertEquals(WatchStepKind.SETUP, kinds.first())
            assertEquals(WatchStepKind.SUMMARY, kinds.last())
            assertTrue("${flavour.id} never peeks", kinds.contains(WatchStepKind.PEEK))
            // Short enough to finish in one sitting.
            assertTrue("${flavour.id} walkthrough is ${script.size} steps", script.size <= 16)
        }
    }

    @Test
    fun `the checkpoint offers every item and marks exactly one right`() {
        for (flavour in listOf(StackFlavour, QueueFlavour)) {
            val script = WatchScriptBuilder(
                LinearStructureAlgorithm(flavour),
                StructureProjector(flavour),
                StructureWatchNarrator(flavour),
            ).build(StructureDatasets.watch)

            val prediction = script.steps.mapNotNull { it.prediction }.single()
            assertEquals(3, prediction.options.size)
            assertTrue(prediction.correctIndex in prediction.options.indices)
            // Slot 0 is the live end in both, by construction of the projector.
            assertEquals(0, prediction.correctIndex)
        }
    }

    @Test
    fun `every step of a walkthrough changes something`() {
        for (flavour in listOf(StackFlavour, QueueFlavour)) {
            val script = WatchScriptBuilder(
                LinearStructureAlgorithm(flavour),
                StructureProjector(flavour),
                StructureWatchNarrator(flavour),
            ).build(StructureDatasets.watch)

            script.steps.zipWithNext { a, b ->
                assertTrue(
                    "${flavour.id} step ${b.index} says nothing new",
                    a.scene != b.scene || a.headline != b.headline,
                )
            }
        }
    }

    // ── Catalog and challenges ────────────────────────────────────────────────

    @Test
    fun `both structures are in the catalog and share one dataset`() {
        assertEquals(AlgorithmId.STACK, AlgorithmCatalog.byId(AlgorithmId.STACK).id)
        assertEquals(AlgorithmId.QUEUE, AlgorithmCatalog.byId(AlgorithmId.QUEUE).id)
        assertEquals(
            "the comparison only works if the numbers match",
            AlgorithmCatalog.byId(AlgorithmId.STACK).watchDataset,
            AlgorithmCatalog.byId(AlgorithmId.QUEUE).watchDataset,
        )
    }

    @Test
    fun `challenge data never repeats the teaching data`() {
        val taught = (StructureDatasets.watch.values + StructureDatasets.tryIt.values).toSet()
        for (round in 1..8) {
            val challenge = ChallengeGenerator.structureForRound(round, round.toLong())
            assertEquals(ChallengeType.OPERATIONS, challenge.type)
            assertTrue(
                "round $round reused a taught value",
                challenge.dataset.values.none { it in taught },
            )
            assertEquals(
                "round $round repeated a value",
                challenge.dataset.values.size,
                challenge.dataset.values.toSet().size,
            )
        }
    }

    @Test
    fun `a challenge is reproducible from its seed`() {
        assertEquals(
            ChallengeGenerator.structureForRound(3, 77).dataset,
            ChallengeGenerator.structureForRound(3, 77).dataset,
        )
    }

    @Test
    fun `every generated challenge can be completed`() {
        for (flavour in listOf(StackFlavour, QueueFlavour)) {
            for (round in 1..6) {
                val challenge = ChallengeGenerator.structureForRound(round, round.toLong())
                val r = playPerfectly(flavour, challenge.dataset)
                assertTrue(
                    "${flavour.id} round $round did not finish",
                    r.probe() is Probe.Terminal,
                )
            }
        }
    }
}
