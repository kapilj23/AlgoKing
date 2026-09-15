package com.algorithms.algoking.engine

import com.algorithms.algoking.engine.algorithms.xor.XorAction
import com.algorithms.algoking.engine.algorithms.xor.XorCipherAlgorithm
import com.algorithms.algoking.engine.algorithms.xor.XorPhase
import com.algorithms.algoking.engine.algorithms.xor.XorProjector
import com.algorithms.algoking.engine.algorithms.xor.XorState
import com.algorithms.algoking.engine.catalog.AlgorithmCatalog
import com.algorithms.algoking.engine.challenge.ChallengeCatalog
import com.algorithms.algoking.engine.core.AlgorithmId
import com.algorithms.algoking.engine.core.AlgorithmRunner
import com.algorithms.algoking.engine.core.Dataset
import com.algorithms.algoking.engine.core.Probe
import com.algorithms.algoking.engine.core.XorProblem
import com.algorithms.algoking.engine.core.xorBit
import com.algorithms.algoking.engine.core.xorBits
import com.algorithms.algoking.engine.dataset.XorDatasets
import com.algorithms.algoking.engine.decision.DecisionKind
import com.algorithms.algoking.engine.decision.DecisionValidation
import com.algorithms.algoking.engine.decision.Validation
import com.algorithms.algoking.engine.event.Outcome
import com.algorithms.algoking.engine.event.VizEvent
import com.algorithms.algoking.engine.narration.NarrationId
import com.algorithms.algoking.engine.progress.AlgorithmProgress
import com.algorithms.algoking.engine.progress.Stage
import com.algorithms.algoking.engine.scene.BitwiseScene
import com.algorithms.algoking.engine.scene.CellState
import com.algorithms.algoking.engine.walkthrough.WatchStepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * XOR Cipher — one operation, and the fact that applying it twice undoes it.
 *
 * Every expected value asserted here is checked against a **separately written
 * bit-by-bit comparison** rather than against the engine's own output, so a bug in
 * the transform cannot make its own test pass.
 */
class XorCipherTest {

    private val algorithm = XorCipherAlgorithm()
    private val projector = XorProjector()

    private fun dataset(plain: String, key: String, roundTrip: Boolean = false) =
        Dataset(emptyList(), xor = XorProblem(plain, key, roundTrip))

    private fun runner(plain: String, key: String, roundTrip: Boolean = false) =
        AlgorithmRunner(algorithm, dataset(plain, key, roundTrip))

    private fun state(plain: String, key: String, roundTrip: Boolean = false) =
        algorithm.initial(dataset(plain, key, roundTrip))

    /** Drive to completion the way WATCH does — always the correct option. */
    private fun driveCorrectly(
        plain: String,
        key: String,
        roundTrip: Boolean = false,
    ): XorState {
        val r = runner(plain, key, roundTrip)
        var guard = 0
        while (guard++ < 500) {
            when (val probe = r.probe()) {
                is Probe.Mechanical -> r.apply(probe.action)
                is Probe.Decide -> r.apply(probe.decision.correct)
                is Probe.Terminal -> return r.current.state
            }
        }
        error("XOR Cipher did not terminate.")
    }

    /**
     * The rule, written out independently — a character comparison rather than the
     * engine's inequality, so it shares no code with the implementation.
     */
    private fun referenceXor(a: String, b: String): String = buildString {
        for (i in a.indices) append(if (a[i] == b[i]) '0' else '1')
    }

    // -- 1. The operation ------------------------------------------------------

    @Test
    fun `the documented examples are what the engine produces`() {
        // The five the brief specifies, value by value.
        assertEquals("0110", xorBits("1010", "1100"))
        assertEquals("1010", xorBits("0110", "1100"))
        assertEquals("0110", xorBits("1011", "1101"))
        // XOR with zeros returns the original...
        assertEquals("1010", xorBits("1010", "0000"))
        // ...and XOR with itself returns zeros.
        assertEquals("0000", xorBits("1010", "1010"))
    }

    @Test
    fun `the truth table is the four rows it should be`() {
        assertEquals(0, xorBit(0, 0))
        assertEquals(1, xorBit(0, 1))
        assertEquals(1, xorBit(1, 0))
        assertEquals(0, xorBit(1, 1))
    }

    @Test
    fun `the transform agrees with an independent bit comparison`() {
        // Exhaustive over every 4-bit pair — 256 combinations, all of them.
        for (a in 0 until 16) {
            for (b in 0 until 16) {
                val left = a.toString(2).padStart(4, '0')
                val right = b.toString(2).padStart(4, '0')
                assertEquals(
                    "$left XOR $right",
                    referenceXor(left, right),
                    xorBits(left, right),
                )
            }
        }
    }

    @Test
    fun `applying the key twice always gives the original back`() {
        // The claim the whole lesson rests on, over every 4-bit pair.
        for (a in 0 until 16) {
            for (b in 0 until 16) {
                val plain = a.toString(2).padStart(4, '0')
                val key = b.toString(2).padStart(4, '0')
                assertEquals(plain, xorBits(xorBits(plain, key), key))
            }
        }
    }

    // -- 2. Validation ---------------------------------------------------------

    private fun refused(block: () -> Unit): Boolean = try {
        block()
        false
    } catch (e: IllegalArgumentException) {
        true
    }

    @Test
    fun `unequal lengths are refused where they are authored`() {
        assertTrue(refused { XorProblem("1010", "110") })
        assertTrue(refused { XorProblem("101", "1100") })
        // ...and the loose function refuses them too, rather than truncating — a
        // shorter answer than the input is the kind of wrong that looks right.
        assertTrue(refused { xorBits("1010", "110") })
    }

    @Test
    fun `invalid characters are refused`() {
        assertTrue(refused { XorProblem("10A0", "1100") })
        assertTrue(refused { XorProblem("1010", "11 0") })
        assertTrue(refused { XorProblem("1012", "1100") })
        assertTrue(refused { XorProblem("hello", "world") })
    }

    @Test
    fun `empty input is refused`() {
        assertTrue(refused { XorProblem("", "") })
        assertTrue(refused { XorProblem("", "1100") })
        assertTrue(refused { XorProblem("1010", "") })
    }

    @Test
    fun `a bit index outside the string is refused rather than clamped`() {
        assertTrue(refused { com.algorithms.algoking.engine.core.bitAt("1010", 4) })
        assertTrue(refused { com.algorithms.algoking.engine.core.bitAt("1010", -1) })
    }

    // -- 3. TRY: correct answers advance --------------------------------------

    @Test
    fun `the authored TRY message encrypts to 0110, one bit at a time`() {
        val r = AlgorithmRunner(algorithm, XorDatasets.tryIt)
        val asked = mutableListOf<Pair<Int, Int>>()
        var guard = 0
        while (guard++ < 100) {
            val probe = r.probe()
            if (probe is Probe.Terminal) break
            val s = r.current.state
            asked += requireNotNull(s.currentInputBit) to requireNotNull(s.currentKeyBit)
            val before = s.produced.length
            r.apply((probe as Probe.Decide).decision.correct)
            // A correct selection advances the state by exactly one bit.
            assertEquals(before + 1, r.current.state.produced.length)
        }
        assertEquals(listOf(1 to 1, 0 to 1, 1 to 0, 1 to 1), asked)
        assertEquals("0110", r.current.state.produced)
        assertEquals("0110", referenceXor("1011", "1101"))
    }

    @Test
    fun `the WATCH message encrypts and then comes back`() {
        val end = driveCorrectly("1010", "1100", roundTrip = true)
        assertEquals("0110", end.ciphertext)
        // The recovered value is read out of the run, not copied from the problem:
        // if applying the key twice did not give the plaintext back, this fails.
        assertEquals("1010", end.recovered)
        assertEquals(end.problem.plaintext, end.recovered)
        assertEquals(XorPhase.DECRYPT, end.phase)
    }

    @Test
    fun `every decision offers exactly 0 and 1, in that order`() {
        val r = AlgorithmRunner(algorithm, XorDatasets.watch)
        var guard = 0
        while (guard++ < 100) {
            val probe = r.probe()
            if (probe is Probe.Terminal) break
            val decision = (probe as Probe.Decide).decision
            assertEquals(DecisionKind.OPTIONS, decision.kind)
            // Two options, always the same two, always in the same order. A row
            // whose contents changed with the answer would let a learner read the
            // answer off the row rather than off the bits.
            assertEquals(
                listOf(XorAction.SetBit(0), XorAction.SetBit(1)),
                decision.options.map { it.action },
            )
            assertTrue(decision.options.any { it.action == decision.correct })
            // The one wrong option explains itself.
            val wrong = decision.options.map { it.action }.first { it != decision.correct }
            assertNotNull(decision.whyWrong[wrong])
            r.apply(decision.correct)
        }
    }

    @Test
    fun `the feedback names the half of the rule the learner missed`() {
        // 1 XOR 1 — both the same, so the wrong answer is "1" and the reason is
        // that matching bits give 0.
        val same = (runner("1010", "1100").probe() as Probe.Decide).decision
        assertEquals(XorAction.SetBit(0), same.correct)
        assertEquals(
            NarrationId.XOR_WHY_SAME_IS_ZERO,
            same.whyWrong[XorAction.SetBit(1)]?.id,
        )

        // 0 XOR 1 — different, so the wrong answer is "0" and the reason is that
        // differing bits give 1.
        val r = runner("1010", "1100")
        r.apply((r.probe() as Probe.Decide).decision.correct)
        val different = (r.probe() as Probe.Decide).decision
        assertEquals(XorAction.SetBit(1), different.correct)
        assertEquals(
            NarrationId.XOR_WHY_DIFFERENT_IS_ONE,
            different.whyWrong[XorAction.SetBit(0)]?.id,
        )
    }

    // -- 4. TRY: wrong answers do not advance ---------------------------------

    @Test
    fun `a wrong selection is refused and the state does not move`() {
        val r = AlgorithmRunner(algorithm, XorDatasets.tryIt)
        val decision = (r.probe() as Probe.Decide).decision
        val before = r.current.state
        val wrong = decision.options.map { it.action }.first { it != decision.correct }

        // Five wrong answers in a row, and the result is byte-for-byte where it was.
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
        val decision = (runner("1011", "1101").probe() as Probe.Decide).decision
        val wrong = decision.options.map { it.action }.first { it != decision.correct }
        val rungs = (0..3).map {
            (DecisionValidation.validate(decision, wrong, it) as Validation.Retry).guidance
        }
        assertEquals(3, rungs.take(3).toSet().size)
        assertEquals(rungs[2], rungs[3])
    }

    @Test
    fun `a correct selection is accepted and carries an action`() {
        val decision = (runner("1011", "1101").probe() as Probe.Decide).decision
        val result = DecisionValidation.validate(decision, decision.correct, 0)
        assertTrue(result is Validation.Accept)
        assertEquals(decision.correct, (result as Validation.Accept).action)
    }

    @Test
    fun `a run of wrong guesses still ends on the right ciphertext`() {
        val r = AlgorithmRunner(algorithm, XorDatasets.tryIt)
        var guard = 0
        while (guard++ < 100) {
            val probe = r.probe()
            if (probe is Probe.Terminal) break
            val decision = (probe as Probe.Decide).decision
            val wrong = decision.options.map { it.action }.first { it != decision.correct }
            // A Retry carries no action, so there is nothing to apply.
            assertTrue(DecisionValidation.validate(decision, wrong, 0) is Validation.Retry)
            r.apply(decision.correct)
        }
        assertEquals("0110", r.current.state.produced)
        assertEquals(0, r.current.metrics.wrongDecisions)
    }

    @Test
    fun `applying a wrong bit directly still leaves a legal state that terminates`() {
        // `apply` is total (ADR-001) — it takes any action, which is what makes
        // `validate` a pure comparison. In TRY nothing ever calls it this way.
        val start = state("1011", "1101")
        val diverged = algorithm.apply(start, XorAction.SetBit(1))
        assertEquals("1", diverged.next.produced)
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
    fun `a value that is not a bit is a no-op`() {
        val start = state("1011", "1101")
        for (bad in listOf(-1, 2, 7)) {
            val after = algorithm.apply(start, XorAction.SetBit(bad))
            assertSame("bit $bad", start, after.next)
            assertFalse(after.correct)
            assertTrue(after.events.isEmpty())
        }
    }

    @Test
    fun `setting a bit past the end is a no-op`() {
        val end = driveCorrectly("1011", "1101")
        val after = algorithm.apply(end, XorAction.SetBit(1))
        assertSame(end, after.next)
        assertFalse(after.correct)
        assertTrue(after.events.isEmpty())
    }

    @Test
    fun `adversarial driving terminates from every beat`() {
        for (seed in 0 until 40) {
            val r = AlgorithmRunner(algorithm, XorDatasets.watch)
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

    // -- 5. Completion ---------------------------------------------------------

    @Test
    fun `completion happens only after every bit is processed`() {
        val r = AlgorithmRunner(algorithm, XorDatasets.tryIt)
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
    fun `a round-trip lesson is not finished when the first pass ends`() {
        val r = AlgorithmRunner(algorithm, XorDatasets.watch)
        repeat(4) { r.apply((r.probe() as Probe.Decide).decision.correct) }

        // The first pass is done and the second has not started being asked.
        assertEquals("0110", r.current.state.ciphertext)
        assertEquals(XorPhase.DECRYPT, r.current.state.phase)
        assertFalse(r.current.state.finished)
        assertTrue(r.probe() is Probe.Decide)

        repeat(4) { r.apply((r.probe() as Probe.Decide).decision.correct) }
        assertTrue(r.current.state.finished)
        assertEquals("1010", r.current.state.recovered)
    }

    @Test
    fun `rewind returns the exact previous state`() {
        val r = runner("1011", "1101")
        val before = r.current.state
        r.apply((r.probe() as Probe.Decide).decision.correct)
        assertEquals(before, r.rewind().state)
    }

    // -- 6. Edge cases ---------------------------------------------------------

    @Test
    fun `degenerate keys are legal lessons`() {
        // An all-zero key changes nothing...
        assertEquals("1010", driveCorrectly("1010", "0000").produced)
        // ...an all-one key flips everything...
        assertEquals("0101", driveCorrectly("1010", "1111").produced)
        // ...and a key equal to the message zeroes it.
        assertEquals("0000", driveCorrectly("1010", "1010").produced)
    }

    @Test
    fun `a single bit is an ordinary lesson`() {
        assertEquals("1", driveCorrectly("1", "0").produced)
        assertEquals("0", driveCorrectly("1", "1").produced)
        assertEquals("1", driveCorrectly("1", "0", roundTrip = true).recovered)
    }

    @Test
    fun `longer messages stay correct`() {
        val plain = "11010010"
        val key = "01101011"
        assertEquals(referenceXor(plain, key), driveCorrectly(plain, key).produced)
        assertEquals(plain, driveCorrectly(plain, key, roundTrip = true).recovered)
    }

    @Test
    fun `a dataset with no xor problem still produces a lesson`() {
        val fallback = algorithm.initial(Dataset(emptyList()))
        assertEquals("1010", fallback.problem.plaintext)
        assertEquals("1100", fallback.key)
    }

    // -- 7. The picture --------------------------------------------------------

    @Test
    fun `three rows share one set of columns`() {
        val scene = projector.project(state("1010", "1100"), emptyList()) as BitwiseScene
        assertEquals(3, scene.rows.size)
        assertEquals(listOf("Plaintext", "Key", "Ciphertext"), scene.rows.map { it.label })
        // The shared column is the operation, so every row has to have all of them.
        assertTrue(scene.rows.all { it.bits.size == 4 })
        assertEquals(4, scene.width)
    }

    @Test
    fun `the result row is holes until it is decided`() {
        val scene = projector.project(state("1010", "1100"), emptyList()) as BitwiseScene
        val result = scene.rows.last().bits
        // GHOST, never a `0` — and here that matters more than anywhere else in
        // the app, because `0` is a real answer in this table.
        assertTrue(result.all { it.state == CellState.GHOST })
        assertTrue(result.all { it.label == null })
        // The two input rows read as bits.
        assertEquals(listOf("1", "0", "1", "0"), scene.rows[0].bits.map { it.label })
        assertEquals(listOf("1", "1", "0", "0"), scene.rows[1].bits.map { it.label })
    }

    @Test
    fun `the truth table is always four rows, with the one in play lit`() {
        val scene = projector.project(state("1010", "1100"), emptyList()) as BitwiseScene
        assertEquals(4, scene.truthTable.size)
        // The first column is 1 XOR 1, so that is the row the learner should be
        // reading — and the picture makes the lookup rather than describing it.
        val active = scene.truthTable.filter { it.active }
        assertEquals(1, active.size)
        assertEquals(1, active.single().a)
        assertEquals(1, active.single().b)
        assertEquals(0, active.single().result)
    }

    @Test
    fun `the strip shows the working and hides the result until it is known`() {
        val pending = projector.project(state("1010", "1100"), emptyList()) as BitwiseScene
        val asked = requireNotNull(pending.step)
        assertEquals(0, asked.index)
        assertEquals(1, asked.a)
        assertEquals(1, asked.b)
        // An answer already on screen is not a question (ADR-030).
        assertNull(asked.result)

        val r = runner("1010", "1100")
        val frame = r.apply((r.probe() as Probe.Decide).decision.correct)
        val answered = requireNotNull(
            (projector.project(frame.state, frame.events) as BitwiseScene).step,
        )
        assertEquals(0, answered.result)
        // ...and it still describes column 0, not the column the cursor moved to.
        assertEquals(0, answered.index)
    }

    @Test
    fun `the row labels change with the phase and nothing else does`() {
        val r = AlgorithmRunner(algorithm, XorDatasets.watch)
        repeat(4) { r.apply((r.probe() as Probe.Decide).decision.correct) }
        val scene = projector.project(r.current.state, emptyList()) as BitwiseScene

        // The second pass reads the ciphertext the first pass actually produced.
        assertEquals(listOf("Ciphertext", "Key", "Recovered"), scene.rows.map { it.label })
        assertEquals(listOf("0", "1", "1", "0"), scene.rows[0].bits.map { it.label })
        assertEquals(listOf("1", "1", "0", "0"), scene.rows[1].bits.map { it.label })
        assertTrue(scene.rows[2].bits.all { it.state == CellState.GHOST })
    }

    @Test
    fun `the frame that finishes encrypting still draws the encrypting pass`() {
        // The bug this pins: the state after the last encrypted bit has already
        // derived its way into the second pass, so drawing *the state's* phase
        // relabels the rows and blanks the result — underneath a sentence reading
        // "1010 ⊕ 1100 = 0110". The projector picks the pass the *frame* belongs
        // to, from the events it carries.
        val r = AlgorithmRunner(algorithm, XorDatasets.watch)
        var frame = r.current
        repeat(4) { frame = r.apply((r.probe() as Probe.Decide).decision.correct) }

        val scene = projector.project(frame.state, frame.events) as BitwiseScene
        assertEquals(listOf("Plaintext", "Key", "Ciphertext"), scene.rows.map { it.label })
        // The whole ciphertext is on screen, which is what the copy is about.
        assertEquals(listOf("0", "1", "1", "0"), scene.rows.last().bits.map { it.label })
        assertTrue(scene.rows.last().bits.none { it.state == CellState.GHOST })
        // ...and the strip is still describing the column just decided.
        assertEquals(3, requireNotNull(scene.step).index)
        assertEquals(0, requireNotNull(scene.step).result)

        // The state itself has moved on, which is exactly why the projector may
        // not read it for this.
        assertEquals(XorPhase.DECRYPT, frame.state.phase)
    }

    @Test
    fun `a lesson that never goes back reports its answer after it finishes`() {
        // The second bug the restructure exposed: deriving the phase purely from
        // the encrypted length put a non-round-trip lesson into a second pass that
        // never runs, so `produced` came back empty at completion.
        val end = driveCorrectly("1011", "1101", roundTrip = false)
        assertTrue(end.finished)
        assertEquals(XorPhase.ENCRYPT, end.phase)
        assertEquals("0110", end.produced)
        assertEquals("0110", end.ciphertext)
        // Nothing was recovered, because nothing was asked to be.
        assertNull(end.recovered)

        val scene = projector.project(end, emptyList()) as BitwiseScene
        assertEquals(listOf("0", "1", "1", "0"), scene.rows.last().bits.map { it.label })
    }

    @Test
    fun `the finished scene has no holes`() {
        val scene = projector.project(
            driveCorrectly("1010", "1100", roundTrip = true),
            emptyList(),
        ) as BitwiseScene
        assertEquals(listOf("1", "0", "1", "0"), scene.rows.last().bits.map { it.label })
        assertTrue(scene.rows.last().bits.none { it.state == CellState.GHOST })
    }

    // -- 8. The walkthrough ----------------------------------------------------

    private fun script() = AlgorithmCatalog.xorCipher().watchScript()

    @Test
    fun `the walkthrough opens on the rule and closes on the idea`() {
        val steps = script().steps
        assertEquals(WatchStepKind.SETUP, steps.first().kind)
        assertEquals(WatchStepKind.INSIGHT, steps[steps.size - 2].kind)
        assertEquals(WatchStepKind.SUMMARY, steps.last().kind)
        // Four recap bullets: the rule, the reversal, the cost, and the caveat.
        assertEquals(4, steps.last().bullets.size)
    }

    @Test
    fun `the walkthrough says out loud that this is not secure encryption`() {
        // A lesson that leaves a learner thinking they have seen encryption has
        // taught them something worse than nothing, so the caveat is pinned.
        val ids = script().steps.flatMap { listOf(it.headline) + it.bullets }.map { it.id }
        assertTrue(ids.contains(NarrationId.XOR_IDEA_4))
    }

    @Test
    fun `the walkthrough narrates every encrypting column and collapses the way back`() {
        val steps = script().steps
        // Four bits out — and those four happen to be all four rows of the truth
        // table, which is why none of them is collapsed.
        assertEquals(4, steps.count { it.headline.id == NarrationId.XOR_WATCH_BIT })
        // The way back is one beat of setup and one of payoff.
        assertEquals(1, steps.count { it.headline.id == NarrationId.XOR_WATCH_REVERSE })
        assertEquals(1, steps.count { it.headline.id == NarrationId.XOR_WATCH_RECOVERED })
    }

    @Test
    fun `the walkthrough covers all four rows of the truth table`() {
        // The dataset's claim, asserted: 1^1, 0^1, 1^0, 0^0 is the whole rule.
        val pairs = script().steps
            .filter { it.headline.id == NarrationId.XOR_WATCH_BIT }
            .map { it.headline.args[0] to it.headline.args[1] }
        assertEquals(listOf(1 to 1, 0 to 1, 1 to 0, 0 to 0), pairs)
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
    fun `no more than three steps in a row show the same picture`() {
        // ADR-020's rule, with the soft edge every lesson's closing has: the beat
        // that finishes the run, the insight and the recap all sit on the finished
        // picture, and there is nothing further for it to show. Three is the house
        // norm — Caesar only manages two because its wrap beat lights a different
        // letter between them, and it has one because it had something left to
        // point at. A fourth would mean a beat that has stopped earning its screen.
        val steps = script().steps
        var longest = 1
        var run = 1
        for (i in 1 until steps.size) {
            run = if (steps[i].scene == steps[i - 1].scene) run + 1 else 1
            longest = maxOf(longest, run)
        }
        assertTrue("$longest steps in a row showed the same picture", longest <= 3)
    }

    @Test
    fun `the walkthrough ends on the original, recovered`() {
        val last = script().steps.last().scene as BitwiseScene
        assertEquals(listOf("1", "0", "1", "0"), last.rows.last().bits.map { it.label })
    }

    // -- 9. Registration and progress ------------------------------------------

    @Test
    fun `the lesson is in the catalog and wired to its own datasets`() {
        val pack = AlgorithmCatalog.byId(AlgorithmId.XOR_CIPHER)
        assertEquals(AlgorithmId.XOR_CIPHER, pack.id)
        assertEquals("XOR Cipher", pack.displayName)
        assertEquals(XorDatasets.watch, pack.watchDataset)
        assertEquals(XorDatasets.tryIt, pack.tryDataset)
        // WATCH goes both ways; TRY stops at the ciphertext.
        assertEquals(true, pack.watchDataset.xor?.roundTrip)
        assertEquals(false, pack.tryDataset.xor?.roundTrip)
        assertEquals(8, pack.watchDataset.xor?.decisionCount)
        assertEquals(4, pack.tryDataset.xor?.decisionCount)
    }

    @Test
    fun `there is no challenge, the same as every other MVP lesson`() {
        assertNull(ChallengeCatalog.byId(AlgorithmId.XOR_CIPHER))
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
