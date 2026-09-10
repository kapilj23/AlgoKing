# Dijkstra's Algorithm — design plan

> **⚠ SUPERSEDED BY THE BUILD — 2026-09-09.** The lesson is shipped; the live
> description is [`dijkstra.md`](dijkstra.md) and the reasoning is ADR-039. Two
> things in this plan were **wrong and were corrected by the layout spike** before
> any lesson code was written, and both are worth keeping visible:
>
> 1. **The node positions in §2 fail at 360dp.** A and B sit 69dp apart, leaving
>    21dp of bare edge for a ~20dp weight label. They were replaced with a *ladder*
>    layout — two rows, one diagonal rung — where the shortest edge is 83.5dp. Only
>    the coordinates changed; the dataset, weights, order and answer did not.
> 2. **§6 says distances live on `GraphNodeView.caption`. They cannot.** That is
>    AVL's top-right placement, which works on a tree because the space above a node
>    is empty by construction; on a graph it is where edges leave, and two of the six
>    landed on an edge. They render inside the circle via a new defaulted
>    `secondaryLabel`.
>
> Everything else — the dataset, the two decisions, the state machine, the
> frontier-not-a-queue-strip call, the build order and the gates — shipped as
> written.

**Status:** proposed · 2026-09-09 · **design only, nothing built, no source file modified**
**Category:** Advanced · follows Graph DFS → Graph BFS
**Awaiting:** approval. §15 lists the decisions and risks worth your ruling.

---

## 1. What it teaches, and how it differs from its neighbours

DFS and BFS are **traversals** — they visit everything and the only question is the
order. Dijkstra answers a different question: **what is the cheapest way there?**

| | The rule | The picture |
|---|---|---|
| DFS | go deep, back up at a dead end | a stack |
| BFS | finish this level before the next | a queue |
| **Dijkstra** | **always continue from the cheapest node known so far** | **a set of tentative distances that keep improving** |

The one-line connection, stated once in the insight and nowhere else:

> DFS goes deep. BFS goes level by level. **Dijkstra goes by distance** — and on a
> graph where every edge costs 1, that *is* BFS.

That last clause is worth the sentence: it explains why BFS is the special case,
and why weights are the whole reason a new algorithm exists.

**The hero concept is relaxation**, not node selection. A learner who leaves able to
say "pick the smallest" but not "a distance is a claim that can be beaten" has
learned the loop and missed the algorithm.

---

## 2. The dataset — WATCH

Six nodes, nine edges, all weights positive.

```
            A
           / \
        2 /   \ 5
         /     \
        C ──1── B
         \     / \
        9 \   /3  \ 4
           \ /     \
            D ──5── E
             \     /
            6 \   / 2
               \ /
                F
```

| Edge | Weight | | Edge | Weight |
|---|---|---|---|---|
| A–C | 2 | | B–D | 3 |
| A–B | 5 | | B–E | 4 |
| C–B | 1 | | D–E | 5 |
| C–D | 9 | | D–F | 6 |
| | | | E–F | 2 |

**Start A · target F.** Adjacency is authored in a fixed order (`A: [C, B]`,
`B: [A, C, D, E]`, `C: [A, B, D]`, `D: [C, B, E, F]`, `E: [B, D, F]`, `F: [D, E]`)
so the run is deterministic, exactly as DFS's neighbour order is.

### Expected result

| Node | Final distance | Reached via |
|---|---|---|
| A | 0 | — |
| C | 2 | A |
| B | **3** | C |
| D | 6 | B |
| E | 7 | B |
| F | **9** | E |

**Processing order:** A, C, B, D, E, F
**Shortest path:** **A → C → B → E → F**, total **9**

### Why this graph

1. **The direct edge is a trap.** A–B costs 5, but A→C→B costs 3. The first thing
   the learner watches is a distance being *beaten*, which is relaxation.
2. **Two more improvements follow** — D goes 11 → 6, F goes 12 → 9 — so relaxation
   is not a one-off trick.
3. **It contains a KEEP.** When D is processed, D→E offers 6 + 5 = 11 against E's
   existing 7. Nothing changes. Without this the learner concludes that examining an
   edge always updates it.
4. **D is processed but is not on the shortest path.** D even has a direct edge to
   the target (D–F = 6, the visually obvious route) and the answer goes around it.
   This is the antidote to "processed means it's part of the answer".
5. **The answer is not guessable.** A→D→F looks short on the page and costs 11+;
   the real path takes four edges.
6. **No ties.** Every selection has a unique minimum, so every decision has exactly
   one right answer. (Ties are still *tested*, just not taught — see §11.)

## 3. The dataset — TRY

Five nodes, eight edges. **A different graph — see §15.2 for the reasoning.**

| Edge | Weight | | Edge | Weight |
|---|---|---|---|---|
| A–C | 1 | | C–D | 7 |
| A–B | 4 | | B–D | 3 |
| A–D | 5 | | B–E | 9 |
| C–B | 2 | | D–E | 2 |

**Start A · target E.**

| Node | Final distance | Via |
|---|---|---|
| A | 0 | — |
| C | 1 | A |
| B | 3 | C |
| D | 5 | A |
| E | 7 | D |

**Processing order:** A, C, B, D, E · **Shortest path: A → D → E, total 7**

It is deliberately a different *shape* of answer: the winning route is the plain
direct one, and all the work done around C and B turns out not to be on it. Two
relaxations update and **two keep** — a stronger mix than WATCH, because by TRY the
learner should be able to refuse an update as readily as make one.

---

## 4. The interaction

Two decisions, alternating. Both already exist in the app's vocabulary.

### Decision 1 — **which node is processed next?** (`CELL`, tap the graph)

The DFS/BFS gesture, unchanged: tap a node. The correct answer is the unprocessed
node with the smallest tentative distance, and every node carries its distance on
screen, so the question is answerable by reading the picture.

### Decision 2 — **what should this neighbour's distance become?** (`OPTIONS`, three values)

This is the relaxation, and it is asked as **a choice between numbers**, which is
Prefix Sum's proven pattern: *the options are values, and the wrong ones are
misconceptions.*

> **C is 2, and C→B costs 1. B is currently 5. What should B's distance be?**
> **`3`** · `1` · `5`

| Option | Is | Encodes |
|---|---|---|
| **3** | 2 + 1 | correct — the candidate beats 5 |
| 1 | the edge weight alone | *"forgetting to add the current distance"* |
| 5 | unchanged | *"I don't need to update"* |

And in the KEEP case the same question has the opposite answer:

> **D is 6, and D→E costs 5. E is currently 7. What should E's distance be?**
> `11` · `5` · **`7`**

| Option | Is | Encodes |
|---|---|---|
| **7** | unchanged | correct — 11 is not an improvement |
| 11 | 6 + 5 | *"examining an edge always updates it"* |
| 5 | the edge weight | the same arithmetic slip |

**One question shape covers both branches and three of the listed misconceptions**,
without a second control and without ever printing the comparison before the
learner has made it.

### What the app does, not the learner

| The app | Why |
|---|---|
| initialises A = 0 and everything else to ∞ | definition, not judgement |
| walks to the next neighbour in the authored order | *which* edge to look at is bookkeeping; DFS made the same call |
| performs the addition and says it out loud | arithmetic with one legal answer (`PRODUCT_SPEC.md` §3) — but the learner still has to *use* it, because the distractors are built from it |
| skips a neighbour that is already processed | narrated once — *"B is processed, so its distance is already final"* — which is where "visited means final" is taught |
| relaxes a node that has no distance yet | ∞ loses to everything; there is nothing to compare, so nothing to ask. Narrated, not asked. |
| marks a node processed | a consequence, not a choice |

**Decision budget: 9 in WATCH's graph (5 selections + 4 relaxations), 8 in TRY's
(4 + 4).** In line with the traversals (9) and DFS (8). The raw machine would ask
about 20; the four rules above are what cut it.

---

## 5. WATCH — 13 beats

> **⚠ Amended by what shipped — 2026-09-10.** The walkthrough is **19 beats**, not 13.
> The table below folds each node's *first reach* into the beat that caused it — beat 1
> covers both `A→C` and `A→B`, and beats 3, 5 and 7 each carry an "also …" reach — while
> the engine gives every first reach its own `EXAMINE` beat (five of them: C 2, B 5, D 11,
> E 7, F 12) and narrates F's selection separately from the `FOUND` beat. Every one of
> those beats moves a number on screen, so none of them is the "nothing changed" step
> ADR-020 forbids, and the pedagogy this table specifies is unchanged: four relaxations
> with the KEEP weighted like the three updates, and B chosen *because* it was beaten
> down. The shipped sequence is pinned beat by beat in
> `DijkstraTest.the walkthrough is exactly the beats the lesson was designed as`; the
> table below is kept as the design intent it was.

| # | Beat | Says |
|---|---|---|
| 0 | `SETUP` | Find the cheapest route from A to F. *Every node starts at ∞ except A, which is 0 — we know nothing yet except where we are.* |
| 1 | `EXAMINE` | Process A. *A→C: 0 + 2 = 2. A→B: 0 + 5 = 5. Both had no distance, so both take one.* |
| 2 | `COMPARE` | C has the smallest tentative distance, 2. **Process C.** |
| 3 | **`ELIMINATE`** | **C→B: 2 + 1 = 3, and B was 5. B: 5 → 3.** *The direct edge from A cost 5. Going the long way round through C costs 3 — the first distance was only ever a claim.* · also C→D: 2 + 9 = 11 |
| 4 | `COMPARE` | B is now the smallest, at 3 — **process B**. *B got there by being beaten down, not by being reached first.* |
| 5 | `ELIMINATE` | **B→D: 3 + 3 = 6, and D was 11. D: 11 → 6.** · B→E: 3 + 4 = 7 |
| 6 | `COMPARE` | Smallest is D at 6. **Process D.** |
| 7 | **`KEEP`** | **D→E: 6 + 5 = 11, and E is already 7. 11 is not an improvement — E stays 7.** *Examining an edge does not mean changing anything.* · D→F: 6 + 6 = 12 |
| 8 | `COMPARE` | Smallest is E at 7. **Process E.** |
| 9 | `ELIMINATE` | **E→F: 7 + 2 = 9, and F was 12. F: 12 → 9.** |
| 10 | `FOUND` | F is now the smallest at 9, and F is the target. **Done.** |
| 11 | `INSIGHT` | **A distance is a claim, until something beats it.** |
| 12 | `SUMMARY` | Shortest path A → C → B → E → F, total 9. Four rules as bullets. |

Beats 3, 5, 7 and 9 are the relaxations, and 7 — the one that changes nothing — gets
the same weight as the ones that do. Beat 4 is where the selection rule earns its
keep: B is chosen *because* it was improved.

The closing insight carries the complexity and the constraint (§10), and the
SUMMARY bullets carry the DFS/BFS connection.

---

## 6. The picture

Reuses `GraphScene` and `GraphStage`. Node states map onto the four existing
`CellState`s with nothing left over:

| Means | State | Reads |
|---|---|---|
| being processed right now | `COMPARING` violet | Current |
| **reached, has a tentative distance, not processed** | `CANDIDATE` amber | Frontier |
| processed — its distance is final | `FINALIZED` green | Settled |
| not reached, distance still ∞ | `IDLE` grey | Unreached |

**Distances live on the nodes**, as `GraphNodeView.caption` — the field AVL added
for balance factors, used again with no change. `∞` for unreached, the number
otherwise, and `captionAlert` marks the node whose distance just changed. A
distance table would put the same numbers somewhere the learner has to look up;
on the node they are read in place.

**Edges carry their weights**, which is the one thing the renderer cannot do today
— see §8.

| Edge | State |
|---|---|
| the edge being relaxed right now | `ACTIVE` |
| an edge in the current predecessor tree (how each node's best distance was reached) | `PATH` |
| everything else | `IDLE` |

The predecessor tree drawn as violet edges is how **§9's requirement is met**: the
path is never hardcoded, it is the tree, and watching the tree re-route when B is
improved is the same event as the number changing. At the end, only the edges on the
path to the target stay `PATH`.

**Strips.** `traversal` = the processed order, labelled **Processed**. The second
strip shows **the best known route to the target** once the target has a finite
distance, so §9's "the path emerges" is literally on screen and visibly improves
from `A → C → B → D → F (12)` to `A → C → B → E → F (9)`.

---

## 7. Priority queue: **visible as the amber frontier, not as a queue strip**

Your §7 asks for a decision between showing it and keeping it a supporting detail.
**Recommendation: B, with the frontier made visible on the graph itself rather than
in a separate control.**

- The frontier *is* the amber nodes, and each carries its distance. "Always process
  the smallest tentative distance" is then a thing the learner reads off the picture
  and acts on — which is the skill.
- **A sorted queue strip would answer the question the lesson asks.** If the strip
  says `C 2 · B 5`, the next selection is its first item, and the pick decision stops
  being a decision. That is ADR-030's rule — *an answer already on screen is not a
  question* — and it is why BFS can show its queue (the front is not a judgement
  there) and Dijkstra cannot.
- A heap with sift-up/sift-down is an implementation of the idea, not the idea.

The **complexity claim still names it** (`O((V + E) log V)` *with a binary heap*),
and the summary says why: the cost is dominated by repeatedly finding the smallest.

---

## 8. Graph model and renderer — what has to change

**Yes, weighted edges require a shared change, and it is additive.**

### 8.1 `Graph` gains weights — one defaulted field

`Graph(nodes, adjacency)` has **no** weight support today. Adjacency is
`Map<String, List<String>>`, and changing it to carry edge objects would touch DFS,
BFS, their datasets and six test helpers.

Instead:

```kotlin
data class Graph(
    val nodes: List<GraphNode>,
    val adjacency: Map<String, List<String>>,
    /** Edge cost, keyed canonically so an undirected edge has one entry. Empty for
     *  an unweighted graph, which is what DFS and BFS traverse. */
    val weights: Map<String, Int> = emptyMap(),
) {
    fun weightOf(a: String, b: String): Int?   // null when unweighted
}
```

Defaulted, so **every existing construction site compiles and behaves identically**
— the two `Graph(emptyList(), emptyMap())` fallbacks in DFS and BFS, the DFS
teaching graph, and the six test helpers. This is the same additive move
`Dataset.graph`, `Dataset.tree`, `GraphScene.queue` and `GraphNodeView.caption` each
made. **DFS and BFS are not touched at all.**

A test asserts every edge of a Dijkstra dataset has a weight, and that every weight
is positive.

### 8.2 `GraphEdgeView` gains a label, and `GraphStage` draws it

This is the **first change to the shared renderer since AVL**, and it is
unavoidable: Dijkstra without visible edge weights cannot be taught.

- `GraphEdgeView.label: String? = null` — defaulted, so five lessons are unaffected.
- `GraphStage` places a small label at each edge's midpoint **as a `Text` composable
  on a `surface` pill**, not as canvas text: `ARCHITECTURE.md` §10.5 says *"No text
  drawn into `Canvas` — `Text` composables with tabular figures."* The stage already
  computes node positions in dp, so midpoints are the same arithmetic.

Nothing else changes. No new scene shape, no new screen, no new event type, no new
interaction model.

---

## 9. Engine architecture and state machine

### 9.1 The state — the single source of truth

```kotlin
data class DijkstraState(
    val graph: Graph,
    val start: String,
    val target: String,
    /** Tentative distance per node. Absent = ∞. The whole model, in one map. */
    val distances: Map<String, Int>,
    /** How each node's current best distance was reached. Absent = no route yet. */
    val predecessors: Map<String, String>,
    /** Settled — distance final, never revisited. Ordered: it is the processing order. */
    val processed: List<String>,
    /** The node being processed, or null between selections. */
    val current: String?,
    /** Which neighbour of [current] is next in the authored order. */
    val edgeCursor: Int,
    /** The candidate under consideration, set by the examine beat. */
    val pending: Relaxation?,
)

data class Relaxation(val from: String, val to: String, val weight: Int) {
    val candidate: Int      // distances[from] + weight
    // `existing`, `improves` and the option set are derived from the state
}
```

Everything else is **derived**: the frontier (`distances.keys - processed`), the
next node to select (`frontier.minBy { distances[it] }`), the path to the target
(walk `predecessors` back from `target`), whether a candidate improves, and the
three option values. Nothing is stored that could disagree with the map.

**No Compose code performs an addition or a comparison.** The UI renders
`distances` and sends a `Touch(node)` or a `SetDistance(value)`.

### 9.2 The machine

```
probe(state):
   target settled            -> Terminal(Found)
   frontier empty            -> Terminal(NotFound)      // unreachable
   pending relaxation        -> Decide(what should this distance become?)
   current has edges left    -> next neighbour:
                                  processed        -> Mechanical(SkipSettled)
                                  no distance yet  -> Mechanical(FirstReach)
                                  otherwise        -> Mechanical(Examine)  -> sets pending
   current exhausted         -> Mechanical(Settle)      // mark processed
   no current                -> Decide(which node is processed next?)
```

Seven action types, all pure:

| Action | Kind | What it does |
|---|---|---|
| `Select(node)` | **Decide** (CELL) | begins processing a node; refused unless it is the frontier minimum |
| `Examine` | Mechanical | reads `current + weight`, sets `pending` |
| `SetDistance(value)` | **Decide** (OPTIONS) | the relaxation; refused unless `value` is the correct outcome |
| `FirstReach` | Mechanical | a node with no distance takes the candidate |
| `SkipSettled` | Mechanical | steps past an already-processed neighbour |
| `Settle` | Mechanical | marks `current` processed, clears it |
| — | Terminal | the target is settled, or the frontier is empty |

**One machine serves both stages, with no mode flag** — the existing contract.
WATCH is `runToCompletion()`, which takes `decision.correct` at every `Decide`;
TRY's `LessonController` auto-applies every `Mechanical` and waits at every
`Decide`. This is exactly how the other eighteen lessons work, and it is why the
walkthrough and the learner's run cannot diverge.

A wrong action is **refused** — `Transition(state, …, correct = false)` returning
the state unchanged, the pattern Two Pointers, the BST, AVL and the traversals all
use. Combined with `DecisionValidation` returning a `Retry` that carries no action,
a mistake cannot reach `apply` in TRY at all.

**Termination:** every mechanical action advances `edgeCursor` or `processed`, and
every accepted decision does the same; both are bounded by V + E. The adversarial
test drives every action at every beat and asserts the run still ends correctly.

### 9.3 Files

```
engine/algorithms/dijkstra/
├── Dijkstra.kt            actions, DijkstraState, DijkstraAlgorithm
├── DijkstraProjector.kt   -> GraphScene
└── DijkstraWatchNarrator.kt
```

Plus `DijkstraDatasets` in `Datasets.kt`, an `AlgorithmId`, a `LessonPack`, a card,
a completion insight, narration ids and copy. The same six-touchpoint wiring every
lesson has.

---

## 10. Complexity and the constraint

Taught in the insight support and one summary bullet, and no more than this:

| | |
|---|---|
| Time | **O((V + E) log V)** with a binary heap — every edge is relaxed once, and each of the V selections costs a `log V` heap operation |
| Space | **O(V)** — a distance and a predecessor per node |
| **Negative weights** | **Dijkstra is wrong on them.** It settles a node the moment it is the smallest, on the assumption that nothing later can beat it — and a negative edge can. |

The negative-weight line is stated as *why the algorithm's central move stops being
safe*, not as a rule to memorise. Alternative implementations (Bellman-Ford, A\*)
are named nowhere.

---

## 11. Misconceptions, and where each is handled

| Misconception | Where | How |
|---|---|---|
| picking a node that is not the minimum | **TRY** | wrong tap → *"B is 5, but C is 2. Dijkstra always continues from the cheapest node it knows about."* |
| confusing edge weight with total distance | **TRY** | it is an option on every relaxation — the `1` in `3 · 1 · 5` |
| forgetting to add the current distance | **TRY** | the same option |
| updating when the candidate is larger | **TRY** | the KEEP relaxation, where `11` is offered and wrong |
| the first route found is the shortest | **WATCH** beat 3 | B is reached at 5 and then beaten to 3 — the lesson's opening move |
| processed = on the answer | **WATCH** beat 6–7 + COMPLETE | D is processed, and the path goes around it |
| "visited" too early = final | **WATCH** | narrated on every skipped settled neighbour: *"B is processed — its distance is already final"* |
| confusing Dijkstra with BFS | **WATCH** insight | *"on a graph where every edge costs 1, this is BFS"* |
| negative weights are fine | **WATCH** summary | one bullet, §10 |

TRY gets the four that are *decisions*; WATCH gets the four that are *observations*.

---

## 12. Testing strategy

`DijkstraTest`, structured like `AvlTest` and the traversal suites.

**Correctness against an independent reference.** A plain recursive/iterative
Dijkstra written in the test file, run over a dozen graphs, must agree with the
lesson's engine on every distance and every predecessor. The lesson's answer is
generated, never authored.

| Group | Asserts |
|---|---|
| initial state | start = 0, every other node ∞, nothing processed |
| selection | the frontier minimum is the only accepted `Select`; a non-minimum is refused and changes nothing |
| relaxation — improve | candidate < existing updates the distance **and** the predecessor |
| relaxation — keep | candidate ≥ existing changes neither |
| first reach | ∞ always takes the candidate, and is never asked |
| settled neighbour | skipped, and never re-relaxed |
| predecessors | path reconstruction from `predecessors` equals the reference's path |
| the exact datasets | WATCH: order `A C B D E F`, distances `0 2 3 6 7 9`, path `A→C→B→E→F`. TRY: order `A C B D E`, path `A→D→E`, total 7 |
| unreachable node | a disconnected component terminates with the target still ∞, and says so |
| equal-distance paths | two routes of equal cost give a deterministic result; the datasets contain no tie, and this test proves the engine handles one |
| ties in selection | broken by authored node order, deterministically, and asserted |
| single node | start == target, distance 0, no relaxation |
| positive weights | every dataset edge has a weight, and every weight > 0 |
| **negative weight** | rejected at dataset construction (see §15.4) |
| **wrong actions** | every wrong `Select` and every wrong `SetDistance`, at every beat: state byte-for-byte identical, and the run still completes correctly afterwards |
| adversarial | every action applied at every beat; terminates with the right answer |
| WATCH | the pinned 13-beat sequence, no two adjacent steps identical (ADR-020), every relaxation beat carries its readout |

Plus `GraphTest` additions for `weightOf` — including that an unweighted graph
returns null and that **DFS and BFS produce identical traversals on a graph that has
weights**, which is the regression guard for §8.1.

---

## 13. What is reused, and what is new

**Reused with no change at all:** `LessonPack` · `LessonController` · `WatchScreen`
· `LessonScreen` · `LessonCompleteScreen` · `WatchScriptBuilder` ·
`DecisionValidation` and the guidance ladder · `SceneRenderer` dispatch ·
`SceneLegend` · progress · navigation · every design token · the `GraphScene` shape
· the four `CellState`s · `GraphNodeView.caption` · the tap gesture · the
`DecisionButton` row · all thirteen `VizEvent`s.

**Changed, additively, shared:** `Graph.weights` + `weightOf` (§8.1) ·
`GraphEdgeView.label` (§8.2) · `GraphStage` draws edge labels (§8.2).

**New, Dijkstra only:** the engine package, the datasets, ~35 narration ids and
their copy, the `AlgorithmId`, the pack, the library card, the completion insight,
the test suite, the docs.

**Refactoring recommended: none.** No rename, no restructure, nothing touched in
DFS, BFS, Queue, BST, AVL or the traversals.

---

## 14. Build order and acceptance gates

| | | |
|---|---|---|
| 1 | `Graph.weights` + `weightOf`, with `GraphTest` additions **and DFS/BFS regression** | ~0.5 h |
| 2 | ⟵ **gate A** — full suite green, DFS/BFS byte-identical | |
| 3 | Dijkstra engine + `DijkstraTest`, headless. No UI. | ~1 d |
| 4 | ⟵ **gate B** — the engine matches an independent reference on a dozen graphs | |
| 5 | Projector + `GraphEdgeView.label` + `GraphStage` edge labels | ~0.5 d |
| 6 | ⟵ **gate C** — DFS, BFS, BST, AVL and the three traversals render unchanged **on device** | |
| 7 | WATCH: narrator, copy, pinned beats | ~0.5 d |
| 8 | TRY: wiring, feedback copy, wrong-answer suite | ~0.5 d |
| 9 | Docs: `docs/dijkstra.md`, **ADR-039**, and the four canonical documents | ~0.5 h |
| 10 | Device walkthrough of both stages, and a regression pass over the five graph lessons | |

Gate C is the one that matters most and is new to this lesson: it is the first time
in nineteen lessons that a shared renderer change could damage lessons that are
already shipped, so it is verified on the device rather than only by test.

---

## 15. Decisions, risks and what I would like ruled

**1. Priority queue — visible?** *Recommendation: as the amber frontier on the
graph, not as a sorted strip.* A sorted strip answers the selection question (§7).

**2. WATCH and TRY graphs — same or different?** *Recommendation: different.* Your
§13 prefers the same, and DFS, BFS and the BST all reuse theirs — but they were
reusing five nodes whose traversal is incidental. Dijkstra's answer is a short
memorable sentence (*"A C B E F, nine"*), the run is only nine decisions, and a
learner who recalls it can produce every one of them without reasoning. The TRY
graph also teaches something WATCH cannot: **two KEEPs**, and a shortest path that
is the boring direct route. It is one dataset object; say the word and it collapses
to the WATCH graph.

**3. Layout and readability — the real risk.** Six nodes, nine edges, a distance
caption on every node and a weight label on every edge, inside a 260dp stage on a
360dp phone. Node positions and label placement need on-device validation, and
mitigations if it is tight are, in order of preference: shorten the edge label pill;
nudge the authored positions; make the stage height a per-scene hint. **I would not
shrink the 48dp node.** This is the one thing I cannot fully settle on paper.

**4. Negative weights — validate where?** *Recommendation: a `require` at dataset
construction plus a test*, so a negative weight is impossible to author rather than
handled at runtime. The lesson says why they break the algorithm; the engine never
meets one.

**5. Ties.** The datasets contain none, by construction. The engine still needs a
documented deterministic rule — **authored node order** — and a test. Worth knowing
that a tie is not *wrong*, it just makes two answers correct, which a TRY decision
cannot express.

**6. Decision count.** Nine in TRY. If that runs long on device, the lever is to
make the last selection (the target, when it is the only node left in the frontier)
mechanical, taking it to eight.

---

## 16. Acceptance criteria

**Learning**
- [ ] The learner is asked *which node is processed next* at every selection, and can answer it by reading distances off the graph.
- [ ] The learner is asked *what a distance should become* at every genuine relaxation, with the edge-weight-alone and the no-change values offered as wrong answers.
- [ ] WATCH contains at least one distance that improves after being set, and at least one candidate that is refused.
- [ ] The shortest path is reconstructed from `predecessors` and appears nowhere as authored data.
- [ ] COMPLETE states the path, the total, and the one idea.

**Correctness**
- [ ] The engine agrees with an independent reference implementation on every distance and predecessor, over at least a dozen graphs including disconnected and single-node ones.
- [ ] `A C B D E F` / `0 2 3 6 7 9` / `A→C→B→E→F` = 9 on the WATCH dataset; `A→D→E` = 7 on TRY's.
- [ ] Every wrong action leaves the state byte-for-byte identical, and the run completes correctly afterwards.
- [ ] The run terminates under adversarial driving.

**Architecture**
- [ ] Zero Dijkstra logic in `:app` — no addition or comparison in a Composable.
- [ ] Zero new `Scene` shapes, screens, interaction models or `VizEvent` types.
- [ ] `Graph.weights` and `GraphEdgeView.label` are both defaulted; **DFS, BFS, BST, AVL and the three traversals compile and behave unchanged**, verified by the full suite and on device.
- [ ] Progress is independent: 0 / 50 / 100 for Dijkstra alone.

**Presentation**
- [ ] Every node shows its distance, `∞` before it is reached.
- [ ] Every edge shows its weight, legibly, on a 360dp phone in portrait.
- [ ] No node label, distance caption or edge label overlaps another.
- [ ] Node states use the four existing `CellState`s and no new colour.
