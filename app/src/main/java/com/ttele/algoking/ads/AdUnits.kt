package com.ttele.algoking.ads

/**
 * The AdMob ids — the **only** place in the app that names an ad unit.
 *
 * ### A build is wholly test or wholly production, never a mix
 *
 * An app id and a unit id are a matched pair: a production unit under the test app
 * id does not serve, and the test unit under a production app id is a policy
 * problem. So they are switched together, by build type:
 *
 * | | app id (`AndroidManifest`, via `buildTypes`) | unit id (here) |
 * |---|---|---|
 * | **debug** | `…3940256099942544~3347511713` (Google's test) | [TEST_INTERSTITIAL] |
 * | **release** | `…2478174291729626~9594340402` (production) | [PRODUCTION_INTERSTITIAL] |
 *
 * The app id half lives in `build.gradle.kts` as a manifest placeholder because a
 * manifest value cannot be chosen at runtime; the unit half is chosen here from
 * the debuggable flag. `AdUnitsTest` asserts the two production ids share a
 * publisher, which is what catches "changed one, forgot the other".
 *
 * ### Never test against the production unit
 *
 * Impressions and clicks from a developer's own device are invalid traffic, and
 * AdMob suspends accounts for it. A debug build cannot reach the production unit
 * at all, which is the point of the split. If you need to exercise the real unit —
 * on a release build — register the device in AdMob as a test device first.
 */
object AdUnits {

    /** Google's official sample interstitial. Always fills, safe to tap. */
    const val TEST_INTERSTITIAL: String = "ca-app-pub-3940256099942544/1033173712"

    /** The real interstitial unit. Release builds only. */
    const val PRODUCTION_INTERSTITIAL: String = "ca-app-pub-2478174291729626/4430126130"

    /**
     * The unit this build may request.
     *
     * A pure function of one flag, so the rule is testable without a device: a
     * debuggable build gets the sample unit, and everything else gets the real
     * one.
     */
    fun interstitialFor(debuggable: Boolean): String =
        if (debuggable) TEST_INTERSTITIAL else PRODUCTION_INTERSTITIAL

    /** The publisher half of an id — `ca-app-pub-…`, before the `/` or `~`. */
    fun publisherOf(id: String): String = id.substringBefore('/').substringBefore('~')
}
