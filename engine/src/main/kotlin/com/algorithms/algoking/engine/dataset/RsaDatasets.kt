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

    /** The ten judgements, in the order the lesson asks them. */
    val EXERCISES: List<RsaQuestion> = RsaQuestion.entries.toList()

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
        ),
    )

    /**
     * TRY — `p = 7`, `q = 13`, `e = 5`, `m = 4`.
     *
     * **A different key pair, and that is a deliberate departure from the brief.**
     *
     * §10 of the brief lists the TRY exercises using WATCH's numbers — *"Given p = 5,
     * q = 11, what is n?"*. That would make six of the ten judgements answerable from
     * memory: a learner who watched `n = 55` land does not have to multiply anything
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
        ),
    )
}
