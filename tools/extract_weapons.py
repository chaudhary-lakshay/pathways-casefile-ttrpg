"""Extract the Weapons table (Ch. 8, p. 80, global 4977-5042) into committed JSON.

THE BOOK DOES PRINT A WEAPON TABLE
----------------------------------
Four sections of the handover doc said it did not, and four features were
deferred on that premise: armed parry, weapon damage, and the two Aim rows
whose modifier is "+ weapon's Acc". com.pathways.rules.Ranged.modifiers still
refuses those rows with the message "this book prints no weapon table". The
table was simply in chapter 8, which nothing had read -- coverage.py reported
the chapter at 0.0%, which is the only reason it stayed invisible.

THE ANCHOR IS Acc, THE ONLY BARE INTEGER
----------------------------------------
Six columns: Weapon, Damage, Acc, Range, Cost, Skill. Acc is the only cell
that is a bare integer -- damage cells carry a die or an ST column ("2d pi",
"sw-1 cut"), ranges carry a slash or a reach letter ("150/1800", "C/1"), costs
carry a currency mark ("GBP3", "10s"), and skills are words. So a line is the
Acc cell exactly when it is digits and nothing else, and the three cells after
it are fixed in order.

THE DAMAGE CELL WRAPS, AND CARRIES TWO ATTACKS
----------------------------------------------
The Combat Knife prints "sw-1 cut / thr" and "imp" on separate lines, and the
Broadsword "sw+1 cut / thr+2" and "imp". Joined, each is two modes separated
by " / ": swing for cutting or thrust for impaling. Reading the first mode
only would silently drop half of what both melee weapons can do. The name is
therefore everything in the row before the first cell that STARTS a damage
mode, which is the only cell shape that cannot appear in a weapon's name.

TWO DISAGREEMENTS WITH CHAPTER 5, BOTH RECORDED
-----------------------------------------------
1. The Bolt-Action Rifle deals "5d pi+", and chapter 5's Optional Wounding
   Multipliers table never abbreviates its Large Piercing row. So this book
   prints a damage type in chapter 8 that it cannot look up in chapter 5, and
   Wounds.typeMultiplier refuses it. Adding the abbreviation to the ch. 5 row
   would be a repair; ORPHAN_TYPES names it instead.
2. That same Large Piercing row gives "Hunting rifles" as its example -- while
   chapter 8 prints the Hunting Rifle as plain "pi" and reserves "pi+" for the
   Bolt-Action Rifle. The two chapters disagree about which gun is which, and
   neither is corrected here.

WHAT IS DELIBERATELY NOT EXTRACTED
----------------------------------
No parry modifier: this table has no such column. Chapter 5's formula is
"Weapon Skill / 2 + 3" flat, so a melee weapon parries at that and a firearm
does not parry at all -- which is a reading, and lives in Kotlin
(com.pathways.rules.Weapons) rather than being invented as a column here.
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

CHAPTER = "10"
WEAPON_RANGE = (4296, 4361)

FURNITURE = re.compile(r"^(Powered by GURPS.*|Page \d+)$")
HEADER = {
    "Weapons (TL5+1 Victorian Era)", "Weapon", "Damage", "Acc", "Range",
    "Cost", "Skill",
}
ACC_CELL = re.compile(r"^\d+$")
# A damage mode: an ST column (sw/thr) or a fixed die count, an optional
# modifier written against it, and the damage type.
MODE = re.compile(r"^(sw|thr|\d+d)([+-]\d+)?\s+(\S+)$")
DAMAGE_START = re.compile(r"^(?:sw|thr|\d+d)\b")
# "150/1800" is half-damage and maximum range in metres. "C" and "C/1" are
# melee reach and are NOT distances -- Ranged.bandFor would read "1" as one
# metre and apply a range band to a sword.
METRIC_RANGE = re.compile(r"^(\d+)/(\d+)$")

EXPECTED_WEAPONS = [
    "Revolver (.38 cal)", "Revolver (.44 cal)", "Bolt-Action Rifle",
    "Hunting Rifle", "Double-Barrel Shotgun", "Combat Knife",
    "Truncheon/Baton", "Hand Axe", "Broadsword",
]
# Chapter 8 prints this type; chapter 5's wounding table never abbreviates it.
ORPHAN_TYPES = {"pi+"}
# The prose under the table. It names one weapon, and is attached to it.
NOTE_PREFIX = "Shotgun note:"


def load_lines():
    data = json.loads(LINES_PATH.read_text(encoding="utf-8"))
    return {l["n"]: l["text"] for l in data}


def stream(byn, start, end):
    """Table cells in order, headers and furniture dropped."""
    out = []
    for n in range(start, end + 1):
        text = (byn.get(n) or "").strip()
        if not text or FURNITURE.match(text) or text in HEADER:
            continue
        out.append(text)
    return out


def split_note(cells):
    """The trailing prose, joined, and the cells before it."""
    for i, cell in enumerate(cells):
        if cell.startswith(NOTE_PREFIX):
            return cells[:i], " ".join(cells[i:])
    raise ValueError(f"the prose under the table is gone; expected {NOTE_PREFIX!r}")


def parse_damage(text):
    """One entry per " / "-separated mode in a damage cell."""
    modes = []
    for part in text.split(" / "):
        m = MODE.fullmatch(part.strip())
        if not m:
            raise ValueError(f"unparseable damage mode {part!r} in {text!r}")
        base, modifier, dtype = m.groups()
        modes.append({
            "base": base,
            "modifier": int(modifier) if modifier else 0,
            "type": dtype,
            "printed": part.strip(),
        })
    return modes


def parse(cells):
    """One row per Acc cell; name and damage are read backwards from it."""
    rows, start = [], 0
    for i, cell in enumerate(cells):
        if not ACC_CELL.fullmatch(cell):
            continue
        if i + 3 >= len(cells):
            raise ValueError(f"row ending at {cell!r} is missing columns")
        group = cells[start:i]
        split = next(
            (j for j, c in enumerate(group) if DAMAGE_START.match(c)),
            None,
        )
        if split in (None, 0):
            raise ValueError(f"no weapon name before the damage in {group!r}")
        row_range = cells[i + 1]
        metric = METRIC_RANGE.fullmatch(row_range)
        rows.append({
            "name": " ".join(group[:split]),
            "damage": parse_damage(" ".join(group[split:])),
            "acc": int(cell),
            "range": row_range,
            "halfRange": int(metric.group(1)) if metric else None,
            "maxRange": int(metric.group(2)) if metric else None,
            "cost": cells[i + 2],
            "skill": cells[i + 3],
            "notes": "",
        })
        start = i + 4
    return rows


def attach_note(rows, note):
    """Give the trailing prose to the one weapon it names."""
    named = [r for r in rows if r["name"] in note]
    if len(named) != 1:
        raise ValueError(
            f"the note under the table names {[r['name'] for r in named]}, "
            "so there is no single row to attach it to"
        )
    named[0]["notes"] = note


def orphan_types(rows):
    """Damage types chapter 8 prints that chapter 5 cannot look up."""
    path = OUT / "wounding_multipliers.json"
    known = {
        e["abbreviation"]
        for e in json.loads(path.read_text(encoding="utf-8"))["entries"]
        if e.get("abbreviation")
    }
    return {m["type"] for r in rows for m in r["damage"]} - known


def verify(rows):
    got = [r["name"] for r in rows]
    if got != EXPECTED_WEAPONS:
        raise ValueError(f"the weapon list changed: {got} != {EXPECTED_WEAPONS}")

    for row in rows:
        if not row["damage"]:
            raise ValueError(f"{row['name']} parsed no damage")
        if row["acc"] < 0 or not row["cost"] or not row["skill"]:
            raise ValueError(f"{row['name']} has an empty column: {row}")
        # A metric range is both numbers or neither: half > max would mean the
        # two were read out of order.
        if (row["halfRange"] is None) != (row["maxRange"] is None):
            raise ValueError(f"{row['name']} parsed half of a range: {row['range']}")
        if row["halfRange"] and row["halfRange"] >= row["maxRange"]:
            raise ValueError(f"{row['name']}'s ranges are out of order: {row['range']}")

    # Both melee weapons that print two attacks must still print two.
    for name in ("Combat Knife", "Broadsword"):
        modes = next(r for r in rows if r["name"] == name)["damage"]
        if len(modes) != 2:
            raise ValueError(f"{name} prints {len(modes)} damage mode(s), not 2")

    orphans = orphan_types(rows)
    if orphans != ORPHAN_TYPES:
        raise ValueError(
            "which damage types chapter 8 prints without a chapter 5 "
            f"multiplier changed; that is the finding.\n  now: {sorted(orphans)}"
            f"\n  was: {sorted(ORPHAN_TYPES)}"
        )

    # Chapter 5 gives "Hunting rifles" as its Large Piercing example while
    # chapter 8 prints the Hunting Rifle as plain pi. Recorded, not repaired.
    hunting = next(r for r in rows if r["name"] == "Hunting Rifle")
    if hunting["damage"][0]["type"] != "pi":
        raise ValueError(
            "the Hunting Rifle no longer prints plain 'pi'; the two chapters "
            "may have been reconciled, which is a finding, not a repair to copy"
        )

    if not next(r for r in rows if "Shotgun" in r["name"])["notes"]:
        raise ValueError("the shotgun's pellet note did not attach to its row")


def main():
    cells, note = split_note(stream(load_lines(), *WEAPON_RANGE))
    rows = parse(cells)
    attach_note(rows, note)
    verify(rows)

    OUT.mkdir(parents=True, exist_ok=True)
    payload = {"sourceLines": {CHAPTER: [list(WEAPON_RANGE)]}, "entries": rows}
    (OUT / "weapons.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=1) + "\n", encoding="utf-8"
    )

    print(f"weapons: {len(rows)} rows")
    for row in rows:
        damage = " / ".join(m["printed"] for m in row["damage"])
        print(f"  {row['name']:24} {damage:22} Acc {row['acc']}  "
              f"{row['range']:<10} {row['cost']:<5} {row['skill']}")
    print(f"types chapter 5 cannot look up: {sorted(orphan_types(rows))}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
