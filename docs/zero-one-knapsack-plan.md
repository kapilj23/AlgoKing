# 0/1 Knapsack — design plan

> **⚠ SUPERSEDED BY THE BUILD — 2026-09-14.** Approved with option (a) and built; the live
> description is [`zero-one-knapsack.md`](zero-one-knapsack.md) and the reasoning is ADR-044.
> The datasets, the three questions, the 13 TRY decisions, the 20 WATCH beats, the states and
> the recurrence all shipped as written. Where the build differs:
>
> 1. **The choice strip puts TAKE first (left), SKIP second** — §5.4 drew SKIP left. The strip
>    now matches the button order and tones beneath it, which §5.3's own argument requires.
> 2. **A `BeginTrace` action was added.** Without it, writing the last cell and starting the
>    walk back were one transition, and the answer cell's result beat had no scene of its own.
> 3. **No new design token.** The row gutter reuses `Dimens.compareLabelWidth` (64dp).
> 4. **Datasets live in `engine/dataset/KnapsackDatasets.kt`**, not appended to `Datasets.kt`.
> 5. **Gates B and E (on-device) have not run** — no device was attached. Gates A, C and D are
>    covered by the unit suite (engine 671, app 41, all green).

**Status:** proposed · 2026-09-14 · **design only, nothing built, no source file modified**
**Category:** Advanced · **Access:** Pro (derived from the category — ADR-041) · **Flow:** WATCH → TRY → COMPLETE
**Awaiting:** approval. §17 lists the decisions and risks worth your ruling.

---

## 1. What it teaches, and where it sits

Every lesson in the library so far is either a routine that walks data or a structure that
holds it. 0/1 Knapsack is the first lesson about **dynamic programming**: solve a small
version of the problem once, write the answer down, and build every larger answer out of
answers already written.

It is taught through a problem the learner can hold in their hands before any table appears:

> **You have a bag with limited capacity. Each item has a weight and a value. Choose items
> to maximise total value without exceeding the capacity.**

and one question, asked over and over:

> **Should I take this item, or leave it?**

| | The judgement | Driven by |
|---|---|---|
| Counting Sort | which bucket counts this value | a table indexed by value |
| Prefix Sum | what each running total is | a table built once, read many times |
| Dijkstra | which node is cheapest, and what a distance becomes | tentative answers that improve |
| **0/1 Knapsack** | **take this item, or leave it** | **a table of smaller bags, each solved once** |

Prefix Sum is the nearest ancestor — *build a table, then read it* — and Dijkstra the second:
a value that is only a claim until something beats it. The recap names neither; the Complete
screen names only the idea.

### Why "0/1"

- **1** — take the item. **0** — leave it.
- **Each item is used at most once.** No second Book, no half a Laptop.

This lesson is not Fractional Knapsack (items can be cut), not Unbounded Knapsack (items can
be taken again), and not Coin Change. None of those is mentioned in the lesson. The one place
the difference is *enforced* rather than stated is §8's source tap: building TAKE on the
learner's own row is exactly the unbounded recurrence, and it is refused with the reason.

---

## 2. The dataset — and the candidate that was rejected

### 2.1 The brief's candidate fails verification

`Laptop 3/8 · Headphones 2/5 · Camera 4/10 · Watch 1/3`, capacity 5 — every feasible subset,
enumerated:

| Bag | Weight | Value |
|---|---|---|
| **Laptop + Headphones** | 5 | **13** |
| **Camera + Watch** | 5 | **13** |
| Laptop + Watch | 4 | 11 |
| Camera | 4 | 10 |
| Laptop | 3 | 8 |
| Headphones + Watch | 3 | 8 |
| Headphones | 2 | 5 |
| Watch | 1 | 3 |
| nothing | 0 | 0 |

Three defects, any one of which disqualifies it:

1. **Two different optimal bags.** "Which items were selected?" has two correct answers, so
   backtracking would teach a tie-break rule instead of the idea.
2. **Greedy-by-value is optimal.** Camera (10) then Watch (3) = 13. The lesson could not
   show why greedy is not enough — greedy wins. (Greedy by value/weight does fail, 11.)
3. **Ties inside the table.** `dp[4][3]` and `dp[4][5]` have include = exclude, so TRY would
   ask a question with two right answers.

It is also four items: a 5 × 6 table, 20 recurrence cells, and a TRY of ~20 decisions.

### 2.2 How the replacement was found

An exhaustive search over every 3-item dataset with capacity 4–6, weights 1–capacity and
values 1–12, keeping only those where:

- the optimum is **unique** and uses **exactly two** items (a genuine combination);
- **greedy by value and greedy by value/weight both fail**, with no ratio ties;
- **no cell anywhere** has include = exclude;
- the last item's row contains all three cell kinds — **doesn't fit**, **TAKE wins**,
  **SKIP wins** — so the final row teaches the whole recurrence.

48 datasets survive. The smallest numbers are chosen, because the numbers are not the lesson.

### 2.3 WATCH

| Row | Item | Weight | Value | Value / weight |
|---|---|---|---|---|
| 1 | **Book** | 2 | 3 | 1.50 |
| 2 | **Camera** | 3 | 5 | 1.67 |
| 3 | **Laptop** | 4 | 7 | 1.75 |

**Capacity 5.**

Every feasible bag — verified by enumeration, all 2³ = 8 subsets:

| Bag | Weight | Value | |
|---|---|---|---|
| **Book + Camera** | **5** | **8** | **optimal, unique** |
| Laptop | 4 | 7 | what greedy picks |
| Camera | 3 | 5 | |
| Book | 2 | 3 | |
| nothing | 0 | 0 | |
| Book + Laptop | 6 | — | over capacity |
| Camera + Laptop | 7 | — | over capacity |
| all three | 9 | — | over capacity |

**Optimal: Book + Camera · weight 5 · value 8.**

| Strategy | Picks | Value |
|---|---|---|
| Most valuable first | Laptop (7), then nothing weighs ≤ 1 | **7** |
| Best value per weight first | Laptop (1.75), then nothing fits | **7** |
| Dynamic programming | Book + Camera | **8** |

Both greedy strategies pick the **same** item and lose by the same margin, so the lesson
refutes greedy with one sentence rather than two.

### 2.4 The WATCH table

`dp[i][c]` = the best value using **only the first i items**, with **capacity c**.

```
                 capacity →
                 0   1   2   3   4   5
0  (no items)    0   0   0   0   0   0
1  Book   2/3    0   0   3   3   3   3
2  Camera 3/5    0   0   3   5   5   8
3  Laptop 4/7    0   0   3   5   7   8
```

Cell kinds (rows 1–3, capacity 1–5):

```
Book     no-fit  TAKE    TAKE    TAKE    TAKE
Camera   no-fit  no-fit  TAKE    TAKE    TAKE
Laptop   no-fit  no-fit  no-fit  TAKE    SKIP
```

**The answer cell is a SKIP.** `dp[3][5]`: SKIP keeps `dp[2][5] = 8`; TAKE gives
`7 + dp[2][1] = 7 + 0 = 7`. The Laptop — greedy's pick — leaves 1 capacity that nothing can
use. The refutation of greedy is *inside the table*, not a separate argument.

**The hero TAKE reuses a subproblem.** `dp[2][5]`: TAKE gives `5 + dp[1][2] = 5 + 3 = 8`.
`dp[1][2]` was worked out in row 1 and is simply read back — that is the moment dynamic
programming is named.

**Overlapping subproblems, on this table** (a cell read by two later cells):

| Cell | Read by SKIP of | Read by TAKE of |
|---|---|---|
| `dp[1][2]` | `dp[2][2]` | `dp[2][5]` |
| `dp[1][1]` | `dp[2][1]` | `dp[2][4]` |
| `dp[2][1]` | `dp[3][1]` | `dp[3][5]` |

### 2.5 Backtracking, on the WATCH table

```
at dp[3][5] = 8,  row above dp[2][5] = 8  → same   → Laptop LEFT OUT,  capacity stays 5
at dp[2][5] = 8,  row above dp[1][5] = 3  → higher → Camera TAKEN,     capacity 5 − 3 = 2
at dp[1][2] = 3,  row above dp[0][2] = 0  → higher → Book TAKEN,       capacity 2 − 2 = 0
```

**Book + Camera, weight 5, value 8** — agrees with the enumeration.

### 2.6 TRY — a different bag

| Row | Item | Weight | Value | Value / weight |
|---|---|---|---|---|
| 1 | **Water** | 2 | 2 | 1.00 |
| 2 | **Tent** | 4 | 5 | 1.25 |
| 3 | **Stove** | 3 | 4 | 1.33 |

**Capacity 5.**

| Bag | Weight | Value | |
|---|---|---|---|
| **Water + Stove** | **5** | **6** | **optimal, unique** |
| Tent | 4 | 5 | what most-valuable-first picks |
| Stove | 3 | 4 | |
| Water | 2 | 2 | |
| nothing | 0 | 0 | |
| Water + Tent | 6 | — | over capacity |
| Tent + Stove | 7 | — | over capacity |
| all three | 9 | — | over capacity |

```
                 0   1   2   3   4   5
0  (no items)    0   0   0   0   0   0
1  Water  2/2    0   0   2   2   2   2
2  Tent   4/5    0   0   2   2   5   5
3  Stove  3/4    0   0   2   4   5   6

Stove    no-fit  no-fit  TAKE    SKIP    TAKE
```

Backtracking: `dp[3][5]=6 ≠ dp[2][5]=5` → **Stove taken**, capacity 2 ·
`dp[2][2]=2 = dp[1][2]=2` → **Tent left out** · `dp[1][2]=2 ≠ dp[0][2]=0` → **Water taken**.

### 2.7 Why WATCH and TRY differ — and how

**Different datasets.** The house default (ADR-014), and here it matters more than usual:
three items make an answer a learner can remember ("Book and Camera, eight"), and the whole
TRY would then be recall. The two bags are the same *shape* — three items, capacity 5, a
4 × 6 table — so the screen, the table footprint and the recurrence are identical and only
the reasoning is new.

| | WATCH | TRY |
|---|---|---|
| The answer cell | **SKIP** — the last item is left out | **TAKE** — the last item goes in |
| Backtracking starts with | LEFT OUT | TAKEN |
| The left-out item | the last row (Laptop) | the middle row (Tent) |
| SKIP wins | only at the answer cell | mid-row (`dp[3][4]`), not at the answer |
| Row order by weight | 2, 3, 4 | 2, 4, 3 |

A learner who absorbed *"the last row skips"* or *"backtracking starts by leaving something
out"* from WATCH is wrong in TRY, and finds out by reading the table.

Honest note: in TRY, greedy by value/weight happens to find the optimum (Stove, then Water).
That does not matter — greedy is refuted once, in WATCH, where both strategies fail, and TRY
never mentions greedy.

---

## 3. The state and the recurrence

```
dp[i][c] = the best value using only the first i items, with capacity c

dp[0][c] = 0                      no items, nothing to gain
dp[i][0] = 0                      no room, nothing fits

if weight[i] > c:                 the item cannot go in
    dp[i][c] = dp[i-1][c]

otherwise:
    exclude = dp[i-1][c]                          SKIP — the best without this item
    include = value[i] + dp[i-1][c - weight[i]]   TAKE — this item, plus the best of what is left
    dp[i][c] = max(exclude, include)
```

**Why the row above, on both sides.** `dp[i-1][…]` has not considered item *i*, so adding
item *i* to it can never pack it twice. Reading `dp[i][c - w]` instead is the Unbounded
Knapsack recurrence — the single most important wrong answer in TRY (§8).

**Why `max`.** Each cell is one decision with two outcomes the table has already priced.
The bag worth more is the one worth keeping.

**The table is (n + 1) × (W + 1)**: one row for "no items yet" plus one per item, and one
column for "no room" plus one per unit of capacity. That extra row and column are what make
the two base cases cells rather than special cases — the same reason Prefix Sum's table
carries a leading zero (ADR-033). Here: 4 × 6 = 24 cells, 15 computed.

**Ties.** The authored datasets contain none (§2.2). The engine still has a rule, and it is
**SKIP on a tie**: the cell keeps the row above, which is also exactly what the standard
backtrack reads as "not taken" (`dp[i][c] == dp[i-1][c]`). One rule, used in both
directions, and tested.

---

## 4. WATCH — 20 beats

User-paced, one **Next**. Engine transitions run per cell (§9); the narrator collapses the
repetitive ones, which is ADR-025's rule applied to a table: show the shape, then stop.

| # | Kind | Headline | Support / what changed |
|---|---|---|---|
| 0 | `SETUP` | Pack the most value into a bag that holds 5. | Three items, no table. The bag meter reads `0 / 5`. |
| 1 | `EXAMINE` | Each item goes in once, or not at all. | *1 means take it, 0 means leave it — that is the 0/1 in the name.* Each item card shows its 0 / 1. |
| 2 | `COMPARE` | Take the most valuable first? The Laptop, worth 7. | *It weighs 4, so 1 capacity is left — and nothing weighs 1. Is 7 the best this bag can do?* The bag holds the Laptop. |
| 3 | `EXAMINE` | Ask a smaller question instead. | *What is the best value using only the first few items, with a smaller bag?* The bag empties; the table appears, every cell a hole. |
| 4 | `EXAMINE` | `dp[i][c]`: the best value using the first i items, with capacity c. | *No items, or no room, is worth 0.* Row 0 and column 0 fill with zeros. |
| 5 | `ADD` | Row 1 — only the Book. | *0 while it doesn't fit, then 3 from capacity 2 up.* Row 1 fills. |
| 6 | `EXAMINE` | The Camera weighs 3. At capacity 1 and 2 it can't go in. | *A cell it can't fit in copies the cell above: the best without it.* `dp[2][1]`, `dp[2][2]` fill from above. |
| 7 | `KEEP` | Capacity 3. SKIP the Camera: keep `dp[1][3]` = 3. | The SKIP cell lights amber; the choice strip shows SKIP 3. |
| 8 | `COMPARE` | TAKE the Camera: 5 + `dp[1][0]` = 5. | *The Camera, plus the best of the 0 capacity left.* The TAKE cell lights violet; chip `5 > 3`. |
| 9 | `ADD` | 5 beats 3, so `dp[2][3]` = 5. | The cell fills. |
| 10 | `COMPARE` | Capacity 5. TAKE the Camera, and 2 capacity is left. | ***`dp[1][2]` already knows the best for 2 — the Book, 3. It was worked out once, and is read back.*** (Capacity 4 filled silently on the way.) |
| 11 | `ADD` | 5 + 3 = 8 beats 3. `dp[2][5]` = 8. | *That cell means: Book and Camera together.* |
| 12 | `EXAMINE` | The Laptop weighs 4 — it copies down until capacity 4, where 7 beats 5. | Row 3 fills to capacity 4. |
| 13 | `COMPARE` | The last cell. SKIP keeps 8. TAKE gives 7 + `dp[2][1]` = 7. | Both cells lit; chip `8 > 7`. |
| 14 | `KEEP` | 8 beats 7 — leave the Laptop out. | ***This is where taking the most valuable item first loses: the Laptop leaves room nothing can use.*** |
| 15 | `ELIMINATE` | The best bag is worth 8. But which items? | *Walk back up. dp[3][5] and dp[2][5] are both 8 — the Laptop changed nothing, so it was left out.* |
| 16 | `ADD` | `dp[2][5]` = 8, but `dp[1][5]` = 3. The Camera made the difference. | *Camera in. 5 − 3 = 2 capacity left.* |
| 17 | `FOUND` | `dp[1][2]` = 3, `dp[0][2]` = 0. The Book is in. | *Book + Camera: weight 5, value 8.* The bag fills. |
| 18 | `INSIGHT` | **Every cell is a smaller bag, solved once.** | *Greedy took the Laptop and got 7. The table found 8.* |
| 19 | `SUMMARY` | 3 items, capacity 5, 24 cells. | Five bullets (below). |

Summary bullets:

1. 0/1: every item is taken once, or left.
2. `dp[i][c]` is the best value using the first i items with capacity c.
3. SKIP keeps the cell above. TAKE adds the item to the row above, at the capacity left. Keep the larger.
4. Walk back up: where a cell differs from the one above, that item was taken.
5. **O(n × W)** time and space — one cell per item and capacity, each decided once.

### What the collapse keeps and drops

| Engine transitions | Beats | Why |
|---|---|---|
| intro (4) + base fill (1) | 5 | the problem before the method (brief §5 steps 1–6) |
| row 1 (15 transitions) | 1 | exclude is always 0, so there is nothing to compare |
| row 2 no-fit (2 cells) | 1 | the copy-down rule, stated once on a real row |
| `dp[2][3]` in full | 3 | the recurrence, one term per beat: SKIP, TAKE, max |
| `dp[2][4]` | 0 | the same shape as `dp[2][3]` — shown only as a filled cell |
| `dp[2][5]` | 2 | the first TAKE that reuses a non-zero subproblem — DP named |
| row 3, capacity 1–4 | 1 | the rule, now familiar |
| `dp[3][5]` | 2 | the answer, and the refutation of greedy |
| backtracking (3) | 3 | one beat per row, because each row is one reading |

A pinned test asserts exactly these 20 beats, that adjacent steps always differ (the
`WatchScriptTest` rule — scene, comparison readout or recap), and that beat 7's SKIP value
and beat 8's TAKE value are both read from the engine rather than written in copy.

---

## 5. The picture — `DpTableScene`

### 5.1 Why a sixth scene shape

The five shapes each earned their place by being a different *kind* of data (ADR-030, 033,
034, 036, 040). A DP table is one more:

| Shape | Indexed by | Fails for Knapsack because |
|---|---|---|
| `SequenceScene` | position | one index; the table has two |
| `PrefixScene` | two rows sharing columns | two rows, fixed; no row relationship beyond `i → i+1` |
| `CountingScene` | three independent rows | rows share nothing; here every row reads the one above at two columns |
| `BucketScene` | hashed key | no order at all |
| `GraphScene` | positions in 2-D | nodes and edges; no grid, no headers |

The relationship the lesson lives in — **a cell reads the row above, straight up and
`weight` columns to the left** — is a property of a grid with two meaningful axes, and no
existing shape can draw it. `SequenceScene`'s `GRID` layout is a wrapping flow of boxes, not
a table: its rows mean nothing.

**Scope is kept to what the renderer must draw.** The scene has no knapsack vocabulary in
its fields — rows, columns, headers, cells, a two-sided choice strip — so a later DP lesson
could reuse it, but nothing is added for a lesson that does not exist.

### 5.2 Contents

```
DpTableScene(
    rowHeaders:    one per row     label ("Book"), detail ("w2 · v3"), state
    columnHeaders: one per column  label ("0".."5"), state; axis caption "Capacity"
    cells:         rows × columns of the existing Cell / CellState
    choice:        ChoiceStrip?    two sides: caption, formula, value (null = "?"), chosen
    focusCaption:  String?         "dp[2][5] · Book, Camera · capacity 5"
    items:         item cards      PROBLEM phase and backtracking: name, weight, value, 0/1, state
    bag:           BagMeter?       used / capacity, contents
    tableVisible:  Boolean         false during the PROBLEM beats
    badge, meters, legendLabels    as every other scene
)
```

Selectable slot for a `CELL` decision: `row × (W + 1) + column`.

### 5.3 States — every one already exists

| Means | Treatment | From |
|---|---|---|
| not computed yet | `GHOST` — a hole, no numeral | Insertion Sort's gap |
| **the cell being decided** | `GHOST` + selected outline, header row and column in `primary` | Counting Sort's unfilled output slot |
| computed | `IDLE` — surface, `borderStrong` hairline, numeral | every array |
| **SKIP's cell** (`dp[i-1][c]`) | `CANDIDATE` amber — the value being *kept* | Selection Sort's remembered minimum |
| **TAKE's cell** (`dp[i-1][c-w]`) | `COMPARING` violet | every comparison |
| the answer `dp[n][W]` | `FINALIZED` green | "final" everywhere |
| the backtracking path | `FINALIZED` green, one cell per row | |
| item: current row | violet outline on its card / header | |
| item: taken | `FINALIZED` green, 1 | |
| item: left out | `ELIMINATED`, 0 | |

**Amber is SKIP, violet is TAKE — and that is the decision buttons' own pairing.**
`DecisionTone.forIndex` already draws the first option violet and the second orange, the
SWAP / KEEP pair of DESIGN_SYSTEM.md §6.9. TAKE is first and SKIP second, so the cell a side
builds on is the colour of the button that chooses it. No new colour, no new token.

Legend labels (via `legendLabels`, the existing rename seam): `GHOST` *Not yet* ·
`CANDIDATE` *Skip keeps* · `COMPARING` *Take builds on* · `IDLE` *Solved* ·
`FINALIZED` *Answer*.

**Nothing is shown twice.** During the build the row headers *are* the items, so there is no
separate item strip; the item cards return only for backtracking, where each flips to 1 or 0.

### 5.4 The choice strip — TAKE / SKIP made visible

```
┌─ SKIP ─────────────┐   ┌─ TAKE ───────────────────┐
│ dp[2][5]           │   │ 7 + dp[2][1]             │
│ 8                  │   │ 7 + 0 = 7                │
└────────────────────┘   └──────────────────────────┘
      amber caption              violet caption
```

Two `surfaceVariant` cards side by side, each a `labelSmall` caption over a formula and a
`numeralMedium` value — `PrefixTable`'s `EquationStrip`, twice.

- **An answer on screen is not a question** (ADR-030). While the learner is picking TAKE's
  cell, the TAKE side reads `7 + dp[?][?]` and `?`. When the item does not fit, the TAKE side
  states the weight — *Laptop weighs 4* — and the column header states the capacity; the
  comparison is the learner's.
- After the decision the chosen side takes its full gradient and the other side mutes, and
  the value lands in the cell.

This strip is the brief's §7 "two conceptual paths", drawn from engine state rather than
illustrated: SKIP → keep the previous best; TAKE → gain the value, solve what is left.

### 5.5 The PROBLEM beats

Beats 0–3 show **no table**. Three item cards (a `surface` card each: name, weight, value,
and later a 0 / 1 pill) above a bag meter — a `surfaceMuted` track filling with the item
accent, `used / capacity` in `numeralMedium`. Beat 2 puts the Laptop in the bag; beat 3
empties it and the table fades in. The learner meets the problem before the method.

No emoji: the design system's learning surfaces carry text and glyphs, not emoji
(DESIGN_SYSTEM.md §6), so the brief's 📷 becomes the word *Camera*.

### 5.6 Fitting a phone — measured, not hoped

| | 360dp phone | 320dp phone |
|---|---|---|
| inside card padding | 296dp | 256dp |
| row header column | 64dp | 56dp |
| 6 capacity columns, 4dp gaps | **34.7dp each** | 28.0dp each |
| row height | 44dp (`sceneCellHeight`) | 44dp |
| table height (4 rows + header) | ~208dp | ~208dp |

At 360dp a cell is **wider than Counting Sort's shipped buckets** (8 across, 33.5dp). Every
value in both datasets is a single digit. **No horizontal scroll and nothing is shrunk** —
`sceneNumeral` stays as it is. 320dp is tight and is gate B's job (§14).

---

## 6. TRY — 13 decisions, three questions

One run through the TRY table and then back up it. Every decision is the learner's; the app
performs only what has one legal answer (PRODUCT_SPEC.md §3).

### 6.1 The three questions

| # | Question | Kind | Answered by |
|---|---|---|---|
| **A** | *If you TAKE the Stove, which cell holds the best for what is left?* | `CELL` | a tap on the table |
| **B** | *SKIP or TAKE?* | `OPTIONS` — **TAKE** · **SKIP** | two equal-weight buttons |
| **C** | *Was the Stove taken?* (backtracking) | `OPTIONS` — **Taken** · **Left out** | two equal-weight buttons |

**A** is the one that tests the state meaning and the 0/1 constraint, and it is why TRY is
not a max-of-two-numbers quiz. The learner has to know that TAKE builds on **the row above**
(0/1) at **capacity − weight** (the capacity constraint). Dijkstra's arithmetic is the app's;
here, *where* the arithmetic reads from is the judgement.

**B** tests `max(include, exclude)` and — at the boundary cell — whether the item fits.

**C** tests that the final value alone is not the answer. It is the same question as B,
asked backwards: which choice did this cell record?

### 6.2 What the app does

| The app | Why |
|---|---|
| fills row 0 and column 0 | definition, not judgement |
| fills **row 1** | exclude is always 0, so TAKE-if-it-fits has one legal answer |
| moves to the next cell | bookkeeping |
| no-fit cells **except the last one before the item fits** | the copy-down rule, once per row is the judgement; five times is a gesture |
| picks TAKE's cell when **c = weight** | the remaining capacity is 0, and column 0 is definition |
| adds `value + dp[i-1][c-w]` and says it | arithmetic with one answer; the learner still chose where it reads from |
| writes the winning value into the cell | a consequence of B |
| moves up a row and subtracts the weight during backtracking | arithmetic |

### 6.3 The run, decision by decision

```
Water (row 1)  app fills: 0 0 2 2 2 2

Tent  (row 2, weight 4)
  c3   B  no fit — SKIP                              the boundary: 4 > 3
  c4   B  TAKE 5+0=5  vs  SKIP 2        → TAKE       (A is the app's: c = weight)
  c5   A  tap dp[1][1]
       B  TAKE 5+0=5  vs  SKIP 2        → TAKE

Stove (row 3, weight 3)
  c2   B  no fit — SKIP                              the boundary: 3 > 2
  c3   B  TAKE 4+0=4  vs  SKIP 2        → TAKE
  c4   A  tap dp[2][1]
       B  TAKE 4+0=4  vs  SKIP 5        → SKIP       the mid-row SKIP
  c5   A  tap dp[2][2]
       B  TAKE 4+2=6  vs  SKIP 5        → TAKE       the answer cell

Back up
  row 3  C  6 ≠ 5  → Taken     capacity 5 − 3 = 2
  row 2  C  2 = 2  → Left out
  row 1  C  2 ≠ 0  → Taken
```

**13 decisions:** 3 source taps, 5 TAKE / SKIP, 2 boundary no-fits, 3 backtracking — against
Counting Sort's 12 and Dijkstra's 8. The two levers, if it runs long on a device, are in
§17.3.

### 6.4 Every wrong answer is a named misconception

A wrong answer never moves the table (ADR-021): `DecisionValidation` returns a `Retry`, a
`Retry` carries no action, and the ladder escalates — point at the evidence, ask the
reasoning question, then state it plainly and repeat.

**A — which cell TAKE builds on.** Every computed cell is tappable; a shortlist would do the
reasoning.

| Tapped | Says |
|---|---|
| **the same row**, `dp[3][2]` | *Row 3 is allowed to use the Stove already. Building on it could put the Stove in twice — and 0/1 means once. TAKE builds on the row above.* |
| SKIP's cell, `dp[2][5]` | *That is SKIP's cell — no room used. Taking the Stove uses 3, so TAKE starts from less room.* |
| the row above, wrong capacity | *Taking the Stove leaves 5 − 3 = 2 capacity, not 1.* |
| two rows up | *Row 1 has never seen the Tent. The row directly above is the best of everything before the Stove.* |
| anywhere else | *TAKE starts from the row above, at the capacity the Stove leaves behind.* |

The first line is the unbounded-knapsack recurrence, refused with its reason — the 0/1
constraint enforced by a tap rather than asserted by a sentence.

**B — TAKE or SKIP.**

| Situation | Wrong choice | Says |
|---|---|---|
| doesn't fit | TAKE | *The Stove weighs 3, but only 2 capacity is available. It cannot go in.* |
| include > exclude | SKIP | *Skipping keeps 2. Taking gives 4 + 0 = 4. Which choice keeps the better bag?* |
| exclude > include | TAKE | *Taking gives 4 + 0 = 4, but skipping keeps 5 — a better bag without the Stove. The cell keeps the larger.* |

**C — was it taken.**

| Situation | Wrong choice | Says |
|---|---|---|
| equal to the row above | Taken | *`dp[2][2]` and `dp[1][2]` are both 2. The Tent row changed nothing, so the Tent was left out.* |
| differs from the row above | Left out | *`dp[3][5]` is 6 but `dp[2][5]` is 5. That extra 1 only exists if the Stove went in.* |

Correct answers say what they achieved, never *Correct!* —
*"4 + 2 = 6 beats 5: Water and the Stove together beat the Tent alone."*

---

## 7. COMPLETE

The run's metrics — decisions, comparisons, wrong turns — and the takeaway. No stars
(PRODUCT_SPEC.md §2).

> Every cell asks one question — take this item, or leave it — and answers it by reading two
> smaller bags that are already solved. That is dynamic programming: each smaller problem is
> worked out once and reused. The table is (n + 1) × (W + 1), so it costs O(n × W).

---

## 8. Complexity

| | |
|---|---|
| Time | **O(n × W)** — (n + 1)(W + 1) cells, each decided with two reads and one comparison |
| Space | **O(n × W)** — the whole table, which is also what makes backtracking possible |
| Brute force | **O(2ⁿ)** — every subset; 3 items is 8 bags, 30 items is over a billion |

Stated in the summary bullet and the Complete screen, and nowhere as a paragraph.

**Not taught:** the one-row space optimisation. It discards exactly the rows backtracking
reads, and a first DP lesson should keep the table the learner just watched being built.
Recorded here so its absence is a decision rather than an omission. Also not taught:
pseudo-polynomial time — O(n × W) is honest as stated, and W being a *value* rather than a
size is a second lesson.

---

## 9. Engine architecture

### 9.1 Files

```
engine/algorithms/knapsack/
├── Knapsack.kt                KnapsackState, KnapsackAction, KnapsackAlgorithm
├── KnapsackProjector.kt       -> DpTableScene
└── KnapsackWatchNarrator.kt   the 20 beats
engine/scene/DpTableScene.kt   the sixth shape
engine/core/Knapsack.kt        KnapsackItem, KnapsackProblem (validated at construction)
engine/dataset/Datasets.kt     + KnapsackDatasets { watch, tryIt }
```

`Dataset` gains **one defaulted field**, `knapsack: KnapsackProblem? = null` — the additive
move `graph`, `tree` and `targetNode` each made. No existing lesson reads it.

`KnapsackProblem` **rejects a non-positive weight, a negative value, a negative capacity and
duplicate or blank names at construction** — `Graph.weightsOf`'s rule (ADR-039): a dataset
the lesson cannot teach must be impossible to author.

### 9.2 The state — the single source of truth

```
KnapsackState(
    problem:   items, capacity
    phase:     PROBLEM | BUILD | TRACE | DONE
    intro:     ITEMS | RULE | GREEDY | SUBPROBLEM        (PROBLEM only)
    table:     (n+1) × (W+1) of Int?                     null = not computed
    row, col:  the cell being decided                    (BUILD)
    focused:   Boolean                                   the cell has been opened
    source:    (row, col)?                               TAKE's cell, once chosen
    lastChoice: TAKE | SKIP ?                            for the strip and the narration
    traceRow, traceCap                                   (TRACE)
    taken:     List<Boolean?>                            per item, filled by backtracking
)
```

**Derived, never stored:** the current item · `fits` · `exclude` · `include` · the correct
TAKE / SKIP · the correct source cell · `best = table[n][W]` · the selected items · the bag's
weight · greedy's pick (for beat 2) · the number of subsets. Nothing is stored that could
disagree with the table.

**No Compose code adds, compares or indexes anything.** The UI renders the scene and sends
an action.

### 9.3 Actions

| Action | Probe | What it does |
|---|---|---|
| `Introduce(beat)` | Mechanical | advances the PROBLEM beats; greedy's bag is computed here, in the engine |
| `FillBase` | Mechanical | row 0 and column 0 become 0 |
| `Focus` | Mechanical | opens the next cell: highlights it and SKIP's cell |
| `PickSource(row, col)` | **Decide (CELL)** or Mechanical | fixes TAKE's cell; refused unless it is `(i-1, c-w)` |
| `Take` | **Decide (OPTIONS)** or Mechanical | writes `include`; refused unless the item fits **and** TAKE is the max |
| `Skip` | **Decide (OPTIONS)** or Mechanical | writes `exclude`; refused unless SKIP is the max |
| `MarkTaken` / `MarkLeftOut` | **Decide (OPTIONS)** | records the item, moves up a row; refused unless it matches the table |

### 9.4 The machine

```
probe(state):
    PROBLEM, beats left              -> Mechanical(Introduce)
    PROBLEM, done                    -> Mechanical(FillBase)
    no items, or capacity 0          -> Terminal(Completed)         after FillBase
    BUILD, cell not focused          -> Mechanical(Focus)
    BUILD, item does not fit
        row ≥ 2 and c = weight − 1   -> Decide(TAKE / SKIP)         the boundary
        otherwise                    -> Mechanical(Skip)
    BUILD, fits, no source yet
        row ≥ 2 and c > weight       -> Decide(which cell?)
        otherwise                    -> Mechanical(PickSource)
    BUILD, fits, source chosen
        row ≥ 2                      -> Decide(TAKE / SKIP)
        row 1                        -> Mechanical(the correct one)
    last cell written                -> phase TRACE
    TRACE, row ≥ 1                   -> Decide(Taken / Left out)
    TRACE, row 0                     -> Terminal(Completed)
```

**Which beats the learner answers is a rule in the engine, not a list of cells.** The rules
are the ones in §6.2, and they hold for any dataset: the same way Dijkstra's "a node at ∞ is
not asked" and the traversals' "visit on arrival" are rules rather than authored positions.
No `autoInTry` is used: a beat the learner is not taught is `Mechanical`, which is what the
controller already auto-applies.

**One machine, two stages, no mode flag.** WATCH is `runToCompletion()`, which takes
`decision.correct` at every `Decide`; TRY's `LessonController` auto-applies every
`Mechanical` and waits at every `Decide`. This is how the other twenty-one lessons work, so
**no controller, screen or validation change** is needed.

**A wrong action is refused**, returning the state unchanged with `correct = false` — the
pattern Dijkstra, AVL, the BST and the traversals use. It is the stronger choice here than
Counting Sort's (which applies a wrong count): a wrong value in one cell would silently
poison every cell that reads it, and the table would stop being a real run of the algorithm.

### 9.5 Events — zero new types

`Examine` (the two source cells) · `Compare` (include against exclude — counted in
`Metrics.comparisons`, so COMPLETE's number is true) · `Meter` (the bag's weight during
backtracking) · `Finalize` (the answer cell) · `Terminal`. All thirteen already exist.

### 9.6 Termination

Every action advances `intro`, the cell cursor, `focused`, `source`, or `traceRow`, and each
is bounded by n × W. The adversarial test applies **every** action at **every** beat and
asserts the run still ends on the correct table and bag.

---

## 10. UI architecture — what is reused, what is new

### 10.1 Reused unchanged

`LessonPack` · `LessonController` · `WatchScreen` · `LessonScreen` · `LessonCompleteScreen` ·
`WatchScriptBuilder` · `DecisionValidation` and the ladder · `DecisionButton` +
`DecisionTone` · `SceneCell` and all seven `CellState` treatments · `SceneLegend`
(via `legendLabels`) · `SceneMeters` · `StageStepper` · progress · navigation · the paywall
gate · every design token.

### 10.2 New

| | |
|---|---|
| `DpTableScene` | the scene (§5) |
| `DpTable` composable | a header column + header row + `SceneCell` grid, the choice strip, the item cards and the bag meter |
| four `when (scene)` branches | `SceneRenderer`, `SceneMeters`, `SceneLegend` ×2 — the compiler finds all four, exactly as it did for `CountingScene` |

### 10.3 Is a new grid component actually needed? — **Yes, one, and only the layout**

Checked against every renderer in `ui/components/`:

- `SequenceRenderer`'s `GRID` layout wraps a flow of boxes; its rows carry no meaning and it
  has no headers.
- `PrefixTable` and `CountingTable` stack independent **one-dimensional** rows with captions;
  neither has columns that mean something across rows.
- `BucketTable` is five rows of chips.

None can say *"this cell is row 2, capacity 5, and it reads row 1 at capacity 2"*. What is new
is **layout only**: every cell is the existing `SceneCell`, every strip is the existing
`surfaceVariant` card with `labelSmall` over `numeralMedium`, and every colour is an existing
viz token. Nothing in the design system changes; DESIGN_SYSTEM.md gains §6.16i describing the
table, the same way §6.16h arrived with Counting Sort.

### 10.4 Wiring outside the package

The same touchpoints every lesson has: `AlgorithmId.ZERO_ONE_KNAPSACK` · a `LessonPack` and
its `byId` arm · `ChallengeCatalog.byId → null` · an `AlgorithmEntry` with
`category = "Advanced"` (which **is** the Pro lock — no flag) · a `nextAlgorithm` arm ·
a completion insight · narration ids and copy.

**Library placement:** the end of the Advanced shelf, after Postorder —
`TREE_POSTORDER → ZERO_ONE_KNAPSACK → BINARY_SEARCH`. The shelf moves from walking structures
to building answers, and a DP lesson reads best once Prefix Sum's "build a table once" and
Dijkstra's "a value is a claim" are behind the learner.

**Card:** *0/1 Knapsack* — *"Take it or leave it, and never solve the same bag twice."* ·
accent `Orange` · glyph `TileBars` (the closest existing tile to a table; a dedicated glyph is
an optional polish item, not a dependency).

---

## 11. Visual states — the checklist

| State the brief asks for | Rendered as |
|---|---|
| current item | its row header in `primary`, 700 weight |
| current capacity | its column header in `primary` |
| current DP cell | `GHOST` hole with the selected outline |
| exclude candidate | `CANDIDATE` amber cell + amber SKIP side |
| include candidate | `COMPARING` violet cell + violet TAKE side |
| selected maximum | chosen strip side at full gradient; the other muted; value lands in the cell |
| completed cells | `IDLE` with numeral |
| final answer | `dp[n][W]` `FINALIZED` green |
| selected items | backtracking path `FINALIZED`; item cards 1 green / 0 `ELIMINATED`; bag meter fills |

---

## 12. Misconceptions, and where each is handled

| Misconception | Where |
|---|---|
| an item can be taken more than once | WATCH beat 1 · TRY question A, same-row tap |
| greedy (most valuable / best ratio) is enough | WATCH beats 2 and 14 — the answer cell *is* the refutation |
| `dp[i][c]` is "item i at capacity c" rather than "the first i items" | WATCH beat 4 · the focus caption on every cell |
| TAKE reads the same capacity, forgetting the weight | TRY A, SKIP's-cell and wrong-capacity taps |
| an item that doesn't fit can be taken | TRY B at both boundaries |
| examining a cell always means taking the item | WATCH beat 14 · TRY `dp[3][4]` |
| the table's last number is the whole answer | WATCH beat 15 · TRY question C |
| a changed value somewhere means the item was used | TRY C: only a difference **from the row above** counts |
| a larger table is always affordable | the O(n × W) bullet |

TRY gets the ones that are decisions; WATCH gets the ones that are observations.

---

## 13. Testing strategy

`KnapsackTest` in `:engine`, structured like `DijkstraTest` and `CountingSortTest`. Headless,
milliseconds, no device.

### 13.1 Correctness against independent references

Two references written **in the test file**, sharing no code with the engine:

- a **brute-force subset enumerator** — the answer and the set of optimal bags;
- a **textbook recursive knapsack** — every cell of the table.

Run over both lesson datasets and a table of generated small problems (every 3-item problem
with weights 1–4, values 1–9, capacity 0–6). The engine must agree on every cell, the best
value, and — where the optimum is unique — the selected items.

### 13.2 The exact lessons, deterministically

| Asserts | WATCH | TRY |
|---|---|---|
| table | the grid in §2.4 | the grid in §2.6 |
| best value | 8 | 6 |
| selected | Book, Camera | Water, Stove |
| cell kinds per row | §2.4 | §2.6 |
| greedy's pick (engine-derived) | Laptop, 7 | — |
| the answer cell | SKIP | TAKE |
| no ties anywhere | ✓ | ✓ |
| optimum unique by enumeration | ✓ | ✓ |
| WATCH beats | the pinned 20 | — |
| TRY decisions | — | the 13 in §6.3, in order, with their kinds |

### 13.3 Edge cases

| Case | Behaviour |
|---|---|
| empty item list | base fill, then `Terminal` — a finished lesson, not a crash; best 0, bag empty |
| capacity 0 | a single column; best 0; nothing taken |
| single item that fits | best = its value; taken |
| single item heavier than capacity | its row copies zeros; best 0; left out |
| every item heavier than capacity | all rows copy; best 0; backtracking marks all left out |
| duplicate weights | correct table; both rows behave independently |
| duplicate values | correct table |
| multiple optimal bags | best value correct; the bag reported is the one **SKIP-on-tie** produces, deterministically, and it is a genuine optimum |
| ties inside the table | SKIP wins; backtracking reads it as not taken — the two agree |
| invalid construction | non-positive weight, negative value, negative capacity, duplicate names — rejected |

### 13.4 Decisions and wrong actions

| Asserts |
|---|
| **TAKE** is accepted exactly when the item fits and include > exclude |
| **SKIP** is accepted exactly when exclude ≥ include, or the item does not fit |
| **invalid TAKE** (does not fit) is refused, state byte-for-byte identical |
| every wrong source tap, at every A decision, is refused unchanged — including the same-row cell |
| every wrong Taken / Left out is refused unchanged |
| after any sequence of refused actions the run still completes on the correct table |
| every `Decide` has `correct ∈ options`, a three-rung ladder, and a `whyWrong` for every wrong option |
| no decision is asked in row 1 or at column 0; exactly one no-fit is asked per row ≥ 2 that has one |
| **adversarial driving**: every action applied at every beat terminates on the right answer |
| completion: `Terminal(Completed)` only after backtracking reaches row 0 |
| rewind: `apply(a); rewind()` restores the exact prior state |

### 13.5 Scene

| Asserts |
|---|
| no computed value appears in a cell before its `Take` / `Skip` transition |
| TAKE's side reads `?` while question A is open |
| the no-fit TAKE side never prints the comparison |
| SKIP's cell is `CANDIDATE` and TAKE's cell is `COMPARING`, and nothing else is |
| the table is hidden during PROBLEM and visible from `FillBase` on |
| exactly one `FINALIZED` path cell per backtracked row |

### 13.6 Outside the engine

| Test | Change |
|---|---|
| `LearningProgressTest` | `ZERO_ONE_KNAPSACK` round-trips 0 / 50 / 100, independent of every other id |
| `ProAccessTest` | the new lesson is Pro; **counts move from 10 / 11 / 21 to 11 / 11 / 22** (§17.1) |
| full suite | every existing test green, unmodified apart from `ProAccessTest`'s counts |

---

## 14. Build order and gates

| | Step | Gate |
|---|---|---|
| 1 | `KnapsackProblem` + both datasets + the two reference implementations as tests | the §2 tables, bags and uniqueness proven by test before any engine exists |
| 2 | `KnapsackAlgorithm` + `KnapsackTest`, headless | **A** — engine agrees with both references on every generated problem; wrong-action and adversarial suites green |
| 3 | `DpTableScene` + projector + scene tests | |
| 4 | **Layout spike**: the WATCH table and choice strip at 360dp and 320dp, on a device | **B** — no overlap, no clipped numeral, no scroll, 48dp tap targets; *fit the phone by adjusting the header column, never by shrinking the cell* (ADR-039's rule) |
| 5 | `DpTable` renderer + the four dispatch branches | **C** — every existing lesson renders unchanged; full suite green |
| 6 | WATCH: narrator, copy, pinned beats | **D** — the 20 beats, adjacent steps differ, values read from state |
| 7 | TRY: wiring, prompts, the ladder and `whyWrong` copy | **E** — the 13-decision run on device, including every §6.4 wrong answer |
| 8 | Library entry, `nextAlgorithm`, completion insight, Pro wiring (+ §17.1 ruling) | Pro lock verified with and without entitlement |
| 9 | Docs: `docs/zero-one-knapsack.md`, **ADR-044**, DESIGN_SYSTEM §6.16i, README / PRODUCT_SPEC §3 / §4 tables, `pro-access.md` | |
| 10 | Full regression: suite, and a device pass over the five scene shapes | |

Gate B moves ahead of the renderer on purpose: Dijkstra's spike (ADR-039) found two layout
defects before they were built, and a 7-column table is this lesson's equivalent risk.

---

## 15. Documentation plan

| Document | Change |
|---|---|
| `docs/zero-one-knapsack-plan.md` | **this file**; marked superseded by the build, as `dijkstra-plan.md` is |
| `docs/zero-one-knapsack.md` | the shipped lesson note, in the `counting-sort.md` / `dijkstra.md` format |
| `DECISIONS.md` | **ADR-044** (proposed below) |
| `DESIGN_SYSTEM.md` | §6.16i — the DP table, its states and the choice strip |
| `ARCHITECTURE.md` | the `Scene` union gains its sixth member; the lesson tables gain a column |
| `PRODUCT_SPEC.md` | §3 decision row; §4 insight row |
| `README.md` | the lessons table and the free / Pro counts |
| `docs/pro-access.md` | the Pro list |

### Proposed ADR-044 — 0/1 Knapsack: a table of smaller bags, and TAKE reads the row above

- **Decision.** 0/1 Knapsack ships as an Advanced (Pro) `LessonPack` with WATCH and TRY.
  `Scene` gains a sixth shape, `DpTableScene`; `Dataset` gains a defaulted `knapsack`. The
  learner answers three questions: which cell TAKE builds on, TAKE or SKIP, and — walking back
  up — whether each item was taken.
- **Why a sixth shape** — §5.1.
- **Why the source tap** — it is the only question that tests the state and the 0/1
  constraint rather than `max` of two printed numbers, and its key wrong answer is the
  unbounded recurrence.
- **Why the brief's dataset was replaced** — §2.1, with the enumeration.
- **Why wrong actions are refused rather than applied** — §9.4.
- **Alternatives:** four items (20 recurrence cells, ~20 TRY decisions) · a numeric keypad
  for cell values (a new interaction model) · three value options per cell (tests `max` only)
  · a one-row table (erases what backtracking reads) · the PROBLEM beats as narrator-only
  captions over one unchanging scene (the greedy bag has to be a real, engine-computed state).

---

## 16. Acceptance criteria

**Learning**
- [ ] WATCH states the 0/1 rule in its own beat, and TRY refuses a same-row source tap with that rule as the reason.
- [ ] WATCH defines `dp[i][c]` in plain language before any cell is computed, and every open cell carries its meaning as a caption.
- [ ] WATCH shows SKIP, TAKE and `max` as three separate beats on one cell before collapsing.
- [ ] WATCH shows greedy's bag (7) before the table and names its failure at the answer cell (8 vs 7).
- [ ] TRY asks question A at least three times, B at least five times including one SKIP-wins and two no-fits, and C once per item.
- [ ] The selected items are reconstructed by backtracking, never authored, and shown filling the bag.

**Correctness**
- [ ] WATCH table, 8, Book + Camera · TRY table, 6, Water + Stove — each asserted cell by cell.
- [ ] The engine agrees with a brute-force enumerator and a recursive reference on every generated problem.
- [ ] Every wrong action leaves the state byte-for-byte identical, and the run completes correctly afterwards.
- [ ] The run terminates under adversarial driving, and on empty, zero-capacity and nothing-fits inputs.

**Architecture**
- [ ] Zero knapsack logic in `:app` — no addition, comparison or index arithmetic in a composable.
- [ ] Zero new `VizEvent` types, interaction models, screens, controllers or design tokens.
- [ ] One new `Scene` shape; four dispatch branches; no existing lesson, renderer or test changed apart from `ProAccessTest`'s counts.
- [ ] No change to DFS, BFS, Dijkstra, BST, AVL, the traversals, billing logic, AdMob or UMP.
- [ ] Progress is 0 / 50 / 100 for this lesson alone.

**Presentation**
- [ ] The table fits a 360dp phone in portrait with no scroll and no numeral below `sceneNumeral`; 320dp verified at gate B.
- [ ] Every state in §11 uses an existing `CellState` and token.
- [ ] Nothing answers a question before it is asked: `?` on the TAKE side during A, no printed comparison during a no-fit B.
- [ ] The lesson is Pro via its category, locked without entitlement and open with it.

---

## 17. Decisions, risks and what I would like ruled

**1. The paywall copy says "ten". ⚠ needs your ruling.** `PaywallScreen.kt` hard-codes
*"It is one of the ten advanced lessons in AlgoKing Pro."* and *"10 advanced algorithms"*, and
`ProAccessTest` asserts exactly ten Pro lessons and 21 in total. Adding any Advanced lesson
makes both false. The brief forbids modifying billing or the paywall. Options:
(a) a **copy-only** change to the two strings (to "eleven" / "11") plus the test counts —
*recommended*, no billing logic touched; (b) make the copy count-free ("one of the advanced
lessons") so the next lesson needs no edit; (c) ship without touching it and accept a false
claim on a purchase screen — *not recommended*.

**2. The PROBLEM beats live in the engine.** *Recommendation: yes* — `Introduce` is a
Mechanical action, so greedy's bag is computed by the engine and asserted by test, and each
beat is a real, distinct state. The alternative, narrator captions over one scene, is how
Counting Sort's opening works, but it cannot show a bag that changes. Cost: four trivial
transitions TRY auto-applies.

**3. TRY is 13 decisions.** One more than Counting Sort's 12, five more than Dijkstra's 8. Two levers, in order of
preference, each a one-line rule change: ask the source tap only when the remaining capacity
holds a **non-zero** best (13 → 11, keeps the hero `dp[3][5]` tap); or make the last
backtracking row the app's (13 → 12). *Recommendation: ship 13, measure on device at gate E.*

**4. 320dp width.** 28dp cells, single digits — about the same as Counting Sort's eight
buckets at that width (28.5dp), so tight rather than new. Gate B decides; the lever is the header column (56dp → 48dp with a two-line label),
never the cell or the numeral.

**5. `DpTableScene` naming.** Generic-shaped but not generic-promised: nothing is built for a
future lesson. The alternative, `KnapsackScene`, would be honest today and wrong the day LCS
arrives. *Recommendation: `DpTableScene`.*

**6. Tie rule: SKIP.** Invisible in both lessons by construction; tested; documented. Chosen
because it is the rule backtracking already reads.

**7. Item names.** *Book / Camera / Laptop* and *Water / Tent / Stove* — two themes, so TRY
does not look like WATCH with the numbers changed. Text, no emoji (§5.5). Say the word for
different items; the numbers are what was verified.

**8. The brief's step order.** Followed, with one change: *"why greedy is not enough"* is
**raised** before the table (beat 2) and **answered** at the answer cell (beat 14), rather
than proven up front — proving it before DP would reveal the answer the table then has to
find.
