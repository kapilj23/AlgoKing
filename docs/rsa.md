# RSA — use the keys, then find out where they came from

**Status:** built · re-ordered story-first 2026-09-21 · MVP scope is **WATCH → TRY → COMPLETE** · unit-tested; **on-device pass still outstanding**
**Category:** Cryptography · **PRO** · **Engine:** `engine/algorithms/rsa/` · **Scene:** `KeyPairScene`
**Decisions:** ADR-050 (the lesson) · **ADR-052 (the two acts)**

> **⚠ RE-ORDERED — 2026-09-21, product owner.** The lesson used to open on key
> generation: its second judgement was `n = p × q` and its third was `φ(n)`. It now
> runs in **two acts** — Act I shows a message going out through one key and coming
> back through the other, with the pair handed over as given; Act II explains where
> that pair came from. No `p`, `q`, `φ(n)`, `e` or `d` appears before the round trip
> closes. The engine, the toy numbers, the arithmetic, the Pro gate and the TRY
> architecture are unchanged (ADR-052). Where this note and a passage below disagree,
> this note wins.

> **The numbers in this lesson are a demonstration, not security.** `n = 55` factors
> by inspection, so anyone holding the public key has the private one too. That is
> said on the picture for the whole lesson, gets a beat of its own, and is repeated
> on the Complete screen. Real RSA uses keys of 2048 bits or more and a padding
> scheme — **OAEP** — because textbook RSA is deterministic and not safe as it
> stands.

---

## What it is, and why it comes last

RSA is **asymmetric**: two mathematically related keys, where what one does the other
undoes, so one of them can be published.

Every cipher before it on this shelf shares one key between both sides. Caesar, XOR
and AES all leave the same question open — *how do two people who have never met
agree on that key?* — and RSA is an answer. That is why it is last, and it is the
whole reason the lesson opens on the question rather than on the arithmetic.

```
p, q  ──►  n = p × q
      ──►  φ(n) = (p − 1)(q − 1)
      ──►  e,  with gcd(e, φ(n)) = 1
      ──►  d,  with d × e ≡ 1 (mod φ(n))

public (e, n)   ──►   c = mᵉ mod n
private (d, n)  ──►   m = c^d mod n
```

## The toy example

The brief's worked example, verified before anything was built on it:

| | |
|---|---|
| `p`, `q` | 5, 11 |
| `n` | 5 × 11 = **55** |
| `φ(n)` | 4 × 10 = **40** |
| `e` | **3**, because gcd(3, 40) = 1 |
| `d` | **27**, because 3 × 27 = 81 and 81 mod 40 = 1 |
| Public key | **(3, 55)** |
| Private key | **(27, 55)** |
| `m` | 4 |
| `c` | 4³ mod 55 = 64 mod 55 = **9** |
| back | 9²⁷ mod 55 = **4** |

**Only four of those are authored** — `p`, `q`, `e` and `m`. Everything else is
computed by `engine/core/Rsa.kt`, which is ADR-045's rule for Fibonacci's call counts
applied to a lesson that *is* a chain of dependent values. A stored `d` would be a
second source of truth for the one number the learner is asked to justify.

### How the arithmetic is checked

There is no published NIST vector for a 55-bit — let alone a 55 — modulus, so the
engine is checked against things outside itself:

| Checked against | What it proves |
|---|---|
| **`java.math.BigInteger`** over thousands of random values | `modPow`, `gcd`, `modInverse` and `isPrime` are right |
| every message under `n`, round-tripped | the key pair works generally, not by coincidence |
| **a real 2048-bit RSA key pair** from `KeyPairGenerator` | the lesson's two formulas *are* what real RSA does |

That last one is the lesson's central claim turned into a test: it pulls `(n, e, d)`
out of a genuine JDK key and shows `m^e mod n` round-tripping through it. The copy
says "this is the same arithmetic with bigger numbers", and the suite checks it.

### Why RSA is implemented rather than delegated

`Sha256` delegates to `MessageDigest` because its internals are not that lesson
(ADR-048). RSA is the opposite case, and more sharply: the whole lesson is those five
lines of arithmetic, so they are written out and everything on screen is read back
out of them.

It is also not a choice. The platform's RSA will not touch a toy modulus —
`KeyFactory` rejects anything under 512 bits, and rightly — which is itself part of
what the lesson is careful to say.

## WATCH — 24 beats, in two acts

**Act I — what RSA does.** No `p`, `q`, `φ(n)`, `e` or `d`, anywhere.

| # | Beat | |
|---|---|---|
| 0 | two strangers need to agree a secret | the question the other ciphers leave open |
| 1 | here is the message: 4 | + *what is two related keys called?* — cards |
| 2 | this is asymmetric cryptography | what one does, the other undoes |
| 3 | two keys: `(3, 55)` and `(27, 55)` | **handed over as given** + *which can you share?* |
| 4 | the public key is the one you publish | + *so which half must never leave?* |
| 5 | only one of them has to be kept | + *send the message through the public key — what happens?* |
| 6 | the public key encrypts 4 | the flow's verb lands; the working appears, unfinished |
| 7 | `c = 4³ mod 55 = 9` | **the mathematics, beside the flow it explains** |
| 8 | 4 is now 9 | the pause on the ciphertext + *now the private key — what happens?* |
| 9 | the private key decrypts 9 | |
| 10 | `m = 9²⁷ mod 55 = 4` | |
| 11 | **4 → 9 → 4** | the round trip, on one picture. *This is where Act I ends.* |

**Act II — where those keys came from.**

| # | Beat | |
|---|---|---|
| 12 | so where did `(3, 55)` and `(27, 55)` come from? | the chain appears, empty |
| 13 | start with two primes: 5 and 11 | the only secret inputs |
| 14 | `n = 5 × 11 = 55` | the modulus both keys carry |
| 15 | `φ(n) = 4 × 10 = 40` | the modulus `e` and `d` are inverses in |
| 16 | choose `e = 3` | gcd(3, 40) = 1 |
| 17 | and `d = 27` | 3 × 27 = 81, one more than a multiple of 40 |
| 18 | that gives the public key `(3, 55)` | **the pair they have been using all along** |
| 19 | and the private key `(27, 55)` | + *what does having the pair let you do?* |
| 20 | encrypt with one, decrypt with the other | |
| 21 | **and this is the toy version** | the caveat, as its own beat |
| 22 | **INSIGHT** — *two keys, built from the same chain, and only one has to be kept* | |
| 23 | recap, seven bullets, the last two the caveats | |

**Nothing is collapsed.** ADR-025's rule is about repetition — AES's ten identical
rounds, Bubble Sort's later passes — and there is none here: every beat is a different
idea.

### Why the order is the lesson

The old script reached `n = p × q` on beat three and the round trip on beat eleven. A
learner on beat three cannot yet say what RSA is *for*, and a totient without a purpose
is a fact to be memorised rather than a step in an argument. Now the arithmetic arrives
as the explanation of something already watched working (ADR-052).

Guarded by `the story is asked before the arithmetic` in the engine tests, and by
`the story is told before any key generation` in the app's copy tests, which reads the
resolved sentences of Act I and fails if the words *prime*, *totient*, *φ*, `p × q`,
*gcd* or *inverse* appear in any of them.

### What Act I is allowed to say

`c = m^e mod n`, with its numbers filled in. The line being drawn is **key
generation**, not arithmetic: the brief asks for the mathematics of the transformation
the learner has just watched, so `e` and `n` appear as the two numbers inside the
public key they have been handed. *Where those numbers came from* is the question Act
II exists to answer.

### Frames with cards on them are captioned twice over

A frame is drawn from the state *after* its transition, so the frame that passes a
beat is also the frame the **next** question is pending on. For the six judgements that
settle a value that is exactly right — the frame shows the value landing and the next
one as `?`.

For the six answered by tapping cards it is backwards: the cards appear on the previous
beat's frame. The first version of the lesson handled that by sacrificing that beat's
caption, which was affordable when only two judgements were cards and the beats before
them had nothing of their own to say.

With six, all six of those beats do. So such a frame now carries **both** — the landed
beat's headline, and a support line introducing the choice underneath. That is what the
frame honestly shows, and it is ADR-048's rule (*a frame is captioned by what its own
scene shows*) applied to a scene showing two things.

Each beat keeps its own two-sentence caption for the **statement path** — the one a
dataset that does not ask that question takes — and `a dataset that asks nothing still
narrates every beat` drives exactly that, so those six captions are correct rather than
dead code.

Found, as the last six have been, by dumping the walkthrough and reading it.

## TRY — twelve decisions, in the same two acts

Seven story judgements, then five about the arithmetic. TRY and WATCH run **one
script**, so the progression is identical.

| # | Exercise | Asked as | Answer |
|---|---|---|---|
| 1 | Which kind of cryptography uses two keys? | **four stacked cards** | Asymmetric |
| 2 | Which of these two can you share? | **four stacked cards** | The public key |
| 3 | Which key must remain secret? | **four stacked cards** | The private key |
| 4 | The message goes through the public key — what happens? | **four stacked cards** | Encrypt it |
| 5 | `c = mᵉ mod n`? | four buttons | 9 |
| 6 | Now it goes through the private key — what happens? | **four stacked cards** | Decrypt it |
| 7 | What was the message? | four buttons | 4 |
| 8 | What is `n`? | four buttons: 40 · **55** · 16 · 44 | 55 |
| 9 | What is `φ(n)`? | four buttons: **40** · 55 · 44 · 10 | 40 |
| 10 | Which value can be `e`? | four buttons | 3 — the only one coprime to 40 |
| 11 | Which value is `d`? | four buttons | 27 |
| 12 | What does the key pair let you do? | **four stacked cards** | Encrypt with one, decrypt with the other |

### The two that became statements

*"Which pair is the public key?"* and *"and the private key?"* are no longer asked.
Both pairs have been on screen since beat 3, so asking in Act II is asking a learner to
read a card.

They are **not deleted**: `RsaQuestion.PUBLIC_KEY` and `PRIVATE_KEY`, their four
distractors each and all their copy are untouched, and `stepsFor` turns any unasked
question into a statement — `the two key-assembly questions are stated, not asked`
builds a dataset that asks them both and checks they still work. The two exponents
behind them, `e` and `d`, are still asked, and they are the part nobody could have read
off the screen.

### The questions are interleaved, not asked afterwards

SHA-256 and AES run their whole algorithm and then ask about it, because their
questions are about the run as a whole (ADR-048, ADR-049). RSA's are about *links in a
chain*, and a chain shown whole before being asked about is one the learner reads off
rather than derives.

So **every value is asked at the point it would be computed**, with the values it
depends on already on screen and its own place showing `?`. That is the hash flow's
rule (ADR-030) applied to a dependency chain, and it is what makes the picture
evidence rather than an answer key.

### Six judgements are cards, not buttons

"Asymmetric cryptography" does not fit on a `DecisionButton` — four share a row at
360dp, about 76dp each. That is the wall Two Pointers hit with "Move RIGHT" (ADR-032)
and AVL with its four case names (ADR-037).

So the six **concept** judgements are full-width stacked cards, each with its title and
the clause that makes it unambiguous, each clearing the 48dp touch minimum. The other
six are numbers, which fit comfortably. Same split AES made between its round strip and
its numeric buttons (ADR-049).

The split falls exactly along the two acts, which is not a coincidence: a question about
what RSA *does* cannot be answered with a number, and a question about which number a
formula produces does not need a paragraph.

### Every wrong option is a named misconception

Distractors are **formulas, not numbers**, so they stay wrong whatever key pair the
lesson runs on and none of them can go stale:

| Wrong | What it is | What the copy says |
|---|---|---|
| 40 for `n` | φ(n) | *that comes next, and is a different number* |
| 16 for `n` | `p + q` | *RSA multiplies the primes rather than adding them* |
| 44 for `n` | `(p − 1) × q` | *n uses both primes as they are* |
| 55 for `φ(n)` | n | *φ(n) takes 1 off each prime first* |
| 2, 4, 5 for `e` | share a factor with 40 | *gcd is not 1, so no d would exist* |
| 3 for `d` | e | *d is the number that undoes it* |
| (55, 3) | swapped | *a key is written (exponent, modulus)* |
| (3, 40) | uses φ(n) | *φ(n) never goes into a key — anyone who had it could work d out* |
| 64 for `c` | `mᵉ` unreduced | *taking the remainder mod 55 is what finishes it* |
| 24 for `c` | reduced by φ(n) | *encryption uses n* |
| 9 for `m` | the ciphertext | *decrypting has to give back something different* |

On the lesson's own numbers the `n` and `φ(n)` options come out as exactly the lists
the brief names — 40 / 55 / 16 / 44 and 40 / 55 / 44 / 10 — which is a good sign the
formulas are the ones learners actually get wrong.

## TRY uses a different key pair, and that is a departure from the brief

§10 lists the TRY exercises using WATCH's numbers. That would make **six of the ten
judgements answerable from memory**: a learner who watched `n = 55` land does not have
to multiply anything to answer it again.

ADR-014 is explicit that a TRY dataset must not allow that, and this project has
replaced a brief-supplied dataset for exactly this reason twice before — ADR-044, when
0/1 Knapsack's bag had an optimum both greedy strategies already found, and ADR-047,
when XOR's TRY key produced the same ciphertext WATCH produced.

So the **questions** are the brief's ten, unchanged and in its order, and the
**numbers** are new:

```
p = 7, q = 13
n    = 91        φ(n) = 72
e    = 5,  gcd(5, 72) = 1
d    = 29, 5 × 29 = 145 = 2 × 72 + 1
c    = 4⁵ mod 91 = 1024 mod 91 = 23
```

The message stays `4` on purpose: it is the one value the learner is not asked to
derive, and holding it still is what makes the two runs comparable. `e = 5` also does
something `e = 3` cannot — its unreduced power is `4⁵ = 1024`, four digits against a
two-digit modulus, so the "forgot the modulus" distractor is visibly absurd rather
than merely wrong.

## The picture

**`KeyPairScene`**, the eleventh scene shape (ADR-050), drawn by `KeyPairStage`.

**Two acts, two pictures, never both at once** (ADR-052).

**Act I — the flow, and the arithmetic beside it.** Side by side above
`Dimens.twoColumnMinWidth` (520dp — a tablet or a landscape phone), stacked below it,
because two 140dp columns at 360dp would put the lesson's numbers at a size nobody
should have to squint at.

```
┌ ENCRYPTING ─────────┬ THE MATHEMATICS ────────┐
│  MESSAGE      4     │  c = m^e mod n          │
│      ↓              │  m = 4                  │
│  PUBLIC KEY (3,55)  │  e = 3                  │
│      ↓              │  n = 55                 │
│  ENCRYPT            │  c = 4³ mod 55          │
│      ↓              │  c = 64 mod 55          │
│  CIPHERTEXT   9     │  c = 9        ← settled │
└─────────────────────┴─────────────────────────┘

┌ PUBLIC KEY ────────┐  ┌ PRIVATE KEY 🔒 ┐
│ (3, 55)            │  │ (27, 55)       │
│ Share it freely    │  │ Never share it │
└────────────────────┘  └────────────────┘

MESSAGE    4  →encrypt→  9  →decrypt→  4
```

**The flow's verb node is blank while the learner is being asked which verb it is** —
`ENCRYPT` reads `?` during judgement 4. That is the chain's own `?` rule applied to a
flow, and it is what makes the two operation judgements askable at all.

**The arithmetic's last line is withheld while that value is the question.** While
`c` is being asked the panel ends at `c = 4³ mod 55`; `c = 64 mod 55` and `c = 9`
arrive together once it is settled. The reduction line is the one that makes `mod`
mean something rather than being a symbol, and it is deliberately not shown early —
anyone who can subtract would read the answer straight off it.

**Act II — the chain.** Empty for the whole of Act I: five rows of `?` would put every
symbol the re-ordering exists to delay on the very first screen, wearing a disguise.

```
┌ KEY GENERATION ─────────────────────────┐
│  p, q    two different primes    5, 11  │
│    ↓                                    │
│  n       p × q                     55   │   every value stays on screen,
│    ↓                                    │   because the next one reads it
│  φ(n)    (p − 1)(q − 1)            40   │
│    ↓                                    │
│  e       gcd(e, φ(n)) = 1           ?   │   ← being asked for
└─────────────────────────────────────────┘
```

| Means | State | Reads |
|---|---|---|
| being asked for | `COMPARING` violet | Being asked |
| just derived | `CANDIDATE` amber | Just derived |
| settled, and read by later rows | `FINALIZED` | Settled |
| not reached | `GHOST` | Not yet |

**A `?` is the most important thing it draws.** A value the lesson has not produced is
absent — never zero, never guessed — so the picture can never answer the question
being asked. The arrows between rows are what makes it a chain rather than a list.

**The two keys are drawn together, always.** They share a modulus and differ in one
number and one rule, and a learner who meets them apart can read them as two unrelated
keys — which is the misconception the lesson is for. The private one carries a small
lock in the ornament gold the app already uses for a Pro crown and a streak: a key
that must be kept is a normal thing, not an alarm.

**The caveat is on the picture, not only in the recap** — a quiet amber line for the
whole lesson, and a full card on the one beat that is about it. A caveat attached to
the picture cannot be skipped the way a recap bullet can.

## Access — Pro, and the second lesson that is Pro without being Advanced

RSA is **Pro**, filed under **Cryptography**, and it needed **no change to the access
rule at all** — only its id added to the set ADR-049 created:

```kotlin
val PRO_LESSONS: Set<AlgorithmId> = setOf(AlgorithmId.AES, AlgorithmId.RSA)
```

That is the first time rule 2 has covered more than one lesson, and it is the
evidence ADR-049's design was the right shape: a second lesson that is a real cipher
rather than a teaching device, on the same shelf, for the same reason, cost one line.

Nothing else in billing moved. `SubscriptionRepository`, `PlayBillingGateway`,
`ProEntitlement`, `ProAccess.decide` and `PaywallScreen` are untouched — the paywall's
lesson count is computed from the library, so it says "fourteen" by itself.

## Ads

Nothing was added. RSA is Pro, so a subscriber sees no ads by the existing policy
(ADR-042), and a free learner cannot reach the lesson at all. `Placement` still has
exactly one member and there is no RSA-specific ad logic anywhere.

## Progress

The standard MVP model: 0 % → **50 %** on WATCH → **100 %** on WATCH + TRY,
independent and latched. RSA gets this for free — progress is `Stage`-driven and has
no per-algorithm code anywhere (ADR-028).

## Complexity, stated honestly

| | |
|---|---|
| Key generation | finding two large primes, then one modular inverse |
| Encryption | one modular exponentiation, `mᵉ mod n` |
| Decryption | the same, with `d` — which is large, so this is the expensive half |
| Security rests on | factoring `n` back into `p` and `q` being hard at real sizes |

**RSA is far more expensive than a symmetric cipher.** That is why real systems
commonly use asymmetric cryptography to agree on a key and something like AES for the
data itself — which is the sentence that ties this lesson to the one before it, and
the fifth recap bullet.

The security claim is deliberately narrow: *factoring a 2048-bit `n` is what nobody
knows how to do quickly*. Not "impossible", and not "unbreakable" — a test asserts
that word appears nowhere in the lesson.

## Edge cases — all tested

A composite `p` or `q` · `p = q` (refused: `(p−1)(q−1)` is not `φ(p²)`) · an `e`
sharing a factor with `φ(n)` · `e` outside `1 < e < φ(n)` · a message that does not fit
under `n` · primes too large to check by hand · an empty or duplicated question list —
all refused at construction. Plus: an ill-timed or out-of-range action (a no-op, never
an exception) · a wrong answer applied directly (the run stays legal and terminates) ·
adversarial driving over 40 random sequences · rewind exactness.

## What it cost the architecture

| | |
|---|---|
| New `VizEvent` types | **0** — `Examine`, `Meter`, `Terminal` |
| New interaction models | **0** — `OPTIONS` buttons and the `CELL` tap established by the graph, counting and AES lessons |
| New `Scene` shapes | **1** — `KeyPairScene`, the eleventh |
| New screens or controllers | **0** |
| Changes to existing lessons | **0** |
| Changes to the access rule | **0** — one id added to `PRO_LESSONS` |
| New design tokens | **2** — `Dimens.derivationSymbolWidth`, `Dimens.twoColumnMinWidth` |
| Shared model additions | `Dataset.rsa` (defaulted), `AlgorithmId.RSA` |
| Files added | 5 engine · 1 renderer · 3 test |
| Tests | 67 engine + 11 app (engine 920 → **987**, app 57 → **68**) |

The story-first re-ordering (ADR-052) cost the architecture nothing further: two
**defaulted** fields on `KeyPairScene` — `flow` and `maths` — four new questions, four
new beats, and no change to any event, interaction model, screen, controller or other
lesson. Defaulted, because that is what every scene addition in this project has been
since ADR-037.

The shape earns its place the way the ten before it did: a **derivation chain** — a
short list of named scalars where each is produced from earlier ones by a printed
formula, and the dependency is the lesson — is a new kind of data. `SequenceScene`'s
slots are positions and its one `equation` is a two-operand `+`/`−` line;
`BlockCipherScene` carries *one* key expanded into many of the same kind, where these
are two keys with opposite rules; `HashScene`'s pipeline stages are boxes the data
passes *through*, where every step here **is** a value that stays and gets read.

## Not yet verified

**On a device.** No device was attached, so these are checked by unit test and
arithmetic only:

1. **The derivation card at 320dp.** Five rows of `symbol · formula · value`, where
   the formula wraps and the symbol and value do not. `(p − 1)(q − 1)` is the longest
   formula and `φ(n)` the longest symbol.
2. **The two key cards side by side**, about 160dp each at 360dp, holding a label, a
   pair, and two clauses.
3. **The round-trip row** — three values and two labelled arrows across one line.
4. **The tallest configuration** — chain, both keys, round trip and four stacked
   choice cards, on the secrecy question. This is the tallest scene the app has, and
   it may want the chain to collapse once the keys exist.

## Deferred

- **A real crypto demo.** `javax.crypto` cannot encrypt with a toy key at all, so a
  live demonstration would be a *separate* 2048-bit key pair with OAEP — a different
  thing from the lesson rather than a continuation of it. The test suite does
  generate one to check the lesson's claim, which is where that belongs.
- **Signatures.** Using the private key to sign and the public key to verify is the
  other half of what RSA is for, and it is a lesson of its own.
- **Key exchange in practice.** Diffie–Hellman, and why TLS uses asymmetric
  cryptography only to start a session. Named in the recap, not built.
- **Why factoring is hard.** The lesson says the security rests on it and does not
  attempt to justify it — that is a number theory lesson, not this one.
- **A CHALLENGE.** `ChallengeCatalog.byId(RSA)` returns null, as for every Advanced
  lesson (`v2-challenge.md`). A generated variant would draw new primes and ask the
  same ten questions, which changes the arithmetic and none of the judgements.
