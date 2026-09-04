package com.ttele.algoking.engine.algorithms.quicksort

import com.ttele.algoking.engine.event.MarkId
import com.ttele.algoking.engine.event.MeterId
import com.ttele.algoking.engine.event.PointerId
import com.ttele.algoking.engine.event.RegionId
import com.ttele.algoking.engine.event.VizEvent
import com.ttele.algoking.engine.scene.Arc
import com.ttele.algoking.engine.scene.Badge
import com.ttele.algoking.engine.scene.Cell
import com.ttele.algoking.engine.scene.CellState
import com.ttele.algoking.engine.scene.MeterReadout
import com.ttele.algoking.engine.scene.PointerMark
import com.ttele.algoking.engine.scene.RegionMark
import com.ttele.algoking.engine.scene.SceneProjector
import com.ttele.algoking.engine.scene.SequenceScene

/**
 * Quick Sort presentation knowledge — ARCHITECTURE.md §7.2.
 *
 * The visual story, and it is not Merge Sort's:
 *
 * ```
 * ┌ the active partition, outlined ─────────────────┐
 * │ 3 2 4 │ 8 6 │ 7 │                            5  │   ← amber pivot, at the end
 * └ left ─┴ right ┴ unjudged ┘                       ↑ violet cursor
 * ```
 *
 * Merge Sort divides into equal halves and combines them. Quick Sort grows two
 * *unequal* groups around a pivot, and every finished pivot goes green and never
 * moves again — green appears scattered through the array rather than as one
 * spreading block, which is the picture that says "divide and conquer around a
 * value" instead of "divide down the middle".
 */
class QuickSortProjector : SceneProjector<QuickSortState> {

    override fun project(
        state: QuickSortState,
        activeEvents: List<VizEvent>,
    ): SequenceScene {
        val cells = state.values.mapIndexed { index, value ->
            Cell(key = index, value = value, slot = index, state = stateOf(state, index))
        }

        val pointers = buildList {
            if (!state.done && state.active) {
                state.pivotAt?.let { add(PointerMark(PointerId.J, it, "pivot")) }
                state.cursor?.takeIf { it < state.hi }?.let {
                    add(PointerMark(PointerId.I, it, "check"))
                }
            }
        }

        val regions = buildList {
            if (!state.done && state.active) {
                add(RegionMark(RegionId.SEARCH_SPACE, state.partition))
            }
        }

        val moved = activeEvents.filterIsInstance<VizEvent.Swap>().firstOrNull()

        return SequenceScene(
            cells = cells,
            pointers = pointers,
            regions = regions,
            // The pivot is the value everything is measured against, so it is named
            // as a badge as well as coloured — the same treatment Binary Search
            // gives its target.
            badge = state.pivot?.let { Badge(MarkId.BEST, "Pivot", it) },
            meters = listOf(
                MeterReadout(MeterId.REMAINING, "Placed", state.finalized.size.toLong()),
            ),
            arc = moved?.let { Arc(it.a, it.b) },
            groups = state.groups(),
        )
    }

    private fun stateOf(state: QuickSortState, index: Int): CellState = when {
        state.done -> CellState.FINALIZED
        // A pivot that has landed is finished for good.
        index in state.finalized -> CellState.FINALIZED
        !state.active -> CellState.IDLE
        // The pivot itself, until it lands.
        index == state.pivotAt -> CellState.CANDIDATE
        // The value being measured against it.
        index == state.cursor && state.scanning -> CellState.COMPARING
        index in state.partition -> CellState.IDLE
        // Outside the active partition: still waiting its turn.
        else -> CellState.IDLE
    }
}
