package com.algorithms.algoking.engine.algorithms.kruskal

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
import com.algorithms.algoking.engine.decision.EdgeListReadout
import com.algorithms.algoking.engine.event.ExamineRole
import com.algorithms.algoking.engine.event.MeterId
import com.algorithms.algoking.engine.event.Outcome
import com.algorithms.algoking.engine.event.Relation
import com.algorithms.algoking.engine.event.VizEvent
import com.algorithms.algoking.engine.narration.NarrationId
import com.algorithms.algoking.engine.narration.NarrationKey

/**
 * What the learner does in Kruskal.
 *
 * **One question, asked once per edge: take it, or skip it?** The app walks the
 * sorted list — sorting is not what is being taught — and the learner decides,
 * from the groups on screen, whether the edge joins two separate pieces or would
 * only close a loop.
 */
sealed interface KruskalAction : Action {
    data object Take : KruskalAction
    data object Skip : KruskalAction
}

data class KruskalEdge(val a: String, val b: String, val weight: Int) {
    val label: String get() = "$a – $b"
}

/**
 * Immutable state — the single source of truth.
 *
 * [taken] is the whole model: the groups, the total, the loops and the next edge
 * are all derived from it and the cursor, so **no Composable performs a
 * comparison or an addition**.
 */
data class KruskalState(
    val graph: Graph,
    /** Every edge, cheapest first; equal weights keep the graph's own order. */
    val sorted: List<KruskalEdge>,
    /** How far down [sorted] the walk has got. */
    val cursor: Int,
    val taken: List<KruskalEdge>,
    val skipped: List<KruskalEdge>,
) {
    val total: Int get() = taken.sumOf { it.weight }

    /** A tree over n nodes has n − 1 edges; once it has them, nothing else is looked at. */
    val needed: Int get() = (graph.nodes.size - 1).coerceAtLeast(0)

    val finished: Boolean get() = taken.size >= needed || cursor >= sorted.size

    /** True when the finished tree reaches every node. */
    val spanning: Boolean get() = taken.size == needed

    /** The edge being decided, or null once the walk is over. */
    val next: KruskalEdge? get() = if (finished) null else sorted.getOrNull(cursor)

    /**
     * **Which nodes are already connected**, from the taken edges alone. Each group
     * is in graph order, and the groups are ordered by their first member, so the
     * picture reads the same way every time.
     */
    val groups: List<List<String>>
        get() {
            val root = graph.ids.associateWith { it }.toMutableMap()
            fun find(x: String): String {
                var r = x
                while (root.getValue(r) != r) r = root.getValue(r)
                return r
            }
            for (edge in taken) root[find(edge.a)] = find(edge.b)
            return graph.ids.groupBy { find(it) }.values.toList()
        }

    fun groupOf(node: String): List<String> = groups.first { node in it }

    /** **The rule, in one line**: an edge inside one group only closes a loop. */
    fun makesLoop(edge: KruskalEdge): Boolean = groupOf(edge.a).contains(edge.b)

    /**
     * The list and the groups. [showNext] marks the edge about to be decided; Watch
     * turns it off when it is narrating the edge that was *just* decided, so the
     * list never points at a different edge from the words.
     */
    fun edgeListReadout(showNext: Boolean = true): EdgeListReadout {
        val takenSet = taken.toSet()
        val skippedSet = skipped.toSet()
        val current = if (showNext) next else null
        return EdgeListReadout(
            rows = sorted.map { edge ->
                EdgeListReadout.Row(
                    label = edge.label,
                    weight = edge.weight,
                    status = when (edge) {
                        in takenSet -> EdgeListReadout.Status.TAKEN
                        in skippedSet -> EdgeListReadout.Status.SKIPPED
                        current -> EdgeListReadout.Status.NEXT
                        else -> EdgeListReadout.Status.WAITING
                    },
                )
            },
            groups = groups,
        )
    }
}

/** `{A, B, C}` — how a group is written everywhere the learner reads one. */
fun groupLabel(group: List<String>): String = group.joinToString(", ", "{", "}")

/**
 * Kruskal's algorithm — an Advanced lesson, and the second minimum-spanning-tree one.
 *
 * ```
 * sort every edge, cheapest first
 * every node starts in a group of its own
 * for each edge, in that order:
 *     if its two ends are in different groups: take it, and merge the groups
 *     otherwise: skip it — it would only make a loop
 * stop once n − 1 edges are taken
 * ```
 *
 * ### What the learner has to understand
 *
 * 1. **The only check is "same group?"** Nothing about where the edge is, or how
 *    close it is to what was taken before.
 * 2. **It does not grow from a start.** The first edges taken can be far apart;
 *    Kruskal builds separate pieces and lets them merge. That is the difference
 *    from Prim, which the teaching graph shows by taking its first edge on the
 *    far side from A.
 * 3. **Stop at n − 1.** The most expensive edges are never even looked at.
 *
 * **Time O(E log E)**, for the sort; the group checks are near-constant with
 * union-find. **Space O(V)**.
 */
class KruskalAlgorithm : Algorithm<KruskalState, KruskalAction> {

    override val id = AlgorithmId.KRUSKAL

    override fun initial(dataset: Dataset): KruskalState {
        val graph = dataset.graph ?: Graph(emptyList(), emptyMap())
        val sorted = graph.edges
            .map { (a, b) -> KruskalEdge(a, b, graph.weightOf(a, b) ?: 0) }
            // Stable, so equal weights keep the graph's authored order.
            .sortedBy { it.weight }
        return KruskalState(graph, sorted, cursor = 0, taken = emptyList(), skipped = emptyList())
    }

    override fun probe(state: KruskalState): Probe<KruskalAction> = when {
        state.finished && state.spanning -> Probe.Terminal(Outcome.Completed(true))
        // The list ran out first: the graph is not connected, so no tree spans it.
        state.finished -> Probe.Terminal(Outcome.NotFound)
        else -> Probe.Decide(decision(state))
    }

    private fun decision(state: KruskalState): Decision<KruskalAction> {
        val edge = requireNotNull(state.next)
        val loop = state.makesLoop(edge)
        val groupA = groupLabel(state.groupOf(edge.a))
        val groupB = groupLabel(state.groupOf(edge.b))
        // In graph order, like every other group on screen: {D, E, F}, not {D, F, E}.
        val merged = groupLabel(
            (state.groupOf(edge.a) + state.groupOf(edge.b)).sortedBy(state.graph.ids::indexOf),
        )

        return Decision(
            kind = DecisionKind.OPTIONS,
            prompt = NarrationKey(NarrationId.KR_ASK, listOf(edge.label, edge.weight)),
            options = listOf(
                ActionOption<KruskalAction>(
                    action = KruskalAction.Take,
                    label = NarrationKey(NarrationId.KR_OPTION_TAKE),
                ),
                ActionOption(
                    action = KruskalAction.Skip,
                    label = NarrationKey(NarrationId.KR_OPTION_SKIP),
                ),
            ),
            correct = if (loop) KruskalAction.Skip else KruskalAction.Take,
            focus = listOf(state.graph.indexOf(edge.a), state.graph.indexOf(edge.b)),
            hint = NarrationKey(NarrationId.KR_HINT, listOf(edge.a, edge.b)),
            guidance = listOf(
                NarrationKey(NarrationId.KR_RETRY_LOOK, listOf(edge.a, edge.b)),
                NarrationKey(NarrationId.KR_RETRY_ASK, listOf(edge.a, edge.b)),
                if (loop) {
                    NarrationKey(NarrationId.KR_RETRY_EXPLAIN_SKIP, listOf(edge.a, edge.b, groupA, edge.label))
                } else {
                    NarrationKey(
                        NarrationId.KR_RETRY_EXPLAIN_TAKE,
                        listOf(edge.a, groupA, edge.b, groupB, edge.label),
                    )
                },
            ),
            minimalFeedback = NarrationKey(NarrationId.KR_RETRY_LOOK, listOf(edge.a, edge.b)),
            // Each wrong button is the mistake it encodes.
            whyWrong = if (loop) {
                mapOf(
                    KruskalAction.Take to NarrationKey(
                        NarrationId.KR_WHY_LOOP,
                        listOf(edge.a, edge.b, groupA, edge.label),
                    ),
                )
            } else {
                mapOf(
                    KruskalAction.Skip to NarrationKey(
                        NarrationId.KR_WHY_NEEDED,
                        listOf(edge.a, groupA, edge.b, groupB, edge.label),
                    ),
                )
            },
            correctFeedback = if (loop) {
                NarrationKey(NarrationId.KR_CORRECT_SKIP, listOf(edge.label, edge.a, edge.b, groupA))
            } else {
                NarrationKey(
                    NarrationId.KR_CORRECT_TAKE,
                    listOf(edge.label, groupA, groupB, merged, state.total + edge.weight),
                )
            },
            hintLadder = listOf(
                NarrationKey(NarrationId.KR_HINT, listOf(edge.a, edge.b)),
                NarrationKey(NarrationId.KR_RETRY_ASK, listOf(edge.a, edge.b)),
            ),
            edgeList = state.edgeListReadout(),
            // Whether the ends are already connected is the whole technique.
            autoInTry = false,
        )
    }

    override fun apply(state: KruskalState, action: KruskalAction): Transition<KruskalState> {
        val edge = state.next ?: return Transition(state, emptyList(), null, correct = false)
        val loop = state.makesLoop(edge)
        val right = if (loop) KruskalAction.Skip else KruskalAction.Take
        val a = state.graph.indexOf(edge.a)
        val b = state.graph.indexOf(edge.b)

        // A wrong answer is refused, not applied — the same refusal Prim gives.
        if (action != right) {
            return Transition(
                next = state,
                events = listOf(VizEvent.Examine(listOf(a, b), ExamineRole.INSPECTING)),
                narration = null,
                correct = false,
            )
        }

        val next = if (loop) {
            state.copy(cursor = state.cursor + 1, skipped = state.skipped + edge)
        } else {
            state.copy(cursor = state.cursor + 1, taken = state.taken + edge)
        }
        return Transition(
            next = next,
            events = buildList {
                // One group check per edge: are the two ends already connected?
                add(VizEvent.Compare(a, b, if (loop) Relation.EQUAL else Relation.LESS))
                if (loop) {
                    add(VizEvent.Hold(listOf(a, b)))
                } else {
                    add(VizEvent.Examine(listOf(a, b), ExamineRole.COMPARING))
                    add(VizEvent.Meter(MeterId.RUNNING_SUM, next.total.toLong()))
                }
                if (next.finished) {
                    add(VizEvent.Terminal(if (next.spanning) Outcome.Completed(true) else Outcome.NotFound))
                }
            },
            narration = NarrationKey(
                if (loop) NarrationId.KR_SKIPPED else NarrationId.KR_TAKEN,
                listOf(edge.label, edge.weight, next.total),
            ),
            correct = true,
        )
    }
}
