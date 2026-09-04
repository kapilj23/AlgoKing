package com.ttele.algoking.engine.scene

/**
 * A hash map, drawn as a table of buckets — DESIGN_SYSTEM.md §6.16e.
 *
 * This is the first lesson whose picture is genuinely not a sequence. A hash map
 * has no order to show: what it has is a *calculation* that lands on a bucket, and
 * buckets that may hold more than one thing. Forcing that into a row of cells
 * would have taught the one idea the structure exists to refute — that you find
 * data by walking past the data in front of it.
 */
data class BucketScene(
    val buckets: List<BucketRow>,
    /**
     * The key's journey, drawn above the table: `12 → 12 % 5 → 2 → BUCKET 2`.
     * Null between operations, when there is no key in flight.
     */
    val flow: HashFlow? = null,
    /** e.g. "Looking for 7". */
    val badge: Badge? = null,
    val meters: List<MeterReadout> = emptyList(),
) : Scene

data class BucketRow(
    val index: Int,
    val entries: List<EntryCell>,
    val state: BucketState = BucketState.IDLE,
)

enum class BucketState {
    IDLE,

    /** The bucket the key hashed to. Where the operation is happening. */
    TARGET,

    /**
     * Holds more than one key. Drawn as a chain, and deliberately not as an error:
     * a collision is a thing hash maps expect, not a thing that went wrong.
     */
    COLLIDED,
}

data class EntryCell(
    val key: Int,
    val label: String,
    val state: EntryState = EntryState.IDLE,
)

enum class EntryState {
    IDLE,

    /** Being compared against the key that is being looked up. */
    SCANNING,

    /** The key matched. */
    MATCHED,

    /** On its way out. */
    LEAVING,

    /** Just stored. */
    NEW,
}

/**
 * The step the key has reached on its way to a bucket.
 *
 * Kept as data rather than as an animation so the walkthrough can stop on any part
 * of it — *this is the key*, *this is the arithmetic*, *this is where it lands* —
 * and so Try can ask the learner to finish the calculation themselves.
 */
data class HashFlow(
    val key: Int,
    val modulus: Int,
    /** Null until the bucket has actually been worked out. */
    val bucket: Int? = null,
    val stage: FlowStage = FlowStage.KEY,
    val operation: FlowOperation = FlowOperation.LOOKUP,
)

enum class FlowStage {
    /** A key, and nothing done with it yet. */
    KEY,

    /** The arithmetic is on screen and the answer is not. */
    ASKING,

    /** The bucket index is known. */
    HASHED,

    /** Inside the bucket, comparing keys. */
    SCANNING,

    /** The operation finished. */
    DONE,
}

/** What the key is being used for. Changes the words, never the arithmetic. */
enum class FlowOperation { PUT, LOOKUP, REMOVE }
