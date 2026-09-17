package com.algorithms.algoking.billing

import com.algorithms.algoking.engine.core.AlgorithmId

/**
 * What a lesson costs, and what a tap on it should do — **the one place either
 * question is answered**.
 *
 * Pure Kotlin, no Compose, no Android: the rule is a truth table and is tested as
 * one, the same call ADR-008 made for the ad rules.
 *
 * ### Access is a property of the lesson, never a flag stored on it
 *
 * There is still no `isPro` field on `AlgorithmEntry`. ADR-032 established that
 * **Advanced is a category, not a second taxonomy**, and putting a price flag on
 * every entry would create exactly the parallel axis that ADR refused: two things
 * to keep in step, and eventually an Advanced lesson that is accidentally free.
 *
 * So there are two rules and both live here:
 *
 *  1. **the Advanced shelf is the Pro shelf.** A lesson filed there is protected
 *     the day it is added, with nothing to remember;
 *  2. **[PRO_LESSONS] names the lessons that are Pro despite their shelf.** AES and
 *     RSA are both real ciphers rather than teaching devices, so both belong on the
 *     Cryptography shelf beside the two toy ciphers and the hash that lead up to
 *     them — and both are worth paying for (ADR-049, ADR-050).
 *
 * Rule 2 is deliberately a *set of ids in this file* rather than a flag on the
 * entry, because the failure mode that matters is the one ADR-032 named: a second
 * place where access is decided. There is still exactly one, and both halves of the
 * rule are read in the same breath by [requiresPro].
 */
object ProAccess {

    /** The category whose lessons all require a subscription. */
    const val PRO_CATEGORY: String = "Advanced"

    /**
     * Lessons that are Pro without being on the Pro shelf.
     *
     * **They are here rather than under "Advanced" because a category is a
     * statement about what a lesson is**, and filing a block cipher or a public-key
     * system anywhere but Cryptography would put the wrong word on its card — the
     * same argument ADR-048 used to rename that shelf when a hash function joined
     * two ciphers on it.
     *
     * Keep this small. A long list is a sign the category has stopped describing
     * the library, and the answer then is to fix the categories rather than to grow
     * this set. Two entries, both on one shelf and both for the same reason, is
     * still the rule describing the library rather than fighting it.
     */
    val PRO_LESSONS: Set<AlgorithmId> = setOf(AlgorithmId.AES, AlgorithmId.RSA)

    /**
     * Whether a lesson needs Pro.
     *
     * Both arguments are required on purpose. A caller that knows only the category
     * cannot answer this question correctly for a lesson covered by rule 2, and
     * making that a compile error rather than a silently wrong `false` is the
     * difference between a structural guarantee and a convention someone has to
     * remember — the standard ADR-021 set for decision validation and ADR-041 for
     * entitlement.
     */
    fun requiresPro(category: String, id: AlgorithmId): Boolean =
        category == PRO_CATEGORY || id in PRO_LESSONS

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
    fun decide(
        category: String,
        id: AlgorithmId,
        entitlement: ProEntitlement,
    ): AccessDecision = when {
        !requiresPro(category, id) -> AccessDecision.OpenLesson
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
