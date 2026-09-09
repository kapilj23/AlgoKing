# AVL Tree — the sixth Advanced lesson

**Status:** shipped · 2026-09-09 · MVP scope is **WATCH → TRY → COMPLETE**
**Category:** Advanced · **Engine:** `engine/algorithms/avl/` · **Model:** `engine/core/BinaryTree.kt`

---

## What it is

A **self-balancing binary search tree**. It is the answer to the caveat the Binary
Search Tree lesson has to end on: a BST is only fast while it stays bushy, and
inserting values in ascending order turns it into a linked list where search costs
O(n). An AVL tree refuses to let that happen.

Every node keeps a **balance factor**:

```
balance factor = height(left subtree) - height(right subtree)
```

and AVL's single rule is that it stays in `-1..1`. The moment an insertion pushes a
node to ±2, the tree **rotates** that node back into range.

## The rule

```
straight (LL / RR)  ->  one rotation,   the CHILD comes up
bent     (LR / RL)  ->  two rotations,  the GRANDCHILD comes up
```

Read the two steps from the unbalanced node down toward the value that just
arrived. If they go the same way, the imbalance runs straight and one rotation
fixes it. If they zig-zag, the grandchild is the node that has to come up, and it
takes two.

## Why rotations are safe, which is the insight

A rotation re-hangs three links. It changes how **deep** nodes are and never
changes their **order** — read the tree left to right before and after and the
sequence is identical, so the result is still a search tree.

That is the whole permission slip for reshaping a tree whenever it needs it, and
the picture demonstrates it rather than asserting it: because node positions come
from `BinaryTree.layout()` — in-order across, depth down — **a rotation moves
nodes between rows and never between columns.** A test pins exactly that.

## WATCH

`30(20(10), 40)`, inserting **35, 5, 37**. Thirteen steps, user-paced.

The three insertions are deliberately not alike:

| Insert | What happens | Why it is in the lesson |
|---|---|---|
| **35** | nothing — every factor is still within ±1 | so nobody leaves thinking every insert rotates |
| **5** | 20 hits +2, path runs **left-left** → one **right** rotation at 20 | the straight case, and the child comes up |
| **37** | 40 hits +2, path runs **left-right** → a **double** at 35 then 40 | the bent case, and the grandchild comes up |

The no-rotation insert comes **first**, on purpose. It is the same reasoning
ADR-025 used to put Bubble Sort's *keep* second, before a learner can decide the
algorithm swaps every pair.

Each repair is four beats — what arrived, which node broke, which node comes up,
and the rotation — and **a double rotation is shown as two separate beats**,
because "a double is two singles" is a sentence that only means something if you
watch it happen twice. The first of the two says so out loud: *"This one does not
fix anything yet. It straightens the bend so the second rotation can be an
ordinary single."*

The run ends on a perfectly balanced seven-node tree — the same shape the Binary
Search Tree lesson is taught on, which is not a coincidence worth hiding: it is
what the rotations were for.

## TRY

`20(10, 30(·, 40))`, inserting **15, 50, 13** — **the mirror image of WATCH.**

| | WATCH | TRY |
|---|---|---|
| insert 1 | 35 — nothing to fix | 15 — nothing to fix |
| insert 2 | 5 — left-left, one **right** rotation | 50 — right-right, one **left** rotation |
| insert 3 | 37 — left-right, a **double** | 13 — right-left, a **double** |

This is why Try is not recall even though the shapes are familiar: **every answer
is the opposite hand of the one WATCH gave**, which is exactly where this
technique is got wrong. Between them the two stages cover all four cases without
either being long enough to become a slideshow.

### One gesture, two questions

> **Tap the node that is out of balance.** Then: **tap the node that takes its place.**

Both are taps on the tree, the same gesture DFS, BFS and the BST lesson use.

The alternative was a row of four buttons reading RIGHT / LEFT / LEFT-RIGHT /
RIGHT-LEFT. It was rejected for the reason ADR-034 rejected a BACKTRACK button:
it teaches the learner to classify a case into a name and reach for the matching
control, when what they actually have to see is **which node comes up**. Naming
the case is the app's job, and it names it the moment they are right.

Asking for the node instead of the name also collapses two questions into one:
*child or grandchild* **is** *single or double*, and a learner who can point at
the right node has understood the thing the four case names are only labels for.

### Wrong answers

A wrong tap is a learning event, never a state transition (ADR-021). Nothing
moves, the same question stays on screen, and the ladder escalates. Each wrong
node is answered with the rule about **that** node:

| Tapped | Answer |
|---|---|
| a balanced node | *"35 is at +1, which AVL allows. Only ±2 needs a rotation."* |
| a higher unbalanced node | *"30 is out of balance too, but 20 is lower. Fix the lowest one and the ones above it come back on their own."* |
| the unbalanced node itself | *"20 is the node that has to move down. Something below it takes its place."* |
| a node outside the subtree | *"35 is not below 20. A rotation only rearranges the nodes under the one that is out of balance."* |
| **the child, when the path bends** | *"Bring 35 up and the path still bends the same way — 40 would be out of balance all over again. The grandchild is the one that straightens it."* |

The last one is the single most common AVL mistake, and it has its own line.

A correct answer says what it **achieved**, not that it was correct: *"10 comes
up, 20 goes down the other side, and the subtree is level again. One rotation."*

## The picture

The same `GraphScene` and `GraphStage` the BST and the two graph lessons use — no
new scene shape, no new renderer (ADR-036, ADR-037).

| Means | State | Reads |
|---|---|---|
| the value that just arrived | `CANDIDATE` amber | Just added |
| the node named as out of balance | `COMPARING` violet | Out of balance |
| everything else | `IDLE` | In place |

Edges carry the rest: the route the new value took is `PATH`, and the two links
the rotation is about to re-hang are `ACTIVE`. A rotation is a change to *links*,
so the links are what light up.

**Every node carries its balance factor**, as a caption above it, with any node
past ±1 marked in `secondary` amber. This is the first lesson where the learner
has to read a value the tree computes about itself, so `GraphNodeView` gained
`caption` and `captionAlert` — defaulted, so DFS, BFS and the BST draw exactly as
they did before. Showing the factor only on the broken node would have turned
"find the unbalanced node" into "find the node with a number next to it".

## Complexity

| | |
|---|---|
| Search | **O(log n)** — guaranteed, because the height is |
| Insert | **O(log n)** to descend, plus **O(1)** to rotate |
| Rebalances per insert | **at most one** — fixing the lowest unbalanced node restores every ancestor |
| Space | O(1) for the repair itself |

The guarantee is the point: a plain BST is O(log n) *on average* and O(n) when it
skews. AVL pays a little on every insert to make the good case the only case. A
test inserts 1…200 in ascending order — the worst case for a BST, a 200-level
linked list — and asserts the AVL tree stays under 10 levels.

## Edge cases

All handled in the engine, all tested:

| Case | Behaviour |
|---|---|
| empty starting tree | just builds one, rotating as it goes |
| nothing to insert | a finished lesson, not a crash — and it still draws the tree |
| a value already present | no-op: no second node, no rotation, no exception |
| ascending inserts | 15 values in 4 levels, against a plain BST's 15 |
| rotating a node with nothing to raise | returns the tree unchanged |
| a wrong tap, applied directly | **refused** — the state comes back byte-for-byte identical |

That last one is worth stating precisely. `apply` stays total (ADR-001), but
naming a node that is not out of balance is not a state the algorithm can be in,
so it comes back unchanged — the same refusal Two Pointers gives a false "pair
found". A test drives the whole machine adversarially, applying *every* node as
both answers at every beat, and asserts it still terminates on a balanced tree.

## Progress

The standard MVP model, with no additions: 0 % → **50 %** on WATCH → **100 %** on
TRY. AVL gets this for free — progress is `Stage`-driven and has no per-algorithm
code anywhere (ADR-028).

## What it cost the architecture

| | |
|---|---|
| New `VizEvent` types | **0** — `Insert`, `Examine`, `Finalize`, `Meter`, `Terminal` |
| New `Scene` shapes | **0** — the `GraphScene` three lessons already project into |
| New renderers | **0** — the same `GraphStage` |
| New interaction models | **0** — tap the node, as DFS, BFS and the BST do |
| Changes to existing algorithms | **0** |
| Additive scene fields | `GraphNodeView.caption`, `.captionAlert` — both defaulted |
| Shared model additions | `heightAt`, `balanceFactor`, `balanceFactors`, `isBalanced`, `parentOf`, `rotateLeft`, `rotateRight`, `avlInsert` on `BinaryTree` |
| Files added | 3 engine + 1 test |

## Deferred

**CHALLENGE.** `ChallengeCatalog.byId(AVL_TREE)` returns `null`, the same as every
other Advanced lesson — see [`v2-challenge.md`](v2-challenge.md).

**Deletion.** AVL deletion needs up to O(log n) rotations rather than one, and
rebalancing on the way back up is a different shape of lesson. `BinaryTree`
carries the rotations it would need; nothing else is built, and the lesson does
not mention it.

**Animating the rotation.** Nodes currently snap to their new positions. Because
the layout keeps every node in its column across a rotation, the movement is
purely vertical and would animate cleanly — a worthwhile polish item, and the one
place this lesson would most obviously benefit from motion.
