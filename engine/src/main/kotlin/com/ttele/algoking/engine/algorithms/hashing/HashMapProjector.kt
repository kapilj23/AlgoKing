package com.ttele.algoking.engine.algorithms.hashing

import com.ttele.algoking.engine.event.MarkId
import com.ttele.algoking.engine.event.MeterId
import com.ttele.algoking.engine.event.VizEvent
import com.ttele.algoking.engine.scene.Badge
import com.ttele.algoking.engine.scene.BucketRow
import com.ttele.algoking.engine.scene.BucketScene
import com.ttele.algoking.engine.scene.BucketState
import com.ttele.algoking.engine.scene.EntryCell
import com.ttele.algoking.engine.scene.EntryState
import com.ttele.algoking.engine.scene.FlowOperation
import com.ttele.algoking.engine.scene.FlowStage
import com.ttele.algoking.engine.scene.HashFlow
import com.ttele.algoking.engine.scene.MeterReadout
import com.ttele.algoking.engine.scene.SceneProjector

/**
 * Draws a hash map.
 *
 * Two things are on screen at once, and both are the lesson: the **table** of
 * buckets, and the **flow** above it that shows a key turning into a bucket index.
 * The flow is what makes the structure make sense — without it the table is just a
 * list of lists, and the learner never sees where the speed comes from.
 */
class HashMapProjector : SceneProjector<HashMapState> {

    override fun project(
        state: HashMapState,
        activeEvents: List<VizEvent>,
    ): BucketScene {
        val task = state.task
        val target = state.bucket

        val rows = state.buckets.mapIndexed { index, chain ->
            BucketRow(
                index = index,
                entries = chain.map { entry -> EntryCell(entry.key, entry.label, entryState(state, entry)) },
                state = when {
                    index == target -> BucketState.TARGET
                    // A shared bucket says so even when nothing is happening in it:
                    // collisions are a standing fact about the map, not an event.
                    chain.size > 1 -> BucketState.COLLIDED
                    else -> BucketState.IDLE
                },
            )
        }

        return BucketScene(
            buckets = rows,
            flow = task?.let { flowFor(state, it) },
            badge = task?.let { Badge(MarkId.TARGET, badgeLabel(it), it.key) },
            meters = listOf(
                MeterReadout(MeterId.REMAINING, "Entries", state.entryCount.toLong()),
                MeterReadout(MeterId.BEST, "Shared buckets", state.collisions.toLong()),
            ),
        )
    }

    private fun entryState(state: HashMapState, entry: MapEntry): EntryState {
        val task = state.task
        return when {
            state.hit?.key == entry.key && task is HashTask.Remove -> EntryState.LEAVING
            state.hit?.key == entry.key -> EntryState.MATCHED
            // Everything in the bucket the key landed in is under comparison — which
            // is precisely why a bucket with two entries costs more than one with one.
            state.phase == HashPhase.SCANNING && state.hash(task?.key ?: -1) == state.bucket &&
                state.chain(state.bucket ?: -1).any { it.key == entry.key } -> EntryState.SCANNING

            else -> EntryState.IDLE
        }
    }

    /**
     * The key's journey. It stops where the learner has got to, so the arithmetic is
     * on screen *before* the answer is — which is what makes the question askable.
     */
    private fun flowFor(state: HashMapState, task: HashTask): HashFlow = HashFlow(
        key = task.key,
        modulus = state.modulus,
        bucket = state.bucket,
        stage = when (state.phase) {
            HashPhase.HASHING -> FlowStage.ASKING
            HashPhase.RESOLVING -> FlowStage.HASHED
            HashPhase.SCANNING -> FlowStage.SCANNING
            HashPhase.SETTLED -> if (state.bucket == null) FlowStage.KEY else FlowStage.DONE
        },
        operation = when (task) {
            is HashTask.Put -> FlowOperation.PUT
            is HashTask.Get -> FlowOperation.LOOKUP
            is HashTask.Remove -> FlowOperation.REMOVE
        },
    )

    private fun badgeLabel(task: HashTask) = when (task) {
        is HashTask.Put -> "Storing"
        is HashTask.Get -> "Looking up"
        is HashTask.Remove -> "Removing"
    }
}
