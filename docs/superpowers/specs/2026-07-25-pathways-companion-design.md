# Pathways — LOTM TTRPG Companion: Design

**Date:** 2026-07-25
**Status:** Approved for planning
**Source material:** *Powered by GURPS: Pathways — Lord of the Mysteries* v6.9d (170 pp.) + 23 published character sheets (.xlsx)

---

## 1. What this is

An offline Android app that is a faithful interface over an existing rulebook. The rules already exist; the app does not invent, extend, or reinterpret them. It stores characters, computes everything the book says is computable, rolls 3d6 against the right target number, and tracks state at the table.

**Explicit non-goals:**

- Not a generic GURPS engine. LOTM concepts (SPI, CoR, Digestion, Acting Method, Pathways, Sequences) are first-class in the domain model.
- Not a GM-in-a-box. The engine assists; the human adjudicates.
- No backend, no auth, no network, no sync. Single device, single player, own characters.
- Not a commercial product. GURPS is Steve Jackson Games'; LOTM is Cuttlefish That Loves Diving's. Personal and portfolio use only.

**Audience:** one player, managing their own characters, at a real table, possibly with no signal.

---

## 2. Architecture

Gradle modules. Dependencies point inward only; nothing below `:core:data` knows Android exists.

```
:core:model     pure Kotlin. Character, Attributes, Skill, Advantage,
                Pathway, SequenceBlock, Dice, Modifier, trackers
:core:rules     pure Kotlin engine. derive() validate() resolve()
                Random injected. 100% JVM-unit-testable
:core:data      Room + asset JSON loaders + repositories
:feature:sheet  :feature:builder  :feature:play  :feature:rules
:app            nav host, DI wiring
```

`:core:rules` having no Android dependency is not stylistic — it is what makes the golden-fixture test suite (§6) run in milliseconds on every commit.

---

## 3. State model

### 3.1 What is persisted

Room stores three things and nothing else:

1. **Purchased state** — attribute levels bought, *secondary-characteristic deltas*, advantages taken, disadvantages taken, quirks, skill points spent per `(name, specialization, controllingAttr, difficulty)`.
2. **Beyonder state** — pathway, current sequence, digestion progress, CoR, acting-method log.
3. **Table state** — current HP, FP, SPI, conditions, active situational modifiers, initiative.

### 3.2 What is never persisted

Every derived value: **max** HP, **max** FP, Will, Per, Basic Speed, Basic Move, Dodge, Parry, Block, encumbrance level, skill target numbers, damage expressions. All recomputed from (1) + (2) + (3) on read.

Note the distinction from §3.1(3): *current* HP/FP/SPI are table state and **are** persisted — they are what the player spent. *Maximum* HP/FP are derived and are not. A character at 4/10 HP stores `4`, never `10`.

### 3.3 Secondary characteristics are base + purchased delta

**This corrects an early error in the design.** Ch. 3's Secondary Characteristics table carries a *Cost to Modify* column:

| Characteristic | Base | Cost to modify |
|---|---|---|
| HP | ST | ±2 pts/level |
| FP | HT | ±3 pts/level |
| Will | IQ | ±5 pts/level |
| Per | IQ | ±5 pts/level |
| Basic Speed | (HT+DX)/4 | ±5 pts per **0.25** |
| Basic Move | Basic Speed, floored | ±5 pts/level |

So these are *not* pure functions of attributes — they are `base(attrs) + delta`, and `delta` is billable against the point budget. Basic Speed's 0.25 granularity means the delta is stored in **quarter-steps as an Int**, never as a float, and never as a plain `Modifier(value: Int)`.

### 3.4 Parry and Block are not statistics

There is no character-level Parry. There is a function:

```kotlin
fun parryFor(weapon: Weapon, skills: SkillSet): Int?   // null when the weapon shows "—"
// armed:     floor(weaponSkill / 2) + 3 + weapon.parryMod
// Brawling:  floor(Brawling / 2) + 3
// Boxing:    floor(Boxing / 2) + 3
// Wrestling: floor((Wrestling - 3) / 2) + 3     <- the -3 is INSIDE the halving
```

A character has one Parry per readied weapon, some of them `null`. Block requires a shield and its own DB. Cumulative parry penalties (−4 base, −2 fencing, −1 rapier) are a **per-round counter**, not a boolean flag.

All 23 published sheets leave Parry and Block blank — nobody treats them as stored values.

---

## 4. The modifier pipeline

One pipeline for roll modifiers. It is deliberately richer than a flat sum, because a flat sum returns wrong numbers as early as Chapter 10.

```kotlin
sealed interface ModEffect {
    data class Flat(val n: Int)                          : ModEffect
    data class Scale(val num: Int, val den: Int)         : ModEffect
    data class SetTo(val n: Int)                         : ModEffect
    data class Cancel(val upTo: Int, val of: SourceKind) : ModEffect
    data class Decay(val from: Int, val perTurn: Int)    : ModEffect
    data object Forbid                                   : ModEffect
    data object RequireCrit                              : ModEffect
}

enum class Layer { BASE, POTION, ADVANTAGE, EQUIPMENT, SITUATIONAL,
                   STATE_THRESHOLD, COUNTERMEASURE, COUNTER_COUNTER, BURN }

data class Modifier(
    val id: ModifierId,
    val source: ModifierSource,
    val target: ModTarget,
    val effect: ModEffect,
    val layer: Layer,
    val group: StackGroup? = null,
    val supersedes: ModifierId? = null,
    val scope: Scope = Scope.Self,
    val duration: Duration = Duration.Permanent,
    val condition: Condition = Condition.Always
)

sealed interface ModTarget {
    data class Attribute(val a: Attr)       : ModTarget
    data class Skill(val key: SkillKey)     : ModTarget
    data class RollClass(val tag: SkillTag) : ModTarget  // "all occult/knowledge", "Influence"
    data object AllRolls                    : ModTarget
    data class Damage(val slot: DamageSlot) : ModTarget
    data class Cost(val pool: Pool)         : ModTarget  // SPI | FP | HP
    data object RequiredMargin              : ModTarget
}
```

### 4.1 Why each field exists

| Field | Rule that demands it |
|---|---|
| `ModEffect.SetTo` | Ch. 10 Midnight Poet: *"the bonus during night increases to **+2 (instead of +1)**"* — supersedes Seq 9's +1; a flat sum gives +3 |
| `ModEffect.Scale` | Damage multipliers (cut ×1.5, imp ×2, skull ×4, and they compose by product); encumbrance Move ×0.8/0.6/0.4/0.2 |
| `ModEffect.Cancel` | Ch. 9 Bard: allies *"ignore Fright Check penalties up to −3"* — a `+3` is not equivalent, it would also cancel unrelated penalties |
| `ModEffect.Decay` | Ch. 6 Gauntlet: *"−3 for 5 turns (penalty drops by 1 each turn)"* |
| `ModEffect.Forbid` / `RequireCrit` | Ch. 6.5: *"the −10 penalty cannot be offset by skill alone — only a critical success (3–4) can succeed"* |
| `layer` | Ch. 6.5 §IV states an explicit resolution order; Direct Link *halves* anti-divination penalties (round down) while active techniques apply at *half penalty, rounded up*. A flat sum destroys the partition. |
| `group` | Ch. 6.5 supporting skills *"cap at +2 total"*; Ch. 7 rituals cap at +3; FP burn max +5 |
| `supersedes` | Same as `SetTo` — the book flags stacking as the *exception* (Ch. 12 Werewolf: *"stacks with base potion bonus for +2 total"*) |
| `scope` | Bard buffs *"all allies within earshot for 10 min"*; Corpse Collector gives enemies *"+1 against you"* |
| `condition` | ~80% of the book's ~475 signed modifiers are predicated on circumstance (range, darkness, cover, posture, hit location, sequence delta, preparation quality). A sealed `Condition` ADT keeps new chapters as *data*; a lambda would not. |

### 4.2 Two engine invariants

1. **Derived values are never `ModTarget`s.** They are recomputed from modified inputs using the book's floor semantics. `Modifier(Skill("Broadsword"), Flat(+1))` must move Parry by 0 or 1 depending on parity — a sum-by-target engine returns neither.
2. **Rerolls are a `RollPolicy` returned beside the total**, not a modifier. Luck, Beckoning Luck, Natural Leader, All-Out Attack (Double), Silver Mirror's 50% reflect.

### 4.3 Potion skill grants: set or add

Ch. 9 (p. 85–86): `"Ritualistic Magic/IQ [Very Hard] +3"` means **attribute + 3** — *unless* the character already has that skill at exactly matching name **and** difficulty, in which case it means **level + 3**. Same syntax, two operations, decided by a lookup against prior state.

```kotlin
sealed interface SkillGrant {
    data class SetToAttrPlus(val n: Int) : SkillGrant
    data class AddToLevel(val n: Int)    : SkillGrant
}
// resolver keys on exact (name, difficulty) match before choosing
```

Also: potion-granted points are budget-exempt (*"paid for by the potion"*), and potion drawbacks *"do not give points back"*.

---

## 5. State machines (explicitly outside the pipeline)

`DigestionTracker`, `CorruptionTracker`, `WrathTracker`, `FrightOutcome`, and artifact charge counters are **state machines with thresholds and irreversible terminal states**, not modifiers.

- Digestion 0–100% maps through a **non-linear lookup** (0% → −9, 50% → 0, 100% → +9) feeding a raw 3d6 with **no stat applied**. Failure: *"transform into an NPC Rampager… permanently under GM control."*
- CoR: *"Maximum CoR equals Will"*, thresholds at 3/5/8/10 each granting disadvantages; *"At maximum CoR the character is permanently lost."*
- Fright Check: 3d6 + margin → table granting quirks, −10/−15 pt disadvantages, permanent IQ/Will loss. **Play mutates the purchased set** — disadvantages are not purchase-time-only.

These trackers *emit* modifiers (CoR ≥ 3 → all ritual rolls −1; SPI ≤ ⅓ max → −3 to SPI rolls) but are never stored as pipeline entries. Forcing them in is how one pipeline becomes forty special cases.

---

## 6. Testing: the book is the authority, the sheets are fixtures

The 23 published sheets are a **partial** oracle. They are a hand-maintained build layer over an unmaintained GURPS template, and they are stale precisely where the book diverges.

| Field | Sheet agreement | Use |
|---|---|---|
| Skill target numbers | 232/232 | **assert** |
| Attribute point costs | all | **assert** |
| Will = IQ, Per = IQ | 23/23 | **assert** |
| Basic Move, Dodge | all | **assert** |
| HP | 22/23 | assert, excluding Docks Bouncer |
| FP | 20/23 | assert, excluding Docks Bouncer, Factory Foreman, Street Acrobat |
| Basic Speed | 3/23 | **exclude** — sheets floor 4.75 → 4.0 |
| Thrust/swing | 1/8 where ST > 9 | **exclude** — Docks Bouncer prints the ST-9 row verbatim |
| Parry, Block | 0/23 (blank) | **exclude** |
| Encumbrance | totals hardcoded 0 | **exclude** |
| BP total / surplus | breakdown contradicts item rows on ≥4 sheets | **exclude** |
| Basic Lift, SPI | book never defines either | **exclude** |

Church Healer ships a live `#ERROR!` cell.

**Import model.** A premade is imported as an `importedSnapshot`. The engine computes independently and the UI surfaces a **`computed` vs `asPublished` diff**. Never silently overwrite a published NPC. The diff is a feature: it catches extraction bugs and spreadsheet bugs alike.

**Coverage caveat, stated plainly.** No sheet records a Pathway, Sequence, potion, CoR, or digestion state; every sheet is CoR 0 with no secondary-characteristic purchases. The fixtures prove *character-creation arithmetic*, roughly one chapter of fifteen. Everything else is tested against the book text directly.

**Where book and sheets disagree, the book wins.** Basic Speed at starting attributes is 4.50 (p. 35), not the 4.0 every sheet prints. Encumbrance and Basic Lift appear nowhere in 170 pages — they are stale GURPS-core boilerplate and must be marked house-data, not cited as rules.

**Point budget.** `BP = 70 + min(disadvantages, 40) + min(quirks, 5)`. Validate `spent ≤ BP`, never `spent == BP` — surplus is normal (Hunter-Tracker leaves 26 unspent). Quirks have their own separate 5-point cap. Tolerate missing budget data (Beat Cop has no breakdown block at all).

---

## 7. Content extraction

Extraction is **offline tooling in `tools/`, never app code.** Python reads the PDF and the .xlsx files and emits versioned JSON that is committed to the repo. The app never sees a PDF or a spreadsheet. Re-runnable when v7.0 ships.

### 7.1 The anti-lie guard

"Everything structured" is only true if nothing is silently dropped. Every extraction tier must emit three artifacts:

1. the JSON,
2. a **coverage report** listing every text span in its page range that no rule claimed, and
3. a schema-validation test asserting that every `Condition`, `SourceKind`, `StackGroup`, and `SkillKey` referenced actually exists.

An extraction tier is done when the coverage report's unclaimed spans are all prose (flavour text, examples) and no mechanics remain unparsed. Expect roughly 80% mechanical parsing and 20% hand curation. Do not pretend otherwise.

### 7.2 Tiers

| Tier | Chapters | Pages | Contents | Unblocks |
|---|---|---|---|---|
| **T0** Sheet rules | 2, 3, 8 + thrust/swing table | ~35 | attributes, secondary costs, full skill list w/ difficulty + defaults, advantages, disadvantages, equipment, weapons (damage, parry mod, DR) | M1, M2 |
| **T1** Pathways | 9–12 | ~60 | 22 pathways × Seq 9/8/7/6 = **88 stat blocks** — the bulk of the work | M3 |
| **T2** Play | 4, 5, 6, 7.6 | ~25 | spirituality, combat, Beyonder system, spirit vision | M4 |
| **T3** Casting | 6.5, 7, 7.5, 13 | ~25 | divination, ritual magic, summoning, boons | M4 |
| **T4** Edges | 14, 15, App. A–C, glossary | ~25 | non-standard pathways, setting, quick reference | M5 |

### 7.3 Notation traps to encode once, in T0

- **`1d-3` means `1d6 − 3`**, not `1d3`. The book states no minimum-damage rule, so `1d-6` legitimately yields −5…0. Do not invent a floor; render the expression and let the GM adjudicate.
- The thrust/swing table is **not a clean progression** — ST 27 and 28 both give thr `3d-1`/sw `5d+1`; ST 29 and 30 both give `3d`/`5d+2`. Ship it verbatim as data, do not derive it.
- Skill identity is `(name, specialization, controllingAttr, difficulty)` — the sheets carry `Guns (Pistol)/DX`, `History (Occult)/IQ`, `Observation/Per`, `Intimidation/Will`.
- **Very Hard skills have no default** (p. 10). `attr − 3` is a purchase row, never a default.
- Skill-to-skill defaults exist: `Divination Arts ↔ Ritualistic Magic −2`; `Shield/DX or Brawling −2`.
- The `Summons` tab is **md5-identical across all 23 sheets** — pure boilerplate, zero character data. Its content (`3d10+4`, `DC13`, `Will saving`, `disadv`) is non-GURPS and must be quarantined at extraction, not parsed at runtime.

---

## 8. Milestones

| # | Deliverable | Done when |
|---|---|---|
| **M0** | Tooling | `tools/` converts PDF → per-chapter text spans and xlsx → JSON fixtures; coverage-report harness runs |
| **M1** | Engine, no UI | T0 extracted; `:core:model` + `:core:rules` derive/validate; golden test green across all 23 fixtures on the **assert** field set |
| **M2** | Vertical slice | Import a premade → sheet screen with every value computed → tap a skill → 3d6 with modifiers → `computed vs asPublished` diff visible |
| **M3** | Beyonders | T1 extracted; character builder with live budget validation; potion consumption, sequence advancement, digestion tracker |
| **M4** | Table play | T2 + T3; combat/FP/HP/CoR tracking, active defenses, fright checks, ritual and divination resolution assist |
| **M5** | Reference | T4; searchable in-app rules browser across all 170 pages |

M1 is the real gate. If the engine reproduces 23 independently hand-built characters on the trusted field set, the foundation is sound and everything after it is UI.

---

## 9. Open questions

None blocking. Deferred by choice: multi-character/GM mode, networked table, and any content authoring — all addable on top of `:core:rules` without changing it.
