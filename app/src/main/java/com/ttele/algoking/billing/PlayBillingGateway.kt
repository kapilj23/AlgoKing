package com.ttele.algoking.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Play Billing, behind the seam — ARCHITECTURE.md §10.6.
 *
 * This is the only file in the app that knows the Play Billing library exists.
 * Above it, `SubscriptionRepository` sees two flows and two suspend functions;
 * below it, nothing knows what a lesson is.
 *
 * ### Entitlement is queried, never inferred
 *
 * [entitlement] changes in exactly one place — [applyPurchases], which is fed by
 * `queryPurchasesAsync`. A completed purchase flow does **not** set it; the flow
 * finishing triggers a re-query, and it is the query's answer that counts. That is
 * what makes "the store said success but owns nothing" a case that grants nothing
 * rather than a case nobody thought about (ADR-041).
 *
 * A purchase is entitling only when the store reports `PURCHASED`. `PENDING` — a
 * cash payment or a parental approval still in flight — is not, and is left to
 * become one when it clears.
 *
 * ### Acknowledgement
 *
 * Play refunds any purchase that is not acknowledged within three days, so every
 * new `PURCHASED` receipt is acknowledged here as soon as it is seen — including
 * ones that arrive from outside a purchase flow, which is what the query on start
 * is for.
 */
class PlayBillingGateway(
    context: Context,
    private val scope: CoroutineScope,
    /** The Play Console product this app sells. See [PRO_PRODUCT_ID]. */
    private val productId: String = PRO_PRODUCT_ID,
) : BillingGateway {

    private val _billing = MutableStateFlow<BillingState>(BillingState.Loading)
    override val billing: StateFlow<BillingState> = _billing.asStateFlow()

    /** Unknown until the store answers. Unknown is never treated as entitled. */
    private val _entitlement = MutableStateFlow<ProEntitlement>(ProEntitlement.Unknown)
    override val entitlement: StateFlow<ProEntitlement> = _entitlement.asStateFlow()

    /** The offer being sold, kept only so the flow can be launched against it. */
    private var offer: SelectedOffer? = null

    /**
     * The activity the purchase flow is launched from. Held as long as the gateway
     * lives, which is the composition of the one activity this app has.
     */
    private var host: Activity? = context as? Activity

    /** Bridges the asynchronous purchase callback back to the suspend function. */
    private val purchaseUpdates = Channel<PurchaseUpdate>(Channel.CONFLATED)

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        // Play reconnects the service itself; without this every transient
        // disconnect becomes an error the learner has to retry past.
        .enableAutoServiceReconnection()
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .setListener { result, purchases ->
            // Every route into a new purchase comes through here: the flow this
            // app launched, and one completed elsewhere while it was open.
            purchaseUpdates.trySend(PurchaseUpdate(result, purchases.orEmpty()))
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                scope.launch { applyPurchases(purchases.orEmpty()) }
            }
        }
        .build()

    init {
        connect()
    }

    // -- Connection -----------------------------------------------------------

    private fun connect() {
        if (client.isReady) {
            refresh()
            return
        }
        client.startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        refresh()
                    } else {
                        _billing.value = BillingState.Unavailable(result.toUnavailable())
                        // The store could not be reached, which says nothing about
                        // what the learner owns — so entitlement stays Unknown
                        // rather than being downgraded to Free.
                    }
                }

                override fun onBillingServiceDisconnected() {
                    // `enableAutoServiceReconnection` handles the retry; this only
                    // stops the UI claiming a price it can no longer confirm.
                    _billing.value = BillingState.Unavailable(BillingUnavailable.NETWORK)
                }
            },
        )
    }

    /** Re-reads both halves: what is for sale, and what is already owned. */
    override fun refresh() {
        if (!client.isReady) {
            connect()
            return
        }
        queryProduct()
        queryPurchases()
    }

    // -- What is for sale -----------------------------------------------------

    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                ),
            )
            .build()

        client.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                _billing.value = BillingState.Unavailable(result.toUnavailable())
                return@queryProductDetailsAsync
            }
            val product = details.productDetailsList.firstOrNull { it.productId == productId }
            val selected = product?.let(::selectOffer)
            if (selected == null) {
                // Connected, and the id is not configured or has no offer. Almost
                // always a Play Console problem rather than a device one.
                offer = null
                _billing.value = BillingState.Unavailable(BillingUnavailable.NO_PRODUCTS)
                return@queryProductDetailsAsync
            }
            offer = selected
            currentDetails = product
            _billing.value = BillingState.Ready(selected.toProProduct(product))
        }
    }

    /**
     * Picks the offer to sell.
     *
     * An offer tagged `recommended` wins, so which plan is promoted is decided in
     * Play Console rather than in this file; otherwise the first offer is used.
     * **Nothing here invents a "best value" badge** — that flag is the tag's, and
     * absent tags mean an unbadged plan.
     */
    private fun selectOffer(product: ProductDetails): SelectedOffer? {
        val offers = product.subscriptionOfferDetails.orEmpty()
        if (offers.isEmpty()) return null
        val chosen = offers.firstOrNull { details ->
            details.offerTags.any { it.equals(RECOMMENDED_TAG, ignoreCase = true) }
        } ?: offers.first()

        val phases = chosen.pricingPhases.pricingPhaseList
        // The recurring phase is what the learner will actually be charged, every
        // period, once any introductory phase has run out.
        val recurring = phases.lastOrNull() ?: return null
        // A phase that costs nothing before it is the trial, and it is described
        // only because the store described it.
        val free = phases.dropLast(1).firstOrNull { it.priceAmountMicros == 0L }

        return SelectedOffer(
            offerToken = chosen.offerToken,
            formattedPrice = recurring.formattedPrice,
            billingPeriod = periodLabel(recurring.billingPeriod),
            recommended = chosen.offerTags.any { it.equals(RECOMMENDED_TAG, ignoreCase = true) },
            trial = free?.let { "${periodPhrase(it.billingPeriod)} free, then" },
        )
    }

    // -- What is owned --------------------------------------------------------

    private fun queryPurchases() {
        scope.launch { queryPurchasesNow() }
    }

    /**
     * Asks the store what is owned and waits for the answer, so a caller can act
     * on it rather than on a guess about how long it takes.
     *
     * Returns false when the store could not answer — which leaves entitlement
     * [ProEntitlement.Unknown] rather than downgrading it to Free, because a bad
     * network is not evidence that a subscription ended.
     */
    private suspend fun queryPurchasesNow(): Boolean =
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()

            client.queryPurchasesAsync(params) { result, purchases ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    applyPurchases(purchases)
                    if (continuation.isActive) continuation.resumeWith(Result.success(true))
                } else {
                    _entitlement.value = ProEntitlement.Unknown
                    if (continuation.isActive) continuation.resumeWith(Result.success(false))
                }
            }
        }

    /**
     * **The only place entitlement is set.**
     *
     * A receipt entitles the learner when the store says `PURCHASED` and it names
     * this product. Anything else — pending, unspecified, a different product —
     * does not.
     */
    private fun applyPurchases(purchases: List<Purchase>) {
        val owned = purchases.filter { purchase ->
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                productId in purchase.products
        }
        owned.filterNot { it.isAcknowledged }.forEach(::acknowledge)
        _entitlement.value = if (owned.isEmpty()) ProEntitlement.Free else ProEntitlement.Pro
    }

    /** Play refunds anything unacknowledged after three days. */
    private fun acknowledge(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        client.acknowledgePurchase(params) { /* Retried by the next query. */ }
    }

    // -- Buying ---------------------------------------------------------------

    override suspend fun purchase(): PurchaseOutcome {
        val activity = host ?: return PurchaseOutcome.Failed("no screen to open Google Play on")
        val selected = offer ?: return PurchaseOutcome.Unavailable
        val details = currentDetails ?: return PurchaseOutcome.Unavailable

        // Drain anything a previous flow left behind, so this attempt cannot read
        // the last one's answer.
        while (purchaseUpdates.tryReceive().isSuccess) Unit

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(selected.offerToken)
                        .build(),
                ),
            )
            .build()

        val launch = client.launchBillingFlow(activity, flowParams)
        if (launch.responseCode != BillingClient.BillingResponseCode.OK) {
            // Already owning it is not a failure; it means the query is what needs
            // to catch up, so fall through to the re-read below.
            if (launch.responseCode != BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                return launch.toPurchaseOutcome()
            }
        }

        val update = withTimeoutOrNull(PURCHASE_TIMEOUT_MS) { purchaseUpdates.receive() }
            ?: return PurchaseOutcome.Failed("Google Play did not respond")

        if (update.result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            return PurchaseOutcome.Cancelled
        }
        if (update.result.responseCode != BillingClient.BillingResponseCode.OK) {
            return update.result.toPurchaseOutcome()
        }

        // The flow says it worked. Whether the learner is *entitled* is a separate
        // question, and it is asked of the store rather than assumed from here.
        applyPurchases(update.purchases)
        withTimeoutOrNull(RESTORE_TIMEOUT_MS) { queryPurchasesNow() }

        return when {
            _entitlement.value.isPro -> PurchaseOutcome.Purchased
            update.purchases.any { it.purchaseState == Purchase.PurchaseState.PENDING } ->
                PurchaseOutcome.Failed("payment is still pending with Google Play")

            else -> PurchaseOutcome.Failed("the purchase could not be confirmed")
        }
    }

    override suspend fun restore(): RestoreOutcome {
        if (!client.isReady) {
            connect()
            return RestoreOutcome.Failed("not connected to Google Play")
        }
        val answered = withTimeoutOrNull(RESTORE_TIMEOUT_MS) { queryPurchasesNow() }
            ?: return RestoreOutcome.Failed("Google Play did not respond")
        if (!answered) return RestoreOutcome.Failed("Google Play could not be reached")

        return if (_entitlement.value.isPro) {
            RestoreOutcome.Restored
        } else {
            RestoreOutcome.NothingToRestore
        }
    }

    /** Ends the connection. Called when the activity that hosts it goes away. */
    fun close() {
        host = null
        client.endConnection()
    }

    /** The details the flow is launched against, kept beside the offer it came from. */
    private var currentDetails: ProductDetails? = null

    private fun SelectedOffer.toProProduct(details: ProductDetails) = ProProduct(
        id = productId,
        name = details.name.ifBlank { "AlgoKing Pro" },
        // Play's own localised string, passed through untouched.
        formattedPrice = formattedPrice,
        billingPeriod = billingPeriod,
        recommended = recommended,
        trial = trial,
    )

    private data class SelectedOffer(
        val offerToken: String,
        val formattedPrice: String,
        val billingPeriod: String,
        val recommended: Boolean,
        val trial: String?,
    )

    private data class PurchaseUpdate(
        val result: BillingResult,
        val purchases: List<Purchase>,
    )

    companion object {
        /**
         * **The subscription id, which must exist in Play Console with this exact
         * spelling**, as a subscription (not an in-app product), with at least one
         * base plan and an active offer.
         *
         * Nothing else in the app names a product, so changing what is sold is
         * changing this string plus the Play Console configuration.
         */
        const val PRO_PRODUCT_ID: String = "algoking_pro"

        /**
         * An offer tagged this way in Play Console is the one promoted, and the
         * only thing that earns the "BEST VALUE" badge. The app never decides it.
         */
        const val RECOMMENDED_TAG: String = "recommended"

        private const val PURCHASE_TIMEOUT_MS = 10 * 60 * 1000L
        private const val RESTORE_TIMEOUT_MS = 15_000L
    }
}

/** Play's response codes, mapped to the states the paywall can actually explain. */
private fun BillingResult.toUnavailable(): BillingUnavailable = when (responseCode) {
    BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
    BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED,
    -> BillingUnavailable.PLAY_UNAVAILABLE

    BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
    BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
    BillingClient.BillingResponseCode.NETWORK_ERROR,
    BillingClient.BillingResponseCode.SERVICE_TIMEOUT,
    -> BillingUnavailable.NETWORK

    // ITEM_UNAVAILABLE and DEVELOPER_ERROR both mean the product is not sellable
    // as configured, which reads the same way to a learner and differently to us.
    else -> BillingUnavailable.NO_PRODUCTS
}

private fun BillingResult.toPurchaseOutcome(): PurchaseOutcome = when (responseCode) {
    BillingClient.BillingResponseCode.USER_CANCELED -> PurchaseOutcome.Cancelled

    BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
    BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
    -> PurchaseOutcome.Unavailable

    BillingClient.BillingResponseCode.NETWORK_ERROR,
    BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
    -> PurchaseOutcome.Failed("no connection to Google Play")

    else -> PurchaseOutcome.Failed(debugMessage.ifBlank { "Google Play returned $responseCode" })
}

/**
 * `P1Y` → `per year`. Play speaks ISO 8601 durations; a learner does not.
 *
 * Anything unrecognised falls back to the raw period rather than to a guess: a
 * wrong billing period on a paywall is a misrepresentation, and an odd-looking one
 * is merely ugly.
 */
internal fun periodLabel(iso: String): String = when (iso) {
    "P1W" -> "per week"
    "P1M" -> "per month"
    "P3M" -> "every 3 months"
    "P6M" -> "every 6 months"
    "P1Y" -> "per year"
    else -> iso
}

/** `P2W` → `2 weeks`, for the trial phrase. */
internal fun periodPhrase(iso: String): String {
    val match = Regex("^P(\\d+)([DWMY])$").find(iso) ?: return iso
    val (count, unit) = match.destructured
    val name = when (unit) {
        "D" -> "day"
        "W" -> "week"
        "M" -> "month"
        "Y" -> "year"
        else -> return iso
    }
    return if (count == "1") "1 $name" else "$count ${name}s"
}
