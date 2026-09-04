package com.ttele.algoking.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.ttele.algoking.ui.components.AlgoCard
import com.ttele.algoking.ui.components.AlgoHeader
import com.ttele.algoking.ui.components.AlgoScreen
import com.ttele.algoking.ui.components.ArrayVisualizer
import com.ttele.algoking.ui.components.BarSpec
import com.ttele.algoking.ui.components.Decision
import com.ttele.algoking.ui.components.DecisionButton
import com.ttele.algoking.ui.components.ExplanationCard
import com.ttele.algoking.ui.components.Gap
import com.ttele.algoking.ui.components.HeaderTitle
import com.ttele.algoking.ui.components.IconTileButton
import com.ttele.algoking.ui.components.InfoButton
import com.ttele.algoking.ui.components.PlayPauseFab
import com.ttele.algoking.ui.components.SecondaryButton
import com.ttele.algoking.ui.components.Stage
import com.ttele.algoking.ui.components.StageState
import com.ttele.algoking.ui.components.StageStepper
import com.ttele.algoking.ui.components.StepChip
import com.ttele.algoking.ui.components.VizLegend
import com.ttele.algoking.ui.components.VizState
import com.ttele.algoking.ui.icons.AlgoIcons
import com.ttele.algoking.ui.theme.AlgoColors
import com.ttele.algoking.ui.theme.AlgoKingTheme
import com.ttele.algoking.ui.theme.AlgoType
import com.ttele.algoking.ui.theme.Dimens
import com.ttele.algoking.ui.theme.Spacing

/**
 * SCREEN 2 — Bubble Sort learning.
 *
 * The screen reads top to bottom as **COMPARE → DECISION → RESULT**
 * (DESIGN_SYSTEM.md §7):
 *
 *  1. COMPARE — the question, the two coloured bars and the dashed arc naming them.
 *  2. DECISION — SWAP / KEEP, of exactly equal weight. The app has already advanced
 *     the pair pointer; the learner only decides swap-or-keep (PRODUCT_SPEC.md §3).
 *  3. RESULT — the explanation, on a different ground so it reads as consequence.
 */
@Composable
fun LearnScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onNext: () -> Unit = {},
) {
    var playing by remember { mutableStateOf(true) }

    // Step 3 of the Bubble Sort lesson: indices 2 and 3 are the pair under test.
    val bars = listOf(
        BarSpec(5, VizState.Checked),
        BarSpec(1, VizState.Checked),
        BarSpec(8, VizState.Comparing),
        BarSpec(2, VizState.Next),
        BarSpec(4, VizState.Checked),
        BarSpec(7, VizState.Checked),
    )

    // Three stages. Mastery is a result, not a place the learner goes.
    val stages = listOf(
        Stage("Watch", StageState.Complete),
        Stage("Try", StageState.Current),
        Stage("Challenge", StageState.Upcoming),
    )

    AlgoScreen(modifier) {
        Column(Modifier.fillMaxSize()) {
            AlgoHeader(
                modifier = Modifier.statusBarsPadding(),
                leading = { IconTileButton(AlgoIcons.ArrowBack, onClick = onBack) },
                trailing = { IconTileButton(AlgoIcons.Bookmark) },
                center = { HeaderTitle("Bubble Sort") },
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.cardGap),
            ) {
                Gap(Spacing.xxs)
                StageStepper(stages)
                Gap(Spacing.xxs)

                // ── COMPARE + DECISION ────────────────────────────────────────
                AlgoCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            StepChip("Step 3 / 14")
                            Box(Modifier.weight(1f))
                            InfoButton()
                        }

                        Gap(Spacing.md)
                        Text(
                            "Compare 8 and 2",
                            style = AlgoType.headlineLarge,
                            color = AlgoColors.textPrimary,
                        )
                        Gap(Spacing.xxs)
                        Text(
                            "What should we do?",
                            style = AlgoType.bodyLarge,
                            color = AlgoColors.textSecondary,
                        )

                        Gap(Spacing.sm)
                        ArrayVisualizer(bars = bars, arcFrom = 2, arcTo = 3)

                        Gap(Spacing.sm)
                        VizLegend()

                        Gap(Spacing.md)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Dimens.cardGap),
                        ) {
                            DecisionButton(Decision.Swap, AlgoIcons.Shuffle)
                            DecisionButton(Decision.Keep, AlgoIcons.ArrowForward)
                        }
                    }
                }

                // ── RESULT ────────────────────────────────────────────────────
                ExplanationCard(
                    title = "Explanation",
                    lines = listOf(
                        "8 is greater than 2,",
                        "so we swap them.",
                        "Smaller values bubble to the left.",
                    ),
                )

                Gap(Spacing.xxs)
            }

            // ── Transport controls ────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(
                        horizontal = Dimens.screenPadding,
                        vertical = Spacing.sm,
                    ),
                horizontalArrangement = Arrangement.spacedBy(Dimens.cardGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SecondaryButton(
                    label = "Previous",
                    modifier = Modifier.weight(1f),
                    leadingIcon = AlgoIcons.ChevronLeft,
                )
                PlayPauseFab(
                    icon = if (playing) AlgoIcons.Pause else AlgoIcons.Play,
                    onClick = { playing = !playing },
                )
                SecondaryButton(
                    label = "Next",
                    modifier = Modifier.weight(1f),
                    trailingIcon = AlgoIcons.ChevronRight,
                    onClick = onNext,
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun LearnScreenPreview() {
    AlgoKingTheme { LearnScreen() }
}
