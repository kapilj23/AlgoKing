package com.ttele.algoking.billing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The monetization boundary — ARCHITECTURE.md §10.6.
 *
 * Everything in this file is **pure Kotlin with no Android and no Compose**, for
 * the same reason `AdPolicy` was specified that way (ADR-008): the rules that
 * decide what a learner has paid for are worth more as a truth table than as
 * conditions scattered through screens, and they have to be testable without a
 * device or a store.
 *
 * ### The one rule
 *
 * **Entitlement comes from a verified purchase and from nowhere else.** There is
 * no setter, no cached boolean, and no path from "the learner tapped Unlock" to
 * [ProEntitlement.Pro]. A [BillingGateway] reports what Play says it owns, and
 * `PlayBillingGateway` derives that from `queryPurchasesAsync` rather than from a
 * purchase flow's own report of success.
 */

/** What the learner is entitled to, as reported by the store. */
sealed interface ProEntitlement {

    /** Not asked yet, or the answer has not come back. Never treated as Pro. */
    data object Unknown : ProEntitlement

    /** Asked, and the learner owns nothing. */
    data object Free : ProEntitlement

    /**
     * A verified, active purchase. **Only a [BillingGateway] may produce this**,
     * and only from a purchase the store acknowledged.
     */
    data object Pro : ProEntitlement

    val isPro: Boolean get() = this == Pro
}

/**
 * What can be shown on the paywall right now.
 *
 * The product — its price, its billing period, whether it is the recommended plan
 * — is **always** the store's, never the app's. There is deliberately no default
 * price anywhere in this codebase: a hardcoded one would be wrong in every
 * currency, and wrong about tax, and wrong the first time it changed.
 */
sealed interface BillingState {

    /** Connecting, or querying. The CTA waits. */
    data object Loading : BillingState

    /** A real product, from the store. */
    data class Ready(val product: ProProduct) : BillingState

    /** Nothing can be sold right now, and the paywall has to be honest about it. */
    data class Unavailable(val reason: BillingUnavailable) : BillingState
}

enum class BillingUnavailable {
    /**
     * No billing implementation is wired into this build. The default, and the
     * one that ships today — see [UnconfiguredBillingGateway].
     */
    NOT_CONFIGURED,

    /** Play Billing is unreachable: no Play Store, or an out-of-date one. */
    PLAY_UNAVAILABLE,

    /** Connected, but the store returned no products for the configured ids. */
    NO_PRODUCTS,

    /** A transient failure. Retrying is worth offering. */
    NETWORK,
}

/**
 * One purchasable plan, exactly as the store describes it.
 *
 * Every field is a passthrough of what Play returned. [formattedPrice] is Play's
 * own localised string — never assembled from a number and a currency symbol,
 * because that is how an app ends up showing "$4.99" to someone who will be
 * charged ₹399.
 */
data class ProProduct(
    val id: String,
    val name: String,
    /** Play's localised price string, e.g. `₹399.00`. */
    val formattedPrice: String,
    /** Play's billing period, already turned into words: "per year". */
    val billingPeriod: String,
    /**
     * Whether the store's own configuration marks this as the better-value plan.
     * False unless the product configuration says otherwise — the app never
     * invents a "best value" badge.
     */
    val recommended: Boolean = false,
    /** An offer's free-trial phrase, when the *store* reports one. */
    val trial: String? = null,
)

/** What came back from a purchase attempt. */
sealed interface PurchaseOutcome {
    /** Acknowledged by the store. The only outcome that can change entitlement. */
    data object Purchased : PurchaseOutcome

    /** The learner backed out. Not an error, and never worth an alarming message. */
    data object Cancelled : PurchaseOutcome

    /** The store refused or failed. [message] is for a quiet, factual line. */
    data class Failed(val message: String) : PurchaseOutcome

    /** There was nothing to buy in the first place. */
    data object Unavailable : PurchaseOutcome
}

/** What came back from "restore purchases". */
sealed interface RestoreOutcome {
    data object Restored : RestoreOutcome
    data object NothingToRestore : RestoreOutcome
    data class Failed(val message: String) : RestoreOutcome
}

/**
 * The seam a real Play Billing implementation plugs into.
 *
 * Nothing above this interface knows the Play Billing library exists, and nothing
 * below it knows what a lesson is. Connecting billing for real means writing one
 * implementation of this and constructing it in `MainActivity` instead of
 * [UnconfiguredBillingGateway] — no screen, no repository and no test changes.
 */
interface BillingGateway {

    /**
     * What can be sold, and whether anything can.
     *
     * A flow, because a store answers when it answers: the paywall opens on
     * [BillingState.Loading] and the price arrives afterwards.
     */
    val billing: StateFlow<BillingState>

    /**
     * What the learner owns, as the store reports it.
     *
     * An implementation must derive this from queried, acknowledged purchases —
     * never from a local flag it wrote itself after a tap.
     */
    val entitlement: StateFlow<ProEntitlement>

    /** Launches the store's purchase flow and reports what happened. */
    suspend fun purchase(): PurchaseOutcome

    /** Re-queries owned purchases: a reinstall, a new device, a refunded card. */
    suspend fun restore(): RestoreOutcome

    /** Re-reads both, from the store. Safe to call whenever the app comes back. */
    fun refresh()
}

/**
 * The gateway for a build with no store: it entitles nobody and sells nothing.
 *
 * Play Billing **is** connected now — see `PlayBillingGateway`, which is what the
 * app constructs. This one survives for the two places a store cannot be reached
 * at all: unit tests, and Compose previews. It reports
 * [BillingUnavailable.NOT_CONFIGURED], and like every other path in this package
 * it has no way to produce [ProEntitlement.Pro] — a stub that granted Pro, even in
 * debug, is exactly the fake entitlement this design exists to prevent, and it
 * would be one merge from shipping.
 */
class UnconfiguredBillingGateway : BillingGateway {

    override val billing: StateFlow<BillingState> =
        MutableStateFlow(BillingState.Unavailable(BillingUnavailable.NOT_CONFIGURED))

    /** Never anything else. A build with no store cannot have sold anything. */
    override val entitlement: StateFlow<ProEntitlement> = MutableStateFlow(ProEntitlement.Free)

    override suspend fun purchase(): PurchaseOutcome = PurchaseOutcome.Unavailable

    override suspend fun restore(): RestoreOutcome = RestoreOutcome.NothingToRestore

    override fun refresh() = Unit
}
