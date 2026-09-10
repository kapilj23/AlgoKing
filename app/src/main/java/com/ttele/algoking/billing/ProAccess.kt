package com.ttele.algoking.billing

/**
 * What a lesson costs, and what a tap on it should do — **the one place either
 * question is answered**.
 *
 * Pure Kotlin, no Compose, no Android: the rule is a truth table and is tested as
 * one, the same call ADR-008 made for the ad rules.
 *
 * ### Access is derived from the category, never stored per lesson
 *
 * There is no `isPro` flag on `AlgorithmEntry`. ADR-032 established that
 * **Advanced is a category, not a second taxonomy**, and putting a price flag
 * beside it would create exactly the parallel axis that ADR refused: two things to
 * keep in step, and a new lesson that is Advanced but accidentally free.
 *
 * So the rule is one line — the Advanced shelf is the Pro shelf — and a lesson
 * added to that category is protected the day it is added, with nothing to
 * remember.
 */
object ProAccess {

    /** The category whose lessons require a subscription. */
    const val PRO_CATEGORY: String = "Advanced"

    /** Whether a lesson in [category] needs Pro. */
    fun requiresPro(category: String): Boolean = category == PRO_CATEGORY

    /**
     * What tapping a lesson should do.
     *
     * ```
     * free                     -> open the lesson
     * pro, and entitled        -> open the lesson
     * pro, and not entitled    -> show the paywall
     * ```
     *
     * [ProEntitlement.Unknown] is treated as not entitled, deliberately: showing
     * the paywall to someone who turns out to own Pro is a moment's friction that
     * the purchase state corrects, while opening a paid lesson for someone who
     * does not is giving it away.
     */
    fun decide(category: String, entitlement: ProEntitlement): AccessDecision = when {
        !requiresPro(category) -> AccessDecision.OpenLesson
        entitlement.isPro -> AccessDecision.OpenLesson
        else -> AccessDecision.ShowPaywall
    }
}

sealed interface AccessDecision {
    /** Free, or paid for. Either way the learner is going straight in. */
    data object OpenLesson : AccessDecision

    /** Locked. The paywall explains what Pro is, and the tap says which lesson asked. */
    data object ShowPaywall : AccessDecision
}
