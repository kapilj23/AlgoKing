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
 * While TAKE's cell is being picked, TAKE's side reads `?` (ADR-030). When the item
 * does not fit, TAKE's side states the weight and the column header states the
 * capacity, and the comparison is the learner's.
 */
class KnapsackProjector : SceneProjector<KnapsackState> {

    override fun project(state: KnapsackState, activeEvents: List<VizEvent>): DpTableScene {
        val tableVisible = state.phase != KnapsackPhase.PROBLEM || state.intro == IntroBeat.SUBPROBLEM
        val tracing = state.phase == KnapsackPhase.TRACE || state.phase == KnapsackPhase.DONE

        return DpTableScene(
            rowHeaders = rowHeaders(state),
            columnHeaders = (0..state.capacity).map { c ->
                TableHeader(
                    label = c.toString(),
                    active = (state.building && c == state.col) ||
                        (state.phase == KnapsackPhase.TRACE && c == state.traceCap),
                )
            },
            columnCaption = "Capacity",
            cells = cells(state),
            tableVisible = tableVisible,
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
                    (state.phase == KnapsackPhase.TRACE && row == state.traceRow),
            )
        }

    private fun cells(state: KnapsackState): List<List<Cell?>> {
        val current = if (state.building && state.focused) state.position else null
        val skipCell = current?.let { TablePos(it.row - 1, it.col) }
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

    private fun focusCaption(state: KnapsackState): String? = when {
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

    private fun itemCards(state: KnapsackState): List<ItemCard> = when (state.phase) {
        KnapsackPhase.PROBLEM -> {
            if (state.intro == IntroBeat.SUBPROBLEM) {
                emptyList()
            } else {
                val greedy = if (state.intro == IntroBeat.GREEDY) state.greedyPick.toSet() else emptySet()
                state.items.map { item ->
                    ItemCard(
                        name = item.name,
                        weight = item.weight,
                        value = item.value,
                        state = if (item in greedy) CellState.FINALIZED else CellState.IDLE,
                        bitLabel = when (state.intro) {
                            IntroBeat.ITEMS -> null
                            IntroBeat.RULE -> "0 or 1"
                            else -> if (item in greedy) "1" else "0"
                        },
                    )
                }
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
                IntroBeat.SUBPROBLEM -> return null
                IntroBeat.GREEDY -> state.greedyPick
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
