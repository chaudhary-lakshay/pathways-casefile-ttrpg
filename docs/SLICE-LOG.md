# Pathways — Slice Log

Split out of `CLAUDE.md` (it was 497 KB, over the 150 k-char context limit).
Sections 6a-6ch, in the order they were written. **Read from the END.**
The handover header, repo map, non-negotiable rules and process notes stay in
`CLAUDE.md`.

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

**Correction (6aq):** it cannot be bought. Ch. 9 p. 85 forbids raising any of
the three SPI-based skills with points, and the builder no longer offers
them. The gate is real and the screen is unchanged; what reaches it is a
POTION, which is the book's own route — fifteen Sequence blocks grant one of
the three. Every "bought in the builder" in this section is how the slice was
verified, not how the feature is reached.

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

Device-verified on Academic Researcher with Divination Arts bought at 1 point
(a purchase 6aq later established the book forbids; a Fool Seq 9 potion is the
route now): the skill reads **-2**, because it is SPI/Hard and a non-Beyonder
has SPI 0 — the book gating divination on spirituality, arithmetically. *That
last clause is wrong: the gate is PRINTED, not arithmetic. See 6aq.* A Trivial question
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

## 6y. The two ritual modes — one table the app had been using for both

`RitualMode` (core/rules) reads Ch. 7's Section II and the Quick Ritual's own
modifiers. The slice exists because the book prints **two** preparation tables
and the app knew about one.

- **Excellent is +2 in Quick mode and +3 in Full, and the app used +3 for
  everything.** `ritual_preparation.json` is Phase I's table, extracted in
  M4's ritual slice; the Quick Ritual prints its own three-row table on the
  previous page. Every routine ritual has therefore been rolling a point
  higher than its own table allows since that slice shipped. Neither is
  repaired — they are two modes and the book says so.
- **They agree that Adequate is +0**, which is what makes this one
  disagreement a real difference rather than two unrelated ladders. `verify()`
  asserts BOTH halves: that Excellent still differs and that Adequate still
  matches. Straightening either would erase the distinction silently.
- **The target link is counted once in each mode, in different places.** Quick
  says "Sum the following two modifiers: 1. Preparation Quality 2. Target
  Link". Full lists "Link to target" among the declarations feeding "a single
  Preparation Modifier", and its rows say so themselves — Excellent is
  "perfect link", Poor "weak link". So a full ritual that also added the link
  would pay a perfect one twice, **+8 on a 3d6 roll**. `modifierTotal` refuses
  it rather than summing, and refuses rather than ignoring: silently dropping
  it would leave a GM believing the link was counted. `verify()` pins the
  full table's link phrasing, because it is the entire evidence.
- **Section II's supporting skills are NOT scoped to a mode.** They sit above
  the split and mention neither, so both take them — the opposite call to the
  link, and made for the same reason: scope follows what the book says, not
  what looks symmetrical. Five skills grant +1 each and a sentence caps the
  total at **+3**, so the cap is the whole reason the table is not addition.
- **The cap lives in Kotlin, not in the JSON.** It is a number inside a
  sentence, the same split the maneuver table's +4/−4/−6/−2 take (6i);
  committing it as a column would make it look like the book printed one. A
  test asserts `"cap"` is absent from the file.
- **An unknown supporting skill is refused, not skipped.** A caller passing
  "Occultsm" would otherwise lose a point in silence. Naming one twice pays
  once — a character has one Occultism.
- **`targetFor` defaults to `RitualMode.FULL`**, because that is the table
  every existing caller was already passing rows from. The new parameter
  therefore moves nothing until a caller asks for Quick.

The stale-state trap this created is the one the ranged dialog already found
(6k): `preparation` and `link` are both `remember(mode)`, because a Phase I row
left selected after a switch to Quick would apply +3 inside a quick ritual —
and a link left over after a switch to Full would make `modifierTotal` **throw**
rather than merely mislead. Device-verified in both directions.

Also corrected: `targetFor`'s KDoc said "preparation is null for the quick
mode, which the book resolves without Phase I at all". Quick mode has its own
preparation table; that sentence was wrong from the ritual slice onward.

**Codex did not review this slice either** — third in a row inside the usage
limit (resets 2026-08-08).

Device-verified on Academic Researcher (Ritualistic Magic 9, CoR 8): **Full**
shows the five-row Phase I table, no link row, and "3d6 ≤ 8". **Quick** swaps
in the three-row table (Excellent **+2**) and reveals Link to target;
Excellent plus a Perfect link reads **"3d6 ≤ 14"** with "preparation +2, +4
perfect link, −1 for 8 CoR" spelled out. Switching back to **Full** clears
both — link row gone, preparation back to Adequate, target back to 8, no
crash. The Roll button is below the fold on a 1080px phone and reachable by
scrolling, which is what that dialog's `verticalScroll` was added for.

Chapter 7 is now at **25.9%**. Still unread there: Section IV (power sources —
HP sacrifice at 2 HP per SPI, multiple participants at +1 each to a max +3,
external sacrifice at +0/+2/+4, catalysts at +0 to +2) and the **Entity
Response Table**, plus the Time & Complexity table (Rushed −3 to −5, Extended
+1 to +2) and the suspension rule's −2. All are real roll modifiers with a
caller already built; they are the next slice of this chapter.

## 6z. Invocation — a minus sign that means the opposite of what it looks like

`Invocation` (core/rules) reads Ch. 7's Entity Response Table, and the ritual
dialog grew a Power source row. A ritual could already be fuelled; now it can
be *borrowed*, which is what the book calls "the most common method for
low-Sequence Beyonders".

- **"Orthodox deities shift the table result by −2" lands on the GM's 3d6, not
  on the ritual roll.** The table runs best-first — 3-5 is a Full response
  worth +5, 18 is Active rejection — so subtracting 2 walks a favoured caster
  TOWARD the good rows. It is a reward. Read onto the ritual roll it is a
  penalty, and a Nighthawk praying to the Evernight Goddess ends up worse off
  than a stranger. One sentence, two opposite outcomes, and the wrong one
  looks perfectly plausible on screen. `shiftedRoll` therefore takes a roll
  and returns a roll: nothing in this object hands back a number that could be
  added to a skill by mistake.
- **`verify()` pins the direction, not just the value.** It asserts the table
  is still ordered best-first, because that ordering is the entire reason a
  negative shift helps — if the order ever broke, the sign would need
  rereading rather than reusing.
- **The shift is applied BEFORE the row is read.** It moves the roll, not the
  result; reading the row first and adjusting afterwards answers from the
  wrong band entirely. Pinned by a test where the two orders differ: a 9 is
  Minimal (+1), and shifted to 7 it is Partial (+3).
- **Two rows print no modifier for two different reasons.** 12-14 is an entity
  that does not answer — the ritual proceeds unaided, so nothing is added.
  18 is a refusal: the ritual fails whatever the caster rolled, exactly as a
  Broken mystical language forbids the attempt rather than pricing it (6x).
  `ritualModifier` answers 0 for the first and REFUSES the second, and
  `EntityAnswer` carries the failure in `failsRitual` rather than as a large
  negative a caller might add to a target and roll against anyway.
- **The shift floors at 3.** A rolled 3 is already the best row and shifting
  it to 1 would land on no row at all. No ceiling is needed — it only moves
  down.
- **A roll outside 3..18 is refused, not clamped.** A caller holding a 2 has a
  bug in its dice, and answering "Full response" would hide it.
- **The −2 lives in Kotlin, not the JSON** — a number inside a sentence, the
  same split as the +3 support cap (6y) and the maneuver table's +4/−4. A
  committed column would also lose the one fact that matters most about it:
  which roll it lands on. A test asserts `"shift"` is absent from the file.
- **The secret roll is deliberately not modelled.** The book has the GM roll
  this unseen; this app is one screen held by one person, and hiding a roll
  from its only viewer is theatre. A GM can roll here and not say what came up.
- **The answer is cleared whenever the declaration changes**, because an
  answer belongs to the entity it was asked of — the same rule the Major
  Wound's HT roll follows (6n).

**Codex did not review this slice either** — fourth in a row inside the usage
limit (resets 2026-08-08).

Device-verified on Academic Researcher (Ritualistic Magic 9, CoR 8): Personal
SPI is the default and shows no table. Under Invocation a first ask rolled
**7 — Partial, +3**, taking the target from 8 to **11**. Ticking "Orthodox
deity, in good standing" cleared the answer; re-asking showed **"Rolled 7 → 5:
Full response. +5"** — the same 7 now reading a better row, which is the whole
point — and **"Rolled 15 → 13: No response"** landed on the indifferent row
with the target unmoved. The 18 rejection was NOT observed live (1 in 216, and
good standing shifts away from it); it rests on `InvocationTest`, the same way
the ritual CoR charge rests on its own tests (6f).

Chapter 7 is now at **32.9%**. Still unread: power sources A-E — personal
SPI's four-row complexity ladder (2-3 / 4-6 / 7-10 / 11-15+, which does NOT
line up with `ritual_weights.json`'s three rows and needs that decision made),
HP sacrifice at 2 HP per SPI, participants at +1 each to a maximum +3,
external sacrifice at +0/+2/+4 plus +2 per sentient being, and catalysts at
+0 to +2 — plus the Time & Complexity table (Rushed −3 to −5, Extended +1 to
+2) and the suspension rule's −2.

## 6aa. Power sources — and the SPI table the app had been charging from

`PowerSources` (core/rules) reads Ch. 7's Section IV.A-E and the Time &
Complexity Reference above it. The slice's whole weight is one decision, and
it was made before a line of the extractor was written.

**The decision: two axes, not two printings and not a disagreement to record.**
Section IV.A prints a four-row SPI ladder (2-3 / 4-6 / 7-10 / 11-15+) against
`ritual_weights.json`'s three (Light 2 / Moderate 4-6 / Heavy 8+). Only
Moderate agrees. The book's own worked rituals settle which one it uses:

| Section VIII | Category → weight | Weight column | Charged |
|---|---|---|---|
| Ritual 2, Divination | Light | 2 | **3** |
| Ritual 3, Summoning | Heavy | 8+ | **7** |
| Ritual 4, Curse | Moderate | 4-6 | **7** |

Three fall outside the weight column; all of them fall inside the ladder.
(**Correction, made in 6ab:** this section first said "six worked rituals" and
"six of six". Section VIII prints **ten**, seven of them costed. Rechecked
against all seven the conclusion holds and is stronger — seven of seven inside
the ladder, three of seven outside the weight column, none the reverse — but
the denominator was wrong and the count is now asserted by a test rather than
recalled.) Every parenthetical the book gives to justify a cost is written in
the LADDER's vocabulary, never a weight name — "3 SPI (simple category, single
fact)", "4 SPI (moderate duration, single target)". So the WEIGHT is the
ritual's category and gives its base difficulty modifier; the COMPLEXITY is
its scope and gives the SPI cost, the base time and the pacing. Section V's
sentence — "three weights that determine its base difficulty and SPI cost" —
loses its second half to the book's own examples, the way the Major Wound
sentence lost to its worked example (6n).

- **Nothing is merged and nothing is straightened.** `ritual_weights.json`
  keeps its printed cost column, and `extract_power_sources.verify()` FAILS if
  the two tables ever reconcile at either end — or if they stop agreeing on
  Moderate, because that single agreement is what makes the other two a real
  difference rather than two unrelated ladders. **The app charges fuel from
  the complexity now**; `RitualResolution.spiFor` still reads Section V's
  column, because refusing to read a table the book genuinely prints would be
  its own kind of repair.
- **The Time table is the evidence that the ladder is an axis.** It keys Base
  Time, Rushed and Extended on FOUR names — Light, Moderate, Heavy, **Major** —
  and Section V prints no Major at all. That fourth rung is a tier the weight
  table cannot express, and it is exactly why Major carries no base difficulty
  modifier. The two tables are joined by POSITION, which is defensible only
  because two rows also agree by NAME at the same index; `verify()` pins that,
  the same standard the appendix's matching "Pi" had to meet (6v).
- **"Each assistant beyond the first" reads two ways and the sentence cannot
  settle it.** Beyond the first assistant, two helpers are +1; beyond the
  first PARTICIPANT — the caster — they are +2. Ritual 3's modifier table
  prints "Two trained assistants **+2**", so every assistant counts. Nothing
  else in the chapter disambiguates it, so `verify()` pins that worked row;
  the natural reading would have every ritual with helpers rolling a point low.
- **HP sacrifice is fuel and reaches the roll nowhere.** "Every 2 HP spent
  counts as 1 SPI toward the ritual cost" is followed one line later by "HP
  sacrifice does not grant a bonus to the roll — it only provides spiritual
  fuel." A fuel rule printed directly above a list of roll modifiers is the
  exact shape that gets turned into one, so there is deliberately **no HP
  field on `PowerSourceDeclaration` at all** — a caller cannot route it into
  `modifierTotal` by mistake — and a test asserts that absence. 2 HP buys 1
  SPI with `Math.floorDiv`, so 5 HP buys 2 and the odd point is spent for
  nothing; rounding it up would hand out free fuel.
- **A tier and a living offering ADD.** Precious names "sentient sacrifices"
  among its own examples and the next sentence still says "Living sacrifice
  adds +2 per sentient being offered", so one sentient being on a Precious
  altar is +4 and +2. That is the literal reading of two consecutive
  sentences; excluding one from the other needs a clause the book never
  prints. Sentient beings offered with no sacrifice declared are REFUSED
  rather than ignored — the stale-value shape the boon dialog was fixed for.
- **Rushed with no complexity declared is refused, not dropped.** It is -3 at
  one rung and -5 at another, so there is no safe value to assume, and
  silently ignoring it would leave a GM believing the rush was priced. The
  four rung-independent sources still apply without one.
- **Everything else is a number inside a sentence, so it is in Kotlin** —
  +1 per assistant capped at +3, +2 per sentient being, the catalyst's +0 to
  +2, the suspension's -2. Only the ladder and the three sacrifice tiers are
  real tables. A committed column would make the book look like it printed one
  it never did, the split the +3 support cap (6y) and the orthodox -2 (6z)
  already take; a test asserts those keys are absent from both JSON files.
- **Ch. 13's boons deliberately take no power sources**, the same way they
  take no language and no Phase I preparation. The chapter prices the patron's
  involvement itself and its Ritual quality row stands in for the
  circumstances. The genuinely open one is IV.C's assistants, which chapter 13
  neither offers nor forbids — left off rather than assumed, and said so in
  `Boons.targetFor`'s KDoc so the omission stays a decision.

**Codex did not review this slice either** — fifth in a row inside the usage
limit (resets 2026-08-08 16:52; re-tested at the start of this session and
still refused). Standing in for it, the defect class Codex keeps finding — a
rule printed and not applied — was swept for across the whole section: IV.B's
HP now charges through the tracker rather than being displayed and dropped
(`onSpendHp`), and p.66's burning clause ("+1 per SPI, no cap ... the two uses
of SPI are cumulative") is the one line in the range still reported rather
than applied, because nothing in the app rolls-then-boosts yet. Slices 6w-6z
and this one are five consecutive slices of app wiring with no review gate;
they should be re-reviewed together when Codex is back.

Device-verified on Academic Researcher (Ritualistic Magic 9, CoR 8): the two
axes render as two separate chip rows — weight Light/Moderate/Heavy above,
complexity Light/Moderate/Heavy/**Major** below. Choosing Major moved the fuel
chips from 2-3 to **11..18** (the printed ceiling of 15 plus three) and the
pacing row from "Rushed −3 / 15 min / Extended +1" to **"Rushed −5 / 3+ hours
/ Extended +2"**, which is the per-rung reading working. Assistants read
**2 → +2** and **4 → +3**; picking Precious revealed the sentient-beings row
with its own examples, and one being made the sacrifice line read **+6**.
A Heavy/Major ritual, extended, two assisting, Precious plus one sentient, +2
catalyst read **"3d6 ≤ 16"** (9 −4 +0 −1 +2 +2 +6 +2) and **"Costs 10 SPI and
2 HP"** — 11 fuel with 2 HP covering one of it. A rolled 13 succeeded, and the
saved session went `hpLost` **0 → 2**, `spiSpent` **8 → 18** (the ten owed,
not the eleven), `cor` **8 → 10** with `corrupted: true`, the Soul Debt
charging itself on a caster with no SPI pool.

Chapter 7 is now at **41.8%**, from 32.9%. Section VIII's worked rituals — read
here as evidence and not extracted — became 6ab.

## 6ab. The chapter's own homework, marked

`GoldenRitualTest` replays Section VIII's seven costed rituals against the
engine, the way `GoldenSheetTest` replays the 23 character sheets: as a
**partial oracle**, where a disagreement is recorded and neither side is
repaired. The book works its own arithmetic in public seven times, for one
stated caster (Ritualistic Magic-14), and nothing had ever checked it.

**It immediately corrected the slice above it.** 6aa said "six worked
rituals". There are ten — seven with tables, three templates printing ranges —
and the two-axis decision had been argued from the wrong denominator. It
survives the recheck unchanged, which is the good outcome; what it no longer
rests on is my count. Both halves are now asserted in `verify()` and again in
Kotlin, because a decision that outlives its evidence is how the weapon-table
claim survived four sections (6t).

- **Every printed row lands on a printed table.** All six "true name" rows are
  Target Link cells (Perfect +4 twice, Strong's +3 and +2 ends, Weak's +0);
  both "space" rows are preparation qualities (+0 Adequate, +2 Good's better
  end); all three "materials/offerings" rows are Meaningful sacrifices at +2;
  both "assistants" rows are +2 for two, which is the whole evidence for
  reading "beyond the first" as counting from the caster (6aa).
- **The rows nothing covers are named, not skipped.** Four of the thirty-two
  come from no table this book prints — "One month duration -1", "Indefinite
  duration -3", "Single-use object +1", "Both parties willing +0". Chapter 7
  tabulates duration nowhere. Naming them is what keeps the engine's coverage
  honest: it reproduces the printed part and says so.
- **The weight table's MODIFIER column is a summary too.** Curse,
  Oath-Sealing and Fabrication each print a base difficulty of **-3**, and the
  table puts all three under Moderate **-2**; Ritual 8 runs "-2 (minor
  offering) to **-6** (living sacrifice)", past Heavy's -4 entirely. So both
  of Section V's columns are summaries the worked examples grade inside —
  same shape twice. Deliberately NOT turned into a fourth weight: the book
  prints no table of -3s, and inventing one from three examples is authoring.
- **`RitualComplexity`'s join has a counter-example, and 6aa did not know
  it.** That row pairs the Time table's four names with the SPI ladder's four
  rungs by position, so one rung gives both a ritual's base time and its cost.
  **Ritual 6 is Heavy by time and Moderate by cost**: "triple the base time"
  producing 3 hours makes its base an hour (Heavy), its extended bonus is +2
  (Heavy), and its 6 SPI is the Moderate rung. Ritual 7 obeys the join
  exactly, and the other five print no pacing row. Recorded, not repaired —
  one example is not enough to split a row into two axes — but the join is
  now known to be a READING rather than a fact, and the test says where.
- **Ritual 8 names the cheapest sacrifice tier "Trivial"** where Section IV.D
  names it "Ordinary", for the same +0/+2/+4. Two names, one tier, neither
  merged — the way "Eternal Aeon" keeps both spellings (6s). It also rolls the
  Critical Failure Table at **+2** where Section VI's severe misfire rolls it
  at -2; different trigger, opposite shift, and that table is not ordered by
  severity (6d), so neither is "worse".
- **The parse needed the book's own anchor.** The first version joined a
  wrapped label onto the line above with no marker, and ate each ritual's
  Intent and Setup paragraphs into its first modifier row — the arithmetic
  still summed, because the swallowed rows kept their numbers. The table
  prints a "Modifier Source" header; using it is what makes every row inside
  short, valued and real. A test asserts no row contains "Intent:".

Rituals 8-10 are deliberately not extracted — they print ranges rather than a
worked total, so there is no arithmetic to check an engine against. Ritual
10's pathway/material table (17 rows of "Fool / Silver, Paper") is real
content with no caller: nothing in the app creates charms.

**Codex did not review this slice either** — sixth in a row inside the usage
limit, re-tested twice this session and refused both times.

Chapter 7 is now at **81.0%**, from 41.8% — the worked rituals were a third of
the chapter by mechanical line count, which is what a section of nothing but
tabulated modifiers looks like to `coverage.py`. Still unread there: the
12-step canon procedure (reference prose the book itself labels as texture),
Section VII's Sequence notes, and rituals 8-10's templates.

## 6ac. Marking M0's homework — the skill ladder

`GoldenSkillLadderTest` replays Ch. 3's skill cost grid (p. 25-26) and its two
worked examples against `SkillLadder`, which has been production code since
M0. Every skill level the app displays goes through that object — 232 of them
across the 23 sheets — and nothing had ever compared it to the table it was
written from. `SkillLadderTest` checks the behaviour it was given; this checks
the behaviour it was supposed to be given.

**It passes on the code as written**, which is the expected outcome of marking
homework and is still worth having. The value is in what now fails if either
side drifts, and in three things the table turned out to say:

- **The grid is one column SHORT of the ladder.** The cumulative column prints
  nine costs (1 … 256) and every difficulty row prints eight offsets, so the
  256-point rung has a cost and no printed offset. `SkillLadder` continues the
  row by +1, which the prose supports ("each level after the third costs twice
  as much as the level before it") and the grid never shows. A reading, so it
  is pinned by a named test in both languages rather than left to be
  rediscovered by whoever first buys a skill that far.
- **The grid's regularity is what justifies `Difficulty.firstRungOffset`.**
  Each row is the one above shifted down by one and each climbs by one along
  its length, so one number per difficulty stands in for a whole stored row.
  Asserted in both directions — if that ever broke, the offsets would have to
  become data.
- **The ladder is deliberately not GURPS core**, and the test says so out
  loud: core 4e is 1/2/4/8/12/16 with +4 per level past Attr+2, and using it
  would make 14 of those 232 levels wrong. `12 !in cumulative` is the guard.

The parse needed one thing that cost a debugging cycle: **this PDF prints list
bullets as U+007F** (6g already recorded it) and the worked examples are a
bulleted list, so every step regex matched nothing until the control character
was stripped — and the failure looked like "a table with no examples" rather
than like a parse error. The extractor strips it now and a test asserts all
eight steps survive.

Also confirmed while looking: p. 19's "up to -40 points of disadvantages" and
the quirk cap are ALREADY enforced (`PointBudget.MAX_DISADVANTAGES`), so the
unclaimed lines there were coverage bookkeeping rather than a printed rule
going unapplied. Worth checking before assuming otherwise.

Chapter 3 is now at **84.7%**, from 72.8%. What is left there is the mundane
advantage and disadvantage effect tables, and the study/training system on
p. 32 — tutors, study points, and "a skill cannot exceed Attribute+1 through
study alone", which is a real subsystem with no caller in the app yet.

**Codex did not review this slice either** — seventh in a row inside the usage
limit.

## 6ad. Degrees of success — when an appendix is allowed to define a rule

`DegreeOfSuccess` (core/rules) reads Appendix A's seven-row ladder (p. 159)
and the skill-roll dialog now grades a success rather than only reporting
one. 6v said this appendix's success ladder was "genuinely new material and a
slice of its own"; this is that slice, and its whole weight is one question.

**The question: 6v also established that "a summary does not overrule the
chapter it summarises", and this is the same appendix that gets chapter 5
wrong twice.** So why is this table adopted at all?

- **Because it is not summarising anything.** "Good Success" and "Excellent
  Success" appear at global lines 9680 and 9682 and NOWHERE else in the book —
  a test asserts that, and it is the assertion that would fail first if a
  chapter turned out to print them. Ch. 2 line 278 says "Margins matter — the
  degree of success or failure often determines quality of outcome" and
  tabulates nothing, then five Sequence abilities scale their effect by "the
  degree of success" (4679, 9419, 9556, 9595, 9625). The chapters ask; only
  this page answers. Same shape as ch. 3's "See the Languages section for cost
  tables" (6x): a cross-reference is what makes another page reachable.
- **The warrant is per-table, never per-appendix.** "The appendix is reliable"
  is exactly the generalisation 6v disproves. Five of these seven rows repeat
  rules `SuccessRoll` has implemented since M0 — 3-4 always succeeds, 17-18
  always fails, roll ≤ target succeeds, roll > target fails, roll ≥ target+10
  is a critical failure — and `verify()` checks all five BY VALUE, in both
  languages. That exact agreement is the only reason the two unrepeated rows
  are adopted, and it is the standard the appendix's matching "Pi" had to meet
  in 6v. The fail-by-10 row is written from the target's side where
  `SuccessRoll` writes it from the margin's; asserting they meet is what
  proves it is the same rule and not a second opinion.
- **It is deliberately NOT part of `Outcome`.** Good and Excellent are
  sub-bands of a success, and six production sites compare `== Outcome.SUCCESS`
  (`Divination`, `FrightCheck`, `RitualResolution`, `Wounds`, `SkillRoll`,
  `MainActivity`). Widening that enum would make a caster who rolled *well*
  stop counting as having cast the ritual — a defect that would look like a
  dice problem. The degree is a separate object for that reason alone.
- **Outcome first, margin second — and the device showed it.** A rolled 3 or 4
  is a critical success by the always-rule whatever its margin, and the table
  prints that row ABOVE the ladder. Grading by margin first reports the book's
  best row as an ordinary success. This is the slip `FrightCheck`,
  `RitualResolution` and `Wounds` each made once; a rolled 4 against 13 has a
  margin of 9, which is Excellent by margin alone, and the screen correctly
  reads **Critical success**.
- **The roll-under bands OVERLAP, so the LAST match wins.** A margin of 5
  satisfies "≤ Target", "≤ Target −3" and "≤ Target −5" at once. The book
  prints them least- to most-demanding; taking the first match would report
  every success as plain and leave the two new rows loaded, tested and
  unreachable. A test asserts a margin of 10 really does satisfy all three.
- **Scoped to plain skill rolls.** Divination reads clarity off its own margin
  table, and `RitualResolution`, `FrightCheck` and `Wounds` each grade against
  a table their own chapter prints. Specific beats general — the call the PCR's
  critical rules and the ritual CoR ladder already needed — so none of them
  layers this general ladder on top. Pinned by a test asserting the divination
  clarity rows are not named "Good Success"/"Excellent Success".
- **`SkillRollOutcome.degree` reads `effectiveOutcome`, not `roll.outcome`.**
  A roll demoted by `requiresCritical` is displayed as a failure, and grading
  its raw margin would print "Good success" beside the word Failure. Exposing
  the degree as a property is what stops a caller having to remember which of
  the two outcomes to pass.

Deliberately not extracted from the same page, both their own slices:
**"Common Modifiers"** (9725-9745) — nine rows of GM circumstance, easy task
+4 or more, adverse −4, improvised tools −2 to −4, aided +2 — which is a
fourth modifier source beside `Conditions`, `Attacks` and `Ranged` with the
caller already built, and deserves the ranges-and-scope treatment those got;
and **"SPI Recovery"** (9718-9724), one row of 1 SPI per hour of rest, where
nothing in the app recovers SPI and nothing holds a clock. The difficulty grid
at 9634-9664 restates ch. 3's ladder, already carried from the chapter (6ac).

**Codex refused for the eighth consecutive slice** (same usage limit, resets
2026-08-08 16:52). This is the first slice where the documented fallback was
taken rather than the gate simply being skipped: one `ecc:kotlin-reviewer`
pass over the real diff, primed with the defect class Codex keeps finding.
It returned **zero findings** across all nine files and confirmed `.degree`
has exactly one call site. That is weaker than Codex — it did not catch
anything, and six slices running Codex did — so 6w-6ad still want a real
review when the limit resets.

Device-verified on a physical tablet, Academic Researcher, Research 13: margins
0/1/2 read **"Success"**, margin 4 **"Good success"**, margins 5 and 7
**"Excellent success"**, and a rolled 4 at margin 9 **"Critical success"**.
The margin-3 boundary itself was not observed live (it is bracketed by the
observed 2 and 4) and rests on `DegreeOfSuccessTest`.

Appendix A is now at **8.6%**, from 0.0%. Note that `coverage.py` cannot see
most of what is left there — 6v recorded that a bare `×1.5` counts as no
mechanical line at all — so the remaining 32 are a floor, not a total.

## 6ae. Common Modifiers — the fourth source, and a row that is not one

`CommonModifiers` (core/rules) reads Appendix A's nine-row circumstance table
(p. 159) and the skill-roll dialog now asks before it rolls. It joins
`Conditions` (what the character is carrying), `Attacks` (what the player chose
this turn) and `Ranged` (the distance and the scene); this one is what the GM
says the situation is.

- **"Using untrained skill (default) / Attribute −4 to −6" is not a
  modifier, and the row proves it itself.** That printed range is exactly
  `Difficulty.defaultPenalty`'s two ends — −4 Easy, −5 Average, −6 Hard, Very
  Hard having no default at all — so the row is chapter 3's untrained defaults
  restated, and `SkillLadder.defaultFor` has implemented them since M0.
  Applied as a modifier it would be wrong twice over: stacked on a default
  already taken, and subtracted from the SKILL where the book subtracts from
  the ATTRIBUTE. It is **refused** rather than read as +0, because a caller
  handed 0 would believe the rule had been priced — the same shape as
  divination's "See Step 1 table" row (6p). `offered()` withholds it, so no
  chip can reach it.
- **That same agreement is the table's warrant.** Adopting an appendix needs
  per-table evidence, never per-appendix (6ad, and 6v is why). This is the one
  row here the engine can be checked against, and it agrees exactly. Without
  it there would be no basis for the other eight.
- **It is deliberately NOT offered on a ranged attack.** "Adverse conditions
  (dark, noisy, dangerous) −4" and chapter 5's "Darkness or heavy fog −3 to
  −9" are one circumstance priced twice, generally and specifically. Both on
  one roll charges a dark room twice. Specific beats general — the scoping the
  ritual CoR ladder needed (6h) and that keeps chapter 13's boons off Phase I
  preparation (6r). `verify()` and a Kotlin test both pin that the overlap is
  still really there, because the day it stops being there is the day the
  scoping stops being needed.
- **Only four of nine rows print a single number.** Two print ranges, two
  print an open end, one carries its condition in the cell ("+2 (if they
  succeed first)" — another character's roll, which this app cannot test, so
  it is shown rather than applied). The end nearer zero is offered and the
  other bound validates a GM's pick, the gentle reading the darkness row and
  the ritual preparation ranges already take. An open end bounds only the side
  the book prints: +2 on an "easy task" would be gentler than the book's own
  floor, so it is refused.
- **"Routine conditions / +0" is a real printed row that adds nothing**,
  which is not the same as a row that prints no modifier. It stays selectable
  and drops out at the ledger, like any other zero. The untrained row sits on
  the other side of that distinction.
- **The roll now happens inside the dialog.** It used to fire on the tap that
  opened it, which left nowhere to state a circumstance before the dice were
  cast. Re-rolling as each chip toggles would be worse — the GM would watch
  results scroll past and keep whichever arrived last. Roll goes disabled
  after firing rather than being replaced by Close, so the re-entrancy guard
  is visible instead of implied.

**Codex refused for the ninth consecutive slice** (usage limit, resets
2026-08-08 16:52). One `ecc:kotlin-reviewer` fallback pass, primed with six
named probes: **zero findings**, each traced rather than asserted.

Device-verified on the tablet, Academic Researcher, Research 13: eight chips
render and the untrained row is absent; picking "Adverse conditions −4" plus
"Good tools / equipment" at its **+2** end read **"3d6: 11 vs 11 — base 13,
modified to 11"** with both rows named in the contribution line, and Roll
greyed out afterwards.

**Coverage did not move, and the reason is the instrument's third blind
spot.** Appendix A reads **8.6% with 32 unclaimed, before and after** this
slice. The table is physically on **page 160**, and `chapters.json` gives
Appendix A the range `159-159` and Appendix B `160-161` — so a claim keyed
`"A"` (which is correct: Appendix B's heading does not start until line 9748)
falls outside A's own line universe and is credited to nothing, while B still
reads 0.0%. Appendix A's tables simply spill onto the next page and the
chapter ranges are page-granular. Nothing here is worth repairing — renaming
the claim to `"B"` would file Appendix A's content under the political
gazetteer — but it is the third time this instrument has been wrong in a
different way: 6t (a chapter at 0.0% disproving nothing), 6v (bare
multipliers counting as no mechanical line), and now a table whose page
belongs to the next appendix. **Percentages are a floor and an argument, never
a receipt.**

**A debugging note worth more than the slice.** Roll appeared to dismiss the
dialog instead of rolling, and three rounds of uiautomator dumps and logcat
tails all pointed at the app. Both instruments were lying: **logcat was
filtered wrongly** (an unrelated health app floods this device, so a tail
without `--pid` proves nothing about a crash), and **uiautomator's coordinate
scale differs between the activity window and a dialog window** — the activity
reports in a 1440x900 space against a physical 1920x1200, but a dialog's
bounds do not take the same factor, so a scaled tap landed on the scrim past
the dialog's right edge and dismissed it. One `screencap` answered in a single
step what four dumps could not. For a Compose dialog, **take the screenshot
and read the pixels**; they are already physical coordinates.

## 6af. Sealed Artifacts — a column that looks exactly like a gate

`SealedArtifacts` (core/rules) reads Ch. 6's grade table (p. 47) and the four
documented artifacts (p. 49-53), and `ArtifactDialog` spends what they cost.
This is the chapter the app is *named* after and it sat at **31.2%**; the
artifacts were the part with callers already waiting, because
`BeyonderSession.spend` has charged SPI since M4's pool slice and HP since
6aa.

- **"Power Equivalent (Seq 5-7)" is not a requirement, and it reads exactly
  like one.** The grade table's four columns are Grade / Danger / Power
  Equivalent / **Church Access Rules**, and only the fourth gates anything —
  every cell in it names an institutional RANK ("Formal Nighthawk members and
  above", "Diocesan Bishop or Nighthawk deacon and above"). The power column
  says how strong the ARTIFACT is. Enforced as a wielder requirement it would
  lock a Sequence 9 character out of a gauntlet the chapter says arises "as a
  result of the death of an out-of-control Beyonder" — found on a corpse, not
  issued by rank. So there is deliberately **no `canUse`**, and the screen
  prints both columns with the distinction spelled out, because a sentence is
  cheaper than a check that would be wrong. Same shape as a wealth level's
  reaction modifier (6u) and a called shot's wounding multiplier (6l).
- **The warrant is the artifacts' own headers, not the chapter's
  reliability.** Each prints its own "Seq N equivalent" and all four land
  inside their grade's printed band — Grade 2 gets 7 and 6, Grade 3 gets 8,
  Grade 1 gets 4. Four independent confirmations are what license joining the
  two tables by grade number at all; `verify()` and `SealedArtifactsTest`
  check every one by value, and the join is declared a READING rather than a
  fact. That is the standard 6ad set and 6v is why it has to be per-table.
- **Three of the four artifacts print an HP number in their drawbacks and
  only one is charged.** The gauntlet's Soul's Price is "Each use of any
  ability costs 1 HP in addition to SPI" — unconditional. The flagon's is
  conditional on telling a lie, the candle's Burns True on touching the flame,
  and the coin's Time Debt only bites after three uses in a week. A loose
  "1 HP" rule charges all of them. The two regexes are narrow on purpose and
  `verify()` pins the three uncharged sentences too, so the narrow reading
  stays a decision rather than becoming an accident.
- **The two HP rules are different rules and `costOf` adds them.** The
  candle's Extinguish Memory charges HP in its own effect cell, on an artifact
  with no per-use price; the gauntlet charges per use, and none of its
  abilities charge their own. No artifact prints both today — composing them
  is what stops the first one that does from being charged once.
- **The effects are not modelled and there is no formula to model.** Thirteen
  abilities, each unique: duplicate an object, walk through a wall, erase a
  memory, ask a target to roll HT-2. Only the pools move. The gauntlet's
  "-3 ... dropping by 1 each turn" is a decaying modifier `ModifierResolver`
  could carry, but one instance is not a subsystem.
- **"1 SPI/turn" is carried forward rather than flattened.** False Majesty is
  maintained, so a caller spending it once has underpaid. `ArtifactCost.perTurn`
  travels with the number and the dialog charges one turn per tap and says
  which. Same reason `PoolStatus.scope` exists (6b): the qualifier belongs to
  the value.
- The stale-state trap is the one 6k and 6y already found, one degree worse:
  `ability` is `remember(artifact)`, because an ability left selected across a
  switch makes `costOf` **throw** rather than merely mislead. Device-verified
  in both directions.

**Also fixed here, and it was pure bookkeeping:** the PCR's own formula
("Roll 3d6 <= 10 + Digestion Modifier", "no stat or skill applies") sat
outside `potion_consumption.json`'s claimed span, even though
`extract_digestion.verify()` has asserted `effectiveTarget == 10 + modifier`
since M3. An assertion whose source sentence is unclaimed is a rule nobody can
find again, so the file claims two spans now.

**Coverage's page-granularity bites a fourth time, in a fourth way.** Ch 6
went 31.2% -> **85.4%**, and the 7 lines still unclaimed are Chapter 5's
fright-effects table, which spills onto **page 44** — inside ch 6's page
range. `fright_effects.json` claims them correctly under `"5"`, so
coverage.py credits them to nothing and counts them against ch 6. Ch 6.5 has
the mirror image: the flagon spills onto page 53 and is claimed under `"6"`.
Neither is worth repairing. Recording it because the instrument has now been
wrong four distinct ways — 6t (a chapter at 0.0% disproving nothing), 6v (bare
multipliers counting as no mechanical line), 6ae (a table on the next
appendix's page), and now a table straddling a chapter boundary. **A
percentage is a floor and an argument, never a receipt.** Before reading an
unclaimed count as work remaining, check whether the lines are already claimed
under a neighbour.

**Codex refused for the tenth consecutive slice** (usage limit, resets
2026-08-08 16:52). The fallback did not go smoothly either: the first
`ecc:kotlin-reviewer` pass **stalled out entirely** after 600s with no output,
so a second, narrower one was run over three files with four named probes. It
returned zero findings and traced each probe to a file:line rather than
asserting cleanliness. Slices 6w-6af are now ten consecutive slices without the
real review gate and should be re-reviewed together when it returns — and note
that a broad "review the working tree" prompt is what stalled; scoping the
reviewer to named files is what got an answer.

Device-verified on the tablet, Academic Researcher (a MORTAL, SPI 0 — which is
the interesting case, since the grade table would have locked them out):
all four artifacts render; the Gauntlet reads "Grade 2 (Dangerous)" with
"Power equivalent: Mid-Sequence Beyonder (Seq 5–7) — how strong the artifact
is, not what its wielder must be". `Bribe — Weaken` read **"Costs 1 SPI and
1 HP — the artifact's own price for any use"** and took **HP 9/9 → 8/9**, with
the session going `spiSpent: 1`, `hpLost: 1`, `cor: 1` — the Soul Debt
charging itself. Switching to the Candle reset the ability without crashing;
**Extinguish Memory** read the same shape with "this ability itself" as the
reason and took **HP 8 → 7**, `spiSpent` to 4 and `cor` to 4. The Peerless
Flagon reads **"Costs 2 SPI."** with no HP clause, directly above a drawback
that prints "(1 HP burn to the hand)" — the exact number the narrow reading
refuses.

**The button that spends was changed after that run, and re-verified on an
emulator.** The first version disabled Use permanently once a receipt showed,
so maintaining False Majesty — the book's one "1 SPI/turn" ability, where
repeating IS the printed rule — meant closing and reopening the dialog once
per turn. It reads "Use again" now and the taps ALTERNATE: the first clears
the receipt, the second pays. Nothing charges a pool without the button having
read "Use" when it was hit, which is a stronger guard than the `enabled` flag
it replaced — and the reviewer pass below validated the OLD version, since it
cites the `enabled = used == null` that is now gone.

Verified on `Medium_Phone_API_36.1` (1080x2400), Academic Researcher, session
cleared first: False Majesty's first Use took **HP 9/9 → 8/9** and saved
`spiSpent: 1, hpLost: 1, cor: 1`; tapping **"Use again" left all three
unchanged** and returned the label to "Use"; the next Use charged the second
turn at `spiSpent: 2, hpLost: 2, cor: 2`. **Two rapid taps with no delay
produced ONE spend, not two** (2 → 3 across all three fields), which is the
alternation holding under the input a re-entrancy guard exists for.

Two notes on the emulator, both cost a cycle: a cold boot throws a "System UI
isn't responding" dialog OVER the app, which is SystemUI and not the app under
test; and `uiautomator dump` returned "null root node" while still leaving a
STALE ui.xml on disk to be pulled — so a dump can appear to succeed and
describe the previous screen. Screenshots were the only honest instrument
again, the same conclusion 6ae reached for a different reason.

Deliberately not extracted from the same pages: Charms (10 rows), Mystical
Ammunition (8), Special Medicines and Consumables (~16) — all Name / Domain /
Effect prose with no numbers, which is why `coverage.py` never counted them;
and the Sealing Instructions are committed but nothing acts on them. Ch 6's
remaining mechanical lines are the ch-5 spill described above, so the chapter
is done.

## 6ag. Sequence Immunity — a penalty and a prohibition are not the same rule

`AntiDivination` (core/rules) reads Ch. 6.5's Sec. 5 and Sec. 6, and the
divination dialog grew a "Target's protection" row. This is the per-pathway
detection subsystem 6t named as M4's next slice — or rather the DIVINER's half
of it, which is the half with a caller already built.

- **"-10" and "only a critical success (3-4) can succeed" are two rules, and
  the second is not implied by the first.** Seq 4 and Seq 3 print both. A
  skill-20 diviner at -10 rolls against 10 and an ordinary 10 succeeds — which
  is exactly what "the penalty cannot be offset by skill alone" forbids. So the
  clause travels as `ModEffect.RequireCrit`, which `ModifierResolver` has
  carried and `SkillRoll` has honoured since M1 with nothing ever emitting it.
  A test proves the difference THROUGH the engine rather than by assertion: a
  rolled 9 beats the target, `roll.outcome` is SUCCESS, and `effectiveOutcome`
  is FAILURE.
- **A demoted success must not keep a success CLARITY row.** `Divination` reads
  its clarity from the outcome, so a forbidden success would have printed
  "Clear" beside the word Failure — the defect `SkillRollOutcome.degree` avoids
  one object over (6ad). Its margin is positive, so it lands on the mildest
  failure row, the call `FrightCheck` already makes for a roll that overshoots
  its table by nothing.
- **Seq 2 (Angel) prints -10 and NO critical clause**, while the two weaker
  rows do. Read literally the strongest listed target is the EASIEST of the
  three for a highly skilled diviner. That looks like a slip; 4.1 says the book
  wins, so `verify()` and a named test both assert the asymmetry is still there
  and will fail if a later reading tidies it.
- **Lower Sequence is stronger, so "2+ Sequences stronger" counts DOWN.**
  Reversed, Sequence Advantage lands on the weaker party and still produces
  plausible numbers. And when it applies, the critical clause goes with the
  penalty it names — the clause is *about* the -10, and a diviner who never
  takes the -10 is not bound by a sentence describing it.
- **Two halvings with OPPOSITE printed rounding, on negatives** — the defect
  class 4.3 exists for. Direct Link "halves ... (round down)"; Sequence
  Advantage leaves techniques "at half penalty, rounded up". Both are read as
  halving the MAGNITUDE, because reading "round down" numerically on -5 gives
  -3, which is HARSHER than the -5 it was meant to soften, in a section where
  every other bullet helps the diviner. **The book's only worked halving is
  Seq 5's -6 -> -3, which is even and settles nothing** — the Major Wound trap
  (6n) with the parities swapped. A reading, pinned by a named test.
- **That -6 -> -3 is also the warrant** for treating Sections 5 and 6 as one
  system: the row's own printed link value equals the general halving, so the
  general rule may be applied to rows that do not restate it. Checked by value
  in both languages.
- **Order matters and the book prints none, so printed order is the reading.**
  Halve then forgive gives -3; forgive then halve gives -4. Device-verified
  at -3.
- Sec. 3's active techniques are a PARAMETER, not a table here. They are
  something the TARGET activates and pays SPI for, so they belong with the
  detection half; the counters correctly halve whatever is passed, and 0 is the
  honest default. Sections 1, 2 and 4 — passive awareness, the detection roll,
  and anti-divination items — are the target's side and the next slice.

**Three defects, all the same class: arithmetic on screen that does not
reconcile.** Every number was correct in isolation, which is why no test caught
any of them and why the guard that now exists is a SUM.

- **The device found the first.** The breakdown read *"circumstances -10, Seq 4
  (Demigod) -10"* for a single -10, because `extras` already contained the
  protection penalty and the line printed both. A GM adding it up got -20
  against a target of -10. `circumstances` is computed separately now.
- **The reviewer found two more**, both inside `AntiDivination.against`. The
  parts list was built from the PRE-halved values and then appended "Direct
  Link halves them" with no number; and the HP relief printed the book's stored
  sign, so a step that moves the total TOWARD zero read as "forgives -2". Shown
  together: *"-10 ... forgives -2"* for a total of -3. Parts are now built from
  the final values — *"Seq 4 (Demigod) -5 (halved by Direct Link), 4 HP forgives
  +2"*.
- **The guard is general, not three patches.** A test sums the signed numbers in
  `parts` and asserts they equal `penalty`, across every combination of target,
  diviner, technique, link and HP — 300 cases. It fails against the old code.

**And the reviewer found a real cost defect too:** the dialog charged 4 HP
whenever the chip was ticked, while `against()` correctly declines to forgive
anything when there is no penalty. So burning HP against an unprotected target
cost 4 HP and bought nothing. `AntiDivinationResult.hpSpent` reports what the
sacrifice actually cost and the dialog spends that — the decision lives in
rules, where it is tested.

**Codex refused for the eleventh consecutive slice** (usage limit, resets
2026-08-08 16:52), so this was one `ecc:kotlin-reviewer` fallback pass, scoped
to three named files with seven probes. It returned **three HIGH findings and
five clean probes** — the first fallback pass in this project to find anything,
and a reminder that scoping the reviewer to named files is what gets an answer
(a whole-working-tree prompt stalled out in 6af).

Device-verified on the emulator, Academic Researcher with Divination Arts
bought — a purchase 6aq later established the book forbids, so reproducing
this now means seeding a Beyonder — (skill **-2**, since a non-Beyonder has
SPI 0): the protection chips stop
at Seq 2 where the table does; **Seq 4** quotes the book's own row and reads
**"3d6 ≤ -10"** with the critical-only warning in red; ticking **True name +
personal item** and **Burn 4 HP** reads **"3d6 ≤ -3"** with "Seq 4 (Demigod)
-10, Direct Link halves them, 4 HP forgives -2" spelled out — the documented
order, since the other one gives -4. Asking took **HP 9/9 → 5/9** and saved
`hpLost: 4, spiSpent: 1, cor: 1`, the Soul Debt charging itself. A rolled 17
took Critical Failure — Backlash and rolled Ch. 7's table ("Wrong Attention").
After the review fixes: the same pair of chips reads **"Seq 4 (Demigod) -5
(halved by Direct Link), 4 HP forgives +2"**, which sums to the -3 above it; and
ticking **Burn 4 HP** against **Seq 6–9** left `hpLost` at 4 while `spiSpent`
and `cor` moved — the sacrifice bought nothing, so it cost nothing.

**The demotion itself was NOT observed live.** It needs a diviner skilled
enough that an ordinary roll beats the target, and at Divination Arts -2 no
premade can get there against a -10. It rests on `AntiDivinationTest`, which
exercises it twice — once through `SkillRoll` and once through
`Divination.attempt` — the same way the ritual CoR charge rests on its tests
(6f) and the entity's 18 rejection on its own (6z).

**Coverage's page boundary, a fourth time in one session.** Ch 6.5 went
39.3% -> **48.2%**, crediting only 5 lines: the immunity table starts on page 59
and finishes on page **60**, which `chapters.json` gives to Chapter 7, so
everything from Seq 2 onward plus all of Section 6 is claimed correctly under
`"6.5"` and credited to nobody. Ch 7 reads 81.0% before and after. Not
repaired — **check every chapter's claims before reading an unclaimed count as
work remaining.**

## 6ah. The target's half — a roll the diviner's dialog cannot make

`Detection` and `AntiDivinationTechniques` (core/rules) read Ch. 6.5's
Sec. 1-4, and `DetectionDialog` is where the played character asks whether
someone is watching. 6ag took Sec. 5-6, the DIVINER's half, and left this
deliberately; the seam between them was `AntiDivination.against`'s
`techniquePenalty`, a parameter with no caller. Sec. 3 fills it.

- **The detection roll is the TARGET's, and this app holds one character.**
  In the divination dialog that character is the diviner: the target is a
  declared Sequence with no sheet, so there is no SPI or Per there to roll and
  no amount of UI would produce one. So the roll got its own entry point,
  reachable when the played character IS the target, and what went into the
  divination dialog is Sec. 3 as a DECLARATION about the target. Two
  touchpoints, one chapter, split by whose sheet the numbers come from.
- **One GM fact reaches both rolls with opposite sign, and that is not a
  conflict to reconcile.** "The diviner has your true name + personal item" is
  Sec. 6's Direct Link, which HALVES the diviner's penalty, and it is also a
  -2 on the target's detection roll. Two rolls, two effects, one fact.
  `verify()` pins both so a later reading cannot collapse them into one.
- **Sum the techniques, then halve — and the book's own numbers cannot prove
  it.** Sec. 3 says "All active techniques stack — their penalties add
  together when a divination is attempted", which is the moment Sec. 6's two
  opposite-rounding halvings act, so they are one penalty by then. But only
  3A (-2) and 3B (-4) print a flat penalty and BOTH ARE EVEN, so sum-then-halve
  and halve-then-sum agree on every combination this book can produce — the
  Major Wound trap (6n) with the parities the other way round. `verify()`
  asserts the evenness and fails on the first odd one, which is the moment the
  reading has to be made again rather than inherited.
- **Sec. 1 and Sec. 2 are two axes, because Sec. 1's primary output is a
  GATE.** Its eight rows answer whether there is a roll at all — automatic
  awareness, a free roll, or nothing — and only three also print a bonus.
  Sec. 2's six rows are the situation. Two more sources join them: 3A's ward
  grants "+2 on detection rolls" and Sec. 4's pendant "+2 to detect
  divination", so this roll has FOUR declared sources exactly as a skill roll
  has `Conditions`, `Attacks`, `Ranged` and `CommonModifiers`. The book's own
  Quick Reference is the evidence — it numbers the sections as six resolution
  steps with different jobs — and `verify()` pins all six.
- **That produced one overlap immediately.** Sec. 2 prices any protective item
  at +1 and Sec. 4 prices the Concealment Pendant at +2: one charm, generally
  and specifically. Specific beats general, so `modifierTotal` REFUSES the
  pair rather than summing, and the dialog withdraws the general chip rather
  than offering one that throws — the lesson the two Aim rows taught (6t).
  Same scoping as Appendix A's adverse conditions against chapter 5's
  darkness (6ae).
- **The candle does not join them.** Its -3 "suppresses detection of that
  ritual", which is sensing a ritual being performed, not the being-divined
  roll. It carries its own key so no caller can reach the nearest number and
  sum it, and `verify()` pins the scope phrase.
- **One row is in the wrong table.** Wheel of Fortune (Monster) sits in the
  passive-awareness table and grants no awareness: it prints "any divination
  attempt against a Monster may suffer -1", a penalty on the DIVINER, at the
  GM's discretion, from a passive source. Recorded and applied nowhere —
  routing it through `techniquePenalty` would put it through two halvings that
  both name "active techniques", which it is not, and offering it as a chip
  would put a number the GM must first grant beside penalties the target
  actually paid SPI for. Same family as divination's "See Step 1 table" row
  (6p) and Common Modifiers' untrained row (6ae).
- **Nothing gates who may roll.** Sec. 2 scopes itself to Beyonders in prose,
  but the roll it defines is SPI or Per whichever is higher and a mortal has a
  Per. Who suspects they are watched is the GM's call — the reading that
  refused to enforce the Sealed Artifact grade table's Power Equivalent
  column (6af). The Detect button is therefore ungated, unlike Divine.
- **3F is refused, not free.** It prices itself as "SPI equal to what a
  divination of that scope would normally cost", which is a rule and not a
  number, so it carries no `spi` field and `spiFor` throws. Reading it as zero
  would make the strongest effect in the section the cheapest — the
  distinction `RitualCritical.corGained` draws between charging nothing and
  charging zero, with a real consequence this time.
- **Durations are printed and deliberately not tracked.** 1 hour, 1d6 minutes,
  24 hours, and nothing in this app holds a clock (the reason Appendix A's SPI
  recovery was left, 6ad). A technique is a declaration: spend its SPI on the
  tracker, then say the ward is up.
- **Outcome first, then the LAST matching margin.** Four rows are named by
  outcome and one by margin ("Success by 3+"); the success bands overlap, so a
  margin of 5 satisfies both and the deeper one wins, as `DegreeOfSuccess`
  reads its ladder (6ad). This table prints no critical override, so
  `SuccessRoll`'s 3-4/17-18 stand — the FrightCheck reading (6c), not the
  PCR's.

Deliberately not modelled: the six Quick Contests these sections call for —
tracing the connection back, feeding the diviner a false image, piercing a
Faceless's mask, seeing through a Scholar's projection, recalling details
about a concealed target, and the Seq 4+ row's contest against an equal or
stronger diviner. Every one needs both sides' attributes and this app holds
one sheet, the same reason Feint's Quick Contest is unmodelled (6o). Four of
Sec. 4's six items likewise do something no number expresses — a room that
cannot be divined, a 50% reflection, an interruption.

**The device found the one defect, and it was a pool where an attribute
belonged.** `DetectionDialog` was wired to `maxSpi - spiSpent`, so the
breakdown read **"the higher of SPI -2 and Per 10"** for a character who had
spent SPI into Soul Debt. Two things wrong at once: a caster who had cast a
ritual became worse at noticing he was watched, and below zero the number was
nonsense. No test caught it — every rules test passes an attribute, because
that is what the parameter is for. `Detection.attributeFor` now REFUSES a
negative rather than letting Per quietly win, since losing to Per is precisely
what hid it; and the reading it protects is that a detection roll is an
attribute roll, not a resource check.

**Codex refused for the twelfth consecutive slice** (usage limit, resets
2026-08-08 16:52). The fallback was one `ecc:kotlin-reviewer` pass scoped to
four named files with seven explicit probes — the scoping 6af learned to use —
and it returned **zero findings with all seven traced to a file:line** rather
than asserted. It did name one latent inconsistency worth closing:
`modifierNamed` resolved case-insensitively while the charm-overlap guard
compared case-sensitively, so a caller that reached a row through the tolerant
lookup could have slipped past the strict guard. Both are insensitive now.
Slices 6w-6al are SIXTEEN consecutive slices without the real review gate,
and `f0ccc56` (6ai's fix) has been seen only by the fallback reviewer. All of
them belong in one Codex sweep once the limit resets — 2026-08-08 16:52, from
Codex's own error text.

Device-verified on `Medium_Phone_API_36.1` (1080x2400), Academic Researcher —
a MORTAL with SPI 0, which is the interesting case since the section is
written for Beyonders: **Detect** renders beside Divine and is ungated; all
eight awareness chips and all six situation rows appear with their printed
signs. Ticking Sec. 2's generic charm read **"3d6 ≤ 11"** — "Per 10 (the
higher of SPI 0 and Per 10), +1". Then ticking the **Concealment Pendant**
greyed the generic row out, printed "The charm you named prices itself, so
Sec. 2's general charm row does not also apply", and moved the target to
**12** — +2, not +3, which is the double-count not happening. Sense rolled
**8 vs 12** and landed on **"Success by 3+"** with the book's own effect cell,
the deeper band beating plain "Success" where both bands fit.

**Not observed live at first, and the reason was the instrument.** The SPI-wins
branch (a Beyonder whose SPI exceeds Per) and Sec. 1's bonus chip could not be
confirmed on screen in the slice's own session. A Fool Seq 9 Academic
Researcher reaches SPI 9 against Per 10, so Per still wins and no premade plus
a FIRST potion flips it; and on `Medium_Phone_API_36.1` the app then ANRed
twice on the same chip tap. The logcat said why and it was not this code:
`Davey! duration=6973ms`, a **3.95s GC pause**, 139 skipped frames, and
1.4-2.7s frames on interactions that predate this slice. No exception, no
deadlock, and `/data/anr` is unreadable on a production build (`adbd cannot run
as root`). The physical tablet was tried as the faster instrument and its
NotificationShade held window focus through `statusbar collapse`,
`KEYCODE_BACK` and `am start`, so every tap went to the shade — **check
`mCurrentFocus` before concluding an app ignored a tap**, which is a fifth way
these instruments lie.

**Both were confirmed in the next session, and the ANR now has a cause rather
than a symptom.** Two `hs_err_pid*.log` files were sitting in the repo root:
*"There is insufficient memory for the Java Runtime Environment to continue.
Native memory allocation (malloc) failed"* — a Gradle JVM had died of it. The
machine measured **33.7 GB committed against a 39.2 GB limit on 15.2 GB
physical**, so the emulator was running out of the pagefile. That is what
"CPU-starved" actually was. The fix is scheduling, not code: stop the Gradle
daemons, run the emulator at `-memory 2048`, and never hold a Gradle test run
and a live emulator at once — that combination is what killed the JVM.

**Two potions, not one, is what flips the branch.** Fool grants SPI 9 at Seq 9
and +2 more at Seq 8, so **SPI 11 against Per 10**. Seeded by writing
`sessions.json` through `run-as` (base64 in, `{"beyonder": {"pathway": "Fool",
"sequence": 8, "digestion": 100}}`), which is cheaper than walking the pathway
in the UI and survives a reinstall.

**And that run immediately found a defect nothing else could.** See 6ai.

**Coverage moved this time**, and only because the pages happened to fall
right: Ch 6.5 went 39.3% -> 48.2% -> **85.7%**, with the 8 lines still
unclaimed being Sec. 5-6's spill onto page 60 (6ag). The lines claimed here
are 3755-3937, entirely inside pages 56-59, which `chapters.json` gives to
6.5. Checked against the union of every content file's claims before writing a
single span — the highest claim below was 3736 and the lowest above 3938, so
the range was genuinely unclaimed rather than assumed to be.

## 6ai. The field that was extracted, stored, documented, and never read

`DivinationAwareness.freeRoll` came out of the book in 6ah's own extractor,
went into `divination_awareness.json` on four rows, and got a KDoc line saying
what it meant. **Nothing ever read it.** A grep for the identifier returns the
extractor that writes it, the data class that declares it, and no consumer at
all — so every detection roll took Sec. 2's "whichever is higher" and the four
rows that name their own attribute were decoration. This is the same defect
class as 6h (the trackers reaching no roll) and 6m (a Soul Debt printed and not
charged), inside the very slice whose review swept for it.

- **The screen said both things at once.** A Fool Seq 8 with SPI 11 and Per 10
  read *"Free Per roll at +2"* one line above *"SPI 11 ..., +2"* for a target
  of 13. The row grants Per 10 +2 = 12. Neither line is wrong on its own,
  which is why 26 passing tests and a clean reviewer pass both missed it — the
  contradiction only exists when the two are read together, on a character no
  premade can be without two potions.
- **It is a book disagreement underneath, and both halves are kept.** Sec. 2
  scopes "the GM calls for a SPI or Per roll (whichever is higher)" to *"a
  Beyonder with passive awareness"* — Sec. 1's own population — so the two
  sections genuinely conflict whenever the named attribute is the lower one.
  The row wins for the roll the row grants (specific beats general, 6c); Sec. 2
  keeps the roll its own sentence also defines, the one a Beyonder makes on
  suspicion alone, which is what `freeRoll = null` means.
- **The warrant is that the four rows disagree with EACH OTHER.** Fool and
  Fool / Error / Door name PER; Darkness and Hermit name SPI. One attribute
  throughout would read as loose phrasing for Sec. 2's rule and the fix would
  be unjustified; a 2-2 split is a choice per pathway. `verify()` pins the
  split, both sentences, and fails if the named rows ever collapse to one
  attribute — the standard 6ad set and 6v is why it must be per-table.
- **An attribute the table never names is refused, not defaulted.** Falling
  back to the higher would hide a caller reading the wrong row behind a
  perfectly plausible number — the same reason `attributeFor` refuses a
  negative rather than letting Per quietly win (6ah).
- **The fix then reintroduced the defect one line lower, and the reviewer
  caught it.** The dialog had its own hand-written copy of the attribute
  choice for the caption, so the target moved to 12 while the sentence above it
  still said "the higher of SPI and Per" and added up to 13. `Detection`
  authors that sentence now, and the guard is general rather than a patch: a
  test asserts the line names the value the target used across every
  combination of SPI, Per and `freeRoll`. **Two copies of one decision is how
  this file's numbers stop reconciling**, and it has now happened twice in two
  slices (6ag printed a penalty twice).

**The lesson is about the sweep, not the field.** 6ah's own review pass looked
for "a rule printed and not applied" and found none, because it read the rules
objects and the dialog — not the gap between a content field and its consumers.
The cheap instrument is a grep per extracted field: if the only hits are the
extractor and the data class, the field is decoration. Note the asymmetry that
was sitting there in plain sight — `divinerPenalty`'s KDoc says "nothing
applies it" and `freeRoll`'s did not, so the code already recorded that one was
deliberate and the other was not.

**Running that sweep across all 71 content classes and 345 fields** left 11
read by nothing at all and 23 read only by tests. Most are legitimate and the
sweep's value is that it says which: the 198-row trait catalogue has no
production reader because traits are not editable yet (section 6), the golden
tables are consumed by `GoldenRitualTest` and `GoldenSkillLadderTest` marking
the book's homework, and provenance fields like `SequenceBlock.sourceLines`
are meant to be inert. **Write the parser paren-balanced**: a first pass keyed
on `data class X(...\n)` made every ONE-LINE class swallow the next class's
fields, which invented `DetectionModifier.availability` and would have sent a
reader hunting a field that does not exist.

**One real gap came out of it**, of the shape 6af already settled rather than a
new decision: `AntiDivinationTechnique.availability` and four of every five
`bullets` were extracted and never rendered. Each technique prints who may use
it — "Fool Pathway, Seq 6 — Faceless", "Darkness Pathway, Seq 5 — Spirit
Warlock" — and its own caveats, and the dialog showed the Effect line alone.
So a GM declaring a Darkness-only technique for a Justiciar target saw nothing
to stop them, and 3A's ward breaking on a wound over HP/3, 3D's failure against
a diviner 2+ Sequences stronger and 3B's +1 SPI per extra target were invisible.
None is enforceable here — the target has no sheet — which is precisely why it
is shown, the call the Sealed Artifact grade table already needed.

**And one candidate that looked like a defect and was not.** 3D prints
"Does not work against diviners 2+ Sequences stronger than you" while
`AntiDivination` computes exactly that comparison, so it read as a rule the
engine could apply and did not. It cannot: 3D prints no flat penalty at all
(only 3A and 3B do), so it contributes nothing to `penaltyTotal` and there is
nothing for the limitation to switch off. Checked before it was claimed.

Device-verified on the same seeded Fool Seq 8: **"Identity Confusion — Fool
Pathway, Seq 6 — Faceless"** now renders above Cost, Duration, Effect,
Limitation and Counter, and the Ask button is still reachable — the taller
dialog was the risk 6k found in the ranged one.

**Codex refused for the thirteenth consecutive slice**, confirming the reset
date from its own error text ("try again at Aug 8th, 2026 4:52 PM"). The
fallback was one `ecc:kotlin-reviewer` pass scoped to three named files with
seven probes, and it returned **one HIGH with five probes traced clean** — the
second fallback pass in this project to find something real, and again it was
the scoped prompt that got an answer (6af's whole-tree prompt stalled).

Device-verified on `Medium_Phone_API_36.1`, a Fool Seq 8 Academic Researcher
(SPI 11, Per 10): with no row chosen the panel reads **"3d6 ≤ 11 — SPI 11 (the
higher of SPI 11 and Per 10)"**; choosing **Fool** takes it to **10** and says
*"Per 10 (the row grants a Per roll)"*, the lower attribute winning because
the row named it; **Take +2** reads **12** with *"Per 10 (the row grants a Per
roll), +2"*, which sums; and going back to **None** restores 11 with the bonus
cleared. Before the fix the same character read 13 under the Per sentence.

## 6aj. Chapter 4 — the attribute the whole app rolls on, at 0.0%

`SpiritualSenses` (core/rules) reads Ch. 4's two result tables (p. 33-34) and
`GoldenSpiAffinityTest` marks its four claims about chapter 9's data. The
chapter DEFINING SPI — the attribute every divination, ritual and detection
roll in this app runs through — had never been read.

**The instrument's fifth distinct failure, and 6v already named this exact
mode.** `coverage.py` counts a line as mechanical when it holds dice, a signed
number or a point cost. These tables are four rows of `Success by 3+ /
<prose>` with no numbers at all, so only the two `Modifiers:` lines register:
the chapter reported **3 unclaimed lines** while holding two complete skill
resolution tables. 6v recorded that a bare `×1.5` counts as nothing; the same
blindness applies to a bare margin band, and nothing had gone looking. Ch 4 is
now at **100.0%**, from 0.0%, on 39 claimed lines.

**What was going unapplied.** Both skills have been in `skills.json` and
buyable since M0, so a character could buy one, tap it and get a bare success
or failure while the book printed a four-row table and three or four
circumstances for each. Same class as the trackers reaching no roll (6h) and
the Soul Debt printed and not charged (6m).

- **Not `DegreeOfSuccess`, and the edges make that tempting.** Both tables band
  at "Success by 3+" and "Success by 5+" — EXACTLY Appendix A's Good
  (Target−3) and Excellent (Target−5). Reusing the general ladder would look
  tidy and would be the silent merge 4.1 forbids: the effect text is per-skill,
  Intuition's 5+ being "Specific premonition (ambush, poison, betrayal)" where
  Perception's is "Identify type of entity". A chapter's own table beats the
  appendix's general one — the call `Divination`'s clarity rows, `FrightCheck`,
  `RitualResolution` and `Wounds` each already make. The shared edges are a
  coincidence recorded, never a warrant, and a test pins that the two tables
  share no effect.
- **Neither table prints a failure row**, so a failed roll gets NO row rather
  than an invented "you sense nothing", and nothing here overrides the critical
  rules — there is nothing printed to override them with, so `SuccessRoll`'s
  3-4/17-18 stand. The `FrightCheck` reading (6c), not the PCR's. `verify()`
  fails if a failure row ever appears, because a consolation row would look
  perfectly plausible.
- **"−2 distracted/in combat" is Appendix A priced specifically.** That
  appendix prints "Distracted or hurried −2" and "Very adverse conditions
  (combat, extreme weather) −6 or worse" — the same circumstances, the same
  words. Offering both charges one distraction twice, so the general chips are
  WITHHELD and the pair refused in rules, the scoping that keeps chapter 5's
  darkness off the general adverse row (6ae). Deliberately NOT extended to
  "Adverse conditions (dark, noisy, dangerous) −4": chapter 4 prints none of
  those words, and refusing it would be reasoning from a resemblance rather
  than from the text.
- **"+6 Seer" names a SEQUENCE 9 RANK, not a pathway.** A Fool at Sequence 8 is
  a Clown, so it is offered as a chip quoting the book rather than granted to
  anyone on the Fool pathway — which would author a rule the book did not
  print. Same call as the Sealed Artifact grade table's Power Equivalent column
  (6af): show it, do not gate on it. Device-verified on a character who IS a
  Seer, which is exactly where the shortcut would have been invisible.
- **The affinity paragraph describes chapter 9 and is marked, not applied.**
  Five named pathways receive "significantly more", others "+2 to +6",
  reality-leaning ones "a baseline +1", and the Prisoner exactly 0.
  `pathways.json` has held those numbers since M3 and nothing had ever compared
  them. All four reconcile EXACTLY — the five named are the five highest
  (12/11/9/9/9 against a next-best 6), both bands are populated, and Chained is
  the only 0. A rarer outcome here than a disagreement, and the reason the
  paragraph can be trusted as a description of the data. Checked as a strict
  separation rather than against a threshold the book never prints, which would
  keep passing after the gap closed.

**The reviewer found a permissive path, and it is 6l's lesson verbatim.**
`modifiers()` validated a chosen row against EVERY printed row rather than
against the sense being rolled, so Perception's "+6 Seer" would have passed on
an Intuition roll. Unreachable from the dialog, which only ever offers one
sense's own rows — and removed for exactly that reason: "nothing could reach it
yet; removing it is what stops the next caller finding it." `sense` is a
required parameter now. It also caught the golden test truncating a `Double`
SPI sum with no whole-number guard, the truncation-vs-floor family 4.3 exists
for; `PotionDigestion.wholeSteps` already guards it one file over.

**Then the device found what no test could: the tables were UNREACHABLE.** The
sheet screen listed one row per PUBLISHED skill (`itemsIndexed(sheet.skills)`),
so a skill bought in the builder could be paid for, levelled, and never rolled.
Ritualistic Magic and Divination Arts each got their own button and every other
purchase was invisible — a hole open since the edit layer shipped (6e), which
these two senses fell straight into, since no premade prints either. Shipped as
its own `fix:` commit. Bought rows are deliberately NOT comparison rows:
`SheetComparison` keeps using the unedited sheet because that diff is the
engine against the book, so a bought skill has no published column to differ
from, and "sheet: —" would invent a disagreement where there is only an
addition.

Deliberately not extracted from the same pages: **Spirit Vision's aura table**
(already claimed under Ch. 7.6, which prints the fuller version — a second span
would double-claim a summary), its Sequence scaling bands ("SPI typically 0-8 /
6-14 / 12+", descriptive, with no roll to attach them to), and **Spirituality
Recovery** — "1 SPI per hour of rest", now on its THIRD printing (here, p. 35,
and Appendix A, which 6ad also left) and still with no clock in this app to run
it against. Blocks 4 and 5 are one-line cross-references to chapters 7 and 6.5,
both fully modelled.

**Codex refused for the fourteenth consecutive slice** (usage limit, resets
2026-08-08 16:52, confirmed from its own error text this session). One
`ecc:kotlin-reviewer` fallback pass scoped to four named files with seven
probes — the scoping 6af learned to use — returning **one MEDIUM, one LOW and
five probes traced to a file:line** rather than asserted. Both findings were
fixed and are described above.

Device-verified on `Medium_Phone_API_36.1`, Academic Researcher seeded as a
**Fool Seq 9** (SPI 9) with both senses bought: the sheet's last two rows read
**"Spiritual Intuition 12 (bought)"** and **"Spiritual Perception 9 (bought)"**
— 12 because a bought SPI skill re-levels on the digested attribute and the
Seer potion's +3 is frozen on top.

**Correction (6aq):** buying either is forbidden by ch. 9 p. 85 and is no
longer possible. The Seer block grants **Spiritual Intuition +3** itself, so
that row survives on the same character by the book's own route. **Spiritual
Perception does not**: no Fool block grants it, and the four blocks that do
are Hanged Man 9/8, Wheel of Fortune 9 and Death 7. So reproducing this run
means seeding one of those — the sense is not reachable on a Fool at all,
which is the pathway affinity the book intends and not a gap. The
re-levelling reading itself is untouched: it is what a POTION-granted SPI
skill does when a later potion raises SPI. Intuition's dialog shows two chip rows;
ticking **distracted/in combat −2** removes Appendix A's "Distracted or
hurried" and "Very adverse conditions" and prints why, while leaving "Adverse
conditions −4" in place. A 14 vs 10 read **"Failure … Spiritual Intuition
prints no row for this result"**, which is the whole no-failure-row reading on
screen. Reopening cleared every chip. An 8 vs 12 read **"Good success"** beside
**"Success by 3+ — Know approximate direction of the threat"** — both ladders
on one roll, neither merged into the other. Perception offers **Seer +6**
un-ticked on a Seer, and a 6 vs 15 read **"Excellent success"** with
**"Success by 5+ — Identify type of entity"**, the deeper band beating the two
it also satisfied.

## 6ak. "Core Rules" — the chapter that looked like a summary of the engine

`EffortBurn` (core/rules) reads Ch. 2's burn (p. 9), `GoldenCoreRulesTest`
marks the success roll and the untrained defaults against the chapter they
were written from, and the Quick Contests are extracted at last.

**Why a chapter at 0.0% called "Core Rules" was never read.** Every heading
names something M0 already built — `SuccessRoll` is the success roll,
`Difficulty.defaultPenalty` is the skill defaults, `SecondaryCharacteristics`
is the secondary table. Read that way it is a summary of the code. It is not:
two of its rules reach a roll and nothing applied either. 6t's lesson in its
purest form — a chapter at 0.0% disproves nothing, and **"I already
implemented that" is a weaker reason to skip a page than a percentage is.**

- **Burning FP and SPI was printed and unapplied.** "Spend 1 FP to gain +1 on
  any roll based on ST, DX, or HT … Max +5 per roll." "Spend 1 SPI to gain +1
  on any SPI-based or supernatural skill roll … There is no cap." Both usable
  "after seeing the result", and the section closes with a sentence nothing
  enforced: "You cannot spend both FP and SPI on the same roll."
- **The warrant is that two later chapters already implement it.**
  `Divination.BURN_PER_SPI` (ch 6.5, p. 55) and `RitualResolution`'s `burned`
  (ch 7, p. 66) are this same rule scoped to their own rolls and agree
  exactly — 1 SPI, +1, no cap. That agreement is what licenses the general
  form, the per-table standard 6ad set and 6v is why it must be per-table.
  Neither chapter prints the FP half, the +5 cap, or the either-or.
- **Specific still beats general**, so a ritual and a divination keep charging
  their own: offering this one as well would charge one point of effort twice
  — the scoping the CoR ladder needed (6h), that keeps ch 13's boons off
  Phase I preparation (6r), and that withholds Appendix A's darkness on a
  ranged shot (6ae).
- **The bonus raises the TARGET and re-reads the same dice.** The book allows
  it after the result is seen, so re-ROLLING would answer a different question
  — and on a seeded source it would desynchronise every roll after it.
  `SuccessRoll.evaluate` already existed for exactly this; a test asserts the
  total is unchanged, which is what proves it.
- **IQ, Will and Per fall in a gap and are refused, not defaulted.** The FP
  row lists ST/DX/HT and the SPI row lists SPI-based rolls; neither mentions
  the mental attributes, so those skills get no burn rather than fatigue on
  the strength of the book saying nothing. The refusal
  `Languages.ritualModifier` makes about a common tongue's silence (6x).
- **The either-or cannot be violated from the screen**, because the SKILL
  decides which pool applies, so a roll only ever reaches one. `requireOnePool`
  exists anyway as an API-level guard — a plain function, so it is unit-tested,
  the call `armedParries` already made in 6g.
- **Quick Contests are extracted and still not resolved.** Several sections
  record one as "deliberately not modelled" — Feint (6o), six more in 6ah —
  and the reason given each time, that this app holds one sheet, is still
  true. What was implied and is NOT true is that the book leaves the procedure
  unstated: it prints six worked rows, "the winner is the one who succeeds by
  the larger margin", and "ties always go to the defender".

**A correction to 6ad, and U+007F is why.** That section adopted Appendix A's
degree ladder partly because "Ch. 2 … tabulates nothing". Chapter 2 tabulates
it — "Success by 0–2 / 3–4 / 5+", the same three edges under different names.
The sentence above the list was read and the list was not, because **this PDF
prints list bullets as U+007F** (6g, and 6ac lost a debugging cycle to it): a
bulleted list can look like no list at all. The conclusion survives and is
stronger — only Appendix A NAMES the bands, which is what makes it a
definition rather than a summary, and chapter 2 corroborates its edges
independently. `verify()` pins the agreement in both directions.

Chapter 2 also prints a FAILURE band ("Failure by 1–2: Barely failed") that
Appendix A has no counterpart for. Recorded and graded nowhere: one band is
not a ladder, and a test fails if it ever gains siblings.

**The reviewer found three defects and all three were the same shape: a pool
charged without the player getting what they paid for.**

- **The cap was per CALL where the book says per ROLL.** "Max +5 per roll" —
  five one-point pushes walked straight past it, because `bonusFor` only ever
  saw its own call and the ceiling lived in the screen's chip list. It takes
  the running total now, and it lives in rules: **a UI computing its own
  ceiling is a second copy of one rule**, which is how these numbers stopped
  reconciling in 6ag and 6ai.
- **The burn read a stale outcome.** `shownOut` was captured when the row was
  composed, so two taps before a recomposition both read it — the second push
  overwrote the first while the pool paid for both. It reads the live state
  and bails rather than guessing if it is gone.
- **A roll only a critical can win was pushable, and buying nothing.** Whether
  a roll is critical depends on the TOTAL (3 or 4), which a burn never
  touches, so under anti-divination's "only a critical can succeed" (6ag) the
  displayed result cannot improve however much is spent — and on the mystical
  side it bought permanent Soul Debt CoR for it (6m). Refused in `canPush`, so
  a screen that forgets to ask still cannot charge for it. That is arithmetic,
  not a reading.
- Plus a MEDIUM: the bonus moved the target and never appeared in the
  contribution list, so the breakdown no longer summed to the number above it
  — the invariant 6ag added its own 300-case test for, reintroduced one slice
  later. It is a `Contribution` now, and a test sums them across every legal
  spend.

Two parsing notes, both of which failed first. The contest table's trailing
paragraph wraps as "…if the situation" / "allows. If one succeeds…", so
anything counting rows reads two more cells; it stops on the paragraph's own
marker ("If both succeed") instead. And both burn scopes wrap mid-bracket, one
of them inside "Occultism **vs.** Beyonder phenomena" — splitting on ". "
truncated the example list exactly as `extract_fright.py` was once caught
doing (6c). The closing bracket is the terminator, and `verify()` asserts the
brackets balance, because a truncated cell still parses and still reads
plausibly.

**Codex refused for the fifteenth consecutive slice** (usage limit, resets
2026-08-08 16:52 — today, later). One `ecc:kotlin-reviewer` fallback pass
scoped to three named files with eight probes returned **two HIGH, one MEDIUM
and four probes traced to a file:line**; all three were fixed and each new
test was verified failing against the pre-fix code.

Device-verified on `Medium_Phone_API_36.1`, Barmaid Brawler (Brawling 13,
FP 10/10): the burn row is **absent before the roll** and appears under the
result, quoting the book's own scope. A 7 vs 13 read "Excellent success";
**+4 FP** took it to **7 vs 17** with "base 13, modified to 17", `burn:fp: 4`
in the breakdown, and the tracker at **FP 6/10**. Only **+1** was then
offered; taking it read **7 vs 18** with `burn:fp: 4` and `burn:fp: 1` summing
to the five-point move, FP at **5/10**, and **the chip row gone entirely** —
no sixth point purchasable, which is the cap the reviewer caught. The dice
total stayed **7** throughout: the same roll, re-read. Streetwise (IQ-based)
offered no burn at all and failed by 1 with no way to buy it off.

Chapter 2 is now at **30.8%**, from 0.0%. The nine lines still unclaimed are
the play-loop example, the worked Stealth example, the "modifiers change your
target" paragraph, and the Secondary Characteristics cost column — that last
one deliberately, because `SecondaryCharacteristics.COST_HP`…`COST_MOVE`
already hold those six numbers and ch 3's attribute table is where the costs
were claimed. The skill-defaults table's BODY sits on page 10, which
`chapters.json` gives to Chapter 3, so it is claimed under `"2"` and credited
to nobody: the **seventh** instance of that page-granularity gap.

## 6al. The trait catalogue — 198 rows, no reader, and the sweep that excused it

`TraitEffects` (core/rules) turns a premade's own advantages into modifiers.
Three reach a roll; the other seven are shown with the reason they cannot.

**6ai's sweep found this and let it go.** That slice grepped all 345 extracted
fields for a consumer, found the 198-row trait catalogue read by nothing, and
recorded it as legitimate because "traits are not editable yet". That is true
of BUYING one and irrelevant to the 23 premades, every one of which prints
traits the book gives numbers to. **`Alertness 1` is on eight of them and says
"+1 per level to all Per rolls"** — and every Per roll in this app was made at
flat skill. The sweep asked whether a field had a reader; it did not ask
whether the DATA already in the app had one. That is the lesson, and it is a
narrower version of 6ak's: an explanation that sounds sufficient is the most
expensive kind of wrong.

- **A printed number is not enough — it has to name a roll the app can
  identify.** `ControllingAttribute` distinguishes a Per skill from an HT one
  and `ParryBlock` computes the active defences, so Alertness, Fit and Combat
  Reflexes have a real join. The other seven do not, and inventing one would
  be authoring a rule to justify reading another: a "shock penalty" so High
  Pain Threshold could ignore it, a death roll for Hard to Kill, a recall roll
  for Eidetic Memory. They are REPORTED on the sheet with the book's own words
  and the reason — the call the Sealed Artifact grade table (6af) and a
  technique's availability (6ai) already needed.
- **"+1 to all active defenses" means all of them.** Block is the only defence
  whose formula names a modifier, which makes Block-only the easy wrong
  reading and would understate a Dodge by one — the same trap a shield's DB
  had to avoid (6o). Every row in the dialog goes through one function for
  exactly that reason.
- **A sheet prints the level IN the name** ("Alertness 1", "Hard to Kill 2")
  while the catalogue prints the bare name and a `perLevel` flag. An
  unlevelled trait counts as ONE level of itself, never zero — zero would
  silently cancel its own bonus. A trailing word that is not a number stays
  part of the name, which is what keeps "Poverty: Struggling" (on ten
  premades) from being read as a level.
- **The numbers live in Kotlin and the sentences are pinned in a test.** Each
  is a value inside a prose `notes` cell, so committing it as a column would
  make the book look like it printed one — the split the maneuver table's
  +4/−4 (6i), the +3 support cap (6y) and the orthodox −2 (6z) all take. The
  pin reads `ContentLoader.advantages()`, which is the copy production reads.
- **Matching a trait by name had precedent**: `Corruption.FORTITUDE` has read
  `Spiritual Fortitude` off a character since M4's first slice.

**The reviewer found two more things, and the first is 6ai's shape exactly.**

- **`ModifierSource.FromTrait` and `Layer.ADVANTAGE` have existed since M0
  with no production caller** — and this slice, the first advantage effect in
  the app, tagged its modifiers `Situational` instead. `Situational` is what a
  GM says about this turn; an advantage is what a character permanently IS,
  and the two resolve to different `SourceKind`s. Nothing can reach the
  difference today, because no `Cancel` targets `SITUATIONAL` yet — which is
  precisely why it is pinned now rather than found the day one does. Note the
  irony: the fix for "a field with no consumer" was itself written past two
  more fields with no consumer.
- **Alertness says "all Per rolls", not "all Per-controlled SKILL rolls."**
  The detection roll (6ah) is the one bare Per roll this app makes — rolled
  through `Detection.attributeFor`, never through a `SkillKey` — so
  `TraitEffects.modifiers` could not reach it. Threaded explicitly rather than
  left a silent gap. It lifts the Per branch only, because a roll that takes
  SPI is not a Per roll; and it deliberately **cannot decide which attribute
  is rolled**, since "the higher of SPI or Per" is a comparison the book makes
  between two attributes and letting a trait tip it would be a claim the book
  never makes. Choose on the raw values, then apply — the smaller reading, so
  it is pinned by a named test. `attributeLine` names the bonus, because that
  sentence and the target above it must reconcile (6ai).

Five probes traced clean, and two are worth keeping: the Block branch
deliberately does NOT go through `shielded()`, because `Shields.blockFor`
folds in a Block-specific penalty that `Shields.withBonus` cannot express; and
the hoisted `traits` memo is keyed correctly because a potion in this codebase
grants attributes, SPI, secondaries and skill levels — never a trait.

**Codex refused for the sixteenth consecutive slice** (usage limit; it now
reports the reset as "4:52 PM" the same day, retested at 06:23). One
`ecc:kotlin-reviewer` fallback pass scoped to four named files with eight
probes returned one MEDIUM, one LOW and six probes traced to a file:line.

Device-verified on `Medium_Phone_API_36.1`. **Night Watchman** (Alertness 1,
Fit): Observation read **"base 12, modified to 13"** with `trait:alertness: 1`,
and Running read **"base 12, modified to 13"** with `trait:fit: 1` — Alertness
correctly absent from the HT roll, which is the scoping holding. Its Detect
dialog read **"3d6 ≤ 10 — Per 10 (the higher of SPI 0 and Per 9, +1
Alertness)"**, against **Hunter-Tracker** (no Alertness) reading **"3d6 ≤ 9 —
Per 9 (the higher of SPI 0 and Per 9)"** on the identical screen.
**Military Police** (Combat Reflexes) prints Dodge 8 on the sheet and the
defence dialog reads **Dodge 9** and **Parry (Brawling) 9** — every defence,
not just Block.

Still unread in chapter 3: the study/training system on p. 32 (tutors, study
points, "a skill cannot exceed Attribute+1 through study alone"), and the
self-control disadvantages' own rolls — Curious "vs. 12", Bad Temper "vs. 12",
Impulsive "vs. IQ−2", Stubborn "vs. Will−3". Those four are rolls this app
could make; WHEN to call for one is the GM's, which is the same shape as
Single-Minded's extended concentration and is why they are not in this slice.

## 6am. The number a tidy-up regex threw away, five milestones ago

`SelfControl` (core/rules) reads the self-control target off a disadvantage
and `SelfControlDialog` rolls it. The finding is not in the book — it is in
this repo's own extractor.

**`strip_self_control_number` has deleted a printed rule since M0.**
`tools/extract_content.py:172` turned the book's `"Curious (12)"` into
`"Curious"` so a sheet's spelling would match the catalogue. That
parenthesised number IS the self-control target, and nothing else captured
it. The regex had no capture group; the number went nowhere.

- **Three spellings, three anchors, and none of them merged.** The book
  prints the target in the trait NAME ("Curious (12)", nine rows), in the
  effect cell ("Must roll vs. Will−3 …", sixteen rows), and once as the roll
  itself ("roll 3d6 ≤ 6"). `parse_self_control` reads each on its own terms
  and records which one answered in `source`, so a later reader can tell a
  number the book printed twice from one it printed once.
- **Five rows print it BOTH ways and all five agree exactly.** That agreement
  is the entire warrant for reading the bare number on the four that print it
  only in the name — Overconfidence, Obsession, Nightmares, Hallucinations —
  whose effect cells name no roll at all. `verify()` fails if the five stop
  agreeing, because the day they disagree is the day the reading has to be
  made again rather than inherited. The per-table standard 6ad set, and 6v is
  why it must be per-table.
- **Overconfidence is on ELEVEN of the twenty-three premades.** Not an edge
  case that went unread: the most common self-control disadvantage in the
  book's own cast, silently unrollable for five milestones.
- **A flat target and an attribute roll are different rules and are stored
  apart.** "Curious (12)" is 12 whatever the character's Will is, so a potion
  that raises Will must not make it easier to resist; fifteen rows key off
  Will or IQ and move with the digested character. `SelfControlRoll` makes
  the two exclusive by construction, so an attribute can never reach a flat
  row.
- **Two phrasings that print a number to roll under are REFUSED.** Duty's
  "(roll 9 or less)" is how often the duty comes up and Phobia's "Fright
  Check at −2" is a join to chapter 5. Both would have parsed. `verify()`
  pins that each still prints its sentence and still carries no
  `selfControl`, so the refusals stay decisions.
- **The roll is offered, never fired.** 6al left these out because WHEN to
  call for one is the GM's. True, and not a reason to leave the number
  unreachable: a Fright Check (6c) and a Major Wound's HT roll (6n) are
  equally the GM's and both ship as a button the GM presses.
- **`Conditions` are deliberately NOT layered on.** `FrightCheck` takes a
  single GM modifier and no ledger, and two Will rolls behaving differently
  in one app would be worse than either choice alone.
- **The consequence is quoted, never generated.** Three sentence shapes — "or
  investigate any mystery", "to avoid angry outbursts", "to undertake any
  sustained effort" — mean a paraphrase would get one of them backwards.

**Two sheet spellings match nothing, and that is the finding.** Three sheets
print "Absent-Minded" where the book's list prints "Absent-Mindedness", and
one prints "Paranoia: Bodies of water" against a bare "Paranoia". Both rows
the sheets are almost certainly reaching for DO print a self-control number,
which is what makes the mismatch cost something. 4.1 says the book wins and
the disagreement is recorded, so matching is by exact name and a test pins
that they are still unmatched — fuzzy-matching would guess, and guessing is
repair. On screen the row simply carries no effect line, which is the record.

### The app half, which is where the whole slice was stuck

`SelfControl` shipped tested and reachable from no screen, because
**`TraitEffects.reported` reads `ContentLoader.advantages()` only** — so a
character's disadvantages rendered NOWHERE in this app at all. The same hole
`5dcfc25` fixed for bought skills, one list over, and the exact defect class
6ai named. Every disadvantage is a row now; the ~24 that print a number are
tappable.

**The reviewer found the sharpest thing here.** `targetFor` throws for an
attribute this book never keys on — a good refusal (6ah's `attributeFor`
precedent) — but the sheet row computes that target for EVERY disadvantage
during composition of the main `LazyColumn`. A bad content row would have
taken the whole sheet screen down for every character rather than a
dismissable dialog. The fix is at the boundary, not the render site:
`SelfControlRoll` refuses it at construction, so it fails at content load.
Wrapping the render in `runCatching` would have hidden a content defect
behind a missing line. Also caught: the Roll button relied on tree removal
alone, the un-hardened idiom 6p already had to fix for divination.

### And the ritual slice's own gap, found while verifying it

Section II prints five supporting skills and one of them is
**"Hidden Lore (relevant domain)"**. The bracket is the book telling the
caster to pick a domain — the shape `"Duty (specify)"` has one list over —
not part of a skill's name, and no sheet spells it that way. Keyed on the
printed string that row was unreachable and its +1 vanished in silence for
the four premades carrying Hidden Lore. `0f038d3` had just made the other
four reachable for the first time and left this one exactly as unreachable
as before.

The matching moved into `RitualMode` with the fix, because a screen doing
its own comparison can offer a chip `supportBonus` then refuses, or withhold
one it would accept — the two-copies-of-one-decision failure 6ag and 6ai each
produced once.

**Codex refused for the eighteenth consecutive slice, and the reset had not
passed.** Its error text says "try again at 4:52 PM"; local time when this
was run was **08:31**, so 16:52 was still eight hours out. Recording that
because the session brief had read the date as already past — the hour
matters, not just the day. One `ecc:kotlin-reviewer` fallback pass scoped to
four named files with seven probes returned **one HIGH, one MEDIUM, two LOW
and four probes traced to a file:line**; both real findings are described
above and each new test was verified failing against the pre-fix code.
Slices 6w–6am are now nineteen consecutive slices without the real gate.

**A machine note that cost three emulator deaths.** `./gradlew --stop` stops
the Gradle daemon and **does not reap Kotlin compile daemons**, which survive
every build and accumulate: six of them held ~6 GB, and commit charge reached
**38.7 GB of a 39.2 GB limit**, at which point the emulator cannot start at
all and a running one is killed mid-session. Killing the java processes whose
command line matches `kotlin-compiler-embeddable` returned 7 GB instantly. Do
that between a build and a device run, not just `--stop` — the same pagefile
exhaustion 6ah diagnosed, with the actual culprit named.

Device-verified on `Medium_Phone_API_36.1`, **Journalist** — the one premade
carrying all three shapes at once. **Overconfidence — −5 pts — 3d6 ≤ 12**
above "Believes they can handle situations they cannot", which is the whole
finding on screen: that 12 exists nowhere but the name the extractor was
deleting. **Stubborn — −5 pts — 3d6 ≤ 7** against Will 10, the attribute row
resolving. **Curious** quotes "Must roll vs. 12 or investigate any mystery
encountered". **Wierdness Magnet — −15 pts** renders with no target and no
effect line, a THIRD unmatched sheet spelling beside the two the tests pin.
The dialog reads "a flat number the book prints — no attribute moves it",
stepping to **−2** took the target 12 → 10, and a rolled **14 vs 10** gave
**"Gave in"** with the effect cell in red and the Roll button gone.
On **Academic Researcher** (no self-control rows) the four drawbacks render
plain, and "Absent-Minded — −5 pts" carries no effect line — the recorded
disagreement, visible. Its ritual dialog offers **Occultism / Hidden Lore
(relevant domain) / Research**; ticking all three reads **"3d6 ≤ 12 —
Ritualistic Magic 9, Light 0, preparation +0, +3 from supporting skills"**,
which sums, and caps at +3 as the sentence says.

**The two shapes were then separated on device, which is the claim that
matters.** The same Journalist seeded as a **Justiciar Sequence 9** — whose
potion grants Will +1 — reads **Stubborn 3d6 ≤ 8** where the mortal read 7,
while **Curious** and **Overconfidence** both stay at **12**. One potion,
three disadvantages, and only the attribute-keyed one moves.

**A legibility trap that is NOT a defect, recorded so it is not "fixed".** On
that same screen the comparison table's `Will` row still reads **10** while
Stubborn reads 8, which looks like arithmetic that fails to reconcile — 8 is
11−3, not 10−3. The comparison rows are deliberately computed from the
UNEDITED sheet, because that diff is the engine against the book (6e); the
disadvantage rows use the digested character, because that is who is rolling.
Two correct numbers from two different characters on one screen. Anyone
tempted to make them agree should read 6e first.

**And the empty branch, on the character that makes it reachable.** Fifteen of
the twenty-three premades carry none of the five, so seeding Ritualistic Magic
onto **Barmaid Brawler** reads *"This character has none of the book's 5
supporting skills (Occultism, Thaumatology, Hidden Lore (relevant domain),
Research, Professional Skill (Astrology etc))"* with no chip row at all and
the target unmoved at **3d6 ≤ 8**. Naming the five it does not have is the
point: a bare absence would read as a screen that forgot to render.

## 6an. Study Points — read, mostly marked, deliberately not extracted

`GoldenStudyPointsTest` replays Ch. 3's p. 32 against the engine. This was
the other half of chapter 3's remaining third, and reading it first is what
kept it from becoming a content file nobody uses.

**The page prints three things and only one of them is new.**

- **Its "Cumulative CP" grid (2206-2230) is a truncated SECOND PRINTING of
  the skill cost grid** `skill_ladder.json` has carried from p. 25-26 since
  6ac. Four rungs against nine, and every cell agrees. Not stored — a second
  copy of a table this repo already commits is the silent merge 4.1 forbids,
  and 6y's two preparation tables and 6aa's two SPI ladders already set what
  a second printing is FOR: the agreement, checked in both directions, so
  the day it breaks the question of which page wins comes back on the table.
- **Asserted as a PREFIX, not as the ladder.** It stops at 8 CP where the
  fuller grid runs to 256. Read as complete it caps every skill at
  Attribute+3, which is the tidy wrong answer a truncated restatement invites.
- **Its worked example is the only one in the book to exercise the untrained
  default and the ladder in one breath** — Tomás, DX 10, Guns (Pistol)
  DX/Average, "default DX-5 = 5", then 1 CP → 9, 2 CP → 10, 4 CP → 11. A
  third worked example beside 6ac's two, from a different page.
- **And it demonstrates its own printed limit.** The Limits say "a skill
  cannot exceed Attribute+1 through study alone"; the example stops at
  Guns-11 and calls it "the cap". So the cap is the engine's Attr+1 rung and
  not a fifth number the page invented — pinned, along with the fact that the
  rung ABOVE it is reachable by ordinary purchase, which is exactly why the
  limit has to say "through study alone".
- **Two refusals that look like one and are not.** The time table's Very Hard
  self-study cell reads "Not possible"; `Difficulty.VERY_HARD` has carried a
  null default since M0. Both refuse rather than price, and neither implies
  the other — one is about learning alone, the other about rolling untaught.
  Recorded side by side.
- **The Character Creation Summary (2238-2244) checks out bullet by bullet**,
  which is what makes it checkable rather than authoritative (6v). 70-point
  budget, −40 disadvantage floor, the 9 baseline costing nothing, and the
  doubling. `STARTING_TOTAL` really is 70.

**The test caught the engine and the book using two different quantities, and
neither is wrong.** The summary prints the cost of EACH level (ST 10 → 20 →
40); `AttributeCosts.costOf` returns the running total from the free baseline
(10 / 30 / 70). Compared directly they look like a disagreement. The
assertion is written as a diff and says so, because "the engine contradicts
the book" is a conclusion a later reader would otherwise reach from a passing
codebase.

**The time-and-money table is NOT extracted, and that is the slice's real
decision.** It is genuinely new content — four difficulties against tutor and
self-study, "6 hrs + 4s" up to "48 hrs + £2", with one cell reading "Not
possible" — and nothing can consume it. Study points are a separate currency
earned over downtime ("between adventures", "per downtime period"), and this
app holds no clock, the reason Appendix A's SPI recovery (6ad) and a
technique's duration (6ah) are both unmodelled. Extracting it would produce a
content file read by nothing, which is precisely the defect 6ai, 6al and 6am
each found in turn — three consecutive slices whose lesson was *do not do
this*. **A slice that ends in "read, marked, not extracted" is a finished
slice**, and the lines stay unclaimed on purpose; coverage will keep counting
them, which is a floor and not a receipt.

What would make it buildable is a stateless planner rather than a downtime
tracker: given a skill's current level, name the CP the next rung costs and
the hours and money that buy it. That needs no clock — the GM says when time
passed. Recorded as the shape, not started.

**Built in 6au**, and the reason this section could not see it is worth
keeping: the sentence that makes the planner possible ("1 character point
locked to that skill") is on the same page, above everything quoted here, and
this section measured its own summary against a downtime tracker it had
already ruled out. It also records ONE Limits bullet where the book prints
three.

## 6ao. The attribute table, and a ceiling the book's own example breaks

`GoldenAttributeTableTest` replays Ch. 3's attribute cost grid (p. 10-11)
against `AttributeCosts`. That object has been production code since M0 and
**every point total the app displays runs through it** — the budget line, the
builder, the premade diff — and nothing had ever compared it to the twenty
printed cells it was written from. The skill half was marked in 6ac and the
study page's restatement in 6an; this is the half with real money on it.

- **The grid prints INCREMENTAL costs and `costOf` returns the CUMULATIVE
  total**, so the two worked examples — "DX 11 … Total: 60 pts", "ST 12 …
  Total: 70 pts" — are the only place the book gives a cumulative figure and
  therefore the only bridge between the two quantities. 6an's summary test
  discovered that distinction by failing on it, comparing 10/20/40 against
  10/30/70 and reading it as a disagreement. Marked explicitly here so the
  next reader does not repeat the deduction.
- **Nine is free and below nine is REFUSED, not refunded.** "A character who
  leaves an attribute at 9 pays nothing and receives no refund — 9 is simply
  the starting point, not a discount." A negative cost would pay points for
  weakness, which is what that sentence forbids, so the engine throws — the
  refusal-over-a-plausible-number call `Detection.attributeFor` needed (6ah).
- **Two base rates, not four, asserted across the printed width.** A
  single-cell check passes for any ladder that happens to agree at that rung;
  the shape is that DX and IQ are exactly twice ST and HT at every level.

**And chapter 3 contradicts itself about SPI, in the sentence's own
examples.** Lines 505-506: *"mysticism-leaning pathways (e.g., Seer,
Sleepless, Monster) grant as much as **+9**, while reality-leaning pathways
(e.g., Hunter, Warrior, Savant) grant as little as +1."*

| Named | Pathway | Grants |
|---|---|---|
| Hunter / Warrior / Savant | Red Priest / Twilight Giant / Paragon | **1 each** |
| Seer / Sleepless | Fool / Darkness | **9** |
| **Monster** | Wheel of Fortune | **12** |

The low half holds exactly. The high half does not: the sentence's own third
example exceeds the ceiling the same sentence states. Ch. 4's paragraph
describes the same data with no ceiling and reconciles with it perfectly
(6aj), which is what makes this chapter 3's slip rather than chapter 9's.
4.1: recorded, not repaired. Nothing reads the sentence, so there is nothing
to fix — what the test stops is a later reader treating "+9" as a validation
bound and trimming the data, or "correcting" Monster to 9, either of which
erases the conflict. Note the shape: this is the third time a paragraph and a
table have been checked against each other in this repo, and the first time
the paragraph lost.

Also worth recording: **six of chapter 3's unclaimed lines are the same
attribute ladder printed a third time** in the Character Creation Summary
(2240-2241), already checked by 6an. Chapter 3 prints its attribute costs
twice and its skill ladder twice. Neither pair is merged.

Chapter 3's remaining lines after this are the mundane advantage and
disadvantage effect tables.

## 6ap. The parry penalty the defences table has been printing since M4

`ParryBlock.parryPenalty` applies Ch. 5 p. 37's cumulative parry penalty, and
`DefenseDialog` grew the stepper that feeds it. **The rule was committed in
`defenses.json`'s own notes cell in 6g, displayed to the GM on that screen ever
since, and applied by nothing** — the class 6h (trackers reaching no roll),
6m (the Soul Debt printed and not charged) and 6ai (`freeRoll`) each found in
turn. This one was on screen the whole time, in the sentence directly under
the number it should have been moving.

- **Two printings and they agree.** The prose says "Parry accumulates a −4
  cumulative penalty **after the first** (−2 for fencing weapons)"; the
  table's Notes cell says "Cumulative −4 per additional parry (−2 fencing, −1
  rapier)". Nothing to record as a disagreement — the rarer outcome, and the
  reason the reading needed no argument.
- **The FIRST parry of a round is free.** "After the first" is the whole
  sentence: read as −4 per parry it penalises a fighter who parried once and
  never again. `parryPenalty(0)` is 0 and the screen says so out loud, because
  a stepper sitting at zero with no explanation reads as a control that does
  nothing.
- **Block is a LIMIT, not a penalty, and Dodge is neither.** Block prints
  "Once per round"; Dodge prints "May be used repeatedly". Three defences,
  three different rules about repetition, and `parryPenalty` takes no defence
  parameter precisely so a caller cannot hand it a Dodge. The general sentence
  above the table says every type "may be used multiple times per round" and
  the Block row contradicts it — specific beats general, the call the PCR's
  critical rules and the ritual CoR ladder already needed.
- **Two of the three tiers are unreachable and are modelled anyway.** −2 is
  for fencing weapons and −1 for a rapier; chapter 8's nine weapons are five
  firearms, a knife, a truncheon, a hand axe and a broadsword. Printed rules
  with nothing in this book to reach them — the `Guns (Pistol)` shape (6t),
  a skill the weapon table never arms. Modelled so a GM's smallsword is not
  silently charged the broadsword's penalty, and named on screen so the
  absence is visible rather than looking like a gap. A test fails if chapter 8
  ever prints one.
- **A negative count is refused, not clamped.** A caller holding −1 has a bug
  in its round tracking, and answering with a BONUS would hide it behind a
  plausible number — `Detection.attributeFor`'s call (6ah).
- **The stepper sits UNDER the parry rows.** Above them it would read as
  applying to Dodge too, which takes no such penalty. Placement is the only
  thing distinguishing the two on a screen that lists them together.
- **A stepper, not a counter the app increments**, because nothing here knows
  a round has passed — the reason a Fright Check (6c) and a Major Wound's HT
  roll (6n) are both buttons the GM presses.

Device-verified on `Medium_Phone_API_36.1`, **Military Police** (Combat
Reflexes, Brawling 11, Basic Speed 5.00): the dialog opens at **Dodge 9**,
**Parry (Brawling) 9** — 8 + 1 from the trait each — with "The first parry of
a round is free." Stepping to **1** takes Parry to **5** and **leaves Dodge at
9**, which is the scoping holding, and the note reads "Parry rows above
include −4. Chapter 8 prints no fencing weapon or rapier, whose tiers are −2
and −1." The two effects compose: the trait's +1 survives the −4.

One cosmetic follow-up after that run and NOT re-verified on device, because
it cannot move a number: the penalty is an `Int` and printed an ASCII hyphen
beside two typed minus signs, so one line carried two different dashes and
read as two rules. Normalised.

Chapter 5's remaining unclaimed lines are the Wrestling grappling ST bonus
("+1 ST at DX+1, +2 at DX+2, grappling only"), which needs the Quick Contest
this app does not model (6o, 6ah), and the round-order and unconsciousness
prose.

## 6aq. The app was selling three skills the book says cannot be bought

`SkillLadder.isBuyableWithPoints` applies Ch. 9 p. 85, and the builder's skill
picker honours it. This is not a rule printed and unapplied — it is a rule
printed and **contradicted**, which is a worse shape and the first of its kind
in this repo.

> *"Exception — SPI-based skills (Spiritual Intuition, Spiritual Perception,
> Divination Arts) cannot be raised with character points. They improve only
> through Sequence progression and pathway bonuses."*

The builder offered all three, and **6p's entire divination feature was built
on buying one of them** — "no premade prints Divination Arts, so it is bought
in the builder before a question can be asked". 6aj bought both spiritual
senses the same way. Three slices rest on an action the book forbids, and the
page saying so sits in chapter 9's preamble, four lines above the potion
blocks those slices read.

- **The warrant is an exact match, not a resemblance.** The sentence names
  three skills; `skills.json` prints exactly three rows with `attr: "SPI"`,
  3 of 158, and they are the same three. That is what licenses reading the
  rule as a property of the CONTROLLING ATTRIBUTE rather than as a list of
  three special names — the standard 6ad set, and 6v is why it has to be
  per-table. A test fails if a fourth ever appears or one of these stops
  being SPI-based, because the reading would then have to be made again.
- **It is the reading `Attr` already encodes.** SPI "starts at 0 and cannot be
  raised by points", which is precisely why it is not an `Attr` (4.4). The
  skills hanging off it inherit that, and chapter 9 says so out loud. 6p had
  already noticed the effect and mis-read its cause: *"the skill reads −2,
  because it is SPI/Hard and a non-Beyonder has SPI 0 — the book gating
  divination on spirituality, arithmetically."* The gate is not arithmetic.
  It is printed, and it is absolute.
- **The rule GATES rather than forbids, which is what makes the fix safe.**
  Fifteen Sequence blocks grant one of the three. Refusing the purchase would
  be a defect if nothing else could supply them; instead a Beyonder gets them
  and a mortal does not, which is the book's design. Checked by value before
  the filter was written, not after.
- **Withheld from the picker, not offered and then refused** — the lesson the
  two Aim rows taught (6t): a chip that throws on tap is not a choice.
- **And dropped on LOAD as well**, because a session saved before the fix
  would otherwise keep billing points for a purchase the book does not allow.
  Same path a skill the book no longer prints already takes (6e).
- **Ritualistic Magic is IQ/Very Hard and stays buyable**, which is what keeps
  the ritual screen reachable. Confirming that is what stops this fix quietly
  removing rituals along with divination, so it is pinned by its own test.

**Also read on the same page and already correct:** the two-branch rule for a
potion's `+N`. A NEW skill reads "Attribute + N" and explicitly not "N rungs
up from default"; a skill the character already has at the same name AND
difficulty reads "level + N" and stacks; a different difficulty is treated as
new. `PotionDigestion` implements all three branches and its KDoc quotes the
page. Checked because the two sentences look contradictory in a line dump and
are not — they are the two branches, stated one paragraph apart.

Device-verified on `Medium_Phone_API_36.1`, both directions:

- **Academic Researcher seeded as a Fool Sequence 9**, nothing bought: SPI
  **9/9** from the potion, and the action bar reads "Fright · **Ritual** ·
  **Divine** · Detect · Boon" — both screens reachable purely through the
  potion, which is the book's own route.
- **The same mortal with a PRE-FIX save** carrying Divination Arts at 4 points,
  Spiritual Intuition at 2 and Ritualistic Magic at 4: the bar reads "Fright ·
  **Ritual** · Detect · Boon · Artifacts" — **Divine is gone**, the two
  forbidden purchases dropped on load, and the legal IQ-based one kept.

Chapter 9's remaining unclaimed lines are the two upgrade examples (a Seer
paying 16 pts for Occultism at IQ+3, a Sailor paying 40 for ST 12), which are
`SkillLadder` and `AttributeCosts` restated from chapter 3 and now marked
there by 6ao and 6an.

## 6ar. Soul Debt is what an attack does to you, not what overspending does

`Pools.canAffordSpi` floors voluntary SPI spending at zero, and all five
SPI-spending dialogs withdraw their action rather than charging a debt. This
closes OPEN-1, and it is the second find of a defect class that only appeared
in 6aq: **a rule printed and CONTRADICTED**, as against printed and unapplied.

**The sweep that found it is the slice's real product.** Two audits had run on
this repo — 6ai's field-consumer grep and the recurring "printed but not
applied" pass — and both look for the same shape: something extracted and then
ignored. Neither can see a rule the app actively contradicts, because there
the code is present, reachable, and confidently wrong. Grepping the source for
prohibition language (`cannot|may not|must not|never|not allowed|not
possible`) gives 165 lines, 89 with a mechanical hook, and the handful with a
live consumer are the audit.

**What the book actually says, in four lines across two chapters:**

| Line | Text | Where it sits |
|---|---|---|
| 2716 | "You may choose a pool already at 0 (risking death, unconsciousness, or **Soul Debt**)" | Spirit Body Damage |
| 2727 | "SPI below 0: Soul Debt — +1 CoR per 1 point below 0" | *Threshold Penalties by Pool*, same section |
| 2353 | "SPI = 0: Spiritual Exhaustion — **cannot activate any SPI-cost abilities**" | ch 5's pool table |
| 4254 | "The caster pays SPI equal to the ritual's complexity cost. **SPI cannot be reduced below 0.**" | ch 7, Power Source A |

Both definitions of Soul Debt sit inside **Spirit Body Damage**. It is what an
ATTACK on the spirit body does to you. Voluntary spending is capped by what
you have — and the app had merged the two, letting a mortal at 0 SPI fund a
Heavy ritual and walk away with 10 permanent CoR.

- **The contrast with FP is the evidence, not either sentence alone.** p. 42
  prints "Every point of FP spent below 0 also costs 1 HP", so the book knows
  exactly how to permit overspending and price it. It does that for FP and
  states the opposite for SPI. Treating the two pools alike erases a
  distinction the book draws in two directions at once — and `spend`'s KDoc
  had argued for exactly that symmetry since 6m.
- **6m was right about the debt and wrong about the route.** Charging Soul
  Debt was the correct fix; applying it to voluntary spending was not. The
  damage path is untouched: `spend` keeps its floorless behaviour, so
  `DamageDialog` still sends a pool below 0 and still charges for it.
- **Three slices had device-verified the wrong behaviour** (6q, 6r, 6aa),
  each reading ch 5's table on its own and finding it consistent. That is
  what makes this class dangerous: the numbers reconcile, the tests pass, and
  the screen looks right.
- **Withdrawn, not refused on tap** — 6x's Broken mystical language, the same
  shape, for the same reason: a button that throws is not a choice.
- **A cost of zero stays available.** `canAffordSpi(0, current = 0)` is true:
  the book charges nothing for it, and gating it would refuse an action the
  book gives away free.
- **A character already in Soul Debt may spend nothing, not a negative.**
  `spendableSpi(-4)` is 0, because damage can leave the pool there and a
  negative budget would make the comparison nonsense.
- **Ch. 7's Section IV is the book's own answer**, which is what makes the
  refusal safe rather than a dead end: HP sacrifice at 2 HP per SPI,
  participants, external sacrifice, catalysts. `PowerSources` has modelled all
  four since 6aa, so the escape route was already built and reachable.

Device-verified on `Medium_Phone_API_36.1`, Academic Researcher — a MORTAL
with SPI 0, seeded with Ritualistic Magic at 4 points, which is the exact
character the old code charged permanent CoR:

- A Light ritual costing 2 SPI reads **"3d6 ≤ 9 … Costs 2 SPI"** followed in
  red by **"No roll — 2 SPI is more than the 0 this caster has, and personal
  spirituality cannot be spent below 0. Pay in HP, bring assistants, or offer
  a sacrifice."** The Roll button is **gone**.
- Choosing **4 HP** on the Paid-in-HP row takes the same panel to **"Costs 0
  SPI and 4 HP"** and **the Roll button returns** — the book's own Section IV
  route, working, on the character that needed it.

Chapter 5 and chapter 7's remaining unclaimed lines are unchanged by this; the
sweep's other 88 candidates are ability prose with nothing in the app to
contradict.

### A note on Codex

It has found one real defect per slice for six slices running, but it reads
files through PowerShell and mis-decodes UTF-8: on this slice it reported the
`≤` and `—` in `RitualDialog.kt` as mojibake and claimed a P1 build break. The
module compiled clean and the device rendered `3d6 ≤ 5`. Treat encoding
complaints as tooling noise; check `./gradlew` before acting on them.

**Correction (6as):** the companion's job history holds no review for this
slice, or for any slice after 2026-08-01 — every later job is a quota stub.
The mojibake anecdote is real advice and its attribution is unsupported;
6ar was reviewed for the first time in 6as's sweep, and came back **clean**.

## 6as. The sweep that finally ran, and what 25 slices of no review cost

Codex answered on the first probe after its limit reset. **Thirteen chunks,
61 commits, 6n through 6ar, and it never refused once.** Two chunks came back
clean; the other eleven produced **17 findings**. Twelve are fixed here and
two are refused with evidence.

**The gap was bigger than anyone had recorded.** The slice log says the
unreviewed run started at 6w. The companion's own job history says otherwise:
the last substantive review is `review-msaonwcj-43nq9k.log`, **2026-08-01**,
covering the called-shot / Soul Debt era — 6l/6m. Everything after it is a
1068-byte quota stub. So 6n-6v's "(Codex)" attributions are **unsupported by
the job history**, not disproven: a direct `codex` CLI run outside the
companion would not log there. Recorded, not repaired.

**And the hour mattered, twice.** 6am recorded reading "try again at 4:52 PM"
as already past when local time was 08:31. This session probed at 13:28 —
still 3h23m early, refused — and again at 19:48, which worked. **Check the
clock, not the date.**

### The sharpest finding was money, and it was in committed content

`entity_response.json` has carried this since 6z:

> 12-14 — "No response. Ritual draws only on caster's residual power
> **(treat as 2 SPI personal)**."

The dialog charged the declared complexity cost on every row, so a Heavy
invocation that went unanswered took **8+ SPI where the book prints 2** — and
on a caster with no pool the excess bought permanent Soul Debt CoR. Extracted,
committed, displayed on screen, and charged by nothing. That is the class 6aq
and 6ar named — printed and CONTRADICTED — for the third time, and the first
time it was hiding inside a file this repo wrote itself.

- **Only the row that prints a price changes.** The five answering rows keep
  the declared cost. Reading Section IV.F's "you borrow power from someone
  higher" as "those rows are free" would author five prices the book never
  prints — the refusal `Languages.ritualModifier` makes about a common
  tongue's silence (6x). A row saying nothing is not a row saying zero.
- **That leaves the best outcome costing the most.** A Full response charges a
  Heavy ritual's whole 8; No response charges 2. The book prints both and
  reconciles neither, so it is pinned by a named test rather than smoothed.
- **The same section prints the ORDER, and it was not applied either.** "The
  GM rolls secretly on the Entity Response Table **first**, then applies the
  entity's modifier to the Ritualistic Magic roll." Roll was reachable with no
  answer given. Withdrawn with a reason now, not left to refuse on tap — 6x's
  Broken language and 6ar's SPI floor, the same shape a third time.
- **The 12-14 row is identified by its SHAPE, not its band** — answering, yet
  printing no modifier — so a re-extraction that moved the bands cannot point
  the substitution at the wrong row.
- **A declared HP sacrifice is displaced with the rest of the fuel.** "Draws
  ONLY on residual power". The book is silent about HP already offered; that
  step is a reading, and `Invocation.drawsOnResidualPower` is the single place
  both the SPI and the HP charge ask, because two copies is how this dialog's
  numbers stopped reconciling in 6ag and 6ai.

### The fifth SPI spend, and why a sweep could not see it

6ar's commit message says all **five** SPI-spending dialogs withdraw rather
than charge a debt. Only four call `Pools.canAffordSpi`. The fifth is ch. 2's
effort burn — and it was missed because **it is not a dialog of its own**, so
a sweep of the dialogs never reached it. Its only gate was `maxSpi > 0`, which
excludes a mortal and not a Beyonder who has spent everything.

The physical push is deliberately untouched: p. 42 PRICES fatigue below 0 at
1 HP per point where ch. 7 forbids the same for SPI outright, and that
contrast was 6ar's whole evidence. The ceiling lives in
`EffortBurn.affordable`, not in the chip list — a screen that works out its
own limit is how "max +5 per roll" came to be checked per call (6ak).

**The tell was the commit message's own "five".** A count in prose that the
code cannot produce is a finding. Grep the number.

### The rest, and one review that was refused

- **A live crash.** `AttackDialog` copied the plan when a firearm was picked
  up while the range toggle rebuilt it — two copies of one decision,
  disagreeing. Rapid Strike survived onto a ranged plan, and
  `Attacks.modifiers` refuses that with a `require`, so the next composition
  threw. `Attacks.atRange` now lives beside the two requires it satisfies.
- **6e's lesson missed the row that rolls.** A bought skill's level was looked
  up by NAME, so Guns (Pistol) + bought Guns (Rifle) rolled the pistol's
  level. Specialization was made to travel with the purchase and not with the
  display.
- **6am's own silence, one route over.** The ritual dialog's supporting skills
  were built from what was PURCHASED while every other row reads the digested
  map, so a Beyonder granted Occultism by a potion was refused Section II's +1
  without being told. `pathways.json` grants Occultism, Thaumatology, Research
  and six spellings of Hidden Lore.
- **4.2 MB of JVM crash dumps were committed** rather than deleted — the files
  6ah diagnosed the pagefile exhaustion from, carrying local paths and full
  command lines. Untracked and ignored by pattern; history left alone, because
  rewriting it is destructive and the owner's call.
- **Stale saved input was dropped at the wrong place.** 6aq stopped BILLING
  the three forbidden SPI purchases but dropped them during composition only,
  leaving the raw entry in the file — and the sheet's bought-skill rows read
  that raw list, so a potion granting the same skill brought it back as a
  rollable row. Dropped at LOAD now. **Only the forbidden ones**: an SPI skill
  is barred permanently, while a name the book does not print TODAY may be
  printed again after a content fix, so those keep being skipped
  non-destructively. A test pins both halves.
- **Two findings were REFUSED**, both on section 7's rule that Codex output is
  evidence and not orders. A missing `@OptIn(ExperimentalLayoutApi::class)` on
  `FlowRow` was called a build break; `:app:compileDebugKotlin` is green with
  no opt-in anywhere in `MainActivity`, so this Compose version has it stable.
  And `DefenseDialog` was said to flatten the parry tiers — it passes
  `ORDINARY` for every row because **no weapon in the book carries another
  tier**: chapter 8 prints five firearms, a knife, a truncheon, a hand axe and
  a broadsword, `Weapon` has no tier column because the book prints none, and
  `ParryClass.FENCING`/`RAPIER` are reachable only from tests. Carrying a
  class through would mean inventing the column first — the call 6t already
  made about which weapons can parry at all. Both disproofs are the build and
  the data, not an argument.

### Two more, both about a comparison the app refused to make

- **`divinerSequence = null` was hardcoded**, under a comment saying the sheet
  does not model the diviner's Sequence. `BeyonderState` has carried it since
  M3. So Sec. 6's Sequence Advantage could never fire: a Seq 2 diviner reading
  a Seq 4 target still took the full -10 and the critical-only clause the
  section forgives outright. `AntiDivination` has implemented and tested that
  comparison since 6ag; nothing ever handed it the number. Null stays right
  for a mortal, who has no Sequence.
- **Automatic awareness fired against every diviner.** The Seq 4+ row scopes
  itself in its own cell — "always knows when someone of **weaker** Sequence
  attempts to divine them. Equal or stronger Sequence requires a Quick
  Contest" — and the screen read the bare flag, so a demigod was told they
  were aware of an equal who might well have gone unnoticed. **`DetectionTest`
  had stated that scope in prose since 6ah**: the test knew and the code did
  not, which is the cheapest kind of finding to have missed. The comparison is
  the GM's, because this dialog holds the target's sheet and the diviner is a
  declared party with no numbers — the same reason the chapter's six Quick
  Contests are unmodelled. Asked for, like the darkness row's value; and when
  it is not given the fallback is the ordinary Sense roll, never a claim of
  awareness.

### "Supernatural" did no work, and the row's last sentence settled 6ar

The SPI burn reads "any **SPI-based or supernatural** skill roll (Ritualistic
Magic, Spiritual Perception, Occultism vs. Beyonder phenomena, etc.)".
`kindFor` read only the first half, so the row's **own first example could not
use the rule that names it**: Ritualistic Magic is IQ/Very Hard, and since 6aj
made bought skills rollable, tapping it offered no push at all.

- **Occultism is named to the GM, not granted.** Its entry is qualified by
  what is rolled AGAINST, and a `SkillKey` carries no subject matter. Granting
  it always pushes rolls the book does not; refusing it in silence hides a
  printed rule. Shown beside the result — the wealth reaction modifier's call.
- **The "etc." is not guessed at.** The set holds only what the book prints
  unqualified, and a test pins it against the row's own sentence.

**And the same row ends: "There is no cap — you may spend as much SPI as you
have."** That is this row's OWN words for the floor the effort-burn fix above
had to argue across from ch. 7's Power Source A. 6ar's evidence was a contrast
between two chapters; here the rule states its own ceiling in the sentence
directly under the one that was already being read. Worth the lesson: when a
fix rests on an inference from elsewhere, finish reading the paragraph it
actually lives in.

The FP half of the same row had the mirror gap: it names **attack rolls
first** — "any roll based on ST, DX, or HT (attack rolls, dodging, climbing,
lifting, etc.)" — and the push was wired only into the generic skill dialog,
so the roll the rule names before any other could not use it.

**It is per OUTCOME, not per dialog**, and that is the whole subtlety. The cap
is "Max +5 per roll"; All-Out Double and Rapid Strike each make two, so one
shared budget would let a fighter push one attack with the other's allowance —
the per-call-vs-per-roll slip 6ak already made once, here in the one place a
screen shows the difference. `burned` is keyed by index and cleared both by a
fresh roll and by Another turn, because a push belongs to the roll it bought.
`BurnRow` became `internal` rather than being copied into the attack dialog:
two copies of a ceiling is exactly what 6ak's finding was.

### The divination paid after it rolled, and the chapter numbers its own steps

This one was deferred twice as "a reading" and it turned out the book settles
it outright. Ch. 6.5 p. 55: **"Step 2 — Choose Method & Pay SPI ... Pay the
SPI cost."** then **"Step 3 — Roll & Apply Modifiers."** The dialog rolled
first and called `onSpendSpi` afterwards, so the payment could not reach the
roll it paid for.

**It is a three-point swing, not a technicality.** Spiritual Attrition is -3
to all SPI-based rolls at or below 1/3 max SPI, and Divination Arts is
SPI/Hard — so a diviner at 5 of 9 SPI paying 2 for their method lands on 3,
inside the threshold, and the book has them roll under a penalty the app was
computing from the pool they had before paying. `conditionPenalty` is a
function of the pending cost now: a flat Int could only ever describe the
caster who had not paid yet, and the method chip changes the cost after the
dialog is composed.

**Deferring it was right and so was finally reading the page.** Two sessions
recorded it as needing "ch. 6.5's own step list" rather than patching on the
strength of it looking wrong. The list exists, it is numbered, and it took one
grep — the lesson being that "this needs a reading" is a reason to go and read,
not a reason to leave it indefinitely.

**Device-verified in a later session**, once the machine had headroom — the
first two attempts died to 6ah's pagefile exhaustion, not the app. On
`Medium_Phone_API_36.1`, Academic Researcher seeded as a **Fool Seq 9** with
`spiSpent: 4`, so the sheet reads **SPI 5 / 9**: a Trivial question through
**Pendulum (1 SPI)** reads **"Roll 3d6 ≤ 14 — Skill 12, question +2. Costs 1
SPI"**, because paying 1 leaves 4 and the threshold is 3. Tapping **Crystal
Ball (2 SPI)** — changing nothing else — reads **"Roll 3d6 ≤ 11 — Skill 12,
question +2, circumstances −3. Costs 2 SPI"**. Paying 2 lands the caster ON
the 1/3 rung and Spiritual Attrition reaches the roll that bought it; the old
code read the pool before payment and left the target at 14, with the
"circumstances −3" line absent entirely.

### A range's middle was unaskable, and an open end offered one chip

"Improvised / poor tools **-2 to -4**" prints -3 as surely as it prints its
ends, and `CommonModifiers.valueFor` has always accepted it. The chips were
the only thing that could not say it. An open end ("-6 or worse") was worse
off: with no `otherEnd` there was exactly one chip, which is not a choice at
all. It offers its printed end and three steps beyond now — away from zero,
the only direction the book bounds — the same reading the open-ended ritual
fuel cell takes (6q).

`valueFor` still validates every one, so nothing here can widen the book, and
a test pins that every value the chips can produce is one it accepts: offering
a chip that throws on Roll is not offering a choice (6t).

Device-verified with the divination fix above, same session and same device.
Academic Researcher, **Research 13**: the row **"Improvised / poor tools -2 to
-4"** opens **−2 / −3 / −4**, and taking the middle one rolled **5 vs 10** —
*"base 13, modified to 10, common:improvised / poor tools: -3"* — a value the
old chip row could not produce at all. **"Very adverse conditions -6 or
worse"** opens **−6 / −7 / −8 / −9**, where it used to open one chip: bounded
on the printed side (no −5 is offered) and open only away from zero.

### Left open on purpose

Three findings remain, listed with their file:line in the session's plan file.
None needs a decision: `RitualMode` lets an API caller mix Full-mode
preparation into a Quick ritual (unreachable from the dialog), plus two UI
notes with no behavioural consequence.

**Correction: the `RitualMode` one was worth taking, and was closed in
`f107e97`.** "Unreachable from the dialog" describes today's callers, not the
defect — and what it let through was 6y's own finding, a Full ritual's
Excellent (+3) applied to a Quick one that prints +2. `modifierTotal` already
refused a cross-mode target LINK and simply did not refuse a cross-mode
preparation ROW; the guard is the same shape, one line up. Filing something as
"no decision needed" because nothing reaches it is the reasoning 6l warns
against — nothing could reach the permissive path there either, and removing
it is what stopped the next caller finding it. The other two really are
cosmetic.

**Two fixes shipped unit-verified and were device-verified in a later
session** — the divination payment order and the printed-range chips, each
recorded in its own section above. The emulator's `system_server` died twice
during the first attempt ("Can't find service: window", then "…service:
input") at 33 GB of a 38.2 GB commit limit on 15.2 GB of physical memory:
6ah's pagefile exhaustion, not the app, and reaping the Kotlin daemons did not
buy back enough.

**What made the retry work is worth more than either fix.** The APK already on
disk post-dated both commits, so **nothing had to be built** — and a Gradle
run is what puts the machine over the line. Check the APK's mtime against the
commit before rebuilding; the emulator and a Gradle daemon cannot both fit
here (6am). The dead `system_server` also survives as a **zombie emulator**
that `adb devices` reports as `device` while `service check window` answers
`not found`, so it holds ~3.3 GB and answers nothing. Ask the service, not the
device list.

Device-verified on `Medium_Phone_API_36.1`, and the emulator was CPU-starved
enough that a cold start took ~90 seconds (6ah's pagefile diagnosis, still
true — reap the Kotlin compile daemons, `--stop` does not):

- **Hunter-Tracker, the crash path**: Bolt-Action Rifle → **In reach** →
  Rapid Strike **1**, Deceptive **1** → tap **Hunting Rifle**. The dialog
  survives, reads "Acc 3", snaps back to **At range**, and the Rapid Strike
  and Deceptive rows are **gone**. The old code threw on exactly this tap.
- **Academic Researcher seeded as a Fool Seq 9** (SPI 9/9, Ritualistic Magic
  12), Heavy complexity, Invocation: before asking, the panel reads **"3d6 ≤
  12 … Costs 7 SPI"** with **no Roll button** and, in red, "No roll yet — ask
  the entity first." A first ask rolled **9 — Minimal response, +1**, taking
  it to **"3d6 ≤ 13 … Costs 7 SPI"** with Roll restored: an answering row
  keeping the declared cost. A later ask rolled **13 — No response**, and the
  same panel read **"Costs 2 SPI. The entity did not answer, so the ritual
  draws only on residual power."** Two, where the old code charged seven.

- **Barmaid Brawler, Brawling 13, FP 10/10, a Rapid Strike** — "Attack
  options: −6, **2 attacks**" — rolled **10 vs 7** and **15 vs 7**, each under
  its own push row reading "Spent so far: 0". Pushing the FIRST by +3 took it
  to **10 vs 10, success**, on the same dice total of 10: the roll re-read,
  not re-rolled. Its chips dropped to **+1/+2** while **the second stayed at
  15 vs 7 with all four chips and "Spent so far: 0"**, and the tracker read
  **FP 7**. A shared budget would have capped both.

Not observed live and resting on their tests, the way the ritual CoR charge
rests on `RitualResolutionTest` (6f): the drained-Beyonder burn floor, the
bought-specialization level, the potion-granted support skill, and the
Ritualistic Magic push.

## 6at. The fourth blind spot — sweeping code→book

Three sweeps have run on this repo and each found what the one before it
structurally could not see: **a rule printed and not applied** (6h, 6m, 6ai,
6ap), **an extracted field with no consumer** (6ai's grep of all 345 fields),
and **a rule printed and CONTRADICTED** (6aq, 6ar, and 6as's residual-power
row). The question this slice opened with was what the FOURTH one is, and the
answer is a direction rather than a shape.

**All three start at the book or at an extracted field. Nothing has ever
swept from the CODE back to the book.** A number that lives in Kotlin is
invisible to every one of them: there is no book sentence to search on, and
no extracted field to grep. And this repo puts numbers in Kotlin *on
purpose* — "numbers inside prose live in Kotlin, with `verify()` or a Kotlin
test pinning the sentence they were read from" — so the sweep is a compliance
audit of a stated invariant, not a fishing trip.

The sweep, in three passes:

- **57 named numeric constants** across `core/rules` and `core/model`. Traced
  clean. Two worth recording because they LOOK like findings: `Divination
  .MAX_SUPPORT_BONUS = 2` sits beside `RitualMode.SUPPORT_CAP = 3`, and that
  is two printed rules, not a defect — ch. 6.5 line 3580 prints "cumulative
  but **cap at +2 total** from supporting skills" where ch. 7 prints +3, the
  same shape as 6y's two preparation tables. Straightening the pair would be
  the 4.1 sin. And `Character.MAX_SPI = 1000` / `MAX_ATTRIBUTE = 30` are
  documented in their own KDoc as sanity bounds — *"not a rulebook figure.
  The book never states a numeric ceiling for SPI"* — which is exactly the
  disclosure that makes them fine.
- **20 inline literals** in function bodies, which is where an invented
  number hides best, precisely because naming it would invite a pin. All but
  one are printed formulas: parry `skill/2 + 3`, dodge `speed + 3`, the
  10/20 attribute rates, the -10 critical margin.
- **The one finding**, below.

### `Divination` was the only margin-reader that kept its number in Kotlin

Five objects in this codebase read a margin off a table. Four take the
threshold FROM THE ROW — `MajorWound.failBy`, `RitualFailure.from`/`.to`,
`SpiritualSenseRow.margin`, `DegreeBand.offset`. `Divination.clarityFor` held
**5 and 3 as Kotlin literals and reached its rows by array position**.

- **Both sides were already pinned, and nothing joined them.**
  `extract_divination.py` pins the exact printed strings and their order
  (`CLARITY_RESULTS`, raising *"clarity rows changed"*); `DivinationTest`
  pinned the behaviour at each margin. Neither knows about the other. Re-read
  a band as "Success by 4+" and the extractor fails loudly, a developer
  updates the pin, and Kotlin's `>= 5` sits there silently — **selecting a row
  whose own printed text contradicts the margin that selected it**, while the
  dialog displays that text. That is the "two copies of one decision" failure
  6ag and 6ai each produced once, in the one object that both selects by
  margin and renders the band.
- **No wrong number today, and that is stated rather than glossed.** The
  printed bands are 5+/3–4/0–2 and 1–4/5+; Kotlin's thresholds agree on every
  value. This is hardening, and pretending otherwise would be inventing a
  defect.
- **The fix deletes the copy rather than pinning two together.**
  `DivinationClarity` gains `margin` and `isFailure`, parsed by the extractor
  out of the Result cell, and `clarityFor` reads them. The critical row gets
  **neither** — named by outcome, and printing no margin is not printing zero,
  which matters here for once: at 0 it would be the mildest success band and
  could outrank the row that really is.
- **Three readings survive the rewrite unchanged**, and each is still pinned:
  outcome before margin, the deepest of the overlapping bands (a margin of 5
  satisfies all three success rows), and the mildest failure row for a rolled
  17 that beat its target on the dice — where `by` goes negative and matches
  no band at all.
- **The guard is general, not five spot checks.** A test parses each row's own
  Result cell and walks every value of the band it prints, asserting the
  engine picks that row. Same shape as 6ag's parts-sum test.

**The RED proof needed a divergence to exist**, since both copies agree today:
the old hardcoded reading was restored AND one band re-read as "Success by
2–4", which is the exact scenario the test guards. It failed with *"a margin
of 2 should land on Success by 2–4 — expected Clear, was Vague"*. **The other
16 tests in the file passed** in that state, which is the whole argument for
the general form.

**Codex reviewed it and found no correctness issues** — its own targeted
Gradle run was blocked by a sandbox lock on the wrapper's `.lck` file, so the
green suite here is mine: full Kotlin suite and 239 Python tests.

Worth keeping about the sweep itself: **"never named in a test" is the wrong
discriminator.** A test hard-coding `-6` pins `RAPID_STRIKE_PENALTY`
perfectly, and a test that names the symbol pins nothing at all — a symbolic
reference moves with the bug. 37 of the 57 constants are never named in a
test and nearly all are fine. The question is whether a printed sentence
exists and whether anything joins it to the number, which is a question only
the book can answer.

## 6au. Study points — the slice 6an closed as unbuildable

`Study` (core/rules) reads Ch. 3's Study & Training tables (p. 32) and the
builder prints what each skill's next rung would cost. 6an read this page,
marked what it restated, and deliberately did NOT extract it: *"Extracting it
would produce a content file read by nothing, which is precisely the defect
6ai, 6al and 6am each found in turn."* That was the right call with the
information that section had. It was working from its own summary.

**Reading the page again is what changed the answer**, and the sentence is
line 2185, above everything 6an quoted: a study session earns **"1 character
point locked to that skill"**. Study points ARE character points. So
`SkillLadder` already prices the rung and this table only prices the point,
which makes a **stateless** planner possible in an app with no clock — the GM
says when the time passed, the planner says what the time buys. 6an even
named that shape as the way in and then measured it against a downtime
tracker it had already ruled out.

- **The Limits are THREE bullets and 6an recorded one.** Beside "a skill
  cannot exceed Attribute+1 through study alone" the book prints *"Beyonder
  potion-granted skills (Chapters 9-11) cannot be studied"* and *"No more than
  two skills may be studied per downtime period."* A section's summary of a
  page is not the page — the same lesson 6t taught about a chapter at 0.0%.
- **Three rules applied, three shown.** Applied: the Attr+1 cap, the potion
  refusal, and Very Hard self-study's "Not possible". Shown and deliberately
  not enforced: the tutor's own Attr+2 requirement, its 3-student limit, and
  the two-skills-per-period cap — this app models no tutor and no downtime
  period, so enforcing them would be inventing state. The call the Sealed
  Artifact grade table needed (6af).
- **"Not possible" is a refusal, and refusing it is the whole reason the cell
  is stored with no numbers.** Read as zero it would make the hardest road in
  the table the cheapest. `StudyPrice` carries `printed` alone there, the
  distinction `RitualCritical.corGained` draws for corruption, with teeth for
  once.
- **A plan is advice and must never be spent.** Nothing writes through
  `SavedEdits`, because that path bills the 70-point creation budget through
  `PointBudget` and study points are earned OUTSIDE it. A planner that quietly
  bought the rung would charge a character points for time they spent.
- **The money column prints two currencies** — "4s" and "£1" — so a regex
  knowing one silently drops half the column and the half it drops still
  parses. The wealth ladder's two minus signs set that trap first (6u);
  `verify()` now fails if either currency stops surviving.
- **`SkillLadder.nextRungPoints` was added rather than a second copy of the
  rungs.** The doubling ladder is `SkillLadder`'s, and this slice shipped one
  commit after 6at deleted exactly that kind of duplicate from the clarity
  table.
- **The extractor's first cut ate the worked example.** Tomas's paragraph
  wraps across three lines directly above the Limits, so a `startswith`
  blacklist took its first continuation as a fourth Limit — the
  trailing-paragraph trap `extract_core.py` and `extract_rituals.py` each
  needed rescuing from. **The book separates them itself**: the Limits are
  bulleted and the example is not, so `stream()` keeps the U+007F marker
  instead of discarding it.
- **The "Cumulative CP" grid at 2206-2230 is deliberately left unclaimed**, so
  the two spans have a hole in them. It is a truncated second printing of the
  skill cost grid `skill_ladder.json` has carried since 6ac, and
  `GoldenStudyPointsTest` already asserts it as a PREFIX. Storing it would be
  the silent merge 4.1 forbids.

**Codex found the defect, and it is this session's own class.** `Study.plan`
priced SPI-based skills happily: the API took a difficulty and an attribute
LEVEL, so a caller could not even express the refusal. Ch. 9 p. 85 says those
skills *"cannot be raised with character points"* (6aq) and this planner's
foundational sentence says study earns character points — so the prohibition
follows directly from the rule the slice was built on, and the slice
contradicted it. **A currency the book forbids spending on a skill is still
forbidden when it is earned over downtime.** `plan` takes the controlling
attribute now and routes through the same `SkillLadder.isBuyableWithPoints`
gate the builder uses, so the two cannot drift apart. Deliberately NOT left to
`isPotionGranted` to cover, even though every SPI skill in play today arrived
by potion and would be refused anyway: that overlap is a fact about the
premades, not a rule.

All four applied readings were RED-proved by neutering each in turn — the cap
raised to Attr+2, the per-point cost made per-rung, the potion refusal
disabled, and the SPI gate disabled. Each failed; each was restored.

**The golden test now marks the whole worked example.** 6an asserted its CP
arithmetic and left the time and money unassertable, because nothing consumed
them. Tomas is the only proof the cost is LINEAR per point: a printed cell
buys one CP, and he pays *"another 24 hours (16s)"* for two — exactly twice
"12 hrs + 8s". Nothing else on the page says how a multi-point rung is priced.

Device-verified on `Medium_Phone_API_36.1`, Academic Researcher (IQ 10, so the
study cap is 11):

- Seven skills at level 11 or above read **"Study alone cannot pass 11, and
  this skill is already 11."** — History at exactly 11 showing the boundary is
  `>=`, not `>`.
- **Public Speaking** (2 pts, Average, level 10) reads **"24 hrs + 16s for 2
  CP → level 11"**: the book's own Tomas arithmetic on a real character.
- **Switch** to Self-Study takes the same row to **"48 hrs + 4s"** — twice the
  time and LESS money, which is the two currencies not being conflated
  (8s×2 against 2s×2). Mathematics and Cryptography (Hard) read **96 hrs + 8s**
  against Public Speaking's 48, so the difficulty column moves per row.
- **Alchemy** bought at 1 point (IQ/Very Hard, level 7) reads **"The book
  prints "Not possible" for a Very Hard skill by self-study."** and, after one
  tap of Switch, **"48 hrs + £2 for 1 CP → level 8"** — the same skill on the
  same character refused by one road and priced by the other, and the pound
  branch rendering beside the soli rows.

Chapter 3's remaining unclaimed lines are the CP grid above (a second printing,
left on purpose) and the mundane advantage and disadvantage effect tables.

## 6av. Armour — a deferral re-read, and this time it holds

Chapter 8 is the largest unclaimed block left (**21.2%, 82 lines**) and its
armour table is the part with a rule attached: three rows of DR, and chapter 5
printing how DR interacts with the wounding multipliers. 6t deferred it with a
specific blocker — *"nothing applies DR — damage here is an expression, never
a total."* 6au had just shown that a deferral is only as good as its reading,
so this one was re-read rather than inherited.

**It holds, and the re-read is what establishes that rather than the original
note.** Two things could have lifted the blocker and neither does:

- **The attack path still produces an expression.** `Damage.forAttack` returns
  `1d-3 cr`, not a number, so there is nothing for DR to subtract from. This
  is 6t's own reason, still true.
- **`DamageDialog` DOES take a total — and it is the one place the book
  forbids DR.** That looked like the opening: 6n turned it into "one hit, one
  amount, one pool", which is exactly the shape DR reduces. But p. 41 prints
  *"Spirit body damage bypasses physical Damage Resistance (DR) entirely"* and
  *"Physical DR does not protect against spirit body damage"*, and that dialog
  is the spirit-damage one — it renders `spirit_damage.json`. Wiring DR in
  would contradict the very section the dialog exists to implement.

So armour stays unextracted, with a **sharper reason than 6t had**: not merely
that nothing consumes DR, but that the only consumer-shaped thing in the app
is explicitly exempted from it. Building it would need a physical/spirit
declaration on that dialog first, which is a UI decision the book does not
force and no premade needs — every one of the 23 is unarmoured.

**What the re-read did produce is a rule nobody could find.** Line 2789, the
wounding table's own introductory sentence, is *"Damage type determines the
multiplier applied to **damage that gets through DR**"* — the only place this
book states the ORDER, subtract then multiply. `wounding_multipliers.json`
claimed 2790 onward, so the sentence sat one line above the table it governs,
unclaimed and unfindable. It is claimed and pinned now, the same bookkeeping
6af did for the PCR formula: an assertion whose source sentence is unclaimed
is a rule nobody can find again. The rows are untouched — the JSON diff is one
line, the span, not the data.

Also worth recording for whoever does build it: the **Mail Shirt prints
"3/1*"**, a DR that varies by damage type ("DR 3 vs cutting, DR 1 vs other"),
which is the Neck row's shape (6l) in a different table — a naive parse reads
3 and silently doubles a bullet's protection.

**The lesson is the symmetric half of 6au.** That slice re-read a deferral and
overturned it; this one re-read a deferral and confirmed it. Both took one
pass over the source. A deferral that survives is worth as much as one that
falls, because the reason gets sharper and the next reader inherits evidence
instead of a conclusion — and **coverage is still not a receipt**: chapter 8's
82 lines are mostly ~70 rows of shop inventory, which no rule reads.

## 6aw. A summary table, and the default nobody can reach yet

`GoldenMeleeSummaryTest` replays Ch. 5's "Summary: Melee Skill Comparison"
(p. 39) against `ParryBlock`, `Damage` and `skills.json`. Four columns, every
one of them a second printing of something implemented since M0, and nothing
had ever compared them. Not extracted, for that reason — a content file read
by nothing is the defect 6ai, 6al and 6am each found.

**The finding is in the Special Notes column: the summary prints a default PER
SKILL, and two of the three disagree with the difficulty-derived one.**

| Skill | Difficulty | `SkillLadder.defaultFor` | The summary prints |
|---|---|---|---|
| Brawling | DX/**E** | DX−4 | **DX−2** |
| Boxing | DX/A | DX−5 | DX−5 |
| Wrestling | DX/A | DX−5 | **no default** |

- **Boxing agreeing is the warrant.** One row matching exactly is what shows
  the other two are real per-skill overrides rather than a column being
  misread — the standard 6ad set, and 6v is why it must be per-table. Specific
  beats general, so a Brawling default is DX−2 whatever the ladder says.
- **Nothing is wrong today, and the reason is worth stating plainly**:
  `SkillLadder.defaultFor` has **no production caller at all**. This app never
  shows an untrained level. It takes a `Difficulty` and structurally cannot
  express a per-skill override, so the day something wires it up — "what would
  an untrained character roll?" — it answers DX−4 for Brawling and hands
  Wrestling a default the book withholds. 6l's shape exactly: nothing can
  reach the wrong answer yet, and naming it is what stops the next caller
  finding it.
- **Deliberately not fixed by adding a `default` field to `skills.json`.**
  Three rows of 158 print one and nothing would read it — 6ai's defect, which
  this session has now twice declined to reintroduce.
- **Wrestling's "No default" and Very Hard's are different refusals** and are
  recorded side by side, not merged: one is a skill this chapter singles out,
  the other a difficulty nobody can attempt untaught (6an).

Also marked here: Wrestling's parry puts its −3 **inside** the halving
(`(Wrestling−3)/2 + 3`), which differs from subtracting 3 afterwards at every
odd level — the kind of gap that looks like a rounding preference and is not.
Both RED-proved by moving the −3 outside and by removing Boxing's printed +2
cap; each failed, each restored.

**What this closes for chapter 5.** Its remaining unclaimed lines are prose
restating implemented rules, plus the one genuinely unbuilt piece: line 2599's
*"All grappling rolls are Quick Contests of DX or Wrestling"* and the
grappling ST bonus above it. That needs both sides' attributes, which this app
does not hold — the same reason Feint (6o) and Ch. 6.5's six contests (6ah)
are unmodelled. **Verified by reading the line, not by inheriting 6ap's
note** — the discipline 6au and 6av each turned on.

## 6ax. Where this stands, for whoever picks it up next

Written at `882ffe1`, end of the session that ran the Codex sweep. **Read this
before choosing work**, and then verify it — the three slices above it (6au,
6av, 6aw) each turned on re-reading a predecessor's note instead of inheriting
it, and this note is a predecessor's note.

**Nothing is outstanding.** No open findings, no unverified fixes, no failing
tests, no known defect. Both suites green, working tree clean.

### What is genuinely left, and why each is where it is

| Candidate | State |
|---|---|
| Ch. 8 armour / DR | Deferred with evidence in **6av**. The blocker was re-read and holds: the attack path yields an expression, and the one dialog taking a total is spirit damage, which p. 41 exempts from physical DR. |
| Ch. 8's ~70 equipment rows, licences, allies | Real content, no rule attached. This is most of the chapter's 82 unclaimed lines. |
| Quick Contests (grappling, Feint, ch. 6.5's six) | Needs both sides' attributes; this app holds one sheet. Unmodelled on purpose since 6o. |
| Per-skill defaults (Brawling DX−2, Wrestling none) | Named in **6aw**, unreachable — `SkillLadder.defaultFor` has no production caller. Fix it *when* something shows an untrained level, not before. |
| SPI recovery, technique durations, downtime periods | All need a clock. This app holds none, and 6au's planner is the shape that avoids needing one. |
| Appendices B / C / Glossary | Setting prose. `coverage.py` counts a handful of lines there; none is mechanical. |

### The four sweeps, and what each could not see

Each found what its predecessor was structurally blind to. A fifth should
start by asking the same question rather than by picking a chapter.

1. **A rule printed and not applied** (6h, 6m, 6ai, 6ap) — book → code.
2. **An extracted field with no consumer** (6ai's grep of all 345 fields) —
   data → code.
3. **A rule printed and CONTRADICTED** (6aq, 6ar, 6as) — invisible to both,
   because the code is present, reachable, and confidently wrong.
4. **A number invented in code** (6at) — code → book, the only direction that
   can see a constant no source sentence backs.
5. **A gap between two claimed spans** (6ay, 6az, 6ba) — starts at neither
   end. Two extractors walked one section for its tables and left a +1 in the
   seam between them, in a chapter reading 81%. Print the lines either side
   of every claimed span — **window 8, and filter on worded mechanics as well
   as numbers** (6ay's 3-line numeric run missed two rules and one of them
   was a live defect). It found three: the Flourish Bonus, the cross-skill
   default that was hiding the whole Divine screen, and ch. 7's prohibition
   that the second of those broke.

6. **A public function with no caller** (6cf) — code → UI, the mirror of #2
   one level up. The field sweep asks whether DATA has a reader; this asks
   whether implemented BEHAVIOUR has a caller, and `supportBonus` read its
   data perfectly while nothing read IT. 6aj and 6be found this class by
   accident before it was ever run on purpose.

Every one of the six is a claim about what the OTHERS cannot see, so a
seventh starts by asking that question rather than by picking a chapter. And
note 6ba's twist: a sweep's clean verdict is valid only as of its date. 6ar
swept line 4007 correctly and found nothing, because the code that
contradicts it was not written for another two slices.

### Three habits this session earned, in priority order (and see 6ay)

- **Re-read the source before inheriting a deferral or a count.** 6au
  overturned one, 6av confirmed one, 6aw checked a claim made three messages
  earlier. All three cost a single pass. A confident earlier note is the
  cheapest thing to trust and the most expensive thing to trust wrongly.
- **A count in prose is a finding** (6as). "All five dialogs" when only four
  had the guard; "six worked rituals" when the book prints ten. Grep the
  number.
- **Coverage is a floor and an argument, never a receipt.** Five distinct ways
  it has misled here: a chapter at 0.0% disproving nothing (6t), bare
  multipliers counting as no mechanical line (6v), a table on the next
  appendix's page (6ae), a table straddling a chapter boundary (6af), and
  ~70 rows of shop inventory inflating chapter 8 (6av).

## 6ay. The Flourish Bonus, and the fifth blind spot 6ax asked for

`RitualMode.FLOURISH_BONUS` applies Ch. 7 Phase II's +1, and the ritual
dialog grew a Delivery row. The book prints it, then prints what it is FOR,
one line apart:

    The GM may award a Flourish Bonus
    (+1) for good in-character delivery.
    Effective skill = Ritualistic Magic + Preparation Mod + Flourish Bonus

**"Flourish" appeared nowhere in this codebase.** Not in `core`, not in
`app`, not in `tools`. The class 6h, 6m, 6ai and 6ap each found in turn —
printed and not applied — with the book's own formula naming it.

### The fifth blind spot: a gap BETWEEN two claimed spans

6ax listed four sweep directions and asked what the fifth is. This is it, and
it is the first that starts from neither the book nor the code:

`ritual_quick_preparation.json` claims lines **4055-4064**.
`ritual_preparation.json` claimed lines **4120-4139**.
The Flourish sits at **4140-4143** — one line past the end of the second.

Two extractors walked this section for its TABLES and neither looked at the
prose between them, so a modifier printed as a sentence fell in the seam. And
no existing instrument points there: `coverage.py` reports a per-chapter
PERCENTAGE, and chapter 7 read **81.0%**, which is what a chapter looks like
when it is nearly done — not what it looks like when a roll modifier is
missing. 6ab's own list of what remained in the chapter named the 12-step
canon procedure, Section VII, and rituals 8-10, and simply did not contain
Phase II, because 6y had claimed to read Section III.

**The sweep is cheap and mechanical**: for each content file, print the lines
immediately either side of every claimed span. A modifier hiding in a seam is
adjacent to a table by construction — that is why it was missed.

**It was run, because naming a sweep and not running it is what 6at's
precedent forbids.** 136 spans across 71 content files, three lines either
side, keeping only unclaimed lines carrying a die roll, a signed number or a
point cost: **29 seam lines**. Twenty-five are restatements of rules already
implemented and mostly already recorded as unclaimed — the skill ladder's
prose (6ac), the Character Creation Summary's attribute grid (6an/6ao), the
cumulative parry penalty (6ap), the death thresholds (6b), the Fright Check
(6c), the suspension -2 and the catalyst range (6aa), Appendix A's
Retreat/Side/Rear, which `defense_modifiers.json` has carried from chapter 5
since 6g. Two more are headings, and two are deferrals already recorded with
evidence: chapter 8's armour (6av) and Wrestling's grappling Quick Contest
(6aw). **The Flourish was the only seam holding a rule nothing applies.**

Two lines came out as CANDIDATES rather than findings, and were left for a
later slice on purpose — finding was this slice's scope. **Both were settled
in 6az, and the first was a live gate defect**: the cross-skill default is
real and was hiding the whole Divine screen from most casters; the -4 beside
it is the difficulty table restated, as suspected. The candidates:

- **3559-3560**, wrapping mid-sentence: "Divination Arts defaults to
  Ritualistic Magic-2 and vice versa. A character with one skill at 12+ can
  attempt the other at -2." A cross-skill default, in the same family as
  6aw's per-skill melee defaults — printed, and unreachable because
  `SkillLadder.defaultFor` still has no production caller.
- **3558**: complex divination "Requires Ritualistic Magic or a very high
  Divination Arts roll (-4 penalty)". Whether that -4 is a modifier or a
  restatement of `divination_difficulty.json`'s own hardest rows needs the
  page read, not a grep — and reading it as a modifier beside that table
  would be the double-count this repo has scoped out of five times.

### What the slice settles

- **It is the FULL ritual's alone, and it is the target link's exact mirror.**
  "Flourish" occurs exactly twice in this book, both inside Phase II; Quick's
  Step 3 says "Sum the following two modifiers" and names Preparation Quality
  and Target Link. So each mode prints one modifier the other does not, and
  `modifierTotal` refuses the wrong one in either direction. A test asserts
  `usesTargetLink != usesFlourish` for every mode, because if one mode ever
  became a superset of the other the scoping would need re-reading rather
  than inheriting.
- **The formula is EVIDENCE, not an inventory.** It names Ritualistic Magic,
  preparation and the flourish, and omits weight, CoR, supporting skills and
  language — all of which reach the same target. Read as exhaustive it would
  delete four rules; read as Phase II's summary of Phase II it establishes the
  one thing needed, that the +1 lands on the ROLL rather than on the fuel.
- **The number and its name are on different lines**, and neither line alone
  states the rule: one has a bonus with no value, the other a value with no
  name. Worse, the line above ends "(4-part Hermes structure — see reference
  below)", so a rule taking the paragraph's first bracketed number reads the
  structure. The regex anchors to the NAME and the lines are joined first —
  the page-break join `extract_languages.py` needed for "costs / half."
- **A flag, not a stepper.** The book prints one value, unlike the ranges the
  darkness row, the preparation cells and the ritual fuel all print. And it
  defaults to FALSE, because the book has the GM AWARD it: a bonus that
  started ticked would be one nobody granted.
- **The canon procedure's timing penalty is refused, not applied.** Section C
  prints "Wrong timing for the entity gives -1 to -2", and Phase I already
  declares "Timing: Does it match the entity's domain?" among the choices
  feeding the single Preparation Modifier — Excellent's own cell names
  "celestial timing". Charging both prices one mistimed ritual twice, the
  scoping that keeps Appendix A's adverse conditions off a shot already priced
  by chapter 5's darkness row (6ae). The book also labels Section C "a
  reference ... when you want the full texture", and `verify()` pins BOTH —
  because "it is reference prose" is a reason that has to survive the page
  printing a number, which is 6t's lesson about a chapter at 0.0%.
- **The +1 lives in Kotlin and the sentence is claimed**, the split the
  maneuver table's +4/-4 (6i), the +3 support cap (6y) and the orthodox -2
  (6z) all take. A test asserts `"flourish"` is absent from the JSON, and
  another asserts `[4140, 4143]` IS in its `sourceLines` — an assertion whose
  source sentence is unclaimed is a rule nobody can find again (6af).
- **Ch. 13's boons take no flourish**, the same way they take no language, no
  Phase I preparation and no power sources: chapter 13 prices the whole
  performance through its own Ritual quality row. Documented in
  `Boons.targetFor`'s KDoc so the omission stays a decision.

**My own arithmetic was the one thing that failed.** The composition test was
written expecting 8 CoR to be -2 and it is -1, so the suite went red on a
number I had asserted from memory rather than from `Corruption.ritualPenalty`.
Exactly the "count in prose is a finding" habit (6as) turned on my own test.

**Codex reviewed it and returned clean** — "No discrete correctness issues
found in the changed files. The new flourish modifier is scoped consistently
through rules, UI, extractor checks, and tests." Both readings were RED-proved
by neutering them together (QUICK given `usesFlourish = true` and the bonus
set to 0): **5 tests failed**, and were restored.

Device-verified on `Medium_Phone_API_36.1` (1080x2400), Academic Researcher
seeded with Ritualistic Magic at 4 points (level 9), a MORTAL with SPI 0:

- **Full** mode shows **Delivery (Phase II)** with a **Flourish +1** chip and
  the book's own sentence quoted. Untouched, the panel reads **"3d6 ≤ 9 —
  Ritualistic Magic 9, Light 0, preparation +0."**
- Ticking it reads **"3d6 ≤ 10 — ... preparation +0, +1 flourish"**, which
  sums to the number above it.
- Switching to **Quick** with it still ticked **did not crash** — the
  `remember(mode)` reset, which matters more here than for the link because a
  stale flourish makes `modifierTotal` THROW. The Delivery row is **gone**,
  **Link to target** appears in its place, and the target is back to **9** with
  the flourish absent from the breakdown: the two modes swapping exactly one
  modifier each, on screen.
- Switching back to **Full** cleared it again, so it had to be re-ticked —
  verified in both directions.
- Paying **4 HP** (2 HP = 1 SPI) covered the fuel, restoring the Roll button on
  a caster with no SPI pool (6ar's route). The panel read **"3d6 ≤ 10 ... Costs
  0 SPI and 4 HP"** and rolled **13 vs 10** — against the target the flourish
  moved, so `attempt` takes it and not only `targetFor` — landing on "Failure
  by 1-4 — No effect". HP went **9 → 5** and the session saved `hpLost: 4`,
  `spiSpent: 0`.

Chapter 7 is now at **81.6%**, from 81.0%. The four lines claimed are the
seam itself; nothing else in the chapter moved.

## 6az. The seam sweep's second finding, and a gate that hid a whole screen

`Divination.crossDefaultFor` / `.levelFor` apply Ch. 6.5 p. 54's cross-skill
default, and the Divine and Ritual buttons now honour it. 6ay named this line
as a CANDIDATE and left it for a later slice; this is that slice, and the
candidate turned out to be the more expensive of the two findings.

    Default: Divination Arts defaults to Ritualistic Magic-2 and vice versa.
    A character with one skill at 12+ can attempt the other at -2.

Lines 3559-3560, two lines above `divination_support.json`'s span at 3563 --
the seam sweep's shape exactly (6ay), and wrapping across a page break so
neither line alone states the rule.

**The gate was the defect, and the data says how big.** `MainActivity` showed
the Divine button only to a character who OWNED Divination Arts. **25 potion
blocks grant Ritualistic Magic; 5 grant Divination Arts.** Door, White Tower,
Hanged Man, Darkness, Sun, Death, Paragon, Mother, Moon and Abyss all make
casters the book lets ask a question and the app did not -- an entire screen,
unreachable, for most of the casters this app can produce.

- **A DEFAULT, not a modifier**, which is why nothing in the Step 3 table
  covers it and why a per-roll chip would have been the wrong home. It says
  what a caster ROLLS AGAINST.
- **Specific beats general, and the gap is four points.**
  `Difficulty.defaultPenalty` answers -6 for SPI/Hard; this prints -2. Same
  shape as 6aw's Brawling DX-2 against the ladder's DX-4 -- except that there
  `SkillLadder.defaultFor` has no production caller and nothing could reach
  the wrong answer. Here it was reachable, and the wrong answer was "no
  screen at all".
- **"12+" is a gate, and below it the answer is null rather than the general
  default.** A caster with the sibling at 11 is not a caster with no skill:
  what the book declines to say is whether they may try at all, and answering
  -6 for them would settle a question it left open. The refusal
  `Wounds.multiplierFor` draws between a silent cell and a key that is not a
  damage type.
- **The -4 one line above is NOT a sixth modifier.** "Complex divination ...
  Requires Ritualistic Magic or a very high Divination Arts roll (-4
  penalty)" restates the Question Difficulty table, whose Hard row is exactly
  -4 and whose Extreme row is "-6 or worse". Applying both charges one hard
  question twice -- the scoping that keeps Appendix A's adverse conditions off
  a shot already priced by chapter 5's darkness row (6ae). `verify()` fails
  if the table stops printing a -4, because that agreement is the whole
  evidence.

### Codex found two P2s, and the first overturned my reading

- **Owning the skill does not settle which number to roll.** I gated on
  ownership: own it, roll it; lack it, default. The sentence conditions on
  the SOURCE skill's level and says nothing about lacking the target. A
  **Sequence 9 Hermit** is granted Ritualistic Magic at IQ+4 and Divination
  Arts at SPI+2, so at IQ 10 / SPI 9 they own Divination Arts **11** while
  their Ritualistic Magic 14 lends **12**. Ownership-first rolls the worse of
  two numbers the book permits. Not GURPS's "best of skill or default"
  imported as a convention -- GURPS's conventions are GURPS's (6l) -- but the
  printed permission read literally.
- **A better default is not the same as having no skill.** The fix above made
  `isDefault` true for the Hermit, and the dialog printed *"This character has
  no Divination Arts of their own"* at a character who has it. The sentence is
  authored by `Divination.sourceLine` now, not assembled in each dialog -- the
  two-copies-of-one-decision failure 6ag and 6ai each produced once, and
  `SkillOrDefault.own` is what lets one flag tell two cases apart.

Both were RED-proved by restoring the old behaviour: ownership-first failed
1 test, one-sentence-for-both failed 1 test. A third Codex pass came back
clean.

**A machine note.** `:app:testDebugUnitTest` died with "Test process
encountered an unexpected problem" and no compile error -- commit charge was
**37.2 GB of 40.2 GB**. Reaping the Kotlin daemons freed 7 GB and the same
suite passed untouched. 6ah's pagefile exhaustion presenting as a test
failure rather than as an emulator death; `--stop` still does not reap them
(6am).

Device-verified on `Medium_Phone_API_36.1`, both branches, seeded through
`sessions.json`:

- **Academic Researcher as a Hermit Seq 9** (owns Divination Arts 11): the
  dialog reads **"Divination Arts 12"** over *"Defaulted from Ritualistic
  Magic 14. ... **Better than their own Divination Arts 11**, and the book
  conditions the default on the other skill's level, not on lacking this
  one."* -- Codex's finding and its fix in one line.
- **Journalist as a Darkness Seq 9** (owns none): **Divine renders at all**,
  which it did not before this slice, and reads **"Divination Arts 10"** over
  *"... This character has no Divination Arts of their own."*
- That caster's panel read **"Roll 3d6 <= 12 -- Skill 10, question +2. Costs 1
  SPI"** and rolled **17 vs 12**, taking Critical Failure -> Backlash ->
  ch. 7's table ("Wrong Arrival"), with `spiSpent: 1` saved. So the defaulted
  level reaches `attempt`, not only the target line.

Chapter 6.5 is now at **89.3%**, from 85.7%. The six lines left there are
Sec. 5-6's spill onto page 60 (6ag).

## 6ba. The rule the app broke two commits after the sweep that cleared it

`Divination.levelsFor` refuses the cross-skill default in the ritual
direction, applying Ch. 7 II p. 61. This is the defect class 6aq and 6ar
named — **a rule printed and CONTRADICTED** — and for the first time the
contradiction was not inherited from an earlier milestone. **6az created it,
two commits before this one.**

    Ritualistic Magic (IQ/Very Hard) - your core skill. It has no default and
    cannot be substituted. It covers your ability to design, prepare, and
    execute rituals correctly. Supporting skills provide complementary
    bonuses but never replace this skill.

6az read ch. 6.5's "Divination Arts defaults to Ritualistic Magic-2 **and
vice versa**" as symmetric and wired both directions. Ch. 7 forbids exactly
the second one.

### The warrant, which is not "ch. 7 defines the skill"

That reasoning would be the per-source generalisation 6v disproves. Three
things settle it, and the first two were greps that cost a minute each:

- **"cannot be substituted" is printed EXACTLY ONCE in this book.** Had it
  turned up under other Very Hard blurbs it would be boilerplate and the
  sentence would say nothing about this pair. A test asserts the uniqueness
  rather than recalling it, because the day it stops being unique the reading
  has to be made again.
- **Ch. 2 p. 10 agrees independently**: "Very Hard skills ... have no default
  at all" — which is why `Difficulty.VERY_HARD` has carried a null default
  since M0. So two places say Ritualistic Magic has no default against one
  trailing clause that says it has. The clause loses (4.1), the way ch. 3's
  own "+9" ceiling lost to chapter 9's data (6ao).
- **The asymmetry falls out of the difficulty grid.** The direction that
  survives is exactly the one whose difficulty permits a default at all:
  Divination Arts is SPI/Hard and defaults at -6 generally, which p. 54
  improves to -2. An asymmetry that has to be asserted is a smell; this one
  is derivable.
- **Ch. 7 is not speaking past Divination Arts.** Its own category table
  sends divination away to the other chapter — "Divination (1-2 SPI via
  Divination Arts, see Ch 6.5)" (p. 67) — so the prohibition was written with
  that skill in view.

### One function, because the asymmetry is the rule

The first fix was two functions and a call site choosing between them, which
is the shape that cannot be tested: a screen making the second call itself is
checkable only by reading it, and reading it is what failed in 6az.
`levelsFor(ownDivination, ownRitual)` returns both rolls, so a symmetric
implementation fails a test instead of shipping. **All three new tests fail
against the symmetric body** and the other 45 in the file pass — the general
one walks a 20x20 grid and asserts a ritual level is a function of the
caster's own skill alone, while also asserting the sibling still moves the
divination level, so an engine that refused BOTH directions cannot pass it
vacuously.

`RitualDialog` lost its `sourceLine` parameter entirely rather than being
passed a permanent null: a nullable parameter implies there is another number
the level could have come from, and ch. 7 says there is not.

### The lesson: a prohibition sweep expires

6ar swept the source for prohibition language (`cannot|may not|never|not
allowed`), found 165 lines, 89 with a mechanical hook, and triaged them.
**Line 4007 was in that set and correctly produced no finding** — nothing
contradicted it in 2026-08-08's tree. 6az then wrote the code that does.

So a sweep's clean verdict is valid **only as of its date**, and the cheap
guard is not to re-run the whole sweep but to check a new slice's own subject
against the prohibition line-set before shipping it. Neither of the two
sweeps this repo runs on new code — the field-consumer grep (6ai) and the
printed-but-not-applied pass — can see this, because both start from
something extracted, and a rule the code contradicts is a rule nothing
extracted.

### The seam sweep's method was wrong, and Codex audited it

6ay's sweep was submitted for a reasoning audit rather than a code review —
27 verdicts, each with its claimed implementation site. It came back with
**four wrong or unproven, and two method flaws**, and the flaws are what
found this slice:

- **The 3-line window misses a rule that wraps.** Line 2599 ENDS mid-sentence
  at "Grappling ST Bonus: For"; the payload is on 2600-2601, one line outside
  the window, and nothing implements it.
- **The numeric filter is blind to worded mechanics.** "once per round", "no
  cap", "cannot", "halve", "double" carry rules with no signed number.

Re-run at **window 8 with a worded-mechanics filter: 76 seam lines, 15 found
only by the worded filter** — where the narrow run found 29. Of my original
27 verdicts 25 held; both failures were the same shape, a rule wrapped across
the line where a claimed span ends, which is the shape the sweep exists to
find. **The window was the bug, exactly as it was for the two findings the
sweep did produce.**

Confirmed unimplemented and deliberately left for their own slices, each
recorded here so the next reader inherits evidence rather than a to-do:

| Line | Rule | Why not here |
|---|---|---|
| 2600-2601 | Grappling ST: +1 at DX+1, +2 at DX+2 | Its own sentence sends it to a Quick Contest, unmodelled since 6o |
| 2457 | Retreat / Acrobatic Dodge / Dive "once per round" | Governs `defense_modifiers.json`'s rows from ~25 lines above them; needs a round tracker this app does not hold |
| 2687 | Unaware of a shot: "you cannot defend at all" | A refusal with no roll to refuse yet |

### The largest thing the widened sweep found, left as the next slice

Triaging all 76 turned up one block worth its own slice, and it is 6y's
finding one table over. **Lines 4076-4105 are entirely unclaimed** — thirty
lines holding two complete tables, inside the Quick Ritual's Step 4 (p. 62):

- **A third printing of the weight table** (Light +0 / 2 SPI, Moderate -2 /
  4-6, Heavy -4 / 8+). It agrees exactly with Section V, which
  `ritual_weights.json` claims at 4318-4345. Not a defect — a warrant, and
  the evidence that weight is shared across both modes, which the app assumes
  and nothing had checked.
- **An outcome table the app does not use**, holding the only success grading
  ch. 7 prints.

**This section first read that second half as a MODE SPLIT — the app applying
the full ritual's failure bands to quick rituals, 6y's finding one table
over. That was wrong, and 6bb retracts it before it was built.** The failure
half is Section VI COARSENED, not a rival to it. The slice that survived is
the success half alone; see 6bb.

And one found while checking reachability, which is a **parse** finding
rather than a rules one: Demoness Sequence 7 grants
`Ritualistic Magic (Mirror, Dowsing)`, which `skillLevelOf(RITUAL_SKILL)`
never matches, so a caster the book grants the skill at IQ+4 gets no Ritual
button at all. Read the block before assuming it is a spelling — the bracket
may be a printed restriction, the shape "Hidden Lore (relevant domain)" has
one section over (6am). Its own slice.

**Codex reviewed the fix and returned clean.**

Device-verified on the physical tablet (1920x1200), Academic Researcher
seeded through `sessions.json`, both directions on one pathway:

- **Darkness Sequence 6** — the book's own worst case, and reachable with no
  bought skill at all. Ritualistic Magic is granted three times (IQ+2, then
  +2, then +2 = **16**) and Divination Arts once at Sequence 6, as a NEW
  skill on the cumulative SPI 17, giving **19**. So the sibling lends 17,
  which is higher than the caster's own 16. The dialog reads **"3d6 ≤ 16 —
  Ritualistic Magic 16, Light 0, preparation +0. Costs 2 SPI."** and no
  source line at all. Before this commit it read 17.
- **Darkness Sequence 8**, one rung up, where the same character owns
  Ritualistic Magic 14 and no Divination Arts: **"Divination Arts 12"** over
  *"Defaulted from Ritualistic Magic 14. ... This character has no Divination
  Arts of their own."* The surviving direction, intact.

Both spans are claimed now — `ritual_support.json` carries `[[4007, 4008],
[4009, 4028]]`, the prohibition sitting one line above the supporting-skill
table its own extractor walked — and each chapter's `verify()` pins the
OTHER's sentence, because the limit lives in a chapter `extract_divination`
never reads and that is precisely how it went unread.

## 6bb. Grading a ritual that worked — and a finding I retracted mid-slice

`RitualResolution.successFor` reads Ch. 7 Step 4's outcome table (p. 62), and
the ritual dialog now says how well a ritual went rather than only that it
held. The class 6h, 6m, 6ai and 6ap each found — printed and not applied —
found this time in a table claimed by nobody.

**Section VI is "Failure & Consequences" and means it.** It prints no success
row at all, so until this table was read a ritual that surpassed its intent
and one that scraped in were the same event. Step 4 is the one place the
chapter grades a success, and it prints two bands:

| Margin | Outcome |
|---|---|
| Success by 3+ | "Effect surpasses intent. Entity pleased (if invoked)." |
| Success by 0-2 | "Effect works as intended." |

### The retraction, which is the more useful half

The sweep first read the SAME table's failure rows as a rival to Section
VI's — a mode split, 6y's two preparation tables one table over, with the app
wrongly applying the full ritual's bands to quick rituals. **Two lines of the
book killed it, and both were one grep away:**

- **p. 61**: the two modes "use the same underlying canon procedure — they
  just expose different levels of granularity."
- **p. 63, Phase III**: "Then resolve using the **same outcome table as Quick
  Ritual (Step 4)**."

So there is one outcome table, under the Quick heading, serving both modes —
and its failure rows are Section VI's COARSENED, not contradicted. Every band
here is a union of bands there (`5+` is `5-9` ∪ `10+`) with the same sentences
abbreviated. That is exactly what "different levels of granularity" describes,
and it is why the app's existing failure handling was correct all along.

**The discriminator is worth keeping, because the two shapes look identical
at a glance.** 6y's preparation tables print *different numbers for the same
named row* — Excellent +2 against +3 — which granularity cannot explain. This
table prints *fewer, wider bands with the same text*. A conflict is a rival
table; a coarsening is a summary. `verify()` now asserts the coarsening in
both directions: Step 4's band edges must be a SUBSET of Section VI's, and
must not equal them.

Near-miss worth recording plainly: I had the fix half-written before reading
p. 63. Nothing shipped, because 4.1 cuts both ways — a rule the book does not
print must not be applied, and code the book does not contradict must not be
"fixed" (section 7: never fix correct code to satisfy a review).

### What made the success rows adoptable

- **The same table's failure rows agree with Section VI.** That agreement,
  off the SAME table, is the entire warrant for trusting the two rows it
  alone prints — the per-table standard 6ad set, and 6v is why it can never
  be a per-source one. Both halves are pinned.
- **Printed exactly once each.** "Effect surpasses intent" and "Entity
  pleased" occur at line 4095 and nowhere else; the only other "works as
  intended" is a Sequence ability's own ladder in ch. 12, a different context.
- **A critical success is graded by MARGIN like any other.** The table prints
  no critical row, and a rolled 3 succeeds against any target however
  hopeless — so the margin can be NEGATIVE and land on no printed band. That
  takes the mildest row rather than crashing or inventing a "surpasses
  intent" the book never granted: `FrightCheck`'s call for a roll that
  overshoots its table by nothing (6c).
- **The milder band starts at 0, not 1**, and that is load-bearing: a ritual
  that exactly hits its target succeeded and must land on a row. Pinned in
  both languages, and it is the case the device happened to roll first.
- **`RitualOutcome.success` is a separate field from `consequence`**, so a
  caller cannot read a success band off a failed ritual — the slip
  `SkillRollOutcome.degree` avoids one object over (6ad). A test asserts it.
- **`DegreeOfSuccess` is still not layered on top.** Each chapter grades
  against the table it prints, which is exactly why 6ad scoped its general
  ladder to plain skill rolls — and that reasoning now has ch. 7's own table
  to point at rather than an absence.

**The block is claimed whole (4076-4105) and only its success rows are
stored.** The other two things in it are a THIRD printing of the weight table,
agreeing with Section V, and the quick ritual's burning clause, which ch. 2
already covers. Neither is stored and both are asserted — storing them would
be the silent merge 4.1 forbids, and an assertion whose source sentence is
unclaimed is a rule nobody can find again (6af).

Two parsing notes, both of which failed first. `collect` needs EVERY label of
the table as a boundary, not only the two being kept: the success rows come
first and the failure rows follow, so a success-only predicate made the last
success cell swallow the entire failure half — the trailing-paragraph trap
`extract_core` and `extract_rituals` were each caught by. The rows are dropped
after the boundaries are drawn, never before. And Step 4 writes "Success by
0–2" with an EN DASH where Section VI uses a hyphen, so the bands are compared
parsed rather than as printed strings — the two-kinds-of-minus trap the wealth
ladder set first (6u).

**Codex reviewed it and returned clean.** Two of the four new Kotlin tests
fail against ungraded success; the other two guard different properties (the
bands not overlapping, and a failure never carrying a success row) and
correctly stay green.

Device-verified on the physical tablet, Academic Researcher seeded as a
Darkness Sequence 6 (Ritualistic Magic 16, SPI 17):

- **"3d6: 16 vs 16 — Success by 0–2 — Effect works as intended."** The exact
  hit, margin 0, which is the boundary the milder band exists to cover.
- **"3d6: 10 vs 16 — Success by 3+ — Effect surpasses intent. Entity pleased
  (if invoked)."** Margin 6, the deeper band.
- SPI ran 17 → 15 → 13, the fuel charged per attempt as before.

## 6bc. The Seven Orthodox Gods Blessings — read, marked, not extracted

Ch. 3 p. 19, lines 1098-1118. A complete table nothing had read: seven
deities, **75 points each**, Deity / Blessing Effect, plus a printed
requirement ("Character must be a confirmed believer... Losing faith
temporarily suspends blessings"). `advantages.json` claims to 1097 and
resumes at 1120, so the whole table falls in the gap — found by running the
seam sweep over chapter 3 rather than by reading the page.

Four of the seven carry numbers the engine could apply: `+3 HT vs pressure`,
`+3 ST, +3 DX, +3 HT`, `+5 to all occult/knowledge skills`, `1d6/round`.

**Not extracted, and the reason is the one this repo has now enforced three
times.** No premade prints a blessing — checked, not assumed: zero
75-point traits across all 23 fixtures, and no sheet names any of the seven
deities — and traits are still not editable (section 6's standing gap,
`SavedEdits` covers attributes and bought skills only). So a
`blessings.json` would be read by nothing, which is exactly the defect 6ai
found by grep, 6al found in the trait catalogue, and 6am found in the
disadvantage list. Three consecutive slices whose lesson was *do not do
this*.

**What would unblock it, recorded as the shape rather than started:** making
traits purchasable. That is a real feature, not a content pass, and it drags
in a design question this table poses sharply — the God of Combat's blessing
grants `+3 ST, +3 DX, +3 HT`, and M3's rule is that granted attributes are
never written into the billable fields but re-derived from a stored CHOICE
(section 6). A blessing is *bought* for 75 points AND grants attributes, so
it is the first thing in the book that would have to do both at once.

Also confirmed while looking, so the next reader inherits evidence: chapter
3's other 60 unclaimed lines are all already-marked restatements — the
attribute cost grid and its two worked examples (6ao), the Character
Creation Summary (6an), the skill ladder prose (6ac), the study grid (6an),
and the disadvantage cap, which `PointBudget.MAX_DISADVANTAGES` has enforced
since M0 (6ac). The one genuine oddity among them is ch. 3's own "+9" SPI
ceiling at 505-506, which chapter 9's data exceeds — recorded and
deliberately not repaired in 6ao.

## 6bd. Handover — the content is done; what is left are three decisions

Written at `5ab008f`. **Verify this the way 6ax asks you to verify 6ax** —
it is a predecessor's note, and 6ay/6az/6ba each overturned one.

Working tree clean, both suites green: **935 Kotlin, 253 Python**. No open
findings, no unverified fixes. Every slice from 6ay onward was Codex-reviewed
before commit and device-verified after.

### Coverage, and what it does and does not mean

100%: ch. 4, 7.6, 10, 11, 12, 13, 14, 15. High-80s: ch. 6 (85.4), 6.5 (89.3),
ch. 3 (86.2), ch. 7 (84.2). **Chapter 7's remaining 25 lines were enumerated
one by one in 6bc's session and NONE is actionable** — burning SPI, the
refused canon timing penalty, assistants, catalysts, a table heading,
descriptive SPI-by-Sequence prose, and rituals 8-10's range templates.

Remember coverage has now misled six distinct ways (6t, 6v, 6ae, 6af, 6av,
and 6aj's blindness to bare margin bands). **A percentage is a floor and an
argument, never a receipt.**

### The three decisions, ranked by rules unblocked per unit of work

| # | Decision | Unblocks | Where the evidence is |
|---|---|---|---|
| 1 | **A round tracker** | Five printed rules at once: grappling ST (2600-2601), free actions "once per round" (2457), surprise (2377), unaware "cannot defend at all" (2687), reload rate (2690) | 6ba's table |
| 2 | **Purchasable traits** | The Seven Orthodox Gods Blessings (75 pts each) and the 198-row trait catalogue generally | 6bc |
| 3 | **Quick Contests** | Feint (6o), grappling, and ch. 6.5's six contests (6ah) | refused since 6o |

**#1 is the best value and the clearest call.** All five rules are already
located and confirmed unimplemented; the only blocker is that this app holds
no notion of a round. Note that every clock-shaped feature has been refused
here on principle — SPI recovery (6ad), technique durations (6ah), downtime
periods (6an) — so a round tracker is a genuine change of policy and should
be decided deliberately, not slid in. 6au is the precedent for how to avoid
one: it built a *stateless* planner where a downtime tracker had been ruled
out, by finding the sentence that made state unnecessary.

**#3 is the one to be most careful about.** It needs both sides' attributes
and this app holds one sheet. That is not a gap to close casually; it is a
change to what the app IS.

### The sweep to run before starting any of them

6ba's lesson, which is the newest and least tested: **a prohibition sweep's
clean verdict expires.** 6ar swept line 4007 correctly and found nothing,
because the code contradicting it was written two slices later. Before
shipping a slice, check its own subject against the prohibition line-set
(`cannot|may not|must not|never|not allowed|not possible`) — not the whole
sweep, just whether any printed rule forbids what you have just made
possible. A round tracker in particular will make several "cannot" lines
reachable for the first time.

## 6be. The sheet screen was rolling a character who no longer exists

`liveLevelOf` and `grantedSkills` (app) put the digested character behind the
rows that roll. The slice began by chasing 6ba's parse finding, **retracted
it in the first ten minutes**, and the two defects it found instead were both
larger than the thing it set out to fix.

### 6ba's finding is retracted, and the retraction is a test

6ba recorded that Demoness Sequence 7 grants
`Ritualistic Magic (Mirror, Dowsing)`, which `skillLevelOf(RITUAL_SKILL)`
"never matches, so a caster the book grants the skill at IQ+4 gets no Ritual
button at all", and filed it as its own slice. **It matches.**
`PotionDigestion.skillKey` has split a bracket into a specialization since
M3 — the same line that turns `Guns (Pistol)` into name + specialization — so
the key's NAME is plain `Ritualistic Magic` and every exact-name gate finds
it. The retraction test digests that Witch and asserts the level (15) and the
specialization; it passed on the first run, against untouched code.

The lesson is 6ax's own rule, applied to 6ax's successor: **a predecessor's
finding is a claim, and the cheapest thing to trust wrongly.** 6ba reasoned
from the JSON's spelling and never digested the block. One test settled it.
Note also what 6ba got right — its "read the block before assuming it is a
spelling" warning was correct advice attached to a wrong conclusion.

### Hole A: the row displayed one number and rolled another

The published rows took their roll base from `sheet.levelFor(skill)` — the
level printed on the sheet, before any potion. A **Paragon Sequence 9**
Academic Researcher rolled Research at **13** with the digested character
standing at **17**, and ten of that one sheet's rows moved. Every Beyonder
this app can make has been rolling stale numbers since the edit layer
shipped.

- **The display is right and was left alone.** A comparison row shows the
  sheet's own figure because that diff is the engine against the book (6e).
  What it ROLLS is a different question, and answering both from one place is
  what made a potion invisible to the dice — the same shape as 6am's Will 10
  beside Stubborn 8, which is two correct numbers from two characters and
  must NOT be reconciled.
- **The fallback is unreachable and a test says so**, rather than a comment.
  All 23 premades, down all 22 pathways to Sequence 6, keep every published
  skill in the digested map. Codex called the silent fallback a stale roll
  waiting to happen; the answer was to pin its premise, because a defence
  nobody rechecks is exactly how a deferral goes stale (6au, 6av).

### Hole B: a potion-granted skill had no row at all

The screen listed one row per PUBLISHED skill and one per BOUGHT skill, which
between them cannot show a skill a potion handed over. **6aj opened this hole
and 6aq widened it**: 6aj gave bought skills their own rows precisely so
chapter 4's two spiritual-sense tables could be reached, then 6aq established
that ch. 9 p. 85 forbids buying the three SPI-based skills — closing the only
route to the rows 6aj had just built. A Fool Seq 9 character was granted
Spiritual Intuition, Divination Arts and Ritualistic Magic and could tap none
of them.

- **Ritualistic Magic and Divination Arts get a row as well as a button**, on
  purpose: the buttons cast and divine, and a GM may still call for a plain
  roll of the skill. Recorded rather than left silent.

### And the identity of a skill is not its name (Codex, two HIGH)

Both fixes first keyed a skill by name + specialization. **The book's identity
is name + DIFFICULTY** — p. 85 stacks a grant onto an existing skill only when
both match and treats a different difficulty as a new skill — and this repo
deliberately preserves five difficulty disagreements between the potion tables
and chapter 3 (4.1, recorded since M3). So the disagreement is not a footnote:
**a Barmaid Brawler who walks the Abyss to Sequence 6 holds Knife/Easy from
her sheet and Knife/Average from the potion, at different levels.** Six such
collisions exist in committed content.

Under the name key the published row answered with the potion's level, and
the granted skill was suppressed as a duplicate of a skill it is not. Verified
against the real content before being believed, then fixed and RED-proved.

- **Where two rows do share a label, the added row names its difficulty** —
  `Knife [A]`. Only added rows are ever suffixed; the published table keeps
  the book's own spelling. Two rows reading "Knife" with different numbers is
  the unreconcilable screen 6ag and 6ai were each fixed for.
- **One resolution, shared** (Codex's re-review). The label check first read
  only the sheet's rows, so a BOUGHT Knife still collided invisibly with a
  granted one; and the bought rows resolved a purchase by raw saved name while
  `grantedSkills` resolved it through the book, so a purchase the catalogue no
  longer prints failed to suppress itself and a potion's skill of the same
  name could render as bought AND as granted. `purchasedKeys()` is the single
  resolution both use now. Two copies of one decision, for the third time in
  this file (6ag, 6ai).

**Codex answered on every pass and found something on the first two** — two
HIGH plus a MEDIUM, then a HIGH plus a LOW on its own fixes, then clean. The
MEDIUM was refused with the test described above rather than a signature
change. Note that its first `review --wait` reported an empty diff because
HEAD was the commit under review; `task` with an explicit parent works.

**A count in my own prose was wrong, which is the habit turning on its
author.** Both this section and the test's comment said "all 20 pathways";
`pathways.json` holds **22**. The test iterates `distinct()`, so the code was
right and only the sentence was wrong — which is exactly how "all five
dialogs" and "six worked rituals" got in (6as, 6ab). Grep the number,
including your own.

Device-verified on the physical tablet (1920x1200), seeded through
`sessions.json`, all three findings on screen:

- **Academic Researcher as a Paragon Seq 9.** The Research row still reads
  **13** — the sheet's own number, because that diff is the engine against the
  book (6e) — and tapping it opens **"Skill 17"**. Rolling gave **"3d6: 13 vs
  17 — Good success, margin 4"**, which is the fix twice over: the level
  reaches the dice, and the degree ladder reads the corrected margin. Under
  the old code the same dice are a bare success at margin 0.
- **The same sheet as a Fool Seq 9**, the second observable exactly:
  **Ritualistic Magic 13**, **Divination Arts 12**, **Spiritual Intuition 12**,
  each marked `(potion)` — IQ 10+3 and SPI 9+3. These are the three skills
  6aq established cannot be bought, so before this slice no route reached
  them at all.
- **Barmaid Brawler down the Abyss to Seq 6**, which is the Codex finding and
  the sharpest of the three. Two Knife rows: the published one reads **10**
  and rolls **Skill 12** (DX/Easy, re-levelled on the risen DX), and the
  potion's is a row of its own reading **Knife [A] — 11 (potion)**. One name,
  two difficulties, two levels, and only the added row is suffixed. Under the
  name-only key the published row answered with the potion's level and the
  granted skill was suppressed as a duplicate of a skill it is not.

**A machine note, because it inverted the usual order.** The APK on disk
predated every fix, so a build was unavoidable at 39.85 GB of a 40.17 GB
commit limit — 324 MB free, which is 6ah's pagefile exhaustion. What made it
fit was building in ONE short-lived JVM rather than two daemons:
`--no-daemon :app:assembleDebug -Dorg.gradle.jvmargs=-Xmx1280m
-Pkotlin.compiler.execution.strategy=in-process`. It exits when done, so
there is nothing to reap afterwards. And the tablet made the emulator's
~3 GB moot entirely — **check `adb devices` before assuming an emulator is
needed**, then `adb shell service check window` to confirm it is not a zombie
(6as).

Two seeding notes that each cost a cycle: `files/` does not exist until the
app has run once on that device, so `run-as ... cat >` fails with "No such
file or directory"; and PowerShell's `-Encoding utf8` writes a **BOM**, which
`SessionStore` discards through its own `runCatching` — the app then shows a
mortal and the test silently passes as a false negative. Write it with
`UTF8Encoding($false)` and check the first bytes are `7b 22`.

## 6bf. A notes cell holds three rules, and the app applied one

`TraitEffects.PARTLY_APPLIED` reports the clauses an APPLIED trait does not
use. The slice began by reading 6bd's #1 decision at source, and produced a
correction to 6bd, a sweep class none of the five could see, and one fix.

### Correction to 6bd: four of its five rules need no clock

6bd ranks a round tracker first because it "unblocks five printed rules at
once". Read at source, only one of the five is about rounds:

| Line | Rule | What it actually needs |
|---|---|---|
| 2377 | "Surprised characters ... cannot defend on it" | a DECLARATION — the GM ticks it |
| 2687 | "you cannot defend at all" (unaware of the shot) | the same, ranged-scoped |
| 2453 | "one active defense **(unless you took an All-Out Attack)**" | the same again — a third, and 6bd does not list it |
| 2457 | free actions "once per round" | nothing numeric — a LIMIT recorded, as Block's own "once per round" already is (6ap) |
| 2689 | reload rate | rounds AND ammunition state — **double-blocked**, since weapons are not editable (section 6) |

So the enforceable surface is three defence WITHDRAWALS, all stateless, and
the one rule that genuinely wants a clock is also blocked by something else.
6bd's ranking would have spent a slice building global combat state for a
payoff of one rule. **This is 6au's move, not a new one**: that slice found
the sentence that made a downtime tracker unnecessary, and this one finds
that "surprised" is something the GM says rather than something the app
counts. Ranking a blocker without reading what it blocks is what produced
the error.

### The sweep class: clause-level drift inside a field that has a reader

`advantages.json`'s Combat Reflexes cell reads **"+1 to all active defenses,
+6 to recover from stun, never freeze in surprise"**. `defenseBonus` applies
the first. The other two reached nothing — and, worse, were **invisible**:
`reported()` keyed on the trait NAME and Combat Reflexes is an applied trait,
so it returned nothing at all. A GM saw no note, and "one who sees nothing
assumes it was counted" is that function's own KDoc.

- **This is not 6ai restated, and the distinction is what makes it a fifth
  direction.** 6ai grepped all 345 extracted fields for a consumer; `notes`
  HAS one. This is drift inside a field with a production reader, which
  every sweep in this repo starts too coarse to see — they ask about fields,
  and a prose cell holds rules.
- **6al's triage was correct when it ran** and went stale the moment one
  clause of a multi-rule cell was implemented. The general lesson is 6ba's:
  **a verdict expires**, and here the expiring event was this repo's own fix.
- **Naming a sweep obliges running it** (6ay). Cheap here — only three traits
  are applied at all: **Alertness** prints one rule and is clean; **Fit**
  prints two and "recover FP at twice the normal rate" is clock-shaped, so it
  joins SPI recovery (6ad), technique durations (6ah) and downtime (6an);
  **Combat Reflexes** as above. Three traits, six clauses, three applied.
- **The reported clause is quoted, and a test pins it against
  `ContentLoader`** — a clause the catalogue does not print would be this
  object inventing a rule in order to disclaim it.
- **The two maps must stay disjoint.** A trait in both would be reported
  twice under two explanations, the two-copies-of-one-decision failure this
  repo produced three times (6ag, 6ai, 6be). Pinned by a test rather than by
  care.
- **`never freeze in surprise` is one declaration away from applying**, which
  is the point: the report is not a permanent disclaimer but a statement of
  what is true today, and it should disappear when the surprise withdrawal
  ships.

### The design was reviewed BEFORE it was written

Overturning a recorded framing is exactly what section 7 routes to Codex
`adversarial-review`, so it ran first, on the decomposition rather than on a
diff. It returned **needs-attention with three findings**, and the HIGH is
the one above — that `reported()` suppresses the whole trait — which I had
not seen: I had found the unapplied clauses and assumed they were merely
unenforced, not hidden. It also confirmed the clause class is genuinely
distinct from 6ai, and independently reached the same verdict on 6bd's
ranking. **That inverted the slice**: the reporting fix is a prerequisite to
the withdrawals, is rules-only, and shipped alone.

Two readings it asked to have pinned when the withdrawals are built, recorded
here so they are not re-derived: model the refusal with an explicit CAUSE
(`SURPRISE`, `UNAWARE_RANGED`, `ALL_OUT_ATTACK`) rather than one boolean,
and let Combat Reflexes negate **SURPRISE only** — 2687's shot is a different
cause and the trait's clause says "in surprise". The withdrawal decision
belongs in one `core/rules` function; a dialog computing its own copy is the
failure named above.

Also found while checking: **"Wrestling Master" is named by the grappling ST
cap (2600) and appears in no catalogue this book prints** — the shape
`Dead Language Fluency` naming "Ancient Loen" already has (6x). Recorded, not
repaired.

**Codex reviewed the diff and returned clean.** RED was verified as a compile
failure on the new API, then the four tests were run green; the suite is
**949 Kotlin** (946 + 4 added − 1 replaced) and 253 Python.

Device-verified on the physical tablet, **Military Police** (the one premade
carrying Combat Reflexes): two rows now render where the trait previously
rendered nothing — **"Combat Reflexes — +6 to recover from stun / Not applied
here: this app models no stun recovery roll to make."** and **"Combat
Reflexes — never freeze in surprise / Not applied here: this app models no
surprise, so there is no freeze to prevent."** The applied clause is absent
from that list and still lands: the Defend dialog reads **Dodge 9** and
**Parry (Brawling) 9** against a sheet printing Dodge 8, which is 6al's own
verified number, unmoved.

**Next slice, with its design already reviewed:** the three defence
withdrawals. It is dialog wiring, which is the class only a device catches
here, so budget the tablet pass.

## 6bg. The defences the book refuses, and a fourth maneuver nobody counted

`ActiveDefenses` (core/rules) applies Ch. 5's three refusals of an active
defence, and `DefenseDialog` withdraws the rows. This is 6bf's named next
slice, built on the design Codex reviewed there.

**All three were in seams, which is why they were never applied.** Checked
before a line was written: lines 2377, 2453 and 2686-2688 fell in NO content
file's claimed span. Three printed rules, in three sections, none in a table
— the shape 6ay opened and 6ba widened, and the reason a chapter can read
"done" while a rule of it has never been read. They are claimed now, in
`defense_refusals.json`, because a rule nobody can find again is a rule that
goes unapplied (6af).

- **A refusal is a DECLARATION, and that is the whole reason this ships
  without a round tracker.** Whether a character was surprised, went all-out
  last turn, or never saw the shot is something the GM knows and the app
  cannot compute — it holds no turn, no initiative and no line of sight.
  What a tracker would add is EXPIRY, and expiry of a fact the GM restates
  each time they resolve an attack is not worth global state. 6au's move a
  third time.
- **Three causes, not one boolean.** They print different sentences, and
  Combat Reflexes cancels exactly one. A single flag would have to answer
  "why" with a guess — and a screen showing the wrong sentence beside a
  withdrawn defence is worse than showing none.
- **Combat Reflexes cancels SURPRISE alone.** Ch. 3 prints "never freeze in
  surprise" and the prose beside it says "prevents freezing on surprise", so
  the surprise rule is the freeze it names. The unaware shot is not a freeze
  — the bullet arrives before reaction — and an All-Out Attack is a choice
  the fighter made. Extending the clause to those would author a rule from a
  resemblance, which is what 6aj refused when ch. 4 printed none of Appendix
  A's words. A reading, pinned by a named test, and RED-proved: cancelling
  every cause fails two tests.
- **A cancelled cause is SAID, not silently dropped.** A GM who ticks
  "surprised" on a Combat Reflexes character and sees nothing move would
  fairly conclude the tick failed. And when another cause survives alongside
  it, the surviving one answers — naming the surprise would credit a trait
  that did not save them.
- **The maneuver table names FOUR forfeiting rows where p. 37 names three.**
  The sentence granting the defence excepts "an All-Out Attack maneuver";
  `maneuvers.json` has carried **"Cannot defend next turn."** on the three
  All-Out Attacks *and on Charge Attack* since 6i — extracted, committed,
  displayed, and applied by nothing, the class 6as found inside our own
  content. Specific beats general, so the table wins. The list is READ from
  that file rather than restated, because a second copy of a set the book
  prints is what 4.1 forbids; RED-proved by hardcoding the parenthetical's
  three.
- **Only the "cannot defend" half is enforced.** The surprise rule also says
  the character "skips their first turn entirely", and this app has no turn
  to skip. Enforcing half a sentence and saying which half is the honest
  version of a rule that is partly outside the model.
- **`verify()` refuses a refusal that prints a modifier.** None of the three
  offers a number to apply instead — they withdraw the defence outright — so
  a signed number appearing in one means the span caught a neighbouring rule,
  and for two of them the neighbour IS a defence modifier a few lines away.
- **The dialog takes the traits, not the +1.** Combat Reflexes now has two
  consequences here, and deriving one at the call site while the other is
  computed in the dialog is the two-copies failure this repo produced three
  times (6ag, 6ai, 6be).

**Codex reviewed the diff and returned clean.** 956 Kotlin (+7) and 256
Python (+3), all green.

Device-verified on the physical tablet, **Military Police** (Combat
Reflexes), all four states:

- Nothing declared: three chips render, **Dodge 9 / Parry (Brawling) 9**.
- **Surprised**: defences stand, with *"Combat Reflexes — "never freeze in
  surprise", so the defences stand."*
- **Surprised + Never saw the shot**: every defence row **gone**, the book's
  unaware sentence in red, and the Combat Reflexes note still shown beneath
  it — the surviving cause outranking the cancelled one, on screen.
- **All-out last turn**: *"Forfeits the next turn's defence: All-Out Attack
  (Determined), All-Out Attack (Strong), All-Out Attack (Double), **Charge
  Attack**."* directly above p. 37's own sentence, which excepts only "an
  All-Out Attack maneuver". The table's fourth row and the parenthetical that
  omits it, visible together.

**Left open, with evidence.** The free actions' "once per round" (2456-2457)
is also unclaimed and is a LIMIT to record rather than enforce, exactly as
Block's own "once per round" already is (6ap) — 6bf's table says so and this
slice deliberately did not widen to it. Reload (2689-2690) stays
double-blocked: rounds AND ammunition state, with weapons not editable.

**A self-inflicted lesson worth more than the slice.** Refreshing the test
totals with PowerShell's `Get-Content -Raw | Set-Content -Encoding utf8`
rewrote this entire file with a **BOM and double-encoded em dashes** — 901
lines changed — because `Get-Content` decoded UTF-8 as the ANSI codepage
first. The same trap recorded one slice earlier for `sessions.json` (6be),
walked into two hours later on a bigger target. **Never round-trip a UTF-8
file through PowerShell text cmdlets**; use the editing tool, which preserves
the encoding. `git checkout` cost nothing here only because the change was
uncommitted and the section could be re-applied verbatim.

## 6bh. Running the clause sweep, and finding a hole in 6bg

6bf named a sweep class — clause-level drift inside a field that has a
reader — and ran it over the three applied TRAITS only, which is where it was
found. 6bg then found another instance **by accident** while reading a
different table. Two of two by accident is not a method, so the sweep was
run properly: every prose cell in committed content, split into clauses,
keeping cells with **two or more** mechanical clauses. One clause per cell is
ordinary; two is the shape that hides one, because the field looks handled
when the first is applied.

**49 cells across 11 files.** Most are decisions already recorded — the trait
reports (6bf), the language-dependent clause 6x deliberately refuses, cover's
"+2 DR" 6k leaves on the damage side. One was a hole in the slice committed
an hour earlier.

### `extra_effort.json` prints the same forfeit sentence, on Feverish Defense

    Feverish Defense — Variant Rule: +2 to one defense roll.
                                     Cannot defend next turn.

Word for word what `maneuvers.json` prints on four rows, and what 6bg's
`FORFEIT_PHRASE` matches — but `forfeitingManeuvers()` read the maneuver
table alone. So the one extra-effort option that forfeits its defence was
missing from the list the defence dialog shows, and **invisibly**: four
All-Out rows plus a Charge looks complete.

- **The SENTENCE is the identity, not the table.** Both tables are read for
  the phrase now, so a third printing it is included the day it appears.
  `forfeitingManeuvers` became `forfeitingActions` for the same reason the
  chip stopped saying "All-out last turn": a name that describes one table
  makes the other rows look like a different rule.
- **RED-proved twice**, and the second is the one that matters: reverting to
  the maneuvers-only reading fails both new tests, which is the exact code
  that shipped in 6bg.
- **Codex caught what the fix broke** (P2). Broadening the chip left it
  quoting p. 37's "unless you took an All-Out Attack maneuver" at a GM whose
  fighter spent 1 FP on a Feverish Defense — a citation that does not cover
  their case. The operative sentence is now the tables' own, which covers all
  five, and p. 37's narrower printing is shown beside it with its scope said
  out loud. 4.1 records a disagreement rather than smoothing it, and that
  cuts both ways: the narrow printing is not deleted either.

### What else the sweep found, and what was left

**Three of the four extra-effort options are offered nowhere.** Only Heroic
Charge is wired (6j). Of the rest: **Giant Step** is movement, which this app
does not model; **Strong Blow** is "+2 damage. Regular melee attacks only",
and `Attacks.damageBonus` already composes exactly that kind of bonus — a
real gap, in the ATTACK dialog, and its own slice; **Feverish Defense**'s
"+2 to one defense roll" half is likewise unoffered. Named here rather than
built, because widening this slice into the attack dialog is how a fix
becomes a feature.

**The sweep's own limit, stated so the next one is not trusted too far.** It
keys on cells with 2+ mechanical clauses, so a cell with exactly ONE
unapplied rule is invisible to it — which is most of the trait catalogue and
every single-sentence effect cell. It finds the shape where an applied clause
HIDES an unapplied one. That is the class; it is not "every unapplied rule".

**A note on the instrument, not the app.** The tablet rotated to portrait
mid-run and three taps landed on the wrong premade. Worth checking before
concluding anything about layout — and the action row DOES scroll
horizontally in portrait, so Attack/Defend/Damage are reachable there; that
was checked rather than assumed, because an unreachable control is exactly
the defect 6j found.

**Codex reviewed the fix and, after the P2, returned clean.** 958 Kotlin and
256 Python, all green.

Device-verified on the tablet, **Military Police**: the chip reads
**"Forfeited last turn"**, and selecting it lists *"Forfeits the next turn's
defence: All-Out Attack (Determined), All-Out Attack (Strong), All-Out Attack
(Double), Charge Attack, **Feverish Defense — Variant Rule**."* above
**"Cannot defend next turn."** in red, above *"Page 37 prints it narrowly —
… — and names only the All-Out Attacks."* Five rows, the operative rule, and
the narrower printing, in that order. Also confirmed there: 6bf's other
clause, on a Fit character — *"Fit — recover FP at twice the normal rate /
Not applied here: this app holds no clock to recover against."*

## 6bi. Strong Blow — one word, and what it points at

`Attacks.strongBlow` applies Ch. 5's second extra-effort option, and the
attack dialog offers it. 6bh named this as its own slice: **"+2 damage.
Regular melee attacks only."**, 1 FP, extracted since the combat slice and
offered nowhere. `Attacks.damageBonus` already composed exactly this kind of
bonus — the class 6h, 6m, 6ai and 6ap each found, with the consumer already
built.

- **"Regular" names the Regular Attack MANEUVER, not "ordinary".** Three
  things settle it: that row is printed two lines above the bullet list; the
  sibling bullet keys to a row the same way ("Ignore **Charge Attack**
  penalties"), so this list references maneuvers; and read loosely the word
  does no work, because the sentence already says melee only. "Regular" is
  printed as an adjective for an attack **exactly once** in the book, which
  is what makes the maneuver row the only thing it can point at — the
  uniqueness grep 6bg used for "cannot be substituted". Under the loose
  reading All-Out Attack (Strong) is strictly dominated, 2 FP for the +2 this
  buys for 1, and the two stack to +4 for 3.
- **Refused on each of its scopes, never dropped**, because 1 FP is spent
  either way — the reason Heroic Charge outside a Charge throws (6j) and the
  reason a mistimed ritual is refused rather than ignored (6ay).
- **Codex found three states the reading did not cover, in three passes**,
  and each is a different kind of "nothing to add +2 to": **Shove**, whose
  cell reads "Special" and rolls no damage (6j); a skill with **neither an
  unarmed row nor a weapon**, which is most of them, since this book tags no
  skill as a combat skill and every skill is offered (6i); and a **firearm
  held in reach**, where `isRanged` is false but the rifle is still a gun.
- **That third one is why the offer is a rules predicate.** `isRanged` is
  where the fighter stands; whether the weapon is fired is what is held, and
  `modifiers` never sees the weapon. A screen computing its own condition
  would have been the two-copies failure this file records three times (6ag,
  6ai, 6be), so `offersStrongBlow` answers and the dialog calls it.
- **`withAttack` is `atRange`'s sibling.** Ticking the option and then
  tapping Shove has to drop it, the same way going to range drops the two
  melee-only attack options — and for the same reason: the guard is a
  `require`, so a screen that changed only the attack would hand the next
  composition an exception (6as).
- **A drift pin has to be EXACT here** (Codex, first pass). `PINNED` and
  `PINNED_OPTIONS` are substring checks because those cells are paragraphs
  around one number; an extra-effort effect cell IS the whole rule, so a
  sentence APPENDED to it ("Cannot combine with Rapid Strike") would leave
  the substring intact and the Kotlin scope stale. My own comment said
  "pinned whole" while the check was a substring — the count-in-prose habit
  (6as) applied to an assertion rather than a number.
- **Charged once per maneuver, not per attack.** A Regular Attack split by a
  Rapid Strike is still one maneuver, so the +2 rides the damage expression
  both attacks share. The book composes them nowhere; same reading as
  All-Out Double's single 2 FP (6i).

**Also confirmed, not assumed:** the prohibition line-set over lines
2380-2460 turns up nothing forbidding this — the sweep 6ba established
expires and has to be re-run against a new slice's own subject.

Every reading was RED-proved by neutering it in turn (the maneuver scope, the
melee scope, the `atRange` drop, the wounds guard); each failed, each was
restored. **966 Kotlin** (+8) and **257 Python** (+1), all green. Codex
reviewed four times and returned clean on the fourth.

Device-verified on the physical tablet (1920x1200), all four states:

- **Barmaid Brawler**, Brawling 13, Punch + Regular Attack: the chip renders,
  and ticking it takes the damage line **`1d-3 cr` → `1d-1 cr`** with
  "Attack options: no modifier, **costs 1 FP**" — the +2 landing on damage,
  the roll unmoved.
- **Tapping Shove with it still ticked**: the chip is **gone**, the line
  reads **"Rolls no damage; see the note above"**, and the cost line shows
  "-2" with **no FP** — the ticked option dropped rather than charged.
- **Streetwise 10** on the same character: no unarmed row, no weapon, **no
  chip and no damage line at all**.
- **Hunter-Tracker**, Bolt-Action Rifle toggled to **In reach**: damage reads
  **`5d pi+, ×1.5 there`** and Regular Attack is selected, and the chip is
  **still absent** — the one state where only the firearm fact excludes it.

**Still unoffered from the same table**, named in 6bh and unchanged here:
**Giant Step** (movement, unmodelled) and **Feverish Defense**'s "+2 to one
defense roll" half — its forfeit half already reaches the defence dialog
(6bh). Chapter 5 is at **80.8%** before and after: this was a rule printed
and unapplied, not an unclaimed line, and `coverage.py` cannot see the
difference.

## 6bj. Three rules on one line, and the sweep that had already flagged it

`ParryBlock.versusWeaponPenalty` and `.canParryRanged` apply the other two
rules on the Parry (Unarmed) row, and the defence dialog asks what is coming
in. The row prints **three** and this codebase applied one:

    Brawling parries weapons at -3. Boxing uses Boxing / 2 + 3 and ignores
    that penalty. Cannot parry ranged attacks.

6ap applied the cumulative penalty from the line above. These two were
committed in `defenses.json` from 6g onward, shown to the GM on that screen
ever since, and reached nothing — so one number stood for three situations,
and a bare hand turning a knife read the same as turning a fist.

- **The sweep had already found it and my triage lost it.** 6bh's clause
  sweep flags this exact cell with all three clauses; re-running it here
  showed the row plainly. What went wrong in 6bh was reading the shortlist,
  not building it. So the lesson is NOT "the instrument was too narrow" (6ba)
  — it is that a sweep's output is only as good as the pass over it, and 49
  cells is enough to skim. **Re-running a sweep you already ran is cheap;
  trusting its triage is not.** I also nearly recorded the opposite finding:
  a `grep -A3` truncated the sweep's own output at three lines and made the
  cell look absent. Check the instrument before blaming it.
- **Boxing's exemption is printed twice, and that is the warrant.** p. 37
  says "Boxing uses Boxing / 2 + 3 and ignores that penalty"; p. 38 says
  "Unlike Brawling, Boxing parries weapons without penalty". Two printings
  agreeing is what licenses reading the exemption as Boxing's alone rather
  than as loose phrasing — the per-table standard 6ad set, and 6v is why it
  can never be per-source.
- **Wrestling is named by NEITHER exemption**, and its own -3 is a different
  rule in a different place: inside the halving (6aw), always, weapon or not.
  So a wrestler parrying a knife takes both, which is why the two penalties
  are separate functions rather than one number. A second parry, of a weapon,
  bare-handed, is -4 AND -3.
- **The refusal withdraws the rows and says why.** "Cannot parry ranged
  attacks" is a refusal, not a penalty — the distinction
  `Languages.ritualModifier` draws for a Broken mystical tongue (6x), and a
  number there would imply a parry exists to modify. Withdrawn silently it
  would read as a screen that lost them, so the sentence is printed where the
  rows were. **Dodge is untouched**, which is the scoping visible on screen.
- **Scoped to the unarmed row that prints it.** An armed parry is not
  covered: chapter 8 gives no melee weapon a ranged defence rule, and
  extending the sentence to weapons would author one. Wrestling's section
  corroborates the scope from the other side — "Wrestlers can parry MELEE
  attacks with their hands" — which is what makes this a rule about hands
  rather than about one row.
- **What is coming in is a DECLARATION**, like the three refusals (6bg): this
  dialog holds the defender's sheet and the attacker is a party with no
  numbers on it. Three values because the row draws exactly those lines.
- **`canParryRanged` is called by the screen rather than duplicated in it.**
  The first version filtered on the enum directly, which left the predicate
  read by nothing — 6ai's defect, in the same commit that fixed 6ai's class
  one row over.

**Also checked, not assumed:** the prohibition line-set over 2380-2460 turns
up nothing forbidding any of this, and lines 2474-2475 were already claimed
by `defenses.json`, so this was a rule printed and unapplied rather than a
seam.

Both readings were RED-proved (the exemption widened to "not Brawling", the
refusal narrowed to Wrestling); each failed, each was restored. **971
Kotlin** (+5) and 257 Python, all green. Codex reviewed twice and returned
clean both times.

Device-verified on the physical tablet, **Barmaid Brawler** — the one premade
carrying two unarmed styles, then seeded as an **Abyss Sequence 9**, whose
potion grants Boxing, to get all three on one screen:

- Bare hand: Boxing **8**, Brawling **10**, Wrestling **7**.
- **A weapon**: Boxing stays **8**, Brawling **10 → 7**, Wrestling **7 → 4**
  — the exemption and the two that take it, side by side.
- **A shot**: every parry row **gone**, the stepper with them, *"No unarmed
  parry against a shot"* in their place, and **Dodge still 7**.

**Still unoffered from the extra-effort table**, unchanged from 6bh and 6bi:
Giant Step (movement, unmodelled) and Feverish Defense's "+2 to one defense
roll" half. That one was this slice's intended subject and was displaced by
reading the page it lives on — the deferral is now evidence rather than a
to-do (6au, 6av).

## 6bk. Two rules, one bonus, and a picker that had to be paid for once

`DefenseBoost` (core/rules) applies the two printed ways to buy +2 on a
defence, and the defence dialog charges the FP. This was 6bh's named next
slice — Feverish Defense's "+2 to one defense roll" half — and reading the
page it lives on turned one rule into two:

| Row | Table | FP | Scope | Drawback |
|---|---|---|---|---|
| Feverish Defense — Variant Rule | extra effort | 1 | one defense **roll** | Cannot defend next turn |
| All-Out Defense (Increased) | maneuvers | 2 | one active defense **until next turn** | Cannot attack |

Neither reached anything. `AttackManeuver` deliberately carries only the five
maneuvers that move an ATTACK roll, so the two defence maneuvers had never
been modelled anywhere at all — a gap no sweep had named, because the enum's
own KDoc explains the omission and reads as a decision.

- **Found by the SENTENCE, not the table** — 6bh's lesson, applied on purpose
  this time rather than by accident. Searching for "+2 to one" turns up both;
  searching the extra-effort table turns up one.
- **NOT merged**, because the book prices them in opposite directions: the
  cheaper one costs the next turn's defence, the dearer one costs this turn's
  attack. Which was paid is a real choice, so both ship and `verify()`-style
  tests pin that the two drawbacks still differ.
- **"+2 to ONE defence" is the whole reading.** Combat Reflexes says "+1 to
  ALL active defenses" and a shield's DB "adds to all active defenses", and
  both go through the one function every row calls. Routing this the same way
  would buy Dodge, every Parry and Block for 1 FP. So the bonus is keyed on
  the row the GM names, and the row is part of the purchase.
- **Only the rows actually rendered can be bought.** A parry withdrawn
  against a shot (6bj) is not purchasable either, or FP would buy a defence
  the book already refused.

**Codex found three P2s, and all three were mine.**

- **Swapping one paid chip for another charged twice** and showed one +2.
  Neither alternative works: refunding invents a rule, and keeping both
  applies two bonuses the GM sees as one. The picker DISABLES the others
  while one is held, so changing your mind means clearing first — and the FP
  stays spent, which is what the book says and what the label now says.
- **The paid row can VANISH under the payment.** Buy +2 on a bare-handed
  parry, then declare a shot: that row is withdrawn, every chip was disabled,
  and the bonus applied to nothing with no way out. The stale-state trap 6k,
  6y and 6af each found, one dialog on. The clear control is now outside the
  chip rows and always reachable, and the screen says the +2 landed on
  nothing.
- **Block was purchasable with no shield readied**, two lines above the row
  that says "Block needs one Readied" — the app contradicting its own screen.
  Both halves gate it now: the skill AND a shield.

**One P1 was refused with evidence.** Codex reported the enum's
`"Feverish Defense — Variant Rule"` as an em dash against a hyphen in
`extra_effort.json`, "throws during normal rendering". Both strings are
**U+2014**, checked by codepoint, and `fpCostOf(FEVERISH)` returns 1 in a
green test that would throw if the lookup failed. It reads files through
PowerShell and mis-decodes UTF-8 — the same mojibake 6ar recorded. **Check
`./gradlew` before acting on an encoding complaint.**

Both readings were RED-proved (the per-table cost hardcoded to 1; the bonus
applied to every row); each failed, each was restored. **976 Kotlin** (+5)
and 257 Python, all green.

Device-verified on the physical tablet, Barmaid Brawler (Brawling 13,
Wrestling 10, FP 10/10, no shield):

- Both rules render with their own sentence and price, three rows each, and
  **no Block row** — the third finding, visible.
- Buying **Feverish on Parry (Brawling)** took **FP 10/10 → 9/10**, printed
  "Paid 1 FP: +2 on Parry (Brawling) this turn", and **disabled every other
  chip**.
- The rows above read **Parry (Brawling) 9 → 11**, with **Wrestling 6** and
  **Dodge 7** unmoved — "+2 to one defence" on screen.
- Declaring **a shot** withdrew the parries and read *"The defence you paid
  for is not available against this attack, so the +2 lands on nothing. The
  FP stays spent."*, with only Dodge offered and the clear button still
  reachable. FP stayed at 9/10 throughout: no refund anywhere.

**The extra-effort table is now fully offered** — Heroic Charge (6j), Strong
Blow (6bi), Feverish Defense (here). The fourth, **Giant Step**, is movement
and stays unmodelled: this app has no position to step from.

## 6bl. Stunned — a state this app hands out and never charged for

`ActiveDefenses.everyDefence` applies Ch. 5's "-4 to Active Defense", and the
defence dialog has a Stunned chip. The rule was printed on one cell, produced
by three of this app's own tables, and priced by nothing.

**The app is where the state comes from, which is what makes it a defect
rather than a gap.** `fright_effects.json` prints "Stunned 1 sec" on six rows,
`major_wound.json` "Stunned for 1d6 seconds", and two ritual Critical Failure
rows "Stunned 1d minutes" — all rolled by dialogs that already exist. The word
**"stunned" appeared nowhere in Kotlin.** So a GM could roll a fright check on
this screen, be told the character is stunned, open the defence dialog two
taps later and see every number unmoved.

- **The -4 belongs to the STATE, not to the maneuver whose row prints it.**
  It is printed on Do Nothing — "If Stunned: -4 to Active Defense. Otherwise
  rest to recover FP." — and read as scoped to that maneuver a stunned fighter
  would dodge at full value right up until they chose to do nothing, which is
  the reverse of what the state is for. **Ch. 11 settles it**: an ability that
  leaves its target "mentally Stunned for 1 turn (see GURPS stun rules, p.
  B420: -4 to active defenses...)" names no maneuver at all.
- **That second printing is load-bearing twice**, which is why `verify()` pins
  it across chapters (6ba's precedent): it also writes "active defenseS", and
  the plural is the whole basis for taking the penalty to every row rather
  than to Dodge alone. Two printings agreeing is the per-table standard 6ad
  set, and 6v is why it can never be per-source.
- **One function for everything that moves EVERY row.** Combat Reflexes' "+1
  to all active defenses" already had to be folded in once because the screen
  renders a defence in seven places, two of them by hand (6o's Block branch).
  A second such rule applied at six of the seven is the drift this codebase
  produced three times (6ag, 6ai, 6be), so `everyDefence(traits, stunned)`
  answers for both and the dialog holds no arithmetic. Deliberately the
  opposite call to a bought [DefenseBoost], which the book scopes to ONE
  defence (6bk) — same screen, two sentences, two scopes.
- **Combat Reflexes does NOT cancel it.** Its stun clause is "+6 to recover
  from stun" — the recovery roll, which needs a clock — not an exemption from
  the penalty. Extending it would author a rule from a resemblance, the
  reasoning 6bg refused for the unaware shot. A stunned fighter with the trait
  is at **-3**, which is the number that would be wrong if either rule were
  applied alone.
- **The cell is quoted WHOLE**, second clause and all. Cutting it at render
  time to hide the FP rest would be a parser nobody tests (6f); leaving it
  visible is the clause-reporting 6bf asks for, and the pin is exact rather
  than a substring, because the screen shows this sentence (6bi).
- **The chip lives below the refusals and inside the block they guard**, so a
  character whose defences are withdrawn is not offered a -4 on nothing.

### The device found a stale disclaimer, and the test agreed with the bug

The sheet screen read **"Combat Reflexes — never freeze in surprise / Not
applied here: this app models no surprise, so there is no freeze to
prevent."** — two taps from a dialog that has cancelled the surprise refusal
with that exact trait since 6bg. 6bf wrote the disclaimer when it was true and
named the expiry in its own KDoc ("one declaration away"); the declaration
shipped one slice later and the sentence stayed.

- **A report of what is NOT applied expires exactly when the code changes**,
  which is 6ba's lesson pointed at this repo's own commentary rather than at
  the book. Every other instrument here starts from the book or from a field;
  a disclaimer is neither.
- **`TraitEffectsTest` pinned the disclaimer's own words**, so it could not
  notice — a test that quotes a claim cannot audit it. The new test reads BOTH
  objects: if `ActiveDefenses.state` cancels the surprise, no reported clause
  may mention surprise. That is the only shape that fails when the two drift.
- The stun clause is still reported, and correctly: recovering is not the
  penalty.

### The maneuvers.json clause sweep, with every verdict written down

6bj's lesson was that the sweep survived and the triage did not, so the whole
triage is here rather than only its finding:

| Row | Clause | Verdict |
|---|---|---|
| Do Nothing | "-4 to Active Defense" | **This slice.** |
| Do Nothing | "rest to recover FP" | Clock. Quoted, not applied (6ad, 6ah, 6an). |
| Aim (Ranged) | "Canceled by any Active Defense or injury" | **Candidate** — a declaration, like these three, with the Acc it cancels already modelled (6t). Its own slice. |
| Concentrate | "Roll Will-3 if injured or defending" | **Candidate** — a roll this app could make. |
| All-Out Defense (Double) | "Use two different defenses vs. one attack" | Recorded, not built — see below. |
| All-Out Defense (Increased) | "+2 to one active defense" | Applied (6bk). |
| Feint | Quick Contest | Both sides' attributes; unmodelled since 6o. |
| Aim / Charge / Rapid Strike / Deceptive | their numbers | Applied (6t, 6i, 6j). |

**All-Out Defense (Double) is a permission with nothing to permit.** Line 2453
prints the rule it lifts — "you may attempt one active defense" — and this
dialog does not allocate defences: it is a reference sheet saying what each
one WOULD be, which is the right shape for a screen that cannot know what is
coming. Enforcing the general rule would need a round and a chosen defence;
paying 2 FP to lift a limit the app never imposed would charge for nothing,
the exact defect 6bk's picker was fixed for. Recorded here, with its blocker
named, so the next reader inherits evidence rather than a to-do (6au, 6av).

Also confirmed rather than assumed: the prohibition line-set over 2370-2500
forbids nothing about a stun penalty (6ba — a sweep's verdict expires, so it
is re-run against each slice's own subject), and lines 2454-2455 are a second,
agreeing printing of the cumulative parry rule 6bj already applies.

**Codex reviewed both commits.** The disclaimer fix came back clean. On the
stun commit it raised one P2 — that the new `pytest.raises` cases would pass
vacuously because `verify` fails earlier on `Wait`'s movement cell — and it is
**refused with evidence**: `verify` on the committed content passes clean, and
with `PINNED_MANEUVERS_EXACT` emptied the drifted input raises nothing at all,
which is the definition of failing for the intended reason. Its premise was
that the em dash in `MOVEMENTS` did not match the JSON's; both are U+2014. The
PowerShell UTF-8 mis-decode again (6ar, 6bk) — **check the interpreter before
acting on an encoding complaint.**

**980 Kotlin** (885 core:rules, 75 app, 20 core:model) and **258 Python**, all
green. The stun composition was RED-proved by neutering the penalty to zero:
two assertions failed, -4 and -3, and were restored.

Device-verified on the physical tablet (1200x1920), **Military Police**
(Combat Reflexes, Brawling 11, Basic Speed 5.00 — sheet prints Dodge 8):

- Untouched, the dialog reads **Dodge 9 / Parry (Brawling) 9**, the trait's +1.
- **Stunned** takes both to **5** and prints the book's own cell verbatim:
  *Do Nothing (p. 37): "If Stunned: -4 to Active Defense. Otherwise rest to
  recover FP."*
- **Surprised AND Stunned together** is the discriminating screen: the rows
  stand at **5** under *"Combat Reflexes — "never freeze in surprise", so the
  defences stand."* One trait, two clauses, one cancelling a refusal and the
  other not touching the penalty.
- After the second commit the sheet's trait rows show **one** Combat Reflexes
  note — the stun recovery — where there were two.

## 6bm. The aim that survived a parry and a bullet

`Ranged.offeredConditions` applies Ch. 5's "Canceled by any Active Defense or
injury", and the attack dialog asks what happened since the shooter took aim.
This was 6bl's own named candidate, taken in order.

**The sentence is one table away from the rows it governs.** The two Acc rows
live in `ranged_modifiers.json` — "Shooter used Aim last turn / + weapon's
Acc" — and the clause that takes them away is printed on the Aim MANEUVER, in
`maneuvers.json`, extracted since the combat slice and read by nothing. So a
rifleman who parried between taking aim and firing still collected the full
Acc, which on a Bolt-Action Rifle is **+4, or +6 for a long aim**.

- **A refusal, not a penalty.** The book removes the aim rather than pricing
  it, so the rows are withdrawn and the sentence shown in their place — the
  call a Broken mystical tongue (6x) and an unarmed parry against a shot (6bj)
  each needed. Rows that simply vanish read as a screen that lost them.
- **The warrant is that the two printings agree where they overlap.** The
  maneuver says "max ACC+2" and the modifiers table "max Acc +2 total": one
  printed ceiling, said twice, which is what licenses reading a maneuver's
  sentence as governing another table's rows at all. The per-table standard
  6ad set, pinned in both languages, and the cell is pinned EXACT because the
  screen quotes it (6bi).
- **A declaration, so no clock is needed.** Whether the shooter defended or
  was hurt since aiming is something the GM knows and this app cannot compute
  — the same reading that made the three defence refusals buildable without a
  round tracker (6bg), and the third slice running to reach a rule that way.
- **The withdrawal lives in rules and the screen calls it.** A dialog
  filtering its own list is a second copy of a printed refusal, which is how
  these numbers stopped reconciling three times (6ag, 6ai, 6be).
- **`modifiers` refuses a plan that still carries the aim**, rather than
  stripping it quietly: a turn was spent taking it, and a caller told nothing
  would believe the Acc was counted. Nothing reachable can build that plan —
  which is precisely why the guard exists, since the permissive path is what
  the next caller finds (6l).
- **Declaring it drops a selected aim row**, because the guard would otherwise
  turn a stale chip into a throw rather than a mislead — the stale-state trap
  6k, 6y and 6af each found. It clears on "Another turn" with the distance and
  the cover, for the same reason they do.

**983 Kotlin** (888 core:rules, 75 app, 20 core:model) and 258 Python, green.
Both readings were RED-proved by neutering them together — the withdrawal made
a no-op and the guard made unconditional — which failed one test each, and
were restored. **Codex reviewed it and returned clean.**

Device-verified on the physical tablet, **Hunter-Tracker** with the
Bolt-Action Rifle (Guns (Rifle) 10, Acc 4):

- A fresh dialog offers **Shooter used Aim last turn +4** and **for 3+ turns
  +6** under a new **"Since taking aim"** row.
- Selecting the +4 aim and then **Defended or was hurt** removes **both** rows
  and prints *Aim (Ranged): "Add weapon's ACC to your next ranged Attack. +1
  per additional Aim (max ACC+2). Canceled by any Active Defense or injury."*
  The selected aim goes with them: the summary reads **"Range and cover: -5"**
  at 51-100m, where it would have read -1 with the Acc still in.
- Rolling gave **"3d6: 18 vs 5 — base 10, modified to 5"**: the band alone.
- Reopening restores both chips with the declaration cleared — verified in
  both directions (6bk).

**A note on the instruments, which cost three taps each.** `uiautomator dump`
reports this dialog in a 900x1440 space against a 1200x1920 screen, and
scaling by the root frame's ratio still landed two rows low — 6ae's finding,
unchanged: **a dialog's bounds do not take the activity window's scale.** A
`screencap` read as an image gives physical pixels directly and got it right
first time. Also live again: the tablet's unrelated health app throws an ANR
dialog that holds window focus, so check `mCurrentFocus` before concluding the
app ignored a tap (6ah).

**Left where 6bl put it**, unchanged by this slice: Concentrate's "Roll Will-3
if injured or defending" is the last unapplied clause in that table with a
mechanical hook, and All-Out Defense (Double) stays recorded rather than built
— it lifts a limit this app never imposes.

## 6bn. A roll the book asks for only sometimes

`Concentration` (core/rules) rolls Ch. 5's Concentrate maneuver, and the sheet
screen grew a **Concentrate** button. Named as a candidate by 6bl's own sweep
table and taken in order: *"Channel powers or skills requiring focus. **Roll
Will-3 if injured or defending.**"* — in `maneuvers.json` since the combat
slice, read by nothing, so every ability this book gates behind a Concentrate
maneuver went unopposed however badly the caster's turn was going.

- **The roll is CONDITIONAL, and that is the whole reading.** Undisturbed, the
  book asks for no roll at all — so `attempt` REFUSES rather than answering,
  and the dialog withdraws the Roll button and says why. Applied
  unconditionally it would tax every act of focus in the book (a ritual's
  channelling, the gauntlet's aura, the Astral projection) with a penalty that
  names a circumstance none of them is in. The refusal-over-a-plausible-number
  call `Detection.attributeFor` (6ah) and `Pools.canAffordSpi` (6ar) needed.
- **One declaration, where the defences needed three.** 6bg split its refusals
  into three causes because they print three sentences and Combat Reflexes
  cancels exactly one. "Injured" and "defending" share a clause, a target and
  an effect, and nothing in the book tells them apart — so splitting them here
  would be inventing a distinction in order to display it. **The shape of the
  rule decides the shape of the control**, not the precedent.
- **The fourth rule running to become buildable as a DECLARATION** (6bg, 6bl,
  6bm), against a handover that ranked a round tracker first (6bd). Whether
  the character was hurt or parried while concentrating is the GM's to say.
- **No consequence is generated, because none is printed.** The book prints
  the roll and stops. `ConcentrationOutcome` carries the target, the roll and
  nothing else — a test asserts those are its only fields, so the day a
  consequence row turns up the decision has to be made again rather than
  inherited. The call ch. 4's two sense tables needed (6aj), and the screen
  says it out loud: *"The book prints no consequence for a lost focus."*
- **No GM stepper**, unlike the fright triggers and the darkness row: those
  exist because the BOOK declines to pick a value there. This cell prints one
  number and no range, and a circumstance beyond it is Appendix A's business.
- **Ungated, like Fright and Detect.** The maneuver is "powers or skills
  requiring focus" and this book tags no skill as one, so which of them counts
  is the GM's call — curating a list here would be inventing content (6i).
- **No printed critical override, so `SuccessRoll`'s stand** — a rolled 3
  holds against any target. The `FrightCheck` reading (6c), not the PCR's.

**989 Kotlin** (894 core:rules, 75 app, 20 core:model) and 258 Python, green.
The conditional refusal was RED-proved by making the `require` unconditional:
one test failed, and was restored. **Codex reviewed it and returned clean.**

Device-verified on the physical tablet, **Antiquarian Dealer** (Will 10), all
four states: the dialog opens quoting the cell with **no Roll button** and
*"No roll: the book asks for one only when the character is injured or
defending."*; ticking **Injured or defending** reads **"3d6 ≤ 7 — Will 10-3"**
and reveals Roll; a rolled **8 vs 7** titles **"Lost the focus"** above the
no-consequence sentence; and unticking the chip clears the result back to the
no-roll line — an outcome belongs to the circumstance it was rolled under
(6n, 6z).

### The next slice, found while reading this one and NOT taken

The Major Wound table's **success** row prints *"-4 to all rolls for 1 turn
from shock and pain"* (line 2824) and nothing applies it — the app rolls that
table itself (6n), so this is 6bl's shape exactly: a state the app hands out
and never charges for.

**It also disproves a recorded excuse.** `TraitEffects.UNAPPLIED` says High
Pain Threshold is unapplied because *"this app models no shock penalty to
ignore"*, and 6al argued that applying it would mean "inventing a rule to
justify reading another: a 'shock penalty' so High Pain Threshold could ignore
it". **The book prints one**, on a table this app already rolls. 6bf's lesson
— a sweep's excuse is the thing to check — turned on 6al a second time.

Left for its own slice because it needs a decision this one does not: "-4 to
**all rolls**" is the first declared state that would have to reach every roll
in the app, and the only single join is `MainActivity.conditionsFor`, which
feeds six dialogs and is built from TRACKERS rather than declarations. The
defence rows are not on that path at all. Design it first (6bf's precedent:
`adversarial-review` on the decomposition, before a line is written).

## 6bo. Shock — the state the app hands out, and a design review that ran first

`Shock` (core/rules) applies the Major Wound table's success row, *"-4 to all
rolls for 1 turn from shock and pain"*. `DamageDialog` has rolled that table
since 6n and printed the cell as text; nothing applied it. 6bl's shape exactly,
on the OTHER half of the same roll — a failure stuns, a success shocks, and
until now neither reached a number.

**It also disproves a recorded excuse, which is why it was worth doing now.**
`TraitEffects.UNAPPLIED` said High Pain Threshold reached nothing because *"this
app models no shock penalty to ignore"*, and 6al reasoned that applying it would
mean "inventing a rule to justify reading another: a 'shock penalty' so High
Pain Threshold could ignore it". The book prints one, on a table this app
already rolls. **6bf's lesson — a sweep's excuse is the thing to check — turned
on 6al for the second time.**

### The design was reviewed before a line was written, and came back no-ship

6bf's precedent, and it earned its keep: `adversarial-review` on the
decomposition returned **needs-attention with three findings**, two of which
changed the build.

- **`conditionsFor` is not the only ledger.** It feeds six dialogs, and the
  Major Wound HT, Fright, self-control, concentration and detection rolls each
  compute their own target. Wiring only the ledger would have applied "all
  rolls" to skills and silently missed five roll types — and the gap would look
  like an oversight in a UI adapter rather than a reading. So the scope is an
  enum, `RollKind`, decided in rules and tested per class.
- **The declaration needed one owner.** A chip per dialog lets the same
  one-turn fact disagree with itself across surfaces; kept in `DamageDialog` it
  dies when that dialog closes.

That second finding is what produced the better reading: **shock is LATCHED,
not declared.** The app rolled the table, so it knows — the reasoning
`SavedSession.corrupted` already uses (6a) — and the GM clears it when the turn
passes. Not persisted: a one-turn fact that survives a force-stop has outlived
its turn by construction.

### The scope, decided rather than discovered

| Roll | Shock |
|---|---|
| Skill, attack, ritual, divination, boon | **applies**, through the ledger |
| Fright, self-control, concentration, detection | **applies**, each passed the same number |
| Active defences | **applies**, through `everyDefence` (6bl's function) |
| The Major Wound's own HT roll | **refused** |

- **"All rolls" is taken at its word, and that is a reading.** GURPS 4e exempts
  active defences from shock; this book prints no exception, and GURPS's
  conventions are GURPS's (6l).
- **The one refusal is about WHEN, not what.** That HT roll creates the shock,
  so applying it there would let a state penalise the roll that produced it —
  invisibly, since one dialog rolls and displays both.

### The Cancel a review asked for, refused with a test

Codex asked for `ModEffect.Cancel` from the trait so the cancellation would
show in the resolved contributions. **`Cancel` is keyed by `SourceKind`, and
every state penalty in this app shares `SourceKind.STATE`** — `Conditions`
emits reeling, fatigue, Spiritual Attrition and the CoR ladder through the same
source — so a budget of 4 aimed at STATE would also offset a dying character's
reeling. High Pain Threshold ignores shock, not fatigue. It is the first slice
that wanted `Cancel` and could not use it; the trait suppresses the modifier at
source and `TraitEffects` reports it, and a named test carries the disproof so
the decision cannot be reversed by argument alone (section 7: Codex output is
evidence, not orders).

### Three more findings, and the last one was the sharpest

- **The latch fired on the ROLL, not the hit.** Roll the table, then cancel,
  change the pool, or drop the damage below the threshold, and a -4 followed
  the character for a wound nobody took. It latches on Apply now.
- **A shocked target moved without the breakdown saying why.** Three dialogs
  collapse the ledger with `sumOf` into one number labelled "hurt or tired" or
  "circumstances"; shock is neither. They take it as its own argument and name
  it — the invariant 6ag added a 300-case test for.
- **The knockdown clause had a roll after all.** I was one commit from shipping
  *"+3 on HT rolls to avoid knockdown — this app rolls no knockdown roll to
  modify"*, a **fresh excuse in the same commit that removed an old one**. The
  Major Wound's own HT roll decides knockdown on its fail-by-5 row ("Knocked
  down and unconscious"). It applies now, so High Pain Threshold has no
  disclaimed clause left at all.

**997 Kotlin** (902 core:rules, 75 app, 20 core:model) and 258 Python, green.
The `MAJOR_WOUND_HT` refusal was RED-proved by dropping it from `penaltyFor`:
one test failed, and was restored. Four Codex passes; the fourth returned clean.

Device-verified on the physical tablet, both branches:

- **Barmaid Brawler** (High Pain Threshold, HT 10): the panel reads **"Roll HT
  13"** with *"includes +3 from High Pain Threshold — this roll's fail-by-5 row
  is a knockdown."* A success then **Cancel** left the sheet with no shock row
  at all; a success then **Apply 6 to HP** took HP to **4/10** and printed
  *Shock — "-4 to all rolls…" — **ignored by High Pain Threshold***.
- **Military Police** (no such trait, Combat Reflexes): the same hit prints the
  row **without** the ignored clause; the defence dialog reads **Dodge 5** and
  **Parry (Brawling) 5** where it read 9 and 9; and Brawling rolled **"3d6: 11
  vs 7 — base 11, modified to 7, `state:shock: -4`"**.

## 6bp. Two clauses, one cell, and a design review that killed both builds

`TraitEffects` applies the second clause both Balance rows print — "+6 to
avoid knockdown; **+2 to Acrobatics, Climbing, Piloting**" — and reports the
first. The slice set out to build the knockdown roll and came back with
something a third the size, which is the review earning its keep.

- **A skill NAME is the strongest join there is.** Alertness has to be scoped
  by controlling attribute because "all Per rolls" names no skill (6al); this
  cell names three. `modifiers` already takes the `SkillKey`, so the join was
  there all along. **Pickpocket carries Perfect Balance beside Climbing** and
  rolled it flat.
- **The clause sweep FOUND these two cells and the triage lost them.** Both
  carry exactly two mechanical clauses, which is 6bh's own filter — re-running
  it prints them. They were filed under "trait reports (6bf)", a bucket that
  never contained Balance. **6bj's lesson, a second time**, and verified by
  re-running the sweep rather than recalled: 17 advantages match that filter.
- **The knockdown clause is refused, not defaulted.** Five rows print one and
  only High Pain Threshold names an attribute ("+3 on **HT rolls**"), which is
  what joined it to the Major Wound roll in 6bo. The book prints two OTHER
  knockdown mechanics that are DX-based — Shove's "target rolls DX or fall",
  Takedown's opposed DX — and three hit-location rolls (Neck -5, Face -5,
  Skull -10) with no attribute at all. Which of them an unscoped "+6 to avoid
  knockdown" covers is settled by nothing printed.
- **Piloting is in no skill list this book prints.** The "Wrestling Master"
  shape (6bf) and `Dead Language Fluency` naming "Ancient Loen" before it
  (6x). Recorded, not repaired — and because the join is by NAME, the day a
  Piloting row appears the bonus reaches it with no second decision.
- **Iron Jaw and Rapid Recovery are reported whole**, the way `Danger Sense`
  already is — which is the precedent that made listing traits no premade
  carries a consistent call rather than a new policy. Glass Jaw needs no entry:
  it is a disadvantage, and 6am's path prints those whole.

### The review returned no-ship on BOTH builds, before any code

Run on the decomposition, 6bf's precedent. Two HIGHs, each fatal to a
different half:

- **The location knockdown roll would invent an attribute.** The penalty is
  printed and the attribute never is; this repo refuses rather than defaulting
  (6ah, 6ar). Two data points — HPT's cell and the Major Wound roll — argue HT
  and do not print it. So the three cells stay displayed and unrolled, with a
  sharper reason than "nothing consumes them" (6av's standard).
- **A location declaration on `DamageDialog` would be a second hit-location
  concept**, beside `AttackDialog`'s called shot, which already owns the
  to-hit penalty and the effect cell. Legibility, not a broken invariant — the
  two never co-occur, since one is the played character attacking and the
  other is them being hit — so the decisive blocker is the attribute alone.
  Recorded that way rather than as the review phrased it: **evidence, not
  orders** (section 7), the same rule used on its mojibake findings.
- **The MEDIUM's typed API was refused and its caption half taken.**
  `Knockdown.effectsFor(traits, cause, location, attribute?)` would have one
  caller and one applied trait — an abstraction with one implementation, which
  section 7 bans outright. Build it the day a second knockdown roll exists.
  The caption moved: `DamageDialog` spelt "includes +3 from High Pain
  Threshold" itself, so widening the bonus would have named the wrong trait —
  the two-copies-of-one-decision failure (6ag, 6ai, 6be). `knockdownLine`
  authors it now.

### A finding I nearly recorded, and 6bo stands

The first framing was "6bo shipped a live defect: Perfect Balance's +6 never
reaches the knockdown roll." That is only a defect **if** Balance attaches to
the Major Wound HT roll, which is precisely the reading nothing settles. Under
the reading taken it is correct as shipped. A predecessor's code is a claim
like its prose (6ax) — and the check is the same one that overturned 6ba's
parse finding in 6be: read the source, do not reason from the shape.

**What 6bo's +3 does buy is worth recording.** That one roll decides three
outcomes — shock, stun, and knockdown on its fail-by-5 row — so a bonus "to
avoid knockdown" also makes the stun row less likely. The alternative is to
move only the fail-by-5 boundary, which is arithmetic the book prints nowhere;
the literal reading applied to an HT roll that avoids knockdown is the smaller
claim. Recorded, not smoothed (4.1).

**1002 Kotlin** (907 core:rules, 75 app, 20 core:model) and 258 Python, green.
RED was a compile failure on the new API, then both readings were neutered
together — Balance joining the Major Wound roll, and the skill clause dropped
— which failed **3** tests, and were restored. The prohibition sweep was
re-run over this slice's own ranges (6ba) and forbids nothing about a skill
bonus or stacking. Codex reviewed the diff and returned clean.

Device-verified on the physical tablet, **Pickpocket**, all three states:

- The sheet prints **"Perfect Balance — +6 to avoid knockdown"** above *"Not
  applied here: the book's knockdown rolls for a hit location print no
  attribute to roll against…"*. Before this slice that trait rendered
  **nothing at all** — `reported()` keys on the two maps, and it was in
  neither.
- **Climbing** opens at "Skill 11" and rolls **"3d6: 4 vs 13 — base 11,
  modified to 13, `trait:perfect balance: 2`"**, while the sheet row still
  reads 11 — the published diff left alone (6e).
- **Escape**, the same character and the same 11, rolls **"3d6: 8 vs 11"** with
  no contribution line at all. The bonus does not leak to a skill the row does
  not name.

**An instrument lesson, and it is 6af's with a new cause.** Git Bash's MSYS
path conversion rewrites a device path: `adb shell screencap -p /sdcard/s.png`
silently wrote to `/Files/Git/sdcard/s.png`, so `adb pull /sdcard/s.png`
returned **a screenshot from an earlier session** — a different premade
entirely, which I read as a live screen. The tell was the clock in the status
bar (10:55 against a build stamped 23:42). Prefix every `adb` command that
names a device path with `MSYS_NO_PATHCONV=1`, and **check the status-bar
clock on any screenshot that surprises you.**

**The next candidate, named with evidence and not taken:** Night Eyes prints
"Reduce all darkness penalties by 2" and the ranged dialog has carried a
darkness row since 6k — an applicable-looking clause in a trait cell, one
`UNAPPLIED` entry away from the same shape as this slice.

## 6bq. Night Eyes, and a relief that must land after the bounds check

`DarknessCause` (core/rules) applies Ch. 3's Night Eyes to the one darkness
penalty this app prices. 6bp named it with evidence and it held: *"Reduce all
darkness penalties by 2; in dim gaslight or moonlight suffer no penalty at
all."* — extracted since M0, reaching nothing, while ch. 5's "Darkness or heavy
fog" row has been a chip on the attack dialog since 6k. **Night Watchman
carries the trait and Guns**, so its owner could always reach the row.

- **One row, two causes, and the trait covers one.** "Darkness **or heavy
  fog**" charges both with one number; the trait says "all **darkness**
  penalties" and names only light levels. Eyes that see in the dark do not see
  through fog. Extending it would author a rule from a resemblance — the
  reasoning 6aj refused when ch. 4 printed none of Appendix A's words.
- **The second clause cannot be derived from the first**, which is what makes
  it a state rather than a special case: subtracting 2 from the gentlest
  printed severity gives -1 and the book says **0**. Specific beats general.
- **Three states because the source produces three outcomes** — fog untouched,
  darkness relieved by 2, dim light cancelled — which is the only warrant for
  drawing a line the combat table does not (6bn's lesson, applied the other
  way: there one chip, here three, because the source distinguishes three).
- **A DECLARATION, so no clock and no second sheet.** The fifth rule running to
  become buildable that way (6bg, 6bl, 6bm, 6bn), against a handover that
  ranked a round tracker first (6bd).
- **It defaults to Darkness rather than to nothing.** The trait's general
  clause is unconditional and the row's own first-named cause is darkness; the
  two departures are what the GM declares. Starting unset would take the trait
  away from its owner unless they thought to say what the weather was — the
  printed-and-not-applied defect in miniature, reintroduced by the fix for it.
- **The row is found by the RANGE it prints, not by its name**, so a
  re-extraction that reworded the condition cannot silently withdraw the
  chips. The idiom `AIM_ROWS` already uses one table over.
- **Appendix A's "Adverse conditions (dark, noisy, dangerous) -4" is
  deliberately not touched.** It prices three causes as one, so taking 2 off
  would claim the whole -4 was the dark. 6ae already withholds that row
  entirely on a ranged shot, where chapter 5 prices the same circumstance
  specifically.

### The design review returned no-ship again, and the second finding was sharp

Run on the decomposition before a line was written — 6bf's precedent, third
slice running. Both findings were accepted; neither was refusable.

- **HIGH: gating the darkness chip on the trait would delete the base rule for
  everyone else.** My own decomposition said "the chip row is offered ONLY to
  a character who owns the trait" — I meant the cause control, and the harmful
  reading is the one a reviewer sees. An ordinary shooter would have been
  unable to take a penalty the book charges them. **Write the sentence so the
  destructive reading is impossible**, not so the intended one is available.
- **HIGH: the relieved value would fail the row's own `require` and throw on
  Roll.** `gmValue` is validated against the printed -9..-3, and `RangedTest`
  has pinned since 6k that **-1 is refused there** as gentler than the book's
  best end. So relieving through that door crashes on the button — the lock-up
  6bk's picker was fixed for, and "a chip that throws is not a choice" (6t).
  The GM's severity stays raw and inside the printed range; the trait moves the
  result afterwards. That existing test is now load-bearing evidence for the
  separation rather than a rule about validation.

**The relieved row says which trait relieved it.** The chip reads "-3 to -9"
and the contribution would read -1 with nothing joining them — the breakdown
that does not add up, which 6ag added a 300-case test for and 6ai repeated. The
description is authored in rules, not by the screen (6bp's `knockdownLine`, one
slice on).

**1007 Kotlin** (912 core:rules, 75 app, 20 core:model) and 258 Python, green.
RED was a compile failure on the new API; then fog and dim light were neutered
together into the general clause, which failed **2** tests, and were restored.
The prohibition sweep was re-run over this slice's own ranges (6ba) and forbids
nothing about reducing a darkness penalty. Codex reviewed the diff and returned
clean.

**One test failed on my own arithmetic**, which is the count-in-prose habit
(6as) turned on an assertion: I wrote that a reduction of 3 against -3 leaves
-1. It cancels. The floor test now uses a severity the relief cannot reach.

Device-verified on the physical tablet, **Night Watchman**, all three states on
one screen, plus the regression the review named:

- Selecting the darkness row reveals **"What is it?"** with three chips,
  **Darkness** pre-selected, and the trait's own cell quoted underneath.
- **Darkness** reads **"Range and cover: -1"** — the -3 with 2 off.
- **Heavy fog** reads **"Range and cover: -3"** — the book's full penalty.
- **Dim gaslight or moonlight** reads **"Range and cover: no modifier"**, and
  rolling gave **"3d6: 12 vs 10"** against Intimidation 10: the penalty gone
  from the dice, not merely from the summary.
- **Apothecary**, who has no Night Eyes, still gets the darkness row and gets
  **no "What is it?" row at all** — the base rule intact for everyone else.

**Two instrument notes, both already recorded and both re-earned.** A dialog's
uiautomator bounds do not take the activity window's scale (6ae), so three taps
in a row landed on the wrong control until I switched to reading screenshot
pixels; and every `adb` command naming a device path needs `MSYS_NO_PATHCONV=1`
(6bp), which was used throughout this time.

**The next candidate, named with evidence and not taken:** `Iron Jaw` (+2) and
`Glass Jaw` (-2) both scope themselves to "the head", and the hit-location
table prints Skull, Face and Neck each with a knockdown roll — but no attribute
to roll it against, which is exactly what 6bp's review blocked. That one needs
the book to name an attribute, not another slice.

## 6br. The other five, and a sheet that printed the effect inside the name

`TraitEffects.SKILL_BONUSES` applies every trait whose cell names the skills
it lifts, and `catalogueName` gives all four matchers one resolution. 6bp did
this for the Balance rows; **its own sweep listed seventeen advantages and its
triage read one of them.**

- **Flexibility says "+3 to Climbing and Escape" and Pickpocket carries it
  beside Perfect Balance** — the character 6bp was device-verified on, whose
  Climbing roll was reported there as *"modified to 13, trait:perfect balance:
  2"* when three more were owed. **6bj's lesson for the third time**: the sweep
  survived, the pass over its shortlist did not. Re-running it is what found
  this; the query was right both times.
- **Healer's +3 is its whole First Aid bonus.** The cell also prints "+2 to all
  rolls to diagnose, treat, and heal", and a First Aid roll *is* a roll to
  treat — so stacking would charge +5 on the one roll the cell singles out with
  the word "specifically". The general clause names a PURPOSE and this book
  tags no skill as a healing skill, the refusal `Languages` makes for
  "language-dependent skill rolls" (6x), so it is reported.
- **Two traits naming one skill both apply.** Nothing makes independently
  purchased advantages exclusive; taking the better of the two is arithmetic
  the book never asks for.
- **The close-quarters clause was CHECKED, not excused.** 6bo found a
  near-identical excuse for High Pain Threshold was false — the book printed a
  shock penalty all along. So "close-quarters" was grepped: it occurs exactly
  twice, both inside these two trait cells. There is nothing to ignore.
- **"Body Sense" is printed by no skill list**, occurring once in Absolute
  Direction's own cell. The Piloting shape (6bp), "Wrestling Master" (6bf) and
  "Ancient Loen" (6x). Kept in the set, so the name join reaches it the day the
  skill appears.

### The review blocked it, and the cause was bigger than the finding

`adversarial-review` on the decomposition — third slice running, third
no-ship. Its HIGH: adding Healer would give **Church Healer** a First Aid bonus
while its clauses stayed invisible, because `levelOf` prefix-matched and
`reported` did not. Checking that turned up the real defect:

**Seven fixture traits store the name and the book's effect cell as ONE
string**, because the published sheet ran them together on one line —
`"Healer - +2 to all rolls to diagnose, treat, and heal; ..."`. Nothing
resolved those, so **Charity's self-control roll could not be made on the one
character whose defining drawback it is**, five slices after 6am shipped those
rolls.

- **The tail must equal the catalogue row's own notes**, ignoring the sheet's
  wrapping. That is an EXACT join: the sheet is quoting the book verbatim, so
  the two are provably the same trait — unlike 6am's "Absent-Minded" against
  "Absent-Mindedness", where the only evidence is resemblance and 4.1 says the
  disagreement is recorded. **Three of the seven match and four do not**, which
  is the discriminator working rather than a rule that happens to fire.
- **One resolution, four consumers** — the applied bonuses, `reported`,
  `SelfControl.checksFor` and the sheet's disadvantage rows. Two matchers is
  how a bonus keeps its own explanation off the screen (6ag, 6ai, 6be, 6bp).
- The MEDIUM was accepted and **Ether Body Damage left out**: "All SPI-based
  rolls at -1" is a controlling-attribute join like Alertness, but `bonus()`
  hardcodes `advantage = true` and the coverage test reads the advantage
  catalogue only. The first disadvantage-driven modifier is its own slice.

**A second review pass caught two clauses that were neither applied nor
reported** — Absolute Direction's "Always know which way is north" and
Double-Jointed's "any body part bends any way". That is worse than an untouched
trait, because the sheet then shows the cell as understood. The guard is
general: a test walks each handled cell's own segmentation and fails on a
clause that is neither, with the one descriptive lead-in named so the exemption
cannot widen.

**1016 Kotlin** (921 core:rules, 75 app, 20 core:model) and 258 Python, green.
Three readings were RED-proved by neutering them — Healer stacking to +5, two
traits taking the better rather than both, and the exact-tail rule relaxed to
any tail — failing 3, 3 and 1 test respectively. The prohibition sweep over
this slice's ranges turns up only Absolute Direction's own "never become lost",
which is reported rather than applied.

**A self-inflicted loss worth recording.** RED-proving the coverage guard, I
restored the file with `git checkout` instead of the backup I had taken — which
discarded the whole implementation, since it was uncommitted. The tests
survived, so rebuilding it was mechanical, but **`git checkout <file>` is not
an undo for a neutering experiment**: keep the `cp` backup and restore from it.

Device-verified on `Medium_Phone_API_36.1`, both halves:

- **Pickpocket**, Climbing: **"3d6: 7 vs 16 — base 11, modified to 16,
  `trait:perfect balance, trait:flexibility: 5`"**. 6bp's own device run
  reported 13 on this roll.
- **Church Healer**: **"Charity — −10 pts — 3d6 ≤ 11"** with the book's effect
  cell, a roll that did not exist before this slice, and Healer's two unapplied
  clauses each with their reason. **"Sense of Duty (Flock) - Must help/protect
  certain groups even at personal risk"** still renders glued — its tail is not
  the book's words, so it stays unmatched and recorded, which is the
  discriminator visible on screen.

**The device found the one defect nothing else could**, and it is 6bl's shape:
the glued name rendered in full, so each row printed the same sentence twice —
once as the name, once as the clause below it. Every test was green, because
each asserted the right lookup and the right clause; a 150-character name reads
as a broken row rather than a wrong one. The collapse fires only where the tail
was verified, so an unmatched name keeps every character the sheet printed.

**Left with evidence, not taken:** the 36 `(specify)` instantiations —
"Sense of Duty (Patients)" against the catalogue's "Sense of Duty (specify)",
"Duty (PMC)", "Phobia (Cities)", "Vow (Poverty)" — which are a real matching
question of their own and are NOT the glued shape. And `Ether Body Damage`,
above.

## 6bs. Two rows that name their skills in a bracket, and one bare HT roll

`TraitEffects` applies Charisma and Outdoorsman, and Fit and Unfit reach the
Major Wound's HT roll. The slice set out to build one of 6br's two named
deferrals and built neither, because a **census of every trait the 23 fixtures
actually carry** found three better candidates and disqualified both
deferrals on the spot.

**Ether Body Damage is unbuildable and 6br did not say why.** It named it as
the next slice — "the first disadvantage-driven modifier" — which is true and
irrelevant: **no premade carries it**, and a potion in this codebase grants
attributes, SPI, secondaries and skill levels, never a trait (6al). So it
would be a rule nothing can reach, which is the `blessings.json` refusal 6bc
already made. A deferral is only as good as its reading (6au, 6av), and this
one had never been checked against the fixtures.

### The census, and what a reachability filter is for

Every trait string on the 23 sheets, resolved through `catalogueName`, split
into clauses, keeping the mechanical ones. 70 distinct strings. It is 6bh's
clause sweep with the filter that matters most here: **owned by somebody**.
Three findings, all of them printed, all of them reaching nothing:

| Trait | Carried by | Clause |
|---|---|---|
| Charisma 2 | Fortune Teller | +1/level to six named Influence skills |
| Outdoorsman 1 | Hunter-Tracker | +1 per level to seven named Outdoor skills |
| Unfit | Occult Scholar | −1 to all HT rolls |

### The bracket is the list, and the evidence is one skill

Both cells name their skills **in a parenthetical**, which is the only reason
6br's set missed them: same join, one printing shape away. "Influence skills"
reads like a category, and this book really does print five skill banners —
COMBAT, SOCIAL, KNOWLEDGE, THIEF, PHYSICAL, at lines 1668/1728/1777/1861/1877.
Influence is not one of them, and the decisive fact is smaller than that:
**Streetwise, inside Charisma's own bracket, is not under the SOCIAL banner.**
A category reading would widen the trait onto every social skill and narrow
it off Streetwise in the same change.

- **The same phrase encloses a DIFFERENT list one content file over.** A Witch
  ability in `pathways.json` reads "+2 to all Influence skill rolls
  (Diplomacy, Fast-Talk, Sex Appeal)". Two enumerations, one phrase, so each
  cell's bracket is its own. Pinned by a test that reads both.
- **All thirteen names are skills this book prints**, checked by value before
  the map was written — unlike Piloting and "Body Sense" (6bp), which are in
  the map precisely because the name join will reach them the day they appear.
- **Per level, capped at 4**, which each cell prints for itself ("[max 4
  levels]", "Max 4 levels."). The cap is a property of those two rows, not of
  levelled traits: Alertness is "+1 per level" and prints no ceiling, so a
  general cap would invent one for it. `SkillBonus` spells `perLevel` out
  rather than inferring it from a nullable ceiling, and the two constructors
  make the wrong default impossible to reach.
- **Charisma's other half is a reaction modifier**, so it is reported: this
  book prints no reaction roll and at least five sources hand one out, which
  is why 6w established each is shown beside its source and never summed.
  Its cell joins the two halves with "and" in ONE sentence, so the clause
  guard cannot see the reaction half on its own — the skill names inside the
  same segment satisfy the check. Reported explicitly for that reason.

### The design review caught my census claiming the wrong carrier

Fourth slice running to be reviewed before any code. **My decomposition said
Journalist carries Charisma and that both rows were level 1.** Journalist
carries neither; **Fortune Teller prints `Charisma 2`**. A count in prose is a
finding (6as), here about a carrier's identity rather than a number — and it
made the slice better, because the multiplier is now observable on a real
premade rather than only in a test.

Its second finding was structural: **`known()` did not include
`SKILL_BONUSES`.** Every earlier row happened to appear in `PARTLY_APPLIED`
too, so the "every trait named here is one the book prints" guard covered them
by accident. Outdoorsman is the first row that is wholly applied with no
clause left to report, so a typo in its name would have made the modifier
unreachable in silence.

### Unfit was not buildable as reviewed, and what that exposed is bigger

`TraitEffects.modifiers` is scoped by a `SkillKey`'s controlling attribute.
**Occult Scholar owns eleven skills and every one is IQ-based**, so Unfit as
approved would have shipped a rule that reaches nothing on its only carrier.
Checking that turned up the real defect:

**This app makes exactly one bare HT roll — the Major Wound's — and Fit never
reached it.** Its target was `ht + knockdownBonus`, High Pain Threshold's +3
(6bo) and nothing else. **Fit is on six of the 23 premades**, and all six have
been rolling it a point low since 6n shipped that roll. "+1 to all HT rolls",
not "all HT-controlled SKILL rolls" — the same structural gap 6al had to
thread for Alertness, whose "all Per rolls" could not reach the detection
roll.

So the slice split in two, which the review recommended: Fit first, Unfit
second.

- **`knockdownBonus` and `knockdownLine` collapsed into one `MajorWoundHt`.**
  6bp moved that sentence into rules because the screen was spelling out which
  trait paid; it stayed named after the knockdown alone until a second trait
  reached the same roll, at which point a caller adding one bonus and printing
  the other shows a breakdown that does not sum — the invariant 6ag added a
  300-case test for. The number and its explanation now arrive together, and a
  test walks every combination summing the signed numbers in the sentence.
- **`Shock`'s KDoc still said High Pain Threshold's knockdown clause reached
  nothing.** That stopped being true in 6bo, one slice after it was written.
  A disclaimer expires exactly when the code changes (6ba, 6bl) — the second
  time this file has found one of its own.
- **Unfit carries `advantage = false`, read from the CATALOGUE.**
  `ModifierSource.FromTrait` maps it to `SourceKind.DISADVANTAGE`, which is
  what a `ModEffect.Cancel` budget is measured against, so a penalty tagged as
  an advantage would let a cancel aimed at one bucket eat the other. Nothing
  reaches that today, which is the 6al precedent for pinning it now. Deriving
  it from the catalogue rather than passing it in is the point: it is a fact
  about the trait, and a second place deciding the sign is how these
  decisions have drifted four times (6ag, 6ai, 6be, 6bp). `Layer` stays
  `ADVANTAGE` — that enum is the resolution stage and prints no drawback
  member.
- **`reported()` and three guard tests read the advantage catalogue only**, so
  a drawback was invisible to the guards that exist to see it. The shape 6am
  found one list over, when every character's disadvantages rendered nowhere.
  All four read both now.
- **Unfit's FP clause is reported with the same words Fit's mirror clause
  already carries**, and its third sentence — "Unfit for sustained physical
  exertion." — is named in the guard's prose exemption one sentence at a time,
  so that exemption cannot widen into "anything I did not handle".

**1023 Kotlin** (928 core:rules, 75 app, 20 core:model) and **258 Python**,
green. Five readings RED-proved by neutering each in turn — the level
multiplier (3 failures), the cap (1), flat rows scaling (1), Fit reaching the
bare roll (2), and the disadvantage sign (1) — each restored from a `cp`
backup rather than `git checkout`, which is what cost 6br its implementation.
Codex reviewed both commits; the second returned clean, and the first raised
Unfit's absence, which is the split its own design review had recommended.

**My own test regex was the one thing that failed.** The caption-sums test
matched `[+−-]\d+`, so the trailing sentence "fail-by-5 row" counted as a −5
contribution. Every contribution is explicitly signed, so the hyphen is gone
from the class.

Device-verified on the physical tablet (1200x1920), five screens:

- **Fortune Teller** (Charisma 2): Savoir-Faire rolled **"3d6: 8 vs 14 —
  Excellent success, margin 6, base 12, modified to 14, `trait:charisma: 2`"**.
  Its reported half renders above: *"Charisma 2 — +1/level to reaction rolls /
  Not applied here: this book prints no reaction roll to make…"*
- **Diplomacy on the same character** rolled **8 vs 11 with no contribution
  line at all** — it is in the Witch's bracket, not Charisma's, and the bonus
  does not leak. The discriminating screen.
- **Hunter-Tracker** (Outdoorsman 1): Tracking read **"base 11, modified to
  12, `trait:outdoorsman: 1`"**.
- **Occult Scholar** (Unfit, HT 9): the Damage dialog at 6 reads **"Roll HT
  8"** over **"includes −1 from Unfit."**, and the sheet shows *"Unfit —
  recover FP at half normal rate / Not applied here: this app holds no clock
  to recover against."* — a clause on a DISADVANTAGE, which `reported()` was
  structurally blind to.
- **Beat Cop** (Fit, HT 9): the same dialog reads **"Roll HT 10"** over
  **"includes +1 from Fit."** It read 9 before this slice.

**Not observed live**, and resting on its tests the way the ritual CoR charge
rests on `RitualResolutionTest` (6f): the two traits composing on one roll.
**No premade carries both Fit and High Pain Threshold** — checked across all
23, not assumed.

**Two instrument notes.** A stray tap during the Damage sequence switched the
tablet to a completely different app, and the screenshot showed a Flutter demo
page; `mCurrentFocus` named it immediately (6ah). And `MSYS_NO_PATHCONV=1`
disables path conversion for **both** arguments, so an `adb pull` with it set
writes the LOCAL file to a Windows-relative path — pull to an absolute
`C:/...` path, not to `$TMPDIR`.

**The next slice, named with evidence and not taken: skill categories.** The
book prints those five banners and `extract_content.py` discards them —
`skills.json` carries no category field. So "-3 to social skill rolls" names a
group the book prints, and the app cannot identify it *only because the
extractor dropped it*: the "a normaliser can delete a rule" shape, which
`strip_self_control_number` already had once (6am). Six traits key off it —
Empathy +3, Callous −3, Clueless −3, Shyness, Loner, Workaholic — but the
census says only **Empathy** is both unconditional and name-matched; the rest
are conditional declarations or unmatched spellings. An extractor change plus
a 158-row content regen for one applied trait, so it wants its own slice and
its own design review.

Also recorded, not built: **Acute Hearing** ("+2 to Hearing rolls") and
**Acute Vision** ("+2 to Vision rolls") — "Hearing" and "Vision" are not
skills this book prints, checked against `skills.json`, so both are reported.

## 6bt. The fifteen banners a normaliser threw away

`skills.json` carries a `category` now, and `TraitEffects` applies the two
unconditional cells that name one. This is 6bs's named next slice, and 6bs was
wrong about it three times over — which is the slice.

**The rule.** `tools/extract_content.py` walked the Complete Skill List and did
`i += 1  # category banner only` on every banner, so the book's own grouping
was read and discarded. A trait cell saying "+3 to social skill rolls" named a
group the book prints in fifteen places and nothing in this app could identify
it. The `strip_self_control_number` shape (6am): **a normaliser can delete a
rule**, and it did so for four milestones.

- **Fifteen banners, not the five 6bs's prose claims.** It named the first five
  and stopped: COMBAT, SOCIAL, KNOWLEDGE, THIEF, PHYSICAL — then CRAFT &
  TECHNICAL, SPIRITUAL (SPI-based), SURVIVAL & OTHER, INVESTIGATION &
  DETECTION, PERFORMANCE & ARTS, ACADEMIC, TRADE & CRAFT, ANIMALS & OUTDOORS,
  UNDERWORLD & COVERT, MILITARY & NAVAL. A count in prose is a finding (6as),
  and this one is my own from one slice ago.
- **The banners PARTITION the list**, so `category` is required with no
  default: 159 skills under banners against `skills.json`'s 158, the one
  difference being `Inventor!` and its Wildcard difficulty. A row with no
  banner means the extractor's bounds moved, and defaulting it would withdraw
  every category-scoped rule from that skill in silence.
- **The membership is read from the content, never restated in Kotlin.** That
  is the opposite call to `BALANCE_SKILLS` and Charisma's six, and the
  difference is which thing names the skills: those CELLS enumerate theirs, so
  there is no printed table to read, while this cell names a category the book
  tabulates. A second copy of a set the book prints is what 4.1 forbids —
  6bg's precedent for reading the forfeit list out of `maneuvers.json`.
- **Streetwise is under KNOWLEDGE, and that is load-bearing.** It is the
  evidence for 6bs's separate reading that Charisma's bracket ("... Sex Appeal,
  Streetwise") is its own enumeration rather than a reference to this banner.
  Pinned by `verify_skills` now, so the day the book moves it that reading has
  to be made again rather than inherited.

### The census inverts 6bs's plan, and my own regex hid half of it

6bs said "only **Empathy** is both unconditional and name-matched", so Empathy
is the row to build. Checked against the fixtures AND against what each carrier
can roll:

| Trait | Cell | Carriers | Own a SOCIAL skill? |
|---|---|---|---|
| **Empathy** | "+3 to social skill rolls" | Apothecary | **no** |
| **Clueless** | "-3 to **all** social skill rolls" | Church Healer, Monastery Scholar | **no, both** |
| Callous | "-3 ... **when warmth or empathy is required**" | Barmaid Brawler, Docks Bouncer | yes |
| Shyness | "-1 to -2 ... **depending on severity and audience**" | Academic Researcher | yes |
| **Workaholic** | "-2 to **social rolls** in leisure settings" | Factory Foreman, Private Investigator | yes |
| **Loner** | "-1 to **social rolls** in crowds" | Hunter-Tracker | no |

So the two UNCONDITIONAL rows reach nothing on any carrier, and every carrier
that owns a social skill carries a CONDITIONAL row. **The last two rows were
invisible to my first census** because its regex required the word "skill"
after the category word, and those cells say "social rolls" — the reviewer
found Workaholic and re-running on the category word alone found Loner and
eight uncarried siblings. 6ba's lesson in a new instrument: **the filter was
the bug**, and a sweep's false negatives are invisible by construction.

**Empathy and Clueless still ship, and the distinction from 6bs's Unfit is
real.** There, the carrier's eleven skills were all IQ-based and no potion
could change the attribute a roll is controlled by. Here a potion grants social
skills on many rungs (Observation ×7, Intimidation ×6, Acting ×5, Detect Lies
×5, Psychology ×5, Fast-Talk ×3, Diplomacy ×2, Holdout ×1), which is the book's
own route (6aq) and one the app already rolls (6be).

### "social rolls" and "social skill rolls" are two things, and the book says so

Global line 6522: **"(-1 to all emotional reaction rolls and social skill
rolls)"** — both phrases, one bracket. So the bare form is not shorthand for
the other.

- "social skill rolls" appears in five trait cells, a worked ritual's intent and
  a potion drawback table. The only places this book groups skills as social are
  that phrase and the banner; the two other hits are descriptive prose about
  what IQ covers and a language clause. So the phrase names the banner.
- "social rolls" appears seven times, is never defined, is never a banner, and
  the rows using it read like REACTIONS — "+2 to social rolls there; people
  look out for you", "-2 to **first-impression** social rolls with strangers".
  This book prints no reaction roll procedure or result table anywhere (6w), so
  the phrase may name rolls this app cannot make or skills it can, and nothing
  printed settles which. Refused and reported, the call
  `Languages.ritualModifier` makes about a common tongue's silence (6x).

Conditional rows are refused for a different reason and get their own sentence:
Callous, Shyness and Social Anxiety name a circumstance the GM declares, and
two of the three are carried beside a social skill, so applying them flat would
charge a penalty nobody declared.

### The design was reviewed twice before any code, and Workaholic came out

Fifth slice running to be reviewed as a decomposition (6bf's precedent), and
the first to be reviewed **twice** — rev 1 came back no-ship with the census
gap above and a second finding I had not seen, that widening the resolver
without widening `displayName` would reintroduce 6br's device-only defect.
Rev 2 came back no-ship again, on Workaholic:

**its cell also prints "rolls to take a day off at Will-3"**, a self-control
roll `parse_self_control` cannot see, because its anchors are "roll vs." and a
bare 3d6 target (6am). So reporting Workaholic's social clause as unapplied
would add an explanation while hiding a roll the existing machinery could make.
It is **split out entirely** rather than half-reported — widening those anchors
touches a five-row agreement warrant and two deliberate refusals, which is its
own slice. Loner needs no such split: its "roll vs. Will" already parses, so
its social clause is a clause beside an applied one, which is what
`PARTLY_APPLIED` is for.

### The glue, in a second commit

Church Healer and Monastery Scholar print **"Clueless -3 to all social skill
rolls; ..."** as one string with a bare space and no separator, so
`catalogueName` — which split on `" - "` — resolved it to no catalogue row at
all. No effect line on the sheet, no report path, and no way for the -3 to
reach the skills it names. The trait is the most common social row in the cast
after Callous.

- **The join stays exact, which is what keeps it out of 4.1's territory.** The
  tail must reproduce the row's own notes, so the sheet is provably quoting the
  book rather than resembling it — unlike "Absent-Minded" against
  "Absent-Mindedness" (6am), where a name is similar and nothing else agrees.
  Four sheet spellings still fail the test and stay unmatched, including
  "Sense of Duty (Flock) - Must help/protect ...", which is on the same
  character.
- **Only the sheet's typography is normalised away**: its line wrapping, the
  U+2212 it writes where the catalogue writes an ASCII hyphen (6u's
  two-kinds-of-minus, here between two files rather than inside one table), and
  a trailing full stop that Church Healer drops and Monastery Scholar keeps on
  the same trait. None of the three can make a quotation a different sentence.
  RED-proved: without that normalisation, three tests fail.
- **The glue is stripped exactly, never trimmed greedily.** `trimStart(' ', '-')`
  would eat the first character of any cell that opens with its own "-3" — which
  is precisely the cell being matched here.
- **`displayName` shares the one resolution** instead of splitting on the
  separator itself. That was the reviewer's finding and it is 6br's defect
  restated: a spelling that resolved for MATCHING while failing to resolve for
  DISPLAY would apply the trait and render its whole 130-character effect cell
  as the trait's name. Two matchers is how this file's numbers have stopped
  reconciling five times (6ag, 6ai, 6be, 6bp, and 6bs's caption).

**1030 Kotlin** (935 `core:rules`, 75 `app`, 20 `core:model`) and **261
Python**, all green. Every reading RED-proved by neutering it — the category
join removed (2 failures), the conditional and bare-phrase rows applied (2),
the typography normalisation dropped (3), the exact-tail rule relaxed (3, one
of them 6br's own guard). Both commits Codex-reviewed clean. The prohibition
sweep was re-run over the skill list and both trait sections (6ba) and turns up
nothing forbidding a category-scoped bonus; the one adjacent line is Alcohol
Tolerance's "Never suffer social penalties from moderate drinking", which no
premade carries and which is scoped to drinking this app does not model.

Device-verified on the physical tablet (1200×1920), both signs on one pathway —
Church Healer and Apothecary each seeded as a **Visionary Sequence 9**, whose
potion grants Psychology, Body Language, Observation and Detect Lies:

- Church Healer's sheet reads **"Clueless — -10 pts"** with the book's cell
  underneath, where before the commit the NAME was the whole sentence — and two
  rows above it **"Sense of Duty (Flock) - Must help/protect certain groups even
  at personal risk — -5 pts"** still renders glued, which is the discriminator
  visible on screen.
- Its **Psychology** rolled **"3d6: 3 vs 12 — base 15, modified to 12,
  `trait:clueless: -3`"**. Before this slice that roll was 15 flat.
- Its **Body Language** — same character, same IQ attribute, same potion, same
  level 15, INVESTIGATION & DETECTION rather than SOCIAL — rolled **"9 vs 15"**
  with **no contribution line at all**. The scope, on screen.
- **Apothecary's** Psychology rolled **"3d6: 6 vs 17 — base 14, modified to 17,
  `trait:empathy: 3`"**: the same pathway, the opposite sign.

Not observed live and resting on their tests: the conditional refusals, which
are absences (a Callous carrier's Intimidation roll moving by nothing looks
identical to a screen that forgot the trait), and the potion-drawback printing
of Callous at line 6859.

**The next slice, named with evidence: Workaholic.** Two rules on one cell, and
the reason it was split out here. `parse_self_control` recognises "roll vs. X"
and a bare 3d6 target; this cell writes "rolls to take a day off at **Will-3**",
which matches neither, so the trait carries no `selfControl` while Factory
Foreman and Private Investigator both own it. Widening the anchor has to keep
the five-row agreement that warrants reading a bare number (6am) and both
deliberate refusals (Duty's frequency roll, Phobia's Fright Check), so it wants
its own design review. Its social clause then joins the same slice, refused as
Loner's is.

## 6bu. A roll the book requires without saying "roll vs."

`parse_self_control` reads a fourth printed spelling, and Workaholic's roll is
reachable on the two premades that carry it. 6bt named this slice and split it
out; its design review ran **four times and returned no-ship three times**, and
every finding was mine to fix.

**The rule.** Five rows call for a self-control roll in five word orders and
none uses the phrase the anchor was keyed on:

| Row | Cell | Target |
|---|---|---|
| **Workaholic** | "rolls to take a day off **at Will-3**" | Will−3 |
| Compulsive Gambling | "requires **Will roll at -2** to leave a game while ahead" | Will−2 |
| Bloodlust | "**IQ roll** necessary to accept a surrender" | IQ+0 |
| Coldblooded | "**Roll Will** when suppressing evil desires" | Will+0 |
| Reluctant Killer | "**Will rolls required** to use violence again" | Will+0 |

Workaholic is on **Factory Foreman and Private Investigator**, which printed the
cell and offered no roll. The consumer needed no change at all — `checksFor`
reads `selfControl` off the catalogue, so widening the extractor made five rows
rollable on their own.

- **The warrant is NOT 6am's A/B agreement**, which is unavailable: no row
  prints two of these spellings, so there is nothing to reconcile. What
  licenses it is that they add no new mechanic — the same two target shapes (a
  flat number, or an attribute with a signed offset) and the same direction.
  Stated plainly rather than borrowed, because 6ad's per-table standard means a
  warrant has to be produced for each reading, not inherited from a neighbour.
- **The offset must belong to the TARGET, never to the roll.** That is the whole
  discriminator against ~80 modifier cells of near-identical shape — Fit's "+1
  to all HT rolls", Hard to Kill's "+1/level to HT rolls to avoid death",
  Incorruptible Will's "+3 to all Will rolls **to resist**", which is the most
  self-control-shaped phrase in the catalogue. **Grief-Stricken is refused on
  exactly that reading**: "Will rolls to act clearly **are at -2**" attaches its
  offset to the roll, which is Ether Body Damage's shape, not Workaholic's.
- **D is tried last**, so a row printing "roll vs." is never read by the looser
  anchor and B stays the narrow reading it was built to be.
- **The provenance stays `"effect"`.** A review caught that the `source:
  "requires"` I proposed would have failed content loading outright —
  `Content.kt` REQUIRES source in {both, name, effect} — and these ARE
  effect-cell spellings, so no new value was needed in the first place.

### The polarity field I proposed, and my own KDoc disproving it

Rev 1 argued that "rolls to take a day off" INVERTS the direction every existing
row uses, and proposed a second field for which outcome is the failure. **Five
existing rows already make success perform the resisting action** — Stubborn "to
reverse your stated position", Laziness "to undertake any sustained effort",
Charity "to avoid helping", Reckless "to back down", Bad Temper "to avoid angry
outbursts" — so Workaholic is that shape and not a new one.

Worse: `SelfControlOutcome.held`'s KDoc has said so **since 6am**, in this
sentence — *"Every one of the book's phrasings runs the same direction once read
carefully"*. So the field would have been a sixth copy of a decision this file
already documents, proposed in a decomposition that quotes the rule against it.
Read the KDoc of the thing you are about to extend.

### Five filters, five too narrow — and the sixth found by a test

Every census this slice and 6bt ran was too narrow, each in a new way:

| Filter | Missed |
|---|---|
| category word + "skill" (6bt) | Workaholic, Loner — cells saying "social rolls" |
| "at Will-N" / "Will roll" | Coldblooded, Reluctant Killer, Grief-Stricken |
| the bare word **"roll"** | three of the four Fright Check rows |
| the reviewer's own widening | Reluctant Killer, Grief-Stricken |
| my named-mechanic list | Fearlessness, Faithful Grounding, Glass Jaw, Soul Scar |

**The third is the one that is not a regex mistake.** A Fright Check is a roll
the book NAMES rather than describes, so no filter drawn from the word "roll"
can see one — `Phobia: Sacred Symbols`, `Chronophobia (Temporal)` and `Soul
Scar` were invisible to every sweep this repo has ever run, and only
`Phobia (specify)` was pinned, because its cell happens to also say "-1 to all
rolls".

So the enumeration is a **test** now rather than a paragraph in a design
document: `every named mechanic is applied, reported, or rolled` walks both
catalogues for Fright Check, morale roll, death roll and knockdown roll, and
fails on any row that is none of the three. It found four more the moment it
ran, which is the point — a paragraph cannot do that.

**And the RED-proof found a hole in that guard.** Widening the anchor to accept
"SPI-based rolls at -1" produced a wrongly-parsed row and `verify()` passed
anyway, because it was pinning only the rows I had thought of. The modifier rows
closest in shape to a target — Ether Body Damage, Fit, Hard to Kill, High Pain
Threshold, Alertness, Incorruptible Will — are pinned now. **A guard that does
not cover the false positive it was written for is not a guard**, and the only
thing that showed it was neutering the code it guards.

### What is reported rather than applied

Ten rows name a mechanic the book has its own name for and **none is carried by
a premade** (`Phobia (Cities)` on Hunter-Tracker is an instantiated spelling the
book never prints, 6am). Each gets its real reason:

- **Glass Jaw** — the knockdown refusal Iron Jaw already carries, sign flipped
  (no attribute is printed to roll against, 6bp).
- **Soul Scar** — names a Fright Check FAILURE as a trigger, not a roll to make.
- **Fearlessness, Faithful Grounding, Chronophobia, both Phobias** — a GAP
  stated as a gap. This app DOES roll a Fright Check, so "no such roll exists"
  would be the fresh excuse 6bo caught one commit from shipping; what is missing
  is the join to its modifier, and four of the five are conditional anyway.
- **Workaholic's own social clause** is refused as Loner's is (6bt): "social
  rolls" names no printed group.

**1034 Kotlin** (939 `core:rules`, 75 `app`, 20 `core:model`) and **261
Python**, green. RED-proved by neutering: the fourth anchor removed (verify
fails on Workaholic), the discriminator widened to accept a bonus (verify fails
on Ether Body Damage — after the guard was fixed), and the named-mechanic test
observed failing with seven rows before its entries existed. Codex reviewed the
diff and returned clean.

Device-verified on the physical tablet, **Factory Foreman** (Will 9) — all three
printed spellings of one mechanic on one character, which is the warrant on
screen:

- **"Bad Temper — -10 pts — 3d6 ≤ 12"** (a flat target, spelling A/B).
- **"Stubborn — -5 pts — 3d6 ≤ 6"** (attribute + offset via "roll vs.").
- **"Workaholic — -5 pts — 3d6 ≤ 6"** — the new anchor, landing on the same
  number as Stubborn because both are Will−3 on Will 9. Before this commit the
  row printed its cell and no roll at all.
- Its dialog reads *"keyed on Will-3, after any potion"* over **"3d6 ≤ 6"** with
  the stepper and Roll.
- Above them, the refused clause: *"Workaholic — -2 to social rolls in leisure
  settings / Not applied here: the book prints "social rolls" here and "social
  skill rolls" elsewhere, and distinguishes the two in one sentence …"*

**The next slice, named with evidence: the Fright Check modifiers.** Five rows
adjust a roll this app already makes — Fearlessness "+1 per level to Fright
Checks", Faithful Grounding "+3 … in church buildings", Chronophobia "-3",
both Phobias "-2" — and `FrightCheck.against(will, modifier, source)` has taken
a modifier since 6c. Only Fearlessness is unconditional; the rest need the
declaration control the conditional social rows (Callous, Shyness, Social
Anxiety) are also waiting on, so the two groups are one slice. No premade
carries any of them, so nothing is lost by its being next rather than now.

## 6bv. The placeholder the book tells a sheet to fill, and the roll it unlocks

`TraitEffects.placeholderRow` resolves an instance of a `(specify)` row, and
`frightClauses` / `frightBonus` apply the five rows that modify a Fright
Check. **6bu named the second as the next slice and 6br named a different one
before it; a census of what the 23 fixtures ACTUALLY carry disqualified one
and merged the other two into this.**

### The census, which changed the slice twice

- **6br's named successor is unbuildable and 6br did not check.** `Ether Body
  Damage` would be "the first disadvantage-driven modifier" — true, and
  irrelevant: **no premade carries it**, and a potion in this codebase grants
  attributes, SPI, secondaries and skill levels, never a trait (6al). A rule
  nothing can reach is the `blessings.json` refusal 6bc already made.
- **6bu's fright rows could not be reached alone.** The only premade carrying
  any of the five is Hunter-Tracker, through **"Phobia (Cities)"** — which
  resolved to no catalogue row at all, because the book prints
  `"Phobia (specify)"`. So the join had to ship first, and the two named
  deferrals were one slice with a hidden prerequisite.
- **Eight fixture traits across eight premades fill a placeholder in**: one
  Phobia, three Duties, three Vows, and Church Healer's
  `"Resistant (Disease +3)"`. Every one resolved to nothing, so every clause
  they carry was invisible.

**A deferral is a claim about the DATA, not only about the code** (6au, 6av,
and 6be retracting 6ba). An unreachable feature and a missing one look
identical in the source.

### The join turns on the book printing a word, never on the sheet printing a bracket

- **"(specify)" is an INSTRUCTION.** Duty's own cell says it out loud —
  "Specify organization and nature at creation" — and seven other cells print
  the word inside their prose. The same reading matched
  `"Hidden Lore (relevant domain)"` for a ritual's supporting skills (6am).
- **That is exactly what keeps 6am's refusals refused.** "Paranoia: Bodies of
  water", "Shyness (Mild)", "Social Stigma (Criminal)", "Sense of Duty
  (Patients)" hang off rows whose names print **no placeholder**, so the sheet
  added a qualifier the book never asked for and the only evidence is
  resemblance. 4.1: recorded, never guessed away. A test pins the five
  placeholder rows and seven still-unmatched spellings, so the boundary cannot
  widen by accident.
- **Resolving is not applying.** `Resistant (specify)` prints "+3 (3 pts) or
  +8 (5 pts)" scoped to "a specific category", and the one bare HT roll this
  app makes — a Major Wound's — resists neither disease nor poison nor
  temperature. It is reported beside Duty's frequency roll (the refusal 6am
  already made for that cell) rather than joining the map Fit and Unfit move.
- **`displayName` keeps the sheet's spelling.** Which fear a character has is
  content; "Phobia" alone would drop it.

### The fright join, and a disclaimer that expired on schedule

`FrightCheck.against` has taken a modifier since 6c, so the reason those five
rows carried was a GAP stated as one — "what is missing is the join". The join
shipped and the disclaimer went with it, which is 6ba's lesson applied
deliberately rather than after the fact (6bl found one of these that had
outlived its truth by a slice).

- **Four of the five name a circumstance and one does not**, and that is the
  whole of the interface. Fearlessness is what the character permanently IS,
  so it is counted unasked and scales with its level; being in a church or
  facing the feared thing is what the scene happens to be, and this app holds
  one sheet and no scene. The fifth rule running to become buildable as a
  DECLARATION (6bg, 6bl, 6bm, 6bn, 6bq), against a handover that ranked a
  round tracker first (6bd).
- **Fearlessness prints no ceiling**, unlike Charisma's and Outdoorsman's
  "[max 4 levels]" — a property of those two cells, so a general cap would
  invent one here (6bs's reading, held).
- **Sacred Symbols prints TWO circumstances and both are offered.** "-2 on
  direct exposure" is a relic in view; "-1 in consecrated buildings" is
  standing inside one; a cathedral holding a relic is both. The book prints no
  exclusion, and 6aa already read two consecutive sentences of one cell as
  adding. They sum to -3, and the key is trait AND clause so one declaration
  cannot move both — the collision `BoonModifier.key` was widened for (6r).
- **Phobia's "-1 to all rolls" half is reported**, because it is a declared
  all-rolls state and this app models exactly one (shock, through an enum
  tested per roll class, 6bo). A second is its own slice.
- **`known()` had to gain the fright names.** Three of the five are wholly
  applied and appear in neither report map, so a typo in one would make the
  modifier unreachable in silence — 6bs's finding about Outdoorsman, one map
  over, and the guard exists for exactly this.

### The design review ran first and came back no-ship, for the sixth slice running

Both findings were structural and neither is visible in a diff:

- **The dialog was the one roll-building boundary that discarded the
  character's traits.** `SheetScreen` holds them and passed only Will and
  shock, so the join would have shipped looking correct and reaching nothing —
  the sheet would report the clause while the roll ignored it, which is
  precisely the screen-disagrees-with-itself shape 6ai was fixed for.
- **Its Roll relied on tree removal alone**, where the divination dialog was
  hardened in 6p and every sibling guards. Adding chips increases the window
  in which two taps roll twice and keep the second.

Both accepted. The number and its breakdown now come from ONE call in rules,
because a screen that sums its own chips is the second copy of a decision —
the failure this file records five times (6ag, 6ai, 6be, 6bp, 6bs).

**1045 Kotlin** (950 `core:rules`, 75 `app`, 20 `core:model`) and 261 Python,
green. Every reading RED-proved by neutering it: the placeholder join removed
(2 failures), conditional clauses applied unasked (1), Fearlessness's level
ignored (1). **The boundary test correctly stays GREEN under all of them** —
it guards against widening, so it must pass before and after, which is worth
saying rather than reading as a gap. Restored from a `cp` backup each time,
never `git checkout`, which is what cost 6br its implementation. The
prohibition sweep was re-run over both catalogues (6ba) and forbids nothing
about either half. Codex reviewed both commits and returned clean.

Device-verified on the physical tablet (1920x1200), both halves and both
boundaries:

- **Hunter-Tracker** — the only carrier. The sheet prints **"Phobia (Cities) —
  −10 pts"** with the book's effect cell underneath and **"Phobia (Cities) —
  -1 to all rolls…"** with its reason; before commit A that trait resolved to
  no row and rendered neither. Its Fright dialog, on the **+0** trigger, shows
  **"Does any of this hold? — Phobia (Cities): Fright Check at -2 when
  exposed"** at **3d6 vs 9**; ticking it reads **3d6 vs 7** with *"includes
  Phobia (Cities) -2"*. Rolling gave **"3d6: 11 vs 7 — failed by 4"** — an 11
  would have PASSED against 9, so the trait is what failed it, and the number
  reached the dice rather than only the caption. **Two rapid taps produced one
  outcome.**
- **Church Healer**, the other half on one screen: **"Resistant (Disease +3)"**
  now prints its cell and its refusal, **"Vow (Poverty)"** prints the book's
  own, and **"Sense of Duty (Flock) - Must help/protect certain groups even at
  personal risk"** still renders GLUED with no effect line — the boundary
  holding on the same character. Its Damage dialog at 6 reads **"Roll HT 9"**
  with no caption and no bonus: the category-scoped +3 does not leak onto the
  one bare HT roll, which is the reading the design review blocked.

**The next slice, named with evidence: the conditional social rows.** Callous
(-3 "when warmth or empathy is required"), Shyness (-1 to -2 "depending on
severity and audience") and Social Anxiety are the three rows whose GROUP is
identifiable since 6bt and whose CIRCUMSTANCE is not, and four of the five
carriers own a social skill — so applying them flat would charge a penalty
nobody declared. The declaration control now exists in one shape
(`FrightClause`, conditional + key + a chip row), so what is left is deciding
whether a skill roll's LEDGER can carry one, which the fright dialog sidesteps
by taking a single Int. That is a real design question and wants its own
review.

## 6bw. A variant the book prices itself, and the circumstance that charges it

`TraitEffects.variantRow` resolves a sheet spelling the book's own cell
enumerates, and `socialClauses` / `modifiers(..., declared)` charge the three
rows that penalise a social skill roll only when a printed circumstance holds.
6bv named the second as its next slice; a census made it two commits again,
and this time the prerequisite **overturns one of 6bv's own refusals**.

### The census, for the second slice running

- Callous is on **Barmaid Brawler and Docks Bouncer, both beside
  Intimidation**; Shyness on **Academic Researcher, beside Public Speaking**;
  Social Anxiety on nobody. So a flat penalty would charge three of the four
  carriers for a circumstance nobody declared.
- **Shyness was unreachable.** Academic Researcher's sheet spells it
  `"Shyness (Mild)"` and the book prints a bare `"Shyness"` — one of the nine
  spellings 6bv had just pinned as permanently unmatched.

### Correcting a stated reason is not repairing a disagreement

6bv refused those nine because *"the sheet added a qualifier the book never
asked for, so the only evidence is resemblance."* **That sentence is false for
exactly two of them**, and checking a sweep's own excuse is 6bf's lesson:

| Row | What the cell prints | What the sheet bought |
|---|---|---|
| Shyness | "Mild (**-5**): -1 in groups of 6+; Severe (**-10**): -2 in groups of 3+" | "Shyness (Mild)", **5 pts** |
| Social Stigma | "Criminal Record **-5**, Servant Class -5" | "Social Stigma (Criminal)", **5 pts** |

The qualifier is named by the book AND the price agrees — two independent
agreements, which is the standard 6ad set and which resemblance can never
meet. The other nine hang off cells that enumerate nothing, and a test pins
them still unmatched. **"Absent-Minded" against "Absent-Mindedness" is still
refused**, because there a name is similar and nothing else agrees at all.

- **An enumeration has at least two members, and that is the whole filter.**
  Allow one and the same pattern reads Stubborn's "Must roll vs. Will-3" as a
  variant called "Will" priced at 3 — eleven rows print a number in prose that
  way, four of them self-control targets. A cell offering a CHOICE prints more
  than one option, and that single condition takes 198 rows down to two.
- **The qualifier must select exactly one option** (Codex, on the design). The
  sheet writes "Criminal" where the book writes "Criminal Record", so it is a
  prefix that names one option, never a substring search over the cell.
- **The cost is not read at the join.** `catalogueName` takes a name, and
  threading a cost through it would give one decision a second matcher — the
  failure this file records six times. It is the WARRANT, proven across all 23
  fixtures by a test, so the day a sheet pays a price the cell does not print
  for its variant that test fails rather than this resolving on the name alone.

### The declaration, and where it goes instead

- **The group was identifiable since 6bt; only the circumstance was missing.**
  Whether warmth is required, how many people are in the room, whether the
  listener outranks you — none is computable from one sheet. So they are
  declarations, exactly as the four conditional Fright Check rows are, and the
  sixth rule running to reach a roll that way rather than through the round
  tracker 6bd ranked first.
- **`FrightClause` became `TraitClause`, one type for both.** A fright row and
  a social row are the same thing said twice — a printed clause, a value, a
  circumstance — and only the DESTINATION differs: a Fright Check takes a
  single Int, a skill roll takes a `Modifier` through the ledger. Two data
  classes carrying identical fields would be the duplicate this file guards
  against everywhere else.
- **`modifiers` gained `declared: Set<String> = emptySet()`**, one production
  call site, and emits `FromTrait` — never `Situational`. A `Cancel` budget is
  measured by `SourceKind`, and a drawback the character permanently has is not
  something a GM said about this turn (6al's reading, pinned again).
- **Shyness's value comes from the SHEET, not the GM.** Its cell prices two
  severities and a character bought one; only the variant join makes that
  legible. The circumstance stays the GM's; which clause is on offer is not a
  choice.
- **Social Anxiety's two circumstances sum**, with distinct `ModifierId`s —
  two modifiers sharing one id is a collision the resolver should never be
  handed, the same reason the declaration key is trait plus clause (6r). It
  ships although no premade carries it: the list is every conditional social
  clause the book prints, not a premade census.

### Codex found the defect an empty string makes

A bare `Trait("Shyness", …)` has an empty qualifier, and **`"".startsWith(x)`
is true for everything** — so it offered Mild AND Severe at once and would let
a GM declare both, on a value that is not a choice. Refused now, and pinned:
a row keyed to a variant needs one NAMED. The refusal-over-a-plausible-answer
call `Detection.attributeFor` makes (6ah).

Its design review, run on the decomposition before any code (**seventh slice
running**), returned ship-conditional and named the "selects exactly one"
wording, the guard tests to rewrite rather than delete, and the `FromTrait`
requirement. Worth recording that `review --wait` **refused the prose outright**
— it injects a branch diff and found none — so a decomposition review has to
go through `task`, which section 7 already lists for exactly this.

**1055 Kotlin** (960 `core:rules`, 75 `app`, 20 `core:model`) and 261 Python,
green. RED-proved by neutering each reading: one-member enumerations allowed
(2 failures), the declaration gate removed and the category scope widened
together (2). Restored from a `cp` backup each time.

Device-verified on the physical tablet (1920x1200):

- **Barmaid Brawler**: the sheet prints Callous with its cell and **no "Not
  applied here" line** — the disclaimer expired with the control that replaced
  it (6ba). Intimidation opens with **"Does any of this hold? — Callous: -3 to
  social skill rolls when warmth or empathy is required"**; declaring it rolled
  **"3d6: 4 vs 8 — base 11, modified to 8"** with `trait:callous: -3` named in
  the breakdown. **Brawling on the same character offers no such row at all**,
  which is the category scope on screen.
- **Academic Researcher**: Public Speaking offers **"Shyness (Mild): Mild (-5):
  -1 in groups of 6+"** and nothing else — Severe absent, because the sheet
  bought Mild. That chip is reachable only through the variant join in the
  first commit, so the two really are one slice.

**The next slice, named with evidence: `Ether Body Damage`, and it needs the
builder rather than the rules.** It prints "All SPI-based rolls at -1", which
is a controlling-attribute join `modifiers` already makes for Alertness and
Fit — but 6bs deferred it and 6bv disqualified it, because **no premade carries
it and no potion can grant a trait** (6al). It becomes reachable the day traits
are purchasable, which is also what unblocks the Seven Orthodox Gods Blessings
(6bc) and the 198-row catalogue generally. That is a real feature with a design
question inside it — a blessing costs 75 points AND grants attributes, and M3's
rule is that granted attributes are never written into the billable fields —
so it wants its own review, not a content pass.

## 6bx. The economic status nine sheets spell their own way

`Wealth.levelOf` reads the level a sheet names under either printed head word,
and `Wealth.namesEconomicStatus` decides what the picker must replace. 6bw
named `Ether Body Damage` as the next slice; a census of what the 23 fixtures
ACTUALLY carry found something bigger on the way past, and the deferral stands
untouched.

**Nine of the 23 premades print `Poverty: Struggling`.** The book prints
`Wealth: Struggling`. `levelOf` matched `startsWith("Wealth: ")`, so nine
characters' economic status was invisible to the builder's wealth row, which
read "the sheet prints no wealth level" for a sheet that prints one. 6u shipped
that row and device-verified it on Academic Researcher — which prints
`Wealth: Poor`, and therefore worked.

**The display was the smaller half.** `applied()` filtered on the SAME prefix,
so choosing any level ADDED it and LEFT the sheet's poverty row. That is
verbatim what its own KDoc says it prevents: *"a sheet's printed 'Wealth: Poor'
left beside a bought 'Wealth: Wealthy' would raise the budget by 15 and spend
15 in the same breath, and the totals would still look plausible."* Picking
Dead Broke on such a character gave 10 + 25 = 35 points of poverty refunds
under a 40-point cap — plausible, and wrong. Printed-and-CONTRADICTED (6aq,
6ar), with the app contradicting its own written rule rather than the book's.

### The finding was already in this repo's test suite

`ContentLoaderTest` has carried this since M0:

```kotlin
"Poverty: Struggling" to "Wealth: Struggling",
```

So a test has known these are one trait for as long as the fixtures have been
wired up, and reconciles their COSTS through it — while production matched on a
prefix that could not see it. **The test knew and the code did not**, which is
what 6as said about `DetectionTest`'s scope: the cheapest kind of finding to
have missed.

- **The alias is a finding, not a warrant.** The same map pairs
  `"Absent-Minded"` with `"Absent-Mindedness"` — a pairing production
  deliberately REFUSES and pins as unmatched (6am). So it is a test-side
  cost-reconciliation aid, not a 4.1 ruling, and adopting it wholesale would
  have imported the refusal along with the resolution.
- **The evidence therefore came from the book.** "Poverty" as an economic
  status appears NOWHERE: its only two occurrences in the whole extracted text
  are line 1370, inside `Vow (specify)`'s example list where it is priced as a
  promise, and line 9762, setting prose about Loen. "Struggling" appears three
  times and every one is a wealth level — ch. 3's trait list, ch. 8's table at
  **-10 pts**, and the Feysac currency restatement.
- **Two independent agreements, which is what 6bw's standard requires.** The
  qualifier names exactly one thing, and nine sheets pay the printed 10. A
  resemblance offers one; 6am's "Absent-Minded" is why one is not enough.

### The design review caught a regression the decomposition would have shipped

Eighth slice running to be reviewed as prose before any code, through `task`
because `review --wait` refuses a diff-free decomposition (6bw). It came back
**ship-conditional on four findings**, and the first was mine to have missed:

**Fortune Teller prints `Wealth: Comfortable` at 10 where the book prints 5.**
My proposed rule required price agreement of every colon trait, which would
have stopped resolving a canonical row that has always resolved — a cost
conflict already recorded in `ContentLoaderTest`, and still an unambiguous
statement of which level the character is. So the two doors are asymmetric on
purpose: **the book's own head resolves on the status alone; any other head has
to earn it with the price.**

- **Deriving a level and being replaced are different questions** (its fifth
  finding, and the one that shaped the API). University Librarian pays **5**
  for the same words. Its disadvantage column is already a recorded arithmetic
  error (`disadvantagesExclusions`), so the only evidence left is resemblance
  and the level stays unresolved — while the row plainly NAMES a status and
  must still be replaced, or the character holds two. `levelOf` claims only
  what it can prove; `applied` removes whatever names a status.
- **That split makes the whole reading checkable rather than argued.** Nine
  sheets resolve and the tenth does not, on the identical spelling, decided by
  the price — the property 6bw was praised for, and the reason no exception
  list is needed.
- **The kind is checked as well as the magnitude.** `Trait` carries a magnitude
  and puts the direction in its kind, so a row of the wrong sign could resolve
  to a level that happens to cost the same the other way.

**Codex then found the over-match, and 6l is why it was closed rather than
noted.** A head-free rule reads `Trait("Reputation: Wealthy", 15, ADVANTAGE)`
as an economic status the moment its qualifier and price line up — and
`applied` would then DELETE it. No fixture can reach that, which is exactly the
call 6l made about a permissive damage-type lookup: *"nothing could reach the
permissive path yet; removing it is what stops the next caller finding it."*
The heads are named now, which also makes an eleventh sheet inventing a third
word for the same idea **fail a test instead of resolving in silence**.

**A count in my own test was the one thing that failed.** The general guard
asserted exactly one wealth row survives every pick; Average is 0 pts and
correctly leaves none (6u). The invariant is "never two", not "always one" —
the count-in-prose habit (6as) turned on an assertion rather than a number.

**1063 Kotlin** (968 `core:rules`, 75 `app`, 20 `core:model`) and 261 Python,
green. Five readings RED-proved by neutering each in turn and restoring from a
`cp` backup rather than `git checkout` (6br): the canonical door removed (1
failure — exactly the Fortune Teller regression the review named), price and
kind together (3), the kind alone (1), `applied` back to the prefix filter (2),
and the head allow-list widened to admit "Reputation: " (1). The prohibition
sweep was re-run over ch. 3's trait list and ch. 8's wealth table (6ba) and
forbids nothing about naming or replacing a status.

Device-verified on `Medium_Phone_API_36.1` (1080x2400), both branches:

- **Barmaid Brawler** — the sheet still prints **"Poverty: Struggling — −10
  pts"**, because a published row shows the sheet's own spelling (6e), while
  the builder's Wealth row now reads **"Struggling — −10 pts, 15 soli to
  start"** where it used to read the "no wealth level" line the Appearance row
  below it still shows. The picker's first entry reads **"As printed ✓ — The
  sheet prints Struggling"**, a sentence the old code could not produce.
- Choosing **Wealthy** takes the budget from **82 of 95** to **97 of 85 — over
  budget**. The 85 is the finding: the poverty row's 10-point refund is GONE.
  Under the old code the same tap reads **97 of 95**, keeping a poverty
  drawback on a character who just became wealthy.
- **University Librarian** — the discriminator. Its row reads **"The sheet
  prints no wealth level"**, the disagreement staying visible, and choosing
  **Poor** gives **91 of 105** — 95 − 5 + 15, so the unresolved row was removed
  anyway. Had it survived it would read 110.

**The next slice is unchanged and still 6bw's: `Ether Body Damage`, which needs
purchasable traits rather than rules work** — the same unlock as the Seven
Orthodox Gods Blessings (6bc) and the 198-row catalogue generally. Worth adding
from this slice: the fixtures carry **ten unresolved trait spellings** and this
one turned out to hide a live defect, so the other nine are worth a pass each
before they are inherited as recorded disagreements. `Blacklisted: Specific
Industry org or church` is the same colon shape as the row fixed here and
paraphrases its own catalogue notes rather than quoting them, so 6br's
exact-tail rule refuses it — correctly, on today's evidence, and that is a
verdict rather than a proof.

## 6ca. The trait nothing could reach, and the blocker three slices inherited

`TraitEffects` applies Ether Body Damage's "All SPI-based rolls at -1", and
`PurchasableTraits` lets a player buy a trait at all. 6bs, 6bv and 6bw each
named this as the next slice and each deferred it for the same stated reason.

**That reason is false, and checking it took one script.** The deferral said
purchasable traits collide with M3's rule that a granted attribute is never
written into a billable field, *because* a Seven Orthodox Gods Blessing costs
75 points AND grants "+3 ST, +3 DX, +3 HT". But 6bc read that table and
deliberately did not extract it — so the blessings are not in the catalogue —
and a regex over all 198 rows finds **not one that grants an attribute or a
secondary**. The collision belongs to a table that stays deferred, not to this
feature. **A deferral is a claim about the DATA, and it expires like any other
verdict** (6au overturned one, 6av confirmed one, 6bv found one unbuildable).

### The join, and why the bare rolls are evidence rather than assumption

The trait is the third controlling-attribute join, beside Alertness on Per and
Fit/Unfit on HT — and the first to use `SourceKind.DISADVANTAGE`, pinned now
because nothing can reach the difference yet (6bs's call, on the first row
that needs it).

- **"All SPI-based rolls" is not "all SPI-controlled SKILL rolls."** Two rolls
  reach a target outside `SkillKey`: a detection roll takes a bare attribute,
  and a divination builds its target through `Divination.targetFor`. That is
  the gap Alertness fell into (6al) and Fit fell into after it (6bs), so both
  are threaded explicitly and `MainActivity` decides the number ONCE.
- **The book draws that one-word distinction on this attribute.** The
  prohibition sweep turned up line 2351: Spiritual Attrition is "-3 to all
  SPI-based **skill** rolls" where this cell omits the word — exactly the
  difference the book settles for "social rolls" in a bracket of its own
  (6bt). So the wider phrase really is wider. Pinned, because the threading
  rests on it.
- **It cannot decide WHICH attribute a detection roll takes.** Choosing on the
  modified values would let a -1 tip a tie into a Per roll and so cancel
  itself. Choose on the raw values, then apply — the reading Alertness already
  needed, and SPI 11 against Per 11 is the case that tells them apart.
- Its sibling clause, "Spirit Vision on you reveals the damage immediately",
  is reported: that is what ANOTHER character's Spirit Vision sees, and this
  app holds one sheet (6ah's refusal, and Feint's before it).
- **The clause-coverage guard was blind to the new map**, exactly as it was
  when `SKILL_BONUSES` was added (6bs). It reads every applied map now rather
  than a list of the ones that existed when it was written.

### The design review set seven conditions before any code

- **Economic status stays ONE choice, enforced below the UI.** A general
  picker offering "Wealth: Poor" would let a character hold two statuses or
  fight the wealth picker for the same points — the live double count 6bx had
  just fixed. The exclusion is in `PurchasableTraits`, so a hand-edited save
  cannot get round it either.
- **A duplicate is refused rather than allowed twice.** `PointBudget` bills
  every row it is handed while `TraitEffects` reads one canonical match, so a
  second Fit costs 5 points and lifts no roll. Both the picker and the
  composition ask the same predicate, and it resolves through
  `catalogueName` — so a premade's glued "Clueless" counts as holding it.
- **Only rows printing a single cost are offered, and the rest are NAMED.** A
  cost range is settled by the instance (6by's gap) and a "(specify)" row
  needs a description this app cannot store. Silently omitting nineteen rows a
  player can read in the book is the quiet gap this project records instead
  (6af, 6ai). A test asserts every printed row is offered or explained.
- **A save stores the NAME**, so cost and kind are re-read every load — the
  rule a bought skill's difficulty already follows. A name the book no longer
  prints is skipped and NOT deleted, because the book may print it again
  (6as); that is the opposite call to the three SPI skills, barred permanently.
- **Removal ships with the picker**, because a choice needs a way back (6u's
  finding). Only a bought trait can go: removing one the sheet printed needs a
  suppression the save has no shape for, and the sheet is read-only by design.
- Advantages and drawbacks are two headed sections, since they move the budget
  in opposite directions.

**Codex found one P3 on the diff** — the picker took `BuilderScreen.kt` past
the documented 800-line limit — and it was taken rather than argued: both
composables live in `TraitPicker.kt` now.

**1084 Kotlin** (985 `core:rules`, 79 `app`, 20 `core:model`) and 261 Python,
green. Four readings RED-proved by neutering each and restoring from a `cp`
backup (6br): the wealth exclusion (2 failures across both modules), the
duplicate guard (1), the name resolution (2), and the unoffered explanation
(2). The prohibition sweep over ch. 3's trait lists finds nothing forbidding a
purchase — its 14 hits are all effect prose inside the cells themselves.

Device-verified on `Medium_Phone_API_36.1` (1080x2400), Academic Researcher:

- The builder's new **Traits** row reads *"None bought in play — the sheet's
  own are on the sheet screen."*; the picker opens on **Advantages / "Cost
  points."** and reaches **Drawbacks / "Refund points, up to 40."**
- **Ether Body Damage — −10 pts** with the book's cell; buying it saves
  `"traits":["Ether Body Damage"]`, adds a row reading **−10 pts** with **Give
  back**, and takes the budget from **91 of 100** to **91 of 110**.
- Seeded as a **Fool Seq 9** (SPI 9, Per 10), the sheet prints the drawback AND
  reports its unapplied sibling clause. **Detect** reads *"Roll 3d6 ≤ 10 — Per
  10 (the higher of SPI 9 and Per 10)"* with the trait ABSENT, because a Per
  roll is not an SPI-based roll — and selecting the Fool row, which grants a
  Per roll, keeps it absent.
- **Divine** reads **"Roll 3d6 ≤ 13 — Skill 12, question +2, −1 Ether Body
  Damage."** — the penalty reaching a target built outside `SkillKey`, named
  in a breakdown that sums.

**Next, with evidence:** the nineteen rows this picker withholds. The five
`(specify)` rows need somewhere to write the instance down, which is a save
shape rather than a rules question; the fourteen ranged rows need the reading
6by refused, and the narrower warrant it recorded (a sheet quoting a cell's own
notes verbatim) is where that should start.

## 6bz. What does the test suite know that the code does not?

`ContentLoaderTest`'s fixture audit resolves a trait name through
`TraitEffects.catalogueName` before trying anything of its own, and the names
it reaches beyond production are pinned with a reason each. This is the sweep
6bx and 6by both found their defect through, run on purpose.

**Both of the last two slices stumbled into the same class by accident.** 6bx's
live double count was sitting behind a hardcoded alias this test had carried
since M0; 6by's seven-trait gap was a second reading this test had implemented
and production had not. Two accidents make a class, and naming a sweep obliges
running it (6ay) — so it was run over every domain constant in the test tree:
the golden exclusion sets, the untabulated ritual rows, the absent-skill and
absent-trait lists, the alias map, the name-artifact list.

### The one finding: an exclusion that outlived its own reason

`KNOWN_FIXTURE_NAME_ARTIFACTS` skipped two premades' `Clueless <the book's
whole effect cell>` rows, saying: *"no general normalization rule can recover
the true name."* **6bt wrote that rule** — the sheets glue the name to the
book's own notes with a bare space, and a tail reproducing the catalogue's
words exactly is what proves the head. So the reason had been false for several
slices while the skip stayed, and those two rows' costs were never reconciled
against the book in all that time. They agree (10 against 10), so nothing was
wrong; nothing was CHECKED either, which is the finding.

**6ba's lesson, pointed at a test's own exclusion.** A verdict expires — and an
exclusion is the one construct that cannot notice when it stops being needed,
because it removes the very case that would tell it. `reported()`'s stale
disclaimer (6bl) is the same shape in production.

### Why the two resolvers are ALLOWED to differ, which nothing had said

The tempting fix was to make production and the audit share one matcher. That
is wrong, and it is observation-10's shape: **two questions that share a
matcher still need two matchers.** This audit reconciles the fixture corpus
against the book, so it must REACH a near-match in order to report a cost
conflict on it — `Absent-Minded` is a conflict only if you first admit it is
that row. Production decides what a player is shown, and must refuse the same
name (4.1). Collapsing them loses the conflict report.

**So the relationship is not equality, it is a direction:** the audit must be a
strict superset. Knowing more is its job; knowing less is a bug, and it had
one. Delegating to production FIRST is what makes the bug impossible rather
than merely unlikely — a spelling production learns to read stops being skipped
here on the same commit, instead of some later one.

- **Thirteen names only the audit reaches**, each with its reason, and the
  reverse direction asserted EMPTY. That empty assertion is the guard: it is
  what fires the day production out-reaches the audit again.
- **RED-proved in the strongest available way.** Removing the delegation fails
  BOTH tests, and the failure names the two `Clueless` rows as not-found —
  which is the defect itself, printed by the test that now prevents it.
  Dropping one entry from the pinned set fails too.
- Nothing else in the sweep was actionable: the golden sheets' HP/FP and
  subtotal exclusions are the book-versus-sheet disagreements 4.1 exists for
  and are visible on the sheet screen's own diff; `GoldenRitualTest`'s
  `untabulated` rows come from no printed table, so there is nothing for
  production to read; the absent-skill and absent-trait lists were re-verified
  against the extracted text and are still absent.

**1066 Kotlin** (971 `core:rules`, 75 `app`, 20 `core:model`) and 261 Python,
green. Test-only: nothing to device-verify and no behaviour changed, stated
rather than skipped. Codex reviewed the diff and returned clean, and its own
Gradle run failed on a sandbox lock again, so the green suite is mine.

**The next slice is still 6bw's `Ether Body Damage`, which needs purchasable
traits** — and note that this sweep is now a standing check rather than a
one-off: the empty-set assertion runs on every build.

## 6by. Sixteen spellings, three shapes, and a fix worth refusing

`TraitEffectsTest` names every sheet spelling this app cannot resolve, with a
reason each. 6bx closed by asking for exactly this — *"nine unresolved trait
spellings remain, and this one hid a live defect, so they are worth a pass each
before they are inherited as recorded disagreements"* — and the census corrects
that sentence twice before it does anything else.

**There were sixteen, not nine.** The count-in-prose habit (6as) turning on its
author for the third time in three slices. Seven were a group I had not seen at
all: five `Sense of Duty (…)` instances and two `Secret (…)`.

**And there was no census to be wrong against.** The only pin on unresolved
spellings filtered `it.length >= 40`, so the long glued names were watched and
every short one — including all sixteen minus two — was watched by nothing. A
list of exclusions with no test behind it is a comment.

### The three shapes, which is the whole point of writing reasons down

A flat list of "unmatched" hides the distinction that decides what to do:

| Shape | Members | What it means |
|---|---|---|
| **Not printed** | No Sense of Humour, both Pacifisms | the book prints no such row under any spelling — verified by text search, not assumed |
| **Price disagrees** | Absent-Minded (5 vs 15), Paranoia: Bodies of water (5 vs 10) | resembles a row and nothing else agrees. 4.1's own case, recorded forever |
| **Paraphrase** | Blacklisted: …, the glued Sense of Duty (Flock) | an instantiation that paraphrases a cell rather than quoting it, which 6br's exact-tail rule refuses |
| **Deliberate scope** | Poverty: Struggling | resolved in `Wealth`, by 6bx's design review — that resolution reads cost and kind, and this API takes a name |
| **GAP** | 5 × Sense of Duty, 2 × Secret | the data plainly marks something and this app cannot read it |

**A gap is work; a refusal is a decision. Calling the first the second is how
this file shipped four milestones of unreachable rules** (6h, 6m, 6ai, 6ap) —
and 6bu already had to state one reason as a gap rather than let it read as a
refusal. The map is the assertion, so a spelling appearing without an entry
FAILS rather than joining the list in silence. RED-proved by deleting one
entry.

### `Wierdness Magnet` is the sharpest refusal, because everything agrees

Journalist prints it at **15**; the book prints "Weirdness Magnet" exactly once,
at **15**. Price agrees, the row is unique, nothing competes — the two
agreements 6bx and 6bw both took as sufficient. It is still refused, because
what differs is not a head word or a qualifier but the SPELLING, and closing
that needs typo tolerance, which is the guessing 4.1 forbids. Checked rather
than assumed that nothing is lost: the row prints no number, no roll and no
mechanical clause, so refusing costs one effect line and no rule. **A standard
that only ever admits is not a standard.**

### The gap's obvious fix was refused, and the refusal is the more useful half

The book asks for an instance two ways: explicitly, by putting `(specify)` in
the row's NAME — which this app reads — and implicitly, by printing a cost
RANGE the instance settles: `Sense of Duty` 2-15, `Secret` 5-30. Teaching the
resolver the second form resolves all seven, and **`ContentLoaderTest`'s own
resolver has read both since M0** (`costMin != null || variableCost`). The same
test-knows-and-code-does-not divergence 6bx found, surfacing again in the same
session, which is what made it look obvious.

It is refused, and a design review's High is why — verified here rather than
taken on trust (section 7: evidence, not orders):

- **Nineteen rows print a range, and two of them are `Shyness` and
  `Social Stigma`** — the exact pair 6bw resolved through a separately argued
  warrant, because those cells ENUMERATE their variants with prices. If a range
  by itself marked a template, that warrant was never needed and 6bw's
  two-member enumeration filter was pointless. A reading that retroactively
  deletes the previous slice's evidence is wrong.
- `Reputation` is variable-cost, and 6bx's review used **"Reputation: Wealthy"**
  as its own over-match example. The two would have collided.
- A range is **variable pricing**; a placeholder is **name substitution**. The
  book states the link only for the rows that print `(specify)` — `Duty`'s cell
  says "Value reflects frequency and danger" in as many words. Inferring it for
  rows that do not say so is inference, not reading.

**What real evidence exists is narrower and belongs to one row**, recorded so
the next reader inherits it rather than the conclusion: Church Healer prints
`"Sense of Duty (Flock) - "` followed by that row's own notes **verbatim**,
which is the sheet asserting the instantiated name IS that row — the standard
6br set for glued spellings. A slice built on that rather than on the range
would resolve the five Sense of Duty instances and leave `Secret` alone,
because no sheet quotes Secret's cell. That is the shape of the next attempt,
not a to-do.

**1065 Kotlin** (970 `core:rules`, 75 `app`, 20 `core:model`) and 261 Python,
green. Test-only, so there is nothing to device-verify and no behaviour changed
— stated rather than skipped. Codex reviewed the diff and returned clean; note
that its own Gradle run was blocked by a sandbox lock on the wrapper, so the
green suite is mine.

**The next slice is still 6bw's `Ether Body Damage`, which needs purchasable
traits.** Behind it, in order of evidence rather than size: the Sense of Duty
resolution above, and then `Secret`, which has none.

## 6ci. Nine priced rows, seven charged

Both slices before this one closed by naming the same next step — "6ca's
`Ether Body Damage`, which wants purchasable traits, is the largest thing
outstanding". **Both were false when written.** 6cc shipped purchasable
traits three slices earlier and device-verified an `Ether Body Damage`
purchase surviving a reload; `TraitEffects.spiRollBonus` has production
callers at `MainActivity.kt:366`, `DivinationDialog.kt:455` and
`Detection.kt:160`. The second item behind it — "the Sense of Duty
resolution" — is `TraitEffects.QUOTED_INSTANCE_ROWS`, also already shipped.

Two closing paragraphs copied forward instead of re-derived, and the queue
they described had been empty for four slices. **A handover's own "what is
left" is a claim about the tree and expires exactly like a sweep verdict**
(6au, 6av, 6ba, 6bv). Two greps settled both.

### The sweep this repo had never run

Every audit here has iterated one of two units. 6ai swept extracted FIELDS
for a reader. 6cf swept public FUNCTIONS for a caller. Neither can see a
table that is loaded, whose fields are all read, and one of whose **rows**
reaches nothing — a row is not a field and not a function.

So: every content JSON, every row's identifier, grepped against all Kotlin.
The raw output is mostly noise, and **the noise is the instrument working
correctly**: `appearance.json` names 2 rows of 8 in source and `skills.json`
52 of 158, because those tables are consumed by loop, which is the right
design. The signal is the SIBLING RATIO inside one table — where the code
models rows individually and skipped some:

| Table | Modelled | Named nowhere |
|---|---|---|
| `maneuvers.json` | 13 of 14 | **All-Out Defense (Double)** |
| `extra_effort.json` | 3 of 4 | **Giant Step** |

Neither has code, a test, a comment, or a recorded reason. This repo's own
standard (`TraitEffects.UNAPPLIED`) is that a printed rule reaching nothing
says so with its reason, "so the omission stays a decision rather than
becoming an accident".

### The design review was right to refuse, and wrong about why

Reviewed as a decomposition, no diff. Verdict **NO-SHIP**, and its first
finding was a correction to me: my hazard argument named
`ActiveDefenses.boostFor`, **which does not exist** — line 191 is
`const val BOOST_BONUS` and I had read its KDoc as a function's.
`boostOn` is real but lives in `DefenseDialog.kt:421`, outside the rules.
A design document is an argument made of claims about code, and one grep
per symbol is what those claims cost.

Its substantive counter-case: `(Increased)` already ships with its own
"Cannot attack" clause unenforced, so Double's unmodelled middle clause is
not categorically different. That argument is inconclusive from inside
itself — either side could be argued from the same evidence. **A census
settled it in one command.** Take the attribute both rows share, and count
how the corpus treats it:

```
YES  maneuvers     2 FP  All-Out Attack (Determined) / (Strong) / (Double)
YES  maneuvers     2 FP  All-Out Defense (Increased)
NO   maneuvers     2 FP  All-Out Defense (Double)
NO   extra_effort  1 FP  Giant Step
YES  extra_effort  1 FP  Strong Blow / 2 FP Heroic Charge / 1 FP Feverish
```

Nine priced rows, seven charged. **The price is the half this app does
model** — it holds no map, no opponent and no round, but it holds the FP
pool. So neither row is out of scope: they are *unpayable*, which is a gap.
That inverts my own proposal, which was to record both as unapplied with a
reason. Recording a reason for something the app can in fact do is how a
disclaimer starts lying (6bl).

### What shipped, and what is pinned instead

`Attacks.GIANT_STEP`, `AttackPlan.giantStep`, one addend in `fpCost`, one
chip. **No `require` guards it**, and that absence is pinned by a test:
its two neighbours in the same bullet list each print a scope ("Regular
melee attacks only", "Ignore **Charge Attack** penalties") and this cell
prints none, so refusing it anywhere would be this app inventing a limit
(4.1). "No require" is invisible in a diff, so it is a test.

The census is now the guard, anchored to `AttackManeuver.printed`,
`DefenseBoost.printed` and the option constants — **never to a text search
of the source**, which the reviewer showed would call `Move` and `Wait`
covered because this file's own prose contains those words (6cg's
sign-blind guard, one class over). It asserts the remaining gap by NAME:
All-Out Defense (Double). That line fails the day it ships, which makes it
a pin rather than a comment (4.2).

### The diff review found the transition I did not know existed

**SHIP with one MEDIUM.** `atRange` and `withAttack` centralise what
survives a plan transition, and both KDocs say why: a caller doing its own
is a second copy of the rule, which stopped this codebase's numbers
reconciling three times (6ag, 6ai, 6be). There was a **third** transition —
the maneuver chip rebuilt `AttackPlan` inline — and it went unnoticed
because everything it dropped deserved dropping. Giant Step is what exposed
it: no maneuver scopes it, and the rebuild dropped it anyway, so a player
comparing to-hit numbers across maneuvers silently lost a paid option.
`Attacks.withManeuver` now owns it, RED-proved.

One LOW taken: `.filter { it.fp != null }` on the extra-effort side is
vacuous — `ExtraEffort.fp` is a non-nullable `Int`, so it read as the
maneuver side's condition while testing nothing.

**One LOW refused, with evidence.** The reviewer notes the census cannot
see an `fp` VALUE drifting on an already-payable row. There is no second
copy to drift: `fpCost` reads `maneuver.fp` and `effortCost(...)` reads
`row.fp`, both from the JSON at call time. Every FP number this app charges
already comes from the row it is printed in.

### Verified

**1121 Kotlin** (1015 `core:rules`, 86 `app`, 20 `core:model`) and **267
Python**, all green. Three readings RED-proved by neutering one at a time
and restoring from a copy (6bu): dropping `GIANT_STEP` from the payable set
fails the census naming the row; dropping `+ step` from `fpCost` fails both
cost tests; dropping `giantStep` from `withManeuver` fails the transition
test. Not device-verified — the chip is one `FilterChip` beside two written
the same way, and this repo tests no composables.

**Codex still refuses** (resets 2026-08-20 14:23; today is 2026-08-15). Two
`ecc:kotlin-reviewer` passes, decomposition then diff, each scoped to three
questions and the files needed to answer them.

**Next, with evidence.** `All-Out Defense (Double)`: 2 FP, printed,
unpayable, and now named by a test that fails the day it ships. It is
**not** a third `DefenseBoost` — that type's KDoc makes the +2 the point and
`BOOST_BONUS` an inseparable feature of it — so it needs a declaration
shaped like `DefenseRefusal`: a cause and a printed sentence, no number.
Behind it, the row sweep's other single-miss tables are untriaged:
`hit_locations.json` ('Leg'), `divination_support.json` ('Cogitation'),
`ritual_support.json` ('Professional Skill (Astrology etc)'). Each is one
grep to classify, and the ratio says only these are worth the look.

## 6ch. The literal nobody argued for

6cf's API sweep left two survivors. `EffortBurn.scopeOf` was the smaller, and
chasing it found the bigger one two lines away.

`AttackDialog` called `BurnRow(kind, burned[index] ?: 0, spiAvailable = 0)`.
The `0` is a literal, and **no slice in this 7400-line log argues for it** —
it was written the day `BurnRow` gained the parameter and never revisited.
`EffortBurn.affordable` reads it, so it drove the row's early return and
printed, to a Beyonder at full SPI:

> "No push — this character has no spirituality left, and personal SPI cannot
> be spent below 0."

**Directly beneath the rule granting the push**, which the same row prints
one line above. The screen named the rule and denied it in the same breath.

Second defect on the same two lines, latent behind the first: the handler was
`if (kind == BurnKind.PHYSICAL) onSpendFp(points)`. Had a mystical push ever
been offered, it would have moved the target and charged nothing — "printing
the rule and not charging it is how a tracker starts lying" (6m).

### Reachability, checked against the data rather than argued

The dialog offers EVERY skill by a documented decision ("this book tags no
skill as a combat skill, so which of them can attack is the GM's call"), and
`pathways.json` grants Ritualistic Magic in **27 blocks** — including
Sequence 9, the FIRST potion, of Fool, Door, White Tower, Hanged Man,
Darkness and Hermit. No premade owns it unpotioned; 19 of 23 fixtures print
`spi: 0`. So this is a Beyonder-only path, which is why no golden test has
ever seen it, and it is reachable on a character's first potion.

**The design review made me check the claim rather than assert it.** It asked
whether the Roll button needs a weapon or an unarmed attack, since neither
resolves for Ritualistic Magic — if it did, the skill would be unrollable and
my reachability evidence false. It does not: `AttackDialog.kt:521` is a bare
`TextButton(onClick = onRoll)` with no enable condition. The claim survived,
but it was a claim until it was checked (6bs, 6ca).

### What the design review changed before any code

Reviewed as a DECOMPOSITION, no diff, because it moves a dialog boundary —
6bv's exact failure mode. Verdict SHIP, with one finding I took:

- I proposed `onSpendSpi` as a sibling to the existing `onSpendFp`. The
  reviewer pointed out the skill dialog already answers "which pool does this
  kind spend" once, in a single `onBurn: (BurnKind, Int) -> Unit`, and that a
  second single-kind callback is **the same predicate at two call sites**.
  Correct. Better still, both dialogs live in one composable, so the lambda is
  now hoisted ONCE in `SheetScreen` and passed to both — the change removes a
  copy rather than adding one.
- `EffortBurn.requireOnePool` stays deliberately uncalled and now says why in
  a comment: one skill per maneuver means every outcome shares one `BurnKind`,
  so "you cannot spend both" is unviolatable from here. The invariant was
  real and silent; a future multi-skill combo would have broken it quietly.

### The two reviews disagree, and correctness wins

The design review's binding requirement was that `spiAvailable` be a LIVE
expression, never `remember`ed: two attacks of one maneuver share one pool, and
a captured snapshot would let each spend it. The diff review's only remaining
finding is that the non-memoized `spiAvailable` and `burn` churn lambda
identity and cost recomposition — i.e. **it proposes exactly what the first
review forbade.** Both are right about their own axis. The correctness
requirement wins, it is now stated in the KDoc as a requirement on the caller,
and the performance note is refused: no profiler has said so, and the
reviewer's own text records that every other handler in the file is written
the same way. A LOW that would reintroduce a MEDIUM is not a LOW.

### `scopeOf`, and why it was deleted rather than wired

6cf offered two readings — pinned-sentence file, or gap. It is a third: a
one-line wrapper for `pool(kind).scope`, for "a screen that shows why it is
offered". The screen **does** show it, from a row it already holds for
`pool.pool` and `pool.max`, so calling the wrapper would resolve the same row
twice to deliver one of its three fields. An accessor for one field of three
is not an abstraction. Deleted, with the reasoning left where it stood.

### Verified

No unit test was added and none can be: the whole defect lives in a composable,
and this repo tests none (CLAUDE.md section 6). **1117 Kotlin and 267 Python,
all green and all unchanged** — said plainly, because a green suite proves
nothing about this slice. The device is the instrument.

`Medium_Phone_API_36.1`, Academic Researcher seeded as a **Fool Sequence 9**
(SPI 9/9, Ritualistic Magic 13) — the attack dialog does list it, alongside
Divination Arts 12 and Spiritual Intuition 12:

- **Before**, on the unmodified build: `3d6: 13 vs 13 — success`, then the
  scope line, then the false sentence. Sheet read SPI 9/9 throughout.
- **After**: `1 SPI buys +1, and the book prints no cap. Spent so far: 0.`
  with chips +1..+4.
- Pushing +2 read `3d6: 10 vs 15 — base 13, modified to 15`, sheet dropped to
  **SPI 7 / 9**, and `sessions.json` held `"spiSpent":2`. The target moves and
  the pool is charged — the half the old handler would have dropped.
- **The shared pool, which is the review's question 2**: a Rapid Strike made
  two attacks, each with its own "Spent so far: 0" (the cap is per roll).
  Spending **4** on the first shrank the SECOND row's offer from +1..+4 to
  **+1..+3 without it being touched**. No stale snapshot.
- Draining to 0 brought the disclaimer back **on both rows**, which is the
  point: it was never wrong to print, only wrong to print then.
- **The route that changed rather than appeared**, on Barmaid Brawler: All-Out
  Attack (Determined) at 2 FP plus a +2 push left **FP 6 / 10**. Two and two,
  charged once each — the maneuver's `onSpendFp` and the push's `onBurn` reach
  the same pool by different events and do not double-charge.

**Codex still refuses** (resets 2026-08-20 14:23). Two `ecc:kotlin-reviewer`
passes, one on the decomposition and one on the diff, each scoped to three
questions.

**Next, with evidence.** Nothing from 6cf's sweep is left: `scopeOf` is gone
and `attackOptions` was 6cg. The largest thing outstanding is still 6ca's
`Ether Body Damage`, which wants purchasable traits — and 6cb's verdict that a
printed cost RANGE and a printed `(specify)` are one feature, not two.

## 6cg. A pin that only runs one way

6cf deferred `ContentLoader.attackOptions` — "read by nothing at all, not app,
not rules, not tests" — and asked whether `attack_options.json` is a pinned-
sentence file or a gap. The claim is true as stated: the only Kotlin hits are
the loader and its data class, and the app's `AttackOptions` composable is a
name collision, not a caller. **It is neither of the two things 6cf offered.**

The two rows hold Rapid Strike and Deceptive Attack, and both rules ARE
applied — `Attacks.RAPID_STRIKE_PENALTY`, `DECEPTIVE_STEP_PENALTY`,
`maxDeceptive`, `attackCount`, and two `require`s that refuse either at range.
So there is no unapplied rule here. The defect is one layer up: **the pin runs
in one direction only.**

- `extract_combat.PINNED_OPTIONS` pins the book's sentence INTO the JSON, so
  the book changing "-6 to skill" fails on the Python side. That is
  book -> JSON.
- Nothing ran JSON -> Kotlin. `AttacksTest` ties the enum's labels to
  maneuvers.json (`every maneuver this enum implements still names a row in
  the book`) and Strong Blow and Heroic Charge to extra_effort.json — every
  number in `Attacks` had that tie **except the two whose row lives in the
  file with no caller.** The absent caller and the absent guard are one fact,
  not two.

`AttacksTest`'s own class KDoc says "this is what pins it to the book", and
for these two constants it did not. **Commentary about what is pinned expires
exactly the way a sweep verdict does (6ba, 6bl)** — and here it expired at
the moment it was written, because the tie it claims was never built for the
rows in question.

### What the new tests assert, and what was already covered

Honest accounting, because two existing tests (`each rapid strike stacks
another six`, `deceptive attack costs the attacker two per one imposed`)
already fail if a constant moves:

- The substrings are built FROM the constants, never spelt out. A literal
  would pass under a changed constant, which is the whole shape of the miss.
- The genuinely new coverage is **the melee clause on Rapid Strike's row**.
  `modifiers` refuses a ranged plan carrying a rapid strike, and the sole
  warrant for that `require` is the scoping clause in this row —
  `PINNED_OPTIONS` pins the melee wording for Deceptive Attack's row and
  **not** for this one. It was unchecked on both sides.
- RED-proved by neutering the data rather than the code: dropping "melee"
  from the Rapid Strike row leaves **all 267 Python tests green** and fails
  exactly one Kotlin test. That is the direction that had no instrument.

Both constants RED-proved the ordinary way as well — `-6` to `-5` and `-2` to
`-3`, one at a time, restored from a `cp` backup each time (never
`git checkout`, which discards uncommitted work).

### The review found the guard passing under a sign flip

The first version asserted `"${RAPID_STRIKE_PENALTY} to skill" in rule`, and
that is **satisfied by `+6`**: `"6 to skill"` is a substring of
`"-6 to skill"`, so the minus sign is a prefix the match can shed. Same for
`"2 penalty"` inside `"a -2 penalty"`. A penalty silently becoming a bonus is
not a hypothetical here — it is the defect this repo has already measured
once, in `ModifierResolver` (section 5: base -4 landing on +2). **A guard
written against drift in MAGNITUDE was blind to drift in SIGN**, which is the
cheaper mutation and the worse one.

The fix is one word each: anchor to the token before the number — `"suffer
$n to skill"`, `"a $n penalty"` — so the sign is inside the match rather than
in front of it. Verified both ways against the row text: the old form matches
the sign-flipped string and the new form does not, and building with `+6`
fails the tie test.

Two more findings, one taken and one refused:

- **Taken:** the bare word `"melee"` proves presence, not scope — a
  re-extracted row mentioning melee anywhere else would satisfy it while the
  clause that scopes the option disappeared. Now `"melee Attack maneuver"`,
  the clause itself.
- **Refused:** the same check on Deceptive Attack's row. `PINNED_OPTIONS`
  already pins `"-2 penalty to your melee attack roll for every -1"` whole,
  so that clause has a guard; a second is the belt-and-braces verification
  this repo's own token discipline refuses. **A finding that is real for one
  row is not automatically real for its neighbour** — the neighbour's guard
  lives in the other language.

**1117 Kotlin** and **267 Python**, all green. No production change: the two
rules were already correct and already reach the dice, so there is nothing to
device-verify.

**Codex still refuses** (usage limit, resets 2026-08-20 14:23; today is
2026-08-15). One scoped `ecc:kotlin-reviewer` fallback pass — three
questions, two files, "do not explore broadly" — returned in under two
minutes with the HIGH above. **The scoping budget keeps paying** (6af, 6cb):
the narrow ask is what got a finding that a whole-diff sweep of a 41-line
test file would have had no reason to look for.

**Next, with evidence.** The remaining API-sweep survivor is
`EffortBurn.scopeOf`, untested and uncalled — the one candidate 6cf recorded
that this slice did not touch. Beyond it, 6ca's `Ether Body Damage` still
wants purchasable traits, which is the largest thing outstanding.

## 6cf. A sixth sweep direction, and the +2 divination never asked for

`Divination.supportSkillsOwnedBy` offers Ch. 6.5 p. 55's four supporting
skills, and the divination dialog has a chip row. The rule was extracted in
6p, tested, documented — and **`Divination.supportBonus` had no production
caller at all**, so a diviner with Occultism and Psychology rolled flat where
the book gives +2. Five premades carry at least one of the four; Fortune
Teller carries three, which is the cap case.

**This is 6am's finding one chapter over, and 6am fixed only the half it was
looking at.** That slice found ch. 7's five supporting skills keyed on their
printed string, so `Hidden Lore (relevant domain)` matched nothing and its +1
vanished for the four premades carrying Hidden Lore. Ch. 6.5 prints its own
list with its own bracket — `Hidden Lore (relevant)` — and its own cap, and
nothing had ever looked at it. **A fix is a claim about one route**; the
sibling route is not covered by having understood the rule.

### The sixth sweep direction: a public function with no caller

6ax listed four directions and 6ay/6ba added the seam, making five. This is a
sixth, and it starts where none of the others can: **every public function of
`core/rules`, checked for a caller in `app/`.**

- The field sweep (6ai) asks whether extracted DATA has a reader. This one
  asks whether implemented BEHAVIOUR has a caller, which is a different set:
  `supportBonus` reads its data perfectly and is itself read by nobody.
- **6aj and 6be each found this exact class by accident** — chapter 4's two
  sense tables were unreachable because the sheet listed only published
  skills, and potion-granted skills had no row at all. Two of two by accident
  is not a method (6bh), so it was run deliberately: **323 public functions,
  46 with no caller, 3 real after triage.**
- **Two of the five candidates were false positives, both from the same
  regex flaw**: a `name(` search cannot see a PROPERTY of the same name
  (`AntiDivinationTechniques.detectionBonus`) or a `::method` reference
  (`Wealth::namesEconomicStatus`). Check the shape of a miss before believing
  it — the instrument's false positives are as informative as its hits.
- Most of the 46 are legitimate and the sweep's value is saying which: guard
  accessors that exist for the coverage tests (`known`, `skillBonusNames`,
  `unappliedNames`), content loaders consumed by the golden tests, and
  `EffortBurn.requireOnePool`, an API-level guard 6ak documented as
  deliberately unreachable.

### What the wiring had to get right

- **The bracket rule is `RitualMode.bareSupportName`, not a second copy.** It
  is ONE rule — the book naming a domain the GM judges relevant, not part of
  a skill's name — and two copies of one decision is the failure this
  codebase has produced seven times (6ag, 6ai, 6be, 6bp, 6bs, 6cd, 6ce).
  Only the ROW's bracket is stripped, so a sheet's `Hidden Lore (Fae)` still
  matches: relevance is the GM's call, not a spelling.
- **The cap stays +2 and is NOT ch. 7's +3.** Two printed rules — ch. 6.5
  line 3580 prints "cumulative but cap at +2 total from supporting skills"
  and ch. 7 prints +3 for its own five. 6at named this pair while sweeping
  code back to the book, and straightening them is the 4.1 sin. The screen
  says which cap it is using and why, because the two dialogs sit two taps
  apart.
- **The matching lives in rules, not the screen**, for 6am's own reason: a
  dialog comparing names itself can offer a chip `supportBonus` then refuses,
  or withhold one it would accept.
- **A choice, not an automatic bonus.** The row is "Hidden Lore (relevant)",
  and whether the domain is relevant to THIS question is the GM's; granting
  it unasked answers that question for them.
- **Named twice pays once** — a character has one Occultism (6y's rule for
  the ritual list).
- **The bonus is named on its own line, never folded into "circumstances"**,
  because the signed numbers must sum to the target above them — and with
  the cap biting they do not sum to the chips the GM ticked, which is exactly
  why it needs its own name.
- The empty branch names all four skills rather than rendering nothing, the
  call 6am made for the ritual dialog's own empty case: a bare absence reads
  as a screen that forgot.

**Codex refused** (usage limit, resets 2026-08-20 14:23). One
`ecc:kotlin-reviewer` fallback pass scoped to six named files with six probes
returned **no findings at any severity**, tracing every probe to a file:line
— including the two worth having: the bare-name match cannot over-match a
longer skill, and a stale entry in the chosen set is self-correcting because
the filter iterates the CURRENT owned list.

**1115 Kotlin** (1009 `core:rules`, 86 `app`, 20 `core:model`) and **267
Python**, all green. Both readings RED-proved by neutering each and restoring
from a `cp` backup: the bracket rule replaced by a printed-string match (1
failure), and the dedup removed (1).

Device-verified on `Medium_Phone_API_36.1`, **Fortune Teller** seeded as a
Fool Sequence 9 — the one premade carrying three of the four, and a pathway
that grants Divination Arts so the screen is reachable at all (6aq):

- The dialog shows **Supporting skills — "+1 each, capped at +2 — the book's
  own cap for divination, which is not ch. 7's +3"** over exactly three
  chips: **Occultism +1, Hidden Lore (relevant) +1, Psychology +1**.
  **Cogitation is absent**, which is the ownership filter; and Hidden Lore is
  there at all only through the bracket rule, since her sheet spells it with
  a specialization.
- Untouched, the panel reads **"Roll 3d6 ≤ 14 — Skill 12, question +2"**.
- Ticking **all three** reads **"Roll 3d6 ≤ 16 — Skill 12, question +2, +2
  from supporting skills"** — **+2 and not +3**, and 12+2+2 sums to the 16
  printed above it.
- Asking rolled **"3d6: 11 vs 16 — Success by 5+ — Crystal clear"** and saved
  `spiSpent: 1`. That 11 is the observable that matters: against the old
  target of 14 its margin is 3, which is the "Clear" band. The bonus reaches
  the dice and moves the answer, not just the caption.

**Next, with evidence.** The API sweep's other two survivors are recorded
rather than built: `ContentLoader.attackOptions` is read by nothing at all —
not app, not rules, not tests — which is 6ai's own defect class in a loader
rather than a field, and wants its own slice to decide whether the two rows
are a pinned-sentence file or a gap. `EffortBurn.scopeOf` is untested and
uncalled. Neither is a rule going unapplied today, which is why neither
displaced this one.

## 6ce. The table three slices deferred, and the word that names no group

`blessings.json` carries Ch. 3 p. 19's Seven Orthodox Gods Blessings, the
picker sells them, and one of the seven reaches a roll. **6bc read this page
and deliberately left it; 6ca and 6cb both restated the deferral. All three
gave a reason that had stopped being true.**

- **The blocker expired and nobody had re-checked it.** 6bc's reason was that
  no premade prints a blessing and traits were not editable, so the file
  would be read by nothing — the defect 6ai, 6al and 6am each found. 6ca
  shipped `PurchasableTraits` and 6cc widened it to instantiated rows, which
  is the consumer. 6ca's own KDoc then said the blessings "stay deferred",
  citing the M3 collision — true of ONE row, and the reason it gave for the
  other six was the absence of a purchase path it had just built. **A
  deferral is a claim about the data and expires like any other verdict**
  (6au, 6av, 6ca, and 6be retracting 6ba).
- **It is a seam, and that is why no sweep found it.** `advantages.json`
  claims to 1097 and `disadvantages.json` resumes at 1131; these 21 lines
  were claimed by nobody. `extract_content.py` has excluded them since M0 for
  a purely mechanical reason its own docstring records: **the table prints no
  cost column at all**, because the price is stated once in prose for all
  seven rows, and that parser finds records by their cost line. So the
  exclusion was never a decision about the content — it was a parser that
  structurally could not see it, and the docstring saying so is what kept it
  looking deliberate for four milestones.
- **The price is stored once, as `costEach`.** Repeating 75 on seven rows
  would make the file claim a column the book does not print (6v). It is the
  same split every "number inside a sentence" takes, with the difference that
  this one is load-bearing: without that sentence the price has no source,
  so `verify()` fails if it goes.

### "occult/knowledge skills" names ONE banner, and one grep is the warrant

The God of Knowledge's cell reads *"Expert in mysticism. +5 to all
occult/knowledge skills."* Chapter 3 prints fifteen skill banners and
`skills.json` has carried each skill's banner since 6bt, so a cell naming a
category is a join this app can already make (Empathy and Clueless are the
other two). The question is which group the slash names.

- **"occult skill" is printed NOWHERE in this book.** "occult" occurs 28
  times — the skill Occultism, "occult powers", "Magical/occult plant
  knowledge" — and never as a group of skills. "knowledge skill" occurs
  three times and **one of them is the banner itself**. So the slash cannot
  be joining two groups: one half of it names nothing.
- **Both rejected readings author content, in opposite directions.** WIDER
  would add Astrology, the one occult-flavoured skill outside the banner —
  but the book files it under SURVIVAL & OTHER, so calling it occult is my
  judgement and not the book's. NARROWER would mean curating which of the
  banner's twenty-six rows are occult, which is the list-building 6i refused
  when no skill is tagged as a combat skill. Every occult skill the book
  prints — Occultism, Hidden Lore, Thaumatology, Theology, Alchemy — is
  already inside it, so the printed-only reading loses nothing.
- **The evidence is split across the two languages that can check it.**
  `extract_blessings.verify()` owns the grep, because only it can read the
  book; `BlessingsTest` owns the consequence, that every occult skill the
  book prints is a KNOWLEDGE skill and Astrology is not. Same standard 6ad
  set, and 6v is why it can never be per-source.
- "Expert in mysticism." grants nothing and is named in the clause guard's
  prose exemption rather than reported.

### The six that reach nothing, and the one refusal that is about this app

Five are the familiar refusals — no number ("Rapid ID; curses negated"), the
other party's roll ("most divination fails against you", the 1d6/round burn),
a clock (ten hours underwater, regeneration), a roll this app does not make
(pressure, revival). **God of Combat is the only one whose reason is about
this codebase rather than the book's silence, and it says so.** Its "+3 ST,
+3 DX, +3 HT" is real and unambiguous; what is missing is that M3 forbids
writing a granted attribute into a billable field, and **a potion is the only
thing in this codebase that grants an attribute** (6al). A reason claiming
the book prints nothing would be the fresh excuse 6bo caught one commit from
shipping.

**Nothing forbids holding two blessings, so nothing refuses two.** The
requirement is "a confirmed believer in the corresponding Church", which is
per blessing, and the prohibition sweep over the table's own lines finds no
exclusion at all — re-run against this slice's subject, because a sweep's
verdict expires (6ba). Refusing would author a rule, which is the opposite
call to `Wealth`'s exclusion, where the book DOES say a character has one
economic status (6bx). The same blessing twice is still refused, for the
reason every trait is. And the requirement itself is shown and gates
nothing: this app models no faith and no reconciliation (6af).

### A guard that had been blind to a whole map since the slice that added it

`no clause of a handled trait goes unmentioned` builds its set from three
registries by name, under a comment reading *"every applied map, not a list
of the ones that existed when this was written: the guard was blind to
SKILL_BONUSES once already (6bs)"*. **`CATEGORY_BONUSES` was not in that
list**, so Empathy's and Clueless's cells were unchecked from 6bt until now
— and the proof is that correcting one line forced **four** lead-in
sentences to be named at once, none of which had ever been triaged. 6bs's
finding, one map over, with its own warning printed above it. The general
shape is worth more than the fix: **a completeness guard whose scope is a
hand-written list is only as complete as the day it was written**, and a
comment warning about exactly that does not prevent it.

**Codex refused** (usage limit, resets 2026-08-20 14:23 — probed, not
assumed; today is the 15th). One `ecc:kotlin-reviewer` fallback pass scoped
to eight named files with seven probes returned **no CRITICAL and no HIGH**,
traced every probe to a file:line, and found one LOW worth taking: the
picker's Drawbacks filter excluded blessings by `!advantage` where the
Advantages filter excluded them by NAME, so a blessing that ever billed the
other way would render in two sections. Unreachable today, which is when this
repo closes a permissive path rather than after the next caller finds it
(6l). Its second LOW is recorded and not fixed — a malformed `blessings.json`
would throw during the sheet screen's composition, which is true of
`advantages.json` already and so is not a surface this diff adds.

**1113 Kotlin** (1007 `core:rules`, 86 `app`, 20 `core:model`) and **267
Python**, all green — and the first draft of this paragraph said 1006/87,
which is the count-in-prose habit (6as) turning on its author for the third
time in four slices. Four readings RED-proved by neutering each and restoring
from a `cp` backup (6br): the applied join removed (2 failures), the banner
pointed at Astrology's category (3), God of Combat's report removed (2), and
the clause guard's new prose exemption removed (1) — that last one proving
the widened guard is live rather than merely wider.

Device-verified on `Medium_Phone_API_36.1` (1080x2400), Academic Researcher,
whose ten skills happen to contain the perfect discriminator — Research is a
KNOWLEDGE skill and Mathematics is ACADEMIC, and **both are IQ-based**, so
only the banner differs:

- The picker renders **"The Seven Orthodox Gods Blessings"** with the book's
  own requirement as its subtitle, all seven at **+75 pts**, and **Drawbacks
  starts cleanly after them** — the partition holding on screen.
- Buying the God of Knowledge saved `"traits":["Ether Body Damage","God of
  Knowledge and Wisdom"]` — the NAME alone — and took the budget from **91 of
  110** to **166 of 110 — over budget**, which is exactly +75 billed.
- **Research** rolled **"3d6: 10 vs 18 — Excellent success, margin 8, base
  13, modified to 18, `trait:god of knowledge and wisdom: 5`"**, a breakdown
  that sums.
- **Mathematics**, same character, same attribute, rolled **"3d6: 13 vs 9 —
  Failure, margin -4"** with **no contribution line at all**. The scope, on
  one screen.
- Seeded with **God of Combat** and **Lord of Storms**, the sheet prints both
  cells with their reasons — and **HP stayed 9/9**, so the +3 HT really is
  inert rather than quietly applied.

Chapter 3 is now at **87.1%**, from 86.2%.

**Next, with evidence.** `Secret` still needs a sheet to quote its cell and
none does (6cd), so it is not a slice until the data changes. What this slice
NAMES is the one thing that would make the God of Combat row reachable: a
trait that re-derives an attribute the way a potion does. That is the M3
question 6bc predicted, now isolated to a single row with the other six
shipped around it — and it is a real feature with a design review of its own,
not a content pass.

## 6cd. A quotation is evidence; a filter is not

`TraitEffects.quotedInstanceRow` resolves the six `Sense of Duty (group)`
spellings the fixtures carry, and **refuses the rule that would also resolve
`Secret`** — which is the slice.

6by recorded eight sheet spellings as a GAP and named the narrow warrant a
successor should start from. 6cb then re-read them, and 6cc built a whole
feature on the premise that **a printed cost RANGE means the row needs an
instance to settle its cost**. That premise looks like it licenses the rule 6by
refused, so the question was put to a design review before any code, with both
candidates written out and the reviewer asked to argue the strongest case
against the one I was leaning toward.

| | |
|---|---|
| **(A)** the verbatim-quotation warrant, resolving Sense of Duty | **accepted** |
| **(B)** "a bounded interval and no enumerated variants means a bracket instantiates it", resolving Secret too | **refused** |

### Why (B) fails, in one sentence worth keeping

**"Cost-inside-bounds corroborates pricing validity, not catalogue identity."**

(B) is a real narrowing — it excludes Shyness and Social Stigma because they
enumerate, and Reputation because it prints no bounds, which are exactly the
three objections 6by raised. But **excluding the counter-examples is not the
same as producing evidence.** The corroboration on offer — all 19 fixture
instances paying inside their printed interval, pinned as a test in 6cb — is
about what a row COSTS, and the question is what a row IS. Strip the filters
and (B) is the plausible match 4.1 calls repair.

So the app now reads a bracket for exactly one row, and the set is named in
production with the quotation pinned by a test rather than derived — the
evidence lives in the FIXTURES and production cannot read them.

### What (A) rests on, and how far it carries

Church Healer prints `"Sense of Duty (Flock) - "` followed by that row's own
effect cell **verbatim** — the only spelling in the corpus that quotes exactly
behind an instantiated head, which 6cb had already had to correct 6by about.
That is the sheet asserting the instantiated name IS the bare row. The five
bare instances follow because the cell says *"certain groups"* and Patients,
Friends, Public, Coworkers and Workers are each a group: the same pattern
applied, not five independent guesses. It carries no further — `Secret` prints
an interval and enumerates nothing, exactly as Sense of Duty does, and no sheet
quotes its cell.

### Five guards fired, and one of them was built for this

Every one recorded the old state and had to be moved deliberately:
`a tail that is not the book's own words`, the unmatched-glued pin, the
`(specify)`-boundary test, 6by's census map — and **6bz's asymmetry guard**,
which exists to fail when production out-reaches the test-side audit. It did,
naming the six spellings. That is the one construct in this repo that cannot
notice when it stops being needed, so it is worth saying it noticed.

### Two review findings, both mine, both about the same seam

- **The glued path dropped its tail unconditionally**, resolving on the bracket
  alone — so a sheet whose quotation was a typo, a partial quote or a different
  note would have resolved anyway. That is the repair 4.1 forbids, and it let
  the new path round 6br's exact-tail rule. The tail must match now, and the
  truncated near-miss is pinned. **My own KDoc claimed that near-miss was
  covered while the test I had just edited no longer covered it** — a
  count-in-prose error inside a doc comment.
- **`displayName` did not know about the resolver**, so Church Healer's row
  would print the 130-character effect cell as its TITLE and repeat it as the
  note underneath. 6br's device-only defect, one resolver over.

**Codex hit its usage limit** partway through (resets 2026-08-20 14:23), after
returning both findings above. The third pass was the documented fallback —
one `ecc:kotlin-reviewer`, scoped to two named files with six explicit probes
(6af: scoping is what gets an answer). It returned **no CRITICAL or HIGH**,
traced all six to a file:line rather than asserting, and found one LOW worth
taking: a `contains(SHEET_SEPARATOR)` guard of mine was a no-op, since
`substringBefore` returns the receiver when the delimiter is absent — and a
no-op that reads as load-bearing misleads the next reader.

**1106 Kotlin** (1000 `core:rules`, 86 `app`, 20 `core:model`) and **261
Python**, all green. RED-proved on the boundary that matters: adding `"Secret"`
to the set fails **four** guards, which is the refusal made checkable rather
than argued.

**Display-only, and said rather than implied.** `Sense of Duty` prints no roll
and no number, so what this buys is the effect cell rendering on the sheet
where six characters previously showed a bare name. Not device-verified for
that reason — no number moves, and the one visual risk (the title repeating the
note) is pinned by a test after the review caught it.

**Next, with evidence:** `Secret` needs a sheet to quote its cell, and none
does — so it is not a slice until the data changes. The remaining unresolved
spellings are the recorded disagreements 6by triaged (Absent-Minded, Paranoia,
two Pacifisms, No Sense of Humour, Wierdness Magnet, Blacklisted), each refused
for a reason that still holds.

## 6cc. One record shape, and five review passes to get its identity right

`PurchasableTraits.instantiable` / `.instanced` / `.resolved` price the
seventeen rows the picker withheld, and the builder buys them. This is the
slice 6cb named after its design review collapsed two roadmap items into one:
**a printed cost RANGE and a printed `(specify)` are the book asking for an
instance in two notations**, so they are one feature.

**It is not cosmetic.** `Obsession` prints a flat self-control target in the
book's own NAME ("Obsession (12)", stripped into the catalogue by the extractor
in 6am) and `Compulsion: Collection (pathway)` prints Will−2 in its effect
cell. Both print a RANGE rather than a price, so **neither roll was reachable
by any route** before this. `Phobia (specify)` likewise carries the only Fright
Check modifier this app applies (6bv).

### The parts are stored and the name is composed, in three rules

Codex's design verdict was blunt: do not store `"Shyness (Mild)"`, because the
book prints `Shyness` and prices `Mild` inside the cell — that spelling is the
SHEETS' convention, and persisting it makes an app-authored string into
canonical data. So `SavedInstancedTrait(row, variant?, instance?, cost)` holds
the parts, and the identity is composed at load:

| Row shape | Composed name |
|---|---|
| prints `(specify)` | `Phobia (Cities)` — the instance |
| its cell enumerates priced variants | `Shyness (Mild)` — the variant |
| anything else | **`Sense of Duty`** — the book's own bare name |

- **The third rule is the load-bearing one.** `"Sense of Duty (Patients)"`
  resolves to nothing in this app — a recorded gap (6by), not a refusal — so
  composing it would hand the engine an identity it cannot match and the trait
  would silently do nothing. The instance is kept for display alone.
- **Which is exactly why nothing may key on the composed name.** Two
  `Sense of Duty` purchases differ only in their instance and are identical as
  `Trait`s. Removal is therefore by INDEX, and a purchase is identified by
  `InstancedPurchase.key`.

### Monastery Scholar carries two Vows, and that decided the duplicate rule

A census of the fixtures found one published sheet holding one instantiable row
twice — `Vow (Partial Silence)` and `Vow (Vegetarian)` — so the blanket
single-copy refusal `canBuy` applies to fixed-price rows would **forbid a
character the book prints**. Checked against the data rather than reasoned
about (6bs, 6bv).

**But a variant is not an instance**, which took a review pass to see: Shyness
is Mild *or* Severe, two readings of one condition, where a second vow is a
second vow. Since a purchase carries a variant XOR an instance, the fix was to
drop the variant from the key entirely — an enumerating row then keys on itself
and admits one, and every other row keys on what the instance is. One line,
both rules.

### Five review passes on the app half, and every finding was an identity bug

Each one is the same shape — two things that should be one — and none is
visible in a unit test that checks a value:

1. **Buy refused only an identical row**, cost included, while composition
   keyed without the cost. The same trait saved at two prices: billed once,
   listed twice, and giving back the first would silently change what the
   second cost.
2. **Enumerated variants counted as separate instances**, so a premade printing
   `Shyness (Mild)` could be sold `Shyness (Severe)` beside it and the budget
   billed both.
3. **That severity rule lived in the picker only**, so a hand-edited save got
   round it — the fix is below the UI, which is the call 6bx's wealth exclusion
   already needed for the same reason.
4. **The builder resolved a saved row without the held traits**, so it could
   price a purchase the composed character did not have. Fixed by making ONE
   call answer both questions (`resolved`), not by adding a second check.

Plus four on the rules half: an interval accepted without the instance that
settles it (which contradicted my own KDoc), an unlicensed entry consuming a
duplicate key and suppressing the corrected purchase behind it, an unnormalised
key, and `unoffered()` still reporting rows the same object had just made
buyable — an API contradicting itself.

- **`Reputation` and `Language (specify)` stay withheld**, with a sharper
  reason than "prints a range": they print **no bounds at all**, so there is no
  interval to choose inside. An unbounded row is not a range with no ceiling —
  it is a pricing procedure this app has not read, and taking whatever number a
  player typed would make the SAVE authoritative over the book, inverting the
  rule every other stored choice here follows.
- **A save cannot widen what the book allows.** The cost is the one stored
  number that could contradict the catalogue, so it is re-validated against the
  printed interval on every load — skipped, never deleted (6as).
- **`SavedEdits` gains a SEPARATE list rather than widening `traits`**, and
  that is a compatibility decision: `traits` is a `List<String>` in every save
  already written, and `SessionStore.load` discards the WHOLE file on a decode
  failure, so one schema change would cost every character their walk. Pinned
  by a test that loads a save written before this slice existed.
- **Two Phobias are two declarations**, and it falls out rather than needing a
  rule: a fright clause is keyed by the DISPLAYED name, so `Phobia (Cities)`
  and `Phobia (Spiders)` are separately declarable and separately applied.

**1104 Kotlin** (998 `core:rules`, 86 `app`, 20 `core:model`) and **261
Python**, all green. Three readings RED-proved by neutering each and restoring
from a `cp` backup (6br): the bare-name composition (3 failures), the duplicate
key (2), the printed interval (1); the two rules-half review fixes were
RED-proved together (2).

Device-verified on `Medium_Phone_API_36.1` (1080x2400), **Academic Researcher —
the one premade that prints `Shyness (Mild)`**, which is what makes the
severity rule observable rather than argued:

- **The picker's three groups, on one screen.** `Sense of Duty −15 to −2 pts`
  and `Secret −30 to −5 pts` under "Priced by the instance";
  **`Shyness — already held`** greyed out, and tapping it does nothing;
  `Social Stigma` offering `Criminal Record -5 / Servant Class -5`; and
  *"Not offered — the book prints no cost bounds for this row at all …
  **Language (specify), Reputation**"*.
- **The ranged shape.** `Sense of Duty` opens with a text field reading *"What
  is it? The book prices this row by the instance"*, the stepper at **−2 pts**
  with `−` greyed at the floor, *"The book prints 2 to 15 for this row."*, and
  **Buy disabled** under the red *"…so name the instance before buying it."*
  Typing `Patients` enabled Buy and cleared the warning; eight taps of `+` took
  it to **−10 pts** and un-greyed `−`.
- **The save holds PARTS, not a name**:
  `{"row": "Sense of Duty", "variant": null, "instance": "Patients", "cost": 10}`.
  The builder row reads **"Sense of Duty — Patients / −10 pts"** — the book's
  own bare name with the instance beside it, which is composition rule 3 on
  screen.
- **The `(specify)` shape differs, and says so.** `Vow (specify)`'s field reads
  *"Name it — the book asks you to"*, and the dialog **title updates live to
  "Vow (Silence)"** as it is typed — the composed name previewed from the parts.
- **Two Vows stood side by side** — `Vow (Silence)` and `Vow (Vegetarian)`,
  Monastery Scholar's case on a different character — and **Give back on the
  middle row removed the right one**, leaving `Vow (Vegetarian)` in the save.
- **The enumerating shape has no field and no stepper at all**: `Social Stigma`
  showed two chips over *"The book prices each of these itself."*, and choosing
  Servant Class saved
  `{"row": "Social Stigma", "variant": "Servant Class", "instance": null, "cost": 5}`.
- **Then `Social Stigma — already held` greyed out too** — the severity rule
  firing against a BOUGHT trait where Shyness fires against a PRINTED one,
  while `Vow` and `Sense of Duty` stayed offered beside them. The whole
  duplicate rule, both halves, on one screen.
- Incidentally confirmed: a save written before this slice still loads (its
  `Ether Body Damage` purchase survived untouched), and the drawback cap fired
  correctly at *"Disadvantages total 60, capped at 40"*.

**Two instrument notes.** A *"Try out your stylus"* system overlay opened over
the keyboard and ate a tap plus half a `input text`, leaving `PatiPatients` in
the field — SystemUI, not the app, and `mCurrentFocus` named it (6ah). And
every `adb` command naming a device path was prefixed `MSYS_NO_PATHCONV=1`
(6bp); the emulator was launched at `-memory 2048` after reaping the Kotlin
compile daemons, with the APK built first in one short-lived JVM (6am, 6be).

## 6cb. The census that had to become a test, and a design that survived one question

Two test-only commits, and the slice they were meant to precede is NOT built —
because the design review answered in a way that collapses it into a different
feature. 6ca named the work: **the nineteen rows the trait picker withholds.**
This is the census and the reading it produced.

### The census, which corrected me three times

Nineteen rows print no single cost. Fourteen print a range, five print
`(specify)` in the name. Against the fixtures:

| | |
|---|---|
| Withheld rows the 23 sheets instantiate | **9** |
| Instances across those sheets | **19** |
| Instances paying INSIDE the row's printed range | **19** |

- **It is not a dropped placeholder.** `Sense of Duty` and `Secret` print bare
  at book lines 1167 and 1173 — no `(specify)` — so this is not
  `strip_self_control_number`'s shape (6am), where a normaliser deleted a
  printed rule. Checked before anything was written, because that class is the
  one this repo keeps finding.
- **The prohibition sweep over the trait lists forbids nothing** about buying a
  ranged row at a chosen price; its fourteen hits are effect prose inside the
  cells (6ba — a sweep's verdict expires, so it is re-run per slice).
- **I counted 19 instances as 18, by eye**, and wrote 18 into a doc comment AND
  into the design brief a reviewer was reading. The test said 19. **A count in
  prose is a finding (6as), and the cure is not to recount — it is to make the
  census executable and keep it**, which is what shipped. Third slice running
  to catch its own author's number.

### The retraction: a priced scale is not a set of variants

Mid-census I found what looked like a real defect. `pricedVariants` matches
`([A-Z]...)\s*\(?[-−](\d+)\)?` — it requires a MINUS SIGN, which only
disadvantages print. `Mystical Item` is an advantage and enumerates three
priced options spanning its own 5..15 range exactly: *"a minor charm (5 pts), a
useful tool (10 pts), or a significant piece of equipment (15 pts)"*. So a
third enumerating row, invisible to an instrument by construction — 6ba's
lesson for the third time, after 6bt's own too-narrow filter.

**It is not a defect, and reading the whole cell is what showed it.** The
sentence above those three options is **"Cost scales with power"**, and
`Duty (specify)` prints the same shape in the other sign — "Value reflects
frequency and danger: -5 for light duty, -10 for hazardous duty, -15 for
extremely hazardous". Both are a SCALE with worked points, not a closed set,
and both say the value moves. Read as enumerations they offer a variant
literally called *"or a significant piece of equipment"*. 6bb's shape exactly:
the fix was half-written and two lines of the book killed it.

- **What actually excludes them is capitalisation, and the KDoc credited the
  other condition.** `pricedVariants` is safe for two reasons: the "at least
  two members" rule keeps Stubborn's `"Will-3"` out, and the leading `[A-Z]`
  keeps a prose scale out, because a variant is a NAME (`Mild`, `Criminal
  Record`) and a scale point is not (`a minor charm`, `light duty`). The member
  count cannot separate them — both scales print three. **A rationale is a
  hypothesis about which condition does the work, and this one was half right.**
  Pinned by its own test now, RED-proved by relaxing the capital (3 tests fail,
  two of them 6bw's own guards).
- Nothing reaches the wrong answer today: no premade owns either row. That is
  when this repo closes a permissive path rather than after the next caller
  finds it (6l) — and the next caller was me, five minutes later.

### And a correction to where 6by filed one spelling

6by grouped Church Healer's `"Sense of Duty (Flock) - Must help/protect certain
groups even at personal risk"` under **paraphrase**, "an instantiation that
paraphrases a cell rather than quoting it". Its tail reproduces the catalogue's
notes **verbatim** — the only row in the corpus that quotes exactly behind an
instantiated head. What refuses it is the HEAD, never the quotation, and 6by's
own reason string said so while its group heading did not. `Blacklisted:
Specific Industry org or church` really is a paraphrase and stays refused.

### The design review answered in 34 seconds, and moved the goalposts

The first attempt — four questions, five files — **stalled for 38 minutes** and
was abandoned. Re-asked with two questions and two named files it came back in
**34 seconds**. 6af recorded that scoping a reviewer is what gets an answer;
this is the sharpest measurement of it yet, and the broad prompt produced
nothing at all.

Both verdicts corrected the plan:

- **"Your distinction is too clean."** I argued that 6by refused a rule about
  NAME RESOLUTION while offering a priced purchase is about PRICING, so the
  refusal does not apply. Codex accepts the paths differ and rejects that they
  are different rule questions: **a printed range means the row needs an
  instance to settle its cost.** The sheet evidence proves a cost inside the
  range is valid; it does not prove every integer in the range is a valid
  *unnamed* purchase. So a bare row at an arbitrary price is under-specified.
- **Do not store `"Shyness (Mild)"`.** That is a spelling only the SHEETS use —
  the book prints `Shyness` and prices `Mild` inside the cell — so storing it
  makes a sheet convention into app-authored canonical data. The record should
  be row + variant + cost, and rendering composes the display name.
  `Social Stigma` is why it matters: both its variants cost 5, so a price
  cannot say which was picked, and its two options do not span its 5..20 range
  the way Shyness's 5 and 10 span 5..10.

**That collapses two roadmap items into one.** 6ca said the `(specify)` rows
need a save shape while the ranged rows need the reading 6by refused. Under
this verdict the ranged rows need **no** new reading — they need the same save
shape, because a range and a placeholder are the book asking for an instance in
two notations. So all nineteen are one feature, and it is a schema change plus
real UI rather than a widening of the picker's filter. Left unbuilt on purpose;
scaling a slice down is not the same as shipping half of one.

**1086 Kotlin** (987 `core:rules`, 79 `app`, 20 `core:model`) and **261
Python**, all green. Both readings RED-proved: the capitalisation relaxed (3
failures), and one printed range narrowed so its instances fall outside (1).
Both commits Codex-reviewed clean. Test-only, so there is nothing to
device-verify and no behaviour changed — stated rather than skipped.

**The next slice, with its design already half-reviewed:** one record shape for
an instantiated trait — book row, optional variant, optional instance note,
chosen cost — which offers all nineteen withheld rows at once. It needs
`SavedEdits` widening additively (`traits: List<String>` cannot become a list
of objects: `SessionStore.load` discards the WHOLE file on a decode failure, so
every existing save would be dropped). `Compulsion: Collection (pathway)`
carries a **Will-2 self-control roll**, so this is not a cosmetic feature.
Behind it, unchanged: resolving the six `Sense of Duty` instances on the
verbatim-quotation warrant, and then `Secret`, which has none.

