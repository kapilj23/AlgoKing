package com.algorithms.algoking.devtools

import com.algorithms.algoking.BuildConfig
import com.algorithms.algoking.billing.ProEntitlement

/**
 * Lets a **debug** build pretend to be Free or Pro, so both paths can be tested.
 *
 * A sideloaded build is not the Play-signed app, so `queryPurchasesAsync` reports
 * nothing and entitlement sits at [ProEntitlement.Unknown] forever. Unknown opens
 * no Pro lesson (ADR-041) and — since ADR-057 — shows no ad either, so without
 * this a debug build can exercise *neither* of the two states worth checking.
 *
 * ### How to switch
 *
 * In `local.properties`, which is git-ignored and untracked:
 *
 * ```properties
 * algoking.debug.entitlement=FREE     # ads show, paid lessons locked
 * algoking.debug.entitlement=PRO      # no ads, everything unlocked
 * algoking.debug.entitlement=STORE    # no override — whatever Play says
 * ```
 *
 * Then rebuild. `STORE` is the default, so a fresh clone behaves like production
 * until someone deliberately says otherwise.
 *
 * ### What this is not
 *
 * **It is not a purchase, and it does not touch billing.** Nothing here writes to
 * `SubscriptionRepository`, and `PlayBillingGateway` is not involved: the store is
 * still queried, still answers, and its answer is still the only thing a release
 * build can see. This substitutes a value at one call site, in one build type, for
 * the benefit of whoever is holding the phone.
 *
 * Its release counterpart in `src/release/` returns the store's answer unchanged
 * and has no switch at all — see that file for why the separation is a source set
 * rather than an `if`.
 */
object DebugEntitlement {

    /**
     * The store's answer, or the one this build was told to pretend.
     *
     * @param actual what `SubscriptionRepository` reports. Returned unchanged
     *   unless `local.properties` asked for an override.
     */
    fun override(actual: ProEntitlement): ProEntitlement =
        applyOverride(BuildConfig.DEBUG_ENTITLEMENT, actual)

    /** Whether this build can pretend. It can, but only if configured to. */
    const val IS_AVAILABLE: Boolean = true

    /**
     * What this build is pretending to be, or null when it is not pretending.
     *
     * Worth surfacing somewhere visible if a debug session ever gets confusing —
     * "why is this locked?" has exactly one answer, and it is this.
     */
    val activeLabel: String?
        get() = when (BuildConfig.DEBUG_ENTITLEMENT) {
            FREE -> "DEBUG: forced FREE"
            PRO -> "DEBUG: forced PRO"
            else -> null
        }

    /**
     * The rule, separated from the build-time constant so it can be tested.
     *
     * Reading `BuildConfig` directly in a test would assert whatever the machine's
     * `local.properties` happens to say, which is a test of the developer's
     * configuration rather than of this logic.
     */
    internal fun applyOverride(setting: String, actual: ProEntitlement): ProEntitlement =
        when (setting) {
            FREE -> ProEntitlement.Free
            PRO -> ProEntitlement.Pro
            // Anything else — `STORE`, a blank, a typo — means do not pretend.
            // Failing towards the real answer is the only safe default here.
            else -> actual
        }

    private const val FREE = "FREE"
    private const val PRO = "PRO"
}
