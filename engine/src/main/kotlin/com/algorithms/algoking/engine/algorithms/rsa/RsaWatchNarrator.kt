package com.algorithms.algoking.engine.algorithms.rsa

import com.algorithms.algoking.engine.core.Frame
import com.algorithms.algoking.engine.core.RsaProblem
import com.algorithms.algoking.engine.core.RsaQuestion
import com.algorithms.algoking.engine.event.Metrics
import com.algorithms.algoking.engine.narration.NarrationId
import com.algorithms.algoking.engine.narration.NarrationKey
import com.algorithms.algoking.engine.scene.Scene
import com.algorithms.algoking.engine.walkthrough.PartialStep
import com.algorithms.algoking.engine.walkthrough.WatchNarrator
import com.algorithms.algoking.engine.walkthrough.WatchStepKind

/**
 * The RSA walkthrough — **use the keys, then explain them** (ADR-052).
 *
 * ### The shape
 *
 * Two acts, and the order between them is the lesson's one strong claim.
 *
 * **Act I is a story about a message.** It is handed two keys, `(3, 55)` and
 * `(27, 55)`, as things that exist and have opposite jobs; it sends `4` through the
 * public one, gets `9`, sends `9` through the private one, and gets `4` back. It
 * never says the words *prime*, *totient* or *exponent*. By the end of it a learner
 * can answer *"what is RSA doing?"* — which is the question the old script left them
 * holding until beat eleven.
 *
 * **Act II answers the question Act I leaves standing:** where did those two pairs
 * come from? Now `p, q → n → φ(n) → e → d` is the explanation of something the
 * learner has watched work, rather than five facts to hold on trust. One beat per
 * link, because there is nothing to collapse — five values, five different ideas —
 * which is why ADR-025's rule about repetition does not apply the way it does to
 * AES's ten rounds.
 *
 * **Then the caveat**, as a beat of its own rather than a footnote: what separates
 * these two-digit numbers from the RSA that protects a connection.
 *
 * ### Every beat is captioned with the picture it is given
 *
 * `WatchScriptBuilder` hands the narrator the scene projected from the state
 * **after** the transition, so a caption must describe what that scene shows.
 *
 * The judgements answered by tapping a card are the case that has to be handled
 * deliberately, and this lesson now has six of them. Every other question settles a
 * *value*, so its frame shows that value landing and the caption is about it. A card
 * question settles nothing in the picture: what it draws is the four cards, and they
 * appear on the frame where the question is **pending** rather than on the one that
 * answers it.
 *
 * So a frame with a card question pending is captioned with **both** — the headline
 * of the beat that just landed, and a support line introducing the choice now on
 * screen. That is not a compromise; it is what the frame honestly shows, and it is
 * the rule ADR-048 set (*a frame is captioned by what its own scene shows*) applied
 * to a scene that shows two things. Sacrificing the landed beat's caption the way
 * the first version of this narrator did was affordable when the beat before a card
 * question had nothing of its own to say. In this script all six of them do.
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
        val landed = caption(step.kind, problem) ?: return emptyList()

        // A card question is now on screen. Keep the landed beat's headline — it is
        // what the top of the picture shows — and replace the support with the
        // sentence that introduces the choice underneath it.
        val intro = cardIntro(next.step?.question)

        return listOf(
            PartialStep(
                kind = if (intro != null) WatchStepKind.EXAMINE else landed.kind,
                scene = scene,
                headline = landed.headline,
                support = intro ?: landed.support,
            ),
        )
    }

    /** A beat's two sentences, before a frame is chosen to carry them. */
    private data class Caption(
        val kind: WatchStepKind,
        val headline: NarrationKey,
        val support: NarrationKey?,
    )

    /**
     * What each beat says when it lands, independent of what is pending.
     *
     * Kept whole for every beat, including the six that are usually merged with a
     * card question's introduction above. A dataset that does not ask one of the card
     * questions turns its beat into a statement, and then these are what runs — so
     * they are the source of truth rather than a fallback nobody exercises.
     */
    private fun caption(kind: RsaStepKind, problem: RsaProblem): Caption? {
        fun beat(
            kind: WatchStepKind,
            headline: NarrationId,
            headlineArgs: List<Any> = emptyList(),
            support: NarrationId? = null,
            supportArgs: List<Any> = emptyList(),
        ) = Caption(
            kind = kind,
            headline = NarrationKey(headline, headlineArgs),
            support = support?.let { NarrationKey(it, supportArgs) },
        )

        return when (kind) {
            // ── Layer 1: the concept, on a message ───────────────────────────
            RsaStepKind.MESSAGE -> beat(
                WatchStepKind.SETUP,
                NarrationId.RSA_WATCH_MESSAGE, listOf(problem.plaintext),
                NarrationId.RSA_WATCH_MESSAGE_SUPPORT,
            )

            RsaStepKind.ENCRYPT_OPERATION -> beat(
                WatchStepKind.EXAMINE,
                NarrationId.RSA_WATCH_ENCRYPT_OPERATION, listOf(problem.plaintext),
                NarrationId.RSA_WATCH_ENCRYPT_OPERATION_SUPPORT,
            )

            RsaStepKind.KEY_REVEAL -> beat(
                WatchStepKind.EXAMINE,
                NarrationId.RSA_WATCH_KEY_REVEAL,
                support = NarrationId.RSA_WATCH_KEY_REVEAL_SUPPORT,
            )

            RsaStepKind.ASYMMETRIC -> beat(
                WatchStepKind.EXAMINE,
                NarrationId.RSA_WATCH_ASYMMETRIC,
                support = NarrationId.RSA_WATCH_ASYMMETRIC_SUPPORT,
            )

            RsaStepKind.ENCRYPT_KEY -> beat(
                WatchStepKind.EXAMINE,
                NarrationId.RSA_WATCH_ENCRYPT_KEY,
                support = NarrationId.RSA_WATCH_ENCRYPT_KEY_SUPPORT,
            )

            RsaStepKind.CIPHERTEXT -> beat(
                WatchStepKind.EXAMINE,
                NarrationId.RSA_WATCH_CIPHERTEXT,
                listOf(problem.illustrativeCiphertext),
                NarrationId.RSA_WATCH_CIPHERTEXT_SUPPORT,
            )

            RsaStepKind.DECRYPT_KEY -> beat(
                WatchStepKind.EXAMINE,
                NarrationId.RSA_WATCH_DECRYPT_KEY,
                support = NarrationId.RSA_WATCH_DECRYPT_KEY_SUPPORT,
            )

            RsaStepKind.RECOVERED -> beat(
                WatchStepKind.FOUND,
                NarrationId.RSA_WATCH_RECOVERED, listOf(problem.plaintext),
                NarrationId.RSA_WATCH_RECOVERED_SUPPORT,
            )

            RsaStepKind.ROUND_TRIP -> beat(
                WatchStepKind.PASS_COMPLETE,
                NarrationId.RSA_WATCH_ROUND_TRIP, listOf(problem.plaintext),
                NarrationId.RSA_WATCH_ROUND_TRIP_SUPPORT,
            )

            // ── The bridge ───────────────────────────────────────────────────
            RsaStepKind.TEXT_AS_NUMBERS -> beat(
                WatchStepKind.EXAMINE,
                NarrationId.RSA_WATCH_TEXT_AS_NUMBERS,
                support = NarrationId.RSA_WATCH_TEXT_AS_NUMBERS_SUPPORT,
            )

            // The beat that keeps the lesson honest about what follows.
            RsaStepKind.TOY_EXAMPLE -> beat(
                WatchStepKind.EXAMINE,
                NarrationId.RSA_WATCH_TOY_EXAMPLE,
                support = NarrationId.RSA_WATCH_TOY_EXAMPLE_SUPPORT,
                supportArgs = listOf(problem.message, problem.plaintext),
            )

            // ── Layer 2: the mechanism ───────────────────────────────────────
            RsaStepKind.SECRET_KEY -> beat(
                WatchStepKind.EXAMINE,
                NarrationId.RSA_WATCH_SECRET_KEY,
                support = NarrationId.RSA_WATCH_SECRET_KEY_SUPPORT,
            )

            RsaStepKind.SHAREABLE_KEY -> beat(
                WatchStepKind.EXAMINE,
                NarrationId.RSA_WATCH_SHAREABLE_KEY,
                support = NarrationId.RSA_WATCH_SHAREABLE_KEY_SUPPORT,
            )

            RsaStepKind.ENCRYPT -> beat(
                WatchStepKind.EXAMINE,
                NarrationId.RSA_WATCH_ENCRYPT,
                listOf(problem.message, problem.e, problem.modulus, problem.ciphertext),
                NarrationId.RSA_WATCH_ENCRYPT_SUPPORT,
                listOf(problem.publicKey.printed),
            )

            RsaStepKind.TOY_CIPHERTEXT -> beat(
                WatchStepKind.EXAMINE,
                NarrationId.RSA_WATCH_TOY_CIPHERTEXT,
                listOf(problem.message, problem.ciphertext),
                NarrationId.RSA_WATCH_TOY_CIPHERTEXT_SUPPORT,
            )

            RsaStepKind.DECRYPT_OPERATION -> beat(
                WatchStepKind.EXAMINE,
                NarrationId.RSA_WATCH_DECRYPT_OPERATION, listOf(problem.ciphertext),
                NarrationId.RSA_WATCH_DECRYPT_OPERATION_SUPPORT,
            )

            RsaStepKind.DECRYPT -> beat(
                WatchStepKind.FOUND,
                NarrationId.RSA_WATCH_DECRYPT,
                listOf(problem.ciphertext, problem.d, problem.modulus, problem.recovered),
                NarrationId.RSA_WATCH_DECRYPT_SUPPORT,
                listOf(problem.privateKey.printed),
            )

            // The lesson ends where it began, with every arrow now explained.
            RsaStepKind.CLOSING_FLOW -> beat(
                WatchStepKind.PASS_COMPLETE,
                NarrationId.RSA_WATCH_CLOSING_FLOW, listOf(problem.plaintext),
                NarrationId.RSA_WATCH_CLOSING_FLOW_SUPPORT,
            )

            // ── Where the keys came from ─────────────────────────────────────
            RsaStepKind.KEY_ORIGIN -> beat(
                WatchStepKind.EXAMINE,
                NarrationId.RSA_WATCH_KEY_ORIGIN,
                listOf(problem.publicKey.printed, problem.privateKey.printed),
                NarrationId.RSA_WATCH_KEY_ORIGIN_SUPPORT,
            )

            RsaStepKind.PRIMES -> beat(
                WatchStepKind.EXAMINE,
                NarrationId.RSA_WATCH_PRIMES, listOf(problem.p, problem.q),
                NarrationId.RSA_WATCH_PRIMES_SUPPORT,
            )

            RsaStepKind.MODULUS -> beat(
                WatchStepKind.EXAMINE,
                NarrationId.RSA_WATCH_MODULUS,
                listOf(problem.p, problem.q, problem.modulus),
                NarrationId.RSA_WATCH_MODULUS_SUPPORT,
            )

            RsaStepKind.TOTIENT -> beat(
                WatchStepKind.EXAMINE,
                NarrationId.RSA_WATCH_TOTIENT,
                listOf(problem.p - 1, problem.q - 1, problem.totient),
                NarrationId.RSA_WATCH_TOTIENT_SUPPORT,
            )

            RsaStepKind.PUBLIC_EXPONENT -> beat(
                WatchStepKind.EXAMINE,
                NarrationId.RSA_WATCH_PUBLIC_EXPONENT, listOf(problem.e),
                NarrationId.RSA_WATCH_PUBLIC_EXPONENT_SUPPORT,
                listOf(problem.e, problem.totient),
            )

            RsaStepKind.PRIVATE_EXPONENT -> beat(
                WatchStepKind.EXAMINE,
                NarrationId.RSA_WATCH_PRIVATE_EXPONENT, listOf(problem.d),
                NarrationId.RSA_WATCH_PRIVATE_EXPONENT_SUPPORT,
                listOf(problem.e, problem.d, problem.e * problem.d, problem.totient),
            )

            RsaStepKind.PUBLIC_KEY -> beat(
                WatchStepKind.EXAMINE,
                NarrationId.RSA_WATCH_PUBLIC_KEY, listOf(problem.publicKey.printed),
                NarrationId.RSA_WATCH_PUBLIC_KEY_SUPPORT,
            )

            RsaStepKind.PRIVATE_KEY -> beat(
                WatchStepKind.PASS_COMPLETE,
                NarrationId.RSA_WATCH_PRIVATE_KEY, listOf(problem.privateKey.printed),
                NarrationId.RSA_WATCH_PRIVATE_KEY_SUPPORT, listOf(problem.modulus),
            )

            RsaStepKind.KEY_PAIR_PURPOSE -> beat(
                WatchStepKind.EXAMINE,
                NarrationId.RSA_WATCH_KEY_PAIR_PURPOSE,
                support = NarrationId.RSA_WATCH_KEY_PAIR_PURPOSE_SUPPORT,
            )

            // The beat that keeps the lesson honest, and it gets one of its own.
            RsaStepKind.REAL_WORLD -> beat(
                WatchStepKind.EXAMINE,
                NarrationId.RSA_WATCH_REAL_WORLD,
                support = NarrationId.RSA_WATCH_REAL_WORLD_SUPPORT,
                supportArgs = listOf(problem.modulus),
            )
        }
    }

    /**
     * The line that introduces the four cards, when a card judgement is pending.
     *
     * Null for the judgements answered with a number or a pair: those settle a value,
     * so their frame is captioned by the value landing on it and needs nothing here.
     */
    private fun cardIntro(question: RsaQuestion?): NarrationKey? = when (question) {
        RsaQuestion.ASYMMETRIC -> NarrationKey(NarrationId.RSA_WATCH_ASK_ASYMMETRIC)
        RsaQuestion.SHAREABLE_KEY -> NarrationKey(NarrationId.RSA_WATCH_ASK_SHAREABLE_KEY)
        RsaQuestion.SECRET_KEY -> NarrationKey(NarrationId.RSA_WATCH_ASK_SECRET_KEY)
        RsaQuestion.ENCRYPT_OPERATION ->
            NarrationKey(NarrationId.RSA_WATCH_ASK_ENCRYPT_OPERATION)

        RsaQuestion.DECRYPT_OPERATION ->
            NarrationKey(NarrationId.RSA_WATCH_ASK_DECRYPT_OPERATION)

        RsaQuestion.ENCRYPT_KEY -> NarrationKey(NarrationId.RSA_WATCH_ASK_ENCRYPT_KEY)
        RsaQuestion.DECRYPT_KEY -> NarrationKey(NarrationId.RSA_WATCH_ASK_DECRYPT_KEY)

        RsaQuestion.KEY_PAIR_PURPOSE ->
            NarrationKey(NarrationId.RSA_WATCH_ASK_KEY_PAIR_PURPOSE)

        else -> null
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
                // In the order the lesson taught them: what it does, then how the
                // keys are made — the recap reads the way the run did (ADR-052).
                NarrationKey(NarrationId.RSA_IDEA_1),
                NarrationKey(NarrationId.RSA_IDEA_3),
                NarrationKey(NarrationId.RSA_IDEA_4),
                NarrationKey(
                    NarrationId.RSA_IDEA_2,
                    listOf(state.problem.p, state.problem.q, state.problem.modulus),
                ),
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
