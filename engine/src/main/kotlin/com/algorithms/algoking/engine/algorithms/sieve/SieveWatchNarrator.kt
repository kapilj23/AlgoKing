package com.algorithms.algoking.engine.algorithms.sieve

import com.algorithms.algoking.engine.core.Frame
import com.algorithms.algoking.engine.event.Metrics
import com.algorithms.algoking.engine.narration.NarrationId
import com.algorithms.algoking.engine.narration.NarrationKey
import com.algorithms.algoking.engine.scene.Scene
import com.algorithms.algoking.engine.walkthrough.PartialStep
import com.algorithms.algoking.engine.walkthrough.WatchNarrator
import com.algorithms.algoking.engine.walkthrough.WatchStepKind

/**
 * The sieve walkthrough: for each prime, a beat to pick it, a beat to say where
 * crossing starts (from 3 on, where the answer is not obvious), and a beat to
 * cross out its multiples. Then the stop, and the primes.
 */
class SieveWatchNarrator : WatchNarrator<SieveState> {

    override fun opening(state: SieveState, scene: Scene): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.SETUP,
            scene = scene,
            headline = NarrationKey(NarrationId.SV_WATCH_SETUP, listOf(state.n)),
            support = NarrationKey(NarrationId.SV_WATCH_SETUP_SUPPORT),
        ),
    )

    override fun onFrame(previous: SieveState, frame: Frame<SieveState>, scene: Scene): List<PartialStep> {
        val state = frame.state
        return when {
            // -- A prime was picked -------------------------------------------
            state.primes.size > previous.primes.size && state.phase != SievePhase.DONE -> {
                val p = state.primes.last()
                listOf(
                    PartialStep(
                        kind = WatchStepKind.EXAMINE,
                        scene = scene,
                        headline = NarrationKey(NarrationId.SV_WATCH_PICK, listOf(p)),
                        support = NarrationKey(
                            when {
                                state.phase == SievePhase.FINISH -> NarrationId.SV_WATCH_PICK_STOP
                                previous.primes.isEmpty() -> NarrationId.SV_WATCH_PICK_FIRST
                                else -> NarrationId.SV_WATCH_PICK_WHY
                            },
                            listOf(p, p * p, state.n),
                        ),
                    ),
                )
            }

            // -- Where crossing starts ----------------------------------------
            previous.phase == SievePhase.START && state.phase == SievePhase.CROSS -> {
                val p = requireNotNull(state.current)
                listOf(
                    PartialStep(
                        kind = WatchStepKind.COMPARE,
                        scene = scene,
                        headline = NarrationKey(NarrationId.SV_WATCH_START, listOf(p, p * p)),
                        support = NarrationKey(NarrationId.SV_WATCH_START_WHY, listOf(p, 2 * p, p * p)),
                    ),
                )
            }

            // -- Multiples crossed out ----------------------------------------
            previous.phase == SievePhase.CROSS -> {
                val p = requireNotNull(previous.current)
                listOf(
                    PartialStep(
                        kind = WatchStepKind.ELIMINATE,
                        scene = scene,
                        headline = NarrationKey(
                            NarrationId.SV_WATCH_CROSS,
                            listOf(p, state.justCrossed.joinToString(", ")),
                        ),
                        support = NarrationKey(
                            if (p == 2) NarrationId.SV_WATCH_CROSS_TWO else NarrationId.SV_WATCH_CROSS_WHY,
                            listOf(p, state.justCrossed.size),
                        ),
                    ),
                )
            }

            // -- Done ---------------------------------------------------------
            state.phase == SievePhase.DONE && previous.phase != SievePhase.DONE -> listOf(
                PartialStep(
                    kind = WatchStepKind.FOUND,
                    scene = scene,
                    headline = NarrationKey(
                        NarrationId.SV_WATCH_DONE,
                        listOf(state.primes.size, state.n),
                    ),
                    support = NarrationKey(
                        NarrationId.SV_WATCH_DONE_WHY,
                        listOf(state.primes.joinToString(", ")),
                    ),
                ),
            )

            else -> emptyList()
        }
    }

    override fun closing(state: SieveState, metrics: Metrics, scene: Scene): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.INSIGHT,
            scene = scene,
            headline = NarrationKey(NarrationId.SV_WATCH_INSIGHT),
            support = NarrationKey(NarrationId.SV_WATCH_INSIGHT_SUPPORT),
        ),
        PartialStep(
            kind = WatchStepKind.SUMMARY,
            scene = scene,
            headline = NarrationKey(NarrationId.SV_WATCH_SUMMARY, listOf(state.primes.joinToString(", "))),
            support = NarrationKey(NarrationId.SV_WATCH_SUMMARY_SUPPORT),
            bullets = listOf(
                NarrationKey(NarrationId.SV_IDEA_1),
                NarrationKey(NarrationId.SV_IDEA_2),
                NarrationKey(NarrationId.SV_IDEA_3),
                NarrationKey(NarrationId.SV_IDEA_4),
            ),
        ),
    )
}
