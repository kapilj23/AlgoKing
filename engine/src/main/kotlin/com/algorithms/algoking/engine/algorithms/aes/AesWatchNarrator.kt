package com.algorithms.algoking.engine.algorithms.aes

import com.algorithms.algoking.engine.core.Aes
import com.algorithms.algoking.engine.core.AesStep
import com.algorithms.algoking.engine.core.AesStepKind
import com.algorithms.algoking.engine.core.AesTransformation
import com.algorithms.algoking.engine.core.Frame
import com.algorithms.algoking.engine.event.Metrics
import com.algorithms.algoking.engine.narration.NarrationId
import com.algorithms.algoking.engine.narration.NarrationKey
import com.algorithms.algoking.engine.scene.Scene
import com.algorithms.algoking.engine.walkthrough.PartialStep
import com.algorithms.algoking.engine.walkthrough.WatchNarrator
import com.algorithms.algoking.engine.walkthrough.WatchStepKind

/**
 * The AES walkthrough — one block, all the way through.
 *
 * ### The shape
 *
 * **Opening.** What AES is, and then the pipeline assembling itself one stage at a
 * time: the message, the block, the State, the key schedule, and the AddRoundKey
 * that happens before any round begins.
 *
 * **Round 1, in full.** Four beats, one per transformation, each captioned with
 * what that transformation did to the State the learner is looking at. This is the
 * model, and it is the only round narrated step by step.
 *
 * **The middle, collapsed.** Rounds 2 to `rounds - 1` are one beat. They are the
 * same four steps over again, and eight more rounds of them would be thirty-two
 * taps of the same rule — ADR-025's rule, the one that keeps Bubble Sort's later
 * passes and Counting Sort's later values to a beat each: *narrate the smallest
 * prefix that builds the model, then stop.*
 *
 * **The final round, in full.** Three beats, and the third says out loud what the
 * picture has already shown — MixColumns is not there. The whole lesson is built to
 * arrive here, so the beat that lands it is not one of the collapsed ones.
 *
 * **Closing concepts.** The variants, and that decryption runs the inverses. Both
 * are real steps of the run rather than sentences bolted onto the recap, so each
 * gets a scene of its own that changes — the variant table, and the pipeline turned
 * round — rather than a caption over an unchanged screen (ADR-020).
 *
 * ### Every beat is captioned with the picture it is given
 *
 * `WatchScriptBuilder` hands the narrator the scene projected from the state
 * **after** the transition, so a caption must describe what that scene shows rather
 * than what the frame just finished. Here they are the same thing —
 * `frame.state.currentStep` *is* the step the scene draws — which is the
 * arrangement ADR-048 had to rebuild SHA-256's narrator to reach, and it is worth
 * saying why it comes free: this lesson's questions are asked after the run rather
 * than interleaved with it, so no frame is ever showing the evidence for the next
 * one.
 *
 * Those question frames earn **no beats at all**. WATCH is the visual lesson and
 * TRY is the exercises; narrating the answers first would leave TRY asking things
 * the learner had just been told, which is recall rather than application.
 */
class AesWatchNarrator : WatchNarrator<AesState> {

    override fun opening(state: AesState, scene: Scene): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.SETUP,
            scene = scene,
            headline = NarrationKey(NarrationId.AES_WATCH_SETUP),
            support = NarrationKey(NarrationId.AES_WATCH_SETUP_SUPPORT),
        ),
    )

    override fun onFrame(
        previous: AesState,
        frame: Frame<AesState>,
        scene: Scene,
    ): List<PartialStep> {
        val next = frame.state
        // A judgement landed rather than a step of the run. WATCH does not narrate
        // those — see the class comment.
        val step = next.currentStep.takeIf { next.applied > previous.applied }
            ?: return emptyList()

        fun beat(
            kind: WatchStepKind,
            headline: NarrationKey,
            support: NarrationKey? = null,
        ) = listOf(PartialStep(kind, scene, headline, support))

        val variant = next.variant

        return when (step.kind) {
            AesStepKind.PLAINTEXT -> beat(
                WatchStepKind.EXAMINE,
                NarrationKey(NarrationId.AES_WATCH_PLAINTEXT),
                NarrationKey(NarrationId.AES_WATCH_PLAINTEXT_SUPPORT),
            )

            AesStepKind.BLOCK -> beat(
                WatchStepKind.EXAMINE,
                NarrationKey(
                    NarrationId.AES_WATCH_BLOCK,
                    listOf(Aes.BLOCK_BITS, Aes.BLOCK_BYTES),
                ),
                NarrationKey(NarrationId.AES_WATCH_BLOCK_SUPPORT),
            )

            AesStepKind.STATE -> beat(
                WatchStepKind.EXAMINE,
                NarrationKey(
                    NarrationId.AES_WATCH_STATE,
                    listOf(Aes.STATE_ROWS, Aes.STATE_COLUMNS),
                ),
                NarrationKey(
                    NarrationId.AES_WATCH_STATE_SUPPORT,
                    listOf(Aes.BLOCK_BYTES),
                ),
            )

            AesStepKind.KEY_EXPANSION -> beat(
                WatchStepKind.EXAMINE,
                NarrationKey(NarrationId.AES_WATCH_KEY_EXPANSION),
                NarrationKey(
                    NarrationId.AES_WATCH_KEY_EXPANSION_SUPPORT,
                    listOf(variant.keyBits, variant.roundKeys, variant.rounds),
                ),
            )

            AesStepKind.INITIAL_ADD_ROUND_KEY -> beat(
                WatchStepKind.EXAMINE,
                NarrationKey(NarrationId.AES_WATCH_INITIAL_ADD_ROUND_KEY),
                NarrationKey(NarrationId.AES_WATCH_INITIAL_ADD_ROUND_KEY_SUPPORT),
            )

            AesStepKind.ROUND_TRANSFORM -> roundBeat(next, step, scene)

            AesStepKind.CIPHERTEXT -> beat(
                WatchStepKind.FOUND,
                NarrationKey(
                    NarrationId.AES_WATCH_CIPHERTEXT,
                    listOf(variant.rounds),
                ),
                NarrationKey(
                    NarrationId.AES_WATCH_CIPHERTEXT_SUPPORT,
                    listOf(Aes.hex(step.after), Aes.BLOCK_BYTES),
                ),
            )

            AesStepKind.VARIANTS -> beat(
                WatchStepKind.EXAMINE,
                NarrationKey(NarrationId.AES_WATCH_VARIANTS),
                NarrationKey(
                    NarrationId.AES_WATCH_VARIANTS_SUPPORT,
                    listOf(Aes.BLOCK_BITS),
                ),
            )

            AesStepKind.DECRYPTION -> beat(
                WatchStepKind.EXAMINE,
                NarrationKey(NarrationId.AES_WATCH_DECRYPTION),
                NarrationKey(NarrationId.AES_WATCH_DECRYPTION_SUPPORT),
            )

            // The hand-over frame earns no beat: nothing happened on it, and
            // ADR-020 makes a step where nothing changed a bug rather than a beat.
            // It exists so that the beat before it is not drawn showing TRY's first
            // question — see [AesStepKind.READY].
            AesStepKind.READY -> emptyList()
        }
    }

    /**
     * One beat inside a round — or the single beat that stands for all the middle
     * ones.
     *
     * The collapse is emitted on the **first** transformation of round 2 and every
     * other frame between there and the final round is silent. That keeps the
     * walkthrough at a length a learner will finish while leaving both ends of the
     * cipher — the round that establishes the pattern and the round that breaks
     * it — narrated in full.
     */
    private fun roundBeat(
        state: AesState,
        step: AesStep,
        scene: Scene,
    ): List<PartialStep> {
        val variant = state.variant
        val round = step.round ?: return emptyList()
        val transformation = step.transformation ?: return emptyList()
        val isFinal = variant.isFinalRound(round)

        // -- The middle, as one beat -------------------------------------------
        if (!isFinal && round > 1) {
            val first = transformation == AesTransformation.NORMAL_ROUND.first()
            if (round != 2 || !first) return emptyList()
            return listOf(
                PartialStep(
                    kind = WatchStepKind.PASS_COMPLETE,
                    scene = scene,
                    headline = NarrationKey(
                        NarrationId.AES_WATCH_MIDDLE_ROUNDS,
                        listOf(2, variant.rounds - 1),
                    ),
                    support = NarrationKey(
                        NarrationId.AES_WATCH_MIDDLE_ROUNDS_SUPPORT,
                        listOf(variant.rounds - 2),
                    ),
                ),
            )
        }

        // -- Round 1 and the final round, transformation by transformation -----
        val headline = NarrationKey(
            if (isFinal) {
                NarrationId.AES_WATCH_FINAL_TRANSFORM
            } else {
                NarrationId.AES_WATCH_TRANSFORM
            },
            listOf(round, variant.rounds, transformation.label),
        )

        // The beat the lesson is built to arrive at. The picture has already shown
        // MixColumns struck through; this says why, and it is deliberately attached
        // to the final AddRoundKey rather than to a beat of its own — an omission is
        // best named at the moment the round ends without it.
        val support = if (isFinal && transformation == AesTransformation.ADD_ROUND_KEY) {
            NarrationKey(
                NarrationId.AES_WATCH_FINAL_ROUND_OMITS,
                listOf(AesTransformation.SKIPPED_IN_FINAL_ROUND.label),
            )
        } else {
            NarrationKey(
                transformationSupport(transformation),
                listOf(step.changed.size, Aes.BLOCK_BYTES),
            )
        }

        return listOf(PartialStep(WatchStepKind.EXAMINE, scene, headline, support))
    }

    /**
     * What each transformation did, said as a number read out of the run.
     *
     * "ShiftRows moved twelve of the sixteen bytes" is a fact the learner can check
     * against the marks on screen; "ShiftRows rearranges the State" is an adjective.
     * The count comes from comparing the State on both sides of the step, so a
     * dataset change can never leave the sentence stale — ADR-045's rule for
     * Fibonacci's call counts and ADR-048's for SHA-256's avalanche.
     */
    private fun transformationSupport(transformation: AesTransformation): NarrationId =
        when (transformation) {
            AesTransformation.SUB_BYTES -> NarrationId.AES_WATCH_SUB_BYTES_SUPPORT
            AesTransformation.SHIFT_ROWS -> NarrationId.AES_WATCH_SHIFT_ROWS_SUPPORT
            AesTransformation.MIX_COLUMNS -> NarrationId.AES_WATCH_MIX_COLUMNS_SUPPORT
            AesTransformation.ADD_ROUND_KEY -> NarrationId.AES_WATCH_ADD_ROUND_KEY_SUPPORT
        }

    override fun closing(
        state: AesState,
        metrics: Metrics,
        scene: Scene,
    ): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.INSIGHT,
            scene = scene,
            // The round count belongs on the headline — "the same four steps, ten
            // times over" is the sentence, and it reads as a typo without it.
            headline = NarrationKey(
                NarrationId.AES_WATCH_INSIGHT,
                listOf(state.variant.rounds),
            ),
            support = NarrationKey(NarrationId.AES_WATCH_INSIGHT_SUPPORT),
        ),
        PartialStep(
            kind = WatchStepKind.SUMMARY,
            scene = scene,
            headline = NarrationKey(NarrationId.AES_WATCH_SUMMARY),
            support = NarrationKey(NarrationId.AES_WATCH_SUMMARY_SUPPORT),
            bullets = listOf(
                NarrationKey(
                    NarrationId.AES_IDEA_1,
                    listOf(Aes.BLOCK_BITS, Aes.BLOCK_BYTES),
                ),
                NarrationKey(
                    NarrationId.AES_IDEA_2,
                    listOf(Aes.STATE_ROWS, Aes.STATE_COLUMNS),
                ),
                NarrationKey(NarrationId.AES_IDEA_3),
                NarrationKey(NarrationId.AES_IDEA_4),
                NarrationKey(NarrationId.AES_IDEA_5),
                // The two a learner must not leave without, last, where a recap
                // bullet is read rather than skipped — the placement ADR-047 chose
                // for the XOR caveat and ADR-048 for SHA-256's.
                NarrationKey(NarrationId.AES_IDEA_6),
                NarrationKey(NarrationId.AES_IDEA_7),
            ),
        ),
    )
}
