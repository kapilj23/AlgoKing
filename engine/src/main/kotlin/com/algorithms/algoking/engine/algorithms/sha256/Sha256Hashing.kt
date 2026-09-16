package com.algorithms.algoking.engine.algorithms.sha256

import com.algorithms.algoking.engine.core.Algorithm
import com.algorithms.algoking.engine.core.AlgorithmId
import com.algorithms.algoking.engine.core.Dataset
import com.algorithms.algoking.engine.core.HashProblem
import com.algorithms.algoking.engine.core.HashQuestion
import com.algorithms.algoking.engine.core.Probe
import com.algorithms.algoking.engine.core.Sha256
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
 * What happens in the SHA-256 lesson.
 *
 * Two kinds of step, and the split is the whole design:
 *
 *  - [Hash] is the **app's**, because computing a SHA-256 digest is not a
 *    judgement. PRODUCT_SPEC.md §3 gives the app the arithmetic and the learner the
 *    decision, and 64 compression rounds over eight working variables is the most
 *    emphatic case of arithmetic in the library;
 *  - [Answer] is the **learner's**, and it is asked only once every digest is on
 *    screen — so it is answered by reading the evidence rather than by recalling a
 *    claim.
 */
sealed interface HashAction : Action {

    /** The app hashes the next message and reveals its digest. */
    data object Hash : HashAction

    /** The learner commits to one of the two statements on offer. */
    data class Answer(val choice: Int) : HashAction
}

/**
 * Immutable state — the single source of truth for what has been hashed, what has
 * been answered, which question is live and what its expected answer is.
 *
 * Both fields are counts of *work done*, so nothing here can disagree with itself:
 * [hashed] is how many messages have gone through the pipeline and [answers] is
 * every judgement made so far, so its length **is** the question cursor. The same
 * call `XorState.produced`, `PrefixSumState` and `CaesarState` each make — and the
 * reason ADR-047 gives for deriving a phase rather than storing one.
 *
 * Every digest is **computed from [problem], never stored**. A state carrying its
 * own copy of a digest is a second source of truth for the one value the whole
 * lesson is about.
 */
data class Sha256State(
    val problem: HashProblem,
    /** How many of the problem's messages have been hashed so far. */
    val hashed: Int,
    /** One entry per settled question, in order. Its length is the cursor. */
    val answers: List<Int>,
) {

    /** True once every message has gone through the pipeline. */
    val allHashed: Boolean get() = hashed >= problem.messages.size

    /** The message just hashed, or null before anything has been. */
    val currentMessage: String? get() = problem.messages.getOrNull(hashed - 1)

    /** Its digest, or null before anything has been hashed. */
    val currentDigest: String? get() = currentMessage?.let(Sha256::hex)

    /** Digests produced so far, in message order. */
    val digests: List<String> get() = (0 until hashed).map { problem.digestOf(it) }

    /** Which judgement is live, or null when there is none left. */
    val question: HashQuestion? get() = problem.questions.getOrNull(answers.size)

    /** Nothing left to hash and nothing left to ask. */
    val finished: Boolean get() = allHashed && answers.size >= problem.questions.size

    /** How many messages are still waiting for the pipeline. */
    val remainingMessages: Int get() = (problem.messages.size - hashed).coerceAtLeast(0)

    /** How many judgements are still to come. */
    val remainingQuestions: Int
        get() = (problem.questions.size - answers.size).coerceAtLeast(0)

    /**
     * The evidence for the live question, as message positions.
     *
     * **Derived from the dataset, never authored.** Each question is about a
     * relationship between messages, and the dataset either contains that
     * relationship or it does not — so the rows are found by looking rather than
     * listed by hand, and a dataset that lost its avalanche pair cannot leave the
     * beat quietly pointing at the wrong rows.
     */
    val evidence: List<Int>
        get() = when (question) {
            // Three different input lengths, one output length. The whole claim.
            HashQuestion.FIXED_LENGTH -> problem.lengthLadder
            // The same message, hashed twice, as two independent runs.
            HashQuestion.DETERMINISTIC ->
                problem.repeatedPair?.let { listOf(it.first, it.second) }.orEmpty()
            // One character apart, and the digests nothing like each other.
            HashQuestion.AVALANCHE ->
                problem.avalanchePair?.let { listOf(it.first, it.second) }.orEmpty()
            // Neither is about a comparison: the pipeline is the evidence.
            HashQuestion.ONE_WAY, HashQuestion.OUTPUT_SIZE -> emptyList()
            null -> emptyList()
        }.filter { it < hashed }
}

/**
 * SHA-256 — a lesson about **hashing**, which is not encryption.
 *
 * ### What it teaches
 *
 * ```
 * any input, any length
 *         |
 *      SHA-256
 *         |
 * 256 bits = 32 bytes = 64 hexadecimal characters, always
 * ```
 *
 * and three properties of that arrow: the same input always gives the same digest,
 * one changed character changes almost all of it, and there is no way back.
 *
 * ### Why the middle box stays a box
 *
 * SHA-256 really does pad the message, build a 64-entry schedule and run 64
 * compression rounds over eight working variables. None of it is drawn, and none of
 * it is faked (ADR-048). A beginner lesson that animated invented rounds would be
 * teaching something false about a real algorithm, which is worse than teaching
 * less; the box stands for the real thing and the copy says so.
 *
 * ### Why the decisions are concept judgements
 *
 * Every other lesson in the library asks the learner to execute a step. Nobody
 * executes a step of SHA-256 by hand, and asking them to would be a gesture over
 * arithmetic they cannot check. So the judgements are about the *properties* — and
 * they are asked only after every digest is on screen, so each one is answered by
 * reading the picture. That is the same standard the XOR lesson's truth table
 * holds: the copy never asks a learner to recall something it is showing them.
 *
 * ### This is not a way to store passwords
 *
 * SHA-256 is fast by design, which is exactly wrong for a password. The lesson says
 * so and names Argon2, bcrypt and scrypt, because a learner who leaves thinking
 * otherwise has been actively misled.
 */
class Sha256HashingAlgorithm : Algorithm<Sha256State, HashAction> {

    override val id = AlgorithmId.SHA_256

    override fun initial(dataset: Dataset): Sha256State = Sha256State(
        problem = dataset.hash ?: FALLBACK,
        hashed = 0,
        answers = emptyList(),
    )

    override fun probe(state: Sha256State): Probe<HashAction> = when {
        state.finished -> Probe.Terminal(Outcome.Completed(true))
        // Every message goes through the pipeline first, so that by the time a
        // judgement is asked, the evidence that answers it is already drawn.
        !state.allHashed -> Probe.Mechanical(HashAction.Hash)
        else -> Probe.Decide(questionDecision(state))
    }

    // -- The judgement --------------------------------------------------------

    private fun questionDecision(state: Sha256State): Decision<HashAction> {
        val question = requireNotNull(state.question)
        val correct = CORRECT_CHOICE.getValue(question)
        val wrong = 1 - correct
        val copy = QuestionCopy.of(question)

        return Decision(
            kind = DecisionKind.OPTIONS,
            prompt = NarrationKey(copy.prompt),
            // Both options are always offered, always in the same two seats. An
            // option that appeared only when it was correct would answer the
            // question the beat exists to ask (ADR-032), and a row whose contents
            // moved would let a learner read the answer off the row.
            options = listOf(0, 1).map { index ->
                ActionOption<HashAction>(
                    action = HashAction.Answer(index),
                    label = NarrationKey(copy.optionLabels[index]),
                )
            },
            correct = HashAction.Answer(correct),
            focus = state.evidence,
            hint = NarrationKey(copy.hint),
            // Point at the evidence, ask the reasoning question, then say it
            // plainly — PRODUCT_SPEC.md §5's three rungs, and the last one repeats
            // rather than dead-ending anyone.
            guidance = listOf(
                NarrationKey(copy.retryLook),
                NarrationKey(copy.retryAsk),
                NarrationKey(copy.retryExplain),
            ),
            minimalFeedback = NarrationKey(copy.retryLook),
            // With two options there is exactly one wrong answer, so the feedback
            // can name the misconception it is rather than restate the rule. That
            // is where this lesson's whole teaching budget goes (ADR-047's call for
            // the same reason).
            whyWrong = mapOf(
                HashAction.Answer(wrong) to NarrationKey(copy.whyWrong),
            ),
            correctFeedback = NarrationKey(copy.correct),
            hintLadder = listOf(
                NarrationKey(copy.hint),
                NarrationKey(copy.retryAsk),
            ),
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
    override fun apply(state: Sha256State, action: HashAction): Transition<Sha256State> =
        when (action) {
            is HashAction.Hash -> hashNext(state)
            is HashAction.Answer -> answer(state, action.choice)
        }

    private fun hashNext(state: Sha256State): Transition<Sha256State> {
        // Nothing left to hash: a no-op leaving the state byte-for-byte as it was,
        // rather than an exception. An over-long action sequence is a bad caller.
        if (state.allHashed) return Transition(state, emptyList(), null, correct = false)

        val at = state.hashed
        val message = state.problem.messages[at]
        val digest = state.problem.digestOf(at)
        val next = state.copy(hashed = at + 1)

        return Transition(
            next = next,
            events = buildList {
                add(VizEvent.Examine(listOf(at), ExamineRole.INSPECTING))
                // A digest arrived. `Insert` carries an Int, and a digest is 64 hex
                // characters — so the value is the message's *length*, which is the
                // thing that varied and the thing the fixed-length beat is about.
                // The digest itself is read from the state, where it belongs.
                add(VizEvent.Insert(message.length, at))
                add(VizEvent.Meter(MeterId.REMAINING, next.remainingMessages.toLong()))
                if (next.allHashed) add(VizEvent.Finalize(0 until state.problem.messages.size))
            },
            narration = NarrationKey(
                NarrationId.SHA_HASHED,
                listOf(message, digest.take(Sha256.GROUP_SIZE)),
            ),
            // The app's own bookkeeping is never wrong: it computed the digest.
            correct = true,
        )
    }

    private fun answer(state: Sha256State, choice: Int): Transition<Sha256State> {
        val question = state.question
        if (question == null || choice !in 0..1) {
            return Transition(state, emptyList(), null, correct = false)
        }

        val correct = choice == CORRECT_CHOICE.getValue(question)
        val next = state.copy(answers = state.answers + choice)

        return Transition(
            next = next,
            events = buildList {
                add(VizEvent.Examine(state.evidence, ExamineRole.COMPARING))
                add(VizEvent.Meter(MeterId.REMAINING, next.remainingQuestions.toLong()))
                if (next.finished) add(VizEvent.Terminal(Outcome.Completed(correct)))
            },
            narration = NarrationKey(QuestionCopy.of(question).settled),
            correct = correct,
        )
    }

    private companion object {

        /**
         * Which seat holds the true statement, per question.
         *
         * Deliberately **not** all the same seat: a learner who noticed that the
         * first button was always right would finish the stage without reading
         * anything, which is the failure PRODUCT_SPEC.md §5 exists to prevent. A
         * test asserts both seats are used.
         */
        val CORRECT_CHOICE: Map<HashQuestion, Int> = mapOf(
            HashQuestion.FIXED_LENGTH to 0,
            HashQuestion.DETERMINISTIC to 0,
            HashQuestion.AVALANCHE to 1,
            HashQuestion.ONE_WAY to 1,
            HashQuestion.OUTPUT_SIZE to 1,
        )

        /** Used only when a dataset forgets to say; every authored one says. */
        val FALLBACK = HashProblem(
            messages = listOf("hello", "Hello", "hello"),
            questions = HashQuestion.entries.toList(),
        )
    }
}

/**
 * Every narration key one question needs, in one place.
 *
 * The alternative is five `when (question)` blocks scattered through the decision
 * builder, which is five places to forget a question when a sixth is added. Here
 * the compiler's exhaustiveness check over [HashQuestion] catches it once.
 */
internal data class QuestionCopy(
    val prompt: NarrationId,
    val optionLabels: List<NarrationId>,
    val hint: NarrationId,
    val retryLook: NarrationId,
    val retryAsk: NarrationId,
    val retryExplain: NarrationId,
    val whyWrong: NarrationId,
    val correct: NarrationId,
    /** Said on the frame the answer lands, and what WATCH captions that beat with. */
    val settled: NarrationId,
) {
    companion object {
        fun of(question: HashQuestion): QuestionCopy = when (question) {
            HashQuestion.FIXED_LENGTH -> QuestionCopy(
                prompt = NarrationId.SHA_ASK_FIXED_LENGTH,
                optionLabels = listOf(
                    NarrationId.SHA_OPTION_FIXED,
                    NarrationId.SHA_OPTION_VARIES,
                ),
                hint = NarrationId.SHA_HINT_FIXED_LENGTH,
                retryLook = NarrationId.SHA_RETRY_LOOK_FIXED_LENGTH,
                retryAsk = NarrationId.SHA_RETRY_ASK_FIXED_LENGTH,
                retryExplain = NarrationId.SHA_RETRY_EXPLAIN_FIXED_LENGTH,
                whyWrong = NarrationId.SHA_WHY_NOT_VARIES,
                correct = NarrationId.SHA_CORRECT_FIXED_LENGTH,
                settled = NarrationId.SHA_SETTLED_FIXED_LENGTH,
            )

            HashQuestion.DETERMINISTIC -> QuestionCopy(
                prompt = NarrationId.SHA_ASK_DETERMINISTIC,
                optionLabels = listOf(
                    NarrationId.SHA_OPTION_SAME,
                    NarrationId.SHA_OPTION_RANDOM,
                ),
                hint = NarrationId.SHA_HINT_DETERMINISTIC,
                retryLook = NarrationId.SHA_RETRY_LOOK_DETERMINISTIC,
                retryAsk = NarrationId.SHA_RETRY_ASK_DETERMINISTIC,
                retryExplain = NarrationId.SHA_RETRY_EXPLAIN_DETERMINISTIC,
                whyWrong = NarrationId.SHA_WHY_NOT_RANDOM,
                correct = NarrationId.SHA_CORRECT_DETERMINISTIC,
                settled = NarrationId.SHA_SETTLED_DETERMINISTIC,
            )

            HashQuestion.AVALANCHE -> QuestionCopy(
                prompt = NarrationId.SHA_ASK_AVALANCHE,
                optionLabels = listOf(
                    NarrationId.SHA_OPTION_UNCHANGED,
                    NarrationId.SHA_OPTION_DIFFERENT,
                ),
                hint = NarrationId.SHA_HINT_AVALANCHE,
                retryLook = NarrationId.SHA_RETRY_LOOK_AVALANCHE,
                retryAsk = NarrationId.SHA_RETRY_ASK_AVALANCHE,
                retryExplain = NarrationId.SHA_RETRY_EXPLAIN_AVALANCHE,
                whyWrong = NarrationId.SHA_WHY_NOT_UNCHANGED,
                correct = NarrationId.SHA_CORRECT_AVALANCHE,
                settled = NarrationId.SHA_SETTLED_AVALANCHE,
            )

            HashQuestion.ONE_WAY -> QuestionCopy(
                prompt = NarrationId.SHA_ASK_ONE_WAY,
                optionLabels = listOf(
                    NarrationId.SHA_OPTION_YES_KEY,
                    NarrationId.SHA_OPTION_NO_ONE_WAY,
                ),
                hint = NarrationId.SHA_HINT_ONE_WAY,
                retryLook = NarrationId.SHA_RETRY_LOOK_ONE_WAY,
                retryAsk = NarrationId.SHA_RETRY_ASK_ONE_WAY,
                retryExplain = NarrationId.SHA_RETRY_EXPLAIN_ONE_WAY,
                whyWrong = NarrationId.SHA_WHY_NOT_DECRYPTABLE,
                correct = NarrationId.SHA_CORRECT_ONE_WAY,
                settled = NarrationId.SHA_SETTLED_ONE_WAY,
            )

            HashQuestion.OUTPUT_SIZE -> QuestionCopy(
                prompt = NarrationId.SHA_ASK_OUTPUT_SIZE,
                optionLabels = listOf(
                    NarrationId.SHA_OPTION_ENCRYPTED_MESSAGE,
                    NarrationId.SHA_OPTION_256_BIT_HASH,
                ),
                hint = NarrationId.SHA_HINT_OUTPUT_SIZE,
                retryLook = NarrationId.SHA_RETRY_LOOK_OUTPUT_SIZE,
                retryAsk = NarrationId.SHA_RETRY_ASK_OUTPUT_SIZE,
                retryExplain = NarrationId.SHA_RETRY_EXPLAIN_OUTPUT_SIZE,
                whyWrong = NarrationId.SHA_WHY_NOT_ENCRYPTED_MESSAGE,
                correct = NarrationId.SHA_CORRECT_OUTPUT_SIZE,
                settled = NarrationId.SHA_SETTLED_OUTPUT_SIZE,
            )
        }
    }
}
