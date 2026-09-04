package com.ttele.algoking.engine.algorithms.binarysearch

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
 * Binary Search presentation knowledge — ARCHITECTURE.md §7.2.
 *
 * The whole pedagogy of the screen is here: everything outside `lo..hi` is
 * ELIMINATED, `mid` is COMPARING, and the live range is a region. The renderer
 * only knows cell states; it never learns the word "binary".
 */
class BinarySearchProjector : SceneProjector<BinarySearchState> {

    override fun project(
        state: BinarySearchState,
        activeEvents: List<VizEvent>,
    ): SequenceScene {
        val cells = state.values.mapIndexed { index, value ->
            Cell(
                key = index,
                value = value,
                slot = index,
                state = stateOf(state, index),
            )
        }

        // "left" and "right" rather than "lo" and "hi". The learner is being
        // taught which half survives; naming the bounds after the halves they
        // bound costs nothing and spares them a second vocabulary. The indices
        // themselves come from `showIndices`, so the labels stay short enough to
        // sit under a 44dp cell.
        val pointers = buildList {
            if (!state.finished && state.remaining > 0) {
                add(PointerMark(PointerId.LO, state.lo, "left"))
                add(PointerMark(PointerId.HI, state.hi, "right"))
                state.mid?.let { add(PointerMark(PointerId.MID, it, "mid")) }
            }
        }

        val regions = buildList {
            if (!state.finished && state.remaining > 0) {
                add(RegionMark(RegionId.SEARCH_SPACE, state.range))
            }
        }

        return SequenceScene(
            cells = cells,
            pointers = pointers,
            regions = regions,
            badge = Badge(MarkId.TARGET, "Target", state.target),
            meters = listOf(
                MeterReadout(MeterId.REMAINING, "In range", state.remaining.toLong()),
            ),
            // The middle is computed from positions, so the positions are shown.
            showIndices = true,
        )
    }

    private fun stateOf(state: BinarySearchState, index: Int): CellState = when {
        state.foundAt == index -> CellState.FINALIZED
        index < state.lo || index > state.hi -> CellState.ELIMINATED
        state.mid == index -> CellState.COMPARING
        else -> CellState.IDLE
    }
}
