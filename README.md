| Sorting | Bubble · Selection · Insertion | swap or keep · is this the new minimum · shift or insert |
| Sorting | **Counting Sort** | which bucket counts this value, then which value the counts say comes next |
| Sorting | **Counting Sort** | which bucket counts this value, then which value the counts say comes next |
# AlgoKing

> Every other app shows you the algorithm. This one makes you run it.

An Android app that teaches algorithms by handing the learner the controls. The animation is not
decoration — it *is* the lesson — and every decision the algorithm makes, the learner makes.

Free forever, no accounts, fully offline.

```
Home  →  Watch  →  Try  →  Complete  →  Home
```

**WATCH** — a user-paced walkthrough. No play button, no autoplay, no speed control: the learner
taps NEXT and every tap produces a real, deterministic algorithm state. Pressing NEXT to see what
happens next is already a small act of prediction.

**TRY** — the learner drives the same algorithm. A wrong answer is a **learning event, not a
state transition**: the algorithm does not move, the same decision stays on screen, and an
escalating ladder teaches until they get it. There is no penalty and no failure state.

**COMPLETE** — what the run was: decisions, comparisons, wrong turns, and the one idea the lesson
existed to leave behind. No stars — Try is never scored.

---

## The twenty-eight lessons

| | Lessons | The learner decides |
|---|---|---|
| Searching | Binary Search | which cell to check, then which half survives |
| Sorting | Bubble · Selection · Insertion | swap or keep · is this the new minimum · shift or insert |
| Sorting | **Counting Sort** | which bucket counts this value, then which value the counts say comes out next |
| Divide & conquer | Merge · Quick | where it splits, which front value · which side of the pivot — then watch each half get solved on its own |
| Structures | Stack · Queue · Linked List · Hash Map | which end · which link changes · which bucket |
| Cryptography | **Caesar Cipher** | what each letter becomes — and the alphabet wraps round at Z |
| Cryptography | **XOR Cipher** | what each pair of bits makes — and the same key undoes it |
| Cryptography | **SHA-256 Hashing** | which statement about a hash is true — with the real digests on screen to read it off |
| Cryptography | **AES** *(Pro)* | what a round is made of — and which step the last round leaves out |
| Cryptography | **RSA** *(Pro)* | what each key is for and what it does to a message — and only then, where the two keys came from |
| **Advanced** | **Two Pointers** | which pointer can still improve the sum |
| **Advanced** | **Prefix Sum** | what each running total is, then which two answer the range |
| **Advanced** | **Graph DFS** | which node DFS moves to next — deeper, or back |
| **Advanced** | **Graph BFS** | which node BFS touches next — enqueue a neighbour, or dequeue the front |
| **Advanced** | **Dijkstra** | which node is cheapest, then what a distance becomes when a shorter route turns up |
| **Advanced** | **Binary Search Tree** | which way the comparison sends the search — left, right, or found |
| **Advanced** | **AVL Tree** | which node is out of balance, then which node takes its place |
| **Advanced** | **Binary Tree — Inorder** | which node the traversal touches next — LEFT → NODE → RIGHT |
| **Advanced** | **Binary Tree — Preorder** | the same tap, the same tree — NODE → LEFT → RIGHT |
| **Advanced** | **Binary Tree — Postorder** | and again — LEFT → RIGHT → NODE |
| **Advanced** | **Fibonacci** | what each table entry is — and the two cells before it are the whole answer |
| **Advanced** | **0/1 Knapsack** | what to pack when it will not all fit — and only then, what a table of best answers is for |

Stack and Queue are the *same engine class* with one property flipped, and still read as two
different structures — the picture carries the difference. The three traversals go further:
one machine, one tree, one gesture, and three different orders out — which is the entire
reason they ship as three lessons instead of one.

## The one architectural idea

> An algorithm is a **pure, resumable state machine** that can be stepped from any state —
> including states the correct execution would never reach.

Three methods, no time, no Android, no I/O:

```kotlin
interface Algorithm<S : Any, A : Action> {
    fun initial(dataset: Dataset): S
    fun probe(state: S): Probe<A>          // Mechanical | Decide | Terminal
    fun apply(state: S, action: A): Transition<S>
}
```

`probe` never mutates and never chooses; `apply` never decides. That separation is what lets one
algorithm serve Watch (the script answers) and Try (the learner answers) with **no mode flag
anywhere inside it**. Rewind is popping an immutable stack. Watch is `runToCompletion()`.

Everything else falls out of it:

- **A wrong answer cannot corrupt an algorithm.** `DecisionValidation.validate` returns
  `Accept(action)` or `Retry(...)` — and a `Retry` carries no action, so there is nothing the
  caller *could* apply. The rule is structural, not a convention each new algorithm must
  remember.
- **The renderer cannot name an algorithm.** It receives a `Scene` and branches only on the
  *shape* of the data (`ROW` / `PILE` / `CHAIN` / `GRID`, a bucket table, two aligned arrays, a count table, a graph, a DP table, a hash pipeline, a cipher State, or a key-derivation chain). Twenty-eight lessons, one
  renderer entry point, zero `when (algorithm)` in `:app`.
- **Adding a lesson adds a `LessonPack`** — an algorithm, a projector, a narrator and two
  authored datasets. Never a screen, never a renderer.
- **13 events cover all twenty-eight lessons.** None was added after the first — Two Pointers,
  written long after the event model was fixed, needed none (ADR-032), and the Binary Search
  Tree added no renderer either: a tree is a graph, so it draws itself with the one the
  graph lessons already use (ADR-036).

## Modules

```
engine/   pure Kotlin JVM — an `import androidx.compose.*` in here does not compile,
          because the module type forbids it. 1011 tests, milliseconds, no Robolectric.
app/      Compose UI, navigation, persistence.
```

The boundary is enforced by the toolchain rather than by code review. `:app` reads scenes from
`:engine` and cannot reach into `engine.algorithms.*`.

## Build and run

Requires JDK 17+ (Android Studio's bundled JBR works) and an Android SDK.

```bash
./gradlew :engine:test        # 1011 unit tests, no device needed
./gradlew build               # both modules + tests
./gradlew :app:installDebug   # onto a connected device or emulator
```

On Windows, point `JAVA_HOME` at the bundled runtime first:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
```

## Documentation

Four canonical documents, each owning one kind of decision. They are the source of truth; the
code follows them, and where the code has diverged the documents say so.

| Document | Owns |
|---|---|
| [`PRODUCT_SPEC.md`](PRODUCT_SPEC.md) | product behaviour — the stages, the decisions, the rules |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | technical decisions — the engine, the renderer, the data layer |
| [`DESIGN_SYSTEM.md`](DESIGN_SYSTEM.md) | every visual decision, sampled from the approved reference |
| [`DECISIONS.md`](DECISIONS.md) | the reasoning trail — ADR-001 … ADR-054 |

Supporting notes live in [`docs/`](docs/), including
[`v2-challenge.md`](docs/v2-challenge.md) — why the CHALLENGE stage is deferred and what V2
inherits — and the Advanced lessons
[`two-pointers.md`](docs/two-pointers.md) [`prefix-sum.md`](docs/prefix-sum.md) [`graph-dfs.md`](docs/graph-dfs.md) [`graph-bfs.md`](docs/graph-bfs.md) [`dijkstra.md`](docs/dijkstra.md) [`binary-search-tree.md`](docs/binary-search-tree.md) [`avl-tree.md`](docs/avl-tree.md) and [`tree-traversals.md`](docs/tree-traversals.md) — the Sorting lesson [`counting-sort.md`](docs/counting-sort.md), and the two
dynamic-programming lessons [`fibonacci-dp.md`](docs/fibonacci-dp.md) and
[`zero-one-knapsack.md`](docs/zero-one-knapsack.md), and the Cryptography lessons
[`caesar-cipher.md`](docs/caesar-cipher.md), [`xor-cipher.md`](docs/xor-cipher.md) and
[`sha256-hashing.md`](docs/sha256-hashing.md), [`aes.md`](docs/aes.md) and [`rsa.md`](docs/rsa.md).

## Access

Fourteen lessons are free and complete — both stages, the full guidance ladder, progress.
Fourteen are **AlgoKing Pro**, a **one-time purchase** on Google Play — bought once, owned
permanently, nothing to renew: tapping one opens a paywall rather than the lesson. Pro adds
lessons and never takes anything away from a free one.

Twelve of those fourteen are the **Advanced** shelf, and access derives from that category
rather than from a flag — so a lesson filed there is protected the day it is added. **AES is
the exception and the first one**: it is a block cipher, so it belongs on the Cryptography
shelf beside the two ciphers and the hash it builds on, and it is named in `ProAccess` instead.
One object still answers the whole question (ADR-049).

**Ads are one interstitial**, shown to a free learner after they finish a lesson's TRY run,
and nowhere else — no banner, no rewarded ad, nothing during a lesson, nothing on Home.
Pro subscribers see none at all. Google's UMP gathers consent before any ad is requested,
and production ad units are configured — debug builds stay on Google's test units, so a
developer's own device can never touch the real one ([`docs/ads.md`](docs/ads.md)).

Play Billing **is** connected (`billing:8.0.0`) against the one-time product `algoking_pro`
and its `buy` purchase option, which are configured in Play Console. The price on the paywall
is Google Play's own localised string and is never written down anywhere in this repository;
until the store answers, the screen says so rather than guessing. See
[`docs/pro-access.md`](docs/pro-access.md), ADR-041 and ADR-051.

## Status

**Built:** the engine and all twenty-eight lessons · Watch · Try · Complete · Home · ten renderers ·
the full light design-system token layer · progress persistence · 1011 passing engine tests.

**Deferred to V2:** the CHALLENGE stage, and with it stars, mastery and the Daily Challenge.
The machinery — seeded generator, trace-validated constraints, ten challenge types, three star
families, the mission catalogue — is built, tested and quarantined behind `ChallengePack`, so V2
is a matter of wiring a screen rather than rebuilding a system. See
[`docs/v2-challenge.md`](docs/v2-challenge.md).

**Not yet built:** Code Reveal · the three-tab IA (Home is the only destination today) · ads and
consent · analytics · notifications · onboarding.
