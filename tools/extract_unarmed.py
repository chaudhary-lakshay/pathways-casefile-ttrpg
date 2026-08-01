"""Extract the Brawling and Boxing attack tables (Ch. 5, p. 38-39).

Two four-column tables -- Attack / Roll / Damage / Notes -- with the same
shape, flattened into wrapped lines. Wrestling's table is deliberately NOT
here: its columns are Maneuver / Roll / Opposed Roll / Notes and it deals no
damage at all ("None (grapple only)" in the book's own summary), so parsing it
as a damage table would invent damage the book does not print.

The anchor is the ROLL column, which is always the table's own skill with an
optional penalty -- "Brawling", "Brawling-2", "Boxing-1". That is a closed
shape per table, so a line is a roll exactly when it matches it, which makes
the line before it the attack's name and the line after it the damage. Notes
run until the next row. Counting four lines per row instead would mis-cut the
moment a note wraps.

Damage is parsed here rather than at render time, the same way
extract_ritual.py reads a critical row's CoR out of its prose: "thr-1 cr" is a
formula with three parts, and a UI splitting that string every time it drew a
row would be a parser nobody tests. The printed text is kept beside the parts
so the two can be checked against each other, and verify() does exactly that.

Shove prints "Special" instead of a formula -- it knocks down rather than
wounding -- so it carries no parts at all. Absent, not zero: an attack that
deals no damage is not one dealing 0 damage.
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

CHAPTER = "5"
# (skill, first line, last line) -- the two damage-dealing melee tables.
TABLES = (
    ("Brawling", 2545, 2572),
    ("Boxing", 2577, 2596),
)
# The sentence com.pathways.rules.Damage reads Boxing's bonus from. It
# wraps, so it is a range: "Damage Bonus: +1 to all Boxing" / "attacks at
# DX+1; ...".
BONUS_RANGE = (2575, 2576)
BONUS_PHRASE = "+1 to all Boxing attacks at DX+1; +2 at DX+2"

FURNITURE = re.compile(r"^(Powered by GURPS.*|Page \d+)$")
HEADER = {"Attack", "Roll", "Damage", "Notes"}
DAMAGE = re.compile(r"^(thr|sw)([+-]\d+)?\s+(\w+)$")
# "Special" is a real cell, not a missing one; the book prints it for Shove.
SPECIAL = "Special"

EXPECTED = {
    "Brawling": ["Punch", "Kick", "Elbow", "Knee", "Headbutt", "Shove"],
    "Boxing": ["Jab", "Cross", "Hook", "Uppercut"],
}


def load_lines():
    data = json.loads(LINES_PATH.read_text(encoding="utf-8"))
    return {l["n"]: l["text"] for l in data}


def stream(byn, start, end):
    out = []
    for n in range(start, end + 1):
        text = byn.get(n)
        if text is None:
            continue
        stripped = text.strip()
        if not stripped or FURNITURE.match(stripped) or stripped in HEADER:
            continue
        out.append(stripped)
    return out


def parse_table(skill, lines):
    """Rows of Attack / Roll / Damage / Notes for one skill's table."""
    roll = re.compile(rf"^{re.escape(skill)}(-\d+)?$")
    rows = []
    for i, line in enumerate(lines):
        if not roll.match(line):
            continue
        if i == 0:
            raise ValueError(f"{skill} roll {line!r} has no attack name before it")
        if i + 1 >= len(lines):
            raise ValueError(f"{skill} attack {lines[i - 1]!r} has no damage after it")
        rows.append({
            "skill": skill,
            "attack": lines[i - 1],
            "rollModifier": int(line[len(skill):] or 0),
            "damage": lines[i + 1],
            "_notes_from": i + 2,
        })

    for n, row in enumerate(rows):
        # Notes run to the line before the NEXT row's attack name, which sits
        # one line above that row's roll -- i.e. two above its damage.
        end = len(lines) if n + 1 == len(rows) else rows[n + 1]["_notes_from"] - 3
        row["notes"] = " ".join(lines[row.pop("_notes_from"):end])
        parts = DAMAGE.match(row["damage"])
        if parts:
            row["base"] = parts.group(1)
            row["modifier"] = int(parts.group(2) or 0)
            row["type"] = parts.group(3)
        elif row["damage"] != SPECIAL:
            raise ValueError(f"{skill} {row['attack']!r} damage unreadable: {row['damage']!r}")
    return rows


def verify(rows, bonus):
    by_skill = {}
    for row in rows:
        by_skill.setdefault(row["skill"], []).append(row["attack"])
    if by_skill != EXPECTED:
        raise ValueError(f"attack rows changed: {by_skill} != {EXPECTED}")

    for row in rows:
        if not row["notes"]:
            raise ValueError(f"{row['attack']!r} lost its notes")
        if "base" not in row:
            continue
        # The parts must rebuild the printed cell exactly; a parser that
        # drifts from the text beside it is worse than no parser.
        rebuilt = row["base"] + (f"{row['modifier']:+d}" if row["modifier"] else "") + " " + row["type"]
        if rebuilt != row["damage"]:
            raise ValueError(f"{row['attack']} parses to {rebuilt!r}, printed {row['damage']!r}")

    # Every listed unarmed attack is thrust-based; Damage relies on it, and a
    # swing appearing here would mean the book changed under that assumption.
    swings = [r["attack"] for r in rows if r.get("base") == "sw"]
    if swings:
        raise ValueError(f"unarmed attacks now swing: {swings}")

    if BONUS_PHRASE not in bonus:
        raise ValueError(f"Boxing's damage bonus no longer reads {BONUS_PHRASE!r}: {bonus!r}")


def main():
    byn = load_lines()
    rows = []
    for skill, start, end in TABLES:
        rows.extend(parse_table(skill, stream(byn, start, end)))
    bonus = " ".join(stream(byn, *BONUS_RANGE))
    verify(rows, bonus)

    OUT.mkdir(parents=True, exist_ok=True)
    spans = [[start, end] for _, start, end in TABLES] + [list(BONUS_RANGE)]
    payload = {"sourceLines": {CHAPTER: spans}, "entries": rows}
    (OUT / "unarmed_attacks.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=1) + "\n", encoding="utf-8"
    )

    print(f"unarmed_attacks: {len(rows)} rows")
    for row in rows:
        print(f"  {row['skill']:9} {row['attack']:9} {row['rollModifier']:+d}  {row['damage']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
