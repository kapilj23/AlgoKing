package com.algorithms.algoking.review

import android.app.Activity
import android.content.Context
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Google Play's In-App Review flow, behind one function.
 *
 * The only file in the app that touches the Play Review API. Whether the app *may*
 * ask is [ReviewPolicy]'s decision and is pure; this only knows how to ask Play and
 * how to put Play's own dialog on screen. It is the same split `InterstitialAds` and
 * `AdPolicy` have, for the same reason.
 *
 * There is **no custom review UI**, no star picker and no comment box. Play's sheet
 * is the whole interaction: a rating collected anywhere else could not be submitted
 * to the store, so an in-app star widget would be a form that throws its answer
 * away.
 *
 * ### It never holds an Activity
 *
 * Built with the **application** context; the Activity arrives as a parameter to
 * [launch] and is used and dropped inside the call. `InterstitialAds` documents why:
 * a long-lived object with an Activity field is the classic leak, and it is also how
 * a dialog ends up being shown into a window that is already finishing.
 *
 * ### Failure is never the learner's problem
 *
 * Every failure path — no Play Store, an out-of-date one, a `ReviewErrorCode`, a
 * launch that does not take — ends the same way: [launch] returns false, the lesson
 * carries on, and **nothing is said to the learner.** There is no error dialog, no
 * retry loop and no placeholder. The completion screen looks identical whether the
 * flow ran or not.
 */
class InAppReviewManager(context: Context) {

    private val manager = ReviewManagerFactory.create(context.applicationContext)

    /**
     * Asks Play for a review flow and hands it over.
     *
     * @return true when the flow was **successfully launched** — and note what that
     *   does and does not claim. It does not mean the learner saw a dialog: Play
     *   applies its own quotas and eligibility rules and may show nothing at all. It
     *   does not mean the learner rated anything: Play never reports that back, to
     *   any app. It means only that the official flow ran, which is the single
     *   honest fact available here and is therefore what the caller records.
     *
     * False means the ask did not happen, so the caller must **not** mark it used —
     * a future finished lesson may try again.
     */
    suspend fun launch(activity: Activity): Boolean = runCatching {
        // Play builds the flow first. This is the half that fails when there is no
        // Play Store, when it is too old, or when the account cannot review.
        val info = manager.requestReview()
        // ...and this is the half that either shows something or quietly does not.
        // Play decides; the app is not told which happened.
        manager.launchReview(activity, info)
        true
    }.getOrElse {
        // Swallowed on purpose, and not logged to the learner in any form. An app
        // that reported "we could not ask you for a review" would be drawing
        // attention to the one thing that does not matter to them.
        false
    }
}
