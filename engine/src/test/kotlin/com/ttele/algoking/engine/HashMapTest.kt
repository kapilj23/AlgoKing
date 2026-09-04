package com.ttele.algoking.engine

import com.ttele.algoking.engine.algorithms.hashing.HashAction
import com.ttele.algoking.engine.algorithms.hashing.HashMapAlgorithm
import com.ttele.algoking.engine.algorithms.hashing.HashMapProjector
import com.ttele.algoking.engine.algorithms.hashing.HashMapState
import com.ttele.algoking.engine.algorithms.hashing.HashMapWatchNarrator
import com.ttele.algoking.engine.algorithms.hashing.HashPhase
import com.ttele.algoking.engine.algorithms.hashing.HashScripts
import com.ttele.algoking.engine.algorithms.hashing.HashTask
import com.ttele.algoking.engine.algorithms.hashing.MapEntry
import com.ttele.algoking.engine.algorithms.hashing.Resolution
import com.ttele.algoking.engine.catalog.AlgorithmCatalog
import com.ttele.algoking.engine.challenge.ChallengeGenerator
import com.ttele.algoking.engine.challenge.ChallengeType
import com.ttele.algoking.engine.core.AlgorithmId
import com.ttele.algoking.engine.core.AlgorithmRunner
import com.ttele.algoking.engine.core.Dataset
import com.ttele.algoking.engine.core.Probe
import com.ttele.algoking.engine.dataset.HashMapDatasets
import com.ttele.algoking.engine.decision.DecisionKind
import com.ttele.algoking.engine.decision.DecisionValidation
import com.ttele.algoking.engine.decision.Validation
import com.ttele.algoking.engine.event.Outcome
import com.ttele.algoking.engine.narration.NarrationId
import com.ttele.algoking.engine.scene.BucketState
import com.ttele.algoking.engine.scene.EntryState
import com.ttele.algoking.engine.scene.FlowStage
import com.ttele.algoking.engine.walkthrough.WatchScriptBuilder
import com.ttele.algoking.engine.walkthrough.WatchStepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hash Map.
 *
 * The claim these tests defend is that the lesson is about the **hash**: every
 * operation starts by turning a key into a bucket, a collision keeps both entries,
 * the same key updates rather than duplicating, and a wrong answer changes nothing.
 */
class HashMapTest {

    private fun runner(dataset: Dataset) = AlgorithmRunner(HashMapAlgorithm(), dataset)

    private fun playPerfectly(dataset: Dataset): AlgorithmRunner<HashMapState, HashAction> {
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
        r: AlgorithmRunner<HashMapState, HashAction>,
    ): Probe.Decide<HashAction> {
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

    private fun state(
        buckets: List<List<MapEntry>>,
        script: List<HashTask>,
        phase: HashPhase = HashPhase.HASHING,
        bucket: Int? = null,
    ) = HashMapState(buckets = buckets, script = script, at = 0, phase = phase, bucket = bucket)

    private fun empty(count: Int = 5) = List(count) { emptyList<MapEntry>() }

    // ── The hash function ─────────────────────────────────────────────────────

    @Test
    fun `the key decides the bucket, and nothing else does`() {
        val s = state(empty(), listOf(HashTask.Get(12)))
        assertEquals(2, s.hash(12))
        assertEquals(2, s.hash(7))
        assertEquals(0, s.hash(20))
        // Same key, same bucket, every time. That is the entire guarantee.
        assertEquals(s.hash(37), s.hash(37))
    }

    @Test
    fun `every operation begins by hashing the key`() {
        // Not "most" — every one. It is the habit the structure runs on.
        for (label in listOf("watch", "try", "operations")) {
            val r = runner(Dataset(listOf(12, 7, 18), target = 12, label = label))
            val first = toDecision(r).decision
            assertEquals("$label did not hash first", DecisionKind.CELL, first.kind)
            assertTrue(
                "$label asked something other than the bucket",
                first.prompt.id in setOf(
                    NarrationId.HASH_ASK_BUCKET_PUT,
                    NarrationId.HASH_ASK_BUCKET_GET,
                    NarrationId.HASH_ASK_BUCKET_REMOVE,
                ),
            )
        }
    }

    @Test
    fun `the bucket is chosen by tapping the table, not by picking from a list`() {
        val r = runner(HashMapDatasets.watch)
        val decision = toDecision(r).decision
        assertEquals(DecisionKind.CELL, decision.kind)
        // One option per bucket, each addressing its own row.
        assertEquals(HashScripts.BUCKETS, decision.options.size)
        assertEquals((0 until HashScripts.BUCKETS).toList(), decision.options.map { it.slot })
        assertEquals(HashAction.Hash(2), decision.correct)
    }

    // ── Collisions ────────────────────────────────────────────────────────────

    @Test
    fun `two different keys share a bucket and both survive`() {
        // 12 % 5 and 7 % 5 are both 2. Nothing may be lost when they meet.
        val r = runner(Dataset(listOf(12, 7), target = 7, label = "operations"))
        var guard = 0
        while (guard++ < 128 && r.current.state.entryCount < 2) {
            when (val probe = r.probe()) {
                is Probe.Mechanical -> r.apply(probe.action)
                is Probe.Decide -> r.apply(probe.decision.correct)
                is Probe.Terminal -> break
            }
        }
        assertEquals(listOf(12, 7), r.current.state.chain(2).map { it.key })
        assertEquals(1, r.current.state.collisions)
    }

    @Test
    fun `a collision asks what happens, and keeping both is the answer`() {
        val occupied = empty().toMutableList().also { it[2] = listOf(MapEntry(12, "Alice")) }
        val s = state(occupied, listOf(HashTask.Put(7, "Bob")), HashPhase.RESOLVING, bucket = 2)
        val decision = (HashMapAlgorithm().probe(s) as Probe.Decide).decision

        assertEquals(NarrationId.HASH_ASK_OCCUPIED, decision.prompt.id)
        assertEquals(HashAction.Resolve(Resolution.CHAIN), decision.correct)
        // Refusing the entry is offered, because it is what learners expect.
        assertTrue(
            decision.options.map { it.action }.contains(HashAction.Resolve(Resolution.REJECT)),
        )
    }

    @Test
    fun `refusing an entry is never right, and is explained by name`() {
        val occupied = empty().toMutableList().also { it[2] = listOf(MapEntry(12, "Alice")) }
        val s = state(occupied, listOf(HashTask.Put(7, "Bob")), HashPhase.RESOLVING, bucket = 2)
        val decision = (HashMapAlgorithm().probe(s) as Probe.Decide).decision
        assertEquals(
            NarrationId.HASH_WHY_NOT_REJECT,
            decision.whyWrong[HashAction.Resolve(Resolution.REJECT)]?.id,
        )
    }

    @Test
    fun `the same key updates rather than duplicating`() {
        val occupied = empty().toMutableList().also { it[2] = listOf(MapEntry(12, "Alice")) }
        val s = state(occupied, listOf(HashTask.Put(12, "Zara")), HashPhase.RESOLVING, bucket = 2)
        val algorithm = HashMapAlgorithm()
        val decision = (algorithm.probe(s) as Probe.Decide).decision
        assertEquals(HashAction.Resolve(Resolution.REPLACE), decision.correct)

        val resolved = algorithm.apply(s, HashAction.Resolve(Resolution.REPLACE)).next
        val stored = algorithm.apply(resolved, HashAction.Settle).next
        assertEquals("the key was duplicated", 1, stored.chain(2).size)
        assertEquals("Zara", stored.chain(2).single().label)
    }

    @Test
    fun `the try lesson makes the learner meet a duplicate key`() {
        val tasks = HashScripts.forDataset(HashMapDatasets.tryIt)
        val puts = tasks.filterIsInstance<HashTask.Put>()
        assertTrue(
            "no key is ever put twice",
            puts.map { it.key }.size != puts.map { it.key }.distinct().size,
        )
    }

    @Test
    fun `the watch lesson collides on purpose`() {
        val keys = HashMapDatasets.watch.values
        val buckets = keys.map { it.mod(HashScripts.BUCKETS) }
        assertEquals("the watch keys do not collide", 1, buckets.distinct().size)
    }

    // ── Landing in the right bucket is not the same as finding the entry ──────

    @Test
    fun `a shared bucket still has to be scanned`() {
        val shared = empty().toMutableList().also {
            it[2] = listOf(MapEntry(12, "Alice"), MapEntry(7, "Bob"))
        }
        val s = state(shared, listOf(HashTask.Get(7)), HashPhase.SCANNING, bucket = 2)
        val decision = (HashMapAlgorithm().probe(s) as Probe.Decide).decision
        // Both entries, plus "not here": the hash got you to the door, not the answer.
        assertEquals(3, decision.options.size)
        assertEquals(HashAction.Inspect(7), decision.correct)
    }

    @Test
    fun `a missing key in an occupied bucket is a real answer`() {
        val shared = empty().toMutableList().also {
            it[2] = listOf(MapEntry(12, "Alice"), MapEntry(7, "Bob"))
        }
        val s = state(shared, listOf(HashTask.Get(17)), HashPhase.SCANNING, bucket = 2)
        val decision = (HashMapAlgorithm().probe(s) as Probe.Decide).decision
        assertEquals(HashAction.Inspect(null), decision.correct)
        assertEquals(NarrationId.HASH_NOT_FOUND, decision.correctFeedback.id)
    }

    @Test
    fun `an empty bucket needs no scan at all`() {
        // There is nothing to compare against, so offering a one-button "decision"
        // would be a tap rather than a judgement.
        val s = state(empty(), listOf(HashTask.Get(12)), HashPhase.SCANNING, bucket = 2)
        assertTrue(HashMapAlgorithm().probe(s) is Probe.Mechanical)
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test
    fun `looking up a key that was never stored changes nothing`() {
        val algorithm = HashMapAlgorithm()
        val s = state(empty(), listOf(HashTask.Get(12)), HashPhase.SCANNING, bucket = 2)
        val t = algorithm.apply(s, HashAction.Inspect(null))
        assertEquals(empty(), t.next.buckets)
        assertTrue(t.next.missing)
        assertEquals(NarrationId.HASH_NOT_FOUND, t.narration?.id)
    }

    @Test
    fun `removing a key that is not there is narrated, not fatal`() {
        val algorithm = HashMapAlgorithm()
        val s = state(empty(), listOf(HashTask.Remove(12)), HashPhase.SETTLED, bucket = 2)
        val t = algorithm.apply(s, HashAction.Settle)
        assertEquals(empty(), t.next.buckets)
        assertEquals(NarrationId.HASH_NOTHING_TO_REMOVE, t.narration?.id)
        assertTrue(t.correct)
    }

    @Test
    fun `an empty dataset produces no lesson rather than a crash`() {
        val s = HashMapAlgorithm().initial(Dataset(emptyList(), label = "watch"))
        assertTrue(s.done)
        assertTrue(HashMapAlgorithm().probe(s) is Probe.Terminal)
        assertNull(s.task)
    }

    @Test
    fun `both teaching lessons run to completion`() {
        for (dataset in listOf(HashMapDatasets.watch, HashMapDatasets.tryIt)) {
            val r = playPerfectly(dataset)
            assertEquals(
                Outcome.Completed(correct = true),
                (r.probe() as Probe.Terminal).outcome,
            )
        }
    }

    // ── A wrong decision is a learning event, not a state transition ──────────

    @Test
    fun `a wrong bucket never touches the map`() {
        val r = runner(HashMapDatasets.tryIt)
        val decision = toDecision(r).decision
        val before = r.current.state

        decision.options
            .map { it.action }
            .filter { it != decision.correct }
            .forEachIndexed { attempt, action ->
                assertTrue(
                    "a wrong bucket was accepted",
                    DecisionValidation.validate(decision, action, attempt) is Validation.Retry,
                )
                val t = HashMapAlgorithm().apply(before, action)
                assertEquals(before, t.next)
                assertFalse(t.correct)
            }
        assertEquals("the map moved on a miss", before, r.current.state)
    }

    @Test
    fun `a wrong collision answer never loses an entry`() {
        val occupied = empty().toMutableList().also { it[2] = listOf(MapEntry(12, "Alice")) }
        val s = state(occupied, listOf(HashTask.Put(7, "Bob")), HashPhase.RESOLVING, bucket = 2)
        val algorithm = HashMapAlgorithm()

        for (wrong in listOf(Resolution.REPLACE, Resolution.REJECT)) {
            val t = algorithm.apply(s, HashAction.Resolve(wrong))
            assertEquals("resolving wrongly changed the map", s, t.next)
            assertFalse(t.correct)
        }
    }

    @Test
    fun `a wrong entry is refused without removing anything`() {
        val shared = empty().toMutableList().also {
            it[2] = listOf(MapEntry(12, "Alice"), MapEntry(7, "Bob"))
        }
        val s = state(shared, listOf(HashTask.Remove(7)), HashPhase.SCANNING, bucket = 2)
        val t = HashMapAlgorithm().apply(s, HashAction.Inspect(12))
        assertEquals(s, t.next)
        assertFalse(t.correct)
    }

    @Test
    fun `the arithmetic is only spelled out at the end of the ladder`() {
        val r = runner(HashMapDatasets.watch)
        val decision = toDecision(r).decision
        // Level one points, level two asks, level three does the sum. Challenge
        // never climbs past the first rung.
        assertEquals(NarrationId.HASH_RETRY_LOOK, decision.guidance[0].id)
        assertEquals(NarrationId.HASH_RETRY_ASK, decision.guidance[1].id)
        assertEquals(NarrationId.HASH_RETRY_EXPLAIN, decision.guidance[2].id)
        assertEquals(NarrationId.HASH_RETRY_LOOK, decision.minimalFeedback.id)
    }

    // ── Presentation ──────────────────────────────────────────────────────────

    @Test
    fun `a hash map is drawn as a table, never as a sequence`() {
        val scene = HashMapProjector()
            .project(state(empty(), listOf(HashTask.Get(12))), emptyList())
        assertEquals(HashScripts.BUCKETS, scene.buckets.size)
        assertEquals((0..4).toList(), scene.buckets.map { it.index })
    }

    @Test
    fun `the flow hides the bucket until it has been worked out`() {
        val asking = HashMapProjector()
            .project(state(empty(), listOf(HashTask.Get(12))), emptyList())
        assertEquals(FlowStage.ASKING, asking.flow?.stage)
        // An answer already on screen is not a question.
        assertNull("the answer was on screen", asking.flow?.bucket)

        val answered = HashMapProjector().project(
            state(empty(), listOf(HashTask.Get(12)), HashPhase.SCANNING, bucket = 2),
            emptyList(),
        )
        assertEquals(2, answered.flow?.bucket)
    }

    @Test
    fun `the flow always shows the arithmetic`() {
        val scene = HashMapProjector()
            .project(state(empty(), listOf(HashTask.Get(12))), emptyList())
        assertEquals(12, scene.flow?.key)
        assertEquals(5, scene.flow?.modulus)
    }

    @Test
    fun `a shared bucket says so even when nothing is happening in it`() {
        val shared = empty().toMutableList().also {
            it[2] = listOf(MapEntry(12, "Alice"), MapEntry(7, "Bob"))
        }
        val scene = HashMapProjector()
            .project(state(shared, emptyList()).copy(done = true), emptyList())
        assertEquals(BucketState.COLLIDED, scene.buckets[2].state)
        assertEquals(BucketState.IDLE, scene.buckets[0].state)
    }

    @Test
    fun `the bucket a key landed in is highlighted`() {
        val scene = HashMapProjector().project(
            state(empty(), listOf(HashTask.Get(12)), HashPhase.SCANNING, bucket = 2),
            emptyList(),
        )
        assertEquals(BucketState.TARGET, scene.buckets[2].state)
    }

    @Test
    fun `a matched entry is marked as found`() {
        val shared = empty().toMutableList().also { it[2] = listOf(MapEntry(12, "Alice")) }
        val hit = state(shared, listOf(HashTask.Get(12)), HashPhase.SETTLED, bucket = 2)
            .copy(hit = MapEntry(12, "Alice"))
        val scene = HashMapProjector().project(hit, emptyList())
        assertEquals(EntryState.MATCHED, scene.buckets[2].entries.single().state)
    }

    // ── The walkthrough ───────────────────────────────────────────────────────

    @Test
    fun `the walkthrough names the parts, collides, looks up and removes`() {
        val script = WatchScriptBuilder(
            HashMapAlgorithm(),
            HashMapProjector(),
            HashMapWatchNarrator(),
        ).build(HashMapDatasets.watch)

        val kinds = script.steps.map { it.kind }
        val headlines = script.steps.map { it.headline.id }
        assertEquals(WatchStepKind.SETUP, kinds.first())
        assertEquals(WatchStepKind.SUMMARY, kinds.last())
        assertTrue("the key and value are never named", headlines.contains(NarrationId.HASH_WATCH_KEY_VALUE))
        assertTrue("the hash function is never shown", headlines.contains(NarrationId.HASH_WATCH_FUNCTION))
        assertTrue("nothing ever collides", headlines.contains(NarrationId.HASH_WATCH_WAIT))
        assertTrue("chaining is never explained", headlines.contains(NarrationId.HASH_WATCH_CHAINING))
        assertTrue("nothing is looked up", kinds.contains(WatchStepKind.FOUND))
        assertTrue("nothing is removed", kinds.contains(WatchStepKind.REMOVE))
        assertEquals("checkpoints", 1, kinds.count { it == WatchStepKind.PREDICT })
        assertTrue("walkthrough is ${script.size} steps", script.size <= 22)
    }

    @Test
    fun `the walkthrough explains why any of this is faster`() {
        val script = WatchScriptBuilder(
            HashMapAlgorithm(),
            HashMapProjector(),
            HashMapWatchNarrator(),
        ).build(HashMapDatasets.watch)
        val headlines = script.steps.map { it.headline.id }
        assertTrue(headlines.contains(NarrationId.HASH_WATCH_WHY_FAST))
        assertTrue(headlines.contains(NarrationId.HASH_WATCH_VS_ARRAY))
        assertTrue(headlines.contains(NarrationId.HASH_WATCH_AVERAGE))
    }

    @Test
    fun `the checkpoint uses a key the lesson has not used`() {
        val script = WatchScriptBuilder(
            HashMapAlgorithm(),
            HashMapProjector(),
            HashMapWatchNarrator(),
        ).build(HashMapDatasets.watch)

        val prediction = script.steps.mapNotNull { it.prediction }.single()
        assertEquals(HashScripts.BUCKETS, prediction.options.size)
        val key = prediction.prompt.args.first() as Int
        assertFalse("the checkpoint reuses a taught key", key in HashMapDatasets.watch.values)
        assertEquals(key.mod(HashScripts.BUCKETS), prediction.correctIndex)
    }

    @Test
    fun `every step of the walkthrough changes something`() {
        val script = WatchScriptBuilder(
            HashMapAlgorithm(),
            HashMapProjector(),
            HashMapWatchNarrator(),
        ).build(HashMapDatasets.watch)

        script.steps.zipWithNext { a, b ->
            assertTrue(
                "step ${b.index} says nothing new",
                a.scene != b.scene || a.headline != b.headline,
            )
        }
    }

    // ── Catalog and challenges ────────────────────────────────────────────────

    @Test
    fun `the hash map is in the catalog`() {
        assertEquals(AlgorithmId.HASH_MAP, AlgorithmCatalog.byId(AlgorithmId.HASH_MAP).id)
    }

    @Test
    fun `every generated challenge collides`() {
        // A hash map challenge where every key finds an empty bucket would quietly
        // drop the one idea the learner most needs to practise.
        for (round in 1..10) {
            val challenge = ChallengeGenerator.hashForRound(round, round.toLong())
            val buckets = challenge.dataset.values.map { it.mod(HashScripts.BUCKETS) }
            assertTrue(
                "round $round never collides: ${challenge.dataset.values}",
                buckets.size != buckets.distinct().size,
            )
        }
    }

    @Test
    fun `every generated challenge is solvable`() {
        for (round in 1..10) {
            val challenge = ChallengeGenerator.hashForRound(round, round.toLong())
            val r = playPerfectly(challenge.dataset)
            assertTrue("round $round did not finish", r.probe() is Probe.Terminal)
            assertTrue(
                "round $round is not a hash challenge",
                challenge.type in setOf(
                    ChallengeType.HASH_OPERATIONS,
                    ChallengeType.HASH_COLLISION,
                ),
            )
        }
    }

    @Test
    fun `the collision challenge starts with a bucket already shared`() {
        val challenge = (1..12)
            .map { ChallengeGenerator.hashForRound(it, it.toLong()) }
            .first { it.type == ChallengeType.HASH_COLLISION }

        val start = HashMapAlgorithm().initial(challenge.dataset)
        assertEquals("the map was not pre-loaded", 2, start.entryCount)
        assertEquals("the keys did not share a bucket", 1, start.collisions)
        // And the target is the *second* key, so the learner has to read past the
        // first one rather than stopping at the head of the chain.
        assertEquals(challenge.dataset.values[1], challenge.dataset.target)
    }

    @Test
    fun `a challenge is reproducible from its seed`() {
        assertEquals(
            ChallengeGenerator.hashForRound(4, 31).dataset,
            ChallengeGenerator.hashForRound(4, 31).dataset,
        )
    }

    @Test
    fun `challenge keys never repeat the teaching keys wholesale`() {
        val taught = (HashMapDatasets.watch.values + HashMapDatasets.tryIt.values).toSet()
        for (round in 1..10) {
            val values = ChallengeGenerator.hashForRound(round, round.toLong()).dataset.values
            assertFalse(
                "round $round reused the taught keys",
                taught.containsAll(values),
            )
        }
    }

    @Test
    fun `a challenge asks the learner to do the hashing every time`() {
        val challenge = ChallengeGenerator.hashForRound(3, 3)
        val r = runner(challenge.dataset)
        var hashes = 0
        var guard = 0
        while (guard++ < 256) {
            when (val probe = r.probe()) {
                is Probe.Mechanical -> r.apply(probe.action)
                is Probe.Decide -> {
                    if (probe.decision.kind == DecisionKind.CELL) hashes++
                    // Nothing in a hash map lesson is answered for the learner.
                    assertFalse(probe.decision.autoInTry)
                    r.apply(probe.decision.correct)
                }

                is Probe.Terminal -> break
            }
        }
        assertEquals("one hash per operation", challenge.dataset.values.size + 2, hashes)
    }

    @Test
    fun `values are names so a key and a value can never be confused`() {
        assertNotNull(HashScripts.labelFor(0))
        assertEquals(HashScripts.labelFor(0), HashScripts.labelFor(0))
        assertTrue(HashScripts.labelFor(0).none { it.isDigit() })
    }
}
