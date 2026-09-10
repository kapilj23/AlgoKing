package com.ttele.algoking

import com.ttele.algoking.ads.AdDecision
import com.ttele.algoking.ads.AdPolicy
import com.ttele.algoking.ads.AdSuppressed
import com.ttele.algoking.ads.AdUnits
import com.ttele.algoking.ads.Placement
import com.ttele.algoking.billing.ProEntitlement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The whole ad policy, as a truth table.
 *
 * One interstitial, for a free learner, after they finish TRY. The tests below
 * are mostly about the times it must **not** appear, because that is the half
 * that damages the product when it goes wrong (ADR-008's reasoning, applied to
 * the placement that actually shipped).
 */
class AdPolicyTest {

    private fun decide(
        entitlement: ProEntitlement = ProEntitlement.Free,
        completionId: Int = 1,
        lastShown: Int? = null,
        ready: Boolean = true,
    ) = AdPolicy.decide(
        placement = Placement.LESSON_COMPLETE,
        entitlement = entitlement,
        completionId = completionId,
        lastShownForCompletion = lastShown,
        adReady = ready,
    )

    // ── There is exactly one placement ───────────────────────────────────────

    @Test
    fun `the app has one ad placement, and it is the end of a lesson`() {
        // No banner, no rewarded, no native, no app-open, and nowhere else in the
        // app. A second placement cannot be added by writing a call site — only by
        // editing the enum, which is where that argument belongs.
        assertEquals(listOf(Placement.LESSON_COMPLETE), Placement.entries.toList())
    }

    // ── Pro ──────────────────────────────────────────────────────────────────

    @Test
    fun `a Pro subscriber is never shown an ad`() {
        val decision = decide(entitlement = ProEntitlement.Pro)
        assertEquals(AdDecision.Suppress(AdSuppressed.PRO), decision)
    }

    @Test
    fun `Pro wins over every other condition, including an ad already in hand`() {
        // The case that matters when someone subscribes mid-session: an ad loaded
        // while they were free must not be shown to them once they are not.
        for (completion in 1..3) {
            for (ready in listOf(true, false)) {
                assertEquals(
                    AdDecision.Suppress(AdSuppressed.PRO),
                    decide(
                        entitlement = ProEntitlement.Pro,
                        completionId = completion,
                        lastShown = null,
                        ready = ready,
                    ),
                )
            }
        }
    }

    // ── Free ─────────────────────────────────────────────────────────────────

    @Test
    fun `a free learner who has just finished TRY sees the one interstitial`() {
        assertEquals(AdDecision.Show, decide())
    }

    @Test
    fun `an unknown entitlement is treated as free`() {
        // Someone whose subscription has not been confirmed yet is not a
        // subscriber. The paywall makes the same call (ADR-041), and the worst
        // case is one ad shown to a payer whose state had not loaded — which the
        // next completion corrects.
        assertEquals(AdDecision.Show, decide(entitlement = ProEntitlement.Unknown))
    }

    // ── One completion, one opportunity ──────────────────────────────────────

    @Test
    fun `the same completion cannot show a second ad`() {
        // Compose recomposes; screens re-enter; devices rotate. None of it may
        // produce a second interstitial for one finished run.
        assertEquals(AdDecision.Show, decide(completionId = 7, lastShown = null))
        assertEquals(
            AdDecision.Suppress(AdSuppressed.ALREADY_SHOWN_FOR_COMPLETION),
            decide(completionId = 7, lastShown = 7),
        )
    }

    @Test
    fun `a genuinely new completion is a new opportunity`() {
        // Finishing TRY again — a second run, a second lesson — is a different
        // event, not a repeat of the first.
        assertEquals(AdDecision.Show, decide(completionId = 8, lastShown = 7))
    }

    // ── Availability never blocks learning ───────────────────────────────────

    @Test
    fun `no loaded ad means no ad, and never a wait`() {
        assertEquals(
            AdDecision.Suppress(AdSuppressed.NOT_READY),
            decide(ready = false),
        )
    }

    @Test
    fun `a load failure is indistinguishable from no ad, by design`() {
        // Failed load, no fill, no network, a bad unit — every one of them arrives
        // here as "not ready", and the learner carries on identically.
        assertTrue(decide(ready = false) is AdDecision.Suppress)
    }

    // ── The full table ───────────────────────────────────────────────────────

    @Test
    fun `every combination resolves the way the policy says it should`() {
        data class Case(
            val entitlement: ProEntitlement,
            val lastShown: Int?,
            val ready: Boolean,
            val expected: AdDecision,
        )

        val cases = listOf(
            Case(ProEntitlement.Free, null, true, AdDecision.Show),
            Case(ProEntitlement.Unknown, null, true, AdDecision.Show),
            Case(ProEntitlement.Free, null, false, AdDecision.Suppress(AdSuppressed.NOT_READY)),
            Case(
                ProEntitlement.Free,
                1,
                true,
                AdDecision.Suppress(AdSuppressed.ALREADY_SHOWN_FOR_COMPLETION),
            ),
            Case(
                ProEntitlement.Free,
                1,
                false,
                AdDecision.Suppress(AdSuppressed.ALREADY_SHOWN_FOR_COMPLETION),
            ),
            Case(ProEntitlement.Pro, null, true, AdDecision.Suppress(AdSuppressed.PRO)),
            Case(ProEntitlement.Pro, 1, false, AdDecision.Suppress(AdSuppressed.PRO)),
        )

        for (case in cases) {
            assertEquals(
                "$case",
                case.expected,
                decide(
                    entitlement = case.entitlement,
                    completionId = 1,
                    lastShown = case.lastShown,
                    ready = case.ready,
                ),
            )
        }
    }

    // ── The consent gate: may an ad even be requested? ───────────────────────

    @Test
    fun `no ad is requested until consent allows it`() {
        // UMP reports false until the consent state is known, and stays false if
        // the learner declined or the check failed. Either way nothing is fetched
        // — which is the difference between "we did not show an ad" and "we did
        // not collect anything to show one with".
        assertFalse(AdPolicy.mayRequestAds(ProEntitlement.Free, canRequestAds = false))
        assertFalse(AdPolicy.mayRequestAds(ProEntitlement.Unknown, canRequestAds = false))
        assertTrue(AdPolicy.mayRequestAds(ProEntitlement.Free, canRequestAds = true))
    }

    @Test
    fun `a Pro subscriber never requests an ad, consent or not`() {
        // Not merely "never shown" — never fetched. There is no point spending a
        // request on something that cannot be displayed.
        assertFalse(AdPolicy.mayRequestAds(ProEntitlement.Pro, canRequestAds = true))
        assertFalse(AdPolicy.mayRequestAds(ProEntitlement.Pro, canRequestAds = false))
    }

    @Test
    fun `requesting and showing are separate questions`() {
        // Consent gates the request; entitlement and repetition gate the display.
        // A learner can be allowed to request and still not be shown one — which
        // is exactly what happens on the second visit to the same Complete screen.
        assertTrue(AdPolicy.mayRequestAds(ProEntitlement.Free, canRequestAds = true))
        assertEquals(
            AdDecision.Suppress(AdSuppressed.ALREADY_SHOWN_FOR_COMPLETION),
            decide(completionId = 3, lastShown = 3),
        )
    }

    // ── Test units ───────────────────────────────────────────────────────────

    @Test
    fun `a debug build can never reach the production ad unit`() {
        // The rule that keeps a developer's own taps out of the real account:
        // impressions from a debug build are invalid traffic, and AdMob suspends
        // accounts for it.
        assertEquals(AdUnits.TEST_INTERSTITIAL, AdUnits.interstitialFor(debuggable = true))
        assertEquals(
            AdUnits.PRODUCTION_INTERSTITIAL,
            AdUnits.interstitialFor(debuggable = false),
        )
        assertTrue(AdUnits.TEST_INTERSTITIAL != AdUnits.PRODUCTION_INTERSTITIAL)
    }

    @Test
    fun `the app id and the unit id belong to the same AdMob account`() {
        // An app id and a unit id are a matched pair — a production unit under a
        // test app id does not serve, and the reverse is a policy problem. They
        // live in two different files, so this reads both and checks they agree:
        // it is the "changed one, forgot the other" mistake, caught by a test
        // rather than by a support thread three weeks later.
        val gradle = File("build.gradle.kts").readText()
        val ids = Regex("""admobAppId"?\]?\s*=\s*"(ca-app-pub-[0-9]+~[0-9]+)"""")
            .findAll(gradle)
            .map { it.groupValues[1] }
            .toList()
        assertEquals("expected a debug and a release app id", 2, ids.size)

        val publishers = ids.map(AdUnits::publisherOf).toSet()
        assertEquals(
            "the manifest placeholders name accounts the unit ids do not",
            setOf(
                AdUnits.publisherOf(AdUnits.TEST_INTERSTITIAL),
                AdUnits.publisherOf(AdUnits.PRODUCTION_INTERSTITIAL),
            ),
            publishers,
        )
    }

    @Test
    fun `the production ids are the ones the account actually owns`() {
        // Pinned, so a typo in either half is a failing test rather than an ad
        // unit that silently never fills.
        assertEquals("ca-app-pub-2478174291729626/4430126130", AdUnits.PRODUCTION_INTERSTITIAL)
        assertEquals("ca-app-pub-2478174291729626", AdUnits.publisherOf(AdUnits.PRODUCTION_INTERSTITIAL))

        val gradle = File("build.gradle.kts").readText()
        assertTrue(
            "the release app id is not the production one",
            gradle.contains("ca-app-pub-2478174291729626~9594340402"),
        )
    }
}
