# How to play — Pathways companion

A table guide for the app, written from the screens as they ship. It says what
each control does, when to reach for it, and — where the app deliberately
leaves a decision to you — who makes that call.

**The app never plays the game for you.** It holds one character sheet, rolls
3d6 against numbers the rulebook prints, and shows the modifiers it can prove.
Anything it cannot know — whether you were surprised, where the attack came
from, how many parries you have already made — is a *declaration*: you tell it,
it does the arithmetic. Nothing is hidden and nothing is invented.

---

## The four screens

| Screen | How you get there | What it is for |
|---|---|---|
| **Premade list** | opens first | pick one of the 23 published characters |
| **Sheet** | tap a premade | play: roll skills, fight, cast, divine |
| **Pathway** | the **Pathway** button on the sheet — labelled **Drink a potion** until you have drunk one | drink potions, advance Sequence, track digestion |
| **Builder** | the **Spend points** chip on the sheet | change attributes and secondary characteristics, buy skills, traits and languages, and carry weapons, against a live budget |

---

## 1. Pick a character

The premade list shows all 23 sheets. Tap one. The palette icon in its top bar
opens the theme picker — presets, plus light / dark / follow-system.

A row prints the four attributes, the skill count and the first two traits
as you play them: what you bought in the builder, then what your potions
raised. The **Beyonders** tab
holds the characters you have walked onto a pathway, each marked with its
pathway and Sequence; everyone else is under **Mortal**.

The sheet screen opens with a **computed vs. as-published** comparison. Where
the two disagree, the app shows both and *keeps the disagreement* — it does not
quietly repair the sheet. Four sheets really are wrong in their own arithmetic
(Docks Bouncer's HP, three sheets' FP, Monastery Scholar's advantage total,
University Librarian's disadvantage total), and the app is built so nobody can
silently "fix" them. If a number surprises you, the book is the authority.

The number on a **Vitals** or **Skills** row is the one you play: what you
bought in the builder, then what your potions raised, and it is the level
the row rolls: on **Skills** the whole row is the button, with the level in
its badge. The red *(sheet: 9)* is still about the sheet as printed.
Where you have moved off the sheet and it disputes a figure, the grey note
says which: *"HP 14 · 11 from the sheet's points (sheet: 9)"* is a played
14, on a sheet whose own points give 11 and which prints 9.

Four sheets (Apothecary, Barmaid Brawler, Docks Bouncer, Military Police)
print SPI 9 and no pathway. The app cannot play a Beyonder without a pathway,
so it plays them at SPI 0 until they drink a potion, and then at what the
potion grants.

### How the sheet is laid out

- **The bottom bar** switches what the list below shows: **Vitals**, **Skills**,
  **Traits**. It is a footer, not a screen change — the pools and actions stay put.
- **Traits** lists every advantage and drawback by name and price, with a
  tag for how much of it the app counts: *Applied*, *Partly applied*, *Roll
  only*, *GM call*, or *Unmatched name* for a sheet spelling the book's
  list does not match. Tap a row for the book's own text and, for anything not
  counted, why. *What the tags mean* explains each. A self-control drawback
  has its own *Roll 3d6 ≤ N* chip. A row your potions gave you (a Hermit's Combat Reflexes, an
  Abyss Beyonder's drawbacks) is listed too, marked *"from the potion"*
  instead of its points, and counts in every roll; the builder will not sell
  it to you again. A potion's Charisma adds to a bought level (*"Charisma 4 —
  10 pts, +2 from the potion"*). A potion's face beside a bought one: the
  better face is played, and a worse bought face says *"not played: the
  potion's face is better"*.
- **Vitals** asks *"Does any of this hold?"* when a held drawback prints
  "-N to all rolls" under a circumstance only the GM can see: a phobia's
  stimulus present, laudanum withdrawal, a disrupted warding routine, an
  entity nearby, a Soul Scar resurfacing, a Reckless character where caution
  would be smarter. Tap the line to light it. A lit
  line counts on every roll on every screen, and each roll names it (*"includes
  Phobia (Cities) -1"*), until you tap it again. One line is narrower:
  Perceptual Splitting's *"All physical rolls at -2 during an episode"*
  counts only on rolls based on ST, DX or HT (those skills, attacks, defences
  and HT rolls), never on a Will, IQ, Per or SPI roll. It is not saved: going
  back to the gallery clears it, as it clears shock; the builder and the
  potion screen do not.
- **Reference text** in a dialog (shield rules, the incantation, Spirit
  Vision's colour key, spirit body damage, an artifact's description) sits
  behind a book icon, closed until you tap it. Targets, costs and what the
  app does not apply stay open.
- **The header** under the character's name shows the walk as tags: pathway
  and Sequence, digestion, CoR, and *Lost* if the character is gone.
- **HP, FP and SPI** sit in one strip under the header, on every tab, each
  with **−** and **+**. When a pool reaches a row of the book's table, tags
  under it name the state and its cost (*Reeling*, *−1 all rolls*); tap them
  for the printed row. SPI shows only for a Beyonder.
- **The actions** come in two weights. Four buttons — **Attack**, **Defend**,
  **Damage** and **Pathway** — then a row of chips for the rest, coloured by
  what they are for: neutral = a roll the GM called, red = combat, violet = a
  Beyonder power, brass = managing the character. The Beyonder chips (Ritual,
  Divine, Spirit Vision) appear only once a potion grants them; **Boon** is
  always there.
- **⋮ → Reset to sheet**, in the top bar, puts the character back to the
  printed sheet: potions, purchases, wounds, corruption and declared lines
  all go. It asks first, and it cannot be undone. It is for starting over,
  not for play: nothing in the book un-drinks a potion.

---

## 2. Roll a skill

Switch to **Skills** and tap any row. A dialog opens **before** the roll,
because the modifiers come first:

1. State the circumstances the GM called (the dialog shows what applies).
   Under the skill's name and **Skill N**, a card prints the **Target** as it
   stands, large, with *3d6 ≤ N* beneath it; once something moves it, the
   skill's own level is shown struck through beside it. The number moves as
   you pick. What made it is listed under the chips.
2. Press **Roll**. It rolls the number that was on screen.
3. You get the degree of success in its colour, *3d6: total vs target*, and
   the margin, then every line that moved the target.

The target already includes what the app can prove: your level, the trackers
(HP, FP, SPI, Corruption penalties scoped to the skills they actually touch),
and any trait that reaches that roll.

Critical rules are the book's: 3-4 always succeed, 17-18 always fail — except
where a specific table overrides them, which the app follows over the general
rule.

**Push the roll (chapter 2, p. 9).** After the dice are on the table you may
buy the result up: **1 FP for +1** on a ST, DX or HT roll, at most +5, or
**1 SPI for +1** on a spiritual one, with no cap the book prints. You cannot
spend both on the same roll, so the app offers whichever pool the skill's own
row names and nothing on a roll named by neither — an IQ, Will or Per skill
gets no push, which is why the row is missing rather than empty there.

It raises the **target**, never the dice: the same total is re-read against a
higher number, which is what "after seeing your result" means. Spend nothing
and the roll stands. The SPI side stops at what you actually have — personal
spirituality cannot go below zero — while FP is allowed past it, because the
book charges 1 HP for every point below 0 rather than forbidding it.

The same push is offered on **Ritual**, **Boon** and **Divine**, where the
book prints it again in each chapter's own words. There it is charged **when
you close the dialog**, not on each tap, and it is separate from the fuel that
screen already took. Closing is also when a critical failure's Corruption is
charged — because a push can take a ritual off the Critical Failure Table
entirely, and you should not pay for a disaster you bought your way out of.

**Gear that names this skill (not applied).** Under the circumstances you may
see this title; tap it to open lines
from chapter 8's equipment table — "Magnifying glass (3s) — +1 to Search for
fine details". Those are printed for you to read, and the app does not add
them. It cannot: the app records the weapons you carry and no other gear, and
most of the bonuses are scoped to a circumstance ("for fine details", "when
rested", "in rain") that only you and the GM can judge.

If the item is in your kit and the circumstance holds, tick **Good tools /
equipment** in the circumstances above. That is Appendix A's own row and it
prints a range, **+1 to +2**, so you pick the value — which is where the
item's own line earns its keep: a basic disguise kit says +1 and the advanced
one says +2, and the range covers both. **Improvised / poor tools** (-2 to -4)
is the other side of it. Tick the row once; the app never adds the item's
figure on top of your pick.

Every row the table prints for that skill is shown, conditional ones included
— so an empty list means the book prices no gear for it, not that your gear
does nothing.

### A skill you do not have

The book lets you attempt most skills untrained, at a default. Scroll to the
top of **Skills** and tap **Roll a skill this sheet does not have**. Type a
few letters in **Find a skill**, tap the row, and the usual roll dialog opens
with the default as its skill: *"Acrobatics (untrained, DX-6)"*.

- The number is your attribute less 4, 5 or 6 for an Easy, Average or Hard
  skill. **Brawling** is DX-2, which is the book's own line for it.
  **Versatile** adds 1 to every one of them.
- **Medical Training (Informal)** gives its holder Physician at IQ-2 in
  place of the Hard skill's IQ-6, and the row says so. Versatile does not
  add to that one: the book gives it as a level, not a default.
- Very Hard skills and Wrestling have no default and are not listed.
  Divination Arts is listed only with Ritualistic Magic at 12 or more.
- The GM may rule that a skill cannot be attempted untrained at all. The app
  does not record that, so ask before you roll.
- It is the skill roll alone. For an untrained punch, or a weapon you have no
  skill with, use **Attack**, which lists those skills under your own and
  carries the damage. For an Acrobatic Dodge use **Defend**, which rolls the
  default itself and moves the Dodge row.

---

## 3. Combat

Three buttons — **Attack**, **Defend**, **Damage** — in the order a turn
actually runs, plus two neutral rolls a fight reaches for.

### Attack

Pick the skill first. The list is your skills the book files under **Combat
Skills** (Brawling, Knife, Guns, Shield, Fast-Draw and the rest of that
list); a Research or Writing roll is made from the Skills tab. Then pick
the maneuver and any attack options. The dialog prices them as you go:
Deceptive Attack's -2 per -1 imposed (capped at half your skill), Rapid Strike's
-6, All-Out Attack's +4 or extra attack, the FP that Extra Effort costs.

It will not let you buy an option the maneuver does not allow, and it charges FP
once per purchase, not once per recomposition.

The choices scroll; under them, pinned beside **Roll**, it adds the terms up: what injury, fatigue and attrition take,
then the target, *3d6 ≤ N* (*", each"* when the plan makes more than one
attack).

Under your own skills the list carries on with the ones you can attack with
**untrained**: a fist (Brawling at DX-2, Boxing at DX-5) and the weapons in the
book's table (Knife, Axe/Mace, Broadsword and three Guns), each marked
*"untrained, DX-4"* or the like. They roll and deal damage like any other.
A weapon you have no skill with prints *"untrained parry is the GM's call"*: the
book's armed cell is *"Weapon Skill ÷ 2 + 3"* and names no untrained operand, and
Defend says so under its rows.

A **Warrior**, **Pugilist** or **Weapon Master** wields any weapon in hand at no
less than DX, and the header says so. For its parry, the weapon skill is floored at
DX before the halving, on this screen and in Defend, which says it is a reading (tap *Why a master's weapon parries at DX*). Put the weapon down and the
skill is back at its own number, and a Deceptive Attack the lower number cannot
afford goes to 0. The Warrior's *"default +2"* for a weapon used repeatedly is
not applied: the Warrior's own example puts it above DX, and the Weapon
Master, *"a strict upgrade"* of that line, stops at DX, so the book gives no
number that fits both. A Pugilist needs no warm-up with a melee weapon, so for
one of those nothing is left over.

**At range**, tick the conditions the GM called. Two sets replace each other
rather than add: the target's speed (stationary, walking, running) and the two
Aim rows. A target moves at one speed. One of the conditions prints a range
rather than a number — *"Darkness or heavy fog: -3 to -9 depending on
severity"* — so ticking it opens a **How bad is it?** row and you pick where in
that span the scene sits. Until you do, the app applies the gentlest figure the
book prints, which is what the chip shows selected.

### Defend

The busiest screen in the app, and the one with the most declarations.

- **New round** — clears the free actions, the parry count and any paid +2. It
  appears only once something is spent. It does **not** clear a stun: the book
  gives stun its own clock ("Stunned for 1d6 seconds, roll HT each turn to
  recover early"), so that ends when the roll says so.
- **Refusals** (surprised / took an All-Out Attack / never saw the shot) —
  withdraw your defences entirely, and the app says *which* sentence did it.
  Combat Reflexes cancels the surprise one and nothing else.
- **Stunned** — a -4 that prices every defence rather than withdrawing it.
- **What is coming at you** — bare hand, weapon, or a shot. This changes the
  unarmed parries (-3 against a weapon) and withdraws them against a shot.
- **Parry (DX, bare hands)** — the book's unarmed formula is *"Brawling or DX
  ÷ 2 + 3"*, so every character has a parry with no skill at all. The row is
  left out only when your Brawling already parries as well. It takes the same
  -3 against a weapon, which is the app's reading; tap *Why the bare-hand
  parry is DX itself* to read it. A
  melee weapon you carry with no skill for it gets no parry row, and a note
  names it. A firearm parries at nothing, skill or no skill.
- **Facing** — front, side, rear. Decides whether the shield's DB counts.
  From the side the book settles nothing, so the app *offers* the number and
  says the call is yours.
- **Shield Readied** — a shield must be readied to do anything.
- **Free actions** — Retreat (+3 Dodge / +1 Block / +1 Parry, melee only),
  Dive! (+3 Dodge, ranged only), Acrobatic Dodge (roll Acrobatics, ±2; without
  the skill it is rolled at the default, DX-6 and one more with Versatile,
  and labelled untrained). They
  stack with each other and with an active defence; the book allows each once
  per round, which is what **New round** is for.
- **Parries this round** — the stepper. Each parry after the first is -4
  (-2 fencing, -1 rapier), and Dodge takes no such penalty.
- **Buy +2** — All-Out Defense (Increased) or Feverish Defense, spent on **one
  named row**. Paying twice buys one bonus, so the picker clears first.

### Damage

Rolls the weapon's damage, applies the hit-location and damage-type
multipliers, and tells you whether it is a **major wound** (over half max HP) —
and if so, rolls that table, whose shock the sheet then carries for the turn.

### Concentrate / Fright

**Concentrate** is the focus some actions gate behind. **Fright** rolls the
Fright Check: two rolls, the check and then the effect table. Pick the kind
and what was witnessed on one page; each row shows what the book prints for
it, and picking one sets **Modifier** to that figure, which then steps a point
at a time for the GM's severity, with no bound. The target sits
above the choices and **Roll** below them, withdrawn until a row is picked.

Three of the table's nine bands print a way out, and those show a **recovery
roll** against the column's own attribute — Will in Fright and Awe, IQ in
Confusion. The other six print no roll and offer none. The `12` band also
works its duration out for you (`14 sec (25 − HT 11)`), with the subtraction
shown because the attribute it counts down from is not always the one it
rolls.

---

## 4. Beyonder play

### Drink a potion

Go to **Pathways**. Pick your pathway, then the Sequence you are drinking.

- **Drink this potion** applies the grants.
- **Drink and roll** runs the Potion Consumption Roll — whose own table
  overrides the general critical rules at both ends (1 always fails, 19 always
  succeeds). The line under the button prints the roll before you make it: the
  target, what advancing before full digestion costs, and a warning when
  drinking it would end the character even on a success.
- **Reroll the PCR (Luck)** appears on the potion's block after a failed roll,
  for a character with Luck, Beckoning Luck (unless they carry Fate Debt), or
  the Lucky One's rerolls (Wheel of Fortune, Sequence 7 and deeper). It is
  saved, so closing the app does not lose it. A success replaces the failure; a
  second failure changes nothing. The app keeps no session, so how many rerolls
  are left is yours to count.

You walk a pathway one potion at a time: only the first potion for a mortal, or
the next Sequence of the pathway you already walk. Digestion is tracked, and
acting method progress with it.

Once you have drunk, the screen has two tabs. **Potion** shows what the chosen
Sequence grants: stat gains, skills with the level they reach, then the book's
own advantage, drawback and ability text. Each ability is a row: its name and
the bracket the book prints after it, such as **1 SPI** or **Passive**. Tap a
row to read it; **Open all** opens the whole card, and a card of one or two
abilities starts open. A roll's Success and Failure lines sit under the
ability they belong to. Costs, distances, dice and modifiers are picked out in
colour; the words are the book's. **Digestion & corruption** holds the two trackers, and
**What raises corruption** opens the list of what costs CoR.

**What a potion gives you is free.** It is never written into your point
budget — the app stores only the *choice* (pathway, Sequence, digestion) and
re-derives every grant from it. A potion that raises DX lifts every DX skill you
bought; it does not re-level what an earlier potion granted.

**A Hanged Man chooses a drawback.** Sequence 9 prints three and says to choose
one. On the Potion tab the Drawbacks card has a chip for each; the one you tap
is held free from then on, down the whole pathway, and you can switch it. A
drawback you already bought in the builder shows *bought* and cannot be chosen
until you sell it, or its points would still be credited.

### Ritual

Pick the ritual's weight, your preparation, the language, the target link and
any supporting skills. The dialog composes the target, subtracts the Corruption
penalty at the thresholds the book prints, and prices the SPI. Quick and Full
preparation are two different tables with two different link rules. The
language is yours to state per ritual; under the chips the dialog lists the
mystical languages the character bought in the builder, then any a potion
granted (*"From the potion: Ancient Hermes, Native."*), and does not pick one
for you. A language granted by a trait is not in that list. The choices scroll;
pinned under them are the terms of the target, the target, what the ritual
costs and **Cast**, which is withdrawn, with the reason in red, while the SPI
is short, or the book or the entity refuses the attempt.

**An invocation is paid when you ask.** With *Invocation* chosen, the button
reads **Ask the entity** first. Asking rolls the Entity Response Table and
spends the fuel then (*spent on asking: …*), and the power source, complexity,
SPI and HP chips grey out; the button then reads **Cast**, which costs nothing
more. A rejection spends nothing. *No response* spends the book's 2 SPI out of
the HP you declared first, 2 HP for each, so a caster at 0 SPI who bled for it
can still cast. Close the dialog after asking and the answer is gone but the
fuel is not. A ritual on *Personal SPI* pays at **Cast**.

Six of those cells print a span or a floor rather than a number — *Good +1 to
+2*, *Poor -1 to -2*, *None -4 or worse*, the quick table's *Poor / None -2 to
-4*, and the links *Strong +2 to +3* and *Weak +0 to -1*. Picking one opens a
row headed **The book prints … — the GM picks:** with every value in the cell.
Until you tap one, the roll takes the figure nearer zero, which is the chip
shown selected. A pick belongs to its cell: choose another row and it is not
carried over, come back and it is still there, shown selected. *Or worse* offers three steps past its printed figure; the
book names no floor, so a harsher call than that is yours to apply by hand.

Failures roll on their own table (backlash, spiritual drain, and worse), and
criticals on theirs — and both can still be pushed (see **Push the roll**
above). A rolled 17 or 18 cannot: that row is named for the dice rather than
the margin, so it is taken against any target and no amount of SPI moves it.
The app withholds the row rather than selling you a point that buys nothing.

### Divine

Choose the method and difficulty, add the modifiers the GM allows, and roll.
Clarity is read off the result. Divining a Beyonder stronger than you is harder
per their Sequence, and at the top of the ladder only a critical success can
work at all — the app demotes the roll rather than pretending.

A caster with Ritualistic Magic at 12 or more may divine at that skill -2, and
the app uses it where it beats their own Divination Arts, saying *"Defaulted
from"* under the dialog's title. **Versatile**'s +1 to a roll at default is counted
there, before the two numbers are compared.

Four of the circumstance rows print a **range** — *"Method match +0 to +2"*,
*"Target link +0 to +4"*, *"Unfavourable timing -1 to -2"*, *"Quiet, dedicated
space +0 to +1"* — and ticking one opens a second row of chips to pick inside
it. The value shown selected is the one the roll is using, so an untouched row
applies the first figure the book prints. *"Previous divination about the same
subject"* is -2 **per repeat**, so it asks how many times instead: x1 through
x4. Under that rule
no push is offered either, for the same arithmetic: a burn moves the target and
never the dice, so it can never reach the 3 or 4 the rule demands.

Everywhere else it is offered, a critical success included — the clarity table
prints no critical row and grades one by margin like any other, so a point
still buys a clearer answer.

### Boon

Always on the sheet. Without Ritualistic Magic it opens and says so: the
skill has no default, and **Spend points** sells it. It also needs 4 SPI of
fuel, in SPI or in blood.

Pick the **patron's pathway**. The Sequence is not yours to pick: a first
prayer asks for the first rung the book prints for that patron (Sequence 9,
or 8 for the three pathways printed only there), and each later prayer asks
the same patron for the next one. The rung's gains, skills and abilities are
listed above the choices; its advantages and drawbacks are closed rows. One
that names a listed trait with the same numbers (the Broker's Charisma +2,
Fit, Acute Vision, Acute Hearing) is held on the Traits tab, marked *"from
the boon"*, and counts in rolls as a potion's does; the rest is the GM's. Then the sacrifice, the ritual's quality, and 4-6 SPI of fuel, any of
it paid in HP at 2 HP for 1 SPI: that is how a mortal, who has no SPI, prays. The
foot shows every term of the target: Moderate -2, the Sequence (none at 9,
-2 at 8, doubling after), and the two circumstances. **Mystical language**
is the tongue the prayer is said in, as on a ritual: *Accented* is -2 and
*Broken* forbids it.

The button reads **Ask the patron** first. It rolls the Entity Response
Table, spends the fuel, and locks the patron and fuel choices. The answer
is printed above the foot and its modifier joins the target; the button
then reads **Pray**. Two answers end the prayer with no roll: a rejection,
which spends nothing, and no response, which grants no boon and takes 2 SPI
out of the fuel you declared, HP first.

A granted boon is stored when the dialog closes, and held **beside** any
potion walk: its attribute, SPI and skill gains are on the sheet from then
on, free, and the header shows *Boon · name · Seq N*. The book records
nothing past Sequence 8 for any patron, so the dialog refuses a prayer once
the last printed rung is held.

### Detect / Artifacts

**Detect** covers Spirit Vision and the detection rolls, with the
anti-divination counters applied where a target has them. A character with
Acute Vision sees *Acute Vision: +2 to Vision rolls* above the Spirit Vision
roll; tap it if the GM rules that the eye's acuity helps this reading. The
same line, and Acute Hearing's, sits on every Perception-based skill roll,
whether the sense was bought or granted by a potion (a Red Priest's, a
Demoness's, a Visionary's). A potion's stronger row, such as Greater Acute
Vision's +4, is a line of its own; tick both only if the GM rules they add.
A Light Sleeper sees *Light Sleeper: Perception rolls while sleeping at +4*
on those skill rolls and at the foot of the detection roll; it counts there
only when that roll takes Per, and the dialog says so when it takes SPI.
**Artifacts** lists
sealed artifacts, their grades, and what each ability costs in SPI or HP — the
grade is *not* a gate on use, deliberately.

---

## 5. Build and edit

**Spend points** opens the builder: the four attributes against a live point
budget, and skills bought at the ladder's own cost. **Add a skill** buys one.
**Wealth** and **Appearance** each have a row with a **Change** button (the
wealth one opens a picker titled *Economic status*), and picking a level
replaces the one you had rather than stacking a second. Where a potion granted
a face, the Appearance row says so and names the face that is played.

Under the attributes, **Secondary characteristics** has a stepper for each of
the six the book prices — HP, Will, Perception, FP, Basic Speed and Basic Move
— with the book's own price beside it (*±2 pts per level*, and *±5 pts per
0.25* for Basic Speed, which steps a quarter at a time). Spirituality, CoR and
Digestion have no stepper because the book prices none of them. Stepping back
to the printed value clears the purchase; the sheet's own **computed vs.
as-published** table keeps showing what the sheet printed, because that
comparison is the engine against the book and a purchase is neither.

The big number on an attribute or secondary row is the one the character
plays with, potions and God of Combat's +3 ST, DX and HT included, and the
small line adds *N bought* wherever either moved it. The steppers, the prices
and the budget all work on the bought number: a potion's gains are free, and
the blessing's 75 points already paid for its +3. A God of Combat holder can
buy ST, DX and HT three levels less high than anyone else, so every potion
still fits on top. Skill rows
stay on bought levels, and the **Skills** header says *Levels before potions*
once for all of them; the sheet shows the levels actually rolled.

Each skill row is its name and level, its points, and a **Study** tag: what
downtime study would cost for the next rung (*24 hrs + 16s · 2 CP → 10*), or
why it offers none (*at cap 11*, or *not by self-study* for a Very Hard
skill). Tap a row with a refusal for the book's sentence. **Study
by** picks the road the tags are priced for, *With a Tutor* or *Self-Study*.
Study is advice: it never spends the budget.

**Languages** lists the languages bought, with **Add or change** opening the
book's twelve in its own two groups. Pick a language, one of the three
proficiencies at that group's prices (6 / 4 / 2 for a common tongue, 30 / 20 /
10 for a mystical one) and a form: *Spoken + Written* is the printed price,
and *Spoken only* or *Written only* costs half. The line above **Buy** is
what the budget will bill and why. Language Talent halves it, rounded up, and
Multilingual makes the first two common languages bought at Native in both
forms free; Dead Language Fluency does the same for the first of Ancient
Hermes or Ancient Feysac bought at Native in both forms (the owner's reading:
the book names no level, price or list). All three are read whenever the
character is composed, so buying one later reprices what is already owned. Picking a language the
character owns changes it in place, and **Give back** removes it. The points
are applied and the *-2 / -3 to language-dependent skill rolls* are not: the
book tags no skill as language-dependent, so those are the GM's. No other language
is free, because the book prints no free native tongue;
what a premade already speaks is not on its sheet, and the app does not
guess it. A language that comes with a trait (Official Beyonder's *Hermes
Language (Broken)*) or with a potion is not a row
here either, so do not buy it twice.

**Weapons** lists what the character is carrying, with **Carry a weapon**
opening chapter 8's nine rows. All nine are offered whether or not the
character has the skill — a GM can hand anyone a weapon — and a weapon they
have no skill for says so instead of disappearing, because the parry is
`skill / 2 + 3` and there is no skill to halve. Carrying one costs no points:
the book prices weapons in money, which this app does not track. What it
changes is the **Defend** screen, which now prints a `Parry (name)` row for
each carried weapon it can compute one for.

**Add a trait** lists the book's advantages and drawbacks as a name and a
price. Tapping a row opens its text and never buys; **Buy** inside the open
row does. A trait already held is marked *already held* and has no Buy. God
of Combat is refused the same way while a bought ST, DX or HT is above what a
holder may buy, and its row says which to lower.

A bought **Charmed Object** has a **Choose skill** button under it, listing
the character's own skills. The book says to specify the item and the skill;
the app stores the skill. A roll of that skill from the Skills tab, and an
attack with it, then offers a chip, *+1 to one specific skill when used*,
which the table lights when the item is in use. Choosing again changes the
skill. A parry, an Acrobatic Dodge, a ritual, a divination or a boon built on
the skill does not take the +1 in the app.

Traits the book prints with a cost range or a "(specify)" name open the same
way, with **Choose…** in place of Buy, and are bought by naming the instance
and its price — the app stores the row, the variant and the
cost, and composes the display name from them.

Edits are saved per character and survive a restart.

---

## What the app will not do

- **Enforce what only you can see.** Surprise, facing, awareness of a shot, how
  many parries you made — all declarations. The app does the arithmetic once
  you say what happened.
- **Repair the book.** Where a published sheet disagrees with the rulebook, the
  book wins and the disagreement is shown, not smoothed over.
- **Invent a rule to fill a gap.** Where the book prints nothing — the shield at
  your side, the value of a Secret nobody named — the app
  says so and leaves it to the GM.
- **Track a clock it does not hold.** There is no initiative, no map, no turn
  counter beyond the defence screen's own round. FP recovery, stun duration and
  reload rates are yours to run. So is the *day* in HP recovery — but its roll
  is offered: while a character is hurt, **Vitals** prints the book's three
  Natural Recovery rows, a **−1 / +1** for anything else that
  moves the roll (a potion's grant, say — the line under it shows what is
  already counted), and **Roll HT N — a day of rest and food**. You say when
  a day has passed. A success restores nothing by itself: apply what the rows
  print with **+** on the HP strip, because which band applies to a large body
  is the GM's call. A Fast Healer is told of one more HP on a success; on a
  failure, whether that point still comes is the GM's call, and the line
  says so. **Another day** clears it for the next one. The Healing
  advantage and Regeneration are not offered. First Aid and Physician are
  skills, rolled from **Skills** like any other, or untrained from the list
  at its foot, and the app restores no HP for either. The book's rows are *"First Aid: 1
  HP + stops bleeding; 1 minute; once per wound"* and *"Physician (skill 12+):
  1 HP (2 HP on critical success); per day of bed rest; requires equipment"*.
  Add the HP with **+1**; the rest is yours to count.

---

## If a number looks wrong

1. Check what the app says it applied — every dialog shows its modifiers.
2. Check the book. It is the authority, and the app is built to lose to it.
3. If they disagree and the app is right about the book, the sheet is the thing
   that is wrong — and that is a finding worth recording, not fixing.
