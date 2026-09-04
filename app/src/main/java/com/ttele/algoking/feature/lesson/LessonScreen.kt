package com.ttele.algoking.feature.lesson

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import com.ttele.algoking.ui.components.AlgoIcon
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ttele.algoking.ads.InstantRewardedAdHost
import com.ttele.algoking.ads.Reward
import com.ttele.algoking.ads.RewardedPlacement
import com.ttele.algoking.engine.challenge.Challenge
import com.ttele.algoking.engine.challenge.HintAccess
import com.ttele.algoking.engine.catalog.LessonPack
import com.ttele.algoking.engine.decision.Action
import com.ttele.algoking.engine.core.Dataset
import com.ttele.algoking.engine.decision.DecisionKind
import androidx.compose.ui.text.style.TextAlign
import com.ttele.algoking.engine.event.Outcome
import com.ttele.algoking.ui.components.AlgoCard
import com.ttele.algoking.ui.components.AlgoHeader
import com.ttele.algoking.ui.components.AlgoScreen
import com.ttele.algoking.ui.components.CelebrationBanner
import com.ttele.algoking.ui.components.DecisionButton
import com.ttele.algoking.ui.components.DecisionTone
import com.ttele.algoking.ui.components.Gap
import com.ttele.algoking.ui.components.HeaderTitle
import com.ttele.algoking.ui.components.IconTileButton
import com.ttele.algoking.ui.components.InfoButton
import com.ttele.algoking.ui.components.MascotKing
import com.ttele.algoking.ui.components.MidpointChip
import com.ttele.algoking.ui.components.Metric
import com.ttele.algoking.ui.components.MetricRow
import com.ttele.algoking.ui.components.PrimaryButton
import com.ttele.algoking.ui.components.SceneLegend
import com.ttele.algoking.ui.components.SecondaryButton
import com.ttele.algoking.ui.components.SceneRenderer
import com.ttele.algoking.ui.components.Stage
import com.ttele.algoking.ui.components.StageState
import com.ttele.algoking.ui.components.StageStepper
import com.ttele.algoking.ui.components.StepChip
import com.ttele.algoking.ui.icons.AlgoIcons
import com.ttele.algoking.ui.theme.AlgoAccent
import com.ttele.algoking.ui.theme.AlgoColors
import com.ttele.algoking.ui.theme.AlgoType
import com.ttele.algoking.ui.theme.Dimens
import com.ttele.algoking.ui.theme.Radius
import com.ttele.algoking.ui.theme.Spacing

/**
 * The interactive stages — TRY and CHALLENGE.
 *
 * WATCH is a walkthrough and lives in [WatchScreen]; it shares every component on
 * this screen but is driven by NEXT rather than by decisions.
 *
 * There is deliberately no MASTER stage. Mastery is the *result* of a completed
 * challenge and lives on the Result screen; it is never a place the learner goes.
 *
 * Chrome, spacing, cards, buttons and the renderer are identical in all three
 * stages, so the sequence reads as one continuous experience — DESIGN_SYSTEM.md §7.
 */
@Composable
fun <S : Any, A : Action> LessonScreen(
    phase: Phase,
    pack: LessonPack<S, A>,
    dataset: Dataset,
    modifier: Modifier = Modifier,
    challenge: Challenge? = null,
    /** Change to start the same problem over from scratch. */
    key: Int = 0,
    onBack: () -> Unit = {},
    onAdvance: (Phase) -> Unit = {},
    onFinishChallenge: (LessonController<S, A>) -> Unit = {},
    /** Fired when the learner drives this stage to its terminal state. */
    onStageComplete: () -> Unit = {},
) {
    val algorithmName = pack.displayName
    val controller = remember(phase, dataset, key) {
        LessonController(phase, pack, dataset, challenge)
    }
    val ui = controller.ui
    val scroll = rememberScrollState()
    // The rewarded-hint offer. Local to the screen: declining it must leave no
    // trace anywhere, least of all in the run being scored.
    var offeringHint by remember(phase, dataset, key) { mutableStateOf(false) }
    val adHost = remember { InstantRewardedAdHost() }

    // Feedback that lands below the fold has not been given. Bring it into view.
    LaunchedEffect(ui.feedback, ui.wrongTick) {
        if (ui.feedback != null) scroll.animateScrollTo(scroll.maxValue)
    }

    // Reaching the terminal state is completion, whatever it cost to get there.
    // Mistakes and hints are recorded on the result screen; they never decide
    // whether the stage counts as learned.
    LaunchedEffect(ui.finished) {
        if (ui.finished) onStageComplete()
    }

    if (offeringHint) {
        HintUnlockDialog(
            onWatchAd = {
                offeringHint = false
                adHost.show(RewardedPlacement.EXTRA_HINT) { reward ->
                    // The reward is exactly the hint, and only on a completed
                    // watch. A dismissed ad costs the learner nothing.
                    if (reward is Reward.Earned) controller.requestHint()
                }
            },
            onDismiss = { offeringHint = false },
        )
    }

    AlgoScreen(modifier) {
        Column(Modifier.fillMaxSize()) {

            AlgoHeader(
                modifier = Modifier.statusBarsPadding(),
                leading = { IconTileButton(AlgoIcons.ArrowBack, onClick = onBack) },
                trailing = { IconTileButton(AlgoIcons.Bookmark) },
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
                StageStepper(stagesFor(phase))
                ui.mission?.let { mission ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AlgoColors.surfaceVariant, Radius.card)
                            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(mission.icon, style = AlgoType.titleMedium)
                        Gap(Spacing.sm)
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = mission.title,
                                style = AlgoType.labelSmall,
                                color = AlgoColors.textMuted,
                            )
                            Text(
                                text = mission.goal,
                                style = AlgoType.titleSmall,
                                color = AlgoColors.textPrimary,
                                maxLines = 1,
                            )
                        }
                    }
                }
                Gap(Spacing.xxs)

                // ── The lesson card: question, canvas, decision ────────────────
                AlgoCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            StepChip(stepLabel(phase, ui))
                            Box(Modifier.weight(1f))
                            InfoButton()
                        }

                        Gap(Spacing.md)
                        Text(
                            text = headline(phase, ui),
                            style = AlgoType.headlineLarge,
                            color = AlgoColors.textPrimary,
                        )
                        // Try leads with the comparison as its headline; Challenge
                        // leads with the question. Neither repeats itself, and neither
                        // hints at which half survives — that is the decision.

                        Gap(Spacing.md)
                        // The array itself is the control when the learner has to
                        // find the middle — Challenge's first decision every round.
                        val cellPick = ui.decision?.takeIf { it.kind == DecisionKind.CELL }
                        SceneRenderer(
                            scene = ui.scene,
                            selectableSlots = cellPick?.cellActions?.keys.orEmpty(),
                            onSelectSlot = { slot ->
                                cellPick?.cellActions?.get(slot)?.let(controller::choose)
                            },
                        )

                        Gap(Spacing.md)
                        SceneLegend(ui.scene)

                        // The decision, for Try and Challenge. Watch answers itself.
                        val decision = ui.decision?.takeIf {
                            it.kind == DecisionKind.OPTIONS
                        }
                        if (decision != null) {
                            Gap(Spacing.md)
                            if (phase == Phase.Try) {
                                Text(
                                    text = decision.prompt,
                                    style = AlgoType.titleMedium,
                                    color = AlgoColors.textPrimary,
                                )
                                Gap(Spacing.sm)
                            }
                            // A refused choice shakes the options and leaves the
                            // algorithm exactly where it was.
                            val shake = remember { Animatable(0f) }
                            LaunchedEffect(ui.wrongTick) {
                                if (ui.wrongTick == 0) return@LaunchedEffect
                                shake.snapTo(0f)
                                shake.animateTo(
                                    targetValue = 0f,
                                    animationSpec = keyframes {
                                        durationMillis = 320
                                        0f at 0
                                        -10f at 60
                                        10f at 130
                                        -6f at 200
                                        0f at 320
                                    },
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .offset { IntOffset(shake.value.roundToInt(), 0) },
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            ) {
                                decision.options.forEachIndexed { index, option ->
                                    DecisionButton(
                                        label = option.label,
                                        tone = DecisionTone.forIndex(index),
                                        onClick = { controller.choose(option.action) },
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Teaching and consequence — Try only ───────────────────────
                Guidance(ui, phase) { controller.dismissFeedback() }

                // The learner should be solving, not watching a dashboard, so the
                // run is reported as one quiet line (PRODUCT_SPEC.md §6).
                if (phase == Phase.Challenge && !ui.finished) {
                    Text(
                        text = challengeStatus(ui),
                        style = AlgoType.labelSmall,
                        color = AlgoColors.textMuted,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }

                Gap(Spacing.xxs)
            }

            Controls(
                phase = phase,
                algorithmName = algorithmName,
                ui = ui,
                controller = controller,
                onAdvance = onAdvance,
                onFinishChallenge = { onFinishChallenge(controller) },
                // The free rung goes straight through; anything past it is an
                // offer the learner can decline.
                onHint = {
                    if (ui.hintAccess is HintAccess.Rewarded) {
                        offeringHint = true
                    } else {
                        controller.requestHint()
                    }
                },
            )
        }
    }
}

// ── Sections ──────────────────────────────────────────────────────────────────

/**
 * Try's feedback, rendered.
 *
 * A wrong answer produces this card and **nothing else** — the array above it has
 * not moved, and the same decision is still waiting. Escalation is level-based:
 * point at the evidence, ask the reasoning question, then say it plainly.
 */
@Composable
private fun Guidance(ui: LessonUiState<*>, phase: Phase, onRetry: () -> Unit) {
    // Once the lesson is over the celebration supersedes step feedback; showing
    // both stacks two green cards on top of each other.
    val feedback = ui.feedback.takeUnless { ui.finished }
    AnimatedVisibility(
        visible = feedback != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        val accent = when (feedback) {
            is Feedback.Correct -> AlgoAccent.Green
            else -> AlgoAccent.Violet
        }
        AlgoCard(
            modifier = Modifier.fillMaxWidth(),
            color = if (feedback is Feedback.Correct) {
                AlgoColors.successSoft
            } else {
                AlgoColors.surfaceVariant
            },
            shadow = null,
            border = if (feedback is Feedback.Correct) {
                AlgoColors.success.copy(alpha = 0.28f)
            } else {
                AlgoColors.border
            },
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AlgoIcon(
                            icon = if (feedback is Feedback.Correct) {
                                AlgoIcons.Check
                            } else {
                                AlgoIcons.Bulb
                            },
                            tint = if (feedback is Feedback.Correct) {
                                AlgoColors.success
                            } else {
                                AlgoColors.gold
                            },
                            size = Dimens.secondaryGlyph,
                        )
                        Gap(Spacing.xs)
                        Text(
                            text = feedbackTitle(feedback),
                            style = AlgoType.titleMedium,
                            color = if (feedback is Feedback.Correct) {
                                AlgoColors.onSuccessSoft
                            } else {
                                AlgoColors.primary
                            },
                        )
                    }
                    Gap(Spacing.xs)
                    Text(
                        text = feedback?.body.orEmpty(),
                        style = AlgoType.bodyLarge,
                        color = AlgoColors.textSecondary,
                    )
                    // The specific reason only appears once the nudge has failed —
                    // the first wrong answer should still leave room to reason.
                    val why = (feedback as? Feedback.Wrong)?.takeIf { it.level >= 2 }?.why
                    if (why != null) {
                        Gap(Spacing.xxs)
                        Text(
                            text = why,
                            style = AlgoType.bodyMedium,
                            color = AlgoColors.textMuted,
                        )
                    }
                    // A miscounted middle gets the working, not another nudge.
                    // There is nothing to reason toward in an arithmetic answer,
                    // so withholding it only makes the learner guess.
                    // Try only. Challenge answers a wrong middle with one neutral
                    // clue and records the mistake (PRODUCT_SPEC.md §6); handing
                    // over the working there would be handing over the answer.
                    val midpoint = ui.decision?.midpoint
                        ?.takeIf { feedback is Feedback.Wrong && phase == Phase.Try }
                    if (midpoint != null) {
                        Gap(Spacing.sm)
                        MidpointChip(
                            lo = midpoint.lo,
                            hi = midpoint.hi,
                            mid = midpoint.mid,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    }
                    if (feedback is Feedback.Wrong) {
                        Gap(Spacing.sm)
                        Row {
                            SecondaryButton(
                                label = "Try again",
                                modifier = Modifier.weight(1f),
                                leadingIcon = AlgoIcons.Restart,
                                onClick = onRetry,
                            )
                        }
                    }
                }
                if (feedback is Feedback.Correct) {
                    Gap(Spacing.xs)
                    MascotKing(Modifier.size(Dimens.mascot))
                }
            }
        }
    }
}

private fun feedbackTitle(feedback: Feedback?): String = when (feedback) {
    is Feedback.Correct -> "Correct"
    is Feedback.Hint -> "Hint"
    is Feedback.Wrong -> if (feedback.level >= 3) "Here is the reasoning" else "Not quite"
    null -> ""
}

@Composable
private fun <S : Any, A : Action> Controls(
    phase: Phase,
    algorithmName: String,
    ui: LessonUiState<*>,
    controller: LessonController<S, A>,
    onAdvance: (Phase) -> Unit,
    onFinishChallenge: () -> Unit,
    onHint: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Dimens.screenPadding, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (ui.finished) {
            CelebrationBanner(
                badge = celebrationBadge(ui),
                headline = celebrationHeadline(phase, ui, algorithmName),
                support = celebrationSupport(ui),
                accent = if (ui.outcome is Outcome.Found) AlgoAccent.Green else AlgoAccent.Violet,
                stats = celebrationStats(ui),
            )
            Gap(Spacing.xxs)
        }

        when {
            // The lesson is over — one forward action, lowest on the screen.
            ui.finished && phase == Phase.Challenge -> PrimaryButton(
                label = "See results",
                modifier = Modifier.fillMaxWidth(),
                icon = AlgoIcons.ArrowForward,
                onClick = onFinishChallenge,
            )

            ui.finished && phase == Phase.Try -> PrimaryButton(
                label = "Take the challenge",
                modifier = Modifier.fillMaxWidth(),
                icon = AlgoIcons.ArrowForward,
                onClick = { onAdvance(Phase.Challenge) },
            )

            phase == Phase.Challenge -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                // No Undo in Challenge: there is nothing to undo, because a wrong
                // answer never changed anything.
                SecondaryButton(
                    label = "Restart",
                    modifier = Modifier.weight(1f),
                    leadingIcon = AlgoIcons.Restart,
                    onClick = controller::restart,
                )
                SecondaryButton(
                    // The label says what the tap costs before it is made. A
                    // learner should never discover an ad by pressing a button
                    // that looked free.
                    label = if (ui.hintAccess is HintAccess.Rewarded) "Hint · ad" else "Hint",
                    modifier = Modifier.weight(1f),
                    leadingIcon = AlgoIcons.Bulb,
                    iconTint = AlgoColors.gold,
                    onClick = onHint,
                )
            }

            // No Hint in Try. Try already teaches on every miss — the guidance
            // ladder says more than any hint would, and it arrives without the
            // learner having to admit defeat to ask for it. A hint button here
            // was a second, worse route to the same help.
            else -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                SecondaryButton(
                    label = "Restart",
                    modifier = Modifier.weight(1f),
                    leadingIcon = AlgoIcons.Restart,
                    onClick = controller::restart,
                )
                SecondaryButton(
                    label = "Undo",
                    modifier = Modifier.weight(1f),
                    leadingIcon = AlgoIcons.Undo,
                    iconTint = AlgoColors.accent,
                    onClick = controller::rewind,
                )
            }
        }
    }
}

// ── Copy ──────────────────────────────────────────────────────────────────────

private fun stagesFor(phase: Phase): List<Stage> = Phase.entries.map { stage ->
    Stage(
        label = stage.label,
        state = when {
            stage.ordinal < phase.ordinal -> StageState.Complete
            stage == phase -> StageState.Current
            else -> StageState.Upcoming
        },
    )
}

private fun challengeStatus(ui: LessonUiState<*>): String = buildList {
    add("${ui.metrics.comparisons} checks")
    if (ui.mistakes > 0) add("${ui.mistakes} missed")
    if (ui.hintsUsed > 0) add("${ui.hintsUsed} hints")
}.joinToString(" · ")

private fun stepLabel(phase: Phase, ui: LessonUiState<*>): String = when {
    ui.finished -> phase.label + " complete"
    else -> "${phase.label} · comparison ${ui.step.coerceAtLeast(1)}"
}

private fun headline(phase: Phase, ui: LessonUiState<*>): String = when {
    ui.outcome is Outcome.Found -> "Found it."
    ui.outcome is Outcome.NotFound -> "Not in this array."
    // Try leads with what the algorithm just showed you, then asks. Challenge
    // leads with the question and says nothing else — no "now check the middle".
    ui.decision != null -> when {
        phase == Phase.Challenge -> ui.decision.prompt
        ui.decision.kind == DecisionKind.CELL -> ui.decision.prompt
        else -> ui.narration
    }

    else -> phase.blurb
}

// ── The celebration copy ─────────────────────────────────────────────────────
//
// Generated from the run, so it congratulates something real. A learner who used
// three hints should not be told they nailed it.

private fun celebrationBadge(ui: LessonUiState<*>): String = when {
    ui.outcome is Outcome.NotFound -> "PROVED IT"
    ui.metrics.wrongDecisions == 0 && ui.metrics.hintsUsed == 0 -> "PERFECT"
    else -> "FOUND IT"
}

private fun celebrationHeadline(
    phase: Phase,
    ui: LessonUiState<*>,
    algorithmName: String,
): String = when {
    ui.outcome is Outcome.NotFound -> "Not there — and you proved it."
    // Naming the lesson is worth doing once, at the moment it is finished. It has
    // to be *this* lesson's name, which is why it comes from the pack.
    ui.metrics.wrongDecisions == 0 && ui.metrics.hintsUsed == 0 ->
        if (phase == Phase.Try) "You ran $algorithmName." else "Clean run."

    ui.metrics.wrongDecisions <= 1 -> "You've got this."
    else -> "You got there."
}

/**
 * The closing line is built from what the run actually cost, and it has to work for
 * nine different lessons — so it counts *decisions*, which every lesson has, rather
 * than array positions, which not every lesson has.
 */
private fun celebrationSupport(ui: LessonUiState<*>): String {
    val decisions = ui.metrics.steps
    val checks = ui.metrics.comparisons
    val effort = if (checks > 0) "$checks comparisons" else "$decisions decisions"
    return when {
        ui.outcome is Outcome.NotFound ->
            "Proving something is absent is an answer. That is the half most people miss."

        ui.metrics.wrongDecisions == 0 && ui.metrics.hintsUsed == 0 ->
            "$effort, and not one wrong turn."

        ui.metrics.hintsUsed > 0 ->
            "$effort, with a little help. Next time, cold."

        else -> "$effort. The wrong turns are where it stuck — go back and see why."
    }
}

private fun celebrationStats(ui: LessonUiState<*>): List<String> = buildList {
    // Only what the sentence above did not already say.
    if (ui.metrics.wrongDecisions > 0) {
        add("${ui.metrics.wrongDecisions} wrong turn${if (ui.metrics.wrongDecisions == 1) "" else "s"}")
    }
    if (ui.metrics.hintsUsed > 0) {
        add("${ui.metrics.hintsUsed} hint${if (ui.metrics.hintsUsed == 1) "" else "s"}")
    }
}
