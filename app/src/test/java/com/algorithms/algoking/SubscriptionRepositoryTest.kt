package com.algorithms.algoking

import com.algorithms.algoking.billing.BillingGateway
import com.algorithms.algoking.billing.BillingState
import com.algorithms.algoking.billing.BillingUnavailable
import com.algorithms.algoking.billing.PlayBillingGateway
import com.algorithms.algoking.billing.ProEntitlement
import com.algorithms.algoking.billing.ProProduct
import com.algorithms.algoking.billing.PurchaseOutcome
import com.algorithms.algoking.billing.RestoreOutcome
import com.algorithms.algoking.billing.SubscriptionRepository
import com.algorithms.algoking.billing.UnconfiguredBillingGateway
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
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
}
