package com.algorithms.algoking.billing

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
 *
 * ### What is sold
 *
 * **One one-time product, `algoking_pro`, bought once and owned permanently.** It
 * is not a subscription: there is no billing period, no renewal, no trial and no
 * expiry, and the purchase is never consumed — consuming it would make Play forget
 * the learner owns it, which is precisely the opposite of a permanent unlock. What
 * ownership *can* still do is go away, on a refund, which is why entitlement is
 * read from the store every time and never written to disk.
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
 * The product — its price, and whether the store marks it as recommended — is
 * **always** the store's, never the app's. There is deliberately no default price
 * anywhere in this codebase: a hardcoded one would be wrong in every currency, and
 * wrong about tax, and wrong the first time it changed.
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

    /**
     * Connected, and the store has nothing sellable for the configured id: the
     * product is missing, inactive, or carries no purchase option matching the one
     * this app sells. Almost always a Play Console problem rather than a device one.
     */
    NO_PRODUCTS,

    /** A transient failure. Retrying is worth offering. */
    NETWORK,
}

/**
 * The one purchasable thing, exactly as the store describes it.
 *
 * [formattedPrice] is Play's own localised string — never assembled from a number
 * and a currency symbol, because that is how an app ends up showing "$4.99" to
 * someone who will be charged ₹399. **No price, currency or amount appears
 * anywhere in this codebase**; until the store answers, the paywall says so rather
 * than guessing.
 */
data class ProProduct(
    val id: String,
    val name: String,
    /** Play's localised price string, e.g. `₹299.00`. Passed through untouched. */
    val formattedPrice: String,
    /**
     * The line under the price.
     *
     * For a one-time product this says what *kind* of purchase it is rather than
     * how often it recurs, because it does not recur. It was `billingPeriod` while
     * Pro was a subscription, and a field still called that while holding
     * "one-time purchase" is the sort of small untruth that eventually persuades
     * someone to put a renewal date on a screen that has none.
     */
    val priceDetail: String,
    /**
     * Whether the store's own configuration marks this as the recommended option.
     * False unless an offer tag says so — the app never invents a badge.
     */
    val recommended: Boolean = false,
)

/** What came back from a purchase attempt. */
sealed interface PurchaseOutcome {
    /** Acknowledged by the store. The only outcome that can change entitlement. */
    data object Purchased : PurchaseOutcome

    /**
     * The payment is in flight and has not completed — cash at a counter, or a
     * parental approval.
     *
     * **Neither a failure nor a success**, and its own case for exactly that
     * reason: calling it a failure tells a learner who is about to be charged that
     * nothing happened, and calling it a success hands out Pro for a payment that
     * may never clear. Nothing is unlocked; the receipt becomes entitling if and
     * when Play reports `PURCHASED`.
     */
    data object Pending : PurchaseOutcome

    /** The learner backed out. Not an error, and never worth an alarming message. */
    data object Cancelled : PurchaseOutcome

    /** The store refused or failed. [message] is for a quiet, factual line. */
    data class Failed(val message: String) : PurchaseOutcome

    /** There was nothing to buy in the first place. */
    data object Unavailable : PurchaseOutcome
}

/**
 * *This learner just bought Pro, here, now.*
 *
 * An **event**, deliberately carrying nothing. It is not a state, not a flag and
 * not something that can be asked about later — it happens once, it is delivered
 * once, and then it is over. The state question, *"is this learner Pro?"*, is
 * [ProEntitlement]'s and is answered from the store every time; this says only that
 * the answer changed because of a purchase the learner just completed, which is the
 * one circumstance worth congratulating.
 *
 * Emitted by `SubscriptionRepository.purchase` and by nothing else. A restore, a
 * reinstall, the startup query and a reconnect all produce the same *entitlement*
 * and none of them produce this.
 */
data object ProUnlocked

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
