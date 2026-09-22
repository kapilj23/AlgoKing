package com.algorithms.algoking.engine.dataset

import com.algorithms.algoking.engine.core.Dataset
import com.algorithms.algoking.engine.core.RsaProblem
import com.algorithms.algoking.engine.core.RsaQuestion

/**
 * The toy key pairs the RSA lesson is built on.
 *
 * **Four numbers are authored per dataset** — `p`, `q`, `e` and the message — and
 * everything else is derived: `n`, `φ(n)`, `d`, both keys and the ciphertext. That is
 * ADR-045's rule for Fibonacci's call counts and ADR-048's for SHA-256's digests,
 * applied to a lesson that *is* a chain of dependent values. A stored `d` would be a
 * second source of truth for the one number the learner is asked to justify, and it
 * would go quietly wrong the first time a prime moved.
 *
 * `RsaMathTest` checks both pairs against `java.math.BigInteger` and shows the same
 * two formulas round-tripping through a real 2048-bit key, so the lesson's claim —
 * *this is what real RSA does, with bigger numbers* — is checked rather than
 * asserted.
 *
 * ### These numbers are a demonstration
 *
 * A two-digit modulus is the right size to *show* the mechanism and the wrong size
 * to protect anything: `n = 55` factors by inspection, so anyone who sees the public
 * key has the private one. The lesson says so on the picture for its whole length,
 * not only in the recap.
 */
object RsaDatasets {

    /**
     * The twelve judgements, **in the order the lesson asks them** — story first,
     * arithmetic second (ADR-052).
     *
     * ```
     * ACT I   ASYMMETRIC · SHAREABLE_KEY · SECRET_KEY
     *         ENCRYPT_OPERATION · ENCRYPT · DECRYPT_OPERATION · DECRYPT
     *
     * ACT II  MODULUS · TOTIENT · PUBLIC_EXPONENT · PRIVATE_EXPONENT
     *         KEY_PAIR_PURPOSE
     * ```
     *
     * Written out rather than taken from `RsaQuestion.entries`, which is what it used
     * to be. The enum's order is an implementation detail that also seats each
     * question's options; the asked order is a **pedagogical decision**, and the two
     * being the same list was the thing that made it invisible when the lesson opened
     * on `n = p × q`.
     *
     * ### What is deliberately not here
     *
     * `PUBLIC_KEY` and `PRIVATE_KEY`. Both pairs are on screen from the beat that
     * hands the learner the keys, so *"which pair is the public key?"* in Act II is a
     * reading exercise rather than a judgement. Their entries, options, distractors
     * and copy are all still in place and a dataset that lists them gets them back —
     * `RsaEncryption.stepsFor` turns any unasked question into a statement — but this
     * lesson states them instead.
     *
     * The two exponents behind them, `e` and `d`, are still asked, and they are the
     * part a learner could not have read anywhere.
     */
    val EXERCISES: List<RsaQuestion> = listOf(
        // Layer 1 — the concept, on a message. Answerable with no arithmetic, and
        // asked before any number is on screen (ADR-053).
        RsaQuestion.ENCRYPT_OPERATION,
        RsaQuestion.ASYMMETRIC,
        RsaQuestion.ENCRYPT_KEY,
        RsaQuestion.DECRYPT_KEY,
        RsaQuestion.SECRET_KEY,

        // Layer 2 — the mechanism, once the toy example has announced itself.
        RsaQuestion.ENCRYPT,
        RsaQuestion.DECRYPT,
        RsaQuestion.MODULUS,
        RsaQuestion.TOTIENT,
        RsaQuestion.PUBLIC_EXPONENT,
        RsaQuestion.PRIVATE_EXPONENT,
        RsaQuestion.KEY_PAIR_PURPOSE,
    )

    /**
     * WATCH — `p = 5`, `q = 11`, `e = 3`, `m = 4`.
     *
     * The worked example from the brief, and the one every RSA write-up reaches for.
     * Four properties earn it its place:
     *
     *  - **the arithmetic is checkable in your head.** `5 × 11 = 55` and
     *    `4 × 10 = 40` are the whole of the key generation, which is what lets the
     *    picture be evidence rather than something to take on trust;
     *  - **`e = 3` is the smallest legal exponent here**, and the three candidates
     *    below it all share a factor with 40 — so the `gcd(e, φ(n)) = 1` condition
     *    is the thing that decides it rather than a rule stated beside it;
     *  - **`d = 27` is nothing like `e`.** A key pair where the two exponents looked
     *    similar would let a learner finish without noticing they are different
     *    numbers doing opposite jobs;
     *  - **`4 → 9 → 4` is short enough to hold in mind**, so the round trip reads as
     *    one movement rather than as two unrelated calculations.
     */
    val watch = Dataset(
        values = emptyList(),
        label = "watch",
        rsa = RsaProblem(
            p = 5,
            q = 11,
            e = 3,
            message = 4,
            questions = EXERCISES,
            // The lesson's hero, and what the learner meets first (ADR-053). Short
            // enough to sit on one line at 360dp, and obviously something a person
            // would actually want kept private — which `HELLO` is not.
            plaintext = "MEET AT 7",
            illustrativeCiphertext = "8F 3A C1 D4 9B 22",
        ),
    )

    /**
     * TRY — `p = 7`, `q = 13`, `e = 5`, `m = 4`.
     *
     * **A different key pair, and that is a deliberate departure from the brief.**
     *
     * §10 of the brief lists the TRY exercises using WATCH's numbers — *"Given p = 5,
     * q = 11, what is n?"*. That would make four of the twelve judgements answerable
     * from memory: a learner who watched `n = 55` land does not have to multiply anything
     * to answer it again. ADR-014 is explicit that a TRY dataset must not allow that,
     * and this project has replaced a brief-supplied dataset for exactly this reason
     * twice before — ADR-044, when 0/1 Knapsack's bag had an optimum both greedy
     * strategies already found, and ADR-047, when XOR's TRY key produced the same
     * ciphertext WATCH produced.
     *
     * So the *questions* are the brief's ten, unchanged and in its order, and the
     * *numbers* are new. Every one of them has to be derived:
     *
     * ```
     * n    = 7 × 13   = 91        (WATCH: 55)
     * φ(n) = 6 × 12   = 72        (WATCH: 40)
     * e    = 5,  gcd(5, 72) = 1   (WATCH: 3)
     * d    = 29, 5 × 29 = 145 = 2 × 72 + 1
     * c    = 4⁵ mod 91 = 1024 mod 91 = 23
     * ```
     *
     * The message stays `4`, on purpose: it is the one value the learner is not asked
     * to derive, and holding it still is what makes the two runs comparable — the
     * same message, a different key pair, a different ciphertext.
     *
     * `e = 5` also does something WATCH's `e = 3` cannot. Its unreduced power is
     * `4⁵ = 1024`, four digits against a two-digit modulus, so the "forgot the
     * modulus" distractor is visibly absurd rather than merely wrong — and a learner
     * who reaches for it has told you exactly what they did.
     */
    val tryIt = Dataset(
        values = emptyList(),
        label = "try",
        rsa = RsaProblem(
            p = 7,
            q = 13,
            e = 5,
            message = 4,
            questions = EXERCISES,
            // A different message too, for the same reason the key pair differs: a
            // learner who saw "MEET AT 7" encrypted should be applying the idea
            // rather than recognising the picture (ADR-014).
            plaintext = "SEND THE MAP",
            illustrativeCiphertext = "2B 7E 05 A9 C3 61",
        ),
    )
}
