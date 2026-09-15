# Fibonacci — the lesson that argues for dynamic programming

**Status:** built · 2026-09-15 · MVP scope is **WATCH → TRY → COMPLETE** · unit-tested; **on-device pass still outstanding**
**Category:** Advanced (**Pro**) · **Engine:** `engine/algorithms/fibonacci/` · **Scene:** `SequenceScene` (no new shape)
**Decision:** ADR-045

---

## What it is

```
F(0) = 0
F(1) = 1
F(n) = F(n − 1) + F(n − 2)
```

Two base cases and one rule. Every Fibonacci number is the sum of the two before
it, and that is the entire definition.

The lesson is **not** about computing Fibonacci numbers. It is about what happens
when you run that rule as written, and what to do instead — which makes it the
second dynamic-programming lesson and, pedagogically, the first one to meet.
0/1 Knapsack shows a table being *used*; this one shows what it costs not to have
one.

## The problem with the obvious implementation

```
fib(n) = if n <= 1 then n else fib(n-1) + fib(n-2)
```

Every call splits into two, and the two halves overlap. Computing `F(8)`:

| | |
|---|---|
| total calls | **67** |
| numbers actually produced | **9** |
| times `F(3)` is recomputed | **8** |

Those numbers are **computed by the engine**, not written into the copy —
`naiveCallCount(n)` and `naiveCallsTo(n, k)` — and both are checked by test
against a recursion that literally counts itself. An argument a learner is
invited to distrust should be one they can check.

The cost is roughly **O(2ⁿ)**: the call tree has about `F(n)` leaves, and `F(n)`
grows exponentially. The repeated work grows exactly as fast as the answer does,
which is the same fact said twice.

## The fix, in its two shapes

| | Direction | What it does | Time | Space |
|---|---|---|---|---|
| **Memoization** | top-down | keep the recursion, write each answer down the first time, read it back after | O(n) | O(n) |
| **Tabulation** | bottom-up | never recurse; start at the base cases and build upward | O(n) | O(n) |

Both are the same idea — *never solve the same subproblem twice* — and the
walkthrough names both. **Tabulation is what the lesson makes interactive**,
because it is the one with a picture: the table *is* the algorithm, and "these two
cells make that one" is something a learner can point at. A memoization lesson
would have to draw a call tree collapsing, which is a different lesson and a scene
shape this app does not have.

## The dataset — `n = 8`

```
index   0   1   2   3   4   5   6   7   8
dp      0   1   1   2   3   5   8  13  21
```

Four properties earn it its place:

- **long enough for the repeated work to be damning.** 67 calls against nine
  numbers is an argument; at `n = 5` it is 15 calls, which reads as untidy rather
  than as a problem worth solving.
- **short enough to draw.** Nine cells fit one row on a phone without scrolling or
  shrinking, so the table stays the hero visual.
- **the values stay two digits.** `F(8) = 21` is the largest numeral on screen.
- **`dp[2] = 1 + 0 = 1` looks like nothing happened**, and `dp[3] = 1 + 1 = 2`
  arrives immediately to settle it. Starting anywhere later would skip the one
  step where the rule looks like it does not work.

### WATCH and TRY share it, and that is not the house rule

Every other lesson gives TRY fresh data so it tests application rather than recall
(ADR-014). Fibonacci cannot: **there is exactly one Fibonacci sequence**, so a
"different dataset" would only be a different place to stop in the same one, and
the numbers a learner might have memorised would be the same numbers.

What separates the two stages here is the **walkthrough**, not the data:

| | |
|---|---|
| WATCH narrates in full | `dp[2]` `dp[3]` `dp[4]`, then `dp[8]` |
| WATCH collapses to one beat | `dp[5]` `dp[6]` `dp[7]` |
| TRY asks | **all seven**, `dp[2]` through `dp[8]` |

So four of TRY's seven questions are about entries the learner was never walked
through, and none is answerable by repeating a sentence they just read. A test
pins that. It is the same call DFS, BFS and the BST made about sharing one graph
or one tree (ADR-034, ADR-036), reached from a different direction.

## WATCH — 11 beats

| # | Beat | Says |
|---|---|---|
| 0 | `SETUP` | Find F(8). *Every number is the sum of the two before it.* |
| 1 | `EXAMINE` | F(0) = 0, F(1) = 1, and after that F(n) = F(n−1) + F(n−2). |
| 2 | `EXAMINE` | **Run that rule as written and F(8) takes 67 calls.** *F(3) alone is worked out 8 separate times.* |
| 3 | `EXAMINE` | Dynamic programming: solve each one once, then reuse it. *Memoization keeps the recursion; tabulation builds up — one row, 9 cells.* |
| 4 | `EXAMINE` | dp[2] = 1 + 0 = 1 |
| 5 | `EXAMINE` | dp[3] = 1 + 1 = 2 |
| 6 | `EXAMINE` | dp[4] = 2 + 1 = 3 |
| 7 | `PASS_COMPLETE` | dp[5] to dp[7] follow the same rule: 5, 8, 13. |
| 8 | `FOUND` | dp[8] = 13 + 8 = 21. *F(8) = 21, in 9 steps and no repeated work.* |
| 9 | `INSIGHT` | **Solve each smaller problem once, and write the answer down.** *67 calls became 9.* |
| 10 | `SUMMARY` | F(8) = 21, and five recap bullets. |

**The trap lands before the fix.** Beat 2 comes before beat 3, and a test asserts
the order — a fix presented before the problem is a solution to something the
learner has not met.

**Four opening beats, not six.** They all show the same picture, because the table
is still empty, and ADR-020 says a step where nothing changed is a bug. The
recurrence rides with the base cases and the table's shape rides with the fix that
builds it, so each of the four is a genuinely separate idea. A test caps the run
of identical pictures at four.

**The middle is collapsed** by the rule ADR-025 set for the sorts: narrate the
smallest prefix that builds the model, then stop. Three entries establish the
rhythm — the base cases combining, the window sliding, and confirmation it was not
a coincidence. A fourth says nothing new.

## TRY — seven decisions, one question

> **What is `dp[i]`?** — three values, and the learner picks.

Every beat is a `Probe.Decide`. **There is no `Probe.Mechanical` anywhere in this
lesson**: the addition *is* the recurrence, so handing it to the app would leave
the learner tapping through a table that fills itself. What the app owns is the
two base cases and the cursor; what the learner owns is every value that follows.

### The wrong options are the misconceptions

For `dp[6] = 5 + 3`:

| Option | Is | Encodes |
|---|---|---|
| **8** | 5 + 3 | correct |
| 5 | `dp[i-1]` alone | the second operand forgotten |
| 10 | `dp[i-1] + dp[i-1]` | the right shape, the wrong pair |
| 2 | `dp[i-1] − dp[i-2]` | the operator misread |
| 13 | `dp[i] + dp[i-1]` | one index too far — that is `dp[i+1]` |

Three are offered per beat, drawn deterministically from that pool, and **the
correct answer's seat rotates with the index** so it is never in the same place
twice running. A test asserts that; a learner who notices the answer is always the
middle button has found a way to pass without reading the numbers.

### Wrong answers

A wrong answer is a learning event, never a state transition (ADR-021). The table
does not move, the same question stays on screen, and the ladder escalates:

| Attempt | Says |
|---|---|
| 1 | *"Look at the two lit cells again — dp[5] and dp[4]."* |
| 2 | *"Fibonacci adds the previous two values. What is 5 + 3?"* |
| 3+ | *"dp[6] = 5 + 3 = 8. Choose 8."* — and repeats |

Each wrong option additionally gets a `whyWrong` line naming what the learner
actually did — *"That doubles the previous value. The two cells are 5 and 3, not 5
and 5."* The feedback teaches the rule; it never says "Wrong".

A correct answer says what it **achieved**: *"5 + 3 = 8. dp[6] is worked out once
and never again."*

## The picture — no new scene shape

An ordinary `SequenceScene` in `ROW` layout, drawn by the sequence renderer every
array lesson already uses. A DP table indexed by **one** quantity is a row of cells
whose slots are positions, which is what that scene is. Knapsack needed
`DpTableScene` because a knapsack cell is *"the first i items, capacity c"* — two
axes. ADR-036's rule applies: **a new shape is for a new kind of data, not for a
new lesson.**

```
F(8)  ?

 0   1  [1]  _   _   _   _   _   _
 0   1   2   3   4   5   6   7   8

        dp[1] + dp[0] = 1
```

| Means | State | Reads |
|---|---|---|
| the two cells being added | `COMPARING` violet | Adding |
| the cell they just produced | `CANDIDATE` amber | New |
| not computed yet | `GHOST` | Empty |
| `F(n)`, once reached | `FINALIZED` green | Answer |
| already computed | `IDLE` | Done |

Two violet cells and an amber one, at every beat. That is the whole picture, and
it is the recurrence.

**Uncomputed cells are holes with no numeral** — the rule Insertion Sort
established and Prefix Sum reused. A `0` in an uncomputed Fibonacci cell would be
especially dishonest, because `0` is a real value in this table.

**The scene is read from the events, not the cursor.** After a value is written
the state's cursor has already moved on, so lighting the cells the cursor points
at would show the learner the *next* pair beside the sentence explaining the last
one. The projector reads `VizEvent.Insert` instead and lights the operands that
actually produced the value being described.

**The badge withholds the answer.** `F(8)` reads `?` until the table is built — an
answer already on screen is not a question (ADR-030).

### One additive scene field

`SequenceScene.equation`, defaulted to null, carrying the same `PrefixEquation`
Prefix Sum introduced and drawn by the same `EquationStrip` composable (made
`internal`). A sequence lesson whose beat *is* an arithmetic statement needs the
working on screen next to the cells it names, or the numbers in the narration are
ones the learner has no way to find.

The type's name is historical — it is a labelled two-operand line and nothing
about it is specific to prefix sums. One equation strip in the design system beats
two that drift, which is the call `SceneCell` and `SlotRow` made when that shape
arrived. **Every lesson that came before draws exactly as it did**, because the
field is defaulted and none of them sets it.

## Complexity

| | Time | Space |
|---|---|---|
| Naive recursion | **~O(2ⁿ)** | O(n) stack |
| Memoization | **O(n)** | O(n) |
| Tabulation | **O(n)** | O(n) |

The recap states all three, and the Complete screen states them again. The
one-row-at-a-time space optimisation (keeping only the last two values, O(1)
space) is deliberately **not** taught: it discards the table, and the table is
what this lesson is for. It belongs to a later lesson, if any.

## Pro positioning

Filed under **Advanced**, which is the whole of the registration. ADR-032
established that Advanced is a category rather than a second taxonomy, and
ADR-041 made the Advanced shelf the Pro shelf — so:

- **no `isPro` flag** was added to `AlgorithmEntry`;
- **nothing in the billing layer changed.** `ProAccess`, `SubscriptionRepository`,
  `PlayBillingGateway` and `PaywallScreen` are untouched apart from one copy
  string counting the shelf;
- a free learner tapping Fibonacci gets **the existing paywall**, contextual to
  Fibonacci, with no new screen;
- a Pro learner opens the lesson directly;
- `ProEntitlement.Unknown` is not entitled, as everywhere else.

The card carries the same gold **PRO** pill every other Advanced card carries —
no dimming, no padlock (DESIGN_SYSTEM.md §6.3a).

The Advanced shelf is now **twelve** lessons and the paywall copy says so. Eleven
lessons remain free and unchanged.

**Teaching order:** Fibonacci sits immediately before 0/1 Knapsack in the library
and in the `Next algorithm` chain. It is the gentler DP lesson — one axis, one
rule — and it is the one that argues DP is worth having at all; Knapsack then
spends that argument on a real choice.

## Ads

Nothing was added. Fibonacci contains no ad placement of any kind, and the
existing policy (ADR-042) applies unchanged: a free learner may see the one
interstitial on arrival at Complete, a Pro subscriber sees nothing, and `Placement`
still has exactly one member.

## Progress

The standard MVP model, with no additions: 0 % → **50 %** on WATCH → **100 %** on
TRY, independent of every other lesson and latched so it cannot go backwards.
Fibonacci gets this for free — progress is `Stage`-driven and has no per-algorithm
code anywhere (ADR-028).

## Edge cases — all tested

`F(0)` (a finished lesson, nothing to build) · `F(1)` (both base cases are the
whole table) · `F(2)` (exactly one decision) · a negative target (normalised to 0,
never a crash) · a dataset with no target at all (falls back to 8) · targets up to
25 · a wrong value applied directly (the state is legal and still terminates) ·
filling past the end (a no-op that returns the same state) · adversarial driving
over 40 action sequences · rewind exactness.

## Testing

**43 tests** in `engine/src/test/.../FibonacciTest.kt`, plus 2 in the app's
`ProAccessTest`. Engine total: **714**.

| Group | Covers |
|---|---|
| The sequence | `F(0)=0 F(1)=1 F(2)=1 F(3)=2 F(5)=5 F(8)=21`, value by value; the whole table to 20 against an **independent recursive definition** |
| Construction | base cases given not asked; one entry written per transition, in order; `dp[2]..dp[8]` |
| Source indices | always exactly `dp[i-1]` and `dp[i-2]`, at every beat |
| Calculation | the expected answer is always the sum of those two |
| Options | three distinct values, the correct one present, every wrong one explained, the seat rotates |
| Wrong answers | five in a row leave the state byte-for-byte identical; guidance escalates then holds; a `Retry` carries no action; a full run of rejected guesses still reaches the right table with zero recorded wrong decisions |
| Invalid actions | a wrong value applied directly still terminates; filling past the end is a no-op |
| Adversarial | 40 action sequences, every one terminates |
| Completion | terminal outcome, terminal event, rewind exactness |
| Repeated work | `naiveCallCount` and `naiveCallsTo` checked against recursions that count themselves, for every `(n, k)` pair up to 16 |
| The picture | ROW layout, indices shown, holes are holes, the right two cells are lit, the equation hides its result, the badge withholds the answer |
| The walkthrough | opens on the problem, closes on the idea; the trap precedes the fix; both memoization and tabulation are stated; three full beats and one collapsed; length pinned 10–16; no two adjacent steps identical; identical-picture runs capped at 4 |
| Registration | in the catalog with its own datasets; no challenge; progress 0/50/100 and latched; TRY asks entries WATCH never narrated |
| Access (`:app`) | Fibonacci is Advanced; free → paywall, Unknown → paywall, Pro → lesson; **every other lesson is exactly where it was** |

## Acceptance criteria

| | |
|---|---|
| ✓ | Registered as PRO, via the category and nothing else |
| ✓ | Free learners see the premium indicator on the card |
| ✓ | Free learners tapping it get the existing paywall |
| ✓ | Pro learners open the lesson directly |
| ✓ | WATCH is user-controlled — one NEXT, no autoplay, no timer |
| ✓ | TRY requires active reasoning at every one of seven beats |
| ✓ | Tabulation is taught as the interactive experience |
| ✓ | Repeated work in naive recursion is shown, with engine-computed numbers |
| ✓ | Memoization is named and explained |
| ✓ | Wrong answers preserve state and give contextual feedback |
| ✓ | `F(8) = 21`, asserted through the real engine |
| ✓ | Progress 0 / 50 / 100, independent |
| ✓ | No new ad placements |
| ✓ | Existing algorithms unaffected — 671 pre-existing engine tests still green |
| ✓ | All tests pass: engine 714, app 43 |
| ⚠ | **UI verified on a device — not done.** See below. |

## Not yet verified

**On a device.** No device was attached during the build, so two things are
checked by unit test only and need a real screen:

1. **Nine cells at 360dp and 320dp.** Nine is the widest row this renderer has
   been given — Counting Sort's count table is eight — and at 360dp each cell gets
   roughly 29dp. Two digits at `numeralLarge` should fit, but "should" is what a
   device pass is for. If it does not, the fix is the one Dijkstra's layout spike
   established: change the layout, never shrink below the touch minimum.
2. **The equation strip under a sequence scene.** It has only ever been drawn
   under `PrefixTable` before.

## Deferred

**CHALLENGE** — `ChallengeCatalog.byId(FIBONACCI)` returns null, as for every
Advanced lesson (`v2-challenge.md`).

**Memoization as its own lesson.** It needs a call tree collapsing as subproblems
get cached, which is a scene shape this app does not have and should not add for
one lesson. Named here, taught in the copy, not built.

**The O(1)-space variant.** Two variables instead of a row. It is a real
optimisation and it erases the picture, so it is a footnote at best and a separate
lesson at most.

**Climbing Stairs, Coin Change, House Robber.** The obvious sequels — same
one-dimensional table, different recurrence. Each is a dataset, a rule and a
narrator over machinery that now exists.
