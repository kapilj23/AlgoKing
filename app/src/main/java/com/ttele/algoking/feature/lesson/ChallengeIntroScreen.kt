package com.ttele.algoking.feature.lesson

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ttele.algoking.engine.challenge.Challenge
import com.ttele.algoking.engine.challenge.ChallengeType
import com.ttele.algoking.ui.components.AlgoCard
import com.ttele.algoking.ui.components.AlgoHeader
import com.ttele.algoking.ui.components.AlgoScreen
import com.ttele.algoking.ui.components.CategoryBadge
import com.ttele.algoking.ui.components.Gap
import com.ttele.algoking.ui.components.HeaderTitle
import com.ttele.algoking.ui.components.IconTileButton
import com.ttele.algoking.ui.components.MascotKing
import com.ttele.algoking.ui.components.PrimaryButton
import com.ttele.algoking.ui.components.Stage
import com.ttele.algoking.ui.components.StageState
import com.ttele.algoking.ui.components.StageStepper
import com.ttele.algoking.ui.icons.AlgoIcons
import com.ttele.algoking.ui.theme.AlgoAccent
import com.ttele.algoking.ui.theme.AlgoColors
import com.ttele.algoking.ui.theme.AlgoType
import com.ttele.algoking.ui.theme.Dimens
import com.ttele.algoking.ui.theme.Spacing

/**
 * The challenge briefing.
 *
 * Deliberately thin: the target, the rule, and a button. **No tutorial and no
 * re-explanation of the algorithm** — the learner has finished Watch and Try, and
 * repeating the lesson here would undo the point of the stage.
 */
@Composable
fun ChallengeIntroScreen(
    challenge: Challenge,
    algorithmName: String,
    brief: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onStart: () -> Unit = {},
) {
    AlgoScreen(modifier) {
        Column(Modifier.fillMaxSize()) {
            AlgoHeader(
                modifier = Modifier.statusBarsPadding(),
                leading = { IconTileButton(AlgoIcons.ArrowBack, onClick = onBack) },
                center = { HeaderTitle(algorithmName) },
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Dimens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.cardGap),
            ) {
                Gap(Spacing.xxs)
                StageStepper(challengeStages())
                Gap(Spacing.xxs)

                AlgoCard(
                    modifier = Modifier.fillMaxWidth(),
                    color = AlgoColors.surfaceVariant,
                    shadow = null,
                    border = AlgoColors.border,
                    padding = Spacing.lg,
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CategoryBadge(
                                label = challenge.difficulty.name.uppercase(),
                                accent = AlgoAccent.Violet,
                                solid = true,
                            )
                            Gap(Spacing.xs)
                            CategoryBadge(
                                label = when (challenge.type) {
                                    ChallengeType.FIND -> "FIND"
                                    ChallengeType.NOT_FOUND -> "DOES IT EXIST?"
                                    ChallengeType.SORT -> "SORT"
                                    ChallengeType.EARLY_EXIT -> "IS IT DONE?"
                                    ChallengeType.OPERATIONS -> "RUN THE OPERATIONS"
                                    ChallengeType.TRAVERSE -> "WALK THE CHAIN"
                                    ChallengeType.LINK_INSERT -> "LINK IT IN"
                                    ChallengeType.LINK_DELETE -> "UNLINK IT"
                                    ChallengeType.HASH_OPERATIONS -> "HASH EVERY KEY"
                                    ChallengeType.HASH_COLLISION -> "SHARED BUCKET"
                                },
                                accent = AlgoAccent.Blue,
                            )
                        }

                        Gap(Spacing.md)
                        Text(
                            text = "$algorithmName Challenge",
                            style = AlgoType.titleMedium,
                            color = AlgoColors.primary,
                        )
                        Gap(Spacing.xxs)
                        Text(
                            text = challenge.target
                                ?.let { "Find $it" }
                                ?: "Sort the array",
                            style = AlgoType.displayLarge,
                            color = AlgoColors.textPrimary,
                        )

                        Gap(Spacing.sm)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = challenge.target?.let {
                                        "You have a sorted array of ${challenge.size} " +
                                            "numbers. Find the target using $algorithmName."
                                    } ?: "You have ${challenge.size} numbers in the wrong " +
                                        "order. Sort them from smallest to largest using " +
                                        "$algorithmName.",
                                    style = AlgoType.bodyLarge,
                                    color = AlgoColors.textSecondary,
                                )
                                Gap(Spacing.xs)
                                Text(
                                    text = brief,
                                    style = AlgoType.bodyMedium,
                                    color = AlgoColors.textMuted,
                                )
                            }
                            Gap(Spacing.xs)
                            MascotKing(Modifier.size(Dimens.mascot))
                        }
                    }
                }
            }

            PrimaryButton(
                label = "Start challenge",
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = Dimens.screenPadding, vertical = Spacing.sm),
                icon = AlgoIcons.ArrowForward,
                onClick = onStart,
            )
        }
    }
}

internal fun challengeStages(): List<Stage> = Phase.entries.map { stage ->
    Stage(
        label = stage.label,
        state = when {
            stage.ordinal < Phase.Challenge.ordinal -> StageState.Complete
            stage == Phase.Challenge -> StageState.Current
            else -> StageState.Upcoming
        },
    )
}
