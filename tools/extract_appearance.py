"""Extract the Appearance table (Ch. 3, p. 15-16, global 768-806).

THE TRAIT LISTS PRINT NONE OF THESE
-----------------------------------
Starting Wealth was half-printed in chapter 3 -- four of its seven levels sat
among the advantages and disadvantages. Appearance is printed there ZERO
times: `advantages.json` and `disadvantages.json` carry no Attractive, no
Ugly, no Hideous. The one row whose name contains the word is "Unsettling
Appearance", which is a different trait entirely -- 4 points, "a common
consequence of Abyss and Chained Pathway potions" -- and verify() asserts it
stays OUT of this table, because merging the two would price a potion's
disfigurement as a character-creation choice.

THE BOOK SAYS IT DISAGREES WITH GURPS, AND THAT NOTE IS THE POINT
-----------------------------------------------------------------
The line under the table reads "Appearance point costs have been adjusted for
the Pathways setting and differ from standard GURPS values." Every number
here is therefore this book's, and a reviewer who reaches for the GURPS 4e
values (where Attractive is 4 points, not 1) would be correcting the book
with its own source material. NOTE_PHRASE pins the sentence.

THE THIRD COLUMN IS PROSE NOW, AND THE GM'S
------------------------------------------
v6.9d printed Level, Cost, Reaction Modifier: "+2 to reaction rolls". v6.9i
replaced the third column with Effect -- "NPCs notice and treat you
favourably; more inclined to listen and help (GM discretion)" -- and the
intro now says the levels "guide the GM in determining realistic NPC
responses". There is no number left to read, so the row stores the effect as
printed, wrapped lines joined. A row is a level name, its signed cost, then
every line up to the next level name.

WHAT IS DELIBERATELY NOT EXTRACTED
----------------------------------
Nothing on these pages besides the table and its note. The Languages tables
above it (common and mystical proficiency, with their own costs and a -2 to
rituals) are a separate slice with a separate caller -- RitualResolution --
and folding them in here would put two unrelated cost ladders in one file.
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

CHAPTER = "3"
APPEARANCE_RANGE = (768, 806)

FURNITURE = re.compile(r"^(Powered by GURPS.*|Page \d+)$")
HEADER = {"Appearance", "Level", "Cost", "Effect"}
# " +1 pt" / " -24 pts".
COST_CELL = re.compile(r"^([+-]?\d+)\s*pts?$")
# The prose that closes the section. The prose that OPENS it is not matched by
# a prefix: it wraps mid-sentence onto a second line ("...levels of App" /
# "reactions from others:"), and the second line starts with nothing a rule
# could name. The table's own anchor drops both -- see parse().
NOTE_PHRASE = "differ from standard GURPS values"

EXPECTED_LEVELS = [
    "Attractive", "Handsome / Beautiful", "Very Handsome / Very Beautiful",
    "Unattractive", "Ugly", "Hideous", "Monstrous", "Horrific",
]
# Chapter 3's own trait lists print none of the levels above. The one row
# whose name contains "Appearance" is a potion's doing, not a choice.
NOT_AN_APPEARANCE_LEVEL = "Unsettling Appearance"


def load_lines():
    data = json.loads(LINES_PATH.read_text(encoding="utf-8"))
    return {l["n"]: l["text"] for l in data}


def stream(byn, start, end):
    """Table cells in order, headers, furniture and the framing prose dropped."""
    out, note = [], None
    for n in range(start, end + 1):
        text = (byn.get(n) or "").strip()
        if not text or FURNITURE.match(text) or text in HEADER:
            continue
        if NOTE_PHRASE in text:
            note = text
            continue
        out.append(text)
    if note is None:
        raise ValueError(f"the note under the table is gone; expected {NOTE_PHRASE!r}")
    return out, note


def signed(text):
    """A cell's number, reading both kinds of minus this book prints."""
    return int(text.replace("−", "-"))


def parse(cells):
    """A level name, its signed cost, then its effect lines up to the next
    name. The intro prose is dropped by starting at the first level name."""
    try:
        cells = cells[cells.index(EXPECTED_LEVELS[0]):]
    except ValueError:
        raise ValueError("the first level is gone; the table changed")
    rows = []
    for cell in cells:
        if cell in EXPECTED_LEVELS:
            rows.append({"level": cell, "points": None, "effect": []})
        elif rows[-1]["points"] is None:
            cost = COST_CELL.fullmatch(cell)
            if not cost:
                raise ValueError(f"{rows[-1]['level']} has no cost cell: {cell!r}")
            rows[-1]["points"] = signed(cost.group(1))
        else:
            rows[-1]["effect"].append(cell)
    for row in rows:
        if not row["effect"]:
            raise ValueError(f"{row['level']} prints no effect")
        row["effect"] = " ".join(row["effect"])
    return rows


def appearance_named_traits():
    """Trait rows in chapter 3's lists whose name mentions appearance."""
    found = []
    for name in ("advantages", "disadvantages"):
        path = OUT / f"{name}.json"
        for entry in json.loads(path.read_text(encoding="utf-8"))["entries"]:
            if "Appearance" in entry["name"]:
                found.append(entry["name"])
    return found


def verify(rows, note):
    got = [r["level"] for r in rows]
    if got != EXPECTED_LEVELS:
        raise ValueError(f"the appearance ladder changed: {got} != {EXPECTED_LEVELS}")

    points = [r["points"] for r in rows]
    if len(set(points)) != len(points):
        raise ValueError(f"two levels share a cost: {points}")
    # Each half worsens away from the middle: the costs order the faces.
    good = [p for p in points if p > 0]
    bad = [p for p in points if p < 0]
    if good != sorted(good) or bad != sorted(bad, reverse=True) or len(good) + len(bad) != len(points):
        raise ValueError(f"the cost column is out of order: {points}")
    # The number left the table in v6.9i; a reaction modifier coming back is
    # a finding about the book, not a cell to drop.
    if any("reaction roll" in r["effect"] for r in rows):
        raise ValueError("an effect prints a reaction modifier again")

    named = appearance_named_traits()
    if named != [NOT_AN_APPEARANCE_LEVEL]:
        raise ValueError(
            "which appearance-named traits chapter 3's lists print changed.\n"
            f"  now: {named}\n  was: [{NOT_AN_APPEARANCE_LEVEL!r}]"
        )
    if any(r["level"] == NOT_AN_APPEARANCE_LEVEL for r in rows):
        raise ValueError(
            f"{NOT_AN_APPEARANCE_LEVEL!r} is a potion's doing (Abyss and Chained "
            "pathways), not a level bought at creation; it does not belong in "
            "this table"
        )
    if NOTE_PHRASE not in note:
        raise ValueError(f"the note no longer says {NOTE_PHRASE!r}")


def main():
    cells, note = stream(load_lines(), *APPEARANCE_RANGE)
    rows = parse(cells)
    verify(rows, note)

    OUT.mkdir(parents=True, exist_ok=True)
    payload = {"sourceLines": {CHAPTER: [list(APPEARANCE_RANGE)]}, "entries": rows}
    (OUT / "appearance.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=1) + "\n", encoding="utf-8"
    )

    print(f"appearance: {len(rows)} rows")
    for row in rows:
        print(f"  {row['level']:32} {row['points']:+4} pts  {row['effect']}")
    print(f"note: {note}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
