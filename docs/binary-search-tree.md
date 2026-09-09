# Binary Search Tree — the fifth Advanced lesson

**Status:** shipped · 2026-09-09 · MVP scope is **WATCH → TRY → COMPLETE**
**Category:** Advanced · **Engine:** `engine/algorithms/bst/` · **Model:** `engine/core/BinaryTree.kt`

---

## What it is

A **Binary Search Tree (BST)** is a binary tree that keeps its values in order:

```
for every node:   LEFT SUBTREE values  <  NODE value  <  RIGHT SUBTREE values
```

Values are unique integers. That one property is the whole structure, and it is
what makes searching it cheap: at every node, one comparison says which way to go
and **rules the other subtree out entirely**.

## The decision rule

```
target <  node   ->  go LEFT
target >  node   ->  go RIGHT
target == node   ->  FOUND
no child that way ->  NOT FOUND
```

## The tree

```
             50
            /  \
          30    70
         / \    / \
       20  40  60  80
```

`BstDatasets.teachingTree`, built by **inserting** `50, 30, 70, 20, 40, 60, 80` —
insertion order is what decides a BST's shape, so authoring it that way says where
the shape came from instead of asserting it. WATCH and TRY use this same tree.

Target **60**, and the search path is `50 → 70 → 60`:

| At | Comparison | Because | Move | Ruled out |
|---|---|---|---|---|
| 50 | `60 > 50` | larger values are on the right | **RIGHT** | 20, 30, 40 |
| 70 | `60 < 70` | smaller values are on the left | **LEFT** | 80 |
| 60 | `60 == 60` | — | **FOUND** | — |

Four of the seven nodes are never looked at. That is the lesson, and it is on
screen rather than in a sentence.

**The path is generated, never authored.** `50 → 70 → 60` appears nowhere as data:
`path`, `eliminated` and `current` are real state, and the order falls out of the
decisions. Every test drives the engine and reads the path back out of it —
including one that checks the engine against `BinaryTree.searchPath` for every
target from 0 to 100.

### Why this tree

- **the path turns both ways.** A target reached by going right twice would let a
  learner finish having applied only half the rule.
- **every comparison discards a real subtree** — three nodes, then one.
- **it is perfectly balanced**, so the lesson's own example is the O(log n) case it
  describes, and the skew that costs O(n) can be shown as the contrast rather than
  being the thing the learner was taught on.
- **seven nodes over three levels** fit a phone without shrinking anything.

## WATCH

Ten steps, user-paced, one primary **Next**. Every node is **two** beats, and the
split is the pedagogy: the comparison is stated while the search has **not** moved,
and the move is the next tap.

| # | Beat | Says |
|---|---|---|
| 0 | `SETUP` | Find 60 in this binary search tree. *Every value in a node's left subtree is smaller than it, and every value in its right subtree is larger.* |
| 1 | `EXAMINE` | Start at the root: 50. *A BST search starts at the root and only ever walks downwards.* |
| 2 | `COMPARE` | **60 is greater than 50** · chip `60 > 50` · *In a BST, values greater than a node are in its RIGHT subtree.* |
| 3 | `ELIMINATE` | Move RIGHT. *60 cannot be on that side of 50, so 20, 30 and 40 leave the search.* |
| 4 | `COMPARE` | **60 is smaller than 70** · chip `60 < 70` · *…values smaller than a node are in its LEFT subtree.* |
| 5 | `ELIMINATE` | Move LEFT. *…so 80 leaves the search.* |
| 6 | `COMPARE` | **60 matches the current node** · chip `60 = 60` |
| 7 | `FOUND` | Found 60. *3 comparisons, and 4 of the 7 nodes were never looked at.* |
| 8 | `INSIGHT` | **One path from the root, not a scan of every node.** |
| 9 | `SUMMARY` | Search path: 50 → 70 → 60, and the rule as five bullets. |

Collapsing a node's two beats into one would show a learner a search that has
already moved beside the reason it should move — the wrong order to think in. The
engine emits them as two transitions (`Compare`, then the move), so the seam is
real rather than invented by the narrator.

The elimination beat **names the values that leave**, so "a comparison rules out a
subtree" is a sentence about 20, 30 and 40 rather than an abstraction.

A test pins the exact ten beats, that every `COMPARE` carries its readout chip,
that a `COMPARE` always precedes the `ELIMINATE` it justifies, and that no two
adjacent steps are identical (ADR-020).

## TRY

Same tree, same target — the call DFS and BFS also made. Seven nodes are
memorisable either way, so a second tree would buy unfamiliarity rather than a new
judgement. What makes TRY hard is that the learner now has to *produce* each
comparison's answer, at every node, with the wrong branch one tap away.

**The app states the comparison. The learner decides which way to go.** Reading
`60 > 50` is arithmetic the app owns (`Probe.Mechanical`); knowing that greater
means RIGHT is the entire technique, so it is always a `Probe.Decide`. Nothing
moves on its own after a comparison.

### The three options, every round

`LEFT` · `RIGHT` · `FOUND`

All three are offered every single round, **including when FOUND is wrong**.
Offering FOUND only where it happens to be correct would answer the question the
beat exists to ask: *is this the node?* (ADR-032.)

### Wrong answers

A wrong answer is a learning event, never a state transition (ADR-021). The search
does not move, the same decision stays on screen, and the ladder escalates:

| Attempt | Says | Gives away |
|---|---|---|
| 1 | "Look at the comparison again: 60 against 50." | nothing — points at the evidence |
| 2 | "60 is greater than 50. Which side of a node holds the larger values?" | the reasoning shape |
| 3+ | "60 is greater than 50, and every value greater than a node is in its right subtree. Go RIGHT." | everything, and repeats |

Each wrong option also gets a `whyWrong` line naming the invariant on the side the
learner reached for — *"Everything left of 50 is smaller than 50, and 60 is bigger.
It cannot be down there."* The feedback teaches the rule; it never says "Wrong".

A correct answer says what it **achieved**, not that it was correct: *"60 is
greater than 50, so everything to the left of 50 is out — 3 nodes gone in one
comparison."*

## The picture

BST reuses **`GraphScene`** and the **same `GraphStage` renderer** DFS and BFS use:
a tree is nodes at positions joined by edges, which is what that scene already is.
No fifth scene shape, no second renderer (ADR-036).

| means | node state | reads |
|---|---|---|
| being compared now | `COMPARING` | Comparing |
| on the search path | `FINALIZED` | On the path |
| in a ruled-out subtree | `ELIMINATED` | Ruled out |
| not reached | `IDLE` | Not searched |

The current node is the strongest focus — violet gradient, scaled to 1.08, the
treatment DFS already gives the node it is standing on. Ruled-out nodes stay
exactly where they are, dimmed to 55 % and scaled to 0.88: they have not gone
anywhere, they are simply somewhere the algorithm has proved it need not look.
Edges into them fade to `border`.

Two things were added to the shared scene, both defaulted so DFS and BFS did not
change: `EdgeState.ELIMINATED`, and a `badge` for the target — a badge rather than
a colour, exactly as Binary Search shows its target, because spending a viz hue on
it would break the legend contract.

The **search path** strip is read straight from `state.path`, so the display and
the algorithm cannot disagree. It is captioned *Search path* rather than
*Traversal*: a traversal visits everything, and this walk deliberately does not.
The second strip DFS and BFS use for their stack and queue is switched off — a BST
search is driven by the tree itself, and a line repeating the path above it under
another name would be text pretending to be a data structure.

### Layout is derived, never authored

`BinaryTree.layout()` places each node at its **in-order** position horizontally
and its **depth** vertically, normalised 0..1. In-order is what makes the picture a
tree: every node sits between its two subtrees, so a parent is always drawn above
and between its children, and no two nodes can collide. It is also the truest
picture of the invariant — left to right on screen *is* ascending order.

A graph authors its positions because five nodes have a shape a person should
choose; a tree's shape is implied by its data, so choosing it by hand would let the
drawing and the structure disagree.

**Responsiveness falls out of it.** Positions are normalised, so the stage scales
to whatever width it is given. Two nodes at the same depth always have their lowest
common ancestor between them in in-order, so they are at least **two columns**
apart — on the teaching tree that is a third of the width between same-row
neighbours, which clears a 48dp node with room to spare on a 320dp phone and
spreads out on a tablet. Nothing shrinks, and a test pins the property rather than
leaving it to be checked by eye on one device.

## Complexity

| | |
|---|---|
| Search | **O(h)** — one node per level, where `h` is the height |
| Balanced tree | **O(log n)** — each comparison halves what is left |
| Skewed tree | **O(n)** — the tree behaves like a linked list |
| Space | O(1) for the walk itself |

The lesson says **both**. Claiming O(log n) for every BST is the most common thing
said wrongly about them, so the insight line carries the caveat next to the claim,
and a test drives a deliberately skewed tree — `BinaryTree.of(10, 20, 30, 40, 50)`,
height 5 — to show the degenerate case is real.

## The relation to Binary Search

The last recap bullet, and the only place the connection is drawn:

> Binary Search halves a sorted array by arithmetic. A BST keeps that halving in
> its shape.

Both make the same trade — one comparison, half the remaining space — but Binary
Search recomputes the middle from positions every round, while a BST has the
halving built into the structure: the node itself says which way to go. That is
also why `Dataset.values` for this lesson is the tree read **in order**, which is
literally Binary Search's sorted array.

The connection is one bullet and one dataset choice. The Binary Search lesson is
not repeated.

## Edge cases

All handled in the engine, all tested:

| Case | Behaviour |
|---|---|
| empty tree | a finished search, not a crash — nothing to look at, and `NotFound` is the answer |
| single-node tree | finds its one value, misses everything else |
| target is the root | one comparison, done |
| target in the left / right subtree | walks that way and only that way |
| target absent | ends at a null child — `NotFound`, and a completed search |
| target below the minimum | walks left to the leaf, then off the tree |
| target above the maximum | walks right to the leaf, then off the tree |
| null child | an answer, never an exception |
| deep / skewed tree | correct, one node per level |
| a wrong TRY action | the state is byte-for-byte unchanged — a `Retry` carries no action |

`apply` is total for wrong directions too (ADR-001: the wrong action is the same
code path with a different argument), and a test asserts the state on the other
side of one is still a legal search state that terminates and never claims a find.
In TRY nothing ever calls it that way.

## Progress

The standard MVP model, with no additions: 0 % → **50 %** on WATCH → **100 %** on
TRY, shown as **Completed** on the Home card. BST gets this for free — progress is
`Stage`-driven and has no per-algorithm code anywhere (ADR-028).

## What it cost the architecture

| | |
|---|---|
| New `VizEvent` types | **0** — `Examine`, `Compare`, `Eliminate`, `MovePointer`, `Finalize`, `Mark`, `Meter`, `Terminal` |
| New `Scene` shapes | **0** — the `GraphScene` DFS and BFS already project into |
| New renderers | **0** — the same `GraphStage` |
| New interaction models | **0** — a three-option `OPTIONS` decision, like Two Pointers |
| Changes to existing algorithms | **0** |
| Additive scene fields | `EdgeState.ELIMINATED`, `badge`, `traversalLabel`, `showPathStrip` — all defaulted |
| Files added | 4 engine (model, algorithm, projector, narrator) + 1 test |

## Deferred

**CHALLENGE.** `ChallengeCatalog.byId(BINARY_SEARCH_TREE)` returns `null`, the same
as every other Advanced lesson — see [`v2-challenge.md`](v2-challenge.md).

**Insert, delete and traversals.** `BinaryTree` already carries `insert`, and the
node shape supports the rest, so each is an algorithm and a narrator over the same
model rather than a new structure — but none is built, and the lesson does not
mention them. The obvious next dataset is a different target over the same tree:
20 walks left twice, and 65 ends at a null child, and both are data rather than
code.
