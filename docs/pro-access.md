# AlgoKing Pro — access, paywall and billing

**Status:** UI and **Play Billing both connected**, against a **one-time product** · 2026-09-18 · unit-tested; **on-device pass still outstanding**
**Decisions:** ADR-041 (the shelf) · ADR-049 and ADR-050 (lessons that are Pro without being on it) · **ADR-051 (one-time product, `buy`)** · **ADR-055 (the purchase confirmation)**
**Spec:** `PRODUCT_SPEC.md` §1, amended twice

---

## What is sold

| | |
|---|---|
| Product id | **`algoking_pro`** — `PlayBillingGateway.PRO_PRODUCT_ID`, the only place a product is named |
| Product type | **One-time product** (`ProductType.INAPP`). **Not a subscription** |
| Purchase option | **`buy`** — `PlayBillingGateway.PRO_PURCHASE_OPTION_ID` |
| What it is | a permanent unlock: bought once, nothing to renew, nothing to cancel |
| Acknowledged | **yes**, every new `PURCHASED` receipt, within Play's three-day window |
| Consumed | **never.** Consuming would tell Play the learner has used it up and may buy it again — a Pro unlock re-sold on the next reinstall |
| Price | Google Play's own localised `formattedPrice`, passed through untouched. **No price, currency or amount is written anywhere in this repository**, and a test asserts it |

---

## What is free and what is Pro

| | Lessons | |
|---|---|---|
| **Free — 14** | Binary Search · Bubble · Selection · Insertion · Merge · Quick · **Counting** · Stack · Queue · Linked List · Hash Map · **Caesar Cipher** · **XOR Cipher** · **SHA-256 Hashing** | complete: both stages, the full guidance ladder, progress |
| **Pro — 14** | Two Pointers · Prefix Sum · Graph DFS · Graph BFS · Dijkstra · Binary Search Tree · AVL Tree · Binary Tree Inorder · Preorder · Postorder · **Fibonacci** · 0/1 Knapsack | the Advanced shelf |
| | **AES** · **RSA** | Pro, and **not** Advanced — see below |

**Free means complete, not crippled.** Pro adds lessons; it never removes anything
from a free one, never gates progress already earned, and never interrupts a free
lesson to advertise itself.

## The access rule

One object, `billing/ProAccess.kt`, and one call site — `MainActivity`'s
`onOpenAlgorithm`:

```
free                  -> open the lesson
pro, and entitled     -> open the lesson
pro, and not entitled -> show the paywall
```

**Access derives from what the lesson is**, never from a flag on the entry. ADR-032
settled that Advanced is a category rather than a second taxonomy, and a price flag
beside it would be exactly that: two things to keep in step. A lesson filed under
Advanced is protected the day it is added.

### The two lessons that are Pro without being Advanced

`PRO_CATEGORY` covers twelve lessons. **AES and RSA are the other two, and both are on the
Cryptography shelf** — they are real ciphers, and filing them under "Advanced" would
print the wrong word on their cards, which is the objection ADR-048 raised when a hash
function was about to be filed under "Encryption".

So `ProAccess` reads two rules in one breath (ADR-049, ADR-050):

```kotlin
const val PRO_CATEGORY = "Advanced"
val PRO_LESSONS = setOf(AlgorithmId.AES, AlgorithmId.RSA)

fun requiresPro(category: String, id: AlgorithmId): Boolean =
    category == PRO_CATEGORY || id in PRO_LESSONS
```

This is **not** the `isPro` flag ADR-032 refused, and the difference is where it
lives: that was a second place access is decided, sitting on every entry and drifting
out of step with the category. This is a set of ids *inside the one object that
answers the question*, read by the one function every caller already goes through.

Three things keep it that way:

- **both arguments are required**, so a caller that knows only the category cannot
  answer wrongly by accident — a compile error, not a silent `false`;
- **rule 1 is untouched**, so an Advanced lesson that is accidentally free remains
  impossible, and a test asserts the whole shelf is still Pro;
- **the set is meant to stay tiny.** Two entries, both on one shelf and both for the
  same reason, is the rule describing the library rather than fighting it. A long
  list would mean the categories have stopped describing it, and the fix then is the
  categories.

`ProEntitlement.Unknown` is not entitled, deliberately. Friction for someone who
owns Pro is corrected by the next purchase-state read; opening a paid lesson for
someone who does not is giving it away.

## Entitlement — the invariant

> **Pro comes from a verified purchase and from nowhere else.**

- `SubscriptionRepository` has **no method that sets Pro**. A caller cannot express
  it.
- `ProEntitlement.Pro` is produced only by a `BillingGateway`, which must derive it
  from queried, acknowledged purchases.
- After a purchase the repository **re-reads what the store owns** rather than
  trusting the outcome. A flow that reports success while the store owns nothing
  grants nothing — there is a test for exactly that.
- Nothing is persisted. Progress is latched because it is earned (ADR-028); an
  entitlement is the opposite and must be able to go away on a refund or a
  revocation by Play.

## Billing status — **connected**

`com.android.billingclient:billing:8.0.0`, implemented in `PlayBillingGateway` —
the only file in the app that knows the library exists. It:

- connects with `enableAutoServiceReconnection`, so a transient disconnect is not
  an error the learner has to retry past;
- enables pending one-time purchases (`enableOneTimeProducts()`), without which
  Play never reports a `PENDING` receipt at all;
- queries `algoking_pro` as **`ProductType.INAPP`** and selects the **`buy`**
  purchase option — or, for a legacy one-time product with a single unnamed offer,
  that one. **A near miss is never substituted**: options that exist but do not
  include `buy` are reported as nothing sellable, because charging for a purchase
  option the app was not built against is worse than not selling;
- passes Play's **`formattedPrice` through untouched**, and prints *one-time
  purchase* under it rather than a billing period, because there is not one;
- launches the flow against that option's offer token, and **acknowledges** every
  new `PURCHASED` receipt — Play refunds anything unacknowledged after three days;
- **never consumes a purchase**, so the unlock stays permanent and cannot be
  re-sold on a reinstall. `BillingRulesTest` reads this file and fails if
  `consumeAsync` ever appears in it;
- derives entitlement **only** from `queryPurchasesAsync` (`INAPP`), treating
  `PENDING` — a cash payment or a parental approval in flight — as not entitled;
- leaves entitlement `Unknown` rather than `Free` when the store cannot be reached,
  so a bad network never flickers a paying learner out of their lessons.

The two decisions in there that are *rules* rather than plumbing — **which receipt
entitles a learner**, and **which purchase option is sold** — live in
`billing/BillingRules.kt` as pure functions over this app's own `Receipt` and
`PurchaseOption` types, which the gateway maps Play's classes into at its boundary.
The gateway cannot be run without a store and a device; the rules run in
milliseconds on a laptop (ADR-051).

`UnconfiguredBillingGateway` survives for unit tests and Compose previews, and
still cannot produce `Pro`.

### What the app expects from Play Console

| | |
|---|---|
| Product id | `algoking_pro` |
| Type | **One-time product**, not a subscription |
| Purchase option id | `buy` |
| State | active, and in a released track the test account can reach |

If the id is missing, inactive, or carries no `buy` option, the store answers
nothing sellable, the paywall reports `NO_PRODUCTS` and the CTA stays disabled —
no price is shown and nothing can be bought, which is the honest state rather than
a crash.

Tag an offer **`recommended`** to promote it and give it the "BEST VALUE" badge.
Nothing in the app decides that, and with a single purchase option there is
nothing to compare it against — the mechanism is kept because it is the store's to
drive, not the app's.

### Known gap: refresh on resume

Purchases made *inside* the app arrive through `PurchasesUpdatedListener`; the
state is otherwise re-read when the app starts and when the paywall's retry is
tapped. A purchase refunded or revoked from the Play Store while the app sits in
the background is therefore noticed on the next start rather than on resume.
Closing that needs a lifecycle-aware refresh (`lifecycle-runtime-compose`, which is
not currently a dependency).

## The paywall

Reached one way only — tapping a locked lesson. Nothing else in the app pushes it.

| Element | |
|---|---|
| hero | gold crown tile at the same 72dp geometry an algorithm tile uses |
| headline | **contextual**: *"Unlock Dijkstra"* with the PRO badge, or the generic *"Unlock AlgoKing Pro"* |
| includes | six lines, each a thing the app actually does — the last says more advanced algorithms are on the way, which is a plan rather than a dated promise |
| plan card | the store's product, or an honest unavailable line with a Try again where retrying can help |
| CTA | one `PrimaryButton`, **Unlock Pro**, disabled when nothing can be sold |
| secondary | Restore purchases · Privacy, equal weight, both quiet |
| the way out | *"Not ready? Continue with the free algorithms"* — a sentence, never a button competing with the CTA |
| back | the header's back tile, system back, and the way-out line all return to Home |

### What it refuses to do

No countdown, no strike-through discount, no invented user counts, no "master DSA
in 7 days", no "guaranteed interview success". All available, all cost more trust
than they earn.

### The mark on a locked card

A small gold **PRO** pill beside the category badge, and nothing else — no dimming,
no padlock over the tile, no greyed title. A locked lesson is an offer, and an offer
that looks broken sells nothing (DESIGN_SYSTEM.md §6.3a).

## Purchase states

| State | What happens |
|---|---|
| no entitlement | lessons locked, paywall on tap |
| purchase succeeds | store re-read; the moment it owns Pro the paywall closes into the triggering lesson, and **"You're All Set!" confirms it** over that lesson (ADR-055) |
| **pending** | **nothing unlocks.** *"Google Play is still processing the payment. Pro unlocks as soon as it completes."* — neither an error nor a success, and the receipt becomes entitling if and when Play reports `PURCHASED` |
| cancelled | *"Purchase cancelled. Nothing was charged."* — no error styling, retry available |
| failed | one quiet line carrying the store's message, retry available |
| restored | entitlement re-read, lessons unlock — **silently**, with no confirmation dialog |
| billing unavailable | the reason is stated, CTA disabled, Try again where it can help |

**The paywall closes on entitlement, not on the purchase call returning.** So it
also closes for a restore, for a pending payment that clears while the screen is
open, and for the startup query arriving after the learner has already tapped a
locked lesson — one trigger, and it is still the store's answer (ADR-051).

### The confirmation — *"You're All Set!"*

Closing into the lesson is the unlock; it is not the app *saying* the payment went
through, and that is the one thing someone who has just paid needs to hear. So a
completed purchase also raises a confirmation over the lesson it opened, with the
Pro lessons already unlocked behind it (ADR-055).

**Its trigger is an event, not an entitlement**, and that distinction is the whole
of its correctness. `entitlement` becomes Pro for six different reasons — a
purchase, a restore, a reinstall, the startup query, a pending payment clearing, a
`BillingClient` reconnect — and exactly one of them is worth congratulating. So
`SubscriptionRepository.purchase` emits `ProUnlocked` and nothing else does, gated
on the store reporting `PURCHASED` **and** the re-read agreeing the learner is now
entitled.

| | |
|---|---|
| Shown for | a purchase completed in a flow this app launched |
| **Not** shown for | a restore · a reinstall · the startup query · a reconnect · a `PENDING` payment, including when it later clears · a cancellation · a failure · opening the paywall · tapping Unlock |
| Shown how often | **once**, for that purchase |
| Dismissal | *Start Learning*, back press, or a tap outside — all the same thing. It clears a local flag and never touches the route, so it cannot reopen the paywall |

"Once" is structural rather than a flag that gets cleared: the event is a
`Channel`, so delivery **consumes** it and there is no retained `true` for a
recomposition, a resume or process death to find. The dialog's visibility is held
in `remember` and deliberately not `rememberSaveable`, so a cold start cannot
resurrect it — the cost being that a rotation while it is open closes it, which is
the smaller wrong.

Visually it is `ProUnlockedDialog`: the app's own card at the app's own 20dp
radius, the crown tile the paywall opened with, and one `PrimaryButton`. Gold
rather than green, because success green means *status* on the algorithm canvas and
gold is the ornament this app already uses for the wordmark's crown, the streak
bolt and the `PRO` pill (`DESIGN_SYSTEM.md` §0.1, §6.3a).

## Analytics

The app has **no analytics implementation** — no SDK, no dependency, no network
calls. `analytics/Analytics.kt` is the interface `ARCHITECTURE.md` §10.4 specified
plus `NoopAnalytics`, so the events exist at their call sites and go nowhere:
`premium_algorithm_tapped` (with the lesson id — the single most valuable one),
`paywall_viewed`, `purchase_started`, `purchase_succeeded`, `purchase_cancelled`,
`purchase_failed`, `restore_started`, `restore_finished`.

No identifiers, no user properties, no free text.

## Tests

102 JVM unit tests in `:app`, no device needed:

- **`ProAccessTest`** — exactly fourteen Pro lessons, by name and without duplicates ·
  the whole Advanced shelf is Pro and no free lesson is Advanced · `PRO_LESSONS` names
  only real lessons, and none of them is also Advanced · fourteen free, Counting Sort
  among them · the partition is total over all 28 · free opens for any entitlement ·
  Pro opens only for a verified one · `Unknown` is not entitled · the rule applied to
  every real catalogue entry, locked and unlocked.
- **AES access** — registered Pro, on the Cryptography shelf and **not** the Advanced
  one · a free learner and an `Unknown` entitlement both get the paywall, and neither
  can resolve to `OpenLesson` · a Pro learner opens the lesson · and a companion test
  asserts **every other lesson stayed exactly where it was**, free and Pro alike
  (ADR-049).
- **Caesar Cipher access** — it is filed under Cryptography, so it opens for every
  entitlement and the paywall is never reached. **XOR Cipher** is the second lesson on
  that shelf and **SHA-256 Hashing** the third, both resolving the same way.
  A test pins the shelf at **five lessons — the two ciphers and the hash free, AES and
  RSA locked** — and that no entry is filed under the old "Encryption" name (ADR-047,
  ADR-048, ADR-049, ADR-050). It used to assert the shelf held *only* free lessons,
  which was worth saying while it was true; what replaces it is the thing that is true
  now and still worth protecting: the three free ones are still free, and exactly two
  are not.
- **SHA-256 access** — free, and a companion test asserts the Pro lessons are
  unchanged after it was added (ADR-048).
- **RSA access** — registered Pro, on the Cryptography shelf and **not** the Advanced
  one · a free learner and an `Unknown` entitlement both get the paywall, and neither
  can resolve to `OpenLesson` · a Pro learner opens the lesson · and a companion test
  asserts **every other lesson stayed exactly where it was**, including that AES still
  resolves all three ways. This is the first time rule 2 has covered more than one
  lesson, so the test reads the *set* rather than special-casing an id (ADR-050).
- **Fibonacci access** — it is Advanced, so a free learner and an `Unknown`
  entitlement both resolve to the existing paywall and a Pro learner opens the
  lesson · and a companion test asserts **every other lesson stayed exactly where
  it was**, free and Pro alike (ADR-045).
- **`SubscriptionRepositoryTest`** — a store-less build entitles nobody and therefore
  no entitlement · the unconfigured gateway cannot sell or restore · a real purchase
  grants Pro · **a purchase reporting success while the store owns nothing grants
  nothing** · cancelled, failed and unavailable all grant nothing · **pending grants
  nothing, and a pending payment that later clears grants Pro without a second
  purchase** · restore works · **clearing local state cannot lose a purchase Play
  still owns** · `Unknown` is neither Pro nor Free · a withdrawn entitlement is
  withdrawn here too · the price is the store's string.
- **The purchase confirmation** (ADR-055), eleven tests over the same fake gateway,
  and every one of them is about *which* route to Pro announces itself. A completed
  purchase unlocks Pro and raises the event, and **taking it a second time finds
  nothing** — which is how "exactly once" is asserted rather than assumed, and the
  same read repeated three times stands in for a recomposition. Nothing is announced
  by: a purchase that reports success while the store owns nothing, a pending
  payment, **a pending payment that later clears**, a cancellation, a failure, an
  unavailable store, **Restore purchases finding a real purchase**, a fresh
  repository over a store that already owns Pro (an app restart), or a reconnect
  re-reporting the same receipt three times over.
- **`BillingRulesTest`** — the product id and purchase option are pinned to
  `algoking_pro` / `buy` · only a `PURCHASED` receipt naming this product entitles
  anyone, and `PENDING`, `UNSPECIFIED` and another product's receipt do not · an
  unacknowledged purchase is acknowledged and an acknowledged one is not
  acknowledged twice, and a pending one is not acknowledged at all · the `buy`
  option is selected, **a non-matching option is never substituted for it**, a legacy
  single unnamed offer is still sellable and two unnamed offers are refused as
  ambiguous · whatever string Play returns is the price shown, in any currency · and,
  read off the gateway's own source: **`consumeAsync` appears nowhere**,
  `acknowledgePurchase` does, the query is `INAPP` and never `SUBS`, pending one-time
  purchases are enabled, and **no price, currency or amount is written anywhere in
  the billing package or the paywall**.

Not covered: the paywall's own rendering and the confirmation dialog's, both of
which need a Compose UI test on a device, and the real Play Billing flow, which
needs Play test tracks. The *rule* the dialog obeys is covered above — which events
raise it and which do not — so what a device pass adds is that it draws correctly
and that dismissing it lands on the unlocked lesson.

## Still required before release

1. **A device pass against Play.** The product is configured; nothing in this build
   has met the real store. Needed: a licence-tested account on an internal track
   buying `algoking_pro`, the price appearing in the account's own currency, the
   paywall closing into the lesson, an app restart still Pro, a reinstall restored
   by **Restore purchases**, a refund taking Pro away on the next start, and — with
   a test instrument — a **pending** payment unlocking nothing until it clears.
   For the confirmation (ADR-055): it appears once after the purchase with the
   lessons unlocked behind it, *Start Learning* lands on that lesson and does not
   reopen the paywall, and it appears **not at all** on the restart, on the
   reinstall-plus-restore, or while the pending payment is in flight or when it
   clears. **`MainActivity`'s temporary local dev override has to come out first** —
   it pins entitlement to `Pro`, so the paywall is unreachable and nothing can be
   bought.
2. **A hosted privacy policy URL** for the Play listing. The in-app copy has been
   rewritten for billing — the lessons still send nothing, the paywall asks Google
   Play for the price, and Google handles payment and tells the app one thing back:
   whether the learner owns Pro. The listing still needs a URL.
4. **Terms of service.** The paywall links Privacy, which exists; there is no Terms
   page, and a paid product needs one.
5. **Decide what happens to existing progress on the fourteen Pro lessons.** Installs
   in the wild have completed some of them. This change locks them — the progress is
   kept and still shows on the card, but the lesson no longer opens. Grandfathering
   is a product decision and has not been made.
6. **Verify on a device**, including a Play test-track purchase, a cancellation, a
   restore on a second device, and a refund.
