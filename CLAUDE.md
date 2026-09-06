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

**6cz is next, and it is a process finding before it is a code one.** 6cx
and 6cy both shipped recording "Codex is out, the free panel returned empty
bodies, diff self-checked". The owner asked for `ecc:kotlin-reviewer` — the
documented Codex-exhausted fallback both slices had walked past — and it
returned **BLOCK on two HIGHs**. **An unavailable gate is a reason to reach for
the next gate, not a reason to record its absence and carry on.** What it found:
`Equipment.forSkill` matched name AND specialization exactly, but the catalogue
distinguishes `Navigation` (`specializable = false`, three printed rows — strict
match correct) from `Survival` (`specializable = true`, one row, "specify
terrain" — so `Survival (Woodlands)` is an INSTANCE). `hunter_tracker.json:85`
buys exactly that, so **the one character in the corpus who would use the bedroll
was the one who could not see it** — the false-negation failure 6cx's own design
review named in the abstract, shipped in the concrete. Note the reviewer was
right about the defect and **wrong about its reach** (it called the row
unreachable in today's fixtures; one parse finds it) — a reviewer's reachability
claim is a claim about the data and expires like any other. A `ecc:python-reviewer` pass over the two extractors then returned no HIGH but
three **guards that could not fire** — a floor where the failure adds a row, a
`% 3` blind to three cells wrapping at once, and an unbounded `&`/`or`
splitter. Its prescription for the third could never fire either (no skill name
contains `" or "`), so **take a reviewer's diagnosis over its prescription**.
It also found two
**vacuous assertions inside 6cy's own anti-vacuity test file**: a "derivations"
test that never called the engine, and a `step == null || step == "level"` that
all five rows regressing to null would satisfy. Third vacuity in two slices, and
the pattern is one thing — **the assertion was written from the shape of the
DATA rather than the shape of the FAILURE**.

**6cy is next, and its finding is that `pts/level` meant two different
things.** `extract_core_rules.py` had skipped chapter 2's Secondary
Characteristics table with a recorded reason — "ch 3 prints the costs, which
are already extracted". Chapter 3 prints no such thing: its `pts/level` lines
are ADVANTAGE rows (Hard to Kill 2/level, Charisma 5/level), a different table
wearing the same words, and `"Cost to Modify"` occurs **once in the whole
book**. So the six constants `PointBudget` bills every character with had no
tie to any printed line. **The gap was measured, not argued**: move the
extracted HP price from 2 to 4 and exactly **one test in 1597 fails** — the new
one. `PointBudgetTest`, `SecondaryCharacteristicsTest` and `GoldenSheetTest`
stay green while book and engine disagree about what every character paid. Read
it also for a guard that **could not fire** (the priced-set check ran first, so
the unbuyable-row check was unreachable — found by writing the RED test for the
GUARD rather than for the feature), and for the sweep's four cleared clusters,
which are the deliverable: a table implemented as arithmetic reads 0% forever,
so ch 3's 40 unclaimed lines are the coverage proxy, not a gap. Chapter 2:
**30.8% -> 76.9%**.

**6cx is next, and it builds the chapter 6cw named.** Chapter 8 goes from
**21.2% to 81.7%**, unclaimed mechanical lines from **82 to 19**, and all 19
sit inside four tables refused in production source (`Equipment.UNMODELLED`,
to the `ActiveDefenses.UNPAYABLE` standard) rather than overlooked — checked
line by line, not inferred from the total. **Its most reusable finding is that
a precedent travels by ROLE, not by shape.** The design review returned
NO-SHIP on a decomposition that modelled equipment chips on `AttackDialog`'s
weapon picker: `Weapons.forSkill` is ungated and offers the same chapter's
catalogue, but it **re-parameterises** the roll (which attack, which damage,
melee or ranged) where a bonus chip **adds to** it — and `RollDialog`'s own
possession control, `socialClauses`, is gated twice, on category AND on a list
of traits the character carries. Two controls can be pixel-identical and
mechanically opposite; "the app already does this" was a claim about the
screen where the warrant needed to be about the semantics. What shipped is
reference rather than a modifier, on the precedent four lines above it in the
same function — `conditionalBurnNote`'s "**Shown, never applied**". Read it
also for a **RED-proof that indicted the tests**: neutering the signed-clause
predicate makes the extractor refuse to run, and ten of the eleven Kotlin
tests still pass, because **a failing extractor never rewrites its JSON** and
they re-read the old good file. Only `verify()` pins the parser. And for
6cw's census being too narrow in its turn — 47 signed clauses, not 34, in six
shapes spanning three subjects. **The diff got no independent review**: Codex
is out and the free-model panel exited 0 with two empty bodies after sixteen
minutes, which is a silent no-answer and not a clean bill.

**6cw is next, and it named the largest thing outstanding at the time.**
`tools/coverage.py`, re-run after a long gap, puts **chapter 8 at 21.2% with 82
unclaimed mechanical lines** — an equipment catalogue this app has never seen:
armour with DR, a three-tier currency, and items carrying clauses like "-1 to
Lockpicking", "+2 to First Aid & Surgery", "+1 to Ritualistic Magic". The join
was checked before the slice was named: 34 signed clauses name 30 skills and
`skills.json` already holds nearly all of them, so the bonuses have rows to
land on. Three seams decide its shape — **`Forced Entry` and `Gather
Information` are named by chapter 8 and absent from chapter 3's skill list**
(a 4.1 disagreement to record, not repair), `Fortune-Telling` vs `Fortune
Telling` is a hyphenation seam of the 6bx/6by kind, and most clauses are
CONDITIONAL ("+1 to Search for fine details"), which 6bt already showed is a
different feature from an unconditional one. **6cw builds nothing** — it is the
census and the evidence; the build deserves its own decomposition and design
review.

**6cv is next**, and it closes the fixtures' `published` block: every field is
now either reconciled against the engine or recorded with a reason. Its subject
is `cor`, the last one with no reader — and unlike `spi` there is nothing to
read it into, because `Character` declares no corruption at all (CoR is session
state). A sheet printing a nonzero starting CoR would have it dropped in
silence, so the slice is one guard that says so. **Its most reusable finding is
that a predicate is a claim about what a null MEANS**: I tightened
`(cor ?: 0) == 0` to a strict `== 0` and the review pointed at `Premades.kt:80`
— "Absent means mortal" — which makes null legitimately zero, so the strict
version would have failed on a state this repo already calls correct. Read it
also for an editing hazard this session hit: a PowerShell
`Get-Content -Raw | Set-Content` round-trip **re-encodes UTF-8 as ANSI** and
mangled every em-dash in this file and the slice log; use the editing tools for
prose.

**6cu is next**, and its most reusable finding is that **a gap has a
direction, and the direction is the evidence.** It reconciles the two remaining
published budget columns and finds five disagreements, all but one being an
error this file already records, resurfacing where nobody had looked: the
librarian's disadvantage gap shows up in `bpTotal` and `unspent` at **+5**, the
scholar's advantage gap in `unspent` at **−10**, and the opposite signs are
structural (a disadvantage inflates the BUDGET, an advantage inflates the
SPEND). A magnitude guard would pass under either. Read it also for a suspicion
the source settled: Beat Cop's lone non-null `unspent: 0` looked like an
extractor artefact, and `Beat Cop.xlsx` row 1 prints exactly that — **the
source spreadsheets are on this machine, so extraction questions are
answerable rather than arguable.** And for an exclusion that was not needed:
`playable()` drops the blank template without one.

**6ct is next**, and it sweeps a corpus nobody had swept: **the 23 fixtures.**
32 keys, all occurring in Kotlin; counting readers outside the model
declarations leaves five, four already reconciled, and one — `totalSpent` —
with its declaration and nothing else. The sheet's printed TOTAL SPENT had
never been compared with what the rules compute. Wiring it up found exactly one
disagreement, Monastery Scholar's 99 against 89, and that is 4.1's existing
advantages error surfacing in a second column: a test now asserts both gaps are
ten and EQUAL, so if they diverge one spreadsheet error has become two. Read it
for the count guard that caught its own author — `grep -l` says 23 fixtures
carry the field and the loaded answer is 22, because Beat Cop's file has the
key with a `null` value. **A grep for a key is not a count of a value.**

**6cs is next, and it ships a refusal rather than a feature.** 6cr's census
found the one constant in 96 that occurs once in the repo —
`Divination.SPECIALISATION_BONUS` — and the obvious slice was a chip offering
its +1. The design review said NO-SHIP: the same book sentence prices it as a
GURPS technique this book does not reprint, and nothing in the corpus records
which method a diviner trained in, while every other bonus on that screen is
gated on an owned list the CALLER resolves. Its sharpest finding was against my
state design — a `remember(method)` reset would have turned one permanent
point-bought technique into a free +1 on whatever method was picked, because
`spiChoice` resets by RE-DERIVING a value and specialisation has nothing to
load. The refusal is recorded in `Divination.UNAPPLIED` (the
`ActiveDefenses.UNPAYABLE` standard, discoverability not strength) and shown on
screen. Read it also for a string with two readers: the reason renders to the
PLAYER, so the developer's argument moved into the KDoc and the sentence
stayed; for a block that **inherited the branch it was pasted into** (the
refusal first shipped inside `if (ownedSupportSkills.isEmpty()) … else`, so
only the diviners it did not concern would have seen it); and for the corrected
constant census, which is **clean** — 100 constants, one hit, and that hit is a
test locator for a row the screen renders by loop. **No slice is named next.**

**6cr is next**, and its most reusable finding is that **a quotation and a
restatement look identical in a grep.** It ran 6cq's named successor and found
three of the four hits were code comments citing the page — deriving them would
make the source stop saying what the book says — with one real duplicate. The
discriminator is not the string: ask whether a rules value would have to move
for the sentence to become false. Read it also for a **fifth `+2` in
`DefenseDialog`**, in a file two earlier patterns had already searched (a
widened query is not a finished query), and for a number **deleted rather than
derived**: `"+1 each"` was a third copy of a value the chips beneath it already
print per row, and is each row's own field rather than a constant.

**6cq is next**, and its most reusable finding is that **a comment is not a
pin.** It runs a seventh sweep direction — numbers a screen types in English
that production already reads from content — and the six sites it found are
only half the slice. Chasing one of them (`"advancing now also costs 10+ CoR"`)
reached `PotionConsumption.attempt`, which charged a Kotlin literal `10` while
`cor_gained.json` prints `minimum: 10`, and `CorruptionTest` already had a test
joining them — in its COMMENT. The test pinned the data; nothing pinned the
charge. Read it also for a query that had to be widened before it was right
(the first pattern missed a fourth `+2` in a file it had already searched), and
for a design review that refused to invent a check: **no test in this repo can
reach a Compose label**, so deriving removes the drift rather than catching it.

**6cp is next**, and its most reusable finding is that **a refusal can
survive its author's stated reason and die of a fact none of its words
mention.** 6co refused the round boundary because a latch on one control would
claim a round the others could not honour — a claim about REACH, which had
already changed: three free actions, a parry stepper and a paid boost all sit
in one composable, so one assignment honours the paragraph for all of them.
6bf's separate "not worth global state" survives untouched, and nothing here is
global. Read it also for a guard that is a MEMBERSHIP assertion — a field on
`RoundState` is a value a new round clears, so `stunned` (its own 1d6-second
clock, line 2826) reddening the test is the point — and for three design-review
findings taken before commit: a `LaunchedEffect` is not the guard a `remember`
key was (it fires after the pass commits), three comment blocks left orphaned
above the wrong declarations, and a clearing control parked beside "Close"
where a mis-tap drops FP already spent.

**6cp's own closing paragraph was wrong for an hour, and the correction is in
it.** It named 6cb's successor — the nineteen withheld trait rows, one record
shape — which **6cc had already built** (`InstancedTraitDialog.kt`,
`InstantiableTrait`, `PurchasableTraits.instantiableIn`), along with both items
filed behind it (`QUOTED_INSTANCE_ROWS` resolves the six `Sense of Duty`
instances; `Secret` stays unmatched with its reason recorded). **A closing
paragraph is the one part of a slice log nothing verifies** — every other claim
has a test or a book line behind it, and "what to do next" has only the last
author's memory. One grep per named artefact is what checking it costs. So
there is **no named successor outstanding**; the next slice needs a fresh sweep,
and the directions already run (fields with no reader, functions with no caller,
rows with no consumer, seams between claimed spans, rules the app contradicts,
what the tests know that production does not) each expire rather than standing.

**6co is next**, and its most reusable finding is that **a condition can
outlive the question it was standing in for**. The Block row's "Against ranged
attacks" note fired on `shot != melee`, a correct proxy only while every other
term on the two sides was identical; a melee-only bonus on one side would have
had it announce a shield penalty the shield does not have. It now asks the
shield's own rule instead. That is 6bl's defect one step earlier than usual — a
trigger drifting from its stated reason, rather than a disclaimer outliving its
rule. Read it also for a refusal that needed its OWN warrant rather than its
siblings' (the two Dodge-only bullets stay out of `everyDefence` because they
name one row; Retreat stays out because its three numbers DIFFER), and for two
warrants found already printed instead of argued: the Acrobatic Dodge's cell ends
"Stacks with Retreat.", and `ActiveDefenses.UNPAYABLE`'s own text answers why a
one-attack bonus may show on five rows at once.

**6cn is next**, and its most reusable finding is that **a spec which omits a
label is not a spec that has no label** — the implementer picks one, and the
default is the worse one. It wires up the prone -4 that `RangedTest.kt:112` was
the only reader of, and its design review cleared both the mechanism and a
refused tidy-up before catching the one thing the decomposition never wrote down:
what the new chip's text is. Read it also for a scope chosen from the data's own
definition (`melee != null`) and then CHECKED — one grep over the book's own
lines proves that table prices exactly one condition for reach — and for a
guard that is the whole slice: two helpers reading one `plan`, mirrored so that
exactly one can speak, RED-proved by deleting it and watching the roll take -6.

**6cm is next**, and its most reusable finding is about a BAR rather than a
rule. 6ck refused the facing penalty because its bullets print "to defense"
singular with no second printing to widen them; Dive! sits in that same bullet
list and has no second printing either, so the obvious move was to refuse it the
same way — and that would have been wrong. **A precedent travels with the
condition that produced it, not with the shelf it was found on**: the facing
bullets need corroboration because they never say WHICH row, while Dive! says
"+3 Dodge vs. ranged only" and names both its scopes itself. Read it also for a
cost checked rather than assumed — diving leaves you prone, and the only line in
the book that prices prone is on the ATTACKER's to-hit roll, so a Dodge penalty
invented to "balance" the +3 would have been a rule authored from a resemblance.
And for where the answer to its design question lived: p. 36's free-actions
paragraph, in the seam **outside** the span `defense_modifiers.json` claims —
6ba's lesson applied to a REASON rather than to a rule, which no coverage number
can report as missing.

**6cl is next**, and its most reusable finding is about a REASON rather than
a rule. It withheld the Acrobatic Dodge roll from an untrained character citing
6aw's recorded defect in `SkillLadder.defaultFor` — and a design review pointed
out that 6aw's three overrides are the *unarmed-parry* skills, so the cited
defect could not answer wrong for Acrobatics at all. **A recorded defect is
evidence about the inputs it was measured on**; the refusal survived only
because reading `common_modifiers.json` produced a real reason (the untrained
default is stored as a RANGE with no number). Read it also for the finding that
changed a number: rolling through `SuccessRoll` instead of `SkillRoll` would
have had a reeling character roll Acrobatics at one target in the defence dialog
and another on the sheet, because `Conditions`' HP/FP penalties are unscoped.
And for a latch refused as the mirror of 6ck's own refusal — enforcing a
sentence printed PLURAL ("Each of the three free actions…") on one member.

**6ck is next**, and it found the same false sentence in THREE files: "the
book prints no arc to test against", explaining why facing is unmodelled. The
book prints the arc — "Attacked from side: -2 to defense" / "-4 to rear" — in
the list the defence dialog itself renders, and the model's version of the
excuse was drawn from ONE row (Acrobatic Dodge's ±2) and generalised to the
table. Read it for what the design review did with it: it returned **NO-SHIP**
because my warrant for composing the penalty into `everyDefence` was the stun's
*insufficient-alone* reading — that rule reaches every row only because a SECOND
printing says "active defenseS", and facing has no second printing anywhere
(checked across the book and re-checked by the diff reviewer across all 59
content files). So the penalty is refused, pinned by a test asserting the
bullets still read "to defense" singular — and the same check found the half
that ships, the shield DB's arc, printed plural and enumerated. Read it also for
the reviewer's other HIGH: the Block row builds its arrow by hand, so gating
only the shared helper would have left the bonus on exactly the row the change
exists for.

**6cj is next, and it is the slice that did NOT build what it set out to.**
6ci named `All-Out Defense (Double)` as next on the grounds that it had "no
code, a test, a comment, or a recorded reason" — but **6bl had refused that row
four slices earlier, in the slice log**, with a mechanism reason. 6ci's sweep
iterates content rows against Kotlin, so a rationale kept in a prose journal is
invisible to it by construction: read it for that first. Then for what happened
when 6bl's reason was re-run as a query — its PROVENANCE half expired (line
2453 is extracted, stored and on screen, and the app reads only its
parenthetical while the main clause, the one-defence limit, reaches nothing) and
its MECHANISM half survived, so **provenance evidence cannot answer a mechanism
objection**. A design review returned NO-SHIP on three HIGHs before any code
existed, including that I had copied a "charge on first selection" trigger whose
semantics do not transfer — the precedent delivers what you bought at the first
pick, this row delivers it at the second. Read it also for 6ci's other three
deferrals, all closed as artefacts of its own instrument: the sweep's "modelled"
predicate is a substring search, so its sibling ratio means nothing for a table
consumed by loop, and 6ci hardened its *test* against exactly that predicate
while leaving its *shortlist* built on the unhardened one.

**6ci is next**, and it starts by disproving the paragraph directly below
this one. 6cg and 6ch both closed by naming `Ether Body Damage` / purchasable
traits as "the largest thing outstanding"; **6cc had shipped them four slices
earlier**, and the item behind it was shipped too — two closing paragraphs
copied forward instead of re-derived. Read 6ci for three things. Its **sweep
iterates a third unit this repo never had** — 6ai swept extracted FIELDS for a
reader, 6cf swept public FUNCTIONS for a caller, and neither can see a **row**
nothing consumes; the signal is the sibling ratio inside one table, never the
raw miss count, because a table consumed by loop correctly names none of its
rows. Its **design review was right to say NO-SHIP and wrong about why**, and
what settled the question was not a better argument but a census over the
attribute the disputed rows share: the book prices nine rows across its two FP
tables and this app charged **seven**, so those rows were never out of scope —
they were *unpayable*, which inverted my own proposal to record them as
unapplied. That review's first finding was also a **correction to me**: my
decomposition's central argument named `ActiveDefenses.boostFor`, which does
not exist. A design document is an argument made of claims about code, and one
grep per symbol is what those claims cost. The diff review then found a
**third** plan transition living inline in the dialog, which `atRange` and
`withAttack` were both written to prevent — invisible until an unscoped option
existed for it to drop.

**6ch after it**: `AttackDialog` passed `spiAvailable = 0` as a LITERAL, and
**no slice in the 7400-line log ever argued for it** — so the dialog told a
Beyonder at full SPI they had "no spirituality left", one line under the rule
granting the push. Reachable on a character's FIRST potion (Ritualistic Magic
is granted at Sequence 9 of six pathways). Read it for two things. Its design
review made it CHECK its own reachability claim rather than assert it — the
Roll button turned out to need no weapon, but that was a claim until
`AttackDialog.kt:521` said so. And **its two reviews contradict each other**:
the design review requires `spiAvailable` be live and never `remember`ed
(two attacks of one maneuver share one pool), and the diff review's only
finding proposes memoizing it for recomposition cost. The correctness axis
wins and the refusal is recorded — **a LOW that would reintroduce a MEDIUM is
not a LOW.**

**6cg is next**: it takes 6cf's deferred `attackOptions` loader and finds it
is neither of the two things 6cf offered — the rules ARE applied, and what was
missing is the **direction of the pin**. The Python extractor pins the book's
sentence INTO the JSON; nothing ran JSON → Kotlin, so the only two constants in
`Attacks` with no tie back to their row were exactly the two whose row lives in
the file with no caller. **The absent caller and the absent guard are one fact,
not two.** Read it also for the review finding: the first guard asserted
`"$n to skill"`, which **a sign flip satisfies** — `"6 to skill"` is a substring
of `"-6 to skill"` — so a guard against drift in MAGNITUDE was blind to drift in
SIGN, the cheaper mutation and the one this repo has already measured once
(section 5, base -4 landing on +2). And for a finding refused: the same check on
the neighbouring row, whose clause `PINNED_OPTIONS` already pins whole.

**6cf after it**: it wires up a +2 the divination screen has never asked
for — extracted in 6p, tested, documented, and called by NOTHING. Read it
for the sweep that found it, which is a sixth direction this repo had never
run deliberately: **every public rules function checked for a UI caller**.
323 functions, 46 uncalled, 3 real. 6aj and 6be each hit that class by
accident; two of two by accident is not a method.

**6ce after it**: it builds the table 6bc read and left, and that THREE
slices deferred — 6bc, 6ca and 6cb — each on a reason that had already
stopped being true. Read it for the shape of the check that costs a minute
and settles it: **"occult skill" is printed nowhere in this book**, so the
slash in "+5 to all occult/knowledge skills" cannot be joining two groups,
and one grep decides a reading two other readings would have authored. Read
it also for a guard that had been blind to an entire map since the slice
that added it — under a comment warning about exactly that failure.

**6cd is the one to read after it**, for a sentence that settles a question this
file has circled four times: **"cost-inside-bounds corroborates pricing
validity, not catalogue identity."** It refuses a rule that resolves two more
sheet spellings and that excludes every objection previously raised against it
— because excluding counter-examples is not the same as producing evidence.

**Where to start next: 6dh, then 6dg, 6df, 6de, 6dd, 6dc, 6db, 6da, 6cz, 6cy, 6cx, 6cw, 6cv, 6cu, 6ct, 6cs, 6cr, 6cq, 6cp, 6co, 6cn, 6cm, 6cl, 6ck, 6cj, 6ci, 6ch, 6cg, 6cf, 6ce, 6cd, 6cc, 6cb, 6ca, 6bz, 6by, 6bx, 6bw, 6bv, 6bu, 6bt, 6bs, 6br, 6bq,
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
builder (attributes against a live point budget). `docs/HOW-TO-PLAY.md` is the
player-facing guide to all four and is verified against the labels in source.

| | |
|---|---|
| Tests | **1650 Kotlin** (JUnit 5 — 1164 `core:rules`, 466 `app`, 20 `core:model`) + **308 Python** (pytest), all green — counted from the JUnit XML, not from memory. **This row was stale by two on the Python side before 6dg**: it read 300 where `HEAD` carried 302, so re-count rather than adding your delta to what it says. A filtered run (`--tests`) leaves the results directory holding only what it ran, and Gradle then reports the unfiltered task UP-TO-DATE — count after a `--rerun-tasks`, or you will read the filter's leftovers (measured in 6dg: 489). **The `app` module writes to `build/test-results/testDebugUnitTest/`, not `.../test/`**: a glob over `test/` alone silently drops all 466 and still reports a green total (6dc). The `app` jump from 92 is `ThemeSchemesTest`, which sweeps every `ColorScheme` role by reflection and so emits one dynamic test per role per preset per mode |
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
`SkillRoll`, `SuccessRoll`, plus `content/ContentLoader` reading all 79 JSON
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
`unarmed_attacks.json` (10), `range_penalties.json` (7),
`ranged_modifiers.json` (9), `hit_locations.json` (9),
`wounding_multipliers.json` (8), `spirit_damage.json` (15),
`major_wound.json` (3), `shields.json` (4), `shield_rules.json` (3),
`glossary.json` (25), `spirit_vision.json` (4 sections + 7 grants),
`divination_difficulty.json` (5), `divination_methods.json` (8),
`divination_modifiers.json` (8), `divination_clarity.json` (6),
`divination_support.json` (4), `ritual_preparation.json` (5),
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
VitalRolls.printed(check): String             // CONSCIOUSNESS at 0 HP, SURVIVAL at -HP
TraitEffects.deathRollBonus(traits): Int      // Hard to Kill, survival roll only
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
SkillRoll.at(label, base, key, source, mods, ctx): SkillRollOutcome
FrightCheck.against(will, modifier, source): FrightOutcome   // two rolls
FrightCheck.effectFor(roll) / .text(effect, kind) / .triggers()
RitualResolution.attempt(skill, weight, cor, source): RitualOutcome
RitualResolution.targetFor(skill, weight, cor, preparation) / .criticalFor(roll)
RitualResolution.weights() / .preparations() / .preparationNamed(quality)
RitualResolution.spiFor(weight, chosen, burned): Int
Boons.granting() / .modifiers() / .groups() / .weight(): RitualWeight
Boons.penaltyForSequence(sequence): Int      // doubling, 9=0 down to 0=-512
Boons.modifierTotal(chosen, gmValue) / .spiFor(chosen, burned)
Boons.targetFor(skill, cor, sequence, chosen, gmValue) / .attempt(...)
NonStandardPathways.all() / .pathways() / .blocksFor(pathway)
NonStandardPathways.blockFor(pathway, sequence): SequenceBlock?  // null = no such rung
NonStandardPathways.knownSequences(pathway) / .alsoPrintedAs(pathway)
Weapons.all() / .named(name) / .forSkill(key): List<CataloguedWeapon>
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

- **Traits and weapons are not editable yet.** `SavedEdits` covers the four
  attributes and bought skills; advantages, disadvantages and weapons still
  come only from the published sheet. Weapons are what `ParryBlock` is waiting
  on.
- **Advantage and drawback rows are prose, not structure.** The flattened PDF
  gives no boundary between a wrapped effect line and the next row's name, and
  this PDF reports x=0 for every glyph so column geometry is unavailable. See
  `tools/extract_pathways.py`'s docstring before trying again.
- **The screens themselves are still device-only.** `app/src/test` now covers
  `BeyonderSession` and `SessionStore` (22 tests), which is where both
  app-level defects have landed so far. Composables are not tested; that
  needs `androidx.compose.ui.test` and a reason better than coverage.

---

## 6a-6dh. The slice log

**Moved to [`docs/SLICE-LOG.md`](docs/SLICE-LOG.md)** — 454 KB of it, which put this
file over the 150 k-char limit. Every slice from 6a to 6dh is there unchanged,
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
