package com.algorithms.algoking.engine.algorithms.xor

import com.algorithms.algoking.engine.core.Frame
import com.algorithms.algoking.engine.core.bitAt
import com.algorithms.algoking.engine.event.Metrics
import com.algorithms.algoking.engine.event.VizEvent
import com.algorithms.algoking.engine.narration.NarrationId
import com.algorithms.algoking.engine.narration.NarrationKey
import com.algorithms.algoking.engine.scene.Scene
import com.algorithms.algoking.engine.walkthrough.PartialStep
import com.algorithms.algoking.engine.walkthrough.WatchNarrator
import com.algorithms.algoking.engine.walkthrough.WatchStepKind

/**
 * The XOR Cipher walkthrough — the rule, the pass, and the pass back.
 *
 * ### The shape
 *
 * **Opening.** Two beats: the rule in one sentence, and the truth table it comes
 * from. Both rows and the table are on screen from the first frame, so the second
 * beat reads out evidence rather than describing something the learner cannot see.
 *
 * **Encrypt.** Every column in full. Four bits is short enough that collapsing any
 * of them would save nothing, and each one is a different line of the truth table
 * being used — `1 XOR 1`, `0 XOR 1`, `1 XOR 0`, `0 XOR 0` happens to cover all
 * four rows, which is worth watching once.
 *
 * **Decrypt.** The first column in full, then the rest collapsed — because by the
 * second bit the learner is no longer learning XOR, they are watching a claim come
 * true, and the claim is about the whole word rather than about each bit.
 *
 * **Closing.** The insight, then the recap that says out loud this is not secure
 * encryption.
 */
class XorWatchNarrator : WatchNarrator<XorState> {

    override fun opening(state: XorState, scene: Scene): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.SETUP,
            scene = scene,
            headline = NarrationKey(NarrationId.XOR_WATCH_SETUP),
            support = NarrationKey(
                NarrationId.XOR_WATCH_SETUP_SUPPORT,
                listOf(state.problem.plaintext, state.problem.key),
            ),
        ),
        PartialStep(
            kind = WatchStepKind.EXAMINE,
            scene = scene,
            // The table is already drawn, so this beat points at it.
            headline = NarrationKey(NarrationId.XOR_WATCH_TABLE),
            support = NarrationKey(NarrationId.XOR_WATCH_TABLE_SUPPORT),
        ),
    )

    override fun onFrame(
        previous: XorState,
        frame: Frame<XorState>,
        scene: Scene,
    ): List<PartialStep> {
        val inserted = frame.events
            .filterIsInstance<VizEvent.Insert>()
            .firstOrNull()
            ?: return emptyList()

        val at = inserted.at
        val a = bitAt(previous.input, at)
        val b = bitAt(previous.key, at)
        val result = inserted.value
        val last = at == previous.width - 1

        return when (previous.phase) {
            // -- The first pass: every column, because every column is the rule --
            XorPhase.ENCRYPT -> listOf(
                PartialStep(
                    kind = if (last) WatchStepKind.PASS_COMPLETE else WatchStepKind.EXAMINE,
                    scene = scene,
                    headline = NarrationKey(
                        NarrationId.XOR_WATCH_BIT,
                        listOf(a, b, result),
                    ),
                    support = if (last) {
                        // The whole statement, once, at the moment it is true.
                        NarrationKey(
                            NarrationId.XOR_WATCH_ENCRYPTED,
                            listOf(
                                previous.problem.plaintext,
                                previous.key,
                                frame.state.ciphertext,
                            ),
                        )
                    } else {
                        NarrationKey(
                            NarrationId.XOR_WATCH_BIT_SUPPORT,
                            listOf(if (a == b) SAME else DIFFERENT, frame.state.produced),
                        )
                    },
                ),
            )

            // -- The second pass: the claim, and then the claim coming true -----
            XorPhase.DECRYPT -> when {
                at == 0 -> listOf(
                    PartialStep(
                        kind = WatchStepKind.EXAMINE,
                        scene = scene,
                        headline = NarrationKey(
                            NarrationId.XOR_WATCH_REVERSE,
                            listOf(previous.ciphertext, previous.key),
                        ),
                        support = NarrationKey(
                            NarrationId.XOR_WATCH_REVERSE_SUPPORT,
                            listOf(a, b, result),
                        ),
                    ),
                )

                // The rest, in one beat. By now the learner is not learning XOR —
                // they are watching a claim come true, and the claim is about the
                // whole word rather than about each bit (ADR-025's rule).
                last -> listOf(
                    PartialStep(
                        kind = WatchStepKind.FOUND,
                        scene = scene,
                        headline = NarrationKey(
                            NarrationId.XOR_WATCH_RECOVERED,
                            listOf(frame.state.produced),
                        ),
                        support = NarrationKey(
                            NarrationId.XOR_WATCH_RECOVERED_SUPPORT,
                            listOf(previous.problem.plaintext),
                        ),
                    ),
                )

                else -> emptyList()
            }
        }
    }

    override fun closing(
        state: XorState,
        metrics: Metrics,
        scene: Scene,
    ): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.INSIGHT,
            scene = scene,
            headline = NarrationKey(NarrationId.XOR_WATCH_INSIGHT),
            support = NarrationKey(
                NarrationId.XOR_WATCH_INSIGHT_SUPPORT,
                listOf(state.problem.plaintext, state.ciphertext),
            ),
        ),
        PartialStep(
            kind = WatchStepKind.SUMMARY,
            scene = scene,
            headline = NarrationKey(
                NarrationId.XOR_WATCH_SUMMARY,
                listOf(state.problem.plaintext, state.ciphertext),
            ),
            support = NarrationKey(NarrationId.XOR_WATCH_SUMMARY_SUPPORT),
            bullets = listOf(
                NarrationKey(NarrationId.XOR_IDEA_1),
                NarrationKey(NarrationId.XOR_IDEA_2),
                NarrationKey(NarrationId.XOR_IDEA_3),
                // The honest caveat, last, where a recap bullet is read rather
                // than skipped. A lesson that leaves a learner thinking they have
                // seen encryption has taught them something worse than nothing.
                NarrationKey(NarrationId.XOR_IDEA_4),
            ),
        ),
    )

    private companion object {
        const val SAME = "the same"
        const val DIFFERENT = "different"
    }
}
