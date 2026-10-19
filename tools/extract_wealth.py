"""Extract Starting Wealth by Economic Status (Ch. 10, p. 66, global 4256-4295).

THE CALLER WAS ALREADY THERE
----------------------------
Wealth is a point-costed trait and com.pathways.rules.PointBudget has billed
traits since M0 -- but chapter 3 prints only FOUR wealth levels (Dead Broke,
Poor, Struggling, Comfortable) among its advantages and disadvantages. The
other three (Average, Wealthy, Very Wealthy) exist only here, so until this
table was read a character could not be given the two levels above
Comfortable at all. CHAPTER_3_LEVELS pins the four that overlap and
ONLY_IN_CHAPTER_8 pins the three that do not; verify() fails if either set
moves, because that is a change in the book, not in this parser.

THE STATUS CELL IS THE ANCHOR
-----------------------------
Four columns: Status, Starting Money, Social Weight, Lifestyle. Only the
Status cell carries its point cost in brackets ("Dead Broke (-25 pts)"), and
no other cell can take that shape, so a row STARTS at a status cell and runs
to the next one. Everything after the weight is the lifestyle, joined.

Note the two kinds of minus this page prints: the point costs are ASCII
("-25 pts") and the Social Weight cells are U+2212 (" -2"). Reading only one
of them silently drops half the negative numbers.

SOCIAL WEIGHT IS A GUIDELINE, NOT A REACTION MODIFIER
------------------------------------------------------
v6.9d headed this column "Reaction Modifier" and its paragraph priced it per
Status level. v6.9i renamed it and rewrote the paragraph: "Social Weight
values in the table serve as a guideline for the GM to gauge the general
attitude of neutral NPCs." So the numbers are committed as `socialWeight` and
nothing may add them to a roll. verify() pins the sentence.

WHAT IS DELIBERATELY NOT EXTRACTED
----------------------------------
"Starting Wealth in Feysac" (line 4250) restates the same ladder in gold
hoorn for a campaign set there, and Dead Broke is missing from it. It belongs
with the Currency Conversion table above it, not with this one, and nothing
converts currencies today.

The money column is committed exactly as printed, pounds and soli both. This
chapter's own conversion (1 Gold Pound = 20 Soli, line 4185-4186) is used HERE to
check that the ladder climbs, and is not written into the file: a normalised
column would be arithmetic this table does not print.
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

CHAPTER = "10"
WEALTH_RANGE = (4256, 4295)

FURNITURE = re.compile(r"^(Powered by GURPS.*|Page \d+)$")
HEADER = {
    "Starting Wealth by Economic Status", "Status", "Starting", "Money",
    "Social", "Weight", "Lifestyle",
}
# "Dead Broke (-25 pts)". The cost is ASCII-signed; the modifier column is not.
STATUS_CELL = re.compile(r"^(.+?)\s+\(([+-]?\d+)\s*pts\)$")
MODIFIER_CELL = re.compile(r"^([+−-]?)(\d+)$")
# The paragraph under the table, which is a rule about Status, not a row.
NOTE_PREFIX = "Your Wealth level determines"
GUIDELINE = "Social Weight values in the table serve as a guideline for the GM"

EXPECTED_STATUSES = [
    "Dead Broke", "Poor", "Struggling", "Average", "Comfortable", "Wealthy",
    "Very Wealthy",
]
# What chapter 3's advantage and disadvantage lists print for the same levels,
# signed as this table signs them.
CHAPTER_3_LEVELS = {
    "Dead Broke": -25, "Poor": -15, "Struggling": -10, "Comfortable": 5,
}
# The levels chapter 3 never prints. Nothing could buy these before this table.
ONLY_IN_CHAPTER_8 = {"Average", "Wealthy", "Very Wealthy"}
# Ch. 10's own rate: "1 Gold Pound = 20 Soli". Used to check the
# ladder climbs, never written to the file.
SOLI_PER_POUND = 20
MONEY_CELL = re.compile(r"^(?:£(\d+)|(\d+)\s*soli)$", re.IGNORECASE)


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
    """The Status paragraph, joined, and the table cells before it."""
    for i, cell in enumerate(cells):
        if cell.startswith(NOTE_PREFIX):
            return cells[:i], " ".join(cells[i:])
    raise ValueError(f"the paragraph under the table is gone; expected {NOTE_PREFIX!r}")


def parse_modifier(text):
    m = MODIFIER_CELL.fullmatch(text)
    if not m:
        raise ValueError(f"unreadable social weight {text!r}")
    sign, digits = m.groups()
    return -int(digits) if sign in ("-", "−") else int(digits)


def parse(cells):
    """One row per status cell; the row runs to the next status cell."""
    starts = [i for i, c in enumerate(cells) if STATUS_CELL.fullmatch(c)]
    if not starts:
        raise ValueError("no status cell found; the table's shape changed")
    rows = []
    for i, start in enumerate(starts):
        end = starts[i + 1] if i + 1 < len(starts) else len(cells)
        group = cells[start:end]
        if len(group) < 4:
            raise ValueError(f"row {group!r} is missing columns")
        status, points = STATUS_CELL.fullmatch(group[0]).groups()
        rows.append({
            "status": status,
            "points": int(points),
            "startingMoney": group[1],
            "socialWeight": parse_modifier(group[2]),
            # Everything after the weight is the lifestyle, so a wrapped
            # cell joins instead of becoming a row of its own.
            "lifestyle": " ".join(group[3:]),
        })
    return rows


def in_soli(text):
    """A money cell in soli, at chapter 8's own rate. Not committed."""
    m = MONEY_CELL.fullmatch(text)
    if not m:
        raise ValueError(f"unreadable money cell {text!r}")
    pounds, soli = m.groups()
    return int(pounds) * SOLI_PER_POUND if pounds is not None else int(soli)


def chapter_3_levels():
    """The wealth levels chapter 3 prints, signed as chapter 8 signs them."""
    found = {}
    for name, sign in (("advantages", 1), ("disadvantages", -1)):
        path = OUT / f"{name}.json"
        for entry in json.loads(path.read_text(encoding="utf-8"))["entries"]:
            if entry["name"].startswith("Wealth: "):
                found[entry["name"].removeprefix("Wealth: ")] = sign * entry["cost"]
    return found


def verify(rows, note):
    got = [r["status"] for r in rows]
    if got != EXPECTED_STATUSES:
        raise ValueError(f"the wealth ladder changed: {got} != {EXPECTED_STATUSES}")

    for row in rows:
        if not row["startingMoney"] or not row["lifestyle"]:
            raise ValueError(f"{row['status']} has an empty column: {row}")

    # Both ladders climb, and they climb together.
    points = [r["points"] for r in rows]
    money = [in_soli(r["startingMoney"]) for r in rows]
    modifiers = [r["socialWeight"] for r in rows]
    for name, values in (("points", points), ("money", money)):
        if values != sorted(values) or len(set(values)) != len(values):
            raise ValueError(f"the {name} column does not climb: {values}")
    if modifiers != sorted(modifiers):
        raise ValueError(f"the social weight column does not climb: {modifiers}")

    shared = chapter_3_levels()
    if shared != CHAPTER_3_LEVELS:
        raise ValueError(
            f"which wealth levels chapter 3 prints changed.\n  now: {shared}"
            f"\n  was: {CHAPTER_3_LEVELS}"
        )
    disagreements = {
        r["status"]: (r["points"], shared[r["status"]])
        for r in rows if r["status"] in shared and shared[r["status"]] != r["points"]
    }
    if disagreements:
        raise ValueError(
            "chapter 3 and chapter 8 price the same wealth level differently; "
            f"that is a finding, not a column to pick: {disagreements}"
        )
    only_here = {r["status"] for r in rows} - set(shared)
    if only_here != ONLY_IN_CHAPTER_8:
        raise ValueError(
            f"which levels only chapter 8 prints changed.\n  now: {sorted(only_here)}"
            f"\n  was: {sorted(ONLY_IN_CHAPTER_8)}"
        )

    if GUIDELINE not in note:
        raise ValueError(f"the paragraph no longer says {GUIDELINE!r}")


def main():
    cells, note = split_note(stream(load_lines(), *WEALTH_RANGE))
    rows = parse(cells)
    verify(rows, note)

    OUT.mkdir(parents=True, exist_ok=True)
    payload = {"sourceLines": {CHAPTER: [list(WEALTH_RANGE)]}, "entries": rows}
    (OUT / "wealth.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=1) + "\n", encoding="utf-8"
    )

    print(f"wealth: {len(rows)} rows")
    for row in rows:
        print(f"  {row['status']:14} {row['points']:+4} pts  "
              f"{row['startingMoney']:<9} social weight {row['socialWeight']:+d}  "
              f"{row['lifestyle']}")
    print(f"only chapter 8 prints: {sorted(ONLY_IN_CHAPTER_8)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
