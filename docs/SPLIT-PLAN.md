# Split plan: eleven files over the 800-line cap

Written 2026-10-10 against `123d68c`. Every line count in the tables below
the status is from that commit; re-measure before starting a step.

## Status (2026-10-10)

**Steps 0-4 are done**, each a commit with 1950 Kotlin tests green from fresh
XML (`b245394`, `f4d20f3`, `c36e90a`, `7192fb5`; step 0 is `9cc865d`,
`OVER_CAP` in `tools/test_tools.py`). **Step 5 is done** in three commits
(`c6ae498` grants, `ee1a39e` names, then clauses), each RED-first (the
rewritten tests failed to compile against the old tree) and 1950 Kotlin
green from fresh XML; Python 362. `TraitEffects.kt` is 1191 lines, beside
`TraitClauses` 527, `TraitNames` 374 and `TraitGrants` 181, so it stays in
`OVER_CAP`. The diff review (sonnet, `kotlin-reviewer.md`, `2ea72df..c7a7814`)
found no behaviour change and no init-order read; its one LOW, an import
the move left unused, is removed.

**Step 6 is done**, every move verbatim against a line-multiset diff:
`TraitEffectsTest.kt` into five classes by the object each test exercises
(`ee6423c`, 107 tests); `test_tools.py` into eleven `test_tools_*.py` and
`testsupport.py` (`c29ce61`, the same 362 collected names, run as
`py -m pytest tools -q`); `extract_content.py`'s self-control reading into
`content_self_control.py`, which reads nothing else of it (589 + 358).
The diff review (sonnet, `4d0ce5e..b35ab36`) said SHIP; its one
stale count is gone.

**Step 7 is done**: `SheetScreen` keeps its state and derivations, and the
hub, the three tabs and the dialog call sites moved to `SheetActions.kt`
119, `SheetTabs.kt` 271 and `SheetDialogs.kt` 568; `MainActivity.kt` is
483. Every old line is accounted for by a line-multiset diff. 1950
Kotlin and 362 Python green. Device-verified on the SM-T570 (save
`cmp`-identical after): all eleven hub dialogs open and dismiss; a
sheet row, a potion row and the untrained picker each open the roll
dialog at the printed level (Research 13, Ritualistic Magic 13, Guns
(Pistol) untrained DX-4 at 5); Overconfidence's row and dialog both
read 3d6 ≤ 12; and Fright plus Concentrate tapped in one monkey frame
open both, stacked, closing one per BACK, as the eleven flags did.
**Not verified on device**: the shock latch and Clear, and a spend
moving a pool, because each writes the save. The diff review (sonnet,
`ca33f90..ea5665d`) said SHIP; its one LOW, five imports named only in
comments, is removed.

**Step 8a is done** (`c59e8c6`): `RitualDialog.kt` 748, beside
`RitualControls.kt` 395, and off `OVER_CAP`. The line-multiset diff
differs only in the planned assignment-to-callback edits. 1950 Kotlin
and 362 Python green from fresh XML. Design review and diff review
(sonnet, `cdbab7f..c59e8c6`) both said SHIP. Device-verified on the
SM-T570 with a Beat Cop on Fool 9 (save `cmp`-identical after):
- One chip in each moved row moved the printed target and the breakdown
  together, `3d6 ≤ 12` to `≤ 18` (+1 extended, +2 assisting, +4
  sacrifice, +1 catalyst, -2 suspended), costing 2 SPI and 2 HP.
- Occultism took 12 to 13, and Accented took it to 10.
- A one-frame double tap flipped twice on Suspended and on Occultism. A
  single monkey tap deselected Occultism, so the double tap did land.
- Roll double-tapped in one frame charged once.
- A +1 push was charged on Close, not before.

**Not verified on device**: Ask and the mode resets, whose code did not
move.

**Steps 8b and 8c are retired, not done.** Their reason was the cap, and
it expired at step 4: measured at `55e2d01`, `DefenseDialog.kt` is 597
lines and `AttackDialog.kt` 581. A Kind C cut is the riskiest split this
plan has, where 6eg, 6ei and 6gb found their defects, and with no cap to
clear it buys nothing a line count can show. All three dialogs, Ritual
included, still break section 7's 50-line function rule. That is a
different goal, and needs its own decision. Two files remain on
`OVER_CAP`, and neither has a step: `TraitEffects.kt` 1190 and
`BeyonderSessionTest.kt` 863.

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

Two files are still over the cap: `TraitEffects.kt` 1190 and
`BeyonderSessionTest.kt` 863.

## Step 5: the decomposition (2026-10-10, against `f472f3f`)

Reviewed before the code (sonnet, `kotlin-reviewer.md`): CHANGE, three
gaps, each folded in below; no init-order hazard and no structural
objection.

Three objects, one commit each, smallest first: `TraitGrants`, then
`TraitNames`, then `TraitClauses`. Line spans are `TraitEffects.kt` at
`f472f3f` (2187 lines).

| Object | Spans | Public members | Main / test call sites |
|---|---|---|---|
| `TraitGrants` | 1893-1945, 1947-2045 | `grantedTraitTable`, `refusedGrants`, `grantedTraits`, `withGrantedTraits`, `grantedLevelsIn` | 7 / 21 |
| `TraitNames` | 810-1174 | `levelOf`, `catalogueName`, `pricedVariants`, `PLACEHOLDER`, `displayName` | 14 / 61 |
| `TraitClauses` | 1429-1883 (less 1593-1594, 1697, 1825-1826, 1846-1850), 2047-2099 | the fright, Will, concentration, social, attack, skill and sense clause functions, `clauseBonus`, `refusesAim`, `AIM_REFUSAL` (20), and a new `attackClauseNames()` | 21 / 83 |

Counts are textual `TraitEffects.<member>` matches over `.kt`, so KDoc
links count with code. No `TraitEffects::` reference exists anywhere, and
the only member imports are `TraitRefusals.kt`'s, all of constants.

**Where the shared pieces live.**

- **Trait-name constants stay in `TraitEffects`**: `THINNED_VEIL`,
  `CHRONIC_INSOMNIA`, `RELUCTANT_KILLER`, the `ACUTE_*` names,
  `GREATER_ACUTE_VISION`, `INCISIVE_VISION`. Grants and clauses both name
  them, and `TraitRefusals` imports them from there today. The three
  private ones (`ACUTE_TASTE_SMELL`, `ACUTE_TOUCH`, `ACUTE_SMELL`) become
  `internal`. `EIDETIC_MEMORY` and `advantages(vararg)` move with grants,
  their only reader.
- **`catalogue()` moves to `TraitNames` as `internal`**, with `rowNames`,
  `squashed`, `quoted`, `SHEET_SEPARATOR` and the four `*Row` resolvers,
  which no other group reads. `reported`, `held` and `unrowed` keep calling
  it, once each, from function bodies.
- **`bonus()` and `categoryOf()` become `internal` in `TraitEffects`**:
  `attackModifiers` and `skillRollClauses` read them. `maxLevelsFor` is
  already public; `levelled` reads it.
- **`GRANTED_ONLY_SENSES` moves to `TraitClauses` as `internal`**, beside
  `SENSE_CLAUSES`; `unrowed` reads it. `SENSE_ROW_REST` and `SENSE_REST`
  stay in `TraitEffects`, read by `unrowed` alone.
- **`known()` reads the private `ATTACK_CLAUSES`** (2123), so
  `TraitClauses` gains `attackClauseNames()`, the shape its five
  siblings already have.
- **No helper is copied.** Each has one home.

**Direction.** Every cross-object reference is inside a function body:
`known()` reads the clause name sets, `modifiers` calls
`skillRollClauses`, and the rest are `levelOf`/`catalogueName` calls. No
object's `val` initializer reads another object's non-`const` member, so
no JVM static-init order can see a half-built table. The moved tables are
built from `TraitClause`/`Trait` constructors and `const` names, which the
compiler inlines.

**How call sites change.** Inside the four objects, member imports
(`import com.pathways.rules.TraitNames.catalogueName`), the precedent
`TraitRefusals` set, so `TraitEffects`' 45 internal `levelOf` and
`catalogueName` calls are not rewritten. Outside, a textual rewrite of
`TraitEffects.<member>\b` over `.kt` files, which reaches KDoc links and
constants as well as calls. Unqualified KDoc links inside moved code that
name a member left behind (`[reported]`, `[known]`, `[majorWoundHt]`)
are qualified with `TraitEffects.`, as step 3 did, and three links
to the moved tables are requalified the other way (`TraitRefusals.kt` 70
and 104, `TraitEffects.kt` ~801). The rewrite runs over an explicit list
of the moved object's public names, taken from its declarations, never a
glob: `frightClauses` prefixes `frightClauseNames`. Section 3 of
`CLAUDE.md` is updated in the same commits.

**Size.** About 1030 lines leave, so `TraitEffects.kt` lands near 1160,
not the ~850 the table below estimated. It stays in `OVER_CAP`.

**Proof per commit.** Kotlin test counts from fresh XML (all three
modules, `testDebugUnitTest/` included) equal before and after; the
ratchet passes. `known()`'s body changes only by qualification. The
census tests that read it (now in `TraitReportedTest`, with 6es's
complement) assert subsets, not the whole set, so they check
reach, not equality.

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

## Step 7: the decomposition (2026-10-10, against `f9ee692`)

Reviewed before the code (sonnet, `kotlin-reviewer.md`): CHANGE. A single
nullable dialog would have dropped a second tap, so it is a set; the
`shocked` read and the live `session` reads are stated rules below. Its
other notes: keep `SheetDialogs` unconditional, pass `shocked` with a
callback, and a `SheetPlay` holder is a later cleanup, not this step.

`SheetScreen` is `MainActivity.kt` 179-1237: the derived values (186-420),
a `Scaffold` whose `LazyColumn` holds the action hub (500-580) and three
tabs (585-796), and the dialog call sites (800-1236).

**What does not move: the state, and every value more than one part
reads.** `tab`, `rolling`, `controlling`, `pickingUntrained` and `shocked`
stay `remember(sheet)` in `SheetScreen`, and so do the derived values a
tab or the hub reads (`vitals`, `traits`, `selfControl`, `standing`,
`hpNow`, `fpNow`, `shockOnAttribute`, `hurtOnAttribute`, the two caster
levels, `spiritVisionForm`, the trait lists). Nothing below changes a
`remember` key, and the values written as live expressions on purpose
(`fpNow`, `spiAvailable`; 6dc, a design review in M4) stay live.

**One state change: eleven flags become one set.** `frightening`,
`focusing`, `casting`, `defending`, `attacking`, `damaging`, `divining`,
`supplicating`, `handling`, `sensing` and `looking` are each set by one
hub tile and cleared by their own dialog's dismiss (two writers each,
checked). They become `var open by remember(sheet) {
mutableStateOf(emptySet<SheetDialog>()) }`: a tile adds its entry, a
dismiss removes its own. A SET, not one nullable value, because the
design review showed two tiles tapped milliseconds apart (6ei) open two
dialogs today, and a single value would drop the first tap. Each `if`
keeps its gate (`ritualLevel != null`, `divinationLevel != null`,
`spiritVisionForm != null`) beside its membership test.

**Three new files, each a verbatim move of its block.**

| File | What | Reads | Writes, by callback |
|---|---|---|---|
| `SheetActions.kt` | the hub, as `SheetActions(drunk, ritualLevel, divinationLevel, spiritVisionForm, onPathways, onBuild, onOpen: (SheetDialog) -> Unit)` | its parameters | `open` |
| `SheetTabs.kt` | `LazyListScope.vitalsTab`, `.traitsTab`, `.skillsTab`: extension functions, not composables, each emitting the items its tab emits today in today's order | its parameters | `shocked = false`, `controlling`, `rolling`, `pickingUntrained` |
| `SheetDialogs.kt` | every dialog call site, as one `@Composable SheetDialogs(...)` | its parameters, plus `session`'s observable fields read live | `open = null`, `rolling`, `controlling`, `pickingUntrained`, `shocked = true` |

**Values read by the dialogs alone move into `SheetDialogs`, beside their
only reader**: `conditionsFor` (six call sites, all dialogs), `burn`,
`willClauses`, `concentrationClauses`, `untrained`, `mastery`,
`shockOnSkill` and `spiTrait`, each with its `remember` keys unchanged.
A grep of 179-1237 found no reader of any of them outside 800-1236 and
their own definitions.

**Two reads change place, and the rule that keeps them right.**
`conditionsFor` reads `shocked` from `SheetDialogs`' parameter rather than
the delegate, so it is the value as drawn; `SheetScreen` passes a new one
when the latch moves, so no roll differs. And every read of `session`'s
observable fields that a dialog parameter takes (`spiSpent`, `cor`,
`edits`) stays INSIDE `SheetDialogs`' body, never hoisted into
`SheetScreen` and passed down: under strong skipping a hoisted read of a
`session` the screen did not re-read would go stale. `SheetDialogs` is
called unconditionally, as the derivations it now holds were.

**The tabs.** Today every tab's `items(...)` is emitted on every tab with
an empty list, so the hidden tabs contribute zero items. The extension
functions are called inside `when (tab)`, so a hidden tab emits nothing,
which is the same visible sequence at the same indices (the header item
is index 0 either way, and no item has a key).

**Why the handlers are safe.** 6ei/6gb's rule is that a handler acts on
the picture its composition drew. Each dialog keeps its own parameters
and handlers verbatim; what changes is that `SheetDialogs` is a
composition boundary between `SheetScreen` and the dialogs. Its
parameters are the values `SheetScreen` drew, and its own reads of
`session` (`spiSpent`, `cor`, `edits`) make it recompose when those
change, as `SheetScreen` does today. Under strong skipping (Kotlin 2.2's
Compose compiler) a call with the same instances skips, which is correct
because nothing it was handed changed.

**Size.** `MainActivity.kt` about 500 lines, `SheetDialogs.kt` about 480,
`SheetTabs.kt` about 230, `SheetActions.kt` about 100.

**Proof.** Kotlin tests from fresh XML, same 1950. No composable is
unit-tested (section 6), so the behaviour proof is a device pass on the
SM-T570, save backed up first: every hub tile opens its dialog and
dismisses; a skill row, a bought and a granted row and the untrained
picker each open the roll dialog at the printed level; a self-control
row opens its dialog at its printed target; Damage's Major Wound latches
shock and the Vitals tab's Clear removes it; a ritual, a divination and
an artifact spend move the pools on the Vitals tab.

## Step 8a: the decomposition of `RitualDialog` (2026-10-10, against `07396b1`)

`RitualDialog.kt` is 1042 lines: `RITUAL_SKILL` and three private helpers
(46-109), then one composable (129-1042) whose state is 165-336 and whose
`AlertDialog` text slot is 346-1040. The helpers alone are 65 lines, so the
composable itself has to be cut.

**Move views, never reads.** Every `remember` stays in `RitualDialog` with
its keys unchanged: one moved into a gated child would change its lifetime
(`heldLinkPick` would reset whenever `if (mode.usesTargetLink)` left the
tree). So do `target`, `cost`, `spiOwed`, `hpOwed`, the two picks, both
effects, `settle` and `finish`: `targetFor` throws in composition, and these
are what the Roll handler and the breakdown read.

**A child receives a setter only for an absolute value.** `{ pacing =
option }` becomes the child's `onPacing(option)`. Every handler that reads
state does so in a lambda DEFINED in `RitualDialog` and handed down whole,
because a child computing `onSuspended(!suspended)` from its parameter would
make two taps in one frame both write `true` where today they flip twice --
a behaviour change no line-multiset diff shows. Moved this way:
`suspended = !suspended` (`onToggleSuspended`) and the support toggle
`support - name / + name` (`onToggleSupport(name)`). Every guarded handler
stays where it is, unmoved: Roll's `outcome != null`, Ask's `answer !=
null`, the push's `outcome ?: return` and `burned + points`, the link's
re-tap-clears, Flourish's toggle and the two `CellValueChips` picks.

**One new file, `RitualControls.kt`, each block a verbatim move.**

| Declaration | From | Reads | Writes, by callback |
|---|---|---|---|
| `spiChoices`, `hpChoices`, `CellValueChips` | 49-109 | their parameters | -- (`private` → `internal` for `CellValueChips`, which stays called from `RitualDialog`) |
| `RitualPowerSourceChips` | 392-589: Complexity, Fuel, HP, Pacing, Assistants, External sacrifice + Sentient beings, Catalyst, Suspended | `complexity`, `spiChoice`, `cost`, `hpSacrificed`, `pacing`, `assistants`, `sacrifice`, `sentientBeings`, `catalyst`, `suspended` | `onComplexity`, `onSpiChoice`, `onHpSacrificed`, `onPacing`, `onAssistants`, `onSacrifice`, `onSentientBeings`, `onCatalyst` (absolute); `onToggleSuspended` (defined in the parent) |
| `RitualSupportAndLanguage` | 766-839: Supporting skills, Mystical language, the bought/granted note | `ownedSupportSkills`, `support`, `language`, `boughtLanguages`, `grantedLanguages` | `onLanguage` (absolute); `onToggleSupport` (defined in the parent) |

Inside each moved body the only edit is the assignment: `complexity =
option` becomes `onComplexity(option)`, and so on, one per chip. Both
children are plain `Column`-less emitters called inside the existing
scrolling `Column`, as `CellValueChips` is today, so the layout is the
same tree.

`RITUAL_SKILL` stays: three other files read it. The result branch
(984-1038) and the breakdown string (862-920) do not move. The push
handler is the most sensitive lambda in the file and the breakdown
would take 23 parameters; with the two blocks above the file is about
730 lines, under the cap. `RitualDialog.kt` leaves `OVER_CAP` in the
same commit.

**Proof.** The line-multiset diff, old file against both new ones, apart
from `import`, the `private`/`internal` change, the assignments turned
into callbacks and the two call sites. Kotlin 1950 and Python 362 from
fresh XML. Device pass on the SM-T570, `sessions.json` backed up and
`cmp`-restored, every case a spend because spends are this dialog's
point: each moved chip row moves the printed `3d6 ≤` and the breakdown
together; Suspended double-tapped in one monkey frame ends where it
started; a support chip toggles on and off; a mode switch still resets
preparation, link and flourish; Roll double-tapped charges once; Close
charges a critical row's CoR once; Ask double-tapped rolls once.

## Step 9: the decomposition of `TraitEffects.kt` (2026-10-10, against `cb38548`)

Reviewed before the code (sonnet, `kotlin-reviewer.md`): SHIP. Its notes
are folded in below:
- `ETHER_BODY_DAMAGE` (306) is a trait-name constant, so it stays.
- `BespokeBonus` goes from `private` to `internal`.
- `stunRecoveryBonus`'s clause names a condition, not an attribute. It is
  in the object because the one roll this app applies it to is HT.
- The "deliberately NOT a general attribute-roll accessor" KDocs stay
  verbatim. They mean that each function covers one attribute, and that
  stays true.
- The ~735 estimate ignored blank lines, so the file is recounted after
  the move.

The owner chose to split `TraitEffects.kt` (1190 lines). The option as
put to them named three objects: `held, reported, known, UNREAD`, then
luck and acting, then the HT rolls. This plan differs in two places.

- **`known()` stays in `TraitEffects`.** It is the census of every reader
  in this object. The load-time instruction "a new production reader adds
  it to `TraitEffects.known()`" stays true word for word.
- **Two objects, not three.** Luck and acting are 73 lines that share
  nothing. The bare attribute-roll bonuses are a theme of their own, and
  they are what brings the file under the cap.

Kind B, as in step 5: one commit per object, the smaller first. Every
call site is rewritten, with no facade left behind. Each commit is
RED-first: the rewritten tests fail to compile against the old tree.

| Object | Spans at `cb38548` | Members |
|---|---|---|
| `TraitReport`, with top-level `TraitNote` (31-32) beside it in `TraitReport.kt` | 699-706, 926-1076, 1094-1102 | `reported`, `UNREAD`, `ROLLED_ONLY`, `HeldTrait`, `held`, `corruptionNotes`; private `unrowed`, `SENSE_ROW_REST`, `SENSE_REST` |
| `TraitAttributeRolls`, with top-level `MajorWoundHt` (34-47) beside it | 225-231, 281-304, 307-357, 499-532, 575-590, 617-641, 737-823, 908-924 | `alertnessBonus`, `htRollNames`, `spiRollNames`, `spiRollBonus`, `htRollBonus`, `majorWoundHt`, `stunRecoveryHt`, `knockdownBonus`, `deathRollBonus`, `consciousnessRollBonus`, `stunRecoveryBonus`; `HT_ROLL_TRAITS` and `SPI_ROLL_TRAITS` become `internal` |

**The second object's theme is a roll of an attribute itself, never of a
skill.** Every member answers that question. Alertness answers it for
Per, Ether Body Damage for SPI, and the rest for HT, including the HP
table's death and consciousness rolls, the Major Wound's knockdown and
stun recovery. Three bare-roll members stay in `TraitEffects`, because
none of them is an attribute roll:
- `defenseBonus` is an active defence.
- `darknessReduction` is a darkness penalty.
- `defaultBonus` is added to a skill level.

`BespokeBonus` stays in `TraitEffects` too, as `internal`. Its KDoc is
about `known()`, and it holds `DEFAULT`. The three moved functions read
it by name.

**Who reads what across the new boundary.**
- `TraitEffects.modifiers` reads `HT_ROLL_TRAITS` and `SPI_ROLL_TRAITS`.
- `known()` reads both, plus `BespokeBonus.entries`.
- `TraitReport.reported` calls `TraitEffects.known()`.
- The trait-name constants (`FIT`, `UNFIT`, `ETHER_BODY_DAMAGE`,
  `HARD_TO_KILL`, `ACUTE_SMELL`, ...) stay in `TraitEffects`. The moved
  code imports them.

**Init order.** The moved non-`const` `val`s are `HT_ROLL_TRAITS`,
`SPI_ROLL_TRAITS` and `SENSE_ROW_REST`, and each reads only `const`
values, which are inlined. The non-`const` `val`s that stay are
`BALANCE_SKILLS`, `SKILL_BONUSES` and `CATEGORY_BONUSES`. They read
nothing that moves. Neither object's initialiser touches the other.

**Every reference form, counted at `cb38548`.**
- `TraitEffects.<member>`: `reported` 7 main / 43 test, `held` 3 / 12,
  `UNREAD` 0 / 8, `ROLLED_ONLY` 0 / 1, `HeldTrait` 2 / 0,
  `corruptionNotes` 3 / 4, `majorWoundHt` 4 / 14, `htRollBonus` 5 / 2,
  `knockdownBonus` 2 / 6, `stunRecoveryBonus` 4 / 0, `stunRecoveryHt` 1 / 3,
  `htRollNames` 0 / 3, `deathRollBonus` 1 / 0,
  `consciousnessRollBonus` 1 / 0, `alertnessBonus` 4 / 3, `spiRollBonus`
  1 / 3, `spiRollNames` 0 / 2.
- Member imports: one, `HeldTrait` in `SheetTabs.kt`.
- Callable references and star imports: none.
- KDoc links of the form `[TraitEffects.x]` to a moving member: 19.
- Unqualified KDoc links inside the moved spans, such as `[known]`,
  `[UNAPPLIED]` and `[modifiers]`, would dangle in silence. Each is
  qualified in the commit that moves it.
- Readers by path: `tools/test_tools_regeneration.py` names the file in
  `OVER_CAP`, and nothing else does. Commit 2 removes it there.
- `CLAUDE.md`:
  - line 51, the load-time `TraitEffects.UNREAD`;
  - section 3's `held`, `UNREAD`, `ROLLED_ONLY`, `corruptionNotes`,
    `consciousnessRollBonus`, `stunRecoveryHt`, `stunRecoveryBonus`,
    `deathRollBonus` and `alertnessBonus`.

  Each is rewritten in the commit that moves the member.

**Exit criterion, per commit.** A grep for `TraitEffects\.` followed by
any moved name, over `*.kt`, `CLAUDE.md` and `docs/HOW-TO-PLAY.md`,
returns nothing.

**Proof.**
- `known()` is snapshotted as a sorted list before the first commit and
  diffed after each one. The census tests compare it against sets built
  from the same collections, so an entry lost in the move could pass
  them.
- 1950 Kotlin and 362 Python tests, from fresh XML.
- `TraitEffectsTest.kt` (780) is re-measured after its rewrites.
- `OVER_CAP` drops `TraitEffects.kt` in commit 2, at about 735 lines.
- No device pass is needed: no composable changes, and the app's call
  sites change only in their qualifier.

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
| 8 | `RitualDialog`, then `DefenseDialog`, then `AttackDialog` | C | one slice each; 8b and 8c retired (Status) |

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
