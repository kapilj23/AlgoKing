package com.ttele.algoking.engine.scene

/**
 * A graph — DESIGN_SYSTEM.md §6.16g.
 *
 * The fourth scene shape, and the first whose picture is genuinely
 * **two-dimensional**. Every other lesson is a line of cells, a pile, a chain, a
 * table or two aligned rows; a graph is nodes at positions with edges between
 * them, and neither the positions nor the edges can be expressed as slots.
 *
 * Node state reuses [CellState] rather than inventing a parallel enum, so the
 * legend, the colours and the words are the ones every other lesson already uses:
 *
 * | DFS means | is |
 * |---|---|
 * | the node DFS is standing on | `COMPARING` |
 * | already visited | `FINALIZED` |
 * | not visited yet | `IDLE` |
 */
data class GraphScene(
    val nodes: List<GraphNodeView>,
    val edges: List<GraphEdgeView>,
    /** The visit order so far, as labels: `A → B → D`. */
    val traversal: List<String>,
    /**
     * The path DFS is currently standing on, deepest last — the call stack made
     * visible. `[A, B, D]` means DFS reached D through B through A, and that is
     * exactly the route a backtrack will unwind.
     */
    val stack: List<String>,
    /**
     * The queue, front first — BFS only.
     *
     * Empty for a lesson that does not use one, so DFS draws no queue and needed
     * no change when this arrived. It is a `List` rather than a set because the
     * *order* is the entire idea: first in, first out.
     */
    val queue: List<String> = emptyList(),
    /** What the path strip is called. A stack for DFS, a queue for BFS. */
    val pathLabel: String = "Path",
    val legendLabels: Map<CellState, String> = emptyMap(),
) : Scene

/**
 * One node. [x] and [y] are normalised 0..1, so the renderer scales them to
 * whatever width it is given without the scene knowing anything about dp.
 */
data class GraphNodeView(
    val slot: Int,
    val label: String,
    val state: CellState,
    val x: Float,
    val y: Float,
)

data class GraphEdgeView(
    val from: Int,
    val to: Int,
    val state: EdgeState,
)

enum class EdgeState {
    /** An edge that is simply there. */
    IDLE,

    /** On the current path: DFS came this way and has not unwound past it. */
    PATH,

    /** The step just taken, going deeper. */
    ACTIVE,

    /** The step just taken, unwinding. Drawn differently, because it is the
     *  half of DFS learners lose. */
    BACKTRACK,
}
