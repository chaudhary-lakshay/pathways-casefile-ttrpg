# Known Issues

Carried forward from the M0/M1 final whole-branch review (2026-07-25). Every item
here is **unreachable today** — `ModifierResolver` has no production callers and no
rulebook content has been extracted yet — but each becomes live the moment the
milestone named against it lands.

Ordered by when they must be fixed.

---

## Must fix before any content uses the modifier pipeline (T1/T2, M3–M4)

### 1. `Cancel` computes its budget from raw `Flat` values

`ModifierResolver.resolve` derives a `Cancel` effect's penalty budget by scanning
raw `ModEffect.Flat` values in the live list, **not** the post-cap contribution
actually applied to the value.

Two consequences, both measured:

- Four `Flat(-3)` modifiers inside a `StackGroup(cap = 2)` contribute **-2** to the
  result, but `Cancel(12)` computes the budget as **-12** and adds **+12** — turning
  a penalty into a bonus (base 12 resolves to 22).
- A penalty delivered as `Decay`, `Scale` or `SetTo` computes as **0** and is not
  cancelled at all, though Ch. 9's Bard ("ignore Fright Check penalties up to -3")
  draws no such distinction.

Fix alongside T2 (Fright Check content). See the KDoc on `ModEffect.Cancel`.

### 2. `SetTo` is summed, not assigned

`ModEffect.SetTo` is added to the running total rather than replacing it. Its
correctness depends entirely on a `supersedes` link that nothing enforces at the
type level — two live `SetTo` effects in the same layer and group add together.

The fix is smaller than it looks: assign the last (or highest) `SetTo` in the layer
and drop the others; roughly four lines, no type change. Fix alongside T1
(Midnight Poet, Sequence 8: *"the bonus increases to +2 (instead of +1)"*).

### 3. `StackGroup` caps apply only to `Flat` and `SetTo`

`Modifier.group` is accepted on every effect type, but the capping pass filters to
`Flat || SetTo`. `Scale`, `Decay` and `Cancel` are applied afterwards with no
reference to `group` at all — a single `Decay(from = -9)` carrying
`StackGroup(cap = 2)` applies -9 in full.

This is the structural root of issue 1; fix them as one change.

### 4. `ModTarget` payloads are stringly-typed

The design spec declares `Damage(slot: DamageSlot)`, `Cost(pool: Pool) // SPI | FP | HP`
and `RollClass(tag: SkillTag)`. The shipped code uses `String` for all three (drift
originates in the implementation plan, not the implementation).

Spec §7.1 requires a schema-validation test asserting every referenced symbol
exists — with `String` there is nothing to validate against, and a typo
(`"SPI"` vs `"Spi"`) is silent. All three are unused today, so the fix is free now
and costs a content migration later.

---

## Must fix when secondary-characteristic point-buys or skill defaulting land (M3/M4)

### 5. Skills controlled by Will or Per are keyed to IQ

The published sheets carry 26 skills controlled by `Will` (10) and `Per` (16), which
are secondary characteristics, not members of `Attr`. The fixture loader maps both
to `Attr.IQ`.

This is numerically exact today — `will == IQ` and `per == IQ` on all 23 fixtures,
none of which carry a `SecondaryDelta`, and `GoldenSheetTest` pins that invariant
rather than merely assuming it. The mapping is test-only; `Attr`, `SkillKey` and
`Derive` are uncontaminated.

The limitation is structural and pre-dates the loader: `SkillKey.attr` is typed
`Attr`, and `Attributes` exposes only `st/dx/iq/ht`, so nothing can express
"Will-controlled" at all. What breaks if never addressed:

1. `SkillKey.display` prints `Intimidation/IQ` instead of `Intimidation/Will`.
2. Once a character can spend `SecondaryDelta.will`/`.per` — fields that already
   exist and are **already costed by `PointBudget.report`** — a Will-controlled
   skill silently computes against un-raised IQ.
3. Skill defaulting has nowhere to read "is this Will-based" from.

The right fix is a `ControllingAttribute` sum type (`Attr | SecondaryStat`). Do
**not** widen `Attr` — its members are "the four attributes that can be raised with
character points", and blurring that corrupts `AttributeCosts` and `PointBudget`
semantics.

---

## Must fix when the first content file is written (T0, deferred Task 10)

### 6. Coverage guard: non-recursive content glob

`tools/coverage.py` uses `CONTENT.glob("*.json")`, which will silently miss claims
in nested content directories — an undercount in the anti-lie guard itself.

### 7. Coverage guard: `MECHANICAL` regex misses spaced signs

The regex requires no space between sign and digit, so `"Attribute - 4"` is not
counted as mechanical though semantically it is a modifier. This understates the
denominator, making coverage look *better* than it is — the one direction this
guard must never err in.

---

## Lower priority

### 8. `Scale` floors at each step rather than composing by product

Spec §4.1 says damage multipliers "compose by product". Two `Scale(3,2)` on base 5
yields 10; product-then-floor yields 11. Unreachable until M4 damage.

### 9. `ModEffect` cannot express "half, rounded up"

Spec §4.1 requires both directions for Divination Arts — Direct Link halves
anti-divination penalties *rounding down*, while active techniques apply at half
penalty *rounded up*. `Scale` is floor-only.

### 10. `Duration` is declared but never enforced

`Turns(5)` never expires. The Gauntlet case works only because `Decay` clamps at 0.
Noted in its KDoc.

### 11. No validation on effect parameters or attribute bounds

`Scale.den == 0` throws from inside the resolver; `Cancel.upTo < 0` acts as an extra
penalty; `AttributeCosts.costOf` has no upper bound and its shift ladder overflows
`Int` past roughly level 40; `Attributes` and `SecondaryDelta` accept any value.
Relevant once M2 imports untrusted premades — failures should surface at the
boundary, not from deep inside `PointBudget`.

### 12. `Derive.skillLevels` is untested and collapses duplicates

It is built with `associate`, so two `PurchasedSkill` entries sharing a `SkillKey`
yield one entry while `budget.spent` bills both. No fixture has duplicates, but an
imported premade is untrusted input. It also has no test coverage — the golden suite
calls `SkillLadder.levelFor` directly and never reads it, so the API M2 will
actually call is unexercised.

### 13. `ModifierResolverTest` decouples `SourceKind` from `Layer`

The `mod()` helper hardcodes `source = Situational(id)` regardless of the `layer`
argument, so "Cancel applies to the right `SourceKind`" is untested. Fix alongside
issue 1 — same file, same rewrite.

### 14. `Resolution.applied` is a misleading audit trail

It returns every target-matching modifier, including ones capped to a zero
contribution and ones that only set flags.

### 15. Python tooling is not reproducible off this machine

`extract_pdf.py` and `extract_sheets.py` hardcode `~/Downloads/...`, and
`test_tools.py` invokes both with `check=True`. The suite fails on any other machine
and on CI. Acceptable for one-shot offline tooling, but "6 Python tests passing" is
a claim only one workstation can verify.

### 16. Fixtures carry no per-category point breakdown

Spec §6 lists attribute point costs as an asserted field, but the fixture schema
captures no breakdown, so attribute costing is asserted only by the hand-written
Occult Scholar unit test rather than across all 23 sheets.
