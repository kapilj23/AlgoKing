# Graph BFS — the fourth Advanced lesson

**Status:** shipped · 2026-09-04 · MVP scope is **WATCH → TRY → COMPLETE**
**Category:** Advanced · **Engine:** `engine/algorithms/graphbfs/`

---

## What it is

Breadth-First Search. Start at a node, process everything one step away, then
everything two steps away — **level by level**. A **queue** is what makes that
happen.

## The graph — deliberately the same one DFS uses

```
        A
       / \
      B   C
     / \
    D   E

A: [B, C]      C: [A]
B: [A, D, E]   D: [B]
E: [B]
```

Same nodes, same edges, same adjacency order, same start node. That is the entire
point:

| | traversal |
|---|---|
| **DFS** | `A → B → D → E → C` |
| **BFS** | `A → B → C → D → E` |

Nothing about the data changed. The queue is the only difference, and a test
asserts both traversals from the one shared `GraphDatasets.teachingGraph`.

## The algorithm

```
BFS(start):
    mark start visited
    enqueue(start)
    while queue is not empty:
        current = dequeue()
        for each neighbour of current:
            if neighbour is not visited:
                mark neighbour visited
                enqueue(neighbour)
```

### Visited on the way *in*

A node is marked visited the moment it **joins** the queue, not when it leaves.
That is the rule that keeps BFS correct on a graph with cycles: mark on the way
out and the same node can be enqueued several times before its turn comes up, and
it gets processed more than once.

It also means there are **two orders**, and conflating them is the most common way
a BFS visual lies:

| | |
|---|---|
| `visited` | enqueue order — what BFS has *seen* |
| `dequeued` | **the traversal** — what BFS has *processed* |

The traversal strip renders `dequeued`. A test pins that: after enqueuing B and C,
`visited` is `[A, B, C]` while the traversal is still just `[A]`.

## WATCH

Thirteen steps, user-paced, every beat a queue operation:

| # | Beat | Queue after |
|---|---|---|
| 0 | Breadth-first search, starting at A | |
| 1 | Visit A and put it in the queue | `[A]` |
| 2 | Dequeue A from the front | `[]` |
| 3 | A → B. Enqueue it | `[B]` |
| 4 | A → C. Enqueue it | `[B, C]` |
| 5 | Dequeue B | `[C]` |
| 6 | B → D. *A is already visited, so BFS skips it and adds D* | `[C, D]` |
| 7 | B → E. Enqueue it | `[C, D, E]` |
| 8 | Dequeue C | `[D, E]` |
| 9 | Dequeue D | `[E]` |
| 10 | Dequeue E — the queue is empty, so BFS is complete | `[]` |
| 11 | **INSIGHT** — level by level, because the queue says so | |
| 12 | **SUMMARY** — traversal, and four rules | |

**B and C are enqueued before D and E**, and that is what the picture teaches:
everything one step from A is waiting in the queue before anything two steps away
joins it.

The insight carries the DFS comparison in one line — *"BFS gave A → B → C → D → E.
DFS on this same graph gives A → B → D → E → C. Same graph, same neighbour order —
the queue is the only difference."* It lands last, because the comparison means
nothing until the learner has watched the queue produce the first one.

## TRY

Same graph, same gesture as DFS:

> **Tap the node BFS touches next.**

Which is either an unvisited neighbour (enqueue it) or the front of the queue
(dequeue it). Keeping the interaction identical to DFS is deliberate: a learner
who has done both should feel the difference in the *algorithm*, not in the
controls.

All five judgements come from that one tap:

| Tap | Response |
|---|---|
| the first unseen neighbour | correct — *"B joins the back of the queue, and is marked visited straight away."* |
| a **visited** neighbour | *"A is already visited. BFS marks a node the moment it joins the queue, so it never gets added twice."* |
| a **later** unseen neighbour | *"C is a neighbour BFS has not seen, but B comes first in A's list."* |
| a queued node that is **not the front** | *"C is in the queue, but B is at the front. BFS processes them in the order they arrived."* |
| the front, while neighbours remain | *"B is already in the queue and will get its turn. A still has a neighbour to add first."* |
| the front, when nothing is left to add | correct — the dequeue |

A wrong tap never changes the queue or the traversal (ADR-021), and there is a
test that applies each wrong action directly to the state and asserts nothing
moved.

## The picture

The same `GraphScene` and `GraphStage` DFS uses, with the queue filled in — one
renderer, two lessons. Three node states, and the middle one is the lesson:

| means | state | reads |
|---|---|---|
| being processed | `COMPARING` | Current |
| seen, waiting in the queue | `CANDIDATE` | In queue |
| dequeued and processed | `FINALIZED` | Visited |
| not seen | `IDLE` | Unvisited |

`CANDIDATE` — the amber Selection Sort uses for a value it is *holding on to* — is
exactly right: a queued node has been found but not processed, and making that
visible is what shows the frontier growing a level at a time.

The queue itself is drawn as cells flanked by `OUT ←` and `← IN`, the same
language the Queue lesson already uses. An empty queue reads `empty`, because it
is the termination condition rather than a blank.

## The Queue connection

BFS uses a queue because **the first node added should be the first node
processed** — first in, first out. AlgoKing already teaches Queue as its own
lesson, and this is where that rule turns out to have been load-bearing.

## Complexity

**Time O(V + E)** — every vertex once, every edge once.
**Space O(V)** — the queue and the visited set.

## Edge cases

All handled and tested: empty graph, single node, a node with no neighbours, a
disconnected graph (only the start's component), cycles and self-loops (terminate,
each node processed once), an unknown start node, and an edge naming a node that
does not exist.

## Progress

The standard MVP model: 0 % → **50 %** on WATCH → **100 %** on TRY.

## Deferred

**CHALLENGE.** `ChallengeCatalog.byId(GRAPH_BFS)` returns `null`, the same as the
other Advanced lessons — see [`v2-challenge.md`](v2-challenge.md).

A shortest-path variant is the obvious next dataset: BFS already computes it, and
the engine would need a level counter rather than a new interaction.
