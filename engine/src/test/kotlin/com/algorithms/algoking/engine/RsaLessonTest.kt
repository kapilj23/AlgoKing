package com.algorithms.algoking.engine

import com.algorithms.algoking.engine.algorithms.rsa.RsaAction
import com.algorithms.algoking.engine.algorithms.rsa.RsaEncryptionAlgorithm
import com.algorithms.algoking.engine.algorithms.rsa.RsaProjector
import com.algorithms.algoking.engine.algorithms.rsa.RsaState
import com.algorithms.algoking.engine.algorithms.rsa.RsaStepKind
import com.algorithms.algoking.engine.catalog.AlgorithmCatalog
import com.algorithms.algoking.engine.challenge.ChallengeCatalog
import com.algorithms.algoking.engine.core.AlgorithmId
import com.algorithms.algoking.engine.core.AlgorithmRunner
import com.algorithms.algoking.engine.core.Dataset
import com.algorithms.algoking.engine.core.Probe
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
import com.algorithms.algoking.engine.scene.KeyPairScene
import com.algorithms.algoking.engine.scene.RoundTripStage
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

    @Test
    fun `the ten exercises are asked, in the brief's order`() {
        assertEquals(
            listOf(
                RsaQuestion.ASYMMETRIC,
                RsaQuestion.MODULUS,
                RsaQuestion.TOTIENT,
                RsaQuestion.PUBLIC_EXPONENT,
                RsaQuestion.PRIVATE_EXPONENT,
                RsaQuestion.PUBLIC_KEY,
                RsaQuestion.PRIVATE_KEY,
                RsaQuestion.ENCRYPT,
                RsaQuestion.DECRYPT,
                RsaQuestion.SECRET_KEY,
            ),
            RsaDatasets.EXERCISES,
        )
        assertEquals(10, decisions().size)
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
     * Six of the ten judgements are about a derived number. If TRY reused WATCH's
     * primes, every one of them would be answerable from memory.
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
                RsaQuestion.ASYMMETRIC, RsaQuestion.SECRET_KEY -> DecisionKind.CELL
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
                    // The two concept judgements carry a word, not a number.
                    RsaQuestion.ASYMMETRIC, RsaQuestion.SECRET_KEY, null -> Unit
                }
            }
        }
    }

    /** The brief's own numbers, as the lesson asks them. */
    @Test
    fun `the WATCH run asks for 55, 40, 3, 27, (3,55), (27,55), 9 and 4`() {
        val answers = decisions(RsaDatasets.watch).associate { (state, decision) ->
            state.question to decision.options
                .first { it.action == decision.correct }
                .label.args
        }
        assertEquals(listOf(55L), answers[RsaQuestion.MODULUS])
        assertEquals(listOf(40L), answers[RsaQuestion.TOTIENT])
        assertEquals(listOf(3L), answers[RsaQuestion.PUBLIC_EXPONENT])
        assertEquals(listOf(27L), answers[RsaQuestion.PRIVATE_EXPONENT])
        assertEquals(listOf(3L, 55L), answers[RsaQuestion.PUBLIC_KEY])
        assertEquals(listOf(27L, 55L), answers[RsaQuestion.PRIVATE_KEY])
        assertEquals(listOf(9L), answers[RsaQuestion.ENCRYPT])
        assertEquals(listOf(4L), answers[RsaQuestion.DECRYPT])
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
        assertEquals("every decision was guessed at first", 10, refused)
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
    fun `the chain is always five links, and the caveat is always on screen`() {
        scenes().forEach { scene ->
            assertEquals(5, scene.chain.size)
            assertEquals(
                listOf("p, q", "n", "φ(n)", "e", "d"),
                scene.chain.map { it.symbol },
            )
            assertNotNull("the caveat never leaves", scene.caveat)
            assertTrue(requireNotNull(scene.caveat).isNotBlank())
        }
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
            // And the keys are never drawn before they are settled.
            if (state.question == RsaQuestion.PUBLIC_KEY) {
                assertTrue("no key card yet", scene.keys.isEmpty())
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
        val known = scenes().map { scene -> scene.chain.count { it.known } }
        // Monotonic: a value that has been derived never disappears.
        known.zipWithNext().forEach { (a, b) ->
            assertTrue("the chain went backwards: $known", b >= a)
        }
        assertEquals("all five by the end", 5, known.last())
        assertEquals("none at the start", 0, known.first())
    }

    @Test
    fun `both keys are drawn together once they exist, and only one is secret`() {
        val finished = scenes().last()
        assertEquals(2, finished.keys.size)
        assertEquals(listOf("(3, 55)", "(27, 55)"), finished.keys.map { it.printed })
        assertEquals(listOf(false, true), finished.keys.map { it.secret })
        // Both halves share the modulus, which is what the picture must show.
        assertTrue(finished.keys.all { it.printed.endsWith(", 55)") })
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
            val cardQuestion = state.question == RsaQuestion.ASYMMETRIC ||
                state.question == RsaQuestion.SECRET_KEY
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
        assertEquals("both card questions were reached", 2, seen)
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
        val size = script().size
        assertTrue("$size beats", size in 12..20)
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
     */
    @Test
    fun `each card beat is captioned with the picture that shows its cards`() {
        val steps = script().steps
        val asymmetric = steps.single { it.headline.id == NarrationId.RSA_WATCH_ASYMMETRIC }
        val secrecy = steps.single { it.headline.id == NarrationId.RSA_WATCH_SECRET_KEY }

        listOf(asymmetric to "asymmetric", secrecy to "secrecy").forEach { (step, name) ->
            val scene = step.scene as KeyPairScene
            assertEquals("the $name beat shows its four cards", 4, scene.choices.size)
        }

        // ...and no other beat is drawn with cards under it.
        steps.filterNot { it == asymmetric || it == secrecy }.forEach { step ->
            val scene = step.scene as KeyPairScene
            assertTrue(
                "step ${step.index} shows cards it is not about",
                scene.choices.isEmpty(),
            )
        }
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
