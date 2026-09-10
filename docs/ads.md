# Advertising in AlgoKing

**Status:** implemented · production ad units configured · 2026-09-10
**Decision:** ADR-042 · **Spec:** `PRODUCT_SPEC.md` §9, amended

---

> **AlgoKing uses interstitial ads only for Free users after successful TRY
> completion. AlgoKing does not use banner or rewarded ads. Pro users see no ads.**

## The whole policy

| | |
|---|---|
| Format | **Interstitial only.** No banner, no rewarded, no native, no app-open, no adaptive anything. |
| Who | **Free learners only.** A Pro subscriber never sees one, in any state. |
| When | **After a TRY run is finished**, on the Complete screen, once the learner has had their result. |
| How often | **At most one per completion.** |
| Everywhere else | Nothing. Not on Home, Settings, the paywall, navigation, app launch, WATCH, or TRY. |

`Placement` is an enum with exactly one member. A second placement cannot be added
by writing a call site — only by editing that file, which is where the argument
about whether it should exist belongs.

## Where the ad appears, precisely

```
TRY reaches its terminal state
        ↓
learner taps "Finish lesson"          <- completionId += 1
        ↓
COMPLETE screen renders: decisions, comparisons, wrong turns, the takeaway
        ↓
1.2s settle                            <- the feedback lands first
        ↓
AdPolicy.decide(...)
   Pro?                 -> no ad, ever
   already shown here?  -> no ad
   nothing loaded?      -> no ad, no wait
   otherwise            -> show the one interstitial
        ↓
dismissed (or failed, or never shown) -> the learner is back on COMPLETE
```

The learner **always** receives the completion feedback before anything covers it,
and the ad never sits between them and a lesson.

### Why on entering COMPLETE rather than on leaving it

`PRODUCT_SPEC.md` §9 originally specified "ads fire on exit paths, never forward
paths", which would put the interstitial on the tap that leaves the Complete
screen. That is not what shipped, and the difference is deliberate: two of the
three exits from Complete — *Next algorithm* and *Try again* — go **into** more
learning, and an ad on those is the forward-path interstitial the same section
forbids. Firing on arrival, after a settle, is the only placement that is both
after the learning and not attached to a navigation tap.

## Duplicate protection

Each finished run mints a `completionId`. The policy refuses any completion whose
id it has already shown for, and the id is `rememberSaveable`, so a rotation or a
process death cannot resurrect the opportunity. `InterstitialAds.show` also clears
the loaded ad *before* presenting it, so even a double call has nothing to show
twice.

## Availability never blocks learning

Failed load, no fill, no network, no Activity, an SDK error, a show that does not
take — every one of them ends the same way: the app carries on and the next ad is
requested quietly. Nothing retries in a loop (three consecutive failures and it
stops asking until something succeeds), nothing blocks, and **nothing is ever said
to the learner about an ad**. There is no ad container in the UI, no placeholder,
no "watch an ad" button, and the app looks identical when no ad is showing.

## Pro

Checked first, so no combination of the other conditions can reach a subscriber.
Beyond the policy refusing it, the moment entitlement turns Pro the loaded ad is
**discarded** and loading stops — an ad fetched while the learner was free is not
shown to them after they subscribe. Entitlement comes from the existing billing
layer (ADR-041); there is no second Pro flag.

## Architecture

```
AlgoKingApplication      MobileAds.initialize, once, on a background thread
        ↓
InterstitialAds          load / ready / show / dismiss / failure / preload
        ↓                (application context only — the Activity is a show-time
                          parameter, never a field)
AdPolicy.decide(...)     pure, no Android, table-tested
        ↓
MainActivity             the one call site
```

No ad code exists in `:engine`, in any lesson screen, or in any algorithm. The ad
is a monetization layer around a completed learning event, and the engine remains
the source of truth for everything about the lesson itself.

## Ad units — configured, and split by build type

The production ids are in. **A build is wholly test or wholly production, never a
mix:**

| | app id (manifest) | interstitial unit |
|---|---|---|
| **debug** | `ca-app-pub-3940256099942544~3347511713` | `ca-app-pub-3940256099942544/1033173712` |
| **release** | `ca-app-pub-2478174291729626~9594340402` | `ca-app-pub-2478174291729626/4430126130` |

The app-id half lives in `build.gradle.kts` as a per-build-type
`manifestPlaceholders["admobAppId"]`, because a manifest value cannot be chosen at
runtime. The unit-id half is chosen in `AdUnits.interstitialFor(debuggable)`, read
from the installed app's own `FLAG_DEBUGGABLE` — so it cannot disagree with what
was actually built. Verified in the merged manifests of both variants.

**A debug build cannot reach the production unit at all.** That is the whole
point of the split: impressions and clicks from a developer's own device are
invalid traffic, and AdMob suspends accounts for it. To exercise the real unit,
build release *and* register the device in AdMob as a test device first.

Three tests hold this: a debug build resolves to the sample unit, the two
production ids are pinned exactly, and — reading `build.gradle.kts` from the test
— the app ids and unit ids belong to the same two accounts. That last one is the
"changed one, forgot the other" mistake, caught by a test rather than by an ad
unit that silently never fills.

## Privacy and Play Console

The in-app privacy copy has been updated in the same change: it now says free
learners see one ad after the practice stage and nowhere else, that Google's ad
service uses the device's advertising ID to choose and measure it, that the ID can
be reset or deleted in Android's settings, and that Pro subscribers see no ads.
The App screen's "No ads" claim is gone.

**Data Safety** must be filled in to match what the SDK actually does — the Mobile
Ads SDK collects the advertising ID and approximate device/usage signals for ads.
Declare what it does and nothing more; the app itself still collects nothing, has
no analytics, and reads nothing about what the learner does in a lesson.

`com.google.android.gms.permission.AD_ID` is merged in by the SDK's own manifest;
it is required for targetSdk 33+ and must be declared in Data Safety.

## Still required before release

1. **UMP / consent.** `PRODUCT_SPEC.md` §9 requires the UMP consent SDK in the
   first build for GDPR/DMA, and **it is not implemented**. Serving personalised
   ads in the EEA/UK without a consent flow is a compliance problem, not a polish
   item. This is the blocker.
2. **Data Safety declarations** in Play Console.
3. **A device pass**: test ad shows after a free completion, does not show for
   Pro, does not show twice, and the app carries on when it fails to load.
