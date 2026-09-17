package com.algorithms.algoking.engine.dataset

import com.algorithms.algoking.engine.core.Aes
import com.algorithms.algoking.engine.core.AesProblem
import com.algorithms.algoking.engine.core.AesQuestion
import com.algorithms.algoking.engine.core.AesTransformation
import com.algorithms.algoking.engine.core.AesVariant
import com.algorithms.algoking.engine.core.Dataset

/**
 * The blocks the AES lesson encrypts, and the judgements it asks about them.
 *
 * **No ciphertext, State or round key is authored anywhere in this file.** Every
 * byte on screen is computed by [Aes.encryptBlock] from the plaintext and key
 * below, which is ADR-045's rule for Fibonacci's call counts and ADR-048's for
 * SHA-256's digests applied to the one lesson whose entire picture is intermediate
 * values. A hardcoded State would go quietly wrong the first time a dataset moved,
 * and in a lesson about a real cipher that is the one mistake that cannot be
 * tolerated.
 *
 * ### About the keys
 *
 * Both keys here are **published test vectors from FIPS-197**, the standard that
 * defines AES. They are the opposite of secrets — they exist so that independent
 * implementations can be checked against each other, and `AesEncryptionTest` checks
 * this one against NIST's published intermediate States and against the JDK's own
 * AES. Nothing in AlgoKing generates, stores or transmits a key, and the lesson
 * says plainly that a real system does not put one in its source.
 */
object AesDatasets {

    /**
     * The six exercises, in the order TRY asks them.
     *
     * They walk outward from the numbers that never change (the block, the State)
     * through the shape of a round, to the thing that makes a round the last one,
     * and finish on where the round keys came from. WATCH carries the same list so
     * that the two stages provably ask about the same cipher — but it narrates none
     * of them, because WATCH is the visual lesson and TRY is where they are asked.
     */
    val EXERCISES: List<AesQuestion> = buildList {
        // 1 and 2 — the two sizes, and the only two answers that are the same in
        // every variant.
        add(AesQuestion.BlockSize)
        add(AesQuestion.StateSize)
        // 3 — a normal round, rebuilt one transformation at a time.
        AesTransformation.NORMAL_ROUND.indices.forEach {
            add(AesQuestion.NextTransformation(it))
        }
        // 4 — and the one the last round leaves out.
        add(AesQuestion.SkippedInFinalRound)
        // 5 — the variants, all three, so the round count is never the only
        // plausible number on the list.
        AesVariant.entries.forEach { add(AesQuestion.RoundCount(it)) }
        // 6 — where the round keys came from.
        add(AesQuestion.KeyExpansion)
    }

    /**
     * WATCH — sixteen readable characters, and the FIPS-197 Appendix C.1 key.
     *
     * Three properties earn the plaintext its place:
     *
     *  - **it is exactly one block.** Sixteen characters is 16 bytes, so "AES works
     *    on one 128-bit block" is something the learner counts rather than something
     *    the copy asserts — and the lesson never has to explain padding, which is a
     *    second idea stacked on the first;
     *  - **it is text.** The first stage of the pipeline says *Plaintext*, and a
     *    readable one makes "this is a message, and it is about to stop looking like
     *    one" a picture rather than a claim. Its bytes are ASCII, so every one of
     *    them is a printable character before round 1 and none of them is after;
     *  - **it is not the TRY block.** The two stages share no byte of input and no
     *    byte of key, so nothing in TRY can be answered from a remembered State.
     *
     * AES-128 because ten rounds is the shortest of the three and the round counter
     * on screen reads `Round 3 / 10` — the variant the lesson runs is stated, and
     * the other two get their own beat rather than being run.
     */
    val watch = Dataset(
        values = emptyList(),
        label = "watch",
        aes = AesProblem(
            variant = AesVariant.AES_128,
            block = Aes.bytesOfText(WATCH_PLAINTEXT),
            key = Aes.bytesOf("000102030405060708090a0b0c0d0e0f"),
            plaintextLabel = WATCH_PLAINTEXT,
            questions = EXERCISES,
        ),
    )

    /**
     * TRY — **FIPS-197 Appendix B**, the worked example the standard itself walks
     * through.
     *
     * A different block and a different key, so TRY is application rather than
     * recall (ADR-014): not one of the sixteen input bytes and not one of the
     * sixteen key bytes is shared with WATCH, so every State on screen is new.
     *
     * It is also the most-checked sixteen bytes in cryptography. Using it here means
     * the lesson a learner drives is one they can verify against the standard, and
     * `AesEncryptionTest` asserts its ciphertext is the `3925841d…` the document
     * publishes.
     *
     * What does **not** change is the question list. There is one AES, so a second
     * dataset is a different place to stand inside it rather than a new problem —
     * the position ADR-045 reached for Fibonacci and ADR-048 for SHA-256. What makes
     * TRY application is that WATCH narrates none of these six and asks none of
     * them; it shows the run, and TRY is where the run has to be explained back.
     */
    val tryIt = Dataset(
        values = emptyList(),
        label = "try",
        aes = AesProblem(
            variant = AesVariant.AES_128,
            block = Aes.bytesOf("3243f6a8885a308d313198a2e0370734"),
            key = Aes.bytesOf("2b7e151628aed2a6abf7158809cf4f3c"),
            questions = EXERCISES,
        ),
    )
}

/** Sixteen characters, which is exactly one AES block. */
private const val WATCH_PLAINTEXT = "AlgoKing Rules!!"
