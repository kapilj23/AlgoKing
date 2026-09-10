package com.ttele.algoking

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.ttele.algoking.ads.ConsentManager
import com.ttele.algoking.ads.InterstitialAds
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * The one place the Mobile Ads SDK is initialised — `docs/ads.md`.
 *
 * `ARCHITECTURE.md` §3 always named this class; it exists because the ads SDK
 * needs exactly one initialisation for the life of the process. No screen
 * initialises it, and [initializeAdsOnce] cannot be made to run twice.
 *
 * ### Nothing starts until consent says so
 *
 * The SDK is **not** initialised in [onCreate]. Google's guidance is to gather
 * consent first and only then request ads, so the sequence is:
 *
 * ```
 * launch -> ConsentManager.gather(activity)      (every launch)
 *        -> canRequestAds() && not Pro
 *        -> initializeAdsOnce() -> preload
 * ```
 *
 * A learner who declines, whose region requires a form they dismiss, or who is
 * simply offline, ends up with the SDK never initialised and no ad ever
 * requested — and every lesson works exactly as it always did.
 */
class AlgoKingApplication : Application() {

    /**
     * Lives as long as the process, holds the application context only, and never
     * an Activity — the Activity is a parameter at show time.
     */
    lateinit var interstitials: InterstitialAds
        private set

    /** UMP. Consent state outlives any one screen, so it lives here too. */
    lateinit var consent: ConsentManager
        private set

    private val adsStarted = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        interstitials = InterstitialAds(this)
        consent = ConsentManager(this)
    }

    /**
     * Initialises the ads SDK and fetches the first ad — **at most once**, however
     * many times it is called.
     *
     * The caller has already established that ads may be requested
     * ([com.ttele.algoking.ads.AdPolicy.mayRequestAds]); this only guarantees that
     * the SDK is started a single time.
     *
     * **On a background thread**, per Google's own guidance: `MobileAds.initialize`
     * does disk and network work, and on a mid-range device — the ones this app is
     * built for (`PRODUCT_SPEC.md` §16) — doing it on the main thread is a visible
     * hitch.
     */
    fun initializeAdsOnce() {
        if (!adsStarted.compareAndSet(false, true)) return

        thread(name = "ads-init", isDaemon = true) {
            MobileAds.initialize(this) {
                // Ready. Fetch the one ad the app will ever show, so it is in hand
                // long before the completion that might use it. Requested, never
                // shown: that decision is `AdPolicy`'s, at exactly one moment.
                interstitials.preload()
            }
        }
    }
}
