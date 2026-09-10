package com.ttele.algoking.ads

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Consent, via Google's User Messaging Platform — `docs/ads.md`.
 *
 * The only file in the app that knows UMP exists, and the only thing that decides
 * whether an ad may be *requested at all*. Whether a loaded ad may be *shown* is
 * still [AdPolicy]'s call; the two are separate questions and neither one is the
 * other's job.
 *
 * ### The flow, per Google's current guidance
 *
 * ```
 * every launch:  requestConsentInfoUpdate      <- regions and rules change
 *                loadAndShowConsentFormIfRequired
 *                     -> UMP decides whether a form is needed at all
 *                canRequestAds()               <- the gate, checked before any load
 * ```
 *
 * Three things follow from that, and all three are requirements rather than
 * preferences:
 *
 * - **The form is never shown "just in case".** `loadAndShowConsentFormIfRequired`
 *   is what decides, from the user's actual region and consent state. Most
 *   learners — this app is India-weighted (`PRODUCT_SPEC.md` §16) — will never see
 *   one, and that is the SDK's answer, not an assumption made here.
 * - **The consent form is Google's**, rendered by the SDK from the Privacy &
 *   Messaging configuration in the AdMob console. There is no hand-rolled dialog:
 *   a custom one would not produce a valid TCF consent string and would not be a
 *   certified CMP.
 * - **Failure never blocks the app.** If the update fails, or the form fails to
 *   load, [canRequestAds] simply stays false: no ads are requested and every
 *   lesson works exactly as it always did. A learner never waits for consent
 *   machinery, and is never told about it.
 *
 * ### Pro
 *
 * A subscriber sees no ads, so there is nothing to consent to and no form is
 * shown to them. The consent *information* is still refreshed, so that a
 * subscriber who previously consented keeps their Privacy options entry and can
 * withdraw consent — and so that a lapsed subscription finds the state already
 * up to date.
 */
class ConsentManager(context: Context) {

    private val appContext = context.applicationContext
    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(appContext)

    private val debuggable =
        (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private val _canRequestAds = MutableStateFlow(false)

    /**
     * Whether ads may be requested at all.
     *
     * Read before every load. It is false until UMP says otherwise, which is the
     * safe direction: no consent state yet means no ad request yet.
     */
    val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

    private val _privacyOptionsRequired = MutableStateFlow(false)

    /**
     * Whether the app must offer a way to change consent.
     *
     * UMP decides. When it is true, Settings grows a **Privacy options** row —
     * being able to withdraw consent is part of having asked for it, not a nicety
     * (`docs/ads.md`).
     */
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    /**
     * Refreshes consent information and shows the form **only if UMP says one is
     * required**. Call on every launch: a learner's region, and the rules for it,
     * can both change between sessions.
     *
     * @param showFormIfRequired false for a Pro subscriber — the information is
     *   still refreshed, but nobody is asked to consent to advertising they will
     *   never see.
     * @param onReady runs once the answer is known, however it turned out. It is
     *   where ad initialisation hangs off, and it fires on the failure paths too
     *   so that nothing is left waiting on a form that never came.
     */
    fun gather(
        activity: Activity,
        showFormIfRequired: Boolean = true,
        onReady: () -> Unit = {},
    ) {
        val params = ConsentRequestParameters.Builder()
            .apply { debugSettings()?.let(::setConsentDebugSettings) }
            .build()

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                if (showFormIfRequired) {
                    // UMP decides whether to show anything. On a device that needs
                    // no form this returns immediately and nothing is displayed.
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                        // The error is deliberately ignored: a form that failed to
                        // load leaves consent ungranted, which `canRequestAds`
                        // already reports. There is nothing to tell the learner.
                        publish()
                        onReady()
                    }
                } else {
                    publish()
                    onReady()
                }
            },
            {
                // Update failed — no network, a misconfiguration, an SDK error.
                // Ads stay unrequested and the app is untouched.
                publish()
                onReady()
            },
        )
    }

    /** Opens Google's own privacy options form, so consent can be changed. */
    fun showPrivacyOptions(activity: Activity, onDismissed: () -> Unit = {}) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) {
            publish()
            onDismissed()
        }
    }

    /** Re-reads both answers from the SDK. */
    private fun publish() {
        _canRequestAds.value = consentInformation.canRequestAds()
        _privacyOptionsRequired.value =
            consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    /**
     * Debug-only: forces a geography so the form can be exercised outside the
     * EEA, and clears any consent already stored.
     *
     * **`reset()` is for testing and nothing else** — calling it in a release
     * build would re-ask every learner on every launch. It is unreachable here in
     * anything but a debuggable build.
     */
    fun resetForTesting() {
        if (debuggable) consentInformation.reset()
    }

    /**
     * Debug settings, or null in a release build.
     *
     * To test the EEA form on a device outside the EEA: run the app once, find the
     * hashed device id UMP logs (`Use new ConsentDebugSettings.Builder()
     * .addTestDeviceHashedId("…")`), and add it to [TEST_DEVICE_HASHED_IDS].
     * Without an id in that list the geography override does nothing, so an empty
     * list is the same as no debug settings at all.
     */
    private fun debugSettings(): ConsentDebugSettings? {
        if (!debuggable || TEST_DEVICE_HASHED_IDS.isEmpty()) return null
        return ConsentDebugSettings.Builder(appContext)
            .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
            .apply { TEST_DEVICE_HASHED_IDS.forEach(::addTestDeviceHashedId) }
            .build()
    }

    private companion object {
        /**
         * Hashed device ids for consent testing, debug builds only.
         *
         * Empty by design: a populated list checked into a release would be a
         * developer's own device driving the geography override. Add one
         * temporarily, test, and take it out again.
         */
        val TEST_DEVICE_HASHED_IDS = emptyList<String>()
    }
}
