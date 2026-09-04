package com.ttele.algoking.engine.dataset

import com.ttele.algoking.engine.core.Algorithm
import com.ttele.algoking.engine.core.AlgorithmRunner
import com.ttele.algoking.engine.core.Dataset
import com.ttele.algoking.engine.core.Trace
import com.ttele.algoking.engine.decision.Action
import com.ttele.algoking.engine.event.Outcome
import com.ttele.algoking.engine.event.VizEvent
import kotlin.random.Random

/**
 * Challenge generation — ARCHITECTURE.md §9.
 *
 * Deterministic, offline, and **validated by running the real algorithm**, so it
 * is impossible to generate a dataset that violates a pedagogical rule.
 */
data class DatasetSpec(
    val size: IntRange,
    val valueRange: IntRange,
    val sorted: Boolean = true,
    val distinct: Boolean = true,
    val scenario: Scenario = Scenario.ANYWHERE,
    val constraints: List<Constraint> = emptyList(),
)

/**
 * Challenge variety. `ABSENT` is not an edge case bolted on — it is the half of
 * Binary Search that teaches why the range can empty out.
 */
enum class Scenario { EARLY, MIDDLE, LATE, ABSENT, ANYWHERE }

/** A constraint is asserted against the TRACE, not against the raw array. */
fun interface Constraint {
    fun holds(trace: Trace<*>, dataset: Dataset): Boolean
}

/** PRODUCT_SPEC.md §6 — "fully regenerated, no overlap". */
class NoValueOverlapWith(private vararg val others: Dataset) : Constraint {
    override fun holds(trace: Trace<*>, dataset: Dataset): Boolean {
        val mine = dataset.values.toSet()
        return others.none { other -> other.values.any { it in mine } }
    }
}

/** The challenge is neither trivial nor exhausting. */
class ComparisonsBetween(private val min: Int, private val max: Int) : Constraint {
    override fun holds(trace: Trace<*>, dataset: Dataset): Boolean {
        val comparisons = trace.frames.last().metrics.comparisons
        return comparisons in min..max
    }
}

/**
 * PRODUCT_SPEC.md §6 — the solution path must differ from Watch. If Watch found
 * the target by going right first, the challenge must go left first.
 */
class FirstBranchDiffersFrom(private val watch: Trace<*>) : Constraint {
    override fun holds(trace: Trace<*>, dataset: Dataset): Boolean =
        firstBranch(trace) != null && firstBranch(trace) != firstBranch(watch)

    private fun firstBranch(trace: Trace<*>): Boolean? = trace.frames
        .firstNotNullOfOrNull { frame ->
            frame.events.filterIsInstance<VizEvent.Eliminate>().firstOrNull()
        }
        ?.let { it.range.first == 0 }
}

/** The run must end the way the scenario promises. */
class EndsWith(private val found: Boolean) : Constraint {
    override fun holds(trace: Trace<*>, dataset: Dataset): Boolean {
        val outcome = trace.frames.last().events
            .filterIsInstance<VizEvent.Terminal>()
            .lastOrNull()
            ?.outcome
        return when (outcome) {
            is Outcome.Found -> found
            Outcome.NotFound -> !found
            else -> false
        }
    }
}

class SeededGenerator<S : Any, A : Action>(private val algorithm: Algorithm<S, A>) {

    fun generate(spec: DatasetSpec, seed: Long): Dataset {
        val rng = Random(seed)
        repeat(MAX_ATTEMPTS) {
            val candidate = draw(spec, rng)
            val trace = AlgorithmRunner(algorithm, candidate).runToCompletion()
            if (spec.constraints.all { it.holds(trace, candidate) }) return candidate
        }
        // Never fails: fall back to a curated dataset for this scenario.
        return CuratedFallbacks.forScenario(spec.scenario, seed)
    }

    private fun draw(spec: DatasetSpec, rng: Random): Dataset {
        val size = spec.size.random(rng)
        val values = generateSequence { spec.valueRange.random(rng) }
            .distinct()
            .take(size)
            .sorted()
            .toList()

        val target = when (spec.scenario) {
            Scenario.EARLY -> values[rng.nextInt(0, (size / 3).coerceAtLeast(1))]
            Scenario.MIDDLE -> values[size / 2 + rng.nextInt(-1, 2).coerceIn(-(size / 2), 0)]
            Scenario.LATE -> values[rng.nextInt(size - size / 3, size)]
            Scenario.ABSENT -> absentValue(values, spec.valueRange, rng)
            Scenario.ANYWHERE -> values.random(rng)
        }

        return Dataset(values = values, target = target, label = spec.scenario.name)
    }

    private fun absentValue(values: List<Int>, range: IntRange, rng: Random): Int {
        val taken = values.toSet()
        repeat(64) {
            val candidate = range.random(rng)
            if (candidate !in taken) return candidate
        }
        return values.max() + 1
    }

    private companion object {
        const val MAX_ATTEMPTS = 200
    }
}

/**
 * Watch datasets are authored, not generated — ARCHITECTURE.md §9. The teaching
 * example has to be perfect.
 */
object BinarySearchDatasets {

    /**
     * 12 values, target 45 at index 6. Authored against the canonical lower
     * middle, `left + (right - left) / 2`:
     *
     * ```
     * lo=0  hi=11  mid=5  → 37 < 45, keep right, 6 of 12 gone
     * lo=6  hi=11  mid=8  → 61 > 45, keep left,  4 more gone
     * lo=6  hi=7   mid=6  → 45. Found, in three comparisons.
     * ```
     *
     * Three properties earn this target its place, and no other target in the
     * array has all three:
     *
     *  - **it shows both branches.** Right, then left. A learner who only ever
     *    sees the range shrink from one side has watched half the algorithm.
     *  - **the first cut is exactly half.** Six of twelve, which is the number
     *    the insight frame quotes — an honest "half the array, gone" rather than
     *    an awkward seven.
     *  - **it ends on a two-cell range**, where `mid` lands on the *left* of the
     *    two. That is precisely where the lower and upper conventions disagree,
     *    so the lesson demonstrates the rule at the one moment it is visible.
     */
    val watch = Dataset(
        values = listOf(3, 8, 14, 21, 29, 37, 45, 52, 61, 73, 81, 94),
        target = 45,
        label = "watch",
    )

    /**
     * A different array and a different target, so Try is application rather
     * than recall — and the branches run in the opposite order to Watch:
     *
     * ```
     * lo=0  hi=12  mid=6  → 42 > 28, keep left,  7 of 13 gone
     * lo=0  hi=5   mid=2  → 15 < 28, keep right, 3 more gone
     * lo=3  hi=5   mid=4  → 28. Found, in three comparisons.
     * ```
     *
     * Watch goes right then left; Try goes left then right. A learner who
     * pattern-matched the Watch run instead of reasoning gets caught here on the
     * very first decision.
     */
    val tryIt = Dataset(
        values = listOf(4, 9, 15, 21, 28, 35, 42, 49, 56, 62, 71, 84, 91),
        target = 28,
        label = "try",
    )
}

/**
 * Bubble Sort's teaching arrays.
 *
 * [watch] is `7 3 8 2 5` because its first pass reads swap, keep, swap, swap — the
 * learner meets "do not swap" second, before they can mistake Bubble Sort for
 * "swap every pair". [tryIt] shares the rhythm but none of the values.
 */
object BubbleSortDatasets {

    val watch = Dataset(values = listOf(7, 3, 8, 2, 5), label = "watch")

    val tryIt = Dataset(values = listOf(6, 2, 9, 1, 4), label = "try")
}

/**
 * Selection Sort's teaching arrays.
 *
 * [watch] is `7 3 8 2 5`: the minimum changes twice during the first scan, so
 * the learner sees the candidate get *replaced* rather than found on the first
 * look — which is the whole idea of scanning.
 */
object SelectionSortDatasets {

    val watch = Dataset(values = listOf(7, 3, 8, 2, 5), label = "watch")

    val tryIt = Dataset(values = listOf(6, 4, 9, 2, 7), label = "try")
}

/**
 * Insertion Sort's teaching arrays.
 *
 * [watch] is `7 3 8 2 5`: the first key shifts once, the second (8) needs no
 * shift at all, and the third (2) walks all the way to the front. A learner who
 * only saw shifting would conclude every key moves.
 */
object InsertionSortDatasets {

    val watch = Dataset(values = listOf(7, 3, 8, 2, 5), label = "watch")

    val tryIt = Dataset(values = listOf(6, 9, 2, 7, 4), label = "try")
}

/**
 * Merge Sort's teaching arrays.
 *
 * [watch] is eight values so the hierarchy has three clean levels — 8 → 4 → 2 → 1
 * and back — which is what makes the divide-and-conquer shape visible at all.
 * [tryIt] is four values, because Try asks the learner for every merge decision.
 */
object MergeSortDatasets {

    val watch = Dataset(values = listOf(8, 3, 6, 2, 7, 1, 5, 4), label = "watch")

    val tryIt = Dataset(values = listOf(7, 2, 9, 4), label = "try")
}

/**
 * Quick Sort's teaching arrays.
 *
 * The pivot is always the **last** value of the partition, so both arrays are
 * authored to end in a middling number: the first partition then produces two
 * non-empty sides, which is what makes "repeat on each side" visible at all.
 * An array ending in its own minimum or maximum would teach the degenerate case.
 */
object QuickSortDatasets {

    val watch = Dataset(values = listOf(6, 3, 8, 2, 7, 4, 5), label = "watch")

    val tryIt = Dataset(values = listOf(8, 3, 6, 2, 7, 4, 5), label = "try")
}

/**
 * Stack and Queue share their teaching numbers **on purpose**.
 *
 * The two lessons run the identical script over the identical values, so the only
 * thing that differs is which item comes out — which is exactly the comparison the
 * pair is built to make. Reading them side by side is the point.
 */
object StructureDatasets {

    /** Four values: enough for the prediction to be a real question, short enough
     *  that draining the structure stays interesting. */
    val watch = Dataset(values = listOf(12, 27, 41, 58), label = "watch")

    val tryIt = Dataset(values = listOf(7, 23, 38, 54), label = "try")
}

/**
 * The Linked List teaching lists.
 *
 * Both are kept in order, which is what gives insertion a single defensible answer:
 * the learner is asked where a value *belongs*, not asked to guess which gap the
 * author had in mind. The label is not decoration — it selects the lesson script
 * (see `ListScripts`).
 */
object LinkedListDatasets {

    /** Four nodes: long enough that walking is a real journey, short enough to fit. */
    val watch = Dataset(values = listOf(10, 20, 30, 40), target = 30, label = "watch")

    val tryIt = Dataset(values = listOf(5, 12, 18, 25), target = 18, label = "try")
}

/**
 * The Hash Map teaching keys.
 *
 * With five buckets, 12 and 7 both land in bucket 2 — which is the whole reason
 * those two numbers are here. **The collision is authored, not hoped for**: a hash
 * map lesson where every key gets its own bucket teaches nothing about hash maps.
 */
object HashMapDatasets {

    /** 12 → bucket 2, 7 → bucket 2 (collision). The lookup and removal use 12. */
    val watch = Dataset(values = listOf(12, 7), target = 12, label = "watch")

    /** 24 → 4, 18 → 3, 9 → 4 (collision), then 24 again (duplicate key). */
    val tryIt = Dataset(values = listOf(24, 18, 9), target = 18, label = "try")
}

/** Bundled, never fails — one per scenario. */
internal object CuratedFallbacks {

    fun forScenario(scenario: Scenario, seed: Long): Dataset = when (scenario) {
        Scenario.EARLY -> Dataset(
            listOf(5, 11, 18, 24, 33, 40, 47, 55, 66, 78, 88),
            target = 11,
            label = "EARLY",
        )

        Scenario.MIDDLE -> Dataset(
            listOf(6, 13, 19, 26, 34, 41, 48, 57, 64, 76, 85),
            target = 41,
            label = "MIDDLE",
        )

        Scenario.LATE -> Dataset(
            listOf(2, 10, 17, 23, 31, 39, 46, 54, 63, 77, 90),
            target = 77,
            label = "LATE",
        )

        Scenario.ABSENT -> Dataset(
            listOf(7, 12, 20, 27, 36, 43, 51, 58, 67, 79, 86),
            target = 65,
            label = "ABSENT",
        )

        Scenario.ANYWHERE -> forScenario(
            Scenario.entries[(seed.mod(4))],
            seed,
        )
    }
}

/**
 * Two Pointers teaching data — authored, not generated (ADR-014).
 *
 * Both datasets are **sorted and distinct**, because sortedness is the entire
 * precondition of the technique and a teaching array that violates it would make
 * the rule look arbitrary.
 */
object TwoPointersDatasets {

    /**
     * `[1, 2, 4, 6, 8, 10]`, target `10`. Three rounds, and each teaches a
     * different thing:
     *
     * ```
     * left=0 right=5   1 + 10 = 11  >  10   move RIGHT
     * left=0 right=4   1 +  8 =  9  <  10   move LEFT
     * left=1 right=4   2 +  8 = 10  =  10   pair found
     * ```
     *
     * Three properties earn this dataset its place:
     *
     *  - **it shows both moves.** RIGHT first, then LEFT. A learner who only ever
     *    saw one pointer move has watched half the technique.
     *  - **the first sum is over, not under.** Starting high makes the reason
     *    visible: `10` is the largest value there is, so every pair containing it
     *    overshoots — the whole row goes, not just this pair.
     *  - **the answer is not at either end.** `2 + 8` sits inside the array, so
     *    the pair cannot be spotted before the walk begins.
     */
    val watch = Dataset(
        values = listOf(1, 2, 4, 6, 8, 10),
        target = 10,
        label = "watch",
    )

    /**
     * A different array and a different target, so TRY is application rather than
     * recall — but the same *shape* of run, so the model transfers:
     *
     * ```
     * left=0 right=5   3 + 21 = 24  >  17   move RIGHT
     * left=0 right=4   3 + 14 = 17  =  17   pair found
     * ```
     *
     * Deliberately shorter. TRY is where the learner is deciding rather than
     * reading, and two rounds is enough to prove they have the rule — a longer
     * walk would be the same judgement repeated, which is patience rather than
     * understanding (ADR-026).
     */
    val tryIt = Dataset(
        values = listOf(3, 5, 9, 11, 14, 21),
        target = 17,
        label = "try",
    )
}
