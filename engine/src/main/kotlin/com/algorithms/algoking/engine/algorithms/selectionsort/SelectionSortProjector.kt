package com.algorithms.algoking.engine.algorithms.selectionsort

import com.algorithms.algoking.engine.event.MarkId
import com.algorithms.algoking.engine.event.MeterId
import com.algorithms.algoking.engine.event.PointerId
import com.algorithms.algoking.engine.event.RegionId
import com.algorithms.algoking.engine.event.VizEvent
import com.algorithms.algoking.engine.scene.Arc
import com.algorithms.algoking.engine.scene.Badge
import com.algorithms.algoking.engine.scene.Cell
import com.algorithms.algoking.engine.scene.CellState
import com.algorithms.algoking.engine.scene.MeterReadout
import com.algorithms.algoking.engine.scene.PointerMark
import com.algorithms.algoking.engine.scene.RegionMark
import com.algorithms.algoking.engine.scene.SceneProjector
import com.algorithms.algoking.engine.scene.SequenceScene

/**
 * Selection Sort presentation knowledge — ARCHITECTURE.md §7.2.
 *
 * The visual story has to be legible without reading a word, and it has to be
 * *different* from Bubble Sort's:
 *
 * ```
 * sorted prefix (green, grows leftward)  |  candidate minimum (amber, remembered)
 *                                        |  scan cursor (violet, travels right)
 * ```
 *
 * Bubble Sort shows two neighbours trading places; Selection Sort shows a
 * travelling cursor measured against a value the algorithm is *holding on to*.
 */
class SelectionSortProjector : SceneProjector<SelectionSortState> {

    override fun project(
        state: SelectionSortState,
        activeEvents: List<VizEvent>,
    ): SequenceScene {
        val cells = state.values.mapIndexed { index, value ->
            Cell(key = index, value = value, slot = index, state = stateOf(state, index))
        }

        val pointers = buildList {
            if (!state.done) {
                state.cursor?.let { add(PointerMark(PointerId.I, it, "scan")) }
                if (!state.scanComplete || state.minIndex >= state.sortedTo) {
                    add(PointerMark(PointerId.J, state.minIndex, "min"))
                }
            }
        }

        val regions = buildList {
            if (!state.done && state.sortedTo <= state.values.lastIndex) {
                add(RegionMark(RegionId.SEARCH_SPACE, state.unsorted))
            }
            if (state.sortedTo > 0) {
                add(RegionMark(RegionId.SORTED_PREFIX, 0 until state.sortedTo))
            }
        }

        val moved = activeEvents.filterIsInstance<VizEvent.Swap>().firstOrNull()

        return SequenceScene(
            cells = cells,
            pointers = pointers,
            regions = regions,
            // The remembered minimum is a badge, exactly as Binary Search's target
            // is — it is a value the algorithm is holding, not a cell colour.
            badge = if (state.done) {
                null
            } else {
                Badge(MarkId.MIN_SO_FAR, "Smallest so far", state.minValue)
            },
            meters = listOf(
                MeterReadout(MeterId.REMAINING, "Sorted", state.sortedTo.toLong()),
            ),
            arc = moved?.let { Arc(it.a, it.b) },
        )
    }

    private fun stateOf(state: SelectionSortState, index: Int): CellState = when {
        state.done -> CellState.FINALIZED
        // The prefix is provably final and never moves again.
        index < state.sortedTo -> CellState.FINALIZED
        // The cell being judged right now.
        index == state.cursor -> CellState.COMPARING
        // The value the algorithm is remembering.
        index == state.minIndex -> CellState.CANDIDATE
        else -> CellState.IDLE
    }
}
