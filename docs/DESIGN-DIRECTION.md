# Design direction

Decided 2026-08-16, from `ecc:frontend-design-direction`. The point of writing it
down is that a direction decided at the END is one that every earlier choice
already guessed at. This file is what later slices defend against; it is not a
summary of what was built.

## The five answers

1. **Purpose.** Resolve rules mid-game: find a number, roll dice against it,
   apply the modifiers the book prices. Long-term the same consistency serves an
   AI narrating a story (see the simulator note in memory).
2. **Audience.** A player or GM at a table, phone in one hand, conversation
   continuing around them. Repeated daily use. They **scan**; they do not browse.
3. **Tone. Dense, quiet, scannable.** The skill is explicit that a tool for
   repeated use must not be given a landing-page composition. Occult atmosphere
   is carried by COLOUR and TYPE, never by spending layout on decoration.
4. **The memorable detail.** The `computed vs asPublished` disagreement. This app
   recomputes a character from the rulebook and shows where the printed sheet is
   wrong without repairing it (section 4.1). No other companion app does that. It
   is the thing worth designing around rather than hiding in a grey line.
5. **Constraints.** Compose Material 3 1.4.0, minSdk 26, offline, no photography
   or illustration for any character. **The palette is generated, not authored**
   — `tools/gen_theme.py` emits every Material role into `ThemeSchemes.kt`, and
   `Theme.kt` only chooses between the results.

## The palette is a choice, not a decision (2026-08-16)

The first palette was hand-picked and it audited clean: 54 of its 56
foreground/background pairs cleared WCAG AA in both modes. It was still
disliked, and nothing measurable said why. **A taste complaint that measurement
cannot locate is answered by handing over the choice**, which is what the
popular open-source Material 3 apps do — so there are now four presets, three
modes and a true-black option, reachable from the gallery's top bar.

Two things the audit *did* find, and both are now closed:

- The hand-written scheme set **33 of Material's ~48 colour roles**. The 15 it
  missed — `surfaceBright`, `surfaceDim`, the whole `*Fixed` family — fall back
  to baseline Material tokens, which are the stock purple-tinted neutrals. A
  scheme that never mentioned purple was serving it anyway. Pinned by
  `ThemeSchemesTest`, RED-proved by setting one role back to `#3B383E`.
- **The accents cannot follow the seed.** This app spends them semantically, so
  a normally-seeded scheme would render combat, Beyonder material and management
  as three shades of one colour. Each accent family is seeded separately at a
  fixed hue offset (+227 and +75 — the offsets the old palette already sat at,
  so the default preset preserves its relationships).

That constraint has a measured consequence worth keeping: with the error red
fixed, **only hues 65–110 and 195–265 keep all four categories ≥40° apart**.
The preset list is curated rather than a colour wheel *because of that number*,
and `gen_theme.py` refuses a seed that breaks it — it rejected its own first
draft of Abyss (indigo, hue 291) on exactly this rule.

## Rules this direction implies

- **Information per screenful is the metric.** A tile spending 60% of its height
  on a generated monogram fails, however handsome it looks.
- **Never explain the UI inside the UI.** "Tap a skill to roll it" is a control
  that should have been a button. If a sentence teaches an affordance, the
  affordance is missing.
- **Spend all three accents.** `primary` brass, `secondary` arcane and `tertiary`
  verdigris exist so categories can differ. A screen where everything is brass
  has a one-note palette and throws away the distinction it defined.
- **Icons are for repeated tool actions**, not decoration. Twelve actions used
  every session earn `material-icons-extended`; a single back arrow does not.
- **No decorative gradient fills.** Purple gradients are the named generated-AI
  tell. Colour belongs on what means something: a failed roll, a Beyonder, a
  disagreement with the book.
- **Dense tabular text stays legible.** Rules text is the product; type may not
  shrink to win space for chrome.

## Recorded failures of this direction

Kept because a rule with a scar on it gets followed and an abstract one does not.

| Shipped | Rule it broke |
|---|---|
| `COVER_GRADIENTS` in the first gallery | decorative gradients, two of eight literally purple |
| 3:4 monogram covers | information per screenful; the asset was filler |
| "Tap any skill or trait below to roll it." | never explain the UI inside the UI |
| Every accent rendered brass | spend all three accents |
| Twelve actions with no icons | icons are for repeated tool actions |
