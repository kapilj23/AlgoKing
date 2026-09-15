# XOR Cipher — the key that undoes itself

**Status:** built · 2026-09-15 · MVP scope is **WATCH → TRY → COMPLETE** · unit-tested; **on-device pass still outstanding**
**Category:** Encryption · **FREE** · **Engine:** `engine/algorithms/xor/` · **Scene:** `BitwiseScene`
**Decision:** ADR-047

> **This is an educational demonstration of the XOR operation, not secure
> encryption.** XOR is a building block of real cryptography, but a XOR cipher
> with a short or reused key is not secure on its own. The lesson says so in its
> recap and again on the Complete screen.

---

## What it is

XOR compares two bits and answers one question: *are they different?*

| A | B | A ⊕ B |
|---|---|---|
| 0 | 0 | **0** |
| 0 | 1 | **1** |
| 1 | 0 | **1** |
| 1 | 1 | **0** |

One rule, stated as a sentence rather than four rows to memorise: **the result is
1 when the bits are different, 0 when they are the same.**

## The property the lesson is actually about

```
plaintext  ⊕ key = ciphertext
ciphertext ⊕ key = plaintext      ← the same key, applied again
```

XOR is its own inverse, so encrypting and decrypting are not two procedures — they
are the same procedure run twice. A learner who watches `1010` become `0110` and
then become `1010` again has seen why that matters without a word of algebra, and
that is why WATCH does the second pass over the ciphertext the first pass actually
produced rather than asserting the result.

A test proves it over **every 4-bit pair**: `xorBits(xorBits(p, k), k) == p` for all
256 combinations.

## Complexity

| | |
|---|---|
| Time | **O(n)** — one pass, one constant-time comparison per bit |
| Space | **O(n)** — the ciphertext is as long as the plaintext |

## The datasets

### WATCH — `1010 ⊕ 1100`, and back

```
1010      plaintext
1100      key
----
0110      ciphertext

0110      ciphertext
1100      the same key
----
1010      the plaintext, recovered
```

Two properties earn it its place:

- **the four columns are all four rows of the truth table.** `1⊕1`, `0⊕1`, `1⊕0`,
  `0⊕0` — so a learner who watches this once has watched the whole rule being used,
  in order, with nothing left over. A test asserts the walkthrough covers exactly
  those four pairs, in that order.
- **it goes back.** `roundTrip` is on, so the second pass is a real run over real
  output. The reversal is the point of the lesson.

### TRY — `1011 ⊕ 0110 = 1101`, encryption only

```
1011      plaintext
0110      key
----
1101      ciphertext
```

Four columns, no second pass: the judgement is the XOR, and an identical second
pass would be patience rather than understanding.

**The key is `0110`, not the brief's `1101`, and the difference is the point.**
`1011 ⊕ 1101` is also a valid lesson, but it produces the **same `0110` WATCH
produces**, from bit pairs differing in only the last column — so a learner who
memorised the answer could reproduce it without applying the rule once, which is
exactly what ADR-014 says a TRY dataset must not allow.

```
WATCH        (1,1) (0,1) (1,0) (0,0)  ->  0110
brief's TRY  (1,1) (0,1) (1,0) (1,1)  ->  0110    three columns shared, same answer
shipped TRY  (1,0) (0,1) (1,1) (1,0)  ->  1101    one column shared, different answer
```

The same call ADR-044 made when 0/1 Knapsack's brief supplied a bag whose optimum
both greedy strategies already found: the numbers *are* the lesson, so a dataset
that teaches the wrong thing gets replaced and the replacement is written down.
`XorCipherTest` asserts the divergence — same width, different ciphertext, at most
one shared column — rather than trusting it.

## WATCH — 10 beats

| # | Beat | Says |
|---|---|---|
| 0 | `SETUP` | XOR compares two bits. The result is 1 when they are different. |
| 1 | `EXAMINE` | That is the whole rule, in four lines. |
| 2 | `EXAMINE` | 1 ⊕ 1 = 0 |
| 3 | `EXAMINE` | 0 ⊕ 1 = 1 |
| 4 | `EXAMINE` | 1 ⊕ 0 = 1 |
| 5 | `PASS_COMPLETE` | 0 ⊕ 0 = 0. *1010 ⊕ 1100 = 0110* |
| 6 | `EXAMINE` | **Now XOR the ciphertext 0110 with the same key 1100.** |
| 7 | `FOUND` | The rest come back the same way: 1010. *Which is exactly what we started with.* |
| 8 | `INSIGHT` | **The same key, applied twice, gives the original back.** |
| 9 | `SUMMARY` | Four recap bullets, the last of them the security caveat. |

**Every encrypting column is narrated** — four bits is short enough that collapsing
any would save nothing, and each one is a different row of the truth table.

**The way back is two beats, not four.** The first states the claim and shows one
bit of it; the last delivers the whole word. By the second bit the learner is no
longer learning XOR, they are watching a claim come true, and the claim is about
the word rather than each bit (ADR-025's rule).

## TRY — four decisions

> **What is `a ⊕ b`?** — two buttons, `0` and `1`.

Both options are always offered, always in the same order. A row whose contents
changed with the answer would let a learner read the answer off the row rather than
off the bits.

### There is one wrong answer, and the feedback names the half of the rule it misses

| Bits | Wrong pick | Says |
|---|---|---|
| `1 ⊕ 1` | `1` | *"Not quite. XOR gives 0 when both bits are the same, and these are both 1."* |
| `0 ⊕ 1` | `0` | *"Not quite. XOR gives 1 when the bits are different, and 0 and 1 are different."* |

With two options there is no seat to rotate and nothing to guess between, so the
feedback is where the whole teaching budget goes. A wrong selection is a learning
event, never a state transition (ADR-021): nothing advances, the same column stays
on screen, and the ladder escalates *look at the bits → are they different? → they
are the same, so XOR gives 0*.

## The picture

`BitwiseScene`, the eighth shape (ADR-047), drawn by `BitwiseTable`:

```
PLAINTEXT    [1]  0   1   0
KEY          [1]  1   0   0
─────────────────────────────
CIPHERTEXT    _   _   _   _

XOR    0 ⊕ 0 = 0    0 ⊕ 1 = 1
       1 ⊕ 0 = 1   [1 ⊕ 1 = 0]

             bit 0   1 ⊕ 1 = ?
```

| Means | State | Reads |
|---|---|---|
| the column being decided | `COMPARING` violet | Current |
| the bit just produced | `CANDIDATE` amber | New |
| not decided yet | `GHOST` | Empty |
| decided | `FINALIZED` green | Done |
| not reached | `IDLE` | Waiting |

**The column is the operation.** Three rows, one set of columns, a rule above the
last — the way a sum is written on paper. A learner who reads nothing should still
see that the bottom bit is made from the two above it.

**A `GHOST` matters more here than anywhere else in the app.** `0` is a real answer
in this table, so a placeholder zero would be indistinguishable from a decided one.
Undecided result bits carry no label at all.

**The truth table stays on screen for the whole lesson**, with the row in play lit.
It is the lookup the learner should be making, so the picture makes it — and a
table that appeared only when needed would read as a hint rather than as the rule.

**The row labels change with the phase and nothing else does.** Encrypting reads
*Plaintext / Key / Ciphertext*; the second pass reads *Ciphertext / Key /
Recovered* over the ciphertext the first pass produced. That sameness is the
argument.

## Engine — one canonical implementation

`core/Xor.kt` holds the operation and **nothing else in the codebase computes an
XOR**:

```kotlin
fun xorBit(a: Int, b: Int): Int = if (a != b) 1 else 0
fun xorBits(a: String, b: String): String
fun bitAt(bits: String, index: Int): Int
val xorTruthTable: List<Triple<Int, Int, Int>>
```

`xorBit` is written as an inequality rather than as `a xor b` because that is the
sentence the lesson teaches. The truth table the learner reads is *generated from
it*, so the picture and the algorithm cannot drift apart.

`XorState` owns the problem, both passes' output, the cursor, the expected answer
and the phase. Every bit is a `Probe.Decide` — there is no `Probe.Mechanical`
anywhere, because the XOR **is** the lesson.

### The phase is derived, never stored

This is the one design decision worth knowing about, and it was found by drawing
the lesson rather than by reasoning about it.

The obvious build stores `phase` and rolls it over in `apply` when the first pass
finishes. That is wrong: the frame that writes the last encrypted bit then *is*
already in the second phase, so the picture explaining that bit gets drawn with the
rows relabelled and the result row emptied — the learner reads *"1010 ⊕ 1100 =
0110"* beside a blank.

So `XorState` stores `encrypted` and `decrypted` and derives the phase from them,
and `XorProjector` decides which pass a **frame** belongs to from the events it
carries: *a bit was written into the second pass exactly when the second pass has
something in it.* Two regression tests pin both halves.

## Validation

Everything is checked at construction, in `XorProblem` — the rule the codebase
already follows for a non-positive Dijkstra weight and a zero-length Caesar
plaintext:

| Refused | Because |
|---|---|
| empty plaintext or key | there is nothing to ask about |
| unequal lengths | there is no bit 4 to ask about, and finding that out mid-run is an index crash rather than a teachable state |
| anything but `0` and `1` | a typo read as a zero becomes a plausible wrong answer |
| a bit index outside the string | clamping would answer a different question than the one asked |

`xorBits` refuses mismatched lengths too rather than truncating: a shorter answer
than the input is the kind of wrong that looks right.

## Edge cases — all tested

All-zero key (changes nothing) · all-one key (flips everything) · key equal to the
message (zeroes it) · a single bit · an 8-bit message · a value that is not a bit
(a no-op) · setting a bit past the end (a no-op) · a wrong bit applied directly
(the run stays legal and terminates) · adversarial driving over 40 sequences ·
rewind exactness · a dataset with no XOR problem at all (falls back).

## Access, ads and progress

**FREE.** Filed under **Encryption** — the category Caesar Cipher introduced, which
is not the Advanced shelf. Access derives from the category (ADR-032, ADR-041), so
it is free with no flag saying so. `ProAccess`, `SubscriptionRepository`,
`PlayBillingGateway` and `PaywallScreen` are untouched.

**Ads:** nothing added. The existing centralized policy (ADR-042) applies
unchanged — a free learner may see the one interstitial on arrival at Complete,
`Placement` still has exactly one member, and there is no ad code in this lesson.

**Progress:** 0 % → **50 %** on WATCH → **100 %** on WATCH + TRY, independent and
latched. TRY completes only after every bit is correctly processed — a test drives
three of four and asserts the run is neither finished nor terminal.

**Teaching order:** after Caesar Cipher. Caesar hides a message with arithmetic a
learner can do in their head; XOR does the same job with one bitwise operation and
adds the thing Caesar has no equivalent of — a key that undoes itself.

## Testing

**47 tests** in `engine/src/test/.../XorCipherTest.kt`, plus 1 in the app's
`ProAccessTest`. Engine total: **808**.

| Group | Covers |
|---|---|
| The operation | all five documented examples; the four truth-table rows; **every 4-bit pair** against an independent character comparison |
| The reversal | `p ⊕ k ⊕ k == p` over all 256 combinations |
| Validation | unequal lengths, invalid characters, empty input, out-of-range bit index — all refused |
| TRY advance | correct selections advance one bit; `1011 ⊕ 0110 → 1101`; the round trip recovers `1010` |
| TRY refusal | five wrong in a row leave the state byte-for-byte identical; guidance escalates then holds; the feedback names the right half of the rule |
| Invalid actions | non-bit values and past-the-end writes are no-ops; a wrong bit applied directly still terminates; 40 adversarial sequences |
| Completion | only after every bit; a round trip is not finished when the first pass ends; terminal outcome and event; rewind |
| Regression | the frame finishing encryption still draws the encrypting pass; a non-round-trip lesson reports its answer at completion |
| The picture | three rows sharing columns, holes are holes, the truth table is four rows with one lit, the strip hides its result, labels change with the phase |
| The walkthrough | opens on the rule, closes on the idea; the security caveat is present; four encrypt beats and two for the way back; all four truth-table rows covered; length 8–14; no two adjacent steps identical; identical-picture runs capped at 3 |
| Registration | in the catalog with its own datasets; WATCH round-trips and TRY does not; **TRY cannot be answered from memory of WATCH**; no challenge; progress 0/50/100 latched |
| Access (`:app`) | Encryption is not Pro; opens for every entitlement; the category holds exactly two free lessons |

## Quality check

| | |
|---|---|
| ✓ | Project builds — `:app:assembleDebug` succeeds |
| ✓ | Engine 808 tests, app 46, **0 failures** |
| ✓ | Registered FREE, under Encryption |
| ✓ | WATCH is Next-driven — no autoplay, no Play button |
| ✓ | TRY requires a selection at every bit |
| ✓ | Wrong answers do not advance |
| ✓ | Correct answers advance exactly one bit |
| ✓ | Progress 50 % after WATCH, 100 % after both |
| ✓ | The existing free interstitial behaviour is reused, unchanged |
| ✓ | `1010 ⊕ 1100 = 0110` and `0110 ⊕ 1100 = 1010` |
| ✓ | Invalid inputs refused at construction |
| ✓ | Existing algorithms unaffected — 761 pre-existing engine tests still green |
| ⚠ | **UI on a small phone — not verified.** See below. |

## Not yet verified

**On a device.** No device was attached, so two things are checked by unit test and
arithmetic only:

1. **Four bit cells across, three rows deep, plus a four-line truth table and a
   strip.** The cells are generously wide at four columns — this is the *least*
   cramped scene in the app — but the card is tall, and the truth table's two-per-
   row layout has not been seen at 320dp.
2. **The `⊕` glyph** renders in `Plus Jakarta Sans`. It is U+2295; if the bundled
   font lacks it the platform will substitute, which may look off next to the
   numerals. `XOR` as a word is the fallback.

## Deferred

**CHALLENGE** — `ChallengeCatalog.byId(XOR_CIPHER)` returns null, as for every MVP
lesson (`v2-challenge.md`). A generated one needs a constraint keeping the key off
all-zeros and all-ones, neither of which teaches anything.

**Text-to-binary encryption, repeating-key XOR, breaking a XOR cipher by frequency
analysis, and anything with a key schedule.** Each is a lesson of its own, and the
brief scopes this one to bits.
