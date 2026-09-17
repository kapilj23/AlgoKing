package com.algorithms.algoking.engine.core

/**
 * AES — the Advanced Encryption Standard, as far as this lesson draws it.
 *
 * ### Why this one *is* implemented, when SHA-256 is not
 *
 * [Sha256] delegates to `MessageDigest` because its internals are explicitly not
 * that lesson: the box is labelled `64 compression rounds` and stays a box
 * (ADR-048). AES is the opposite case. **The picture of this lesson is the State
 * matrix changing**, so every byte drawn has to be the byte AES really produces —
 * and a platform `Cipher` hands back a finished ciphertext with no way to ask what
 * the State looked like after ShiftRows in round 3.
 *
 * ADR-048's rule was *never fake the steps of a real algorithm*. Honouring it here
 * means computing them, so [encryptBlock] runs the real transformations and records
 * the real State after each one. Nothing in this lesson is illustrative.
 *
 * What keeps that honest rather than merely confident is the verification:
 *
 *  - the S-box is the published table **and** a test regenerates it from its
 *    mathematical definition (multiplicative inverse in GF(2⁸), then the affine
 *    transform) and compares byte for byte;
 *  - the full ten-round encryption is checked against **FIPS-197 Appendix C.1**,
 *    the worked example NIST publishes, including its round-by-round States;
 *  - and independently against the **JDK's own AES**, so the engine agrees with
 *    something outside itself.
 *
 * ### What this is not
 *
 * It is a single-block primitive and the lesson says so. A block cipher on its own
 * is not a way to encrypt a message — that needs a mode of operation, and a real
 * system wants an authenticated one such as **AES-GCM**. ECB is named in the copy
 * only as the thing not to reach for. Nothing here generates, stores or hardcodes a
 * key for any purpose but drawing the lesson.
 */
object Aes {

    /** AES operates on a fixed 128-bit block, in every variant. */
    const val BLOCK_BITS: Int = 128

    /** The same size, in bytes — and the number of cells in the State. */
    const val BLOCK_BYTES: Int = BLOCK_BITS / 8

    /** The State is square: four rows… */
    const val STATE_ROWS: Int = 4

    /** …and four columns, each one a 32-bit word. */
    const val STATE_COLUMNS: Int = 4

    /** Bytes per word, which is also [STATE_ROWS]. */
    private const val WORD_BYTES = 4

    /**
     * Where byte [index] of the block sits in the State.
     *
     * AES fills the State **column by column**: `s[r][c] = in[r + 4c]`. So the flat
     * index used throughout this file is `row + 4 * column`, which puts each column
     * in a contiguous run of four — convenient, because MixColumns works on exactly
     * that run.
     */
    fun rowOf(index: Int): Int = index % STATE_ROWS

    /** The column byte [index] sits in. See [rowOf]. */
    fun columnOf(index: Int): Int = index / STATE_ROWS

    /** The flat index of the byte at [row], [column]. */
    fun indexOf(row: Int, column: Int): Int = row + STATE_ROWS * column

    // ── The published S-box ───────────────────────────────────────────────────

    /**
     * The AES substitution table — FIPS-197 Figure 7.
     *
     * Written out rather than generated, because the table is the artefact every
     * implementation and every reference shares. `AesTest` regenerates it from the
     * definition and asserts it matches, so a mistyped byte fails the build rather
     * than quietly producing a cipher that is not AES.
     */
    val SBOX: IntArray = intArrayOf(
        0x63, 0x7c, 0x77, 0x7b, 0xf2, 0x6b, 0x6f, 0xc5,
        0x30, 0x01, 0x67, 0x2b, 0xfe, 0xd7, 0xab, 0x76,
        0xca, 0x82, 0xc9, 0x7d, 0xfa, 0x59, 0x47, 0xf0,
        0xad, 0xd4, 0xa2, 0xaf, 0x9c, 0xa4, 0x72, 0xc0,
        0xb7, 0xfd, 0x93, 0x26, 0x36, 0x3f, 0xf7, 0xcc,
        0x34, 0xa5, 0xe5, 0xf1, 0x71, 0xd8, 0x31, 0x15,
        0x04, 0xc7, 0x23, 0xc3, 0x18, 0x96, 0x05, 0x9a,
        0x07, 0x12, 0x80, 0xe2, 0xeb, 0x27, 0xb2, 0x75,
        0x09, 0x83, 0x2c, 0x1a, 0x1b, 0x6e, 0x5a, 0xa0,
        0x52, 0x3b, 0xd6, 0xb3, 0x29, 0xe3, 0x2f, 0x84,
        0x53, 0xd1, 0x00, 0xed, 0x20, 0xfc, 0xb1, 0x5b,
        0x6a, 0xcb, 0xbe, 0x39, 0x4a, 0x4c, 0x58, 0xcf,
        0xd0, 0xef, 0xaa, 0xfb, 0x43, 0x4d, 0x33, 0x85,
        0x45, 0xf9, 0x02, 0x7f, 0x50, 0x3c, 0x9f, 0xa8,
        0x51, 0xa3, 0x40, 0x8f, 0x92, 0x9d, 0x38, 0xf5,
        0xbc, 0xb6, 0xda, 0x21, 0x10, 0xff, 0xf3, 0xd2,
        0xcd, 0x0c, 0x13, 0xec, 0x5f, 0x97, 0x44, 0x17,
        0xc4, 0xa7, 0x7e, 0x3d, 0x64, 0x5d, 0x19, 0x73,
        0x60, 0x81, 0x4f, 0xdc, 0x22, 0x2a, 0x90, 0x88,
        0x46, 0xee, 0xb8, 0x14, 0xde, 0x5e, 0x0b, 0xdb,
        0xe0, 0x32, 0x3a, 0x0a, 0x49, 0x06, 0x24, 0x5c,
        0xc2, 0xd3, 0xac, 0x62, 0x91, 0x95, 0xe4, 0x79,
        0xe7, 0xc8, 0x37, 0x6d, 0x8d, 0xd5, 0x4e, 0xa9,
        0x6c, 0x56, 0xf4, 0xea, 0x65, 0x7a, 0xae, 0x08,
        0xba, 0x78, 0x25, 0x2e, 0x1c, 0xa6, 0xb4, 0xc6,
        0xe8, 0xdd, 0x74, 0x1f, 0x4b, 0xbd, 0x8b, 0x8a,
        0x70, 0x3e, 0xb5, 0x66, 0x48, 0x03, 0xf6, 0x0e,
        0x61, 0x35, 0x57, 0xb9, 0x86, 0xc1, 0x1d, 0x9e,
        0xe1, 0xf8, 0x98, 0x11, 0x69, 0xd9, 0x8e, 0x94,
        0x9b, 0x1e, 0x87, 0xe9, 0xce, 0x55, 0x28, 0xdf,
        0x8c, 0xa1, 0x89, 0x0d, 0xbf, 0xe6, 0x42, 0x68,
        0x41, 0x99, 0x2d, 0x0f, 0xb0, 0x54, 0xbb, 0x16,
    )

    // ── The four transformations ──────────────────────────────────────────────

    /**
     * **SubBytes** — every byte replaced by its S-box entry.
     *
     * Position is untouched; only the values change. That is what makes it the one
     * transformation whose picture is "the whole grid changed and nothing moved".
     */
    fun subBytes(state: IntArray): IntArray =
        IntArray(BLOCK_BYTES) { SBOX[state[it] and 0xFF] }

    /**
     * **ShiftRows** — row `r` rotates left by `r`.
     *
     * Row 0 does not move at all, which is worth seeing: a learner who expects all
     * four rows to shift is watching for the wrong thing. Values are untouched;
     * only position changes — the exact inverse of SubBytes, and the reason the two
     * read so differently on screen.
     */
    fun shiftRows(state: IntArray): IntArray {
        val out = IntArray(BLOCK_BYTES)
        for (row in 0 until STATE_ROWS) {
            for (column in 0 until STATE_COLUMNS) {
                val from = (column + row) % STATE_COLUMNS
                out[indexOf(row, column)] = state[indexOf(row, from)]
            }
        }
        return out
    }

    /**
     * **MixColumns** — each column replaced by a mixture of its own four bytes.
     *
     * This is the one step with arithmetic a learner cannot do in their head, and
     * the lesson does not ask them to (PRODUCT_SPEC.md §3): it is multiplication in
     * GF(2⁸), the field AES is defined over. What matters to the lesson is *what it
     * achieves* — every byte of a column now depends on all four, so a change
     * spreads sideways — and that the final round leaves it out.
     */
    fun mixColumns(state: IntArray): IntArray {
        val out = IntArray(BLOCK_BYTES)
        for (column in 0 until STATE_COLUMNS) {
            val base = column * STATE_ROWS
            val a0 = state[base] and 0xFF
            val a1 = state[base + 1] and 0xFF
            val a2 = state[base + 2] and 0xFF
            val a3 = state[base + 3] and 0xFF
            out[base] = mul(a0, 2) xor mul(a1, 3) xor a2 xor a3
            out[base + 1] = a0 xor mul(a1, 2) xor mul(a2, 3) xor a3
            out[base + 2] = a0 xor a1 xor mul(a2, 2) xor mul(a3, 3)
            out[base + 3] = mul(a0, 3) xor a1 xor a2 xor mul(a3, 2)
        }
        return out
    }

    /**
     * **AddRoundKey** — the State XORed with this round's key.
     *
     * The only step that involves the key at all, which is exactly why it happens
     * once before the rounds begin as well as inside every one of them: a round
     * that ended without it would be a fixed, keyless scramble anyone could undo.
     */
    fun addRoundKey(state: IntArray, roundKey: IntArray): IntArray {
        require(roundKey.size == BLOCK_BYTES) {
            "A round key is one block: $BLOCK_BYTES bytes, not ${roundKey.size}."
        }
        return IntArray(BLOCK_BYTES) { (state[it] xor roundKey[it]) and 0xFF }
    }

    /** Multiply in GF(2⁸) by 2 — `xtime`, reducing by the AES polynomial. */
    private fun xtime(byte: Int): Int {
        val shifted = (byte shl 1) and 0xFF
        return if (byte and 0x80 != 0) shifted xor 0x1B else shifted
    }

    /** Multiply in GF(2⁸) by [factor], which here is only ever 1, 2 or 3. */
    private fun mul(byte: Int, factor: Int): Int = when (factor) {
        1 -> byte
        2 -> xtime(byte)
        3 -> xtime(byte) xor byte
        else -> error("MixColumns only multiplies by 1, 2 or 3.")
    }

    // ── Key expansion ─────────────────────────────────────────────────────────

    /**
     * **Key Expansion** — one key in, `rounds + 1` round keys out.
     *
     * The initial AddRoundKey uses round key 0 and each round uses the next, which
     * is why there is always one more round key than there are rounds. The learner
     * is never asked to run this (PRODUCT_SPEC.md §3); they are asked to know that
     * it is what produces the round keys, which is the thing that transfers.
     */
    fun expandKey(key: IntArray, variant: AesVariant): List<IntArray> {
        require(key.size == variant.keyBytes) {
            "${variant.label} takes a ${variant.keyBytes}-byte key, not ${key.size}."
        }

        val nk = variant.keyWords
        val totalWords = STATE_COLUMNS * (variant.rounds + 1)
        val words = ArrayList<IntArray>(totalWords)

        for (i in 0 until nk) {
            words += IntArray(WORD_BYTES) { key[WORD_BYTES * i + it] and 0xFF }
        }

        for (i in nk until totalWords) {
            var temp = words[i - 1].copyOf()
            when {
                i % nk == 0 -> {
                    temp = subWord(rotWord(temp))
                    temp[0] = temp[0] xor RCON[i / nk]
                }
                // AES-256 only: an extra substitution a third of the way through
                // each key's worth of words.
                nk > 6 && i % nk == 4 -> temp = subWord(temp)
            }
            val previous = words[i - nk]
            words += IntArray(WORD_BYTES) { (previous[it] xor temp[it]) and 0xFF }
        }

        // Four words make one round key, laid out in the State's own column-major
        // order so `addRoundKey` can XOR it position for position.
        return (0..variant.rounds).map { round ->
            IntArray(BLOCK_BYTES) { index ->
                words[STATE_COLUMNS * round + columnOf(index)][rowOf(index)]
            }
        }
    }

    private fun rotWord(word: IntArray): IntArray =
        IntArray(WORD_BYTES) { word[(it + 1) % WORD_BYTES] }

    private fun subWord(word: IntArray): IntArray =
        IntArray(WORD_BYTES) { SBOX[word[it] and 0xFF] }

    /**
     * Round constants — `x^(i-1)` in GF(2⁸), and index 0 is never read.
     *
     * Fourteen entries covers AES-256's key schedule, which is the longest of the
     * three.
     */
    private val RCON = intArrayOf(
        0x00, 0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40,
        0x80, 0x1B, 0x36, 0x6C, 0xD8, 0xAB, 0x4D,
    )

    // ── The run the lesson draws ──────────────────────────────────────────────

    /**
     * Encrypt one block, keeping the State after **every** transformation.
     *
     * This is what makes the lesson's picture real rather than illustrative. The
     * returned steps are the whole run in order — the block arriving, the State
     * being formed, the key expanded, the initial AddRoundKey, every round's
     * transformations, and the ciphertext — so the walkthrough and TRY both read
     * one sequence rather than each deciding for themselves what AES does.
     */
    fun encryptBlock(block: IntArray, key: IntArray, variant: AesVariant): List<AesStep> {
        require(block.size == BLOCK_BYTES) {
            "AES encrypts one $BLOCK_BYTES-byte block, not ${block.size}."
        }
        val roundKeys = expandKey(key, variant)
        val steps = mutableListOf<AesStep>()

        val plain = IntArray(BLOCK_BYTES) { block[it] and 0xFF }
        steps += AesStep(AesStepKind.PLAINTEXT, plain, plain)
        steps += AesStep(AesStepKind.BLOCK, plain, plain)
        steps += AesStep(AesStepKind.STATE, plain, plain)
        steps += AesStep(AesStepKind.KEY_EXPANSION, plain, plain)

        var state = addRoundKey(plain, roundKeys[0])
        steps += AesStep(AesStepKind.INITIAL_ADD_ROUND_KEY, plain, state, roundKey = 0)

        for (round in 1..variant.rounds) {
            // The final round leaves MixColumns out, and that omission is the
            // single most asked-about thing in AES. It is expressed once, here, as
            // the shape of the round rather than as a branch inside it.
            for (transformation in variant.transformationsIn(round)) {
                val before = state
                state = when (transformation) {
                    AesTransformation.SUB_BYTES -> subBytes(before)
                    AesTransformation.SHIFT_ROWS -> shiftRows(before)
                    AesTransformation.MIX_COLUMNS -> mixColumns(before)
                    AesTransformation.ADD_ROUND_KEY -> addRoundKey(before, roundKeys[round])
                }
                steps += AesStep(
                    kind = AesStepKind.ROUND_TRANSFORM,
                    before = before,
                    after = state,
                    round = round,
                    transformation = transformation,
                    roundKey = round.takeIf { transformation == AesTransformation.ADD_ROUND_KEY },
                )
            }
        }

        steps += AesStep(AesStepKind.CIPHERTEXT, state, state)
        // Two closing concepts the pipeline itself cannot show: that the variant
        // decides the round count, and that decryption runs the inverses.
        steps += AesStep(AesStepKind.VARIANTS, state, state)
        steps += AesStep(AesStepKind.DECRYPTION, state, state)
        // The hand-over. See [AesStepKind.READY] for why the run ends on a frame
        // that does nothing.
        steps += AesStep(AesStepKind.READY, state, state)
        return steps
    }

    /** The finished ciphertext for this block — the last State the run produces. */
    fun ciphertext(block: IntArray, key: IntArray, variant: AesVariant): IntArray =
        encryptBlock(block, key, variant).last { it.kind == AesStepKind.CIPHERTEXT }.after

    // ── Reading bytes ─────────────────────────────────────────────────────────

    /** One byte as two lowercase hex characters — how every byte is shown. */
    fun hexByte(value: Int): String {
        val byte = value and 0xFF
        return "${HEX_DIGITS[byte ushr 4]}${HEX_DIGITS[byte and 0x0F]}"
    }

    /** A run of bytes as hex, with no separator. */
    fun hex(bytes: IntArray): String = bytes.joinToString("") { hexByte(it) }

    /**
     * Parse a hex string into bytes.
     *
     * Used only where a block or a key is *authored*, so it validates loudly: a
     * dataset with an odd number of characters is a typo, and finding that out
     * halfway through a run is an index error rather than a teachable state — the
     * rule `XorProblem` and `CipherProblem` already follow.
     */
    fun bytesOf(hex: String): IntArray {
        val cleaned = hex.filterNot { it.isWhitespace() }
        require(cleaned.isNotEmpty()) { "There are no bytes in an empty string." }
        require(cleaned.length % 2 == 0) {
            "A byte is two hex characters, so ${cleaned.length} of them is not a whole number."
        }
        require(cleaned.all { it in HEX_ALPHABET }) {
            "Only hexadecimal digits make a byte: “$hex”."
        }
        return IntArray(cleaned.length / 2) {
            cleaned.substring(it * 2, it * 2 + 2).toInt(16)
        }
    }

    /**
     * The bytes of [text], read as **UTF-8**.
     *
     * Stated rather than left to a platform default, for the reason [Sha256.hex]
     * states it: the same string under two encodings is two different blocks, and a
     * lesson whose picture is those exact bytes cannot afford to be
     * locale-dependent.
     */
    fun bytesOfText(text: String): IntArray {
        val bytes = text.toByteArray(Charsets.UTF_8)
        return IntArray(bytes.size) { bytes[it].toInt() and 0xFF }
    }

    /** Which positions of two equal-length blocks hold different bytes. */
    fun differingPositions(a: IntArray, b: IntArray): Set<Int> {
        require(a.size == b.size) {
            "Two States are always the same size: ${a.size} against ${b.size}."
        }
        return a.indices.filterTo(mutableSetOf()) { (a[it] and 0xFF) != (b[it] and 0xFF) }
    }

    private const val HEX_ALPHABET = "0123456789abcdefABCDEF"
    private val HEX_DIGITS = "0123456789abcdef".toCharArray()
}

/**
 * The three AES variants.
 *
 * **The block never changes** — it is 128 bits in all three, and that is the single
 * most common thing said wrongly about AES. What the number in the name refers to
 * is the *key*, and a longer key buys more rounds.
 */
enum class AesVariant(
    val label: String,
    /** The key size in bits — the number in the name. */
    val keyBits: Int,
    /** How many rounds this variant runs. */
    val rounds: Int,
) {
    AES_128("AES-128", 128, 10),
    AES_192("AES-192", 192, 12),
    AES_256("AES-256", 256, 14),
    ;

    /** The key size in bytes. */
    val keyBytes: Int get() = keyBits / 8

    /** The key size in 32-bit words — `Nk` in the specification. */
    val keyWords: Int get() = keyBytes / 4

    /** One more round key than there are rounds: round 0 is used before the first. */
    val roundKeys: Int get() = rounds + 1

    /**
     * What round [round] is made of.
     *
     * Every round but the last is the full four. **The last leaves MixColumns
     * out** — not as a special case bolted on, but because mixing the final State
     * would add nothing a decrypting party could not immediately undo, so the
     * standard does not spend it.
     */
    fun transformationsIn(round: Int): List<AesTransformation> =
        if (round == rounds) AesTransformation.FINAL_ROUND else AesTransformation.NORMAL_ROUND

    /** True when [round] is this variant's last. */
    fun isFinalRound(round: Int): Boolean = round == rounds

    companion object {
        /** The variant a lesson teaches on unless its dataset says otherwise. */
        val DEFAULT: AesVariant = AES_128
    }
}

/**
 * The four transformations a round is built from, in the order a round applies
 * them.
 *
 * Declaration order **is** the round order, and [NORMAL_ROUND] is the enum's own
 * entries — so there is no second list to keep in step with this one, and no way
 * for the lesson to teach an order the engine does not run.
 */
enum class AesTransformation(val label: String, val shortLabel: String) {
    /** Every byte replaced through the S-box. Values change, positions do not. */
    SUB_BYTES("SubBytes", "SUB"),

    /** Row `r` rotates left by `r`. Positions change, values do not. */
    SHIFT_ROWS("ShiftRows", "SHIFT"),

    /** Each column mixed into itself, so a change spreads across all four bytes. */
    MIX_COLUMNS("MixColumns", "MIX"),

    /** The State XORed with the round key. The only step the key touches. */
    ADD_ROUND_KEY("AddRoundKey", "ADDKEY"),
    ;

    companion object {
        /** `SubBytes → ShiftRows → MixColumns → AddRoundKey`. */
        val NORMAL_ROUND: List<AesTransformation> = entries.toList()

        /** The same, without [MIX_COLUMNS] — the final round. */
        val FINAL_ROUND: List<AesTransformation> = entries.filterNot { it == MIX_COLUMNS }

        /** The one the final round leaves out. */
        val SKIPPED_IN_FINAL_ROUND: AesTransformation = MIX_COLUMNS
    }
}

/** What a step of the run is. */
enum class AesStepKind {
    /** The message, before it is anything else. */
    PLAINTEXT,

    /** Sixteen bytes of it — one block. */
    BLOCK,

    /** Those sixteen bytes arranged as the 4 × 4 State. */
    STATE,

    /** The key turned into one round key per round, plus one. */
    KEY_EXPANSION,

    /** The AddRoundKey that happens **before** round 1. */
    INITIAL_ADD_ROUND_KEY,

    /** One transformation inside one round. */
    ROUND_TRANSFORM,

    /** The State as it leaves the last round. */
    CIPHERTEXT,

    /** The three variants and the rounds each runs. */
    VARIANTS,

    /** That decryption is the inverse transformations, with the same key. */
    DECRYPTION,

    /**
     * The run is over, and the judgements begin. Nothing happens here.
     *
     * ### Why an inert step earns its place
     *
     * A frame is drawn from the state *after* a transition, so the frame that
     * applies the last real step is also the frame on which the first question is
     * pending — the two are the same state, and no amount of deriving can tell them
     * apart. Without this step the closing beat about decryption would be drawn
     * with the first question's evidence over it, which is the bug ADR-047 records
     * for XOR's final frame and ADR-048 for SHA-256's whole run.
     *
     * So the collision is given a frame of its own, with nothing on it to collide.
     * WATCH narrates no beat here; TRY asks its first question over a neutral
     * picture of the finished ciphertext.
     */
    READY,
}

/**
 * One step of a real AES run, with the State on both sides of it.
 *
 * [before] and [after] are what let the picture mark exactly which bytes a
 * transformation touched — computed rather than described, so ShiftRows moving
 * twelve bytes and row 0 staying put is something the learner sees rather than
 * something the copy claims.
 */
data class AesStep(
    val kind: AesStepKind,
    val before: IntArray,
    val after: IntArray,
    /** Which round this belongs to, for [AesStepKind.ROUND_TRANSFORM]. */
    val round: Int? = null,
    val transformation: AesTransformation? = null,
    /** Which round key was used, for the steps that use one. */
    val roundKey: Int? = null,
) {
    /** Positions whose byte this step changed. */
    val changed: Set<Int> get() = Aes.differingPositions(before, after)

    // IntArray is an array, so the generated equals/hashCode would compare
    // references. Two steps are the same step when they say the same thing.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AesStep) return false
        return kind == other.kind &&
            before.contentEquals(other.before) &&
            after.contentEquals(other.after) &&
            round == other.round &&
            transformation == other.transformation &&
            roundKey == other.roundKey
    }

    override fun hashCode(): Int {
        var result = kind.hashCode()
        result = 31 * result + before.contentHashCode()
        result = 31 * result + after.contentHashCode()
        result = 31 * result + (round ?: 0)
        result = 31 * result + (transformation?.hashCode() ?: 0)
        result = 31 * result + (roundKey ?: 0)
        return result
    }
}

/**
 * The block a lesson encrypts, the key it uses, and the judgements it then asks.
 *
 * Optional and defaulted on [Dataset], the additive move `graph`, `tree`,
 * `knapsack`, `cipher`, `xor` and `hash` each made before it, so no existing lesson
 * changed when it arrived.
 *
 * **Everything is validated at construction**, the rule [XorProblem],
 * [CipherProblem] and [HashProblem] already follow: a block of the wrong length or
 * a key that does not match the variant is a typo in a dataset, and discovering it
 * halfway through a run is an index error rather than a teachable state.
 */
data class AesProblem(
    /** Which variant this lesson runs. The State is the same size in all three. */
    val variant: AesVariant,
    /** The sixteen bytes going in. */
    val block: IntArray,
    /** The key, of whatever length [variant] takes. */
    val key: IntArray,
    /**
     * What the plaintext block spells, when it spells anything.
     *
     * Null for a block of arbitrary bytes — a teaching block does not have to be
     * text, and pretending otherwise would put a mojibake string on screen.
     */
    val plaintextLabel: String? = null,
    /** The judgements the learner makes, in order. */
    val questions: List<AesQuestion>,
) {
    init {
        require(block.size == Aes.BLOCK_BYTES) {
            "AES encrypts one ${Aes.BLOCK_BYTES}-byte block, not ${block.size}."
        }
        require(key.size == variant.keyBytes) {
            "${variant.label} takes a ${variant.keyBytes}-byte key, not ${key.size}."
        }
        require(questions.isNotEmpty()) { "An AES lesson needs something to ask." }
        require(questions.size == questions.distinct().size) {
            "A question asked twice is the same question twice: $questions."
        }
    }

    /**
     * The whole run, computed. Nothing about it is authored.
     *
     * `by lazy` rather than `get()`: the projector, the probe and the narrator each
     * read it once per frame, and a lesson's problem is built once and never
     * copied. It is deliberately not a constructor parameter — a stored run would
     * be a second source of truth for the one thing the lesson is about, and could
     * disagree with the key it claims to come from.
     */
    val steps: List<AesStep> by lazy { Aes.encryptBlock(block, key, variant) }

    /** Every round key, computed. */
    val roundKeys: List<IntArray> by lazy { Aes.expandKey(key, variant) }

    /** The finished ciphertext. */
    val ciphertext: IntArray
        get() = steps.last { it.kind == AesStepKind.CIPHERTEXT }.after

    /** How many decisions the learner is asked to make. */
    val decisionCount: Int get() = questions.size

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AesProblem) return false
        return variant == other.variant &&
            block.contentEquals(other.block) &&
            key.contentEquals(other.key) &&
            plaintextLabel == other.plaintextLabel &&
            questions == other.questions
    }

    override fun hashCode(): Int {
        var result = variant.hashCode()
        result = 31 * result + block.contentHashCode()
        result = 31 * result + key.contentHashCode()
        result = 31 * result + (plaintextLabel?.hashCode() ?: 0)
        result = 31 * result + questions.hashCode()
        return result
    }
}

/**
 * One thing a learner has to get right about AES.
 *
 * These are **concept judgements and one ordering exercise, not arithmetic** — the
 * same call ADR-048 made for SHA-256, and for the same reason. Nobody runs a
 * MixColumns by hand, and a lesson that asked them to would be teaching a gesture
 * over GF(2⁸) multiplication the learner cannot check.
 *
 * What keeps it from being a quiz is **where the answer comes from**: every
 * question is asked once the run is on screen, and the projector shows the part of
 * the picture that answers it. [NextTransformation] in particular is answered by
 * tapping the round's own steps, which is the gesture the graph, counting and hash
 * lessons already use rather than a row of words.
 */
sealed interface AesQuestion {

    /** How large an AES block is. The answer that does not change between variants. */
    data object BlockSize : AesQuestion

    /** How many bytes the State holds — the same sixteen, counted as a grid. */
    data object StateSize : AesQuestion

    /**
     * Which transformation comes next in a normal round, at [position] of four.
     *
     * Four of these in a row rebuild `SubBytes → ShiftRows → MixColumns →
     * AddRoundKey`, which is the order the whole cipher is made of.
     */
    data class NextTransformation(val position: Int) : AesQuestion {
        init {
            require(position in AesTransformation.NORMAL_ROUND.indices) {
                "A normal round has ${AesTransformation.NORMAL_ROUND.size} steps, " +
                    "so there is no position $position."
            }
        }
    }

    /** Which transformation the final round leaves out. */
    data object SkippedInFinalRound : AesQuestion

    /** How many rounds [variant] runs. */
    data class RoundCount(val variant: AesVariant) : AesQuestion

    /** What produces the round keys. */
    data object KeyExpansion : AesQuestion
}
