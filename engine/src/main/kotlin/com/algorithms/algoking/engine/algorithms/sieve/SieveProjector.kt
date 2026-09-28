package com.algorithms.algoking.engine.algorithms.sieve

import com.algorithms.algoking.engine.event.MarkId
import com.algorithms.algoking.engine.event.VizEvent
import com.algorithms.algoking.engine.scene.Badge
import com.algorithms.algoking.engine.scene.Cell
import com.algorithms.algoking.engine.scene.CellState
import com.algorithms.algoking.engine.scene.SceneLayout
import com.algorithms.algoking.engine.scene.SceneProjector
import com.algorithms.algoking.engine.scene.SequenceScene

/**
 * The sieve's picture: 1 … n in six columns, so the multiples of 2 and of 3 fall
 * into straight vertical stripes and the pattern is visible, not just stated.
 *
 * | Means | State |
 * |---|---|
 * | the prime being worked on | `COMPARING` |
 * | crossed out just now, by it | `CANDIDATE` |
 * | a prime | `FINALIZED` |
 * | crossed out earlier — and 1, which is not prime | `ELIMINATED` |
 * | still standing, not decided yet | `IDLE` |
 */
class SieveProjector : SceneProjector<SieveState> {

    override fun project(state: SieveState, activeEvents: List<VizEvent>): SequenceScene {
        val just = state.justCrossed.toSet()
        // The prime whose multiples are highlighted: the one being worked on, or —
        // right after a crossing — the one that just crossed.
        val working = state.current ?: just.firstOrNull()?.let { state.crossedBy[it] }
        return SequenceScene(
            cells = state.numbers.map { k ->
                Cell(
                    key = k,
                    value = k,
                    slot = k - 1,
                    state = when {
                        k == working -> CellState.COMPARING
                        k in just -> CellState.CANDIDATE
                        k == 1 || state.isCrossed(k) -> CellState.ELIMINATED
                        k in state.primes -> CellState.FINALIZED
                        else -> CellState.IDLE
                    },
                )
            },
            layout = SceneLayout.GRID,
            gridColumns = COLUMNS,
            badge = Badge(
                mark = MarkId.BEST,
                label = "Primes found",
                value = state.primes.size,
            ),
            legendLabels = mapOf(
                CellState.COMPARING to "This prime",
                CellState.CANDIDATE to "Just crossed out",
                CellState.FINALIZED to "Prime",
                CellState.ELIMINATED to "Crossed out",
                CellState.IDLE to "Not decided",
            ),
        )
    }

    private companion object {
        /** Six, so every 2nd and every 3rd number lines up in a column. */
        const val COLUMNS = 6
    }
}
