package com.ttele.algoking.feature.lesson

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.ttele.algoking.engine.scenario.Mission
import com.ttele.algoking.ui.components.AlgoCard
import com.ttele.algoking.ui.components.AlgoHeader
import com.ttele.algoking.ui.components.AlgoScreen
import com.ttele.algoking.ui.components.Gap
import com.ttele.algoking.ui.components.HeaderTitle
import com.ttele.algoking.ui.components.IconTileButton
import com.ttele.algoking.ui.components.PrimaryButton
import com.ttele.algoking.ui.components.StageStepper
import com.ttele.algoking.ui.icons.AlgoIcons
import com.ttele.algoking.ui.theme.AlgoColors
import com.ttele.algoking.ui.theme.AlgoType
import com.ttele.algoking.ui.theme.Dimens
import com.ttele.algoking.ui.theme.Radius
import com.ttele.algoking.ui.theme.Spacing

/**
 * The mission brief — the screen that turns a Challenge into a situation.
 *
 * Three facts and a button: **where you are, what you are looking for, and what
 * counts as done.** Everything a learner needs to start, and nothing that would
 * start solving it for them.
 *
 * It never names Binary Search. Watch taught the algorithm and Try guided it;
 * what Challenge tests is whether the learner *recognises* a sorted world as one
 * they already know how to search (PRODUCT_SPEC.md §6). Printing the answer on
 * the brief would test nothing. The single clue is that the data is ordered —
 * which is a fact about the warehouse, not an instruction.
 */
@Composable
fun MissionIntroScreen(
    mission: Mission,
    algorithmName: String,
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
                Gap(Spacing.sm)

                // ── Where you are ─────────────────────────────────────────────
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(mission.icon, style = AlgoType.displayLarge)
                    Gap(Spacing.xs)
                    Text(
                        text = mission.title,
                        style = AlgoType.displayLarge,
                        color = AlgoColors.textPrimary,
                        textAlign = TextAlign.Center,
                    )
                    Gap(Spacing.sm)
                    Text(
                        text = mission.story,
                        style = AlgoType.bodyLarge,
                        color = AlgoColors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }

                Gap(Spacing.sm)

                // ── What you are looking for ──────────────────────────────────
                AlgoCard(
                    modifier = Modifier.fillMaxWidth(),
                    color = AlgoColors.surfaceVariant,
                    shadow = null,
                    border = AlgoColors.border,
                    padding = Spacing.lg,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "YOUR TARGET",
                            style = AlgoType.labelSmall,
                            color = AlgoColors.textMuted,
                        )
                        Gap(Spacing.xs)
                        Box(
                            modifier = Modifier
                                .background(AlgoColors.primarySoft, Radius.pill)
                                .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                        ) {
                            Text(
                                text = mission.targetLabel,
                                style = AlgoType.displayLarge,
                                color = AlgoColors.primary,
                            )
                        }
                        Gap(Spacing.md)
                        Text(
                            text = mission.goal,
                            style = AlgoType.titleMedium,
                            color = AlgoColors.textPrimary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                // ── The one clue: the world is ordered ────────────────────────
                // A fact about the warehouse, never an instruction about what to
                // do with it. Noticing that this is enough is the whole test.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AlgoColors.successSoft, Radius.card)
                        .padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("↕", style = AlgoType.titleMedium, color = AlgoColors.onSuccessSoft)
                    Gap(Spacing.sm)
                    Text(
                        text = mission.sortedBy,
                        style = AlgoType.bodyMedium,
                        color = AlgoColors.onSuccessSoft,
                    )
                }

                Text(
                    text = "${mission.size} ${mission.subject} · " +
                        "no clue which one until you look",
                    style = AlgoType.labelSmall,
                    color = AlgoColors.textMuted,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(Dimens.screenPadding),
            ) {
                PrimaryButton(
                    label = "Start mission",
                    modifier = Modifier.fillMaxWidth(),
                    icon = AlgoIcons.ArrowForward,
                    onClick = onStart,
                )
            }
        }
    }
}
