package com.algorithms.algoking.engine

import com.algorithms.algoking.engine.algorithms.aes.AesAction
import com.algorithms.algoking.engine.algorithms.aes.AesEncryptionAlgorithm
import com.algorithms.algoking.engine.algorithms.aes.AesProjector
import com.algorithms.algoking.engine.algorithms.aes.AesState
import com.algorithms.algoking.engine.catalog.AlgorithmCatalog
import com.algorithms.algoking.engine.challenge.ChallengeCatalog
import com.algorithms.algoking.engine.core.Aes
import com.algorithms.algoking.engine.core.AesProblem
import com.algorithms.algoking.engine.core.AesQuestion
import com.algorithms.algoking.engine.core.AesStepKind
import com.algorithms.algoking.engine.core.AesTransformation
import com.algorithms.algoking.engine.core.AesVariant
import com.algorithms.algoking.engine.core.AlgorithmId
import com.algorithms.algoking.engine.core.AlgorithmRunner
import com.algorithms.algoking.engine.core.Dataset
import com.algorithms.algoking.engine.core.Probe
import com.algorithms.algoking.engine.dataset.AesDatasets
import com.algorithms.algoking.engine.decision.DecisionKind
import com.algorithms.algoking.engine.decision.DecisionValidation
import com.algorithms.algoking.engine.decision.Validation
import com.algorithms.algoking.engine.event.Outcome
import com.algorithms.algoking.engine.event.VizEvent
import com.algorithms.algoking.engine.progress.AlgorithmProgress
import com.algorithms.algoking.engine.progress.Stage
import com.algorithms.algoking.engine.scene.BlockCipherScene
import com.algorithms.algoking.engine.scene.CellState
import com.algorithms.algoking.engine.scene.RoundStepState
import com.algorithms.algoking.engine.walkthrough.WatchStepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * AES — the lesson, and the cipher underneath it.
 *
 * The first group is the one that matters most. This is the only lesson in the
 * library whose picture is a real algorithm's *intermediate* values, so the
 * transformations are checked three ways: against the S-box regenerated from its
 * mathematical definition, against **FIPS-197's published worked examples**
 * including their round-by-round States, and against the **JDK's own AES**. If any
 * of those disagree, the bytes the learner is shown are not AES's.
 */
class AesEncryptionTest {

    // ── 1. The cipher is really AES ──────────────────────────────────────────

    /**
     * The published S-box, rebuilt from what it actually is.
     *
     * Every entry is the multiplicative inverse of its index in GF(2⁸) — with 0
     * mapped to 0 — followed by the standard affine transform. Writing the table
     * out and then regenerating it means a mistyped byte fails the build rather
     * than silently producing a cipher that is almost AES.
     */
    @Test
    fun `the S-box is the one the definition produces`() {
        val generated = IntArray(256) { index ->
            val inverse = if (index == 0) 0 else gfInverse(index)
            var result = inverse
            var value = inverse
            repeat(4) {
                value = ((value shl 1) or (value ushr 7)) and 0xFF
                result = result xor value
            }
            (result xor 0x63) and 0xFF
        }

        assertEquals(256, Aes.SBOX.size)
        generated.indices.forEach { index ->
            assertEquals(
                "S-box entry ${Aes.hexByte(index)}",
                generated[index],
                Aes.SBOX[index],
            )
        }
    }

    /** FIPS-197 Appendix C.1 — the canonical AES-128 vector. */
    @Test
    fun `FIPS-197 Appendix C1 produces the published ciphertext`() {
        val ciphertext = Aes.ciphertext(
            block = Aes.bytesOf("00112233445566778899aabbccddeeff"),
            key = Aes.bytesOf("000102030405060708090a0b0c0d0e0f"),
            variant = AesVariant.AES_128,
        )
        assertEquals("69c4e0d86a7b0430d8cdb78070b4c55a", Aes.hex(ciphertext))
    }

    /** FIPS-197 Appendix B — the worked example the standard walks through. */
    @Test
    fun `FIPS-197 Appendix B produces the published ciphertext`() {
        val ciphertext = Aes.ciphertext(
            block = Aes.bytesOf("3243f6a8885a308d313198a2e0370734"),
            key = Aes.bytesOf("2b7e151628aed2a6abf7158809cf4f3c"),
            variant = AesVariant.AES_128,
        )
        assertEquals("3925841d02dc09fbdc118597196a0b32", Aes.hex(ciphertext))
    }

    /**
     * The intermediate States, not just the answer.
     *
     * A cipher can produce the right ciphertext from wrong-but-self-cancelling
     * steps, and this lesson *draws* the steps — so the published round-by-round
     * values are the assertion that actually protects the picture. These are the
     * `start`, `s_box`, `s_row` and `m_col` values FIPS-197 Appendix B prints for
     * round 1, plus the final round's output.
     */
    @Test
    fun `the intermediate States match the ones FIPS-197 publishes`() {
        val steps = Aes.encryptBlock(
            block = Aes.bytesOf("3243f6a8885a308d313198a2e0370734"),
            key = Aes.bytesOf("2b7e151628aed2a6abf7158809cf4f3c"),
            variant = AesVariant.AES_128,
        )

        // After the initial AddRoundKey — Appendix B's "round 1 start".
        val initial = steps.single { it.kind == AesStepKind.INITIAL_ADD_ROUND_KEY }
        assertEquals("193de3bea0f4e22b9ac68d2ae9f84808", Aes.hex(initial.after))

        val roundOne = steps.filter {
            it.kind == AesStepKind.ROUND_TRANSFORM && it.round == 1
        }
        assertEquals(4, roundOne.size)
        assertEquals(
            "d42711aee0bf98f1b8b45de51e415230",
            Aes.hex(roundOne[0].after), // after SubBytes
        )
        assertEquals(
            "d4bf5d30e0b452aeb84111f11e2798e5",
            Aes.hex(roundOne[1].after), // after ShiftRows
        )
        assertEquals(
            "046681e5e0cb199a48f8d37a2806264c",
            Aes.hex(roundOne[2].after), // after MixColumns
        )
        assertEquals(
            "a49c7ff2689f352b6b5bea43026a5049",
            Aes.hex(roundOne[3].after), // after AddRoundKey
        )
    }

    /** And the whole thing, against an implementation nobody here wrote. */
    @Test
    fun `every variant agrees with the JDK's own AES`() {
        val block = Aes.bytesOf("00112233445566778899aabbccddeeff")
        val keys = mapOf(
            AesVariant.AES_128 to "000102030405060708090a0b0c0d0e0f",
            AesVariant.AES_192 to "000102030405060708090a0b0c0d0e0f1011121314151617",
            AesVariant.AES_256 to
                "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f",
        )

        keys.forEach { (variant, keyHex) ->
            val key = Aes.bytesOf(keyHex)
            assertEquals(
                "${variant.label} against the JDK",
                jdkEncryptBlock(block, key),
                Aes.hex(Aes.ciphertext(block, key, variant)),
            )
        }
    }

    /** Both teaching datasets, against the JDK, so neither can drift. */
    @Test
    fun `both lesson datasets agree with the JDK`() {
        listOf(AesDatasets.watch, AesDatasets.tryIt).forEach { dataset ->
            val problem = requireNotNull(dataset.aes)
            assertEquals(
                "${dataset.label} ciphertext",
                jdkEncryptBlock(problem.block, problem.key),
                Aes.hex(problem.ciphertext),
            )
        }
    }

    // ── 2. The numbers the lesson teaches ────────────────────────────────────

    @Test
    fun `a block is 128 bits and 16 bytes, in every variant`() {
        assertEquals(128, Aes.BLOCK_BITS)
        assertEquals(16, Aes.BLOCK_BYTES)
        AesVariant.entries.forEach { variant ->
            val problem = AesProblem(
                variant = variant,
                block = IntArray(Aes.BLOCK_BYTES) { it },
                key = IntArray(variant.keyBytes) { it },
                questions = listOf(AesQuestion.BlockSize),
            )
            assertEquals(Aes.BLOCK_BYTES, problem.block.size)
            assertEquals(Aes.BLOCK_BYTES, problem.ciphertext.size)
        }
    }

    @Test
    fun `the State is 4 by 4 and holds the block's 16 bytes`() {
        assertEquals(4, Aes.STATE_ROWS)
        assertEquals(4, Aes.STATE_COLUMNS)
        assertEquals(Aes.BLOCK_BYTES, Aes.STATE_ROWS * Aes.STATE_COLUMNS)

        // Column-major: byte i lives at row i % 4, column i / 4.
        (0 until Aes.BLOCK_BYTES).forEach { index ->
            assertEquals(index % 4, Aes.rowOf(index))
            assertEquals(index / 4, Aes.columnOf(index))
            assertEquals(index, Aes.indexOf(Aes.rowOf(index), Aes.columnOf(index)))
        }
    }

    @Test
    fun `AES-128 runs 10 rounds, AES-192 twelve and AES-256 fourteen`() {
        assertEquals(10, AesVariant.AES_128.rounds)
        assertEquals(12, AesVariant.AES_192.rounds)
        assertEquals(14, AesVariant.AES_256.rounds)
    }

    @Test
    fun `the key size is the number in the name, and the block never changes`() {
        assertEquals(128, AesVariant.AES_128.keyBits)
        assertEquals(192, AesVariant.AES_192.keyBits)
        assertEquals(256, AesVariant.AES_256.keyBits)
        assertEquals(16, AesVariant.AES_128.keyBytes)
        assertEquals(24, AesVariant.AES_192.keyBytes)
        assertEquals(32, AesVariant.AES_256.keyBytes)
    }

    @Test
    fun `there is one more round key than there are rounds`() {
        AesVariant.entries.forEach { variant ->
            val keys = Aes.expandKey(IntArray(variant.keyBytes) { it }, variant)
            assertEquals(variant.rounds + 1, keys.size)
            assertEquals(variant.roundKeys, keys.size)
            keys.forEach { assertEquals(Aes.BLOCK_BYTES, it.size) }
        }
    }

    /** Round key 0 is the key itself — which is why the initial AddRoundKey is free. */
    @Test
    fun `round key 0 is the original key`() {
        val key = Aes.bytesOf("000102030405060708090a0b0c0d0e0f")
        assertEquals(Aes.hex(key), Aes.hex(Aes.expandKey(key, AesVariant.AES_128).first()))
    }

    // ── 3. The shape of a round ──────────────────────────────────────────────

    @Test
    fun `a normal round is SubBytes ShiftRows MixColumns AddRoundKey, in that order`() {
        assertEquals(
            listOf(
                AesTransformation.SUB_BYTES,
                AesTransformation.SHIFT_ROWS,
                AesTransformation.MIX_COLUMNS,
                AesTransformation.ADD_ROUND_KEY,
            ),
            AesTransformation.NORMAL_ROUND,
        )
    }

    @Test
    fun `the final round omits MixColumns and nothing else`() {
        assertEquals(
            listOf(
                AesTransformation.SUB_BYTES,
                AesTransformation.SHIFT_ROWS,
                AesTransformation.ADD_ROUND_KEY,
            ),
            AesTransformation.FINAL_ROUND,
        )
        assertEquals(AesTransformation.MIX_COLUMNS, AesTransformation.SKIPPED_IN_FINAL_ROUND)
        assertEquals(
            AesTransformation.NORMAL_ROUND - AesTransformation.MIX_COLUMNS,
            AesTransformation.FINAL_ROUND,
        )
    }

    @Test
    fun `every run has an initial AddRoundKey before round 1`() {
        AesVariant.entries.forEach { variant ->
            val steps = Aes.encryptBlock(
                block = IntArray(Aes.BLOCK_BYTES) { it },
                key = IntArray(variant.keyBytes) { it },
                variant = variant,
            )
            val initial = steps.indexOfFirst { it.kind == AesStepKind.INITIAL_ADD_ROUND_KEY }
            val firstRound = steps.indexOfFirst { it.kind == AesStepKind.ROUND_TRANSFORM }
            assertTrue("${variant.label} has an initial AddRoundKey", initial >= 0)
            assertTrue("${variant.label}: it comes before round 1", initial < firstRound)
            assertEquals(0, steps[initial].roundKey)
        }
    }

    @Test
    fun `every round runs its transformations in order, and only the last skips one`() {
        AesVariant.entries.forEach { variant ->
            val steps = Aes.encryptBlock(
                block = IntArray(Aes.BLOCK_BYTES) { it * 7 },
                key = IntArray(variant.keyBytes) { it },
                variant = variant,
            )
            (1..variant.rounds).forEach { round ->
                val applied = steps
                    .filter { it.kind == AesStepKind.ROUND_TRANSFORM && it.round == round }
                    .map { it.transformation }
                val expected = if (round == variant.rounds) {
                    AesTransformation.FINAL_ROUND
                } else {
                    AesTransformation.NORMAL_ROUND
                }
                assertEquals("${variant.label} round $round", expected, applied)
            }

            val mixed = steps.count {
                it.transformation == AesTransformation.MIX_COLUMNS
            }
            assertEquals(
                "${variant.label}: MixColumns runs in every round but the last",
                variant.rounds - 1,
                mixed,
            )
        }
    }

    // ── 4. What each transformation does, as the picture claims ──────────────

    @Test
    fun `SubBytes changes values and moves nothing`() {
        val state = IntArray(Aes.BLOCK_BYTES) { it * 11 }
        val after = Aes.subBytes(state)
        state.indices.forEach { assertEquals(Aes.SBOX[state[it]], after[it]) }
    }

    @Test
    fun `ShiftRows moves positions, keeps values, and leaves row 0 alone`() {
        val state = IntArray(Aes.BLOCK_BYTES) { it }
        val after = Aes.shiftRows(state)

        // Same multiset of bytes: nothing was substituted.
        assertEquals(state.sorted(), after.sorted())

        // Row 0 untouched; row r rotated left by r.
        for (row in 0 until Aes.STATE_ROWS) {
            for (column in 0 until Aes.STATE_COLUMNS) {
                assertEquals(
                    "row $row column $column",
                    state[Aes.indexOf(row, (column + row) % Aes.STATE_COLUMNS)],
                    after[Aes.indexOf(row, column)],
                )
            }
        }
        (0 until Aes.STATE_COLUMNS).forEach { column ->
            val at = Aes.indexOf(0, column)
            assertEquals(state[at], after[at])
        }
    }

    /**
     * The claim the copy makes about MixColumns: a change to one byte reaches every
     * byte of its column, and no byte outside it.
     */
    @Test
    fun `MixColumns spreads one byte across its column and no further`() {
        val state = IntArray(Aes.BLOCK_BYTES) { it }
        val nudged = state.copyOf().also { it[5] = it[5] xor 0x01 }

        val changed = Aes.differingPositions(Aes.mixColumns(state), Aes.mixColumns(nudged))
        assertEquals("the whole of column 1", setOf(4, 5, 6, 7), changed)
    }

    @Test
    fun `AddRoundKey is its own inverse with the same key`() {
        val state = IntArray(Aes.BLOCK_BYTES) { it * 3 }
        val key = IntArray(Aes.BLOCK_BYTES) { 0xA5 }
        assertTrue(
            Aes.addRoundKey(Aes.addRoundKey(state, key), key).contentEquals(state),
        )
    }

    /**
     * Every step of both lessons changes something.
     *
     * A step that changed nothing would be a beat where the picture does not move,
     * which ADR-020 makes a bug rather than a beat — and here it would also be a
     * transformation the learner could reasonably conclude does nothing.
     */
    @Test
    fun `every transformation in both lessons visibly changes the State`() {
        listOf(AesDatasets.watch, AesDatasets.tryIt).forEach { dataset ->
            val problem = requireNotNull(dataset.aes)
            problem.steps
                .filter { it.kind == AesStepKind.ROUND_TRANSFORM }
                .forEach { step ->
                    assertTrue(
                        "${dataset.label} round ${step.round} " +
                            "${step.transformation?.label} changed nothing",
                        step.changed.isNotEmpty(),
                    )
                }
        }
    }

    // ── 5. The state machine ─────────────────────────────────────────────────

    private fun stateFor(dataset: Dataset = AesDatasets.watch): AesState =
        AesEncryptionAlgorithm().initial(dataset)

    @Test
    fun `the whole cipher runs before any judgement is asked`() {
        val algorithm = AesEncryptionAlgorithm()
        var state = stateFor()
        var asked = 0

        while (true) {
            when (val probe = algorithm.probe(state)) {
                is Probe.Terminal -> break
                is Probe.Mechanical -> {
                    assertEquals("nothing is asked before the run ends", 0, asked)
                    state = algorithm.apply(state, probe.action).next
                }

                is Probe.Decide -> {
                    assertTrue("the run finished first", state.encrypted)
                    asked++
                    state = algorithm.apply(state, probe.decision.correct).next
                }
            }
        }

        assertEquals(AesDatasets.EXERCISES.size, asked)
        assertTrue(state.finished)
    }

    @Test
    fun `a full run terminates and reports completion`() {
        val trace = AlgorithmRunner(AesEncryptionAlgorithm(), AesDatasets.watch)
            .runToCompletion()
        val last = trace.frames.last()
        assertTrue(last.state.finished)
        assertTrue(
            last.events.any { it is VizEvent.Terminal } ||
                trace.frames.any { frame ->
                    frame.events.any {
                        it is VizEvent.Terminal && it.outcome == Outcome.Completed(true)
                    }
                },
        )
    }

    @Test
    fun `no State matrix is stored on the state`() {
        // Every byte is recomputed from the problem, so there is nothing to drift.
        val state = stateFor()
        assertEquals(state.problem.steps, state.steps)
    }

    @Test
    fun `an over-long action sequence is a no-op, never an exception`() {
        val algorithm = AesEncryptionAlgorithm()
        var state = stateFor()
        repeat(state.steps.size) { state = algorithm.apply(state, AesAction.Advance).next }

        val settled = state
        repeat(5) { state = algorithm.apply(state, AesAction.Advance).next }
        assertEquals(settled, state)

        // And an answer outside the options on offer.
        assertEquals(settled, algorithm.apply(settled, AesAction.Answer(99)).next)
        assertEquals(settled, algorithm.apply(settled, AesAction.Answer(-1)).next)
    }

    @Test
    fun `rewind is exact`() {
        val runner = AlgorithmRunner(AesEncryptionAlgorithm(), AesDatasets.watch)
        val trace = runner.runToCompletion()
        assertTrue(trace.frames.size > 2)

        val runner2 = AlgorithmRunner(AesEncryptionAlgorithm(), AesDatasets.watch)
        val before = runner2.current.state
        runner2.apply(AesAction.Advance)
        assertEquals(before, runner2.rewind().state)
    }

    // ── 6. The judgements ────────────────────────────────────────────────────

    private fun decisionsOf(dataset: Dataset = AesDatasets.tryIt) = buildList {
        val algorithm = AesEncryptionAlgorithm()
        var state = algorithm.initial(dataset)
        while (true) {
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

    @Test
    fun `TRY asks the six exercises, and the round order four times`() {
        val questions = AesDatasets.EXERCISES
        assertEquals(AesQuestion.BlockSize, questions[0])
        assertEquals(AesQuestion.StateSize, questions[1])
        assertEquals(
            AesTransformation.NORMAL_ROUND.indices.map { AesQuestion.NextTransformation(it) },
            questions.slice(2..5),
        )
        assertEquals(AesQuestion.SkippedInFinalRound, questions[6])
        assertEquals(
            AesVariant.entries.map { AesQuestion.RoundCount(it) },
            questions.slice(7..9),
        )
        assertEquals(AesQuestion.KeyExpansion, questions[10])
        assertEquals(11, questions.size)
    }

    @Test
    fun `WATCH and TRY ask the same exercises in the same order`() {
        assertEquals(
            requireNotNull(AesDatasets.watch.aes).questions,
            requireNotNull(AesDatasets.tryIt.aes).questions,
        )
    }

    @Test
    fun `every decision offers its correct answer, and none is auto-answered`() {
        decisionsOf().forEach { (_, decision) ->
            assertTrue(
                "the correct action is on offer",
                decision.options.any { it.action == decision.correct },
            )
            assertFalse("a judgement is never the app's", decision.autoInTry)
            assertTrue("three rungs of guidance", decision.guidance.size >= 3)
        }
    }

    @Test
    fun `the block size question offers 128 and the answer is 128`() {
        val (_, decision) = decisionsOf().first()
        assertEquals(4, decision.options.size)
        assertEquals(DecisionKind.OPTIONS, decision.kind)
        // Option index 1 is 128 — the second of 64 / 128 / 192 / 256.
        assertEquals(AesAction.Answer(1), decision.correct)
    }

    @Test
    fun `the State size question's answer is 16`() {
        val (_, decision) = decisionsOf()[1]
        assertEquals(4, decision.options.size)
        // Option index 2 is 16 — the third of 4 / 8 / 16 / 32.
        assertEquals(AesAction.Answer(2), decision.correct)
    }

    /**
     * The round-order exercise is answered by **tapping the round**, not by picking
     * a word off a list — so each option carries the slot it selects.
     */
    @Test
    fun `the round order is rebuilt by tapping, in the right order`() {
        val rounds = decisionsOf().slice(2..5)
        rounds.forEachIndexed { position, (_, decision) ->
            assertEquals(DecisionKind.CELL, decision.kind)
            assertEquals(
                AesTransformation.NORMAL_ROUND.indices.toList(),
                decision.options.map { it.slot },
            )
            assertEquals(
                "step ${position + 1} of a normal round",
                AesAction.Answer(AesTransformation.NORMAL_ROUND[position].ordinal),
                decision.correct,
            )
        }
    }

    @Test
    fun `the skipped-transformation exercise answers MixColumns, and is a tap`() {
        val (_, decision) = decisionsOf()[6]
        assertEquals(DecisionKind.CELL, decision.kind)
        assertEquals(
            AesAction.Answer(AesTransformation.MIX_COLUMNS.ordinal),
            decision.correct,
        )
    }

    @Test
    fun `each variant's round count is asked, and the three counts are the options`() {
        val counts = AesVariant.entries.map { it.rounds }
        decisionsOf().slice(7..9).forEachIndexed { index, (_, decision) ->
            val variant = AesVariant.entries[index]
            assertEquals(3, decision.options.size)
            assertEquals(
                "${variant.label} runs ${variant.rounds}",
                AesAction.Answer(counts.indexOf(variant.rounds)),
                decision.correct,
            )
        }
    }

    @Test
    fun `the key expansion exercise answers Key Expansion`() {
        val (_, decision) = decisionsOf()[10]
        assertEquals(2, decision.options.size)
        assertEquals(AesAction.Answer(0), decision.correct)
    }

    /**
     * A learner who noticed the answer was always in the same seat could finish
     * without reading anything — the failure PRODUCT_SPEC.md §5 exists to prevent.
     */
    @Test
    fun `the correct answer is not always in the same seat`() {
        val seats = decisionsOf().map { (_, decision) ->
            decision.options.indexOfFirst { it.action == decision.correct }
        }
        assertTrue("the correct seat moves: $seats", seats.distinct().size > 1)
    }

    @Test
    fun `every wrong option is explained by name`() {
        decisionsOf().forEach { (_, decision) ->
            decision.options
                .filter { it.action != decision.correct }
                .forEach { option ->
                    assertNotNull(
                        "no whyWrong for ${option.action}",
                        decision.whyWrong[option.action],
                    )
                }
        }
    }

    // ── 7. A wrong answer is a learning event, never a state transition ──────

    @Test
    fun `five wrong answers leave the state byte-for-byte identical`() {
        val algorithm = AesEncryptionAlgorithm()
        var state = stateFor(AesDatasets.tryIt)
        while (algorithm.probe(state) is Probe.Mechanical) {
            state = algorithm.apply(state, (algorithm.probe(state) as Probe.Mechanical).action).next
        }

        val decision = (algorithm.probe(state) as Probe.Decide).decision
        val wrong = decision.options.first { it.action != decision.correct }.action
        val before = state

        repeat(5) { attempt ->
            val verdict = DecisionValidation.validate(decision, wrong, attempt)
            assertTrue("a wrong answer is a Retry", verdict is Validation.Retry)
            // A Retry carries no action, so there is nothing the caller could apply.
            assertEquals("the state never moved", before, state)
        }
    }

    @Test
    fun `guidance escalates and then holds`() {
        val algorithm = AesEncryptionAlgorithm()
        var state = stateFor(AesDatasets.tryIt)
        while (algorithm.probe(state) is Probe.Mechanical) {
            state = algorithm.apply(state, (algorithm.probe(state) as Probe.Mechanical).action).next
        }
        val decision = (algorithm.probe(state) as Probe.Decide).decision
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
    fun `retry works - a run driven by rejected guesses still completes correctly`() {
        val algorithm = AesEncryptionAlgorithm()
        var state = stateFor(AesDatasets.tryIt)
        var wrongAttempts = 0

        while (true) {
            when (val probe = algorithm.probe(state)) {
                is Probe.Terminal -> break
                is Probe.Mechanical -> state = algorithm.apply(state, probe.action).next
                is Probe.Decide -> {
                    val decision = probe.decision
                    val wrong = decision.options.firstOrNull { it.action != decision.correct }
                    if (wrong != null) {
                        // Every wrong answer is refused, and refusing it applies nothing.
                        val verdict = DecisionValidation.validate(decision, wrong.action, 0)
                        assertTrue(verdict is Validation.Retry)
                        wrongAttempts++
                    }
                    val accepted = DecisionValidation.validate(decision, decision.correct, 3)
                    assertTrue(accepted is Validation.Accept)
                    state = algorithm.apply(state, (accepted as Validation.Accept).action).next
                }
            }
        }

        assertTrue("every decision was guessed at first", wrongAttempts >= 11)
        assertTrue(state.finished)
    }

    @Test
    fun `a wrong answer applied directly still leaves a legal, terminating run`() {
        val algorithm = AesEncryptionAlgorithm()
        var state = stateFor(AesDatasets.tryIt)
        var guard = 0

        while (guard++ < 2_000) {
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
        val algorithm = AesEncryptionAlgorithm()
        repeat(40) { seed ->
            var state = stateFor(if (seed % 2 == 0) AesDatasets.watch else AesDatasets.tryIt)
            var guard = 0
            val random = kotlin.random.Random(seed.toLong())
            while (guard++ < 4_000 && algorithm.probe(state) !is Probe.Terminal) {
                val action = if (random.nextBoolean()) {
                    AesAction.Advance
                } else {
                    AesAction.Answer(random.nextInt(-2, 6))
                }
                state = algorithm.apply(state, action).next
            }
            assertTrue("seed $seed terminated", algorithm.probe(state) is Probe.Terminal)
        }
    }

    // ── 8. The picture ───────────────────────────────────────────────────────

    private fun scenesOf(dataset: Dataset = AesDatasets.watch): List<BlockCipherScene> {
        val projector = AesProjector()
        return AlgorithmRunner(AesEncryptionAlgorithm(), dataset)
            .runToCompletion()
            .frames
            .map { projector.project(it.state, it.events) }
    }

    @Test
    fun `the State is always sixteen cells, laid out 4 by 4`() {
        scenesOf().forEach { scene ->
            assertEquals(Aes.BLOCK_BYTES, scene.state.size)
            assertEquals(
                (0 until Aes.BLOCK_BYTES).toList(),
                scene.state.map { it.slot },
            )
            // Bytes read as hex, always two characters, so the columns line up.
            scene.state.forEach { assertEquals(2, it.label?.length) }
        }
    }

    @Test
    fun `the pipeline is always five stages`() {
        scenesOf().forEach { assertEquals(5, it.pipeline.size) }
    }

    @Test
    fun `the marked bytes are exactly the ones the transformation changed`() {
        val projector = AesProjector()
        AlgorithmRunner(AesEncryptionAlgorithm(), AesDatasets.watch)
            .runToCompletion()
            .frames
            .forEach { frame ->
                val step = frame.state.currentStep ?: return@forEach
                if (step.kind != AesStepKind.ROUND_TRANSFORM) return@forEach
                // Only frames the run itself produced — a judgement frame carries a
                // different kind of Examine and must not mark the State.
                if (frame.events.none { it is VizEvent.Insert || it is VizEvent.Examine }) {
                    return@forEach
                }
                val scene = projector.project(frame.state, frame.events)
                if (frame.state.encrypted && frame.state.question != null) return@forEach
                assertEquals(
                    "round ${step.round} ${step.transformation?.label}",
                    step.changed,
                    scene.changed,
                )
            }
    }

    @Test
    fun `ShiftRows marks twelve bytes and leaves row 0 unmarked`() {
        val projector = AesProjector()
        val frame = AlgorithmRunner(AesEncryptionAlgorithm(), AesDatasets.watch)
            .runToCompletion()
            .frames
            .first {
                it.state.currentStep?.transformation == AesTransformation.SHIFT_ROWS
            }
        val scene = projector.project(frame.state, frame.events)

        assertTrue("row 0 never moves", scene.changed.none { Aes.rowOf(it) == 0 })
        assertEquals("rows 1, 2 and 3 all move", 12, scene.changed.size)
    }

    /** The omission has to be *visible*, so it keeps its place and is struck out. */
    @Test
    fun `the final round draws MixColumns as skipped, and normal rounds do not`() {
        val projector = AesProjector()
        val frames = AlgorithmRunner(AesEncryptionAlgorithm(), AesDatasets.watch)
            .runToCompletion()
            .frames

        val variant = AesVariant.AES_128
        val finalRound = frames.first {
            it.state.currentStep?.round == variant.rounds &&
                it.state.currentStep?.kind == AesStepKind.ROUND_TRANSFORM
        }
        val finalScene = projector.project(finalRound.state, finalRound.events)
        assertEquals(4, finalScene.roundSteps.size)
        assertEquals(
            RoundStepState.SKIPPED,
            finalScene.roundSteps[AesTransformation.MIX_COLUMNS.ordinal].state,
        )

        val firstRound = frames.first { it.state.currentStep?.round == 1 }
        val firstScene = projector.project(firstRound.state, firstRound.events)
        assertTrue(
            "a normal round skips nothing",
            firstScene.roundSteps.none { it.state == RoundStepState.SKIPPED },
        )
    }

    @Test
    fun `no round key is drawn before the key has been expanded`() {
        val projector = AesProjector()
        AlgorithmRunner(AesEncryptionAlgorithm(), AesDatasets.watch)
            .runToCompletion()
            .frames
            .forEach { frame ->
                val kind = frame.state.currentStep?.kind
                val scene = projector.project(frame.state, frame.events)
                if (kind == null ||
                    kind == AesStepKind.PLAINTEXT ||
                    kind == AesStepKind.BLOCK ||
                    kind == AesStepKind.STATE
                ) {
                    assertNull("nothing is expanded yet", scene.keySchedule)
                }
            }
    }

    /** An answer already on screen is not a question (ADR-030). */
    @Test
    fun `a variant's round count is withheld until it has been settled`() {
        val algorithm = AesEncryptionAlgorithm()
        val projector = AesProjector()
        var state = algorithm.initial(AesDatasets.tryIt)

        while (true) {
            val probe = algorithm.probe(state)
            if (probe is Probe.Terminal) break
            if (probe is Probe.Mechanical) {
                state = algorithm.apply(state, probe.action).next
                continue
            }
            val question = state.question
            val scene = projector.project(state, emptyList())
            if (question is AesQuestion.RoundCount) {
                val row = scene.variants.first { it.label == question.variant.label }
                assertNull(
                    "${question.variant.label}'s round count is what is being asked",
                    row.rounds,
                )
                // And the block size is printed on every row, identically.
                assertTrue(scene.variants.all { it.blockBits == Aes.BLOCK_BITS })
            }
            state = algorithm.apply(state, (probe as Probe.Decide).decision.correct).next
        }
    }

    @Test
    fun `the round question offers its steps as taps, and shows what is already rebuilt`() {
        val algorithm = AesEncryptionAlgorithm()
        val projector = AesProjector()
        var state = algorithm.initial(AesDatasets.tryIt)
        var seen = 0

        while (true) {
            val probe = algorithm.probe(state)
            if (probe is Probe.Terminal) break
            if (probe is Probe.Mechanical) {
                state = algorithm.apply(state, probe.action).next
                continue
            }
            val question = state.question
            if (question is AesQuestion.NextTransformation) {
                val scene = projector.project(state, emptyList())
                assertEquals(4, scene.roundSteps.size)
                assertEquals(
                    "already rebuilt",
                    question.position,
                    scene.roundSteps.count { it.state == RoundStepState.DONE },
                )
                assertEquals(
                    "the rest are the answer",
                    4 - question.position,
                    scene.roundSteps.count { it.state == RoundStepState.SELECTABLE },
                )
                seen++
            }
            state = algorithm.apply(state, (probe as Probe.Decide).decision.correct).next
        }
        assertEquals(4, seen)
    }

    @Test
    fun `both claims are on screen whenever the key expansion question is asked`() {
        val algorithm = AesEncryptionAlgorithm()
        val projector = AesProjector()
        var state = algorithm.initial(AesDatasets.tryIt)

        while (true) {
            val probe = algorithm.probe(state)
            if (probe is Probe.Terminal) break
            if (probe is Probe.Mechanical) {
                state = algorithm.apply(state, probe.action).next
                continue
            }
            if (state.question == AesQuestion.KeyExpansion) {
                val scene = projector.project(state, emptyList())
                assertEquals(2, scene.claims.size)
                // The scene does not carry which claim is true, so the renderer
                // could not style the right answer differently even by accident.
                assertTrue(scene.claims.all { it.text.isNotBlank() })
                assertTrue(scene.claims.all { it.choice.isNotBlank() })
            }
            state = algorithm.apply(state, (probe as Probe.Decide).decision.correct).next
        }
    }

    @Test
    fun `the ciphertext is drawn as final, and never before it exists`() {
        val projector = AesProjector()
        AlgorithmRunner(AesEncryptionAlgorithm(), AesDatasets.watch)
            .runToCompletion()
            .frames
            .forEach { frame ->
                val kind = frame.state.currentStep?.kind ?: return@forEach
                val scene = projector.project(frame.state, frame.events)
                val finalised = scene.state.count { it.state == CellState.FINALIZED }
                if (kind == AesStepKind.CIPHERTEXT ||
                    kind == AesStepKind.VARIANTS ||
                    kind == AesStepKind.DECRYPTION ||
                    kind == AesStepKind.READY
                ) {
                    assertEquals(Aes.BLOCK_BYTES, finalised)
                } else {
                    assertEquals("nothing is final yet", 0, finalised)
                }
            }
    }

    // ── 9. The walkthrough ───────────────────────────────────────────────────

    private fun watchScript() = AlgorithmCatalog.aes().watchScript()

    @Test
    fun `WATCH opens on the cipher and closes on the idea`() {
        val steps = watchScript().steps
        assertEquals(WatchStepKind.SETUP, steps.first().kind)
        assertEquals(WatchStepKind.SUMMARY, steps.last().kind)
        assertEquals(WatchStepKind.INSIGHT, steps[steps.size - 2].kind)
    }

    @Test
    fun `WATCH is long enough to teach and short enough to finish`() {
        val size = watchScript().size
        assertTrue("$size beats", size in 14..24)
    }

    /**
     * ADR-020: a step where nothing changed is a bug, not a beat.
     *
     * Scene *and* copy, because two beats may legitimately share a picture while
     * saying different things — but never both.
     */
    @Test
    fun `no two adjacent WATCH steps are identical`() {
        val steps = watchScript().steps
        steps.zipWithNext().forEach { (a, b) ->
            assertFalse(
                "steps ${a.index} and ${b.index} are the same beat",
                a.scene == b.scene && a.headline == b.headline && a.support == b.support,
            )
        }
    }

    @Test
    fun `round 1 is narrated in full and the middle rounds are collapsed`() {
        val steps = watchScript().steps
        // Four beats for round 1's four transformations.
        val roundOne = steps.count { it.headline.args.firstOrNull() == 1 }
        assertEquals("round 1, transformation by transformation", 4, roundOne)

        // Exactly one beat stands for everything between round 1 and the last.
        val collapsed = steps.count { it.kind == WatchStepKind.PASS_COMPLETE }
        assertEquals("the middle is one beat", 1, collapsed)
    }

    @Test
    fun `the final round gets three beats and names the omission`() {
        val steps = watchScript().steps
        // By narration id, not by "a beat mentioning round 10" — the ciphertext
        // beat names the round count too, and counting it here would have made this
        // assertion pass for the wrong reason.
        val finalBeats = steps.filter {
            it.headline.id ==
                com.algorithms.algoking.engine.narration.NarrationId.AES_WATCH_FINAL_TRANSFORM
        }
        assertEquals("SubBytes, ShiftRows, AddRoundKey", 3, finalBeats.size)

        assertTrue(
            "the omission is said out loud",
            steps.any { it.support?.id == com.algorithms.algoking.engine.narration.NarrationId.AES_WATCH_FINAL_ROUND_OMITS },
        )
    }

    @Test
    fun `WATCH teaches the variants and the inverse, and narrates no judgement`() {
        val ids = watchScript().steps.map { it.headline.id }
        assertTrue(
            com.algorithms.algoking.engine.narration.NarrationId.AES_WATCH_VARIANTS in ids,
        )
        assertTrue(
            com.algorithms.algoking.engine.narration.NarrationId.AES_WATCH_DECRYPTION in ids,
        )
        // No exercise is answered for the learner before TRY asks it.
        assertTrue(
            ids.none { it.name.startsWith("AES_ASK_") || it.name.startsWith("AES_CORRECT_") },
        )
    }

    /**
     * The bug ADR-047 records for XOR's last frame and ADR-048 for SHA-256's whole
     * run, guarded here.
     *
     * A frame is drawn from the state *after* its transition, so the frame that
     * applies the last step of the run is also the frame the first question is
     * pending on. Without the inert hand-over step the closing beat about
     * decryption was drawn with TRY's first question over it — found, as every one
     * of these has been, by dumping the walkthrough and reading it.
     */
    @Test
    fun `the run ends on an inert hand-over, so no closing beat shows a question`() {
        assertEquals(
            AesStepKind.READY,
            requireNotNull(AesDatasets.watch.aes).steps.last().kind,
        )

        val frames = AlgorithmRunner(AesEncryptionAlgorithm(), AesDatasets.watch)
            .runToCompletion()
            .frames

        frames.filter {
            val kind = it.state.currentStep?.kind
            kind == AesStepKind.VARIANTS || kind == AesStepKind.DECRYPTION
        }.forEach {
            assertFalse(
                "a closing concept beat is drawn while a question is live",
                it.state.encrypted,
            )
        }

        // And the hand-over itself draws nothing that could answer a question.
        val handOver = frames.first { it.state.currentStep?.kind == AesStepKind.READY }
        val scene = AesProjector().project(handOver.state, handOver.events)
        assertTrue("no variant table over the first question", scene.variants.isEmpty())
        assertTrue("no round strip either", scene.roundSteps.isEmpty())
    }

    /** Both closing concepts get a beat, and each is drawn with its own picture. */
    @Test
    fun `the variants beat shows the table and the decryption beat turns the pipeline round`() {
        val projector = AesProjector()
        val frames = AlgorithmRunner(AesEncryptionAlgorithm(), AesDatasets.watch)
            .runToCompletion()
            .frames

        val variants = frames.first { it.state.currentStep?.kind == AesStepKind.VARIANTS }
        val variantScene = projector.project(variants.state, variants.events)
        assertEquals(3, variantScene.variants.size)
        assertTrue(
            "every round count is taught here",
            variantScene.variants.all { it.rounds != null },
        )
        assertEquals("Plaintext", variantScene.pipeline.first().label)

        val decryption = frames.first { it.state.currentStep?.kind == AesStepKind.DECRYPTION }
        val decryptScene = projector.project(decryption.state, decryption.events)
        assertEquals("Ciphertext", decryptScene.pipeline.first().label)
        assertEquals("Plaintext", decryptScene.pipeline.last().label)
        assertTrue(
            "the inverses are named",
            decryptScene.roundSteps.any { it.label.startsWith("Inv") },
        )
    }

    @Test
    fun `the recap ends on the two caveats`() {
        val bullets = watchScript().steps.last().bullets.map { it.id }
        assertEquals(
            listOf(
                com.algorithms.algoking.engine.narration.NarrationId.AES_IDEA_6,
                com.algorithms.algoking.engine.narration.NarrationId.AES_IDEA_7,
            ),
            bullets.takeLast(2),
        )
    }

    // ── 10. The lesson in the library ────────────────────────────────────────

    @Test
    fun `AES is registered with its own datasets`() {
        val pack = AlgorithmCatalog.byId(AlgorithmId.AES)
        assertEquals(AlgorithmId.AES, pack.id)
        assertEquals("AES", pack.displayName)
        assertEquals(AesDatasets.watch, pack.watchDataset)
        assertEquals(AesDatasets.tryIt, pack.tryDataset)
        assertTrue("WATCH and TRY are different blocks", pack.watchDataset != pack.tryDataset)
    }

    @Test
    fun `WATCH and TRY share no input byte and no key byte`() {
        val watch = requireNotNull(AesDatasets.watch.aes)
        val tryIt = requireNotNull(AesDatasets.tryIt.aes)
        assertFalse(watch.block.contentEquals(tryIt.block))
        assertFalse(watch.key.contentEquals(tryIt.key))
        assertFalse(
            "and the ciphertexts differ too",
            watch.ciphertext.contentEquals(tryIt.ciphertext),
        )
    }

    @Test
    fun `AES has no challenge, and the catalogue says so`() {
        assertNull(ChallengeCatalog.byId(AlgorithmId.AES))
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
        assertEquals(100, progress.complete(Stage.TRY).percent)
    }

    // ── 11. What it cost everything else ─────────────────────────────────────

    @Test
    fun `every lesson in the library still builds its walkthrough`() {
        AlgorithmId.entries.forEach { id ->
            val script = AlgorithmCatalog.byId(id).watchScript()
            assertTrue("$id produced no walkthrough", script.size > 0)
        }
    }

    @Test
    fun `the three other Cryptography lessons are untouched`() {
        // Their ciphertexts and digests are what they were; AES added a dataset
        // field and nothing else to the shape they share.
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
    }

    // ── 12. Validation ───────────────────────────────────────────────────────

    @Test
    fun `a block of the wrong size is refused where it is authored`() {
        listOf(0, 1, 15, 17, 32).forEach { size ->
            runCatching {
                AesProblem(
                    variant = AesVariant.AES_128,
                    block = IntArray(size),
                    key = IntArray(16),
                    questions = listOf(AesQuestion.BlockSize),
                )
            }.onSuccess { error("a $size-byte block was accepted") }
        }
    }

    @Test
    fun `a key that does not match the variant is refused`() {
        runCatching {
            AesProblem(
                variant = AesVariant.AES_256,
                block = IntArray(16),
                key = IntArray(16),
                questions = listOf(AesQuestion.BlockSize),
            )
        }.onSuccess { error("a 16-byte key was accepted for AES-256") }
    }

    @Test
    fun `an empty or duplicated question list is refused`() {
        runCatching {
            AesProblem(AesVariant.AES_128, IntArray(16), IntArray(16), questions = emptyList())
        }.onSuccess { error("no questions was accepted") }

        runCatching {
            AesProblem(
                variant = AesVariant.AES_128,
                block = IntArray(16),
                key = IntArray(16),
                questions = listOf(AesQuestion.BlockSize, AesQuestion.BlockSize),
            )
        }.onSuccess { error("a repeated question was accepted") }
    }

    @Test
    fun `bad hex is refused, and a position outside a normal round is too`() {
        listOf("", "abc", "zz", "00 11 2").forEach { bad ->
            runCatching { Aes.bytesOf(bad) }.onSuccess { error("“$bad” was accepted") }
        }
        // And whitespace is tolerated, because an authored key is readable in groups.
        assertEquals(4, Aes.bytesOf("00 11 22 33").size)

        listOf(-1, 4, 9).forEach { position ->
            runCatching { AesQuestion.NextTransformation(position) }
                .onSuccess { error("position $position was accepted") }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * One block through the JDK's AES, for verification only.
     *
     * **This is the only place in the project ECB appears, and it is a test.** For a
     * single block with no padding it is simply "the raw block transform", which is
     * exactly what needs checking here. The lesson itself never suggests encrypting
     * a message this way — the recap names AES-GCM and says so.
     */
    private fun jdkEncryptBlock(block: IntArray, key: IntArray): String {
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        val secret = SecretKeySpec(ByteArray(key.size) { key[it].toByte() }, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, secret)
        val out = cipher.doFinal(ByteArray(block.size) { block[it].toByte() })
        return out.joinToString("") { Aes.hexByte(it.toInt()) }
    }

    /** The multiplicative inverse of [value] in GF(2⁸), by brute force. */
    private fun gfInverse(value: Int): Int {
        for (candidate in 1 until 256) {
            if (gfMultiply(value, candidate) == 1) return candidate
        }
        error("no inverse for $value")
    }

    /** Multiplication in GF(2⁸) modulo the AES polynomial. */
    private fun gfMultiply(a: Int, b: Int): Int {
        var left = a
        var right = b
        var result = 0
        repeat(8) {
            if (right and 1 != 0) result = result xor left
            val high = left and 0x80
            left = (left shl 1) and 0xFF
            if (high != 0) left = left xor 0x1B
            right = right shr 1
        }
        return result and 0xFF
    }
}
