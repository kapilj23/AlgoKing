package com.ttele.algoking.engine.algorithms.twopointers

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
 * Two Pointers presentation knowledge — ARCHITECTURE.md §7.2.
 *
 * The picture has to say three things at once: *these two cells are the pair*,
 * *this is what they add up to*, and *everything outside the window is gone*.
 * All three are ordinary scene data, so the renderer learns nothing new — it
 * still only knows cell states, pointer marks, a region and a meter.
 *
 * The one judgement here is that **both ends are `COMPARING`, never `CANDIDATE`**.
 * Amber means a value the algorithm is *holding on to* (Selection Sort's
 * remembered minimum); Two Pointers holds nothing — it looks at exactly two cells
 * and then discards one. Two violet cells with a sum between them is the honest
 * picture.
 */
class TwoPointersProjector : SceneProjector<TwoPointersState> {

    override fun project(
        state: TwoPointersState,
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

        // Textbook vocabulary, in the textbook's own words. The learner will meet
        // "left" and "right" in every write-up of this technique, so the labels
        // are the terms rather than a friendlier paraphrase.
        val pointers = buildList {
            if (!state.finished && state.hasPair) {
                add(PointerMark(PointerId.LO, state.left, "LEFT"))
                add(PointerMark(PointerId.HI, state.right, "RIGHT"))
            }
        }

        val regions = buildList {
            if (!state.finished && state.hasPair) {
                add(RegionMark(RegionId.SEARCH_SPACE, state.window))
            }
        }

        // The sum appears only once the app has actually read the pair. Printing
        // it a beat early would answer the comparison before it is asked, the way
        // a hash bucket showing its answer stops being a question (ADR-030).
        val meters = buildList {
            state.sum?.let { add(MeterReadout(MeterId.RUNNING_SUM, "Sum", it.toLong())) }
            if (!state.finished) {
                add(MeterReadout(MeterId.REMAINING, "In window", state.remaining.toLong()))
            }
        }

        return SequenceScene(
            cells = cells,
            pointers = pointers,
            regions = regions,
            badge = Badge(MarkId.TARGET, "Target", state.target),
            meters = meters,
            // The pointers are positions, and the lesson talks about them moving
            // one place at a time, so the positions are worth showing.
            showIndices = true,
            legendLabels = mapOf(
                CellState.COMPARING to "The pair",
                CellState.ELIMINATED to "Ruled out",
                CellState.FINALIZED to "Pair found",
            ),
        )
    }

    private fun stateOf(state: TwoPointersState, index: Int): CellState = when {
        // The found pair outranks everything: it is the answer, and it is green.
        state.foundLeft == index || state.foundRight == index -> CellState.FINALIZED
        // Outside the window is gone for good — moving a pointer discards every
        // pair that value was part of, not just the one just tried.
        index < state.left || index > state.right -> CellState.ELIMINATED
        state.hasPair && (index == state.left || index == state.right) -> CellState.COMPARING
        else -> CellState.IDLE
    }
}
