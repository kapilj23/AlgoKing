package com.ttele.algoking.engine.algorithms.dijkstra

import com.ttele.algoking.engine.core.Frame
import com.ttele.algoking.engine.event.Metrics
import com.ttele.algoking.engine.event.Relation
import com.ttele.algoking.engine.narration.NarrationId
import com.ttele.algoking.engine.narration.NarrationKey
import com.ttele.algoking.engine.scene.Scene
import com.ttele.algoking.engine.walkthrough.ComparisonReadout
import com.ttele.algoking.engine.walkthrough.PartialStep
import com.ttele.algoking.engine.walkthrough.WatchNarrator
import com.ttele.algoking.engine.walkthrough.WatchStepKind

/**
 * The Dijkstra walkthrough.
 *
 * Nineteen beats on the teaching graph, and the shape of them is the argument:
 * **four are relaxations**, and the one that changes nothing gets the same weight
 * as the three that do.
 *
 * The rhythm is one beat per node chosen, one per node reached for the first time,
 * and one per distance actually put to the test. A first reach is narrated but
 * never *asked* — ∞ loses to everything, so there is nothing to compare — which
 * leaves one **decision** per judgement, which is what TRY then asks for.
 *
 * What earns no beat at all: settling an ordinary node, and stepping past a
 * neighbour that is already settled. Neither changes the picture, and a beat where
 * nothing changed is a bug rather than a step (ADR-020).
 *
 * `docs/dijkstra-plan.md` §5 sketched thirteen by folding each first reach into
 * the beat that caused it. The sequence that shipped is pinned, beat by beat, by
 * `DijkstraTest.the walkthrough is exactly the beats the lesson was designed as`.
 */
class DijkstraWatchNarrator : WatchNarrator<DijkstraState> {

    override fun opening(state: DijkstraState, scene: Scene): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.SETUP,
            scene = scene,
            headline = NarrationKey(
                NarrationId.DIJ_WATCH_SETUP,
                listOf(state.start, state.target),
            ),
            // The one sentence the whole lesson rests on.
            support = NarrationKey(NarrationId.DIJ_WATCH_SETUP_SUPPORT, listOf(state.start)),
        ),
    )

    override fun onFrame(
        previous: DijkstraState,
        frame: Frame<DijkstraState>,
        scene: Scene,
    ): List<PartialStep> {
        val state = frame.state

        // -- A node was chosen to process -------------------------------------
        if (state.current != null && previous.current == null) {
            val node = state.current
            val distance = state.distanceOf(node) ?: 0
            // Was this node's distance beaten down to get here? If so, say so —
            // it is why the selection rule is worth having.
            val improved = state.predecessors[node] != null && node != state.start
            return listOf(
                PartialStep(
                    kind = WatchStepKind.COMPARE,
                    scene = scene,
                    headline = NarrationKey(
                        NarrationId.DIJ_WATCH_SELECT,
                        listOf(node, distance),
                    ),
                    support = NarrationKey(
                        if (improved) {
                            NarrationId.DIJ_WATCH_SELECT_IMPROVED_WHY
                        } else {
                            NarrationId.DIJ_WATCH_SELECT_WHY
                        },
                        listOf(node, distance, state.predecessors[node] ?: state.start),
                    ),
                ),
            )
        }

        // -- A neighbour was reached for the first time ------------------------
        // Folded rather than skipped: it is where a distance comes from, and the
        // beat says the arithmetic out loud without asking anything.
        val reached = state.distances.keys - previous.distances.keys
        if (reached.isNotEmpty() && state.pending == null) {
            val node = reached.first()
            return listOf(
                PartialStep(
                    kind = WatchStepKind.EXAMINE,
                    scene = scene,
                    headline = NarrationKey(
                        NarrationId.DIJ_WATCH_REACH,
                        listOf(node, state.distanceOf(node) ?: 0),
                    ),
                    support = NarrationKey(
                        NarrationId.DIJ_WATCH_REACH_WHY,
                        listOf(
                            previous.current ?: state.start,
                            previous.distanceOf(previous.current ?: state.start) ?: 0,
                            state.graph.weightOf(previous.current ?: state.start, node) ?: 0,
                            state.distanceOf(node) ?: 0,
                        ),
                    ),
                ),
            )
        }

        // -- A relaxation was resolved ----------------------------------------
        val r = previous.pending
        if (r != null && state.pending == null) {
            val existing = previous.distanceOf(r.to) ?: 0
            val improved = previous.improves
            return listOf(
                PartialStep(
                    kind = if (improved) WatchStepKind.ELIMINATE else WatchStepKind.KEEP,
                    scene = scene,
                    headline = NarrationKey(
                        if (improved) NarrationId.DIJ_WATCH_UPDATE else NarrationId.DIJ_WATCH_KEEP,
                        listOf(r.to, existing, r.candidate),
                    ),
                    support = NarrationKey(
                        if (improved) {
                            NarrationId.DIJ_WATCH_UPDATE_WHY
                        } else {
                            NarrationId.DIJ_WATCH_KEEP_WHY
                        },
                        listOf(r.from, previous.distanceOf(r.from) ?: 0, r.weight, r.candidate, existing),
                    ),
                    // `5 < 8` — the comparison the learner will be asked to make,
                    // shown only once they have watched it being made.
                    comparison = ComparisonReadout(
                        r.candidate,
                        if (improved) Relation.LESS else Relation.GREATER,
                        existing,
                    ),
                ),
            )
        }

        // -- The target was settled -------------------------------------------
        if (state.target in state.processed && previous.target !in previous.processed) {
            return listOf(
                PartialStep(
                    kind = WatchStepKind.FOUND,
                    scene = scene,
                    headline = NarrationKey(
                        NarrationId.DIJ_WATCH_DONE,
                        listOf(state.target, state.distanceOf(state.target) ?: 0),
                    ),
                    support = NarrationKey(
                        NarrationId.DIJ_WATCH_DONE_WHY,
                        listOf(state.target),
                    ),
                ),
            )
        }

        // Settling an ordinary node, or stepping past one that is already settled:
        // no visible change worth a beat of its own.
        return emptyList()
    }

    override fun closing(
        state: DijkstraState,
        metrics: Metrics,
        scene: Scene,
    ): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.INSIGHT,
            scene = scene,
            headline = NarrationKey(NarrationId.DIJ_WATCH_INSIGHT),
            // Why taking the cheapest is safe — which is the whole proof, and also
            // exactly the sentence a negative edge falsifies.
            support = NarrationKey(NarrationId.DIJ_WATCH_INSIGHT_SUPPORT),
        ),
        PartialStep(
            kind = WatchStepKind.SUMMARY,
            scene = scene,
            headline = NarrationKey(
                NarrationId.DIJ_WATCH_SUMMARY,
                listOf(
                    state.pathTo(state.target).joinToString("  →  "),
                    state.distanceOf(state.target) ?: 0,
                ),
            ),
            support = NarrationKey(NarrationId.DIJ_WATCH_SUMMARY_SUPPORT),
            bullets = listOf(
                NarrationKey(NarrationId.DIJ_IDEA_1),
                NarrationKey(NarrationId.DIJ_IDEA_2),
                NarrationKey(NarrationId.DIJ_IDEA_3),
                NarrationKey(NarrationId.DIJ_IDEA_4),
                // Where it sits beside DFS and BFS, last, once the run is watched.
                NarrationKey(NarrationId.DIJ_IDEA_5),
            ),
        ),
    )
}
