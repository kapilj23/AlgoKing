package com.algorithms.algoking.engine.algorithms.sha256

import com.algorithms.algoking.engine.core.HashQuestion
import com.algorithms.algoking.engine.core.Sha256
import com.algorithms.algoking.engine.event.MarkId
import com.algorithms.algoking.engine.event.MeterId
import com.algorithms.algoking.engine.event.VizEvent
import com.algorithms.algoking.engine.scene.Badge
import com.algorithms.algoking.engine.scene.CellState
import com.algorithms.algoking.engine.scene.HashClaim
import com.algorithms.algoking.engine.scene.HashDigest
import com.algorithms.algoking.engine.scene.HashRow
import com.algorithms.algoking.engine.scene.HashScene
import com.algorithms.algoking.engine.scene.HashStage
import com.algorithms.algoking.engine.scene.MeterReadout
import com.algorithms.algoking.engine.scene.SceneProjector

/**
 * SHA-256 presentation knowledge — ARCHITECTURE.md §7.2.
 *
 * ### What the picture has to say
 *
 * Two things, and which one is in front depends on where the lesson is:
 *
 *  - **while messages are being hashed**, the pipeline with the current message in
 *    it and the digest it produced, so "any input, 64 hex characters" is watched
 *    rather than asserted;
 *  - **while a judgement is live**, the rows that answer it. Each property this
 *    lesson teaches is a *relationship between messages*, so the projector shows
 *    the two or three rows that hold that relationship and nothing else. A screen
 *    of every row answers no question in particular.
 *
 * The rows come from [Sha256State.evidence], which finds them in the dataset rather
 * than listing them by hand — so the picture cannot end up pointing at rows that no
 * longer demonstrate the thing being asked.
 *
 * Like every other projector, the scene is read from [activeEvents] where it can
 * be: after a message is hashed the cursor has moved on, so lighting the row the
 * cursor points at would show the learner the *next* message beside the sentence
 * explaining the last one (ADR-032).
 */
class Sha256Projector : SceneProjector<Sha256State> {

    override fun project(state: Sha256State, activeEvents: List<VizEvent>): HashScene {
        // The message just hashed, if this frame hashed one. That is the row the
        // narration is about, so it is the row the picture should be lighting.
        val justHashed = activeEvents
            .filterIsInstance<VizEvent.Insert>()
            .firstOrNull()
            ?.at

        val focus = justHashed ?: (state.hashed - 1).takeIf { it >= 0 }
        val message = state.problem.messages.getOrNull(focus ?: -1)
        val digest = message?.let(Sha256::hex)
        val asking = state.allHashed && state.question != null
        val rows = if (asking) comparisonRows(state) else emptyList()

        // The single-message demonstration gives way to the rows **only when there
        // are rows**. Three of the five judgements are about a relationship between
        // messages and the comparison is their evidence; the other two — is there a
        // way back, and what comes out — are about the pipeline itself, and their
        // copy points straight at it: *"there is no key going in, and no arrow
        // coming back."* Hiding the arrow under that sentence would leave the
        // learner reading a description of something not on screen.
        val showDigest = !asking || rows.isEmpty()

        return HashScene(
            pipeline = pipeline(state, message, digest, asking && rows.isNotEmpty()),
            digest = if (!showDigest) null else digest?.let {
                HashDigest(
                    input = message,
                    inputLength = message.length,
                    hex = it,
                    groups = Sha256.groups(it),
                    bits = Sha256.BITS,
                    bytes = Sha256.BYTES,
                )
            },
            comparisons = rows,
            claims = state.question?.takeIf { state.allHashed }?.let(::claimsFor).orEmpty(),
            badge = Badge(mark = MarkId.TARGET, label = "Hash", value = Sha256.BITS)
                .copy(valueLabel = "SHA-256"),
            meters = buildList {
                if (!state.allHashed) {
                    add(
                        MeterReadout(
                            meter = MeterId.REMAINING,
                            label = "To hash",
                            value = state.remainingMessages.toLong(),
                        ),
                    )
                } else if (!state.finished) {
                    add(
                        MeterReadout(
                            meter = MeterId.REMAINING,
                            label = "Left",
                            value = state.remainingQuestions.toLong(),
                        ),
                    )
                }
            },
            // Short: the legend is one centred row, and a long label pushes the
            // last entry off the edge (DESIGN_SYSTEM.md §6.13).
            legendLabels = mapOf(
                CellState.COMPARING to "Reading",
                CellState.CANDIDATE to "Changed",
                CellState.FINALIZED to "Hashed",
                CellState.IDLE to "Waiting",
                CellState.GHOST to "Empty",
            ),
        )
    }

    /**
     * The five stages, always all five.
     *
     * The **SHA-256 box is one box on purpose** (ADR-048). Padding, the message
     * schedule and 64 compression rounds are what it stands for, and the lesson
     * says as much rather than drawing rounds it is not teaching or inventing ones
     * it would be lying about.
     */
    private fun pipeline(
        state: Sha256State,
        message: String?,
        digest: String?,
        comparing: Boolean,
    ): List<HashStage> {
        // While rows are being compared the pipeline steps back to being the
        // diagram it is: the beat is about the digests, not about a message moving
        // through, so nothing in it is lit.
        val live = !comparing && message != null

        return listOf(
            HashStage(
                label = "Input",
                detail = message?.let { "“$it” · ${it.length} characters" }
                    ?: "any text, any length",
                state = if (live) CellState.COMPARING else CellState.IDLE,
            ),
            HashStage(
                label = "Preprocess",
                detail = "padded into 512-bit blocks",
                state = if (live) CellState.FINALIZED else CellState.IDLE,
            ),
            HashStage(
                label = "SHA-256",
                // Named, not drawn. The learner is told what is inside the box and
                // told that this lesson is not about it.
                detail = "64 compression rounds",
                state = if (live) CellState.FINALIZED else CellState.IDLE,
            ),
            HashStage(
                label = "Hash",
                detail = "${Sha256.BITS} bits · ${Sha256.BYTES} bytes",
                state = if (live && digest != null) CellState.FINALIZED else CellState.IDLE,
            ),
            HashStage(
                label = "Hex",
                detail = "${Sha256.HEX_LENGTH} characters",
                state = if (live && digest != null) CellState.FINALIZED else CellState.IDLE,
            ),
        )
    }

    /**
     * The rows that answer the live question.
     *
     * The **second row is marked against the first**, which is what turns each
     * property into something visible: for the avalanche that lights 61 of 64
     * characters, and for determinism it lights none at all. A beat where nothing
     * is highlighted is exactly the right picture for "the same input gives the
     * same hash".
     */
    private fun comparisonRows(state: Sha256State): List<HashRow> {
        val evidence = state.evidence
        if (evidence.isEmpty()) return emptyList()

        val reference = state.problem.digestOf(evidence.first())

        return evidence.mapIndexed { position, index ->
            val input = state.problem.messages[index]
            val hex = state.problem.digestOf(index)
            HashRow(
                label = "“$input”",
                input = input,
                inputLength = input.length,
                hex = hex,
                groups = Sha256.groups(hex),
                state = if (position == 0) CellState.COMPARING else CellState.CANDIDATE,
                // Only the rows being read against the first carry marks, and only
                // where the question is about the digest changing. The fixed-length
                // beat is about the *length* of these strings, so colouring their
                // characters would point at the wrong thing.
                differing = when {
                    position == 0 -> emptySet()
                    state.question == HashQuestion.FIXED_LENGTH -> emptySet()
                    else -> Sha256.differingPositions(reference, hex)
                },
            )
        }
    }

    /**
     * The two statements, in the order and tones of the buttons beneath them.
     *
     * The full sentence lives here rather than on the button because a
     * `DecisionButton` is one line at `labelLarge` — the constraint Two Pointers
     * met and solved the same way (ADR-032), and the arrangement
     * `DpTableScene`'s choice strip already uses.
     *
     * Neither claim is marked as the true one. The renderer could not style the
     * right answer differently even by accident, which is the requirement
     * PRODUCT_SPEC.md §5 makes of every decision in the app.
     */
    private fun claimsFor(question: HashQuestion): List<HashClaim> = when (question) {
        HashQuestion.FIXED_LENGTH -> listOf(
            HashClaim("SHA-256 always produces a fixed-length output.", "FIXED"),
            HashClaim("SHA-256 output length depends on the input length.", "VARIES"),
        )

        HashQuestion.DETERMINISTIC -> listOf(
            HashClaim("The same input produces the same SHA-256 hash.", "SAME"),
            HashClaim("The same input produces a random hash every time.", "RANDOM"),
        )

        HashQuestion.AVALANCHE -> listOf(
            HashClaim("The hash stays exactly the same.", "SAME"),
            HashClaim("The hash changes substantially.", "DIFFERENT"),
        )

        HashQuestion.ONE_WAY -> listOf(
            HashClaim("Yes — it can be decrypted with the right key.", "YES"),
            HashClaim("No — hashing is designed as a one-way operation.", "NO"),
        )

        HashQuestion.OUTPUT_SIZE -> listOf(
            HashClaim("A variable-length encrypted message.", "A MESSAGE"),
            HashClaim("A 256-bit hash.", "256 BITS"),
        )
    }
}
