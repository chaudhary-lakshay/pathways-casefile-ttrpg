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

Phase D fixed everything else that was still open:
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
  (Attributes/Advantages/Disadvantages/Skills), and `GoldenSheetTest` asserts
  the engine's computed cost against each category across all fixtures that
  carry one. Attributes and Skills agree everywhere; Advantages disagrees for
  Monastery Scholar and Fortune Teller, and Disadvantages disagrees for Street
  Acrobat and University Librarian — each sheet's own item list does not sum to
  its own printed subtotal. These four are excluded by name in
  `GoldenSheetTest`, matching the book (the item list) rather than the sheet's
  arithmetic, exactly as the pre-existing Docks Bouncer/Factory Foreman/Street
  Acrobat HP/FP exclusions already do.

One item remains open by deliberate choice, not oversight:

---

### `Duration` is declared but not enforced

`Turns(5)` never expires; the Gauntlet case only works because `Decay` clamps at
0. This is a scope decision: enforcing it needs state `ModifierResolver` does
not have. `Modifier` carries no "activated at turn N" field, and `resolve()` is
a pure function of `(base, mods, target, ctx)` called fresh each time with no
memory of when any given modifier's clock started — `ctx.turn` alone cannot
answer "has N turns elapsed since this modifier began" without a start-turn to
compare it against. Adding that would mean either mutating `Modifier` (a
model-wide change touching every content file and serialized instance) or
introducing stateful bookkeeping in a caller that does not yet exist — both are
a bigger redesign than "enforce Duration" sounds like from the outside.

Until a caller exists that can supply that state, `Turns` and `Minutes` behave
exactly like `Permanent` and `Scene`: never expiring on their own. `Duration`'s
KDoc spells this out, and `ModifierResolverTest` pins the current
(non-)behaviour so a future change cannot silently alter it without the test
(and this note) being updated.
