| 2 | ~~Pass Prediction (Format B) ships for the three sorts~~ — **moved to V2 by ADR-031** with the rest of CHALLENGE | — |
| 5 | ~~Dark theme only in v1 (ADR-011)~~ — **overruled by ADR-016**: v2 is a light theme | — |
| 6 | **CHALLENGE ships in V2, not the MVP (ADR-031)** | the whole assessment half of the product — `docs/v2-challenge.md` |# AlgoKing — Decision Log

Architecture Decision Records. Newest last. Each entry records the decision, why, what else was
considered, and why the alternatives lost.

**Escalation rule:** decisions that materially change product behaviour, MVP scope, monetization,
UX, privacy, or infrastructure cost are flagged **⚠ NEEDS OWNER SIGN-OFF** and listed in §Open
at the bottom. Everything else is made and documented, not asked.

---

## ADR-001 — Algorithms are pure resumable state machines, not precomputed traces

**Decision.** Every algorithm implements `probe(state) → Probe` and `apply(state, action) →
Transition`, both pure. Traces are *materialised* by running the machine, not authored.

**Why.** Three product requirements are mutually exclusive under any simpler model:
"previous step" needs the past to still exist; "execute the learner's wrong choice" needs to
reach states the correct path never visits; "mercy exit" needs to resume from an arbitrary
mid-lesson state. A resumable pure machine satisfies all three with one mechanism.

**Alternatives.**
- *Precomputed trace only.* Cannot express wrong-choice consequences — the diverged state is not
  in the trace. Would need a second, parallel "wrong branch" authoring system per decision point.
- *Live animating loop (the classic visualizer approach).* Cannot express previous-step or
  rewind; state is destroyed as it goes. Also entangles timing with logic.
- *Event-sourced with replay-from-zero.* Works, but rewind becomes O(n) recomputation per tap
  and the algorithm still needs to be resumable to branch.

**Why this won.** It is the only model where wrong-choice consequence is *not a feature* — it is
`apply()` called with a different argument. The most distinctive mechanic in the product costs
zero additional machinery.

---

## ADR-002 — Four Gradle modules, with `:engine` as a pure JVM module

**Decision.** `:engine` (JVM), `:design` (Android lib), `:app`, plus root. No feature modules.

**Why.** The spec requires algorithm logic to be independent of Compose. Making `:engine` a
**JVM** module means `import androidx.compose.*` does not compile — the rule is enforced by the
toolchain rather than by code review. It also makes engine tests run in milliseconds with no
Robolectric, which matters because the property-based suites run thousands of seeds.

**Alternatives.**
- *Single module with package discipline.* Simplest, but the separation is a convention that
  erodes. One `@Composable` helper in an algorithm file and the boundary is gone.
- *Full feature-module split (`:feature:home`, `:feature:lesson`, …).* Real cost in build config
  for a solo project with eight screens; buys parallel build time we do not need yet.

**Why this won.** Two boundaries, each enforcing a rule the product explicitly asked for. No
boundary exists for tidiness.

**Known leak, deliberately accepted.** `:design` depends on `:engine` for the `SequenceScene`
model. Documented tripwire: if `:design` ever imports from `engine.algorithms.*`, extract a
fifth `:scene` module immediately.

---

## ADR-003 — Event vocabulary is named for rendering, not for algorithms

**Decision.** Rejected the literal `COMPARE / SWAP / KEEP / PUSH / POP / ENQUEUE / DEQUEUE /
FOUND / NOT_FOUND` list. Shipped 13 render-oriented events instead, with `Insert`/`Remove`
covering push/pop/enqueue/dequeue and `Terminal(Outcome)` covering found/not-found.

**Why.** `PUSH`, `POP`, `ENQUEUE` and `DEQUEUE` are four names for two operations. Encoding
algorithm vocabulary in the event stream forces the renderer to learn nine vocabularies, which
produces nine visualizers — the exact outcome the brief forbids.

**Alternatives.**
- *The literal list.* Renderer gains a `when` over algorithm-specific verbs; adding an algorithm
  adds events and renderer branches. Fails the "one renderer" requirement by construction.
- *Fully generic diff events (`CellChanged(index, before, after)`).* Too generic — the renderer
  could not tell a swap (arc motion) from an elimination (collapse), and metrics would be
  unrecoverable.

**Why this won.** Algorithm vocabulary moves to narration keys, where it is user-facing copy
rather than structure. **Acceptance test: adding Binary Search must add zero new event types.**

---

## ADR-004 — Manual DI via a single `AppContainer`, not Hilt

**Decision.** One `AppContainer` constructed in `Application.onCreate`, `viewModelFactory { }`
per screen. Revisit at ~25 graph types.

**Why.** The graph is roughly twelve singletons. Hilt would add KSP to every module, a dozen
annotations per feature, and build time, to solve a problem that does not exist at this size.

**Alternatives.** *Hilt* — standard, and the right answer at scale; not at this scale.
*Koin* — a runtime dependency and runtime failure modes to replace compile-time constructor calls.

**Why this won.** "Prefer the simplest architecture that satisfies the product." A container of
twelve constructor calls satisfies it. The revisit trigger is written down so this does not
become dogma.

---

## ADR-005 — Room for structured data, DataStore for settings

**Decision.** Room: `AlgorithmProgress`, `ChallengeAttempt`, `DailyResult`, `AchievementUnlock`.
DataStore Preferences: settings, session counters, ad state, consent, onboarding flags.

**Why.** `DailyResult` is one row per day, forever — genuinely unbounded — and the streak
calendar is a date-range query, personal bests are `MIN`/`MAX` aggregates, and Home's primary
card is driven by a reactive query. That is a database. Settings are a flat map that is written
often and never queried. That is a key-value store.

**Alternatives.**
- *DataStore Proto for everything.* Would work at MVP volume, but the streak calendar becomes a
  full deserialise-and-scan, and every schema change rewrites the whole blob.
- *Room for everything.* Settings in SQLite is ceremony without benefit.

**Why this won.** Standard split, each store used for what it is good at, neither over-applied.
**`fallbackToDestructiveMigration` is never enabled** — losing a 90-day streak to a schema change
is the one-star-review scenario in `PRODUCT_SPEC.md` §14.

---

## ADR-006 — Challenge constraints are validated against the trace, not the array

**Decision.** `Constraint.holds(trace, dataset)`. The generator draws a candidate from a seeded
RNG, runs the real algorithm headlessly, and accepts only if every constraint holds against the
resulting trace. 200 attempts, then a bundled curated fallback.

**Why.** `PRODUCT_SPEC.md` §6 requires the challenge's *solution path* to differ from the Watch
example, not just its values. That is a property of the execution, not of the numbers. Asserting
it against the trace makes it **impossible** to generate a pedagogically invalid dataset.

**Alternatives.**
- *Hand-authored challenge datasets.* Defeats "new data every time"; a returning learner would
  see the same challenge.
- *Array-shape heuristics ("must not be nearly sorted").* Proxy metrics that do not actually
  guarantee the property we care about, and would need re-tuning per algorithm.

**Why this won.** The generator reuses `runToCompletion` — the same code path Watch uses. One
execution path in the system, and correctness by construction rather than by heuristic.

---

## ADR-007 — Stable `CellKey` identity, never index-keyed rendering

**Decision.** Every cell gets an identity at dataset creation that survives every swap. A swap
changes two cells' `slot`, not their identity.

**Why.** If cells are keyed by index, a swap is "two boxes changed their numbers" and Compose
cross-fades text. If cells are keyed by identity, a swap is "two boxes moved", and the arc
animation from `DESIGN_SYSTEM.md` §6 becomes possible. The animation *is* the teaching
(`PRODUCT_SPEC.md` §1) so this is a product requirement, not a rendering nicety.

**Alternatives.** Index-keyed with manual cross-fade — cheaper, and produces exactly the flicker
that makes existing visualizers hard to follow.

**Why this won.** It is the difference between the app teaching and the app blinking. A test
asserts key stability across every `Swap` in every trace.

---

## ADR-008 — `AdPolicy` is a pure, table-tested class; ads are unreachable from learning code

**Decision.** All ad rules live in one pure Kotlin class with no Android imports.
`Placement` distinguishes `FORWARD` from `EXIT`; `FORWARD` is hard-coded to suppress. `AdHost`
is injected only into navigation-level exit handlers — no lesson composable can reach it.

**Why.** The rules in `PRODUCT_SPEC.md` §9 are numerous and subtle (first session, first three
lessons, 180 s gap, four per session, milestone suppression, notification-launch suppression).
Getting one wrong costs more product damage than a missed impression earns. Scattering them
across navigation callbacks guarantees drift.

**Alternatives.** Inline checks at each call site — untestable and certain to diverge.
An ad SDK wrapper with the policy inside it — couples policy to the Android SDK and to
instrumentation tests.

**Why this won.** The rules become a truth table with unit tests. And structural unreachability
means "just add a small banner to the challenge screen" is not something a future change can do
by accident — it would require a new dependency edge that shows up in review.

`AdSuppressed` is logged to analytics deliberately, so the rules can be verified as firing in
production rather than assumed.

---

## ADR-009 — minSdk raised 24 → 26; JVM target 11 → 17

**Decision.** minSdk 26, JVM target 17, compileSdk/targetSdk unchanged at 36.1/36.

**Why.** minSdk 26 gives mandatory notification channels without version branching, adaptive
icons, and `java.time` without core-library desugaring. The app's entire retention loop is a
daily notification (`PRODUCT_SPEC.md` §14), so notification-path complexity is worth removing.
Cost is roughly 1.5 % of the addressable device base. JVM 17 is required by the AGP 9 toolchain.

**Alternatives.** Keep 24 and desugar — carries a compat branch on the single most
retention-critical code path, permanently.

---

## ADR-010 — Two font families, not three

**Decision.** Archivo (display + interface) and JetBrains Mono (data + code). The published spec
proposed a third face (IBM Plex Sans) for body text; cut.

**Why.** At 13–17sp on a phone the third family was not distinguishable from Archivo at those
weights, and it cost APK weight and a third variable font to bundle.

**Alternatives.** Three bundled families (weight, no visible gain). Downloadable Google Fonts
(risk of silent fallback on devices without the provider — unacceptable on the one screen that
must look premium).

**Why this won.** Fewer moving parts, identical result. Recorded because it is a visible
divergence from the published spec.

---

## ADR-011 — Dark theme only in v1

**Decision.** Ship dark. Build the token structure for light. Validate and ship light in v1.1.

**Why.** All six data-semantic colours need independent contrast validation on a light ground,
and `dataEliminated` (ink @ 22 %) behaves very differently on white. A half-validated light
theme would silently break the semantic contract that the entire learning transfer depends on.

⚠ **This is a product-visible scope decision** — flagged in the open list below, though
`PRODUCT_SPEC.md` §15 already carries it as an assumption.

---

## ADR-012 — MVI-style reducer for the lesson screen only

**Decision.** Plain MVVM for Home, Journey, Progress, Hub, Settings. A pure `LessonReducer` for
the lesson screen.

**Why.** The lesson genuinely is a state machine with rewind, replay, attempt counting, a
guidance ladder and phase transitions. A reducer is the shape of that problem. The other screens
map a repository flow to a UI state and would gain nothing but ceremony.

**Alternatives.** MVI everywhere (ceremony on five simple screens). MVVM everywhere (the lesson's
ladder logic ends up as mutable ViewModel fields, which is where rewind bugs live).

**Why this won.** Use the pattern where it earns its keep. No MVI framework is added — the
reducer is a function.

---

## ADR-013 — Firebase Analytics + Crashlytics, no custom backend

**Decision.** Firebase behind an `Analytics` interface, `NoopAnalytics` in debug and tests.
Initialised only after UMP consent.

**Why.** Zero-ops, free at this volume, and already implied by the Play Services dependency
AdMob requires. The single most valuable signal in the app is `DecisionMade(algorithmId,
stepIndex, correct, attempt)` — it identifies exactly which step of which algorithm confuses
learners, which is the content-quality feedback loop the product needs to improve.

**Alternatives.** No analytics (flying blind on the one thing worth measuring). A custom
endpoint (violates "no backend unless genuinely required" and adds ops).

**Privacy:** no PII, no email, no identifiers beyond the Firebase instance ID, gated on consent,
no user-generated content ever leaves the device.

---

## ADR-014 — Watch datasets are authored; only Challenge and Daily are generated

**Decision.** Watch and Try use hand-picked datasets committed as assets. Challenge and Daily use
the seeded generator.

**Why.** The teaching example must be perfect. Bubble Sort's `[7, 3, 8, 2, 5]` produces
swap → keep → swap in the first three comparisons, which is exactly the rhythm the lesson needs
to establish. A generator satisfying "size 5, distinct, unsorted" would produce that only
sometimes, and a mediocre first example damages the highest-leverage 60 seconds in the product.

**Alternatives.** Generate everything with tight constraints — possible, but it makes the most
important content in the app a search problem instead of an authored one.

---

## ADR-015 — Narration is emitted as keys, resolved in `:app`

**Decision.** `:engine` emits `NarrationKey(id, args)`. `:app` maps to string resources.

**Why.** Keeps `:engine` a pure JVM module with no Android `R` reference (which is what makes
ADR-002 possible), and makes the app translatable without touching algorithm code.

**Alternatives.** Raw strings in the engine — would work, but pins the engine to English and
mixes copy into logic. A separate copy module — unnecessary indirection at this size.

---

## ADR-016 — The reference image is the visual source of truth; v2 is a light theme

**Decision.** `DESIGN_SYSTEM.md` is rewritten from the approved three-screen reference
(`app/src/main/res/drawable/ref.png`). Colours, radii, spacing, elevation and typography are
sampled from that image, not designed. The v1 dark system is archived at
`docs/DESIGN_SYSTEM_v1_dark_superseded.md`.

**Why.** The reference was approved by the product owner as three finished screens. A design
system derived from an approved artefact is verifiable — every token can be traced back to a
pixel — whereas one derived from principles has to be re-litigated on every screen.

**Consequence.** This supersedes **ADR-011 (dark theme only in v1)** and the `dark theme only`
line in `PRODUCT_SPEC.md` §15. Dark becomes a v1.1 counterpart that inverts
`background`/`surface`/text and keeps every hue token, including the whole viz palette,
unchanged.

**Alternatives.** *Keep the dark system and re-skin the reference.* Rejected: it discards the
one approved artefact in the project. *Ship both from day one.* Rejected: two themes double
the contrast-validation surface before a single lesson exists.

---

## ADR-017 — There are three learning stages, and mastery is a status

**Decision.** The lesson spine is **WATCH → TRY → CHALLENGE**, followed by a Result screen.
`MASTER` is removed as a stage everywhere: from `StageStepper`, from the lesson screens, and
from `PRODUCT_SPEC.md` §2. Mastery is awarded on the Result screen (★★ or better) and shown
on the Home card. `StageStepper` renders exactly one node per stage, by contract.

> **Amended by ADR-031 (2026-09-04):** CHALLENGE is deferred to V2, so the MVP spine is
> **WATCH → TRY** and the stepper renders two nodes. The rule this ADR establishes is unchanged
> and is the reason the change was cheap: a stage is somewhere the learner *goes and does
> something*, and completion is a status, not a node.

**Why.** A stage is somewhere the learner *goes and does something*. Mastery is something that
*happens to them* as a consequence of the challenge. Modelling it as a fourth node promised a
fourth activity that does not exist, and made the spine read as 75 % complete at the moment
the learner had actually finished the lesson.

**Alternatives.** *Keep MASTER as a terminal, non-interactive node.* Rejected: a node the
learner can never be *in* is a label pretending to be a step. *Rename it RESULT.* Rejected for
the same reason — the result is a screen you land on, not a stage you progress through.

---

## ADR-018 — `:engine` is a real Gradle JVM module, created with the Binary Search slice

**Decision.** `ARCHITECTURE.md` §3's `:engine` module now exists: pure Kotlin JVM, depending on
nothing but the stdlib, holding `core/ event/ narration/ decision/ scene/ dataset/ scoring/`
and `algorithms/binarysearch/`. Binary Search is implemented there, not in Compose.

**Why.** The boundary that matters is that an `import androidx.compose.*` in algorithm code
*does not compile*. A package convention inside `:app` documents that rule; a JVM module
enforces it. It also means the 11 Binary Search tests run in milliseconds with no Robolectric.

**Notes.**
- `:design` was **not** extracted yet. The renderer lives in `:app/ui/components/` and already
  takes only `engine.scene.*`, so extracting it later is a file move. The tripwire from
  `ARCHITECTURE.md` §3 stands: if the renderer ever imports `engine.algorithms.*`, extract it
  that day.
- `kotlinx-collections-immutable` was **not** added; the engine uses `List`. The immutability
  guarantee comes from `data class` + `val`, and one fewer dependency keeps the module trivial
  to build. Revisit if a hot path shows defensive-copy cost.
- The module applies `id("org.jetbrains.kotlin.jvm")` without a version — AGP 9 already puts
  the Kotlin plugin on the classpath, and re-declaring the version fails resolution. Bytecode
  is pinned to Java 11 to match `:app`.

---

## ADR-019 — Binary Search bar heights encode value; the reference's do not

**Decision.** In the array visualiser, bar height is proportional to the cell's value. The
reference image's bars are near-uniform and unrelated to their numbers.

**Why.** The reference is a static mock, and in a static mock decorative heights read fine. In
a running sorting visualiser, a bar whose height contradicts its number actively teaches the
wrong thing — the learner cannot see "the largest bubbled to the end" if the largest is not
the tallest. Everything else about the bar — width, radius, gradient, numeral placement, gap —
is the reference's treatment unchanged.

**This is the only deliberate departure from the reference in the whole system,** and it is
recorded here so it is not mistaken for drift.

---

## ADR-020 — Watch is a user-paced walkthrough; autoplay and the play button are removed

**Decision.** WATCH no longer plays itself. It is a list of `WatchStep`s — deterministic
algorithm states, each paired with one short sentence — and the learner advances with a single
primary **NEXT**. Play, pause, autoplay and speed presets are removed from the learning flow.

**Why.** Autoplay makes the learner a viewer. The whole product thesis is that the learner
*operates* the algorithm, and that starts in Watch: pressing NEXT to see what happens next is
already a small act of prediction, which is exactly what Try then formalises. Autoplay also
forced the timing to guess how long a given learner needs to read, and it will always be wrong
for someone.

**What this replaces.** `PRODUCT_SPEC.md` §4's millisecond timing contract and the 0.5× / 1× /
2× speed presets governed autoplay and no longer apply to Watch. Per-transition motion
durations survive in `DESIGN_SYSTEM.md` §8 — cells still collapse over 300 ms — but motion is
now a consequence of a tap, never of a clock.

**Also removed:** the unscored mid-Watch prediction beat. It existed to interrupt autoplay and
make the viewer commit. In a walkthrough every NEXT already does that, so the extra card was
friction without a job. The prediction machinery stays in `LessonController` because Try uses
the same decision plumbing.

**Architecture.** The step list is built in `:engine` (`walkthrough/`), not in Compose. A
generic `WatchScriptBuilder` walks the trace the real algorithm produces; a per-algorithm
`WatchNarrator` captions each frame. Bubble Sort, Two Pointers and Sliding Window plug in by
supplying a narrator — no new screen, no new renderer.

**Alternatives.**
- *Keep autoplay with a prominent pause.* Rejected: the default still teaches passivity, and
  every learner who reads slower than the timer starts by fighting the UI.
- *Autoplay with a NEXT that skips ahead.* Rejected: two pacing models in one screen, and the
  learner can never tell whether a change was theirs or the timer's.
- *Author the steps as static content per algorithm.* Rejected: the copy would drift from what
  the engine actually does, and nine algorithms would mean nine hand-maintained scripts.

**Test that holds the line.** `WatchScriptTest` asserts every adjacent pair of steps differs —
scene, comparison readout, or recap. A step where nothing changed fails the build.

---

## ADR-021 — In Try, a wrong answer is a learning event, never a state transition

**Decision.** A wrong decision in Try does not touch the algorithm. The state stays exactly
where it was, the same decision stays on screen, and escalating guidance teaches until the
learner picks the correct action. Only `Validation.Accept` yields an action to apply.

**Why.** Two reasons, and the second is the one that matters.

1. *Pedagogy.* If a wrong answer advances the lesson, the learner has been rewarded for
   guessing and has learned a gesture instead of the logic. They must not be able to reach the
   end of Try without ever being right.
2. *Integrity.* Executing a wrong action puts the algorithm into a state the algorithm itself
   would never produce. Everything downstream — the narration, the scene projection, the
   metrics, the star formula — is then describing a run that is not Binary Search. The
   invariant "the state on screen is always a real state of the real algorithm" is worth more
   than any single teaching moment.

**How it is enforced.** `DecisionValidation.validate` is pure and returns `Accept(action)` or
`Retry(level, guidance, whyWrong, focus)`. **A `Retry` carries no action** — the caller has
nothing it could apply. The rule is therefore structural rather than a convention every future
algorithm has to remember, and it costs zero per-algorithm code: an algorithm supplies a
`Decision` with a `guidance` ladder and gets the behaviour.

**This reverses ADR-00x-era behaviour and supersedes `ConsequencePolicy`,** which let a second
wrong answer execute and "lose the target". That was the hero example in `PRODUCT_SPEC.md` §5
and it was a good idea in isolation — but it is precisely "a wrong answer became a state
transition", so it had to go. `PRODUCT_SPEC.md` §5 and `ARCHITECTURE.md` §6 are rewritten to
match; `ConsequencePolicy` is deleted rather than left dead.

**The escalation ladder** replaces it, and does the same teaching job without corrupting
anything:

| Level | Says | Gives away |
|---|---|---|
| 1 | "Look at the comparison again." | nothing — points at the evidence |
| 2 | "The target is larger than the middle. Which side can still contain it?" | the reasoning shape, not the answer |
| 3+ | "42 is smaller than 62, so 62 must be on the right. Keep the right half." | everything, and repeats forever |

The `whyWrong` line is withheld until level 2 so the first miss still leaves room to think.

**Challenge is deliberately different.** There a wrong answer logs ✕ and the run advances with
the *correct* action — unguided, but never blocked, and never corrupted either
(`PRODUCT_SPEC.md` §6).

**Alternatives.**
- *Let the wrong action execute and offer Rewind.* Rejected: see reason 2. Rewind also asks the
  learner to notice they are lost, which is exactly what a confused learner cannot do.
- *Disable the wrong option once identified.* Rejected: it turns the decision into a
  process-of-elimination puzzle and violates "identical visual weight in every state".
- *Auto-solve after three misses.* Rejected — this is what the old ladder did. It advances the
  lesson without the learner ever being right, which is the exact failure mode this ADR exists
  to prevent. The level-3 rung tells them the answer and then *waits for them to give it*.

**Tests that hold the line.** `DecisionValidationTest` asserts the state is byte-for-byte
identical after five wrong answers, that guidance escalates then holds, and that a run driven
entirely by rejected guesses still reaches an optimal trace with zero recorded wrong decisions.

---

## ADR-022 — Choosing the middle is a decision, not a mechanical step

**Decision.** `BinarySearchAction.CheckMiddle` is replaced by `Inspect(index)`, and `probe()`
now returns a `Decision` for it — `DecisionKind.CELL`, with every live slot as an option and
the true middle as `correct`. Which *phase* answers it is a host policy:

| Phase | Who picks the middle |
|---|---|
| Watch | the walkthrough script |
| Try | the app — the learner's job there is the half |
| Challenge | **the learner** |

**Why.** Challenge has to test whether the learner can apply Binary Search, and "start at the
middle of what is left" is half of the algorithm. If the app keeps computing `mid`, the
challenge only ever tests the second half of the idea.

The alternative was a `mode` flag on the algorithm, or a second Binary Search implementation
for Challenge. Both were rejected: `ARCHITECTURE.md` §4.1 exists precisely so that one
algorithm serves all three phases, and a mode flag is how that erodes.

**Why this does not change Try or Watch.** `probe()` returns the same decision to everyone;
Try's host drains `CELL` decisions by applying `decision.correct` before showing anything,
which is exactly what the old `Probe.Mechanical` drain did. The behaviour is identical.

**`Probe.Mechanical` survives** even though no algorithm currently emits it — Bubble Sort's
pass boundaries and pointer advancement will. It was not deleted to avoid churning the
hierarchy twice.

---

## ADR-023 — Challenge and Try share one invariant and differ only in how much they say

**Decision.** Challenge uses the same `DecisionValidation` as Try — a wrong answer never
changes the algorithm state — but responds with `Decision.minimalFeedback`: one neutral clue,
no escalation, no `whyWrong`, and the mistake is recorded.

**Why.** These are two different jobs wearing the same mechanism.

- Try's job is *understanding*, so a miss buys teaching and costs nothing.
- Challenge's job is *assessment*, so a miss buys a nudge and costs a star.

Both must protect the algorithm, and neither may let a learner advance by guessing. Putting
the invariant in the validator and the tone in the data means the difference between the
stages is two fields on a `Decision`, not two code paths.

**This reverses the earlier Challenge behaviour** (log the ✕ and advance with the correct
action). Advancing on a wrong answer meant a learner could tap through a challenge without
ever being right and still reach the Result screen — which makes the score meaningless. The
"never blocks" guarantee is preserved differently: the run always completes, because retry is
always available and the hint ladder always ends somewhere useful.

**Difficulty is data, not rules.** Beginner/Intermediate/Advanced change the array size and
the target's position. They never change the validator, and there is still no timer.

**NOT_FOUND scores like FIND.** Driving the range to empty is a correct execution of Binary
Search, and a learner who proves absence has demonstrated the half of the algorithm most
courses skip. Treating it as a lesser outcome would teach the wrong thing.

**Star thresholds** were loosened from `≤1 wrong` to `≤2 wrong, ≤1 hint` for two stars, so
★★ means what `PRODUCT_SPEC.md` §7 says it means: *understood it, with some mistakes*.

---

## ADR-024 — Bubble Sort ships as a `LessonPack`, and the screens become generic

**Decision.** Everything a lesson needs is bundled into `LessonPack<S, A>` — algorithm,
projector, watch narrator, the two authored datasets, star family, challenge factory — and
`LessonController`, `WatchScreen`, `LessonScreen`, `ChallengeIntroScreen` and `ResultScreen`
are generic over `<S, A>`. Adding an algorithm adds a pack and nothing else.

**Why now.** Binary Search alone did not prove the architecture; two algorithms do. The
refactor was the cheapest moment to do it — before a second lesson's worth of `if (algorithm
== ...)` had a chance to appear.

**What the second algorithm actually exercised.** Bubble Sort uses the halves of the engine
Binary Search never touched, which is the useful result:

| | Binary Search | Bubble Sort |
|---|---|---|
| Probe kinds | `Decide`, `Terminal` | **`Mechanical`**, `Decide`, `Terminal` |
| Decision kinds | `CELL` + `OPTIONS` | `OPTIONS` |
| Terminal outcome | `Found` / `NotFound` | `Sorted` |
| Star family | Efficiency | **Accuracy** |

**No new events, probes or validators were needed.** `ARCHITECTURE.md` §5.2 predicted that
adding an algorithm should add zero events; it did. `Swap`, `Hold` and `Finalize` were already
there and unused.

### Three decisions inside Bubble Sort worth recording

**1. The pair is mechanical, the verdict is the decision.** `probe()` returns
`Mechanical(FocusPair)` to bring the next pair under comparison and only *then* a `Decide` for
SWAP/KEEP. `PRODUCT_SPEC.md` §3 explicitly rejects "which pair should we compare?" — the next
pair is always the next pair, and tapping the only legal target teaches a gesture. Splitting
the beat also gives Watch its "compare" step separately from its "swap" step.

**2. Watch shows pass 1 in full and collapses the rest.** A complete 5-value sort is ten
comparisons; narrating all of them would be ~30 taps of the same rule. Pass 1 builds the model
(and is authored as `7 3 8 2 5` so the learner meets *keep* second, before they can mistake
Bubble Sort for "swap every pair"); later passes collapse to one beat each. A test pins the
walkthrough between 12 and 22 steps.

**3. Bubble Sort scores on Accuracy, not Efficiency.** How many swaps an array needs is a
property of the input. Scoring it would grade the data and punish a learner for drawing a
reversed array. Swaps are reported on Result and excluded from `ChallengeRun.optimal`.

**Alternatives considered.**
- *Give Bubble Sort its own controller and screens.* Rejected — it is exactly the "nine
  unrelated visualizers" outcome `ARCHITECTURE.md` §3 exists to prevent, and the shared
  invariant (a wrong answer never mutates state) would then need enforcing twice.
- *Make the learner pick the pair, for symmetry with Binary Search's cell pick.* Rejected by
  `PRODUCT_SPEC.md` §3. Symmetry between algorithms is not a goal; each decision must be the
  one that carries *that* algorithm.
- *Reuse `Scenario`/`SeededGenerator` for sorting data.* Rejected: those constraints are
  written against search traces. Sorting variety is about **shape** — mixed, reversed,
  nearly-sorted, duplicates, already-sorted — so `bubbleForRound` draws by shape instead.

---

## ADR-025 — Selection Sort and Insertion Sort: three sorts that must not look alike

**Decision.** Both ship as `LessonPack`s with interaction models drawn from their own mental
models, not from Bubble Sort's:

| | The learner's decision | Why that one |
|---|---|---|
| Bubble Sort | **swap or keep** | the judgement is about two neighbours |
| Selection Sort | **is this the new minimum?** then **where does it go?** | the judgement is against a value being *remembered*, not an adjacent one |
| Insertion Sort | **shift or insert** | the judgement is whether a value must move *out of the way* of a key held outside the array |

`PRODUCT_SPEC.md` §3 already specified Selection Sort's decision as *"Is this the new minimum?
+ where it lands"*; this implements it literally. Insertion Sort's entry was reworded from
"shift left, or place here?" to **"shift right, or insert here?"** — the values move right into
the gap; the *gap* is what moves left.

### The scan and the key are the app's job

Both algorithms emit `Probe.Mechanical` for the parts that are bookkeeping: Selection Sort
advances the scan cursor, Insertion Sort lifts the next key and walks the compare cursor. Only
the judgement reaches the learner. Making them tap "the next cell" would be the gesture-teaching
trap `PRODUCT_SPEC.md` §3 warns about.

### A shift is not a swap, and the renderer must not draw one

This is the decision with the most teeth. Insertion Sort emits `Insert` + `Remove` and
**never `Swap`** — there is a test asserting no `Swap` event is produced anywhere in a full
run. Consequences:

- `SequenceScene.arc` stays null for Insertion Sort, so no exchange arc is ever drawn.
- The vacated slot renders as `CellState.GHOST`: `primarySoft` fill, violet outline, and
  **no numeral**. A greyed-out number would say "this value is still here, just dimmed", which
  is exactly the wrong idea. It is a hole.
- The key lives in a badge *outside* the array, like Binary Search's target.

Selection Sort reuses `CellState.CANDIDATE` (amber) for the remembered minimum — a value the
algorithm is *holding*, distinct from the violet cell it is *looking at*.

**Both cell states already existed in the enum and were unused.** As with the events, adding
two algorithms added zero primitives.

### Watch length is capped by narrating only what teaches

A complete 5-value sort is far too many taps to narrate in full. Each narrator picks the
smallest prefix of the run that builds the model, then collapses the rest:

- **Bubble Sort** — pass 1 in full, one beat per later pass.
- **Selection Sort** — the first scan in full, one beat per later placement.
- **Insertion Sort** — the first *two* keys in full, because the second one (`8`) needs **no**
  shift. A learner who only ever saw shifting would conclude every key moves.

Every walkthrough is pinned by a test to a step count, so "just narrate one more thing" cannot
quietly turn a lesson into a slideshow.

### Teaching data is authored around the misconception, not the happy path

All three sorts share `7 3 8 2 5` for Watch, and each for a different reason:

- Bubble Sort: pass 1 reads swap, **keep**, swap, swap — *keep* arrives second, before the
  learner can decide the algorithm swaps every pair.
- Selection Sort: the minimum is replaced **twice** during the first scan, so the candidate is
  visibly provisional rather than spotted immediately.
- Insertion Sort: key 1 shifts once, key 2 does not shift at all, key 3 walks to the front.

**Alternatives considered.**
- *Model Selection Sort's scan as "tap each value in turn".* Rejected: the next cell is always
  the next cell. It would be a gesture, and it would make a 5-value array cost ~14 taps of
  which only half are judgements.
- *Model Insertion Sort's shift as a swap between the key and its neighbour.* Rejected — it is
  the single most common misconception about the algorithm, and it would make Insertion Sort
  render identically to Bubble Sort.
- *Give the sorts their own generator instead of reusing `bubbleForRound`.* Deferred: the shape
  rotation (mixed / reversed / nearly-sorted / duplicates / already-sorted) is exactly as
  relevant to Selection and Insertion as it is to Bubble. Worth revisiting when a sort-specific
  constraint appears — e.g. "the key must travel more than one place".

---

## ADR-026 — Merge Sort is bottom-up, and it earns two generic engine additions

**Decision.** Merge Sort ships as a `LessonPack` with two learner decisions — **where does it
split?** (once, `CELL`) and **which front value comes next?** (every merge step, `OPTIONS`) —
implemented as an iterative **bottom-up** merge.

### Why bottom-up rather than recursive

The recursion is not the lesson; *divide, then combine* is. Bottom-up produces exactly the
hierarchy the learner is shown, read upwards:

```
8 3 6 2 7 1 5 4        divide   groupWidth 8 → 4 → 2 → 1
3 8 | 2 6 | 1 7 | 4 5  merge    runWidth 1
2 3 6 8 | 1 4 5 7      merge    runWidth 2
1 2 3 4 5 6 7 8        merge    runWidth 4
```

and it renders as **one flat row with group boundaries** — no call stack to draw, no second
kind of screen, no tree renderer. A true recursive implementation would have needed a
`TreeScene` and a second renderer, which is the "nine unrelated visualizers" outcome
`ARCHITECTURE.md` §3 exists to prevent. The divide phase moves no data at all — only the
boundaries — and a test asserts exactly that.

### The window is rewritten as `output ++ leftRest ++ rightRest`

During a merge the display window always reads as *merged prefix, then what is left of each
run*. That is what makes the merge legible: the green prefix grows while both runs visibly
shrink. It also means the two front values sit at **display** slots (`leftSlot`, `rightSlot`),
not at their original indices — a bug caught by a test that expected a `COMPARING` cell and
found none.

### Two generic additions, both earned

1. **`SequenceScene.groups`** — a sequence can now say it is divided, and the renderer draws a
   separator between groups. Generic: any divide-and-conquer lesson gets it free.
2. **`Decision.autoInTry`** — replaces the old rule that Try auto-answers every `CELL`
   decision. **That rule was a bug.** It silently answered Selection Sort's *"where does the
   minimum go?"* and would have answered Merge Sort's *"where does it split?"* — the exact
   judgements those lessons exist to teach. Only Binary Search's `mid` computation sets it,
   because that one genuinely is arithmetic the app owns (`PRODUCT_SPEC.md` §3).

### What is asked, and what is not

| Beat | Who | Why |
|---|---|---|
| the first split | **learner** | it is the idea |
| deeper splits | app | the same idea again; tapping each boundary is patience, not understanding |
| a merge step with both fronts live | **learner** | this is *why* merging works |
| the leftover tail of a run | app | one run is empty — there is nothing to judge |

**Alternatives considered.**
- *Ask the learner for every split at every level.* Rejected: eight values would mean seven
  split taps that all say the same thing.
- *Draw an arc for each merge step.* Rejected: an arc means an exchange. Merge Sort combines,
  and a test asserts it never emits `Swap`.
- *A dedicated tree renderer.* Rejected as above — and the flat row with separators turned out
  to show the hierarchy perfectly well while staying inside the approved design system.

---

## ADR-027 — Stack and Queue are one algorithm with two flavours

**Decision.** Stack and Queue ship as **two separate lessons** driven by **one** engine class,
`LinearStructureAlgorithm`, parameterised by a `StructureFlavour`. The flavour supplies the copy,
the drawn orientation, and exactly one behavioural line:

```kotlin
/** Queue removes from the front; Stack removes from the end it added to. */
val removesFromFront: Boolean
```

`StructureState.items` is stored in **entry order** — index 0 entered first — in both. Which end
counts as "next out" is the structure's business, not the state's.

### Why one class rather than two

Writing `StackAlgorithm` and `QueueAlgorithm` would have produced two files that were 95%
identical, and the 5% that differed would have been scattered through both. That hides the thing
the pair of lessons exists to teach. Here the difference is a single named property, and the
tests iterate `listOf(StackFlavour, QueueFlavour)` and assert *opposite* answers from the *same*
assertions — which is the claim, stated as code.

This is not the "force everything through one interaction" trap that `ARCHITECTURE.md` §3 warns
about. The learner's decision genuinely is the same shape in both: **which operation does this
job?**, and **which item leaves next?** Only the rule behind the right answer differs.

### Both lessons use the same numbers

`StructureDatasets.watch` is `[12, 27, 41, 58]` for **both** structures, and the script is the
same. So the two walkthroughs run the same story over the same data and disagree only where the
structures disagree:

| Step | Stack | Queue |
|---|---|---|
| after pushing 12 27 41 | pile reads `41 27 12` top-down | line reads `12 27 41` front-to-rear |
| "which leaves next?" | **41** | **12** |
| peek, after adding 58 | **58** | **27** |
| drained | 58, 27, 12 | 27, 41, 58 |

A learner who has done both has seen one sequence of instructions produce two different answers,
which is stronger than any sentence the app could write.

### They must not look alike

- **Stack** — `Orientation.VERTICAL`. A pile of wide plates, newest on top, resting on a base
  line, labelled `IN ↓   OUT ↑` above and `BOTTOM` below — both arrows naming the *same* end, because there is only one.
- **Queue** — `Orientation.HORIZONTAL`. A line of cells with `FRONT` and `REAR` named under them
  and `OUT ←` / `← IN` flanking the row. Two live ends, and the caps say so.

`SequenceScene.endCaps` was added for this and defaults to null, so no existing lesson changed.

### The prediction hides its own answer

The projector removes the pointer labels, the end caps and the `CANDIDATE` highlight while the
live task is a `Predict`. Without that, "which item leaves next?" is answered by an arrow already
pointing at it, and the checkpoint tests whether the learner can follow an arrow.

Note this is keyed off the **task**, not the phase — which resolves the Slice-2 question about
whether `SceneProjector` needs a per-phase variant. It does not: what the learner is being asked
is already in the state.

### Empty and full are lesson content, not error handling

Both are total in the engine — a removal or a peek on an empty structure advances the script with
an explanatory narration, and an add to a full one keeps the existing items. Neither throws.

But handling them silently is not enough. `buildScript` **drains the structure completely and
then asks for one more removal**, so every learner meets underflow on purpose. And because a
caption on an unchanged screen would leave two identical frames in a row — the thing WATCH is not
allowed to do (ADR-020) — `StructureState.reachedEmpty` makes the projector draw the slot that
was reached for as a `GHOST` cell. The screen says "there was nothing there"; the caption only
explains it.

Overflow is guarded and tested, but the scripted lessons are sized so it never fires: an
instruction the learner obeys should always do what it says.

### What is scored

`StarFamily.ACCURACY`. The script fixes the number of operations, so there is no cost to
optimise — only whether the learner reached for the right end every time. `ChallengeType.OPERATIONS`
is the new challenge type; its generator excludes every value used in Watch or Try, so the
challenge cannot be answered from memory (`PRODUCT_SPEC.md` §6).

### The comparison card

The result screen for either structure ends with a five-row Stack-vs-Queue table, with the
structure just completed highlighted. It is the payoff of learning both, and it is placed where
the learner has just proved they understand one of them.

**Alternatives considered.**
- *One "Stack or Queue?" capstone lesson* (the old open assumption #1). Rejected: the contrast
  only lands once each structure is separately solid, and a combined lesson would have to teach
  both rules before it could ask anything.
- *Drawing the stack bottom-up so it matches array indexing.* Rejected: a stack that grows
  downward contradicts the word "top", and the word is the whole mnemonic.
- *Skipping the underflow beat to keep WATCH short.* Rejected: "what happens when it is empty?"
  is the question every implementation must answer, and a lesson that never reaches empty never
  answers it.

---

## ADR-028 — Progress is three latched booleans, and the percentage is derived

**Decision.** How far an algorithm has been learned is stored as exactly three flags —
`watchCompleted`, `tryCompleted`, `challengeCompleted` — per `AlgorithmId`. The percentage is
computed on every read from how many are set: **0 / 33 / 66 / 100**.

```kotlin
val percent: Int get() = when (completedStages) { 0 -> 0; 1 -> 33; 2 -> 66; else -> 100 }
```

### Why derived rather than stored

A stored percentage is a second source of truth, and second sources of truth drift. The moment
`percent = 60` and `tryCompleted = false` can both be true, the Home screen and the lesson
disagree and there is no way to say which is right. Deriving makes the disagreement
unrepresentable.

It also settles the UX question the same way: there is no 50 %, because there is no half-stage.
Progress counts **milestones**, never visits, taps, screens seen or time spent.

### Why counted, not ordered

`percent` counts completed stages instead of walking the sequence. In the app the stages can
only be finished in order, so the two agree everywhere it matters — but counting means the
number can never contradict the flags, whatever order a future flow completes them in.

### Why it cannot go backwards

`AlgorithmProgress.complete(stage)` only ever *sets* a flag. There is no API to clear one, so
"reset progress on failure" is not a thing a caller can express — retries, failed challenges and
repeat practice runs are structurally incapable of subtracting progress. The stored form is a
`Set<String>` of `ALGORITHM:STAGE` keys, which is additive for the same reason.

### Where completion is decided

| Stage | Fires when | Not when |
|---|---|---|
| WATCH | the learner reaches the **final** walkthrough step | they open Watch, or advance one step |
| TRY | the lesson reaches its terminal state | they answer correctly, or open Try |
| CHALLENGE | the challenge reaches its terminal state | they open the briefing, or start a run |

Watch completes on *reaching* the last step rather than on tapping "Start Try": there is nothing
left to do on that screen, and punishing someone who read the recap and then went back would be
measuring obedience rather than learning. Try and Challenge complete on the terminal state
whatever it cost — mistakes and hints shape the stars on the result screen, and never decide
whether the stage counts as learned.

### Where it is stored

**DataStore Preferences**, not Room (`ARCHITECTURE.md` §8.1). Twenty-seven booleans that are
written a handful of times per lesson and never queried by range or aggregated are exactly the
"small, flat, frequently written, never queried" case. Room's `AlgorithmProgress` entity remains
the home for attempt history, personal bests and streak dates — none of which the Home ring
shows, and all of which genuinely need a database.

The repository exposes a `Flow`, so a stage finished three screens deep reaches Home's rings on
the next frame with no restart and no manual refresh.

### What Home stopped hardcoding

`AlgorithmEntry` no longer carries a `status`. Progress belongs to the learner, not to the
catalogue, so the card's ring and label are derived at render time from the repository. The
highlighted card — started, not yet mastered — is derived the same way, and an untouched install
highlights nothing rather than pretending at a favourite.

**Alternatives considered.**
- *Room from the start.* Rejected for this data: an entity, a DAO, a KSP plugin, exported schemas
  and a migration policy, all to hold three booleans per algorithm.
- *Storing the percentage alongside the flags.* Rejected — see above; it is the bug this ADR
  exists to prevent.
- *Marking Watch complete on open.* Rejected: opening is not learning, and a Home screen that
  says 33 % for something the learner glanced at is worse than one that says 0 %.

---

## ADR-029 — A Linked List is a lesson about arrows, so the arrows are the interface

**Decision.** Linked List ships as a `LessonPack` whose learner decisions are, without exception,
about **links**:

- *is this the node?* — one question per node, because walking is the only way to reach anything;
- *which link changes?* — a `CELL` decision whose slots are **gaps**, not nodes;
- *where should it point?* — the second half of every insertion and every deletion.

### Numbering the gaps

Link `0` is HEAD's own arrow, link `i` is the arrow into node `i`, and link `n` is the arrow to
NULL. A list of `n` nodes therefore has `n + 1` links. That numbering is what turns *"which link
must change?"* into a question the learner can point at, and it makes the two hard cases fall out
for free rather than needing special code:

- **inserting before the head** is gap `0` — there is no previous node to rewire, which is the
  case every implementation gets wrong first;
- **deleting the last node** reconnects to NULL, which is the other one.

### Why not the Stack/Queue interaction

Stack and Queue ask *which operation does this job?*, because their entire content is which end
you may touch (ADR-027). A linked list has no privileged end. Reusing that question here would
have taught the learner to pick a word from a row of buttons; asking them which arrow changes
teaches the thing the structure is actually made of. **The brief was explicit that Linked List
must not be forced into Stack/Queue mechanics, and it is not.**

### Every insertion and deletion is two decisions

One tap could have done it. Two are used because the two halves fail independently in real code:

| Operation | First | Second |
|---|---|---|
| Insert | which gap does it belong in? | what does the new node point to? |
| Delete | which link points at it? | where should that link point instead? |

The second insert question exists because *a node spliced in without its own NEXT* is the most
common linked-list bug there is, and a lesson that performs it silently teaches the learner
nothing about it.

### The list is kept in order

Every teaching list is ascending, which is what gives insertion **one** defensible answer: the
learner is asked where a value *belongs*, not asked to guess which gap the author had in mind.
A test asserts the list is still ordered after every generated challenge.

### The chain is its own layout

`Orientation` became `SceneLayout { ROW, PILE, CHAIN }`, and the scene gained `links` and
`detached`. The renderer branches on the *shape of the data*, never on which algorithm sent it,
so this is the same generic-renderer contract the app has always had — a third shape, not a third
renderer contract.

What the chain draws that a row cannot:

- each node as **value | NEXT**, divided, with the arrow leaving from the NEXT compartment;
- a **cut link** as two dashed stubs with a real gap, because during an operation the list
  genuinely is broken there;
- a **detached node** hovering over its gap, so insertion has a moment where the node exists and
  is connected to nothing. Without that moment, inserting looks like a value dropped into an
  array slot — precisely the wrong idea.

### Edge cases are lesson content

Searching an empty list, deleting a value that is not there, and a walk that runs off the end all
advance with an explanation rather than throwing. Reaching NULL is treated as an *answer*, not a
failure: it is how a linked list proves absence. A challenge that changes the list is always
followed by a walk over it — after a delete that walk runs to NULL, which is the most convincing
proof available that the node is really gone.

**Alternatives considered.**
- *Tapping nodes instead of arrows.* Rejected: it is the same gesture an array lesson uses, and it
  quietly teaches that a list is positions.
- *Performing the pointer updates automatically once the gap is chosen.* Rejected — that is the
  half of the operation worth asking about.
- *A separate `ChainScene` type and a second renderer.* Rejected for the reason `ARCHITECTURE.md`
  §3 gives: one scene type, one renderer entry point, a new layout value.

---

## ADR-030 — A Hash Map is not a sequence, so `Scene` became a union

**Decision.** `Scene` is now a sealed interface with two shapes: `SequenceScene`, which every
lesson so far projects into, and `BucketScene`, which the Hash Map projects into. `SceneProjector`
returns `Scene`; the app has one entry point, `SceneRenderer`, that dispatches on the shape.

### Why not force it into `SequenceScene`

Eight lessons fitted a row of cells because they *are* sequences — even the linked list, which is
a row with arrows, and the stack, which is a row stood on its end. A hash map is not. It has:

- no order to show — bucket 3 is not "after" bucket 2 in any sense the learner should absorb;
- **buckets that hold more than one thing**, which a slot cannot represent;
- a *calculation* that has to be visible before its answer is, which no array algorithm has.

Jamming that into cells and slots would have taught the one idea a hash map exists to refute:
that you find data by walking past the data in front of it. `ARCHITECTURE.md` §3 warns against
nine unrelated visualisers; it does not ask for one visualiser that lies.

### The cost was smaller than it looks

Projectors return `SequenceScene` still — a covariant return, so **not one existing projector
changed**. What changed was seven narrator signatures (`SequenceScene` → `Scene`, one line each),
`WatchStep.scene`, `LessonUiState.scene`, and the renderer entry point. No lesson's behaviour
moved.

### Two things on screen, both the lesson

`BucketScene` carries a table **and** a `HashFlow`:

```
GET 12  →  HASH 12 % 5  →  BUCKET ?
```

The flow stops where the learner has got to. While the bucket is being asked for, it shows `?` —
because an answer already on screen is not a question. That single rule is what makes the hash
askable rather than merely watchable, and a test pins it.

### Every operation starts by hashing

PUT, GET and REMOVE all begin with the same `CELL` decision — *which bucket does `key % 5` land
in?* — answered by tapping the bucket row itself. The repetition is the point: it is the one
habit the structure runs on, and a learner who has done it eight times understands why lookup
does not have to walk the data. A test asserts every lesson's first decision is that one.

The second decision appears **only when there is something to learn from it**:

| Situation | Question | Answer |
|---|---|---|
| empty bucket, PUT | — | the app stores it |
| occupied bucket, different key | what happens? | keep both — a collision |
| occupied bucket, same key | what happens? | replace the value |
| bucket with one entry, GET | which entry? | the only one |
| bucket with two entries, GET | which entry? | compare the keys |
| empty bucket, GET | — | nothing to compare; not here |

"Refuse it" is offered as a third answer to the collision question because it is what learners
expect a hash map to do, and it is wrong. Being told *why* it is wrong is worth more than never
being tempted.

### Collisions are authored, never hoped for

`HashMapDatasets.watch` is `[12, 7]` precisely because `12 % 5 == 7 % 5 == 2`. The Try lesson
adds the case nobody guesses right — the same key put twice, which updates rather than
duplicating. And `ChallengeGenerator.hashForRound` **constructs** two keys that share a bucket
rather than drawing randomly and hoping; a test asserts every generated challenge collides.

A hash map lesson where every key finds an empty bucket teaches nothing about hash maps.

### Separate chaining, and it is a teaching choice

Collisions are resolved by keeping both entries in the bucket. Open addressing would have been
defensible and is not used: probing to a *different* bucket breaks the one sentence the lesson is
built on — "the key tells you where to look" — at the exact moment the learner is still forming it.
The renderer draws a shared bucket as a small chain with a leading tick, and never as an error.

**Alternatives considered.**
- *A row of number buttons for the hash answer.* Rejected: the answer is a place in the table, so
  it should be tapped there. Tapping the bucket is also what makes the flow strip land.
- *Letting the app hash and asking only about collisions.* Rejected — the hashing **is** the
  lesson; the collision is the twist.
- *Open addressing.* Rejected above.
- *Teaching worst-case complexity.* Out of scope for the MVP. Watch says average GET/PUT/REMOVE
  are close to O(1) and says out loud that this depends on a good hash function and short
  buckets, which is as much as a conceptual lesson can honestly claim.

---

## ADR-031 — CHALLENGE is deferred to V2, and the machinery is quarantined rather than deleted

**Decision.** The MVP ships **WATCH → TRY**, ending on a Complete screen. The CHALLENGE stage
is removed from the app entirely — no route, no screen, no placeholder — and every piece of
challenge *architecture* is retained, compiled and tested behind
`engine/challenge/ChallengePack.kt`. Progress becomes two latched booleans, 0 / 50 / 100.

⚠ **This is a product-visible scope decision**, taken by the product owner on 2026-09-04 and
recorded in full at `docs/v2-challenge.md`.

**Why.** A challenge is not one feature; it is ten. Every algorithm needs its own generated
scenarios, constraints, hint ladder, answer validation and edge cases, and each has to be
authored and tuned before the stage teaches anything. That was making the MVP
disproportionately complicated relative to what it added, while WATCH → TRY already delivers
the product thesis — the learner operates the algorithm rather than watching it.

Shipping a weak challenge is worse than shipping none: a challenge that can be passed by
guessing teaches the learner that the assessment is meaningless.

### Deferred, not deleted — and the difference is structural

The tempting version of this change deletes `engine/challenge/`, `engine/scenario/` and
`engine/scoring/` outright. That would have thrown away the seeded generator, the
trace-validated constraints (ADR-006), the ten authored `ChallengeType`s, the three star
families, the mission catalogue and `MissionRun` — roughly 50 passing tests — and V2 would have
rebuilt all of it from the specification rather than from working code.

Instead the challenge-only fields came **off** `LessonPack` and onto a new `ChallengePack`:

| | Before | After |
|---|---|---|
| `LessonPack` | id, name, **challengeBrief**, algorithm, projector, narrator, datasets, **starFamily**, **challengeFactory** | id, name, algorithm, projector, narrator, datasets |
| `ChallengePack` | — | id, challengeBrief, starFamily, challengeFactory |

That single split is the whole quarantine, and it earns its keep in both directions:
`LessonPack` now holds only what WATCH and TRY read, so no MVP screen can reach a challenge
even by accident; and `ChallengeCatalog` keeps all ten packs authored, so V2 does not have to
re-derive which star family each algorithm scores on.

**The tripwire:** if the MVP ever needs something out of `ChallengePack`, that is a signal the
thing belongs on `LessonPack` — move it, rather than importing `engine.challenge.*` from a
lesson screen.

### There are no stars in the MVP

`PRODUCT_SPEC.md` §2 has always said **Try is never scored**. It exists so a learner can be
wrong as often as they like at no cost, which is the entire premise of the guidance ladder
(ADR-021). Putting a star rating on the Complete screen would have quietly undone that: the
moment a run carries a grade, the ladder becomes something to avoid rather than something to
use, and a learner starts guessing to protect a score.

So `LessonCompleteScreen` reports what the run *was* — decisions, comparisons, wrong turns —
and judges none of it. `Scorer`, `StarFamily` and `Verdict` stay in `:engine`, unreferenced by
`:app`, for the stage whose job assessment actually is.

### Hints went with it

`LessonScreen` already documented "No Hint in Try" — the guidance ladder arrives without the
learner having to ask, which is strictly better than a button they must admit defeat to press.
Hints were therefore Challenge-only in the UI, and so was everything behind them: the
`HintUnlockDialog` and the `ads/RewardedAdHost` seam are removed from `:app`, while `HintPolicy`
(first rung free, the rest rewarded) stays in `:engine` where it was already table-tested.

Deleting the ad seam is worth noting: it was the only thing in the project that referred to
advertising at all, and the MVP is now entirely free of it.

### Old installs keep what they earned

`ProgressCodec` drops stage keys it does not recognise (ADR-028), so an install holding
`BINARY_SEARCH:CHALLENGE` loads its WATCH and TRY keys and reads as 100 % rather than failing.
That behaviour existed for renamed algorithms and turned out to be exactly the upgrade path
this change needed; there is now a test pinning it.

When CHALLENGE returns, adding it back to `Stage` restores thirds with no other change, because
`percent` counts `Stage.entries` rather than storing a number.

**Alternatives considered.**
- *Keep Challenge behind a feature flag.* Rejected: a flag means both paths must keep compiling
  against live screens, so the MVP carries the challenge UI's weight without shipping it — and
  flags of this size become permanent.
- *Leave a "Coming soon" Challenge card in the spine.* Rejected: a third node the learner can
  never enter makes a finished lesson read as two-thirds done, which is the exact failure
  ADR-017 removed the MASTER node for.
- *Delete the challenge engine and rebuild it in V2.* Rejected above.
- *Keep stars on the Complete screen as encouragement.* Rejected: see above — it contradicts
  "Try is never scored", and encouragement that is not earned is noise.

---

## ADR-032 — Two Pointers is the first Advanced lesson, and it added no primitives

**Decision.** Two Pointers ships as a `LessonPack` with WATCH and TRY, in a new
**Advanced** category. The learner's decision is *which pointer moves* — LEFT, RIGHT, or
"pair found" — and the app owns only the arithmetic. Full detail: `docs/two-pointers.md`.

**Why it was worth being first.** Every lesson so far is a named routine with steps to
follow. Two Pointers is a *technique*: one rule, and the rule is only sound because the
array is sorted. That makes it the first lesson where the learner can execute perfectly
and still not have learned anything — which is exactly the failure the copy is written
against.

### The app states the sum; the learner moves the pointer

`probe` returns `Mechanical(Compare)` to read `values[left] + values[right]`, then
`Decide` for the move. This is Binary Search's shape exactly (`Inspect`, then the half),
and it exists for the same reason: adding two numbers is not a judgement, and asking the
learner to press a button to perform arithmetic teaches a gesture (PRODUCT_SPEC.md §3).

It also gives WATCH its seam for free. Each round is two steps — *"1 + 10 = 11, that is
greater than 10"*, then *"move RIGHT one position left"* — with the pointers still
unmoved on the first. Collapsing them would show a learner a pointer that has already
moved beside the reason it should move, which is the wrong order to think in. Because
the engine emits two transitions, the narrator does not have to invent the split.

### All three options are offered every round

Binary Search adds its `FOUND` option only when `values[mid] == target`, so the option
appearing *is* the answer. Repeating that here would have destroyed the beat: *is this
the pair?* is the question, and an option that only shows up when it is correct answers
it before it is asked. Two Pointers therefore offers LEFT, RIGHT and "Pair found" on
every decision, and "Pair found" is wrong on most of them.

This is a small divergence from the older lesson and it is deliberate. The Binary Search
behaviour is not being changed — it is load-bearing for that lesson's own tests — but new
lessons should not inherit it.

### Advanced is a category, not a new mechanism

`AlgorithmEntry.category` already drives the Home chip row and the card badge, so
"Advanced" is one more string in `algorithmCategories`. No `level` field, no `isPro`
flag, no second taxonomy. The tradeoff is that a category says *what an algorithm is*
and this one says *how hard it is*, so Two Pointers is not also filed under Searching —
accepted, because a parallel difficulty axis is a second system to maintain for one
lesson, and the brief asked for the existing approach.

### What it cost

**Zero new `VizEvent` types, zero renderer branches, zero changes to existing
algorithms.** `LO`/`HI` carry the LEFT/RIGHT labels, `RUNNING_SUM` carries the sum,
`Eliminate` collapses what the move discarded. Three engine files, one test file, and
five lines of wiring elsewhere — which is what ADR-024 promised adding a lesson would
cost, now tested against a lesson written after the promise.

The one nullable introduced is `ChallengeCatalog.byId`, which returns null for
TWO_POINTERS. Authoring a challenge nobody asked for, to satisfy an exhaustive `when`,
would have put an untested challenge in the catalogue that V2 would inherit as though it
were designed. "Not written yet" is a real state and the signature now says so.

**Alternatives considered.**
- *Make the sum a learner decision too.* Rejected: it is arithmetic with one legal
  answer, and PRODUCT_SPEC.md §3 rejects exactly this ("tapping the only legal target
  teaches a gesture").
- *Move the pointer automatically after showing the comparison.* Rejected by the brief,
  and rightly: the move **is** the decision. Automating it leaves the learner watching.
- *Model it as Binary Search with two cursors.* Rejected: Binary Search eliminates by
  halving around a computed midpoint; Two Pointers eliminates by stepping one end
  inward. Sharing an implementation would have needed a mode flag in the one place
  ARCHITECTURE.md §4.1 exists to keep clean.
- *A same-direction (fast/slow) variant first.* Deferred: opposite ends make the
  *reason* visible — largest and smallest value in play — which is the half that
  transfers. Fast/slow is a dataset and a narrator away.

---

## ADR-033 — Prefix Sum takes a third `Scene` shape, and fixes one representation

**Decision.** Prefix Sum ships as a `LessonPack` with WATCH and TRY. `Scene` gains a third
shape, `PrefixScene`, and `Dataset` gains optional `queryLeft`/`queryRight`. The prefix array
uses the **standard leading-zero form** everywhere. Full detail: `docs/prefix-sum.md`.

**Why a third shape.** ADR-030 said a hash map is not a sequence. Prefix Sum is not one either,
and for a different reason: it has **two arrays of different lengths**, and the lesson lives in
the *offset* between them — `prefix[i + 1]` is the running total that `array[i]` produced.

Flattening both into one row of `n + (n + 1)` cells would assert they are one sequence, which is
the single thing the learner must not believe. Showing one at a time would hide the relationship
that *is* the technique. So the scene carries two cell lists, and the renderer lays them over the
same `n + 1` slots with the source row inset by one — which puts each array value directly above
the prefix cell it feeds, and leaves `prefix[0] = 0` alone at the left explaining why the row is
longer.

Inside, it is ordinary scene data: the same `Cell` and `CellState` as everywhere else, so an
uncomputed entry is a `GHOST` — the hole Insertion Sort already established. `SceneCell` and
`SlotRow` became `internal` so the new shape reuses them; duplicating cell styling is how a
design system forks.

### One representation, chosen for the formula it produces

```
prefix is n + 1 long, prefix[0] = 0
prefix[i + 1]         = prefix[i] + array[i]
rangeSum(left, right) = prefix[right + 1] - prefix[left]
```

The n-length form without the leading zero appears nowhere — not in the engine, the copy, the
UI or the tests. It is not a style preference: without the leading zero the range query needs a
branch for `left == 0`, and a formula with an exception in it is one the learner memorises
instead of understanding. The teaching range deliberately starts at 1 so the subtraction is
visibly doing something; a range starting at 0 subtracts zero and the whole idea looks like a
no-op.

### Choosing the indices is its own beat

The query is two decisions, not one: *which two prefix values?* then *what do they make?*
Reaching for `prefix[right]` instead of `prefix[right + 1]` is the mistake this technique is
famous for, and it has to be askable on its own — so the off-by-one is always among the options.
A beat that cannot be failed is not testing anything.

### The options are values, and the wrong ones are misconceptions

Each beat offers three numbers. For `prefix[2] = 2 + 4 = 6` they are 6, **4** (the running total
dropped) and **7** (two array values added to each other). Because each distractor encodes a
specific error, `whyWrong` can name what the learner actually did rather than restating the
rule at them.

A numeric keypad was rejected: it is a new interaction model for one lesson, and "select" is
what the brief asked for.

### What it cost

Zero new `VizEvent` types, zero changes to existing algorithms, zero new interaction models.
The `Dataset` fields are optional and defaulted, so no existing lesson moved.

**Alternatives considered.**
- *One `SequenceScene` with `groups` separating the two arrays.* Rejected: groups divide **one**
  sequence, and these are two, with different lengths and different index meanings. The
  separator would be a lie about what the cells are.
- *Show only the prefix array, and the source array in the copy.* Rejected: the relationship is
  the lesson, and prose cannot carry an alignment.
- *A numeric keypad for the answers.* Rejected above.
- *Ask the learner for `prefix[0] = 0`.* Rejected: it is the definition of the representation,
  not a judgement. Asking it would be the gesture-teaching trap PRODUCT_SPEC.md §3 warns about.

---

## ADR-034 — Graph DFS takes a fourth `Scene` shape, and backtracking is a tap, not a button

**Decision.** DFS ships as a `LessonPack` with WATCH and TRY. `Scene` gains `GraphScene`, the
first two-dimensional shape; `Dataset` gains an optional `graph` and `startNode`. The learner's
only gesture is **tap the node DFS moves to next**. Full detail: `docs/graph-dfs.md`.

**Why a fourth shape.** A graph is nodes at positions with edges between them. No amount of
slots expresses that: a sequence has an order a graph does not have, a bucket table has keys a
graph does not have, and two aligned rows are still rows. This is the same judgement ADR-030
and ADR-033 made, for the third time, and the shape is what a new *kind* of data looks like in
this architecture.

Inside, it reuses everything: node state is `CellState`, so current is `COMPARING`, visited is
`FINALIZED` and unvisited is `IDLE`, and the legend, the colours and the words all come out of
the design system with no new tokens.

### One gesture, three judgements

DFS asks three things — which neighbour, when to backtrack, and why some neighbours are
skipped. The obvious build gives them separate controls: a row of neighbour buttons and a
BACKTRACK button. That was rejected.

**Backtracking is not a mode, it is a move.** Returning to the node you came from *is* the
backtrack, so tapping it is the honest interaction — and modelling it as its own button would
have taught the learner to reach for a control rather than to notice a dead end. One tap on the
graph covers all three judgements:

| the tap | what it is |
|---|---|
| the first unvisited neighbour | go deeper — correct |
| a visited neighbour | the "skip it" case |
| a later unvisited neighbour | the "first one first" case |
| the parent, mid-branch | backtracking too early |
| the parent, at a dead end | the backtrack — correct |

The brief asked for TRY not to become a multiple-choice quiz. A row of buttons is one; tapping
the graph is not.

### The traversal is generated, never authored

`A → B → D → E → C` appears nowhere as data. `visited` and `stack` are real state and the
order falls out of them; every test drives the engine and reads it back. The brief was explicit
about this and it is the difference between a lesson and an animation.

### Three backtracks, not two

The teaching graph needs D→B, E→B **and** B→A, and the third is a different kind: not a leaf
dead end but a node whose branches are all used up. A first pass of the tests asserted two and
was wrong — the graph was right and the expectation was not. A graph with only leaf dead ends
would teach backtracking as a special case.

### WATCH and TRY share one graph

Every other lesson gives Try fresh data so it tests application rather than recall. Five nodes
are memorisable either way, and what makes Try hard here is producing the three backtracks
rather than meeting new data. A second graph would have added unfamiliarity without adding a
judgement.

**Alternatives considered.**
- *A separate BACKTRACK button.* Rejected above.
- *Force the graph into `SequenceScene` as a row of nodes with `links`.* Rejected: the linked
  list is a chain because a list *is* a line. A graph is not, and drawing it as one would teach
  the wrong shape.
- *Auto-layout the nodes.* Rejected for now: five authored positions look better than anything
  a force-directed pass would produce, and the layout lives on the dataset where a future graph
  will author its own.
- *Unwind the stack to empty after the last visit.* Rejected: two more taps with nothing left to
  decide. The lesson ends where the traversal ends.

---

## ADR-035 — Graph BFS reuses DFS's scene and gesture, so the queue is the only difference

**Decision.** BFS ships as a `LessonPack` with WATCH and TRY, on the **same graph** as DFS,
projecting into the **same `GraphScene`**, driven by the **same gesture**. `GraphScene` gains an
optional `queue` and a `pathLabel`. Full detail: `docs/graph-bfs.md`.

**Why sameness is the design.** The pair exists to make one point: on identical data, DFS gives
`A → B → D → E → C` and BFS gives `A → B → C → D → E`. That point only lands if *everything
else* is held constant. A second graph, a second renderer or a second interaction model would
each give the learner somewhere to misattribute the difference.

So: same `GraphDatasets.teachingGraph`, same adjacency order, same start node, same
`GraphStage`, same "tap the node the algorithm touches next". A test asserts both traversals
from the one shared graph, which is the claim stated as code.

The queue is a defaulted field on the existing scene, so DFS needed no change and draws no
queue — the same additive move `Dataset.queryLeft` and `Dataset.graph` made before it.

### Visited on the way in, not on the way out

A node is marked visited the moment it is **enqueued**. This is the rule the brief called out
and it is not a detail: mark on dequeue instead and a cyclic graph enqueues the same node
several times before its turn arrives, then processes it more than once.

It has a visible consequence the lesson has to be honest about — there are **two orders**:

| | |
|---|---|
| `visited` | enqueue order — what BFS has *seen* |
| `dequeued` | the traversal — what BFS has *processed* |

Conflating them is the most common way a BFS visual lies, so the traversal strip renders
`dequeued` and a test pins it: after enqueuing B and C, `visited` is `[A, B, C]` while the
traversal is still `[A]`.

### Queued is its own node state

Three states were not enough. A node that has been *seen and is waiting* is neither unvisited
nor processed, and that middle state is the frontier — the thing that makes level-order
visible. It reuses `CellState.CANDIDATE`, the amber Selection Sort uses for a value it is
holding on to, which is exactly the right meaning: found, not yet acted on.

### The queue is drawn, not written

BFS *is* the queue, so it gets cells flanked by `OUT ←` and `← IN` — the language the Queue
lesson already established (ADR-027) — rather than a line of text. An empty queue reads
`empty`, because it is the termination condition and not a blank.

**Alternatives considered.**
- *A different graph for BFS.* Rejected: it would let the learner blame the graph for the
  different traversal, which is the one conclusion the lesson must prevent.
- *Separate ENQUEUE / DEQUEUE buttons.* Rejected for the reason ADR-034 rejected a BACKTRACK
  button, and additionally because it would make BFS's controls differ from DFS's — putting the
  difference in the UI rather than in the algorithm.
- *Enqueue all of a node's neighbours in one step.* Rejected: the per-neighbour beat is where
  "skip the visited one" is asked, and that is one of the five judgements.
- *Show `visited` as the traversal.* Rejected above — it is a different sequence, and on a
  bigger graph it is visibly wrong rather than merely imprecise.

---

## ADR-036 — A Binary Search Tree is a graph, so it reuses one — and adds no scene shape

**Decision.** BST ships as a `LessonPack` with WATCH and TRY, in the **Advanced**
category. Its data model is a new `BinaryTree` in `engine/core/`; its picture is the
**existing `GraphScene`**, drawn by the **existing `GraphStage`**. `Dataset` gains an
optional `tree`. Full detail: `docs/binary-search-tree.md`.

**Why no fifth shape.** ADR-030, ADR-033 and ADR-034 each added a `Scene` shape because
the data genuinely was not a sequence: a hash map has buckets, prefix sum has two arrays
of different lengths, a graph is two-dimensional. A binary tree is **nodes at positions
joined by edges**, which is exactly what `GraphScene` already is. Adding `TreeScene`
would have been a second name for the same picture, and the two would then have had to
be kept looking alike by discipline rather than by construction.

The judgement is the same one those three ADRs made, applied honestly in the other
direction: a new shape is for a new *kind* of data, not for a new lesson.

### What was genuinely new was a node state, not a shape

DFS and BFS are traversals: they visit everything they can reach, so nothing is ever
ruled out. A **search** discards regions of the structure without looking at them, and
that is the whole point of a BST. So the shared scene gained `EdgeState.ELIMINATED`, and
`GraphStage` learned to draw an `ELIMINATED` node — the state was already in `CellState`
and already in the legend, and it had simply never reached a graph before.

Four additive, defaulted fields in total (`EdgeState.ELIMINATED`, `badge`,
`traversalLabel`, `showPathStrip`), so **DFS and BFS did not change** — the same move
`queue` made when BFS arrived.

### The decision is Binary Search's, and the app owns the arithmetic

`probe` returns `Mechanical(Compare)` to read the target against the current node, then
`Decide` for the move. Reading `60 > 50` is not a judgement; knowing that greater means
RIGHT is the entire technique. That is Two Pointers' shape exactly, and it gives WATCH
its seam for free: the comparison is stated while the search has **not** moved, and the
move is the next tap. The brief was explicit about this — *"do not instantly jump
without showing the decision"* — and because the engine emits two transitions, the
narrator does not have to invent the split.

All three options — LEFT, RIGHT, FOUND — are offered every round, including where FOUND
is wrong, for the reason ADR-032 gives: an option that only appears when it is correct
answers the question the beat exists to ask.

### Positions are derived, because a tree's shape is its data

`GraphNode.x` is authored for DFS, and rightly: five nodes have a shape a person should
choose. A tree does not get that. `BinaryTree.layout()` places each node at its
**in-order** position horizontally and its depth vertically, which is the only choice
that makes the drawing a statement about the structure rather than an opinion about it:
every node sits between its two subtrees, a parent is always above and between its
children, no two nodes can collide, and left-to-right on screen *is* ascending order —
the invariant, drawn.

Responsiveness falls out of it rather than being handled: two nodes at the same depth
always have their lowest common ancestor between them in in-order, so they are at least
two columns apart, which clears a 48dp node on a 320dp phone without shrinking anything.
A test pins that property.

### The complexity claim is stated in full

The lesson says **O(log n) balanced, O(n) skewed**, together, in the insight line — and a
test drives a deliberately skewed tree to prove the degenerate case is real. "A BST is
O(log n)" is the most common thing said wrongly about them, and a lesson that teaches
only the happy half is teaching something untrue.

### The Binary Search connection is one bullet

*"Binary Search halves a sorted array by arithmetic. A BST keeps that halving in its
shape."* That, plus `Dataset.values` being the tree read in order — which is literally
Binary Search's sorted array. The older lesson is not repeated, and nothing in it changed.

### WATCH and TRY share the tree

The same call DFS and BFS made (ADR-034), for the same reason and one more: seven nodes
are memorisable either way, so a second tree would buy unfamiliarity rather than a new
judgement — and the brief specified this tree for both stages. What makes TRY hard is
producing each comparison's answer with the wrong branch one tap away. A different
target over the same tree is the obvious next dataset, and it is data rather than code.

**Alternatives considered.**
- *A `TreeScene` and a tree renderer.* Rejected above — and it would have split the
  visual language for a picture the graph renderer already draws correctly.
- *Force it into `SequenceScene` as a row with `links`.* Rejected: the linked list is a
  chain because a list *is* a line. A tree is not, and the ruled-out subtree — the whole
  lesson — is not expressible as a contiguous row of cells the learner can read as a
  shape. (It *is* a contiguous in-order range, which is why `Eliminate` still carries an
  honest slot range, and why the connection to Binary Search is real.)
- *Tap the node to move to, as DFS and BFS do.* Rejected, and this is the one place BST
  deliberately differs from its neighbours: the brief specified LEFT / RIGHT, and it is
  the better question here. DFS's tap works because *which* neighbour is the judgement;
  in a BST there are only ever two children and the judgement is **which side the rule
  sends you to**. Naming the sides is what makes the answer transferable to a tree the
  learner has not seen.
- *Ask the learner to perform the comparison.* Rejected: it is arithmetic with one legal
  answer, and PRODUCT_SPEC.md §3 rejects exactly this.
- *Teach insertion or traversals as well.* Rejected as scope. `BinaryTree` carries
  `insert` and its node shape supports delete and the four traversals, so each is an
  algorithm and a narrator over the same model — but building them now would have made
  the lesson about a structure rather than about a search.

---

## ADR-037 — AVL asks which node comes up, not which case it is

**Decision.** AVL ships as a `LessonPack` with WATCH and TRY, in the **Advanced** category.
Its two decisions are both **taps on the tree** — *which node is out of balance?* then
*which node takes its place?* — and it reuses the `GraphScene` and `GraphStage` the BST and
the two graph lessons already use. `GraphNodeView` gains `caption` and `captionAlert`. Full
detail: `docs/avl-tree.md`.

**Why it was worth building.** The Binary Search Tree lesson has to end on a caveat: a BST
is O(log n) only while it stays bushy, and O(n) when it skews. Leaving a learner there is
leaving them with a technique and a reason to distrust it. AVL is the answer, and it is the
first lesson in the app about a structure that **maintains its own invariant**.

### The learner names a node, not a case

The obvious build gives four buttons — RIGHT, LEFT, LEFT-RIGHT, RIGHT-LEFT — and asks the
learner to classify LL/LR/RL/RR. That was rejected twice over.

Practically, four `DecisionButton`s do not fit one row at 16sp/800; "LEFT-RIGHT" alone is
wider than the button it would sit in. But the real reason is the one ADR-034 gives for
rejecting a BACKTRACK button: a row of case names teaches the learner to classify a shape
into a label and reach for the matching control. What they actually have to see is **which
node comes up** — and that single question collapses two:

| the path below the unbalanced node | which node comes up | how many rotations |
|---|---|---|
| runs straight (LL / RR) | the **child** | one |
| bends (LR / RL) | the **grandchild** | two |

*Child or grandchild* **is** *single or double*. A learner who can point at the right node
has understood the thing the four case names are labels for, and the app names the case for
them the moment they are right — which is the correct order: the name is a handle for an
idea they already have, not a substitute for it.

### Reading the balance factors is the app's; finding the broken one is not

Every node carries its balance factor as a caption, always — including the balanced ones.
Showing it only where it is broken would turn "find the unbalanced node" into "find the
node with a number next to it". Computing `height(left) - height(right)` is arithmetic the
app owns (PRODUCT_SPEC.md §3); scanning for the one that broke, and knowing to take the
*lowest* of them, is not.

`GraphNodeView.caption`/`.captionAlert` are defaulted, so DFS, BFS and the BST needed no
change — the same additive move `queue`, `badge` and `EdgeState.ELIMINATED` made before.

### The teaching data is authored around three different lessons

Three insertions, and the first one **needs no rotation at all**. That is the same call
ADR-025 made putting Bubble Sort's *keep* second: a learner who only ever sees insertions
that rotate will conclude that insertion means rotation. The second is a straight imbalance
and the third is a bent one, so WATCH covers single and double without covering all four
named cases.

TRY is then **the mirror image** — the same three shapes with every direction reversed, so
it is application rather than recall even though the shapes are familiar, and the two
stages together cover all four cases. Getting the mirror wrong is precisely where this
technique fails in practice.

### A double rotation is shown as two rotations

The engine performs the plan one step at a time, so WATCH gets two beats for a double and
the first one says out loud that it has not fixed anything yet — it straightened the bend
so the second could be an ordinary single. Collapsing them into one step would have made
"a double is two singles" a claim rather than something the learner watched.

### The insight was already in the layout

**A rotation changes depth, never order.** Because `BinaryTree.layout()` places nodes by
their in-order position (ADR-036), a rotation moves nodes between *rows* and never between
*columns* — so the picture demonstrates the invariant that makes rotations legal instead of
asserting it. That was not designed for AVL; it fell out of the choice made for the BST,
and a test now pins it.

### Complexity is stated as a guarantee, not an average

The lesson says O(log n) **guaranteed**, and a test inserts 1…200 ascending — a plain BST's
worst case, a 200-level chain — and asserts AVL stays under 10 levels. This is the payoff
for the honest caveat the BST lesson was made to end on.

**Alternatives considered.**
- *Four case buttons.* Rejected above.
- *Ask the learner to compute the balance factors.* Rejected: it is subtraction with one
  legal answer, and PRODUCT_SPEC.md §3 rejects exactly this.
- *Let the app find the unbalanced node and ask only for the rotation.* Rejected: rotating
  at the wrong node — usually the root — is one of the two real mistakes, so it has to be
  askable.
- *Perform a double rotation in one step.* Rejected above.
- *Teach deletion as well.* Rejected as scope: deletion needs up to O(log n) rotations
  rather than one, which is a different lesson, not a longer one.

---

## ADR-038 — Three traversal lessons over one machine, with the rule stated in each

**Decision.** Inorder, Preorder and Postorder ship as **three separate Advanced lessons** —
three `AlgorithmId`s, three `LessonPack`s, three cards, three progress entries, three
narrators, three test suites — driven by **one** `TreeTraversalAlgorithm`. Each lesson
supplies a `TraversalRule` whose `order` is the algorithm, written on one line in its own
file. The gesture is DFS's: **tap the node the traversal touches next.** Full detail:
`docs/tree-traversals.md`; the design plan and its decision trail: `docs/tree-traversals-plan.md`.

⚠ **Product-owner decision**, taken 2026-09-09: three user-facing modules, no duplicated
engine.

### Why three lessons rather than one with a picker

The three orders on one tree *are* the content. A single lesson with a mode selector would
make the difference a thing the learner switches rather than a thing they produce, and
would give one progress entry for three ideas. Three modules also mean a learner can meet
postorder — the hardest — on its own terms rather than as a third tab.

### Why one machine rather than three

The three state machines are the same machine with `VISIT` in a different position in a
three-element list. Writing that out three times would have been ~380 lines of near-identical
code and three places to fix any bug in the stack handling.

This is exactly what ADR-027 did for Stack and Queue — one `LinearStructureAlgorithm`, one
flipped property, two lessons — and the objection there and here is the same: does the
lesson's content disappear into a constructor argument? It does not, because the argument
is not passed from anywhere. Each lesson file *is* the rule:

```kotlin
object InorderRule : TraversalRule {
    override val order = listOf(Step.LEFT, Step.VISIT, Step.RIGHT)   // the algorithm
}
```

One line, named, documented, in `InorderTraversal.kt`, above the fifteen lines of that
lesson's own copy. The rule is the most visible thing in the file, which is the test that
matters — not whether the machine underneath is shared.

### One gesture, and why it is never ambiguous

At any moment the traversal's next action is one of four things, and each maps to a
**different node**: the left child, the node itself, the right child, the parent. So "tap
the node" carries both *what* and *which* without the learner ever saying which kind of
touch they meant, and the same gesture serves all three lessons — which is what stops a
learner attributing the different orders to different controls.

A row of `GO LEFT / VISIT / GO RIGHT / BACK` buttons was rejected for the reason ADR-034
rejected a BACKTRACK button, and because four `DecisionButton`s do not fit one row.

### Two beats the app performs, and why that is not a loss

**Returning to the parent.** Once a node's steps are done there is nothing else the
traversal could do. Unlike DFS — where *when* to backtrack is the judgement — it is never a
choice here, so it is bookkeeping (PRODUCT_SPEC.md §3). Nothing is lost, because the
question asked immediately *after* a return is the beat that separates the three
traversals: *visit this node, or go right?*

**Visiting on arrival.** Moving into a node performs its own visit when that is the next
step. Without it the learner taps the same node twice in a row at every leaf, and the second
tap has exactly one legal target — the gesture-teaching trap, at every leaf, in every
lesson. A test asserts no two consecutive taps are ever the same node.

Together these cut 17 raw actions to 9 decisions, and 20-odd beats to 12–16.

### The same line of logic produces opposite walkthroughs

A return earns a WATCH beat **when it lands on a node that still owes something.** In
postorder that is every return, and those beats are where the lesson lives. In preorder it
is never, because a parent is always already out by the time the traversal comes back up —
so preorder's walkthrough has no return beats at all, and *that is the lesson* rather than
an omission. One predicate, three characters.

### The trap in the shared tree, found and fenced

`BinaryTree` was built for search trees, and `node()`, `searchPath()`, `parentOf()` and
`subtree()` descend **by comparing values**. On a tree that is not ordered they silently
fail to find nodes that are plainly on screen — and the traversal lessons deliberately use
one. Structural equivalents were **added** (`findNode`, `pathToNode`, `parentByStructure`,
`childrenOf`, `preorder`, `postorder`); nothing existing changed, so BST and AVL behaviour
could not move. `BinaryTreeTest` pins the distinction with the trap itself: `node(5)`
returns null on the TRY tree while `findNode(5)` returns the node.

`BstNode` → `TreeNode` landed first, in its own revertable commit, with the full suite green
either side.

### TRY runs on a tree that is not a search tree

Inorder on a BST is the sorted order, so the WATCH answer could be produced by sorting seven
numbers without traversing anything. TRY's tree gives `2 4 7 9 5`, which only the rule
produces — and the Inorder lesson says the reason out loud: *"Inorder traversal is a
traversal rule, not a sorting algorithm. It produces sorted values only when the tree itself
is a search tree."* It carries a left-only and a right-only child as ordinary content too.

### The gate held

Adding Preorder, and then Postorder, after Inorder was finished required: a rule object, a
narrator, a copy block, a dataset reference, a card, a test suite. **Zero** new events, scene
shapes, renderer changes, screens or interaction models, **zero** lines of
`TreeTraversalAlgorithm` or `TreeWalk`, and **zero** changes to any existing lesson. Not one
line of `:app/ui/components` was touched by any of the three.

**Alternatives considered.**
- *Three fully independent engines.* Rejected by the product owner, and the plan's own
  estimate agreed: ~380 lines against ~120, with three places to fix one bug.
- *One lesson with a traversal picker.* Rejected above.
- *Four decision buttons.* Rejected above.
- *Charging a tap for the return to the parent.* Rejected: 13 decisions instead of 9, for a
  move with exactly one legal target.
- *TRY on the same tree.* Rejected: inorder becomes answerable by sorting.
- *Level-order as a fourth rule.* Deferred, and it does not belong here: it is driven by a
  queue rather than a stack, so it is a sibling of this machine rather than a fourth order
  for it.

---

## ADR-039 — Dijkstra: weights are additive, and the frontier is not a queue strip

**Decision.** Dijkstra ships as a `LessonPack` with WATCH and TRY, in **Advanced**, as the
third graph lesson. `Graph` gains a defaulted `weights` map; `GraphEdgeView` gains a
defaulted `label`; `GraphNodeView` gains a defaulted `secondaryLabel`. The learner makes two
decisions — **tap the cheapest node**, then **choose what a distance becomes**. Full detail:
`docs/dijkstra.md`; the plan and its validation: `docs/dijkstra-plan.md`.

**Why it follows BFS.** DFS and BFS are traversals; this is the first graph lesson that is a
**search**. It also closes the set: where every edge costs 1, the cheapest unsettled node is
the nearest one, so Dijkstra *is* BFS — and the weights are the entire reason a different
algorithm exists. That is one line in the recap, not a comparison screen.

### Weighted edges are one defaulted field, not a second graph type

`Graph.adjacency` is `Map<String, List<String>>`, and changing it to carry edge objects would
have touched DFS, BFS, their datasets and six test helpers. Instead `weights: Map<String, Int>
= emptyMap()`, keyed canonically so an undirected edge has one entry however it is looked up.
Every existing construction site compiles and behaves identically, and `GraphWeightTest`
asserts the thing that actually matters: **DFS and BFS produce byte-identical states on a
graph that carries weights**, because a traversal asks where it can get to and never what it
costs.

`weightsOf` **rejects a non-positive weight at construction**. A negative edge does not make
Dijkstra give a worse answer, it makes it give a wrong one, so the lesson must be unable to
hold one — validated where it is authored rather than handled at runtime.

### Relaxation is asked as a number, and the wrong numbers are the misconceptions

The selection rule is the famous half of Dijkstra, but relaxation is the half that is
actually understood or not. It is asked as a choice between three values — Prefix Sum's
pattern (ADR-033):

> C is 2, C→B costs 1, B is currently 5. What should B be? **3** · 1 · 5

`1` is the edge weight alone — *forgetting to add where you already are*. `5` is *not
updating*. In the KEEP case the same question offers `11` · `5` · **`7`** and the correct
answer is to change nothing. **One question shape covers both branches and three of the
listed misconceptions**, with no second control, and the comparison is never printed before
the learner makes it.

### Two beats stay the app's, and the pedagogy survives both

A neighbour with **no distance yet** is not asked about: ∞ loses to everything, so there is
nothing to compare (PRODUCT_SPEC.md §3). The app still says the arithmetic out loud, so the
formula is narrated five times and tested four. Stepping past an **already settled**
neighbour is narrated rather than asked, and that narration is where *"settled means final"*
is taught. Together they cut about twenty raw actions to nine decisions.

### The frontier is amber nodes, not a sorted strip

The plan asked whether to show the priority queue. It is shown — **as the amber nodes
carrying their distances** — and deliberately not also as a sorted list, because a strip
reading `C 2 · B 5` answers the question the lesson asks before the learner does (ADR-030).
BFS may draw its queue because taking the front is not a judgement there; here it is the
entire rule. A heap with sift-up and sift-down is an implementation of the idea, not the idea.

### The layout was validated before the lesson was written

A 1:1 spike at 360dp, run as its own step, found two things this ADR would otherwise have
shipped wrong:

1. **The proposed positions failed.** A and B sat 69dp apart, leaving 21dp of bare edge for a
   20dp label. Replaced with a **ladder** — the graph is a strip of triangles, so it draws as
   two rows with one diagonal rung. Shortest edge 83.5dp, every label clearing every circle,
   no crossings. Only coordinates changed; the dataset did not.
2. **Distances cannot reuse `caption`.** AVL hangs its balance factor off the node's
   top-right, which works on a *tree* because that space is empty by construction. On a graph
   it is where edges leave, and two of six captions landed on one. Hence `secondaryLabel`,
   rendered **inside** the circle — the only placement that cannot collide, because the node
   already owns that space.

Node touch targets stayed at 48dp. **The lesson was fitted to the phone by moving nodes, not
by shrinking them** — which is the rule worth keeping from this exercise.

### TRY gets a different graph

The house default, and here it matters more than usual: the answer is a short memorable
sentence and the run is nine decisions, so recall would replace reasoning entirely. TRY's
graph also teaches what WATCH cannot — **two** KEEPs rather than one, and a winning route
that is the plain direct one, with all the work elsewhere not on it.

**Alternatives considered.**
- *A `WeightedGraph` type beside `Graph`.* Rejected: two graph models to keep in step, and
  every renderer branch doubled.
- *Changing `adjacency` to carry edge objects.* Rejected: it breaks DFS, BFS and six test
  helpers to add a field two of them will never read.
- *A sorted priority-queue strip.* Rejected above.
- *Asking UPDATE / KEEP as two buttons.* Rejected: it tests the comparison but not the
  arithmetic, and the arithmetic is where the two commonest mistakes live.
- *Charging a tap for reaching a node at ∞.* Rejected: one legal answer is not a decision.
- *Shrinking the node to fit nine edges.* Rejected — see the spike.

---


---

## ADR-040 — Counting Sort is a Sorting lesson, and the table is a fifth scene shape

**Decision.** Counting Sort ships as a `LessonPack` with WATCH and TRY, in the
**Sorting** category beside the five comparison sorts. `Scene` gains a fifth shape,
`CountingScene`. The learner's gesture is a tap on a count bucket, and it is the
same tap in both halves of the lesson. Full detail: `docs/counting-sort.md`.

**Why it was worth building.** Every sort in the library so far answers the same
question — *is this bigger than that?* — and a learner who has done five of them
can reasonably conclude that sorting *is* comparing. Counting Sort is the
counter-example, and it is the first lesson whose Complete screen reports **0
comparisons** as a true fact about the run rather than as a claim in the copy.

### The category is Sorting, and there is no other kind

The brief asked for it to be free, under Sorting, and not behind "Advanced/Pro".
Two thirds of that is a no-op here and it is worth writing down why: **AlgoKing has
no paid tier.** `PRODUCT_SPEC.md` §1 forbids locked content, and "Advanced" is a
category for techniques rather than a price (ADR-032). So the requirement resolves
to one line — `category = "Sorting"` on the library entry — and the free/paid half
had nothing to act on.

The teaching order puts it after Quick Sort: the comparison sorts hand over to the
one that does not compare at all, which only lands once the learner has met the
comparison model enough times to have generalised it.

### A count table is not a sequence

`SequenceScene` is one row whose slots are **positions**. The count table is
indexed by **value** — bucket 3 is not the fourth thing in a line, it is the answer
to *"how many threes?"* — and that is precisely the idea the lesson exists to
leave behind. Flattening it into positions would say the opposite of the lesson.

`PrefixScene` was the near miss, and the reason it fails is instructive: its two
rows share slots because `array[i]` genuinely produced `prefix[i + 1]`, and that
offset *is* Prefix Sum. Counting Sort's three rows have no column relationship at
all, so they are laid out independently — inventing an alignment would be a lie
about the data, and the same judgement ADR-030, ADR-033 and ADR-034 each made.

What the shape did **not** need: no new `VizEvent`, no new interaction model, no
new renderer contract, and no change to any existing lesson. Four `when` sites over
`Scene` gained a branch, which is the compiler doing exactly the job the sealed
union is for.

### One gesture, twice over

Counting and rebuilding are asked with the same tap on the same table. That is
deliberate: it is one table doing one job — being filled, then being read — and a
second control would imply a second idea. It is the reasoning ADR-034 used to
reject a BACKTRACK button, applied to a lesson with two phases instead of two
kinds of move.

The wrong taps carry the lesson's three real misconceptions, and the engine can
tell them apart because it knows the difference between a bucket that counted
nothing, a bucket already spent, and a bucket that still has values but is not
next.

### Two things the app does, and why that is not a loss

**The increment.** Once the bucket is named, adding one to it is the only legal
move, and tapping the only legal target teaches a gesture (`PRODUCT_SPEC.md` §3).
The app raises it and narrates `count[3]: 1 → 2`, which is the beat the lesson is
about — watching it is the point, performing it is not.

**The offset.** `value - min` is arithmetic with one answer. The learner names a
value and the app finds the slot, so the word "index" never appears in the copy.
A bucket is *the bucket for the value 3*, which is the form that transfers to a
table the learner has not seen.

### Stability is deliberately not taught

Real counting sort accumulates the counts into starting positions so equal
elements keep their original order. That is a second idea stacked on the first,
and it makes the rebuild a loop over the *input* rather than over the table —
which would hide the thing this lesson is for. What ships is the honest core, and
`docs/counting-sort.md` says so rather than leaving a reader to notice the
omission. Radix Sort is where stability has to arrive, because that is where it is
load-bearing.

### The range is `min..max`, and the empty buckets are content

The table spans the range the lesson just went and found, not `0..max`. The
teaching data leaves three buckets empty on purpose: the rebuild has to step over
them, so *"a count of zero places nothing"* is watched rather than asserted, and
the cost of `k` is visible in the picture before the recap names it. An outlier
value (8, when everything else is 1–4) is what makes that cost concrete.

**Alternatives considered.**
- *Force it into `SequenceScene` with `groups` separating three sections.* Rejected:
  groups divide **one** sequence, and these are three arrays with different lengths
  and different index meanings.
- *Ask the learner to perform the increment.* Rejected above — one legal answer is
  not a decision.
- *Ask "how many of this value?" once per bucket in the rebuild, instead of one tap
  per element.* Rejected: it collapses `count[2] = 2` into a single answer, and the
  duplicate coming out twice **is** the beat that connects the table to the output.
- *Teach the prefix-sum placement pass for stability.* Rejected as scope, above.
- *Put it in Advanced.* Rejected: it is a named routine over an array, which is what
  Sorting is; Advanced is where techniques live (ADR-032).
- *0..max instead of min..max.* Rejected: the lesson opens by finding the range, and
  a table that ignores what it found would make step 2 decorative.


---

## ADR-041 — The Advanced shelf becomes Pro, and entitlement has no back door

**Decision.** The ten **Advanced** lessons require an **AlgoKing Pro** subscription; the
other eleven stay free. Tapping a locked lesson opens a paywall instead of the lesson.
Access is decided in one place, `billing/ProAccess.kt`, from the lesson's **category** and
the store's **entitlement**. Full detail: `docs/pro-access.md`.

⚠ **This reverses a locked principle**, taken by the product owner on 2026-09-10.
`PRODUCT_SPEC.md` §1 read *"free forever, AdMob only"* and listed subscriptions, paid
algorithms and locked content under **Never**. §1 is amended in place rather than
contradicted in silence, because a spec the code disagrees with stops being a spec.

**What did not change.** Coins, gems, energy, lives, leaderboards, a social graph and a
required login are still never. Neither is anything taken away from a free lesson: eleven
lessons keep both stages, the whole guidance ladder and their progress. Pro adds lessons.

### Access derives from the category, and is not a second flag

`AlgorithmEntry` gains **no** `isPro` field. ADR-032 established that *Advanced is a
category, not a new mechanism*, and a price flag beside the category is exactly the parallel
taxonomy it refused: two things to keep in step, and eventually an Advanced lesson that is
accidentally free. So the rule is one line — the Advanced shelf is the Pro shelf — and a
lesson filed there is protected the day it is added, with nothing to remember.

The whole decision is four lines and lives in one object:

```
free                  -> open the lesson
pro, and entitled     -> open the lesson
pro, and not entitled -> show the paywall
```

`ProEntitlement.Unknown` is deliberately **not** entitled. Showing the paywall to someone who
turns out to own Pro is a moment's friction that the next purchase-state read corrects;
opening a paid lesson for someone who does not own it is giving it away.

### There is no path from a tap to an entitlement

This is the invariant the feature rests on, and it is structural rather than a rule someone
has to follow. `SubscriptionRepository` has no method that sets Pro. `ProEntitlement.Pro` can
only come out of a `BillingGateway`, which must derive it from queried, acknowledged
purchases. After a purchase the repository **re-reads what the store owns** instead of
trusting the outcome it was just handed — and there is a test for the case that matters: a
flow that reports success while the store owns nothing grants nothing.

No boolean is persisted. Progress is latched and additive (ADR-028) precisely because
progress is earned and cannot be taken away; an entitlement is the opposite — a refund, an
expiry or a cancellation must be able to take it back — so it is read from the store every
time and never cached to disk.

### Billing is a seam, and the seam is now filled

> **Amended the same day.** Play Billing is connected: `PlayBillingGateway` against
> `billing:8.0.0`, constructed in `MainActivity`. The paragraph below described the state
> the seam shipped in a few hours earlier, and it is kept because it is why connecting it
> touched exactly one construction site and no screen, repository or test. The
> `algoking_pro` product still has to be configured in Play Console before anything can be
> sold, and `UnconfiguredBillingGateway` survives for tests and previews — still unable to
> produce `Pro`.

### The seam as it shipped

There is no Play Billing integration in this build and **no stub that pretends otherwise**.
`UnconfiguredBillingGateway` reports `NOT_CONFIGURED`, entitles nothing, and refuses to sell.
A debug-only "grant Pro" switch was considered and rejected: it is one merge away from
shipping, and it is the exact fake entitlement the design exists to prevent. Connecting
billing for real means writing one `BillingGateway` against `BillingClient` and changing one
construction site.

Because of that, the paywall today shows no price and its CTA is disabled with the reason
stated: *"Pro is not on sale yet."* That is the honest state, and it is better than a number
nobody will be charged.

### The price is never in the app

`ProProduct.formattedPrice` is Play's own localised string, passed through untouched. Nothing
assembles a price from a number and a currency symbol, because that is how an app shows
"$4.99" to someone who will be charged ₹399 — and nothing marks a plan "best value" unless
the store's own configuration does.

### The paywall is a screen in the app, not an ad wearing its clothes

Same cards, same buttons, same 20dp radius, same gold ornament. No countdown, no
strike-through discount, no invented user counts, no "master DSA in 7 days". Those are all
available and all cost more trust than they earn — in an app whose pitch is that it is honest
about how learning works, the paywall is where that claim is tested.

It is **contextual first and complete second**: tapping Dijkstra opens on *"Unlock
Dijkstra"*, because a paywall that does not say why it appeared reads as a trap, and then
gives the full offer — what Pro includes, which ten lessons, the price. One dominant CTA, a
quiet Restore beside Privacy, and the way out as a plain sentence that can never be mistaken
for the purchase.

### The lock is a crown, not a padlock

A Pro card changes exactly one thing: a small gold `PRO` pill beside its category badge. No
dimming, no padlock over the tile, no greyed title. A locked lesson is an **offer**, and an
offer that looks broken sells nothing — it also has to keep looking like the library it lives
in (DESIGN_SYSTEM.md §6.3a).

**Alternatives considered.**
- *An `isPro` flag per entry.* Rejected above — a second taxonomy to keep in step with the
  first.
- *A `proUntil` timestamp cached in DataStore.* Rejected: it is a stored boolean by another
  name, it survives a refund, and it is writable by anything that can reach the repository.
- *A debug flag that grants Pro for testing.* Rejected above. A real `BillingGateway`
  implementation backed by Play's test tracks is how this gets exercised.
- *Locking a lesson the learner has already completed.* Not decided here and worth flagging:
  the ten Advanced lessons have existing progress on installed builds, and this change locks
  them. `docs/pro-access.md` names it as the open question it is.
- *Putting the paywall behind Settings as well.* Rejected for now: one entry point, reached
  by wanting a specific lesson, is the least pushy thing that still works.


---

## ADR-042 — One interstitial, after the lesson, for free learners only

**Decision.** AlgoKing shows **interstitial ads only, to free learners, once, after a TRY
run is finished**, on the Complete screen. No banner, no rewarded, no native, no app-open,
and no second placement anywhere. Pro subscribers see nothing. Full detail: `docs/ads.md`.

**Why this shape.** `PRODUCT_SPEC.md` §9 already argued the hard part: an ad that interrupts
learning costs more than it earns, and the rules are subtle enough that scattering them
across navigation callbacks guarantees drift. What changed is that the assessment stage §9
was written around no longer exists, and Pro now does the earning — so the ad surface could
shrink to the smallest thing that still makes sense: the moment a lesson is genuinely over.

### `Placement` has one member, on purpose

The enum could have been a string, or a boolean, or nothing at all. Making it a one-member
enum means **a new ad placement cannot be added by writing a call site** — someone has to
edit the policy file, which is exactly where the argument about whether a banner belongs on
Home should happen. It is the same reasoning ADR-008 used to make ads structurally
unreachable from lesson code, applied to a codebase that now has an ad in it.

### Where it fires, and why not on the exit tap

§9 said "ads fire on exit paths, never forward paths", which implies the interstitial belongs
on the tap that leaves the Complete screen. That is **not** what shipped, and the reason is
that §9's own rule forbids it: two of the three exits from Complete — *Next algorithm* and
*Try again* — lead **into** more learning, and an interstitial there is precisely the
forward-path ad the section prohibits. Attaching it to the third exit only would mean the ad
depends on which button the learner reaches for, which is worse than either.

So it fires on **arrival** at Complete, after a 1.2 second settle. The learner has finished,
the metrics and the takeaway have landed, and nothing sits between them and a lesson. §9 is
amended to say so rather than left contradicting the code.

### One completion, one opportunity — and it is not a convention

Each finished run mints a `completionId`; the policy refuses any id it has already shown
for; the id is `rememberSaveable`, so a rotation or process death cannot resurrect the
opportunity; and `InterstitialAds.show` clears the loaded ad *before* presenting it, so a
double call has nothing left to show. Four independent mechanisms, because Compose
recomposition is not a thing to be careful about — it is a thing to be immune to.

### Availability is never the learner's problem

Failed load, no fill, no network, no Activity, an SDK error, a presentation that does not
take: all of them end with the app carrying on and the next ad requested quietly. Three
consecutive failures stop the requests until something succeeds, so an offline device does
not burn battery asking. **Nothing is ever said to the learner about an ad** — no error, no
"watch this to continue", no placeholder, no container. The app looks identical when no ad
is showing, which is the point.

### Pro is checked first, and the loaded ad is thrown away

The order of the conditions is load-bearing: entitlement is tested before anything else, so
no combination of "already loaded" and "not yet shown" can reach a subscriber. On top of
that, the moment entitlement turns Pro the loaded ad is discarded and loading stops — an ad
fetched while the learner was free must not be shown to them after they pay for its absence.
Entitlement comes from the billing layer built in ADR-041; there is no second Pro flag.

### The SDK is initialised once, off the main thread

`AlgoKingApplication` is the class `ARCHITECTURE.md` §3 always named and never needed until
now. `MobileAds.initialize` does disk and network work, and on the mid-range devices this app
targets (`PRODUCT_SPEC.md` §16) that is a visible hitch on the first frame — so it runs on a
background thread, and the first ad is requested from its callback. No screen initialises the
SDK, and nothing re-initialises it.

`InterstitialAds` holds the **application** context and takes the Activity as a parameter to
`show`. A long-lived singleton with an Activity field is the classic leak, and it is also how
an ad ends up presented into a window that is already finishing.

### Test units ship, and a test says so

The build points at Google's sample app id and interstitial unit. `AdPolicyTest` asserts
that — the test failing is the signal that a production unit was set, and a reminder that
the manifest's `APPLICATION_ID` is the other half of the same change. Testing against a
production unit is invalid traffic, and AdMob suspends accounts for it.

**Alternatives considered.**
- *A banner on Home.* Rejected: §9 forbade it before there was any ad code, and a permanent
  ad container contradicts the one thing the design system is for.
- *A rewarded ad for a hint.* Rejected: hints belong to CHALLENGE, which does not exist, and
  the guidance ladder deliberately arrives without the learner having to ask (ADR-031).
- *An ad on the exit tap from Complete.* Rejected above.
- *Firing at TRY's terminal state rather than on Complete.* Rejected: the learner would be
  interrupted between finishing and finding out how they did, which is the one beat the
  Complete screen exists for.
- *Retrying a failed load on a timer.* Rejected: a learner with no signal would generate a
  request per completion forever, and the payoff is an ad nobody asked for.


---

## ADR-043 — Consent is gathered before the ads SDK starts, not after

**Decision.** Google's **User Messaging Platform** gathers consent on every launch, and
`MobileAds.initialize` runs only once UMP reports `canRequestAds()`. A Pro subscriber is
never asked. When UMP says a privacy-options entry point is required, Settings grows one.
Full detail: `docs/ads.md`.

**Why it had to be this order.** The tempting shape is to initialise the SDK at startup and
gather consent alongside it — the app feels the same and the ad is ready sooner. It is also
the shape that gets an app's ad serving restricted: under GDPR and the DMA, using the
advertising ID to personalise ads is processing personal data, and AdMob policy requires a
certified CMP for EEA, UK and Swiss users **before** that happens. An SDK initialised first
has already started before anyone has been asked.

So the sequence is `requestConsentInfoUpdate` → `loadAndShowConsentFormIfRequired` →
`canRequestAds()` → `initializeAdsOnce()`, and nothing requests an ad before the last step.

### Every launch, and the form only when UMP says so

Consent information is refreshed on **every** launch, because a learner's region and the
rules that apply to it can both change between sessions — and because the consent they gave
can expire. What is *not* done every launch is showing a form:
`loadAndShowConsentFormIfRequired` decides that from the actual region and consent state. On
an India-weighted audience (`PRODUCT_SPEC.md` §16) most learners will never see one, and
that is the SDK's answer rather than an assumption made here.

**No custom dialog.** The form is Google's, rendered from the Privacy & Messaging
configuration in the AdMob console. A hand-rolled one would not produce a valid TCF consent
string and would not be a certified CMP, so it would look like compliance while being none.

### Failure leaves the app exactly as it was

If the update fails, the form fails to load, or the learner declines, `canRequestAds` stays
false: the ads SDK is never initialised, no ad is ever requested, and every lesson behaves
identically. Nothing waits on consent machinery and the learner is never told about it —
the same rule ADR-042 set for ad availability, applied one layer earlier.

The gate is a pure function, `AdPolicy.mayRequestAds(entitlement, canRequestAds)`, so the
rule is testable without a device or a network.

### Pro is not asked to consent to advertising it will never see

A subscriber's consent information is still refreshed — so that someone who consented while
free keeps their privacy-options entry and can withdraw, and so a lapsed subscription finds
the state current — but no form is shown and no ad is requested. Asking a paying learner to
consent to ad personalisation would be a dark pattern in miniature: a question whose only
possible purpose is a thing they have paid to not have.

### The privacy-options entry point appears exactly when it is required

Being able to withdraw consent is part of having asked for it, so when
`privacyOptionsRequirementStatus` is `REQUIRED` Settings shows an **Ad privacy options** row
that opens Google's own form. When it is not required the row does not exist — a row that
opened a consent form for someone who was never asked to consent is noise, and it makes the
Settings screen a worse answer to "what can I change here?".

### Testing it outside the EEA

`ConsentDebugSettings` can force the geography, and it is wired in — but only in a
debuggable build, and only when a hashed test-device id has been added by hand. The id list
ships **empty**, so the override is inert by default: a populated list checked into a
release would be a developer's own device steering real behaviour. `reset()` is likewise
debug-only; in a release build it would re-ask every learner on every launch.

**Alternatives considered.**
- *Initialise ads at startup and gather consent in parallel.* Rejected above — it is the
  arrangement the requirement exists to prevent.
- *A custom consent dialog matching the design system.* Rejected: not a certified CMP, no
  valid consent string, and it would look like compliance while providing none. This is one
  of the few places in the app where Google's own UI is the right answer.
- *Show the form to everyone, to keep the code simple.* Rejected: it is exactly the
  "unnecessary form" the guidance warns about, and it would ask most of this app's learners
  a question their region does not require.
- *Ask Pro subscribers too, for uniformity.* Rejected above.
- *Declaring `user-messaging-platform` explicitly in the version catalog.* Rejected as a
  duplicate: it arrives with `play-services-ads` 24.7.0 (as 3.2.0), and pinning a second
  version is how the two drift.

## Open — ⚠ needs owner sign-off

These are recorded as **assumptions currently in force**. Work proceeds on them; overruling any
one changes the sections named.

| # | Assumption | Affects if overruled |
|---|---|---|
| 1 | ~~Stack + Queue merge into one capstone~~ — **overruled by ADR-027**: two lessons, one engine | — |
| 2 | ~~Pass Prediction (Format B) ships for the three sorts~~ — **moved to V2 by ADR-031**, with the rest of CHALLENGE | — |
| 3 | Target geography is India-weighted, 3 GB device floor | performance budget, eCPM model, `ARCHITECTURE.md` §10.5 |
| 4 | ~~Dark theme only in v1 (ADR-011)~~ — **overruled by ADR-016**: v2 is a light theme | — |
| 5 | **CHALLENGE ships in V2, not the MVP (ADR-031)** | the whole assessment half of the product — `docs/v2-challenge.md` |

---

## Pending — decided at Slice 2

Deliberately deferred until Binary Search stress-tests the architecture, because the right answer
depends on what that slice reveals:

- Whether `SceneProjector` needs a per-phase variant (Challenge may want to suppress pointer
  labels that Watch shows).
- Whether `ConsequencePolicy.Execute.graceSteps` is enough, or whether the consequence reveal
  needs to be a distinct terminal outcome rather than a policy on the decision.
- Whether `Trace` needs pass-boundary indices as first-class data for Format B, or whether
  folding events is sufficient.
