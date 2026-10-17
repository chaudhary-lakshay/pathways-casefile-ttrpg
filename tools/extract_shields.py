"""Extract the Shields table and its bullets (Ch. 5, p. 37, global 2486-2537).

Seven columns -- Shield Type, DB, Cover DR, HP, Weight, Cost, Notes -- and the
same anchor the hit-location table uses: a line is the DB cell exactly when it
is a bare signed integer. The Notes cells contain "-1 to Block vs. ranged",
"-2 to Block" and "Move -1", so a loose signed-number rule would cut three of
the four rows in the wrong place.

Every shield NAME wraps: "Light (buckler," / "target)". The name is therefore
walked BACKWARD from the DB cell while the brackets are still open, which is
the same unclosed-bracket rule that holds the Vitals multiplier together in
extract_wounds.py. Counting two lines per name would work today and break the
first time the book prints a shield whose name fits on one.

The header wraps too ("Cover" / "DR"), which is why the header set lists the
halves rather than the column title.

The three bullets under the table go in a separate file, the way the Active
Defenses table and its bullets already do: they are prose the GM reads, not
columns. Their numbers stay out of Kotlin except where the app applies them --
see com.pathways.rules.Shields, whose two Block penalties and Shield Bash rows
are pinned here by the sentences they were read from.
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

CHAPTER = "5"
SHIELD_RANGE = (2486, 2537)

# This PDF prints list bullets as U+007F. See extract_defenses.py.
BULLET = "\x7f"
FURNITURE = re.compile(r"^(Powered by GURPS.*|Page \d+)$")
HEADER = {
    "Shields", "Shield Type", "DB", "Cover", "DR", "HP", "Weight", "Cost",
    "Notes",
}
DB_CELL = re.compile(r"^[+-]\d+$")

EXPECTED_TYPES = [
    "Light (buckler, target)", "Medium (round, heater)", "Heavy (tower, riot)",
    "Improvised (lid, crate side)",
]
# The sentences com.pathways.rules.Shields reads as rules. If one goes, the app
# is applying a number the book stopped printing.
PINNED = [
    "its DB (Defense Bonus) adds to all",
    "active defenses (Dodge, Parry, and Block)",
    # The arc the DB is conditional on. Pinned by the slice that started
    # WITHHOLDING the bonus at the rear: the two phrases above pin what the DB
    # reaches, and this pins when it reaches nothing. A rule the app applies
    # needs its own guard, and this one had none while three KDocs called
    # facing unmodelled.
    "against attacks from the front or shield side",
    "-1 to Block vs. ranged",
    "-2 to Block",
    # Shields.PRINTED_BASH and PRINTED_BASH_DEFAULT: the bash rows (6ib).
    "damage = thr-2 cr for light, thr-1 cr for medium, thr cr for heavy",
    "Skill defaults to Shield/DX or Brawling-2",
]
EXPECTED_BULLETS = 3


def load_lines():
    data = json.loads(LINES_PATH.read_text(encoding="utf-8"))
    return {l["n"]: l["text"] for l in data}


def stream(byn, start, end):
    """(is_bullet, text) per line, headers and furniture dropped."""
    out = []
    for n in range(start, end + 1):
        text = byn.get(n)
        if text is None:
            continue
        stripped = text.strip()
        if not stripped or FURNITURE.match(stripped):
            continue
        if stripped.startswith(BULLET):
            out.append((True, stripped.lstrip(BULLET).strip()))
        elif stripped not in HEADER:
            out.append((False, stripped))
    return out


def name_before(cells, i):
    """The shield name ending at cells[i-1], taking wrapped halves with it."""
    parts = []
    j = i - 1
    while j >= 0:
        parts.insert(0, cells[j])
        joined = " ".join(parts)
        if joined.count("(") == joined.count(")"):
            return joined
        j -= 1
    raise ValueError(f"no complete shield name before {cells[i]!r}")


def parse(cells):
    """One row per DB cell; the five cells after it are fixed in order."""
    rows = []
    for i, cell in enumerate(cells):
        if not DB_CELL.fullmatch(cell):
            continue
        if i + 5 >= len(cells):
            raise ValueError(f"row {cell!r} is missing columns")
        rows.append({
            "type": name_before(cells, i),
            "db": int(cell),
            "coverDr": int(cells[i + 1]),
            "hp": int(cells[i + 2]),
            "weight": cells[i + 3],
            "cost": cells[i + 4],
            "notes": cells[i + 5],
        })
    return rows


def join_bullets(lines):
    """Bullet text, with the non-bullet continuation lines that follow it.

    Only lines AFTER the first bullet can continue one; everything before is
    the table, which parse() has already read.
    """
    out = []
    for is_bullet, text in lines:
        if is_bullet:
            out.append(text)
        elif out:
            out[-1] += " " + text
    return out


def verify(shields, rules, prose):
    got = [s["type"] for s in shields]
    if got != EXPECTED_TYPES:
        raise ValueError(f"shield types changed: {got} != {EXPECTED_TYPES}")
    if len(rules) != EXPECTED_BULLETS:
        raise ValueError(f"expected {EXPECTED_BULLETS} bullets, got {len(rules)}")

    # Every column the app reads must be a number, not a wrapped cell that
    # happened to parse. Weight and Cost stay text on purpose: "varies" and an
    # em dash are real values on the improvised row.
    for shield in shields:
        if shield["db"] <= 0 or shield["coverDr"] <= 0 or shield["hp"] <= 0:
            raise ValueError(f"{shield['type']} has a non-positive column: {shield}")

    body = " ".join(prose + [r["rule"] for r in rules] + [s["notes"] for s in shields])
    for phrase in PINNED:
        if phrase not in body:
            raise ValueError(f"the rule the app applies is gone: {phrase!r}")


def write(name, entries, spans):
    OUT.mkdir(parents=True, exist_ok=True)
    payload = {"sourceLines": {CHAPTER: spans}, "entries": entries}
    (OUT / f"{name}.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=1) + "\n", encoding="utf-8"
    )


def main():
    lines = stream(load_lines(), *SHIELD_RANGE)
    prose = [text for is_bullet, text in lines if not is_bullet]
    shields = parse(prose)
    rules = [{"rule": text} for text in join_bullets(lines)]
    verify(shields, rules, prose)

    write("shields", shields, [list(SHIELD_RANGE)])
    write("shield_rules", rules, [list(SHIELD_RANGE)])

    print(f"shields: {len(shields)} rows")
    for row in shields:
        print(f"  {row['type']:30} DB {row['db']:+d}  DR {row['coverDr']:<3} HP {row['hp']}")
    print(f"shield_rules: {len(rules)} bullets")
    for rule in rules:
        print(f"  {rule['rule'][:78]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
