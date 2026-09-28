package com.algorithms.algoking.engine.algorithms.prim

import com.algorithms.algoking.engine.core.Algorithm
import com.algorithms.algoking.engine.core.AlgorithmId
import com.algorithms.algoking.engine.core.Dataset
import com.algorithms.algoking.engine.core.Graph
import com.algorithms.algoking.engine.core.Probe
import com.algorithms.algoking.engine.core.Transition
import com.algorithms.algoking.engine.decision.Action
import com.algorithms.algoking.engine.decision.ActionOption
import com.algorithms.algoking.engine.decision.CheapestReadout
import com.algorithms.algoking.engine.decision.Decision
import com.algorithms.algoking.engine.decision.DecisionKind
import com.algorithms.algoking.engine.event.ExamineRole
import com.algorithms.algoking.engine.event.MeterId
import com.algorithms.algoking.engine.event.Outcome
import com.algorithms.algoking.engine.event.VizEvent
import com.algorithms.algoking.engine.narration.NarrationId
import com.algorithms.algoking.engine.narration.NarrationKey

/**
 * What the learner does in Prim.
 *
 * **One question, asked once per node: which node joins the tree next?** A tap on
 * the graph, the Dijkstra gesture. Everything else — listing the edges that leave
 * the tree, adding up the total, ruling out an edge that would make a loop — is the
 * app's, and it is all said out loud.
 */
sealed interface PrimAction : Action {

    /** Bring [node] into the tree. Correct only for the node the cheapest crossing edge reaches. */
    data class Add(val node: String) : PrimAction
}

/** One edge of the graph, read from the tree's side: [from] is in the tree. */
data class PrimEdge(val from: String, val to: String, val weight: Int) {
    val label: String get() = "$from – $to"
}

/**
 * Immutable state — the single source of truth.
 *
 * [inTree] is the whole model. The edges leaving the tree, the cheapest of them,
 * the loops and the total are all derived below, so **no Composable performs a
 * comparison or an addition**.
 */
data class PrimState(
    val graph: Graph,
    val start: String,
    /** In the tree, in the order they joined. */
    val inTree: List<String>,
    /** The edges chosen so far, in the order they were chosen. */
    val treeEdges: List<PrimEdge>,
) {
    /** The node that joined most recently, or null before anything but the start has. */
    val lastAdded: String? get() = treeEdges.lastOrNull()?.to

    /** What the tree costs so far. */
    val total: Int get() = treeEdges.sumOf { it.weight }

    /**
     * **Every edge with one end in the tree and one end outside it.** These are the
     * only edges Prim ever considers.
     *
     * Listed by tree node in the order they joined, then in the graph's authored
     * order — never sorted by weight, which would put the answer first.
     */
    val crossing: List<PrimEdge>
        get() = inTree.flatMap { from ->
            graph.neighbours(from)
                .filter { it !in inTree }
                .map { to -> PrimEdge(from, to, graph.weightOf(from, to) ?: 0) }
        }

    /** **The rule, in one line.** First in [crossing] order on a tie. */
    val cheapest: PrimEdge? get() = crossing.minByOrNull { it.weight }

    /**
     * Edges with both ends in the tree that are not in it — each would only close
     * a loop. Written `A – B`, in the graph's own edge order.
     */
    val loops: List<String>
        get() {
            val used = treeEdges.map { setOf(it.from, it.to) }.toSet()
            return graph.edges
                .filter { (a, b) -> a in inTree && b in inTree && setOf(a, b) !in used }
                .map { (a, b) -> "$a – $b" }
        }

    /** The cheapest edge from the tree to [node], or null when none reaches it. */
    fun bestEdgeTo(node: String): PrimEdge? =
        crossing.filter { it.to == node }.minByOrNull { it.weight }

    /** True once every node is in, or nothing more can be reached. */
    val finished: Boolean get() = inTree.size == graph.nodes.size || crossing.isEmpty()

    /** True when the finished tree reaches every node. */
    val spanning: Boolean get() = inTree.size == graph.nodes.size

    /**
     * **The choice, written out**: every edge leaving the tree and what it costs,
     * the one that wins, and the edges left off because they would make a loop.
     * Null when there is nothing left to choose.
     */
    fun cheapestReadout(): CheapestReadout? {
        val best = cheapest ?: return null
        return CheapestReadout(
            rows = crossing.map { edge ->
                CheapestReadout.Row(
                    node = edge.to,
                    via = edge.from,
                    viaDistance = 0,
                    weight = edge.weight,
                    distance = edge.weight,
                )
            },
            chosen = best.to,
            chosenVia = best.from,
            mode = CheapestReadout.Mode.EDGES,
            skippedLoops = loops,
        )
    }
}

/**
 * Prim's algorithm — an Advanced lesson, and the fourth graph one.
 *
 * ```
 * put the start in the tree
 * repeat until every node is in:
 *     look at every edge from a tree node to a node outside the tree
 *     take the cheapest one — its outside node joins the tree
 * ```
 *
 * ### What the learner has to understand
 *
 * 1. **Look at the whole tree, not just the newest node.** The cheapest edge out
 *    can leave from a node that joined long ago. It is the mistake people make,
 *    and the teaching graph makes it once on purpose.
 * 2. **An edge inside the tree is never offered.** Both ends are already
 *    connected, so it could only make a loop — and a loop only adds cost.
 *
 * ### Where it sits
 *
 * Dijkstra compares whole routes from the start; Prim compares single edges.
 * They answer different questions — *the cheapest way to each node* against
 * *the cheapest way to connect them all* — and on many graphs build different
 * trees.
 *
 * **Time O(E log V)** with a binary heap; **space O(V)**.
 */
class PrimAlgorithm : Algorithm<PrimState, PrimAction> {

    override val id = AlgorithmId.PRIM

    override fun initial(dataset: Dataset): PrimState {
        val graph = dataset.graph ?: Graph(emptyList(), emptyMap())
        val start = dataset.startNode
            ?.takeIf { graph.node(it) != null }
            ?: graph.nodes.firstOrNull()?.id.orEmpty()
        return PrimState(
            graph = graph,
            start = start,
            inTree = if (graph.node(start) != null) listOf(start) else emptyList(),
            treeEdges = emptyList(),
        )
    }

    override fun probe(state: PrimState): Probe<PrimAction> = when {
        state.spanning -> Probe.Terminal(Outcome.Completed(true))
        // Nothing reaches the rest: the graph is not connected, so no tree spans it.
        state.finished -> Probe.Terminal(Outcome.NotFound)
        else -> Probe.Decide(addDecision(state))
    }

    private fun addDecision(state: PrimState): Decision<PrimAction> {
        val best = requireNotNull(state.cheapest)
        val order = state.graph.ids
        val newest = state.lastAdded

        return Decision(
            kind = DecisionKind.CELL,
            prompt = NarrationKey(NarrationId.PRIM_ASK_ADD),
            // Every node is tappable. Offering only the reachable ones would answer
            // half the question by outlining where to look.
            options = order.mapIndexed { slot, node ->
                ActionOption<PrimAction>(
                    action = PrimAction.Add(node),
                    label = NarrationKey(NarrationId.PRIM_OPTION_NODE, listOf(node)),
                    slot = slot,
                )
            },
            correct = PrimAction.Add(best.to),
            focus = listOf(state.graph.indexOf(best.to)),
            hint = NarrationKey(NarrationId.PRIM_HINT_ADD),
            guidance = listOf(
                NarrationKey(NarrationId.PRIM_RETRY_LOOK),
                NarrationKey(NarrationId.PRIM_RETRY_ASK),
                NarrationKey(
                    NarrationId.PRIM_RETRY_EXPLAIN,
                    listOf(best.label, best.weight, best.to),
                ),
            ),
            minimalFeedback = NarrationKey(NarrationId.PRIM_RETRY_LOOK),
            // Each wrong tap is the misconception it is.
            whyWrong = buildMap {
                for (node in order) {
                    if (node == best.to) continue
                    val theirs = state.bestEdgeTo(node)
                    val key = when {
                        node in state.inTree ->
                            NarrationKey(NarrationId.PRIM_WHY_IN_TREE, listOf(node))

                        theirs == null ->
                            NarrationKey(NarrationId.PRIM_WHY_UNREACHED, listOf(node))

                        // Only looked at the newest node's edges — the classic slip.
                        newest != null && theirs.from == newest && best.from != newest ->
                            NarrationKey(
                                NarrationId.PRIM_WHY_NEWEST_ONLY,
                                listOf(theirs.label, theirs.weight, best.label, best.weight, newest),
                            )

                        else -> NarrationKey(
                            NarrationId.PRIM_WHY_NOT_CHEAPEST,
                            listOf(node, theirs.label, theirs.weight, best.label, best.weight),
                        )
                    }
                    put(PrimAction.Add(node), key)
                }
            },
            correctFeedback = NarrationKey(
                NarrationId.PRIM_CORRECT_ADD,
                listOf(best.to, best.label, best.weight, state.total + best.weight),
            ),
            hintLadder = listOf(
                NarrationKey(NarrationId.PRIM_HINT_ADD),
                NarrationKey(NarrationId.PRIM_RETRY_ASK),
            ),
            cheapest = state.cheapestReadout(),
            // Which edge is cheapest is the rule the algorithm turns on. Never the app's.
            autoInTry = false,
        )
    }

    override fun apply(state: PrimState, action: PrimAction): Transition<PrimState> =
        when (action) {
            is PrimAction.Add -> add(state, action.node)
        }

    /**
     * Adding anything but the node the cheapest edge reaches is **refused**, not
     * applied. In Try it never gets here, because a `Retry` carries no action.
     */
    private fun add(state: PrimState, node: String): Transition<PrimState> {
        val best = state.cheapest
        if (best == null || best.to != node) {
            val slot = state.graph.indexOf(node)
            return Transition(
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

        val next = state.copy(
            inTree = state.inTree + node,
            treeEdges = state.treeEdges + best,
        )
        val slot = state.graph.indexOf(node)
        return Transition(
            next = next,
            events = buildList {
                add(VizEvent.Examine(listOf(slot), ExamineRole.COMPARING))
                add(VizEvent.Finalize(slot..slot))
                add(VizEvent.Meter(MeterId.RUNNING_SUM, next.total.toLong()))
                if (next.finished) {
                    add(
                        VizEvent.Terminal(
                            if (next.spanning) Outcome.Completed(true) else Outcome.NotFound,
                        ),
                    )
                }
            },
            narration = NarrationKey(
                NarrationId.PRIM_ADDED,
                listOf(node, best.label, best.weight, next.total),
            ),
            correct = true,
        )
    }
}
