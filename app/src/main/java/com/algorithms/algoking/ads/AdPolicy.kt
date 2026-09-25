package com.algorithms.algoking.ads

import com.algorithms.algoking.billing.ProEntitlement

/**
 * When an ad may be shown — and it is almost never.
 *
 * Pure Kotlin, no Android, no Compose. `ARCHITECTURE.md` §10.3 always specified
 * the ad rules this way (ADR-008): they are subtle, getting one wrong costs more
 * product damage than a missed impression earns, and scattering them across
 * navigation callbacks guarantees drift. So they are a truth table, and they are
 * tested as one.
 *
 * ### The whole policy
 *
 * > **One interstitial, for a free learner, after they finish TRY. Nowhere else.**
 *
 * There is no banner, no rewarded ad, no native ad, no app-open ad, and no second
 * placement — not on Home, not on Settings, not on navigation, not on launch, and
 * never inside WATCH or TRY. [Placement] has exactly one member on purpose: a new
 * placement cannot be added by writing a call site, only by editing this file,
 * which is where the argument about whether it should exist belongs.
 */
enum class Placement {
    /**
     * The learner has finished TRY and the Complete screen has shown them how the
     * run went. The lesson is over; nothing is interrupted.
     */
    LESSON_COMPLETE,
}

/** Why an ad was not shown. Logged, so the rules can be verified in production. */
enum class AdSuppressed {
    /** A Pro learner. The most important one in the list. */
    PRO,

    /**
     * The store has not said yet what the learner owns.
     *
     * At every launch there is a window where `queryPurchasesAsync` has not come
     * back and entitlement is [ProEntitlement.Unknown]. Unknown is not Pro — but it
     * is not evidence of *Free* either, and showing an ad on that basis is showing
     * one to a learner who may well have paid to never see it, purely because the
     * app asked before the answer arrived.
     *
     * So the rule is the conservative one in both directions, and it matches what
     * `ProAccess` already does with the same value: an unresolved entitlement never
     * unlocks a paid lesson, and it never shows an ad either. Loading one is still
     * fine — that is `mayRequestAds`' business, and an ad fetched for someone who
     * turns out to be Pro is discarded rather than shown.
     */
    ENTITLEMENT_UNKNOWN,

    /** This completion has already had its one opportunity. */
    ALREADY_SHOWN_FOR_COMPLETION,

    /** Nothing is loaded. The learner is never made to wait for one. */
    NOT_READY,
}

sealed interface AdDecision {
    data object Show : AdDecision
    data class Suppress(val reason: AdSuppressed) : AdDecision
}

object AdPolicy {

    /**
     * Whether an ad may be **requested from the network at all**.
     *
     * A different question from [decide], and asked earlier. Two things have to be
     * true before a single request goes out:
     *
     * - the learner is not a subscriber — a Pro learner never sees an ad, so
     *   fetching one would be traffic spent on something that cannot be shown;
     * - UMP says consent allows it (`ConsentManager.canRequestAds`), which is
     *   false until the consent state is known, and stays false if the learner
     *   declined or the check failed.
     *
     * Nothing else in the app requests an ad, so this is the whole gate.
     */
    fun mayRequestAds(entitlement: ProEntitlement, canRequestAds: Boolean): Boolean =
        !entitlement.isPro && canRequestAds

    /**
     * Whether the one interstitial may be shown right now.
     *
     * @param entitlement what the learner owns, from the billing layer — there is
     *   no second Pro flag anywhere (ADR-041).
     * @param completionId identifies the finished run. One completion gets at most
     *   one opportunity, however many times Compose recomposes.
     * @param lastShownForCompletion the completion the last interstitial belonged
     *   to, or null if none has been shown.
     * @param adReady whether an interstitial is actually loaded.
     */
    fun decide(
        placement: Placement,
        entitlement: ProEntitlement,
        completionId: Int,
        lastShownForCompletion: Int?,
        adReady: Boolean,
    ): AdDecision = when {
        // Pro is checked first, so a paying learner cannot be shown an ad by any
        // combination of the conditions below — including one that was already
        // loaded before they bought.
        entitlement.isPro -> AdDecision.Suppress(AdSuppressed.PRO)

        // And second: an ad is shown only when the store has actually said the
        // learner is Free. "Not yet known" is not the same as "does not own Pro",
        // and the difference is a paying learner being advertised at during the
        // seconds after a cold start.
        entitlement != ProEntitlement.Free ->
            AdDecision.Suppress(AdSuppressed.ENTITLEMENT_UNKNOWN)

        completionId == lastShownForCompletion ->
            AdDecision.Suppress(AdSuppressed.ALREADY_SHOWN_FOR_COMPLETION)

        // Learning never waits for an ad. If nothing is loaded, the learner goes
        // on with their day and the next one loads in the background.
        !adReady -> AdDecision.Suppress(AdSuppressed.NOT_READY)

        else -> AdDecision.Show
    }
}
