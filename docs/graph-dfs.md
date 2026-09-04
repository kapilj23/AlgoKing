# Graph DFS — the third Advanced lesson

**Status:** shipped · 2026-09-04 · MVP scope is **WATCH → TRY → COMPLETE**
**Category:** Advanced · **Engine:** `engine/algorithms/graphdfs/`

---

## What it is

Depth-First Search. Start at a node, go as deep as possible down one path, and
when you cannot go deeper, **backtrack** and take the next branch.

```
        A
       / \
      B   C
     / \
    D   E
```

## Graph representation

An authored `Graph` on the `Dataset`: nodes with ids, labels and normalised 0..1
layout, plus an **ordered** adjacency list.

```
A: [B, C]      C: [A]
B: [A, D, E]   D: [B]
E: [B]
```

**Neighbour order is data, never UI.** DFS's traversal is decided entirely by
which neighbour it tries first, so `adjacency` is an ordered `List`, authored once
and read by the engine, the walkthrough, Try and the tests alike. A renderer that
sorted nodes for its own convenience would silently produce a different traversal
than the lesson teaches.

## The algorithm

```
DFS(node):
    mark node visited
    for each neighbour of node:
        if neighbour is not visited:
            DFS(neighbour)
```

Starting at **A**, that generates:

```
A → B → D → E → C
```

**The traversal is generated, never authored.** `visited` and `stack` are real
state and the order falls out of them; nothing in the lesson writes the answer
down. Every test drives the real engine and reads `visited` out of it.

### Visited tracking and backtracking

`visited` is an ordered list — it *is* the traversal order, so a `Set` would have
thrown away the one output the lesson produces.

`stack` is the path DFS is standing on, deepest last: the call stack a recursive
implementation would build, made into data. `[A, B, D]` means DFS reached D
through B through A, and it is exactly what a backtrack unwinds.

The run needs **three** backtracks, and they are not all alike:

| | |
|---|---|
| D → B | a leaf dead end |
| E → B | another leaf dead end |
| B → A | a node whose branches are all used up |

A graph with only one kind would teach backtracking as a special case rather than
as the rule.

## WATCH

Eleven steps, user-paced. Every beat says **why**, not what.

| # | Beat | Says |
|---|---|---|
| 0 | `SETUP` | Depth-first search, starting at A. |
| 1 | visit | Visit A. *Mark it visited, and remember how we got here.* |
| 2 | deeper | A → B. Go deeper. |
| 3 | deeper | B → D. *A is already visited, so DFS skips it and takes D.* |
| 4 | **dead end** | D has no unvisited neighbours. *Backtrack to B.* |
| 5 | deeper | B → E. |
| 6 | **dead end** | E has no unvisited neighbours. *Backtrack to B.* |
| 7 | **dead end** | B has no unvisited neighbours. *Backtrack to A.* |
| 8 | found | A → C. Every node reached. |
| 9 | `INSIGHT` | **Deep first, wide last.** DFS finished the whole B branch before it ever looked at C. |
| 10 | `SUMMARY` | Traversal, and the four rules as bullets. |

The three backtracks get the same weight as the five visits, deliberately. A
learner who sees only the visits has watched a list being written down; the
unwinds are where recursion becomes visible.

## TRY

Same graph, same start. One gesture the whole way through:

> **Tap the node DFS moves to next.**

That is either an unvisited neighbour (go deeper) or the node you came from
(backtrack) — and choosing between those two *is* depth-first search. Modelling
backtracking as its own button would have made it a mode the learner toggles
rather than a move the graph makes.

It also makes all three judgements askable from one tap:

| Tap | Response |
|---|---|
| the first unvisited neighbour | correct — *"D is B's first unvisited neighbour, so DFS goes deeper."* |
| a **visited** neighbour | *"A is already visited. DFS skips visited neighbours — that is what stops it going round in circles."* |
| a **later** unvisited neighbour | *"E is a neighbour, but not the first unvisited one. DFS finishes the D branch completely before it starts another."* |
| the parent, too early | *"Not yet — B still has an unvisited neighbour. DFS only backtracks from a dead end."* |
| the parent, at a dead end | correct — the backtrack |

A wrong tap never changes the graph (ADR-021): nothing moves, and the ladder
escalates.

### Why WATCH and TRY use the same graph

Every other lesson gives Try fresh data. A graph is different: five nodes are
memorisable either way, and what makes Try hard here is not new data — it is that
the learner must now *produce* the three backtracks they previously watched.
Changing the graph as well would have added unfamiliarity without adding a single
new judgement.

## The picture

`Scene` gained a fourth shape, `GraphScene` — the first that is two-dimensional.
Node positions arrive normalised 0..1 and are scaled to whatever width the card
gives, so the scene never learns anything about dp.

- node state reuses `CellState`: current = `COMPARING`, visited = `FINALIZED`,
  unvisited = `IDLE`, so the legend and colours come from the design system
  unchanged;
- the **path** DFS came down is drawn as solid violet edges — the call stack made
  visible, and the route a backtrack visibly unwinds rather than teleporting;
- a backtrack edge is **dashed amber**, because a retreat is not progress;
- the traversal strip and the path strip are read straight from `visited` and
  `stack`, so the display and the algorithm cannot disagree.

## Complexity

**Time O(V + E)** — every vertex once, every edge once.
**Space O(V)** — the visited set and the deepest path.

## Edge cases

All handled in the engine, all tested: empty graph, single node, a node with no
neighbours, a disconnected graph (only the start's component is traversed), cycles
and self-loops (terminate, each node once), an unknown start node (falls back
rather than crashing), and an edge naming a node that does not exist (ignored).

## Progress

The standard MVP model: 0 % → **50 %** on WATCH → **100 %** on TRY.

## Deferred

**CHALLENGE.** `ChallengeCatalog.byId(GRAPH_DFS)` returns `null`, the same as the
other Advanced lessons — see [`v2-challenge.md`](v2-challenge.md).
