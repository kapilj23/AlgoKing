package com.algorithms.algoking.engine.algorithms.gcd

import com.algorithms.algoking.engine.event.VizEvent
import com.algorithms.algoking.engine.scene.Cell
import com.algorithms.algoking.engine.scene.CellState
import com.algorithms.algoking.engine.scene.ChoiceEmphasis
import com.algorithms.algoking.engine.scene.ChoiceSide
import com.algorithms.algoking.engine.scene.ChoiceStrip
import com.algorithms.algoking.engine.scene.DpTableScene
import com.algorithms.algoking.engine.scene.SceneProjector
import com.algorithms.algoking.engine.scene.TableHeader

/**
 * Euclid's algorithm as a table — one row per step, `a | b | a mod b` — so the pair
 * visibly slides down and to the left: each row's b and remainder become the next
 * row's a and b.
 *
 * While a remainder is being asked for, the two cards under the table do the
 * division the way it is done by hand: how much of a the b's fill, and what is
 * left over.
 *
 * | Means | State |
 * |---|---|
 * | the pair being worked on | `COMPARING` |
 * | the remainder to work out | `GHOST` |
 * | the GCD, once found | `FINALIZED` |
 * | a finished step | `IDLE` |
 */
class EuclidGcdProjector : SceneProjector<GcdState> {

    override fun project(state: GcdState, activeEvents: List<VizEvent>): DpTableScene =
        scene(state, finished = null)

    /**
     * The table, with [finished] as the step just worked out when Watch is narrating
     * it — drawn with its remainder filled in and the working complete, rather than
     * with the next pair's question already open underneath.
     */
    fun scene(state: GcdState, finished: GcdStep?): DpTableScene {
        val showPending = !state.stopped && finished == null
        val rows = state.steps.map { Triple(it.a, it.b, it.r as Int?) } +
            if (showPending) listOf(Triple(state.a, state.b, null)) else emptyList()
        val current = when {
            finished != null -> rows.lastIndex
            state.stopped -> -1
            else -> rows.lastIndex
        }
        // Once stopped, the answer is the last row's b — the last number before the 0.
        val answerRow = if (state.stopped) rows.lastIndex else -1

        val cells = rows.mapIndexed { r, (a, b, rem) ->
            listOf(a, b, rem).mapIndexed { c, value ->
                val slot = r * COLUMNS + c
                when {
                    value == null -> Cell(key = slot, value = 0, slot = slot, state = CellState.GHOST, label = "?")
                    else -> Cell(
                        key = slot,
                        value = value,
                        slot = slot,
                        state = when {
                            r == current && c < 2 -> CellState.COMPARING
                            r == current && c == 2 && finished != null -> CellState.CANDIDATE
                            r == answerRow && c == 1 -> if (state.answered) CellState.FINALIZED else CellState.COMPARING
                            else -> CellState.IDLE
                        },
                    )
                }
            }
        }

        return DpTableScene(
            rowHeaders = rows.indices.map { TableHeader(label = "Step ${it + 1}", active = it == current) },
            columnHeaders = listOf(
                TableHeader("a", active = current >= 0),
                TableHeader("b", active = current >= 0),
                TableHeader("a mod b"),
            ),
            columnCaption = "",
            cells = cells,
            tableVisible = true,
            choice = when {
                finished != null -> working(finished.a, finished.b, finished.r)
                state.stopped -> null
                else -> working(state.a, state.b, null)
            },
            focusCaption = when {
                state.done -> "GCD(${state.start.first}, ${state.start.second}) = ${state.a}"
                finished != null -> "${finished.a} mod ${finished.b} = ${finished.r}"
                state.stopped -> "The remainder is 0 — stop"
                else -> "Divide ${state.a} by ${state.b}, keep what is left over"
            },
            legendLabels = mapOf(
                CellState.COMPARING to if (state.stopped) "Last number before 0" else "This pair",
                CellState.GHOST to "To work out",
                CellState.CANDIDATE to "Remainder",
                CellState.FINALIZED to "GCD",
                CellState.IDLE to "Done",
            ),
        )
    }

    /** The division by hand: how much the b's fill, and what is left. */
    private fun working(a: Int, b: Int, r: Int?): ChoiceStrip {
        val q = a / b
        val fits = b * q
        return ChoiceStrip(
            first = ChoiceSide(
                caption = "$q × $b fits",
                formula = "$b × $q",
                value = fits,
                emphasis = ChoiceEmphasis.OPEN,
            ),
            second = ChoiceSide(
                caption = "Left over",
                formula = "$a − $fits",
                value = r,
                emphasis = if (r == null) ChoiceEmphasis.OPEN else ChoiceEmphasis.CHOSEN,
            ),
            stem = "$a mod $b = ${r ?: "?"}",
        )
    }

    private companion object {
        const val COLUMNS = 3
    }
}
