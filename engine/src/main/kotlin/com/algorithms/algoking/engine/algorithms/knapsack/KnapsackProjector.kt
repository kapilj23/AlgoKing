package com.algorithms.algoking.engine.algorithms.knapsack

import com.algorithms.algoking.engine.event.VizEvent
import com.algorithms.algoking.engine.scene.BagMeter
import com.algorithms.algoking.engine.scene.Cell
import com.algorithms.algoking.engine.scene.CellState
import com.algorithms.algoking.engine.scene.ChoiceEmphasis
import com.algorithms.algoking.engine.scene.ChoiceSide
import com.algorithms.algoking.engine.scene.ChoiceStrip
import com.algorithms.algoking.engine.scene.DpTableScene
import com.algorithms.algoking.engine.scene.ItemCard
import com.algorithms.algoking.engine.scene.SceneProjector
import com.algorithms.algoking.engine.scene.TableHeader

/**
 * 0/1 Knapsack presentation knowledge — ARCHITECTURE.md §7.2.
 *
 * ### The first act draws a bag, not a table
 *
 * ADR-053 put a whole act in front of the table, and it added **no field to the
 * scene**: `items`, `bag`, `choice` and `focusCaption` were already there for the
 * cards and the strip the second act ends on, and `tableVisible` was already the
 * switch that says a table does not exist yet. So the first act is those four
 * fields with no `cells` under them, and the renderer's only new job is to draw
 * them when there is no table below.
 *
 * | Beat | What is on screen |
 * |---|---|
 * | `BAG` | an empty bag, and how much it holds |
 * | `ITEMS` | the cards, and what they weigh together |
 * | `TOO_MUCH` | the same, with the overflow stated |
 * | `ONCE` | every card marked `0 or 1` |
 * | `PACKING` | the most valuable item in the bag, and the room it left |
 * | `PACKED` | a full bag — and a second one beside it, both totals hidden |
 * | `COMPARED` | both totals, and the better bag is not the obvious one |
 * | `EVERY_BAG` | the cards again, and how many bags they make |
 * | `FORK` | TAKE and SKIP under one item — the rule, before the table |
 *
 * ### The states — every one already exists
 *
 * | Means | State |
 * |---|---|
 * | the cell being decided | `GHOST` — a hole |
 * | SKIP's cell, `dp[i-1][c]` | `CANDIDATE` amber — the value being kept |
 * | TAKE's cell, `dp[i-1][c-w]` | `COMPARING` violet |
 * | the answer, and the walk back up | `FINALIZED` green |
 * | computed | `IDLE` |
 * | not computed | no cell at all |
 *
 * Amber is SKIP and violet is TAKE because TAKE is the first option button and
 * SKIP the second, and `DecisionTone` draws those violet and orange: the cell each
 * side reads is the colour of the button that chooses it.
 *
 * ### Nothing answers a question before it is asked
 *
 * While TAKE's cell is being picked, TAKE's side reads `?` (ADR-030). While the two
 * bags are being compared, **both** totals read `?`, because a learner who can see
 * 14 beside 13 is reading rather than adding. When the item does not fit, TAKE's
 * side states the weight and the column header states the capacity, and the
 * comparison is the learner's.
 */
class KnapsackProjector : SceneProjector<KnapsackState> {

    override fun project(state: KnapsackState, activeEvents: List<VizEvent>): DpTableScene {
        val tracing = state.phase == KnapsackPhase.TRACE || state.phase == KnapsackPhase.DONE

        return DpTableScene(
            rowHeaders = rowHeaders(state),
            columnHeaders = (0..state.capacity).map { c ->
                TableHeader(
                    label = c.toString(),
                    active = (state.building && c == state.col) ||
                        (state.explainingTable && state.intro >= IntroBeat.AXES &&
                            c == state.teachingCell.col) ||
                        (state.phase == KnapsackPhase.TRACE && c == state.traceCap),
                )
            },
            columnCaption = "Capacity",
            cells = cells(state),
            tableVisible = !state.posing || state.explainingTable,
            choice = choice(state),
            focusCaption = focusCaption(state),
            items = itemCards(state),
            bag = bag(state),
            legendLabels = if (tracing) {
                mapOf(
                    CellState.COMPARING to "This row",
                    CellState.CANDIDATE to "Row above",
                    CellState.FINALIZED to "Path",
                    CellState.IDLE to "Solved",
                )
            } else {
                mapOf(
                    CellState.GHOST to "Deciding",
                    CellState.CANDIDATE to "Skip keeps",
                    CellState.COMPARING to "Take builds on",
                    CellState.IDLE to "Solved",
                    CellState.FINALIZED to "Answer",
                )
            },
        )
    }

    private fun rowHeaders(state: KnapsackState): List<TableHeader> =
        listOf(TableHeader(label = "No items")) + state.items.mapIndexed { index, item ->
            val row = index + 1
            TableHeader(
                label = item.name,
                detail = "w${item.weight} · v${item.value}",
                active = (state.building && row == state.row) ||
                    (state.explainingTable && state.intro >= IntroBeat.AXES &&
                        row == state.teachingCell.row) ||
                    (state.phase == KnapsackPhase.TRACE && row == state.traceRow),
            )
        }

    private fun cells(state: KnapsackState): List<List<Cell?>> {
        val current = when {
            state.building && state.focused -> state.position
            // The box the introduction is pointing at, before anything is in it.
            state.intro >= IntroBeat.AXES && state.explainingTable -> state.teachingCell
            else -> null
        }
        // Only while building: during the introduction the box is a hole being
        // pointed at, and nothing else on the empty grid should be lit.
        val skipCell = if (state.building) current?.let { TablePos(it.row - 1, it.col) } else null
        val takeCell = if (current != null) state.source else null

        // The cell just written keeps its winning side lit, so the learner sees
        // where the number came from.
        val resolved = state.resolved.takeIf { !state.focused && state.phase == KnapsackPhase.BUILD }
        val winner = when {
            resolved == null -> null
            resolved.choice == Choice.TAKE -> resolved.source
            else -> TablePos(resolved.pos.row - 1, resolved.pos.col)
        }

        val traceHere = state.tracePosition
        val traceAbove = traceHere?.let { TablePos(it.row - 1, it.col) }
        val path = state.tracedPath.toSet()
        val answer = TablePos(state.itemCount, state.capacity)

        return (0..state.itemCount).map { r ->
            (0..state.capacity).map { c ->
                val pos = TablePos(r, c)
                val slot = state.slotOf(r, c)
                val value = state.valueAt(r, c)
                when {
                    pos == current -> Cell(key = slot, value = 0, slot = slot, state = CellState.GHOST)
                    value == null -> null
                    else -> Cell(
                        key = slot,
                        value = value,
                        slot = slot,
                        state = when (pos) {
                            takeCell -> CellState.COMPARING
                            skipCell -> CellState.CANDIDATE
                            traceHere -> CellState.COMPARING
                            traceAbove -> CellState.CANDIDATE
                            in path -> CellState.FINALIZED
                            winner -> if (resolved?.choice == Choice.TAKE) {
                                CellState.COMPARING
                            } else {
                                CellState.CANDIDATE
                            }
                            answer -> if (state.buildComplete) CellState.FINALIZED else CellState.IDLE
                            else -> CellState.IDLE
                        },
                    )
                }
            }
        }
    }

    private fun choice(state: KnapsackState): ChoiceStrip? {
        if (state.posing) return introChoice(state)

        if (state.building && state.focused) {
            val item = requireNotNull(state.item)
            val source = state.source
            val take = when {
                !state.fits -> ChoiceSide("Take", "${item.name} weighs ${item.weight}", value = null)
                source == null -> ChoiceSide("Take", "${item.value} + dp[?][?]", value = null)
                else -> ChoiceSide("Take", "${item.value} + dp[${source.row}][${source.col}]", state.include)
            }
            val skip = ChoiceSide("Skip", "dp[${state.row - 1}][${state.col}]", state.exclude)
            return ChoiceStrip(take, skip)
        }

        val resolved = state.resolved
        if (resolved != null && state.phase == KnapsackPhase.BUILD) {
            val item = state.items[resolved.pos.row - 1]
            val source = resolved.source
            val took = resolved.choice == Choice.TAKE
            val take = if (resolved.include != null && source != null) {
                ChoiceSide(
                    "Take",
                    "${item.value} + dp[${source.row}][${source.col}]",
                    resolved.include,
                    if (took) ChoiceEmphasis.CHOSEN else ChoiceEmphasis.PASSED,
                )
            } else {
                ChoiceSide("Take", "doesn't fit", null, ChoiceEmphasis.PASSED)
            }
            val skip = ChoiceSide(
                "Skip",
                "dp[${resolved.pos.row - 1}][${resolved.pos.col}]",
                resolved.exclude,
                if (took) ChoiceEmphasis.PASSED else ChoiceEmphasis.CHOSEN,
            )
            return ChoiceStrip(take, skip)
        }

        val here = state.tracePosition ?: return null
        return ChoiceStrip(
            ChoiceSide("This row", "dp[${here.row}][${here.col}]", state.valueAt(here)),
            ChoiceSide("Row above", "dp[${here.row - 1}][${here.col}]", state.valueAt(here.row - 1, here.col)),
        )
    }

    /**
     * The first act uses the strip twice, for the two beats that are a comparison.
     *
     * On `PACKED` the two bags are side by side with **both totals hidden**, which
     * is what makes it a question; on `COMPARED` they are shown and the better one
     * is lit. On `FORK` the same strip draws the rule itself — one item above, TAKE
     * and SKIP below it, and no numbers at all, because there is nothing yet to put
     * in them.
     */
    private fun introChoice(state: KnapsackState): ChoiceStrip? {
        val hand = state.problem.greedyBag
        val best = state.problem.bestBag
        return when (state.intro) {
            IntroBeat.PACKED -> ChoiceStrip(
                bagSide(hand, value = null),
                bagSide(best, value = null),
            )
            IntroBeat.COMPARED -> ChoiceStrip(
                bagSide(hand, state.handValue, ChoiceEmphasis.PASSED),
                bagSide(best, state.bestValue, ChoiceEmphasis.CHOSEN),
            )
            IntroBeat.FORK -> ChoiceStrip(
                first = ChoiceSide("Take", "its value + the best of the room left", value = null),
                second = ChoiceSide("Skip", "the best without it", value = null),
                stem = "Every item, one at a time",
            )
            else -> null
        }
    }

    private fun bagSide(
        bag: List<com.algorithms.algoking.engine.core.KnapsackItem>,
        value: Int?,
        emphasis: ChoiceEmphasis = ChoiceEmphasis.OPEN,
    ) = ChoiceSide(
        caption = bag.joinToString(" + ") { it.name },
        formula = bag.joinToString(" + ") { it.value.toString() },
        value = value,
        emphasis = emphasis,
    )

    private fun focusCaption(state: KnapsackState): String? = when {
        state.posing -> introCaption(state)
        state.building && state.focused -> {
            val names = state.items.take(state.row).joinToString(", ") { it.name }
            "dp[${state.row}][${state.col}] — the best using $names, with capacity ${state.col}"
        }
        state.phase == KnapsackPhase.BUILD && state.resolved != null -> {
            val pos = state.resolved.pos
            "dp[${pos.row}][${pos.col}] = ${state.valueAt(pos)}"
        }
        state.phase == KnapsackPhase.TRACE ->
            "dp[${state.traceRow}][${state.traceCap}] against the row above"
        else -> null
    }

    /** The working of the first act, printed where the table's caption goes. */
    private fun introCaption(state: KnapsackState): String? = when (state.intro) {
        // Not on ITEMS: the cards arriving is that beat, and the sum arriving is
        // this one. Two beats that drew the same thing would be one beat.
        IntroBeat.TOO_MUCH ->
            "Everything together: ${state.items.joinToString(" + ") { it.weight.toString() }} = " +
                "${state.totalWeight} kg, into a bag that holds ${state.capacity}"
        IntroBeat.PACKING -> {
            val picked = requireNotNull(state.firstPick)
            "${state.capacity} − ${picked.weight} = ${state.roomLeft} kg left"
        }
        IntroBeat.EVERY_BAG ->
            "${List(state.itemCount) { "2" }.joinToString(" × ")} = ${state.bagCount} bags to check"
        IntroBeat.AXES ->
            "This box: the best you can do with " +
                "${state.teachingItems.joinToString(" and ") { it.name }}, " +
                "and ${state.teachingCell.col} kg of room"
        IntroBeat.NAME ->
            "dp[${state.teachingCell.row}][${state.teachingCell.col}] — row " +
                "${state.teachingCell.row}, column ${state.teachingCell.col}"
        else -> null
    }

    /**
     * The cards carry the 0/1 themselves: `1` for what is in the bag on screen and
     * `0` for what is not, which is the encoding the lesson is named after, shown
     * rather than defined.
     */
    private fun itemCards(state: KnapsackState): List<ItemCard> = when (state.phase) {
        KnapsackPhase.PROBLEM -> {
            val inBag = when (state.intro) {
                IntroBeat.PACKING, IntroBeat.PACKED -> state.handBag
                IntroBeat.COMPARED -> state.rivalBag
                else -> emptyList()
            }
            if (state.intro == IntroBeat.BAG || state.explainingTable) {
                emptyList()
            } else state.items.map { item ->
                val taken = item in inBag
                ItemCard(
                    name = item.name,
                    weight = item.weight,
                    value = item.value,
                    state = if (taken) CellState.FINALIZED else CellState.IDLE,
                    bitLabel = when {
                        state.intro < IntroBeat.ONCE -> null
                        state.intro == IntroBeat.ONCE || state.intro == IntroBeat.EVERY_BAG ||
                            state.intro == IntroBeat.FORK -> "0 or 1"
                        taken -> "1"
                        else -> "0"
                    },
                )
            }
        }

        KnapsackPhase.BUILD -> emptyList()

        KnapsackPhase.TRACE, KnapsackPhase.DONE -> state.items.mapIndexed { index, item ->
            val decided = state.taken.getOrNull(index)
            ItemCard(
                name = item.name,
                weight = item.weight,
                value = item.value,
                state = when {
                    decided == true -> CellState.FINALIZED
                    decided == false -> CellState.ELIMINATED
                    state.phase == KnapsackPhase.TRACE && index == state.traceRow - 1 -> CellState.COMPARING
                    else -> CellState.IDLE
                },
                bitLabel = when (decided) {
                    true -> "1"
                    false -> "0"
                    null -> if (index == state.traceRow - 1) "?" else null
                },
            )
        }
    }

    private fun bag(state: KnapsackState): BagMeter? {
        val contents = when (state.phase) {
            KnapsackPhase.PROBLEM -> when (state.intro) {
                IntroBeat.EVERY_BAG, IntroBeat.FORK, IntroBeat.GRID, IntroBeat.AXES, IntroBeat.NAME ->
                    return null
                IntroBeat.PACKING, IntroBeat.PACKED -> state.handBag
                IntroBeat.COMPARED -> state.rivalBag
                else -> emptyList()
            }
            KnapsackPhase.BUILD -> return null
            KnapsackPhase.TRACE, KnapsackPhase.DONE -> state.bag
        }
        return BagMeter(
            used = contents.sumOf { it.weight },
            capacity = state.capacity,
            value = contents.sumOf { it.value },
            contents = contents.map { it.name },
        )
    }
}
