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
| **Pathway** | the **Pathway** tile on the sheet — labelled **Drink a potion** until you have drunk one | drink potions, advance Sequence, track digestion |
| **Builder** | the **Spend points** tile on the sheet | change attributes and secondary characteristics, buy skills and traits, and carry weapons, against a live budget |

---

## 1. Pick a character

The premade list shows all 23 sheets. Tap one. The palette icon in its top bar
opens the theme picker — presets, plus light / dark / follow-system.

The sheet screen opens with a **computed vs. as-published** comparison. Where
the two disagree, the app shows both and *keeps the disagreement* — it does not
quietly repair the sheet. Four sheets really are wrong in their own arithmetic
(Docks Bouncer's HP, three sheets' FP, Monastery Scholar's advantage total,
University Librarian's disadvantage total), and the app is built so nobody can
silently "fix" them. If a number surprises you, the book is the authority.

### How the sheet is laid out

- **The bottom bar** switches what the list below shows: **Vitals**, **Skills**,
  **Traits**. It is a footer, not a screen change — the action tiles stay put.
- **The action tiles** are one wrapping grid of twelve, colour-coded by what
  they are for: neutral = a roll the GM called, red = combat, purple = a
  Beyonder power, green = managing the character. The three Beyonder tiles
  (Ritual, Divine, Boon) appear only once you have drunk a potion.

---

## 2. Roll a skill

Switch to **Skills** and tap any row. A dialog opens **before** the roll,
because the modifiers come first:

1. State the circumstances the GM called (the dialog shows what applies).
2. Press **Roll**.
3. You get 3d6, the target, the margin, and the degree of success.

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

**Gear that names this skill (not applied).** Under the roll you may see lines
from chapter 8's equipment table — "Magnifying glass (3s) — +1 to Search for
fine details". Those are printed for you to read, and the app does not add
them. It cannot: nothing here records what your character is carrying, and
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

---

## 3. Combat

Three red tiles — **Attack**, **Defend**, **Damage** — in the order a turn
actually runs, plus two neutral rolls a fight reaches for.

### Attack

Pick the maneuver and any attack options. The dialog prices them as you go:
Deceptive Attack's -2 per -1 imposed (capped at half your skill), Rapid Strike's
-6, All-Out Attack's +4 or extra attack, the FP that Extra Effort costs.

It will not let you buy an option the maneuver does not allow, and it charges FP
once per purchase, not once per recomposition.

**At range**, tick the conditions the GM called. One of them prints a range
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
- **Facing** — front, side, rear. Decides whether the shield's DB counts.
  From the side the book settles nothing, so the app *offers* the number and
  says the call is yours.
- **Shield Readied** — a shield must be readied to do anything.
- **Free actions** — Retreat (+3 Dodge / +1 Block / +1 Parry, melee only),
  Dive! (+3 Dodge, ranged only), Acrobatic Dodge (roll Acrobatics, ±2). They
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
Fright Check: two rolls, the check and then the effect table.

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

You walk a pathway one potion at a time: only the first potion for a mortal, or
the next Sequence of the pathway you already walk. Digestion is tracked, and
acting method progress with it.

**What a potion gives you is free.** It is never written into your point
budget — the app stores only the *choice* (pathway, Sequence, digestion) and
re-derives every grant from it. A potion that raises DX lifts every DX skill you
bought; it does not re-level what an earlier potion granted.

### Ritual

Pick the ritual's weight, your preparation, the language, the target link and
any supporting skills. The dialog composes the target, subtracts the Corruption
penalty at the thresholds the book prints, and prices the SPI. Quick and Full
preparation are two different tables with two different link rules.

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

Supplication: the entity's response, the Sequence penalty (doubling: none at
Sequence 9, -512 at Sequence 0), and the orthodox standing shift on the
response table.

### Detect / Artifacts

**Detect** covers Spirit Vision and the detection rolls, with the
anti-divination counters applied where a target has them. **Artifacts** lists
sealed artifacts, their grades, and what each ability costs in SPI or HP — the
grade is *not* a gate on use, deliberately.

---

## 5. Build and edit

**Spend points** opens the builder: the four attributes against a live point
budget, and skills bought at the ladder's own cost. **Add a skill** buys one.
**Wealth** and **Appearance** each have a row with a **Change** button (the
wealth one opens a picker titled *Economic status*), and picking a level
replaces the one you had rather than stacking a second.

Under the attributes, **Secondary characteristics** has a stepper for each of
the six the book prices — HP, Will, Perception, FP, Basic Speed and Basic Move
— with the book's own price beside it (*±2 pts per level*, and *±5 pts per
0.25* for Basic Speed, which steps a quarter at a time). Spirituality, CoR and
Digestion have no stepper because the book prices none of them. Stepping back
to the printed value clears the purchase; the sheet's own **computed vs.
as-published** table keeps showing what the sheet printed, because that
comparison is the engine against the book and a purchase is neither.

For a Beyonder, the big number on an attribute or secondary row is the one
the character plays with, potions included, and the small line adds *N before
potions* wherever a potion moved it. The steppers, the prices and the budget
all work on the bought number, because a potion's gains are free. Skill rows
stay on bought levels and say *before potions*; the sheet shows the levels
actually rolled.

**Weapons** lists what the character is carrying, with **Carry a weapon**
opening chapter 8's nine rows. All nine are offered whether or not the
character has the skill — a GM can hand anyone a weapon — and a weapon they
have no skill for says so instead of disappearing, because the parry is
`skill / 2 + 3` and there is no skill to halve. Carrying one costs no points:
the book prices weapons in money, which this app does not track. What it
changes is the **Defend** screen, which now prints a `Parry (name)` row for
each carried weapon it can compute one for.

Traits the book prints with a cost range or a "(specify)" name are bought by
naming the instance and its price — the app stores the row, the variant and the
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
  your side, an untrained parry, the value of a Secret nobody named — the app
  says so and leaves it to the GM.
- **Track a clock it does not hold.** There is no initiative, no map, no turn
  counter beyond the defence screen's own round. FP recovery, stun duration and
  reload rates are yours to run. So is the *day* in HP recovery — but its roll
  is offered: while a character is hurt, **Vitals** prints the book's three
  Natural Recovery rows under the HP row, a **−1 / +1** for anything else that
  moves the roll (a potion's grant, say — the line under it shows what is
  already counted), and **Roll HT N — a day of rest and food**. You say when
  a day has passed. A success restores nothing by itself: apply what the rows
  print with **+1** on the HP row, because which band applies to a large body
  is the GM's call. **Another day** clears it for the next one. First Aid,
  the Healing advantage and Regeneration are not offered; Physician is a
  skill, rolled from **Skills** like any other.

---

## If a number looks wrong

1. Check what the app says it applied — every dialog shows its modifiers.
2. Check the book. It is the authority, and the app is built to lose to it.
3. If they disagree and the app is right about the book, the sheet is the thing
   that is wrong — and that is a finding worth recording, not fixing.
