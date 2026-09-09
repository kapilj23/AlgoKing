# Binary Tree Traversals — design plan

**Status:** **decisions resolved 2026-09-09, awaiting approval to build** · nothing built
**Scope:** three separate Advanced lessons — Inorder, Preorder, Postorder.

> **All four open questions are now resolved — see §11.** The product owner's rulings on
> 2026-09-09 changed one of them (the engine is now shared, with the rule stated explicitly
> per lesson) and confirmed the other three. §8 has been rewritten to match; everything
> else stands as designed.

---

## 1. The three modules

| Lesson | `AlgorithmId` | Rule | Order on the teaching tree |
|---|---|---|---|
| Binary Tree — Inorder | `TREE_INORDER` | LEFT → **NODE** → RIGHT | 20 30 40 50 60 70 80 |
| Binary Tree — Preorder | `TREE_PREORDER` | **NODE** → LEFT → RIGHT | 50 30 20 40 70 60 80 |
| Binary Tree — Postorder | `TREE_POSTORDER` | LEFT → RIGHT → **NODE** | 20 40 30 60 80 70 50 |

Three `AlgorithmId`s, three `LessonPack`s, three library cards, three progress entries,
three test suites, three completion insights. Nothing is combined, and none of the three
sequences above is written down anywhere in the code — each is produced by running the
lesson's own engine, and the tests assert against an independent recursive reference.

Card title **Binary Tree — Inorder**; header/`displayName` **Inorder Traversal** — the
header is a centred `titleLarge` between two 44dp icon buttons, and "Binary Search Tree"
is already at the width that fits there.

---

## 2. The one interaction — and why it works

> **Tap the node the traversal touches next.**

The same single gesture DFS and BFS use (ADR-034, ADR-035), and the same reason applies
here with more force than anywhere yet: three lessons whose *controls* are identical make
the difference between them impossible to misattribute. The learner is not choosing a
label; they are choosing what happens.

At any moment the traversal's next action is exactly one of four things, and **each maps
to a different node**, so a tap is never ambiguous:

| The action | The tap |
|---|---|
| go into the left subtree | the left child |
| **visit** this node | the node itself |
| go into the right subtree | the right child |
| return to the parent | *(the app does this — see §4)* |

Every wrong tap is therefore a specific misconception with a specific answer. The three
lines the brief asked for fall straight out of the model, at the very first decision of
each lesson:

| Lesson | First decision | A wrong tap gets |
|---|---|---|
| Preorder | visit 50 | tap 30 → *"The root is visited first in preorder."* |
| Postorder | go to 30 | tap 50 → *"Not yet. In postorder, both subtrees are visited before the parent."* |
| Inorder | go to 30 | tap 50 → *"Inorder takes the whole left subtree first — 50 comes after it."* |

**The crux beat.** After a node's left side is finished, the traversal is standing on that
node with one question outstanding: *visit it, or go right?* That single beat is where the
three algorithms disagree, and every lesson asks it at every node. Inorder answers "visit";
postorder answers "go right"; preorder has already visited and answers "go right" too but
for the opposite reason. It is the same tap, on the same tree, with three different right
answers — which is the entire point of shipping three modules.

### Why not a row of buttons

`GO LEFT / VISIT / GO RIGHT / BACK` was rejected. Four `DecisionButton`s do not fit one row
at 16sp/800 (the AVL lesson hit the same wall and was designed around it), and more
importantly a button row teaches the learner to classify the situation into a word and
reach for the matching control. Tapping the node *is* the traversal step.

---

## 3. The picture — no renderer changes

Every lesson projects into the existing `GraphScene`, drawn by the existing `GraphStage`.
Node positions come from `BinaryTree.layout()` — in-order across, depth down — which is
already structural and already guarantees no two nodes on a row are adjacent columns.

| Means | `CellState` | Reads |
|---|---|---|
| the node the traversal is standing on | `COMPARING` | Current |
| **on the call stack, not visited yet** | `CANDIDATE` | Waiting |
| visited — emitted to the output | `FINALIZED` | Visited |
| not reached | `IDLE` | Not reached |

**`CANDIDATE` is the load-bearing state**, and it is what makes postorder teachable
without a word of explanation: 30 sits amber while 20 and 40 turn green underneath it,
*then* 30 turns green. "Children first, parent last" is a picture. Run preorder on the same
tree and 30 turns green the instant it is reached, with its children still grey. Same
colours, same tree, opposite rhythm.

Two strips, both already on `GraphScene` and both read straight from engine state:

- **the output** — `traversal`, labelled `INORDER` / `PREORDER` / `POSTORDER`, growing one
  node at a time. This is the lesson's product.
- **the call stack** — `stack`, labelled `Stack`, drawn as `50 › 30 › 20` (§8). It is the
  recursion made visible without a word about recursion, and it is what explains a return.

Edges: the stack path is `PATH`, the step just taken is `ACTIVE`. Already implemented.

> **One small fix to verify on device.** The output strip holds 7 values plus 6 arrows;
> DFS's only ever holds 5. `StripLine` wraps rather than clips, but it should be checked on
> a 360dp phone and given a two-line allowance if it looks cramped.

---

## 4. Granularity — what is asked and what is not

The full explicit machine has three kinds of step: descend, visit, return. On the teaching
tree that is 17 actions, which is far too many beats for Watch and far too many taps for
Try. The cut:

**Returning to the parent is the app's.** Once a node's three steps are done there is
literally nothing else the traversal could do, so it is bookkeeping, not a judgement
(`PRODUCT_SPEC.md` §3). This is the one place these lessons differ from DFS, where *when*
to backtrack genuinely is the judgement — here it never is.

Crucially the pedagogy survives the cut: the app returns to 30 and immediately asks again,
and *"visit 30 or go right to 40?"* is exactly the beat that distinguishes the three
traversals. Nothing was lost by not charging a tap for the return itself.

**Watch narrates the first subtree in full and collapses the second** — the rule ADR-025
established for the sorts, applied here for the same reason: by the time the traversal
reaches 70, the learner has seen the pattern three times and a fourth full narration is
patience, not understanding.

That gives 12–13 beats per lesson (BFS is 13) and 9 decisions in Try.

---

## 5. WATCH, beat by beat

### Inorder — 13 beats

| # | Beat | Says |
|---|---|---|
| 0 | `SETUP` | Inorder: the left subtree, then the node, then the right. |
| 1 | `EXAMINE` | Go left as far as you can: 50 → 30 → 20. *Nothing is visited on the way down.* |
| 2 | `FOUND` | 20 has no left child, so nothing comes before it. **Visit 20.** |
| 3 | `EXAMINE` | 20 has no right child either — it is finished. Return to 30. |
| 4 | `FOUND` | 30's left side is done. **Visit 30.** |
| 5 | `EXAMINE` | Now 30's right: go to 40. |
| 6 | `FOUND` | **Visit 40.** 30's subtree is complete. |
| 7 | `FOUND` | Back at 50, whose whole left side is now done. **Visit 50.** |
| 8 | `EXAMINE` | The right subtree, same rule: 70 → 60. **Visit 60.** |
| 9 | `FOUND` | Return to 70 and **visit 70**. |
| 10 | `FOUND` | Go right and **visit 80**. Every node is visited. |
| 11 | `INSIGHT` | **Every node waits for its whole left subtree.** |
| 12 | `SUMMARY` | 20 30 40 50 60 70 80, and the rule as bullets. |

Beats 1–7 are the left subtree in full — the answers to *"why is 20 visited before 30"*,
*"why does the traversal return to 30"* and *"why does 40 come after 30"* are beats 1, 3
and 5. Beats 8–10 collapse the right subtree now the rule is established.

Inorder's insight names the thing the other two cannot: **on a search tree, inorder comes
out sorted — and that is a fact about the tree, not about the traversal.** Try then proves
it by running inorder on a tree that is not a search tree (§7).

### Preorder — 12 beats

Opens on the payoff: **visit 50 first**, before either subtree has been looked at. Then
each descent and its visit are one beat, because in preorder arriving and visiting are the
same moment — which is itself the rule, stated by the shape of the walkthrough.

Insight: **the node is visited on the way *down*.** Support names the consequence — a
preorder sequence starts at the root, which is why it is the order used to copy or
serialise a tree.

### Postorder — 13 beats

The most interesting of the three, and the beats are written around the waiting:

| # | Says |
|---|---|
| 1 | Go left as far as you can: 50 → 30 → 20. Nothing is visited on the way down. |
| 2 | 20 has no children at all. **Visit 20.** |
| 3 | Return to 30 — but do **not** visit it. 30 still has a right subtree. |
| 4 | Go to 40. **Visit 40.** |
| 5 | Return to 30. **Now** both of its subtrees are done, so **visit 30**. |
| 6 | Return to 50 — still not yet. Its right subtree has not been touched. |
| 7–9 | 70 → 60, **visit 60**; return, go right, **visit 80**; both done, **visit 70**. |
| 10 | And last of all, **visit 50** — the root, after everything below it. |

Beats 3, 5, 6 and 10 are the lesson. Insight: **a node is visited only once there is
nothing left below it**, which is why postorder is the order you delete a tree in.

---

## 6. TRY

Same gesture, on a tree the learner has not seen. Nine decisions: five visits and four
descents, with returns performed by the app.

Wrong taps never move the traversal (ADR-021 — a `Retry` carries no action), and each is
answered by naming what the rule says about *that* node:

| Tapped | Answer |
|---|---|
| a node whose left subtree is unfinished, in inorder or postorder | *"4 still has a left subtree. Everything under it is visited first."* |
| the parent, in postorder, with a subtree outstanding | *"7's right subtree has not been touched yet. The parent comes last."* |
| an already-visited node | *"2 has already been visited — it is in the output."* |
| a node that is not adjacent to where the traversal is standing | *"A traversal only ever moves to a child or back to a parent. It cannot jump."* |
| going right before visiting, in inorder | *"9 comes after 7, not before it. Inorder visits the node between its two subtrees."* |

---

## 7. The data

**Watch — the shared tree, all three lessons.** As specified in the brief:

```
             50
            /  \
          30    70
         / \    / \
       20  40  60  80
```

Identical in all three modules, so the only thing that differs between the three
walkthroughs is the order that comes out. `TreeDatasets.teachingTree`, authored once and
read by all three packs.

**Try — a different tree, and deliberately not a search tree:**

```
        7
       / \
      4   9
     /     \
    2       5
```

Four reasons:

1. **it kills the shortcut.** Inorder on a search tree is the sorted order, so a learner
   could produce the whole Watch sequence by sorting seven numbers without traversing
   anything. Here inorder gives `2 4 7 9 5`, and only the traversal produces it.
2. **it makes the point that a traversal needs no ordering** — these work on any binary
   tree, and saying so is worth a lesson beat.
3. **it carries two edge cases as content**: 4 has only a left child, 9 has only a right
   one (§12).
4. five nodes is nine decisions rather than thirteen.

Its three orders — inorder `2 4 7 9 5`, preorder `7 4 2 9 5`, postorder `2 4 5 9 7` — are
also all different from each other, so Try discriminates as sharply as Watch does.

> An equally good alternative, from the brief: `7(4(2, 9(5, ·)), ·)` — a root with only a
> left child. It carries a stronger "this is not a search tree" signal but is four levels
> deep against three, which is tighter in a 260dp stage. Either works; the shallower one is
> proposed. Say the word and it swaps — it is one dataset line.

**Why the two stages use different trees, written down because it will be asked:** WATCH
shares one tree across all three lessons so the *only* thing that differs between the three
walkthroughs is the order that comes out — that comparison is the reason three modules
exist. TRY changes the tree so the learner is applying the rule rather than recalling the
sequence, and changes it to a **non-search** tree specifically so that:

> **Inorder traversal is a traversal rule, not a sorting algorithm. It produces sorted
> values only when the tree itself is a search tree.**

The Inorder lesson states that line in its insight — it has just watched inorder produce
`20 30 40 50 60 70 80` and is about to watch it produce `2 4 7 9 5`. The other two lessons
only note that a traversal needs no ordering at all.

---

## 8. Architecture — what is shared and what is not

### Already exists, reused unchanged

`GraphScene` · `GraphStage` · `SceneRenderer` · `SceneLegend` · `LessonPack` ·
`LessonController` · `WatchScreen` · `LessonScreen` · `LessonCompleteScreen` ·
`WatchScriptBuilder` · `DecisionValidation` · progress · navigation · the whole design
system. **No new scene shape, no new renderer, no new screen, no new event type.**

### Shared, needs adding — `engine/core/BinaryTree.kt`

The structure is already there and already serves two lessons. It needs three things:

1. **`preorder()` and `postorder()`**, beside the existing `inorder()` — structural, and
   the independent reference the three test suites assert against.
2. **Structural lookups.** `node(value)`, `searchPath`, `parentOf`, `subtree` and `insert`
   all descend *by comparison* — they assume the search-tree invariant, which is correct
   for BST and AVL and **wrong for a tree that is not sorted**. The traversal lessons need
   `find`, `parentOf` and `pathTo` that walk the structure instead. These are additions;
   nothing existing changes.
3. **A literal builder.** `BinaryTree.of(…)` builds by BST insertion, so it cannot express
   the Try tree. `BstNode(value, left, right)` is already a public data class and can be
   nested directly; a small `tree { }` helper would read better in the dataset file.

Also proposed: rename `BstNode` → `TreeNode`. It is a binary-tree node, three lessons that
are not about search trees are about to use it, and the name will actively mislead. Two
files touched, mechanical.

### Shared, needs adding — the walk

The three traversals are the same machine: a stack of frames, each frame being *a node and
how many of its three steps are done*.

```kotlin
data class WalkFrame(val node: Int, val stepsDone: Int)

data class TreeWalkState(
    val tree: BinaryTree,
    val stack: List<WalkFrame>,   // the call stack, deepest last
    val visited: List<Int>,       // the output — this IS the traversal
    val lastMove: Move?,          // for the picture and the copy
)
```

`visited` is an ordered list because it *is* the answer, exactly as `DfsState.visited` is.

### Shared engine, explicit lessons — **the resolved shape**

One state machine, three lesson definitions that each state their own rule in their own
file. This follows the house precedent exactly: `LinearStructureAlgorithm(flavour)` with
`StackFlavour` and `QueueFlavour` is one engine and two lessons, and ADR-027 records it as
the right call.

```
engine/algorithms/traversal/
├── TreeWalk.kt                  shared  WalkFrame, TreeWalkState, TraversalMove
├── TreeTraversalAlgorithm.kt    shared  the machine: probe, apply, the decision builder
├── TraversalRule.kt             shared  the interface a lesson fills in
├── InorderTraversal.kt          lesson  InorderRule + InorderWatchNarrator
├── PreorderTraversal.kt         lesson  PreorderRule + PreorderWatchNarrator
└── PostorderTraversal.kt        lesson  PostorderRule + PostorderWatchNarrator
```

The engine never names a traversal. Each lesson file opens with its rule, in the plainest
form the language allows:

```kotlin
/**
 * Inorder — LEFT → NODE → RIGHT.
 *
 * The left subtree is finished before the node is visited, and the right subtree
 * is not touched until after it. On a search tree that produces sorted order,
 * which is a fact about the tree and not about the traversal.
 */
object InorderRule : TraversalRule {
    override val id = AlgorithmId.TREE_INORDER
    override val order = listOf(Step.LEFT, Step.VISIT, Step.RIGHT)   // <- the algorithm
    override val copy = TraversalCopy(
        ask                = NarrationId.INORDER_ASK_NEXT,
        whyNodeTooEarly    = NarrationId.INORDER_WHY_LEFT_FIRST,
        whyRightTooEarly   = NarrationId.INORDER_WHY_NODE_BEFORE_RIGHT,
        onVisit            = NarrationId.INORDER_VISITED,
        …
    )
}
```

`order` is one line, it is named, it is documented, and it sits in a file called
`InorderTraversal.kt` next to every sentence that lesson says. That is the opposite of an
opaque constructor parameter: **the rule is the most visible thing in the file.**

`TraversalCopy` is a data class of the ~13 narration ids that differ per lesson — the
prompt, the hint, the three guidance rungs, the five `whyWrong` lines and the three correct
feedback lines. Naming each field is what keeps the per-lesson script readable in one place
rather than scattered through a shared `when`.

**What is shared:** the stack of frames, advancing a frame's step count, pushing a child,
popping a finished frame, emitting a visit, building the `Decision` from the set of legal
taps, and the termination guarantee. Written once, tested once.

**What is per-lesson:** the step order, every line of copy, the walkthrough narrator, the
datasets entry, the completion insight, the library card, the tests. That is where a
lesson's identity actually lives, and none of it is shared.

The three projectors differ only in the output strip's label, so they are one
`TraversalProjector(label)` rather than three copies of the same forty lines.

> **Why this is safe rather than a compromise.** The acceptance gate at §12 gets sharper,
> not weaker: adding Preorder should touch **one new rule object, one narrator, one copy
> block, one dataset entry, one card and one test suite — and zero lines of the engine.**
> If Preorder needs an engine change, the shared machine is wrong and gets revised before
> Postorder is written.

---

## 9. Edge cases

Handled in the shared walk, so all three inherit them, and tested per lesson:

| Case | Behaviour |
|---|---|
| empty tree | terminal immediately, empty output — not a crash |
| single node | one visit, whatever the order |
| left child only | the right step is skipped silently; no beat is manufactured for it |
| right child only | likewise — and the Try tree has one of each |
| a long left spine | the stack strip grows; correct output, no special case |
| unbalanced / larger tree | correct; the stack is O(h), which is the space claim |
| null children | never dereferenced — a missing child advances the frame's step count |
| duplicate values | out of scope: the teaching trees have unique values, and a tap identifies a node by value |

The last one is a genuine constraint of the tap interaction and should be written down
rather than discovered later.

---

## 10. Tests

`BinaryTreeTest` — the shared structure, tested once: the three traversal functions against
hand-worked expectations, the structural lookups, layout invariants, and the edge cases
above.

`InorderTraversalTest`, `PreorderTraversalTest`, `PostorderTraversalTest` — each asserting:

- the order the engine produces equals an **independent recursive reference** over the same
  tree, for the teaching tree, the Try tree, and a set of generated shapes;
- every edge case in §9 through the real engine;
- the decision at every beat: kind, correct action, a `whyWrong` for every wrong option,
  a three-rung ladder;
- **a wrong tap leaves the state byte-for-byte identical**, and the run still terminates
  when driven adversarially;
- the walkthrough: pinned beat sequence, no two adjacent steps identical (ADR-020), and
  the output strip growing by exactly one node per visit.

`TreeTraversalComparisonTest` — the claim of §9 of the brief, as code: all three engines
over the one shared tree produce the three documented sequences, all three visit each of
the seven nodes exactly once, and the three sequences are pairwise different. This is the
test that would catch a copy-paste between the three rules.

**Traversal depends on structure alone, never on values.** Asserted directly: take a tree,
relabel every node through an order-scrambling map, and the traversal must visit the same
*positions* in the same order — the output is the relabelled sequence, not a re-sorted one.
This is the test that proves inorder is not sorting, and it runs for all three lessons over
the balanced tree, the non-search tree, a left-only chain and a right-only chain.

Coverage required per lesson (§14 of the brief): balanced tree · non-search tree · empty ·
single node · left-only · right-only · unbalanced · state transitions · Try validation.

---

## 11. Decisions — resolved

**1. One shared engine, three explicit lesson definitions.** *(the crux — changed from the
first draft)*
The three modules stay separate everywhere the learner can see them: three `AlgorithmId`s,
three `LessonPack`s, three library cards, three progress entries, three narrators, three
test suites, three completion insights. Underneath, one `TreeTraversalAlgorithm` drives a
shared stack machine and each lesson supplies a named `TraversalRule` whose `order` is the
algorithm, written on one line in its own file. **The rule must be the most visible thing
in the lesson file** — never an argument passed from somewhere else. §8 has the shape.
*Ruling: no ~380 lines of duplicated engine; readability is preserved by where the rule
lives, not by copying the machine three times.*

**2. Try runs on a non-search tree.** Confirmed. Inorder on a BST is the sorted order, so
Watch's answer could be produced by sorting seven numbers without traversing anything. The
Try tree is deliberately not sorted, and every lesson says so once:

> *Inorder traversal is a traversal rule, not a sorting algorithm. It produces sorted
> values only when the tree itself is a search tree.*

That line belongs in the Inorder lesson's insight and in this document; the other two
mention only that a traversal needs no ordering at all.

**3. Returning to the parent is the app's.** Confirmed. Nine decisions rather than
thirteen, and nothing is lost: WATCH still shows every return as its own beat (§5), and the
question the app asks *after* a return — *visit this node, or go right?* — is exactly the
beat that separates the three traversals.

**4. `BstNode` → `TreeNode`: yes, and it is safe.** Measured rather than assumed: `BstNode`
appears in **two files and twenty-one places** — `BinaryTree.kt` (19, nearly all private
helpers) and `BinarySearchTree.kt` (2). Kotlin is statically typed, so the compiler proves
the rename complete, and the 499-test suite covers BST and AVL behaviour. It lands as its
own commit, before any traversal code, so it can be reverted independently of the feature.

The **existing search-tree methods are not touched.** `node()`, `searchPath()`, `contains()`,
`insert()`, `subtree()`, `parentOf()`, `balanceFactor()` and the rotations all descend by
comparison and stay exactly as they are; they gain a doc line saying they require the
search-tree invariant. The traversal lessons use new, purely structural additions
(`preorder()`, `postorder()`, `findNode()`, `pathToNode()`, `parentByStructure()`), so
**BST and AVL behaviour cannot change** — which is priority 1.

---

## 12. Build order and the acceptance gates

| | | |
|---|---|---|
| 0 | `BstNode` → `TreeNode`, **its own commit**, 499 tests green before and after | ~10 min |
| 1 | Shared: `BinaryTree` structural additions + `TreeWalk` + `TreeTraversalAlgorithm` + `TraversalRule`, with `BinaryTreeTest` | ~0.5 d |
| 2 | **Inorder** end to end — rule, copy, narrator, dataset, tests, wiring | ~1 d |
| 3 | ⟵ **gate** ⟶ | |
| 4 | **Preorder** | ~0.5 d |
| 5 | ⟵ **gate** ⟶ | |
| 6 | **Postorder** — the same, plus the most careful copy of the three | ~0.5 d |
| 7 | Docs: `docs/tree-traversals.md`, ADR-038, and the four canonical documents | ~0.5 d |

**The gate, run after Inorder and again after Preorder**, in the spirit of the slice-2 gate
in `docs/plans/01-bubble-sort-slice.md`. Adding the next traversal must require:

- **zero** new `VizEvent` types
- **zero** new `Scene` shapes
- **zero** renderer changes
- **zero** new screens or interaction models
- **zero** changes to `TreeTraversalAlgorithm` or `TreeWalk`
- **zero** changes to BST, AVL or any existing lesson

If any of those is violated, stop and revise the architecture before writing the next one.
Discovering it at the third lesson costs two rewrites.
