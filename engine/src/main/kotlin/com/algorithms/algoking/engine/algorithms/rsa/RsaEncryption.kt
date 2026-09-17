package com.algorithms.algoking.engine.algorithms.rsa

import com.algorithms.algoking.engine.core.Algorithm
import com.algorithms.algoking.engine.core.AlgorithmId
import com.algorithms.algoking.engine.core.Dataset
import com.algorithms.algoking.engine.core.Probe
import com.algorithms.algoking.engine.core.Rsa
import com.algorithms.algoking.engine.core.RsaProblem
import com.algorithms.algoking.engine.core.RsaQuestion
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
 * What happens in the RSA lesson.
 *
 * Two kinds of step:
 *
 *  - [Advance] is the **app's** — the beats that state something rather than derive
 *    it: which primes were picked, what each key is for, and the difference between
 *    this demonstration and real RSA;
 *  - [Answer] is the **learner's**, and it is every link in the derivation chain.
 */
sealed interface RsaAction : Action {

    /** The app states the next beat. */
    data object Advance : RsaAction

    /** The learner commits to one of the options on offer. */
    data class Answer(val choice: Int) : RsaAction
}

/**
 * One beat of the lesson.
 *
 * A step either states something or asks for something; [question] is what decides
 * which, and it is null for the beats the app owns.
 */
data class RsaStep(val kind: RsaStepKind, val question: RsaQuestion? = null)

/** What a beat of the lesson is about. */
enum class RsaStepKind {
    /** Two keys rather than one — the frame the rest hangs on. */
    SETUP,

    /** Which kind of cryptography this is. */
    ASYMMETRIC,

    /** `p` and `q`, chosen. */
    PRIMES,

    /** `n = p × q`. */
    MODULUS,

    /** `φ(n) = (p − 1)(q − 1)`. */
    TOTIENT,

    /** `e`, and the `gcd(e, φ(n)) = 1` condition that makes it legal. */
    PUBLIC_EXPONENT,

    /** `d`, the inverse of `e` modulo `φ(n)`. */
    PRIVATE_EXPONENT,

    /** `(e, n)`. */
    PUBLIC_KEY,

    /** `(d, n)`. */
    PRIVATE_KEY,

    /** What each half is for, and which one may be shared. */
    KEY_ROLES,

    /** `c = mᵉ mod n`. */
    ENCRYPT,

    /** `m = c^d mod n`. */
    DECRYPT,

    /** `4 → 9 → 4`, with both halves on screen. */
    ROUND_TRIP,

    /** Which key must stay secret. */
    SECRET_KEY,

    /** What separates this demonstration from real RSA. */
    REAL_WORLD,
}

/**
 * Immutable state — how far the lesson has got and what the learner has settled.
 *
 * One cursor, [at], over a fixed list of beats, plus [answers] for the judgements
 * made so far. A beat that carries a question cannot be passed without answering it,
 * so the two can never disagree about where the lesson is — and `answers.size` is
 * both the answer cursor and the count of questions settled.
 *
 * **No derived value is stored.** `n`, `φ(n)`, `d`, the keys and the ciphertext are
 * all read back out of [RsaProblem], which computes them from the four numbers that
 * are actually authored. A state carrying its own copy of `d` would be a second
 * source of truth for the one number the learner is asked to justify.
 */
data class RsaState(
    val problem: RsaProblem,
    /** How many beats have been passed. */
    val at: Int,
    /** One entry per settled question, in order. */
    val answers: List<Int>,
) {

    /** The lesson's beats, in order. Computed from the problem's question list. */
    val steps: List<RsaStep> get() = stepsFor(problem)

    /** The beat the lesson is standing on, or null once it is over. */
    val step: RsaStep? get() = steps.getOrNull(at)

    /** The judgement being asked right now, or null when the app is speaking. */
    val question: RsaQuestion? get() = step?.question

    /** The beat just passed — what the picture is showing. */
    val lastStep: RsaStep? get() = steps.getOrNull(at - 1)

    /** Nothing left to say and nothing left to ask. */
    val finished: Boolean get() = at >= steps.size

    /** How many beats are still to come. */
    val remaining: Int get() = (steps.size - at).coerceAtLeast(0)

    /** How many judgements are still to come. */
    val remainingQuestions: Int
        get() = steps.drop(at).count { it.question != null }

    /**
     * Which values of the derivation are known, and may therefore be drawn.
     *
     * **This is what stops the picture answering the question.** A value becomes
     * known only once the beat that produces it has been passed, so while the
     * learner is being asked for `φ(n)` the chain shows `p`, `q` and `n` and a `?`
     * where `φ(n)` will be — the rule the hash flow set (ADR-030) and the one that
     * makes a derivation chain askable rather than merely readable.
     */
    val known: Set<RsaStepKind>
        get() = steps.take(at).map { it.kind }.toSet()

    /** Whether [kind]'s value has been settled and can be shown. */
    fun knows(kind: RsaStepKind): Boolean = kind in known
}

/** The beats, in the order the lesson runs them. */
private fun stepsFor(problem: RsaProblem): List<RsaStep> {
    val asked = problem.questions.toSet()
    fun step(kind: RsaStepKind, question: RsaQuestion? = null) =
        RsaStep(kind, question?.takeIf { it in asked })

    return listOf(
        step(RsaStepKind.SETUP),
        step(RsaStepKind.ASYMMETRIC, RsaQuestion.ASYMMETRIC),
        step(RsaStepKind.PRIMES),
        step(RsaStepKind.MODULUS, RsaQuestion.MODULUS),
        step(RsaStepKind.TOTIENT, RsaQuestion.TOTIENT),
        step(RsaStepKind.PUBLIC_EXPONENT, RsaQuestion.PUBLIC_EXPONENT),
        step(RsaStepKind.PRIVATE_EXPONENT, RsaQuestion.PRIVATE_EXPONENT),
        step(RsaStepKind.PUBLIC_KEY, RsaQuestion.PUBLIC_KEY),
        step(RsaStepKind.PRIVATE_KEY, RsaQuestion.PRIVATE_KEY),
        step(RsaStepKind.KEY_ROLES),
        step(RsaStepKind.ENCRYPT, RsaQuestion.ENCRYPT),
        step(RsaStepKind.DECRYPT, RsaQuestion.DECRYPT),
        step(RsaStepKind.ROUND_TRIP),
        step(RsaStepKind.SECRET_KEY, RsaQuestion.SECRET_KEY),
        step(RsaStepKind.REAL_WORLD),
    )
}

/**
 * RSA — a lesson about **asymmetric** cryptography, at a size a learner can check.
 *
 * ### What it teaches
 *
 * ```
 * p, q  ->  n = p × q
 *       ->  φ(n) = (p − 1)(q − 1)
 *       ->  e, with gcd(e, φ(n)) = 1
 *       ->  d, with d × e ≡ 1 (mod φ(n))
 *
 * public (e, n)   ->   c = mᵉ mod n
 * private (d, n)  ->   m = c^d mod n
 * ```
 *
 * and the thing that makes it worth a lesson: **one key undoes the other**, so one
 * of them can be published.
 *
 * ### Why the questions are interleaved
 *
 * SHA-256 and AES run their whole algorithm and then ask about it, because their
 * questions are about the run as a whole (ADR-048, ADR-049). RSA's are about
 * *links in a chain*, and a chain shown whole before being asked about is a chain
 * the learner reads off rather than derives. So every value is asked at the point it
 * would be computed, with the values it depends on already on screen and its own
 * place showing `?`.
 *
 * That also means this lesson needs no inert hand-over beat: a frame is drawn from
 * the state *after* its transition, and here that state's pending question is always
 * about the *next* value — which is correctly drawn as unknown. The collision
 * ADR-049 had to fence cannot arise.
 *
 * ### The learner derives, and never grinds
 *
 * Nobody computes `9²⁷ mod 55` in their head. PRODUCT_SPEC.md §3 gives the app the
 * arithmetic, so every question prints its formula with the operands filled in and
 * asks which value it produces — and every wrong option is a **named misconception**
 * rather than noise: the totient mistaken for the modulus, `p + q` for `p × q`, the
 * exponent that shares a factor, the key with its halves swapped, the power taken
 * without the modulus.
 *
 * ### These numbers are a demonstration, and the lesson never pretends otherwise
 *
 * Textbook RSA with a two-digit modulus is the right way to *show* the mechanism and
 * the wrong way to encrypt anything. The caveat is on the picture for the whole
 * lesson rather than saved for the recap, and the recap names what real use needs:
 * large parameters and **OAEP** padding.
 */
class RsaEncryptionAlgorithm : Algorithm<RsaState, RsaAction> {

    override val id = AlgorithmId.RSA

    override fun initial(dataset: Dataset): RsaState = RsaState(
        problem = dataset.rsa ?: FALLBACK,
        at = 0,
        answers = emptyList(),
    )

    override fun probe(state: RsaState): Probe<RsaAction> {
        val step = state.step ?: return Probe.Terminal(Outcome.Completed(true))
        val question = step.question ?: return Probe.Mechanical(RsaAction.Advance)
        return Probe.Decide(decisionFor(state, question))
    }

    // -- The judgements -------------------------------------------------------

    private fun decisionFor(state: RsaState, question: RsaQuestion): Decision<RsaAction> {
        val options = RsaOptions.of(question, state.problem)
        val copy = RsaQuestionCopy.of(question, state.problem)
        val correct = options.indexOfFirst { it.correct }

        return Decision(
            // A judgement whose options are sentences is tapped on a card; one whose
            // options are numbers or pairs fits on a button. See `RsaOptionSpec`.
            kind = if (options.first().card) DecisionKind.CELL else DecisionKind.OPTIONS,
            prompt = copy.prompt,
            options = options.mapIndexed { index, option ->
                ActionOption(
                    action = RsaAction.Answer(index),
                    label = option.label,
                    slot = index.takeIf { option.card },
                )
            },
            correct = RsaAction.Answer(correct),
            focus = options.indices.toList(),
            hint = copy.hint,
            // Point at the evidence, ask the reasoning question, then say it plainly
            // — PRODUCT_SPEC.md §5's three rungs, the last repeating rather than
            // dead-ending anyone.
            guidance = listOf(copy.retryLook, copy.retryAsk, copy.retryExplain),
            minimalFeedback = copy.retryLook,
            whyWrong = options
                .mapIndexedNotNull { index, option ->
                    option.why?.let { RsaAction.Answer(index) to it }
                }
                .toMap(),
            correctFeedback = copy.correct,
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
    override fun apply(state: RsaState, action: RsaAction): Transition<RsaState> =
        when (action) {
            is RsaAction.Advance -> advance(state)
            is RsaAction.Answer -> answer(state, action.choice)
        }

    private fun advance(state: RsaState): Transition<RsaState> {
        val step = state.step
        // Nothing left, or a beat that has to be answered rather than passed: a
        // no-op leaving the state byte-for-byte as it was, rather than an exception.
        if (step == null || step.question != null) {
            return Transition(state, emptyList(), null, correct = false)
        }
        return move(state, step, correct = true)
    }

    private fun answer(state: RsaState, choice: Int): Transition<RsaState> {
        val step = state.step
        val question = step?.question
        if (question == null) return Transition(state, emptyList(), null, correct = false)

        val options = RsaOptions.of(question, state.problem)
        if (choice !in options.indices) {
            return Transition(state, emptyList(), null, correct = false)
        }

        return move(
            state = state.copy(answers = state.answers + choice),
            step = step,
            correct = options[choice].correct,
        )
    }

    /** One beat forward, with the events and the sentence that go with it. */
    private fun move(
        state: RsaState,
        step: RsaStep,
        correct: Boolean,
    ): Transition<RsaState> {
        val next = state.copy(at = state.at + 1)
        return Transition(
            next = next,
            events = buildList {
                // The value this beat produced, so the projector lights what the
                // caption is about rather than what the cursor now points at
                // (ADR-032's rule, and ADR-045's for Fibonacci).
                add(VizEvent.Examine(listOf(step.kind.ordinal), ExamineRole.INSPECTING))
                add(VizEvent.Meter(MeterId.REMAINING, next.remainingQuestions.toLong()))
                if (next.finished) add(VizEvent.Terminal(Outcome.Completed(correct)))
            },
            narration = narrationFor(step, state.problem),
            correct = correct,
        )
    }

    /** What is said as each beat lands. */
    private fun narrationFor(step: RsaStep, problem: RsaProblem): NarrationKey =
        when (step.kind) {
            RsaStepKind.SETUP -> NarrationKey(NarrationId.RSA_STEP_SETUP)
            RsaStepKind.ASYMMETRIC -> NarrationKey(NarrationId.RSA_STEP_ASYMMETRIC)
            RsaStepKind.PRIMES ->
                NarrationKey(NarrationId.RSA_STEP_PRIMES, listOf(problem.p, problem.q))

            RsaStepKind.MODULUS -> NarrationKey(
                NarrationId.RSA_STEP_MODULUS,
                listOf(problem.p, problem.q, problem.modulus),
            )

            RsaStepKind.TOTIENT -> NarrationKey(
                NarrationId.RSA_STEP_TOTIENT,
                listOf(problem.p - 1, problem.q - 1, problem.totient),
            )

            RsaStepKind.PUBLIC_EXPONENT -> NarrationKey(
                NarrationId.RSA_STEP_PUBLIC_EXPONENT,
                listOf(problem.e, problem.totient),
            )

            RsaStepKind.PRIVATE_EXPONENT -> NarrationKey(
                NarrationId.RSA_STEP_PRIVATE_EXPONENT,
                listOf(problem.d, problem.e, problem.e * problem.d, problem.totient),
            )

            RsaStepKind.PUBLIC_KEY -> NarrationKey(
                NarrationId.RSA_STEP_PUBLIC_KEY,
                listOf(problem.publicKey.printed),
            )

            RsaStepKind.PRIVATE_KEY -> NarrationKey(
                NarrationId.RSA_STEP_PRIVATE_KEY,
                listOf(problem.privateKey.printed),
            )

            RsaStepKind.KEY_ROLES -> NarrationKey(NarrationId.RSA_STEP_KEY_ROLES)
            RsaStepKind.ENCRYPT -> NarrationKey(
                NarrationId.RSA_STEP_ENCRYPT,
                listOf(problem.message, problem.e, problem.modulus, problem.ciphertext),
            )

            RsaStepKind.DECRYPT -> NarrationKey(
                NarrationId.RSA_STEP_DECRYPT,
                listOf(problem.ciphertext, problem.d, problem.modulus, problem.recovered),
            )

            RsaStepKind.ROUND_TRIP -> NarrationKey(
                NarrationId.RSA_STEP_ROUND_TRIP,
                listOf(problem.message, problem.ciphertext, problem.recovered),
            )

            RsaStepKind.SECRET_KEY -> NarrationKey(NarrationId.RSA_STEP_SECRET_KEY)
            RsaStepKind.REAL_WORLD -> NarrationKey(NarrationId.RSA_STEP_REAL_WORLD)
        }

    private companion object {
        /** Used only when a dataset forgets to say; every authored one says. */
        val FALLBACK = RsaProblem(
            p = 5,
            q = 11,
            e = 3,
            message = 4,
            questions = RsaQuestion.entries.toList(),
        )
    }
}

/**
 * One option on offer, with everything both the decision and the picture need.
 *
 * The decision reads [label] and [why]; the projector reads [title] and [detail] to
 * draw a card. **They come from one function** ([RsaOptions.of]), because two lists
 * in the same order maintained in two places is how a card ends up describing a
 * different option than the button beneath it selects.
 */
internal data class RsaOptionSpec(
    val label: NarrationKey,
    val correct: Boolean,
    /** Why this option is wrong, named rather than generic. Null when it is right. */
    val why: NarrationKey? = null,
    /** True when this option is a tappable card rather than a button. */
    val card: Boolean = false,
    /** The card's heading. */
    val title: String? = null,
    /** The clause that makes the card unambiguous. */
    val detail: String? = null,
)

/**
 * The options for each judgement — **derived from the problem, never authored**.
 *
 * Every wrong option is a formula rather than a number, so it is a *misconception*
 * that stays wrong whatever key pair the lesson runs on: `(p − 1)(q − 1)` offered
 * where `p × q` is wanted, `p + q` for a learner who added, `mᵉ` for one who forgot
 * the modulus. A dataset change moves every distractor with it, and none of them can
 * go stale.
 *
 * On the lesson's own numbers these come out as the values the brief lists — `n` as
 * 40 / 55 / 16 / 44 and `φ(n)` as 40 / 55 / 44 / 10 — which is a good sign the
 * formulas are the ones learners actually get wrong.
 */
internal object RsaOptions {

    fun of(question: RsaQuestion, problem: RsaProblem): List<RsaOptionSpec> {
        // Distractors are formulas, so on some key pairs two of them can land on the
        // same number — and an option offered twice is a question with two right
        // answers or two wrong ones that cannot be told apart. Deduplicating by what
        // the learner actually reads is the only check that catches it.
        val specs = build(question, problem)
            .distinctBy { it.label.args to it.label.id }
            .let { deduped -> deduped.take(OPTION_COUNT).ifEmpty { deduped } }

        // The correct answer must not always sit in the same seat: a learner who
        // noticed would finish the stage without reading anything, which is the
        // failure PRODUCT_SPEC.md §5 exists to prevent. Rotating by the question's
        // own ordinal is deterministic, so the lesson is the same every run.
        val shift = question.ordinal % specs.size
        return specs.drop(shift) + specs.take(shift)
    }

    /** How many options a judgement offers when it has that many distinct ones. */
    private const val OPTION_COUNT = 4

    private fun build(question: RsaQuestion, problem: RsaProblem): List<RsaOptionSpec> =
        when (question) {
            RsaQuestion.ASYMMETRIC -> listOf(
                card(
                    title = "Asymmetric cryptography",
                    detail = "Two related keys — one public, one private.",
                    short = "ASYMMETRIC",
                    correct = true,
                ),
                card(
                    title = "Symmetric cryptography",
                    detail = "One shared key, used to encrypt and to decrypt.",
                    short = "SYMMETRIC",
                    why = NarrationId.RSA_WHY_NOT_SYMMETRIC,
                ),
                card(
                    title = "Hashing",
                    detail = "A one-way fingerprint. No key, and nothing to undo.",
                    short = "HASHING",
                    why = NarrationId.RSA_WHY_NOT_HASHING,
                ),
                card(
                    title = "Compression",
                    detail = "Makes data smaller. Not a security operation at all.",
                    short = "COMPRESSION",
                    why = NarrationId.RSA_WHY_NOT_COMPRESSION,
                ),
            )

            RsaQuestion.MODULUS -> listOf(
                number(problem.modulus, correct = true),
                number(problem.totient, why = NarrationId.RSA_WHY_N_IS_TOTIENT),
                number(problem.p + problem.q, why = NarrationId.RSA_WHY_N_IS_SUM),
                number(
                    (problem.p - 1) * problem.q,
                    why = NarrationId.RSA_WHY_N_IS_PARTIAL,
                    whyArgs = listOf(problem.p, problem.q),
                ),
            )

            RsaQuestion.TOTIENT -> listOf(
                number(problem.totient, correct = true),
                number(problem.modulus, why = NarrationId.RSA_WHY_PHI_IS_N),
                number(
                    (problem.p - 1) * problem.q,
                    why = NarrationId.RSA_WHY_PHI_IS_PARTIAL,
                    whyArgs = listOf(problem.p - 1, problem.q - 1),
                ),
                number(problem.q - 1, why = NarrationId.RSA_WHY_PHI_IS_HALF),
            )

            // The three smallest numbers above 1 that share a factor with φ(n), so
            // every wrong answer fails the one test the beat is about.
            RsaQuestion.PUBLIC_EXPONENT -> buildList {
                add(number(problem.e, correct = true))
                var candidate = 2L
                while (size < 4 && candidate < problem.totient) {
                    if (candidate != problem.e && Rsa.gcd(candidate, problem.totient) != 1L) {
                        add(
                            number(
                                candidate,
                                why = NarrationId.RSA_WHY_E_SHARES_FACTOR,
                                whyArgs = listOf(
                                    candidate,
                                    problem.totient,
                                    Rsa.gcd(candidate, problem.totient),
                                ),
                            ),
                        )
                    }
                    candidate++
                }
            }

            RsaQuestion.PRIVATE_EXPONENT -> listOf(
                number(problem.d, correct = true),
                number(problem.e, why = NarrationId.RSA_WHY_D_IS_E),
                number(
                    problem.totient - problem.d,
                    why = NarrationId.RSA_WHY_D_NOT_INVERSE,
                    whyArgs = listOf(
                        problem.totient - problem.d,
                        problem.e,
                        (problem.totient - problem.d) * problem.e % problem.totient,
                        problem.totient,
                    ),
                ),
                number(
                    problem.e * problem.e,
                    why = NarrationId.RSA_WHY_D_NOT_INVERSE,
                    whyArgs = listOf(
                        problem.e * problem.e,
                        problem.e,
                        problem.e * problem.e * problem.e % problem.totient,
                        problem.totient,
                    ),
                ),
            )

            RsaQuestion.PUBLIC_KEY -> listOf(
                pair(problem.e, problem.modulus, correct = true),
                pair(problem.modulus, problem.e, why = NarrationId.RSA_WHY_KEY_SWAPPED),
                pair(problem.d, problem.modulus, why = NarrationId.RSA_WHY_KEY_IS_PRIVATE),
                pair(problem.e, problem.totient, why = NarrationId.RSA_WHY_KEY_USES_PHI),
            )

            RsaQuestion.PRIVATE_KEY -> listOf(
                pair(problem.d, problem.modulus, correct = true),
                pair(problem.e, problem.modulus, why = NarrationId.RSA_WHY_KEY_IS_PUBLIC),
                pair(problem.modulus, problem.d, why = NarrationId.RSA_WHY_KEY_SWAPPED),
                pair(problem.d, problem.totient, why = NarrationId.RSA_WHY_KEY_USES_PHI),
            )

            RsaQuestion.ENCRYPT -> buildList {
                add(number(problem.ciphertext, correct = true))
                // "Forgot the modulus" is the best distractor this beat has, and on
                // the lesson's numbers it is a four-digit answer beside a two-digit
                // modulus — visibly absurd rather than merely wrong. It is offered
                // only while it stays a number a learner can read; `power` returns
                // null rather than overflowing on a key pair this lesson would
                // refuse anyway.
                val unreduced = power(problem.message, problem.e)
                if (unreduced != null) {
                    add(
                        number(
                            unreduced,
                            why = NarrationId.RSA_WHY_C_NO_MOD,
                            whyArgs = listOf(
                                problem.message, problem.e, unreduced,
                                problem.modulus, problem.ciphertext,
                            ),
                        ),
                    )
                }
                add(
                    number(
                        problem.message * problem.e,
                        why = NarrationId.RSA_WHY_C_MULTIPLIED,
                        whyArgs = listOf(problem.message, problem.e),
                    ),
                )
                // The same power reduced by the wrong modulus — φ(n) instead of n.
                add(
                    number(
                        Rsa.modPow(problem.message, problem.e, problem.totient),
                        why = NarrationId.RSA_WHY_C_USED_PHI,
                        whyArgs = listOf(problem.totient, problem.modulus),
                    ),
                )
                // A last resort that is never reached on either lesson dataset, and
                // a test says so: if every distractor above collided, the beat would
                // still offer a wrong answer rather than a single button.
                add(
                    number(
                        problem.message,
                        why = NarrationId.RSA_WHY_C_UNCHANGED,
                        whyArgs = listOf(problem.message),
                    ),
                )
            }

            RsaQuestion.DECRYPT -> listOf(
                number(problem.message, correct = true),
                number(problem.ciphertext, why = NarrationId.RSA_WHY_M_IS_CIPHERTEXT),
                number(problem.d, why = NarrationId.RSA_WHY_M_IS_EXPONENT),
                number(problem.e, why = NarrationId.RSA_WHY_M_IS_EXPONENT),
            )

            RsaQuestion.SECRET_KEY -> listOf(
                card(
                    title = "The private key",
                    detail = "It is what undoes the public key's work. Keep it.",
                    short = "PRIVATE",
                    correct = true,
                ),
                card(
                    title = "The public key",
                    detail = "This is the half that is meant to be handed out.",
                    short = "PUBLIC",
                    why = NarrationId.RSA_WHY_SECRET_PUBLIC,
                ),
                card(
                    title = "Both of them",
                    detail = "Then nobody could send you anything.",
                    short = "BOTH",
                    why = NarrationId.RSA_WHY_SECRET_BOTH,
                ),
                card(
                    title = "Neither",
                    detail = "Then anyone who wanted to could read the message.",
                    short = "NEITHER",
                    why = NarrationId.RSA_WHY_SECRET_NEITHER,
                ),
            )
        }

    /**
     * `base^exponent`, unreduced — or null when it would stop being readable.
     *
     * Only ever used to build the "forgot the modulus" distractor, which is the
     * point: an unreduced power is a number the learner is meant to recognise as
     * far too large, and a twenty-digit one is just noise. Returning null rather
     * than overflowing means the option is dropped instead of wrong.
     */
    private fun power(base: Long, exponent: Long): Long? {
        var result = 1L
        repeat(exponent.toInt()) {
            result *= base
            if (result > READABLE_CEILING) return null
        }
        return result
    }

    /** Above this, an unreduced power is noise rather than a recognisable mistake. */
    private const val READABLE_CEILING = 1_000_000L

    private fun number(
        value: Long,
        correct: Boolean = false,
        why: NarrationId? = null,
        whyArgs: List<Any> = emptyList(),
    ) = RsaOptionSpec(
        label = NarrationKey(NarrationId.RSA_OPTION_NUMBER, listOf(value)),
        correct = correct,
        why = why?.let { NarrationKey(it, whyArgs.ifEmpty { listOf(value) }) },
    )

    private fun pair(
        left: Long,
        right: Long,
        correct: Boolean = false,
        why: NarrationId? = null,
    ) = RsaOptionSpec(
        label = NarrationKey(NarrationId.RSA_OPTION_PAIR, listOf(left, right)),
        correct = correct,
        why = why?.let { NarrationKey(it, listOf(left, right)) },
    )

    private fun card(
        title: String,
        detail: String,
        short: String,
        correct: Boolean = false,
        why: NarrationId? = null,
    ) = RsaOptionSpec(
        label = NarrationKey(NarrationId.RSA_OPTION_TEXT, listOf(short)),
        correct = correct,
        why = why?.let { NarrationKey(it) },
        card = true,
        title = title,
        detail = detail,
    )
}

/**
 * Every narration key one judgement needs, in one place.
 *
 * The alternative is a `when (question)` in the decision builder and another in the
 * narrator — two places to forget a question when an eleventh is added. Here the
 * compiler's exhaustiveness check over [RsaQuestion] catches it once.
 */
internal data class RsaQuestionCopy(
    val prompt: NarrationKey,
    val hint: NarrationKey,
    val retryLook: NarrationKey,
    val retryAsk: NarrationKey,
    val retryExplain: NarrationKey,
    val correct: NarrationKey,
) {
    companion object {
        fun of(question: RsaQuestion, problem: RsaProblem): RsaQuestionCopy =
            when (question) {
                RsaQuestion.ASYMMETRIC -> RsaQuestionCopy(
                    prompt = key(NarrationId.RSA_ASK_ASYMMETRIC),
                    hint = key(NarrationId.RSA_HINT_ASYMMETRIC),
                    retryLook = key(NarrationId.RSA_RETRY_LOOK_ASYMMETRIC),
                    retryAsk = key(NarrationId.RSA_RETRY_ASK_ASYMMETRIC),
                    retryExplain = key(NarrationId.RSA_RETRY_EXPLAIN_ASYMMETRIC),
                    correct = key(NarrationId.RSA_CORRECT_ASYMMETRIC),
                )

                RsaQuestion.MODULUS -> RsaQuestionCopy(
                    prompt = key(NarrationId.RSA_ASK_MODULUS),
                    hint = key(NarrationId.RSA_HINT_MODULUS, problem.p, problem.q),
                    retryLook = key(NarrationId.RSA_RETRY_LOOK_MODULUS),
                    retryAsk = key(NarrationId.RSA_RETRY_ASK_MODULUS, problem.p, problem.q),
                    retryExplain = key(
                        NarrationId.RSA_RETRY_EXPLAIN_MODULUS,
                        problem.p, problem.q, problem.modulus,
                    ),
                    correct = key(
                        NarrationId.RSA_CORRECT_MODULUS,
                        problem.p, problem.q, problem.modulus,
                    ),
                )

                RsaQuestion.TOTIENT -> RsaQuestionCopy(
                    prompt = key(NarrationId.RSA_ASK_TOTIENT),
                    hint = key(NarrationId.RSA_HINT_TOTIENT),
                    retryLook = key(NarrationId.RSA_RETRY_LOOK_TOTIENT),
                    retryAsk = key(
                        NarrationId.RSA_RETRY_ASK_TOTIENT,
                        problem.p - 1, problem.q - 1,
                    ),
                    retryExplain = key(
                        NarrationId.RSA_RETRY_EXPLAIN_TOTIENT,
                        problem.p - 1, problem.q - 1, problem.totient,
                    ),
                    correct = key(
                        NarrationId.RSA_CORRECT_TOTIENT,
                        problem.p - 1, problem.q - 1, problem.totient,
                    ),
                )

                RsaQuestion.PUBLIC_EXPONENT -> RsaQuestionCopy(
                    prompt = key(NarrationId.RSA_ASK_PUBLIC_EXPONENT, problem.totient),
                    hint = key(NarrationId.RSA_HINT_PUBLIC_EXPONENT, problem.totient),
                    retryLook = key(NarrationId.RSA_RETRY_LOOK_PUBLIC_EXPONENT),
                    retryAsk = key(
                        NarrationId.RSA_RETRY_ASK_PUBLIC_EXPONENT,
                        problem.totient,
                    ),
                    retryExplain = key(
                        NarrationId.RSA_RETRY_EXPLAIN_PUBLIC_EXPONENT,
                        problem.e, problem.totient,
                    ),
                    correct = key(
                        NarrationId.RSA_CORRECT_PUBLIC_EXPONENT,
                        problem.e, problem.totient,
                    ),
                )

                RsaQuestion.PRIVATE_EXPONENT -> RsaQuestionCopy(
                    prompt = key(
                        NarrationId.RSA_ASK_PRIVATE_EXPONENT,
                        problem.e, problem.totient,
                    ),
                    hint = key(NarrationId.RSA_HINT_PRIVATE_EXPONENT, problem.e, problem.totient),
                    retryLook = key(NarrationId.RSA_RETRY_LOOK_PRIVATE_EXPONENT),
                    retryAsk = key(
                        NarrationId.RSA_RETRY_ASK_PRIVATE_EXPONENT,
                        problem.e, problem.totient,
                    ),
                    retryExplain = key(
                        NarrationId.RSA_RETRY_EXPLAIN_PRIVATE_EXPONENT,
                        problem.e, problem.d, problem.e * problem.d, problem.totient,
                    ),
                    correct = key(
                        NarrationId.RSA_CORRECT_PRIVATE_EXPONENT,
                        problem.e, problem.d, problem.e * problem.d, problem.totient,
                    ),
                )

                RsaQuestion.PUBLIC_KEY -> RsaQuestionCopy(
                    prompt = key(NarrationId.RSA_ASK_PUBLIC_KEY),
                    hint = key(NarrationId.RSA_HINT_PUBLIC_KEY),
                    retryLook = key(NarrationId.RSA_RETRY_LOOK_PUBLIC_KEY),
                    retryAsk = key(NarrationId.RSA_RETRY_ASK_PUBLIC_KEY),
                    retryExplain = key(
                        NarrationId.RSA_RETRY_EXPLAIN_PUBLIC_KEY,
                        problem.e, problem.modulus,
                    ),
                    correct = key(
                        NarrationId.RSA_CORRECT_PUBLIC_KEY,
                        problem.e, problem.modulus,
                    ),
                )

                RsaQuestion.PRIVATE_KEY -> RsaQuestionCopy(
                    prompt = key(NarrationId.RSA_ASK_PRIVATE_KEY),
                    hint = key(NarrationId.RSA_HINT_PRIVATE_KEY),
                    retryLook = key(NarrationId.RSA_RETRY_LOOK_PRIVATE_KEY),
                    retryAsk = key(NarrationId.RSA_RETRY_ASK_PRIVATE_KEY),
                    retryExplain = key(
                        NarrationId.RSA_RETRY_EXPLAIN_PRIVATE_KEY,
                        problem.d, problem.modulus,
                    ),
                    correct = key(
                        NarrationId.RSA_CORRECT_PRIVATE_KEY,
                        problem.d, problem.modulus,
                    ),
                )

                RsaQuestion.ENCRYPT -> RsaQuestionCopy(
                    prompt = key(
                        NarrationId.RSA_ASK_ENCRYPT,
                        problem.message, problem.e, problem.modulus,
                    ),
                    hint = key(NarrationId.RSA_HINT_ENCRYPT),
                    retryLook = key(NarrationId.RSA_RETRY_LOOK_ENCRYPT),
                    // Deliberately stated symbolically rather than with the
                    // unreduced power filled in: `mᵉ` is a four-digit number here
                    // and a twenty-digit one on a larger key pair, and the rung has
                    // to read the same either way.
                    retryAsk = key(
                        NarrationId.RSA_RETRY_ASK_ENCRYPT,
                        problem.message, problem.e, problem.modulus,
                    ),
                    retryExplain = key(
                        NarrationId.RSA_RETRY_EXPLAIN_ENCRYPT,
                        problem.message, problem.e,
                        problem.modulus, problem.ciphertext,
                    ),
                    correct = key(
                        NarrationId.RSA_CORRECT_ENCRYPT,
                        problem.message, problem.e, problem.modulus, problem.ciphertext,
                    ),
                )

                RsaQuestion.DECRYPT -> RsaQuestionCopy(
                    prompt = key(
                        NarrationId.RSA_ASK_DECRYPT,
                        problem.ciphertext, problem.d, problem.modulus,
                    ),
                    hint = key(NarrationId.RSA_HINT_DECRYPT),
                    retryLook = key(NarrationId.RSA_RETRY_LOOK_DECRYPT),
                    retryAsk = key(NarrationId.RSA_RETRY_ASK_DECRYPT),
                    retryExplain = key(
                        NarrationId.RSA_RETRY_EXPLAIN_DECRYPT,
                        problem.ciphertext, problem.d, problem.modulus, problem.recovered,
                    ),
                    correct = key(
                        NarrationId.RSA_CORRECT_DECRYPT,
                        problem.ciphertext, problem.d, problem.modulus, problem.recovered,
                    ),
                )

                RsaQuestion.SECRET_KEY -> RsaQuestionCopy(
                    prompt = key(NarrationId.RSA_ASK_SECRET_KEY),
                    hint = key(NarrationId.RSA_HINT_SECRET_KEY),
                    retryLook = key(NarrationId.RSA_RETRY_LOOK_SECRET_KEY),
                    retryAsk = key(NarrationId.RSA_RETRY_ASK_SECRET_KEY),
                    retryExplain = key(NarrationId.RSA_RETRY_EXPLAIN_SECRET_KEY),
                    correct = key(NarrationId.RSA_CORRECT_SECRET_KEY),
                )
            }

        private fun key(id: NarrationId, vararg args: Any) =
            NarrationKey(id, args.toList())
    }
}
