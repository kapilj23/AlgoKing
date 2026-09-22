package com.algorithms.algoking.engine.algorithms.quicksort

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
 * Quick Sort presentation knowledge — ARCHITECTURE.md §7.2.
 *
 * The visual story, and it is not Merge Sort's:
 *
 * ```
 * ┌ left of 5 ──────────┐
 * │ 3 2 4 │ 8 6 │ 7 │   │  5  · · ·      ← parked values, shrunk and grey
 * └ left ─┴ right ┴ ─┘      ▲ green, home for good
 * ```
 *
 * Merge Sort divides into equal halves and combines them. Quick Sort grows two
 * *unequal* groups around a pivot, and every finished pivot goes green and never
 * moves again — green appears scattered through the array rather than as one
 * spreading block, which is the picture that says "divide and conquer around a
 * value" instead of "divide down the middle".
 *
 * ### Saying which part is being solved (ADR-054)
 *
 * Recursion is the hard part of this lesson, and it used to be invisible: the
 * outline jumped to a new range and the copy said how many values were in it. A
 * learner had no way to tell that those three values were the ones that lost to
 * the 5, or that the other half was still waiting rather than finished.
 *
 * Three things now say it, and none of them needed a new scene shape:
 *
 *  - **everything outside the live partition is parked** — `ELIMINATED`, which the
 *    renderer shrinks and greys, with the legend renamed to *Waiting* because these
 *    values are deferred rather than ruled out;
 *  - **the live partition is captioned** — `RegionMark.label` reads `left of 5`, so
 *    the picture names the part rather than only outlining it;
 *  - **the beat after a pivot lands shows the split** — `groups` becomes the three
 *    pieces `[3, 2, 4] · 5 · [7, 8, 6]`, which is the one frame where both halves
 *    exist at once.
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
            if (!state.done && state.active && !state.showingSplit) {
                state.pivotAt?.let { add(PointerMark(PointerId.J, it, "pivot")) }
                state.cursor?.takeIf { it < state.hi }?.let {
                    add(PointerMark(PointerId.I, it, "check"))
                }
            }
        }

        val regions = buildList {
            if (state.done) return@buildList
            val split = state.split
            if (split != null) {
                // The two halves, each named for what it is now.
                if (!split.left.isEmpty()) {
                    add(RegionMark(RegionId.SEARCH_SPACE, split.left, "below ${split.pivot}"))
                }
                if (!split.right.isEmpty()) {
                    add(RegionMark(RegionId.WINDOW, split.right, "above ${split.pivot}"))
                }
            } else if (state.active) {
                add(RegionMark(RegionId.SEARCH_SPACE, state.partition, state.sideLabel))
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
            // A parked value is waiting its turn, not ruled out. Quick Sort is the
            // only lesson that means that by this colour, so it says so.
            legendLabels = mapOf(
                CellState.ELIMINATED to "Waiting",
                CellState.CANDIDATE to "Pivot",
                CellState.FINALIZED to "Home",
            ),
        )
    }

    private fun stateOf(state: QuickSortState, index: Int): CellState = when {
        state.done -> CellState.FINALIZED
        // A pivot that has landed is finished for good.
        index in state.finalized -> CellState.FINALIZED
        // On the split beat both halves are in play and neither is being worked
        // on yet, so nothing is parked and nothing is being measured.
        // On the split beat both halves are in play and neither is being worked on
        // yet — but whatever lies outside the partition that just split is still
        // waiting, and should not flicker back to full strength for one frame.
        state.showingSplit -> if (index in state.partition) CellState.IDLE else CellState.ELIMINATED
        !state.active -> CellState.IDLE
        // The pivot itself, until it lands.
        index == state.pivotAt -> CellState.CANDIDATE
        // The value being measured against it.
        index == state.cursor && state.scanning -> CellState.COMPARING
        index in state.partition -> CellState.IDLE
        // Outside the live partition: waiting its turn, and visibly not the thing
        // being solved right now.
        else -> CellState.ELIMINATED
    }
}
