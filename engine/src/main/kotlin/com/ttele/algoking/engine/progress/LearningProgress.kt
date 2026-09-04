package com.ttele.algoking.engine.progress

import com.ttele.algoking.engine.core.AlgorithmId

/**
 * The two learning stages — PRODUCT_SPEC.md §2.
 *
 * The MVP spine is **WATCH → TRY**, and an algorithm with both finished is
 * complete. CHALLENGE is deferred to V2 (`docs/v2-challenge.md`); when it lands
 * it becomes a third entry here and [AlgorithmProgress.percent] starts counting
 * in thirds again, because the percentage is derived rather than stored.
 */
enum class Stage { WATCH, TRY }

/**
 * How far one algorithm has been learned.
 *
 * The booleans are the **only** stored state. [percent] is derived from them on
 * every read, which is what makes it impossible for a stored number to drift out
 * of step with the stages it is supposed to summarise.
 */
data class AlgorithmProgress(
    val watchCompleted: Boolean = false,
    val tryCompleted: Boolean = false,
) {
    val completedStages: Int
        get() = listOf(watchCompleted, tryCompleted).count { it }

    /**
     * 0 / 50 / 100.
     *
     * Counted rather than ordered on purpose: whatever order the stages are
     * completed in, one of two finished is always half way, and the number can
     * never disagree with the flags.
     */
    val percent: Int
        get() = when (completedStages) {
            0 -> 0
            1 -> 50
            else -> 100
        }

    val fraction: Float get() = percent / 100f

    val started: Boolean get() = completedStages > 0

    /** True only once every stage is done. Practising again never changes it. */
    val finished: Boolean get() = completedStages == Stage.entries.size

    /** Where the learner should be taken next. Null once there is nothing left. */
    val nextStage: Stage?
        get() = when {
            !watchCompleted -> Stage.WATCH
            !tryCompleted -> Stage.TRY
            else -> null
        }

    fun isComplete(stage: Stage): Boolean = when (stage) {
        Stage.WATCH -> watchCompleted
        Stage.TRY -> tryCompleted
    }

    /**
     * Records a stage as finished.
     *
     * **Only ever sets a flag, never clears one.** Retrying a stage or running a
     * finished algorithm again therefore cannot take progress away — which is the
     * whole reason completion is stored as latches rather than as a position in a
     * sequence.
     */
    fun complete(stage: Stage): AlgorithmProgress = when (stage) {
        Stage.WATCH -> copy(watchCompleted = true)
        Stage.TRY -> copy(tryCompleted = true)
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
 * Kept here, beside the model and away from Android, so the persistence *rules*
 * can be tested without a device: a key that no longer maps to a known algorithm
 * or stage is ignored rather than crashing, and encode/decode round-trips exactly.
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
     * update the app without losing the progress that *is* still valid — and it is
     * what lets an install that recorded `:CHALLENGE` before that stage was
     * deferred to V2 keep its WATCH and TRY progress instead of failing to load.
     */
    private inline fun <reified E : Enum<E>> enumOrNull(name: String): E? =
        enumValues<E>().firstOrNull { it.name == name }
}
