# 0/1 Knapsack — the first dynamic-programming lesson

**Status:** built · 2026-09-14 · MVP scope is **WATCH → TRY → COMPLETE** · unit-tested; **on-device pass still outstanding**
**Category:** Advanced (**Pro**) · **Engine:** `engine/algorithms/knapsack/` · **Scene:** `DpTableScene`
**Design plan:** [`zero-one-knapsack-plan.md`](zero-one-knapsack-plan.md) · **Decision:** ADR-044

---

## What it is

> You have a bag with limited capacity. Each item has a weight and a value. Choose
> items to maximise total value without exceeding the capacity.

**0/1** means each item is taken once (**1**) or left (**0**). Not Fractional, not
Unbounded, not Coin Change — none of those is mentioned.

The lesson is the first about **dynamic programming**: solve each smaller bag once,
write the answer down, and build every larger answer out of answers already written.

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
what backtracking reads as "not taken".

## The datasets — verified by enumeration

The brief's candidate (Laptop / Headphones / Camera / Watch, capacity 5) was
rejected: two optimal bags tie at 13, most-valuable-first already finds 13, and the
table has ties. Both lesson bags below were found by exhaustive search and are
re-checked by `KnapsackTest` against a brute-force enumerator.

### WATCH — Book 2/3 · Camera 3/5 · Laptop 4/7 · capacity 5

```
                 0   1   2   3   4   5
0  No items      0   0   0   0   0   0
1  Book   2/3    0   0   3   3   3   3
2  Camera 3/5    0   0   3   5   5   8
3  Laptop 4/7    0   0   3   5   7   8
```

**Book + Camera = 8**, the only optimal bag. Most-valuable-first and
best-value-per-weight both take the Laptop and get **7**. The answer cell is a SKIP —
`8` against `7 + dp[2][1] = 7` — so greedy is refuted inside the table.

### TRY — Water 2/2 · Tent 4/5 · Stove 3/4 · capacity 5

```
                 0   1   2   3   4   5
0  No items      0   0   0   0   0   0
1  Water  2/2    0   0   2   2   2   2
2  Tent   4/5    0   0   2   2   5   5
3  Stove  3/4    0   0   2   4   5   6
```

**Water + Stove = 6.** The opposite shape where memorising WATCH would mislead: the
answer cell is a **TAKE**, backtracking opens with **Taken**, the left-out item is
the middle row, and SKIP wins mid-row (`dp[3][4]`).

## WATCH — 20 beats

| # | Beat | |
|---|---|---|
| 0–3 | the bag, the 0/1 rule, greedy's Laptop (7), ask a smaller question | no table yet |
| 4 | `dp[i][c]` defined; row 0 and column 0 are zeros | |
| 5 | row 1 — only the Book | |
| 6 | the Camera doesn't fit at 1 and 2: copy the cell above | |
| 7–9 | `dp[2][3]` in full: SKIP 3 · TAKE 5 + 0 = 5 · 5 beats 3 | |
| 10–11 | `dp[2][5]` reads `dp[1][2]` = 3 — worked out once, read back | DP named |
| 12 | the Laptop row, to capacity 4 | |
| 13–14 | the last cell: SKIP 8 against TAKE 7 — greedy loses here | |
| 15–17 | walk back: Laptop out · Camera in · Book in — weight 5, value 8 | |
| 18 | **Every cell is a smaller bag, solved once.** | chip `8 > 7` |
| 19 | summary, five bullets | |

Which beats appear is decided by rules about where a cell sits, not by the dataset;
`KnapsackTest` pins the exact sequence and the numbers in it.

## TRY — 13 questions

| Question | Kind | Asked |
|---|---|---|
| *If you TAKE the Stove, which cell holds the best for the room that is left?* | tap a cell | when the item fits with room to spare, rows 2+ |
| *SKIP or TAKE?* | two buttons | every fitting cell in rows 2+, and the last no-fit before the item fits |
| *Was the Stove taken?* | two buttons | every row, walking back |

The app fills row 0, column 0 and row 1, moves between cells, does the arithmetic,
and picks TAKE's cell when the item fills the column exactly.

Every wrong answer is refused with the state unchanged and a named reason: the
same-row tap (*"could put the Stove in twice — and 0/1 means once"*), SKIP's cell,
the wrong capacity, a row too high, TAKE when it doesn't fit, the smaller bag, and
both directions of the walk back.

## The picture

`DpTableScene` — the sixth scene shape — drawn by `DpTable`. Every cell is the shared
`SceneCell`; the only new code is the grid layout.

| Means | Drawn as |
|---|---|
| not computed | an empty hairline slot |
| the cell being decided | `GHOST` |
| SKIP's cell | `CANDIDATE` amber |
| TAKE's cell | `COMPARING` violet |
| computed | `IDLE` |
| the answer and the walk back | `FINALIZED` green |

Under the table a two-card strip weighs **TAKE** (violet, first) against **SKIP**
(orange, second) — the same order and tones as the buttons beneath it. TAKE reads
`?` until its cell is chosen, and names only the weight when the item does not fit.
Item cards and a bag meter carry the problem beats and the walk back.

## Complexity

**Time O(n × W)**, **space O(n × W)** — `(n + 1) × (W + 1)` cells, each decided with
two reads. Brute force is O(2ⁿ). The one-row optimisation is deliberately not
taught: it discards the rows backtracking reads.

## Edge cases — all tested

Empty item list · capacity 0 · a single item that fits · a single item too heavy ·
nothing fits · duplicate weights · duplicate values · two optimal bags (SKIP on the
tie, deterministic, and still optimal) · invalid problems rejected at construction
(non-positive weight, negative value, negative capacity, blank or duplicate names) ·
every wrong action refused byte-for-byte · adversarial driving · rewind.

`KnapsackTest` also runs the engine against a recursive reference and a brute-force
enumerator on 5,184 generated problems.

## What it cost

| | |
|---|---|
| New `VizEvent` types | **0** |
| New `Scene` shapes | **1** — `DpTableScene` |
| New interaction models, screens, controllers | **0** |
| Changes to existing lessons or renderers | **0** — four dispatch branches added |
| New design tokens | **0** — the row gutter reuses `Dimens.compareLabelWidth` |
| Shared model additions | `Dataset.knapsack` (defaulted), `KnapsackProblem` |
| Files | 5 engine · 1 renderer · 1 test |
| Tests | 33 (engine total 671) |

## Progress and access

0 % → **50 %** on WATCH → **100 %** on TRY, independent of every other lesson.
Filed under **Advanced**, which makes it Pro with no flag (ADR-041). The paywall now
reads *eleven* advanced lessons.

## Not yet verified

- **On a device.** No device was attached during the build, so the plan's gate B
  (the table at 360dp and 320dp) and gate E (a TRY run on a phone) are still open.
  Everything above is verified by unit test only.

## Deferred

**CHALLENGE** — `ChallengeCatalog.byId` returns null, as for every Advanced lesson.
**Fractional, Unbounded, Coin Change, the one-row table** — separate lessons, if any.
