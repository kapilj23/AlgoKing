package com.ttele.algoking.engine.algorithms.hashing

import com.ttele.algoking.engine.core.Algorithm
import com.ttele.algoking.engine.core.AlgorithmId
import com.ttele.algoking.engine.core.Dataset
import com.ttele.algoking.engine.core.Probe
import com.ttele.algoking.engine.core.Transition
import com.ttele.algoking.engine.decision.ActionOption
import com.ttele.algoking.engine.decision.Decision
import com.ttele.algoking.engine.decision.DecisionKind
import com.ttele.algoking.engine.event.ExamineRole
import com.ttele.algoking.engine.event.Outcome
import com.ttele.algoking.engine.event.VizEvent
import com.ttele.algoking.engine.narration.NarrationId
import com.ttele.algoking.engine.narration.NarrationKey

/**
 * Hash Map — the key tells you where to look.
 *
 * Every operation in this lesson starts with the same decision: **which bucket does
 * this key hash to?**, answered by tapping the bucket itself. That repetition is
 * deliberate. It is the one habit that makes a hash map make sense, and a learner
 * who has done it eight times understands why lookup does not have to walk the data.
 *
 * The second decision only appears when there is something to learn from it — a
 * bucket that is already occupied, or a chain with more than one key in it. A hash
 * map that never collides teaches nothing about hash maps, so the lessons are
 * authored to collide.
 */
class HashMapAlgorithm : Algorithm<HashMapState, HashAction> {

    override val id = AlgorithmId.HASH_MAP

    override fun initial(dataset: Dataset): HashMapState {
        val script = HashScripts.forDataset(dataset)
        return HashMapState(
            buckets = HashScripts.preloadFor(dataset),
            script = script,
            at = 0,
            phase = if (script.isEmpty()) HashPhase.SETTLED else HashPhase.HASHING,
            done = script.isEmpty(),
        )
    }

    // ── Probing ───────────────────────────────────────────────────────────────

    override fun probe(state: HashMapState): Probe<HashAction> {
        if (state.done || state.at >= state.script.size) {
            return Probe.Terminal(Outcome.Completed(correct = true))
        }
        val task = requireNotNull(state.task)
        return when (state.phase) {
            HashPhase.HASHING -> Probe.Decide(hashDecision(state, task))
            HashPhase.RESOLVING -> Probe.Decide(resolveDecision(state, task))
            HashPhase.SCANNING ->
                // An empty bucket has nothing to compare against. The conclusion is
                // immediate, and offering it as a one-button "decision" would be a
                // tap, not a judgement.
                if (state.chain(state.bucket ?: 0).isEmpty()) {
                    Probe.Mechanical(HashAction.Inspect(null))
                } else {
                    Probe.Decide(scanDecision(state, task))
                }
            // Storing, removing and stepping to the next task are bookkeeping. The
            // learner has already made every judgement the operation contains.
            HashPhase.SETTLED -> Probe.Mechanical(HashAction.Settle)
        }
    }

    /**
     * "`key % 5` = which bucket?" — asked by tapping a bucket, not by picking a
     * number out of a list, so the answer lands on the thing it names.
     */
    private fun hashDecision(state: HashMapState, task: HashTask): Decision<HashAction> {
        val key = task.key
        val answer = state.hash(key)
        val args = listOf(key, state.modulus, answer)
        val verb = when (task) {
            is HashTask.Put -> NarrationId.HASH_ASK_BUCKET_PUT
            is HashTask.Get -> NarrationId.HASH_ASK_BUCKET_GET
            is HashTask.Remove -> NarrationId.HASH_ASK_BUCKET_REMOVE
        }

        return Decision(
            kind = DecisionKind.CELL,
            prompt = NarrationKey(verb, listOf(key, state.modulus)),
            options = state.buckets.indices.map { index ->
                ActionOption(
                    action = HashAction.Hash(index),
                    label = NarrationKey(NarrationId.HASH_OPTION_BUCKET, listOf(index)),
                    slot = index,
                )
            },
            correct = HashAction.Hash(answer),
            focus = listOf(answer),
            hint = NarrationKey(NarrationId.HASH_HINT_REMAINDER, listOf(key, state.modulus)),
            guidance = listOf(
                NarrationKey(NarrationId.HASH_RETRY_LOOK, listOf(key, state.modulus)),
                NarrationKey(NarrationId.HASH_RETRY_ASK, listOf(key, state.modulus)),
                NarrationKey(NarrationId.HASH_RETRY_EXPLAIN, args),
            ),
            // Challenge says the arithmetic and nothing else. It is a clue, not a
            // lesson — the learner still has to do the division.
            minimalFeedback = NarrationKey(NarrationId.HASH_RETRY_LOOK, listOf(key, state.modulus)),
            correctFeedback = NarrationKey(NarrationId.HASH_HASHED, args),
            hintLadder = listOf(
                NarrationKey(NarrationId.HASH_HINT_REMAINDER, listOf(key, state.modulus)),
                NarrationKey(NarrationId.HASH_HINT_KEY_DECIDES),
            ),
        )
    }

    /**
     * "The bucket is not empty. What happens?"
     *
     * The two right answers are different, and telling them apart is the whole
     * question: the *same* key updates, a *different* key joins the chain. Refusing
     * the entry is offered because it is what learners expect, and it is wrong.
     */
    private fun resolveDecision(state: HashMapState, task: HashTask): Decision<HashAction> {
        val put = task as HashTask.Put
        val bucket = requireNotNull(state.bucket)
        val existing = state.chain(bucket)
        val duplicate = existing.any { it.key == put.key }
        val neighbour = existing.first()
        val args = listOf(put.key, neighbour.key, bucket)

        return Decision(
            kind = DecisionKind.OPTIONS,
            prompt = NarrationKey(NarrationId.HASH_ASK_OCCUPIED, listOf(bucket, neighbour.key)),
            options = listOf(
                ActionOption(
                    HashAction.Resolve(Resolution.CHAIN),
                    NarrationKey(NarrationId.HASH_OPTION_CHAIN),
                ),
                ActionOption(
                    HashAction.Resolve(Resolution.REPLACE),
                    NarrationKey(NarrationId.HASH_OPTION_REPLACE),
                ),
                ActionOption(
                    HashAction.Resolve(Resolution.REJECT),
                    NarrationKey(NarrationId.HASH_OPTION_REJECT),
                ),
            ),
            correct = HashAction.Resolve(
                if (duplicate) Resolution.REPLACE else Resolution.CHAIN,
            ),
            focus = listOf(bucket),
            hint = NarrationKey(
                if (duplicate) NarrationId.HASH_HINT_SAME_KEY else NarrationId.HASH_HINT_DIFFERENT_KEYS,
                listOf(put.key, neighbour.key),
            ),
            guidance = listOf(
                NarrationKey(NarrationId.HASH_RETRY_COMPARE_KEYS, listOf(put.key, neighbour.key)),
                NarrationKey(
                    if (duplicate) {
                        NarrationId.HASH_RETRY_ASK_SAME
                    } else {
                        NarrationId.HASH_RETRY_ASK_DIFFERENT
                    },
                    listOf(put.key, neighbour.key),
                ),
                NarrationKey(
                    if (duplicate) {
                        NarrationId.HASH_RETRY_EXPLAIN_UPDATE
                    } else {
                        NarrationId.HASH_RETRY_EXPLAIN_CHAIN
                    },
                    args,
                ),
            ),
            minimalFeedback = NarrationKey(
                NarrationId.HASH_RETRY_COMPARE_KEYS,
                listOf(put.key, neighbour.key),
            ),
            whyWrong = mapOf<HashAction, NarrationKey>(
                HashAction.Resolve(Resolution.REJECT) to
                    NarrationKey(NarrationId.HASH_WHY_NOT_REJECT, listOf(put.key)),
                HashAction.Resolve(
                    if (duplicate) Resolution.CHAIN else Resolution.REPLACE,
                ) to NarrationKey(
                    if (duplicate) {
                        NarrationId.HASH_WHY_NOT_CHAIN
                    } else {
                        NarrationId.HASH_WHY_NOT_REPLACE
                    },
                    listOf(put.key, neighbour.key),
                ),
            ),
            correctFeedback = NarrationKey(
                if (duplicate) NarrationId.HASH_UPDATED else NarrationId.HASH_COLLISION,
                args,
            ),
            hintLadder = listOf(
                NarrationKey(
                    if (duplicate) {
                        NarrationId.HASH_HINT_SAME_KEY
                    } else {
                        NarrationId.HASH_HINT_DIFFERENT_KEYS
                    },
                    listOf(put.key, neighbour.key),
                ),
                NarrationKey(NarrationId.HASH_HINT_CHAIN_RULE),
            ),
        )
    }

    /**
     * "Which entry in this bucket is the one?"
     *
     * Trivial in a bucket of one, and the real test in a bucket of two: the whole
     * reason a hash map still has to compare keys after it has jumped.
     */
    private fun scanDecision(state: HashMapState, task: HashTask): Decision<HashAction> {
        val key = task.key
        val bucket = requireNotNull(state.bucket)
        val chain = state.chain(bucket)
        val match = chain.firstOrNull { it.key == key }
        val looking = task is HashTask.Remove

        return Decision(
            kind = DecisionKind.OPTIONS,
            prompt = NarrationKey(
                if (looking) NarrationId.HASH_ASK_WHICH_REMOVE else NarrationId.HASH_ASK_WHICH_ENTRY,
                listOf(key, bucket),
            ),
            options = chain.map<MapEntry, ActionOption<HashAction>> { entry ->
                ActionOption(
                    HashAction.Inspect(entry.key),
                    NarrationKey(NarrationId.HASH_OPTION_ENTRY, listOf(entry.key, entry.label)),
                )
            } + ActionOption(
                HashAction.Inspect(null),
                NarrationKey(NarrationId.HASH_OPTION_NOT_HERE),
            ),
            correct = HashAction.Inspect(match?.key),
            focus = listOf(bucket),
            hint = NarrationKey(NarrationId.HASH_HINT_COMPARE_IN_BUCKET, listOf(key)),
            guidance = listOf(
                NarrationKey(NarrationId.HASH_RETRY_SCAN_LOOK, listOf(key)),
                NarrationKey(NarrationId.HASH_RETRY_SCAN_ASK, listOf(key)),
                NarrationKey(
                    if (match == null) {
                        NarrationId.HASH_RETRY_SCAN_EXPLAIN_MISSING
                    } else {
                        NarrationId.HASH_RETRY_SCAN_EXPLAIN
                    },
                    listOfNotNull(key, match?.label),
                ),
            ),
            minimalFeedback = NarrationKey(NarrationId.HASH_RETRY_SCAN_LOOK, listOf(key)),
            correctFeedback = if (match == null) {
                NarrationKey(NarrationId.HASH_NOT_FOUND, listOf(key, bucket))
            } else {
                NarrationKey(NarrationId.HASH_FOUND, listOf(key, match.label))
            },
            hintLadder = listOf(
                NarrationKey(NarrationId.HASH_HINT_COMPARE_IN_BUCKET, listOf(key)),
                NarrationKey(NarrationId.HASH_HINT_BUCKET_NOT_ANSWER),
            ),
        )
    }

    // ── Transitions ───────────────────────────────────────────────────────────

    override fun apply(
        state: HashMapState,
        action: HashAction,
    ): Transition<HashMapState> = when (action) {
        is HashAction.Hash -> hash(state, action.bucket)
        is HashAction.Resolve -> resolve(state, action.resolution)
        is HashAction.Inspect -> inspect(state, action.key)
        HashAction.Settle -> settle(state)
    }

    private fun hash(state: HashMapState, bucket: Int): Transition<HashMapState> {
        val task = state.task ?: return refuse(state)
        if (bucket != state.hash(task.key)) return refuse(state)

        val chain = state.chain(bucket)
        // Where the operation goes next is decided by what is already in the bucket,
        // which is exactly the branch the structure is built around.
        val next = when {
            task is HashTask.Put && chain.isEmpty() -> HashPhase.SETTLED
            task is HashTask.Put -> HashPhase.RESOLVING
            else -> HashPhase.SCANNING
        }
        return Transition(
            next = state.copy(bucket = bucket, phase = next),
            events = listOf(VizEvent.Examine(listOf(bucket), ExamineRole.INSPECTING)),
            narration = NarrationKey(
                NarrationId.HASH_HASHED,
                listOf(task.key, state.modulus, bucket),
            ),
            correct = true,
        )
    }

    private fun resolve(state: HashMapState, resolution: Resolution): Transition<HashMapState> {
        val put = state.task as? HashTask.Put ?: return refuse(state)
        val bucket = state.bucket ?: return refuse(state)
        val duplicate = state.chain(bucket).any { it.key == put.key }
        val wanted = if (duplicate) Resolution.REPLACE else Resolution.CHAIN
        if (resolution != wanted) return refuse(state)

        return Transition(
            next = state.copy(
                phase = HashPhase.SETTLED,
                collided = !duplicate,
                updated = duplicate,
            ),
            events = emptyList(),
            narration = NarrationKey(
                if (duplicate) NarrationId.HASH_UPDATED else NarrationId.HASH_COLLISION,
                listOf(put.key, state.chain(bucket).first().key, bucket),
            ),
            correct = true,
        )
    }

    private fun inspect(state: HashMapState, key: Int?): Transition<HashMapState> {
        val task = state.task ?: return refuse(state)
        val bucket = state.bucket ?: return refuse(state)
        val match = state.chain(bucket).firstOrNull { it.key == task.key }
        if (key != match?.key) return refuse(state)

        return Transition(
            next = state.copy(phase = HashPhase.SETTLED, hit = match, missing = match == null),
            events = listOfNotNull(
                match?.let { VizEvent.Examine(listOf(bucket), ExamineRole.CANDIDATE) },
            ),
            narration = if (match == null) {
                // Not finding something is an answer. A hash map proves absence by
                // looking in exactly one bucket, which is the whole trick.
                NarrationKey(NarrationId.HASH_NOT_FOUND, listOf(task.key, bucket))
            } else {
                NarrationKey(NarrationId.HASH_FOUND, listOf(task.key, match.label))
            },
            correct = true,
        )
    }

    /**
     * The app's half: put the entry in, take it out, and move to the next task. No
     * judgement happens here — everything worth deciding has already been decided.
     */
    private fun settle(state: HashMapState): Transition<HashMapState> {
        val task = state.task ?: return refuse(state)
        val bucket = state.bucket ?: return advance(state, emptyList(), null)

        return when (task) {
            is HashTask.Put -> {
                val chain = state.chain(bucket)
                val duplicate = chain.any { it.key == task.key }
                val entry = MapEntry(task.key, task.label)
                val updatedChain = if (duplicate) {
                    chain.map { if (it.key == task.key) entry else it }
                } else {
                    chain + entry
                }
                advance(
                    state.copy(buckets = state.buckets.replacing(bucket, updatedChain)),
                    listOf(VizEvent.Insert(task.key, bucket)),
                    NarrationKey(
                        if (duplicate) NarrationId.HASH_STORED_UPDATE else NarrationId.HASH_STORED,
                        listOf(task.key, task.label, bucket),
                    ),
                )
            }

            is HashTask.Remove -> {
                val hit = state.hit
                    ?: return advance(
                        state,
                        emptyList(),
                        NarrationKey(NarrationId.HASH_NOTHING_TO_REMOVE, listOf(task.key)),
                    )
                advance(
                    state.copy(
                        buckets = state.buckets.replacing(
                            bucket,
                            state.chain(bucket).filterNot { it.key == hit.key },
                        ),
                    ),
                    listOf(VizEvent.Remove(bucket)),
                    NarrationKey(NarrationId.HASH_REMOVED, listOf(hit.key, bucket)),
                )
            }

            is HashTask.Get -> advance(state, emptyList(), null)
        }
    }

    private fun advance(
        state: HashMapState,
        events: List<VizEvent>,
        narration: NarrationKey?,
    ): Transition<HashMapState> {
        val next = state.at + 1
        return Transition(
            next = state.copy(
                at = next,
                done = next >= state.script.size,
                phase = if (next >= state.script.size) HashPhase.SETTLED else HashPhase.HASHING,
                bucket = null,
                hit = null,
                collided = false,
                updated = false,
                missing = false,
            ),
            events = events,
            narration = narration,
            correct = true,
        )
    }

    /** An action the state cannot accept. Nothing moves, and nothing is lost. */
    private fun refuse(state: HashMapState) =
        Transition(state, emptyList(), null, correct = false)
}

private fun List<List<MapEntry>>.replacing(index: Int, chain: List<MapEntry>) =
    mapIndexed { i, existing -> if (i == index) chain else existing }
