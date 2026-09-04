package com.ttele.algoking.engine.algorithms.prefixsum

import com.ttele.algoking.engine.core.Frame
import com.ttele.algoking.engine.event.Metrics
import com.ttele.algoking.engine.event.VizEvent
import com.ttele.algoking.engine.narration.NarrationId
import com.ttele.algoking.engine.narration.NarrationKey
import com.ttele.algoking.engine.scene.Scene
import com.ttele.algoking.engine.walkthrough.PartialStep
import com.ttele.algoking.engine.walkthrough.WatchNarrator
import com.ttele.algoking.engine.walkthrough.WatchStepKind

/**
 * The Prefix Sum walkthrough, in the two phases the technique actually has.
 *
 * **Phase 1 — build.** One beat per entry, each naming the arithmetic:
 * *"prefix[2] = prefix[1] + array[1]. 2 + 4 = 6."*
 *
 * **Phase 2 — query.** The payoff. The range is stated, the two prefix cells are
 * chosen, and the subtraction is evaluated — three beats, because a learner who
 * sees only the answer has watched a magic trick.
 *
 * The insight step is where the complexity lands, and it is deliberately last:
 * O(1) per query means nothing until you have seen a query answered without
 * touching the array.
 */
class PrefixSumWatchNarrator : WatchNarrator<PrefixSumState> {

    override fun opening(
        state: PrefixSumState,
        scene: Scene,
    ): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.SETUP,
            scene = scene,
            headline = NarrationKey(NarrationId.PS_WATCH_SETUP),
            support = NarrationKey(NarrationId.PS_WATCH_SETUP_SUPPORT),
        ),
        PartialStep(
            kind = WatchStepKind.EXAMINE,
            scene = scene,
            // The leading zero is the representation, so it is stated rather than
            // asked for — and stated *before* anything is built, because every
            // later value leans on it.
            headline = NarrationKey(NarrationId.PS_WATCH_SEED),
            support = NarrationKey(NarrationId.PS_WATCH_SEED_SUPPORT),
        ),
    )

    override fun onFrame(
        previous: PrefixSumState,
        frame: Frame<PrefixSumState>,
        scene: Scene,
    ): List<PartialStep> {
        val state = frame.state
        val events = frame.events

        // -- Phase 1: one entry written ----------------------------------------
        val inserted = events.filterIsInstance<VizEvent.Insert>().firstOrNull()
        if (inserted != null) {
            val i = inserted.at
            val addend = previous.values[i - 1]
            val runningBefore = previous.prefix.last()
            val justBuilt = state.buildComplete
            return listOf(
                PartialStep(
                    kind = WatchStepKind.EXAMINE,
                    scene = scene,
                    headline = NarrationKey(
                        NarrationId.PS_WATCH_BUILD,
                        listOf(runningBefore, addend, inserted.value),
                    ),
                    support = NarrationKey(
                        if (justBuilt) {
                            NarrationId.PS_WATCH_BUILD_DONE
                        } else {
                            NarrationId.PS_WATCH_BUILD_SUPPORT
                        },
                        listOf(i, i - 1, i - 1, inserted.value),
                    ),
                ),
            )
        }

        // -- Phase 2a: the two prefix values are chosen ------------------------
        if (events.any { it is VizEvent.Region }) {
            val range = requireNotNull(state.queryRange)
            val hi = requireNotNull(state.chosenHi)
            val lo = requireNotNull(state.chosenLo)
            return listOf(
                PartialStep(
                    kind = WatchStepKind.COMPARE,
                    scene = scene,
                    headline = NarrationKey(
                        NarrationId.PS_WATCH_QUERY,
                        listOf(range.first, range.last),
                    ),
                    support = NarrationKey(
                        NarrationId.PS_WATCH_QUERY_INDICES,
                        listOf(hi, lo, range.last, range.first),
                    ),
                ),
            )
        }

        // -- Phase 2b: the subtraction ------------------------------------------
        if (events.any { it is VizEvent.Finalize }) {
            val range = requireNotNull(state.queryRange)
            val full = state.fullPrefix
            val hi = full[requireNotNull(state.chosenHi).coerceIn(0, state.size)]
            val lo = full[requireNotNull(state.chosenLo).coerceIn(0, state.size)]
            return listOf(
                PartialStep(
                    kind = WatchStepKind.FOUND,
                    scene = scene,
                    headline = NarrationKey(
                        NarrationId.PS_WATCH_ANSWER,
                        listOf(hi, lo, requireNotNull(state.answer)),
                    ),
                    // *Why* the subtraction works. Without this the learner has a
                    // formula; with it they have the idea.
                    support = NarrationKey(
                        NarrationId.PS_WATCH_ANSWER_WHY,
                        listOf(range.first, range.last),
                    ),
                ),
            )
        }

        return emptyList()
    }

    override fun closing(
        state: PrefixSumState,
        metrics: Metrics,
        scene: Scene,
    ): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.INSIGHT,
            scene = scene,
            headline = NarrationKey(NarrationId.PS_WATCH_INSIGHT),
            support = NarrationKey(
                NarrationId.PS_WATCH_INSIGHT_SUPPORT,
                listOf(state.size),
            ),
        ),
        PartialStep(
            kind = WatchStepKind.SUMMARY,
            scene = scene,
            headline = NarrationKey(NarrationId.PS_WATCH_SUMMARY),
            support = NarrationKey(NarrationId.PS_WATCH_SUMMARY_SUPPORT),
            bullets = listOf(
                NarrationKey(NarrationId.PS_IDEA_1),
                NarrationKey(NarrationId.PS_IDEA_2),
                NarrationKey(NarrationId.PS_IDEA_3),
                NarrationKey(NarrationId.PS_IDEA_4),
            ),
        ),
    )
}
