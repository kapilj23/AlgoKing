package com.ttele.algoking.billing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one place the app asks what the learner owns — ARCHITECTURE.md §10.6.
 *
 * It mirrors `ProgressRepository`: a repository over a source of truth, exposing
 * flows the screens collect, so no composable ever holds monetization logic. The
 * difference is what it refuses to do — `ProgressRepository.complete()` writes,
 * and **this class has no write path to [ProEntitlement.Pro] at all**. The only
 * way `entitlement` becomes Pro is a [BillingGateway] reporting a verified,
 * acknowledged purchase.
 *
 * That is the invariant the whole feature rests on, and it is structural: a
 * caller cannot express "make this learner Pro", because no such method exists.
 */
class SubscriptionRepository(private val gateway: BillingGateway) {

    /**
     * What the learner owns. Collected by Home and by the paywall.
     *
     * Passed straight through from the gateway rather than mirrored into a local
     * copy: a mirror is a second source of truth, and this is the one value in the
     * app where a stale copy would hand out paid lessons.
     */
    val entitlement: StateFlow<ProEntitlement> = gateway.entitlement

    /** What can be sold, and whether anything can. */
    val billing: StateFlow<BillingState> = gateway.billing

    private val _lastOutcome = MutableStateFlow<PurchaseOutcome?>(null)

    /** The last purchase attempt, for the message under the CTA. Cleared on view. */
    val lastOutcome: StateFlow<PurchaseOutcome?> = _lastOutcome.asStateFlow()

    /**
     * Buys the plan, and then **asks the gateway what the learner owns** rather
     * than assuming the answer.
     *
     * The re-read is the point: a purchase that reports success but does not show
     * up in owned purchases has not entitled anyone to anything, and this is where
     * that discrepancy is caught rather than papered over.
     */
    suspend fun purchase(): PurchaseOutcome {
        val outcome = gateway.purchase()
        _lastOutcome.value = outcome
        refresh()
        return outcome
    }

    /** Re-queries owned purchases — a reinstall, a new device, a restored account. */
    suspend fun restore(): RestoreOutcome = gateway.restore()

    /** Re-reads both, from the store and only from the store. */
    fun refresh() = gateway.refresh()

    fun clearOutcome() {
        _lastOutcome.value = null
    }
}
