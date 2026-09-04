package com.ttele.algoking.engine.algorithms.hashing

import com.ttele.algoking.engine.core.Dataset
import com.ttele.algoking.engine.decision.Action

/** One stored pair. The [label] is the value; the [key] is how it is found again. */
data class MapEntry(val key: Int, val label: String)

/** What a lesson asks the learner to do to the map. */
sealed interface HashTask {

    /** Store [label] under [key]. If the key is already there, its value is updated. */
    data class Put(val key: Int, val label: String) : HashTask

    /** Find what is stored under [key] — or prove nothing is. */
    data class Get(val key: Int) : HashTask

    /** Take [key] out. */
    data class Remove(val key: Int) : HashTask
}

/** The [key] each task acts on, whatever kind of task it is. */
val HashTask.key: Int
    get() = when (this) {
        is HashTask.Put -> key
        is HashTask.Get -> key
        is HashTask.Remove -> key
    }

/**
 * How far through the current operation the learner is.
 *
 * Every operation begins the same way — **hash the key** — and that is the point of
 * splitting the phases: the arithmetic is asked first and separately, every single
 * time, so it becomes the reflex the structure depends on.
 */
enum class HashPhase {
    /** The key is on screen and the bucket has not been worked out yet. */
    HASHING,

    /** The bucket already holds something. What happens to the new entry? */
    RESOLVING,

    /** Inside the bucket, comparing keys. */
    SCANNING,

    /** Nothing in flight. */
    SETTLED,
}

/** What a hash map does when the bucket it lands on is already occupied. */
enum class Resolution {
    /** Keep both entries in the bucket. Separate chaining. */
    CHAIN,

    /** Same key, so the old value is overwritten. */
    REPLACE,

    /** Refuse the new entry. Never correct — offered because learners expect it. */
    REJECT,
}

/** What the learner can do to a hash map. */
sealed interface HashAction : Action {

    /** "The key hashes to this bucket." The learner taps the bucket itself. */
    data class Hash(val bucket: Int) : HashAction

    /** "The bucket is occupied, and this is what should happen." */
    data class Resolve(val resolution: Resolution) : HashAction

    /**
     * "This is the entry." [key] is the entry's key, or null for *it is not here* —
     * which is a real answer, not a failure.
     */
    data class Inspect(val key: Int?) : HashAction

    /** Bookkeeping the app performs: storing, removing, moving to the next task. */
    data object Settle : HashAction
}

/**
 * A hash map, mid-lesson.
 *
 * [buckets] is a fixed-size list of chains. It is a list of lists rather than a flat
 * array because that shape *is* the teaching model: a bucket is a place that can
 * hold more than one thing, and a collision is two keys sharing one.
 */
data class HashMapState(
    val buckets: List<List<MapEntry>>,
    val script: List<HashTask>,
    val at: Int,
    val phase: HashPhase = HashPhase.HASHING,
    /** The bucket the key hashed to, once the learner has worked it out. */
    val bucket: Int? = null,
    /** The entry the scan landed on. */
    val hit: MapEntry? = null,
    /** True when the last operation was a genuine collision — two different keys. */
    val collided: Boolean = false,
    /** True when the last put overwrote a value rather than adding one. */
    val updated: Boolean = false,
    /** True when a lookup or removal proved the key was not there. */
    val missing: Boolean = false,
    val done: Boolean = false,
) {
    val task: HashTask? get() = script.getOrNull(at)
    val modulus: Int get() = buckets.size

    /** The teaching hash function, and the only one in the app. */
    fun hash(key: Int): Int = key.mod(modulus)

    fun chain(index: Int): List<MapEntry> = buckets.getOrElse(index) { emptyList() }

    fun entryFor(key: Int): MapEntry? = chain(hash(key)).firstOrNull { it.key == key }

    val entryCount: Int get() = buckets.sumOf { it.size }

    /** Buckets holding more than one key. What "collision" means, counted. */
    val collisions: Int get() = buckets.count { it.size > 1 }
}

/**
 * Turns a dataset into a lesson script.
 *
 * The dataset's `label` names its role, and the roles differ because the lessons
 * differ: Watch shows each operation once and engineers exactly one collision, Try
 * adds the duplicate-key case, and a challenge is a bare sequence of operations
 * with nothing explained.
 */
object HashScripts {

    const val BUCKETS = 5

    const val WATCH = "watch"
    const val TRY = "try"
    const val OPERATIONS = "operations"
    const val COLLISION = "collision"

    /**
     * Names rather than numbers for the values, so a key and a value can never be
     * confused for each other on screen.
     */
    private val ROSTER = listOf("Alice", "Bob", "Charlie", "Dana", "Evan", "Farah")

    fun labelFor(index: Int): String = ROSTER[index.mod(ROSTER.size)]

    fun forDataset(dataset: Dataset): List<HashTask> {
        val keys = dataset.values
        if (keys.isEmpty()) return emptyList()
        val target = dataset.target ?: keys.first()

        return when (dataset.label) {
            // Store, collide, look up, remove. Every idea once, nothing repeated.
            WATCH -> buildList {
                keys.forEachIndexed { index, key -> add(HashTask.Put(key, labelFor(index))) }
                add(HashTask.Get(target))
                add(HashTask.Remove(target))
            }

            // Try adds the case nobody guesses right: the same key put twice.
            TRY -> buildList {
                keys.forEachIndexed { index, key -> add(HashTask.Put(key, labelFor(index))) }
                add(HashTask.Put(keys.first(), labelFor(keys.size)))
                add(HashTask.Get(target))
                add(HashTask.Remove(keys.first()))
            }

            // A pre-loaded map with a shared bucket, and one lookup inside it. The
            // whole question is whether the learner scans the chain.
            COLLISION -> listOf(
                HashTask.Get(keys.first()),
                HashTask.Get(target),
                HashTask.Remove(target),
            )

            else -> buildList {
                keys.forEachIndexed { index, key -> add(HashTask.Put(key, labelFor(index))) }
                add(HashTask.Get(target))
                add(HashTask.Remove(keys.first()))
            }
        }
    }

    /**
     * The map a challenge starts from. Only the collision challenge is pre-loaded —
     * everything else is built by the learner, which is most of the point.
     */
    fun preloadFor(dataset: Dataset): List<List<MapEntry>> {
        val empty = List(BUCKETS) { emptyList<MapEntry>() }
        if (dataset.label != COLLISION) return empty
        return dataset.values
            .mapIndexed { index, key -> MapEntry(key, labelFor(index)) }
            .fold(empty) { acc, entry ->
                val slot = entry.key.mod(BUCKETS)
                acc.mapIndexed { index, chain ->
                    if (index == slot) chain + entry else chain
                }
            }
    }
}
