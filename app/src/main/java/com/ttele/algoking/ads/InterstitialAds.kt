package com.ttele.algoking.ads

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * The one ad surface in the app — `docs/ads.md`.
 *
 * This is the only file that touches the Mobile Ads SDK. Whether an ad *may* be
 * shown is [AdPolicy]'s decision and is pure; this class only knows how to have
 * one ready and how to put it on screen.
 *
 * ### It never holds an Activity
 *
 * The instance lives as long as the app, so it is built with the **application**
 * context and the Activity arrives as a parameter to [show], used and dropped
 * within the call. A long-lived singleton with an Activity field is the classic
 * leak, and it is also how an ad ends up being shown into a window that is
 * already finishing.
 *
 * ### Failure is never the learner's problem
 *
 * Every failure path — no fill, no network, SDK error, a show that does not take —
 * ends the same way: the callback fires, the app carries on, and the next ad is
 * requested quietly. Nothing blocks, nothing retries in a loop, and nothing is
 * ever said to the learner about an ad.
 */
class InterstitialAds(context: Context) {

    private val appContext = context.applicationContext

    /**
     * Which unit this build requests: the sample one in a debuggable build, the
     * real one otherwise. Read from the installed application's own flag rather
     * than from a constant, so it cannot disagree with what was actually built.
     */
    private val adUnitId: String = AdUnits.interstitialFor(
        debuggable = (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0,
    )

    private var loaded: InterstitialAd? = null
    private var loading = false

    /**
     * Consecutive failures, so a device that cannot fill stops asking. Reset by
     * any success — this is a brake, not a retry loop.
     */
    private var failures = 0

    /** True when there is an ad in hand. Read by [AdPolicy], never assumed. */
    val isReady: Boolean get() = loaded != null

    /**
     * Requests one ad, if there is not one already loaded or in flight.
     *
     * Called once after the SDK initialises and once after an ad is consumed —
     * never speculatively per screen, and never more than one at a time.
     */
    fun preload() {
        if (loaded != null || loading) return
        if (failures >= MAX_CONSECUTIVE_FAILURES) return

        loading = true
        InterstitialAd.load(
            appContext,
            adUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    loading = false
                    failures = 0
                    loaded = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    // No fill, no network, a bad unit id — all the same from here.
                    // The learner is not told and nothing is retried immediately.
                    loading = false
                    failures++
                    loaded = null
                }
            },
        )
    }

    /**
     * Shows the loaded ad, and calls [onFinished] exactly once however it goes.
     *
     * The callback is the app's cue to carry on, so it fires on dismissal, on a
     * failed presentation, and on the "there was nothing to show" path too. A
     * caller therefore never has to handle ads failing — it just continues.
     */
    fun show(activity: Activity, onFinished: () -> Unit = {}) {
        val ad = loaded
        if (ad == null || activity.isFinishing || activity.isDestroyed) {
            // Nothing to show, or nowhere to show it. Not an error.
            onFinished()
            preload()
            return
        }

        // Consumed the moment it is handed over: an InterstitialAd may be shown
        // once, and clearing it here is what makes a second show impossible even
        // if something calls twice.
        loaded = null
        var finished = false
        fun finishOnce() {
            if (!finished) {
                finished = true
                onFinished()
            }
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                finishOnce()
                // The next one is fetched only after this one is spent.
                preload()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                finishOnce()
                preload()
            }
        }

        ad.show(activity)
    }

    /**
     * Throws away anything loaded, and stops loading more.
     *
     * Called the moment Pro becomes active: an ad bought and paid out of before
     * the subscription must not be shown after it (`docs/ads.md`). [AdPolicy]
     * would refuse it anyway — this makes it impossible twice over.
     */
    fun discard() {
        loaded = null
        failures = MAX_CONSECUTIVE_FAILURES
    }

    /** Undoes [discard], for a subscription that lapses inside one session. */
    fun resume() {
        if (failures >= MAX_CONSECUTIVE_FAILURES) failures = 0
    }

    private companion object {
        /**
         * After three failures in a row the device is probably offline or the
         * unit has no fill; asking again on every completion would burn battery
         * to no purpose. Any success clears it.
         */
        const val MAX_CONSECUTIVE_FAILURES = 3
    }
}
