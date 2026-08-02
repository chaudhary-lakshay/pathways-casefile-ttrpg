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
| Tests | **504 Kotlin** (JUnit 5, incl. 44 in `app/`) + **74 Python** (pytest), all green |
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
`SkillRoll`, `SuccessRoll`, plus `content/ContentLoader` reading twenty-seven JSON
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
`major_wound.json` (3).

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
ContentLoader.majorWound()
SkillRoll.at(label, base, key, source, mods, ctx): SkillRollOutcome
FrightCheck.against(will, modifier, source): FrightOutcome   // two rolls
FrightCheck.effectFor(roll) / .text(effect, kind) / .triggers()
RitualResolution.attempt(skill, weight, cor, source): RitualOutcome
RitualResolution.targetFor(skill, weight, cor) / .criticalFor(roll) / .weights()
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
- **Weapons never needed a table.** This book prints no weapon list at all —
  parry is `Weapon Skill / 2 + 3`, so the number follows the skill. That
  retires the "weapons in `SavedEdits`" item: what was actually missing was
  the pairing, not the data.
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
  combat skill and prints no weapon table, so which of them can attack is the
  GM's call; curating the list here would be inventing content.

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
- **Two rows have no number this app can know.** "+ weapon's Acc" — and this
  book prints no weapon table. Reading the first signed integer would have
  called the second Aim row +2. They are shown as prose and refused by
  `modifiers`, because applying zero is not the same as having no value.
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
- **Only the four abbreviations the book prints exist.** "Cr", "Cut", "Imp"
  come from the Neck cell and "pi" from the Vitals cell. GURPS writes the
  other two piercing rows "pi-" and "pi+"; this book never does, so they carry
  no key and cannot be looked up.
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

**Still ahead in M4:** shields. Also unmodelled: Feint's Quick Contest, and
Aim's ACC.

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
