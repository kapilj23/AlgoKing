package com.ttele.algoking.engine.algorithms.mergesort

import com.ttele.algoking.engine.core.Algorithm
import com.ttele.algoking.engine.core.AlgorithmId
import com.ttele.algoking.engine.core.Dataset
import com.ttele.algoking.engine.core.Probe
import com.ttele.algoking.engine.core.Transition
import com.ttele.algoking.engine.decision.Action
import com.ttele.algoking.engine.decision.ActionOption
import com.ttele.algoking.engine.decision.Decision
import com.ttele.algoking.engine.decision.DecisionKind
import com.ttele.algoking.engine.event.ExamineRole
import com.ttele.algoking.engine.event.MarkId
import com.ttele.algoking.engine.event.Outcome
import com.ttele.algoking.engine.event.RegionId
import com.ttele.algoking.engine.event.Relation
import com.ttele.algoking.engine.event.VizEvent
import com.ttele.algoking.engine.narration.NarrationId
import com.ttele.algoking.engine.narration.NarrationKey

/**
 * What the learner can do in Merge Sort — the two halves of divide and conquer.
 *
 *  1. **Where does it split?** — asked once, for the whole array. Deeper splits
 *     are mechanical; making the learner tap every recursive boundary would test
 *     patience, not understanding.
 *  2. **Which value comes next?** — the merge decision, asked at every step where
 *     both runs still have a front value. This is the one that has to be earned:
 *     it is *why* merging works.
 */
sealed interface MergeSortAction : Action {

    /** Halve the current grouping. [at] is the boundary the learner picked. */
    data class SplitAt(val at: Int) : MergeSortAction

    /** Keep halving without asking — the deeper levels are the same idea. */
    data object DivideAgain : MergeSortAction

    /** Every piece is a single value; start combining them. */
    data object BeginMerge : MergeSortAction

    /** Take the front of the left run. */
    data object TakeLeft : MergeSortAction

    /** Take the front of the right run. */
    data object TakeRight : MergeSortAction

    /** One merge is finished; move to the next pair, or the next level. */
    data object NextMerge : MergeSortAction
}

enum class MergePhase { DIVIDING, MERGING, DONE }

/**
 * Bottom-up merge sort, which is the same hierarchy the learner is shown read
 * upwards — and it renders as one flat row with group boundaries rather than
 * needing a second kind of screen.
 *
 * ```
 * 8 3 6 2 7 1 5 4      divide  (groupWidth 8 → 4 → 2 → 1)
 * 3 8 | 2 6 | 1 7 | 4 5    merge runWidth 1
 * 2 3 6 8 | 1 4 5 7        merge runWidth 2
 * 1 2 3 4 5 6 7 8          merge runWidth 4
 * ```
 */
data class MergeSortState(
    val values: List<Int>,
    val phase: MergePhase,
    /** DIVIDING: how wide each group currently is. */
    val groupWidth: Int,
    /** MERGING: how wide each already-sorted run is. */
    val runWidth: Int,
    /** MERGING: the window being merged, `lo until hi`, split at `mid`. */
    val lo: Int,
    val mid: Int,
    val hi: Int,
    /** Snapshots of the two runs, so writing the output cannot clobber them. */
    val left: List<Int>,
    val right: List<Int>,
    val leftAt: Int,
    val rightAt: Int,
    /** How many values of this merge have been placed. */
    val taken: Int,
    val done: Boolean,
) {
    val leftFront: Int? get() = left.getOrNull(leftAt)
    val rightFront: Int? get() = right.getOrNull(rightAt)
    val merging: Boolean get() = phase == MergePhase.MERGING && hi > lo

    /** Where the next merged value lands — also the display slot of the left front. */
    val writeAt: Int get() = lo + taken

    /** Display slot of the left run-s front value. */
    val leftSlot: Int get() = writeAt

    /** Display slot of the right run-s front value. */
    val rightSlot: Int get() = writeAt + (left.size - leftAt)

    /** The groups the sequence is currently divided into. */
    fun groups(): List<IntRange> {
        val width = when (phase) {
            MergePhase.DIVIDING -> groupWidth
            MergePhase.MERGING -> runWidth
            MergePhase.DONE -> values.size
        }
        if (width >= values.size) return listOf(values.indices)
        return values.indices.step(width).map { start ->
            start until minOf(start + width, values.size)
        }
    }
}

class MergeSortAlgorithm : Algorithm<MergeSortState, MergeSortAction> {

    override val id = AlgorithmId.MERGE_SORT

    override fun initial(dataset: Dataset) = MergeSortState(
        values = dataset.values,
        phase = if (dataset.values.size <= 1) MergePhase.DONE else MergePhase.DIVIDING,
        groupWidth = dataset.values.size,
        runWidth = 1,
        lo = 0,
        mid = 0,
        hi = 0,
        left = emptyList(),
        right = emptyList(),
        leftAt = 0,
        rightAt = 0,
        taken = 0,
        done = dataset.values.size <= 1,
    )

    override fun probe(state: MergeSortState): Probe<MergeSortAction> {
        if (state.done) return Probe.Terminal(Outcome.Sorted)

        if (state.phase == MergePhase.DIVIDING) {
            // The very first split is the learner's; the rest is the same idea again.
            if (state.groupWidth == state.values.size) {
                return Probe.Decide(splitDecision(state))
            }
            return Probe.Mechanical(
                if (state.groupWidth > 1) {
                    MergeSortAction.DivideAgain
                } else {
                    MergeSortAction.BeginMerge
                },
            )
        }

        // A merge with nothing left to compare is bookkeeping, not a judgement.
        if (!state.merging || state.taken >= state.left.size + state.right.size) {
            return Probe.Mechanical(MergeSortAction.NextMerge)
        }
        val leftFront = state.leftFront
        val rightFront = state.rightFront
        if (leftFront == null) return Probe.Mechanical(MergeSortAction.TakeRight)
        if (rightFront == null) return Probe.Mechanical(MergeSortAction.TakeLeft)

        return Probe.Decide(mergeDecision(state, leftFront, rightFront))
    }

    // ── Decisions ─────────────────────────────────────────────────────────────

    private fun splitDecision(state: MergeSortState): Decision<MergeSortAction> {
        val middle = state.values.size / 2
        return Decision(
            kind = DecisionKind.CELL,
            prompt = NarrationKey(NarrationId.MERGE_ASK_SPLIT),
            // Any boundary is offerable; only the halfway one is right.
            options = (1 until state.values.size).map { at ->
                ActionOption(
                    action = MergeSortAction.SplitAt(at),
                    label = NarrationKey(NarrationId.MERGE_OPTION_SPLIT),
                    slot = at,
                )
            },
            correct = MergeSortAction.SplitAt(middle),
            focus = listOf(middle),
            hint = NarrationKey(NarrationId.MERGE_HINT_SPLIT),
            guidance = listOf(
                NarrationKey(NarrationId.MERGE_RETRY_SPLIT_LOOK),
                NarrationKey(NarrationId.MERGE_RETRY_SPLIT_ASK, listOf(state.values.size)),
                NarrationKey(NarrationId.MERGE_RETRY_SPLIT_EXPLAIN, listOf(middle)),
            ),
            minimalFeedback = NarrationKey(NarrationId.MERGE_RETRY_SPLIT_LOOK),
            correctFeedback = NarrationKey(NarrationId.MERGE_SPLIT_DONE),
            hintLadder = listOf(
                NarrationKey(NarrationId.MERGE_HINT_SPLIT),
                NarrationKey(NarrationId.MERGE_RETRY_SPLIT_ASK, listOf(state.values.size)),
                NarrationKey(NarrationId.MERGE_RETRY_SPLIT_EXPLAIN, listOf(middle)),
            ),
        )
    }

    private fun mergeDecision(
        state: MergeSortState,
        leftFront: Int,
        rightFront: Int,
    ): Decision<MergeSortAction> {
        val takeLeft = leftFront <= rightFront
        val correct = if (takeLeft) MergeSortAction.TakeLeft else MergeSortAction.TakeRight
        val args = listOf(leftFront, rightFront)

        return Decision(
            kind = DecisionKind.OPTIONS,
            prompt = NarrationKey(
                when {
                    state.taken == 0 && state.runWidth == 1 -> NarrationId.MERGE_ASK_FIRST
                    state.runWidth == 1 -> NarrationId.MERGE_ASK_NEXT
                    else -> NarrationId.MERGE_ASK_YOUR_MOVE
                },
                args,
            ),
            // Labelled by value, so the learner picks a number and not a side.
            options = listOf(
                ActionOption(
                    MergeSortAction.TakeLeft,
                    NarrationKey(NarrationId.MERGE_VALUE, listOf(leftFront)),
                ),
                ActionOption(
                    MergeSortAction.TakeRight,
                    NarrationKey(NarrationId.MERGE_VALUE, listOf(rightFront)),
                ),
            ),
            correct = correct,
            focus = listOf(state.leftSlot, state.rightSlot),
            hint = NarrationKey(NarrationId.MERGE_HINT_FRONTS, args),
            guidance = listOf(
                NarrationKey(NarrationId.MERGE_RETRY_LOOK, args),
                NarrationKey(NarrationId.MERGE_RETRY_ASK, args),
                NarrationKey(
                    NarrationId.MERGE_RETRY_EXPLAIN,
                    listOf(minOf(leftFront, rightFront), maxOf(leftFront, rightFront)),
                ),
            ),
            minimalFeedback = NarrationKey(NarrationId.MERGE_COMPARE, args),
            whyWrong = mapOf(
                (if (takeLeft) MergeSortAction.TakeRight else MergeSortAction.TakeLeft) to
                    NarrationKey(
                        NarrationId.MERGE_WHY_WRONG,
                        listOf(
                            maxOf(leftFront, rightFront),
                            minOf(leftFront, rightFront),
                        ),
                    ),
            ),
            correctFeedback = NarrationKey(
                NarrationId.MERGE_TOOK,
                listOf(minOf(leftFront, rightFront), maxOf(leftFront, rightFront)),
            ),
            hintLadder = listOf(
                NarrationKey(NarrationId.MERGE_HINT_FRONTS, args),
                NarrationKey(NarrationId.MERGE_HINT_RULE),
                NarrationKey(
                    NarrationId.MERGE_RETRY_EXPLAIN,
                    listOf(minOf(leftFront, rightFront), maxOf(leftFront, rightFront)),
                ),
            ),
        )
    }

    // ── Transitions ───────────────────────────────────────────────────────────

    override fun apply(
        state: MergeSortState,
        action: MergeSortAction,
    ): Transition<MergeSortState> = when (action) {
        is MergeSortAction.SplitAt -> split(state, action.at)
        MergeSortAction.DivideAgain -> divideAgain(state)
        MergeSortAction.BeginMerge -> beginMerge(state)
        MergeSortAction.TakeLeft -> take(state, fromLeft = true)
        MergeSortAction.TakeRight -> take(state, fromLeft = false)
        MergeSortAction.NextMerge -> nextMerge(state)
    }

    /** Dividing moves no data — it only changes where the boundaries are. */
    private fun split(state: MergeSortState, at: Int): Transition<MergeSortState> {
        val width = maxOf(1, at)
        return Transition(
            next = state.copy(groupWidth = width),
            events = listOf(VizEvent.Region(0 until at, RegionId.SEARCH_SPACE)),
            narration = NarrationKey(NarrationId.MERGE_SPLIT_DONE),
            correct = at == state.values.size / 2,
        )
    }

    private fun divideAgain(state: MergeSortState): Transition<MergeSortState> {
        val width = maxOf(1, (state.groupWidth + 1) / 2)
        return Transition(
            next = state.copy(groupWidth = width),
            events = listOf(VizEvent.Region(0 until width, RegionId.SEARCH_SPACE)),
            narration = NarrationKey(
                if (width == 1) NarrationId.MERGE_DIVIDED_TO_ONE else NarrationId.MERGE_DIVIDE_AGAIN,
            ),
            correct = true,
        )
    }

    private fun beginMerge(state: MergeSortState): Transition<MergeSortState> =
        Transition(
            next = openMerge(state.copy(phase = MergePhase.MERGING, runWidth = 1), 0, 1),
            events = listOf(VizEvent.Mark(null, MarkId.BEST)),
            narration = NarrationKey(NarrationId.MERGE_BASE_CASE),
            correct = true,
        )

    /** Sets up the merge window starting at [lo] for run width [width]. */
    private fun openMerge(state: MergeSortState, lo: Int, width: Int): MergeSortState {
        val mid = minOf(lo + width, state.values.size)
        val hi = minOf(lo + width * 2, state.values.size)
        return state.copy(
            runWidth = width,
            lo = lo,
            mid = mid,
            hi = hi,
            left = state.values.subList(lo, mid).toList(),
            right = state.values.subList(mid, hi).toList(),
            leftAt = 0,
            rightAt = 0,
            taken = 0,
        )
    }

    /**
     * Take the smaller front value. The window is rewritten as
     * `output ++ remaining left ++ remaining right`, so the display always shows a
     * merged prefix growing while both runs shrink.
     */
    private fun take(state: MergeSortState, fromLeft: Boolean): Transition<MergeSortState> {
        val leftFront = state.leftFront
        val rightFront = state.rightFront
        val shouldTakeLeft = when {
            leftFront == null -> false
            rightFront == null -> true
            else -> leftFront <= rightFront
        }
        val value = if (fromLeft) leftFront else rightFront
        if (value == null) return Transition(state, emptyList(), null, correct = false)

        val leftAt = state.leftAt + if (fromLeft) 1 else 0
        val rightAt = state.rightAt + if (fromLeft) 0 else 1
        val taken = state.taken + 1

        val output = state.values.subList(state.lo, state.lo + state.taken).toList() + value
        val window = output +
            state.left.drop(leftAt) +
            state.right.drop(rightAt)

        val values = state.values.toMutableList().also {
            window.forEachIndexed { offset, v -> it[state.lo + offset] = v }
        }

        return Transition(
            next = state.copy(
                values = values,
                leftAt = leftAt,
                rightAt = rightAt,
                taken = taken,
            ),
            events = buildList {
                add(
                    VizEvent.Compare(
                        state.leftSlot,
                        state.rightSlot,
                        if (shouldTakeLeft) Relation.LESS else Relation.GREATER,
                    ),
                )
                add(VizEvent.Insert(value, state.writeAt))
                add(VizEvent.Examine(listOf(state.writeAt), ExamineRole.CANDIDATE))
            },
            narration = NarrationKey(NarrationId.MERGE_TOOK_VALUE, listOf(value)),
            correct = fromLeft == shouldTakeLeft,
        )
    }

    /** Advance to the next pair at this width, or double the width and start again. */
    private fun nextMerge(state: MergeSortState): Transition<MergeSortState> {
        val finished = state.hi..state.values.lastIndex
        val nextLo = state.hi

        // Another pair at the current width?
        if (nextLo + state.runWidth < state.values.size) {
            return Transition(
                next = openMerge(state, nextLo, state.runWidth),
                events = listOf(VizEvent.Region(nextLo until state.values.size, RegionId.SEARCH_SPACE)),
                narration = NarrationKey(NarrationId.MERGE_NEXT_PAIR),
                correct = true,
            )
        }

        // This level is done — double the run width.
        val nextWidth = state.runWidth * 2
        if (nextWidth >= state.values.size) {
            return Transition(
                next = state.copy(phase = MergePhase.DONE, runWidth = state.values.size, done = true),
                events = listOf(
                    VizEvent.Finalize(state.values.indices),
                    VizEvent.Terminal(Outcome.Sorted),
                ),
                narration = NarrationKey(NarrationId.MERGE_SORTED),
                correct = true,
            )
        }

        return Transition(
            next = openMerge(state, 0, nextWidth),
            events = listOf(VizEvent.Region(0 until nextWidth * 2, RegionId.SEARCH_SPACE)),
            narration = NarrationKey(NarrationId.MERGE_LEVEL_DONE, listOf(nextWidth)),
            correct = true,
        ).also { _ -> finished }
    }
}
