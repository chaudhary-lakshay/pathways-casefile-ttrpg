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
`task --effort high`, which takes the target list, the three questions and the
"do not read CLAUDE.md or docs/SLICE-LOG.md" instruction in one prompt.

**When Codex is out and the free panel is also silent, go to
`ecc:kotlin-reviewer` rather than shipping self-checked** — 6cz is what two
slices of not doing that cost. **When the free panel is also silent, go to
`ecc:kotlin-reviewer` rather than shipping self-checked** — 6cz is what two
slices of not doing that cost. Every pass since 6cd has been the documented
`ecc:kotlin-reviewer` fallback, scoped to three questions and the files needed
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

**6eg is newest, and it reverses the fix 6ef shipped a day earlier.** 6ef
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
seven pairs left and the Fright Check's inline sum**, design review first.

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

**6ed is next, and it is the sweep 6ec named: does a committed generated
file still reproduce from the tool that writes it?** Clean, with its control —
40 extractors rewrite **81 of 81** content files, one writer each, zero diff,
and every one recovers after its outputs are overwritten with junk; the 23
fixtures, `ThemeSchemes.kt` and `build/extract` reproduce too. **The first
upstream run measured the instrument**: six files byte-compared `same` after
running a tool that writes two of them — an untouched file compares equal by
construction. Re-done with an mtime marker, one file was rewritten by NOTHING:
**`build/extract/book.txt`, the citation anchor, had no writer.**
`extract_markdown.py` credited it to `extract_pdf.py`, which writes
`pages.json` and `chapters.json` only, so a fresh clone could not build the
file every `sourceLines` number is read against. It had not drifted (10252 of
10252 lines match `lines.json`); `coverage.py` writes it now, in the same walk
that numbers `lines.json`, byte-identical. That is 6dg's loader sweep
inverted — **every generated file checked for a WRITER.** Read it for three
more things. **`extract_spirit.py` exited 1 AFTER writing its file** whenever
stdout was redirected (the book's `→` against cp1252), so every scripted run
read a correct rewrite as a failure. **The guard is three tests in
`test_tools.py`** that run each extractor as a script under `runpy` with
`Path.write_text` captured — as a script because four extractors have no
`main()`, which the first version found by reporting nine files with no
writer. Its RED proof is historical: at `5e803b6` it names `pathways.json`,
`pool_thresholds.json` and `non_standard_pathways.json`, exactly the three
6ec found by reading a diff. And **writing the anchor's format down found an
assertion that could never fire** — `"
Critical
" not in book`, on a file
whose every line opens with a number. Regeneration order on a fresh clone:
`extract_pdf.py`, then `coverage.py`, then the extractors. Codex's diff review:
no HIGH, no MEDIUM, two LOWs (one taken, one refused with its bound). **No
successor is named.**

**6ec is next, and its sweep came back clean while the defect sat beside
it.** The direction was the inverse of 6ea's — every declared `@Serializable`
property checked for a JSON key that actually SETS it, since a property no
data file populates is always its default. **The
first two runs measured the instrument**: matching property names against the
union of all 81 files' keys returned 0 of 471 because a key named `name` is in
nearly every file, and the tightened per-file version silently covered **73 of
81** because its loader regex could not see a comment between `by lazy {` and
the `load(…)` call. At 81 of 81 it returns **10 hits, and all ten are
settled by a named query** rather than by assertion — including `\(\d+\)`
returning 0 over the advantages spans and 9 over the disadvantages spans,
which is why the book prints no self-control advantage. **A sweep's zero is
worth nothing until the same query has returned non-zero on data known to
hold the thing**: the qualifier query that settled chapters 14/15 first
returned 0 on `pathways.json`, which holds twelve such notes, because the
flattened PDF puts the attribute on one line and its gain on the next. Fixing
that control is what found the slice. **Two stat notes were cut at their
wrap and the remainder landed in the neighbouring prose field** — White Tower
Seq 9 stored `"(knowledge retention"` and ended its advantages `"…near-perfect
recall and learning)"`; Demoness Seq 9 stored `"— reflexes sharpen"` and
OPENED its advantages `"for precise timing Night Vision…"`. One sentence,
arriving as two wrong fields, both rendered (`PathwayScreen.kt:505` and
`:513`). The rule is the book's own signal — a wrapped line is mid-sentence
so it opens lowercase — and it is measured over **all 105 blocks this parser
sees: 62 followers, 60 capitalised advantage names, 2 lowercase and both the
defect.** The first count said 88 blocks, and the review's LOW was that the 17
non-standard blocks run the same parser and were excluded from the corpus that
could contradict the rule. **Regenerating found a larger defect**:
`b1c8c4e` taught `coverage.py` to partition chapters at the HEADING and
hand-refiled `pathways.json` to match, and `extract_pathways.py` was never
taught — so re-running it **silently reverts that commit**, and only reading
the diff can see it. `chapter_at` asks `coverage.heading_lines` now, and the
signature change is what found the second call site (`extract_non_standard.py`
shares `extract()` and failed loudly). **Reading `b1c8c4e`'s own file list
found a THIRD site and the worst kind**: `extract_pools.py` had
`CHAPTER = "5"`, a CONSTANT, writing both printed ranges under Combat when
the SPI ladder prints in chapter 4 — so a re-run reverted that refile too.
**A hardcoded answer cannot learn a correction**, and a grep for
`chapters.json` cannot SEE one — it does not return `extract_pools.py` at
all. So "is there a fourth site" is answered by an audit of the data:
every chapter key of every top-level `sourceLines` dict in all 81
committed files against `chapter_at` — **80 carry one, 0 disagree**, and
it ships as a test with the pre-`b1c8c4e` span as an inline positive
control.
**And the partition test only became a test after the review**: reverting
`chapter_of` left all 351 green, because its fixture had the page boundary
and the heading on one line — **an `AttributeError` proves a function is
missing, not that a behaviour changed.** The oracle was `HEAD` itself: the
regenerated spans are set-identical to the committed ones and the per-chapter
counts match exactly. **Two corpus guards ship and each RED-proved on a
DIFFERENT instance**, which is why both are kept. **Device-verified on an
SM-T570 without drinking anything**, since the pathway screen renders a block
before the Drink button. **No successor is named**, but one thing found on the
way is a sweep direction: **nothing anywhere checks that a committed content
file still reproduces from its extractor**, and that is what saved this slice.

**6eb is next, and it is a parry the defence screen could never show.**
`DefenseDialog.armedParries` read `Character.weapons` — a field no published
sheet fills and no save could write — so the armed-parry rows were
permanently absent on all 23 premades, while `AttackDialog.kt:722` printed a
chapter-8 weapon's parry from `Weapons.parryFor` one tap away. **One
question, two sites, data at one.** It ships as `SavedEdits.weapons`
(catalogue NAMES, resolved by the book), a `Weapons` row and picker in the
builder, and `armedParries` moved onto `CataloguedWeapon`. Read it for six
things. **The shape was the decision**: `Weapon.parryMod` exists because a
published SHEET prints a dash or a number and this book prints no per-weapon
modifier anywhere, so manufacturing one from catalogue data would claim
chapter 8 supplied it and leave two formulas for one fact — the strongest
case against is recorded, not dismissed, and `ParryBlock.parryFor` plus
`Character.weapons` are now a **stated non-goal in production source** rather
than accidental silence. **The tests MOVED rather than died**: three
`DefenseDialogTest` cases were the only thing pinning the sheet path's
formula, and they are one `ParryBlockTest` case now. **Possession is not
training** — the picker offers all nine rows because a GM may hand anyone a
weapon, and an untrained one prints *"not a skill this character has, so no
parry"* rather than vanishing; the reviewer's further demand that
`AttackDialog` filter its chips to possessed weapons is **refused** on 6cx's
role distinction — a picker asks, a list asserts. **`Weapons.levelFor` takes
the HIGHEST matching level**, because p. 85 makes two rows of one name at two
difficulties into two skills and iteration order would otherwise decide the
number. **The diff review returned four findings and all four were the
vacuity class**, three RED-proved: deleting `+ parryPenalty` from the armed
rows alone left every test green (the two row types are one `parryScore`
expression now, and no test pins the sum because a test of `a + b + c`
restates it), my "highest level" test inserted the 14 LAST so *"take the last
match"* passed it, and both case-insensitive matches were asserted only with
canonical spellings. **A third finding landed after the review**: the builder's new row computed
its skill levels from `Derive` while the sheet screen has folded a potion's
grants in since M3, so a Beyonder would read one parry beside the weapon and
another on the defence screen — `liveSkillLevels` is the one expression now,
and it was found by checking what the OLDER call site held rather than the
line I had just written. **Device-verified on an SM-T570 on 2026-09-27**,
in the session after the one that shipped it, and all three claims held. The carrier was the
Journalist, who has no Knife at all, so ONE row was read twice: *"Knife —
not a skill this character has, so no parry"* as a mortal, and **"Knife 13
— parry 9"** after one Error Sequence 9 potion. The defence dialog one tap
away printed **`Parry (Combat Knife) 9`** — one expression, two screens,
one number, which is 3f177c5's check rather than one of this slice's three.
The picker offered all nine rows — five firearms and four melee — to a
character trained in none of them; the parry ran 9 to 5 on one prior parry
and back on `New round`, and the
carry survived a force-stop as `"weapons": ["Combat Knife"]`. And one
environment fact: Gradle failed six times with `Unable to
delete directory` on Windows, **indistinguishable from a build failure in the
exit code**; `rm -rf` the named directory and re-run. **No successor is
named** — both gaps 6dz recorded are closed, and the device pass that was
outstanding is done, so the next slice needs a fresh sweep. One thing the
pass found outside the slice is recorded and NOT repaired: `PremadeGallery
.isBeyonder` is `sheet.published.spi > 0`, so a character walked onto a
pathway keeps its `Mortal` tab and `SPI 0` in the roster — which may be
correct, because every number in a gallery row is the PUBLISHED sheet's.
**It also cut the lead
paragraphs for 6cz down to 6cd**, because this file went back over the
150 k-char limit (152,583); each was checked against the slice log first, and
**6ct had no section there at all** — its paragraph was the only copy, and it
is restored in `docs/SLICE-LOG.md` as its own section. **A lead paragraph can
be the only copy**, so one `grep -c "^## <slice>\."` per name is what cutting
one costs.

**6ea is next, and it is a field whose only writer was a potion.**
`PointBudget.report` has billed `SecondaryCharacteristics.costOf` since M0,
`Derive` composes it and `PotionDigestion` adds grants into it — and **nothing
in production ever constructed a non-default `SecondaryDelta`**, so the six
rows chapter 2 prices on pp. 8-9 (HP ±2, FP ±3, Will ±5, Per ±5, Basic Speed
±5 per 0.25, Basic Move ±5) could be charged for and never bought. That is
6dz's own defect class one rung up, at a FIELD rather than a parameter. It
ships as a `Secondary characteristics` section of steppers in the builder.
Read it for six things. **The save stores the book's ROW KEY** (`"hp"`,
`"basicSpeed"`) to the steps bought, not a `SecondaryDelta`, because that
class rejects a magnitude over 100 in its constructor and `SessionStore.load`
discards the WHOLE file on a decode failure — the same NAME-and-resolve rule
`wealth`, `appearance` and `traits` already follow, and those keys are stable
save identifiers from here on. **The merge ADDS**: my decomposition proposed
replacing `base.secondaryDelta` on the evidence that no fixture carries one,
and the design review named the state that breaks it — a granted Will +1 goes
to zero the moment a player buys a point of HP. **A reachability claim about
today's fixtures is not an argument about the API.** **The obvious tidy-up was
refused with arithmetic**: rewriting `SecondaryCostPinTest.engineCostOf` to
call the new mapping would have left four rows indistinguishable, because Will,
Per, Basic Speed and Basic Move all cost 5, so that test keeps its own
independent `when` as the oracle. **The diff review's two MEDIUMs were one
line and the device had already disproved it**: a *"sheet prints 4.50"*
comparison derived from the unedited CHARACTER, where the Vitals screen one
tap away reads `Basic Speed 4.50 (sheet: 4.00)` — at least 19 of the 23 sheets
floor Basic Speed and the line asserted the opposite on all of them. It is
DELETED rather than repaired, because `SheetComparison` is the authority for
computed-versus-published and renders one tap away (6cr); what ships is
`±2 pts per level — +1 bought`, which is a claim this screen can back. The
third MEDIUM is an overflow: coercing AFTER the addition let a hand-edited
`Int.MAX_VALUE` wrap negative into a legal purchase — **a bound applied after
arithmetic is not a bound on the arithmetic.** And **four of five LOWs were
vacuity and all four bit**, three RED-proved by mutation, including a label
test that survived reading HP out of FP because a default character has 9 of
each. **Device-verified on an SM-T570 in both directions** (Basic Speed 4.50 →
4.75 taking the budget 102/105 to `107 of 105 — over budget`, five points for
one QUARTER; HP 9 → 10 at 104/105; stepping back clearing the line, the budget
and the saved entry together). **Its two fresh sweeps both came back clean and
both are recorded with their bounds**: every content JSON KEY checked for a
Kotlin declaration (81 files, one hit and it is a chapter label) — which
matters because `ContentLoader` decodes with `ignoreUnknownKeys = true`, so an
undeclared key is dropped in SILENCE and 6ai's sweep cannot see it — and every
enum MEMBER checked for a production use (116 members, 35 flagged and every one
a false positive, because the query excluded the declaring file and a picker
reaches its members through `entries`). **Neither number is a finding; both
measure the query.** **No successor is named**: `Character.weapons` is now the
only one of 6dz's two recorded gaps left, and it wants its own design review.

**6dz is next, and it ran the sweep 6dy named: every optional parameter
checked for a non-default ARGUMENT.** 369 optional public parameters across
`core/rules` and `core/model`, 175 excluded by the query's own rules (a
`ContentLoader` default and a `RandomSource` are deliberate test seams), **36
never overridden in production, 5 a real gap** — two of them already-recorded
deliberate gaps (`Character.weapons`, `Character.secondaryDelta`) and one a
false positive that was CHECKED rather than believed. What shipped is one
defect wearing two costumes: several modifier rows print a SPAN, every engine
reading one has taken a validated `gmValue` override since extraction, and
**only `BoonDialog` ever passed one**. So `Method match +0 to +2` applied +0 —
a control whose only effect was nothing, with its own range printed on it —
`Darkness or heavy fog -3 to -9` was always -3, and `-2 per repeat` never
multiplied, so a third divination cost what the first did. Read it for four
things. **`endsBetween` runs OUTWARD from the row's own figure, not
numerically**: a descending span worsens as it runs, so sorting would put the
worst case first and leave the chip the roll is USING at the far end of the
row (mutation-proved). **The default chip showing selected is a claim about
the engine** — the screen passes an empty map rather than seeding `picked`, so
there is one authority on that default and it is not the screen. **A stale
pick cannot reach a later roll by construction**: `overrides` is built by
filtering against the rows ticked RIGHT NOW, which is `BoonDialog`'s own
design, rather than by pruning on every change. And its review — through the
single-agent fallback, because Codex hit its weekly limit mid-pass and the
free `pi-qa` gateway was down — returned **four findings and every one was a
guard that could not fire**, including the two tests written for this slice's
own wiring: deleting the `gmValue` argument left every test green. Read 6dz
also for a dead `to == from` branch deleted along with the test guarding it,
and for why NOT auto-incrementing the repeat count on "Ask again" is correct.
**Device-verified on an SM-T570**: `circumstances -6` to `-4` on a +2 pick,
the target 16 to 18, and `Range and cover: -9`. **No successor is named, and 6dz says
so as a verdict**: the census closes, and the two real gaps the parameter
sweep left are features already recorded in section 6 — weapons and the six
priced secondary characteristics, both blocked behind an ADDITIVE `SavedEdits`
widening, because `SessionStore.load` drops the whole file on a decode
failure. The next slice needs a fresh sweep; every direction already run
expires rather than standing.

**6dy is next, and it is a scoping sentence that was a claim about this app.**
`EffortBurn`'s KDoc kept the post-roll SPI push on the plain skill roll because
*"a ritual and a divination keep charging their own burn"* — and neither did.
All three specialist dialogs called `spiFor` with `burned` defaulted and drew no
push row, `Divination.attempt(..., burned)` had no non-default caller in `app/`
at all, and `RitualResolution.attempt` took no `burned` while `spiFor` would
charge for one: **the ritual's charge was implemented and its bonus was not.**
The book prints the rule five times (lines 393, 3708, 4105, 4145, 4268) and
three of those sit inside CLAIMED spans, so no coverage number and no content
sweep could report it. Read it for five things. **The unit that finds it is a
PARAMETER checked for a non-default ARGUMENT** — the five sweeps this file
already records iterate declarations (fields, functions, rows, files, screen
strings) and every one is blind to a call that passes a default; 6dy states the
query. **Boons arrive by REFERRAL, not resemblance**: ch. 13 prints no burn
sentence, and the warrant is its own line 8933, *"The boon granting ritual uses
the standard Ritualistic Magic system (see Chapter 7)"*, plus ch. 2's SPI row
naming Ritualistic Magic outright — arguing from `Boons.weight()` returning a
`RitualWeight` would have been the warrant 6cx returned NO-SHIP on. **`canPush`
answers OPPOSITELY on the two objects**: a ritual's rolled 17-18 takes its row
whatever the target so nothing can move it, while a divination's critical
SUCCESS is pushable because the clarity table grades one by margin — copying
`EffortBurn.canPush` wholesale would have refused it. **The Critical Failure
Table's roll is a second SAMPLE**, re-used or dropped and never re-taken, with
the impossible case (`requireNotNull`) RED-proved by flipping the push to widen
the margin and reading the raised message. And **deferring the CoR introduced a
regression the design review did not reach**: `remember` does not survive the
dialog leaving composition and leaving composition is not a dismiss request, so
a rotation dropped the CoR the roll owed — which the pre-deferral version
charged at the roll and could not lose. `DisposableEffect { onDispose { … } }`
closes every in-app path; process death is a stated bound. **Device-verified
on an SM-T570 on both screens**: a divination went `12 vs 10` (Murky) to
`12 vs 12` (Vague) on a +2 with the TOTAL unmoved, a ritual `11 vs 14` to
`11 vs 15` on a +1, and SPI ran 18 → 15 → 12, which is each fuel cost plus
each push charged exactly once at close. **The third screen and the rotation path were
verified after that**: `BoonDialog` had never been rendered and turns *"The
prayer fails — 13 vs 12"* into *"The boon is granted — 13 vs 13"* on a +1
(SPI 6 → 1, 4 fuel plus 1 pushed, and it offered only +1 and +2 because 2 was
all the caster had); and rotating with 2 pushed but uncharged took SPI 10 → 6,
so `DisposableEffect` charges exactly once on a path that has no dismiss
request. Only the divination backlash clearing on a push is still unreached.

**6dy also closes the coverage question with a census rather than a
percentage.** All 196 unclaimed mechanical lines are accounted for in one table
in the slice log — twelve of fourteen candidates came back SETTLED before a
line was written, citing `file:line` or a grep that returned nothing. **That
table's first version said "every" over a corpus of about 155 of the 196**,
and Appendix C — 0.0%, claimed by nothing — was not in it at all, which is
6db's finding in a paragraph written four slices after 6db. The remaining ~40
are triaged in it now, and Appendix C's two lines turn out to be **dates**:
`(expelled post-1350)` and `(pre-1352)`, read by the filter as signed
numbers, so that chapter's whole percentage is an artefact. **Read the table
as a claim with an expiry date**: three of its rows are refusals
whose stated reason is a claim about this app, which is the exact shape 6dy
itself overturned, and 6da, 6dj, 6dl, 6dq and 6dr each began by re-running one
such reason as a query and watching it die. One grep per row is what checking
it costs.

**6dx is next, and it is a refusal that turned out not to need writing.**
`Shaken` (line 8030) had been carried unchecked from 6dk through 6dw as
"likely a refusal, nobody has looked". Run as a query it is not one: its
carrier is `reinforcedAbilities` prose, which **`PotionDigestion.UNPARSED`
already records — as a GAP**, in the KDoc's own words *"unbuilt work, and it
must not be inherited as settled"*, so an UNPAYABLE-standard refusal beside it
would say the clause is settled AND invite the next reader to read the prose
around it that way. **And the instance is not distinctive.** Seven lines defer
to a GURPS Basic Set rule this book does not reprint; two sit outside that
prose and are already ruled on (2723 → `VitalRolls`, 3653 →
`Divination.UNAPPLIED`), and of the five inside it three name a term the book
prints elsewhere and this app models, while **TWO are printed exactly once:
`Shaken` and 7348's `suffocates`.** A refusal against `Shaken` alone would be
a blocklist of one with its twin a chapter away. Read it for three more
things. **The review's HIGH is 6ba's rule applied to a regex**: my census read
one line at a time and `p. B229` **wraps** — 3653 ends *"(see GURPS
Techniques, p."* and 3654 opens *"B229)"* — so the query silently returned
seven sites and dropped it; the scan joins each line to the next now,
RED-proved by restoring the line-local version, and the fix makes it reproduce
6dr's hand census exactly. **The MEDIUM killed my discriminator and the
replacement is what found the twin**: "does the sentence gloss the term" was
wrong about 8062, so the question became "does the book print this term
anywhere else", which is a query and not a reading. **And 6dw's named sweep
ran clean in four shapes** — `return@` (4 sites), `if (x != null)`, `if (x ==
null)` and `when (x)` — with the fourth unable to find anything by
construction, because an exhaustive `when` names its subject in the branch
LABELS and never in the bodies. The double-tap-guard census beside it is clean
too: the three dialogs missing the `if (result != null) return@TextButton`
idiom each guard the button another way, and the Acrobatic Dodge's "Roll
again" is a deliberate re-roll.

**6dw is next, and it is the slice 6dv's device pass named: one
`return@Column` that showed reference AFTER the dice.** Three blocks sit below
that early return in `RollDialog` and only one was misplaced — the burn row and
its conditional note both need an outcome and the book allows the push only
after the result, while **chapter 8's gear reference depends on the outcome not
at all**. So the rows that name a skill reached the GM after the roll, when the
one thing they are for is choosing a modifier before it. It moves into the
pre-roll branch and **no longer renders post-roll, which is the deliberate
half**: the roll is over and the reference can inform nothing. The
`return@Column` itself is left alone — removing it is a ~40-line indentation
diff and a deeper nest for behaviour this screen does not need, and a local
composable called from both branches is two call sites for a pre-roll-only
list. **It ships BELOW the circumstance chips against the reviewer's stated
preference**, on the reviewer's own counter-case: these rows print numbers, and
a numbered line directly above a row of tappable modifier chips reads as
another selectable one — the distinction 6cx already returned NO-SHIP to
protect. **Verified the only way it can be**, which is why it survived from 6cx
with a live caller the whole time: nothing here can assert a Compose label, so
three pre-roll cases were read off an SM-T570 (`Savoir-Faire (High Society)`
showing 6dv's own join, `Psychology` showing a join that has existed since 6cx,
`Occultism` showing no section), plus the post-roll state confirmed empty. No
test ships, because the only assertion available would restate the source.

**6dv is next, and it is one predicate answered at two sites, taught on
only one of them.** It runs the sweep 6du named — which extractors DERIVE an
output row from a sibling content file rather than merely verify against it —
and **nine of ten are verify-only**, which is the house style and is recorded
as closed. The tenth is `extract_equipment.skill_index()`, whose value decides
the `skills` field on all 77 equipment rows, and it used a strict
`(name, spec)` match. Chapter 3 prints `Connoisseur` and `Savoir-Faire` ONCE
each with no specialization and `specializable: true` — an open row the player
fills in — and chapter 8 prices instances of both, so both bonuses were
dropped and **the one fixture holding `Savoir-Faire (High Society)` was offered
nothing**. That is 6cz's defect on the WRITING side of the same join: its
`isTemplate` and the extractor's `key in index` ask one question of one flag in
one file, and **the two must agree by construction, not by both having been
edited**. Read it for five more things. **A committed test asserted the defect
while quoting the fact that disproves it** — *"the two the book writes the same
way and chapter 3 leaves open"*, beside an assertion that the openness means no
match. **`verify()` ran one way**: no recorded exclusion may resolve, and
nothing said every unresolved target must be recorded, so a new dropped clause
was invisible with the row count, the pinned joins and the joiner set all green
— `RECORDED_UNJOINED` closes it, and the hyphenation seam is NAMED there while
the spelling pair below stays the authority on the fact, so there is one check
on one fact. **The RED proof had to be an addition and the first one proved
nothing**: appending `+1 to Cryptography` resolved, because Cryptography IS in
chapter 3's list — **a mutation has to land where the guard looks**.
`Connoisseur (Books)` is correct with **no carrier**: no fixture buys it and
the builder cannot produce a specialized instance at all (its picker passes
`definition.specialization`, null for an open row, and prints *"needs a
specialization"*). And its review's LOW was the vacuity class again — a subset
check forced by the equality above it, now disjointness, because what a union
cannot see is one name recorded in two sets.

**6dv is device-verified, and the device found the sharper half.** The Fortune
Teller's roll prints `Fine clothes (quality) (£1 10s) — +1 to Savoir-Faire
(High Society); status marker` — but only AFTER the roll, because
`MainActivity.kt:2195` is a **`return@Column`** and everything below it (the
burn row, the conditional-burn note, the gear reference) composes only once a
result exists. So chapter 8's rows reach the GM *after* the dice, when the one
thing they are for is choosing a modifier *before* them. Recorded, not
repaired — it wants its own design review and is the named next slice. **A rule
can be applied, tested, wired to a caller and still be shown at the wrong
moment**, and no test here reaches a composable: three hypotheses (stale APK,
failed content load, key mismatch) died to one temporary `Log.w` printing
`rows=77 withSkills=34 ref=1` while the screen showed nothing. **And the dump's
scale is per WINDOW**, which corrects 6du: the sheet is a clean 4/3, the DIALOG
is not — its Roll button at dump `(787,1146)` is tapped at about `(1024,1356)`
and the 4/3 point `(1049,1528)` lands on the scrim and dismisses it. One scale
cannot drive both windows.

**6du is next, and it is the sweep 6dt named, run in a sharper form than
6dt describes — plus the one duration in the Fright Effects table that the
book computes from the character.** The sweep: *claimed mechanical lines whose
NUMBERS appear nowhere in the file claiming them*, where 6dt's version compared
WORDS and returned 76 hits by construction. 26 hits, and **18 of them were the
instrument** — three content files share one `sourceLines` span, so each was
being asked to hold its siblings' numbers; unioning the files that claim a line
before comparing leaves **8, all accounted for** (the cross-skill default and
the two support caps live in Kotlin pinned by Python; two are the Effect Weight
table's first printing, deduped against its second; one is chapter 2's burn;
one is a worked example). Its inverse — *numbers a file STORES that its claimed
span never prints* — had not been run either and is also clean, with one thing
worth inheriting: **`spirit_vision.json` derives two of its grants from
`pathways.json`, so it consumes lines it cannot name.** Not a defect (the
sibling claims them), but it names the next sweep: nine extractors read a JSON
they do not write, and the question is which DERIVE a row from it rather than
merely verify against it. The slice itself is row 12 — *"Retching for (25 − HT)
sec"*, *"Ecstasy for (25 − Will) sec"*, *"Dazed for (25 − IQ) sec"* — which the
dialog printed verbatim, so a player did the subtraction by hand. Read it for
four things. **The boundary is the table's own**: a duration with a STAT
OPERAND is the only kind this app can work out, so rows 4-9's "1 sec" and
10-11's "1d-2d sec" stay prose, and the field is `frightComputedDuration`
rather than `frightDuration` because **a name is where a scope decision either
survives or gets misread** (the design review's correction, before any code).
**The operand is not the attribute the same cell ROLLS** — HT for the duration,
Will for the recovery — so `DurationAttribute` has a third member
`RecoveryAttribute` must not gain, the anchor is the printed parenthetical, and
`verify()` fails the day the two anchors answer alike. **A guard caught by its
neighbour is a guard not yet proved**: the first mutation was caught by the
band-set check before the attribute check could speak, and a second mutation
that keeps the band set and flips only the attribute is what actually fired it.
The floor above HT 25 is this app's and the screen prints its working
(`0 sec (25 − HT 27)`). Diff review: **no findings**, and its one design opinion
(a `durationBreakdown` struct instead of `durationLine`) is refused as an
abstraction with one consumer. **And the heredoc ate a regex escape again** —
this time loudly, as a `SyntaxWarning` where 6ds's `\b` was silent.
**Device-verified on all three columns** (SM-T570, Church Healer HT 9 / Will 11
/ IQ 11: `16 sec (25 − HT 9)` above a `Roll WILL 11 to recover now` button,
`14 sec (25 − Will 11)`, `14 sec (25 − IQ 11)`), and read 6du for what that
cost. Band 12 is ONE value on a 3d6-plus-margin table, so it cannot be
navigated to — about 6.9% per attempt at a target of 7 — and **a tap must come
from screencap, never from the dump**: deriving the scale from the largest node
in a `uiautomator dump` gives `(1.386, 1.576)` instead of `(1.333, 1.333)`
whenever a dialog is up, because no node spans the window then. That lands the
tap on the scrim and dismisses the dialog, which is **indistinguishable from
the control not responding**. **Three
follow-ups landed after that review**, all of them things a diff review was
never going to be asked for: the new `init` require was the slice's one
UNPROVED branch (RED-proved now by neutering it), the extractor's 0-or-3 check
read the FRIGHT column alone so a two-of-three parse reached a `TypeError`
instead of its own message, and `docs/HOW-TO-PLAY.md` was **a slice behind
before this one touched it** — it never got 6ds's recovery roll either.

**6dt is next, and it is the row printed DIRECTLY BELOW one that was
applied.** `Hard to Kill` (2 pts/level, *"+1/level to HT rolls to avoid
death"*) has been applied since 6de. `Hard to Subdue` sits under it at the same
price and the same shape — *"+1/level **to remain conscious** when reduced to 0
or negative HP"* — and reached nothing. Between them they partition the two
rolls the HP table prints, and the second **quotes the `0 HP` row's own words**
(*"roll each turn to remain conscious"*). Read it for four things. **Every
sweep was blind to it by construction**: `VitalRolls`' KDoc said *"Three traits
name an HT roll"* and there were four, because this cell never contains the
words "HT roll" — 6bu's finding on a fourth mechanic, a roll the book NAMES
rather than describes. **A second gap was found alongside it and the two
CANCEL**: `TraitEffects.known()` claims to be "every trait this object knows
about" and omitted two it applies (`Hard to Kill`, `High Pain Threshold` —
single-trait functions belonging to no collection), so widening the census
filter would have reported a correctly-applied trait as dropped, while the
narrow filter cannot see the phrasing. Either gap alone gives a wrong answer;
together they give silence. Its own KDoc records that failure **twice** already
and both fixes were "add the collection" — these had none, so `BespokeBonus` is
a private enum the bonus functions AND `known()` both read: **a fourth bespoke
bonus cannot be written without an entry.** **The clause names "0 or negative
HP" and still does not reach the survival roll** — both checks are due below
`-HP`, and two rolls falling in one moment does not merge their scopes, because
that row's own words are "or die" (6ck). No cap, where the sibling's cell
prints "1-5 levels". Its review returned one LOW, the vacuity class in a test
written to be careful about it: my "no cap" guard asserted the CATALOGUE TEXT
lacks the word "levels", which a mistaken cap of 5 satisfies along with every
level below it. **Three sweeps ran and two were clean** — content spans against
chapter bounds (81 files, all inside), and lines a file claims whose words
appear nowhere in it (76 hits, no defect: a cell storing `+1` shares no word
with "+1 to reaction rolls", so that is a measurement of the instrument). **And
a mutation restore corrupted an unrelated line**: a first-occurrence replace of
`emptyList()` hit a function 600 lines up, and the suite stayed GREEN under the
corruption — CLAUDE.md's own `cp`-first rule, written down and not followed.
Read the diff's REMOVED lines, not the exit code.

**6ds is next, and it builds the gap 6dr named — nine cells whose attribute
belongs to the COLUMN.** The Fright Effects table prints a recovery roll in
three of its nine bands, Will in the Fright and Awe columns and IQ in
Confusion, and the other six bands print an outcome with no way out — so
nothing is offered there, because **offering a roll the table does not print is
granting a way back the book withholds.** Read it for five things. **The anchor
was the whole parsing problem**: row 12's fright cell reads *"Retching for (25 −
**HT**) sec; roll vs. **Will** to recover"* and its awe cell prints `Will`
twice in two roles, so an attribute-name search is wrong in two cells out of
nine — the anchor is the cell's own `roll vs. X`, 6do's rule applied BEFORE the
bad parse, and mutation-proved (with the mutation verified to have landed, 6db).
**The state ships as the book's word, decoded not matched**: a boolean would
make the extractor decide what a stun IS rather than structure what the cell
printed, so `StunRecovery.isStun` answers, in the object that already holds
which Major Wound row is the stun. **The Confusion column takes no clauses and
that is the BOOK's gap** — the recovery roll is a bare Will roll in two columns
so 6df's clause list reaches it, and this book prints no trait clause naming IQ
rolls, so there is no list to offer. **The diff review found a defect 6dr
shipped and this slice copied**: both panels stored a `RollResult` and re-judged
it against a RECOMPUTED target, so a chip moving under an open dialog reprints
an old roll against a number it never faced — `RollResult` carries its own
`target` and `outcome`, and **a finding on new code is a finding on the code it
was modelled on**, worth running backwards one slice every time. Its other two
were mine: `attribute == "IQ"` at two call sites reading every non-IQ as Will
(typed now, both questions answered once in `FrightCheck`), and a **data-shape
assertion** restating the book where the real guard is the extractor's anchor
test. And one editing hazard: writing a regex through a Python heredoc's
replacement string **ate `\b` as a backspace byte** — pattern compiled, matched
nothing, exit 0. Same class as the `sed a/i/c` rule; verify a written pattern
with `repr()`.

**6dr is next, and it is the harder version of 6dq: a refusal whose reason
was a claim about the app, and the claim was false the day it was written.**
`TraitEffects.PARTLY_APPLIED` reported Combat Reflexes' *"+6 to recover from
stun"* as *"this app models no stun recovery roll to make"* — while
`DamageDialog` had rendered the Major Wound Failure row, *"Stunned for 1d6
seconds (**roll HT each turn to recover early**)"*, since 6n. Military Police
carries the trait, so a GM who failed a Major Wound read the row telling them
to roll HT on a screen also telling them the trait moving it did nothing. Read
it for five more things. **The sweep it came from found nothing and the walk to
it found this**: the direction was *every place the book defers to a GURPS rule
it does not reprint* (six sites, one already recorded by accident in
`Divination.UNAPPLIED`), and a `p. B420` line that quotes its effect inline
sent the query back into chapter 5. That census's real yield is zero and it is
recorded as run. **The book prints this roll five times and the attribute is
never the stun's** — HT from a Major Wound, Will in two Fright columns, IQ in
the third — and **two Interrogator abilities on ONE page disagree with each
other** about the same stun (HT at line 8059, Will at 8062). Recorded, never
repaired; it is what settles the design, because an object answering for all
six would take the attribute from the source. So `StunRecovery` answers for
ONE and says so: its `bonusFor` composes `htRollBonus` unconditionally, and
because **a KDoc saying "this roll is HT" cannot fail**, the claim is read off
the row in the test that pins the composition. The fright rows are a **GAP, not
a refusal** — their attribute is in prose, and structuring prose is the
extractor's job (6dh). **The two scopes cross**, which is why the credit is a
second function and not a flag: Combat Reflexes reaches this roll and not the
table's own (its clause names the CONDITION, no attribute), High Pain Threshold
reaches the table's and not this one (its clause names knockdown). **Two
committed tests asserted the expired reason** — second instance of 6do in three
slices, and both said so in their own comments; `ActiveDefensesTest`'s seam test
exists to catch a GM being told a trait does nothing on a screen that applies
it, and was enforcing that very defect in reverse. **Half the reason survives
and is honoured**: `Rapid Recovery`'s "Stun durations halved" stays refused,
one roll is offered rather than one a turn, and the button says **"to recover
now"** (6cn). Its diff review returned a MEDIUM and a LOW, both taken — the
recovery roll omitted an active **shock**, which `Shock.appliesTo` refuses only
for `MAJOR_WOUND_HT` (that roll CREATES the shock) and reaches everywhere else,
so it is a separate parameter and never folded into `hurt`. **Not
device-verified.**

**6dq is next, and it is a refusal that named the wrong printing for the
right reason.** `Study.kt`'s KDoc cited *"Appendix A's SPI recovery"* as one of
the two rules this app leaves unmodelled for want of a clock — and Appendix A
is the SUMMARY. Its row is three cells (`Rest` / `1 SPI per hour` / the
requirement); p. 34 prints the rule and carries two clauses that row drops,
*"This is the standard and only recovery rate"* and the interruption clause.
**The reason never expired — only its subject and its address were wrong**,
which is the half of a refusal nothing ever re-reads. It now lives in
`Pools.UNMODELLED`, on the object that owns `spi()`, where a sweep over Kotlin
finds it (6cj: a parenthetical in an object about downtime STUDY is invisible
to a sweep for what `Pools` refuses). Read it for three more things. **The
exclusivity clause is contradicted in the letter** by p. 88's Sequence 9 Bard
(*"allies recover 1 SPI at the song's end"*, doubled at p. 100), and the
specific-beats-general reading ships **as this repo's, not the book's** — the
diff review caught the first draft asserting it as fact, and the book states no
priority. **The obvious close was refused**: an `spi_recovery.json` would be
data no Kotlin loads, the defect 6dg found and 6dh closed, so **chapter 4 stays
at 80.0%** — the third measurement that claiming is orthogonal to the
percentage (6di, 6dk), and the first where the honest move was to claim
nothing. And **no Kotlin test shipped on purpose**: a census over a one-entry
map written in the same commit asserts that the compiler compiled a string, and
the design review named that before it was written rather than after. Chapter
4's other unclaimed line prices Spirit Vision a second time and **says strictly
less** (no activation charge), so 7.6 governs and the dialog's price is
undisputed — 6da's bar applied to a line rather than to a modifier.

**6dp is next, and it is 6dn's finding a second time in three slices.**
Chapter 3 was the largest block left (56 unclaimed lines at 87.9%) and all 56
are a coverage proxy, exactly as 6cy predicted — the attribute ladder and the
skill ladder are arithmetic, so they read unclaimed forever. What the sweep
found was in the guard reading that chapter's summary box: `GoldenStudyPoints
Test` has pinned `PointBudget.STARTING_TOTAL` and `MAX_DISADVANTAGES` against
lines 2239-2244 since 6v, and **`MAX_QUIRKS` was asserted nowhere in 1665
tests** — because that box prints the -40 and says nothing about quirks. **An
assertion's corpus excluding the constant it cannot see.** The printing is
lines 1121-1122, *"Up to 5 **additional** points of quirks (minor personality
traits at -1 pt each) are allowed"*, and `additional` is the load-bearing word:
it is what makes the budget `70 + min(dis, 40) + min(quirks, 5)` instead of
quirks coming out of the -40 — and I read it the other way first, off line 1120
alone. Read it for three more things. **Nothing this app ships produces a
quirk**, checked against three corpora rather than one because 6de's verdict
failed within the hour for naming a single one: no `src/main` Kotlin constructs
`TraitKind.QUIRK`, no fixture carries one, and no `disadvantages.json` row
costs 1 — while `extract_sheets.py` files the sheets' own "DISADVANTAGES AND
QUIRKS" block whole as disadvantages, which is a block NAME and not evidence.
**The consequence is smaller than it looks and is stated that way**: a quirk
filed as a disadvantage credits the same point unless the character is at the
-40 cap, and no fixture is within five of it — a missing producer (6cf), not a
miscount. And the review corrected an overclaim: the first draft called the
term **unreachable**, but `report` takes any `List<Trait>`, so a caller can
hand it a quirk and the branch answers correctly — *no producer in the bundled
corpus* is the true claim. Its other finding is worth keeping: the Python pin
sliced `lines.json` positionally, and **a positional slice proves nothing about
the line number it claims to cite**; it keys by each line's own `n` now. And the one restatement in that chapter is **compared rather than believed**: line 2204 says the study grid follows the same ladder, which is the book asserting its two printings agree — all 16 cells are checked against `skill_ladder.json` in a test, RED-proved by drifting one.

**6do is next, and it is a whole table kept out by a reason that was true
about one column.** `AttackDialog.kt:113` and `extract_unarmed.py`'s docstring
both said Wrestling has no attacks because the book gives it *"None (grapple
only)"* and *"parsing it as a damage table would invent damage"* — both true,
and both about the DAMAGE column, while book lines 2602-2621 print a four-row
Wrestling table with different columns (**Maneuver / Roll / Opposed Roll /
Notes**) that no content file claimed. **The counter-example was in the file
the reason is written in**: `Shove` has sat in `unarmed_attacks.json` since M0
with no damage parts at all, its own KDoc arguing that "deals no damage" is not
"deals zero". Third instance running of 6dl's shape. **Five of the 23 fixtures
carry Wrestling or Escape**, so a Docks Bouncer picked Wrestling in the attack
screen and was offered nothing. Read it for four more things. **The reason was
read SELECTIVELY, not ignored** — the same summary row's third cell,
*"parry at -3"*, is applied through `ParryBlock`. **The subject decides the
shape, not the column count**: Grapple/Takedown/Pin are the grappler's and
`Escape-2` is the HELD character's, so the fourth row is left out as a GAP
(this app has no grappled state and no screen for the acted-upon character),
not a refusal — and the filter is *keep the rows rolled off this table's own
skill*, drawn from the table's definition rather than a blocklist. **An anchor
matched a maneuver NAME**: the maneuver `Escape` is spelled exactly like the
skill `Escape`, so the Roll-column anchor the two damage tables use read a note
as a row; the guard caught it rather than a silent bad parse, and the anchor
moved to the Opposed Roll column — **a column whose values collide with another
column's is not an anchor.** And **two committed tests asserted the expired
half** and had to be rewritten (`wrestling has no attack rows`, `Wrestling
prints no attacks, so it can roll no damage`): **a test can encode a reason,
and then it expires with the reason**, which is the inverse of this repo's
usual "the tests knew and production did not". Chapter 5: **74.4% → 76.3%**.

**6dn is next, and it is a count taken with the wrong filter.**
`extract_worked_rituals.py`'s docstring says *"Rechecked against all seven
costed rituals"* — the evidence CLAUDE.md 6aa's two-axis SPI decision rests on,
with `verify()` asserting it so it *"cannot quietly change"*. Every `SPI Cost:`
line in Section VIII says **nine**: Rituals 8 and 9 print one too, as RANGES
(2-4 and 4-8), and those two are the only costs in the section that **no rung
of the complexity ladder and no row of the weight table contains**. The filter
was *has a worked total*, not *prints a cost* — so **the assertion's corpus
excluded the only rows that could contradict it, and the exclusion and the
assertion were written in the same docstring.** Not repaired: a template prices
a CLASS of ritual across scopes where a rung prices one ritual, so `verify()`
now fails the day a printed row swallows a template range whole (4.2). Read it
for three more things. **The excluding sentence — *"they print ranges rather
than a worked total, so there is no arithmetic to check an engine against"* —
is TRUE and is about the ORACLE**, the 6dl shape again, and the file disproves
its own reach by already reading Ritual 8's template for two disagreements;
**Ritual 9 had group membership and nothing else.** Its review's finding is one
the widening itself created: `TEMPLATE_TIERS` and `TEMPLATE_SHIFT` are Ritual
8's facts and now searched a blob containing Ritual 9, so **a guard about one
ritual could pass on its neighbour's line** — cut at the book's own `Ritual 9:`
title, with its absence a stated failure rather than a silent fallback. **A
widened range is a widened scope for every predicate reading it.** And the
sweep behind it read **44 unclaimed mechanical lines across chapters 1, 2, 4,
6.5, 7, 9 and C** and accounted for all 44 — chapter 7's are `PowerSources`
constants its own KDoc refuses to make into content, so they read unclaimed
forever by design.

**6dm is newest, and it is a slice that deliberately did NOT close the thing
it was named for.** 6dl named `AntiDivinationTechniques.spiFor` as the one
function with no production caller, with the consequence that
`DetectionDialog`'s ward chip said `Spiritual Warding +2` over a caption
promising the SPI cost was "spent on the tracker" and **no number anywhere on
the screen**. The chip now prints the book's own `Cost` bullet — and `spiFor`
still has no caller, because the dialog offers exactly one ward (3A is the only
row in the section printing a `detectionBonus`) so the chip price and any total
are permanently the same number, and printing both is 6cr's third-copy defect.
**A sum function called on a permanent singleton is the sweep answering
itself.** Read it for three more things. The offered set moved out of the
dialog into `onDetectionRoll()` so the guard would not copy the predicate it
guards. Its review's sharpest finding — that the offered set can hold a row
`spiFor` cannot price — is **right in its diagnosis and wrong in its
prescription** (6cz): filtering the offer down to payable rows would withdraw a
printed rule for being unsummable, which is the 4.1 sin, so what ships is the
seam recorded in KDoc — *which rows move the roll* was never the same question
as *which rows can be summed*. And the wider slice is named with the two
questions it must answer first: whether `availability` gates the offer (against
`SealedArtifacts`' deliberate no-`canUse`), and how 3F's throw is kept out of a
composable.

**6dl is newest, and it is a stated reason that stayed TRUE and stopped being
relevant.** 6dk's cross-reference sweep ran clean — 35 claimed lines carry one
and every target resolves to a claimed file — and what it found on the way past
was in its own corpus. Appendix A line 9672 summarises chapter 5's Major Wound
table and **disagrees with it**: *"Fail by 5+ -> down 15 min"* against chapter
5's *"Knocked down and unconscious for 1d hours"*. Fifteen minutes against one
to six hours, on a row `DamageDialog` PRINTS to the player. Chapter 5 governs
(a summary may define but not overrule) so it is recorded, never repaired, and
`major_wound.json` is untouched. Read it for four things. **`extract_appendix
.py`'s reason for skipping the page — "a summary of chapters already claimed" —
does two jobs at once** (the lines restate claimed content, THEREFORE nothing
is lost) **and both halves have now failed separately**: 6da killed the first
at line 9667, and this kills the second, which is the worse failure because the
sentence remains true while answering a different question than the decision
needs. **Claim status was never the same question as agreement.** 6da read this
line and called it a restatement — verified with `git log -S` against its own
commit date rather than asserted, because 6ci and 6cj were both burned by a
count without its query. The file's docstring already recorded TWO
hit-location disagreements **in prose, which no sweep can read (6cj)**, and a
third that moves a displayed number sat between them. So the method is now a
GUARD: `verify_major_wound` compares clause by clause and
`SUMMARY_DISAGREEMENTS` names both printings in the 4.2 shape, RED-proved by
editing the committed row to match. Its review found **a one-way check with a
blind side** (the agreement clause read only the appendix's wording, so chapter
5's row could drift under a passing guard — 6dh again) and **an order
dependency** (it read `major_wound.json`, a file it does not write; it reads
chapter 5's own lines now). **No vacuous assertion in what the reviewer read** — a
weaker claim than "the first in eleven", because the tests were restructured
AFTER the review and the restructuring introduced one (a hand-typed literal of
two book lines with nothing tying it to them). **A review's verdict expires
when the diff moves under it.** A **twelfth** turned up while verifying the
tree for handover: `PoolsTest.kt:183` asserted `penalty != null ||
corGained == 0` on a NON-NULLABLE `Int`, so it short-circuited and could never
fail — and **Kotlin had been printing `Condition is always 'true'` on every
build the whole time**. The other eleven cost a reviewer; this one was free in
the build output. **Read the build's own warnings before asking for findings.**

**6dk is next, and it found the reference a CLAIMED sentence pointed at.**
Book line 4141 sits inside `ritual_preparation.json`'s claimed span and reads
*"The player narrates the incantation (**4-part Hermes structure — see
reference below**). The GM may award a Flourish Bonus (+1)..."* — and
`RitualDialog` renders a Flourish chip quoting only the SECOND half, while the
reference itself (4195-4204) was claimed by nothing and modelled nowhere. **The
app awarded a bonus for narrating a structure it never showed.** That is a
sweep direction this repo had not run — **a claimed line whose cross-reference
target is unclaimed** — and every line involved is filter-invisible, so 6di's
"not reported at all" holds a second time. The suite knew: the Flourish test
has quoted the whole sentence, parenthetical included, since that slice
(**fourth instance** of the tests knowing what production does not). Read it
for five more things. **6dj's named chapter 7.5 was the right lead and the
wrong content**: its summoning ritual is ALREADY rollable as a Heavy Full
Ritual, on p. 61's own "curses, binding, **summoning**, cleansing" rather than
on the shared noun (6cy's trap, checked from 7.5's side). Its 3d
creature-response roll is **not** the Entity Response Table — the book names
that table by its proper name four times and line 4730 does not, and the output
axis differs. Its ancient/regular mystical-language split is a **GAP, not a
refusal**: it partitions all six mystical languages and reaches nothing because
this app records a proficiency TIER and never which language, so 7.5 is the
first rule in the book needing language identity. Three structures ship as
three rows (the two three-line forms identify a CASTER and a CREATURE — merging
them prints a rule the book does not), the first two SELECTED by the book's own
"For Praying to Oneself, replace..." keyed on `RitualDialog`'s existing
`invoked` chip pair, the third `referenceOnly` because nothing can say a ritual
IS a summoning. **And the diff review caught a stale content file I had caused
and hidden**: I re-ran the extractor as `>/dev/null 2>&1`, it crashed on its own
new guard, and **a failing extractor never rewrites its JSON** (6cx) — 318 tests
stayed green because the Python tests build entries in memory and the Kotlin
test read only the fields that had not moved. Never redirect an extractor's
stderr; pin the field from the committed FILE. Chapter 7.5 still reads 0.0% and
chapter 7 still 85.9%, because none of the eighteen lines claimed carries a
number — **the second measurement that claiming lines moves no percentage.**

**6dj is next, and it overturns a refusal whose author had already written
down that its evidence was thin.** Book lines 3553-3558 — chapter 6.5's
skill-boundary table, three scopes of question and which of the two skills
each needs — were claimed by nothing, with `divination_support.json`'s claims
BRACKETING them (3559-3560 and 3563-3580). Its Complex row prices the
substitution at **-4**, and `extract_divination.py` had REFUSED that number as
"prose for the SAME axis the Question Difficulty table already prices", whose
Hard row is also -4; `verify_cross_default`'s own message called that numeric
agreement **"the only evidence"**. Re-run as a query it dies: the descriptors
are disjoint and they CROSS — "long-range" belongs to the **Moderate** band's
"distant" at **-2**, "multiple questions" to no band at all, "resisting
countermeasures" to `AntiDivination`, and neither of the Hard band's own words
(*future*, *abstract*) is in the bullet. **Two -4s pricing different facts,
agreeing by coincidence.** Read it for four more things. **Half the reason
survives and is honoured** — it is not a sixth row of the Step 3 modifier
table, so it ships as Section I's OWN table and `divination_modifiers.json` is
untouched. **A guard on a dead reason must become a guard on the live one**:
the old check asserted the two numbers agree; `verify_scope` now asserts the
two tables' descriptors stay DISJOINT, so a re-extraction that puts *future*
into that bullet reddens rather than silently re-merging the axes. **The cost
column is not a second charge** — Step 2 names Section II's method table as the
payment, and the tiers print 2 SPI where a Pendulum costs 1, so it is stored as
PROSE (`printedCost: "2 SPI"`), the type-level "shown, never applied". And
**"very high" is left unenforced**: the cross-skill default one line below
prints its own threshold ("at 12+") and this sentence prints none, so a cutoff
would be app-authored. Chapter 6.5: 92.3% → 96.9%. Its diff review returned one
LOW (the composition test pinned the band, which the difficulty test above
already pins, and left the scope penalty it actually composes unpinned) and two
explicit no-findings.

**6di is next, and it found a rule no coverage number could ever have
reported.** Book lines 2785-2787 — the Crippling paragraph, which governs the
hit-location table's entire `Special Effect on Hit` column — sit in the
**three-line seam** between `hit_locations.json` (ends 2784) and
`wounding_multipliers.json` (starts 2789), AND match **neither the old
`MECHANICAL` filter nor 6db's widened one**: their numbers are a fraction
glyph, a bare `1`, and a `12+` whose sign comes last where the filter wants it
first. **A line the filter cannot see is not reported unclaimed and is not
reported claimed — it is not reported at all**, which is 6db's finding at line
resolution, surviving 6db's own widening. Read it for four more things. The
column it governs **had no reader whatsoever** — `HitLocation.effect`, stored
on nine rows, unread in production AND in the test suite — while pricing five
outcomes against damage the app already held. **Arm and Leg print two numbers
and only one is the rule**: "> ½ HP of LIMB" governs and "~ HP/3" is the
BOOK's own approximation of it in character HP, so `approximate` travels with
the divisor rather than letting a test bless the approximation as the rule
(the design review's finding, and the one I would not have made). **Two of the
four refusals were already recorded in production source** — `Shock.kt` for the
knockdown cells, `Equipment.UNMODELLED["Armour"]` for DR — and citing them beat
re-deriving them. And the picker went to `DamageDialog`, not to the screen that
owns the table: `AttackDialog` has the location and the damage EXPRESSION,
`DamageDialog` has the AMOUNT and the target's HP, and **neither screen had all
three**.

**6di also measured something worth quoting: claiming those lines moved no
number.** Chapter 5 reads 74.4% before and after, because the filter cannot see
what was claimed — so **a coverage percentage is not a measure of whether a
chapter has been read**, and `coverage.py`'s filter has now been shown to have
false negatives twice, neither found by looking at the filter. Its own first
sweep also produced false positives by re-deriving `chapters.json`'s raw page
partition, which `coverage.py`'s comment says disagrees with the boundaries it
measures by — **a sweep that rebuilds the instrument's partition inherits none
of its corrections.** Its diff review returned four findings, all taken,
including the **eighth vacuous assertion in eight slices** (mine:
`thresholds.isNotEmpty() == effectsAt(99).isNotEmpty()` cannot fail, because
`effectsAt` filters that same list) and a prefix match that would read
"Blindness or disfigurement" as the Eye's definite "Blind" while leaving its
own raise-guard silent.

**6dh is newest, and its finding is that a summary can be wrong in both
directions at once.** It builds the slice 6dg named — `spirit_vision.json` was
the one content file of 79 with no loader, and the sole claimant of chapter
7.6, so `coverage.py` reported that chapter 100% off a file no Kotlin read.
Read it for five things. **Chapter 7.6's section C is not four-right-and-one-
wrong**: it lists five Sequence 9 pathways and there are five Sequence 9
grants, but it prints Visionary (whose Spectator block grants none) and omits
Moon (whose Apothecary block grants it). Same size, membership differing by one
in each direction — so a count check passes, a magnitude check passes, and the
row → block check that had been running since extraction finds half of it. **A
check that runs one way over two printings has a blind side, and the reverse
query is a separate sweep.** **21 blocks say "Spirit Vision" and mean three
things** — 9 grants, 7 upgrades, and 5 that are a DEFENCE against someone
else's (the Werewolf reveals only black hair; the Hypnotist's illusion works
"even against Spirit Vision") — so the predicate is a LABEL opening its own
clause, drawn from how the book writes a power rather than from the cases that
went wrong; and the common name is not enough, because the Mystery Pryer's
block writes only "Eyes of Mystery Prying". The design review moved the prose
matching **out of `core:rules` and into the extractor**: a matcher in the
engine is a second authority surface competing with the content files, so what
ships is structure. It also killed a branch — `reinforcedAbilities` is not
read, because every upgrade sits on a pathway granted at a higher rung, and a
branch no data can reach is one a later reader trusts; the invariant is
asserted instead. And **the FAILURE row shaped the screen**: "Incorrect
reading" is a confident WRONG answer, not an absence, so the dialog prints the
row's own words with no "Failure" label — a screen that prints the verdict has
modelled the die and not the rule.

**6dh also refused a MEDIUM with the lines that disprove it.** The diff review
said `Look again` rolls with stale inputs, `spiAvailable` never recomposes, and
the button stays enabled after the pool empties. `spiSpent` is
`mutableIntStateOf` (`BeyonderSession.kt:74`) read in the caller's composable
scope (`MainActivity.kt:1092`), and **nothing about spending SPI can move the
target**: `SpiritVision.targetFor` is `per + shock + hurt`, which read the
attribute and the HP/FP pools and contain no SPI term. Re-roll-and-recharge is
`ArtifactDialog`'s `"Use again"` exactly. What the finding was worth is that
the button did not SAY the second press costs a point; it now names the price.
Its two LOWs asked for Compose tests this repo cannot write, but the survivable
half was real — both test NAMES overclaimed, and one duplicated an assertion
from the test above it. **Seventh forced assertion in seven slices.**

**6dg is newest, and it shipped a guard that could not pass.**
`tools/extract_glossary.py` sat untracked and fully written, and had never
run: `verify()` asserted `len(ent) == 26` beside an assertion that
`Examples` is not a term, and those two cannot both hold — 25 entries with
the exclusion, 26 without. The book prints **25**; the docstring said 28.
Read it for five things. The broken count was **hiding a real defect**: the
closing note wraps onto a second line, `entries()` skipped only the first,
and `Will` ended "...and CoR. **Characters (4th Edition).**" — invisible in
any term list, because every term is present and one definition is longer
than the page. The replacement is CONSERVATION (the entries rendered back to
`Term: definition` must EQUAL the lines they came from), and its **first
version was vacuous**: it built its expected value with `stream()`, the same
function the parse uses, so widening `FURNITURE` dropped the line from both
sides and it stayed green — the mutation suite is what said so, and it now
reads the raw lines. Naming the furniture then **caught its own author**: the
list shipped with two page breaks and only ONE is in range. `TERM`'s comment
credited the length bound; measured, the bound is slack at 58 against a
longest term of 31, and the **full stop** the class omits is what does the
work. And **chapter G's 0.0% → 100.0% is nearly weightless**: `MECHANICAL`
matches three lines in the whole chapter, and the 39 lines of back matter in
its page range are prose, so they can never be counted as missing.

**6dg also names its successor with a query, and the query is a sweep
direction this repo had not run: every content FILE checked for a loader.**
79 files, exactly one unloaded — `spirit_vision.json`, which is also the
**sole claimant of chapter 7.6**, so `coverage.py` reports that chapter at
100.0% off a file no Kotlin reads. Field-for-reader (6ai), function-for-caller
(6cf) and row-for-consumer (6ci) sweeps are all blind to it by construction.
The first run of that sweep returned 79 false positives because a relative
path put the loader out of reach and every `grep` failed — **a failed command
read as a finding**, so the recorded query resolves the loader once and
asserts it exists. 6dg's diff review then found a **fifth vacuity in five
slices**, in a test file written to be careful about exactly that: five
assertions in `GlossaryContentTest` were forced by the exact ordered term list
above them, so two tests and two assertions were deleted and what they were
SAYING moved into that list's KDoc. An intent with no independent failure mode
is a comment, not a test. **The sheet itself is not device-verified** — read
6dg as compiles, unit-tested, not rendered — and its `LazyColumn` became a
scrolling `Column` for that reason: a lazy list inside a `ModalBottomSheet`
shares one gesture with drag-to-dismiss, and 25 short rows never needed lazy
layout. `ThemePicker` is not precedent for the lazy version; it uses a plain
`Column`.

**6df is newest, and it is three clauses nothing joined — plus a count that
could not be checked.** Three catalogue rows modify a bare Will roll and
reached nothing (`Incorruptible Will` +3, `Grief-Stricken` -2, `Evil Eye Fear`
-1); each is now a GM declaration on all three of this app's bare Will rolls —
self-control, Concentrate, and the Fright Check, which IS a Will roll and now
takes both clause lists. Read it for four things. **6de's "four rows" was right
and unreproducible**: it states no query, and the three obvious ones return 1,
5 and 4 — the filter is `Will rolls` PLURAL, which is what drops `Compulsive
Gambling`'s singular "requires Will roll at -2", already read as a self-control
check. A count without its query cannot be checked, and the fix is a test that
asserts the set and names the filter. **The scope discriminator is not whether
a cell adds a qualifier but whether the qualifier names a roll the book
PRINTS** — `Hard to Kill`'s "to avoid death" picks one of a printed pair, which
is what made 6de's scoping available at all, while "to act clearly" and "to
resist Beyonder abilities" name no printed roll, so all three are
circumstances. That killed both my split (two applied, one refused) and the
design review's per-row scope enum, whose five values would have had **one
inhabitant**; the reviewer had been told not to read the slice log, so **a
review's reach is bounded by the corpus you hand it**. And `Grief-Stricken` was
routed to `TraitEffects` by `SelfControlTest` in 6am — *"a modifier and
`TraitEffects`' business"* — and `TraitEffects` **never took the business**,
which is the third instance of the suite knowing what production does not, and
the third found by accident **after 6bz ran that exact sweep deliberately and
closed it**.

**6de is the slice before it, and it is two fields nothing ever read.** `Pools.hp` has
computed `rollsToStayConscious` and `rollsToSurvive` since M0; a grep for
readers returns `PoolsTest` and nothing else. The sheet printed the HP table's
own sentences — *"Roll 3d6 <= HT or fall unconscious"* and *"Roll vs. HT or
die"* — and **no screen could roll either**. `VitalRolls` gives both flags
their first reader. Read it for four things. The **scope is the slice**: three
traits name an HT roll and none means the same thing — `Fit`/`Very Fit`/`Unfit`
say "to all HT rolls" and reach both checks, `Hard to Kill` says "HT rolls to
avoid death" and reaches the `-HP` roll ALONE (dying starts one row lower;
widening a clause that names its own condition is 6ck's refused move), and
`High Pain Threshold` says "to avoid knockdown" and reaches neither. **`Very
Fit` was missing from `HT_ROLL_TRAITS`**, whose KDoc claims to hold every trait
saying "to all HT rolls" — invisible because `htRollNames()` returns the map's
own keys, so every guard asserted the data rather than the failure; the
catalogue is now the corpus that can disagree, and the diff review caught that
adding it also moves the **Major Wound's** roll, correctly and untested until
pinned. **Three recorded refusals cited "this app models no death roll"** and
all three moved in the same commit — one applied, two re-stated on the halves
of their reason that survive. And the sweep 6dd named came back **closed**: 16
`to all X rolls` shapes, the only open class is Will, all four rows are
CONDITIONAL and no fixture carries one — but the sentence that would have
stopped the next sweep ("nothing here names those") was loose and is corrected
in place. **A sweep that finds nothing still has a deliverable.**

**And its own reachability verdict was wrong within the hour.** 6de closed the
Will class as "no fixture carries one" — but `PurchasableTraits.offered()` takes
the WHOLE advantages and disadvantages catalogue minus Wealth, and none of the
four is excluded, so **they are buyable today**. Every census in this file that
used the 23 fixtures as its reachability corpus has that blind spot: a
purchasable row is reachable whether or not a premade prints it. The next
slice it named — a declared-condition chip on the three bare Will rolls — **is
built, in 6df**, and 6df corrects the shape it proposed: the chip is not the
`socialClauses` / `frightClauses` shape borrowed whole, because those lists are
each pre-scoped to ONE roll site by their own cells and a Will clause is not.

**6dd is next, and it is one sentence that had two reaches in one app.**
p. 40's *"Reeling: -1 to all rolls"* and p. 42's *"-1 to all rolls"* reached
`SkillKey` rolls only, through `Conditions.forSkill`'s ledger — while the
Major Wound row on the SAME page prints the identical phrase and `Shock`
carries it to all four `RollKind`s, on a warrant its own KDoc records
(*"'All rolls' is taken at its word"*). So a reeling, fatigued character
defended at full Dodge, Parry and Block and rolled their Fright Check,
self-control roll, Concentration, Detection and Major Wound HT roll at full
value, with their shock applied to every one. **6dc named this next and named
the wrong bar for it**: 6ck's "a printing that says active defenseS" is the bar
for widening a SINGULAR, and `-1 to all rolls` names a superset that already
contains a defence — a refusal's bar travels with the condition that produced
it, exactly as a precedent does (6cm). `Conditions.penaltyFor(kind, hp, fp)`
answers once and **refuses `RollKind.SKILL`**, which `forSkill` already
carries. Read it for three more things. **Shock's own exclusion does not
transfer** — it refuses `MAJOR_WOUND_HT` because that roll CREATES the shock,
an argument about WHEN, and nothing creates reeling mid-roll — so the two
states answer differently for one kind and the call site names it. The
composition order against 6dc's halving is a DECISION the book does not print
(`ceil(base/2) - 1`, not `ceil((base-1)/2)`; they differ at base 8), and the
diff review was right that its test **composes both readings by hand** and so
pins the difference rather than the order: what stops the reversal is the
BOUNDARY, since `DefenseDialog` never sees the unhalved score. And `spi` and
`cor` stay out of the new number because both printings name a scope.

**6dc is next, and it is the rule one screen printed while the next screen
ignored it.** p. 42's FP row — *"At 1/3 FP or below: -1 to all rolls; **Move and
Dodge halved**"* — had its -1 applied since 6h and its whole sentence printed on
the sheet, while `DefenseDialog` one tap away was handed the full
`vitals.dodge`. The threshold was re-derived at three sites inside `Pools`,
which is what let one screen print the rule and another break it; it is now
`Pools.isFatigued` with four readers, and `Pools.halvedForFatigue` halves the
BASE score only (the boost, Dive!, Retreat and a shield's DB print whole numbers
by rules of their own). **The rounding direction is this app's recorded choice,
not the book's**: that row prints "halved" bare where the book prints "rounded
down" (line 2450) and "round up" (line 6050), so there is no house convention —
up ships because it cannot leave a character worse off than the sentence states,
and both citations are pinned in `test_tools.py`. Read it for three things. Its
design review **corrected the warrant before any code existed**: I had cited
CLAUDE.md 4.3 for the rounding, and every `floorDiv` in `Pools` is a THRESHOLD
COMPARISON — a rule cited from the right file can still be the wrong rule.
6db's named cluster was **16 lines, not 15**, and three of those were already
implemented in Kotlin, which `coverage.py` cannot see — so chapter 5 stays at
**73.9%** and that is the honest number. And the diff review found a **fifth
vacuity in four slices**, again mine: a test named for the below-zero case
asserted the tired case's text. `docs/HOW-TO-PLAY.md`'s *"FP recovery … yours to
run"* turns out to be a real refusal with a mechanism reason (it sits under
"Track a clock it does not hold") that never covered this rule at all — **the
recovery and Regeneration RATES stay unapplied for that reason**, and
"Very Rapid Healing" (line 2861) is in no catalogue while `Rapid Recovery`
(line 922) and `Rapid Healing` (line 6928) are two other rules wearing similar
names. **It also leaves one sentence half-applied, deliberately**: 2852's other
clause, "-1 to all rolls", still reaches skill rolls only, and whether it
reaches a defence VALUE on a screen that never rolls one needs the bar 6ck set
for the stun — a printing that says "active defenseS". That is the named next
slice, ahead of 2899's Regeneration rule, which needs a reachability check
first.

**6db is next, and every coverage percentage in this file is now suspect.**
`tools/coverage.py`'s `MECHANICAL` filter decides which lines COUNT as rules
content; what it cannot see is excluded from the numerator **and the
denominator**, and `main()` reads an empty denominator as "chapter is
complete". It matched `N pts` but no other unit, so every price this book
quotes in SPI, HP, FP, CoR or DR — "Ability - Book Reading (1 SPI)",
"Attributes: 4 HP" — read as flavour. **Chapter 7.6, Spirit Vision Guide, is
claimed by NO content file at all and was reporting 100.0%**; it now reads
0.0%, which is what it always was. That is not a number nobody looked at:
**6cw's census shortlisted the four chapters below 100% and 7.6 was not one
of them**, because the instrument said it was finished — an unread chapter
was filtered out of the very sweep that exists to find unread chapters.
Widening the
filter to the other pools (derived from its own `N pts` condition, not from
the lines that were missed) made **286 lines visible, 33 of them unclaimed**,
and moved six chapters — 4 100.0%→66.7%, 5 80.8%→73.9%, 6.5 89.3%→81.2%,
while 6 and 9 ROSE because their newly visible lines were already claimed.
**Percentages recorded before 6db were measured under the narrower filter and
must not be compared with ones after it** — including this session's own
"ch. 8 21.2%→81.7%". Read 6db also for a RED proof that silently did nothing
(a `str.replace` that matched no text, printed `narrowed`, and left both tests
green — **a mutation that reports success is not a mutation that happened**),
and for the review's HIGH: my test was named for the reported percentage and
asserted only the preconditions, so an off-by-one in the page range would have
left chapter 7.6 claiming completion with every assertion still true.

**6da is next, and it reads the chapter 6cz called "probably correct".**
Appendix A's 32 unclaimed lines, one at a time: **31 are faithful restatements
and one is not.** Line 9667 prints `Direction Penalties: Side -2 | Rear -4` —
and `ActiveDefenses`' KDoc had refused the facing penalty saying it "has no
second printing anywhere in the book — checked, not assumed". **Two careful
exclusions hid it between them**: the KDoc's claim was verified by sweeping the
59 content files, and `extract_appendix.py` skipped the page as "a summary of
chapters already claimed" — true of the other 31 lines, false of the one that
summarises a rule nothing claims. Each was right about its own corpus. **A
sweep's blind spot can be another document's stated reason.** The design review
then returned NO-SHIP on applying it, with the sentence that settles it: *to
widen a singular, the second printing has to say something the first did not,
and this one says strictly less* — 9667 carries no noun at all. It also killed
my supporting argument: Retreat reaches all three rows because **ch. 5's own
bullet** enumerates them, not because the appendix prints it nearby, so a
neighbour that was applied is not proof the neighbourhood grants authority. The
refusal ships on the right reason, with `test_tools.py` pinning 9667's wording
and its nounlessness — an argument resting on a sentence needs that sentence
guarded.

**6cz back to 6cd had lead paragraphs here and they have been cut, because
this file went over the 150 k-char limit again (6eb: 152,583).** Every one of
them is in [`docs/SLICE-LOG.md`](docs/SLICE-LOG.md) unchanged and in full, and
the reading order below still names them. What they are worth in one line
each: **6cz** — an unavailable review gate is a reason to reach for the next
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

**Where to start next: 6eg, then 6ef, 6ee, 6ed, 6ec, 6eb, 6ea, 6dz, 6dy, 6dx, 6dw, 6dv, 6du, 6dt, 6ds, 6dr, 6dq, 6dp, 6do, 6dn, 6dm, 6dl, 6dk, 6dj, 6di, 6dh, 6dg, 6df, 6de, 6dd, 6dc, 6db, 6da, 6cz, 6cy, 6cx, 6cw, 6cv, 6cu, 6ct, 6cs, 6cr, 6cq, 6cp, 6co, 6cn, 6cm, 6cl, 6ck, 6cj, 6ci, 6ch, 6cg, 6cf, 6ce, 6cd, 6cc, 6cb, 6ca, 6bz, 6by, 6bx, 6bw, 6bv, 6bu, 6bt, 6bs, 6br, 6bq,
6bp, 6bo, 6bn, 6bm, 6bl, 6bk, 6bj, 6bi, 6bh, 6bg and 6bf.** 6cb is the slice that
did NOT build what it set out to, and the reason is the most reusable thing in
it: a design review, **re-asked with two questions and two files, answered in 34
seconds where the broad four-question version stalled for 38 minutes** (6af's
scoping lesson, measured). Its verdict then collapsed two roadmap items into
one — a printed cost RANGE and a printed `(specify)` are the book asking for an
instance in two notations, so the nineteen withheld trait rows are one feature
and not two. Read it also for a retraction: `pricedVariants` looked like it had
a blind spot (its regex needs a minus sign, so an ADVANTAGE enumerating three
priced options is invisible), and reading the whole cell killed the fix — the
sentence above those options says **"Cost scales with power"**, which makes it a
SCALE with worked points rather than a closed set, exactly like `Duty
(specify)`'s "-5 for light duty, -10 for hazardous". What actually keeps both
out is capitalisation, and the KDoc credited the other condition: **a safety
rationale is a hypothesis about which condition does the work.** And its census
caught its own author's number — 19 instances hand-counted as 18, quoted to a
reviewer before a test said otherwise. 6ca is next, the slice THREE
predecessors deferred, and it opens by disproving the reason all three gave.
"Purchasable traits collide with M3, because a 75-point blessing grants
attributes" — but the blessings were deliberately not extracted, and **not one
of the 198 catalogue rows grants an attribute at all**. A deferral is a claim
about the DATA and expires like any other verdict (6au, 6av, 6bv). Read it
also for the shape of the join it makes reachable: a trait cell saying "all
SPI-based rolls" is not "all SPI-controlled SKILL rolls", and **the book draws
that one-word distinction on this very attribute** — Spiritual Attrition is
"-3 to all SPI-based SKILL rolls" one chapter over, so the two bare rolls
(detection, divination) are evidence rather than an assumption. Its design
review then set seven conditions before any code, and the sharpest is that
economic status must stay ONE choice: the picker excludes wealth **below the
UI**, because a hand-edited save would otherwise hold two statuses and bill
both — the live double count 6bx had just fixed. 6bz runs the sweep 6bx and
6by both stumbled into and neither ran: **what does the TEST suite know that
production does not?** Both of those slices found their defect that way by
accident, so running it deliberately was an obligation (6ay). It found one more
— a test-side exclusion that had outlived its own reason by several slices,
skipping two rows whose costs then went unreconciled — and, more usefully, it
names why the two resolvers are ALLOWED to differ and pins the direction. Read
it for the shape of that answer: an audit must be a strict **superset** of
production, free to reach further (it must, to report a conflict on a
near-match) and never permitted to reach less. Two questions, two matchers, one
direction. 6by is next, the census 6bx's own
closing paragraph asked for, and it corrects that paragraph twice: the set is
**sixteen** spellings, not the nine I wrote, and it is not one list but three
shapes that look identical — a disagreement the book settles, a paraphrase
that leaves only resemblance, and a **gap** where the data marks something this
app cannot read. Read it for the discipline that separates them, because
calling a gap a refusal is how this file shipped four milestones of unreachable
rules. Its own gap is seven traits: the book marks a row instantiable two ways,
by putting "(specify)" in the NAME and by printing a cost RANGE the instance
settles, and only the first is read. **The obvious fix was refused and the
refusal is the more useful half** — nineteen rows print a range, and two of them
are the exact pair 6bw resolved through a separately argued warrant, so a
range-means-template rule would have retroactively made that warrant pointless.
6bx is next, and is the first slice whose finding was already written down **in
this repo's own test suite** — `ContentLoaderTest` has carried the alias
`"Poverty: Struggling" -> "Wealth: Struggling"` since M0, and production matched
on a prefix that answered null for the nine sheets using it. The test knew and
the code did not (6as's cheapest kind of miss), and the consequence was not a
missing display but a **live double count**: `applied()` filtered on the same
prefix, so picking a level left the sheet's poverty row standing beside the
chosen one — precisely what that function's own KDoc says it prevents. Read it
for two things. First, **a hardcoded alias in a test is a finding, not a
warrant**: the same map pairs "Absent-Minded" with "Absent-Mindedness", which
production deliberately refuses (6am), so the evidence had to come from the book
instead. Second, its design review caught a **regression the decomposition would
have shipped** — a uniform price-agreement rule stops resolving Fortune Teller's
canonical row, which has always worked — and split the design in two: *deriving
a level* and *being replaced by the picker* are different questions, and the
tenth sheet answers them differently. 6bw is next, because it is the first slice
to **overturn its immediate predecessor's own refusal** — and to be right to. 6bv pinned nine sheet spellings as permanently
unmatched, giving the reason "the sheet added a qualifier the book never asked
for, so the only evidence is resemblance". For two of the nine that sentence is
simply false: the cell ENUMERATES its own variants with their prices, and the
sheet bought one at exactly the price printed for it. **Correcting a stated
reason is not repairing a disagreement (4.1)** — and the seven whose reason
still holds are pinned unmatched in the same commit, which is what makes the
difference checkable rather than argued. Read it with 6bv, which it corrects,
and note that its design review had to go through `task`: `review --wait`
refuses a decomposition outright, because it injects a branch diff and finds
none. 6bv is next, and its
lesson is about **deferrals rather than sweeps**: it was handed two named
successors, ran a census of what the 23 fixtures ACTUALLY carry, and found one
unbuildable (no premade owns the trait, and no potion can grant one) and the
other unreachable on its own — the rows it wanted to apply were matchable only
after a separate name-resolution fix nobody had named. So two named next-slices
were one slice with a hidden prerequisite. **An unreachable feature and a
missing one look identical in the source**; only the data tells them apart. Its
design review then ran before any code and came back no-ship on two structural
findings, neither visible in a diff — the dialog boundary silently discarded
the input the whole feature needed, so it would have shipped looking correct
and reaching nothing. 6bu is next, for one thing
above all: **five successive censuses were each too narrow**, and the third
failed for a reason no regex fixes — a Fright Check is a roll the book NAMES
rather than describes, so no filter drawn from the word "roll" can see one.
Three such rows were invisible to every sweep this repo has ever run. Its answer
is to make the enumeration a TEST rather than a paragraph, and that test found
four more rows the moment it ran. It also records a field I proposed on a false
premise that my own KDoc had disproved since 6am, and a RED-proof that found a
hole in the guard I had just written. 6bt is next: it builds the slice
6bs named and finds 6bs wrong about it three times over — the banner count (five
in that section's prose, fifteen in the book), the reachability (both
unconditional rows reach nothing on any carrier, and every carrier that CAN roll
a social skill carries a conditional row instead), and the shape of the census
itself. **My own census regex hid two carried traits**, because it required the
word "skill" after the category word and their cells say "social rolls" — which
the book distinguishes from "social skill rolls" in one bracket of its own.
Read it for that: an instrument's false negatives are invisible by
construction, so the filter is the thing to attack. Its design review ran twice
and came back no-ship twice. 6bs is next: it was handed two named
successors by 6br and built neither, because a census of the traits the 23
fixtures ACTUALLY carry disqualified one outright — no premade owns it — and
turned up three better candidates. Its design review then caught the census
naming the wrong carrier, and a reachability check on the approved build
exposed something larger than the slice: **"+1 to all HT rolls" had never
reached the one bare HT roll this app makes**, on six premades, since 6n. Read
it as the argument for checking a deferral against the fixtures before
inheriting it. 6br is next: it corrects 6bp with 6bp's
own instrument — the clause sweep listed seventeen rows and that slice's triage
read one, leaving an identical unapplied trait on the same character's same
roll it had just device-verified. Its design review then blocked the obvious
fix and, in checking why, turned up seven fixture traits storing the name and
the book's effect cell as one string — which had made a self-control roll
unreachable on the character whose defining drawback it is, five slices after
that roll shipped. 6bq is next: it is the THIRD slice running
whose design review ran before any code, and the second to come back no-ship —
one finding caught a sentence in my own decomposition whose destructive reading
would have deleted a book rule for every character who lacks the trait, and the
other caught a value that would have thrown on the Roll button, which an
existing test had been pinning since 6k without anyone noticing what it proved.
6bp is next: its design review came back **no-ship on both builds it
proposed**, so the slice that shipped is a third the size of
the one that was planned — and it names its own successor with evidence. It
also declines to record a defect in 6bo that would have been real under a
different reading, and refuses a reviewer's API for being an abstraction with
one implementation. 6bo is next: its design was also reviewed BEFORE any code
and came back **no-ship**, which is what turned a declaration into a latch and
an accidental scope into an enum tested per roll class. It also caught itself
one commit from shipping a fresh excuse in place of the one it removed, and it
refuses a reviewer's central recommendation with a test rather than an
argument.
6bn is the FOURTH rule running to become buildable as a declaration rather
than through the round tracker 6bd ranked first, and it takes the opposite
shape to 6bg deliberately — one chip where the defences needed three, because
the book prints one sentence rather than three. It also names the next slice
with evidence: the Major Wound's success row prints a shock penalty nothing
applies, which disproves the excuse `TraitEffects` records for High Pain
Threshold. 6bm
is the third slice running to reach a printed rule with a DECLARATION rather
than the round tracker 6bd ranked first — an aim the shooter defended through,
with the cancelling sentence printed one table away from the rows it governs.
6bl found a rule THIS APP hands out and never charged for — three of its own
tables leave a character stunned and the -4 that costs reached nothing — and
then the device found the sharper half: a "not applied here" disclaimer that
had been false since the slice before it, with the test quoting the
disclaimer's own words and therefore unable to notice. **Commentary about what
is unapplied expires exactly the way a sweep verdict does (6ba)**; only a test
that reads both objects can see it. It also writes down every verdict of its
clause sweep, including the ones that found nothing, which is 6bj's lesson
applied on purpose rather than after the fact. 6bk
turned one named next-slice into two rules by reading the page it lived on,
and its three review findings were all in the UI state machine rather than
the rules — a picker that charged twice, one that locked up, and one that
sold a defence the same screen calls unavailable. **The rules were right and
the affordance was not**, which is the half no unit test reaches. 6bj found
three rules on one printed line with one applied — and the sweep from 6bh had
ALREADY flagged that cell, so what failed was the triage, not the
instrument. Re-running a sweep you have run is cheap. 6bi is the other half
of the same lesson: one word ("Regular") decided a rule's whole scope, and
Codex still found three states the reading did not cover — a scope argued
from the text is not the same as a scope that covers every state the screen
can reach. 6bh runs the clause
sweep 6bf named and finds a hole in 6bg, committed an hour earlier — the
newest evidence that a slice's own verdict expires fastest. Together the
three **correct 6bd**, whose "round tracker unblocks five rules" ranking
survives reading only one of the five. Read 6bd after them, not before, or
you will inherit the ranking they correct. 6be RETRACTS 6ba's parse finding with a
test, and the two defects it found instead were both on the sheet screen,
where every roll a Beyonder makes had been using the level printed before the
potion.

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
| Tests | **1743 Kotlin** (JUnit 5 — 1235 `core:rules`, 488 `app`, 20 `core:model`) + **356 Python** (pytest), all green — counted from the JUnit XML, not from memory. **This row was stale by two on the Python side before 6dg**: it read 300 where `HEAD` carried 302, so re-count rather than adding your delta to what it says. A filtered run (`--tests`) leaves the results directory holding only what it ran, and Gradle then reports the unfiltered task UP-TO-DATE — count after a `--rerun-tasks`, or you will read the filter's leftovers (measured in 6dg: 489). **The `app` module writes to `build/test-results/testDebugUnitTest/`, not `.../test/`**: a glob over `test/` alone silently drops all 466 and still reports a green total (6dc). The `app` jump from 92 is `ThemeSchemesTest`, which sweeps every `ColorScheme` role by reflection and so emits one dynamic test per role per preset per mode |
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
today. Turning it on strips the generated serializers and breaks all 77 content
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

## 6a-6eg. The slice log

**Moved to [`docs/SLICE-LOG.md`](docs/SLICE-LOG.md)** — 454 KB of it, which put this
file over the 150 k-char limit. Every slice from 6a to 6eg is there unchanged,
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

- *Route all file edits through the opencode MCP* (`mcp__opencode__opencode_run`,
  provider `opencode`, model `deepseek-v4-flash-free`). Codex has no such MCP;
  edit directly.
- *Use the sequential-thinking MCP before acting.* No equivalent; reason
  normally.
- *Sub-agent debate:* for any decision, design, plan, or trade-off, spawn 3
  parallel agents with distinct stances (proposer / skeptic / pragmatist), read
  their outputs, converge in at most one more round, then act. Skip for trivial
  or mechanical prompts. Adapt to whatever parallelism Codex offers, or take
  the multiple perspectives yourself in one pass.
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

**Codex is the review gate now** (section 7 lists the invocation). In M4 it
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
