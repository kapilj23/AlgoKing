package com.ttele.algoking.engine.algorithms.linkedlist

import com.ttele.algoking.engine.core.Frame
import com.ttele.algoking.engine.event.Metrics
import com.ttele.algoking.engine.narration.NarrationId
import com.ttele.algoking.engine.narration.NarrationKey
import com.ttele.algoking.engine.scene.Scene
import com.ttele.algoking.engine.walkthrough.PartialStep
import com.ttele.algoking.engine.walkthrough.WatchNarrator
import com.ttele.algoking.engine.walkthrough.WatchPrediction
import com.ttele.algoking.engine.walkthrough.WatchStepKind

/**
 * The Linked List walkthrough.
 *
 * It opens with the part no engine transition can produce — *what a node is, what
 * HEAD is, what NULL means* — by projecting states the narrator constructs itself.
 * Those are still real engine states, just ones the algorithm would never stop at,
 * which is the only honest way to give an idea its own beat.
 *
 * After that the script takes over and the walkthrough is the operations
 * themselves: search, insert, delete.
 */
class LinkedListWatchNarrator : WatchNarrator<LinkedListState> {

    private val projector = LinkedListProjector()

    private var predictionPlaced = false

    // ── The ideas, before anything happens ────────────────────────────────────

    override fun opening(state: LinkedListState, scene: Scene): List<PartialStep> {
        val values = state.values
        val settled = state.copy(phase = ListPhase.SETTLED, cursor = null)

        fun at(cursor: Int?, phase: ListPhase = ListPhase.WALKING) =
            projector.project(settled.copy(cursor = cursor, phase = phase), emptyList())

        return buildList {
            // 1 — the shape of the thing.
            add(
                PartialStep(
                    kind = WatchStepKind.SETUP,
                    scene = at(null, ListPhase.SETTLED),
                    headline = NarrationKey(NarrationId.LIST_WATCH_MEET),
                    support = NarrationKey(NarrationId.LIST_WATCH_CHAIN),
                ),
            )

            // 2 — one node, on its own.
            add(
                PartialStep(
                    kind = WatchStepKind.EXAMINE,
                    scene = at(1),
                    headline = NarrationKey(
                        NarrationId.LIST_WATCH_NODE,
                        listOfNotNull(values.getOrNull(1)),
                    ),
                    support = NarrationKey(NarrationId.LIST_WATCH_NODE_SUPPORT),
                ),
            )
            add(
                PartialStep(
                    kind = WatchStepKind.EXAMINE,
                    scene = at(null, ListPhase.SETTLED),
                    headline = NarrationKey(NarrationId.LIST_WATCH_LINKS_CONNECT),
                ),
            )

            // 3 — HEAD, the only way in.
            add(
                PartialStep(
                    kind = WatchStepKind.EXAMINE,
                    scene = at(0),
                    headline = NarrationKey(NarrationId.LIST_WATCH_HEAD),
                    support = NarrationKey(NarrationId.LIST_WATCH_HEAD_SUPPORT),
                ),
            )

            // 4 — walking to NULL.
            add(
                PartialStep(
                    kind = WatchStepKind.EXAMINE,
                    scene = at(0),
                    headline = NarrationKey(
                        NarrationId.LIST_WATCH_START_AT_HEAD,
                        listOfNotNull(values.firstOrNull()),
                    ),
                ),
            )
            for (index in 1 until values.size) {
                add(
                    PartialStep(
                        kind = WatchStepKind.EXAMINE,
                        scene = at(index),
                        headline = NarrationKey(
                            if (index == 1) {
                                NarrationId.LIST_WATCH_FOLLOW
                            } else {
                                NarrationId.LIST_WATCH_KEEP_FOLLOWING
                            },
                            listOf(values[index]),
                        ),
                    ),
                )
            }
            add(
                PartialStep(
                    kind = WatchStepKind.NOT_FOUND,
                    scene = at(null, ListPhase.SETTLED),
                    headline = NarrationKey(NarrationId.LIST_WATCH_NULL_MEANS),
                ),
            )

            // 5 — and now the same walk, with a reason.
            add(
                PartialStep(
                    kind = WatchStepKind.SETUP,
                    scene = projector.project(state, emptyList()),
                    headline = NarrationKey(NarrationId.LIST_WATCH_SEARCH_INTRO, targetArg(state)),
                ),
            )
        }
    }

    private fun targetArg(state: LinkedListState): List<Any> =
        listOfNotNull((state.script.firstOrNull() as? ListTask.Find)?.target)

    // ── The operations ────────────────────────────────────────────────────────

    override fun onFrame(
        previous: LinkedListState,
        frame: Frame<LinkedListState>,
        scene: Scene,
    ): List<PartialStep> = when (previous.task) {
        is ListTask.Find -> findStep(previous, frame, scene)
        is ListTask.Insert -> insertStep(previous, frame, scene)
        is ListTask.Delete -> deleteStep(previous, frame, scene)
        null -> emptyList()
    }

    private fun findStep(
        previous: LinkedListState,
        frame: Frame<LinkedListState>,
        scene: Scene,
    ): List<PartialStep> {
        val task = previous.task as ListTask.Find
        // The frame that merely closes the walk has nothing new to say.
        if (previous.found || previous.hitNull) return emptyList()
        val here = previous.nodes.getOrNull(previous.cursor ?: 0) ?: return emptyList()
        val matched = here.value == task.target

        val step = PartialStep(
            kind = if (matched) WatchStepKind.FOUND else WatchStepKind.EXAMINE,
            scene = scene,
            headline = NarrationKey(
                if (matched) NarrationId.LIST_WATCH_FOUND else NarrationId.LIST_WATCH_NOT_IT,
                listOf(here.value, task.target),
            ),
            support = if (matched) {
                NarrationKey(NarrationId.LIST_WATCH_SEARCH_SUMMARY)
            } else {
                null
            },
        )
        if (!matched) return listOf(step)

        // The checkpoint lands the moment searching is understood and before the
        // link surgery starts — the one idea the learner must already hold.
        return listOf(step) + listOfNotNull(checkpoint(frame.state))
    }

    /**
     * One unscored checkpoint: *if we remove a node, what does the one before it
     * point to?* It is the whole of insertion and deletion in a single question.
     */
    private fun checkpoint(state: LinkedListState): PartialStep? {
        if (predictionPlaced || state.nodes.size < 3) return null
        predictionPlaced = true

        val values = state.values
        val doomed = values[1]
        val previous = values[0]
        val survivor = values[2]

        return PartialStep(
            kind = WatchStepKind.PREDICT,
            scene = projector.project(
                state.copy(cursor = null, phase = ListPhase.SETTLED),
                emptyList(),
            ),
            headline = NarrationKey(NarrationId.LIST_PREDICT_PROMPT, listOf(doomed, previous)),
            prediction = WatchPrediction(
                prompt = NarrationKey(NarrationId.LIST_PREDICT_PROMPT, listOf(doomed, previous)),
                options = listOf(
                    NarrationKey(NarrationId.LIST_OPTION_NULL),
                    NarrationKey(NarrationId.LIST_OPTION_NODE, listOf(doomed)),
                    NarrationKey(NarrationId.LIST_OPTION_NODE, listOf(survivor)),
                ),
                correctIndex = 2,
                whenRight = NarrationKey(NarrationId.LIST_PREDICT_RIGHT, listOf(previous, survivor)),
                whenWrong = NarrationKey(NarrationId.LIST_PREDICT_WRONG, listOf(doomed, survivor)),
            ),
        )
    }

    private fun insertStep(
        previous: LinkedListState,
        frame: Frame<LinkedListState>,
        scene: Scene,
    ): List<PartialStep> {
        val task = previous.task as ListTask.Insert
        return when (previous.phase) {
            // The node is in. Name what it now points at, because that link is the
            // half of insertion everybody forgets.
            ListPhase.LINKING -> {
                val gap = previous.openLink
                val after = gap?.let { frame.state.nodes.getOrNull(it + 1) }
                listOf(
                    PartialStep(
                        kind = WatchStepKind.ADD,
                        scene = scene,
                        headline = NarrationKey(
                            NarrationId.LIST_WATCH_CONNECTED,
                            listOfNotNull(task.value, after?.value),
                        ),
                    ),
                )
            }

            // Two beats, not one. A node that appears already spliced in looks like
            // a value dropped into an array slot, which is exactly the wrong idea.
            else -> listOf(
                PartialStep(
                    kind = WatchStepKind.ADD,
                    scene = projector.project(
                        previous.copy(phase = ListPhase.SETTLED, cursor = null),
                        emptyList(),
                    ),
                    headline = NarrationKey(NarrationId.LIST_WATCH_INSERT_INTRO, listOf(task.value)),
                ),
                PartialStep(
                    kind = WatchStepKind.ADD,
                    scene = scene,
                    headline = NarrationKey(NarrationId.LIST_WATCH_NEW_NODE, listOf(task.value)),
                ),
            )
        }
    }

    private fun deleteStep(
        previous: LinkedListState,
        frame: Frame<LinkedListState>,
        scene: Scene,
    ): List<PartialStep> {
        val task = previous.task as ListTask.Delete
        return when (previous.phase) {
            ListPhase.RECONNECTING -> listOf(
                PartialStep(
                    kind = WatchStepKind.REMOVE,
                    scene = scene,
                    headline = NarrationKey(NarrationId.LIST_WATCH_GONE, listOf(task.value)),
                ),
            )

            // The cut is its own beat: first the list as it stands, then the link
            // hanging open. A node that simply vanishes teaches nothing about why
            // deletion is a *link* operation.
            else -> listOf(
                PartialStep(
                    kind = WatchStepKind.REMOVE,
                    scene = projector.project(
                        previous.copy(phase = ListPhase.SETTLED, cursor = null),
                        emptyList(),
                    ),
                    headline = NarrationKey(NarrationId.LIST_WATCH_DELETE_INTRO, listOf(task.value)),
                    support = NarrationKey(NarrationId.LIST_WATCH_UNLINK),
                ),
                PartialStep(
                    kind = WatchStepKind.REMOVE,
                    scene = scene,
                    headline = NarrationKey(
                        NarrationId.LIST_LINK_CHOSEN,
                        listOfNotNull(task.value, previous.nodeBeforeLink(
                            previous.indexOfValue(task.value) ?: 0,
                        )?.value),
                    ),
                ),
            )
        }
    }

    // ── The two ideas worth keeping ───────────────────────────────────────────

    override fun closing(
        state: LinkedListState,
        metrics: Metrics,
        scene: Scene,
    ) = listOf(
        PartialStep(
            kind = WatchStepKind.INSIGHT,
            scene = scene,
            headline = NarrationKey(NarrationId.LIST_WATCH_INSIGHT),
            support = NarrationKey(NarrationId.LIST_WATCH_INSIGHT_SUPPORT),
        ),
        // The comparison that makes the whole structure make sense.
        PartialStep(
            kind = WatchStepKind.INSIGHT,
            scene = scene,
            headline = NarrationKey(NarrationId.LIST_WATCH_VS_ARRAY),
            support = NarrationKey(NarrationId.LIST_WATCH_VS_ARRAY_SUPPORT),
        ),
        PartialStep(
            kind = WatchStepKind.SUMMARY,
            scene = scene,
            headline = NarrationKey(NarrationId.LIST_WATCH_SUMMARY, listOf(state.nodes.size)),
            support = NarrationKey(NarrationId.LIST_WATCH_SUMMARY_SUPPORT),
            bullets = listOf(
                NarrationKey(NarrationId.LIST_IDEA_1),
                NarrationKey(NarrationId.LIST_IDEA_2),
                NarrationKey(NarrationId.LIST_IDEA_3),
            ),
        ),
    )
}
