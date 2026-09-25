package com.algorithms.algoking.review

/**
 * When the app may ask for a review — and it is once, ever.
 *
 * Pure Kotlin, no Android, no Compose, for the reason ADR-008 gave the ad rules
 * the same treatment: the rules about when to interrupt a learner are subtle, the
 * damage from getting one wrong is out of all proportion to what it earns, and
 * scattering them across screens guarantees drift. So they are a truth table and
 * they are tested as one.
 *
 * ### The whole policy
 *
 * > **Ask once, after the learner has actually finished something. Then never
 * > again, automatically.**
 *
 * [ReviewTrigger] has exactly one member on purpose — the same device
 * [com.algorithms.algoking.ads.Placement] uses. A review prompt cannot be added to
 * Home, to Settings, to the paywall, to a billing callback or to app launch by
 * writing a call site; it takes editing this file, which is where the argument
 * about whether it belongs there should happen.
 *
 * ### What "asked" means, and what it deliberately does not
 *
 * Google Play decides whether the review dialog is actually drawn. It applies its
 * own quotas and eligibility rules, it tells the app nothing about them, and it
 * never reports back what the learner did — there is no star count, no review text,
 * and no "they rated us" callback. Any code here that claimed to know would be
 * inventing it.
 *
 * So the thing that gets recorded is the only thing the app can honestly observe:
 * **the official flow was successfully handed to Play.** Play showing nothing is
 * Play's decision and is not a reason to come back and ask again — an app that
 * retried until it saw a dialog would be nagging on Play's behalf.
 *
 * ### The ad is not this object's business
 *
 * A free learner may be shown the one interstitial on the same screen, and the
 * review must never be drawn over it. That is handled where it belongs — by
 * *sequence*, in the completion effect, which waits for the ad to be completely
 * dismissed before it gets here. It is deliberately **not** a rule in this file:
 * a policy that suppressed on "an ad was shown" would mean a free learner whose
 * ads reliably fill is never asked at all, which is a permanent silence dressed up
 * as politeness. **An ad delays the ask; it never spends it.**
 */
enum class ReviewTrigger {
    /**
     * The learner finished a lesson: WATCH and TRY both done, progress at 100 %,
     * and the Complete screen has had time to land.
     *
     * The only trigger there is. Finishing WATCH is not one, opening a lesson is
     * not one, and neither is launching the app, returning from the background,
     * opening Settings, buying Pro or restoring a purchase.
     */
    LESSON_COMPLETE,
}

/** Why a review was not asked for. */
enum class ReviewSuppressed {
    /**
     * The automatic ask has already been used. The most important one in the list,
     * and checked first — this is the whole of "then leave the user alone".
     */
    ALREADY_ASKED,

    /**
     * The lesson is not actually finished. A run can reach the Complete screen
     * without progress being at 100 %, and half a lesson is not the meaningful
     * progress this prompt is supposed to follow.
     */
    LESSON_NOT_COMPLETE,

    /**
     * This completion has already had its one attempt. What makes the rule hold
     * against recomposition, rotation and a re-entered screen.
     */
    ALREADY_TRIED_THIS_COMPLETION,
}

sealed interface ReviewDecision {
    data object Ask : ReviewDecision
    data class Suppress(val reason: ReviewSuppressed) : ReviewDecision
}

object ReviewPolicy {

    /**
     * Whether Play's review flow may be requested right now.
     *
     * @param trigger what happened. There is one, and it is a finished lesson.
     * @param alreadyAsked whether the flow has ever been successfully launched for
     *   this install, read from [com.algorithms.algoking.data.ReviewStore] rather than
     *   from anything held in memory — it has to survive a restart.
     * @param lessonComplete whether progress for the lesson just finished is at
     *   100 %: WATCH **and** TRY.
     * @param triedForCompletion whether this completion has already had an attempt.
     */
    fun decide(
        trigger: ReviewTrigger,
        alreadyAsked: Boolean,
        lessonComplete: Boolean,
        triedForCompletion: Boolean,
    ): ReviewDecision = when {
        // First, so that no combination of the conditions below can reach a learner
        // who has already been asked once.
        alreadyAsked -> ReviewDecision.Suppress(ReviewSuppressed.ALREADY_ASKED)

        !lessonComplete -> ReviewDecision.Suppress(ReviewSuppressed.LESSON_NOT_COMPLETE)

        triedForCompletion ->
            ReviewDecision.Suppress(ReviewSuppressed.ALREADY_TRIED_THIS_COMPLETION)

        else -> ReviewDecision.Ask
    }
}
