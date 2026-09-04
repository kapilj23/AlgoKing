package com.ttele.algoking.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.ttele.algoking.ui.components.AlgoCard
import com.ttele.algoking.ui.components.AlgoHeader
import com.ttele.algoking.ui.components.AlgoScreen
import com.ttele.algoking.ui.components.ArrayCellRow
import com.ttele.algoking.ui.components.ArrayVisualizer
import com.ttele.algoking.ui.components.BarSpec
import com.ttele.algoking.ui.components.ChallengeDots
import com.ttele.algoking.ui.components.Gap
import com.ttele.algoking.ui.components.HeaderTitle
import com.ttele.algoking.ui.components.IconTileButton
import com.ttele.algoking.ui.components.InstructionText
import com.ttele.algoking.ui.components.Metric
import com.ttele.algoking.ui.components.MetricRow
import com.ttele.algoking.ui.components.PrimaryButton
import com.ttele.algoking.ui.components.SecondaryButton
import com.ttele.algoking.ui.components.StreakChip
import com.ttele.algoking.ui.components.VizLegend
import com.ttele.algoking.ui.components.VizState
import com.ttele.algoking.ui.icons.AlgoIcons
import com.ttele.algoking.ui.theme.AlgoColors
import com.ttele.algoking.ui.theme.AlgoKingTheme
import com.ttele.algoking.ui.theme.AlgoType
import com.ttele.algoking.ui.theme.Dimens
import com.ttele.algoking.ui.theme.Spacing

/**
 * SCREEN 3 — Practice / Challenge.
 *
 * Bubble Sort's challenge is Format B, Pass Prediction (PRODUCT_SPEC.md §6): the
 * learner predicts the array after one full pass by tapping two adjacent tiles to
 * swap them, then submits. There is no fail state and no countdown — the metrics
 * strip records the run silently.
 *
 * Visually it is the same system as Home and Learning: same header, same 20dp
 * cards, same array language, same legend, same button components.
 */
@Composable
fun PracticeScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    val problem = listOf(9, 3, 7, 1, 5, 2)

    val bars = listOf(
        BarSpec(9, VizState.Comparing),
        BarSpec(3, VizState.Next),
        BarSpec(7, VizState.Checked),
        BarSpec(1, VizState.Checked),
        BarSpec(5, VizState.Checked),
        BarSpec(2, VizState.Checked),
    )

    val metrics = listOf(
        Metric("Comparisons", "4"),
        Metric("Swaps", "2"),
        Metric("Best", "--"),
    )

    AlgoScreen(modifier) {
        Column(Modifier.fillMaxSize()) {
            AlgoHeader(
                modifier = Modifier.statusBarsPadding(),
                leading = { IconTileButton(AlgoIcons.ArrowBack, onClick = onBack) },
                trailing = { StreakChip(10) },
                center = { HeaderTitle("Practice") },
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.cardGap),
            ) {
                Gap(Spacing.xxs)

                // ── The challenge: progress, instruction, the problem itself ──
                AlgoCard(
                    modifier = Modifier.fillMaxWidth(),
                    color = AlgoColors.surfaceVariant,
                    shadow = null,
                    border = AlgoColors.border,
                    padding = Spacing.lg,
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            "Challenge 2 / 10",
                            style = AlgoType.titleMedium,
                            color = AlgoColors.primary,
                        )
                        Gap(Spacing.md)
                        ChallengeDots(total = 10, completed = 2)
                        Gap(Spacing.md)

                        AlgoCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth()) {
                                InstructionText(
                                    listOf(
                                        "Show the array after one full pass.",
                                        "Tap two adjacent tiles to swap them.",
                                    ),
                                )
                                Gap(Spacing.md)
                                ArrayCellRow(problem)
                            }
                        }
                    }
                }

                // ── The live canvas ───────────────────────────────────────────
                Gap(Spacing.xxs)
                ArrayVisualizer(bars = bars, arcFrom = 0, arcTo = 1)
                Gap(Spacing.xs)
                VizLegend()
                Gap(Spacing.xxs)

                // ── Metrics ───────────────────────────────────────────────────
                MetricRow(metrics)

                // ── Reversible actions ────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    SecondaryButton(
                        label = "Restart",
                        modifier = Modifier.weight(1f),
                        leadingIcon = AlgoIcons.Restart,
                        iconTint = AlgoColors.textSecondary,
                    )
                    SecondaryButton(
                        label = "Hint",
                        modifier = Modifier.weight(1f),
                        leadingIcon = AlgoIcons.Bulb,
                        iconTint = AlgoColors.gold,
                    )
                    SecondaryButton(
                        label = "Undo",
                        modifier = Modifier.weight(1f),
                        leadingIcon = AlgoIcons.Undo,
                        iconTint = AlgoColors.accent,
                    )
                }

                Gap(Spacing.xxs)
            }

            // ── The one forward action, lowest on the screen ──────────────────
            PrimaryButton(
                label = "Submit Answer",
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = Dimens.screenPadding, vertical = Spacing.sm),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun PracticeScreenPreview() {
    AlgoKingTheme { PracticeScreen() }
}
