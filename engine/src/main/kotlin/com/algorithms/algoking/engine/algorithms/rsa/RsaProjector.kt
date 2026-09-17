package com.algorithms.algoking.engine.algorithms.rsa

import com.algorithms.algoking.engine.core.RsaProblem
import com.algorithms.algoking.engine.core.RsaQuestion
import com.algorithms.algoking.engine.event.ExamineRole
import com.algorithms.algoking.engine.event.MarkId
import com.algorithms.algoking.engine.event.MeterId
import com.algorithms.algoking.engine.event.VizEvent
import com.algorithms.algoking.engine.scene.Badge
import com.algorithms.algoking.engine.scene.CellState
import com.algorithms.algoking.engine.scene.ChoiceCard
import com.algorithms.algoking.engine.scene.DerivationStep
import com.algorithms.algoking.engine.scene.KeyCard
import com.algorithms.algoking.engine.scene.KeyPairScene
import com.algorithms.algoking.engine.scene.MeterReadout
import com.algorithms.algoking.engine.scene.RoundTripStage
import com.algorithms.algoking.engine.scene.RoundTripView
import com.algorithms.algoking.engine.scene.SceneProjector

/**
 * RSA presentation knowledge — ARCHITECTURE.md §7.2.
 *
 * ### What the picture has to say
 *
 * The **derivation chain**, always, from the first frame — with every value that has
 * been settled on screen and every value that has not shown as `?`. That is the
 * whole design: the chain is the lesson, so it stays visible and grows, rather than
 * being a pipeline whose stages are done with once the data has passed through.
 *
 * ### Why a `?` is the most important thing here
 *
 * A learner asked for `φ(n)` while `φ(n) = 40` is on screen is not being asked
 * anything. So a value appears **only once the beat that produces it has been
 * passed** — which the state answers directly through [RsaState.knows], because the
 * questions are interleaved with the chain rather than asked after it. That is the
 * hash flow's rule (ADR-030) applied to a dependency chain instead of a single
 * answer.
 *
 * Like every other projector, what is *lit* is read from [activeEvents] rather than
 * from the cursor: after a value lands the cursor has already moved to the next
 * question, so lighting what the cursor points at would show the learner the next
 * beat's subject beside the sentence explaining the last one (ADR-032, ADR-045).
 */
class RsaProjector : SceneProjector<RsaState> {

    override fun project(state: RsaState, activeEvents: List<VizEvent>): KeyPairScene {
        val problem = state.problem
        val question = state.question

        // Which beat this frame is about — the one whose value just landed.
        val justLanded = activeEvents
            .filterIsInstance<VizEvent.Examine>()
            .firstOrNull { it.role == ExamineRole.INSPECTING }
            ?.indices
            ?.firstOrNull()
            ?.let { ordinal -> RsaStepKind.entries.getOrNull(ordinal) }
            ?: state.lastStep?.kind

        return KeyPairScene(
            chain = chain(state, problem, justLanded),
            keys = keys(state, problem, justLanded),
            roundTrip = roundTrip(state, problem),
            choices = choices(state, question),
            // On the picture for the whole lesson, not saved for the recap. A
            // learner who looks away should not be able to come back believing they
            // have watched real encryption.
            caveat = "Toy numbers, chosen so you can check them. Real RSA is far larger.",
            // ...and the subject of exactly one beat, which is the one that has
            // nothing else to change.
            caveatProminent = state.lastStep?.kind == RsaStepKind.REAL_WORLD,
            badge = Badge(
                mark = MarkId.TARGET,
                label = "Cipher",
                value = problem.modulus.toInt(),
                valueLabel = "RSA",
            ),
            meters = buildList {
                if (!state.finished && state.remainingQuestions > 0) {
                    add(
                        MeterReadout(
                            meter = MeterId.REMAINING,
                            label = "Left",
                            value = state.remainingQuestions.toLong(),
                        ),
                    )
                }
            },
            legendLabels = mapOf(
                CellState.CANDIDATE to "Just derived",
                CellState.COMPARING to "Being asked",
                CellState.FINALIZED to "Settled",
                CellState.GHOST to "Not yet",
            ),
        )
    }

    /**
     * The six values, in the order they are built.
     *
     * Each carries **its formula as well as its value**, because the lesson is the
     * relationship and not the number: `40` on its own teaches nothing, and
     * `φ(n) = (p − 1)(q − 1) = 40` teaches the whole step.
     */
    private fun chain(
        state: RsaState,
        problem: RsaProblem,
        justLanded: RsaStepKind?,
    ): List<DerivationStep> {
        fun step(
            kind: RsaStepKind,
            symbol: String,
            formula: String,
            value: Long,
            note: String? = null,
        ): DerivationStep {
            val known = state.knows(kind)
            val asking = state.question != null && state.step?.kind == kind
            return DerivationStep(
                symbol = symbol,
                formula = formula,
                // The heart of it: a value the lesson has not produced is absent,
                // never zero and never guessed.
                value = value.takeIf { known },
                note = note,
                state = when {
                    asking -> CellState.COMPARING
                    kind == justLanded -> CellState.CANDIDATE
                    known -> CellState.FINALIZED
                    else -> CellState.GHOST
                },
            )
        }

        val primesKnown = state.knows(RsaStepKind.PRIMES)
        return listOf(
            // p and q are chosen rather than derived, so they are known from the
            // beat that states them onward — and they are two numbers under one
            // symbol, so they print through `valueLabel` rather than `value`.
            DerivationStep(
                symbol = "p, q",
                formula = "two different primes",
                valueLabel = "${problem.p}, ${problem.q}".takeIf { primesKnown },
                note = "the only secret inputs".takeIf { primesKnown },
                state = when {
                    justLanded == RsaStepKind.PRIMES -> CellState.CANDIDATE
                    primesKnown -> CellState.FINALIZED
                    else -> CellState.GHOST
                },
            ),
            step(
                RsaStepKind.MODULUS,
                symbol = "n",
                formula = "p × q",
                value = problem.modulus,
                note = "the modulus both keys share",
            ),
            step(
                RsaStepKind.TOTIENT,
                symbol = "φ(n)",
                formula = "(p − 1)(q − 1)",
                value = problem.totient,
                note = "the modulus e and d are inverses in",
            ),
            step(
                RsaStepKind.PUBLIC_EXPONENT,
                symbol = "e",
                formula = "gcd(e, φ(n)) = 1",
                value = problem.e,
                note = "the public exponent",
            ),
            step(
                RsaStepKind.PRIVATE_EXPONENT,
                symbol = "d",
                formula = "d × e ≡ 1 (mod φ(n))",
                value = problem.d,
                note = "the private exponent",
            ),
        )
    }

    /**
     * The two halves, drawn together once each exists.
     *
     * Together, always — they share a modulus and differ in exactly one number and
     * one rule, and a learner who meets them apart can read them as two unrelated
     * keys, which is the misconception the lesson is for.
     */
    private fun keys(
        state: RsaState,
        problem: RsaProblem,
        justLanded: RsaStepKind?,
    ): List<KeyCard> = buildList {
        if (state.knows(RsaStepKind.PUBLIC_KEY)) {
            add(
                KeyCard(
                    label = problem.publicKey.role.label,
                    printed = problem.publicKey.printed,
                    rule = "Share it freely",
                    use = "encrypts",
                    secret = false,
                    state = highlight(justLanded, RsaStepKind.PUBLIC_KEY),
                ),
            )
        }
        if (state.knows(RsaStepKind.PRIVATE_KEY)) {
            add(
                KeyCard(
                    label = problem.privateKey.role.label,
                    printed = problem.privateKey.printed,
                    rule = "Never share it",
                    use = "decrypts",
                    secret = true,
                    state = highlight(justLanded, RsaStepKind.PRIVATE_KEY),
                ),
            )
        }
    }

    private fun highlight(justLanded: RsaStepKind?, kind: RsaStepKind): CellState =
        if (justLanded == kind) CellState.CANDIDATE else CellState.FINALIZED

    /**
     * The message out and back.
     *
     * [RoundTripView.recovered] is read from a real decryption rather than copied
     * from the message — the call `XorState.recovered` makes (ADR-047). If applying
     * the private key did not give the message back, the picture would say so.
     */
    private fun roundTrip(state: RsaState, problem: RsaProblem): RoundTripView? {
        // Nothing to show until the keys exist and the lesson has reached the
        // message. Before that the chain is the whole picture.
        val reached = state.knows(RsaStepKind.KEY_ROLES) ||
            state.step?.kind == RsaStepKind.ENCRYPT
        if (!reached) return null

        val encrypted = state.knows(RsaStepKind.ENCRYPT)
        val decrypted = state.knows(RsaStepKind.DECRYPT)

        return RoundTripView(
            message = problem.message,
            ciphertext = problem.ciphertext.takeIf { encrypted },
            recovered = problem.recovered.takeIf { decrypted },
            encryptFormula = "c = ${problem.message}^${problem.e} mod ${problem.modulus}",
            decryptFormula = if (encrypted) {
                "m = ${problem.ciphertext}^${problem.d} mod ${problem.modulus}"
            } else {
                "m = c^d mod ${problem.modulus}"
            },
            stage = when {
                decrypted -> RoundTripStage.DECRYPTED
                encrypted -> RoundTripStage.ENCRYPTED
                else -> RoundTripStage.READY
            },
        )
    }

    /**
     * The tappable cards, for the two judgements whose options are sentences.
     *
     * Built from [RsaOptions] — the same function the decision reads — so a card and
     * the option it selects can never describe different things.
     *
     * No card is marked as the correct one. The scene does not carry which is true,
     * so the renderer could not style it differently even by accident, which is the
     * requirement PRODUCT_SPEC.md §5 makes of every decision in the app.
     */
    private fun choices(state: RsaState, question: RsaQuestion?): List<ChoiceCard> {
        question ?: return emptyList()
        val options = RsaOptions.of(question, state.problem)
        if (options.none { it.card }) return emptyList()

        return options.mapIndexed { index, option ->
            ChoiceCard(
                slot = index,
                title = option.title.orEmpty(),
                detail = option.detail.orEmpty(),
            )
        }
    }
}
