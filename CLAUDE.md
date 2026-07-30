# Pathways — Agent Handover

Handover for a fresh agent (written for Claude; Claude auto-loads this file).
Everything below is verified against the tree at commit `cf32f27`, not recalled.

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
| Tests | **404 Kotlin** (JUnit 5, incl. 22 in `app/`) + **37 Python** (pytest), all green |
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
`PointBudget`, `Corruption`, `FrightCheck`, `Pools`, `PotionConsumption`, `PotionDigestion`,
`Premades`, `RitualResolution`,
`RandomSource`, `SecondaryCharacteristics`, `SheetComparison`, `SkillLadder`,
`SkillRoll`, `SuccessRoll`, plus `content/ContentLoader` reading fifteen JSON
files from `core/rules/src/main/resources/content/`: `skills.json` (158),
`advantages.json` (99), `disadvantages.json` (99), `thrust_swing.json` (30),
`pathways.json` (88), `acting_method.json` (5), `potion_consumption.json` (11),
`cor_gained.json` (6), `ritual_cor_thresholds.json` (4),
`pool_thresholds.json` (9), `fright_triggers.json` (5),
`fright_effects.json` (9), `ritual_weights.json` (3),
`ritual_failures.json` (4), `ritual_critical.json` (9).

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

- **Only the potion walk persists.** `SessionStore` writes each premade's
  `SavedSession` (pathway, Sequence, digestion, CoR, both losses) to
  `filesDir/sessions.json`; `BuilderScreen`'s attribute edits are still
  composition-only. The 23 premades are read-only, so there is no edited
  character worth saving yet — that is the prerequisite for editing
  skills/traits in the builder.
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

**Blocked, and it is one blocker, not three:** the 23 premades are mortals
with no weapons and no Ritualistic Magic skill. That means wiring `ParryBlock`
to the sheet shows nothing, and a ritual screen has no caster to hang on.
`RitualResolution` and `ParryBlock` are tested and ready; **an editable
character is the prerequisite for both**, and for the rest of T2 combat.
`ModifierResolver` still has no production caller — combat is where it earns
its keep, with `Corruption.ritualPenalty` and `Pools`' penalties as its first
ready-made inputs.

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
