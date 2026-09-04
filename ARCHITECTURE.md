# AlgoKing — Technical Architecture

**Status:** v1.0 · 2026-08-23 · authored against `PRODUCT_SPEC.md` v1.0
**Scope:** all technical decisions. Product behaviour lives in `PRODUCT_SPEC.md`; visual
decisions live in `DESIGN_SYSTEM.md`; the reasoning trail lives in `DECISIONS.md`.

> **⚠ MVP AMENDMENT — 2026-09-04.** The MVP spine is **WATCH → TRY**; CHALLENGE is
> **deferred to V2** (`docs/v2-challenge.md`). The challenge machinery described below is
> **built, tested and retained**, but quarantined behind `engine/challenge/ChallengePack.kt`
> and reached by no MVP screen. Three things below now read differently:
> `Stage` is `{WATCH, TRY}` and progress derives 0 / 50 / 100 (§6.4); `LessonPack` no longer
> carries `challengeBrief`, `starFamily` or `challengeFactory`; and the `:app` lesson flow ends
> on `LessonCompleteScreen` rather than a scored Result.

---

## 0. The one architectural idea

Everything below follows from a single decision:

> **An algorithm is a pure, resumable state machine that can be stepped from *any* state —
> including states the correct execution would never reach.**

A precomputed trace alone cannot express *"execute the learner's wrong choice and show the
consequence"*, because that state is off the correct path. A live-animating loop cannot express
*"previous step"*, because the past has already been thrown away.

A resumable pure state machine gives both, and gives them for free:

| Product requirement | How it falls out |
|---|---|
| Watch playback | run the machine to completion, keep the frames, index them |
| Previous step / scrub | frames are an immutable `List` — index backwards |
| Try | stop at a decision, wait for the learner, apply their action |
| Wrong-choice consequence | apply the wrong action; the machine simply continues from there |
| Rewind | states are immutable values — pop the previous one off a stack |
| Challenge metrics | fold the emitted event stream |
| Challenge generation | run the machine headlessly and assert on the resulting trace |
| Testability | zero Android, zero Compose, zero coroutines in the engine |

If a future requirement cannot be expressed as *(pure state) → (probe) → (apply action)*, that is
the signal to revisit this document — not to add a special case.

---

## 1. Stack

| Concern | Choice | Version |
|---|---|---|
| Language | Kotlin | 2.2.10 |
| Build | AGP + Gradle KTS + version catalog | 9.3.1 |
| UI | Jetpack Compose (BOM) | 2026.02.01 |
| Design baseline | Material 3 (components) + custom token layer | from BOM |
| minSdk | **26** (raised from 24) | Android 8.0 |
| compileSdk / targetSdk | 36.1 / 36 | unchanged |
| JVM target | **17** (raised from 11) | required by AGP 9 toolchain |
| Navigation | Navigation Compose, type-safe routes | `navigation-compose` 2.9.x |
| Serialization | `kotlinx-serialization-json` | 1.9.x |
| Immutable collections | `kotlinx-collections-immutable` | 0.4.x |
| Async | Coroutines + Flow | 1.10.x |
| Structured storage | Room (KSP) | 2.9.x |
| Key-value storage | DataStore Preferences | 1.2.x |
| DI | **manual** — a single `AppContainer` | — |
| Ads | Google Mobile Ads SDK + UMP consent | 24.x |
| Analytics / crash | Firebase Analytics + Crashlytics | BOM 34.x |
| Unit test | JUnit 5 + Turbine + kotlin-test | — |
| UI test | Compose UI Test + Espresso | from BOM |
| Startup | Baseline Profile via `androidx.baselineprofile` | 1.4.x |

**minSdk 26** buys mandatory notification channels without branching, adaptive icons, and
`java.time` without desugaring, at a cost of roughly 1.5 % of the addressable device base.
For an app whose retention loop *is* a daily notification, that is a good trade.

Exact versions are pinned in `gradle/libs.versions.toml`. Nothing is declared outside the catalog.

---

## 2. Application architecture

**MVVM with unidirectional data flow, and a reducer inside the ViewModel for lesson playback.**

- Screens are `@Composable` functions that take `state: UiState` and `onEvent: (UiEvent) -> Unit`.
  They contain no business logic, no algorithm knowledge, and no `remember`ed domain state.
- ViewModels expose `StateFlow<UiState>` and accept `onEvent(UiEvent)`.
- For simple screens (Home, Journey, Progress) the ViewModel maps repository flows to state
  directly — plain MVVM, no reducer ceremony.
- For the **lesson screen only**, the ViewModel delegates to a `LessonReducer`: a pure function
  `(LessonState, LessonEvent) -> LessonState + Effects`. The lesson genuinely *is* a state
  machine with rewind and replay; a reducer here is not architectural fashion, it is the
  shape of the problem.

No MVI framework, no Orbit, no Mobius. The reducer is ~120 lines of Kotlin.

```
Compose UI ──onEvent──▶ ViewModel ──▶ LessonReducer (pure)
     ▲                      │                │
     └────StateFlow<UiState>┘                ▼
                                     AlgorithmRunner (pure)
                                            │
                                     Algorithm<S, A> (pure)
```

---

## 3. Module structure

Four Gradle modules. Each boundary enforces a rule the product spec asked for; none exists
for tidiness alone.

```
AlgoKing/
├── engine/     pure Kotlin JVM   — cannot see Android or Compose (compiler-enforced)
├── design/     Android library   — Compose only; knows nothing about algorithms
├── app/        Android app       — features, navigation, persistence, ads, analytics
└── (root)      docs + version catalog
```

| Module | May depend on | Why the boundary exists |
|---|---|---|
| `:engine` | *nothing* (stdlib, kotlinx-serialization, kotlinx-collections-immutable) | *"Keep algorithm logic independent from Compose UI."* Making `:engine` a **JVM** module means an `import androidx.compose.*` will not compile. The rule is enforced, not documented. Also: tests run in milliseconds with no Robolectric. |
| `:design` | `:engine` (**only** `engine.scene.*`) | *"Do not build nine unrelated visualizers."* The renderer receives a `SequenceScene` — cells, pointers, regions — and has no way to ask which algorithm produced it. |
| `:app` | `:engine`, `:design` | Wiring, persistence, platform. |

> **The one permitted leak:** `:design` depends on `:engine` for the scene data model. If
> `:design` ever imports anything from `engine.algorithms.*`, extract a fifth `:scene` module
> immediately — that import is the tripwire.

Feature modules are deliberately **not** created. At ten lessons and four screens they would
add build configuration without buying anything; features are packages inside `:app`.

### Package layout

```
engine/src/main/kotlin/com/ttele/algoking/engine/
├── core/          Algorithm, Probe, Transition, Frame, Trace, AlgorithmRunner
├── event/         VizEvent, Metrics, MetricsFolder
├── narration/     NarrationKey, NarrationArg     (keys only — no strings)
├── decision/      Decision, Action, ActionOption, DecisionValidation, Validation
├── scene/         SequenceScene, Cell, CellState, PointerMark, RegionMark, Badge
├── walkthrough/   WatchStep, WatchScript, WatchScriptBuilder, WatchNarrator
├── challenge/     Challenge, ChallengeType, Difficulty, ChallengeGenerator, ChallengeRun
├── algorithms/
│   ├── binarysearch/  State, Algorithm, Projector, WatchNarrator
│   ├── bubblesort/    State, Algorithm, Projector, WatchNarrator
│   ├── selectionsort/ State, Algorithm, Projector, WatchNarrator
│   ├── insertionsort/ State, Algorithm, Projector, WatchNarrator
│   ├── mergesort/     State, Algorithm, Projector, WatchNarrator
│   ├── quicksort/     State, Algorithm, Projector, WatchNarrator
│   ├── hashing/       HashMapState, HashMapAlgorithm, HashScripts,
│   │                  HashMapProjector, HashMapWatchNarrator
│   ├── linkedlist/    LinkedListState, LinkedListAlgorithm, ListScripts,
│   │                  LinkedListProjector, LinkedListWatchNarrator
│   └── structures/    StructureState, LinearStructureAlgorithm, StructureFlavour,
│                      StackFlavour, QueueFlavour, StructureProjector,
│                      StructureWatchNarrator     (one engine, two lessons — ADR-027)
├── dataset/       Dataset, DatasetSpec, Constraint, SeededGenerator, ConstraintValidator
├── progress/      Stage, AlgorithmProgress, LearningProgress, ProgressCodec
│                  (three flags per algorithm; the percentage is derived — ADR-028)
├── scoring/       StarFamily, ScoreInput, ScoreResult, Scorer, VerdictBuilder
└── catalog/       AlgorithmId, LessonPack, AlgorithmCatalog (id → everything one lesson needs)

design/src/main/kotlin/com/ttele/algoking/design/
├── theme/         AlgoTheme, AlgoColors, AlgoTypography, AlgoShapes, AlgoSpacing, LocalAlgo*
├── motion/        Durations, Easings, MotionScale, LocalReducedMotion
├── renderer/      SequenceRenderer, CellView, PointerView, RegionView, BadgeView, SwapArc
└── component/     AlgoButton, DecisionChip, MetricTile, NarrationStrip, StageSpine,
                   StarRow, MasteryBadge, TransportBar, AlgoScaffold

app/src/main/kotlin/com/ttele/algoking/
├── AlgoKingApplication.kt, MainActivity.kt, AppContainer.kt
├── nav/           Route (@Serializable), AlgoNavHost, BottomBar
├── feature/
│   ├── home/      HomeScreen, HomeViewModel, HomeUiState, PrimaryCardResolver
│   ├── journey/   JourneyScreen, JourneyViewModel
│   ├── hub/       HubScreen, HubViewModel
│   ├── lesson/    LessonScreen, LessonViewModel, LessonReducer, LessonUiState,
│   │              playback/PlaybackController, phase/{Watch,Try,Challenge}Host
│   ├── result/    ResultScreen, ResultViewModel
│   ├── code/      CodeScreen, CodeViewModel, CodeSource (assets)
│   ├── daily/     DailyScreen, DailyViewModel, DailySeed
│   ├── progress/  ProgressScreen, ProgressViewModel
│   └── settings/  SettingsScreen, SettingsViewModel
├── data/
│   ├── db/        AlgoDatabase, entities/, dao/
│   ├── prefs/     SettingsStore, SessionStore  (DataStore)
│   └── repo/      ProgressRepository, StreakRepository, SettingsRepository, AdStateRepository
├── ads/           AdPolicy (pure), AdHost, ConsentManager, Placement
├── analytics/     Analytics (interface), FirebaseAnalytics impl, NoopAnalytics, LearningEvent
└── notify/        NotificationScheduler, Channels
```

---

## 4. Domain layer — the algorithm engine

### 4.1 The three primitives

```kotlin
/** A pure, resumable algorithm. No time, no Android, no I/O. */
interface Algorithm<S : Any, A : Action> {
    val id: AlgorithmId

    /** Build the starting state from a dataset. Pure. */
    fun initial(dataset: Dataset): S

    /** What does the algorithm want to do next, from THIS state? Pure, side-effect free. */
    fun probe(state: S): Probe<A>

    /**
     * Apply ANY legal action — correct or not — and return the resulting state.
     * This is the method that makes wrong-choice consequences possible: applying an
     * incorrect action is not an error path, it is the same code path with a different
     * argument. Pure.
     */
    fun apply(state: S, action: A): Transition<S>
}

sealed interface Probe<out A : Action> {
    /** No learner input: the app advances (pointer moves, pass boundaries). */
    data class Mechanical<A : Action>(val action: A) : Probe<A>

    /** The learner decides. Carries every legal option AND which one is correct. */
    data class Decide<A : Action>(val decision: Decision<A>) : Probe<A>

    /** The algorithm has finished — correctly or otherwise. */
    data class Terminal(val outcome: Outcome) : Probe<Nothing>
}

data class Transition<S : Any>(
    val next: S,
    val events: ImmutableList<VizEvent>,
    val narration: NarrationKey?,          // key + args, never a string
    val correct: Boolean,                  // was the applied action the correct one?
)
```

Three methods. `probe` never mutates and never chooses; `apply` never decides. That separation
is what lets the same algorithm serve Watch (auto-choose), Try (learner chooses, guided) and
Challenge (learner chooses, unguided) without a `mode` flag anywhere inside it.

### 4.2 The runner

```kotlin
class AlgorithmRunner<S : Any, A : Action>(
    private val algorithm: Algorithm<S, A>,
    dataset: Dataset,
) {
    private val history = ArrayDeque<Frame<S>>()   // rewind stack — states are immutable values
    var current: Frame<S> ; private set

    fun probe(): Probe<A>
    fun apply(action: A): Frame<S>       // pushes onto history
    fun rewind(steps: Int = 1): Frame<S> // pops — exact, not recomputed
    fun reset()

    /** Drive to completion taking the correct option at every decision. Used by Watch, by the
     *  Try replay preamble, by the mercy exit, and by the dataset validator. */
    fun runToCompletion(maxSteps: Int = 4_096): Trace<S>
}

data class Frame<S>(
    val index: Int,
    val state: S,
    val events: ImmutableList<VizEvent>,
    val narration: NarrationKey?,
    val correct: Boolean,
    val metrics: Metrics,                 // cumulative, folded up to this frame
)

@JvmInline value class Trace<S>(val frames: ImmutableList<Frame<S>>)
```

`maxSteps` is a hard termination guard. Any algorithm that exceeds it under *any* action
sequence is a bug, and there is a test for exactly that (§11).

### 4.3 How each phase uses the same runner

| Phase | Loop |
|---|---|
| **Watch** | `runToCompletion()` up front → play `Trace` frames on a timer → scrub freely |
| **Try** | `probe()` → `Mechanical` ⇒ auto-apply · `Decide` ⇒ emit to UI, await action, `apply(chosen)` |
| **Mercy exit** | `runToCompletion()` from the *current* state → play the remainder at 3× |
| **Challenge A** | identical to Try with guidance suppressed and metrics surfaced |
| **Challenge B** | `runToCompletion()` headlessly, compare the learner's arranged array to the frame at the pass boundary |
| **Consequence** | `apply(wrongAction)` — no special code path; the machine continues from the diverged state |
| **Rewind** | `rewind(1)` — pops the stack |

The mercy exit and the dataset validator use the exact same `runToCompletion` the Watch phase
uses. There is one execution path in the system.

### 4.4 Binary Search midpoint convention — canonical

AlgoKing uses the standard **lower middle**, written in the overflow-safe form:

```kotlin
// BinarySearchState.middleOfRange — the ONLY place a midpoint is computed.
val middleOfRange: Int get() = lo + (hi - lo) / 2
```

For an **inclusive** range `left..right`:

| Comparison | Transition |
|---|---|
| `target < values[mid]` | `right = mid - 1` |
| `target > values[mid]` | `left = mid + 1` |
| `target == values[mid]` | **found** |

**On an even-sized range this takes the left of the two centre cells.** That is the only case
where the convention is observable, so it is the case every dataset, hint and test is authored
against.

Three rules follow, and all three are enforced by test:

1. **The engine computes it; nothing else does.** Watch, Try, Challenge, `MissionRun`,
   `MissionCatalog` and every hint read `middleOfRange` or mirror it under test. A second
   midpoint expression anywhere is how Watch and Challenge silently disagree.
2. **The taught formula is the printed formula.** `MidpointChip`, the rewarded hint and
   Code Reveal all show `left + (right - left) / 2` — not `(left + right) / 2`, because the
   subtraction form cannot overflow and a learner should be able to reuse what they were shown.
3. **Authored content is derived, never assumed.** Watch and Try datasets are chosen by running
   the real trace (`docs/` note in `BinarySearchDatasets`), so "six of twelve eliminated" is a
   computed fact rather than a remembered one.

> Changing this convention is a content change, not a one-line change: it re-authors the Watch
> and Try datasets, every trace assertion, the insight copy and the hint ladder. It was changed
> once, from upper to lower middle, to keep Code Reveal honest against the textbook.

---

## 5. Event model

### 5.1 Why not the literal verb list

`PUSH`, `POP`, `ENQUEUE`, `DEQUEUE` are four names for *"insert at an end"* and *"remove from an
end"*. `FOUND` / `NOT_FOUND` are one terminal outcome with a payload. `KEEP` is *"examine, then
do nothing"*. Encoding algorithm vocabulary in the event stream forces the renderer to learn
nine vocabularies — which is exactly the *"nine unrelated visualizers"* outcome the spec forbids.

Events are therefore named for **what the renderer must do**, not for what the algorithm calls
it. Algorithm vocabulary lives in narration keys, where it belongs.

### 5.2 The events

```kotlin
sealed interface VizEvent {
    /** Draw attention without changing anything. compare / inspect / peek. */
    data class Examine(val indices: ImmutableList<Int>, val role: ExamineRole) : VizEvent

    /** A comparison actually happened — counted in Metrics.comparisons. */
    data class Compare(val a: Int, val b: Int, val relation: Relation) : VizEvent

    /** Two cells exchange positions. Rendered as the arc. Counted in Metrics.swaps. */
    data class Swap(val a: Int, val b: Int) : VizEvent

    /** Examined and deliberately left alone. The visual counterpart of "keep". */
    data class Hold(val indices: ImmutableList<Int>) : VizEvent

    /** A named cursor moves. lo/hi/mid/i/j/left/right/windowStart… */
    data class MovePointer(val pointer: PointerId, val to: Int?) : VizEvent

    /** A range leaves consideration forever. Collapse + desaturate. */
    data class Eliminate(val range: IntRange, val reason: EliminateReason) : VizEvent

    /** A range is now provably final. Lock + green. */
    data class Finalize(val range: IntRange) : VizEvent

    /** A value enters the sequence at an index. push / enqueue / insertion-sort placement. */
    data class Insert(val value: Int, val at: Int) : VizEvent

    /** A value leaves the sequence. pop / dequeue. */
    data class Remove(val at: Int) : VizEvent

    /** A persistent annotation on one cell. minSoFar, target, best. */
    data class Mark(val index: Int?, val mark: MarkId) : VizEvent

    /** A persistent span annotation. window, sortedPrefix, searchSpace. */
    data class Region(val range: IntRange?, val region: RegionId) : VizEvent

    /** Scalar readout changed. runningSum, best, distance. */
    data class Meter(val meter: MeterId, val value: Long) : VizEvent

    /** The run ended. */
    data class Terminal(val outcome: Outcome) : VizEvent
}

enum class ExamineRole { COMPARING, INSPECTING, CANDIDATE }
enum class Relation { LESS, EQUAL, GREATER }
enum class EliminateReason { TOO_SMALL, TOO_LARGE, ALREADY_SORTED, OUT_OF_WINDOW }
sealed interface Outcome {
    data class Found(val index: Int) : Outcome
    data object NotFound : Outcome
    data object Sorted : Outcome
    data class Completed(val correct: Boolean) : Outcome
}
```

Thirteen events cover all ten MVP algorithms, and `Insert`/`Remove`/`Region`/`Meter` already
cover the v1.1 grid and window work. Adding an algorithm should add **zero** events; if it does
not, that is a real signal worth stopping for.

### 5.3 Metrics

Derived, never hand-maintained:

```kotlin
data class Metrics(
    val comparisons: Int, val swaps: Int, val passes: Int,
    val pointerMoves: Int, val eliminated: Int,
    val steps: Int, val wrongDecisions: Int, val hintsUsed: Int,
    val elapsedMillis: Long,
)
```

`MetricsFolder.fold(previous, events, correct)` is pure and total. A metric that cannot be
folded from the event stream does not exist.

### 5.4 Narration

The engine emits `NarrationKey(id, args)`; `:app` resolves it to a string resource. This keeps
`:engine` free of Android `R` references, keeps it a JVM module, and makes the app translatable
without touching algorithm code.

---

## 6. Decision system

```kotlin
data class Decision<A : Action>(
    val prompt: NarrationKey,               // "Which half can contain 62?"
    val options: ImmutableList<ActionOption<A>>,
    val correct: A,
    val focus: ImmutableList<Int>,          // cells to emphasise while they reason
    val hint: NarrationKey,                 // the free hint, before any wrong answer
    val guidance: ImmutableList<NarrationKey>,  // escalating; least to most explicit
    val whyWrong: Map<A, NarrationKey>,     // why THIS option cannot be right
    val correctFeedback: NarrationKey,      // what their right answer achieved
)
```

**The correct action is data on the decision, not a branch in the UI.** `ActionOption` carries
no `isCorrect` field, so the UI could not style the right answer differently even by accident —
a requirement in `PRODUCT_SPEC.md` §5.

### 6.0 Two shapes of decision

```kotlin
enum class DecisionKind { OPTIONS, CELL }
```

`OPTIONS` renders a row of equal-weight buttons; `CELL` makes the sequence tappable and each
option carries the `slot` it selects. This is presentation-shape only — the renderer needs to
know whether to draw buttons or arm the array, and nothing more.

It is also what lets one algorithm serve every phase without a mode flag. Binary Search always
emits two decisions per round — *which cell?* then *which half?* — and the **host** decides
who answers the first:

Whether the *host* may answer a decision in Try is a property of the **decision**
(`Decision.autoInTry`), not of its kind. Only bookkeeping the learner is not being taught sets
it — Binary Search computing `mid`. Marking it by `DecisionKind` instead was a bug: it
silently answered Selection Sort's placement and Merge Sort's split, which are the very
judgements those lessons exist to teach.

| Phase | `autoInTry` decision | every other decision |
|---|---|---|
| Watch | script auto-answers | script auto-answers |
| Try | host auto-answers | **the learner** |
| Challenge | **the learner** | **the learner** |

### 6.1 Validation — the one rule that protects the algorithm

```kotlin
sealed interface Validation<out A : Action> {
    data class Accept<A : Action>(val action: A, val feedback: NarrationKey) : Validation<A>
    data class Retry(
        val level: Int,                     // 1-based, escalates per repeat
        val guidance: NarrationKey,
        val whyWrong: NarrationKey?,
        val focus: ImmutableList<Int>,
    ) : Validation<Nothing>
}

object DecisionValidation {
    fun <A : Action> validate(decision: Decision<A>, chosen: A, priorAttempts: Int): Validation<A>
}
```

```
DecisionPoint → UserAction → validate → Accept → engine advances
                                      ↘ Retry  → feedback, SAME state
```

> **A `Retry` carries no action.** There is nothing the caller *could* apply, so a learner's
> mistake cannot corrupt an algorithm state — in any algorithm, by construction rather than by
> discipline. This is the single most important invariant in the learning engine.

`validate` is pure, total, and knows nothing about which algorithm it is validating: it compares
against `decision.correct` and indexes `decision.guidance`. Adding an algorithm adds a narrator,
not a code path.

### 6.2 The ladder, as a reducer

`PRODUCT_SPEC.md` §5 defines the ladder. It is implemented once, in `LessonReducer`, driven by
`attempt` and the phase:

| Phase | Wrong · 1st | Wrong · 2nd | Wrong · 3rd+ | State moves? |
|---|---|---|---|---|
| **Try** | shake, point at the evidence | ask the reasoning question + `whyWrong` | state it plainly, then repeat | **never** |
| **Challenge** | log ✕, advance with `decision.correct` | same | after 2 failed *runs* → free walkthrough | yes, but always *correctly* |

Try never advances on a wrong answer, so `attempt` only ever resets on an `Accept`. Challenge
never blocks, so its `attempt` resets every decision.

### 6.3 What was removed

`ConsequencePolicy` — which let a second wrong answer *execute* and lose the target — is gone.
It contradicted the rule above: executing a wrong action is exactly "a wrong answer became a
state transition". `Algorithm.apply` still accepts any action (that is what makes `validate` a
pure comparison rather than a special case), but in Try nothing ever calls it with one.

---
## 6.4 One lesson, many algorithms — `LessonPack`

```kotlin
class LessonPack<S : Any, A : Action>(
    val id: AlgorithmId,
    val displayName: String,
    val algorithm: Algorithm<S, A>,
    val projector: SceneProjector<S>,
    private val watchNarrator: WatchNarrator<S>,
    val watchDataset: Dataset,          // authored
    val tryDataset: Dataset,            // authored, different values
)

// The challenge-only fields moved to `engine/challenge/ChallengePack.kt` when
// CHALLENGE was deferred to V2, so nothing on the MVP path carries them:
class ChallengePack(
    val id: AlgorithmId,
    val challengeBrief: String,
    val starFamily: StarFamily,
    private val challengeFactory: (round: Int, seed: Long) -> Challenge,
)
```

`LessonController`, `WatchScreen`, `LessonScreen`, `ChallengeIntroScreen` and `ResultScreen`
are all generic over `<S, A>` and read everything else from the pack. **Adding an algorithm
adds a pack — never a screen, a renderer, or a `when (algorithm)`.**

The one star projection lives in `MainActivity.LessonFlow`, because a navigation route can
only carry an `AlgorithmId`. It stops there: every screen below it is fully typed.

### Learning progress

Stage completion lives in `:engine` as pure data, so the rule is testable without a device:

```kotlin
data class AlgorithmProgress(watchCompleted, tryCompleted) {
    val percent: Int   // 0 / 50 / 100 — DERIVED, never stored
}
```

`:app` holds `ProgressRepository` over DataStore Preferences (§8.1) and exposes a `Flow`, so a
stage finished inside a lesson reaches Home's rings on the next frame. `complete()` only ever
sets a flag, so no retry, failure or repeat practice run can subtract progress — see ADR-028.
Adding a lesson gets progress for free: there is no per-algorithm progress code anywhere.

### What the ten built lessons prove

| | Binary Search | Bubble Sort | Selection Sort | Insertion Sort |
|---|---|---|---|---|
| The learner decides | which cell, then which half | swap or keep | is this the new minimum, then where does it go | shift or insert |
| The app decides | nothing in Challenge | the pair, pass boundaries | the scan cursor | the key, the compare cursor |
| Decision kinds | `CELL` + `OPTIONS` | `OPTIONS` | `OPTIONS` + `CELL` | `OPTIONS` |
| Probe kinds | `Decide` | `Mechanical` + `Decide` | `Decide` | `Mechanical` + `Decide` |
| Signature event | `Eliminate` | `Swap` | `Mark(MIN_SO_FAR)` | `Insert`/`Remove` |
| Signature cell state | `ELIMINATED` | `COMPARING` pair | `CANDIDATE` | `GHOST` |
| Star family | Efficiency | Accuracy | Accuracy | Accuracy |
| Terminal outcome | `Found` / `NotFound` | `Sorted` | `Sorted` | `Sorted` |

| | Merge Sort | Quick Sort | Stack | Queue | Linked List |
|---|---|---|---|---|---|
| The learner decides | where to split, then which front value | which side of the pivot, then where the pivot lands | which operation, then which item leaves next | *(identical questions, opposite answers)* | is this the node, then which link changes and where it points |
| The app decides | the deeper splits, the leftover tail | the pivot, the partition order | nothing | nothing | nothing |
| Decision kinds | `CELL` + `OPTIONS` | `OPTIONS` + `CELL` | `OPTIONS` + `CELL` | `OPTIONS` + `CELL` | `CELL` (over **links**) + `OPTIONS` |
| Probe kinds | `Mechanical` + `Decide` | `Mechanical` + `Decide` | `Decide` | `Decide` | `Mechanical` + `Decide` |
| Signature event | `Region` + `groups` | `Finalize` (the pivot) | `Insert`/`Remove` + `endCaps` | `Insert`/`Remove` + `endCaps` | `links` + `detached` |
| Signature cell state | merged prefix `FINALIZED` | pivot `FINALIZED` | `CANDIDATE` at the top | `CANDIDATE` at the front | `ELIMINATED` while unlinking |
| Layout | `ROW` | `ROW` | **`PILE`** | `ROW` | **`CHAIN`** |
| Star family | Accuracy | Accuracy | Accuracy | Accuracy | Accuracy |
| Terminal outcome | `Sorted` | `Sorted` | `Completed` | `Completed` | `Completed` |

| | Hash Map |
|---|---|
| The learner decides | which bucket `key % 5` lands in, then what a full bucket does, then which entry matches |
| The app decides | storing and removing, once every judgement is made |
| Decision kinds | `CELL` (over **buckets**) + `OPTIONS` |
| Probe kinds | `Mechanical` + `Decide` |
| Signature scene | **`BucketScene`** — not a sequence at all |
| Signature idea | a collision is normal, and the same key updates rather than duplicating |
| Star family | Accuracy |
| Terminal outcome | `Completed` |

They exercise different halves of the same machinery, which is the point: none was forced into
another's interaction model, and **no event, probe kind or validator was added after the first**.
Five generic additions have been earned along the way — `SequenceScene.groups` (Merge Sort: a
sequence can be shown divided), `Decision.autoInTry` (see below), `SequenceScene.endCaps` plus
`legendLabels` (the structures: a sequence can name its ends and rename a cell state), and
`SceneLayout` plus `links`/`detached` (the linked list: a sequence can be a chain), and the
`Scene` union itself (the hash map: a lesson need not be a sequence). Every one of them is data
on the scene, so the app's only branch is still the shape of what it was given.

The three elementary sorts in particular had to end up looking different from each other, and
they do — trading neighbours, carrying a minimum to the front, and walking a gap backwards are
three distinct pictures built from the same thirteen events. Stack and Queue go further: they
are the same `LinearStructureAlgorithm` instance shape with one property flipped, and they still
read as two different structures because the scene declares a different orientation and a
different set of live ends (ADR-027).

---

## 7. Renderer architecture

### 7.1 The contract

`Scene` is a sealed union of **shapes**, and the app dispatches on the shape — never on which
algorithm produced it. Eight lessons are sequences and share one renderer; a hash map is not a
sequence and has its own (ADR-030).

```kotlin
sealed interface Scene
data class SequenceScene(...) : Scene   // ROW | PILE | CHAIN
data class BucketScene(...) : Scene     // a table of buckets, plus the hash flow
```

The sequence renderer receives pure data, and its only branch is `layout`:

```kotlin
// engine/scene/ — pure Kotlin, no Compose types
@Immutable
data class SequenceScene(
    val cells: ImmutableList<Cell>,
    val pointers: ImmutableList<PointerMark>,
    val regions: ImmutableList<RegionMark>,
    val badge: Badge?,                     // "Target 73"
    val meters: ImmutableList<MeterReadout>,
    val layout: SceneLayout,               // ROW | PILE (stack) | CHAIN (linked list)
    val endCaps: EndCaps?,                 // "OUT <-" / "<- IN", "HEAD" / "NULL"
    val links: ImmutableList<Link>,        // CHAIN only: one arrow per gap, 0..n
    val detached: DetachedNode?,           // CHAIN only: a node made but not linked in yet
    val legendLabels: Map<CellState, String>,  // CANDIDATE is not always "smallest so far"
)

@Immutable
data class Cell(
    val key: CellKey,                      // STABLE identity — survives swaps. This is the
                                           // single most important field in the renderer.
    val value: Int,
    val slot: Int,                         // current position
    val state: CellState,
)

enum class CellState { IDLE, EXAMINING, COMPARING, CANDIDATE, FINALIZED, ELIMINATED, GHOST }
```

`CellKey` is assigned at dataset creation and **never changes**. A swap changes two cells' `slot`,
not their identity. That is what allows Compose to animate the exchange instead of recomposing
two boxes with different numbers in them — and it is why swaps look like motion rather than a
flicker.

### 7.2 Who builds the scene

Each algorithm ships a `SceneProjector<S>`:

```kotlin
interface SceneProjector<S : Any> {
    fun project(state: S, activeEvents: ImmutableList<VizEvent>): SequenceScene
}
```

Algorithm-specific *presentation* knowledge (which pointer is called `mid`, that the sorted
suffix is finalized) lives beside the algorithm; the renderer stays generic. `:design` receives
`SequenceScene` and can neither name nor branch on the algorithm.

### 7.3 Layout and motion

- Arrays are ≤ 14 cells. A custom `Layout` measures a uniform cell size to fit the width, then
  positions each cell **by its animated offset**, not by list order.
- Each cell owns an `Animatable<Offset>` targeting `slot * (cellWidth + gap)`.
- A swap animates both cells to each other's slot and applies opposing vertical arcs (one over,
  one under) so the exchange is legible — `DESIGN_SYSTEM.md` §8.
- **Offsets are read inside `Modifier.graphicsLayer { }`**, a deferred read, so an animation
  frame invalidates only the draw phase — not composition, not layout. This is the single
  most important performance idiom in the app.
- Reduced motion (`LocalReducedMotion`): translation is replaced by a cross-fade in place;
  **every duration and hold is preserved**, because the holds are the pedagogy.

### 7.4 Playback

The engine has no concept of time. `PlaybackController` (in `:app`, `feature/lesson/playback/`)
owns it:

```kotlin
class PlaybackController(
    private val scope: CoroutineScope,
    private val motionScale: StateFlow<Float>,     // 0.5× / 1× / 2× — scales HOLDS ONLY
) {
    val position: StateFlow<Int>
    fun play() ; fun pause() ; fun next() ; fun previous() ; fun seek(i: Int) ; fun restart()
}
```

The step timing contract from `PRODUCT_SPEC.md` §4 is encoded once, as a `StepTimeline` data
class in `:design/motion`. Motion durations are constant across speeds; only holds scale. A test
asserts this (§11) because it is easy to break and it changes how the algorithm *reads*.

---

## 8. Data layer

### 8.1 Split

| Store | Holds | Why |
|---|---|---|
| **Room** | algorithm progress, challenge attempts, daily results, achievements | grows unbounded over time (daily results are one row per day, forever), needs date-range queries for the streak calendar and `MAX`/`MIN` for personal bests, and gives reactive `Flow` queries that drive Home's primary card |
| **DataStore Preferences** | settings, session counters, ad state, consent, onboarding flags | small, flat, frequently written, never queried |

Nothing is stored in `SharedPreferences`. Nothing is stored in files.

### 8.2 Room schema — v1

```kotlin
@Entity  AlgorithmProgress(
    algorithmId: String PK, masteryState: String, bestStars: Int,
    bestComparisons: Int?, bestSwaps: Int?, bestAccuracy: Float?, bestTimeMillis: Long?,
    lastPhase: String?, lastStepIndex: Int?,          // powers "Continue — Try · step 7"
    firstSeenAt: Long?, masteredAt: Long?, lastPracticedAt: Long?  // lastPracticedAt → Rusty
)

@Entity  ChallengeAttempt(
    id: Long PK, algorithmId: String, startedAt: Long, durationMillis: Long,
    seed: Long, format: String,                        // OPERATE | PASS_PREDICTION
    comparisons: Int, swaps: Int, wrongDecisions: Int, hintsUsed: Int,
    stars: Int, passed: Boolean
)

@Entity  DailyResult(
    epochDay: Long PK, seed: Long, roundsCompleted: Int, roundsCorrect: Int,
    score: Int, durationMillis: Long, streakProtected: Boolean   // true ⇒ a freeze was spent
)

@Entity  AchievementUnlock(achievementId: String PK, unlockedAt: Long)
```

`epochDay` as the daily primary key makes the streak calendar a single range query and makes
double-submission structurally impossible.

**Migrations:** exported schemas committed under `app/schemas/`, `fallbackToDestructiveMigration`
is **never** enabled — losing a 90-day streak to a schema change is exactly the one-star-review
scenario `PRODUCT_SPEC.md` §14 warns about.

### 8.3 What is deliberately *not* persisted

Lesson traces (recomputed from `dataset + seed` in microseconds), scene objects, ad SDK state,
and anything derivable. Persist inputs and outcomes; recompute everything in between.

### 8.4 No backend

There is no server, no account, and no network dependency for any learning function. The app is
fully usable in airplane mode; only ads and analytics require connectivity, and both degrade
silently. Google sign-in for streak backup is a v1.1 item and remains strictly optional.

---

## 9. Challenge generation

Deterministic, offline, and **validated by running the real algorithm**.

```kotlin
data class DatasetSpec(
    val size: IntRange,
    val valueRange: IntRange,
    val sorted: Boolean,
    val distinct: Boolean,
    val constraints: ImmutableList<Constraint>,
)

/** A constraint is asserted against the TRACE, not against the raw array. */
fun interface Constraint {
    fun holds(trace: Trace<*>, dataset: Dataset): Boolean
}
```

Because constraints inspect the trace produced by the actual algorithm, it is **impossible** to
generate a dataset that violates a pedagogical rule. Examples:

| Constraint | Enforces |
|---|---|
| `FirstDecisionIs(Keep)` | `PRODUCT_SPEC.md` §6 — the solution path must differ from Watch |
| `TargetFoundOnBranch(LEFT)` | Binary Search Challenge diverges from the Watch example |
| `MinimumPasses(2)` | the Bubble Sort challenge is not trivially pre-sorted |
| `SwapCountBetween(3, 6)` | the challenge is neither trivial nor tedious |
| `OptimalComparisons(3)` | a ★★★ is actually reachable |
| `NoValueOverlapWith(watchDataset)` | *"fully regenerated, no overlap"* |

```kotlin
class SeededGenerator(private val algorithm: Algorithm<*, *>) {
    fun generate(spec: DatasetSpec, seed: Long): Dataset {
        val rng = Random(seed)
        repeat(MAX_ATTEMPTS) {                       // 200
            val candidate = draw(spec, rng)
            val trace = AlgorithmRunner(algorithm, candidate).runToCompletion()
            if (spec.constraints.all { it.holds(trace, candidate) }) return candidate
        }
        return CuratedFallbacks.forSeed(algorithm.id, seed)   // bundled, never fails
    }
}
```

**Watch datasets are authored, not generated.** The teaching example must be perfect — the
Bubble Sort demo is `[7, 3, 8, 2, 5]` because it produces a swap, then a keep, then a swap in
the first three comparisons, which is exactly the rhythm the lesson needs. Try reuses the Watch
dataset. Only Challenge and Daily are generated.

**Daily seed:** `seed = hash(epochDay)` — global, identical for every user on a given date, with
no server. Shareable comparison with zero social infrastructure.

---

## 10. Cross-cutting

### 10.1 Dependency injection — manual

A single `AppContainer` built in `Application.onCreate`, holding ~12 singletons, with
`viewModelFactory { }` builders per screen. Hilt would add KSP to every module and roughly a
dozen annotations per feature to solve a graph this size. Revisit if the graph passes ~25 types.

### 10.2 Navigation

Navigation Compose with `@Serializable` route types — compile-time-checked arguments, no string
parsing. `Route.Lesson(algorithmId, phase)` rather than `"lesson/{id}/{phase}"`.

Lesson, Result, Code and Daily are declared **outside** the bottom-bar graph so the nav bar is
structurally absent during learning rather than conditionally hidden.

### 10.3 Ads

```kotlin
// Pure Kotlin, zero Android imports, fully unit-tested.
class AdPolicy(private val clock: Clock) {
    fun decide(placement: Placement, session: SessionState, progress: ProgressSnapshot): AdDecision
}
sealed interface AdDecision {
    data object Show : AdDecision
    data class Suppress(val reason: SuppressReason) : AdDecision
}
enum class SuppressReason {
    FIRST_SESSION, UNDER_THREE_LESSONS, FORWARD_PATH, INSIDE_LEARNING,
    FREQUENCY_CAP, SESSION_CAP, STREAK_MILESTONE, AD_FREE_WINDOW, NO_CONSENT
}
```

The rules in `PRODUCT_SPEC.md` §9 are subtle and getting one wrong damages the product more than
a missed impression earns. They are therefore encoded in a **pure, table-tested** class rather
than scattered across navigation callbacks.

- `AdHost` (Android) preloads and shows; it asks `AdPolicy` first, always.
- **`Placement` distinguishes `FORWARD` from `EXIT`.** `Result → Next` is `FORWARD` and is
  hard-coded to `Suppress(FORWARD_PATH)`. This implements the one rule that governs everything.
- **Ads are unreachable from learning code.** `AdHost` is injected only into nav-level exit
  handlers. No lesson composable receives it, so a "quick banner on the challenge screen" is not
  something a future change can do by accident.
- `ConsentManager` (UMP) runs before any ad or analytics initialisation.

### 10.4 Analytics

Firebase Analytics + Crashlytics behind an `Analytics` interface, with `NoopAnalytics` used in
debug and in tests. Zero-ops, free, and already implied by the Play Services dependency AdMob
requires. No custom backend.

```kotlin
sealed interface LearningEvent {
    data class PhaseStarted(val algorithmId: String, val phase: Phase)
    data class PhaseCompleted(val algorithmId: String, val phase: Phase, val durationMs: Long)

    /** The single most valuable event in the app: which step of which algorithm confuses
     *  people, and on which attempt they recover. This is the content-quality signal. */
    data class DecisionMade(
        val algorithmId: String, val phase: Phase, val stepIndex: Int,
        val correct: Boolean, val attempt: Int, val millisToDecide: Long,
    )

    data class ConsequenceShown(val algorithmId: String, val stepIndex: Int)
    data class MercyExitUsed(val algorithmId: String, val atStep: Int)
    data class HintUsed(val algorithmId: String, val stepIndex: Int, val rewarded: Boolean)
    data class WalkthroughShown(val algorithmId: String, val afterFailures: Int)
    data class ChallengeCompleted(
        val algorithmId: String, val format: String, val stars: Int,
        val comparisons: Int, val wrongDecisions: Int, val durationMs: Long,
    )
    data class MasteryAchieved(val algorithmId: String, val stars: Int)
    data class DailyCompleted(val epochDay: Long, val rounds: Int, val correct: Int)
    data class StreakMilestone(val days: Int)
    data class CodeRevealOpened(val algorithmId: String, val language: String)
    data class AdShown(val format: String, val placement: String)
    data class AdSuppressed(val format: String, val placement: String, val reason: String)
}
```

`AdSuppressed` is logged deliberately: it is how we verify in production that the ad rules are
actually firing as specified, rather than trusting that they are.

**Privacy:** no PII, no email, no device identifiers beyond the Firebase instance ID. Analytics
initialise only after UMP consent. No user-generated content ever leaves the device.

### 10.5 Performance budget

Target: sustained 60 fps on a 3 GB / mid-range 2022 device (`PRODUCT_SPEC.md` §16 assumption 3).

- `SequenceScene` and every nested type are `@Immutable` with `ImmutableList` — no unstable
  `List` parameters crossing a composable boundary.
- Animated offsets are read in `graphicsLayer` (deferred), never in composition.
- ≤14 cells means no `LazyRow`; a plain custom `Layout` is cheaper and gives absolute positioning.
- No text drawn into `Canvas` — `Text` composables with tabular figures.
- Baseline Profile generated for cold start and the Watch→Try transition.
- Strict mode + `Compose compiler metrics` wired in debug builds; any composable in the renderer
  reported as unstable is a build-review failure.

---

## 11. Testing strategy

### `:engine` — pure JVM, milliseconds, no Robolectric

| Test | Asserts |
|---|---|
| **Correctness (property-based, 1 000 seeds)** | `runToCompletion` output equals a reference implementation for random inputs |
| **Termination under adversarial input** | for every algorithm, applying *randomly chosen legal actions* (including always-wrong ones) terminates within `maxSteps`. **This is the test that makes wrong-choice consequences safe.** |
| **Decision integrity** | `decision.correct ∈ decision.options` on every `Decide`, for every reachable state |
| **Metrics ≡ events** | folded metrics match an independent brute-force count |
| **Rewind exactness** | `apply(a); rewind()` returns a state `==` the pre-apply state, for all reachable frames |
| **Determinism** | same seed ⇒ byte-identical dataset and trace, across runs and platforms |
| **Generator constraints** | 500 seeds per algorithm all satisfy their `DatasetSpec`; fallback rate < 1 % |
| **Challenge ≠ Watch** | generated challenge datasets provably differ in solution path, not just values |
| **Scoring tables** | every star family, boundary values included (exactly optimal, optimal+2, 2 wrong, 1 hint) |
| **Projector stability** | `CellKey` identity is preserved across every `Swap` in every trace |

### `:app`

| Test | Asserts |
|---|---|
| **`AdPolicy` table tests** | every rule and every `SuppressReason` in `PRODUCT_SPEC.md` §9, including `Result → Next` never showing an ad |
| **`PrimaryCardResolver`** | all five Home states resolve in the documented priority order |
| **`LessonReducer`** | the full guidance ladder — 1st/2nd/3rd wrong, idle nudges, mercy exit eligibility |
| **Repository tests** | Room in-memory; streak arithmetic across day boundaries, timezone changes, and freeze consumption |

### UI (Compose Test) — the vertical slice only

- End-to-end Bubble Sort: Home → Hub → Watch → Try → Challenge → Result → Code.
- Decision chips expose **identical semantics** — a test asserts no `contentDescription`,
  test tag, or role distinguishes the correct chip.
- Reduced-motion path renders and completes.
- The Challenge dataset differs from the Watch dataset (product invariant, tested in UI because
  that is where the wiring can break).

**Not in MVP:** screenshot testing (Paparazzi), macrobenchmark suites beyond the baseline
profile generator. Both are v1.1 once the design system stops moving.

---

## 12. Build and quality gates

- Version catalog is the only place dependencies are declared.
- `explicitApi()` on `:engine` — its API is a contract two other modules depend on.
- Compose compiler stability metrics on in debug.
- No `!!` outside tests; no `runBlocking` outside tests.
- `:engine` has a dependency-verification test asserting it pulls in no Android artifact.
- R8 enabled for release (the scaffold currently has `optimization { enable = false }` — this
  must be turned on before any store build).

---

## 13. Sequencing

1. **Slice 1 — Bubble Sort** (`docs/plans/01-bubble-sort-slice.md`). Home → Hub → Watch → Try →
   Challenge → Result → Code, end to end, with the engine, renderer and design system built only
   as far as this slice requires.
2. **Slice 2 — Binary Search.** The stress test. Different interaction shape entirely: range
   elimination instead of adjacent swaps, a three-option decision, and the hero
   wrong-choice consequence. **Acceptance: implementing it adds zero new `VizEvent` types, zero
   changes to `SequenceRenderer`, and zero algorithm-specific branches in `:app`.** If any of
   those three is violated, this document is wrong and gets revised before anything scales.
3. Remaining seven lessons, then Daily Challenge, then ads and analytics, then polish.
