package com.ttele.algoking

import com.ttele.algoking.billing.BillingGateway
import com.ttele.algoking.billing.BillingState
import com.ttele.algoking.billing.BillingUnavailable
import com.ttele.algoking.billing.ProEntitlement
import com.ttele.algoking.billing.ProProduct
import com.ttele.algoking.billing.PurchaseOutcome
import com.ttele.algoking.billing.RestoreOutcome
import com.ttele.algoking.billing.SubscriptionRepository
import com.ttele.algoking.billing.UnconfiguredBillingGateway
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
                id = "algoking_pro",
                name = "AlgoKing Pro",
                formattedPrice = "₹399.00",
                billingPeriod = "per year",
            )
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
        // A refund, an expiry, a cancelled subscription. Entitlement is read from
        // the store every time rather than latched, so it can go down as well as up
        // — which is the difference between this and progress (ADR-028).
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
        assertEquals("₹399.00", ready.product.formattedPrice)
        assertEquals("per year", ready.product.billingPeriod)
        // Nothing is recommended unless the store's own configuration says so.
        assertFalse(ready.product.recommended)
        assertNull(ready.product.trial)
    }
}
