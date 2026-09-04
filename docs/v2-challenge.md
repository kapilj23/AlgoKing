# Challenge — deferred to V2

**Status:** deferred, not cancelled · decided 2026-09-04 · supersedes the CHALLENGE
sections of `PRODUCT_SPEC.md` for the MVP only.

```
MVP:  WATCH → TRY
V2:   WATCH → TRY → CHALLENGE
```

---

## The decision

The MVP ships **WATCH → TRY**. An algorithm is complete once both are finished.

CHALLENGE is removed from the MVP UI entirely — no tab, no card, no route, and no
placeholder screen. It returns in V2, when there is time to design a challenge
system that is genuinely strong for each of the ten algorithms.

## Why

A challenge is not one feature; it is ten. Each algorithm needs its own generated
scenarios, its own constraints, its own hint ladder, its own answer validation and
its own edge cases — and each of those has to be authored, tuned and maintained
before the stage teaches anything. That work was making the MVP disproportionately
complicated relative to what it added, when WATCH → TRY already delivers the
product thesis: *the learner operates the algorithm rather than watching it.*

Shipping a weak challenge would be worse than shipping none. A challenge that can
be passed by guessing, or whose generated data is occasionally trivial, teaches the
learner that the assessment does not mean anything.

## What this changes

| | Before | MVP |
|---|---|---|
| Spine | WATCH → TRY → CHALLENGE → RESULT | WATCH → TRY → COMPLETE |
| `Stage` | `WATCH, TRY, CHALLENGE` | `WATCH, TRY` |
| Progress | 0 / 33 / 66 / 100 | **0 / 50 / 100** |
| Completion | 🏆 Mastered, at ★★ on a challenge | **Completed**, on finishing TRY |
| Stars | three families, on the Result screen | **none — TRY is never scored** |
| Hints | free rung, then a rewarded ad | none; TRY's guidance ladder does the job |

**There are no stars in the MVP.** `PRODUCT_SPEC.md` §2 has always said TRY is never
scored — it exists so a learner can be wrong as often as they like at no cost. A
grade on that run would quietly undo it, turning the guidance ladder into something
to avoid rather than something to use. Assessment is CHALLENGE's job, and it comes
back with CHALLENGE.

## What was kept, and where it lives

None of the challenge *architecture* was deleted. It compiles, it is covered by 50
engine tests, and it is quarantined off the MVP path so that V2 is a matter of
wiring a screen rather than rebuilding a system.

| Retained | Location | What it still does |
|---|---|---|
| `Challenge`, `ChallengeType`, `Difficulty`, `ChallengeRun` | `engine/challenge/` | the generated-challenge model, all ten types |
| `ChallengeGenerator` | `engine/challenge/` | seeded, trace-validated generation per algorithm |
| `ChallengePack`, `ChallengeCatalog` | `engine/challenge/` | **new** — the per-algorithm challenge wiring, split out of `LessonPack` |
| `HintPolicy`, `HintAccess` | `engine/challenge/` | first rung free, the rest behind a rewarded ad |
| `MissionRun`, `MissionHints` | `engine/challenge/` | the three-gate Binary Search challenge loop |
| `Mission`, `MissionCatalog` | `engine/scenario/` | Warehouse / Box Office / Server Log stories |
| `Scorer`, `StarFamily`, `Verdict` | `engine/scoring/` | the three star families and the generated verdict line |
| `SeededGenerator`, `Constraint`, `DatasetSpec` | `engine/dataset/` | constraint validation against the real trace |
| `Decision.minimalFeedback`, `hintLadder` | `engine/decision/` | per-algorithm challenge copy, already authored |

### The `LessonPack` / `ChallengePack` split

`challengeBrief`, `starFamily` and `challengeFactory` used to live on `LessonPack`,
which meant every MVP screen touching a lesson also carried a challenge factory and
a star family it never read. They now live on `ChallengePack`, keyed by
`AlgorithmId` through `ChallengeCatalog`.

That is the whole quarantine, and it is deliberate in both directions:

- `LessonPack` holds **only** what WATCH and TRY actually use, so nothing in `:app`
  can reach a challenge by accident;
- `ChallengeCatalog` holds the ten packs fully authored, so V2 does not re-derive
  which star family each algorithm scores on or which generator it draws from.

**Rule for V2 work:** if the MVP ever needs something out of `ChallengePack`, that
is a signal the thing belongs on `LessonPack` instead — move it rather than
importing the challenge package from a lesson screen.

## What was removed

Challenge-only UI, with no placeholder left behind:

- `ChallengeIntroScreen`, `MissionIntroScreen`, `MissionChallengeScreen`
- `ResultScreen` — replaced by `LessonCompleteScreen`, which reports the run and
  scores none of it
- `HintUnlockDialog` and `ads/RewardedAds` — the rewarded-hint seam existed only for
  challenge hints, and `LessonScreen` has always documented "No Hint in Try"
- `PracticeScreen` (the Practice/Challenge mockup recreation) and `ChallengeDots`
- `ArrayCellRow` and `InstructionText`, used only by that mockup
- `Phase.Challenge`, `Stage.CHALLENGE`, and the `Route.ChallengeIntro` /
  `Route.Challenge` / `Route.Result` destinations

## The upgrade path

`ProgressCodec` drops stage keys it does not recognise, so an install that recorded
`BINARY_SEARCH:CHALLENGE` before this change keeps its WATCH and TRY progress and
reads as 100 % complete rather than failing to load. There is a test for exactly
that: `LearningProgressTest.a stored CHALLENGE key from before the MVP cut is
dropped, not fatal`.

When CHALLENGE returns, adding `CHALLENGE` back to `Stage` restores thirds
automatically — the percentage is derived from `Stage.entries`, never stored
(ADR-028).

## What V2 has to decide

Deliberately not settled now, because the answers depend on design work that has
not happened:

1. Whether the challenge is one screen per algorithm shape, or the `MissionRun`
   three-gate loop generalised beyond Binary Search.
2. Whether stars return at all, or completion stays binary and the assessment
   surfaces as something else.
3. Whether Format B (Pass Prediction) ships for the three elementary sorts — it was
   specified in `docs/plans/01-bubble-sort-slice.md` and never built.
4. Whether missions extend past Binary Search, and what a "story" means for a sort.
