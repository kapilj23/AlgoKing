package com.ttele.algoking

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.ttele.algoking.ads.InterstitialAds
import kotlin.concurrent.thread

/**
 * The one place the Mobile Ads SDK is initialised — `docs/ads.md`.
 *
 * `ARCHITECTURE.md` §3 always named this class; it exists now because the ads SDK
 * needs exactly one initialisation for the life of the process. No screen
 * initialises it, and nothing re-initialises it.
 *
 * **On a background thread**, per Google's own guidance: `MobileAds.initialize`
 * does disk and network work, and on a mid-range device — the ones this app is
 * built for (`PRODUCT_SPEC.md` §16) — doing it on the main thread is a visible
 * hitch on the very first frame the learner sees.
 *
 * The first ad is requested here too, so that a learner who finishes a lesson two
 * minutes later has one ready. It is *requested*, never shown: whether it may be
 * shown is [com.ttele.algoking.ads.AdPolicy]'s decision, at exactly one moment.
 */
class AlgoKingApplication : Application() {

    /**
     * Lives as long as the process, holds the application context only, and never
     * an Activity — the Activity is a parameter at show time.
     */
    lateinit var interstitials: InterstitialAds
        private set

    override fun onCreate() {
        super.onCreate()
        interstitials = InterstitialAds(this)

        thread(name = "ads-init", isDaemon = true) {
            MobileAds.initialize(this) {
                // Ready. Fetch the one ad the app will ever show, so it is in hand
                // long before the completion that might use it.
                interstitials.preload()
            }
        }
    }
}
