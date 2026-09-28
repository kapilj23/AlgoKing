package com.algorithms.algoking.engine.algorithms.floydwarshall

import com.algorithms.algoking.engine.event.VizEvent
import com.algorithms.algoking.engine.scene.Cell
import com.algorithms.algoking.engine.scene.CellState
import com.algorithms.algoking.engine.scene.ChoiceEmphasis
import com.algorithms.algoking.engine.scene.ChoiceSide
import com.algorithms.algoking.engine.scene.ChoiceStrip
import com.algorithms.algoking.engine.scene.DpTableScene
import com.algorithms.algoking.engine.scene.EdgeState
import com.algorithms.algoking.engine.scene.GraphEdgeView
import com.algorithms.algoking.engine.scene.GraphNodeView
import com.algorithms.algoking.engine.scene.GraphScene
import com.algorithms.algoking.engine.scene.SceneProjector
import com.algorithms.algoking.engine.scene.TableHeader

/**
 * Floyd–Warshall presentation knowledge.
 *
 * The distance table is the algorithm, so it is the `DpTableScene` Knapsack uses —
 * rows are "from", columns are "to" — with the roads drawn small above it, so a
 * number can always be traced back to a route.
 *
 * One colour language across the picture and the table:
 *
 * | Means | Table cell | Graph |
 * |---|---|---|
 * | the pair being checked | `CANDIDATE` (orange) | its two ends orange |
 * | the two legs of the detour | `COMPARING` (purple) | the via node purple |
 * | just improved | `FINALIZED` (green) | — |
 *
 * And the two-card strip under the table *is* the question: the distance known
 * now against the detour, written out as its sum.
 */
class FloydWarshallProjector : SceneProjector<FwState> {

    override fun project(state: FwState, activeEvents: List<VizEvent>): DpTableScene =
        scene(state, focus = state.current, decided = false)

    /**
     * The picture around [focus]. Watch narrates a pair *after* it was decided,
     * when the cursor has already moved on, so it passes the decided pair and
     * `decided = true` to show which card won.
     */
    fun scene(state: FwState, focus: Pair<Int, Int>?, decided: Boolean, before: FwState? = null): DpTableScene {
        val via = state.via.takeIf { !state.finished && state.viaStarted }
        val legs = if (focus != null && via != null) {
            setOf(focus.first to via, via to focus.second)
        } else {
            emptySet()
        }
        val improved = state.lastImproved

        val cells = (0 until state.n).map { r ->
            (0 until state.n).map { c ->
                val d = state.d(r, c)
                val slot = state.slotOf(r, c)
                Cell(
                    key = slot,
                    value = d ?: 0,
                    slot = slot,
                    state = when {
                        improved != null && decided && (r to c) == improved -> CellState.FINALIZED
                        focus != null && (r to c) == focus -> CellState.CANDIDATE
                        (r to c) in legs -> CellState.COMPARING
                        else -> CellState.IDLE
                    },
                    label = distanceLabel(d),
                )
            }
        }

        return DpTableScene(
            rowHeaders = state.ids.mapIndexed { i, id ->
                TableHeader(label = id, active = i == via)
            },
            columnHeaders = state.ids.mapIndexed { i, id ->
                TableHeader(label = id, active = i == via)
            },
            columnCaption = "From → To",
            cells = cells,
            tableVisible = true,
            choice = choice(before ?: state, focus, decided),
            focusCaption = when {
                state.finished -> "Every pair holds its shortest distance"
                via != null -> "Round ${via + 1} of ${state.n}: going through ${state.ids[via]}"
                else -> "The direct roads — ∞ where there is no road"
            },
            graph = graph(state, focus, via),
            legendLabels = mapOf(
                CellState.CANDIDATE to "Pair being checked",
                CellState.COMPARING to "Route through ${via?.let { state.ids[it] } ?: "the via node"}",
                CellState.FINALIZED to "Just improved",
                CellState.IDLE to "Known",
            ),
        )
    }

    /** "Now" against "Through k", with the sum written out. */
    private fun choice(state: FwState, focus: Pair<Int, Int>?, decided: Boolean): ChoiceStrip? {
        val (i, j) = focus ?: return null
        val k = state.viaNode ?: return null
        val a = state.name(i)
        val b = state.name(j)
        val now = state.d(i, j)
        val legA = state.d(i, state.via)
        val legB = state.d(state.via, j)
        val through = state.detour(i, j)
        val better = state.improves(i, j)
        return ChoiceStrip(
            first = ChoiceSide(
                caption = "Now",
                formula = "$a → $b",
                value = now,
                emphasis = when {
                    !decided -> ChoiceEmphasis.OPEN
                    better -> ChoiceEmphasis.PASSED
                    else -> ChoiceEmphasis.CHOSEN
                },
                valueLabel = distanceLabel(now),
            ),
            second = ChoiceSide(
                caption = "Through $k",
                formula = "${distanceLabel(legA)} + ${distanceLabel(legB)}",
                value = through,
                emphasis = when {
                    !decided -> ChoiceEmphasis.OPEN
                    better -> ChoiceEmphasis.CHOSEN
                    else -> ChoiceEmphasis.PASSED
                },
                valueLabel = distanceLabel(through),
            ),
            stem = "Shortest $a → $b?",
        )
    }

    /** The roads, small: the via node purple, the pair being checked orange. */
    private fun graph(state: FwState, focus: Pair<Int, Int>?, via: Int?): GraphScene {
        val ends = focus?.let { setOf(it.first, it.second) }.orEmpty()
        return GraphScene(
            nodes = state.graph.nodes.mapIndexed { index, node ->
                GraphNodeView(
                    slot = index,
                    label = node.label,
                    state = when (index) {
                        via -> CellState.COMPARING
                        in ends -> CellState.CANDIDATE
                        else -> CellState.IDLE
                    },
                    x = node.x,
                    y = node.y,
                )
            },
            edges = state.graph.edges.map { (a, b) ->
                val ia = state.graph.indexOf(a)
                val ib = state.graph.indexOf(b)
                // The two roads of the detour, when both are direct roads.
                val leg = via != null && focus != null &&
                    (setOf(ia, ib) == setOf(focus.first, via) || setOf(ia, ib) == setOf(via, focus.second))
                GraphEdgeView(
                    from = ia,
                    to = ib,
                    state = if (leg) EdgeState.ACTIVE else EdgeState.IDLE,
                    label = state.graph.weightOf(a, b)?.toString(),
                )
            },
            traversal = emptyList(),
            stack = emptyList(),
            showPathStrip = false,
            showStrips = false,
        )
    }
}
