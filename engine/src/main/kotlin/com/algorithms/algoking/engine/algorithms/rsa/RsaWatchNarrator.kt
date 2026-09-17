package com.algorithms.algoking.engine.algorithms.rsa

import com.algorithms.algoking.engine.core.Frame
import com.algorithms.algoking.engine.core.RsaQuestion
import com.algorithms.algoking.engine.event.Metrics
import com.algorithms.algoking.engine.narration.NarrationId
import com.algorithms.algoking.engine.narration.NarrationKey
import com.algorithms.algoking.engine.scene.Scene
import com.algorithms.algoking.engine.walkthrough.PartialStep
import com.algorithms.algoking.engine.walkthrough.WatchNarrator
import com.algorithms.algoking.engine.walkthrough.WatchStepKind

/**
 * The RSA walkthrough — build the keys, then use them, then give the message back.
 *
 * ### The shape
 *
 * **One beat per link in the chain.** There is nothing to collapse: `p, q → n → φ(n)
 * → e → d` is five beats and every one of them is a different idea, which is the
 * opposite of AES's ten identical rounds or Bubble Sort's later passes (ADR-025's
 * rule is about repetition, and there is none here).
 *
 * **Then the pair**, both halves together, and what each is for. **Then the round
 * trip** — encrypt, decrypt, and the message arriving back — which is the beat the
 * whole lesson is built to reach.
 *
 * **Then the caveat**, as a beat of its own rather than a footnote: what separates
 * these two-digit numbers from the RSA that protects a connection.
 *
 * ### Every beat is captioned with the picture it is given
 *
 * `WatchScriptBuilder` hands the narrator the scene projected from the state
 * **after** the transition, so a caption must describe what that scene shows. Here
 * they agree by construction, because the questions are interleaved with the chain:
 * the frame that lands `n` has `n` newly known and `φ(n)` still showing `?`, and the
 * caption is about `n`. That is what ADR-049 had to add an inert step to reach, and
 * it comes free from asking each value where it is derived rather than afterwards.
 *
 * Nothing here narrates a judgement's *answer* before TRY asks it, because in WATCH
 * the beat and the judgement are the same beat: the script settles each one and the
 * caption states what was settled, which is what a walkthrough is.
 */
class RsaWatchNarrator : WatchNarrator<RsaState> {

    override fun opening(state: RsaState, scene: Scene): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.SETUP,
            scene = scene,
            headline = NarrationKey(NarrationId.RSA_WATCH_SETUP),
            support = NarrationKey(NarrationId.RSA_WATCH_SETUP_SUPPORT),
        ),
    )

    override fun onFrame(
        previous: RsaState,
        frame: Frame<RsaState>,
        scene: Scene,
    ): List<PartialStep> {
        val next = frame.state
        val step = next.lastStep.takeIf { next.at > previous.at } ?: return emptyList()
        val problem = next.problem

        fun beat(
            kind: WatchStepKind,
            headline: NarrationKey,
            support: NarrationKey? = null,
        ) = listOf(PartialStep(kind, scene, headline, support))

        // -- The two judgements answered by tapping a card --------------------
        //
        // Every other question settles a *value*, so its frame shows that value
        // landing and the caption is about it. These two settle nothing: the picture
        // they produce is the four cards themselves, which appear on the frame where
        // the question is **pending** rather than on the one that answers it.
        //
        // So they are captioned there. That is ADR-048's rule — *a frame is
        // captioned by what its own scene shows* — and getting it wrong is what the
        // walkthrough dump caught here: the secrecy cards were drawn under the
        // round-trip sentence, and the beat that was actually about them showed
        // nothing.
        //
        // Each one needs the step before it to have no story of its own, or that
        // story would be lost. `SETUP` is already covered by `opening()`, and
        // `ROUND_TRIP` draws exactly what the `DECRYPT` beat before it drew — so
        // both are seams already, and no inert step had to be invented the way
        // ADR-049 needed one for AES.
        when (next.step?.question) {
            RsaQuestion.ASYMMETRIC -> return beat(
                WatchStepKind.EXAMINE,
                NarrationKey(NarrationId.RSA_WATCH_ASYMMETRIC),
                NarrationKey(NarrationId.RSA_WATCH_ASYMMETRIC_SUPPORT),
            )

            RsaQuestion.SECRET_KEY -> return beat(
                WatchStepKind.EXAMINE,
                NarrationKey(NarrationId.RSA_WATCH_SECRET_KEY),
                NarrationKey(NarrationId.RSA_WATCH_SECRET_KEY_SUPPORT),
            )

            else -> Unit
        }

        return when (step.kind) {
            // Said by the opening beat already; the frame that passes it is where
            // the first judgement's cards appear, and it is captioned above.
            RsaStepKind.SETUP -> emptyList()

            // Captioned on the frame that showed its cards, above.
            RsaStepKind.ASYMMETRIC, RsaStepKind.SECRET_KEY -> emptyList()

            // The seam. Its picture is the one `DECRYPT` already drew — the message
            // out and back — so it earns no beat of its own, and its frame carries
            // the secrecy cards instead.
            RsaStepKind.ROUND_TRIP -> emptyList()

            RsaStepKind.PRIMES -> beat(
                WatchStepKind.EXAMINE,
                NarrationKey(NarrationId.RSA_WATCH_PRIMES, listOf(problem.p, problem.q)),
                NarrationKey(NarrationId.RSA_WATCH_PRIMES_SUPPORT),
            )

            RsaStepKind.MODULUS -> beat(
                WatchStepKind.EXAMINE,
                NarrationKey(
                    NarrationId.RSA_WATCH_MODULUS,
                    listOf(problem.p, problem.q, problem.modulus),
                ),
                NarrationKey(NarrationId.RSA_WATCH_MODULUS_SUPPORT),
            )

            RsaStepKind.TOTIENT -> beat(
                WatchStepKind.EXAMINE,
                NarrationKey(
                    NarrationId.RSA_WATCH_TOTIENT,
                    listOf(problem.p - 1, problem.q - 1, problem.totient),
                ),
                NarrationKey(NarrationId.RSA_WATCH_TOTIENT_SUPPORT),
            )

            RsaStepKind.PUBLIC_EXPONENT -> beat(
                WatchStepKind.EXAMINE,
                NarrationKey(NarrationId.RSA_WATCH_PUBLIC_EXPONENT, listOf(problem.e)),
                NarrationKey(
                    NarrationId.RSA_WATCH_PUBLIC_EXPONENT_SUPPORT,
                    listOf(problem.e, problem.totient),
                ),
            )

            RsaStepKind.PRIVATE_EXPONENT -> beat(
                WatchStepKind.EXAMINE,
                NarrationKey(NarrationId.RSA_WATCH_PRIVATE_EXPONENT, listOf(problem.d)),
                NarrationKey(
                    NarrationId.RSA_WATCH_PRIVATE_EXPONENT_SUPPORT,
                    listOf(problem.e, problem.d, problem.e * problem.d, problem.totient),
                ),
            )

            RsaStepKind.PUBLIC_KEY -> beat(
                WatchStepKind.EXAMINE,
                NarrationKey(
                    NarrationId.RSA_WATCH_PUBLIC_KEY,
                    listOf(problem.publicKey.printed),
                ),
                NarrationKey(NarrationId.RSA_WATCH_PUBLIC_KEY_SUPPORT),
            )

            RsaStepKind.PRIVATE_KEY -> beat(
                WatchStepKind.PASS_COMPLETE,
                NarrationKey(
                    NarrationId.RSA_WATCH_PRIVATE_KEY,
                    listOf(problem.privateKey.printed),
                ),
                NarrationKey(
                    NarrationId.RSA_WATCH_PRIVATE_KEY_SUPPORT,
                    listOf(problem.modulus),
                ),
            )

            RsaStepKind.KEY_ROLES -> beat(
                WatchStepKind.EXAMINE,
                NarrationKey(NarrationId.RSA_WATCH_KEY_ROLES),
                NarrationKey(NarrationId.RSA_WATCH_KEY_ROLES_SUPPORT),
            )

            RsaStepKind.ENCRYPT -> beat(
                WatchStepKind.EXAMINE,
                NarrationKey(
                    NarrationId.RSA_WATCH_ENCRYPT,
                    listOf(problem.message, problem.e, problem.modulus, problem.ciphertext),
                ),
                NarrationKey(
                    NarrationId.RSA_WATCH_ENCRYPT_SUPPORT,
                    listOf(problem.publicKey.printed),
                ),
            )

            RsaStepKind.DECRYPT -> beat(
                WatchStepKind.FOUND,
                NarrationKey(
                    NarrationId.RSA_WATCH_DECRYPT,
                    listOf(problem.ciphertext, problem.d, problem.modulus, problem.recovered),
                ),
                NarrationKey(
                    NarrationId.RSA_WATCH_DECRYPT_SUPPORT,
                    listOf(problem.privateKey.printed),
                ),
            )

            // The beat that keeps the lesson honest, and it gets one of its own.
            RsaStepKind.REAL_WORLD -> beat(
                WatchStepKind.EXAMINE,
                NarrationKey(NarrationId.RSA_WATCH_REAL_WORLD),
                NarrationKey(
                    NarrationId.RSA_WATCH_REAL_WORLD_SUPPORT,
                    listOf(problem.modulus),
                ),
            )
        }
    }

    override fun closing(
        state: RsaState,
        metrics: Metrics,
        scene: Scene,
    ): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.INSIGHT,
            scene = scene,
            headline = NarrationKey(NarrationId.RSA_WATCH_INSIGHT),
            support = NarrationKey(NarrationId.RSA_WATCH_INSIGHT_SUPPORT),
        ),
        PartialStep(
            kind = WatchStepKind.SUMMARY,
            scene = scene,
            headline = NarrationKey(NarrationId.RSA_WATCH_SUMMARY),
            support = NarrationKey(NarrationId.RSA_WATCH_SUMMARY_SUPPORT),
            bullets = listOf(
                NarrationKey(NarrationId.RSA_IDEA_1),
                NarrationKey(
                    NarrationId.RSA_IDEA_2,
                    listOf(state.problem.p, state.problem.q, state.problem.modulus),
                ),
                NarrationKey(NarrationId.RSA_IDEA_3),
                NarrationKey(NarrationId.RSA_IDEA_4),
                NarrationKey(NarrationId.RSA_IDEA_5),
                // The two a learner must not leave without, last, where a recap
                // bullet is read rather than skipped — the placement ADR-047 chose
                // for XOR's caveat, ADR-048 for SHA-256's and ADR-049 for AES's.
                NarrationKey(NarrationId.RSA_IDEA_6),
                NarrationKey(NarrationId.RSA_IDEA_7),
            ),
        ),
    )
}
