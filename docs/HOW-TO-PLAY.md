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
| **Builder** | the **Spend points** tile on the sheet | change attributes and buy skills against a live budget |

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
  succeeds).

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

Failures roll on their own table (backlash, spiritual drain, and worse), and
criticals on theirs.

### Divine

Choose the method and difficulty, add the modifiers the GM allows, and roll.
Clarity is read off the result. Divining a Beyonder stronger than you is harder
per their Sequence, and at the top of the ladder only a critical success can
work at all — the app demotes the roll rather than pretending.

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
  reload rates are yours to run.

---

## If a number looks wrong

1. Check what the app says it applied — every dialog shows its modifiers.
2. Check the book. It is the authority, and the app is built to lose to it.
3. If they disagree and the app is right about the book, the sheet is the thing
   that is wrong — and that is a finding worth recording, not fixing.
