# Changelog

## [0.4.0](https://github.com/chaudhary-lakshay/pathways-casefile-ttrpg/compare/v0.3.1...v0.4.0) (2026-10-22)


### Features

* **web:** app shell, routes, saves and the gallery ([5e05c63](https://github.com/chaudhary-lakshay/pathways-casefile-ttrpg/commit/5e05c638be95b4391f5112630a72cbdf2d7104e9))
* **web:** browser store that keeps a bad save aside ([f71ecf8](https://github.com/chaudhary-lakshay/pathways-casefile-ttrpg/commit/f71ecf830574a29577c766e99a2920d708a5c188))
* **web:** builder helpers ([d72075f](https://github.com/chaudhary-lakshay/pathways-casefile-ttrpg/commit/d72075f059ce52ea2514fbcde7a41c61d2226310))
* **web:** damage and divination dialogs ([74f82b8](https://github.com/chaudhary-lakshay/pathways-casefile-ttrpg/commit/74f82b86cc67ac7bd3b711c767c1a29772c143ef))
* **web:** defence helpers ([9951853](https://github.com/chaudhary-lakshay/pathways-casefile-ttrpg/commit/99518539f5283dc01e632c24b33a89f637f292af))
* **web:** fright and detection dialogs ([c8ef269](https://github.com/chaudhary-lakshay/pathways-casefile-ttrpg/commit/c8ef269b35ef01335705dc2fc6160e23590665ee))
* **web:** glossary, credits, e2e and Pages workflow ([1162606](https://github.com/chaudhary-lakshay/pathways-casefile-ttrpg/commit/116260653a54d7fda70fe7bd02d219d25d044593))
* **web:** parity harness, content loader, derivation port ([e89a32a](https://github.com/chaudhary-lakshay/pathways-casefile-ttrpg/commit/e89a32a4591547e8140cd373e37de23bf3ad94d6))
* **web:** pathway helpers and screen parts ([66cad2d](https://github.com/chaudhary-lakshay/pathways-casefile-ttrpg/commit/66cad2d5d30c09409afc3168d57ddca2c1f444ff))
* **web:** port the remaining rules with parity ([4360c93](https://github.com/chaudhary-lakshay/pathways-casefile-ttrpg/commit/4360c939c9055979678a63bbd074b7646ee7ae47))
* **web:** port the session, saves and sheet text ([88a899d](https://github.com/chaudhary-lakshay/pathways-casefile-ttrpg/commit/88a899ddd98ce8396488fdf5b48d9af6e017c84c))
* **web:** port trait, pool, wound, weapon and digestion rules ([e14f2f1](https://github.com/chaudhary-lakshay/pathways-casefile-ttrpg/commit/e14f2f1a052a619331460e642fb8480afc93a21b))
* **web:** rest, concentrate, sight, contract ([200f57e](https://github.com/chaudhary-lakshay/pathways-casefile-ttrpg/commit/200f57e2ed8ec3cd38e939460be0f4937c6bc2c8))
* **web:** ritual and boon dialogs ([440c200](https://github.com/chaudhary-lakshay/pathways-casefile-ttrpg/commit/440c200b06dcd34624c3e372f7388632d0ce62d4))
* **web:** roll log, sheet scene and round state ([fd93945](https://github.com/chaudhary-lakshay/pathways-casefile-ttrpg/commit/fd93945845344151de57f133f905ea03af9c07aa))
* **web:** sheet derivation and sheet text ([a6ba39a](https://github.com/chaudhary-lakshay/pathways-casefile-ttrpg/commit/a6ba39a69488b59cffc28152cf5d36a9c8bb55ae))
* **web:** sheet parts, tabs and the skill roll dialog ([fd64405](https://github.com/chaudhary-lakshay/pathways-casefile-ttrpg/commit/fd644059bd909e9dee7ee0c66dd0385118062dfb))
* **web:** the attack dialog ([25dfd8c](https://github.com/chaudhary-lakshay/pathways-casefile-ttrpg/commit/25dfd8c6b3ae0842a955ad36b5c9ea2f154f67ac))
* **web:** the builder and its pickers ([b87bc76](https://github.com/chaudhary-lakshay/pathways-casefile-ttrpg/commit/b87bc76bd65332ff33333d9edbb46bde943ffc2c))
* **web:** the defend dialog ([87bbb73](https://github.com/chaudhary-lakshay/pathways-casefile-ttrpg/commit/87bbb733bf428a0cfa29b7957ef2752a44436a75))
* **web:** the pathway screen ([e9e7fc0](https://github.com/chaudhary-lakshay/pathways-casefile-ttrpg/commit/e9e7fc0be0d5a1b81c50afec21c9a24eece62ab6))
* **web:** the sheet screen and three short rolls ([9677f2b](https://github.com/chaudhary-lakshay/pathways-casefile-ttrpg/commit/9677f2b1ff3ad3afe6b59016c0e48ab074ba7ea9))
* **web:** theme, fonts and the phone frame ([a4a9779](https://github.com/chaudhary-lakshay/pathways-casefile-ttrpg/commit/a4a977939fe444b11beb47576da3b4a920507c35))


### Bug Fixes

* **web:** defer a stale reload met while rendering ([c8f9848](https://github.com/chaudhary-lakshay/pathways-casefile-ttrpg/commit/c8f98484f3f33400034f93a5f98ac7eca6f73a9a))
* **web:** dialog controls keep room in landscape ([6e4e30c](https://github.com/chaudhary-lakshay/pathways-casefile-ttrpg/commit/6e4e30cf553eda931522a8da33be0b4c54118f83))
* **web:** never overwrite a save that is not safe ([e47034f](https://github.com/chaudhary-lakshay/pathways-casefile-ttrpg/commit/e47034f356bdf319becdb94017f2f2d9a409588f))

## 0.3.1 (2026-10-20): four fixes from a play session

- **Spirit contract:** the list under the target now shows every term in it:
  the Corruption penalty, and any hurt, shock or declared states. Before, at
  CoR 4 the list read 16 - 6 while the target said 9. ([#1](https://github.com/chaudhary-lakshay/pathways-casefile-ttrpg/issues/1))
- **Boon petition:** a fixed choice such as "+0" was a blank black box. It now
  shows its value and check mark. ([#2](https://github.com/chaudhary-lakshay/pathways-casefile-ttrpg/issues/2))
- **Roll log:** the header no longer mentions pushing a roll, which v6.9i
  removed. ([#3](https://github.com/chaudhary-lakshay/pathways-casefile-ttrpg/issues/3))
- **Powers:** the Spend button now states all the Corruption it costs. Below
  0 SPI it names both the Soul Debt and the drained use, for example
  "Spend 1 SPI · +2 CoR (Soul Debt, drained)". Before, it said +1 and charged
  2. ([#4](https://github.com/chaudhary-lakshay/pathways-casefile-ttrpg/issues/4))

## 0.3 (2026-10-20): rulebook v6.9i

The app now follows the v6.9i rulebook. What the new book prints is applied,
and what it dropped is removed. All 23 premade characters are re-priced.

### Building a character

- Attributes have flat prices: ST 10 a level, DX, IQ and HT 20 a level,
  Will and Per 10 a level. HT costs more than before, so four premades now
  overspend. That is recorded, not changed.
- Skills cost 1, 2, 4, 8, 16 points, then 16 more for each level.
- Two advantages need a prerequisite before the builder sells them.

### Potions and digestion

- Before: a potion fixed your skill levels when you digested it.
  Now: a potion adds +N to your current level, or to your untrained level
  if you did not buy the skill. A Very Hard skill must be bought first.
  When you raise an attribute later, potion skills rise with it.
- The Potion Consumption Roll takes -1 for each point of Corruption.
  Drinking early costs 1d6 Corruption.
- A starting Beyonder can roll 4d20% digestion.
- 1 earned character point buys 1% digestion.
- Before: the Acting Method doubled your gains. Now: it rolls 3d6% at the
  end of a session.

### Rolls and effort

- Before: you could push a roll after you saw the dice. Now: you burn FP or
  SPI before you roll, +1 for each point, with no cap. Pushing is gone.
- Divination Arts defaults to SPI-4.
- Spirit Vision costs 1 SPI and needs no roll. The two spiritual senses roll
  3d6 against your current SPI.

### Combat

- Pins: a Pin gets +4 against a grappled, prone target. A pinned character
  cannot defend. The pinner has no Dodge and no Retreat, and Parry -4.
- Spirit body damage: a hit costs SPI, adds the Corruption the ability
  states, and gives -1 for each point on your next roll.
- A Warrior who used a weapon repeatedly attacks and parries at DX+2.
- Carrying: type the weight you carry. A heavier load lowers Move and Dodge.

### New screens

- Rest: Short and Long Rest restore FP and SPI. A Long Rest ends with full FP.
- Spirit contract: a roll to bind a spirit world creature.
- Revelation penalties for a ritual that reveals an entity.
- Social chips: traits such as Workaholic and Loner are tick boxes on a
  social skill roll.

### Screen fixes

- Attack: picking Pin no longer pushes the controls under it down.
- Defend: ticking Pinned or Surprised no longer makes the dialog jump.
- Defend: with a pin held, the parry note no longer blames the -4 on
  repeated parries.
- Digestion: the 1% stepper is labelled "Digestion", not "Earned CP".

### Removed, because v6.9i does not print them

Entity response, the worked ritual examples, ritual support, ritual
sacrifice, divination scope and support, and the unique sealed artifacts.

## 0.2 (2026-10-19)

- A Credits screen, opened by the **i** button next to the glossary.

## 0.1 (2026-10-19)

- The first release.
