package com.algorithms.algoking.engine.core

/**
 * A binary message and the key it is XORed with.
 *
 * Optional and defaulted on [Dataset], the additive move `graph`, `tree`,
 * `knapsack` and `cipher` made before it, so no existing lesson changed when it
 * arrived.
 *
 * **Everything is validated at construction**, which is the whole reason this is a
 * type rather than two loose strings: a lesson whose key is a different length
 * from its message has no bit 4 to ask about, and discovering that halfway through
 * a run is an index-out-of-bounds rather than a teachable state. The rule the
 * codebase already follows for a non-positive Dijkstra weight and a zero-length
 * Caesar plaintext — refuse it where it is authored, not where it is used.
 */
data class XorProblem(
    val plaintext: String,
    val key: String,
    /**
     * Whether the lesson goes on to apply the key a second time and recover the
     * original.
     *
     * That reversal is the *point* of an XOR cipher, so WATCH does it and watches
     * every bit come back. TRY does not: four bits of encryption is the judgement,
     * and a second identical pass is patience rather than understanding — the
     * reasoning ADR-026 used for Merge Sort's deeper splits.
     */
    val roundTrip: Boolean = false,
) {
    init {
        require(plaintext.isNotEmpty()) { "An XOR lesson needs something to encrypt." }
        require(key.isNotEmpty()) { "An XOR lesson needs a key." }
        require(plaintext.length == key.length) {
            "Plaintext and key must be the same length: " +
                "'$plaintext' is ${plaintext.length}, '$key' is ${key.length}."
        }
        require(plaintext.all { it == '0' || it == '1' }) {
            "Plaintext must be binary: '$plaintext'."
        }
        require(key.all { it == '0' || it == '1' }) { "Key must be binary: '$key'." }
    }

    val length: Int get() = plaintext.length

    val plaintextBits: List<Int> get() = plaintext.map { it - '0' }
    val keyBits: List<Int> get() = key.map { it - '0' }

    /** The answer, in full. The one place a ciphertext is produced. */
    val ciphertext: String get() = xorBits(plaintext, key)

    /**
     * How many bits the learner is asked about: every bit once, or twice when the
     * lesson also goes back the other way.
     */
    val decisionCount: Int get() = if (roundTrip) length * 2 else length
}

/**
 * XOR on one bit — **the only implementation in the codebase**.
 *
 * ```
 * 0 XOR 0 = 0      the bits are the same
 * 0 XOR 1 = 1      the bits differ
 * 1 XOR 0 = 1      the bits differ
 * 1 XOR 1 = 0      the bits are the same
 * ```
 *
 * Written as an inequality rather than as `a xor b`, because that is the sentence
 * the lesson teaches: *the result is 1 when the bits are different*. A learner who
 * leaves with that has the truth table without having memorised four rows.
 *
 * The projector, the walkthrough, the decision options and the tests all read this
 * or [xorBits]; a second expression anywhere is how the picture and the algorithm
 * start disagreeing (the rule ARCHITECTURE.md §4.4 sets for Binary Search's
 * midpoint).
 */
fun xorBit(a: Int, b: Int): Int = if (a != b) 1 else 0

/**
 * Two binary strings, XORed bit by bit.
 *
 * Callers are expected to have validated through [XorProblem]; this refuses
 * mismatched lengths rather than silently truncating, because a shorter answer
 * than the input is the kind of wrong that looks right.
 */
fun xorBits(a: String, b: String): String {
    require(a.length == b.length) {
        "XOR needs equal lengths: ${a.length} against ${b.length}."
    }
    return a.indices
        .map { xorBit(bitAt(a, it), bitAt(b, it)) }
        .joinToString("")
}

/**
 * One character as a bit.
 *
 * Anything that is not `0` or `1` is a bad string, and it is refused here rather
 * than quietly read as a zero — which is what `Character.digit` would do and how a
 * typo becomes a plausible wrong answer.
 */
fun bitAt(bits: String, index: Int): Int {
    require(index in bits.indices) { "Bit $index is outside '$bits'." }
    return when (bits[index]) {
        '0' -> 0
        '1' -> 1
        else -> throw IllegalArgumentException("'${bits[index]}' is not a bit.")
    }
}

/** The four rows of the XOR truth table, in the order every textbook prints them. */
val xorTruthTable: List<Triple<Int, Int, Int>> = listOf(
    Triple(0, 0, xorBit(0, 0)),
    Triple(0, 1, xorBit(0, 1)),
    Triple(1, 0, xorBit(1, 0)),
    Triple(1, 1, xorBit(1, 1)),
)
