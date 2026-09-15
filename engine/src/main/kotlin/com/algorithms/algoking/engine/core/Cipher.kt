package com.algorithms.algoking.engine.core

/**
 * A message and the number of places to shift it by.
 *
 * Optional and defaulted on [Dataset], the additive move `graph`, `tree` and
 * `knapsack` made before it, so no existing lesson changed when it arrived.
 *
 * [shift] is **normalised at construction**, which is the whole reason this is a
 * type rather than two loose fields: a shift of 29 and a shift of 3 are the same
 * shift, and an engine that has to remember to reduce it is an engine that will
 * eventually forget. `-1` normalises to 25, so a backwards shift is expressible
 * and still lands inside the alphabet.
 */
data class CipherProblem(val plaintext: String, private val rawShift: Int) {

    /** Always `0..25`. `29 -> 3`, `-1 -> 25`, `26 -> 0`. */
    val shift: Int = ((rawShift % ALPHABET_SIZE) + ALPHABET_SIZE) % ALPHABET_SIZE

    init {
        require(plaintext.isNotEmpty()) { "A cipher lesson needs something to encrypt." }
    }

    /** How many characters the learner will actually be asked about. */
    val letterCount: Int get() = plaintext.count { it.isLetter() }

    /** The answer, in full. The one place a ciphertext is produced. */
    val ciphertext: String get() = caesarEncrypt(plaintext, shift)

    companion object {
        const val ALPHABET_SIZE: Int = 26
    }
}

/**
 * The canonical Caesar transform — **the only implementation in the codebase**.
 *
 * ```
 * encrypted = (index + shift) mod 26
 * decrypted = (index - shift + 26) mod 26
 * ```
 *
 * Case is preserved and non-alphabetic characters are passed through untouched: a
 * space is not a letter, so shifting it would be inventing a rule the cipher does
 * not have. The projector, the walkthrough, the decision options and the tests all
 * read this or [shiftLetter]; a second expression anywhere is how the picture and
 * the algorithm start disagreeing (the rule ARCHITECTURE.md §4.4 sets for Binary
 * Search's midpoint).
 */
fun caesarEncrypt(text: String, shift: Int): String =
    text.map { shiftLetter(it, shift) }.joinToString("")

/** [caesarEncrypt] run backwards, which is all decryption is. */
fun caesarDecrypt(text: String, shift: Int): String =
    caesarEncrypt(text, -shift)

/**
 * One character, shifted. Non-letters come back unchanged.
 *
 * The `+ 26` before the second `mod` is not decoration: Kotlin's `%` keeps the
 * sign of its left operand, so a negative shift without it produces a negative
 * index and an exception two lines later.
 */
fun shiftLetter(char: Char, shift: Int): Char {
    if (!char.isLetter()) return char
    val base = if (char.isUpperCase()) 'A' else 'a'
    val index = char - base
    val moved = ((index + shift) % CipherProblem.ALPHABET_SIZE +
        CipherProblem.ALPHABET_SIZE) % CipherProblem.ALPHABET_SIZE
    return base + moved
}

/** `'C' -> 2`, case-insensitively. `-1` for anything that is not a letter. */
fun alphabetIndexOf(char: Char): Int =
    if (char.isLetter()) char.uppercaseChar() - 'A' else -1

/** `2 -> 'C'`. Wraps, so callers never have to reduce first. */
fun letterAt(index: Int): Char {
    val wrapped = ((index % CipherProblem.ALPHABET_SIZE) +
        CipherProblem.ALPHABET_SIZE) % CipherProblem.ALPHABET_SIZE
    return 'A' + wrapped
}
