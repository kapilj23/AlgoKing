package com.ttele.algoking.feature.paywall

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.ttele.algoking.billing.BillingState
import com.ttele.algoking.billing.BillingUnavailable
import com.ttele.algoking.billing.ProProduct
import com.ttele.algoking.billing.PurchaseOutcome
import com.ttele.algoking.ui.components.AlgoCard
import com.ttele.algoking.ui.components.AlgoHeader
import com.ttele.algoking.ui.components.AlgoIcon
import com.ttele.algoking.ui.components.AlgoScreen
import com.ttele.algoking.ui.components.Gap
import com.ttele.algoking.ui.components.HeaderTitle
import com.ttele.algoking.ui.components.IconTileButton
import com.ttele.algoking.ui.components.PrimaryButton
import com.ttele.algoking.ui.components.ProBadge
import com.ttele.algoking.ui.components.SecondaryButton
import com.ttele.algoking.ui.icons.AlgoIcons
import com.ttele.algoking.ui.theme.AlgoColors
import com.ttele.algoking.ui.theme.AlgoGradients
import com.ttele.algoking.ui.theme.AlgoKingTheme
import com.ttele.algoking.ui.theme.AlgoType
import com.ttele.algoking.ui.theme.Dimens
import com.ttele.algoking.ui.theme.Elevation
import com.ttele.algoking.ui.theme.Radius
import com.ttele.algoking.ui.theme.Spacing
import com.ttele.algoking.ui.theme.algoShadow

/**
 * The paywall — what Pro is, what it costs, and the way out.
 *
 * ### What this screen refuses to do
 *
 * No countdown, no strike-through "discount", no invented user counts, no
 * "master DSA in 7 days". Every one of those is available and every one would
 * cost more trust than it earns — and this app's entire pitch is that it is
 * honest about how learning works. The screen is the same cards, the same
 * buttons and the same gold ornament as the rest of AlgoKing, because it is part
 * of AlgoKing and not an ad wearing its clothes.
 *
 * **There is no price in this file.** The whole product — its price string, its
 * billing period, whether it is a recommended plan, whether there is a trial —
 * arrives as a [ProProduct] from the store. When nothing can be sold the screen
 * says so plainly and the CTA will not pretend otherwise.
 *
 * ### It is contextual, and then it is complete
 *
 * Tapping Dijkstra opens this with *"Unlock Dijkstra's Algorithm"* at the top,
 * because a paywall that does not say why it appeared reads as a trap. Under that
 * headline it is the full offer: what Pro includes, and what it costs.
 */
@Composable
fun PaywallScreen(
    billing: BillingState,
    modifier: Modifier = Modifier,
    /** The lesson that triggered this, when one did. */
    triggeringAlgorithm: String? = null,
    lastOutcome: PurchaseOutcome? = null,
    purchasing: Boolean = false,
    onBack: () -> Unit = {},
    onUnlock: () -> Unit = {},
    onRestore: () -> Unit = {},
    onRetry: () -> Unit = {},
    onPrivacy: () -> Unit = {},
) {
    AlgoScreen(modifier) {
        Column(Modifier.fillMaxSize()) {
            AlgoHeader(
                modifier = Modifier.statusBarsPadding(),
                leading = { IconTileButton(AlgoIcons.ArrowBack, onClick = onBack) },
                center = { HeaderTitle("AlgoKing Pro") },
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.screenPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Hero(triggeringAlgorithm)

                Gap(Dimens.sectionGap)
                Includes()

                Gap(Dimens.cardGap)
                PlanCard(billing, onRetry)

                lastOutcome?.let {
                    Gap(Spacing.sm)
                    OutcomeLine(it)
                }

                Gap(Spacing.lg)
            }

            // The forward action is the lowest thing on the screen, as it is on
            // every other screen in the app (DESIGN_SYSTEM.md §7).
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = Dimens.screenPadding, vertical = Spacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val sellable = billing is BillingState.Ready
                PrimaryButton(
                    label = if (purchasing) "Opening Google Play…" else "Unlock Pro",
                    modifier = Modifier.fillMaxWidth(),
                    icon = AlgoIcons.Crown,
                    // Disabled rather than hidden: the offer is real, and the
                    // reason it cannot be taken right now is stated above.
                    enabled = sellable && !purchasing,
                    onClick = onUnlock,
                )
                Gap(Spacing.xs)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    SecondaryButton(
                        label = "Restore purchases",
                        modifier = Modifier.weight(1f),
                        leadingIcon = AlgoIcons.Restart,
                        onClick = onRestore,
                    )
                    SecondaryButton(
                        label = "Privacy",
                        modifier = Modifier.weight(1f),
                        leadingIcon = AlgoIcons.Shield,
                        onClick = onPrivacy,
                    )
                }
                Gap(Spacing.sm)
                // The way out, always, and never disguised as anything else.
                Text(
                    text = "Not ready? Continue with the free algorithms",
                    style = AlgoType.bodyMedium,
                    color = AlgoColors.textMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickableText(onBack),
                )
            }
        }
    }
}

/** The crown, the promise, and — when there is one — the lesson that asked. */
@Composable
private fun Hero(triggeringAlgorithm: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Gap(Spacing.sm)
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

        if (triggeringAlgorithm != null) {
            // Why this screen appeared, in the learner's own words: they tapped
            // this lesson.
            Text(
                text = "Unlock $triggeringAlgorithm",
                style = AlgoType.displayLarge,
                color = AlgoColors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Gap(Spacing.xxs)
            ProBadge()
            Gap(Spacing.xs)
            Text(
                text = "It is one of the ten advanced lessons in AlgoKing Pro.",
                style = AlgoType.bodyLarge,
                color = AlgoColors.textSecondary,
                textAlign = TextAlign.Center,
            )
        } else {
            Text(
                text = "Unlock AlgoKing Pro",
                style = AlgoType.displayLarge,
                color = AlgoColors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Gap(Spacing.xs)
            Text(
                text = "Go beyond the fundamentals with advanced algorithms and " +
                    "interactive practice.",
                style = AlgoType.bodyLarge,
                color = AlgoColors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** What Pro includes. Four lines, each one a thing the app actually does. */
@Composable
private fun Includes() {
    AlgoCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = "What Pro includes",
                style = AlgoType.titleMedium,
                color = AlgoColors.primary,
            )
            Gap(Spacing.sm)
            listOf(
                "10 advanced algorithms",
                "Interactive WATCH and TRY for every one",
                "Graph and tree algorithms",
                "Learn by driving the algorithm, not memorising it",
                "No ads, anywhere in the app",
                "Your progress, kept on your device",
                // A plan, not a promise of a date: new lessons have arrived at a
                // steady rate and are expected to keep doing so, and saying more
                // than that would be selling something not yet built.
                "More advanced algorithms are on the way",
            ).forEach { line ->
                TickLine(line)
            }
        }
    }
}

@Composable
private fun TickLine(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlgoIcon(AlgoIcons.Check, AlgoColors.success, Dimens.tickGlyph)
        Gap(Spacing.xs)
        Text(
            text = text,
            style = AlgoType.bodyLarge,
            color = AlgoColors.textSecondary,
        )
    }
}

/**
 * The plan, entirely as the store describes it.
 *
 * Every branch here is a real state the learner can be in, and none of them
 * invents a number. `NOT_CONFIGURED` is the one that ships today: billing is not
 * connected yet, and saying so is better than showing a price nobody will be
 * charged.
 */
@Composable
private fun PlanCard(billing: BillingState, onRetry: () -> Unit) {
    AlgoCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            when (billing) {
                is BillingState.Loading -> {
                    Text(
                        text = "AlgoKing Pro",
                        style = AlgoType.titleMedium,
                        color = AlgoColors.textPrimary,
                    )
                    Gap(Spacing.xxs)
                    Text(
                        text = "Checking the price with Google Play…",
                        style = AlgoType.bodyMedium,
                        color = AlgoColors.textMuted,
                    )
                }

                is BillingState.Ready -> ReadyPlan(billing.product)

                is BillingState.Unavailable -> {
                    Text(
                        text = "AlgoKing Pro",
                        style = AlgoType.titleMedium,
                        color = AlgoColors.textPrimary,
                    )
                    Gap(Spacing.xxs)
                    Text(
                        text = unavailableLine(billing.reason),
                        style = AlgoType.bodyMedium,
                        color = AlgoColors.textSecondary,
                    )
                    if (billing.reason != BillingUnavailable.NOT_CONFIGURED) {
                        Gap(Spacing.sm)
                        SecondaryButton(
                            label = "Try again",
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = AlgoIcons.Restart,
                            onClick = onRetry,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadyPlan(product: ProProduct) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = product.name,
                    style = AlgoType.titleMedium,
                    color = AlgoColors.textPrimary,
                )
                // Only ever from the store's own configuration.
                if (product.recommended) {
                    Gap(Spacing.xs)
                    Box(
                        modifier = Modifier
                            .background(AlgoColors.successSoft, Radius.pill)
                            .padding(
                                horizontal = Dimens.badgePadding,
                                vertical = Spacing.xxs,
                            ),
                    ) {
                        Text(
                            text = "BEST VALUE",
                            style = AlgoType.labelSmall,
                            color = AlgoColors.onSuccessSoft,
                        )
                    }
                }
            }
            product.trial?.let {
                Gap(Spacing.xxs)
                Text(it, style = AlgoType.bodyMedium, color = AlgoColors.textMuted)
            }
        }
        Gap(Spacing.sm)
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = product.formattedPrice,
                style = AlgoType.numeralMedium,
                color = AlgoColors.primary,
            )
            Text(
                text = product.billingPeriod,
                style = AlgoType.labelSmall,
                color = AlgoColors.textMuted,
            )
        }
    }
}

private fun unavailableLine(reason: BillingUnavailable): String = when (reason) {
    // Only reachable in a build with no store at all: unit tests and previews.
    BillingUnavailable.NOT_CONFIGURED ->
        "Pro cannot be purchased in this build."

    BillingUnavailable.PLAY_UNAVAILABLE ->
        "Google Play is not available on this device, so Pro cannot be purchased here."

    BillingUnavailable.NO_PRODUCTS ->
        "The subscription could not be loaded from Google Play. Please try again later."

    BillingUnavailable.NETWORK ->
        "No connection to Google Play. Check your network and try again."
}

/** One quiet line under the CTA. Never a dialog, and never an alarm. */
@Composable
private fun OutcomeLine(outcome: PurchaseOutcome) {
    val (text, colour) = when (outcome) {
        is PurchaseOutcome.Purchased ->
            "Purchase complete — Pro is unlocked." to AlgoColors.onSuccessSoft

        is PurchaseOutcome.Cancelled ->
            "Purchase cancelled. Nothing was charged." to AlgoColors.textMuted

        is PurchaseOutcome.Failed ->
            "That did not go through: ${outcome.message}" to AlgoColors.error

        is PurchaseOutcome.Unavailable ->
            "There is nothing to purchase yet." to AlgoColors.textMuted
    }
    Text(
        text = text,
        style = AlgoType.bodyMedium,
        color = colour,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * A text link with the app's ripple-free press — the way out is a sentence, not a
 * button, so it can never compete with the one CTA above it.
 */
@Composable
private fun Modifier.clickableText(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
        .padding(vertical = Spacing.xs)
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun PaywallPreview() {
    AlgoKingTheme {
        PaywallScreen(
            billing = BillingState.Unavailable(BillingUnavailable.NOT_CONFIGURED),
            triggeringAlgorithm = "Dijkstra",
        )
    }
}
