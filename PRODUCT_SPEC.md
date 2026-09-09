# AlgoKing — Product Specification

**Status:** LOCKED · v1.0 · 2026-08-23
**Canonical readable version:** https://claude.ai/code/artifact/53686787-71eb-4768-a51d-3c0f52ecac2b

> Every other app shows you the algorithm. This one makes you run it.

> **⚠ MVP AMENDMENT — 2026-09-04.** The MVP spine is **WATCH → TRY**. The CHALLENGE
> stage is **deferred to V2**, not cancelled — see `docs/v2-challenge.md`. The
> CHALLENGE passages below (§2 · §6 · §7 · §8 · §15) describe the V2 target and are
> **not** what the MVP ships; where this amendment and a section disagree, the
> amendment wins. They are retained deliberately: the engine still implements them,
> and they are what V2 builds from.

This file is the single source of truth for **product behaviour**.
Technical decisions live in `ARCHITECTURE.md`. Visual decisions live in `DESIGN_SYSTEM.md`.
Any change to this file requires product-owner approval.

---

## 1. Locked principles

```
MVP:  WATCH → TRY → COMPLETE
V2:   WATCH → TRY → CHALLENGE → RESULT
```

- **Animation teaches.** The animation is not decoration; it is the lesson.
- **Interaction reinforces.** The learner operates the algorithm, they do not watch it.
- **A NEW problem tests understanding.** Challenge data is never the Watch data.
- **Mastery represents demonstrated skill.** Not time spent, not lessons opened.

**Monetization:** free forever, AdMob only.
**Never:** subscriptions · paid algorithms · locked content · coins · gems · energy · lives · leaderboards · social graph · required login.

---

## 2. The learning stages

| Stage | Ships in | Length | The learner's job | Scored |
|---|---|---|---|---|
| **WATCH** | **MVP** | ~60 s | Observe, at their own pace. | No |
| **TRY** | **MVP** | ~2 min | Same dataset, full guidance, the learner decides. | No |
| **CHALLENGE** | *V2* | ~2 min | New dataset, no guidance, metrics HUD. | Yes |

**In the MVP there are exactly two stages, and there is no MASTER stage.** Finishing TRY
completes the algorithm. The learner lands on **COMPLETE**, which reports what the run was —
decisions, comparisons, wrong turns — and states the one idea the lesson existed to leave
behind. The Home card then reads **Completed** at 100 %.

**Try is never scored, so COMPLETE has no stars.** Try exists so a learner can be wrong as
often as they like at no cost; grading that run would turn the guidance ladder into something
to avoid rather than something to use. Assessment is CHALLENGE's job, and it returns with
CHALLENGE.

> *V2:* CHALLENGE becomes a third stage, followed by **RESULT** — stars, the run metrics, one
> generated verdict line, and either 🏆 Mastered or ⭐ Keep practising. Mastery is a *status*
> awarded there at ★★ or better (§7) and shown on the Home card; it is never a place the
> learner navigates to, never a node in the stage spine, and never a tab.

---

## 3. The decision, per algorithm

The user's decision must be the one that carries the algorithm. Mechanical pointer
advancement is performed by the app, never by the user.

| Algorithm | App does automatically | Learner decides |
|---|---|---|
| Binary Search | computes `mid` | **Which half survives?** (left / right / found) |
| The Race (Linear) | runs both | which will finish first, and by how much |
| Bubble Sort | advances the pair pointer | **Swap or keep?** |
| Selection Sort | advances the scan cursor | **Is this the new minimum?** + where it lands |
| Insertion Sort | lifts the key, walks the compare cursor | **Shift right, or insert here?** |
| Two Pointers | adds the two values and states the sum | **Move LEFT, move RIGHT, or pair found?** |
| Prefix Sum | gives the leading `prefix[0] = 0` | **each running total**, then **which two prefix values answer the range** |
| Graph DFS | gives the start node | **which node DFS moves to next** — deeper into the first unvisited neighbour, or back to where it came from |
| Graph BFS | gives the start node | **which node BFS touches next** — the next unseen neighbour onto the queue, or the front of the queue off it |
| Binary Search Tree | compares the target with the current node and states the result | **which way the search goes** — LEFT, RIGHT, or "found" |
| Sliding Window | — | **Grow, or shrink from the left?** |
| Merge Sort | the deeper splits, the leftover tail | **Where does it split?** then **which front value comes next?** |
| Quick Sort | picks the pivot, orders the partitions | **Which side of the pivot?** then **where does the pivot land?** |
| Stack | — | **Which operation does this job?** then **which item comes off next?** |
| Queue | — | the same two questions, and the opposite answers |
| Linked List | — | **Is this the node?** then **which link changes, and where should it point?** |
| Hash Map | stores and removes, once the judgements are made | **Which bucket does `key % 5` land in?** then, when the bucket is not empty, **what happens?** |

> **Explicitly rejected:** "Which pair should we compare?" in Bubble Sort. The next pair is
> always the next pair — there is no decision, and tapping the only legal target teaches a
> gesture rather than the comparison logic. Exception: this prompt appears exactly twice per
> Bubble Sort lesson — at the start of pass 2 (teaches restart-from-left) and at the point
> where the tail is already sorted (teaches early stop).

---

## 4. Watch — the walkthrough contract

**Watch is an interactive walkthrough, not a video.** There is no play button, no pause, no
autoplay and no speed control. The learner advances with a single primary **NEXT**, and the
pace is entirely theirs.

### The step contract

Watch is a list of **steps**. Each step is a real, deterministic algorithm state paired with
the sentence that explains it:

```
WatchStep { algorithmState · scene · headline · optional support · optional comparison }
```

- **Every NEXT must produce a meaningful visual change.** A step where nothing changed is a
  bug, not a beat. There is a test for exactly this.
- **Every step answers one question:** *what did the algorithm just do?*
- **Copy is one short sentence, plus at most one more.** Never a paragraph. The visualisation
  and the text reinforce each other; neither carries the lesson alone.
- Steps are produced by the engine, not authored per screen, so the same machinery serves
  Bubble Sort, Two Pointers and Sliding Window (`ARCHITECTURE.md` §4.4).

### The shape of a Watch

| Kind | What it does |
|---|---|
| `SETUP` | the problem, before anything happens — *"Let's find 73."* |
| `EXAMINE` | attention moves to a cell — *"First, check the middle."* |
| `COMPARE` | the comparison is stated, with a readout chip — *"45 is smaller than 73."* |
| `ELIMINATE` | part of the search space visibly collapses — *"73 must be on the right."* |
| `FOUND` / `NOT_FOUND` | the outcome |
| `INSIGHT` | the one engineered idea of the lesson, given its own step |
| `SUMMARY` | the recap, and the hand-off to Try |

Binary Search on the teaching array runs exactly nine steps:
`SETUP · EXAMINE · COMPARE · ELIMINATE · EXAMINE · COMPARE · FOUND · INSIGHT · SUMMARY`.

### Insight frames
One engineered moment per algorithm, given its **own step** so it cannot be scrolled past.

| Algorithm | The line |
|---|---|
| Binary Search | "One comparison. Half the search space." |
| The Race | "32 comparisons. Or 6." |
| Bubble Sort | "The largest number bubbled to the end." |
| Selection Sort | "A whole pass. One swap." |
| Insertion Sort | "The left side is always already sorted." |
| Two Pointers | "Each move rules out a whole row of pairs." |
| Binary Search Tree | "One path from the root, not a scan of every node." |
| Sliding Window | "The window never re-reads what it already counted." |
| Stack or Queue | "Last in, first out. First in, first out." |

### Progress and navigation
- Header: back · algorithm name · a quiet `n / total` count.
- A small dot row under the stage spine. It answers *"am I getting anywhere?"* and nothing
  else — it must never compete with the array for attention.
- Bottom: **NEXT** as the single primary CTA. A subtle **Back** appears only once there is
  somewhere to go back to.
- The final step's primary reads **Start Try**.

### Motion
Transitions between steps still animate — cells collapse and desaturate over 300 ms, the
comparison chip fades in — but **motion is a consequence of the learner's tap, never a clock.**
The timing numbers that used to live here governed autoplay and no longer apply to Watch;
per-transition durations are in `DESIGN_SYSTEM.md` §8.

---
## 5. Try — the guidance ladder

> **The core rule: a wrong decision is a learning event, not a state transition.**
>
> In Try the learner may choose wrongly as often as they like, and the algorithm **never**
> moves. The state stays byte-for-byte where it was, the same decision is still on screen, and
> the only way forward is the correct action. A learner can never progress through Try by
> guessing.

1. **The handoff** — narration becomes a question, the decision options rise, one haptic tick.
2. **Decision loop** — 2–4 options, ≥56dp, **identical visual weight in every state**.
3. **Escalating guidance** — see the ladder below. It never runs out.

### The ladder

| Attempt | Response | Does the algorithm move? |
|---|---|---|
| **Correct** | Execute, plus one line of *why it worked*. Move on briskly. | **Yes** |
| **Wrong · 1st** | Options shake. *"Look at the comparison again."* Point at the evidence, say nothing more. | **No** |
| **Wrong · 2nd** | Ask the reasoning question — *"The target is larger than the middle. Which side can still contain it?"* — and add why the chosen option fails. | **No** |
| **Wrong · 3rd+** | State it plainly: *"42 is smaller than 62, so 62 must be on the right. Keep the right half."* The most explicit rung then repeats — never dead-end a learner. | **No** |
| Hint (tapped) | The free hint, available before any wrong answer. | No |

**No penalty, ever.** A wrong answer in Try costs no stars, fails nothing, resets nothing, and
shows no red failure state. The learner should finish thinking *"the app showed me why I was
wrong"*, never *"I failed"*.

**Feedback is always contextual, never generic.** *"❌ Wrong"* is forbidden. Every rung names
the actual numbers in front of the learner.

### Wrong answers never branch the algorithm

```
        correct state
              ↓
      decision required
              ↓
        user action
         ╱          ╲
   correct            wrong
      ↓                 ↓
   advance          feedback
                        ↓
                  SAME state, retry
```

This is enforced in the engine, not in each screen: `DecisionValidation.validate` is pure and
returns either `Accept(action)` or `Retry(level, guidance)`. **A `Retry` carries no action, so
there is nothing the caller could apply.** The rule therefore holds for every algorithm —
Binary Search, Two Pointers, Sliding Window, Bubble, Selection, Insertion, Stack or Queue —
without a line of per-algorithm code.

Per-algorithm examples of the same rule:

| Algorithm | Wrong choice | What must NOT happen | What happens instead |
|---|---|---|---|
| Binary Search | keeps the impossible half | the half is eliminated | *"42 is smaller than 62."* → retry |
| Bubble Sort | KEEP when 7 > 3 | the pair is kept and the pass advances | *"7 is greater than 3. The larger value moves right."* → retry |
| Two Pointers | moves the wrong pointer | the pointer moves | *"The sum is below the target. We need a larger value."* → retry |
| Sliding Window | shrinks a window that is still invalid | the window shrinks | *"The window is still below the condition."* → retry |

Rewind stays free in Try, and free once per Challenge — but it is now for changing your mind
after a *correct* answer, not for undoing damage: a wrong answer never did any.

### Challenge is different
Challenge is unguided and never blocks. A wrong decision is logged as ✕ and the algorithm
advances **with the correct action**, so one mistake cannot cascade into a ruined run (§6). The
algorithm is never corrupted there either — it simply is not paused to teach.

---
## 6. Challenge — *V2, deferred*

> **Not in the MVP.** This whole section describes the deferred CHALLENGE stage
> (`docs/v2-challenge.md`). The engine still implements all of it; no MVP screen reaches it.

> **WATCH** — "let me show you." **TRY** — "let's do it together."
> **CHALLENGE** — "now you do it yourself."

Challenge tests whether the learner can *apply* the algorithm to a problem they have never
seen. It is not another tutorial, and it must never explain the next step.

### What makes it a challenge

1. **Fresh data, every time.** Never the Watch array, the Watch target, the Try array, the Try
   target, or the same decision sequence. The learner has to retrieve the model, not recall the
   run. Enforced by `NoValueOverlapWith`, asserted against the trace (§9 of `ARCHITECTURE.md`).
2. **The learner picks the middle.** Try lets the app compute it; Challenge asks
   *"Where should you look first?"* and makes the array tappable. From the second round on the
   prompt becomes *"What is your next move?"* — the app never says "now check the middle".
3. **No instructional narration.** After a comparison the app states the fact — *"46 < 71"* —
   and stops. Which half survives is the learner's call.
4. **A briefing, not a lesson.** The intro screen shows the target, the difficulty, the type,
   and a button. No re-explanation of Binary Search.

### Wrong decisions

Identical invariant to Try — **the algorithm state never changes** — but the *response* is
deliberately thinner:

| | Try | Challenge |
|---|---|---|
| Wrong answer | escalating teaching ladder + why that option fails | one neutral clue, e.g. *"46 is smaller than 71."* |
| Escalation | 3 rungs, ending in the explicit answer | none — it stays terse however many times |
| Recorded | no penalty | **mistake recorded** |
| Retry | yes | yes |

A wrong *middle* gets *"That is not the middle of the current range."* and nothing more.
**The learner can never progress by guessing** — only the correct action advances anything.

### Challenge types

| Type | Algorithm | Asks | Ends |
|---|---|---|---|
| **FIND** | Binary Search | *Find 71.* | the target is located |
| **NOT_FOUND** | Binary Search | *Find 70.* | the range empties — **a success, not a failure** |
| **SORT** | Bubble Sort | *Sort the array.* | every value is in place |
| **EARLY_EXIT** | Bubble Sort | *Sort the array* — but it already is | a pass makes no swaps |
| **OPERATIONS** | Stack, Queue | *Run this sequence of operations.* | the structure is drained, including one removal past empty |
| **TRAVERSE** | Linked List | *Find 40.* | the node turns up, or NULL proves it will not |
| **LINK_INSERT** | Linked List | *Insert 18.* | the node is in the right gap **and** points at the right node |
| **LINK_DELETE** | Linked List | *Delete 23.* | the chain reconnects around it — a broken link is never accepted |
| **HASH_OPERATIONS** | Hash Map | *Run these PUTs, GETs and REMOVEs.* | every key was hashed, and the shared bucket handled |
| **HASH_COLLISION** | Hash Map | *Two keys already share a bucket. Find one.* | the right entry, not merely the right bucket |

`NOT_FOUND` is not an edge case bolted on: driving the range to empty is half of Binary
Search, and a learner who does it correctly scores exactly as well as one who finds a target.
The engine is shaped so later types — predict the next middle, spot the incorrect step,
minimise comparisons — add a `ChallengeType` and nothing else.

**Structure difficulty is length, not rules.** LIFO does not get harder; staying consistent
across more operations does. A structure challenge is a longer script over two-digit values the
learner has not seen in Watch or Try, and its final beats always drain the structure to empty
and then ask for one removal more — because *"what happens when it is empty?"* is the question
every real implementation has to answer.

### Difficulty

**Sorting difficulty is shape, not size.** A learner who only ever meets reversed arrays never
has to decide KEEP, so the Bubble Sort rotation mixes mixed, reversed, nearly-sorted, duplicate
and already-sorted data. Sorting arrays stay short (4–6 values): every element costs taps.

| Tier | Array | Appears |
|---|---|---|
| Beginner | 7–9 values, target present | round 1 |
| Intermediate | 11–13 values, target anywhere | rounds 2–3 |
| Advanced | 14–16 values, target may be absent | round 4+ |

Difficulty is a property of the **data**, never of the rules. A harder challenge is a bigger
array or a less obvious target — never a stricter validator, and **never a countdown timer**.
Elapsed time is recorded for personal bests and never shown as pressure.

### Hints

Opt-in only; the app never interrupts. Three rungs, and the last one still leaves the learner
to act:

1. *Binary Search starts with the middle of the active range.*
2. *Count the 7 active values and take the centre one.*
3. *The middle of the active range is 31.*

Every hint is recorded and costs a star. **The core solution is never behind an ad** — a
rewarded ad may later buy an *extra* hint, never the walkthrough.

### Metrics

Recorded per run: total decisions · correct decisions · mistakes · comparisons · swaps ·
passes · hints · elapsed time · outcome · challenge type · seed.

> **Swaps are reported, never scored.** How many swaps an array needs is decided by the input,
> not by the learner. Grading it would grade the data. Bubble Sort therefore scores on the
> **Accuracy** family — correct decisions, mistakes, hints — while Binary Search, where the
> learner genuinely controls the comparison count, scores on **Efficiency** (§7).

**None of it is prominent while the learner works** — the array is the hero, and the run is
summarised as one quiet line (`3 checks · 1 missed`). The full picture belongs on Result.

### Universal rules
- **No fail state.** The challenge always completes; only the score varies.
- **No countdown timer in MVP.**
- Two failed attempts auto-unlock the full walkthrough — free, no ad, no friction.

---
## 7. Result & Mastery — *V2, deferred*

> **Not in the MVP.** Stars, star families and mastery all belong to CHALLENGE
> (`docs/v2-challenge.md`). The MVP ends a lesson on **COMPLETE**, which reports the run and
> scores none of it — see §2. `Scorer`, `StarFamily` and `Verdict` remain in `:engine` for V2.

### Star formulas — three families

Stars rank **correctness, then independence, then efficiency**. Never speed.
★★★ = excellent independent application · ★★ = understood it, with some mistakes ·
★ = completed with significant support.
Never apply an efficiency formula to an algorithm whose cost the learner does not control.

| Family | Algorithms | ★★★ | ★★ | ★ |
|---|---|---|---|---|
| **Efficiency** | Linear, Binary, Two Pointers, Sliding Window | optimal comparisons, 0 wrong, 0 hints | ≤ optimal+2, ≤2 wrong, ≤1 hint | completed |
| **Accuracy** | Bubble, Selection, Insertion | 0 wrong, 0 hints | ≤2 wrong, or 1 hint | completed |
| **Scenario** | Stack or Queue | correct output, ≤ optimal ops | correct output | completed after retry |

### Mastery states
| State | Earned by |
|---|---|
| 🔒 Locked | prerequisite section not started |
| 👀 Seen | Watch completed |
| 🎮 Driven | Try completed — **gates Daily Challenge inclusion** |
| 🏆 Mastered | Challenge passed at **★★ or better** |
| 🌫 Rusty | Mastered, then 30 days without practice |

★★★ adds an **Optimal** tag, not a separate state.

**Rusty is additive, never subtractive.** It overlays the 🏆 badge with a haze. It never removes
it, never decrements the mastery count, and never notifies until ≥5 algorithms are mastered.

### The verdict line
Generated from run data, not a template pool. *"Two missteps, both on the same comparison in
pass 2"* is coaching. *"Great job!"* is noise. This single line is the difference between a
scoreboard and a tutor.

### Result screen buttons
- Primary: `Next: {algorithm} →` — **always instant, never gated by an ad**
- Secondary: `See the code` · `Try again`

---

## 8. Daily Challenge — *V2, deferred*

> **Not in the MVP.** Daily Challenge draws on algorithms at ≥ 🎮 Driven and on the challenge
> generator, so it follows CHALLENGE into V2 (`docs/v2-challenge.md`).

| | Lesson Challenge | Daily Challenge |
|---|---|---|
| Length | ~2 min, full run | 3 rounds × ~20 s |
| Shape | drive the whole algorithm | one decisive moment per round |
| Source | the current algorithm | **only algorithms at ≥ 🎮 Driven** |
| Scoring | ★ + mastery | daily score + personal best. **No stars.** |
| Data | per-attempt seed | **one global seed per date** — everyone gets the same puzzle |
| Failure | retry freely | round ends, next begins. Never blocks the streak. |

### Streak rules
- **Maintains:** one completed round. Partial credit protects the streak.
- **Full daily:** all three rounds → logs a daily score, updates personal best.
- **Freeze:** one earned free every 7 days + one via rewarded ad. Auto-applies; the user is
  told afterwards, never asked in the moment.
- **Locked until 3 algorithms at ≥ Driven.** Before that the Home slot shows `1 / 3` progress.

Daily result is shareable as an image (`Day 47 · 3/3 · 11.4s`). Same puzzle for everyone makes
it comparable between friends with zero social infrastructure.

---

## 9. Ads

> **One rule governs everything: ads fire on exit paths, never forward paths.**

### Absolute prohibitions
- No ad of any kind inside Watch, Try, Challenge, Daily Challenge, or Code Reveal.
- No ad in the entire first session.
- No ad until 3 lessons are complete.
- No interstitial on any tap that moves the user *deeper* into learning.
- No interstitial on a streak-milestone day (7 / 30 / 100 / 365).
- No banner on the Algorithm Hub.

### Banner
Anchored adaptive, bottom, **outside the scroll container**, height reserved at layout time.
Home · Journey · Progress · Settings only.

### Interstitial
| Fires on | Cap |
|---|---|
| Daily result → Home *(best slot in the app)* | 1 / day |
| Lesson Result → Home (back, **not** Next) | shared |
| Algorithm Hub → back to Journey | shared |

Global: ≥180 s between interstitials, ≤4 per session.
App-open: cold start only, 1 per 2 h, **suppressed when launched from a notification.**

### Rewarded — optional extras only
| Slot | Reward | Free alternative |
|---|---|---|
| Second hint | reveals the next move | 1st free; walkthrough free after 2 fails |
| Streak freeze | protects the streak | 1 free per 7 days |
| Immediate retry | retry now for a better star | free after 10-min cooldown |
| Practice pack | 5 extra generated problems | pure bonus |
| Bonus daily rounds | 3 extra, no streak effect | pure bonus |
| Ad-free hour | 60 min no banner/interstitial | auto-granted 24 h at a 7-day streak |

**Never behind an ad:** any lesson, any algorithm, solution walkthroughs, Code Reveal, mastery,
or progress. Every rewarded slot buys convenience or bonus volume — never comprehension.

### Compliance
- **Do not enrol in Designed for Families.** Play target age 13+.
- **UMP consent SDK in the first build.** GDPR/DMA.

---

## 10. Information architecture

Three tabs. Settings behind a gear in Progress. No search, no profile tab, no shop, no social.

| Tab | Job |
|---|---|
| **Home** | answers *"what do I do right now?"* with exactly one primary action |
| **Journey** | browse and choose — the full algorithm map by section |
| **Progress** | proof of work — mastery map, streak calendar, personal bests, achievements |

**Full-screen flows (bottom nav hidden, no ads):** Watch · Try · Complete *(MVP)* · Challenge ·
Code Reveal · Daily Challenge · Onboarding.

**Back-stack rule:** leaving a lesson mid-flow stores `{algorithm, phase, stepIndex}`. Home's
primary card becomes *"Continue: Insertion Sort · Try · step 7"*. A learner who is interrupted
must never restart.

### Home primary card — resolved in priority order
| # | Condition | Card |
|---|---|---|
| 1 | zero lessons complete | **Start here — Binary Search · 3 min** |
| 2 | a lesson is in progress | **Continue — {algo} · {phase} · {n}%** |
| 3 | daily unlocked and undone | **Today's Challenge — 3 rounds · ~3 min** |
| 4 | an algorithm is Rusty | **Refresh — Binary Search · 45 s** |
| 5 | all caught up | **Next up — Selection Sort · 4 min** |

Home never shows: two equal-weight CTAs · an ad above the fold · any ad in session 1 · a
greeting that changes nothing.

### Algorithm Hub
A spine, not a menu. Hero ambient loop → name → one-line promise → complexity chips →
four connected stage cards (Watch / Try / Challenge / Code) → Remember card → **one** primary
CTA reflecting the next step.

Gating: Try after Watch · Challenge after Try · Code after a passed Challenge.
**Escape hatch always present:** *"I already know this — skip to Challenge."* Passing cold
marks Watch and Try complete and awards mastery.

---

## 11. Code Reveal

Unlocked only after a passed Challenge. Entry transition: the array the learner just sorted
dissolves into the code that sorts it.

- Layout: data strip 30% / code 55% / controls 15%. Never full visualiser + full code in portrait.
- One scrubber drives both. Tapping a code line jumps the data to the first step that line executes.
- **The ◆ marker** sits on the line whose branch the learner was deciding in Try. On first open
  it animates a callout: *"This is the decision you were making."* This is the entire reason the
  screen exists — make it explicit, not subtle.
- Java and Python. Segmented toggle, remembered globally.
- **Copy is free.** No ad. No ads on this screen at all.

---

## 12. Journey & Progress

**Journey:** sections as horizontal chapter bands, not a winding bubble path. Locked cards state
their unlock condition in plain text. A visible **Coming soon** row ships in v1 — it tells the
user the app is not finished, which is what earns a return visit in month two.

**Progress:** mastery ring (`8 / 15`) · mastery map (state-coloured tile grid, designed to be
screenshotted) · streak calendar (freezes marked distinctly from completions) · personal bests
per algorithm · ~12 achievements, all skill-flavoured, **locked ones show their criteria in full**.

---

## 13. First five minutes

Zero ads. Zero accounts. Zero carousel. First interaction within 5 seconds.

| t | Beat |
|---|---|
| 0:00 | Cold launch, splash < 800 ms. No sign-in, no carousel, no permission prompts. |
| 0:02 | Sorted array staggers in. One line: **"Find 73."** |
| 0:05 | *"Tap the number you'd check first."* Every tap accepted — no wrong answer here. |
| 0:08 | *"Checking one at a time? That's up to 12 checks."* Fast linear scan. *"Watch this instead."* |
| 0:12 | Binary Search — Watch, full timing contract. |
| **0:28** | **Insight frame.** Six boxes collapse. *"One comparison. Half the array. Gone."* ← the install is decided here |
| 0:40 | 73 found. "3 comparisons instead of 10." |
| 0:45 | Try — new array, target 34, 3 guided decisions. |
| 1:20 | Complete. **Binary Search 🎮 Driven.** |
| **1:25** | **Only now:** notification permission, framed as streak reminders. After value, never before. |
| 1:35 | Home assembles. 🔥 1. One card: *Challenge — Binary Search · 2 min*. |
| 2:50 | Result ★★★. *"3 comparisons — optimal."* 🏆 Mastered · 1 / 9. |
| 3:05 | The Race. 32 comparisons against 6. *"That's what O(log n) means."* |
| 4:40 | Home. *"Come back tomorrow for your Daily Challenge."* |

---

## 14. Retention

| Horizon | Mechanism |
|---|---|
| Tomorrow | Daily notification at the chosen hour + a 20:00 streak-at-risk nudge |
| This week | Journey progression + home-screen widget (streak + today's puzzle) |
| Week 2–4 | Personal bests — the only mechanic that survives content exhaustion |
| Month 2 | 🌫 Rusty refresh at 30 days + first new-content drop (Grid renderer: BFS/DFS) |
| Month 3+ | Interview Mode (10 mixed rounds, weekly) + day-30/60 re-engagement push |

**Notification budget — hard cap:** 1 per day, exactly 3 types (daily reminder, streak-at-risk,
new content). Nothing else is ever added.

**New content is a retention feature.** One algorithm every two weeks with a push notification
is a legitimate retention engine for the first year.

---

## 15. MVP boundaries

### MUST HAVE — MVP
Step-trace engine + Sequence / Chain / Bucket renderers · **10 lessons** (Binary Search,
Bubble, Selection, Insertion, Merge, Quick, Stack, Queue, Linked List, Hash Map) ·
**Watch** as a user-paced walkthrough with one insight frame each · **Try** with the full
guidance ladder · **Complete**, reporting the run and scoring none of it · progress at
0 / 50 / 100 · local persistence, fully offline · light theme.

### DEFERRED TO V2 — was MUST HAVE
**Challenge in both formats · Result with three star families + verdict line · Daily Challenge
+ streak + freeze.** All three depend on the CHALLENGE stage and move together —
`docs/v2-challenge.md`. The engine machinery for them is built, tested and quarantined.

### NOT YET BUILT — still v1.0 scope
Code Reveal (display, Java/Python, static ◆) · 3-tab nav (Home is the only destination today) ·
AdMob banner + interstitial + rewarded + UMP · analytics · notifications · onboarding.

### SHOULD HAVE — v1.1
Light theme (fully contrast-validated) · home-screen widget · Rusty + spaced repetition ·
Google sign-in for streak backup · scrubber-synced code highlighting for all 9 · shareable daily
cards · Find the Mistake mode · **Grid renderer → BFS & DFS**.

### LATER — v1.2+
Interview Mode · tree/recursion renderer (Recursion, Merge Sort, Quick Sort) · Dijkstra (presets,
6–8 nodes) · dynamic programming (reuses the Grid renderer) · playground/sandbox · C++ and JS.

### EXPLICITLY NOT BUILDING
Leaderboards · friends and social · coins, gems, energy, lives · user-authored graphs ·
in-app code editor · video content · accounts in MVP · a standalone Big-O sandbox screen.

---

## 16. Open product decisions

1. **Stack + Queue merged** into one "Stack or Queue?" capstone, or two separate lessons?
   *Current assumption: merged.* Drops the count from 9 to 8; a buffer slot is reserved.
2. **Pass Prediction (Format B)** for the three sorts — accept or reject?
   *Current assumption: accepted.*
3. **Target geography** — changes eCPM assumptions and the animation performance budget.
   *Current assumption: India-weighted, budget devices, 3 GB RAM floor.*
4. **Dark-only for v1** — acceptable?
   *Current assumption: yes.*

These are assumptions, not decisions. Overrule any of them and the affected sections change.
