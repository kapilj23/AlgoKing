package com.algorithms.algoking.engine.algorithms.knapsack

import com.algorithms.algoking.engine.core.Frame
import com.algorithms.algoking.engine.core.KnapsackItem
import com.algorithms.algoking.engine.event.Metrics
import com.algorithms.algoking.engine.event.Relation
import com.algorithms.algoking.engine.narration.NarrationId
import com.algorithms.algoking.engine.narration.NarrationKey
import com.algorithms.algoking.engine.scene.Scene
import com.algorithms.algoking.engine.walkthrough.ComparisonReadout
import com.algorithms.algoking.engine.walkthrough.PartialStep
import com.algorithms.algoking.engine.walkthrough.WatchNarrator
import com.algorithms.algoking.engine.walkthrough.WatchStepKind

/**
 * The 0/1 Knapsack walkthrough — twenty-six beats, in two acts.
 *
 * The engine runs one transition per beat of the problem, per cell, and per step of
 * the walk back; this narrator decides which of them teach. The rules are about
 * *where* a beat sits, not which dataset is loaded, so a different bag still gets a
 * coherent script:
 *
 * **Act I — nine beats, and no table.** Every beat of the problem is narrated,
 * because that act *is* the explanation ADR-053 was written to add: the bag, the
 * things, the overflow, the 0/1 rule, the bag packed by hand, the better bag that
 * refutes it, the size of the brute force, and the fork the table is about to
 * mechanise.
 *
 * **Act II — the table**, narrated as sparsely as it was before:
 *
 * - **row 1 is one beat**: the row above is all zeros, so nothing is compared;
 * - **the first cell in row 2 where the item fits is shown in full**, one term of
 *   the recurrence per beat: SKIP, TAKE, then the larger;
 * - **the first TAKE that reads a non-zero cell** is where dynamic programming is
 *   named — an answer worked out once, read back;
 * - **one later row is summarised**, once the rule is familiar, rather than all of
 *   them;
 * - **the last cell is shown in full**, because it is the answer;
 * - **every row of the walk back** is a beat, because each one is a reading.
 *
 * Everything else fills silently, and the pinned test in `KnapsackTest` holds the
 * exact sequence (ADR-025's rule: show the shape, then stop).
 */
class KnapsackWatchNarrator : WatchNarrator<KnapsackState> {

    override fun opening(state: KnapsackState, scene: Scene): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.SETUP,
            scene = scene,
            headline = NarrationKey(NarrationId.KN_WATCH_BAG, listOf(state.capacity)),
            support = NarrationKey(NarrationId.KN_WATCH_BAG_SUPPORT),
        ),
    )

    override fun onFrame(
        previous: KnapsackState,
        frame: Frame<KnapsackState>,
        scene: Scene,
    ): List<PartialStep> {
        val state = frame.state

        // -- Act I: the problem, before the method ----------------------------
        if (state.posing && state.intro != previous.intro) {
            return listOfNotNull(intro(state, scene))
        }

        // -- The table exists -------------------------------------------------
        if (previous.posing && !state.posing) {
            return listOf(
                PartialStep(
                    kind = WatchStepKind.EXAMINE,
                    scene = scene,
                    headline = NarrationKey(NarrationId.KN_WATCH_DEFINE),
                    support = NarrationKey(NarrationId.KN_WATCH_DEFINE_SUPPORT),
                ),
            )
        }

        // -- A cell was opened: only the one shown in full says SKIP first -----
        if (state.focused && !previous.focused) {
            return if (state.position == detailedCell(state)) listOf(skipSide(state, scene)) else emptyList()
        }

        // -- TAKE's cell was named --------------------------------------------
        if (state.source != null && previous.source == null) {
            return when (state.position) {
                answerCell(state) -> listOf(lastCell(state, scene))
                detailedCell(state) -> listOf(takeSide(state, scene))
                reuseCell(state) -> listOf(reuse(state, scene))
                else -> emptyList()
            }
        }

        // -- A cell was written -----------------------------------------------
        val resolved = state.resolved
        if (resolved != null && resolved != previous.resolved) {
            return listOfNotNull(written(state, resolved, scene))
        }

        // -- A row of the walk back was read ----------------------------------
        if (state.taken != previous.taken) return listOf(traced(previous, state, scene))

        return emptyList()
    }

    override fun closing(state: KnapsackState, metrics: Metrics, scene: Scene): List<PartialStep> {
        val best = state.best ?: 0
        val beatsGreedy = state.greedyValue < best
        return listOf(
            PartialStep(
                kind = WatchStepKind.INSIGHT,
                scene = scene,
                headline = NarrationKey(NarrationId.KN_WATCH_INSIGHT),
                support = if (beatsGreedy) {
                    NarrationKey(NarrationId.KN_WATCH_INSIGHT_SUPPORT, listOf(state.greedyValue, best))
                } else {
                    NarrationKey(NarrationId.KN_WATCH_INSIGHT_SUPPORT_PLAIN)
                },
                // `14 > 13` — the table against the bag packed by hand.
                comparison = if (beatsGreedy) {
                    ComparisonReadout(best, Relation.GREATER, state.greedyValue)
                } else {
                    null
                },
            ),
            PartialStep(
                kind = WatchStepKind.SUMMARY,
                scene = scene,
                headline = NarrationKey(
                    NarrationId.KN_WATCH_SUMMARY,
                    listOf(state.itemCount, state.capacity, (state.itemCount + 1) * (state.capacity + 1)),
                ),
                support = NarrationKey(NarrationId.KN_WATCH_SUMMARY_SUPPORT),
                bullets = listOf(
                    NarrationKey(NarrationId.KN_IDEA_1),
                    NarrationKey(NarrationId.KN_IDEA_2),
                    NarrationKey(NarrationId.KN_IDEA_3),
                    NarrationKey(NarrationId.KN_IDEA_4),
                    NarrationKey(NarrationId.KN_IDEA_5),
                ),
            ),
        )
    }

    // -- Act I ----------------------------------------------------------------

    /**
     * One beat per step of the problem.
     *
     * Two of them carry a comparison chip, and both are comparisons the lesson
     * turns on: everything against the bag, and the bag packed by hand against the
     * best one there is.
     */
    private fun intro(state: KnapsackState, scene: Scene): PartialStep? = when (state.intro) {
        IntroBeat.BAG -> null
        IntroBeat.ITEMS -> PartialStep(
            kind = WatchStepKind.EXAMINE,
            scene = scene,
            headline = NarrationKey(NarrationId.KN_WATCH_ITEMS, listOf(state.itemCount)),
            support = NarrationKey(NarrationId.KN_WATCH_ITEMS_SUPPORT),
        )
        IntroBeat.TOO_MUCH -> PartialStep(
            kind = WatchStepKind.COMPARE,
            scene = scene,
            headline = NarrationKey(
                NarrationId.KN_WATCH_TOO_MUCH,
                listOf(state.totalWeight, state.capacity),
            ),
            support = NarrationKey(NarrationId.KN_WATCH_TOO_MUCH_SUPPORT),
            comparison = ComparisonReadout(state.totalWeight, Relation.GREATER, state.capacity),
        )
        IntroBeat.ONCE -> PartialStep(
            kind = WatchStepKind.EXAMINE,
            scene = scene,
            headline = NarrationKey(NarrationId.KN_WATCH_ONCE),
            support = NarrationKey(NarrationId.KN_WATCH_ONCE_SUPPORT),
        )
        IntroBeat.PACKING -> {
            val picked = requireNotNull(state.firstPick)
            PartialStep(
                kind = WatchStepKind.ADD,
                scene = scene,
                headline = NarrationKey(
                    NarrationId.KN_WATCH_PACKING,
                    listOf(picked.name, picked.value),
                ),
                support = NarrationKey(
                    NarrationId.KN_WATCH_PACKING_SUPPORT,
                    listOf(picked.weight, state.capacity, picked.weight, state.roomLeft),
                ),
            )
        }
        IntroBeat.PACKED -> {
            val bag = state.handBag
            PartialStep(
                kind = WatchStepKind.ADD,
                scene = scene,
                headline = NarrationKey(
                    NarrationId.KN_WATCH_PACKED,
                    listOf(bag.joinToString(" + ") { it.name }, bag.sumOf { it.weight }, state.handValue),
                ),
                support = NarrationKey(NarrationId.KN_WATCH_PACKED_SUPPORT, listOf(state.handValue)),
            )
        }
        IntroBeat.COMPARED -> PartialStep(
            kind = WatchStepKind.COMPARE,
            scene = scene,
            headline = NarrationKey(
                NarrationId.KN_WATCH_COMPARED,
                listOf(state.rivalBag.joinToString(" + ") { it.name }, state.bestValue),
            ),
            support = NarrationKey(
                NarrationId.KN_WATCH_COMPARED_SUPPORT,
                listOf(state.rivalBag.sumOf { it.weight }, state.handValue),
            ),
            comparison = ComparisonReadout(state.bestValue, Relation.GREATER, state.handValue),
        )
        IntroBeat.EVERY_BAG -> PartialStep(
            kind = WatchStepKind.EXAMINE,
            scene = scene,
            headline = NarrationKey(NarrationId.KN_WATCH_EVERY_BAG, listOf(state.bagCount)),
            support = NarrationKey(
                NarrationId.KN_WATCH_EVERY_BAG_SUPPORT,
                listOf(state.itemCount, state.bagCount),
            ),
        )
        IntroBeat.FORK -> PartialStep(
            kind = WatchStepKind.EXAMINE,
            scene = scene,
            headline = NarrationKey(NarrationId.KN_WATCH_FORK),
            support = NarrationKey(NarrationId.KN_WATCH_FORK_SUPPORT),
        )
        IntroBeat.GRID -> PartialStep(
            kind = WatchStepKind.EXAMINE,
            scene = scene,
            headline = NarrationKey(NarrationId.KN_WATCH_GRID),
            support = NarrationKey(
                NarrationId.KN_WATCH_GRID_SUPPORT,
                listOf((state.itemCount + 1) * (state.capacity + 1)),
            ),
        )
        IntroBeat.AXES -> PartialStep(
            kind = WatchStepKind.EXAMINE,
            scene = scene,
            headline = NarrationKey(NarrationId.KN_WATCH_AXES),
            support = NarrationKey(
                NarrationId.KN_WATCH_AXES_SUPPORT,
                listOf(
                    state.teachingItems.joinToString(" and ") { it.name },
                    state.teachingCell.col,
                ),
            ),
        )
        IntroBeat.NAME -> PartialStep(
            kind = WatchStepKind.EXAMINE,
            scene = scene,
            headline = NarrationKey(
                NarrationId.KN_WATCH_NAME,
                listOf(state.teachingCell.row, state.teachingCell.col),
            ),
            support = NarrationKey(NarrationId.KN_WATCH_NAME_SUPPORT),
        )
    }

    // -- Which cells teach ----------------------------------------------------

    /** The first cell of row 2 where its item fits — the recurrence, shown in full. */
    private fun detailedCell(state: KnapsackState): TablePos? =
        state.items.getOrNull(1)
            ?.takeIf { it.weight <= state.capacity }
            ?.let { TablePos(2, it.weight) }

    private fun answerCell(state: KnapsackState): TablePos = TablePos(state.itemCount, state.capacity)

    /**
     * The first TAKE, in reading order, that builds on a cell worth more than 0 —
     * a smaller answer being read back. The rows it needs are always computed by
     * the time the scan reaches it, so the answer never changes over the run.
     */
    private fun reuseCell(state: KnapsackState): TablePos? {
        val answer = answerCell(state)
        val detailed = detailedCell(state)
        for (r in 2..state.itemCount) {
            val weight = state.items[r - 1].weight
            for (c in weight..state.capacity) {
                val pos = TablePos(r, c)
                if (pos == answer || pos == detailed) continue
                val sub = state.valueAt(r - 1, c - weight) ?: return null
                if (sub > 0) return pos
            }
        }
        return null
    }

    // -- Beats ----------------------------------------------------------------

    private fun skipSide(state: KnapsackState, scene: Scene): PartialStep {
        val item = requireNotNull(state.item)
        return PartialStep(
            kind = WatchStepKind.KEEP,
            scene = scene,
            headline = NarrationKey(
                NarrationId.KN_WATCH_SKIP_SIDE,
                listOf(state.col, item.name, state.row - 1, state.exclude ?: 0),
            ),
            support = NarrationKey(NarrationId.KN_WATCH_SKIP_SIDE_SUPPORT, listOf(item.name)),
        )
    }

    private fun takeSide(state: KnapsackState, scene: Scene): PartialStep {
        val item = requireNotNull(state.item)
        val source = requireNotNull(state.source)
        val include = state.include ?: 0
        val exclude = state.exclude ?: 0
        return PartialStep(
            kind = WatchStepKind.COMPARE,
            scene = scene,
            headline = NarrationKey(
                NarrationId.KN_WATCH_TAKE_SIDE,
                listOf(item.name, item.value, source.row, source.col, include),
            ),
            support = NarrationKey(NarrationId.KN_WATCH_TAKE_SIDE_SUPPORT, listOf(item.name, source.col)),
            comparison = ComparisonReadout(include, relation(include, exclude), exclude),
        )
    }

    private fun reuse(state: KnapsackState, scene: Scene): PartialStep {
        val item = requireNotNull(state.item)
        val source = requireNotNull(state.source)
        return PartialStep(
            kind = WatchStepKind.COMPARE,
            scene = scene,
            headline = NarrationKey(NarrationId.KN_WATCH_REUSE, listOf(state.col, item.name, source.col)),
            support = NarrationKey(
                NarrationId.KN_WATCH_REUSE_SUPPORT,
                listOf(source.row, source.col, state.valueAt(source) ?: 0),
            ),
        )
    }

    private fun lastCell(state: KnapsackState, scene: Scene): PartialStep {
        val item = requireNotNull(state.item)
        val source = requireNotNull(state.source)
        val include = state.include ?: 0
        val exclude = state.exclude ?: 0
        return PartialStep(
            kind = WatchStepKind.COMPARE,
            scene = scene,
            headline = NarrationKey(NarrationId.KN_WATCH_LAST_CELL, listOf(exclude, include)),
            support = NarrationKey(
                NarrationId.KN_WATCH_LAST_CELL_SUPPORT,
                listOf(item.value, source.row, source.col, state.valueAt(source) ?: 0),
            ),
            comparison = ComparisonReadout(exclude, relation(exclude, include), include),
        )
    }

    private fun written(state: KnapsackState, r: Resolution, scene: Scene): PartialStep? {
        val pos = r.pos
        val item = state.items[pos.row - 1]
        return when {
            pos == answerCell(state) -> answer(state, r, item, scene)

            pos.row == 1 && pos.col == state.capacity -> PartialStep(
                kind = WatchStepKind.ADD,
                scene = scene,
                headline = NarrationKey(NarrationId.KN_WATCH_FIRST_ROW, listOf(item.name)),
                support = NarrationKey(
                    NarrationId.KN_WATCH_FIRST_ROW_SUPPORT,
                    listOf(item.value, item.weight),
                ),
            )

            pos == detailedCell(state) -> max(r, scene)

            pos == reuseCell(state) -> if (r.choice == Choice.TAKE && r.include != null) {
                PartialStep(
                    kind = WatchStepKind.ADD,
                    scene = scene,
                    headline = NarrationKey(
                        NarrationId.KN_WATCH_REUSE_RESULT,
                        listOf(
                            item.value,
                            r.source?.let(state::valueAt) ?: 0,
                            r.include,
                            r.exclude,
                            pos.row,
                            pos.col,
                        ),
                    ),
                    support = NarrationKey(
                        NarrationId.KN_WATCH_REUSE_RESULT_SUPPORT,
                        listOf(state.bagFor(pos.row, pos.col).joinToString(" and ") { it.name }),
                    ),
                )
            } else {
                max(r, scene)
            }

            // The copy-down rule, stated once, on a real row.
            pos.row == 2 && r.include == null && pos.col == item.weight - 1 -> PartialStep(
                kind = WatchStepKind.EXAMINE,
                scene = scene,
                headline = NarrationKey(
                    NarrationId.KN_WATCH_NO_FIT,
                    listOf(item.name, item.weight, pos.col),
                ),
                support = NarrationKey(NarrationId.KN_WATCH_NO_FIT_SUPPORT),
            )

            // One later row, once the rule is familiar — not every one of them.
            pos.row == 3 && pos.col == state.capacity - 1 -> PartialStep(
                kind = WatchStepKind.EXAMINE,
                scene = scene,
                headline = NarrationKey(
                    NarrationId.KN_WATCH_ROW_SUMMARY,
                    listOf(item.name, item.weight),
                ),
                support = NarrationKey(
                    NarrationId.KN_WATCH_ROW_SUMMARY_SUPPORT,
                    listOf(pos.row, pos.col, state.valueAt(pos) ?: 0),
                ),
            )

            else -> null
        }
    }

    private fun max(r: Resolution, scene: Scene): PartialStep {
        val include = r.include ?: r.exclude
        val (win, lose) = if (r.choice == Choice.TAKE) include to r.exclude else r.exclude to include
        return PartialStep(
            kind = WatchStepKind.ADD,
            scene = scene,
            headline = NarrationKey(
                NarrationId.KN_WATCH_MAX,
                listOf(win, lose, r.pos.row, r.pos.col),
            ),
            support = NarrationKey(NarrationId.KN_WATCH_MAX_SUPPORT),
        )
    }

    private fun answer(state: KnapsackState, r: Resolution, item: KnapsackItem, scene: Scene): PartialStep {
        val best = state.best ?: 0
        // Where the hand-packed bag's own item is the one the table leaves out, say
        // so: the refutation, found inside the table rather than argued beside it.
        val support = if (r.choice == Choice.SKIP && item in state.greedyPick && state.greedyValue < best) {
            NarrationKey(NarrationId.KN_WATCH_ANSWER_GREEDY_SUPPORT, listOf(item.name))
        } else {
            NarrationKey(NarrationId.KN_WATCH_ANSWER_SUPPORT, listOf(best))
        }
        val include = r.include
        return when {
            include == null -> PartialStep(
                kind = WatchStepKind.KEEP,
                scene = scene,
                headline = NarrationKey(NarrationId.KN_WATCH_ANSWER_COPY, listOf(item.name, r.exclude)),
                support = support,
            )
            r.choice == Choice.TAKE -> PartialStep(
                kind = WatchStepKind.ADD,
                scene = scene,
                headline = NarrationKey(
                    NarrationId.KN_WATCH_ANSWER_TAKE,
                    listOf(include, r.exclude, item.name),
                ),
                support = support,
            )
            else -> PartialStep(
                kind = WatchStepKind.KEEP,
                scene = scene,
                headline = NarrationKey(
                    NarrationId.KN_WATCH_ANSWER_SKIP,
                    listOf(r.exclude, include, item.name),
                ),
                support = support,
            )
        }
    }

    private fun traced(previous: KnapsackState, state: KnapsackState, scene: Scene): PartialStep {
        val row = previous.traceRow
        val cap = previous.traceCap
        val item = state.items[row - 1]
        val here = state.valueAt(row, cap) ?: 0
        val above = state.valueAt(row - 1, cap) ?: 0
        val taken = state.taken.getOrNull(row - 1) == true

        val support = if (taken) {
            NarrationKey(
                NarrationId.KN_WATCH_TRACE_TAKEN_SUPPORT,
                listOf(row, cap, here, row - 1, above, item.name, cap - item.weight),
            )
        } else {
            NarrationKey(
                NarrationId.KN_WATCH_TRACE_LEFT_OUT_SUPPORT,
                listOf(row, cap, here, row - 1, above, item.name),
            )
        }
        val kind = if (taken) WatchStepKind.ADD else WatchStepKind.ELIMINATE

        return when {
            state.phase == KnapsackPhase.DONE -> {
                val bag = state.bag
                PartialStep(
                    kind = WatchStepKind.FOUND,
                    scene = scene,
                    headline = NarrationKey(
                        NarrationId.KN_WATCH_TRACE_DONE,
                        listOf(
                            if (bag.isEmpty()) "Nothing" else bag.joinToString(" + ") { it.name },
                            bag.sumOf { it.weight },
                            bag.sumOf { it.value },
                        ),
                    ),
                    support = support,
                )
            }
            previous.taken.all { it == null } -> PartialStep(
                kind = kind,
                scene = scene,
                headline = NarrationKey(NarrationId.KN_WATCH_TRACE_START, listOf(state.best ?: 0)),
                support = support,
            )
            else -> PartialStep(
                kind = kind,
                scene = scene,
                headline = NarrationKey(
                    if (taken) NarrationId.KN_WATCH_TRACE_TAKEN else NarrationId.KN_WATCH_TRACE_LEFT_OUT,
                    listOf(item.name),
                ),
                support = support,
            )
        }
    }

    private fun relation(a: Int, b: Int): Relation = when {
        a < b -> Relation.LESS
        a > b -> Relation.GREATER
        else -> Relation.EQUAL
    }
}
