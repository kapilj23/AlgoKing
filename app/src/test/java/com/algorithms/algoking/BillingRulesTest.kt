package com.algorithms.algoking

import com.algorithms.algoking.billing.BillingRules
import com.algorithms.algoking.billing.PlayBillingGateway
import com.algorithms.algoking.billing.ProEntitlement
import com.algorithms.algoking.billing.PurchaseOption
import com.algorithms.algoking.billing.Receipt
import com.algorithms.algoking.billing.ReceiptState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The two Play Billing rules, as truth tables.
 *
 * `PlayBillingGateway` cannot be run without a store and a device, so the parts of
 * it that are actually *decisions* live in `BillingRules` and are tested here:
 * which receipt entitles a learner, and which purchase option the app sells.
 *
 * The last group reads the gateway's own source. That is the idiom `AdPolicyTest`
 * already uses for the ad ids — some facts are properties of the file rather than
 * of a value it exposes, and "this file never calls `consumeAsync`" is exactly
 * one of them.
 */
class BillingRulesTest {

    private val pro = PlayBillingGateway.PRO_PRODUCT_ID
    private val other = "something_else"

    private fun receipt(
        product: String = pro,
        state: ReceiptState = ReceiptState.PURCHASED,
        acknowledged: Boolean = true,
        token: String = "token",
    ) = Receipt(listOf(product), state, acknowledged, token)

    // ── The product ──────────────────────────────────────────────────────────

    @Test
    fun `the app sells exactly the product and option configured in Play Console`() {
        // Pinned, so a typo is a failing test rather than a paywall that can never
        // sell anything on a device nobody has tested on yet.
        assertEquals("algoking_pro", PlayBillingGateway.PRO_PRODUCT_ID)
        assertEquals("buy", PlayBillingGateway.PRO_PURCHASE_OPTION_ID)
    }

    // ── What entitles a learner ──────────────────────────────────────────────

    @Test
    fun `a purchased receipt for this product is the one thing that grants Pro`() {
        assertEquals(ProEntitlement.Pro, BillingRules.entitlement(listOf(receipt()), pro))
    }

    @Test
    fun `a pending payment grants nothing`() {
        // The case with real money behind it: a cash payment or a parental
        // approval still in flight. Unlocking here would give Pro away to a
        // payment that may never clear.
        val pending = listOf(receipt(state = ReceiptState.PENDING))
        assertEquals(ProEntitlement.Free, BillingRules.entitlement(pending, pro))
        assertTrue(BillingRules.isPending(pending, pro))
    }

    @Test
    fun `a state the store could not specify grants nothing either`() {
        val unspecified = listOf(receipt(state = ReceiptState.UNSPECIFIED))
        assertEquals(ProEntitlement.Free, BillingRules.entitlement(unspecified, pro))
    }

    @Test
    fun `owning something else is not owning Pro`() {
        val theirs = listOf(receipt(product = other))
        assertEquals(ProEntitlement.Free, BillingRules.entitlement(theirs, pro))
        assertFalse(BillingRules.isPending(theirs, pro))
    }

    @Test
    fun `owning nothing is Free, never Unknown`() {
        // Unknown means "the store has not answered". An empty answer *is* an
        // answer, and the two must not be confused: one shows the paywall while a
        // query is in flight, the other says the learner does not own Pro.
        assertEquals(ProEntitlement.Free, BillingRules.entitlement(emptyList(), pro))
    }

    @Test
    fun `Pro is granted from the right receipt among several`() {
        val mixed = listOf(
            receipt(product = other),
            receipt(state = ReceiptState.PENDING, token = "pending"),
            receipt(token = "the real one"),
        )
        assertEquals(ProEntitlement.Pro, BillingRules.entitlement(mixed, pro))
    }

    @Test
    fun `entitlement is not latched, so a refund takes it away`() {
        // The difference between an entitlement and progress (ADR-028): progress
        // is earned and only ever goes up; this is owned, and ownership can end.
        val owned = listOf(receipt())
        assertEquals(ProEntitlement.Pro, BillingRules.entitlement(owned, pro))
        assertEquals(ProEntitlement.Free, BillingRules.entitlement(emptyList(), pro))
    }

    // ── Acknowledgement ──────────────────────────────────────────────────────

    @Test
    fun `an unacknowledged purchase is acknowledged`() {
        // Play refunds anything left unacknowledged for three days, which would
        // silently un-sell a purchase the learner made.
        val fresh = receipt(acknowledged = false, token = "fresh")
        assertEquals(listOf(fresh), BillingRules.toAcknowledge(listOf(fresh), pro))
    }

    @Test
    fun `an acknowledged purchase is not acknowledged twice`() {
        assertTrue(BillingRules.toAcknowledge(listOf(receipt()), pro).isEmpty())
    }

    @Test
    fun `a pending payment is not acknowledged, because there is nothing to acknowledge yet`() {
        val pending = listOf(receipt(state = ReceiptState.PENDING, acknowledged = false))
        assertTrue(BillingRules.toAcknowledge(pending, pro).isEmpty())
    }

    @Test
    fun `another product's receipt is never acknowledged by this app`() {
        val theirs = listOf(receipt(product = other, acknowledged = false))
        assertTrue(BillingRules.toAcknowledge(theirs, pro).isEmpty())
    }

    // ── Which purchase option is sold ────────────────────────────────────────

    private fun option(id: String?, token: String = "token-$id", price: String = "₹299.00") =
        PurchaseOption(purchaseOptionId = id, offerToken = token, formattedPrice = price)

    @Test
    fun `the configured purchase option is the one selected`() {
        val options = listOf(option("upgrade"), option("buy"), option("rent"))
        val selected = BillingRules.selectPurchaseOption(options, "buy")
        assertEquals("buy", selected?.purchaseOptionId)
        assertEquals("token-buy", selected?.offerToken)
    }

    @Test
    fun `an option that is not the configured one is never substituted for it`() {
        // Selling a different purchase option than the app was built against —
        // a rental, say, or an upgrade priced differently — is worse than not
        // selling anything, because the learner is charged for it.
        val options = listOf(option("rent"), option("upgrade"))
        assertNull(BillingRules.selectPurchaseOption(options, "buy"))
    }

    @Test
    fun `a legacy one-time product with a single unnamed offer is still sellable`() {
        // Products created before purchase options existed report one offer and no
        // option id. There is no ambiguity about which one is meant.
        val legacy = listOf(option(id = null, token = ""))
        val selected = BillingRules.selectPurchaseOption(legacy, "buy")
        assertEquals("", selected?.offerToken)
        assertEquals("₹299.00", selected?.formattedPrice)
    }

    @Test
    fun `a product with no offers at all is not sellable`() {
        assertNull(BillingRules.selectPurchaseOption(emptyList(), "buy"))
    }

    @Test
    fun `several unnamed offers are ambiguous, and ambiguity is refused`() {
        val ambiguous = listOf(option(id = null, token = "a"), option(id = null, token = "b"))
        assertNull(BillingRules.selectPurchaseOption(ambiguous, "buy"))
    }

    @Test
    fun `the price carried is the store's own string, whatever it says`() {
        // Every currency, every locale, tax included or not — the app passes it
        // through and never parses, formats or assembles one.
        for (price in listOf("₹299.00", "$4.99", "€4,99", "¥500", "R$ 19,90")) {
            val selected = BillingRules.selectPurchaseOption(
                listOf(option("buy", price = price)),
                "buy",
            )
            assertEquals(price, selected?.formattedPrice)
        }
    }

    // ── Facts about the gateway's source ─────────────────────────────────────

    private val gatewaySource: String
        get() = File("src/main/java/com/algorithms/algoking/billing/PlayBillingGateway.kt").readText()

    /**
     * The file with its comments taken out.
     *
     * The prose is allowed to name the thing it is explaining — the gateway says
     * out loud that it never consumes a purchase — and a test that could not tell
     * a sentence from a call would forbid writing that sentence down.
     */
    private fun code(source: String): String = source
        .lineSequence()
        .map { it.substringBefore("//").trim() }
        .filterNot { it.startsWith("*") || it.startsWith("/*") }
        .joinToString("\n")

    @Test
    fun `the Pro purchase is never consumed`() {
        // Consuming a one-time product tells Play the learner has used it up and
        // may buy it again — so a consumed Pro unlock would be re-sold on the next
        // reinstall. There is no legitimate reason for this file to ever call it,
        // and this is the cheapest possible guard against someone adding one.
        val source = code(gatewaySource)
        assertFalse("the gateway consumes a purchase", source.contains("consumeAsync"))
        assertFalse("the gateway builds ConsumeParams", source.contains("ConsumeParams"))
    }

    @Test
    fun `the purchase is acknowledged, or Play refunds it after three days`() {
        assertTrue(code(gatewaySource).contains("acknowledgePurchase"))
    }

    @Test
    fun `the product is queried as a one-time product and never as a subscription`() {
        val source = code(gatewaySource)
        assertTrue(
            "the gateway does not query INAPP",
            source.contains("ProductType.INAPP"),
        )
        assertFalse(
            "the gateway still queries subscriptions",
            source.contains("ProductType.SUBS") || source.contains("subscriptionOfferDetails"),
        )
    }

    @Test
    fun `pending one-time purchases are enabled, or Play would never report one`() {
        assertTrue(code(gatewaySource).contains("enableOneTimeProducts()"))
    }

    @Test
    fun `no price, currency or amount is written into the app`() {
        // The one number a paywall must never invent. Checked across the whole
        // billing package and the paywall itself rather than only where a price is
        // set today.
        val sources = listOf(
            File("src/main/java/com/algorithms/algoking/billing"),
            File("src/main/java/com/algorithms/algoking/feature/paywall"),
        ).flatMap { it.walkTopDown().filter { file -> file.extension == "kt" } }

        val currency = Regex("""[₹$€£¥]\s?\d""")
        for (file in sources) {
            for ((number, line) in file.readLines().withIndex()) {
                // Doc comments may quote an example price; code may not.
                val code = line.substringBefore("//").trim()
                if (code.startsWith("*") || code.startsWith("/*")) continue
                assertFalse(
                    "${file.name}:${number + 1} writes a price into the app: $code",
                    currency.containsMatchIn(code),
                )
            }
        }
    }
}
