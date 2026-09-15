package com.algorithms.algoking.engine.algorithms.fibonacci

import com.algorithms.algoking.engine.core.Frame
import com.algorithms.algoking.engine.event.Metrics
import com.algorithms.algoking.engine.event.VizEvent
import com.algorithms.algoking.engine.narration.NarrationId
import com.algorithms.algoking.engine.narration.NarrationKey
import com.algorithms.algoking.engine.scene.Scene
import com.algorithms.algoking.engine.walkthrough.PartialStep
import com.algorithms.algoking.engine.walkthrough.WatchNarrator
import com.algorithms.algoking.engine.walkthrough.WatchStepKind

/**
 * The Fibonacci walkthrough — the problem, the trap, and the fix.
 *
 * ### The shape
 *
 * **Opening.** Four beats that have to land before a single cell is filled: what
 * the sequence is, the base cases and the recurrence, *what goes wrong if you run
 * that recurrence as written*, and the two ways out of it. The third is the one
 * the lesson exists for — a learner who never sees the repeated work has no reason
 * to care that there is a table.
 *
 * Four rather than six, because these beats all show the same picture: the table
 * is still empty, so the copy is the only thing that changes between them. ADR-020
 * says a step where nothing changed is a bug, and a conceptual run-up earns its
 * screens only while each one is a genuinely separate idea. The recurrence rides
 * with the base cases, and the table's shape rides with the fix that builds it.
 *
 * **Build.** The first three entries in full, the middle collapsed to one beat,
 * and the last in full. That is ADR-025's rule: narrate the smallest prefix that
 * builds the model, then stop. Seven identical additions narrated one at a time is
 * a slideshow, and by `dp[5]` the learner has either got it or is not going to get
 * it from a sixth repetition.
 *
 * It is also what keeps TRY from being a replay: the entries WATCH collapses are
 * entries the learner has never been walked through.
 *
 * **Closing.** The insight, then the recap — and the complexity lands there rather
 * than up front, because O(2ⁿ) against O(n) means nothing until you have watched
 * the table produce the same answer in nine steps.
 */
class FibonacciWatchNarrator : WatchNarrator<FibonacciState> {

    override fun opening(state: FibonacciState, scene: Scene): List<PartialStep> {
        val n = state.n
        return listOf(
            PartialStep(
                kind = WatchStepKind.SETUP,
                scene = scene,
                headline = NarrationKey(NarrationId.FIB_WATCH_SETUP, listOf(n)),
                support = NarrationKey(NarrationId.FIB_WATCH_SETUP_SUPPORT),
            ),
            PartialStep(
                kind = WatchStepKind.EXAMINE,
                scene = scene,
                // The base cases are stated, never asked for: they are the
                // definition of the sequence, and every value after them leans on
                // them (PRODUCT_SPEC.md §3). The recurrence rides with them
                // because the three lines are one idea, and splitting them would
                // buy a second beat that shows the identical picture.
                headline = NarrationKey(NarrationId.FIB_WATCH_BASE),
                support = NarrationKey(NarrationId.FIB_WATCH_BASE_SUPPORT),
            ),
            // The trap. Everything after this is the answer to it, so it has its
            // own beat and its numbers are computed rather than claimed.
            PartialStep(
                kind = WatchStepKind.EXAMINE,
                scene = scene,
                headline = NarrationKey(
                    NarrationId.FIB_WATCH_NAIVE,
                    listOf(n, state.naiveCalls),
                ),
                support = NarrationKey(
                    NarrationId.FIB_WATCH_NAIVE_SUPPORT,
                    listOf(REPEATED_EXAMPLE, state.naiveRecomputesOf(REPEATED_EXAMPLE)),
                ),
            ),
            PartialStep(
                kind = WatchStepKind.EXAMINE,
                scene = scene,
                // Both shapes of dynamic programming, named, before the lesson
                // commits to one. A learner who only ever meets tabulation will
                // not recognise memoization when they meet it written down.
                headline = NarrationKey(NarrationId.FIB_WATCH_TWO_FIXES),
                // ...and the table this lesson is about to build, named here
                // rather than in a beat of its own: it is the second half of the
                // same sentence, and the picture does not change between them.
                support = NarrationKey(
                    NarrationId.FIB_WATCH_TWO_FIXES_SUPPORT,
                    listOf(n + 1),
                ),
            ),
        )
    }

    override fun onFrame(
        previous: FibonacciState,
        frame: Frame<FibonacciState>,
        scene: Scene,
    ): List<PartialStep> {
        val inserted = frame.events
            .filterIsInstance<VizEvent.Insert>()
            .firstOrNull()
            ?: return emptyList()

        val state = frame.state
        val i = inserted.at
        val a = previous.dp[i - 1]
        val b = previous.dp[i - 2]
        val plan = BuildPlan(state.n)

        return when {
            // The last entry always gets its own beat: it is the answer.
            i == state.n -> listOf(
                PartialStep(
                    kind = WatchStepKind.FOUND,
                    scene = scene,
                    headline = NarrationKey(
                        NarrationId.FIB_WATCH_FINAL,
                        listOf(i, a, b, inserted.value),
                    ),
                    support = NarrationKey(
                        NarrationId.FIB_WATCH_FINAL_SUPPORT,
                        listOf(state.n, inserted.value, state.n + 1),
                    ),
                ),
            )

            // The opening entries, in full — this is where the model is built.
            i in plan.detailed -> listOf(
                PartialStep(
                    kind = WatchStepKind.EXAMINE,
                    scene = scene,
                    headline = NarrationKey(
                        NarrationId.FIB_WATCH_BUILD,
                        listOf(i, a, b, inserted.value),
                    ),
                    support = NarrationKey(
                        NarrationId.FIB_WATCH_BUILD_SUPPORT,
                        listOf(i, i - 1, i - 2),
                    ),
                ),
            )

            // The middle, collapsed into one beat at its last index — by which
            // point every value it covers is on screen to be read.
            i == plan.collapsedAt -> listOf(
                PartialStep(
                    kind = WatchStepKind.PASS_COMPLETE,
                    scene = scene,
                    headline = NarrationKey(
                        NarrationId.FIB_WATCH_COLLAPSED,
                        listOf(
                            plan.collapsed.first,
                            plan.collapsed.last,
                            plan.collapsed.joinToString(", ") { state.dp[it].toString() },
                        ),
                    ),
                    support = NarrationKey(NarrationId.FIB_WATCH_COLLAPSED_SUPPORT),
                ),
            )

            // Covered by the collapsed beat. A step where nothing new is said is a
            // step that should not exist (ADR-020).
            else -> emptyList()
        }
    }

    override fun closing(
        state: FibonacciState,
        metrics: Metrics,
        scene: Scene,
    ): List<PartialStep> = listOf(
        PartialStep(
            kind = WatchStepKind.INSIGHT,
            scene = scene,
            headline = NarrationKey(NarrationId.FIB_WATCH_INSIGHT),
            support = NarrationKey(
                NarrationId.FIB_WATCH_INSIGHT_SUPPORT,
                listOf(state.naiveCalls, state.n + 1, state.n),
            ),
        ),
        PartialStep(
            kind = WatchStepKind.SUMMARY,
            scene = scene,
            headline = NarrationKey(
                NarrationId.FIB_WATCH_SUMMARY,
                listOf(state.n, state.answer),
            ),
            support = NarrationKey(NarrationId.FIB_WATCH_SUMMARY_SUPPORT),
            bullets = listOf(
                NarrationKey(NarrationId.FIB_IDEA_1),
                NarrationKey(NarrationId.FIB_IDEA_2),
                NarrationKey(NarrationId.FIB_IDEA_3),
                NarrationKey(NarrationId.FIB_IDEA_4),
                NarrationKey(NarrationId.FIB_IDEA_5),
            ),
        ),
    )

    private companion object {
        /**
         * The index whose recomputation count is quoted in the "repeated work"
         * beat. Low enough that naive recursion reaches it from many directions —
         * which is the whole point — and present in every table this lesson can
         * be given, because a dataset below it has no repeated work to show.
         */
        const val REPEATED_EXAMPLE = 3
    }
}

/**
 * Which entries the walkthrough narrates in full, and which it collapses.
 *
 * Stated as a rule over `n` rather than as indices for one dataset, so a lesson
 * given a different target behaves sensibly instead of silently narrating every
 * step or none of them.
 *
 * For `n = 8`: `dp[2] dp[3] dp[4]` in full, `dp[5] dp[6] dp[7]` as one beat, and
 * `dp[8]` in full.
 */
internal class BuildPlan(n: Int) {

    /** The opening entries, narrated one at a time. */
    val detailed: IntRange = 2..minOf(2 + DETAILED_COUNT - 1, n - 1)

    /** The entries folded into a single beat. Empty when there are none. */
    val collapsed: IntRange = (detailed.last + 1) until n

    /** The index whose frame carries the collapsed beat, or null if there is none. */
    val collapsedAt: Int? = collapsed.lastOrNull()

    private companion object {
        /**
         * Three is enough to establish the rhythm — the first entry shows the two
         * base cases combining, the second shows the window sliding, and the third
         * shows it is not a coincidence. A fourth says nothing new.
         */
        const val DETAILED_COUNT = 3
    }
}
