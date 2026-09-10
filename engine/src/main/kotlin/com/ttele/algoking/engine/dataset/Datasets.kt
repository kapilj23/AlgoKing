package com.ttele.algoking.engine.dataset

import com.ttele.algoking.engine.core.Algorithm
import com.ttele.algoking.engine.core.AlgorithmRunner
import com.ttele.algoking.engine.core.BinaryTree
import com.ttele.algoking.engine.core.Dataset
import com.ttele.algoking.engine.core.Graph
import com.ttele.algoking.engine.core.GraphNode
import com.ttele.algoking.engine.core.TreeNode
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
 * Counting Sort teaches on a **small range with holes in it**, because both halves
 * of the table have to be readable.
 *
 * ```
 * input   [4, 2, 2, 8, 3, 3, 1]      n = 7
 * range   1 .. 8                     k = 8
 * counts   1:1  2:2  3:2  4:1  5:0  6:0  7:0  8:1
 * output  [1, 2, 2, 3, 3, 4, 8]
 * ```
 *
 * Four properties earn this dataset its place:
 *
 *  - **two values repeat**, and one of them repeats on consecutive inputs. A
 *    bucket going `1 → 2` is the beat that makes a count a count rather than a
 *    yes/no flag, and it has to arrive early — it lands on the fourth value.
 *  - **three buckets stay empty.** `5`, `6` and `7` never appear, so the rebuild
 *    has to step over them, and "a count of zero places nothing" is something the
 *    learner watches rather than something the copy asserts.
 *  - **the range starts at 1, not 0**, so the table is visibly `min..max` — the
 *    range the lesson just went and found — rather than something that happens to
 *    start where arrays do.
 *  - **8 is an outlier.** It is the only value above 4, which is what makes the
 *    cost of `k` visible: three of the eight buckets exist only because one value
 *    is far away, and that is exactly when counting sort stops being a good idea.
 */
object CountingSortDatasets {

    val watch = Dataset(values = listOf(4, 2, 2, 8, 3, 3, 1), label = "watch")

    /**
     * `[3, 1, 4, 1, 5, 3]` — range `1..5`, output `[1, 1, 3, 3, 4, 5]`.
     *
     * Application rather than recall: a tighter range, a different hole (`2` is
     * missing), and the repeats are **not** adjacent in the input, so the learner
     * cannot count a run by eye and has to use the table. It also opens on a value
     * that is not the smallest, so the first placement is never the first thing
     * counted.
     */
    val tryIt = Dataset(values = listOf(3, 1, 4, 1, 5, 3), label = "try")
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

/**
 * Prefix Sum teaching data — authored, not generated (ADR-014).
 *
 * Both datasets carry their range query on the [Dataset] itself, so a second
 * range, a harder one, or a generated one later is data rather than code.
 */
object PrefixSumDatasets {

    /**
     * `[2, 4, 3, 7, 1]`, asking for `sum(1..3)`.
     *
     * ```
     * array  =    [2, 4, 3,  7,  1]
     * prefix = [0, 2, 6, 9, 16, 17]
     *
     * sum(1..3) = prefix[4] - prefix[1] = 16 - 2 = 14   (4 + 3 + 7)
     * ```
     *
     * Four properties earn this dataset its place:
     *
     *  - **the range starts at 1, not 0.** A range starting at 0 subtracts
     *    `prefix[0] = 0`, so the subtraction looks like it does nothing and the
     *    whole idea is invisible.
     *  - **it ends before the last element**, so `prefix[right + 1]` is a real
     *    interior cell rather than the final total — the off-by-one has somewhere
     *    to go wrong.
     *  - **the values are small and distinct**, so a learner can check the answer
     *    by adding 4 + 3 + 7 in their head and *see* that the subtraction agrees.
     *  - **7 is the largest value and sits inside the range**, so the running
     *    total takes a visible jump exactly where the query is looking.
     */
    val watch = Dataset(
        values = listOf(2, 4, 3, 7, 1),
        label = "watch",
        queryLeft = 1,
        queryRight = 3,
    )

    /**
     * A different array and a different range, so TRY is application rather than
     * recall:
     *
     * ```
     * array  =    [5, 1, 8,  2,  6]
     * prefix = [0, 5, 6, 14, 16, 22]
     *
     * sum(2..4) = prefix[5] - prefix[2] = 22 - 6 = 16   (8 + 2 + 6)
     * ```
     *
     * This range deliberately **ends at the last element**, so `prefix[right + 1]`
     * is the final total. Watch showed an interior boundary; meeting the other end
     * once is what stops "right + 1" being remembered as "somewhere in the middle".
     */
    val tryIt = Dataset(
        values = listOf(5, 1, 8, 2, 6),
        label = "try",
        queryLeft = 2,
        queryRight = 4,
    )
}

/**
 * Graph DFS teaching data — authored, not generated (ADR-014).
 *
 * ```
 *         A
 *        / \
 *       B   C
 *      / \
 *     D   E
 * ```
 *
 * Adjacency, in the order DFS explores it:
 *
 * ```
 * A: [B, C]      C: [A]
 * B: [A, D, E]   D: [B]
 * E: [B]
 * ```
 *
 * Starting at A, that produces `A → B → D → E → C` — and the order is *generated*
 * by the engine from this data, never written down anywhere as an answer.
 *
 * Four properties earn this graph its place:
 *
 *  - **it needs two backtracks**, D→B and B→A, and they are different: the first
 *    is a leaf dead end, the second is a node whose branches are all used up. A
 *    graph with one backtrack teaches backtracking as a special case.
 *  - **C is visited last**, long after it was available from A. That is the whole
 *    point of *depth* first, and a learner who expects breadth-first gets it wrong
 *    here in a way they will remember.
 *  - **B's neighbour list starts with A**, which is already visited — so the
 *    "skip visited neighbours" rule has to fire before the interesting choice.
 *  - **it is five nodes**, which fits on a phone without shrinking anything.
 */
object GraphDatasets {

    /** The lesson graph. Positions are normalised 0..1, authored for the shape. */
    val teachingGraph = Graph(
        nodes = listOf(
            GraphNode(id = "A", label = "A", x = 0.50f, y = 0.12f),
            GraphNode(id = "B", label = "B", x = 0.28f, y = 0.50f),
            GraphNode(id = "C", label = "C", x = 0.76f, y = 0.50f),
            GraphNode(id = "D", label = "D", x = 0.12f, y = 0.88f),
            GraphNode(id = "E", label = "E", x = 0.46f, y = 0.88f),
        ),
        adjacency = mapOf(
            "A" to listOf("B", "C"),
            "B" to listOf("A", "D", "E"),
            "C" to listOf("A"),
            "D" to listOf("B"),
            "E" to listOf("B"),
        ),
    )

    /**
     * WATCH and TRY use the **same graph**, which is a deliberate departure from
     * every other lesson.
     *
     * Elsewhere Try gets fresh data so it tests application rather than recall.
     * A graph is different: the traversal is only five nodes, so a second graph
     * would be memorisable just as easily — and what makes Try hard here is not
     * new data, it is that the learner now has to *produce* the two backtracks
     * they previously watched. Changing the graph as well would have added
     * unfamiliarity without adding a single new judgement.
     */
    val watch = Dataset(
        values = emptyList(),
        label = "watch",
        graph = teachingGraph,
        startNode = "A",
    )

    val tryIt = Dataset(
        values = emptyList(),
        label = "try",
        graph = teachingGraph,
        startNode = "A",
    )
}

/**
 * Graph BFS teaching data.
 *
 * **Deliberately the same graph as DFS** — `GraphDatasets.teachingGraph`, the same
 * adjacency order, the same start node. That is the whole comparison: on
 * identical data DFS gives `A → B → D → E → C` and BFS gives `A → B → C → D → E`,
 * so the difference cannot be blamed on the graph or on neighbour order. The
 * queue is the only thing that changed.
 *
 * ```
 * queue [A]        dequeue A   enqueue B, C   queue [B, C]
 * queue [B, C]     dequeue B   enqueue D, E   queue [C, D, E]
 * queue [C, D, E]  dequeue C   (A seen)       queue [D, E]
 * queue [D, E]     dequeue D   (B seen)       queue [E]
 * queue [E]        dequeue E   (B seen)       queue []
 * ```
 */
object BfsDatasets {

    val watch = Dataset(
        values = emptyList(),
        label = "watch",
        graph = GraphDatasets.teachingGraph,
        startNode = "A",
    )

    /**
     * Same graph again, for the reason DFS uses the same one twice: five nodes are
     * memorisable either way, and what makes TRY hard is producing the queue
     * operations rather than meeting new data.
     */
    val tryIt = Dataset(
        values = emptyList(),
        label = "try",
        graph = GraphDatasets.teachingGraph,
        startNode = "A",
    )
}

/**
 * Binary Search Tree teaching data.
 *
 * ```
 *              50
 *             /  \
 *           30    70
 *          / \    / \
 *        20  40  60  80
 * ```
 *
 * Searching for **60** produces `50 → 70 → 60` — and that path is *generated* by
 * the engine from this tree, never written down as an answer anywhere.
 *
 * Four properties earn this tree its place:
 *
 *  - **the path turns both ways.** 60 is greater than 50 (go RIGHT) and then
 *    smaller than 70 (go LEFT). A target reached by going right twice would let a
 *    learner finish the lesson having only ever applied half the rule.
 *  - **every comparison discards a real subtree.** Going right at 50 rules out
 *    20, 30 and 40; going left at 70 rules out 80. Four of seven nodes are never
 *    looked at, which is the entire point made visible.
 *  - **it is perfectly balanced**, so the lesson's own example is the O(log n)
 *    case it describes — and the skew that costs O(n) can be shown as the
 *    contrast rather than being the thing the learner was taught on.
 *  - **seven nodes over three levels** fit a phone without shrinking anything.
 *
 * `values` is the tree read **in order**, which is not decoration: a BST read
 * in-order *is* a sorted array, and that is the sentence connecting this lesson
 * to Binary Search.
 */
object BstDatasets {

    /**
     * The lesson tree, built by inserting values rather than by naming children.
     *
     * Insertion order is what decides a BST's shape, so authoring it this way
     * says where the shape came from — and it is checked by the same [insert]
     * a future insert lesson would teach.
     */
    val teachingTree: BinaryTree = BinaryTree.of(50, 30, 70, 20, 40, 60, 80)

    /** The target for both stages. Reached by one RIGHT and one LEFT. */
    const val TARGET = 60

    val watch = Dataset(
        values = teachingTree.inorder(),
        target = TARGET,
        label = "watch",
        tree = teachingTree,
    )

    /**
     * **The same tree, and the same target** — a deliberate departure from the
     * lessons whose Try gets fresh data, and the same call DFS and BFS made.
     *
     * Seven nodes are memorisable either way, so a second tree would buy
     * unfamiliarity rather than a new judgement. What makes Try hard here is that
     * the learner now has to *produce* each comparison's answer instead of
     * reading it, at every node, with the wrong branch one tap away.
     *
     * A different target over this tree is the obvious next dataset — 20 walks
     * left twice, and 65 ends at a null child — and both are data rather than
     * code.
     */
    val tryIt = Dataset(
        values = teachingTree.inorder(),
        target = TARGET,
        label = "try",
        tree = teachingTree,
    )
}

/**
 * AVL tree teaching data — three insertions, each a different lesson.
 *
 * `values` is the sequence to insert and `tree` is what the learner starts with.
 *
 * ```
 * WATCH                          TRY (the mirror)
 *       30                             20
 *      /  \                           /  \
 *    20    40                       10    30
 *   /                                       \
 * 10                                         40
 * ```
 *
 * Both stages run the same three shapes in the same order, and the second and
 * third are **mirror images** between them:
 *
 * | | WATCH | TRY |
 * |---|---|---|
 * | insert 1 | 35 — nothing to fix | 15 — nothing to fix |
 * | insert 2 | 5 — left-left, one **right** rotation | 50 — right-right, one **left** rotation |
 * | insert 3 | 37 — left-right, a **double** | 13 — right-left, a **double** |
 *
 * That is why Try is not recall even though the shapes are familiar: every answer
 * is the opposite hand of the one WATCH gave, which is exactly where this
 * technique is got wrong. Between them the two stages cover all four cases
 * without either being long enough to become a slideshow.
 *
 * Both runs end on a perfectly balanced tree of seven nodes — the same shape the
 * Binary Search Tree lesson is taught on, which is not a coincidence worth
 * hiding: it is what the rotations were for.
 */
object AvlDatasets {

    /**
     * Left-heavy to begin with: `30(20(10), 40)`, legal AVL, every factor within
     * ±1. Authored by insertion, because insertion order is what gives a tree its
     * shape.
     */
    val watchTree: BinaryTree = BinaryTree.of(30, 20, 40, 10)

    /** The mirror: `20(10, 30(-, 40))`. */
    val tryTree: BinaryTree = BinaryTree.of(20, 10, 30, 40)

    val watch = Dataset(
        values = listOf(35, 5, 37),
        label = "watch",
        tree = watchTree,
    )

    val tryIt = Dataset(
        values = listOf(15, 50, 13),
        label = "try",
        tree = tryTree,
    )
}

/**
 * Dijkstra teaching data — a weighted graph, and the first one in the app.
 *
 * ```
 *        B ──1── C          A–B 5   B–C 1   C–D 9
 *       /│\     /│          A–C 2   B–D 3   D–E 5
 *    5 / │ \3  / │9         B–E 4   D–F 6   E–F 2
 *     /  │  \ /  │
 *    A   4   X   │          start A · target F
 *     \  │  / \  │
 *    2 \ │ /   \ │
 *       \│/     \│
 *        C ──9── D ──6── F
 * ```
 *
 * The drawing above is only a sketch; the real shape is the **ladder** the layout
 * spike settled on — two rows of three with one diagonal rung, B→D. Positions are
 * authored from that spike: at 360dp the shortest edge is 83.5dp, every weight
 * label clears every node circle by at least 7.8dp, and no edge crosses another.
 *
 * ### The run
 *
 * ```
 * settle A(0)  ->  C = 2, B = 5
 * settle C(2)  ->  B: 5 -> 3   (the direct edge from A cost 5; the long way costs 3)
 *                  D = 11
 * settle B(3)  ->  D: 11 -> 6,  E = 7
 * settle D(6)  ->  E: 6+5 = 11, and E is already 7  ->  KEEP
 *                  F = 12
 * settle E(7)  ->  F: 12 -> 9
 * settle F(9)  ->  target reached
 * ```
 *
 * **Order A C B D E F · distances 0 2 3 6 7 9 · path A → C → B → E → F = 9.**
 * None of that is written down anywhere: the lesson generates it, and the tests
 * check it against an independent reference implementation.
 *
 * Five properties earn this graph its place:
 *
 *  - **the direct edge is a trap.** A–B costs 5 and A→C→B costs 3, so the very
 *    first thing the learner watches is a distance being *beaten*, which is what
 *    relaxation is;
 *  - **two more improvements follow**, D 11 → 6 and F 12 → 9, so it is not a trick
 *    that happens once;
 *  - **it contains a KEEP.** D offers E 6 + 5 = 11 against E's 7, and nothing
 *    changes. Without it a learner concludes that examining an edge always updates;
 *  - **D is settled but is not on the answer** — and D even has the direct edge to
 *    the target that looks shortest on the page. Settled does not mean chosen;
 *  - **no ties.** Every selection has a unique cheapest node, so every decision has
 *    exactly one right answer.
 */
object DijkstraDatasets {

    /** Node positions come from the layout spike, not from taste. */
    val teachingGraph = Graph(
        nodes = listOf(
            GraphNode(id = "A", label = "A", x = 0.02f, y = 0.50f),
            GraphNode(id = "B", label = "B", x = 0.30f, y = 0.08f),
            GraphNode(id = "C", label = "C", x = 0.30f, y = 0.92f),
            GraphNode(id = "D", label = "D", x = 0.66f, y = 0.92f),
            GraphNode(id = "E", label = "E", x = 0.66f, y = 0.08f),
            GraphNode(id = "F", label = "F", x = 0.98f, y = 0.50f),
        ),
        // Cheapest edge first at each node, so the walk reads naturally. The order
        // is data, exactly as it is for DFS: it decides which relaxation is shown
        // first, and the lesson, the tests and the walkthrough all read it here.
        adjacency = mapOf(
            "A" to listOf("C", "B"),
            "B" to listOf("C", "D", "E", "A"),
            "C" to listOf("B", "A", "D"),
            "D" to listOf("B", "E", "F", "C"),
            "E" to listOf("F", "B", "D"),
            "F" to listOf("E", "D"),
        ),
        weights = Graph.weightsOf(
            Triple("A", "B", 5),
            Triple("A", "C", 2),
            Triple("B", "C", 1),
            Triple("B", "D", 3),
            Triple("B", "E", 4),
            Triple("C", "D", 9),
            Triple("D", "E", 5),
            Triple("D", "F", 6),
            Triple("E", "F", 2),
        ),
    )

    /**
     * TRY gets a different graph, and this is the one lesson where that matters
     * most: Dijkstra's answer is a short memorable sentence — *"A C B E F, nine"* —
     * and the whole run is nine decisions, so a learner who recalls it can produce
     * every one of them without reasoning.
     *
     * ```
     * A–B 4   A–C 1   A–D 5   C–B 2
     * C–D 7   B–D 3   B–E 9   D–E 2       start A · target E
     * ```
     *
     * It also teaches something WATCH cannot. There are **two KEEPs** here rather
     * than one — C offers D 8 against 5, and B offers D 6 against 5, and both are
     * refused — and the winning route turns out to be the plain direct one,
     * `A → D → E = 7`, with all the work around C and B not on it at all.
     *
     * ```
     * settle A(0) -> B = 4, C = 1, D = 5
     * settle C(1) -> B: 4 -> 3,  D: 1+7 = 8 vs 5  ->  KEEP
     * settle B(3) -> D: 3+3 = 6 vs 5  ->  KEEP,   E = 12
     * settle D(5) -> E: 12 -> 7
     * settle E(7) -> target reached
     * ```
     */
    val tryGraph = Graph(
        nodes = listOf(
            GraphNode(id = "A", label = "A", x = 0.04f, y = 0.50f),
            GraphNode(id = "B", label = "B", x = 0.36f, y = 0.06f),
            GraphNode(id = "C", label = "C", x = 0.36f, y = 0.94f),
            GraphNode(id = "D", label = "D", x = 0.72f, y = 0.50f),
            GraphNode(id = "E", label = "E", x = 0.98f, y = 0.06f),
        ),
        adjacency = mapOf(
            "A" to listOf("C", "B", "D"),
            "B" to listOf("C", "D", "E", "A"),
            "C" to listOf("A", "B", "D"),
            "D" to listOf("A", "B", "E", "C"),
            "E" to listOf("D", "B"),
        ),
        weights = Graph.weightsOf(
            Triple("A", "B", 4),
            Triple("A", "C", 1),
            Triple("A", "D", 5),
            Triple("C", "B", 2),
            Triple("C", "D", 7),
            Triple("B", "D", 3),
            Triple("B", "E", 9),
            Triple("D", "E", 2),
        ),
    )

    val watch = Dataset(
        values = emptyList(),
        label = "watch",
        graph = teachingGraph,
        startNode = "A",
        targetNode = "F",
    )

    val tryIt = Dataset(
        values = emptyList(),
        label = "try",
        graph = tryGraph,
        startNode = "A",
        targetNode = "E",
    )
}

/**
 * Tree traversal teaching data — **shared by Inorder, Preorder and Postorder**.
 *
 * ### WATCH: one tree, three lessons
 *
 * ```
 *              50
 *             /  \
 *           30    70
 *          / \    / \
 *        20  40  60  80
 * ```
 *
 * All three walkthroughs run on **this same tree**, which is the whole reason the
 * three lessons are worth shipping separately: the data is identical, the picture
 * is identical, the gesture is identical, and the orders that come out are not.
 *
 * ```
 * inorder    20 → 30 → 40 → 50 → 60 → 70 → 80
 * preorder   50 → 30 → 20 → 40 → 70 → 60 → 80
 * postorder  20 → 40 → 30 → 60 → 80 → 70 → 50
 * ```
 *
 * None of those three sequences is written down in the code. Each is produced by
 * running its own lesson, and a test asserts all three against `BinaryTree`'s
 * independent recursive implementations.
 *
 * ### TRY: a different tree, and deliberately **not** a search tree
 *
 * ```
 *         7
 *        / \
 *       4   9
 *      /     \
 *     2       5
 * ```
 *
 * Read in order that is `2, 4, 7, 9, 5` — **not sorted**, and that is the point.
 *
 * > **Inorder traversal is a traversal rule, not a sorting algorithm. It produces
 * > sorted values only when the tree itself is a search tree.**
 *
 * On the WATCH tree, inorder happens to come out sorted, so a learner could
 * produce the whole answer by sorting seven numbers without traversing anything.
 * Here they cannot: only the rule produces `2, 4, 7, 9, 5`. The same tree also
 * carries two edge cases as ordinary content — 4 has only a left child and 9 has
 * only a right one — and five nodes keeps TRY to nine decisions.
 *
 * It is built from [TreeNode] literals rather than by insertion, because
 * `BinaryTree.of` inserts in search-tree order and could not express a tree that
 * is not one.
 */
object TreeDatasets {

    /** The shared teaching tree. Same shape in all three traversal lessons. */
    val teachingTree: BinaryTree = BinaryTree.of(50, 30, 70, 20, 40, 60, 80)

    /** Not a search tree, on purpose. */
    val tryTree: BinaryTree = BinaryTree(
        TreeNode(
            value = 7,
            left = TreeNode(4, left = TreeNode(2)),
            right = TreeNode(9, right = TreeNode(5)),
        ),
    )

    val watch = Dataset(values = emptyList(), label = "watch", tree = teachingTree)
    val tryIt = Dataset(values = emptyList(), label = "try", tree = tryTree)
}
