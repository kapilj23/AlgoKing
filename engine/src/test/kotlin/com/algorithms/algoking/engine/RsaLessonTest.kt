package com.algorithms.algoking.engine

import com.algorithms.algoking.engine.algorithms.rsa.RsaAction
import com.algorithms.algoking.engine.algorithms.rsa.RsaEncryptionAlgorithm
import com.algorithms.algoking.engine.algorithms.rsa.RsaProjector
import com.algorithms.algoking.engine.algorithms.rsa.RsaState
import com.algorithms.algoking.engine.algorithms.rsa.RsaStepKind
import com.algorithms.algoking.engine.algorithms.rsa.RsaWatchNarrator
import com.algorithms.algoking.engine.catalog.AlgorithmCatalog
import com.algorithms.algoking.engine.challenge.ChallengeCatalog
import com.algorithms.algoking.engine.core.AlgorithmId
import com.algorithms.algoking.engine.core.AlgorithmRunner
import com.algorithms.algoking.engine.core.Dataset
import com.algorithms.algoking.engine.core.Probe
import com.algorithms.algoking.engine.core.RsaProblem
import com.algorithms.algoking.engine.core.RsaQuestion
import com.algorithms.algoking.engine.dataset.RsaDatasets
import com.algorithms.algoking.engine.decision.Decision
import com.algorithms.algoking.engine.decision.DecisionKind
import com.algorithms.algoking.engine.decision.DecisionValidation
import com.algorithms.algoking.engine.decision.Validation
import com.algorithms.algoking.engine.narration.NarrationId
import com.algorithms.algoking.engine.progress.AlgorithmProgress
import com.algorithms.algoking.engine.progress.Stage
import com.algorithms.algoking.engine.scene.CellState
import com.algorithms.algoking.engine.scene.FlowNodeKind
import com.algorithms.algoking.engine.scene.FlowValueStyle
import com.algorithms.algoking.engine.scene.KeyPairScene
import com.algorithms.algoking.engine.scene.LessonLayer
import com.algorithms.algoking.engine.scene.MathsEmphasis
import com.algorithms.algoking.engine.scene.RoundTripStage
import com.algorithms.algoking.engine.walkthrough.WatchScriptBuilder
import com.algorithms.algoking.engine.walkthrough.WatchStepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The RSA lesson — the state machine, the judgements, the picture and the
 * walkthrough.
 *
 * The arithmetic underneath it is checked separately, against `BigInteger` and
 * against a real 2048-bit key pair, in `RsaMathTest`.
 */
class RsaLessonTest {

    private val algorithm = RsaEncryptionAlgorithm()

    /**
     * The judgements whose options are sentences, and are therefore tapped on a
     * stacked card rather than a decision button.
     *
     * All six are concept judgements, which is not a coincidence: a question about
     * what RSA *does* cannot be answered with a number, and a question about which
     * number a formula produces does not need a paragraph.
     */
    private val CARD_QUESTIONS = setOf(
        RsaQuestion.ASYMMETRIC,
        RsaQuestion.SHAREABLE_KEY,
        RsaQuestion.SECRET_KEY,
        RsaQuestion.ENCRYPT_OPERATION,
        RsaQuestion.DECRYPT_OPERATION,
        RsaQuestion.ENCRYPT_KEY,
        RsaQuestion.DECRYPT_KEY,
        RsaQuestion.KEY_PAIR_PURPOSE,
    )

    /** The support line each card judgement puts on the frame that shows its cards. */
    private val CARD_INTROS = setOf(
        NarrationId.RSA_WATCH_ASK_ASYMMETRIC,
        NarrationId.RSA_WATCH_ASK_SHAREABLE_KEY,
        NarrationId.RSA_WATCH_ASK_SECRET_KEY,
        NarrationId.RSA_WATCH_ASK_ENCRYPT_OPERATION,
        NarrationId.RSA_WATCH_ASK_DECRYPT_OPERATION,
        NarrationId.RSA_WATCH_ASK_ENCRYPT_KEY,
        NarrationId.RSA_WATCH_ASK_DECRYPT_KEY,
        NarrationId.RSA_WATCH_ASK_KEY_PAIR_PURPOSE,
    )

    /** The judgements the authored lesson actually asks, as cards. */
    private val ASKED_CARD_QUESTIONS
        get() = RsaDatasets.EXERCISES.filter { it in CARD_QUESTIONS }

    private fun start(dataset: Dataset = RsaDatasets.watch): RsaState =
        algorithm.initial(dataset)

    /** Every decision the lesson asks, with the state it was asked from. */
    private fun decisions(
        dataset: Dataset = RsaDatasets.tryIt,
    ): List<Pair<RsaState, Decision<RsaAction>>> = buildList {
        var state = start(dataset)
        var guard = 0
        while (guard++ < 200) {
            when (val probe = algorithm.probe(state)) {
                is Probe.Terminal -> break
                is Probe.Mechanical -> state = algorithm.apply(state, probe.action).next
                is Probe.Decide -> {
                    add(state to probe.decision)
                    state = algorithm.apply(state, probe.decision.correct).next
                }
            }
        }
    }

    // ── 1. The lesson's shape ────────────────────────────────────────────────

    /**
     * The one test the whole re-ordering turns on (ADR-052).
     *
     * Every judgement a learner can answer **without any arithmetic** comes before
     * every judgement that needs some. If a future edit slides `MODULUS` back up to
     * second place, this is what says so.
     */
    @Test
    fun `the story is asked before the arithmetic`() {
        val story = setOf(
            RsaQuestion.ENCRYPT_OPERATION,
            RsaQuestion.ASYMMETRIC,
            RsaQuestion.ENCRYPT_KEY,
            RsaQuestion.DECRYPT_KEY,
        )
        val arithmetic = setOf(
            RsaQuestion.ENCRYPT,
            RsaQuestion.DECRYPT,
            RsaQuestion.MODULUS,
            RsaQuestion.TOTIENT,
            RsaQuestion.PUBLIC_EXPONENT,
            RsaQuestion.PRIVATE_EXPONENT,
        )

        val asked = RsaDatasets.EXERCISES
        val lastStory = asked.indexOfLast { it in story }
        val firstArithmetic = asked.indexOfFirst { it in arithmetic }

        assertTrue(
            "the arithmetic starts at $firstArithmetic, " +
                "but a story question is still being asked at $lastStory",
            firstArithmetic > lastStory,
        )

        // And the very first thing asked is about the message, not about a number.
        assertEquals(RsaQuestion.ENCRYPT_OPERATION, asked.first())
    }

    @Test
    fun `the twelve exercises are asked in the lesson's order`() {
        assertEquals(
            listOf(
                // Layer 1 — the concept, on a message.
                RsaQuestion.ENCRYPT_OPERATION,
                RsaQuestion.ASYMMETRIC,
                RsaQuestion.ENCRYPT_KEY,
                RsaQuestion.DECRYPT_KEY,
                RsaQuestion.SECRET_KEY,
                // Layer 2 — the mechanism.
                RsaQuestion.ENCRYPT,
                RsaQuestion.DECRYPT,
                RsaQuestion.MODULUS,
                RsaQuestion.TOTIENT,
                RsaQuestion.PUBLIC_EXPONENT,
                RsaQuestion.PRIVATE_EXPONENT,
                RsaQuestion.KEY_PAIR_PURPOSE,
            ),
            RsaDatasets.EXERCISES,
        )
        assertEquals(12, decisions().size)
    }

    /**
     * **The lesson's central honesty claim** (ADR-053), asserted on the scene rather
     * than on the copy.
     *
     * `n = 55` encrypts numbers below 55. It cannot encrypt *"MEET AT 7"*. So every
     * frame declares which layer it is on, the concept layer never carries a number,
     * and the toy layer never carries the message.
     */
    @Test
    fun `neither layer is ever mistaken for the other`() {
        var concept = 0
        var toy = 0

        AlgorithmRunner(algorithm, RsaDatasets.watch).runToCompletion().frames.forEach { frame ->
            val scene = RsaProjector().project(frame.state, frame.events)
            val problem = requireNotNull(RsaDatasets.watch.rsa)

            when (scene.layer) {
                LessonLayer.CONCEPT -> {
                    concept++
                    // No arithmetic reaches the concept layer — not the working,
                    // not the chain, not the toy round trip, not even the key pair.
                    assertNull("a formula in the concept layer", scene.maths)
                    assertNull("the toy round trip in the concept layer", scene.roundTrip)
                    assertTrue("the chain in the concept layer", scene.chain.isEmpty())
                    assertTrue(
                        "a key printed its numbers in the concept layer",
                        scene.keys.all { it.printed == null },
                    )
                    val printed = scene.flow?.nodes.orEmpty()
                        .mapNotNull { it.value }
                        .joinToString(" ")
                    assertFalse(
                        "the toy ciphertext appeared in the concept layer: $printed",
                        printed.contains(problem.ciphertext.toString()) &&
                            !printed.contains(problem.illustrativeCiphertext),
                    )
                }

                LessonLayer.TOY -> {
                    toy++
                    // The toy layer says what it is, every single frame.
                    assertTrue(
                        "the toy banner never claims to be secure",
                        "NOT SECURE" in LessonLayer.TOY.label,
                    )
                }

                // The bridge belongs to neither, and is the only frame that may
                // hold both a message and a number.
                null -> assertNotNull("the bridge draws the text strip", scene.bridge)
            }
        }

        assertTrue("the concept layer was never reached", concept > 0)
        assertTrue("the toy layer was never reached", toy > 0)
    }

    /**
     * The bridge beat exists, and it is the only place the two layers touch.
     *
     * Without it the toy layer's `m = 4` arrives from nowhere: a learner who has
     * spent nine beats on a message is owed an account of how RSA gets a number.
     */
    @Test
    fun `text becomes numbers exactly once, and says so`() {
        val bridges = scenes().mapNotNull { it.bridge }
        assertTrue("the bridge is never drawn", bridges.isNotEmpty())

        val strip = bridges.first()
        assertEquals(
            listOf("M" to 77, "E" to 69, "E" to 69, "T" to 84, "␣" to 32, "A" to 65),
            strip.cells.map { it.character to it.code },
        )
        assertTrue("the message was longer than the strip", strip.truncated)

        // And it is gone by the time the arithmetic starts, so no frame carries
        // three pictures.
        val withToyFlow = scenes().count { it.bridge != null && it.maths != null }
        assertEquals("the bridge outstayed its beat", 0, withToyFlow)
    }

    /**
     * The two the lesson deliberately states rather than asks.
     *
     * Both key pairs are on screen from the beat that hands them over, so asking
     * *"which pair is the public key?"* afterwards is a reading exercise. Their
     * machinery is all still present — this pins that it is unused rather than
     * broken, and that a dataset which does ask them still works.
     */
    @Test
    fun `the two key-assembly questions are stated, not asked`() {
        assertTrue(RsaQuestion.PUBLIC_KEY !in RsaDatasets.EXERCISES)
        assertTrue(RsaQuestion.PRIVATE_KEY !in RsaDatasets.EXERCISES)

        // …and are still fully built, for a dataset that wants them.
        val asking = Dataset(
            values = emptyList(),
            label = "both keys asked",
            rsa = RsaProblem(
                p = 5, q = 11, e = 3, message = 4,
                questions = listOf(RsaQuestion.PUBLIC_KEY, RsaQuestion.PRIVATE_KEY),
            ),
        )
        val asked = decisions(asking).map { it.first.question }
        assertEquals(listOf(RsaQuestion.PUBLIC_KEY, RsaQuestion.PRIVATE_KEY), asked)
    }

    @Test
    fun `WATCH and TRY ask the same exercises in the same order`() {
        assertEquals(
            requireNotNull(RsaDatasets.watch.rsa).questions,
            requireNotNull(RsaDatasets.tryIt.rsa).questions,
        )
    }

    /**
     * The house rule, and the reason TRY has its own key pair (ADR-014).
     *
     * Four of the twelve judgements are about a derived number, and two more are
     * about a value computed from one. If TRY reused WATCH's primes, every one of
     * them would be answerable from memory.
     */
    @Test
    fun `TRY cannot be answered from memory of WATCH`() {
        val watch = requireNotNull(RsaDatasets.watch.rsa)
        val tryIt = requireNotNull(RsaDatasets.tryIt.rsa)

        assertTrue("different primes", watch.p != tryIt.p && watch.q != tryIt.q)
        assertTrue("a different modulus", watch.modulus != tryIt.modulus)
        assertTrue("a different totient", watch.totient != tryIt.totient)
        assertTrue("a different public exponent", watch.e != tryIt.e)
        assertTrue("a different private exponent", watch.d != tryIt.d)
        assertTrue("a different ciphertext", watch.ciphertext != tryIt.ciphertext)
        // The message is deliberately the same, so the two runs are comparable.
        assertEquals(watch.message, tryIt.message)
    }

    @Test
    fun `a full run terminates and completes`() {
        val trace = AlgorithmRunner(algorithm, RsaDatasets.watch).runToCompletion()
        assertTrue(trace.frames.last().state.finished)
        assertEquals(0, trace.frames.last().state.remainingQuestions)
    }

    @Test
    fun `rewind is exact`() {
        val runner = AlgorithmRunner(algorithm, RsaDatasets.watch)
        val before = runner.current.state
        runner.apply(RsaAction.Advance)
        assertEquals(before, runner.rewind().state)
    }

    @Test
    fun `an out-of-range or ill-timed action is a no-op, never an exception`() {
        val settled = start()
        // The first beat is the app's, so an answer is not a thing to apply.
        assertEquals(settled, algorithm.apply(settled, RsaAction.Answer(0)).next)

        // Walk to a question, where Advance is the wrong action instead.
        var state = settled
        while (algorithm.probe(state) is Probe.Mechanical) {
            state = algorithm.apply(state, RsaAction.Advance).next
        }
        val atQuestion = state
        assertEquals(atQuestion, algorithm.apply(atQuestion, RsaAction.Advance).next)
        assertEquals(atQuestion, algorithm.apply(atQuestion, RsaAction.Answer(99)).next)
        assertEquals(atQuestion, algorithm.apply(atQuestion, RsaAction.Answer(-1)).next)

        // And past the end.
        var finished = start()
        var guard = 0
        while (guard++ < 200 && algorithm.probe(finished) !is Probe.Terminal) {
            finished = when (val probe = algorithm.probe(finished)) {
                is Probe.Mechanical -> algorithm.apply(finished, probe.action).next
                is Probe.Decide -> algorithm.apply(finished, probe.decision.correct).next
                is Probe.Terminal -> finished
            }
        }
        assertEquals(finished, algorithm.apply(finished, RsaAction.Advance).next)
    }

    // ── 2. The judgements ────────────────────────────────────────────────────

    @Test
    fun `every decision offers its correct answer, and none is auto-answered`() {
        decisions().forEach { (_, decision) ->
            assertTrue(
                "the correct action is on offer",
                decision.options.any { it.action == decision.correct },
            )
            assertFalse("a judgement is never the app's", decision.autoInTry)
            assertTrue("three rungs of guidance", decision.guidance.size >= 3)
            assertTrue("at least two options", decision.options.size >= 2)
        }
    }

    @Test
    fun `no judgement offers the same option twice`() {
        listOf(RsaDatasets.watch, RsaDatasets.tryIt).forEach { dataset ->
            decisions(dataset).forEach { (state, decision) ->
                val labels = decision.options.map { it.label }
                assertEquals(
                    "${state.question} offered a duplicate: $labels",
                    labels.size,
                    labels.distinct().size,
                )
            }
        }
    }

    @Test
    fun `every wrong option is explained by name`() {
        listOf(RsaDatasets.watch, RsaDatasets.tryIt).forEach { dataset ->
            decisions(dataset).forEach { (state, decision) ->
                decision.options
                    .filter { it.action != decision.correct }
                    .forEach { option ->
                        assertNotNull(
                            "${state.question} has no whyWrong for ${option.label.id}",
                            decision.whyWrong[option.action],
                        )
                    }
            }
        }
    }

    /**
     * A learner who noticed the answer was always in the same seat could finish
     * without reading anything — the failure PRODUCT_SPEC.md §5 exists to prevent.
     */
    @Test
    fun `the correct answer moves between seats`() {
        val seats = decisions().map { (_, decision) ->
            decision.options.indexOfFirst { it.action == decision.correct }
        }
        assertTrue("the correct seat moves: $seats", seats.distinct().size > 1)
    }

    /** The two wordy judgements are tapped on cards; the rest are buttons. */
    @Test
    fun `sentence options are cards and number options are buttons`() {
        decisions().forEach { (state, decision) ->
            val expected = when (state.question) {
                // Every concept judgement — all six of them now (ADR-052) — offers
                // sentences, and a sentence does not fit on a decision button.
                in CARD_QUESTIONS -> DecisionKind.CELL
                else -> DecisionKind.OPTIONS
            }
            assertEquals("${state.question}", expected, decision.kind)
            if (expected == DecisionKind.CELL) {
                assertEquals(
                    "a card carries the slot it selects",
                    decision.options.indices.toList(),
                    decision.options.map { it.slot },
                )
            } else {
                assertTrue(
                    "a button carries no slot",
                    decision.options.all { it.slot == null },
                )
            }
        }
    }

    /** Each answer is the value the formula produces, on both datasets. */
    @Test
    fun `every answer is what the arithmetic gives`() {
        listOf(RsaDatasets.watch, RsaDatasets.tryIt).forEach { dataset ->
            val problem = requireNotNull(dataset.rsa)
            decisions(dataset).forEach { (state, decision) ->
                val chosen = decision.options
                    .first { it.action == decision.correct }
                    .label
                    .args

                fun expect(vararg args: Any) =
                    assertEquals("${dataset.label} ${state.question}", args.toList(), chosen)

                when (state.question) {
                    RsaQuestion.MODULUS -> expect(problem.modulus)
                    RsaQuestion.TOTIENT -> expect(problem.totient)
                    RsaQuestion.PUBLIC_EXPONENT -> expect(problem.e)
                    RsaQuestion.PRIVATE_EXPONENT -> expect(problem.d)
                    RsaQuestion.PUBLIC_KEY -> expect(problem.e, problem.modulus)
                    RsaQuestion.PRIVATE_KEY -> expect(problem.d, problem.modulus)
                    RsaQuestion.ENCRYPT -> expect(problem.ciphertext)
                    RsaQuestion.DECRYPT -> expect(problem.message)
                    // The concept judgements carry a word, not a number.
                    RsaQuestion.ASYMMETRIC,
                    RsaQuestion.SECRET_KEY,
                    RsaQuestion.SHAREABLE_KEY,
                    RsaQuestion.ENCRYPT_OPERATION,
                    RsaQuestion.DECRYPT_OPERATION,
                    RsaQuestion.ENCRYPT_KEY,
                    RsaQuestion.DECRYPT_KEY,
                    RsaQuestion.KEY_PAIR_PURPOSE,
                    null,
                    -> Unit
                }
            }
        }
    }

    /** The brief's own numbers, as the lesson asks them. */
    @Test
    fun `the WATCH run asks for 9, 4, 55, 40, 3 and 27`() {
        val answers = decisions(RsaDatasets.watch).associate { (state, decision) ->
            state.question to decision.options
                .first { it.action == decision.correct }
                .label.args
        }
        // Act I — the two the story is made of.
        assertEquals(listOf(9L), answers[RsaQuestion.ENCRYPT])
        assertEquals(listOf(4L), answers[RsaQuestion.DECRYPT])

        // Act II — the chain that built the keys.
        assertEquals(listOf(55L), answers[RsaQuestion.MODULUS])
        assertEquals(listOf(40L), answers[RsaQuestion.TOTIENT])
        assertEquals(listOf(3L), answers[RsaQuestion.PUBLIC_EXPONENT])
        assertEquals(listOf(27L), answers[RsaQuestion.PRIVATE_EXPONENT])

        // The pair itself is stated rather than asked — see the test above.
        assertNull(answers[RsaQuestion.PUBLIC_KEY])
        assertNull(answers[RsaQuestion.PRIVATE_KEY])

        // And the toy key pair on the picture is still the brief's.
        val problem = requireNotNull(RsaDatasets.watch.rsa)
        assertEquals("(3, 55)", problem.publicKey.printed)
        assertEquals("(27, 55)", problem.privateKey.printed)
    }

    /** The distractors the brief lists, which fall out of the formulas. */
    @Test
    fun `the modulus and totient distractors are the ones the brief names`() {
        val byQuestion = decisions(RsaDatasets.watch).associate { (state, decision) ->
            state.question to decision.options.flatMap { it.label.args }.toSet()
        }
        assertEquals(setOf(55L, 40L, 16L, 44L), byQuestion[RsaQuestion.MODULUS])
        assertEquals(setOf(40L, 55L, 44L, 10L), byQuestion[RsaQuestion.TOTIENT])
    }

    /** Every wrong exponent fails the one test the beat is about. */
    @Test
    fun `every rejected public exponent shares a factor with the totient`() {
        listOf(RsaDatasets.watch, RsaDatasets.tryIt).forEach { dataset ->
            val problem = requireNotNull(dataset.rsa)
            val (_, decision) = decisions(dataset)
                .first { it.first.question == RsaQuestion.PUBLIC_EXPONENT }

            decision.options
                .filter { it.action != decision.correct }
                .forEach { option ->
                    val value = option.label.args.first() as Long
                    assertTrue(
                        "gcd($value, ${problem.totient}) should not be 1",
                        com.algorithms.algoking.engine.core.Rsa
                            .gcd(value, problem.totient) != 1L,
                    )
                }
        }
    }

    /** The "forgot the modulus" distractor is offered, and it is the raw power. */
    @Test
    fun `the encryption beat offers the unreduced power as a wrong answer`() {
        val (_, decision) = decisions(RsaDatasets.watch)
            .first { it.first.question == RsaQuestion.ENCRYPT }
        val values = decision.options.map { it.label.args.first() as Long }
        assertTrue("4³ = 64 is on the table", 64L in values)
        assertTrue("and the answer is 9", 9L in values)
    }

    // ── 3. A wrong answer is a learning event, never a state transition ──────

    private fun firstDecision(): Pair<RsaState, Decision<RsaAction>> =
        decisions(RsaDatasets.tryIt).first { it.first.question == RsaQuestion.MODULUS }

    @Test
    fun `five wrong answers leave the state byte-for-byte identical`() {
        val (state, decision) = firstDecision()
        val wrong = decision.options.first { it.action != decision.correct }.action

        repeat(5) { attempt ->
            val verdict = DecisionValidation.validate(decision, wrong, attempt)
            assertTrue("a wrong answer is a Retry", verdict is Validation.Retry)
            // A Retry carries no action, so there is nothing the caller could apply.
            assertEquals("the state never moved", state, state)
        }
        // And the state really is unchanged: nothing was applied.
        assertEquals(RsaQuestion.MODULUS, state.question)
    }

    @Test
    fun `guidance escalates and then holds`() {
        val (_, decision) = firstDecision()
        val wrong = decision.options.first { it.action != decision.correct }.action
        val rungs = (0 until 6).map {
            (DecisionValidation.validate(decision, wrong, it) as Validation.Retry).guidance
        }
        assertEquals(decision.guidance[0], rungs[0])
        assertEquals(decision.guidance[1], rungs[1])
        assertEquals(decision.guidance[2], rungs[2])
        // Past the end the most explicit rung repeats — never a dead end.
        assertEquals(decision.guidance.last(), rungs[3])
        assertEquals(decision.guidance.last(), rungs[5])
    }

    @Test
    fun `retry works - a run guessed wrongly at every beat still completes`() {
        var state = start(RsaDatasets.tryIt)
        var refused = 0
        var guard = 0

        while (guard++ < 200) {
            when (val probe = algorithm.probe(state)) {
                is Probe.Terminal -> break
                is Probe.Mechanical -> state = algorithm.apply(state, probe.action).next
                is Probe.Decide -> {
                    val decision = probe.decision
                    val wrong = decision.options.first { it.action != decision.correct }
                    // Every wrong answer is refused, and refusing it applies nothing.
                    assertTrue(
                        DecisionValidation.validate(decision, wrong.action, 0)
                            is Validation.Retry,
                    )
                    refused++
                    val accepted = DecisionValidation.validate(decision, decision.correct, 3)
                    assertTrue(accepted is Validation.Accept)
                    state = algorithm.apply(state, (accepted as Validation.Accept).action).next
                }
            }
        }
        assertEquals("every decision was guessed at first", 12, refused)
        assertTrue(state.finished)
    }

    @Test
    fun `a wrong answer applied directly still leaves a terminating run`() {
        var state = start(RsaDatasets.tryIt)
        var guard = 0
        while (guard++ < 200) {
            when (val probe = algorithm.probe(state)) {
                is Probe.Terminal -> break
                is Probe.Mechanical -> state = algorithm.apply(state, probe.action).next
                is Probe.Decide -> {
                    val wrong = probe.decision.options
                        .first { it.action != probe.decision.correct }.action
                    state = algorithm.apply(state, wrong).next
                }
            }
        }
        assertTrue("the run still terminates", state.finished)
    }

    @Test
    fun `adversarial driving always terminates`() {
        repeat(40) { seed ->
            var state = start(if (seed % 2 == 0) RsaDatasets.watch else RsaDatasets.tryIt)
            val random = kotlin.random.Random(seed.toLong())
            var guard = 0
            while (guard++ < 2_000 && algorithm.probe(state) !is Probe.Terminal) {
                val action = if (random.nextBoolean()) {
                    RsaAction.Advance
                } else {
                    RsaAction.Answer(random.nextInt(-2, 6))
                }
                state = algorithm.apply(state, action).next
            }
            assertTrue("seed $seed terminated", algorithm.probe(state) is Probe.Terminal)
        }
    }

    // ── 4. The picture ───────────────────────────────────────────────────────

    private fun scenes(dataset: Dataset = RsaDatasets.watch): List<KeyPairScene> {
        val projector = RsaProjector()
        return AlgorithmRunner(algorithm, dataset)
            .runToCompletion()
            .frames
            .map { projector.project(it.state, it.events) }
    }

    @Test
    fun `the caveat is on screen for the whole lesson`() {
        scenes().forEach { scene ->
            assertNotNull("the caveat never leaves", scene.caveat)
            assertTrue(requireNotNull(scene.caveat).isNotBlank())
        }
    }

    /**
     * **Act I never shows a symbol.** The point of the re-ordering (ADR-052).
     *
     * Not *"the chain holds question marks"* — the chain is not drawn at all, because
     * five rows of `?` would put `p`, `q`, `φ(n)`, `e` and `d` on the first screen
     * wearing a disguise, which is the thing being fixed.
     */
    @Test
    fun `no symbol is on screen until the lesson asks where the keys came from`() {
        var sawStory = false

        AlgorithmRunner(algorithm, RsaDatasets.watch).runToCompletion().frames.forEach { frame ->
            val scene = RsaProjector().project(frame.state, frame.events)
            if (frame.state.knows(RsaStepKind.KEY_ORIGIN)) return@forEach

            sawStory = true
            assertTrue(
                "the derivation chain is Act II's picture, not Act I's",
                scene.chain.isEmpty(),
            )
            // Nor may any of the five leak in through the flow or the arithmetic.
            val printed = (scene.flow?.nodes?.map { "${it.label} ${it.value}" }.orEmpty() +
                scene.maths?.lines?.map { it.text }.orEmpty()).joinToString(" ")
            listOf("φ", "p ×", "(p −", "gcd", "prime").forEach { symbol ->
                assertFalse("Act I printed \"$symbol\": $printed", printed.contains(symbol))
            }
        }

        assertTrue("Act I was never reached", sawStory)
    }

    @Test
    fun `key generation draws the full chain, and the flow gives way to it`() {
        val full = scenes().last { it.chain.isNotEmpty() }
        assertEquals(5, full.chain.size)
        assertEquals(
            listOf("p, q", "n", "φ(n)", "e", "d"),
            full.chain.map { it.symbol },
        )
        assertTrue("the chain never finished", full.chain.all { it.known })

        // The two pictures are answers to two different questions, and are never on
        // screen together.
        scenes().forEach { scene ->
            assertFalse(
                "the flow and the chain were drawn at the same time",
                scene.chain.isNotEmpty() && scene.flow != null,
            )
        }
    }

    /**
     * The concept layer's picture: a message becoming unreadable bytes, and coming
     * back — with the same shape reused for the toy numbers afterwards.
     *
     * The flow only ever moves forward, and each stage's nodes are the ones the
     * brief asks for.
     */
    @Test
    fun `the flow walks the message out through one key and back through the other`() {
        val titles = scenes().mapNotNull { it.flow?.title }.distinct()
        assertEquals(
            listOf(
                "THE MESSAGE",
                "ENCRYPTING",
                "DECRYPTING",
                "THE WHOLE JOURNEY",
                "TOY EXAMPLE - ENCRYPTING",
                "TOY EXAMPLE - DECRYPTING",
            ),
            titles,
        )

        val encrypting = scenes().last { it.flow?.title == "ENCRYPTING" }.flow!!
        assertEquals(
            listOf("MESSAGE", "PUBLIC KEY", "ENCRYPT", "CIPHERTEXT"),
            encrypting.nodes.map { it.label },
        )

        val decrypting = scenes().last { it.flow?.title == "DECRYPTING" }.flow!!
        assertEquals(
            listOf("CIPHERTEXT", "PRIVATE KEY", "DECRYPT", "MESSAGE"),
            decrypting.nodes.map { it.label },
        )

        // "MEET AT 7" -> bytes -> "MEET AT 7", on one picture, in the concept layer
        // and with no numbers on it.
        val journey = scenes().first { it.flow?.title == "THE WHOLE JOURNEY" }.flow!!
        assertEquals(
            listOf("MEET AT 7", null, null, "8F 3A C1 D4 9B 22", null, null, "MEET AT 7"),
            journey.nodes.map { it.value },
        )
        // And the message is styled as prose, the ciphertext as bytes — a learner
        // must not read one as the other (ADR-053).
        assertEquals(
            FlowValueStyle.TEXT,
            journey.nodes.first { it.kind == FlowNodeKind.MESSAGE }.style,
        )
        assertEquals(
            FlowValueStyle.BYTES,
            journey.nodes.first { it.kind == FlowNodeKind.CIPHERTEXT }.style,
        )

        // The toy layer reuses the same shape with numbers in it — which is the
        // claim about the two layers, drawn rather than asserted.
        val toy = scenes().last { it.flow?.title == "TOY EXAMPLE - DECRYPTING" }.flow!!
        assertEquals(listOf("9", "(27, 55)", null, "4"), toy.nodes.map { it.value })
        assertTrue(
            "the toy flow is drawn as numerals",
            toy.nodes.filter { it.value != null }.all { it.style == FlowValueStyle.NUMBER },
        )
    }

    /**
     * The verb node is blank while the learner is being asked which key does the
     * work.
     *
     * The chain's `?` rule, applied to a flow — and the thing that makes the two key
     * judgements askable at all.
     */
    @Test
    fun `the flow never names the operation it is asking about`() {
        val projector = RsaProjector()
        var state = start(RsaDatasets.tryIt)
        var guard = 0
        var asked = 0

        while (guard++ < 200) {
            val probe = algorithm.probe(state)
            if (probe is Probe.Terminal) break

            val scene = projector.project(state, emptyList())
            if (state.question == RsaQuestion.ENCRYPT_KEY ||
                state.question == RsaQuestion.DECRYPT_KEY
            ) {
                val verb = requireNotNull(scene.flow).nodes
                    .single { it.kind == FlowNodeKind.OPERATION }
                assertEquals("the verb is what is being asked for", "?", verb.label)
                asked++
            }

            state = when (probe) {
                is Probe.Mechanical -> algorithm.apply(state, probe.action).next
                is Probe.Decide -> algorithm.apply(state, probe.decision.correct).next
                is Probe.Terminal -> state
            }
        }
        assertEquals("both key judgements were reached", 2, asked)
    }

    /**
     * The arithmetic arrives **after** the transformation it explains, and its last
     * line is withheld while that value is the question.
     */
    @Test
    fun `the mathematics explains what was shown, and never answers the question`() {
        val projector = RsaProjector()
        var state = start(RsaDatasets.watch)
        var guard = 0
        var checked = 0

        while (guard++ < 200) {
            val probe = algorithm.probe(state)
            if (probe is Probe.Terminal) break
            val scene = projector.project(state, emptyList())

            // Nothing arithmetic before the lesson has shown a key doing work.
            if (!state.knows(RsaStepKind.ENCRYPT_OPERATION)) {
                assertNull("no formula before the flow it explains", scene.maths)
            }

            when (state.question) {
                RsaQuestion.ENCRYPT -> {
                    val lines = requireNotNull(scene.maths).lines
                    assertEquals("c = m^e mod n", lines.first().text)
                    assertTrue(lines.any { it.text == "c = 4³ mod 55" })
                    assertTrue(
                        "the answer is what is being asked for",
                        lines.none { it.emphasis == MathsEmphasis.RESULT },
                    )
                    assertFalse(lines.any { it.text.contains("= 9") })
                    checked++
                }

                RsaQuestion.DECRYPT -> {
                    val lines = requireNotNull(scene.maths).lines
                    assertEquals("m = c^d mod n", lines.first().text)
                    assertTrue(lines.any { it.text == "m = 9²⁷ mod 55" })
                    assertTrue(lines.none { it.emphasis == MathsEmphasis.RESULT })
                    checked++
                }

                else -> Unit
            }

            state = when (probe) {
                is Probe.Mechanical -> algorithm.apply(state, probe.action).next
                is Probe.Decide -> algorithm.apply(state, probe.decision.correct).next
                is Probe.Terminal -> state
            }
        }
        assertEquals(2, checked)

        // And once settled, the full working is there — including the reduction,
        // which is the line that makes `mod` mean something.
        val settled = requireNotNull(scenes().first { scene ->
            scene.maths?.lines?.any { it.text == "c = 9" } == true
        }.maths)
        assertEquals(
            listOf(
                "c = m^e mod n", "m = 4", "e = 3", "n = 55",
                "c = 4³ mod 55", "c = 64 mod 55", "c = 9",
            ),
            settled.lines.map { it.text },
        )
    }

    /**
     * The rule the whole design turns on: a value the lesson has not produced is
     * absent, so the picture can never answer the question being asked.
     */
    @Test
    fun `a value is never drawn before the beat that produces it`() {
        val projector = RsaProjector()
        var state = start(RsaDatasets.tryIt)
        var guard = 0

        while (guard++ < 200) {
            val probe = algorithm.probe(state)
            if (probe is Probe.Terminal) break

            val scene = projector.project(state, emptyList())
            val symbolFor = mapOf(
                RsaQuestion.MODULUS to "n",
                RsaQuestion.TOTIENT to "φ(n)",
                RsaQuestion.PUBLIC_EXPONENT to "e",
                RsaQuestion.PRIVATE_EXPONENT to "d",
            )
            symbolFor[state.question]?.let { symbol ->
                val link = scene.chain.first { it.symbol == symbol }
                assertNull("$symbol is what is being asked for", link.value)
                assertEquals(CellState.COMPARING, link.state)
            }
            // The keys are handed over as given at the start of Act I, and nothing
            // draws them before that beat.
            if (!state.knows(RsaStepKind.KEY_REVEAL)) {
                assertTrue("no key card yet", scene.keys.isEmpty())
                assertTrue(
                    "no key in the flow yet",
                    scene.flow?.nodes.orEmpty().none {
                        it.kind == FlowNodeKind.PUBLIC_KEY ||
                            it.kind == FlowNodeKind.PRIVATE_KEY
                    },
                )
            }

            state = when (probe) {
                is Probe.Mechanical -> algorithm.apply(state, probe.action).next
                is Probe.Decide -> algorithm.apply(state, probe.decision.correct).next
                is Probe.Terminal -> state
            }
        }
    }

    @Test
    fun `the chain fills in order, and nothing is skipped`() {
        // Up to the closing beat, which puts the journey back on screen and takes
        // the chain down — by then it has been explained, and three pictures on one
        // frame is not a summary.
        val upToClosing = AlgorithmRunner(algorithm, RsaDatasets.watch)
            .runToCompletion()
            .frames
            .takeWhile { !it.state.knows(RsaStepKind.CLOSING_FLOW) }
            .map { RsaProjector().project(it.state, it.events) }

        val known = upToClosing.map { scene -> scene.chain.count { it.known } }
        // Monotonic: a value that has been derived never disappears.
        known.zipWithNext().forEach { (a, b) ->
            assertTrue("the chain went backwards: $known", b >= a)
        }
        assertEquals("all five by the end", 5, known.last())
        assertEquals("none at the start", 0, known.first())

        // The beat that asks where the keys came from draws the empty outline —
        // five rows, no values — which is what makes it a beat rather than a jump
        // straight into `p × q`.
        val origin = scenes().first { it.chain.isNotEmpty() }
        assertEquals(5, origin.chain.size)
        assertTrue("the outline is empty", origin.chain.none { it.known })
    }

    @Test
    fun `both keys are drawn together once they exist, and only one is secret`() {
        val finished = scenes().last()
        assertEquals(2, finished.keys.size)
        assertEquals(listOf("(3, 55)", "(27, 55)"), finished.keys.map { it.printed })
        assertEquals(listOf(false, true), finished.keys.map { it.secret })
        // Both halves share the modulus, which is what the picture must show.
        assertTrue(finished.keys.all { it.printed?.endsWith(", 55)") == true })
    }

    /**
     * The keys carry **no numbers at all** while the concept layer is on screen
     * (ADR-053).
     *
     * A card printing `(3, 55)` there would put the lesson's first arithmetic on the
     * one stretch of it whose whole job is to carry none. The card still says which
     * half it is, whether it may be shared, and what it does.
     */
    @Test
    fun `the keys are a lock and a key before they are a pair of numbers`() {
        var sawConcept = false
        var sawNumbered = false

        AlgorithmRunner(algorithm, RsaDatasets.watch).runToCompletion().frames.forEach { frame ->
            val scene = RsaProjector().project(frame.state, frame.events)
            if (scene.keys.isEmpty()) return@forEach

            if (frame.state.knows(RsaStepKind.TOY_EXAMPLE)) {
                sawNumbered = true
                assertTrue(
                    "the toy layer prints the pair",
                    scene.keys.all { it.printed != null },
                )
            } else {
                sawConcept = true
                assertTrue(
                    "a key card printed numbers in the concept layer",
                    scene.keys.all { it.printed == null },
                )
                // …and the flow's key nodes are just as bare.
                assertTrue(
                    "a flow key node printed numbers in the concept layer",
                    scene.flow?.nodes.orEmpty()
                        .filter {
                            it.kind == FlowNodeKind.PUBLIC_KEY ||
                                it.kind == FlowNodeKind.PRIVATE_KEY
                        }
                        .all { it.value == null },
                )
            }
        }

        assertTrue("the concept layer never showed the keys", sawConcept)
        assertTrue("the toy layer never numbered them", sawNumbered)
    }

    @Test
    fun `the round trip goes ready, encrypted, then decrypted`() {
        val stages = scenes().mapNotNull { it.roundTrip?.stage }.distinct()
        assertEquals(
            listOf(
                RoundTripStage.READY,
                RoundTripStage.ENCRYPTED,
                RoundTripStage.DECRYPTED,
            ),
            stages,
        )

        val finished = requireNotNull(scenes().last().roundTrip)
        assertEquals(4L, finished.message)
        assertEquals(9L, finished.ciphertext)
        // Read out of a real decryption, not copied from the message.
        assertEquals(4L, finished.recovered)
    }

    @Test
    fun `the ciphertext is not drawn before it is computed`() {
        val projector = RsaProjector()
        AlgorithmRunner(algorithm, RsaDatasets.watch).runToCompletion().frames.forEach { frame ->
            val scene = projector.project(frame.state, frame.events)
            val trip = scene.roundTrip ?: return@forEach
            if (!frame.state.knows(RsaStepKind.ENCRYPT)) {
                assertNull("no ciphertext yet", trip.ciphertext)
            }
            if (!frame.state.knows(RsaStepKind.DECRYPT)) {
                assertNull("nothing recovered yet", trip.recovered)
            }
        }
    }

    @Test
    fun `the cards appear only while a card question is live`() {
        val projector = RsaProjector()
        var state = start(RsaDatasets.tryIt)
        var guard = 0
        var seen = 0

        while (guard++ < 200) {
            val probe = algorithm.probe(state)
            if (probe is Probe.Terminal) break
            val scene = projector.project(state, emptyList())
            val cardQuestion = state.question in CARD_QUESTIONS
            if (cardQuestion) {
                assertEquals("four cards", 4, scene.choices.size)
                assertTrue(scene.choices.all { it.title.isNotBlank() })
                assertTrue(scene.choices.all { it.detail.isNotBlank() })
                // The slots are what a CELL option selects.
                assertEquals(scene.choices.indices.toList(), scene.choices.map { it.slot })
                seen++
            } else {
                assertTrue("no cards here", scene.choices.isEmpty())
            }
            state = when (probe) {
                is Probe.Mechanical -> algorithm.apply(state, probe.action).next
                is Probe.Decide -> algorithm.apply(state, probe.decision.correct).next
                is Probe.Terminal -> state
            }
        }
        assertEquals("every card question was reached", ASKED_CARD_QUESTIONS.size, seen)
    }

    // ── 5. The walkthrough ───────────────────────────────────────────────────

    private fun script() = AlgorithmCatalog.rsa().watchScript()

    @Test
    fun `WATCH opens on the idea and closes on it`() {
        val steps = script().steps
        assertEquals(WatchStepKind.SETUP, steps.first().kind)
        assertEquals(WatchStepKind.SUMMARY, steps.last().kind)
        assertEquals(WatchStepKind.INSIGHT, steps[steps.size - 2].kind)
    }

    @Test
    fun `WATCH is long enough to teach and short enough to finish`() {
        // Wider than it was, and deliberately so (ADR-052). Telling the story and
        // *then* explaining the arithmetic costs beats that showing the arithmetic
        // alone did not — the round trip, the two operation judgements, the pause on
        // the ciphertext and the beat that asks where the keys came from. Every one
        // of them is a real visual change, which is the bar ADR-020 sets.
        val size = script().size
        assertTrue("$size beats", size in 24..32)
    }

    /**
     * WATCH tells the story on a message before it shows any arithmetic — ADR-053,
     * on the walkthrough rather than on the question list.
     *
     * The complete round trip has to land before a single number is spoken.
     */
    @Test
    fun `WATCH completes the message round trip before any arithmetic`() {
        val ids = script().steps.map { it.headline.id }

        val roundTrip = ids.indexOf(NarrationId.RSA_WATCH_ROUND_TRIP)
        val bridge = ids.indexOf(NarrationId.RSA_WATCH_TEXT_AS_NUMBERS)
        val toy = ids.indexOf(NarrationId.RSA_WATCH_TOY_EXAMPLE)
        val encrypt = ids.indexOf(NarrationId.RSA_WATCH_ENCRYPT)
        val keyOrigin = ids.indexOf(NarrationId.RSA_WATCH_KEY_ORIGIN)
        val primes = ids.indexOf(NarrationId.RSA_WATCH_PRIMES)

        listOf(roundTrip, bridge, toy, encrypt, keyOrigin, primes).forEach {
            assertTrue("a required beat is missing: $ids", it >= 0)
        }

        // The concept round trip, then the bridge, then the toy layer announcing
        // itself, then the arithmetic, then key generation. In that order.
        assertTrue("the bridge comes before the round trip closes", bridge > roundTrip)
        assertTrue("the toy layer is not announced before it starts", toy > bridge)
        assertTrue("arithmetic before the toy layer announced itself", encrypt > toy)
        assertTrue("key generation before the arithmetic", keyOrigin > encrypt)
        assertTrue("the primes arrive before they are motivated", primes > keyOrigin)

        // And every concept beat lands before the bridge.
        listOf(
            NarrationId.RSA_WATCH_MESSAGE,
            NarrationId.RSA_WATCH_ENCRYPT_OPERATION,
            NarrationId.RSA_WATCH_KEY_REVEAL,
            NarrationId.RSA_WATCH_ASYMMETRIC,
            NarrationId.RSA_WATCH_ENCRYPT_KEY,
            NarrationId.RSA_WATCH_CIPHERTEXT,
            NarrationId.RSA_WATCH_DECRYPT_KEY,
            NarrationId.RSA_WATCH_RECOVERED,
        ).forEach { id ->
            val at = ids.indexOf(id)
            assertTrue("$id is never said", at >= 0)
            assertTrue("$id lands after the concept layer is over", at < bridge)
        }

        // And it finishes where it started: the journey, then the caveat.
        assertTrue(
            "the lesson never returns to the picture it opened on",
            ids.indexOf(NarrationId.RSA_WATCH_CLOSING_FLOW) > keyOrigin,
        )
    }

    /**
     * The statement path, which this lesson's own datasets never take.
     *
     * A card question's beat is normally captioned together with the cards it puts
     * on screen. A dataset that does not *ask* it turns the beat into a statement,
     * and then the beat's own caption is what runs — so it has to exist and be
     * correct. This is what keeps those six captions honest rather than dead.
     */
    @Test
    fun `a dataset that asks nothing still narrates every beat`() {
        val silent = Dataset(
            values = emptyList(),
            label = "no judgements",
            rsa = RsaProblem(
                p = 5, q = 11, e = 3, message = 4,
                // One question, so the problem is legal — and not a card one.
                questions = listOf(RsaQuestion.MODULUS),
            ),
        )
        val steps = WatchScriptBuilder(algorithm, RsaProjector(), RsaWatchNarrator())
            .build(silent)
            .steps
        val ids = steps.map { it.headline.id }

        listOf(
            NarrationId.RSA_WATCH_MESSAGE,
            NarrationId.RSA_WATCH_ENCRYPT_OPERATION,
            NarrationId.RSA_WATCH_KEY_REVEAL,
            NarrationId.RSA_WATCH_ASYMMETRIC,
            NarrationId.RSA_WATCH_ENCRYPT_KEY,
            NarrationId.RSA_WATCH_CIPHERTEXT,
            NarrationId.RSA_WATCH_DECRYPT_KEY,
            NarrationId.RSA_WATCH_SECRET_KEY,
            NarrationId.RSA_WATCH_KEY_PAIR_PURPOSE,
        ).forEach { assertTrue("$it has no statement caption", it in ids) }

        // And every one of them carries its own support, rather than a card intro.
        steps.forEach { step ->
            assertFalse(
                "a card intro leaked into a run that asks nothing",
                step.support?.id in CARD_INTROS,
            )
        }
    }

    /** ADR-020: a step where nothing changed is a bug, not a beat. */
    @Test
    fun `no two adjacent WATCH steps are identical`() {
        script().steps.zipWithNext().forEach { (a, b) ->
            assertFalse(
                "steps ${a.index} and ${b.index} are the same beat",
                a.scene == b.scene && a.headline == b.headline && a.support == b.support,
            )
        }
    }

    /**
     * Every link in the chain gets a beat of its own.
     *
     * There is nothing to collapse here — five values, five different ideas — which
     * is why ADR-025's rule does not apply the way it does to AES's ten rounds.
     */
    @Test
    fun `every link in the chain is narrated`() {
        val ids = script().steps.map { it.headline.id }
        listOf(
            NarrationId.RSA_WATCH_PRIMES,
            NarrationId.RSA_WATCH_MODULUS,
            NarrationId.RSA_WATCH_TOTIENT,
            NarrationId.RSA_WATCH_PUBLIC_EXPONENT,
            NarrationId.RSA_WATCH_PRIVATE_EXPONENT,
            NarrationId.RSA_WATCH_PUBLIC_KEY,
            NarrationId.RSA_WATCH_PRIVATE_KEY,
            NarrationId.RSA_WATCH_ENCRYPT,
            NarrationId.RSA_WATCH_DECRYPT,
            NarrationId.RSA_WATCH_REAL_WORLD,
        ).forEach { assertTrue("$it is never said", it in ids) }
    }

    /**
     * The bug the walkthrough dump caught, guarded.
     *
     * A frame is drawn from the state *after* its transition, so the frame that
     * passes a beat is also the frame the next question is pending on. For a
     * judgement answered by tapping cards that means the cards land on the previous
     * beat's frame — and the first draft captioned the secrecy cards with the
     * round-trip sentence while the beat that was about them showed nothing.
     *
     * The rule now: **a frame with cards on it is captioned by both** — the landed
     * beat's headline, and a support line that introduces the choice. Six judgements
     * take that path (ADR-052), and every one of them has to, or the beat it sits on
     * loses its sentence.
     */
    @Test
    fun `each card beat is captioned with the picture that shows its cards`() {
        val steps = script().steps
        val withCards = steps.filter { (it.scene as KeyPairScene).choices.isNotEmpty() }

        assertEquals(
            "one captioned frame per card judgement",
            ASKED_CARD_QUESTIONS.size,
            withCards.size,
        )

        withCards.forEach { step ->
            val scene = step.scene as KeyPairScene
            assertEquals("step ${step.index} shows four cards", 4, scene.choices.size)
            // The support introduces the cards…
            assertTrue(
                "step ${step.index} draws cards without introducing them",
                step.support?.id in CARD_INTROS,
            )
            // …and the headline still says what just landed, rather than being
            // sacrificed to them.
            assertFalse(
                "step ${step.index} lost the landed beat's headline",
                step.headline.id in CARD_INTROS,
            )
        }

        // Every intro belongs to a card judgement, and each is used exactly once —
        // so none is orphaned and none is shown twice. The set is the *asked* ones:
        // `SHAREABLE_KEY` and `DECRYPT_OPERATION` are superseded and this lesson
        // does not ask them, which is what `optionalStep` is for.
        val used = withCards.mapNotNull { it.support?.id }
        assertEquals("an intro was shown twice", used.size, used.toSet().size)
        assertTrue("an intro is not a card intro", CARD_INTROS.containsAll(used))
        assertEquals(ASKED_CARD_QUESTIONS.size, used.size)
    }

    /** The honesty beat has a picture of its own, or it would not be a beat. */
    @Test
    fun `the real-world beat makes the caveat its subject`() {
        val step = script().steps.single {
            it.headline.id == NarrationId.RSA_WATCH_REAL_WORLD
        }
        assertTrue((step.scene as KeyPairScene).caveatProminent)

        // And it is quiet everywhere the lesson is about something else.
        val quiet = script().steps.takeWhile {
            it.headline.id != NarrationId.RSA_WATCH_REAL_WORLD
        }
        assertTrue(quiet.none { (it.scene as KeyPairScene).caveatProminent })
    }

    @Test
    fun `the recap ends on the two caveats`() {
        val bullets = script().steps.last().bullets.map { it.id }
        assertEquals(
            listOf(NarrationId.RSA_IDEA_6, NarrationId.RSA_IDEA_7),
            bullets.takeLast(2),
        )
        assertEquals(7, bullets.size)
    }

    // ── 6. The lesson in the library ─────────────────────────────────────────

    @Test
    fun `RSA is registered with its own datasets`() {
        val pack = AlgorithmCatalog.byId(AlgorithmId.RSA)
        assertEquals(AlgorithmId.RSA, pack.id)
        assertEquals("RSA", pack.displayName)
        assertEquals(RsaDatasets.watch, pack.watchDataset)
        assertEquals(RsaDatasets.tryIt, pack.tryDataset)
    }

    @Test
    fun `RSA has no challenge, and the catalogue says so`() {
        assertNull(ChallengeCatalog.byId(AlgorithmId.RSA))
    }

    @Test
    fun `progress is 0, 50 then 100, and is latched`() {
        var progress = AlgorithmProgress()
        assertEquals(0, progress.percent)
        progress = progress.complete(Stage.WATCH)
        assertEquals(50, progress.percent)
        progress = progress.complete(Stage.TRY)
        assertEquals(100, progress.percent)
        assertTrue(progress.finished)
        // Additive: finishing a stage again cannot subtract anything.
        assertEquals(100, progress.complete(Stage.WATCH).percent)
    }

    // ── 7. What it cost everything else ──────────────────────────────────────

    @Test
    fun `every lesson in the library still builds its walkthrough`() {
        AlgorithmId.entries.forEach { id ->
            assertTrue("$id produced no walkthrough", AlgorithmCatalog.byId(id).watchScript().size > 0)
        }
    }

    @Test
    fun `the other Cryptography lessons are untouched`() {
        assertEquals(
            "KHOOR",
            com.algorithms.algoking.engine.core.caesarEncrypt("HELLO", 3),
        )
        assertEquals(
            "0110",
            com.algorithms.algoking.engine.core.xorBits("1010", "1100"),
        )
        assertEquals(
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            com.algorithms.algoking.engine.core.Sha256.hex("hello"),
        )
        // AES still produces the ciphertext FIPS-197 publishes.
        assertEquals(
            "69c4e0d86a7b0430d8cdb78070b4c55a",
            com.algorithms.algoking.engine.core.Aes.hex(
                com.algorithms.algoking.engine.core.Aes.ciphertext(
                    block = com.algorithms.algoking.engine.core.Aes
                        .bytesOf("00112233445566778899aabbccddeeff"),
                    key = com.algorithms.algoking.engine.core.Aes
                        .bytesOf("000102030405060708090a0b0c0d0e0f"),
                    variant = com.algorithms.algoking.engine.core.AesVariant.AES_128,
                ),
            ),
        )
    }
}
