package com.algorithms.algoking

import com.algorithms.algoking.ads.AdDecision
import com.algorithms.algoking.ads.AdPolicy
import com.algorithms.algoking.ads.AdSuppressed
import com.algorithms.algoking.ads.Placement
import com.algorithms.algoking.billing.BillingGateway
import com.algorithms.algoking.billing.BillingState
import com.algorithms.algoking.billing.BillingUnavailable
import com.algorithms.algoking.billing.PlayBillingGateway
import com.algorithms.algoking.billing.ProEntitlement
import com.algorithms.algoking.billing.ProProduct
import com.algorithms.algoking.billing.ProUnlocked
import com.algorithms.algoking.billing.PurchaseOutcome
import com.algorithms.algoking.billing.RestoreOutcome
import com.algorithms.algoking.billing.SubscriptionRepository
import com.algorithms.algoking.billing.UnconfiguredBillingGateway
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Nothing but a verified purchase grants Pro.**
 *
 * This is the invariant the whole feature rests on, so it is tested against the
 * cases that would break it: a purchase that reports success while the store owns
 * nothing, a cancellation, a failure, an unavailable store, and a restore that
 * finds nothing.
 */
class SubscriptionRepositoryTest {

    /**
     * A gateway whose purchase outcome and owned-entitlement are set separately —
     * so a test can say "the flow claimed success but the store owns nothing",
     * which is exactly the discrepancy the repository has to survive.
     */
    private class FakeGateway(
        state: BillingState = BillingState.Ready(product),
        owned: ProEntitlement = ProEntitlement.Free,
        var purchaseOutcome: PurchaseOutcome = PurchaseOutcome.Cancelled,
        var restoreOutcome: RestoreOutcome = RestoreOutcome.NothingToRestore,
        /** What the store owns *after* a purchase attempt. */
        var ownedAfterPurchase: ProEntitlement? = null,
        var ownedAfterRestore: ProEntitlement? = null,
    ) : BillingGateway {

        override val billing = MutableStateFlow(state)
        override val entitlement = MutableStateFlow(owned)

        override suspend fun purchase(): PurchaseOutcome {
            ownedAfterPurchase?.let { entitlement.value = it }
            return purchaseOutcome
        }

        override suspend fun restore(): RestoreOutcome {
            ownedAfterRestore?.let { entitlement.value = it }
            return restoreOutcome
        }

        override fun refresh() = Unit

        companion object {
            val product = ProProduct(
                id = PlayBillingGateway.PRO_PRODUCT_ID,
                name = "AlgoKing Pro",
                // Whatever Play returned for this device's locale. The app never
                // writes one, so a test may not pretend it has a favourite.
                formattedPrice = STORE_PRICE,
                priceDetail = PlayBillingGateway.ONE_TIME_PURCHASE,
            )

            /** Stands in for Play's localised string. Its value means nothing. */
            const val STORE_PRICE = "TEST_PRICE_FROM_PLAY"
        }
    }

    @Test
    fun `the build ships with no billing, and therefore with no entitlement`() {
        val repository = SubscriptionRepository(UnconfiguredBillingGateway())
        assertEquals(ProEntitlement.Free, repository.entitlement.value)
        assertEquals(
            BillingState.Unavailable(BillingUnavailable.NOT_CONFIGURED),
            repository.billing.value,
        )
    }

    @Test
    fun `the unconfigured gateway cannot sell or restore anything`() = runBlocking {
        val repository = SubscriptionRepository(UnconfiguredBillingGateway())

        assertEquals(PurchaseOutcome.Unavailable, repository.purchase())
        assertFalse(repository.entitlement.value.isPro)

        assertEquals(RestoreOutcome.NothingToRestore, repository.restore())
        assertFalse(repository.entitlement.value.isPro)
    }

    @Test
    fun `a successful purchase grants Pro, because the store then owns it`() = runBlocking {
        val gateway = FakeGateway(
            purchaseOutcome = PurchaseOutcome.Purchased,
            ownedAfterPurchase = ProEntitlement.Pro,
        )
        val repository = SubscriptionRepository(gateway)

        assertEquals(PurchaseOutcome.Purchased, repository.purchase())
        assertTrue(repository.entitlement.value.isPro)
    }

    @Test
    fun `a purchase that reports success while the store owns nothing grants nothing`() =
        runBlocking {
            // The case that matters. If entitlement were derived from the outcome
            // rather than re-read from the store, this would hand out Pro for free.
            val gateway = FakeGateway(
                purchaseOutcome = PurchaseOutcome.Purchased,
                ownedAfterPurchase = null,
            )
            val repository = SubscriptionRepository(gateway)

            repository.purchase()
            assertFalse(repository.entitlement.value.isPro)
        }

    @Test
    fun `a cancelled purchase grants nothing`() = runBlocking {
        val repository = SubscriptionRepository(
            FakeGateway(purchaseOutcome = PurchaseOutcome.Cancelled),
        )
        assertEquals(PurchaseOutcome.Cancelled, repository.purchase())
        assertFalse(repository.entitlement.value.isPro)
    }

    @Test
    fun `a failed purchase grants nothing, and says why`() = runBlocking {
        val repository = SubscriptionRepository(
            FakeGateway(purchaseOutcome = PurchaseOutcome.Failed("card declined")),
        )
        val outcome = repository.purchase()
        assertEquals(PurchaseOutcome.Failed("card declined"), outcome)
        assertFalse(repository.entitlement.value.isPro)
    }

    @Test
    fun `a billing error grants nothing`() = runBlocking {
        val repository = SubscriptionRepository(
            FakeGateway(
                state = BillingState.Unavailable(BillingUnavailable.NETWORK),
                purchaseOutcome = PurchaseOutcome.Unavailable,
            ),
        )
        repository.purchase()
        assertFalse(repository.entitlement.value.isPro)
        assertEquals(
            BillingState.Unavailable(BillingUnavailable.NETWORK),
            repository.billing.value,
        )
    }

    @Test
    fun `restoring an existing purchase restores the entitlement`() = runBlocking {
        val gateway = FakeGateway(
            restoreOutcome = RestoreOutcome.Restored,
            ownedAfterRestore = ProEntitlement.Pro,
        )
        val repository = SubscriptionRepository(gateway)

        assertEquals(RestoreOutcome.Restored, repository.restore())
        assertTrue(repository.entitlement.value.isPro)
    }

    @Test
    fun `a restore that finds nothing leaves the learner where they were`() = runBlocking {
        val repository = SubscriptionRepository(
            FakeGateway(restoreOutcome = RestoreOutcome.NothingToRestore),
        )
        repository.restore()
        assertFalse(repository.entitlement.value.isPro)
    }

    @Test
    fun `an entitlement the store withdraws is withdrawn here too`() = runBlocking {
        // A refund, or a purchase revoked by Play. Entitlement is read from the
        // store every time rather than latched, so it can go down as well as up —
        // which is the difference between this and progress (ADR-028).
        val gateway = FakeGateway(
            purchaseOutcome = PurchaseOutcome.Purchased,
            ownedAfterPurchase = ProEntitlement.Pro,
        )
        val repository = SubscriptionRepository(gateway)
        repository.purchase()
        assertTrue(repository.entitlement.value.isPro)

        gateway.entitlement.value = ProEntitlement.Free
        repository.refresh()
        assertFalse(repository.entitlement.value.isPro)
    }

    @Test
    fun `the last outcome is reported once and then cleared`() = runBlocking {
        val repository = SubscriptionRepository(
            FakeGateway(purchaseOutcome = PurchaseOutcome.Cancelled),
        )
        assertNull(repository.lastOutcome.value)

        repository.purchase()
        assertEquals(PurchaseOutcome.Cancelled, repository.lastOutcome.value)

        repository.clearOutcome()
        assertNull(repository.lastOutcome.value)
    }

    @Test
    fun `the price shown is whatever the store said, and is never assembled here`() {
        val repository = SubscriptionRepository(FakeGateway())
        val ready = repository.billing.value as BillingState.Ready
        assertEquals(FakeGateway.STORE_PRICE, ready.product.formattedPrice)
        // A one-time product: what kind of purchase it is, never how often it
        // recurs, because it does not.
        assertEquals("one-time purchase", ready.product.priceDetail)
        assertEquals(PlayBillingGateway.PRO_PRODUCT_ID, ready.product.id)
        // Nothing is recommended unless the store's own configuration says so.
        assertFalse(ready.product.recommended)
    }

    @Test
    fun `a pending payment grants nothing and is reported as pending, not as failure`() = runBlocking {
        // Cash at a counter, or a parental approval. The learner may well be
        // charged, so calling it a failure is wrong — and Pro stays locked until
        // Play says the payment completed, so calling it a success is worse.
        val gateway = FakeGateway(purchaseOutcome = PurchaseOutcome.Pending)
        val repository = SubscriptionRepository(gateway)

        assertEquals(PurchaseOutcome.Pending, repository.purchase())
        assertFalse(repository.entitlement.value.isPro)
        assertEquals(PurchaseOutcome.Pending, repository.lastOutcome.value)
    }

    @Test
    fun `a pending payment that later clears grants Pro, without a second purchase`() = runBlocking {
        val gateway = FakeGateway(purchaseOutcome = PurchaseOutcome.Pending)
        val repository = SubscriptionRepository(gateway)
        repository.purchase()
        assertFalse(repository.entitlement.value.isPro)

        // Play reports PURCHASED on a later query — the app asks again and the
        // answer changes. Nothing had to be bought twice.
        gateway.entitlement.value = ProEntitlement.Pro
        repository.refresh()
        assertTrue(repository.entitlement.value.isPro)
    }

    @Test
    fun `clearing local state cannot lose a purchase Play still owns`() {
        // A reinstall, cleared app data, a new device. There is no local flag to
        // lose: entitlement is a passthrough of what the store answers, so a fresh
        // repository over a store that owns Pro is Pro.
        val gateway = FakeGateway(owned = ProEntitlement.Pro)
        assertTrue(SubscriptionRepository(gateway).entitlement.value.isPro)

        // And a fresh one, as at app start, before anything is tapped.
        val afterRestart = SubscriptionRepository(gateway)
        assertTrue(afterRestart.entitlement.value.isPro)
    }

    @Test
    fun `an unanswered store is Unknown, which is not Pro and not Free`() {
        // The state a paywall must not resolve permanently: the query is still in
        // flight. `ProAccess` treats it as not entitled, and the paywall opens the
        // lesson the moment a real answer arrives.
        val repository = SubscriptionRepository(FakeGateway(owned = ProEntitlement.Unknown))
        assertEquals(ProEntitlement.Unknown, repository.entitlement.value)
        assertFalse(repository.entitlement.value.isPro)
    }

    // -- The receipt ----------------------------------------------------------
    //
    // `proUnlocked` is what puts "You're All Set!" on screen, and the whole of its
    // correctness is *which* of the several ways a learner becomes Pro it fires
    // for. Exactly one: a purchase this app just completed. Every other route to
    // the same entitlement — a restore, a reinstall, the startup query, a
    // reconnect, a pending payment clearing later — must restore Pro in silence.
    //
    // It is an event and not a flag, so "shown exactly once" is tested by taking
    // it twice: the second read has nothing to take, because delivery consumed it.

    /**
     * The next unlock event, or null if there is not one.
     *
     * Reading it **consumes** it, which is the property under test as much as it is
     * the way to observe it.
     */
    private suspend fun SubscriptionRepository.nextUnlock(): ProUnlocked? =
        withTimeoutOrNull(EVENT_TIMEOUT_MS) { proUnlocked.first() }

    @Test
    fun `a completed purchase unlocks Pro and announces it exactly once`() = runBlocking {
        val repository = SubscriptionRepository(
            FakeGateway(
                purchaseOutcome = PurchaseOutcome.Purchased,
                ownedAfterPurchase = ProEntitlement.Pro,
            ),
        )

        assertEquals(PurchaseOutcome.Purchased, repository.purchase())
        assertTrue(repository.entitlement.value.isPro)
        assertEquals(ProUnlocked, repository.nextUnlock())

        // Taken once, gone. Nothing is left for a second collector to find, which
        // is what makes a recomposition unable to raise the dialog again.
        assertNull(repository.nextUnlock())
    }

    @Test
    fun `re-collecting the event after a recomposition finds nothing to show`() = runBlocking {
        // A recomposition does not restart the collector, but if one ever did, the
        // channel is the reason it would be harmless: there is no retained `true`.
        val repository = SubscriptionRepository(
            FakeGateway(
                purchaseOutcome = PurchaseOutcome.Purchased,
                ownedAfterPurchase = ProEntitlement.Pro,
            ),
        )
        repository.purchase()
        assertEquals(ProUnlocked, repository.nextUnlock())

        repeat(3) { assertNull(repository.nextUnlock()) }
    }

    @Test
    fun `a purchase reporting success while the store owns nothing announces nothing`() =
        runBlocking {
            // The discrepancy this repository exists to catch. It grants nothing —
            // and it must therefore also *claim* nothing, or the dialog would
            // announce an unlock that did not happen.
            val repository = SubscriptionRepository(
                FakeGateway(
                    purchaseOutcome = PurchaseOutcome.Purchased,
                    ownedAfterPurchase = null,
                ),
            )

            repository.purchase()
            assertFalse(repository.entitlement.value.isPro)
            assertNull(repository.nextUnlock())
        }

    @Test
    fun `a pending payment announces nothing, and still announces nothing when it clears`() =
        runBlocking {
            val gateway = FakeGateway(purchaseOutcome = PurchaseOutcome.Pending)
            val repository = SubscriptionRepository(gateway)

            repository.purchase()
            assertFalse(repository.entitlement.value.isPro)
            assertNull(repository.nextUnlock())

            // The payment clears and a later query reports it. Pro arrives, and it
            // arrives through a *query* rather than through a purchase flow — so
            // the lessons unlock and nothing is celebrated. The learner is already
            // somewhere else in the app by now; a dialog arriving over a lesson
            // minutes after they paid is a surprise, not a confirmation.
            gateway.entitlement.value = ProEntitlement.Pro
            repository.refresh()
            assertTrue(repository.entitlement.value.isPro)
            assertNull(repository.nextUnlock())
        }

    @Test
    fun `a cancelled purchase announces nothing`() = runBlocking {
        val repository = SubscriptionRepository(
            FakeGateway(purchaseOutcome = PurchaseOutcome.Cancelled),
        )
        repository.purchase()
        assertNull(repository.nextUnlock())
    }

    @Test
    fun `a failed purchase announces nothing`() = runBlocking {
        val repository = SubscriptionRepository(
            FakeGateway(purchaseOutcome = PurchaseOutcome.Failed("card declined")),
        )
        repository.purchase()
        assertNull(repository.nextUnlock())
    }

    @Test
    fun `an unavailable store announces nothing`() = runBlocking {
        val repository = SubscriptionRepository(
            FakeGateway(
                state = BillingState.Unavailable(BillingUnavailable.NOT_CONFIGURED),
                purchaseOutcome = PurchaseOutcome.Unavailable,
            ),
        )
        repository.purchase()
        assertNull(repository.nextUnlock())
    }

    @Test
    fun `Restore purchases restores Pro silently`() = runBlocking {
        // The learner owns Pro from another device or a previous install. Pro comes
        // back and the paywall closes, and they are told nothing — because they did
        // not just buy anything, and being congratulated on a months-old purchase
        // reads as the app having lost track of what it sold.
        val repository = SubscriptionRepository(
            FakeGateway(
                restoreOutcome = RestoreOutcome.Restored,
                ownedAfterRestore = ProEntitlement.Pro,
            ),
        )

        assertEquals(RestoreOutcome.Restored, repository.restore())
        assertTrue(repository.entitlement.value.isPro)
        assertNull(repository.nextUnlock())
    }

    @Test
    fun `an app restart over a store that owns Pro announces nothing`() = runBlocking {
        // A fresh repository, as at app start, over a store that already owns Pro.
        // Entitlement is Pro from the first frame and nothing is announced: there
        // is no persisted flag to come back, which is the reason `remember` rather
        // than `rememberSaveable` holds the dialog's state in `MainActivity`.
        val repository = SubscriptionRepository(FakeGateway(owned = ProEntitlement.Pro))

        assertTrue(repository.entitlement.value.isPro)
        assertNull(repository.nextUnlock())
    }

    @Test
    fun `a billing reconnect re-reading the same purchase announces nothing`() = runBlocking {
        // `enableAutoServiceReconnection` means a transient disconnect re-queries
        // owned purchases, and `PurchasesUpdatedListener` can deliver the same
        // receipt again. Both change *entitlement*, and neither is a purchase the
        // learner just made — so however many times the store re-reports it, there
        // is nothing to announce.
        val gateway = FakeGateway(
            purchaseOutcome = PurchaseOutcome.Purchased,
            ownedAfterPurchase = ProEntitlement.Pro,
        )
        val repository = SubscriptionRepository(gateway)

        repository.purchase()
        assertEquals(ProUnlocked, repository.nextUnlock())

        repeat(3) {
            // A reconnect: the store answers again with the same thing it owned.
            gateway.entitlement.value = ProEntitlement.Free
            gateway.entitlement.value = ProEntitlement.Pro
            repository.refresh()
            assertTrue(repository.entitlement.value.isPro)
            assertNull(repository.nextUnlock())
        }
    }

    // -- Removing ads is the same entitlement --------------------------------
    //
    // One purchase, several benefits: the Pro lessons open *and* the one
    // interstitial stops. There is no second "ads removed" flag to keep in step —
    // `AdPolicy` reads the same `ProEntitlement` this repository exposes, so these
    // tests run the real policy against the real outcome of each purchase path.
    // What they are really pinning is that the two cannot disagree.

    /** The ad decision a learner would get at the end of a lesson, right now. */
    private fun SubscriptionRepository.adAtCompletion(completion: Int = 1) = AdPolicy.decide(
        placement = Placement.LESSON_COMPLETE,
        entitlement = entitlement.value,
        completionId = completion,
        lastShownForCompletion = null,
        adReady = true,
    )

    @Test
    fun `a completed purchase removes ads immediately, with no restart`() = runBlocking {
        val repository = SubscriptionRepository(
            FakeGateway(
                purchaseOutcome = PurchaseOutcome.Purchased,
                ownedAfterPurchase = ProEntitlement.Pro,
            ),
        )
        // Before: the store says Free, so the one interstitial is allowed.
        assertEquals(AdDecision.Show, repository.adAtCompletion())

        repository.purchase()

        // After, on the very next decision — no relaunch, no navigation, no second
        // purchase check. The entitlement changed and the policy reads it.
        assertTrue(repository.entitlement.value.isPro)
        assertEquals(
            AdDecision.Suppress(AdSuppressed.PRO),
            repository.adAtCompletion(),
        )
    }

    @Test
    fun `an existing purchase found at startup means no ad from the first lesson`() {
        // A reinstall, a new device, or simply the next launch. Entitlement is Pro
        // before anything is tapped, so there is no window in which an ad is
        // allowed.
        val repository = SubscriptionRepository(FakeGateway(owned = ProEntitlement.Pro))
        assertEquals(
            AdDecision.Suppress(AdSuppressed.PRO),
            repository.adAtCompletion(),
        )
    }

    @Test
    fun `while the store has not answered, no ad is shown`() {
        // The startup race. `Unknown` is not Pro, but it is not evidence of Free
        // either, and an ad shown in that window lands on a learner who may have
        // paid not to see it.
        val repository = SubscriptionRepository(FakeGateway(owned = ProEntitlement.Unknown))
        assertEquals(
            AdDecision.Suppress(AdSuppressed.ENTITLEMENT_UNKNOWN),
            repository.adAtCompletion(),
        )
    }

    @Test
    fun `Restore purchases removes ads too`() = runBlocking {
        val repository = SubscriptionRepository(
            FakeGateway(
                restoreOutcome = RestoreOutcome.Restored,
                ownedAfterRestore = ProEntitlement.Pro,
            ),
        )
        repository.restore()
        assertEquals(
            AdDecision.Suppress(AdSuppressed.PRO),
            repository.adAtCompletion(),
        )
    }

    @Test
    fun `a pending payment does not remove ads`() = runBlocking {
        // Nothing is owned until Play says `PURCHASED`. A learner mid-payment is
        // still a free learner, and pretending otherwise would be giving away the
        // benefit before the money arrives.
        val repository = SubscriptionRepository(
            FakeGateway(purchaseOutcome = PurchaseOutcome.Pending),
        )
        repository.purchase()
        assertEquals(AdDecision.Show, repository.adAtCompletion())
    }

    @Test
    fun `a cancelled purchase does not remove ads`() = runBlocking {
        val repository = SubscriptionRepository(
            FakeGateway(purchaseOutcome = PurchaseOutcome.Cancelled),
        )
        repository.purchase()
        assertEquals(AdDecision.Show, repository.adAtCompletion())
    }

    @Test
    fun `a failed purchase does not remove ads`() = runBlocking {
        val repository = SubscriptionRepository(
            FakeGateway(purchaseOutcome = PurchaseOutcome.Failed("card declined")),
        )
        repository.purchase()
        assertEquals(AdDecision.Show, repository.adAtCompletion())
    }

    @Test
    fun `a purchase claiming success the store cannot confirm does not remove ads`() = runBlocking {
        // The discrepancy again, in its ad-shaped form: no entitlement, no benefit.
        val repository = SubscriptionRepository(
            FakeGateway(
                purchaseOutcome = PurchaseOutcome.Purchased,
                ownedAfterPurchase = null,
            ),
        )
        repository.purchase()
        assertEquals(AdDecision.Show, repository.adAtCompletion())
    }

    @Test
    fun `an entitlement the store withdraws brings the ads back`() = runBlocking {
        // A refund. The benefit goes with the entitlement, because there is no
        // separate flag to be left behind holding it open.
        val gateway = FakeGateway(
            purchaseOutcome = PurchaseOutcome.Purchased,
            ownedAfterPurchase = ProEntitlement.Pro,
        )
        val repository = SubscriptionRepository(gateway)
        repository.purchase()
        assertEquals(
            AdDecision.Suppress(AdSuppressed.PRO),
            repository.adAtCompletion(),
        )

        gateway.entitlement.value = ProEntitlement.Free
        repository.refresh()
        assertEquals(AdDecision.Show, repository.adAtCompletion())
    }

    @Test
    fun `the confirmation says the ads are gone, because nothing else would show it`() {
        // A learner watches the lessons unlock; the only way they would discover
        // the ads had stopped is by not seeing one. If the dialog does not say it,
        // the benefit is invisible.
        val dialog = java.io.File(
            "src/main/java/com/algorithms/algoking/feature/paywall/ProUnlockedDialog.kt",
        ).readText()
        assertTrue(
            "the purchase confirmation must mention the ads being removed",
            dialog.contains("ads are now removed"),
        )

        val paywall = java.io.File(
            "src/main/java/com/algorithms/algoking/feature/paywall/PaywallScreen.kt",
        ).readText()
        assertTrue(
            "the paywall must list removing ads as a benefit",
            paywall.contains("Remove all ads"),
        )
    }

    private companion object {
        /**
         * How long a test waits for an event before concluding there is not one.
         *
         * Long enough that a delivery cannot be missed on a loaded machine, short
         * enough that the nine "announces nothing" cases stay fast.
         */
        const val EVENT_TIMEOUT_MS = 150L
    }
}
