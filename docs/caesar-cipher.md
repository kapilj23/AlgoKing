# Caesar Cipher — the alphabet is a ring

**Status:** built · 2026-09-15 · MVP scope is **WATCH → TRY → COMPLETE** · unit-tested; **on-device pass still outstanding**
**Category:** Encryption · **FREE** · **Engine:** `engine/algorithms/caesar/` · **Scene:** `CipherScene`
**Decision:** ADR-046

---

## What it is

Every letter moves a fixed number of places along the alphabet.

```
Plaintext    H E L L O      shift 3
Ciphertext   K H O O R
```

```
encrypted = (position + shift) mod 26
decrypted = (position − shift + 26) mod 26
```

`A → D`, `B → E`, `C → F` … and then `X → A`, `Y → B`, `Z → C`.

**The `mod 26` is the whole lesson.** `A → D` is arithmetic anyone can do. `Z → C`
is the thing that has to be *seen*, because it is where the line becomes a circle —
so the teaching alphabet is always drawn in full, and the walkthrough spends a beat
on the wrap even though `HELLO` never needs one.

**Encryption is the lesson; decryption is one line of it.** Shifting back by the
same amount is the whole of it, it is named in the recap and in the wrong-answer
feedback, and it is not built as a second stage. A lesson that does both teaches
neither twice as well.

## Complexity

| | |
|---|---|
| Time | **O(n)** — one pass, one constant-time shift per character |
| Space | **O(n)** — the ciphertext is as long as the plaintext |

And the caveat the recap ends on: **there are only 25 useful shifts**, so anyone
can try all of them. This hides a message; it does not secure one.

## The datasets

Both are short, uppercase and letters-only. The content is the *ring*, not the
length: a longer word would add taps without adding a judgement, and the collapse
rule (ADR-025) would eat the extra beats anyway.

### WATCH — `HELLO`, shift 3 → `KHOOR`

Three properties earn it its place:

- **`L` appears twice, adjacently.** Identical letters always encrypt to the same
  letter — which is both why the cipher is easy to apply and why it is trivial to
  break. The walkthrough collapses the pair into one beat and says so.
- **nothing wraps.** All five letters sit comfortably inside the alphabet, so the
  wrap can be taught as its own beat against `Z` rather than arriving mid-word as a
  surprise.
- **shift 3 is small enough to count on screen.** A learner checking the mapping
  row by eye can verify every step, which is what makes the picture evidence rather
  than decoration.

### TRY — `ALGO`, shift 2 → `CNIQ`

A different word and a different shift, so TRY is application rather than recall
(ADR-014). Verified independently:

```
A(0) + 2 = 2  -> C
L(11)+ 2 = 13 -> N
G(6) + 2 = 8  -> I
O(14)+ 2 = 16 -> Q
```

`A` opens it deliberately: it is the letter learners are most confident about and
most often get wrong, because shifting it *backwards* lands on `Y` and looks
perfectly plausible. That distractor is on the table at every beat.

## WATCH — 9 beats

| # | Beat | Says |
|---|---|---|
| 0 | `SETUP` | Hide "HELLO" by shifting every letter. |
| 1 | `EXAMINE` | The shift is 3. *The mapping row shows every letter and what it becomes.* |
| 2 | `EXAMINE` | H + 3 → K |
| 3 | `EXAMINE` | E + 3 → H |
| 4 | `PASS_COMPLETE` | "LL" follows the same rule, giving KHOO. *The same letter always encrypts to the same letter.* |
| 5 | `FOUND` | O + 3 → R. *"HELLO" encrypts to "KHOOR".* |
| 6 | `EXAMINE` | **And past the end: Z + 3 → C.** *28 is past Z, so subtract 26 and carry on from A.* |
| 7 | `INSIGHT` | **The alphabet is a ring, not a line.** |
| 8 | `SUMMARY` | "HELLO" → "KHOOR", and four recap bullets. |

**The wrap beat lights `Z`.** It says *"look at the end of the mapping row"*, so
the end of the mapping row is what it highlights — not the letter the message
happened to finish on. Its strip shows `Z (25) + 3 = 28 − 26 = 2 → C`, and the
message rows are left exactly as they were, so it reads as an aside about the
alphabet rather than as the run doing something more. Tested.

**The middle is collapsed** by ADR-025's rule: narrate the smallest prefix that
builds the model, then stop. Two letters establish it — the first shows the rule
applied, the second shows it was not a coincidence — and the repeated `LL` is worth
one beat rather than two precisely because the repetition is the observation.

## TRY — four decisions, one question

> **What does this letter become?** — three letters, and the learner picks.

### The wrong options are the misconceptions

For `A` with shift 2:

| Option | Is | Encodes |
|---|---|---|
| **C** | A + 2 | correct |
| Y | A − 2 | **shifted the wrong way** — the commonest Caesar error, and the one decryption depends on understanding |
| B | A + 1 | **off by one** — counting the starting letter as the first step |

The correct answer's seat rotates with the cursor, so it is never in the same place
twice running. A test asserts that.

**Why three buttons rather than tapping the alphabet.** The brief suggested
selecting the letter in the mapping row. Twenty-six tap targets are about 22dp
each at 360dp, well under the 48dp touch minimum the design system holds every
interactive element to — and ADR-039's rule is that the layout gives way, never the
thing the learner has to reach for. So the alphabet is *evidence* the learner reads
and the answer is a 56dp button, which is the pattern Prefix Sum, Dijkstra and
Fibonacci already use for "the answer is a value".

### Wrong answers

A wrong selection is a learning event, never a state transition (ADR-021). Nothing
advances, the same letter stays on screen, and the ladder escalates:

| Attempt | Says |
|---|---|
| 1 | *"Look at the alphabet again — A, then 2 places forward."* |
| 2 | *"A is lit in the mapping row. Which letter is printed under it?"* |
| 3+ | *"A is position 0. 0 + 2 = 2, which is C."* — and repeats |

Each wrong option additionally names what the learner actually did — *"That is A
shifted 2 places backwards. Encrypting moves forwards — decrypting is the one that
goes back."* Never "Wrong".

A correct answer says what it achieved, and on a wrap it says the thing worth
saying: *"Z plus 3 runs past Z — 28 wraps round to C. The alphabet is a ring."*

## The picture

`CipherScene`, the seventh shape (ADR-046), drawn by `CipherTable`:

```
PLAINTEXT    [H]  E   L   L   O

SHIFT 3      A B C D E F G H I J K L M        <- 13 per row
             D E F G H I J K L M N O P
             N O P Q R S T U V W X Y Z
             Q R S T U V W X Y Z A B C

CIPHERTEXT    _   _   _   _   _

             H  +  3  =  10  →  K
```

| Means | State | Reads |
|---|---|---|
| the letter being encrypted | `COMPARING` violet | Current |
| the letter just produced | `CANDIDATE` amber | New |
| not produced yet | `GHOST` | Empty |
| finished | `FINALIZED` green | Done |
| not reached | `IDLE` | Waiting |

**The alphabet wraps onto rows; it never shrinks.** Twenty-six tiles in one row is
about 7dp each at 360dp, which is not a letter — it is a smudge. Two rows of
thirteen give each tile a legible width, and nothing in the row is tappable so the
touch minimum does not apply to it.

**It is drawn in full, always** — including letters this message never uses. A
mapping showing only the letters in play would remove the wrap, which is the
lesson.

**The tile is the mapping.** Each tile carries the plain letter over the letter it
becomes, so one lit tile *is* the answer rather than pointing at it — the treatment
Counting Sort gives a bucket, which prints the count inside and the value it counts
underneath.

**The strip shows the working, and the subtraction when it happens.** `H (7) + 3 =
10 → K`, and `Z (25) + 3 = 28 − 26 = 2 → C`. A learner told only "it wraps" has a
word; one who watches 28 become 2 has the rule. The result reads `?` until it is
known (ADR-030).

**The scene is read from the events, not the cursor.** After a character is
encrypted the cursor has moved on, so lighting the tile the cursor points at would
show the learner the *next* letter beside the sentence explaining the last one.

## Edge cases — all tested

| Case | Behaviour |
|---|---|
| shift 0 | a legal lesson; every letter maps to itself |
| shift 1, 25 | ordinary lessons |
| shift 26, 29, 52 | normalised — 29 **is** 3, and produces the same run |
| shift −1, −3, −26 | normalised into `0..25`; 25, 23, 0 |
| `Z` | wraps to `A` at shift 1, `C` at shift 3 |
| `ZZZ`, `ZAZ` | every `Z` wraps; nothing carries between characters |
| lowercase | case preserved — `Hello` → `Khoor` |
| spaces, punctuation, digits | passed through untouched, by the **app**, never asked about |
| a message of only punctuation | completes without asking anything |
| empty plaintext | refused at construction, not at runtime |
| no cipher on the dataset | falls back to `HELLO`/3 |
| a wrong letter applied directly | the run stays legal and still terminates |
| an ill-timed action | a no-op returning the same state |
| adversarial driving | 40 action sequences, every one terminates |

**Shift normalisation lives in `CipherProblem`**, at construction, which is the
whole reason it is a type rather than two loose fields: an engine that has to
remember to reduce a shift is one that will eventually forget.

**The `+ 26` before the second `mod` is not decoration.** Kotlin's `%` keeps the
sign of its left operand, so a negative shift without it produces a negative index
and an exception two lines later. Tested across `−30..30`.

## Engine — one canonical implementation

`core/Cipher.kt` holds the transform and **nothing else in the codebase encrypts
anything**:

```kotlin
fun caesarEncrypt(text: String, shift: Int): String
fun caesarDecrypt(text: String, shift: Int): String
fun shiftLetter(char: Char, shift: Int): Char
fun alphabetIndexOf(char: Char): Int
fun letterAt(index: Int): Char
```

`CaesarState` owns the plaintext, the normalised shift, the cursor, the ciphertext
so far, the expected answer, whether the current character wraps and what its sum
is. The projector, the walkthrough, the decision options and the UI all read that
state — there is no cipher arithmetic anywhere in Compose.

Every letter is a `Probe.Decide`. A non-letter is a `Probe.Mechanical`, because a
space has exactly one legal outcome and tapping the only legal target teaches a
gesture (PRODUCT_SPEC.md §3).

## Access, ads and progress

**FREE.** Filed under **Encryption**, which is not the Advanced shelf, and access
derives from the category (ADR-032, ADR-041) — so it is free with no flag saying
so, and the paywall is never reached. `ProAccess`, `SubscriptionRepository`,
`PlayBillingGateway` and `PaywallScreen` are untouched.

**Ads:** nothing added. The existing centralized policy (ADR-042) applies
unchanged — a free learner may see the one interstitial on arrival at Complete,
`Placement` still has exactly one member, and there is no ad code in this lesson.

**Progress:** 0 % → **50 %** on WATCH → **100 %** on WATCH + TRY, independent and
latched. No code needed — progress is `Stage`-driven with no per-algorithm code
anywhere (ADR-028).

**Teaching order:** after Hash Map, before the Advanced shelf — a short,
self-contained idea between the structures and the techniques.

## Testing

**47 tests** in `engine/src/test/.../CaesarCipherTest.kt`, plus 2 in the app's
`ProAccessTest`. Engine total: **761**.

| Group | Covers |
|---|---|
| The transform | `HELLO+3=KHOOR`, `ALGO+2=CNIQ`, `Z+1=A`, `Z+3=C`, `ABC+0=ABC`, `XYZ+3=ABC`; and every shift `−30..30` over seven messages against an **independent alphabet walk** |
| Decryption | undoes encryption for every shift |
| Normalisation | 0, 1, 25, 26, 27, 29, 52, −1, −3, −25, −26; shift 29 drives the same run as shift 3 |
| Wrapping | single `Z`, `ZZZ`, `ZAZ`; the state's sum and wrap flag |
| Case and non-letters | preserved and passed through; a space is `Mechanical`, `Hold`, never `Insert` |
| TRY advance | correct selections advance one character; `ALGO` → `CNIQ` |
| TRY refusal | five wrong in a row leave the state byte-for-byte identical; guidance escalates then holds; a `Retry` carries no action; the backwards shift is always offered and named |
| Invalid actions | a wrong letter applied directly still terminates; ill-timed actions are no-ops; 40 adversarial sequences |
| Completion | only after every character; terminal outcome and event; rewind exactness |
| Edge cases | single characters, all-wrapping, punctuation-only, empty (refused at construction), no dataset |
| The picture | all 26 tiles, exactly one lit and it is the current letter, ciphertext holes, the strip hides its result, the wrap beat lights `Z`, the finished scene has no holes |
| The walkthrough | opens on the problem, closes on the idea; the wrap is taught and follows the message; decryption named once; two letters in full and the repeats collapsed; length pinned 8–14; no two adjacent steps identical; **no more than two steps share a picture** |
| Registration | in the catalog with its own datasets; WATCH and TRY differ; no challenge; progress 0/50/100 latched |
| Access (`:app`) | Encryption is not Pro; opens for `Unknown`, `Free` and `Pro` alike; the category holds only free lessons; **every other lesson is exactly where it was** |

## Quality check

| | |
|---|---|
| ✓ | Project builds — `:app:assembleDebug` succeeds |
| ✓ | Engine 761 tests, app 45, **0 failures** |
| ✓ | Registered FREE, under Encryption |
| ✓ | WATCH is Next-driven — no autoplay, no Play button |
| ✓ | TRY requires a selection at every letter |
| ✓ | Wrong selections do not advance |
| ✓ | Correct selections advance exactly one character |
| ✓ | Progress 50 % after WATCH, 100 % after both |
| ✓ | The existing free interstitial behaviour is reused, unchanged |
| ✓ | Existing algorithms unaffected — 714 pre-existing engine tests still green |
| ✓ | `Z → A` wrapping, and every `Z` in a message |
| ✓ | Shift normalisation, including negatives |
| ⚠ | **UI on a small phone — not verified.** See below. |

## Not yet verified

**On a device.** No device was attached during the build, so two things are checked
by unit test and arithmetic only:

1. **Thirteen tiles per row at 360dp and 320dp.** About 22dp a tile at 360dp and
   19dp at 320dp — a single capital at `labelMedium` should fit with room, but
   "should" is what a device pass is for. If it does not, the fix is the rule
   ADR-039 set: change the layout (three rows of nine, or a wider tile with the
   alphabet scrolling) rather than shrink the letter.
2. **Four rows stacked in one card** — plaintext, two alphabet rows, ciphertext,
   plus the strip. It is the tallest scene in the app.

## Deferred

**CHALLENGE** — `ChallengeCatalog.byId(CAESAR_CIPHER)` returns null, as for every
MVP lesson (`v2-challenge.md`).

**Decryption as a stage, breaking a cipher by brute force, Vigenère, and anything
with a key schedule.** Each is a lesson of its own. This one is deliberately the
smallest complete idea in the library.
