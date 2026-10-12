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
CLAUDE.md or docs/SLICE-LOG.md" instruction in one prompt. A prompt over ~32 KB must
reach `codex exec` on stdin (`-`), or it exits 126 (6fg). **Codex is out
until 2026-10-14 16:44** (6fg); check the clock before trusting that.

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

**6gp: the six roll dialogs 6gn did not draw.** Self-control,
Concentrate, Detection, Spirit Vision, Boon and Divination take the
approved components, one a commit (`57e9935` to `493ccd7`), with no new
Stitch round: `TargetCard`, and two new shared pieces, `RollFooter` and
`VerdictCard`; Boon and Divination take the ritual's `RitualFooter`.
Three do not take the verdict whole, each for a rule: Spirit Vision's
reading has no verdict colour (an incorrect reading is believed), a boon
prints no Step 4 grade, and Divination colours from `effectiveOutcome`.
**The AlertDialog text slot stacks bare children**: wrap two in a
`Column`. Six SHIPs, six device passes, saves `cmp`-identical, census
32, 2070 tests.

**6gn: the redesign, nine Stitch components, one a commit.** The owner
rejected text walls. Wada #269 is the default palette, and Stitch drew one
component per prompt: skill roll, Attack, Defend, Damage, Ritual, Fright,
character card, pathway panel and builder panel, all built (`dcebd9f` to
`4bc0de1`). Read it for three things.

- **A roll's handler moves into a pinned footer unchanged.** Ritual's
  Cast sat off the tablet's screen.
- **A control that appears on the first pick moves the rows under a
  finger** (`49e2cd9`): draw it from the start, as `TargetCard` keeps one
  height.
- **The click-handler census reads 32.** Three pool lambdas became one
  at `dcebd9f`. Scan from the repo root: the rule's `files:` glob is
  relative, and a scan elsewhere reports nothing.

Stitch's inventions are listed and none was copied. Each commit got a
Sonnet `kotlin-reviewer.md` SHIP and a device pass, and the save was
`cmp`-identical after every one.

**6gm, 6gl, 6gk, 6gj, 6gi, 6gh, 6gg, 6gf, 6ge and 6gd: a ledger roll prints its target first, in words.** The skill
roll, the attack and the Acrobatic Dodge printed a level and resolved
their target inside the click: 6eg's third category, named in its
census and neither fixed nor refused for forty-odd slices. A reeling
character read *"Skill 16"*, rolled, and learned from the result that it
had been 15. `SkillRoll.at` is split: `targetFor` gives a `SkillTarget`
and `rollAgainst` rolls the one it is handed. Each dialog resolves while
it is drawn. Read it for two things.

- **Resolving in the draw moves every `require` behind the chips from
  the tap to the screen**, where a throw has no way out. The design
  review traced each chip handler before any screen was touched: none
  can fire.
- **Attack's tracker line is read off the ledger by id**, never as a
  remainder, which would absorb a term the screen forgot and still add
  up. The diff review then found my matrix test took that number from
  the function under test. It is a literal now.

`SkillRoll.at` and `.of` have no production caller. The design review
changed four things; the diff review said SHIP. Device-verified,
reeling Docks Bouncer: *"Skill 16"*, *"3d6 ≤ 15"*, *"state:hp: -1"*,
then *"3d6: 7 vs 15"*; the attack *"3d6 ≤ 2, each"* and two rolls *"vs
2"*; *"Roll vs. Acrobatics — 3d6 ≤ 4 (5 → 4)"*. Save restored.

6ge: those lines printed each modifier's ID, *"state:hp: -1"*. Every
`ModifierSource` has carried words for itself since the model was
written and no screen read one. `ModifierSource.label()` is the one
place a source becomes text; `lines()` on a target or an outcome gives
them, and a push names itself. Before the dice the skill dialog leaves
out the circumstances picked in it, already lit chips above. The
resolver is untouched. SHIP, prose only; device-verified: *"1/3 HP
remaining: -1"*, *"Pushed with FP: +1"*. 6gf: the attack's
add-up test now runs the range band, each circumstance, darkness and
the trait terms, alone and together. It never reads what `AttackOptions`
prints. 6gg: *walking* and *running* could both be ticked, a -3 no
speed produces. The three speed rows are one pick now: the extractor
marks them, `Ranged.modifiers` refuses two and the chip swaps through
`Ranged.alternativesTo`. A reading from bounds that do not meet, not a
printed sentence; Fit beside Very Fit (6fk) is two held traits and does
not transfer. Design review changed three things, one of them 6gf's own
plans. Device-verified. 6gh: the refusal counted a flag and the swap
filtered on it, 6gg's LOW, a defect by the owner's own rule. One
`Ranged.oneOf` answers both now; one mutant reddens both sides. Review:
"behaviour-preserving" was false for a row with both flags; none
exists. The Aim swap is device-verified. 6gi: with no shot called the
damage line read *"×1.5 there"* under nine unlit chips; it says *there*
only for a called location now. Its log tables the trait backlog: 125
unread rows, none a slice without a new reading. Device-verified.
6gj: Absent-Mindedness's "-3 to skill rolls" is a GM declaration on
every skill roll; Voice and Industrial Expertise name a purpose and are
refused on Healer's reason, one shared constant, `PURPOSE_NOT_SKILL`,
so a reading that applies one applies all three. Device-verified.
6gk: the potion text, checked both ways against the book: verbatim, but
three grants naming no listed skill were dropped and reached no screen;
stored and shown now. The pathway screen lays the text out as cards and
tabs; `abilityParagraphs` is display only and rejoins all 105 blocks byte
for byte. 6gl: the Traits tab is a name, a price and a tag per row,
`TraitReport.applied`, which says no less than the open row's notes; a
dialog's reference prose sits closed in `RuleNote`, never a target, cost
or refusal; the header is tags. 6gm: the builder. A skill row is name,
level, points and a study tag naming the refusal's cause (`StudyRefusal`),
the sentence on a tap; *before potions* once, on the header. The trait
picker's tap opens a row and never buys; Buy sits inside it.

**6gc (its device pass) and 6gb: the second tap of one picture.** 6ga left the dialogs
to 6eh, and ten slices had moved a dialog's target since, so they were
read again: 18 rolls and every charge. Each prints the number it rolls
or charges. What did not hold was 6ei's count, *"Drink alone had no
re-entrancy guard"*. Read it for three things.

- **The push rows.** A chip is offered against the count as drawn. In
  the skill dialog *+3* twice throws past the FP cap, and on the SPI row
  spends a pool the first tap emptied: Soul Debt, permanent CoR. The
  attack's also pushed the outcome it was drawn with, 2 FP for +1.
- **A stale tap is dropped, not honoured**, the design review's change
  to my proposal: these two charge AT the tap and hold the balance as
  drawn, where the ritual's push charges on closing. `EffortBurn.pushed`
  is the rule's refusal behind the drop.
- **My own count was wrong within the hour.** The first commit says
  seven handlers. A sentence I wrote here about *"every handler"* was
  questioned, and the query it needed found Damage's Apply and the
  builder's Give back, which sold the neighbour. The slice log gives
  tables and no count.

Both diff reviews said SHIP. 6gc, on the SM-T570: the tablet still
held the build before the fix, and two taps on *+3* gave the FATAL in
`EffortBurn.bonusFor` that 6gb had written down, 3 FP already charged;
two on *Apply 1 to HP* cost 2. On this build, one push and one hit.
6ga's hurt case with it: reeling, the Traits row and its dialog both
read *"3d6 ≤ 11"*. The save was restored.

**6ga back to 6de had lead paragraphs here until 2026-10-12. Each is in
[`docs/READING-ORDER.md`](docs/READING-ORDER.md) verbatim, and in full in the
slice log.** One line each:
**6ga–6fz** — a sheet row prints the played number, never the unedited sheet's; re-run 6ga's printed-number sweep after any new screen, and a grep for derivation calls is not it;
**6fy–6ft** — a sheet's printed SPI 9 is not played: a premade plays 0 until a potion; the gallery and every chip print played numbers; `repairedEdits` is the one repair;
**6fr** — every held row prints a cell: of 495 rows 29 had none; Wealthy prints the wealth table's, and the sense chips were cut, so a chip's text is not a row's cell (6fs: its device pass);
**6fq** — a held face printed its name alone on Traits; `held()` gives it the table's reaction cell, and my test had computed its expectation with `cellFor`'s own formula;
**6fp** — the builder's Appearance row says what the potion grants and which face is played, through `Appearance.played`; that closed 6fn's census;
**6fo** — Mother 8 and Justiciar 6 grant Ancient Hermes, Native, both forms; `Languages.grantedBy` feeds the ritual dialog's note and no roll changes;
**6fn** — a potion's face: the chain keeps Sequence 6's and the better of bought and granted is played; a second `mapOf` key would have silently dropped Charisma's grant;
**6fm** — a potion's Charisma levels add down the chain and onto a bought level, capped at 4; the label first said the grant's level, not what it added;
**6fk** — a potion's catalogue rows are granted through one table pinned both ways; a census that dropped names under four characters lost Fit, and the builder would have sold a granted row;
**6fj** — the Warrior's default +2 stays refused, on the book's reason: no number fits both the Warrior's example and the Weapon Master's DX ceiling;
**6fi** — a potion's senses reach the Per roll through one table; the census took its names from my list, not the prose, and a non-compiling mutant read stale XML as a catch;
**6fh** — Acute Vision and Hearing are declared on every Per skill; Spirit Vision takes Vision alone, and a row held twice is deduped on `trait to printed`;
**6fg** — Luck may reroll a failed PCR, so the attempted potion is saved; a restart would strand it, and a Will lowered since made the reroll throw;
**6ff** — six rows that name one skill are declared on its roll; one list, `skillRollClauses`, feeds the chips and the dice, and "rural" excludes the sea;
**6fe** — Alertness reaches Spirit Vision's Per roll; a recorded census ("the one bare Per roll") expired when a second roll arrived;
**6fd** — Cleansed Spirit halves nothing, and the CoR stepper says why; "Soul Debt is reported, not applied" had been false since `ea6ae38`;
**6fc** — Fast Healer's +1 is said on the recovery row's result; on a failure the two readings disagree, so the line says it is the GM's;
**6fb** — Chronic Insomnia's "sustained concentration at -1" is declared on the Concentrate roll; two API lines had been pasted inside 6ew's lead;
**6fa** — Thinned Veil's "Concentration ... at -2" is a declaration on the Concentrate roll alone; the verbatim guard checked two traits, and now checks every partly-applied row;
**6ez** — Knows the Acting Method doubles the digestion ranges the tracker prints for a holder; the note asks `doublesActingGains`, not whether two tables differ;
**6ey** — a master's weapon parries at DX: `SkillDefaults.wieldedAt` floors the skill, and Attack, Defend and the builder all print it; the Pugilist was invisible to a keyword;
**6ex** — a Reluctant Killer's target is the GM's to declare (-4, or -2 with the face hidden); "cannot Aim" is unconditional, and the Attack roll takes the general trait term;
**6ew** — a self-control row's other clauses were unsaid; `ROLLED_ONLY` says the roll alone is applied, and `catalogueName` had cut `SC 6` to `SC` as if 6 were a level;
**6ev** — the bare-hand parry cell prints "Brawling or DX", so DX is a row of its own; the Acrobatic Dodge rolls its default, and Defend gained the missing trait term;
**6eu** — the Attack dialog takes an untrained skill with a fist or weapon row; a Warrior's weapon is wielded at no less than DX, and one `levelWith` feeds the header, damage and roll;
**6et** — the untrained roll: `defaultFor` had no caller since M0; a skill's own line (Brawling DX-2, Wrestling none) beats the table, and Versatile's +1 sits inside every default;
**6es** — the complement of `known()`, listed: 148 rows nothing read; `UNREAD` says so, and the sheet lists every held advantage with its cell;
**6er** — the builder sells a language; the two halvings combine with one ceiling, and a Language Talent bought later reprices what is owned;
**6eq** — 39 refusals in production source whose subject is this app, seven false or stale; Versatile's +1 goes inside the pick between an owned level and a default;
**6ep** — 6dy's three refusals re-asked; the HP recovery table's first row is a ROLL, so the daily HT roll is offered and nothing is restored;
**6eo** — the builder printed bought values and the sheet the played ones; rows show the value as played, and every edit still reads the purchase;
**6en** — Basic Speed and Basic Move sold a hundred steps each; both stand at zero in `ScoreFloors`, as policy, and Move is measured after Speed;
**6em** — maximum CoR is a state the app checked only on a gain; a builder sale is refused where a potion latches, and the first potion never latched;
**6el** — a potion's grants are added to the bought score, so browsing a block crashed a high-ST save; `PurchaseCeilings` bounds each purchase, and 6ek's own example was false;
**6ek** — the builder sold a secondary a hundred steps and four guards threw below HP/FP 1, a CoR ceiling of 1 and Per 0; "four" came from a keyword grep and held, for crashes, on a wider one;
**6ej** — the Drink button printed nothing about its own roll; `pcrLine` feeds both blocks, and the new line could crash the block it was drawn on, a Will below 1 throwing in composition;
**6ei** — two taps milliseconds apart DO reach one composition's handler, so 6ef-6eh's "two frames" premise was false; Drink had no re-entrancy guard (not alone: 6gb) and crashed on an SM-T570;
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
**6dx back to 6de** have one-line digests too, moved verbatim to
[`docs/READING-ORDER.md`](docs/READING-ORDER.md) to keep this index under
its 8,000-character cap (6ff, 6gb, 6gd).

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

**Where to start next: the slice log, from the END — 6gv first**
(Perceptual Splitting's -2 declared on the sheet and scoped to the rolls
line 390 calls physical: `physicalStates` beside `states`, and the ledger
asks `EffortBurn.isPhysical`; the signed unread rows are all triaged, and
none is a slice without a stored choice or a new roll). Then 6gu (Light
Sleeper's +4 declared on a Per skill and on Detection's Per branch, never
Spirit Vision; Skinny refused as a purpose; the unread census read 117).
Then 6gt (no roll
changed: the PCR takes no carried state, on the book's "Only the drinker's
digestion ... matters", and no row names the roll). Then 6gs (five
"-N to all rolls" drawbacks declared once on the Vitals tab, reaching every
roll shock reaches and the Major Wound HT roll too; Phobia's and Soul
Scar's refusals expired; shock now reaches the Vitals tab's HT rolls;
Attack takes the states among its own terms, since its tracker line is
read off the ledger by id). Then 6gr (Tobacco
and Guilt's "-1 to Will" read as a Will-roll clause, a reading recorded as
one; Bound to a Place refused, a count of weeks). Then 6gq (no code:
every restyled charge handler kept its second-tap guard, double-tapped on
the device beside a mutant RED; the pinned Drink button refused, as it sits above the fold). The
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
| Tests | **2089 Kotlin** (JUnit 5 — 1378 `core:rules`, 691 `app`, 20 `core:model`) + **366 Python** (pytest), all green — counted from the JUnit XML, not from memory. **This row was stale by two on the Python side before 6dg**: it read 300 where `HEAD` carried 302, so re-count rather than adding your delta to what it says. A filtered run (`--tests`) leaves the results directory holding only what it ran, and Gradle then reports the unfiltered task UP-TO-DATE — count after a `--rerun-tasks`, or you will read the filter's leftovers (measured in 6dg: 489). **The `app` module writes to `build/test-results/testDebugUnitTest/`, not `.../test/`**: a glob over `test/` alone silently drops all 466 and still reports a green total (6dc). The `app` jump from 92 is `ThemeSchemesTest`, which sweeps every `ColorScheme` role by reflection and so emits one dynamic test per role per preset per mode |
| Open issues | **Zero.** OPEN-1 was fixed in 6ar; the Codex sweep's 17 findings are all closed or refused with evidence (6as, 6at) |
| Review gate | **ecc, not Codex** (the owner, 2026-10-10). The plugin is disabled, so a sonnet sub-agent runs `~/.claude/plugins/cache/ecc/ecc/2.0.0/agents/kotlin-reviewer.md` with an explicit range (6fl). It approved `ad8b4e1..123d68c -- core app`, and then `123d68c..4e9ab4e`, the file splits (SHIP, no findings); nothing is owed |
| Branch | everything on `master`; working tree clean |
| Toolchain | Kotlin 2.2.0, JVM toolchain 17, Gradle 9.3.1 (wrapper committed) |

### Commands

```bash
./gradlew test                    # all Kotlin tests, app module included
./gradlew :core:rules:test        # one module
py -m pytest tools -q                # Python tooling tests (Windows: `py`, not `python`)
ast-grep scan                     # 4.3's division rule; silent and exit 0 when clean
ast-grep scan --rule tools/sg-census/session-mutator-in-lambda.yml --report-style short
                                  # 6gb's census: 32 lines (6gn), one per call site
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
SkillDefaults.wieldedAt(weapon, skillLevels, mastery, dx): Int?
                                      // the level a CARRIED weapon parries at:
                                      // its best skill, floored at DX for a
                                      // master. Attack, Defend and the builder
                                      // all print it (6ey)
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
TraitGrants.grantedTraits(state) / .withGrantedTraits(traits, state)
TraitGrants.grantedLevelsIn(merged, bought)   // the levels a potion ADDED
                                      // to a bought levelled row (6fm)
TraitGrants.grantedTraitTable() / .refusedGrants()
                                      // every catalogue row (and 6fi's sense
                                      // rows) a walked potion chain printed,
                                      // zero cost; refusals with reasons (6fk)
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
Ranged.bandFor(meters): RangePenalty / .penaltyFor(meters): Int
Ranged.modifiers(band, conditions, gmValue): List<Modifier>
Ranged.alternativesTo(condition): Set<String>   // the other Aim row, or the
                                      // target's other speeds; the chip swaps (6gg, 6gh)
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
SpiritVision.targetFor(per, shock, hurt, alertness, vision) / .attempt(...) / .partsFor(...)
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
Appearance.played(traits) / .isSuperseded(trait, traits)   // one face: the
                                      // better of bought and granted (6fn)
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
