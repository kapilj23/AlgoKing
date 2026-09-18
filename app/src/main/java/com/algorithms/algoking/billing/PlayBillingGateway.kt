package com.algorithms.algoking.billing

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
 * ### What is sold
 *
 * **One one-time product** — [PRO_PRODUCT_ID], bought through purchase option
 * [PRO_PURCHASE_OPTION_ID] — queried as [BillingClient.ProductType.INAPP], never
 * as a subscription. Owning it is permanent, so:
 *
 *  - the purchase is **acknowledged** and **never consumed.** Consuming a
 *    one-time product tells Play the learner has used it up and may buy it again,
 *    which is the opposite of a permanent unlock and would make every reinstall a
 *    second sale. There is no call to `consumeAsync` in this file and a test reads
 *    the file to keep it that way;
 *  - entitlement is still **read from the store every time** rather than latched
 *    to disk, because a refund has to be able to take it back (ADR-041).
 *
 * ### Entitlement is queried, never inferred
 *
 * [entitlement] changes in exactly one place — [applyPurchases], which is fed by
 * `queryPurchasesAsync`. A completed purchase flow does **not** set it; the flow
 * finishing triggers a re-query, and it is the query's answer that counts. That is
 * what makes "the store said success but owns nothing" a case that grants nothing
 * rather than a case nobody thought about.
 *
 * A receipt is entitling only when the store reports `PURCHASED`. `PENDING` — a
 * cash payment or a parental approval still in flight — is not, and is left to
 * become one when it clears. Both of those rules are [BillingRules]', where they
 * can be tested without a store.
 */
class PlayBillingGateway(
    context: Context,
    private val scope: CoroutineScope,
    /** The Play Console product this app sells. See [PRO_PRODUCT_ID]. */
    private val productId: String = PRO_PRODUCT_ID,
    /** The purchase option to buy it through. See [PRO_PURCHASE_OPTION_ID]. */
    private val purchaseOptionId: String = PRO_PURCHASE_OPTION_ID,
) : BillingGateway {

    private val _billing = MutableStateFlow<BillingState>(BillingState.Loading)
    override val billing: StateFlow<BillingState> = _billing.asStateFlow()

    /** Unknown until the store answers. Unknown is never treated as entitled. */
    private val _entitlement = MutableStateFlow<ProEntitlement>(ProEntitlement.Unknown)
    override val entitlement: StateFlow<ProEntitlement> = _entitlement.asStateFlow()

    /** The option being sold, kept only so the flow can be launched against it. */
    private var option: PurchaseOption? = null

    /** The details the flow is launched against, kept beside the option it came from. */
    private var currentDetails: ProductDetails? = null

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
        // Required before Play will report a PENDING one-time purchase at all.
        // Without it a cash payment simply never arrives.
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
                        // On every start: what is for sale, and what is already
                        // owned. The second half is what restores Pro after a
                        // reinstall, a new device, or cleared app data, with the
                        // learner doing nothing at all.
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
                        // A one-time product. Querying this id as SUBS returns
                        // nothing at all, which is how a paywall ends up
                        // permanently unable to sell something Play is selling.
                        .setProductType(BillingClient.ProductType.INAPP)
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
            val selected = product?.let {
                BillingRules.selectPurchaseOption(it.purchaseOptions(), purchaseOptionId)
            }
            if (product == null || selected == null) {
                // Connected, and the id is not configured, or is inactive, or has
                // no option matching the one this app sells.
                option = null
                currentDetails = null
                _billing.value = BillingState.Unavailable(BillingUnavailable.NO_PRODUCTS)
                return@queryProductDetailsAsync
            }
            option = selected
            currentDetails = product
            _billing.value = BillingState.Ready(selected.toProProduct(product))
        }
    }

    /**
     * Every purchase option Play reports for this product, in one list.
     *
     * A product configured with purchase options reports them through
     * `oneTimePurchaseOfferDetailsList`; a legacy one-time product predates that
     * and reports a single offer with no option id. Reading both and letting
     * [BillingRules.selectPurchaseOption] decide means the gateway does not have
     * to know which shape Play Console happens to be using.
     */
    private fun ProductDetails.purchaseOptions(): List<PurchaseOption> {
        val listed = oneTimePurchaseOfferDetailsList.orEmpty().map { it.toPurchaseOption() }
        if (listed.isNotEmpty()) return listed
        return listOfNotNull(oneTimePurchaseOfferDetails?.toPurchaseOption())
    }

    private fun ProductDetails.OneTimePurchaseOfferDetails.toPurchaseOption() = PurchaseOption(
        purchaseOptionId = purchaseOptionId,
        offerToken = offerToken.orEmpty(),
        // Play's own localised string, passed through untouched.
        formattedPrice = formattedPrice,
        offerTags = offerTags.orEmpty(),
    )

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
     * network is not evidence that a purchase was refunded.
     */
    private suspend fun queryPurchasesNow(): Boolean =
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            val params = QueryPurchasesParams.newBuilder()
                // One-time purchases. The half of this class that restores Pro.
                .setProductType(BillingClient.ProductType.INAPP)
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
     * The rule itself is [BillingRules.entitlement]: a receipt entitles the
     * learner when the store says `PURCHASED` and it names this product, and
     * anything else — pending, unspecified, a different product — does not. This
     * function is the wiring around it.
     */
    private fun applyPurchases(purchases: List<Purchase>) {
        val receipts = purchases.map { it.toReceipt() }
        val unacknowledged = BillingRules.toAcknowledge(receipts, productId).map { it.token }
        purchases.filter { it.purchaseToken in unacknowledged }.forEach(::acknowledge)
        _entitlement.value = BillingRules.entitlement(receipts, productId)
    }

    /**
     * Play refunds anything unacknowledged after three days.
     *
     * Acknowledged, **never consumed**: consuming would tell Play the learner has
     * used the product up and may buy it again, and this one is a permanent
     * unlock.
     */
    private fun acknowledge(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        client.acknowledgePurchase(params) { /* Retried by the next query. */ }
    }

    // -- Buying ---------------------------------------------------------------

    override suspend fun purchase(): PurchaseOutcome {
        val activity = host ?: return PurchaseOutcome.Failed("no screen to open Google Play on")
        val selected = option ?: return PurchaseOutcome.Unavailable
        val details = currentDetails ?: return PurchaseOutcome.Unavailable

        // Drain anything a previous flow left behind, so this attempt cannot read
        // the last one's answer.
        while (purchaseUpdates.tryReceive().isSuccess) Unit

        val product = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .apply {
                // A product with purchase options names the one being bought; a
                // legacy one-time product has no token and must not be given one.
                if (selected.offerToken.isNotBlank()) setOfferToken(selected.offerToken)
            }
            .build()

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(product))
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
            BillingRules.isPending(update.purchases.map { it.toReceipt() }, productId) ->
                PurchaseOutcome.Pending

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

    private fun Purchase.toReceipt() = Receipt(
        products = products,
        state = when (purchaseState) {
            Purchase.PurchaseState.PURCHASED -> ReceiptState.PURCHASED
            Purchase.PurchaseState.PENDING -> ReceiptState.PENDING
            else -> ReceiptState.UNSPECIFIED
        },
        acknowledged = isAcknowledged,
        token = purchaseToken,
    )

    private fun PurchaseOption.toProProduct(details: ProductDetails) = ProProduct(
        id = productId,
        name = details.name.ifBlank { "AlgoKing Pro" },
        // Play's own localised string, passed through untouched.
        formattedPrice = formattedPrice,
        priceDetail = ONE_TIME_PURCHASE,
        recommended = offerTags.any { it.equals(RECOMMENDED_TAG, ignoreCase = true) },
    )

    private data class PurchaseUpdate(
        val result: BillingResult,
        val purchases: List<Purchase>,
    )

    companion object {
        /**
         * **The product id, which must exist in Play Console with this exact
         * spelling**, as a **one-time product** (not a subscription), active, with
         * a purchase option named [PRO_PURCHASE_OPTION_ID].
         *
         * Nothing else in the app names a product, so changing what is sold is
         * changing this string plus the Play Console configuration.
         */
        const val PRO_PRODUCT_ID: String = "algoking_pro"

        /**
         * The purchase option `algoking_pro` is bought through.
         *
         * The app sells this one or nothing: a product that comes back with other
         * options and not this one is reported as unsellable rather than having
         * one of them substituted (see [BillingRules.selectPurchaseOption]).
         */
        const val PRO_PURCHASE_OPTION_ID: String = "buy"

        /**
         * The line under the price. Not a billing period — there is not one, and
         * a one-time product that implied a renewal would be a misrepresentation
         * on the one screen where that costs the most.
         */
        const val ONE_TIME_PURCHASE: String = "one-time purchase"

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
