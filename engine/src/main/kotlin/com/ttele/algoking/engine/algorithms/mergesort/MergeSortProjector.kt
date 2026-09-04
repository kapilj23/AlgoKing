package com.ttele.algoking.engine.algorithms.mergesort

import com.ttele.algoking.engine.event.MeterId
import com.ttele.algoking.engine.event.PointerId
import com.ttele.algoking.engine.event.RegionId
import com.ttele.algoking.engine.event.VizEvent
import com.ttele.algoking.engine.scene.Cell
import com.ttele.algoking.engine.scene.CellState
import com.ttele.algoking.engine.scene.MeterReadout
import com.ttele.algoking.engine.scene.PointerMark
import com.ttele.algoking.engine.scene.RegionMark
import com.ttele.algoking.engine.scene.SceneProjector
import com.ttele.algoking.engine.scene.SequenceScene

/**
 * Merge Sort presentation knowledge — ARCHITECTURE.md §7.2.
 *
 * The visual story is *structure*, which none of the other sorts have:
 *
 * ```
 * dividing:  8 3 6 2 | 7 1 5 4        group separators halve each level
 * merging:   2 3 6 8 | 1 4 5 7        a merged prefix grows inside one window
 *                     ^left  ^right   the two fronts under comparison
 * ```
 *
 * Bubble/Selection/Insertion all act on one flat row. Merge Sort is the only one
 * that shows the array *divided*, and the group separators are what say so.
 */
class MergeSortProjector : SceneProjector<MergeSortState> {

    override fun project(
        state: MergeSortState,
        activeEvents: List<VizEvent>,
    ): SequenceScene {
        val cells = state.values.mapIndexed { index, value ->
            Cell(key = index, value = value, slot = index, state = stateOf(state, index))
        }

        val pointers = buildList {
            if (state.merging && !state.done) {
                state.leftFront?.let { add(PointerMark(PointerId.I, state.leftSlot, "left")) }
                state.rightFront?.let { add(PointerMark(PointerId.J, state.rightSlot, "right")) }
            }
        }

        val regions = buildList {
            if (state.merging && !state.done && state.hi > state.lo) {
                add(RegionMark(RegionId.SEARCH_SPACE, state.lo until state.hi))
            }
        }

        return SequenceScene(
            cells = cells,
            pointers = pointers,
            regions = regions,
            meters = listOf(
                MeterReadout(
                    MeterId.REMAINING,
                    if (state.phase == MergePhase.DIVIDING) "Pieces" else "Runs of",
                    (if (state.phase == MergePhase.DIVIDING) state.groupWidth else state.runWidth)
                        .toLong(),
                ),
            ),
            // Merging combines; it never exchanges, so there is no arc.
            arc = null,
            groups = state.groups(),
        )
    }

    private fun stateOf(state: MergeSortState, index: Int): CellState = when {
        state.done -> CellState.FINALIZED
        !state.merging -> CellState.IDLE
        // Already merged into place in this window.
        index in state.lo until state.writeAt -> CellState.FINALIZED
        // The two front values under comparison.
        index == state.leftSlot && state.leftFront != null -> CellState.COMPARING
        index == state.rightSlot && state.rightFront != null -> CellState.CANDIDATE
        else -> CellState.IDLE
    }
}
