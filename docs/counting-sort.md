# Counting Sort — the sort that does not compare

**Status:** shipped · 2026-09-10 · MVP scope is **WATCH → TRY → COMPLETE**
**Category:** Sorting · **Engine:** `engine/algorithms/countingsort/`

---

## What it is

Every other sort in the library decides order by **comparing elements to each
other**. Bubble compares neighbours, Selection compares against a remembered
minimum, Quick compares against a pivot. Counting Sort never compares two values
at all.

```
1. find the range of the values
2. make one bucket per value in that range
3. walk the array once, raising the bucket for each value
4. read the table back in order, placing each value as many times as it was counted
```

The order comes out of the table, because **a bucket's position already is its
value**. That sentence is the lesson.

## The learning objective

> Count how many times each value appears, then use those counts to rebuild the
> sorted array.

The Complete screen reports **0 comparisons**, and it is not a rounding: the run
genuinely emits no `Compare` event, and a test asserts it. That number beside a
correctly sorted array is the strongest form the argument can take.

| | asks | to decide |
|---|---|---|
| Comparison sorts | *is this bigger than that?* | where one element goes relative to another |
| Counting Sort | *how many of these are there?* | how many times a value appears in the output |

## The dataset

```
input    [4, 2, 2, 8, 3, 3, 1]      n = 7
range    1 .. 8                      k = 8
counts    1:1  2:2  3:2  4:1  5:0  6:0  7:0  8:1
output   [1, 2, 2, 3, 3, 4, 8]
```

Four properties earn it its place:

- **two values repeat**, and one of them repeats on consecutive inputs — a bucket
  going `1 → 2` is what makes a count a count rather than a yes/no flag, and it
  arrives on the fourth value, early enough to be inside the detailed part of the
  walkthrough;
- **three buckets stay empty.** 5, 6 and 7 never appear, so the rebuild has to step
  over them and *"a count of zero places nothing"* is something the learner watches
  rather than something the copy asserts;
- **the range starts at 1, not 0**, so the table is visibly `min..max` — the range
  the lesson just went and found — rather than something that happens to start
  where arrays do;
- **8 is an outlier.** Three of the eight buckets exist only because one value is
  far away from the others, which is exactly the case where counting sort stops
  being a good idea. The cost of `k` is in the picture, not only in the recap.

### TRY — a different array

```
input    [3, 1, 4, 1, 5, 3]         n = 6
range    1 .. 5                      k = 5
counts    1:2  2:0  3:2  4:1  5:1
output   [1, 1, 3, 3, 4, 5]
```

The house rule (`ADR-014`): TRY is application, not recall. The range is tighter,
the hole moved to `2`, the repeats are **not adjacent** in the input — so a
learner cannot count a run by eye and has to use the table — and the first value
counted is not the first value placed.

## Constraints, stated

- **Non-negative integers**, small range. The lesson's arrays are 6–7 values over a
  range of 5–8.
- The table spans **`min..max`**, not `0..max`: the lesson opens by finding the
  range, so the table it builds is the range it found. Indexing is `value - min`,
  and that subtraction is the **app's** — a bucket is presented as *the bucket for
  the value 3*, never as an offset, because the offset is the one part that does
  not transfer.
- **No stable-placement pass.** Real implementations accumulate the counts into
  starting positions so that equal elements keep their original order. That is a
  second idea stacked on this one, and it is not what a learner meeting counting
  sort for the first time needs. What ships is the honest core: count, then
  rebuild. `docs/` note it here rather than implementing it silently.

## Complexity

**Time O(n + k) · Space O(k)**, where `n` is the number of elements and `k` is the
width of the value range: one pass to count, one pass over the table to rebuild.

The lesson says both halves out loud. Counting Sort beats the O(n log n)
comparison sorts when `k` is reasonable, and is a bad trade the moment it is not —
a table of a million buckets to sort seven values costs a million steps to read
back. `CS_IDEA_4` is that sentence, and the teaching data is chosen so the learner
has already seen three wasted buckets by the time they read it.

## WATCH — 16 beats

Fourteen actions (seven counted, seven placed) collapse to sixteen beats: each
phase shows its shape and then stops, which is ADR-025's rule.

| # | Beat | Says |
|---|---|---|
| 0 | `SETUP` | Sort this array without comparing anything. |
| 1 | `EXAMINE` | The values run from 1 to 8. |
| 2 | `EXAMINE` | A count for each of the 8 values, all starting at 0. |
| 3 | `ADD` | 4 goes in bucket 4: 0 → 1. |
| 4 | `ADD` | 2 goes in bucket 2: 0 → 1. |
| 5 | **`ADD`** | **2 goes in bucket 2: 1 → 2.** *That is 2 of them — a bucket counts, it does not just remember.* |
| 6 | `ADD` | 8 goes in bucket 8: 0 → 1. |
| 7 | `ADD` | The last 3 values are counted the same way. |
| 8 | `PASS_COMPLETE` | The table now says how many of each value there are — and the array is not needed again. |
| 9 | `REMOVE` | Bucket 1 holds 1 — place a 1. |
| 10 | `REMOVE` | Bucket 2 holds 2 — place a 2. |
| 11 | `REMOVE` | …and the second one. That bucket is empty now. |
| 12 | `REMOVE` | The rest of the table is read the same way — empty buckets giving up none. |
| 13 | `SORTED` | Sorted. The order came out of the table. |
| 14 | `INSIGHT` | **Count how many times each value appears, then rebuild from the counts.** *0 comparisons.* |
| 15 | `SUMMARY` | 7 values, 8 buckets, one pass each way. Four ideas as bullets. |

Beat 5 is the one the collapse is designed around, and a test asserts a bucket is
shown going from one to two. The insight prints the comparison count **read from
the run**, so the claim on screen is the one the engine actually produced.

## TRY — one gesture, two questions

The learner taps a **bucket**, and does so all the way through. It is the same
table doing the same job — first being filled, then being read — and a second
control would suggest a second idea (the reasoning ADR-034 gives for refusing a
BACKTRACK button).

| Phase | Asked | Correct answer |
|---|---|---|
| **Counting** | *Which bucket counts this value?* | the bucket with the same number under it |
| **Rebuild** | *Which value goes in this slot?* | the smallest value with anything left |

Six values in, six out: **12 decisions**, every one of them the learner's.

### What the app does, and why that is not a loss

**The increment.** Once the bucket is named there is exactly one legal thing to do
to it, and tapping the only legal target teaches a gesture rather than the idea
(`PRODUCT_SPEC.md` §3). The app raises the count and says so — `count[3]: 1 → 2` —
which is the beat the brief asked to be visible.

**The offset.** `value - min` is arithmetic with one answer. The learner names a
value; the app finds the slot.

### Every wrong tap is a specific misconception

| Phase | Wrong tap | What it says |
|---|---|---|
| Counting | any other bucket | *Bucket 5 counts how many 5s there are. This value is 3.* |
| Rebuild | a bucket that counted 0 | *4 was never counted — no 4 goes in the output at all.* |
| Rebuild | a bucket already spent | *Every 1 is already placed. That bucket has nothing left to give.* |
| Rebuild | a larger value | *5 is larger than 3, and 3 still has values waiting. Taking 5 now would put it in front of them.* |

The state never moves on a wrong tap — `DecisionValidation` returns a `Retry`, and
a `Retry` carries no action (ADR-021). Two tests assert the state is byte-for-byte
identical after a run of wrong taps in each phase, and that the run still
completes correctly afterwards.

## The picture

The fifth `Scene` shape, `CountingScene` — three rows, read top to bottom:

```
INPUT    [4] [2] [2] [8] [3] [3] [1]

COUNT    [1] [2] [2] [1] [0] [0] [0] [1]
          1   2   3   4   5   6   7   8

         count[2]:  1 → 2

OUTPUT   [1] [2] [2] [ ] [ ] [ ] [ ]
```

- **the bucket carries two numbers**, and the design keeps them apart: the count is
  *in* the cell, the value it counts is printed *under* it — where an index rail
  sits in every other lesson, because that is where the eye already looks for
  "what this cell is about";
- **the rows do not share columns.** `PrefixTable` aligns its two arrays because
  `array[i]` really did produce `prefix[i + 1]`; here input position 0 has nothing
  to do with bucket 0, and drawing them in shared columns would invent a
  relationship the algorithm does not have;
- **unfilled output slots are `GHOST`** — the hole Insertion Sort established, never
  a zero pretending to be an answer;
- **a bucket that counted nothing is `ELIMINATED`**, so "place none of these" is a
  visible state rather than a cell that looks untouched;
- **the tally strip reports what just happened**, never what should happen next. A
  strip reading `count[3]: 1 → ?` while the learner is being asked which bucket
  takes a 3 would answer the question in the act of posing it (ADR-030), so before
  the first action there is no strip at all.

## Edge cases

All handled in the engine, all tested: an empty array (a finished lesson, not a
crash) · a single element · every value equal (one bucket, `k = 1`) · zero as an
ordinary value · already-sorted and reverse-sorted input · both range extremes
repeated · an action that is illegal from the current state — placing before
counting is done, or counting into a bucket outside the range — refused with the
state unchanged · adversarial driving, where every bucket is applied at every beat
and the run still terminates.

## What it cost the architecture

| | |
|---|---|
| New `VizEvent` types | **0** — `Examine`, `Meter`, `Insert`, `Finalize`, `Terminal` |
| New interaction models | **0** — `DecisionKind.CELL`, the tap the hash map established |
| New `Scene` shapes | **1** — `CountingScene`, the fifth |
| Changes to existing lessons | **0** |
| Changes to existing renderers | **0** — four dispatch sites gained a branch, none changed |
| Files added | 4 engine + 1 renderer + 1 test |

The scene shape earns its place the way the four before it did (ADR-030, ADR-033,
ADR-034): the data is a different *kind*. A sequence is one row whose slots are
positions; the count table is indexed by **value**, which is the entire idea.

## Progress

The standard MVP model: 0 % → **50 %** on WATCH → **100 %** on TRY. No challenge,
no stars, no gating — the same as every other lesson in the library.

## Category and access

**Sorting**, and free, like everything else in AlgoKing. The app has no paid tier
and no locked content (`PRODUCT_SPEC.md` §1); "Advanced" is a *category* for
techniques rather than a price (ADR-032). Counting Sort sits with the five
comparison sorts, and the teaching order puts it immediately after Quick Sort —
the comparison sorts hand over to the one that does not compare at all.

## Deferred

**CHALLENGE**, as for every lesson (`v2-challenge.md`). `ChallengeCatalog.byId`
returns null: a generated counting-sort challenge needs a constraint that keeps
the range small enough to stay readable and wide enough to have holes in it, and
that constraint has not been written.

**Radix Sort** is the natural sequel — it is counting sort applied one digit at a
time, and it is the answer to the `k` problem this lesson ends on. It is a lesson
of its own, and it needs stable placement first, which this one deliberately does
not teach.
