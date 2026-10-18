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

**A mutant's catch is read from fresh XML.** Delete the test-results
directory before each mutant and count `e:` in the log: one that did not
compile left the previous mutant's failures in place, and read as a catch
(6fi).

**A new production reader of a trait by name must add it to
`TraitEffects.known()` in the same commit.** Before 6es a trait missing
from that set made the sheet silent. Now `reported()` prints
`TraitReport.UNREAD` for it, *"this app applies nothing this row prints"*,
beside a rule that is applied. Nothing enforces this: the census tests name
the readers that were found (Night Eyes, Spiritual Fortitude, the language
pricing pair) and cannot see the next one.

**Every throw reaches the roll log, and nothing enforces it** (6ht).
A roller reports through `RandomSource.thrown`: `SuccessRoll.against`,
`threeDSix()`, or an explicit call as `Damage.roll` makes. `RollThrowsTest`
names today's five rollers and cannot see a sixth that rolls raw dice. A
roll site takes its dice from `LocalDice` or `rollLog.dice`; `LocalDice`
has no default, so a roll site outside `App`'s provider throws on open.

**A handler that rolls, charges or sells guards its own second tap.**
Two taps a few milliseconds apart both reach the handler of the
composition drawn before the first (6ei), so what it captured is the
picture as drawn. A one-shot returns if its result is already there. A
repeatable one returns if the live state is not what it was drawn with.
A push row calls `EffortBurn.pushed`, never `applied`. A stepper is
meant to repeat, and a setter handed an absolute value is safe. Nothing
enforces this: 6gb found the unguarded ones by grepping every click
lambda that reaches a session mutator, and its screen guards have no
test.

**Every roll at a default takes Versatile's +1, and goes through
`SkillDefaults`, or through `Divination.divinationLevelFor` for Divination
Arts.** Four do: the Skills tab's untrained list, the Attack dialog's
untrained rows (6eu), the Defend dialog's Acrobatic Dodge (6ev) and the
Divine tile. Versatile's sheet note was removed on that count (6et), so a
fifth consumer that skips the +1 leaves the trait silently short. A
Warrior's weapon at DX is not a default and
takes none, and neither does Medical Training's Physician at IQ-2 (6gy).

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
REACH and all six per-round values now sit in one composable. **6hs found that composable was the dialog**: closing it forgot a Retreat, so `App` holds the round per character now (`SheetScene`). **A refusal can
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
CLAUDE.md or docs/SLICE-LOG.md" instruction in one prompt. A prompt over ~32 KB must
reach `codex exec` on stdin (`-`), or it exits 126 (6fg). **Codex was out
until 2026-10-18 00:24** (6ic) and answered at 01:48 that day.

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

**What the redesign's retired leads carried that a new screen needs
(6gd, 6gl, 6gn, 6gp).** The owner rejected text walls: a row is a name, a
number and a tag, and a dialog's reference prose sits closed in `RuleNote`,
never a target, a cost or a refusal. A roll's button sits in a pinned
footer, and its handler moved there unchanged. A control that appears on
the first pick moves the rows under a finger: draw it from the start. The
AlertDialog text slot stacks bare children: wrap two in a `Column`. A
ledger roll resolves its target while it is drawn, so every `require`
behind a chip throws on the screen, where it has no way out: trace each
chip handler first. Attack's tracker line is read off the ledger by id,
never as a remainder. Scan the click-handler census from the repo root:
the rule's `files:` glob is relative, and a scan elsewhere reports nothing.

**6gy back to 6de had lead paragraphs here until 2026-10-12. Each is in
[`docs/READING-ORDER.md`](docs/READING-ORDER.md) verbatim, and in full in the
slice log.** One line each:
**6id** — Case File slice 2, the shared components: the verdict a tilted stamp, the dialogs' form codes, Close in stamp red; pool pips on one Canvas, where a BoxWithConstraints crashed;
**6ic** — Case File slice 1: one generated light scheme replaces every preset, stamp red #BC382A; Fraunces and Plex; a 600 dp column; roll sounds and ripple reverted;
**6ib** — the shield bash, a row per shield at Brawling-2; from the front a shield's figure is the only one, and its block penalty stands without its DB;
**6ia** — play session 6, Codex's scenario in landscape: a dialog body holds one height, so a verdict, search or pick moves no control;
**6hz** — play session 5, combat: a critical hit doubles its rolled damage, a reading; a held stun rolls recovery from a Vitals row; a rolled parry counts;
**6hy** — play session 4, the campaign arc: nine findings; one caption, `SuccessRoll.printed`; no push past *Success by 3+*; Will and Per roll from their Vitals rows;
**6hx** — Detect takes Spiritual Attrition's -3 only on a roll an awareness row grants; a roll on suspicion alone takes none, the owner's ruling;
**6hw** — play session 3: a typed spirit hit multiplies on FP and SPI, the location on HP only; Spirit Vision takes Spiritual Attrition's -3, read after activation;
**6hv** — play session 2: shock leaves the daily recovery roll; the skill stepper steps rung to rung; a hit priced by type, floored as a reading;
**6hu** — own characters are save entries naming their premade; study points ride on `points` with a `studied` share the budget subtracts;
**6ht** — reset to sheet rebuilds the session from an empty save; a roll log fed by one `RandomSource.thrown` hook, dice and target, never a verdict;
**6hs** — play session 1: twelve device findings, two of them state a `remember` lost, one 6cp's once-per-round claim; the revolver fires on Guns (Pistol);
**6hr** — the ritual's fuel: No response's 2 SPI comes out of declared HP first, as the prayer's; an invoked ritual pays at the asking; `Invocation.fuelFor` is the one price;
**6hq** — a prayer takes a mystical tongue and Entity Response, asked first; no response grants no boon; the fuel paid does not lower the roll; a bonus on a bought skill re-levels;
**6hp** — nine components the owner picked from three variants each: one Vitals card, steppers, section cards, a card per power, the gallery filter as tabs; display only;
**6ho** — a prayer's fuel may be paid in HP, 2 for 1 SPI, as ch. 7 IV.B prints and ch. 13 defers to: how a mortal prays; fuel only, `Boons.targetFor` untouched;
**6hn** — a boon block's catalogue rows are granted through the potion merge: Charisma +2, Acute Hearing, Acute Vision, Fit; five look-alike names are refused, each printing another rule;
**6hm** — a granted boon stored nothing; it is `SavedEdits.boon`, digested after the potions; the rung is the next printed, a patron required;
**6hl** — a potion's abilities are rows that open, the bracket a tag, Success and Failure under their power; display only; 6gk's "the book does not say which" fell to a census;
**6hk** — Charmed Object's skill rides in the held trait's name: a declared +1 on that skill's roll and in Attack; 6gv's "a choice the save would have to store" was the book's instruction;
**6hj** — the unread census at 112 rows; Cannot Kill offers Reluctant Killer's Will roll, the one cell naming another row's; Marked by Ritual stays refused;
**6hi** — three standing sweeps re-run; the builder's study tag read the bought attribute beside a blessed level;
**6hh** — Dead Language Fluency frees the first of Ancient Hermes or Feysac at Native, both forms; Threshold Blindness stays refused;
**6hg** — Reckless's -1 is a sixth declared all-rolls chip, on the owner's ruling; Entity Fixation's -2 stays refused;
**6hf** — God of Combat's +3 ST, DX and HT is a played gain, never billed: one blessed base for `Derive.of` and digestion, and purchase ceilings three lower for a holder;
**6he** — four owner answers: a sheet's "Secret (...)" is the Secret row; Hanged Man 9's drawback is stored in `BeyonderState` and granted free; the untrained parry stays refused;
**6hd** — the device pass 6gz, 6hb and 6hc owed; the Defend dialog printed the unarmed cell's notes under the wrong row;
**6hc** — the Damage dialog summed the stun recovery target by hand; it calls `StunRecovery.targetFor`, which 6gs had left two terms short; 979 functions censused for callers;
**6hb** — the Fright dialog gated Combat Reflexes' +6 itself; `FrightCheck.recoveryBonus` is its one copy now;
**6ha** — no code: a comment-and-literal sweep 6gz could not see; fifteen stale comments and one guide sentence went;
**6gz** — an untrained weapon's parry is refused on the two formula cells, not "a gap"; eight stale comment claims about this app went;
**6gy** — Medical Training's Physician at IQ-2, which the untrained list had offered at IQ-6 since 6et; no Versatile +1;
**6gx** — no code: the unread census over both halves, 114 rows outside `known()`; 6gu's count had missed nine self-control rows;
**6gw** — Anchored Soul refused: every "against you" the book prints is the other party's attempt, so it is not Incorruptible Will's mirror; the chain capped at five;
**6gv** — Perceptual Splitting's -2 declared on the sheet and scoped to the rolls line 390 calls physical; the ledger asks `EffortBurn.isPhysical`;
**6gu** — Light Sleeper's +4 declared on a Per skill and on Detection's Per branch, never Spirit Vision; Skinny refused as a purpose;
**6gt** — no roll changed: the PCR takes no carried state, on the book's "Only the drinker's digestion ... matters", and no row names the roll;
**6gs** — five "-N to all rolls" drawbacks declared once on the Vitals tab, reaching every roll shock reaches and the Major Wound HT roll; Phobia's and Soul Scar's refusals expired;
**6gr** — Tobacco and Guilt's "-1 to Will" read as a Will-roll clause, a reading recorded as one; Bound to a Place refused, a count of weeks;
**6gq** — no code: every restyled charge handler kept its second-tap guard, double-tapped beside a mutant RED; the pinned Drink button refused, as it sits above the fold;
**6gp** — six more roll dialogs take the approved components, `RollFooter` and `VerdictCard` new; three keep their own verdict, each for a rule;
**6gn** — the redesign: nine Stitch components over Wada #269, one a commit; Ritual's Cast sat off the tablet's screen, and Stitch's inventions were listed and none copied;
**6gm** — the builder: a skill row is name, level, points and a study tag naming the refusal's cause; the trait picker's tap opens a row and never buys;
**6gl** — the Traits tab is a name, a price and a tag per row, `TraitReport.applied`, which says no less than the open row's notes; the header is tags;
**6gk** — the potion text is verbatim, but three grants naming no listed skill had reached no screen; stored and shown now, and `abilityParagraphs` is display only;
**6gj** — Absent-Mindedness's -3 is declared on every skill roll; Voice and Industrial Expertise are refused on Healer's reason, one constant, `PURPOSE_NOT_SKILL`;
**6gi** — with no shot called the damage line read "×1.5 there"; it says *there* only for a called location, and the trait backlog is tabled: 125 unread rows;
**6gh** — the speed refusal counted a flag and the swap filtered on it; one `Ranged.oneOf` answers both, and "behaviour-preserving" was false for a row with both flags;
**6gg back to 6de** have one-line digests too, moved verbatim to
[`docs/READING-ORDER.md`](docs/READING-ORDER.md) to keep this index under
its 8,000-character cap (6ff, 6gb, 6gd, 6gw, 6ha, 6hb, 6hc, 6hd, 6he, 6hg, 6hh, 6hi, 6hk, 6hm, 6hq, 6hr, 6hs, 6ht, 6hu, 6hv, 6hw, 6ib, 6ic, 6ie, 6ih, 6ii).

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

**Where to start next: the slice log, from the END — 6ii first.** END
means the newest slice, not the end of the file: 6a to 6by run down to
line 6728, then 6ii back to 6cb, newest first, so `tail` prints 6cb. 6ii:
(Case File slice 5, the ten roll dialogs from frames 13-22; three rulings
asked first: each pick's description folds into a closed note, Defend
keeps every control, Damage no default location; `CaseChip` one width
picked or not; footers that wrap at 390 dp; double taps charged once on
the SM-T570; Codex's three MEDIUMs fixed, its re-review owed). Then 6ih
(Case File slice 6, the pathway screen from frames 09-12, read from the
HTML renders when the Figma MCP hit its plan limit; three rulings asked
first: Drink offers the next potion on every rung, so every guard reads
`drinkTarget`; the CoR ladder's four rungs under "From ritual work"; the
hub legend left a note; a wrapped heading and three phone-width faults
fixed on the SM-T570; Codex SHIP). Then 6ig
(Case File slice 4, the gallery, New character and the glossary from
frames 01, 05 and 07; four rulings asked first: a lost character's row
red, the blank-sheet button kept, letter groups in book order, a
template filter; the frames' "at 10" and "Appendix B" corrected to the
data; Codex's HIGH, a list key a name could repeat, fixed). Then 6if
(the owner's "be truthful to the figma screen": the sheet rebuilt against
frames 02-04 and 08 pulled from Figma; 6ie's skipped list was the
defect; four rulings asked first, A-Z still out; `fileNumber`, pools in
two forms, the amended table, "Show amendments"; 6id's three device
checks done on the SM-T570). Then 6ie
(Case File slice 3, the sheet: the name a Fraunces heading out of the top
bar, the pathway the verdict's stamp, a loss a filled red pill; "§"
heads, boxed levels, ruled trait rows; FILE Nº deferred to slice 4's
numbering; no device pass, the tablet off adb all session). The chain
holds the five newest: a slice that adds a link retires the oldest to a
digest above, its sentence to `docs/READING-ORDER.md` first. The
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
(glossary in its top bar) -> sheet (the `computed vs asPublished` diff, a
Vitals/Skills/Traits bottom bar, and an action hub), the pathway
screen (potion grants, digestion tracker, Potion Consumption Roll), and the
builder (attributes, secondary characteristics, wealth, appearance, languages, traits,
weapons and bought skills, against a live point budget). `docs/HOW-TO-PLAY.md` is the
player-facing guide to all four and is verified against the labels in source.

| | |
|---|---|
| Tests | **1988 Kotlin** (JUnit 5 — 1481 `core:rules`, 484 `app`, 23 `core:model`, counted in 6ii) + **366 Python** (pytest), all green — counted from the JUnit XML, not from memory. **This row was stale by two on the Python side before 6dg**: it read 300 where `HEAD` carried 302, so re-count rather than adding your delta to what it says. A filtered run (`--tests`) leaves the results directory holding only what it ran, and Gradle then reports the unfiltered task UP-TO-DATE — count after a `--rerun-tasks`, or you will read the filter's leftovers (measured in 6dg: 489). **The `app` module writes to `build/test-results/testDebugUnitTest/`, not `.../test/`**: a glob over `test/` alone silently drops all 466 and still reports a green total (6dc). The `app` count moves with `ThemeSchemesTest`, which emits one dynamic test per `ColorScheme` role and per contrast pair: 6ic's one light scheme took `app` from 892 to 431, and 6ic's guards to 433, 6id's `PoolPipsTest` to 437, 6ie's `SheetHeaderTest` to 442, 6if's six more to 448, 6ig's `GalleryFileTest` to 463, 6ih's `PathwayDrinkTest` and `WholeWordsTest` to 476, 6ii's `CaseChipTest`, `RollFooterTest` and a `fileLabel` case to 484 |
| Open issues | **Zero.** OPEN-1 was fixed in 6ar; the Codex sweep's 17 findings are all closed or refused with evidence (6as, 6at) |
| Review gate | **Codex, always** (the owner, 2026-10-17: "use codex instead of ecc always", reversing 2026-10-10's ecc). Through `codex exec` with the target named (6eg). When Codex and pi-qa are both down, one review sub-agent, never ecc. 6ic: Codex reviewed Wabi, the plan, `58119c4`, `cb75e32` and `4d891e5`, then hit its limit; a sub-agent reviewed `a9be8f0` and `5148cb1` (SHIP WITH CHANGES, taken); 6id's, 6ie's and 6if's sub-agent reviews returned SHIP WITH CHANGES, the findings taken. On 2026-10-18 Codex (`codex exec`, gpt-6-astra, xhigh) reviewed the whole owed range `a9be8f0~1..d0f2299` under `app/` (generated `ThemeSchemes.kt` and fonts excluded; nothing outside `app/` changed): SHIP, no findings on handlers, composition crashes or printed figures. Nothing owes Codex a review; rotation stays device-unverified. 6ig: Codex NO SHIP, its HIGH taken (`5a36735`, re-reviewed SHIP), its MEDIUM refused with the line that disproves it. 6ih: Codex SHIP, no findings. 6ii: Codex returned three MEDIUMs on `d7e1a47`, all taken in `7f0ac87`, whose re-review is OWED (stopped for low memory). Earlier gates are in each slice's Review section |
| Branch | everything on `master`; working tree clean |
| Toolchain | Kotlin 2.2.0, JVM toolchain 17, Gradle 9.3.1 (wrapper committed) |

### Commands

```bash
./gradlew test                    # all Kotlin tests, app module included
./gradlew :core:rules:test        # one module
py -m pytest tools -q                # Python tooling tests (Windows: `py`, not `python`)
ast-grep scan                     # 4.3's division rule; silent and exit 0 when clean
ast-grep scan --rule tools/sg-census/session-mutator-in-lambda.yml --report-style short
                                  # 6gb's census: 40 lines (6hv), one per call site
ast-grep run -l kotlin -p 'SkillRoll.targetFor($$$)' app core   # call sites
```

**Read a span, not a file.** This prints where one function starts and ends
(`RollDialog`: `RollDialog.kt` 50-282), and Read takes it from there with an offset:
`ast-grep scan --inline-rules '{id: s, language: kotlin, rule: {kind: function_declaration, has: {kind: simple_identifier, regex: "^RollDialog$"}}}' app`.
A `fun $N($$$): $R { $$$ }` pattern misses every `@Composable`, which has no
return type. Nothing runs `ast-grep scan` for you: it is not in the Gradle
build. No file is over the 800-line cap, and the ratchet in
`tools/test_tools_regeneration.py` fails on the first; [`docs/SPLIT-PLAN.md`](docs/SPLIT-PLAN.md)
has steps 0-10 done and 8b-8c retired (both files under the cap). `graphify-out/` was rebuilt 2026-10-10 with `python -m graphify
update .` (no LLM; the interpreter is named in `graphify-out/.graphify_python`,
and the `graphify.exe` shim is refused by Git Bash). Its call edges are
heuristic: asked what calls `SkillRoll.targetFor` it returned none of the
three the pattern above finds.

**Serena** (MCP, this project only: `claude mcp get serena`) answers the same
questions through the Kotlin language server, across modules: asked for
`SkillRoll.targetFor`'s and `SkillDefaults.wieldedAt`'s references on
2026-10-10 it returned exactly the callers ast-grep finds. Its first index
takes about 90 s, and the server needs `-Xmx4G` (set in
`~/.serena/serena_config.yml`, beside an `ls_path` to a hand-downloaded
server, because Serena's own download 404s). At 3 GB with Gradle running it
died of OutOfMemoryError. It also starts a Gradle daemon to import the
project. Run `./gradlew --stop` before a session if memory is short.

Note: on this machine `python` is not on PATH. Use the `py` launcher. Gradle's
`--console=plain -q` prints nothing on success; check the exit code.

---

## 3. Repo map

```
core/model/     pure data types, no logic       (kotlinx-serialization)
core/rules/     all computation + book content  (api-depends on :core:model)
app/            Compose UI: premade list, sheet, pathway, builder
                Theme.kt applies the one Case File scheme, fonts and column;
                ThemeSchemes.kt is GENERATED by tools/gen_theme.py and
                must not be hand-edited (6ic)
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
PointBudget.report(...): BudgetReport  // bills points - studied (6hu)
PurchasedSkill(key, points, studied = 0)  // studied: earned, unbilled
PotionDigestion.isSameSkill(a, b)     // p. 86's identity, the one copy
SheetComparison.of(sheet): List<SheetRow>     // the diff: the UNEDITED sheet's
SheetComparison.secondaryRows(s, published)   // the seven rows for any
                                      // Secondaries; a screen asks it of the
                                      // PLAYED ones and pairs by label (6fz)
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
SkillLadder.stepped(bought, studied, up): Int   // the builder's step: rung to
                                      // rung, study counted, 0 un-buys (6hv)
SkillLadder.defaultFor(difficulty, attributeLevel): Int?   // ch.2's table ALONE
SkillDefaults.offeredTo(character): List<Untrained>   // what a screen asks: the
                                      // table, Brawling's DX-2, Wrestling's
                                      // none, and Divination Arts via Divination
SkillDefaults.offered(owned, attributeLevel, defaultBonus, traits) / .weaponNote(pathway, sequence)
                                      // traits: Medical Training's Physician
                                      // at IQ-2, a level and not a default (6gy)
SkillDefaults.NOT_LISTED / .PLAIN_ROLL    // what the list withholds and does not carry
SkillDefaults.attacks(untrained, carried, owned)   // the rows the Attack dialog can show an
                                      // attack for: an app boundary, not a rule
SkillDefaults.masteryFor(pathway, sequence) / .mastered(level, weaponInHand, mastery, dx)
                                      // Warrior / Weapon Master: a weapon IN
                                      // HAND is wielded at no less than DX
SkillDefaults.wieldedAt(weapon, skillLevels, mastery, dx): Int?
                                      // the level a CARRIED weapon parries at:
                                      // its best skill, floored at DX for a
                                      // master. Attack, Defend and the builder
                                      // all print it (6ey)
Attacks.atLevel(plan, level)          // the hand changed the level: drop a
                                      // Deceptive Attack it cannot afford
SuccessRoll.against(target, source: RandomSource): RollResult
RandomSource.thrown(faces, target) / RandomSource.threeDSix()
                                      // every throw reported: the roll log (6ht)
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
Blessings.played(attributes, traits) / .gainTo(attr, held)   // God of
                                      // Combat's +3 ST/DX/HT, played and never
                                      // billed (6hf)
Derive.attributes(c, walked)          // ST/DX/IQ/HT as played: the walk's,
                                      // or the blessed purchase. The fallback
PurchaseCeilings.attribute(attr, held) / .secondarySteps(key)   // the highest a
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
NaturalRecovery.dayLine(success, traits)   // what a day's roll restores, said;
                                      // Fast Healer's +1 on a success, the
                                      // GM's on a failure (6fc)
Conditions.forSkill(skill, hp, fp, spi, cor): List<Modifier>   // scoped per skill
Conditions.penaltyFor(kind, hp, fp): Int      // the same two, for the rolls
                                              // outside SkillKey; throws on SKILL
ActiveDefenses.everyDefence(traits, stunned, shocked, poolPenalty): Int
VitalRolls.due(hp): List<VitalCheck>          // the HP table's own two rolls
VitalRolls.bonusFor(check, traits) / .targetFor(check, ht, traits, poolPenalty)
TraitAttributeRolls.consciousnessRollBonus(traits): Int   // Hard to Subdue, 0 HP roll
VitalRolls.printed(check): String             // CONSCIOUSNESS at 0 HP, SURVIVAL at -HP
StunRecovery.stunsOn(row) / .printed() / .bonusFor(traits)
StunRecovery.isStun(recovery)                 // the one authority on the word
StunRecovery.stillStunned(roll)               // a turn's recovery roll failed:
                                      // the Vitals stun row (6hz)
FrightCheck.recoveryFor(effect, kind): FrightRecovery?   // null in 6 of 9 bands
FrightCheck.recoveryBonus(recovery, traits)   // Combat Reflexes, Stunned only;
                                      // the Fright dialog's one gate (6hb)
FrightCheck.levelFor(recovery, will, iq) / .takesWillClauses(recovery)
FrightCheck.durationFor(effect, kind): ComputedDuration?   // row 12 only:
                                      // the one duration with a stat in it
FrightCheck.secondsFor(duration, ht, will, iq) / .durationLine(...)
                                      // the operand is NOT the roll's
Outcome.isSuccess: Boolean                    // print a roll's OWN verdict
StunRecovery.targetFor(ht, traits, poolPenalty, shock, states)   // the Major Wound Failure
                                              // row's own roll; the fright
                                              // table's are FrightCheck's
TraitAttributeRolls.stunRecoveryHt(traits): MajorWoundHt
TraitAttributeRolls.stunRecoveryBonus(traits): Int   // Combat Reflexes, any stun
TraitAttributeRolls.deathRollBonus(traits): Int      // Hard to Kill, survival roll only
TraitEffects.defaultBonus(traits): Int        // Versatile, on a roll AT A DEFAULT
TraitReport.held(traits, kind): List<HeldTrait>   // one row per held trait:
                                      // the book's cell and what of it is not
                                      // applied. What the Traits tab prints
TraitReport.applied(row): Applied     // one word for a collapsed Traits row,
                                      // read off its cell and notes (6gl)
TraitReport.UNREAD                   // reported() says it of a row outside
                                      // known() that prints no self-control roll
TraitReport.ROLLED_ONLY              // ...and this of one that does: the
                                      // roll is applied, nothing else (6ew)
TraitClauses.concentrationClauses(traits)   // Will clauses + Thinned Veil and
                                      // Chronic Insomnia: the Concentrate roll
                                      // ALONE (6fa, 6fb)
TraitEffects.actingGains(traits) / .doublesActingGains(traits)
                                      // ch.6's Acting Method table, doubled for
                                      // Knows the Acting Method (6ez)
TraitReport.corruptionNotes(traits)   // Cleansed Spirit beside the CoR stepper:
                                      // nothing halved, and why (6fd)
TraitEffects.rerollSource(traits, state)   // Luck / Beckoning Luck (not with
                                      // Fate Debt) / the Lucky One: who may
                                      // reroll a failed PCR (6fg)
TraitClauses.skillRollClauses(traits, skill)   // every clause a skill roll asks
                                      // the GM about: social rows on a social skill,
                                      // and rows naming this skill (6ff), and
                                      // Acute Vision/Hearing on a Per skill
                                      // (6fh), and Absent-Mindedness on every
                                      // skill (6gj). The dialog's chips and
                                      // modifiers() read it
TraitClauses.declaredStates(traits, declared): DeclaredStates
                                      // the lit sheet states: .all for a roll
                                      // that is not physical, .physical for
                                      // one that is (6gs, 6gv)
TraitClauses.declaredModifiers(traits, declared, physical)   // the ledger's
EffortBurn.isPhysical(key)            // line 390: based on ST, DX or HT
TraitClauses.perRollClauses(traits)   // Light Sleeper's declared +4: a Per
                                      // skill and Detection's Per branch (6gu)
TraitClauses.visionClause(traits)     // Acute Vision alone, for Spirit
                                      // Vision's roll: a GM declaration (6fh)
TraitNames.catalogueName(printed) / .displayName(printed) / .levelOf(traits, name)
                                      // the ONE resolution of a sheet's spelling
                                      // to a catalogue row, and its level
SelfControl.borrowedRolls()           // Cannot Kill takes Reluctant Killer's
                                      // Will roll: the one cell naming
                                      // another row's (6hj)
CharmedObject.heldAs(skill, spec) / .skillOf(printed) / .clauses(traits, skill)
                                      // the chosen skill rides in the held
                                      // trait's NAME; a declared +1 on that
                                      // skill's roll and in Attack (6hk)
TraitGrants.grantedTraits(state, boon) / .withGrantedTraits(traits, state, boon)
TraitGrants.boonTraitTable() / .refusedBoonGrants()   // a boon's rows (6hn)
TraitGrants.grantedLevelsIn(merged, bought)   // the levels a potion ADDED
                                      // to a bought levelled row (6fm)
TraitGrants.grantedTraitTable() / .refusedGrants()
                                      // every catalogue row (and 6fi's sense
                                      // rows) a walked potion chain printed,
                                      // zero cost; refusals with reasons (6fk)
TraitGrants.drawbackChoices(state) / .drawbackChoiceTable()
                                      // Hanged Man 9's choose-one; the pick is
                                      // BeyonderState.drawback, granted free (6he)
BeyonderSession.canChooseDrawback(sheet, name) / .chooseDrawback(...)
                                      // refused for a row the builder sold
BeyonderSession.traitsInPlay(sheet)   // THE list the sheet's rolls and the
                                      // builder's "already held" read; never
                                      // the character, which the budget prices
Divination.levelsFor(ownDivination, ownRitual, defaultBonus): CasterLevels
                                      // the bonus goes inside the pick between
                                      // owned and default; a ritual never defaults
                                              // both via BespokeBonus, which known() reads
Attacks.modifiers(plan, skillLevel): List<Modifier>        // maneuver + options
Attacks.attackCount(plan) / .fpCost(plan) / .maxDeceptive(skillLevel)
Attacks.damageBonus(plan, fromSkill): Int                  // Strong + Boxing
Damage.forAttack(attack, st, bonus): DamageRoll?           // null = Shove
Damage.thrust(st) / .swing(st) / .attacksFor(skill) / .boxingBonus(dx, level)
Damage.onAttack(thrown, outcome)                           // a critical hit doubles the
                                      // ROLLED total, before DR (6hz)
Ranged.bandFor(meters): RangePenalty / .penaltyFor(meters): Int
Ranged.modifiers(band, conditions, gmValue): List<Modifier>
Ranged.alternativesTo(condition): Set<String>   // the other Aim row, or the
                                      // target's other speeds; the chip swaps (6gg, 6gh)
Ranged.meleeConditions() / .meleeModifiers(rows)   // the one row priced in reach
Wounds.locationNamed(target): HitLocation / .locations() / .damageTypes()
Wounds.multiplierFor(location, type): Double   // both tables, composed
Wounds.typeMultiplier(type) / .modifier(location) / .render(multiplier)
Wounds.majorWoundThreshold(maxHp) / .isMajorWound(damage, maxHp)
Wounds.injuryFor(penetrating, location, type)   // both multipliers,
                                      // floored as a reading (6hv)
Wounds.injuryTo(pool, penetrating, location, type)   // the type on every
                                      // pool, the location on HP only (6hw)
Wounds.effectsAt(location, damage, maxHp) / .thresholdFor(location, outcome, maxHp)
Wounds.cripplingRule()                // p.41, unclaimed and filter-invisible until 6di
Wounds.majorWoundFor(roll): MajorWound / .majorWounds()
ContentLoader.hitLocations() / .woundingMultipliers() / .spiritDamage()
ContentLoader.glossary()               // p.167-168, reference only; no rule
SpiritVision.grantedTo(pathway, sequence) / .formFor(pathway)  // the BLOCKS, never ch.7.6's table
SpiritVision.targetFor(per, shock, hurt, alertness, vision, states, spi) / .attempt(...) / .partsFor(...)
                                      // spi: Spiritual Attrition, an ability check (6hw)
TraitAttributeRolls.alertnessBonus(traits)   // "all Per rolls" for the two bare ones:
                                      // Detection and Spirit Vision (6fe)
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
SkillRoll.targetFor(label, base, key, mods, ctx): SkillTarget
SkillRoll.rollAgainst(target: SkillTarget, source): SkillRollOutcome
                                      // what the three ledger dialogs call:
                                      // resolved while drawn, rolled as
                                      // printed (6gd). `at` and `of` are the
                                      // two composed, for the tests
SkillTarget.lines() / SkillRollOutcome.lines(): List<LedgerLine>
ModifierSource.label()                // what moved the target, in the
                                      // modifier's own words, never its id (6ge)
FrightCheck.against(will, modifier, source): FrightOutcome   // two rolls
FrightCheck.effectFor(roll) / .text(effect, kind) / .triggers()
FrightCheck.grantsQuirk(effect, kind)  // the 13-15 band; recorded as
                                      // SavedEdits.quirks at 0 (6hv)
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
Boons.targetFor(skill, cor, sequence, chosen, gmValue, language, answer) / .attempt(...)
Boons.canPush(outcome) / .pushed(outcome, points)   // ch.13 line 8933 defers
                                      // to ch.7, so ch.7's push reaches here
Boons.ask(source) / .endsPrayer(answer)   // the patron asked first,
                                      // unshifted; no response grants nothing
Invocation.fuelFor(answer, cost, hp): Fuel   // owed at the asking, for a
                                      // prayer (6hq) and an invoked ritual
                                      // (6hr); a rejection costs nothing
Invocation.residualFuel(hp): Fuel     // the no-response 2 SPI, HP first:
                                      // the owner's reading, both dialogs
NonStandardPathways.all() / .pathways() / .blocksFor(pathway)
NonStandardPathways.blockFor(pathway, sequence): SequenceBlock?  // null = no such rung
NonStandardPathways.knownSequences(pathway) / .alsoPrintedAs(pathway)
NonStandardPathways.patrons() / .patronOf(pathway) / .chainFor(boon)
NonStandardPathways.nextRung(held, patron): BoonState?   // the next rung
                                      // PRINTED, on the patron served (6hm)
BeyonderSession.boon / .nextBoon(patron) / .grantBoon(next)
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
Appearance.played(traits) / .isSuperseded(trait, traits)   // one face: the
                                      // better of bought and granted (6fn)
ContentLoader.appearance()
Languages.all() / .common() / .mystical(): List<LanguageEntry>
Languages.proficiencies(type) / .proficiency(type, name)   // type is REQUIRED
Languages.ritualModifier(prof?): Int    // throws for Broken and for common
Languages.canPerformRituals(prof) / .notes()
Languages.owned(purchases, held): List<Pair<LanguagePurchase, OwnedLanguage?>>
                                      // table price, half for one form, half
                                      // for Language Talent, ONE ceiling;
                                      // Multilingual frees the first two that
                                      // are common, Native and in both forms;
                                      // Dead Language Fluency the first of
                                      // DEAD_LANGUAGES, the owner's list (6hh)
Languages.traits(purchases, held) / .forms() / OwnedLanguage.costLine
Languages.grantedBy(state)            // a potion's language: Ancient Hermes,
                                      // Native, both forms (6fo)
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
- `four sheets print SPI 9 and none plays it - guard against silent repair` (6ft)

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

`ModifierResolver` has **one production caller, `SkillRoll.targetFor`**
(`SkillRoll.kt:185`; it was `SkillRoll.at` until 6gd), which three screens
call: the sheet's skill roll (`SheetDialogs.kt:198`), the Attack dialog
and the Defend dialog. Rituals,
divinations, boons and the bare attribute rolls compose their own targets
and never reach it. This paragraph said *"zero production callers today.
M2 is where that changes"* until 6fu.
`ModTarget.RollClass`, `SkillTag` and `DamageSlot` are still constructed by
nothing in production.

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
  **A boon is a third** (6hm): `Character.boon`, saved as
  `SavedEdits.boon`, folded by `PotionDigestion.of` after the potions.
  **A blessing is the second played gain** (6hf): God of Combat's +3 enters
  through `Blessings.played`, never `attributes`, and lowers the purchase
  ceilings by its gain.
  **Study points are the one free gain IN `skills`** (6hu): earned over
  downtime, they land on `PurchasedSkill.points` with a `studied` share
  that `PointBudget.report` subtracts (`points - studied`), so every level
  reader keeps reading `points`. A new reader that wants "what was paid"
  must subtract `studied`; a new free gain must not reuse this field.
  **A quirk acquired in play** (6hv) is a trait at cost 0 from
  `SavedEdits.quirks`, never a priced one: it bills nothing, as a gained
  disadvantage does.
- **Sequences are cumulative.** A Sequence 8 Fool drank the Sequence 9 potion
  first. `digestThrough` folds the chain; digesting one block alone answers a
  different question.
- **Granted levels are frozen at digestion; purchased skills re-level.** A
  potion that raises DX lifts every bought DX skill (level = attribute +
  rungs) but not a level a previous potion granted, which has no points
  behind it to recompute from. The book does not settle this — it is a
  documented reading. **A bonus stacked on a BOUGHT skill does follow the
  attribute** (6hq, the owner's ruling): it has those points behind it.
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
  carried (6eb), languages (6er) and Charmed Object's skill (6hk). Widen it ADDITIVELY: `SessionStore.load` discards the whole
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
  `tools/extract_pathways.py`'s docstring before trying again. **A row that
  names a catalogue trait is now granted anyway** (6fk): a hand-written
  `GRANTED_TRAITS` table pinned both ways against the prose, with
  `REFUSED_GRANTS` for variants and a choose-one. Charisma's levels add
  down the chain and onto a bought level (6fm).
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
