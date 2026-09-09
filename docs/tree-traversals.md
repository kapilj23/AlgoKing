# Binary Tree Traversals — three Advanced lessons

**Status:** shipped · 2026-09-09 · MVP scope is **WATCH → TRY → COMPLETE**
**Category:** Advanced · **Engine:** `engine/algorithms/traversal/`
**Design plan:** [`tree-traversals-plan.md`](tree-traversals-plan.md)

| Lesson | Rule | On the teaching tree |
|---|---|---|
| **Binary Tree — Inorder** | LEFT → **NODE** → RIGHT | 20 30 40 50 60 70 80 |
| **Binary Tree — Preorder** | **NODE** → LEFT → RIGHT | 50 30 20 40 70 60 80 |
| **Binary Tree — Postorder** | LEFT → RIGHT → **NODE** | 20 40 30 60 80 70 50 |

Three `AlgorithmId`s, three `LessonPack`s, three library cards, three progress
entries, three narrators, three test suites, three completion insights. **None of
those three sequences appears anywhere in the code** — each is produced by running
its own lesson, and the tests assert them against `BinaryTree`'s independent
recursive implementations.

---

## One tree, three orders

```
             50
            /  \
          30    70
         / \    / \
       20  40  60  80
```

All three walkthroughs run on **this same tree**, which is the whole reason the
three lessons are worth shipping separately. The data is identical, the picture is
identical, the gesture is identical — and the orders that come out are not. A
learner who has done all three has watched one tree produce three answers, which
is stronger than any sentence the app could write.

`TreeTraversalComparisonTest` is that claim as code: the three orders on one tree,
pairwise different, every node visited exactly once by each, and the same wrong tap
answered differently by each lesson.

---

## The interaction

> **Tap the node the traversal touches next.**

The gesture DFS, BFS, the BST and AVL already use. At any moment the traversal's
next action is one of four things, and **each maps to a different node**, so a tap
is never ambiguous:

| The action | The tap |
|---|---|
| go into the left subtree | the left child |
| **visit** this node | the node itself |
| go into the right subtree | the right child |
| return to the parent | *nobody — the app does it* |

### The crux beat

Once a node's left side is finished, the traversal is standing on it with one
question outstanding: **visit it, or go right?** Every lesson asks it at every
node, and the three disagree:

| | at 30, with 20 out and 40 untouched |
|---|---|
| **Inorder** | visit 30 — its left subtree is done, and the node comes before the right |
| **Postorder** | go to 40 — *both* subtrees come before the parent |
| **Preorder** | go to 40 — 30 came out on arrival, long ago |

One tap, one tree, three right answers. That is the lesson, and it is why the
controls had to stay identical across the three modules.

### Every wrong tap is a specific misconception

| Tapped | Inorder says | Preorder says | Postorder says |
|---|---|---|---|
| this node, too early | *"Everything in 50's left subtree is visited before 50 itself."* | — | *"Both child subtrees are visited before the parent, and 50 still has one outstanding."* |
| a child, too early | — | *"The node is visited first in preorder. 50 comes out before anything below it."* | — |
| the right child first | *"The left subtree comes first."* | *"The left subtree comes first."* | *"The left subtree is finished first."* |
| an already-visited node | *"20 is already visited — it is in the output."* | same | same |
| somewhere unreachable | *"A traversal only moves to a child or back to a parent. It cannot jump."* | same | same |

### Two things the app does, not the learner

**Returning to the parent.** Once a node's steps are all done there is nothing else
the traversal could do, so charging a tap for it would be charging for bookkeeping
(`PRODUCT_SPEC.md` §3). Nothing is lost: WATCH still shows every meaningful return
as its own beat, and the question the app asks *after* a return is the crux beat
above.

**Visiting on arrival.** Moving into a node performs whatever that node then owes
with no choice attached — its own visit, when that is the next step. Without this
the learner taps the same node twice in a row at every leaf, and the second tap has
exactly one legal target. A test asserts no lesson ever asks for two consecutive
taps on one node.

The two together give **9 decisions** for inorder and postorder and **7** for
preorder on the teaching tree, where the raw machine would have asked for 17.

---

## The picture — nothing new was needed

The same `GraphScene` and `GraphStage` four lessons already use, with no renderer
change of any kind. Positions come from `BinaryTree.layout()` — in-order across,
depth down.

| Means | State | Reads |
|---|---|---|
| where the traversal is standing | `COMPARING` | Current |
| **reached, waiting on the stack, not emitted** | `CANDIDATE` | Waiting |
| emitted to the output | `FINALIZED` | Visited |
| not reached | `IDLE` | Not reached |

**`CANDIDATE` carries the lesson.** It is what makes three traversals visibly
different on identical data:

- **postorder** leaves a trail of amber parents that turn green only on the way
  back up — "children first, parent last" as a picture rather than a sentence;
- **preorder** turns a node green the moment it is reached, so green grows
  downward *ahead* of the walk;
- **inorder** does both, and an amber node sitting between a green left subtree and
  an untouched right one is exactly the beat being taught.

Two strips, both existing fields: the **output** (`traversal`, labelled Inorder /
Preorder / Postorder) and the **call stack** (`stack`, labelled Stack). The stack
is supporting information — the tree, the current node and the order are the
picture — and it is the recursion made visible without a word about recursion.

---

## WATCH

12–16 beats each, user-paced. The **left subtree is narrated in full and the right
one is collapsed** — the rule ADR-025 set for the sorts.

A return earns a beat **when it lands on a node that still owes something.** One
line of shared logic, and it produces opposite results:

- **postorder** earns the most: every return lands on a parent that is still
  waiting, which is where the lesson lives — *"Back to 30. But not 30 yet — it
  still has a right subtree that has not been touched."*
- **preorder** earns **none**, and that is the lesson rather than an omission: a
  parent is always already out by the time the traversal comes back up, so the way
  back really does do nothing.
- **inorder** earns some — the returns onto nodes still owed their visit.

Each lesson closes on its own insight:

| | |
|---|---|
| Inorder | *"Every node waits for its whole left subtree."* |
| Preorder | *"The node is emitted on the way down."* |
| Postorder | *"A node waits for everything beneath it."* |

---

## TRY — and why the tree changes

```
        7
       / \
      4   9
     /     \
    2       5
```

Read in order that is `2, 4, 7, 9, 5` — **not sorted**, and that is the point.

> **Inorder traversal is a traversal rule, not a sorting algorithm. It produces
> sorted values only when the tree itself is a search tree.**

On the WATCH tree inorder comes out sorted, so a learner could produce the whole
answer by sorting seven numbers without traversing anything. Here they cannot: only
the rule produces `2, 4, 7, 9, 5`. The Inorder lesson says the line out loud in its
insight, having just watched both happen.

The same tree also carries two edge cases as ordinary content — 4 has only a left
child and 9 has only a right one — and five nodes keeps TRY to six or seven
decisions. It is built from `TreeNode` literals, because `BinaryTree.of` inserts in
search-tree order and could not express a tree that is not one.

**Why the two stages differ, stated once:** WATCH shares one tree across all three
lessons so the only thing that differs between the walkthroughs is the order that
comes out. TRY changes the tree so the learner is applying the rule rather than
recalling a sequence, and changes it to a tree that is not ordered so that inorder
cannot be shortcut.

---

## Complexity

| | |
|---|---|
| Time | **O(n)** — every node is pushed once, stepped once, popped once |
| Space | **O(h)** — one stack frame per level of the current path |
| Worst case | `h = n` on a skewed tree, which is the chain the AVL lesson exists to prevent |

The lesson can claim O(h) honestly because the stack is on screen doing it.

---

## Architecture

**One machine, three lessons.** `TreeTraversalAlgorithm` walks the tree with an
explicit stack of `WalkFrame`s — the recursion written as data, so it can be drawn,
stepped and rewound. It never learns which traversal it is running: it reads a
`TraversalRule`, and each lesson supplies one whose `order` is the algorithm, on
one line, in its own file:

```kotlin
object InorderRule : TraversalRule {
    override val order = listOf(Step.LEFT, Step.VISIT, Step.RIGHT)   // the algorithm
    …
}
```

This is `LinearStructureAlgorithm(flavour)` applied again (ADR-027): one engine,
the difference stated where the lesson is. Full reasoning in ADR-038.

| Shared | Per lesson |
|---|---|
| the frame stack, pushes, pops, visits | the step order — **the rule** |
| the decision builder and the five wrong-tap categories | every line of copy, in a named `TraversalCopy` |
| the termination guarantee | the walkthrough narrator |
| `TraversalProjector`, differing only in the strip label | the datasets entry, card, insight, tests |

### The shared tree, and the trap in it

`BinaryTree` serves five lessons now, and its operations fall into two halves:

- **structural** — `inorder`, `preorder`, `postorder`, `findNode`, `pathToNode`,
  `parentByStructure`, `childrenOf`, `edges`, `depths`, `layout`. Correct for **any**
  binary tree.
- **search-tree** — `node`, `contains`, `searchPath`, `insert`, `subtree`,
  `parentOf`, `balanceFactor`, the rotations. They descend **by comparing values**,
  so on an unordered tree they quietly fail to find nodes that are plainly there.

The traversal lessons use the structural half exclusively. `BinaryTreeTest` pins
the distinction directly: on the TRY tree, `node(5)` returns null while
`findNode(5)` returns the node — the exact trap, asserted so it cannot be walked
into again.

`BstNode` was renamed to `TreeNode` before any of this was written, in its own
commit: it is a binary-tree node, and three lessons that are not about search trees
now use it.

## What it cost the architecture

| | |
|---|---|
| New `VizEvent` types | **0** |
| New `Scene` shapes | **0** |
| Renderer changes | **0** — not one line of `:app/ui/components` |
| New screens or interaction models | **0** |
| Changes to existing lessons | **0** |
| Shared model additions | `preorder`, `postorder`, `findNode`, `holds`, `pathToNode`, `parentByStructure`, `childrenOf` — all additive |
| Files added | 6 engine + 5 test |

**The acceptance gate held.** After Inorder was finished, adding Preorder and then
Postorder required a rule object, a narrator, a copy block, a dataset reference, a
card and a test suite each — and **zero lines of `TreeTraversalAlgorithm` or
`TreeWalk`**.

## Edge cases

All handled in the shared walk, so all three inherit them, and all three test them:
empty tree · single node · left-only chain · right-only chain · unbalanced ·
larger trees · null children · a tree that is not a search tree · a wrong tap
(refused, state byte-for-byte identical) · adversarial driving (every node applied
at every beat, and the run still terminates on the correct order).

`TreeTraversalComparisonTest` additionally proves **traversal depends on shape
alone**: relabel every node through an order-destroying map and each lesson visits
the same positions in the same sequence.

## Progress

Three independent entries: 0 % → 50 % on WATCH → 100 % on TRY, one per lesson,
never combined. All three get it for free — progress is `Stage`-driven and has no
per-algorithm code (ADR-028).

## Deferred

**CHALLENGE.** All three return `null` from `ChallengeCatalog.byId`, the same as
every other Advanced lesson — see [`v2-challenge.md`](v2-challenge.md).

**Level-order.** The obvious fourth: it is a queue rather than a stack, so it does
not fit `TraversalRule` — it would be a sibling of this machine, not a fourth rule
for it. Worth noting that the Queue and BFS lessons already teach what it needs.
