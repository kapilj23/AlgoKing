package com.ttele.algoking.engine.core

import com.ttele.algoking.engine.decision.Action
import com.ttele.algoking.engine.decision.Decision
import com.ttele.algoking.engine.event.Metrics
import com.ttele.algoking.engine.event.MetricsFolder
import com.ttele.algoking.engine.event.Outcome
import com.ttele.algoking.engine.event.VizEvent
import com.ttele.algoking.engine.narration.NarrationKey

/** The dataset an algorithm starts from. Authored for Watch/Try, generated for Challenge. */
data class Dataset(
    val values: List<Int>,
    val target: Int? = null,
    val label: String = "",
    /**
     * An authored range query, for lessons that ask about a span rather than a
     * value — Prefix Sum asks for `sum(left..right)`.
     *
     * Optional and defaulted, so no existing lesson changed when it arrived, and
     * it is the seam a second range or a generated one plugs into later.
     */
    val queryLeft: Int? = null,
    val queryRight: Int? = null,
    /**
     * A graph, for lessons whose data is nodes and edges rather than an array —
     * DFS traverses one. Optional and defaulted, so no array lesson changed when
     * it arrived, and a second graph later is data rather than code.
     */
    val graph: Graph? = null,
    /** Where a graph traversal begins. */
    val startNode: String? = null,
)

enum class AlgorithmId {
    BINARY_SEARCH,

    // Advanced: a technique rather than a named routine. Two cursors walking a
    // sorted array toward each other, discarding a whole column of pairs with
    // every step.
    TWO_POINTERS,

    // Advanced: precomputation. Build a table of running totals once, then answer
    // any range sum with one subtraction.
    PREFIX_SUM,

    // Advanced: the first lesson whose data is a graph rather than an array.
    GRAPH_DFS,

    // Advanced: the same graph as DFS, driven by a queue instead of a stack.
    GRAPH_BFS,

    BUBBLE_SORT,
    SELECTION_SORT,
    INSERTION_SORT,
    MERGE_SORT,
    QUICK_SORT,

    // Data structures, not algorithms: there is no array to sort and no target to
    // find. What they teach is the rule that decides which item you may touch.
    STACK,
    QUEUE,
    LINKED_LIST,
    HASH_MAP,
}

/**
 * The three primitives — ARCHITECTURE.md §4.1.
 *
 * `probe` never mutates and never chooses; `apply` never decides. That separation
 * is what lets one algorithm serve Watch (auto-choose), Try (learner chooses,
 * guided) and Challenge (learner chooses, unguided) with no `mode` flag inside it.
 */
interface Algorithm<S : Any, A : Action> {

    val id: AlgorithmId

    /** Build the starting state from a dataset. Pure. */
    fun initial(dataset: Dataset): S

    /** What does the algorithm want to do next, from THIS state? Pure. */
    fun probe(state: S): Probe<A>

    /**
     * Apply ANY legal action — correct or not — and return the resulting state.
     * Applying an incorrect action is not an error path; it is the same code path
     * with a different argument. Pure.
     */
    fun apply(state: S, action: A): Transition<S>
}

sealed interface Probe<out A : Action> {

    /** No learner input: the app advances. */
    data class Mechanical<A : Action>(val action: A) : Probe<A>

    /** The learner decides. Carries every legal option AND which one is correct. */
    data class Decide<A : Action>(val decision: Decision<A>) : Probe<A>

    /** The algorithm has finished — correctly or otherwise. */
    data class Terminal(val outcome: Outcome) : Probe<Nothing>
}

data class Transition<S : Any>(
    val next: S,
    val events: List<VizEvent>,
    val narration: NarrationKey?,
    /** Was the applied action the correct one? */
    val correct: Boolean,
)

data class Frame<S>(
    val index: Int,
    val state: S,
    val events: List<VizEvent>,
    val narration: NarrationKey?,
    val correct: Boolean,
    val metrics: Metrics,
)

@JvmInline
value class Trace<S>(val frames: List<Frame<S>>)

/**
 * The runner — ARCHITECTURE.md §4.2.
 * There is one execution path in the system: Watch, the Try preamble, the mercy
 * exit and the dataset validator all come through [runToCompletion].
 */
class AlgorithmRunner<S : Any, A : Action>(
    private val algorithm: Algorithm<S, A>,
    private val dataset: Dataset,
) {
    private val history = ArrayDeque<Frame<S>>()

    var current: Frame<S> = seed()
        private set

    private fun seed() = Frame(
        index = 0,
        state = algorithm.initial(dataset),
        events = emptyList(),
        narration = null,
        correct = true,
        metrics = Metrics.EMPTY,
    )

    fun probe(): Probe<A> = algorithm.probe(current.state)

    fun apply(action: A): Frame<S> {
        val transition = algorithm.apply(current.state, action)
        history.addLast(current)
        current = Frame(
            index = current.index + 1,
            state = transition.next,
            events = transition.events,
            narration = transition.narration,
            correct = transition.correct,
            metrics = MetricsFolder.fold(current.metrics, transition.events, transition.correct),
        )
        return current
    }

    /** Exact, not recomputed — states are immutable values. */
    fun rewind(steps: Int = 1): Frame<S> {
        repeat(steps) { if (history.isNotEmpty()) current = history.removeLast() }
        return current
    }

    fun canRewind(): Boolean = history.isNotEmpty()

    fun reset() {
        history.clear()
        current = seed()
    }

    /** Drive to completion taking the correct option at every decision. */
    fun runToCompletion(maxSteps: Int = MAX_STEPS): Trace<S> {
        val frames = mutableListOf(current)
        var guard = 0
        while (guard++ < maxSteps) {
            when (val probe = probe()) {
                is Probe.Mechanical -> frames += apply(probe.action)
                is Probe.Decide -> frames += apply(probe.decision.correct)
                is Probe.Terminal -> return Trace(frames.toList())
            }
        }
        error("Algorithm ${algorithm.id} exceeded $maxSteps steps — non-terminating.")
    }

    private companion object {
        /** Hard termination guard — ARCHITECTURE.md §4.2. */
        const val MAX_STEPS = 4_096
    }
}
