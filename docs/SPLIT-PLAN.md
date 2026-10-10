# Split plan: eleven files over the 800-line cap

Written 2026-10-10 against `123d68c`. Every line count in the tables below
the status is from that commit; re-measure before starting a step.

## Status (2026-10-10)

**Steps 0-4 are done**, each a commit with 1950 Kotlin tests green from fresh
XML (`b245394`, `f4d20f3`, `c36e90a`, `7192fb5`; step 0 is `9cc865d`,
`OVER_CAP` in `tools/test_tools.py`). Steps 5-8 have not been started.

| File | Was | Now | Moved to |
|---|---|---|---|
| `Content.kt` | 2106 | 56 | `ContentCharacter` 441, `ContentCombat` 345, `ContentRitual` 322, `ContentPathways` 263, `ContentDivination` 248, `ContentVitals` 227, `ContentSenses` 131, `ContentGear` 102 |
| `MainActivity.kt` | 2815 | 1235 | `SheetRows` 548, `RollDialog` 514, `FrightDialog` 310, `SelfControlDialog` 195, `ConcentrateDialog` 123 |
| `TraitEffects.kt` | 2610 | 2187 | `TraitRefusals` 455 |
| `DefenseDialog.kt` | 1460 | 597 | `DefensePickers` 437, `Parries` 193, `AcrobaticDodge` 173, `RoundState` 80 |
| `AttackDialog.kt` | 1311 | 581 | `AttackOptions` 538, `RangedOptions` 238 |
| `BuilderScreen.kt` | 949 | 440 | `BuilderRows` 336, `BuilderPickers` 216 |

Every move was verbatim except four kinds of edit, each in its commit body:
private declarations used across a new boundary became `internal`; the
three refusal tables' KDoc links were qualified with `TraitEffects.`;
`signed(n: Int)` kept one of its four byte-identical private copies; and
Defend's `Note` became `DefenseNote`, because PathwayScreen's `Note` pads
16 dp where Defend's pads 4.

Seven files are still over the cap: `test_tools.py` 5978,
`TraitEffectsTest.kt` 3080, `TraitEffects.kt` 2187, `MainActivity.kt` 1235,
`RitualDialog.kt` 1042, `extract_content.py` 938, `BeyonderSessionTest.kt` 863.

The point is read cost. An agent that needs one dialog reads 2815 lines of
`MainActivity.kt` to get it. Section 7's own rule is 800 lines a file.

| File | Lines | Largest declaration |
|---|---|---|
| `tools/test_tools.py` | 5941 | — |
| `core/rules/src/test/.../TraitEffectsTest.kt` | 3080 | — |
| `app/.../MainActivity.kt` | 2815 | `SheetScreen`, 1070 |
| `core/rules/.../TraitEffects.kt` | 2610 | `object TraitEffects`, 2527 (176 members, 117 public) |
| `core/rules/.../content/Content.kt` | 2106 | 112 classes, none over 75 |
| `app/.../DefenseDialog.kt` | 1460 | `DefenseDialog`, 579 |
| `app/.../AttackDialog.kt` | 1311 | `AttackDialog`, 463 |
| `app/.../RitualDialog.kt` | 1044 | `RitualDialog`, 933 |
| `app/.../BuilderScreen.kt` | 949 | `BuilderScreen`, 377 |
| `tools/extract_content.py` | 938 | — |
| `app/src/test/.../BeyonderSessionTest.kt` | 863 | — |

## Three kinds of split, and they are not the same risk

**A. A pure move.** A top-level declaration goes to a new file in the same
package. No call site changes; `private` becomes `internal` only where the
move separates a declaration from its caller. Proof: the old file's lines,
sorted, equal the new files' lines, sorted, apart from `package`, `import`
and the widened modifiers; and the test counts from fresh XML are the same
1950 Kotlin and 361 Python.

**B. A member leaves `TraitEffects`.** Kotlin cannot spread one `object` over
files. Private tables move to an `internal object` with no public change.
Public members move to a new object and every call site is rewritten by
`ast-grep run -p 'TraitEffects.x($$$A)' -r 'TraitClauses.x($$$A)' -U`, with no
facade left behind. Section 3's entry-point list changes in the same commit.

**C. A composable is cut up.** `SheetScreen`, `RitualDialog`, `DefenseDialog`
and `AttackDialog` are single functions. Cutting one moves state across a
composition boundary, which is where 6eg, 6ei and 6gb found their defects: a
handler belongs to the composition that drew it. Each is a slice of its own,
with a design review before the code and a device pass after.

## Order: most lines per unit of risk first

| # | Step | Kind | Before → after |
|---|---|---|---|
| 0 | A ratchet test: no tracked source file over 800 lines, save an allowlist naming the eleven. A step deletes its file from the list; the list never grows | new test | — |
| 1 | `Content.kt` → one file per chapter group (skills, combat, wounds, ritual, divination, fright, pathway, gear) | A | 2106 → 8 × ~260 |
| 2 | `MainActivity.kt`: every top-level declaration but `MainActivity`, `App` and `SheetScreen` leaves. `RollDialog`, `FrightDialog` + `FrightRecoveryPanel`, `SelfControlDialog` and `ConcentrateDialog` get a file each; the 20-odd row and result composables go to `SheetRows.kt` | A | 2815 → ~1300 |
| 3 | `TraitEffects.kt`: `PARTLY_APPLIED`, `UNAPPLIED`, `BARE_SOCIAL_ROLLS`, `CLOSE_QUARTERS_UNPRINTED`, `KNOCKDOWN_UNREACHED` (lines 2155-2580, all private data) to an `internal object` | B, no public change | 2610 → ~2180 |
| 4 | The same for the other three screens, leaving each file its one large composable. `DefenseDialog.kt`: pickers and controls to `DefensePickers.kt`, `RoundState`, the Acrobatic Dodge and the parry helpers to their own files. `AttackDialog.kt`: `AttackOptions`, `RangedOptions` and their helpers. `BuilderScreen.kt`: pickers and rows | A | 1460 → ~650, 1311 → ~550, 949 → ~450 |
| 5 | `TraitEffects.kt`: clauses (19 public members, 14 main call sites, 83 test), name parsing (5; 10; 57) and potion grants (5; 5; 21) to an object each | B | ~2180 → ~850 + 3 files |
| 6 | `TraitEffectsTest.kt` splits along the seams steps 3 and 5 made; `test_tools.py` and `extract_content.py` by extractor. Test names do not change, so the counts are the check | A | 3080, 5941, 938 → under cap |
| 7 | `SheetScreen`: the fourteen dialog call sites (lines 866-1264) and the three tabs become composables | C | 1070 → ~300 |
| 8 | `RitualDialog`, then `DefenseDialog`, then `AttackDialog` | C | one slice each |

Steps 1-4 touch no behaviour. After them the largest main file is
`TraitEffects.kt` at about 2180, where today five exceed 1300, and
`Content.kt` is gone. Stop there and measure before committing to 5-8.

## What this plan does not do

- **Comments.** Main Kotlin is 44% comment lines (14,054 of 31,976), and
  `TraitEffects.kt` is 1,479 of 2,611. Much of it is slice history that the
  slice log already holds. Trimming it is a separate decision: a refusal's
  reason in production source is what 6eq's sweep reads.
- **`docs/SLICE-LOG.md`** (1.17 MB). It is never loaded whole; grep it.
- **`BeyonderSessionTest.kt`** (863): over the cap, and no step above makes
  a seam for it. It stays on the allowlist until one does.

## Unverified

- The after-sizes are estimates from declaration spans, not from a trial move.
- Step 5's call-site counts are `TraitEffects.$M` matches outside the object;
  a member referenced by a callable reference (`TraitEffects::x`) was not
  counted.
- The eleven are tracked `.kt` and `.py` files only. JSON content, fixtures
  and Markdown were not measured against the cap.
