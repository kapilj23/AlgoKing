package com.ttele.algoking.feature.complete

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ttele.algoking.engine.core.AlgorithmId
import com.ttele.algoking.engine.event.Metrics
import com.ttele.algoking.ui.components.AlgoCard
import com.ttele.algoking.ui.components.AlgoHeader
import com.ttele.algoking.ui.components.AlgoScreen
import com.ttele.algoking.ui.components.CategoryBadge
import com.ttele.algoking.ui.components.Gap
import com.ttele.algoking.ui.components.HeaderTitle
import com.ttele.algoking.ui.components.IconTileButton
import com.ttele.algoking.ui.components.MascotKing
import com.ttele.algoking.ui.components.Metric
import com.ttele.algoking.ui.components.MetricRow
import com.ttele.algoking.ui.components.PrimaryButton
import com.ttele.algoking.ui.components.SecondaryButton
import com.ttele.algoking.ui.icons.AlgoIcons
import com.ttele.algoking.ui.theme.AlgoAccent
import com.ttele.algoking.ui.theme.AlgoColors
import com.ttele.algoking.ui.theme.AlgoType
import com.ttele.algoking.ui.theme.Dimens
import com.ttele.algoking.ui.theme.Spacing

/**
 * The end of a lesson — WATCH and TRY are both done.
 *
 * ### Why there are no stars here
 *
 * **TRY is never scored** (PRODUCT_SPEC.md §2). It exists so that the learner can
 * be wrong as often as they like without cost, and a star rating would quietly
 * undo that: the moment a run has a grade, the guidance ladder becomes something
 * to avoid rather than something to use. So this screen reports what the run
 * *was* — decisions, comparisons, wrong turns — and judges none of it.
 *
 * Assessment is the job of CHALLENGE, which is deferred to V2
 * (`docs/v2-challenge.md`). The star families, the verdict builder and the
 * generator all survive in `:engine` for it; none of them is referenced here.
 */
@Composable
fun LessonCompleteScreen(
    algorithmName: String,
    metrics: Metrics,
    modifier: Modifier = Modifier,
    /** Only used to decide whether the Stack-vs-Queue card has earned its place. */
    algorithmId: AlgorithmId? = null,
    onWatchAgain: () -> Unit = {},
    onTryAgain: () -> Unit = {},
    onNextAlgorithm: () -> Unit = {},
    onHome: () -> Unit = {},
) {
    val clean = metrics.wrongDecisions == 0

    AlgoScreen(modifier) {
        Column(Modifier.fillMaxSize()) {
            AlgoHeader(
                modifier = Modifier.statusBarsPadding(),
                leading = { IconTileButton(AlgoIcons.ArrowBack, onClick = onHome) },
                center = { HeaderTitle("Complete") },
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.cardGap),
            ) {
                Gap(Spacing.xxs)

                // -- Headline: the algorithm is done ---------------------------
                AlgoCard(
                    modifier = Modifier.fillMaxWidth(),
                    color = AlgoColors.successSoft,
                    shadow = null,
                    border = AlgoColors.success.copy(alpha = 0.28f),
                    padding = Spacing.lg,
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            CategoryBadge(
                                label = "COMPLETED",
                                accent = AlgoAccent.Green,
                                solid = true,
                            )
                            Gap(Spacing.sm)
                            Text(
                                text = "$algorithmName complete",
                                style = AlgoType.headlineLarge,
                                color = AlgoColors.textPrimary,
                            )
                            Gap(Spacing.xs)
                            Text(
                                text = "You watched it, then you ran it yourself.",
                                style = AlgoType.bodyLarge,
                                color = AlgoColors.textSecondary,
                            )
                        }
                        Gap(Spacing.xs)
                        MascotKing(Modifier.size(Dimens.mascot))
                    }
                }

                // -- What the run actually was ---------------------------------
                SectionLabel("Your run")
                MetricRow(
                    listOf(
                        Metric("Decisions", metrics.steps.toString()),
                        Metric("Comparisons", metrics.comparisons.toString()),
                        Metric("Wrong turns", metrics.wrongDecisions.toString()),
                    ),
                )

                // -- The takeaway, which is the reason the lesson existed -------
                SectionLabel("What you learned")
                AlgoCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            text = runLine(metrics, clean),
                            style = AlgoType.bodyLarge,
                            color = AlgoColors.textSecondary,
                        )
                        algorithmId?.let { id ->
                            Gap(Spacing.sm)
                            Text(
                                text = insightFor(id),
                                style = AlgoType.bodyLarge,
                                color = AlgoColors.textPrimary,
                            )
                        }
                    }
                }

                // -- The payoff of learning both -------------------------------
                if (algorithmId == AlgorithmId.STACK || algorithmId == AlgorithmId.QUEUE) {
                    SectionLabel("Stack vs Queue")
                    StackVsQueueCard(algorithmId)
                }

                Gap(Spacing.xxs)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = Dimens.screenPadding, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                PrimaryButton(
                    label = "Next algorithm",
                    modifier = Modifier.fillMaxWidth(),
                    icon = AlgoIcons.ArrowForward,
                    onClick = onNextAlgorithm,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    SecondaryButton(
                        label = "Watch again",
                        modifier = Modifier.weight(1f),
                        leadingIcon = AlgoIcons.Restart,
                        onClick = onWatchAgain,
                    )
                    SecondaryButton(
                        label = "Try again",
                        modifier = Modifier.weight(1f),
                        leadingIcon = AlgoIcons.Restart,
                        onClick = onTryAgain,
                    )
                }
            }
        }
    }
}

/**
 * The one card that makes learning both structures worth more than learning either.
 *
 * The two columns are deliberately identical in shape and differ in exactly the
 * places the structures differ — so the eye lands on the three lines that matter
 * rather than on a wall of prose. The structure just finished is the highlighted
 * one, which is what turns a reference table into a conclusion.
 */
@Composable
private fun StackVsQueueCard(current: AlgorithmId) {
    val rows = listOf(
        "Rule" to ("Last in, first out" to "First in, first out"),
        "Add" to ("Push onto the top" to "Enqueue at the rear"),
        "Remove" to ("Pop from the top" to "Dequeue from the front"),
        "Shape" to ("A pile, one live end" to "A line, two live ends"),
        "Used for" to ("Undo, back button, DFS" to "Print jobs, BFS, task queues"),
    )
    AlgoCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth()) {
                Box(Modifier.width(Dimens.compareLabelWidth))
                ComparisonHeading("STACK", current == AlgorithmId.STACK, Modifier.weight(1f))
                Gap(Spacing.xs)
                ComparisonHeading("QUEUE", current == AlgorithmId.QUEUE, Modifier.weight(1f))
            }
            rows.forEach { (label, values) ->
                Gap(Spacing.sm)
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        text = label,
                        style = AlgoType.labelSmall,
                        color = AlgoColors.textMuted,
                        modifier = Modifier.width(Dimens.compareLabelWidth),
                    )
                    ComparisonCell(values.first, current == AlgorithmId.STACK, Modifier.weight(1f))
                    Gap(Spacing.xs)
                    ComparisonCell(values.second, current == AlgorithmId.QUEUE, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ComparisonHeading(text: String, highlighted: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = AlgoType.labelSmall,
        color = if (highlighted) AlgoColors.primary else AlgoColors.textMuted,
        modifier = modifier,
    )
}

@Composable
private fun ComparisonCell(text: String, highlighted: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = AlgoType.bodyMedium,
        color = if (highlighted) AlgoColors.textPrimary else AlgoColors.textSecondary,
        modifier = modifier,
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = AlgoType.labelSmall,
        color = AlgoColors.textMuted,
        modifier = Modifier.padding(start = Spacing.xxs),
    )
}

/**
 * Built from what the run actually cost, so it describes this learner's run rather
 * than congratulating them generically. It counts *decisions*, which every lesson
 * has, rather than array positions, which not every lesson has.
 */
private fun runLine(metrics: Metrics, clean: Boolean): String {
    val effort = if (metrics.comparisons > 0) {
        "${metrics.comparisons} comparisons"
    } else {
        "${metrics.steps} decisions"
    }
    return when {
        clean -> "$effort, and not one wrong turn."
        metrics.wrongDecisions == 1 -> "$effort, with one wrong turn that you worked back from."
        else -> "$effort, with ${metrics.wrongDecisions} wrong turns — each one explained before " +
            "you moved on."
    }
}

/** The single idea the lesson exists to leave behind. */
private fun insightFor(id: AlgorithmId): String = when (id) {
    AlgorithmId.BINARY_SEARCH ->
        "One comparison eliminates half the search space. That is what O(log n) means."

    AlgorithmId.BUBBLE_SORT ->
        "Every pass floats the largest remaining value to the end, so the sorted tail " +
            "grows from the right."

    AlgorithmId.SELECTION_SORT ->
        "A whole pass, and one swap. Selection Sort remembers the smallest value rather " +
            "than moving things as it goes."

    AlgorithmId.INSERTION_SORT ->
        "The left side is always already sorted. The key walks left into the gap that " +
            "shifting opened for it."

    AlgorithmId.MERGE_SORT ->
        "Divide until the pieces are trivially sorted, then combine. The combining is " +
            "where the work happens."

    AlgorithmId.QUICK_SORT ->
        "Once a pivot is in its final place, it never moves again — and the two sides " +
            "can be sorted independently."

    AlgorithmId.STACK ->
        "Last in, first out. Only the top is reachable, and that single rule is the " +
            "whole structure."

    AlgorithmId.QUEUE ->
        "First in, first out. Two live ends: items join at the rear and leave from the front."

    AlgorithmId.LINKED_LIST ->
        "A list is its links. There is no jumping ahead — every node is reached through " +
            "the one before it."

    AlgorithmId.HASH_MAP ->
        "The key calculates where its value lives, so lookup does not have to walk the " +
            "data. A collision is normal, not an error."

    AlgorithmId.TWO_POINTERS ->
        "Every move ruled out a whole row of pairs, not just one — and that only works " +
            "because the array is sorted."
}
