package com.algorithms.algoking.engine.algorithms.topo

import com.algorithms.algoking.engine.core.Algorithm
import com.algorithms.algoking.engine.core.AlgorithmId
import com.algorithms.algoking.engine.core.Dataset
import com.algorithms.algoking.engine.core.DirectedEdge
import com.algorithms.algoking.engine.core.Graph
import com.algorithms.algoking.engine.core.Probe
import com.algorithms.algoking.engine.core.Transition
import com.algorithms.algoking.engine.decision.Action
import com.algorithms.algoking.engine.decision.ActionOption
import com.algorithms.algoking.engine.decision.Decision
import com.algorithms.algoking.engine.decision.DecisionKind
import com.algorithms.algoking.engine.decision.EdgeListReadout
import com.algorithms.algoking.engine.event.ExamineRole
import com.algorithms.algoking.engine.event.Outcome
import com.algorithms.algoking.engine.event.VizEvent
import com.algorithms.algoking.engine.narration.NarrationId
import com.algorithms.algoking.engine.narration.NarrationKey

/**
 * What the learner does in topological sort.
 *
 * **One question, once per node: which node can go next?** A tap on the graph.
 * The answer is a node with no arrows still coming in — nothing left that has to
 * come before it — and when several qualify, the earliest letter, so that every
 * learner builds the same order. Removing a placed node's arrows is the app's.
 */
sealed interface TopoAction : Action {
    data class Place(val node: String) : TopoAction
}

/**
 * Immutable state — the single source of truth. [placed] is the whole model: an
 * arrow counts only while the node it comes from has not been placed.
 */
data class TopoState(
    val graph: Graph,
    /** `from → to` means from must come before to. */
    val edges: List<DirectedEdge>,
    val placed: List<String>,
) {
    val done: Boolean get() = placed.size == graph.nodes.size

    val lastPlaced: String? get() = placed.lastOrNull()

    /** The unplaced nodes pointing into [node] — what it is still waiting on. */
    fun waitingOn(node: String): List<String> =
        edges.filter { it.to == node && it.from !in placed }.map { it.from }.sorted()

    /** Arrows still coming into [node]: the number drawn inside it. */
    fun arrowsIn(node: String): Int = waitingOn(node).size

    /** Unplaced nodes with nothing coming in, earliest letter first. */
    val ready: List<String>
        get() = graph.ids.filter { it !in placed && arrowsIn(it) == 0 }.sorted()

    /** **The rule**: the earliest-lettered node with no arrows coming in. */
    val next: String? get() = ready.firstOrNull()

    /** True when nodes remain but none is free — every one waits on another: a cycle. */
    val stuck: Boolean get() = !done && ready.isEmpty()

    /** The nodes placing [node] would free: it is the last thing each waits on. */
    fun wouldFree(node: String): List<String> =
        edges.filter { it.from == node }.map { it.to }.distinct()
            .filter { it !in placed && waitingOn(it) == listOf(node) }
            .sorted()

    /** One row per node, alphabetical: its count, and what it waits on or where it was placed. */
    fun readout(): EdgeListReadout = EdgeListReadout(
        rows = graph.ids.sorted().map { node ->
            val at = placed.indexOf(node)
            val waiting = waitingOn(node)
            EdgeListReadout.Row(
                label = node,
                weight = waiting.size,
                status = when {
                    at >= 0 -> EdgeListReadout.Status.TAKEN
                    waiting.isEmpty() -> EdgeListReadout.Status.CHECKED
                    else -> EdgeListReadout.Status.WAITING
                },
                note = when {
                    at >= 0 -> "placed ${ordinal(at + 1)}"
                    waiting.isEmpty() -> "ready — nothing coming in"
                    else -> "waiting on ${waiting.joinToString(", ")}"
                },
            )
        },
        groups = emptyList(),
        title = "Arrows still coming in",
        footer = "Order so far: " + (placed.takeIf { it.isNotEmpty() }?.joinToString(" → ") ?: "—"),
    )
}

private fun ordinal(n: Int): String = n.toString() + when {
    n % 100 in 11..13 -> "th"
    n % 10 == 1 -> "st"
    n % 10 == 2 -> "nd"
    n % 10 == 3 -> "rd"
    else -> "th"
}

/**
 * Topological sort — Kahn's method — an Advanced lesson.
 *
 * ```
 * count the arrows coming into every node
 * repeat:
 *     take a node with none coming in           ← nothing has to come before it
 *     put it next in the order
 *     remove its arrows — the nodes they pointed at each have one fewer
 * if nodes remain but none is free, there is a cycle and no order exists
 * ```
 *
 * ### What the learner has to understand
 *
 * 1. **A node can go only when nothing points into it.** The count inside each
 *    node is exactly that, and it is the whole decision.
 * 2. **Placing a node frees others.** Its arrows go, and a node whose last arrow
 *    that was is now free.
 * 3. **More than one order can be right.** When two nodes are free together,
 *    either could go first; the lesson takes the earliest letter only so that
 *    everyone builds the same order.
 *
 * **Time O(V + E)**; **space O(V)**.
 */
class TopologicalSortAlgorithm : Algorithm<TopoState, TopoAction> {

    override val id = AlgorithmId.TOPOLOGICAL_SORT

    override fun initial(dataset: Dataset): TopoState = TopoState(
        graph = dataset.graph ?: Graph(emptyList(), emptyMap()),
        edges = dataset.directedEdges,
        placed = emptyList(),
    )

    override fun probe(state: TopoState): Probe<TopoAction> = when {
        state.done -> Probe.Terminal(Outcome.Completed(true))
        state.stuck -> Probe.Terminal(Outcome.NotFound)
        else -> Probe.Decide(decision(state))
    }

    private fun decision(state: TopoState): Decision<TopoAction> {
        val next = requireNotNull(state.next)
        val ready = state.ready
        val order = state.graph.ids
        val freed = state.wouldFree(next)

        return Decision(
            kind = DecisionKind.CELL,
            prompt = NarrationKey(NarrationId.TS_ASK),
            options = order.mapIndexed { slot, node ->
                ActionOption<TopoAction>(
                    action = TopoAction.Place(node),
                    label = NarrationKey(NarrationId.TS_OPTION_NODE, listOf(node)),
                    slot = slot,
                )
            },
            correct = TopoAction.Place(next),
            focus = listOf(state.graph.indexOf(next)),
            hint = NarrationKey(NarrationId.TS_HINT),
            guidance = listOf(
                NarrationKey(NarrationId.TS_RETRY_LOOK),
                NarrationKey(NarrationId.TS_RETRY_ASK),
                NarrationKey(
                    if (ready.size > 1) NarrationId.TS_RETRY_EXPLAIN_TIE else NarrationId.TS_RETRY_EXPLAIN,
                    listOf(next, ready.joinToString(" and ")),
                ),
            ),
            minimalFeedback = NarrationKey(NarrationId.TS_RETRY_LOOK),
            whyWrong = buildMap {
                for (node in order) {
                    if (node == next) continue
                    val key = when {
                        node in state.placed -> NarrationKey(NarrationId.TS_WHY_PLACED, listOf(node))
                        node in ready -> NarrationKey(NarrationId.TS_WHY_NOT_FIRST, listOf(node, next))
                        else -> {
                            val waiting = state.waitingOn(node)
                            NarrationKey(
                                NarrationId.TS_WHY_WAITING,
                                listOf(node, waiting.size, waiting.joinToString(" and ")),
                            )
                        }
                    }
                    put(TopoAction.Place(node), key)
                }
            },
            correctFeedback = NarrationKey(
                NarrationId.TS_CORRECT,
                listOf(next, freed.joinToString(" and "), freed.size),
            ),
            hintLadder = listOf(NarrationKey(NarrationId.TS_HINT), NarrationKey(NarrationId.TS_RETRY_ASK)),
            edgeList = state.readout(),
            // Which node is free is the whole idea. Never the app's.
            autoInTry = false,
        )
    }

    override fun apply(state: TopoState, action: TopoAction): Transition<TopoState> {
        val node = (action as TopoAction.Place).node
        val slot = state.graph.indexOf(node)
        // Placing anything but the free, earliest node is refused, not applied.
        if (node != state.next) {
            return Transition(
                next = state,
                events = if (slot >= 0) listOf(VizEvent.Examine(listOf(slot), ExamineRole.INSPECTING)) else emptyList(),
                narration = null,
                correct = false,
            )
        }
        val freed = state.wouldFree(node)
        val next = state.copy(placed = state.placed + node)
        return Transition(
            next = next,
            events = buildList {
                add(VizEvent.Examine(listOf(slot), ExamineRole.COMPARING))
                add(VizEvent.Finalize(slot..slot))
                if (next.done) add(VizEvent.Terminal(Outcome.Completed(true)))
                else if (next.stuck) add(VizEvent.Terminal(Outcome.NotFound))
            },
            narration = NarrationKey(NarrationId.TS_PLACED, listOf(node, freed.joinToString(" and "), freed.size)),
            correct = true,
        )
    }
}
