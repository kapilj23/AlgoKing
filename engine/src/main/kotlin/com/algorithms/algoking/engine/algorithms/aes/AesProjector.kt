package com.algorithms.algoking.engine.algorithms.aes

import com.algorithms.algoking.engine.core.Aes
import com.algorithms.algoking.engine.core.AesQuestion
import com.algorithms.algoking.engine.core.AesStep
import com.algorithms.algoking.engine.core.AesStepKind
import com.algorithms.algoking.engine.core.AesTransformation
import com.algorithms.algoking.engine.core.AesVariant
import com.algorithms.algoking.engine.event.ExamineRole
import com.algorithms.algoking.engine.event.MarkId
import com.algorithms.algoking.engine.event.MeterId
import com.algorithms.algoking.engine.event.VizEvent
import com.algorithms.algoking.engine.scene.Badge
import com.algorithms.algoking.engine.scene.BlockCipherScene
import com.algorithms.algoking.engine.scene.Cell
import com.algorithms.algoking.engine.scene.CellState
import com.algorithms.algoking.engine.scene.CipherClaim
import com.algorithms.algoking.engine.scene.CipherStage
import com.algorithms.algoking.engine.scene.KeyScheduleView
import com.algorithms.algoking.engine.scene.MeterReadout
import com.algorithms.algoking.engine.scene.RoundStepState
import com.algorithms.algoking.engine.scene.RoundStepView
import com.algorithms.algoking.engine.scene.SceneProjector
import com.algorithms.algoking.engine.scene.VariantRow

/**
 * AES presentation knowledge — ARCHITECTURE.md §7.2.
 *
 * ### What the picture has to say
 *
 * Three things, and which is in front depends on where the lesson is:
 *
 *  - **while the cipher runs**, the State with the bytes this step changed marked,
 *    the round it is in, and the four steps that round is made of. That is the
 *    lesson: SubBytes marks all sixteen and moves nothing, ShiftRows marks twelve
 *    and leaves row 0 alone, and the final round draws MixColumns struck through;
 *  - **while a round question is live**, the same round strip with its steps
 *    offered as taps rather than narrated;
 *  - **while a size or variant question is live**, the State or the variant table,
 *    with the number being asked for withheld.
 *
 * ### Nothing is authored
 *
 * Every byte comes out of [Aes.encryptBlock], and every mark comes from comparing
 * the State on both sides of a step. There is no illustrative State anywhere in
 * this lesson, which is ADR-048's rule — *never fake the steps of a real
 * algorithm* — honoured by computing them rather than by refusing to draw them.
 *
 * Like every other projector, the scene is read from [activeEvents] where it can
 * be: after a transformation the cursor has moved on, so marking the bytes the
 * cursor points at would show the learner the *next* step's changes beside the
 * sentence explaining the last one (ADR-032).
 */
class AesProjector : SceneProjector<AesState> {

    override fun project(state: AesState, activeEvents: List<VizEvent>): BlockCipherScene {
        val step = state.currentStep
        val variant = state.variant
        val asking = state.encrypted && state.question != null
        val question = state.question.takeIf { asking }

        // Only an `Advance` emits INSPECTING, and it carries exactly the positions
        // its own transformation changed. A question frame emits COMPARING over its
        // focus, which is a different thing entirely and must not mark the State.
        val changed = activeEvents
            .filterIsInstance<VizEvent.Examine>()
            .firstOrNull { it.role == ExamineRole.INSPECTING }
            ?.indices
            ?.toSet()
            ?: step?.changed.orEmpty()

        return BlockCipherScene(
            pipeline = pipeline(state, step),
            state = stateCells(state, step, changed),
            changed = changed,
            roundLabel = roundLabel(state, step, question),
            transformation = step?.transformation?.label.takeIf { !asking },
            transformationDetail = step?.transformation?.let(::detailOf).takeIf { !asking },
            roundSteps = roundSteps(state, step, question),
            keySchedule = keySchedule(state, step, variant),
            variants = variants(state, step, question, asking),
            claims = claimsFor(question),
            badge = Badge(
                mark = MarkId.TARGET,
                label = "Cipher",
                value = variant.keyBits,
                valueLabel = variant.label,
            ),
            meters = buildList {
                if (!state.encrypted) {
                    add(
                        MeterReadout(
                            meter = MeterId.REMAINING,
                            label = "Steps",
                            value = state.remainingSteps.toLong(),
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
            // Short: the legend is one centred row, and a long label pushes the last
            // entry off the edge (DESIGN_SYSTEM.md §6.13).
            legendLabels = mapOf(
                CellState.COMPARING to "The State",
                CellState.CANDIDATE to "Changed",
                CellState.IDLE to "Unchanged",
                CellState.FINALIZED to "Ciphertext",
            ),
        )
    }

    /**
     * The five stages, always all five.
     *
     * The **Rounds stage is one stage and not ten.** Ten identical boxes would make
     * the pipeline a progress bar and push everything else off a phone; what a
     * round is made of is drawn properly, once, in the round strip below.
     */
    private fun pipeline(state: AesState, step: AesStep?): List<CipherStage> {
        val variant = state.variant
        val kind = step?.kind

        // The closing beat turns the pipeline round. Decryption is the same five
        // stages read the other way with the inverse transformations inside them,
        // and showing that is the only honest way to say "it goes back" — the
        // distinction this lesson's neighbour SHA-256 exists to deny about itself.
        if (kind == AesStepKind.DECRYPTION) {
            return listOf(
                CipherStage(
                    label = "Ciphertext",
                    detail = "${Aes.BLOCK_BYTES} bytes",
                    state = CellState.COMPARING,
                ),
                CipherStage(
                    label = "Inverse rounds",
                    detail = "${variant.rounds}, in reverse",
                    state = CellState.FINALIZED,
                ),
                CipherStage(
                    label = "State",
                    detail = "${Aes.STATE_ROWS} × ${Aes.STATE_COLUMNS}",
                    state = CellState.FINALIZED,
                ),
                CipherStage(
                    label = "Block",
                    detail = "${Aes.BLOCK_BITS} bits",
                    state = CellState.FINALIZED,
                ),
                CipherStage(
                    label = "Plaintext",
                    detail = "the same key",
                    state = CellState.FINALIZED,
                ),
            )
        }

        fun stateOf(vararg live: AesStepKind): CellState = when {
            kind in live -> CellState.COMPARING
            reached(kind, live.last()) -> CellState.FINALIZED
            else -> CellState.IDLE
        }

        return listOf(
            CipherStage(
                label = "Plaintext",
                detail = state.problem.plaintextLabel?.let { "“$it”" } ?: "any 16 bytes",
                state = stateOf(AesStepKind.PLAINTEXT),
            ),
            CipherStage(
                label = "Block",
                detail = "${Aes.BLOCK_BITS} bits · ${Aes.BLOCK_BYTES} bytes",
                state = stateOf(AesStepKind.BLOCK),
            ),
            CipherStage(
                label = "State",
                detail = "${Aes.STATE_ROWS} × ${Aes.STATE_COLUMNS}",
                state = stateOf(AesStepKind.STATE, AesStepKind.KEY_EXPANSION),
            ),
            CipherStage(
                label = "Rounds",
                detail = "${variant.rounds} of them",
                state = stateOf(
                    AesStepKind.INITIAL_ADD_ROUND_KEY,
                    AesStepKind.ROUND_TRANSFORM,
                ),
            ),
            CipherStage(
                label = "Ciphertext",
                detail = "${Aes.BLOCK_BYTES} bytes",
                state = stateOf(
                    AesStepKind.CIPHERTEXT,
                    AesStepKind.VARIANTS,
                    AesStepKind.DECRYPTION,
                    AesStepKind.READY,
                ),
            ),
        )
    }

    /** True once the run has moved past [stage]. */
    private fun reached(kind: AesStepKind?, stage: AesStepKind): Boolean =
        kind != null && kind.ordinal > stage.ordinal

    /**
     * The sixteen bytes, as cells whose slot is `row + 4 × column`.
     *
     * Ordinary [Cell]s carrying a hex label, so they are drawn by the same
     * `SceneCell` every other lesson uses — the call Caesar made for its letters
     * (ADR-046), and what stops a cryptography lesson forking the visual language.
     */
    private fun stateCells(
        state: AesState,
        step: AesStep?,
        changed: Set<Int>,
    ): List<Cell> {
        val bytes = state.stateBytes ?: state.problem.block
        return (0 until Aes.BLOCK_BYTES).map { index ->
            Cell(
                key = index,
                value = bytes[index] and 0xFF,
                slot = index,
                state = when {
                    step == null -> CellState.IDLE
                    // The run is over: this is the answer, and it is final.
                    step.kind == AesStepKind.CIPHERTEXT ||
                        step.kind == AesStepKind.VARIANTS ||
                        step.kind == AesStepKind.DECRYPTION ||
                        step.kind == AesStepKind.READY -> CellState.FINALIZED
                    // The beat where the block *becomes* the State: all sixteen at
                    // once, because the arrangement is the thing being shown.
                    step.kind == AesStepKind.STATE -> CellState.COMPARING
                    index in changed -> CellState.CANDIDATE
                    else -> CellState.IDLE
                },
                // Bytes are read as hex, always two characters, so the grid's
                // columns line up however large the values are.
                label = Aes.hexByte(bytes[index]),
            )
        }
    }

    /** `"Round 3 / 10"`, or what the run is doing when it is not in a round. */
    private fun roundLabel(
        state: AesState,
        step: AesStep?,
        question: AesQuestion?,
    ): String? {
        val variant = state.variant
        return when {
            question is AesQuestion.NextTransformation -> "A normal round"
            question is AesQuestion.SkippedInFinalRound ->
                "The final round · ${variant.rounds} of ${variant.rounds}"

            question != null -> null
            step == null -> null
            step.kind == AesStepKind.INITIAL_ADD_ROUND_KEY -> "Before round 1"
            step.kind == AesStepKind.ROUND_TRANSFORM -> {
                val round = step.round ?: return null
                val suffix = if (variant.isFinalRound(round)) " · final" else ""
                "Round $round / ${variant.rounds}$suffix"
            }

            else -> null
        }
    }

    /**
     * The steps of the round on screen.
     *
     * **The final round keeps MixColumns in its place and strikes it through.** A
     * step simply absent says nothing; a step drawn and crossed out says *this one
     * is left out*, which is the single most asked-about thing about AES and the
     * one the lesson is built to land.
     *
     * While a round question is live these same four slots are the control, so
     * "which comes next?" is answered by pointing at the round rather than by
     * picking its name off a list (ADR-034).
     */
    private fun roundSteps(
        state: AesState,
        step: AesStep?,
        question: AesQuestion?,
    ): List<RoundStepView> {
        val order = AesTransformation.NORMAL_ROUND

        // -- Being rebuilt by the learner, one tap at a time ------------------
        if (question is AesQuestion.NextTransformation) {
            val rebuilt = state.rebuiltTransformations
            return order.map { transformation ->
                RoundStepView(
                    slot = transformation.ordinal,
                    label = transformation.label,
                    state = if (transformation in rebuilt) {
                        RoundStepState.DONE
                    } else {
                        RoundStepState.SELECTABLE
                    },
                )
            }
        }

        // -- Being read, to find the one the last round leaves out -------------
        if (question is AesQuestion.SkippedInFinalRound) {
            return order.map { transformation ->
                RoundStepView(
                    slot = transformation.ordinal,
                    label = transformation.label,
                    state = RoundStepState.SELECTABLE,
                )
            }
        }

        if (question != null) return emptyList()

        // -- The inverses, on the closing beat ---------------------------------
        // Named rather than run: decryption is one recap beat, not a second
        // walkthrough, and a learner who has watched the forward round knows what
        // "the same steps backwards" means the moment they see them listed.
        if (step?.kind == AesStepKind.DECRYPTION) {
            return listOf(
                "InvShiftRows",
                "InvSubBytes",
                "AddRoundKey",
                "InvMixColumns",
            ).mapIndexed { index, label ->
                RoundStepView(
                    slot = index,
                    label = label,
                    state = RoundStepState.UPCOMING,
                )
            }
        }

        // -- Being run by the app ---------------------------------------------
        val round = step?.round ?: return emptyList()
        val live = step.transformation ?: return emptyList()
        val runs = state.variant.transformationsIn(round)

        return order.map { transformation ->
            RoundStepView(
                slot = transformation.ordinal,
                label = transformation.label,
                state = when {
                    transformation !in runs -> RoundStepState.SKIPPED
                    transformation == live -> RoundStepState.CURRENT
                    transformation.ordinal < live.ordinal -> RoundStepState.DONE
                    else -> RoundStepState.UPCOMING
                },
            )
        }
    }

    /**
     * The key and what expansion made of it.
     *
     * Drawn from the step that expands the key onwards, and never before: a round
     * key on screen before anything produced it would be the picture answering a
     * question the lesson has not asked yet (ADR-030).
     *
     * Only the first few are drawn. Eleven round keys of thirty-two hex characters
     * is a wall, and what the learner has to take away is *one key in, one per
     * round out* — not the values, which they are never asked for.
     */
    private fun keySchedule(
        state: AesState,
        step: AesStep?,
        variant: AesVariant,
    ): KeyScheduleView? {
        val kind = step?.kind ?: return null
        if (kind == AesStepKind.PLAINTEXT ||
            kind == AesStepKind.BLOCK ||
            kind == AesStepKind.STATE
        ) {
            return null
        }

        return KeyScheduleView(
            keyLabel = variant.label,
            keyHex = Aes.hex(state.problem.key),
            keyBits = variant.keyBits,
            roundKeys = state.problem.roundKeys.map(Aes::hex),
            activeRoundKey = step.roundKey,
            shown = SHOWN_ROUND_KEYS,
        )
    }

    /**
     * The three variants, side by side.
     *
     * The **block size is printed on every row and is the same on every row**,
     * which is the whole point of drawing them together: the number in the name is
     * the key size, and a learner who reads "AES-256" as a 256-bit block has
     * misread the one thing this table exists to settle.
     *
     * Round counts are withheld while they are being asked for and revealed as each
     * is settled — an answer already on screen is not a question (ADR-030).
     */
    private fun variants(
        state: AesState,
        step: AesStep?,
        question: AesQuestion?,
        asking: Boolean,
    ): List<VariantRow> {
        val subject = (question as? AesQuestion.RoundCount)?.variant
        val teaching = !asking &&
            (step?.kind == AesStepKind.VARIANTS || step?.kind == AesStepKind.DECRYPTION)

        if (subject == null && !teaching) return emptyList()

        val settled = state.settledRoundCounts
        return AesVariant.entries.map { variant ->
            VariantRow(
                label = variant.label,
                keyBits = variant.keyBits,
                // Identical on every row, on purpose.
                blockBits = Aes.BLOCK_BITS,
                rounds = when {
                    teaching -> variant.rounds
                    else -> settled[variant]
                },
                state = when {
                    variant == subject -> CellState.COMPARING
                    variant == state.variant && teaching -> CellState.CANDIDATE
                    else -> CellState.IDLE
                },
            )
        }
    }

    /**
     * The statements on offer, in the order and tones of the buttons beneath them.
     *
     * Neither is marked as the true one. The scene does not carry which is correct,
     * so the renderer could not style it differently even by accident — the
     * requirement PRODUCT_SPEC.md §5 makes of every decision in the app.
     */
    private fun claimsFor(question: AesQuestion?): List<CipherClaim> =
        when (question) {
            AesQuestion.KeyExpansion -> listOf(
                CipherClaim(
                    text = "Key Expansion derives one round key per round from the " +
                        "original key.",
                    choice = "EXPANSION",
                ),
                CipherClaim(
                    text = "The S-box looks up each round key in a substitution table.",
                    choice = "S-BOX",
                ),
            )

            else -> emptyList()
        }

    /** What a transformation does, in one clause the picture can be read against. */
    private fun detailOf(transformation: AesTransformation): String =
        when (transformation) {
            AesTransformation.SUB_BYTES ->
                "every byte swapped through the S-box · nothing moves"

            AesTransformation.SHIFT_ROWS ->
                "row r rotates left by r · row 0 stays put"

            AesTransformation.MIX_COLUMNS ->
                "each column mixed into itself · every byte now depends on four"

            AesTransformation.ADD_ROUND_KEY ->
                "the State XORed with this round's key"
        }

    private companion object {
        /**
         * How many round keys the schedule draws before eliding.
         *
         * Three is enough to show that expansion produced a *series*; the rest are
         * a wall of hex the learner is never asked about.
         */
        const val SHOWN_ROUND_KEYS = 3
    }
}
