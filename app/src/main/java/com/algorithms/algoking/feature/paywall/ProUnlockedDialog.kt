package com.algorithms.algoking.feature.paywall

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.algorithms.algoking.ui.components.AlgoCard
import com.algorithms.algoking.ui.components.AlgoIcon
import com.algorithms.algoking.ui.components.Gap
import com.algorithms.algoking.ui.components.PrimaryButton
import com.algorithms.algoking.ui.icons.AlgoIcons
import com.algorithms.algoking.ui.theme.AlgoColors
import com.algorithms.algoking.ui.theme.AlgoGradients
import com.algorithms.algoking.ui.theme.AlgoKingTheme
import com.algorithms.algoking.ui.theme.AlgoType
import com.algorithms.algoking.ui.theme.Dimens
import com.algorithms.algoking.ui.theme.Elevation
import com.algorithms.algoking.ui.theme.Radius
import com.algorithms.algoking.ui.theme.Spacing
import com.algorithms.algoking.ui.theme.algoShadow

/**
 * The receipt — shown once, when a purchase has genuinely completed.
 *
 * ### Why it exists
 *
 * Everything about the unlock already worked: the store reported `PURCHASED`, the
 * purchase was acknowledged, entitlement was re-read, and the paywall closed
 * straight into the lesson the learner had tapped. What was missing was the app
 * saying so. A payment that ends with the screen quietly changing leaves the
 * learner asking the one question a paid product must never leave open — *did that
 * go through?* — and sending them to check their Play receipts is not an answer.
 *
 * ### What it is not
 *
 * Not a gate, and not a sales moment. There is nothing to upsell, nothing to
 * confirm and nothing to decide: by the time this appears the lessons are already
 * unlocked **behind it**, which is the point. So it carries one button, it can be
 * dismissed any way a learner reaches for, and it never reappears.
 *
 * ### It is AlgoKing, not a store notification
 *
 * The same white card at the same 20dp radius, the same crown tile the paywall
 * opened with, the same heavy 800-weight heading, the same `PrimaryButton`. Gold
 * because gold is this app's ornament — the wordmark's crown, the streak bolt, the
 * `PRO` pill (`DESIGN_SYSTEM.md` §6.3a) — and because the learner has only ever
 * seen it mean *nice*. No confetti, no sound, no green tick: success green is
 * reserved for *status* in the algorithm canvas (`DESIGN_SYSTEM.md` §0.1) and
 * spending it here would put a lesson colour on a billing screen.
 *
 * The one motion is `CelebrationBanner`'s spring — the language the app already
 * uses for the moment a learner lands something.
 */
@Composable
fun ProUnlockedDialog(
    modifier: Modifier = Modifier,
    onStartLearning: () -> Unit = {},
) {
    Dialog(
        onDismissRequest = onStartLearning,
        properties = DialogProperties(
            // Dismissible both ways, on purpose. The unlock has already happened
            // and nothing is being asked, so trapping the learner in an
            // acknowledgement would be friction bought with no information. Back
            // press and a tap outside both mean what the button means.
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            // The card takes the app's own 16dp gutter rather than the platform's
            // dialog width, so it is inset exactly as every other screen is
            // (`DESIGN_SYSTEM.md` §3) instead of at whatever margin the OS picks.
            usePlatformDefaultWidth = false,
        ),
    ) {
        ProUnlockedCard(modifier = modifier, onStartLearning = onStartLearning)
    }
}

/**
 * The card itself, separately so it can be previewed — a `Dialog` renders empty in
 * a `@Preview`, which makes the one visual check that matters here impossible.
 */
@Composable
private fun ProUnlockedCard(
    modifier: Modifier = Modifier,
    onStartLearning: () -> Unit = {},
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.92f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "proUnlockedScale",
    )

    AlgoCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.screenPadding)
            .scale(scale),
        padding = Dimens.cardPadding,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Gap(Spacing.xs)

            // The crown the paywall opened with, at the same geometry. The learner
            // is meant to recognise it: this is the thing they tapped.
            Box(
                modifier = Modifier
                    .size(Dimens.paywallCrown)
                    .algoShadow(Elevation.card, Radius.card)
                    .background(AlgoGradients.goldTile(), Radius.card),
                contentAlignment = Alignment.Center,
            ) {
                AlgoIcon(AlgoIcons.Crown, AlgoColors.onPrimary, Dimens.paywallCrownGlyph)
            }

            Gap(Spacing.md)

            Text(
                text = "You're All Set! 🎉",
                style = AlgoType.headlineLarge,
                color = AlgoColors.textPrimary,
                textAlign = TextAlign.Center,
            )

            Gap(Spacing.xs)

            Text(
                text = "AlgoKing Pro has been unlocked successfully.",
                style = AlgoType.bodyLarge,
                color = AlgoColors.textSecondary,
                textAlign = TextAlign.Center,
            )

            Gap(Spacing.xxs)

            Text(
                text = "All Pro algorithms and interactive lessons are now unlocked.",
                style = AlgoType.bodyMedium,
                color = AlgoColors.textMuted,
                textAlign = TextAlign.Center,
            )

            Gap(Spacing.lg)

            PrimaryButton(
                label = "Start Learning",
                onClick = onStartLearning,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(widthDp = 360)
@Composable
private fun ProUnlockedCardPreview() {
    AlgoKingTheme { ProUnlockedCard() }
}
