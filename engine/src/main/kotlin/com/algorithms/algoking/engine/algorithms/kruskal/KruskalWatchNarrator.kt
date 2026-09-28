package com.algorithms.algoking.engine.algorithms.kruskal

import com.algorithms.algoking.engine.core.Frame
import com.algorithms.algoking.engine.event.Metrics
import com.algorithms.algoking.engine.narration.NarrationId
import com.algorithms.algoking.engine.narration.NarrationKey
import com.algorithms.algoking.engine.scene.Scene
import com.algorithms.algoking.engine.walkthrough.PartialStep
import com.algorithms.algoking.engine.walkthrough.WatchNarrator
import com.algorithms.algoking.engine.walkthrough.WatchStepKind

/**
 * The Kruskal walkthrough.
 *
 * One beat per edge looked at — a take or a skip, each with the whole sorted list
 * and the groups beside it — then one beat saying the walk stopped early, because
 * the most expensive edges are never looked at and that is worth seeing.
 */
class KruskalWatchNarrator : WatchNarrator<KruskalState> {

    /** To redraw a beat around the edge it is about, rather than the next one. */
    private val projector = KruskalProjector()

    override fun opening(state: KruskalState, scene: Scene): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.SETUP,
            scene = scene,
            headline = NarrationKey(NarrationId.KR_WATCH_SETUP),
            support = NarrationKey(
                NarrationId.KR_WATCH_SETUP_SUPPORT,
                listOf(state.graph.nodes.size, state.needed),
            ),
            edgeList = state.edgeListReadout(),
        ),
    )

    override fun onFrame(
        previous: KruskalState,
        frame: Frame<KruskalState>,
        scene: Scene,
    ): List<PartialStep> {
        val state = frame.state
        if (state.cursor == previous.cursor) return emptyList()

        val edge = previous.sorted[previous.cursor]
        val took = state.taken.size > previous.taken.size
        val groupA = groupLabel(previous.groupOf(edge.a))
        val groupB = groupLabel(previous.groupOf(edge.b))

        // The picture and the list are about the edge just decided, not the next.
        val focused = projector.scene(state, focus = edge)
        val list = state.edgeListReadout(showNext = false)

        val steps = mutableListOf(
            if (took) {
                PartialStep(
                    kind = WatchStepKind.ADD,
                    scene = focused,
                    headline = NarrationKey(NarrationId.KR_WATCH_TAKE, listOf(edge.label, edge.weight)),
                    support = NarrationKey(
                        NarrationId.KR_WATCH_TAKE_WHY,
                        listOf(edge.a, groupA, edge.b, groupB, state.total),
                    ),
                    edgeList = list,
                )
            } else {
                PartialStep(
                    kind = WatchStepKind.ELIMINATE,
                    scene = focused,
                    headline = NarrationKey(NarrationId.KR_WATCH_SKIP, listOf(edge.label, edge.weight)),
                    support = NarrationKey(
                        NarrationId.KR_WATCH_SKIP_WHY,
                        listOf(edge.a, edge.b, groupA),
                    ),
                    edgeList = list,
                )
            },
        )

        if (state.finished) {
            val unseen = state.sorted.drop(state.cursor)
            steps += PartialStep(
                kind = WatchStepKind.FOUND,
                scene = scene,
                headline = NarrationKey(
                    NarrationId.KR_WATCH_DONE,
                    listOf(state.taken.size, state.graph.nodes.size, state.total),
                ),
                support = NarrationKey(
                    if (unseen.isEmpty()) NarrationId.KR_WATCH_DONE_WHY_ALL else NarrationId.KR_WATCH_DONE_WHY,
                    listOf(unseen.joinToString(", ") { "${it.label} (${it.weight})" }, unseen.size),
                ),
            )
        }
        return steps
    }

    override fun closing(
        state: KruskalState,
        metrics: Metrics,
        scene: Scene,
    ): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.INSIGHT,
            scene = scene,
            headline = NarrationKey(NarrationId.KR_WATCH_INSIGHT),
            support = NarrationKey(NarrationId.KR_WATCH_INSIGHT_SUPPORT),
        ),
        PartialStep(
            kind = WatchStepKind.SUMMARY,
            scene = scene,
            headline = NarrationKey(
                NarrationId.KR_WATCH_SUMMARY,
                listOf(state.taken.joinToString(",  ") { "${it.label} (${it.weight})" }, state.total),
            ),
            support = NarrationKey(NarrationId.KR_WATCH_SUMMARY_SUPPORT),
            bullets = listOf(
                NarrationKey(NarrationId.KR_IDEA_1),
                NarrationKey(NarrationId.KR_IDEA_2),
                NarrationKey(NarrationId.KR_IDEA_3),
                NarrationKey(NarrationId.KR_IDEA_4),
                NarrationKey(NarrationId.KR_IDEA_5),
            ),
        ),
    )
}
