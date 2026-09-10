package com.ttele.algoking.analytics

/**
 * The analytics seam — ARCHITECTURE.md §10.4.
 *
 * The app has **no analytics implementation**: no Firebase, no SDK, no network
 * calls, no dependency. What is here is the interface that document specified and
 * a no-op that swallows everything, so the monetization events have somewhere
 * honest to be emitted from and connecting a real backend later is one class
 * rather than a hunt through screens for the places worth measuring.
 *
 * Adding a whole analytics framework to ship a paywall would be the wrong trade;
 * leaving the events unwritten would mean nobody ever learns which lesson makes
 * people look at the price. This is the middle: the call sites exist and are
 * reviewable, and today they cost nothing and send nothing.
 *
 * **Privacy:** every event below carries an algorithm id or nothing at all. No
 * identifiers, no user properties, no free text. When a real implementation
 * arrives it must initialise only after consent (`PRODUCT_SPEC.md` §9), and the
 * privacy copy in Settings has to be rewritten the same day.
 */
interface Analytics {
    fun log(event: MonetizationEvent)
}

/** Sends nothing, anywhere. The only implementation this build has. */
object NoopAnalytics : Analytics {
    override fun log(event: MonetizationEvent) = Unit
}

/**
 * What is worth knowing about the paywall, and nothing more.
 *
 * The single most valuable one is [PremiumAlgorithmTapped]: which locked lesson a
 * learner reached for is the difference between "people do not want Pro" and
 * "people want Dijkstra and never find it".
 */
sealed interface MonetizationEvent {

    /** A locked lesson was tapped. [algorithm] is the lesson's id, e.g. `DIJKSTRA`. */
    data class PremiumAlgorithmTapped(val algorithm: String) : MonetizationEvent

    /** The paywall was shown. [algorithm] is what triggered it, when anything did. */
    data class PaywallViewed(val algorithm: String?) : MonetizationEvent

    data class PurchaseStarted(val algorithm: String?) : MonetizationEvent

    data class PurchaseSucceeded(val algorithm: String?) : MonetizationEvent

    data class PurchaseCancelled(val algorithm: String?) : MonetizationEvent

    /** [reason] is a store-provided code or message, never the learner's words. */
    data class PurchaseFailed(val reason: String) : MonetizationEvent

    data object RestoreStarted : MonetizationEvent

    data class RestoreFinished(val restored: Boolean) : MonetizationEvent
}
