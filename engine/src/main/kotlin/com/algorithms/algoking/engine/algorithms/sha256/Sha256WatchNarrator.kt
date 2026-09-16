package com.algorithms.algoking.engine.algorithms.sha256

import com.algorithms.algoking.engine.core.Frame
import com.algorithms.algoking.engine.core.HashQuestion
import com.algorithms.algoking.engine.core.Sha256
import com.algorithms.algoking.engine.event.Metrics
import com.algorithms.algoking.engine.narration.NarrationId
import com.algorithms.algoking.engine.narration.NarrationKey
import com.algorithms.algoking.engine.scene.Scene
import com.algorithms.algoking.engine.walkthrough.PartialStep
import com.algorithms.algoking.engine.walkthrough.WatchNarrator
import com.algorithms.algoking.engine.walkthrough.WatchStepKind

/**
 * The SHA-256 walkthrough — what a hash is, then what it guarantees.
 *
 * ### The shape
 *
 * **Opening.** Two beats: what a hash function does, and the three numbers that are
 * the same number. The pipeline is on screen from the first frame, so the second
 * beat reads out a diagram the learner can already see rather than describing one.
 *
 * **Hashing.** Only the first message gets a beat of its own — `hello`, and the
 * digest every write-up of SHA-256 opens with. Six beats of "and here is another
 * digest" would be a slideshow; what the later messages are *for* is the
 * comparisons that come next, and that is where they get their beat. ADR-025's
 * rule: narrate the smallest prefix that builds the model, then stop.
 *
 * **The properties.** One beat per judgement — the same five TRY then asks. WATCH
 * narrates the answers; TRY asks them, which is the only honest difference
 * available here (ADR-045's call for Fibonacci, for the same reason: there is one
 * SHA-256, and a second dataset would be a different place to stand inside it).
 *
 * ### Every beat is captioned with the picture it is actually given
 *
 * This is the subtle part, and getting it wrong is the failure ADR-047 records for
 * the XOR lesson arriving through a different door. `WatchScriptBuilder` hands the
 * narrator the scene projected from the state **after** the transition — so the
 * frame that answers question *n* is drawn showing the evidence for question
 * *n + 1*. Captioning such a frame with the question it just settled puts the
 * sentence for fixed length beside a picture of the determinism rows, which is the
 * wrong order to think in (ADR-032).
 *
 * So a frame is captioned by `frame.state.question` — *what its own scene shows* —
 * never by what it just answered. That shifts every property beat one frame
 * earlier: the last hash frame carries the first judgement's beat, because the last
 * hash frame is where the first judgement's evidence appears, and the frame that
 * settles the last question draws no question at all and earns no beat.
 *
 * It was found by dumping the walkthrough and reading it, which is the only way
 * this class of bug is ever found — the same technique that caught Caesar's wrap
 * beat lighting the wrong letter and Fibonacci's six identical opening screens.
 *
 * **Closing.** The insight, then the recap — whose last two bullets are the two
 * things a learner must not leave without: hashing is not encryption, and SHA-256
 * on its own is not how passwords are stored.
 */
class Sha256WatchNarrator : WatchNarrator<Sha256State> {

    override fun opening(state: Sha256State, scene: Scene): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.SETUP,
            scene = scene,
            headline = NarrationKey(NarrationId.SHA_WATCH_SETUP),
            support = NarrationKey(NarrationId.SHA_WATCH_SETUP_SUPPORT),
        ),
        PartialStep(
            kind = WatchStepKind.EXAMINE,
            scene = scene,
            // The pipeline is already drawn, so this beat points at it.
            headline = NarrationKey(NarrationId.SHA_WATCH_SIZES),
            support = NarrationKey(
                NarrationId.SHA_WATCH_SIZES_SUPPORT,
                listOf(Sha256.BITS, Sha256.BYTES, Sha256.HEX_LENGTH),
            ),
        ),
    )

    override fun onFrame(
        previous: Sha256State,
        frame: Frame<Sha256State>,
        scene: Scene,
    ): List<PartialStep> {
        val next = frame.state

        // -- A message went through the pipeline, and it is still the subject --
        // Only while there is another message to come: the frame that finishes
        // hashing is already drawn as the first judgement's evidence, so it is
        // captioned below with that judgement rather than with the hash.
        if (next.hashed > previous.hashed && !next.allHashed) {
            val at = next.hashed - 1
            // The first one, in full: the example the lesson is anchored on. The
            // ones between it and the comparisons get no beat — they are not what
            // the learner is being taught, they are what the next five beats read.
            if (at != 0) return emptyList()

            return listOf(
                PartialStep(
                    kind = WatchStepKind.EXAMINE,
                    scene = scene,
                    headline = NarrationKey(
                        NarrationId.SHA_WATCH_FIRST,
                        listOf(next.problem.messages[at]),
                    ),
                    support = NarrationKey(
                        NarrationId.SHA_WATCH_FIRST_SUPPORT,
                        listOf(next.problem.digestOf(at), Sha256.HEX_LENGTH),
                    ),
                ),
            )
        }

        // -- The scene is showing a judgement ---------------------------------
        // `frame.state.question`, never `previous.question`: the caption has to
        // describe the picture this frame was actually given. The frame that
        // settles the last question shows no question, and earns no beat.
        val question = next.question ?: return emptyList()
        return listOf(
            PartialStep(
                kind = if (previous.hashed < next.hashed) {
                    // The hand-over from demonstrating to comparing.
                    WatchStepKind.PASS_COMPLETE
                } else {
                    WatchStepKind.EXAMINE
                },
                scene = scene,
                headline = NarrationKey(QuestionCopy.of(question).settled),
                support = supportFor(question, next),
            ),
        )
    }

    /**
     * The evidence for a property, stated as numbers read out of the run.
     *
     * The avalanche count in particular is **computed, never authored** — the rule
     * ADR-045 set for Fibonacci's 67 calls. "Almost all of it changes" is an
     * adjective a learner is invited to distrust; "61 of the 64 characters changed"
     * is a fact they can check against the two rows in front of them, and a dataset
     * change can never leave it stale.
     */
    private fun supportFor(question: HashQuestion, state: Sha256State): NarrationKey? =
        when (question) {
            HashQuestion.FIXED_LENGTH -> {
                val ladder = state.problem.lengthLadder
                val shortest = state.problem.messages[ladder.first()]
                val longest = state.problem.messages[ladder.last()]
                NarrationKey(
                    NarrationId.SHA_WATCH_FIXED_LENGTH_SUPPORT,
                    listOf(
                        // This beat is also the hand-over from hashing, so it says
                        // out loud that every message went through the same
                        // pipeline before it reads the two lengths off the rows.
                        state.problem.messages.size,
                        shortest.length,
                        longest.length,
                        Sha256.HEX_LENGTH,
                    ),
                )
            }

            HashQuestion.DETERMINISTIC -> {
                val pair = state.problem.repeatedPair
                val message = pair?.let { state.problem.messages[it.first] }.orEmpty()
                NarrationKey(NarrationId.SHA_WATCH_DETERMINISTIC_SUPPORT, listOf(message))
            }

            HashQuestion.AVALANCHE -> {
                val pair = state.problem.avalanchePair
                if (pair == null) {
                    null
                } else {
                    val (first, second) = pair
                    val changed = Sha256.differingPositions(
                        state.problem.digestOf(first),
                        state.problem.digestOf(second),
                    ).size
                    NarrationKey(
                        NarrationId.SHA_WATCH_AVALANCHE_SUPPORT,
                        listOf(
                            state.problem.messages[first],
                            state.problem.messages[second],
                            changed,
                            Sha256.HEX_LENGTH,
                        ),
                    )
                }
            }

            HashQuestion.ONE_WAY -> NarrationKey(NarrationId.SHA_WATCH_ONE_WAY_SUPPORT)
            HashQuestion.OUTPUT_SIZE -> NarrationKey(NarrationId.SHA_WATCH_USES_SUPPORT)
        }

    override fun closing(
        state: Sha256State,
        metrics: Metrics,
        scene: Scene,
    ): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.INSIGHT,
            scene = scene,
            headline = NarrationKey(NarrationId.SHA_WATCH_INSIGHT),
            support = NarrationKey(
                NarrationId.SHA_WATCH_INSIGHT_SUPPORT,
                listOf(Sha256.BITS),
            ),
        ),
        PartialStep(
            kind = WatchStepKind.SUMMARY,
            scene = scene,
            headline = NarrationKey(NarrationId.SHA_WATCH_SUMMARY),
            support = NarrationKey(NarrationId.SHA_WATCH_SUMMARY_SUPPORT),
            bullets = listOf(
                NarrationKey(NarrationId.SHA_IDEA_1, listOf(Sha256.BITS, Sha256.HEX_LENGTH)),
                NarrationKey(NarrationId.SHA_IDEA_2),
                NarrationKey(NarrationId.SHA_IDEA_3),
                NarrationKey(NarrationId.SHA_IDEA_4),
                // The two a learner must not leave without, last, where a recap
                // bullet is read rather than skipped — the placement ADR-047 chose
                // for the XOR lesson's caveat, and for the same reason.
                NarrationKey(NarrationId.SHA_IDEA_5),
                NarrationKey(NarrationId.SHA_IDEA_6),
            ),
        ),
    )
}
