package com.ttele.algoking.ads

/**
 * The seam a rewarded ad plugs into.
 *
 * There is no Google Mobile Ads SDK in the project yet, and this deliberately
 * does not add one — an ad SDK is a consent flow, a policy class and a store
 * decision, not a dependency to slip in behind a hint button. What exists here
 * is the shape the real one has to fit, so wiring AdMob later is a second
 * implementation of this interface and no change anywhere else.
 *
 * The contract that matters is the callback: **the reward is granted only on
 * [Reward.Earned]**, so a learner who dismisses the ad early gets nothing and
 * loses nothing.
 */
interface RewardedAdHost {
    fun show(placement: RewardedPlacement, onResult: (Reward) -> Unit)
}

/** What a rewarded ad may buy. One slot today; the enum is where the rest go. */
enum class RewardedPlacement {
    /** The next rung of a Challenge hint ladder. Never the first rung. */
    EXTRA_HINT,
}

sealed interface Reward {
    /** Watched to the end. Grant exactly the thing that was offered, nothing more. */
    data object Earned : Reward

    /** Dismissed, failed to load, or offline. Grant nothing, cost nothing. */
    data object Declined : Reward
}

/**
 * Debug stand-in: grants immediately.
 *
 * It exists so the whole unlock flow — offer, accept, decline, grant, record —
 * is exercised and testable before any SDK is present. It must never ship in a
 * release build; the release wiring supplies the real host or no host at all.
 */
class InstantRewardedAdHost : RewardedAdHost {
    override fun show(placement: RewardedPlacement, onResult: (Reward) -> Unit) {
        onResult(Reward.Earned)
    }
}
