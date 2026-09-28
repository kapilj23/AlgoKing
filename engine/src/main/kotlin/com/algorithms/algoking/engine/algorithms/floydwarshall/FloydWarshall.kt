package com.algorithms.algoking.engine.algorithms.floydwarshall

import com.algorithms.algoking.engine.core.Algorithm
import com.algorithms.algoking.engine.core.AlgorithmId
import com.algorithms.algoking.engine.core.Dataset
import com.algorithms.algoking.engine.core.Graph
import com.algorithms.algoking.engine.core.Probe
import com.algorithms.algoking.engine.core.Transition
import com.algorithms.algoking.engine.decision.Action
import com.algorithms.algoking.engine.decision.ActionOption
import com.algorithms.algoking.engine.decision.Decision
import com.algorithms.algoking.engine.decision.DecisionKind
import com.algorithms.algoking.engine.event.ExamineRole
import com.algorithms.algoking.engine.event.Outcome
import com.algorithms.algoking.engine.event.Relation
import com.algorithms.algoking.engine.event.VizEvent
import com.algorithms.algoking.engine.narration.NarrationId
import com.algorithms.algoking.engine.narration.NarrationKey

/**
 * What the learner does in Floyd–Warshall.
 *
 * **One question, asked once per pair per round: what should this distance be
 * now?** Three numbers, and the two wrong ones are the two mistakes people make —
 * keeping the old distance when the detour is shorter (or taking the detour when
 * it is not), and adding only one leg of the detour.
 *
 * Everything else is the app's: starting each round, stepping to the next pair,
 * and passing over a pair whose detour has an ∞ leg, because ∞ + anything is ∞.
 */
sealed interface FwAction : Action {

    /** Start the round that tries every route through the next node. Mechanical. */
    data object BeginVia : FwAction

    /** Set the pair's distance to [value]; null is ∞. Correct only for the smaller. */
    data class SetDistance(val value: Int?) : FwAction

    /** Pass a pair whose detour has an ∞ leg — nothing can improve. Mechanical. */
    data object NoRoute : FwAction

    /** Finish the round. Mechanical. */
    data object EndVia : FwAction
}

/** `∞` or the number — how a distance is written everywhere the learner reads one. */
fun distanceLabel(d: Int?): String = d?.toString() ?: "∞"

/**
 * Immutable state — the single source of truth.
 *
 * [dist] is the whole model: `dist[i][j]` is the shortest distance known so far
 * from node i to node j, null for ∞. The graph is undirected, so the table is kept
 * symmetric, and each round visits only the pairs above the diagonal.
 */
data class FwState(
    val graph: Graph,
    val dist: List<List<Int?>>,
    /** Index of the node the current round routes through; `n` once every round is done. */
    val via: Int,
    val viaStarted: Boolean,
    /** How far through [pairs] this round has got. */
    val cursor: Int,
    /** The pair improved by the most recent decision, for the picture. */
    val lastImproved: Pair<Int, Int>? = null,
) {
    val n: Int get() = graph.nodes.size
    val ids: List<String> get() = graph.ids

    val finished: Boolean get() = via >= n

    /** The node this round routes through, or null once every round is done. */
    val viaNode: String? get() = ids.getOrNull(via)

    /** Every pair `i < j` that does not involve the via node, in table order. */
    val pairs: List<Pair<Int, Int>>
        get() = (0 until n).flatMap { i ->
            (i + 1 until n).map { j -> i to j }
        }.filter { (i, j) -> i != via && j != via }

    /** The pair being looked at, or null between rounds. */
    val current: Pair<Int, Int>? get() = if (finished || !viaStarted) null else pairs.getOrNull(cursor)

    fun d(i: Int, j: Int): Int? = dist[i][j]

    /** `dist[i][via] + dist[via][j]`, or null when either leg is ∞. */
    fun detour(i: Int, j: Int): Int? {
        val first = d(i, via) ?: return null
        val second = d(via, j) ?: return null
        return first + second
    }

    /** What the pair's distance should become: the smaller of what is known and the detour. */
    fun relaxed(i: Int, j: Int): Int? {
        val now = d(i, j)
        val through = detour(i, j) ?: return now
        return if (now == null || through < now) through else now
    }

    fun improves(i: Int, j: Int): Boolean {
        val through = detour(i, j) ?: return false
        val now = d(i, j) ?: return true
        return through < now
    }

    /**
     * The three numbers a decision offers: what is known, the detour, and the first
     * leg on its own — the slip of adding only half the route. Distinct, ∞ last.
     */
    fun options(i: Int, j: Int): List<Int?> =
        listOf(d(i, j), detour(i, j), d(i, via))
            .distinct()
            .sortedWith(compareBy(nullsLast()) { it })

    fun name(i: Int): String = ids[i]
}

/**
 * Floyd–Warshall — an Advanced lesson, and the all-pairs shortest-path one.
 *
 * ```
 * dist = the direct roads: 0 on the diagonal, the weight of each edge, ∞ elsewhere
 * for each node k:                                  ← one round per node
 *     for each pair i, j:
 *         if dist[i][k] + dist[k][j] < dist[i][j]:  ← is going through k shorter?
 *             dist[i][j] = dist[i][k] + dist[k][j]
 * ```
 *
 * ### What the learner has to understand
 *
 * 1. **One question, over and over: is going through k shorter?** Every cell of
 *    the table is only ever compared with one detour.
 * 2. **Rounds build on each other.** After the round through B, A → C is 5; the
 *    round through C then uses that 5 to find A → D = 6, which is really
 *    A → B → C → D. Three roads, found without ever looking for a three-road route.
 *
 * Dijkstra finds the distances from one start; Floyd–Warshall finds them between
 * every pair at once, with three loops and nothing else.
 *
 * **Time O(V³)**; **space O(V²)**, for the table.
 */
class FloydWarshallAlgorithm : Algorithm<FwState, FwAction> {

    override val id = AlgorithmId.FLOYD_WARSHALL

    override fun initial(dataset: Dataset): FwState {
        val graph = dataset.graph ?: Graph(emptyList(), emptyMap())
        val ids = graph.ids
        val dist = ids.map { a ->
            ids.map { b -> if (a == b) 0 else graph.weightOf(a, b) }
        }
        return FwState(graph, dist, via = 0, viaStarted = false, cursor = 0)
    }

    override fun probe(state: FwState): Probe<FwAction> {
        if (state.finished) return Probe.Terminal(Outcome.Completed(true))
        if (!state.viaStarted) return Probe.Mechanical(FwAction.BeginVia)
        val (i, j) = state.current ?: return Probe.Mechanical(FwAction.EndVia)
        if (state.detour(i, j) == null) return Probe.Mechanical(FwAction.NoRoute)
        return Probe.Decide(decision(state, i, j))
    }

    private fun decision(state: FwState, i: Int, j: Int): Decision<FwAction> {
        val a = state.name(i)
        val b = state.name(j)
        val k = requireNotNull(state.viaNode)
        val now = state.d(i, j)
        val legA = requireNotNull(state.d(i, state.via))
        val legB = requireNotNull(state.d(state.via, j))
        val through = legA + legB
        val better = state.improves(i, j)
        val correct = state.relaxed(i, j)
        val options = state.options(i, j)
        val nowLabel = distanceLabel(now)

        // Shared by every line about this pair: A, B, k, now, first leg, second leg, detour.
        val args = listOf(a, b, k, nowLabel, legA, legB, through)

        return Decision(
            kind = DecisionKind.OPTIONS,
            prompt = NarrationKey(NarrationId.FW_ASK, args),
            options = options.map { value ->
                ActionOption<FwAction>(
                    action = FwAction.SetDistance(value),
                    label = NarrationKey(NarrationId.FW_OPTION_VALUE, listOf(distanceLabel(value))),
                )
            },
            correct = FwAction.SetDistance(correct),
            focus = listOf(state.slotOf(i, j)),
            hint = NarrationKey(NarrationId.FW_HINT, args),
            guidance = listOf(
                NarrationKey(NarrationId.FW_RETRY_LOOK, args),
                NarrationKey(
                    if (better) NarrationId.FW_RETRY_ASK_BETTER else NarrationId.FW_RETRY_ASK_WORSE,
                    args,
                ),
                NarrationKey(
                    if (better) NarrationId.FW_RETRY_EXPLAIN_UPDATE else NarrationId.FW_RETRY_EXPLAIN_KEEP,
                    args,
                ),
            ),
            minimalFeedback = NarrationKey(NarrationId.FW_RETRY_LOOK, args),
            // Each wrong number is the mistake it encodes.
            whyWrong = buildMap {
                for (value in options) {
                    if (value == correct) continue
                    val id = when {
                        value == legA && value != now -> NarrationId.FW_WHY_ONE_LEG
                        better -> NarrationId.FW_WHY_MISSED
                        else -> NarrationId.FW_WHY_WORSE
                    }
                    put(FwAction.SetDistance(value), NarrationKey(id, args))
                }
            },
            correctFeedback = NarrationKey(
                if (better) NarrationId.FW_CORRECT_UPDATE else NarrationId.FW_CORRECT_KEEP,
                args,
            ),
            hintLadder = listOf(NarrationKey(NarrationId.FW_HINT, args)),
            // Which is shorter is the whole algorithm. Never the app's.
            autoInTry = false,
        )
    }

    override fun apply(state: FwState, action: FwAction): Transition<FwState> = when (action) {
        FwAction.BeginVia -> Transition(
            next = state.copy(viaStarted = true, cursor = 0, lastImproved = null),
            events = listOf(VizEvent.Examine(listOf(state.via), ExamineRole.INSPECTING)),
            narration = NarrationKey(NarrationId.FW_BEGIN_VIA, listOf(state.viaNode.orEmpty())),
            correct = true,
        )

        FwAction.NoRoute -> Transition(
            next = state.copy(cursor = state.cursor + 1, lastImproved = null),
            events = emptyList(),
            narration = null,
            correct = true,
        )

        FwAction.EndVia -> {
            val next = state.copy(via = state.via + 1, viaStarted = false, cursor = 0, lastImproved = null)
            Transition(
                next = next,
                events = if (next.finished) listOf(VizEvent.Terminal(Outcome.Completed(true))) else emptyList(),
                narration = null,
                correct = true,
            )
        }

        is FwAction.SetDistance -> setDistance(state, action.value)
    }

    private fun setDistance(state: FwState, value: Int?): Transition<FwState> {
        val (i, j) = state.current ?: return Transition(state, emptyList(), null, correct = false)
        val slot = state.slotOf(i, j)
        // A wrong number is refused, not applied.
        if (value != state.relaxed(i, j)) {
            return Transition(
                next = state,
                events = listOf(VizEvent.Examine(listOf(slot), ExamineRole.INSPECTING)),
                narration = null,
                correct = false,
            )
        }

        val better = state.improves(i, j)
        val dist = if (better) {
            state.dist.mapIndexed { r, row ->
                row.mapIndexed { c, d -> if ((r == i && c == j) || (r == j && c == i)) value else d }
            }
        } else {
            state.dist
        }
        return Transition(
            next = state.copy(
                dist = dist,
                cursor = state.cursor + 1,
                lastImproved = if (better) i to j else null,
            ),
            events = listOf(
                // One comparison per pair: the detour against what is known.
                VizEvent.Compare(
                    slot,
                    state.slotOf(i, state.via),
                    if (better) Relation.LESS else Relation.GREATER,
                ),
                if (better) VizEvent.Finalize(slot..slot) else VizEvent.Hold(listOf(slot)),
            ),
            narration = NarrationKey(
                if (better) NarrationId.FW_UPDATED else NarrationId.FW_KEPT,
                listOf(state.name(i), state.name(j), distanceLabel(value)),
            ),
            correct = true,
        )
    }
}

/** A table cell's slot: row-major, the same numbering the scene uses. */
fun FwState.slotOf(i: Int, j: Int): Int = i * n + j
