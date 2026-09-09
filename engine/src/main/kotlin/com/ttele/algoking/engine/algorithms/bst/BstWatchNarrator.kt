package com.ttele.algoking.engine.algorithms.bst

import com.ttele.algoking.engine.core.Frame
import com.ttele.algoking.engine.core.joinNames
import com.ttele.algoking.engine.event.Metrics
import com.ttele.algoking.engine.event.Relation
import com.ttele.algoking.engine.event.VizEvent
import com.ttele.algoking.engine.narration.NarrationId
import com.ttele.algoking.engine.narration.NarrationKey
import com.ttele.algoking.engine.scene.Scene
import com.ttele.algoking.engine.walkthrough.ComparisonReadout
import com.ttele.algoking.engine.walkthrough.PartialStep
import com.ttele.algoking.engine.walkthrough.WatchNarrator
import com.ttele.algoking.engine.walkthrough.WatchStepKind

/**
 * The BST walkthrough, one beat at a time.
 *
 * Every node is **two** steps, and the split is the pedagogy:
 *
 * 1. `COMPARE` — *"60 is greater than 50. Values greater than a node are in its
 *    RIGHT subtree."* The comparison is on screen and the search has not moved.
 * 2. `ELIMINATE` — *"Move RIGHT. 20, 30 and 40 leave the search."* Now it has.
 *
 * Collapsing those into one step would show the learner a search that has already
 * moved beside the reason it should move — the wrong order to think in, and the
 * one thing the brief was explicit about ("do not instantly jump without showing
 * the decision"). The engine emits them as two transitions, so the seam is real
 * rather than invented here.
 *
 * The elimination beat is where the lesson actually lands: the values that leave
 * are **named**, so "a comparison rules out a subtree" is a sentence about four
 * specific numbers rather than an abstraction.
 */
class BstWatchNarrator : WatchNarrator<BstState> {

    override fun opening(state: BstState, scene: Scene): List<PartialStep> = buildList {
        add(
            PartialStep(
                kind = WatchStepKind.SETUP,
                scene = scene,
                headline = NarrationKey(NarrationId.BST_WATCH_SETUP, listOf(state.target)),
                // The invariant, said once before anything happens. Every step
                // after this one is an application of it.
                support = NarrationKey(NarrationId.BST_WATCH_SETUP_SUPPORT),
            ),
        )
        // An empty tree has no root to start at, and saying "start at the root"
        // over a picture with no nodes in it would be a step that lies.
        state.current?.let { root ->
            add(
                PartialStep(
                    kind = WatchStepKind.EXAMINE,
                    scene = scene,
                    headline = NarrationKey(NarrationId.BST_WATCH_ROOT, listOf(root)),
                    support = NarrationKey(NarrationId.BST_WATCH_ROOT_SUPPORT),
                ),
            )
        }
    }

    override fun onFrame(
        previous: BstState,
        frame: Frame<BstState>,
        scene: Scene,
    ): List<PartialStep> {
        val state = frame.state

        // -- The node was read: state the comparison, and the rule it invokes ---
        val compared = frame.events.filterIsInstance<VizEvent.Compare>().firstOrNull()
        if (compared != null) {
            val node = requireNotNull(state.current)
            return listOf(
                PartialStep(
                    kind = WatchStepKind.COMPARE,
                    scene = scene,
                    headline = NarrationKey(
                        when (compared.relation) {
                            Relation.LESS -> NarrationId.BST_WATCH_COMPARE_LESS
                            Relation.GREATER -> NarrationId.BST_WATCH_COMPARE_GREATER
                            Relation.EQUAL -> NarrationId.BST_WATCH_COMPARE_EQUAL
                        },
                        listOf(state.target, node),
                    ),
                    // The rule, restated at every node. Repetition is the point:
                    // the learner should be able to say it before the app does.
                    support = NarrationKey(
                        when (compared.relation) {
                            Relation.LESS -> NarrationId.BST_WATCH_RULE_LEFT
                            Relation.GREATER -> NarrationId.BST_WATCH_RULE_RIGHT
                            Relation.EQUAL -> NarrationId.BST_WATCH_RULE_EQUAL
                        },
                    ),
                    comparison = ComparisonReadout(state.target, compared.relation, node),
                ),
            )
        }

        // -- The target was claimed --------------------------------------------
        if (state.foundValue != null && previous.foundValue == null) {
            return listOf(
                PartialStep(
                    kind = WatchStepKind.FOUND,
                    scene = scene,
                    headline = NarrationKey(
                        NarrationId.BST_WATCH_FOUND,
                        listOf(state.foundValue),
                    ),
                    support = NarrationKey(
                        NarrationId.BST_WATCH_FOUND_SUPPORT,
                        listOf(state.depth, state.eliminated.size, state.tree.size),
                    ),
                ),
            )
        }

        // -- The search moved down a level -------------------------------------
        if (state.current != previous.current || state.missing != previous.missing) {
            val from = requireNotNull(previous.current)
            val goingLeft = previous.relation == Relation.LESS
            // Named, because "a whole subtree" means nothing until it is 20, 30
            // and 40. Sorted so the sentence reads in the order the tree does.
            val dropped = (state.eliminated - previous.eliminated).sorted()

            if (state.missing) {
                return listOf(
                    PartialStep(
                        kind = WatchStepKind.NOT_FOUND,
                        scene = scene,
                        headline = NarrationKey(
                            NarrationId.BST_WATCH_NOT_FOUND,
                            listOf(state.target, from),
                        ),
                        support = NarrationKey(NarrationId.BST_WATCH_NOT_FOUND_SUPPORT),
                    ),
                )
            }

            return listOf(
                PartialStep(
                    kind = WatchStepKind.ELIMINATE,
                    scene = scene,
                    headline = NarrationKey(
                        if (goingLeft) {
                            NarrationId.BST_WATCH_MOVE_LEFT
                        } else {
                            NarrationId.BST_WATCH_MOVE_RIGHT
                        },
                    ),
                    support = if (dropped.isEmpty()) {
                        NarrationKey(NarrationId.BST_WATCH_MOVE_WHY_NONE, listOf(from))
                    } else {
                        NarrationKey(
                            NarrationId.BST_WATCH_MOVE_WHY,
                            listOf(
                                state.target,
                                from,
                                joinNames(dropped.map { it.toString() }),
                            ),
                        )
                    },
                ),
            )
        }

        // Nothing visible happened — do not manufacture a step for it.
        return emptyList()
    }

    override fun closing(
        state: BstState,
        metrics: Metrics,
        scene: Scene,
    ): List<PartialStep> = listOf(
        // The one engineered moment of the lesson, on its own step so it cannot
        // be scrolled past (PRODUCT_SPEC.md §4).
        PartialStep(
            kind = WatchStepKind.INSIGHT,
            scene = scene,
            headline = NarrationKey(NarrationId.BST_WATCH_INSIGHT),
            // Both halves of the complexity claim, together. O(log n) is a
            // property of a *balanced* tree, and a lesson that says only the
            // happy half teaches something that is not true.
            support = NarrationKey(
                NarrationId.BST_WATCH_INSIGHT_SUPPORT,
                listOf(state.eliminated.size, state.tree.size, state.depth),
            ),
        ),
        PartialStep(
            kind = WatchStepKind.SUMMARY,
            scene = scene,
            headline = if (state.foundValue != null) {
                NarrationKey(
                    NarrationId.BST_WATCH_SUMMARY,
                    listOf(state.path.joinToString("  →  ")),
                )
            } else {
                NarrationKey(
                    NarrationId.BST_WATCH_SUMMARY_NOT_FOUND,
                    listOf(state.target, state.path.joinToString("  →  ")),
                )
            },
            support = NarrationKey(NarrationId.BST_WATCH_SUMMARY_SUPPORT),
            bullets = listOf(
                NarrationKey(NarrationId.BST_IDEA_1),
                NarrationKey(NarrationId.BST_IDEA_2),
                NarrationKey(NarrationId.BST_IDEA_3),
                NarrationKey(NarrationId.BST_IDEA_4),
                // The Binary Search connection, last: it only means something to
                // a learner who has just watched the tree do the halving.
                NarrationKey(NarrationId.BST_IDEA_5),
            ),
        ),
    )
}
