package com.algorithms.algoking.engine.core

import java.security.MessageDigest

/**
 * SHA-256 — **the only implementation in the codebase**.
 *
 * ### Why this delegates rather than implements
 *
 * Every other transform in `:engine` is written out by hand, because every other
 * transform is the lesson: [xorBit] is an inequality because *the result is 1 when
 * the bits differ* is the sentence being taught, and [shiftLetter] does its own
 * `mod 26` because the wrap is the whole of the Caesar lesson.
 *
 * SHA-256 is the opposite case. Its internals — padding, the message schedule, 64
 * compression rounds over eight working variables — are deliberately **not** what
 * this lesson teaches (ADR-048), and a hand-rolled copy of them would be a second,
 * unreviewed cryptographic implementation whose only job is to agree with the
 * platform's. So the platform's is used: `MessageDigest` is JDK standard library,
 * present on every Android level the app supports, and adds no dependency.
 *
 * **This is the first `java.*` import in `:engine`**, and it is worth being precise
 * about what that does and does not cost. The module boundary ARCHITECTURE.md §3
 * enforces is *no Android and no Compose*, so that a lesson cannot reach a
 * `@Composable`; `java.security` is neither. The module is still a pure JVM module,
 * the tests still run in milliseconds with no Robolectric, and `MessageDigest
 * .getInstance("SHA-256")` is required by the Java platform specification, so there
 * is no device on which it is absent.
 *
 * ### This is a hash function, not a cipher
 *
 * There is no key, there is no inverse, and nothing here decrypts. A digest is a
 * fixed-size fingerprint of its input, and recovering the input from it is
 * designed to be computationally infeasible — which is a different claim from
 * "impossible", and the copy says the one that is true.
 */
object Sha256 {

    /** A SHA-256 digest is always this many bits, whatever went in. */
    const val BITS: Int = 256

    /** The same size, in bytes. */
    const val BYTES: Int = BITS / 8

    /** The same size again, written as hexadecimal — two characters per byte. */
    const val HEX_LENGTH: Int = BYTES * 2

    /** How many hex characters are shown per group, so 64 of them stay readable. */
    const val GROUP_SIZE: Int = 8

    private const val ALGORITHM = "SHA-256"

    /**
     * The digest of [text], as 64 lowercase hexadecimal characters.
     *
     * The input is read as **UTF-8**, stated rather than left to a platform
     * default: the same string hashed under two encodings gives two different
     * digests, and a lesson about determinism cannot afford to be
     * locale-dependent. Every displayed value in the lesson comes through here,
     * so the picture and the claim cannot disagree.
     */
    fun hex(text: String): String {
        val digest = MessageDigest.getInstance(ALGORITHM).digest(text.toByteArray(Charsets.UTF_8))
        return buildString(HEX_LENGTH) {
            digest.forEach { byte ->
                val value = byte.toInt() and 0xFF
                append(HEX_DIGITS[value ushr 4])
                append(HEX_DIGITS[value and 0x0F])
            }
        }
    }

    /**
     * The same digest, split into [GROUP_SIZE]-character groups.
     *
     * Sixty-four characters in one run is a wall a reader's eye slides off, and on
     * a 360dp phone it is also wider than the screen. Grouping is a **reading
     * aid and nothing else**: the characters and their order are untouched, so
     * `groups(h).joinToString("") == h` for every digest, and there is a test
     * saying so.
     */
    fun groups(hex: String): List<String> = hex.chunked(GROUP_SIZE)

    /**
     * Which character positions of two digests differ.
     *
     * This is how the avalanche beat is stated as a number the learner can check
     * rather than as an adjective. It is **computed from the two digests**, never
     * authored — the rule ADR-045 set for Fibonacci's call counts — so a dataset
     * change can never leave the copy claiming something the picture contradicts.
     *
     * Digests are always the same length, so a mismatch is a caller bug rather
     * than something to tolerate quietly.
     */
    fun differingPositions(a: String, b: String): Set<Int> {
        require(a.length == b.length) {
            "Digests are always $HEX_LENGTH characters: ${a.length} against ${b.length}."
        }
        return a.indices.filterTo(mutableSetOf()) { a[it] != b[it] }
    }

    private val HEX_DIGITS = "0123456789abcdef".toCharArray()
}

/**
 * The messages a hashing lesson hashes, and the judgements it then asks about.
 *
 * Optional and defaulted on [Dataset], the additive move `graph`, `tree`,
 * `knapsack`, `cipher` and `xor` each made before it, so no existing lesson
 * changed when it arrived.
 *
 * **Everything is validated at construction**, for the reason [XorProblem] and
 * [CipherProblem] are: a lesson with no messages has nothing to hash and a lesson
 * with no questions has nothing to ask, and discovering either halfway through a
 * run is an index error rather than a teachable state.
 */
data class HashProblem(
    /**
     * What gets hashed, in order. Each one is a real string put through
     * [Sha256.hex]; nothing here is an authored digest.
     */
    val messages: List<String>,
    /**
     * The judgements the learner makes once the evidence is on screen, in order.
     *
     * They are asked **after** every message has been hashed, which is what makes
     * each one answerable by reading the picture rather than by recalling a claim.
     */
    val questions: List<HashQuestion>,
) {
    init {
        require(messages.isNotEmpty()) { "A hashing lesson needs something to hash." }
        require(messages.none { it.isEmpty() }) {
            "An empty message hashes fine, but it is a poor thing to show first."
        }
        require(questions.isNotEmpty()) { "A hashing lesson needs something to ask." }
        require(questions.size == questions.distinct().size) {
            "A question asked twice is the same question twice: $questions."
        }
    }

    /** The digest of message [index]. Computed, never stored. */
    fun digestOf(index: Int): String = Sha256.hex(messages[index])

    /** Every digest, in message order. */
    val digests: List<String> get() = messages.map(Sha256::hex)

    /** How many decisions the learner is asked to make. */
    val decisionCount: Int get() = questions.size

    /**
     * The two messages that differ by one character, if the lesson carries such a
     * pair — the avalanche beat's evidence.
     *
     * Derived by looking for it rather than authored, so a dataset cannot claim a
     * pair it does not contain.
     */
    val avalanchePair: Pair<Int, Int>?
        get() {
            for (i in messages.indices) {
                for (j in messages.indices) {
                    if (i == j) continue
                    if (differsByOneCharacter(messages[i], messages[j])) return i to j
                }
            }
            return null
        }

    /**
     * The two positions holding the **same** message, if the lesson hashes one
     * twice — the determinism beat's evidence, and the only honest way to show it.
     *
     * Saying "the same input always gives the same hash" over a single row asks
     * the learner to take it on trust. Hashing the message a second time, as a
     * separate run, puts two independently produced digests side by side.
     */
    val repeatedPair: Pair<Int, Int>?
        get() {
            for (i in messages.indices) {
                for (j in i + 1 until messages.size) {
                    if (messages[i] == messages[j]) return i to j
                }
            }
            return null
        }

    /**
     * Message positions with distinct lengths, shortest first — the fixed-length
     * evidence.
     *
     * **At most three**, and that cap is a layout decision with a pedagogical
     * reason behind it. The claim is *"different input lengths, one output
     * length"*, and three rows — the shortest, one in between, the longest — make
     * it as completely as six would while leaving the two statement cards and the
     * buttons above the fold on a 360dp screen. A row the learner has to scroll
     * past to reach the question is a row that is not evidence.
     */
    val lengthLadder: List<Int>
        get() {
            val distinct = messages.indices
                .distinctBy { messages[it].length }
                .sortedBy { messages[it].length }
            if (distinct.size <= LADDER_ROWS) return distinct
            // Shortest and longest are the claim; the middle one is what stops it
            // reading as a trick played with two extremes.
            return listOf(
                distinct.first(),
                distinct[distinct.size / 2],
                distinct.last(),
            )
        }

    private companion object {
        /** How many rows the fixed-length beat may put on screen at once. */
        const val LADDER_ROWS = 3
    }

    private fun differsByOneCharacter(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        return a.indices.count { a[it] != b[it] } == 1
    }
}

/**
 * One thing a learner has to get right about hashing.
 *
 * These are **concept judgements, not arithmetic**, which makes this lesson the
 * odd one in the library and is worth stating plainly (ADR-048). Every other
 * lesson asks the learner to execute a step of the algorithm; nobody executes a
 * step of SHA-256 by hand, and a lesson that pretended otherwise would be teaching
 * a gesture over 64 compression rounds.
 *
 * What keeps them from being a quiz is **where the answer comes from**: each one is
 * asked only once the digests it is about are on screen, and the projector shows
 * the rows that answer it. The learner reads the evidence, exactly as the XOR
 * lesson has them read its truth table.
 */
enum class HashQuestion {
    /** Different input lengths, one output length. The headline property. */
    FIXED_LENGTH,

    /** The same input, hashed twice, giving the same digest both times. */
    DETERMINISTIC,

    /** One character changed, and most of the digest with it. */
    AVALANCHE,

    /** There is no key and no decrypt step. The property that is not a cipher's. */
    ONE_WAY,

    /** 256 bits, 32 bytes, 64 hexadecimal characters — the same three numbers. */
    OUTPUT_SIZE,
}
