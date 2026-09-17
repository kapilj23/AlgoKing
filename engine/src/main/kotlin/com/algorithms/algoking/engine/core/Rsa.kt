package com.algorithms.algoking.engine.core

/**
 * RSA — the arithmetic, at a size a learner can check by hand.
 *
 * ### Why this is written out rather than delegated
 *
 * [Sha256] delegates to `MessageDigest` because its internals are not that lesson,
 * and [Aes] implements its transformations because the State changing *is* that
 * lesson (ADR-048, ADR-049). RSA is the second case again, and more sharply: the
 * whole lesson is `n = p × q`, `φ(n) = (p−1)(q−1)`, `d × e ≡ 1 (mod φ)` and
 * `c = mᵉ mod n`. Those five lines are the content, so they are written here, once,
 * and everything on screen is read back out of them.
 *
 * It is also not a choice. The platform's RSA will not touch a 55-bit — let alone a
 * 55 — modulus: `KeyFactory` rejects anything under 512 bits, and rightly. A toy
 * example cannot go through a real provider, which is exactly why the lesson is
 * careful to say what it is.
 *
 * What keeps it honest is the same standard AES was held to: every function here is
 * checked against **`java.math.BigInteger`**, which is the JDK's own arbitrary
 * precision arithmetic, over thousands of random values — and one test generates a
 * **real 2048-bit RSA key pair** and shows that `m^e mod n` round-trips through it,
 * so the claim "this is the same arithmetic, with bigger numbers" is a claim the
 * suite checks rather than one the copy makes.
 *
 * ### This is textbook RSA, and the lesson says so
 *
 * No padding, no randomness, tiny parameters. That is the right way to *show* the
 * mechanism and the wrong way to encrypt anything: textbook RSA is deterministic, so
 * the same message always gives the same ciphertext, and small messages can be
 * recovered by taking an ordinary integer root. Real use needs large parameters and
 * a padding scheme — **OAEP** for encryption — and the recap says that rather than
 * leaving a learner to find out.
 */
object Rsa {

    /**
     * The greatest common divisor, by Euclid.
     *
     * The lesson needs it for one judgement — *is `gcd(e, φ(n)) = 1`?* — which is
     * what makes an exponent a legal choice. Written iteratively because the
     * recursion adds nothing here but a stack frame.
     */
    fun gcd(a: Long, b: Long): Long {
        var left = kotlin.math.abs(a)
        var right = kotlin.math.abs(b)
        while (right != 0L) {
            val next = left % right
            left = right
            right = next
        }
        return left
    }

    /** Whether [value] is prime. Trial division, which is ample at lesson sizes. */
    fun isPrime(value: Long): Boolean {
        if (value < 2) return false
        if (value < 4) return true
        if (value % 2 == 0L) return false
        var factor = 3L
        while (factor * factor <= value) {
            if (value % factor == 0L) return false
            factor += 2
        }
        return true
    }

    /**
     * `base^exponent mod modulus`, by square-and-multiply.
     *
     * **This is the operation RSA is**, in both directions — encryption and
     * decryption differ only in which exponent goes in. It is written the way it is
     * for the reason the lesson exists: `9^27` is a twenty-six digit number, and
     * computing it and *then* reducing would overflow every fixed-width type while
     * teaching the wrong idea. Reducing at every step keeps every intermediate
     * under [modulus], which is why the arithmetic stays small even when the
     * exponent does not.
     *
     * Intermediates are `Long`, so this is exact for any modulus below about
     * 3 × 10⁹ — far beyond anything a lesson will use, and a deliberate ceiling:
     * real RSA moduli are hundreds of digits and belong to `BigInteger`, not here.
     */
    fun modPow(base: Long, exponent: Long, modulus: Long): Long {
        require(modulus > 0) { "A modulus is positive: $modulus." }
        require(exponent >= 0) { "This lesson has no negative exponents: $exponent." }
        if (modulus == 1L) return 0

        var result = 1L
        var factor = base.mod(modulus)
        var remaining = exponent

        while (remaining > 0) {
            if (remaining and 1L == 1L) result = result * factor % modulus
            factor = factor * factor % modulus
            remaining = remaining shr 1
        }
        return result
    }

    /**
     * The `d` with `d × value ≡ 1 (mod modulus)`, or null when there is none.
     *
     * Extended Euclid. Null is a real answer rather than an exception: an inverse
     * exists exactly when `gcd(value, modulus) = 1`, which is precisely the
     * condition the lesson asks the learner to check before choosing `e`. Returning
     * null lets [RsaProblem] refuse a bad exponent where it is authored instead of
     * failing halfway through a run.
     *
     * The result is normalised into `0 until modulus`, so `d` is the one a textbook
     * prints rather than a negative representative of the same class.
     */
    fun modInverse(value: Long, modulus: Long): Long? {
        require(modulus > 0) { "A modulus is positive: $modulus." }
        if (gcd(value, modulus) != 1L) return null

        var oldRemainder = value.mod(modulus)
        var remainder = modulus
        var oldCoefficient = 1L
        var coefficient = 0L

        while (remainder != 0L) {
            val quotient = oldRemainder / remainder

            val nextRemainder = oldRemainder - quotient * remainder
            oldRemainder = remainder
            remainder = nextRemainder

            val nextCoefficient = oldCoefficient - quotient * coefficient
            oldCoefficient = coefficient
            coefficient = nextCoefficient
        }

        return oldCoefficient.mod(modulus)
    }

    /**
     * Euler's totient for a modulus made of two distinct primes: `(p−1)(q−1)`.
     *
     * It counts how many numbers below `n` share no factor with it, and the only
     * reason the lesson meets it is that it is the modulus `e` and `d` are inverses
     * *in*. That is the sentence the copy uses; the counting argument is a different
     * lesson.
     */
    fun totient(p: Long, q: Long): Long = (p - 1) * (q - 1)

    /** `c = mᵉ mod n`. The same operation as [decrypt], with the other exponent. */
    fun encrypt(message: Long, e: Long, n: Long): Long = modPow(message, e, n)

    /** `m = c^d mod n`. The same operation as [encrypt], with the other exponent. */
    fun decrypt(ciphertext: Long, d: Long, n: Long): Long = modPow(ciphertext, d, n)
}

/**
 * The toy key pair a lesson is built on, and the judgements it then asks.
 *
 * Optional and defaulted on [Dataset], the additive move `graph`, `tree`,
 * `knapsack`, `cipher`, `xor`, `hash` and `aes` each made before it, so no existing
 * lesson changed when it arrived.
 *
 * ### Only four things are authored
 *
 * [p], [q], [e] and [message]. **Everything else is derived** — `n`, `φ(n)`, `d`,
 * the two keys and the ciphertext — which is ADR-045's rule for Fibonacci's call
 * counts and ADR-048's for SHA-256's digests, applied to a lesson whose entire
 * subject is a chain of dependent values. A stored `d` would be a second source of
 * truth for the one number the learner is asked to justify.
 *
 * **Everything is validated at construction**, the rule [XorProblem],
 * [CipherProblem], [HashProblem] and [AesProblem] already follow. A composite `p`, a
 * `e` sharing a factor with `φ(n)`, or a message that does not fit under `n` are all
 * typos in a dataset, and finding out mid-run is an arithmetic surprise rather than
 * a teachable state.
 */
data class RsaProblem(
    /** The first prime. Small, on purpose — see [TOY_CEILING]. */
    val p: Long,
    /** The second prime, and not the first. */
    val q: Long,
    /** The public exponent, chosen so that `gcd(e, φ(n)) = 1`. */
    val e: Long,
    /** The message. A number, because textbook RSA encrypts numbers. */
    val message: Long,
    /** The judgements the learner makes, in order. */
    val questions: List<RsaQuestion>,
) {
    init {
        require(Rsa.isPrime(p)) { "p must be prime: $p." }
        require(Rsa.isPrime(q)) { "q must be prime: $q." }
        require(p != q) {
            "p and q must be different primes, or n is a square and φ(n) is not (p−1)(q−1)."
        }
        require(e > 1) { "A public exponent above 1: $e." }
        require(e < totient) { "e must be smaller than φ(n) = $totient: $e." }
        require(Rsa.gcd(e, totient) == 1L) {
            "gcd(e, φ(n)) must be 1, and gcd($e, $totient) is ${Rsa.gcd(e, totient)}."
        }
        require(message >= 0 && message < modulus) {
            "The message must fit under n = $modulus: $message."
        }
        require(questions.isNotEmpty()) { "An RSA lesson needs something to ask." }
        require(questions.size == questions.distinct().size) {
            "A question asked twice is the same question twice: $questions."
        }
        require(p <= TOY_CEILING && q <= TOY_CEILING) {
            "This lesson's numbers are meant to be checkable by hand; " +
                "$p and $q are not. Real RSA belongs to BigInteger, not here."
        }
    }

    /** `n = p × q`. The modulus both keys share. */
    val modulus: Long get() = p * q

    /** `φ(n) = (p − 1)(q − 1)`. */
    val totient: Long get() = Rsa.totient(p, q)

    /**
     * `d`, the inverse of [e] modulo [totient].
     *
     * Computed, never authored. The constructor has already established that it
     * exists, so the non-null assertion here cannot fire.
     */
    val d: Long get() = requireNotNull(Rsa.modInverse(e, totient)) {
        "gcd(e, φ(n)) = 1 was checked at construction, so an inverse exists."
    }

    /** `(e, n)` — the half that can be published. */
    val publicKey: RsaKey get() = RsaKey(RsaKeyRole.PUBLIC, e, modulus)

    /** `(d, n)` — the half that cannot. */
    val privateKey: RsaKey get() = RsaKey(RsaKeyRole.PRIVATE, d, modulus)

    /** `c = mᵉ mod n`. */
    val ciphertext: Long get() = Rsa.encrypt(message, e, modulus)

    /**
     * `c^d mod n`, which is the message again.
     *
     * Read out of the run rather than copied from [message] — if the round trip did
     * not hold, the lesson would say so instead of asserting what it hoped for. The
     * same call `XorState.recovered` makes (ADR-047).
     */
    val recovered: Long get() = Rsa.decrypt(ciphertext, d, modulus)

    /** How many decisions the learner is asked to make. */
    val decisionCount: Int get() = questions.size

    companion object {
        /**
         * The largest prime a lesson may use.
         *
         * Not a technical limit — [Rsa.modPow] is exact far beyond it. It is a
         * statement about what this lesson is for: every number on screen has to be
         * one a learner can check on paper, and the moment they cannot, the picture
         * is asking for trust rather than giving evidence.
         */
        const val TOY_CEILING: Long = 97
    }
}

/** Which half of the pair a key is, and therefore what may be done with it. */
enum class RsaKeyRole(val label: String) {
    /** `(e, n)`. Published, and used here to encrypt. */
    PUBLIC("Public key"),

    /** `(d, n)`. Kept, and used here to decrypt. */
    PRIVATE("Private key"),
    ;

    /** Whether this half is the one that must not be shared. */
    val secret: Boolean get() = this == PRIVATE
}

/**
 * One half of an RSA key pair: an exponent and the modulus it shares with the other.
 *
 * Both keys carry the **same** `n`, which is the thing the picture has to make
 * obvious — a learner who reads the pair as two unrelated numbers has missed why
 * one undoes the other.
 */
data class RsaKey(val role: RsaKeyRole, val exponent: Long, val modulus: Long) {
    /** `(3, 55)` — how a textbook prints it, and how the lesson does. */
    val printed: String get() = "($exponent, $modulus)"
}

/**
 * One thing a learner has to get right about RSA.
 *
 * Two of them are **concept judgements** and the rest walk the derivation chain —
 * which is the shape this lesson needs, because the chain *is* the content. Each
 * number is asked only once the ones it depends on are settled and on screen, so
 * every answer is read off the picture rather than recalled.
 *
 * The learner is never asked to *perform* the hard arithmetic: nobody computes
 * `9^27 mod 55` in their head, and PRODUCT_SPEC.md §3 gives the app the arithmetic.
 * What they are asked for is which number the formula produces, with the formula
 * printed and the operands lit.
 */
enum class RsaQuestion {
    /** Which kind of cryptography uses two keys. The lesson's frame. */
    ASYMMETRIC,

    /** `n = p × q`. */
    MODULUS,

    /** `φ(n) = (p − 1)(q − 1)`. */
    TOTIENT,

    /** Which candidate exponent satisfies `gcd(e, φ(n)) = 1`. */
    PUBLIC_EXPONENT,

    /** Which `d` satisfies `d × e ≡ 1 (mod φ(n))`. */
    PRIVATE_EXPONENT,

    /** `(e, n)`. */
    PUBLIC_KEY,

    /** `(d, n)`. */
    PRIVATE_KEY,

    /** `c = mᵉ mod n`. */
    ENCRYPT,

    /** `m = c^d mod n`. */
    DECRYPT,

    /** Which half must stay secret. The one that matters outside the arithmetic. */
    SECRET_KEY,
}
