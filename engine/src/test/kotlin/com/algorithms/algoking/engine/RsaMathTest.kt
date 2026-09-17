package com.algorithms.algoking.engine

import com.algorithms.algoking.engine.core.Rsa
import com.algorithms.algoking.engine.core.RsaKeyRole
import com.algorithms.algoking.engine.core.RsaProblem
import com.algorithms.algoking.engine.core.RsaQuestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPrivateKeySpec
import java.security.spec.RSAPublicKeySpec
import kotlin.random.Random

/**
 * The arithmetic, before anything is built on it.
 *
 * This is the first thing that was written for the RSA lesson and the first thing
 * that ran. Every number the lesson puts on screen comes out of these functions, so
 * a mistake here is a lesson that teaches something false — and unlike AES there is
 * no published NIST vector to check a toy modulus against.
 *
 * What replaces it is **`java.math.BigInteger`**, the JDK's own arbitrary-precision
 * arithmetic, over thousands of random values; and one test that generates a **real
 * 2048-bit RSA key pair** and shows the same `m^e mod n` round-trips through it. So
 * the engine is checked against something nobody here wrote, which is the standard
 * ADR-048 set for SHA-256 and ADR-049 for AES.
 */
class RsaMathTest {

    // ── 1. The toy example the lesson is built on ────────────────────────────

    /**
     * The brief's worked example, checked end to end.
     *
     * Nothing in this test is read from the engine's own derived values except the
     * thing being asserted: the expected numbers are written out as the brief gives
     * them, so this fails if the engine and the brief ever disagree.
     */
    @Test
    fun `the toy example is arithmetically correct, exactly as specified`() {
        val p = 5L
        val q = 11L
        val e = 3L
        val message = 4L

        // n = p × q = 55
        val n = p * q
        assertEquals(55L, n)

        // φ(n) = (p − 1)(q − 1) = 4 × 10 = 40
        val totient = Rsa.totient(p, q)
        assertEquals(40L, totient)
        assertEquals((p - 1) * (q - 1), totient)

        // gcd(3, 40) = 1, which is what makes 3 a legal public exponent
        assertEquals(1L, Rsa.gcd(e, totient))

        // d = 27, because 3 × 27 = 81 and 81 mod 40 = 1
        val d = Rsa.modInverse(e, totient)
        assertEquals(27L, d)
        assertEquals(81L, e * 27L)
        assertEquals(1L, (e * 27L) % totient)

        // c = 4³ mod 55 = 64 mod 55 = 9
        val ciphertext = Rsa.encrypt(message, e, n)
        assertEquals(9L, ciphertext)
        assertEquals(64L, 4L * 4L * 4L)
        assertEquals(9L, 64L % 55L)

        // m = 9²⁷ mod 55 = 4
        assertEquals(message, Rsa.decrypt(ciphertext, 27L, n))
    }

    /** And the same example as the lesson's own data model states it. */
    @Test
    fun `the problem derives every value the brief lists`() {
        val problem = RsaProblem(
            p = 5,
            q = 11,
            e = 3,
            message = 4,
            questions = RsaQuestion.entries.toList(),
        )

        assertEquals(55L, problem.modulus)
        assertEquals(40L, problem.totient)
        assertEquals(27L, problem.d)
        assertEquals(9L, problem.ciphertext)
        assertEquals(4L, problem.recovered)

        assertEquals("(3, 55)", problem.publicKey.printed)
        assertEquals("(27, 55)", problem.privateKey.printed)
        assertEquals(RsaKeyRole.PUBLIC, problem.publicKey.role)
        assertEquals(RsaKeyRole.PRIVATE, problem.privateKey.role)

        // Both halves share the modulus, which is the thing the picture must show.
        assertEquals(problem.publicKey.modulus, problem.privateKey.modulus)
        // And the private half is the one that must not be shared.
        assertTrue(problem.privateKey.role.secret)
        assertTrue(!problem.publicKey.role.secret)
    }

    /**
     * Every message under `n` round-trips, not just the one the lesson uses.
     *
     * A lesson that worked for `m = 4` and nothing else would be a coincidence
     * dressed as a rule.
     */
    @Test
    fun `every message under n round-trips through this key pair`() {
        val problem = RsaProblem(5, 11, 3, 4, RsaQuestion.entries.toList())
        for (m in 0 until problem.modulus) {
            val c = Rsa.encrypt(m, problem.e, problem.modulus)
            assertEquals("m = $m", m, Rsa.decrypt(c, problem.d, problem.modulus))
        }
    }

    // ── 2. Checked against the JDK ───────────────────────────────────────────

    @Test
    fun `modPow agrees with BigInteger over thousands of random values`() {
        val random = Random(20260917)
        repeat(4_000) {
            val base = random.nextLong(0, 100_000)
            val exponent = random.nextLong(0, 4_096)
            val modulus = random.nextLong(1, 100_000)

            val expected = BigInteger.valueOf(base)
                .modPow(BigInteger.valueOf(exponent), BigInteger.valueOf(modulus))
                .toLong()

            assertEquals(
                "$base^$exponent mod $modulus",
                expected,
                Rsa.modPow(base, exponent, modulus),
            )
        }
    }

    @Test
    fun `gcd agrees with BigInteger`() {
        val random = Random(4242)
        repeat(2_000) {
            val a = random.nextLong(0, 1_000_000)
            val b = random.nextLong(0, 1_000_000)
            assertEquals(
                "gcd($a, $b)",
                BigInteger.valueOf(a).gcd(BigInteger.valueOf(b)).toLong(),
                Rsa.gcd(a, b),
            )
        }
    }

    @Test
    fun `modInverse agrees with BigInteger, and is null exactly when there is none`() {
        val random = Random(99)
        repeat(3_000) {
            val value = random.nextLong(1, 10_000)
            val modulus = random.nextLong(2, 10_000)
            val inverse = Rsa.modInverse(value, modulus)

            if (Rsa.gcd(value, modulus) != 1L) {
                assertNull("no inverse for $value mod $modulus", inverse)
            } else {
                val expected = BigInteger.valueOf(value)
                    .modInverse(BigInteger.valueOf(modulus))
                    .toLong()
                assertEquals("$value⁻¹ mod $modulus", expected, inverse)
                // And it is an inverse, which is the property rather than the value.
                assertEquals(1L, value % modulus * inverse!! % modulus)
            }
        }
    }

    @Test
    fun `isPrime agrees with BigInteger up to two thousand`() {
        for (candidate in 0L..2_000L) {
            val expected = candidate >= 2 &&
                BigInteger.valueOf(candidate).isProbablePrime(50)
            assertEquals("$candidate", expected, Rsa.isPrime(candidate))
        }
    }

    /**
     * **The lesson's central claim, checked against real RSA.**
     *
     * The copy says the toy example is the same arithmetic as the real thing with
     * bigger numbers. That is a claim worth checking rather than asserting, so this
     * generates a genuine 2048-bit key pair through the JDK, pulls out its `n`, `e`
     * and `d`, and shows that `c = mᵉ mod n` and `m = c^d mod n` — the two lines the
     * lesson teaches — round-trip through it.
     *
     * It also demonstrates the distance the lesson is careful about: `n` here is 617
     * digits, against the lesson's two.
     */
    @Test
    fun `the lesson's two formulas are what real RSA does, at 2048 bits`() {
        val generator = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
        val pair = generator.generateKeyPair()

        val factory = KeyFactory.getInstance("RSA")
        val publicSpec = factory.getKeySpec(pair.public, RSAPublicKeySpec::class.java)
        val privateSpec = factory.getKeySpec(pair.private, RSAPrivateKeySpec::class.java)

        val n = publicSpec.modulus
        val e = publicSpec.publicExponent
        val d = privateSpec.privateExponent

        assertEquals("both halves share n", n, privateSpec.modulus)
        assertEquals(2048, n.bitLength())

        val message = BigInteger("42424242424242424242424242")
        val ciphertext = message.modPow(e, n)
        assertEquals("c^d mod n is m again", message, ciphertext.modPow(d, n))

        // The distance the copy is careful about: 617 digits against two.
        assertTrue("a real modulus is enormous", n.toString().length > 600)
        assertTrue("the lesson's is not", RsaProblem(5, 11, 3, 4, RsaQuestion.entries.toList()).modulus < 100)

        // And the type the real thing needs is not the one the lesson uses.
        assertTrue(n > BigInteger.valueOf(Long.MAX_VALUE))

        // Sanity: the JDK's own keys really are an (n, e, d) triple of this shape.
        assertTrue(pair.public is RSAPublicKey)
        assertTrue(pair.private is RSAPrivateKey)
    }

    // ── 3. Modular arithmetic, at the edges ──────────────────────────────────

    @Test
    fun `modPow handles the cases that trip a naive implementation`() {
        // Exponent 0 is 1, whatever the base.
        assertEquals(1L, Rsa.modPow(7, 0, 55))
        // Base 0 stays 0.
        assertEquals(0L, Rsa.modPow(0, 5, 55))
        // Modulus 1 collapses everything.
        assertEquals(0L, Rsa.modPow(7, 5, 1))
        // A base above the modulus is reduced first.
        assertEquals(Rsa.modPow(4, 3, 55), Rsa.modPow(59, 3, 55))
        // A negative base is normalised into the positive class, not left negative.
        assertTrue(Rsa.modPow(-4, 3, 55) in 0 until 55)
        assertEquals(
            BigInteger.valueOf(-4).modPow(BigInteger.valueOf(3), BigInteger.valueOf(55)).toLong(),
            Rsa.modPow(-4, 3, 55),
        )
        // A large exponent never overflows, because nothing is ever raised whole.
        assertEquals(
            BigInteger.valueOf(9).modPow(BigInteger.valueOf(1_000_003), BigInteger.valueOf(55)).toLong(),
            Rsa.modPow(9, 1_000_003, 55),
        )
    }

    @Test
    fun `gcd and modInverse handle their edges`() {
        assertEquals(5L, Rsa.gcd(5, 0))
        assertEquals(5L, Rsa.gcd(0, 5))
        assertEquals(0L, Rsa.gcd(0, 0))
        assertEquals(1L, Rsa.gcd(3, 40))
        assertEquals(4L, Rsa.gcd(4, 40))

        // No inverse when a factor is shared — the condition the lesson asks about.
        assertNull(Rsa.modInverse(4, 40))
        assertNull(Rsa.modInverse(10, 40))
        assertEquals(27L, Rsa.modInverse(3, 40))
        // The result is the textbook representative, never a negative one.
        assertTrue(Rsa.modInverse(3, 40)!! in 0 until 40)
    }

    // ── 4. A dataset is refused where it is authored ─────────────────────────

    private fun problem(
        p: Long = 5,
        q: Long = 11,
        e: Long = 3,
        message: Long = 4,
    ) = RsaProblem(p, q, e, message, RsaQuestion.entries.toList())

    @Test
    fun `a composite p or q is refused`() {
        listOf(4L, 9L, 1L, 0L, 55L).forEach { composite ->
            runCatching { problem(p = composite) }
                .onSuccess { error("p = $composite was accepted") }
            runCatching { problem(q = composite) }
                .onSuccess { error("q = $composite was accepted") }
        }
    }

    @Test
    fun `equal primes are refused, because phi would be wrong`() {
        runCatching { problem(p = 5, q = 5) }.onSuccess {
            error("p = q was accepted, and (p−1)(q−1) is not φ(p²)")
        }
    }

    /** The condition the lesson makes the learner check, enforced where it is set. */
    @Test
    fun `a public exponent sharing a factor with phi is refused`() {
        // φ(55) = 40, so anything even or divisible by 5 is out.
        listOf(2L, 4L, 5L, 10L, 20L, 8L).forEach { bad ->
            runCatching { problem(e = bad) }.onSuccess { error("e = $bad was accepted") }
        }
        // ...and the legal ones are accepted.
        listOf(3L, 7L, 9L, 11L, 13L).forEach { good ->
            assertEquals(1L, Rsa.gcd(good, 40))
            problem(e = good)
        }
    }

    @Test
    fun `an exponent outside the useful range is refused`() {
        runCatching { problem(e = 1) }.onSuccess { error("e = 1 was accepted") }
        runCatching { problem(e = 0) }.onSuccess { error("e = 0 was accepted") }
        runCatching { problem(e = 41) }.onSuccess { error("e above φ(n) was accepted") }
    }

    @Test
    fun `a message that does not fit under n is refused`() {
        runCatching { problem(message = 55) }.onSuccess { error("m = n was accepted") }
        runCatching { problem(message = 100) }.onSuccess { error("m > n was accepted") }
        runCatching { problem(message = -1) }.onSuccess { error("a negative m was accepted") }
        // The boundaries that are legal really are.
        problem(message = 0)
        problem(message = 54)
    }

    /**
     * The lesson refuses numbers a learner could not check by hand.
     *
     * Not a technical limit — `modPow` is exact far beyond it. It is a statement
     * about what this lesson is for.
     */
    @Test
    fun `primes too large for a hand-checkable lesson are refused`() {
        runCatching { problem(p = 101) }.onSuccess { error("p = 101 was accepted") }
        runCatching { problem(q = 8191) }.onSuccess { error("q = 8191 was accepted") }
        assertEquals(97L, RsaProblem.TOY_CEILING)
    }

    @Test
    fun `an empty or duplicated question list is refused`() {
        runCatching {
            RsaProblem(5, 11, 3, 4, emptyList())
        }.onSuccess { error("no questions was accepted") }

        runCatching {
            RsaProblem(5, 11, 3, 4, listOf(RsaQuestion.MODULUS, RsaQuestion.MODULUS))
        }.onSuccess { error("a repeated question was accepted") }
    }

    /** Other small key pairs work too, so the lesson is not tuned to one dataset. */
    @Test
    fun `other toy key pairs round-trip as well`() {
        val pairs = listOf(
            Triple(7L, 13L, 5L),
            Triple(11L, 13L, 7L),
            Triple(3L, 7L, 5L),
            Triple(13L, 17L, 5L),
            Triple(17L, 19L, 7L),
        )
        pairs.forEach { (p, q, e) ->
            val built = RsaProblem(p, q, e, message = 4, questions = RsaQuestion.entries.toList())
            assertEquals("$p·$q e=$e", built.message, built.recovered)
            assertEquals(1L, built.e * built.d % built.totient)
        }
    }
}
