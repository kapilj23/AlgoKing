package com.algorithms.algoking.engine.algorithms.aes

import com.algorithms.algoking.engine.core.Aes
import com.algorithms.algoking.engine.core.AesProblem
import com.algorithms.algoking.engine.core.AesQuestion
import com.algorithms.algoking.engine.core.AesStep
import com.algorithms.algoking.engine.core.AesStepKind
import com.algorithms.algoking.engine.core.AesTransformation
import com.algorithms.algoking.engine.core.AesVariant
import com.algorithms.algoking.engine.core.Algorithm
import com.algorithms.algoking.engine.core.AlgorithmId
import com.algorithms.algoking.engine.core.Dataset
import com.algorithms.algoking.engine.core.Probe
import com.algorithms.algoking.engine.core.Transition
import com.algorithms.algoking.engine.decision.Action
import com.algorithms.algoking.engine.decision.ActionOption
import com.algorithms.algoking.engine.decision.Decision
import com.algorithms.algoking.engine.decision.DecisionKind
import com.algorithms.algoking.engine.event.ExamineRole
import com.algorithms.algoking.engine.event.MeterId
import com.algorithms.algoking.engine.event.Outcome
import com.algorithms.algoking.engine.event.VizEvent
import com.algorithms.algoking.engine.narration.NarrationId
import com.algorithms.algoking.engine.narration.NarrationKey

/**
 * What happens in the AES lesson.
 *
 * Two kinds of step, and the split is the same one SHA-256 makes (ADR-048):
 *
 *  - [Advance] is the **app's**. Running a round of AES is not a judgement —
 *    MixColumns is multiplication in GF(2⁸) — so PRODUCT_SPEC.md §3 gives it to the
 *    app, which performs it for real and draws what it produced;
 *  - [Answer] is the **learner's**, and every one of them is asked once the whole
 *    run is on screen, so it is settled by reading the picture rather than by
 *    recalling a claim.
 */
sealed interface AesAction : Action {

    /** The app performs the next step of the real encryption. */
    data object Advance : AesAction

    /**
     * The learner commits to an answer.
     *
     * [choice] is an option index for a question answered with buttons, and a
     * transformation's position in a normal round for one answered by tapping the
     * round. One action either way, because the difference is which control the
     * learner used and not what they decided.
     */
    data class Answer(val choice: Int) : AesAction
}

/**
 * Immutable state — the single source of truth for how far the encryption has got
 * and what the learner has settled.
 *
 * Both fields are counts of *work done*, so nothing here can disagree with itself:
 * [applied] is how many steps of the real run have happened and [answers] is every
 * judgement made so far, so its length **is** the question cursor. The same call
 * `Sha256State`, `XorState` and `CaesarState` each make — and the reason ADR-047
 * gives for deriving a phase rather than storing one.
 *
 * **No State matrix is stored.** Every byte is read back out of
 * [AesProblem.steps], which computes the real cipher. A state carrying its own copy
 * of the State would be a second source of truth for the one thing the lesson
 * draws, and could quietly disagree with the key it claims to come from.
 */
data class AesState(
    val problem: AesProblem,
    /** How many steps of the run have been applied. */
    val applied: Int,
    /** One entry per settled question, in order. Its length is the cursor. */
    val answers: List<Int>,
) {

    /** The whole run, in order. Computed from the key, never stored. */
    val steps: List<AesStep> get() = problem.steps

    /** Which variant this lesson is running. */
    val variant: AesVariant get() = problem.variant

    /** True once the encryption has finished and only judgements are left. */
    val encrypted: Boolean get() = applied >= steps.size

    /** The step just applied, or null before anything has happened. */
    val currentStep: AesStep? get() = steps.getOrNull(applied - 1)

    /** The State as it stands — the result of the last step applied. */
    val stateBytes: IntArray?
        get() = currentStep?.after

    /** Which round the run is in, or null before the rounds begin. */
    val round: Int? get() = currentStep?.round

    /** Which judgement is live, or null when there is none left. */
    val question: AesQuestion? get() = problem.questions.getOrNull(answers.size)

    /** Nothing left to encrypt and nothing left to ask. */
    val finished: Boolean get() = encrypted && answers.size >= problem.questions.size

    /** How many steps of the run are still to come. */
    val remainingSteps: Int get() = (steps.size - applied).coerceAtLeast(0)

    /** How many judgements are still to come. */
    val remainingQuestions: Int
        get() = (problem.questions.size - answers.size).coerceAtLeast(0)

    /**
     * Which transformations of a normal round the learner has already rebuilt.
     *
     * Derived by reading the answers back rather than counted separately, so it
     * cannot drift from them — the rule this file follows everywhere. It is what
     * lets the four [AesQuestion.NextTransformation] beats fill a round in one
     * after another while the picture keeps up.
     */
    val rebuiltTransformations: List<AesTransformation>
        get() = problem.questions
            .take(answers.size)
            .filterIsInstance<AesQuestion.NextTransformation>()
            .map { AesTransformation.NORMAL_ROUND[it.position] }

    /**
     * The round counts the learner has settled so far, by variant.
     *
     * The variant table reads this, so a round count appears on screen exactly when
     * it stops being a question — an answer already drawn is not a question
     * (ADR-030).
     */
    val settledRoundCounts: Map<AesVariant, Int>
        get() = problem.questions
            .take(answers.size)
            .filterIsInstance<AesQuestion.RoundCount>()
            .associate { it.variant to it.variant.rounds }
}

/**
 * AES — a lesson about a **symmetric block cipher**, and about the one shape every
 * round of it has.
 *
 * ### What it teaches
 *
 * ```
 * plaintext -> 16-byte block -> 4 x 4 State
 *                                   |
 *                            AddRoundKey (round key 0)
 *                                   |
 *   SubBytes -> ShiftRows -> MixColumns -> AddRoundKey     x (rounds - 1)
 *                                   |
 *   SubBytes -> ShiftRows ->              AddRoundKey      the final round
 *                                   |
 *                              ciphertext
 * ```
 *
 * and the thing that makes it stick: the final round leaves **MixColumns** out.
 *
 * ### Why the State on screen is real
 *
 * ADR-048 refused to draw SHA-256's compression rounds because drawing invented
 * ones would teach something false about a real algorithm. The same rule points the
 * other way here: the State changing **is** this lesson's picture, so every byte
 * drawn is the byte AES really produces. [Aes] runs the real transformations, and
 * the run is checked against FIPS-197's published worked example and against the
 * JDK's own AES.
 *
 * ### Why the decisions are judgements rather than arithmetic
 *
 * Nobody performs a MixColumns by hand, and PRODUCT_SPEC.md §3 gives the app the
 * arithmetic. So the app encrypts, and the learner is asked what the run *means* —
 * how big a block is, what the State holds, what order a round runs in, what the
 * last round leaves out, how the variants differ, and what makes the round keys.
 * Two of those are answered by **tapping the round itself** rather than by picking
 * a word, which is the gesture ADR-034 chose over a row of buttons.
 *
 * ### This is a block cipher, and that is not a protocol
 *
 * One block, one key, and no mode of operation. The recap says so, names **AES-GCM**
 * as what a real system reaches for, and says plainly not to reach for ECB. It also
 * does not call AES unbreakable: what is true is that there is no known practical
 * attack better than trying every key, and that is the sentence the copy uses.
 */
class AesEncryptionAlgorithm : Algorithm<AesState, AesAction> {

    override val id = AlgorithmId.AES

    override fun initial(dataset: Dataset): AesState = AesState(
        problem = dataset.aes ?: FALLBACK,
        applied = 0,
        answers = emptyList(),
    )

    override fun probe(state: AesState): Probe<AesAction> = when {
        state.finished -> Probe.Terminal(Outcome.Completed(true))
        // The whole encryption runs first, so that by the time a judgement is
        // asked, the evidence that answers it is already drawn.
        !state.encrypted -> Probe.Mechanical(AesAction.Advance)
        else -> Probe.Decide(questionDecision(state))
    }

    // -- The judgements -------------------------------------------------------

    private fun questionDecision(state: AesState): Decision<AesAction> {
        val question = requireNotNull(state.question)
        val copy = AesQuestionCopy.of(question, state.variant)

        return Decision(
            kind = copy.kind,
            prompt = copy.prompt,
            options = copy.options.mapIndexed { index, label ->
                ActionOption(
                    action = AesAction.Answer(index),
                    label = label,
                    // A tapped round step selects itself by position, so the option
                    // and the slot are the same number. A button carries no slot.
                    slot = index.takeIf { copy.kind == DecisionKind.CELL },
                )
            },
            correct = AesAction.Answer(copy.correct),
            focus = copy.focus,
            hint = copy.hint,
            // Point at the evidence, ask the reasoning question, then say it
            // plainly — PRODUCT_SPEC.md §5's three rungs, the last repeating rather
            // than dead-ending anyone.
            guidance = listOf(copy.retryLook, copy.retryAsk, copy.retryExplain),
            minimalFeedback = copy.retryLook,
            // Every wrong option names the misconception it is, rather than
            // restating the rule at the learner.
            whyWrong = copy.whyWrong.mapKeys { (index, _) -> AesAction.Answer(index) },
            correctFeedback = copy.correctFeedback,
            hintLadder = listOf(copy.hint, copy.retryAsk),
        )
    }

    // -- Transitions ----------------------------------------------------------

    /**
     * Applies whatever it was given, correct or not.
     *
     * `apply` takes any legal action and is not what decides which are legal
     * (ARCHITECTURE.md §4.1) — which is what makes `validate` a pure comparison. In
     * Try nothing ever calls it with a wrong answer, because a `Retry` carries no
     * action (ADR-021).
     */
    override fun apply(state: AesState, action: AesAction): Transition<AesState> =
        when (action) {
            is AesAction.Advance -> advance(state)
            is AesAction.Answer -> answer(state, action.choice)
        }

    private fun advance(state: AesState): Transition<AesState> {
        // Nothing left to run: a no-op leaving the state byte-for-byte as it was,
        // rather than an exception. An over-long action sequence is a bad caller.
        if (state.encrypted) return Transition(state, emptyList(), null, correct = false)

        val at = state.applied
        val step = state.steps[at]
        val next = state.copy(applied = at + 1)

        return Transition(
            next = next,
            events = buildList {
                // The positions this step actually changed, computed by comparing
                // the State on both sides of it. ShiftRows marks twelve and leaves
                // row 0 alone; SubBytes marks all sixteen and moves none.
                val changed = step.changed.sorted()
                if (changed.isNotEmpty()) {
                    add(VizEvent.Examine(changed, ExamineRole.INSPECTING))
                }
                add(VizEvent.Meter(MeterId.REMAINING, next.remainingSteps.toLong()))
                if (step.kind == AesStepKind.CIPHERTEXT) {
                    add(VizEvent.Finalize(0 until Aes.BLOCK_BYTES))
                }
            },
            narration = narrationFor(step, state.variant),
            // The app's own bookkeeping is never wrong: it ran the cipher.
            correct = true,
        )
    }

    private fun answer(state: AesState, choice: Int): Transition<AesState> {
        val question = state.question
        val copy = question?.let { AesQuestionCopy.of(it, state.variant) }
        if (copy == null || choice !in copy.options.indices) {
            return Transition(state, emptyList(), null, correct = false)
        }

        val correct = choice == copy.correct
        val next = state.copy(answers = state.answers + choice)

        return Transition(
            next = next,
            events = buildList {
                if (copy.focus.isNotEmpty()) {
                    add(VizEvent.Examine(copy.focus, ExamineRole.COMPARING))
                }
                add(VizEvent.Meter(MeterId.REMAINING, next.remainingQuestions.toLong()))
                if (next.finished) add(VizEvent.Terminal(Outcome.Completed(correct)))
            },
            narration = copy.correctFeedback.takeIf { correct },
            correct = correct,
        )
    }

    /** What the walkthrough says as each step of the run lands. */
    private fun narrationFor(step: AesStep, variant: AesVariant): NarrationKey? =
        when (step.kind) {
            AesStepKind.PLAINTEXT -> NarrationKey(NarrationId.AES_STEP_PLAINTEXT)
            AesStepKind.BLOCK -> NarrationKey(
                NarrationId.AES_STEP_BLOCK,
                listOf(Aes.BLOCK_BITS, Aes.BLOCK_BYTES),
            )

            AesStepKind.STATE -> NarrationKey(
                NarrationId.AES_STEP_STATE,
                listOf(Aes.STATE_ROWS, Aes.STATE_COLUMNS),
            )

            AesStepKind.KEY_EXPANSION -> NarrationKey(
                NarrationId.AES_STEP_KEY_EXPANSION,
                listOf(variant.keyBits, variant.roundKeys),
            )

            AesStepKind.INITIAL_ADD_ROUND_KEY ->
                NarrationKey(NarrationId.AES_STEP_INITIAL_ADD_ROUND_KEY)

            AesStepKind.ROUND_TRANSFORM -> {
                val transformation = requireNotNull(step.transformation)
                NarrationKey(
                    NarrationId.AES_STEP_TRANSFORM,
                    listOf(step.round ?: 0, variant.rounds, transformation.label),
                )
            }

            AesStepKind.CIPHERTEXT -> NarrationKey(
                NarrationId.AES_STEP_CIPHERTEXT,
                listOf(variant.rounds),
            )

            AesStepKind.VARIANTS -> NarrationKey(NarrationId.AES_STEP_VARIANTS)
            AesStepKind.DECRYPTION -> NarrationKey(NarrationId.AES_STEP_DECRYPTION)
            // The hand-over says nothing, because nothing happened.
            AesStepKind.READY -> null
        }

    private companion object {

        /**
         * Used only when a dataset forgets to say; every authored one says.
         *
         * The key and block are the FIPS-197 Appendix C.1 example — a published
         * test vector rather than anything that could be mistaken for a secret.
         */
        val FALLBACK = AesProblem(
            variant = AesVariant.AES_128,
            block = Aes.bytesOf("00112233445566778899aabbccddeeff"),
            key = Aes.bytesOf("000102030405060708090a0b0c0d0e0f"),
            questions = listOf(
                AesQuestion.BlockSize,
                AesQuestion.StateSize,
                AesQuestion.SkippedInFinalRound,
                AesQuestion.KeyExpansion,
            ),
        )
    }
}

/**
 * Every narration key one question needs, in one place.
 *
 * The alternative is a `when (question)` in the decision builder, another in the
 * projector and a third in the narrator — three places to forget a question when a
 * seventh is added. Here the compiler's exhaustiveness check over [AesQuestion]
 * catches it once.
 *
 * [kind] is part of the copy because it is part of *how the question is asked*: the
 * two questions about a round's shape are answered by tapping the round, and the
 * four about sizes and names are answered with buttons.
 */
internal data class AesQuestionCopy(
    val prompt: NarrationKey,
    /** The labels, in the order they are offered. Their indices are the answers. */
    val options: List<NarrationKey>,
    val correct: Int,
    val whyWrong: Map<Int, NarrationKey>,
    val hint: NarrationKey,
    val retryLook: NarrationKey,
    val retryAsk: NarrationKey,
    val retryExplain: NarrationKey,
    val correctFeedback: NarrationKey,
    val kind: DecisionKind = DecisionKind.OPTIONS,
    /** Slots to emphasise while the learner reasons. */
    val focus: List<Int> = emptyList(),
) {
    companion object {

        fun of(question: AesQuestion, variant: AesVariant): AesQuestionCopy =
            when (question) {
                AesQuestion.BlockSize -> blockSize()
                AesQuestion.StateSize -> stateSize()
                is AesQuestion.NextTransformation -> nextTransformation(question.position)
                AesQuestion.SkippedInFinalRound -> skippedInFinalRound(variant)
                is AesQuestion.RoundCount -> roundCount(question.variant)
                AesQuestion.KeyExpansion -> keyExpansion(variant)
            }

        /** A bare number on a button — the label is the answer. */
        private fun number(value: Int) =
            NarrationKey(NarrationId.AES_OPTION_NUMBER, listOf(value))

        /**
         * The four sizes a learner might reach for, and only one of them is a
         * block.
         *
         * 192 and 256 are on the table deliberately: they are AES **key** sizes,
         * and "the number in the name is the block size" is the single commonest
         * misreading of AES.
         */
        private fun blockSize() = AesQuestionCopy(
            prompt = NarrationKey(NarrationId.AES_ASK_BLOCK_SIZE),
            options = listOf(number(64), number(128), number(192), number(256)),
            correct = 1,
            whyWrong = mapOf(
                0 to NarrationKey(NarrationId.AES_WHY_BLOCK_TOO_SMALL),
                2 to NarrationKey(NarrationId.AES_WHY_BLOCK_IS_KEY_SIZE, listOf(192)),
                3 to NarrationKey(NarrationId.AES_WHY_BLOCK_IS_KEY_SIZE, listOf(256)),
            ),
            hint = NarrationKey(
                NarrationId.AES_HINT_BLOCK_SIZE,
                listOf(Aes.BLOCK_BYTES, Aes.BLOCK_BITS),
            ),
            retryLook = NarrationKey(NarrationId.AES_RETRY_LOOK_BLOCK_SIZE),
            retryAsk = NarrationKey(
                NarrationId.AES_RETRY_ASK_BLOCK_SIZE,
                listOf(Aes.BLOCK_BYTES),
            ),
            retryExplain = NarrationKey(
                NarrationId.AES_RETRY_EXPLAIN_BLOCK_SIZE,
                listOf(Aes.BLOCK_BYTES, Aes.BLOCK_BITS),
            ),
            correctFeedback = NarrationKey(
                NarrationId.AES_CORRECT_BLOCK_SIZE,
                listOf(Aes.BLOCK_BITS, Aes.BLOCK_BYTES),
            ),
            // The State is the evidence: sixteen cells, on screen, countable.
            focus = (0 until Aes.BLOCK_BYTES).toList(),
        )

        private fun stateSize() = AesQuestionCopy(
            prompt = NarrationKey(NarrationId.AES_ASK_STATE_SIZE),
            options = listOf(number(4), number(8), number(16), number(32)),
            correct = 2,
            whyWrong = mapOf(
                0 to NarrationKey(
                    NarrationId.AES_WHY_STATE_ONE_ROW,
                    listOf(Aes.STATE_ROWS),
                ),
                1 to NarrationKey(NarrationId.AES_WHY_STATE_HALF_BLOCK),
                3 to NarrationKey(NarrationId.AES_WHY_STATE_IS_KEY_SIZE),
            ),
            hint = NarrationKey(
                NarrationId.AES_HINT_STATE_SIZE,
                listOf(Aes.STATE_ROWS, Aes.STATE_COLUMNS),
            ),
            retryLook = NarrationKey(NarrationId.AES_RETRY_LOOK_STATE_SIZE),
            retryAsk = NarrationKey(
                NarrationId.AES_RETRY_ASK_STATE_SIZE,
                listOf(Aes.STATE_ROWS, Aes.STATE_COLUMNS),
            ),
            retryExplain = NarrationKey(
                NarrationId.AES_RETRY_EXPLAIN_STATE_SIZE,
                listOf(Aes.STATE_ROWS, Aes.STATE_COLUMNS, Aes.BLOCK_BYTES, Aes.BLOCK_BITS),
            ),
            correctFeedback = NarrationKey(
                NarrationId.AES_CORRECT_STATE_SIZE,
                listOf(Aes.STATE_ROWS, Aes.STATE_COLUMNS, Aes.BLOCK_BYTES, Aes.BLOCK_BITS),
            ),
            focus = (0 until Aes.BLOCK_BYTES).toList(),
        )

        /**
         * Which transformation comes next, answered by **tapping the round**.
         *
         * A row of four buttons reading SubBytes / ShiftRows / MixColumns /
         * AddRoundKey does not fit one line at `labelLarge` — "AddRoundKey" alone is
         * wider than the button it would sit in, the wall AVL hit with its four case
         * names (ADR-037) and Two Pointers with "Move RIGHT" (ADR-032). But the
         * better reason is ADR-034's: the round's steps are already drawn, and
         * pointing at the next one is what understanding the order looks like.
         * Picking its name off a list is what recognising a word looks like.
         */
        private fun nextTransformation(position: Int): AesQuestionCopy {
            val order = AesTransformation.NORMAL_ROUND
            val answer = order[position]
            return AesQuestionCopy(
                kind = DecisionKind.CELL,
                prompt = NarrationKey(
                    NarrationId.AES_ASK_NEXT_TRANSFORMATION,
                    listOf(position + 1, order.size),
                ),
                options = order.map {
                    NarrationKey(NarrationId.AES_OPTION_TRANSFORMATION, listOf(it.label))
                },
                correct = answer.ordinal,
                whyWrong = order.withIndex()
                    .filter { (index, _) -> index != answer.ordinal }
                    .associate { (index, transformation) ->
                        index to NarrationKey(
                            // Already applied, or still to come — said without
                            // naming the one that is right, so the first miss still
                            // leaves something to work out.
                            if (index < position) {
                                NarrationId.AES_WHY_TRANSFORMATION_ALREADY_DONE
                            } else {
                                NarrationId.AES_WHY_TRANSFORMATION_LATER
                            },
                            listOf(transformation.label),
                        )
                    },
                hint = NarrationKey(NarrationId.AES_HINT_NEXT_TRANSFORMATION),
                retryLook = NarrationKey(NarrationId.AES_RETRY_LOOK_NEXT_TRANSFORMATION),
                retryAsk = NarrationKey(
                    NarrationId.AES_RETRY_ASK_NEXT_TRANSFORMATION,
                    listOf(position + 1),
                ),
                retryExplain = NarrationKey(
                    NarrationId.AES_RETRY_EXPLAIN_NEXT_TRANSFORMATION,
                    listOf(position + 1, answer.label),
                ),
                correctFeedback = NarrationKey(
                    when (answer) {
                        AesTransformation.SUB_BYTES -> NarrationId.AES_CORRECT_SUB_BYTES
                        AesTransformation.SHIFT_ROWS -> NarrationId.AES_CORRECT_SHIFT_ROWS
                        AesTransformation.MIX_COLUMNS -> NarrationId.AES_CORRECT_MIX_COLUMNS
                        AesTransformation.ADD_ROUND_KEY -> NarrationId.AES_CORRECT_ADD_ROUND_KEY
                    },
                ),
                focus = order.indices.toList(),
            )
        }

        /** The omission, tapped on the round it is missing from. */
        private fun skippedInFinalRound(variant: AesVariant): AesQuestionCopy {
            val order = AesTransformation.NORMAL_ROUND
            val answer = AesTransformation.SKIPPED_IN_FINAL_ROUND
            return AesQuestionCopy(
                kind = DecisionKind.CELL,
                prompt = NarrationKey(
                    NarrationId.AES_ASK_SKIPPED,
                    listOf(variant.rounds),
                ),
                options = order.map {
                    NarrationKey(NarrationId.AES_OPTION_TRANSFORMATION, listOf(it.label))
                },
                correct = answer.ordinal,
                whyWrong = order
                    .filterNot { it == answer }
                    .associate { transformation ->
                        transformation.ordinal to NarrationKey(
                            NarrationId.AES_WHY_NOT_SKIPPED,
                            listOf(transformation.label),
                        )
                    },
                hint = NarrationKey(NarrationId.AES_HINT_SKIPPED),
                retryLook = NarrationKey(
                    NarrationId.AES_RETRY_LOOK_SKIPPED,
                    listOf(variant.rounds),
                ),
                retryAsk = NarrationKey(NarrationId.AES_RETRY_ASK_SKIPPED),
                retryExplain = NarrationKey(NarrationId.AES_RETRY_EXPLAIN_SKIPPED),
                correctFeedback = NarrationKey(NarrationId.AES_CORRECT_SKIPPED),
                focus = order.indices.toList(),
            )
        }

        /**
         * How many rounds a variant runs.
         *
         * The three counts are the options every time, so the answer is never the
         * only plausible number on the list — and the variant table on screen shows
         * key sizes with the round counts withheld until each is settled, because an
         * answer already drawn is not a question (ADR-030).
         */
        private fun roundCount(subject: AesVariant): AesQuestionCopy {
            val counts = AesVariant.entries.map { it.rounds }
            return AesQuestionCopy(
                prompt = NarrationKey(
                    NarrationId.AES_ASK_ROUND_COUNT,
                    listOf(subject.label),
                ),
                options = counts.map(::number),
                correct = counts.indexOf(subject.rounds),
                whyWrong = AesVariant.entries
                    .filterNot { it == subject }
                    .associate { other ->
                        counts.indexOf(other.rounds) to NarrationKey(
                            NarrationId.AES_WHY_ROUND_COUNT,
                            listOf(other.rounds, other.label, subject.label),
                        )
                    },
                hint = NarrationKey(NarrationId.AES_HINT_ROUND_COUNT),
                retryLook = NarrationKey(
                    NarrationId.AES_RETRY_LOOK_ROUND_COUNT,
                    listOf(subject.label, subject.keyBits),
                ),
                retryAsk = NarrationKey(NarrationId.AES_RETRY_ASK_ROUND_COUNT),
                retryExplain = NarrationKey(
                    NarrationId.AES_RETRY_EXPLAIN_ROUND_COUNT,
                    listOf(subject.label, subject.keyBits, subject.rounds),
                ),
                correctFeedback = NarrationKey(
                    NarrationId.AES_CORRECT_ROUND_COUNT,
                    listOf(subject.label, subject.rounds),
                ),
            )
        }

        /**
         * What makes the round keys.
         *
         * Two options with the statements on cards above them — SHA-256's
         * arrangement (ADR-048), because "the round keys are derived from the
         * original key" is longer than a `DecisionButton` can hold and trimming it
         * to fit would make it ambiguous.
         */
        private fun keyExpansion(variant: AesVariant) = AesQuestionCopy(
            prompt = NarrationKey(NarrationId.AES_ASK_KEY_EXPANSION),
            options = listOf(
                NarrationKey(NarrationId.AES_OPTION_KEY_EXPANSION),
                NarrationKey(NarrationId.AES_OPTION_SBOX),
            ),
            correct = 0,
            whyWrong = mapOf(
                1 to NarrationKey(NarrationId.AES_WHY_NOT_SBOX),
            ),
            hint = NarrationKey(NarrationId.AES_HINT_KEY_EXPANSION),
            retryLook = NarrationKey(NarrationId.AES_RETRY_LOOK_KEY_EXPANSION),
            retryAsk = NarrationKey(
                NarrationId.AES_RETRY_ASK_KEY_EXPANSION,
                listOf(variant.roundKeys),
            ),
            retryExplain = NarrationKey(
                NarrationId.AES_RETRY_EXPLAIN_KEY_EXPANSION,
                listOf(variant.keyBits, variant.roundKeys),
            ),
            correctFeedback = NarrationKey(
                NarrationId.AES_CORRECT_KEY_EXPANSION,
                listOf(variant.roundKeys, variant.rounds),
            ),
        )
    }
}
