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

---

## ADR-044 — 0/1 Knapsack: a table of smaller bags, and TAKE reads the row above

**Decision.** 0/1 Knapsack ships as an Advanced (so Pro) `LessonPack` with WATCH and
TRY. `Scene` gains a sixth shape, `DpTableScene`; `Dataset` gains a defaulted
`knapsack`. The learner answers three questions: **which cell TAKE builds on** (a tap
on the table), **TAKE or SKIP**, and — walking back up — **was each item taken**.
Full detail: `docs/zero-one-knapsack.md`; the plan: `docs/zero-one-knapsack-plan.md`.

⚠ **Product-owner decision**, 2026-09-14: approved, with the paywall copy updated from
"ten" to "eleven" advanced lessons (a copy-only change; no billing logic touched).

### Why a sixth shape

A DP table is indexed by two quantities, and the lesson lives in how a cell reads the
row above — straight up for SKIP, `weight` columns left for TAKE. A sequence has one
index; `PrefixScene` and `CountingScene` stack one-dimensional rows; a graph has no
grid. The same judgement ADR-030, 033, 034 and 040 made. Fields name what is drawn —
rows, columns, headers, a two-sided choice — not knapsacks.

### Why the source tap

Printing both candidates and asking for the larger tests `max` and nothing else.
Asking *where TAKE reads from* tests the state's meaning and the 0/1 constraint
together, and its key wrong answer — the same row — is the Unbounded Knapsack
recurrence, refused with that reason.

### Why the brief's dataset was replaced

Enumerated: two optimal bags tie at 13, most-valuable-first already finds 13, and two
cells tie. Any one of those teaches something false. The shipped bags were chosen by
exhaustive search for a unique optimum both greedy strategies miss, no ties, and a
last row with every cell kind.

### Why wrong actions are refused, not applied

Counting Sort applies a wrong count. Here a wrong value in one cell would silently
poison every cell that reads it, so — like Dijkstra, AVL and the traversals — the
engine accepts only the recurrence's answer.

**Alternatives.** Four items (20 recurrence cells, ~20 TRY decisions) · a numeric
keypad (a new interaction model) · three value options per cell (tests `max` only) · a
one-row table (erases what backtracking reads) · the problem beats as captions over one
unchanging scene (greedy's bag has to be a real, engine-computed state).

---

## ADR-045 — Fibonacci teaches why DP exists, and adds no scene shape

**Decision.** Fibonacci ships as an Advanced (so Pro) `LessonPack` with WATCH and
TRY, over `n = 8`. Its picture is an ordinary **`SequenceScene`** — no seventh
shape. `SequenceScene` gains one defaulted field, `equation`. The learner answers
one question seven times: **what is `dp[i]`?** Full detail:
`docs/fibonacci-dp.md`.

⚠ **Product-owner request**, 2026-09-15: Fibonacci as a paywalled Advanced
lesson, tabulation as the interactive experience.

### Why it belongs before Knapsack

0/1 Knapsack (ADR-044) is the app's first DP lesson and it starts from a table
already assumed to be worth building. Fibonacci is the argument for the table:
the recurrence is two lines, running it as written costs about O(2ⁿ), and the
learner watches the cost rather than being told it. So it sits ahead of Knapsack
in the library and in the `Next algorithm` chain — the gentler lesson, and the one
that earns the harder one's premise.

That reordering moved one existing card and one existing chain arm. Nothing else
about Knapsack changed.

### The repeated work is computed, not claimed

The opening beat says *"F(8) takes 67 calls, and F(3) alone is worked out 8
times."* Both numbers come out of the engine — `naiveCallCount(n)` and
`naiveCallsTo(n, k)` — and both are checked by test against recursions that
literally count themselves, for every `(n, k)` pair up to 16.

This is the same rule ADR-034 applied to DFS's traversal and ADR-038 to the three
traversal orders: **the lesson's central claim is generated, never authored.** An
argument a learner is invited to distrust has to be one they can check, and a
hardcoded 67 is a number that quietly goes wrong the first time the dataset moves.

Writing the closed form for `naiveCallsTo` was the one place this bit. The count
looks like Fibonacci — `T(k+2,k) = 2`, `T(k+3,k) = 3` — and it is, for `k ≥ 1`.
For `k = 0` it is not, because naive `fib(1)` returns without recursing and so
never calls `fib(0)` at all. The test caught it; the fix was to stop being clever
and build the counts bottom-up from the actual recurrence, which is also, neatly,
the technique the lesson teaches.

### No seventh scene shape

ADR-030, ADR-033, ADR-034, ADR-040 and ADR-044 each added a `Scene` shape because
the data was a different *kind*. ADR-036 refused one for the same reason, and this
is that refusal again: a DP table indexed by **one** quantity is a row of cells
whose slots are positions, which is exactly what `SequenceScene` is.

Knapsack earned `DpTableScene` because a knapsack cell is *"the first i items,
capacity c"* — two axes, and a cell that reads the row above. Fibonacci has one
axis and reads two cells to its left. Giving it a shape of its own would have been
a second name for a picture the renderer already draws, and the two would then
have had to be kept looking alike by discipline rather than by construction.

What it did add is **one defaulted field**: `SequenceScene.equation`, carrying the
`PrefixEquation` Prefix Sum introduced and drawn by the same `EquationStrip`
composable, made `internal` for the purpose. A sequence lesson whose beat *is* an
arithmetic statement needs the working beside the cells it names, or the numbers
in the narration are ones the learner cannot find. Defaulted, so the twelve
lessons already projecting into `SequenceScene` draw exactly as they did — the
same additive move `groups`, `endCaps`, `links`, `legendLabels` and `showIndices`
all made before it.

The type's name is now historical: it is a labelled two-operand line and nothing
about it is specific to prefix sums. Renaming it would have touched Prefix Sum for
no behavioural gain, so it keeps its name and the field documents why.

### The picture is read from the events, not the cursor

After a value is written, the state's cursor has already moved on. Lighting the
cells the cursor points at would show the learner the *next* pair beside the
sentence explaining the last one — the wrong order to think in, and the thing
ADR-032 split Two Pointers' comparison from its move to avoid.

So the projector reads `VizEvent.Insert` out of `activeEvents` and lights the
operands that actually produced the value being described. Two violet cells and an
amber one, at every beat, and the amber one is always the cell the sentence is
about.

### WATCH and TRY share `n`, and the walkthrough is what differs

Every other lesson gives TRY fresh data (ADR-014). Fibonacci cannot: there is
exactly one Fibonacci sequence, so a second dataset would be a different place to
stop in the same one, and the numbers a learner might have memorised would be the
same numbers.

What makes TRY application rather than recall is the **narration budget**. WATCH
narrates `dp[2] dp[3] dp[4]` in full, collapses `dp[5] dp[6] dp[7]` into one beat
and finishes on `dp[8]`; TRY asks all seven. Four of TRY's seven questions are
about entries the learner was never walked through, and a test pins that.

The collapse is ADR-025's rule — narrate the smallest prefix that builds the
model, then stop. Three entries establish the rhythm: the base cases combining,
the window sliding, and confirmation it was not a coincidence.

### Four opening beats, because six showed the same picture

The conceptual run-up — what the sequence is, the base cases and the rule, the
repeated work, the two fixes — all happens while the table is still empty, so the
copy is the only thing that changes between those beats. ADR-020 says a step where
nothing changed is a bug, and a first draft with six of them was one.

Folding the recurrence into the base-case beat and the table's shape into the fix
that builds it got it to four, each a genuinely separate idea. A test now caps the
run of identical pictures at four, so a future beat cannot quietly rejoin it.

### Every beat is a decision

There is no `Probe.Mechanical` anywhere in this lesson, which makes it the first
one where that is true. Elsewhere the app owns the arithmetic and the learner owns
the judgement (PRODUCT_SPEC.md §3) — but here the addition **is** the recurrence,
so handing it over would leave the learner tapping through a table that fills
itself. What the app owns instead is the two base cases, which are the definition
of the sequence and have no reasoning in them, and the cursor.

### Pro is the category, and nothing else moved

`category = "Advanced"` on the library entry is the entire registration. No
`isPro` flag (ADR-032), no billing change (ADR-041), no second paywall, no new ad
placement (ADR-042). `ProAccess`, `SubscriptionRepository`, `PlayBillingGateway`,
`ConsentManager` and `AdPolicy` were not touched; `PaywallScreen` changed by one
word, because the shelf it counts is now twelve.

**Alternatives considered.**
- *A seventh `Scene` shape for a DP row.* Rejected above.
- *`DpTableScene` with one row.* Rejected: its axes are items and capacity, its
  gutter prints an item's weight and value, and its choice strip weighs TAKE
  against SKIP. Every one of those would be dead or lying here.
- *Ask the learner for `F(0)` and `F(1)`.* Rejected: they are the definition, and
  tapping the only legal answer teaches a gesture (PRODUCT_SPEC.md §3). The same
  call ADR-033 made about `prefix[0] = 0`.
- *Let the app perform the addition and ask only "is this right?".* Rejected: it
  is the whole recurrence, and a lesson where the table fills itself is the thing
  PRODUCT_SPEC.md §1 exists to prevent.
- *A numeric keypad instead of three options.* Rejected for ADR-033's reason: a
  new interaction model for one lesson, and the distractors are where the
  misconceptions get named.
- *Making memoization interactive too.* Rejected as scope: it needs a call tree
  collapsing as subproblems get cached, which is a scene shape this app does not
  have and should not add for one lesson. It is named and explained in the copy.
- *Teaching the O(1)-space variant.* Rejected: it discards the row, and the row is
  the lesson.
- *A different `n` for TRY.* Rejected above.

---

## ADR-046 — Caesar Cipher: a seventh shape, and a category that is not a price

**Decision.** Caesar Cipher ships **free** as a `LessonPack` with WATCH and TRY, in
a new **Encryption** category. `Scene` gains a seventh shape, `CipherScene`;
`Dataset` gains a defaulted `cipher`. The learner answers one question per letter:
**what does this become?** Full detail: `docs/caesar-cipher.md`.

⚠ **Product-owner request**, 2026-09-15: a small free MVP lesson, encryption
category, tabulated against the alphabet.

### The lesson is the ring, not the addition

`A + 3 = D` is arithmetic a learner can do before the lesson starts. `Z + 3 = C` is
the whole content: it is where the alphabet stops being a line and becomes a
circle, and it is what `mod 26` means.

Everything in the lesson follows from taking that seriously. The mapping row is
drawn **in full, all 26 tiles**, including letters the message never uses — a
mapping showing only the letters in play would be tidier and would delete the
lesson. `HELLO` is chosen partly *because nothing in it wraps*, so the wrap can be
taught as its own beat against `Z` rather than arriving mid-word as a surprise. And
that beat lights `Z`'s tile and prints `25 + 3 = 28 − 26 = 2`, because a beat that
says "look at the end of the row" while highlighting a letter in the middle of it
is asking the learner to find its own evidence.

### Why a seventh shape

ADR-036 and ADR-045 both refused one, so this needs to clear the same bar: a shape
is for a new *kind* of data.

A Caesar cipher is a **mapping** — 26 from/to pairs — sitting between two aligned
messages of the same length. None of the six existing shapes says that. A
`SequenceScene` is one row of positions and there are three rows here.
`PrefixScene` has two rows but lays them over shared slots **offset by one**,
because `array[i]` produced `prefix[i + 1]`; plaintext and ciphertext line up
exactly and the alphabet aligns with neither. A `CountBucket` carries one value and
one count, and this carries two letters — the same argument `CountingScene` made
for not being a sequence. `DpTableScene` has two axes; this has one, twice.

What it did **not** need: no new event, no new interaction model, no new cell state,
no new token, and no change to any existing lesson. Five `when` sites over `Scene`
gained a branch, which is the compiler doing the job the sealed union is for. The
message rows are drawn by `SceneCell` unchanged, because it already renders
`cell.label ?: cell.value` — the field missions added for book titles turns out to
be exactly what a letter needs.

### The alphabet wraps onto rows; it never shrinks

Twenty-six tiles in one row is about 7dp each at 360dp, which is not a letter, it
is a smudge. Two rows of thirteen give each tile a legible width.

That is ADR-039's rule applied again — **the layout gives way, never the thing the
learner has to read** — and it is also why the answer is three buttons rather than a
tap on the mapping row, which the brief suggested. Twenty-six tap targets at 22dp
are less than half the 48dp minimum the design system holds every interactive
element to. The alphabet is therefore *evidence the learner reads*, and the answer
is a 56dp button: the pattern Prefix Sum, Dijkstra and Fibonacci already use
wherever the answer is a value, with the wrong options carrying the misconceptions.

Here those are unusually good. Shifting **backwards** is the commonest Caesar error
and the one decryption depends on understanding, so `A → Y` is on the table at every
beat and answered by name.

### Encryption is a category, and a category is not a price

The new shelf is one string in `algorithmCategories`, and the lesson is free
because **it is not filed under Advanced**. ADR-032 established that Advanced is a
category rather than a second taxonomy and ADR-041 made that shelf the Pro shelf,
so "free" needed nothing said about it — no flag, no exclusion list, no billing
change. `ProAccess`, `SubscriptionRepository`, `PlayBillingGateway` and
`PaywallScreen` were not touched, and a test pins that the Encryption category
contains no locked lesson.

This is the first time that rule has been exercised in the *free* direction, and it
is worth recording that it cost nothing, because the tempting alternative — an
`isFree` flag, or an allow-list beside the Pro one — is exactly the parallel axis
ADR-032 refused.

### A space is not a letter

Non-alphabetic characters are passed through untouched, by the app, as
`Probe.Mechanical`. Shifting a space would invent a rule the cipher does not have,
and asking the learner to press a button to copy one is the gesture-teaching trap
PRODUCT_SPEC.md §3 warns about. Neither teaching dataset contains one, so the beat
never fires in the lessons — but it is implemented, narrated and tested, because
"what happens to the space?" is the first question anyone asks.

### Shift normalisation lives at construction

`CipherProblem` reduces its shift into `0..25` when it is built, which is the whole
reason it is a type rather than two loose fields on `Dataset`: 29 and 3 are the same
shift, and an engine that has to remember to reduce one is an engine that will
eventually forget. `-1` normalises to 25, so a backwards shift is expressible and
still lands inside the alphabet.

The `+ 26` before the second `mod` in `shiftLetter` is not decoration: Kotlin's `%`
keeps the sign of its left operand, so a negative shift without it produces a
negative index and an exception two lines later. A test drives every shift from
−30 to 30 over seven messages against an independently written alphabet walk.

**Alternatives considered.**
- *Force it into `SequenceScene` with `groups` separating the rows.* Rejected:
  groups divide **one** sequence, and these are three rows with three meanings —
  the same judgement ADR-033 and ADR-040 each made.
- *Tap the mapping row to answer.* Rejected above, on the touch minimum.
- *Show a seven-letter window of the alphabet instead of all 26.* Rejected: it
  fits comfortably and it deletes the wrap, which is the lesson.
- *Ask the learner to compute the alphabet position.* Rejected: it is arithmetic
  with one legal answer, and PRODUCT_SPEC.md §3 rejects exactly this. The position
  is printed beside the letter in the strip instead.
- *Teach encryption and decryption as two halves.* Rejected as scope: decryption is
  this run backwards, it is named in the recap and in the wrong-answer copy, and a
  lesson that does both teaches neither twice as well.
- *A longer message.* Rejected: five letters and four letters are already enough
  beats for the collapse rule to be eating some, and the content is the ring.
- *Put it under "Advanced" so the category list does not grow.* Rejected: it would
  make a beginner lesson cost money, which is the opposite of what it is for.

---

## ADR-047 — XOR Cipher: the phase is derived, and the caveat is a recap bullet

**Decision.** XOR Cipher ships **free** as a `LessonPack` with WATCH and TRY, in the
**Encryption** category ADR-046 opened. `Scene` gains an eighth shape,
`BitwiseScene`; `Dataset` gains a defaulted `xor`. The learner answers one question
per column: **what is `a ⊕ b`?** Full detail: `docs/xor-cipher.md`.

⚠ **Product-owner request**, 2026-09-15: a small free MVP lesson teaching bitwise
XOR and the same-key reversal, explicitly labelled as not secure encryption.

### The lesson is the reversal, not the operation

The truth table takes one screen and one sentence — *the result is 1 when the bits
are different*. What is worth a lesson is what falls out of it: XOR is its own
inverse, so encrypting and decrypting are not two procedures but one procedure run
twice.

So WATCH does the second pass, over the ciphertext the first pass actually
produced, and `XorState.recovered` is read out of the run rather than copied from
the problem — if applying the key twice did not give the plaintext back, the
lesson would say so. A test proves the property over all 256 four-bit pairs.

### The phase is derived, and that was found by drawing it

The obvious build stores `phase` and rolls it over in `apply` when the first pass
finishes. It passed every test I had written, and it was wrong.

Dumping the walkthrough showed it: the frame that writes the last encrypted bit
leaves a state already in the second phase, so the beat explaining that bit was
drawn with the rows relabelled *Ciphertext / Recovered* and the result row blanked.
The learner would read *"1010 ⊕ 1100 = 0110"* beside an empty row. It is the
failure ADR-032 split Two Pointers' comparison from its move to avoid, arriving
through a different door.

The fix has two halves, and both are worth stating:

1. **`XorState` stores `encrypted` and `decrypted` and derives the phase.** Two
   strings that cannot disagree with a third field, because there is no third
   field.
2. **`XorProjector` decides which pass a *frame* belongs to** from the events it
   carries — *a bit was written into the second pass exactly when the second pass
   has something in it* — rather than reading the state's current phase.

Deriving the phase then exposed a second bug the first arrangement had hidden: a
lesson that never goes back derived its way into a second pass that never runs, so
`produced` came back empty at completion. `phase` now answers ENCRYPT for the whole
life of a non-round-trip lesson. Two regression tests pin both.

The general lesson, which is not new but keeps being true: **a walkthrough dump is
a test the test suite cannot write.** Caesar's wrap beat lighting the wrong letter
(ADR-046) and Fibonacci's six identical opening screens (ADR-045) were both found
the same way.

### Why an eighth shape

A bitwise operation is **three rows sharing one set of columns**, where the third
is computed from the two above it. `CountingScene` also has three rows and its own
documentation says they deliberately *do not* share columns; `PrefixScene` shares
columns between two rows but offset by one; `CipherScene` has two aligned messages
plus a lookup, and a `CipherPair` holds two letters where a truth-table row holds
three bits.

`DpTableScene` is the near miss and the instructive one. It is `rows × columns` and
this could be squeezed into it — but its two axes are two *quantities* and a cell
is a point in that space, while these rows are three different *things* that happen
to line up. "The key's bit 2" is not a coordinate. It also carries `ItemCard`,
`BagMeter` and a two-sided `ChoiceStrip` that a bitwise lesson would null out,
which is a union pretending to be a record — and the brief was explicit that the
two encryption lessons must not be forced into an abstraction that makes the
architecture worse.

It added no event, no interaction model and no cell state; five `when` sites gained
a branch, and the compiler found the three call sites in `:app` that needed copy.

### The security caveat is a recap bullet, not a footnote

A lesson that leaves a learner thinking they have seen encryption has taught them
something worse than nothing. So the sentence — *XOR is a building block of real
cryptography, but a XOR cipher with a short or reused key is not secure on its
own* — is the **last recap bullet**, where a bullet is read rather than skipped,
and it is repeated on the Complete screen. A test asserts it is present in the
walkthrough.

### Two options, so the budget goes on the feedback

Every other lesson with a value answer offers three options and rotates the correct
one's seat. A bit has two values, so there is no seat to rotate and a guess is a
coin flip — which means the only thing separating a learner who knows the rule from
one who does not is what happens when they are wrong.

There is exactly one wrong answer per column, and which half of the rule it misses
is decided entirely by whether the two bits match. So the feedback names that half:
*"XOR gives 0 when both bits are the same, and these are both 1."* That is the most
useful sentence available at that moment, and it is the whole reason the lesson
works with two buttons.

### The brief's TRY dataset was replaced, and the replacement is asserted

`1011 ⊕ 1101 = 0110` is a correct lesson and a weak one: it produces the **same
ciphertext WATCH produces**, from bit pairs differing in only the last column. A
learner who remembered `0110` could reproduce it without applying the rule once,
which is what ADR-014 says a TRY dataset must not allow.

```
WATCH        (1,1) (0,1) (1,0) (0,0)  ->  0110
brief's TRY  (1,1) (0,1) (1,0) (1,1)  ->  0110    three columns shared, same answer
shipped TRY  (1,0) (0,1) (1,1) (1,0)  ->  1101    one column shared, different answer
```

So the key is **`0110`**, keeping the brief's plaintext. That is the same call
ADR-044 made when 0/1 Knapsack's brief supplied a bag whose optimum both greedy
strategies already found: the numbers *are* the lesson, so a dataset that teaches
the wrong thing gets replaced and the replacement is written down.

The guard is a test rather than a comment — `TRY cannot be answered from memory of
WATCH` asserts the two datasets have the same width, different ciphertexts, and at
most one column in common. A future dataset change that reintroduces the overlap
fails the build.

It was shipped weak first, flagged, and fixed on request. Worth recording that way
round: the flag was what made the fix a one-line decision rather than a discovery.

**Alternatives considered.**
- *Reuse `DpTableScene`.* Rejected above.
- *Extend `CipherScene` with a key row and a truth table.* Rejected: Caesar would
  null both and XOR would null the alphabet, which is two lessons sharing a type
  and no behaviour.
- *Let the app compute some columns.* Rejected: the XOR is the lesson, and there is
  nothing else in it to hand over.
- *Three options per column, for consistency with every other value lesson.*
  Rejected: a bit has two values, and a third option would have to be a non-bit.
- *Teach text-to-binary first, so the message is a word.* Rejected as scope by the
  brief, and rightly: it is a second encoding idea stacked on the operation.
- *Drop the second pass from WATCH to keep it short.* Rejected: the reversal is the
  lesson, and asserting it is weaker than watching it.

---

## ADR-048 — SHA-256 is a hash, so the shelf is renamed and the picture has no way back

**Decision.** SHA-256 Hashing ships **free** as a `LessonPack` with WATCH and TRY.
`Scene` gains a ninth shape, `HashScene`; `Dataset` gains a defaulted `hash`. The
**Encryption category is renamed Cryptography**. The app hashes; the learner makes
five judgements about what hashing guarantees. Full detail: `docs/sha256-hashing.md`.

⚠ **Product-owner request**, 2026-09-16: a free beginner lesson teaching hashing,
explicitly not described as encryption, and explicitly not teaching the 64
compression rounds.

### The category is renamed, because the old name was about to become a lie

`Encryption` held two ciphers and was accurate. Adding a hash function to it would
have put SHA-256 under a chip, a card badge and a filter reading *Encryption* — the
app asserting on the Home screen the exact thing the lesson exists to correct,
before the learner has opened anything.

The brief offered *"Encryption / Cryptography"*, and Cryptography is the name that
is true of all three. It cost one string in `algorithmCategories`, three entries and
four test assertions; access is unchanged, because access derives from *not being
Advanced* (ADR-032, ADR-041) and the new name is not the Pro one either. This is the
second time that rule has been exercised in the free direction and it again cost
nothing — no flag, no exclusion list, no billing change.

### Why a ninth shape

ADR-036 and ADR-045 each refused one, so this has to clear the same bar: a shape is
for a new *kind* of data.

A hash is an input of any size, a fixed-size output, and **no positional
relationship whatsoever between them** — and that last clause is the lesson, so a
shape that implies one is disqualified rather than merely imperfect:

- `CipherScene` is the near miss by subject and the furthest by shape. Its two
  messages are the same length and aligned position by position, because Caesar
  letter 3 became ciphertext letter 3. Hashing `hi` gives 64 characters, and digest
  character 3 came from the whole message;
- `BitwiseScene` shares one set of columns across its rows, which is the same
  assertion in stronger form;
- `SequenceScene` is one row whose slots are positions — digest position 7 is not a
  concept this lesson has;
- `BucketScene` is the interesting one, because it already carries a *hash flow*:
  `GET 12 → HASH 12 % 5 → BUCKET ?`. But its flow ends in an index into a five-row
  table, and the table is that lesson. There is no bucket, no key and no value here;
- `CountingScene`, `PrefixScene` and `DpTableScene` are grids of `Cell`s. A
  64-character digest chopped into cells would be sixty-four boxes asserting
  sixty-four meanings.

What the shape actually holds is a labelled pipeline, a digest as *text*, and rows
to compare — because comparison is the only way the properties can be shown at all.
Fixed length is two inputs of different lengths with the same output length;
determinism is one message hashed twice; the avalanche is one character changed.
Each is a *relationship between rows*, so the rows are scene data rather than
something the copy describes.

It added no event, no interaction model and no cell state; five `when` sites gained
a branch, and the compiler found all five.

### The middle box stays a box, and that is the lesson's honesty

Real SHA-256 pads the message, builds a 64-entry schedule and runs 64 compression
rounds over eight working variables. **None of it is drawn and none of it is
faked.** Animating sixty-four rounds would bury a beginner; animating invented ones
would teach something false about a real algorithm, which is worse than teaching
less. The box is labelled `64 compression rounds` and the copy says the lesson is
not about them.

The same honesty governs the complexity claim: O(n) is stated as a *high-level*
figure, with the 512-bit blocking that produces it and the fixed per-block work
named rather than hidden behind it.

### The decisions are concept judgements, and that is new

Every other lesson asks the learner to execute a step of the algorithm. Nobody
executes a step of SHA-256 by hand, and the brief rightly forbade pretending
otherwise — so the five judgements are about the *properties*.

The risk in that is obvious and is the one ADR-034 named when it refused a row of
neighbour buttons: **TRY must not become a multiple-choice quiz.** What keeps it
from being one is *where the answer comes from*. Every message is hashed first, by
the app, as `Probe.Mechanical` — computing a digest is arithmetic and
PRODUCT_SPEC.md §3 gives the app the arithmetic. Only then is a judgement asked, and
the projector puts the two or three rows that answer it on screen. The learner reads
the evidence, exactly as the XOR lesson has them read its truth table; the copy
never asks anyone to recall something it is showing them.

It is worth recording that this is a genuine stretch of the interaction model rather
than a comfortable fit, and that the mitigation is the evidence rather than the
question format.

### Long statements live on cards, not on buttons

A `DecisionButton` is one line at `labelLarge`; "SHA-256 output length depends on the
input length" is not. Two Pointers hit the same wall and solved it the same way
(ADR-032): the meaning goes where it teaches rather than where it wraps. So the full
statement sits in a card — `DpTableScene`'s choice strip, reused — and the button
carries one short word, printed on the card too so the pairing cannot be misread.
Neither card is styled as the true one, and the scene does not carry which is, so the
renderer could not leak it even by accident.

The correct answer is deliberately **not always in the same seat**. A learner who
noticed the first button was always right would finish the stage without reading
anything; a test asserts both seats are used.

### `MessageDigest`, and the first `java.*` import in `:engine`

Every other transform in the engine is hand-written because the transform *is* the
lesson — `xorBit` is an inequality because *the result is 1 when the bits differ* is
the sentence being taught. SHA-256's internals are explicitly not the lesson, and a
hand-rolled copy of them would be a second, unreviewed cryptographic implementation
whose only job is to agree with the platform's.

The boundary ARCHITECTURE.md §3 enforces is *no Android and no Compose*, so a lesson
cannot reach a `@Composable`. `java.security` is neither; the module is still a pure
JVM module and its tests still run in milliseconds with no Robolectric.

**No digest is authored anywhere.** Every value on screen is computed from a message
string at run time — ADR-045's rule for Fibonacci's call counts — and the tests pin
them against values produced independently by `sha256sum` and `openssl dgst
-sha256`, so the engine is checked against the outside world rather than against
itself. The avalanche beat's "61 of 64" is computed the same way.

### The caption was one frame ahead of the picture

`WatchScriptBuilder` hands the narrator the scene projected from the state **after**
the transition, so the frame that answers question *n* is drawn showing question
*n + 1*'s evidence. The first draft captioned each frame with the question it had
just settled, which put the fixed-length sentence over the determinism rows and the
determinism sentence over the avalanche rows, all the way down. Every test passed.

It was found by dumping the walkthrough and reading it. That is now three lessons in
a row — Caesar's wrap beat lighting the wrong letter (ADR-046), Fibonacci's six
identical opening screens (ADR-045), XOR's relabelled final frame (ADR-047) — and
the general lesson keeps being true: **a walkthrough dump is a test the test suite
cannot write.**

The fix is that a frame is captioned by `frame.state.question`, never by what it just
answered, and a new test asserts each property beat's scene actually contains that
property's evidence.

### The two caveats are recap bullets

*Hashing is not encryption*, and *SHA-256 alone is not how passwords are stored* —
last, where a bullet is read rather than skipped, and repeated on the Complete
screen. That is ADR-047's placement for the XOR security caveat, for the same
reason. The password bullet names Argon2, bcrypt and scrypt rather than leaving the
learner with a correction and no alternative.

The one-way copy says **"designed to be computationally infeasible to reverse from
the hash alone"** and never "impossible". The difference is why salting and rainbow
tables exist, and a lesson that overstated it would be teaching a beginner a
sentence they would later have to unlearn.

**Alternatives considered.**
- *Force it into `CipherScene` or `BitwiseScene`.* Rejected above — both assert a
  positional relationship between input and output, which is the one thing false.
- *Keep the category called Encryption.* Rejected: it files the lesson under the word
  the lesson exists to correct, in a badge, on Home.
- *Visualise the 64 compression rounds.* Rejected by the brief and on its merits.
- *Fake a few representative rounds.* Rejected harder: inventing steps of a real
  algorithm is worse than showing none.
- *Hand-write SHA-256 in the engine.* Rejected — a second cryptographic
  implementation to review, for no teaching gain.
- *Hardcode the digests.* Rejected: it is a second source of truth for the one value
  the lesson is about, and it goes stale silently.
- *Free-form text input for a "Hash It" demo.* Deferred: the mechanical hashing beats
  already hash real strings and show real digests, and a text field adds an input
  surface, a keyboard and validation for one beat (brief §9 permits predefined
  messages).
- *Put the lesson before Caesar and XOR.* Rejected: "there is no way back" only reads
  as a distinction once the learner has watched two messages be turned back.
- *Ask the learner to compute a digest.* Rejected by the brief, and rightly — it is
  the gesture-teaching trap over arithmetic nobody can check by hand.

---

## ADR-049 — AES is Pro without being Advanced, and the State is drawn for real

**Decision.** AES ships as a **Pro** `LessonPack` with WATCH and TRY, filed under
**Cryptography**. `Scene` gains a tenth shape, `BlockCipherScene`; `Dataset` gains a
defaulted `aes`. `ProAccess` gains a second, narrow rule so that a lesson can be Pro
without being on the Advanced shelf. Full detail: `docs/aes.md`.

⚠ **Product-owner decision**, 2026-09-17: AES as a paywalled lesson on the
Cryptography shelf, taught conceptually rather than by hand.

### The category and the price came apart, and that is new

Every paid lesson until now was paid **because of its shelf**. ADR-032 established
that Advanced is a category rather than a second taxonomy, ADR-041 made that shelf
the Pro shelf, and the rule was one line with nothing to remember.

AES breaks the coincidence. It is a block cipher, so its category is Cryptography —
filing it under "Advanced" would print the wrong word on its card, on Home, in the
chip row and in the filter, which is precisely the objection ADR-048 raised when a
hash function was about to be filed under "Encryption". But it is also the one lesson
on that shelf worth paying for, and the brief asked for it to be Pro.

So `requiresPro` now reads two rules in one breath:

```kotlin
fun requiresPro(category: String, id: AlgorithmId): Boolean =
    category == PRO_CATEGORY || id in PRO_LESSONS
```

**This is not the flag ADR-032 refused**, and the difference is where it lives. That
ADR rejected an `isPro` field on `AlgorithmEntry` — a second place access is
decided, sitting beside the category, drifting out of step with it. `PRO_LESSONS` is
a set of ids **inside the one object that answers the question**, read by the one
function every caller already goes through. There is still exactly one access rule
and exactly one call site.

Three things keep it from becoming the thing it is not:

1. **Both arguments are required.** A caller that knows only the category cannot
   answer the question at all, so "forgot to pass the id" is a compile error rather
   than a silently wrong `false`. That is the standard ADR-021 set for decision
   validation — structural, not a convention someone has to remember.
2. **Rule 1 is untouched**, so the failure ADR-032 actually named — an Advanced
   lesson that is accidentally free — remains impossible. A test asserts the whole
   Advanced shelf is still Pro.
3. **The set is meant to stay tiny.** A long list would mean the categories had
   stopped describing the library, and the fix then is the categories.

**Nothing else in billing moved.** `SubscriptionRepository`, `PlayBillingGateway`,
`ProEntitlement` and the paywall are untouched; `ProEntitlement.Unknown` is still not
entitled; no boolean is persisted. What did change is that the paywall's "twelve
advanced lessons" is now **counted from the library** — it already said "eleven"
while the shelf held twelve, which is what a hardcoded number does eventually.

**Alternatives considered.**
- *File AES under Advanced.* Rejected above: it is the architecture's own answer and
  it costs the card its true category. Recorded because it is the cheaper option and
  a reasonable person would take it.
- *An `isPro` flag on `AlgorithmEntry`.* Rejected — ADR-032's parallel axis, and the
  one this decision was careful not to become.
- *A "Cryptography — Advanced" category.* Rejected: a category that encodes a price
  in its name is the same flag wearing a chip.
- *Make Pro a set of categories and add Cryptography.* Rejected outright — it would
  lock Caesar, XOR and SHA-256, which are free and must stay free.

### The State is computed, because here the steps *are* the lesson

ADR-048 refused to draw SHA-256's 64 compression rounds: the internals are not that
lesson, and **drawing invented ones would teach something false about a real
algorithm**. That rule points the other way here. AES's picture *is* the State
changing, so every byte drawn has to be the byte AES really produces — and a platform
`Cipher` hands back a finished ciphertext with no way to ask what the State looked
like after ShiftRows in round 3.

So `engine/core/Aes.kt` implements the transformations and the key schedule. What
keeps that honest rather than merely confident is that it is checked against things
outside itself: the **S-box regenerated from its mathematical definition**, **FIPS-197
Appendix C.1**, **FIPS-197 Appendix B including its round-by-round States**, and the
**JDK's own AES** for all three variants. The intermediate assertion is the one that
matters — a cipher can reach the right ciphertext through wrong, self-cancelling
steps, and this lesson draws the steps.

No ciphertext, State or round key is authored anywhere; every value is computed from
the plaintext and key. That is ADR-045's rule for Fibonacci's call counts applied to
the one lesson where a stale hardcoded value would be invisible.

### The learner explains the cipher back rather than running it

Nobody performs a MixColumns by hand. PRODUCT_SPEC.md §3 gives the app the arithmetic,
so the app encrypts and the six exercises are about what the run *means* — the block
size, the State, the order of a round, what the last round leaves out, the variants,
and what makes the round keys.

Two of them are answered by **tapping the round strip**, not by picking a word.
Practically, four buttons reading SubBytes / ShiftRows / MixColumns / AddRoundKey do
not fit one row at `labelLarge` — the wall ADR-037 hit with AVL's case names. But the
real reason is ADR-034's: the round's steps are already on screen, and pointing at the
next one is what understanding an order looks like.

This is the same stretch of the interaction model ADR-048 recorded, and it is named as
one: TRY must not become a quiz, and what keeps it from being one is that every
question is asked with the run drawn and the evidence in the picture.

### Why a tenth shape

The bar every shape has cleared and that ADR-036 and ADR-045 each refused: a new
*kind* of data. The State is a 4 × 4 grid whose **rows and columns are each named by
a transformation that acts on them** — ShiftRows moves along rows, MixColumns mixes
down columns. No existing shape has an axis an operation is named after.

`DpTableScene` is the near miss and the instructive one. It is genuinely rows ×
columns — but its axes are two *quantities* and a cell is a point in that space, while
the State's axes are a byte's position in a block and the grid is the same sixteen
bytes rearranged. It also carries item cards, a bag meter and a two-sided choice strip
a cipher would null out, which is the union-pretending-to-be-a-record ADR-047 refused.
`SequenceScene.GRID` is a *wrap* with no meaning in which line a box lands on, which
is the thing that is false here.

It added no event, no interaction model and no cell state; the bytes are ordinary
`Cell`s carrying a hex label, drawn by the `SceneCell` every other lesson uses.

### The hand-over step, and the bug that earned it

A frame is drawn from the state **after** its transition, so the frame that applies
the last step of the run is also the frame the first TRY question is pending on. They
are the same state, and nothing can derive them apart. The closing beat about
decryption was therefore drawn with the first question's evidence over it.

That is ADR-047's XOR bug and ADR-048's SHA-256 bug arriving through a third door, and
it was found the same way — by dumping the walkthrough and reading it. The fix is an
inert `AesStepKind.READY`: the run ends on a frame where nothing happens, so the
collision lands somewhere with nothing to collide. **An inert step is a strange thing
to add and it earns its place**: the alternative is moving the narration one frame
earlier, which is what ADR-048 had to do, and which costs a beat its own picture.

**Four lessons in a row now.** The general lesson is no longer a surprise and should
be treated as procedure: *dump the walkthrough and read it before calling a lesson
finished.*

### The three things the copy will not say

- never **"unbreakable"**, and never "impossible to break". What is true is that no
  practical attack is known that beats trying every key, and that is what the recap
  says. The word appears exactly once, inside the sentence that denies it, because a
  learner who meets the myth elsewhere is better served having already been warned —
  and a test asserts that single negated use;
- never a suggestion that encrypting a message means encrypting its blocks. A block
  cipher is a primitive; the recap names **AES-GCM**, and **ECB** appears once, as the
  thing not to reach for;
- never that AES alone keeps anything safe. Key management, a sound mode and the
  protocol around it are what do that.

All three are asserted against the resolved copy rather than trusted to review.

**Alternatives considered.**
- *Hand-wave the State with illustrative bytes.* Rejected — ADR-048's rule, and the
  worst available option: a picture of AES that is not AES.
- *Use the platform `Cipher` and draw only input and output.* Rejected: that is a
  lesson about a black box, and the 4 × 4 State is what the brief asked to teach.
- *Animate all ten rounds.* Rejected: forty-three beats of the same four steps. Round
  1 and the final round are narrated in full and the middle is one beat (ADR-025).
- *Ask the learner for a byte.* Rejected: a table lookup and GF(2⁸) multiplication are
  arithmetic they cannot check, which is the gesture-teaching trap PRODUCT_SPEC.md §3
  names.
- *A live AES-GCM demo.* Deferred: the lesson already performs real AES with a real
  key and a verified ciphertext, and a second demonstration would add an input
  surface and a nonce discussion for one beat.

---

## ADR-050 — RSA: the chain is the lesson, so the questions are asked inside it

**Decision.** RSA ships as a **Pro** `LessonPack` with WATCH and TRY, filed under
**Cryptography**. `Scene` gains an eleventh shape, `KeyPairScene`; `Dataset` gains a
defaulted `rsa`. Access needed **no change** — one id added to the set ADR-049
created. Full detail: `docs/rsa.md`.

⚠ **Product-owner decision**, 2026-09-17: RSA as a paywalled lesson on the
Cryptography shelf, taught on a small verified example.

### The questions are interleaved, and that is the whole design

SHA-256 and AES run their algorithm and *then* ask about it (ADR-048, ADR-049),
because their judgements are about the run as a whole — what a hash guarantees, what
shape a round has. That pattern is wrong here and it took drawing the lesson to see
why.

RSA's judgements are about **links in a chain**: `n` from `p` and `q`, `φ(n)` from
the same two, `d` from `e` and `φ(n)`. Run the chain to completion first and every
value is on screen when the learner is asked for it — which is not a question, it is
a reading exercise. So every value is asked **at the point it would be computed**,
with the values it depends on already drawn and its own place showing `?`.

That is the hash flow's rule (ADR-030) applied to a dependency chain rather than to a
single answer, and it settles the shape of the state machine: one cursor over a fixed
list of beats, where a beat either states something or asks for something and cannot
be passed without being answered.

**It also comes with a free property.** The collision ADR-049 had to fence — the
frame that ends the run is also the frame the first question is pending on — cannot
arise, because here the pending question is always about the *next* value, which is
correctly drawn as unknown. No inert hand-over step was needed.

### …except for the two judgements that settle nothing

Eight of the ten questions settle a value, so their frame shows that value landing.
Two do not: *which kind of cryptography is this* and *which key stays secret* produce
no number, and the picture they make is the four choice cards themselves — which
appear on the frame where the question is **pending**, one beat before it is answered.

The first draft captioned them where they were answered, so the secrecy cards were
drawn under the round-trip sentence and the beat that was actually about them showed
nothing. Every test passed. It was found by dumping the walkthrough and reading it,
which is now **five lessons in a row** and should be treated as procedure rather than
as a discovery each time.

The fix is ADR-048's rule — *a frame is captioned by what its own scene shows* — so
those two are captioned where their cards appear. Each needs the step before it to
have no story of its own, and both already did: `SETUP` is covered by `opening()`, and
`ROUND_TRIP` draws exactly what the `DECRYPT` beat before it drew. So unlike AES, no
inert step had to be invented; two existing seams were already there.

### Why an eleventh shape

The bar every shape has cleared and that ADR-036 and ADR-045 each refused: a new
*kind* of data. A **derivation chain** — named scalars, each produced from earlier
ones by a printed formula, all staying on screen because later ones read them — is
not something any existing shape holds:

- `SequenceScene`'s slots are **positions**, and its `equation` is one
  `PrefixEquation`: a single two-operand line with `+` or `−`. This needs six
  different formulas, two of which (`mod`, exponentiation) it cannot express;
- `BlockCipherScene` carries a key schedule, which is the near miss — but that is
  *one* key expanded into many of the same kind, drawn as hex beside a 4 × 4 State.
  RSA's two keys are **different kinds with opposite rules**, and saying which is
  which is half of what asymmetric means. Its State grid, round strip and variant
  table would all be null here;
- `HashScene` has a pipeline, but a stage is a box the data passes *through* and
  carries no value of its own. Every step here **is** a value that stays;
- `CipherScene` and `BitwiseScene` align rows position by position, and
  `DpTableScene`, `CountingScene` and `PrefixScene` are grids indexed by quantities.
  There is no second axis here at all.

It added no event, no cell state and no interaction model.

### The access rule did not move, and that is the result worth recording

ADR-049 added `PRO_LESSONS` for AES, with a warning attached: *keep this small; a long
list means the categories have stopped describing the library.* RSA is the first test
of that, and it cost **one line**:

```kotlin
val PRO_LESSONS: Set<AlgorithmId> = setOf(AlgorithmId.AES, AlgorithmId.RSA)
```

Two entries, both on one shelf, both for the same reason — a real cipher rather than a
teaching device — is the rule describing the library rather than fighting it. Rule 1
is untouched, so the Advanced shelf is still wholly Pro, and a test asserts both
halves. `SubscriptionRepository`, `PlayBillingGateway`, `ProEntitlement`,
`ProAccess.decide` and `PaywallScreen` were not touched at all; the paywall's count is
computed from the library and says "fourteen" by itself.

### TRY gets its own key pair, against the brief

The brief lists the TRY exercises using WATCH's numbers. Six of the ten judgements
would then be answerable from memory — a learner who watched `n = 55` land does not
have to multiply anything to answer it again — which is precisely what ADR-014
forbids.

So the **questions** are the brief's ten, unchanged and in its order, and the
**numbers** are new: `p = 7, q = 13, e = 5`, giving `n = 91`, `φ(n) = 72`, `d = 29`,
`c = 23`. The message stays `4`, because it is the one value the learner is not asked
to derive and holding it still makes the two runs comparable.

This is the third time a brief-supplied dataset has been replaced for teaching the
wrong thing — ADR-044 for 0/1 Knapsack's bag, ADR-047 for XOR's key — and the rule
those two set applies unchanged: **the numbers are the lesson, so a dataset that
teaches the wrong thing gets replaced and the replacement is written down.**

### The arithmetic is written out, and checked against the JDK

`Sha256` delegates because its internals are not that lesson; RSA's five lines *are*
the lesson, so `engine/core/Rsa.kt` implements them. It is also not a choice: the
platform's RSA will not touch a toy modulus, since `KeyFactory` rejects anything under
512 bits — which is itself part of what the lesson says.

What keeps it honest is that there is no NIST vector for a 55 modulus, so the engine
is checked against `java.math.BigInteger` over thousands of random values, against
every message under `n` round-tripping, and against **a real 2048-bit key pair** from
`KeyPairGenerator` — which turns the lesson's central claim, *this is the same
arithmetic with bigger numbers*, into something the suite checks rather than something
the copy asserts.

### What the copy will not say

- never **"unbreakable"**, and never that the numbers here are secure. `n = 55`
  factors by inspection, and the lesson says so on the picture for its whole length,
  in a beat of its own, and on the Complete screen;
- never textbook RSA as a recipe. It is deterministic and unpadded; the recap names
  **OAEP**, 2048-bit keys, and reaching for a reviewed library rather than writing any
  of it yourself;
- never "anyone can encrypt and only you can ever decrypt" as an unqualified promise.
  What is true is narrower and is what the copy says: the private key is what undoes
  the public key's work, and it is the half that is kept.

All three are asserted against the resolved copy rather than trusted to review.

**Alternatives considered.**
- *Ask the ten questions after the chain, as AES does.* Rejected above — it turns six
  of them into reading exercises.
- *Reuse `BlockCipherScene`'s key schedule.* Rejected: one key expanded is not two
  keys with opposite rules, and a cipher would null out most of it.
- *Four buttons for "Asymmetric cryptography".* Rejected on width, the wall ADR-032
  and ADR-037 each hit — and stacked cards are the better interaction anyway.
- *Ask the learner to compute `9²⁷ mod 55`.* Rejected: PRODUCT_SPEC.md §3 gives the
  app the arithmetic, and this is arithmetic nobody can check by hand.
- *A live `javax.crypto` demonstration.* Deferred: it cannot use the lesson's key at
  all, so it would be a separate 2048-bit key pair — a different thing from the
  lesson. The test suite generates one, which is where that belongs.
- *File RSA under Advanced.* Rejected for ADR-049's reason: a category is a statement
  about what a lesson *is*.

---

## ADR-051 — Pro is a one-time purchase, and the gateway's rules move out of the gateway

**Decision.** AlgoKing Pro is sold as a **one-time product** — `algoking_pro`, bought
through purchase option **`buy`** — and no longer as a subscription. `PlayBillingGateway`
queries and restores it as `ProductType.INAPP`, launches the flow against that option's
offer token, acknowledges the receipt and **never consumes it**. The two decisions inside
the gateway that are rules rather than plumbing move to a new pure `BillingRules`. Nothing
else about access, the paywall or ads changed. Full detail: `docs/pro-access.md`.

⚠ **Product-owner decision**, 2026-09-18: the product exists in Play Console as a one-time
product with purchase option `buy`, and the app is to sell exactly that.

### Why the type change is not a small one

A subscription and a one-time product are different Play APIs end to end, and the failure
mode is silent: `queryProductDetailsAsync` for a one-time id **as `SUBS` returns nothing at
all**, and so does `queryPurchasesAsync`. An app left on the old type does not crash and
does not warn — it simply reports "the product could not be loaded" forever, and a learner
who already owns Pro is never restored. So this is not a flag: it is the product type on
both queries, the offer the flow is launched against, and what a receipt is allowed to mean.

**Two things did not change, and they are the two that carry the risk.** Entitlement still
comes only from `queryPurchasesAsync` and is never inferred from a purchase flow's own
report of success (ADR-041), and it is still never written to disk — a refund has to be able
to take Pro away, which is exactly what makes a cached `isPro` boolean the wrong shape here
even for a permanent unlock.

### The purchase is acknowledged and never consumed

The one new way to get this wrong. Consuming a one-time product tells Play the learner has
used it up and may buy it again — so a consumed Pro unlock would be **re-sold on the next
reinstall**, to someone who already paid. Acknowledgement is still required within three
days or Play refunds it. Neither has an in-app symptom on the day it is written, so
`BillingRulesTest` reads the gateway's source and fails if `consumeAsync` or `ConsumeParams`
ever appears in it, the same call ADR-042's ad-id tests made about `build.gradle.kts`.

### `PENDING` became its own outcome

It was reported as `Failed("payment is still pending")`, which put *"That did not go
through"* on the screen of someone whose cash payment is about to clear. A pending payment
is neither a failure nor a success, and on an India-weighted audience (PRODUCT_SPEC.md §16)
it is not a rare path — so `PurchaseOutcome.Pending` says so plainly, unlocks nothing, and
the receipt becomes entitling if and when Play reports `PURCHASED`.

### The rules left the gateway, because the gateway cannot be run

`PlayBillingGateway` needs a store, a device and a signed build, so every rule inside it was
untestable by construction. Two of them are not plumbing at all:

- **which receipt entitles a learner** — `PURCHASED` and naming this product, and nothing
  else, with `PENDING` and `UNSPECIFIED` explicitly not entitling;
- **which purchase option is sold** — `buy`, or a legacy single unnamed offer, and
  otherwise nothing.

They now live in `BillingRules` over `Receipt` and `PurchaseOption`, this app's own types,
which the gateway maps Play's classes into at its boundary. That is ADR-008's call for the
ad rules applied to the billing ones, and it is what makes "a pending payment grants
nothing" a test that runs in milliseconds on a laptop instead of a thing someone has to
remember while reading a callback.

**A near miss is refused rather than substituted.** If Play returns options and none is
`buy`, the paywall reports that nothing can be sold. Quietly charging for a different
purchase option than the app was built against — a rental, an upgrade at another price — is
worse than not selling, because the learner is charged for it.

### The paywall shows Play's price and closes on entitlement

`formattedPrice` is Google Play's own localised string, passed through untouched; there is
still no price, currency or amount written anywhere in the app, and a test asserts that
across the whole billing package and the paywall. What was `ProProduct.billingPeriod` is now
`priceDetail` and reads *one-time purchase*: a field of the old name holding that value is
the sort of small untruth that eventually persuades someone to put a renewal date on a
screen that has none.

The paywall now closes **on entitlement rather than on the purchase call returning** — so it
also closes for a restore, for a pending payment that clears, and for the query that runs at
startup arriving after the learner has already tapped a locked lesson. One trigger, and it
is still the store's answer.

**Alternatives considered.**
- *Keep `SUBS` and add an `INAPP` path beside it.* Rejected: two product models to keep in
  step for a product that is one of them, and the dead half is the one that silently returns
  nothing.
- *Select the first purchase option when `buy` is missing.* Rejected above.
- *Treat `PENDING` as entitling and reconcile later.* Rejected: it gives Pro away for a
  payment that may never clear, and the reconciliation is a refund the learner experiences
  as the app taking something back.
- *Consume the purchase so it can be re-bought.* Rejected — it is a permanent unlock, and
  this is how one gets sold twice.
- *Cache the entitlement so a Pro learner is never briefly shown the paywall.* Rejected
  (ADR-041): a cached boolean survives a refund, and the real fix was the paywall closing
  the moment the store answers.
- *Rename `SubscriptionRepository`.* Not done. It is one rename away from being accurate and
  it touches the one call site, but it is churn in the middle of a product-type change;
  noted here so the next person knows it is deliberate rather than missed.

---

## ADR-052 — RSA tells the story first, and explains the keys second

**Decision.** The RSA lesson runs in **two acts**. Act I shows a message being encrypted with a
public key and decrypted with a private one, with the two keys handed over as `(3, 55)` and
`(27, 55)` — things that exist and have opposite jobs. Act II then answers the question Act I
leaves standing: *where did those two pairs come from?* — and only there does `p`, `q`, `φ(n)`,
`e` or `d` appear. The engine, the toy key pair, the arithmetic, the Pro gate and the TRY
architecture are unchanged; what moved is the order, the picture, and four new judgements.

⚠ **Product-owner decision**, 2026-09-21: the lesson introduced key generation before the
learner knew what RSA was for, and is to be re-ordered story-first.

### What was wrong with the old order

The first thing the old lesson did after its opening was ask for `n = p × q`, and its fourth
beat asked for `φ(n)`. Every one of those numbers is correct, derived at the point it is
computed, and drawn with a `?` until it is settled — ADR-050 got all of that right. What it got
wrong is that **a learner on beat four cannot yet say what RSA does**, and a totient without a
purpose is a fact to be memorised rather than a step in an argument. The lesson reached
`4 → 9 → 4` on beat twelve, which is where its point lives.

The test that now guards this is `the story is asked before the arithmetic`: every judgement
answerable with no arithmetic comes before every judgement that needs some.

### The story half is asked, not told

Four judgements are new — which key may be shared, what the public key does to a message, what
the private key does to a ciphertext, and what the pair is therefore *for*. They could all have
been statements, because the answer is on the card in front of the learner. They are questions
because a learner who taps *"the public key"* has committed to something, and PRODUCT_SPEC.md
§5's ladder then has something to teach against when they do not. A story told at someone is a
video, which is the one thing this app is not (ADR-020).

That makes six card judgements where there were two, and it is what forced the narrator change
below.

### `PUBLIC_KEY` and `PRIVATE_KEY` became statements, and that is forced

Both pairs are on screen from the beat that hands them over, so asking *"which pair is the
public key?"* in Act II is asking a learner to read a card. They were dropped from the asked
list — **not deleted**: the questions, their four distractors each, and all their copy are
untouched, `stepsFor` turns any unasked question into a statement, and a test builds a dataset
that asks them both and checks they still work. The two exponents behind them, `e` and `d`, are
still asked, and they are the part nobody could have read off the screen.

### Act I draws a flow; Act II draws the chain

The derivation chain is **empty for the whole of Act I**. The alternative — five rows of `?` —
was tried first and is worse than it sounds: it puts every symbol the re-ordering exists to
delay on the very first screen, wearing a question mark. So `KeyPairScene` gained two defaulted
fields, the move every scene addition in this project has made (ADR-037, ADR-039):

- **`flow`** — `message → public key → encrypt → ciphertext`, and the same shape backwards. A
  node whose value the lesson has not produced is null and drawn `?`, which is the chain's own
  rule applied to a flow, and it is what makes *"what does the public key do to this?"* askable:
  the verb node is blank while the learner is choosing which verb it is.
- **`maths`** — the worked arithmetic beside it, whose **last line is withheld while that value
  is the question**. `c = 4³ mod 55` while asking; `c = 64 mod 55` and `c = 9` once settled.
  That `64 mod 55` line is the one that makes `mod` mean something rather than being a symbol,
  and it is deliberately not shown early, because anyone who can subtract would read the answer
  off it.

They are never on screen together, because they answer two different questions and the lesson
only asks the second once the learner can answer the first.

**The flow turns round one beat before the arithmetic does.** On the beat that pauses on the
ciphertext, the flow already shows `9 → private key → ?` — because that is the picture the next
judgement is about — while the panel still shows how `9` was produced. Turning both at once
wipes `c = 9` one beat after earning it.

### The narrator now captions two things at once

A frame is drawn from the state *after* its transition, so the frame that passes a beat is also
the frame the next question is pending on. For a card judgement that means the cards land on the
previous beat's frame. ADR-050 handled this by sacrificing the landed beat's caption, which was
affordable when the two beats before a card question had nothing of their own to say.

With six card judgements, all six of those beats do. So a frame with cards on it is now
captioned by **both** — the landed beat's headline, and a support line introducing the choice
underneath. That is not a compromise: it is what the frame honestly shows, and it is ADR-048's
rule (*a frame is captioned by what its own scene shows*) applied to a scene showing two things.
Each beat keeps its own two-sentence caption for the statement path, and a test drives a dataset
that asks nothing to prove those captions are correct rather than dead.

### What did not change

The toy key pair, `Rsa`, `RsaProblem`, every `BigInteger` and 2048-bit check in `RsaMathTest`,
`ProAccess`, `PlayBillingGateway`, the paywall, the one interstitial, `ProgressRepository`, the
`LessonPack` shape, `LessonController`, and every other lesson. The diff touches RSA's four
engine files, its scene, its copy, its renderer and its two test files.

WATCH went from 16 beats to 24. That is the cost of telling the story and then explaining it,
and every added beat is a real visual change rather than a pause — the round trip, the two
operation judgements, the pause on the ciphertext, and the beat that asks where the keys came
from. The bound in `WATCH is long enough to teach and short enough to finish` moved with it.

**Alternatives considered.**
- *Keep the order and rewrite the copy.* Rejected. The complaint is not that `φ(n) = 40` is
  badly worded; it is that it arrives before the learner has a reason to care.
- *Split into two lessons — "RSA" and "RSA key generation".* Rejected: the round trip only means
  something once you know one key undoes the other, and key generation only means something once
  you have seen the round trip. Splitting them puts a paywall-shaped gap in the middle of one
  idea.
- *Show the chain greyed out through Act I so the learner knows it is coming.* Rejected above —
  it is the thing being fixed, with a question mark on it.
- *Drop `PUBLIC_KEY`/`PRIVATE_KEY` entirely.* Rejected: they cost nothing to keep, they are the
  right questions for a dataset that does not hand the keys over first, and deleting working
  authored copy to make a list shorter is not a saving.
- *Ask both operation judgements before either formula, as §13 of the brief lists them.*
  Rejected: §1 of the same brief interleaves them — story, then the mathematics behind **that**
  step — and §16's acceptance criteria list the interleaved order. Interleaving also keeps WATCH
  and TRY on one script, which `WATCH and TRY ask the same exercises in the same order` pins.


---

## ADR-053 — 0/1 Knapsack poses the problem before it draws the table

**Decision.** The 0/1 Knapsack lesson runs in **two acts**. Act I has no table in it
at all: a bag, four things, the fact that they will not all fit, the 0/1 rule, a bag
packed by hand for 13, a better one worth 14, the size of the brute force, and the
TAKE-or-SKIP fork. Act II then builds `dp[i][c]` as the tool that answers the fork
reliably. The recurrence, the walk back, the scene shape, the Pro gate and the
`LessonPack` are unchanged; what moved is the order, the picture before the table,
and how much of the table the learner fills.

⚠ **Product-owner decision**, 2026-09-22: the lesson was *"look at this table and
understand it"*, and is to be re-ordered problem-first.

### What was wrong with the old order

The old lesson spent four beats on the problem and sixteen inside the table. Those
four beats were correct and none of them was a question: the items, the 0/1 rule,
a sentence asserting that greedy loses, and `2ⁿ`. Then `dp[i][c]` was defined and the
learner filled cells.

The complaint is not that any of it was false. It is that **a learner on beat five
cannot yet say what problem the table is for**, and a cell defined before the problem
is a definition to be memorised rather than a step in an argument. Every question the
old lesson asked was about the table; none was about the bag.

The test that now guards this is `the story is asked before the table`: every
question answerable with no table comes before every question about one.

### The refutation is performed, not asserted

The old lesson's third beat said, in copy, that taking the most valuable item first
loses. Act I makes the learner do it instead: pack the most valuable thing, find the
one item that still fits, and then meet a second full bag with **both totals hidden**
and pick the larger. Adding `10 + 3` against `8 + 6` is arithmetic a beginner can do,
and being wrong about it is the moment the method becomes worth having.

That is four new judgements — can you take all of it, how many times can one item go
in, what else still fits, which bag is worth more. All four could have been
statements. They are questions because a learner who taps *"Camera + Watch"* has
committed to something, and PRODUCT_SPEC.md §5's ladder then has something to teach
against — the argument ADR-052 made for RSA, applied to a bag.

**The 0/1 question is the one that earns its place most cheaply.** Its two wrong
answers are the two *other* knapsacks: "as many as fit" is the unbounded problem and
"any fraction of it" is the fractional one. Both are named in the why-wrong copy, so
the learner is told what this lesson is not at the point where the name would
otherwise be trivia. The old lesson never mentioned either.

### The brief's dataset was rejected once, and one number brings it back

The product owner specified Laptop 3/8 · Headphones 2/5 · Camera 4/10 · Watch 1/3 at
capacity 5. ADR-044 had already rejected that exact set, and the reason survives:
it has **two** optimal bags worth 13, `Laptop + Headphones` and `Camera + Watch`.
They are disjoint, so whichever item goes in the last row, `dp[n][5]` is a tie — the
final and most important question of the lesson would mark a learner wrong on a
convention. There is no row order that avoids it; the tie is a property of the bag,
not of the layout.

**Headphones 5 → 6 fixes all of it**, and nothing else changes: the capacity, the
four names, the Laptop, the Camera and the Watch are the brief's. The optimum becomes
14 and unique, no cell in the table ties, and taking the most valuable item first now
genuinely loses — which is what §6 of the brief wanted and what its own numbers could
not deliver, since `Camera + Watch` also reached 13.

Every one of those properties is a test over a brute-force enumerator rather than a
claim in a comment, and there are six of them, because Act I asks four questions that
only have single answers if the data cooperates.

### The learner fills five cells of thirty

The old rule asked about every cell where the item fitted. The bag grew from three
items to four, which would have taken TRY from 13 questions to 25 — and answering
*"TAKE or SKIP?"* twenty-five times is a drill, not a lesson. So the build now asks:

- one *does it fit?*, at the **first** boundary in the table;
- one *which cell does TAKE build on?*, at the **first** cell whose TAKE reads a
  value worth more than 0 — the reuse, which is the only reason that question exists;
- one *TAKE or SKIP?* per row, on the **last column**, which is the cell the answer
  is eventually read from, plus the cell whose source was just named.

The rules are about where a cell sits rather than which dataset is loaded, so they
hold for any bag. TRY is 13 questions again — four about the problem, five about the
table, four walking back up it — and a test pins the five, so it cannot creep back up
without somebody deciding to.

This is the half of the decision most likely to be argued with later, so: a learner
who has answered *"TAKE or SKIP?"* three times **after** understanding what the
question means has learnt more than one who answered it twenty-five times before.

### The first act added no scene shape and no scene field

`DpTableScene` already carried `items`, `bag`, `focusCaption` and `choice` — the
cards and the strip the second act ends on — and `tableVisible` already meant *there
is no table yet*. So Act I is those four fields with no `cells` under them, and the
renderer's only new work is the arrangement for when there is no grid to sit beneath.
The union did not grow, which is what ADR-036 refused a shape for the Binary Search
Tree to protect.

One defaulted field joined `ChoiceStrip`: **`stem`**, naming the one thing both sides
are about. With it the same two cards draw as a fork — the item, a short drop, TAKE
and SKIP — which is how the recurrence is stated before there is a table to state it
in. Null everywhere else, so no other lesson changed: the additive move ADR-037,
ADR-039 and ADR-052 each made.

### Nothing answers a question before it is asked

While the two bags are side by side, **both totals read `?`**. Showing 13 and hiding
14 would make the question "read the other one"; showing both would make it "compare
two printed numbers". They appear together on the following beat with the winner lit.
That is ADR-030's rule applied to a comparison rather than to a cell, and it is
tested.

### A bag the story does not fit is not asked about

Act I's questions each need something to be true: everything must not fit, exactly
one thing must still fit after the first pick, and greed must lose. The authored bags
satisfy all of it. A synthetic one — nothing fits, or greed is already optimal —
cannot, so `storyHolds` is false and those beats are **stated instead of asked**.
This is the rule `KnapsackProblem` already applies to its own arguments: a lesson that
cannot be taught is not taught, rather than taught wrongly. The exhaustive
three-item sweep in `KnapsackTest` drives hundreds of such bags, and it was that sweep
that found the crash this paragraph describes the fix for.

### What did not change

The recurrence, `KnapsackProblem`'s validation, the walk back, `DpTableScene`'s
shape, the projector's cell states, `ProAccess`, progress, the one interstitial, the
`LessonPack` shape, `LessonController`, and every other lesson. The diff touches
knapsack's four engine files, its dataset, its scene's one field, its copy, the DP
renderer, one design token and its test file.

WATCH went from 20 beats to 26, and TRY stayed at 13. That is the cost of explaining
the problem before the method, and every added beat is a real visual change rather
than a pause — the adjacent-steps test caught two beats that drew the same thing and
they were separated before this was written.

**Alternatives considered.**
- *Keep the order and rewrite the copy.* Rejected. The complaint is not that the
  table was badly worded; it is that it arrives before the learner has a reason to
  want it.
- *Keep the brief's numbers and teach the tie.* Rejected: the tie lands on the final
  question, and "both choices give 13, and the rule keeps the row above" is a
  convention, not knapsack. A beginner-friendly rewrite cannot end on an arbitrary
  wrong answer.
- *Change Camera 10 → 9 instead.* Rejected: it also gives one optimum, but leaves a
  tie at `dp[4][3]`. Raising the Headphones is the only single-number fix that
  removes every tie.
- *Shrink the table instead of the questions* — three items rather than four.
  Rejected: the brief names four things, and the fourth is what makes `2ⁿ = 16` worth
  saying.
- *Give the fork its own scene shape.* Rejected on ADR-036's grounds. A fork is two
  outcomes weighed against each other, which is what `ChoiceStrip` already is; what
  was missing was the thing they are about, and that is one string.
- *Keep asking every fitting cell, and let TRY be 25 questions.* Rejected — see
  above. It is the drill the rewrite exists to remove.


---

## ADR-054 — Quick Sort shows which part it is solving

**Decision.** After a pivot lands, Quick Sort holds the finished partition on screen
for one beat as `[3, 2, 4] 5 [7, 8, 6]`, and then **names and isolates** the part it
descends into: the live partition is captioned `left of 5`, and everything outside it
is parked — shrunk and greyed — rather than drawn the same as the values being worked
on. The algorithm, the pivot rule, the decisions and the scene shape are unchanged.

⚠ **Product-owner decision**, 2026-09-22: after the pivot is fixed, a learner could
not see that the app was now solving the left part, or the right part, and it needed
to be shown visually rather than stated.

### What was wrong

Quick Sort's hard idea is not partitioning — the learner answers *which side?* for
every value and gets that. The hard idea is **recursion**: that the two sides are now
two smaller versions of the same problem, solved one at a time.

The lesson ran that recursion correctly and showed almost none of it. When a pivot
landed the outline vanished, the next partition's outline appeared somewhere else,
and the copy said *"Now the partition of 3."* Three things were invisible:

- **which side of which pivot** those three values were;
- that the other half was **waiting** rather than finished — every value outside the
  live partition was drawn exactly like the values inside it;
- that a partition had two halves at all, because the two never existed on screen at
  the same time. The pivot landed and the frame moved straight on.

### One beat where both halves exist

`placePivot` no longer closes the partition. It keeps `lo`/`hi`, records a `Split`,
and the next probe is mechanical — so there is exactly one frame showing the three
pieces, with `groups` dividing them and both halves captioned `below 5` and
`above 5`. `nextPartition` then clears the split and descends.

That frame is the one the request was about, and it costs one state. It is also the
only place in the lesson where the sentence *"two smaller problems are left"* is
literally true of the picture.

### A partition remembers where it came from

`pending` held `IntRange`. A range is enough to run the algorithm and not enough to
*describe* it, which is exactly why the copy could only count values. It now holds a
`Partition` — the range, the side, and the pivot it sits beside — so the lesson can
say `left of 5` in the caption and *"the values smaller than 5: 3, 2, 4"* in the
narration, both read from the same two fields.

### Parked, not eliminated

Everything outside the live partition is `CellState.ELIMINATED`, which the renderer
already shrinks to 0.82 and fades to 0.45. That is the right *picture* — this is not
what is being solved right now — and the wrong *word*, because these values are
deferred rather than ruled out. So the legend renames it to **Waiting**, which is the
mechanism ADR-027 added for exactly this case and the reason `legendLabels` exists.

No new cell state was added. A new one would have been a second way of saying
"inactive" that every other lesson would then have to ignore.

### `RegionMark` gained a label

One defaulted field. An outline says *something here is special*; a caption says
**what**. Quick Sort is the first lesson where the span changes meaning as the run
goes on — whole array, then left of 5, then right of 5 — so it is the first that
needs to name it. Null everywhere else, so no other lesson changed: the additive move
ADR-037, ADR-039, ADR-052 and ADR-053 each made.

The renderer draws the captions in a row that mirrors the cell row exactly, group
dividers included, so a caption sits over its own cells rather than drifting when a
divider is inserted.

### The split beat says "two halves" only when there are two

Later pivots often leave one side empty — the 4 in `[3, 2, 4]` has nothing above it —
and the first draft of this change announced *"two smaller arrays"* on every one of
them, four times, three of them false. The beat now carries the full split copy only
for the first partition and only when both sides are non-empty; every later pivot gets
the terse beat it already had. A test pins that, because the failure mode is copy that
is true of the teaching example and false of the run.

### What did not change

Lomuto, the last-value pivot rule, `<=` sending duplicates left, both decisions, the
datasets, the metrics, `SequenceScene`'s shape, and every other lesson. WATCH went
from 17 beats to 19 — the split, and one beat naming each side. TRY asks the same
questions it always did; what changed there is that between them the learner can see
which part is live.

**Alternatives considered.**
- *Say it in the narration only.* Rejected — that is what the lesson already did, and
  it is what the request was about. The complaint was explicitly visual.
- *A `RecursionScene` showing the call tree.* Rejected on ADR-036's grounds. The tree
  is a picture of the machinery, not of the data; the array already shows every state
  the tree would, and a second shape would have to be kept in step with it.
- *Indent or offset the live partition.* Rejected: it breaks the stable-slot contract
  ADR-007 rests on, and cells would appear to move without a swap.
- *A new cell state for "waiting".* Rejected — see above.
- *Keep both halves outlined while one is solved.* Rejected: two live-looking outlines
  is the ambiguity being removed. One is live and captioned; the other is parked.

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
