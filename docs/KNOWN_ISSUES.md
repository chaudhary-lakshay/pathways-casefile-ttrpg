# Known Issues

**There are no open issues.** Every item ever recorded here has been fixed and
is pinned by a test. This file is kept as the audit trail of what was found and
what was done about it, not as a backlog.

---

## FIXED-1 — voluntary SPI spending went below 0, and the book forbids it

**Fixed in slice 6ar** (`a8323a4`), same session it was found. `Pools.canAffordSpi`
floors voluntary spending at zero and all five dialogs withdraw their action;
the damage path is untouched, so spirit body damage still reaches Soul Debt and
still charges for it. Five tests in `PoolsTest`, three verified failing against
the old behaviour, and device-verified in both directions on a mortal caster.
The record of what was wrong follows.

Found 2026-08-08 by a sweep for *prohibitions the app violates* — a defect
class 6aq turned up (a rule printed and CONTRADICTED, as against printed and
unapplied) and that nothing had ever searched for.

**The app charges permanent CoR for an action the book does not allow.**
`BeyonderSession.spend` applies Soul Debt to any SPI reduction, and its KDoc
argues there is deliberately no floor. That reasoning is right for spirit body
DAMAGE and wrong for voluntary spending, because the book separates the two:

| Line | Text | Context |
|---|---|---|
| 2353 / 2726 | "SPI = 0: Spiritual Exhaustion — all Beyonder abilities deactivate; **cannot activate any SPI-cost abilities**" | ch 5 pool table |
| 4254 | "The caster pays SPI equal to the ritual's complexity cost. **SPI cannot be reduced below 0.**" | ch 7, Power Source A |
| 2716 | "You may choose a pool already at 0 (risking death, unconsciousness, or **Soul Debt**)" | ch 5, **Spirit Body Damage** |
| 2727 | "SPI below 0: Soul Debt — +1 CoR per 1 point below 0" | ch 5, under *Threshold Penalties by Pool*, inside that same damage section |

Read together the book is coherent, and the reading is not a stretch: **Soul
Debt is what an ATTACK on the spirit body does to you**, not what overspending
does. Lines 2716 and 2727 both sit inside Spirit Body Damage. Voluntary
spending is capped by what you have — you cannot activate what you cannot pay
for.

**The contrast with FP is explicit and deliberate**, which is the strongest
evidence: line 2848 prints "Every point of FP spent below 0 also costs 1 HP",
so the book knows how to permit overspending and price it. It does that for FP
and does the opposite for SPI. An app that treats the two pools alike erases a
distinction the book draws in two directions at once.

**What is affected.** Every voluntary SPI spend in the app: `RitualDialog`
fuel, `DivinationDialog`'s method cost, `ArtifactDialog`'s per-use and per-turn
costs, and `DetectionDialog`'s techniques. All can currently be performed at
0 SPI, and each buys permanent CoR — `Corruption` is explicit that Soul Debt
CoR "are permanent and require special rituals to remove" (6m), so this is not
a recoverable mistake at the table. Several slices device-verified that
behaviour as correct (6q, 6r, 6aa) because it matched ch 5's table read alone.

**Shape of the fix, not started.**
1. A rules-level affordability question: what a character may voluntarily
   spend is their current SPI, floored at 0.
2. The four dialogs ask it before OFFERING the action, and withdraw the
   button rather than refusing on tap — the precedent is 6x's Broken mystical
   language, which "replaces the target with 'No roll — the book forbids this
   attempt' and withdraws the Roll button entirely, so no SPI is charged".
3. `spend` keeps its floorless behaviour for the DAMAGE path
   (`DamageDialog`), which is the one route the book does send below 0.
4. Ch 7's alternative power sources are the answer for a caster who cannot
   afford a ritual — HP sacrifice at 2 HP per SPI, participants, external
   sacrifice, catalysts. `PowerSources` already models all four (6aa), so the
   book's own escape route is built and reachable.

**Why it was worth its own slice.** It is a reading that reverses
long-standing, device-verified behaviour across four screens, and it deserves
a slice with its own device pass rather than being tacked onto the end of an
audit. The evidence is above so the next session need not re-derive it.

Items 1-4 of the original review (raw-Flat Cancel budgets, summed SetTo, ungrouped
Scale/Decay/Cancel caps, and the mod() test helper decoupling SourceKind from
Layer) were fixed in the M1 hardening Phase C pass and are no longer listed here.

The review's remaining items 1-4 at the time ("`ModTarget` payloads are stringly-
typed", "skills controlled by Will or Per are keyed to IQ", and the two coverage-guard
gaps) were fixed in Phases A and B and are no longer listed here either.

Phase D fixed everything else that was open at the time:
- `Scale` now composes multiple ungrouped, floor-mode effects by product and
  floors once, instead of flooring at every step.
- `ModEffect.Scale` gained a `roundUp` flag so half-penalty effects can round
  either down (Direct Link) or up (active Divination Arts techniques).
- `Scale.den`, `Cancel.upTo`, `Attributes`, and `SecondaryDelta` now validate at
  construction instead of failing (or silently misbehaving) deep inside a
  calculation; `AttributeCosts.costOf` gained an upper bound
  (`MAX_ATTRIBUTE = 30`) so its doubling ladder cannot overflow `Int`.
- `Character` now rejects two `PurchasedSkill` entries sharing a `SkillKey` at
  construction, and `Derive.skillLevels` has direct test coverage.
- `Resolution.applied` now reports `Contribution(ids, delta)` for each step that
  actually changed the value, instead of every target-matching modifier
  including zero-contribution and flag-only ones.
- `tools/extract_pdf.py` and `tools/extract_sheets.py` accept their source paths
  via `PATHWAYS_RULEBOOK_PDF`/`PATHWAYS_SHEETS_DIR` (or `--pdf`/`--src`), and
  `tools/test_tools.py` skips (rather than fails) the tests that need those
  source files when they're absent, so the suite is reproducible off this
  machine and on CI.
- The 23 fixtures now carry per-category POINT BREAKDOWN subtotals
  (Attributes/Advantages/Disadvantages/Skills), and `GoldenSheetTest` checks
  each category across all fixtures that carry one, with a small number of
  named exclusions (see "Fixtures with a genuinely self-contradictory point
  breakdown" below for the two that remain, and the fix wave entry below for
  the two that were repaired rather than excluded).

A follow-up fix wave (this pass) closed everything the final whole-branch review
flagged as "do not merge as-is", except one item left deliberately open (see
below) and one left unreachable by explicit scope decision:

---

### Fixed: a `Scale` after a `Cancel` could turn a penalty into a bonus

`Scale`'s ledger contribution (`scaled - before`) is a function of the running
`value` at the point the `Scale` runs. That differs between the shadow pass
(which measures `Cancel` budgets with `Cancel` itself disabled) and the real
pass (which may have already restored an earlier penalty via `Cancel` before
the `Scale` runs) whenever a `Scale` sits downstream of a `Cancel` targeting
its `SourceKind`. Feeding that mismatched delta into the penalty ledger let a
downstream `Cancel` offset a delta that was never actually applied in the real
pass — in the worst case, turning a set of pure penalties into a net bonus
(measured: base -4 with a Flat(-10)/Cancel(10)/Scale(2,1)/Cancel(10) shape
landed on **+2** instead of any penalty at all).

Fixed by no longer feeding `Scale`'s delta into the penalty ledger at all
(`ModifierResolver.applyScale`, both branches). The consequence is a explicit,
documented trade-off rather than a silent gap: **a Scale-delivered penalty is
not cancellable** — a `Cancel` aimed at a `SourceKind` whose only penalty
arrived via `Scale` is simply a no-op. `ModEffect.Cancel`'s KDoc states this
directly (it previously claimed Scale-delivered penalties WERE offset the same
as Flat/Decay, which was never actually true once a Cancel ran upstream of the
Scale). `ModifierResolverTest` pins all three of the reviewer's measured
scenarios: base 20 with a fully-cancelled Flat(-10) followed by Scale(1,2)
now correctly lands on 10 (not the pre-fix 15, and not a naively "fully
invariant" 20 — the halving is real and not cancellable); base -4 with
Scale(2,1) lands on -8 (not the pre-fix +2); and a Scale(1,3)/Scale(3,1)
chain that composes to net ×1 is unaffected by an unrelated no-op Cancel,
landing on 7.

### Fixed: an ungrouped `SetTo` erased unrelated ungrouped `Flat`s

`ModifierResolver.runLayers` pooled every Flat/SetTo effect in a layer by
`it.group`, including `group == null`. Kotlin's `groupBy` treats `null` as one
ordinary key, so *every* ungrouped Flat and SetTo modifier in a layer landed in
the same bucket regardless of whether they had anything to do with each other
— and a live SetTo in that bucket silently overrode (deleted) every unrelated
ungrouped Flat sharing the layer, not just the ones it was actually meant to
supersede. Measured: an unrelated ungrouped `Flat(+1)` alongside an unrelated
ungrouped `SetTo(+2)` gave 14 instead of 15 (the Flat was dropped entirely,
including from `Resolution.applied` — the audit trail hid the erasure too).

Fixed by restricting the joint-pool override to an explicit, non-null
`StackGroup`. When `group == null`, Flat and SetTo now apply independently:
Flat's simply sum, and multiple *live* ungrouped SetTo's still override each
other (the highest wins — this is the pre-existing KNOWN_ISSUES #2 "two live
SetTo" case, which deliberately names no group) rather than sum, but a SetTo
no longer reaches across and erases an unrelated ungrouped Flat. A modifier a
SetTo actually names via `Modifier.supersedes` was already removed from
`live` upstream in `resolve()` before any of this runs, so that mechanism is
unaffected. `ModifierResolverTest` and `ModEffect.SetTo`'s KDoc both cover the
restricted semantics; the fixed unrelated-Flat case now correctly appears in
`Resolution.applied`.

### Fixed: `Fortune Teller` and `Street Acrobat` fixtures carried wrong trait costs

`tools/extract_sheets.py`'s `traits()` always read the `Cost` column of an
ADVANTAGES/DISADVANTAGES block. That's correct for 21 of 23 sheets, but two
sheets have the real cost typed into the `Roll` column instead, leaving `Cost`
wrong wherever both are populated:

| Sheet | Block | Trait | Cost column (wrong) | Roll column (right) |
|---|---|---|---|---|
| Fortune Teller | Advantages | Charisma 2 | 1 | 10 |
| Fortune Teller | Advantages | Wealth: Comfortable | 10 | 10 (unaffected) |
| Street Acrobat | Disadvantages | Poverty: Struggling | 5 | 10 |
| Street Acrobat | Disadvantages | Overconfidence | 10 | 5 |
| Street Acrobat | Disadvantages | Paranoia: Bodies of water | 10 | 5 |

Fixed by disambiguating with a checksum: when any row in a block has both
`Roll` and `Cost` populated, `traits()` now sums each column and prefers
whichever sum reconciles with that block's own printed POINT BREAKDOWN
subtotal (Fortune Teller Advantages: Cost sums to 11, Roll sums to 20, which
matches "Advantages: 20 pts" — use Roll. Street Acrobat Disadvantages: Cost
sums to 35, Roll sums to 30, which matches "Disadvantages: 30 pts" — use
Roll), falling back to `Cost` when there's no subtotal to check against or
neither column reconciles. It prints a notice naming any sheet where this
changes the answer, so the disambiguation is never silent. Regenerating all 23
fixtures changed only these two files; Street Acrobat's single-row, Cost-only
ADVANTAGES block (Flexibility) was correctly left untouched, since Roll is
blank there. `GoldenSheetTest`'s Fortune Teller advantages exclusion and
Street Acrobat disadvantages exclusion are both gone — both now pass on the
engine's own terms. This also shifted (not removed) two pinned
`ContentLoaderTest` fixture-vs-book cost conflicts: Fortune Teller's
"Charisma 2" fixtureCost moved from 1 to 10 (still disagrees with the book's
flat per-level rate of 5 — this comparison never multiplies by level); Street
Acrobat's "Overconfidence" conflict disappeared (it now agrees with the book,
like every other fixture already did) and a new "Paranoia: Bodies of water"
conflict appeared (5 vs. book's 10 — it previously agreed only by
coincidence, since 10 happened to equal the book value before the fix).

### Fixed: three "engine" subtotal assertions were actually fixture-vs-fixture

`GoldenSheetTest`'s advantages/disadvantages/skills subtotal checks compare
`f.published.*Subtotal` against `f.<list>.sumOf { it.cost }` (or `.points`) —
both sides come from the same fixture, so these never touch the engine. Only
the attributes check calls into the engine (`AttributeCosts.totalCost`). This
doc previously claimed "the engine's computed cost is asserted against each
category," which was only true for Attributes. Fixed by renaming the three
tests to describe what they actually check (`each sheet's own advantage/
disadvantage/skill item list sums to its published subtotal`) rather than
building new engine-side cost computation for advantages, disadvantages, and
skills, which would be new functionality, not a fix to existing behavior —
out of scope for this pass. A genuine engine-side check for those three
categories (e.g., cross-referencing each item against its `ContentLoader`
book entry, the way `ContentLoaderTest` already does for cost-conflict
tracking) remains open as future work, not a defect.

### Fixed: nothing reconciled fixture skill `attr`/`difficulty` against the book

`ContentLoaderTest` already reconciled fixture skill *names* against
`skills.json` (with a documented, exact set of book gaps and name conflicts),
but nothing checked whether a resolved skill's `attr`/`difficulty` fields
actually agreed with the book entry it resolved to. A sheet-side typo here
would silently feed `SkillLadder` the wrong controlling attribute or
difficulty rung. Fixed by pinning an exact conflict set, same pattern as the
trait cost conflicts: six skills disagree across seven fixture instances —
Fishing (fixture IQ vs. book Per), Lockpicking (fixture DX vs. book IQ), Law:
Police (fixture Average vs. book Hard, in both Beat Cop and Nighthawk
Recruit), Literature (fixture Average vs. book Hard, in Occult Scholar only —
Academic Researcher's and University Librarian's Literature entries already
agree), Cooking (fixture Easy vs. book Average), and Camouflage (fixture
Average vs. book Easy). These are sheet-side leftovers, not extractor drops;
independently verified against the book, no additional unresolved skill names
were found beyond the four genuinely-absent skills (Meditation, Forced Entry,
Juggling, Gesture) and the three name conflicts (Mathematics, History:
Occult, Engineer: Steam) `ContentLoaderTest` already documents for name
resolution.

### Fixed: three cheap validation/documentation gaps

- `StackGroup(name, cap)` had no `require(cap >= 0)` — the one construction
  boundary the earlier validation sweep missed. A negative cap died inside
  `coerceIn` with the opaque "Cannot coerce value to an empty range" instead
  of a meaningful message at the actual construction boundary. Fixed.
- `Character.spi` was unbounded while `Attributes` and `SecondaryDelta` both
  already validate at construction. Gave it the same kind of generous,
  malformed-input-only bound (`MAX_SPI = 1000`) — not a rulebook figure, since
  the book states no numeric ceiling for SPI.
- `ContentLoader.thrustSwing()` built its map with `associate { it.st to ... }`,
  so a duplicate ST row in `thrust_swing.json` would silently collapse to one
  entry instead of failing loudly — the same defect class `Character` already
  guards against for duplicate `PurchasedSkill` entries. Now a hard failure at
  load time. There are no duplicates in the committed content today.
- `Character`'s class KDoc said "purchased state only... table state arrives
  at M3 and M4," which `spi` (current, table-state SPI, present since before
  this fix wave) already contradicted. Corrected to describe `spi` as the one
  piece of table state that already needs to exist this early, with
  everything else (current HP/FP, conditions, situational modifiers, the rest
  of Beyonder state) still arriving at M3/M4.

Both items that this doc previously left open have now been fixed as well:

---

### Fixed: mixed-`SourceKind` group attribution over-credited every kind

A `StackGroup` whose live members came from more than one `SourceKind` gave
each kind the group's full capped total instead of that kind's share of it.
`runLayers` capped each kind's own raw sum independently, so the parts could
sum to far more than the group had actually applied. Measured: a `cap = 2`
group with `Flat(-3)` from an ADVANTAGE source and `Flat(-3)` from a
SITUATIONAL source correctly applies **-2** net, but recorded **-2 against
each kind** — so `Cancel(3, ADVANTAGE)` and `Cancel(3, SITUATIONAL)` each
collected +2, taking base 12 to **14**: strictly above the base, produced by
nothing but penalties.

Fixed by splitting the applied total across contributing kinds in proportion
to each kind's raw share, computed as prefix differences
(`floorDiv(applied * prefixRaw, raw)`) so the parts sum to **exactly**
`applied` for any sign and any cap. Full cancellation of a mixed-kind group
now restores the base (12) and can never exceed it. `ModifierResolverTest`
pins both halves of the scenario — the -2 net application and the
cancel-back-to-12 — and the test was verified to fail (only that test, at the
cancel assertion) against the previous attribution logic before the fix
landed.

This closes the last correctness gap in `ModifierResolver`'s Cancel budget.
The earlier caveat in `runLayers`'s comment — that per-kind attribution was
"exact when the bucket is single-kind" — no longer applies: it is now exact
for any mix of kinds.

### Fixed: `Duration` is now enforced for modifiers that carry a start turn

`Modifier` gained a nullable `startTurn: Int?` (defaulting to `null`), and
`ModifierResolver.resolve` now filters out any modifier whose clock has run
out before `supersedes` and condition filtering run. The rules:

| Duration | Expires? |
|---|---|
| `Permanent` | never — by definition |
| `Scene` | never on a turn count; a scene ends when the caller drops the modifier |
| `Turns(n)` | when `ctx.turn - startTurn >= n` |
| `Minutes(n)` | when `ctx.turn - startTurn >= n * 60` (one turn is one second) |

A modifier with `startTurn == null` is not on a clock and never expires, so
every modifier written before duration tracking existed keeps behaving
exactly as it did. That default is what makes this a non-breaking change
despite `Duration` having been declared since M0.

Expiry is applied in the same filter as `condition`, which means an expired
modifier also **cannot supersede** the modifier it names — the same rule
already proven for a condition that does not hold, and pinned by its own
test. `ModifierResolverTest` covers all four `Duration` cases, the no-start-
turn default, the exact turn boundaries in both directions, and the
supersedes interaction.

The stateful caller that owns advancing `ctx.turn` and stamping `startTurn`
still arrives at a later milestone — but the resolver no longer silently
ignores a duration once that caller exists.
