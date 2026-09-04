package com.ttele.algoking.engine.progress

import com.ttele.algoking.engine.core.AlgorithmId

/**
 * The three learning stages — PRODUCT_SPEC.md §2.
 *
 * There is no fourth. Mastery is the *result* of finishing [CHALLENGE], not a place
 * the learner goes.
 */
enum class Stage { WATCH, TRY, CHALLENGE }

/**
 * How far one algorithm has been learned.
 *
 * The three booleans are the **only** stored state. [percent] is derived from them
 * on every read, which is what makes it impossible for a stored number to drift out
 * of step with the stages it is supposed to summarise.
 */
data class AlgorithmProgress(
    val watchCompleted: Boolean = false,
    val tryCompleted: Boolean = false,
    val challengeCompleted: Boolean = false,
) {
    val completedStages: Int
        get() = listOf(watchCompleted, tryCompleted, challengeCompleted).count { it }

    /**
     * 0 / 33 / 66 / 100.
     *
     * Counted rather than ordered on purpose: whatever order the stages are
     * completed in, two of three finished is always two thirds of the way, and the
     * number can never disagree with the flags.
     */
    val percent: Int
        get() = when (completedStages) {
            0 -> 0
            1 -> 33
            2 -> 66
            else -> 100
        }

    val fraction: Float get() = percent / 100f

    val started: Boolean get() = completedStages > 0

    /** True only once all three are done. Practising again never changes it. */
    val mastered: Boolean get() = completedStages == Stage.entries.size

    /** Where the learner should be taken next. Null once there is nothing left. */
    val nextStage: Stage?
        get() = when {
            !watchCompleted -> Stage.WATCH
            !tryCompleted -> Stage.TRY
            !challengeCompleted -> Stage.CHALLENGE
            else -> null
        }

    fun isComplete(stage: Stage): Boolean = when (stage) {
        Stage.WATCH -> watchCompleted
        Stage.TRY -> tryCompleted
        Stage.CHALLENGE -> challengeCompleted
    }

    /**
     * Records a stage as finished.
     *
     * **Only ever sets a flag, never clears one.** Retrying a stage, failing a
     * challenge, or practising a mastered algorithm again therefore cannot take
     * progress away — which is the whole reason completion is stored as three
     * latches rather than as a position in a sequence.
     */
    fun complete(stage: Stage): AlgorithmProgress = when (stage) {
        Stage.WATCH -> copy(watchCompleted = true)
        Stage.TRY -> copy(tryCompleted = true)
        Stage.CHALLENGE -> copy(challengeCompleted = true)
    }

    companion object {
        val NONE = AlgorithmProgress()
    }
}

/**
 * Every algorithm's progress, in one value.
 *
 * Each algorithm is independent — there is no global percentage anywhere in the
 * app — and an algorithm that has never been opened simply has no entry, which
 * reads as [AlgorithmProgress.NONE].
 */
@JvmInline
value class LearningProgress(private val byAlgorithm: Map<AlgorithmId, AlgorithmProgress>) {

    operator fun get(id: AlgorithmId): AlgorithmProgress =
        byAlgorithm[id] ?: AlgorithmProgress.NONE

    fun complete(id: AlgorithmId, stage: Stage): LearningProgress =
        LearningProgress(byAlgorithm + (id to get(id).complete(stage)))

    /** Only what has actually been completed, so nothing empty is ever written. */
    fun entries(): Map<AlgorithmId, AlgorithmProgress> =
        byAlgorithm.filterValues { it.started }

    companion object {
        val EMPTY = LearningProgress(emptyMap())
    }
}

/**
 * How progress is written down — one flat key per completed stage.
 *
 * Kept here, beside the model and away from Android, so the persistence *rules* can
 * be tested without a device: a key that no longer maps to a known algorithm or
 * stage is ignored rather than crashing, and encode/decode round-trips exactly.
 */
object ProgressCodec {

    /** e.g. `BINARY_SEARCH:WATCH`. */
    fun encode(progress: LearningProgress): Set<String> = buildSet {
        for ((id, algorithm) in progress.entries()) {
            for (stage in Stage.entries) {
                if (algorithm.isComplete(stage)) add("${id.name}:${stage.name}")
            }
        }
    }

    fun decode(keys: Set<String>): LearningProgress {
        var progress = LearningProgress.EMPTY
        for (key in keys) {
            val id = enumOrNull<AlgorithmId>(key.substringBefore(':')) ?: continue
            val stage = enumOrNull<Stage>(key.substringAfter(':', "")) ?: continue
            progress = progress.complete(id, stage)
        }
        return progress
    }

    /**
     * An unknown name is a renamed or removed algorithm from an older install, not
     * a bug. Dropping the key silently is the only behaviour that lets someone
     * update the app without losing the progress that *is* still valid.
     */
    private inline fun <reified E : Enum<E>> enumOrNull(name: String): E? =
        enumValues<E>().firstOrNull { it.name == name }
}
