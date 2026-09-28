package com.algorithms.algoking.engine.algorithms.bellmanford

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
import com.algorithms.algoking.engine.event.MeterId
import com.algorithms.algoking.engine.event.Outcome
import com.algorithms.algoking.engine.event.Relation
import com.algorithms.algoking.engine.event.VizEvent
import com.algorithms.algoking.engine.narration.NarrationId
import com.algorithms.algoking.engine.narration.NarrationKey

/**
 * What the learner does in Bellman–Ford.
 *
 * **One question, asked of every road whose start has a distance: what should the
 * end's distance be now?** Three numbers, like Dijkstra — the two wrong ones are
 * keeping the old distance when the road is shorter (or taking the road when it is
 * not), and reading the road's length on its own.
 *
 * Everything else is the app's: starting and ending passes, passing over a road
 * that starts at ∞, a road that gives exactly the distance already known, and the
 * whole final check pass, which is bookkeeping once the idea is learned.
 */
sealed interface BfAction : Action {
    data object BeginPass : BfAction
    data class SetDistance(val value: Int?) : BfAction

    /** The road starts at a node still at ∞ — it cannot help yet. Mechanical. */
    data object Unreached : BfAction

    /** The road gives exactly the distance already known. Mechanical. */
    data object Same : BfAction

    /** The check pass: does this road still lower anything? Mechanical. */
    data object Check : BfAction
    data object EndPass : BfAction
}

/** What happened to a road this pass, for the list. */
sealed interface RoadResult {
    data class Improved(val value: Int) : RoadResult
    data object NoChange : RoadResult
    data object FromInfinity : RoadResult
    data object StillDrops : RoadResult
}

/** `∞` or the number. */
fun bfLabel(d: Int?): String = d?.toString() ?: "∞"

/** A road's length as the learner reads it: a real minus sign for a negative road. */
fun weightLabel(w: Int): String = if (w < 0) "−${-w}" else "$w"

/**
 * Immutable state — the single source of truth. [dist] is the whole model: a node
 * absent from it is at ∞.
 */
data class BfState(
    val graph: Graph,
    val start: String,
    /** The roads, in the order every pass visits them. */
    val edges: List<DirectedEdge>,
    val dist: Map<String, Int>,
    val pred: Map<String, String>,
    /** 1-based. The check pass is pass `n`. */
    val pass: Int,
    val passStarted: Boolean,
    val cursor: Int,
    val changedThisPass: Boolean,
    /** True during the extra pass after `n − 1`, which only looks for a negative cycle. */
    val checking: Boolean,
    val done: Boolean,
    val negativeCycle: Boolean,
    /** What happened to each road so far this pass, index-aligned with [edges]. */
    val results: List<RoadResult?>,
) {
    val n: Int get() = graph.nodes.size

    /** At most `n − 1` passes: a shortest route uses at most that many roads. */
    val maxPasses: Int get() = (n - 1).coerceAtLeast(1)

    val current: DirectedEdge? get() = if (done || !passStarted) null else edges.getOrNull(cursor)

    fun d(node: String): Int? = dist[node]

    /** `dist(from) + weight`, or null when the road starts at ∞. */
    fun candidate(edge: DirectedEdge): Int? = d(edge.from)?.let { it + edge.weight }

    fun improves(edge: DirectedEdge): Boolean {
        val c = candidate(edge) ?: return false
        val now = d(edge.to) ?: return true
        return c < now
    }

    fun relaxed(edge: DirectedEdge): Int? = if (improves(edge)) candidate(edge) else d(edge.to)

    /** The three numbers: what is known, the road’s route, and the road on its own. */
    fun options(edge: DirectedEdge): List<Int?> =
        listOf(d(edge.to), candidate(edge), edge.weight)
            .distinct()
            .sortedWith(compareBy(nullsLast()) { it })

    /** The best known route to [node], start first. Reconstructed, never stored. */
    fun pathTo(node: String): List<String> {
        if (node !in dist) return emptyList()
        val route = ArrayDeque<String>()
        var step: String? = node
        var guard = 0
        while (step != null && guard++ <= n) {
            route.addFirst(step)
            step = pred[step]
        }
        return route.toList()
    }

    val distancesLine: String
        get() = "Distances: " + graph.ids.joinToString("  ·  ") { "$it ${bfLabel(d(it))}" }

    /** The road list with this pass's results. [showNext] marks the road about to be looked at. */
    fun readout(showNext: Boolean = true): EdgeListReadout {
        val next = if (showNext) current else null
        return EdgeListReadout(
            rows = edges.mapIndexed { i, edge ->
                val result = results.getOrNull(i)
                EdgeListReadout.Row(
                    label = edge.label,
                    weight = edge.weight,
                    status = when {
                        result is RoadResult.Improved -> EdgeListReadout.Status.TAKEN
                        result == RoadResult.NoChange -> EdgeListReadout.Status.CHECKED
                        result == RoadResult.FromInfinity -> EdgeListReadout.Status.SKIPPED
                        result == RoadResult.StillDrops -> EdgeListReadout.Status.SKIPPED
                        edge == next && i == cursor -> EdgeListReadout.Status.NEXT
                        else -> EdgeListReadout.Status.WAITING
                    },
                    note = when (result) {
                        is RoadResult.Improved -> "now ${result.value}"
                        RoadResult.FromInfinity -> "starts at ∞"
                        RoadResult.StillDrops -> "still drops!"
                        else -> null
                    },
                )
            },
            groups = emptyList(),
            title = if (checking) {
                "Check pass — every road once more"
            } else {
                "Pass $pass of $maxPasses — every road, in order"
            },
            footer = distancesLine,
        )
    }
}

/**
 * Bellman–Ford — an Advanced lesson: shortest routes from one start, even when a
 * road has a negative length.
 *
 * ```
 * dist(start) = 0, everything else ∞
 * repeat up to n − 1 times:                     ← one pass
 *     for each road u → v, in a fixed order:
 *         if dist(u) + w < dist(v):  dist(v) = dist(u) + w
 *     if the pass changed nothing: stop — every distance is final
 * one more pass: if anything still drops, there is a negative cycle
 * ```
 *
 * ### What the learner has to understand
 *
 * 1. **Repeat the whole list.** A distance found late in one pass only helps the
 *    roads after it; the next pass carries it further. The WATCH order is chosen
 *    badly on purpose, so the answer takes all three passes to arrive.
 * 2. **A negative road is fine.** Nothing is ever settled, so a road that makes a
 *    route cheaper — even one into a node that already has a distance — is simply
 *    one more relaxation. That is exactly what Dijkstra cannot do.
 * 3. **A pass with no change means done**, and after `n − 1` passes, a pass that
 *    still lowers something means a negative cycle — a loop that gets cheaper every
 *    time round, so no shortest route exists.
 *
 * **Time O(V · E)**; **space O(V)**.
 */
class BellmanFordAlgorithm : Algorithm<BfState, BfAction> {

    override val id = AlgorithmId.BELLMAN_FORD

    override fun initial(dataset: Dataset): BfState {
        val graph = dataset.graph ?: Graph(emptyList(), emptyMap())
        val start = dataset.startNode?.takeIf { graph.node(it) != null }
            ?: graph.nodes.firstOrNull()?.id.orEmpty()
        val edges = dataset.directedEdges
        return BfState(
            graph = graph,
            start = start,
            edges = edges,
            dist = if (graph.node(start) != null) mapOf(start to 0) else emptyMap(),
            pred = emptyMap(),
            pass = 1,
            passStarted = false,
            cursor = 0,
            changedThisPass = false,
            checking = false,
            done = edges.isEmpty(),
            negativeCycle = false,
            results = List(edges.size) { null },
        )
    }

    override fun probe(state: BfState): Probe<BfAction> {
        if (state.done) {
            return Probe.Terminal(if (state.negativeCycle) Outcome.NotFound else Outcome.Completed(true))
        }
        if (!state.passStarted) return Probe.Mechanical(BfAction.BeginPass)
        val edge = state.current ?: return Probe.Mechanical(BfAction.EndPass)
        if (state.checking) return Probe.Mechanical(BfAction.Check)
        val candidate = state.candidate(edge) ?: return Probe.Mechanical(BfAction.Unreached)
        if (candidate == state.d(edge.to)) return Probe.Mechanical(BfAction.Same)
        return Probe.Decide(decision(state, edge))
    }

    private fun decision(state: BfState, edge: DirectedEdge): Decision<BfAction> {
        val now = state.d(edge.to)
        val fromDist = requireNotNull(state.d(edge.from))
        val candidate = fromDist + edge.weight
        val better = state.improves(edge)
        val correct = state.relaxed(edge)
        val options = state.options(edge)
        // Shared by every line about this road: from, to, dist(from), weight, candidate, now.
        val args = listOf(edge.from, edge.to, fromDist, weightLabel(edge.weight), candidate, bfLabel(now))

        return Decision(
            kind = DecisionKind.OPTIONS,
            prompt = NarrationKey(NarrationId.BF_ASK, args),
            options = options.map { value ->
                ActionOption<BfAction>(
                    action = BfAction.SetDistance(value),
                    label = NarrationKey(NarrationId.BF_OPTION_VALUE, listOf(bfLabel(value))),
                )
            },
            correct = BfAction.SetDistance(correct),
            focus = listOf(state.graph.indexOf(edge.from), state.graph.indexOf(edge.to)),
            hint = NarrationKey(NarrationId.BF_HINT, args),
            guidance = listOf(
                NarrationKey(NarrationId.BF_RETRY_LOOK, args),
                NarrationKey(if (better) NarrationId.BF_RETRY_ASK_BETTER else NarrationId.BF_RETRY_ASK_WORSE, args),
                NarrationKey(
                    if (better) NarrationId.BF_RETRY_EXPLAIN_UPDATE else NarrationId.BF_RETRY_EXPLAIN_KEEP,
                    args,
                ),
            ),
            minimalFeedback = NarrationKey(NarrationId.BF_RETRY_LOOK, args),
            whyWrong = buildMap {
                for (value in options) {
                    if (value == correct) continue
                    val id = when {
                        value == edge.weight && value != now && value != candidate -> NarrationId.BF_WHY_WEIGHT_ONLY
                        better -> NarrationId.BF_WHY_MISSED
                        else -> NarrationId.BF_WHY_WORSE
                    }
                    put(BfAction.SetDistance(value), NarrationKey(id, args))
                }
            },
            correctFeedback = NarrationKey(
                if (better) NarrationId.BF_CORRECT_UPDATE else NarrationId.BF_CORRECT_KEEP,
                args,
            ),
            hintLadder = listOf(NarrationKey(NarrationId.BF_HINT, args)),
            edgeList = state.readout(),
            autoInTry = false,
        )
    }

    override fun apply(state: BfState, action: BfAction): Transition<BfState> = when (action) {
        BfAction.BeginPass -> Transition(
            next = state.copy(
                passStarted = true,
                cursor = 0,
                changedThisPass = false,
                results = List(state.edges.size) { null },
            ),
            events = emptyList(),
            narration = NarrationKey(
                if (state.checking) NarrationId.BF_BEGIN_CHECK else NarrationId.BF_BEGIN_PASS,
                listOf(state.pass, state.maxPasses),
            ),
            correct = true,
        )

        BfAction.Unreached -> advance(state, RoadResult.FromInfinity)
        BfAction.Same -> advance(state, RoadResult.NoChange)

        BfAction.Check -> {
            val edge = requireNotNull(state.current)
            if (state.improves(edge)) {
                advance(state, RoadResult.StillDrops).let { t ->
                    t.copy(next = t.next.copy(negativeCycle = true))
                }
            } else {
                advance(state, if (state.candidate(edge) == null) RoadResult.FromInfinity else RoadResult.NoChange)
            }
        }

        BfAction.EndPass -> endPass(state)
        is BfAction.SetDistance -> setDistance(state, action.value)
    }

    private fun advance(state: BfState, result: RoadResult): Transition<BfState> = Transition(
        next = state.copy(
            cursor = state.cursor + 1,
            results = state.results.toMutableList().also { it[state.cursor] = result },
        ),
        events = emptyList(),
        narration = null,
        correct = true,
    )

    private fun endPass(state: BfState): Transition<BfState> {
        val next = when {
            // The check pass is over: whatever it found is the answer.
            state.checking -> state.copy(done = true, passStarted = false)
            // Nothing changed: every distance is final, and no cycle can exist.
            !state.changedThisPass -> state.copy(done = true, passStarted = false)
            // n − 1 passes done and still changing: one more, only to check.
            state.pass >= state.maxPasses -> state.copy(pass = state.pass + 1, passStarted = false, checking = true)
            else -> state.copy(pass = state.pass + 1, passStarted = false)
        }
        return Transition(
            next = next,
            events = if (next.done) {
                listOf(VizEvent.Terminal(if (next.negativeCycle) Outcome.NotFound else Outcome.Completed(true)))
            } else {
                emptyList()
            },
            narration = null,
            correct = true,
        )
    }

    private fun setDistance(state: BfState, value: Int?): Transition<BfState> {
        val edge = state.current ?: return Transition(state, emptyList(), null, correct = false)
        val to = state.graph.indexOf(edge.to)
        val from = state.graph.indexOf(edge.from)
        // A wrong number is refused, not applied.
        if (value != state.relaxed(edge)) {
            return Transition(
                next = state,
                events = listOf(VizEvent.Examine(listOf(to), ExamineRole.INSPECTING)),
                narration = null,
                correct = false,
            )
        }
        val better = state.improves(edge)
        val candidate = requireNotNull(state.candidate(edge))
        val next = if (better) {
            state.copy(
                dist = state.dist + (edge.to to candidate),
                pred = state.pred + (edge.to to edge.from),
                changedThisPass = true,
                cursor = state.cursor + 1,
                results = state.results.toMutableList().also { it[state.cursor] = RoadResult.Improved(candidate) },
            )
        } else {
            state.copy(
                cursor = state.cursor + 1,
                results = state.results.toMutableList().also { it[state.cursor] = RoadResult.NoChange },
            )
        }
        return Transition(
            next = next,
            events = buildList {
                add(VizEvent.Compare(from, to, if (better) Relation.LESS else Relation.GREATER))
                if (better) {
                    add(VizEvent.Examine(listOf(to), ExamineRole.CANDIDATE))
                    add(VizEvent.Meter(MeterId.BEST, candidate.toLong()))
                } else {
                    add(VizEvent.Hold(listOf(to)))
                }
            },
            narration = NarrationKey(
                if (better) NarrationId.BF_UPDATED else NarrationId.BF_KEPT,
                listOf(edge.to, bfLabel(value)),
            ),
            correct = true,
        )
    }
}
