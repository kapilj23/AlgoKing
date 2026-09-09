# AlgoKing — Design System

**Status:** v2.0 · 2026-08-25 · **derived from `app/src/main/res/drawable/ref.png`**
**Source of truth:** the three approved screens in the reference image (Home · Bubble Sort Learning · Practice).
**Scope:** every visual decision. Product behaviour is in `PRODUCT_SPEC.md`; technical decisions in `ARCHITECTURE.md`.

> The v1 dark system is superseded and archived at `docs/DESIGN_SYSTEM_v1_dark_superseded.md`.
> The reference is a **light, bright, premium** system. Where v1 and the reference disagree, the reference wins.

> **⚠ MVP AMENDMENT — 2026-09-04.** CHALLENGE is **deferred to V2**
> (`docs/v2-challenge.md`), so the stage spine is **two nodes, not three**: `StageStepper`
> renders Watch · Try. §6.14, §6.15 and §7 below are amended in place. `ChallengeDots` and the
> Practice/Challenge screen composition are removed from the system; they return with V2.

---

## 0. The visual identity, read off the reference

**Bright, rounded, confident. A premium consumer learning product — not a developer tool.**

Five characteristics define it, and everything below is in service of them:

1. **Near-white ground, pure-white cards.** The page is `#FCFCFD` with a faint warm-pink glow in
   the top-left corner. Cards are pure `#FFFFFF` and float on it with an almost invisible shadow.
   The separation is *value*, not borders. Borders are hairlines used only where a card sits on a
   card.
2. **Generous corner radii everywhere.** Nothing in the reference has a sharp corner. Cards 20dp,
   buttons and icon tiles 16dp, array cells 10dp, chips and badges fully rounded.
3. **Colour is always a two-stop gradient, top-left to bottom-right.** Every filled surface that
   carries colour — icon tiles, primary buttons, array bars, progress arcs — is a diagonal gradient
   from a lighter tint to the saturated core. Flat fills are reserved for tiny elements (legend
   swatches, dots).
4. **Type is heavy and dark.** Headings are 800-weight in a near-black indigo `#0E1230`. Body is
   500-weight in `#3A4166`. There is no light-grey text carrying meaning.
5. **Ample vertical rhythm.** Cards are separated by 12dp, sections by 20–24dp, and every card
   carries 16dp of internal padding. Density is *low* — the reference never crowds.

### 0.1 The colour relationship — the most important rule

The reference does **not** use colour decoratively. It runs three independent colour jobs, and
they never bleed into each other:

| Job | Owner | Where it appears |
|---|---|---|
| **The app is violet.** | `primary` `#6D28F0` | Wordmark, active chip, active nav item, step chip, progress arc, primary buttons, the FAB, the "Explanation" heading, the dashed swap arc. If it is *chrome*, it is violet. |
| **The decision is violet vs orange.** | `primary` + `secondary` `#FB8B02` | Exactly one pair of buttons per learning screen — SWAP (violet) / KEEP (orange). Orange is the *alternative action*, never a warning. It is the only colour permitted to compete with violet at full strength. |
| **Each algorithm owns one accent hue.** | green / violet / orange / pink / blue | An algorithm's accent colours **three things and only three**: its icon tile gradient, its category badge, and its progress ring. Binary Search is green, Bubble Sort violet, Selection Sort orange, Insertion Sort pink, Merge Sort blue. |

And on top of those, one strictly semantic set for the algorithm canvas:

> **comparing = violet · next = orange · sorted = green · checked = lavender-grey.**
> These four are printed as a legend under every array in the reference, on *both* the Learning
> and the Practice screen. That legend is a contract: the same four colours mean the same four
> things in every algorithm, forever.

**Why violet appears in three of those jobs without collapsing:** it is separated by *saturation
and area*. Chrome violet is a large calm gradient; decision violet is a small saturated button;
algorithm-accent violet only ever appears inside a 56dp icon tile or a 44dp ring. The reference
never places two violets of different roles adjacent at the same size.

**Green is never a button.** In the reference green appears only as: a mastered ring, a completed
stepper node, a completed challenge dot, a "sorted" swatch, and the Binary Search accent. It is
*status*, never an action. Do not make a green CTA.

**There is no red in the reference.** An `error` token is defined below because the product needs
one (wrong-answer consequence, per `PRODUCT_SPEC.md` §5), and it is derived to sit inside this
palette — but it must stay as rare as the spec says. Red is not part of the visible language of
these three screens.

---

## 1. Colour tokens

All values sampled from the reference. **Nothing is hard-coded per screen** — every screen reads
these tokens (`ui/theme/Color.kt`).

### 1.1 Semantic core

| Token | Hex | Role |
|---|---|---|
| `primary` | `#6D28F0` | the app's violet — chrome, active state, primary action |
| `primaryLight` | `#8B4BF6` | gradient start, light half of the icon tile |
| `primaryDark` | `#5A1FD0` | pressed state, gradient end on buttons |
| `primarySoft` | `#EFEAFE` | violet-tinted ground: step chip, "Sorting" badge, info-icon fill |
| `primarySurface` | `#F2EFFE` | the *selected* algorithm card fill |
| `secondary` | `#FB8B02` | the alternative action — KEEP, "next" element, hint |
| `secondaryLight` | `#FEA802` | gradient start |
| `secondaryDark` | `#F27600` | pressed |
| `secondarySoft` | `#FFF1DC` | orange-tinted ground: streak chip, orange category badge |
| `accent` | `#2F7DFC` | blue accent — "Visualize.", Merge Sort, Undo |
| `accentLight` | `#6BA0FD` | gradient start |
| `accentSoft` | `#E7F0FF` | blue-tinted ground |
| `success` | `#2FB444` | mastered, sorted, completed — **status only, never an action** |
| `successLight` | `#62D05C` | gradient start, legend swatch |
| `successSoft` | `#E5F7E3` | green-tinted badge ground |
| `onSuccessSoft` | `#1E8A2B` | text on `successSoft` |
| `warning` | `#FB8B02` | **alias of `secondary`** — the reference has no separate warning hue |
| `error` | `#F0455C` | *derived, not in the reference.* Wrong-answer consequence only |
| `errorSoft` | `#FEE8EB` | error ground |
| `pink` | `#EA3C91` | fifth category accent (Insertion Sort) |
| `pinkLight` | `#F65AA6` | gradient start |
| `pinkSoft` | `#FDE7F2` | pink-tinted badge ground |
| `gold` | `#FBA90A` | the crown, the streak bolt, the hint bulb — brand ornament only |
| `goldSoft` | `#FDF4DF` | streak-chip ground |

### 1.2 Surfaces & neutrals

Neutrals carry a slight blue-violet bias (hue ≈ 245). **Never pure grey.**

| Token | Hex | Role |
|---|---|---|
| `background` | `#FCFCFD` | the page |
| `backgroundGlowWarm` | `#FDF3F9` | top-left corner glow (radial, fades out by ~55 % of width) |
| `backgroundGlowCool` | `#F5F2FE` | top-right corner glow |
| `surface` | `#FFFFFF` | all cards, the bottom nav, header icon buttons |
| `surfaceVariant` | `#F7F6FE` | card-inside-a-card ground: Explanation card, Challenge card |
| `surfaceMuted` | `#F1F1F7` | inert fill: locked tile, ring track |
| `border` | `#EDEDF5` | hairline, 1dp — only on `surfaceVariant` cards and the nav top edge |
| `borderStrong` | `#DDDDE9` | array-cell outline, unvisited stepper node |
| `textPrimary` | `#0E1230` | headings, card titles, numerals |
| `textSecondary` | `#3A4166` | body copy, explanations, legend labels |
| `textMuted` | `#6E769B` | captions, status labels, inactive nav labels |
| `disabled` | `#A6AABC` | lock glyph, chevron on a locked row |
| `disabledSurface` | `#EEEEF5` | locked tile ground |
| `brandInk` | `#151A4E` | the "Algo" half of the wordmark — deeper and bluer than `textPrimary` |

### 1.3 Algorithm visualization tokens

These are the legend. They are the same on Learning and Practice and must be the same in every
future algorithm.

| Token | Flat | Gradient (top → bottom) | Means |
|---|---|---|---|
| `comparing` | `#6D28F0` | `#B26AFA` → `#6E46EE` | the element under examination **right now** |
| `next` | `#FB8B02` | `#FEB102` → `#FD901F` | its partner — the other half of the comparison |
| `sorted` | `#2FB444` | `#62D05C` → `#2FAE3D` | finalised, in its final position |
| `checked` | `#DEDEF6` | `#E1E2FC` → `#CECFEA` | seen this pass, not yet final — the idle bar |
| `active` | `#6D28F0` | — | the focus ring / current index marker (same hue as comparing, used as a 2dp outline, never a fill) |
| `eliminated` | `#E8E8F0` @ 55 % | — | out of consideration — desaturated and collapsed |
| `pointer` | `#6D28F0` | — | the dashed swap arc, its arrowhead, and pointer marks |
| `indexLabel` | `#0E1230` | — | the small index numerals above the array |

**Array bars are gradients; array *cells* (the static row in Practice) are flat white with a
`borderStrong` outline.** That is how the reference distinguishes "the problem statement" from
"the live canvas".

---

## 2. Typography

**Family:** `Plus Jakarta Sans` — a geometric humanist sans with a single-storey `g`, a straight-tail
`y`, and a tall x-height. This is the face in the reference.
**Token:** `AlgoType.fontFamily`. Drop the `.ttf` files into `app/src/main/res/font/` and the whole
app follows; until then it falls back to the platform sans-serif. Never name a family anywhere else.

Weights used: **500 (Medium) · 600 (SemiBold) · 700 (Bold) · 800 (ExtraBold)**. There is no Regular
(400) anywhere in the reference and no Light.

| Style token | Size | Weight | Line height | Tracking | Used for |
|---|---|---|---|---|---|
| `displayLarge` | 26sp | 800 | 34sp | −0.4 | the two-line home hero |
| `headlineLarge` | 22sp | 800 | 28sp | −0.2 | "Compare 8 and 2" |
| `titleLarge` | 20sp | 800 | 26sp | −0.2 | screen title in the app header |
| `titleMedium` | 17sp | 700 | 22sp | −0.1 | algorithm card title, "Challenge 2 / 10", "Explanation" |
| `titleSmall` | 15sp | 700 | 20sp | 0 | Previous / Next, secondary button labels |
| `bodyLarge` | 15sp | 500 | 24sp | 0 | instruction copy, explanation body |
| `bodyMedium` | 13sp | 500 | 20sp | 0 | card description, legend labels, hero subtitle |
| `labelLarge` | 16sp | 800 | 20sp | +0.6 | SWAP · KEEP · Submit Answer |
| `labelMedium` | 13sp | 600 | 16sp | 0 | category chips, stepper labels, "Step 3 / 14" |
| `labelSmall` | 11sp | 600 | 14sp | +0.2 | category badges, metric labels, nav labels, "Mastered" |
| `numeralLarge` | 22sp | 800 | 26sp | −0.5 | array-bar and array-cell values |
| `numeralMedium` | 20sp | 800 | 24sp | −0.5 | metric values |
| `numeralSmall` | 14sp | 800 | 16sp | −0.3 | percentage inside a progress ring |

Rules:
- **Headings never sit above 800 or below 700.** The weight contrast between 800 headings and 500
  body is what gives the reference its crispness.
- **Numerals are always 800** and tabular where they change (metrics, percentages, step counters).
- Body copy is set with a **generous ~1.6 line height** and breaks into short lines (see the
  Explanation card: three lines, each a complete clause). Prefer explicit short lines over a
  justified paragraph.

---

## 3. Spacing

One scale. Every gap in every screen is a member of it.

| Token | Value |
|---|---|
| `Spacing.xxs` | 4dp |
| `Spacing.xs` | 8dp |
| `Spacing.sm` | 12dp |
| `Spacing.md` | 16dp |
| `Spacing.lg` | 20dp |
| `Spacing.xl` | 24dp |
| `Spacing.xxl` | 32dp |
| `Spacing.xxxl` | 40dp |

Applied constants (also tokens, so a screen never picks its own):

| Token | Value | Meaning |
|---|---|---|
| `Dimens.screenPadding` | 16dp | left/right gutter on every screen, all three |
| `Dimens.cardPadding` | 16dp | internal padding of every card |
| `Dimens.cardGap` | 12dp | vertical gap between sibling cards in a list |
| `Dimens.sectionGap` | 20dp | gap between distinct sections |
| `Dimens.headerHeight` | 56dp | app header row |
| `Dimens.bottomNavHeight` | 64dp | + system inset |
| `Dimens.minTouchTarget` | 48dp | every interactive element |

**The one vertical rhythm:** header → 20dp → hero/stepper → 20dp → content card → 12dp → next card.
That rhythm is identical on all three screens.

---

## 4. Shapes

| Token | Radius | Applied to |
|---|---|---|
| `Radius.swatch` | 4dp | legend swatch |
| `Radius.cell` | 10dp | array bar, array cell |
| `Radius.button` | 16dp | primary button, decision button, secondary button, header icon button |
| `Radius.icon` | 16dp | 56dp algorithm icon tile, 44dp header icon tile |
| `Radius.card` | 20dp | **every card** — algorithm, content, explanation, metric, challenge, instruction |
| `Radius.sheet` | 24dp | bottom nav top corners, bottom sheets |
| `Radius.pill` | 50 % | category chip, category badge, step chip, streak chip, progress dot |

**There is exactly one card radius.** If a rectangle reads as a card, it is 20dp. No exceptions —
this is the single most visible consistency rule across the three screens.

---

## 5. Elevation

The reference's shadows are extremely soft, low-opacity, and violet-tinted — never grey, never
Material's default black.

| Token | Spec | Applied to |
|---|---|---|
| `Elevation.card` | y 2dp · blur 12dp · `#6D28F0` @ 5 % | cards, chips, header icon buttons, bottom nav |
| `Elevation.raised` | y 4dp · blur 16dp · `#6D28F0` @ 8 % | secondary buttons, the array bar that is comparing |
| `Elevation.button` | y 6dp · blur 18dp · **the button's own hue** @ 28 % | primary / SWAP / KEEP / Submit — the shadow is tinted violet or orange to match the fill |
| `Elevation.floating` | y 8dp · blur 24dp · `#6D28F0` @ 32 % | the play/pause FAB |
| `Elevation.pressed` | y 1dp · blur 4dp · same hue @ 20 % + scale 0.97 | any pressed state |

**Pressed = drop, don't darken.** The shadow collapses and the element scales to 0.97; the fill
shifts to the `*Dark` token. It never gets a grey overlay.

---

## 6. Components

Every one of these is a single composable used by all three screens. If it appears twice, it is the
same component with different content.

### 6.1 App header — `AlgoHeader`
56dp tall, `screenPadding` gutters, transparent (sits on `background`).
Three slots: **leading icon button · centred title · trailing action**.
- Icon button: 44dp square, `surface` fill, `Radius.icon`, `Elevation.card`, 22dp glyph in
  `textPrimary`. Identical on all three screens (crown / back arrow).
- Title: `titleLarge`, `textPrimary`, optically centred.
- Home replaces the title with the **wordmark**: `AlgoKing` at 26sp/800 — `Algo` in `brandInk`,
  `King` in `primary`, with a 12dp gold crown tilted −12° over the `i`.
- Trailing: either a 44dp icon button (search / bookmark) or the **streak chip** — a pill,
  `goldSoft` fill, gold bolt + count in `titleSmall`/`textPrimary`, 44dp tall.

### 6.2 Bottom navigation — `AlgoBottomNav`
64dp, `surface`, `Radius.sheet` on the top corners only, 1dp `border` top hairline,
`Elevation.card`. Four items, evenly weighted.
- **Active:** 26dp *filled* glyph in `primary` + `labelSmall` label in `primary`.
- **Inactive:** 26dp *outlined* glyph (2dp stroke) in `textMuted` + label in `textMuted`.
- 4dp between glyph and label. No pill, no indicator, no badge.

### 6.3 Algorithm card — `AlgorithmCard`
Full width, ~100dp tall, `surface`, `Radius.card`, `Elevation.card`, `cardPadding`.
Row: **56dp icon tile** → 12dp → **text column (weight 1)** → **status column** → 12dp → chevron.
- Icon tile: `Radius.icon`, the algorithm's accent gradient (TL→BR), 28dp white glyph.
- Title `titleMedium` / description `bodyMedium` in `textSecondary`, 2 lines, badge below.
- Status column, right-aligned, 44dp wide: a `labelSmall` state word in `textMuted` above a
  **progress ring** or, when locked, a 36dp `disabledSurface` rounded square holding a
  `disabled` lock glyph.
- Chevron: 20dp, 2.5dp stroke, `disabled`.
- **Selected variant:** fill becomes `primarySurface`, shadow is removed, and the category badge
  switches to the solid-gradient treatment. Nothing else changes — not the radius, not the padding.

### 6.4 Category chip — `CategoryChip`
Pill, 34dp tall, 16dp horizontal padding, `labelMedium`.
- **Selected:** `primary` gradient fill, white label, `Elevation.card`.
- **Unselected:** `surface` fill, 1dp `border`, `textPrimary` label.
Row scrolls horizontally with 8dp gaps and `screenPadding` end-padding.

### 6.5 Status badge — `CategoryBadge`
Pill, 22dp tall, 10dp horizontal padding, `labelSmall`.
- **Tinted (default):** `{accent}Soft` fill, `{accent}` label.
- **Solid (on a selected card):** `{accent}` gradient fill, white label.
Never bordered.

### 6.6 Progress ring — `ProgressRing`
44dp, 4dp stroke, round caps, starts at 12 o'clock, sweeps clockwise.
Track `surfaceMuted`. Arc is a **sweep gradient** from the accent's light tint to its core, so a
full ring reads as a gradient loop. Centre: `numeralSmall` in `textPrimary`.

### 6.7 Primary button — `PrimaryButton`
Full width (or weighted), 56dp, `Radius.button`, `primary` gradient TL→BR, white `labelLarge`,
`Elevation.button` tinted violet. Optional 22dp leading glyph, 12dp gap.

### 6.8 Secondary button — `SecondaryButton`
Same 56dp / `Radius.button` geometry, `surface` fill, `Elevation.raised`, `titleSmall` label in
`textPrimary`, 20dp glyph which may carry its own accent (Restart `textSecondary`, Hint `gold`,
Undo `accent`). No border. Used for Previous / Next / Restart / Hint / Undo — **one component**.

### 6.9 Swap button & Keep button — `DecisionButton`
`PrimaryButton` geometry exactly, differing only in the gradient token and glyph:
- **SWAP** — `primary` gradient, shuffle glyph, label `SWAP`.
- **KEEP** — `secondary` gradient, arrow-right glyph, label `KEEP`.
They are always a 50/50 row with a 12dp gap and always the same height. **Identical visual weight**
— neither is styled as the recommended answer.

### 6.10 Metric card — `MetricRow`
Full width, 68dp, `surface`, `Radius.card`, `Elevation.card`. Three equal cells split by 1dp
`border` vertical rules inset 12dp top and bottom.
Each cell: `labelSmall` in `textMuted` above `numeralMedium` in `textPrimary`. Empty value = `--`.

### 6.11 Explanation card — `ExplanationCard`
Full width, `surfaceVariant` fill, 1dp `border`, `Radius.card`, `cardPadding`.
`titleMedium` heading in **`primary`**, then `bodyLarge` in `textSecondary` on short lines.
An optional **mascot slot** on the right, 88dp wide, bottom-aligned.

### 6.12 Array cell & array bar — `ArrayBar` / `ArrayCell`
- **`ArrayBar`** (live canvas): 42dp wide, height proportional to value over a 96dp minimum,
  `Radius.cell`, gradient from the viz token, value in `numeralLarge` centred 14dp from the bottom
  — white on comparing/next/sorted, `textPrimary` on checked. 12dp gaps. A comparing bar also
  carries `Elevation.raised`.
- **`ArrayCell`** (static problem statement): 44dp square, `surface`, `Radius.cell`, 1.5dp
  `borderStrong`, `numeralLarge` in `textPrimary`. No gradient, no shadow.

### 6.13a Scene legend — `SceneLegend`
Derived from the scene, not from the algorithm: it lists only the cell states actually on
screen, so Binary Search's eliminated halves and Bubble Sort's settled suffix are described by
the same words in the same colours.

| Cell state | Swatch | Reads |
|---|---|---|
| `COMPARING` | `comparing` | Comparing |
| `IDLE` | `primary` @ 35 % | In play |
| `ELIMINATED` | `#E8E8F0` | Eliminated |
| `CANDIDATE` | `next` (amber) | Smallest so far |
| `GHOST` | `primarySoft`, **no numeral** | Gap |
| `FINALIZED` | `sorted` | Final |

The renderer never receives an algorithm name, so this legend cannot fork per lesson.

### 6.13 Legend — `VizLegend`
A single centred row under every array. 14dp swatch at `Radius.swatch` + 8dp + `bodyMedium` label
in `textSecondary`, 20dp between entries. Always four entries, always in this order:
**Comparing · Next · Sorted · Checked.**

### 6.14 Stage stepper — `StageStepper`
**Exactly two nodes — Watch · Try — joined by a 2dp connector**, labels `labelMedium`
beneath. The component renders one node per `Phase`, so it is the spine and never a picture of
it: when CHALLENGE returns in V2 a third node appears with no change here. There is no
MASTER node — completion is a status on the Complete screen (`PRODUCT_SPEC.md` §2), not a
stage, so it must never appear in this component.
- **Complete:** 30dp `success` circle, white check. The connector leaving it is a **green → violet
  gradient** into the current node.
- **Current:** 30dp `primary` gradient circle, white numeral, label in `textPrimary` at 700.
- **Upcoming:** 30dp `surface` circle, 1.5dp `borderStrong`, numeral in `textSecondary`,
  connector `border`, label `textMuted`.

### 6.15 Challenge progress dots — `ChallengeDots` *(V2, removed)*
*Removed with the CHALLENGE stage — `docs/v2-challenge.md`. Restore when the stage returns:*
N nodes on a 2dp rail. Complete: 14dp filled `success` dot on a `success` rail.
Current: 18dp `surface` dot with a 3dp `success` outline. Upcoming: 12dp `#DBD6F9` dot on a
`primarySoft` rail.

### 6.16 Challenge card — `ChallengeCard` *(V2, not built)*
*The Practice screen it anchors is deferred — `docs/v2-challenge.md`.*
The Practice hero: full width, `surfaceVariant`, `Radius.card`, 20dp padding. Holds the
`titleMedium` "Challenge n / N" in **`primary`**, the dots, and then a nested **white
instruction card** (`surface`, `Radius.card`, 16dp padding) containing `bodyLarge` copy and the
`ArrayCell` row. This nesting — a white card inside a lavender card — is a signature of the
reference and is what `surfaceVariant` exists for.

### 6.16b Sequence renderer — `SequenceRenderer`
One renderer for every array/sequence algorithm. It is handed a `SequenceScene` from the
engine and cannot ask which algorithm produced it (`ARCHITECTURE.md` §7.1), so the visual
language can never fork per algorithm.

It enforces one reading order, top to bottom:
**TARGET → CURRENT SEARCH RANGE → MIDDLE → ELIMINATED RANGE.**

| Scene state | Treatment |
|---|---|
| `badge` | pill on `primarySoft`, `labelSmall` label + `numeralMedium` value in `primary`. The target is a badge, never a colour — spending a viz hue on it would break the legend contract. |
| `COMPARING` | `comparing` gradient fill, white numeral, `Elevation.raised` |
| in-region `IDLE` | `surface` fill, 1.5dp `primary` @ 35 % outline — "still possible" |
| `ELIMINATED` | flat `#E8E8F0`, `textMuted` numeral, **scaled to 0.82 and faded to 45 %** — the collapse is what makes "half the search space, gone" legible |
| `CANDIDATE` | `next` gradient, white numeral — a value the algorithm is *holding on to*, not one it is looking at |
| `GHOST` | `primarySoft` fill, violet outline, **and no number at all** — it is a hole in the array, so drawing a greyed value there would be a lie |
| `FINALIZED` | `sorted` gradient, white numeral |
| `pointers` | 20dp rails above (`mid`, with a caret) and below (`lo` / `hi`) the row, `labelSmall` |

Cells are 44dp tall and share the row width equally, so an array of up to 16 fits one line
without horizontal scroll.

**Each sort must look like itself.** The renderer is generic, but the scenes it is given are
not interchangeable, and that is deliberate:

| Algorithm | The picture |
|---|---|
| Bubble Sort | two neighbours highlighted, an arc between them, a green suffix growing from the right |
| Selection Sort | an amber remembered minimum, a violet cursor travelling right, a green prefix growing from the left |
| Insertion Sort | a key held outside the array in a badge, a **gap** walking left through the green prefix, and no arc at all |
| Merge Sort | the row visibly **divided into groups** by separators, a merged prefix filling one window, and the two front values under comparison |
| Quick Sort | one green pivot locked in place, everything else sorted into a smaller side and a larger side around it |
| Stack | a **vertical pile** of wide plates on a base line, newest on top, one live end |
| Queue | a horizontal line with **both** ends named, items joining at one and leaving from the other |
| Linked List | boxes joined by **arrows**, each node split into value and NEXT, bracketed by HEAD and NULL |
| Hash Map | a **table of buckets** under a flow strip that turns a key into a bucket index |

A learner who cannot read the copy should still be able to tell them apart. Stack and Queue in
particular are driven by the *same* engine class, so the picture is the only thing carrying the
difference — which is exactly why it has to carry it well (`DECISIONS.md` ADR-027).

**End caps.** When a scene carries `endCaps`, the leading and trailing labels flank the whole
stage in `labelSmall`/`textMuted` — `OUT ←` and `← IN` for the queue, `IN ↓   OUT ↑` and `BOTTOM`
above and below the stack — one live end against two, said out loud. The cells, the swap rail and the pointer rail then share one inner
column so they stay aligned under the caps. Scenes with no caps lay out exactly as before.

**Legend labels.** A scene may rename a cell state through `legendLabels`. `CANDIDATE` means
"the smallest so far" to a sort and "next out" to a structure, and the legend has to say
whichever is true without the renderer ever learning which lesson is running.

### 6.16c Vertical stage — the pile

A scene whose `orientation` is `VERTICAL` is drawn as a pile instead of a row. It is the same
`SceneCell` with the same state-to-treatment mapping; only the layout changes.

| Element | Treatment |
|---|---|
| plate | 132 × 40dp, 5dp apart — wide and short, so it reads as a *thing on a pile* rather than a number in a box |
| order | slot 0 is drawn **first, at the top**, because on a stack the top really is the top |
| pointer label | printed to the right of its plate in `primary`, not on a rail below |
| base | a 2.5dp `borderStrong` pill under the pile, so the bottom is somewhere real rather than the edge of the screen |
| empty | a 132 × 40dp outlined box reading `empty` — an empty structure is a state worth drawing, not a blank |
| reached-for-nothing | a `GHOST` plate, so "there was nothing there" is a picture and the caption only explains it |

### 6.16d Chain stage — the linked list

A scene whose `layout` is `CHAIN` is drawn as nodes joined by arrows. It is the only stage where
the *gaps* carry the meaning, and the design says so at every level.

| Element | Treatment |
|---|---|
| node | 30dp value compartment, a hairline, then a 14dp NEXT compartment holding a 6dp violet dot |
| the divider | non-negotiable — a node is a value **plus** a reference, and the arrow leaves from the reference |
| arrow | 22dp of real width, 2dp stroke with a head; `ACTIVE` and tappable arrows go violet and thicken to 3dp |
| a cut link | two dashed stubs with a visible gap, in `warning` — during an operation the chain genuinely is broken there |
| a new link | `sorted` green, so the arrow the learner just made is the thing that changed |
| detached node | a violet-outlined box hovering **above its gap**, so a new node exists before it is connected |
| HEAD | a `primary` label with a caret, never a box: it points, it does not hold a value |
| NULL | a muted label, never a box, for the same reason |
| tap targets | the **arrows**, labelled `tap` while selectable — the learner changes links, not boxes |

The row scrolls horizontally rather than shrinking: six nodes plus their arrows exceed a phone's
width, and squeezing the arrows to fit would shrink the one thing the lesson is about.

### 6.16e Bucket table — the hash map

The only stage that is not a sequence. Two things share it, and both are the lesson.

**The flow strip**, above the table: `GET 12 → HASH 12 % 5 → BUCKET ?`. Three chips on
`surfaceVariant`, separated by muted arrows, each a `labelSmall` caption over a `numeralMedium`
value. The bucket chip is `textMuted` and reads `?` until the answer is known, then turns
`sorted` green — because an answer already on screen is not a question.

**The table**, below it: five equal rows, always all five.

| Element | Treatment |
|---|---|
| bucket row | 52dp minimum, `cell` radius, 6dp apart; grows if the bucket holds a chain |
| bucket number | a 52dp gutter, `BUCKET` in `labelSmall` over the index in `numeralMedium`, then a hairline rule |
| the target bucket | `primarySoft` fill and a `comparing` outline — the one the key landed in |
| a shared bucket | a `next` amber outline, standing, whether or not anything is happening in it |
| a selectable bucket | a 2.5dp `primary` outline: the answer to "where does this key go?" is tapped in the table |
| empty | the word `empty` in `textMuted`, never a blank row — an empty bucket is addressable space, and that is the point |
| entry | a pill, `key → value`, outlined; `MATCHED` fills `sorted`, `SCANNING` fills `comparing` |
| a chain | each entry gets a leading `└` tick, so two keys sharing a bucket read as *living together* and never as an error |

**Values are names, keys are numbers.** `12 → Alice` can be misread in only one direction, and
that direction is the one the lesson cares about.

**Group separators.** When a scene carries `groups`, a 3dp `borderStrong` rule is drawn
between them, inset 8dp vertically. It is the only way the renderer expresses structure, and
it is what lets a divide-and-conquer algorithm share the one sequence renderer instead of
needing a tree view of its own.

**The swap arc.** When a scene carries an `Arc`, a dashed `pointer`-coloured hop is drawn from
one slot to the other with an arrowhead at the destination — the same language as the
reference's swap indicator. It appears only as the consequence of a decision, never as
decoration.

### 6.16f Two aligned arrays — `PrefixTable`

The third scene shape, and the second that is not a sequence (ADR-030, ADR-033). Prefix Sum has
**two arrays of different lengths**, and the lesson is the offset between them.

Both rows are laid out over the same `n + 1` slots, and the source row leaves slot 0 empty:

```
ARRAY          [ 2 ] [ 4 ] [ 3 ] [ 7 ] [ 1 ]
                 0     1     2     3     4

PREFIX   [ 0 ] [ 2 ] [ 6 ] [ 9 ] [16 ] [17 ]
           0     1     2     3     4     5
```

That puts `array[i]` directly above `prefix[i + 1]` — the cell it is the increment for — and
leaves `prefix[0]` alone at the left, which is the whole reason the prefix row is one longer.

| Element | Treatment |
|---|---|
| cell | the same `SceneCell` every other lesson draws — the weight goes **into** the cell, never around it, or it collapses to a narrow capsule with the numeral spilling out |
| row caption | `labelSmall` in `textMuted`, uppercase — two arrays must never read as one |
| index rail | `labelSmall` under each row, in its own slots |
| uncomputed entry | `GHOST` — a hole with no numeral. A `0` there would claim a value exists |
| the range | a `pointer`-coloured rail under the source cells it covers, captioned `sum 1..3` |
| equation strip | `surfaceVariant` card: operand over `labelSmall` caption, `numeralMedium` values, the result in `primary` |
| the result | reads `?` until it is known — an answer already on screen is not a question |

### 6.16g The graph — `GraphStage`

The fourth scene shape, and the first that is two-dimensional. Node positions arrive normalised
0..1 and scale to whatever width the card gives, so the scene never learns about dp.

| Element | Treatment |
|---|---|
| node | 48dp circle — clears the 48dp touch minimum. `numeralMedium` label |
| unvisited | `surface` fill, 1.5dp `borderStrong` outline, `textPrimary` label |
| current | `comparing` gradient, white label, scaled to 1.08 — in a 2-D picture, position alone does not say "you are here" |
| visited | `sorted` gradient, white label |
| tappable | 2.5dp `pointer` outline, the same affordance a selectable cell carries |
| edge, idle | 2dp `borderStrong` |
| edge, on the path | 3dp `pointer` — the call stack made visible, and the route a backtrack unwinds |
| edge, just taken | 4dp `comparing` |
| edge, backtracking | 3dp **dashed** `secondary` — a retreat is not progress, and it is the half of DFS learners lose |
| traversal strip | `primarySoft` card, `A  →  B  →  D` in `titleSmall`/`primary` |
| path strip (DFS) | `surfaceVariant` card, `A  ›  B  ›  D` in `textSecondary` |
| queue strip (BFS) | `surfaceVariant` card of `next`-amber cells flanked by `OUT ←` and `← IN` — the Queue lesson's own language (§6.16). An empty queue reads `empty`: it is the termination condition, not a blank |
| queued node | `CANDIDATE` amber — seen, waiting its turn. The frontier, and what makes level-order visible |

Both strips are read straight from engine state, so the display and the algorithm cannot
disagree. For DFS the path *is* the call stack; for BFS the traversal *is* the dequeue order —
not the enqueue order, which is a different sequence and the most common way a BFS visual lies.

**A tree is a graph, and a search is not a traversal.** The Binary Search Tree draws itself with
this same stage (ADR-036), and the two differences are the lesson:

| Element | Treatment |
|---|---|
| ruled out | `ELIMINATED` — flat `eliminated` fill, `textMuted` label, faded to 55 % and scaled to 0.88, with no "still in play" outline. Less collapse than a cell's 0.82, because a two-digit numeral in a circle stops being readable first |
| an edge into a ruled-out subtree | 2dp `border` — still drawn, because the structure did not change, and visibly no longer a route |
| the target | the `primarySoft` badge every search lesson uses, above the stage. A badge, never a hue |
| the first strip | captioned **Search path**, not Traversal: this walk deliberately never visits most of the tree |
| the second strip | **absent.** DFS and BFS show the structure driving them; a BST search is driven by the tree itself, and a line repeating the path under another name would be text pretending to be a data structure |
| node positions | derived from the tree — in-order across, depth down — never authored. Two nodes on one row are always at least two columns apart, so nothing overlaps and nothing has to shrink |

**A number the node knows about itself.** A node may carry a `caption` — the AVL lesson puts
each node's balance factor there — drawn as a `labelSmall` pill on `surface`, tucked at the
node's top-right corner so it reads as an annotation rather than as part of the value. It is
`textMuted` normally and `secondary` amber when `captionAlert` marks it as the thing that is
wrong. Every node carries one or none: showing the number only where it is broken would turn
"find the unbalanced node" into "find the node with a number next to it".

**A rotation is drawn as a change to links.** The two edges a rotation will re-hang go
`ACTIVE`, and the nodes keep their columns and change rows — which is the invariant that
makes the rotation legal, shown rather than said (ADR-037).

**Waiting is a state, and it is what tells three traversals apart.** The three tree-traversal
lessons draw the *same* tree with the *same* four states, and `CANDIDATE` — reached, on the
stack, not yet emitted — is the one that carries the difference (ADR-038):

| | what the amber does |
|---|---|
| **Postorder** | parents sit amber while their children turn green underneath them, then go green last — "children first, parent last" as a picture |
| **Preorder** | a node turns green the instant it is reached, so green grows *downward* ahead of the walk and amber barely appears |
| **Inorder** | both — and an amber node between a green left subtree and an untouched right one is exactly the beat being taught |

Same tokens, same tree, three rhythms. Nothing was added to the design system for any of
them, which is the strongest evidence the scene contract holds.

**An edge may carry a value.** Dijkstra is the first lesson where the *edges* mean something,
so a `GraphEdgeView` may carry a `label` — its weight — drawn at the edge's midpoint on a
`surface` pill with a hairline `border`, `labelSmall` in `textSecondary`. The edge being
relaxed right now takes `comparing` violet for both its pill outline and its numeral, so the
weight the arithmetic is about is the weight that is lit. It is a `Text`, never text drawn
into a `Canvas` (`ARCHITECTURE.md` §10.5).

**A node may carry a second line, and it goes *inside*.** Dijkstra's tentative distance —
`∞` until the node is reached — is drawn under the node's name within the 48dp circle:
`titleSmall` over `labelSmall`, white on a filled node, `textMuted` on an empty one. It is
deliberately not AVL's top-right `caption`. That placement works on a **tree**, where the
space above a node is empty by construction; on a **graph** it is where edges leave, and a
1:1 spike at 360dp put two of six distances straight through an edge. Inside the circle is
the only placement that cannot collide, because the node already owns that space.

**Fit a graph by moving nodes, not by shrinking them.** The same spike rejected the first
set of Dijkstra's node positions — two nodes 69dp apart leave 21dp of bare edge, and a weight
pill needs about 20 — and the fix was a different layout, not a smaller node. **48dp stays
48dp**; it is the touch minimum, and the graph is what gives way.

### 6.17 Mascot container — `MascotKing`
The purple blob king: body `#9957F8` with a soft inner highlight, gold crown `#FBA90A`, white
eyes (one winking), a magenta smile, blush, a gold sceptre, and three violet sparkles.
Sizes: 88dp inside the Explanation card, 120dp on result/empty states.
Rules: **never on the algorithm canvas**, never larger than 120dp, never more than one per screen,
always bottom-aligned in its slot. It is an ornament in the copy area, never part of the data.

### 6.18 Walkthrough controls — `StepDots` · `HeaderCountChip` · `ComparisonChip`
Watch is user-paced, so its controls are a progress readout and one primary button.

- **`HeaderCountChip`** — `primarySoft` pill in the header's trailing slot, `labelMedium` in
  `primary`, the same 44dp height as `IconTileButton` and `StreakChip` so header treatments
  line up across every screen.
- **`StepDots`** — 6dp dots, 10dp for the current one, 4dp gaps. Done: `primary` @ 35 %.
  Current: `primary`. Upcoming: `borderStrong`. Deliberately quiet.
- **`ComparisonChip`** — `primarySoft` pill holding `45 < 73`: the left operand in
  `comparing`, the operator in `textSecondary`, the target in `primary`, all `numeralMedium`.
  It appears on `COMPARE` steps and is the visible change that earns that step its place.

**Watch has no play, pause or speed control.** The bottom of a Watch screen is a full-width
`PrimaryButton` reading *Next*, or *Start Try* on the last step, with an optional `weight(1f)`
`SecondaryButton` *Back* beside it. Nothing else.

### 6.19 Floating control — `PlayPauseFab`
62dp violet-gradient circle, white 22dp pause/play glyph, `Elevation.floating`. Sits centred
between Previous and Next.

> **Not used in Watch.** It survives only on the Bubble Sort reference recreation, which
> reproduces the approved mockup literally. No learning stage may reintroduce it.

---

## 7. Screen composition

All three share: `background` + corner glow · 56dp header · 16dp gutters · 20dp to the first
section · 12dp between cards.

| | Home | Bubble Sort Learning | Practice |
|---|---|---|---|
| Header | crown · wordmark · search | back · "Bubble Sort" · bookmark | back · "Practice" · streak chip |
| Progress | — | `StageStepper` (3 stages) | `ChallengeDots` inside `ChallengeCard` |
| Hero | 2-line 28sp hero + subtitle | — | instruction card + `ArrayCell` row |
| Body | `CategoryChip` row → `AlgorithmCard` list | content card: step chip · question · `ArrayBar` row · `VizLegend` · SWAP/KEEP | `ArrayBar` row · `VizLegend` · `MetricRow` |
| Support | — | `ExplanationCard` + mascot | three `SecondaryButton`s |
| Footer | `AlgoBottomNav` | Previous · `PlayPauseFab` · Next | `PrimaryButton` "Submit Answer" |

### CTA hierarchy — identical logic on all three
1. **One** gradient-filled button per screen is the forward action
   (Submit Answer / the SWAP–KEEP pair, which is one decision presented as two equal halves).
2. Everything reversible or lateral is a white `SecondaryButton`.
3. Navigation is a bare icon button, never a filled one.
4. The forward action is always the lowest element on the screen.

### The stage spine is two nodes

`WATCH → TRY`, then the Complete screen. Both stages use the **same** header, stepper, card,
renderer, legend, decision buttons and transport row — only the content and the amount of
guidance change. That sameness is the point: the learner should not feel they have moved to a
different product between stages.

| | Watch | Try | *Challenge (V2)* |
|---|---|---|---|
| Who answers the decision | the walkthrough script | the learner, guided | *the learner, alone* |
| Guidance card | — | why-wrong, consequence | *—* |
| HUD | — | — | *`MetricRow` (comparisons · wrong · hints)* |
| Transport | *(Back) · **Next*** — no play control | Restart · Undo | *Restart · Hint · Undo* |
| Forward action | "Start Try" | "Finish lesson" | *"See results"* |

There is **no Hint control in Try**, and that is deliberate rather than an omission: Try teaches
on every miss through the guidance ladder, which says more than a hint would and arrives without
the learner having to admit defeat to ask for it. Hints belong to Challenge, and return with it.

### The Complete screen

The end of a lesson. It reports what the run *was* — decisions, comparisons, wrong turns, in a
`MetricRow` — states the one idea the lesson existed to leave behind, and **shows no stars**:
Try is never scored (`PRODUCT_SPEC.md` §2). Chrome is the same header and the same cards as
every other screen; the forward action is `Next algorithm`, with `Watch again` and `Try again`
as white `SecondaryButton`s beneath it.

### The learning screen must read COMPARE → DECISION → RESULT
The reference already encodes this as a top-to-bottom sequence, and it must stay in this order:

1. **COMPARE** — the question (`headlineLarge`), the two highlighted bars (violet + orange), and
   the dashed arc with index labels. Every other bar is `checked` grey.
2. **DECISION** — the SWAP/KEEP pair, directly beneath the array, of equal weight.
3. **RESULT** — the Explanation card, visually separated from the decision by a gap and a change of
   ground (`surfaceVariant`, not `surface`), so it reads as consequence rather than instruction.

---

## 8. Motion

Timing follows `PRODUCT_SPEC.md` §4 exactly. This system only specifies *how* it looks:

| Change | Duration | Curve |
|---|---|---|
| Viz colour change (idle → comparing) | 200 ms | `FastOutSlowIn` |
| Swap arc travel | 400 ms | `FastOutSlowIn` |
| Bar height change | 300 ms | `FastOutSlowIn` |
| Button press | 90 ms | `LinearOutSlowIn` — scale 0.97 + shadow to `Elevation.pressed` |
| Card enter (staggered list) | 260 ms, 40 ms stagger | fade + 8dp rise |
| Progress ring fill | 600 ms | `FastOutSlowIn` |

---

## 9. Theme

**v2 is a light theme.** It supersedes the v1 "dark theme only" MVP boundary in
`PRODUCT_SPEC.md` §15 — that line needs a product-owner amendment. A dark counterpart will invert
`background`/`surface`/text and *keep every hue token unchanged*; the viz semantics must survive
the inversion untouched.

---

## 10. Token locations

| Concern | File | Access |
|---|---|---|
| Colour | `ui/theme/Color.kt` | `AlgoColors.primary` |
| Gradients | `ui/theme/Color.kt` | `AlgoGradients.primary()` |
| Viz semantics | `ui/theme/Color.kt` | `AlgoViz.comparing` |
| Type | `ui/theme/Type.kt` | `AlgoType.titleMedium` |
| Spacing | `ui/theme/Dimens.kt` | `Spacing.md`, `Dimens.screenPadding` |
| Shape | `ui/theme/Dimens.kt` | `Radius.card` |
| Elevation | `ui/theme/Elevation.kt` | `Modifier.algoShadow(Elevation.card)` |

**A screen file may not contain a `Color(0x…)`, a raw `.dp` for padding, or a bare `.sp`.** Anything
a screen needs that is not in a token is a missing token.
