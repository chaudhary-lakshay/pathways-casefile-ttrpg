# Known Issues

Carried forward from the M0/M1 final whole-branch review (2026-07-25). Every item
here is **unreachable today** — `ModifierResolver` has no production callers and no
rulebook content has been extracted yet — but each becomes live the moment the
milestone named against it lands.

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

Two items remain, by explicit decision rather than oversight:

---

### Left unreachable by design: mixed-`SourceKind` group attribution

A `StackGroup` whose live members come from more than one `SourceKind` doesn't
attribute the group's capped total back to each contributing kind separately
— `runLayers`'s per-kind attribution for a Flat/SetTo bucket is only exact
when the bucket is single-kind (see its own comment). Measured: a `cap = 2`
group with `Flat(-3)` from an ADVANTAGE source and `Flat(-3)` from a
SITUATIONAL source correctly applies -2 net, but `Cancel(3, ADVANTAGE)` and
`Cancel(3, SITUATIONAL)` — aimed at the two different contributing kinds —
each separately collect +2 apiece, taking base 12 to 14 instead of leaving it
at 10 (12 - 2).

Left alone because it is provably unreachable today: there are zero
`Modifier(`/`StackGroup(` construction sites anywhere in main source (both
are only ever constructed in test code), so no content or production path can
currently produce a mixed-kind group to trigger this. It becomes relevant the
moment a real content-authoring or Modifier-construction path exists; whoever
adds that path should either keep every `StackGroup`'s live members
single-kind by construction, or revisit `runLayers`'s per-kind attribution to
handle a genuinely mixed-kind bucket correctly.

### `Duration` is declared but not enforced

`Turns(5)` never expires; the Gauntlet case only works because `Decay` clamps
at 0. This is a scope decision: enforcing it needs state `ModifierResolver`
does not have. `Modifier` carries no "activated at turn N" field, and
`resolve()` is a pure function of `(base, mods, target, ctx)` called fresh
each time with no memory of when any given modifier's clock started —
`ctx.turn` alone cannot answer "has N turns elapsed since this modifier
began" without a start-turn to compare it against.

The real reason this is deferred is simply that **no stateful caller exists
yet** to own that bookkeeping — not, as a previous version of this note
claimed, that adding a start-turn would require "mutating `Modifier` (a
model-wide change touching every content file and serialized instance)".
That claim doesn't hold up: there are zero `Modifier(...)` construction sites
in main source today, and no content file holds a serialized `Modifier` at
all (the only files under `core/rules/src/main/resources/content/` are
`advantages.json`, `disadvantages.json`, `skills.json`, and
`thrust_swing.json` — none of them a `Modifier`). Adding a start-turn field
would be a small, blast-radius-free change to a type nothing constructs yet.
The actual blocker is that the state has to live *somewhere* a caller can
supply and update it turn-by-turn, and that caller doesn't exist until a
later milestone wires modifiers into live play.

Until a caller exists that can supply that state, `Turns` and `Minutes`
behave exactly like `Permanent` and `Scene`: never expiring on their own.
`Duration`'s KDoc spells this out, and `ModifierResolverTest` pins the current
(non-)behaviour so a future change cannot silently alter it without the test
(and this note) being updated.
