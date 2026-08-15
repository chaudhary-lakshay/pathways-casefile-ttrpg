# Pathways — Agent Handover

Handover for a fresh agent (written for Claude; Claude auto-loads this file).
Everything below is verified against the tree, not recalled. Section 1-5 were
written against commit `cf14e7c`; the counts in section 2 and the slice
sections below are current as of `086ad7d` (slice 6be).

**The slice log lives in [`docs/SLICE-LOG.md`](docs/SLICE-LOG.md)** — sections
6a-6cp, moved out of this file because it was 497 KB against a 150 k-char
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

**Codex is refusing again** — usage limit, resets **2026-08-20 14:23** from its
own error text. Every pass since 6cd has been the documented
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

**6cp is newest**, and its most reusable finding is that **a refusal can
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

**Where to start next: 6cp, then 6co, 6cn, 6cm, 6cl, 6ck, 6cj, 6ci, 6ch, 6cg, 6cf, 6ce, 6cd, 6cc, 6cb, 6ca, 6bz, 6by, 6bx, 6bw, 6bv, 6bu, 6bt, 6bs, 6br, 6bq,
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

There is an Android module (`app/`, Compose) with three screens: premade list
-> sheet (the `computed vs asPublished` diff, tap a skill to roll 3d6), the
pathway screen (potion grants, digestion tracker, Potion Consumption Roll),
and the builder (attributes against a live point budget).

| | |
|---|---|
| Tests | **1139 Kotlin** (JUnit 5 — 1027 `core:rules`, 92 `app`, 20 `core:model`) + **268 Python** (pytest), all green — counted at 6cp from the JUnit XML, not from memory |
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
fixtures/       23 character-sheet JSON fixtures (wired onto core:rules TEST classpath)
tools/          offline Python extractors (pypdf, openpyxl) + their tests
docs/           spec, plan, KNOWN_ISSUES
```

`core/model` — `Attr`, `Attributes`, `BeyonderState`, `Character`,
`ControllingAttribute`, `Dice`, `Difficulty`, `Modifier`, `PurchasedSkill`,
`RollResult`, `SecondaryDelta`, `SkillKey`, `Trait`, `Weapon`.

`core/rules` — `AttributeCosts`, `Derive`, `ModifierResolver`, `ParryBlock`,
`PointBudget`, `Conditions`, `Corruption`, `FrightCheck`, `Pools`, `PotionConsumption`,
`PotionDigestion`,
`Attacks`, `Damage`, `Ranged`, `Wounds`, `Premades`, `RitualResolution`,
`RandomSource`, `SecondaryCharacteristics`, `SheetComparison`, `SkillLadder`,
`SkillRoll`, `SuccessRoll`, plus `content/ContentLoader` reading 59 JSON
files from `core/rules/src/main/resources/content/`: `skills.json` (158),
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
`divination_difficulty.json` (5), `divination_methods.json` (8),
`divination_modifiers.json` (8), `divination_clarity.json` (6),
`divination_support.json` (4), `ritual_preparation.json` (5),
`boon_granting.json` (1), `boon_modifiers.json` (8),
`non_standard_pathways.json` (17), `weapons.json` (9), `wealth.json` (7),
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

## 6a-6cp. The slice log

**Moved to [`docs/SLICE-LOG.md`](docs/SLICE-LOG.md)** — 454 KB of it, which put this
file over the 150 k-char limit. Every slice from 6a to 6cp is there unchanged,
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
