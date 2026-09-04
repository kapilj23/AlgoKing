# Two Pointers — the first Advanced lesson

**Status:** shipped · 2026-09-04 · MVP scope is **WATCH → TRY → COMPLETE**
**Category:** Advanced · **Engine:** `engine/algorithms/twopointers/`

---

## What it is

Two cursors start at opposite ends of a **sorted** array and walk toward each
other, looking for a pair of values that adds up to a target.

```
        LEFT                          RIGHT
         ↓                              ↓
      [  1  ][  2  ][  4  ][  6  ][  8  ][ 10 ]        Target 10
```

It is a *technique* rather than a named routine, which is why it sits on the
Advanced shelf: nothing about it is a recipe to memorise, and the whole thing
collapses to one rule the learner has to actually understand.

## The rule

```
sum = values[left] + values[right]

sum >  target  →  move RIGHT left    (the only way to get a smaller sum)
sum <  target  →  move LEFT  right   (the only way to get a larger sum)
sum == target  →  pair found
left >= right  →  no pair exists
```

### Why it is sound, which is the actual lesson

Sortedness is doing all the work. When the sum is too large, `values[right]` is
the **largest value still in play** — so every pair that includes it also
overshoots. Stepping RIGHT inward does not discard one pair, it discards a whole
row of them at once. That is why the technique is linear where the obvious
nested loop is quadratic.

A learner who leaves with *"click LEFT or RIGHT"* has missed the lesson. The
narration, the guidance ladder and the completion insight all name the **reason**
rather than the button:

> *10 is the largest value left, so every pair using it overshoots. All of them
> go at once.*

## Dataset assumptions

| Assumption | Enforced by |
|---|---|
| **sorted ascending** | authored datasets; a test asserts both are sorted |
| distinct values | authored datasets (not required by the engine — duplicates work) |
| at least two values | `initial` sets `exhausted` when `size < 2`; no pair exists, and that is an answer rather than an error |
| a target sum is present | `initial` requires `dataset.target` |

Negative numbers, duplicates and a pair sitting at the two ends all work with no
special casing, and each has a test.

## WATCH

`[1, 2, 4, 6, 8, 10]`, target `10`. Ten steps, user-paced, one primary **Next**.

| # | Beat | Says |
|---|---|---|
| 1 | `SETUP` | Find two values that add up to 10. *The array is sorted — that is the only thing this technique needs.* |
| 2 | `EXAMINE` | Start at both ends. *LEFT at index 0, RIGHT at index 5.* |
| 3 | `COMPARE` | **1 + 10 = 11** · chip `11 > 10` · *greater than the target* |
| 4 | `ELIMINATE` | Move RIGHT one position left. *10 is the largest value left, so every pair using it overshoots.* |
| 5 | `COMPARE` | **1 + 8 = 9** · chip `9 < 10` · *smaller than the target* |
| 6 | `ELIMINATE` | Move LEFT one position right. *1 is the smallest value left, so no pair using it can reach the target.* |
| 7 | `COMPARE` | **2 + 8 = 10** · chip `10 = 10` · *exactly the target* |
| 8 | `FOUND` | Pair found. *2 + 8 = 10.* |
| 9 | `INSIGHT` | Each move rules out a whole row of pairs. |
| 10 | `SUMMARY` | Recap, and the four rules as bullets. |

**Each round is two steps, and the split is the pedagogy.** The sum is stated
while the pointers have *not* moved; the move comes next. Collapsing them would
show a learner a pointer that has already moved beside the reason it should move
— the wrong order to think in. The engine emits them as two transitions, so the
seam is real rather than invented by the narrator.

A test pins the order (`COMPARE` before `ELIMINATE`), that every `COMPARE` step
carries its readout chip, and that no two adjacent steps are identical (ADR-020).

## TRY

`[3, 5, 9, 11, 14, 21]`, target `17` — a different array and a different target,
so TRY is application rather than recall.

```
left=0 right=5    3 + 21 = 24  >  17    move RIGHT
left=0 right=4    3 + 14 = 17  =  17    pair found
```

Two rounds, deliberately. TRY is where the learner decides rather than reads, and
two rounds prove the rule; a longer walk is the same judgement repeated, which is
patience rather than understanding (the reasoning ADR-026 used for Merge Sort).

**The app states the sum. The learner moves the pointer.** Adding two numbers is
arithmetic the app owns (`Probe.Mechanical`); which pointer can still improve the
sum is the whole technique, so it is always a `Probe.Decide`. Nothing moves on
its own after a comparison.

### The three options, every round

`Move LEFT →` · `← Move RIGHT` · `Pair found`

All three are offered every single round, **including when "Pair found" is
wrong**. Binary Search only offers its `FOUND` option when it happens to be
correct; repeating that here would answer the very question the beat exists to
ask — *is this the pair?*

### Wrong answers

A wrong answer is a learning event, never a state transition (ADR-021). The
pointers do not move, the same decision stays on screen, and the ladder escalates:

| Attempt | Says | Gives away |
|---|---|---|
| 1 | "Look at the sum again: 11 against a target of 10." | nothing — points at the evidence |
| 2 | "The sum is too big. Which pointer can give you a smaller value?" | the reasoning shape |
| 3+ | "11 is greater than 10, so you need a smaller sum. The array is sorted, so only RIGHT can move to a smaller value. Move RIGHT left." | everything, and repeats |

Each wrong option also gets a `whyWrong` line naming what it would do to the sum
— *"Moving LEFT right makes the sum larger, and 11 is already above 10."* The
feedback teaches the rule; it never says "Wrong".

## Completion

Finishing TRY completes the algorithm. The Complete screen reports the run —
decisions, comparisons, wrong turns — with **no stars** (TRY is never scored,
`PRODUCT_SPEC.md` §2), and closes with the takeaway:

> Every move ruled out a whole row of pairs, not just one — and that only works
> because the array is sorted.

## Progress

The standard MVP model, with no additions:

| | |
|---|---|
| 0 % | not started |
| 50 % | WATCH completed |
| 100 % | WATCH + TRY completed → **Completed** on the Home card |

Two Pointers gets this for free — progress is `Stage`-driven and has no
per-algorithm code anywhere (ADR-028).

## What it cost the architecture

Nothing structural, which was the point:

| | |
|---|---|
| New `VizEvent` types | **0** — `Compare`, `Eliminate`, `MovePointer`, `Meter`, `Finalize`, `Mark` were all there |
| New `Scene` shapes or renderer branches | **0** — an ordinary `SequenceScene` in `ROW` layout |
| New `PointerId` / `MeterId` | **0** — `LO`/`HI` labelled `LEFT`/`RIGHT`, `RUNNING_SUM` for the sum |
| Changes to existing algorithms | **0** |
| New screens | **0** — `LessonPack` + `WatchScreen` + `LessonScreen` + `LessonCompleteScreen` |
| Files added | 3 engine + 1 test |

The only wiring outside the algorithm's own package is an `AlgorithmId`, a
`LessonPack`, an `AlgorithmEntry`, one `nextAlgorithm` arm and one completion
insight — which is exactly what ADR-024 promised adding a lesson would cost.

## Presentation notes

- **Both ends are `COMPARING` (violet), never `CANDIDATE` (amber).** Amber means
  a value the algorithm is *holding on to* — Selection Sort's remembered minimum.
  Two Pointers holds nothing; it looks at exactly two cells and discards one.
- **The sum appears only once the app has read the pair.** A sum printed a beat
  early answers the comparison before it is asked, the way a hash bucket showing
  its answer stops being a question (ADR-030).
- **Everything outside the window is `ELIMINATED`.** Moving a pointer discards
  every pair that value belonged to, so the collapse is honest rather than
  decorative.
- Indices are shown (`showIndices = true`), because the lesson talks about
  pointers moving one position at a time.

## Deferred

**CHALLENGE.** `ChallengeCatalog.byId(TWO_POINTERS)` returns `null` — its
challenge needs generated datasets and constraints of its own, and inventing them
to satisfy an exhaustive `when` would put an untested challenge in the catalogue
that V2 would then trust. See [`v2-challenge.md`](v2-challenge.md).

Later variations the engine is already shaped for: same-direction pointers (fast
and slow), the pair-closest-to-target variant, and removing duplicates in place.
Each is a dataset and a narrator, not a new interaction model.
