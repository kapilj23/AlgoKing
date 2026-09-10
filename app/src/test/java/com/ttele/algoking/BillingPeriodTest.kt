package com.ttele.algoking

import com.ttele.algoking.billing.periodLabel
import com.ttele.algoking.billing.periodPhrase
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Play speaks ISO 8601 durations; a learner does not.
 *
 * This is the one piece of the price display the app computes rather than passes
 * through, so it is the one place a billing period could be misrepresented — and
 * a paywall that says "per month" over a yearly plan is a lie whether or not it
 * was meant as one. The price string itself is never touched: it is Play's own
 * localised text.
 */
class BillingPeriodTest {

    @Test
    fun `the periods a subscription can actually have read as English`() {
        assertEquals("per week", periodLabel("P1W"))
        assertEquals("per month", periodLabel("P1M"))
        assertEquals("every 3 months", periodLabel("P3M"))
        assertEquals("every 6 months", periodLabel("P6M"))
        assertEquals("per year", periodLabel("P1Y"))
    }

    @Test
    fun `an unrecognised period falls back to the raw value, never to a guess`() {
        // Showing `P2Y` is ugly. Showing "per year" for a two-year plan is a
        // misrepresentation, and this is the side to err on.
        assertEquals("P2Y", periodLabel("P2Y"))
        assertEquals("P10D", periodLabel("P10D"))
        assertEquals("", periodLabel(""))
    }

    @Test
    fun `a trial phrase counts and pluralises what the store said`() {
        assertEquals("1 week", periodPhrase("P1W"))
        assertEquals("2 weeks", periodPhrase("P2W"))
        assertEquals("1 month", periodPhrase("P1M"))
        assertEquals("3 months", periodPhrase("P3M"))
        assertEquals("7 days", periodPhrase("P7D"))
        assertEquals("1 year", periodPhrase("P1Y"))
    }

    @Test
    fun `a trial phrase it cannot parse is passed through untouched`() {
        assertEquals("P1Y6M", periodPhrase("P1Y6M"))
        assertEquals("nonsense", periodPhrase("nonsense"))
    }
}
