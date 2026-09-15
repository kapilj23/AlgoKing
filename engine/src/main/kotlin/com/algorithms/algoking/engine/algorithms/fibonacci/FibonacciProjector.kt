package com.algorithms.algoking.engine.algorithms.fibonacci

import com.algorithms.algoking.engine.event.MarkId
import com.algorithms.algoking.engine.event.MeterId
import com.algorithms.algoking.engine.event.VizEvent
import com.algorithms.algoking.engine.scene.Badge
import com.algorithms.algoking.engine.scene.Cell
import com.algorithms.algoking.engine.scene.CellState
import com.algorithms.algoking.engine.scene.MeterReadout
import com.algorithms.algoking.engine.scene.PrefixEquation
import com.algorithms.algoking.engine.scene.PrefixOp
import com.algorithms.algoking.engine.scene.SceneLayout
import com.algorithms.algoking.engine.scene.SceneProjector
import com.algorithms.algoking.engine.scene.SequenceScene

/**
 * Fibonacci presentation knowledge — ARCHITECTURE.md §7.2.
 *
 * ### Why this is an ordinary sequence
 *
 * A DP table indexed by one quantity is a row of cells whose slots are positions,
 * which is exactly what [SequenceScene] is. Knapsack needed [DpTableScene] because
 * a knapsack cell is *"the first i items, capacity c"* — two axes, and a cell that
 * reads the row above. Fibonacci has one axis and reads two cells to its left, so
 * giving it a shape of its own would be a second name for a picture the renderer
 * already draws. ADR-036 made that call for the Binary Search Tree and it is the
 * same call here: **a new shape is for a new kind of data, not for a new lesson.**
 *
 * ### What the picture has to say
 *
 * One thing, at every beat: *these two cells make that one.* So the two source
 * cells are `COMPARING` violet, the cell they produce is `CANDIDATE` amber, and
 * everything not computed yet is a `GHOST` — the hole Insertion Sort established
 * and Prefix Sum reused, because a `0` in an uncomputed cell would claim a value
 * exists before it does.
 *
 * The scene is read from [activeEvents] rather than from the cursor alone. After a
 * value is written the state's cursor has already moved on, so lighting the cells
 * the cursor points at would show the learner the *next* pair beside the sentence
 * explaining the last one — which is the wrong order to think in, and the reason
 * Two Pointers splits its comparison from its move (ADR-032).
 */
class FibonacciProjector : SceneProjector<FibonacciState> {

    override fun project(
        state: FibonacciState,
        activeEvents: List<VizEvent>,
    ): SequenceScene {
        // The entry just written, if this frame wrote one. Its two operands are
        // what the picture should be lighting, not the cursor's.
        val justWritten = activeEvents
            .filterIsInstance<VizEvent.Insert>()
            .firstOrNull()
            ?.at

        // Which cell is under construction, and which two feed it.
        val focusIndex = justWritten ?: state.nextIndex.takeIf { !state.buildComplete }
        val sources = focusIndex
            ?.let { setOf(it - 1, it - 2) }
            ?.filter { it >= 0 }
            ?.toSet()
            .orEmpty()

        val cells = (0..state.n).map { index ->
            val known = state.dp.getOrNull(index)
            Cell(
                key = index,
                value = known ?: 0,
                slot = index,
                state = when {
                    // Not computed yet — a hole, never a greyed-out number.
                    known == null -> CellState.GHOST
                    // The answer, once the learner has actually reached it.
                    state.buildComplete && index == state.n -> CellState.FINALIZED
                    // The two cells this step is adding together.
                    index in sources -> CellState.COMPARING
                    // The cell they just produced.
                    index == justWritten -> CellState.CANDIDATE
                    else -> CellState.IDLE
                },
            )
        }

        return SequenceScene(
            cells = cells,
            layout = SceneLayout.ROW,
            // The lesson's whole vocabulary is `dp[i - 1]` and `dp[i - 2]`, so a
            // learner who cannot see the positions has been shown a magic trick
            // rather than a recurrence.
            showIndices = true,
            badge = Badge(
                mark = MarkId.BEST,
                label = "F(${state.n})",
                value = state.answer,
            ).let { badge ->
                // The answer is the question until it is answered.
                if (state.buildComplete) badge else badge.copy(valueLabel = "?")
            },
            equation = equation(state, justWritten),
            meters = buildList {
                if (!state.buildComplete) {
                    add(
                        MeterReadout(
                            MeterId.REMAINING,
                            "Built",
                            state.dp.size.toLong(),
                        ),
                    )
                }
            },
            // Short: four entries share one centred row, and a long label pushes
            // the last one off the edge (DESIGN_SYSTEM.md §6.13).
            legendLabels = mapOf(
                CellState.COMPARING to "Adding",
                CellState.CANDIDATE to "New",
                CellState.GHOST to "Empty",
                CellState.FINALIZED to "Answer",
                CellState.IDLE to "Done",
            ),
        )
    }

    /**
     * The working, as data: `dp[1] + dp[0] = 1`.
     *
     * [PrefixEquation.result] stays null while the result is still the question,
     * which is what lets one component carry both the walkthrough's statement and
     * Try's prompt without the projector knowing which phase is running — the same
     * rule the hash flow follows (ADR-030).
     *
     * The type is Prefix Sum's by name only; it is a labelled two-operand line and
     * nothing about it is specific to prefix sums. Reusing it is what keeps one
     * equation strip in the design system instead of two that drift.
     */
    private fun equation(state: FibonacciState, justWritten: Int?): PrefixEquation? {
        val i = justWritten ?: state.nextIndex.takeIf { !state.buildComplete } ?: return null
        val a = state.dp.getOrNull(i - 1) ?: return null
        val b = state.dp.getOrNull(i - 2) ?: return null
        return PrefixEquation(
            left = a,
            operator = PrefixOp.PLUS,
            right = b,
            // Known only once the cell actually holds something.
            result = state.dp.getOrNull(i),
            leftLabel = "dp[${i - 1}]",
            rightLabel = "dp[${i - 2}]",
            resultLabel = "dp[$i]",
        )
    }
}
