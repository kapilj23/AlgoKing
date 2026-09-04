package com.ttele.algoking.engine.algorithms.graphdfs

import com.ttele.algoking.engine.core.Frame
import com.ttele.algoking.engine.event.Metrics
import com.ttele.algoking.engine.narration.NarrationId
import com.ttele.algoking.engine.narration.NarrationKey
import com.ttele.algoking.engine.scene.Scene
import com.ttele.algoking.engine.walkthrough.PartialStep
import com.ttele.algoking.engine.walkthrough.WatchNarrator
import com.ttele.algoking.engine.walkthrough.WatchStepKind

/**
 * The DFS walkthrough.
 *
 * Every beat is one move, and each move says **why** rather than what:
 *
 *  - going deeper names the neighbour *and* that it was the first unvisited one;
 *  - a backtrack names the dead end that forced it.
 *
 * The backtracks get the same weight as the visits, deliberately. A learner who
 * only sees the five visits has watched a list being written down; the two
 * unwinds are where recursion becomes visible, and skipping them is how DFS gets
 * taught as `A → B → D → E → C` and nothing more.
 */
class DfsWatchNarrator : WatchNarrator<DfsState> {

    override fun opening(state: DfsState, scene: Scene): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.SETUP,
            scene = scene,
            headline = NarrationKey(NarrationId.DFS_WATCH_SETUP, listOf(label(state, state.start))),
            support = NarrationKey(NarrationId.DFS_WATCH_SETUP_SUPPORT),
        ),
    )

    override fun onFrame(
        previous: DfsState,
        frame: Frame<DfsState>,
        scene: Scene,
    ): List<PartialStep> {
        val state = frame.state

        // -- A backtrack -------------------------------------------------------
        if (state.stack.size < previous.stack.size) {
            val left = previous.current.orEmpty()
            val to = state.current
            return listOf(
                PartialStep(
                    kind = WatchStepKind.ELIMINATE,
                    scene = scene,
                    headline = NarrationKey(
                        NarrationId.DFS_WATCH_DEAD_END,
                        listOf(label(state, left)),
                    ),
                    support = to?.let {
                        NarrationKey(
                            NarrationId.DFS_WATCH_BACKTRACK,
                            listOf(label(state, left), label(state, it)),
                        )
                    },
                ),
            )
        }

        // -- A visit -----------------------------------------------------------
        val visited = state.visited.lastOrNull() ?: return emptyList()
        if (state.visited.size == previous.visited.size) return emptyList()

        val first = previous.visited.isEmpty()
        val from = previous.current
        return listOf(
            PartialStep(
                kind = if (state.finished) WatchStepKind.FOUND else WatchStepKind.EXAMINE,
                scene = scene,
                headline = when {
                    first -> NarrationKey(
                        NarrationId.DFS_WATCH_VISIT_START,
                        listOf(label(state, visited)),
                    )

                    else -> NarrationKey(
                        NarrationId.DFS_WATCH_GO_DEEPER,
                        listOf(label(state, from.orEmpty()), label(state, visited)),
                    )
                },
                support = when {
                    first -> NarrationKey(NarrationId.DFS_WATCH_VISIT_START_SUPPORT)
                    state.finished -> NarrationKey(
                        NarrationId.DFS_WATCH_COMPLETE,
                        listOf(state.visited.joinToString(" → ") { label(state, it) }),
                    )
                    // Naming the skipped neighbours is the third judgement, and it
                    // is the one a learner never asks about until they get it wrong.
                    else -> skipped(previous, state, visited)
                },
            ),
        )
    }

    /**
     * "B's neighbours are A, D and E — A is already visited, so DFS takes D."
     *
     * Only said when something actually was skipped; otherwise it is noise.
     */
    private fun skipped(previous: DfsState, state: DfsState, chosen: String): NarrationKey {
        val from = previous.current ?: return NarrationKey(NarrationId.DFS_WATCH_FIRST_UNVISITED)
        val visitedNeighbours = state.graph.neighbours(from).filter { previous.isVisited(it) }
        return if (visitedNeighbours.isEmpty()) {
            NarrationKey(NarrationId.DFS_WATCH_FIRST_UNVISITED)
        } else {
            NarrationKey(
                NarrationId.DFS_WATCH_SKIPPED,
                listOf(
                    visitedNeighbours.joinToString(", ") { label(state, it) },
                    label(state, chosen),
                ),
            )
        }
    }

    override fun closing(
        state: DfsState,
        metrics: Metrics,
        scene: Scene,
    ): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.INSIGHT,
            scene = scene,
            headline = NarrationKey(NarrationId.DFS_WATCH_INSIGHT),
            support = NarrationKey(NarrationId.DFS_WATCH_INSIGHT_SUPPORT),
        ),
        PartialStep(
            kind = WatchStepKind.SUMMARY,
            scene = scene,
            headline = NarrationKey(
                NarrationId.DFS_WATCH_SUMMARY,
                listOf(state.visited.joinToString(" → ") { label(state, it) }),
            ),
            support = NarrationKey(NarrationId.DFS_WATCH_SUMMARY_SUPPORT),
            bullets = listOf(
                NarrationKey(NarrationId.DFS_IDEA_1),
                NarrationKey(NarrationId.DFS_IDEA_2),
                NarrationKey(NarrationId.DFS_IDEA_3),
                NarrationKey(NarrationId.DFS_IDEA_4),
            ),
        ),
    )

    private fun label(state: DfsState, id: String): String =
        state.graph.node(id)?.label ?: id
}
