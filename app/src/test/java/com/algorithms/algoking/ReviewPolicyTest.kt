package com.algorithms.algoking

import com.algorithms.algoking.review.ReviewDecision
import com.algorithms.algoking.review.ReviewPolicy
import com.algorithms.algoking.review.ReviewSuppressed
import com.algorithms.algoking.review.ReviewTrigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The whole review policy, as a truth table.
 *
 * Ask once, after the learner has actually finished a lesson, and then never
 * automatically again. As with `AdPolicyTest`, nearly every test below is about a
 * moment the prompt must **not** appear — that is the half that damages the product
 * when it goes wrong, and a review prompt is the single easiest thing in an app to
 * make people resent.
 */
class ReviewPolicyTest {

    private fun decide(
        alreadyAsked: Boolean = false,
        lessonComplete: Boolean = true,
        triedThisCompletion: Boolean = false,
    ) = ReviewPolicy.decide(
        trigger = ReviewTrigger.LESSON_COMPLETE,
        alreadyAsked = alreadyAsked,
        lessonComplete = lessonComplete,
        triedForCompletion = triedThisCompletion,
    )

    // ── There is exactly one trigger ─────────────────────────────────────────

    @Test
    fun `the app has one review trigger, and it is a finished lesson`() {
        // This single assertion is what rules out the long list of places a review
        // prompt must never come from: app launch, returning from the background,
        // Home, Settings, the Progress screen, opening a lesson, finishing WATCH,
        // the paywall, a Pro purchase, a billing callback and Restore purchases.
        // None of them can trigger one, because there is no trigger for them to
        // use — and a new one cannot be added by writing a call site.
        assertEquals(listOf(ReviewTrigger.LESSON_COMPLETE), ReviewTrigger.entries.toList())
    }

    @Test
    fun `the decision does not depend on what the learner has paid`() {
        // Free and Pro are asked on the same terms, so entitlement is deliberately
        // not an input at all — the policy cannot see it, so no later edit can make
        // it treat subscribers differently without that showing up as a signature
        // change here. Read off the source, the way `BillingRulesTest` reads the
        // gateway's, because the fact being pinned is about the shape of the file.
        val source = File("src/main/java/com/algorithms/algoking/review/ReviewPolicy.kt").readText()
        for (forbidden in listOf("ProEntitlement", "entitlement", "isPro")) {
            assertTrue(
                "ReviewPolicy must not know what the learner has paid: $forbidden",
                !source.contains(forbidden),
            )
        }
    }

    // ── The one ask ──────────────────────────────────────────────────────────

    @Test
    fun `a first finished lesson is asked`() {
        assertEquals(ReviewDecision.Ask, decide())
    }

    @Test
    fun `a second finished lesson is not asked, because the one ask is spent`() {
        assertEquals(
            ReviewDecision.Suppress(ReviewSuppressed.ALREADY_ASKED),
            decide(alreadyAsked = true),
        )
    }

    @Test
    fun `once asked, no number of finished lessons asks again`() {
        // The requirement in its strongest form. Twenty-eight lessons exist; a
        // learner who finishes all of them is asked exactly once, on the first.
        repeat(28) {
            assertEquals(
                ReviewDecision.Suppress(ReviewSuppressed.ALREADY_ASKED),
                decide(alreadyAsked = true),
            )
        }
    }

    @Test
    fun `an app restart does not bring the ask back`() {
        // `alreadyAsked` is read from DataStore, not from anything in memory, so a
        // restart reads back `true` and lands here. `ReviewStoreTest` pins the
        // persistence half; this pins what the policy does with it.
        assertEquals(
            ReviewDecision.Suppress(ReviewSuppressed.ALREADY_ASKED),
            decide(alreadyAsked = true),
        )
    }

    @Test
    fun `already asked wins over every other condition`() {
        // The ordering that matters: no combination of a finished lesson, a fresh
        // completion and an ad-free screen can reach a learner who has had their
        // one ask.
        for (complete in listOf(true, false)) {
            for (tried in listOf(true, false)) {
                assertEquals(
                    ReviewDecision.Suppress(ReviewSuppressed.ALREADY_ASKED),
                    decide(
                        alreadyAsked = true,
                        lessonComplete = complete,
                        triedThisCompletion = tried,
                    ),
                )
            }
        }
    }

    // ── It follows real progress, not a screen ───────────────────────────────

    @Test
    fun `a lesson that is not finished is not asked about`() {
        // Progress below 100 %: WATCH done and TRY not, or a run that reached the
        // Complete screen without the stage being recorded. Finishing WATCH alone
        // is the case this exists for — it is half a lesson, and the prompt is
        // supposed to follow meaningful progress rather than a screen transition.
        assertEquals(
            ReviewDecision.Suppress(ReviewSuppressed.LESSON_NOT_COMPLETE),
            decide(lessonComplete = false),
        )
    }

    @Test
    fun `a finished lesson is what earns the ask, and nothing else in the run does`() {
        // TRY has no failure state by design (ADR-021): a wrong answer is a
        // learning event, the algorithm does not move, and the run completes
        // whenever the learner gets there. So "TRY failed" is not a state that
        // exists — what the policy checks is whether progress actually reached
        // 100 %, which is the only honest version of the question.
        assertEquals(ReviewDecision.Ask, decide(lessonComplete = true))
        assertEquals(
            ReviewDecision.Suppress(ReviewSuppressed.LESSON_NOT_COMPLETE),
            decide(lessonComplete = false),
        )
    }

    // ── An ad delays the ask; it never spends it ─────────────────────────────

    @Test
    fun `an ad is not an input to this decision at all`() {
        // The coordination is a *sequence*, not a rule: the completion effect waits
        // for the interstitial to be completely dismissed and only then gets here.
        //
        // Suppressing on "an ad was shown" was the first design and it was wrong.
        // A free learner is shown an ad after essentially every completion, so that
        // rule meant a reliably-filling device would never be asked at all — a
        // permanent silence dressed up as politeness. The policy therefore cannot
        // see ads, which is what stops that rule being reintroduced by accident.
        val source = File("src/main/java/com/algorithms/algoking/review/ReviewPolicy.kt").readText()

        // The signature, not the prose — the file explains at length *why* ads are
        // not its business, and that explanation is the point rather than a leak.
        // Pinned as the exact parameter list, so adding an ad input to this
        // decision is a failing test rather than a quiet reintroduction.
        val parameters = source
            .substringAfter("fun decide(")
            .substringBefore("): ReviewDecision")
            .split(",")
            .map { it.substringBefore(":").trim() }
            .filter { it.isNotEmpty() }

        assertEquals(
            listOf("trigger", "alreadyAsked", "lessonComplete", "triedForCompletion"),
            parameters,
        )

        // And the outcomes it can return name no ad reason either.
        assertEquals(
            listOf(
                ReviewSuppressed.ALREADY_ASKED,
                ReviewSuppressed.LESSON_NOT_COMPLETE,
                ReviewSuppressed.ALREADY_TRIED_THIS_COMPLETION,
            ),
            ReviewSuppressed.entries.toList(),
        )
    }

    @Test
    fun `a free learner who was shown an ad is still asked on that same completion`() {
        // The correction in one assertion. The ad has been and gone by the time the
        // decision is made, and the answer is the same as it is for a Pro learner
        // who never saw one: ask.
        assertEquals(ReviewDecision.Ask, decide())
    }

    @Test
    fun `free and Pro reach the same decision, because only the timing differs`() {
        // Pro: settle, then ask. Free: settle, ad, wait for it to go, then ask.
        // Two sequences, one decision — there is no branch here that could drift
        // apart from the other.
        assertEquals(ReviewDecision.Ask, decide(alreadyAsked = false, lessonComplete = true))
    }

    // ── One completion, one attempt ──────────────────────────────────────────

    @Test
    fun `a completion that has already been tried is not tried again`() {
        // What holds the line against recomposition, rotation and a re-entered
        // screen: the attempt is recorded against the completion before the flow is
        // launched, so a second pass over the same completion stops here.
        assertEquals(
            ReviewDecision.Suppress(ReviewSuppressed.ALREADY_TRIED_THIS_COMPLETION),
            decide(triedThisCompletion = true),
        )
    }

    @Test
    fun `recomposing a completion many times yields one ask and then refusals`() {
        // The first pass asks; every pass after it is refused, whatever the screen
        // does in between.
        assertEquals(ReviewDecision.Ask, decide(triedThisCompletion = false))
        repeat(10) {
            assertEquals(
                ReviewDecision.Suppress(ReviewSuppressed.ALREADY_TRIED_THIS_COMPLETION),
                decide(triedThisCompletion = true),
            )
        }
    }

    // ── The two things the app refuses to build ──────────────────────────────

    @Test
    fun `nothing in the app claims to know whether the learner rated anything`() {
        // Play's API reports neither the rating nor whether a dialog was shown, so
        // any field or function named for it would be a fabrication. The flag is
        // named for what actually happened — the flow was attempted.
        val reviewSources = File("src/main/java/com/algorithms/algoking/review")
            .walkTopDown()
            .filter { it.extension == "kt" }
            .toList()
        assertTrue("expected the review package to exist", reviewSources.isNotEmpty())

        val forbidden = listOf("hasRated", "userRated", "didRate", "ratingGiven", "starsGiven")
        for (source in reviewSources) {
            val text = source.readText()
            for (name in forbidden) {
                assertTrue(
                    "${source.name} must not claim to know the learner rated: $name",
                    !text.contains(name),
                )
            }
        }
    }

    @Test
    fun `the completion effect waits for the ad before it asks`() {
        // The correction's actual mechanism, and it is a sequence rather than a
        // rule, so this is where it has to be pinned. One effect: settle, then the
        // ad if there is one, then *suspend until it reports back*, then the
        // review. `InterstitialAds.show` calls back exactly once however it goes,
        // so the wait ends on dismissal, on a failed presentation and on the
        // nothing-to-show path alike.
        val main = File("src/main/java/com/algorithms/algoking/MainActivity.kt").readText()

        assertTrue(
            "the ad must be awaited, not fired and forgotten",
            main.contains("suspendCancellableCoroutine"),
        )

        val adShown = main.indexOf("ads.show(host)")
        val reviewAsked = main.indexOf("reviews.launch(reviewHost)")
        assertTrue("expected both the ad and the review in the completion flow", adShown > 0)
        assertTrue(
            "the review must come after the ad in the same sequence",
            reviewAsked > adShown,
        )

        // And two separate timers would be exactly the thing that lets them
        // collide, so the review's wait is relative to the ad finishing rather
        // than added to the ad's own settle.
        assertTrue(
            "the review must not re-time itself from the completion",
            !main.contains("AD_SETTLE_MS + REVIEW_SETTLE_MS"),
        )
    }

    @Test
    fun `Settings keeps its own manual path to the Play listing`() {
        // Automatic and manual stay separate: the in-app flow is spent once, and
        // "Rate AlgoKing" in Settings keeps opening the store listing forever after
        // — which is the only route left once Play has had its one chance.
        val main = File("src/main/java/com/algorithms/algoking/MainActivity.kt").readText()
        assertTrue(
            "Settings must still open the Play listing",
            main.contains("onRate = { openPlayStoreListing(context) }"),
        )
        assertTrue(
            "Settings must not launch the in-app review flow",
            !main.contains("onRate = { reviews"),
        )
    }
}
