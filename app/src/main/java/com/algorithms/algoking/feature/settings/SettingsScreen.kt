package com.algorithms.algoking.feature.settings

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
import com.algorithms.algoking.ui.components.AlgoCard
import com.algorithms.algoking.ui.components.AlgoHeader
import com.algorithms.algoking.ui.components.AlgoIcon
import com.algorithms.algoking.ui.components.AlgoScreen
import com.algorithms.algoking.ui.components.Gap
import com.algorithms.algoking.ui.components.HeaderTitle
import com.algorithms.algoking.ui.components.IconTileButton
import com.algorithms.algoking.ui.components.MascotKing
import com.algorithms.algoking.ui.components.SecondaryButton
import com.algorithms.algoking.ui.icons.AlgoIcons
import com.algorithms.algoking.ui.theme.AlgoAccent
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
    /** Drawn under the last section. Privacy uses it to link the published copy. */
    footer: (@Composable () -> Unit)? = null,
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

                // Below the prose, when a page has somewhere else to send you.
                // Null for About, which does not.
                footer?.invoke()

                Gap(Spacing.lg)
            }
        }
    }
}

data class InfoSection(val heading: String, val body: String)

/**
 * The date this policy last changed.
 *
 * Update it whenever the copy in [PrivacyPolicyScreen] changes — **and update the
 * published policy at [POLICY_URL] the same day.** Two policies dated differently
 * is the sort of thing that gets noticed, and the published one is what the Play
 * listing points at.
 */
const val POLICY_EFFECTIVE_DATE: String = "25 September 2026"

/** Where privacy questions go. Also printed on the published policy. */
const val POLICY_CONTACT: String = "irislabs46@gmail.com"

/**
 * The published policy — what the Play listing links to.
 *
 * The same text as [PrivacyPolicyScreen], hosted so the store has a URL it can
 * reach. The in-app copy stays the primary one for a learner: the lessons make no
 * network calls, so a policy that needed a connection to read would be the only
 * part of the product that did.
 */
const val POLICY_URL: String = "https://sites.google.com/view/algoking-privacy/home"

/**
 * The privacy policy, in full, in the app.
 *
 * It is text rather than a link on purpose: the lessons make no network calls, so
 * a policy the learner cannot read offline would be the only part of the product
 * that needs a connection.
 *
 * ### It describes what the code does, and it is checked against it
 *
 * Every claim below traces to something in this repository — the permissions the
 * merged manifest actually carries, the two DataStore files, the one ad placement,
 * the billing gateway's return type, and what Play's review API does and does not
 * hand back. Where a third-party SDK processes something the app itself never
 * touches, the copy says so rather than rounding it down to "we collect nothing".
 *
 * **Keep it that way.** The copy that shipped before this one said the app had no
 * ads while AdMob was already built, and said progress existed nowhere but the
 * device while Android Auto Backup was copying it to the learner's Drive. Both
 * were written when they were true and neither was revisited. When the ad policy,
 * the billing product, the review trigger, the stored keys or the backup rules
 * change, this text and `web/privacy-policy/index.html` change the same day.
 *
 * The hosted copy at `web/privacy-policy/` is the same substance for the Play
 * listing, which requires a URL. **It is not deployed yet.**
 */
@Composable
fun PrivacyPolicyScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    /** Opens [POLICY_URL]. The page below is the same text, readable offline. */
    onOpenOnline: () -> Unit = {},
) {
    InfoPage(
        title = "Privacy",
        lead = "Effective $POLICY_EFFECTIVE_DATE",
        modifier = modifier,
        onBack = onBack,
        footer = {
            // The published copy, for anyone who wants the version the store
            // links to — or a page they can send someone. The policy itself is
            // already above, in full, so this is an alternative rather than the
            // way to read it: nothing here requires a connection.
            SecondaryButton(
                label = "View the published policy",
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = AlgoIcons.Shield,
                onClick = onOpenOnline,
            )
        },
        sections = listOf(
            InfoSection(
                heading = "What AlgoKing does",
                body = "AlgoKing teaches data structures and algorithms by letting you run " +
                    "them yourself. Every lesson runs on your device, which is why they all " +
                    "work in airplane mode.\n\nThis policy explains what the app stores, what " +
                    "it does not, and which Google services handle things we never see.",
            ),
            InfoSection(
                heading = "Information AlgoKing collects",
                body = "AlgoKing does not operate a user account or a backend database. " +
                    "There is no login, no profile, and no server of ours holding anything " +
                    "about you.\n\nThe app itself does not collect:\n\n" +
                    "• your name, email address or phone number\n" +
                    "• your location, contacts, photos, audio or files\n" +
                    "• anything you type — there is no text box anywhere in the app\n" +
                    "• your card, bank or billing details\n" +
                    "• analytics or crash reports of our own\n\n" +
                    "It has no analytics service, no crash-reporting service, and sends " +
                    "nothing to any AI service while you use it.\n\nThat is not the same as " +
                    "saying no information is ever processed. The Google services described " +
                    "below process some information when they run, and the sections that " +
                    "follow say what and by whom.",
            ),
            InfoSection(
                heading = "Information stored on your device",
                body = "Two things, both in the app's own private storage:\n\n" +
                    "• Learning progress — which lesson stages you have finished, as one " +
                    "mark for Watch and one for Try per algorithm. Stored so the app can " +
                    "show how far you have got and reopen a lesson where you left it.\n" +
                    "• Review prompt state — whether the app has already used its one " +
                    "automatic request to rate it. Stored so that it does not ask you " +
                    "again.\n\n" +
                    "Neither is an account or a profile. Neither contains your name, an " +
                    "email address, or anything else that identifies you, and neither is " +
                    "sent to us.",
            ),
            InfoSection(
                heading = "Android backup",
                body = "Android can back up app data so a new phone picks up where the old " +
                    "one left off. AlgoKing leaves that enabled, so the two items above — " +
                    "your progress and the review flag — may be included in your device's " +
                    "backup and restored when you set up a new device.\n\n" +
                    "That backup is provided by Android and Google, not by us. It goes to " +
                    "your own Google account, we cannot read it, and whether it happens at " +
                    "all is controlled by the backup settings on your device and in your " +
                    "Google account.",
            ),
            InfoSection(
                heading = "Advertising and Google AdMob",
                body = "AlgoKing uses Google AdMob. If you have not bought Pro, you may see " +
                    "one full-screen (interstitial) ad after you finish the practice stage " +
                    "of a lesson — and nowhere else.\n\n" +
                    "Interstitial is the only ad format the app uses. There are no banner " +
                    "ads, no rewarded ads, no native ads, no app-open ads, no ads inside a " +
                    "lesson, and none on Home or Settings.\n\n" +
                    "Pro learners see no advertisements at all.\n\n" +
                    "The app does not itself read or store your advertising ID. Google's " +
                    "advertising SDK processes advertising identifiers and device and app " +
                    "signals in order to deliver and measure ads, to limit how often you see " +
                    "one, and — where your consent and settings permit — to personalise " +
                    "them. You can reset or delete your advertising ID in Android's privacy " +
                    "settings.",
            ),
            InfoSection(
                heading = "Privacy choices and Google UMP",
                body = "AlgoKing uses Google's User Messaging Platform to manage consent and " +
                    "privacy choices for advertising where they apply.\n\n" +
                    "The app asks UMP about your consent state when it starts, and requests " +
                    "an ad only when that state allows one. Whether you are shown a consent " +
                    "message is Google's decision, based on your region and the requirements " +
                    "that apply there.\n\nWhere an ongoing choice is required, an \"Ad " +
                    "privacy options\" entry appears in this app's Settings so you can change " +
                    "it later. Pro learners are not shown a consent message, because they " +
                    "are not shown ads.",
            ),
            InfoSection(
                heading = "AlgoKing Pro and Google Play Billing",
                body = "AlgoKing Pro (product algoking_pro) is a one-time purchase: bought " +
                    "once, owned permanently, with nothing to renew or cancel. It unlocks the " +
                    "advanced algorithms and their interactive lessons, and removes " +
                    "advertising.\n\n" +
                    "Google Play processes the payment. AlgoKing has no payment system " +
                    "and does not receive or store your card number, bank details or full " +
                    "billing information.\n\nWhat the app receives from Google Play is only " +
                    "what it needs to decide whether to unlock Pro: the product, the purchase " +
                    "state, the purchase token, and whether the purchase has been " +
                    "acknowledged.\n\nThe purchase is associated with your Google account by " +
                    "Google Play rather than held by us, so reinstalling or moving to a new " +
                    "device restores it with Restore purchases.",
            ),
            InfoSection(
                heading = "In-app reviews",
                body = "AlgoKing uses Google Play's official In-App Review. After you finish " +
                    "a lesson it may ask, once, whether you would like to rate the app. The " +
                    "automatic request is deliberately limited: once the app has launched the " +
                    "review flow it records a local flag and does not ask again.\n\n" +
                    "Google Play decides whether the review screen is actually shown, and " +
                    "runs it entirely. AlgoKing does not receive your star rating, does not " +
                    "receive anything you write, and cannot tell whether you submitted a " +
                    "review at all. The only thing it knows is that it launched the flow.\n\n" +
                    "Settings also has a \"Rate AlgoKing\" option, which opens the app's " +
                    "Google Play listing if you want to leave a review yourself.",
            ),
            InfoSection(
                heading = "Third-party services",
                body = "Five, all provided by Google:\n\n" +
                    "• Google AdMob — advertising\n" +
                    "• Google User Messaging Platform — consent and privacy choices for " +
                    "advertising\n" +
                    "• Google Play Billing — processing the Pro purchase\n" +
                    "• Google Play In-App Review — the optional review prompt\n" +
                    "• Android / Google backup — backing up and restoring the app data " +
                    "described above\n\n" +
                    "Their handling of information is governed by Google's privacy policy at " +
                    "policies.google.com/privacy. AlgoKing uses no other third-party services " +
                    "and no advertising networks besides Google's.",
            ),
            InfoSection(
                heading = "Data deletion",
                body = "You can remove AlgoKing's locally stored data by uninstalling the " +
                    "app, or by clearing its storage in Android's app settings. That removes " +
                    "the learning progress and the review flag from the device.\n\n" +
                    "There is no AlgoKing account to delete and no database of ours holding a " +
                    "profile of you, so there is no server-side deletion request to send " +
                    "us.\n\nCopies held in your Android backup are controlled through your " +
                    "own device and Google backup settings. Purchase records are held by " +
                    "Google Play. Neither is an AlgoKing database, and we cannot delete " +
                    "Google's records on your behalf.",
            ),
            InfoSection(
                heading = "Data security",
                body = "AlgoKing relies on reasonable, standard technical measures: " +
                    "Android's application sandbox, which keeps the stored data private to " +
                    "the app; the platform's own security controls, including device " +
                    "encryption; and HTTPS for the third-party services above, which manage " +
                    "their own connections.\n\nThe app does not add encryption of its own on " +
                    "top of that. No system can be guaranteed completely secure — though " +
                    "there is also very little stored here, which is the most useful thing " +
                    "that can be said about it.",
            ),
            InfoSection(
                heading = "Children's privacy",
                body = "AlgoKing is an educational data structures and algorithms " +
                    "application. It is not specifically designed or directed toward young " +
                    "children, has no features aimed at them, and does not collect personal " +
                    "information from anyone of any age.\n\nThe age rating that applies to " +
                    "the app is shown on its Google Play listing.",
            ),
            InfoSection(
                heading = "Changes to this privacy policy",
                body = "If what the app does changes, this policy is updated to match and " +
                    "the effective date at the top is changed. The current version is always " +
                    "the one in the app and at the published policy address.",
            ),
            InfoSection(
                heading = "Contact us",
                body = "Questions about privacy or about this policy:\n\n$POLICY_CONTACT",
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

/**
 * Opens the published privacy policy in a browser.
 *
 * Kept out of the composable for the reason [openPlayStoreListing] is: opening
 * another app is the platform's business, and the screen stays a function of its
 * arguments (ARCHITECTURE.md §2).
 *
 * If there is no browser to handle it, nothing happens — the whole policy is
 * already on the screen the learner is looking at, so there is nothing to tell
 * them and nothing they have missed.
 */
fun openPrivacyPolicyOnline(context: Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(POLICY_URL)))
    } catch (_: ActivityNotFoundException) {
        // No browser. The policy above is the same text.
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
