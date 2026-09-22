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
import com.algorithms.algoking.engine.scene.FlowNode
import com.algorithms.algoking.engine.scene.FlowNodeKind
import com.algorithms.algoking.engine.scene.FlowValueStyle
import com.algorithms.algoking.engine.scene.FlowView
import com.algorithms.algoking.engine.scene.KeyCard
import com.algorithms.algoking.engine.scene.KeyPairScene
import com.algorithms.algoking.engine.scene.LessonLayer
import com.algorithms.algoking.engine.scene.MathsEmphasis
import com.algorithms.algoking.engine.scene.MathsLine
import com.algorithms.algoking.engine.scene.MathsPanel
import com.algorithms.algoking.engine.scene.MeterReadout
import com.algorithms.algoking.engine.scene.RoundTripStage
import com.algorithms.algoking.engine.scene.RoundTripView
import com.algorithms.algoking.engine.scene.SceneProjector
import com.algorithms.algoking.engine.scene.TextBridgeCell
import com.algorithms.algoking.engine.scene.TextBridgeView

/**
 * RSA presentation knowledge — ARCHITECTURE.md §7.2.
 *
 * ### Two acts, two pictures (ADR-052)
 *
 * **Act I draws a [FlowView]** — `message → public key → encrypt → ciphertext`, and
 * then the same shape backwards — with a [MathsPanel] beside it once there is
 * arithmetic to justify. The derivation chain is **empty for the whole of Act I**,
 * because `p`, `q`, `φ(n)`, `e` and `d` have not been mentioned and a panel of five
 * question marks is not a picture of anything. The keys are simply `(3, 55)` and
 * `(27, 55)`: two things with jobs.
 *
 * **Act II draws the chain**, exactly as it always did — every value that has been
 * settled on screen and every value that has not shown as `?` — because by then the
 * lesson is about where those two pairs came from.
 *
 * The flow and the chain are therefore never on screen together, and that is the
 * design rather than a coincidence: they are answers to two different questions, and
 * the lesson only asks the second one once the learner can answer the first.
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
            flow = flow(state, problem),
            bridge = bridge(state, problem),
            maths = maths(state, problem),
            roundTrip = roundTrip(state, problem),
            choices = choices(state, question),
            layer = layer(state),
            // On the picture for the whole lesson, not saved for the recap. A
            // learner who looks away should not be able to come back believing they
            // have watched real encryption — and in the concept layer the thing to
            // be careful about is the opposite one, that those bytes are not a
            // computation (ADR-053).
            caveat = (layer(state) ?: LessonLayer.CONCEPT).note,
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
     * `M E E T` over `77 69 69 84` — the one beat that joins the two layers.
     *
     * Shown on the bridge beat and on the beat that announces the toy example, then
     * gone. It answers the question a learner who has watched nine beats about a
     * *message* will otherwise carry into the arithmetic: where does RSA get a number
     * from? (ADR-053.)
     *
     * Cut to [BRIDGE_CELLS] characters, because the point is the correspondence and
     * a fourteen-column strip at 360dp makes it illegible. Truncation is reported
     * rather than hidden, so the renderer can draw an ellipsis instead of implying
     * the message was that short.
     */
    private fun bridge(state: RsaState, problem: RsaProblem): TextBridgeView? {
        // The bridge beat only. Once the toy example has announced itself the strip
        // has done its job, and leaving it up puts three pictures on one frame.
        val on = state.knows(RsaStepKind.TEXT_AS_NUMBERS) &&
            !state.knows(RsaStepKind.TOY_EXAMPLE)
        if (!on) return null

        val shown = problem.plaintext.take(BRIDGE_CELLS)
        return TextBridgeView(
            cells = shown.map { character ->
                TextBridgeCell(
                    // A space has a code like any other character, and showing it
                    // blank would look like a gap in the correspondence.
                    character = if (character == ' ') "␣" else character.toString(),
                    code = character.code,
                )
            },
            truncated = problem.plaintext.length > BRIDGE_CELLS,
        )
    }

    /**
     * Which layer this frame is on — **the one thing the lesson must not get wrong**
     * (ADR-053).
     *
     * The concept layer is a true account of what RSA is for, told on a message a
     * person would send. The toy layer is real arithmetic on numbers small enough to
     * check by hand. `n = 55` cannot encrypt *"MEET AT 7"*, so neither layer is
     * allowed to be mistaken for the other, and the banner that says which is which
     * is data rather than a line of copy someone has to remember to write.
     *
     * The switch happens at [RsaStepKind.TOY_EXAMPLE], which is the beat that
     * announces the toy layer *before* showing any of its numbers.
     */
    private fun layer(state: RsaState): LessonLayer? = when {
        state.knows(RsaStepKind.TOY_EXAMPLE) -> LessonLayer.TOY
        // The bridge belongs to neither: it is about the move between them.
        state.knows(RsaStepKind.TEXT_AS_NUMBERS) -> null
        else -> LessonLayer.CONCEPT
    }

    /**
     * The five values, in the order they are built — **Act II only**.
     *
     * Each carries **its formula as well as its value**, because the lesson is the
     * relationship and not the number: `40` on its own teaches nothing, and
     * `φ(n) = (p − 1)(q − 1) = 40` teaches the whole step.
     *
     * Empty until the lesson asks where the keys came from (ADR-052). Drawing five
     * rows of `?` through the whole of Act I would put every symbol the re-ordering
     * exists to delay on the very first screen — the thing being fixed, wearing a
     * question mark.
     */
    private fun chain(
        state: RsaState,
        problem: RsaProblem,
        justLanded: RsaStepKind?,
    ): List<DerivationStep> {
        if (!state.knows(RsaStepKind.KEY_ORIGIN)) return emptyList()
        // The closing beat goes back to the journey, alone. The chain has been
        // explained by then, and three pictures on one frame is not a summary.
        if (state.knows(RsaStepKind.CLOSING_FLOW)) return emptyList()

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
    ): List<KeyCard> {
        // Both halves arrive together, on the beat that hands them over — long
        // before the lesson says where they came from (ADR-052). That is what makes
        // Act I possible: a key is a thing with a job, and its construction is a
        // separate question the lesson asks later.
        if (!state.knows(RsaStepKind.KEY_REVEAL)) return emptyList()

        // Re-lit when the derivation reaches each one, so a learner can see the pair
        // they have been using all along being built.
        fun litBy(vararg kinds: RsaStepKind) =
            if (kinds.any { it == justLanded }) CellState.CANDIDATE else CellState.FINALIZED

        // **The numbers arrive with the toy layer, not before** (ADR-053). In the
        // concept layer a key is a half that may be shared and a half that may not;
        // printing `(3, 55)` there would put the lesson's first arithmetic on the
        // one screen whose whole job is to carry none.
        val numbered = state.knows(RsaStepKind.TOY_EXAMPLE)

        return listOf(
            KeyCard(
                label = problem.publicKey.role.label,
                printed = problem.publicKey.printed.takeIf { numbered },
                rule = "Can be shared",
                use = "encrypts",
                secret = false,
                state = litBy(RsaStepKind.KEY_REVEAL, RsaStepKind.PUBLIC_KEY),
            ),
            KeyCard(
                label = problem.privateKey.role.label,
                printed = problem.privateKey.printed.takeIf { numbered },
                rule = "Keep secret",
                use = "decrypts",
                secret = true,
                state = litBy(RsaStepKind.KEY_REVEAL, RsaStepKind.PRIVATE_KEY),
            ),
        )
    }

    /**
     * The step the lesson is on, drawn as a flow.
     *
     * ### It changes what it is made of, halfway through
     *
     * In the **concept layer** the nodes are a message, a lock, a key and some
     * unreadable bytes — `"MEET AT 7" -> public key -> ENCRYPT -> 8F 3A C1 D4 9B 22`.
     * There is not a digit on it, which is the point: a learner should be able to say
     * what RSA is for before meeting any of its arithmetic (ADR-053).
     *
     * In the **toy layer** the same shape carries `4`, `(3, 55)` and `9`. Same
     * picture, same arrows, different contents — which is exactly the claim the
     * lesson is making about the two layers, drawn rather than asserted.
     *
     * Which flow is decided by **what the learner has settled**, never by where the
     * cursor is, so it only ever moves forward. [FlowValueStyle] keeps the two layers
     * visually apart: prose for a message, monospace for bytes, tabular numerals for
     * arithmetic.
     *
     * A node whose value the lesson has not produced carries a null value and is
     * drawn `?` — the chain's rule, applied to a flow. That is what lets
     * `ENCRYPT_OPERATION` be asked at all: the verb node is blank while the learner
     * is choosing which verb it is.
     */
    private fun flow(state: RsaState, problem: RsaProblem): FlowView? {
        // The derivation chain is the picture once key generation starts, right up
        // until the closing beat returns to the journey.
        if (state.knows(RsaStepKind.KEY_ORIGIN) && !state.knows(RsaStepKind.CLOSING_FLOW)) {
            return null
        }

        // -- Concept-layer nodes: a message, two keys, some bytes -------------

        fun plaintext(lit: Boolean = false) = FlowNode(
            label = "MESSAGE",
            value = problem.plaintext,
            kind = FlowNodeKind.MESSAGE,
            style = FlowValueStyle.TEXT,
            state = if (lit) CellState.CANDIDATE else CellState.FINALIZED,
        )

        fun bytes(known: Boolean, lit: Boolean = false) = FlowNode(
            label = "CIPHERTEXT",
            value = problem.illustrativeCiphertext.takeIf { known },
            kind = FlowNodeKind.CIPHERTEXT,
            style = FlowValueStyle.BYTES,
            note = "unreadable without the other key".takeIf { known },
            state = when {
                lit -> CellState.CANDIDATE
                known -> CellState.FINALIZED
                else -> CellState.GHOST
            },
        )

        // The keys carry their numbers only once the toy layer has introduced them
        // (ADR-053). In the concept layer they are a half that may be shared and a
        // half that may not, and nothing else.
        val numbered = state.knows(RsaStepKind.TOY_EXAMPLE)
        val keyPublic = FlowNode(
            label = "PUBLIC KEY",
            value = problem.publicKey.printed.takeIf { numbered },
            kind = FlowNodeKind.PUBLIC_KEY,
            note = "can be shared",
            state = CellState.FINALIZED,
        )
        val keyPrivate = FlowNode(
            label = "PRIVATE KEY",
            value = problem.privateKey.printed.takeIf { numbered },
            kind = FlowNodeKind.PRIVATE_KEY,
            note = "keep secret",
            state = CellState.FINALIZED,
        )

        fun operation(label: String, known: Boolean) = FlowNode(
            label = if (known) label else "?",
            kind = FlowNodeKind.OPERATION,
            state = if (known) CellState.FINALIZED else CellState.COMPARING,
        )

        // -- Toy-layer nodes: the same shape, carrying numbers ----------------

        fun number(
            label: String,
            kind: FlowNodeKind,
            value: Long?,
            lit: Boolean = false,
        ) = FlowNode(
            label = label,
            value = value?.toString(),
            kind = kind,
            state = when {
                lit -> CellState.CANDIDATE
                value != null -> CellState.FINALIZED
                else -> CellState.GHOST
            },
        )

        val journey = listOf(
            plaintext(),
            keyPublic,
            operation("ENCRYPT", known = true),
            bytes(known = true),
            keyPrivate,
            operation("DECRYPT", known = true),
            plaintext(lit = true),
        )

        return when {
            // The closing beat returns to the picture the learner started on, now
            // that every arrow on it has been explained.
            state.knows(RsaStepKind.CLOSING_FLOW) ->
                FlowView(title = "THE WHOLE JOURNEY", nodes = journey)

            // -- Toy layer ----------------------------------------------------

            // From the pause on `c = 9`, never from the beat that lands it — that
            // frame's caption is about the encryption, and turning the picture round
            // under it is the collision `TOY_CIPHERTEXT` exists to fence.
            state.knows(RsaStepKind.TOY_CIPHERTEXT) -> FlowView(
                title = "TOY EXAMPLE - DECRYPTING",
                nodes = listOf(
                    number("CIPHERTEXT", FlowNodeKind.CIPHERTEXT, problem.ciphertext),
                    keyPrivate,
                    operation("DECRYPT", known = true),
                    number(
                        "MESSAGE",
                        FlowNodeKind.MESSAGE,
                        problem.recovered.takeIf { state.knows(RsaStepKind.DECRYPT) },
                        lit = state.knows(RsaStepKind.DECRYPT),
                    ),
                ),
            )

            state.knows(RsaStepKind.TOY_EXAMPLE) -> FlowView(
                title = "TOY EXAMPLE - ENCRYPTING",
                nodes = listOf(
                    number("MESSAGE", FlowNodeKind.MESSAGE, problem.message),
                    keyPublic,
                    operation("ENCRYPT", known = true),
                    number(
                        "CIPHERTEXT",
                        FlowNodeKind.CIPHERTEXT,
                        problem.ciphertext.takeIf { state.knows(RsaStepKind.ENCRYPT) },
                        lit = state.knows(RsaStepKind.ENCRYPT),
                    ),
                ),
            )

            // The bridge beat draws the text-to-numbers strip instead; leaving the
            // flow up beside it would put two pictures of the same idea on one frame.
            state.knows(RsaStepKind.TEXT_AS_NUMBERS) -> null

            // -- Concept layer ------------------------------------------------

            state.knows(RsaStepKind.ROUND_TRIP) ->
                FlowView(title = "THE WHOLE JOURNEY", nodes = journey)

            // From the beat that shows the ciphertext, through the message arriving
            // back. The verb is blank while the learner is being asked which key
            // does this.
            state.knows(RsaStepKind.CIPHERTEXT) -> FlowView(
                title = "DECRYPTING",
                nodes = listOf(
                    bytes(known = true),
                    keyPrivate,
                    operation("DECRYPT", known = state.knows(RsaStepKind.DECRYPT_KEY)),
                    FlowNode(
                        label = "MESSAGE",
                        value = problem.plaintext.takeIf { state.knows(RsaStepKind.RECOVERED) },
                        kind = FlowNodeKind.MESSAGE,
                        style = FlowValueStyle.TEXT,
                        state = if (state.knows(RsaStepKind.RECOVERED)) {
                            CellState.CANDIDATE
                        } else {
                            CellState.GHOST
                        },
                    ),
                ),
            )

            // From the beat that reveals the two keys, through encryption.
            state.knows(RsaStepKind.KEY_REVEAL) -> FlowView(
                title = "ENCRYPTING",
                nodes = listOf(
                    plaintext(),
                    keyPublic,
                    operation("ENCRYPT", known = state.knows(RsaStepKind.ENCRYPT_KEY)),
                    bytes(known = state.knows(RsaStepKind.ENCRYPT_KEY)),
                ),
            )

            // The opening: a message, and nothing has happened to it yet.
            else -> FlowView(
                title = "THE MESSAGE",
                nodes = listOf(plaintext(lit = true)),
            )
        }
    }

    /**
     * The arithmetic behind the flow, revealed a line at a time.
     *
     * **The last line is withheld while that value is what is being asked for.** A
     * learner asked for `c` beside a panel ending `c = 9` is not being asked
     * anything — the rule the chain's `?` enforces, applied to a worked calculation
     * (ADR-030, ADR-052).
     *
     * Null until the flow it explains has been shown, which is what makes it the
     * *explanation* of something rather than the thing itself.
     */
    private fun maths(state: RsaState, problem: RsaProblem): MathsPanel? {
        // **Never in the concept layer** (ADR-053). The arithmetic belongs to the
        // toy layer, and putting `c = mᵉ mod n` beside `"MEET AT 7"` would be the
        // one claim this lesson has to refuse to make.
        if (!state.knows(RsaStepKind.TOY_EXAMPLE)) return null
        // Key generation replaces it with the chain, and the closing beat is the
        // journey alone — a worked calculation beside it would be three pictures on
        // one frame.
        if (state.knows(RsaStepKind.KEY_ORIGIN)) return null

        // Turns round on the pause beat, in step with the flow.
        val decrypting = state.knows(RsaStepKind.TOY_CIPHERTEXT)

        return if (decrypting) {
            val known = state.knows(RsaStepKind.DECRYPT)
            MathsPanel(
                title = "THE MATHEMATICS",
                lines = buildList {
                    add(MathsLine("m = c^d mod n", MathsEmphasis.FORMULA))
                    add(MathsLine("c = ${problem.ciphertext}", MathsEmphasis.SUBSTITUTION))
                    add(MathsLine("d = ${problem.d}", MathsEmphasis.SUBSTITUTION))
                    add(MathsLine("n = ${problem.modulus}", MathsEmphasis.SUBSTITUTION))
                    add(
                        MathsLine(
                            "m = ${problem.ciphertext}${superscript(problem.d)} " +
                                "mod ${problem.modulus}",
                            MathsEmphasis.STEP,
                        ),
                    )
                    if (known) {
                        add(MathsLine("m = ${problem.recovered}", MathsEmphasis.RESULT))
                    }
                },
            )
        } else {
            val known = state.knows(RsaStepKind.ENCRYPT)
            val unreduced = readablePower(problem.message, problem.e)
            MathsPanel(
                title = "THE MATHEMATICS",
                lines = buildList {
                    add(MathsLine("c = m^e mod n", MathsEmphasis.FORMULA))
                    add(MathsLine("m = ${problem.message}", MathsEmphasis.SUBSTITUTION))
                    add(MathsLine("e = ${problem.e}", MathsEmphasis.SUBSTITUTION))
                    add(MathsLine("n = ${problem.modulus}", MathsEmphasis.SUBSTITUTION))
                    add(
                        MathsLine(
                            "c = ${problem.message}${superscript(problem.e)} " +
                                "mod ${problem.modulus}",
                            MathsEmphasis.STEP,
                        ),
                    )
                    if (known) {
                        // `64 mod 55` is the step that makes `mod` mean something
                        // rather than being a symbol. Only shown once the answer is
                        // settled, because it gives the answer away to anyone who
                        // can subtract — which is the point of showing it at all.
                        if (unreduced != null) {
                            add(
                                MathsLine(
                                    "c = $unreduced mod ${problem.modulus}",
                                    MathsEmphasis.STEP,
                                ),
                            )
                        }
                        add(MathsLine("c = ${problem.ciphertext}", MathsEmphasis.RESULT))
                    }
                },
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
        // The compact `4 → 9 → 4` strip, and it belongs to the **toy layer only**
        // (ADR-053): those are the toy numbers, and showing them while the concept
        // layer is on screen is precisely the blur the two-layer split exists to
        // prevent. It sits under the toy flow — that is the step in detail, this is
        // the toy round trip at a glance — and stays for the key generation, where
        // it is the only reminder left of what `e` and `d` were for.
        if (!state.knows(RsaStepKind.TOY_EXAMPLE)) return null

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

    private companion object {
        /**
         * `4³`, `9²⁷` — the exponent as the superscript a textbook prints.
         *
         * The lesson's exponents are two digits at most (`RsaProblem.TOY_CEILING`
         * keeps the primes small, and `e < φ(n)`), so this never has to fall back to
         * a caret.
         */
        fun superscript(value: Long): String =
            value.toString().map { digit -> SUPERSCRIPTS[digit - '0'] }.joinToString("")

        private val SUPERSCRIPTS =
            listOf('⁰', '¹', '²', '³', '⁴', '⁵', '⁶', '⁷', '⁸', '⁹')

        /**
         * `base^exponent` unreduced, or null when it stops being a number a learner
         * can take in.
         *
         * Only used for the `c = 64 mod 55` line, whose whole job is to show the
         * reduction happening. On a key pair where the power is twenty digits that
         * line teaches nothing, so it is dropped rather than printed — the same
         * judgement `RsaOptions.power` makes about the matching distractor.
         */
        fun readablePower(base: Long, exponent: Long): Long? {
            var result = 1L
            repeat(exponent.toInt()) {
                result *= base
                if (result > READABLE_CEILING) return null
            }
            return result
        }

        private const val READABLE_CEILING = 1_000_000L

        /**
         * How many characters the text-to-numbers strip shows.
         *
         * Six columns of a character over a three-digit code is about as much as
         * 360dp holds while both rows stay readable, and the beat is about the
         * *correspondence* rather than about the whole message — four columns would
         * make the point just as well as forty.
         */
        private const val BRIDGE_CELLS = 6
    }
}
