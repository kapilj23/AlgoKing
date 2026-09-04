package com.ttele.algoking.feature.result

import androidx.compose.foundation.background
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
import com.ttele.algoking.engine.challenge.ChallengeRun
import com.ttele.algoking.engine.challenge.ChallengeType
import com.ttele.algoking.engine.core.AlgorithmId
import com.ttele.algoking.engine.scoring.ScoreResult
import com.ttele.algoking.engine.scoring.Verdict
import com.ttele.algoking.ui.components.AlgoCard
import com.ttele.algoking.ui.components.AlgoHeader
import com.ttele.algoking.ui.components.AlgoIcon
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
import com.ttele.algoking.ui.theme.Radius
import com.ttele.algoking.ui.theme.Spacing

/**
 * The result of a completed challenge.
 *
 * Mastery is shown **here**, as a status, and nowhere else. It is not a stage the
 * learner visits — the learning spine is WATCH → TRY → CHALLENGE and stops there.
 */
@Composable
fun ResultScreen(
    algorithmName: String,
    run: ChallengeRun,
    score: ScoreResult,
    modifier: Modifier = Modifier,
    /** Only used to decide whether the Stack-vs-Queue card has earned its place. */
    algorithmId: AlgorithmId? = null,
    onPracticeAgain: () -> Unit = {},
    onTryAgain: () -> Unit = {},
    onNextAlgorithm: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val strong = score.stars >= 3
    val passed = score.stars >= 2

    AlgoScreen(modifier) {
        Column(Modifier.fillMaxSize()) {
            AlgoHeader(
                modifier = Modifier.statusBarsPadding(),
                leading = { IconTileButton(AlgoIcons.ArrowBack, onClick = onBack) },
                center = { HeaderTitle("Result") },
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.cardGap),
            ) {
                Gap(Spacing.xxs)

                // ── Headline: what happened, and how well ─────────────────────
                AlgoCard(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (passed) AlgoColors.successSoft else AlgoColors.surfaceVariant,
                    shadow = null,
                    border = if (passed) {
                        AlgoColors.success.copy(alpha = 0.28f)
                    } else {
                        AlgoColors.border
                    },
                    padding = Spacing.lg,
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            CategoryBadge(
                                label = if (passed) "MASTERED" else "KEEP PRACTISING",
                                accent = if (passed) AlgoAccent.Green else AlgoAccent.Violet,
                                solid = true,
                            )
                            Gap(Spacing.sm)
                            Text(
                                text = if (strong) {
                                    "$algorithmName complete"
                                } else {
                                    "Good attempt"
                                },
                                style = AlgoType.headlineLarge,
                                color = AlgoColors.textPrimary,
                            )
                            Gap(Spacing.xs)
                            StarRow(score.stars)
                        }
                        Gap(Spacing.xs)
                        MascotKing(Modifier.size(Dimens.mascot))
                    }
                }

                // ── Performance ───────────────────────────────────────────────
                SectionLabel("Performance")
                MetricRow(
                    if (run.challenge.type == ChallengeType.OPERATIONS) {
                        // A structure has nothing to compare and nothing to swap.
                        // What it has is operations, and whether they were right.
                        listOf(
                            Metric("Operations", run.decisions.toString()),
                            Metric("Mistakes", run.mistakes.toString()),
                            Metric("Hints", run.hintsUsed.toString()),
                        )
                    } else if (run.challenge.target == null) {
                        // Sorting: swaps are informative, but they are a property of
                        // the input — never of the learner (PRODUCT_SPEC.md §7).
                        listOf(
                            Metric("Comparisons", run.comparisons.toString()),
                            Metric("Swaps", run.swaps.toString()),
                            Metric("Mistakes", run.mistakes.toString()),
                        )
                    } else {
                        listOf(
                            Metric("Comparisons", run.comparisons.toString()),
                            Metric("Mistakes", run.mistakes.toString()),
                            Metric("Hints", run.hintsUsed.toString()),
                        )
                    },
                )

                // ── The verdict: coaching, not a scoreboard ───────────────────
                SectionLabel("Insight")
                AlgoCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            text = verdictLine(score.verdict, run),
                            style = AlgoType.bodyLarge,
                            color = AlgoColors.textSecondary,
                        )
                        Gap(Spacing.sm)
                        Text(
                            text = insightLine(run),
                            style = AlgoType.bodyLarge,
                            color = AlgoColors.textPrimary,
                        )
                    }
                }

                // ── The payoff of learning both ───────────────────────────────
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
                // A weaker run offers the same problem again; a strong one offers a
                // fresh one. Never gated by an ad — PRODUCT_SPEC.md §7.
                if (passed) {
                    PrimaryButton(
                        label = "Practice again",
                        modifier = Modifier.fillMaxWidth(),
                        icon = AlgoIcons.Restart,
                        onClick = onPracticeAgain,
                    )
                    SecondaryButton(
                        label = "Next algorithm",
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = AlgoIcons.ArrowForward,
                        onClick = onNextAlgorithm,
                    )
                } else {
                    PrimaryButton(
                        label = "Try again",
                        modifier = Modifier.fillMaxWidth(),
                        icon = AlgoIcons.Restart,
                        onClick = onTryAgain,
                    )
                    SecondaryButton(
                        label = "Practice a new problem",
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = AlgoIcons.ArrowForward,
                        onClick = onPracticeAgain,
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

@Composable
private fun StarRow(stars: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .size(Dimens.starSize)
                    .background(
                        if (index < stars) AlgoColors.goldSoft else AlgoColors.surfaceMuted,
                        Radius.pill,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                AlgoIcon(
                    icon = AlgoIcons.Sparkle,
                    tint = if (index < stars) AlgoColors.gold else AlgoColors.disabled,
                    size = Dimens.starGlyph,
                )
            }
        }
    }
}

/**
 * Generated from run data, not picked from a template pool. "Two missteps, both on
 * the same comparison" is coaching; "Great job!" is noise.
 */
private fun verdictLine(verdict: Verdict, run: ChallengeRun): String {
    val found = when (run.challenge.type) {
        ChallengeType.NOT_FOUND -> "You proved ${run.challenge.target} is not in the array"
        ChallengeType.FIND -> "You found ${run.challenge.target}"
        ChallengeType.EARLY_EXIT -> "You spotted that it was already sorted"
        ChallengeType.SORT -> "You sorted the array"
        ChallengeType.OPERATIONS -> "You ran every operation on the right end"
        ChallengeType.TRAVERSE -> "You walked the chain to ${run.challenge.target}"
        ChallengeType.LINK_INSERT -> "You linked ${run.challenge.target} into the chain"
        ChallengeType.LINK_DELETE -> "You unlinked ${run.challenge.target} without breaking the chain"
        ChallengeType.HASH_OPERATIONS -> "You hashed every key to the right bucket"
        ChallengeType.HASH_COLLISION -> "You read a shared bucket correctly"
    }
    return when (verdict) {
        Verdict.Optimal ->
            "$found in ${run.comparisons} comparisons — the fewest this array allows."

        is Verdict.ExtraComparisons ->
            "$found, but with ${verdict.extra} more comparison" +
                "${plural(verdict.extra)} than needed."

        is Verdict.WrongDecisions ->
            "$found, but made ${verdict.count} incorrect decision${plural(verdict.count)}."

        is Verdict.UsedHints ->
            "$found with ${verdict.count} hint${plural(verdict.count)}. Try the next one cold."

        Verdict.Completed -> "$found."
    }
}

private fun insightLine(run: ChallengeRun): String = when (run.challenge.type) {
    ChallengeType.NOT_FOUND ->
        "Emptying the range is an answer. Binary Search proves absence as fast as presence."

    ChallengeType.EARLY_EXIT ->
        "A pass with no swaps proves the array is sorted. That is when Bubble Sort stops."

    ChallengeType.SORT -> if (run.mistakes == 0) {
        "You made every SWAP and KEEP decision correctly, and moved the larger value " +
            "toward the end on every pass."
    } else {
        "Bubble Sort only swaps when the left value is the larger one."
    }

    ChallengeType.HASH_OPERATIONS -> if (run.mistakes == 0) {
        "You used the key to find the bucket instead of searching the whole collection — " +
            "and you handled the shared bucket correctly."
    } else {
        "Every operation starts the same way: hash the key, then look in that one bucket."
    }

    ChallengeType.HASH_COLLISION -> if (run.mistakes == 0) {
        "Landing in the right bucket is not the same as finding the right entry. You did both."
    } else {
        "Two keys can share a bucket. The hash gets you there; comparing keys finds the entry."
    }

    ChallengeType.TRAVERSE -> if (run.mistakes == 0) {
        "You reached it the only way a linked list allows: one link at a time."
    } else {
        "There is no jumping ahead in a linked list. Every node is reached through the one before it."
    }

    ChallengeType.LINK_INSERT -> if (run.mistakes == 0) {
        "You put the node in the right gap and gave it the right NEXT — both halves, in order."
    } else {
        "Inserting is two links, not one: what points at the new node, and what it points at."
    }

    ChallengeType.LINK_DELETE -> if (run.mistakes == 0) {
        "You reconnected the chain around the node, so nothing was left dangling."
    } else {
        "A node is deleted when nothing points at it any more — the previous node has to skip past it."
    }

    ChallengeType.OPERATIONS -> if (run.mistakes == 0) {
        "You reached for the right end every single time. That rule is the whole structure."
    } else {
        "Every operation on a linear structure touches one end, and which end never changes."
    }

    ChallengeType.FIND -> if (run.mistakes == 0) {
        "You eliminated half the search space at every decision."
    } else {
        "When the middle is smaller than the target, everything to its left is smaller too."
    }
}

private fun plural(n: Int) = if (n == 1) "" else "s"
