package com.ttele.algoking.engine.algorithms.hashing

import com.ttele.algoking.engine.core.Frame
import com.ttele.algoking.engine.event.Metrics
import com.ttele.algoking.engine.narration.NarrationId
import com.ttele.algoking.engine.narration.NarrationKey
import com.ttele.algoking.engine.scene.Scene
import com.ttele.algoking.engine.walkthrough.PartialStep
import com.ttele.algoking.engine.walkthrough.WatchNarrator
import com.ttele.algoking.engine.walkthrough.WatchPrediction
import com.ttele.algoking.engine.walkthrough.WatchStepKind

/**
 * The Hash Map walkthrough.
 *
 * It opens by naming the parts — buckets, a key, a value, and the function that
 * joins them — using states the narrator builds itself, because "what is a key?" is
 * not something an engine transition can produce. Then the script takes over and
 * every idea after that is a real operation: store, collide, look up, remove.
 *
 * The collision is engineered, not stumbled into. A hash map that never collides
 * teaches nothing about hash maps.
 */
class HashMapWatchNarrator : WatchNarrator<HashMapState> {

    private val projector = HashMapProjector()

    private var predictionPlaced = false
    private var explainedCollision = false

    // ── The parts, before anything happens ────────────────────────────────────

    override fun opening(state: HashMapState, scene: Scene): List<PartialStep> {
        val empty = state.copy(phase = HashPhase.SETTLED, bucket = null, at = state.script.size)
        val firstPut = state.script.firstOrNull() as? HashTask.Put
        val key = firstPut?.key ?: 0
        val label = firstPut?.label.orEmpty()

        // The same empty table, captioned three different ways: the structure, then
        // the pair it stores, then the function that decides where.
        val blank = projector.project(empty, emptyList())

        return listOf(
            PartialStep(
                kind = WatchStepKind.SETUP,
                scene = blank,
                headline = NarrationKey(NarrationId.HASH_WATCH_MEET),
                support = NarrationKey(NarrationId.HASH_WATCH_PURPOSE),
            ),
            PartialStep(
                kind = WatchStepKind.SETUP,
                scene = blank,
                headline = NarrationKey(NarrationId.HASH_WATCH_KEY_VALUE, listOf(key, label)),
                support = NarrationKey(NarrationId.HASH_WATCH_KEY_FINDS),
            ),
            PartialStep(
                kind = WatchStepKind.SETUP,
                // The key in flight, arithmetic showing, answer not yet given.
                scene = projector.project(
                    state.copy(phase = HashPhase.HASHING, bucket = null),
                    emptyList(),
                ),
                headline = NarrationKey(
                    NarrationId.HASH_WATCH_FUNCTION,
                    listOf(key, state.modulus, state.hash(key)),
                ),
                support = NarrationKey(NarrationId.HASH_WATCH_FUNCTION_SUPPORT),
            ),
        )
    }

    // ── The operations ────────────────────────────────────────────────────────

    override fun onFrame(
        previous: HashMapState,
        frame: Frame<HashMapState>,
        scene: Scene,
    ): List<PartialStep> {
        val task = previous.task ?: return emptyList()
        return when (previous.phase) {
            HashPhase.HASHING -> hashedStep(previous, frame, task, scene)
            HashPhase.RESOLVING -> resolvedStep(previous, task, scene)
            HashPhase.SCANNING -> scannedStep(previous, frame, task, scene)
            // The store or the removal actually landing.
            HashPhase.SETTLED -> settledStep(previous, frame, task, scene)
        }
    }

    private fun hashedStep(
        previous: HashMapState,
        frame: Frame<HashMapState>,
        task: HashTask,
        scene: Scene,
    ): List<PartialStep> {
        val bucket = previous.hash(task.key)
        val args = listOf(task.key, previous.modulus, bucket)
        val step = PartialStep(
            kind = WatchStepKind.EXAMINE,
            scene = scene,
            headline = NarrationKey(
                when (task) {
                    is HashTask.Put -> NarrationId.HASH_WATCH_PUT_HASH
                    is HashTask.Get -> NarrationId.HASH_WATCH_GET_HASH
                    is HashTask.Remove -> NarrationId.HASH_WATCH_REMOVE_HASH
                },
                args,
            ),
            support = when (task) {
                is HashTask.Get -> NarrationKey(NarrationId.HASH_WATCH_JUMP_STRAIGHT)
                else -> null
            },
        )
        // The checkpoint goes in the moment the arithmetic has been shown once and
        // before it is asked for real.
        return listOf(step) + listOfNotNull(checkpoint(frame.state))
    }

    private fun resolvedStep(
        previous: HashMapState,
        task: HashTask,
        scene: Scene,
    ): List<PartialStep> {
        val put = task as HashTask.Put
        val bucket = requireNotNull(previous.bucket)
        val neighbour = previous.chain(bucket).first()
        val duplicate = previous.chain(bucket).any { it.key == put.key }

        if (duplicate) {
            return listOf(
                PartialStep(
                    kind = WatchStepKind.ADD,
                    scene = scene,
                    headline = NarrationKey(NarrationId.HASH_WATCH_SAME_KEY, listOf(put.key)),
                    support = NarrationKey(NarrationId.HASH_WATCH_SAME_KEY_SUPPORT),
                ),
            )
        }

        // Two beats, because the surprise and the answer are different ideas. A
        // learner who sees "collision" and "here is what a hash map does about it"
        // in one breath never gets to be surprised, and never remembers it.
        val steps = listOf(
            PartialStep(
                kind = WatchStepKind.EXAMINE,
                scene = scene,
                headline = NarrationKey(NarrationId.HASH_WATCH_WAIT, listOf(bucket, neighbour.key)),
                support = NarrationKey(NarrationId.HASH_WATCH_COLLISION_NAMED),
            ),
            PartialStep(
                kind = WatchStepKind.INSIGHT,
                scene = scene,
                headline = NarrationKey(NarrationId.HASH_WATCH_CHAINING),
                support = NarrationKey(NarrationId.HASH_WATCH_CHAINING_SUPPORT)
                    .takeUnless { explainedCollision },
            ),
        )
        explainedCollision = true
        return steps
    }

    private fun scannedStep(
        previous: HashMapState,
        frame: Frame<HashMapState>,
        task: HashTask,
        scene: Scene,
    ): List<PartialStep> {
        val bucket = requireNotNull(previous.bucket)
        val hit = frame.state.hit
        // Finding the entry and taking it out are one idea. Giving the scan its own
        // beat would repeat the lookup step the learner has already seen.
        if (task is HashTask.Remove && hit != null) return emptyList()
        return listOf(
            PartialStep(
                kind = if (hit == null) WatchStepKind.NOT_FOUND else WatchStepKind.FOUND,
                scene = scene,
                headline = if (hit == null) {
                    NarrationKey(NarrationId.HASH_WATCH_NOT_HERE, listOf(task.key, bucket))
                } else {
                    NarrationKey(NarrationId.HASH_WATCH_FOUND, listOf(task.key, hit.label))
                },
                support = when {
                    hit == null -> null
                    previous.chain(bucket).size > 1 -> {
                        NarrationKey(NarrationId.HASH_WATCH_SCANNED_CHAIN, listOf(bucket))
                    }

                    else -> NarrationKey(NarrationId.HASH_WATCH_ONE_BUCKET)
                },
            ),
        )
    }

    private fun settledStep(
        previous: HashMapState,
        frame: Frame<HashMapState>,
        task: HashTask,
        scene: Scene,
    ): List<PartialStep> = when (task) {
        is HashTask.Put -> listOf(
            PartialStep(
                kind = WatchStepKind.ADD,
                scene = scene,
                headline = NarrationKey(
                    NarrationId.HASH_WATCH_STORED,
                    listOf(task.key, task.label, previous.bucket ?: 0),
                ),
                support = NarrationKey(NarrationId.HASH_WATCH_STORED_SUPPORT)
                    .takeIf { previous.at == 0 },
            ),
        )

        is HashTask.Remove -> listOf(
            PartialStep(
                kind = WatchStepKind.REMOVE,
                scene = scene,
                headline = NarrationKey(NarrationId.HASH_WATCH_REMOVED, listOf(task.key)),
                support = NarrationKey(NarrationId.HASH_WATCH_REMOVED_SUPPORT),
            ),
        )

        // A lookup changes nothing, so its settle frame has nothing new to say.
        is HashTask.Get -> emptyList()
    }

    /**
     * One unscored checkpoint: *which bucket will this key land in?* It is the
     * single skill everything else in the lesson rests on.
     */
    private fun checkpoint(state: HashMapState): PartialStep? {
        if (predictionPlaced) return null
        predictionPlaced = true

        // A key the lesson has not used, so the answer cannot be recalled.
        val key = state.modulus * 3 + 2
        val answer = state.hash(key)
        return PartialStep(
            kind = WatchStepKind.PREDICT,
            scene = projector.project(
                state.copy(phase = HashPhase.SETTLED, bucket = null),
                emptyList(),
            ),
            headline = NarrationKey(NarrationId.HASH_PREDICT_PROMPT, listOf(key)),
            prediction = WatchPrediction(
                prompt = NarrationKey(NarrationId.HASH_PREDICT_PROMPT, listOf(key)),
                options = (0 until state.modulus).map {
                    NarrationKey(NarrationId.HASH_OPTION_BUCKET, listOf(it))
                },
                correctIndex = answer,
                whenRight = NarrationKey(
                    NarrationId.HASH_PREDICT_RIGHT,
                    listOf(key, state.modulus, answer),
                ),
                whenWrong = NarrationKey(
                    NarrationId.HASH_PREDICT_WRONG,
                    listOf(key, state.modulus),
                ),
            ),
        )
    }

    // ── Why any of this is worth doing ────────────────────────────────────────

    override fun closing(
        state: HashMapState,
        metrics: Metrics,
        scene: Scene,
    ) = listOf(
        PartialStep(
            kind = WatchStepKind.INSIGHT,
            scene = scene,
            headline = NarrationKey(NarrationId.HASH_WATCH_WHY_FAST),
            support = NarrationKey(NarrationId.HASH_WATCH_WHY_FAST_SUPPORT),
        ),
        PartialStep(
            kind = WatchStepKind.INSIGHT,
            scene = scene,
            headline = NarrationKey(NarrationId.HASH_WATCH_VS_ARRAY),
            support = NarrationKey(NarrationId.HASH_WATCH_VS_ARRAY_SUPPORT),
        ),
        PartialStep(
            kind = WatchStepKind.INSIGHT,
            scene = scene,
            headline = NarrationKey(NarrationId.HASH_WATCH_AVERAGE),
            support = NarrationKey(NarrationId.HASH_WATCH_AVERAGE_SUPPORT),
        ),
        PartialStep(
            kind = WatchStepKind.SUMMARY,
            scene = scene,
            headline = NarrationKey(NarrationId.HASH_WATCH_SUMMARY),
            support = NarrationKey(NarrationId.HASH_WATCH_SUMMARY_SUPPORT),
            bullets = listOf(
                NarrationKey(NarrationId.HASH_IDEA_1),
                NarrationKey(NarrationId.HASH_IDEA_2),
                NarrationKey(NarrationId.HASH_IDEA_3),
            ),
        ),
    )
}
