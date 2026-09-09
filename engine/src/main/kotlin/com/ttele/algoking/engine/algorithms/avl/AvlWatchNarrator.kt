package com.ttele.algoking.engine.algorithms.avl

import com.ttele.algoking.engine.core.Frame
import com.ttele.algoking.engine.event.Metrics
import com.ttele.algoking.engine.narration.NarrationId
import com.ttele.algoking.engine.narration.NarrationKey
import com.ttele.algoking.engine.scene.Scene
import com.ttele.algoking.engine.walkthrough.PartialStep
import com.ttele.algoking.engine.walkthrough.WatchNarrator
import com.ttele.algoking.engine.walkthrough.WatchStepKind

/**
 * The AVL walkthrough, one beat at a time.
 *
 * The run is three insertions, and they are deliberately not alike:
 *
 * 1. **one that needs nothing.** It comes first on purpose — the same reasoning
 *    ADR-025 used to put Bubble Sort's *keep* second, before a learner can decide
 *    the algorithm swaps every pair. Nobody should leave here thinking every
 *    insert rotates.
 * 2. **one straight imbalance**, fixed by a single rotation.
 * 3. **one bent imbalance**, fixed by two — and the two are shown as *two beats*,
 *    because "a double rotation is two single rotations" is a sentence that only
 *    means something if you watch it happen twice.
 *
 * Each repair is four beats: what arrived, which node broke, which node comes up,
 * and the rotation itself. The third of those is the one Try will ask, so it gets
 * its own step rather than being folded into the rotation that follows it.
 */
class AvlWatchNarrator : WatchNarrator<AvlState> {

    override fun opening(state: AvlState, scene: Scene): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.SETUP,
            scene = scene,
            headline = NarrationKey(
                NarrationId.AVL_WATCH_SETUP,
                listOf(state.pending.joinToString(", ")),
            ),
            // The rule, said once before anything happens. Every beat after this
            // is an application of it.
            support = NarrationKey(NarrationId.AVL_WATCH_SETUP_SUPPORT),
        ),
    )

    override fun onFrame(
        previous: AvlState,
        frame: Frame<AvlState>,
        scene: Scene,
    ): List<PartialStep> {
        val state = frame.state

        // -- A value went in ---------------------------------------------------
        if (state.justInserted != null && state.justInserted != previous.justInserted) {
            val value = state.justInserted
            val broke = state.unbalanced
            return listOf(
                PartialStep(
                    kind = WatchStepKind.ADD,
                    scene = scene,
                    headline = NarrationKey(NarrationId.AVL_WATCH_INSERT, listOf(value)),
                    support = if (broke == null) {
                        // The beat that stops "every insert rotates" from ever
                        // becoming the lesson.
                        NarrationKey(NarrationId.AVL_WATCH_STILL_BALANCED)
                    } else {
                        NarrationKey(
                            NarrationId.AVL_WATCH_BROKE_IT,
                            listOf(broke, signed(state.tree.balanceFactor(broke))),
                        )
                    },
                ),
            )
        }

        // -- The unbalanced node was named -------------------------------------
        if (state.pivot != null && previous.pivot == null) {
            val pivot = state.pivot
            val shape = state.shapeAt(pivot)
            return listOf(
                PartialStep(
                    kind = WatchStepKind.EXAMINE,
                    scene = scene,
                    headline = NarrationKey(
                        NarrationId.AVL_WATCH_PIVOT,
                        listOf(pivot, signed(state.tree.balanceFactor(pivot))),
                    ),
                    // Naming which way it leans is what sets up the next beat:
                    // straight or bent decides child or grandchild.
                    support = NarrationKey(
                        if (shape?.isDouble == true) {
                            NarrationId.AVL_WATCH_SHAPE_BENT
                        } else {
                            NarrationId.AVL_WATCH_SHAPE_STRAIGHT
                        },
                        listOf(pivot),
                    ),
                ),
            )
        }

        // -- The node that comes up was named ----------------------------------
        if (state.riser != null && previous.riser == null) {
            val double = state.plan.size > 1
            return listOf(
                PartialStep(
                    kind = WatchStepKind.EXAMINE,
                    scene = scene,
                    headline = NarrationKey(
                        NarrationId.AVL_WATCH_RISER,
                        listOf(state.riser, requireNotNull(state.pivot)),
                    ),
                    support = NarrationKey(
                        if (double) {
                            NarrationId.AVL_WATCH_RISER_DOUBLE
                        } else {
                            NarrationId.AVL_WATCH_RISER_SINGLE
                        },
                    ),
                ),
            )
        }

        // -- A rotation happened -----------------------------------------------
        val rotation = state.lastRotation
        if (rotation != null && rotation != previous.lastRotation) {
            val half = state.plan.isNotEmpty()
            return listOf(
                PartialStep(
                    kind = WatchStepKind.SWAP,
                    scene = scene,
                    headline = NarrationKey(
                        when {
                            half -> NarrationId.AVL_WATCH_ROTATE_FIRST
                            rotation.kind == RotationKind.RIGHT ->
                                NarrationId.AVL_WATCH_ROTATE_RIGHT

                            else -> NarrationId.AVL_WATCH_ROTATE_LEFT
                        },
                        listOf(rotation.at),
                    ),
                    support = if (half) {
                        // Why a double is a double: the first rotation does not
                        // fix anything, it straightens the bend so the second one
                        // can be an ordinary single.
                        NarrationKey(NarrationId.AVL_WATCH_ROTATE_FIRST_WHY)
                    } else {
                        NarrationKey(
                            NarrationId.AVL_WATCH_ROTATE_DONE,
                            listOf(state.tree.height),
                        )
                    },
                ),
            )
        }

        // Nothing visible happened — do not manufacture a step for it.
        return emptyList()
    }

    override fun closing(
        state: AvlState,
        metrics: Metrics,
        scene: Scene,
    ): List<PartialStep> = listOf(
        // The one engineered moment of the lesson, on its own step so it cannot
        // be scrolled past (PRODUCT_SPEC.md §4). It is also the thing the picture
        // has been quietly demonstrating: every rotation moved nodes between
        // rows, and never between columns.
        PartialStep(
            kind = WatchStepKind.INSIGHT,
            scene = scene,
            headline = NarrationKey(NarrationId.AVL_WATCH_INSIGHT),
            support = NarrationKey(
                NarrationId.AVL_WATCH_INSIGHT_SUPPORT,
                listOf(state.tree.inorder().joinToString(", ")),
            ),
        ),
        PartialStep(
            kind = WatchStepKind.SUMMARY,
            scene = scene,
            headline = NarrationKey(
                NarrationId.AVL_WATCH_SUMMARY,
                listOf(state.rotations, state.tree.height, state.tree.size),
            ),
            support = NarrationKey(NarrationId.AVL_WATCH_SUMMARY_SUPPORT),
            bullets = listOf(
                NarrationKey(NarrationId.AVL_IDEA_1),
                NarrationKey(NarrationId.AVL_IDEA_2),
                NarrationKey(NarrationId.AVL_IDEA_3),
                NarrationKey(NarrationId.AVL_IDEA_4),
                // The Binary Search Tree connection, last: it is the caveat that
                // lesson had to end on, and this is the answer to it.
                NarrationKey(NarrationId.AVL_IDEA_5),
            ),
        ),
    )

    private fun signed(factor: Int): String = if (factor > 0) "+$factor" else "$factor"
}
