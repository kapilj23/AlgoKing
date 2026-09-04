package com.ttele.algoking.feature.mission

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import com.ttele.algoking.ads.InstantRewardedAdHost
import com.ttele.algoking.ads.Reward
import com.ttele.algoking.ads.RewardedPlacement
import com.ttele.algoking.engine.challenge.Boundary
import com.ttele.algoking.engine.challenge.Comparison
import com.ttele.algoking.engine.challenge.HintAccess
import com.ttele.algoking.engine.challenge.MissionHint
import com.ttele.algoking.engine.challenge.MissionRun
import com.ttele.algoking.engine.challenge.MissionStep
import com.ttele.algoking.engine.challenge.Verdict
import com.ttele.algoking.engine.scenario.Mission
import com.ttele.algoking.feature.lesson.HintUnlockDialog
import com.ttele.algoking.ui.components.AlgoCard
import com.ttele.algoking.ui.components.AlgoHeader
import com.ttele.algoking.ui.components.AlgoScreen
import com.ttele.algoking.ui.components.DecisionButton
import com.ttele.algoking.ui.components.DecisionTone
import com.ttele.algoking.ui.components.Gap
import com.ttele.algoking.ui.components.HeaderTitle
import com.ttele.algoking.ui.components.IconTileButton
import com.ttele.algoking.ui.components.PrimaryButton
import com.ttele.algoking.ui.components.SecondaryButton
import com.ttele.algoking.ui.icons.AlgoIcons
import com.ttele.algoking.ui.theme.AlgoColors
import com.ttele.algoking.ui.theme.AlgoGradients
import com.ttele.algoking.ui.theme.AlgoType
import com.ttele.algoking.ui.theme.AlgoViz
import com.ttele.algoking.ui.theme.Dimens
import com.ttele.algoking.ui.theme.Elevation
import com.ttele.algoking.ui.theme.Radius
import com.ttele.algoking.ui.theme.Spacing
import com.ttele.algoking.ui.theme.algoShadow

/**
 * The Binary Search Challenge — a warehouse, not an array.
 *
 * Three gates per round, and the learner passes all three:
 *
 *  1. **find the middle** — tap the box at the centre of the live range;
 *  2. **compare** — is the target below, at, or above what that box holds;
 *  3. **move the boundary** — tap the box that becomes the new edge.
 *
 * There is deliberately no LEFT / RIGHT pair of buttons. Two buttons can be
 * beaten by tapping one and, if refused, tapping the other; a boundary that has
 * to land on `mid + 1` cannot. The off-by-one is the part of Binary Search
 * people actually get wrong, so it is the part this asks for.
 *
 * Nothing here decides what is correct. Every tap goes to [MissionRun], which
 * asks the real engine — and a refused tap leaves the search exactly where it
 * was.
 */
@Composable
fun MissionChallengeScreen(
    mission: Mission,
    algorithmName: String,
    modifier: Modifier = Modifier,
    /** Change to restart the same mission from the top. */
    attempt: Int = 0,
    onBack: () -> Unit = {},
    onComplete: (MissionRun) -> Unit = {},
    onStageComplete: () -> Unit = {},
) {
    val run = remember(mission, attempt) { MissionRun(mission) }
    var snapshot by remember(mission, attempt) { mutableStateOf(run.snapshot()) }
    var feedback by remember(mission, attempt) { mutableStateOf<String?>(null) }
    var hint by remember(mission, attempt) { mutableStateOf<MissionHint?>(null) }
    var offeringHint by remember(mission, attempt) { mutableStateOf(false) }
    val adHost = remember { InstantRewardedAdHost() }
    val scroll = rememberScrollState()

    fun settle(verdict: Verdict, whenWrong: String) {
        when (verdict) {
            // Feedback names what to think about, never what to tap — the hint
            // ladder is opt-in and this must not pre-empt it.
            Verdict.Wrong -> feedback = whenWrong
            Verdict.Right -> { feedback = null; hint = null }
            Verdict.Ignored -> Unit
        }
        snapshot = run.snapshot()
    }

    // Finishing records the progress immediately — solving it is what counts,
    // whatever it cost. It does *not* navigate: the learner has earned the
    // moment of seeing the found box on the shelf, and being thrown straight to
    // a scoreboard takes that away. "See results" is their tap to make.
    LaunchedEffect(snapshot.finished) {
        if (snapshot.finished) onStageComplete()
    }

    if (offeringHint) {
        HintUnlockDialog(
            onWatchAd = {
                offeringHint = false
                adHost.show(RewardedPlacement.EXTRA_HINT) { reward ->
                    if (reward is Reward.Earned) {
                        hint = run.takeHint()
                        snapshot = run.snapshot()
                    }
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
                center = { HeaderTitle(algorithmName) },
                trailing = { TargetChip(mission.targetLabel) },
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scroll)
                    .padding(horizontal = Dimens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.cardGap),
            ) {
                MissionStrip(mission, snapshot.remaining, mission.size)

                AlgoCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            text = prompt(snapshot.step, snapshot.comparison),
                            style = AlgoType.headlineLarge,
                            color = AlgoColors.textPrimary,
                        )
                        Gap(Spacing.md)

                        ProductShelf(
                            mission = mission,
                            left = snapshot.left,
                            right = snapshot.right,
                            mid = snapshot.mid,
                            selectable = snapshot.selectableSlots,
                            onTap = { slot ->
                                when (snapshot.step) {
                                    MissionStep.FIND_MID -> settle(
                                        run.chooseMid(slot),
                                        "Not the middle. Look at where the live " +
                                            "range starts and ends, not the whole shelf.",
                                    )

                                    MissionStep.MOVE_BOUNDARY -> {
                                        // Which side of the opened box the tap
                                        // lands on says which boundary is moving,
                                        // so one tap carries both the direction
                                        // and the off-by-one.
                                        val m = snapshot.mid ?: return@ProductShelf
                                        val boundary =
                                            if (slot > m) Boundary.LEFT else Boundary.RIGHT
                                        settle(
                                            run.moveBoundary(boundary, slot),
                                            "Not that edge. Think about which part of a " +
                                                "sorted shelf can still hold the target — " +
                                                "and whether the box you just opened is " +
                                                "still in play.",
                                        )
                                    }

                                    else -> Unit
                                }
                            },
                        )

                        if (snapshot.step == MissionStep.COMPARE) {
                            Gap(Spacing.md)
                            ComparisonRow(
                                targetLabel = mission.targetLabel,
                                midLabel = snapshot.mid?.let(mission::label).orEmpty(),
                                onChoose = { choice ->
                                    settle(
                                        run.chooseComparison(choice),
                                        "Read the two numbers again: the target, and the " +
                                            "id on the box you just opened.",
                                    )
                                },
                            )
                        }
                    }
                }

                // The moment the mission lands, said on the shelf where the found
                // box is still visible — before any scoreboard.
                AnimatedVisibility(
                    visible = snapshot.finished,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    NoticeCard(
                        title = "Mission complete",
                        body = mission.targetLabel + " found after opening " +
                            snapshot.trail.size +
                            if (snapshot.trail.size == 1) " box." else " boxes.",
                        tone = NoticeTone.Success,
                    )
                }

                AnimatedVisibility(
                    visible = feedback != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    NoticeCard(
                        title = "Not quite",
                        body = feedback.orEmpty(),
                        tone = NoticeTone.Correction,
                    )
                }

                AnimatedVisibility(
                    visible = hint != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    NoticeCard(
                        title = if (hint?.rule == true) "The rule" else "Think about this",
                        body = hint?.body.orEmpty(),
                        tone = NoticeTone.Hint,
                    )
                }

                Gap(Spacing.xxs)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = Dimens.screenPadding, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                if (snapshot.finished) {
                    PrimaryButton(
                        label = "See results",
                        modifier = Modifier.fillMaxWidth(),
                        icon = AlgoIcons.ArrowForward,
                        onClick = { onComplete(run) },
                    )
                } else {
                    SecondaryButton(
                        label = when (snapshot.hintAccess) {
                            HintAccess.Rewarded -> "Hint · ad"
                            HintAccess.Exhausted -> "Hint"
                            else -> "Hint"
                        },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = AlgoIcons.Bulb,
                        iconTint = AlgoColors.gold,
                        onClick = {
                            // Only the second rung costs anything, and only when
                            // the learner asks for it by name.
                            if (snapshot.hintAccess is HintAccess.Rewarded) {
                                offeringHint = true
                            } else {
                                hint = run.takeHint()
                                snapshot = run.snapshot()
                            }
                        },
                    )
                }
            }
        }
    }
}

// ── Pieces ────────────────────────────────────────────────────────────────────

/**
 * The shelf.
 *
 * Boxes keep their size and wrap onto as many rows as they need — a product id
 * that has to be squinted at is worse than a second row. Discarded stock stays
 * on the shelf, dimmed and shrunk, because *seeing* the ruled-out half is the
 * lesson.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProductShelf(
    mission: Mission,
    left: Int,
    right: Int,
    mid: Int?,
    selectable: Set<Int>,
    onTap: (Int) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(
            Dimens.missionBoxGap,
            Alignment.CenterHorizontally,
        ),
        verticalArrangement = Arrangement.spacedBy(Dimens.missionBoxGap),
    ) {
        mission.dataset.values.indices.forEach { slot ->
            ProductBox(
                label = mission.label(slot),
                inRange = slot in left..right,
                isMid = slot == mid,
                tag = when {
                    slot == mid -> "MID"
                    slot == left && left <= right -> "LEFT"
                    slot == right && left <= right -> "RIGHT"
                    else -> ""
                },
                selectable = slot in selectable,
                onClick = { onTap(slot) },
            )
        }
    }
}

@Composable
private fun ProductBox(
    label: String,
    inRange: Boolean,
    isMid: Boolean,
    tag: String,
    selectable: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(
        targetValue = if (inRange) 1f else 0.86f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "boxScale",
    )
    val fade by animateFloatAsState(
        targetValue = if (inRange) 1f else 0.4f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "boxFade",
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(Dimens.missionBoxWidth)
                .height(Dimens.missionBoxHeight)
                .scale(scale)
                .alpha(fade)
                .then(
                    if (isMid) Modifier.algoShadow(Elevation.raised, Radius.sceneCell) else Modifier,
                )
                .background(
                    if (isMid) {
                        AlgoGradients.bar(AlgoViz.comparingTop, AlgoViz.comparingBottom)
                    } else if (inRange) {
                        AlgoGradients.bar(AlgoColors.surface, AlgoColors.surface)
                    } else {
                        AlgoGradients.bar(AlgoViz.eliminated, AlgoViz.eliminated)
                    },
                    Radius.sceneCell,
                )
                .border(
                    width = if (selectable) Dimens.outlineStrong else Dimens.outline,
                    color = when {
                        isMid -> AlgoViz.comparing
                        selectable -> AlgoColors.primary
                        inRange -> AlgoColors.primary.copy(alpha = 0.30f)
                        else -> AlgoColors.borderStrong.copy(alpha = 0.4f)
                    },
                    shape = Radius.sceneCell,
                )
                .then(
                    if (selectable) {
                        Modifier.clickable(
                            interactionSource = interaction,
                            indication = null,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📦", style = AlgoType.labelSmall)
                Text(
                    text = label,
                    style = AlgoType.sceneNumeral,
                    color = if (isMid) AlgoColors.onPrimary else AlgoColors.textPrimary,
                    maxLines = 1,
                )
            }
        }
        Gap(Spacing.xxs)
        // The tag rides with its box, so it still points at the right one after
        // the shelf wraps onto another row.
        Text(
            text = tag,
            style = AlgoType.labelSmall,
            color = if (isMid) AlgoViz.comparing else AlgoViz.pointer,
            maxLines = 1,
        )
    }
}

@Composable
private fun ComparisonRow(
    targetLabel: String,
    midLabel: String,
    onChoose: (Comparison) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(targetLabel, style = AlgoType.numeralMedium, color = AlgoColors.primary)
            Gap(Spacing.sm)
            Text("?", style = AlgoType.numeralMedium, color = AlgoColors.textSecondary)
            Gap(Spacing.sm)
            Text(midLabel, style = AlgoType.numeralMedium, color = AlgoViz.comparing)
        }
        Gap(Spacing.sm)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            // Three equal-weight halves of one decision, in the same component
            // the SWAP/KEEP pair uses. The glyphs read against the
            // "target ? mid" line directly above, so no option needs more words
            // than another — and none can be styled as the recommended answer.
            DecisionButton(
                label = "<",
                tone = DecisionTone.First,
                onClick = { onChoose(Comparison.LESS) },
            )
            DecisionButton(
                label = "=",
                tone = DecisionTone.Third,
                onClick = { onChoose(Comparison.EQUAL) },
            )
            DecisionButton(
                label = ">",
                tone = DecisionTone.Second,
                onClick = { onChoose(Comparison.GREATER) },
            )
        }
    }
}

@Composable
private fun MissionStrip(mission: Mission, remaining: Int, total: Int) {
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
            // The shrinking search space, stated plainly. This is the efficiency
            // lesson, and it belongs where the learner can watch it fall.
            Text(
                text = "$remaining of $total ${mission.subject} still possible",
                style = AlgoType.titleSmall,
                color = AlgoColors.textPrimary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TargetChip(label: String) {
    Row(
        modifier = Modifier
            .background(AlgoColors.primarySoft, Radius.pill)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🎯", style = AlgoType.labelSmall)
        Gap(Spacing.xxs)
        Text(label, style = AlgoType.titleSmall, color = AlgoColors.primary, maxLines = 1)
    }
}

private enum class NoticeTone { Correction, Hint, Success }

@Composable
private fun NoticeCard(title: String, body: String, tone: NoticeTone) {
    AlgoCard(
        modifier = Modifier.fillMaxWidth(),
        color = if (tone == NoticeTone.Success) {
            AlgoColors.successSoft
        } else {
            AlgoColors.surfaceVariant
        },
        shadow = null,
        border = if (tone == NoticeTone.Success) {
            AlgoColors.success.copy(alpha = 0.28f)
        } else {
            AlgoColors.border
        },
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = AlgoType.titleMedium,
                color = when (tone) {
                    NoticeTone.Hint -> AlgoColors.gold
                    NoticeTone.Success -> AlgoColors.onSuccessSoft
                    NoticeTone.Correction -> AlgoColors.primary
                },
            )
            Gap(Spacing.xs)
            Text(text = body, style = AlgoType.bodyLarge, color = AlgoColors.textSecondary)
        }
    }
}

private fun prompt(step: MissionStep, comparison: Comparison?): String = when (step) {
    MissionStep.FIND_MID -> "Find the middle of the range you are still searching."
    MissionStep.COMPARE -> "How does the target compare with the box you opened?"
    // Neutral on purpose: naming the surviving half here would answer the
    // question the step exists to ask.
    MissionStep.MOVE_BOUNDARY -> "Set the new boundary. Tap the box that becomes " +
        "the new edge of the range you will search next."

    MissionStep.DONE -> "Found it."
}
