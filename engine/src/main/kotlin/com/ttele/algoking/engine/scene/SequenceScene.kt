package com.ttele.algoking.engine.scene

import com.ttele.algoking.engine.event.MarkId
import com.ttele.algoking.engine.event.MeterId
import com.ttele.algoking.engine.event.PointerId
import com.ttele.algoking.engine.event.RegionId
import com.ttele.algoking.engine.event.VizEvent

/**
 * What a lesson looks like, as data — ARCHITECTURE.md §7.1.
 *
 * Every lesson projects into one of these, and the app picks a renderer by *shape*.
 * A scene never names an algorithm, so no renderer can branch on one.
 */
sealed interface Scene

/**
 * The renderer contract — ARCHITECTURE.md §7.1.
 *
 * One renderer serves every sequence lesson. It receives pure data and has no way
 * to ask which algorithm produced it; what it branches on is [layout], which is a
 * statement about the *shape* of the data, never about who sent it.
 */
data class SequenceScene(
    val cells: List<Cell>,
    val pointers: List<PointerMark> = emptyList(),
    val regions: List<RegionMark> = emptyList(),
    /** e.g. "Target 73". */
    val badge: Badge? = null,
    val meters: List<MeterReadout> = emptyList(),
    val layout: SceneLayout = SceneLayout.ROW,
    /** Two slots exchanging, for the arc. */
    val arc: Arc? = null,
    /**
     * Contiguous groups the sequence is currently divided into. The renderer draws
     * a separator between them — it is how a divide-and-conquer algorithm shows
     * its structure without needing a second kind of renderer.
     */
    val groups: List<IntRange> = emptyList(),
    /**
     * Labels flanking the whole sequence, e.g. `FRONT →` and `← REAR`. A Queue has
     * two live ends and has to say so; an array algorithm leaves this null and the
     * renderer draws nothing.
     */
    val endCaps: EndCaps? = null,
    /**
     * The arrows of a [SceneLayout.CHAIN]. Empty for every other layout.
     *
     * A linked list is *made of* its links, so they are first-class scene data
     * rather than decoration inferred from cell order — which is what lets the
     * renderer show a link being followed, cut, rewired and closed again.
     */
    val links: List<Link> = emptyList(),
    /**
     * A node that exists but is not in the chain yet — the moment between making a
     * node and connecting it. Drawn above the gap it is destined for.
     */
    val detached: DetachedNode? = null,
    /**
     * Renames a cell state in the legend. CANDIDATE means "the smallest so far" to
     * a sort and "the one you can reach" to a stack, and the legend has to say
     * whichever is true without the renderer knowing which algorithm is running.
     */
    val legendLabels: Map<CellState, String> = emptyMap(),
    /**
     * Draw each cell's position beneath it.
     *
     * On by default nowhere: for a sort, the values are the subject and a row of
     * indices is noise. For Binary Search the positions *are* the subject — the
     * middle is arithmetic on them — so a lesson that computes an index has to
     * ask for the indices to be visible, and this is how it asks.
     */
    val showIndices: Boolean = false,
) : Scene

/**
 * Dress a projected scene as a mission, without the projector knowing that
 * missions exist.
 *
 * The algorithm searched ranks or ids; the learner reads titles or prices. Only
 * the words change — every cell state, pointer and region the projector decided
 * survives untouched, which is what keeps one Binary Search honest across four
 * different worlds.
 */
fun SequenceScene.asMission(labels: List<String>, targetLabel: String?): SequenceScene {
    if (labels.isEmpty()) return this
    return copy(
        cells = cells.map { it.copy(label = labels.getOrNull(it.slot)) },
        badge = badge?.copy(valueLabel = targetLabel),
        // Boxes big enough to print a whole id, wrapping rather than shrinking.
        layout = SceneLayout.GRID,
        // No positions. Watch showed the arithmetic and Try practised it; a
        // Challenge that numbers the boxes has done the counting for the learner.
        showIndices = false,
    )
}

/** Labels at the two extremes of a sequence. Either side may be absent. */
data class EndCaps(val leading: String? = null, val trailing: String? = null)

/**
 * [key] is assigned at dataset creation and **never changes**. A swap changes two
 * cells' [slot], not their identity — which is what lets the renderer animate an
 * exchange instead of flickering two boxes with different numbers in them.
 */
data class Cell(
    val key: Int,
    val value: Int,
    val slot: Int,
    val state: CellState,
    /**
     * What the learner reads on this cell, when the number is not the point.
     *
     * A Library mission searches book titles on an engine that only understands
     * integers: the values are alphabetical ranks, and the label is the title.
     * Null everywhere else, so every existing lesson still draws its value.
     */
    val label: String? = null,
)

enum class CellState { IDLE, EXAMINING, COMPARING, CANDIDATE, FINALIZED, ELIMINATED, GHOST }

/** How the cells are arranged. The renderer's only branch. */
enum class SceneLayout {
    /** Left to right. Every array algorithm. */
    ROW,

    /** Top to bottom, slot 0 highest. A stack. */
    PILE,

    /** Nodes joined by arrows, with a head and a null terminator. A linked list. */
    CHAIN,

    /**
     * Boxes that wrap onto as many rows as they need.
     *
     * A row squeezes twelve cells into a twelfth of the width each, which is
     * fine for a two-digit value and useless for a product id. When the label is
     * the subject — a mission — the boxes keep their size and the layout gives
     * way instead.
     */
    GRID,
}

/**
 * One arrow in a chain.
 *
 * [slot] indexes the **gaps**, not the nodes: link `0` is the arrow from HEAD into
 * the first node, link `i` is the arrow from node `i-1` into node `i`, and link `n`
 * is the arrow from the last node to NULL. Numbering the gaps is what turns
 * "which link must change?" into a question the learner can point at.
 */
data class Link(val slot: Int, val state: LinkState = LinkState.SETTLED)

enum class LinkState {
    /** A connection that is simply there. */
    SETTLED,

    /** The link currently being followed, or the one under consideration. */
    ACTIVE,

    /** Cut, and not reconnected yet. The list is mid-operation. */
    OPEN,

    /** Just created. */
    NEW,
}

/** A node held outside the chain, waiting to be linked in at gap [atLink]. */
data class DetachedNode(val value: Int, val atLink: Int)

data class PointerMark(val pointer: PointerId, val slot: Int, val label: String)

data class RegionMark(val region: RegionId, val range: IntRange)

data class Badge(
    val mark: MarkId,
    val label: String,
    val value: Int,
    /** Overrides [value] when the target is not a bare number. */
    val valueLabel: String? = null,
)

data class MeterReadout(val meter: MeterId, val label: String, val value: Long)

data class Arc(val from: Int, val to: Int)

/**
 * Algorithm-specific *presentation* knowledge lives beside the algorithm; the
 * renderer stays generic — ARCHITECTURE.md §7.2.
 */
interface SceneProjector<S : Any> {
    fun project(state: S, activeEvents: List<VizEvent>): Scene
}
