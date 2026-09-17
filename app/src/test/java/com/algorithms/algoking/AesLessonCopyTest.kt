package com.algorithms.algoking

import com.algorithms.algoking.engine.catalog.AlgorithmCatalog
import com.algorithms.algoking.engine.core.AlgorithmRunner
import com.algorithms.algoking.engine.core.Probe
import com.algorithms.algoking.engine.dataset.AesDatasets
import com.algorithms.algoking.engine.narration.NarrationId
import com.algorithms.algoking.engine.narration.NarrationKey
import com.algorithms.algoking.feature.lesson.Narration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The AES lesson's copy, resolved.
 *
 * `Narration.resolve` is an exhaustive `when`, so the compiler already guarantees
 * every key has a sentence. What it cannot catch is a sentence with a **missing
 * argument**: `arg(3)` on a key emitted with two args resolves to an empty string
 * and leaves a gap or a doubled space in the middle of a line, which reads as a
 * typo and is invisible to every other test.
 *
 * So this walks the real lesson, resolves every key it actually emits, and reads
 * the result the way a learner would.
 */
class AesLessonCopyTest {

    /** Every narration key the AES lesson emits, from both stages. */
    private fun keysInPlay(): List<NarrationKey> = buildList {
        // WATCH: the walkthrough's headlines, supports and recap bullets.
        AlgorithmCatalog.aes().watchScript().steps.forEach { step ->
            add(step.headline)
            step.support?.let(::add)
            addAll(step.bullets)
        }

        // TRY: every prompt, option, rung and explanation the learner can reach.
        val pack = AlgorithmCatalog.aes()
        val runner = AlgorithmRunner(pack.algorithm, AesDatasets.tryIt)
        var guard = 0
        while (guard++ < 500) {
            when (val probe = runner.probe()) {
                is Probe.Terminal -> break
                is Probe.Mechanical -> runner.apply(probe.action)
                is Probe.Decide -> {
                    val decision = probe.decision
                    add(decision.prompt)
                    add(decision.hint)
                    add(decision.correctFeedback)
                    addAll(decision.options.map { it.label })
                    addAll(decision.guidance)
                    addAll(decision.whyWrong.values)
                    runner.apply(decision.correct)
                }
            }
        }
    }

    @Test
    fun `every line the lesson can show resolves to a real sentence`() {
        keysInPlay().forEach { key ->
            val text = Narration.resolve(key)
            assertTrue("${key.id} resolved to nothing", text.isNotBlank())

            // A missing positional argument resolves to "" and leaves its hole
            // behind — as a doubled space, a gap before punctuation, or a dangling
            // preposition at the end of the line.
            assertFalse("${key.id} has a gap: “$text”", "  " in text)
            assertFalse("${key.id} has a gap before punctuation: “$text”", " ." in text)
            assertFalse("${key.id} has a gap before a comma: “$text”", " ," in text)
            assertFalse("${key.id} ends mid-sentence: “$text”", text.trim().endsWith(" "))
            // An unsubstituted "$" would mean a template that never ran.
            assertFalse("${key.id} has a raw template: “$text”", "\${" in text)
        }
    }

    /**
     * The claims the lesson makes about AES that it must not make.
     *
     * These are the brief's three accuracy constraints, checked against the copy
     * rather than trusted to review: never "unbreakable", never a recommendation of
     * ECB, and never the suggestion that a block cipher is a whole security system.
     */
    @Test
    fun `the copy never overstates what AES is`() {
        val everything = keysInPlay().joinToString(" ") { Narration.resolve(it) }.lowercase()

        // The lesson never *describes* AES as unbreakable. It does name the myth in
        // order to deny it, which is better teaching than leaving a learner to meet
        // the word elsewhere and believe it — so the word is allowed exactly once,
        // inside the sentence that refuses it.
        if ("unbreakable" in everything) {
            assertTrue(
                "“unbreakable” appears without being denied",
                "is not \"unbreakable\"" in everything,
            )
            assertEquals(
                "“unbreakable” appears more than once, so not every use is the denial",
                1,
                Regex("unbreakable").findAll(everything).count(),
            )
        }
        assertFalse("the copy calls AES impossible to break", "impossible to break" in everything)
        assertFalse("the copy promises total security", "completely secure" in everything)
        // And the claim that replaces it is the one that is actually true.
        assertTrue(
            "the honest claim is missing",
            "no practical attack is known" in everything,
        )

        // ECB is named once, and only as the thing not to reach for.
        if ("ecb" in everything) {
            assertTrue(
                "ECB is mentioned without being warned against",
                "not ecb" in everything || "never ecb" in everything,
            )
        }

        // And the mode that should be reached for is named.
        assertTrue("AES-GCM is never named", "aes-gcm" in everything)
    }

    /** The numbers the lesson teaches, as the learner reads them. */
    @Test
    fun `the recap states the block size, the round shape and the variants`() {
        val recap = AlgorithmCatalog.aes()
            .watchScript()
            .steps
            .last()
            .bullets
            .joinToString(" ") { Narration.resolve(it) }

        fun states(what: String, text: String) =
            assertTrue("the recap never states $what:\n$recap", text in recap)

        states("the block size", "128-bit block")
        states("the block size in bytes", "16 bytes")
        states("the State's shape", "4 × 4 State")
        states("Key Expansion", "Key Expansion")
        states("a round's order", "SubBytes → ShiftRows → MixColumns → AddRoundKey")
        states("the final round's omission", "leaves MixColumns out")
        states("AES-128's rounds", "AES-128 runs 10 rounds")
        states("AES-192's rounds", "AES-192 twelve")
        states("AES-256's rounds", "AES-256 fourteen")
        states("that decryption is the inverse", "inverse steps in reverse")
    }

    /** A number on a button is the button's whole label. */
    @Test
    fun `the numeric options resolve to bare numbers`() {
        listOf(64, 128, 192, 256).forEach { value ->
            val text = Narration.resolve(
                NarrationKey(NarrationId.AES_OPTION_NUMBER, listOf(value)),
            )
            assertTrue("a bare number", text == value.toString())
        }
    }

    /**
     * Every button label is short enough to sit on one line.
     *
     * Four `DecisionButton`s share a row at 360dp — about 76dp each — and a label
     * that wraps is the wall Two Pointers hit with "Move RIGHT" (ADR-032) and AVL
     * with its four case names (ADR-037). The transformation names are deliberately
     * *not* button labels here: they are tapped on the round strip instead.
     */
    @Test
    fun `no option label is long enough to wrap a decision button`() {
        val pack = AlgorithmCatalog.aes()
        val runner = AlgorithmRunner(pack.algorithm, AesDatasets.tryIt)
        var guard = 0
        while (guard++ < 500) {
            when (val probe = runner.probe()) {
                is Probe.Terminal -> break
                is Probe.Mechanical -> runner.apply(probe.action)
                is Probe.Decide -> {
                    val decision = probe.decision
                    // Only the options that actually become buttons. A CELL
                    // decision's labels are drawn on the round strip, which wraps.
                    if (decision.options.none { it.slot != null }) {
                        decision.options.forEach { option ->
                            val label = Narration.resolve(option.label)
                            assertTrue(
                                "“$label” is too long for a shared button row",
                                label.length <= MAX_BUTTON_LABEL,
                            )
                        }
                    }
                    runner.apply(decision.correct)
                }
            }
        }
    }

    private companion object {
        /**
         * Characters a `labelLarge` button label may hold when four share a row.
         *
         * Nine is what "EXPANSION" needs, and it is the longest label in this
         * lesson — every other one is a number.
         */
        const val MAX_BUTTON_LABEL = 9
    }
}
