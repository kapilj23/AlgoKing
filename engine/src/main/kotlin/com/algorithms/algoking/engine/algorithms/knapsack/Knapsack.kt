package com.algorithms.algoking.engine.algorithms.knapsack

import com.algorithms.algoking.engine.core.Algorithm
import com.algorithms.algoking.engine.core.AlgorithmId
import com.algorithms.algoking.engine.core.Dataset
import com.algorithms.algoking.engine.core.KnapsackItem
import com.algorithms.algoking.engine.core.KnapsackProblem
import com.algorithms.algoking.engine.core.Probe
import com.algorithms.algoking.engine.core.Transition
import com.algorithms.algoking.engine.decision.Action
import com.algorithms.algoking.engine.decision.ActionOption
import com.algorithms.algoking.engine.decision.Decision
import com.algorithms.algoking.engine.decision.DecisionKind
import com.algorithms.algoking.engine.event.ExamineRole
import com.algorithms.algoking.engine.event.MeterId
import com.algorithms.algoking.engine.event.Outcome
import com.algorithms.algoking.engine.event.Relation
import com.algorithms.algoking.engine.event.VizEvent
import com.algorithms.algoking.engine.narration.NarrationId
import com.algorithms.algoking.engine.narration.NarrationKey

/**
 * What happens in 0/1 Knapsack.
 *
 * The lesson runs in **two acts** (ADR-053). The first one has no table in it at
 * all: the learner meets a bag, packs it by hand, finds out that the bag they
 * packed is not the best one, and only then is shown the question the table
 * exists to answer. The second act builds the table.
 *
 * The learner answers seven kinds of question:
 *
 * *Act I — the problem*
 * 1. **can you take all of it?** ([AnswerCapacity])
 * 2. **how many times can one item go in?** ([AnswerTimes]) — the 0/1 in the name
 * 3. **what still fits?** ([AnswerFits]) — the room an item leaves behind
 * 4. **which of these two bags is worth more?** ([AnswerBetter]) — where grabbing
 *    the most valuable thing first is refuted, by the learner rather than the copy
 *
 * *Act II — the table*
 * 5. **which cell does TAKE build on?** — a tap on the table ([PickSource])
 * 6. **TAKE or SKIP?** — two buttons ([Take], [Skip])
 * 7. **was this item taken?** — walking back up the table ([MarkTaken], [MarkLeftOut])
 *
 * Everything else is the app's: the arithmetic, the zeros that define the table,
 * row 1, moving between cells, and every cell whose answer repeats one the learner
 * has already given.
 */
sealed interface KnapsackAction : Action {

    /** The next beat of the problem, before any table exists. Mechanical. */
    data object Introduce : KnapsackAction

    /** Whether everything together goes in the bag. */
    data class AnswerCapacity(val all: Boolean) : KnapsackAction

    /** How many times one item may go in — the 0/1 rule, stated three ways. */
    data class AnswerTimes(val rule: TakeRule) : KnapsackAction

    /** Which item still fits once the most valuable one is in. Null means none does. */
    data class AnswerFits(val item: String?) : KnapsackAction

    /** Which of the two packed bags is worth more. */
    data class AnswerBetter(val optimal: Boolean) : KnapsackAction

    /** Row 0 and column 0 become 0: no items, or no room, is worth nothing. Mechanical. */
    data object FillBase : KnapsackAction

    /** Open the next cell. Mechanical. */
    data object Focus : KnapsackAction

    /**
     * Name the cell TAKE builds on. Correct only for `dp[i-1][c-weight]`: the row
     * above, because that row has never seen this item, and the capacity the item
     * leaves behind.
     */
    data class PickSource(val row: Int, val col: Int) : KnapsackAction

    /** Put the item in: the cell becomes `value + dp[i-1][c-weight]`. */
    data object Take : KnapsackAction

    /** Leave it out: the cell copies `dp[i-1][c]`. */
    data object Skip : KnapsackAction

    /** The table is full; start walking back up it. Mechanical. */
    data object BeginTrace : KnapsackAction

    /** This row changed the value, so its item is in the bag. */
    data object MarkTaken : KnapsackAction

    /** This row changed nothing, so its item is not. */
    data object MarkLeftOut : KnapsackAction
}

/** The three knapsacks a learner might think they are being shown. Only one is this one. */
enum class TakeRule {
    /** 0/1: in once, or not at all. */
    ONCE,

    /** Unbounded: as many copies as fit. */
    ANY_NUMBER,

    /** Fractional: half an item, for half the value. */
    FRACTION,
}

enum class KnapsackPhase {
    /** The problem, posed in a bag and four things — no table yet. */
    PROBLEM,

    /** Filling the table, one cell at a time. */
    BUILD,

    /** Walking back up the finished table to find which items were taken. */
    TRACE,

    DONE,
}

/**
 * The beats of the first act, in the order they are shown.
 *
 * Each names **what is on screen**, and poses the question that moves to the next
 * one. Four of the nine ask the learner something; the rest are the app stating
 * what was just settled, which is what gives every NEXT a real visual change
 * (PRODUCT_SPEC.md §4).
 */
enum class IntroBeat {
    /** A bag, and how much it holds. Nothing in it. */
    BAG,

    /** The things that could go in it, each with a weight and a value. */
    ITEMS,

    /** Everything at once is too heavy — so something has to be left behind. */
    TOO_MUCH,

    /** An item goes in once, or not at all. That is the 0/1. */
    ONCE,

    /** The most valuable item is in, and the room it left is the next question. */
    PACKING,

    /** A full bag, packed by hand, and what it is worth. */
    PACKED,

    /** A second full bag, worth more — so the obvious way of packing was wrong. */
    COMPARED,

    /** Every bag there is: 2 to the n of them, doubling with each new item. */
    EVERY_BAG,

    /** One item at a time: TAKE or SKIP, and keep the better. */
    FORK,

    // The table arrives here and is explained before it is filled. `dp[i][c]` is
    // notation, and notation before meaning is the thing ADR-053 exists to stop —
    // so the grid is shown, then what one box means, and only then its name.

    /** An empty grid. Every box will hold one number. */
    GRID,

    /** A row and a column, crossing: what the box where they meet means. */
    AXES,

    /** The same box, and the shorthand that names it. */
    NAME,
}

enum class Choice { TAKE, SKIP }

data class TablePos(val row: Int, val col: Int)

/** How the cell that was just written got its value — for the picture and the copy. */
data class Resolution(
    val pos: TablePos,
    val choice: Choice,
    val exclude: Int,
    /** Null when the item did not fit, so there was no TAKE to weigh. */
    val include: Int?,
    val source: TablePos?,
)

/**
 * Immutable state — the single source of truth.
 *
 * [table] is the whole model of the second act. Everything a screen shows — the
 * current item, whether it fits, both candidates, the right answer, the bag — is
 * derived below, and **no Composable adds, compares or indexes anything**.
 */
data class KnapsackState(
    val problem: KnapsackProblem,
    val phase: KnapsackPhase,
    val intro: IntroBeat,
    /** `(items + 1) x (capacity + 1)`. Null means not computed yet. */
    val table: List<List<Int?>>,
    /** The cell being decided while building. `row > items` once the table is full. */
    val row: Int,
    val col: Int,
    /** True once the current cell has been opened. */
    val focused: Boolean,
    /** TAKE's cell, once it has been named. */
    val source: TablePos?,
    /** The cell most recently written, kept until the next one is opened. */
    val resolved: Resolution?,
    /** While tracing: the row being read, and the capacity still to explain. */
    val traceRow: Int,
    val traceCap: Int,
    /** Per item: taken, left out, or not decided yet. */
    val taken: List<Boolean?>,
) {
    val items: List<KnapsackItem> get() = problem.items
    val capacity: Int get() = problem.capacity
    val itemCount: Int get() = items.size
    val columns: Int get() = capacity + 1

    fun slotOf(row: Int, col: Int): Int = row * columns + col
    fun slotOf(pos: TablePos): Int = slotOf(pos.row, pos.col)

    fun valueAt(row: Int, col: Int): Int? = table.getOrNull(row)?.getOrNull(col)
    fun valueAt(pos: TablePos): Int? = valueAt(pos.row, pos.col)

    // -- Act I ----------------------------------------------------------------

    val posing: Boolean get() = phase == KnapsackPhase.PROBLEM

    /** What everything together weighs, against what the bag holds. */
    val totalWeight: Int get() = problem.totalWeight

    /** The most valuable item — what packing by hand reaches for first. */
    val firstPick: KnapsackItem? get() = problem.greedyBag.firstOrNull()

    /** The room left once [firstPick] is in. */
    val roomLeft: Int get() = capacity - (firstPick?.weight ?: 0)

    /**
     * Everything that still fits in that room.
     *
     * The authored bags leave **exactly one**, so the question has exactly one
     * answer; a test holds them to it.
     */
    val stillFits: List<KnapsackItem>
        get() = items.filter { it != firstPick && it.weight <= roomLeft }

    /** The bag being packed by hand, as far as the first act has got. */
    val handBag: List<KnapsackItem>
        get() = when {
            !posing -> emptyList()
            intro < IntroBeat.PACKING -> emptyList()
            intro == IntroBeat.PACKING -> problem.greedyBag.take(1)
            else -> problem.greedyBag
        }

    /** The bag the learner is asked to find, once the hand-packed one is on screen. */
    val rivalBag: List<KnapsackItem>
        get() = if (posing && intro >= IntroBeat.COMPARED) problem.bestBag else emptyList()

    val handValue: Int get() = problem.greedyValue

    val bestValue: Int get() = problem.bestValue

    /** How many bags trying every combination would mean. */
    val bagCount: Long get() = problem.bagCount

    /** What grabbing the most valuable item first packs. */
    val greedyPick: List<KnapsackItem> get() = problem.greedyBag

    val greedyValue: Int get() = problem.greedyValue

    /** True when everything together is too heavy — the fact the lesson exists for. */
    val overloaded: Boolean get() = totalWeight > capacity

    /**
     * Whether the first act's packing story can honestly be told about this bag.
     *
     * It needs three things to be true, and the authored bags are chosen so that
     * they are (a test holds them to it): something fits, **exactly one** other
     * thing still fits beside it — so *"what else fits?"* has one answer — and the
     * bag greed packs is beaten by a different one, so *"which is worth more?"* is
     * a real question rather than the same bag twice.
     *
     * A bag that cannot support it, which in practice means a synthetic one, skips
     * those three beats rather than asking a question with no answer. The rule is
     * the one `KnapsackProblem` already follows for its own arguments: a lesson
     * that cannot be taught is not taught, rather than taught wrongly.
     */
    val storyHolds: Boolean
        get() = firstPick != null && stillFits.size == 1 &&
            problem.greedyBag != problem.bestBag && problem.greedyValue < problem.bestValue

    /**
     * The box the table's introduction points at, and the row and column that
     * cross there.
     *
     * Row 2 wherever there is one, because row 1 is the app's and a row that can
     * only hold one item is a thin example of "which items you may use"; the last
     * column, because a full bag is the one the learner already has in mind. It is
     * also, in both authored bags, the cell the reuse beat comes back to.
     */
    val teachingCell: TablePos
        get() = TablePos(minOf(2, itemCount).coerceAtLeast(1), capacity)

    /** The items that row allows — the meaning of a row, as a list. */
    val teachingItems: List<KnapsackItem> get() = items.take(teachingCell.row)

    /** True while the table is on screen but has nothing in it yet. */
    val explainingTable: Boolean get() = posing && intro >= IntroBeat.GRID

    // -- Act II ---------------------------------------------------------------

    /** True while there is a real cell to decide. */
    val building: Boolean get() = phase == KnapsackPhase.BUILD && row in 1..itemCount

    val position: TablePos get() = TablePos(row, col)

    /** The item this row adds. Null outside the build. */
    val item: KnapsackItem? get() = if (building) items[row - 1] else null

    val fits: Boolean get() = item?.let { it.weight <= col } ?: false

    /** SKIP: the best without this item. */
    val exclude: Int? get() = if (building) valueAt(row - 1, col) else null

    /** Where TAKE reads from — the row above, at the capacity the item leaves. */
    val takeSource: TablePos?
        get() {
            val it = item ?: return null
            return if (it.weight <= col) TablePos(row - 1, col - it.weight) else null
        }

    /** TAKE: this item, plus the best of what is left. Null when it does not fit. */
    val include: Int?
        get() {
            val it = item ?: return null
            val sub = takeSource?.let(::valueAt) ?: return null
            return it.value + sub
        }

    /**
     * **The recurrence, in one line.** SKIP on a tie: the cell keeps the row above,
     * which is exactly what backtracking reads as "not taken" — one rule, used in
     * both directions. The authored bags have no ties at all, so a learner is never
     * marked wrong by it.
     */
    val bestChoice: Choice?
        get() {
            val ex = exclude ?: return null
            val inc = include ?: return Choice.SKIP
            return if (inc > ex) Choice.TAKE else Choice.SKIP
        }

    val buildComplete: Boolean
        get() = phase == KnapsackPhase.TRACE || phase == KnapsackPhase.DONE ||
            (phase == KnapsackPhase.BUILD && row > itemCount)

    /** The answer — the best value for every item and the whole bag. */
    val best: Int? get() = valueAt(itemCount, capacity)

    /**
     * The bag behind `dp[row][col]`, read back from the table: where a cell differs
     * from the one above it, that row's item went in. Empty if the cells it needs
     * are not computed yet.
     */
    fun bagFor(row: Int, col: Int): List<KnapsackItem> {
        val bag = ArrayDeque<KnapsackItem>()
        var c = col
        for (r in row downTo 1) {
            val here = valueAt(r, c) ?: return emptyList()
            val above = valueAt(r - 1, c) ?: return emptyList()
            if (here != above) {
                bag.addFirst(items[r - 1])
                c -= items[r - 1].weight
                if (c < 0) return emptyList()
            }
        }
        return bag.toList()
    }

    /** The item whose row is being read during the walk back. */
    val traceItem: KnapsackItem?
        get() = if (phase == KnapsackPhase.TRACE) items.getOrNull(traceRow - 1) else null

    val tracePosition: TablePos?
        get() = if (phase == KnapsackPhase.TRACE) TablePos(traceRow, traceCap) else null

    /** Whether the row being read changed the value — the right answer to question 7. */
    val traceTaken: Boolean?
        get() {
            if (phase != KnapsackPhase.TRACE) return null
            val here = valueAt(traceRow, traceCap) ?: return null
            val above = valueAt(traceRow - 1, traceCap) ?: return null
            return here != above
        }

    /** The cells the walk back has already read, answer first. */
    val tracedPath: List<TablePos>
        get() {
            val path = mutableListOf<TablePos>()
            var c = capacity
            for (r in itemCount downTo 1) {
                val decided = taken.getOrNull(r - 1) ?: break
                path += TablePos(r, c)
                if (decided) c -= items[r - 1].weight
            }
            return path
        }

    /** Everything marked taken so far. */
    val bag: List<KnapsackItem> get() = items.filterIndexed { i, _ -> taken.getOrNull(i) == true }
}

/**
 * 0/1 Knapsack — an Advanced lesson, and the first about dynamic programming.
 *
 * ```
 * dp[i][c] = the best value using only the first i items, with capacity c
 *
 * dp[0][c] = dp[i][0] = 0
 * weight[i] > c   ->  dp[i][c] = dp[i-1][c]
 * otherwise       ->  dp[i][c] = max(dp[i-1][c], value[i] + dp[i-1][c - weight[i]])
 * ```
 *
 * ### What the learner has to understand, in the order they meet it
 *
 * 1. **You cannot take everything**, so every item is a decision.
 * 2. **0/1 means once.** Not twice, and not half of one.
 * 3. **Taking an item spends room**, and the room left decides the rest.
 * 4. **The obvious bag is not the best bag** — the learner packs one by hand and is
 *    then shown a better one. This is the reason the method exists, and until
 *    ADR-053 the lesson asserted it instead of demonstrating it.
 * 5. **Trying every bag is 2^n**, so something cleverer is needed.
 * 6. **TAKE or SKIP, keep the better** — the recurrence, in words, before symbols.
 * 7. **A cell is a smaller bag, solved once**, and TAKE builds on the row *above*,
 *    which has never seen this item — that is where 0/1 lives in the table.
 * 8. **The last number is not the answer.** Which items were taken is recovered by
 *    walking back up.
 *
 * ### Which beats the learner answers
 *
 * Rules, not a list of cells, so they hold for any dataset:
 *
 * - **Act I asks four of its nine beats**, and states the rest;
 * - row 1 is the app's — the row above is all zeros, so TAKE-if-it-fits has one
 *   legal answer;
 * - a cell whose item does not fit is the app's, **except the first boundary in the
 *   table**, where "does it fit?" is asked once;
 * - naming TAKE's cell is asked **once**, at the first cell in the table whose TAKE
 *   reads a value worth more than 0 — the moment a smaller answer is reused, which
 *   is the only reason that question exists;
 * - **TAKE or SKIP is asked on the cell that decides each row** — the last column,
 *   which is where the answer is eventually read from — and on the cell where
 *   TAKE's source was just named;
 * - every row of the walk back is asked, because each one is a reading.
 *
 * Everything else fills itself, and that is ADR-053's other half: a learner who has
 * answered TAKE or SKIP three times has understood the recurrence, and one who
 * answers it twenty-five times has been drilled.
 *
 * A beat the learner is not taught is `Mechanical`; `LessonController` already
 * applies those, so WATCH and TRY run this one machine with no mode flag.
 *
 * **A wrong action is refused**, never applied: a wrong value in one cell would
 * quietly poison every cell that reads it.
 *
 * **Time O(n x W), space O(n x W)** — `(n + 1) x (W + 1)` cells, each decided once.
 */
class KnapsackAlgorithm : Algorithm<KnapsackState, KnapsackAction> {

    override val id = AlgorithmId.ZERO_ONE_KNAPSACK

    override fun initial(dataset: Dataset): KnapsackState {
        val problem = dataset.knapsack ?: KnapsackProblem(emptyList(), 0)
        return KnapsackState(
            problem = problem,
            phase = KnapsackPhase.PROBLEM,
            intro = IntroBeat.BAG,
            table = List(problem.items.size + 1) { List<Int?>(problem.capacity + 1) { null } },
            row = 1,
            col = 1,
            focused = false,
            source = null,
            resolved = null,
            traceRow = 0,
            traceCap = 0,
            taken = List(problem.items.size) { null },
        )
    }

    override fun probe(state: KnapsackState): Probe<KnapsackAction> = when (state.phase) {
        KnapsackPhase.PROBLEM -> probeProblem(state)
        KnapsackPhase.BUILD -> probeBuild(state)
        KnapsackPhase.TRACE -> Probe.Decide(traceDecision(state))
        KnapsackPhase.DONE -> Probe.Terminal(Outcome.Completed(correct = true))
    }

    // -- Act I ----------------------------------------------------------------
    /**
     * A bag with no room, or nothing to put in it, has no story to tell: every
     * question below would have no answer. It states what it can and goes straight
     * to the table, where [fillBase] finishes it.
     */
    private fun probeProblem(state: KnapsackState): Probe<KnapsackAction> {
        if (state.itemCount == 0 || state.capacity == 0) {
            return Probe.Mechanical(KnapsackAction.FillBase)
        }
        return when (asksAt(state)) {
            IntroBeat.ITEMS -> Probe.Decide(capacityDecision(state))
            IntroBeat.TOO_MUCH -> Probe.Decide(timesDecision(state))
            IntroBeat.ONCE -> Probe.Decide(fitsDecision(state))
            IntroBeat.PACKED -> Probe.Decide(betterDecision(state))
            else -> if (state.intro == IntroBeat.NAME) {
                Probe.Mechanical(KnapsackAction.FillBase)
            } else {
                Probe.Mechanical(KnapsackAction.Introduce)
            }
        }
    }

    /**
     * Which beat, if any, this state is asking about.
     *
     * The four questions of the first act each need something to be true of the
     * bag: the first only means anything when everything together is too heavy, and
     * the last two need the packing story to hold. A beat whose question cannot be
     * asked is stated instead, which is the same move `stepsFor` makes in RSA
     * (ADR-052) and for the same reason — a question with no answer is worse than
     * a sentence.
     */
    private fun asksAt(state: KnapsackState): IntroBeat? = when (state.intro) {
        IntroBeat.ITEMS -> IntroBeat.ITEMS.takeIf { state.overloaded }
        IntroBeat.TOO_MUCH -> IntroBeat.TOO_MUCH
        IntroBeat.ONCE -> IntroBeat.ONCE.takeIf { state.storyHolds }
        IntroBeat.PACKED -> IntroBeat.PACKED.takeIf { state.storyHolds }
        else -> null
    }

    /** The bag holds 5 kg and everything weighs 10. How much of it can you take? */
    private fun capacityDecision(state: KnapsackState): Decision<KnapsackAction> {
        val options = listOf(
            ActionOption<KnapsackAction>(
                KnapsackAction.AnswerCapacity(all = true),
                NarrationKey(NarrationId.KN_OPTION_ALL),
            ),
            ActionOption<KnapsackAction>(
                KnapsackAction.AnswerCapacity(all = false),
                NarrationKey(NarrationId.KN_OPTION_SOME),
            ),
        )
        val look = NarrationKey(
            NarrationId.KN_RETRY_CAPACITY_LOOK,
            listOf(state.totalWeight, state.capacity),
        )
        return Decision(
            kind = DecisionKind.OPTIONS,
            prompt = NarrationKey(NarrationId.KN_ASK_CAPACITY, listOf(state.capacity)),
            options = options,
            correct = KnapsackAction.AnswerCapacity(all = false),
            focus = emptyList(),
            hint = NarrationKey(NarrationId.KN_HINT_CAPACITY),
            guidance = listOf(
                look,
                NarrationKey(
                    NarrationId.KN_RETRY_CAPACITY_ASK,
                    listOf(state.totalWeight, state.capacity),
                ),
                NarrationKey(
                    NarrationId.KN_RETRY_CAPACITY_EXPLAIN,
                    listOf(state.totalWeight, state.capacity, state.totalWeight - state.capacity),
                ),
            ),
            minimalFeedback = look,
            whyWrong = mapOf(
                KnapsackAction.AnswerCapacity(all = true) to NarrationKey(
                    NarrationId.KN_WHY_ALL_FITS,
                    listOf(state.totalWeight, state.capacity),
                ),
            ),
            correctFeedback = NarrationKey(
                NarrationId.KN_CORRECT_CAPACITY,
                listOf(state.totalWeight, state.capacity),
            ),
            hintLadder = listOf(NarrationKey(NarrationId.KN_HINT_CAPACITY)),
            autoInTry = false,
        )
    }

    /**
     * How many times can the Laptop go in?
     *
     * The two wrong answers are the two **other** knapsacks — unbounded and
     * fractional — so the learner is told what this lesson is not, at the point
     * where the name would otherwise be a piece of trivia.
     */
    private fun timesDecision(state: KnapsackState): Decision<KnapsackAction> {
        val item = state.items.first()
        val options = listOf(
            ActionOption<KnapsackAction>(
                KnapsackAction.AnswerTimes(TakeRule.ONCE),
                NarrationKey(NarrationId.KN_OPTION_ONCE),
            ),
            ActionOption<KnapsackAction>(
                KnapsackAction.AnswerTimes(TakeRule.ANY_NUMBER),
                NarrationKey(NarrationId.KN_OPTION_MANY),
            ),
            ActionOption<KnapsackAction>(
                KnapsackAction.AnswerTimes(TakeRule.FRACTION),
                NarrationKey(NarrationId.KN_OPTION_FRACTION),
            ),
        )
        val look = NarrationKey(NarrationId.KN_RETRY_TIMES_LOOK, listOf(item.name))
        return Decision(
            kind = DecisionKind.OPTIONS,
            prompt = NarrationKey(NarrationId.KN_ASK_TIMES, listOf(item.name)),
            options = options,
            correct = KnapsackAction.AnswerTimes(TakeRule.ONCE),
            focus = emptyList(),
            hint = NarrationKey(NarrationId.KN_HINT_TIMES),
            guidance = listOf(
                look,
                NarrationKey(NarrationId.KN_RETRY_TIMES_ASK),
                NarrationKey(NarrationId.KN_RETRY_TIMES_EXPLAIN, listOf(item.name)),
            ),
            minimalFeedback = look,
            whyWrong = mapOf(
                KnapsackAction.AnswerTimes(TakeRule.ANY_NUMBER) to NarrationKey(
                    NarrationId.KN_WHY_MANY,
                    listOf(item.name),
                ),
                KnapsackAction.AnswerTimes(TakeRule.FRACTION) to NarrationKey(
                    NarrationId.KN_WHY_FRACTION,
                    listOf(item.name, item.value),
                ),
            ),
            correctFeedback = NarrationKey(NarrationId.KN_CORRECT_TIMES, listOf(item.name)),
            hintLadder = listOf(NarrationKey(NarrationId.KN_HINT_TIMES)),
            autoInTry = false,
        )
    }

    /**
     * The Camera is in, 1 kg is left — what else still fits?
     *
     * Every other item is an option, and so is "nothing", so the learner has to
     * read the weights rather than pick the only plausible-looking card.
     */
    private fun fitsDecision(state: KnapsackState): Decision<KnapsackAction> {
        val picked = requireNotNull(state.firstPick)
        val room = state.roomLeft
        val answer = state.stillFits.firstOrNull()
        val options = state.items.filter { it != picked }.map { item ->
            ActionOption<KnapsackAction>(
                KnapsackAction.AnswerFits(item.name),
                NarrationKey(NarrationId.KN_OPTION_ITEM, listOf(item.name, item.weight)),
            )
        } + ActionOption<KnapsackAction>(
            KnapsackAction.AnswerFits(null),
            NarrationKey(NarrationId.KN_OPTION_NOTHING),
        )
        val look = NarrationKey(
            NarrationId.KN_RETRY_FITS_LOOK,
            listOf(state.capacity, picked.weight, room),
        )
        return Decision(
            kind = DecisionKind.OPTIONS,
            prompt = NarrationKey(NarrationId.KN_ASK_FITS, listOf(picked.name, room)),
            options = options,
            correct = KnapsackAction.AnswerFits(answer?.name),
            focus = emptyList(),
            hint = NarrationKey(NarrationId.KN_HINT_FITS, listOf(room)),
            guidance = listOf(
                look,
                NarrationKey(NarrationId.KN_RETRY_FITS_ASK, listOf(room)),
                NarrationKey(
                    NarrationId.KN_RETRY_FITS_EXPLAIN,
                    listOf(answer?.name ?: "Nothing", answer?.weight ?: 0, room),
                ),
            ),
            minimalFeedback = look,
            whyWrong = buildMap {
                for (item in state.items) {
                    if (item == picked || item.name == answer?.name) continue
                    put(
                        KnapsackAction.AnswerFits(item.name),
                        NarrationKey(
                            NarrationId.KN_WHY_TOO_HEAVY,
                            listOf(item.name, item.weight, room),
                        ),
                    )
                }
                if (answer != null) {
                    put(
                        KnapsackAction.AnswerFits(null),
                        NarrationKey(
                            NarrationId.KN_WHY_SOMETHING_FITS,
                            listOf(answer.name, answer.weight, room),
                        ),
                    )
                }
            },
            correctFeedback = NarrationKey(
                NarrationId.KN_CORRECT_FITS,
                listOf(
                    answer?.name ?: "Nothing",
                    answer?.weight ?: 0,
                    room - (answer?.weight ?: 0),
                ),
            ),
            hintLadder = listOf(NarrationKey(NarrationId.KN_HINT_FITS, listOf(room))),
            autoInTry = false,
        )
    }

    /**
     * Two bags. Which one is worth more?
     *
     * **The whole lesson turns on this beat.** The first bag is the one the learner
     * has just packed by taking the most valuable thing first; the second is the
     * best bag there is. Adding up two pairs of numbers is arithmetic a beginner
     * can do, and it is the only honest way to establish that the obvious strategy
     * is wrong — the alternative is a sentence asserting it, which is what ADR-053
     * replaced.
     */
    private fun betterDecision(state: KnapsackState): Decision<KnapsackAction> {
        val hand = state.problem.greedyBag
        val best = state.problem.bestBag
        val handNames = hand.joinToString(" + ") { it.name }
        val bestNames = best.joinToString(" + ") { it.name }
        val handSum = hand.joinToString(" + ") { it.value.toString() }
        val bestSum = best.joinToString(" + ") { it.value.toString() }
        val options = listOf(
            ActionOption<KnapsackAction>(
                KnapsackAction.AnswerBetter(optimal = false),
                NarrationKey(NarrationId.KN_OPTION_BAG, listOf(handNames)),
            ),
            ActionOption<KnapsackAction>(
                KnapsackAction.AnswerBetter(optimal = true),
                NarrationKey(NarrationId.KN_OPTION_BAG, listOf(bestNames)),
            ),
        )
        val look = NarrationKey(NarrationId.KN_RETRY_BETTER_LOOK, listOf(handSum, bestSum))
        return Decision(
            kind = DecisionKind.OPTIONS,
            prompt = NarrationKey(NarrationId.KN_ASK_BETTER),
            options = options,
            correct = KnapsackAction.AnswerBetter(optimal = true),
            focus = emptyList(),
            hint = NarrationKey(NarrationId.KN_HINT_BETTER),
            guidance = listOf(
                look,
                NarrationKey(NarrationId.KN_RETRY_BETTER_ASK),
                NarrationKey(
                    NarrationId.KN_RETRY_BETTER_EXPLAIN,
                    listOf(bestNames, state.bestValue, handNames, state.handValue),
                ),
            ),
            minimalFeedback = look,
            whyWrong = mapOf(
                KnapsackAction.AnswerBetter(optimal = false) to NarrationKey(
                    NarrationId.KN_WHY_WORSE_BAG,
                    listOf(handNames, state.handValue, bestNames, state.bestValue),
                ),
            ),
            correctFeedback = NarrationKey(
                NarrationId.KN_CORRECT_BETTER,
                listOf(bestNames, state.bestValue, state.handValue),
            ),
            hintLadder = listOf(NarrationKey(NarrationId.KN_HINT_BETTER)),
            autoInTry = false,
        )
    }

    // -- Act II ---------------------------------------------------------------

    private fun probeBuild(state: KnapsackState): Probe<KnapsackAction> {
        val item = state.item ?: return Probe.Mechanical(KnapsackAction.BeginTrace)
        if (!state.focused) return Probe.Mechanical(KnapsackAction.Focus)

        if (!state.fits) {
            return if (state.position == fitBoundaryCell(state)) {
                Probe.Decide(choiceDecision(state))
            } else {
                Probe.Mechanical(KnapsackAction.Skip)
            }
        }

        val source = requireNotNull(state.takeSource)
        if (state.source == null) {
            return if (state.position == sourceCell(state)) {
                Probe.Decide(sourceDecision(state))
            } else {
                Probe.Mechanical(KnapsackAction.PickSource(source.row, source.col))
            }
        }

        return if (isChoiceAsked(state)) {
            Probe.Decide(choiceDecision(state))
        } else {
            Probe.Mechanical(actionFor(requireNotNull(state.bestChoice)))
        }
    }

    /**
     * The one cell where "does it fit?" is a judgement: the first column in the
     * table where an item is one kilo too heavy.
     *
     * Every smaller column is the same answer again and every later row asks the
     * same thing about a different number, so it is asked once and then becomes a
     * rule the learner has seen — ADR-025's standard, show the shape and then stop.
     */
    private fun fitBoundaryCell(state: KnapsackState): TablePos? {
        for (r in 2..state.itemCount) {
            val boundary = state.items[r - 1].weight - 1
            if (boundary in 1..state.capacity) return TablePos(r, boundary)
        }
        return null
    }

    /**
     * The one cell where naming TAKE's cell is a judgement: the first TAKE, in
     * reading order, that builds on a cell worth more than 0.
     *
     * Before that the question is "which zero?", which teaches nothing; at that
     * cell it is "which smaller answer does this reuse?", which is the whole idea.
     * A cell its item exactly fills is skipped for the same reason — the room left
     * is 0, and column 0 is a definition rather than a result.
     *
     * The rows it reads are always computed by the time the scan reaches them, so
     * the answer stops changing once row 1 is filled and never moves again.
     */
    private fun sourceCell(state: KnapsackState): TablePos? {
        for (r in 2..state.itemCount) {
            val weight = state.items[r - 1].weight
            for (c in weight..state.capacity) {
                if (c == weight) continue
                val sub = state.valueAt(r - 1, c - weight) ?: return null
                if (sub > 0) return TablePos(r, c)
            }
        }
        return null
    }

    /**
     * TAKE or SKIP is the learner's on the cell that decides a row — the last
     * column, which is where the answer is eventually read from — and on the cell
     * where they have just named TAKE's source, because having named it they should
     * be the one to finish it.
     */
    private fun isChoiceAsked(state: KnapsackState): Boolean =
        state.row >= 2 && (state.col == state.capacity || state.position == sourceCell(state))

    private fun actionFor(choice: Choice): KnapsackAction = when (choice) {
        Choice.TAKE -> KnapsackAction.Take
        Choice.SKIP -> KnapsackAction.Skip
    }

    /**
     * If you TAKE this item, which cell holds the best for what is left?
     *
     * Every computed cell is tappable — a shortlist would do the reasoning — and
     * each wrong one is a named mistake.
     */
    private fun sourceDecision(state: KnapsackState): Decision<KnapsackAction> {
        val item = requireNotNull(state.item)
        val row = state.row
        val col = state.col
        val correct = requireNotNull(state.takeSource)
        val sub = requireNotNull(state.valueAt(correct))
        val left = col - item.weight
        val previousItem = state.items[row - 2]

        val options = buildList {
            for (r in 0..state.itemCount) {
                for (c in 0..state.capacity) {
                    if (state.valueAt(r, c) == null) continue
                    add(
                        ActionOption<KnapsackAction>(
                            action = KnapsackAction.PickSource(r, c),
                            label = NarrationKey(NarrationId.KN_OPTION_CELL, listOf(r, c)),
                            slot = state.slotOf(r, c),
                        ),
                    )
                }
            }
        }
        val correctAction = KnapsackAction.PickSource(correct.row, correct.col)

        return Decision(
            kind = DecisionKind.CELL,
            prompt = NarrationKey(NarrationId.KN_ASK_SOURCE, listOf(item.name)),
            options = options,
            correct = correctAction,
            focus = listOf(state.slotOf(row - 1, col)),
            hint = NarrationKey(NarrationId.KN_HINT_SOURCE),
            guidance = listOf(
                NarrationKey(NarrationId.KN_RETRY_SOURCE_LOOK, listOf(item.name, item.weight)),
                NarrationKey(NarrationId.KN_RETRY_SOURCE_ASK, listOf(col, item.weight, item.name)),
                NarrationKey(
                    NarrationId.KN_RETRY_SOURCE_EXPLAIN,
                    listOf(item.name, col, item.weight, left, row - 1),
                ),
            ),
            minimalFeedback = NarrationKey(
                NarrationId.KN_RETRY_SOURCE_LOOK,
                listOf(item.name, item.weight),
            ),
            whyWrong = buildMap {
                for (option in options) {
                    val tapped = option.action as KnapsackAction.PickSource
                    if (tapped == correctAction) continue
                    val key = when {
                        // The unbounded recurrence: this row may already hold the item.
                        tapped.row == row -> NarrationKey(
                            NarrationId.KN_WHY_SAME_ROW,
                            listOf(row, item.name),
                        )
                        // SKIP's own cell: no room was used.
                        tapped.row == row - 1 && tapped.col == col -> NarrationKey(
                            NarrationId.KN_WHY_SKIP_CELL,
                            listOf(item.name, item.weight),
                        )
                        tapped.row == row - 1 -> NarrationKey(
                            NarrationId.KN_WHY_WRONG_CAPACITY,
                            listOf(item.name, col, item.weight, left, tapped.col),
                        )
                        else -> NarrationKey(
                            NarrationId.KN_WHY_ROW_TOO_HIGH,
                            listOf(tapped.row, previousItem.name),
                        )
                    }
                    put(tapped, key)
                }
            },
            correctFeedback = NarrationKey(
                NarrationId.KN_CORRECT_SOURCE,
                listOf(item.name, left, row - 1, sub),
            ),
            hintLadder = listOf(NarrationKey(NarrationId.KN_HINT_SOURCE)),
            autoInTry = false,
        )
    }

    /** SKIP or TAKE? The recurrence's max — and at a boundary, whether it fits at all. */
    private fun choiceDecision(state: KnapsackState): Decision<KnapsackAction> {
        val item = requireNotNull(state.item)
        val col = state.col
        val exclude = requireNotNull(state.exclude)
        val correct = actionFor(requireNotNull(state.bestChoice))
        val options = listOf(
            ActionOption<KnapsackAction>(
                KnapsackAction.Take,
                NarrationKey(NarrationId.KN_OPTION_TAKE),
            ),
            ActionOption<KnapsackAction>(
                KnapsackAction.Skip,
                NarrationKey(NarrationId.KN_OPTION_SKIP),
            ),
        )
        val focus = listOfNotNull(
            state.slotOf(state.row - 1, col),
            state.source?.let(state::slotOf),
        )

        if (!state.fits) {
            return Decision(
                kind = DecisionKind.OPTIONS,
                prompt = NarrationKey(NarrationId.KN_ASK_CHOICE, listOf(item.name)),
                options = options,
                correct = KnapsackAction.Skip,
                focus = focus,
                hint = NarrationKey(NarrationId.KN_HINT_CHOICE),
                guidance = listOf(
                    NarrationKey(NarrationId.KN_RETRY_FIT_LOOK, listOf(item.name, item.weight, col)),
                    NarrationKey(NarrationId.KN_RETRY_FIT_ASK, listOf(item.weight, col)),
                    NarrationKey(
                        NarrationId.KN_RETRY_FIT_EXPLAIN,
                        listOf(item.name, item.weight, col, exclude),
                    ),
                ),
                minimalFeedback = NarrationKey(
                    NarrationId.KN_RETRY_FIT_LOOK,
                    listOf(item.name, item.weight, col),
                ),
                whyWrong = mapOf(
                    KnapsackAction.Take to NarrationKey(
                        NarrationId.KN_WHY_NO_FIT,
                        listOf(item.name, item.weight, col),
                    ),
                ),
                correctFeedback = NarrationKey(
                    NarrationId.KN_CORRECT_NO_FIT,
                    listOf(item.name, col, exclude),
                ),
                hintLadder = listOf(NarrationKey(NarrationId.KN_HINT_CHOICE)),
                autoInTry = false,
            )
        }

        val sub = requireNotNull(state.source?.let(state::valueAt))
        val include = requireNotNull(state.include)
        val takeWins = correct == KnapsackAction.Take

        return Decision(
            kind = DecisionKind.OPTIONS,
            prompt = NarrationKey(NarrationId.KN_ASK_CHOICE, listOf(item.name)),
            options = options,
            correct = correct,
            focus = focus,
            hint = NarrationKey(NarrationId.KN_HINT_CHOICE),
            guidance = listOf(
                NarrationKey(NarrationId.KN_RETRY_CHOICE_LOOK, listOf(include, exclude)),
                NarrationKey(NarrationId.KN_RETRY_CHOICE_ASK),
                NarrationKey(
                    if (takeWins) {
                        NarrationId.KN_RETRY_CHOICE_EXPLAIN_TAKE
                    } else {
                        NarrationId.KN_RETRY_CHOICE_EXPLAIN_SKIP
                    },
                    listOf(include, exclude, item.name),
                ),
            ),
            minimalFeedback = NarrationKey(
                NarrationId.KN_RETRY_CHOICE_LOOK,
                listOf(include, exclude),
            ),
            whyWrong = if (takeWins) {
                mapOf(
                    KnapsackAction.Skip to NarrationKey(
                        NarrationId.KN_WHY_SKIP_LOSES,
                        listOf(exclude, item.value, sub, include),
                    ),
                )
            } else {
                mapOf(
                    KnapsackAction.Take to NarrationKey(
                        NarrationId.KN_WHY_TAKE_LOSES,
                        listOf(item.value, sub, include, exclude, item.name),
                    ),
                )
            },
            correctFeedback = if (takeWins) {
                NarrationKey(
                    NarrationId.KN_CORRECT_TAKE,
                    listOf(item.value, sub, include, exclude, item.name),
                )
            } else {
                NarrationKey(NarrationId.KN_CORRECT_SKIP, listOf(include, exclude, item.name))
            },
            hintLadder = listOf(NarrationKey(NarrationId.KN_HINT_CHOICE)),
            autoInTry = false,
        )
    }

    /** Was this item taken? The same question as TAKE or SKIP, asked backwards. */
    private fun traceDecision(state: KnapsackState): Decision<KnapsackAction> {
        val item = requireNotNull(state.traceItem)
        val r = state.traceRow
        val c = state.traceCap
        val here = requireNotNull(state.valueAt(r, c))
        val above = requireNotNull(state.valueAt(r - 1, c))
        val wasTaken = requireNotNull(state.traceTaken)
        val left = c - item.weight

        return Decision(
            kind = DecisionKind.OPTIONS,
            prompt = NarrationKey(NarrationId.KN_ASK_TRACE, listOf(item.name)),
            options = listOf(
                ActionOption<KnapsackAction>(
                    KnapsackAction.MarkTaken,
                    NarrationKey(NarrationId.KN_OPTION_TAKEN),
                ),
                ActionOption<KnapsackAction>(
                    KnapsackAction.MarkLeftOut,
                    NarrationKey(NarrationId.KN_OPTION_LEFT_OUT),
                ),
            ),
            correct = if (wasTaken) KnapsackAction.MarkTaken else KnapsackAction.MarkLeftOut,
            focus = listOf(state.slotOf(r, c), state.slotOf(r - 1, c)),
            hint = NarrationKey(NarrationId.KN_HINT_TRACE),
            guidance = listOf(
                NarrationKey(NarrationId.KN_RETRY_TRACE_LOOK, listOf(r, c, here, r - 1, above)),
                NarrationKey(NarrationId.KN_RETRY_TRACE_ASK, listOf(item.name)),
                NarrationKey(
                    if (wasTaken) {
                        NarrationId.KN_RETRY_TRACE_EXPLAIN_TAKEN
                    } else {
                        NarrationId.KN_RETRY_TRACE_EXPLAIN_LEFT_OUT
                    },
                    listOf(here, above, item.name),
                ),
            ),
            minimalFeedback = NarrationKey(
                NarrationId.KN_RETRY_TRACE_LOOK,
                listOf(r, c, here, r - 1, above),
            ),
            whyWrong = if (wasTaken) {
                mapOf(
                    KnapsackAction.MarkLeftOut to NarrationKey(
                        NarrationId.KN_WHY_TRACE_CHANGED,
                        listOf(r, c, here, r - 1, above, item.name),
                    ),
                )
            } else {
                mapOf(
                    KnapsackAction.MarkTaken to NarrationKey(
                        NarrationId.KN_WHY_TRACE_SAME,
                        listOf(r, c, here, r - 1, above, item.name),
                    ),
                )
            },
            correctFeedback = if (wasTaken) {
                NarrationKey(NarrationId.KN_CORRECT_TAKEN, listOf(item.name, c, item.weight, left))
            } else {
                NarrationKey(NarrationId.KN_CORRECT_LEFT_OUT, listOf(item.name, c))
            },
            hintLadder = listOf(NarrationKey(NarrationId.KN_HINT_TRACE)),
            autoInTry = false,
        )
    }

    // -- Transitions ----------------------------------------------------------

    override fun apply(
        state: KnapsackState,
        action: KnapsackAction,
    ): Transition<KnapsackState> = when (action) {
        KnapsackAction.Introduce -> introduce(state)
        is KnapsackAction.AnswerCapacity -> answerCapacity(state, action)
        is KnapsackAction.AnswerTimes -> answerTimes(state, action)
        is KnapsackAction.AnswerFits -> answerFits(state, action)
        is KnapsackAction.AnswerBetter -> answerBetter(state, action)
        KnapsackAction.FillBase -> fillBase(state)
        KnapsackAction.Focus -> focus(state)
        is KnapsackAction.PickSource -> pickSource(state, TablePos(action.row, action.col))
        KnapsackAction.Take -> choose(state, Choice.TAKE)
        KnapsackAction.Skip -> choose(state, Choice.SKIP)
        KnapsackAction.BeginTrace -> beginTrace(state)
        KnapsackAction.MarkTaken -> mark(state, taken = true)
        KnapsackAction.MarkLeftOut -> mark(state, taken = false)
    }

    /** The beats of the first act the app states rather than asks. */
    private fun introduce(state: KnapsackState): Transition<KnapsackState> {
        if (!state.posing || state.intro == IntroBeat.NAME || asksAt(state) != null) {
            return refuse(state)
        }
        return advance(state)
    }

    private fun answerCapacity(
        state: KnapsackState,
        action: KnapsackAction.AnswerCapacity,
    ): Transition<KnapsackState> {
        if (!state.posing || asksAt(state) != IntroBeat.ITEMS || action.all) return refuse(state)
        return advance(state)
    }

    private fun answerTimes(
        state: KnapsackState,
        action: KnapsackAction.AnswerTimes,
    ): Transition<KnapsackState> {
        if (!state.posing || asksAt(state) != IntroBeat.TOO_MUCH || action.rule != TakeRule.ONCE) {
            return refuse(state)
        }
        return advance(state)
    }

    private fun answerFits(
        state: KnapsackState,
        action: KnapsackAction.AnswerFits,
    ): Transition<KnapsackState> {
        if (!state.posing || asksAt(state) != IntroBeat.ONCE) return refuse(state)
        if (action.item != state.stillFits.firstOrNull()?.name) return refuse(state)
        return advance(state)
    }

    private fun answerBetter(
        state: KnapsackState,
        action: KnapsackAction.AnswerBetter,
    ): Transition<KnapsackState> {
        if (!state.posing || asksAt(state) != IntroBeat.PACKED || !action.optimal) {
            return refuse(state)
        }
        return advance(state)
    }

    /**
     * One beat forward, with the sentence that beat is for.
     *
     * The narration is attached here rather than at each caller because every beat
     * of the first act makes the same move: settle what was just answered, and show
     * the next thing.
     */
    private fun advance(state: KnapsackState): Transition<KnapsackState> {
        // A beat whose question this bag cannot support is skipped, not asked with
        // no answer. FORK is last and is never skipped, so this always terminates.
        var beat = IntroBeat.entries[state.intro.ordinal + 1]
        while (beat in STORY_BEATS && !state.storyHolds) beat = IntroBeat.entries[beat.ordinal + 1]
        val next = state.copy(intro = beat)
        val narration = when (next.intro) {
            IntroBeat.BAG -> null
            IntroBeat.ITEMS -> NarrationKey(
                NarrationId.KN_INTRO_ITEMS,
                listOf(next.itemCount, next.capacity),
            )
            IntroBeat.TOO_MUCH -> NarrationKey(
                NarrationId.KN_INTRO_TOO_MUCH,
                listOf(next.totalWeight, next.capacity),
            )
            IntroBeat.ONCE -> NarrationKey(NarrationId.KN_INTRO_ONCE)
            IntroBeat.PACKING -> {
                val picked = requireNotNull(next.firstPick)
                NarrationKey(
                    NarrationId.KN_INTRO_PACKING,
                    listOf(picked.name, picked.value, picked.weight, next.roomLeft),
                )
            }
            IntroBeat.PACKED -> NarrationKey(
                NarrationId.KN_INTRO_PACKED,
                listOf(
                    next.handBag.joinToString(" + ") { it.name },
                    next.handBag.sumOf { it.weight },
                    next.handValue,
                ),
            )
            IntroBeat.COMPARED -> NarrationKey(
                NarrationId.KN_INTRO_COMPARED,
                listOf(
                    next.rivalBag.joinToString(" + ") { it.name },
                    next.bestValue,
                    next.handValue,
                ),
            )
            IntroBeat.EVERY_BAG -> NarrationKey(
                NarrationId.KN_INTRO_EVERY_BAG,
                listOf(next.bagCount, next.itemCount),
            )
            IntroBeat.FORK -> NarrationKey(NarrationId.KN_INTRO_FORK)
            IntroBeat.GRID -> NarrationKey(
                NarrationId.KN_INTRO_GRID,
                listOf((next.itemCount + 1) * (next.capacity + 1)),
            )
            IntroBeat.AXES -> NarrationKey(
                NarrationId.KN_INTRO_AXES,
                listOf(
                    next.teachingItems.joinToString(" and ") { it.name },
                    next.teachingCell.col,
                ),
            )
            IntroBeat.NAME -> NarrationKey(
                NarrationId.KN_INTRO_NAME,
                listOf(next.teachingCell.row, next.teachingCell.col),
            )
        }
        val packed = next.handBag.sumOf { it.weight }
        val events = when (next.intro) {
            IntroBeat.PACKING, IntroBeat.PACKED ->
                listOf(VizEvent.Meter(MeterId.REMAINING, (next.capacity - packed).toLong()))
            else -> emptyList()
        }
        return Transition(next, events, narration, correct = true)
    }

    private fun fillBase(state: KnapsackState): Transition<KnapsackState> {
        val degenerate = state.itemCount == 0 || state.capacity == 0
        if (!state.posing || (state.intro != IntroBeat.NAME && !degenerate)) return refuse(state)
        val table = List(state.itemCount + 1) { r ->
            List(state.columns) { c -> if (r == 0 || c == 0) 0 else null }
        }
        // No items, or no room: every cell is a base case and there is nothing to
        // decide. A finished lesson, not an error.
        val next = state.copy(
            phase = if (degenerate) KnapsackPhase.DONE else KnapsackPhase.BUILD,
            intro = IntroBeat.NAME,
            table = table,
            taken = if (degenerate) List(state.itemCount) { false } else state.taken,
        )
        return Transition(
            next = next,
            events = buildList {
                add(VizEvent.Meter(MeterId.REMAINING, state.capacity.toLong()))
                if (degenerate) add(VizEvent.Terminal(Outcome.Completed(correct = true)))
            },
            narration = NarrationKey(NarrationId.KN_BASE),
            correct = true,
        )
    }

    private fun focus(state: KnapsackState): Transition<KnapsackState> {
        if (!state.building || state.focused) return refuse(state)
        return Transition(
            next = state.copy(focused = true, source = null, resolved = null),
            events = listOf(
                VizEvent.Examine(
                    listOf(state.slotOf(state.row - 1, state.col)),
                    ExamineRole.CANDIDATE,
                ),
            ),
            narration = NarrationKey(
                NarrationId.KN_FOCUS,
                listOf(
                    state.row,
                    state.col,
                    state.items.take(state.row).joinToString(", ") { it.name },
                ),
            ),
            correct = true,
        )
    }

    /** Anything but `dp[i-1][c-weight]` is refused — in TRY it never gets here. */
    private fun pickSource(state: KnapsackState, pos: TablePos): Transition<KnapsackState> {
        if (!state.building || !state.focused || state.source != null || pos != state.takeSource) {
            return refuse(state)
        }
        val item = requireNotNull(state.item)
        val sub = requireNotNull(state.valueAt(pos))
        val exclude = requireNotNull(state.exclude)
        return Transition(
            next = state.copy(source = pos),
            events = listOf(VizEvent.Examine(listOf(state.slotOf(pos)), ExamineRole.COMPARING)),
            // States the arithmetic and stops. Which side wins is the question.
            narration = NarrationKey(
                NarrationId.KN_SOURCE,
                listOf(item.value, pos.row, pos.col, sub, item.value + sub, exclude),
            ),
            correct = true,
        )
    }

    /**
     * Writes the cell. Refused unless the choice is the recurrence's: an item that
     * does not fit cannot be taken, and a cell only ever holds the larger bag.
     */
    private fun choose(state: KnapsackState, choice: Choice): Transition<KnapsackState> {
        if (!state.building || !state.focused) return refuse(state)
        if (state.fits && state.source == null) return refuse(state)
        if (choice != state.bestChoice) return refuse(state)

        val item = requireNotNull(state.item)
        val exclude = requireNotNull(state.exclude)
        val include = state.include
        val value = if (choice == Choice.TAKE) requireNotNull(include) else exclude

        val table = state.table.mapIndexed { r, cells ->
            if (r != state.row) {
                cells
            } else {
                cells.mapIndexed { c, v -> if (c == state.col) value else v }
            }
        }
        val lastColumn = state.col >= state.capacity
        val nextRow = if (lastColumn) state.row + 1 else state.row
        val nextCol = if (lastColumn) 1 else state.col + 1
        val skipCell = state.slotOf(state.row - 1, state.col)

        val next = state.copy(
            table = table,
            row = nextRow,
            col = nextCol,
            focused = false,
            source = null,
            resolved = Resolution(state.position, choice, exclude, include, state.source),
        )

        return Transition(
            next = next,
            events = buildList {
                val source = state.source
                if (include != null && source != null) {
                    add(VizEvent.Compare(state.slotOf(source), skipCell, relation(include, exclude)))
                } else {
                    // Did not fit: looked at, and deliberately left alone.
                    add(VizEvent.Hold(listOf(skipCell)))
                }
                if (next.buildComplete) {
                    val answer = next.slotOf(next.itemCount, next.capacity)
                    add(VizEvent.Finalize(answer..answer))
                }
            },
            narration = when {
                choice == Choice.TAKE -> NarrationKey(
                    NarrationId.KN_TOOK,
                    listOf(state.row, state.col, value, item.name),
                )
                include != null -> NarrationKey(
                    NarrationId.KN_SKIPPED,
                    listOf(state.row, state.col, value, item.name, include),
                )
                else -> NarrationKey(
                    NarrationId.KN_COPIED,
                    listOf(state.row, state.col, value, item.name, item.weight),
                )
            },
            correct = true,
        )
    }

    private fun beginTrace(state: KnapsackState): Transition<KnapsackState> {
        if (state.phase != KnapsackPhase.BUILD || state.row <= state.itemCount) return refuse(state)
        val next = state.copy(
            phase = KnapsackPhase.TRACE,
            traceRow = state.itemCount,
            traceCap = state.capacity,
            resolved = null,
            focused = false,
        )
        return Transition(
            next = next,
            events = listOf(
                VizEvent.Examine(
                    listOf(
                        state.slotOf(state.itemCount, state.capacity),
                        state.slotOf(state.itemCount - 1, state.capacity),
                    ),
                    ExamineRole.COMPARING,
                ),
            ),
            narration = NarrationKey(NarrationId.KN_TRACE_BEGIN, listOf(state.best ?: 0)),
            correct = true,
        )
    }

    private fun mark(state: KnapsackState, taken: Boolean): Transition<KnapsackState> {
        if (state.phase != KnapsackPhase.TRACE || state.traceTaken != taken) return refuse(state)
        val item = requireNotNull(state.traceItem)
        val r = state.traceRow
        val c = state.traceCap
        val left = if (taken) c - item.weight else c
        val nextRow = r - 1
        val done = nextRow == 0

        val next = state.copy(
            phase = if (done) KnapsackPhase.DONE else KnapsackPhase.TRACE,
            traceRow = nextRow,
            traceCap = left,
            taken = state.taken.mapIndexed { i, t -> if (i == r - 1) taken else t },
        )
        return Transition(
            next = next,
            events = buildList {
                add(
                    VizEvent.Examine(
                        listOf(state.slotOf(r, c), state.slotOf(r - 1, c)),
                        ExamineRole.COMPARING,
                    ),
                )
                add(VizEvent.Meter(MeterId.REMAINING, left.toLong()))
                if (done) add(VizEvent.Terminal(Outcome.Completed(correct = true)))
            },
            narration = if (taken) {
                NarrationKey(NarrationId.KN_MARKED_TAKEN, listOf(item.name, item.weight, left))
            } else {
                NarrationKey(NarrationId.KN_MARKED_LEFT_OUT, listOf(item.name, c))
            },
            correct = true,
        )
    }

    private fun relation(a: Int, b: Int): Relation = when {
        a < b -> Relation.LESS
        a > b -> Relation.GREATER
        else -> Relation.EQUAL
    }

    private fun refuse(state: KnapsackState) =
        Transition(state, emptyList(), null, correct = false)

    private companion object {
        /** The beats that only exist when a bag can be packed by hand and then beaten. */
        val STORY_BEATS = setOf(
            IntroBeat.PACKING,
            IntroBeat.PACKED,
            IntroBeat.COMPARED,
        )
    }
}
