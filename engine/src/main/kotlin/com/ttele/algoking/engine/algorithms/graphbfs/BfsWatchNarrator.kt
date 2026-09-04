package com.ttele.algoking.engine.algorithms.graphbfs

import com.ttele.algoking.engine.core.Frame
import com.ttele.algoking.engine.core.joinNames
import com.ttele.algoking.engine.event.Metrics
import com.ttele.algoking.engine.narration.NarrationId
import com.ttele.algoking.engine.narration.NarrationKey
import com.ttele.algoking.engine.scene.Scene
import com.ttele.algoking.engine.walkthrough.PartialStep
import com.ttele.algoking.engine.walkthrough.WatchNarrator
import com.ttele.algoking.engine.walkthrough.WatchStepKind

/**
 * The BFS walkthrough.
 *
 * Every beat is one queue operation, and each names the queue rather than the
 * graph — because the queue is what makes this breadth-first. A dequeue says what
 * came off the front; an enqueue says what joined the back and, where it applies,
 * which neighbour was skipped for being seen already.
 *
 * The insight is the comparison with DFS, and it lands last: `A → B → C → D → E`
 * against `A → B → D → E → C` means nothing until the learner has watched the
 * queue produce the first one.
 */
class BfsWatchNarrator : WatchNarrator<BfsState> {

    override fun opening(state: BfsState, scene: Scene): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.SETUP,
            scene = scene,
            headline = NarrationKey(NarrationId.BFS_WATCH_SETUP, listOf(label(state, state.start))),
            support = NarrationKey(NarrationId.BFS_WATCH_SETUP_SUPPORT),
        ),
    )

    override fun onFrame(
        previous: BfsState,
        frame: Frame<BfsState>,
        scene: Scene,
    ): List<PartialStep> {
        val state = frame.state

        // -- The seed ----------------------------------------------------------
        if (previous.visited.isEmpty() && state.visited.size == 1) {
            return listOf(
                PartialStep(
                    kind = WatchStepKind.EXAMINE,
                    scene = scene,
                    headline = NarrationKey(
                        NarrationId.BFS_WATCH_SEED,
                        listOf(label(state, state.start)),
                    ),
                    support = NarrationKey(NarrationId.BFS_WATCH_SEED_SUPPORT),
                ),
            )
        }

        // -- A dequeue ---------------------------------------------------------
        if (state.dequeued.size > previous.dequeued.size) {
            val node = state.dequeued.last()
            return listOf(
                PartialStep(
                    kind = if (state.finished) WatchStepKind.FOUND else WatchStepKind.COMPARE,
                    scene = scene,
                    headline = NarrationKey(
                        NarrationId.BFS_WATCH_DEQUEUE,
                        listOf(label(state, node)),
                    ),
                    support = when {
                        state.finished -> NarrationKey(
                            NarrationId.BFS_WATCH_COMPLETE,
                            listOf(state.dequeued.joinToString(" → ") { label(state, it) }),
                        )
                        // Naming what is left to do is what makes the queue feel
                        // like a plan rather than a list.
                        state.queue.isEmpty() -> NarrationKey(
                            NarrationId.BFS_WATCH_QUEUE_EMPTYING,
                        )

                        else -> NarrationKey(
                            NarrationId.BFS_WATCH_QUEUE_NOW,
                            listOf(state.queue.joinToString(", ") { label(state, it) }),
                        )
                    },
                ),
            )
        }

        // -- An enqueue --------------------------------------------------------
        if (state.visited.size > previous.visited.size) {
            val node = state.visited.last()
            val from = state.current
            val skipped = from?.let { f ->
                state.graph.neighbours(f)
                    .takeWhile { it != node }
                    .filter { previous.isVisited(it) }
            }.orEmpty()

            return listOf(
                PartialStep(
                    kind = WatchStepKind.EXAMINE,
                    scene = scene,
                    headline = NarrationKey(
                        NarrationId.BFS_WATCH_ENQUEUE,
                        listOf(label(state, node), from?.let { label(state, it) }.orEmpty()),
                    ),
                    support = if (skipped.isEmpty()) {
                        NarrationKey(
                            NarrationId.BFS_WATCH_QUEUE_NOW,
                            listOf(state.queue.joinToString(", ") { label(state, it) }),
                        )
                    } else {
                        // The "skip visited" judgement, said out loud the moment
                        // it happens rather than as a rule up front.
                        NarrationKey(
                            if (skipped.size == 1) {
                                NarrationId.BFS_WATCH_SKIPPED
                            } else {
                                NarrationId.BFS_WATCH_SKIPPED_MANY
                            },
                            listOf(
                                joinNames(skipped.map { label(state, it) }),
                                label(state, node),
                            ),
                        )
                    },
                ),
            )
        }

        return emptyList()
    }

    override fun closing(
        state: BfsState,
        metrics: Metrics,
        scene: Scene,
    ): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.INSIGHT,
            scene = scene,
            headline = NarrationKey(NarrationId.BFS_WATCH_INSIGHT),
            // The DFS comparison, in one line. Short on purpose — this is not a
            // comparison screen, it is the moment the difference is obvious.
            support = NarrationKey(
                NarrationId.BFS_WATCH_INSIGHT_SUPPORT,
                listOf(state.dequeued.joinToString(" → ") { label(state, it) }),
            ),
        ),
        PartialStep(
            kind = WatchStepKind.SUMMARY,
            scene = scene,
            headline = NarrationKey(
                NarrationId.BFS_WATCH_SUMMARY,
                listOf(state.dequeued.joinToString(" → ") { label(state, it) }),
            ),
            support = NarrationKey(NarrationId.BFS_WATCH_SUMMARY_SUPPORT),
            bullets = listOf(
                NarrationKey(NarrationId.BFS_IDEA_1),
                NarrationKey(NarrationId.BFS_IDEA_2),
                NarrationKey(NarrationId.BFS_IDEA_3),
                NarrationKey(NarrationId.BFS_IDEA_4),
            ),
        ),
    )

    private fun label(state: BfsState, id: String): String =
        state.graph.node(id)?.label ?: id
}
