# Pathways

Pathways is an offline Android app for the *Lord of the Mysteries* tabletop RPG. It holds a character sheet, rolls the dice and does the rule arithmetic, so the table can keep playing instead of looking things up.

<p align="center">
  <img src="docs/media/gallery.png" width="250" alt="The character list">
  <img src="docs/media/sheet-skills.png" width="250" alt="A character sheet on the Skills tab">
  <img src="docs/media/pathway-grid.png" width="250" alt="The 22 pathways to pick from">
</p>

The rules come from the fan rulebook *Powered by GURPS: Pathways, Lord of the Mysteries* (v6.9d), which builds on GURPS 4th Edition. The app adds no rules of its own. Each modifier is listed beside the roll that uses it. If the book leaves a decision open, the app asks you or the GM to make it. The guide lists the few places where the book is unclear and the app had to pick a reading.

## What it does

- Opens any of the 23 published characters, or a new one that you build.
- Rolls 3d6 for skills, attacks, defenses, Fright Checks, rituals, divination and prayers. Each dialog shows the target number before you roll and the full calculation after.
- Tracks HP, FP, Spirituality (SPI) and Corruption, and applies their penalties to the rolls that the book says they affect.
- Lets a character drink a potion and walk a pathway one Sequence at a time, with digestion and the Potion Consumption Roll.
- Has a point-buy builder for attributes, skills, advantages, disadvantages, languages and weapons, with a live budget.
- Works with no network connection. The app asks for no Android permissions.

## A turn at the table

These recordings come from a Samsung Galaxy Tab Active3. The character is the Church Healer, a Sequence 6 Beyonder on the Abyss pathway.

### Roll a skill

The GM says the healer is rushed. You tap **Diagnosis**, pick *Distracted or hurried -2*, and the target drops from 13 to 11 before you roll. The result shows the dice, the margin and each modifier that counted.

<p align="center"><img src="docs/media/skill-roll.gif" width="300" alt="Rolling Diagnosis with a -2 penalty"></p>

### Shoot

**Attack** asks which skill to use. You pick *Guns (Revolver)*. The dialog fills in the weapon, the maneuver and the range, and prints the target. After a hit, **Roll damage** throws the revolver's 2d.

<p align="center"><img src="docs/media/attack.gif" width="300" alt="An attack with a revolver, then the damage roll"></p>

### Read your powers

**Pathway** opens what each potion gave the character: attribute gains, skills, advantages and abilities. Each ability is one line with its cost tag. Tap a line to read the book's text for it.

<p align="center"><img src="docs/media/pathway.gif" width="300" alt="The Devil potion's abilities on the pathway screen"></p>

## More screens

| | | |
|:-:|:-:|:-:|
| <img src="docs/media/sheet-vitals.png" width="240" alt="Vitals tab"> | <img src="docs/media/sheet-traits.png" width="240" alt="Traits tab"> | <img src="docs/media/defend.png" width="240" alt="Defend dialog"> |
| Vitals: HP, FP and SPI, and the actions | Traits: each advantage with a tag for how much of it the app applies | Defend: say what the attack is, then roll Dodge, Parry or Block |
| <img src="docs/media/pathway.png" width="240" alt="Pathway screen"> | <img src="docs/media/builder.png" width="240" alt="Builder"> | <img src="docs/media/glossary.png" width="240" alt="Glossary"> |
| Pathway: the four Sequences and digestion | Spend points: the builder and its budget | Glossary: the book's terms |

## How to play

[docs/HOW-TO-PLAY.md](docs/HOW-TO-PLAY.md) is the player guide. It explains each screen and each roll, and it lists what the app leaves to the GM.

## Build and run

You need Android Studio (or the Android SDK) and JDK 17. The app runs on Android 8.0 (API 26) and later.

1. Clone the repository.
2. Connect a device with USB or wireless debugging turned on, or start an emulator.
3. Install the debug build:

```bash
./gradlew :app:installDebug
```

To run the tests:

```bash
./gradlew test              # Kotlin tests, all modules
py -m pytest tools -q       # Python extractor tests (use `python` outside Windows)
```

The Python tests need only `pytest`. Tests that read the rulebook PDF or the source spreadsheets skip when those files are not there. To run the extractors themselves, install `pypdf` and `openpyxl`.

## How the project is laid out

| Folder | What it holds |
|---|---|
| `core/model` | Plain data types: attributes, skills, traits, a character |
| `core/rules` | All rule logic, and the book's tables as JSON in `src/main/resources/content/` |
| `app` | The Jetpack Compose user interface |
| `fixtures` | The 23 published character sheets as JSON. The tests check the rules against them. |
| `tools` | Python scripts that pulled the tables out of the rulebook PDF |
| `docs` | The player guide, the design spec and the development log |

The rule tables are already in the repository, so you do not need the rulebook to build the app. You need the PDF only to run the extractors in `tools/` again.

## When the book and a sheet disagree

Some published sheets have arithmetic errors. For example, one sheet prints a disadvantage total of 30 points, but its four items add up to 25. In these cases the app follows the book and shows the sheet's figure next to its own. It does not quietly correct the sheet, and the tests fail if anyone tries.

## Legal

GURPS is a trademark of Steve Jackson Games. *Lord of the Mysteries* belongs to Cuttlefish That Loves Diving. This is a personal fan project with no connection to either. It is not for commercial use or redistribution.
