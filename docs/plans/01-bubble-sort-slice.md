# Slice 1 — Bubble Sort, end to end

**Goal:** one algorithm working completely — Home → Hub → Watch → Try → Challenge → Result →
Code — building the engine, renderer and design system only as far as this slice needs.

**Explicit non-goal:** anything general. No second algorithm, no Daily Challenge, no ads, no
analytics, no Journey chapter bands beyond one row, no light theme. Slice 2 (Binary Search) is
what proves the architecture generalises; slice 1 only has to prove it *works*.

---

## 1. The interaction, specified exactly

### Watch dataset — authored, not generated (ADR-014)

`[7, 3, 8, 2, 5]`

Chosen because its first three comparisons are **swap → keep → swap**, which is the rhythm the
lesson needs to establish. Full trace:

| Pass | Comparisons | Result | Swaps |
|---|---|---|---|
| 1 | 7·3 swap · 7·8 keep · 8·2 swap · 8·5 swap | `3 7 2 5 [8]` | 3 |
| 2 | 3·7 keep · 7·2 swap · 7·5 swap | `3 2 5 [7 8]` | 2 |
| 3 | 3·2 swap · 3·5 keep | `2 3 [5 7 8]` | 1 |
| 4 | 2·3 keep → no swaps this pass | `[2 3 5 7 8]` | 0 |

**10 comparisons · 6 swaps · 4 passes.** Watch runs ≈ 40 s at 1×.

### The decisions

Per `PRODUCT_SPEC.md` §3 the learner never chooses which pair to compare — the app advances the
pointer. The learner decides **SWAP or KEEP**, plus exactly two special prompts:

| When | Prompt | Options | Correct | Teaches |
|---|---|---|---|---|
| every comparison | "7 and 3 — swap, or keep?" | Swap · Keep | `a[j] > a[j+1]` | the comparison rule |
| start of pass 2 | "Where does the next pass start?" | index 0 · index 1 · where we left off | index 0 | passes restart from the left |
| after a pass with zero swaps | "Do we need another pass?" | one more · we're done | we're done | the early-stop optimisation |

Pass boundaries are `Mechanical` — the app performs them, emitting `Finalize(n-1-i)` and the
insight narration.

### Insight frame

At the end of pass 1: everything dims, `8` pulses, slides to its locked slot and turns
`dataFinal`, and the line **"The largest number bubbled to the end."** holds for 1.5 s.

### Challenge — Format B, Pass Prediction

Per `PRODUCT_SPEC.md` §6, sorts use Pass Prediction, not step-driving.

> *"What does this look like after one full pass?"* — tap two tiles to swap.

Generated dataset, size 5, with constraints:
`Distinct`, `MinimumPasses(2)`, `SwapCountBetween(2, 4)`, `FirstDecisionIs(Keep)`
(diverges from Watch, which opens with a swap), `NoValueOverlapWith(watchDataset)`.

On **Check**, the app animates the real pass one comparison at a time against the learner's
arrangement, marking each slot. Scoring uses the **Accuracy** family with
`wrongDecisions = failedChecks`:

| Stars | Condition |
|---|---|
| ★★★ | correct on the first Check, no hint |
| ★★ | correct within two Checks, or one hint |
| ★ | completed |

Mastery at ★★ or better.

---

## 2. Build order

Nine steps. Each is independently verifiable; do not start the next until the previous one is
green.

### Step 1 — Project scaffolding *(~0.5 d)*

- Version catalog: add navigation-compose, kotlinx-serialization, kotlinx-collections-immutable,
  room + ksp, datastore-preferences, coroutines, junit5, turbine.
- Create `:engine` (JVM, `explicitApi()`), `:design` (Android lib), wire into `settings.gradle.kts`.
- `app/build.gradle.kts`: minSdk 26, JVM target 17, **enable R8 for release** (the scaffold ships
  with `optimization { enable = false }` — turn it on now, not before the store build).
- Bundle Archivo and JetBrains Mono variable fonts in `:design/src/main/res/font/`.

**Verify:** `./gradlew :engine:test` runs (empty), `:engine` has no Android artifacts on its
classpath (add the dependency-verification test from `ARCHITECTURE.md` §12 now, while it is cheap).

---

### Step 2 — Engine core, no algorithm yet *(~1 d)*

```
engine/core/       Action, Probe, Transition, Frame, Trace, AlgorithmRunner, Outcome
engine/event/      VizEvent (all 13), Metrics, MetricsFolder
engine/decision/   Decision, ActionOption, ConsequencePolicy
engine/narration/  NarrationKey, NarrationArg
engine/dataset/    Dataset, CellKey, DatasetSpec, Constraint
```

Write `AlgorithmRunner` against a **trivial fake algorithm** (counts to 5, one decision in the
middle) so the runner's rewind, replay and `runToCompletion` semantics are tested before any real
algorithm exists.

**Verify:** runner tests green — rewind exactness, `maxSteps` guard, metrics folding, trace
determinism.

---

### Step 3 — Bubble Sort in the engine *(~1 d)*

```kotlin
data class BubbleSortState(
    val cells: ImmutableList<DataCell>,   // value + stable CellKey, ordered by slot
    val pass: Int,
    val j: Int,
    val sortedFrom: Int,
    val swappedThisPass: Boolean,
    val stage: Stage,                     // COMPARING | PASS_END | PASS_START | DONE
)

sealed interface BubbleAction : Action {
    data object Swap ; data object Keep
    data class StartPassAt(val index: Int)
    data object OneMorePass ; data object Stop
    data object EndPass                   // mechanical
}
```

`probe`:
- `stage == COMPARING` → `Decide(swap/keep, correct = a[j] > a[j+1])`
- `stage == PASS_END && !swappedThisPass` → `Decide(oneMore/stop, correct = Stop)`
- `stage == PASS_START && pass == 1` → `Decide(startPassAt, correct = 0)`
- other pass boundaries → `Mechanical(EndPass)`
- sorted → `Terminal(Sorted)`

`apply` handles every action including wrong ones. `ConsequencePolicy = CorrectInPlace`
(`PRODUCT_SPEC.md` §5 — a wrong bubble-sort swap is tedium, not insight).

**Verify:**
- `runToCompletion([7,3,8,2,5])` produces exactly 10 comparisons, 6 swaps, 4 passes.
- Property test: 1 000 random arrays sort correctly vs `List.sorted()`.
- **Adversarial termination:** random legal actions (including always-wrong) terminate within
  `maxSteps` for 1 000 seeds. This is the test that makes wrong-choice handling safe.
- `CellKey` identity survives every swap.

---

### Step 4 — Design system foundation *(~1.5 d)*

```
design/theme/   AlgoTheme, AlgoColors (dark only), AlgoTypography, AlgoShapes, AlgoSpacing
design/motion/  Durations, Easings, StepTimeline, MotionScale, LocalReducedMotion
```

Build the full token set from `DESIGN_SYSTEM.md` §1–3 even though this slice uses about half of
it — the tokens are cheap and retrofitting them is not.

**Verify:** a preview screen rendering every token, every cell state and every type style.
This preview stays in the repo as the design reference.

---

### Step 5 — `SequenceRenderer` *(~2 d — the highest-risk step)*

```
engine/scene/     SequenceScene, Cell, CellState, PointerMark, RegionMark, Badge
engine/algorithms/bubblesort/BubbleSortProjector
design/renderer/  SequenceRenderer, CellView, PointerRail, BadgeView, SwapArc
```

Custom `Layout`, one `Animatable<Offset>` per `CellKey`, offsets read inside
`Modifier.graphicsLayer { }` (deferred read — `ARCHITECTURE.md` §7.3). Swap animates opposing
±14dp arcs.

**Verify:**
- Compose compiler metrics report every renderer composable as **restartable and skippable**.
  If `SequenceScene` reports unstable, stop and fix it here — it will not get easier later.
- A driver preview steps the real Bubble Sort trace at 1× and it reads correctly.
- Reduced-motion path renders and completes with holds preserved.

---

### Step 6 — Lesson screen: Watch and Try *(~2.5 d)*

```
app/feature/lesson/  LessonScreen, LessonViewModel, LessonReducer, LessonUiState
app/feature/lesson/playback/  PlaybackController
design/component/    NarrationStrip, DecisionChip, TransportBar, MetricTile
```

The reducer implements the guidance ladder once (`ARCHITECTURE.md` §6) — attempts 1/2/3, idle
nudges at 8 s and 20 s, mercy exit after four consecutive correct.

**Verify:**
- Watch plays, pauses, steps forward and backward, scrubs, restarts.
- Speed presets change hold durations only — a unit test asserts motion durations are constant
  across 0.5× / 1× / 2×.
- Try: correct advances; first wrong shakes and re-asks without executing; second wrong corrects
  in place with explanation; third auto-solves.
- Mercy exit appears after four consecutive correct and completes the remainder at 3×.
- Reducer unit tests cover the full ladder without any UI.

---

### Step 7 — Challenge (Format B) + generator *(~2 d)*

```
engine/dataset/   SeededGenerator, ConstraintValidator, CuratedFallbacks
app/feature/lesson/phase/ChallengeHost  (tap-to-swap arrangement + Check)
```

**Verify:**
- 500 seeds all satisfy the spec; curated-fallback rate under 1 %.
- The generated challenge dataset provably differs from `[7,3,8,2,5]` in solution path, not just
  values — asserted in an engine test, and again in the UI test where the wiring can break.
- Check animates the real pass against the prediction, marking slots one comparison at a time.

---

### Step 8 — Result and Code Reveal *(~1.5 d)*

```
engine/scoring/  Scorer (Accuracy family), VerdictBuilder
app/feature/result/  ResultScreen + the choreographed reveal
app/feature/code/    CodeScreen, assets/code/bubble_sort.{java,py}
app/data/            Room + repositories (AlgorithmProgress, ChallengeAttempt)
```

`VerdictBuilder` produces the specific line from run data — *"Two missteps, both on the same
comparison in pass 2"* — not a template pool (`PRODUCT_SPEC.md` §7).

Code Reveal ships the v1 scope only: display, Java/Python toggle, **static ◆ annotation on
line 3** (`if (a[j] > a[j+1])`) with the first-open callout. Scrubber-synced highlighting is v1.1.

**Verify:** all three star boundaries; mastery persists across process death; the ◆ callout fires
once and only once.

---

### Step 9 — Home and Hub *(~1.5 d)*

```
app/feature/home/  HomeScreen, PrimaryCardResolver
app/feature/hub/   HubScreen (StageSpine, Remember card, skip-to-Challenge)
app/nav/           Route, AlgoNavHost, BottomBar
```

Minimal but real: Home renders the primary card with all five states resolving correctly
(states 3 and 4 will have no data yet — resolver tests cover them anyway). Journey shows one
chapter band with one card.

**Verify:** the end-to-end UI test — Home → Hub → Watch → Try → Challenge → Result → Code.

**Total: ≈ 13–14 working days.**

---

## 3. Acceptance criteria for the slice

Functional:
- [ ] The full flow completes end to end without a crash or a dead end.
- [ ] Watch supports play, pause, next, previous, scrub, restart, and three speeds.
- [ ] Speed changes hold durations only; motion durations are constant (asserted by test).
- [ ] The learner never chooses which pair to compare, except at the two specified prompts.
- [ ] The guidance ladder behaves exactly as `PRODUCT_SPEC.md` §5 specifies.
- [ ] The mercy exit appears after four consecutive correct decisions and works.
- [ ] The Challenge dataset differs from the Watch dataset in **solution path**, every time.
- [ ] Stars, mastery and personal bests persist across process death.
- [ ] Code Reveal's ◆ annotation lands on the comparison line with its one-time callout.
- [ ] The whole flow works in airplane mode.

Architectural — these are the ones that matter:
- [ ] `:engine` has zero Android and zero Compose on its classpath, enforced by test.
- [ ] `:design` imports nothing from `engine.algorithms.*`.
- [ ] `SequenceRenderer` contains no reference to Bubble Sort, and no `when` over an algorithm id.
- [ ] `LessonReducer` contains no reference to Bubble Sort.
- [ ] Every renderer composable reports restartable + skippable in compiler metrics.
- [ ] Adversarial-termination test passes for 1 000 random action sequences.

Quality:
- [ ] Sustained 60 fps during Watch on a mid-range device.
- [ ] Cold start under 1.2 s with the baseline profile.
- [ ] Reduced motion preserves every hold and completes the lesson.
- [ ] TalkBack reads the array as one container with a live-region narration update per step.

---

## 4. Known risks

| Risk | Signal | Response |
|---|---|---|
| `SwapArc` looks wrong at small cell sizes | the exchange reads as a jitter rather than motion | tune arc height as a ratio of cell width, not a fixed dp; fall back to sequenced (out-then-in) motion |
| Compose recomposes the whole array per frame | jank on a mid-range device | offsets must be read in `graphicsLayer`; the metrics check at step 5 catches it early |
| Pass Prediction feels fiddly | learners mis-tap tiles | tap-to-swap with a clear selected state; if it still misses, add long-press-to-drag as an alternative, not a replacement |
| The two special prompts feel like interruptions | they break the swap/keep rhythm | they are the only prompts of their kind in the lesson; if they read badly, move both to Watch as checkpoints instead of Try decisions |
| `runToCompletion` for the mercy exit is slow | jank on tapping "finish this for me" | it is ≤ 40 steps of pure arithmetic — measure before optimising |

---

## 5. What slice 2 must prove

Binary Search is deliberately the opposite interaction: range elimination instead of adjacent
swaps, a three-option decision instead of binary, `Eliminate` and `Region` events instead of
`Swap`, **Format A** (Operate) instead of Format B, and the hero wrong-choice consequence that
Bubble Sort does not have.

Between them the two slices exercise both challenge formats, both consequence policies, and nine
of the thirteen event types.

**Hard acceptance gate for slice 2:**

1. Zero new `VizEvent` types.
2. Zero changes to `SequenceRenderer`.
3. Zero algorithm-specific branches anywhere in `:app`.

If any of those three is violated, stop and revise `ARCHITECTURE.md` before building the
remaining seven lessons. Discovering it at algorithm nine costs eight rewrites.
