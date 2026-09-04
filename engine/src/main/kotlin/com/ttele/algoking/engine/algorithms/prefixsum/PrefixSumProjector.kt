package com.ttele.algoking.engine.algorithms.prefixsum

import com.ttele.algoking.engine.event.MarkId
import com.ttele.algoking.engine.event.MeterId
import com.ttele.algoking.engine.event.VizEvent
import com.ttele.algoking.engine.scene.Badge
import com.ttele.algoking.engine.scene.Cell
import com.ttele.algoking.engine.scene.CellState
import com.ttele.algoking.engine.scene.MeterReadout
import com.ttele.algoking.engine.scene.PrefixEquation
import com.ttele.algoking.engine.scene.PrefixOp
import com.ttele.algoking.engine.scene.PrefixScene
import com.ttele.algoking.engine.scene.SceneProjector

/**
 * Prefix Sum presentation knowledge — ARCHITECTURE.md §7.2.
 *
 * Two rows, and the offset between them is the lesson. While the array is being
 * built the picture reads left to right: *this array value*, *that previous
 * prefix*, *this new prefix*. Once it is built the reading flips — the span on
 * the source row is the question, and the two prefix cells are the answer.
 *
 * Unbuilt prefix entries are [CellState.GHOST], which the design system already
 * defines as a hole with no numeral. Drawing a `0` or a greyed number there would
 * claim a value exists before it does.
 */
class PrefixSumProjector : SceneProjector<PrefixSumState> {

    override fun project(
        state: PrefixSumState,
        activeEvents: List<VizEvent>,
    ): PrefixScene {
        val building = !state.buildComplete && state.values.isNotEmpty()
        val next = state.nextIndex
        val range = state.queryRange

        // -- The original array -------------------------------------------------
        val source = state.values.mapIndexed { index, value ->
            Cell(
                key = index,
                value = value,
                slot = index,
                state = when {
                    // The element being folded in right now.
                    building && index == next - 1 -> CellState.COMPARING
                    // Answered: the queried span is the result.
                    state.answer != null && range != null && index in range ->
                        CellState.FINALIZED
                    // Asked about, not yet answered.
                    !building && range != null && index in range -> CellState.CANDIDATE
                    // Already folded into the prefix array.
                    building && index < next - 1 -> CellState.IDLE
                    else -> CellState.IDLE
                },
            )
        }

        // -- The prefix array, holes and all -----------------------------------
        val prefix = (0..state.size).map { index ->
            val known = state.prefix.getOrNull(index)
            Cell(
                key = 1_000 + index,
                value = known ?: 0,
                slot = index,
                state = when {
                    known == null -> CellState.GHOST
                    // The two ends of the subtraction, once chosen.
                    index == state.chosenHi || index == state.chosenLo ->
                        if (state.answer != null) CellState.FINALIZED else CellState.COMPARING
                    // The running total just written, and the one it came from.
                    building && index == next - 1 -> CellState.CANDIDATE
                    else -> CellState.IDLE
                },
            )
        }

        return PrefixScene(
            source = source,
            prefix = prefix,
            sourceLabel = "Array",
            prefixLabel = "Prefix",
            equation = equation(state),
            // The bracket appears only once there is a range being asked about;
            // during the build it would be a question nobody has posed yet.
            queryRange = if (building) null else range,
            badge = range?.takeIf { !building }?.let {
                Badge(MarkId.BEST, "Sum ${it.first}..${it.last}", state.queryAnswer ?: 0)
                    .let { badge ->
                        // The answer is the question until it is answered.
                        if (state.answer == null) badge.copy(valueLabel = "?") else badge
                    }
            },
            meters = buildList {
                if (building) {
                    add(MeterReadout(MeterId.REMAINING, "Built", state.prefix.size.toLong()))
                }
            },
            // Short: four entries share one centred row, and a long label pushes
            // the last one off the edge.
            legendLabels = mapOf(
                CellState.COMPARING to if (building) "Adding" else "Using",
                CellState.CANDIDATE to if (building) "Total" else "Range",
                CellState.GHOST to "Empty",
                CellState.FINALIZED to "Answer",
                CellState.IDLE to "Done",
            ),
        )
    }

    /**
     * The working, as data.
     *
     * `result` stays null while the result is the question, which is what lets
     * the same component carry the walkthrough's statement and Try's prompt
     * without the projector knowing which phase is running.
     */
    private fun equation(state: PrefixSumState): PrefixEquation? {
        if (state.values.isEmpty()) return null

        if (!state.buildComplete) {
            val i = state.nextIndex
            return PrefixEquation(
                left = state.prefix.last(),
                operator = PrefixOp.PLUS,
                right = state.values[i - 1],
                result = null,
                leftLabel = "prefix[${i - 1}]",
                rightLabel = "array[${i - 1}]",
                resultLabel = "prefix[$i]",
            )
        }

        val hi = state.chosenHi ?: return null
        val lo = state.chosenLo ?: return null
        val full = state.fullPrefix
        return PrefixEquation(
            left = full[hi.coerceIn(0, state.size)],
            operator = PrefixOp.MINUS,
            right = full[lo.coerceIn(0, state.size)],
            result = state.answer,
            leftLabel = "prefix[$hi]",
            rightLabel = "prefix[$lo]",
            resultLabel = state.queryRange?.let { "sum ${it.first}..${it.last}" },
        )
    }
}
