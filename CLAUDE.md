# Pathways — Agent Handover

Handover for a fresh agent (written for Claude; Claude auto-loads this file).
Everything below is verified against the tree, not recalled. Section 1-5 were
written against commit `cf14e7c`; the counts in section 2 and the slice
sections below are current as of `086ad7d` (slice 6be).

**The slice log lives in [`docs/SLICE-LOG.md`](docs/SLICE-LOG.md)** — sections
6a-6cw, moved out of this file because it was 497 KB against a 150 k-char
limit. Every reference below to a slice by number points there.

**Read the slice log from the END.** 6ax is a handover header; 6ay through
6bc are the slices after it, and between them they found a roll modifier, a
gate that hid an entire screen, a rule the app itself broke, the only place
the book grades a ritual that worked, and a whole 75-point advantage table —
none visible in 6ax's own "nothing is outstanding" table. Treat 6ax the way
6ax says to treat its predecessors: verify it, do not inherit it.

**Handover size and retention.** Keep this file at or below 80,000
characters, and the four loaded instruction files together below 150,000;
count decoded characters after every slice update. Keep full lead paragraphs
for at most the five newest slices, within 16,000 characters combined, and
retire the oldest first. Before retiring a lead, move it VERBATIM into
[`docs/READING-ORDER.md`](docs/READING-ORDER.md) and compare the two exactly —
a matching heading in the slice log is not a copy (6ct's lead was the only
one). What replaces it here is a digest of at most 200 characters: the topic
and its consequential correction. Keep the digest index within 8,000
characters and archive its oldest entries there too. Active constraints,
unresolved work and durable operating instructions stay here whatever their
age.

**Three instructions the retired leads carried that are needed at load
time.** A purchasable row is REACHABLE whether or not a premade prints it:
`PurchasableTraits.offered()` takes the whole advantages and disadvantages
catalogue minus Wealth, so the 23 fixtures alone are not a reachability
corpus (6de). 6dy's census of the 196 unclaimed mechanical lines is a claim
with an expiry date — three of its rows are refusals whose reason is a claim
about this app, and one grep per row is what checking it costs (6dy; 6ep ran all three, and one split). And on
a fresh clone the regeneration order is `extract_pdf.py`, then `coverage.py`,
then the extractors, because `coverage.py` writes `build/extract/book.txt`,
the anchor every `sourceLines` number is read against (6ed).

**A new production reader of a trait by name must add it to
`TraitEffects.known()` in the same commit.** Before 6es a trait missing
from that set made the sheet silent. Now `reported()` prints
`TraitEffects.UNREAD` for it, *"this app applies nothing this row prints"*,
beside a rule that is applied. Nothing enforces this: the census tests name
the readers that were found (Night Eyes, Spiritual Fortitude, the language
pricing pair) and cannot see the next one.

**Every roll at a default takes Versatile's +1, and goes through
`SkillDefaults`, or through `Divination.divinationLevelFor` for Divination
Arts.** Four do: the Skills tab's untrained list, the Attack dialog's
untrained rows (6eu), the Defend dialog's Acrobatic Dodge (6ev) and the
Divine tile. Versatile's sheet note was removed on that count (6et), so a
fifth consumer that skips the +1 leaves the trait silently short. A
Warrior's weapon at DX is not a default and
takes none.

**`defense_modifiers.json` is finished, and so is the round boundary behind
it.** 6co applied Retreat — three numbers scoped per defence row (+3 Dodge / +1
Block / +1 Parry vs. 1 melee attack per round) — after 6cm's Dive! and 6cn's
prone -4. The two facing bullets are not outstanding: 6ck refused them with a
reason and a test. **6cp then ran 6co's own named query and the refusal
expired**: "Each can be used once per round" is honoured by a `New round`
control over `RoundState`, with no global state at all. Read 6cp for WHICH half
of the recorded reason died — 6bf's "a round tracker is not worth global state"
survives untouched, and 6co's "a latch on one control claims a boundary the
others cannot honour" is the half that expired, because it was a claim about
REACH and all six per-round values now sit in one composable. **A refusal can
survive its author's stated reason and die of a fact none of its words
mention.** Book lines 2456-2457 stay unclaimed by any content file, and that is
now a deliberate nothing: the sentence has no field a consumer would read.

**Nothing in the two FP tables is outstanding.** 6cj closed the last one:
`All-Out Defense (Double)` is a priced row this app deliberately does not
charge for, and the reason now lives in `ActiveDefenses.UNPAYABLE` — in
production source, where a sweep over Kotlin can find it — with the census
test reading its expected set from there. Both suites green, working tree
clean. Verify that claim the way 6ax asks you to verify 6ax, and note that
6ci found two claims in this file's own newest paragraphs had been false for
four slices, and then 6cj found one of 6ci's own.

**Codex answered again on 2026-08-20 at 23:14**, its stated 14:23 reset having
passed — check the CLOCK and not just the date (6am, 6as). Two things about
that pass are worth inheriting. `adversarial-review --wait` refused its own
input (`Input exceeds the maximum length of 1048576 characters`): the companion
discovers this file from the cwd, and it plus `docs/SLICE-LOG.md` are several
hundred KB, so a design review has to go through `task --effort high` with an
explicit *"do not read CLAUDE.md or docs/SLICE-LOG.md"*. And `review --wait`
with no argument **reviewed the wrong target** — an untracked leftover script —
returning two P2s about it and one dismissive sentence about the ten files that
had actually changed; re-asked with the target named, it returned a real
finding on its first pass. **Read a reviewer's target list before reading its
findings** (6dd). **And as of 6df the companion's `review` subcommand refuses
focus text at all** — it answers *"/codex:review now maps directly to the
built-in reviewer and does not support custom focus text"* — so naming the
target, which is the fix 6dd found, is no longer possible through that
subcommand. Both the design review and the diff review now go through
`codex exec` called directly (6eg; the invocation is in `~/.claude/CLAUDE.md`),
which takes the target list, the three questions and the "do not read
CLAUDE.md or docs/SLICE-LOG.md" instruction in one prompt.

**When Codex is out and the free panel is also silent, go to one review
sub-agent rather than shipping self-checked** — 6cz is what two slices of not
doing that cost. From 6cd that fallback was `ecc:kotlin-reviewer` (the `ecc`
plugin is disabled as of 2026-10-08, so the agent no longer loads), scoped to three questions and the files needed
to answer them — 6cg's returned a HIGH in under two minutes, and 6ch ran two
(decomposition, then diff). The scoping budget keeps paying (6af, 6cb). Check
the CLOCK and not just the date (6am, 6as).

**There is now a free second reviewer.** `node ~/.claude/qa/qa.mjs` drives the
`pi` CLI against a local **9router** gateway (`http://127.0.0.1:20128/v1`,
provider `9router` in `~/.pi/agent/models.json`) — free upstreams, so it costs
nothing and can run for hours. Read-only by default (`-xt edit,write`), and when
`--dir` is this repo, pi discovers this file the way Claude does — that is
cwd-based discovery, not something the script passes, and `-nc` is the flag that
turns it off — so the section 4 rules apply to its reviews too.

```bash
node ~/.claude/qa/qa.mjs probe                  # which models actually answer today
node ~/.claude/qa/qa.mjs review [--base REF] [focus]   # first model that answers
node ~/.claude/qa/qa.mjs panel  [--base REF] [focus]   # all four, same diff
node ~/.claude/qa/qa.mjs ask "<question>"       # read+bash, no edits
node ~/.claude/qa/qa.mjs sync --key <KEY>       # re-read the gateway catalogue
```

**Probe before you trust the catalogue, and a probe expires within the day.**
The gateway advertises 20 models. **4 answered** on 2026-08-15
(`nvidia/z-ai/glm-5.2`, `nvidia/minimaxai/minimax-m3`, `ollama/gpt-oss:120b`,
`ollama/minimax-m3`); the rest returned 401 (grok-cli login expired), 410 (model
EOL) or 404 (never pulled). **Later the SAME day (6co) only 1 of 20 answered** —
the other three had moved to 429, and the survivor then 429'd through the whole
failover chain, so the review fell back to `ecc:kotlin-reviewer`. A model id in
`pi --list-models` is a claim about the past, and so is this paragraph. `review`
fails over down the panel on 429 and waits out the printed reset window, so a
single rate limit is not a refusal — but **every one of these is a small free
model, so it is a widener, not a replacement for Codex**: treat a finding as a
lead to verify, never as a verdict, and keep the scoping budget (≤3 questions,
only the files needed).

**The panel's sensitivity is now measured, and it is low.** `qa-findings/`
holds the harness for a full sweep run 2026-08-15/16: 25 batches of test files
checked against `build/extract/book.txt`, each required to cite a verbatim book
line, then a second session cross-verifying findings it did not author
(`run-round1.sh`, `run-round2.sh`, `batches.txt`; the outputs are gitignored
because a round is a claim about the models that answered that day). Result:
**24 batches returned `NO FINDINGS`, 1 produced a citable finding, and round 2
REFUTED it** — the finding's own "grep returns nothing" claim was false. Four
batches came back `NOCITE`, so the harness does discriminate; what it does not
do is find defects. **Read that as a number about the instrument, not a clean
bill of health on 1513 tests.** Re-running the sweep is cheap and proves
nothing new unless the batches change.

**6ew is newest, and it is 6es's successor: a self-control row's other
clauses were unsaid.** `reported` gave `UNREAD` only to a row with no
self-control roll, so 28 of the 30 rows with one said nothing beside
Reckless's -1 and Reluctant Killer's *"-4 to hit ... cannot Aim"*. Read
it for three things.

- **`TraitEffects.ROLLED_ONLY`** is one sentence for all 28, the roll
  excepted, and `SelfControl.checksFor` decides it, so a quirk of the
  same name is not told its roll is applied (the review's MEDIUM).
- **The sweep the review asked for found a row that could not find
  itself.** `catalogueName` cut `Compulsive Behavior (Indulge Evil
  Desires) SC 6` to `... SC` as if 6 were a level. The builder sold it,
  and the sheet showed no cell, no roll and no note. A row's own name
  now resolves to itself.
- **6ev's attack-roll successor is answered by census**: with every row
  held, `TraitEffects.modifiers` returns nothing for all 19 combat
  skills, so the missing term costs nothing yet.

Codex was out and the gateway down. One sub-agent reviewed the diff, and
the `catalogueName` line came after its review. **Codex then SHIPped
6ev and found one MEDIUM in 6ew**: that line returned the sheet's casing,
and `reported` checks `known()` case-sensitively. It returns the row's spelling now. **Successors**: Reluctant Killer
as a declared clause in the Attack dialog (-4, -2 with the face hidden,
and no Aim), a Warrior's weapon parrying at DX, Warrior's *default +2*,
and 6es's two lists.

**6ev is next, and it is the successor 6eu named, asked the right way:
the bare-hand parry was never a default.** Book line 2473, the Parry
(Unarmed) formula cell, prints *"Brawling or DX ÷ 2 + 3"*, and the app had
read the first operand alone since the row was extracted. Read it for four
things.

- **The DX row is one of the rows.** `ParryBlock.bareHandParry` is DX
  itself, not Brawling's DX-2 halved, and it is withheld only where a held
  Brawling parries as well. The cell says *"or"*, so my *"only without
  Brawling"* was an unprinted gate. Its −3 against a weapon is a reading,
  and the screen says so.
- **An armed default stays out, on the cells' own difference**: *"Weapon
  Skill ÷ 2 + 3"* prints no second operand. A carried weapon with no skill
  is named in a note.
- **The Acrobatic Dodge rolls its default here**, at the level
  `SkillDefaults` composed: the fourth consumer.
- **Offering that roll to more sheets made an old gap reachable.** Defend
  passed the trackers' modifiers and never `TraitEffects.modifiers`. No
  premade holds both Perfect Balance and Acrobatics, so nothing showed it
  until Pickpocket rolled 4 here and 6 on Skills (the diff review's HIGH).

**Codex ran out mid-review** (back Oct 9, 2:02 AM) and the free panel's
gateway was down, so both reviews were one sub-agent each, the third gate.
Twenty-two mutations, twenty-two caught. On the SM-T570: `Parry (DX, bare
hands) 7`, then `4` against a weapon and no row against a shot; Pickpocket's
`Acrobatics (untrained, DX-6) 4 → 6`. **The tablet's save was left seeded**:
a harness check refused the restore, and 6ev says where the backup is.
**Owed first: a Codex diff review of `5fe2b41..127532d`**, which only a
sub-agent has read, and whose post-review fix no reviewer has. **Successors**:
whether a Warrior's weapon parries at DX, which is sharper now; the same
missing trait term on the ATTACK roll; Warrior's *default +2*; and 6es's
two lists.

**6eu is next, and it is 6et's second consumer: the Attack dialog takes
an untrained skill.** A character without Brawling had no punch there, and
one handed a revolver had no shot. Read it for four things.

- **The list is "the attacks supported here"**, an app boundary: the eight
  untrained skills with a fist or a weapon row. The book's COMBAT SKILLS
  banner prints nineteen and does not decide it.
- **6et's refusal lasted one slice.** It named three potion lines about
  weapons and applied none, because a list of skills has no weapon. This
  screen has one, so a Warrior or Weapon Master wields it at no less than
  DX, with no Versatile +1.
- **The level now depends on the hand**, so one `levelWith` feeds the
  header, the stepper, the damage and the roll, and `Attacks.atLevel`
  drops a Deceptive Attack the lower level cannot afford.
- **The dialog's KDoc said "this book tags no skill as a combat skill"**
  beside a banner in `skills.json`. Corrected, and in `TraitEffects` too.

Codex answered both reviews: CHANGE on all three design questions, with
two HIGHs on when the mastery applies, then FIX on the diff. Taken: the
floor reaches a BOUGHT skill below DX too. **Declined**: a Compose test for
the two `atLevel` call sites (section 6). Twelve mutations of the code as
it stands, twelve caught. **The floor then printed a parry of 9 in Attack
beside Defend's 8**, found after both reviews: a fix for a review finding
is code no review has read. On the SM-T570: `Brawling (untrained, DX-2, …) 7`, `1d-3 cr`; a
seeded Warrior's `Knife (DX, Warrior's "no default penalty") 11`, then `7`
and `Deceptive 0` with the knife down. **Successors**: the Defend dialog
takes no default, Warrior's *default +2*, and 6es's two lists.

**6et is next, and it is the successor three slices carried: the
untrained roll.** `SkillLadder.defaultFor` had no production caller since
M0, so chapter 2's table reached no roll, and three screens called an
untrained roll *"the GM's call"*. Read it for four things.

- **A skill's own line beats the table.** Brawling is DX-2 where Easy says
  -4, Wrestling has *"No default"* where Average says -5, and Boxing
  agrees. `SkillDefaults` holds the two overrides. `defaultFor` is the
  table alone.
- **Divination Arts is asked of `Divination`, not recomputed.** Line 3559
  does not say *only*, so withholding SPI-6 is a reading (the design
  review's MEDIUM). It is the one the Divine tile already made, and the
  list says so on screen.
- **One consumer.** A row at the foot of the Skills tab opens a filtered
  list of 149 skills, and a pick goes to the sheet's own roll dialog. The
  Attack and Defence dialogs take no default, and now say that.
- **Versatile's +1 is inside every default**, and its sheet note is gone:
  the second false sentence that trait has carried.

Codex answered both reviews: CHANGE, SHIP, CHANGE on the design, with ten
stale sentences listed, then FIX on the diff. Its MEDIUM: one note
flattened three potion lines about weapons, and Harvest Priest's covers
farming tools only. Seventeen mutations, two of which survived until they
got an assertion. On the SM-T570: `Acrobatics (untrained, DX-6)`, `Skill
4`, `3d6: 14 vs 4`. **Both reviews passed a list that offered five premades
their own skill back**: a sheet prints *"Mathematics"* where the book
prints *"Mathematics (Applied)"*. A probe of every premade through every
potion found it, after the commit. **Successors**: an untrained punch and a carried weapon
in the Attack dialog, the Acrobatic Dodge, whether a default parries, and
6es's two lists.

**6es is next, and it is 6er's first successor: it lists the complement
of `TraitEffects.known()`, which nothing had.** 148 of the 198 catalogue
rows, by a throwaway test at `c3011e6`. Read it for four things.

- **Two rows in it were applied by name**: Night Eyes, from the same
  object, and Spiritual Fortitude, in `Corruption`. Both are in `known()`
  now. The census is a grep for name matches diffed against the set.
- **Three cells name printed skills and no circumstance**, and lifted
  nothing: Acute Social Awareness, Former Military Officer, Photographic
  Instinct. Every guard on `SKILL_BONUSES` walks the map.
- **Official Beyonder is refused whole.** I favoured applying its *+N* to
  an owned skill, where both readings agree. The design review's HIGH:
  chapter 6 says that of a potion's entry, and agreement between two
  proposed readings does not establish the rule.
- **The sheet listed no advantages.** It printed `reported()` and the
  drawbacks, so 24 of the premades' 50 advantage rows were nowhere on it.
  Each held trait is one row now, with its cell and its notes, and a row
  nothing reads says `TraitEffects.UNREAD`.

Codex answered both reviews: CHANGE on all three design questions, then
FIX on the diff. Its MEDIUM was that `UNREAD`'s first wording, *"reads no
rule from this row"*, was false of five rows whose PRICE the app reads; it
begins *"its price aside"* now. Ten mutations of ten were caught. On the
SM-T570, Beat Cop's Traits tab: `Legal Enforcement Powers — 5 pts`, its
cell, and the line; neither was there before. **The helper drops any line
holding a quotation** (6er's open question): read it from the raw dump.
**Successors, none ranked**: six conditional skill rows, a list of rows
naming a mechanic this app has a screen for (untriaged), and 6eq's second,
book 2540's *"Default: DX-2 if untrained"*.

**6er back to 6de had lead paragraphs here until 2026-10-08. Each is in
[`docs/READING-ORDER.md`](docs/READING-ORDER.md) verbatim, and in full in the
slice log.** One line each:
**6er** — the builder sells a language; the two halvings combine with one ceiling, and a Language Talent bought later reprices what is owned;
**6eq** — 39 refusals in production source whose subject is this app, seven false or stale; Versatile's +1 goes inside the pick between an owned level and a default;
**6ep** — 6dy's three refusals re-asked; the HP recovery table's first row is a ROLL, so the daily HT roll is offered and nothing is restored;
**6eo** — the builder printed bought values and the sheet the played ones; rows show the value as played, and every edit still reads the purchase;
**6en** — Basic Speed and Basic Move sold a hundred steps each; both stand at zero in `ScoreFloors`, as policy, and Move is measured after Speed;
**6em** — maximum CoR is a state the app checked only on a gain; a builder sale is refused where a potion latches, and the first potion never latched;
**6el** — a potion's grants are added to the bought score, so browsing a block crashed a high-ST save; `PurchaseCeilings` bounds each purchase, and 6ek's own example was false;
**6ek** — the builder sold a secondary a hundred steps and four guards threw below HP/FP 1, a CoR ceiling of 1 and Per 0; "four" came from a keyword grep and held, for crashes, on a wider one;
**6ej** — the Drink button printed nothing about its own roll; `pcrLine` feeds both blocks, and the new line could crash the block it was drawn on, a Will below 1 throwing in composition;
**6ei** — two taps milliseconds apart DO reach one composition's handler, so 6ef-6eh's "two frames" premise was false; Drink alone had no re-entrancy guard and crashed on an SM-T570;
**6eh** — seven more engines roll the target they printed; `attempt` became test-only, and no test had rolled a Spirit Vision success through the engine;
**6eg** — print a target, then roll it: a handler belongs to the composition that drew it, so `rollAgainst` takes the target on screen;
**6ef** — the GM picks inside a span cell; a keyed `remember` resets a frame late, so a pick lives with its row, and the click-time read was undone by 6eg;
**6ee** — every free toolchain diagnostic run as a sweep: clean, and beside its hits an assertion that could not fail and a fourth inline copy of `endsBetween`;
**6ed** — does a committed generated file reproduce from its tool: 81 of 81 do, and `build/extract/book.txt`, the citation anchor, had no writer (`coverage.py` writes it now);
**6ec** — every `@Serializable` property checked for a key that sets it; two stat notes were cut at their wrap, and re-running `extract_pathways.py` silently reverted `b1c8c4e`;
**6eb** — the defence screen read `Character.weapons`, which nothing fills; carried weapons are catalogue names in `SavedEdits.weapons`, priced by `Weapons.parryFor`;
**6ea** — the six priced secondary characteristics were billed and could not be bought; the save stores the book's row key, and the merge ADDS to a potion's grant;
**6dz** — every optional parameter checked for a non-default argument; span rows (`+0 to +2`) applied their near end because only `BoonDialog` passed `gmValue`;
**6dy** — the post-roll SPI push reached the plain skill roll alone; ritual, divination and boon take it now, and the 196 unclaimed mechanical lines are tabled as a census;
**6dx** — `Shaken` needed no refusal, its carrier being a recorded GAP in `PotionDigestion.UNPARSED`; a line-local regex had dropped a wrapped `p. B229`;
**6dw** — chapter 8's gear reference rendered only after the roll; it sits in the pre-roll branch now, below the modifier chips;
**6dv** — `extract_equipment.skill_index()` dropped the open-row skills `isTemplate` matched, one flag at two sites; and a dump's tap scale is per WINDOW;
**6du** — claimed lines checked for numbers their file never holds (clean); the fright table's `25 − HT` durations are computed, and the operand is not the attribute the cell rolls;
**6dt** — `Hard to Subdue` reached nothing beside an applied `Hard to Kill`; two gaps cancelled into silence, and `BespokeBonus` feeds `known()` now;
**6ds** — fright recovery rolls for the three bands that print one, anchored on the cell's own `roll vs. X`; `RollResult` carries its own target and outcome;
**6dr** — "this app models no stun recovery roll" was false the day it was written; `StunRecovery` answers for the Major Wound row alone;
**6dq** — the SPI-recovery refusal cited the appendix summary, not p. 34; it lives in `Pools.UNMODELLED` now, and chapter 4 deliberately claims nothing;
**6dp** — `MAX_QUIRKS` was asserted nowhere; it is pinned to book lines 1121-1122, and nothing this app ships produces a quirk;
**6do** — Wrestling was kept out by a reason true of the damage column only; its maneuver table is extracted, and the held character's `Escape-2` row is a gap;
**6dn** — "all seven costed rituals" was nine; the filter had excluded the two template ranges that could contradict the assertion;
**6dm** — the detection ward chip prints the book's cost, and `AntiDivinationTechniques.spiFor` still has no caller on purpose;
**6dl** — Appendix A's Major Wound row disagrees with chapter 5's (15 min against 1d hours), recorded in `SUMMARY_DISAGREEMENTS` and never repaired; read the build's own warnings;
**6dk** — a claimed sentence pointed at an unclaimed reference, so the incantation structures ship as `incantations.json`; never redirect an extractor's stderr;
**6dj** — the skill-boundary table's -4 is not the difficulty band's -4; it ships as `divination_scope.json` behind a disjointness guard;
**6di** — the Crippling paragraph sat in a three-line seam the coverage filter cannot see; `Wounds.effectsAt` reads it, and claiming it moved no percentage;
**6dh** — `spirit_vision.json` got its loader; chapter 7.6's summary table is wrong in both directions at once, so grants are read from the BLOCKS;
**6dg** — the glossary extractor's guard could not pass (25 entries, not 26), and the sweep of every content FILE for a loader;
**6df** — three Will-roll clauses joined nothing; they are GM declarations on all three bare Will rolls, and a count needs its query;
**6de** — `VitalRolls` gave the HP table's two rolls their first reader, and its "no fixture carries one" verdict was wrong within the hour.

**6dd back to 6cd had lead paragraphs here and they have been cut, because
this file keeps reaching the 150 k-char limit (6eb: 152,583; 6eh cut 6dd to
6da at 146,179, each checked for its own section first).** Every one of
them is in [`docs/SLICE-LOG.md`](docs/SLICE-LOG.md) unchanged and in full,
and `docs/READING-ORDER.md` still names them. What they are worth in one line
each: **6dd** — "-1 to all rolls" had two reaches in one app, and a
refusal's bar travels with the condition that produced it; **6dc** — one
screen printed "Dodge halved" and the next ignored it, and a rule cited from
the right file can be the wrong rule; **6db** — a line the coverage filter
cannot see leaves the denominator too, so an unread chapter reported 100%,
and **percentages recorded before 6db must not be compared with ones after
it**; **6da** — two careful exclusions hid one line between them, and an
absolute names the corpus it was checked against.
**6cz back to 6cd** have one-line digests too, moved verbatim to
[`docs/READING-ORDER.md`](docs/READING-ORDER.md) to keep this index under
its 8,000-character cap (6ev).

**Where to start next: the slice log, from the END — 6ew first.** The
reading-order narrative for 6cc back to 6bf moved to
[`docs/READING-ORDER.md`](docs/READING-ORDER.md) on 2026-10-08, unchanged.

**6bb also retracts a finding of its own, mid-slice.** It read one table as
contradicting another, had the fix half-written, and two lines of the book
killed it. 4.1 cuts both ways: a rule the book does not print must not be
applied, and code the book does not contradict must not be "fixed".

**And 6ba corrects 6ay's METHOD, not only its output.** The seam sweep ran a
3-line window and a numeric-only filter; both are too narrow, and a rule that
wraps across the line where a claimed span ends is invisible to them. Every
sweep in this file is also a claim about what it cannot see — audit the
method before trusting the verdicts.

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

**M0 through M4 are complete and merged to `master`.** M4's slices are
sections 6a–6am; the ones after 6o are chapter-by-chapter content work rather
than milestone scope, so read the slice log from the END, not from 6a. Section
6 still describes M4's original shape and is kept for that; the live picture
of what is left is the closing paragraph of the most recent slice.

There is an Android module (`app/`, Compose) with four screens: premade list
(theme picker in its top bar) -> sheet (the `computed vs asPublished` diff, a
Vitals/Skills/Traits bottom bar, and a twelve-tile action hub), the pathway
screen (potion grants, digestion tracker, Potion Consumption Roll), and the
builder (attributes, secondary characteristics, wealth, appearance, languages, traits,
weapons and bought skills, against a live point budget). `docs/HOW-TO-PLAY.md` is the
player-facing guide to all four and is verified against the labels in source.

| | |
|---|---|
| Tests | **1866 Kotlin** (JUnit 5 — 1306 `core:rules`, 540 `app`, 20 `core:model`) + **361 Python** (pytest), all green — counted from the JUnit XML, not from memory. **This row was stale by two on the Python side before 6dg**: it read 300 where `HEAD` carried 302, so re-count rather than adding your delta to what it says. A filtered run (`--tests`) leaves the results directory holding only what it ran, and Gradle then reports the unfiltered task UP-TO-DATE — count after a `--rerun-tasks`, or you will read the filter's leftovers (measured in 6dg: 489). **The `app` module writes to `build/test-results/testDebugUnitTest/`, not `.../test/`**: a glob over `test/` alone silently drops all 466 and still reports a green total (6dc). The `app` jump from 92 is `ThemeSchemesTest`, which sweeps every `ColorScheme` role by reflection and so emits one dynamic test per role per preset per mode |
| Open issues | **Zero.** OPEN-1 was fixed in 6ar; the Codex sweep's 17 findings are all closed or refused with evidence (6as, 6at) |
| Review gate | **Codex is answering again.** The 26-slice refusal ended in 6as; every slice since has been reviewed before commit |
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
                Theme.kt chooses a scheme; ThemeSchemes.kt is GENERATED by
                tools/gen_theme.py and must not be hand-edited; ThemePicker.kt
                is the bottom sheet, reached from the gallery's top bar
fixtures/       23 character-sheet JSON fixtures (wired onto core:rules TEST classpath)
tools/          offline Python extractors (pypdf, openpyxl) + their tests
docs/           spec, plan, KNOWN_ISSUES, SLICE-LOG, HOW-TO-PLAY (table guide)
qa-findings/    free-model QA harness: batches.txt + two round scripts
```

`core/model` — `Attr`, `Attributes`, `BeyonderState`, `Character`,
`ControllingAttribute`, `Dice`, `Difficulty`, `Modifier`, `PurchasedSkill`,
`RollResult`, `SecondaryDelta`, `SkillKey`, `Trait`, `Weapon`.

`core/rules` — `AttributeCosts`, `Derive`, `ModifierResolver`, `ParryBlock`,
`PointBudget`, `Conditions`, `Corruption`, `FrightCheck`, `Pools`, `PotionConsumption`,
`PotionDigestion`,
`Attacks`, `Damage`, `Ranged`, `Wounds`, `Premades`, `RitualResolution`,
`RandomSource`, `SecondaryCharacteristics`, `SheetComparison`, `SkillLadder`,
`SkillRoll`, `SuccessRoll`, plus `content/ContentLoader` reading all 82 JSON
files from `core/rules/src/main/resources/content/` — **every one of them has
a loader as of 6dh**, which closed `spirit_vision.json`, the last file no
Kotlin named. Re-run that sweep rather than trusting this sentence; the query
is in 6dg: `skills.json` (158),
`advantages.json` (99), `disadvantages.json` (99), `thrust_swing.json` (30),
`pathways.json` (88), `acting_method.json` (5), `potion_consumption.json` (11),
`cor_gained.json` (6), `ritual_cor_thresholds.json` (4),
`pool_thresholds.json` (9), `hp_recovery.json` (3), `fright_triggers.json` (5),
`fright_effects.json` (9), `ritual_weights.json` (3),
`ritual_failures.json` (4), `ritual_critical.json` (9), `defenses.json` (4),
`defense_modifiers.json` (5), `maneuvers.json` (14),
`extra_effort.json` (4), `attack_options.json` (2),
`unarmed_attacks.json` (13), `range_penalties.json` (7),
`ranged_modifiers.json` (9), `hit_locations.json` (9),
`wounding_multipliers.json` (8), `spirit_damage.json` (15),
`major_wound.json` (3), `shields.json` (4), `shield_rules.json` (3),
`glossary.json` (25), `spirit_vision.json` (4 sections + 7 grants),
`divination_difficulty.json` (5), `divination_scope.json` (3),
`divination_methods.json` (8),
`divination_modifiers.json` (8), `divination_clarity.json` (6),
`divination_support.json` (4), `ritual_preparation.json` (5), `incantations.json` (3),
`boon_granting.json` (1), `boon_modifiers.json` (8),
`non_standard_pathways.json` (17), `weapons.json` (9), `wealth.json` (7),
`equipment.json` (77), `secondary_costs.json` (9),
`damage_abbreviations.json` (8), `appearance.json` (8),
`languages.json` (12), `language_proficiency.json` (6),
`language_notes.json` (2), `ritual_support.json` (5),
`ritual_quick_preparation.json` (3), `ritual_target_link.json` (4),
`entity_response.json` (6), `sealed_artifact_grades.json` (4),
`sealed_artifacts.json` (4), `sequence_immunity.json` (5),
`anti_divination_counters.json` (5).

### Public entry points

```kotlin
AttributeCosts.costOf(attr, level): Int
AttributeCosts.totalCost(a: Attributes): Int
Derive.of(c: Character): DerivedCharacter
Derive.controllingLevel(ca, attrs, secondaries, spi): Int
ModifierResolver.resolve(base, mods, target, ctx): Resolution
ParryBlock.parryFor(weapon, skillLevel): Int?
ParryBlock.unarmedParry(style, skillLevel): Int   // BARE_HANDS takes DX
ParryBlock.bareHandParry(dx, brawling): Int?      // p.37's "Brawling or DX":
                                      // null only where a held Brawling
                                      // parries at least as well
ParryBlock.blockFor(shieldSkillLevel, shieldDb): Int
PointBudget.report(...): BudgetReport
SecondaryCharacteristics.of(a, d): Secondaries
SecondaryCharacteristics.costOf(d): Int            // what PointBudget bills
SecondaryCharacteristics.stepsOf(d, key) / .withSteps(d, key, steps)
SecondaryCharacteristics.plusSteps(base, steps: Map<String, Int>)
                                      // the book's own row key from
                                      // secondary_costs.json -> steps bought;
                                      // ADDS, because a potion grant lands in
                                      // the same field. Basic Speed counts
                                      // QUARTERS, and an unpriced or unknown
                                      // key changes nothing
SecondaryCharacteristics.valueLabelOf(s, key): String?
                                      // null for spi/cor/digestion: the three
                                      // rows the table refuses to price
SkillLadder.levelFor(points, difficulty, attributeLevel): Int
SkillLadder.defaultFor(difficulty, attributeLevel): Int?   // ch.2's table ALONE
SkillDefaults.offeredTo(character): List<Untrained>   // what a screen asks: the
                                      // table, Brawling's DX-2, Wrestling's
                                      // none, and Divination Arts via Divination
SkillDefaults.offered(owned, attributeLevel, defaultBonus) / .weaponNote(pathway, sequence)
SkillDefaults.NOT_LISTED / .PLAIN_ROLL    // what the list withholds and does not carry
SkillDefaults.attacks(untrained)      // the rows the Attack dialog can show an
                                      // attack for: an app boundary, not a rule
SkillDefaults.masteryFor(pathway, sequence) / .mastered(level, weaponInHand, mastery, dx)
                                      // Warrior / Weapon Master: a weapon IN
                                      // HAND is wielded at no less than DX
Attacks.atLevel(plan, level)          // the hand changed the level: drop a
                                      // Deceptive Attack it cannot afford
SuccessRoll.against(target, source: RandomSource): RollResult
ContentLoader.skills() / .advantages() / .disadvantages() / .thrustSwing()
ContentLoader.pathways() / .actingMethod() / .potionConsumption()
ContentLoader.corGained() / .ritualCorThresholds() / .poolThresholds()
ContentLoader.hpRecovery()            // p. 42's three Natural Recovery rows
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
PotionConsumption.corCostFor(state): Int      // 0 at 100%, else the book's floor;
                                      // what attempt charges AND the screen prints
Corruption.maxFor(c) / .maxFor(will, traits): Int          // Will, +3 with Spiritual Fortitude
Corruption.clamp(cor, max) / .isLostAt(cor, max) / .isLostAfter(cor, delta, max)
Corruption.ritualPenalty(cor) / .ritualThresholdsReached(cor)
ScoreFloors.MIN_POOL_MAX / .MIN_COR_CEILING / .MIN_PER   // the app's, not the
                                      // book's: the guards in Pools, Wounds,
                                      // Corruption and Detection read these
ScoreFloors.MIN_MOVE / .MIN_SPEED_QUARTERS   // policy, not a guard: both 0,
                                      // and Move is measured after Speed (6en)
ScoreFloors.shortfall(secondaries, traits) / .holdsCeiling(ceiling)
PurchaseCeilings.attribute(attr) / .secondarySteps(key)   // the highest a
                                      // purchase may reach so every potion
                                      // fits on top: bound less the largest
                                      // walk, derived from the catalogue (6el)
PotionDigestion.largestGrants(blocks): PotionGrants   // each row's peak
Pools.hp(current, max) / .fp(current, max) / .spi(current, max): PoolStatus
Pools.thresholds(pool): List<PoolThreshold>   // empty for FP: prose only
NaturalRecovery.isOffered(hp) / .rows()       // hurt and not dead; the rows
                                      // are PRINTED, nothing is restored
NaturalRecovery.bonusFor(traits) / .targetFor(ht, traits, poolPenalty, gmModifier)
                                      // all-HT-roll traits only, never
                                      // VitalRolls.bonusFor's scoped two
NaturalRecovery.UNMODELLED            // First Aid, Physician, Healing,
                                      // Regeneration, each with its reason
Conditions.forSkill(skill, hp, fp, spi, cor): List<Modifier>   // scoped per skill
Conditions.penaltyFor(kind, hp, fp): Int      // the same two, for the rolls
                                              // outside SkillKey; throws on SKILL
ActiveDefenses.everyDefence(traits, stunned, shocked, poolPenalty): Int
VitalRolls.due(hp): List<VitalCheck>          // the HP table's own two rolls
VitalRolls.bonusFor(check, traits) / .targetFor(check, ht, traits, poolPenalty)
TraitEffects.consciousnessRollBonus(traits): Int   // Hard to Subdue, 0 HP roll
VitalRolls.printed(check): String             // CONSCIOUSNESS at 0 HP, SURVIVAL at -HP
StunRecovery.stunsOn(row) / .printed() / .bonusFor(traits)
StunRecovery.isStun(recovery)                 // the one authority on the word
FrightCheck.recoveryFor(effect, kind): FrightRecovery?   // null in 6 of 9 bands
FrightCheck.recoveryBonus(recovery, traits)   // Combat Reflexes, Stunned only
FrightCheck.levelFor(recovery, will, iq) / .takesWillClauses(recovery)
FrightCheck.durationFor(effect, kind): ComputedDuration?   // row 12 only:
                                      // the one duration with a stat in it
FrightCheck.secondsFor(duration, ht, will, iq) / .durationLine(...)
                                      // the operand is NOT the roll's
Outcome.isSuccess: Boolean                    // print a roll's OWN verdict
StunRecovery.targetFor(ht, traits, poolPenalty)   // the Major Wound Failure
                                              // row's own roll; the fright
                                              // table's Will/IQ ones are a GAP
TraitEffects.stunRecoveryHt(traits): MajorWoundHt
TraitEffects.stunRecoveryBonus(traits): Int   // Combat Reflexes, any stun
TraitEffects.deathRollBonus(traits): Int      // Hard to Kill, survival roll only
TraitEffects.defaultBonus(traits): Int        // Versatile, on a roll AT A DEFAULT
TraitEffects.held(traits, kind): List<HeldTrait>   // one row per held trait:
                                      // the book's cell and what of it is not
                                      // applied. What the Traits tab prints
TraitEffects.UNREAD                   // reported() says it of a row outside
                                      // known() that prints no self-control roll
TraitEffects.ROLLED_ONLY              // ...and this of one that does: the
                                      // roll is applied, nothing else (6ew)
Divination.levelsFor(ownDivination, ownRitual, defaultBonus): CasterLevels
                                      // the bonus goes inside the pick between
                                      // owned and default; a ritual never defaults
                                              // both via BespokeBonus, which known() reads
Attacks.modifiers(plan, skillLevel): List<Modifier>        // maneuver + options
Attacks.attackCount(plan) / .fpCost(plan) / .maxDeceptive(skillLevel)
Attacks.damageBonus(plan, fromSkill): Int                  // Strong + Boxing
Damage.forAttack(attack, st, bonus): DamageRoll?           // null = Shove
Damage.thrust(st) / .swing(st) / .attacksFor(skill) / .boxingBonus(dx, level)
Ranged.bandFor(meters): RangePenalty / .penaltyFor(meters): Int
Ranged.modifiers(band, conditions, gmValue): List<Modifier>
Ranged.meleeConditions() / .meleeModifiers(rows)   // the one row priced in reach
Wounds.locationNamed(target): HitLocation / .locations() / .damageTypes()
Wounds.multiplierFor(location, type): Double   // both tables, composed
Wounds.typeMultiplier(type) / .modifier(location) / .render(multiplier)
Wounds.majorWoundThreshold(maxHp) / .isMajorWound(damage, maxHp)
Wounds.effectsAt(location, damage, maxHp) / .thresholdFor(location, outcome, maxHp)
Wounds.cripplingRule()                // p.41, unclaimed and filter-invisible until 6di
Wounds.majorWoundFor(roll): MajorWound / .majorWounds()
ContentLoader.hitLocations() / .woundingMultipliers() / .spiritDamage()
ContentLoader.glossary()               // p.167-168, reference only; no rule
SpiritVision.grantedTo(pathway, sequence) / .formFor(pathway)  // the BLOCKS, never ch.7.6's table
SpiritVision.targetFor(per, shock, hurt) / .attempt(...) / .partsFor(...)
SpiritVision.rowFor(margin) / .rowForOutcome(outcome) / .isMisleading(row)
SpiritVision.astralColors() / .etherBody() / .pathwayDifferences() / .grants()
ContentLoader.spiritVision()          // loadWhole: six sections, not `entries`
ContentLoader.majorWound() / .shields() / .shieldRules()
Shields.all() / .rules() / .named(type): Shield
Shields.withBonus(defense, shield) / .blockFor(shield, skill, isRanged)
Shields.blockPenalty(shield, isRanged): Int
Divination.targetFor(skill, difficulty, extras) / .attempt(...): DivinationOutcome
Divination.clarityFor(roll) / .modifierTotal(rows, gmValue, repeats)
Divination.supportBonus(skills) / .spiFor(method, chosen, burned)
Divination.difficulties() / .methods() / .modifiers() / .clarities()
Divination.scopes() / .scopeNamed(name) / .penaltyFor(scope): Int
                                      // Sec. I's skill boundary; -4 for Complex,
                                      // NOT the question's difficulty band
ContentLoader.divinationScopes()
SkillRoll.at(label, base, key, source, mods, ctx): SkillRollOutcome
FrightCheck.against(will, modifier, source): FrightOutcome   // two rolls
FrightCheck.effectFor(roll) / .text(effect, kind) / .triggers()
RitualResolution.attempt(skill, weight, cor, source): RitualOutcome
RitualResolution.rollAgainst(target, source): RitualOutcome
                                      // the roll, against a target targetFor
                                      // already composed -- what a screen that
                                      // PRINTED the number must call (6eg)
Concentration.rollAgainst(target, disturbed, source)
SelfControl.rollAgainst(check, target, source)
Divination.rollAgainst(target, source, requiresCritical)
FrightCheck.targetFor(will, modifier) / .rollAgainst(target, source)
SpiritVision.rollAgainst(target, source, parts)
Detection.partsFor(...) / .rollAgainst(target, source, parts)
                                      // the same shape in seven more engines
                                      // (6eh). Each attempt(inputs) is now
                                      // rollAgainst(targetFor(inputs)) and has
                                      // NO production caller: it is the
                                      // composed convenience the tests use
RitualResolution.canPush(outcome) / .pushed(outcome, points): RitualOutcome
                                      // p.63/66's post-roll push: the same
                                      // dice against a higher target. Keeps or
                                      // DROPS criticalRoll; never re-samples
RitualResolution.targetFor(skill, weight, cor, preparation) / .criticalFor(roll)
RitualResolution.weights() / .preparations() / .preparationNamed(quality)
RitualResolution.spiFor(weight, chosen, burned): Int
Boons.granting() / .modifiers() / .groups() / .weight(): RitualWeight
Boons.penaltyForSequence(sequence): Int      // doubling, 9=0 down to 0=-512
Boons.modifierTotal(chosen, gmValue) / .spiFor(chosen, burned)
Boons.targetFor(skill, cor, sequence, chosen, gmValue) / .attempt(...)
Boons.canPush(outcome) / .pushed(outcome, points)   // ch.13 line 8933 defers
                                      // to ch.7, so ch.7's push reaches here
NonStandardPathways.all() / .pathways() / .blocksFor(pathway)
NonStandardPathways.blockFor(pathway, sequence): SequenceBlock?  // null = no such rung
NonStandardPathways.knownSequences(pathway) / .alsoPrintedAs(pathway)
Weapons.all() / .named(name) / .forSkill(key): List<CataloguedWeapon>
Weapons.levelFor(weapon, skillLevels): Int?   // the HIGHEST matching skill,
                                      // never the first: p.85 makes one name
                                      // at two difficulties two skills. Null
                                      // means carried but untrained
Weapons.parryFor(weapon, skillLevel): Int?    // ch.5's skill/2+3, no modifier
                                      // because the book prints none. This is
                                      // what BOTH screens use; ParryBlock
                                      // .parryFor is the SHEET path and has
                                      // no production caller on purpose
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
Equipment.all() / .forSkill(key) / .referenceFor(key)   // shown, never applied
                                      // an OPEN catalogue row (specializable,
                                      // no specialization) is matched by an
                                      // instance in EITHER direction: the
                                      // extractor's `joins` and `isTemplate`
                                      // read one flag in skills.json (6dv)
Equipment.UNMODELLED                  // ch.8's four unread tables, with reasons
ContentLoader.equipment()
Appearance.all() / .named(level): AppearanceLevel  // throws for an unprinted level
Appearance.traitFor(level): Trait                 // never null; no level is free
Appearance.levelOf(traits) / .applied(traits, level) / .without(traits)
ContentLoader.appearance()
Languages.all() / .common() / .mystical(): List<LanguageEntry>
Languages.proficiencies(type) / .proficiency(type, name)   // type is REQUIRED
Languages.ritualModifier(prof?): Int    // throws for Broken and for common
Languages.canPerformRituals(prof) / .ritualProficiencies() / .notes()
Languages.owned(purchases, held): List<Pair<LanguagePurchase, OwnedLanguage?>>
                                      // table price, half for one form, half
                                      // for Language Talent, ONE ceiling;
                                      // Multilingual frees the first two that
                                      // are common, Native and in both forms
Languages.traits(purchases, held) / .forms() / OwnedLanguage.costLine
ContentLoader.languages() / .languageProficiencies() / .languageNotes()
RitualResolution.targetFor(skill, weight, cor, preparation, language, mode, link, support)
RitualMode.valueFor(preparation, chosen) / .valueFor(link, chosen)
                                      // a GM's pick inside a cell that prints
                                      // a span or a floor; refused outside
                                      // its own cell, near end when unpicked
Incantations.all() / .forPowerSource(isInvoking): Incantation
                                      // ch.7's pair is selected; ch.7.5's
                                      // summoning row is shown, never chosen
ContentLoader.incantations()
RitualMode.QUICK / .FULL                      // two tables, two link rules
RitualMode.preparations() / .preparationNamed(quality) / .modifierTotal(prep, link)
RitualMode.links() / .linkNamed(name) / .supportSkills() / .supportBonus(names)
ContentLoader.ritualSupport() / .ritualQuickPreparation() / .ritualTargetLink()
Invocation.ORTHODOX_SHIFT = -2        // on the TABLE roll, never the ritual
Invocation.responses() / .responseFor(roll) / .ritualModifier(row)  // throws on 18
Invocation.shiftedRoll(roll, orthodoxInGoodStanding) / .ask(source, standing)
ContentLoader.entityResponse()
SealedArtifacts.all() / .grades() / .named(name) / .abilityNamed(artifact, name)
SealedArtifacts.gradeOf(a) / .powerEquivalentOf(a) / .agreesWithGrade(a)
SealedArtifacts.costOf(artifact, ability): ArtifactCost   // spi, hp, perTurn
                                      // deliberately NO canUse: grade is not a gate
ContentLoader.sealedArtifactGrades() / .sealedArtifacts()
AntiDivination.rows() / .counters() / .counterNamed(name) / .rowFor(sequence)
AntiDivination.halvedMagnitude(penalty, roundUp)   // by MAGNITUDE, floorDiv
AntiDivination.against(targetSequence, divinerSequence?, techniquePenalty,
                       directLink, hpSacrificed): AntiDivinationResult
AntiDivination.modifiers(result)      // Flat + RequireCrit, never one alone
Divination.attempt(..., requiresCritical)  // demotes, and re-reads clarity
                                      // NO `burned`: it added to the target
                                      // BEFORE the dice, which is not the
                                      // decision the book prints (6dy)
Divination.canPush(outcome) / .pushed(outcome, points): DivinationOutcome
                                      // a critical SUCCESS is pushable here
                                      // and a rolled 17-18 is not, on a ritual
ContentLoader.sequenceImmunity() / .antiDivinationCounters()
```

`RandomSource` is a `fun interface` — inject a deterministic one in tests, a
real RNG in the app. Never call `Random` directly in rules code.

**Do not enable release minification without adding `kotlinx.serialization`
keep rules first.** `app/build.gradle.kts` sets no `minifyEnabled` and there are
no `.pro` files, so R8 is off and every `@Serializable` content class is safe
today. Turning it on strips the generated serializers and breaks all 82 content
files at once, at runtime, on the first screen that loads one — a class of
failure no test in this repo can reach (6cz).

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

- **`SavedEdits` now covers everything a player changes** — the four
  attributes, bought skills, wealth, appearance, traits (fixed-price and
  instanced), the six priced secondary characteristics (6ea), the weapons
  carried (6eb) and languages (6er). Widen it ADDITIVELY: `SessionStore.load` discards the whole
  file on a decode failure, so a shape change drops every existing save.
- **A SHEET-printed weapon row is a stated non-goal.** `Character.weapons`
  and `ParryBlock.parryFor(Weapon, …)` have no production caller: no
  published sheet prints a weapon row, and the weapons a player carries are
  chapter 8's catalogue, priced by `Weapons.parryFor`, which takes no
  modifier because the book prints none. Both are kept, with the formula
  pinned by a test, for the provenance that does print one (6eb).
- **Advantage and drawback rows are prose, not structure.** The flattened PDF
  gives no boundary between a wrapped effect line and the next row's name, and
  this PDF reports x=0 for every glyph so column geometry is unavailable. See
  `tools/extract_pathways.py`'s docstring before trying again.
- **The screens themselves are still device-only.** `app/src/test` now covers
  `BeyonderSession` and `SessionStore` (22 tests), which is where both
  app-level defects have landed so far. Composables are not tested; that
  needs `androidx.compose.ui.test` and a reason better than coverage.

---

## 6a-6ev. The slice log

**Moved to [`docs/SLICE-LOG.md`](docs/SLICE-LOG.md)** — 454 KB of it, which put this
file over the 150 k-char limit. Every slice from 6a to 6ev is there unchanged,
and the reading order is the one at the top of this file: **from the END**.

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

**Codex is the review gate now** (`~/.claude/CLAUDE.md` lists the invocation). In M4 it
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
