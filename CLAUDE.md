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
about this app, and one grep per row is what checking it costs (6dy). And on
a fresh clone the regeneration order is `extract_pdf.py`, then `coverage.py`,
then the extractors, because `coverage.py` writes `build/extract/book.txt`,
the anchor every `sourceLines` number is read against (6ed).

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

**6ei is newest, and it is the first claim in this chain produced on a
device.** 6eh named a stale check on Drink because "the digestion stepper is
on the same screen" — **false, and never checked**: the tracker is drawn on
the current potion's block and Drink on the next. The design review refuted
it from two lines and found the real defect beside it: **Drink had no
re-entrancy guard, alone among the roll buttons.** On an SM-T570, with the
guard neutered, two taps from one `monkey --throttle 0 -f` script killed the
app in `drink`'s own `require` — *"…is not the next potion after…"*. Read it
for four things. **That settles the premise 6ef, 6eg and 6eh rested on**: two
taps a few milliseconds apart do reach one composition's handler, and the
comment at `MainActivity.kt:1568` saying *"the two taps are two frames"* is
false — its refusal was right and its reason was not. **It does not settle a
finger**: monkey's taps are milliseconds apart, a human double-tap about a
hundred. **Two injectors failed first** — parallel `input tap` is too slow,
and writing `/dev/input` is refused by SELinux and was left alone. **The fix
is two questions on the session**, pulled apart by the two reviews:
`stillShows(printed, printedCor)` for Drink, because a CoR step and an early
advancement in one frame can end a character, and the looser
`onSamePotion(printed)` for the CoR steppers, because a Drink can LOWER the
ceiling they were drawn against while a digestion step must not drop the next
tap. `drink` is unchanged; the session still trusts no number from the
screen. Seven mutations, all caught. **Device-verified on the SM-T570**: the
crash before, one clean drink after, two fast CoR taps both counted — and
the ritual dialog and all seven of 6eh's at last on hardware (`3d6 ≤ 15`,
then `14 vs 15`; the seven are tabled in the log). `onSamePotion` was written
after the diff review and has two mutations and a grep behind it, no reviewer.
**No successor is named.**

**6eh is next, and it is the slice 6eg's corrected census named: seven
more rolls that printed a target and then composed it again.** Boon,
Detection, Divination, Concentration, self-control, Spirit Vision and the
Fright Check. Each engine gains `rollAgainst`, its older `attempt` is that
roll with the target composed first, and each handler passes the value on
screen; Detection and Spirit Vision pass the breakdown they printed as well,
so Detection's caption is the result's own itemised list now. Read it for
four things. **The design review changed three of the seven before any
code** — my sketch dropped Concentration's `disturbed` guard off the path
production takes, and passing a target without its breakdown was *"cosmetic"*.
**The potion roll is left, and its window is open**: the review's
session-held prepared target is a cache and is refused, but my first reason
for leaving the pair ("one predicate, in the engine") was equally true of
Divination before this slice. What differs is that the session owns PERSISTED
state and must not trust a number from the screen. **This paragraph then
claimed the `±5%` stepper and Drink shared a screen; they do not (6ei).** **`attempt` is test-only API in eight engines now**, and
that is what the mutations found: the diff review proposed seven by reading,
**five survived the whole suite when run**, three of them forwarding bugs in
wrappers production no longer calls — which mislead only the suite, and the
suite is what reads them. The other two were on the production path, and one
is the finding of the slice: **no test had ever rolled a Spirit Vision
success through the engine**, so reading it by outcome instead of by margin
returned the bare row for every margin. **And one "survived" was not a
survival** — a half-applied script left two tests unchanged, and `BUILD
SUCCESSFUL` under a mutation says the guard did not fire, not that it exists.
**A regression pass on the emulator, all seven** — it proves no fix,
since the mismatch was never produced on a device; printed and rolled are the
same number in each (`vs 6` → `11 vs 6`, `≤ 7` → `9 vs 7`, `≤ 10` → `5 vs 10`,
`10 or under` → `10 vs 10`, `≤ 14` → `10 vs 14`, `≤ 11` → `13 vs 11`, `≤ 9` →
`12 vs 9`). **The successor is a stale check on Drink** — built in 6ei, for a
different reason than the one given here.

**6eg is next, and it reverses the fix 6ef shipped a day earlier.** 6ef
closed one stale-state window in the ritual Roll handler by reading the pick
at click time, and recorded two more beside it. **Reading state later cannot
close them**: a handler belongs to the composition that drew it, so after a
mode tap it reads the new `mode` beside the OLD composition's `remember(mode)`
row, and no click-time read reaches the new objects. The consistent pairing
runs the other way — roll what was printed. `RitualResolution.rollAgainst
(target, source)` is what `attempt` did after composing its target, `attempt`
is that roll with the target composed first, and the handler is one line
passing the composition's own `target`. **The warrant is not the race**: the
dialog composed its target at two call sites from the same fifteen arguments,
and 6ef's device pass had written down that nothing but the screen pinned
them together. Read it for three things. **The census**: eight rolls print
a target and then compose it again to roll — Ritual, Divination,
self-control, Boon, Detection, Concentration, Spirit Vision and the potion
roll (whose two sites are in two FILES) — and the Fright Check writes its sum
in the screen and again in the engine. Ritual was the only one that could
throw. **Its first draft listed seven and called the rest "available, not
owed"**: the header query returned two rows the table left out, and "a
mismatch nobody has seen" is a drift argument the owner's own rule rejects —
*the same predicate at two call sites is the defect.* **A comment at `MainActivity.kt:
1566` says "the two taps are two frames"**, which is this slice's premise
denied; neither is reproduced and the slice does not depend on which is
right. And **the fix made its own test's equality blind**: `attempt ==
rollAgainst(targetFor)` cannot see a draw-order change once one calls the
other, so the literal numbers beside it are what pin the order — the review's
LOW was that `scripted(…, 4, 4, 4)` repeats its last face and hides a
discarded draw (4, 4, 3 now, RED-proved). One behaviour changes by contract:
an unspeakable language tapped in the same frame as Roll used to throw and
now rolls the number on screen. Design review before code, diff review before
commit, both through `codex exec`. **Device-verified on the emulator**: `3d6
≤ 15` then `8 vs 15`; `3d6 ≤ 16` then `9 vs 16`. **The successor is the
seven pairs left and the Fright Check's inline sum** — built in 6eh.

**6ef is next, and it builds the slice 6ee named: the GM picks inside a
ritual cell that prints a span.** Six cells across chapter 7's three tables
(`Good +1 to +2`, `Poor -1 to -2`, `None -4 or worse`, the quick table's
`Poor / None -2 to -4`, the links `Strong +2 to +3` and `Weak +0 to -1`)
showed the span on the chip and applied its near end. `RitualMode.valueFor`
holds a pick to its OWN cell — +3 is Excellent's and is refused for Good —
and the pick is threaded by name through `modifierTotal`, `targetFor` and
`attempt`. **Unpicked is still the near end, and that is this app's default,
not the book's rule.** Read it for four things. **Its warrant had named two
precedents and neither held**: "the gentle reading the darkness row and the
fright triggers already settled", where one has a picker and the other a
stepper. **A third review, after the commit, found a throw two earlier reviews had
cleared — and both had answered their question correctly**: the picks were
`remember(mode, preparation)` under a note calling that key "load-bearing",
the design review was asked whether pick and row can disagree *"for one
composition"*, and they cannot. **A keyed `remember` resets one recomposition
late while a click handler reads state as it stands**, so Good's +2, a tap on
Excellent and a tap on Roll inside one frame hand the engine a pick it throws
on. Read off the code, never reproduced. The pick is held WITH its row now
and filtered on read (`pickInside`), which is 6dz's design — and the fix the
design review had itself written down as its strongest objection; a pick
therefore comes back when the GM returns to its row. **Ask a reviewer about
the handlers by name, not the compositions.** **That finding indicts its
model and the model is NOT repaired**: the Roll handler still passes
`preparation`, `link` and `flourish` (`remember(mode)`) beside a fresh `mode`
and a composition-time `declaration` beside a fresh `complexity`. Same
two-finger window, no more or less reachable than the one fixed; the pick was
fixed because its fix was one function. The query and the 43-hit census are
in the slice log.
**Six mutations were executed rather than predicted**, all caught — and the
first baseline run failed on Gradle's file lock, which looked exactly like a
caught mutation. The review's LOW (the golden test reaches `valueFor`, not
`attempt`) is refused on the reviewer's own next clause. **Device-verified on
an EMULATOR** (`Medium_Tablet`, API 35 — no hardware attached): Good at +2
took `3d6 ≤ 13` to `≤ 15` and rolled **`15 vs 15`**, a success the unpicked
+1 misses; quick mode at -3 with a Strong +3 link printed `preparation -3,
+3 strong link` and rolled `15 vs 13`. **And this paragraph first said no
decomposition review was on record**: it was checked against the commit body
and the slice log, and `~/.codex/sessions` held a design review and a diff
review from the hour before the commit. The nearest thing to a successor is
the two handler windows — **which 6eg closed, by undoing this slice's
click-time read.**

**6ee is next, and it ran 6dl's own advice as a sweep: every diagnostic the
toolchain gives for nothing.** Clean on eight instruments, each recorded with
its control — and **the compiler's zero had to be earned twice**: the first
run restored one compile task FROM-CACHE, which prints nothing, and a planted
control showed this compiler reports `x is Int` and a doubled null check but
**not `val unused = 1`**, so its silence says nothing about a value computed
and dropped. Two crude scanners cover that blind spot (1160 parameters, 1736
locals, every hit a false positive or a documented choice). **Nothing an
instrument flagged was a defect; two things beside their hits were.** Four
lines above a dead binding, `assert "0.0%" in report` sat under a comment
false since M1 — and `"100.0%"` contains `"0.0%"`, so it could not fail. And
one of the local scan's false positives was a **fourth inline copy of
`endsBetween`** in `RollDialog`, under a KDoc saying *"Three of this book's
modifier tables print a span"*: the same list by a different spelling, one
call now. **A count in a KDoc is a claim about every other file** — and I
then corrected "three" to "four" by counting CALLERS, which was the same
mistake: **eight content files store a second bound**, and a span the screen
collapses leaves no inline copy for a grep to find. Read it
also for the Gradle lock's third costume (`Failed to load cache entry`), for
why deleting the module's whole `build/` beats deleting the named directory,
and for a harness line reading `exited with code 0` over `BUILD FAILED` —
grep the log for `BUILD SUCCESSFUL`. **The successor is named with its
evidence: chapter 7's three ritual tables print five spans (`Good +1 to +2`,
`Poor / None -2 to -4`, `Strong +2 to +3`…) and `RitualDialog` applies the
near end of each with no picker.** `RitualMode.modifierTotal` takes no
override, so 6dz's parameter sweep could not see it; its warrant (6q: "the
same gentle reading as the darkness row") died when 6dz gave darkness a
picker; and `GoldenRitualTest` already accepts the book's worked rituals at
`it.best`, values this dialog cannot produce. **6ef built it.**

**6ed back to 6de had lead paragraphs here until 2026-10-08. Each is in
[`docs/READING-ORDER.md`](docs/READING-ORDER.md) verbatim, and in full in the
slice log.** One line each:
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
absolute names the corpus it was checked against; **6cz** — an unavailable review gate is a reason to reach for the next
gate, not to record its absence and ship; **6cy** — `pts/level` named two
different tables, so six constants billed every character with no tie to a
printed line; **6cx** — a precedent travels by ROLE, not by shape; **6cw** —
chapter 8 was at 21.2% with an equipment catalogue nobody had read; **6cv** —
a predicate is a claim about what a null MEANS; **6cu** — a gap has a
DIRECTION and the direction is the evidence; **6ct** — a grep for a key is not
a count of a value; **6cs** — a refusal shipped instead of a feature, on a
design review's NO-SHIP; **6cr** — a quotation and a restatement look
identical in a grep; **6cq** — a comment is not a pin; **6cp** — a refusal can
survive its author's stated reason and die of a fact none of its words
mention; **6co** — a condition can outlive the question it stood in for;
**6cn** — a spec that omits a label still gets one; **6cm** — a precedent
travels with the condition that produced it; **6cl** — a recorded defect is
evidence about the inputs it was measured on; **6ck** — one false sentence
copied into three files reads as corroboration; **6cj** — a sweep cannot see
the journal, and provenance cannot answer a mechanism objection; **6ci** — a
census settles what an argument cannot; **6ch** — two reviews can contradict,
and a LOW that would reintroduce a MEDIUM is not a LOW; **6cg** — a pin runs
in one direction; **6cf** — sweep the API for callers, not just data for
readers; **6ce** — one grep can decide a reading two other readings would have
authored; **6cd** — cost-inside-bounds corroborates pricing validity, not
catalogue identity.

**Where to start next: the slice log, from the END — 6ei first.** The
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
builder (attributes, secondary characteristics, wealth, appearance, traits,
weapons and bought skills, against a live point budget). `docs/HOW-TO-PLAY.md` is the
player-facing guide to all four and is verified against the labels in source.

| | |
|---|---|
| Tests | **1753 Kotlin** (JUnit 5 — 1242 `core:rules`, 491 `app`, 20 `core:model`) + **357 Python** (pytest), all green — counted from the JUnit XML, not from memory. **This row was stale by two on the Python side before 6dg**: it read 300 where `HEAD` carried 302, so re-count rather than adding your delta to what it says. A filtered run (`--tests`) leaves the results directory holding only what it ran, and Gradle then reports the unfiltered task UP-TO-DATE — count after a `--rerun-tasks`, or you will read the filter's leftovers (measured in 6dg: 489). **The `app` module writes to `build/test-results/testDebugUnitTest/`, not `.../test/`**: a glob over `test/` alone silently drops all 466 and still reports a green total (6dc). The `app` jump from 92 is `ThemeSchemesTest`, which sweeps every `ColorScheme` role by reflection and so emits one dynamic test per role per preset per mode |
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
`SkillRoll`, `SuccessRoll`, plus `content/ContentLoader` reading all 81 JSON
files from `core/rules/src/main/resources/content/` — **every one of them has
a loader as of 6dh**, which closed `spirit_vision.json`, the last file no
Kotlin named. Re-run that sweep rather than trusting this sentence; the query
is in 6dg: `skills.json` (158),
`advantages.json` (99), `disadvantages.json` (99), `thrust_swing.json` (30),
`pathways.json` (88), `acting_method.json` (5), `potion_consumption.json` (11),
`cor_gained.json` (6), `ritual_cor_thresholds.json` (4),
`pool_thresholds.json` (9), `fright_triggers.json` (5),
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
ParryBlock.unarmedParry(style, skillLevel): Int
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
SkillLadder.defaultFor(difficulty, attributeLevel): Int?
SuccessRoll.against(target, source: RandomSource): RollResult
ContentLoader.skills() / .advantages() / .disadvantages() / .thrustSwing()
ContentLoader.pathways() / .actingMethod() / .potionConsumption()
ContentLoader.corGained() / .ritualCorThresholds() / .poolThresholds()
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
Corruption.maxFor(c) / .maxFor(will, traits): Int          // Will, +3 with Spiritual Fortitude
Corruption.clamp(cor, max) / .isLostAt(cor, max)
Corruption.ritualPenalty(cor) / .ritualThresholdsReached(cor)
Pools.hp(current, max) / .fp(current, max) / .spi(current, max): PoolStatus
Pools.thresholds(pool): List<PoolThreshold>   // empty for FP: prose only
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
today. Turning it on strips the generated serializers and breaks all 81 content
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
  instanced), the six priced secondary characteristics (6ea) and the weapons
  carried (6eb). Widen it ADDITIVELY: `SessionStore.load` discards the whole
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

## 6a-6ei. The slice log

**Moved to [`docs/SLICE-LOG.md`](docs/SLICE-LOG.md)** — 454 KB of it, which put this
file over the 150 k-char limit. Every slice from 6a to 6ei is there unchanged,
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
