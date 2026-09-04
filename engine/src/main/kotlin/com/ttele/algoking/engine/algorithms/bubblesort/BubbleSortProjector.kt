package com.ttele.algoking.engine.algorithms.bubblesort

import com.ttele.algoking.engine.event.MeterId
import com.ttele.algoking.engine.event.PointerId
import com.ttele.algoking.engine.event.RegionId
import com.ttele.algoking.engine.event.VizEvent
import com.ttele.algoking.engine.scene.Arc
import com.ttele.algoking.engine.scene.Cell
import com.ttele.algoking.engine.scene.CellState
import com.ttele.algoking.engine.scene.MeterReadout
import com.ttele.algoking.engine.scene.PointerMark
import com.ttele.algoking.engine.scene.RegionMark
import com.ttele.algoking.engine.scene.SceneProjector
import com.ttele.algoking.engine.scene.SequenceScene

/**
 * Bubble Sort presentation knowledge — ARCHITECTURE.md §7.2.
 *
 * The pedagogy of the screen lives here: the pair under comparison is COMPARING,
 * the bubbled suffix is FINALIZED, everything else is IDLE. The renderer only ever
 * learns cell states — it never learns the word "bubble".
 */
class BubbleSortProjector : SceneProjector<BubbleSortState> {

    override fun project(
        state: BubbleSortState,
        activeEvents: List<VizEvent>,
    ): SequenceScene {
        val cells = state.values.mapIndexed { index, value ->
            Cell(
                // Identity is the slot: Bubble Sort's values move, and the renderer
                // animates the exchange from the Swap event rather than from keys.
                key = index,
                value = value,
                slot = index,
                state = stateOf(state, index),
            )
        }

        val pointers = buildList {
            if (!state.done && state.focused && state.hasPair) {
                add(PointerMark(PointerId.I, state.i, "a"))
                add(PointerMark(PointerId.J, state.pairEnd, "b"))
            }
        }

        val regions = buildList {
            if (!state.done && state.sortedFrom in 1..state.values.lastIndex) {
                add(RegionMark(RegionId.SORTED_PREFIX, state.sortedFrom..state.values.lastIndex))
            }
        }

        val swap = activeEvents.filterIsInstance<VizEvent.Swap>().firstOrNull()

        return SequenceScene(
            cells = cells,
            pointers = pointers,
            regions = regions,
            meters = listOf(
                MeterReadout(MeterId.REMAINING, "Pass", state.pass.toLong()),
            ),
            arc = swap?.let { Arc(it.a, it.b) },
        )
    }

    private fun stateOf(state: BubbleSortState, index: Int): CellState = when {
        state.done -> CellState.FINALIZED
        // The bubbled suffix is provably final.
        index >= state.sortedFrom -> CellState.FINALIZED
        state.focused && (index == state.i || index == state.pairEnd) -> CellState.COMPARING
        else -> CellState.IDLE
    }
}
