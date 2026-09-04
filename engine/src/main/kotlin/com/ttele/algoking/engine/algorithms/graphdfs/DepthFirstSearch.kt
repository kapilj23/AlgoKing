package com.ttele.algoking.engine.algorithms.graphdfs

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
import com.ttele.algoking.engine.event.ExamineRole
import com.ttele.algoking.engine.event.MeterId
import com.ttele.algoking.engine.event.Outcome
import com.ttele.algoking.engine.event.VizEvent
import com.ttele.algoking.engine.narration.NarrationId
import com.ttele.algoking.engine.narration.NarrationKey

/**
 * What the learner does in DFS.
 *
 * One gesture, all the way through: **tap the node DFS moves to next.** That is
 * either an unvisited neighbour (go deeper) or the node you came from (backtrack),
 * and choosing between those two *is* depth-first search.
 *
 * Modelling backtracking as its own button would have made it a mode the learner
 * toggles rather than a move the graph makes; here it is the same tap on a
 * different node, which is what it actually is.
 */
sealed interface DfsAction : Action {

    /** Move to [node] — deeper if it is unvisited, back if it is the parent. */
    data class Go(val node: String) : DfsAction
}

/**
 * Immutable state.
 *
 * [stack] is the DFS path, deepest last — the call stack a recursive
 * implementation would build, made into data. `[A, B, D]` means DFS reached D
 * through B through A, and it is exactly what a backtrack unwinds.
 *
 * [visited] is kept as an ordered list because it *is* the traversal order; a
 * `Set` would have thrown away the one output the lesson exists to produce.
 */
data class DfsState(
    val graph: Graph,
    val start: String,
    /** Visit order. Also the traversal the lesson is teaching. */
    val visited: List<String>,
    val stack: List<String>,
    /** True on the frame a backtrack happened, so the picture can say so. */
    val backtracked: Boolean,
) {
    val current: String? get() = stack.lastOrNull()

    /** Where a backtrack would go. Null at the root. */
    val parent: String? get() = stack.getOrNull(stack.lastIndex - 1)

    /** Everything DFS can get to from [start]. A disconnected part is not it. */
    val reachable: Set<String> get() = graph.reachableFrom(start)

    fun isVisited(id: String): Boolean = id in visited

    /** Neighbours of [id] that DFS has not been to, in authored order. */
    fun unvisitedNeighbours(id: String): List<String> =
        graph.neighbours(id).filter { it !in visited }

    /** The neighbour DFS explores next: the *first* unvisited one. */
    val nextDeeper: String? get() = current?.let { unvisitedNeighbours(it).firstOrNull() }

    /** A dead end: nowhere deeper to go from here. */
    val atDeadEnd: Boolean get() = current != null && nextDeeper == null

    /**
     * Finished when every reachable node has been visited.
     *
     * DFS would then unwind the rest of the stack, but there is nothing left to
     * decide or to learn from it — the lesson ends on the last visit, which is
     * also where the traversal it teaches ends.
     */
    val finished: Boolean
        get() = graph.nodes.isEmpty() ||
            graph.node(start) == null ||
            visited.size >= reachable.size
}

/**
 * Depth-First Search on an undirected graph — an Advanced lesson.
 *
 * ```
 * DFS(node):
 *     mark node visited
 *     for each neighbour of node:
 *         if neighbour is not visited:
 *             DFS(neighbour)
 * ```
 *
 * ### What the learner has to understand
 *
 * Not the output `A → B → D → E → C`, which is memorisable and worth nothing.
 * The three judgements behind it:
 *
 * 1. **which neighbour** — the first unvisited one, in the graph's own order;
 * 2. **when to backtrack** — only at a dead end, never before;
 * 3. **why some neighbours are skipped** — they are already visited.
 *
 * All three are the same tap, which is why there is one action.
 *
 * The traversal is **generated**, never authored: `visited` and `stack` are real
 * state, and the order falls out of them.
 */
class DepthFirstSearchAlgorithm : Algorithm<DfsState, DfsAction> {

    override val id = AlgorithmId.GRAPH_DFS

    override fun initial(dataset: Dataset): DfsState {
        val graph = dataset.graph ?: Graph(emptyList(), emptyMap())
        // A missing or unknown start is bad data, not a crash: the run simply has
        // nothing to visit and terminates.
        val start = dataset.startNode
            ?.takeIf { graph.node(it) != null }
            ?: graph.nodes.firstOrNull()?.id.orEmpty()
        return DfsState(
            graph = graph,
            start = start,
            visited = emptyList(),
            stack = emptyList(),
            backtracked = false,
        )
    }

    override fun probe(state: DfsState): Probe<DfsAction> {
        if (state.graph.nodes.isEmpty() || state.graph.node(state.start) == null) {
            return Probe.Terminal(Outcome.Completed(true))
        }
        if (state.finished) return Probe.Terminal(Outcome.Completed(true))

        // The first visit is the app's: where DFS starts is given, not chosen, and
        // asking the learner to tap the only legal node teaches a gesture
        // (PRODUCT_SPEC.md §3).
        if (state.visited.isEmpty()) return Probe.Mechanical(DfsAction.Go(state.start))

        return Probe.Decide(moveDecision(state))
    }

    // -- The decision ---------------------------------------------------------

    private fun moveDecision(state: DfsState): Decision<DfsAction> {
        val current = requireNotNull(state.current)
        val neighbours = state.graph.neighbours(current)
        val deeper = state.nextDeeper
        val parent = state.parent
        val correctNode = deeper ?: parent ?: current
        val backtracking = deeper == null

        // Every neighbour is tappable, and so is the node DFS came from. That is
        // what makes all three judgements askable with one gesture: a visited
        // neighbour is the "skip it" case, a later unvisited one is the "first
        // one first" case, and the parent is the backtrack.
        val targets = LinkedHashSet<String>()
        targets += neighbours
        parent?.let { targets += it }

        val options = targets.map { node ->
            ActionOption<DfsAction>(
                action = DfsAction.Go(node),
                label = NarrationKey(NarrationId.DFS_OPTION_NODE, listOf(label(state, node))),
                slot = state.graph.indexOf(node),
            )
        }

        return Decision(
            kind = DecisionKind.CELL,
            prompt = NarrationKey(
                if (backtracking) NarrationId.DFS_ASK_DEAD_END else NarrationId.DFS_ASK_NEXT,
                listOf(label(state, current)),
            ),
            options = options,
            correct = DfsAction.Go(correctNode),
            focus = listOfNotNull(state.graph.indexOf(current).takeIf { it >= 0 }),
            hint = NarrationKey(
                if (backtracking) NarrationId.DFS_HINT_BACKTRACK else NarrationId.DFS_HINT_DEEPER,
                listOf(label(state, current)),
            ),
            guidance = listOf(
                NarrationKey(
                    if (backtracking) {
                        NarrationId.DFS_RETRY_DEAD_END_LOOK
                    } else {
                        NarrationId.DFS_RETRY_LOOK
                    },
                    listOf(label(state, current)),
                ),
                NarrationKey(
                    if (backtracking) {
                        NarrationId.DFS_RETRY_DEAD_END_ASK
                    } else {
                        NarrationId.DFS_RETRY_ASK
                    },
                    listOf(label(state, current)),
                ),
                NarrationKey(
                    if (backtracking) {
                        NarrationId.DFS_RETRY_DEAD_END_EXPLAIN
                    } else {
                        NarrationId.DFS_RETRY_EXPLAIN
                    },
                    listOf(label(state, current), label(state, correctNode)),
                ),
            ),
            minimalFeedback = NarrationKey(
                if (backtracking) {
                    NarrationId.DFS_RETRY_DEAD_END_LOOK
                } else {
                    NarrationId.DFS_RETRY_LOOK
                },
                listOf(label(state, current)),
            ),
            // Each wrong tap is named for the misconception it is: revisiting,
            // taking a branch out of order, or backtracking too early.
            whyWrong = buildMap {
                for (node in targets) {
                    if (node == correctNode) continue
                    val id = when {
                        node == parent -> NarrationId.DFS_WHY_TOO_EARLY
                        state.isVisited(node) -> NarrationId.DFS_WHY_ALREADY_VISITED
                        else -> NarrationId.DFS_WHY_WRONG_BRANCH
                    }
                    put(
                        DfsAction.Go(node),
                        NarrationKey(
                            id,
                            listOf(
                                label(state, node),
                                label(state, correctNode),
                                label(state, current),
                            ),
                        ),
                    )
                }
            },
            correctFeedback = NarrationKey(
                if (backtracking) {
                    NarrationId.DFS_CORRECT_BACKTRACK
                } else {
                    NarrationId.DFS_CORRECT_DEEPER
                },
                listOf(label(state, current), label(state, correctNode)),
            ),
            hintLadder = listOf(
                NarrationKey(
                    if (backtracking) {
                        NarrationId.DFS_HINT_BACKTRACK
                    } else {
                        NarrationId.DFS_HINT_DEEPER
                    },
                    listOf(label(state, current)),
                ),
                NarrationKey(
                    if (backtracking) {
                        NarrationId.DFS_RETRY_DEAD_END_ASK
                    } else {
                        NarrationId.DFS_RETRY_ASK
                    },
                    listOf(label(state, current)),
                ),
                NarrationKey(
                    if (backtracking) {
                        NarrationId.DFS_RETRY_DEAD_END_EXPLAIN
                    } else {
                        NarrationId.DFS_RETRY_EXPLAIN
                    },
                    listOf(label(state, current), label(state, correctNode)),
                ),
            ),
        )
    }

    // -- Transitions ----------------------------------------------------------

    override fun apply(state: DfsState, action: DfsAction): Transition<DfsState> {
        val node = when (action) {
            is DfsAction.Go -> action.node
        }
        if (state.graph.node(node) == null) {
            return Transition(state, emptyList(), null, correct = false)
        }

        // Returning to the node we came from is a backtrack, not a revisit. That
        // distinction is the lesson, so the engine draws it rather than the UI.
        val isBacktrack = node == state.parent
        return if (isBacktrack) backtrack(state, node) else descend(state, node)
    }

    private fun descend(state: DfsState, node: String): Transition<DfsState> {
        // Visiting something already visited would put two of it on the stack and
        // corrupt the traversal. It is refused rather than applied — this is the
        // one place `apply` is not total, because there is no meaningful state on
        // the other side of it.
        if (state.isVisited(node)) return refuse(state, node)

        // The seed. With an empty stack there is no current node to have a
        // neighbour, so "the first unvisited neighbour" is not the rule yet —
        // the rule is "start where the dataset says".
        val seeding = state.stack.isEmpty()
        val correct = if (seeding) node == state.start else node == state.nextDeeper

        val next = state.copy(
            visited = state.visited + node,
            stack = state.stack + node,
            backtracked = false,
        )
        return Transition(
            next = next,
            events = buildList {
                add(VizEvent.Examine(listOf(state.graph.indexOf(node)), ExamineRole.COMPARING))
                add(VizEvent.Finalize(indexRange(state, node)))
                add(VizEvent.Meter(MeterId.REMAINING, (next.reachable.size - next.visited.size).toLong()))
                if (next.finished) add(VizEvent.Terminal(Outcome.Completed(true)))
            },
            narration = NarrationKey(
                NarrationId.DFS_VISITED,
                listOf(label(state, node), next.visited.size),
            ),
            correct = correct,
        )
    }

    private fun backtrack(state: DfsState, node: String): Transition<DfsState> {
        val correct = state.atDeadEnd
        val from = state.current
        val next = state.copy(stack = state.stack.dropLast(1), backtracked = true)
        return Transition(
            next = next,
            events = listOf(
                VizEvent.Examine(listOf(state.graph.indexOf(node)), ExamineRole.INSPECTING),
                VizEvent.Meter(MeterId.DISTANCE, next.stack.size.toLong()),
            ),
            narration = NarrationKey(
                NarrationId.DFS_BACKTRACKED,
                listOf(label(state, from.orEmpty()), label(state, node)),
            ),
            correct = correct,
        )
    }

    /** No state change, and it is not correct. The validator is what stops it. */
    private fun refuse(state: DfsState, node: String) = Transition(
        next = state,
        events = listOf(
            VizEvent.Examine(listOf(state.graph.indexOf(node)), ExamineRole.INSPECTING),
        ),
        narration = null,
        correct = false,
    )

    private fun indexRange(state: DfsState, node: String): IntRange {
        val index = state.graph.indexOf(node)
        return index..index
    }

    private fun label(state: DfsState, id: String): String =
        state.graph.node(id)?.label ?: id
}
