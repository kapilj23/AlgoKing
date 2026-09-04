# AlgoKing — Design System

**Status:** v1.0 · 2026-08-23
**Scope:** all visual and motion decisions. Product behaviour is in `PRODUCT_SPEC.md`;
technical decisions are in `ARCHITECTURE.md`.

---

## 0. The identity

> **An instrument you operate — not a course you take.**

The reference points are precision instruments: an oscilloscope, a mixing desk, a chess clock.
Dark, dense, legible, with one confident accent and colour used as *information* rather than
decoration. Warm enough to sit with for twenty minutes; never clinical, never cute.

**What we take from the products we studied, and what we refuse:**

| Product | Take | Refuse |
|---|---|---|
| Duolingo | one unambiguous next action; the streak as a habit spine | the bubble path, the mascot, the rounded-friendly typography, confetti |
| Brilliant | discovery through doing; short explanations after the visual | the pastel illustration language |
| Mimo | bite-sized completion states | XP as the primary currency |
| Khan Academy | educational credibility, structural clarity | the density and the whiteness |
| Algorithm visualizers | the animation itself | the giant grey Step button and the static text panel below it |

### The one rule everything else serves

**The data is the hero.** On any learning screen the algorithm occupies the majority of the
canvas, and every other element is subordinate: no app bar, no bottom nav, no ads, no branding.
If a UI element competes with the array for attention, it is wrong.

---

## 1. Two palettes that must never mix

This is the most important rule in the system.

The **brand palette** drives the interface. A separate, strictly semantic **data palette**
drives the algorithm. If the primary button and the "found it" state share a hue, the array
starts reading as UI and the visual language collapses.

### 1.1 Brand & interface — dark (primary theme)

| Token | Hex | Use |
|---|---|---|
| `brand` | `#8B72FF` | primary buttons, active nav, progress fill, brand moments |
| `brandPressed` | `#7A5FF0` | pressed state |
| `brandSoft` | `#8B72FF` @ 14 % | selected chip fill, callout ground |
| `ground` | `#0A0C12` | app background **and the visualiser canvas** |
| `surface` | `#12141C` | cards, sheets |
| `surfaceRaised` | `#191C26` | chips, decision buttons, elevated cards |
| `surfaceHigh` | `#222633` | pressed surfaces, dividers on cards |
| `line` | `#242938` | hairline borders (1dp) |
| `lineStrong` | `#333A4D` | emphasised borders, outlined buttons |
| `ink` | `#E8EAF2` | primary text |
| `inkMuted` | `#A8AEC2` | secondary text, narration support line |
| `inkFaint` | `#7C8398` | labels, captions, disabled |

Neutrals carry a slight blue-violet bias (`hue ≈ 230`) so they read as chosen rather than
inherited. **Never a pure grey, never pure black.**

### 1.2 Data semantics — identical meaning in all nine algorithms

| Token | Hex | Means — everywhere, always |
|---|---|---|
| `dataExamining` | `#F5A524` | under examination **right now** |
| `dataPointerA` | `#38BDF8` | pointer / cursor / left boundary (`lo`, `i`, `windowStart`) |
| `dataPointerB` | `#FB7185` | second pointer / right boundary (`hi`, `j`, `windowEnd`) |
| `dataFinal` | `#34D399` | finalized, sorted, correct, found |
| `dataEliminated` | `ink` @ 22 % | out of consideration — collapsed and desaturated |
| `dataConsequence` | `#F87171` | **the consequence of a wrong decision. Nothing else, ever.** |
| `dataIdle` | `surfaceRaised` + `line` border | untouched |

**The target is not a colour.** It is a badge above the array (`TARGET 73`) plus a 1.5dp dashed
outline on the matching cell. Spending a data colour on the target would break the contract —
and that contract, *amber always means "being examined"*, is what lets a learner carry
understanding from Binary Search into Two Pointers without being taught the visual language
twice.

`dataConsequence` red appears perhaps four times in an entire lesson. That scarcity is what
gives it weight.

### 1.3 Light theme — v1.1

Dark ships first and is the default (`PRODUCT_SPEC.md` §15). Light theme is deferred not because
it is unimportant but because all six data colours need independent contrast validation on a
light ground, and a half-validated light theme would silently break the semantic contract
everything else depends on.

The token structure is built for it from day one — the light values below are recorded now so
the v1.1 work is validation, not redesign.

| Token | Light |
|---|---|
| `brand` | `#6244F5` |
| `ground` / `surface` / `surfaceRaised` | `#F7F7FB` / `#FFFFFF` / `#F0F0F6` |
| `ink` / `inkMuted` / `inkFaint` | `#14161F` / `#454A5C` / `#767C91` |
| `dataExamining` | `#B06E00` |
| `dataPointerA` / `dataPointerB` | `#0284C7` / `#E11D48` |
| `dataFinal` | `#0E9F6E` |
| `dataConsequence` | `#DC2626` |

### 1.4 Contrast

All body text ≥ 4.5:1 against its surface; all data-state colours ≥ 3:1 against `ground` and
distinguishable from each other for the three common colour-vision deficiencies. **Colour is
never the sole carrier of meaning** — every cell state also carries a border weight, an icon, or
a pointer label. A deuteranopic learner can tell `dataPointerA` from `dataPointerB` because they
are labelled `lo` and `hi`, not because they are blue and pink.

---

## 2. Typography

**Two families, both bundled as variable fonts.** Three families were considered and cut: at
mobile sizes the third bought nothing and cost APK weight.

| Role | Family | Weights | Notes |
|---|---|---|---|
| Display & interface | **Archivo** (variable) | 500 / 600 / 700 | industrial grotesque — engineering-adjacent, and pointedly not the rounded friendly faces of the children's-education category |
| Data, metrics & code | **JetBrains Mono** | 400 / 500 / 700 | **`tabular-nums` everywhere, non-negotiable** |

> Proportional digits make array cells change width when the value changes, which reads as a
> rendering bug during animation. Every digit in this app is tabular.

Bundled, not downloadable: a downloadable-font fallback on a device without the provider would
degrade the one screen that has to look premium.

### Scale

| Token | Size / line | Family · weight · tracking | Use |
|---|---|---|---|
| `displayL` | 32 / 36 | Archivo 700 · −2.5 % | Result headline, insight-frame line |
| `displayM` | 24 / 28 | Archivo 700 · −2 % | screen titles, algorithm name |
| `titleL` | 20 / 26 | Archivo 600 · −1 % | card titles, stage names |
| `cellValue` | 22 / 24 | JetBrains Mono 500 · tabular | **array values** |
| `narration` | 17 / 24 | Archivo 500 · 0 | the narration strip — the app's teaching voice |
| `body` | 15 / 22 | Archivo 400 | explanations, Remember card |
| `label` | 13 / 18 | Archivo 500 | buttons, chips |
| `metric` | 15 / 18 | JetBrains Mono 500 · tabular | HUD counters |
| `caption` | 11 / 14 | JetBrains Mono 500 · +0.13 em · UPPER | eyebrows, metric captions, pointer labels |
| `code` | 14 / 22 | JetBrains Mono 400 | Code Reveal |

`caption` is the system's signature: uppercase mono at 11sp with wide tracking. It is the
"instrument panel" tell, and it is used only for labels — never for content.

---

## 3. Spacing, shape, elevation

| Token | Value |
|---|---|
| grid base | **4dp**, rhythm on 8dp |
| `space.xs / s / m / l / xl / xxl` | 4 / 8 / 12 / 16 / 24 / 32 dp |
| screen margin | 20dp |
| card padding | 20dp |
| min touch target | 48dp · **decision chips 64dp** |

| Shape | Radius |
|---|---|
| card | 16dp |
| button | 14dp |
| decision chip | 14dp |
| array cell | 10dp |
| pill / chip | full |

**Elevation: dark theme uses surface lightness, never shadow.** Shadows on a `#0A0C12` ground
produce mud. Light theme (v1.1) uses `y2 blur8 @6%`. Never both in the same theme.

**Cards are defined by their 1dp hairline border**, not by a fill difference — this keeps the
Home screen readable without stacking translucent greys.

---

## 4. The visualiser — the hero surface

```
┌──────────────────────────────────────────────┐
│ ✕   ▓▓▓▓▓▓▓░░░░░░░░░  4/11            1×     │  40dp  transport rail
├──────────────────────────────────────────────┤
│                                              │
│                                              │
│              TARGET  73                      │  badge — caption + cellValue
│                                              │
│    3   8  14  21  [29]  37  45  52  61  73   │  ← 55 % of the canvas
│    ·   ·   ·   ·   ▲    ·   ·   ·   ·   ·    │
│                   lo                         │  pointer rail, caption type
│                                              │
├──────────────────────────────────────────────┤
│  29 < 73                                     │  narration strip — 17sp
│  Everything left of 29 is too small.         │  max 2 lines, fixed height
├──────────────────────────────────────────────┤
│  COMPARISONS 2        ELIMINATED 4           │  metric rail — caption + metric
├──────────────────────────────────────────────┤
│                                              │
│      ┌───────────┐   ┌───────────┐           │  decision zone
│      │   SWAP    │   │   KEEP    │           │  64dp, equal weight
│      └───────────┘   └───────────┘           │
└──────────────────────────────────────────────┘
```

Fixed rules:

- **The narration strip has a fixed height (2 lines).** Text that grows and shrinks pushes the
  array up and down, which destroys the spatial memory the whole lesson depends on. Reserve the
  space; let it be empty.
- **The decision zone has a fixed height** for the same reason. It is empty during Watch.
- **The array is vertically centred in its region and never moves between phases.** Watch, Try
  and Challenge share one layout; only the strips below change.
- No app bar, no bottom nav, no ads, no branding on this screen.

### Cell states

| State | Fill | Border | Type | Extra |
|---|---|---|---|---|
| `IDLE` | `surfaceRaised` | 1dp `line` | `ink` | — |
| `EXAMINING` | `dataExamining` @ 16 % | 1.5dp `dataExamining` | `dataExamining` | — |
| `COMPARING` | `dataExamining` @ 24 % | 2dp `dataExamining` | `ink` | comparison glyph drawn between the pair |
| `CANDIDATE` | `surfaceRaised` | 1.5dp `dataPointerA` dashed | `ink` | min-so-far, best-so-far |
| `FINALIZED` | `dataFinal` @ 16 % | 1.5dp `dataFinal` | `dataFinal` | small lock tick |
| `ELIMINATED` | transparent | none | `ink` @ 22 % | scale 0.88, no motion afterwards |
| `GHOST` | dashed 1dp `line` | — | — | the vacated slot during a swap |

Pointers render **below** the array as a labelled caret (`▲ lo`), never as a coloured cell
border — a learner must be able to see that a cell is *being compared* and *is where `lo`
points* at the same time.

---

## 5. Components

| Component | Spec |
|---|---|
| `AlgoButton.Primary` | filled `brand`, full-width, 56dp, `label` type. **Exactly one per screen.** |
| `AlgoButton.Secondary` | 1dp `lineStrong` outline, transparent, 48dp |
| `AlgoButton.Tertiary` | text only, `inkMuted` |
| `DecisionChip` | 64dp min, `surfaceRaised`, 1dp `line`, `label` type. 2–4 across. **Mandatory equal weight in every state.** |
| `MetricTile` | `caption` label above `metric` value, tabular |
| `NarrationStrip` | fixed 2-line height, `narration` type, cross-fades on change |
| `StageSpine` | the Hub's four connected stage cards with a 2dp connector rail |
| `StarRow` | three stars, 180 ms stagger, one haptic per star |
| `MasteryBadge` | 🔒 / 👀 / 🎮 / 🏆 / 🌫 — icon + `caption` |
| `TransportBar` | prev · play/pause · next · scrubber · speed. 40dp, `inkMuted` |
| `AlgoCard` | 16dp radius, `surface`, 1dp `line`, 20dp padding |
| `BannerSlot` | **reserves its height at layout time**, so a late-loading ad can never shift content |

### The decision chip rule

`ActionOption` carries no correctness flag (`ARCHITECTURE.md` §6), so the renderer *cannot*
style the correct chip differently — not through colour, order, size, elevation, ripple,
content description, or test tag. Options are presented in the algorithm's declared order, which
is stable across attempts.

The idle nudge at 8 s is the sole exception, and it is a deliberate 8 % opacity glow on the
correct chip — the one place the system is allowed to lean.

---

## 6. Motion

> **Motion is information. If an animation does not encode a fact about the algorithm, it does
> not ship.** There is no decorative motion anywhere in this app.

### Durations

| Action | ms | Easing |
|---|---|---|
| state colour change | 150 | standard `(0.2, 0, 0, 1)` |
| pointer move | 200 | standard |
| examine / highlight | 250 | standard |
| swap | **400** | emphasised `(0.2, 0, 0, 1)` + opposing vertical arc |
| eliminate | **500** | collapse width → 0 with desaturate |
| insert / remove | 350 | emphasised |
| narration cross-fade | 200 | linear |
| screen transition | 300 | shared-axis |
| star reveal | 180 each | overshoot, staggered |

### The step timing contract

Encoded once, in `design/motion/StepTimeline.kt`. Reproduced from `PRODUCT_SPEC.md` §4 because
the numbers matter more here than anywhere else:

```
0     highlight involved cells                          200ms
200   ── HOLD 350 ──          the eye locates what changed
550   comparison glyph appears in place:  7 > 3 ?
750   ── HOLD 400 ──          THE ANTICIPATION BEAT
                              the viewer answers it silently, to themselves.
                              this is what converts watching into predicting.
1150  reveal + execute        swap 400 / eliminate 500
1650  ── HOLD 250 ──
1900  narration updates       AFTER the visual, never during
2200  idle, advance pointer
```

**Speed presets scale the holds only.** `MotionScale` multiplies hold durations by
`2.0 / 1.0 / 0.5`; motion durations are constant. The algorithm must always look like itself —
a 2× swap that takes 200 ms reads as a different algorithm than a 1× swap that takes 400 ms.
There is a unit test asserting this (`ARCHITECTURE.md` §11).

### Rules

- **Never animate two concepts simultaneously.** If a pointer moves and a value changes,
  sequence them.
- **Always hold after a state change.** The 350 ms beat is not padding; it is the mechanism by
  which a beginner keeps up.
- **Swaps arc.** One cell travels over, one under, by ±14dp. Two cells sliding through each
  other on the same axis is illegible.
- **Eliminations collapse, they do not just fade.** Width → 0 with a desaturate, so the learner
  sees the search space physically shrink. This is the Binary Search insight frame; it is worth
  the extra work.

### Reduced motion

`LocalReducedMotion` (from `Settings.Global.ANIMATOR_DURATION_SCALE` and the system a11y flag):
translation is replaced by a cross-fade in place, and **every duration and every hold is
preserved**. The lesson must teach identically with motion off — the pedagogy is in the timing,
not in the travel.

### Haptics

Sparingly, and only as confirmation: one tick on the Try handoff, one per star, one on a
consequence reveal. Never on a wrong answer — a punitive buzz contradicts
`PRODUCT_SPEC.md` §5.

---

## 7. Feedback language

| Situation | Treatment |
|---|---|
| Correct in Try | execute + one line of *why*, `dataFinal` accent on the affected cells. **No celebration** — briskness is the reward |
| Wrong, 1st attempt | chip shakes 8dp (2 cycles, 240 ms), `focus` cells pulse `dataExamining`, narration re-asks. **No red, no ✕, no sound** |
| Wrong, 2nd attempt | consequence per policy. Where executed: the diverged state renders normally, then a `dataConsequence` reveal card rises from the bottom |
| Wrong in Challenge | a small `✕` increments in the HUD. Nothing else. No interruption |
| Idle 8 s | 8 % glow on the correct chip |
| Idle 20 s | inline hint text in the narration strip. **Never a dialog** |
| Star earned | 180 ms overshoot + haptic. Third star adds a single `brand` bloom, 400 ms |
| Mastery achieved | badge scale-in from 0.8 with the mastery ring incrementing |

**There is no confetti, no mascot, no popup dialog, and no sound effect in this product.**

---

## 8. Screen-level composition

| Screen | Chrome | Ads | Notes |
|---|---|---|---|
| Home | status strip only (streak · mastery ring) | anchored banner, outside scroll | exactly one primary card, above the fold, never pushed by an ad |
| Journey | title + chapter bands | anchored banner | horizontal card rows per section; **not** a bubble path |
| Hub | back + overflow | **none** | the spine, one CTA, the Remember card |
| Watch / Try / Challenge | transport rail only | **none** | the hero surface, §4 |
| Result | none | interstitial on **exit** only | choreographed reveal, §7 |
| Code Reveal | ✕ + language toggle | **none** | 30 / 55 / 15 split |
| Daily | ⚡ accent chrome | interstitial after result → Home | visually distinct from lessons |
| Progress | title + gear | anchored banner | the mastery map is designed to be screenshotted |

---

## 9. Iconography

Outline, 2dp stroke, 24dp, rounded caps and joins — drawn as `ImageVector`, no icon font, no
raster. Material Symbols Rounded where a standard glyph exists; custom for the five that do not
(mastery badge, pointer caret, swap arc, window bracket, elimination collapse).

State badges are the only filled marks in the system.

**Emoji are used for exactly one thing:** the five mastery states (🔒 👀 🎮 🏆 🌫) and the streak
flame, because they are recognisable at 16dp and carry no localisation cost. Nowhere else.

---

## 10. Accessibility

- **Touch targets** ≥ 48dp; decision chips 64dp.
- **TalkBack:** the array is a single semantics container reading *"Array, 10 items, position 5
  of 10, value 29, being compared with target 73"*. Individual cells are not separately
  focusable — 14 focus stops per step is unusable. Each step announces a `liveRegion` update
  from the narration text.
- **Decision chips carry identical semantic roles.** A screen-reader user must not be able to
  infer the answer from the accessibility tree — this is asserted by a UI test.
- **Colour is never the sole carrier.** Every state has a border weight, a glyph, or a label.
- **Font scaling** honoured to 200 %. The visualiser reduces cell count before it reduces type
  size; the array wraps to two rows at extreme scales rather than shrinking below 16sp.
- **Reduced motion** fully supported (§6) with pedagogy preserved.
- No content relies on hearing. There is no audio in the product.

---

## 11. What this system deliberately does not have

Gradients on cards · glassmorphism · drop shadows in dark mode · a mascot · illustration ·
confetti · sound effects · XP counters · coins or gems · progress rings around avatars ·
bubble paths · onboarding carousels · modal dialogs during learning · any colour outside the
two palettes above.
