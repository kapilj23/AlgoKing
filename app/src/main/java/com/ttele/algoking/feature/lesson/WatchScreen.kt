package com.ttele.algoking.feature.lesson

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ttele.algoking.engine.catalog.LessonPack
import com.ttele.algoking.engine.decision.Action
import com.ttele.algoking.engine.walkthrough.WatchPrediction
import androidx.compose.runtime.mutableStateOf
import com.ttele.algoking.ui.components.DecisionButton
import com.ttele.algoking.ui.components.DecisionTone
import com.ttele.algoking.engine.event.Relation
import com.ttele.algoking.engine.walkthrough.WatchStep
import com.ttele.algoking.engine.walkthrough.WatchStepKind
import com.ttele.algoking.ui.components.AlgoCard
import com.ttele.algoking.ui.components.AlgoHeader
import com.ttele.algoking.ui.components.AlgoScreen
import com.ttele.algoking.ui.components.CategoryBadge
import com.ttele.algoking.ui.components.ComparisonChip
import com.ttele.algoking.ui.components.Gap
import com.ttele.algoking.ui.components.HeaderCountChip
import com.ttele.algoking.ui.components.HeaderTitle
import com.ttele.algoking.ui.components.IconTileButton
import com.ttele.algoking.ui.components.MascotKing
import com.ttele.algoking.ui.components.MidpointChip
import com.ttele.algoking.ui.components.PrimaryButton
import com.ttele.algoking.ui.components.SceneLegend
import com.ttele.algoking.ui.components.SecondaryButton
import com.ttele.algoking.ui.components.SceneRenderer
import com.ttele.algoking.ui.components.Stage
import com.ttele.algoking.ui.components.StageState
import com.ttele.algoking.ui.components.StageStepper
import com.ttele.algoking.ui.components.StepDots
import com.ttele.algoking.ui.icons.AlgoIcons
import com.ttele.algoking.ui.theme.AlgoAccent
import com.ttele.algoking.ui.theme.AlgoColors
import com.ttele.algoking.ui.theme.AlgoType
import com.ttele.algoking.ui.theme.Dimens
import com.ttele.algoking.ui.theme.Spacing

/**
 * WATCH — an interactive walkthrough, not a video.
 *
 * There is **no play button, no pause, no speed control and no autoplay** anywhere
 * on this screen. The learner advances with NEXT and controls the pace entirely.
 * Every step is a deterministic algorithmic state produced by the engine
 * (`WatchScript`), paired with the one sentence that explains it.
 *
 * The chrome, cards, renderer, legend and buttons are the same components Try and
 * Try use — only the interaction differs.
 */
@Composable
fun <S : Any, A : Action> WatchScreen(
    pack: LessonPack<S, A>,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onStartTry: () -> Unit = {},
    /** Fired once the learner has actually seen the walkthrough through. */
    onWatchComplete: () -> Unit = {},
) {
    val algorithmName = pack.displayName
    val script = remember(pack.id) { pack.watchScript() }

    var index by remember(pack.id) { mutableIntStateOf(0) }
    // The checkpoint's answer, if the learner has given one. Never scored.
    var predictionAnswer by remember(index) { mutableStateOf<Int?>(null) }
    val step = script[index]
    val isLast = index == script.size - 1
    val scroll = rememberScrollState()

    // Reaching the last step *is* finishing Watch — there is nothing left to do on
    // it. Waiting for the "Start Try" tap would punish a learner who read the recap
    // and then backed out, which is a complete viewing by any honest measure.
    LaunchedEffect(pack.id, isLast) {
        if (isLast) onWatchComplete()
    }

    AlgoScreen(modifier) {
        Column(Modifier.fillMaxSize()) {

            AlgoHeader(
                modifier = Modifier.statusBarsPadding(),
                leading = { IconTileButton(AlgoIcons.ArrowBack, onClick = onBack) },
                trailing = { HeaderCountChip(index + 1, script.size) },
                center = { HeaderTitle(algorithmName) },
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scroll)
                    .padding(horizontal = Dimens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.cardGap),
            ) {
                Gap(Spacing.xxs)
                StageStepper(watchStages())
                StepDots(total = script.size, current = index)

                // ── The step: what just happened, and why ─────────────────────
                AlgoCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        if (step.kind == WatchStepKind.FOUND ||
                            step.kind == WatchStepKind.SUMMARY
                        ) {
                            CategoryBadge(
                                label = "FOUND",
                                accent = AlgoAccent.Green,
                                solid = true,
                            )
                            Gap(Spacing.xs)
                        } else if (step.kind == WatchStepKind.NOT_FOUND) {
                            CategoryBadge(
                                label = "NOT FOUND",
                                accent = AlgoAccent.Orange,
                                solid = true,
                            )
                            Gap(Spacing.xs)
                        }
                        Text(
                            text = Narration.resolve(step.headline),
                            style = if (step.kind == WatchStepKind.INSIGHT) {
                                AlgoType.displayLarge
                            } else {
                                AlgoType.headlineLarge
                            },
                            color = if (step.kind == WatchStepKind.INSIGHT) {
                                AlgoColors.primary
                            } else {
                                AlgoColors.textPrimary
                            },
                        )
                        step.support?.takeIf { step.kind != WatchStepKind.SUMMARY }?.let { support ->
                            Gap(Spacing.xs)
                            Text(
                                text = Narration.resolve(support),
                                style = AlgoType.bodyLarge,
                                color = AlgoColors.textSecondary,
                            )
                        }

                        AnimatedVisibility(
                            visible = step.comparison != null || step.midpoint != null,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Gap(Spacing.md)
                                // The two chips never coexist: one step picks the
                                // middle, the next compares it. Showing both at once
                                // would put two equations on screen and let the
                                // learner read neither.
                                step.midpoint?.let {
                                    MidpointChip(it.lo, it.hi, it.mid)
                                }
                                step.comparison?.let {
                                    ComparisonChip(it.left, it.relation.symbol(), it.right)
                                }
                            }
                        }

                        Gap(Spacing.md)
                        SceneRenderer(step.scene)

                        Gap(Spacing.md)
                        SceneLegend(step.scene)
                    }
                }

                // ── The unscored checkpoint ───────────────────────────────────
                step.prediction?.let { prediction ->
                    PredictionCard(
                        prediction = prediction,
                        answer = predictionAnswer,
                        onAnswer = { predictionAnswer = it },
                    )
                }

                // ── The closing recap ─────────────────────────────────────────
                if (step.kind == WatchStepKind.SUMMARY) {
                    SummaryCard(step)
                }

                Gap(Spacing.xxs)
            }

            // ── One primary action. NEXT is the whole interaction. ────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = Dimens.screenPadding, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Subtle, and only once there is somewhere to go back to.
                if (index > 0) {
                    SecondaryButton(
                        label = "Back",
                        modifier = Modifier.weight(1f),
                        leadingIcon = AlgoIcons.ChevronLeft,
                        onClick = { index-- },
                    )
                }
                val awaitingAnswer = step.prediction != null && predictionAnswer == null
                PrimaryButton(
                    label = if (isLast) "Start Try" else "Next",
                    modifier = Modifier.weight(if (index > 0) 2f else 1f),
                    icon = AlgoIcons.ArrowForward,
                    enabled = !awaitingAnswer,
                    onClick = { if (isLast) onStartTry() else index++ },
                )
            }
        }
    }
}

/**
 * The one unscored checkpoint in a walkthrough.
 *
 * It exists so the learner commits to an answer before Try asks for real. Getting
 * it wrong costs nothing and blocks nothing — but they must answer, because an
 * unanswered prediction teaches nothing.
 */
@Composable
private fun PredictionCard(
    prediction: WatchPrediction,
    answer: Int?,
    onAnswer: (Int) -> Unit,
) {
    val right = answer == prediction.correctIndex
    AlgoCard(
        modifier = Modifier.fillMaxWidth(),
        color = when {
            answer == null -> AlgoColors.surfaceVariant
            right -> AlgoColors.successSoft
            else -> AlgoColors.surfaceVariant
        },
        shadow = null,
        border = if (answer != null && right) {
            AlgoColors.success.copy(alpha = 0.28f)
        } else {
            AlgoColors.border
        },
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = if (answer == null) "Your turn" else if (right) "Correct" else "Not quite",
                style = AlgoType.titleMedium,
                color = if (answer != null && right) {
                    AlgoColors.onSuccessSoft
                } else {
                    AlgoColors.primary
                },
            )
            Gap(Spacing.xs)
            Text(
                text = if (answer == null) {
                    Narration.resolve(prediction.prompt)
                } else {
                    Narration.resolve(
                        if (right) prediction.whenRight else prediction.whenWrong,
                    )
                },
                style = AlgoType.bodyLarge,
                color = AlgoColors.textSecondary,
            )

            if (answer == null) {
                Gap(Spacing.sm)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    prediction.options.forEachIndexed { i, label ->
                        DecisionButton(
                            label = Narration.resolve(label),
                            tone = DecisionTone.forIndex(i),
                            onClick = { onAnswer(i) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(step: WatchStep) {
    AlgoCard(
        modifier = Modifier.fillMaxWidth(),
        color = AlgoColors.surfaceVariant,
        shadow = null,
        border = AlgoColors.border,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = Narration.resolve(step.support),
                    style = AlgoType.titleMedium,
                    color = AlgoColors.primary,
                )
                Gap(Spacing.xs)
                step.bullets.forEachIndexed { i, bullet ->
                    Text(
                        text = "${i + 1}.  ${Narration.resolve(bullet)}",
                        style = AlgoType.bodyLarge,
                        color = AlgoColors.textSecondary,
                    )
                }
            }
            Gap(Spacing.xs)
            MascotKing(Modifier.size(Dimens.mascot))
        }
    }
}

private fun watchStages(): List<Stage> = Phase.entries.map { stage ->
    Stage(
        label = stage.label,
        state = if (stage == Phase.Watch) StageState.Current else StageState.Upcoming,
    )
}

private fun Relation.symbol(): String = when (this) {
    Relation.LESS -> "<"
    Relation.GREATER -> ">"
    Relation.EQUAL -> "="
}
