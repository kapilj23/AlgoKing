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
