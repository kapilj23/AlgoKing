package com.ttele.algoking.engine.algorithms.linkedlist

import com.ttele.algoking.engine.core.Algorithm
import com.ttele.algoking.engine.core.AlgorithmId
import com.ttele.algoking.engine.core.Dataset
import com.ttele.algoking.engine.core.Probe
import com.ttele.algoking.engine.core.Transition
import com.ttele.algoking.engine.decision.ActionOption
import com.ttele.algoking.engine.decision.Decision
import com.ttele.algoking.engine.decision.DecisionKind
import com.ttele.algoking.engine.event.ExamineRole
import com.ttele.algoking.engine.event.Outcome
import com.ttele.algoking.engine.event.VizEvent
import com.ttele.algoking.engine.narration.NarrationId
import com.ttele.algoking.engine.narration.NarrationKey

/**
 * Linked List — a chain of nodes joined by links.
 *
 * The interaction is deliberately **not** the one Stack and Queue use. Those ask
 * *which operation does this job?*, because their whole content is which end you
 * may touch. A linked list has no privileged end: its content is that changing the
 * list means **changing the arrows**, so every decision here is one of two
 * questions:
 *
 *  - *is this the node?* — walking, one node at a time, because that is the only
 *    way to reach anything in a linked list; or
 *  - *which link changes, and where should it point?* — the two halves of every
 *    insertion and every deletion.
 *
 * A learner who can answer those can implement a linked list. One who can only tap
 * the right box has learned a diagram.
 */
class LinkedListAlgorithm : Algorithm<LinkedListState, ListAction> {

    override val id = AlgorithmId.LINKED_LIST

    override fun initial(dataset: Dataset): LinkedListState {
        val nodes = dataset.values.mapIndexed { index, value -> LinkedNode(index, value) }
        val script = ListScripts.forDataset(dataset)
        val first = script.firstOrNull()
        return LinkedListState(
            nodes = nodes,
            script = script,
            at = 0,
            // Every operation starts at HEAD, because there is nowhere else to start.
            cursor = if (first is ListTask.Find) 0 else null,
            phase = phaseFor(first),
            nextId = nodes.size + 1_000,
            done = script.isEmpty(),
        )
    }

    // ── Probing ───────────────────────────────────────────────────────────────

    override fun probe(state: LinkedListState): Probe<ListAction> {
        if (state.done || state.at >= state.script.size) {
            return Probe.Terminal(Outcome.Completed(correct = true))
        }
        return when (val task = requireNotNull(state.task)) {
            is ListTask.Find -> findProbe(state, task)
            is ListTask.Insert -> insertProbe(state, task)
            is ListTask.Delete -> deleteProbe(state, task)
        }
    }

    /**
     * Walking *is* searching a linked list, so every node on the way is a real
     * question. There is no shortcut to offer, because the structure has none.
     */
    private fun findProbe(state: LinkedListState, task: ListTask.Find): Probe<ListAction> {
        // The walk is over. Its last frame gets to stand on its own — the found
        // node stays lit, or NULL stays reached — before the lesson moves on.
        if (state.found || state.hitNull) return Probe.Mechanical(ListAction.Skip)
        // Nothing to walk. Explained, never crashed.
        if (state.empty) return Probe.Mechanical(ListAction.Skip)
        val cursor = state.cursor ?: 0
        if (cursor !in state.nodes.indices) return Probe.Mechanical(ListAction.Skip)

        val here = state.nodes[cursor]
        val matches = here.value == task.target
        val pair = listOf(here.value, task.target)

        return Probe.Decide(
            Decision(
                kind = DecisionKind.OPTIONS,
                prompt = NarrationKey(NarrationId.LIST_ASK_IS_THIS_IT, listOf(here.value)),
                options = listOf(
                    ActionOption(
                        ListAction.Answer(true),
                        NarrationKey(NarrationId.LIST_OPTION_YES),
                    ),
                    ActionOption(
                        ListAction.Answer(false),
                        NarrationKey(NarrationId.LIST_OPTION_NO),
                    ),
                ),
                correct = ListAction.Answer(matches),
                focus = listOf(cursor),
                hint = NarrationKey(NarrationId.LIST_HINT_COMPARE, pair),
                guidance = listOf(
                    NarrationKey(NarrationId.LIST_RETRY_LOOK, pair),
                    NarrationKey(NarrationId.LIST_RETRY_ASK_MATCH, pair),
                    NarrationKey(
                        if (matches) {
                            NarrationId.LIST_RETRY_EXPLAIN_MATCH
                        } else {
                            NarrationId.LIST_RETRY_EXPLAIN_NO_MATCH
                        },
                        pair,
                    ),
                ),
                minimalFeedback = NarrationKey(NarrationId.LIST_RETRY_LOOK, pair),
                whyWrong = mapOf<ListAction, NarrationKey>(
                    ListAction.Answer(!matches) to NarrationKey(
                        if (matches) {
                            NarrationId.LIST_WHY_MISSED_MATCH
                        } else {
                            NarrationId.LIST_WHY_NOT_A_MATCH
                        },
                        pair,
                    ),
                ),
                correctFeedback = if (matches) {
                    NarrationKey(NarrationId.LIST_FOUND, listOf(here.value))
                } else {
                    NarrationKey(NarrationId.LIST_FOLLOW_NEXT, listOf(here.value))
                },
                hintLadder = listOf(
                    NarrationKey(NarrationId.LIST_HINT_COMPARE, pair),
                    NarrationKey(NarrationId.LIST_HINT_WALK),
                ),
            ),
        )
    }

    private fun insertProbe(state: LinkedListState, task: ListTask.Insert): Probe<ListAction> =
        when (state.phase) {
            ListPhase.LINKING -> Probe.Decide(linkingDecision(state, task.value))
            else -> Probe.Decide(gapDecision(state, task))
        }

    private fun deleteProbe(state: LinkedListState, task: ListTask.Delete): Probe<ListAction> {
        // Deleting something that is not there is worth teaching, not a bug to hide.
        if (state.indexOfValue(task.value) == null) return Probe.Mechanical(ListAction.Skip)
        return when (state.phase) {
            ListPhase.RECONNECTING -> Probe.Decide(reconnectDecision(state, task))
            else -> Probe.Decide(gapDecision(state, task))
        }
    }

    /**
     * "Which gap?" — a `CELL` decision whose slots are **links**, not nodes.
     *
     * One question serves insertion and deletion, because in both the first move is
     * the same: find the arrow that has to change.
     */
    private fun gapDecision(state: LinkedListState, task: ListTask): Decision<ListAction> {
        val inserting = task is ListTask.Insert
        val value = when (task) {
            is ListTask.Insert -> task.value
            is ListTask.Delete -> task.value
            is ListTask.Find -> 0
        }
        val answer = when (task) {
            is ListTask.Insert -> state.sortedGap(task.value)
            // The arrow *into* the doomed node is the one that must be redirected.
            is ListTask.Delete -> requireNotNull(state.indexOfValue(task.value))
            is ListTask.Find -> 0
        }
        val before: Any = state.nodeBeforeLink(answer)?.value ?: HEAD
        val after: Any = state.nodeAfterLink(answer)?.value ?: NULL

        return Decision(
            kind = DecisionKind.CELL,
            prompt = NarrationKey(
                if (inserting) {
                    NarrationId.LIST_ASK_WHERE_BELONGS
                } else {
                    NarrationId.LIST_ASK_WHICH_LINK
                },
                listOf(value),
            ),
            options = (0 until state.linkCount).map { slot ->
                ActionOption(
                    action = ListAction.PickLink(slot),
                    label = NarrationKey(NarrationId.LIST_OPTION_LINK),
                    slot = slot,
                )
            },
            correct = ListAction.PickLink(answer),
            focus = listOf(answer),
            hint = NarrationKey(
                if (inserting) NarrationId.LIST_HINT_ORDER else NarrationId.LIST_HINT_PREDECESSOR,
                listOf(value),
            ),
            guidance = listOf(
                NarrationKey(NarrationId.LIST_RETRY_GAP_LOOK, listOf(value)),
                NarrationKey(
                    if (inserting) {
                        NarrationId.LIST_RETRY_GAP_ASK
                    } else {
                        NarrationId.LIST_RETRY_LINK_ASK
                    },
                    listOf(value),
                ),
                if (inserting) {
                    NarrationKey(NarrationId.LIST_RETRY_GAP_EXPLAIN, listOf(value, before, after))
                } else {
                    NarrationKey(NarrationId.LIST_RETRY_LINK_EXPLAIN, listOf(value, before))
                },
            ),
            minimalFeedback = NarrationKey(NarrationId.LIST_RETRY_GAP_LOOK, listOf(value)),
            correctFeedback = NarrationKey(
                if (inserting) NarrationId.LIST_GAP_OPENED else NarrationId.LIST_LINK_CHOSEN,
                listOf(value),
            ),
            hintLadder = listOf(
                NarrationKey(
                    if (inserting) {
                        NarrationId.LIST_HINT_ORDER
                    } else {
                        NarrationId.LIST_HINT_PREDECESSOR
                    },
                    listOf(value),
                ),
                NarrationKey(NarrationId.LIST_HINT_LINKS_NOT_BOXES),
            ),
        )
    }

    /**
     * "What does the new node point to?"
     *
     * The most common linked-list bug in the world is a node spliced in without its
     * own NEXT set, so the lesson asks for it out loud rather than doing it quietly.
     */
    private fun linkingDecision(state: LinkedListState, value: Int): Decision<ListAction> {
        val gap = requireNotNull(state.openLink)
        val after = state.nodeAfterLink(gap)
        val before = state.nodeBeforeLink(gap)
        val pair = listOfNotNull(value, after?.value)

        return Decision(
            kind = DecisionKind.OPTIONS,
            prompt = NarrationKey(NarrationId.LIST_ASK_POINTS_TO, listOf(value)),
            options = pointerOptions(state, correct = after, decoy = before),
            correct = ListAction.PointAt(after?.id),
            focus = listOfNotNull(state.nodes.indexOf(after).takeIf { it >= 0 }),
            hint = NarrationKey(NarrationId.LIST_HINT_INHERIT),
            guidance = listOf(
                NarrationKey(NarrationId.LIST_RETRY_POINT_LOOK, listOf(value)),
                NarrationKey(NarrationId.LIST_RETRY_POINT_ASK),
                if (after == null) {
                    NarrationKey(NarrationId.LIST_RETRY_POINT_EXPLAIN_NULL, listOf(value))
                } else {
                    NarrationKey(NarrationId.LIST_RETRY_POINT_EXPLAIN, pair)
                },
            ),
            minimalFeedback = NarrationKey(NarrationId.LIST_RETRY_POINT_LOOK, listOf(value)),
            correctFeedback = if (after == null) {
                NarrationKey(NarrationId.LIST_LINKED_TO_NULL, listOf(value))
            } else {
                NarrationKey(NarrationId.LIST_LINKED_TO, pair)
            },
            hintLadder = listOf(
                NarrationKey(NarrationId.LIST_HINT_INHERIT),
                NarrationKey(NarrationId.LIST_HINT_LINKS_NOT_BOXES),
            ),
        )
    }

    /** "Now that the node is leaving, where should the arrow point instead?" */
    private fun reconnectDecision(
        state: LinkedListState,
        task: ListTask.Delete,
    ): Decision<ListAction> {
        val gap = requireNotNull(state.openLink)
        val doomed = requireNotNull(state.nodeAfterLink(gap))
        val survivor = state.nodes.getOrNull(gap + 1)
        val before = state.nodeBeforeLink(gap)
        val pair = listOfNotNull(doomed.value, survivor?.value)

        return Decision(
            kind = DecisionKind.OPTIONS,
            prompt = if (before == null) {
                NarrationKey(NarrationId.LIST_ASK_HEAD_POINTS)
            } else {
                NarrationKey(NarrationId.LIST_ASK_REPOINT, listOf(before.value))
            },
            options = pointerOptions(state, correct = survivor, decoy = doomed),
            correct = ListAction.PointAt(survivor?.id),
            focus = listOfNotNull(state.nodes.indexOf(survivor).takeIf { it >= 0 }),
            hint = NarrationKey(NarrationId.LIST_HINT_SKIP_OVER, listOf(doomed.value)),
            guidance = listOf(
                NarrationKey(NarrationId.LIST_RETRY_REPOINT_LOOK, listOf(doomed.value)),
                NarrationKey(NarrationId.LIST_RETRY_REPOINT_ASK, listOf(doomed.value)),
                if (survivor == null) {
                    NarrationKey(NarrationId.LIST_RETRY_REPOINT_EXPLAIN_NULL, listOf(doomed.value))
                } else {
                    NarrationKey(NarrationId.LIST_RETRY_REPOINT_EXPLAIN, pair)
                },
            ),
            minimalFeedback = NarrationKey(
                NarrationId.LIST_RETRY_REPOINT_LOOK,
                listOf(doomed.value),
            ),
            whyWrong = mapOf<ListAction, NarrationKey>(
                ListAction.PointAt(doomed.id) to
                    NarrationKey(NarrationId.LIST_WHY_STILL_LINKED, listOf(doomed.value)),
            ),
            correctFeedback = if (survivor == null) {
                NarrationKey(NarrationId.LIST_REPOINTED_NULL, listOf(doomed.value))
            } else {
                NarrationKey(NarrationId.LIST_REPOINTED, pair)
            },
            hintLadder = listOf(
                NarrationKey(NarrationId.LIST_HINT_SKIP_OVER, listOf(doomed.value)),
                NarrationKey(NarrationId.LIST_HINT_LINKS_NOT_BOXES),
            ),
        )
    }

    /**
     * The answers to "where should this arrow point?": the right node, a tempting
     * wrong node, and NULL. Always offered in list order, so the position of the
     * correct answer carries no information of its own.
     */
    private fun pointerOptions(
        state: LinkedListState,
        correct: LinkedNode?,
        decoy: LinkedNode?,
    ): List<ActionOption<ListAction>> {
        val nodes = listOfNotNull(correct, decoy).distinctBy { it.id }.toMutableList()
        // Two nodes plus NULL keeps the shape of the question constant even at the
        // ends of the list, where one of the neighbours does not exist.
        if (nodes.size < 2) {
            state.nodes.firstOrNull { node -> nodes.none { it.id == node.id } }
                ?.let { nodes += it }
        }
        return nodes
            .sortedBy { state.nodes.indexOf(it) }
            .map<LinkedNode, ActionOption<ListAction>> { node ->
                ActionOption(
                    ListAction.PointAt(node.id),
                    NarrationKey(NarrationId.LIST_OPTION_NODE, listOf(node.value)),
                )
            } + ActionOption(ListAction.PointAt(null), NarrationKey(NarrationId.LIST_OPTION_NULL))
    }

    // ── Transitions ───────────────────────────────────────────────────────────

    override fun apply(
        state: LinkedListState,
        action: ListAction,
    ): Transition<LinkedListState> = when (action) {
        is ListAction.Answer -> answer(state, action.yes)
        is ListAction.PickLink -> pickLink(state, action.slot)
        is ListAction.PointAt -> pointAt(state, action.nodeId)
        ListAction.Skip -> skip(state)
    }

    private fun answer(state: LinkedListState, yes: Boolean): Transition<LinkedListState> {
        val task = state.task as? ListTask.Find ?: return refuse(state)
        val cursor = state.cursor ?: 0
        val here = state.nodes.getOrNull(cursor) ?: return refuse(state)
        val matches = here.value == task.target

        // Validation stops a wrong answer ever reaching here; the transition still
        // has to be total, so an unexpected one simply changes nothing.
        if (yes != matches) return refuse(state)

        if (matches) {
            return Transition(
                next = state.copy(found = true, phase = ListPhase.SETTLED),
                events = listOf(VizEvent.Examine(listOf(cursor), ExamineRole.CANDIDATE)),
                narration = NarrationKey(NarrationId.LIST_FOUND, listOf(here.value)),
                correct = true,
            )
        }

        val next = cursor + 1
        // Running off the end is an answer in its own right: NULL is how a linked
        // list says "it was never here".
        if (next >= state.nodes.size) {
            return Transition(
                next = state.copy(cursor = null, hitNull = true, phase = ListPhase.SETTLED),
                events = emptyList(),
                narration = NarrationKey(NarrationId.LIST_HIT_NULL, listOf(task.target)),
                correct = true,
            )
        }
        return Transition(
            next = state.copy(cursor = next, phase = ListPhase.WALKING),
            events = listOf(VizEvent.Examine(listOf(next), ExamineRole.INSPECTING)),
            narration = NarrationKey(NarrationId.LIST_FOLLOW_NEXT, listOf(here.value)),
            correct = true,
        )
    }

    private fun pickLink(state: LinkedListState, slot: Int): Transition<LinkedListState> =
        when (val task = state.task) {
            is ListTask.Insert -> {
                if (slot != state.sortedGap(task.value)) {
                    refuse(state)
                } else {
                    val node = LinkedNode(state.nextId, task.value)
                    Transition(
                        // The node exists but is *not* in the chain. Showing that
                        // moment is what stops insertion looking like a value
                        // teleporting into an array slot.
                        next = state.copy(
                            phase = ListPhase.LINKING,
                            openLink = slot,
                            detached = node,
                            changedLink = null,
                            nextId = state.nextId + 1,
                            cursor = null,
                        ),
                        events = emptyList(),
                        narration = NarrationKey(NarrationId.LIST_GAP_OPENED, listOf(task.value)),
                        correct = true,
                    )
                }
            }

            is ListTask.Delete -> {
                if (slot != state.indexOfValue(task.value)) {
                    refuse(state)
                } else {
                    Transition(
                        next = state.copy(
                            phase = ListPhase.RECONNECTING,
                            openLink = slot,
                            changedLink = null,
                            cursor = null,
                        ),
                        events = listOf(VizEvent.Examine(listOf(slot), ExamineRole.COMPARING)),
                        narration = NarrationKey(NarrationId.LIST_LINK_CHOSEN, listOf(task.value)),
                        correct = true,
                    )
                }
            }

            else -> refuse(state)
        }

    private fun pointAt(state: LinkedListState, nodeId: Int?): Transition<LinkedListState> {
        val gap = state.openLink ?: return refuse(state)
        return when (state.phase) {
            ListPhase.LINKING -> completeInsert(state, gap, nodeId)
            ListPhase.RECONNECTING -> completeDelete(state, gap, nodeId)
            else -> refuse(state)
        }
    }

    private fun completeInsert(
        state: LinkedListState,
        gap: Int,
        nodeId: Int?,
    ): Transition<LinkedListState> {
        val node = state.detached ?: return refuse(state)
        val after = state.nodeAfterLink(gap)
        if (nodeId != after?.id) return refuse(state)

        val nodes = state.nodes.toMutableList().apply { add(gap, node) }
        return Transition(
            next = advance(
                state.copy(nodes = nodes, detached = null, openLink = null, changedLink = gap),
            ),
            events = listOf(VizEvent.Insert(node.value, gap)),
            narration = if (after == null) {
                NarrationKey(NarrationId.LIST_INSERTED_AT_END, listOf(node.value))
            } else {
                NarrationKey(NarrationId.LIST_INSERTED, listOf(node.value, after.value))
            },
            correct = true,
        )
    }

    private fun completeDelete(
        state: LinkedListState,
        gap: Int,
        nodeId: Int?,
    ): Transition<LinkedListState> {
        val doomed = state.nodeAfterLink(gap) ?: return refuse(state)
        val survivor = state.nodes.getOrNull(gap + 1)
        if (nodeId != survivor?.id) return refuse(state)

        val nodes = state.nodes.filterIndexed { index, _ -> index != gap }
        return Transition(
            next = advance(state.copy(nodes = nodes, openLink = null, changedLink = gap)),
            events = listOf(VizEvent.Remove(gap)),
            narration = if (survivor == null) {
                NarrationKey(NarrationId.LIST_DELETED_LAST, listOf(doomed.value))
            } else {
                NarrationKey(NarrationId.LIST_DELETED, listOf(doomed.value, survivor.value))
            },
            correct = true,
        )
    }

    /** A task with nothing to do. It always says why. */
    private fun skip(state: LinkedListState): Transition<LinkedListState> {
        val narration = when {
            // The walk already said what happened; saying it twice is noise.
            state.found || state.hitNull -> null
            state.task is ListTask.Delete ->
                NarrationKey(NarrationId.LIST_NOT_PRESENT, listOf((state.task as ListTask.Delete).value))
            else -> NarrationKey(NarrationId.LIST_EMPTY)
        }
        return Transition(
            next = advance(state),
            events = emptyList(),
            narration = narration,
            correct = true,
        )
    }

    /** An action the state cannot accept. Nothing moves, and nothing is lost. */
    private fun refuse(state: LinkedListState) =
        Transition(state, emptyList(), null, correct = false)

    /** Move to the next task, standing the learner back at HEAD for it. */
    private fun advance(state: LinkedListState): LinkedListState {
        val next = state.at + 1
        val upcoming = state.script.getOrNull(next)
        return state.copy(
            at = next,
            done = next >= state.script.size,
            cursor = if (upcoming is ListTask.Find) 0 else null,
            phase = phaseFor(upcoming),
            detached = null,
            openLink = null,
            found = false,
            hitNull = false,
        )
    }

    private fun phaseFor(task: ListTask?) = when (task) {
        null -> ListPhase.SETTLED
        is ListTask.Find -> ListPhase.WALKING
        else -> ListPhase.CHOOSING_GAP
    }

    private companion object {
        const val HEAD = "HEAD"
        const val NULL = "NULL"
    }
}
