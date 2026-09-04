package com.ttele.algoking.engine.algorithms.graphbfs

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
 * What the learner does in BFS.
 *
 * The same single gesture DFS uses — **tap the node BFS touches next** — which
 * here means one of two things: add an unvisited neighbour to the queue, or take
 * the node at the front of the queue off it.
 *
 * Modelling those as one action rather than two buttons keeps the interaction
 * identical to DFS, which is what lets a learner who has done both feel the
 * difference in the *algorithm* rather than in the controls.
 */
sealed interface BfsAction : Action {

    /** Touch [node] — enqueue it if it is a new neighbour, dequeue it if it is next. */
    data class Go(val node: String) : BfsAction
}

/**
 * Immutable state.
 *
 * ### Visited when enqueued, not when dequeued
 *
 * A node is marked visited the moment it enters [queue]. That is the rule that
 * keeps BFS correct on a graph with cycles: if nodes were only marked on the way
 * out, the same node could be enqueued several times before its first turn came
 * up, and it would be processed more than once.
 *
 * So there are **two orders** here and they are not the same thing:
 *
 *  - [visited] — enqueue order. What BFS has *seen*.
 *  - [dequeued] — the traversal. What BFS has *processed*.
 */
data class BfsState(
    val graph: Graph,
    val start: String,
    /** Enqueue order. A node enters this the moment it joins the queue. */
    val visited: List<String>,
    /** Front first. The order is the whole idea. */
    val queue: List<String>,
    /** Dequeue order — **this is the traversal the lesson teaches**. */
    val dequeued: List<String>,
    /** The node whose neighbours are being examined right now. */
    val current: String?,
) {
    val reachable: Set<String> get() = graph.reachableFrom(start)

    fun isVisited(id: String): Boolean = id in visited

    /** Neighbours of [current] not yet seen, in authored order. */
    val pendingNeighbours: List<String>
        get() = current?.let { node -> graph.neighbours(node).filter { it !in visited } }
            .orEmpty()

    /** The neighbour BFS enqueues next: the first unseen one. */
    val nextToEnqueue: String? get() = pendingNeighbours.firstOrNull()

    /** The node BFS takes off the queue next. */
    val front: String? get() = queue.firstOrNull()

    /** True once the current node has nothing left to add. */
    val doneWithCurrent: Boolean get() = nextToEnqueue == null

    val finished: Boolean
        get() = graph.nodes.isEmpty() ||
            graph.node(start) == null ||
            (queue.isEmpty() && doneWithCurrent && visited.isNotEmpty())
}

/**
 * Breadth-First Search on an undirected graph — an Advanced lesson.
 *
 * ```
 * BFS(start):
 *     mark start visited
 *     enqueue(start)
 *     while queue is not empty:
 *         current = dequeue()
 *         for each neighbour of current:
 *             if neighbour is not visited:
 *                 mark neighbour visited
 *                 enqueue(neighbour)
 * ```
 *
 * ### What it teaches against DFS
 *
 * On the same graph, DFS gives `A → B → D → E → C` and BFS gives
 * `A → B → C → D → E`. The difference is not the graph and not the neighbour
 * order — it is the **queue**. First in, first out means everything one step from
 * A is processed before anything two steps away, so BFS finishes a level before
 * it goes deeper.
 *
 * A learner who takes away only the output has learned two strings. The judgement
 * worth having is *why the queue produces levels*, which is why the queue is on
 * screen and why dequeuing is a decision rather than an animation.
 */
class BreadthFirstSearchAlgorithm : Algorithm<BfsState, BfsAction> {

    override val id = AlgorithmId.GRAPH_BFS

    override fun initial(dataset: Dataset): BfsState {
        val graph = dataset.graph ?: Graph(emptyList(), emptyMap())
        val start = dataset.startNode
            ?.takeIf { graph.node(it) != null }
            ?: graph.nodes.firstOrNull()?.id.orEmpty()
        return BfsState(
            graph = graph,
            start = start,
            visited = emptyList(),
            queue = emptyList(),
            dequeued = emptyList(),
            current = null,
        )
    }

    override fun probe(state: BfsState): Probe<BfsAction> {
        if (state.graph.nodes.isEmpty() || state.graph.node(state.start) == null) {
            return Probe.Terminal(Outcome.Completed(true))
        }

        // The seed: mark the start visited and put it in the queue. Where BFS
        // begins is given, not chosen (PRODUCT_SPEC.md §3).
        if (state.visited.isEmpty()) return Probe.Mechanical(BfsAction.Go(state.start))

        if (state.finished) return Probe.Terminal(Outcome.Completed(true))

        return Probe.Decide(moveDecision(state))
    }

    // -- The decision ---------------------------------------------------------

    private fun moveDecision(state: BfsState): Decision<BfsAction> {
        val enqueueing = state.nextToEnqueue != null
        val correctNode = state.nextToEnqueue ?: state.front ?: state.start

        // Everything the learner might reasonably tap: the current node's
        // neighbours, and everything sitting in the queue. That is what makes the
        // five judgements askable from one gesture — including "C is not at the
        // front", which needs the rest of the queue to be tappable at all.
        val targets = LinkedHashSet<String>()
        targets += state.pendingNeighbours
        state.current?.let { targets += state.graph.neighbours(it) }
        targets += state.queue

        val options = targets.map { node ->
            ActionOption<BfsAction>(
                action = BfsAction.Go(node),
                label = NarrationKey(NarrationId.BFS_OPTION_NODE, listOf(label(state, node))),
                slot = state.graph.indexOf(node),
            )
        }

        val current = state.current?.let { label(state, it) }.orEmpty()
        val front = state.front?.let { label(state, it) }.orEmpty()

        return Decision(
            kind = DecisionKind.CELL,
            prompt = NarrationKey(
                if (enqueueing) NarrationId.BFS_ASK_ENQUEUE else NarrationId.BFS_ASK_DEQUEUE,
                listOf(current),
            ),
            options = options,
            correct = BfsAction.Go(correctNode),
            focus = listOfNotNull(state.current?.let { state.graph.indexOf(it) }),
            hint = NarrationKey(
                if (enqueueing) NarrationId.BFS_HINT_ENQUEUE else NarrationId.BFS_HINT_DEQUEUE,
                listOf(current, front),
            ),
            guidance = listOf(
                NarrationKey(
                    if (enqueueing) {
                        NarrationId.BFS_RETRY_ENQUEUE_LOOK
                    } else {
                        NarrationId.BFS_RETRY_DEQUEUE_LOOK
                    },
                    listOf(current),
                ),
                NarrationKey(
                    if (enqueueing) {
                        NarrationId.BFS_RETRY_ENQUEUE_ASK
                    } else {
                        NarrationId.BFS_RETRY_DEQUEUE_ASK
                    },
                    listOf(current, front),
                ),
                NarrationKey(
                    if (enqueueing) {
                        NarrationId.BFS_RETRY_ENQUEUE_EXPLAIN
                    } else {
                        NarrationId.BFS_RETRY_DEQUEUE_EXPLAIN
                    },
                    listOf(current, label(state, correctNode)),
                ),
            ),
            minimalFeedback = NarrationKey(
                if (enqueueing) {
                    NarrationId.BFS_RETRY_ENQUEUE_LOOK
                } else {
                    NarrationId.BFS_RETRY_DEQUEUE_LOOK
                },
                listOf(current),
            ),
            whyWrong = buildMap {
                for (node in targets) {
                    if (node == correctNode) continue
                    val id = when {
                        // Somewhere in the queue, but not its front.
                        node in state.queue && enqueueing ->
                            NarrationId.BFS_WHY_STILL_ADDING
                        node in state.queue -> NarrationId.BFS_WHY_NOT_THE_FRONT
                        state.isVisited(node) -> NarrationId.BFS_WHY_ALREADY_VISITED
                        else -> NarrationId.BFS_WHY_WRONG_ORDER
                    }
                    put(
                        BfsAction.Go(node),
                        NarrationKey(
                            id,
                            listOf(
                                label(state, node),
                                label(state, correctNode),
                                current,
                                front,
                            ),
                        ),
                    )
                }
            },
            correctFeedback = NarrationKey(
                if (enqueueing) {
                    NarrationId.BFS_CORRECT_ENQUEUE
                } else {
                    NarrationId.BFS_CORRECT_DEQUEUE
                },
                listOf(label(state, correctNode), current),
            ),
            hintLadder = listOf(
                NarrationKey(
                    if (enqueueing) {
                        NarrationId.BFS_HINT_ENQUEUE
                    } else {
                        NarrationId.BFS_HINT_DEQUEUE
                    },
                    listOf(current, front),
                ),
                NarrationKey(
                    if (enqueueing) {
                        NarrationId.BFS_RETRY_ENQUEUE_ASK
                    } else {
                        NarrationId.BFS_RETRY_DEQUEUE_ASK
                    },
                    listOf(current, front),
                ),
                NarrationKey(
                    if (enqueueing) {
                        NarrationId.BFS_RETRY_ENQUEUE_EXPLAIN
                    } else {
                        NarrationId.BFS_RETRY_DEQUEUE_EXPLAIN
                    },
                    listOf(current, label(state, correctNode)),
                ),
            ),
        )
    }

    // -- Transitions ----------------------------------------------------------

    override fun apply(state: BfsState, action: BfsAction): Transition<BfsState> {
        val node = when (action) {
            is BfsAction.Go -> action.node
        }
        if (state.graph.node(node) == null) return refuse(state, node)

        // The seed.
        if (state.visited.isEmpty()) return seed(state, node)

        // An unseen neighbour of the current node joins the queue.
        val isNeighbour = state.current?.let { node in state.graph.neighbours(it) } ?: false
        if (isNeighbour && !state.isVisited(node)) return enqueue(state, node)

        // The front of the queue comes off it.
        if (node == state.front) return dequeue(state, node)

        return refuse(state, node)
    }

    private fun seed(state: BfsState, node: String): Transition<BfsState> = Transition(
        next = state.copy(visited = listOf(node), queue = listOf(node)),
        events = listOf(
            VizEvent.Examine(listOf(state.graph.indexOf(node)), ExamineRole.CANDIDATE),
            VizEvent.Insert(0, 0),
        ),
        narration = NarrationKey(NarrationId.BFS_SEEDED, listOf(label(state, node))),
        correct = node == state.start,
    )

    /**
     * Marked visited **on the way in**, which is what stops a node being queued
     * twice in a graph with cycles.
     */
    private fun enqueue(state: BfsState, node: String): Transition<BfsState> {
        val correct = node == state.nextToEnqueue
        val next = state.copy(
            visited = state.visited + node,
            queue = state.queue + node,
        )
        return Transition(
            next = next,
            events = listOf(
                VizEvent.Examine(listOf(state.graph.indexOf(node)), ExamineRole.CANDIDATE),
                VizEvent.Insert(0, next.queue.lastIndex),
                VizEvent.Meter(MeterId.REMAINING, next.queue.size.toLong()),
            ),
            narration = NarrationKey(
                NarrationId.BFS_ENQUEUED,
                listOf(label(state, node), state.current?.let { label(state, it) }.orEmpty()),
            ),
            correct = correct,
        )
    }

    private fun dequeue(state: BfsState, node: String): Transition<BfsState> {
        val correct = state.doneWithCurrent && node == state.front
        val next = state.copy(
            queue = state.queue.drop(1),
            dequeued = state.dequeued + node,
            current = node,
        )
        return Transition(
            next = next,
            events = buildList {
                add(VizEvent.Examine(listOf(state.graph.indexOf(node)), ExamineRole.COMPARING))
                add(VizEvent.Finalize(indexRange(state, node)))
                add(VizEvent.Remove(0))
                add(VizEvent.Meter(MeterId.REMAINING, next.queue.size.toLong()))
                if (next.finished) add(VizEvent.Terminal(Outcome.Completed(true)))
            },
            narration = NarrationKey(NarrationId.BFS_DEQUEUED, listOf(label(state, node))),
            correct = correct,
        )
    }

    /** No state change. The validator is what stops this ever being reached in Try. */
    private fun refuse(state: BfsState, node: String) = Transition(
        next = state,
        events = listOf(
            VizEvent.Examine(listOf(state.graph.indexOf(node)), ExamineRole.INSPECTING),
        ),
        narration = null,
        correct = false,
    )

    private fun indexRange(state: BfsState, node: String): IntRange {
        val index = state.graph.indexOf(node)
        return index..index
    }

    private fun label(state: BfsState, id: String): String =
        state.graph.node(id)?.label ?: id
}
