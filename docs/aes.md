# AES — four steps a round, and the last one is different

**Status:** built · 2026-09-17 · MVP scope is **WATCH → TRY → COMPLETE** · unit-tested; **on-device pass still outstanding**
**Category:** Cryptography · **PRO** · **Engine:** `engine/algorithms/aes/` · **Scene:** `BlockCipherScene`
**Decision:** ADR-049

> **AES is a block cipher, not a security system.** It encrypts one 128-bit block
> with one key. Turning that into a way to protect a message takes a mode of
> operation — a real system wants an authenticated one such as **AES-GCM**, and not
> ECB. The lesson says so in its recap and again on the Complete screen, and it
> never calls AES "unbreakable".

---

## What it is

A **symmetric block cipher**: one key both encrypts and decrypts, and it works on a
fixed number of bytes at a time.

```
plaintext → 16-byte block → 4 × 4 State
                                 │
                         AddRoundKey  (round key 0)
                                 │
   SubBytes → ShiftRows → MixColumns → AddRoundKey     × (rounds − 1)
                                 │
   SubBytes → ShiftRows →                AddRoundKey    ← the final round
                                 │
                            ciphertext
```

The thing worth taking away is the *shape*: one block size, one square, and a round
that is always the same four transformations — except the last, which leaves
MixColumns out.

## The numbers

| | |
|---|---|
| Block | **128 bits · 16 bytes**, in every variant |
| State | **4 × 4 bytes**, filled column by column |
| AES-128 | 128-bit key · **10 rounds** · 11 round keys |
| AES-192 | 192-bit key · **12 rounds** · 13 round keys |
| AES-256 | 256-bit key · **14 rounds** · 15 round keys |

**The number in the name is the key size, not the block size.** That is the single
commonest misreading of AES, and it is why the variant table prints the block size
on every row — identically — rather than only the thing that differs.

There is always **one more round key than there are rounds**, because the initial
AddRoundKey spends round key 0 before round 1 begins.

## The four transformations

| | What changes | What does not |
|---|---|---|
| **SubBytes** | every byte, through the S-box | nothing moves |
| **ShiftRows** | row `r` rotates left by `r` — row 0 stays put | no value changes |
| **MixColumns** | each column mixed into itself | nothing crosses a column |
| **AddRoundKey** | the State XORed with this round's key | — |

SubBytes and ShiftRows are exact opposites in what they touch, and on screen they
read that way: SubBytes marks all sixteen bytes and moves none; ShiftRows marks
twelve and changes no value. Both are *pictures*, computed by comparing the State on
either side of the step — never sentences the copy asserts.

### Why the final round omits MixColumns

Mixing the columns at the very end would add nothing a decrypting party could not
immediately undo, so the standard does not spend it. The lesson makes the omission
**visible rather than described**: MixColumns keeps its place in the round strip and
is struck through. A step that simply vanished would say nothing.

## Every byte on screen is real

This is the only lesson in the library whose picture is a real algorithm's
*intermediate* values, and that raised the one question ADR-048 settled the other
way for SHA-256.

SHA-256 delegates to `MessageDigest` and draws its 64 compression rounds as a single
labelled box, because its internals are explicitly not that lesson and **drawing
invented ones would be worse than drawing none**. Here the State changing *is* the
lesson — so the same rule points the other way: the transformations are computed for
real, and `engine/core/Aes.kt` implements them.

What keeps that honest rather than merely confident is the verification:

| Checked against | What it proves |
|---|---|
| the S-box **regenerated from its definition** — GF(2⁸) inverse, then the affine transform | not one of the 256 bytes is mistyped |
| **FIPS-197 Appendix C.1** | the canonical AES-128 vector, end to end |
| **FIPS-197 Appendix B**, including its round-by-round States | the *intermediate* values, which is what the picture actually draws |
| the **JDK's own AES**, for all three variants | the engine agrees with something nobody here wrote |

That last pair matters most. A cipher can produce the right ciphertext from
wrong-but-self-cancelling steps; asserting the published `s_box`, `s_row` and
`m_col` States for round 1 is what protects the thing on screen.

**No ciphertext, State or round key is authored anywhere.** Every value is computed
from the plaintext and key — ADR-045's rule for Fibonacci's call counts and
ADR-048's for SHA-256's digests, applied to the one lesson where a stale hardcoded
value would be invisible.

## The datasets

### WATCH — `"AlgoKing Rules!!"`, FIPS-197 Appendix C.1's key

Sixteen readable characters, which is exactly one block. Three properties earn it
its place:

- **it is exactly one block**, so "AES works on 128 bits at a time" is something the
  learner counts rather than something the copy asserts — and padding, a second idea
  entirely, never has to be explained;
- **it is text**, so the first pipeline stage saying *Plaintext* means something. Its
  bytes are printable ASCII before round 1 and none of them is after;
- **it shares nothing with TRY** — not one input byte, not one key byte.

### TRY — **FIPS-197 Appendix B**

`3243f6a8885a308d313198a2e0370734` under `2b7e151628aed2a6abf7158809cf4f3c`, the
worked example the standard itself walks through, giving `3925841d02dc09fbdc118597196a0b32`.

A different block and a different key, so TRY is application rather than recall
(ADR-014) — and it is also the most-checked sixteen bytes in cryptography, so a
learner who wants to verify the lesson against the standard can.

**About the keys:** both are *published test vectors*. They exist so independent
implementations can be checked against each other, which is exactly what they are
used for here. Nothing in AlgoKing generates, stores or transmits a key.

## WATCH — 19 beats

| # | Beat | |
|---|---|---|
| 0 | what a symmetric block cipher is | |
| 1–3 | the message, the block, the State | filled column by column |
| 4 | Key Expansion → 11 round keys | |
| 5 | **the initial AddRoundKey**, before round 1 | |
| 6–9 | **round 1, in full** — SubBytes · ShiftRows · MixColumns · AddRoundKey | |
| 10 | rounds 2–9, collapsed into one beat | |
| 11–13 | **the final round, in full** — and the third names the omission | |
| 14 | the ciphertext | |
| 15 | the three variants | one block size, three key sizes |
| 16 | decryption — the pipeline turned round, the inverses named | |
| 17 | **INSIGHT** — *the same four steps, ten times over, and the last round leaves one out* | |
| 18 | recap, seven bullets, the last two the caveats | |

**The middle is collapsed** by ADR-025's rule — narrate the smallest prefix that
builds the model, then stop. Round 1 establishes the pattern and the final round
breaks it; eight more identical rounds in between would be thirty-two taps of the
same thing. Both ends are narrated in full because both ends are the lesson.

### The bug the walkthrough dump caught

A frame is drawn from the state *after* its transition, so the frame that applies the
last step of the run is also the frame the first TRY question is pending on — the two
are the same state, and no amount of deriving can tell them apart. The closing beat
about decryption was therefore drawn with the first question's evidence over it.

That is the failure ADR-047 records for XOR's final frame and ADR-048 for SHA-256's
whole run, arriving through a third door. The fix is an inert **`READY`** step: the
run ends on a frame where nothing happens, so the collision lands somewhere with
nothing to collide. WATCH narrates no beat there; TRY asks its first question over a
neutral picture of the finished ciphertext.

Found, as all three have been, by dumping the walkthrough and reading it. **A
walkthrough dump is still a test the test suite cannot write.**

## TRY — eleven decisions, six exercises

| # | Exercise | Asked as | Answer |
|---|---|---|---|
| 1 | How large is an AES block? | four buttons: 64 · **128** · 192 · 256 | 128 bits |
| 2 | How many bytes does the State hold? | four buttons: 4 · 8 · **16** · 32 | 16 bytes |
| 3 | Which transformation comes next? | **a tap on the round**, four times | SubBytes → ShiftRows → MixColumns → AddRoundKey |
| 4 | Which one does the final round leave out? | **a tap on the round** | MixColumns |
| 5 | How many rounds does each variant run? | three buttons, three times | 10 · 12 · 14 |
| 6 | What produces the round keys? | two buttons + claim cards | Key Expansion |

### Exercises 3 and 4 are taps, not words

The obvious build gives four buttons reading SubBytes / ShiftRows / MixColumns /
AddRoundKey. They do not fit one row at `labelLarge` — "AddRoundKey" alone is wider
than the button it would sit in, the wall AVL hit with its four case names (ADR-037)
and Two Pointers with "Move RIGHT" (ADR-032).

But the better reason is ADR-034's, the one that refused a BACKTRACK button: **the
round's steps are already drawn, and pointing at the next one is what understanding
an order looks like.** Picking its name off a list is what recognising a word looks
like. So the round strip is the control, each step clears the 48dp touch minimum, and
the steps already placed stay filled in as the learner rebuilds the round.

### What the app does, and why that is not a loss

Everything a learner cannot check by hand. SubBytes is a 256-entry table lookup and
MixColumns is multiplication in GF(2⁸) — PRODUCT_SPEC.md §3 gives the app the
arithmetic, and asking for a byte would be a gesture over maths the learner has no
way to verify. The app encrypts; the learner explains it back.

That is the same stretch of the interaction model ADR-048 recorded for SHA-256, and
it is worth naming as one. What keeps it from being a quiz is where the answer comes
from: every question is asked with the run on screen, and the projector shows the
part of the picture that settles it.

### Wrong answers

A wrong answer is a learning event, never a state transition (ADR-021). Nothing
advances, the same question stays on screen, and the ladder escalates. Every wrong
option is answered by name:

| Wrong | Says |
|---|---|
| 192 or 256 for the block | *that is an AES **key** size — the number in the name. The block does not change between variants* |
| 64 for the block | *that is DES's block, and it is half of AES's* |
| 32 bytes for the State | *that is 256 bits, which is AES-256's key* |
| a transformation already applied | *it has already run in this round* |
| a transformation still to come | *it is in the round, but not yet* |
| a transformation as "skipped" | *it runs in every round, including the last* |
| the wrong round count | *that is AES-192's, not AES-256's* |
| the S-box for the round keys | *it substitutes bytes inside SubBytes; it is not what produces them* |

Two of these — "shifted the wrong way" for Caesar, "the number in the name" here —
are the misconception the lesson exists to correct, and both are on the table at
every beat rather than only where they happen to be wrong.

## The picture

**`BlockCipherScene`**, the tenth scene shape (ADR-049), drawn by `BlockCipherStage`.

```
Plaintext → Block → State → Rounds → Ciphertext

┌ Round 3 / 10                          SubBytes ┐
│  ✓SubBytes  ✓ShiftRows  *MixColumns  ·AddKey   │
│  every byte swapped through the S-box · nothing moves │
└────────────────────────────────────────────────┘

        ┌────┬────┬────┬────┐
        │ 3b │ 9c │ 7f │ b4 │
        ├────┼────┼────┼────┤      the State — slot = row + 4 × column,
        │ c7 │ 38 │ 7e │ e4 │      so a column is four contiguous slots,
        ├────┼────┼────┼────┤      which is what MixColumns works on
        │ b8 │ 96 │ 99 │ 25 │
        ├────┼────┼────┼────┤
        │ 05 │ 1f │ 79 │ 84 │
        └────┴────┴────┴────┘
```

| Means | State | Reads |
|---|---|---|
| the block becoming the State | `COMPARING` violet | The State |
| a byte this step changed | `CANDIDATE` amber | Changed |
| a byte it did not | `IDLE` | Unchanged |
| the finished ciphertext | `FINALIZED` green | Ciphertext |

Cells are the shared `SceneCell` carrying a hex label — the call Caesar made for its
letters (ADR-046), which is what stops a cryptography lesson forking the visual
language.

**Nothing scrolls sideways.** Four byte cells across is the least cramped grid in the
app, and the round strip wraps rather than shrinking its labels — ADR-039's rule from
Dijkstra's graph.

**The bright language, not a dark one.** Same cards, same 20dp radii, same violet,
same `surfaceVariant` grounds as every other lesson. A cipher is not a licence for a
terminal aesthetic.

## Access — Pro, and the first lesson that is Pro without being Advanced

AES is **Pro**. It is also filed under **Cryptography**, because that is what it is,
and putting a block cipher on a shelf labelled "Advanced" would print the wrong word
on its card.

Those two had never had to come apart before. ADR-041 made "the Advanced shelf is the
Pro shelf" the whole access rule, and every paid lesson until now was paid because of
its category. ADR-049 adds a second, narrow rule in the **same object**:

```kotlin
fun requiresPro(category: String, id: AlgorithmId): Boolean =
    category == PRO_CATEGORY || id in PRO_LESSONS
```

- there is still **exactly one place** access is decided, and no `isPro` flag on
  `AlgorithmEntry` — the parallel taxonomy ADR-032 refused;
- both arguments are **required**, so a caller that knows only the category cannot
  answer the question wrongly by accident. That is a compile error rather than a
  silently wrong `false`;
- `PRO_LESSONS` is deliberately tiny. A long list would mean the categories have
  stopped describing the library, and the fix then is the categories.

**Nothing else in billing changed.** `SubscriptionRepository`, `PlayBillingGateway`,
`ProEntitlement` and `PaywallScreen` are untouched apart from one derived count. A
free learner tapping AES gets the existing paywall, contextual to AES; a subscriber
opens the lesson.

The Pro shelf is now **thirteen** lessons and fourteen stay free. The paywall's count
is **computed from the library** rather than written down — it said "eleven" while
the shelf held twelve, which is what a hardcoded number does eventually.

## Ads

Nothing was added. AES is Pro, so a subscriber sees no ads by the existing policy
(ADR-042), and a free learner cannot reach the lesson at all. `Placement` still has
exactly one member and there is no AES-specific ad logic anywhere.

## Progress

The standard MVP model, with no additions: 0 % → **50 %** on WATCH → **100 %** on
WATCH + TRY, independent and latched. AES gets this for free — progress is
`Stage`-driven and has no per-algorithm code anywhere (ADR-028).

## Complexity

| | |
|---|---|
| Encrypting one block | **O(1)** — a fixed number of rounds over a fixed 16 bytes |
| A message of `n` bytes | **O(n)** — one block at a time, under a mode of operation |
| Key Expansion | **O(1)** per key, done once and reused for every block |

Hardware AES instructions (AES-NI and the ARMv8 extensions) make this a few cycles
per block on any modern device, which is the practical reason AES is everywhere.

## What it cost the architecture

| | |
|---|---|
| New `VizEvent` types | **0** — `Examine`, `Meter`, `Finalize`, `Terminal` |
| New interaction models | **0** — `OPTIONS` buttons and the `CELL` tap the graph and counting lessons established |
| New `Scene` shapes | **1** — `BlockCipherScene`, the tenth |
| New screens or controllers | **0** |
| Changes to existing lessons | **0** |
| New design tokens | **1** — `Dimens.roundKeyLabelWidth` |
| Shared model additions | `Dataset.aes` (defaulted), `AlgorithmId.AES` |
| Access-rule change | `ProAccess.requiresPro`/`decide` take the lesson id (ADR-049) |
| Files added | 5 engine · 1 renderer · 2 test |
| Tests | 71 engine + 9 app (engine 849 → **920**, app 48 → **57**) |

The shape earns its place the way the nine before it did: a 4 × 4 grid whose **rows
and columns are each named by a transformation that acts on them** is a new kind of
data. `DpTableScene` is the near miss — genuinely rows × columns, but its axes are
two *quantities* and a cell is a point in that space, while the State's axes are a
byte's position in a block and the grid is the same sixteen bytes rearranged. It also
carries item cards, a bag meter and a two-sided choice strip a cipher would null out,
which is the union-pretending-to-be-a-record ADR-047 refused.

## Edge cases — all tested

A block or key of the wrong length (refused at construction) · an empty or duplicated
question list (refused) · bad hex (refused, and whitespace tolerated so an authored
key is readable in groups) · a position outside a normal round (refused) · an
over-long action sequence (a no-op, never an exception) · an answer outside the
options (a no-op) · a wrong answer applied directly (the run stays legal and
terminates) · adversarial driving over 40 random sequences · rewind exactness.

## Not yet verified

**On a device.** No device was attached during the build, so these are checked by
unit test and arithmetic only:

1. **The 4 × 4 State at 320dp.** Four cells across is the least cramped grid the app
   has, so this is the safest of the recent lessons' layout risks — but two hex
   characters at `sceneNumeral` in roughly 76dp has not been seen on a real screen.
2. **The round strip's four chips.** They wrap through a `FlowRow`; at 360dp
   "AddRoundKey" and "MixColumns" will likely take a second line, which is intended.
   What needs looking at is whether a *selectable* chip at the 48dp minimum leaves
   the State above the fold.
3. **The key schedule card.** Three 32-character hex strings at `AlgoType.digest`,
   which has only ever been drawn by the SHA-256 lesson before.
4. **The tallest configuration** — pipeline, round card, State, key schedule and two
   claim cards, on the Key Expansion question.

## Deferred

- **Decryption as a stage.** It is the inverse steps in reverse; the lesson names
  them and turns the pipeline round, and building a second interactive walkthrough
  would teach the same rule twice.
- **A real AES-GCM demo.** The lesson already performs real AES — a real key, a real
  block, a real ciphertext, verified against the JDK — so a second live demonstration
  would add an input surface and a nonce discussion for one beat. The recap names
  AES-GCM as what a real system reaches for, which is the part that transfers.
- **The key schedule, interactively.** RotWord, SubWord and the round constants are a
  lesson of their own, and one that needs the learner to want it.
- **Modes of operation.** CBC, CTR and GCM are the natural sequel and are a lesson
  about *messages*, not about the cipher. This one deliberately stops at one block.
- **A CHALLENGE.** `ChallengeCatalog.byId(AES)` returns null, as for every Advanced
  lesson (`v2-challenge.md`) — and for the reason SHA-256's does: the judgements are
  about what AES *is* rather than a step to execute, so a seeded variant would change
  the bytes on screen and not one of the questions.
