package com.ttele.algoking.engine.algorithms.dijkstra

import com.ttele.algoking.engine.core.Algorithm
import com.ttele.algoking.engine.core.AlgorithmId
import com.ttele.algoking.engine.core.Dataset
import com.ttele.algoking.engine.core.Graph
import com.ttele.algoking.engine.core.Probe
import com.ttele.algoking.engine.core.Transition
import com.ttele.algoking.engine.decision.Action
import com.ttele.algoking.engine.decision.ActionOption
import com.ttele.algoking.engine.decision.Decision
import com.ttele.algoking.engine.decision.DecisionKind
import com.ttele.algoking.engine.event.EliminateReason
import com.ttele.algoking.engine.event.ExamineRole
import com.ttele.algoking.engine.event.MeterId
import com.ttele.algoking.engine.event.Outcome
import com.ttele.algoking.engine.event.VizEvent
import com.ttele.algoking.engine.narration.NarrationId
import com.ttele.algoking.engine.narration.NarrationKey

/**
 * What the learner does in Dijkstra.
 *
 * Two questions, alternating, and both are the algorithm:
 *
 * 1. **which node is processed next?** — a tap on the graph, the DFS/BFS gesture
 * 2. **what should this neighbour's distance become?** — three numbers, and the
 *    two wrong ones are the two mistakes people actually make
 *
 * Everything else is the app's: initialising, walking to the next neighbour in the
 * authored order, doing the addition out loud, and marking a node settled.
 */
sealed interface DijkstraAction : Action {

    /** Begin processing [node]. Correct only for the cheapest node not yet settled. */
    data class Select(val node: String) : DijkstraAction

    /** Read `distance(current) + weight` for the next neighbour. Mechanical. */
    data object Examine : DijkstraAction

    /**
     * The relaxation: what [value] the pending neighbour's distance ends up as.
     * Correct only for the true outcome — the candidate when it is an improvement,
     * the existing distance when it is not.
     */
    data class SetDistance(val value: Int) : DijkstraAction

    /** A neighbour with no distance at all takes the candidate. Mechanical: ∞ loses to everything. */
    data object FirstReach : DijkstraAction

    /** Step past a neighbour that is already settled. Mechanical. */
    data object SkipSettled : DijkstraAction

    /** Mark the current node settled and look for the next one. Mechanical. */
    data object Settle : DijkstraAction
}

/** The edge under consideration, once the app has read it. */
data class Relaxation(val from: String, val to: String, val weight: Int, val candidate: Int)

/**
 * Immutable state — the single source of truth.
 *
 * [distances] is the whole model: a node absent from it is at ∞. Everything a
 * screen shows is read from here, and **no Composable performs an addition or a
 * comparison** — the frontier, the next node, the option values and the path are
 * all derived below.
 */
data class DijkstraState(
    val graph: Graph,
    val start: String,
    val target: String,
    /** Tentative distance per node. Absent means ∞ — not yet reached. */
    val distances: Map<String, Int>,
    /** How each node's current best distance was reached. The path lives here. */
    val predecessors: Map<String, String>,
    /** Settled, in the order they were settled. Their distances are final. */
    val processed: List<String>,
    /** The node being processed, or null while the next one is being chosen. */
    val current: String?,
    /** How far through [current]'s neighbours the walk has got. */
    val edgeCursor: Int,
    /** The candidate on the table, set by [DijkstraAction.Examine]. */
    val pending: Relaxation?,
) {
    /** Reached but not settled — the frontier. These are the amber nodes. */
    val frontier: List<String>
        get() = distances.keys.filter { it !in processed }.sortedBy { distances[it] }

    /** **The selection rule, in one line.** Null when there is nothing left to reach. */
    val cheapest: String? get() = frontier.minByOrNull { distances.getValue(it) }

    /** The neighbours of [current], in the graph's authored order. */
    val currentNeighbours: List<String>
        get() = current?.let { graph.neighbours(it) }.orEmpty()

    /** The neighbour the cursor is on, or null once they are exhausted. */
    val nextNeighbour: String? get() = currentNeighbours.getOrNull(edgeCursor)

    fun distanceOf(node: String): Int? = distances[node]

    val finished: Boolean get() = target in processed || (current == null && frontier.isEmpty())

    /** True when the run ended without ever reaching the target. */
    val unreachable: Boolean get() = finished && target !in processed

    /**
     * The best known route to [node], start first — **reconstructed, never stored.**
     * Empty until the node has been reached at all.
     */
    fun pathTo(node: String): List<String> {
        if (node !in distances) return emptyList()
        val route = ArrayDeque<String>()
        var step: String? = node
        var guard = 0
        while (step != null && guard++ <= graph.nodes.size) {
            route.addFirst(step)
            step = predecessors[step]
        }
        return route.toList()
    }

    /** The edges of the current best-known-route tree, for the picture. */
    val routeEdges: Set<Pair<String, String>>
        get() = predecessors.entries.map { (node, from) -> from to node }.toSet()

    /**
     * The three values a relaxation offers, in a stable order.
     *
     * The correct one, plus the two real mistakes: **the edge weight on its own**
     * (forgetting to add where you already are) and **the value that does not
     * change** — or, when keeping is correct, the candidate that should have been
     * refused. Distinct, so a duplicate never appears as two buttons.
     */
    fun relaxationOptions(): List<Int> {
        val r = pending ?: return emptyList()
        val existing = distances[r.to]
        return listOfNotNull(r.candidate, r.weight, existing).distinct()
    }

    /** What the pending relaxation should produce. */
    val relaxedValue: Int?
        get() {
            val r = pending ?: return null
            val existing = distances[r.to] ?: return r.candidate
            return minOf(r.candidate, existing)
        }

    /** True when the pending candidate is an improvement on what is already known. */
    val improves: Boolean
        get() {
            val r = pending ?: return false
            val existing = distances[r.to] ?: return true
            return r.candidate < existing
        }
}

/**
 * Dijkstra's algorithm — an Advanced lesson, and the third graph one.
 *
 * ```
 * every node starts at ∞ except the start, which is 0
 * repeat:
 *     take the cheapest node not yet settled
 *     for each of its neighbours:
 *         candidate = distance(node) + weight(node, neighbour)
 *         if candidate < distance(neighbour):  distance(neighbour) = candidate
 *     settle the node
 * ```
 *
 * ### What the learner has to understand
 *
 * Not the loop, which is memorisable. Two things:
 *
 * 1. **A distance is a claim, not a fact** — until every cheaper route has been
 *    tried, it is only the best thing known so far, and relaxation is what beats
 *    it down. The teaching graph opens by beating one: B is reached at 5 and
 *    settles at 3.
 * 2. **Why taking the cheapest is safe.** Every edge costs something, so a route
 *    through anything still unsettled is already at least as long. That is the
 *    entire proof, and it is also exactly the sentence a negative edge falsifies.
 *
 * ### Where it sits
 *
 * DFS goes deep, BFS goes level by level, and Dijkstra goes by distance. On a
 * graph where every edge costs 1, the cheapest unsettled node is always the
 * nearest one — so **Dijkstra is BFS**, and the weights are the whole reason a
 * different algorithm exists.
 *
 * **Time O((V + E) log V)** with a binary heap; **space O(V)**.
 */
class DijkstraAlgorithm : Algorithm<DijkstraState, DijkstraAction> {

    override val id = AlgorithmId.DIJKSTRA

    override fun initial(dataset: Dataset): DijkstraState {
        val graph = dataset.graph ?: Graph(emptyList(), emptyMap())
        val start = dataset.startNode
            ?.takeIf { graph.node(it) != null }
            ?: graph.nodes.firstOrNull()?.id.orEmpty()
        val target = dataset.targetNode
            ?.takeIf { graph.node(it) != null }
            ?: graph.nodes.lastOrNull()?.id.orEmpty()

        return DijkstraState(
            graph = graph,
            start = start,
            target = target,
            // The start is 0 and everything else is absent, which is ∞. Nothing is
            // claimed about a node the algorithm has not reached.
            distances = if (graph.node(start) != null) mapOf(start to 0) else emptyMap(),
            predecessors = emptyMap(),
            processed = emptyList(),
            current = null,
            edgeCursor = 0,
            pending = null,
        )
    }

    override fun probe(state: DijkstraState): Probe<DijkstraAction> {
        // A candidate on the table is the question that matters most.
        if (state.pending != null) return Probe.Decide(relaxDecision(state))

        if (state.current != null) {
            val neighbour = state.nextNeighbour
                ?: return Probe.Mechanical(DijkstraAction.Settle)
            return when {
                // Settled already: its distance is final, so nothing here can move.
                neighbour in state.processed -> Probe.Mechanical(DijkstraAction.SkipSettled)
                // No distance at all: ∞ loses to everything, so there is nothing to
                // compare and nothing to ask (PRODUCT_SPEC.md §3).
                neighbour !in state.distances -> Probe.Mechanical(DijkstraAction.FirstReach)
                else -> Probe.Mechanical(DijkstraAction.Examine)
            }
        }

        if (state.target in state.processed) {
            return Probe.Terminal(Outcome.Found(state.graph.indexOf(state.target)))
        }
        // Nothing reachable left, and the target was never reached.
        if (state.frontier.isEmpty()) return Probe.Terminal(Outcome.NotFound)

        return Probe.Decide(selectDecision(state))
    }

    // -- Decisions ------------------------------------------------------------

    private fun selectDecision(state: DijkstraState): Decision<DijkstraAction> {
        val correct = requireNotNull(state.cheapest)
        val order = state.graph.ids
        val best = state.distances.getValue(correct)

        return Decision(
            kind = DecisionKind.CELL,
            prompt = NarrationKey(NarrationId.DIJ_ASK_SELECT),
            // Every node is tappable. Offering only the frontier would answer half
            // the question by outlining where to look.
            options = order.mapIndexed { slot, node ->
                ActionOption<DijkstraAction>(
                    action = DijkstraAction.Select(node),
                    label = NarrationKey(NarrationId.DIJ_OPTION_NODE, listOf(node)),
                    slot = slot,
                )
            },
            correct = DijkstraAction.Select(correct),
            focus = listOf(state.graph.indexOf(correct)),
            hint = NarrationKey(NarrationId.DIJ_HINT_SELECT),
            guidance = listOf(
                NarrationKey(NarrationId.DIJ_RETRY_SELECT_LOOK),
                NarrationKey(NarrationId.DIJ_RETRY_SELECT_ASK),
                NarrationKey(NarrationId.DIJ_RETRY_SELECT_EXPLAIN, listOf(correct, best)),
            ),
            minimalFeedback = NarrationKey(NarrationId.DIJ_RETRY_SELECT_LOOK),
            whyWrong = buildMap {
                for (node in order) {
                    if (node == correct) continue
                    val theirs = state.distances[node]
                    val id = when {
                        node in state.processed -> NarrationId.DIJ_WHY_ALREADY_SETTLED
                        theirs == null -> NarrationId.DIJ_WHY_UNREACHED
                        else -> NarrationId.DIJ_WHY_NOT_CHEAPEST
                    }
                    put(
                        DijkstraAction.Select(node),
                        NarrationKey(id, listOf(node, theirs ?: 0, correct, best)),
                    )
                }
            },
            correctFeedback = NarrationKey(
                NarrationId.DIJ_CORRECT_SELECT,
                listOf(correct, best),
            ),
            hintLadder = listOf(
                NarrationKey(NarrationId.DIJ_HINT_SELECT),
                NarrationKey(NarrationId.DIJ_RETRY_SELECT_ASK),
            ),
            // Which node is cheapest is the rule the algorithm turns on. Never the app's.
            autoInTry = false,
        )
    }

    private fun relaxDecision(state: DijkstraState): Decision<DijkstraAction> {
        val r = requireNotNull(state.pending)
        val existing = state.distances[r.to]
        val correct = requireNotNull(state.relaxedValue)
        val options = state.relaxationOptions()

        return Decision(
            kind = DecisionKind.OPTIONS,
            prompt = NarrationKey(
                NarrationId.DIJ_ASK_RELAX,
                listOf(r.to, existing ?: 0, r.from, r.weight, r.candidate),
            ),
            options = options.map { value ->
                ActionOption<DijkstraAction>(
                    action = DijkstraAction.SetDistance(value),
                    label = NarrationKey(NarrationId.DIJ_OPTION_VALUE, listOf(value)),
                )
            },
            correct = DijkstraAction.SetDistance(correct),
            focus = listOfNotNull(
                state.graph.indexOf(r.from).takeIf { it >= 0 },
                state.graph.indexOf(r.to).takeIf { it >= 0 },
            ),
            hint = NarrationKey(
                NarrationId.DIJ_HINT_RELAX,
                listOf(r.from, state.distances[r.from] ?: 0, r.weight, r.candidate),
            ),
            guidance = listOf(
                NarrationKey(NarrationId.DIJ_RETRY_RELAX_LOOK, listOf(r.candidate, r.to, existing ?: 0)),
                NarrationKey(
                    if (state.improves) {
                        NarrationId.DIJ_RETRY_RELAX_ASK_BETTER
                    } else {
                        NarrationId.DIJ_RETRY_RELAX_ASK_WORSE
                    },
                    listOf(r.candidate, existing ?: 0),
                ),
                NarrationKey(
                    if (state.improves) {
                        NarrationId.DIJ_RETRY_RELAX_EXPLAIN_UPDATE
                    } else {
                        NarrationId.DIJ_RETRY_RELAX_EXPLAIN_KEEP
                    },
                    listOf(r.candidate, existing ?: 0, r.to),
                ),
            ),
            minimalFeedback = NarrationKey(
                NarrationId.DIJ_RETRY_RELAX_LOOK,
                listOf(r.candidate, r.to, existing ?: 0),
            ),
            // Each wrong number is the mistake it encodes, named for what the
            // learner actually did rather than restating the rule at them.
            whyWrong = buildMap {
                for (value in options) {
                    if (value == correct) continue
                    val id = when {
                        value == r.weight && value != r.candidate -> NarrationId.DIJ_WHY_WEIGHT_ONLY
                        state.improves -> NarrationId.DIJ_WHY_MISSED_IMPROVEMENT
                        else -> NarrationId.DIJ_WHY_WORSE_ROUTE
                    }
                    put(
                        DijkstraAction.SetDistance(value),
                        NarrationKey(
                            id,
                            listOf(value, r.to, r.from, r.weight, r.candidate, existing ?: 0),
                        ),
                    )
                }
            },
            correctFeedback = NarrationKey(
                if (state.improves) {
                    NarrationId.DIJ_CORRECT_UPDATE
                } else {
                    NarrationId.DIJ_CORRECT_KEEP
                },
                listOf(r.to, r.candidate, existing ?: 0, r.from),
            ),
            hintLadder = listOf(
                NarrationKey(
                    NarrationId.DIJ_HINT_RELAX,
                    listOf(r.from, state.distances[r.from] ?: 0, r.weight, r.candidate),
                ),
            ),
            // The comparison is the whole technique. The app does the addition and
            // says it out loud; deciding what it means is always the learner's.
            autoInTry = false,
        )
    }

    // -- Transitions ----------------------------------------------------------

    override fun apply(
        state: DijkstraState,
        action: DijkstraAction,
    ): Transition<DijkstraState> = when (action) {
        is DijkstraAction.Select -> select(state, action.node)
        DijkstraAction.Examine -> examine(state)
        is DijkstraAction.SetDistance -> setDistance(state, action.value)
        DijkstraAction.FirstReach -> firstReach(state)
        DijkstraAction.SkipSettled -> skipSettled(state)
        DijkstraAction.Settle -> settle(state)
    }

    /**
     * Selecting anything but the cheapest is **refused**, not applied — the same
     * refusal Two Pointers gives a false "pair found". In Try it never gets here,
     * because a `Retry` carries no action (ARCHITECTURE.md §6.1).
     */
    private fun select(state: DijkstraState, node: String): Transition<DijkstraState> {
        if (state.current != null || state.cheapest != node) {
            return refuse(state, state.graph.indexOf(node))
        }
        val distance = state.distances.getValue(node)
        return Transition(
            next = state.copy(current = node, edgeCursor = 0, pending = null),
            events = listOf(
                VizEvent.Examine(listOf(state.graph.indexOf(node)), ExamineRole.COMPARING),
                VizEvent.Meter(MeterId.DISTANCE, distance.toLong()),
            ),
            narration = NarrationKey(NarrationId.DIJ_SELECTED, listOf(node, distance)),
            correct = true,
        )
    }

    private fun examine(state: DijkstraState): Transition<DijkstraState> {
        val from = state.current ?: return unchanged(state)
        val to = state.nextNeighbour ?: return unchanged(state)
        val weight = state.graph.weightOf(from, to) ?: return unchanged(state)
        val candidate = state.distances.getValue(from) + weight

        return Transition(
            next = state.copy(pending = Relaxation(from, to, weight, candidate)),
            events = listOf(
                VizEvent.Examine(listOf(state.graph.indexOf(to)), ExamineRole.CANDIDATE),
            ),
            // States the arithmetic and stops. What it means is the question.
            narration = NarrationKey(
                NarrationId.DIJ_EXAMINED,
                listOf(from, state.distances.getValue(from), weight, candidate, to),
            ),
            correct = true,
        )
    }

    private fun setDistance(state: DijkstraState, value: Int): Transition<DijkstraState> {
        val r = state.pending ?: return unchanged(state)
        if (value != state.relaxedValue) return refuse(state, state.graph.indexOf(r.to))

        val improved = state.improves
        val next = state.copy(
            distances = if (improved) state.distances + (r.to to r.candidate) else state.distances,
            // The predecessor moves with the distance: the path *is* this map.
            predecessors = if (improved) state.predecessors + (r.to to r.from) else state.predecessors,
            pending = null,
            edgeCursor = state.edgeCursor + 1,
        )
        return Transition(
            next = next,
            events = buildList {
                val slot = state.graph.indexOf(r.to)
                if (improved) {
                    add(VizEvent.Examine(listOf(slot), ExamineRole.CANDIDATE))
                    add(VizEvent.Meter(MeterId.BEST, r.candidate.toLong()))
                } else {
                    // Examined and deliberately left alone — Hold is exactly this.
                    add(VizEvent.Hold(listOf(slot)))
                }
            },
            narration = NarrationKey(
                if (improved) NarrationId.DIJ_UPDATED else NarrationId.DIJ_KEPT,
                listOf(r.to, r.candidate, state.distances[r.to] ?: 0, r.from),
            ),
            correct = true,
        )
    }

    private fun firstReach(state: DijkstraState): Transition<DijkstraState> {
        val from = state.current ?: return unchanged(state)
        val to = state.nextNeighbour ?: return unchanged(state)
        val weight = state.graph.weightOf(from, to) ?: return unchanged(state)
        val candidate = state.distances.getValue(from) + weight

        return Transition(
            next = state.copy(
                distances = state.distances + (to to candidate),
                predecessors = state.predecessors + (to to from),
                edgeCursor = state.edgeCursor + 1,
            ),
            events = listOf(
                VizEvent.Examine(listOf(state.graph.indexOf(to)), ExamineRole.CANDIDATE),
                VizEvent.Meter(MeterId.BEST, candidate.toLong()),
            ),
            narration = NarrationKey(
                NarrationId.DIJ_FIRST_REACH,
                listOf(to, candidate, from, weight),
            ),
            correct = true,
        )
    }

    /** Where "settled means final" is taught, one neighbour at a time. */
    private fun skipSettled(state: DijkstraState): Transition<DijkstraState> {
        val to = state.nextNeighbour ?: return unchanged(state)
        return Transition(
            next = state.copy(edgeCursor = state.edgeCursor + 1),
            events = listOf(
                VizEvent.Examine(listOf(state.graph.indexOf(to)), ExamineRole.INSPECTING),
            ),
            narration = NarrationKey(
                NarrationId.DIJ_SKIP_SETTLED,
                listOf(to, state.distances[to] ?: 0),
            ),
            correct = true,
        )
    }

    private fun settle(state: DijkstraState): Transition<DijkstraState> {
        val node = state.current ?: return unchanged(state)
        val next = state.copy(
            processed = state.processed + node,
            current = null,
            edgeCursor = 0,
            pending = null,
        )
        val slot = state.graph.indexOf(node)
        return Transition(
            next = next,
            events = buildList {
                add(VizEvent.Finalize(slot..slot))
                // Nothing can improve it now — that is what settled means.
                add(VizEvent.Eliminate(slot..slot, EliminateReason.ALREADY_SORTED))
                if (next.finished) {
                    add(
                        VizEvent.Terminal(
                            if (next.unreachable) {
                                Outcome.NotFound
                            } else {
                                Outcome.Found(state.graph.indexOf(next.target))
                            },
                        ),
                    )
                }
            },
            narration = NarrationKey(
                if (node == state.target) NarrationId.DIJ_TARGET_SETTLED else NarrationId.DIJ_SETTLED,
                listOf(node, state.distances.getValue(node)),
            ),
            correct = true,
        )
    }

    private fun unchanged(state: DijkstraState) =
        Transition(state, emptyList(), null, correct = false)

    private fun refuse(state: DijkstraState, slot: Int) = Transition(
        next = state,
        events = if (slot >= 0) {
            listOf(VizEvent.Examine(listOf(slot), ExamineRole.INSPECTING))
        } else {
            emptyList()
        },
        narration = null,
        correct = false,
    )
}
