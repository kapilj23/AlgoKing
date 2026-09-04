package com.ttele.algoking.engine.algorithms.insertionsort

import com.ttele.algoking.engine.event.MarkId
import com.ttele.algoking.engine.event.MeterId
import com.ttele.algoking.engine.event.PointerId
import com.ttele.algoking.engine.event.RegionId
import com.ttele.algoking.engine.event.VizEvent
import com.ttele.algoking.engine.scene.Badge
import com.ttele.algoking.engine.scene.Cell
import com.ttele.algoking.engine.scene.CellState
import com.ttele.algoking.engine.scene.MeterReadout
import com.ttele.algoking.engine.scene.PointerMark
import com.ttele.algoking.engine.scene.RegionMark
import com.ttele.algoking.engine.scene.SceneProjector
import com.ttele.algoking.engine.scene.SequenceScene

/**
 * Insertion Sort presentation knowledge — ARCHITECTURE.md §7.2.
 *
 * The visual story, and it is deliberately unlike the other two sorts:
 *
 * ```
 * sorted prefix (green)   …with a GAP travelling left through it…
 * the key, held outside the array in a badge
 * one violet cell being judged against the key
 * ```
 *
 * Nothing is ever drawn exchanging places. Bubble Sort trades neighbours;
 * Selection Sort carries a minimum to the front; Insertion Sort opens a hole and
 * walks it backwards until the key fits.
 */
class InsertionSortProjector : SceneProjector<InsertionSortState> {

    override fun project(
        state: InsertionSortState,
        activeEvents: List<VizEvent>,
    ): SequenceScene {
        val cells = state.values.mapIndexed { index, value ->
            Cell(key = index, value = value, slot = index, state = stateOf(state, index))
        }

        val pointers = buildList {
            if (!state.done) {
                state.comparingAt?.let { add(PointerMark(PointerId.J, it, "vs key")) }
                state.holeAt?.let { add(PointerMark(PointerId.I, it, "gap")) }
            }
        }

        val regions = buildList {
            if (!state.done) {
                if (state.sortedTo in 1..state.values.lastIndex) {
                    add(RegionMark(RegionId.SORTED_PREFIX, state.sortedRange))
                    add(
                        RegionMark(
                            RegionId.SEARCH_SPACE,
                            state.sortedTo..state.values.lastIndex,
                        ),
                    )
                }
            }
        }

        return SequenceScene(
            cells = cells,
            pointers = pointers,
            regions = regions,
            // The key lives *outside* the array while it is being placed — a badge,
            // exactly like Binary Search's target and Selection Sort's minimum.
            badge = state.key?.let { Badge(MarkId.BEST, "Key", it) },
            meters = listOf(
                MeterReadout(MeterId.REMAINING, "Sorted", state.sortedTo.toLong()),
            ),
            // Deliberately no arc: an arc means an exchange, and nothing here exchanges.
            arc = null,
        )
    }

    private fun stateOf(state: InsertionSortState, index: Int): CellState = when {
        state.done -> CellState.FINALIZED
        // The gap the key left behind, or that a shifted value just vacated.
        index == state.holeAt -> CellState.GHOST
        // The value being judged against the key.
        index == state.comparingAt -> CellState.COMPARING
        // The always-sorted left side.
        index < state.sortedTo -> CellState.FINALIZED
        else -> CellState.IDLE
    }
}
