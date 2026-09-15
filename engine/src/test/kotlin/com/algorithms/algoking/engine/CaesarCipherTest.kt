package com.algorithms.algoking.engine

import com.algorithms.algoking.engine.algorithms.caesar.CaesarAction
import com.algorithms.algoking.engine.algorithms.caesar.CaesarCipherAlgorithm
import com.algorithms.algoking.engine.algorithms.caesar.CaesarProjector
import com.algorithms.algoking.engine.algorithms.caesar.CaesarState
import com.algorithms.algoking.engine.algorithms.caesar.caesarAlphabet
import com.algorithms.algoking.engine.catalog.AlgorithmCatalog
import com.algorithms.algoking.engine.challenge.ChallengeCatalog
import com.algorithms.algoking.engine.core.AlgorithmId
import com.algorithms.algoking.engine.core.AlgorithmRunner
import com.algorithms.algoking.engine.core.CipherProblem
import com.algorithms.algoking.engine.core.Dataset
import com.algorithms.algoking.engine.core.Probe
import com.algorithms.algoking.engine.core.caesarDecrypt
import com.algorithms.algoking.engine.core.caesarEncrypt
import com.algorithms.algoking.engine.dataset.CaesarDatasets
import com.algorithms.algoking.engine.decision.DecisionKind
import com.algorithms.algoking.engine.decision.DecisionValidation
import com.algorithms.algoking.engine.decision.Validation
import com.algorithms.algoking.engine.event.Outcome
import com.algorithms.algoking.engine.event.VizEvent
import com.algorithms.algoking.engine.narration.NarrationId
import com.algorithms.algoking.engine.progress.AlgorithmProgress
import com.algorithms.algoking.engine.progress.Stage
import com.algorithms.algoking.engine.scene.CellState
import com.algorithms.algoking.engine.scene.CipherScene
import com.algorithms.algoking.engine.walkthrough.WatchStepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Caesar Cipher — the first encryption lesson.
 *
 * The transform, the wrap, the shift normalisation, the refusals and the
 * walkthrough. Every ciphertext asserted here is checked against a **separately
 * written character-by-character shift** rather than against the engine's own
 * output, so a bug in the transform cannot make its own test pass.
 */
class CaesarCipherTest {

    private val algorithm = CaesarCipherAlgorithm()
    private val projector = CaesarProjector()

    private fun dataset(text: String, shift: Int) =
        Dataset(emptyList(), cipher = CipherProblem(text, shift))

    private fun runner(text: String, shift: Int) =
        AlgorithmRunner(algorithm, dataset(text, shift))

    private fun state(text: String, shift: Int) = algorithm.initial(dataset(text, shift))

    /** Drive to completion the way WATCH does — always the correct option. */
    private fun driveCorrectly(text: String, shift: Int): CaesarState {
        val r = runner(text, shift)
        var guard = 0
        while (guard++ < 500) {
            when (val probe = r.probe()) {
                is Probe.Mechanical -> r.apply(probe.action)
                is Probe.Decide -> r.apply(probe.decision.correct)
                is Probe.Terminal -> return r.current.state
            }
        }
        error("Caesar Cipher did not terminate.")
    }

    /**
     * The rule, written out independently — a literal walk of the alphabet rather
     * than modular arithmetic, so it shares no code with the implementation.
     */
    private fun referenceEncrypt(text: String, shift: Int): String = buildString {
        val upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val lower = "abcdefghijklmnopqrstuvwxyz"
        val steps = ((shift % 26) + 26) % 26
        for (char in text) {
            val row = when {
                upper.contains(char) -> upper
                lower.contains(char) -> lower
                else -> null
            }
            if (row == null) {
                append(char)
                continue
            }
            var at = row.indexOf(char)
            repeat(steps) { at = if (at == 25) 0 else at + 1 }
            append(row[at])
        }
    }

    // -- 1. The transform ------------------------------------------------------

    @Test
    fun `the documented examples encrypt as documented`() {
        // The two the brief specifies, and the wrap cases it names.
        assertEquals("KHOOR", caesarEncrypt("HELLO", 3))
        assertEquals("CNIQ", caesarEncrypt("ALGO", 2))
        assertEquals("A", caesarEncrypt("Z", 1))
        assertEquals("C", caesarEncrypt("Z", 3))
        assertEquals("ABC", caesarEncrypt("ABC", 0))
        assertEquals("ABC", caesarEncrypt("XYZ", 3))
    }

    @Test
    fun `the transform agrees with an independent alphabet walk`() {
        // The implementation is modular arithmetic; the reference steps along a
        // string one letter at a time. If they agree across every shift on mixed
        // input, the transform is the cipher and not something that resembles it.
        val samples = listOf("HELLO", "ALGO", "XYZ", "ZZZ", "AbCdEf", "A B-C!", "Attack")
        for (text in samples) {
            for (shift in -30..30) {
                assertEquals(
                    "\"$text\" shift $shift",
                    referenceEncrypt(text, shift),
                    caesarEncrypt(text, shift),
                )
            }
        }
    }

    @Test
    fun `decrypting undoes encrypting, for every shift`() {
        // Named in the lesson and not built as a stage, but it has to be true or
        // the sentence the recap ends on is a lie.
        for (shift in 0..25) {
            assertEquals("HELLO", caesarDecrypt(caesarEncrypt("HELLO", shift), shift))
        }
    }

    @Test
    fun `A maps to D at shift 3, and the whole documented mapping holds`() {
        val map = caesarAlphabet(3).toMap()
        assertEquals('D', map['A'])
        assertEquals('E', map['B'])
        assertEquals('F', map['C'])
        // ...and the three at the end, which are the ones that matter.
        assertEquals('A', map['X'])
        assertEquals('B', map['Y'])
        assertEquals('C', map['Z'])
        assertEquals(26, caesarAlphabet(3).size)
    }

    // -- 2. Shift normalisation -----------------------------------------------

    @Test
    fun `shift 29 behaves exactly like shift 3`() {
        assertEquals(3, CipherProblem("HELLO", 29).shift)
        assertEquals(caesarEncrypt("HELLO", 3), caesarEncrypt("HELLO", 29))
        assertEquals("KHOOR", driveCorrectly("HELLO", 29).produced)
    }

    @Test
    fun `every equivalent shift normalises into the alphabet`() {
        assertEquals(0, CipherProblem("A", 0).shift)
        assertEquals(0, CipherProblem("A", 26).shift)
        assertEquals(0, CipherProblem("A", 52).shift)
        assertEquals(1, CipherProblem("A", 27).shift)
        assertEquals(25, CipherProblem("A", 25).shift)
        // Kotlin's % keeps the sign of its left operand, so a negative shift is
        // where a missing `+ 26` would produce a negative index and an exception.
        assertEquals(25, CipherProblem("A", -1).shift)
        assertEquals(23, CipherProblem("A", -3).shift)
        assertEquals(1, CipherProblem("A", -25).shift)
        assertEquals(0, CipherProblem("A", -26).shift)
    }

    @Test
    fun `shift 0 and shift 25 are both legal lessons`() {
        assertEquals("HELLO", driveCorrectly("HELLO", 0).produced)
        assertEquals(referenceEncrypt("HELLO", 25), driveCorrectly("HELLO", 25).produced)
        assertEquals(referenceEncrypt("HELLO", 1), driveCorrectly("HELLO", 1).produced)
    }

    // -- 3. Wrapping -----------------------------------------------------------

    @Test
    fun `Z wraps, and every Z in a message wraps`() {
        assertEquals("A", caesarEncrypt("Z", 1))
        assertEquals("C", caesarEncrypt("Z", 3))
        // Multiple Zs, adjacent and separated — nothing carries between characters.
        assertEquals("CCC", caesarEncrypt("ZZZ", 3))
        assertEquals("CDC", caesarEncrypt("ZAZ", 3))
        assertEquals("CCC", driveCorrectly("ZZZ", 3).produced)
    }

    @Test
    fun `the state knows when a character wraps, and what the sum was`() {
        val z = state("ZAP", 3)
        assertEquals(25, z.currentIndex)
        assertEquals(28, z.currentSum)
        assertTrue(z.currentWraps)

        // A the next character does not wrap, so the strip stays simple for it.
        val next = algorithm.apply(z, CaesarAction.Encrypt('C')).next
        assertEquals(0, next.currentIndex)
        assertEquals(3, next.currentSum)
        assertFalse(next.currentWraps)
    }

    // -- 4. Case and non-letters ----------------------------------------------

    @Test
    fun `case is preserved`() {
        assertEquals("Khoor", caesarEncrypt("Hello", 3))
        assertEquals("kHOOR", caesarEncrypt("hELLO", 3))
        assertEquals("c", caesarEncrypt("z", 3))
    }

    @Test
    fun `non-alphabetic characters pass through untouched`() {
        // A space is not a letter, so shifting it would invent a rule the cipher
        // does not have.
        assertEquals("KHOOR ZRUOG", caesarEncrypt("HELLO WORLD", 3))
        assertEquals("KHOOR!", caesarEncrypt("HELLO!", 3))
        assertEquals("D1E2F3", caesarEncrypt("A1B2C3", 3))
    }

    @Test
    fun `a non-letter is copied by the app, never asked about`() {
        val r = runner("A B", 2)
        // A -> a decision.
        assertTrue(r.probe() is Probe.Decide)
        r.apply((r.probe() as Probe.Decide).decision.correct)
        // The space -> mechanical, because there is one legal outcome.
        val probe = r.probe()
        assertTrue("a space is not a judgement", probe is Probe.Mechanical)
        assertEquals(CaesarAction.CopyThrough, (probe as Probe.Mechanical).action)
        val frame = r.apply(probe.action)
        // It is held, not inserted: nothing was transformed.
        assertTrue(frame.events.any { it is VizEvent.Hold })
        assertTrue(frame.events.none { it is VizEvent.Insert })
        assertEquals("C ", frame.state.produced)
    }

    // -- 5. TRY: correct selections advance ------------------------------------

    @Test
    fun `the authored TRY message encrypts to CNIQ, one letter at a time`() {
        val r = AlgorithmRunner(algorithm, CaesarDatasets.tryIt)
        val asked = mutableListOf<Char>()
        var guard = 0
        while (guard++ < 100) {
            val probe = r.probe()
            if (probe is Probe.Terminal) break
            val decision = (probe as Probe.Decide).decision
            asked += r.current.state.currentChar!!
            val before = r.current.state.produced.length
            r.apply(decision.correct)
            // A correct selection advances the state by exactly one character.
            assertEquals(before + 1, r.current.state.produced.length)
        }
        assertEquals(listOf('A', 'L', 'G', 'O'), asked)
        assertEquals("CNIQ", r.current.state.produced)
        assertEquals("CNIQ", referenceEncrypt("ALGO", 2))
    }

    @Test
    fun `every decision offers three distinct letters including the right one`() {
        val r = AlgorithmRunner(algorithm, CaesarDatasets.watch)
        var guard = 0
        while (guard++ < 100) {
            val probe = r.probe()
            if (probe is Probe.Terminal) break
            val decision = (probe as Probe.Decide).decision
            assertEquals(DecisionKind.OPTIONS, decision.kind)
            assertEquals(3, decision.options.size)
            // Distinct, or two buttons read the same and one of them is a trap.
            assertEquals(3, decision.options.map { it.action }.toSet().size)
            assertTrue(decision.options.any { it.action == decision.correct })
            // Every wrong option explains itself.
            for (option in decision.options) {
                if (option.action == decision.correct) continue
                assertNotNull("whyWrong for $option", decision.whyWrong[option.action])
            }
            r.apply(decision.correct)
        }
    }

    @Test
    fun `the backwards shift is always on the table, because it is the real mistake`() {
        // Shifting the wrong way is the error this cipher produces most, and the
        // one that matters for decryption. It has to be offerable.
        val decision = (runner("ALGO", 2).probe() as Probe.Decide).decision
        val letters = decision.options.map { (it.action as CaesarAction.Encrypt).letter }
        assertTrue("A shifted back by 2 is Y", letters.contains('Y'))
        assertEquals(CaesarAction.Encrypt('C'), decision.correct)
        assertEquals(
            NarrationId.CC_WHY_BACKWARDS,
            decision.whyWrong[CaesarAction.Encrypt('Y')]?.id,
        )
    }

    @Test
    fun `the correct answer does not always sit in the same seat`() {
        val seats = mutableSetOf<Int>()
        val r = AlgorithmRunner(algorithm, CaesarDatasets.watch)
        var guard = 0
        while (guard++ < 100) {
            val probe = r.probe()
            if (probe is Probe.Terminal) break
            val decision = (probe as Probe.Decide).decision
            seats += decision.options.indexOfFirst { it.action == decision.correct }
            r.apply(decision.correct)
        }
        assertTrue("the answer never moved seat: $seats", seats.size > 1)
    }

    // -- 6. TRY: wrong selections do not advance -------------------------------

    @Test
    fun `a wrong selection is refused and the state does not move`() {
        val r = AlgorithmRunner(algorithm, CaesarDatasets.tryIt)
        val decision = (r.probe() as Probe.Decide).decision
        val before = r.current.state
        val wrong = decision.options.map { it.action }.first { it != decision.correct }

        // Five wrong answers in a row, and the message is byte-for-byte where it was.
        repeat(5) { attempt ->
            val result = DecisionValidation.validate(decision, wrong, attempt)
            assertTrue("attempt $attempt", result is Validation.Retry)
        }
        assertEquals(before, r.current.state)
        assertEquals("", r.current.state.produced)
        assertEquals(0, r.current.state.index)
    }

    @Test
    fun `guidance escalates and then holds, so a learner is never dead-ended`() {
        val decision = (runner("ALGO", 2).probe() as Probe.Decide).decision
        val wrong = decision.options.map { it.action }.first { it != decision.correct }

        val rungs = (0..3).map {
            (DecisionValidation.validate(decision, wrong, it) as Validation.Retry).guidance
        }
        // Three distinct rungs...
        assertEquals(3, rungs.take(3).toSet().size)
        // ...and the most explicit one repeats rather than running out (ADR-021).
        assertEquals(rungs[2], rungs[3])
    }

    @Test
    fun `a correct selection is accepted and carries an action`() {
        val decision = (runner("ALGO", 2).probe() as Probe.Decide).decision
        val result = DecisionValidation.validate(decision, decision.correct, 0)
        assertTrue(result is Validation.Accept)
        assertEquals(decision.correct, (result as Validation.Accept).action)
    }

    @Test
    fun `a run of wrong guesses still ends on the right ciphertext`() {
        val r = AlgorithmRunner(algorithm, CaesarDatasets.tryIt)
        var guard = 0
        while (guard++ < 100) {
            val probe = r.probe()
            if (probe is Probe.Terminal) break
            val decision = (probe as Probe.Decide).decision
            for (option in decision.options) {
                if (option.action == decision.correct) continue
                // A Retry carries no action, so there is nothing to apply.
                assertTrue(
                    DecisionValidation.validate(decision, option.action, 0)
                        is Validation.Retry,
                )
            }
            r.apply(decision.correct)
        }
        assertEquals("CNIQ", r.current.state.produced)
        assertEquals(0, r.current.metrics.wrongDecisions)
    }

    @Test
    fun `applying a wrong letter directly still leaves a legal state that terminates`() {
        // `apply` is total (ADR-001) — it takes any action, which is what makes
        // `validate` a pure comparison. In TRY nothing ever calls it this way.
        val start = state("ALGO", 2)
        val diverged = algorithm.apply(start, CaesarAction.Encrypt('X'))
        assertEquals("X", diverged.next.produced)
        assertFalse(diverged.correct)

        var s = diverged.next
        var guard = 0
        while (guard++ < 100) {
            when (val probe = algorithm.probe(s)) {
                is Probe.Decide -> s = algorithm.apply(s, probe.decision.correct).next
                is Probe.Mechanical -> s = algorithm.apply(s, probe.action).next
                is Probe.Terminal -> break
            }
        }
        assertTrue(s.finished)
        assertEquals(4, s.produced.length)
    }

    @Test
    fun `adversarial driving terminates from every beat`() {
        for (seed in 0 until 40) {
            val r = AlgorithmRunner(algorithm, CaesarDatasets.watch)
            var guard = 0
            while (guard++ < 200) {
                when (val probe = r.probe()) {
                    is Probe.Decide -> {
                        val options = probe.decision.options
                        r.apply(options[(seed + guard) % options.size].action)
                    }
                    is Probe.Mechanical -> r.apply(probe.action)
                    is Probe.Terminal -> break
                }
            }
            assertTrue("seed $seed did not terminate", r.current.state.finished)
        }
    }

    @Test
    fun `an ill-timed action is a no-op rather than a crash`() {
        val end = driveCorrectly("HELLO", 3)
        // Encrypting past the end changes nothing.
        val after = algorithm.apply(end, CaesarAction.Encrypt('Z'))
        assertSame(end, after.next)
        assertFalse(after.correct)
        assertTrue(after.events.isEmpty())

        // Copying through while the cursor is on a letter changes nothing either.
        val onLetter = state("HELLO", 3)
        val copied = algorithm.apply(onLetter, CaesarAction.CopyThrough)
        assertSame(onLetter, copied.next)
    }

    // -- 7. Completion ---------------------------------------------------------

    @Test
    fun `completion happens only after every character is processed`() {
        val r = AlgorithmRunner(algorithm, CaesarDatasets.tryIt)
        repeat(3) {
            r.apply((r.probe() as Probe.Decide).decision.correct)
            // Three of four done: still not finished, still not terminal.
            assertFalse(r.current.state.finished)
            assertFalse(r.probe() is Probe.Terminal)
        }
        r.apply((r.probe() as Probe.Decide).decision.correct)

        assertTrue(r.current.state.finished)
        val probe = r.probe()
        assertTrue(probe is Probe.Terminal)
        assertEquals(Outcome.Completed(true), (probe as Probe.Terminal).outcome)
        assertTrue(r.current.events.any { it is VizEvent.Terminal })
    }

    @Test
    fun `rewind returns the exact previous state`() {
        val r = runner("ALGO", 2)
        val before = r.current.state
        r.apply((r.probe() as Probe.Decide).decision.correct)
        assertEquals(before, r.rewind().state)
    }

    // -- 8. Edge cases ---------------------------------------------------------

    @Test
    fun `single characters and all-wrapping messages are ordinary lessons`() {
        assertEquals("A", driveCorrectly("Z", 1).produced)
        assertEquals("D", driveCorrectly("A", 3).produced)
        assertEquals("ABC", driveCorrectly("XYZ", 3).produced)
        assertEquals("ABC", driveCorrectly("ABC", 0).produced)
    }

    @Test
    fun `an empty message is rejected where it is authored, not at runtime`() {
        // A lesson with nothing to encrypt is a bad dataset, and it is refused at
        // construction rather than producing a lesson with no beats in it.
        var threw = false
        try {
            CipherProblem("", 3)
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue("an empty plaintext should be refused", threw)
    }

    @Test
    fun `a message of only punctuation completes without asking anything`() {
        val r = AlgorithmRunner(algorithm, dataset("!!!", 3))
        var guard = 0
        while (guard++ < 20) {
            when (val probe = r.probe()) {
                is Probe.Mechanical -> r.apply(probe.action)
                is Probe.Decide -> error("punctuation is not a decision")
                is Probe.Terminal -> break
            }
        }
        assertEquals("!!!", r.current.state.produced)
    }

    @Test
    fun `a dataset with no cipher problem still produces a lesson`() {
        val fallback = algorithm.initial(Dataset(emptyList()))
        assertEquals("HELLO", fallback.plaintext)
        assertEquals(3, fallback.shift)
    }

    // -- 9. The picture --------------------------------------------------------

    @Test
    fun `the alphabet is drawn in full, always`() {
        val scene = projector.project(state("HELLO", 3), emptyList()) as CipherScene
        // All 26, including the letters this message never uses — the wrap is only
        // visible as a picture when X Y Z are on screen above A B C.
        assertEquals(26, scene.alphabet.size)
        assertEquals('A', scene.alphabet.first().from)
        assertEquals('D', scene.alphabet.first().to)
        assertEquals('Z', scene.alphabet.last().from)
        assertEquals('C', scene.alphabet.last().to)
    }

    @Test
    fun `exactly one mapping tile is lit, and it is the current letter`() {
        val scene = projector.project(state("HELLO", 3), emptyList()) as CipherScene
        val lit = scene.alphabet.filter { it.state == CellState.COMPARING }
        assertEquals(1, lit.size)
        assertEquals('H', lit.single().from)
        assertEquals('K', lit.single().to)
    }

    @Test
    fun `the ciphertext is holes until it is produced`() {
        val scene = projector.project(state("HELLO", 3), emptyList()) as CipherScene
        assertEquals(5, scene.ciphertext.size)
        // GHOST — the hole Insertion Sort established. A placeholder letter would
        // claim an answer exists before it does.
        assertTrue(scene.ciphertext.all { it.state == CellState.GHOST })
        assertTrue(scene.ciphertext.all { it.label == null })
        // The plaintext reads as letters, not as numbers.
        assertEquals(listOf("H", "E", "L", "L", "O"), scene.plaintext.map { it.label })
    }

    @Test
    fun `the strip shows the working and hides the result until it is known`() {
        val pending = projector.project(state("HELLO", 3), emptyList()) as CipherScene
        val asked = requireNotNull(pending.step)
        assertEquals('H', asked.from)
        assertEquals(7, asked.fromIndex)
        assertEquals(3, asked.shift)
        assertEquals(10, asked.sum)
        assertFalse(asked.wrapped)
        // An answer already on screen is not a question (ADR-030).
        assertNull(asked.toIndex)
        assertNull(asked.to)

        val r = runner("HELLO", 3)
        val frame = r.apply((r.probe() as Probe.Decide).decision.correct)
        val answered = requireNotNull(
            (projector.project(frame.state, frame.events) as CipherScene).step,
        )
        assertEquals('K', answered.to)
        assertEquals(10, answered.toIndex)
        // ...and it is still describing H, not the E the cursor has moved on to.
        assertEquals('H', answered.from)
    }

    @Test
    fun `the strip shows the subtraction when a letter wraps`() {
        val scene = projector.project(state("ZAP", 3), emptyList()) as CipherScene
        val step = requireNotNull(scene.step)
        assertTrue(step.wrapped)
        // 25 + 3 = 28, which the renderer shows becoming 2. A learner told only
        // "it wraps" has a word; one who watches 28 become 2 has the rule.
        assertEquals(28, step.sum)
        assertEquals(25, step.fromIndex)
    }

    @Test
    fun `the finished scene has no holes and no lit tile`() {
        val scene = projector.project(driveCorrectly("HELLO", 3), emptyList()) as CipherScene
        assertEquals(listOf("K", "H", "O", "O", "R"), scene.ciphertext.map { it.label })
        assertTrue(scene.ciphertext.none { it.state == CellState.GHOST })
        assertTrue(scene.alphabet.none { it.state == CellState.COMPARING })
        assertNull(scene.step)
    }

    // -- 10. The walkthrough ---------------------------------------------------

    private fun script() = AlgorithmCatalog.caesarCipher().watchScript()

    @Test
    fun `the walkthrough opens on the problem and closes on the idea`() {
        val steps = script().steps
        assertEquals(WatchStepKind.SETUP, steps.first().kind)
        assertEquals(WatchStepKind.INSIGHT, steps[steps.size - 2].kind)
        assertEquals(WatchStepKind.SUMMARY, steps.last().kind)
        // Four recap bullets: the rule, the mod, decryption, and the honest caveat.
        assertEquals(4, steps.last().bullets.size)
    }

    @Test
    fun `the walkthrough teaches the wrap, even though HELLO never wraps`() {
        // Z -> C is the half of this cipher a learner gets wrong, and HELLO gives
        // no occasion for it. So it gets its own beat against the mapping row.
        val ids = script().steps.map { it.headline.id }
        assertTrue(ids.contains(NarrationId.CC_WATCH_WRAP))
        // ...and it lands after the message is finished, so it reads as the extra
        // rule it is rather than as an interruption.
        val wrap = ids.indexOf(NarrationId.CC_WATCH_WRAP)
        val done = script().steps.indexOfFirst { it.kind == WatchStepKind.FOUND }
        assertTrue("the wrap should follow the message", done < wrap)
    }

    @Test
    fun `the wrap beat lights Z, so it points at the evidence it names`() {
        val wrap = script().steps.single { it.headline.id == NarrationId.CC_WATCH_WRAP }
        val scene = wrap.scene as CipherScene

        // The beat says "look at the end of the mapping row", so the end of the
        // mapping row is what is lit — not the letter the message finished on.
        val lit = scene.alphabet.single { it.state == CellState.COMPARING }
        assertEquals('Z', lit.from)
        assertEquals('C', lit.to)

        // ...and the strip shows the subtraction rather than asserting a wrap.
        val step = requireNotNull(scene.step)
        assertTrue(step.wrapped)
        assertEquals(28, step.sum)
        assertEquals('C', step.to)

        // The message itself is untouched: this is an aside about the alphabet,
        // not the run doing something more.
        val finished = script().steps.last().scene as CipherScene
        assertEquals(finished.plaintext, scene.plaintext)
        assertEquals(finished.ciphertext, scene.ciphertext)
    }

    @Test
    fun `no more than two steps in a row show the same picture`() {
        // ADR-020's rule, with the soft edge every lesson's closing has: the
        // insight and the recap genuinely do sit on the finished picture. Three
        // would mean a beat that has stopped earning its screen.
        val steps = script().steps
        var longest = 1
        var run = 1
        for (i in 1 until steps.size) {
            run = if (steps[i].scene == steps[i - 1].scene) run + 1 else 1
            longest = maxOf(longest, run)
        }
        assertTrue("$longest steps in a row showed the same picture", longest <= 2)
    }

    @Test
    fun `the walkthrough names decryption once, and does not build it`() {
        val ids = script().steps.flatMap { listOf(it.headline) + it.bullets }.map { it.id }
        assertEquals(1, ids.count { it == NarrationId.CC_IDEA_3 })
    }

    @Test
    fun `the walkthrough narrates the opening letters and collapses the repeats`() {
        val steps = script().steps
        val letters = steps.count { it.headline.id == NarrationId.CC_WATCH_LETTER }
        val collapsed = steps.count { it.headline.id == NarrationId.CC_WATCH_COLLAPSED }
        // H and E in full, LL as one beat, O as the finale.
        assertEquals(3, letters)
        assertEquals(1, collapsed)
        assertEquals(1, steps.count { it.kind == WatchStepKind.FOUND })
    }

    @Test
    fun `the walkthrough is a readable length`() {
        val size = script().size
        assertTrue("walkthrough was $size steps", size in 8..14)
    }

    @Test
    fun `no two adjacent walkthrough steps are identical`() {
        // ADR-020: a step where nothing changed is a bug, not a beat.
        val steps = script().steps
        for (i in 1 until steps.size) {
            val a = steps[i - 1]
            val b = steps[i]
            assertTrue(
                "steps $i and ${i - 1} say and show the same thing",
                a.headline != b.headline || a.scene != b.scene || a.support != b.support,
            )
        }
    }

    @Test
    fun `the walkthrough ends on the ciphertext it set out to produce`() {
        val last = script().steps.last()
        assertEquals(listOf("HELLO", "KHOOR"), last.headline.args.map { it.toString() })
        assertEquals(
            listOf("K", "H", "O", "O", "R"),
            (last.scene as CipherScene).ciphertext.map { it.label },
        )
    }

    // -- 11. Registration and progress -----------------------------------------

    @Test
    fun `the lesson is in the catalog and wired to its own datasets`() {
        val pack = AlgorithmCatalog.byId(AlgorithmId.CAESAR_CIPHER)
        assertEquals(AlgorithmId.CAESAR_CIPHER, pack.id)
        assertEquals("Caesar Cipher", pack.displayName)
        assertEquals(CaesarDatasets.watch, pack.watchDataset)
        assertEquals(CaesarDatasets.tryIt, pack.tryDataset)
        // WATCH and TRY are different messages, so TRY is application not recall.
        assertEquals("HELLO", pack.watchDataset.cipher?.plaintext)
        assertEquals("ALGO", pack.tryDataset.cipher?.plaintext)
        assertEquals(3, pack.watchDataset.cipher?.shift)
        assertEquals(2, pack.tryDataset.cipher?.shift)
    }

    @Test
    fun `there is no challenge, the same as every other MVP lesson`() {
        assertNull(ChallengeCatalog.byId(AlgorithmId.CAESAR_CIPHER))
    }

    @Test
    fun `progress runs nought, fifty, one hundred and cannot go backwards`() {
        var progress = AlgorithmProgress()
        assertEquals(0, progress.percent)

        progress = progress.complete(Stage.WATCH)
        assertEquals(50, progress.percent)

        progress = progress.complete(Stage.TRY)
        assertEquals(100, progress.percent)

        // Latched and additive (ADR-028): repeating a stage cannot subtract.
        progress = progress.complete(Stage.WATCH)
        assertEquals(100, progress.percent)
    }
}
