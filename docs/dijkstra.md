# Dijkstra's Algorithm — the third graph lesson

**Status:** shipped · 2026-09-09 · MVP scope is **WATCH → TRY → COMPLETE**
**Category:** Advanced · **Engine:** `engine/algorithms/dijkstra/`
**Design plan:** [`dijkstra-plan.md`](dijkstra-plan.md) · **layout spike:** §"The picture" below

---

## What it is, and how it differs from its neighbours

DFS and BFS are **traversals** — they visit everything, and the only question is
the order. Dijkstra answers a different question: **what is the cheapest way
there?**

| | The rule | Driven by |
|---|---|---|
| DFS | go deep, back up at a dead end | a stack |
| BFS | finish this level before the next | a queue |
| **Dijkstra** | **always continue from the cheapest node known so far** | **tentative distances that keep improving** |

The connection is one line in the recap and nowhere else:

> DFS goes deep, BFS goes level by level, Dijkstra goes by distance — and where
> every edge costs 1, that is BFS.

Which is also the point: the weights are the whole reason a different algorithm
exists.

## The rule

```
every node starts at ∞ except the start, which is 0
repeat:
    take the cheapest node not yet settled
    for each of its neighbours:
        candidate = distance(node) + weight(node, neighbour)
        if candidate < distance(neighbour):  distance(neighbour) = candidate
    settle the node
```

**Relaxation is the hero, not selection.** A learner who leaves able to say "take
the smallest" but not "a distance is a claim that can be beaten" has learned the
loop and missed the algorithm.

## The teaching graph

Six nodes, nine edges, positive weights. Start **A**, target **F**.

| Edge | | | Edge | | | Edge | |
|---|---|---|---|---|---|---|---|
| A–C | 2 | | B–D | 3 | | D–E | 5 |
| A–B | 5 | | B–E | 4 | | D–F | 6 |
| C–B | 1 | | C–D | 9 | | E–F | 2 |

```
settle A(0)  ->  C = 2, B = 5
settle C(2)  ->  B: 5 → 3    the direct edge cost 5; the long way costs 3
                 D = 11
settle B(3)  ->  D: 11 → 6,  E = 7
settle D(6)  ->  E: 6 + 5 = 11 against E's 7  ->  KEEP
                 F = 12
settle E(7)  ->  F: 12 → 9
settle F(9)  ->  target reached
```

**Order A C B D E F · distances 0 2 3 6 7 9 · path A → C → B → E → F = 9.**
None of that is written down: the lesson generates it, and the tests check it
against a textbook Dijkstra written independently in the test file.

### Why this graph

- **the direct edge is a trap.** A–B costs 5 and A→C→B costs 3, so the first thing
  the learner watches is a distance being *beaten* — which is what relaxation is;
- **two more improvements follow**, D 11 → 6 and F 12 → 9, so it is not a one-off;
- **it contains a KEEP.** D offers E 11 against 7 and nothing changes. Without it a
  learner concludes that examining an edge always updates it;
- **D is settled but is not on the answer** — and D holds the direct edge to the
  target that looks shortest on the page. Settled does not mean chosen;
- **no ties.** Every selection has a unique cheapest node, so every decision has
  exactly one right answer. A test asserts that across both datasets.

## TRY — a different graph

Five nodes, eight edges, start **A**, target **E**. Dijkstra's answer is a short
memorable sentence — *"A C B E F, nine"* — and the whole run is nine decisions, so
a learner who recalls it could produce every one without reasoning.

```
settle A(0) -> B = 4, C = 1, D = 5
settle C(1) -> B: 4 → 3,   D: 1 + 7 = 8 against 5  ->  KEEP
settle B(3) -> D: 3 + 3 = 6 against 5  ->  KEEP,     E = 12
settle D(5) -> E: 12 → 7
settle E(7) -> target reached
```

**Path A → D → E = 7.** It teaches two things WATCH cannot: **two KEEPs** rather
than one, and a winning route that is the plain direct one, with all the work
around C and B not on it at all.

## The interaction

Two decisions, alternating, both already in the app's vocabulary.

### Which node is processed next? — a tap on the graph

The DFS/BFS gesture. Every node carries its distance, so the question is
answerable by reading the picture.

| Tapped | Answer |
|---|---|
| a settled node | *"C is already settled. Its distance is final and Dijkstra never goes back to it."* |
| an unreached node | *"D has no distance yet — nothing has reached it."* |
| a reached but dearer node | *"B is 5 away, but C is only 2. Dijkstra always takes the cheapest node it knows about."* |

### What should this distance become? — three numbers

The relaxation, asked as a choice between values — Prefix Sum's pattern, where
**the wrong options are the misconceptions**:

> C is 2, and C→B costs 1, so that route costs 3. B is currently 5. What should it be?
> **`3`** · `1` · `5`

| Option | Is | Encodes |
|---|---|---|
| **3** | 2 + 1 | correct |
| 1 | the edge weight alone | *"forgetting to add where you already are"* |
| 5 | unchanged | *"I do not need to update"* |

And in the KEEP case the same question has the opposite answer — `11` · `5` ·
**`7`** — so **one question shape covers both branches and three misconceptions**,
without ever printing the comparison before the learner makes it.

### What the app does

| The app | Why |
|---|---|
| sets the start to 0 and everything else to ∞ | definition, not judgement |
| walks to the next neighbour in the authored order | which edge to look at is bookkeeping |
| performs the addition and says it out loud | arithmetic with one legal answer (`PRODUCT_SPEC.md` §3) — and the distractors are built from it, so the learner still has to use it |
| reaches a node that has no distance yet | ∞ loses to everything: nothing to compare, nothing to ask |
| steps past a settled neighbour | narrated — *"B is already settled at 3, nothing can improve it now"* — which is where "settled means final" is taught |
| settles a node | a consequence, not a choice |

**Nine decisions in WATCH's graph** (five selections, four relaxations) and eight
in TRY's. The raw machine would ask about twenty; those five rules are the cut.

## The picture

The same `GraphScene` and `GraphStage` five lessons already use. Node states map
onto the four existing `CellState`s with nothing left over:

| Means | State | Reads |
|---|---|---|
| being processed now | `COMPARING` | Current |
| **reached, not settled** | `CANDIDATE` | Frontier |
| settled — distance final | `FINALIZED` | Settled |
| unreached, still ∞ | `IDLE` | Unreached |

**The amber frontier is the priority queue**, and it is deliberately not *also*
drawn as a sorted strip: a list reading `C 2 · B 5` would answer the question the
lesson asks before the learner does (ADR-030). BFS may show its queue because
taking the front is not a judgement there; here it is the whole rule.

The **predecessor tree** is drawn as violet edges, so the path is never hardcoded —
it *is* the tree, and watching it re-route when B is beaten down is the same event
as the number changing. Once the run ends, only the route to the target stays lit.

Two strips: **Settled** (the processing order) and **Best route so far**, which
appears once the target has any distance and visibly improves from
`A → C → B → D → F` at 12 to `A → C → B → E → F` at 9. That is how the answer is
seen to *emerge*.

### The layout was validated before the lesson was built

A 1:1 spike at 360dp found two things the plan had wrong:

1. **The proposed node positions failed.** A and B sat 69dp apart; two 48dp circles
   leave 21dp of bare edge and a weight label needs about 20, so it landed on both.
   Replaced with a **ladder** — the graph is a strip of triangles, so it draws as two
   rows with one diagonal rung. Shortest edge 83.5dp, every weight label clears every
   circle by at least 7.8dp, zero edge crossings. **Only the coordinates moved; the
   dataset is unchanged.**
2. **Distances cannot sit beside the node.** AVL hangs its balance factor off the
   top-right corner, which works on a *tree* because the space above a node is empty
   by construction. On a graph that corner is where edges leave, and two of the six
   captions landed on an edge. They render **inside the circle** instead — the only
   placement that cannot collide, because the node already owns that space.

Node touch targets stayed at 48dp throughout. Nothing was shrunk to make it fit.

## Complexity

| | |
|---|---|
| Time | **O((V + E) log V)** with a binary heap — every edge relaxed once, each selection a `log V` heap operation |
| Space | **O(V)** — a distance and a predecessor per node |
| **Negative weights** | **Dijkstra is wrong on them** |

The constraint is taught as *why the central move stops being safe*, not as a rule
to memorise: taking the cheapest node is safe because every edge costs something,
so any route through a node still unsettled is already at least as long. That one
sentence is the whole proof, and it is exactly what a negative edge falsifies.

`Graph.weightsOf` **rejects a non-positive weight at construction**, so the lesson
can never hold one.

## Misconceptions, and where each is handled

| Misconception | Where |
|---|---|
| picking a node that is not the cheapest | **TRY** — every wrong tap is answered by name |
| confusing edge weight with total distance | **TRY** — it is an option on every relaxation |
| forgetting to add the current distance | **TRY** — the same option |
| updating when the candidate is larger | **TRY** — the KEEP relaxation |
| the first route found is the shortest | **WATCH** — B is reached at 5 and beaten to 3, in the opening move |
| settled = on the answer | **WATCH** + COMPLETE — D is settled and the path goes around it |
| "visited" = final, too early | **WATCH** — narrated at every settled neighbour |
| confusing Dijkstra with BFS | **WATCH** insight and the last recap bullet |
| negative weights are fine | **WATCH** insight, and rejected in the model |

TRY gets the four that are decisions; WATCH gets the five that are observations.

## Edge cases

All handled in the engine, all tested: empty graph · single node that is its own
target · a node with no edges · **an unreachable target** (the run ends,
`Outcome.NotFound`, and the path is empty) · equal-cost routes (deterministic, and
the datasets deliberately contain none) · every wrong action (refused, state
byte-for-byte identical) · adversarial driving (every node and fourteen values
applied at every beat, and the run still ends on the right answer).

## What it cost the architecture

| | |
|---|---|
| New `VizEvent` types | **0** |
| New `Scene` shapes | **0** — the `GraphScene` five lessons share |
| New screens or interaction models | **0** |
| Changes to existing lessons | **0** |
| Shared model additions | `Graph.weights` + `weightOf` + `weightsOf`, `Dataset.targetNode` — all defaulted |
| Shared scene additions | `GraphEdgeView.label`, `GraphNodeView.secondaryLabel` — both defaulted |
| Renderer | edge-weight pills at midpoints, and a two-line node. `Text`, never canvas text (`ARCHITECTURE.md` §10.5) |
| Files added | 3 engine + 2 test |

`GraphWeightTest` is the guard on the shared change: **DFS and BFS produce
byte-identical states on a graph that carries weights**, because a traversal asks
where it can get to and never what it costs.

## Progress

The standard MVP model: 0 % → **50 %** on WATCH → **100 %** on TRY.

## Deferred

**CHALLENGE.** `ChallengeCatalog.byId(DIJKSTRA)` returns `null`, as every Advanced
lesson does — see [`v2-challenge.md`](v2-challenge.md).

**A\* and Bellman–Ford** are named nowhere. A\* is Dijkstra with a heuristic added
to the same selection rule, and Bellman–Ford is the answer to the negative-weight
constraint this lesson states — both are lessons of their own, and neither belongs
in the one that has just introduced relaxation.
