# AlgoKing Pro — access, paywall and billing

**Status:** UI and **Play Billing both connected** · 2026-09-10 (shelf updated 2026-09-17, twice) · the product is not yet configured in Play Console
**Decisions:** ADR-041 (the shelf) · ADR-049 and ADR-050 (lessons that are Pro without being on it)
**Spec:** `PRODUCT_SPEC.md` §1, amended

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
  entitlement is the opposite and must be able to go away on a refund, an expiry or
  a cancellation.

## Billing status — **connected**

`com.android.billingclient:billing:8.0.0`, implemented in `PlayBillingGateway` —
the only file in the app that knows the library exists. It:

- connects with `enableAutoServiceReconnection`, so a transient disconnect is not
  an error the learner has to retry past;
- queries the subscription product and picks the offer, preferring one tagged
  `recommended` in Play Console — **the only thing that earns the "BEST VALUE"
  badge**;
- passes Play's **`formattedPrice` through untouched**, and turns only the ISO 8601
  billing period into words (`P1Y` → *per year*), falling back to the raw value
  rather than guessing at one it does not recognise;
- launches the flow, and **acknowledges** every new `PURCHASED` receipt — Play
  refunds anything unacknowledged after three days;
- derives entitlement **only** from `queryPurchasesAsync`, treating `PENDING` — a
  cash payment or a parental approval in flight — as not entitled;
- leaves entitlement `Unknown` rather than `Free` when the store cannot be reached,
  so a bad network never flickers a paying learner out of their lessons.

`UnconfiguredBillingGateway` survives for unit tests and Compose previews, and
still cannot produce `Pro`.

### What is still needed in Play Console

The app sells the product id **`algoking_pro`** — `PlayBillingGateway.PRO_PRODUCT_ID`,
the only place in the codebase a product is named. It must exist as a
**subscription** (not an in-app product), with at least one base plan and an active
offer.

Until it does, the store answers "no such product", the paywall reports
`NO_PRODUCTS` and the CTA stays disabled. That is what an unconfigured product
looks like from the device, and it is honest: no price is shown and nothing can be
bought.

Tag an offer **`recommended`** to promote it and give it the badge. Nothing in the
app decides that.

### Known gap: refresh on resume

Purchases made *inside* the app arrive through `PurchasesUpdatedListener`; the
state is otherwise re-read when the app starts and when the paywall's retry is
tapped. A subscription refunded or cancelled from the Play Store while the app sits
in the background is therefore noticed on the next start rather than on resume.
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
| purchase succeeds | store re-read; if it owns Pro, the triggering lesson opens |
| cancelled | *"Purchase cancelled. Nothing was charged."* — no error styling, retry available |
| failed | one quiet line carrying the store's message, retry available |
| restored | entitlement re-read, lessons unlock |
| billing unavailable | the reason is stated, CTA disabled, Try again where it can help |

## Analytics

The app has **no analytics implementation** — no SDK, no dependency, no network
calls. `analytics/Analytics.kt` is the interface `ARCHITECTURE.md` §10.4 specified
plus `NoopAnalytics`, so the events exist at their call sites and go nowhere:
`premium_algorithm_tapped` (with the lesson id — the single most valuable one),
`paywall_viewed`, `purchase_started`, `purchase_succeeded`, `purchase_cancelled`,
`purchase_failed`, `restore_started`, `restore_finished`.

No identifiers, no user properties, no free text.

## Tests

66 JVM unit tests in `:app`, no device needed:

- **`ProAccessTest`** — exactly fourteen Pro lessons, by name and without duplicates ·
  the whole Advanced shelf is Pro and no free lesson is Advanced · `PRO_LESSONS` names
  only real lessons, and none of them is also Advanced · fourteen free, Counting Sort
  among them · the partition is total over all 28 · free opens for any entitlement ·
  Pro opens only for a verified one · `Unknown` is not entitled · the rule applied to
  every real catalogue entry, locked and unlocked.
- **AES access** — registered Pro, on the Cryptography shelf and **not** the Advanced
  one · a free learner and an `Unknown` entitlement both get the paywall, and neither
  can resolve to `OpenLesson` · a subscriber opens the lesson · and a companion test
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
  can resolve to `OpenLesson` · a subscriber opens the lesson · and a companion test
  asserts **every other lesson stayed exactly where it was**, including that AES still
  resolves all three ways. This is the first time rule 2 has covered more than one
  lesson, so the test reads the *set* rather than special-casing an id (ADR-050).
- **Fibonacci access** — it is Advanced, so a free learner and an `Unknown`
  entitlement both resolve to the existing paywall and a subscriber opens the
  lesson · and a companion test asserts **every other lesson stayed exactly where
  it was**, free and Pro alike (ADR-045).
- **`SubscriptionRepositoryTest`** — a store-less build entitles nobody and therefore
  no entitlement · the unconfigured gateway cannot sell or restore · a real purchase
  grants Pro · **a purchase reporting success while the store owns nothing grants
  nothing** · cancelled, failed and unavailable all grant nothing · restore works ·
  a withdrawn entitlement is withdrawn here too · the price is the store's string.

Not covered: the paywall's own rendering, which needs a Compose UI test on a
device, and the real Play Billing flow, which needs Play test tracks.

## Still required before release

1. **Configure `algoking_pro` in Play Console** — a subscription, a base plan, an
   active offer, and the plan shape (period, trial, whether an offer is tagged
   `recommended`). None of it is in the app, and until it exists nothing can be
   sold.
2. **A hosted privacy policy URL** for the Play listing. The in-app copy has been
   rewritten for billing — the lessons still send nothing, the paywall asks Google
   Play for the price, and Google handles payment and tells the app one thing back:
   whether a subscription is active. The listing still needs a URL.
4. **Terms of service.** The paywall links Privacy, which exists; there is no Terms
   page, and a subscription needs one.
5. **Decide what happens to existing progress on the fourteen Pro lessons.** Installs
   in the wild have completed some of them. This change locks them — the progress is
   kept and still shows on the card, but the lesson no longer opens. Grandfathering
   is a product decision and has not been made.
6. **Verify on a device**, including a Play test-track purchase, a cancellation, a
   restore on a second device, and a refund.
