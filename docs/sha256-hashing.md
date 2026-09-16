# SHA-256 Hashing — the fingerprint that does not go back

**Status:** built · 2026-09-16 · MVP scope is **WATCH → TRY → COMPLETE** · unit-tested; **on-device pass still outstanding**
**Category:** Cryptography · **FREE** · **Engine:** `engine/algorithms/sha256/` · **Scene:** `HashScene`
**Decision:** ADR-048

> **SHA-256 is a hash function, not encryption.** It has no key, no inverse and no
> decrypt step. The lesson says so in its recap, on the Complete screen, and in the
> wrong-answer copy — and the shelf it lives on is called **Cryptography** rather
> than Encryption for the same reason.

---

## What hashing is

A hash function takes data of **any** size and produces a value of **one fixed**
size.

```
any input, any length
        │
     SHA-256
        │
256 bits = 32 bytes = 64 hexadecimal characters
```

Those three numbers are three ways of writing one size. SHA-256 produces 256 bits;
256 bits is 32 bytes; a byte is two hexadecimal characters, so the digest a person
reads is 64 characters long — always, whatever went in.

```
"hello"  →  2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824
```

## The four properties the lesson teaches

| | |
|---|---|
| **Fixed length** | two characters in or twenty-one, the output is 64 hex characters |
| **Deterministic** | the same input always produces the same hash |
| **Avalanche** | one changed character rewrites almost the whole hash |
| **One-way** | the input cannot normally be recovered from the hash |

Different inputs normally produce different hashes. Because the input space is
unbounded and the output space is 2²⁵⁶ values, two inputs *can* collide in
principle; for SHA-256 no such pair is known, and finding one is not currently
feasible. The lesson states the practical claim and does not over-promise.

## Hashing is not encryption

This comparison is why the lesson sits after Caesar and XOR rather than before
them — both of those turn a message into something else and then turn it back, and
"there is no way back" only reads as a distinction once a learner has watched one.

```
ENCRYPTION                          HASHING

plaintext                           input
    │ + key                             │
ciphertext                          hash function
    │ + key                              │
plaintext          ← recovered      hash        ← and that is the end of it
```

A cipher is *meant* to be undone; that is what the key is for. A hash has no key
and no reverse operation. The accurate claim is that SHA-256 is **designed to be
computationally infeasible to reverse from the hash alone** — not that reversing it
is mathematically impossible, which is a different and false statement. The
difference is exactly why salting and rainbow tables are things.

## Password storage — the caveat that has to be said

SHA-256 **on its own is not how passwords should be stored.** It is built to be
fast, which is precisely the wrong property for a password: an attacker who obtains
the hashes can try billions of guesses per second. Password systems use algorithms
designed to be slow and memory-hard — **Argon2, bcrypt or scrypt**.

This is the last recap bullet and it is repeated on the Complete screen, where a
bullet is read rather than skipped — the placement ADR-047 chose for the XOR
lesson's security caveat, for the same reason.

## Complexity

| | |
|---|---|
| Time | **O(n)** in the input length, at a high level |
| Output | **256 bits · 32 bytes · 64 hexadecimal characters**, always — O(1) |

That O(n) is a *high-level* statement and the lesson says so rather than implying
SHA-256 is one simple pass. The message is padded and split into 512-bit blocks;
the number of blocks grows with the input, which is where the O(n) comes from. Each
block then runs a **fixed 64-round compression** over eight working variables — real
work, constant per block, and deliberately not drawn (see below).

## What is deliberately not visualised

Real SHA-256 contains message preprocessing, padding, a 64-entry message schedule,
64 compression rounds, eight working variables and 64 round constants. **None of it
is animated, and none of it is faked.**

```
INPUT  →  Preprocess  →  SHA-256  →  Hash  →  Hex
                            ▲
                    one box, standing for
                    64 real compression rounds
```

The middle box is labelled with what is actually inside it — `64 compression
rounds` — and the copy says this lesson is about the hashing *concept* rather than
the internals. Drawing sixty-four rounds would bury a beginner; inventing plausible
ones would teach something false about a real algorithm, which is worse than
teaching less.

## The datasets

**Not one digest is authored.** Every value on screen is produced by `Sha256.hex`
at run time from the message strings, which is ADR-045's rule for Fibonacci's call
counts applied here: the lesson's central claim is generated, never written down. A
hardcoded digest goes quietly wrong the first time a message is edited, and in a
lesson whose entire subject is *this input gives exactly this output*, that is the
one mistake that cannot be tolerated.

### WATCH — six messages

```
hello                  the example every write-up of SHA-256 opens with
Hello                  one character different — the avalanche partner
hi                     shorter                \
hello world            longer                  >  the fixed-length ladder
a much longer message  longer still           /
hello                  hashed a second time — determinism, as two real runs
```

Three of those choices are load-bearing:

- **`hello` opens it** because its digest is the one a curious learner will check
  against any other tool, and the lesson has to be the thing that agrees with the
  world.
- **`Hello` differs by exactly one character**, and by a *case* change rather than a
  different letter — the smallest edit there is, which is what makes 61 of 64 digest
  characters changing worth watching.
- **`hello` appears twice**, and that is the only honest way to show determinism.
  Saying "the same input always gives the same hash" over a single row asks the
  learner to take it on trust; hashing it again, as a separate run through the same
  pipeline, puts two independently produced digests side by side.

The avalanche pair, the repeated pair and the length ladder are all **found in the
dataset by looking for them** (`HashProblem.avalanchePair`, `.repeatedPair`,
`.lengthLadder`), never listed by hand — so a dataset edit cannot leave a beat
pointing at rows that no longer demonstrate the thing it claims.

### TRY — five messages

```
hello · Hello · hi · a much longer message · hello
```

The brief's two inputs, plus the lengths question 1 needs and the repeat question 2
needs. **TRY cannot drop `hi` and `a much longer message`**: question 1 asks whether
output length follows input length, and `hello` and `Hello` are *both five
characters* — without a length ladder the learner would be asked to read evidence
the picture does not contain.

**What makes TRY application rather than recall is the narration budget, not the
data.** There is exactly one SHA-256, so a second dataset would be a different place
to stand inside the same function rather than a new problem — the position ADR-045
reached for Fibonacci. WATCH *states* each property as its frame lands; TRY *asks*
all five, with the evidence on screen and nothing saying which way it points.

## The verified digests

Every digest displayed or asserted was produced independently with **two** tools,
checked to agree, on the exact bytes with no trailing newline:

```
$ printf '%s' hello | sha256sum
$ printf '%s' hello | openssl dgst -sha256
```

| Input | SHA-256 |
|---|---|
| `hello` | `2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824` |
| `Hello` | `185f8db32271fe25f561a6fc938b2e264306ec304eda518007d1764826381969` |
| `hello world` | `b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9` |
| `Hello world` | `64ec88ca00b268e5ba1a35678a1b5316d212f4f366b2477232534a8aeca37f3c` |
| `hi` | `8f434346648f6b96df89dda901c5176b10a6d83961dd3c1ac88b59b2dc327aa4` |
| `a much longer message` | `fe6d9c131faa5bb5a8b1a7a8f90fe7db53f22be192e735e2f755c93746434407` |
| `""` (empty) | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` |

`hello` against `Hello` differ in **61 of their 64** characters. That number is
computed by the engine and printed by the walkthrough; the test pins it.

### Where the implementation comes from

`java.security.MessageDigest`, via `engine/core/Hashing.kt` — the one place in the
codebase a hash is produced. **This is the first `java.*` import in `:engine`**, and
it is worth being precise about what that costs: the boundary ARCHITECTURE.md §3
enforces is *no Android and no Compose*, so that a lesson cannot reach a
`@Composable`. `java.security` is neither. The module is still a pure JVM module and
its tests still run in milliseconds with no Robolectric.

Every other transform in the engine is hand-written because the transform *is* the
lesson. SHA-256's internals are explicitly not the lesson, and a hand-rolled copy of
them would be a second, unreviewed cryptographic implementation whose only job is to
agree with the platform's.

## WATCH — 10 beats

| # | Kind | Says | Shows |
|---|---|---|---|
| 0 | `SETUP` | A hash function turns data into a fixed-size value. | the pipeline |
| 1 | `EXAMINE` | SHA-256 is one such function — 256 bits, 32 bytes, 64 hex characters. | the pipeline |
| 2 | `EXAMINE` | Hashing "hello". | input → SHA-256 → digest |
| 3 | `PASS_COMPLETE` | **Different messages. The same output length.** | `hi` · `hello world` · `a much longer message` |
| 4 | `EXAMINE` | **The same input always produces the same hash.** | `hello` twice, nothing marked |
| 5 | `EXAMINE` | **Change one character, and almost all of the hash changes.** | `hello` vs `Hello`, 61 marked |
| 6 | `EXAMINE` | **A hash does not go back.** | the one-way pipeline |
| 7 | `EXAMINE` | Where hashes are actually used. | the one-way pipeline |
| 8 | `INSIGHT` | **Any input, any length — always 256 bits, and never back again.** | |
| 9 | `SUMMARY` | Six recap bullets, the last two the caveats. | |

Only the **first** message gets a beat of its own. Six beats of "and here is another
digest" would be a slideshow; the later messages exist for the comparisons, and that
is where they earn their beat — ADR-025's rule, narrate the smallest prefix that
builds the model then stop.

### The bug the walkthrough dump caught

`WatchScriptBuilder` hands the narrator the scene projected from the state **after**
the transition. So the frame that answers question *n* is drawn showing the evidence
for question *n + 1* — and the first draft, which captioned each frame with the
question it had just settled, put the **fixed-length sentence over the determinism
rows**, the determinism sentence over the avalanche rows, and so on down the whole
run. Every test passed.

It was found by dumping the walkthrough and reading it, which is the only way this
class of bug is ever found — the technique that caught Caesar's wrap beat lighting
the wrong letter (ADR-046) and Fibonacci's six identical opening screens (ADR-045),
and the same failure ADR-047 records for XOR arriving through a different door.

The fix is that a frame is captioned by `frame.state.question` — *what its own scene
shows* — never by what it just answered. `each property beat is captioned with the
picture it was given` now pins it.

## TRY — five judgements

> Every question is asked **after every message has been hashed**, so the evidence
> that answers it is already on screen. The learner reads the picture; they are not
> asked to recall a claim.

| # | Question | Evidence on screen | Correct |
|---|---|---|---|
| 1 | Which statement is correct? | three messages of different lengths, all 64 out | **FIXED** |
| 2 | Which statement is correct? | `hello` hashed twice, identical | **SAME** |
| 3 | One character changed. What happens to the hash? | `hello` vs `Hello`, 61 characters marked | **DIFFERENT** |
| 4 | Can a SHA-256 hash normally be turned back into its input? | the one-way pipeline | **NO** |
| 5 | What does SHA-256 produce? | the pipeline's last two stages | **256 BITS** |

**The true statement is not always in the same seat** — a learner who noticed the
first button was always right could finish the stage without reading anything. A
test asserts both seats are used.

### Long statements on short buttons

A `DecisionButton` is one line at `labelLarge`, so a statement long enough to be
unambiguous cannot live on it — the constraint Two Pointers met and solved the same
way (ADR-032). So the **full statement sits in a card** and the button carries one
short word, with the word printed on the card too so the pairing cannot be misread.
That is `DpTableScene`'s choice strip, reused.

Both cards are identical in every state. The scene does not carry which claim is
true, so the renderer could not style the right answer differently even by accident
(PRODUCT_SPEC.md §5).

### A wrong answer is a learning event, never a state transition

Nothing advances, the same question stays on screen, and the ladder escalates:
*point at the evidence → ask the reasoning question → say it plainly*, with the last
rung repeating forever. Enforced by the shared `DecisionValidation` — a `Retry`
carries no action, so there is nothing the caller could apply (ADR-021).

With two options there is exactly one wrong answer, so the feedback names the
**misconception** rather than restating the rule:

| Wrong pick | Says |
|---|---|
| output length VARIES | *"Not quite. SHA-256 always produces a 256-bit output — the rows above have very different messages and hashes of exactly the same length."* |
| hash is RANDOM | *"Not quite. Nothing about SHA-256 is random… A hash that changed each time could never verify anything."* |
| hash stays the SAME | *"Not quite. A one-character change rewrites almost the whole hash — that is what makes a hash useful for spotting that a file was altered."* |
| YES, it can be decrypted | *"Not quite — that is encryption, not hashing. SHA-256 has no key and no reverse operation."* |
| a variable-length message | *"Not quite. SHA-256 does not encrypt anything, and its output is never variable-length."* |

## The picture

`HashScene` is the **ninth** scene shape (ADR-048). A hash is an input of any size,
a fixed-size output, and *no positional relationship whatsoever between them* — and
that last clause is the lesson. `CipherScene` aligns two messages position by
position because Caesar letter 3 became ciphertext letter 3; digest character 3 came
from the whole message. `BitwiseScene` shares columns across rows, which is the same
lie in stronger form. Drawing either would assert the one thing that is false.

| Element | Treatment |
|---|---|
| pipeline | five `labelMedium` pills on `surfaceVariant`, wrapping, `→` between; the live stage takes `primarySoft`, completed stages `successSoft` |
| input card | `surfaceVariant`, the message in `titleSmall`, its length in the corner |
| the arrow | `↓ SHA-256 ↓` in `primary` — one arrow, one way, and the whole distinction from the two ciphers on the shelf |
| hash card | `surfaceVariant`, captioned `256 bits · 32 bytes · 64 chars` |
| the digest | `AlgoType.digest` — the app's one monospace style — in **eight groups of eight**, wrapping |
| a changed character | `next` amber, bold, per character position |
| comparison row | `surfaceVariant` card, `comparing` violet outline on the reference row and `next` amber on the row read against it, with `N in · 64 out` in the corner |
| claim card | `surface`, hairline `border`, the short word in the button's own tone above the full statement |

**Nothing scrolls sideways.** Sixty-four characters is wider than a phone, so the
digest wraps into groups and the pipeline wraps its stages — neither ever shrinks a
glyph to fit. That is ADR-039's rule from Dijkstra's graph and ADR-046's from
Caesar's alphabet: *the layout gives way, never the thing the learner has to read.*
Grouping is a reading aid only — `groups.joinToString("")` is the exact digest, and a
test says so.

**The fixed-length beat shows at most three rows.** The claim is "different input
lengths, one output length", and three rows — shortest, one in between, longest —
make it as completely as six would while leaving the statement cards and the buttons
above the fold. A row the learner has to scroll past to reach the question is not
evidence.

**The bright language, not a dark one.** Same cards, same 20dp radii, same violet,
same `surfaceVariant` grounds as every other lesson. A cryptography lesson is not an
excuse for a terminal aesthetic; the only thing that is not Plus Jakarta Sans is the
digest, and only so that two digests line up column for column.

## Edge cases — all tested

| | |
|---|---|
| the empty string | hashes fine (`e3b0c442…`); a dataset refuses it, because it is a poor thing to show first |
| a 10 000-character input | still 64 hex characters |
| hashing past the last message | a no-op returning the same state, never an exception |
| an answer that is not 0 or 1 | the same |
| a dataset with no messages, no questions, an empty message, or a repeated question | refused at construction, where it is authored |
| two identical digests | `differingPositions` is empty — which is the determinism beat's picture |

## Access, ads and progress

Nothing was added for any of them.

- **Free**, because it is filed under Cryptography and only the Advanced shelf is Pro
  (ADR-032, ADR-041). No flag, no exclusion list, no billing change. `ProAccess`,
  `SubscriptionRepository`, `PlayBillingGateway` and `PaywallScreen` were not touched.
- **Ads** are the existing single interstitial on the Complete screen for a free
  learner (ADR-042). No placement, no call site, no lesson-specific ad logic.
- **Progress** is the house rule — 0 / 50 / 100, derived from two latched booleans
  (ADR-028). There is no per-lesson progress code anywhere.

## Testing

**41 JVM unit tests** in `Sha256HashingTest`, no device needed, grouped as:

1. **The digests, against the outside world** — `hello`, all six lesson values and the
   empty string against `sha256sum`/`openssl`; 64 lowercase hex characters for every
   input including a 10 000-character one; 256 = 32 × 8 = 64 ÷ 2; determinism over a
   hundred runs; grouping never changes a value; `differingPositions` symmetric and
   empty for equal digests.
2. **The datasets** — no digest is authored anywhere; WATCH carries an avalanche pair,
   a repeated pair and a length ladder; TRY can answer every question it asks; both
   stages ask the same five in the same order; bad problems are refused.
3. **The state machine** — hashing happens before any judgement; a full run
   terminates; digests are never stored on the state; over-long action sequences are
   no-ops; rewind is exact.
4. **The judgements** — two options, the correct one on offer, none auto-answered, a
   three-rung ladder and a named wrong option; the true statement is not always in
   the same seat; a wrong answer leaves the state byte-for-byte identical five times
   over; guidance escalates then holds; every question must be answered before the
   lesson completes.
5. **The picture** — the pipeline is always five stages; nothing is printed before it
   is produced; the avalanche beat marks exactly the computed positions (61); the
   determinism beat marks nothing; the fixed-length beat shows different inputs and
   identical output lengths; both claims are on screen whenever one is asked for; no
   digest reaches the screen ungrouped.
6. **The walkthrough** — every step changes something visible; every property gets a
   beat; **each property beat is captioned with the picture it was given**; the recap
   ends on the two caveats; the avalanche count is computed rather than authored.
7. **The lesson in the library** — registered and reachable by id; no challenge, and
   why; progress 0 → 50 → 100, latched and additive.
8. **What it cost everything else** — every one of the 26 packs still builds its
   walkthrough, and both ciphers still produce exactly what they did.

Engine suite: **849 tests, 0 failures** (808 before this lesson). App suite: **48
tests, 0 failures** (46 before).

## Not yet verified

**On a device.** No device was attached, so these are checked by unit test and
arithmetic only:

1. **The digest at 320dp.** Eight groups of eight monospace characters at 12sp wrap
   through `FlowRow`; at 360dp that should be two rows of four and at 320dp three
   rows. If a group ever breaks mid-way the fix is the rule ADR-039 set — change the
   grouping or the layout, never shrink the glyph.
2. **The two claim cards side by side.** About 144dp each at 360dp, holding up to
   about 50 characters at `bodyMedium`. They will be four or five lines tall on the
   longest statement; if that reads badly the answer is to stack them, not to trim
   the statements, because an ambiguous claim is worse than a tall card.
3. **The tallest configuration** — pipeline, three comparison rows, two claim cards
   and two buttons, on the fixed-length beat.

## Deferred

- **Free-form input.** The brief allows predefined messages, and the mechanical
  hashing beats already are the "Hash It" demonstration (§9): the app really hashes
  a real string and shows the real digest. A text field would add an input surface,
  a keyboard, validation and a Compose test for one beat.
- **A CHALLENGE.** V2 (ADR-031), and this one may never have one: the judgements are
  about what hashing *guarantees* rather than a step to execute, so there is no run
  to generate a fresh dataset for. `ChallengeCatalog.byId` returns null and says why.
- **Collision resistance and birthday bounds.** Named honestly in this document, out
  of scope for a beginner lesson.
- **The internals as a second lesson.** Padding, the message schedule and the
  compression function are a real lesson for someone who has this one — and they are
  a different lesson, not a longer one.
