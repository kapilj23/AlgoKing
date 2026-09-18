package com.algorithms.algoking.billing

/**
 * The Play Billing rules, as pure Kotlin — ARCHITECTURE.md §10.6.
 *
 * `PlayBillingGateway` is the only file that knows the Play Billing library
 * exists, and it is also the one file that cannot be unit-tested without a store
 * and a device. So the two decisions inside it that are actually *rules* live
 * here instead, over types of this app's own:
 *
 *  1. **which receipt entitles a learner** — and, just as importantly, which does
 *     not: a `PENDING` receipt is a payment still in flight, and it is not one;
 *  2. **which purchase option is the one this app sells.**
 *
 * The gateway maps Play's `Purchase` and `ProductDetails` into [Receipt] and
 * [PurchaseOption] at its boundary and then asks the questions here. That is the
 * same call ADR-008 made for the ad rules: a truth table is worth more as a
 * truth table than as conditions inside a callback nobody can run on a laptop.
 *
 * **Nothing here can produce [ProEntitlement.Pro] out of nothing** — every answer
 * is a function of the receipts Play returned, which is the invariant ADR-041
 * rests on, restated at the only layer that decides it.
 */

/** What Play says the state of a receipt is. Mirrors `Purchase.PurchaseState`. */
internal enum class ReceiptState {
    /** Paid for and complete. The only state that entitles anybody. */
    PURCHASED,

    /**
     * A payment in flight — cash at a counter, or a parental approval. It becomes
     * `PURCHASED` when it clears, or it goes away. **Never entitling.**
     */
    PENDING,

    /** Play could not say. Treated exactly as `PENDING` is: not entitling. */
    UNSPECIFIED,
}

/** One owned receipt, reduced to the four things this app reads off it. */
internal data class Receipt(
    val products: List<String>,
    val state: ReceiptState,
    val acknowledged: Boolean,
    /** Play's purchase token — what an acknowledgement is sent against. */
    val token: String = "",
)

/**
 * One purchase option of a one-time product, as Play describes it.
 *
 * A product configured with purchase options reports one of these per option;
 * a legacy one-time product reports a single one whose [purchaseOptionId] is
 * null. Both shapes are handled by [BillingRules.selectPurchaseOption].
 */
internal data class PurchaseOption(
    val purchaseOptionId: String?,
    /** What the purchase flow is launched against. Empty for a legacy product. */
    val offerToken: String,
    /** Play's own localised price string. Never assembled by this app. */
    val formattedPrice: String,
    val offerTags: List<String> = emptyList(),
)

internal object BillingRules {

    /**
     * The receipts that entitle a learner to [productId].
     *
     * Two conditions, both required: the store says `PURCHASED`, and the receipt
     * names this product. Anything else — pending, unspecified, a receipt for
     * something else entirely — is not an entitlement and is not treated as one.
     */
    fun entitling(receipts: List<Receipt>, productId: String): List<Receipt> =
        receipts.filter { it.state == ReceiptState.PURCHASED && productId in it.products }

    /**
     * What the learner owns, given what the store returned.
     *
     * This is a **total function of the store's answer**, which is what makes
     * "the purchase flow said success but the store owns nothing" grant nothing
     * rather than be a case nobody thought about.
     */
    fun entitlement(receipts: List<Receipt>, productId: String): ProEntitlement =
        if (entitling(receipts, productId).isEmpty()) ProEntitlement.Free else ProEntitlement.Pro

    /**
     * The receipts that still need acknowledging.
     *
     * Play refunds any purchase left unacknowledged for three days, so every new
     * `PURCHASED` receipt is acknowledged as soon as it is seen — including ones
     * that arrive from outside a purchase flow, which is what the query on start
     * is for. A pending receipt is deliberately not acknowledged: there is
     * nothing to acknowledge until it clears.
     */
    fun toAcknowledge(receipts: List<Receipt>, productId: String): List<Receipt> =
        entitling(receipts, productId).filterNot { it.acknowledged }

    /** Whether a payment for [productId] is still in flight. */
    fun isPending(receipts: List<Receipt>, productId: String): Boolean =
        receipts.any {
            productId in it.products &&
                (it.state == ReceiptState.PENDING || it.state == ReceiptState.UNSPECIFIED)
        }

    /**
     * The purchase option this app sells, or null when it is not configured.
     *
     * The app names exactly one option — `buy`
     * ([PlayBillingGateway.PRO_PURCHASE_OPTION_ID]) — and will sell that one or
     * nothing. **A near miss is not substituted for it**: if Play returns options
     * and none of them is the configured one, that is a Play Console problem, and
     * an app that quietly charged for a different option than the one it was
     * built against would be a worse answer than a paywall that says it cannot
     * sell anything right now.
     *
     * The one accommodation is for a **legacy one-time product**, created before
     * purchase options existed: Play reports a single offer with no option id at
     * all, and there is no ambiguity about which one is meant.
     */
    fun selectPurchaseOption(
        options: List<PurchaseOption>,
        purchaseOptionId: String,
    ): PurchaseOption? {
        options.firstOrNull { it.purchaseOptionId == purchaseOptionId }?.let { return it }

        val legacy = options.singleOrNull()
        return legacy?.takeIf { it.purchaseOptionId.isNullOrBlank() }
    }
}
