# Prefix Sum — the second Advanced lesson

**Status:** shipped · 2026-09-04 · MVP scope is **WATCH → TRY → COMPLETE**
**Category:** Advanced · **Engine:** `engine/algorithms/prefixsum/`

---

## What it is

Precomputation. Walk the array once building a table of running totals, and every
range sum afterwards costs a single subtraction — however long the range is.

```
Array          [2] [4] [3] [7] [1]
                0   1   2   3   4

Prefix   [0] [2] [6] [9] [16] [17]
          0   1   2   3   4    5
```

It is the first lesson about *paying up front*. Two Pointers taught a smarter way
to walk data; Prefix Sum teaches not walking it at all.

## The representation — fixed, and used everywhere

The prefix array uses the **standard leading-zero form**: it is `n + 1` long and
`prefix[0] = 0`.

```
prefix[i + 1]         = prefix[i] + array[i]
rangeSum(left, right) = prefix[right + 1] - prefix[left]
```

**The other representation — an n-length array with no leading zero — appears
nowhere.** Not in the engine, the walkthrough, the copy, the UI or the tests. The
leading zero is not decoration: without it the range query needs a special case
for `left == 0`, and a formula with an exception in it is one the learner has to
memorise rather than understand.

Worked, on the teaching data:

```
prefix[1] = prefix[0] + array[0] =  0 + 2 =  2
prefix[2] = prefix[1] + array[1] =  2 + 4 =  6
prefix[3] = prefix[2] + array[2] =  6 + 3 =  9
prefix[4] = prefix[3] + array[3] =  9 + 7 = 16
prefix[5] = prefix[4] + array[4] = 16 + 1 = 17

sum(1..3) = prefix[4] - prefix[1] = 16 - 2 = 14      (4 + 3 + 7 = 14)
```

### Why the subtraction works

`prefix[right + 1]` holds everything up to *and including* `right`.
`prefix[left]` holds everything strictly *before* `left`.
The difference is exactly `array[left..right]` — nothing else can survive it.

That sentence is the lesson. A learner who can build the table but reaches for
`prefix[right]` has not learned the technique, which is why choosing the indices
is its own decision with its own guidance ladder.

## Complexity

| | |
|---|---|
| Build the table | **O(n)**, once |
| Each range query | **O(1)** |
| `q` queries | **O(n + q)** — against O(n·q) for the naive re-add |

## WATCH

`[2, 4, 3, 7, 1]`, asking for `sum(1..3)`. Eleven steps, user-paced, one **Next**.

**Phase 1 — build.** The leading zero is stated (not asked for: it is the
definition of the representation), then one beat per entry, each naming its own
arithmetic — *"2 + 4 = 6 · prefix[2] = prefix[1] + array[1]."*

**Phase 2 — query.** The payoff, in three beats: the range is stated, the two
prefix cells are chosen, and the subtraction is evaluated. A learner who sees only
the answer has watched a magic trick.

**Then the insight**, deliberately last: *"One subtraction, however long the
range."* O(1) per query means nothing until you have seen a query answered without
touching the array.

## TRY

`[5, 1, 8, 2, 6]`, asking for `sum(2..4)` — a different array and a different
range, so TRY is application rather than recall.

```
prefix = [0, 5, 6, 14, 16, 22]
sum(2..4) = prefix[5] - prefix[2] = 22 - 6 = 16      (8 + 2 + 6 = 16)
```

That range deliberately **ends at the last element**, so `prefix[right + 1]` is
the final total. Watch used an interior boundary; meeting the other end once is
what stops "right + 1" being remembered as "somewhere in the middle".

Seven decisions: five build steps, then the indices, then the arithmetic.

### The options are values, not a keypad

Each beat offers three numbers and the learner picks. The wrong two are the two
mistakes learners actually make, so the feedback can name the misconception rather
than restate the rule:

| For `prefix[2] = 2 + 4 = 6` | Encodes |
|---|---|
| **6** | correct |
| 4 | the running total dropped — *"That is only the current value."* |
| 7 | two array values added together — *"A prefix sum adds one array value to the previous prefix."* |

For the query beat, `prefix[3] − prefix[1]` is always offered: the off-by-one is
what this technique is famous for, and a beat that cannot be failed tests nothing.

### Wrong answers

A wrong answer is a learning event, never a state transition (ADR-021). Nothing
moves, and the ladder escalates: point at the evidence → ask the reasoning
question → state it plainly and repeat.

## The picture

Prefix Sum is the second lesson whose picture is genuinely **not** a sequence, and
it fails to fit for a different reason than the hash map did (ADR-030): it has two
arrays *of different lengths*, and the whole lesson lives in the offset between
them. So `Scene` gained a third shape, `PrefixScene`.

Both rows are laid out over the same `n + 1` slots and the source row leaves slot
0 empty. That puts `array[i]` directly above `prefix[i + 1]` — the cell it is the
increment for — and leaves `prefix[0] = 0` standing alone at the left with nothing
above it, which is exactly why the prefix array is one longer.

- Uncomputed prefix entries are `GHOST` — a hole, never a greyed-out number
  claiming a value exists before it does.
- The equation strip shows the working with the result as `?` until it is known:
  an answer already on screen is not a question (ADR-030).
- Cells are the same `SceneCell` every other lesson draws, so the visual language
  cannot fork.

## Progress

The standard MVP model, with no additions: 0 % → **50 %** on WATCH → **100 %** on
TRY. Prefix Sum gets this for free — progress is `Stage`-driven and has no
per-algorithm code (ADR-028).

## Edge cases

All handled in the state, all tested:

| Case | Behaviour |
|---|---|
| empty array | a finished lesson, not a crash — nothing to build, nothing to query |
| single element | one build step; the only range is `0..0` |
| range of one element | `prefix[i + 1] - prefix[i]` = that element |
| whole array | `prefix[n] - prefix[0]` = the total |
| range starting at 0 | subtracts the leading zero; no special case |
| range ending at n-1 | uses the final prefix entry |
| `left > right` | normalised once, in `queryRange` — a bad dataset, not a crash |
| out of bounds | clamped into the array |
| negatives, duplicates | work unchanged; a running total may go down |

`rangeSum` never throws for any bounds at all, and there is a test that calls it
across a grid of illegal ones.

## Dataset architecture

The range query lives on `Dataset` (`queryLeft` / `queryRight`), added as optional
defaulted fields so no existing lesson changed. A second range, a harder one or a
generated one later is data rather than code — which is what §15 of the brief
asked for.

## What it cost the architecture

| | |
|---|---|
| New `VizEvent` types | **0** — `Insert`, `Examine`, `Region`, `Finalize`, `Meter` |
| New `Scene` shape | **1** — `PrefixScene`, on ADR-030's precedent |
| Changes to existing algorithms | **0** |
| Changes to existing renderers | `SceneCell`/`SlotRow` made internal so the new shape reuses them rather than duplicating cell styling |
| Files added | 4 engine + 1 renderer + 1 test |

## Deferred

**CHALLENGE.** `ChallengeCatalog.byId(PREFIX_SUM)` returns `null`, the same as Two
Pointers — see [`v2-challenge.md`](v2-challenge.md).

Variations the engine is already shaped for: multiple queries over one built table
(the strongest demonstration of O(n + q)), 2-D prefix sums, and difference arrays.
Each is a dataset and a narrator, not a new interaction model.
