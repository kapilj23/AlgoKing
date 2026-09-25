package com.algorithms.algoking.billing

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

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
     * A purchase that **just completed, in this app, in a flow this app launched.**
     *
     * Deliberately a one-shot event and not a flag. `entitlement` already answers
     * *"is this learner Pro?"*, and it answers it for every reason a learner can be
     * Pro — a purchase, a restore, a reinstall, the startup query, a pending
     * payment clearing, a `BillingClient` reconnect re-reading what the store owns.
     * A congratulation is true of exactly one of those, so it cannot be derived
     * from entitlement without congratulating the other six.
     *
     * A [Channel] rather than a `StateFlow` is the whole mechanism: an element is
     * delivered to one collector and is then **gone**. There is no `true` left
     * behind for a recomposition to re-read, for a resume to find, or for process
     * death to restore — which is what makes "exactly once, for that purchase"
     * structural rather than a flag somebody has to remember to clear. It is the
     * same reasoning ADR-042 used for the one-interstitial-per-completion rule:
     * recomposition is not a thing to be careful about, it is a thing to be immune
     * to.
     *
     * [Channel.CONFLATED] so a purchase completing while nothing is collecting is
     * held rather than dropped, and never queues up behind itself.
     */
    private val _proUnlocked = Channel<ProUnlocked>(Channel.CONFLATED)

    /** @see _proUnlocked */
    val proUnlocked: Flow<ProUnlocked> = _proUnlocked.receiveAsFlow()

    /**
     * Buys the plan, and then **asks the gateway what the learner owns** rather
     * than assuming the answer.
     *
     * The re-read is the point: a purchase that reports success but does not show
     * up in owned purchases has not entitled anyone to anything, and this is where
     * that discrepancy is caught rather than papered over.
     *
     * It is also what the [proUnlocked] event is gated on. The celebration needs
     * **both** halves to be true — the store reported `PURCHASED`, *and* the
     * re-read says the learner is now entitled — so the one discrepancy this class
     * exists to catch cannot produce a dialog announcing an unlock that did not
     * happen. Nothing else in the app emits this event: [restore] does not, and
     * neither does [refresh].
     */
    suspend fun purchase(): PurchaseOutcome {
        val outcome = gateway.purchase()
        _lastOutcome.value = outcome
        refresh()
        if (outcome == PurchaseOutcome.Purchased && entitlement.value.isPro) {
            _proUnlocked.trySend(ProUnlocked)
        }
        return outcome
    }

    /**
     * Re-queries owned purchases — a reinstall, a new device, a restored account.
     *
     * **Restores silently.** Entitlement comes back and the paywall closes, and no
     * [proUnlocked] event is emitted: the learner did not just buy anything, and
     * "You're all set!" for a purchase they made months ago on another device reads
     * as the app having lost track of what it sold.
     */
    suspend fun restore(): RestoreOutcome = gateway.restore()

    /** Re-reads both, from the store and only from the store. */
    fun refresh() = gateway.refresh()

    fun clearOutcome() {
        _lastOutcome.value = null
    }
}
