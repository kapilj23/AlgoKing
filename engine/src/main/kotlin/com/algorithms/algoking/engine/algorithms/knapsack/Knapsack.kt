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
 * The learner answers three questions, and only three:
 *
 * 1. **which cell does TAKE build on?** — a tap on the table ([PickSource])
 * 2. **TAKE or SKIP?** — two buttons ([Take], [Skip])
 * 3. **was this item taken?** — walking back up the table ([MarkTaken], [MarkLeftOut])
 *
 * Everything else is the app's: posing the problem, the zeros that define the
 * table, row 1, moving between cells, and the arithmetic.
 */
sealed interface KnapsackAction : Action {

    /** The next beat of the problem, before any table exists. Mechanical. */
    data object Introduce : KnapsackAction

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

enum class KnapsackPhase {
    /** The problem, posed in words and a bag — no table yet. */
    PROBLEM,

    /** Filling the table, one cell at a time. */
    BUILD,

    /** Walking back up the finished table to find which items were taken. */
    TRACE,

    DONE,
}

/** The beats of the problem, in the order they are shown. */
enum class IntroBeat { ITEMS, RULE, GREEDY, SUBPROBLEM }

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
 * [table] is the whole model. Everything a screen shows — the current item,
 * whether it fits, both candidates, the right answer, the bag — is derived below,
 * and **no Composable adds, compares or indexes anything**.
 */
data class KnapsackState(
    val problem: KnapsackProblem,
    val phase: KnapsackPhase,
    val intro: IntroBeat,
    /** `(items + 1) × (capacity + 1)`. Null means not computed yet. */
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
     * both directions.
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
    val traceItem: KnapsackItem? get() = if (phase == KnapsackPhase.TRACE) items.getOrNull(traceRow - 1) else null

    val tracePosition: TablePos? get() = if (phase == KnapsackPhase.TRACE) TablePos(traceRow, traceCap) else null

    /** Whether the row being read changed the value — the right answer to question 3. */
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

    /**
     * What grabbing the most valuable item first would pack. Computed here so the
     * lesson's claim about greedy is the engine's, and is tested, rather than a
     * sentence in the copy.
     */
    val greedyPick: List<KnapsackItem>
        get() {
            var left = capacity
            val picked = mutableListOf<KnapsackItem>()
            for (candidate in items.sortedByDescending { it.value }) {
                if (candidate.weight <= left) {
                    picked += candidate
                    left -= candidate.weight
                }
            }
            return picked
        }

    val greedyValue: Int get() = greedyPick.sumOf { it.value }

    /** How many bags trying every combination would mean: 2ⁿ. */
    val bagCount: Long get() = if (itemCount >= 62) Long.MAX_VALUE else 1L shl itemCount
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
 * ### What the learner has to understand
 *
 * 1. **0/1 means once.** TAKE builds on the row *above*, which has never seen the
 *    item, so it can never go in twice. Building on the same row is the unbounded
 *    recurrence, and it is the one wrong tap the source question exists to catch.
 * 2. **A cell is a smaller bag, solved once.** `dp[2][5]` reads `dp[1][2]`, which
 *    row 1 already worked out — that reuse is the whole idea.
 * 3. **The last number is not the answer.** Which items were taken is recovered by
 *    walking back up the table.
 *
 * ### Which beats the learner answers
 *
 * Rules, not a list of cells, so they hold for any dataset:
 *
 * - row 1 is the app's — the row above is all zeros, so TAKE-if-it-fits has one
 *   legal answer;
 * - a cell whose item does not fit is the app's, **except** the last one before the
 *   item fits, where "does it fit?" is a real judgement;
 * - naming TAKE's cell is the app's when the item fills the column exactly — the
 *   capacity left is 0, and column 0 is definition;
 * - TAKE or SKIP is always the learner's from row 2 on, and so is every row of the
 *   walk back.
 *
 * A beat the learner is not taught is `Mechanical`; `LessonController` already
 * applies those, so WATCH and TRY run this one machine with no mode flag.
 *
 * **A wrong action is refused**, never applied: a wrong value in one cell would
 * quietly poison every cell that reads it.
 *
 * **Time O(n × W), space O(n × W)** — `(n + 1) × (W + 1)` cells, each decided once.
 */
class KnapsackAlgorithm : Algorithm<KnapsackState, KnapsackAction> {

    override val id = AlgorithmId.ZERO_ONE_KNAPSACK

    override fun initial(dataset: Dataset): KnapsackState {
        val problem = dataset.knapsack ?: KnapsackProblem(emptyList(), 0)
        return KnapsackState(
            problem = problem,
            phase = KnapsackPhase.PROBLEM,
            intro = IntroBeat.ITEMS,
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
        KnapsackPhase.PROBLEM ->
            if (state.intro != IntroBeat.SUBPROBLEM) {
                Probe.Mechanical(KnapsackAction.Introduce)
            } else {
                Probe.Mechanical(KnapsackAction.FillBase)
            }

        KnapsackPhase.BUILD -> probeBuild(state)
        KnapsackPhase.TRACE -> Probe.Decide(traceDecision(state))
        KnapsackPhase.DONE -> Probe.Terminal(Outcome.Completed(correct = true))
    }

    private fun probeBuild(state: KnapsackState): Probe<KnapsackAction> {
        val item = state.item ?: return Probe.Mechanical(KnapsackAction.BeginTrace)
        if (!state.focused) return Probe.Mechanical(KnapsackAction.Focus)

        val row = state.row
        val col = state.col

        if (!state.fits) {
            // The last capacity before the item fits is where "does it fit?" is a
            // judgement. Every smaller one is the same answer again.
            return if (row >= 2 && col == item.weight - 1) {
                Probe.Decide(choiceDecision(state))
            } else {
                Probe.Mechanical(KnapsackAction.Skip)
            }
        }

        val source = requireNotNull(state.takeSource)
        if (state.source == null) {
            return if (row >= 2 && col > item.weight) {
                Probe.Decide(sourceDecision(state))
            } else {
                Probe.Mechanical(KnapsackAction.PickSource(source.row, source.col))
            }
        }

        return if (row >= 2) {
            Probe.Decide(choiceDecision(state))
        } else {
            Probe.Mechanical(actionFor(requireNotNull(state.bestChoice)))
        }
    }

    private fun actionFor(choice: Choice): KnapsackAction = when (choice) {
        Choice.TAKE -> KnapsackAction.Take
        Choice.SKIP -> KnapsackAction.Skip
    }

    // -- Decisions ------------------------------------------------------------

    /**
     * *If you TAKE this item, which cell holds the best for what is left?*
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

    /** *SKIP or TAKE?* — the recurrence's `max`, and at a boundary, whether it fits at all. */
    private fun choiceDecision(state: KnapsackState): Decision<KnapsackAction> {
        val item = requireNotNull(state.item)
        val col = state.col
        val exclude = requireNotNull(state.exclude)
        val correct = actionFor(requireNotNull(state.bestChoice))
        val options = listOf(
            ActionOption<KnapsackAction>(KnapsackAction.Take, NarrationKey(NarrationId.KN_OPTION_TAKE)),
            ActionOption<KnapsackAction>(KnapsackAction.Skip, NarrationKey(NarrationId.KN_OPTION_SKIP)),
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

    /** *Was this item taken?* — the same question as TAKE or SKIP, asked backwards. */
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
        KnapsackAction.FillBase -> fillBase(state)
        KnapsackAction.Focus -> focus(state)
        is KnapsackAction.PickSource -> pickSource(state, TablePos(action.row, action.col))
        KnapsackAction.Take -> choose(state, Choice.TAKE)
        KnapsackAction.Skip -> choose(state, Choice.SKIP)
        KnapsackAction.BeginTrace -> beginTrace(state)
        KnapsackAction.MarkTaken -> mark(state, taken = true)
        KnapsackAction.MarkLeftOut -> mark(state, taken = false)
    }

    private fun introduce(state: KnapsackState): Transition<KnapsackState> {
        if (state.phase != KnapsackPhase.PROBLEM || state.intro == IntroBeat.SUBPROBLEM) {
            return refuse(state)
        }
        val next = state.copy(intro = IntroBeat.entries[state.intro.ordinal + 1])
        val narration = when (next.intro) {
            IntroBeat.RULE -> NarrationKey(NarrationId.KN_INTRO_RULE)
            IntroBeat.GREEDY -> {
                val pick = next.greedyPick
                if (pick.isEmpty()) {
                    NarrationKey(NarrationId.KN_INTRO_GREEDY_NONE)
                } else {
                    NarrationKey(
                        NarrationId.KN_INTRO_GREEDY,
                        listOf(pick.first().name, next.greedyValue, next.capacity - pick.sumOf { it.weight }),
                    )
                }
            }
            IntroBeat.SUBPROBLEM -> NarrationKey(NarrationId.KN_INTRO_SUBPROBLEM, listOf(next.bagCount))
            IntroBeat.ITEMS -> null
        }
        return Transition(next, emptyList(), narration, correct = true)
    }

    private fun fillBase(state: KnapsackState): Transition<KnapsackState> {
        if (state.phase != KnapsackPhase.PROBLEM || state.intro != IntroBeat.SUBPROBLEM) {
            return refuse(state)
        }
        val table = List(state.itemCount + 1) { r ->
            List(state.columns) { c -> if (r == 0 || c == 0) 0 else null }
        }
        // No items, or no room: every cell is a base case and there is nothing to
        // decide. A finished lesson, not an error.
        val nothingToBuild = state.itemCount == 0 || state.capacity == 0
        val next = state.copy(
            phase = if (nothingToBuild) KnapsackPhase.DONE else KnapsackPhase.BUILD,
            table = table,
            taken = if (nothingToBuild) List(state.itemCount) { false } else state.taken,
        )
        return Transition(
            next = next,
            events = buildList {
                add(VizEvent.Meter(MeterId.REMAINING, state.capacity.toLong()))
                if (nothingToBuild) add(VizEvent.Terminal(Outcome.Completed(correct = true)))
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
                VizEvent.Examine(listOf(state.slotOf(state.row - 1, state.col)), ExamineRole.CANDIDATE),
            ),
            narration = NarrationKey(
                NarrationId.KN_FOCUS,
                listOf(state.row, state.col, state.items.take(state.row).joinToString(", ") { it.name }),
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
            if (r != state.row) cells else cells.mapIndexed { c, v -> if (c == state.col) value else v }
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
                add(VizEvent.Examine(listOf(state.slotOf(r, c), state.slotOf(r - 1, c)), ExamineRole.COMPARING))
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
}
