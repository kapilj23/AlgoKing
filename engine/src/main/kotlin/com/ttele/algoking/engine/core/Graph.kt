package com.ttele.algoking.engine.core

/**
 * An authored graph — nodes, edges, and the order neighbours are explored in.
 *
 * ### Neighbour order is part of the data, never of the UI
 *
 * DFS's traversal is decided entirely by which neighbour it tries first, so that
 * order is **authored here and read from here by everything**: the engine, the
 * walkthrough, Try and the tests. A renderer that sorted nodes for its own
 * convenience would silently produce a different traversal than the lesson
 * teaches, which is why [adjacency] is an ordered `List` rather than a `Set`.
 *
 * [GraphNode.x] and [GraphNode.y] are authored layout, in the same spirit that
 * `Dataset.values` is authored: a teaching graph has a shape the learner should
 * see, and no automatic layout gives a nicer one for five nodes. **The algorithm
 * never reads them** — only the projector does.
 */
data class Graph(
    val nodes: List<GraphNode>,
    /** node id → the neighbours it explores, in order. */
    val adjacency: Map<String, List<String>>,
) {
    val ids: List<String> get() = nodes.map { it.id }

    fun indexOf(id: String): Int = nodes.indexOfFirst { it.id == id }

    fun node(id: String): GraphNode? = nodes.firstOrNull { it.id == id }

    /**
     * The neighbours of [id], in authored order, with anything unknown dropped.
     *
     * Total by design: an edge naming a node that does not exist is bad data, not
     * a crash, and a lesson should still run on the part of the graph that is real.
     */
    fun neighbours(id: String): List<String> =
        adjacency[id].orEmpty().filter { neighbour -> nodes.any { it.id == neighbour } }

    /**
     * Every node reachable from [start], including it.
     *
     * This is what makes a disconnected graph well defined: DFS visits its own
     * component and stops, rather than silently jumping to an unrelated one.
     */
    fun reachableFrom(start: String): Set<String> {
        if (node(start) == null) return emptySet()
        val seen = linkedSetOf(start)
        val pending = ArrayDeque(listOf(start))
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            for (next in neighbours(current)) {
                if (seen.add(next)) pending.addLast(next)
            }
        }
        return seen
    }

    /** Undirected edges, de-duplicated, for drawing. */
    val edges: List<Pair<String, String>>
        get() = buildList {
            val seen = mutableSetOf<Pair<String, String>>()
            for (node in nodes) {
                for (other in neighbours(node.id)) {
                    val key = if (node.id <= other) node.id to other else other to node.id
                    if (seen.add(key)) add(key)
                }
            }
        }
}

/**
 * One node. [x] and [y] are normalised 0..1 layout, authored with the graph.
 */
data class GraphNode(
    val id: String,
    val label: String,
    val x: Float,
    val y: Float,
)

/**
 * `A` · `A and B` · `A, B and C` — a list a sentence can contain.
 *
 * Narration reads as prose, so a bare `joinToString(", ")` dropped into
 * "… is already visited" produces "A, D is already visited", which is wrong in
 * two ways at once. The caller still picks the singular or plural sentence; this
 * only makes the list itself grammatical.
 */
fun joinNames(names: List<String>): String = when (names.size) {
    0 -> ""
    1 -> names[0]
    2 -> "${names[0]} and ${names[1]}"
    else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
}
