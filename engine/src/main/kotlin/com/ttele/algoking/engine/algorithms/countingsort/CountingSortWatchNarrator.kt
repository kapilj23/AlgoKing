package com.ttele.algoking.engine.algorithms.countingsort

import com.ttele.algoking.engine.core.Frame
import com.ttele.algoking.engine.event.Metrics
import com.ttele.algoking.engine.narration.NarrationId
import com.ttele.algoking.engine.narration.NarrationKey
import com.ttele.algoking.engine.scene.Scene
import com.ttele.algoking.engine.walkthrough.PartialStep
import com.ttele.algoking.engine.walkthrough.WatchNarrator
import com.ttele.algoking.engine.walkthrough.WatchStepKind

/**
 * The Counting Sort walkthrough.
 *
 * ### What earns a beat, and what does not
 *
 * Counting seven values and placing seven values is fourteen actions, and
 * narrating all fourteen would be the same sentence over and over — the
 * slideshow ADR-025 caps every walkthrough against. So each phase shows its
 * shape and then collapses:
 *
 *  - **the first four values are counted in full**, because the fourth is the
 *    second `2` and that is the beat that matters: a bucket going `1 → 2` is the
 *    whole reason a count is a count rather than a flag. The remaining values are
 *    one summarising beat.
 *  - **the finished table gets its own beat**, because it is the pivot of the
 *    lesson: the moment the array stops being needed at all.
 *  - **the first three placements are shown in full** — one value, then the same
 *    value twice out of one bucket — and the rest collapse.
 *
 * The empty buckets are never skipped over silently: the recap says out loud that
 * a count of zero places nothing, because "5, 6 and 7 never appeared" is the half
 * of the table a learner otherwise ignores.
 */
class CountingSortWatchNarrator : WatchNarrator<CountingSortState> {

    private var countedShown = 0
    private var placedShown = 0
    private var tableAnnounced = false

    override fun opening(state: CountingSortState, scene: Scene): List<PartialStep> {
        countedShown = 0
        placedShown = 0
        tableAnnounced = false

        return listOf(
            PartialStep(
                kind = WatchStepKind.SETUP,
                scene = scene,
                headline = NarrationKey(NarrationId.CS_WATCH_SETUP),
                support = NarrationKey(NarrationId.CS_WATCH_SETUP_SUPPORT),
            ),
            PartialStep(
                kind = WatchStepKind.EXAMINE,
                scene = scene,
                headline = NarrationKey(
                    NarrationId.CS_WATCH_RANGE,
                    listOf(state.min, state.max),
                ),
                support = NarrationKey(
                    NarrationId.CS_WATCH_RANGE_SUPPORT,
                    listOf(state.span, state.min, state.max),
                ),
            ),
            PartialStep(
                kind = WatchStepKind.EXAMINE,
                scene = scene,
                headline = NarrationKey(NarrationId.CS_WATCH_TABLE, listOf(state.span)),
                support = NarrationKey(NarrationId.CS_WATCH_TABLE_SUPPORT),
            ),
        )
    }

    override fun onFrame(
        previous: CountingSortState,
        frame: Frame<CountingSortState>,
        scene: Scene,
    ): List<PartialStep> {
        val state = frame.state

        // ── A value was counted ───────────────────────────────────────────────
        if (state.cursor > previous.cursor) {
            val value = requireNotNull(previous.currentValue)
            val before = previous.countOf(value)
            countedShown++

            // In full, up to and including the first repeat.
            if (countedShown <= DETAILED_COUNTS) {
                return listOf(
                    PartialStep(
                        kind = WatchStepKind.ADD,
                        scene = scene,
                        headline = NarrationKey(
                            NarrationId.CS_WATCH_COUNT,
                            listOf(value, before, before + 1),
                        ),
                        support = NarrationKey(
                            if (before == 0) {
                                NarrationId.CS_WATCH_COUNT_FIRST
                            } else {
                                NarrationId.CS_WATCH_COUNT_AGAIN
                            },
                            listOf(value, before + 1),
                        ),
                    ),
                )
            }

            // The rest, once, as one beat — and only when the counting is done, so
            // the table on screen is the finished one being described.
            if (state.countingComplete && !tableAnnounced) {
                tableAnnounced = true
                val rest = state.size - DETAILED_COUNTS
                return listOf(
                    PartialStep(
                        kind = WatchStepKind.ADD,
                        scene = scene,
                        headline = NarrationKey(NarrationId.CS_WATCH_COUNT_REST, listOf(rest)),
                        support = NarrationKey(NarrationId.CS_WATCH_COUNT_REST_SUPPORT),
                    ),
                    PartialStep(
                        kind = WatchStepKind.PASS_COMPLETE,
                        scene = scene,
                        headline = NarrationKey(NarrationId.CS_WATCH_COUNTED),
                        support = NarrationKey(
                            NarrationId.CS_WATCH_COUNTED_SUPPORT,
                            listOf(state.size),
                        ),
                    ),
                )
            }
            return emptyList()
        }

        // ── A value was placed ────────────────────────────────────────────────
        if (state.placed.size > previous.placed.size) {
            val value = state.placed.last()
            val left = state.remainingOf(value)
            placedShown++

            if (state.rebuildComplete) {
                return listOf(
                    PartialStep(
                        kind = WatchStepKind.SORTED,
                        scene = scene,
                        headline = NarrationKey(NarrationId.CS_WATCH_DONE),
                        support = NarrationKey(NarrationId.CS_WATCH_DONE_SUPPORT),
                    ),
                )
            }

            if (placedShown <= DETAILED_PLACEMENTS) {
                return listOf(
                    PartialStep(
                        kind = WatchStepKind.REMOVE,
                        scene = scene,
                        headline = NarrationKey(
                            NarrationId.CS_WATCH_PLACE,
                            listOf(value, previous.remainingOf(value)),
                        ),
                        support = NarrationKey(
                            if (left > 0) {
                                NarrationId.CS_WATCH_PLACE_MORE
                            } else {
                                NarrationId.CS_WATCH_PLACE_LAST
                            },
                            listOf(value, left),
                        ),
                    ),
                )
            }

            if (placedShown == DETAILED_PLACEMENTS + 1) {
                return listOf(
                    PartialStep(
                        kind = WatchStepKind.REMOVE,
                        scene = scene,
                        headline = NarrationKey(NarrationId.CS_WATCH_PLACE_REST),
                        support = NarrationKey(NarrationId.CS_WATCH_PLACE_REST_SUPPORT),
                    ),
                )
            }
            return emptyList()
        }

        return emptyList()
    }

    override fun closing(
        state: CountingSortState,
        metrics: Metrics,
        scene: Scene,
    ): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.INSIGHT,
            scene = scene,
            // The comparison count is read from the run rather than asserted, so
            // the claim on screen is the one the engine actually produced.
            headline = NarrationKey(NarrationId.CS_WATCH_INSIGHT, listOf(metrics.comparisons)),
            support = NarrationKey(NarrationId.CS_WATCH_INSIGHT_SUPPORT),
        ),
        PartialStep(
            kind = WatchStepKind.SUMMARY,
            scene = scene,
            headline = NarrationKey(
                NarrationId.CS_WATCH_SUMMARY,
                listOf(state.size, state.span),
            ),
            support = NarrationKey(NarrationId.CS_WATCH_SUMMARY_SUPPORT),
            bullets = listOf(
                NarrationKey(NarrationId.CS_IDEA_1),
                NarrationKey(NarrationId.CS_IDEA_2),
                NarrationKey(NarrationId.CS_IDEA_3),
                NarrationKey(NarrationId.CS_IDEA_4),
            ),
        ),
    )

    private companion object {
        /** Enough to reach the first repeated value, which is the beat that teaches. */
        const val DETAILED_COUNTS = 4

        /** One single, then a pair out of one bucket. */
        const val DETAILED_PLACEMENTS = 3
    }
}
