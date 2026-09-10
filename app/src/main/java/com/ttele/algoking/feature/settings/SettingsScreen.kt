package com.ttele.algoking.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.ttele.algoking.ui.components.AlgoCard
import com.ttele.algoking.ui.components.AlgoHeader
import com.ttele.algoking.ui.components.AlgoIcon
import com.ttele.algoking.ui.components.AlgoScreen
import com.ttele.algoking.ui.components.Gap
import com.ttele.algoking.ui.components.HeaderTitle
import com.ttele.algoking.ui.components.IconTileButton
import com.ttele.algoking.ui.components.MascotKing
import com.ttele.algoking.ui.icons.AlgoIcons
import com.ttele.algoking.ui.theme.AlgoAccent
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
 * Settings — three things a learner may want, and nothing else.
 *
 * `PRODUCT_SPEC.md` §10 puts settings behind a gear in the **Progress** tab. The
 * MVP has no tabs — Home is the only destination — so the gear lives in Home's
 * trailing header slot, which is the same affordance in the only place there is
 * to put it. When the three-tab IA arrives, this screen moves behind Progress's
 * gear and nothing inside it changes.
 *
 * There is deliberately no preferences list here: sound, theme, motion and
 * notifications are all either not built or not optional yet, and a settings
 * screen full of switches that do nothing is worse than a short one that does
 * three real things.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    versionLabel: String = "",
    /**
     * Whether UMP says this learner must be able to change their ad consent.
     *
     * The SDK decides, from their region and what they were asked. Being able to
     * withdraw consent is part of having asked for it — but a row that opened a
     * form for someone who was never asked would be noise, so the entry point
     * appears exactly when it is required (`docs/ads.md`).
     */
    privacyOptionsRequired: Boolean = false,
    onBack: () -> Unit = {},
    onRate: () -> Unit = {},
    onOpenPrivacy: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onPrivacyOptions: () -> Unit = {},
) {
    AlgoScreen(modifier) {
        Column(Modifier.fillMaxSize()) {
            AlgoHeader(
                modifier = Modifier.statusBarsPadding(),
                leading = { IconTileButton(AlgoIcons.ArrowBack, onClick = onBack) },
                center = { HeaderTitle("Settings") },
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.cardGap),
            ) {
                Gap(Spacing.xxs)

                // The promise, with the mascot beside it — DESIGN_SYSTEM.md §6.11
                // and §6.17. It gives the screen something to open on, and it is
                // the sentence the whole product is built around.
                AlgoCard(
                    modifier = Modifier.fillMaxWidth(),
                    color = AlgoColors.surfaceVariant,
                    shadow = null,
                    border = AlgoColors.border,
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "AlgoKing",
                                style = AlgoType.titleMedium,
                                color = AlgoColors.primary,
                            )
                            Gap(Spacing.xs)
                            Text(
                                text = "Every other app shows you the algorithm.\n" +
                                    "This one makes you run it.",
                                style = AlgoType.bodyLarge,
                                color = AlgoColors.textSecondary,
                            )
                        }
                        Gap(Spacing.xs)
                        MascotKing(Modifier.size(Dimens.mascot))
                    }
                }

                // Full cards rather than a list of rows: this screen has three
                // items, and three thin rows in one card left most of the phone
                // empty. The anatomy is the algorithm card's — gradient tile,
                // title, two lines, chevron — so Home and Settings read as the
                // same app.
                SettingsCard(
                    icon = AlgoIcons.Star,
                    accent = AlgoAccent.Orange,
                    title = "Rate AlgoKing",
                    description = "A minute of your time helps more than you would think.",
                    onClick = onRate,
                )
                SettingsCard(
                    icon = AlgoIcons.Shield,
                    accent = AlgoAccent.Violet,
                    title = "Privacy policy",
                    description = "What the app keeps on your phone, and what it never collects.",
                    onClick = onOpenPrivacy,
                )
                SettingsCard(
                    icon = AlgoIcons.Info,
                    accent = AlgoAccent.Blue,
                    title = "About the app",
                    description = "What AlgoKing is for, and how a lesson actually works.",
                    onClick = onOpenAbout,
                )

                // Only when UMP says it is required — which is the only time it
                // has anything to open.
                if (privacyOptionsRequired) {
                    SettingsCard(
                        icon = AlgoIcons.Shield,
                        accent = AlgoAccent.Green,
                        title = "Ad privacy options",
                        description = "Change the advertising choices you made for this app.",
                        onClick = onPrivacyOptions,
                    )
                }

                Gap(Spacing.xs)
            }

            // Pinned, so the screen ends on a line rather than on whatever space
            // is left over.
            if (versionLabel.isNotBlank()) {
                Text(
                    text = versionLabel,
                    style = AlgoType.labelSmall,
                    color = AlgoColors.textMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = Spacing.md),
                )
            }
        }
    }
}

/**
 * One tappable card, built like the algorithm card on Home: a 56dp accent-gradient
 * tile with a white glyph, the title, one description line, and the chevron that
 * means "this opens something".
 */
@Composable
private fun SettingsCard(
    icon: ImageVector,
    accent: AlgoAccent,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    AlgoCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interaction, null, onClick = onClick),
        color = if (pressed) AlgoColors.primarySurface else AlgoColors.surface,
        shadow = if (pressed) Elevation.pressed else Elevation.card,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(Dimens.algorithmIconTile)
                    .algoShadow(Elevation.card, Radius.icon)
                    .background(AlgoGradients.accentTile(accent), Radius.icon),
                contentAlignment = Alignment.Center,
            ) {
                AlgoIcon(icon, AlgoColors.onPrimary, Dimens.algorithmIconGlyph)
            }
            Gap(Spacing.sm)
            Column(Modifier.weight(1f)) {
                Text(title, style = AlgoType.titleMedium, color = AlgoColors.textPrimary)
                Gap(Spacing.xxs)
                Text(
                    text = description,
                    style = AlgoType.bodyMedium,
                    color = AlgoColors.textSecondary,
                    minLines = 2,
                    maxLines = 2,
                )
            }
            Gap(Spacing.xs)
            AlgoIcon(AlgoIcons.ChevronRight, AlgoColors.disabled, Dimens.secondaryGlyph)
        }
    }
}

/**
 * A page of prose — Privacy and About are the same shape, so they are the same
 * composable with different content.
 *
 * Each section is a card: the heading in `primary` and the body in
 * `textSecondary`, which is the `ExplanationCard` treatment from
 * DESIGN_SYSTEM.md §6.11 applied to a page instead of a lesson beat.
 */
@Composable
fun InfoPage(
    title: String,
    lead: String,
    sections: List<InfoSection>,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    AlgoScreen(modifier) {
        Column(Modifier.fillMaxSize()) {
            AlgoHeader(
                modifier = Modifier.statusBarsPadding(),
                leading = { IconTileButton(AlgoIcons.ArrowBack, onClick = onBack) },
                center = { HeaderTitle(title) },
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = Dimens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.cardGap),
            ) {
                Text(
                    text = lead,
                    style = AlgoType.headlineLarge,
                    color = AlgoColors.textPrimary,
                    modifier = Modifier.padding(vertical = Spacing.xs),
                )

                sections.forEach { section ->
                    AlgoCard(
                        modifier = Modifier.fillMaxWidth(),
                        color = AlgoColors.surfaceVariant,
                        shadow = null,
                        border = AlgoColors.border,
                    ) {
                        Column {
                            Text(
                                text = section.heading,
                                style = AlgoType.titleMedium,
                                color = AlgoColors.primary,
                            )
                            Gap(Spacing.xs)
                            Text(
                                text = section.body,
                                style = AlgoType.bodyLarge,
                                color = AlgoColors.textSecondary,
                            )
                        }
                    }
                }

                Gap(Spacing.lg)
            }
        }
    }
}

data class InfoSection(val heading: String, val body: String)

/**
 * The privacy policy, in full, in the app.
 *
 * It is text rather than a link on purpose: the app makes no network calls, so a
 * policy the learner cannot read offline would be the only part of the product
 * that needs a connection.
 *
 * **This is only true while it is true.** AdMob and Firebase Analytics are both
 * specified (`PRODUCT_SPEC.md` §9, `ARCHITECTURE.md` §10.4) and neither is built.
 * The day either lands, this copy has to be rewritten and a hosted policy URL
 * added for the Play listing — it is not a detail that can be left to drift.
 */
@Composable
fun PrivacyPolicyScreen(modifier: Modifier = Modifier, onBack: () -> Unit = {}) {
    InfoPage(
        title = "Privacy",
        lead = "Everything stays on your phone.",
        modifier = modifier,
        onBack = onBack,
        sections = listOf(
            InfoSection(
                heading = "What AlgoKing stores",
                body = "Which lessons you have finished — one mark for Watch and one for Try, " +
                    "per algorithm. That is the whole of it, and it is held in the app's own " +
                    "private storage on this device.",
            ),
            InfoSection(
                heading = "What it never collects",
                body = "No account with us, no name, no email address, no contacts and no " +
                    "location. The app has no analytics of its own and never reads what you " +
                    "do in a lesson.",
            ),
            InfoSection(
                heading = "What it sends",
                body = "The lessons send nothing. Every lesson runs on the device, which is " +
                    "why they all work in airplane mode.\n\nTwo Google services are the " +
                    "exception. AlgoKing Pro: opening the paywall asks Google Play for the " +
                    "price, and buying or restoring goes through Google Play, which handles " +
                    "the payment and tells this app one thing back — whether a subscription " +
                    "is active. We never see your payment details.",
            ),
            InfoSection(
                heading = "Advertising",
                body = "Free learners see one full-screen ad after finishing the practice " +
                    "stage of a lesson, and nowhere else — no banners, no rewarded ads, and " +
                    "nothing during a lesson. Google's ad service uses your device's " +
                    "advertising ID to choose and measure those ads; you can reset or delete " +
                    "that ID in Android's privacy settings.\n\nPro subscribers see no ads at " +
                    "all.",
            ),
            InfoSection(
                heading = "Removing your data",
                body = "Clearing the app's storage, or uninstalling it, deletes your progress " +
                    "permanently. There is no copy anywhere else, so there is nothing to ask " +
                    "us to delete.",
            ),
            InfoSection(
                heading = "Your subscription",
                body = "A Pro subscription belongs to your Google account, not to this app, " +
                    "and is managed and cancelled in the Play Store. Reinstalling restores it " +
                    "with Restore purchases.",
            ),
            InfoSection(
                heading = "Children",
                body = "The app is rated for ages 13 and over. It collects nothing from anyone, " +
                    "of any age.",
            ),
        ),
    )
}

/** What the app is, for someone deciding whether to keep it. */
@Composable
fun AboutScreen(
    versionLabel: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    InfoPage(
        title = "About",
        lead = "Every other app shows you the algorithm. This one makes you run it.",
        modifier = modifier,
        onBack = onBack,
        sections = listOf(
            InfoSection(
                heading = "How a lesson works",
                body = "Watch is a walkthrough you advance yourself — no autoplay, no speed " +
                    "control. Try hands you the same algorithm and asks you to make its " +
                    "decisions. A wrong answer never moves the algorithm: it stays exactly " +
                    "where it was and explains itself until you have it.",
            ),
            InfoSection(
                heading = "Nothing is scored",
                body = "Try has no stars, no timer and no failure state, so being wrong costs " +
                    "you nothing. A lesson is complete when you have driven it, not when you " +
                    "have performed well.",
            ),
            InfoSection(
                heading = "Twenty-one lessons",
                body = "Eleven are free: searching, the six sorts, and the four data " +
                    "structures. Ten are the Advanced shelf — the graph algorithms, the " +
                    "trees and their traversals — and those are AlgoKing Pro.",
            ),
            InfoSection(
                heading = "What free means here",
                body = "Every free lesson is complete: both stages, the full guidance, and " +
                    "your progress. No coins, no energy, no leaderboard and no login. There " +
                    "is one ad, after you finish a lesson's practice stage — never during " +
                    "one. Pro adds lessons and removes that ad; it never takes anything away " +
                    "from the free ones. $versionLabel",
            ),
        ),
    )
}

/**
 * Opens the app's Play listing, preferring the store app and falling back to the
 * browser. Kept out of the composable so the screen stays a function of its
 * arguments (ARCHITECTURE.md §2).
 */
fun openPlayStoreListing(context: Context) {
    val id = context.packageName
    val store = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$id"))
    val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$id"))
    try {
        context.startActivity(store)
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(web)
        } catch (_: ActivityNotFoundException) {
            // No store and no browser. Nothing to do, and nothing worth telling
            // the learner about a button they will not miss.
        }
    }
}

/** `1.0 (1)`, read at runtime so the screen cannot claim a version it is not. */
@Composable
fun rememberVersionLabel(): String {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "Version ${info.versionName}"
        }.getOrDefault("")
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SettingsScreenPreview() {
    AlgoKingTheme { SettingsScreen(versionLabel = "Version 1.0") }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun PrivacyPolicyPreview() {
    AlgoKingTheme { PrivacyPolicyScreen() }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun AboutPreview() {
    AlgoKingTheme { AboutScreen(versionLabel = "Version 1.0") }
}
