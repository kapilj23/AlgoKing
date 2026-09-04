package com.ttele.algoking.engine

import com.ttele.algoking.engine.algorithms.linkedlist.LinkedListAlgorithm
import com.ttele.algoking.engine.algorithms.linkedlist.LinkedListProjector
import com.ttele.algoking.engine.algorithms.linkedlist.LinkedListState
import com.ttele.algoking.engine.algorithms.linkedlist.LinkedListWatchNarrator
import com.ttele.algoking.engine.algorithms.linkedlist.LinkedNode
import com.ttele.algoking.engine.algorithms.linkedlist.ListAction
import com.ttele.algoking.engine.algorithms.linkedlist.ListPhase
import com.ttele.algoking.engine.algorithms.linkedlist.ListScripts
import com.ttele.algoking.engine.algorithms.linkedlist.ListTask
import com.ttele.algoking.engine.catalog.AlgorithmCatalog
import com.ttele.algoking.engine.challenge.ChallengeGenerator
import com.ttele.algoking.engine.challenge.ChallengeType
import com.ttele.algoking.engine.core.AlgorithmId
import com.ttele.algoking.engine.core.AlgorithmRunner
import com.ttele.algoking.engine.core.Dataset
import com.ttele.algoking.engine.core.Probe
import com.ttele.algoking.engine.dataset.LinkedListDatasets
import com.ttele.algoking.engine.decision.DecisionKind
import com.ttele.algoking.engine.decision.DecisionValidation
import com.ttele.algoking.engine.decision.Validation
import com.ttele.algoking.engine.event.Outcome
import com.ttele.algoking.engine.narration.NarrationId
import com.ttele.algoking.engine.scene.LinkState
import com.ttele.algoking.engine.scene.SceneLayout
import com.ttele.algoking.engine.walkthrough.WatchScriptBuilder
import com.ttele.algoking.engine.walkthrough.WatchStepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Linked List.
 *
 * The claim these tests defend is that the lesson is about **links**: every
 * operation is two link decisions, a wrong one changes nothing, and the list is
 * never left with a dangling arrow.
 */
class LinkedListTest {

    private fun runner(dataset: Dataset) = AlgorithmRunner(LinkedListAlgorithm(), dataset)

    private fun playPerfectly(dataset: Dataset): AlgorithmRunner<LinkedListState, ListAction> {
        val r = runner(dataset)
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

    private fun toDecision(
        r: AlgorithmRunner<LinkedListState, ListAction>,
    ): Probe.Decide<ListAction> {
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

    /** Advance to the first decision of the given kind. */
    private fun decisionOfKind(
        r: AlgorithmRunner<LinkedListState, ListAction>,
        kind: DecisionKind,
    ): Probe.Decide<ListAction> {
        var probe = toDecision(r)
        var guard = 0
        while (probe.decision.kind != kind && guard++ < 64) {
            r.apply(probe.decision.correct)
            probe = toDecision(r)
        }
        assertEquals(kind, probe.decision.kind)
        return probe
    }

    private fun state(values: List<Int>, script: List<ListTask>, at: Int = 0) = LinkedListState(
        nodes = values.mapIndexed { index, value -> LinkedNode(index, value) },
        script = script,
        at = at,
        phase = if (script.getOrNull(at) is ListTask.Find) ListPhase.WALKING else ListPhase.CHOOSING_GAP,
        cursor = if (script.getOrNull(at) is ListTask.Find) 0 else null,
    )

    // ── The operations do what they say ───────────────────────────────────────

    @Test
    fun `the watch lesson searches, inserts and deletes`() {
        val r = playPerfectly(LinkedListDatasets.watch)
        // 15 went in between 10 and 20, then came straight back out again.
        assertEquals(listOf(10, 20, 30, 40), r.current.state.values)
        assertEquals(
            Outcome.Completed(correct = true),
            (r.probe() as Probe.Terminal).outcome,
        )
    }

    @Test
    fun `the try lesson ends with a list the learner built`() {
        val r = playPerfectly(LinkedListDatasets.tryIt)
        // 5 12 18 25 → find 18 → insert 8 → insert 3 at the head → delete 18.
        assertEquals(listOf(3, 5, 8, 12, 25), r.current.state.values)
    }

    @Test
    fun `an insertion keeps the list in order`() {
        for (round in 1..8) {
            val challenge = ChallengeGenerator.listForRound(round, round.toLong())
            val values = playPerfectly(challenge.dataset).current.state.values
            assertEquals("round $round left the list out of order", values.sorted(), values)
        }
    }

    // ── Every operation is two link decisions ─────────────────────────────────

    @Test
    fun `insertion asks for the gap and then for the new node's NEXT`() {
        val r = runner(Dataset(listOf(10, 20, 30), target = 15, label = "insert"))
        val gap = decisionOfKind(r, DecisionKind.CELL)
        assertEquals(NarrationId.LIST_ASK_WHERE_BELONGS, gap.decision.prompt.id)
        assertEquals(ListAction.PickLink(1), gap.decision.correct)
        r.apply(gap.decision.correct)

        // The node exists and is not in the chain: this is the moment that stops
        // insertion looking like a value dropped into an array slot.
        assertEquals(ListPhase.LINKING, r.current.state.phase)
        assertNotNull(r.current.state.detached)
        assertEquals(listOf(10, 20, 30), r.current.state.values)

        val link = toDecision(r)
        assertEquals(NarrationId.LIST_ASK_POINTS_TO, link.decision.prompt.id)
        r.apply(link.decision.correct)
        assertEquals(listOf(10, 15, 20, 30), r.current.state.values)
    }

    @Test
    fun `deletion asks which link changes and then where it points`() {
        val r = runner(Dataset(listOf(10, 20, 30), target = 20, label = "delete"))
        val gap = decisionOfKind(r, DecisionKind.CELL)
        assertEquals(NarrationId.LIST_ASK_WHICH_LINK, gap.decision.prompt.id)
        // The arrow *into* 20 is the one that must be redirected.
        assertEquals(ListAction.PickLink(1), gap.decision.correct)
        r.apply(gap.decision.correct)

        // Nothing has been removed yet — the link is merely open.
        assertEquals(listOf(10, 20, 30), r.current.state.values)
        assertEquals(ListPhase.RECONNECTING, r.current.state.phase)

        val repoint = toDecision(r)
        r.apply(repoint.decision.correct)
        assertEquals(listOf(10, 30), r.current.state.values)
    }

    @Test
    fun `the learner points at links, not at nodes`() {
        // A list of n nodes has n+1 gaps, and every one of them is offered.
        val r = runner(Dataset(listOf(10, 20, 30), target = 15, label = "insert"))
        val gap = decisionOfKind(r, DecisionKind.CELL)
        assertEquals(4, gap.decision.options.size)
        assertEquals(listOf(0, 1, 2, 3), gap.decision.options.map { it.slot })
    }

    @Test
    fun `where to point is always the same shape of question`() {
        // Two nodes and NULL, wherever in the list the operation happens — the
        // number of options must never leak which answer is correct.
        for (target in listOf(20, 40)) {
            val r = runner(Dataset(listOf(10, 20, 30, 40), target = target, label = "delete"))
            val gap = decisionOfKind(r, DecisionKind.CELL)
            r.apply(gap.decision.correct)
            assertEquals(3, toDecision(r).decision.options.size)
        }
    }

    // ── A wrong decision is a learning event, not a state transition ──────────

    @Test
    fun `a wrong gap never touches the list`() {
        val r = runner(Dataset(listOf(10, 20, 30), target = 15, label = "insert"))
        val gap = decisionOfKind(r, DecisionKind.CELL)
        val before = r.current.state

        gap.decision.options
            .map { it.action }
            .filter { it != gap.decision.correct }
            .forEachIndexed { attempt, action ->
                assertTrue(
                    "a wrong gap was accepted",
                    DecisionValidation.validate(gap.decision, action, attempt) is Validation.Retry,
                )
            }
        assertEquals("the list moved on a miss", before, r.current.state)
    }

    @Test
    fun `a wrong pointer never leaves a broken link`() {
        val r = runner(Dataset(listOf(10, 20, 30), target = 20, label = "delete"))
        r.apply(decisionOfKind(r, DecisionKind.CELL).decision.correct)
        val repoint = toDecision(r)
        val before = r.current.state

        repoint.decision.options
            .map { it.action }
            .filter { it != repoint.decision.correct }
            .forEach { action ->
                assertTrue(
                    "a wrong pointer was accepted",
                    DecisionValidation.validate(repoint.decision, action, 0) is Validation.Retry,
                )
                // Even applied directly, the transition refuses: the list is never
                // left in a state the learner could not have reached correctly.
                val t = LinkedListAlgorithm().apply(before, action)
                assertEquals(before, t.next)
                assertFalse(t.correct)
            }
        assertEquals(before, r.current.state)
    }

    @Test
    fun `pointing back at the deleted node is called out by name`() {
        val r = runner(Dataset(listOf(10, 20, 30), target = 20, label = "delete"))
        r.apply(decisionOfKind(r, DecisionKind.CELL).decision.correct)
        val repoint = toDecision(r)
        // The classic broken delete: reconnect to the node you meant to remove.
        val stillLinked = repoint.decision.whyWrong.values.map { it.id }
        assertTrue(stillLinked.contains(NarrationId.LIST_WHY_STILL_LINKED))
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test
    fun `searching an empty list is explained, not fatal`() {
        val s = state(emptyList(), listOf(ListTask.Find(5)))
        val algorithm = LinkedListAlgorithm()
        assertTrue(algorithm.probe(s) is Probe.Mechanical)
        val t = algorithm.apply(s, ListAction.Skip)
        assertEquals(NarrationId.LIST_EMPTY, t.narration?.id)
        assertTrue(t.correct)
    }

    @Test
    fun `deleting a value that is not there is explained, not fatal`() {
        val s = state(listOf(10, 20), listOf(ListTask.Delete(99)))
        val algorithm = LinkedListAlgorithm()
        assertTrue(algorithm.probe(s) is Probe.Mechanical)
        val t = algorithm.apply(s, ListAction.Skip)
        assertEquals(NarrationId.LIST_NOT_PRESENT, t.narration?.id)
        assertEquals(listOf(10, 20), t.next.values)
    }

    @Test
    fun `a search that runs off the end reaches NULL and says so`() {
        val r = runner(Dataset(listOf(10, 20, 30), target = 99, label = "find"))
        var guard = 0
        while (guard++ < 32) {
            when (val probe = r.probe()) {
                is Probe.Mechanical -> r.apply(probe.action)
                is Probe.Decide -> r.apply(probe.decision.correct)
                is Probe.Terminal -> break
            }
        }
        assertEquals(listOf(10, 20, 30), r.current.state.values)
    }

    @Test
    fun `inserting before the head is a real case with a real answer`() {
        val r = runner(Dataset(listOf(10, 20, 30), target = 5, label = "insert"))
        val gap = decisionOfKind(r, DecisionKind.CELL)
        // Gap zero is HEAD's own arrow. There is no previous node to rewire, which
        // is exactly why every implementation gets this one wrong first.
        assertEquals(ListAction.PickLink(0), gap.decision.correct)
        assertEquals(listOf(5, 10, 20, 30), playPerfectly(r.current.state.let {
            Dataset(listOf(10, 20, 30), target = 5, label = "insert")
        }).current.state.values)
    }

    @Test
    fun `inserting after the last node points at NULL`() {
        val r = runner(Dataset(listOf(10, 20, 30), target = 99, label = "insert"))
        val gap = decisionOfKind(r, DecisionKind.CELL)
        assertEquals(ListAction.PickLink(3), gap.decision.correct)
        r.apply(gap.decision.correct)
        assertEquals(ListAction.PointAt(null), toDecision(r).decision.correct)
    }

    @Test
    fun `deleting the head moves HEAD, not a node`() {
        val r = runner(Dataset(listOf(10, 20, 30), target = 10, label = "delete"))
        val gap = decisionOfKind(r, DecisionKind.CELL)
        assertEquals(ListAction.PickLink(0), gap.decision.correct)
        r.apply(gap.decision.correct)
        val repoint = toDecision(r)
        assertEquals(NarrationId.LIST_ASK_HEAD_POINTS, repoint.decision.prompt.id)
        r.apply(repoint.decision.correct)
        assertEquals(listOf(20, 30), r.current.state.values)
    }

    @Test
    fun `deleting the last node makes the previous link NULL`() {
        val r = runner(Dataset(listOf(10, 20, 30), target = 30, label = "delete"))
        r.apply(decisionOfKind(r, DecisionKind.CELL).decision.correct)
        val repoint = toDecision(r)
        assertEquals(ListAction.PointAt(null), repoint.decision.correct)
        r.apply(repoint.decision.correct)
        assertEquals(listOf(10, 20), r.current.state.values)
    }

    @Test
    fun `a one-node list can still be emptied`() {
        val r = runner(Dataset(listOf(42), target = 42, label = "delete"))
        var guard = 0
        while (guard++ < 32) {
            when (val probe = r.probe()) {
                is Probe.Mechanical -> r.apply(probe.action)
                is Probe.Decide -> r.apply(probe.decision.correct)
                is Probe.Terminal -> break
            }
        }
        assertTrue(r.current.state.empty)
    }

    // ── Presentation ──────────────────────────────────────────────────────────

    @Test
    fun `a linked list is drawn as a chain, not as a row`() {
        val scene = LinkedListProjector()
            .project(state(listOf(10, 20, 30), listOf(ListTask.Find(30))), emptyList())
        assertEquals(SceneLayout.CHAIN, scene.layout)
        // One arrow per gap: HEAD's, two between nodes, and the one to NULL.
        assertEquals(4, scene.links.size)
        assertEquals("HEAD", scene.endCaps?.leading)
        assertEquals("NULL", scene.endCaps?.trailing)
    }

    @Test
    fun `an open link is drawn as open`() {
        val mid = state(listOf(10, 20, 30), listOf(ListTask.Delete(20)))
            .copy(phase = ListPhase.RECONNECTING, openLink = 1)
        val scene = LinkedListProjector().project(mid, emptyList())
        assertEquals(LinkState.OPEN, scene.links.first { it.slot == 1 }.state)
        // The arrow out of the doomed node is going too — both ends are loose.
        assertEquals(LinkState.OPEN, scene.links.first { it.slot == 2 }.state)
    }

    @Test
    fun `a node waiting to be linked is drawn outside the chain`() {
        val r = runner(Dataset(listOf(10, 20, 30), target = 15, label = "insert"))
        r.apply(decisionOfKind(r, DecisionKind.CELL).decision.correct)
        val scene = LinkedListProjector().project(r.current.state, emptyList())
        assertEquals(15, scene.detached?.value)
        assertEquals(1, scene.detached?.atLink)
        // It is not in the chain yet, and the picture must not pretend otherwise.
        assertEquals(listOf(10, 20, 30), scene.cells.map { it.value })
    }

    @Test
    fun `an empty list still draws HEAD and NULL`() {
        val scene = LinkedListProjector().project(state(emptyList(), emptyList()), emptyList())
        assertTrue(scene.cells.isEmpty())
        assertEquals(1, scene.links.size)
        assertNotNull(scene.endCaps)
    }

    // ── The walkthrough ───────────────────────────────────────────────────────

    @Test
    fun `the walkthrough teaches the node, the head, the walk and both operations`() {
        val script = WatchScriptBuilder(
            LinkedListAlgorithm(),
            LinkedListProjector(),
            LinkedListWatchNarrator(),
        ).build(LinkedListDatasets.watch)

        val kinds = script.steps.map { it.kind }
        assertEquals(WatchStepKind.SETUP, kinds.first())
        assertEquals(WatchStepKind.SUMMARY, kinds.last())
        assertTrue("no search", kinds.contains(WatchStepKind.FOUND))
        assertTrue("no insertion", kinds.contains(WatchStepKind.ADD))
        assertTrue("no deletion", kinds.contains(WatchStepKind.REMOVE))
        assertEquals("checkpoints", 1, kinds.count { it == WatchStepKind.PREDICT })
        // It is the richest concept in the app, but it still has to fit one sitting.
        assertTrue("walkthrough is ${script.size} steps", script.size <= 26)
    }

    @Test
    fun `the insertion is shown in two beats, not one`() {
        val script = WatchScriptBuilder(
            LinkedListAlgorithm(),
            LinkedListProjector(),
            LinkedListWatchNarrator(),
        ).build(LinkedListDatasets.watch)

        // There must be a step where the new node exists and is *not* in the chain.
        assertTrue(
            "the node never appears detached",
            script.steps.any { it.scene.sequence.detached != null },
        )
    }

    @Test
    fun `every step of the walkthrough changes something`() {
        val script = WatchScriptBuilder(
            LinkedListAlgorithm(),
            LinkedListProjector(),
            LinkedListWatchNarrator(),
        ).build(LinkedListDatasets.watch)

        script.steps.zipWithNext { a, b ->
            assertTrue(
                "step ${b.index} says nothing new",
                a.scene != b.scene || a.headline != b.headline,
            )
        }
    }

    @Test
    fun `the checkpoint asks what the previous node should point to`() {
        val script = WatchScriptBuilder(
            LinkedListAlgorithm(),
            LinkedListProjector(),
            LinkedListWatchNarrator(),
        ).build(LinkedListDatasets.watch)

        val prediction = script.steps.mapNotNull { it.prediction }.single()
        assertEquals(3, prediction.options.size)
        // "If we delete 20, what should 10 point to?" — 30, the node after it.
        assertEquals(2, prediction.correctIndex)
    }

    // ── Catalog and challenges ────────────────────────────────────────────────

    @Test
    fun `the linked list is in the catalog`() {
        assertEquals(AlgorithmId.LINKED_LIST, AlgorithmCatalog.byId(AlgorithmId.LINKED_LIST).id)
    }

    @Test
    fun `the first challenge is a walk, and all three kinds come round`() {
        assertEquals(ChallengeType.TRAVERSE, ChallengeGenerator.listForRound(1, 1).type)
        val types = (1..9).map { ChallengeGenerator.listForRound(it, it.toLong()).type }.toSet()
        assertTrue("insertion never appears", types.contains(ChallengeType.LINK_INSERT))
        assertTrue("deletion never appears", types.contains(ChallengeType.LINK_DELETE))
    }

    @Test
    fun `every generated challenge is solvable and asks something real`() {
        for (round in 1..10) {
            val challenge = ChallengeGenerator.listForRound(round, round.toLong())
            val r = playPerfectly(challenge.dataset)
            assertTrue("round $round did not finish", r.probe() is Probe.Terminal)

            val values = challenge.dataset.values
            assertEquals("round $round is unordered", values.sorted(), values)
            val target = requireNotNull(challenge.dataset.target)
            when (challenge.type) {
                // An insertion the learner could satisfy by tapping either end is
                // not a question about order.
                ChallengeType.LINK_INSERT -> {
                    assertFalse("round $round inserts a duplicate", target in values)
                    assertTrue("round $round inserts at an end", target > values.first())
                    assertTrue("round $round inserts at an end", target < values.last())
                }

                else -> assertTrue("round $round targets a missing value", target in values)
            }
        }
    }

    @Test
    fun `a challenge is reproducible from its seed`() {
        assertEquals(
            ChallengeGenerator.listForRound(4, 21).dataset,
            ChallengeGenerator.listForRound(4, 21).dataset,
        )
    }

    @Test
    fun `challenge data never repeats the teaching data`() {
        val taught = (LinkedListDatasets.watch.values + LinkedListDatasets.tryIt.values).toSet()
        for (round in 1..8) {
            val values = ChallengeGenerator.listForRound(round, round.toLong()).dataset.values
            assertTrue(
                "round $round reused a taught list",
                values.toSet() != taught && !taught.containsAll(values),
            )
        }
    }

    // ── The scripts ───────────────────────────────────────────────────────────

    @Test
    fun `the try lesson includes the case that has no previous node`() {
        val tasks = ListScripts.forDataset(LinkedListDatasets.tryIt)
        val inserts = tasks.filterIsInstance<ListTask.Insert>()
        assertTrue(
            "nothing is inserted before the head",
            inserts.any { it.value < LinkedListDatasets.tryIt.values.min() },
        )
    }

    @Test
    fun `a changing challenge is followed by a walk that proves it worked`() {
        for (label in listOf("insert", "delete")) {
            val tasks = ListScripts.forDataset(
                Dataset(listOf(10, 20, 30), target = 20, label = label),
            )
            assertEquals(2, tasks.size)
            assertTrue("$label is not verified", tasks.last() is ListTask.Find)
        }
    }

    @Test
    fun `an empty dataset produces no lesson rather than a crash`() {
        val s = LinkedListAlgorithm().initial(Dataset(emptyList(), label = "watch"))
        assertTrue(s.done)
        assertTrue(LinkedListAlgorithm().probe(s) is Probe.Terminal)
        assertNull(s.task)
    }
}
