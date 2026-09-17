package com.algorithms.algoking

import com.algorithms.algoking.engine.catalog.AlgorithmCatalog
import com.algorithms.algoking.engine.core.AlgorithmRunner
import com.algorithms.algoking.engine.core.Probe
import com.algorithms.algoking.engine.dataset.RsaDatasets
import com.algorithms.algoking.engine.decision.DecisionKind
import com.algorithms.algoking.engine.narration.NarrationId
import com.algorithms.algoking.engine.narration.NarrationKey
import com.algorithms.algoking.feature.lesson.Narration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The RSA lesson's copy, resolved.
 *
 * `Narration.resolve` is an exhaustive `when`, so the compiler already guarantees
 * every key has a sentence. What it cannot catch is a **missing argument**:
 * `arg(3)` on a key emitted with two args resolves to an empty string and leaves a
 * gap in the middle of a line, which reads as a typo and is invisible to every other
 * test. It caught exactly that in the AES lesson.
 *
 * It also checks the three claims this lesson must not make — the ones §9 of the
 * brief is about — against the resolved text rather than trusting them to review.
 */
class RsaLessonCopyTest {

    /** Every narration key the RSA lesson emits, from both stages. */
    private fun keysInPlay(): List<NarrationKey> = buildList {
        // WATCH: headlines, supports and recap bullets.
        AlgorithmCatalog.rsa().watchScript().steps.forEach { step ->
            add(step.headline)
            step.support?.let(::add)
            addAll(step.bullets)
        }

        // TRY: every prompt, option, rung and explanation the learner can reach.
        val pack = AlgorithmCatalog.rsa()
        val runner = AlgorithmRunner(pack.algorithm, RsaDatasets.tryIt)
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
            // behind — as a doubled space, or a gap before punctuation.
            assertFalse("${key.id} has a gap: “$text”", "  " in text)
            assertFalse("${key.id} has a gap before punctuation: “$text”", " ." in text)
            assertFalse("${key.id} has a gap before a comma: “$text”", " ," in text)
            assertFalse("${key.id} ends mid-sentence: “$text”", text.trim().endsWith(" "))
            assertFalse("${key.id} has a raw template: “$text”", "\${" in text)
            // An empty pair or bracket means an argument never arrived.
            assertFalse("${key.id} has an empty bracket: “$text”", "()" in text)
            assertFalse("${key.id} has a half-empty pair: “$text”", "(, " in text)
        }
    }

    /**
     * The three claims this lesson must not make — §9 of the brief.
     *
     * Checked against the resolved copy, because "the lesson is careful about this"
     * is the kind of thing that stays true only while someone is watching.
     */
    @Test
    fun `the copy never sells toy RSA as real security`() {
        val everything = keysInPlay().joinToString(" ") { Narration.resolve(it) }.lowercase()

        // 1 — it says outright that these numbers are not secure.
        assertTrue(
            "the lesson never says the toy numbers are a demonstration",
            "demonstration, not security" in everything ||
                "a demonstration, not security" in everything,
        )
        assertTrue(
            "the lesson never says n factors by inspection",
            "factors by inspection" in everything,
        )

        // 2 — real key sizes and OAEP are both named.
        assertTrue("real key sizes are never named", "2048 bits" in everything)
        assertTrue("OAEP is never named", "oaep" in everything)

        // 3 — it never recommends rolling your own.
        assertTrue(
            "the lesson never points at a reviewed library",
            "reviewed library" in everything,
        )
        assertFalse("the copy calls RSA unbreakable", "unbreakable" in everything)
        assertFalse("the copy promises total security", "completely secure" in everything)

        // And it never overstates what the key pair does. The safe form names the
        // private key as what undoes the public key's work; the unqualified promise
        // — "only you can ever decrypt anything" — is the one to avoid.
        assertFalse(
            "the copy makes an unqualified decryption promise",
            "only you can ever" in everything,
        )
    }

    /** The mathematics the lesson teaches, as the learner reads it in the recap. */
    @Test
    fun `the recap states the whole key-generation chain`() {
        val recap = AlgorithmCatalog.rsa()
            .watchScript()
            .steps
            .last()
            .bullets
            .joinToString(" ") { Narration.resolve(it) }

        fun states(what: String, text: String) =
            assertTrue("the recap never states $what:\n$recap", text in recap)

        states("that RSA is asymmetric", "asymmetric")
        states("the public and private keys", "public key")
        states("n = p × q", "n = p × q")
        states("the worked modulus", "5 × 11 = 55")
        states("the totient", "φ(n) = (p − 1)(q − 1)")
        states("the condition on e", "gcd(e, φ(n)) = 1")
        states("the condition on d", "d × e ≡ 1 (mod φ(n))")
        states("the public key's shape", "(e, n)")
        states("the private key's shape", "(d, n)")
        states("encryption", "c = mᵉ mod n")
        states("decryption", "m = c^d mod n")
        states("the cost of asymmetric work", "more expensive than a symmetric cipher")
    }

    /** A number or a pair on a button is the button's whole label. */
    @Test
    fun `the option labels resolve to bare values`() {
        assertTrue(
            Narration.resolve(NarrationKey(NarrationId.RSA_OPTION_NUMBER, listOf(55L))) == "55",
        )
        assertTrue(
            Narration.resolve(
                NarrationKey(NarrationId.RSA_OPTION_PAIR, listOf(3L, 55L)),
            ) == "(3, 55)",
        )
    }

    /**
     * Every button label is short enough to sit on one line.
     *
     * Four `DecisionButton`s share a row at 360dp — about 76dp each — and a label
     * that wraps is the wall Two Pointers hit with "Move RIGHT" (ADR-032) and AVL
     * with its four case names (ADR-037). The two judgements whose options are
     * sentences are deliberately **not** buttons: they are tapped on stacked cards,
     * which is why nothing here has to be abbreviated.
     */
    @Test
    fun `no button label is long enough to wrap a decision button`() {
        val pack = AlgorithmCatalog.rsa()
        val runner = AlgorithmRunner(pack.algorithm, RsaDatasets.tryIt)
        var guard = 0
        var buttonRows = 0

        while (guard++ < 500) {
            when (val probe = runner.probe()) {
                is Probe.Terminal -> break
                is Probe.Mechanical -> runner.apply(probe.action)
                is Probe.Decide -> {
                    val decision = probe.decision
                    if (decision.kind == DecisionKind.OPTIONS) {
                        buttonRows++
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
        assertTrue("there were button rows to check", buttonRows == 8)
    }

    private companion object {
        /**
         * Characters a `labelLarge` label may hold when four share a row.
         *
         * `(1024, 91)` is the longest this lesson produces — the "forgot the
         * modulus" distractor on TRY's key pair — and digits and brackets are
         * narrower than capitals, so ten is comfortable at 76dp.
         */
        const val MAX_BUTTON_LABEL = 10
    }
}
