package com.algorithms.algoking.engine.algorithms.linearsearch

import com.algorithms.algoking.engine.event.MarkId
import com.algorithms.algoking.engine.event.MeterId
import com.algorithms.algoking.engine.event.PointerId
import com.algorithms.algoking.engine.event.VizEvent
import com.algorithms.algoking.engine.scene.Badge
import com.algorithms.algoking.engine.scene.Cell
import com.algorithms.algoking.engine.scene.CellState
import com.algorithms.algoking.engine.scene.MeterReadout
import com.algorithms.algoking.engine.scene.PointerMark
import com.algorithms.algoking.engine.scene.SceneProjector
import com.algorithms.algoking.engine.scene.SequenceScene

/**
 * Linear Search presentation: Binary Search's row, target badge and indices, with
 * one pointer walking left to right instead of three.
 *
 * | Means | State |
 * |---|---|
 * | being compared with the target | `COMPARING` |
 * | already checked — not the target | `ELIMINATED` |
 * | the target, found | `FINALIZED` |
 * | not looked at yet | `IDLE` |
 *
 * So the greyed-out run on the left *is* the cost so far, one cell per check.
 */
class LinearSearchProjector : SceneProjector<LinearSearchState> {

    override fun project(state: LinearSearchState, activeEvents: List<VizEvent>): SequenceScene =
        SequenceScene(
            cells = state.values.mapIndexed { i, value ->
                Cell(
                    key = i,
                    value = value,
                    slot = i,
                    state = when {
                        state.foundAt == i -> CellState.FINALIZED
                        i < state.index -> CellState.ELIMINATED
                        i == state.index && !state.finished -> CellState.COMPARING
                        else -> CellState.IDLE
                    },
                )
            },
            pointers = if (state.finished) {
                emptyList()
            } else {
                listOf(PointerMark(PointerId.I, state.index, "i"))
            },
            badge = Badge(MarkId.TARGET, "Target", state.target),
            meters = listOf(MeterReadout(MeterId.REMAINING, "Checked", state.checked.toLong())),
            showIndices = true,
        )
}
