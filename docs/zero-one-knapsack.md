# 0/1 Knapsack — pack a bag, get it wrong, then learn the table

**Status:** built · rewritten 2026-09-22 · MVP scope is **WATCH → TRY → COMPLETE** · unit-tested; **on-device pass still outstanding**
**Category:** Advanced (**Pro**) · **Engine:** `engine/algorithms/knapsack/` · **Scene:** `DpTableScene`
**Design plan:** [`zero-one-knapsack-plan.md`](zero-one-knapsack-plan.md) · **Decisions:** ADR-044, **ADR-053**

---

## What it is

> You have a bag with limited capacity. Each item has a weight and a value. Choose
> items to maximise total value without exceeding the capacity.

**0/1** means each item is taken once (**1**) or left (**0**). Not Fractional, not
Unbounded — and since ADR-053 both of those are *named*, as the two wrong answers to
a question the learner is asked, rather than left unmentioned.

The lesson is the first about **dynamic programming**: solve each smaller bag once,
write the answer down, and build every larger answer out of answers already written.

## The shape of the lesson — two acts

ADR-053 rewrote the teaching order. The complaint it answers is that the old lesson
showed a table of thirty cells and then explained what the cells meant, which is
*"look at this and understand it"*. The table is now the **second** act, and it
arrives as the tool for a question the learner already wants answered.

```
Act I   a bag   ->  four things  ->  it will not all fit  ->  0/1 means once
                ->  pack it by hand: 13  ->  here is a bag worth 14
                ->  16 bags to check, doubling per item
                ->  so: TAKE or SKIP, one item at a time, keep the better
Act II  dp[i][c]  ->  fill it  ->  walk back up it  ->  which items were taken
```

Nine beats pass before the word `dp` appears. **Four of the nine are questions**,
because a story told at someone is a video, which is the one thing this app is not
(ADR-020, and the same argument ADR-052 made for RSA).

| # | Act I beat | On screen | Asked? |
|---|---|---|---|
| 1 | `BAG` | an empty bag, and how much it holds | — |
| 2 | `ITEMS` | four cards, each a weight and a value | **can you take all of it?** |
| 3 | `TOO_MUCH` | the weights summed against the capacity | **how many times can one item go in?** |
| 4 | `ONCE` | every card marked `0 or 1` | **what else still fits?** |
| 5 | `PACKING` | the Camera in the bag, 1 kg left | — |
| 6 | `PACKED` | a full bag, and a second one beside it | **which is worth more?** |
| 7 | `COMPARED` | both totals: 13 against 14 | — |
| 8 | `EVERY_BAG` | 2 × 2 × 2 × 2 = 16 bags | — |
| 9 | `FORK` | TAKE and SKIP under one item | — |

Beat 6 is the one the lesson turns on. The learner has just packed the Camera and
the Watch — the obvious bag, most valuable thing first — and is shown it beside
`Laptop + Headphones`, **with both totals hidden**, and asked which is worth more.
Adding `10 + 3` against `8 + 6` is arithmetic a beginner can do, and it is the only
honest way to establish that the obvious strategy is wrong. The old lesson asserted
that in a sentence.

## The recurrence

```
dp[i][c] = the best value using only the first i items, with capacity c

dp[0][c] = dp[i][0] = 0
weight[i] > c   ->  dp[i][c] = dp[i-1][c]
otherwise       ->  dp[i][c] = max(dp[i-1][c], value[i] + dp[i-1][c - weight[i]])
```

Both sides read **the row above**. That row has never seen item *i*, so taking it
can never put it in twice — reading the same row instead is the unbounded
recurrence, and TRY refuses that tap by name. **Ties go to SKIP**, which is exactly
what backtracking reads as "not taken"; neither authored bag has one anywhere.

## The datasets — verified by enumeration

The product owner asked for Laptop / Headphones / Camera / Watch at capacity 5. That
exact set had been **rejected once before** (ADR-044) and for a real reason: it has
**two** optimal bags worth 13, `Laptop + Headphones` and `Camera + Watch`. Because
the two are disjoint, `dp[4][5]` is a tie *whatever order the rows go in*, so the
lesson's final question would mark a learner wrong on a coin flip.

**One number fixes it: Headphones 5 → 6.** Every other number, the capacity and all
four names are the brief's. The optimum becomes 14 and unique, the table has no tie
anywhere, and — as a bonus the brief wanted but its own numbers could not deliver —
taking the most valuable item first now genuinely loses.

### WATCH — Laptop 3/8 · Headphones 2/6 · Camera 4/10 · Watch 1/3, capacity 5

```
             c=0   1    2    3    4    5
 no items      0   0    0    0    0    0
 Laptop        0   0    0    8    8    8
 Headphones    0   0    6    8    8   14
 Camera        0   0    6    8   10   14
 Watch         0   3    6    9   11   14
```

- **best = 14**, `Laptop + Headphones`, and it is the only bag worth 14;
- **packed by hand = 13**, `Camera + Watch` — most valuable first, then the only
  thing that still fits;
- **the answer cell is a SKIP**: `dp[4][5]` keeps 14 against the Watch's `3 + 10`;
- **`dp[2][5]` reads `dp[1][3]` = 8**, a real answer from an earlier row — where
  dynamic programming gets its name, and the one cell whose source the learner names;
- **no ties anywhere**, so every question has exactly one right answer.

### TRY — Tent 4/10 · Water 1/2 · Rope 2/5 · Stove 3/9, capacity 5

```
             c=0   1    2    3    4    5
 no items      0   0    0    0    0    0
 Tent          0   0    0    0   10   10
 Water         0   2    2    2   10   12
 Rope          0   2    5    7   10   12
 Stove         0   2    5    9   11   14
```

Application, not recall: the same size and the same story, and the opposite shape
everywhere a memorised WATCH would mislead. Hand-packed is `Tent + Water` for 12
against `Rope + Stove` for **14**; the answer cell `dp[4][5]` is a **TAKE** rather
than a SKIP; the walk back takes two before it leaves two out, where WATCH leaves
two out before it takes two; and no item name is shared with WATCH.

### What the authored bags are held to

`KnapsackTest` re-derives all of this by brute force rather than trusting the table
above. Each is a test, and each exists because a question would otherwise have no
answer:

| Property | Why the lesson needs it |
|---|---|
| one optimal bag | two would make the last question a coin flip |
| no tie anywhere in the table | a tie marks a learner wrong for a convention |
| everything together does not fit | beat 2 has a true answer |
| no three of them fit either | "only some of it" is the honest answer, not a hedge |
| exactly one thing fits after the first pick | beat 4 has exactly one answer |
| most-valuable-first loses | beat 6 is a real question, not the same bag twice |

## WATCH — 26 beats

Nine of problem, then the table. The exact sequence is pinned by
`the walkthrough is exactly the twenty-six beats the lesson was designed as`.

| Beats | What they do |
|---|---|
| 1–9 | Act I, above |
| 10 | `dp[i][c]` defined, and row 0 and column 0 are zeros |
| 11 | row 1 in one beat — the row above is all zeros, so nothing is weighed |
| 12 | the copy-down rule: the Headphones weigh 2, so capacity 1 copies |
| 13–15 | `dp[2][2]` in full, one term per beat: SKIP, then TAKE, then the larger |
| 16–17 | `dp[2][5]` reads `dp[1][3]` = 8 — **the reuse**, and what that cell means |
| 18 | one later row summarised, once the rule is familiar |
| 19–20 | the last cell in full, and the answer: 14, leaving the Watch out |
| 21–24 | the walk back, one beat per row |
| 25 | the insight — *"Every cell is a smaller bag, solved once"* |
| 26 | the recap, five bullets |

WATCH went from 20 beats to 26. That is the cost of explaining the problem before
the method, and every added beat is a real visual change rather than a pause — a
test asserts no two adjacent steps draw the same thing.

## TRY — 13 questions

The same count as before the rewrite, redistributed from bookkeeping to
understanding.

| Phase | Count | What is asked |
|---|---|---|
| Act I | 4 | can you take all of it · how many times · what else fits · which bag wins |
| The table | 5 | one *does it fit?* · one *which cell does TAKE build on?* · three *TAKE or SKIP?* |
| The walk back | 4 | was this item taken? — one per row |

### The learner fills five cells of thirty, and that is deliberate

The old lesson asked about every cell where an item fitted. On a four-item bag that
is twenty-five questions, and answering *"TAKE or SKIP?"* twenty-five times is a
drill rather than a lesson. The rules are about **where a cell sits**, so they hold
for any dataset:

- row 1 is the app's — the row above is all zeros, so TAKE-if-it-fits has one legal
  answer;
- a cell whose item does not fit is the app's, **except the first boundary in the
  table**, where *"does it fit?"* is asked once and then becomes a rule;
- naming TAKE's cell is asked **once**, at the first cell whose TAKE reads a value
  worth more than 0 — before that the question is *"which zero?"*, which teaches
  nothing;
- **TAKE or SKIP is asked on the cell that decides each row** — the last column,
  which is where the answer is eventually read from — and on the cell where TAKE's
  source was just named;
- every row of the walk back is asked, because each one is a reading.

A test (`the learner fills five cells of thirty, not all of them`) holds that number,
so it cannot creep back up without somebody deciding to.

## The picture

**ADR-053 added no field to `DpTableScene`.** The first act is drawn with
`items`, `bag`, `focusCaption` and `choice`, all of which already existed for the
second act, and `tableVisible` was already the switch that says a table does not
exist yet. What changed is the renderer's arrangement: with no table, the caption and
the strip sit directly under the bag instead of under a grid.

One defaulted field joined `ChoiceStrip`: **`stem`**, the one thing both sides are
about. With it the same two cards draw as a **fork** — the item above, a short drop,
TAKE and SKIP below — which is how beat 9 states the recurrence before there is a
table to state it in. Every other lesson leaves it null and is untouched, the additive
move ADR-037, ADR-039 and ADR-052 all made.

| Act I beat | Drawn with |
|---|---|
| the bag filling | `BagMeter` — used / capacity, value, and what is in it |
| the four things | `ItemCard`, with `0` and `1` for the bag on screen |
| the working | `focusCaption` — `5 − 4 = 1 kg left` |
| two bags compared | `ChoiceStrip`, **both values null while asking** |
| the fork | `ChoiceStrip` with a `stem` and no values at all |

### Nothing answers a question before it is asked

While the two bags are side by side, **both totals read `?`** — a learner who can see
14 beside 13 is reading rather than adding. They appear together on the next beat,
with the winner lit. This is ADR-030's rule, applied to a comparison instead of a cell.

## Complexity

**Time O(n × W), space O(n × W)** — `(n + 1) × (W + 1)` cells, each decided once.
Stated honestly in the recap: `W` is the capacity, not the number of items, so this
is *pseudo-polynomial* rather than polynomial. Beat 8 gives the alternative its own
number: 2ⁿ, which is 16 here and over a billion at 30 items.

## Edge cases — all tested

| Case | What happens |
|---|---|
| no items | a finished lesson, not a crash — nothing is asked |
| capacity 0 | the same |
| one item that fits | Act I and the walk back; row 1 is never asked |
| one item too heavy | left out, and the table is all zeros |
| nothing fits at all | every row copies down, everything is left out |
| duplicate weights and values | handled like anything else |
| two optimal bags | the engine reports the one SKIP-on-a-tie produces |
| weight 0, negative value, duplicate names | **cannot be built** — `require` at the constructor |
| **a bag the first act's story does not fit** | the three packing beats are **skipped**, not asked with no answer |

That last row is ADR-053's own edge case. A synthetic bag where nothing fits, or
where greed already finds the optimum, cannot support *"what else fits?"* or
*"which bag is worth more?"* — so `storyHolds` is false and those beats are stated
rather than asked. The authored bags always support them, and a test proves it.

## What it cost

Nine engine and app files, no new scene shape, no new event, no new interaction
model, and no change to any other lesson. One defaulted field on `ChoiceStrip`, one
design token (`Dimens.forkDrop`), and 45 tests in `KnapsackTest` — up from 30.

## Progress and access

Unchanged: **Advanced**, so **Pro** by category with no flag to set (ADR-032,
ADR-041). Progress is 0 / 50 / 100 from the two stages, and the one interstitial on
arrival at Complete is a free learner's only ad (ADR-042).

## Not yet verified

- **No on-device pass.** Everything here is unit-tested against the engine; the
  five-row table, the item cards and the fork have not been seen on a phone. Five
  rows is one more than the old table had, and 4 × 6 cells of 44dp plus a 64dp
  gutter should still fit 360dp — but *should* is doing work in that sentence.
- The fork drop is drawn with hairline `Box`es rather than a path; it has not been
  checked against a dark background, which does not exist yet anyway.
- The first act's copy has not been read aloud by anyone but its author.

## Deferred

- **CHALLENGE**, with the rest of the stage (ADR-031). A knapsack challenge would
  generate a bag and check it against the same brute force this lesson's tests use.
- A second DP lesson that reuses the fork. The stem field is general; nothing else
  asks for it yet.
