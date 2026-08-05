# Pathways — Agent Handover

Handover for a fresh agent (written for Claude; Claude auto-loads this file).
Everything below is verified against the tree at commit `cf14e7c`, not recalled.

**Legal:** GURPS is Steve Jackson Games' property; Lord of the Mysteries is
Cuttlefish That Loves Diving's. This is a personal/portfolio project. Not for
commercial use or redistribution.

---

## 1. What this is

An offline Android companion app for a Lord of the Mysteries TTRPG whose rules
are GURPS 4e-derived. **Not a new game** — the rules already exist in a
rulebook PDF and 23 published character sheets. This project builds a faithful
digital interface over them: validate, derive, roll, track.

The owner's framing, verbatim: *"i want the app to be true to the gurps goal,
but just the not user interface. we allready have everything we just are
making interface."*

Design authority is `docs/superpowers/specs/2026-07-25-pathways-companion-design.md`.
Read it before designing anything.

---

## 2. Current state

**M0 through M3 are complete and merged to `master`**, and M4's first slice
(CoR tracking) with them. The rest of M4 is next — see sections 6 and 6a.

There is an Android module (`app/`, Compose) with three screens: premade list
-> sheet (the `computed vs asPublished` diff, tap a skill to roll 3d6), the
pathway screen (potion grants, digestion tracker, Potion Consumption Roll),
and the builder (attributes against a live point budget).

| | |
|---|---|
| Tests | **625 Kotlin** (JUnit 5, incl. 61 in `app/`) + **135 Python** (pytest), all green |
| Open issues | **Zero.** `docs/KNOWN_ISSUES.md` is now an audit trail, not a backlog |
| Branch | everything on `master`; working tree clean |
| Toolchain | Kotlin 2.2.0, JVM toolchain 17, Gradle 9.3.1 (wrapper committed) |

### Commands

```bash
./gradlew test                    # all Kotlin tests, app module included
./gradlew :core:rules:test        # one module
py -m pytest tools/test_tools.py -q   # Python tooling tests (Windows: `py`, not `python`)
```

Note: on this machine `python` is not on PATH. Use the `py` launcher. Gradle's
`--console=plain -q` prints nothing on success; check the exit code.

---

## 3. Repo map

```
core/model/     pure data types, no logic       (kotlinx-serialization)
core/rules/     all computation + book content  (api-depends on :core:model)
app/            Compose UI: premade list, sheet, pathway, builder
fixtures/       23 character-sheet JSON fixtures (wired onto core:rules TEST classpath)
tools/          offline Python extractors (pypdf, openpyxl) + their tests
docs/           spec, plan, KNOWN_ISSUES
```

`core/model` — `Attr`, `Attributes`, `BeyonderState`, `Character`,
`ControllingAttribute`, `Dice`, `Difficulty`, `Modifier`, `PurchasedSkill`,
`RollResult`, `SecondaryDelta`, `SkillKey`, `Trait`, `Weapon`.

`core/rules` — `AttributeCosts`, `Derive`, `ModifierResolver`, `ParryBlock`,
`PointBudget`, `Conditions`, `Corruption`, `FrightCheck`, `Pools`, `PotionConsumption`,
`PotionDigestion`,
`Attacks`, `Damage`, `Ranged`, `Wounds`, `Premades`, `RitualResolution`,
`RandomSource`, `SecondaryCharacteristics`, `SheetComparison`, `SkillLadder`,
`SkillRoll`, `SuccessRoll`, plus `content/ContentLoader` reading forty JSON
files from `core/rules/src/main/resources/content/`: `skills.json` (158),
`advantages.json` (99), `disadvantages.json` (99), `thrust_swing.json` (30),
`pathways.json` (88), `acting_method.json` (5), `potion_consumption.json` (11),
`cor_gained.json` (6), `ritual_cor_thresholds.json` (4),
`pool_thresholds.json` (9), `fright_triggers.json` (5),
`fright_effects.json` (9), `ritual_weights.json` (3),
`ritual_failures.json` (4), `ritual_critical.json` (9), `defenses.json` (4),
`defense_modifiers.json` (5), `maneuvers.json` (14),
`extra_effort.json` (4), `attack_options.json` (2),
`unarmed_attacks.json` (10), `range_penalties.json` (7),
`ranged_modifiers.json` (9), `hit_locations.json` (9),
`wounding_multipliers.json` (8), `spirit_damage.json` (15),
`major_wound.json` (3), `shields.json` (4), `shield_rules.json` (3),
`divination_difficulty.json` (5), `divination_methods.json` (8),
`divination_modifiers.json` (8), `divination_clarity.json` (6),
`divination_support.json` (4), `ritual_preparation.json` (5),
`boon_granting.json` (1), `boon_modifiers.json` (8),
`non_standard_pathways.json` (17), `weapons.json` (9), `wealth.json` (7),
`damage_abbreviations.json` (8), `appearance.json` (8),
`languages.json` (12), `language_proficiency.json` (6),
`language_notes.json` (2).

### Public entry points

```kotlin
AttributeCosts.costOf(attr, level): Int
AttributeCosts.totalCost(a: Attributes): Int
Derive.of(c: Character): DerivedCharacter
Derive.controllingLevel(ca, attrs, secondaries, spi): Int
ModifierResolver.resolve(base, mods, target, ctx): Resolution
ParryBlock.parryFor(weapon, skillLevel): Int?
ParryBlock.unarmedParry(style, skillLevel): Int
ParryBlock.blockFor(shieldSkillLevel, shieldDb): Int
PointBudget.report(...): BudgetReport
SecondaryCharacteristics.of(a, d): Secondaries
SkillLadder.levelFor(points, difficulty, attributeLevel): Int
SkillLadder.defaultFor(difficulty, attributeLevel): Int?
SuccessRoll.against(target, source: RandomSource): RollResult
ContentLoader.skills() / .advantages() / .disadvantages() / .thrustSwing()
ContentLoader.pathways() / .actingMethod() / .potionConsumption()
ContentLoader.corGained() / .ritualCorThresholds() / .poolThresholds()
ContentLoader.frightTriggers() / .frightEffects()
ContentLoader.ritualWeights() / .ritualFailures() / .ritualCriticals()
ContentLoader.defenses() / .defenseModifiers()
ContentLoader.maneuvers() / .extraEffort() / .attackOptions()
ContentLoader.unarmedAttacks() / .rangePenalties() / .rangedModifiers()
PotionDigestion.digest(c, block): DigestedCharacter
PotionDigestion.digestThrough(c, pathway, sequence, blocks): DigestedCharacter
PotionDigestion.of(c, blocks): DigestedCharacter?     // from c.beyonder
PotionConsumption.attempt(state, source): PcrOutcome
PotionConsumption.targetFor(digestion): Int
Corruption.maxFor(c) / .maxFor(will, traits): Int          // Will, +3 with Spiritual Fortitude
Corruption.clamp(cor, max) / .isLostAt(cor, max)
Corruption.ritualPenalty(cor) / .ritualThresholdsReached(cor)
Pools.hp(current, max) / .fp(current, max) / .spi(current, max): PoolStatus
Pools.thresholds(pool): List<PoolThreshold>   // empty for FP: prose only
Conditions.forSkill(skill, hp, fp, spi, cor): List<Modifier>   // scoped per skill
Attacks.modifiers(plan, skillLevel): List<Modifier>        // maneuver + options
Attacks.attackCount(plan) / .fpCost(plan) / .maxDeceptive(skillLevel)
Attacks.damageBonus(plan, fromSkill): Int                  // Strong + Boxing
Damage.forAttack(attack, st, bonus): DamageRoll?           // null = Shove
Damage.thrust(st) / .swing(st) / .attacksFor(skill) / .boxingBonus(dx, level)
Ranged.bandFor(meters): RangePenalty / .penaltyFor(meters): Int
Ranged.modifiers(band, conditions, gmValue): List<Modifier>
Wounds.locationNamed(target): HitLocation / .locations() / .damageTypes()
Wounds.multiplierFor(location, type): Double   // both tables, composed
Wounds.typeMultiplier(type) / .modifier(location) / .render(multiplier)
Wounds.majorWoundThreshold(maxHp) / .isMajorWound(damage, maxHp)
Wounds.majorWoundFor(roll): MajorWound / .majorWounds()
ContentLoader.hitLocations() / .woundingMultipliers() / .spiritDamage()
ContentLoader.majorWound() / .shields() / .shieldRules()
Shields.all() / .rules() / .named(type): Shield
Shields.withBonus(defense, shield) / .blockFor(shield, skill, isRanged)
Shields.blockPenalty(shield, isRanged): Int
Divination.targetFor(skill, difficulty, extras) / .attempt(...): DivinationOutcome
Divination.clarityFor(roll) / .modifierTotal(rows, gmValue, repeats)
Divination.supportBonus(skills) / .spiFor(method, chosen, burned)
Divination.difficulties() / .methods() / .modifiers() / .clarities()
SkillRoll.at(label, base, key, source, mods, ctx): SkillRollOutcome
FrightCheck.against(will, modifier, source): FrightOutcome   // two rolls
FrightCheck.effectFor(roll) / .text(effect, kind) / .triggers()
RitualResolution.attempt(skill, weight, cor, source): RitualOutcome
RitualResolution.targetFor(skill, weight, cor, preparation) / .criticalFor(roll)
RitualResolution.weights() / .preparations() / .preparationNamed(quality)
RitualResolution.spiFor(weight, chosen, burned): Int
Boons.granting() / .modifiers() / .groups() / .weight(): RitualWeight
Boons.penaltyForSequence(sequence): Int      // doubling, 9=0 down to 0=-512
Boons.modifierTotal(chosen, gmValue) / .spiFor(chosen, burned)
Boons.targetFor(skill, cor, sequence, chosen, gmValue) / .attempt(...)
NonStandardPathways.all() / .pathways() / .blocksFor(pathway)
NonStandardPathways.blockFor(pathway, sequence): SequenceBlock?  // null = no such rung
NonStandardPathways.knownSequences(pathway) / .alsoPrintedAs(pathway)
Weapons.all() / .named(name) / .forSkill(key): List<CataloguedWeapon>
Weapons.damageFor(weapon, mode, st, bonus): DamageRoll
Weapons.isRanged(weapon) / .canParry(weapon) / .parryFor(weapon, skill): Int?
Weapons.hasWoundingMultiplier(mode)   // true for pi+ since Appendix A (6v)
Wounds.knowsType(type): Boolean       // ask before multiplierFor
Wounds.knownAbbreviations()           // chapter 5's four, plus the appendix's
ContentLoader.damageAbbreviations()   // Appendix A's Wounding line
ContentLoader.weapons()
Wealth.all() / .named(status): WealthLevel        // throws for an unprinted level
Wealth.traitFor(level): Trait?                    // null at 0 pts (Average)
Wealth.levelOf(traits) / .applied(traits, level)  // replaces, never adds
ContentLoader.wealth()
Appearance.all() / .named(level): AppearanceLevel  // throws for an unprinted level
Appearance.traitFor(level): Trait                 // never null; no level is free
Appearance.levelOf(traits) / .applied(traits, level) / .without(traits)
ContentLoader.appearance()
Languages.all() / .common() / .mystical(): List<LanguageEntry>
Languages.proficiencies(type) / .proficiency(type, name)   // type is REQUIRED
Languages.ritualModifier(prof?): Int    // throws for Broken and for common
Languages.canPerformRituals(prof) / .ritualProficiencies() / .notes()
ContentLoader.languages() / .languageProficiencies() / .languageNotes()
RitualResolution.targetFor(skill, weight, cor, preparation, language)
```

`RandomSource` is a `fun interface` — inject a deterministic one in tests, a
real RNG in the app. Never call `Random` directly in rules code.

---

## 4. Rules that are not negotiable

These were learned the hard way. Violating one silently corrupts output.

### 4.1 The book is the authority; the sheets are fixtures

The 23 sheets are a **partial oracle**, not ground truth. Where a sheet
disagrees with the rulebook, **the book wins** and the disagreement is
*recorded*, never "repaired". `GoldenSheetTest` encodes this with named
exclusion sets:

```kotlin
private val hpExclusions = setOf("Docks Bouncer")
private val fpExclusions = setOf("Docks Bouncer", "Factory Foreman", "Street Acrobat")
private val advantagesExclusions = setOf("Monastery Scholar")
private val disadvantagesExclusions = setOf("University Librarian")
```

Monastery Scholar prints "Advantages: 15 pts" but lists one 5pt advantage.
University Librarian prints "Disadvantages: 30 pts"; its four items sum to 25.
Both are genuine sheet arithmetic errors — neither column reconciles.

### 4.2 Some tests assert that the source data is WRONG

Do not "fix" these into passing. They exist to make silent repair impossible:

- `the excluded sheets really do disagree - guard against silent repair`
- `the excluded advantage and disadvantage sheets really do disagree - guard against silent repair`
- `most sheets floor Basic Speed and the book does not` — asserts `offenders >= 19`

If one of these starts failing, the *data* changed, and that is the finding.

### 4.3 `Math.floorDiv`, never `/`

Kotlin's `/` truncates toward zero; the rulebook floors. They differ only for
negatives, so tests on positive values pass under both. This defect class
recurred **three separate times** in this codebase. Every division in rules
code uses `Math.floorDiv`, and `ParryBlockTest` has a negative-quarters
characterization test pinning it. Ceiling division is `-Math.floorDiv(-a, b)`.

### 4.4 `ControllingAttribute` is not `Attr`

The book's skill list contains `SPI/Hard`, `SPI/Average`, `Per/Average`,
`Will/*`. SPI can never join `Attr`: it starts at 0 and "cannot be raised by
points". Hence:

```kotlin
sealed interface ControllingAttribute
data class Buyable(val attr: Attr) : ControllingAttribute
data object Will : ControllingAttribute
data object Per  : ControllingAttribute
data object Spi  : ControllingAttribute
```

Resolve through `Derive.controllingLevel`, never by substituting IQ.

### 4.5 `tools/extract_sheets.py` — the Roll/Cost checksum

Two sheets (Fortune Teller, Street Acrobat) have real trait costs typed into
the spreadsheet's `Roll` column, not `Cost`. `traits()` sums both columns and
prefers whichever reconciles with the block's own printed POINT BREAKDOWN
subtotal, falling back to `Cost`. It prints a notice naming any sheet where
this changes the answer, so the disambiguation is never silent.

An earlier "fix" made the parser deterministic but picked the semantically
wrong column, and a reviewer confirmed it matched the raw cells — both were
right about the mechanism and wrong about the meaning. **Reconcile against the
printed subtotal, not against the cell you happen to be reading.**

### 4.6 `tools/coverage.py` uses global line numbering

The module docstring defines the `sourceLines` contract. It compares line
*sets*, not counts — comparing counts can report "fully covered" while missing
lines. Do not change the numbering scheme.

---

## 5. `ModifierResolver` — the highest-risk file

Read its KDoc in full before touching it. Two structural facts:

**Two-pass design.** `resolve()` runs `runLayers` twice. The *shadow pass*
(`cancelBudget = null`) measures each `SourceKind`'s penalty contribution with
`Cancel` disabled; the *real pass* depletes a mutable copy of that ledger. This
makes `Cancel` correct regardless of layer order.

**`Scale` is never ledgered.** Its delta (`scaled - before`) depends on the
running value, which differs between passes whenever a `Scale` sits downstream
of a `Cancel`. Feeding it into the ledger let pure penalties net to a *bonus*
(measured: base -4 landing on +2). The documented trade-off: **a
Scale-delivered penalty is not cancellable.** `ModEffect.Cancel`'s KDoc says
so; three `ModifierResolverTest` cases pin it.

**Other invariants pinned by tests:**
- `group == null` means "no relationship declared", not "one shared pool" — an
  ungrouped `SetTo` must not erase an unrelated ungrouped `Flat`.
- Ungrouped floor-mode `Scale`s compose by product and floor **once**.
- A modifier that is inactive, off-target, or expired **cannot supersede** the
  one it names.
- Mixed-`SourceKind` group attribution splits the applied total by prefix
  differences so the parts sum to exactly what was applied.
- `Duration` is enforced only when `Modifier.startTurn != null`. `Turns(n)`
  expires at n turns; `Minutes(n)` at n*60 (one turn = one second);
  `Permanent`/`Scene` never expire on a turn count.

`ModifierResolver` has **zero production callers today**. M2 is where that
changes.

---

## 6. What's next: M4 — table play

M3 is complete. Spec section 8's M3 line — *"T1 extracted; character builder
with live budget validation; potion consumption, sequence advancement,
digestion tracker"* — is all in `master` and device-verified on an emulator.

| M3 piece | Where it lives |
|---|---|
| T1 extraction | `tools/extract_pathways.py` -> `content/pathways.json`, 88 blocks |
| Ch. 6 digestion tables | `tools/extract_digestion.py` -> `acting_method.json`, `potion_consumption.json` |
| Potion effects on a character | `PotionDigestion.digest` / `.digestThrough` / `.of` |
| Potion Consumption Roll | `PotionConsumption.attempt` |
| Stored Beyonder state | `BeyonderState(pathway, sequence, digestion)` on `Character.beyonder` |
| Screens | `app/`: `PathwayScreen` (grants, tracker, PCR), `BuilderScreen` (live budget) |

### What M3 established that M4 must not undo

- **`Character` stays purchased-only.** Potion gains are free (p. 85). They
  are never written into `attributes`/`secondaryDelta`/`skills`, because
  `PointBudget` bills those. Only the CHOICE is stored — pathway, sequence,
  digestion — and every gain re-derives from it. Writing grants into billable
  fields overcharges silently, and the totals still look plausible.
- **Sequences are cumulative.** A Sequence 8 Fool drank the Sequence 9 potion
  first. `digestThrough` folds the chain; digesting one block alone answers a
  different question.
- **Granted levels are frozen at digestion; purchased skills re-level.** A
  potion that raises DX lifts every bought DX skill (level = attribute +
  rungs) but not a level a previous potion granted, which has no points
  behind it to recompute from. The book does not settle this — it is a
  documented reading.
- **The PCR overrides the general critical rules.** `SuccessRoll` applies
  GURPS crits (3-4 always succeed, 17-18 always fail); the PCR table's own
  "1 — always fails" / "19 — always succeeds" contradict that at both ends.
  Specific beats general. `PcrOutcome.integrated` compares the raw total.
- **A pathway is walked one potion at a time.** `canDrink` admits only the
  first potion for a mortal, or the next Sequence of the pathway already
  walked. Without it a mortal could store Sequence 6 and be granted the whole
  chain.

### Recorded disagreements (never repair these)

- Five skills carry a different difficulty in the potion tables than in the
  chapter-3 list: Herb Lore, Herbal Medicine, Jeweler, Knife, Leatherworking.
  Pinned by `PathwayContentTest` and `extract_pathways.DIFFICULTY_DISAGREEMENTS`.
- Three grants name no real skill and are recorded, not guessed:
  Reader's player-choice placeholder, Savant's Wildcard-difficulty
  `Inventor!`, Potions Professor's `Poison`.

### Known gaps left deliberately

- **Traits and weapons are not editable yet.** `SavedEdits` covers the four
  attributes and bought skills; advantages, disadvantages and weapons still
  come only from the published sheet. Weapons are what `ParryBlock` is waiting
  on.
- **Advantage and drawback rows are prose, not structure.** The flattened PDF
  gives no boundary between a wrapped effect line and the next row's name, and
  this PDF reports x=0 for every glyph so column geometry is unavailable. See
  `tools/extract_pathways.py`'s docstring before trying again.
- **The screens themselves are still device-only.** `app/src/test` now covers
  `BeyonderSession` and `SessionStore` (22 tests), which is where both
  app-level defects have landed so far. Composables are not tested; that
  needs `androidx.compose.ui.test` and a reason better than coverage.

---

## 6a. CoR — the first slice of M4

`Corruption` (core/rules) is the tracker, `SavedSession.cor` the total, and
`PathwayScreen`'s stepper the interface. Four things it settles:

- **Max CoR is Will**, +3 with the `Spiritual Fortitude` advantage (p. 18,
  p. 45). `Corruption.maxFor` takes either a `Character` or an
  already-derived Will, because a Beyonder's Will is the digested one.
- **The loss is latched, never derived.** `SavedSession.corrupted` is stored.
  Recomputing `cor >= max` would resurrect a lost character the moment a
  potion raised Will — and would miss the reverse, which is real: the Abyss
  pathway LOWERS Will at Sequences 9, 8 and 6, so a potion can drop the
  ceiling onto a total that was safe. `BeyonderSession.drink` therefore takes
  a `maxCorAfter` function and checks the cap of the state being drunk INTO,
  even when the advancement costs 0 CoR (Codex caught this one).
- **The 3/5/8/10 ladder is ritual-scoped.** The book prints it under
  "Corruption from Ritual Work" and calls it "additional ritual-specific
  thresholds"; spec section 5 paraphrases it as general. Book wins, so the
  file is `ritual_cor_thresholds.json`, the loader is
  `ContentLoader.ritualCorThresholds()`, and the only penalty exposed is
  `Corruption.ritualPenalty`. There is deliberately no general `penaltyFor`.
- **Advancing early charges the book's floor, not a guess.** 10 CoR on a
  successful PCR before full digestion; the "+" stays the GM's. Against a
  typical Will of 10 that single click can both integrate the potion and end
  the character — verified on device.

Still reported rather than modelled: the other CoR sources (`cor_gained.json`
carries ranges and a `1d` row the GM rolls), and CoR removal (p. 68: one per
month of abstinence, or a cleansing ritual).

## 6b. Pools — HP, FP, SPI (Ch. 5)

`Pools` reports where a pool stands; `SavedSession` stores what has been
LOST, never what is left, because potions move the maximums. Four readings
worth keeping:

- **FP has no printed table.** Its thresholds appear as prose in two
  sections that disagree — p. 42 "at 1/3 FP or below: -1 to all rolls",
  p. 41 "-1 per 1 FP below 0". `Pools.fp` takes the WORSE, never the sum,
  and `extract_pools.py` deliberately emits no FP rows rather than authoring
  a table the book does not print.
- **Penalty scope travels with the number.** Spiritual Attrition is -3 to
  SPI rolls and ability checks, not to a sword swing, so `PoolStatus.scope`
  is part of the answer. Same lesson as the ritual CoR ladder.
- **Spending FP below 0 costs 1 HP per point** (p. 42). `BeyonderSession`
  applies it, and refunds it on the way back up — printing a rule the app
  then fails to apply is how a tracker starts lying (Codex caught this).
- **-5×HP is dead with NO roll**, so both roll flags are clear there; below
  -HP they are set. Reeling is never withdrawn once HP falls further.

## 6c. Fright checks (Ch. 5, p. 43-44)

`FrightCheck` rolls twice — 3d6 vs Will + the trigger's modifier, then
3d6 + margin of failure against a nine-band table. Three things it settles:

- **The table does NOT override the critical rules.** The PCR prints its own
  "1 always fails / 19 always succeeds"; this one prints nothing, so
  `SuccessRoll` stands and a 3 passes at any target. Specific beats general
  only where the specific rule actually speaks.
- **A 17 or 18 can fail a target it never beat**, overshooting by nothing —
  and the table starts at 4, so a bare 3 would land on no row. The mildest
  band is the answer there; crashing mid-scene is not.
- **Three of the five triggers print no single number** (`-2 to -6`,
  `-8 or worse`). The dialog offers a stepper, because rolling the gentlest
  end turns the GM's harsher call into a softer one (Codex caught this).

The effects table is three columns wide with unevenly wrapped cells, so
`extract_fright.py` ends a cell on a sentence — `vs.` excluded — and requires
exactly three per row rather than splitting lines evenly, which mis-cuts the
22–23 and 24–29 rows while looking correct everywhere else.

## 6d. Rituals (Ch. 7, p. 67-68)

`RitualResolution` is `Corruption.ritualPenalty`'s first production caller —
the reason that penalty was scoped to rituals instead of made general. Target
= skill + weight (Light +0 / Moderate -2 / Heavy -4) + the CoR penalty.

- **Two doors into the Critical Failure Table.** One row is named by the ROLL
  ("Critical Failure (roll 17-18)"), one by the MARGIN ("Failure by 10+ …
  with -2 to the result roll"). GURPS already calls a failure by 10+ critical,
  so an 18 against a low target is both. The roll-named row wins and carries
  no -2 — same shape as the PCR reading.
- **Success goes by outcome, never by margin.** A rolled 18 against a target
  of 22 has a non-negative margin; reading the margin reported the book's own
  critical-failure row as a success (Codex — the same slip `FrightCheck` had
  made an hour earlier). Regression test verified against the old branch.
- **The critical table is not ordered by severity** — 3-4 Backlash, 13-14 a
  CoR, 18 Catastrophe — so the -2 shift moves down the TABLE, not down a
  severity scale. Pinned in `test_tools.py`.

## 6e. The edit layer (`SavedEdits`)

A premade is read-only, so an edit is a **delta** stored per session:
attributes (null = as printed) and skills bought in play. `applyTo` composes
sheet + edits, and every screen that needs the character being played goes
through `session.characterFor(sheet)` — while `SheetComparison` keeps using
the unedited sheet, because that diff is the engine against the book.

- **A bought skill stores name + points only.** Attribute and difficulty are
  resolved from `skills.json` on load, so a save file cannot contradict the
  book after a content fix. A skill the book no longer prints is dropped, not
  guessed at; one the sheet already prints is not duplicated (`Character`
  rejects two purchases of a key).
- **Specialization travels with the pick.** Five names carry several rows —
  Guns has four, Navigation three, plus Gunner/Boating/Artist. Resolving by
  name alone bought whichever the book printed first (Codex).
- **Only skills bought in play are editable.** The sheet's printed purchases
  are shown because the budget bills them, but re-pointing them would make the
  published-sheet diff meaningless.

This unblocked rituals: no premade prints Ritualistic Magic, so buying it is
the only way to have a caster. Device-verified — bought at 1 point (level 7 on
IQ 10, Very Hard), raised to 4 points (level 9), survived a force-stop.

## 6f. The ritual screen

`RitualDialog` is reachable only when the character owns Ritualistic Magic —
gated on `skillLevelOf(RITUAL_SKILL, …)`, which is null for all 23 premades.
Buying the skill in the builder is the whole reason 6e came first.

- **The CoR a critical row charges is extracted, not scraped.** Three rows
  print a cost inside their prose ("Corruption. Gain 1 CoR."); `corGained` is
  parsed once in `extract_ritual.py` and pinned by `verify()`. A UI that read
  numbers out of rule text at render time would be a parser nobody tests.
  The six rows that charge nothing carry no field — charging no corruption is
  not the same as charging zero.
- Device-verified: Heavy on a level-9 caster gives target 5; a 16 failed by
  11, took the "Failure by 10+" row, and rolled the Critical Failure Table at
  8 after the −2 shift. The CoR charge itself was NOT observed live — reaching
  a charging row needs a critical roll of 13+, which after the −2 shift is
  ~0.4% per attempt. It rests on `RitualResolutionTest` and `gainCor`'s tests.

## 6g. Active defenses

`DefenseDialog` pairs the character's own skills with `ParryBlock`: Dodge from
Basic Speed, an unarmed parry per Brawling/Boxing/Wrestling, Block off the
Shield skill, plus the book's modifier bullets. Device-verified on Barmaid
Brawler — Dodge 7, Parry (Brawling) 9, Parry (Wrestling) 6, the last two
differing because Wrestling's -3 sits INSIDE the halving.

- **The formulas are content, not arithmetic.** `ParryBlock` implements them
  and is tested against the book, so `defenses.json` carries the printed
  formula and notes for the GM to read, never a second copy of the maths.
- **Weapons never needed a table** *(wrong — see section 6t)*. This slice
  concluded the book printed no weapon list, because parry is
  `Weapon Skill / 2 + 3` and the number follows the skill. It prints nine, in
  chapter 8, which nothing had read. The formula claim still holds; the
  "no weapon list" one does not.
- `armedParries()` handles the armed case anyway (Codex) — no premade carries
  a weapon, but the model does, and a silent omission would be wrong the first
  time one appeared. It is a plain function so it is unit-tested.
- Two parsing notes: the table's label wraps ("Parry" / "(Unarmed)"), so
  labels are a known set rather than a shape; and this PDF prints list bullets
  as **U+007F**, which `stream()` strips — worth knowing for any future
  bullet list.

## 6h. Conditions — the trackers reaching the dice

`Conditions.forSkill` is the join between the trackers and a roll. Correction
to what this doc said for three milestones: **`ModifierResolver` always had a
production caller** — `SkillRoll.of` routes every roll through it. What was
missing is that the app passed an EMPTY modifier list, so a character at 0 HP
rolled at full skill and the trackers were decoration.

- **Built per skill, not per character.** Spiritual Attrition is -3 to
  SPI-based rolls only (p. 35); the CoR ladder is -1 to ritual rolls only
  (p. 68). `ModTarget.RollClass` cannot express either — `SkillKey` carries no
  tag — so the scope is decided where the skill is known. A list built once
  per character would have to lie about one of them.
- **The ritual dialog takes the same penalties minus CoR** (Codex), because
  `RitualResolution` applies the ladder itself; passing both would charge a
  corrupted caster twice for one rule.
- Device-verified: at 10/10 HP, Knife rolled against 10 flat; at 3/10 the
  same roll read **"base 10, modified to 9"**.

## 6i. Attack options — the chosen half of a roll

`Attacks` is the other half of `Conditions`: what the PLAYER chose this turn,
where `Conditions` is what the character is carrying. `AttackDialog` picks the
skill first, then the maneuver and its options, and hands both lists to the
same `SkillRoll`.

- **Skill first, because the cap depends on it.** Deceptive Attack's ceiling is
  "half your skill (rounded down)", so a stepper offered before the skill is
  known would either cap against the best skill on the sheet or trim the choice
  afterwards — and `Attacks.modifiers` refuses a plan past the cap rather than
  silently obeying it.
- **The cap reads the sheet's level, not this turn's.** Anything else is
  circular: Deceptive's own -2s would move the ceiling limiting them, and
  All-Out Attack would let a fighter feint deeper by swinging harder.
- **Some options make more than one attack** (Codex). All-Out Double is "roll
  twice" and a Rapid Strike splits one attack into two; rolling once dropped
  the rest, and rolling the missing one as a fresh maneuver charged its 2 FP
  again. `Attacks.attackCount` settles it, and how the two COMPOSE (2 × splits,
  not 2 + splits) is a documented reading — the book composes them nowhere.
- **Numbers printed in prose stay in Kotlin.** Same split the defenses slice
  settled: `maneuvers.json` carries what the GM reads plus the FP the app
  SPENDS; the +4/-4/-6/-2 live in `Attacks`, and `extract_combat.py`'s
  `verify()` pins the sentences they were read from.
- **Every skill is offered, not a combat list.** This book tags no skill as a
  combat skill, so which of them can attack is the GM's call; curating the
  list here would be inventing content. (The reason given alongside — "and
  prints no weapon table" — was wrong; see section 6t. The conclusion is not:
  chapter 8's table names seven skills and the sheets carry dozens.)

The middle column is what makes the table parseable at all: it is a closed set
of five tokens, so a line is a maneuver NAME exactly when the line after it is
one of them. A shape heuristic cuts "(max ACC+2). Canceled by…" into a row of
its own and shifts everything below it.

Device-verified on Barmaid Brawler at 3/10 HP: All-Out (Determined) with two
deceptive steps read **"base 13, modified to 12"** (+4 −4 −1 reeling) and took
2 FP; one Rapid Strike produced **two** rolls at 6 and charged nothing.

## 6j. Damage — the ST table's first reader

`Damage` closes T2. `ContentLoader.thrustSwing()` had been loaded, tested and
callerless since M0; until an attack roll existed there was nothing to attach
it to.

- **Wrestling has no attack rows and gets none.** Its table is Maneuver /
  Roll / Opposed Roll, and the book's own summary says "None (grapple only)".
  Parsing it beside the other two would invent damage.
- **Shove returns null, not zero.** Its damage cell reads "Special" — it
  knocks down instead of wounding, and `1d-4 cr` on that row would be a claim
  the book never makes. Same shape as `RitualCritical.corGained`.
- **Two unrelated bonuses reach damage** (Codex): All-Out Attack (Strong)'s
  +2, which touches damage and not the roll, and Boxing's for skill above DX.
  The first version applied only Boxing's, so Strong cost 2 FP and did
  nothing. `Attacks.damageBonus` composes them so a caller cannot read one and
  miss the other.
- **No minimum damage.** `DiceExpr` allows a negative total and this book
  prints no floor, so an elbow at ST 1 really is `1d-8 cr`. Flooring it at 1
  would quietly strengthen the weak.
- **The attack's own to-hit penalty is a modifier**, not a smaller level: a
  Kick is "Brawling-2", and folding that into the level would hide from the
  player why the number moved.

Wounding multipliers (cut ×1.5, imp ×2) are deliberately not applied — they
belong to the target's side of the hit, with DR, and every unarmed attack here
is crushing. `DamageTest` asserts that, so a cut row appearing is a finding.

**Found on device, by nothing else:** a horizontally scrolling `Row` of chips
inside an `AlertDialog` never receives the drag on a 1080px phone, so three of
the five maneuvers — Strong, Double, Charge — could not be selected at all.
Shipped in the previous commit and invisible to every test. They wrap in a
`FlowRow` now. Device-verified after the fix: Punch `1d-3 cr`, Kick `-2` to
hit and `1d-2 cr`, Strong `1d-1 cr` with the roll unchanged, and Heroic Charge
buying off Charge's -4 for 2 FP.

## 6k. Ranged — distance and the scene

`Ranged` is the third modifier source, beside `Conditions` (what the character
carries) and `Attacks` (what the player chose). Same `SITUATIONAL` kind as
`Attacks`: a shot into fog is circumstance, not a threshold state.

- **The book's own bands overlap at 100 metres.** "51-100" is -5 and "100+" is
  -6, so exactly 100 matches both. Recorded, never repaired. The nearer band
  wins — if the open one did, "51-100" would silently stop at 99.
- **That overlap immediately bit** (Codex): the screen stored a chosen band and
  passed back its lower bound, so picking "100+" displayed -6 and rolled -5.
  `Ranged.modifiers` now takes a `RangePenalty`, not a distance; a caller
  holding metres calls `bandFor` itself and gets one answer. The regression
  test was verified against the old signature.
- **Two rows have no number this app can know** *(no longer true — see 6t)*.
  "+ weapon's Acc". Reading the first signed integer would have called the
  second Aim row +2, which is still the parsing trap; what was wrong was the
  conclusion that the number did not exist. The cell says "see weapon table"
  and chapter 8 is that table. `modifiers` now takes the Acc, and still
  refuses the row when no weapon was chosen.
- **Prone means two different things.** "-2 ranged (but -4 in melee)" — one
  row, two scopes, so the melee value is kept beside the ranged one. Same
  lesson as Spiritual Attrition and the ritual CoR ladder.
- **Darkness is a range the GM picks inside** ("-3 to -9"), so `modifiers`
  takes an override and validates it against both printed ends. The screen
  applies the gentle end for now.
- **Cover's "+2 DR" is not a to-hit modifier.** Only the -2 reaches the roll;
  DR belongs to the damage side, which is not modelled.

**Two defects only the device found**, both invisible to 479 tests: the
`Another turn` reset left last turn's distance and cover in place, silently
modifying the next roll (Codex); and the dialog grew taller than a phone, so
the Roll button fell off the bottom — an `AlertDialog` does not scroll its own
text slot. Device-verified after: `100+` with cover read "Range and cover: -8"
and a reeling Brawling 13 rolled against 4.

## 6l. Called shots — the two tables that multiply a hit

`Wounds` holds both Optional tables from p. 41, because they are one decision:
aiming at a skull is -7 AND ×4. The to-hit half joins `Conditions`, `Attacks`
and `Ranged` on the roll; the damage half is shown beside the expression.

- **The two multipliers STACK, and that is not GURPS.** In GURPS proper a hit
  location's multiplier replaces the damage type's. This book prints the
  opposite with a worked example — "a cutting hit to the Neck (Cut ×2) with a
  sword (Cut ×1.5) applies ×3 total" — so the book wins, and an impaling hit
  to the Vitals is ×6. Codex read the source lines and confirmed the sentence
  is general, not scoped to the Neck row.
- **Two rows have no single multiplier.** The Neck prints "Cr ×1.5 / Cut ×2 /
  Imp ×2" and the Vitals "×3 (imp/pi only)", both wrapped mid-cell. Reading
  the first number would call a cutting neck hit ×1.5 and triple every punch
  to the vitals. The extractor consumes a multiplier cell while a line still
  holds a "×" or the text so far has an unclosed bracket — so both wraps are
  handled for a stated reason rather than by counting lines.
- **A missing key and an unlisted type are not the same thing** (Codex). The
  Neck row is silent about piercing, so `pi` takes the plain ×1; `pi-` is not
  a damage type this book prints at all and is refused. The first draft put
  the fallback on `HitLocation.multiplierFor(String)`, where a typo resolved
  to ×1 — the lookup now lives only in `Wounds.multiplierFor`, which validates
  the type first. Nothing could reach the permissive path yet; removing it is
  what stops the next caller finding it.
- **Only the four abbreviations CHAPTER 5 prints exist** *(the second half of
  this was wrong — see section 6v)*. "Cr", "Cut", "Imp" come from the Neck
  cell and "pi" from the Vitals cell. This section then said GURPS writes
  "pi-" and "pi+" and "this book never does". Appendix A does, at p. 159. The
  chapter-5 half still holds; the claim about the book did not.
- **The multiplier is shown, never applied.** Damage here is an expression,
  not a rolled total, so there is no product to round — and this book prints
  no rounding rule to round it by. GURPS's round-down is GURPS's.

Device-verified on Barmaid Brawler at 3/10 HP: Skull read "Called shot: Skull
-7, ×4" and "Damage on a hit: 1d-3 cr, ×4 there", and the roll read **"base
13, modified to 5"** (−7 called shot, −1 reeling). Vitals quoted "×3 (imp/pi
only)" while leaving the crushing punch's damage line at plain `1d-3 cr` —
the exact answer a naive parse gets wrong. "Another turn" clears the location,
and the Roll button is still reachable now that the panel is one line taller.

## 6m. Spirit-body damage, and the debt that never clears

`SpiritDamageDialog` applies one amount to one pool, and `BeyonderSession`
finally charges the Soul Debt the SPI row had been printing for two
milestones. The trackers already let a GM move any pool by hand, so what this
adds is the CHOICE the book puts on the target — and enforcing "One pool per
instance — you cannot split a single hit across multiple pools" by having one
amount, one pool, and a dialog that closes.

- **The rule was printed and never applied.** `Pools.spi` has computed
  `corGained` since M4's pool slice, the SPI row displayed "Soul Debt: +1 CoR
  per 1 point below 0", and `spend(Pool.SPI, …)` moved the pool and stopped.
  Exactly the FP→HP defect Codex caught, one pool over, sitting in `master`
  the whole time. Three regression tests, all verified failing against the old
  branch.
- **Soul Debt is ONE-WAY, and Codex is why** (this was the review's blocker,
  and it beat my own reasoning with the book). FP's HP charge is refunded when
  the pool comes back up, because a stepper the GM cannot undo is a worse
  tool. I copied that symmetry to CoR — but the section extracted in this very
  slice says "CoR from Soul Debt are permanent and require special rituals
  (Chapter 6) to remove". Worse, `cor` is one total with no memory of what
  charged it, so recovering SPI could have spent a RITUAL's corruption paying
  the pool back. Charging is on the increase only; removal keeps its own path.
- **"add 3 CoR on the pathway screen" was found on device.** Honest while
  nothing charged it, an instruction to double-count the moment something did.
  It reads "3 CoR charged" now. No test could have caught it — the string was
  correct in isolation and wrong in the same commit that made it stale.
- **`maxCor` is a required parameter on `spend`, not a defaulted one.** No
  value is safe when it is wrong: too low corrupts a character who should have
  survived. `MainActivity` computes it once and both the ritual dialog and the
  trackers charge against the same number.
- **"Threshold Penalties by Pool" is deliberately not extracted.** Its HP and
  SPI lines are already `pool_thresholds.json` and its FP line is one of the
  two prose sources `Pools` quotes. `verify()` asserts the group is still
  THERE in the source, so the drop stays a decision rather than a parse that
  quietly stopped working.
- **The sheets give mortals SPI, and the book says non-Beyonders have none.**
  Barmaid Brawler's published block prints `spi: 9`. Rather than pick a side,
  the app reads p. 41's "Non-Beyonders **(SPI 0)**" as the definition it is —
  whoever has SPI 0 cannot choose it — so `maxSpi > 0` gates both the tracker
  and the dialog's third chip, and no premade is contradicted.

Device-verified on Barmaid Brawler: 12 spirit damage to SPI took 9/9 to
**-3 / 9** and the saved session read `cor: 3`. Recovering SPI to +2 left
`cor` at **1** — the debt stayed owed.

## 6n. The Major Wound, and one sentence that needs its own example

`Wounds` gained the p. 41-42 threshold, and `SpiritDamageDialog` became
`DamageDialog` — one hit, one amount, one pool, because a Major Wound is a
property of the SINGLE hit and that is exactly what a -1/+1 stepper destroys.

- **The threshold sentence contradicts itself, and the example only half
  settles it.** "Any single attack that deals more than half your HP (round
  up)". Read literally that is `damage > ceil(HP/2)`, which at HP 9 needs 6 —
  and the book's own example says a 5-damage strike triggers it. So "round up"
  describes what to do with the half, not a ceiling to then exceed.
- **The example is an ODD total, so it does not settle even ones** (Codex
  called this out and asked for the decision to be recorded rather than
  changed). Two readings survive it: `damage >= ceil(HP/2)`, making 5 a Major
  Wound at HP 10, and `damage > HP/2`, needing 6. The second is taken, because
  at HP 10 a 5-damage hit deals EXACTLY half and the sentence asks for more
  than half — and because GURPS 4e, which this book is derived from, defines a
  major wound as damage over 1/2 HP, strictly greater. Still a reading, so it
  is pinned by a named test rather than left to be rediscovered.
- **The roll is offered, not made.** The book says "make an immediate HT
  roll", and a roll the app fires on its own is one the GM cannot decline for a
  hit they already resolved at the table. Changing the amount or the pool
  clears the result, because a roll belongs to the hit it was rolled for.
- **Read by outcome, then by margin.** A 17 fails a target of 18 with a
  POSITIVE margin, so a margin-first reading would report the book's own
  failure rows as successes — the slip `FrightCheck` and `RitualResolution`
  each made once. Only `Fail by 5+` carries a margin; the other two rows carry
  none rather than a zero.
- **The Result column is the anchor.** Its Effect cells hold "-4" and "1d6",
  so the signed-number rule the hit-location table uses would cut them wrong.
  Three known strings, like the maneuver table's movement column.

Device-verified on both parities: Academic Researcher at **HP 9** — the book's
own example character — reads "Major wound at 5+", and Barmaid Brawler at
**HP 10** reads "Major wound at 6+", with 5 damage refusing to trigger it. At
6 the HT button appeared, rolled **12 vs 10** and took "Failure — Stunned for
1d6 seconds" rather than the Fail-by-5 row; stepping back to 5 cleared it. The
mortal's dialog offers HP and FP only.

## 6o. Shields — the number `ParryBlock` waited four milestones for

`ParryBlock.blockFor` has taken a `shieldDb` since M0 with nothing able to
supply one: the defense dialog printed `"+ shield DB"` and left it to the GM.
The book prints four shields, so it is a chip row now. **This closes M4.**

- **The DB is not a Block bonus.** "While Readied, its DB (Defense Bonus) adds
  to all active defenses (Dodge, Parry, and Block)." Block is the only defence
  whose FORMULA mentions a shield, which makes Block-only the easy wrong
  reading — and it would understate a shielded Dodge by up to 3.
- **Facing is not modelled, so the total is not presented as one** (Codex).
  The bonus applies only "against attacks from the front or shield side", and
  this book prints no arc to test against. Baking it in would let a GM roll a
  shielded Dodge against a backstab, so every row shows **both** numbers —
  `7 → 10` — with the condition stated once above. Cheaper than a facing
  selector and impossible to misread as unconditional.
- **The two Block penalties are not the same rule.** Light is "-1 to Block vs.
  ranged" and Improvised is "-2 to Block" with no scope named, so it applies in
  reach too. Same lesson as Spiritual Attrition and the prone target's melee
  value: the scope travels with the number. They live in `Shields` rather than
  the JSON because they are numbers inside prose — `extract_shields.py` pins
  the sentences and `ShieldsTest` pins that the cells still say them.
- **A shield name always wraps** ("Light (buckler," / "target)"), so the name
  is walked BACKWARD from the DB cell while its brackets are still open — the
  unclosed-bracket rule the Vitals multiplier already needed. Counting two
  lines per name works today and breaks on the first one-line shield.
- **The DB column is the anchor**, being the only bare signed integer: three of
  the four Notes cells carry their own signed numbers ("-1 to Block vs.
  ranged", "Move -1 while Readied").
- **Weight and Cost stay text.** The improvised row prints "varies" and an em
  dash, which are values, not gaps.

Device-verified on Academic Researcher with Shield bought at 1 point (level 9,
Block 7): a heavy shield read Dodge **7 → 10**, a light one Dodge **7 → 8**,
Block **7 → 8** and "Against ranged attacks: **7**" — the -1 landing on shots
and nothing else.

Still unmodelled and deliberately so: Feint's Quick Contest, a shield's facing
arc, and the shield bash's damage (it is `thr-2`/`thr-1`/`thr`, which `Damage`
could roll, but a bash is a weapon attack and the shield is not in chapter 8's
weapon table). Aim's ACC was on this list — "no weapon in this book prints
one" — and section 6t retires it: nine do.

**Correction:** this section first said "M4 is complete". It was not. M4 is
spec section 8's "T2 + T3", and T3 is chapters 6.5, 7, 7.5 and 13 — so
**Divination Arts (Ch. 6.5) was still at 0%** when Chapter 5 closed. Section
6p is that slice. Check `tools/coverage.py` against the tier table before
calling a milestone done; the chapter you just finished is not the milestone.

## 6p. Divination — the second caster, and one table with two doors

`Divination` is `RitualResolution`'s sibling: state the question's difficulty,
choose a method and pay its SPI, add the circumstances, roll, read the clarity
off the margin. Same gate as rituals — no premade prints Divination Arts, so
it is bought in the builder before the `Divine` button appears.

- **The clarity table has two doors, and this time it was expected.** Five
  rows are named by margin ("Success by 3-4") and the sixth by OUTCOME
  ("Critical Failure"), which is exactly the shape `FrightCheck` and
  `RitualResolution` each got wrong once. Outcome first, margin second.
- **A critical SUCCESS is read by its margin.** The book prints a
  critical-failure row and no critical-success row, and that asymmetry is
  deliberate enough to follow: a 4 against a target of 5 is "Vague", and
  inventing "Crystal clear" for it would be inventing content. Codex read the
  source lines and agreed.
- **One modifier row is not a modifier.** "Question difficulty / See Step 1
  table" points back at Step 1, which `targetFor` already applies. Read as
  zero it silently drops the whole question; added beside Step 1 it charges it
  twice. `modifierTotal` refuses it and the dialog never offers it.
- **The critical row prints no effects of its own** — it says "treat as a
  ritual critical failure" — so `RitualResolution.criticalFor` is rolled
  rather than a second copy of that table being carried here.
- **Dowsing Rods costs "1-2 SPI", and the app charged 1** (Codex). The screen
  displayed the range while `spiFor` returned the low end. Undercharging SPI
  is not a rounding difference: below 0 it is Soul Debt, and that CoR is
  permanent. There are chips for the two ends now, and `spiFor` validates.
- **`Ask` was re-entrant** (Codex). Two taps could reach the handler before
  the button left the tree and spend the SPI twice — again buying permanent
  CoR from one visible action. It returns early when an answer is already
  showing.
- **"Overnight" arrives split MID-WORD** as "Overnig" / "ht". No cell-shaped
  rule can see that; the extractor joins the time cell without a separator and
  checks it against the six times this book prints. "Dream" / "Interpretation"
  wraps the method NAME in the same table, which is why the closed set of
  eight method names is that table's anchor.

Device-verified on Academic Researcher with Divination Arts bought at 1 point:
the skill reads **-2**, because it is SPI/Hard and a non-Beyonder has SPI 0 —
the book gating divination on spirituality, arithmetically. A Trivial question
gave "Roll 3d6 ≤ 0"; a 9 landed on **"Failure by 5+ — Distorted"**; choosing
the 2 SPI end of Dowsing Rods saved `spiSpent: 2` and `cor: 2`, the Soul Debt
charging itself.

## 6q. Rituals stop being free

A ritual cost nothing until this slice. `RitualDialog` rolled, charged the
critical rows' CoR, and never touched SPI — so a caster could cast forever,
which is the opposite of what SPI is in this book for. Two tables fix it, both
of which were sitting unclaimed in Chapter 7.

- **The SPI column was in a different printing of the same table.**
  `ritual_weights.json` came from the p.67 SUMMARY (Weight / Categories). The
  fuller table at p.67's Section V prints Base Mod, **Typical SPI** and
  Example Categories, and only there does the cost appear. The two printings
  agree on the modifier, so the cost is merged onto the existing rows by
  weight name rather than becoming a second weights table.
- **Two of the three costs are not a number.** Light is "2 SPI", Moderate
  "4-6 SPI", Heavy "8+ SPI" — fixed, a range, and a floor. The open cell
  offers its floor and three above it in the UI, because the book names no
  ceiling and a stepper that runs to a hundred is not a choice worth making
  at the table.
- **`Int.MAX_VALUE` was the wrong way to say "no ceiling"** (Codex). With it
  as the upper bound, `chosen + burned` wrapped NEGATIVE — and a negative cost
  would have been paid back as SPI by `spend`, healing the caster. It is a
  null bound and `Math.addExact` now. Unreachable from the dialog, which
  offers 8..11; reachable through the rules API, which is the point.
- **Preparation is Phase I, and its default is Adequate.** Excellent is +3 and
  starting there would hand out the book's best preparation for free. Two rows
  print ranges ("+1 to +2", "-1 to -2") and one a floor ("-4 or worse"), all
  applied at the end nearer zero — the same gentle reading as the darkness row.
- **The fuel is spent on a failure too**: "No effect. SPI and materials
  spent" (p. 62). And `Roll` returns early when an answer is already showing,
  the same re-entrancy guard the divination dialog needed.

Device-verified on Academic Researcher (Ritualistic Magic 9): a Heavy ritual
with Excellent preparation read **"3d6 ≤ 8"** (9 − 4 + 3) and offered fuel
chips 8/9/10/11. A 16 failed by 8 and took "Failure by 5-9 — Partial, wrong
activation"; the saved session went from `spiSpent: 2` to **10** and `cor: 2`
to **10**, the Soul Debt charging itself because that caster had no SPI pool.

## 6r. Boons — a ritual whose two parameters are not the same axis

`Boons` is Chapter 13, and it re-implements nothing: a boon IS a Chapter 7
ritual with its parameters nailed down, so `RitualResolution` rolls it,
charges its failure bands, and rolls the Critical Failure Table the refusal
penalty points back at. `BoonDialog` is gated on Ritualistic Magic, the same
skill and the same reason the ritual screen is.

- **"Heavy" and "Moderate" are printed one line apart and only one reaches
  the dice.** "Ritual Complexity: Heavy (1 hour base time)" is TIME; "Effect
  Weight: Moderate (−2 difficulty, 4−6 SPI required)" is the roll — the book
  says why, in the next sentence: "because the actual power comes from the
  patron, not the ritual itself." Reading the first as the weight rolls at −4
  and charges 8+ SPI for a ritual priced at 4−6. Both are extracted so the
  two cannot be collapsed silently, and `verify()` fails if they ever read
  the same.
- **The boon-level ladder is a DOUBLING, and the printed values do not settle
  it.** "Sequence 9 equivalent (+0), Sequence 8 (−2), Sequence 7 (−4), and so
  on, doubling the penalty with each sequence." Those three fit a doubling
  and a flat −2 per step equally; they diverge first at Sequence 6 (−8 vs
  −6). The sentence says doubling, so Sequence 0 is **−512** — which is the
  point, because the chapter says a generous sacrifice "can offset or even
  negate this penalty" and the best one printed is +6. A reading, so it is
  pinned by a named test rather than left to be rediscovered.
- **That ladder made a UI line lie** (Codex). At Sequence 0 the panel read
  "3d6 ≤ −505", which says impossible — and it is not, because `SuccessRoll`
  treats a rolled 3 or 4 as a critical success whatever the target and this
  chapter prints no override. `targetLine` says so now, and `SuccessRoll`'s
  4/17 thresholds are named constants instead of two magic numbers a screen
  had to guess at.
- **A GM's chosen value inside a range was keyed by label** (Codex). Nothing
  in the book or the extractor stops the two groups printing the same words,
  and one value would then have moved both rows. `BoonModifier.key` is
  `group / label`, an override naming nothing chosen is refused rather than
  ignored, and the dialog builds the map from the CURRENT picks — so no path
  can leave a stale value behind, which is what "Another turn" did.
- **The third modifier bullet is a rule, not a row set.** Parsed like the
  other two it would stop at Sequence 7 and the ladder would have to be
  guessed. It ships as the sentence; the ladder is Kotlin. Same split as the
  maneuver table's +4/−4/−6/−2.
- **Ch. 7's Phase I preparation is deliberately not applied.** Chapter 13
  prints its own Ritual quality row for the same idea, and applying both
  would charge one preparation twice — the double-count the CoR ladder
  already had to be scoped out of.
- **Summoning (Ch. 7.5) was read and deliberately left unmodelled.** It says
  "the GM must roll an additional 3d for the creature's response" and prints
  no response table; the creature's abilities, cost and attributes are a
  format for the GM to fill in. Coverage agrees: one mechanical line in two
  pages.

Device-verified on Academic Researcher (Ritualistic Magic 9, SPI 0): Sequence
8 with a great sacrifice at +6 read **"3d6 ≤ 11"** (9 −2 −2 +6) and a 13
failed by 2 onto "Failure by 1-4"; the fuel was charged anyway and
`spiSpent: 4` bought `cor: 4`, the Soul Debt again. A rolled **17** took the
roll-named critical row with no −2 shift and landed on "9-10 Spiritual
Drain". Sequence 0 read **"3d6 ≤ −505 — a rolled 3 or 4 still succeeds"**. A
7 against 13 granted the boon and printed Section IV's four consequences.

## 6s. The non-standard pathways — 17 blocks that must never be drinkable

Chapters 14 and 15 print Sequence blocks in exactly the shape chapters 9-12
do, so `extract_non_standard.py` is `extract_pathways.extract()` with a
different chapter list and its own `verify()`. What the slice is actually
about is where they are NOT allowed to go.

- **They are not potions, and `pathways.json` is what makes something a
  potion.** Ch. 14: "Unlike standard Beyonders who consume potions brewed
  from Beyonder Characteristics, followers of non-standard pathways advance
  through boons." `PotionDigestion.of` walks whatever that file holds and
  `canDrink` offers whatever it walks, so one row in the wrong file puts a
  Sequence 9 Dancer potion on the pathway screen. Separate file, separate
  object, and `not_also_a_potion()` fails the extract if the two ever
  intersect.
- **The ladder has holes, which is a second reason they could not go there.**
  Ten Sequence 9 blocks, seven Sequence 8, and they do not line up: Second
  Law, Sublunary Eye and Eternal Edict print a Sequence 8 whose Sequence 9
  the book never records. `pathways.json` verifies that every pathway carries
  all of 6/7/8/9 — these would have broken it, and the intro says why: "most
  pathways are only known at this sequence".
- **Three blocks print an " Ability" heading with nothing under it** —
  Villain, Astronomy Aficionado, Secretary. In each the next line is a page
  break or the following block's header, so the section is empty in the BOOK
  and nothing was lost in the parse. Named exclusion set, same shape as
  `GoldenSheetTest`'s; the screen says so out loud rather than showing a gap.
- **One pathway is spelled two ways and both survive.** Ch. 14 prints
  "Eternal Aeon (Inevitability)", ch. 15 "Eternal Aeon". Merging them is a
  repair, so the lookups stay exact — which means the computed gap list has
  four members and only three are real gaps.
- **That disagreement immediately misled the UI** (Codex): the picker lists
  printed names, so both appear, and choosing ch. 15's spelling at Sequence 9
  read "the book records no Sequence 9" while the Dancer block sat under the
  other name. Codex's fix was an alias map collapsing them into one patron;
  that was declined, because it repairs the data and hides that the book
  prints two names. `alsoPrintedAs` names the pair instead, and the screen
  points across: "The other chapter prints this pathway as ... and records 9.
  The book never reconciles the two names."
- **`blockFor` throws for an unknown pathway and returns null for an
  unrecorded rung.** Same distinction `Wounds.multiplierFor` draws between an
  unlisted damage type and a silent cell. The dialog's pathway is nullable on
  purpose — the Great Ones are not a closed list, and petitioning one the
  book never printed is the GM's call.

Device-verified on Academic Researcher: all 14 pathway chips render; "Eternal
Aeon (Inevitability)" at Sequence 9 shows **Dancer — DX +2, SPI +6, HT +1,
Dancing +4, Acrobatics +3**; "Eternal Aeon" at Sequence 9 shows the
cross-reference; Mother Goddess of Depravity shows the Villain's stats,
skills and drawback followed by **"The book prints an Ability heading for
this block and nothing under it."**

## 6t. The weapon table this doc said four times did not exist

Chapter 8 prints nine weapons with Damage, Acc, Range, Cost and Skill. Four
sections above asserted it printed none — 6g, 6j, 6k and 6o — and three
features were deferred on that premise: armed parry, weapon damage, and Aim,
whose whole modifier is "+ weapon's Acc". `Ranged.modifiers` refused those two
rows with the message *"this book prints no weapon table"*. The table had been
in chapter 8 the entire time, at **0.0% coverage**, because nothing had read
it. Every claim above was checked against `defenses.json` and chapter 5 and
never against the chapter that would have disproved it.

**The lesson is procedural, not about weapons.** A "the book does not print
X" conclusion is only as good as the chapters that were read, and this repo
already had the instrument that would have caught it: `coverage.py` listed
chapter 8 at 0.0% next to every one of those four claims. Check the coverage
table before recording an absence.

- **The Aim cell said so out loud.** "+ weapon's Acc **(see weapon table)**".
  The parenthetical was extracted, committed, displayed on screen and read
  four times as decoration.
- **`accBonus` is what the row adds ON TOP of the Acc.** The long-aim cell is
  "+ weapon's Acc +2 (max Acc +2 total)" — three numbers' worth of trap. The
  old reading refused the row; reading +2 as the modifier would make a long
  aim *worse* than a short one for any weapon with Acc 3+. The regex anchors
  to the Acc mention so the restated ceiling is not counted twice.
- **Two Aim rows cannot both apply.** They are one aim held for different
  lengths; together they pay the Acc twice and break the cell's own printed
  ceiling. `Ranged.modifiers` refuses the pair and the dialog swaps rather
  than toggles — offering a chip that throws on Roll is not offering a choice.
- **Chapter 8 prints a damage type chapter 5 cannot price** *(and the BOOK
  can — see 6v)*. The Bolt-Action Rifle deals `pi+`, and chapter 5's wounding
  table prints its Large Piercing row with **no abbreviation** — section 6l
  recorded that "this book never does". It does, one chapter over. Adding the
  abbreviation would be repairing chapter 5 from chapter 8, so
  `Wounds.knowsType` answered instead and the damage line named which chapter
  was silent. **The conclusion drawn from that was still too broad**: two
  chapters being silent is not the book being silent, and Appendix A prints
  the label. The guard survives; the message no longer names a chapter.
- **And the same row disagrees about which rifle it means.** Large Piercing's
  example is "Hunting rifles" — while chapter 8 prints the *Hunting Rifle* as
  plain `pi` and gives `pi+` to the Bolt-Action. Two disagreements, one row,
  neither reconciled; `verify()` fails if either quietly goes away.
- **A firearm cannot parry, and that is a reading.** The table has no parry
  column, so the only signal is the Range cell: a reach letter ("C", "C/1") is
  a weapon in the hand, metres are a gun. It lives in `Weapons`, not as an
  invented column in `weapons.json` — a column would look extracted.
- **ST reaches a swing and stops.** `sw`/`thr` rows read the thrust/swing
  table; `2d pi` is printed outright, because a stronger arm does not push a
  bullet harder. All-Out Attack (Strong)'s +2 still lands on both: the book
  scopes Determined ("+4 melee, +1 ranged") and deliberately does not scope
  Strong.
- **`Guns (Pistol)` is armed by nothing.** The skill list prints four Guns
  specializations and the table covers three. Beat Cop, Night Watchman,
  Nighthawk Recruit and the Private Investigator all carry Pistol and get no
  weapon chip. Not a parse failure — the book arms three of its four.

Both Codex findings were app wiring again, and both were live defects: a Guns
skill opened **in reach**, so an immediate roll took All-Out Determined's
melee +4 instead of its ranged +1 and hid the distance rows entirely; and
putting a weapon down cleared the unarmed attack with it, leaving a Brawling
character with no damage line at all until Punch was tapped by hand.

Device-verified on Hunter-Tracker (Guns (Rifle) 10, Knife 9, ST 10): the rifle
opens **At range**, reads "Acc 4 · reach/range 500/3500 · cannot parry · £8",
and a 3-turn aim reads **+6** — tapping the short aim first leaves +6, not
+10. Rolled **base 10, modified to 16**, a number the old code could only
throw on. Damage read *"5d pi+ — chapter 5 prints no wounding multiplier for
"pi+""*. The Combat Knife offers **sw-1 cut** and **thr imp**, reads "parry
7", and a cut to the Neck reads **×3** — the book's own worked example. On
Barmaid Brawler, Brawling opens on the fist, the Truncheon reads "parry 9 /
1d cr", and putting it down returns **1d-3 cr**.

Not extracted from chapter 8, deliberately: armour (3 rows of DR, and nothing
applies DR — damage here is an expression, never a total), starting wealth and
the two currencies, ~70 rows of common equipment, licences, and hired allies.
The chapter is at **9.6%**; the weapons are the part that had callers waiting.

**Still ahead in M4 (T3):** summoning is closed as a decision (6r), so what is
left is Ch. 6.5's Section IV onward — divination awareness and
countermeasures, which is a per-pathway detection subsystem and a slice of its
own. Chapter 7 is also still only part-claimed: power sources, entity
response, ritual timing and the sample rituals are unread. Chapters 14 and 15
are complete (6s). Starting wealth was the piece with a caller already waiting
and is section 6u; the largest unclaimed content left is chapter 3's remaining
third and the rest of chapter 8.

## 6u. Starting Wealth — the table chapter 3 only half prints

`Wealth` (core/rules) reads Ch. 8's Starting Wealth by Economic Status, and
`BuilderScreen`'s wealth row is its interface. The caller really had been
waiting since M0: `PointBudget` bills traits, and a wealth level IS a trait.

- **Chapter 3 prints four of the seven levels, and only the cheap end.** Its
  advantage and disadvantage lists carry Dead Broke, Poor, Struggling and
  Comfortable; Average, Wealthy and Very Wealthy exist only in chapter 8. So
  until this table was read, nothing could give a character either level above
  Comfortable — a 25-point hole in the builder that looked like a complete
  list. Where both chapters price a level they agree exactly, and
  `extract_wealth.py`'s `verify()` fails if that ever stops being true.
- **The reaction ladder is not symmetric, and the prose says it is.** "+1 to
  reaction rolls per Status level above Average ... and −1 per level below
  Average." Above Average the column obeys that exactly. Below it, it does
  not: Struggling is one level down and the column prints **0**, and the
  paragraph's own example puts Poor — two levels down — at −1. Deriving the
  modifier from a level's position is the tidy wrong answer; the printed
  column wins (4.1), and both halves are pinned so a later parse cannot
  straighten it.
- **The page prints two kinds of minus.** Point costs are ASCII (`-25 pts`),
  reaction modifiers are U+2212 (` −2`). A regex that knows one silently drops
  half the negative numbers — and the half it drops still parses, as positives.
- **The sign is in the number here and in the KIND everywhere else.** `Trait`
  carries a magnitude with `TraitKind` giving direction; this table signs its
  points. `Wealth.traitFor` is the single place the two conventions meet,
  because applying both is how a disadvantage starts paying twice.
- **Average is 0 pts and therefore produces no trait at all.** A zero-cost
  advantage or disadvantage would be a row that bills nothing — the same
  distinction `RitualCritical.corGained` draws between charging no corruption
  and charging zero. That immediately bit the UI (Codex): the row read the
  composed character, so choosing Average showed "the sheet prints no wealth
  level" and left the picker unticked, while the budget had silently moved by
  15 points. The CHOICE is authoritative and the trait is only what the sheet
  printed — `wealthShownFor` asks in that order.
- **Changing wealth REPLACES, never adds.** A character has one economic
  status; a printed "Wealth: Poor" left beside a bought "Wealth: Wealthy"
  raises the budget by 15 and spends 15 in the same breath, and both totals
  still look plausible.
- **The reaction modifier is shown, never applied.** This book has no reaction
  roll to apply it to, and the paragraph hands it to the GM out loud ("GMs may
  limit this modifier based on context"). Same shape as a called shot's
  wounding multiplier, quoted beside the damage rather than multiplied into it.
- **A choice needs a way back, and 20 of the 23 premades need it most**
  (Codex). `setWealth(null)` existed and was tested from the first commit of
  this slice, and was unreachable from the picker — which offered seven book
  levels and Close. Twenty premades print no wealth level at all, so one tap
  would have permanently given such a character a status the book never gave
  them. The picker's first row is "As printed" now, and its tick reads the
  OVERRIDE rather than the shown level, because the shown one falls back to
  the sheet's and could never tick.
- **Only the level's NAME is saved.** The points, money and lifestyle are
  re-read from the book every load, exactly as a bought skill's controlling
  attribute is, and a level the book no longer prints falls back to the
  printed one rather than defaulting to Average — which would move a budget by
  up to 25 points in silence.

Deliberately not extracted: "Starting Wealth in Feysac", which restates the
same ladder in gold hoorn and omits Dead Broke. It belongs with the currency
conversion table above it, and nothing converts currencies today. The money
column is committed exactly as printed, pounds and soli both; chapter 8's own
1 pound = 20 soli is used only to check that the ladder climbs.

Device-verified on Academic Researcher (prints Wealth: Poor, 95 of 100
points): the row reads **"Poor — −15 pts, 5 soli to start, reaction −1"**, all
seven levels render in the picker with Poor ticked, and choosing **Average**
gives "Average — 0 pts, £1 to start — sheet prints Poor" with the budget
flipping to **95 of 85 — over budget**. `wealth: "Average"` survived a
force-stop, and "As printed" put the budget back to **95 of 100, 5 spare**
with `wealth: null` saved. Chapter 8 is now at **21.2%**.

## 6v. The appendix, and the same lesson a second time

Section 6t's whole lesson was: check the coverage table before recording an
absence, because a chapter at 0.0% disproves nothing. **This slice is that
lesson repeating**, one appendix over and with the doc's own correction
already on the page.

Appendix A, p. 159, line 9671:

    Wounding: Crush ×1 | Cut ×1.5 | Impale ×2 |
              Pi (small) ×0.5 / Pi ×1 / Pi+ ×1.5 | Burn ×1 | Toxic ×1

- **The app was telling players something false.** Chapter 8's Bolt-Action
  Rifle deals `pi+`; `Wounds.knowsType` returned false and the damage line
  read *"chapter 5 prints no wounding multiplier for pi+"*. True of chapter 5.
  False of the book, which prints ×1.5 for it — and chapter 5 had printed the
  Large Piercing multiplier all along, just unlabelled. What was missing was
  never the number.
- **So the fix adds a label and no number, and the extractor proves it.**
  `extract_appendix.py`'s `verify()` fails if any multiplier the appendix
  repeats disagrees with chapter 5. That agreement is the whole basis for
  adopting the label — without it this would be a second opinion being
  silently preferred, which is not what the book-is-authority rule allows.
- **Provenance stays visible: a separate file, not a new column.** Writing
  `pi+` into `wounding_multipliers.json` would make the committed content
  claim chapter 5 prints something it does not. The multiplier is chapter
  5's, the label is the appendix's, and `damage_abbreviations.json` says so.
- **Only the piercing group is read as abbreviations, and the line says
  why.** The appendix writes "Crush" where chapter 5 abbreviates "cr" and
  "Impale" where it says "imp" — those are short names, and adopting them
  would give one damage type two spellings. Inside the slash-separated
  piercing group it writes "Pi", which is EXACTLY chapter 5's abbreviation
  for that row, and that agreement is what makes its siblings labels of the
  same kind. `verify()` fails if that matching "Pi" ever disappears, because
  it is the only evidence the group has.
- **GURPS's "pi-" is still refused.** The appendix spells small piercing
  "Pi (small)", so that is the key. A book that never writes "pi-" still
  never writes it.
- **Making `pi+` real made a called shot reachable, and that is a reading**
  (Codex). The Vitals cell prints "×3 (imp/pi only)" and chapter 5
  abbreviates exactly one row "pi", so the bracket names plain Piercing and a
  `pi+` hit takes the location's plain ×1 — ×1.5 in total. GURPS gives the
  whole piercing family ×3 there; following it would triple a rifle hit on a
  bracket this book wrote narrowly, and GURPS's conventions are GURPS's
  (6l). Same fallback the Neck row already uses. Pinned by a named test.
- **Coverage did not catch this one, and could not.** `coverage.py` counts a
  line as mechanical when it holds dice, a signed number or a point cost —
  `×1.5` is none of those, so Appendix A reads **0.0% both before and after**
  this slice claimed a line from it. The instrument that would have caught
  chapter 8 is blind here. Where a table's numbers are bare multipliers,
  read the pages; do not ask the percentage.

Deliberately not extracted from the appendix: it is otherwise a summary of
chapters already claimed, and in one place a summary that DISAGREES with
them — its hit-location line reads "Vitals -3 ×3" flat, dropping the
"(imp/pi only)" scope, and lists a "Hand/Foot" location where chapter 5's
table has only Hand. A summary does not overrule the chapter it summarises.
Its success ladder (Good Success at Target−3, Excellent at Target−5) is
genuinely new material and a slice of its own.

## 6w. Appearance — the same shape as wealth, and three ways it is not

`Appearance` (core/rules) reads Ch. 3's eight-level table and `BuilderScreen`
grew a second point-costed row beside Wealth. Same caller, same reason —
`PointBudget` bills traits — and the differences are the whole slice.

- **Chapter 3's trait lists print none of the eight levels.** Wealth was HALF
  printed there, which is what made `"Wealth: Poor"` the spelling to match on.
  Appearance appears among the advantages and disadvantages zero times, so the
  trait carries the level's own printed name and no prefix — committing
  `"Appearance: Ugly"` would invent a spelling the book never uses. That
  disjointness is not assumed: `extract_appearance.py` fails if any of the
  eight names turns up in those lists, and it is exactly what makes matching a
  bare name unambiguous. The one chapter 3 trait whose NAME says appearance is
  "Unsettling Appearance" (4 pts, −1 to all reactions), and the book calls it
  "a common consequence of Abyss and Chained Pathway potions" — suffered in
  play, not chosen at creation, so folding it in would price a disfigurement.
- **There is no zero-cost middle row, so ordinary looks are the ABSENCE of a
  trait.** Wealth prints Average at 0 pts, which is why `Wealth.traitFor`
  returns null and why `wealthShownFor` had to ask the choice before the
  character (6u, Codex). The cheapest row here is Attractive at 1 point, so
  every level leaves a trait behind and the composed character is enough —
  there is deliberately no `appearanceShownFor`.
- **The reaction column is not derivable from the cost, and both are read.**
  The good half doubles its cost per step (1/4/8) while its modifier climbs by
  one, and the bad half skips −12 entirely, so −8 pts gives −2 and −16 gives
  −4. Deriving one from the other is the tidy wrong answer the wealth ladder
  already punished.
- **The costs are this book's and a reviewer must not "correct" them.** The
  note under the table says so: "Appearance point costs have been adjusted for
  the Pathways setting and differ from standard GURPS values." Attractive is 1
  point here and 4 in GURPS 4e. `NOTE_PHRASE` pins the sentence.
- **Reaction modifiers are shown per source and NEVER summed.** This book
  prints no reaction roll procedure and no result table anywhere, while at
  least five things hand one out — this table, wealth's Status column,
  Charisma, Reputation and Unsettling Appearance. With no printed rule for
  combining them a single total would be arithmetic the book never asked for.
  Same shape as a called shot's wounding multiplier, quoted beside the damage
  rather than multiplied into it.

**Codex did not review this slice** — the account hit its usage limit
(resets 2026-08-08), and the review gate section 8 relies on simply did not
run. Standing in for it, the source page was re-read end to end to check for
the defect class Codex keeps finding (a rule printed and not applied): lines
714–744 print reaction rolls and nothing else — no Influence-skill or Sex
Appeal tie — so nothing on the page goes unapplied. That is a weaker check
than a real review, and this slice should be re-reviewed when Codex is back.

**The device found the one defect anyway**: `pointsLabel` printed a flat
"pts", so Attractive read **"+1 pts"** where the book prints "+1 pt". Wealth
never reached it — its smallest magnitude is 5 — and no test asserts prose.
The helper takes the singular now.

Device-verified on Academic Researcher: the row reads "Ordinary — the book
prints no level for it" with "As printed ✓" in the picker; all eight levels
render; choosing **Ugly** moves the budget from **95 of 100** to **95 of 108**
and reads "Ugly — −8 pts, reaction −2" while the Wealth row stays on Poor,
which is the two choices not colliding. `appearance: "Ugly"` survived a
force-stop, and "As printed" put it back to 100 with `appearance: null`.

Chapter 3 is now at **70.3%**. Its remaining third is the mundane advantage
and disadvantage effect tables, plus the Languages tables directly above this
one (global 690–713) — common and mystical proficiency, whose Broken/Accented
rows print "Rituals using the language at −2" and "Cannot perform rituals in
the language". Those have a caller already waiting in `RitualResolution`, the
same way this table had one in `PointBudget`.

## 6x. Languages — two tables that print the same three row names

`Languages` (core/rules) reads Ch. 3's language section, and
`RitualResolution.targetFor` gained a `language` parameter. The caller really
had been waiting: the mystical Accented row prints "Rituals using the language
at −2" and nothing had ever read it, so a ritual rolled at full skill in a
tongue the caster barely speaks.

- **Native / Accented / Broken are printed TWICE at different prices** — 6/4/2
  common, 30/20/10 mystical — and nothing inside a row says which table it
  came from. Only the header above it does. Keyed on the row name, a mystical
  Native costs 6 points, a fifth of the book's price. So the two are parsed as
  two blocks split on their section headers, every lookup takes a type, and
  `verify()` fails if a proficiency ever stops being dearer as a mystical
  language — the shape a silent merge would take.
- **Only the mystical table speaks about rituals, and a common row's silence
  is not permission.** Reading it as +0 would let a ritual be cast in everyday
  Loen on the strength of the book saying nothing. `ritualModifier` refuses
  both refusing cases and says which is which: Broken because the book forbids
  it outright, a common tongue because the book never grants it. Same
  distinction `Wounds.multiplierFor` draws between an unlisted damage type and
  a key that is not a damage type at all.
- **"Cannot perform rituals" is a refusal, not a penalty.** A modifier there
  would imply a roll exists to modify, so `Broken` carries `false` and no
  number — charging no penalty is not the same as charging zero, the
  distinction `RitualCritical.corGained` already draws. On screen the chip is
  still OFFERED and then withdraws the Roll button, because a caster who only
  speaks Broken needs to see why there is no roll.
- **The Accented row names its penalty and never says the caster may cast.**
  Requiring the words "can perform" as well left the row unreadable — the
  extract died on it. Printing a ritual modifier IS the permission.
- **The −2/−3 to "language-dependent skill rolls" is deliberately NOT
  applied.** This book tags no skill as language-dependent, and the two whose
  names look like it — Body Language (Per) and Linguistics — are exactly the
  ones a name match would wrongly catch. Which rolls it touches is the GM's
  call, like a reaction modifier. Only the ritual clause is scoped tightly
  enough to apply.
- **The cost rules stay sentences.** "Spoken-only or written-only costs half"
  and "Language Talent halves all costs (rounded up)" interact, and the book
  combines them in no worked example. Nothing buys a language yet, so a
  calculator here would be arithmetic with no printed total to check it
  against.
- **Chapter 3 cross-references this section and then disagrees with it
  twice.** Unlike Appearance, whose levels the trait lists print zero times,
  "Language (specify) / varies / See the Languages section for cost tables"
  has pointed at these numbers since M0 — the pointer is what makes them
  reachable from a billable trait, and `verify()` fails if it goes. Then
  `Multilingual` says "two additional languages" where the section says "two
  COMMON languages … it does not apply to mystical languages", and
  `Dead Language Fluency` names **Ancient Loen**, which neither table prints.
  Both recorded, neither repaired, and a test asserts the Multilingual row
  still fails to mention "common" so a later "fix" cannot erase the conflict.
- **Chapter 13's boons deliberately take no language**, the same way they take
  no Phase I preparation: the chapter prints its own modifier list and names
  neither, "because the actual power comes from the patron, not the ritual
  itself" (6r).

Three parsing notes, all of which failed first: the Mystical section prints
three lines of intro prose whose last line ("resonance.") is short enough to
look like a language name, so rows are anchored on the table's first real
name; "Spoken-only or written-only costs" ends one line and "half." starts the
next **across a page break**, so the rule exists only in the joined intro; and
the closing footnote, left in the stream, became the tail of Broken's effect
cell — the app would have quoted a trait footnote as a rule about speaking
badly. It is found by its own shape, pricing traits inline as "(10 pts)",
which no effect cell does. Balam's note wraps onto "similar", seven characters
and indistinguishable from a name, which is why a row needs its NEXT line to
be long too.

**Codex did not review this slice either** — still inside the usage limit that
stopped 6w (resets 2026-08-08). Standing in for it, the callers of
`RitualResolution.targetFor` were enumerated rather than assumed: `Boons` is
the only other one, and its omission is now documented in its KDoc instead of
being invisible.

Device-verified on Academic Researcher (Ritualistic Magic 9, CoR 4): a Light
ritual reads **"3d6 ≤ 8"** at "Not stated" and at Native; **Accented** takes it
to **"3d6 ≤ 6"** and adds "−2 for an accented tongue" to the breakdown, with
the book's own effect cell quoted underneath; **Broken** shows "Cannot perform
rituals in the language", replaces the target with "No roll — the book forbids
this attempt", and **withdraws the Roll button entirely**, so no SPI is
charged. Going back to "Not stated" restores 8.

Chapter 3 is now at **72.8%**. What is left there is the mundane advantage and
disadvantage effect tables.

### A note on Codex

It has found one real defect per slice for six slices running, but it reads
files through PowerShell and mis-decodes UTF-8: on this slice it reported the
`≤` and `—` in `RitualDialog.kt` as mojibake and claimed a P1 build break. The
module compiled clean and the device rendered `3d6 ≤ 5`. Treat encoding
complaints as tooling noise; check `./gradlew` before acting on them.

---

## 7. Global rules the owner has set

From the owner's private global config. **Some are Claude Code-specific and do
not transfer to Codex** — marked below.

### Universal (apply to any agent on this repo)

- **Commits:** NEVER add the agent as co-author. No `Co-Authored-By:` trailer,
  no "Generated with ..." line. This overrides any default harness behaviour.
- **Conventional commits:** `<type>: <description>` with an optional body.
  Types: `feat, fix, refactor, docs, test, chore, perf, ci`.
- **Immutability:** create new objects, never mutate in place.
- **KISS / DRY / YAGNI.** No abstraction with one implementation. No config for
  a value that never changes. No scaffolding "for later".
- **Many small files > few large.** 200-400 lines typical, 800 max. Functions
  under 50 lines. No nesting deeper than 4 — use early returns.
- **Naming:** `camelCase` values/functions, `PascalCase` types,
  `UPPER_SNAKE_CASE` constants, booleans prefixed `is`/`has`/`should`/`can`.
- **Errors handled explicitly at every level.** Never silently swallow. Validate
  all input at system boundaries.
- **Security before any commit:** no hardcoded secrets, validate user input,
  parameterized queries, no sensitive data in error messages.
- **TDD is mandatory:** write the failing test (RED), watch it fail, minimal
  implementation (GREEN), refactor. **80% coverage minimum.**
- **Research before implementing:** search GitHub (`gh search repos` /
  `gh search code`) and library docs first; prefer porting a proven approach
  over net-new code. Check package registries before writing utilities.
- **Code review after writing code**, before any commit to a shared branch.

### Claude Code-specific — ignore or adapt

- *Route all file edits through the opencode MCP* (`mcp__opencode__opencode_run`,
  provider `opencode`, model `deepseek-v4-flash-free`). Codex has no such MCP;
  edit directly.
- *Use the sequential-thinking MCP before acting.* No equivalent; reason
  normally.
- *Sub-agent debate:* for any decision, design, plan, or trade-off, spawn 3
  parallel agents with distinct stances (proposer / skeptic / pragmatist), read
  their outputs, converge in at most one more round, then act. Skip for trivial
  or mechanical prompts. Adapt to whatever parallelism Codex offers, or take
  the multiple perspectives yourself in one pass.
- *Named agent roster* (`planner`, `architect`, `tdd-guide`, `code-reviewer`,
  `security-reviewer`, `build-error-resolver`, `kotlin-reviewer`, ...) —
  Claude Code's `~/.claude/agents/`. Not portable.
- *Delegation completion contract:* if you delegate, you own collecting the
  results. Never end a turn with "waiting for background agents" — a spawned
  task is not a completed task. Worth honouring under any harness that has
  background work.
- *Memory:* the owner runs a Memento MCP store plus a file-based memory at
  `~/.claude/projects/.../memory/`. Not available to Codex.

### Response style (owner's active session hooks)

- **Caveman mode:** terse prose. Drop articles, filler, pleasantries, hedging.
  Fragments fine. **Code, commits, and security warnings are written normally.**
- **Ponytail mode:** lazy-senior-developer discipline. Stop at the first
  solution that holds: does it need to exist at all → stdlib → native platform
  feature → already-installed dependency → one line → minimum code that works.
  Mark deliberate simplifications with a `ponytail:` comment naming the ceiling
  and the upgrade path. Never simplify away input validation, error handling
  that prevents data loss, security, accessibility, or anything explicitly
  requested.

---

## 8. Process this project was built with

Claude Code's `superpowers` skills. The artifacts they produced are in
`docs/superpowers/` and remain authoritative even though the skills themselves
are not portable.

| Skill | Produced |
|---|---|
| `brainstorming` | `docs/superpowers/specs/2026-07-25-pathways-companion-design.md` |
| `writing-plans` | `docs/superpowers/plans/2026-07-25-m0-m1-engine.md` (14 TDD tasks) |
| `subagent-driven-development` | fresh implementer per task, review gate after each, broad whole-branch review at the end |
| `finishing-a-development-branch` | verify tests -> present merge options -> execute -> clean up |

**Codex is the review gate now** (section 7 lists the invocation). In M4 it
found one P2 per slice, both in app wiring rather than in rules: a
Will-lowering potion that never latched the corruption loss it caused, and an
FP row that printed "every point below 0 costs 1 HP" without charging it.
`app/src/test` exists because of those two.

**The single most valuable lesson:** review gates caught defects the tests
could not — 7 in M0/M1, 6 more in the hardening branch, including two that
every existing test passed both before and after. When a fix lands, verify the
new test **fails against the old code**. This codebase has already produced one
regression test that passed under the bug it was written to guard.

Two of the plan's own tasks were mis-specified and only caught at execution
time: Task 10 was described as needing hand-curation when the content was
machine-parseable, and the plan's prose said `floor(...)` while its code used
`/`. Treat the plan as evidence, not scripture.
