package com.algorithms.algoking.engine

import com.algorithms.algoking.engine.algorithms.sha256.HashAction
import com.algorithms.algoking.engine.algorithms.sha256.Sha256HashingAlgorithm
import com.algorithms.algoking.engine.algorithms.sha256.Sha256Projector
import com.algorithms.algoking.engine.algorithms.sha256.Sha256State
import com.algorithms.algoking.engine.catalog.AlgorithmCatalog
import com.algorithms.algoking.engine.challenge.ChallengeCatalog
import com.algorithms.algoking.engine.core.AlgorithmId
import com.algorithms.algoking.engine.core.AlgorithmRunner
import com.algorithms.algoking.engine.core.Dataset
import com.algorithms.algoking.engine.core.HashProblem
import com.algorithms.algoking.engine.core.HashQuestion
import com.algorithms.algoking.engine.core.Probe
import com.algorithms.algoking.engine.core.Sha256
import com.algorithms.algoking.engine.dataset.Sha256Datasets
import com.algorithms.algoking.engine.decision.DecisionKind
import com.algorithms.algoking.engine.decision.DecisionValidation
import com.algorithms.algoking.engine.decision.Validation
import com.algorithms.algoking.engine.event.Outcome
import com.algorithms.algoking.engine.progress.AlgorithmProgress
import com.algorithms.algoking.engine.progress.Stage
import com.algorithms.algoking.engine.scene.CellState
import com.algorithms.algoking.engine.scene.HashScene
import com.algorithms.algoking.engine.walkthrough.WatchStepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SHA-256 — a fixed-size fingerprint, and the three things it guarantees.
 *
 * ### Where the expected digests come from
 *
 * **Not from this engine.** Every digest asserted below was produced independently
 * with two separate tools and checked to agree:
 *
 * ```
 * $ printf '%s' hello | sha256sum
 * $ printf '%s' hello | openssl dgst -sha256
 * ```
 *
 * That matters more here than in any other lesson in the library. Elsewhere a
 * reference implementation can be written beside the test — `XorCipherTest`
 * compares characters rather than using the engine's inequality — but nobody should
 * hand-write a second SHA-256 to check the first. So the check is against the
 * outside world: if this engine and `sha256sum` ever disagree, this file fails.
 */
class Sha256HashingTest {

    private val algorithm = Sha256HashingAlgorithm()
    private val projector = Sha256Projector()

    /**
     * Verified with `sha256sum` and `openssl dgst -sha256`, both agreeing, on the
     * exact bytes with no trailing newline.
     */
    private companion object {
        const val HELLO = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
        const val HELLO_CAPITAL = "185f8db32271fe25f561a6fc938b2e264306ec304eda518007d1764826381969"
        const val HELLO_WORLD = "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9"
        const val HELLO_WORLD_CAPITAL =
            "64ec88ca00b268e5ba1a35678a1b5316d212f4f366b2477232534a8aeca37f3c"
        const val HI = "8f434346648f6b96df89dda901c5176b10a6d83961dd3c1ac88b59b2dc327aa4"
        const val LONGER = "fe6d9c131faa5bb5a8b1a7a8f90fe7db53f22be192e735e2f755c93746434407"
        const val EMPTY = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    }

    private fun dataset(problem: HashProblem) = Dataset(emptyList(), hash = problem)

    private fun runner(problem: HashProblem) = AlgorithmRunner(algorithm, dataset(problem))

    /** Drive to completion the way WATCH does — always the correct option. */
    private fun driveCorrectly(dataset: Dataset): Sha256State {
        val r = AlgorithmRunner(algorithm, dataset)
        var guard = 0
        while (guard++ < 500) {
            when (val probe = r.probe()) {
                is Probe.Mechanical -> r.apply(probe.action)
                is Probe.Decide -> r.apply(probe.decision.correct)
                is Probe.Terminal -> return r.current.state
            }
        }
        error("SHA-256 did not terminate.")
    }

    /** Settle the app's beats, exactly as `LessonController` does in TRY. */
    private fun settle(r: AlgorithmRunner<Sha256State, HashAction>) {
        var guard = 0
        while (guard++ < 500) {
            val probe = r.probe()
            if (probe is Probe.Mechanical) r.apply(probe.action) else return
        }
    }

    // -- 1. The digests, against the outside world -----------------------------

    @Test
    fun `SHA-256 of hello is the documented value`() {
        // The single most checkable fact in the lesson: this is the digest every
        // other SHA-256 implementation on earth produces for these five bytes.
        assertEquals(HELLO, Sha256.hex("hello"))
    }

    @Test
    fun `every verified digest matches an independent implementation`() {
        assertEquals(HELLO, Sha256.hex("hello"))
        assertEquals(HELLO_CAPITAL, Sha256.hex("Hello"))
        assertEquals(HELLO_WORLD, Sha256.hex("hello world"))
        assertEquals(HELLO_WORLD_CAPITAL, Sha256.hex("Hello world"))
        assertEquals(HI, Sha256.hex("hi"))
        assertEquals(LONGER, Sha256.hex("a much longer message"))
        // The empty string hashes fine. It is not used in either lesson — a dataset
        // refuses it — but the function has to be total.
        assertEquals(EMPTY, Sha256.hex(""))
    }

    @Test
    fun `a capital letter gives a completely different digest`() {
        val lower = Sha256.hex("hello")
        val upper = Sha256.hex("Hello")
        assertTrue("one character apart must not give the same hash", lower != upper)
        // And not merely different — different nearly everywhere. This is the
        // number the walkthrough prints, computed the same way it computes it.
        assertEquals(61, Sha256.differingPositions(lower, upper).size)
    }

    @Test
    fun `every digest is 64 hexadecimal characters, whatever went in`() {
        val inputs = listOf(
            "",
            "a",
            "hi",
            "hello",
            "hello world",
            "a much longer message",
            "x".repeat(10_000),
        )
        for (input in inputs) {
            val hex = Sha256.hex(input)
            assertEquals("length of hash for '${input.take(12)}'", 64, hex.length)
            assertEquals(Sha256.HEX_LENGTH, hex.length)
            assertTrue(
                "hash of '${input.take(12)}' must be lowercase hex",
                hex.all { it in '0'..'9' || it in 'a'..'f' },
            )
        }
    }

    @Test
    fun `the output is 256 bits and 32 bytes, and those are the same size`() {
        assertEquals(256, Sha256.BITS)
        assertEquals(32, Sha256.BYTES)
        assertEquals(64, Sha256.HEX_LENGTH)
        // Three ways of saying one number, and they had better agree.
        assertEquals(Sha256.BITS / 8, Sha256.BYTES)
        assertEquals(Sha256.BYTES * 2, Sha256.HEX_LENGTH)
    }

    @Test
    fun `the same input always produces the same hash`() {
        // Determinism, asserted rather than assumed: a hundred runs, byte-identical.
        val first = Sha256.hex("hello")
        repeat(100) { assertEquals(first, Sha256.hex("hello")) }
        // ...and two independently constructed equal strings hash alike too, so it
        // is the value that decides and not the object.
        assertEquals(Sha256.hex("hel" + "lo"), Sha256.hex("hello"))
    }

    @Test
    fun `grouping a digest never changes it`() {
        // The renderer breaks 64 characters into groups so they fit a phone. It is
        // a reading aid, so the exact value has to survive it intact.
        for (input in listOf("hello", "Hello", "hi", "")) {
            val hex = Sha256.hex(input)
            val groups = Sha256.groups(hex)
            assertEquals(8, groups.size)
            assertTrue(groups.all { it.length == Sha256.GROUP_SIZE })
            assertEquals(hex, groups.joinToString(""))
        }
    }

    @Test
    fun `differing positions are symmetric, and empty for equal digests`() {
        val a = Sha256.hex("hello")
        val b = Sha256.hex("Hello")
        assertEquals(Sha256.differingPositions(a, b), Sha256.differingPositions(b, a))
        // The determinism beat's picture: the same input, and nothing to mark.
        assertTrue(Sha256.differingPositions(a, Sha256.hex("hello")).isEmpty())
    }

    // -- 2. The lesson's datasets ---------------------------------------------

    @Test
    fun `no digest is authored anywhere in the datasets`() {
        // Every value on screen is computed from a message. A dataset that carried
        // a digest would be a second source of truth for the one number the whole
        // lesson is about.
        for (dataset in listOf(Sha256Datasets.watch, Sha256Datasets.tryIt)) {
            val problem = requireNotNull(dataset.hash)
            for (index in problem.messages.indices) {
                assertEquals(
                    Sha256.hex(problem.messages[index]),
                    problem.digestOf(index),
                )
            }
        }
    }

    @Test
    fun `the WATCH dataset carries evidence for every property it teaches`() {
        val problem = requireNotNull(Sha256Datasets.watch.hash)

        // One character apart, for the avalanche — found by looking, not authored.
        val avalanche = requireNotNull(problem.avalanchePair)
        val (a, b) = avalanche
        assertEquals(problem.messages[a].length, problem.messages[b].length)
        assertEquals(
            1,
            problem.messages[a].indices.count { problem.messages[a][it] != problem.messages[b][it] },
        )

        // The same message twice, for determinism — as two real runs.
        val repeated = requireNotNull(problem.repeatedPair)
        assertEquals(problem.messages[repeated.first], problem.messages[repeated.second])
        assertEquals(
            problem.digestOf(repeated.first),
            problem.digestOf(repeated.second),
        )

        // Three different input lengths, for fixed length.
        val ladder = problem.lengthLadder
        assertTrue("need at least three lengths, got ${ladder.size}", ladder.size >= 3)
        val lengths = ladder.map { problem.messages[it].length }
        assertEquals(lengths.sorted(), lengths)
        assertEquals(lengths.size, lengths.toSet().size)
        // ...and every one of them still produces 64 characters.
        assertTrue(ladder.all { problem.digestOf(it).length == 64 })
    }

    @Test
    fun `the TRY dataset can answer every question it asks`() {
        val problem = requireNotNull(Sha256Datasets.tryIt.hash)

        // The brief's two inputs are both there.
        assertTrue("hello" in problem.messages)
        assertTrue("Hello" in problem.messages)

        // Question 1 asks whether output length follows input length, so TRY has to
        // contain inputs of different lengths — `hello` and `Hello` are both five,
        // which is exactly the trap this test exists to catch.
        assertTrue(problem.lengthLadder.size >= 3)
        assertNotNull(problem.avalanchePair)
        assertNotNull(problem.repeatedPair)
    }

    @Test
    fun `WATCH and TRY ask the same five questions in the same order`() {
        // The lesson's difference between the stages is the narration budget, not
        // the data (ADR-045's position for Fibonacci). Asking a different set in
        // TRY would make the stage transition read as a different lesson.
        val watch = requireNotNull(Sha256Datasets.watch.hash).questions
        val tryIt = requireNotNull(Sha256Datasets.tryIt.hash).questions
        assertEquals(watch, tryIt)
        assertEquals(
            listOf(
                HashQuestion.FIXED_LENGTH,
                HashQuestion.DETERMINISTIC,
                HashQuestion.AVALANCHE,
                HashQuestion.ONE_WAY,
                HashQuestion.OUTPUT_SIZE,
            ),
            watch,
        )
        // Every property the enum knows about is actually taught.
        assertEquals(HashQuestion.entries.toSet(), watch.toSet())
    }

    @Test
    fun `a problem refuses data it could not teach from`() {
        val questions = listOf(HashQuestion.FIXED_LENGTH)
        for (bad in listOf<() -> HashProblem>(
            { HashProblem(messages = emptyList(), questions = questions) },
            { HashProblem(messages = listOf("hello", ""), questions = questions) },
            { HashProblem(messages = listOf("hello"), questions = emptyList()) },
            {
                HashProblem(
                    messages = listOf("hello"),
                    questions = listOf(HashQuestion.ONE_WAY, HashQuestion.ONE_WAY),
                )
            },
        )) {
            var refused = false
            try {
                bad()
            } catch (expected: IllegalArgumentException) {
                refused = true
            }
            assertTrue("a bad problem must be refused where it is authored", refused)
        }
    }

    // -- 3. The state machine --------------------------------------------------

    @Test
    fun `hashing happens first, then every judgement`() {
        val problem = requireNotNull(Sha256Datasets.watch.hash)
        val r = runner(problem)

        // Every message goes through the pipeline before anything is asked, which
        // is what makes each question answerable by reading the picture.
        repeat(problem.messages.size) {
            assertTrue(
                "message $it should be the app's beat",
                r.probe() is Probe.Mechanical,
            )
            r.apply(HashAction.Hash)
        }
        assertTrue(r.current.state.allHashed)

        repeat(problem.questions.size) {
            val probe = r.probe()
            assertTrue("question $it should be the learner's", probe is Probe.Decide)
            r.apply((probe as Probe.Decide).decision.correct)
        }
        assertTrue(r.probe() is Probe.Terminal)
    }

    @Test
    fun `a full correct run terminates and completes`() {
        val state = driveCorrectly(Sha256Datasets.watch)
        assertTrue(state.finished)
        assertTrue(state.allHashed)
        assertEquals(state.problem.questions.size, state.answers.size)
        assertEquals(0, state.remainingMessages)
        assertEquals(0, state.remainingQuestions)
    }

    @Test
    fun `the digests are never stored on the state`() {
        val state = driveCorrectly(Sha256Datasets.watch)
        // Derived from the problem every time, so the state cannot hold a digest
        // that disagrees with the message beside it.
        assertEquals(
            state.problem.messages.map(Sha256::hex),
            state.digests,
        )
    }

    @Test
    fun `an over-long action sequence is a no-op, never a crash`() {
        val problem = HashProblem(listOf("hello"), listOf(HashQuestion.ONE_WAY))
        val r = runner(problem)
        settle(r)
        val settled = r.current.state

        // Hashing past the end changes nothing.
        assertSame(settled, algorithm.apply(settled, HashAction.Hash).next)
        // Neither does an answer that is not one of the two options.
        assertSame(settled, algorithm.apply(settled, HashAction.Answer(7)).next)
        assertSame(settled, algorithm.apply(settled, HashAction.Answer(-1)).next)
    }

    @Test
    fun `rewind returns the exact previous state`() {
        val r = runner(requireNotNull(Sha256Datasets.tryIt.hash))
        settle(r)
        val before = r.current.state
        val probe = r.probe() as Probe.Decide
        r.apply(probe.decision.correct)
        assertTrue(r.current.state != before)
        r.rewind(1)
        assertEquals(before, r.current.state)
    }

    // -- 4. The judgements -----------------------------------------------------

    @Test
    fun `every question offers two options and names one of them correct`() {
        val r = runner(requireNotNull(Sha256Datasets.tryIt.hash))
        settle(r)

        var asked = 0
        while (true) {
            val probe = r.probe()
            if (probe !is Probe.Decide) break
            val decision = probe.decision
            asked++

            assertEquals(DecisionKind.OPTIONS, decision.kind)
            assertEquals(2, decision.options.size)
            // The correct action must actually be on offer, or the learner is being
            // asked something they cannot answer.
            assertTrue(decision.correct in decision.options.map { it.action })
            // Nothing the app answers for them: these are the judgements.
            assertFalse(decision.autoInTry)
            // Every rung of the ladder is present, and the wrong option is named.
            assertEquals(3, decision.guidance.size)
            assertEquals(1, decision.whyWrong.size)

            r.apply(decision.correct)
        }
        assertEquals(5, asked)
    }

    @Test
    fun `the true statement is not always in the same seat`() {
        // A learner who noticed the first button was always right could finish the
        // stage without reading anything.
        val r = runner(requireNotNull(Sha256Datasets.tryIt.hash))
        settle(r)

        val seats = mutableListOf<Int>()
        while (true) {
            val probe = r.probe()
            if (probe !is Probe.Decide) break
            val decision = probe.decision
            seats += decision.options.indexOfFirst { it.action == decision.correct }
            r.apply(decision.correct)
        }
        assertEquals(5, seats.size)
        assertTrue("both seats must be used, got $seats", seats.toSet().size == 2)
    }

    @Test
    fun `a wrong answer does not advance the lesson`() {
        val r = runner(requireNotNull(Sha256Datasets.tryIt.hash))
        settle(r)

        val before = r.current.state
        val decision = (r.probe() as Probe.Decide).decision
        val wrong = decision.options.map { it.action }.first { it != decision.correct }

        // Five wrong answers, the way a learner might actually give them. The
        // validator refuses each one and hands back nothing to apply, so the
        // state is byte-for-byte where it was (ADR-021).
        repeat(5) { attempt ->
            val verdict = DecisionValidation.validate(decision, wrong, attempt)
            assertTrue(verdict is Validation.Retry)
            assertEquals(before, r.current.state)
            // The same decision is still on screen. `probe` rebuilds it from the
            // state each call, so this is equality rather than identity — and the
            // point stands either way: nothing moved.
            assertEquals(decision, (r.probe() as Probe.Decide).decision)
        }
        assertEquals(0, before.answers.size)
    }

    @Test
    fun `guidance escalates and then holds`() {
        val r = runner(requireNotNull(Sha256Datasets.tryIt.hash))
        settle(r)
        val decision = (r.probe() as Probe.Decide).decision
        val wrong = decision.options.map { it.action }.first { it != decision.correct }

        val rungs = (0 until 6).map { attempt ->
            (DecisionValidation.validate(decision, wrong, attempt) as Validation.Retry).guidance
        }
        // Three distinct rungs, then the most explicit one repeats forever — a
        // learner is never dead-ended.
        assertEquals(3, rungs.take(3).toSet().size)
        assertEquals(rungs[2], rungs[3])
        assertEquals(rungs[2], rungs[5])

        // The wrong option is always named — `DecisionValidation` returns the
        // `whyWrong` for the chosen option at every level, and the *screen*
        // decides when to surface it. With two options there is exactly one wrong
        // answer, so naming the misconception is the most useful thing the lesson
        // can say (ADR-047's reasoning for the XOR lesson's two buttons).
        repeat(3) { attempt ->
            val retry = DecisionValidation.validate(decision, wrong, attempt) as Validation.Retry
            assertNotNull(retry.whyWrong)
            assertEquals(attempt + 1, retry.level)
        }
    }

    @Test
    fun `a correct answer advances, and only a correct one`() {
        val r = runner(requireNotNull(Sha256Datasets.tryIt.hash))
        settle(r)
        val decision = (r.probe() as Probe.Decide).decision

        val accepted = DecisionValidation.validate(decision, decision.correct, 0)
        assertTrue(accepted is Validation.Accept)
        r.apply((accepted as Validation.Accept).action)
        assertEquals(1, r.current.state.answers.size)
        assertTrue(r.current.correct)
    }

    @Test
    fun `every question must be answered before the lesson completes`() {
        val problem = requireNotNull(Sha256Datasets.tryIt.hash)
        val r = runner(problem)
        settle(r)

        // Four of five answered is not finished, whatever else is true.
        repeat(problem.questions.size - 1) {
            r.apply((r.probe() as Probe.Decide).decision.correct)
            assertFalse(r.current.state.finished)
            assertFalse(r.probe() is Probe.Terminal)
        }
        r.apply((r.probe() as Probe.Decide).decision.correct)
        assertTrue(r.current.state.finished)
        assertEquals(Outcome.Completed(true), (r.probe() as Probe.Terminal).outcome)
    }

    // -- 5. The picture --------------------------------------------------------

    private fun sceneAt(r: AlgorithmRunner<Sha256State, HashAction>): HashScene =
        projector.project(r.current.state, r.current.events) as HashScene

    @Test
    fun `the pipeline is always all five stages`() {
        val r = runner(requireNotNull(Sha256Datasets.watch.hash))
        assertEquals(5, sceneAt(r).pipeline.size)
        settle(r)
        assertEquals(5, sceneAt(r).pipeline.size)
        // ...including the SHA-256 box, which stands for the real algorithm and is
        // deliberately one box rather than 64 rounds.
        assertTrue(sceneAt(r).pipeline.any { it.label == "SHA-256" })
    }

    @Test
    fun `nothing is printed before it has been produced`() {
        val r = runner(requireNotNull(Sha256Datasets.watch.hash))
        // Before the first hash there is no digest to show — the rule ADR-030 set:
        // an answer already on screen is not a question.
        assertNull(sceneAt(r).digest)
        r.apply(HashAction.Hash)
        val digest = requireNotNull(sceneAt(r).digest)
        assertEquals("hello", digest.input)
        assertEquals(HELLO, digest.hex)
        assertEquals(HELLO, digest.groups.joinToString(""))
        assertEquals(256, digest.bits)
        assertEquals(32, digest.bytes)
    }

    @Test
    fun `the avalanche beat marks the characters that actually changed`() {
        val r = runner(requireNotNull(Sha256Datasets.watch.hash))
        settle(r)
        // Walk to the avalanche question.
        while ((r.probe() as? Probe.Decide) != null &&
            r.current.state.question != HashQuestion.AVALANCHE
        ) {
            r.apply((r.probe() as Probe.Decide).decision.correct)
        }
        assertEquals(HashQuestion.AVALANCHE, r.current.state.question)

        val rows = sceneAt(r).comparisons
        assertEquals(2, rows.size)
        // The first row is the reference, so nothing on it is marked...
        assertTrue(rows[0].differing.isEmpty())
        // ...and the second carries exactly the positions that differ, computed
        // rather than authored.
        assertEquals(
            Sha256.differingPositions(rows[0].hex, rows[1].hex),
            rows[1].differing,
        )
        assertEquals(61, rows[1].differing.size)
    }

    @Test
    fun `the determinism beat marks nothing, because nothing changed`() {
        val r = runner(requireNotNull(Sha256Datasets.watch.hash))
        settle(r)
        while ((r.probe() as? Probe.Decide) != null &&
            r.current.state.question != HashQuestion.DETERMINISTIC
        ) {
            r.apply((r.probe() as Probe.Decide).decision.correct)
        }

        val rows = sceneAt(r).comparisons
        assertEquals(2, rows.size)
        assertEquals(rows[0].input, rows[1].input)
        assertEquals(rows[0].hex, rows[1].hex)
        // The right picture for "the same input gives the same hash" is one with
        // nothing highlighted at all.
        assertTrue(rows.all { it.differing.isEmpty() })
    }

    @Test
    fun `the fixed-length beat shows different inputs and identical output lengths`() {
        val r = runner(requireNotNull(Sha256Datasets.watch.hash))
        settle(r)
        assertEquals(HashQuestion.FIXED_LENGTH, r.current.state.question)

        val rows = sceneAt(r).comparisons
        assertTrue("need at least three lengths on screen", rows.size >= 3)
        // Different inputs...
        assertEquals(rows.size, rows.map { it.inputLength }.toSet().size)
        // ...one output length, and no character marks: this beat is about the
        // lengths, so colouring the characters would point at the wrong thing.
        assertTrue(rows.all { it.hexLength == 64 })
        assertTrue(rows.all { it.differing.isEmpty() })
    }

    @Test
    fun `both statements are on screen whenever one is being asked for`() {
        val r = runner(requireNotNull(Sha256Datasets.tryIt.hash))
        settle(r)
        while (true) {
            val probe = r.probe()
            if (probe !is Probe.Decide) break
            val scene = sceneAt(r)
            assertEquals(2, scene.claims.size)
            // The button word appears on the card above it, so the pairing cannot
            // be misread...
            assertEquals(
                probe.decision.options.size,
                scene.claims.size,
            )
            // ...and neither claim is marked as the true one: the scene does not
            // carry which is correct, so the renderer cannot leak it.
            assertTrue(scene.claims.all { it.text.isNotBlank() && it.choice.isNotBlank() })
            r.apply(probe.decision.correct)
        }
    }

    @Test
    fun `a digest never reaches the screen ungrouped`() {
        // Sixty-four characters is wider than a phone. Every digest the renderer is
        // handed arrives pre-grouped, and grouping never alters the value.
        val r = runner(requireNotNull(Sha256Datasets.watch.hash))
        var guard = 0
        while (guard++ < 500) {
            val scene = sceneAt(r)
            scene.digest?.let {
                assertEquals(8, it.groups.size)
                assertEquals(it.hex, it.groups.joinToString(""))
            }
            scene.comparisons.forEach {
                assertEquals(8, it.groups.size)
                assertEquals(it.hex, it.groups.joinToString(""))
            }
            when (val probe = r.probe()) {
                is Probe.Mechanical -> r.apply(probe.action)
                is Probe.Decide -> r.apply(probe.decision.correct)
                is Probe.Terminal -> return
            }
        }
        error("SHA-256 did not terminate.")
    }

    // -- 6. The walkthrough ----------------------------------------------------

    @Test
    fun `WATCH covers every property, and never shows the same picture twice`() {
        val script = AlgorithmCatalog.sha256().watchScript()

        // Long enough to teach, short enough not to be a slideshow.
        assertTrue("walkthrough is ${script.size} steps", script.size in 9..16)
        assertEquals(WatchStepKind.SETUP, script.steps.first().kind)
        assertEquals(WatchStepKind.SUMMARY, script.steps.last().kind)
        assertTrue(script.steps.any { it.kind == WatchStepKind.INSIGHT })

        // A step where nothing changed is a bug, not a beat — the house rule from
        // ADR-020: scene, headline, support or recap, something has to move.
        script.steps.zipWithNext { a, b ->
            val changed = a.scene != b.scene ||
                a.headline != b.headline ||
                a.support != b.support ||
                a.bullets != b.bullets
            assertTrue("steps ${a.index} and ${b.index} are identical", changed)
        }
    }

    @Test
    fun `each property beat is captioned with the picture it was given`() {
        // The bug this test exists for: `WatchScriptBuilder` projects the scene
        // from the state *after* the transition, so a beat captioned with the
        // question it just answered is drawn showing the *next* question's
        // evidence — the fixed-length sentence over the determinism rows. It was
        // found by dumping the walkthrough and reading it (ADR-047, ADR-048).
        val script = AlgorithmCatalog.sha256().watchScript()

        val propertyBeats = script.steps.filter { step ->
            HashQuestion.entries.any { step.headline.id.name.contains(it.name) }
        }
        assertEquals(5, propertyBeats.size)

        for (step in propertyBeats) {
            val question = HashQuestion.entries
                .single { step.headline.id.name.contains(it.name) }
            val scene = step.scene as HashScene

            // The claims on screen are the ones this question is actually about,
            // so the sentence and the picture describe the same moment.
            assertEquals("claims missing on the $question beat", 2, scene.claims.size)
            when (question) {
                HashQuestion.FIXED_LENGTH -> {
                    assertTrue(scene.comparisons.size >= 3)
                    assertTrue(scene.comparisons.all { it.differing.isEmpty() })
                }

                HashQuestion.DETERMINISTIC -> {
                    assertEquals(2, scene.comparisons.size)
                    assertEquals(scene.comparisons[0].hex, scene.comparisons[1].hex)
                }

                HashQuestion.AVALANCHE -> {
                    assertEquals(2, scene.comparisons.size)
                    assertTrue(scene.comparisons[1].differing.isNotEmpty())
                }

                // Neither is about a comparison: the pipeline is the evidence.
                HashQuestion.ONE_WAY, HashQuestion.OUTPUT_SIZE ->
                    assertTrue(scene.comparisons.isEmpty())
            }
        }

        // And the five pictures are five different pictures.
        assertEquals(5, propertyBeats.map { it.scene }.toSet().size)
    }

    @Test
    fun `WATCH states every property TRY then asks about`() {
        val script = AlgorithmCatalog.sha256().watchScript()
        val headlines = script.steps.map { it.headline.id.name }

        // The five judgements each get their own beat, captioned with the same key
        // the decision settles on — so the walkthrough cannot drift from the
        // questions.
        for (question in HashQuestion.entries) {
            assertTrue(
                "no WATCH beat for $question",
                headlines.any { it.contains(question.name) },
            )
        }
    }

    @Test
    fun `the recap says hashing is not encryption, and not a password store`() {
        val script = AlgorithmCatalog.sha256().watchScript()
        val summary = script.steps.last()
        val bullets = summary.bullets.map { it.id.name }

        // Six bullets, and the last two are the ones a learner must not leave
        // without. They are last deliberately: a recap bullet is read rather than
        // skipped (ADR-047's placement for the XOR caveat).
        assertEquals(6, bullets.size)
        assertEquals("SHA_IDEA_5", bullets[4])
        assertEquals("SHA_IDEA_6", bullets[5])
    }

    @Test
    fun `the avalanche count in the walkthrough is computed, not authored`() {
        val script = AlgorithmCatalog.sha256().watchScript()
        val beat = script.steps.single { it.headline.id.name.contains("AVALANCHE") }
        val args = requireNotNull(beat.support).args

        // The claim the beat makes, checked against the two digests it is about.
        val expected = Sha256.differingPositions(HELLO, HELLO_CAPITAL).size
        assertTrue("the changed count must be in the copy's arguments", expected in args)
        assertEquals(61, expected)
        assertTrue(64 in args)
    }

    // -- 7. The lesson in the library -----------------------------------------

    @Test
    fun `the pack is registered and reachable by id`() {
        val pack = AlgorithmCatalog.byId(AlgorithmId.SHA_256)
        assertEquals(AlgorithmId.SHA_256, pack.id)
        assertEquals("SHA-256 Hashing", pack.displayName)
        assertSame(Sha256Datasets.watch, pack.watchDataset)
        assertSame(Sha256Datasets.tryIt, pack.tryDataset)
    }

    @Test
    fun `there is no challenge, and that is deliberate`() {
        // CHALLENGE is V2 (ADR-031), and this lesson's judgements are about what
        // hashing guarantees rather than a step to execute — so there is no run to
        // generate a fresh dataset for. Null is the honest signature.
        assertNull(ChallengeCatalog.byId(AlgorithmId.SHA_256))
    }

    @Test
    fun `progress is the house rule — 0, 50, then 100`() {
        var progress = AlgorithmProgress()
        assertEquals(0, progress.percent)

        progress = progress.complete(Stage.WATCH)
        assertEquals(50, progress.percent)
        assertTrue(progress.watchCompleted)
        assertFalse(progress.finished)

        progress = progress.complete(Stage.TRY)
        assertEquals(100, progress.percent)
        assertTrue(progress.finished)

        // Latched and additive: repeating a stage cannot subtract anything.
        assertEquals(100, progress.complete(Stage.WATCH).percent)
        assertEquals(100, progress.complete(Stage.TRY).percent)
    }

    // -- 8. What it cost everything else ---------------------------------------

    @Test
    fun `adding this lesson changed no other lesson`() {
        // Every other pack still builds its walkthrough, and the two lessons on the
        // same shelf still produce exactly what they did.
        for (id in AlgorithmId.entries) {
            val script = AlgorithmCatalog.byId(id).watchScript()
            assertTrue("$id has an empty walkthrough", script.size > 0)
        }

        // The ciphers, specifically: both still reversible, both untouched.
        assertEquals("0110", com.algorithms.algoking.engine.core.xorBits("1010", "1100"))
        assertEquals("1010", com.algorithms.algoking.engine.core.xorBits("0110", "1100"))
        assertEquals(
            "KHOOR",
            com.algorithms.algoking.engine.core.caesarEncrypt("HELLO", 3),
        )
    }

    @Test
    fun `the scene reuses the shared cell states rather than inventing any`() {
        val r = runner(requireNotNull(Sha256Datasets.watch.hash))
        settle(r)
        val scene = sceneAt(r)
        val used = scene.legendStates
        // Every state on screen is one the legend already knows how to name, so the
        // lesson needed no new token and no renderer fork.
        assertTrue(used.all { it in CellState.entries })
        assertTrue(used.isNotEmpty())
    }
}
