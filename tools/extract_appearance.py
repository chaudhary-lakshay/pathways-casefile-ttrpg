"""Extract the Appearance table (Ch. 3, p. 13-14, global 714-744).

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

THE COST CELL IS THE ANCHOR, AND IT IS SIGNED
---------------------------------------------
Three columns: Level, Cost, Reaction Modifier. Both numeric cells are signed
integers, so neither can be told from the other by shape -- but the ORDER is
fixed and the level name is neither. A row is therefore a name followed by
exactly two signed cells, and a group of three is a row.

The reaction modifier is NOT derivable from the cost: +1pt gives +1 and +4pts
gives +2, while -4pts gives -1 and -24pts gives -6. The two columns are
independent and both are read.

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
APPEARANCE_RANGE = (714, 744)

FURNITURE = re.compile(r"^(Powered by GURPS.*|Page \d+)$")
HEADER = {"Appearance", "Level", "Cost", "Reaction Modifier"}
# " +1 pt" / " -24 pts". Both numeric columns are signed, so shape alone
# cannot tell cost from modifier -- only their order can.
COST_CELL = re.compile(r"^([+-]?\d+)\s*pts?$")
MODIFIER_CELL = re.compile(r"^([+−-]?\d+) to reaction rolls$")
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
    """Groups of three: a name, a signed cost, a signed reaction modifier.

    The table starts one cell before the FIRST cost cell, because the intro
    prose wraps mid-sentence and its second line ("reactions from others:")
    has no shape a rule could name. Anchoring on the cost cell uses the
    table's own structure instead of guessing where the prose stopped.
    """
    first_cost = next(
        (i for i, c in enumerate(cells) if COST_CELL.fullmatch(c)),
        None,
    )
    if first_cost is None or first_cost == 0:
        raise ValueError("no cost cell with a level name before it; the table changed")
    cells = cells[first_cost - 1:]

    rows = []
    for i in range(0, len(cells), 3):
        group = cells[i:i + 3]
        if len(group) != 3:
            raise ValueError(f"trailing cells with no row to join: {group!r}")
        name, cost, modifier = group
        cost_match = COST_CELL.fullmatch(cost)
        modifier_match = MODIFIER_CELL.fullmatch(modifier)
        if not cost_match or not modifier_match:
            raise ValueError(f"row {group!r} is not name / cost / modifier")
        rows.append({
            "level": name,
            "points": signed(cost_match.group(1)),
            "reactionModifier": signed(modifier_match.group(1)),
        })
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
    modifiers = [r["reactionModifier"] for r in rows]
    if len(set(points)) != len(points):
        raise ValueError(f"two levels share a cost: {points}")

    # A level that costs points reads better than one it refunds, and a level
    # that refunds them reads worse. No row may cross the two.
    for row in rows:
        if row["points"] > 0 and row["reactionModifier"] <= 0:
            raise ValueError(f"{row['level']} costs points and does not help: {row}")
        if row["points"] < 0 and row["reactionModifier"] >= 0:
            raise ValueError(f"{row['level']} refunds points and does not hurt: {row}")

    # The two halves each worsen away from the middle, so a row read out of
    # order would show up here rather than as a plausible-looking number.
    good = [m for m in modifiers if m > 0]
    bad = [m for m in modifiers if m < 0]
    if good != sorted(good) or bad != sorted(bad, reverse=True):
        raise ValueError(f"the reaction column is out of order: {modifiers}")

    # The reaction modifier is NOT the cost. If it ever became derivable, the
    # second column would be redundant, and that is a finding about the book.
    if all(r["reactionModifier"] == r["points"] for r in rows):
        raise ValueError(
            "every reaction modifier now equals its cost; the two columns used "
            "to be independent (+4 pts gives +2), so the table has changed"
        )

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
        print(f"  {row['level']:32} {row['points']:+4} pts  "
              f"reaction {row['reactionModifier']:+d}")
    print(f"note: {note}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
