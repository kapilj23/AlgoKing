package com.ttele.algoking.engine.algorithms.countingsort

import com.ttele.algoking.engine.core.Algorithm
import com.ttele.algoking.engine.core.AlgorithmId
import com.ttele.algoking.engine.core.Dataset
import com.ttele.algoking.engine.core.Probe
import com.ttele.algoking.engine.core.Transition
import com.ttele.algoking.engine.decision.Action
import com.ttele.algoking.engine.decision.ActionOption
import com.ttele.algoking.engine.decision.Decision
import com.ttele.algoking.engine.decision.DecisionKind
import com.ttele.algoking.engine.event.ExamineRole
import com.ttele.algoking.engine.event.MeterId
import com.ttele.algoking.engine.event.Outcome
import com.ttele.algoking.engine.event.VizEvent
import com.ttele.algoking.engine.narration.NarrationId
import com.ttele.algoking.engine.narration.NarrationKey

/**
 * What the learner does in Counting Sort.
 *
 * **One gesture, twice over: tap a bucket.** In the counting phase the tap answers
 * *"which bucket counts this value?"*; in the rebuild it answers *"which value
 * comes next?"*. Using the same gesture for both is deliberate (ADR-034's rule):
 * it is the same table doing the same job, first being filled and then being read,
 * and a second control would suggest a second idea.
 */
sealed interface CountingSortAction : Action {

    /** Count the current input value into the bucket for [value]. */
    data class Count(val value: Int) : CountingSortAction

    /** Take one [value] out of its bucket and place it in the output. */
    data class Place(val value: Int) : CountingSortAction
}

/**
 * Immutable state — the single source of truth for the lesson (§7 of the brief).
 *
 * ### The constraints, stated
 *
 * Values are **non-negative integers** in a small range, and the count table spans
 * `min..max` rather than `0..max`: the lesson opens by finding the range, so the
 * table it builds has to be the range it found. Indexing is `value - min`, and
 * that subtraction is the app's — the learner never sees an internal index,
 * because a bucket is presented as *the bucket for the value 3*, which is the only
 * way the idea transfers.
 *
 * [counts] is the table being filled. [placed] is the answer being rebuilt, and
 * [taken] records how many of each value have already been emitted, so a bucket
 * that has given up everything it had can say so rather than looking untouched.
 */
data class CountingSortState(
    val values: List<Int>,
    /** One entry per value in `min..max`, indexed `value - min`. */
    val counts: List<Int>,
    /** The next input position to count. Equal to `values.size` once counting ends. */
    val cursor: Int,
    /** The sorted output so far. */
    val placed: List<Int>,
    /** How many of each value have been placed, parallel to [counts]. */
    val taken: List<Int>,
    /** The value the last action touched, for the tally strip and the highlight. */
    val lastValue: Int? = null,
) {
    val size: Int get() = values.size

    val min: Int get() = values.minOrNull() ?: 0
    val max: Int get() = values.maxOrNull() ?: 0

    /** `k` — the width of the value range, and the size of the count table. */
    val span: Int get() = if (values.isEmpty()) 0 else max - min + 1

    /** Every value the table has a bucket for, ascending. */
    val bucketValues: List<Int> get() = (0 until span).map { min + it }

    val countingComplete: Boolean get() = cursor >= size

    val rebuildComplete: Boolean get() = placed.size >= size

    val finished: Boolean get() = values.isEmpty() || (countingComplete && rebuildComplete)

    /** The input value being counted right now. Null once counting is done. */
    val currentValue: Int? get() = values.getOrNull(cursor)

    private fun indexOf(value: Int): Int? =
        (value - min).takeIf { values.isNotEmpty() && it in counts.indices }

    fun countOf(value: Int): Int = indexOf(value)?.let { counts[it] } ?: 0

    fun takenOf(value: Int): Int = indexOf(value)?.let { taken[it] } ?: 0

    /** How many of [value] are still waiting to be placed. */
    fun remainingOf(value: Int): Int = countOf(value) - takenOf(value)

    /**
     * The value the rebuild must emit next: **the smallest one with anything
     * left**. That rule, applied over and over, is the whole of the second half of
     * counting sort — the output comes out sorted because the table is read in
     * ascending order, not because anything was compared.
     */
    val nextToPlace: Int?
        get() = bucketValues.firstOrNull { remainingOf(it) > 0 }

    /** The answer, whether or not the learner has rebuilt it yet. */
    val sorted: List<Int> get() = values.sorted()

    /**
     * The counts a correct run ends with — what the table *should* say once every
     * input value has been counted.
     */
    val trueCounts: List<Int>
        get() = (0 until span).map { i -> values.count { it == min + i } }
}

/**
 * Counting Sort — a Sorting lesson about not comparing anything.
 *
 * ### What it teaches
 *
 * Every other sort in the library decides order by comparing elements to each
 * other. This one never compares two values at all: it counts how many times each
 * value appears, then reads the table back in order. The Complete screen reports
 * **0 comparisons** because the run genuinely made none, which is the strongest
 * statement of the idea the app can make.
 *
 * ```
 * input   [4, 2, 2, 8, 3, 3, 1]
 * range   1 .. 8
 * counts   1:1  2:2  3:2  4:1  5:0  6:0  7:0  8:1
 * output  [1, 2, 2, 3, 3, 4, 8]
 * ```
 *
 * ### Complexity
 *
 * **O(n + k)** time and **O(k)** space, where `n` is the number of elements and
 * `k` is the width of the value range: one pass to count, one pass over the table
 * to rebuild. That is faster than the O(n log n) comparison sorts — and it is a
 * bad trade the moment `k` is large, because a table of a million buckets to sort
 * seven values costs a million steps to read back. The lesson says both halves.
 *
 * ### Constraints, deliberately narrow
 *
 * Non-negative integers, small range, no stability machinery and no prefix-sum
 * placement pass. Real implementations accumulate the counts into starting
 * positions to keep equal elements in their original order; that is a second idea
 * on top of this one, and it is not what a learner meeting counting sort first
 * needs. What ships is the honest core: count, then rebuild.
 */
class CountingSortAlgorithm : Algorithm<CountingSortState, CountingSortAction> {

    override val id = AlgorithmId.COUNTING_SORT

    override fun initial(dataset: Dataset): CountingSortState {
        val values = dataset.values
        val span = if (values.isEmpty()) 0 else values.max() - values.min() + 1
        return CountingSortState(
            values = values,
            counts = List(span) { 0 },
            cursor = 0,
            placed = emptyList(),
            taken = List(span) { 0 },
        )
    }

    override fun probe(state: CountingSortState): Probe<CountingSortAction> {
        // An empty array is already sorted. That is a finished lesson, not an error.
        if (state.values.isEmpty()) return Probe.Terminal(Outcome.Sorted)

        if (!state.countingComplete) return Probe.Decide(countDecision(state))
        if (!state.rebuildComplete) return Probe.Decide(placeDecision(state))

        return Probe.Terminal(Outcome.Sorted)
    }

    // -- Phase 1: count -------------------------------------------------------

    /**
     * *Which bucket counts this value?*
     *
     * Every bucket is an option, including the ones that are obviously wrong: the
     * answer is "the bucket with the same number on it", and a shortlist would do
     * the matching for the learner.
     *
     * The **increment is not asked**. Once the bucket is named there is exactly one
     * legal thing to do to it, and tapping the only legal target teaches a gesture
     * rather than the idea (PRODUCT_SPEC.md §3). The app adds one and says so.
     */
    private fun countDecision(state: CountingSortState): Decision<CountingSortAction> {
        val value = requireNotNull(state.currentValue)
        val current = state.countOf(value)

        return Decision(
            kind = DecisionKind.CELL,
            prompt = NarrationKey(NarrationId.CS_ASK_BUCKET, listOf(value)),
            options = state.bucketValues.mapIndexed { slot, bucket ->
                ActionOption(
                    action = CountingSortAction.Count(bucket),
                    label = NarrationKey(NarrationId.CS_OPTION_BUCKET, listOf(bucket)),
                    slot = slot,
                )
            },
            correct = CountingSortAction.Count(value),
            focus = listOf(state.cursor),
            hint = NarrationKey(NarrationId.CS_HINT_BUCKET),
            guidance = listOf(
                NarrationKey(NarrationId.CS_RETRY_COUNT_LOOK, listOf(value)),
                NarrationKey(NarrationId.CS_RETRY_COUNT_ASK, listOf(value)),
                NarrationKey(NarrationId.CS_RETRY_COUNT_EXPLAIN, listOf(value, current, current + 1)),
            ),
            minimalFeedback = NarrationKey(NarrationId.CS_RETRY_COUNT_LOOK, listOf(value)),
            whyWrong = buildMap {
                for (bucket in state.bucketValues) {
                    if (bucket == value) continue
                    put(
                        CountingSortAction.Count(bucket),
                        NarrationKey(NarrationId.CS_WHY_WRONG_BUCKET, listOf(bucket, value)),
                    )
                }
            },
            correctFeedback = NarrationKey(
                NarrationId.CS_CORRECT_COUNT,
                listOf(value, current, current + 1),
            ),
            hintLadder = listOf(
                NarrationKey(NarrationId.CS_HINT_BUCKET),
                NarrationKey(NarrationId.CS_RETRY_COUNT_ASK, listOf(value)),
            ),
        )
    }

    // -- Phase 2: rebuild -----------------------------------------------------

    /**
     * *Which value comes next?*
     *
     * The same tap on the same table, now being read instead of written. The two
     * wrong answers a learner actually gives are both available at every beat and
     * both mean something specific: a bucket that counted nothing, and a bucket
     * further along that still has values in it but is not next.
     */
    private fun placeDecision(state: CountingSortState): Decision<CountingSortAction> {
        val correct = requireNotNull(state.nextToPlace)
        val remaining = state.remainingOf(correct)
        val position = state.placed.size

        return Decision(
            kind = DecisionKind.CELL,
            prompt = NarrationKey(NarrationId.CS_ASK_NEXT_OUT, listOf(position + 1)),
            options = state.bucketValues.mapIndexed { slot, bucket ->
                ActionOption(
                    action = CountingSortAction.Place(bucket),
                    label = NarrationKey(NarrationId.CS_OPTION_BUCKET, listOf(bucket)),
                    slot = slot,
                )
            },
            correct = CountingSortAction.Place(correct),
            focus = listOf(position),
            hint = NarrationKey(NarrationId.CS_HINT_REBUILD),
            guidance = listOf(
                NarrationKey(NarrationId.CS_RETRY_PLACE_LOOK),
                NarrationKey(NarrationId.CS_RETRY_PLACE_ASK),
                NarrationKey(NarrationId.CS_RETRY_PLACE_EXPLAIN, listOf(correct, remaining)),
            ),
            minimalFeedback = NarrationKey(NarrationId.CS_RETRY_PLACE_LOOK),
            whyWrong = buildMap {
                for (bucket in state.bucketValues) {
                    if (bucket == correct) continue
                    val left = state.remainingOf(bucket)
                    val id = when {
                        // Nothing was ever counted here.
                        state.countOf(bucket) == 0 -> NarrationId.CS_WHY_EMPTY_BUCKET
                        // It had values and they are all out already.
                        left == 0 -> NarrationId.CS_WHY_BUCKET_SPENT
                        // It still has values, but a smaller one is waiting.
                        else -> NarrationId.CS_WHY_OUT_OF_ORDER
                    }
                    put(
                        CountingSortAction.Place(bucket),
                        NarrationKey(id, listOf(bucket, correct, state.countOf(bucket))),
                    )
                }
            },
            correctFeedback = NarrationKey(
                NarrationId.CS_CORRECT_PLACE,
                listOf(correct, remaining - 1),
            ),
            hintLadder = listOf(
                NarrationKey(NarrationId.CS_HINT_REBUILD),
                NarrationKey(NarrationId.CS_RETRY_PLACE_ASK),
            ),
        )
    }

    // -- Transitions ----------------------------------------------------------

    override fun apply(
        state: CountingSortState,
        action: CountingSortAction,
    ): Transition<CountingSortState> = when (action) {
        is CountingSortAction.Count -> count(state, action.value)
        is CountingSortAction.Place -> place(state, action.value)
    }

    /**
     * Counts the current input value into whichever bucket it was given — right or
     * wrong. `apply` takes any legal action and is not the thing that decides
     * which are legal (ARCHITECTURE.md §4.1); in Try nothing ever calls it with a
     * wrong bucket, because a `Retry` carries no action.
     */
    private fun count(state: CountingSortState, bucket: Int): Transition<CountingSortState> {
        if (state.countingComplete || state.values.isEmpty()) {
            return Transition(state, emptyList(), null, correct = false)
        }
        val value = requireNotNull(state.currentValue)
        val index = bucket - state.min
        if (index !in state.counts.indices) {
            return Transition(state, emptyList(), null, correct = false)
        }

        val updated = state.counts.toMutableList().also { it[index] = it[index] + 1 }

        return Transition(
            next = state.copy(
                counts = updated,
                cursor = state.cursor + 1,
                lastValue = bucket,
            ),
            events = listOf(
                VizEvent.Examine(listOf(state.cursor), ExamineRole.INSPECTING),
                // No `Compare`: this algorithm never compares two elements, and the
                // 0 on the Complete screen is the lesson's closing argument.
                VizEvent.Meter(MeterId.REMAINING, updated[index].toLong()),
            ),
            narration = NarrationKey(
                NarrationId.CS_COUNTED,
                listOf(value, bucket, updated[index] - 1, updated[index]),
            ),
            correct = bucket == value,
        )
    }

    /** Emits one value from its bucket into the output. */
    private fun place(state: CountingSortState, value: Int): Transition<CountingSortState> {
        if (state.rebuildComplete || !state.countingComplete) {
            return Transition(state, emptyList(), null, correct = false)
        }
        val index = value - state.min
        if (index !in state.taken.indices) {
            return Transition(state, emptyList(), null, correct = false)
        }

        val updated = state.taken.toMutableList().also { it[index] = it[index] + 1 }
        val next = state.copy(
            placed = state.placed + value,
            taken = updated,
            lastValue = value,
        )
        val at = state.placed.size

        return Transition(
            next = next,
            events = buildList {
                add(VizEvent.Insert(value, at))
                add(VizEvent.Meter(MeterId.REMAINING, next.remainingOf(value).toLong()))
                if (next.rebuildComplete) {
                    add(VizEvent.Finalize(0..state.values.lastIndex))
                    add(VizEvent.Terminal(Outcome.Sorted))
                }
            },
            narration = NarrationKey(
                NarrationId.CS_PLACED,
                listOf(value, at + 1, next.remainingOf(value)),
            ),
            correct = value == state.nextToPlace,
        )
    }
}
